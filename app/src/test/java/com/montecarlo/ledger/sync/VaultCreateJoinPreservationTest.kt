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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultCreateJoinPreservationTest {

    private lateinit var fakeServer: FakeVaultHttpServer
    private val vaultId = "join-guard"
    private val passphrase = "join-pass".toCharArray()

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            fakeServer = FakeVaultHttpServer().also { it.start() }
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val repo = LedgerRepository(db)
            repo.ensureDefaultAccountSeeded()
            db.transactionDao().insert(
                TransactionEntity(
                    id = 1,
                    description = "Opening",
                    amount_cents = 50,
                    date = "2026-11-01",
                    type = "Adjustment",
                    category = "System",
                ),
            )
            CloudVaultSync(context, repo).createVault(vaultId, passphrase, fakeServer.baseUrl)
            db.close()
        }
    }

    @After
    fun tearDown() {
        if (::fakeServer.isInitialized) fakeServer.stop()
    }

    @Test
    fun createJoin_existingVaultBlockedWhenAndroidOnlyGoalPresent() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val repo = LedgerRepository(db)
            repo.ensureDefaultAccountSeeded()
            db.transactionDao().insert(
                TransactionEntity(
                    id = 1,
                    description = "Opening",
                    amount_cents = 50,
                    date = "2026-11-01",
                    type = "Adjustment",
                    category = "System",
                ),
            )
            db.goalDao().insertGoal(GoalEntity(name = "Keep", targetAmountCents = 1, currentAmountCents = 0))
            val sync = CloudVaultSync(context, repo)
            assertThrows(VaultLocalChangesException::class.java) {
                runBlocking {
                    sync.createVault(vaultId, passphrase, fakeServer.baseUrl)
                }
            }
            assertEquals(1, db.goalDao().getAllGoals().first().size)
            assertEquals("Keep", db.goalDao().getAllGoals().first().single().name)
            db.close()
        }
    }
}
