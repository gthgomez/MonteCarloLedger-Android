package com.montecarlo.ledger.processing

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.montecarlo.ledger.processing.RecurrenceMath

object PaymentSchedule {
    val recurrenceOptions = listOf(
        "Weekly",
        "Bi-weekly",
        "Semi-monthly",
        "Monthly",
        "Bi-monthly",
        "Quarterly",
        "Yearly",
        "One-time",
    )

    fun requiresExplicitDueDate(recurrence: String): Boolean {
        return normalize(recurrence) != "monthly"
    }

    fun resolveNextPaymentDate(
        today: LocalDate,
        recurrence: String,
        dueDay: Int?,
        dueDate: LocalDate?,
    ): String? {
        return when (normalize(recurrence)) {
            "monthly" -> {
                val day = (dueDay ?: dueDate?.dayOfMonth ?: 1).coerceIn(1, 31)
                var next = dueDate ?: run {
                    val thisMonth = today.withDayOfMonth(day.coerceAtMost(today.lengthOfMonth()))
                    if (thisMonth >= today) thisMonth else RecurrenceMath.nextDate(thisMonth, recurrence, day)!!
                }
                while (next.isBefore(today)) {
                    next = RecurrenceMath.nextDate(next, recurrence, day) ?: return next.toString()
                }
                next.toString()
            }
            "onetime" -> dueDate?.toString()
            else -> {
                var next = dueDate ?: return null
                while (next.isBefore(today)) {
                    next = RecurrenceMath.nextDate(next, recurrence, dueDay ?: next.dayOfMonth)
                        ?: return next.toString()
                }
                next.toString()
            }
        }
    }

    /** An empty stored date is reserved for an imported monthly day rule. */
    fun firstDueInOrBeforeWindow(recurrence: String, dueDay: Int?, storedDate: String, asOf: LocalDate): LocalDate? {
        if (storedDate.isNotEmpty()) return com.montecarlo.ledger.util.LedgerDate.parseIsoOrNull(storedDate)
        if (normalize(recurrence) != "monthly" || dueDay == null || dueDay !in 1..31) return null
        val thisMonth = asOf.withDayOfMonth(minOf(dueDay, asOf.lengthOfMonth()))
        return if (thisMonth >= asOf) thisMonth else RecurrenceMath.nextDate(thisMonth, recurrence, dueDay)
    }

    fun recurrenceSummary(
        recurrence: String,
        dayOfMonth: Int? = null,
        nextDate: String? = null,
    ): String {
        return when (normalize(recurrence)) {
            "weekly" -> "Every week"
            "biweekly" -> "Every 2 weeks"
            "semimonthly" -> "Twice a month"
            "monthly" -> dayOfMonth?.let { "Monthly on the ${ordinalSuffix(it.coerceIn(1, 31))}" } ?: "Monthly"
            "bimonthly" -> "Every 2 months"
            "quarterly" -> "Every 3 months"
            "annually", "yearly" -> "Yearly"
            "onetime" -> nextDate?.let {
                val parsed = runCatching { LocalDate.parse(it) }.getOrNull()
                if (parsed != null) {
                    "One-time due ${parsed.format(displayDateFormatter)}"
                } else {
                    "One-time due $it"
                }
            } ?: "One-time"
            else -> recurrence.replace('-', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun normalize(frequency: String): String {
        return RecurrenceMath.normalizeFrequency(frequency)
    }

    private fun ordinalSuffix(value: Int): String {
        val suffix = when {
            value % 100 in 11..13 -> "th"
            value % 10 == 1 -> "st"
            value % 10 == 2 -> "nd"
            value % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$value$suffix"
    }

    private val displayDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
}
