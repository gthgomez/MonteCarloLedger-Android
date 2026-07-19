package com.example.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyDisplayTest {

    // ── centsToDisplay ──────────────────────────────────────────────

    @Test
    fun `centsToDisplay positive whole dollars`() {
        assertEquals("$12.00", centsToDisplay(1200))
        assertEquals("$1.00", centsToDisplay(100))
        assertEquals("$100.00", centsToDisplay(10000))
    }

    @Test
    fun `centsToDisplay positive with cents`() {
        assertEquals("$12.34", centsToDisplay(1234))
        assertEquals("$0.99", centsToDisplay(99))
        assertEquals("$0.01", centsToDisplay(1))
    }

    @Test
    fun `centsToDisplay zero`() {
        assertEquals("$0.00", centsToDisplay(0))
    }

    @Test
    fun `centsToDisplay negative`() {
        assertEquals("-$5.00", centsToDisplay(-500))
        assertEquals("-$12.34", centsToDisplay(-1234))
        assertEquals("-$0.01", centsToDisplay(-1))
    }

    @Test
    fun `centsToDisplay large values`() {
        assertEquals("\$21474836.36", centsToDisplay(Int.MAX_VALUE / 100 * 100 + 36)) // near Int.MAX_VALUE edge
        assertEquals("-\$21474836.36", centsToDisplay(Int.MIN_VALUE / 100 * 100 - 36))
    }

    @Test
    fun `centsToDisplay rounding — exact division`() {
        // 50 cents is exactly $0.50 — no rounding ambiguity
        assertEquals("$0.50", centsToDisplay(50))
    }

    // ── centsToDollarInputString ─────────────────────────────────────

    @Test
    fun `centsToDollarInputString positive`() {
        assertEquals("12.34", centsToDollarInputString(1234))
        assertEquals("5.00", centsToDollarInputString(500))
        assertEquals("0.99", centsToDollarInputString(99))
    }

    @Test
    fun `centsToDollarInputString zero`() {
        assertEquals("0.00", centsToDollarInputString(0))
    }

    @Test
    fun `centsToDollarInputString negative`() {
        assertEquals("-5.00", centsToDollarInputString(-500))
        assertEquals("-12.34", centsToDollarInputString(-1234))
    }

    @Test
    fun `centsToDollarInputString large values`() {
        assertEquals("21474836.36", centsToDollarInputString(Int.MAX_VALUE / 100 * 100 + 36))
    }

    // ── Consistency ──────────────────────────────────────────────────

    @Test
    fun `centsToDollarInputString equals centsToDisplay without dollar sign`() {
        val display = centsToDisplay(1234)  // "$12.34"
        val input = centsToDollarInputString(1234)  // "12.34"
        assertEquals("\$$input", display)
    }

    // ── centsToDisplayWhole ──────────────────────────────────────────

    @Test
    fun `centsToDisplayWhole positive`() {
        assertEquals("\$12", centsToDisplayWhole(1234))
        assertEquals("\$12", centsToDisplayWhole(1249))  // rounds down
        assertEquals("\$13", centsToDisplayWhole(1250))  // rounds up
        assertEquals("\$1", centsToDisplayWhole(100))
        assertEquals("\$0", centsToDisplayWhole(49))
    }

    @Test
    fun `centsToDisplayWhole zero`() {
        assertEquals("\$0", centsToDisplayWhole(0))
    }

    @Test
    fun `centsToDisplayWhole negative`() {
        assertEquals("-\$5", centsToDisplayWhole(-500))
        assertEquals("-\$12", centsToDisplayWhole(-1234))
        assertEquals("-\$1", centsToDisplayWhole(-149))
        assertEquals("-\$2", centsToDisplayWhole(-150))
    }
}
