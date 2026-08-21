package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Historical calibration for [MonteCarloEngine].
 *
 * Instead of hardcoded defaults, variation ranges and surprise statistics are derived
 * from the user's own ledger history:
 *  - Income/expense variation: coefficient of variation (stddev / mean) of monthly
 *    totals, expressed as a symmetric percent range around each simulated event.
 *  - Surprises: expenses whose description does not match any detected recurring
 *    pattern are treated as irregular; their frequency and size distribution shape
 *    the surprise probability and amount range.
 *
 * Calibration is honest about its sample: [monthsCovered] drives the basis label in
 * the UI, and below [MIN_CALIBRATED_MONTHS] the caller should fall back to defaults.
 */
data class MonteCarloCalibration(
    val incomeVariationMin: Int,
    val incomeVariationMax: Int,
    val expenseVariationMin: Int,
    val expenseVariationMax: Int,
    val surpriseProbability: Double,
    val surpriseAmountMin: Int,
    val surpriseAmountMax: Int,
    val monthsCovered: Int,
) {
    /** True when there is enough ledger history to trust the derived ranges. */
    val isCalibrated: Boolean get() = monthsCovered >= MIN_CALIBRATED_MONTHS

    companion object {
        const val MIN_CALIBRATED_MONTHS = 3

        /** Fallback matching the previous hardcoded engine defaults. */
        fun defaults(): MonteCarloCalibration = MonteCarloCalibration(
            incomeVariationMin = -8,
            incomeVariationMax = 8,
            expenseVariationMin = 0,
            expenseVariationMax = 0,
            surpriseProbability = 0.15,
            surpriseAmountMin = 2_000,
            surpriseAmountMax = 15_000,
            monthsCovered = 0,
        )
    }
}

object MonteCarloCalibrator {

    fun calibrate(
        transactions: List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
        recurringPatterns: Set<String> = RecurringDetector.detect(transactions)
            .map { it.pattern.lowercase(Locale.ROOT) }
            .toSet(),
    ): MonteCarloCalibration {
        if (transactions.isEmpty()) return MonteCarloCalibration.defaults()

        val dated = transactions.mapNotNull { txn ->
            val date = runCatching { LocalDate.parse(txn.date.trim()) }.getOrNull() ?: return@mapNotNull null
            txn to YearMonth.from(date)
        }

        val firstMonth = dated.minOfOrNull { it.second } ?: return MonteCarloCalibration.defaults()
        val lastMonth = YearMonth.from(today)
        val monthsCovered = monthsBetween(firstMonth, lastMonth).coerceAtLeast(1)

        val monthlyIncome = monthlyTotals(dated, type = "income")
        val monthlyExpense = monthlyTotals(dated, type = "expense")

        val incomeRange = cvPercentRange(monthlyIncome, floor = 2, ceiling = 25, minimumMonths = 2)
        val expenseRange = cvPercentRange(monthlyExpense, floor = 3, ceiling = 30, minimumMonths = 2)

        val irregularCents = dated
            .asSequence()
            .filter { it.first.type == "expense" && abs(it.first.amount_cents) > 0L }
            .filter { normalize(it.first.description) !in recurringPatterns }
            .map { abs(it.first.amount_cents) }
            .sorted()
            .toList()

        var surpriseProbability = 0.15
        var surpriseAmountMin = 2_000
        var surpriseAmountMax = 15_000

        if (irregularCents.isNotEmpty()) {
            // A "surprised" month is one containing at least one unusually large
            // irregular charge (above the 75th percentile of irregular history).
            val spikeThreshold = percentile(irregularCents, 0.75)
            if (spikeThreshold > 0L && monthsCovered > 0) {
                val spikedMonths = dated.asSequence()
                    .filter { it.first.type == "expense" && abs(it.first.amount_cents) >= spikeThreshold }
                    .filter { normalize(it.first.description) !in recurringPatterns }
                    .map { it.second }
                    .toSet()
                surpriseProbability = (spikedMonths.size.toDouble() / monthsCovered)
                    .coerceIn(0.02, 0.5)
            }
            surpriseAmountMin = percentile(irregularCents, 0.5).toInt().coerceIn(500, 100_000)
            surpriseAmountMax = percentile(irregularCents, 0.9).toInt()
                .coerceAtLeast(surpriseAmountMin + 500)
                .coerceAtMost(500_000)
        }

        return MonteCarloCalibration(
            incomeVariationMin = -(incomeRange),
            incomeVariationMax = incomeRange,
            expenseVariationMin = -(expenseRange),
            expenseVariationMax = expenseRange,
            surpriseProbability = surpriseProbability,
            surpriseAmountMin = surpriseAmountMin,
            surpriseAmountMax = surpriseAmountMax,
            monthsCovered = monthsCovered,
        )
    }

    /**
     * Maps the coefficient of variation of monthly totals onto a symmetric percent range.
     * Returns 0 when there is not enough history, so the caller keeps a flat (deterministic)
     * dimension instead of inventing variance it cannot support.
     */
    private fun cvPercentRange(
        monthlyTotalsCents: List<Long>,
        floor: Int,
        ceiling: Int,
        minimumMonths: Int,
    ): Int {
        val positive = monthlyTotalsCents.filter { it > 0L }
        if (positive.size < minimumMonths) return 0

        val mean = positive.sum().toDouble() / positive.size
        if (mean <= 0.0) return 0

        val variance = positive.sumOf { (it - mean) * (it - mean) } / positive.size
        val cvPct = ((sqrt(variance) / mean) * 100.0).toInt()
        return cvPct.coerceIn(floor, ceiling)
    }

    private fun monthlyTotals(
        dated: List<Pair<TransactionEntity, YearMonth>>,
        type: String,
    ): List<Long> {
        return dated.asSequence()
            .filter { it.first.type == type }
            .groupBy({ it.second }, { it.first })
            .mapValues { (_, txns) -> txns.sumOf { abs(it.amount_cents) } }
            .values
            .toList()
    }

    private fun percentile(sortedValues: List<Long>, fraction: Double): Long {
        if (sortedValues.isEmpty()) return 0L
        val index = (fraction * (sortedValues.size - 1)).toInt()
            .coerceIn(0, sortedValues.lastIndex)
        return sortedValues[index]
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun monthsBetween(from: YearMonth, to: YearMonth): Int =
        (to.year - from.year) * 12 + (to.monthValue - from.monthValue)
}
