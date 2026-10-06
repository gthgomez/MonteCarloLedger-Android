package com.montecarlo.ledger.contract

/**
 * The binding SplitMix64 PRNG from MonteCarlo Contract 1.0 (`contracts/simulation.md`).
 *
 * `ULong` wraps modulo `2^64` by language definition and `shr` is a logical shift, so this is a
 * literal transcription of the contract pseudocode.
 *
 * Reference vectors (binding):
 * - seed `0`  -> first [nextU64] = `16294208416658607535` (`0xE220A8397B1DCDAF`), `nextInt(0, 100)` = `67`
 * - seed `42` -> first [nextU64] = `13679457532755275413`, `nextInt(0, 100)` = `23`
 */
class SplitMix64(seed: Long) {

    private var state: ULong = seed.toULong()

    /** Contract `next_u64()`. */
    fun nextU64(): ULong {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return z xor (z shr 31)
    }

    /** Contract `next_bounded(n)` for `n >= 1`. */
    fun nextBounded(n: ULong): ULong {
        require(n >= 1uL) { "bound must be >= 1" }
        return nextU64() % n
    }

    /** Contract `next_int(min, max)` inclusive. */
    fun nextInt(min: Long, maxInclusive: Long): Long {
        require(maxInclusive >= min) { "max must be >= min" }
        val span = (maxInclusive - min) + 1L
        return min + nextBounded(span.toULong()).toLong()
    }

    /** Contract `next_int(min, max)` inclusive, `Int` convenience overload. */
    fun nextInt(min: Int, maxInclusive: Int): Int =
        nextInt(min.toLong(), maxInclusive.toLong()).toInt()

    /** Contract `next_ppm_hit(p)`: `(next_u64() mod 1_000_000) < p`. Always consumes a draw. */
    fun nextPpmHit(ppm: Int): Boolean {
        require(ppm in 0..1_000_000) { "ppm must be in 0..1_000_000" }
        return (nextU64() % 1_000_000uL).toLong() < ppm.toLong()
    }
}
