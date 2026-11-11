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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/**
 * Pull holds no repository mutex during HTTP; a competing [LedgerRepository.insertGoal] while the
 * vault fetch is blocked must still fail the in-transaction pre-restore guard (not delete the goal).
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
    private val dbExecutor = Executors.newFixedThreadPool(2)
    private val orchestrationExecutor = Executors.newFixedThreadPool(2)

    @Before
    fun setUp() {
        dbExecutor.submit {
            runBlocking {
                val context = ApplicationProvider.getApplicationContext<android.content.Context>()
                fakeServer = FakeVaultHttpServer().also { it.start() }
                db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                    .setQueryExecutor(dbExecutor)
                    .setTransactionExecutor(dbExecutor)
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
        }.get(30, TimeUnit.SECONDS)
    }

    @After
    fun tearDown() {
        orchestrationExecutor.shutdownNow()
        dbExecutor.shutdownNow()
        if (::db.isInitialized) db.close()
        if (::fakeServer.isInitialized) fakeServer.stop()
    }

    @Test
    fun pull_blocksWhenGoalInsertedDuringBlockedVaultFetch_withoutDeletingGoal() {
        fakeServer.beginBlockingVaultFetch()
        val pullTask = FutureTask(
            java.util.concurrent.Callable {
                runBlocking {
                    runCatching {
                        sync.pullVault(vaultId, passphrase, fakeServer.baseUrl, allowLocalOverwrite = false)
                    }
                }
            },
        )
        orchestrationExecutor.execute(pullTask)
        fakeServer.awaitVaultFetchEntered()
        orchestrationExecutor.submit {
            runBlocking {
                repo.insertGoal(
                    GoalEntity(name = "During fetch", targetAmountCents = 1, currentAmountCents = 0),
                )
            }
        }.get(15, TimeUnit.SECONDS)
        fakeServer.releaseBlockedVaultFetch()
        val result = pullTask.get(30, TimeUnit.SECONDS)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VaultLocalChangesException)
        dbExecutor.submit {
            runBlocking {
                assertEquals(1, db.goalDao().getAllGoals().first().size)
                assertEquals("During fetch", db.goalDao().getAllGoals().first().single().name)
                assertEquals("Opening", db.transactionDao().getAllTransactionsList().single().description)
            }
        }.get(15, TimeUnit.SECONDS)
        assertTrue(
            fakeServer.fetchVaultInvocationCount() >= 1,
        )
    }
}
