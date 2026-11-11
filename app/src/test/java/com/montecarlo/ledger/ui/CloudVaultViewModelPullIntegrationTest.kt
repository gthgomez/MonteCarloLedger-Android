package com.montecarlo.ledger.ui

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.sync.CloudVaultSync
import com.montecarlo.ledger.sync.FakeVaultHttpServer
import com.montecarlo.ledger.sync.VaultLocalChangesException
import com.montecarlo.ledger.sync.VaultSyncStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ViewModel + Room path (not full [CloudVaultDialog] Compose). Dialog cancel/confirm wiring remains
 * callback-tested in [DestructiveVaultPullConfirmDialogTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudVaultViewModelPullIntegrationTest {

    private lateinit var fakeServer: FakeVaultHttpServer
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var viewModel: MainViewModel
    private val vaultId = "vm-pull"
    private val passphrase = "vm-pass"

    @Before
    fun setUp() {
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<Application>()
            fakeServer = FakeVaultHttpServer().also { it.start() }
            db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            repo = LedgerRepository(db)
            viewModel = MainViewModel(app, db)
            repo.ensureDefaultAccountSeeded()
            db.transactionDao().insert(
                TransactionEntity(
                    id = 1,
                    description = "Opening",
                    amount_cents = 10_000,
                    date = "2026-11-01",
                    type = "Adjustment",
                    category = "System",
                ),
            )
            CloudVaultSync(app, repo).createVault(vaultId, passphrase.toCharArray(), fakeServer.baseUrl)
            VaultSyncStore(app).commitSyncState(
                vaultId = vaultId,
                serverUrl = fakeServer.baseUrl,
                revision = 1,
                syncedFingerprint = com.montecarlo.ledger.sync.VaultDestructivePullGuard.currentCoreFingerprint(repo)!!,
                androidPreservationFingerprint = com.montecarlo.ledger.sync.LocalLedgerPreservation.fingerprint(repo),
                ancestryEstablished = true,
            )
        }
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        if (::fakeServer.isInitialized) fakeServer.stop()
    }

    @Test
    fun viewModelPull_guardedPath_preservesGoalWhenBlocked() {
        runBlocking {
            repo.insertGoal(GoalEntity(name = "Keep", targetAmountCents = 1, currentAmountCents = 0))
            assertThrows(VaultLocalChangesException::class.java) {
                runBlocking {
                    viewModel.pullCloudVault(vaultId, passphrase, fakeServer.baseUrl, allowOverwrite = false)
                }
            }
            assertEquals("Keep", repo.allGoals.first().single().name)
        }
    }
}
