package com.montecarlo.ledger.sync

import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal vault HTTP client (Python/Web protocol). Remote vaults require HTTPS. */
class VaultHttpClient(
    serverBaseUrl: String,
) {
    private val serverBaseUrl = requireSecureBaseUrl(serverBaseUrl)
    fun fetchMetadata(vaultId: String): VaultMetadata? {
        val url = URL("${serverBaseUrl.trimEnd('/')}/api/vault/${encode(vaultId)}/metadata")
        val conn = open(url, "GET")
        return when (conn.responseCode) {
            404 -> null
            200 -> {
                val body = conn.readBody()
                val json = JSONObject(body)
                VaultMetadata(
                    kdfSaltB64 = json.getString("kdf_salt_b64"),
                    revision = json.getInt("revision"),
                )
            }
            else -> throw VaultSyncException("metadata HTTP ${conn.responseCode}: ${conn.readErrorBody()}")
        }.also { conn.disconnect() }
    }

    fun fetchVault(vaultId: String, authToken: String): VaultRecord {
        val url = URL("${serverBaseUrl.trimEnd('/')}/api/vault/${encode(vaultId)}")
        val conn = open(url, "GET", authToken = authToken)
        if (conn.responseCode == 401) throw VaultAuthException()
        if (conn.responseCode != 200) {
            throw VaultSyncException("read HTTP ${conn.responseCode}: ${conn.readErrorBody()}")
        }
        val json = JSONObject(conn.readBody())
        return VaultRecord(
            revision = json.getInt("revision"),
            ciphertextB64 = json.getString("ciphertext_b64"),
            nonceB64 = json.getString("nonce_b64"),
        ).also { conn.disconnect() }
    }

    fun createVault(
        vaultId: String,
        authToken: String,
        saltB64: String,
        ciphertextB64: String,
        nonceB64: String,
    ): Int {
        val url = URL("${serverBaseUrl.trimEnd('/')}/api/vault")
        val conn = open(url, "POST")
        val payload = JSONObject()
            .put("vault_id", vaultId)
            .put("auth_token", authToken)
            .put("kdf_salt_b64", saltB64)
            .put("ciphertext_b64", ciphertextB64)
            .put("nonce_b64", nonceB64)
        conn.writeJson(payload)
        return when (conn.responseCode) {
            201 -> JSONObject(conn.readBody()).getInt("revision")
            409 -> throw VaultConflictException("VAULT_ALREADY_EXISTS")
            else -> throw VaultSyncException("create HTTP ${conn.responseCode}: ${conn.readErrorBody()}")
        }.also { conn.disconnect() }
    }

    fun pushVault(
        vaultId: String,
        authToken: String,
        expectedRevision: Int,
        ciphertextB64: String,
        nonceB64: String,
    ): Int {
        val url = URL("${serverBaseUrl.trimEnd('/')}/api/vault/${encode(vaultId)}")
        val conn = open(url, "PUT", authToken = authToken)
        val payload = JSONObject()
            .put("expected_revision", expectedRevision)
            .put("ciphertext_b64", ciphertextB64)
            .put("nonce_b64", nonceB64)
        conn.writeJson(payload)
        return when (conn.responseCode) {
            200 -> JSONObject(conn.readBody()).getInt("revision")
            409 -> throw VaultConflictException("REVISION_CONFLICT")
            401 -> throw VaultAuthException()
            else -> throw VaultSyncException("push HTTP ${conn.responseCode}: ${conn.readErrorBody()}")
        }.also { conn.disconnect() }
    }

    private fun open(url: URL, method: String, authToken: String? = null): HttpURLConnection {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            if (method == "POST" || method == "PUT") {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            authToken?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        return conn
    }

    private fun HttpURLConnection.writeJson(payload: JSONObject) {
        OutputStreamWriter(outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }
    }

    private fun HttpURLConnection.readBody(): String =
        (if (responseCode in 200..299) inputStream else errorStream)
            ?.bufferedReader()
            ?.use(BufferedReader::readText)
            .orEmpty()

    private fun HttpURLConnection.readErrorBody(): String = readBody().ifBlank { "empty body" }

    private fun encode(vaultId: String): String = URLEncoder.encode(vaultId, "UTF-8")

    companion object {
        fun requireSecureBaseUrl(raw: String): String {
            val trimmed = raw.trim().trimEnd('/')
            val url = URL(trimmed)
            val scheme = url.protocol.lowercase()
            val host = url.host.lowercase()
            val localDev = host == "localhost" || host == "127.0.0.1" || host.endsWith(".localhost")
            require(scheme == "https" || (localDev && scheme == "http")) {
                "Vault server must use HTTPS (http only allowed for localhost testing)"
            }
            if (url.userInfo != null) {
                throw VaultSyncException("Vault server URL must not embed credentials.")
            }
            return trimmed
        }
    }

    data class VaultMetadata(val kdfSaltB64: String, val revision: Int)

    data class VaultRecord(
        val revision: Int,
        val ciphertextB64: String,
        val nonceB64: String,
    )
}
