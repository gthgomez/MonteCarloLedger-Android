package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.util.LedgerDate
import com.montecarlo.ledger.util.safeAddCents
import com.montecarlo.ledger.util.safeSubtractCents
import java.math.BigInteger
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One held-out month, scored against the month-end net and the within-month minimum. */
data class BacktestObservation(
    val month: String,
    /** Ledger balance immediately before the month, from rows dated before its first day. */
    val openingCents: Long,
    /** Realized income minus expense for the month; adjustment rows are excluded. */
    val actualNetCents: Long,
    /** Realized `min(opening, running balance after each row)`, ordered date then money-in first. */
    val actualTroughCents: Long,
    /** Model P10/P50/P90 of the month's ending balance from a zero opening, i.e. of its net. */
    val monthEndP10Cents: Long,
    val monthEndP50Cents: Long,
    val monthEndP90Cents: Long,
    /** Model P10/P50/P90 of each run's within-month minimum balance. */
    val troughP10Cents: Long,
    val troughP50Cents: Long,
    val troughP90Cents: Long,
    /** Model chance (0..1) that a run's balance went below zero at any point in the month. */
    val predictedDipProbability: Double,
) {
    val withinMonthEndBand: Boolean get() = actualNetCents in monthEndP10Cents..monthEndP90Cents
    val withinTroughBand: Boolean get() = actualTroughCents in troughP10Cents..troughP90Cents
    val actualDippedNegative: Boolean get() = actualTroughCents < 0L
}

/**
 * Out-of-sample report. The month-end band checks the calibrator's own monthly-variance unit with no
 * surprises; the trough band and [dipBrier] check a zero-surprise path quantity. The dip baseline is
 * the **training** window's realized dip rate — a holdout-fitted base rate would score the model
 * against its own scored outcomes.
 */
data class BacktestReport(
    val observations: List<BacktestObservation>,
    val trainingCalibration: MonteCarloCalibration,
    val trainingMonths: Int,
    val holdoutMonths: Int,
    val seed: Long,
    val runs: Int,
    val nominalCoverage: Double = RiskBacktest.NOMINAL_COVERAGE,
    val trainingDipMonths: Int = 0,
    /** Rows with a non-ISO date, and rows in the as-of month or later; both are excluded. */
    val unparseableDateRows: Int = 0,
    val excludedIncompleteMonthRows: Int = 0,
) {
    val sampleSize: Int get() = observations.size
    val trainingDipRate: Double
        get() = if (trainingMonths == 0) 0.0 else trainingDipMonths.toDouble() / trainingMonths

    val empiricalCoverage: Double get() = rate { it.withinMonthEndBand }
    val coverageError: Double get() = abs(empiricalCoverage - nominalCoverage)
    val troughCoverage: Double get() = rate { it.withinTroughBand }

    /** Mean squared error of the predicted dip probability against the realized dip outcome. */
    val dipBrier: Double get() = observations.sumOf { observation ->
        val p = observation.predictedDipProbability
        val o = if (observation.actualDippedNegative) 1.0 else 0.0
        (p - o) * (p - o)
    }.let { if (sampleSize == 0) 0.0 else it / sampleSize }

    /** Brier score of the constant **training** dip rate, scored out of sample. */
    val dipBaseRateBrier: Double get() = observations.sumOf { observation ->
        val o = if (observation.actualDippedNegative) 1.0 else 0.0
        (trainingDipRate - o) * (trainingDipRate - o)
    }.let { if (sampleSize == 0) 0.0 else it / sampleSize }

    /**
     * `1 - dipBrier / training baseline`. Forced to 0.0 **only** when the baseline Brier is exactly
     * zero: the constant training rate already matches every scored outcome (all held-out months dip
     * with a training rate of 1, or none dip with a rate of 0). A nonzero baseline is never
     * suppressed; if the model merely matches it the formula also yields 0.0, and a worse model
     * yields a negative score.
     */
    val dipSkillScore: Double get() = if (dipBaseRateBrier <= 0.0) 0.0 else 1.0 - dipBrier / dipBaseRateBrier

    private fun rate(predicate: (BacktestObservation) -> Boolean): Double =
        if (sampleSize == 0) 0.0 else observations.count(predicate).toDouble() / sampleSize
}

