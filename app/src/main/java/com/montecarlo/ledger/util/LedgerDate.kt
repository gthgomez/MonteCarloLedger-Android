package com.montecarlo.ledger.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Centralized date parsing for ledger data.
 *
 * All persisted dates are ISO-8601 (`LocalDate.toString`). Parsing MUST go through
 * [parseIsoOrNull] so a malformed value is handled at one place instead of a dozen
 * scattered `runCatching` blocks with silent-failure semantics.
 */
object LedgerDate {

    /** Parses an ISO-8601 date (yyyy-MM-dd). Returns null when blank or malformed. */
    fun parseIsoOrNull(value: String?): LocalDate? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return runCatching { LocalDate.parse(trimmed) }.getOrNull()
    }

    /**
     * Parses the date formats banks actually emit in statement CSVs. Order matters:
     * two-digit years must come after their four-digit counterparts so "01/02/2025"
     * resolves as Jan 2, 2025 (US convention) rather than being consumed early.
     */
    fun parseBankDateOrNull(raw: String?): LocalDate? {
        val value = raw?.trim()?.substringBefore('T')?.substringBefore(' ').orEmpty()
        if (value.isBlank()) return null

        for (formatter in BANK_DATE_FORMATTERS) {
            try {
                return LocalDate.parse(value, formatter)
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private val BANK_DATE_FORMATTERS = listOf(
        DateTimeFormatter.ofPattern("uuuu-MM-dd"),
        DateTimeFormatter.ofPattern("uuuu/M/d"),
        DateTimeFormatter.ofPattern("M/d/uuuu"),
        DateTimeFormatter.ofPattern("MM/dd/uuuu"),
        DateTimeFormatter.ofPattern("M/d/yy"),
        DateTimeFormatter.ofPattern("MM/dd/yy"),
        DateTimeFormatter.ofPattern("M-d-uuuu"),
        DateTimeFormatter.ofPattern("MM-dd-uuuu"),
        // Unambiguous EU fallbacks: "13/02/2026" cannot be US month/day (month 13 is
        // invalid) so it resolves as dd/MM. Ambiguous low-day values like "01/02/2026"
        // keep the US-first convention used across the app.
        DateTimeFormatter.ofPattern("d/M/uuuu"),
        DateTimeFormatter.ofPattern("d/M/yy"),
        DateTimeFormatter.ofPattern("d-M-uuuu"),
        DateTimeFormatter.ofPattern("d-M-yy"),
    )
}
