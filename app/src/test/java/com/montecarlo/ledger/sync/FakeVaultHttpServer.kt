package com.montecarlo.ledger.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-process vault API for unit tests (http://127.0.0.1 only). Not a protocol reference server.
 */
class FakeVaultHttpServer {

    data class VaultState(
        val kdfSaltB64: String,
        var authToken: String,
        var ciphertextB64: String,
        var nonceB64: String,
        var revision: Int,
    )

    private val vaults = LinkedHashMap<String, VaultState>()
    private val createCalls = AtomicInteger(0)
    private val pushCalls = AtomicInteger(0)
    private val fetchVaultCalls = AtomicInteger(0)

    lateinit var server: HttpServer
        private set

    val baseUrl: String
        get() = "http://127.0.0.1:${server.address.port}"

    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/vault") { exchange -> handle(exchange) }
        server.executor = Executors.newSingleThreadExecutor()
        server.start()
    }

    fun stop() {
        server.stop(0)
    }

    fun vault(vaultId: String): VaultState? = vaults[vaultId]

    fun createInvocationCount(): Int = createCalls.get()
    fun pushInvocationCount(): Int = pushCalls.get()
    fun fetchVaultInvocationCount(): Int = fetchVaultCalls.get()

    private fun handle(exchange: HttpExchange) {
        try {
            val path = exchange.requestURI.path.removePrefix("/api/vault").trim('/')
            val segments = path.split('/').filter { it.isNotEmpty() }
            when (exchange.requestMethod) {
                "POST" -> {
                    if (segments.isNotEmpty()) {
                        respond(exchange, 404, "{}")
                        return
                    }
                    createCalls.incrementAndGet()
                    val body = JSONObject(exchange.readBody())
                    val vaultId = body.getString("vault_id")
                    if (vaults.containsKey(vaultId)) {
                        respond(exchange, 409, """{"error":"VAULT_ALREADY_EXISTS"}""")
                        return
                    }
                    vaults[vaultId] = VaultState(
                        kdfSaltB64 = body.getString("kdf_salt_b64"),
                        authToken = body.getString("auth_token"),
                        ciphertextB64 = body.getString("ciphertext_b64"),
                        nonceB64 = body.getString("nonce_b64"),
                        revision = 1,
                    )
                    respond(exchange, 201, """{"revision":1}""")
                }
                "GET" -> {
                    if (segments.isEmpty()) {
                        respond(exchange, 404, "{}")
                        return
                    }
                    val vaultId = decode(segments[0])
                    val state = vaults[vaultId]
                    if (state == null) {
                        respond(exchange, 404, "{}")
                        return
                    }
                    if (segments.size == 2 && segments[1] == "metadata") {
                        respond(
                            exchange,
                            200,
                            JSONObject()
                                .put("kdf_salt_b64", state.kdfSaltB64)
                                .put("revision", state.revision)
                                .toString(),
                        )
                        return
                    }
                    if (segments.size == 1) {
                        val token = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")?.trim()
                        if (token != state.authToken) {
                            respond(exchange, 401, "{}")
                            return
                        }
                        fetchVaultCalls.incrementAndGet()
                        respond(
                            exchange,
                            200,
                            JSONObject()
                                .put("revision", state.revision)
                                .put("ciphertext_b64", state.ciphertextB64)
                                .put("nonce_b64", state.nonceB64)
                                .toString(),
                        )
                        return
                    }
                    respond(exchange, 404, "{}")
                }
                "PUT" -> {
                    if (segments.size != 1) {
                        respond(exchange, 404, "{}")
                        return
                    }
                    pushCalls.incrementAndGet()
                    val vaultId = decode(segments[0])
                    val state = vaults[vaultId]
                    if (state == null) {
                        respond(exchange, 404, "{}")
                        return
                    }
                    val token = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")?.trim()
                    if (token != state.authToken) {
                        respond(exchange, 401, "{}")
                        return
                    }
                    val body = JSONObject(exchange.readBody())
                    val expected = body.getInt("expected_revision")
                    if (expected != state.revision) {
                        respond(exchange, 409, """{"error":"REVISION_CONFLICT"}""")
                        return
                    }
                    state.ciphertextB64 = body.getString("ciphertext_b64")
                    state.nonceB64 = body.getString("nonce_b64")
                    state.revision += 1
                    respond(exchange, 200, """{"revision":${state.revision}}""")
                }
                else -> respond(exchange, 405, "{}")
            }
        } catch (ex: Exception) {
            respond(exchange, 500, """{"error":"${ex.message}"}""")
        }
    }

    private fun decode(segment: String): String = java.net.URLDecoder.decode(segment, "UTF-8")

    private fun HttpExchange.readBody(): String =
        requestBody.bufferedReader().use { it.readText() }

    private fun respond(exchange: HttpExchange, code: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.set("Content-Type", "application/json")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}
