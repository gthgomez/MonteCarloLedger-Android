package com.montecarlo.ledger.data

data class RecurringCandidate(
    val pattern: String,
    val category: String,
    val cadenceLabel: String,
    val occurrenceCount: Int,
    val lastSeenDate: String,
)
