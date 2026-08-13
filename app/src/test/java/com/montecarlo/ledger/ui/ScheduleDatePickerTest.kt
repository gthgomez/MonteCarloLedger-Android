package com.montecarlo.ledger.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ScheduleDatePickerTest {

    @Test
    fun utcMillisRoundTripKeepsTheCalendarDate() {
        val date = LocalDate.of(2026, 8, 13)
        val millis = date.toUtcDateMillisOrNullFromLocalDate()
        assertEquals("2026-08-13", date.toString().toUtcDateMillisOrNull()?.let { it.utcMillisToLocalDateString() })
        assertEquals(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), millis)
    }

    @Test
    fun utcConversionDoesNotShiftTheDateForUsOffsets() {
        val millis = "2026-08-13".toUtcDateMillisOrNull()
        assertEquals("2026-08-13", millis?.utcMillisToLocalDateString())
    }

    private fun LocalDate.toUtcDateMillisOrNullFromLocalDate(): Long? = toString().toUtcDateMillisOrNull()
}
