package com.montecarlo.ledger.ui

import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.CanonicalForecastStatus
import com.montecarlo.ledger.util.centsToDisplayWhole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WS2/F2: the 3-month estimate card must not render the neutral zeros of a withheld calculation as
 * a reassuring "$0 / $0 / $0" and "0.0%".
 */
class MonteCarloDisplayTest {

    @Test
    fun withheldCalculationShowsUnavailableInsteadOfNeutralZeros() {
        val uiState = AppUiState(
            canonicalForecastStatus = CanonicalForecastStatus.Failed,
            monteCarlo10thCents = 0L,
            monteCarlo50thCents = 0L,
            monteCarlo90thCents = 0L,
            probabilityNegativePct = 0.0,
        )

        val display = monteCarloDisplay(uiState)

        assertTrue(display.withheld)
        assertEquals("Unavailable", display.risk)
        assertEquals("—", display.lower)
        assertEquals("—", display.typical)
        assertEquals("—", display.higher)
        assertNotEquals("0.0", display.risk)
    }

    @Test
    fun liveCalculationShowsTheRealNumbers() {
        val uiState = AppUiState(
            canonicalForecastStatus = CanonicalForecastStatus.Live,
            monteCarlo10thCents = -5_000L,
            monteCarlo50thCents = 100_000L,
            monteCarlo90thCents = 250_000L,
            probabilityNegativePct = 12.5,
        )

        val display = monteCarloDisplay(uiState)

        assertFalse(display.withheld)
        assertEquals("12.5", display.risk)
        assertEquals(centsToDisplayWhole(-5_000L), display.lower)
        assertEquals(centsToDisplayWhole(100_000L), display.typical)
        assertEquals(centsToDisplayWhole(250_000L), display.higher)
    }
}
