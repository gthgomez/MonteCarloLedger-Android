package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonteCarloCalibratorTest {

    private val today = LocalDate.of(2026, 8, 21)

    private fun expense(
        description: String,
        cents: Long,
        date: String,
    ) = TransactionEntity(
        description = description,
        amount_cents = -cents,
        date = date,
        type = "expense",
        category = "uncategorized",
    )

    private fun income(cents: Long, date: String) = TransactionEntity(
        description = "Paycheck",
        amount_cents = cents,
        date = date,
        type = "income",
        category = "income",
    )

    @Test
    fun calibrate_emptyLedgerFallsBackToDefaults() {
        val calibration = MonteCarloCalibrator.calibrate(emptyList(), today)

        val defaults = MonteCarloCalibration.defaults()
        assertEquals(defaults.incomeVariationMin, calibration.incomeVariationMin)
        assertEquals(defaults.surpriseProbability, calibration.surpriseProbability, 0.0001)
        assertFalse(calibration.isCalibrated)
        assertEquals(0, calibration.monthsCovered)
    }

    @Test
    fun calibrate_flatSpendingYieldsZeroExpenseVariation() {
        // Identical monthly totals -> CV of 0 -> clamps to the floor (3%).
        val txns = mutableListOf<TransactionEntity>()
        for (month in 1..4) {
            repeat(10) { i ->
                txns += expense("Coffee $i", 500L, "2026-0$month-05")
            }
            txns += expense("Rent", 100_000L, "2026-0$month-01")
        }

        val calibration = MonteCarloCalibrator.calibrate(txns, today)

        assertEquals(3, calibration.expenseVariationMax)
        assertEquals(-3, calibration.expenseVariationMin)
        assertTrue(calibration.isCalibrated)
    }

    @Test
    fun calibrate_volatileSpendingRaisesTheRange() {
        // Wildly different monthly totals -> high CV -> near the ceiling.
        val amounts = listOf(20_000L, 200_000L, 40_000L, 400_000L)
        val txns = amounts.mapIndexed { idx, total ->
            expense("Big spend", total, "2026-%02d-10".format(idx + 1))
        }

        val calibration = MonteCarloCalibrator.calibrate(txns, today)

        assertTrue(calibration.expenseVariationMax > 15)
        assertEquals(calibration.expenseVariationMax, calibration.expenseVariationMax.coerceAtMost(30))
    }

    @Test
    fun calibrate_singleMonthOfHistoryIsNotCalibrated() {
        val txns = List(5) { expense("Item $it", 1_000L * (it + 1), "2026-08-05") }

        val calibration = MonteCarloCalibrator.calibrate(txns, today)

        assertFalse(calibration.isCalibrated)
        assertEquals(1, calibration.monthsCovered)
    }

    @Test
    fun calibrate_irregularSpikesShapeSurpriseStatistics() {
        val recurring = setOf("netflix".lowercase())
        val txns = mutableListOf<TransactionEntity>()
        // 5 months of steady Netflix (recurring pattern) plus one big irregular spike per month.
        for (month in 1..5) {
            repeat(3) { txns += expense("Netflix", 1_500L, "2026-0$month-01") }
            txns += expense("Car repair visit ${month}", 30_000L + month * 5_000L, "2026-0$month-15")
            txns += expense("Groceries run $month", 8_000L, "2026-0$month-20")
        }
        // Pass the recurring patterns explicitly so the test does not depend on
        // RecurringDetector's minimum-occurrence heuristics.
        val calibration = MonteCarloCalibrator.calibrate(txns, today, recurringPatterns = recurring)

        assertTrue(calibration.surpriseAmountMin >= 2_000)
        assertTrue(calibration.surpriseAmountMax > calibration.surpriseAmountMin)
        assertTrue(calibration.surpriseProbability in 0.02..0.5)
    }

    @Test
    fun calibrate_recurringChargesDoNotCountAsSurprises() {
        // Only recurring-pattern expenses exist: surprise stats must stay at defaults.
        val txns = mutableListOf<TransactionEntity>()
        for (month in 1..4) {
            repeat(3) { txns += expense("Netflix", 1_500L, "2026-0$month-01") }
        }

        val calibration = MonteCarloCalibrator.calibrate(
            txns,
            today,
            recurringPatterns = setOf("netflix"),
        )

        assertEquals(0.15, calibration.surpriseProbability, 0.0001)
        assertEquals(2_000, calibration.surpriseAmountMin)
        assertEquals(15_000, calibration.surpriseAmountMax)
    }

    @Test
    fun calibrate_incomeVariationStaysZeroWithoutTwoIncomeMonths() {
        val txns = listOf(income(200_000L, "2026-08-01"))

        val calibration = MonteCarloCalibrator.calibrate(txns, today)

        assertEquals(0, calibration.incomeVariationMax)
        assertEquals(0, calibration.incomeVariationMin)
    }
}
