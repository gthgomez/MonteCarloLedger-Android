package com.montecarlo.ledger.domain

import java.util.Locale

/**
 * Canonical category-key handling.
 *
 * Categories are free-text strings persisted in transactions, rules, budgets, and
 * presets. Every comparison MUST go through [normalize] so case and whitespace
 * drift cannot split one logical category into several.
 */
object Categories {

    const val UNCATEGORIZED = "uncategorized"

    /** Trim, lowercase, and collapse internal whitespace runs to a single space. */
    fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    fun normalizeOrUncategorized(value: String): String =
        normalize(value).ifBlank { UNCATEGORIZED }

    fun isUncategorized(value: String): Boolean {
        val normalized = normalize(value)
        return normalized.isEmpty() || normalized == UNCATEGORIZED
    }

    /** Categories whose expense transactions represent scheduled bills rather than variable spend. */
    private val BILL_CATEGORIES = setOf("bills", "bill", "utilities", "rent", "subscriptions")

    fun isBillCategory(value: String): Boolean = normalize(value) in BILL_CATEGORIES
}
