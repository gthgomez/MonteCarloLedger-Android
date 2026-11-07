package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/** The backtest holdout must contain only realized, complete months. */
class RiskBacktestBoundaryTest {

    private val today = LocalDate.of(2026, 12, 31)

    private fun expense(cents: Long, date: String) = TransactionEntity(
        description = "Expense $date",
        amount_cents = -cents,
        date = date,
        type = "expense",
        category = "bills",
    )

    private fun income(cents: Long, date: String) = TransactionEntity(
        description = "Paycheck $date",
        amount_cents = cents,
        date = date,
        type = "income",
        category = "income",
    )

    private fun ledger(): List<TransactionEntity> = buildList {
        for (month in 1..10) {
            val stamp = "2026-%02d".format(month)
            add(income(200_000L + month * 1_000L, "$stamp-01"))
            add(expense(100_000L, "$stamp-02"))
            add(expense(30_000L + month * 500L, "$stamp-15"))
        }
    }

    @Test
    fun futureDatedRowsDoNotBecomeHoldoutMonths() {
        val base = RiskBacktest.run(ledger(), today = today, seed = 42, runs = 50)
        val withFuture = ledger() + income(9_999_999L, "2027-06-01") +
            expense(9_999_999L, "2027-06-02")
        val after = RiskBacktest.run(withFuture, today = today, seed = 42, runs = 50)
        assertEquals(base.holdoutMonths, after.holdoutMonths)
        assertEquals(base.observations, after.observations)
    }
}
