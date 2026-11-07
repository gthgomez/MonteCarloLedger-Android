package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.data.AccountEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.util.centsToDisplay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardDeriverTest {

    private val today = LocalDate.of(2026, 8, 21)

    /** Id of the credit account that a linked, active debt routes its charges into. */
    private val creditAccountId = 42L

    /** The credit account itself: its TYPE is what makes charges non-cash. */
    private val creditAccount = AccountEntity(
        id = creditAccountId,
        name = "Visa",
        type = "credit",
        balanceCents = 0L,
        isReconciled = false,
        isDefault = false,
        lastUpdated = "2026-08-21",
    )

    private fun emptyPack(
        txns: List<TransactionEntity> = emptyList(),
        accounts: List<AccountEntity> = emptyList(),
    ) = ReportingPackage(
        incomes = emptyList(),
        payments = emptyList(),
        txns = txns,
        accounts = accounts,
        balanceState = LedgerRepository.BalanceState(bankBalanceCents = 0L, isReconciled = false),
        billOccurrences = emptyList(),
        assets = emptyList(),
        goals = emptyList(),
        categoryBudgets = emptyList(),
        debts = emptyList(),
        rules = emptyList(),
        dashboardConfig = DashboardConfig(),
    )

    @Test
    fun derive_emptyLedgerProducesDefaultsAndDefaultBasisLabel() = runBlocking {
        val derivation = DashboardDeriver().derive(emptyPack(), today)

        assertFalse(derivation.uiState.isLoading)
        assertFalse(derivation.reconciliationMismatch)
        assertEquals(null, derivation.reconciliationDetails)
        assertTrue(derivation.uiState.monteCarloBasisLabel!!.startsWith("Default assumptions"))
        // Contract MCD-0023: simulation is defined for an empty ledger too; surprise expenses
        // give a non-zero overdraft probability rather than a fabricated 0%.
        assertTrue(derivation.uiState.probabilityNegativePct > 0.0)
        assertTrue(derivation.uiState.probabilityNegativePct <= 100.0)
    }

    @Test
    fun derive_calibratesBasisLabelFromHistoryMonths() = runBlocking {
        val txns = mutableListOf<TransactionEntity>()
        for (month in 1..5) {
            repeat(8) { i ->
                txns += TransactionEntity(
                    description = "Coffee $i",
                    amount_cents = -500L,
                    date = "2026-0$month-05",
                    type = "expense",
                    category = "food",
                )
            }
            txns += TransactionEntity(
                description = "Rent",
                amount_cents = -100_000L,
                date = "2026-0$month-01",
                type = "expense",
                category = "bills",
            )
        }

        val derivation = DashboardDeriver().derive(emptyPack(txns), today)

        assertEquals("Based on 5 months of your history", derivation.uiState.monteCarloBasisLabel)
        assertTrue(derivation.uiState.transactions.isNotEmpty())
    }

    @Test
    fun derive_reportsReconciliationMismatchWhenBankDisagreesWithLedger() = runBlocking {
        val txns = listOf(
            TransactionEntity(
                description = "Groceries",
                amount_cents = -1_500L,
                date = "2026-08-10",
                type = "expense",
                category = "food",
            )
        )
        val pack = emptyPack(txns).copy(
            balanceState = LedgerRepository.BalanceState(bankBalanceCents = 99_999L, isReconciled = true),
        )

        val derivation = DashboardDeriver().derive(pack, today)

        assertTrue(derivation.reconciliationMismatch)
        assertEquals(Pair(-1_500L, 99_999L), derivation.reconciliationDetails)
        assertTrue(derivation.uiState.bankLedgerMismatch)
    }

    @Test
    fun derive_creditAccountChargeReducesNetWorthByChargeOnce() = runBlocking {
        val cardCharge = TransactionEntity(
            description = "Card purchase",
            amount_cents = -6_000L,
            date = "2026-08-12",
            type = "expense",
            category = "shopping",
            account_id = creditAccountId,
        )
        val creditDebt = DebtEntity(
            name = "Visa",
            balanceCents = 6_000L,
            aprBasisPoints = 1_999,
            minimumPaymentCents = 2_500L,
            dueDayOfMonth = 15,
            linkedAccountId = creditAccountId,
            isActive = true,
        )

        val charged = DashboardDeriver().derive(
            emptyPack(listOf(cardCharge), accounts = listOf(creditAccount))
                .copy(debts = listOf(creditDebt)),
            today,
        )
        val uncharged = DashboardDeriver().derive(
            emptyPack(accounts = listOf(creditAccount))
                .copy(debts = listOf(creditDebt.copy(balanceCents = 0L))),
            today,
        )

        assertEquals(
            -6_000L,
            charged.uiState.totalNetWorthCents - uncharged.uiState.totalNetWorthCents,
        )
    }

    @Test
    fun derive_openingReconciledBalanceCountsOnceInNetWorth() = runBlocking {
        val pack = emptyPack().copy(
            balanceState = LedgerRepository.BalanceState(bankBalanceCents = 500_000L, isReconciled = true),
        )

        val derivation = DashboardDeriver().derive(pack, today)

        assertEquals(500_000L, derivation.uiState.totalNetWorthCents)
    }

    @Test
    fun derive_creditChargeDoesNotFalselyTriggerReconciliationMismatch() = runBlocking {
        val cashIncome = TransactionEntity(
            description = "Paycheck",
            amount_cents = 10_000L,
            date = "2026-08-05",
            type = "income",
            category = "income",
        )
        val cashExpense = TransactionEntity(
            description = "Groceries",
            amount_cents = -1_500L,
            date = "2026-08-10",
            type = "expense",
            category = "food",
        )
        val cardCharge = TransactionEntity(
            description = "Card purchase",
            amount_cents = -6_000L,
            date = "2026-08-12",
            type = "expense",
            category = "shopping",
            account_id = creditAccountId,
        )
        val creditDebt = DebtEntity(
            name = "Visa",
            balanceCents = 6_000L,
            aprBasisPoints = 1_999,
            minimumPaymentCents = 2_500L,
            dueDayOfMonth = 15,
            linkedAccountId = creditAccountId,
            isActive = true,
        )
        // Cash movements net to 8_500; the card charge is a liability, not cash.
        val pack = emptyPack(
            txns = listOf(cashIncome, cashExpense, cardCharge),
            accounts = listOf(creditAccount),
        ).copy(
            debts = listOf(creditDebt),
            balanceState = LedgerRepository.BalanceState(bankBalanceCents = 8_500L, isReconciled = true),
        )

        val derivation = DashboardDeriver().derive(pack, today)

        assertFalse(derivation.reconciliationMismatch)
        assertFalse(derivation.uiState.bankLedgerMismatch)
        assertEquals(null, derivation.reconciliationDetails)
    }

    /**
     * F1(b): the repository moves neither the bank nor a debt for a credit account with no linked
     * debt, so such a charge must not be counted as cash either.
     */
    @Test
    fun derive_creditAccountWithoutLinkedDebtChargeIsNotCash() = runBlocking {
        val cardCharge = TransactionEntity(
            description = "Card purchase with no linked debt",
            amount_cents = -5_000L,
            date = "2026-08-12",
            type = "expense",
            category = "shopping",
            account_id = creditAccountId,
        )
        val reconciledPack = emptyPack(listOf(cardCharge), accounts = listOf(creditAccount)).copy(
            balanceState = LedgerRepository.BalanceState(bankBalanceCents = 0L, isReconciled = true),
        )

        val reconciled = DashboardDeriver().derive(reconciledPack, today)
        val provisional = DashboardDeriver().derive(
            emptyPack(listOf(cardCharge), accounts = listOf(creditAccount)),
            today,
        )

        // The confirmed bank balance never moved, and the cash ledger agrees.
        assertFalse(reconciled.reconciliationMismatch)
        assertEquals(null, reconciled.reconciliationDetails)
        assertEquals(0L, reconciled.uiState.totalNetWorthCents)
        // Unreconciled too: a card charge is not cash leaving the account.
        assertEquals(0L, provisional.uiState.totalNetWorthCents)
    }

    /**
     * F1(a) regression guard: a NON-credit account is always cash, even when an active debt
     * happens to point at it. The repository writes the bank for it, so dropping the transaction
     * from the cash ledger would report a phantom drift and overstate unreconciled net worth.
     */
    @Test
    fun derive_nonCreditAccountWithLinkedDebtChargeStaysInCash() = runBlocking {
        val checking = AccountEntity(
            id = 7L,
            name = "Everyday checking",
            type = "checking",
            balanceCents = 0L,
            lastUpdated = "2026-08-21",
        )
        val mislinkedDebt = DebtEntity(
            name = "Instalment loan",
            balanceCents = 0L,
            aprBasisPoints = 599,
            minimumPaymentCents = 1_000L,
            dueDayOfMonth = 20,
            linkedAccountId = checking.id,
            isActive = true,
        )
        val cashExpense = TransactionEntity(
            description = "Groceries",
            amount_cents = -2_500L,
            date = "2026-08-10",
            type = "expense",
            category = "food",
            account_id = checking.id,
        )
        val pack = emptyPack(listOf(cashExpense), accounts = listOf(checking)).copy(
            debts = listOf(mislinkedDebt),
        )

        val reconciled = DashboardDeriver().derive(
            pack.copy(balanceState = LedgerRepository.BalanceState(bankBalanceCents = -2_500L, isReconciled = true)),
            today,
        )
        val provisional = DashboardDeriver().derive(pack, today)

        // The bank moved by the same amount, so there is nothing to reconcile.
        assertFalse(reconciled.reconciliationMismatch)
        assertEquals(-2_500L, reconciled.uiState.totalNetWorthCents)
        // And an unreconciled cash spend still reduces net worth.
        assertEquals(-2_500L, provisional.uiState.totalNetWorthCents)
    }

    /** F1: a transaction with no account id keeps the legacy cash routing. */
    @Test
    fun derive_nullAccountIdTransactionsAreAlwaysCash() = runBlocking {
        val untaggedExpense = TransactionEntity(
            description = "Groceries",
            amount_cents = -1_500L,
            date = "2026-08-10",
            type = "expense",
            category = "food",
        )
        val cardCharge = TransactionEntity(
            description = "Card purchase",
            amount_cents = -5_000L,
            date = "2026-08-12",
            type = "expense",
            category = "shopping",
            account_id = creditAccountId,
        )
        val pack = emptyPack(
            txns = listOf(untaggedExpense, cardCharge),
            accounts = listOf(creditAccount),
        ).copy(
            balanceState = LedgerRepository.BalanceState(bankBalanceCents = -1_500L, isReconciled = true),
        )

        val derivation = DashboardDeriver().derive(pack, today)

        assertFalse(derivation.reconciliationMismatch)
        assertEquals(-1_500L, derivation.uiState.totalNetWorthCents)
    }

    /**
     * F3: while unreconciled the forecast seeds from the app's CASH ledger, so a credit-card
     * charge cannot deflate the forecast, safe-to-spend or money buckets.
     */
    @Test
    fun derive_unreconciledForecastSeedUsesTheCashLedger() = runBlocking {
        val cashIncome = TransactionEntity(
            description = "Paycheck",
            amount_cents = 10_000L,
            date = "2026-08-05",
            type = "income",
            category = "income",
        )
        val cashExpense = TransactionEntity(
            description = "Groceries",
            amount_cents = -1_500L,
            date = "2026-08-10",
            type = "expense",
            category = "food",
        )
        val cardCharge = TransactionEntity(
            description = "Card purchase",
            amount_cents = -6_000L,
            date = "2026-08-12",
            type = "expense",
            category = "shopping",
            account_id = creditAccountId,
        )
        val pack = emptyPack(
            txns = listOf(cashIncome, cashExpense, cardCharge),
            accounts = listOf(creditAccount),
        )

        val derivation = DashboardDeriver().derive(pack, today)

        // All-txn ledger is 2_500; the cash ledger is 8_500.
        assertEquals(2_500L, derivation.uiState.ledgerBalanceCents)
        val basis = derivation.uiState.trustSignals.single { it.label == "Forecast basis" }
        assertEquals("App balance", basis.value)
        assertTrue(basis.detail, basis.detail.contains(centsToDisplay(8_500L)))
        assertFalse(basis.detail, basis.detail.contains(centsToDisplay(2_500L)))
    }
}
