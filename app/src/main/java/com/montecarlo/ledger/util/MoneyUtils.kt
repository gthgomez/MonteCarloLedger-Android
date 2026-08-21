package com.montecarlo.ledger.util

/**
 * Outcome of parsing a user-typed dollar amount.
 *
 * Callers MUST handle [DollarParseResult.Empty] and [DollarParseResult.Invalid]
 * explicitly; the old sentinel behavior (malformed input silently becoming $0.00)
 * is how zero-dollar ledger rows happen.
 */
sealed interface DollarParseResult {
    data class Valid(val cents: Long) : DollarParseResult
    data object Empty : DollarParseResult
    data object Invalid : DollarParseResult
}

/**
 * Convert a dollar (or other currency) amount string into exact integer cents.
 *
 * Uses BigDecimal with HALF_UP rounding so inputs like "10.005" map to 1001 cents
 * instead of the 1004 cents produced by `(dollars.toDouble() * 100).toInt()`,
 * which the QA checklist forbids (no floating-point math for currency).
 */
fun parseDollars(amount: String?): DollarParseResult {
    if (amount.isNullOrBlank()) return DollarParseResult.Empty
    val cents = runCatching {
        java.math.BigDecimal(amount.trim())
            .multiply(java.math.BigDecimal(100))
            .setScale(0, java.math.RoundingMode.HALF_UP)
            .longValueExact()
    }.getOrNull() ?: return DollarParseResult.Invalid
    return DollarParseResult.Valid(cents)
}

/**
 * Legacy convenience wrapper. Prefer [parseDollars] and handle Empty/Invalid
 * explicitly; this wrapper preserves the historical silent-zero behavior for
 * display-only call sites.
 */
@Deprecated(
    message = "Silently maps malformed input to 0. Use parseDollars and handle Empty/Invalid.",
    replaceWith = ReplaceWith("parseDollars(amount)"),
)
fun dollarsToCents(amount: String?): Long =
    when (val result = parseDollars(amount)) {
        is DollarParseResult.Valid -> result.cents
        else -> 0L
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
