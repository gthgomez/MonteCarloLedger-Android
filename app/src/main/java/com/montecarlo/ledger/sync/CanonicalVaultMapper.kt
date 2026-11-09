package com.montecarlo.ledger.sync

import com.montecarlo.ledger.contract.ContractDebt
import com.montecarlo.ledger.contract.ContractLiability
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.DebtKind
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.TransactionEntity
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Maps the cross-platform vault snapshot (2.1) to/from the Android Room core.
 * Android-only rows (goals, assets, rules) are intentionally excluded from the vault envelope.
 */
object CanonicalVaultMapper {
    private val SUPPORTED_INCOME_FREQUENCIES = setOf("Weekly", "Bi-weekly", "Monthly", "One-time")
    private val SUPPORTED_PAYMENT_RECURRENCES = setOf("Monthly", "One-time", "Weekly", "Bi-weekly")

    fun buildSnapshot(
        ledgerBalanceCents: Long,
        incomes: List<IncomeEntity>,
        payments: List<PaymentEntity>,
        transactions: List<TransactionEntity>,
        occurrences: List<BillOccurrenceEntity>,
        debts: List<DebtEntity>,
        updatedAtIso: String,
    ): JsonObject = buildJsonObject {
        put("version", "2.1")
        put("updated_at", updatedAtIso)
        put("current_balance_cents", ledgerBalanceCents)
        putJsonArray("incomes") {
            incomes.forEach { inc ->
                val frequency = mapIncomeFrequency(inc.frequency)
                add(
                    buildJsonObject {
                        put("id", inc.id)
                        put("name", inc.name)
                        put("amount_cents", inc.amount_cents)
                        put("frequency", frequency)
                        put("last_payday", inc.next_date)
                        put("next_payday", inc.next_date)
                        inc.expectedAmountCents?.let { put("expected_amount", it) }
                    },
                )
            }
        }
        putJsonArray("payments") {
            payments.forEach { pmt ->
                add(
                    buildJsonObject {
                        put("id", pmt.id)
                        put("name", pmt.name)
                        put("amount_cents", pmt.amount_cents)
                        put("recurrence", mapPaymentRecurrence(pmt.frequency))
                        pmt.day_of_month?.let { put("due_day", it) }
                        put("due_date", pmt.next_date)
                        put("is_auto_withdraw", pmt.isAutoWithdraw)
                    },
                )
            }
        }
        putJsonArray("transactions") {
            transactions.forEach { tx ->
                add(
                    buildJsonObject {
                        put("id", tx.id)
                        put("amount_cents", tx.amount_cents)
                        put("category", tx.category)
                        put("type", mapTransactionType(tx.type))
                        put("date", tx.date)
                        put("description", tx.description)
                    },
                )
            }
        }
        putJsonArray("bill_occurrences") {
            occurrences.forEach { occ ->
                add(
                    buildJsonObject {
                        put("id", occ.id)
                        put("payment_id", occ.payment_id)
                        put("due_date", occ.due_date)
                        put("paid", occ.is_paid != 0)
                        occ.transaction_id?.let { put("transaction_id", it) }
                        put("original_due_date", occ.original_due_date ?: occ.due_date)
                        put("is_user_modified", occ.is_user_modified != 0)
                    },
                )
            }
        }
        putJsonArray("liabilities") {
            debts.filter { it.isActive }.forEach { debt ->
                add(
                    buildJsonObject {
                        put("id", debt.id.toString())
                        put("name", debt.name)
                        put("balance_cents", debt.balanceCents)
                        put("apr_basis_points", debt.aprBasisPoints)
                        put("min_payment_cents", debt.minimumPaymentCents)
                        put("kind", DebtKind.normalize(debt.kind))
                        if (debt.minPaymentPercentBps != 0) put("min_payment_percent_bps", debt.minPaymentPercentBps)
                        if (debt.minPaymentFloorCents != 0L) put("min_payment_floor_cents", debt.minPaymentFloorCents)
                        put("due_day_of_month", debt.dueDayOfMonth)
                    },
                )
            }
        }
    }

    fun liabilitiesFromDebts(debts: List<DebtEntity>): List<ContractLiability> =
        debts.filter { it.isActive }.map { debt ->
            ContractLiability(
                id = debt.id.toString(),
                name = debt.name,
                balanceCents = debt.balanceCents,
                aprBasisPoints = debt.aprBasisPoints,
                minPaymentCents = debt.minimumPaymentCents,
                kind = DebtKind.normalize(debt.kind),
                minPaymentPercentBps = debt.minPaymentPercentBps,
                minPaymentFloorCents = debt.minPaymentFloorCents,
                dueDayOfMonth = debt.dueDayOfMonth,
            )
        }

    fun validateLiabilities(debts: List<DebtEntity>) {
        ContractDebt.validateLiabilities(liabilitiesFromDebts(debts))
    }

    private fun mapIncomeFrequency(value: String): String {
        val normalized = when (value) {
            "Semi-monthly" -> "Bi-weekly"
            else -> value
        }
        require(normalized in SUPPORTED_INCOME_FREQUENCIES) {
            "Unsupported income frequency for vault export: $value"
        }
        return normalized
    }

    private fun mapPaymentRecurrence(value: String): String {
        val normalized = when (value) {
            "Semi-monthly", "Bi-monthly", "Quarterly", "Yearly" -> "Monthly"
            else -> value
        }
        require(normalized in SUPPORTED_PAYMENT_RECURRENCES) {
            "Unsupported payment recurrence for vault export: $value"
        }
        return normalized
    }

    private fun mapTransactionType(type: String): String = when (type.lowercase()) {
        "income" -> "Income"
        "expense" -> "Expense"
        "adjustment" -> "Adjustment"
        else -> throw IllegalArgumentException("Unsupported transaction type: $type")
    }
}
