package com.montecarlo.ledger.util

/**
 * Convert a dollar (or other currency) amount string into exact integer cents.
 *
 * Uses BigDecimal with HALF_UP rounding so inputs like "10.005" map to 1001 cents
 * instead of the 1004 cents produced by `(dollars.toDouble() * 100).toInt()`,
 * which the QA checklist forbids (no floating-point math for currency).
 */
fun dollarsToCents(amount: String?): Long {
    if (amount.isNullOrBlank()) return 0L
    return runCatching {
        java.math.BigDecimal(amount.trim())
            .multiply(java.math.BigDecimal(100))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .toLong()
    }.getOrDefault(0L)
}