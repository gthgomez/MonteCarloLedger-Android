package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.random.Random

/**
 * WS4 out-of-sample backtest of the Monte Carlo risk model.
 *
 * The harness ([RiskBacktest]) calibrates on a training window and scores the model against a
 * held-out window. The leaky, unverified probability the dashboard used to show without caveat is
 * exactly what these assertions pin down:
 *
 *  - a well-specified ledger must be covered roughly as often as the nominal 80% band claims;
 *  - the metric must be deterministic for a fixed seed; and
 *  - the harness must *detect* a misspecified model (one whose variance is wrong) as under-covering.
 */
class RiskBacktestTest {

    private val today = LocalDate.of(2026, 12, 31)

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

    /**
     * Twelve months of a stationary process: the month-to-month volatility is drawn from a seeded
     * RNG, so the ledger is reproducible and the model's own variance assumption is roughly correct.
     */
    private fun stationaryLedger(months: Int = 12, seed: Int = 7): List<TransactionEntity> {
        val rng = Random(seed)
        return buildList {
            for (month in 1..months) {
                val stamp = "2026-%02d".format(month)
                add(income(200_000L + rng.nextInt(-20_000, 20_001), "$stamp-01"))
                add(expense("Rent", 100_000L, "$stamp-02", "bills"))
                add(expense("Groceries", 40_000L + rng.nextInt(-15_000, 15_001), "$stamp-10", "food"))
                add(expense("Dining", 20_000L + rng.nextInt(-8_000, 8_001), "$stamp-20", "dining"))
                // Irregular charge (unique description each month) so surprise statistics calibrate.
                add(expense("One-off repair $month", 5_000L + rng.nextInt(0, 20_000), "$stamp-25", "home"))
            }
        }
    }

    @Test
    fun backtest_wellSpecifiedHistory_coversTheNominalBand() {
        val report = RiskBacktest.run(stationaryLedger(), today = today, seed = 42, runs = 500)

        println(
            "WS4 backtest: samples=${report.sampleSize} train=${report.trainingMonths} holdout=${report.holdoutMonths} " +
                "coverage=${"%.3f".format(report.empiricalCoverage)} nominal=${report.nominalCoverage} " +
                "coverageError=${"%.3f".format(report.coverageError)} brier=${"%.4f".format(report.brierScore)} " +
                "baseRateBrier=${"%.4f".format(report.baseRateBrier)} skill=${"%.3f".format(report.brierSkillScore)} " +
                "months=${report.observations.joinToString { "${it.month}:${it.actualNetCents}in[${it.p10NetCents},${it.p90NetCents}]" }}"
        )

        assertEquals(6, report.holdoutMonths)
        assertEquals(report.holdoutMonths, report.sampleSize)
        // The band must exist (not degenerate) and be ordered, and the actuals must land inside it at
        // roughly the nominal rate — the property an honest "80% range" must satisfy.
        assertTrue("band must be non-degenerate", report.observations.any { it.p90NetCents > it.p10NetCents })
        assertTrue(
            "P10-P90 band must be ordered",
            report.observations.all { it.p10NetCents <= it.p50NetCents && it.p50NetCents <= it.p90NetCents },
        )
        assertTrue(
            "coverage too low: ${report.empiricalCoverage}",
            report.empiricalCoverage >= 0.5,
        )
        // Defined tolerance on a 6-month holdout: the band must not be off by more than a quarter.
        assertTrue(
            "coverage too far from nominal: error=${report.coverageError}",
            report.coverageError <= 0.25,
        )
    }

    @Test
    fun backtest_isDeterministicForAFixedSeed() {
        val first = RiskBacktest.run(stationaryLedger(), today = today, seed = 42, runs = 500)
        val second = RiskBacktest.run(stationaryLedger(), today = today, seed = 42, runs = 500)

        assertEquals(first.empiricalCoverage, second.empiricalCoverage, 0.0)
        assertEquals(first.brierScore, second.brierScore, 0.0)
        assertEquals(first.observations, second.observations)
    }

    @Test
    fun backtest_detectsMisspecification_flatModelUndercovers() {
        val ledger = stationaryLedger()

        val calibrated = RiskBacktest.run(ledger, today = today, seed = 42, runs = 500)
        // Inject a deliberately misspecified model: no variation and no surprises. Its P10-P90 band
        // collapses toward a point, so real months should fall outside it far more than 20% of the time.
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
            "a flat model must under-cover the nominal band (calibrated=${calibrated.empiricalCoverage}, " +
                "flat=${flatModel.empiricalCoverage})",
            flatModel.empiricalCoverage < calibrated.empiricalCoverage,
        )
        assertTrue(
            "a flat model should miss most months: ${flatModel.empiricalCoverage}",
            flatModel.empiricalCoverage < 0.5,
        )
    }

    @Test
    fun backtest_insufficientHistory_reportsNothingToScore() {
        val report = RiskBacktest.run(stationaryLedger(months = 2), today = today, seed = 42, runs = 500)

        assertEquals(0, report.sampleSize)
        assertEquals(0, report.holdoutMonths)
    }
}
