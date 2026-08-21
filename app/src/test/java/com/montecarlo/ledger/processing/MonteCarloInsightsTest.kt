package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonteCarloInsightsTest {

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

    private fun income(cents: Long, date: String) = TransactionEntity(
        description = "Paycheck",
        amount_cents = cents,
        date = date,
        type = "income",
        category = "income",
    )

    @Test
    fun emptyLedgerProducesNoInsights() {
        val calibration = MonteCarloCalibrator.calibrate(emptyList(), today)
        assertTrue(MonteCarloInsights.generate(emptyList(), today, calibration).isEmpty())
    }

    @Test
    fun shortHistoryLeadsWithDefaultAssumptionGuidance() {
        val txns = List(4) { expense("Coffee $it", 500L * (it + 1), "2026-08-0${it + 1}", category = "food") }
        val calibration = MonteCarloCalibrator.calibrate(txns, today)

        val insights = MonteCarloInsights.generate(txns, today, calibration)

        assertEquals("Using default assumptions", insights.first().label)
        assertTrue(insights.size <= 4)
    }

    @Test
    fun calibratedHistorySurfacesVolatilityDriverAndSteadyAnchor() {
        val dining = listOf(10_000L, 90_000L, 30_000L, 300_000L)
        val txns = mutableListOf<TransactionEntity>()
        for ((idx, month) in listOf("01", "02", "03", "04").withIndex()) {
            txns += expense("Landlord", 100_000L, "2026-$month-01", category = "rent")
            txns += expense("Restaurant visit $idx", dining[idx], "2026-$month-15", category = "dining out")
            txns += income(200_000L, "2026-$month-25")
        }

        val calibration = MonteCarloCalibrator.calibrate(txns, today)
        val insights = MonteCarloInsights.generate(txns, today, calibration)

        assertTrue(insights.none { it.label == "Using default assumptions" })
        assertTrue(
            "dining out should be named as the volatility driver",
            insights.any { it.label.contains("Dining out") && it.label.contains("drives") }
        )
        assertTrue(
            "rent should be named as the steady anchor",
            insights.any { it.label.contains("Rent") && it.label.contains("anchor") }
        )
    }

    @Test
    fun irregularSpendPatternProducesSurpriseInsight() {
        val txns = mutableListOf<TransactionEntity>()
        for (month in listOf("01", "02", "03", "04")) {
            txns += expense("Groceries run $month", 8_000L, "2026-$month-05", category = "groceries")
            txns += expense("Car repair visit $month", 30_000L + month.toInt() * 5_000L, "2026-$month-15", category = "car")
        }

        val calibration = MonteCarloCalibrator.calibrate(txns, today)
        val insights = MonteCarloInsights.generate(txns, today, calibration)

        assertTrue(insights.any { it.label.contains("Unexpected charges") })
    }
}
