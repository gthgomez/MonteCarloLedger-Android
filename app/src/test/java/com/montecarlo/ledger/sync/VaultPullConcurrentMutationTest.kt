package com.montecarlo.ledger.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Exercises the pre-restore revalidation guard with a deterministic barrier at the same point
 * a delayed HTTP response would resume (after fetch/decrypt, before [CloudVaultSync] restores Room).
 * Does not prove the full HTTP client stack is race-free under explicit overwrite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultPullConcurrentMutationTest {

    private lateinit var fakeServer: FakeVaultHttpServer
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var sync: CloudVaultSync

    private val vaultId = "latency-guard"
    private val passphrase = "latency-pass".toCharArray()

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fakeServer = FakeVaultHttpServer().also { it.start() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
        sync = CloudVaultSync(context, repo)
        repo.ensureDefaultAccountSeeded()
        db.transactionDao().insert(
            TransactionEntity(
                id = 1,
                description = "Opening",
                amount_cents = 100,
                date = "2026-11-01",
                type = "Adjustment",
                category = "System",
            ),
        )
        sync.createVault(vaultId, passphrase, fakeServer.baseUrl)
    }

    @After
    fun tearDown() {
        sync.preRestoreRevalidationBarrier = null
        if (::db.isInitialized) db.close()
        if (::fakeServer.isInitialized) fakeServer.stop()
    }

    @Test
    fun pull_revalidatesBeforeRestore_whenLocalRiskAppearsAfterFetch(): Unit = runBlocking {
        val fetchesBefore = fakeServer.fetchVaultInvocationCount()
        sync.preRestoreRevalidationBarrier = {
            db.goalDao().insertGoal(
                GoalEntity(name = "During fetch", targetAmountCents = 1, currentAmountCents = 0),
            )
        }
        assertThrows(VaultLocalChangesException::class.java) {
            runBlocking {
                sync.pullVault(vaultId, passphrase, fakeServer.baseUrl, allowLocalOverwrite = false)
            }
        }
        assertTrue(
            "pull must reach the vault read before the pre-restore guard",
            fakeServer.fetchVaultInvocationCount() > fetchesBefore,
        )
        assertEquals(1, db.goalDao().getAllGoals().first().size)
        assertEquals("During fetch", db.goalDao().getAllGoals().first().single().name)
        assertEquals("Opening", db.transactionDao().getAllTransactionsList().single().description)
    }
}
