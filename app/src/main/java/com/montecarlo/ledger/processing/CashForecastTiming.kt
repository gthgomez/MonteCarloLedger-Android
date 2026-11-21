package com.montecarlo.ledger.processing

import com.montecarlo.ledger.contract.ContractMoney
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.util.LedgerDate
import java.math.BigInteger
import java.time.LocalDate

object CashForecastTiming {
    fun opening(seed: Long, cash: List<TransactionEntity>, asOf: LocalDate): Long {
        val day = asOf.toString()
        if (cash.none { it.date > day }) return seed
        // Reconciliation stores no cutoff that says whether the manually entered bank
        // figure includes each future row. The dated ledger supplies an unambiguous
        // opening when there are future entries. The displayed bank figure stays intact.
        val datedTotal = cash.asSequence().filter { it.date <= day }
            .fold(BigInteger.ZERO) { total, row -> total + BigInteger.valueOf(row.amount_cents) }
        return ContractMoney.toLong(datedTotal)
    }

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
