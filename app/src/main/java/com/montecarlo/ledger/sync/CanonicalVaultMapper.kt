package com.montecarlo.ledger.sync

import com.montecarlo.ledger.contract.ContractDebt
import com.montecarlo.ledger.contract.ContractLiability
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.DebtKind
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.domain.DomainRules
import com.montecarlo.ledger.util.LedgerDate
import java.time.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Maps the cross-platform vault snapshot (2.1/2.2) to/from the Android Room core.
 * Android-only rows (goals, assets, rules) are intentionally excluded from the vault envelope.
 */
object CanonicalVaultMapper {
    private val SUPPORTED_INCOME_FREQUENCIES = setOf("Weekly", "Bi-weekly", "Monthly", "One-time")
    private val SUPPORTED_PAYMENT_RECURRENCES = setOf("Monthly", "One-time", "Weekly", "Bi-weekly")

    internal fun monthlyIncomeAnchor(last: LocalDate, next: LocalDate): Int =
        if (next.dayOfMonth == minOf(last.dayOfMonth, next.lengthOfMonth())) last.dayOfMonth
        else next.dayOfMonth

    internal fun isLinkable(payment: PaymentEntity): Boolean {
        if (payment.is_active == 0) return false
        return when (payment.frequency) {
            "Monthly" -> (payment.day_of_month ?: 0) in 1..31
            "One-time" -> runCatching { java.time.LocalDate.parse(payment.next_date) }.isSuccess
            else -> false
        }
    }

