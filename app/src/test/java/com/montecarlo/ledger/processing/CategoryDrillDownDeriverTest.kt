package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CategoryDrillDownDeriverTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 21)

    private fun expense(
        description: String,
        amountCents: Long,
        date: LocalDate,
        category: String = "groceries",
    ) = TransactionEntity(
        description = description,
        amount_cents = -amountCents,
        date = date.toString(),
        type = "expense",
        category = category,
    )

    @Test
    fun build_summarizesCurrentWindowAgainstPrevious() {
        val transactions = listOf(
            expense("Whole Foods", 10_000L, today.minusDays(1)),
            expense("whole foods", 5_000L, today.minusDays(11)),
            expense("Trader Joes", 2_000L, today.minusDays(27)),
            // Exactly on the window boundary still belongs to the current window.
            expense("Boundary", 500L, today.minusDays(30)),
            // Previous window of equal length.
            expense("Whole Foods", 30_000L, today.minusDays(31 + 14)),
            // Outside both windows.
            expense("Ancient", 99_000L, today.minusDays(61)),
            // Not expenses, not this category.
            TransactionEntity(
                description = "Paycheck",
                amount_cents = 400_000L,
                date = today.minusDays(5).toString(),
                type = "income",
                category = "groceries",
            ),
            expense("Dining out", 7_777L, today.minusDays(3), category = "dining"),
        )

        val drillDown = CategoryDrillDownDeriver.build(transactions, "Groceries", today)!!

        assertEquals("groceries", drillDown.category)
        assertEquals(30, drillDown.windowDays)
        // Current window includes the boundary day charge: 10000+5000+2000+500.
        assertEquals(17_500L, drillDown.totalCents)
        assertEquals(4, drillDown.transactionCount)
        assertEquals(17_500L / 4, drillDown.averageCents)
        assertEquals(10_000L, drillDown.largestCents)
        assertEquals(30_000L, drillDown.previousWindowCents)

        assertNotNull(drillDown.changePercent)
        assertEquals(-41.67, drillDown.changePercent!!, 0.01)
        assertEquals("down sharply vs previous 30d", drillDown.trendLabel)
    }

    @Test
    fun build_rollsUpMerchantsByNormalizedDescription_largestFirst() {
        val transactions = listOf(
            expense("Whole Foods Market", 3_000L, today.minusDays(2)),
            expense("whole   foods market", 9_000L, today.minusDays(4)),
            expense("Kroger", 6_000L, today.minusDays(6)),
        )

        val drillDown = CategoryDrillDownDeriver.build(transactions, "groceries", today)!!

        val merchants = drillDown.topMerchants
        assertEquals("whole foods market", merchants.first().label)
        assertEquals(12_000L, merchants.first().totalCents)
        assertEquals(2, merchants.first().count)
        assertEquals(2, merchants.size)
    }

    @Test
    fun build_returnsNull_whenNeitherWindowHasActivity() {
        val transactions = listOf(
            expense("Old spend", 5_000L, today.minusDays(120)),
        )
        assertNull(CategoryDrillDownDeriver.build(transactions, "groceries", today))
    }

    @Test
    fun build_changeIsNull_withoutPreviousBaseline() {
        val transactions = listOf(
            expense("Fresh start", 4_000L, today.minusDays(3)),
        )
        val drillDown = CategoryDrillDownDeriver.build(transactions, "groceries", today)!!
        assertNull(drillDown.changePercent)
        assertNull(drillDown.trendLabel)
        assertEquals(0L, drillDown.previousWindowCents)
    }

    @Test
    fun build_reportsRisingSpend_asSharpUp() {
        val transactions = listOf(
            expense("Now expensive habit", 60_000L, today.minusDays(3), category = "coffee"),
            expense("Was cheap before", 20_000L, today.minusDays(45), category = "coffee"),
        )
        val drillDown = CategoryDrillDownDeriver.build(transactions, "coffee", today)!!
        assertEquals(200.00, drillDown.changePercent!!, 0.01)
        assertEquals("up sharply vs previous 30d", drillDown.trendLabel)
    }

    @Test
    fun build_handlesUnparseableDates_byIgnoringThoseRows() {
        val transactions = listOf(
            TransactionEntity(
                description = "Broken date row",
                amount_cents = -1_000L,
                date = "not-a-date",
                type = "expense",
                category = "groceries",
            ),
            expense("Valid", 2_000L, today.minusDays(2)),
        )
        val drillDown = CategoryDrillDownDeriver.build(transactions, "groceries", today)!!
        assertEquals(1, drillDown.transactionCount)
        assertEquals(2_000L, drillDown.totalCents)
    }
}
