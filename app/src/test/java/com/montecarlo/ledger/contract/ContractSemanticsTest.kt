package com.montecarlo.ledger.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Binding examples from the contract documents that are not covered by a golden fixture. */
class ContractSemanticsTest {

    @Test
    fun splitMix64ReproducesTheBindingVectors() {
        assertEquals(16294208416658607535uL, SplitMix64(0).nextU64())
        assertEquals(67L, SplitMix64(0).nextInt(0, 100).toLong())

        assertEquals(13679457532755275413uL, SplitMix64(42).nextU64())
        assertEquals(23L, SplitMix64(42).nextInt(0, 100).toLong())
    }

    @Test
    fun percentageScalingUsesRoundHalfAwayFromZero() {
        // Binding table from contracts/money.md (corrected by MCD-0007 at eea85f0).
        assertEquals(101L, ContractMoney.scaleCentsByPercent(100, 1))
        assertEquals(99L, ContractMoney.scaleCentsByPercent(100, -1))
        assertEquals(93L, ContractMoney.scaleCentsByPercent(101, -8)) // 101 * 0.92 = 92.92 -> 93
        assertEquals(150L, ContractMoney.scaleCentsByPercent(150, 0))
        assertEquals(50L, ContractMoney.scaleCentsByBasisPoints(999, 500)) // 49.95 -> 50
    }

    @Test
    fun integerRoundingIsHalfAwayFromZero() {
        assertEquals(3L, ContractMoney.roundHalfAway(5L, 2L))
        assertEquals(-3L, ContractMoney.roundHalfAway(-5L, 2L))
        assertEquals(0L, ContractMoney.roundHalfAway(0L, 2L))
    }

    @Test
    fun overflowIsAnErrorNeverAWrap() {
        assertThrows(ContractException::class.java) {
            ContractMoney.checkedAdd(Long.MAX_VALUE, 1L)
        }.also { assertEquals(ContractErrorCode.MONEY_OVERFLOW, it.code) }

        assertThrows(ContractException::class.java) {
            ContractMoney.checkedAdd(ContractMoney.MIN_CENTS, -1L)
        }.also { assertEquals(ContractErrorCode.MONEY_OVERFLOW, it.code) }
    }

    @Test
    fun nearestRankPercentileMatchesTheBindingExamples() {
        val data = LongArray(500) { it.toLong() }
        assertEquals(49L, ContractSimulation.percentileNearestRank(data, 1, 10))
        assertEquals(249L, ContractSimulation.percentileNearestRank(data, 1, 2))
        assertEquals(449L, ContractSimulation.percentileNearestRank(data, 9, 10))
        assertEquals(0L, ContractSimulation.percentileNearestRank(LongArray(5), 1, 10))
        assertEquals(7L, ContractSimulation.percentileNearestRank(longArrayOf(7), 1, 2))
    }

    @Test
    fun medianOfEvenCountIsTheLowerElementNotAnAverage() {
        // P50 of [10, 20] is the lower median 10; the old Kotlin midpoint average returned 15 (bug B-06).
        assertEquals(10L, ContractSimulation.percentileNearestRank(longArrayOf(10, 20), 1, 2))
    }
}
