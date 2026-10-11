package com.montecarlo.ledger.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared cash-vs-card predicate. The repository's transaction side effects and the dashboard's
 * cash ledger both route through this, so only the TYPE may ever decide it.
 */
class AccountRoutingTest {

    private fun account(type: String) = AccountEntity(
        id = 1L,
        name = "Account",
        type = type,
        balanceCents = 0L,
        lastUpdated = "2026-08-21",
    )

    @Test
    fun creditTypesAreCardsRegardlessOfCasing() {
        assertTrue(account("credit").isCreditAccount())
        assertTrue(account("CREDIT").isCreditAccount())
    }

    @Test
    fun everyOtherTypeIsCash() {
        listOf("checking", "savings", "cash", "other", "").forEach { type ->
            assertFalse(type, account(type).isCreditAccount())
        }
    }
}
