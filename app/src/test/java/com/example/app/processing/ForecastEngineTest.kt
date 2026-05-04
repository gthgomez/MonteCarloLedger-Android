package com.example.app.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ForecastEngineTest {

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
}
