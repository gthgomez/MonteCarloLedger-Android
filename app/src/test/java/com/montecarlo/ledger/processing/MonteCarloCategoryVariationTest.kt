package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonteCarloCategoryVariationTest {

    private val today = LocalDate.of(2026, 8, 21)

    private fun expense(
        description: String,
        cents: Long,
        date: String,
        category: String,
    ) = TransactionEntity(
        description = description,
        amount_cents = -cents,
        date = date,
        type = "expense",
        category = category,
    )

    @Test
    fun calibrator_derivesDistinctRangesPerCategory() {
        val txns = mutableListOf<TransactionEntity>()
        // Rent: identical every month -> flat (floor range).
        // Dining: wildly different every month -> volatile.
        val dining = listOf(10_000L, 90_000L, 30_000L, 300_000L)
        for ((idx, month) in listOf("01", "02", "03", "04").withIndex()) {
            txns += expense("Landlord", 100_000L, "2026-$month-01", category = "rent")
            txns += expense("Restaurant visit $idx", dining[idx], "2026-$month-15", category = "dining out")
        }

        val calibration = MonteCarloCalibrator.calibrate(txns, today)

        assertTrue(calibration.expenseCategoryVariation.containsKey("rent"))
        assertTrue(calibration.expenseCategoryVariation.containsKey("dining out"))
        val rentRange = calibration.expenseCategoryVariation.getValue("rent")
        val diningRange = calibration.expenseCategoryVariation.getValue("dining out")
        assertEquals(3, rentRange.first) // flat spend clamps to the floor
        assertTrue("volatile category must exceed flat one", diningRange.first > rentRange.first)
    }

    @Test
    fun calibrator_excludesCategoriesWithoutEnoughMonths() {
        val txns = mutableListOf<TransactionEntity>()
        for (month in listOf("01", "02", "03", "04")) {
            txns += expense("Groceries run", 8_000L, "2026-$month-05", category = "groceries")
        }
        // Only one month of pet spending: not enough history to trust.
        txns += expense("Vet bill", 20_000L, "2026-04-09", category = "pet")

        val calibration = MonteCarloCalibrator.calibrate(txns, today)

        assertTrue(calibration.expenseCategoryVariation.containsKey("groceries"))
        assertFalse(calibration.expenseCategoryVariation.containsKey("pet"))
    }

    @Test
    fun calibrator_uncategorizedSpendNeverProducesACategoryKey() {
        val txns = mutableListOf<TransactionEntity>()
        for (month in listOf("01", "02", "03", "04")) {
            txns += expense("Misc item $month", 5_000L + month.length, "2026-$month-08", category = "uncategorized")
        }

        val calibration = MonteCarloCalibrator.calibrate(txns, today)

        assertFalse(calibration.expenseCategoryVariation.containsKey("uncategorized"))
    }
}