    fun buildSnapshot(
        ledgerBalanceCents: Long,
        incomes: List<IncomeEntity>,
        payments: List<PaymentEntity>,
        transactions: List<TransactionEntity>,
        occurrences: List<BillOccurrenceEntity>,
        debts: List<DebtEntity>,
        updatedAtIso: String,
        canonicalIds: CanonicalIdMapping = CanonicalIdMapping(),
    ): JsonObject {
        require(payments.none { it.is_active == 0 }) {
            "This ledger has inactive payments that vault format cannot preserve. Use the encrypted Android backup."
        }
        require(debts.none { it.statementDayOfMonth != null }) {
            "This ledger has statement-day debt data that vault format cannot preserve. Use the encrypted Android backup."
        }
        val paymentIds = payments.map { it.id }
        require(paymentIds.size == paymentIds.toSet().size) {
            "Cannot export a vault with duplicate payment identifiers"
        }
        val linkablePaymentIds = payments.filter(::isLinkable).map { it.id }.toSet()
        val linkedPaymentIds = debts.mapNotNull { it.linkedPaymentId }
        require(linkedPaymentIds.size == linkedPaymentIds.toSet().size) {
            "Cannot export a vault with multiple debts linked to the same payment"
        }
        require(linkedPaymentIds.all { it in linkablePaymentIds }) {
            "This ledger has a debt linked to a missing, inactive, or unsupported payment"
        }
        require(debts.none { it.linkedAccountId != null }) {
            "This ledger has account-linked debts that the cross-platform vault cannot preserve. " +
                "Use the encrypted Android backup until the vault format supports account links."
        }
        val paymentsById = payments.associateBy { it.id }
        val transactionsById = transactions.associateBy { it.id }
        val activeDebtPaymentIds = debts.filter { it.isActive }.mapNotNull { it.linkedPaymentId }.toSet()
        val occupiedSlots = HashSet<Pair<Int, String>>()
        val settlements = HashSet<Int>()
        occurrences.forEach { occurrence ->
            val payment = paymentsById[occurrence.payment_id]
                ?: throw IllegalArgumentException("A saved occurrence refers to a missing bill.")
            // An unlinked, already closed marker with no cash transaction only
            // suppresses its slot. Its old amount does not affect the forecast.
            val amountMatters = occurrence.is_paid == 0 || occurrence.transaction_id != null ||
                occurrence.payment_id in activeDebtPaymentIds
            require(!amountMatters || occurrence.amount_cents == payment.amount_cents) {
                "The vault cannot preserve a bill occurrence with an amount different from its schedule. " +
                    "Use the encrypted Android backup."
            }
            val originalDate = occurrence.original_due_date ?: occurrence.due_date
            require(occupiedSlots.add(occurrence.payment_id to originalDate)) {
                "Cannot export a vault with duplicate original bill slots."
            }
            occurrence.transaction_id?.let { id ->
                require(occurrence.is_paid != 0 && settlements.add(id)) {
                    "Cannot export an unpaid bill with a payment link or a transaction used by multiple bills."
                }
                DomainRules.validateOccurrenceLink(occurrence, transactionsById[id])
            }
        }
        val requiresV22 = debts.any { !it.isActive || it.linkedPaymentId != null }
        return buildJsonObject {
        put("version", if (requiresV22) "2.2" else "2.1")
        put("updated_at", updatedAtIso)
        put("current_balance_cents", ledgerBalanceCents)
        putJsonArray("incomes") {
            incomes.forEach { inc ->
                val frequency = mapIncomeFrequency(inc.frequency)
                val next = requireNotNull(LedgerDate.parseIsoOrNull(inc.next_date)) { "Invalid next payday." }
                val last = inc.last_date?.let {
                    requireNotNull(LedgerDate.parseIsoOrNull(it)) { "Invalid last payday." }
                } ?: next
                require(frequency != "Monthly" ||
                    monthlyIncomeAnchor(last, next) == (inc.day_of_month ?: next.dayOfMonth)) {
                    "The vault cannot preserve a month-end payday for this income. Use the encrypted Android backup."
                }
                add(
                    buildJsonObject {
                        put("id", canonicalIds.canonicalElement("incomes", inc.id))
                        put("name", inc.name)
                        put("amount_cents", inc.amount_cents)
                        put("frequency", frequency)
                        put("last_payday", last.toString())
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
                        put("id", canonicalIds.canonicalElement("payments", pmt.id))
                        put("name", pmt.name)
                        put("amount_cents", pmt.amount_cents)
                        put("recurrence", mapPaymentRecurrence(pmt.frequency))
                        pmt.day_of_month?.let { put("due_day", it) }
                        if (pmt.next_date.isEmpty()) put("due_date", kotlinx.serialization.json.JsonNull)
                        else put("due_date", pmt.next_date)
                        put("is_auto_withdraw", pmt.isAutoWithdraw)
                    },
                )
            }
        }
        putJsonArray("transactions") {
            transactions.forEach { tx ->
                add(
                    buildJsonObject {
                        put("id", canonicalIds.canonicalElement("transactions", tx.id))
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
                        put("id", canonicalIds.canonicalElement("occurrences", occ.id))
                        put("payment_id", canonicalIds.canonicalElement("payments", occ.payment_id))
                        put("due_date", occ.due_date)
                        put("paid", occ.is_paid != 0)
                        occ.transaction_id?.let { put("transaction_id", canonicalIds.canonicalElement("transactions", it)) }
                        put("original_due_date", occ.original_due_date ?: occ.due_date)
                        // Older readers accept paid/no-transaction markers only without
                        // the edit flag. Both dates still identify the moved skipped slot.
                        put("is_user_modified", occ.is_user_modified != 0 &&
                            (occ.is_paid == 0 || occ.transaction_id != null))
                    },
                )
            }
        }
        putJsonArray("liabilities") {
            debts.filter { requiresV22 || it.isActive }.forEach { debt ->
                add(
                    buildJsonObject {
                        put("id", canonicalIds.canonicalElement("debts", debt.id))
                        put("name", debt.name)
                        put("balance_cents", debt.balanceCents)
                        put("apr_basis_points", debt.aprBasisPoints)
                        put("min_payment_cents", debt.minimumPaymentCents)
                        put("kind", DebtKind.normalize(debt.kind))
                        if (debt.minPaymentPercentBps != 0) put("min_payment_percent_bps", debt.minPaymentPercentBps)
                        if (debt.minPaymentFloorCents != 0L) put("min_payment_floor_cents", debt.minPaymentFloorCents)
                        put("due_day_of_month", debt.dueDayOfMonth)
                        if (requiresV22) {
                            put("is_active", debt.isActive)
                            debt.linkedPaymentId?.let { put("linked_payment_id", canonicalIds.canonicalElement("payments", it)) }
                        }
                    },
                )
            }
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
        require(value in SUPPORTED_INCOME_FREQUENCIES) {
            "Income frequency \"$value\" is not supported for cross-platform vault export. " +
                "Supported: ${SUPPORTED_INCOME_FREQUENCIES.joinToString()}. " +
                "Change the schedule or export a backup until the contract adds this frequency."
        }
        return value
    }

    private fun mapPaymentRecurrence(value: String): String {
        require(value in SUPPORTED_PAYMENT_RECURRENCES) {
            "Bill recurrence \"$value\" is not supported for cross-platform vault export. " +
                "Supported: ${SUPPORTED_PAYMENT_RECURRENCES.joinToString()}. " +
                "Do not silently approximate schedules — adjust the bill or wait for contract support."
        }
        return value
    }

    private fun mapTransactionType(type: String): String = when (type.lowercase()) {
        "income" -> "Income"
        "expense" -> "Expense"
        "adjustment" -> "Adjustment"
        else -> throw IllegalArgumentException("Unsupported transaction type: $type")
    }
}
