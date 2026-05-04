package com.example.app.domain

import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.TransactionEntity
import kotlin.math.abs

object DomainRules {

    fun validateTransactionSign(amountCents: Int, type: String) {
        when (type.lowercase()) {
            "income" -> {
                if (amountCents <= 0) {
                    throw IllegalArgumentException("Income transactions must have a positive amount (> 0).")
                }
            }
            "expense" -> {
                if (amountCents >= 0) {
                    throw IllegalArgumentException("Expense transactions must have a negative amount (< 0).")
                }
            }
            "adjustment" -> { /* Any sign is valid for reconciliation adjustments. */ }
            else -> throw IllegalArgumentException("Invalid transaction type: $type")
        }
    }

    fun validateIncomeSign(amountCents: Int): Result<Unit> = runCatching {
        validateAmountPositivity(amountCents, "Income amount")
    }

    fun validatePaymentSign(amountCents: Int): Result<Unit> = runCatching {
        validateAmountPositivity(amountCents, "Payment amount")
    }

    fun validateOccurrenceLink(occurrence: BillOccurrenceEntity?, transaction: TransactionEntity?) {
        if (occurrence == null) {
            throw IllegalArgumentException("Occurrence not found.")
        }
        if (transaction == null) {
            throw IllegalArgumentException("Transaction not found.")
        }
        if (transaction.type.lowercase() != "expense") {
            throw IllegalArgumentException("Transaction must be an Expense.")
        }
        if (abs(transaction.amount_cents) != occurrence.amount_cents) {
            throw IllegalArgumentException("Transaction amount does not match the bill amount.")
        }
    }

    fun validateExpectedAmountUsage(amount: Int) {
        validateAmountPositivity(amount, "Income amount")
    }

    fun validateAmountPositivity(amount: Int, label: String) {
        if (amount <= 0) {
            throw IllegalArgumentException("$label must be positive.")
        }
    }
}
