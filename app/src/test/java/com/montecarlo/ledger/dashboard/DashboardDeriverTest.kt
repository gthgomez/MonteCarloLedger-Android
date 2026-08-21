package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DashboardDeriverTest {

    private val today = LocalDate.of(2026, 8, 21)

    private fun emptyPack(txns: List<TransactionEntity> = emptyList()) = ReportingPackage(
        incomes = emptyList(),
        payments = emptyList(),
        txns = txns,
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
        assertEquals(0.0, derivation.uiState.probabilityNegativePct, 0.0001)
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

        assertTrue(derivation.uiState.monteCarloBasisLabel!!.contains("months of your history"))
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
}
