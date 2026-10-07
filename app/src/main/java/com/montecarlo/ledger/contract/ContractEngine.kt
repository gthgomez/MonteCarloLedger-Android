package com.montecarlo.ledger.contract

import java.time.LocalDate

/**
 * Deterministic contract engine: recurrence expansion, canonical ordering, and the forecast metrics
 * of `contracts/forecast.md` / `contracts/timeline.md`.
 *
 * This path never reads a clock. `as_of` is always supplied by the scenario (MCD-0001).
 */
object ContractEngine {

    /** Total order `(date, sequence, input_index)` from `contracts/timeline.md`. */
    internal val EVENT_COMPARATOR: Comparator<ContractEvent> =
        compareBy<ContractEvent>({ it.date }, { it.effectiveSequence }, { it.inputIndex })

    data class Forecast(
        val minimumBalanceCents: Long,
        val minimumBalanceDate: LocalDate,
        val endingBalanceCents: Long,
        val firstNegativeDate: LocalDate?,
    )

    /**
     * Expands recurrences, drops out-of-window (including overdue) events, applies
     * `expected_amount_cents` to the first in-window income occurrence, and returns the canonical
     * ordering `(date, sequence, input_index)`.
     */
    fun inWindowBaseEvents(scenario: ContractScenario): List<ContractEvent> {
        val end = scenario.windowEndExclusive
        val expanded = ArrayList<ContractEvent>()
        var nextIndex = scenario.events.size
        // Contract 1.1 (MCD-0024): occurrences declared excluded are removed before the
        // expected-amount slot is decided, so an excluded occurrence does not consume it.
        val exclusions = scenario.occurrenceExclusions
            .mapTo(HashSet()) { it.recurrenceId to it.date }

        for (recurrence in scenario.recurrences) {
            val occurrences = generateOccurrences(recurrence, end)
            var firstKept = true
            for (date in occurrences) {
                // Outer filter keeps only the half-open window; overdue dates are not projected.
                if (date.isBefore(scenario.asOf) || !date.isBefore(end)) continue
                if ((recurrence.id to date) in exclusions) continue
                val amount = if (recurrence.type == "income" &&
                    recurrence.expectedAmountCents != null && firstKept
                ) {
                    recurrence.expectedAmountCents
                } else {
                    recurrence.amountCents
                }
                firstKept = false
                expanded.add(
                    ContractEvent(
                        id = recurrence.id,
                        name = recurrence.name,
                        date = date,
                        amountCents = amount,
                        type = recurrence.type,
                        sequence = null,
                        inputIndex = nextIndex++,
                        category = recurrence.category,
                    )
                )
            }
        }

        return (scenario.events + expanded)
            .filter { !it.date.isBefore(scenario.asOf) && it.date.isBefore(end) }
            .sortedWith(EVENT_COMPARATOR)
    }

    /**
     * Produces occurrence dates for a recurrence, ascending, beginning no earlier than `start_date`
     * and stopping before `endExclusive` (or past `end_date`).
     *
     * The month anchor is preserved across clamped months by always projecting from `start_date`
     * with an index, rather than stepping from the previously clamped date (MCD-0016 / bug B-07).
     */
    internal fun generateOccurrences(recurrence: ContractRecurrence, endExclusive: LocalDate): List<LocalDate> {
        val result = ArrayList<LocalDate>()
        val start = recurrence.startDate
        val end = recurrence.endDate
        var guard = 0

        fun bounded(): Boolean {
            guard++
            if (guard > 1_000_000) {
                throw ContractException(
                    ContractErrorCode.SCHEMA_INVALID,
                    "recurrence '${recurrence.id}' failed to advance",
                )
            }
            return true
        }

        when (recurrence.frequency) {
            "weekly", "biweekly" -> {
                val stepWeeks = if (recurrence.frequency == "weekly") 1L else 2L
                var k = 0L
                while (true) {
                    val date = start.plusWeeks(stepWeeks * k)
                    if (!date.isBefore(endExclusive)) break
                    if (end != null && date.isAfter(end)) break
                    result.add(date)
                    k++
                    if (!bounded()) break
                }
            }

            "onetime" -> {
                if (start.isBefore(endExclusive) && (end == null || !start.isAfter(end))) {
                    result.add(start)
                }
            }

            "semimonthly" -> {
                val anchor = recurrence.anchorDay ?: start.dayOfMonth
                val fifteenAndLast = anchor >= 15
                var monthCursor = start.withDayOfMonth(1)
                loop@ while (true) {
                    val firstDay = if (fifteenAndLast) 15 else 1
                    val secondDay = if (fifteenAndLast) monthCursor.lengthOfMonth() else 15
                    for (day in intArrayOf(firstDay, secondDay)) {
                        val date = monthCursor.withDayOfMonth(day)
                        if (!date.isBefore(endExclusive)) break@loop
                        if (end != null && date.isAfter(end)) break@loop
                        if (!date.isBefore(start)) result.add(date)
                    }
                    monthCursor = monthCursor.plusMonths(1)
                    if (!bounded()) break
                }
            }

            else -> {
                val monthsPerStep = when (recurrence.frequency) {
                    "monthly" -> 1L
                    "bimonthly" -> 2L
                    "quarterly" -> 3L
                    "semiannually" -> 6L
                    "annually" -> 12L
                    else -> throw ContractException(
                        ContractErrorCode.SCHEMA_INVALID,
                        "unsupported frequency '${recurrence.frequency}'",
                    )
                }
                val anchor = recurrence.anchorDay ?: start.dayOfMonth
                var k = 0L
                while (true) {
                    // Project from the original start so clamped months never move the anchor.
                    val base = start.plusMonths(monthsPerStep * k)
                    val day = minOf(anchor, base.lengthOfMonth())
                    val date = base.withDayOfMonth(day)
                    if (!date.isBefore(endExclusive)) break
                    if (end != null && date.isAfter(end)) break
                    if (!date.isBefore(start)) result.add(date)
                    k++
                    if (!bounded()) break
                }
            }
        }
        return result
    }

    /**
     * Forecast metrics over an already-ordered event list.
     *
     * `minimum_balance_date` is the date of the first row achieving the minimum, or `as_of` when the
     * starting balance is the low point (never null, MCD-0018). `first_negative_date` is `as_of`
     * when the opening balance is negative (MCD-0010 / bug B-05).
     */
    fun forecast(startingBalanceCents: Long, orderedEvents: List<ContractEvent>, asOf: LocalDate): Forecast {
        var balance = startingBalanceCents
        var minimum = startingBalanceCents
        var minimumDate = asOf
        var firstNegative: LocalDate? = if (startingBalanceCents < 0) asOf else null

        for (event in orderedEvents) {
            balance = ContractMoney.checkedAdd(balance, event.amountCents)
            if (firstNegative == null && balance < 0L) firstNegative = event.date
            if (balance < minimum) {
                minimum = balance
                minimumDate = event.date
            }
        }

        return Forecast(
            minimumBalanceCents = minimum,
            minimumBalanceDate = minimumDate,
            endingBalanceCents = balance,
            firstNegativeDate = firstNegative,
        )
    }
}
