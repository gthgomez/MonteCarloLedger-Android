package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.util.LedgerDate
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * One held-out month evaluated against the model's forecast for that month.
 *
 * The model forecasts a *monthly net cash flow* (income minus expense) because that is the unit
 * [MonteCarloCalibrator] actually calibrates: it derives income/expense variation from monthly
 * totals and surprise frequency from irregular charges. Starting from a zero balance, the simulated
 * ending balance is therefore the model's predicted net for the month, and the actual net is the
 * ground truth drawn from the ledger's own held-out month.
 */
data class BacktestObservation(
    val month: String,
    /** Actual income minus actual expense for that calendar month (all ledger rows). */
    val actualNetCents: Long,
    /**
     * Model P10 of the month's *ending* balance (which, from a zero starting balance, is the P10 of
     * the month's net). The ending-balance quantiles are used — not the running-minimum "lowest
     * balance" quantiles — because a zero-start, net-positive month never dips below its opening
     * balance, so the lowest-balance band would collapse to the degenerate [0, 0].
     */
    val p10NetCents: Long,
    /** Model P50 of the month's ending balance / net. */
    val p50NetCents: Long,
    /** Model P90 of the month's ending balance / net. */
    val p90NetCents: Long,
    /** Model probability (0..1) that the month's net ends negative. */
    val predictedNegativeProbability: Double,
    /** Ground truth: whether the holdout month's actual net was negative. */
    val actualNegative: Boolean,
) {
    /** Whether the actual net fell inside the nominal 80% central band [p10NetCents, p90NetCents]. */
    val withinCentralBand: Boolean get() = actualNetCents in p10NetCents..p90NetCents
}

/**
 * Out-of-sample calibration report for the Monte Carlo risk model (WS4).
 *
 * `empiricalCoverage` is the fraction of held-out months whose actual net landed inside the model's
 * nominal 80% central band (P10-P90). A perfectly calibrated model would score ≈ 0.80; the
 * `coverageError` is the absolute gap. `brierScore` scores the model's predicted negative-net
 * probability against the realised outcome per month (0 = perfect, 0.25 = a coin flip), and
 * `brierSkillScore` compares it to the base-rate ("climatology") forecast — a positive value means
 * the model beats always predicting the historic negative rate.
 *
 * Limits (documented, not hidden):
 *  - It evaluates one forecast unit (the calendar month) at a horizon the model was not designed to
 *    headline (90 days). It is a *necessary* check, not a sufficient one.
 *  - The holdout is small by construction (a personal ledger has few months); a handful of months
 *    cannot resolve a coverage difference smaller than a few tens of a percent.
 *  - `today` is accepted for API symmetry but the harness never reads a clock: windows come only
 *    from transaction dates, so results are reproducible.
 */
data class BacktestReport(
    val observations: List<BacktestObservation>,
    val trainingCalibration: MonteCarloCalibration,
    val trainingMonths: Int,
    val holdoutMonths: Int,
    val seed: Long,
    val runs: Int,
    val nominalCoverage: Double = RiskBacktest.NOMINAL_COVERAGE,
) {
    val sampleSize: Int get() = observations.size

    /** Fraction of held-out months inside the nominal central band. */
    val empiricalCoverage: Double
        get() = if (sampleSize == 0) 0.0 else observations.count { it.withinCentralBand }.toDouble() / sampleSize

    /** Absolute distance from the nominal coverage (0.80 for the P10-P90 band). */
    val coverageError: Double get() = abs(empiricalCoverage - nominalCoverage)

    /** Mean squared error of the predicted negative probability; lower is better. */
    val brierScore: Double
        get() = if (sampleSize == 0) {
            0.0
        } else {
            observations.sumOf { observation ->
                val p = observation.predictedNegativeProbability
                val o = if (observation.actualNegative) 1.0 else 0.0
                (p - o) * (p - o)
            } / sampleSize
        }

    /** Historic negative rate over the holdout — the constant forecast a naive model would use. */
    val baseRate: Double
        get() = if (sampleSize == 0) 0.0 else observations.count { it.actualNegative }.toDouble() / sampleSize

    /** Brier score of the constant base-rate forecast, the reference a skilled model must beat. */
    val baseRateBrier: Double
        get() = if (sampleSize == 0) {
            0.0
        } else {
            observations.sumOf { observation ->
                val o = if (observation.actualNegative) 1.0 else 0.0
                (baseRate - o) * (baseRate - o)
            } / sampleSize
        }

    /** 1 - (model Brier / base-rate Brier). Positive = the model beats climatology. */
    val brierSkillScore: Double
        get() = if (baseRateBrier <= 0.0) 0.0 else 1.0 - brierScore / baseRateBrier
}

/**
 * Deterministic, seeded out-of-sample backtest for the Monte Carlo risk model (WS4).
 *
 * Given a ledger with history it splits the distinct calendar months into a training window and a
 * held-out window, calibrates [MonteCarloCalibration] on the training window only (no lookahead:
 * recurring patterns are detected from training rows too), then replays the model on each held-out
 * month and scores the resulting forecast against what actually happened.
 *
 * The harness is a pure function of its inputs plus [seed]: same ledger and seed ⇒ same report.
 * This is deliberately a *test-time / offline* tool, not a dashboard code path — it runs the engine
 * once per held-out month and is not something the UI should do on every recomposition.
 */