/**
 * Deterministic, seeded out-of-sample backtest for the Monte Carlo risk model.
 *
 * Months are split chronologically; calibration and the intra-month timing baseline use training
 * rows only. Both runs use one zero-surprise [MonteCarloParams]:
 *
 *  - **Month-end**: one income event on the first day and one expense on the last, from a zero
 *    opening. This isolates the calibrator's monthly-total variance from any timing approximation, so
 *    a coverage miss here is attributable to calibration.
 *  - **Path**: the same monthly totals on a training-derived payday and four weekly buckets, from the
 *    ledger balance before the month. This is the only layout that can produce a non-degenerate trough.
 *
 * Surprises are suppressed in both runs. The baseline expense is the mean of realized monthly totals,
 * which already contains irregular charges; the harness has no verified recurring/irregular
 * decomposition to subtract them, so injecting the calibrated surprise layer would count them twice.
 * A one-month window is not the product's 90-day headline. See `docs/risk-backtest.md` for limits.
 */
object RiskBacktest {

    const val NOMINAL_COVERAGE = 0.80

    /** Below this many distinct months there is nothing meaningful to hold out. */
    const val MIN_MONTHS = 3

    private const val BUCKET_COUNT = 4

    /**
     * @param today the as-of boundary: rows dated in this month or later are excluded. Required, so
     *   the result cannot silently depend on the wall clock; the calibrator receives it too.
     * @param calibration test override; production callers use the real calibrator.
     */
    fun run(
        transactions: List<TransactionEntity>,
        today: LocalDate,
        seed: Long = 42,
        runs: Int = 500,
        trainingFraction: Double = 0.5,
        calibration: ((List<TransactionEntity>) -> MonteCarloCalibration)? = null,
    ): BacktestReport {
        val todayMonth = YearMonth.from(today)
        var unparseable = 0
        var excluded = 0
        val dated = ArrayList<DatedRow>(transactions.size)
        for (txn in transactions) {
            val date = LedgerDate.parseIsoOrNull(txn.date)
            if (date == null) {
                unparseable += 1
                continue
            }
            if (YearMonth.from(date) >= todayMonth) {
                excluded += 1
                continue
            }
            // Canonical ISO keeps the string-based opening-balance owner correct.
            dated += DatedRow(txn.copy(date = date.toString()), date)
        }

        val months = dated.map { YearMonth.from(it.date) }.distinct().sorted()
        if (months.size < MIN_MONTHS) {
            return BacktestReport(
                observations = emptyList(),
                trainingCalibration = MonteCarloCalibration.defaults(),
                trainingMonths = 0,
                holdoutMonths = 0,
                seed = seed,
                runs = runs,
                unparseableDateRows = unparseable,
                excludedIncompleteMonthRows = excluded,
            )
        }

        val split = (months.size * trainingFraction).roundToInt().coerceIn(1, months.size - 1)
        val training = months.take(split)
        val holdout = months.drop(split)
        val trainingSet = training.toSet()
        val trainingRows = dated.filter { YearMonth.from(it.date) in trainingSet }
        val calibrate = calibration ?: { txns -> MonteCarloCalibrator.calibrate(txns, today) }
        val trainCalibration = calibrate(trainingRows.map { it.txn })

        val baselineIncome = meanPositive(training.map { monthlyTotal(dated, it, "income") }).roundToLong()
        val baselineExpense = meanPositive(training.map { monthlyTotal(dated, it, "expense") }).roundToLong()
        val timing = timingBaseline(trainingRows, baselineExpense)

        val observations = holdout.mapIndexed { index, month ->
            val start = month.atDay(1)
            val opening = openingFor(month, dated)
            val params = trainCalibration.toParams(seed + index, runs)
            val monthEnd = MonteCarloEngine(params).runSimulation(
                balanceCents = 0L,
                events = listOfNotNull(
                    baselineIncome.takeIf { it > 0L }
                        ?.let { ForecastEvent(start, "Backtest income", it, "income") },
                    baselineExpense.takeIf { it > 0L }
                        ?.let { ForecastEvent(month.atEndOfMonth(), "Backtest expense", it, "expense") },
                ),
                today = start,
            )
            // `today = start` fixes each scored run to this month. The product's 90-day horizon
            // and the engine's wall-clock default are both deliberately not used.
            val path = MonteCarloEngine(params).runSimulation(
                balanceCents = opening,
                events = pathEvents(month, baselineIncome, timing),
                today = start,
            )
            BacktestObservation(
                month = month.toString(),
                openingCents = opening,
                actualNetCents = safeSubtractCents(
                    monthlyTotal(dated, month, "income"),
                    monthlyTotal(dated, month, "expense"),
                ),
                actualTroughCents = trough(month, opening, dated),
                monthEndP10Cents = monthEnd.worst_10_ending_balance_cents,
                monthEndP50Cents = monthEnd.median_ending_balance_cents,
                monthEndP90Cents = monthEnd.best_90_ending_balance_cents,
                troughP10Cents = path.worst_10_balance_cents,
                troughP50Cents = path.median_balance_cents,
                troughP90Cents = path.best_90_balance_cents,
                predictedDipProbability = path.probability_negative_pct / 100.0,
            )
        }

        val trainingDipMonths = training.count { trough(it, openingFor(it, dated), dated) < 0L }
        return BacktestReport(
            observations = observations,
            trainingCalibration = trainCalibration,
            trainingMonths = training.size,
            holdoutMonths = holdout.size,
            seed = seed,
            runs = runs,
            trainingDipMonths = trainingDipMonths,
            unparseableDateRows = unparseable,
            excludedIncompleteMonthRows = excluded,
        )
    }

