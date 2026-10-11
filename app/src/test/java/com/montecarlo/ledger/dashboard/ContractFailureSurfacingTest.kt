package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.CanonicalForecastStatus
import com.montecarlo.ledger.TrustSignalLevel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS2: a canonical (contract) calculation failure must be surfaced and must never silently switch
 * the headline financial conclusions onto the legacy native engines.
 *
 * (a) the UI state carries an explicit status/error;
 * (b) the headline numbers are NOT the native-engine values (no silent substitution);
 * (c) the last known valid canonical result and its timestamp are retained after a prior success.
 */
class ContractFailureSurfacingTest {

    private val today = ContractFailureFixtures.today

    /** (a)+(b) With no prior success the deriver fails closed and surfaces the failure explicitly. */
    @Test
    fun contractFailureSurfacesExplicitStatusAndFailsClosed() = runBlocking {
        val state = DashboardDeriver()
            .derive(ContractFailureFixtures.failingPack(), today, contractForecastEnabled = true)
            .uiState

        // (a) explicit status + reason.
        assertEquals(CanonicalForecastStatus.Failed, state.canonicalForecastStatus)
        assertNotNull("failure reason must be surfaced", state.canonicalForecastError)
        assertNull("no canonical result is shown, so there is no as-of stamp", state.canonicalForecastAsOf)

        // (b) fail closed: neutral values, and demonstrably not the native-engine numbers.
        val native = ContractFailureFixtures.nativeSafeToSpend(ContractFailureFixtures.failingPack())
        assertEquals(0L, state.safeToSpendCents)
        assertNotEquals("must not substitute native safe-to-spend", native, state.safeToSpendCents)

        val nativeMc = ContractFailureFixtures.nativeMonteCarlo(ContractFailureFixtures.failingPack())
        assertEquals(0L, state.monteCarlo10thCents)
        assertEquals(0L, state.monteCarlo50thCents)
        assertEquals(0L, state.monteCarlo90thCents)
        assertEquals(0.0, state.probabilityNegativePct, 1e-9)
        assertEquals(0L, state.dailyBudgetCents)
        assertTrue("no canonical forecast rows are shown", state.forecastRows.isEmpty())
        assertNotEquals("must not substitute native Monte Carlo median", nativeMc.median_balance_cents, state.monteCarlo50thCents)

        // A visible trust-layer signal announces the failure.
        val signal = state.trustSignals.firstOrNull { it.label == "Forecast engine" }
        assertNotNull("a 'Forecast engine' trust signal must be visible", signal)
        assertEquals(TrustSignalLevel.Warning, signal!!.level)
        assertEquals("Calculation failed", signal.value)
    }

    /**
     * (c) After a prior success a later failure shows the retained canonical result, clearly labelled
     * with the timestamp of when that result was computed.
     */
    @Test
    fun contractFailureRetainsLastKnownValidResultWithTimestamp() = runBlocking {
        val deriver = DashboardDeriver()
        val healthy = deriver.derive(ContractFailureFixtures.healthyPack(), today, contractForecastEnabled = true).uiState
        assertEquals(CanonicalForecastStatus.Live, healthy.canonicalForecastStatus)

        val failing = deriver.derive(ContractFailureFixtures.failingPack(), today, contractForecastEnabled = true).uiState

        assertEquals(CanonicalForecastStatus.Stale, failing.canonicalForecastStatus)
        assertNotNull("failure reason must be surfaced", failing.canonicalForecastError)
        assertNotNull("the retained result must carry an as-of timestamp", failing.canonicalForecastAsOf)
        assertTrue(
            "as-of timestamp must look like yyyy-MM-dd HH:mm but was ${failing.canonicalForecastAsOf}",
            Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}""").matches(failing.canonicalForecastAsOf!!),
        )

        // The retained canonical headline numbers are shown unchanged.
        assertEquals(healthy.safeToSpendCents, failing.safeToSpendCents)
        assertEquals(healthy.monteCarlo10thCents, failing.monteCarlo10thCents)
        assertEquals(healthy.monteCarlo50thCents, failing.monteCarlo50thCents)
        assertEquals(healthy.monteCarlo90thCents, failing.monteCarlo90thCents)
        assertEquals(healthy.probabilityNegativePct, failing.probabilityNegativePct, 1e-9)
        assertEquals(healthy.forecastRows.map { it.date }, failing.forecastRows.map { it.date })

        // ...and they are still not the native-engine values.
        val native = ContractFailureFixtures.nativeSafeToSpend(ContractFailureFixtures.failingPack())
        assertNotEquals(native, failing.safeToSpendCents)

        val signal = failing.trustSignals.firstOrNull { it.label == "Forecast engine" }
        assertNotNull(signal)
        assertEquals(TrustSignalLevel.Warning, signal!!.level)
        assertEquals("Using last valid result", signal.value)
        assertTrue(
            "the stale signal must mention the as-of timestamp",
            signal.detail.contains(failing.canonicalForecastAsOf!!),
        )
    }

    /** A successful canonical calculation reports itself as Live with a timestamp, and no error. */
    @Test
    fun successfulCanonicalCalculationIsLive() = runBlocking {
        val state = DashboardDeriver()
            .derive(ContractFailureFixtures.healthyPack(), today, contractForecastEnabled = true)
            .uiState

        assertEquals(CanonicalForecastStatus.Live, state.canonicalForecastStatus)
        assertNull(state.canonicalForecastError)
        assertNotNull(state.canonicalForecastAsOf)
        assertNull(state.trustSignals.firstOrNull { it.label == "Forecast engine" })
    }

    /**
     * Requirement 3: with the contract engine disabled the native path is the *intended* engine, not
     * a fallback — it must keep supplying the headline numbers and report [CanonicalForecastStatus.Native].
     */
    @Test
    fun nativeModeRemainsIntendedEngineWhenContractDisabled() = runBlocking {
        val pack = ContractFailureFixtures.failingPack()
        val state = DashboardDeriver()
            .derive(pack, today, contractForecastEnabled = false)
            .uiState

        assertEquals(CanonicalForecastStatus.Native, state.canonicalForecastStatus)
        assertNull(state.canonicalForecastError)
        assertNull(state.canonicalForecastAsOf)

        // Deliberate native mode: headline numbers equal the native engine's values.
        assertEquals(ContractFailureFixtures.nativeSafeToSpend(pack), state.safeToSpendCents)
        val nativeMc = ContractFailureFixtures.nativeMonteCarlo(pack)
        assertEquals(nativeMc.median_balance_cents, state.monteCarlo50thCents)
        assertEquals(nativeMc.probability_negative_pct, state.probabilityNegativePct, 1e-9)
        assertNull("no failure to announce in native mode", state.trustSignals.firstOrNull { it.label == "Forecast engine" })
    }
}
