package com.montecarlo.ledger.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultPullConcurrentMutationTest {

    private lateinit var fakeServer: FakeVaultHttpServer
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private val roomExecutor = Executors.newSingleThreadExecutor()

    private val vaultId = "latency-guard"
    private val passphrase = "latency-pass".toCharArray()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fakeServer = FakeVaultHttpServer().also { it.start() }
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .setQueryExecutor(roomExecutor)
            .setTransactionExecutor(roomExecutor)
            .build()
        roomExecutor.submit {
            runBlocking {
                repo = LedgerRepository(db)
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
                CloudVaultSync(context, repo).createVault(vaultId, passphrase, fakeServer.baseUrl)
            }
        }.get(30, TimeUnit.SECONDS)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        if (::fakeServer.isInitialized) fakeServer.stop()
        roomExecutor.shutdownNow()
    }

    @Test
    fun pull_revalidatesBeforeRestore_whenLocalRiskAppearsDuringNetworkDelay() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fakeServer.beginBlockingVaultFetch()
        val pullFuture = roomExecutor.submit<Result<Int>> {
            runBlocking {
                runCatching {
                    CloudVaultSync(context, repo).pullVault(
                        vaultId,
                        passphrase,
                        fakeServer.baseUrl,
                        allowLocalOverwrite = false,
                    )
                }
            }
        }
        fakeServer.awaitVaultFetchEntered()
        roomExecutor.submit {
            runBlocking {
                db.goalDao().insertGoal(
                    GoalEntity(name = "During fetch", targetAmountCents = 1, currentAmountCents = 0),
                )
            }
        }.get(10, TimeUnit.SECONDS)
        fakeServer.releaseBlockedVaultFetch()
        val result = pullFuture.get(30, TimeUnit.SECONDS)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VaultLocalChangesException)
        roomExecutor.submit {
            runBlocking {
                assertEquals(1, db.goalDao().getAllGoals().first().size)
                assertEquals("During fetch", db.goalDao().getAllGoals().first().single().name)
            }
        }.get(10, TimeUnit.SECONDS)
    }
}
