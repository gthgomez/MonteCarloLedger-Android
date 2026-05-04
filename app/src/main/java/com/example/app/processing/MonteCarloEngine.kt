package com.example.app.processing

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

data class MonteCarloParams(
    val seed: Long = 42,
    val runs: Int = 500,
    val incomeVariationMin: Int = -8,
    val incomeVariationMax: Int = 8,
    val surpriseProbability: Double = 0.15,
    val surpriseCheckIntervalDays: Int = 14,
    val surpriseAmountMin: Int = 2000,
    val surpriseAmountMax: Int = 15000,
    val worstPercentile: Double = 0.1
)

data class MonteCarloResult(
    val worst_10_balance_cents: Int,
    val median_balance_cents: Int,
    val best_90_balance_cents: Int,
    val probability_negative_pct: Double,
    val runs: Int,
    val negative_runs: Int,
    val most_common_first_negative_date: String?,
    val most_common_negative_window_start: String?,
    val most_common_negative_window_end: String?
)

class MonteCarloEngine(
    private val params: MonteCarloParams = MonteCarloParams(),
) {

    fun runSimulation(
        balanceCents: Int,
        events: List<ForecastEvent>,
    ): MonteCarloResult {
        val rng = Random(params.seed)
        val endingBalances = mutableListOf<Int>()
        val lowestBalances = mutableListOf<Int>()
        var negativeRuns = 0
        val negativeDates = mutableListOf<String>()

        repeat(params.runs) {
            val scenario = generateScenarioTimeline(events, rng)
            val summary = simulateScenario(balanceCents, scenario)

            endingBalances.add(summary.endingBalance)
            lowestBalances.add(summary.lowestBalance)
            summary.firstNegativeDate?.let { date ->
                negativeRuns += 1
                negativeDates.add(date)
            }
        }

        endingBalances.sort()
        lowestBalances.sort()

        val medianEnding = getMedian(endingBalances)
        val worst10Ending = percentile(endingBalances, params.worstPercentile)
        val best90Ending = percentile(endingBalances, 0.90)
        val mostCommonNegDate = negativeDates.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }
            ?.key

        return MonteCarloResult(
            worst_10_balance_cents = worst10Ending,
            median_balance_cents = medianEnding,
            best_90_balance_cents = best90Ending,
            probability_negative_pct = if (params.runs > 0) negativeRuns * 100.0 / params.runs else 0.0,
            runs = params.runs,
            negative_runs = negativeRuns,
            most_common_first_negative_date = mostCommonNegDate,
            most_common_negative_window_start = mostCommonNegDate,
            most_common_negative_window_end = mostCommonNegDate,
        )
    }

    private fun generateScenarioTimeline(
        baseTimeline: List<ForecastEvent>,
        rng: Random,
    ): List<ForecastEvent> {
        val scenario = mutableListOf<ForecastEvent>()
        val today = LocalDate.now()

        for (event in baseTimeline) {
            val adjustedAmount = if (event.type == "income") {
                val variationPercent = rng.nextInt(
                    params.incomeVariationMin,
                    params.incomeVariationMax + 1,
                )
                max(0, event.amount_cents + (event.amount_cents * variationPercent / 100))
            } else {
                event.amount_cents
            }
            scenario.add(event.copy(amount_cents = adjustedAmount))
        }

        if (baseTimeline.isNotEmpty()) {
            val lastDate = baseTimeline.last().date
            val daysTotal = (lastDate.toEpochDay() - today.toEpochDay()).toInt().coerceAtLeast(0)
            val checks = daysTotal / params.surpriseCheckIntervalDays

            repeat(checks) { index ->
                if (rng.nextDouble() < params.surpriseProbability) {
                    val surpriseDayOffset =
                        index * params.surpriseCheckIntervalDays + rng.nextInt(params.surpriseCheckIntervalDays)
                    val surpriseDay = today.plusDays(surpriseDayOffset.toLong())
                    if (surpriseDay <= lastDate) {
                        val surpriseAmount = rng.nextInt(
                            params.surpriseAmountMin,
                            params.surpriseAmountMax + 1,
                        )
                        scenario.add(
                            ForecastEvent(
                                date = surpriseDay,
                                description = "Unexpected Expense",
                                amount_cents = surpriseAmount,
                                type = "bill",
                            ),
                        )
                    }
                }
            }
        }

        return scenario.sortedWith(compareBy({ it.date }, { if (it.type == "income") 0 else 1 }))
    }

    private data class ScenarioSummary(
        val endingBalance: Int,
        val lowestBalance: Int,
        val firstNegativeDate: String?,
    )

    private fun simulateScenario(
        balanceCents: Int,
        scenarioTimeline: List<ForecastEvent>,
    ): ScenarioSummary {
        var runningBalance = balanceCents
        var lowestBalance = balanceCents
        var firstNegativeDate: String? = null

        for (event in scenarioTimeline) {
            runningBalance += if (event.type == "income") event.amount_cents else -event.amount_cents
            if (runningBalance < lowestBalance) {
                lowestBalance = runningBalance
            }
            if (firstNegativeDate == null && runningBalance < 0) {
                firstNegativeDate = event.date.toString()
            }
        }

        return ScenarioSummary(
            endingBalance = runningBalance,
            lowestBalance = lowestBalance,
            firstNegativeDate = firstNegativeDate,
        )
    }

    private fun percentile(sortedValues: List<Int>, percentile: Double): Int {
        if (sortedValues.isEmpty()) return 0
        val index = max(0, ceil(sortedValues.size * percentile).toInt() - 1)
        return sortedValues[index.coerceAtMost(sortedValues.lastIndex)]
    }

    private fun getMedian(sortedValues: List<Int>): Int {
        if (sortedValues.isEmpty()) return 0
        val middle = sortedValues.size / 2
        return if (sortedValues.size % 2 == 1) {
            sortedValues[middle]
        } else {
            (sortedValues[middle - 1] + sortedValues[middle]) / 2
        }
    }
}
