package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Calibration may only describe realized, complete months. */
class MonteCarloCalibratorBoundaryTest {

    private val today = LocalDate.of(2026, 8, 21)

    private fun expense(cents: Long, date: String) = TransactionEntity(
        description = "Expense $date",
        amount_cents = -cents,
        date = date,
        type = "expense",
        category = "food",
    )

    @Test
    fun futureDatedAndIncompleteCurrentMonthAreExcluded() {
        val txns = listOf(
            expense(10_000L, "2026-01-10"),
            expense(12_000L, "2026-02-10"),
            // Incomplete current month (today is the 21st) — not a realized month.
            expense(999_999L, "2026-08-05"),
            // Future-dated row — has not happened at all.
            expense(888_888L, "2026-12-05"),
        )
        val calibration = MonteCarloCalibrator.calibrate(txns, today)
        assertEquals(2, calibration.monthsCovered)
    }

    @Test
    fun aLedgerWithOnlyFutureRowsFallsBackToDefaults() {
        val calibration = MonteCarloCalibrator.calibrate(
            listOf(expense(10_000L, "2027-01-10")),
            today,
        )
        assertEquals(0, calibration.monthsCovered)
        assertTrue(!calibration.isCalibrated)
        assertEquals(
            MonteCarloCalibration.defaults().surpriseProbability,
            calibration.surpriseProbability,
            0.0001,
        )
    }
}
