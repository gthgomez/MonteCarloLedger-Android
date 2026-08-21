package com.montecarlo.ledger.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DollarParseResultTest {

    @Test
    fun parseDollars_validInputsMapToExactCents() {
        assertEquals(1_001L, (parseDollars("10.005") as DollarParseResult.Valid).cents)
        assertEquals(-34_25L, (parseDollars("-34.25") as DollarParseResult.Valid).cents)
        assertEquals(0L, (parseDollars("0.00") as DollarParseResult.Valid).cents)
    }

    @Test
    fun parseDollars_blankIsEmptyAndGarbageIsInvalid() {
        assertTrue(parseDollars(null) is DollarParseResult.Empty)
        assertTrue(parseDollars("   ") is DollarParseResult.Empty)
        assertTrue(parseDollars("abc") is DollarParseResult.Invalid)
        assertTrue(parseDollars("1.2.3") is DollarParseResult.Invalid)
        assertTrue(parseDollars("99999999999999999999") is DollarParseResult.Invalid)
    }

    @Test
    fun legacyWrapperPreservesSilentZeroForDisplayOnlyCallSites() {
        assertEquals(0L, dollarsToCents("abc"))
        assertEquals(475L, dollarsToCents("4.75"))
        assertEquals(0L, dollarsToCents(""))
    }
}
