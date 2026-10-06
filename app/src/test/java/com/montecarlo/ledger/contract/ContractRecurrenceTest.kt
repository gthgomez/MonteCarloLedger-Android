package com.montecarlo.ledger.contract

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Exercises the recurrence frequencies the golden corpus does not cover (weekly, semimonthly,
 * bimonthly, quarterly, semiannually, onetime) and the anchor-preservation rule (MCD-0016).
 */
class ContractRecurrenceTest {

    private fun dates(
        asOf: LocalDate,
        horizonDays: Int,
        recurrence: ContractRecurrence,
    ): List<LocalDate> {
        val scenario = ContractScenario(
            scenarioId = "recurrence-test",
            asOf = asOf,
            startingBalanceCents = 0,
            horizonDays = horizonDays,
            events = emptyList(),
            recurrences = listOf(recurrence),
            simulation = null,
        )
        return ContractEngine.inWindowBaseEvents(scenario).map { it.date }
    }

    private fun income(
        frequency: String,
        start: String,
        anchor: Int? = null,
        end: String? = null,
    ) = ContractRecurrence(
        id = "r",
        name = "r",
        type = "income",
        amountCents = 100,
        frequency = frequency,
        startDate = LocalDate.parse(start),
        anchorDay = anchor,
        endDate = end?.let { LocalDate.parse(it) },
        expectedAmountCents = null,
    )

    @Test
    fun weeklyAdvancesSevenDays() {
        val result = dates(LocalDate.parse("2026-10-01"), 21, income("weekly", "2026-10-01"))
        assertEquals(
            listOf("2026-10-01", "2026-10-08", "2026-10-15").map(LocalDate::parse),
            result,
        )
    }

    @Test
    fun semimonthlyFirstAndFifteenth() {
        val result = dates(LocalDate.parse("2026-10-01"), 40, income("semimonthly", "2026-10-01", anchor = 1))
        assertEquals(
            listOf("2026-10-01", "2026-10-15", "2026-11-01").map(LocalDate::parse),
            result,
        )
    }

    @Test
    fun semimonthlyFifteenthAndLastDay() {
        val result = dates(LocalDate.parse("2026-10-15"), 40, income("semimonthly", "2026-10-15", anchor = 15))
        assertEquals(
            listOf("2026-10-15", "2026-10-31", "2026-11-15").map(LocalDate::parse),
            result,
        )
    }

    @Test
    fun bimonthlyQuarterlyAndSemiannuallyAdvanceByTheirMonths() {
        assertEquals(
            listOf("2026-10-01", "2026-12-01").map(LocalDate::parse),
            dates(LocalDate.parse("2026-10-01"), 70, income("bimonthly", "2026-10-01")),
        )
        assertEquals(
            listOf("2026-10-01", "2027-01-01").map(LocalDate::parse),
            dates(LocalDate.parse("2026-10-01"), 100, income("quarterly", "2026-10-01")),
        )
        assertEquals(
            listOf("2026-10-01", "2027-04-01").map(LocalDate::parse),
            dates(LocalDate.parse("2026-10-01"), 200, income("semiannually", "2026-10-01")),
        )
    }

    @Test
    fun onetimeEmitsExactlyOnce() {
        assertEquals(
            listOf(LocalDate.parse("2026-10-05")),
            dates(LocalDate.parse("2026-10-01"), 30, income("onetime", "2026-10-05")),
        )
        // An onetime occurrence exactly at the exclusive horizon end is not in the window.
        assertEquals(
            emptyList<LocalDate>(),
            dates(LocalDate.parse("2026-10-01"), 4, income("onetime", "2026-10-05")),
        )
    }

    @Test
    fun monthAnchorIsPreservedAcrossClampedMonths() {
        // Jan 31 -> Feb 28 -> Mar 31 (MCD-0016 / bug B-07).
        val result = dates(
            LocalDate.parse("2027-01-15"),
            90,
            income("monthly", "2027-01-31", anchor = 31),
        )
        assertEquals(
            listOf("2027-01-31", "2027-02-28", "2027-03-31").map(LocalDate::parse),
            result,
        )
    }

    @Test
    fun endDateStopsExpansion() {
        val result = dates(
            LocalDate.parse("2026-10-01"),
            365,
            income("weekly", "2026-10-01", end = "2026-10-15"),
        )
        assertEquals(
            listOf("2026-10-01", "2026-10-08", "2026-10-15").map(LocalDate::parse),
            result,
        )
    }
}
