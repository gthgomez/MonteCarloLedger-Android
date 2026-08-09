package com.example.app.processing

import java.time.LocalDate

/**
 * A single event in a forecast or Monte Carlo timeline.
 *
 * @property type Event classification: "income", "expense", or "adjustment".
 *   Surprise / unexpected events (e.g. Monte Carlo injected expenses) use "expense"
 *   so that filters, summarizers, and dashboard aggregations handle them without
 *   a dedicated type.
 */
data class ForecastEvent(
    val date: LocalDate,
    val description: String,
    val amount_cents: Long,
    val type: String,
    val recurrenceLabel: String? = null
)
