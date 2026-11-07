package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.CanonicalForecastStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS2/F2 + F3: a withheld canonical calculation must be labelled as unavailable everywhere it would
 * otherwise read as a genuine — and reassuring — number.
 *
 *  - F2: the action center must not render "Stable forecast" / "Safe to spend" beside neutral zeros.
 *  - F3: the fan chart must not fall back to native daily percentiles when the calculation failed.
 */
class ContractWithheldPresentationTest {

    private val today = ContractFailureFixtures.today

    // ---- F2 ------------------------------------------------------------------------------------

    @Test
    fun failedContractMarksTheActionCenterUnavailable() = runBlocking {
        val state = DashboardDeriver()
            .derive(ContractFailureFixtures.failingPack(), today, contractForecastEnabled = true)
            .uiState

        assertEquals(CanonicalForecastStatus.Failed, state.canonicalForecastStatus)
        assertFalse("the action center must not present a withheld forecast as available", state.actionCenter.forecastAvailable)
        assertEquals("Unavailable", state.actionCenter.forecastRiskLabel)
        assertEquals("Forecast unavailable", state.actionCenter.safeToSpendCaption)
        // The reassuring default labels are gone.
        assertNotEquals("Stable forecast", state.actionCenter.forecastRiskLabel)
        assertNotEquals("Safe to spend", state.actionCenter.safeToSpendCaption)
    }

    // ---- F3 ------------------------------------------------------------------------------------

    @Test
    fun failedContractWithholdsTheDailyFanChart() = runBlocking {
        val pack = ContractFailureFixtures.failingPack()
        val state = DashboardDeriver().derive(pack, today, contractForecastEnabled = true).uiState

        assertEquals(CanonicalForecastStatus.Failed, state.canonicalForecastStatus)
        assertTrue("the fan chart must be empty when the calculation is withheld", state.monteCarloDailyPercentiles.isEmpty())
        // ...and it is definitely not the native fan the old unconditional assignment leaked.
        assertTrue(
            "fixture sanity: the native engine does produce a non-empty fan",
            ContractFailureFixtures.nativeMonteCarlo(pack).dailyPercentiles.isNotEmpty(),
        )
    }

    // ---- positive controls: a live/stale canonical result still shows real data ---------------

    @Test
    fun liveContractKeepsTheActionCenterAndFanChart() = runBlocking {
        val state = DashboardDeriver()
            .derive(ContractFailureFixtures.healthyPack(), today, contractForecastEnabled = true)
            .uiState

        assertEquals(CanonicalForecastStatus.Live, state.canonicalForecastStatus)
        assertTrue(state.actionCenter.forecastAvailable)
        assertNotEquals("Unavailable", state.actionCenter.forecastRiskLabel)
        assertTrue("a live canonical result keeps its fan chart", state.monteCarloDailyPercentiles.isNotEmpty())
    }

    @Test
    fun staleContractShowsTheRetainedResultAsAvailable() = runBlocking {
        val deriver = DashboardDeriver()
        val healthy = deriver.derive(ContractFailureFixtures.healthyPack(), today, contractForecastEnabled = true).uiState
        val failing = deriver.derive(ContractFailureFixtures.failingPack(), today, contractForecastEnabled = true).uiState

        assertEquals(CanonicalForecastStatus.Stale, failing.canonicalForecastStatus)
        // A retained (stale) result is real data, just old — it stays available and keeps its fan,
        // which must match the retained result rather than a freshly recomputed native fan.
        assertTrue(failing.actionCenter.forecastAvailable)
        assertNotEquals("Unavailable", failing.actionCenter.forecastRiskLabel)
        assertEquals(
            healthy.monteCarloDailyPercentiles.map { it.date },
            failing.monteCarloDailyPercentiles.map { it.date },
        )
    }

    @Test
    fun nativeModeKeepsItsOwnFanChart() = runBlocking {
        val state = DashboardDeriver()
            .derive(ContractFailureFixtures.failingPack(), today, contractForecastEnabled = false)
            .uiState

        assertEquals(CanonicalForecastStatus.Native, state.canonicalForecastStatus)
        assertTrue(state.actionCenter.forecastAvailable)
        assertTrue(state.monteCarloDailyPercentiles.isNotEmpty())
    }
}
