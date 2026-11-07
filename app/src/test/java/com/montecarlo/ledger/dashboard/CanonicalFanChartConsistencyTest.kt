package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.processing.DailyPercentilePoint
import com.montecarlo.ledger.processing.MonteCarloCalibrator
import com.montecarlo.ledger.processing.RecurringDetector
import com.montecarlo.ledger.processing.TimelineService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The dashboard fan chart must be exactly the canonical simulation's daily percentiles. */
class CanonicalFanChartConsistencyTest {

    private val today = ContractFailureFixtures.today

    @Test
    fun dashboardFanChartEqualsCanonicalDailyPercentiles() = runBlocking {
        val pack = ContractFailureFixtures.healthyPack()
        val state = DashboardDeriver()
            .derive(pack, today, contractForecastEnabled = true)
            .uiState

        val calibration = MonteCarloCalibrator.calibrate(
            transactions = pack.txns,
            today = today,
            recurringPatterns = RecurringDetector.detect(pack.txns).map { it.pattern }.toSet(),
        )
        val paymentCategories = pack.payments.mapNotNull { payment ->
            TimelineService.resolveCategory(payment.name, pack.rules)?.let { payment.id to it }
        }.toMap()
        val scenario = ContractScenarioBridge.build(
            ContractScenarioBridge.Inputs(
                startingBalanceCents = ContractFailureFixtures.seedOf(pack),
                asOf = today,
                incomes = pack.incomes,
                payments = pack.payments,
                billOccurrences = pack.billOccurrences,
                simulation = ContractScenarioBridge.simulationParams(calibration),
                paymentCategories = paymentCategories,
            )
        )
        val (result, canonicalDaily) = ContractRunner.runWithDaily(scenario)
        val expected = canonicalDaily.map {
            DailyPercentilePoint(
                dayIndex = it.dayIndex,
                date = it.date,
                worst10Cents = it.p10Cents,
                medianCents = it.p50Cents,
                best90Cents = it.p90Cents,
            )
        }

        assertEquals(expected, state.monteCarloDailyPercentiles)
        assertEquals(
            result.risk!!.negativeBalanceProbabilityPpm / 10_000.0,
            state.probabilityNegativePct,
            1e-9,
        )
        // Day 0 is the canonical starting balance on every simulated path.
        assertEquals(ContractFailureFixtures.seedOf(pack), expected.first().medianCents)
    }
}
