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

/** Scale a cent amount by a whole-number percentage without floating-point currency math. */
fun scaleCentsByPercent(amountCents: Long, variationPercent: Int): Long {
    return java.math.BigDecimal.valueOf(amountCents)
        .multiply(java.math.BigDecimal.valueOf(100L + variationPercent.toLong()))
        .divide(java.math.BigDecimal.valueOf(100L), 0, java.math.RoundingMode.HALF_UP)
        .longValueExact()
}

/** Calculate one month's interest from APR basis points (hundredths of a percent). */
fun monthlyInterestCents(balanceCents: Long, aprBasisPoints: Int): Long {
    require(balanceCents >= 0L) { "Balance cannot be negative" }
    require(aprBasisPoints >= 0) { "APR cannot be negative" }
    return java.math.BigDecimal.valueOf(balanceCents)
        .multiply(java.math.BigDecimal.valueOf(aprBasisPoints.toLong()))
        .divide(java.math.BigDecimal.valueOf(120_000L), 0, java.math.RoundingMode.HALF_UP)
        .longValueExact()
}
