package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.CategoryBudgetEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.domain.Categories
import java.util.Locale
import java.time.LocalDate
import kotlin.math.abs

/**
 * Result row for a single category budget watchlist entry.
 * Computed from stored [CategoryBudgetEntity] + the current month's expense transactions.
 */
data class CategoryBudgetRow(
    val category: String,
    val limitCents: Long,
    val spentCents: Long,
    val remaining: Long,
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
    fun evaluate(
        budgets: List<CategoryBudgetEntity>,
        transactions: List<TransactionEntity>,
        today: LocalDate = LocalDate.now(),
    ): List<CategoryBudgetRow> {
        val startOfMonth = today.withDayOfMonth(1).toString()
        val endOfMonth = today.withDayOfMonth(today.lengthOfMonth()).toString()

        val expenseByCategory = transactions
            .asSequence()
            .filter { it.type.lowercase(Locale.ROOT) == "expense" }
            .filter {
                val d = it.date
                d in startOfMonth..endOfMonth
            }
            .groupBy { normalizeCategory(it.category) }
            .mapValues { (_, txns) -> txns.sumOf { abs(it.amount_cents) } }

        return budgets.map { budget ->
            val normalizedCategory = normalizeCategory(budget.category)
            val spentCents = if (budget.enabled != 0) {
                expenseByCategory[normalizedCategory] ?: 0L
            } else {
                0L
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

    private fun normalizeCategory(category: String): String = Categories.normalize(category)

    private fun parseDateOrNull(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim()) }.getOrNull()
}
