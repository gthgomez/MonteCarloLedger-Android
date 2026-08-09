package com.example.app.processing

import com.example.app.data.CategoryBudgetEntity
import com.example.app.data.TransactionEntity
import java.time.LocalDate
import java.util.Locale
import kotlin.math.abs

/**
 * Result row for a single category budget watchlist entry.
 * Computed from stored [CategoryBudgetEntity] + the current month's expense transactions.
 */
data class CategoryBudgetRow(
    val category: String,
    val limitCents: Int,
    val spentCents: Int,
    val remaining: Int,
    val overLimit: Boolean,
    val enabled: Boolean,
    val budgetId: Int,
)

/**
 * Pure calculator: turns saved category budget limits + this month's expense transactions
 * into per-category spend-vs-limit rows.
 *
 * Soft watchlist only — never blocks transaction insert.
 */
object CategoryBudgetTracker {

    /**
     * For each enabled budget, sum expense transactions in the current calendar month
     * whose category (lowercased) matches the budget category.
     *
     * Disabled budgets are included with zero spend (so the UI can show them as paused).
     */
    fun compute(
        budgets: List<CategoryBudgetEntity>,
        transactions: List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
    ): List<CategoryBudgetRow> {
        val monthStart = today.withDayOfMonth(1)
        val monthEndExclusive = monthStart.plusMonths(1)

        val expenseByCategory: Map<String, Int> = transactions
            .filter { it.type == "expense" }
            .filter { tx ->
                val date = parseDateOrNull(tx.date) ?: return@filter false
                !date.isBefore(monthStart) && date.isBefore(monthEndExclusive)
            }
            .groupBy { normalizeCategory(it.category) }
            .mapValues { (_, txns) -> txns.sumOf { abs(it.amount_cents.toLong()) }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }

        return budgets.map { budget ->
            val normalizedCategory = normalizeCategory(budget.category)
            val spentCents = if (budget.enabled != 0) {
                expenseByCategory[normalizedCategory] ?: 0
            } else {
                0
            }
            val remaining = budget.limitCents - spentCents
            CategoryBudgetRow(
                category = budget.category,
                limitCents = budget.limitCents,
                spentCents = spentCents,
                remaining = remaining,
                overLimit = budget.enabled != 0 && remaining < 0,
                enabled = budget.enabled != 0,
                budgetId = budget.id,
            )
        }
    }

    private fun normalizeCategory(category: String): String =
        category.trim().lowercase(Locale.ROOT)

    private fun parseDateOrNull(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim()) }.getOrNull()
}