    private class DatedRow(val txn: TransactionEntity, val date: LocalDate)

    private class Timing(val paydayOfMonth: Int?, val expenseBucketCents: LongArray)

    /**
     * Engine parameters for a scored month. The calibrated surprise fields are deliberately not
     * copied: the baseline expense already includes irregular spend, so injecting surprises would
     * double-count it and bias both the month-end band and the path dip probability.
     */
    private fun MonteCarloCalibration.toParams(seed: Long, runs: Int): MonteCarloParams = MonteCarloParams(
        seed = seed,
        runs = runs,
        incomeVariationMin = incomeVariationMin,
        incomeVariationMax = incomeVariationMax,
        expenseVariationMin = expenseVariationMin,
        expenseVariationMax = expenseVariationMax,
        expenseCategoryVariation = expenseCategoryVariation,
        surpriseProbability = 0.0,
        surpriseAmountMin = 0,
        surpriseAmountMax = 0,
        includeDailyPercentiles = false,
    )

    /** Median training payday and weekly expense shares, both from training rows only. */
    private fun timingBaseline(training: List<DatedRow>, budgetCents: Long): Timing {
        val incomeDays = training.filter { it.txn.type == "income" }.map { it.date.dayOfMonth }.sorted()
        val payday = incomeDays.takeIf { it.isNotEmpty() }?.let {
            it[(ceil(it.size * 0.5).toInt() - 1).coerceIn(0, it.lastIndex)]
        }

        val bucketTotals = LongArray(BUCKET_COUNT)
        var expenseTotal = 0L
        for (row in training) {
            if (row.txn.type != "expense") continue
            val amount = abs(row.txn.amount_cents)
            if (amount == 0L) continue
            val bucket = bucketOf(row.date)
            bucketTotals[bucket] = safeAddCents(bucketTotals[bucket], amount)
            expenseTotal = safeAddCents(expenseTotal, amount)
        }

        val buckets = LongArray(BUCKET_COUNT)
        if (expenseTotal > 0L && budgetCents > 0L) {
            val lastNonEmpty = bucketTotals.indexOfLast { it > 0L }
            var assigned = 0L
            for (index in 0 until lastNonEmpty) {
                val part = BigInteger.valueOf(budgetCents)
                    .multiply(BigInteger.valueOf(bucketTotals[index]))
                    .divide(BigInteger.valueOf(expenseTotal))
                    .toLong()
                buckets[index] = part
                assigned = safeAddCents(assigned, part)
            }
            buckets[lastNonEmpty] = safeSubtractCents(budgetCents, assigned)
        }
        return Timing(paydayOfMonth = payday, expenseBucketCents = buckets)
    }

