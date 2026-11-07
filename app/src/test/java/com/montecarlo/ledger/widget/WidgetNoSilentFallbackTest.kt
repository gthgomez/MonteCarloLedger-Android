package com.montecarlo.ledger.widget

import com.montecarlo.ledger.CanonicalForecastStatus
import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.contract.ContractSimulationParams
import com.montecarlo.ledger.dashboard.ContractFailureFixtures
import com.montecarlo.ledger.dashboard.canonicalCalculation
import com.montecarlo.ledger.util.MoneyArithmeticOverflow
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS2/F1: the home-screen widget must not silently substitute native-engine headline numbers when
 * the contract flag is on. It shares the dashboard's [CanonicalForecastStatus] mechanism: a
 * canonical failure yields a neutral "Unavailable" state instead of falling back to
 * [com.montecarlo.ledger.processing.ForecastEngine] / [com.montecarlo.ledger.processing.MonteCarloEngine].
 */
class WidgetNoSilentFallbackTest {

    private val today = ContractFailureFixtures.today

    private fun forecastFor(pack: com.montecarlo.ledger.dashboard.ReportingPackage, contractEnabled: Boolean) =
        deriveWidgetForecast(
            today = today,
            contractForecastEnabled = contractEnabled,
            forecastSeedCents = ContractFailureFixtures.seedOf(pack),
            incomes = pack.incomes,
            payments = pack.payments,
            occurrences = pack.billOccurrences,
            txns = pack.txns,
            rules = pack.rules,
        )

    @Test
    fun contractFailureShowsUnavailableInsteadOfNativeNumbers() {
        val pack = ContractFailureFixtures.failingPack()
        assertThrows(MoneyArithmeticOverflow::class.java) {
            ContractFailureFixtures.nativeSafeToSpend(pack)
        }

        val forecast = forecastFor(pack, contractEnabled = true)

        assertEquals(CanonicalForecastStatus.Failed, forecast.status)
        assertFalse("a withheld forecast must never be presented as available", forecast.available)
        assertEquals("Unavailable", forecast.riskLabel)
        assertEquals(
            "the withheld state must render neutrally, not as a native value",
            WidgetForecast(
                safeToSpendCents = 0L,
                probabilityNegativePct = 0.0,
                riskLabel = "Unavailable",
                status = CanonicalForecastStatus.Failed,
                available = false,
                calibrated = false,
            ),
            forecast,
        )
        assertNotEquals("must not claim a stable forecast", "Stable Forecast", forecast.riskLabel)
    }

    @Test
    fun healthyContractStaysLiveAndUsesTheCanonicalValue() {
        val pack = ContractFailureFixtures.healthyPack()

        // Independently recompute the canonical result through the same bridge the widget uses.
        val scenario = ContractScenarioBridge.build(
            ContractScenarioBridge.Inputs(
                startingBalanceCents = ContractFailureFixtures.seedOf(pack),
                asOf = today,
                incomes = pack.incomes,
                payments = pack.payments,
                billOccurrences = pack.billOccurrences,
                simulation = ContractSimulationParams(runs = 100),
            )
        )
        val expectedSafeToSpend = ContractRunner.run(scenario).risk?.safeToSpendCents

        val forecast = forecastFor(pack, contractEnabled = true)

        assertEquals(CanonicalForecastStatus.Live, forecast.status)
        assertTrue(forecast.available)
        assertEquals(expectedSafeToSpend, forecast.safeToSpendCents)
        assertNotEquals("Unavailable", forecast.riskLabel)
    }

    @Test
    fun nativeModeFailsLoudlyOnOverflowInsteadOfReturningWrappedNumbers() {
        val pack = ContractFailureFixtures.failingPack()
        assertThrows(MoneyArithmeticOverflow::class.java) {
            forecastFor(pack, contractEnabled = false)
        }
    }

    /**
     * F1 also required the widget's outer `runCatching` to stop swallowing cancellation. The shared
     * helper is what the widget now routes through, so prove it re-throws rather than reporting a
     * benign failed calculation.
     */
    @Test
    fun coroutineCancellationIsNeverTreatedAsAContractFailure() {
        assertThrows(CancellationException::class.java) {
            canonicalCalculation<Unit> { throw CancellationException("cancelled") }
        }
    }
}