object RiskBacktest {

    /** Nominal coverage of the two-sided P10-P90 interval. */
    const val NOMINAL_COVERAGE = 0.80

    /** Below this many distinct months there is nothing meaningful to hold out. */
    const val MIN_MONTHS = 3

    /**
     * Runs the backtest.
     *
     * @param transactions full ledger history (any order).
     * @param today unused for windowing — accepted so callers can pass the same clock boundary as
     *   the dashboard; the split is derived solely from transaction dates for reproducibility.
     * @param seed RNG seed; the same seed and inputs give byte-identical metrics.
     * @param runs scenarios per held-out month.
     * @param trainingFraction share of distinct months used to calibrate (the rest are held out).
     * @param calibration override for the training-window calibrator, used by tests to inject a
     *   deliberately misspecified model and prove the harness detects miscalibration.
     */
    fun run(
        transactions: List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
        seed: Long = 42,
        runs: Int = 500,
        trainingFraction: Double = 0.5,
        calibration: ((List<TransactionEntity>) -> MonteCarloCalibration)? = null,
    ): BacktestReport {
        val dated = transactions.mapNotNull { txn ->
            val date = LedgerDate.parseIsoOrNull(txn.date) ?: return@mapNotNull null
            txn to YearMonth.from(date)
        }
        val months = dated.map { it.second }.distinct().sorted()
        if (months.size < MIN_MONTHS) {
            return BacktestReport(
                observations = emptyList(),
                trainingCalibration = MonteCarloCalibration.defaults(),
                trainingMonths = 0,
                holdoutMonths = 0,
                seed = seed,
                runs = runs,
            )
        }

        val splitIndex = (months.size * trainingFraction).roundToInt().coerceIn(1, months.size - 1)
        val trainingMonths = months.take(splitIndex)
        val holdoutMonths = months.drop(splitIndex)
        val trainingSet = trainingMonths.toSet()

        // Calibrate on the training window only — patterns and totals never see the holdout.
        val trainingTxns = dated.filter { it.second in trainingSet }.map { it.first }
        val trainCalibration = (calibration ?: { txns -> MonteCarloCalibrator.calibrate(txns) })(trainingTxns)

        val baselineIncome = meanPositive(monthlyTotals(dated, trainingSet, "income")).roundToLong()
        val baselineExpense = meanPositive(monthlyTotals(dated, trainingSet, "expense")).roundToLong()

        val observations = holdoutMonths.mapIndexed { index, month ->
            val actualIncome = monthlyTotal(dated, month, "income")
            val actualExpense = monthlyTotal(dated, month, "expense")
            val actualNet = actualIncome - actualExpense

            val start = month.atDay(1)
            val events = buildList {
                if (baselineIncome > 0L) {
                    add(ForecastEvent(start, "Backtest income", baselineIncome, "income"))
                }
                if (baselineExpense > 0L) {
                    add(ForecastEvent(month.atEndOfMonth(), "Backtest expense", baselineExpense, "expense"))
                }
            }
            // Vary the seed per month so the simulated bands are not byte-identical across holdout
            // months; still fully deterministic given the caller's seed.
            val result = MonteCarloEngine(trainCalibration.toParams(seed + index, runs))
                .runSimulation(0L, events, start)

            BacktestObservation(
                month = month.toString(),
                actualNetCents = actualNet,
                p10NetCents = result.worst_10_ending_balance_cents,
                p50NetCents = result.median_ending_balance_cents,
                p90NetCents = result.best_90_ending_balance_cents,
                predictedNegativeProbability = result.probability_negative_pct / 100.0,
                actualNegative = actualNet < 0L,
            )
        }

        return BacktestReport(
            observations = observations,
            trainingCalibration = trainCalibration,
            trainingMonths = trainingMonths.size,
            holdoutMonths = holdoutMonths.size,
            seed = seed,
            runs = runs,
        )
    }

    private fun MonteCarloCalibration.toParams(seed: Long, runs: Int): MonteCarloParams = MonteCarloParams(
        seed = seed,
        runs = runs,
        incomeVariationMin = incomeVariationMin,
        incomeVariationMax = incomeVariationMax,
        expenseVariationMin = expenseVariationMin,
        expenseVariationMax = expenseVariationMax,
        expenseCategoryVariation = expenseCategoryVariation,
        surpriseProbability = surpriseProbability,
        surpriseAmountMin = surpriseAmountMin,
        surpriseAmountMax = surpriseAmountMax,
        includeDailyPercentiles = false,
    )

    private fun monthlyTotals(
        dated: List<Pair<TransactionEntity, YearMonth>>,
        months: Set<YearMonth>,
        type: String,
    ): List<Long> = months.map { month -> monthlyTotal(dated, month, type) }

    private fun monthlyTotal(
        dated: List<Pair<TransactionEntity, YearMonth>>,
        month: YearMonth,
        type: String,
    ): Long = dated.asSequence()
        .filter { it.second == month && it.first.type == type }
        .sumOf { abs(it.first.amount_cents) }

    private fun meanPositive(values: List<Long>): Double {
        val positive = values.filter { it > 0L }
        return if (positive.isEmpty()) 0.0 else positive.sum().toDouble() / positive.size
    }
}
