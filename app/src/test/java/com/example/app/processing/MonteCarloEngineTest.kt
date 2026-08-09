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
        assertEquals(1_000L, result.worst_10_balance_cents)
        assertEquals(1_700L, result.worst_10_ending_balance_cents)
        assertEquals(1_000L, result.median_balance_cents)
        assertEquals(1_700L, result.median_ending_balance_cents)
        assertNull(result.most_common_first_negative_date)
        assertNull(result.negative_window_start)
        assertNull(result.negative_window_end)
    }

    @Test
    fun runSimulation_detectsMidHorizonDipEvenIfEndingBalanceIsPositive() {
        val today = LocalDate.now()
        val events = listOf(
            ForecastEvent(
                date = today.plusDays(1),
                description = "Big Expense",
                amount_cents = 5_000,
                type = "expense",
            ),
            ForecastEvent(
                date = today.plusDays(10),
                description = "Late Payday",
                amount_cents = 10_000,
                type = "income",
            ),
        )
        val engine = MonteCarloEngine(
            MonteCarloParams(
                seed = 1,
                runs = 10,
                incomeVariationMin = 0,
                incomeVariationMax = 0,
                surpriseProbability = 0.0,
            )
        )

        // Starting balance 1,000. Dips to -4,000 on Day 1, ends at 6,000 on Day 10.
        val result = engine.runSimulation(1_000L, events)

        assertEquals(10, result.negative_runs)
        assertEquals(-4_000L, result.worst_10_balance_cents)
        assertEquals(6_000L, result.worst_10_ending_balance_cents)
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

    @Test
    fun runSimulation_dailyPercentilesGeneratedWhenOptedIn() {
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
            seed = 42,
            runs = 20,
            incomeVariationMin = 0,
            incomeVariationMax = 0,
            surpriseProbability = 0.0,
            includeDailyPercentiles = true,
        )
        val engine = MonteCarloEngine(params)
        val result = engine.runSimulation(1_000L, events, fixedToday)

        assertEquals(91, result.dailyPercentiles.size)

        // Day 0 opening balance
        val day0 = result.dailyPercentiles[0]
        assertEquals(0, day0.dayIndex)
        assertEquals(fixedToday, day0.date)
        assertEquals(1_000L, day0.medianCents)
        assertEquals(1_000L, day0.worst10Cents)
        assertEquals(1_000L, day0.best90Cents)

        // Assert worst10 <= median <= best90 for all 91 days
        result.dailyPercentiles.forEach { pt ->
            assertTrue(
                "Invariant violated at day ${pt.dayIndex}: ${pt.worst10Cents} <= ${pt.medianCents} <= ${pt.best90Cents}",
                pt.worst10Cents <= pt.medianCents && pt.medianCents <= pt.best90Cents
            )
        }

        // Day 90 end-of-day balance
        val day90 = result.dailyPercentiles.last()
        assertEquals(90, day90.dayIndex)
        assertEquals(fixedToday.plusDays(89), day90.date)
        assertEquals(2_200L, day90.medianCents)
    }

    @Test
    fun runSimulation_dailyPercentilesEmptyWhenOptedOut() {
        val fixedToday = LocalDate.of(2026, 7, 15)
        val events = listOf(
            ForecastEvent(
                date = fixedToday.plusDays(1),
                description = "Payday",
                amount_cents = 2_000,
                type = "income",
            ),
        )
        val params = MonteCarloParams(
            seed = 42,
            runs = 20,
            includeDailyPercentiles = false,
        )
        val engine = MonteCarloEngine(params)
        val result = engine.runSimulation(1_000L, events, fixedToday)

        assertTrue(result.dailyPercentiles.isEmpty())
    }

    @Test
    fun runSimulation_handlesSingleRun() {
        val fixedToday = LocalDate.of(2026, 7, 15)
        val events = listOf(
            ForecastEvent(
                date = fixedToday.plusDays(1),
                description = "Expense",
                amount_cents = 500,
                type = "expense",
            ),
        )
        val params = MonteCarloParams(
            seed = 42,
            runs = 1,
            incomeVariationMin = 0,
            incomeVariationMax = 0,
            surpriseProbability = 0.0,
            includeDailyPercentiles = true,
        )
        val engine = MonteCarloEngine(params)
        val result = engine.runSimulation(1_000L, events, fixedToday)

        assertEquals(1, result.runs)
        assertEquals(91, result.dailyPercentiles.size)
        result.dailyPercentiles.forEach { pt ->
            assertEquals(pt.worst10Cents, pt.medianCents)
            assertEquals(pt.medianCents, pt.best90Cents)
        }
    }

    @Test
    fun runSimulation_outOfHorizonEventsDoNotDistortDay90DailyPercentiles() {
        val fixedToday = LocalDate.of(2026, 7, 15)
        val events = listOf(
            ForecastEvent(
                date = fixedToday.plusDays(10),
                description = "Day 10 Payday",
                amount_cents = 10_000,
                type = "income",
            ),
            ForecastEvent(
                date = fixedToday.plusDays(120), // Beyond 90-day horizon (Day 90 = today + 89)
                description = "Day 120 Late Expense",
                amount_cents = 5_000,
                type = "expense",
            ),
        )
        val params = MonteCarloParams(
            seed = 42,
            runs = 20,
            incomeVariationMin = 0,
            incomeVariationMax = 0,
            surpriseProbability = 0.0,
            includeDailyPercentiles = true,
        )
        val engine = MonteCarloEngine(params)
        val result = engine.runSimulation(1_000L, events, fixedToday)

        // Day 90 (index 90, date today + 89) should include Day 10 income but NOT Day 120 expense
        val day90 = result.dailyPercentiles.last()
        assertEquals(90, day90.dayIndex)
        assertEquals(fixedToday.plusDays(89), day90.date)
        assertEquals(11_000L, day90.medianCents)

        // Aggregate ending balance includes full scenario timeline through Day 120
        assertEquals(6_000L, result.median_ending_balance_cents)
    }
}
