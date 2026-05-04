package com.example.app.processing

import java.time.LocalDate

data class ForecastEvent(
    val date: LocalDate,
    val description: String,
    val amount_cents: Int,
    val type: String, // "income" or "payment"
    val recurrenceLabel: String? = null
)
