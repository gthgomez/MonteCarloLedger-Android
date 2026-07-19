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
        var runningBalance = balanceCents
        var lowestBalance = balanceCents

        for (event in events.sortedWith(EVENT_COMPARATOR)) {
            runningBalance += if (event.type == "income") event.amount_cents else -event.amount_cents
            lowestBalance = minOf(lowestBalance, runningBalance)
        }

        return lowestBalance
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

        var runningBalance = balanceCents
        return windowStarts.mapIndexed { index, windowStart ->
            val windowEnd = windowStarts.getOrNull(index + 1) ?: endDate
            val windowEvents = sortedEvents.filter { !it.date.isBefore(windowStart) && it.date.isBefore(windowEnd) }
            val startingBalance = runningBalance
            var incomeCents = 0
            var billCents = 0
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
            val safeToSpend = lowestBalance.coerceAtLeast(0)
            CashFlowWindow(
                startDate = windowStart,
                endDate = windowEnd,
                startingBalanceCents = startingBalance,
                incomeCents = incomeCents,
                billCents = billCents,
                lowestBalanceCents = lowestBalance,
                endingBalanceCents = runningBalance,
                safeToSpendCents = safeToSpend,
                dailySafeSpendCents = safeToSpend / days,
                shortfallCents = (-lowestBalance).coerceAtLeast(0),
                days = days,
            )
        }
    }

    /**
     * Computes the worst-case buffer improvement that upcoming income events provide
     * over a bills-only scenario.
     *
     * The function runs two parallel balance simulations across the sorted event window,
     * both starting from [balanceCents]:
     *
     * 1. **All-events path** (`lowestProjected`) — processes every event (income adds,
     *    bills subtract). This is the actual projected balance floor.
     * 2. **Bills-only path** (`minCash`) — processes only bill events, skipping income
     *    entirely. This is the balance floor if no income arrived.
     *
     * Each path's minimum is floored at zero. The contribution is the difference:
     *
     * ```
     * contribution = max(0, max(0, lowestProjected) - max(0, minCash))
     * ```
     *
     * In other words: how many cents does income lift the worst-case balance above
     * what it would be with bills alone?
     *
     * **Returns** a non-negative value in cents. Zero means income provides no
     * measurable buffer improvement (either because there is no income, both paths
     * go negative, or bills alone never dip below the all-events floor).
     *
     * @param balanceCents starting balance in cents (non-negative).
     * @param events       forecast events to simulate (income + bills, may be empty).
     * @return worst-case buffer improvement attributable to income, in cents.
     */
    fun calculateIncomeContribution(balanceCents: Int, events: List<ForecastEvent>): Int {
        val sorted = events.sortedWith(EVENT_COMPARATOR)
        var runningProjected = balanceCents
        var runningCash = balanceCents
        var lowestProjected = balanceCents
        var minCash = balanceCents

        for (event in sorted) {
            if (event.type == "income") {
                runningProjected += event.amount_cents
                // runningCash unchanged — we're computing bills-only
            } else {
                runningProjected -= event.amount_cents
                runningCash -= event.amount_cents
            }
            if (runningProjected < lowestProjected) lowestProjected = runningProjected
            if (runningCash < minCash) minCash = runningCash
        }

        val safeToSpend = maxOf(0, lowestProjected)
        val currentCashContribution = maxOf(0, minCash)
        return maxOf(0, safeToSpend - currentCashContribution)
    }

    fun buildBalanceForecast(balanceCents: Int, events: List<ForecastEvent>): List<BalanceForecastRow> {
        var runningBalance = balanceCents
        return events.sortedWith(EVENT_COMPARATOR).map { event ->
            runningBalance += if (event.type == "income") event.amount_cents else -event.amount_cents
            BalanceForecastRow(
                date = event.date,
                balanceCents = runningBalance,
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
