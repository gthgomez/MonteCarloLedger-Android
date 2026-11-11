package com.montecarlo.ledger.sync

/** Snapshot of sync metadata for an in-transaction destructive-pull check. */
data class VaultPullRestoreGuardState(
    val allowLocalOverwrite: Boolean,
    val ancestryEstablished: Boolean,
    val syncedFingerprint: String?,
)
