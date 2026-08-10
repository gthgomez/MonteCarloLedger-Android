package com.montecarlo.ledger.domain

import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainRulesTest {

    @Test
    fun validateTransactionSign_acceptsValidTypesAndRejectsInvalidAmounts() {
        DomainRules.validateTransactionSign(1_000L, "income")
        DomainRules.validateTransactionSign(-1_000L, "expense")
        DomainRules.validateTransactionSign(0L, "adjustment")

        assertThrows(IllegalArgumentException::class.java) {
            DomainRules.validateTransactionSign(0L, "income")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DomainRules.validateTransactionSign(1_000L, "expense")
        }
    }

    @Test
    fun validateOccurrenceLink_requiresMatchingExpenseTransaction() {
        val occurrence = BillOccurrenceEntity(
            id = 1,
            payment_id = 7,
            due_date = "2026-04-20",
            amount_cents = 4_500L,
            is_paid = 0,
        )
        val transaction = TransactionEntity(
            id = 9,
            description = "Rent payment",
            amount_cents = -4_500L,
            date = "2026-04-20",
            type = "expense",
        )

        DomainRules.validateOccurrenceLink(occurrence, transaction)

        assertThrows(IllegalArgumentException::class.java) {
            DomainRules.validateOccurrenceLink(
                occurrence,
                transaction.copy(amount_cents = -5_000L),
            )
        }
    }
}
