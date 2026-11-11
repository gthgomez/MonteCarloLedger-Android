package com.montecarlo.ledger.sync

import android.content.Context
import android.util.Base64
import androidx.annotation.VisibleForTesting
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

    /**
     * Unit tests only: runs after the vault ciphertext is fetched/decrypted but before the
     * pre-restore destructive-pull guard (simulates local mutation while the network is in flight).
     */
    @VisibleForTesting
    @Volatile
    var preRestoreRevalidationBarrier: (suspend () -> Unit)? = null

    suspend fun pullVault(
        vaultId: String,
        passphrase: CharArray,
        serverUrl: String = store.serverUrl,
        allowLocalOverwrite: Boolean = false,
    ): Int {
        ensureSafeForDestructivePull(allowLocalOverwrite)

        val client = VaultHttpClient(serverUrl)
        val metadata = client.fetchMetadata(vaultId)
            ?: throw VaultSyncException("Vault $vaultId not found.")
        val salt = Base64.decode(metadata.kdfSaltB64, Base64.DEFAULT)
        val keys = VaultCrypto.deriveKeys(String(passphrase), salt)
        val record = client.fetchVault(vaultId, keys.authToken)
        val plaintext = VaultCrypto.decrypt(record.ciphertextB64, record.nonceB64, keys.encKey)

        preRestoreRevalidationBarrier?.invoke()
        ensureSafeForDestructivePull(allowLocalOverwrite)

        repository.restoreCrossPlatformVaultJson(plaintext)

        val localFingerprint = localLedgerFingerprint()
            ?: throw VaultSyncException(
                "Vault restored but the local ledger could not be fingerprinted; pull again before pushing.",
            )
        val preservationFingerprint = LocalLedgerPreservation.fingerprint(repository)

        if (!store.commitSyncState(
                vaultId,
                serverUrl,
                record.revision,
                localFingerprint,
                preservationFingerprint,
                ancestryEstablished = true,
            )
        ) {
            throw VaultSyncException(
                "Ledger was replaced from the vault but sync metadata could not be saved. " +
                    "Run a local backup now and pull again before pushing.",
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
        val preservationFingerprint = LocalLedgerPreservation.fingerprint(repository)
        if (!store.commitSyncState(
                vaultId,
                serverUrl,
                revision,
                fingerprint,
                preservationFingerprint,
                ancestryEstablished = true,
            )
        ) {
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
        val preservationFingerprint = LocalLedgerPreservation.fingerprint(repository)
        if (!store.commitSyncState(
                vaultId,
                serverUrl,
                revision,
                fingerprint,
                preservationFingerprint,
                ancestryEstablished = true,
            )
        ) {
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

    /**
     * Guards destructive vault restore only. Push intentionally uploads pending core edits.
     * Matching preservation fingerprints does **not** prove Android-only data is on the server.
     */
    private suspend fun ensureSafeForDestructivePull(allowLocalOverwrite: Boolean) {
        if (allowLocalOverwrite) return
        if (LocalLedgerPreservation.hasPreservationRisk(repository)) {
            throw VaultLocalChangesException()
        }
        if (!store.ancestryEstablished && repository.hasCrossPlatformLedgerContent()) {
            throw VaultLocalChangesException()
        }
        if (store.ancestryEstablished) {
            val local = localLedgerFingerprint()
            val synced = store.syncedFingerprint
            if (local == null || synced == null || local != synced) {
                throw VaultLocalChangesException()
            }
        }
    }

    private suspend fun localLedgerFingerprint(): String? = runCatching {
        val json = repository.exportCrossPlatformVaultJson("fingerprint")
        VaultSnapshotFingerprint.ofSnapshotJson(json)
    }.getOrNull()
}
