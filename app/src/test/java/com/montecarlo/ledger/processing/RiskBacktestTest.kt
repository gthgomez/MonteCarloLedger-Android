package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import kotlin.random.Random

/**
 * Out-of-sample backtest of the Monte Carlo risk model.
 *
 * The month-end band checks the calibrator's monthly-total variance; the trough band and dip Brier
 * score the path quantity the dashboard shows. The deterministic fixtures use a zero-variation,
 * zero-surprise calibration, so their expected band values are hand-computed and RNG-free. The dip
 * baseline is asserted to come from the training window, not from the scored outcomes.
 */
class RiskBacktestTest {

    private val today = LocalDate.of(2027, 1, 31)

    private fun expense(description: String, cents: Long, date: String, category: String) =
        TransactionEntity(
            description = description,
            amount_cents = -cents,
            date = date,
            type = "expense",
            category = category,
        )

    private fun income(cents: Long, date: String) = TransactionEntity(
        description = "Paycheck",
        amount_cents = cents,
        date = date,
        type = "income",
        category = "income",
    )

    private fun noVariation() = MonteCarloCalibration(
        incomeVariationMin = 0,
        incomeVariationMax = 0,
        expenseVariationMin = 0,
        expenseVariationMax = 0,
        surpriseProbability = 0.0,
        surpriseAmountMin = 0,
        surpriseAmountMax = 0,
        monthsCovered = 6,
    )

    /** Seeded stationary ledger: volatility is drawn from the same stationary law the model assumes. */
    private fun stationaryLedger(months: Int = 12, seed: Int = 7): List<TransactionEntity> {
        val rng = Random(seed)
        return buildList {
            for (month in 1..months) {
                val stamp = "2026-%02d".format(month)
                add(income(200_000L + rng.nextInt(-20_000, 20_001), "$stamp-01"))
                add(expense("Rent", 100_000L, "$stamp-02", "bills"))
                add(expense("Groceries", 40_000L + rng.nextInt(-15_000, 15_001), "$stamp-10", "food"))
                add(expense("Dining", 20_000L + rng.nextInt(-8_000, 8_001), "$stamp-20", "dining"))
                add(expense("One-off repair $month", 5_000L + rng.nextInt(0, 20_000), "$stamp-25", "home"))
            }
        }
    }

    @Test
    fun backtest_wellSpecifiedHistory_coversTheNominalBand() {
        val report = RiskBacktest.run(stationaryLedger(), today = today, seed = 42, runs = 500)

        println(
            "Risk backtest: samples=${report.sampleSize} train=${report.trainingMonths} " +
                "holdout=${report.holdoutMonths} monthEndCoverage=${"%.3f".format(report.empiricalCoverage)} " +
                "troughCoverage=${"%.3f".format(report.troughCoverage)} dipBrier=${"%.4f".format(report.dipBrier)} " +
                "trainingDipRate=${"%.3f".format(report.trainingDipRate)} " +
                "dipBaseRateBrier=${"%.4f".format(report.dipBaseRateBrier)}"
        )

        assertEquals(6, report.holdoutMonths)
        assertEquals(report.holdoutMonths, report.sampleSize)
        assertEquals(0, report.unparseableDateRows)
        assertEquals(0, report.excludedIncompleteMonthRows)
        assertTrue(
            "month-end band must be non-degenerate",
            report.observations.any { it.monthEndP90Cents > it.monthEndP10Cents },
        )
        assertTrue(
            "bands must be ordered",
            report.observations.all {
                it.monthEndP10Cents <= it.monthEndP50Cents && it.monthEndP50Cents <= it.monthEndP90Cents &&
                    it.troughP10Cents <= it.troughP50Cents && it.troughP50Cents <= it.troughP90Cents
            },
        )
        assertTrue("month-end coverage too low: ${report.empiricalCoverage}", report.empiricalCoverage >= 0.5)
        // Defined tolerance on a 6-month holdout: the band must not be off by more than a quarter.
        assertTrue("month-end coverage too far: ${report.coverageError}", report.coverageError <= 0.25)
        // The trough sample is too small to gate; exact behaviour is pinned by the fixtures below.
        assertTrue(
            "trough band must be non-degenerate for at least one month",
            report.observations.any { it.troughP90Cents > it.troughP10Cents },
        )
    }

