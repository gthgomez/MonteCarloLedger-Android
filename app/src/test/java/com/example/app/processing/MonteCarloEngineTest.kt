package com.example.app.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    /**
     * Same seed + same fixed today ⇒ identical percentile results across two runs.
     * This guarantees the simulation is clock-independent when [today] is injected.
     */
    @Test
    fun runSimulation_isDeterministicWithFixedToday() {
        val fixedToday = LocalDate.of(2026, 7, 15)
        val events = listOf(
            ForecastEvent(
                date = fixedToday.plusDays(1),
                description = "Payday",
                amount_cents = 2_000,
                type = "income",
            ),
            ForecastEvent(
                date = fixedToday.plusDays(5),
                description = "Rent",
                amount_cents = 800,
                type = "expense",
            ),
        )
        val params = MonteCarloParams(
            seed = 999,
            runs = 50,
            incomeVariationMin = -10,
            incomeVariationMax = 10,
            surpriseProbability = 0.2,
            surpriseCheckIntervalDays = 7,
            surpriseAmountMin = 500,
            surpriseAmountMax = 5_000,
            worstPercentile = 0.1,
        )

        val engineA = MonteCarloEngine(params)
        val engineB = MonteCarloEngine(params)

        val resultA = engineA.runSimulation(2_000, events, fixedToday)
        val resultB = engineB.runSimulation(2_000, events, fixedToday)

        assertEquals("median should be identical for same seed+today",
            resultA.median_balance_cents, resultB.median_balance_cents)
        assertEquals("worst-10 should be identical for same seed+today",
            resultA.worst_10_balance_cents, resultB.worst_10_balance_cents)
        assertEquals("best-90 should be identical for same seed+today",
            resultA.best_90_balance_cents, resultB.best_90_balance_cents)
        assertEquals("negative probability should be identical for same seed+today",
            resultA.probability_negative_pct, resultB.probability_negative_pct, 0.0001)
        assertEquals("runs should match",
            resultA.runs, resultB.runs)
    }

    /**
     * Same seed but different today values can produce different results because
     * the surprise-event injection window shifts relative to the timeline.
     */
    @Test
    fun runSimulation_differentTodayCanDiffer() {
        val todayA = LocalDate.of(2026, 7, 1)
        val todayB = LocalDate.of(2026, 8, 1)
        val baseDate = LocalDate.of(2026, 7, 15)
        val events = listOf(
            ForecastEvent(
                date = baseDate.plusDays(14),
                description = "Payday",
                amount_cents = 3_000,
                type = "income",
            ),
            ForecastEvent(
                date = baseDate.plusDays(30),
                description = "Rent",
                amount_cents = 1_200,
                type = "expense",
            ),
        )
        val params = MonteCarloParams(
            seed = 42,
            runs = 100,
            incomeVariationMin = -5,
            incomeVariationMax = 5,
            surpriseProbability = 0.3,
            surpriseCheckIntervalDays = 7,
            surpriseAmountMin = 1_000,
            surpriseAmountMax = 10_000,
            worstPercentile = 0.1,
        )

        val engine = MonteCarloEngine(params)

        val resultA = engine.runSimulation(3_000, events, todayA)
        val resultB = engine.runSimulation(3_000, events, todayB)

        // They *may* differ, but with the same seed we can't guarantee they will.
        // The test validates that with different today values the simulation does
        // not crash and produces structurally valid results.
        // The key invariant is: same seed + same today = identical (tested above).
        assertNotNull(resultA)
        assertNotNull(resultB)
        assertEquals(100, resultA.runs)
        assertEquals(100, resultB.runs)
    }
}
