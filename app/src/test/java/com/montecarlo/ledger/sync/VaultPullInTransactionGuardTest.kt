package com.montecarlo.ledger.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.PaymentEntity
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
class VaultPullInTransactionGuardTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setUp() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
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
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun vaultPullRestore_throwsInsideTransaction_whenGoalPresent() = runBlocking {
        repo.insertGoal(GoalEntity(name = "Stay", targetAmountCents = 1, currentAmountCents = 0))
        val json = repo.exportCrossPlatformVaultJson("2026-11-11")
        assertThrows(VaultLocalChangesException::class.java) {
            runBlocking {
                repo.restoreCrossPlatformVaultJsonForVaultPull(
                    json,
                    VaultPullRestoreGuardState(
                        allowLocalOverwrite = false,
                        ancestryEstablished = true,
                        syncedFingerprint = VaultSnapshotFingerprint.ofSnapshotJson(json),
                    ),
                )
            }
        }
        assertEquals(1, db.goalDao().getAllGoals().first().size)
        assertEquals("Stay", db.goalDao().getAllGoals().first().single().name)
    }

    @Test
    fun vaultPullRefusesToEraseLinkedDebtThatFormatCannotRepresent() = runBlocking {
        val remoteSnapshot = repo.exportCrossPlatformVaultJson("2026-11-11")
        db.paymentDao().insert(
            PaymentEntity(
                id = 7,
                name = "Card payment",
                amount_cents = 2_500,
                frequency = "Monthly",
                day_of_month = 10,
                next_date = "2026-12-10",
            ),
        )
        db.debtDao().insert(
            DebtEntity(
                id = 11,
                name = "Card",
                balanceCents = 50_000,
                aprBasisPoints = 1_900,
                minimumPaymentCents = 2_500,
                dueDayOfMonth = 10,
                linkedPaymentId = 7,
            ),
        )
        val debtBefore = db.debtDao().getAll().first()
        val transactionsBefore = db.transactionDao().getAllTransactionsList()

        assertThrows(VaultLocalChangesException::class.java) {
            runBlocking {
                repo.restoreCrossPlatformVaultJsonForVaultPull(
                    remoteSnapshot,
                    VaultPullRestoreGuardState(
                        allowLocalOverwrite = false,
                        ancestryEstablished = true,
                        syncedFingerprint = VaultSnapshotFingerprint.ofSnapshotJson(remoteSnapshot),
                    ),
                )
            }
        }
        assertEquals(debtBefore, db.debtDao().getAll().first())
        assertEquals(transactionsBefore, db.transactionDao().getAllTransactionsList())
    }
}
