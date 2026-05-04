package com.example.app.domain

import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.TransactionEntity
import org.junit.Assert.assertThrows
import org.junit.Test

class DomainRulesTest {

    @Test
    fun validateTransactionSign_acceptsValidTypesAndRejectsInvalidAmounts() {
        DomainRules.validateTransactionSign(1_000, "income")
        DomainRules.validateTransactionSign(-1_000, "expense")
        DomainRules.validateTransactionSign(0, "adjustment")

        assertThrows(IllegalArgumentException::class.java) {
            DomainRules.validateTransactionSign(0, "income")
        }
        assertThrows(IllegalArgumentException::class.java) {
            DomainRules.validateTransactionSign(1_000, "expense")
        }
    }

    @Test
    fun validateOccurrenceLink_requiresMatchingExpenseTransaction() {
        val occurrence = BillOccurrenceEntity(
            id = 1,
            payment_id = 7,
            due_date = "2026-04-20",
            amount_cents = 4_500,
            is_paid = 0,
        )
        val transaction = TransactionEntity(
            id = 9,
            description = "Rent payment",
            amount_cents = -4_500,
            date = "2026-04-20",
            type = "expense",
        )

        DomainRules.validateOccurrenceLink(occurrence, transaction)

        assertThrows(IllegalArgumentException::class.java) {
            DomainRules.validateOccurrenceLink(
                occurrence,
                transaction.copy(amount_cents = -5_000),
            )
        }
    }
}
