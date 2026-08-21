package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.domain.Categories
import com.montecarlo.ledger.util.LedgerDate
import java.time.LocalDate

/** Merchant-level rollup inside a category window, keyed on normalized description. */
data class CategoryMerchantSpend(
    val label: String,
    val totalCents: Long,
    val count: Int,
)

/**
 * Plain-language drill-down for one spending category: what it cost this window,
 * how that compares to the previous window of the same length, and where the money went.
 */
data class CategoryDrillDown(
    val category: String,
    val windowDays: Int,
    /** Total spent in the current window (positive cents). */
    val totalCents: Long,
    val transactionCount: Int,
    /** Mean spend per occurrence in the current window. */
    val averageCents: Long,
    /** Largest single charge in the current window (positive cents). */
    val largestCents: Long,
    /** Total spent in the immediately preceding window of equal length. */
    val previousWindowCents: Long,
    /**
     * Percent change vs the previous window: positive means spending rose.
     * Null when there is no previous-window baseline to compare against.
     */
    val changePercent: Double?,
    /** Biggest merchant descriptions first, capped for display. */
    val topMerchants: List<CategoryMerchantSpend>,
) {
    val trendLabel: String?
        get() {
            val change = changePercent ?: return null
            return when {
                change >= 20.0 -> "up sharply vs previous ${windowDays}d"
                change >= 5.0 -> "up vs previous ${windowDays}d"
                change <= -20.0 -> "down sharply vs previous ${windowDays}d"
                change <= -5.0 -> "down vs previous ${windowDays}d"
                else -> "flat vs previous ${windowDays}d"
            }
        }
}

object CategoryDrillDownDeriver {

    const val DEFAULT_WINDOW_DAYS = 30
    private const val MAX_MERCHANT_ROWS = 5

    /**
     * Builds a drill-down for [category] over [windowDays] ending today.
     * Returns null when the category has no expense activity in either window —
     * an empty panel teaches nothing and would read as broken.
     */
    fun build(
        transactions: List<TransactionEntity>,
        category: String,
        today: LocalDate,
        windowDays: Int = DEFAULT_WINDOW_DAYS,
    ): CategoryDrillDown? {
        require(windowDays > 0) { "windowDays must be positive" }
        val target = Categories.normalize(category)
        val expenses = transactions.asSequence()
            .filter { it.type == "expense" && Categories.normalize(it.category) == target }
            .mapNotNull { txn ->
                LedgerDate.parseIsoOrNull(txn.date)?.let { date -> date to txn }
            }
            .toList()

        val currentStart = today.minusDays(windowDays.toLong())
        val previousStart = currentStart.minusDays(windowDays.toLong())

        val current = expenses.filter { (date, _) -> date.isAfter(currentStart) || date == currentStart }
            .map { (_, txn) -> txn }
        val previous = expenses.filter { (date, _) ->
            !date.isBefore(previousStart) && date.isBefore(currentStart)
        }.map { (_, txn) -> txn }

        if (current.isEmpty() && previous.isEmpty()) return null

        val currentTotal = current.sumOf { kotlin.math.abs(it.amount_cents) }
        val previousTotal = previous.sumOf { kotlin.math.abs(it.amount_cents) }
        val changePercent = if (previous.isEmpty()) {
            null
        } else if (previousTotal == 0L) {
            if (currentTotal == 0L) 0.0 else null
        } else {
            // Exact integer ratio scaled to percent, rounded HALF_UP.
            val scaled = java.math.BigDecimal.valueOf(currentTotal)
                .multiply(java.math.BigDecimal.valueOf(10_000L))
                .divide(java.math.BigDecimal.valueOf(previousTotal), 0, java.math.RoundingMode.HALF_UP)
                .toLong()
            (scaled / 100.0) - 100.0
        }

        val merchants = current
            .groupBy { merchantLabel(it.description) }
            .map { (label, rows) ->
                CategoryMerchantSpend(
                    label = label,
                    totalCents = rows.sumOf { kotlin.math.abs(it.amount_cents) },
                    count = rows.size,
                )
            }
            .sortedByDescending { it.totalCents }
            .take(MAX_MERCHANT_ROWS)

        return CategoryDrillDown(
            category = target,
            windowDays = windowDays,
            totalCents = currentTotal,
            transactionCount = current.size,
            averageCents = if (current.isEmpty()) 0L else currentTotal / current.size,
            largestCents = current.maxOfOrNull { kotlin.math.abs(it.amount_cents) } ?: 0L,
            previousWindowCents = previousTotal,
            changePercent = changePercent,
            topMerchants = merchants,
        )
    }

    private fun merchantLabel(description: String): String {
        val normalized = Categories.normalize(description)
        if (normalized.isBlank()) return "Unlabeled"
        // Truncate long statements so merchant rows stay scannable.
        return if (normalized.length > 28) normalized.take(27) + "…" else normalized
    }
}
