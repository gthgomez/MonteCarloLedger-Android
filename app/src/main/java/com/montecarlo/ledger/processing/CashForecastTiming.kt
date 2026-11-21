package com.montecarlo.ledger.processing

import com.montecarlo.ledger.contract.ContractMoney
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.util.LedgerDate
import java.math.BigInteger
import java.time.LocalDate

object CashForecastTiming {
    fun opening(confirmedBankCents: Long?, cash: List<TransactionEntity>, asOf: LocalDate): Long {
        val day = asOf.toString()
        if (confirmedBankCents != null) {
            require(cash.none { it.date > day }) {
                "The forecast cannot tell whether future-dated transactions are included in your confirmed bank balance. " +
                    "Check those transaction dates before using this forecast."
            }
            return ContractMoney.requireValid(confirmedBankCents, "confirmed bank balance")
        }
        val datedTotal = cash.asSequence().filter { it.date <= day }
            .fold(BigInteger.ZERO) { total, row -> total + BigInteger.valueOf(row.amount_cents) }
        return ContractMoney.toLong(datedTotal)
    }

    // Pending cash rows remain scheduled on their recorded date. They are ledger commitments,
    // not evidence that the bank has already cleared the payment or receipt.
    fun nativeEvents(rows: List<TransactionEntity>, endExclusive: LocalDate): List<ForecastEvent> =
        rows.mapNotNull { row ->
            val date = LedgerDate.parseIsoOrNull(row.date) ?: return@mapNotNull null
            if (date >= endExclusive) return@mapNotNull null
            ForecastEvent(
                date = date,
                description = row.description.ifBlank { row.category },
                amount_cents = if (row.amount_cents < 0) Math.negateExact(row.amount_cents) else row.amount_cents,
                type = if (row.amount_cents >= 0) "income" else "expense",
            )
        }
}
