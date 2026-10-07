package com.montecarlo.ledger.contract

/**
 * Monte Carlo overlay of `contracts/simulation.md` and aggregation of `contracts/risk.md`.
 *
 * Draw order is part of the contract and is consumed sequentially from a single [SplitMix64]
 * instance seeded once per simulation. All arithmetic is integer-only.
 */
object ContractSimulation {

    /**
     * Runs the simulation for [scenario] over the canonical in-window [baseEvents] and returns the
     * risk block. [projectedLowPointCents] is the deterministic forecast minimum.
     */
    fun simulate(
        scenario: ContractScenario,
        baseEvents: List<ContractEvent>,
        projectedLowPointCents: Long,
    ): ContractRiskResult {
        val params = scenario.simulation ?: ContractSimulationParams()
        val rng = SplitMix64(params.seed)
        val runs = params.runs
        val endings = LongArray(runs)
        val troughs = LongArray(runs)
        var negativeRuns = 0

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

        return ContractRiskResult(
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
