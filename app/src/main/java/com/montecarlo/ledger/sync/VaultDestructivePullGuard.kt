package com.montecarlo.ledger.sync

import com.montecarlo.ledger.data.LedgerRepository

/** Shared fail-closed rules for vault pull/join (fast pre-network check and in-transaction revalidation). */
object VaultDestructivePullGuard {

    suspend fun verifyOrThrow(
        repository: LedgerRepository,
        state: VaultPullRestoreGuardState,
        localCoreFingerprint: String?,
    ) {
        if (state.allowLocalOverwrite) return
        if (LocalLedgerPreservation.hasPreservationRisk(repository)) {
            throw VaultLocalChangesException()
        }
        if (!state.ancestryEstablished && repository.hasCrossPlatformLedgerContent()) {
            throw VaultLocalChangesException()
        }
        if (state.ancestryEstablished) {
            val synced = state.syncedFingerprint
            if (localCoreFingerprint == null || synced == null || localCoreFingerprint != synced) {
                throw VaultLocalChangesException()
            }
        }
    }

    suspend fun currentCoreFingerprint(repository: LedgerRepository): String? = runCatching {
        val json = repository.exportCrossPlatformVaultJson("fingerprint")
        VaultSnapshotFingerprint.ofSnapshotJson(json)
    }.getOrNull()
}
