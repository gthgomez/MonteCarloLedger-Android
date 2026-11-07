package com.montecarlo.ledger.contract

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Monte Carlo overlay of `contracts/simulation.md` and aggregation of `contracts/risk.md`.
 *
 * Draw order is part of the contract and is consumed sequentially from a single [SplitMix64]
 * instance seeded once per simulation. All arithmetic is integer-only.
 */
object ContractSimulation {

    /** A non-normative daily visualization point derived from the canonical simulation paths. */
    data class ContractDailyPoint(
        val dayIndex: Int,
        val date: LocalDate,
        val p10Cents: Long,
        val p50Cents: Long,
        val p90Cents: Long,
    )

    /**
     * Runs the simulation for [scenario] over the canonical in-window [baseEvents] and returns the
     * risk block. [projectedLowPointCents] is the deterministic forecast minimum.
     */
    fun simulate(
        scenario: ContractScenario,
        baseEvents: List<ContractEvent>,
        projectedLowPointCents: Long,
    ): ContractRiskResult = runSimulation(
        scenario = scenario,
        baseEvents = baseEvents,
        projectedLowPointCents = projectedLowPointCents,
        collectDaily = false,
    ).first

    /**
     * Canonical risk **and** daily fan-chart percentiles from the same simulated paths.
     *
     * The chart is a visualization of the canonical model, not a second engine: percentiles are
     * taken from the exact same draws that produce the headline probability and quantiles.
     */
    fun simulateWithDaily(
        scenario: ContractScenario,
        baseEvents: List<ContractEvent>,
        projectedLowPointCents: Long,
    ): Pair<ContractRiskResult, List<ContractDailyPoint>> = runSimulation(
        scenario = scenario,
        baseEvents = baseEvents,
        projectedLowPointCents = projectedLowPointCents,
        collectDaily = true,
    )

