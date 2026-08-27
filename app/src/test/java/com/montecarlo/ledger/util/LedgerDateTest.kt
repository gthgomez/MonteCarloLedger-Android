package com.montecarlo.ledger.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerDateTest {

    @Test
    fun parseIsoOrNull_acceptsTrimmedIsoAndRejectsGarbage() {
        assertEquals(2026, LedgerDate.parseIsoOrNull("2026-08-21")?.year)
        assertEquals(2026, LedgerDate.parseIsoOrNull("  2026-08-21  ")?.year)
        assertNull(LedgerDate.parseIsoOrNull(null))
        assertNull(LedgerDate.parseIsoOrNull(""))
        assertNull(LedgerDate.parseIsoOrNull("not a date"))
        assertNull(LedgerDate.parseIsoOrNull("04/15/2026"))
    }

    @Test
    fun parseBankDateOrNull_handlesCommonBankFormats() {
        assertEquals("2026-04-01", LedgerDate.parseBankDateOrNull("2026-04-01").toString())
        assertEquals("2026-04-02", LedgerDate.parseBankDateOrNull("04/02/2026").toString())
        assertEquals("2025-01-02", LedgerDate.parseBankDateOrNull("1/2/25").toString())
        assertEquals("2026-04-30", LedgerDate.parseBankDateOrNull("2026/4/30").toString())
        assertNull(LedgerDate.parseBankDateOrNull("April 3, 2026"))
    }

    @Test
    fun parseBankDateOrNull_resolvesUnambiguousEuropeanDayFirstDates() {
        // Day 13 cannot be a month, so dd/MM must win without corrupting US dates.
        assertEquals("2026-02-13", LedgerDate.parseBankDateOrNull("13/02/2026").toString())
        assertEquals("2026-02-13", LedgerDate.parseBankDateOrNull("13/2/26").toString())
        assertEquals("2026-02-13", LedgerDate.parseBankDateOrNull("13-02-2026").toString())
        assertEquals("2026-02-13", LedgerDate.parseBankDateOrNull("13-2-26").toString())
        // Ambiguous low-day values keep the US-first convention.
        assertEquals("2026-01-02", LedgerDate.parseBankDateOrNull("01/02/2026").toString())
    }
}
