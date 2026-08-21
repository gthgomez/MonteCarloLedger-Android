package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.domain.Categories
import com.montecarlo.ledger.util.LedgerDate
import com.montecarlo.ledger.util.centsToDisplay
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
    /**
     * Per-category symmetric percent ranges derived from that category's own monthly
     * volatility. Only categories with enough history appear; events in other
     * categories fall back to the aggregate [expenseVariationMin]/[expenseVariationMax].
     */
    val expenseCategoryVariation: Map<String, IntRange> = emptyMap(),
    val surpriseProbability: Double,
    val surpriseAmountMin: Int,
    val surpriseAmountMax: Int,
    val monthsCovered: Int,
) {
    /** True when there is enough ledger history to trust the derived ranges. */
    val isCalibrated: Boolean get() = monthsCovered >= MIN_CALIBRATED_MONTHS

    companion object {
        const val MIN_CALIBRATED_MONTHS = 3
        const val MIN_CATEGORY_MONTHS = 2

        /** Fallback matching the previous hardcoded engine defaults. */
        fun defaults(): MonteCarloCalibration = MonteCarloCalibration(
            incomeVariationMin = -8,
            incomeVariationMax = 8,
            expenseVariationMin = 0,
            expenseVariationMax = 0,
            expenseCategoryVariation = emptyMap(),
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

        // Per-category ranges: a volatile groceries budget and a flat rent bill should
        // not share one variance number. Sparse categories (fewer than
        // MIN_CATEGORY_MONTHS distinct months) are excluded rather than guessed.
        val categoryVariation = categoryRanges(dated)

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
            expenseCategoryVariation = categoryVariation,
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

    /**
     * Derives a symmetric percent range per expense category from that category's own
     * monthly totals. Requires at least [MonteCarloCalibration.MIN_CATEGORY_MONTHS]
     * distinct months of history in the category; otherwise the category is omitted
     * so events fall back to the aggregate range.
     */
    private fun categoryRanges(
        dated: List<Pair<TransactionEntity, YearMonth>>,
    ): Map<String, IntRange> {
        return dated.asSequence()
            .filter { it.first.type == "expense" && Categories.normalizeOrUncategorized(it.first.category) != Categories.UNCATEGORIZED }
            .groupBy { Categories.normalize(it.first.category) }
            .mapNotNull { (category, txns) ->
                val months = txns.groupBy({ it.second }, { it.first })
                    .mapValues { (_, monthTxns) -> monthTxns.sumOf { abs(it.amount_cents) } }
                    .values.toList()
                val range = cvPercentRange(months, floor = 3, ceiling = 40, minimumMonths = MonteCarloCalibration.MIN_CATEGORY_MONTHS)
                if (range > 0) category to (range..range) else null
            }
            .toMap()
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

/**
 * A single human-readable observation about why the 3-month estimate looks the way
 * it does. Closes the loop between statistical calibration and user understanding:
 * the bands stop being decoration when the app can say what drives them.
 */
data class ForecastInsight(
    val label: String,
    val detail: String,
)

/**
 * Derives plain-language insights from the same history that calibrated the engine.
 *
 * Deterministic and side-effect free; ordered most-relevant first and capped so the
 * UI never renders a wall of caveats.
 */
object MonteCarloInsights {

    private const val MAX_INSIGHTS = 4
    private const val MIN_CATEGORY_SHARE = 0.10

    fun generate(
        transactions: List<TransactionEntity>,
        today: LocalDate,
        calibration: MonteCarloCalibration,
        recurringPatterns: Set<String> = RecurringDetector.detect(transactions)
            .map { Categories.normalize(it.pattern) }
            .toSet(),
    ): List<ForecastInsight> {
        if (transactions.isEmpty()) return emptyList()

        val insights = mutableListOf<ForecastInsight>()

        if (!calibration.isCalibrated) {
            val logged = calibration.monthsCovered
            insights += ForecastInsight(
                label = "Using default assumptions",
                detail = if (logged == 0) {
                    "Log a few weeks of spending to start personalizing your 3-month estimate."
                } else {
                    "$logged month(s) logged so far — ${MonteCarloCalibration.MIN_CALIBRATED_MONTHS} months personalizes every band below."
                }
            )
        }

        val dated = transactions.mapNotNull { txn ->
            val date = LedgerDate.parseIsoOrNull(txn.date) ?: return@mapNotNull null
            txn to YearMonth.from(date)
        }
        val expenseItems = dated.filter { it.first.type == "expense" && abs(it.first.amount_cents) > 0L }
        val totalExpense = expenseItems.sumOf { abs(it.first.amount_cents) }.toDouble()

        if (totalExpense > 0.0) {
            val shares = expenseItems
                .groupBy { Categories.normalize(it.first.category) }
                .mapValues { (_, items) -> items.sumOf { abs(it.first.amount_cents) } }
                .filterKeys { it != Categories.UNCATEGORIZED }

            // Volatility driver: highest-variance well-represented category.
            val driver = calibration.expenseCategoryVariation.entries
                .filter { (shares[it.key]?.toDouble() ?: 0.0) / totalExpense >= MIN_CATEGORY_SHARE }
                .maxByOrNull { it.value.first }
            if (driver != null && driver.value.first >= 15) {
                insights += ForecastInsight(
                    label = "\"${driver.key.replaceFirstChar { it.uppercase() }}\" drives your forecast swings",
                    detail = "Swings roughly ±${driver.value.first}% month to month — the largest source of uncertainty in your estimate."
                )
            }

            // Steady anchor: lowest-variance well-represented category.
            val anchor = calibration.expenseCategoryVariation.entries
                .filter { (shares[it.key]?.toDouble() ?: 0.0) / totalExpense >= MIN_CATEGORY_SHARE }
                .minByOrNull { it.value.first }
            if (anchor != null && anchor.value.first <= 6) {
                insights += ForecastInsight(
                    label = "\"${anchor.key.replaceFirstChar { it.uppercase() }}\" is a steady anchor",
                    detail = "Holding within ±${anchor.value.first}% month to month — a reliable baseline for planning."
                )
            }

            // Surprise cadence: how often irregular charges actually land.
            val irregular = expenseItems
                .filter { Categories.normalize(it.first.description) !in recurringPatterns }
                .map { abs(it.first.amount_cents) }
                .sorted()
            if (irregular.size >= 3 && calibration.surpriseProbability > 0.05) {
                val spikeThreshold = percentileOf(irregular, 0.75)
                if (spikeThreshold > 0L) {
                    val spikedMonths = expenseItems
                        .filter { abs(it.first.amount_cents) >= spikeThreshold && Categories.normalize(it.first.description) !in recurringPatterns }
                        .map { it.second }
                        .toSet()
                    val typical = percentileOf(irregular, 0.5)
                    insights += ForecastInsight(
                        label = "Unexpected charges are part of your pattern",
                        detail = "They landed in ${spikedMonths.size} of the last ${calibration.monthsCovered} months, typically around ${centsToDisplay(typical)} each."
                    )
                }
            }

            // Income stability only when we have income history to speak about.
            if (calibration.incomeVariationMax > 0) {
                insights += ForecastInsight(
                    label = "Income swings ±${calibration.incomeVariationMax}%",
                    detail = "Your paycheck amounts vary month to month; the estimate accounts for that."
                )
            } else if (dated.none { it.first.type == "income" }) {
                insights += ForecastInsight(
                    label = "Income not calibrated yet",
                    detail = "Record a couple of paychecks and the bands will reflect how steady yours really is."
                )
            }
        }

        return insights.take(MAX_INSIGHTS)
    }

    private fun percentileOf(sortedValues: List<Long>, fraction: Double): Long {
        if (sortedValues.isEmpty()) return 0L
        val index = (fraction * (sortedValues.size - 1)).toInt().coerceIn(0, sortedValues.lastIndex)
        return sortedValues[index]
    }
}
