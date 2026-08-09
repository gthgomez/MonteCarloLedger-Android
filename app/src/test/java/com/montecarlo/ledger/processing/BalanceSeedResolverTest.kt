package com.montecarlo.ledger.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceSeedResolverTest {

    @Test
    fun resolve_usesLedgerSumUntilBalanceIsReconciled() {
        assertEquals(42_00, BalanceSeedResolver.resolve(42_00, 99_00, false))
    }

    @Test
    fun resolve_usesStoredBalanceAfterReconciliation() {
        assertEquals(99_00, BalanceSeedResolver.resolve(42_00, 99_00, true))
    }
}
