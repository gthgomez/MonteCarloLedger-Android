package com.montecarlo.ledger.sync

/**
 * Immutable sync baselines captured during vault pull restore.
 *
 * [restoredLocalCoreFingerprint] is a re-export fingerprint of the Room core taken inside the same
 * restore transaction (after wipe + insert). This matches [VaultDestructivePullGuard.currentCoreFingerprint]
 * on the restored ledger and must be used as the synced baseline — remote payload bytes can differ
 * (e.g. v2.0 → local 2.1 export, regenerated debt ids, income date fields).
 *
 * [remotePayloadCoreFingerprint] fingerprints the decrypted remote JSON for provenance/debug only;
 * it must not be used for local dirty detection.
 */
data class VaultPullRestoreBaselines(
    val restoredLocalCoreFingerprint: String,
    val postRestorePreservationFingerprint: String,
    val remotePayloadCoreFingerprint: String,
)
