package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.RecurringCandidate
import com.montecarlo.ledger.domain.Categories
import com.montecarlo.ledger.util.LedgerDate
import com.montecarlo.ledger.data.TransactionEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object RecurringDetector {
    fun detect(transactions: List<TransactionEntity>): List<RecurringCandidate> {
        val grouped = transactions
            .filter { it.type != "adjustment" }
            .groupBy { normalizePattern(it.description) }

        return grouped.mapNotNull { (pattern, items) ->
            if (items.size < 3) return@mapNotNull null
            val dates = items.mapNotNull { LedgerDate.parseIsoOrNull(it.date) }.sorted()
            if (dates.size < 3) return@mapNotNull null

            val gaps = dates.zipWithNext().map { (a, b) -> ChronoUnit.DAYS.between(a, b).toInt() }
            val avgGap = gaps.average()
            val cadenceLabel = when {
                avgGap in 25.0..35.0 -> "Monthly"
                avgGap in 11.0..17.0 -> "Biweekly"
                avgGap in 6.0..8.0 -> "Weekly"
                else -> "Recurring"
            }

            val representative = items.groupBy { normalizeCategory(it.category) }
                .maxByOrNull { (_, bucket) -> bucket.size }
                ?.key
                .orEmpty()
                .ifBlank { "uncategorized" }

            RecurringCandidate(
                pattern = pattern,
                category = representative,
                cadenceLabel = cadenceLabel,
                occurrenceCount = items.size,
                lastSeenDate = dates.last().toString(),
            )
        }
            .sortedWith(
                compareByDescending<RecurringCandidate> { it.occurrenceCount }
                    .thenByDescending { it.pattern.length }
            )
            .take(8)
    }

    private fun normalizePattern(value: String): String = Categories.normalize(value)

    private fun normalizeCategory(value: String): String = Categories.normalize(value)
}
