package com.example.app.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ForecastEngineTest {

    // ──────────────────────────────────────────────
    // Existing tests (preserved)
    // ──────────────────────────────────────────────

    @Test
    fun forecastMath_accountsForIncomeAndBillEvents() {
        val balance = 10_000
        val events = listOf(
            ForecastEvent(
                date = LocalDate.of(2026, 1, 3),
                description = "Paycheck",
                amount_cents = 2_000,
                type = "income",
            ),
            ForecastEvent(
                date = LocalDate.of(2026, 1, 4),
                description = "Rent",
                amount_cents = 3_500,
                type = "bill",
            ),
            ForecastEvent(
                date = LocalDate.of(2026, 1, 5),
                description = "Utilities",
                amount_cents = 1_500,
                type = "bill",
            )
        )

        assertEquals(7_000, ForecastEngine.calculateSafeToSpend(balance, events))
        assertEquals(1_000, ForecastEngine.calculateDailySafeSpend(balance, events, 7))
        assertEquals(2_000, ForecastEngine.calculateIncomeContribution(balance, events))
        val forecast = ForecastEngine.buildBalanceForecast(balance, events)
        assertEquals(7_000, forecast.last().balanceCents)
        val summary = ForecastEngine.calculateForecastSummary(balance, events)
        assertEquals(7_000, summary.safeToSpendCents)
        assertEquals(7_000, summary.lowestBalanceCents)
        assertEquals(7_000, summary.endingBalanceCents)
        assertNull(summary.firstNegativeDate)
        assertTrue(forecast.isNotEmpty())
    }

    @Test
    fun cashFlowWindows_reserveBillsBetweenPaychecks() {
        val startDate = LocalDate.of(2026, 1, 1)
        val events = listOf(
            ForecastEvent(
                date = LocalDate.of(2026, 1, 4),
                description = "Before payday bill",
                amount_cents = 80_00,
                type = "bill",
            ),
            ForecastEvent(
                date = LocalDate.of(2026, 1, 8),
                description = "Paycheck",
                amount_cents = 400_00,
                type = "income",
            ),
            ForecastEvent(
                date = LocalDate.of(2026, 1, 10),
                description = "Rent",
                amount_cents = 200_00,
                type = "bill",
            ),
            ForecastEvent(
                date = LocalDate.of(2026, 1, 13),
                description = "Car repair",
                amount_cents = 320_00,
                type = "bill",
            ),
            ForecastEvent(
                date = LocalDate.of(2026, 1, 15),
                description = "Paycheck",
                amount_cents = 250_00,
                type = "income",
            ),
        )

        val windows = ForecastEngine.buildCashFlowWindows(
            balanceCents = 100_00,
            events = events,
            startDate = startDate,
            daysAhead = 21,
        )

        assertEquals(3, windows.size)
        assertEquals(80_00, windows[0].billCents)
        assertEquals(20_00, windows[0].safeToSpendCents)
        assertEquals(285, windows[0].dailySafeSpendCents)
        assertEquals(0, windows[0].shortfallCents)

        assertEquals(400_00, windows[1].incomeCents)
        assertEquals(520_00, windows[1].billCents)
        assertEquals(0, windows[1].safeToSpendCents)
        assertEquals(100_00, windows[1].shortfallCents)

        assertEquals(250_00, windows[2].incomeCents)
        assertEquals(150_00, windows[2].safeToSpendCents)
    }

    // ──────────────────────────────────────────────
    // calculateSafeToSpend
    // ──────────────────────────────────────────────

    @Test
    fun calculateSafeToSpend_allIncomeScenario() {
        val balance = 100_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 2), "Pay 1", 50_00, "income"),
            ForecastEvent(LocalDate.of(2026, 1, 5), "Pay 2", 30_00, "income"),
            ForecastEvent(LocalDate.of(2026, 1, 8), "Pay 3", 20_00, "income"),
        )
        // Only income: balance never drops below starting balance
        assertEquals(100_00, ForecastEngine.calculateSafeToSpend(balance, events))
    }

    @Test
    fun calculateSafeToSpend_allExpenseScenario_goesNegative() {
        val balance = 100_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 2), "Rent", 60_00, "bill"),
            ForecastEvent(LocalDate.of(2026, 1, 5), "Car", 50_00, "bill"),
            ForecastEvent(LocalDate.of(2026, 1, 8), "Food", 40_00, "bill"),
        )
        // Running: 10000 -> 4000 -> -1000 -> -5000, lowest = -5000
        assertEquals(-50_00, ForecastEngine.calculateSafeToSpend(balance, events))
    }

    @Test
    fun calculateSafeToSpend_emptyEvents_returnsBalance() {
        assertEquals(500_00, ForecastEngine.calculateSafeToSpend(500_00, emptyList()))
    }

    @Test
    fun calculateSafeToSpend_outOfOrderEvents_sortedCorrectly() {
        val balance = 1000_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 3), "Rent", 300_00, "bill"),
            ForecastEvent(LocalDate.of(2026, 1, 1), "Payday", 500_00, "income"),
            ForecastEvent(LocalDate.of(2026, 1, 2), "Insurance", 100_00, "bill"),
        )
        // After sort: income day1, bill day2, bill day3
        // Running: 100000 -> 150000 -> 140000 -> 110000, lowest = 100000
        assertEquals(1000_00, ForecastEngine.calculateSafeToSpend(balance, events))
    }

    @Test
    fun calculateSafeToSpend_sameDayIncomeBeforeBill() {
        val sameDay = LocalDate.of(2026, 1, 5)
        val balance = 50_00
        val events = listOf(
            ForecastEvent(sameDay, "Rent", 100_00, "bill"),
            ForecastEvent(sameDay, "Payday", 200_00, "income"),
        )
        // Sort puts income first on same date → 5000 -> 25000 -> 15000, lowest = 5000
        // If bill were processed first: 5000 -> -5000 -> 15000, lowest = -5000
        assertEquals(50_00, ForecastEngine.calculateSafeToSpend(balance, events))
    }

    // ──────────────────────────────────────────────
    // calculateDailySafeSpend
    // ──────────────────────────────────────────────

    @Test
    fun calculateDailySafeSpend_negativeSafeToSpend_returnsZero() {
        val balance = 10_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 2), "Big bill", 20_00, "bill"),
        )
        assertEquals(0, ForecastEngine.calculateDailySafeSpend(balance, events, 5))
    }

    @Test
    fun calculateDailySafeSpend_zeroDaysUntilPayday_returnsFullSafeToSpend() {
        val balance = 100_00
        assertEquals(100_00, ForecastEngine.calculateDailySafeSpend(balance, emptyList(), 0))
    }

    @Test
    fun calculateDailySafeSpend_dividesByDaysUntilPayday() {
        val balance = 100_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 2), "Income", 50_00, "income"),
        )
        // safeToSpend = 100_00, daysUntilPayday = 5
        assertEquals(20_00, ForecastEngine.calculateDailySafeSpend(balance, events, 5))
    }

    // ──────────────────────────────────────────────
    // buildCashFlowWindows
    // ──────────────────────────────────────────────

    @Test
    fun buildCashFlowWindows_daysAheadZero_returnsEmpty() {
        val windows = ForecastEngine.buildCashFlowWindows(
            balanceCents = 100_00,
            events = listOf(ForecastEvent(LocalDate.of(2026, 1, 2), "Pay", 50_00, "income")),
            startDate = LocalDate.of(2026, 1, 1),
            daysAhead = 0,
        )
        assertTrue(windows.isEmpty())
    }

    @Test
    fun buildCashFlowWindows_singleWindowWhenNoPaychecks() {
        val startDate = LocalDate.of(2026, 1, 1)
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 5), "Rent", 300_00, "bill"),
            ForecastEvent(LocalDate.of(2026, 1, 10), "Car", 200_00, "bill"),
        )
        val windows = ForecastEngine.buildCashFlowWindows(
            balanceCents = 1000_00,
            events = events,
            startDate = startDate,
            daysAhead = 30,
        )
        assertEquals(1, windows.size)
        assertEquals(1000_00, windows[0].startingBalanceCents)
        assertEquals(0, windows[0].incomeCents)
        assertEquals(500_00, windows[0].billCents)
        assertEquals(500_00, windows[0].endingBalanceCents)
    }

    @Test
    fun buildCashFlowWindows_windowsSplitAtPaycheckBoundaries() {
        val startDate = LocalDate.of(2026, 1, 1)
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 10), "Paycheck", 500_00, "income"),
            ForecastEvent(LocalDate.of(2026, 1, 20), "Paycheck", 500_00, "income"),
        )
        val windows = ForecastEngine.buildCashFlowWindows(
            balanceCents = 200_00,
            events = events,
            startDate = startDate,
            daysAhead = 30,
        )
        // Three windows: [Jan 1-10), [Jan 10-20), [Jan 20-31)
        assertEquals(3, windows.size)
        assertEquals(startDate, windows[0].startDate)
        assertEquals(LocalDate.of(2026, 1, 10), windows[0].endDate)
        assertEquals(LocalDate.of(2026, 1, 10), windows[1].startDate)
        assertEquals(LocalDate.of(2026, 1, 20), windows[2].startDate)
    }

    // ──────────────────────────────────────────────
    // calculateIncomeContribution
    // ──────────────────────────────────────────────

    @Test
    fun calculateIncomeContribution_noIncome_returnsZero() {
        val balance = 100_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 2), "Bill1", 5_00, "bill"),
            ForecastEvent(LocalDate.of(2026, 1, 5), "Bill2", 5_00, "bill"),
        )
        // Both passes yield safeToSpend=0, so contribution = 0
        assertEquals(0, ForecastEngine.calculateIncomeContribution(balance, events))
    }

    @Test
    fun calculateIncomeContribution_negativeProjectedBalance_handledCorrectly() {
        val balance = 10_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 2), "Income", 5_00, "income"),
            ForecastEvent(LocalDate.of(2026, 1, 5), "Big bill", 20_00, "bill"),
        )
        // First pass yields safeToSpend = 0, second pass yields 0, contribution = 0
        assertEquals(0, ForecastEngine.calculateIncomeContribution(balance, events))
    }

    @Test
    fun calculateIncomeContribution_incomeImprovesSafetyMargin() {
        // With-income safeToSpend: 100 + 50 - 60 = 90 => 90_00
        // Bills-only minCash: 100 - 60 = 40 => 40_00
        // Contribution: 90_00 - 40_00 = 50_00
        val balance = 100_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 2), "Income", 50_00, "income"),
            ForecastEvent(LocalDate.of(2026, 1, 5), "Expense", 60_00, "bill"),
        )
        assertEquals(50_00, ForecastEngine.calculateIncomeContribution(balance, events))
    }

    // ──────────────────────────────────────────────
    // buildBalanceForecast
    // ──────────────────────────────────────────────

    @Test
    fun buildBalanceForecast_sequence() {
        val balance = 1000_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 2), "Payday", 500_00, "income"),
            ForecastEvent(LocalDate.of(2026, 1, 5), "Rent", 300_00, "bill"),
            ForecastEvent(LocalDate.of(2026, 1, 10), "Car", 100_00, "bill"),
        )
        val forecast = ForecastEngine.buildBalanceForecast(balance, events)
        assertEquals(3, forecast.size)
        assertEquals(LocalDate.of(2026, 1, 2), forecast[0].date)
        assertEquals(1500_00, forecast[0].balanceCents)
        assertEquals(LocalDate.of(2026, 1, 5), forecast[1].date)
        assertEquals(1200_00, forecast[1].balanceCents)
        assertEquals(LocalDate.of(2026, 1, 10), forecast[2].date)
        assertEquals(1100_00, forecast[2].balanceCents)
    }

    @Test
    fun buildBalanceForecast_emptyEvents_returnsEmptyList() {
        val forecast = ForecastEngine.buildBalanceForecast(500_00, emptyList())
        assertTrue(forecast.isEmpty())
    }

    @Test
    fun buildBalanceForecast_incomeAndExpenseOnSameDay_appliesIncomeFirst() {
        val sameDay = LocalDate.of(2026, 1, 5)
        val balance = 100_00
        val events = listOf(
            ForecastEvent(sameDay, "Rent", 200_00, "bill"),
            ForecastEvent(sameDay, "Payday", 300_00, "income"),
        )
        // Sort: income first → balance: 100+300=400, then 400-200=200
        val forecast = ForecastEngine.buildBalanceForecast(balance, events)
        assertEquals(2, forecast.size)
        assertEquals(400_00, forecast[0].balanceCents)
        assertEquals(200_00, forecast[1].balanceCents)
    }

    // ──────────────────────────────────────────────
    // calculateForecastSummary
    // ──────────────────────────────────────────────

    @Test
    fun calculateForecastSummary_allPositive() {
        val balance = 1000_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 2), "Payday", 500_00, "income"),
            ForecastEvent(LocalDate.of(2026, 1, 5), "Rent", 300_00, "bill"),
        )
        val summary = ForecastEngine.calculateForecastSummary(balance, events)
        // Lowest balance is the starting balance (1000) since income comes first
        assertEquals(1000_00, summary.safeToSpendCents)
        assertEquals(1000_00, summary.lowestBalanceCents)
        assertNull(summary.lowestBalanceDate)
        assertEquals(1200_00, summary.endingBalanceCents)
        assertNull(summary.firstNegativeDate)
    }

    @Test
    fun calculateForecastSummary_goesNegativeAndRecovers() {
        val balance = 500_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 2), "Big bill", 600_00, "bill"),
            ForecastEvent(LocalDate.of(2026, 1, 5), "Payday", 500_00, "income"),
            ForecastEvent(LocalDate.of(2026, 1, 8), "Small bill", 100_00, "bill"),
        )
        val summary = ForecastEngine.calculateForecastSummary(balance, events)
        // Row 1 (Jan 2, bill 600): 500-600 = -100 (neg, lowest)
        // Row 2 (Jan 5, inc 500): -100+500 = 400
        // Row 3 (Jan 8, bill 100): 400-100 = 300 (end)
        // safeToSpendCents mirrors the lowest running balance (negative = overdraft)
        assertEquals(-100_00, summary.safeToSpendCents)
        assertEquals(-100_00, summary.lowestBalanceCents)
        assertEquals(LocalDate.of(2026, 1, 2), summary.lowestBalanceDate)
        assertEquals(300_00, summary.endingBalanceCents)
        assertEquals(LocalDate.of(2026, 1, 2), summary.firstNegativeDate)
    }

    @Test
    fun calculateForecastSummary_emptyEvents_returnsBalanceBasedSummary() {
        val balance = 500_00
        val summary = ForecastEngine.calculateForecastSummary(balance, emptyList())
        assertEquals(500_00, summary.safeToSpendCents)
        assertEquals(500_00, summary.lowestBalanceCents)
        assertNull(summary.lowestBalanceDate)
        assertEquals(500_00, summary.endingBalanceCents)
        assertNull(summary.firstNegativeDate)
    }

    @Test
    fun calculateForecastSummary_incomeOnly_neverBelowStartingBalance() {
        val balance = 200_00
        val events = listOf(
            ForecastEvent(LocalDate.of(2026, 1, 5), "Bonus", 300_00, "income"),
            ForecastEvent(LocalDate.of(2026, 1, 10), "Refund", 100_00, "income"),
        )
        val summary = ForecastEngine.calculateForecastSummary(balance, events)
        assertEquals(200_00, summary.safeToSpendCents)
        assertEquals(200_00, summary.lowestBalanceCents)
        assertNull(summary.lowestBalanceDate)
        assertEquals(600_00, summary.endingBalanceCents)
        assertNull(summary.firstNegativeDate)
    }
}
