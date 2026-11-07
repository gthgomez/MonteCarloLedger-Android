package com.montecarlo.ledger.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The daily fan chart must be a visualization of the canonical simulation, not a second engine.
 */
class ContractSimulationDailyTest {

    private fun scenario(
        starting: Long = 0L,
        events: String = "[]",
        horizon: Int = 30,
        surprisePpm: Int = 1_000_000,
        surpriseAmount: Int = 10_000,
    ): ContractScenario = ContractScenarioParser.parse(
        """
        {
          "contract_version": "2.0",
          "scenario_id": "daily-test",
          "as_of": "2026-11-01",
          "starting_balance_cents": $starting,
          "horizon_days": $horizon,
          "events": $events,
          "simulation": {
            "runs": 100,
            "seed": 7,
            "surprise_probability_ppm": $surprisePpm,
            "surprise_check_interval_days": 7,
            "surprise_amount_min": $surpriseAmount,
            "surprise_amount_max": $surpriseAmount,
            "income_variation_min": 0,
            "income_variation_max": 0,
            "expense_variation_min": 0,
            "expense_variation_max": 0
          }
        }
        """.trimIndent()
    )

    @Test
    fun dailyPointsComeFromTheCanonicalPathsAndRespectTheGrid() {
        val (result, daily) = ContractRunner.runWithDaily(scenario())
        assertEquals(1_000_000L, result.risk!!.negativeBalanceProbabilityPpm)
        assertEquals(31, daily.size)
        assertEquals("2026-11-01", daily[0].date.toString())
        assertEquals("2026-11-01", daily[1].date.toString())
        assertEquals("2026-11-02", daily[2].date.toString())
        daily.forEach { point ->
            assertTrue(point.p10Cents <= point.p50Cents)
            assertTrue(point.p50Cents <= point.p90Cents)
        }
        // Day 0 is the opening balance for every simulated run.
        assertEquals(0L, daily[0].p10Cents)
        assertEquals(0L, daily[0].p50Cents)
        assertEquals(0L, daily[0].p90Cents)
        // Surprises are generated from the horizon even with an empty schedule.
        assertTrue(daily.drop(1).any { it.p10Cents < 0L })
    }

    @Test
    fun dailySeriesIsDeterministicForTheSameSeed() {
        val first = ContractRunner.runWithDaily(scenario())
        val second = ContractRunner.runWithDaily(scenario())
        assertEquals(first.first.risk, second.first.risk)
        assertEquals(first.second, second.second)
    }

    @Test
    fun sameDayIncomeAndExpenseAreReflectedInTheDailyBalance() {
        val events = """
            [
              {"date":"2026-11-01","amount_cents":100000,"type":"income","name":"Pay"},
              {"date":"2026-11-01","amount_cents":-50000,"type":"expense","name":"Rent"}
            ]
        """.trimIndent()
        val (_, daily) = ContractRunner.runWithDaily(
            scenario(events = events, surprisePpm = 0)
        )
        assertEquals(50_000L, daily[1].p50Cents)
        assertEquals(50_000L, daily[30].p50Cents)
    }

    @Test
    fun negativeOpeningBalanceIsVisibleFromDayZero() {
        val (_, daily) = ContractRunner.runWithDaily(
            scenario(starting = -1_000L, surprisePpm = 0)
        )
        assertEquals(-1_000L, daily[0].p10Cents)
        assertEquals(-1_000L, daily[0].p90Cents)
        assertEquals(-1_000L, daily[30].p10Cents)
    }
}
