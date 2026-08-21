package com.montecarlo.ledger.processing

import java.time.LocalDate
import com.montecarlo.ledger.util.scaleCentsByPercent
import kotlin.math.ceil
import kotlin.math.max
import kotlin.random.Random

data class MonteCarloParams(
    val seed: Long = 42,
    val runs: Int = 500,
    val incomeVariationMin: Int = -8,
    val incomeVariationMax: Int = 8,
    val expenseVariationMin: Int = 0,
    val expenseVariationMax: Int = 0,
    /**
     * Per-category symmetric percent ranges overriding the scalar expense range.
     * Keyed by normalized category; events whose category is present here vary
     * within their own range instead of the aggregate one.
     */
    val expenseCategoryVariation: Map<String, IntRange> = emptyMap(),
    val surpriseProbability: Double = 0.15,
    val surpriseCheckIntervalDays: Int = 14,
    val surpriseAmountMin: Int = 2000,
    val surpriseAmountMax: Int = 15000,
    val worstPercentile: Double = 0.1,
    val includeDailyPercentiles: Boolean = false,
)

data class DailyPercentilePoint(
    val dayIndex: Int,
    val date: LocalDate,
    val worst10Cents: Long,
    val medianCents: Long,
    val best90Cents: Long,
)

data class MonteCarloResult(
    val worst_10_balance_cents: Long,
    val median_balance_cents: Long,
    val best_90_balance_cents: Long,
    val worst_10_ending_balance_cents: Long = worst_10_balance_cents,
    val median_ending_balance_cents: Long = median_balance_cents,
    val best_90_ending_balance_cents: Long = best_90_balance_cents,
    val probability_negative_pct: Double,
    val runs: Int,
    val negative_runs: Int,
    val most_common_first_negative_date: String?,
    val negative_window_start: String?,
    val negative_window_end: String?,
    val dailyPercentiles: List<DailyPercentilePoint> = emptyList(),
)

