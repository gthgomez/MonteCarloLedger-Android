package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.CategoryBudgetEntity
import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CategoryBudgetTrackerTest {

    private val today = LocalDate.of(2026, 4, 15)

    @Test
    fun compute_showsSpentVsLimitForMatchedCategory() {
        val budgets = listOf(
            CategoryBudgetEntity(
                id = 1,
                category = "dining",
                limitCents = 50_000L,
                enabled = 1,
                createdAt = "2026-04-01",
            )
        )
        val transactions = listOf(
            TransactionEntity(
                description = "Restaurant",
                amount_cents = -12_000L,
                date = "2026-04-10",
                type = "expense",
                category = "dining",
            ),
            TransactionEntity(
                description = "Coffee",
                amount_cents = -450L,
                date = "2026-04-12",
                type = "expense",
                category = "dining",
            ),
        )

        val rows = CategoryBudgetTracker.evaluate(budgets, transactions, today)

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals("dining", row.category)
        assertEquals(50_000L, row.limitCents)
        assertEquals(12_450L, row.spentCents)
        assertEquals(37_550L, row.remaining)
        assertFalse(row.overLimit)
        assertTrue(row.enabled)
        assertEquals(1, row.budgetId)
    }

    @Test
    fun compute_marksOverLimitWhenSpendingExceedsBudget() {
        val budgets = listOf(
            CategoryBudgetEntity(
                id = 2,
                category = "groceries",
                limitCents = 10_000L,
                enabled = 1,
                createdAt = "2026-04-01",
            )
        )
        val transactions = listOf(
            TransactionEntity(
                description = "Supermarket",
                amount_cents = -12_500L,
                date = "2026-04-08",
                type = "expense",
                category = "groceries",
            ),
        )

        val rows = CategoryBudgetTracker.evaluate(budgets, transactions, today)

        assertEquals(1, rows.size)
        val row = rows.single()
        assertTrue(row.overLimit)
        assertEquals(10_000L, row.limitCents)
        assertEquals(12_500L, row.spentCents)
        assertEquals(-2_500L, row.remaining)
    }

    @Test
    fun compute_matchesCategoryCaseInsensitively() {
        val budgets = listOf(
            CategoryBudgetEntity(
                id = 3,
                category = "Dining",
                limitCents = 30_000L,
                enabled = 1,
                createdAt = "2026-04-01",
            )
        )
        val transactions = listOf(
            TransactionEntity(
                description = "Pizza",
                amount_cents = -15_000L,
                date = "2026-04-05",
                type = "expense",
                category = "dining",  // lowercase
            ),
            TransactionEntity(
                description = "Burger",
                amount_cents = -8_000L,
                date = "2026-04-10",
                type = "expense",
                category = "DINING",  // uppercase
            ),
        )

        val rows = CategoryBudgetTracker.evaluate(budgets, transactions, today)

        assertEquals(1, rows.size)
        val row = rows.single()
        assertEquals(23_000L, row.spentCents)
    }

    @Test
    fun compute_ignoresTransactionsOutsideCurrentMonth() {
        val budgets = listOf(
            CategoryBudgetEntity(
                id = 4,
                category = "shopping",
                limitCents = 20_000L,
                enabled = 1,
                createdAt = "2026-04-01",
            )
        )
        val transactions = listOf(
            TransactionEntity(
                description = "March jacket",
                amount_cents = -15_000L,
                date = "2026-03-28",
                type = "expense",
                category = "shopping",
            ),
            TransactionEntity(
                description = "April shoes",
                amount_cents = -5_000L,
                date = "2026-04-03",
                type = "expense",
                category = "shopping",
            ),
        )

        val rows = CategoryBudgetTracker.evaluate(budgets, transactions, today)

        assertEquals(5_000L, rows.single().spentCents)
        assertFalse(rows.single().overLimit)
    }

    @Test
    fun compute_disabledBudgetShowsZeroSpend() {
        val budgets = listOf(
            CategoryBudgetEntity(
                id = 5,
                category = "entertainment",
                limitCents = 10_000L,
                enabled = 0,  // disabled
                createdAt = "2026-04-01",
            )
        )
        val transactions = listOf(
            TransactionEntity(
                description = "Movies",
                amount_cents = -5_000L,
                date = "2026-04-10",
                type = "expense",
                category = "entertainment",
            ),
        )

        val rows = CategoryBudgetTracker.evaluate(budgets, transactions, today)

        assertEquals(0L, rows.single().spentCents)
        assertFalse(rows.single().overLimit)
        assertFalse(rows.single().enabled)
    }

    @Test
    fun compute_ignoresIncomeTransactions() {
        val budgets = listOf(
            CategoryBudgetEntity(
                id = 6,
                category = "dining",
                limitCents = 50_000L,
                enabled = 1,
                createdAt = "2026-04-01",
            )
        )
        val transactions = listOf(
            TransactionEntity(
                description = "Paycheck",
                amount_cents = 200_000L,
                date = "2026-04-01",
                type = "income",
                category = "dining",  // same category but income type
            ),
            TransactionEntity(
                description = "Lunch",
                amount_cents = -3_000L,
                date = "2026-04-05",
                type = "expense",
                category = "dining",
            ),
        )

        val rows = CategoryBudgetTracker.evaluate(budgets, transactions, today)

        assertEquals(3_000L, rows.single().spentCents)
    }

    @Test
    fun compute_emptyBudgetsReturnsEmptyList() {
        val rows = CategoryBudgetTracker.evaluate(emptyList(), emptyList(), today)
        assertTrue(rows.isEmpty())
    }

    @Test
    fun compute_multipleBudgetsEachTrackTheirCategory() {
        val budgets = listOf(
            CategoryBudgetEntity(id = 1, category = "dining", limitCents = 50_000L, enabled = 1, createdAt = "2026-04-01"),
            CategoryBudgetEntity(id = 2, category = "groceries", limitCents = 80_000L, enabled = 1, createdAt = "2026-04-01"),
        )
        val transactions = listOf(
            TransactionEntity(description = "Restaurant", amount_cents = -12_000L, date = "2026-04-10", type = "expense", category = "dining"),
            TransactionEntity(description = "Supermarket", amount_cents = -35_000L, date = "2026-04-12", type = "expense", category = "groceries"),
            TransactionEntity(description = "Cafe", amount_cents = -5_000L, date = "2026-04-14", type = "expense", category = "dining"),
        )

        val rows = CategoryBudgetTracker.evaluate(budgets, transactions, today)
        assertEquals(2, rows.size)

        val diningRow = rows.first { it.category == "dining" }
        assertEquals(17_000L, diningRow.spentCents)
        assertEquals(33_000L, diningRow.remaining)

        val groceriesRow = rows.first { it.category == "groceries" }
        assertEquals(35_000L, groceriesRow.spentCents)
        assertEquals(45_000L, groceriesRow.remaining)
    }
}
