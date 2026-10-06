package com.montecarlo.ledger.contract

import java.math.BigInteger

/**
 * Exact integer money per MonteCarlo Contract 1.0 (`contracts/money.md`).
 *
 * All arithmetic is integer-only. Multiplication that feeds [roundHalfAway] goes through
 * [BigInteger] so an intermediate product cannot wrap a `Long`; a result outside the valid range
 * is [ContractErrorCode.MONEY_OVERFLOW], never a wrap, saturation, or float promotion.
 */
object ContractMoney {

    /** `MAX_MONEY_CENTS = 2^63 - 1`. */
    const val MAX_CENTS: Long = Long.MAX_VALUE

    /** `MIN_MONEY_CENTS = -(2^63 - 1)`. Note this is one greater than [Long.MIN_VALUE]. */
    const val MIN_CENTS: Long = -Long.MAX_VALUE

    private val MAX_BIG: BigInteger = BigInteger.valueOf(MAX_CENTS)
    private val MIN_BIG: BigInteger = BigInteger.valueOf(MIN_CENTS)
    private val HUNDRED = BigInteger.valueOf(100)

    /** Casts an exact integer to `Long`, raising [ContractErrorCode.MONEY_OVERFLOW] out of range. */
    fun toLong(value: BigInteger): Long {
        if (value > MAX_BIG || value < MIN_BIG) {
            throw ContractException(ContractErrorCode.MONEY_OVERFLOW, "value out of money range: $value")
        }
        return value.toLong()
    }

    /** Rejects an input already outside the documented valid money range. */
    fun requireValid(value: Long, what: String): Long {
        if (value < MIN_CENTS || value > MAX_CENTS) {
            throw ContractException(ContractErrorCode.MONEY_OVERFLOW, "$what out of money range: $value")
        }
        return value
    }

    /**
     * `round_half_away(n, d) = sign(n) * ((abs(n) + d // 2) // d)` for `d > 0`.
     * Exact integer arithmetic; banker's rounding is never used.
     */
    fun roundHalfAway(n: BigInteger, d: BigInteger): BigInteger {
        require(d.signum() > 0) { "denominator must be positive" }
        if (n.signum() == 0) return BigInteger.ZERO
        val quotient = (n.abs() + d.divide(BigInteger.TWO)).divide(d)
        return if (n.signum() < 0) quotient.negate() else quotient
    }

    /** `round_half_away(n, d)` with range checking. */
    fun roundHalfAway(n: Long, d: Long): Long =
        toLong(roundHalfAway(BigInteger.valueOf(n), BigInteger.valueOf(d)))

    /**
     * `scale_cents_by_percent(amount, percent) = round_half_away(amount * (100 + percent), 100)`.
     *
     * `percent` is a signed variation: `-20` means 20% less, so the result is `(100 + percent)%`
     * of the amount. This is the corrected rule from MCD-0007 (`eea85f0`); the earlier draft that
     * treated `percent` as a raw fraction was inconsistent with `simulation.md`.
     */
    fun scaleCentsByPercent(amountCents: Long, percent: Long): Long {
        val numerator = BigInteger.valueOf(amountCents)
            .multiply(BigInteger.valueOf(100L + percent))
        return toLong(roundHalfAway(numerator, HUNDRED))
    }

    /** `scale_cents_by_basis_points(amount, bps) = round_half_away(amount * bps, 10000)`. */
    fun scaleCentsByBasisPoints(amountCents: Long, basisPoints: Long): Long {
        val numerator = BigInteger.valueOf(amountCents).multiply(BigInteger.valueOf(basisPoints))
        return toLong(roundHalfAway(numerator, BigInteger.valueOf(10_000)))
    }

    /** Overflow-checked addition. Rejects results outside `[MIN_CENTS, MAX_CENTS]`. */
    fun checkedAdd(a: Long, b: Long): Long =
        toLong(BigInteger.valueOf(a).add(BigInteger.valueOf(b)))

    /** Overflow-checked subtraction. */
    fun checkedSubtract(a: Long, b: Long): Long =
        toLong(BigInteger.valueOf(a).subtract(BigInteger.valueOf(b)))

    /**
     * Exact integer ceiling of `n * num / den` with `den > 0`, used by the single nearest-rank
     * percentile convention (`contracts/risk.md`).
     */
    fun ceilRatio(n: Long, num: Long, den: Long): Long {
        require(den > 0) { "denominator must be positive" }
        val numerator = BigInteger.valueOf(n).multiply(BigInteger.valueOf(num))
        val d = BigInteger.valueOf(den)
        // ceil(x / d) for positive numerator = (x + d - 1) / d
        return numerator.add(d.subtract(BigInteger.ONE)).divide(d).toLong()
    }
}
