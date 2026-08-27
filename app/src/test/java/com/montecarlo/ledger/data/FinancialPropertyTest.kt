package com.montecarlo.ledger.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.processing.DebtItem
import com.montecarlo.ledger.processing.DebtPayoffEngine
import com.montecarlo.ledger.processing.PayoffStrategy
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
import java.time.LocalDate
import kotlin.random.Random

/**
 * Generative / property-style financial invariants over a real Room database.
 *
 * Every test is seeded so failures are byte-for-byte reproducible. These assert
 * conservation properties that catch entire classes of ledger corruption:
 *   - add then delete restores prior derived state
 *   - editing amount A->B changes totals by exactly B-A
 *   - pending/posted transitions never double count
 *   - categorization changes buckets but never overall cash flow
 *   - backup -> restore preserves logical equality
 *   - a valid debt payoff sequence never drives a balance negative
 *   - recurring generation is idempotent across restarts
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FinancialPropertyTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository
    private lateinit var rng: Random

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
        rng = Random(seed = 20260827L)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun randomSignedAmount(type: String): Long {
        val magnitude = 1L + rng.nextLong(5_000_000L)
        return when (type) {
            "income" -> magnitude
            "expense" -> -magnitude
            else -> if (rng.nextBoolean()) magnitude else -magnitude
        }
    }

    private fun randomDescription(i: Int): String {
        val words = listOf("Coffee", "Groceries", "Rent", "Transit", "Salary", "Refund", "Utilities", "Dining")
        return "${words[rng.nextInt(words.size)]} #$i"
    }

    private fun randomCategory(): String {
        val categories = listOf("food", "groceries", "transport", "bills", "income", "shopping", "health")
        return categories[rng.nextInt(categories.size)]
    }

    private fun randomTransaction(i: Int): TransactionEntity {
        val type = when (rng.nextInt(3)) {
            0 -> "income"
            1 -> "expense"
            else -> "adjustment"
        }
        return TransactionEntity(
            description = randomDescription(i),
            amount_cents = randomSignedAmount(type),
            date = LocalDate.of(2026, 1 + rng.nextInt(8), 1 + rng.nextInt(28)).toString(),
            type = type,
            category = randomCategory(),
        )
    }

    private suspend fun insertRandomLedger(count: Int): List<TransactionEntity> {
        val inserted = mutableListOf<TransactionEntity>()
        for (i in 0 until count) {
            val txn = randomTransaction(i)
            repo.insertTransaction(txn)
            inserted += db.transactionDao().getAll().first().last { it.description == txn.description }
        }
        return inserted
    }

    @Test
    fun addingThenDeletingEveryTransaction_restoresStartingBalance() = runBlocking {
        repo.setBankBalance(100_000L)
        val initial = repo.getBankBalanceCents()

        val txns = insertRandomLedger(40)

        val sumAfterInsert = repo.getBankBalanceCents()
        assertEquals(
            "reconciled balance must track the transaction sum exactly",
            initial + txns.sumOf { it.amount_cents },
            sumAfterInsert,
        )

        for (txn in txns) {
            repo.deleteTransaction(db.transactionDao().getById(txn.id)!!)
        }

        assertEquals("deleting every transaction must restore the starting balance", initial, repo.getBankBalanceCents())
        assertTrue(db.transactionDao().getAll().first().isEmpty())
    }

    @Test
    fun editingAmount_changesTotalsByExactlyTheDelta() = runBlocking {
        repo.setBankBalance(50_000L)
        val txn = randomTransaction(0)
        repo.insertTransaction(txn)
        val stored = db.transactionDao().getAll().first().single()
        val before = repo.getBankBalanceCents()

        val newAmount = stored.amount_cents + 123_456L
        repo.updateTransaction(stored.copy(amount_cents = newAmount))

        assertEquals(
            "balance must move by exactly new - old",
            before + (newAmount - stored.amount_cents),
            repo.getBankBalanceCents(),
        )
    }

    @Test
    fun pendingToPostedTransitions_neverDoubleCountAnAmount() = runBlocking {
        repo.setBankBalance(25_000L)
        val txn = randomTransaction(0).copy(type = "expense", amount_cents = -500L)
        repo.insertTransaction(txn)
        val id = db.transactionDao().getAll().first().single().id
        val afterInsert = repo.getBankBalanceCents()

        repo.setTransactionClearingStatus(id, pending = true)
        repo.setTransactionClearingStatus(id, pending = false)
        repo.setTransactionClearingStatus(id, pending = true)
        repo.setTransactionClearingStatus(id, pending = false)

        assertEquals("clearing-state flips are pure and must not re-apply the amount", afterInsert, repo.getBankBalanceCents())
    }

    @Test
    fun changingCategory_movesBucketsButNotOverallCashFlow() = runBlocking {
        repo.setBankBalance(0L)
        val txns = insertRandomLedger(20)
        val before = repo.getBankBalanceCents()

        for (txn in txns) {
            val stored = db.transactionDao().getById(txn.id)!!
            repo.updateTransaction(stored.copy(category = "recategorized"))
        }

        assertEquals("recategorization must not change overall cash flow", before, repo.getBankBalanceCents())
    }

    @Test
    fun backupRestore_preservesLogicalEqualityAcrossARandomLedger() = runBlocking {
        repo.setBankBalance(75_000L)
        insertRandomLedger(30)
        repo.insertPayment(
            PaymentEntity(
                name = "Bill A",
                amount_cents = 12_000L,
                frequency = "Monthly",
                day_of_month = 15,
                next_date = LocalDate.now().toString(),
            )
        )
        repo.insertIncome(
            IncomeEntity(
                name = "Salary",
                amount_cents = 200_000L,
                frequency = "Monthly",
                day_of_month = 1,
                next_date = LocalDate.now().toString(),
                payType = "FLAT",
            )
        )
        repo.syncBillOccurrences()

        val snapshot = LedgerBackupSnapshot(
            schemaVersion = 6,
            exportedAtIso = "2026-08-27T00:00:00",
            bankBalanceCents = repo.getBankBalanceCents(),
            isBalanceReconciled = repo.isBalanceReconciled(),
            onboardingProgress = repo.onboardingProgress.first(),
            settings = repo.allSettings.first(),
            rules = repo.allTransactionRules.first(),
            incomes = repo.allIncome.first(),
            payments = repo.allPayments.first(),
            transactions = repo.allTransactions.first(),
            billOccurrences = repo.allBillOccurrences.first(),
            assets = repo.allAssets.first(),
            goals = repo.allGoals.first(),
            categoryBudgets = repo.allCategoryBudgets.first(),
            debts = repo.allDebts.first(),
            accounts = repo.allAccounts.first(),
        )

        val fresh = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        try {
            val freshRepo = LedgerRepository(fresh)
            freshRepo.restoreBackup(snapshot)

            assertEquals(snapshot.bankBalanceCents, freshRepo.getBankBalanceCents())
            assertEquals(snapshot.isBalanceReconciled, freshRepo.isBalanceReconciled())
            assertEquals(
                snapshot.transactions.sortedBy { it.id }.map { it to it.amount_cents },
                fresh.transactionDao().getAll().first().sortedBy { it.id }.map { it to it.amount_cents },
            )
            assertEquals(snapshot.incomes.map { it.name }, fresh.incomeDao().getAllIncomes().first().map { it.name })
            assertEquals(snapshot.payments.map { it.name }, fresh.paymentDao().getAll().first().map { it.name })
            assertEquals(snapshot.billOccurrences.size, fresh.billOccurrenceDao().getAll().first().size)
            assertEquals(snapshot.accounts.size, fresh.accountDao().getAll().first().size)
            assertEquals(
                snapshot.transactions.sumOf { it.amount_cents },
                fresh.transactionDao().getTotalBalanceCents().first() ?: 0L,
            )
        } finally {
            fresh.close()
        }
    }

    @Test
    fun debtPayoff_neverDrivesAnyBalanceNegativeForSeededLedgers() = runBlocking {
        for (iteration in 0 until 5) {
            val debts = (0 until (2 + rng.nextInt(4))).map { i ->
                val revolving = rng.nextBoolean()
                DebtItem(
                    id = i.toLong(),
                    name = "Debt $i",
                    balanceCents = 1_000L + rng.nextLong(1_000_000L),
                    aprBasisPoints = 0 + rng.nextInt(3_000),
                    minPaymentCents = if (revolving) 0L else 500L + rng.nextLong(50_000L),
                    kind = if (revolving) DebtKind.REVOLVING else DebtKind.INSTALLMENT,
                    minPaymentPercentBps = if (revolving) 100 + rng.nextInt(400) else 0,
                    minPaymentFloorCents = if (revolving) 500L else 0L,
                )
            }

            val summary = DebtPayoffEngine.simulateSchedule(
                debts = debts,
                extraMonthlyPaymentCents = 0L,
                strategy = PayoffStrategy.AVALANCHE,
                startDate = LocalDate.of(2026, 1, 15),
            )

            assertTrue(
                "iteration $iteration: a valid payoff sequence must never end a month negative",
                summary.monthlySchedule.all { it.endingBalanceCents >= 0L }
            )
            assertTrue(
                "iteration $iteration: total paid can never exceed starting balances plus interest",
                summary.totalPaidCents <= debts.sumOf { it.balanceCents } + summary.totalInterestCents
            )
        }
    }

    @Test
    fun recurringGeneration_isIdempotentAcrossRestarts() = runBlocking {
        val payments = (0 until 5).map { i ->
            PaymentEntity(
                name = "Payment $i",
                amount_cents = 1_000L + rng.nextLong(100_000L),
                frequency = listOf("Weekly", "Bi-weekly", "Monthly", "Semi-monthly")[rng.nextInt(4)],
                day_of_month = 1 + rng.nextInt(28),
                next_date = LocalDate.now().toString(),
            )
        }
        payments.forEach { repo.insertPayment(it) }

        repo.syncBillOccurrences()
        val first = repo.allBillOccurrences.first()
        repo.syncBillOccurrences()
        repo.syncBillOccurrences()
        val after = repo.allBillOccurrences.first()

        assertEquals("restart must not duplicate occurrences", first.size, after.size)
        val keyCounts = after.groupingBy { "${it.payment_id}|${it.due_date}" }.eachCount()
        assertTrue(
            "no (payment, due_date) pair may appear twice after repeated syncs",
            keyCounts.values.all { it == 1 }
        )
    }
}