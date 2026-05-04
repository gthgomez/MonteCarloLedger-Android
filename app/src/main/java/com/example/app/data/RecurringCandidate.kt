package com.example.app.data

data class RecurringCandidate(
    val pattern: String,
    val category: String,
    val cadenceLabel: String,
    val occurrenceCount: Int,
    val lastSeenDate: String,
)
