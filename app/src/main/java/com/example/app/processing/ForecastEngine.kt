package com.example.app.processing

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

internal val EVENT_COMPARATOR: Comparator<ForecastEvent> = compareBy<ForecastEvent> { it.date }
    .thenBy { if (it.type == "income") 0 else 1 }

data class BalanceForecastRow(
    val date: LocalDate,
    val balanceCents: Int,
)

data class ForecastSummary(
    /** Minimum running balance across the window. Negative when the forecast goes into overdraft. */
    val safeToSpendCents: Int,
    val lowestBalanceCents: Int,
    val lowestBalanceDate: LocalDate?,
    val endingBalanceCents: Int,
    val firstNegativeDate: LocalDate?,
)

data class CashFlowWindow(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startingBalanceCents: Int,
    val incomeCents: Int,
    val billCents: Int,
    val lowestBalanceCents: Int,
    val endingBalanceCents: Int,
    val safeToSpendCents: Int,
    val dailySafeSpendCents: Int,
    val shortfallCents: Int,
    val days: Int,
)

object ForecastEngine {

    // Returns the minimum running balance across the event window — can be negative (overdraft signal).
    fun calculateSafeToSpend(balanceCents: Int, events: List<ForecastEvent>): Int {
        var runningBalance = balanceCents.toLong()
        var lowestBalance = balanceCents.toLong()

        for (event in events.sortedWith(EVENT_COMPARATOR)) {
            runningBalance += if (event.type == "income") event.amount_cents else -event.amount_cents
            lowestBalance = minOf(lowestBalance, runningBalance)
        }

        return lowestBalance.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
    }

    fun calculateDailySafeSpend(balanceCents: Int, events: List<ForecastEvent>, daysUntilPayday: Int): Int {
        val safeToSpend = calculateSafeToSpend(balanceCents, events)
        if (safeToSpend <= 0) return 0
        return if (daysUntilPayday > 0) safeToSpend / daysUntilPayday else safeToSpend
    }

    fun buildCashFlowWindows(
        balanceCents: Int,
        events: List<ForecastEvent>,
        startDate: LocalDate,
        daysAhead: Int,
    ): List<CashFlowWindow> {
        if (daysAhead <= 0) return emptyList()

        val endDate = startDate.plusDays(daysAhead.toLong())
        val sortedEvents = events
            .filter { !it.date.isBefore(startDate) && it.date.isBefore(endDate) }
            .sortedWith(EVENT_COMPARATOR)
        val paycheckDates = sortedEvents
            .filter { it.type == "income" }
            .map { it.date }
            .distinct()
            .sorted()

        val windowStarts = buildList {
            add(startDate)
            paycheckDates
                .filter { it.isAfter(startDate) }
                .forEach { add(it) }
        }

        var runningBalance = balanceCents.toLong()
        return windowStarts.mapIndexed { index, windowStart ->
            val windowEnd = windowStarts.getOrNull(index + 1) ?: endDate
            val windowEvents = sortedEvents.filter { !it.date.isBefore(windowStart) && it.date.isBefore(windowEnd) }
            val startingBalance = runningBalance
            var incomeCents = 0L
            var billCents = 0L
            val openingIncomeEvents = windowEvents.filter { it.date == windowStart && it.type == "income" }
            openingIncomeEvents.forEach { event ->
                incomeCents += event.amount_cents
                runningBalance += event.amount_cents
            }
            var lowestBalance = runningBalance

            windowEvents.filterNot { it.date == windowStart && it.type == "income" }.forEach { event ->
                if (event.type == "income") {
                    incomeCents += event.amount_cents
                    runningBalance += event.amount_cents
                } else {
                    billCents += event.amount_cents
                    runningBalance -= event.amount_cents
                }
                lowestBalance = minOf(lowestBalance, runningBalance)
            }

            val days = ChronoUnit.DAYS.between(windowStart, windowEnd).toInt().coerceAtLeast(1)
            val safeToSpend = lowestBalance.coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            CashFlowWindow(
                startDate = windowStart,
                endDate = windowEnd,
                startingBalanceCents = startingBalance.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                incomeCents = incomeCents.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                billCents = billCents.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                lowestBalanceCents = lowestBalance.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                endingBalanceCents = runningBalance.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                safeToSpendCents = safeToSpend,
                dailySafeSpendCents = safeToSpend / days,
                shortfallCents = (-lowestBalance).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                days = days,
            )
        }
    }

    /**
     * Computes the worst-case buffer improvement that upcoming income events provide
     * over a bills-only scenario.
     */
    fun calculateIncomeContribution(balanceCents: Int, events: List<ForecastEvent>): Int {
        val sorted = events.sortedWith(EVENT_COMPARATOR)
        var runningProjected = balanceCents.toLong()
        var runningCash = balanceCents.toLong()
        var lowestProjected = balanceCents.toLong()
        var minCash = balanceCents.toLong()

        for (event in sorted) {
            if (event.type == "income") {
                runningProjected += event.amount_cents
            } else {
                runningProjected -= event.amount_cents
                runningCash -= event.amount_cents
            }
            if (runningProjected < lowestProjected) lowestProjected = runningProjected
            if (runningCash < minCash) minCash = runningCash
        }

        val safeToSpend = maxOf(0L, lowestProjected)
        val currentCashContribution = maxOf(0L, minCash)
        val contribution = safeToSpend - currentCashContribution
        return maxOf(0L, contribution).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun buildBalanceForecast(balanceCents: Int, events: List<ForecastEvent>): List<BalanceForecastRow> {
        var runningBalance = balanceCents.toLong()
        return events.sortedWith(EVENT_COMPARATOR).map { event ->
            runningBalance += if (event.type == "income") event.amount_cents else -event.amount_cents
            BalanceForecastRow(
                date = event.date,
                balanceCents = runningBalance.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
            )
        }
    }

    fun calculateForecastSummary(balanceCents: Int, events: List<ForecastEvent>): ForecastSummary {
        val forecastRows = buildBalanceForecast(balanceCents, events)
        val endingBalance = forecastRows.lastOrNull()?.balanceCents ?: balanceCents

        var lowestBalance = balanceCents
        var lowestBalanceDate: LocalDate? = null
        var firstNegativeDate: LocalDate? = null

        for (row in forecastRows) {
            if (row.balanceCents < lowestBalance) {
                lowestBalance = row.balanceCents
                lowestBalanceDate = row.date
            }
            if (firstNegativeDate == null && row.balanceCents < 0) {
                firstNegativeDate = row.date
            }
        }

        return ForecastSummary(
            safeToSpendCents = lowestBalance,
            lowestBalanceCents = lowestBalance,
            lowestBalanceDate = lowestBalanceDate,
            endingBalanceCents = endingBalance,
            firstNegativeDate = firstNegativeDate,
        )
    }
}
