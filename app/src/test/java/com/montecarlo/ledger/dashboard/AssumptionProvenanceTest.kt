package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.AssumptionOrigin
import com.montecarlo.ledger.DataSufficiency
import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * WS4 typed-provenance contract for the dashboard risk model.
 *
 * Three origins must stay distinct — user-entered, history-derived, and unverified — and the
 * insufficient-data state must be explicit, so default assumptions are never presented as calibrated.
 */
class AssumptionProvenanceTest {

    private val today = LocalDate.of(2026, 8, 21)

    private fun expense(description: String, cents: Long, date: String, category: String = "food") =
        TransactionEntity(
            description = description,
            amount_cents = -cents,
            date = date,
            type = "expense",
            category = category,
        )

    private fun income(cents: Long, date: String) = TransactionEntity(
        description = "Paycheck",
        amount_cents = cents,
        date = date,
        type = "income",
        category = "income",
    )

    private fun pack(
        txns: List<TransactionEntity>,
        reconciled: Boolean = false,
        bankBalanceCents: Long = 0L,
    ) = ReportingPackage(
        incomes = emptyList(),
        payments = emptyList(),
        txns = txns,
        accounts = emptyList(),
        balanceState = LedgerRepository.BalanceState(bankBalanceCents, isReconciled = reconciled),
        billOccurrences = emptyList(),
        assets = emptyList(),
        goals = emptyList(),
        categoryBudgets = emptyList(),
        debts = emptyList(),
        rules = emptyList(),
        dashboardConfig = DashboardConfig(),
    )

    private fun insufficientHistory(): List<TransactionEntity> = listOf(
        income(200_000L, "2026-07-01"),
        expense("Rent", 100_000L, "2026-07-02", "bills"),
        income(200_000L, "2026-08-01"),
        expense("Rent", 100_000L, "2026-08-02", "bills"),
    )

    private fun calibratedHistory(): List<TransactionEntity> = buildList {
        for (month in 1..5) {
            add(income(200_000L, "2026-0$month-01"))
            add(expense("Rent", 100_000L, "2026-0$month-02", "bills"))
            add(expense("Groceries", 40_000L + month * 1_000L, "2026-0$month-10", "food"))
        }
    }

    private fun com.montecarlo.ledger.AppUiState.provenance(label: String) =
        assumptionProvenance.firstOrNull { it.label == label }
            ?: error("no provenance row for '$label': ${assumptionProvenance.map { it.label }}")

    @Test
    fun insufficientHistory_isUnverifiedAndFlaggedInsufficientData() = runBlocking {
        val state = DashboardDeriver().derive(pack(insufficientHistory()), today).uiState

        assertTrue("defaults must be flagged insufficient-data", state.riskModelInsufficientData)

        val variation = state.provenance("Income & expense variation")
        assertEquals(AssumptionOrigin.Unverified, variation.origin)
        assertEquals(DataSufficiency.InsufficientData, variation.sufficiency)

        val probability = state.riskProbabilityProvenance!!
        assertEquals(AssumptionOrigin.Unverified, probability.origin)
        assertEquals(DataSufficiency.InsufficientData, probability.sufficiency)
    }

    @Test
    fun calibratedHistory_marksInputsHistoryDerivedButProbabilityRemainsUnverified() = runBlocking {
        val state = DashboardDeriver().derive(pack(calibratedHistory()), today).uiState

        assertFalse(state.riskModelInsufficientData)

        val variation = state.provenance("Income & expense variation")
        assertEquals(AssumptionOrigin.HistoryDerived, variation.origin)
        assertEquals(DataSufficiency.Sufficient, variation.sufficiency)

        // Enough history to calibrate variance is NOT proof that the probability is accurate.
        val probability = state.riskProbabilityProvenance!!
        assertEquals(AssumptionOrigin.Unverified, probability.origin)
        assertEquals(DataSufficiency.Sufficient, probability.sufficiency)
    }

    @Test
    fun reconciledBalance_marksForecastSeedUserEntered() = runBlocking {
        val state = DashboardDeriver()
            .derive(
                pack(calibratedHistory(), reconciled = true, bankBalanceCents = 250_000L),
                today,
            )
            .uiState

        val seed = state.provenance("Forecast balance")
        assertEquals(AssumptionOrigin.UserEntered, seed.origin)
        assertEquals(DataSufficiency.Sufficient, seed.sufficiency)
    }

    @Test
    fun provenance_isVisibleInTheTrustLayer() = runBlocking {
        val state = DashboardDeriver().derive(pack(insufficientHistory()), today).uiState

        val probabilitySignal = state.trustSignals.firstOrNull { it.label == "Risk probability" }
            ?: error("probability provenance is not surfaced: ${state.trustSignals.map { it.label }}")
        assertEquals("Unverified", probabilitySignal.value)

        // Insufficient history must read as a warning, not a quiet good signal.
        assertTrue(state.assumptionProvenance.any { it.sufficiency == DataSufficiency.InsufficientData })
    }
}
