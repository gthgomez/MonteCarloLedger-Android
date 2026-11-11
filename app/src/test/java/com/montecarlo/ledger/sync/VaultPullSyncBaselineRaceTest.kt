package com.montecarlo.ledger.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultPullSyncBaselineRaceTest {

    private lateinit var fakeServer: FakeVaultHttpServer
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var sync: CloudVaultSync
    private lateinit var remoteCoreAtVaultCreate: String
    private val vaultId = "baseline-race"
    private val passphrase = "baseline-pass".toCharArray()
    private val orchestration = Executors.newSingleThreadExecutor()

    @Before
    fun setUp() {
        runBlocking {
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
                    amount_cents = 100_000,
                    date = "2026-11-01",
                    type = "Adjustment",
                    category = "System",
                ),
            )
            repo.insertIncome(
                IncomeEntity(
                    id = 1,
                    name = "Salary",
                    amount_cents = 50_000,
                    frequency = "Monthly",
                    day_of_month = null,
                    next_date = "2026-11-01",
                ),
            )
            sync.createVault(vaultId, passphrase, fakeServer.baseUrl)
            remoteCoreAtVaultCreate = VaultSnapshotFingerprint.ofSnapshotJson(
                repo.exportCrossPlatformVaultJson("remote-ref"),
            )
        }
    }

    @After
    fun tearDown() {
        sync.afterRestoreBeforePersistSyncStateBarrier = null
        orchestration.shutdownNow()
        if (::db.isInitialized) db.close()
        if (::fakeServer.isInitialized) fakeServer.stop()
    }

    @Test
    fun pull_persistsRemoteCoreFingerprint_notPostBarrierLocalEdit() {
        val incomeBefore = repo.allIncome.first().single()
        repo.updateIncome(incomeBefore.copy(amount_cents = 75_000))
        sync.afterRestoreBeforePersistSyncStateBarrier = {
            val current = repo.allIncome.first().single()
            repo.updateIncome(current.copy(name = "Edited in barrier", amount_cents = 99_000))
        }
        runBlocking {
            sync.pullVault(vaultId, passphrase, fakeServer.baseUrl, allowLocalOverwrite = true)
        }
        val store = VaultSyncStore(ApplicationProvider.getApplicationContext())
        assertEquals(remoteCoreAtVaultCreate, store.syncedFingerprint)
        assertNotEquals(
            VaultDestructivePullGuard.currentCoreFingerprint(repo),
            store.syncedFingerprint,
        )
        val edited = repo.allIncome.first().single()
        assertEquals("Edited in barrier", edited.name)
        assertEquals(99_000, edited.amount_cents)
        assertThrows(VaultLocalChangesException::class.java) {
            runBlocking {
                sync.pullVault(vaultId, passphrase, fakeServer.baseUrl, allowLocalOverwrite = false)
            }
        }
        assertEquals("Edited in barrier", repo.allIncome.first().single().name)
    }

    @Test
    fun pull_cleanControl_commitsRemoteBaselinesMatchingVaultPayload() {
        runBlocking {
            sync.pullVault(vaultId, passphrase, fakeServer.baseUrl, allowLocalOverwrite = true)
        }
        val store = VaultSyncStore(ApplicationProvider.getApplicationContext())
        assertEquals(remoteCoreAtVaultCreate, store.syncedFingerprint)
        assertEquals(
            VaultDestructivePullGuard.currentCoreFingerprint(repo),
            store.syncedFingerprint,
        )
    }
}
