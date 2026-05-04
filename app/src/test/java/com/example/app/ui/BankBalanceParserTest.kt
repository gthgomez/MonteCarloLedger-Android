package com.example.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankBalanceParserTest {

    @Test
    fun parseBankBalanceCents_acceptsCurrencyFormatting() {
        assertEquals(123456, parseBankBalanceCents("$1,234.56"))
    }

    @Test
    fun parseBankBalanceCents_roundsHalfUpToCents() {
        assertEquals(100, parseBankBalanceCents("0.995"))
    }

    @Test
    fun parseBankBalanceCents_acceptsNegativeAndAccountingFormat() {
        assertEquals(-12345, parseBankBalanceCents("-123.45"))
        assertEquals(-12345, parseBankBalanceCents("(123.45)"))
    }

    @Test
    fun parseBankBalanceCents_rejectsInvalidText() {
        assertNull(parseBankBalanceCents("not a balance"))
    }
}
