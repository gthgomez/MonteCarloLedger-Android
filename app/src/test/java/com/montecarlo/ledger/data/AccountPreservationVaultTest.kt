package com.montecarlo.ledger.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.sync.CloudVaultSync
import com.montecarlo.ledger.sync.LocalLedgerPreservation
import com.montecarlo.ledger.sync.VaultLocalChangesException
import com.montecarlo.ledger.sync.VaultSnapshotFingerprint
import com.montecarlo.ledger.sync.VaultSyncStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountPreservationVaultTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var context: Context

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
        repo.ensureDefaultAccountSeeded()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun primaryAccount(): AccountEntity =
        db.accountDao().getDefault() ?: error("default account missing")

    private fun AccountEntity.snapshot() =
        listOf(id, name, type, balanceCents, isReconciled, isDefault, lastUpdated)

    @Test
    fun hasCustomAccounts_falseForPristinePrimaryOnly() = runBlocking {
        val before = primaryAccount().snapshot()
        assertFalse(repo.hasCustomAccounts())
        assertFalse(LocalLedgerPreservation.hasPreservationRisk(repo))
        assertEquals(before, primaryAccount().snapshot())
    }

    @Test
    fun hasCustomAccounts_trueWhenPrimaryHasReconciledNonZeroBalance() = runBlocking {
        val beforeId = primaryAccount().id
        repo.setBankBalance(42_500)
        val after = primaryAccount()
        assertEquals(beforeId, after.id)
        assertEquals(42_500L, after.balanceCents)
        assertTrue(after.isReconciled)
        assertTrue(repo.hasCustomAccounts())
    }

    @Test
    fun hasCustomAccounts_trueWhenTransactionLinkedToLonePrimary() = runBlocking {
        val primary = primaryAccount()
        assertEquals(0L, primary.balanceCents)
        repo.insertTransaction(
            TransactionEntity(
                description = "Tagged",
                amount_cents = -100,
                date = "2026-11-01",
                type = "expense",
                category = "misc",
                account_id = primary.id,
            ),
        )
        assertTrue(repo.hasCustomAccounts())
    }

    @Test
    fun pull_blocksOnReconciledPrimary_withoutGoalsOrFingerprintMismatch() = runBlocking {
        repo.setBankBalance(50_000)
        val accountBefore = primaryAccount()
        val vaultFp = VaultSnapshotFingerprint.ofSnapshotJson(repo.exportCrossPlatformVaultJson("fp"))
        assertEquals(vaultFp, VaultSnapshotFingerprint.ofSnapshotJson(repo.exportCrossPlatformVaultJson("fp")))
        VaultSyncStore(context).commitSyncState(
            vaultId = "v",
            serverUrl = VaultSyncStore.DEFAULT_SERVER,
            revision = 1,
            syncedFingerprint = vaultFp,
            androidPreservationFingerprint = LocalLedgerPreservation.fingerprint(repo),
            ancestryEstablished = true,
        )
        val sync = CloudVaultSync(context, repo)
        assertThrows(VaultLocalChangesException::class.java) {
            runBlocking {
                sync.pullVault("v", "secret".toCharArray(), "https://127.0.0.1:9", allowLocalOverwrite = false)
            }
        }
        val accountAfter = primaryAccount()
        assertEquals(accountBefore.snapshot(), accountAfter.snapshot())
        assertEquals(accountBefore.balanceCents, accountAfter.balanceCents)
        assertEquals(accountBefore.isReconciled, accountAfter.isReconciled)
    }

    @Test
    fun vaultRestore_reseedsPrimaryAndDropsReconciledState_whenInvokedDirectly() = runBlocking {
        repo.setBankBalance(33_000)
        val before = primaryAccount()
        assertTrue(before.isReconciled)
        assertEquals(33_000L, before.balanceCents)
        val vaultJson = repo.exportCrossPlatformVaultJson("2026-11-11")
        repo.restoreCrossPlatformVaultJson(vaultJson)
        val after = db.accountDao().getDefault()
        assertNotNull(after)
        assertEquals("Primary account", after!!.name)
        assertEquals(0L, after.balanceCents)
        assertFalse(after.isReconciled)
    }
}
