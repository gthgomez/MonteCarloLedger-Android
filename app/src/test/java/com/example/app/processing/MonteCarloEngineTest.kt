package com.example.app.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class MonteCarloEngineTest {

    @Test
    fun runSimulation_staysDeterministicWhenVariationAndSurprisesAreDisabled() {
        val today = LocalDate.now()
        val events = listOf(
            ForecastEvent(
                date = today.plusDays(1),
                description = "Payday",
                amount_cents = 1_000,
                type = "income",
            ),
            ForecastEvent(
                date = today.plusDays(2),
                description = "Rent",
                amount_cents = 300,
                type = "expense",
            ),
        )
        val engine = MonteCarloEngine(
            MonteCarloParams(
                seed = 1,
                runs = 10,
                incomeVariationMin = 0,
                incomeVariationMax = 0,
                surpriseProbability = 0.0,
                surpriseCheckIntervalDays = 14,
                surpriseAmountMin = 0,
                surpriseAmountMax = 0,
                worstPercentile = 0.1,
            )
        )

        val result = engine.runSimulation(1_000, events)

        assertEquals(10, result.runs)
        assertEquals(0, result.negative_runs)
        assertEquals(0.0, result.probability_negative_pct, 0.0001)
        assertEquals(1_700, result.worst_10_balance_cents)
        assertEquals(1_700, result.median_balance_cents)
        assertEquals(1_700, result.best_90_balance_cents)
        assertNull(result.most_common_first_negative_date)
        assertNull(result.negative_window_start)
        assertNull(result.negative_window_end)
    }
}
