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
}
