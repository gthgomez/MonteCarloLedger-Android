package com.montecarlo.ledger.util

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyArithmeticTest {
    @Test
    fun scaleCentsByPercent_usesDeterministicCentRounding() {
        assertEquals(101L, scaleCentsByPercent(100L, 1))
        assertEquals(99L, scaleCentsByPercent(100L, -1))
        assertEquals(50L, scaleCentsByPercent(100L, -50))
    }

    @Test
    fun monthlyInterestCents_usesAprBasisPoints() {
        assertEquals(150L, monthlyInterestCents(10_000L, 1_800))
        assertEquals(0L, monthlyInterestCents(1L, 1_800))
        assertEquals(0L, monthlyInterestCents(10_000L, 0))
    }

    @Test
    fun persistedBoolean_acceptsLegacyAndCanonicalValues() {
        assertEquals(true, "true".toPersistedBoolean())
        assertEquals(true, "1".toPersistedBoolean())
        assertEquals(false, "false".toPersistedBoolean())
        assertEquals(false, "0".toPersistedBoolean())
        assertEquals(false, null.toPersistedBoolean())
    }
}
