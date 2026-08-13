package com.montecarlo.ledger.processing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RecurrenceMathTest {

    @Test
    fun monthlyDatesStayAnchoredToTheConfiguredDay() {
        val january31 = LocalDate.of(2026, 1, 31)
        val februaryDate = RecurrenceMath.nextDate(january31, "monthly", 31)
        val marchDate = RecurrenceMath.nextDate(februaryDate!!, "monthly", 31)

        assertEquals(LocalDate.of(2026, 2, 28), februaryDate)
        assertEquals(LocalDate.of(2026, 3, 31), marchDate)
        assertEquals(LocalDate.of(2026, 2, 28), RecurrenceMath.previousDate(marchDate!!, "monthly", 31))
    }

    @Test
    fun semimonthlyAlternatesBetweenTheFirstAndFifteenth() {
        val first = LocalDate.of(2026, 1, 1)
        val fifteenth = RecurrenceMath.nextDate(first, "semimonthly")
        val nextFirst = RecurrenceMath.nextDate(fifteenth!!, "semimonthly")

        assertEquals(LocalDate.of(2026, 1, 15), fifteenth)
        assertEquals(LocalDate.of(2026, 2, 1), nextFirst)
        assertEquals(first, RecurrenceMath.previousDate(fifteenth, "semimonthly"))
    }

    @Test
    fun semimonthlyHandlesFifteenthAndEndOfMonthSchedules() {
        val fifteenth = LocalDate.of(2026, 1, 15)
        val endOfJan = RecurrenceMath.nextDate(fifteenth, "semimonthly", 15)
        val midFeb = RecurrenceMath.nextDate(endOfJan!!, "semimonthly", 15)
        val endOfFeb = RecurrenceMath.nextDate(midFeb!!, "semimonthly", 15)

        assertEquals(LocalDate.of(2026, 1, 31), endOfJan)
        assertEquals(LocalDate.of(2026, 2, 15), midFeb)
        assertEquals(LocalDate.of(2026, 2, 28), endOfFeb)
        assertEquals(fifteenth, RecurrenceMath.previousDate(endOfJan, "semimonthly", 15))
    }

    @Test
    fun otherFrequenciesAdvanceByTheirExpectedIntervals() {
        val base = LocalDate.of(2026, 4, 20)

        assertEquals(LocalDate.of(2026, 4, 27), RecurrenceMath.nextDate(base, "weekly"))
        assertEquals(LocalDate.of(2026, 5, 4), RecurrenceMath.nextDate(base, "biweekly"))
        assertEquals(LocalDate.of(2026, 7, 20), RecurrenceMath.nextDate(base, "quarterly", 20))
        assertEquals(LocalDate.of(2025, 4, 20), RecurrenceMath.previousDate(base, "annually"))
        assertEquals(null, RecurrenceMath.nextDate(base, "onetime"))
    }

    @Test
    fun annualFeb29RestoresOnTheNextLeapYear() {
        val leapDay = LocalDate.of(2024, 2, 29)
        val yearLater = RecurrenceMath.nextDate(leapDay, "annually", 29)
        val fourYears = generateSequence(leapDay) { RecurrenceMath.nextDate(it, "annually", 29) }
            .drop(4)
            .first()

        assertEquals(LocalDate.of(2025, 2, 28), yearLater)
        assertEquals(LocalDate.of(2028, 2, 29), fourYears)
    }
}
