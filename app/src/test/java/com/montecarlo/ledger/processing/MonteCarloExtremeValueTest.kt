package com.montecarlo.ledger.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Monte Carlo extreme-value and degeneracy invariants:
 *   - percentiles stay ordered at every timestep (P10 <= P50 <= P90)
 *   - no NaN/Infinity/negative-wrapped results on extreme inputs
 *   - deterministic seeds reproduce results for the same input
 *   - minimal history (single month) and very long horizons behave
 */
class MonteCarloExtremeValueTest {

    private val today = LocalDate.of(2026, 1, 15)

    private fun monthlyEvents(
        months: Int,
        incomeCents: Long = 500_000L,
        expenseCents: Long = 350_000L,
    ): List<ForecastEvent> {
        val events = mutableListOf<ForecastEvent>()
        for (month in 0 until months) {
            val day = today.plusMonths(month.toLong())
            events += ForecastEvent(date = day, description = "Income", amount_cents = incomeCents, type = "income")
            events += ForecastEvent(date = day.plusDays(5), description = "Bills", amount_cents = expenseCents, type = "expense", category = "bills")
            events += ForecastEvent(date = day.plusDays(20), description = "Spend", amount_cents = 120_000L, type = "expense", category = "shopping")
        }
        return events
    }

    @Test
    fun percentiles_remainOrderedUnderExtremeVolatility() {
        val engine = MonteCarloEngine(
            MonteCarloParams(
                seed = 7,
                runs = 500,
                incomeVariationMin = -25,
                incomeVariationMax = 25,
                expenseVariationMin = -25,
                expenseVariationMax = 25,
                surpriseProbability = 0.5,
                includeDailyPercentiles = true,
            )
        )
        val result = engine.runSimulation(
            balanceCents = 50_000_000L,
            events = monthlyEvents(months = 12, incomeCents = 20_000_000L, expenseCents = 19_000_000L),
            today = today,
        )

        assertTrue(result.dailyPercentiles.size == 91)
        for (point in result.dailyPercentiles) {
            assertTrue(
                "P10 must be <= P50 at day ${point.dayIndex}",
                point.worst10Cents <= point.medianCents
            )
            assertTrue(
                "P50 must be <= P90 at day ${point.dayIndex}",
                point.medianCents <= point.best90Cents
            )
            assertTrue("P90 must be >= P10 at day ${point.dayIndex}", point.best90Cents >= point.worst10Cents)
        }
        assertTrue(result.probability_negative_pct in 0.0..100.0)
        assertTrue(result.probability_negative_pct.isFinite())
    }

    @Test
    fun veryLargeBalances_doNotOverflowOrWrapNegative() {
        val engine = MonteCarloEngine(
            MonteCarloParams(seed = 99, runs = 100, incomeVariationMax = 10, expenseVariationMax = 10)
        )
        val hugeBalance = 9_000_000_000_000_000L // $90 trillion
        val result = engine.runSimulation(
            balanceCents = hugeBalance,
            events = monthlyEvents(months = 3, incomeCents = 1_000_000_000L, expenseCents = 900_000_000L),
            today = today,
        )

        assertTrue("median ending must stay positive and near the starting balance", result.median_ending_balance_cents > hugeBalance / 2)
        assertTrue(result.median_ending_balance_cents >= result.worst_10_ending_balance_cents)
        assertEquals(hugeBalance, result.best_90_balance_cents) // best-case lowest can't exceed the starting balance
    }

    @Test
    fun singleMonthHistory_behavesWithoutDivisionByZeroOrNaNCent() {
        val engine = MonteCarloEngine(MonteCarloParams(seed = 5, runs = 200, includeDailyPercentiles = true))
        val result = engine.runSimulation(
            balanceCents = 10_000L,
            events = monthlyEvents(months = 1),
            today = today,
        )
        assertTrue(result.dailyPercentiles.isNotEmpty())
        for (point in result.dailyPercentiles) {
            assertTrue(point.worst10Cents <= point.medianCents && point.medianCents <= point.best90Cents)
        }
        assertTrue(result.probability_negative_pct in 0.0..100.0)
    }

    @Test
    fun sameSeedSameInput_reproducesIdenticalResult() {
        fun run(): MonteCarloResult {
            val engine = MonteCarloEngine(MonteCarloParams(seed = 42, runs = 300, includeDailyPercentiles = true))
            return engine.runSimulation(
                balanceCents = 25_000L,
                events = monthlyEvents(months = 6),
                today = today,
            )
        }
        val first = run()
        val second = run()
        assertEquals(first.worst_10_balance_cents, second.worst_10_balance_cents)
        assertEquals(first.median_balance_cents, second.median_balance_cents)
        assertEquals(first.best_90_balance_cents, second.best_90_balance_cents)
        assertEquals(first.dailyPercentiles, second.dailyPercentiles)
    }

    @Test
    fun longHorizon_manyYears_terminatesAndStaysFinite() {
        val engine = MonteCarloEngine(
            MonteCarloParams(seed = 123, runs = 100, incomeVariationMax = 5, expenseVariationMax = 5)
        )
        val result = engine.runSimulation(
            balanceCents = 100_000L,
            events = monthlyEvents(months = 60, incomeCents = 500_000L, expenseCents = 300_000L),
            today = today,
        )
        assertTrue("long horizon with positive net cash flow must stay positive", result.median_ending_balance_cents > 0L)
        assertTrue(result.probability_negative_pct.isFinite())
    }
}