    /**
     * Dated events for one month. `MonteCarloEngine.generateScenarioTimeline` reads
     * `baseTimeline.last()` **before** it sorts, so this list is built chronologically and always
     * ends with a month-end expense. When the last bucket is empty that event carries zero cents;
     * it has no path effect, but it keeps the terminal date explicit for the engine's timeline scan.
     * Surprises are disabled in [toParams], so the engine adds none.
     */
    private fun pathEvents(month: YearMonth, baselineIncome: Long, timing: Timing): List<ForecastEvent> {
        val payday = timing.paydayOfMonth
        if (baselineIncome <= 0L && timing.expenseBucketCents.all { it == 0L }) return emptyList()
        return buildList {
            if (baselineIncome > 0L && payday != null) {
                add(
                    ForecastEvent(
                        date = month.atDay(payday.coerceIn(1, month.lengthOfMonth())),
                        description = "Backtest income",
                        amount_cents = baselineIncome,
                        type = "income",
                    )
                )
            }
            for (index in 0 until BUCKET_COUNT - 1) {
                val amount = timing.expenseBucketCents[index]
                if (amount > 0L) {
                    add(ForecastEvent(bucketEnd(month, index), "Backtest expense ${index + 1}", amount, "expense"))
                }
            }
            add(
                ForecastEvent(
                    date = month.atEndOfMonth(),
                    description = "Backtest expense $BUCKET_COUNT",
                    amount_cents = timing.expenseBucketCents[BUCKET_COUNT - 1],
                    type = "expense",
                )
            )
        }.sortedWith(compareBy<ForecastEvent>({ it.date }, { if (it.type == "income") 0 else 1 }))
    }

    private fun bucketOf(date: LocalDate): Int = when {
        date.dayOfMonth <= 7 -> 0
        date.dayOfMonth <= 14 -> 1
        date.dayOfMonth <= 21 -> 2
        else -> 3
    }

    private fun bucketEnd(month: YearMonth, index: Int): LocalDate = when (index) {
        0 -> month.atDay(7)
        1 -> month.atDay(14)
        else -> month.atDay(21)
    }

    /**
     * Balance before [month] from the rows the ledger already contains: the product's opening rule.
     * Earlier periods — including earlier scored months — may feed it; nothing at or after the cutoff.
     */
    private fun openingFor(month: YearMonth, rows: List<DatedRow>): Long = CashForecastTiming.opening(
        confirmedBankCents = null,
        cash = rows.map { it.txn },
        asOf = month.atDay(1).minusDays(1),
    )

    /**
     * Realized within-month minimum. Amounts are summed by their recorded sign (the opening owner's
     * authority) after ordering by date with money-in before money-out, mirroring the engine.
     */
    private fun trough(month: YearMonth, opening: Long, rows: List<DatedRow>): Long {
        var running = opening
        var lowest = opening
        rows.asSequence()
            .filter { YearMonth.from(it.date) == month }
            .sortedWith(compareBy<DatedRow>({ it.date }, { if (it.txn.amount_cents >= 0L) 0 else 1 }))
            .forEach { row ->
                running = safeAddCents(running, row.txn.amount_cents)
                if (running < lowest) lowest = running
            }
        return lowest
    }

    private fun monthlyTotal(rows: List<DatedRow>, month: YearMonth, type: String): Long =
        rows.asSequence()
            .filter { YearMonth.from(it.date) == month && it.txn.type == type }
            .sumOf { abs(it.txn.amount_cents) }

    private fun meanPositive(values: List<Long>): Double {
        val positive = values.filter { it > 0L }
        return if (positive.isEmpty()) 0.0 else positive.sum().toDouble() / positive.size
    }
}
