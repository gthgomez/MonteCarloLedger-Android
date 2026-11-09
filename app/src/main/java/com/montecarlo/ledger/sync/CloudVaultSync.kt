package com.montecarlo.ledger.sync

import android.content.Context
import android.util.Base64
import com.montecarlo.ledger.data.LedgerRepository
import java.security.MessageDigest
import java.time.Instant

/**
 * Optional encrypted cloud sync (opt-in). Passphrase stays in memory for the operation only.
 */
class CloudVaultSync(
    context: Context,
    private val repository: LedgerRepository,
) {
    private val store = VaultSyncStore(context)

    suspend fun pullVault(
        vaultId: String,
        passphrase: CharArray,
        serverUrl: String = store.serverUrl,
        allowLocalOverwrite: Boolean = false,
    ): Int {
        val client = VaultHttpClient(serverUrl)
        val metadata = client.fetchMetadata(vaultId)
            ?: throw VaultSyncException("Vault $vaultId not found.")
        val salt = Base64.decode(metadata.kdfSaltB64, Base64.DEFAULT)
        val keys = VaultCrypto.deriveKeys(String(passphrase), salt)
        val record = client.fetchVault(vaultId, keys.authToken)
        val plaintext = VaultCrypto.decrypt(record.ciphertextB64, record.nonceB64, keys.encKey)

        val fingerprint = fingerprintOf(plaintext)
        val localFingerprint = exportFingerprint()
        if (!allowLocalOverwrite && localFingerprint != null && localFingerprint != store.syncedFingerprint) {
            throw VaultLocalChangesException()
        }

        repository.restoreCrossPlatformVaultJson(plaintext)

        store.vaultId = vaultId
        store.serverUrl = serverUrl
        store.revision = record.revision
        store.ancestryEstablished = true
        store.syncedFingerprint = fingerprint
        return record.revision
    }

    suspend fun pushVault(
        vaultId: String,
        passphrase: CharArray,
        serverUrl: String = store.serverUrl,
    ): Int {
        if (!store.ancestryEstablished) throw VaultAncestryException()
        val client = VaultHttpClient(serverUrl)
        val json = repository.exportCrossPlatformVaultJson(Instant.now().toString())
        val metadata = client.fetchMetadata(vaultId)
            ?: throw VaultSyncException("Vault $vaultId not found.")
        val salt = Base64.decode(metadata.kdfSaltB64, Base64.DEFAULT)
        val keys = VaultCrypto.deriveKeys(String(passphrase), salt)
        val encrypted = VaultCrypto.encrypt(json, keys.encKey)
        val revision = client.pushVault(
            vaultId = vaultId,
            authToken = keys.authToken,
            expectedRevision = store.revision,
            ciphertextB64 = encrypted.ciphertextB64,
            nonceB64 = encrypted.nonceB64,
        )
        store.vaultId = vaultId
        store.serverUrl = serverUrl
        store.revision = revision
        store.syncedFingerprint = fingerprintOf(json)
        return revision
    }

    fun lastSyncSummary(): String? {
        val id = store.vaultId ?: return null
        return buildString {
            append("Vault ")
            append(id)
            append(" · revision ")
            append(store.revision)
            if (store.ancestryEstablished) append(" · ancestry OK") else append(" · pull required before push")
        }
    }

    private suspend fun exportFingerprint(): String? = runCatching {
        fingerprintOf(repository.exportCrossPlatformVaultJson("fingerprint"))
    }.getOrNull()

    private fun fingerprintOf(json: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
