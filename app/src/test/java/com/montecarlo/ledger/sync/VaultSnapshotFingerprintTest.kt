package com.montecarlo.ledger.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultSnapshotFingerprintTest {
    @Test
    fun ignoresUpdatedAt() {
        val withTs = """{"version":"2.1","updated_at":"2026-01-01T00:00:00Z","current_balance_cents":0,"incomes":[],"payments":[],"transactions":[],"bill_occurrences":[]}"""
        val withoutTs = """{"version":"2.1","current_balance_cents":0,"incomes":[],"payments":[],"transactions":[],"bill_occurrences":[]}"""
        assertEquals(
            VaultSnapshotFingerprint.ofSnapshotJson(withTs),
            VaultSnapshotFingerprint.ofSnapshotJson(withoutTs),
        )
    }

    @Test
    fun detectsMeaningfulChange() {
        val a = """{"version":"2.1","updated_at":"t1","current_balance_cents":100,"incomes":[],"payments":[],"transactions":[],"bill_occurrences":[]}"""
        val b = """{"version":"2.1","updated_at":"t2","current_balance_cents":200,"incomes":[],"payments":[],"transactions":[],"bill_occurrences":[]}"""
        assertNotEquals(VaultSnapshotFingerprint.ofSnapshotJson(a), VaultSnapshotFingerprint.ofSnapshotJson(b))
    }
}
