package com.example.app.ui

import java.time.LocalDate

internal val SCHEDULE_DATE_OPTIONS = listOf("Today", "Tomorrow", "Next Week", "Next Month", "Custom")

internal fun resolveScheduleDate(option: String, customDateText: String): String {
    val today = LocalDate.now()
    val resolved = when (option) {
        "Today" -> today
        "Tomorrow" -> today.plusDays(1)
        "Next Week" -> today.plusWeeks(1)
        "Next Month" -> today.plusMonths(1)
        "Custom" -> customDateText.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today
        else -> today
    }
    return resolved.toString()
}