    @Test
    fun backtest_isDeterministicForAFixedSeed() {
        val first = RiskBacktest.run(stationaryLedger(), today = today, seed = 42, runs = 500)
        val second = RiskBacktest.run(stationaryLedger(), today = today, seed = 42, runs = 500)

        assertEquals(first.empiricalCoverage, second.empiricalCoverage, 0.0)
        assertEquals(first.troughCoverage, second.troughCoverage, 0.0)
        assertEquals(first.dipBrier, second.dipBrier, 0.0)
        assertEquals(first.observations, second.observations)
    }

    @Test
    fun backtest_detectsMisspecification_flatModelUndercovers() {
        val ledger = stationaryLedger()

        val calibrated = RiskBacktest.run(ledger, today = today, seed = 42, runs = 500)
        // A flat model collapses the band toward a point, so real months should fall outside it.
        val flatModel = RiskBacktest.run(ledger, today = today, seed = 42, runs = 500) {
            MonteCarloCalibration(
                incomeVariationMin = 0,
                incomeVariationMax = 0,
                expenseVariationMin = 0,
                expenseVariationMax = 0,
                surpriseProbability = 0.0,
                surpriseAmountMin = 0,
                surpriseAmountMax = 0,
                monthsCovered = 6,
            )
        }

        assertTrue(
            "flat model must under-cover (calibrated=${calibrated.empiricalCoverage}, " +
                "flat=${flatModel.empiricalCoverage})",
            flatModel.empiricalCoverage < calibrated.empiricalCoverage,
        )
        assertTrue("flat model should miss most months", flatModel.empiricalCoverage < 0.5)
    }

    @Test
    fun backtest_insufficientHistory_reportsNothingToScore() {
        val report = RiskBacktest.run(stationaryLedger(months = 2), today = today, seed = 42, runs = 500)

        assertEquals(0, report.sampleSize)
        assertEquals(0, report.holdoutMonths)
        assertEquals(0, report.trainingMonths)
        assertEquals(0, report.trainingDipMonths)
        assertEquals(0.0, report.trainingDipRate, 0.0)
    }

    /**
     * Hand-computed oracle: the month ends positive but dips below zero, and the model (same
     * training-derived timing) predicts both. Deterministic because all variation and surprises are 0.
     */
    @Test
    fun backtest_scoresRealizedMinimumAgainstModelTroughAndDip() {
        // Jan/Feb/Mar: income 50_000 on the 20th, rent 40_000 on the 5th.
        // Mar opening +20_000 -> trough -20_000, ending +30_000 (net +10_000).
        val ledger = buildList {
            for (month in 1..3) {
                add(income(50_000L, "2026-%02d-20".format(month)))
                add(expense("Rent", 40_000L, "2026-%02d-05".format(month), "bills"))
            }
        }

        val report = RiskBacktest.run(
            ledger,
            today = LocalDate.of(2026, 4, 30),
            seed = 42,
            runs = 32,
            calibration = { noVariation() },
        )

        assertEquals(2, report.trainingMonths)
        assertEquals(1, report.sampleSize)
        assertEquals(2, report.trainingDipMonths)

        val observation = report.observations.single()
        assertEquals("2026-03", observation.month)
        assertEquals(20_000L, observation.openingCents)
        assertEquals(10_000L, observation.actualNetCents)
        assertEquals(-20_000L, observation.actualTroughCents)
        assertTrue(observation.actualDippedNegative)
        assertTrue(observation.withinMonthEndBand)
        assertTrue(observation.withinTroughBand)
        assertEquals(10_000L, observation.monthEndP10Cents)
        assertEquals(10_000L, observation.monthEndP90Cents)
        assertEquals(-20_000L, observation.troughP10Cents)
        assertEquals(-20_000L, observation.troughP90Cents)
        assertEquals(1.0, observation.predictedDipProbability, 0.0)

        assertEquals(1.0, report.empiricalCoverage, 0.0)
        assertEquals(1.0, report.troughCoverage, 0.0)
        assertEquals(0.0, report.dipBrier, 0.0)
        // Every training month dipped, so the training climatology is degenerate; reported as 0.
        assertEquals(0.0, report.dipBaseRateBrier, 0.0)
        assertEquals(0.0, report.dipSkillScore, 0.0)
    }

