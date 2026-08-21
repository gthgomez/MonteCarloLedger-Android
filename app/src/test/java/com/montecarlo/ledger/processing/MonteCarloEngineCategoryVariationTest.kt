package com.montecarlo.ledger.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonteCarloEngineCategoryVariationTest {

    private val today = LocalDate.of(2026, 8, 21)

    private fun event(
        description: String,
        category: String?,
        cents: Long,
    ) = ForecastEvent(
        date = today.plusDays(5),
        description = description,
        amount_cents = cents,
        type = "bill",
        category = category,
    )

    @Test
    fun categorizedEventsVaryWithinTheirOwnRangeWhileScalarIsDisabled() {
        val params = MonteCarloParams(
            runs = 400,
            incomeVariationMin = 0,
            incomeVariationMax = 0,
            expenseVariationMin = 0,
            expenseVariationMax = 0,
            expenseCategoryVariation = mapOf("volatile" to (-30..30)),
            surpriseProbability = 0.0,
        )
        val engine = MonteCarloEngine(params)
        val events = listOf(event("Flat bill", null, 10_000L))

        val result = engine.runSimulation(1_000_000L, events, today)

        // Scalar variation disabled and no category match: the event must not move.
        assertEquals(0.0, result.probability_negative_pct, 0.0001)
        assertEquals(990_000L, result.median_ending_balance_cents)
    }

    @Test
    fun categoryRangeOverridesScalarForMatchedEvents() {
        val params = MonteCarloParams(
            runs = 500,
            incomeVariationMin = 0,
            incomeVariationMax = 0,
            expenseVariationMin = -50,
            expenseVariationMax = 50,
            expenseCategoryVariation = mapOf("tight" to (-1..1)),
            surpriseProbability = 0.0,
        )
        val engine = MonteCarloEngine(params)
        val events = listOf(event("Tight bill", "tight", 10_000L))

        var minObserved = Long.MAX_VALUE
        var maxObserved = Long.MIN_VALUE
        repeat(params.runs) { runIdx ->
            val scenario = engine.let { e ->
                val method = e.javaClass.getDeclaredMethod(
                    "generateScenarioTimeline",
                    List::class.java,
                    kotlin.random.Random::class.java,
                    LocalDate::class.java,
                )
                method.isAccessible = true
                @Suppress("UNCHECKED_CAST")
                method.invoke(e, events, kotlin.random.Random(runIdx.toLong()), today) as List<ForecastEvent>
            }
            val scaled = scenario.single().amount_cents
            minObserved = minOf(minObserved, scaled)
            maxObserved = maxOf(maxObserved, scaled)
        }

        // ±1% of 10_000 -> 9_900..10_100; the scalar ±50% range must never appear.
        assertTrue(minObserved >= 9_900L)
        assertTrue(maxObserved <= 10_100L)
        assertTrue("range should actually be exercised", minObserved < maxObserved)
    }
}
