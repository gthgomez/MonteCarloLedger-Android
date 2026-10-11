package com.montecarlo.ledger.dashboard

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.data.AccountEntity
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * F1 end-to-end guard: the dashboard's cash ledger must route transactions exactly like
 * [LedgerRepository]'s transaction side effects — by the account's TYPE, never by whether some
 * debt happens to be linked to that account.
 *
 * Each case records what the real repository did to the bank balance / debts, then asserts the
 * dashboard agrees, so the two halves can never silently diverge again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CashLedgerRoutingIntegrationTest {

    private val today = LocalDate.of(2026, 8, 21)
    private lateinit var db: AppDatabase
    private lateinit var repo: LedgerRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = LedgerRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun pack() = ReportingPackage(
        incomes = repo.allIncome.first(),
        payments = repo.allPayments.first(),
        txns = repo.allTransactions.first(),
        accounts = repo.allAccounts.first(),
        balanceState = repo.balanceState.first(),
        billOccurrences = repo.allBillOccurrences.first(),
        assets = repo.allAssets.first(),
        goals = repo.allGoals.first(),
        categoryBudgets = repo.allCategoryBudgets.first(),
        debts = repo.allDebts.first(),
        rules = repo.allTransactionRules.first(),
        dashboardConfig = DashboardConfig(),
    )

    private suspend fun insertAccount(name: String, type: String): Long = repo.insertAccount(
        AccountEntity(
            name = name,
            type = type,
            balanceCents = 0L,
            isReconciled = false,
            isDefault = false,
            lastUpdated = "2026-08-21",
        )
    )

    @Test
    fun creditAccountWithoutLinkedDebt_movesNothingAndStaysOutOfCash() = runBlocking {
        repo.setBankBalance(0L)
        val creditAccountId = insertAccount("Visa", "credit")
        repo.insertTransaction(
            TransactionEntity(
                description = "Card purchase",
                amount_cents = -5_000L,
                date = "2026-08-12",
                type = "expense",
                category = "shopping",
                account_id = creditAccountId,
            )
        )

        // The repository wrote neither the bank nor a debt for this charge.
        assertEquals(0L, repo.balanceState.first().bankBalanceCents)
        assertTrue(repo.allDebts.first().isEmpty())

        val derivation = DashboardDeriver().derive(pack(), today)

        // Cash ledger agrees with the confirmed bank balance, and no phantom liability exists.
        assertFalse(derivation.reconciliationMismatch)
        assertEquals(0L, derivation.uiState.totalNetWorthCents)
    }

    @Test
    fun nonCreditAccountWithLinkedDebt_writesTheBankAndStaysInCash() = runBlocking {
        repo.setBankBalance(0L)
        val checkingId = insertAccount("Everyday", "checking")
        repo.insertDebt(
            DebtEntity(
                name = "Instalment loan",
                balanceCents = 0L,
                aprBasisPoints = 599,
                minimumPaymentCents = 1_000L,
                dueDayOfMonth = 20,
                linkedAccountId = checkingId,
                isActive = true,
            )
        )
        repo.insertTransaction(
            TransactionEntity(
                description = "Groceries",
                amount_cents = -2_500L,
                date = "2026-08-10",
                type = "expense",
                category = "food",
                account_id = checkingId,
            )
        )

        // The repository moved the bank: a debt link does not make a non-credit account a card.
        assertEquals(-2_500L, repo.balanceState.first().bankBalanceCents)
        assertEquals(0L, repo.allDebts.first().single().balanceCents)

        val derivation = DashboardDeriver().derive(pack(), today)

        // So the dashboard must not report drift for the very same transaction.
        assertFalse(derivation.reconciliationMismatch)
        assertEquals(-2_500L, derivation.uiState.totalNetWorthCents)
    }

    @Test
    fun untaggedTransactionsStayCash() = runBlocking {
        repo.setBankBalance(0L)
        val creditAccountId = insertAccount("Visa", "credit")
        repo.insertDebt(
            DebtEntity(
                name = "Visa",
                balanceCents = 0L,
                aprBasisPoints = 1_999,
                minimumPaymentCents = 2_500L,
                dueDayOfMonth = 15,
                linkedAccountId = creditAccountId,
                isActive = true,
            )
        )
        repo.insertTransaction(
            TransactionEntity(
                description = "Groceries",
                amount_cents = -1_500L,
                date = "2026-08-10",
                type = "expense",
                category = "food",
            )
        )

        assertEquals(-1_500L, repo.balanceState.first().bankBalanceCents)

        val derivation = DashboardDeriver().derive(pack(), today)

        assertFalse(derivation.reconciliationMismatch)
        assertEquals(-1_500L, derivation.uiState.totalNetWorthCents)
    }
}