    private fun runSimulation(
        scenario: ContractScenario,
        baseEvents: List<ContractEvent>,
        projectedLowPointCents: Long,
        collectDaily: Boolean,
    ): Pair<ContractRiskResult, List<ContractDailyPoint>> {
        val params = scenario.simulation ?: ContractSimulationParams()
        val rng = SplitMix64(params.seed)
        val runs = params.runs
        val endings = LongArray(runs)
        val troughs = LongArray(runs)
        var negativeRuns = 0
        val days = scenario.horizonDays
        val dailyPerRun: Array<LongArray>? =
            if (collectDaily && runs > 0) Array(runs) { LongArray(days + 1) } else null

        for (run in 0 until runs) {
            val events = ArrayList<ContractEvent>(baseEvents.size + 8)

            // Step 2: per-event variation in canonical order. Draws happen only for enabled branches.
            for (event in baseEvents) {
                var amount = event.amountCents
                if (event.type == "income") {
                    if (params.incomeVariationMin != 0 || params.incomeVariationMax != 0) {
                        val pct = rng.nextInt(params.incomeVariationMin, params.incomeVariationMax)
                        amount = maxOf(0L, ContractMoney.scaleCentsByPercent(amount, pct.toLong()))
                    }
                } else {
                    // Contract 1.2: a matching category range overrides the scalar expense range.
                    val categoryRange = event.category?.let { params.expenseCategoryVariation[it] }
                    if (categoryRange != null && !categoryRange.isEmpty()) {
                        val pct = rng.nextInt(categoryRange.first, categoryRange.last)
                        amount = minOf(0L, ContractMoney.scaleCentsByPercent(amount, pct.toLong()))
                    } else if (params.expenseVariationMin != 0 || params.expenseVariationMax != 0) {
                        val pct = rng.nextInt(params.expenseVariationMin, params.expenseVariationMax)
                        amount = minOf(0L, ContractMoney.scaleCentsByPercent(amount, pct.toLong()))
                    }
                }
                events.add(event.copy(amountCents = amount))
            }

            // Step 3: surprise checks. `nextPpmHit` always consumes one draw, even at ppm = 0.
            val checks = scenario.horizonDays / params.surpriseCheckIntervalDays
            var appended = 0
            for (i in 0 until checks) {
                if (rng.nextPpmHit(params.surpriseProbabilityPpm)) {
                    val offset = i * params.surpriseCheckIntervalDays +
                        rng.nextInt(0, params.surpriseCheckIntervalDays - 1)
                    val date = scenario.asOf.plusDays(offset.toLong())
                    if (date.isBefore(scenario.windowEndExclusive)) {
                        val amount = -rng.nextInt(params.surpriseAmountMin, params.surpriseAmountMax).toLong()
                        events.add(
                            ContractEvent(
                                id = null,
                                name = null,
                                date = date,
                                amountCents = amount,
                                type = "expense",
                                sequence = 1,
                                inputIndex = baseEvents.size + appended,
                            )
                        )
                        appended++
                    }
                }
            }

            // Step 4 + 5: canonical order, then deterministic forecast.
            val ordered = events.sortedWith(ContractEngine.EVENT_COMPARATOR)
            val forecast = ContractEngine.forecast(scenario.startingBalanceCents, ordered, scenario.asOf)
            endings[run] = forecast.endingBalanceCents
            troughs[run] = forecast.minimumBalanceCents
            if (forecast.firstNegativeDate != null) negativeRuns++

            val row = dailyPerRun?.get(run)
            if (row != null) {
                row[0] = scenario.startingBalanceCents
                var current = scenario.startingBalanceCents
                var lastIdx = 0
                for (event in ordered) {
                    current = ContractMoney.checkedAdd(current, event.amountCents)
                    val idx = ChronoUnit.DAYS.between(scenario.asOf, event.date).toInt() + 1
                    if (idx in 1..days) {
                        for (k in lastIdx + 1..idx) row[k] = current
                        if (idx <= lastIdx) row[idx] = current
                        lastIdx = maxOf(lastIdx, idx)
                    }
                }
                for (k in lastIdx + 1..days) row[k] = current
            }
        }

        endings.sort()
        troughs.sort()

        val ppm = ContractMoney.roundHalfAway(
            negativeRuns.toLong() * 1_000_000L,
            runs.toLong(),
        )
        val troughP = { num: Long, den: Long -> percentileNearestRank(troughs, num, den) }
        val endingP = { num: Long, den: Long -> percentileNearestRank(endings, num, den) }
        val lowerTrough = percentileNearestRank(
            troughs,
            params.quantileNum.toLong(),
            params.quantileDen.toLong(),
        )

        val daily = dailyPerRun?.let { buildDailyPercentiles(it, scenario, days) } ?: emptyList()

        val risk = ContractRiskResult(
            negativeBalanceProbabilityPpm = ppm,
            minimumBalanceP10Cents = troughP(1, 10),
            minimumBalanceP50Cents = troughP(1, 2),
            minimumBalanceP90Cents = troughP(9, 10),
            endingBalanceP10Cents = endingP(1, 10),
            endingBalanceP50Cents = endingP(1, 2),
            endingBalanceP90Cents = endingP(9, 10),
            projectedLowPointCents = projectedLowPointCents,
            safeToSpendCents = ContractMoney.checkedSubtract(lowerTrough, params.reserveCents),
        )
        return risk to daily
    }

    private fun buildDailyPercentiles(
        rows: Array<LongArray>,
        scenario: ContractScenario,
        days: Int,
    ): List<ContractDailyPoint> {
        if (rows.isEmpty()) return emptyList()
        return (0..days).map { day ->
            val slice = LongArray(rows.size) { rows[it][day] }
            slice.sort()
            val p10 = percentileNearestRank(slice, 1, 10)
            val p50 = percentileNearestRank(slice, 1, 2)
            val p90 = percentileNearestRank(slice, 9, 10)
            val date = if (day == 0) scenario.asOf else scenario.asOf.plusDays((day - 1).toLong())
            ContractDailyPoint(
                dayIndex = day,
                date = date,
                p10Cents = minOf(p10, p50),
                p50Cents = p50,
                p90Cents = maxOf(p90, p50),
            )
        }
    }

    /**
     * The single percentile convention (`contracts/risk.md`): `index = ceil(N * num / den) - 1`,
     * clamped. `P50` for even `N` is the lower middle element; there is no averaging.
     */
    fun percentileNearestRank(sortedAscending: LongArray, num: Long, den: Long): Long {
        require(sortedAscending.isNotEmpty()) { "percentile of empty array" }
        val index = ContractMoney.ceilRatio(sortedAscending.size.toLong(), num, den) - 1L
        return sortedAscending[index.coerceIn(0L, sortedAscending.lastIndex.toLong()).toInt()]
    }
}
