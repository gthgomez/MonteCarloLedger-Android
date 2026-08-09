package com.montecarlo.ledger.domain

import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.TransactionEntity
import java.util.Locale
import kotlin.math.abs

object DomainRules {

    fun validateTransactionSign(amountCents: Long, type: String) {
        when (type.lowercase(Locale.ROOT)) {
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

    fun validateIncomeSign(amountCents: Long): Result<Unit> = runCatching {
        validateAmountPositivity(amountCents, "Income amount")
    }

    fun validatePaymentSign(amountCents: Long): Result<Unit> = runCatching {
        validateAmountPositivity(amountCents, "Payment amount")
    }

    fun validateOccurrenceLink(occurrence: BillOccurrenceEntity?, transaction: TransactionEntity?) {
        if (occurrence == null) {
            throw IllegalArgumentException("Occurrence not found.")
        }
        if (transaction == null) {
            throw IllegalArgumentException("Transaction not found.")
        }
        if (transaction.type.lowercase(Locale.ROOT) != "expense") {
            throw IllegalArgumentException("Transaction must be an Expense.")
        }
        if (abs(transaction.amount_cents) != occurrence.amount_cents) {
            throw IllegalArgumentException("Transaction amount does not match the bill amount.")
        }
    }

    fun toExpenseTransactionAmount(magnitudeCents: Long): Long = -abs(magnitudeCents)

    fun toMagnitude(amountCents: Long): Long = abs(amountCents)

    fun validateExpectedAmountUsage(amount: Long) {
        validateAmountPositivity(amount, "Income amount")
    }

    fun validateAmountPositivity(amount: Long, label: String) {
        if (amount <= 0) {
            throw IllegalArgumentException("$label must be positive.")
        }
    }
}