class MonteCarloEngine(
    private val params: MonteCarloParams = MonteCarloParams(),
) {

    fun runSimulation(
        balanceCents: Long,
        events: List<ForecastEvent>,
        today: LocalDate = LocalDate.now(),
    ): MonteCarloResult {
        val rng = Random(params.seed)
        val endingBalances = mutableListOf<Long>()
        val lowestBalances = mutableListOf<Long>()
        var negativeRuns = 0
        val negativeDates = mutableListOf<String>()
        var earliestNegDate: String? = null
        var latestNegDate: String? = null

        val runCount = max(0, params.runs)
        val dailyBalancesPerRun = if (params.includeDailyPercentiles && runCount > 0) {
            Array(runCount) { LongArray(91) }
        } else null

        repeat(runCount) { runIdx ->
            val scenario = generateScenarioTimeline(events, rng, today)
            val summary = simulateScenarioWithDaily(
                balanceCents = balanceCents,
                scenarioTimeline = scenario,
                today = today,
                dailyBalances = dailyBalancesPerRun?.get(runIdx),
            )

            endingBalances.add(summary.endingBalance)
            lowestBalances.add(summary.lowestBalance)
            summary.firstNegativeDate?.let { date ->
                negativeRuns += 1
                negativeDates.add(date)
                if (earliestNegDate == null || date < earliestNegDate!!) earliestNegDate = date
                if (latestNegDate == null || date > latestNegDate!!) latestNegDate = date
            }
        }

        endingBalances.sort()
        lowestBalances.sort()

        val medianLowest = getMedian(lowestBalances)
        val worst10Lowest = percentile(lowestBalances, params.worstPercentile)
        val best90Lowest = percentile(lowestBalances, 0.90)

        val medianEnding = getMedian(endingBalances)
        val worst10Ending = percentile(endingBalances, params.worstPercentile)
        val best90Ending = percentile(endingBalances, 0.90)

        val mostCommonNegDate = negativeDates.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }
            ?.key

        val dailyPercentiles = if (dailyBalancesPerRun != null && runCount > 0) {
            val points = mutableListOf<DailyPercentilePoint>()
            for (dayIdx in 0..90) {
                val daySlice = LongArray(runCount)
                for (runIdx in 0 until runCount) {
                    daySlice[runIdx] = dailyBalancesPerRun[runIdx][dayIdx]
                }
                daySlice.sort()
                val sortedList = daySlice.toList()

                val med = getMedian(sortedList)
                var w10 = percentile(sortedList, params.worstPercentile)
                var b90 = percentile(sortedList, 0.90)

                w10 = w10.coerceAtMost(med)
                b90 = b90.coerceAtLeast(med)

                val pointDate = if (dayIdx == 0) today else today.plusDays((dayIdx - 1).toLong())
                points.add(
                    DailyPercentilePoint(
                        dayIndex = dayIdx,
                        date = pointDate,
                        worst10Cents = w10,
                        medianCents = med,
                        best90Cents = b90,
                    )
                )
            }
            points
        } else {
            emptyList()
        }

        return MonteCarloResult(
            worst_10_balance_cents = worst10Lowest,
            median_balance_cents = medianLowest,
            best_90_balance_cents = best90Lowest,
            worst_10_ending_balance_cents = worst10Ending,
            median_ending_balance_cents = medianEnding,
            best_90_ending_balance_cents = best90Ending,
            probability_negative_pct = if (runCount > 0) negativeRuns * 100.0 / runCount else 0.0,
            runs = runCount,
            negative_runs = negativeRuns,
            most_common_first_negative_date = mostCommonNegDate,
            negative_window_start = earliestNegDate,
            negative_window_end = latestNegDate,
            dailyPercentiles = dailyPercentiles,
        )
    }

    private fun generateScenarioTimeline(
        baseTimeline: List<ForecastEvent>,
        rng: Random,
        today: LocalDate,
    ): List<ForecastEvent> {
        val scenario = mutableListOf<ForecastEvent>()

        for (event in baseTimeline) {
            val adjustedAmount = if (event.type == "income") {
                val variationPercent = if (params.incomeVariationMin != 0 || params.incomeVariationMax != 0) {
                    rng.nextInt(
                        params.incomeVariationMin,
                        params.incomeVariationMax + 1,
                    )
                } else 0
                max(0L, scaleCentsByPercent(event.amount_cents, variationPercent))
            } else {
                val categoryRange = event.category?.let { params.expenseCategoryVariation[it] }
                val variationPercent = when {
                    categoryRange != null && !categoryRange.isEmpty() ->
                        rng.nextInt(categoryRange.first, categoryRange.last + 1)
                    params.expenseVariationMin != 0 || params.expenseVariationMax != 0 ->
                        rng.nextInt(
                            params.expenseVariationMin,
                            params.expenseVariationMax + 1,
                        )
                    else -> 0
                }
                max(0L, scaleCentsByPercent(event.amount_cents, variationPercent))
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
                        ).toLong()
                        scenario.add(
                            ForecastEvent(
                                date = surpriseDay,
                                description = "Unexpected Expense",
                                amount_cents = surpriseAmount,
                                type = "expense",
                            ),
                        )
                    }
                }
            }
        }

        return scenario.sortedWith(compareBy({ it.date }, { if (it.type == "income") 0 else 1 }))
    }

    private data class ScenarioSummary(
        val endingBalance: Long,
        val lowestBalance: Long,
        val firstNegativeDate: String?,
    )

    /**
     * Simulates a scenario timeline and optionally populates an array of 91 daily balances
     * (dayIndex 0 = opening balance on today, dayIndex 1..90 = end-of-day balances for today through today+89).
     * Events on the same date are processed with income before expense.
     */
    private fun simulateScenarioWithDaily(
        balanceCents: Long,
        scenarioTimeline: List<ForecastEvent>,
        today: LocalDate,
        dailyBalances: LongArray?,
    ): ScenarioSummary {
        var runningBalance = balanceCents
        var lowestBalance = balanceCents
        var firstNegativeDate: String? = null

        if (dailyBalances != null && dailyBalances.size >= 91) {
            dailyBalances[0] = balanceCents
            val eventMap = scenarioTimeline.groupBy { it.date }
            for (dayIdx in 1..90) {
                val currentDate = today.plusDays((dayIdx - 1).toLong())
                val dayEvents = eventMap[currentDate].orEmpty().sortedWith(compareBy { if (it.type == "income") 0 else 1 })
                for (event in dayEvents) {
                    runningBalance += if (event.type == "income") event.amount_cents.toLong() else -event.amount_cents.toLong()
                    if (runningBalance < lowestBalance) {
                        lowestBalance = runningBalance
                    }
                    if (firstNegativeDate == null && runningBalance < 0L) {
                        firstNegativeDate = currentDate.toString()
                    }
                }
                dailyBalances[dayIdx] = runningBalance
            }
            val afterDay90Events = scenarioTimeline.filter { it.date > today.plusDays(89) }
            for (event in afterDay90Events) {
                runningBalance += if (event.type == "income") event.amount_cents.toLong() else -event.amount_cents.toLong()
                if (runningBalance < lowestBalance) {
                    lowestBalance = runningBalance
                }
                if (firstNegativeDate == null && runningBalance < 0L) {
                    firstNegativeDate = event.date.toString()
                }
            }
        } else {
            for (event in scenarioTimeline) {
                runningBalance += if (event.type == "income") event.amount_cents.toLong() else -event.amount_cents.toLong()
                if (runningBalance < lowestBalance) {
                    lowestBalance = runningBalance
                }
                if (firstNegativeDate == null && runningBalance < 0L) {
                    firstNegativeDate = event.date.toString()
                }
            }
        }

        return ScenarioSummary(
            endingBalance = runningBalance,
            lowestBalance = lowestBalance,
            firstNegativeDate = firstNegativeDate,
        )
    }

    /**
     * Computes nearest-rank percentile for a sorted list of Long values.
     * Formula: index = ceil(p * count) - 1, clamped to 0..count-1.
     * For count <= 0, returns 0L.
     * For count = 1, returns the single value.
     *
     * NOTE: This engine uses a deliberate mixed percentile convention:
     * 1. Tail risk percentiles (10th/90th) use nearest-rank (`ceil(p * N) - 1`) to preserve authentic
     *    simulated run values without synthetic linear interpolation.
     * 2. Median (50th) uses midpoint averaging for even N (`(a + b) / 2`) via [getMedian] to minimize
     *    median estimation bias across even run counts.
     */
    private fun percentile(sortedValues: List<Long>, percentile: Double): Long {
        if (sortedValues.isEmpty()) return 0L
        val index = max(0, ceil(sortedValues.size * percentile).toInt() - 1)
        return sortedValues[index.coerceAtMost(sortedValues.lastIndex)]
    }

    /**
     * Computes 50th percentile median. Uses exact middle element for odd N, and midpoint averaging
     * `(a + b) / 2` for even N.
     */
    private fun getMedian(sortedValues: List<Long>): Long {
        if (sortedValues.isEmpty()) return 0L
        val middle = sortedValues.size / 2
        return if (sortedValues.size % 2 == 1) {
            sortedValues[middle]
        } else {
            (sortedValues[middle - 1] + sortedValues[middle]) / 2L
        }
    }
}
