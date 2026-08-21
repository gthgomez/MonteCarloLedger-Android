package com.montecarlo.ledger.ui

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Full export -> wipe -> restore equivalence test over a real Room database.
 *
 * This is the strongest data-integrity guarantee the app can make in CI: a backup
 * produced from a populated ledger must restore into an equivalent ledger, even
 * after the database is dirtied between export and restore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreIntegrationTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
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

    private suspend fun populateLedger() {
        repo.setBankBalance(50_000)

        repo.insertIncome(
            IncomeEntity(
                id = 0,
                name = "Salary",
                amount_cents = 200_000,
                frequency = "Bi-weekly",
                day_of_month = null,
                next_date = "2026-09-04",
                payType = "FLAT",
            )
        )
        repo.insertPayment(
            com.montecarlo.ledger.data.PaymentEntity(
                name = "Rent",
                amount_cents = 100_000,
                frequency = "Monthly",
                day_of_month = 1,
                next_date = "2026-09-01",
            )
        )
        repo.syncBillOccurrences()
        repo.insertTransaction(
            TransactionEntity(
                description = "Groceries",
                amount_cents = -4_500,
                date = "2026-08-10",
                type = "expense",
                category = "groceries",
            )
        )
        repo.importTransactions(
            listOf(
                TransactionEntity(
                    description = "Transit tap",
                    amount_cents = -275,
                    date = "2026-08-11",
                    type = "expense",
                    category = "transport",
                ),
                TransactionEntity(
                    description = "Freelance payout",
                    amount_cents = 75_000,
                    date = "2026-08-12",
                    type = "income",
                    category = "income",
                ),
            )
        )
        repo.saveTransactionRule("netflix", "subscriptions")
        repo.insertGoal(GoalEntity(id = 0, name = "Emergency fund", targetAmountCents = 100_000, currentAmountCents = 10_000, deadline = null, createdAt = "2026-08-01"))
        repo.insertAsset(com.montecarlo.ledger.data.AssetEntity(name = "Index fund", type = "Stock", balanceCents = 250_000, lastUpdated = "2026-08-01"))
        repo.upsertCategoryBudget(com.montecarlo.ledger.data.CategoryBudgetEntity(id = 0, category = "groceries", limitCents = 40_000, enabled = 1, createdAt = "2026-08-01"))
        repo.insertDebt(com.montecarlo.ledger.data.DebtEntity(id = 0, name = "Card", balanceCents = 60_000, aprBasisPoints = 1899, minimumPaymentCents = 2_500, dueDayOfMonth = 12, linkedPaymentId = null, isActive = true))
    }

    @Test
    fun exportThenRestoreIntoDirtyDatabaseReproducesTheLedger() = runBlocking {
        populateLedger()

        // ---- snapshot the pre-export state across every table ----
        val beforeIncomes = repo.allIncome.first()
        val beforePayments = repo.allPayments.first()
        val beforeTxns = repo.allTransactions.first().sortedBy { it.id }
        val beforeOccurrences = repo.allBillOccurrences.first().sortedBy { it.id }
        val beforeGoals = repo.allGoals.first()
        val beforeAssets = repo.allAssets.first()
        val beforeBudgets = repo.allCategoryBudgets.first()
        val beforeRules = repo.allTransactionRules.first()
        val beforeDebts = repo.allDebts.first()
        val beforeSettings = repo.allSettings.first().associate { it.key to it.value }

        // ---- export ----
        val json = buildLedgerBackupJson(
            exportedAtIso = "2026-08-21T09:00:00",
            uiState = AppUiState(bankBalanceCents = beforeSettings["bank_balance_cents"]?.toLongOrNull() ?: 0L),
            incomes = beforeIncomes,
            payments = beforePayments,
            transactions = beforeTxns,
            billOccurrences = beforeOccurrences,
            onboardingProgress = OnboardingProgress(),
            settings = repo.allSettings.first(),
            rules = beforeRules,
            assets = beforeAssets,
            goals = beforeGoals,
            categoryBudgets = beforeBudgets,
            debts = beforeDebts,
        )

        // ---- dirty the database after exporting ----
        repo.setBankBalance(1_000)
        repo.insertIncome(
            IncomeEntity(id = 0, name = "Ghost income", amount_cents = 999, frequency = "Monthly", day_of_month = null, next_date = "2026-10-01")
        )
        repo.deleteTransaction(beforeTxns.first { it.description == "Groceries" })

        // ---- restore ----
        val snapshot = parseLedgerBackupJson(json)
        repo.restoreBackup(snapshot)
        repo.ensureDefaultAccountSeeded()

        // ---- assert equivalence with the pre-export state ----
        assertEquals(beforeIncomes.map { it.name }, repo.allIncome.first().map { it.name })
        assertEquals(beforePayments.map { it.name }, repo.allPayments.first().map { it.name })
        assertEquals(beforeTxns.size, repo.allTransactions.first().size)
        assertEquals(beforeTxns.map { it.description }, repo.allTransactions.first().sortedBy { it.id }.map { it.description })
        assertEquals(beforeOccurrences.size, repo.allBillOccurrences.first().size)
        assertEquals(beforeGoals.single().name, repo.allGoals.first().single().name)
        assertEquals(beforeAssets.single().name, repo.allAssets.first().single().name)
        assertEquals(beforeBudgets.single().limitCents, repo.allCategoryBudgets.first().single().limitCents)
        assertEquals(beforeRules.single().match_text, repo.allTransactionRules.first().single().match_text)
        assertEquals(beforeDebts.single().balanceCents, repo.allDebts.first().single().balanceCents)

        // Reconciliation runs in lockstep, so the pre-export balance already reflects
        // every populated transaction; restore must land on exactly that value.
        val expectedBankCents = beforeSettings["bank_balance_cents"]?.toLongOrNull() ?: 0L
        assertEquals(expectedBankCents, repo.getBankBalanceCents())
        assertTrue(repo.isBalanceReconciled())

        // App-lock state is never exported: restoring must not invent one.
        val keys = repo.allSettings.first().map { it.key }
        assertTrue(keys.none { it.startsWith("app_lock_") })

        // The default account mirrors the restored primary balance.
        val account = db.accountDao().getDefault()
        assertNotNull(account)
        assertEquals(expectedBankCents, account!!.balanceCents)
        assertTrue(account.isReconciled)

        // Occurrence links survive: the rent occurrence keeps its bill record intact.
        val occurrence = repo.allBillOccurrences.first().firstOrNull()
        assertNotNull(occurrence)
        assertNull(occurrence!!.transaction_id)
    }
}
