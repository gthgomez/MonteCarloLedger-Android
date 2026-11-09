package com.montecarlo.ledger.sync

import android.content.Context
import android.util.Base64
import com.montecarlo.ledger.data.LedgerRepository
import java.security.SecureRandom
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
        if (localChangesPending() && !allowLocalOverwrite) {
            throw VaultLocalChangesException()
        }

        val client = VaultHttpClient(serverUrl)
        val metadata = client.fetchMetadata(vaultId)
            ?: throw VaultSyncException("Vault $vaultId not found.")
        val salt = Base64.decode(metadata.kdfSaltB64, Base64.DEFAULT)
        val keys = VaultCrypto.deriveKeys(String(passphrase), salt)
        val record = client.fetchVault(vaultId, keys.authToken)
        val plaintext = VaultCrypto.decrypt(record.ciphertextB64, record.nonceB64, keys.encKey)

        repository.restoreCrossPlatformVaultJson(plaintext)

        val localFingerprint = localLedgerFingerprint()
            ?: throw VaultSyncException(
                "Vault restored but the local ledger could not be fingerprinted; pull again before pushing.",
            )

        if (!store.commitSyncState(vaultId, serverUrl, record.revision, localFingerprint, ancestryEstablished = true)) {
            throw VaultSyncException(
                "Ledger restored but sync metadata could not be saved. Pull again before pushing.",
            )
        }
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
        val fingerprint = VaultSnapshotFingerprint.ofSnapshotJson(json)
        if (!store.commitSyncState(vaultId, serverUrl, revision, fingerprint, ancestryEstablished = true)) {
            throw VaultSyncException(
                "Upload succeeded (revision $revision) but sync metadata could not be saved locally.",
            )
        }
        return revision
    }

    /**
     * Create a remote vault from the current local ledger (or join if the vault already exists).
     */
    suspend fun createVault(
        vaultId: String,
        passphrase: CharArray,
        serverUrl: String = store.serverUrl,
    ): Int {
        val client = VaultHttpClient(serverUrl)
        val json = repository.exportCrossPlatformVaultJson(Instant.now().toString())
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val keys = VaultCrypto.deriveKeys(String(passphrase), salt)
        val encrypted = VaultCrypto.encrypt(json, keys.encKey)
        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val revision = try {
            client.createVault(
                vaultId = vaultId,
                authToken = keys.authToken,
                saltB64 = saltB64,
                ciphertextB64 = encrypted.ciphertextB64,
                nonceB64 = encrypted.nonceB64,
            )
        } catch (_: VaultConflictException) {
            return pullVault(vaultId, passphrase, serverUrl, allowLocalOverwrite = false)
        }
        val fingerprint = VaultSnapshotFingerprint.ofSnapshotJson(json)
        if (!store.commitSyncState(vaultId, serverUrl, revision, fingerprint, ancestryEstablished = true)) {
            throw VaultSyncException("Vault created on server but local sync metadata could not be saved.")
        }
        return revision
    }

    fun readStoredVaultId(): String? = store.vaultId

    fun readStoredServerUrl(): String = store.serverUrl

    fun lastSyncSummary(): String? {
        val id = store.vaultId ?: return null
        return buildString {
            append("Vault ")
            append(id)
            append(" · revision ")
            append(store.revision)
            if (store.ancestryEstablished) append(" · ancestry OK") else append(" · pull or create required before push")
        }
    }

    private suspend fun localLedgerFingerprint(): String? = runCatching {
        val json = repository.exportCrossPlatformVaultJson("fingerprint")
        VaultSnapshotFingerprint.ofSnapshotJson(json)
    }.getOrNull()

    private suspend fun localChangesPending(): Boolean {
        val fingerprint = localLedgerFingerprint()
        return if (!store.ancestryEstablished) {
            repository.hasCrossPlatformLedgerContent()
        } else {
            val synced = store.syncedFingerprint
            fingerprint == null || synced == null || fingerprint != synced
        }
    }
}
