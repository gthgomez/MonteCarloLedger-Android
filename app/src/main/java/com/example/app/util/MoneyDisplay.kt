package com.example.app.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Format integer cents as a display string with dollar sign, using exact BigDecimal division.
 *
 * Examples:
 * - centsToDisplay(1234) → "$12.34"
 * - centsToDisplay(-500) → "-$5.00"
 * - centsToDisplay(0)    → "$0.00"
 * - centsToDisplay(99)   → "$0.99"
 * - centsToDisplay(1)    → "$0.01"
 */
fun centsToDisplay(cents: Int): String = centsToDisplay(cents.toLong())

fun centsToDisplay(cents: Long): String {
    val absCents = if (cents < 0) -cents else cents
    val dollars = BigDecimal(absCents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val sign = if (cents < 0) "-" else ""
    return "$sign\$${dollars.toPlainString()}"
}

/**
 * Format integer cents as a whole-dollar display string (no cents), using exact BigDecimal division.
 * Used for Monte Carlo percentile forecasts and other whole-dollar estimates.
 *
 * Examples:
 * - centsToDisplayWhole(1234) → "$12"
 * - centsToDisplayWhole(-500) → "-$5"
 * - centsToDisplayWhole(0)    → "$0"
 * - centsToDisplayWhole(149)  → "$1"
 * - centsToDisplayWhole(150)  → "$2"
 */
fun centsToDisplayWhole(cents: Int): String = centsToDisplayWhole(cents.toLong())

fun centsToDisplayWhole(cents: Long): String {
    val absCents = if (cents < 0) -cents else cents
    val dollars = BigDecimal(absCents).divide(BigDecimal(100), 0, RoundingMode.HALF_UP)
    val sign = if (cents < 0) "-" else ""
    return "$sign\$${dollars.toPlainString()}"
}

/**
 * Format integer cents as a plain dollar string (no '$' sign) for TextField edit-field initial values.
 *
 * Examples:
 * - centsToDollarInputString(1234) → "12.34"
 * - centsToDollarInputString(-500) → "-5.00"
 * - centsToDollarInputString(0)    → "0.00"
 */
fun centsToDollarInputString(cents: Int): String = centsToDollarInputString(cents.toLong())

fun centsToDollarInputString(cents: Long): String {
    val absCents = if (cents < 0) -cents else cents
    val dollars = BigDecimal(absCents).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
    val sign = if (cents < 0) "-" else ""
    return "$sign${dollars.toPlainString()}"
}