    /**
     * Sign-changing oracle: training paydays are on the 1st, so training never dips and the model
     * never predicts a dip. The holdout month moves its payday to the 20th: it dips to -50_000 and
     * ends at 0, its net. A month-end-only evaluation calls this a perfect hit; the path evaluation
     * must not. `dipBaseRateBrier == 1.0` is only true with a training-only baseline.
     */
    @Test
    fun backtest_detectsShiftedPaydayAsATroughMiss_andKeepsBaselineTrainingOnly() {
        val ledger = buildList {
            for (month in 1..2) {
                add(income(50_000L, "2026-%02d-01".format(month)))
                add(expense("Rent", 50_000L, "2026-%02d-05".format(month), "bills"))
            }
            add(income(50_000L, "2026-03-20"))
            add(expense("Rent", 50_000L, "2026-03-05", "bills"))
        }

        val report = RiskBacktest.run(
            ledger,
            today = LocalDate.of(2026, 4, 30),
            seed = 42,
            runs = 32,
            calibration = { noVariation() },
        )

        assertEquals(0, report.trainingDipMonths)

        val observation = report.observations.single()
        assertEquals(0L, observation.openingCents)
        assertEquals(0L, observation.actualNetCents)
        assertEquals(-50_000L, observation.actualTroughCents)
        assertTrue(observation.actualDippedNegative)
        assertEquals(0L, observation.monthEndP10Cents)
        assertEquals(0L, observation.monthEndP90Cents)
        assertEquals(0L, observation.troughP10Cents)
        assertEquals(0L, observation.troughP90Cents)
        assertEquals(0.0, observation.predictedDipProbability, 0.0)

        assertTrue(observation.withinMonthEndBand)
        assertFalse(observation.withinTroughBand)
        assertEquals(1.0, report.empiricalCoverage, 0.0)
        assertEquals(0.0, report.troughCoverage, 0.0)
        assertEquals(1.0, report.dipBrier, 0.0)
        assertEquals(1.0, report.dipBaseRateBrier, 0.0)
        assertEquals(0.0, report.dipSkillScore, 0.0)
    }

    /**
     * No-lookahead oracle: doubling the last holdout month's amounts changes its realized net but
     * leaves the training calibration, its opening, both bands and its dip probability identical.
     */
    @Test
    fun backtest_lastHoldoutOutcomeCannotChangeItsOwnForecastOrTraining() {
        val base = buildList {
            for (month in 1..6) {
                add(income(100_000L, "2026-%02d-01".format(month)))
                add(expense("Rent", 40_000L, "2026-%02d-05".format(month), "bills"))
            }
        }
        val mutated = base.map { txn ->
            if (txn.date.startsWith("2026-06")) txn.copy(amount_cents = txn.amount_cents * 2) else txn
        }
        val cutoff = LocalDate.of(2026, 7, 31)

        val first = RiskBacktest.run(base, today = cutoff, seed = 42, runs = 32)
        val second = RiskBacktest.run(mutated, today = cutoff, seed = 42, runs = 32)

        assertEquals(first.trainingCalibration, second.trainingCalibration)
        val before = first.observations.last()
        val after = second.observations.last()
        assertEquals("2026-06", before.month)
        assertEquals(before.openingCents, after.openingCents)
        assertEquals(before.monthEndP10Cents, after.monthEndP10Cents)
        assertEquals(before.monthEndP90Cents, after.monthEndP90Cents)
        assertEquals(before.troughP10Cents, after.troughP10Cents)
        assertEquals(before.troughP90Cents, after.troughP90Cents)
        assertEquals(before.predictedDipProbability, after.predictedDipProbability, 0.0)
        assertEquals(60_000L, before.actualNetCents)
        assertEquals(120_000L, after.actualNetCents)
    }

    /** Clock oracle: training months are complete at the caller's cutoff, not at the machine clock. */
    @Test
    fun backtest_trainingWindowIsRealizedAsOfItsOwnCutoffNotWallClock() {
        val firstMonth = YearMonth.now().plusMonths(1)
        val months = (0 until 6).map { firstMonth.plusMonths(it.toLong()) }
        val ledger = buildList {
            for (month in months) {
                add(income(100_000L, month.atDay(1).toString()))
                add(expense("Rent", 40_000L, month.atDay(5).toString(), "bills"))
            }
        }
        val cutoff = months.last().plusMonths(1).atDay(1)

        val report = RiskBacktest.run(ledger, today = cutoff, seed = 42, runs = 50)

        assertEquals(3, report.trainingMonths)
        assertEquals(report.trainingMonths, report.trainingCalibration.monthsCovered)
    }
}
