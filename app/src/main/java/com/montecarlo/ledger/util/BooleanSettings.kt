package com.montecarlo.ledger.util

/** Reads persisted booleans from both the legacy numeric and canonical text formats. */
fun String?.toPersistedBoolean(): Boolean = when (this?.trim()?.lowercase()) {
    "true", "1" -> true
    else -> false
}

fun Boolean.toPersistedValue(): String = toString()
