package com.montecarlo.ledger.sync

/**
 * Immutable sync baselines captured during vault pull restore.
 *
 * [remoteCoreFingerprint] is derived from the remote vault JSON payload (what was actually on the
 * server), not from a post-restore re-export of the mutable local ledger.
 */
data class VaultPullRestoreBaselines(
    val remoteCoreFingerprint: String,
    val postRestorePreservationFingerprint: String,
)
