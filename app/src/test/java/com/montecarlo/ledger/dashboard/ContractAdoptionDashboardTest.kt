package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.adoption.ContractDashboardMapper
import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.processing.MonteCarloCalibrator
import com.montecarlo.ledger.processing.RecurringDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * MC-06b: the dashboard's headline forecast / Monte Carlo / safe-to-spend values must be exactly
 * the canonical contract result for the same repository state.
 */
class ContractAdoptionDashboardTest {

    private val today = LocalDate.of(2026, 1, 1)

    private fun pack(): ReportingPackage {
        val txns = listOf(
            TransactionEntity(
                description = "Starting balance",
                amount_cents = 200_000L,
                date = "2025-12-01",
                type = "income",
                category = "income",
            )
        )
        return ReportingPackage(
            incomes = listOf(
                IncomeEntity(
                    id = 1,
                    name = "Paycheck",
                    amount_cents = 100_000L,
                    frequency = "Monthly",
                    day_of_month = 5,
                    next_date = "2026-01-05",
                )
            ),
            payments = listOf(
                PaymentEntity(
                    id = 1,
                    name = "Rent",
                    amount_cents = 60_000L,
                    frequency = "Monthly",
                    day_of_month = 3,
                    next_date = "2026-01-03",
                )
            ),
            txns = txns,
            balanceState = LedgerRepository.BalanceState(bankBalanceCents = 200_000L, isReconciled = true),
            billOccurrences = emptyList(),
            assets = emptyList(),
            goals = emptyList(),
            categoryBudgets = emptyList(),
            debts = emptyList(),
            rules = emptyList(),
            dashboardConfig = DashboardConfig(),
        )
    }

    @Test
    fun dashboardHeadlineMatchesCanonicalContractResult() = runBlocking {
        val reporting = pack()
        val derivation = DashboardDeriver().derive(reporting, today)
        val state = derivation.uiState

        // Recompute the canonical result through the same bridge, independently of the deriver.
        val calibration = MonteCarloCalibrator.calibrate(
            transactions = reporting.txns,
            today = today,
            recurringPatterns = RecurringDetector.detect(reporting.txns).map { it.pattern }.toSet(),
        )
        val scenario = ContractScenarioBridge.build(
            ContractScenarioBridge.Inputs(
                startingBalanceCents = 200_000L,
                asOf = today,
                incomes = reporting.incomes,
                payments = reporting.payments,
                billOccurrences = reporting.billOccurrences,
                simulation = ContractScenarioBridge.simulationParams(calibration),
            )
        )
        val expected = ContractRunner.run(scenario)
        val risk = expected.risk!!

        assertEquals(risk.safeToSpendCents, state.safeToSpendCents)
        assertEquals(risk.minimumBalanceP10Cents, state.monteCarlo10thCents)
        assertEquals(risk.minimumBalanceP50Cents, state.monteCarlo50thCents)
        assertEquals(risk.minimumBalanceP90Cents, state.monteCarlo90thCents)
        assertEquals(risk.negativeBalanceProbabilityPpm / 10_000.0, state.probabilityNegativePct, 1e-9)
        assertEquals(expected.forecast.firstNegativeDate?.toString(), state.firstNegativeDateLabel)
        assertEquals(expected.forecast.minimumBalanceDate.toString(), state.lowestBalanceDateLabel)
        assertEquals(risk.endingBalanceP50Cents, state.monteCarloResult!!.median_ending_balance_cents)
        // The non-normative daily fan chart overlay is preserved.
        assertTrue(state.monteCarloDailyPercentiles.isNotEmpty())
    }

    /**
     * MCD-0008: safe-to-spend is the quantile-based value, not the deterministic low point, so it
     * must match the contract risk block rather than `forecast.minimum_balance_cents`.
     */
    @Test
    fun safeToSpendIsTheQuantileValueNotTheDeterministicLowPoint() = runBlocking {
        val reporting = pack()
        val state = DashboardDeriver().derive(reporting, today).uiState

        val calibration = MonteCarloCalibrator.calibrate(
            transactions = reporting.txns,
            today = today,
            recurringPatterns = RecurringDetector.detect(reporting.txns).map { it.pattern }.toSet(),
        )
        val scenario = ContractScenarioBridge.build(
            ContractScenarioBridge.Inputs(
                startingBalanceCents = 200_000L,
                asOf = today,
                incomes = reporting.incomes,
                payments = reporting.payments,
                simulation = ContractScenarioBridge.simulationParams(calibration),
            )
        )
        val expected = ContractRunner.run(scenario)

        assertEquals(expected.risk!!.safeToSpendCents, state.safeToSpendCents)
    }

    /** MC-07/C1: the displayed rows come from the canonical timeline, not a second engine. */
    @Test
    fun forecastRowsAreCanonical() = runBlocking {
        val reporting = pack()
        val state = DashboardDeriver().derive(reporting, today).uiState
        val calibration = MonteCarloCalibrator.calibrate(
            transactions = reporting.txns,
            today = today,
            recurringPatterns = RecurringDetector.detect(reporting.txns).map { it.pattern }.toSet(),
        )
        val scenario = ContractScenarioBridge.build(
            ContractScenarioBridge.Inputs(
                startingBalanceCents = 200_000L,
                asOf = today,
                incomes = reporting.incomes,
                payments = reporting.payments,
                simulation = ContractScenarioBridge.simulationParams(calibration),
            )
        )
        val expectedRows = ContractDashboardMapper.toBalanceForecastRows(scenario)
        assertEquals(expectedRows.map { it.date }, state.forecastRows.map { it.date })
        assertEquals(expectedRows.map { it.balanceCents }, state.forecastRows.map { it.balanceCents })
    }

    /** MC-07/C2: the daily budget is derived from the canonical safe-to-spend (guidance only). */
    @Test
    fun dailyBudgetDerivesFromCanonicalSafeToSpend() = runBlocking {
        val state = DashboardDeriver().derive(pack(), today).uiState
        assertTrue(state.dailyBudgetCents >= 0L)
        if (state.safeToSpendCents <= 0L) assertEquals(0L, state.dailyBudgetCents)
    }
}
