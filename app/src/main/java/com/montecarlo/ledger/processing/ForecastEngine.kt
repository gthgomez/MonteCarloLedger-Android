package com.montecarlo.ledger.processing

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

internal val EVENT_COMPARATOR: Comparator<ForecastEvent> = compareBy<ForecastEvent> { it.date }
    .thenBy { if (it.type == "income") 0 else 1 }

data class BalanceForecastRow(
    val date: LocalDate,
    val balanceCents: Long,
)

data class ForecastSummary(
    /**
     * Deterministic minimum running balance across the window (MCD-0008 "projected low point").
     * Negative when the forecast goes into overdraft. This is **not** a spendable amount; a
     * spendable value must come from a quantile-based [safeToSpendCents].
     */
    val projectedLowPointCents: Long,
    val lowestBalanceDate: LocalDate?,
    val endingBalanceCents: Long,
    /**
     * `as_of` when the opening balance is already negative, otherwise the first row whose balance
     * dips below zero, or `null` when the projection never goes negative (MCD-0010 / bug B-05).
     */
    val firstNegativeDate: LocalDate?,
    /**
     * Quantile-based safe-to-spend (MCD-0008): the lower-tail trough percentile minus a reserve.
     * The native engine models no distribution, so this is `null` on that path; callers must fall
     * back to [projectedLowPointCents] (labeled as a low point) rather than call it spendable.
     */
    val safeToSpendCents: Long? = null,
)

data class CashFlowWindow(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val startingBalanceCents: Long,
    val incomeCents: Long,
    val billCents: Long,
    val lowestBalanceCents: Long,
    val endingBalanceCents: Long,
    val safeToSpendCents: Long,
    val dailySafeSpendCents: Long,
    val shortfallCents: Long,
    val days: Int,
)

object ForecastEngine {

    // Returns the minimum running balance across the event window — can be negative (overdraft signal).
    fun calculateSafeToSpend(balanceCents: Long, events: List<ForecastEvent>): Long {
        var runningBalance = balanceCents
        var lowestBalance = balanceCents

        for (event in events.sortedWith(EVENT_COMPARATOR)) {
            runningBalance += if (event.type == "income") event.amount_cents else -event.amount_cents
            lowestBalance = minOf(lowestBalance, runningBalance)
        }

        return lowestBalance
    }

    fun calculateDailySafeSpend(balanceCents: Long, events: List<ForecastEvent>, daysUntilPayday: Int): Long {
        val safeToSpend = calculateSafeToSpend(balanceCents, events)
        if (safeToSpend <= 0L) return 0L
        return if (daysUntilPayday > 0) safeToSpend / daysUntilPayday else safeToSpend
    }

    fun buildCashFlowWindows(
        balanceCents: Long,
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
            var incomeCents = 0L
            var billCents = 0L
            val openingIncomeEvents = windowEvents.filter { it.date == windowStart && it.type == "income" }
            openingIncomeEvents.forEach { event ->
                incomeCents += event.amount_cents
                runningBalance += event.amount_cents
            }
            // MCD-0010 / bug B-05: the window's opening balance is itself a low-point candidate.
            // Initialising from `runningBalance` ignored an already-negative opening balance
            // whenever a same-day paycheck moved the balance up first.
            var lowestBalance = startingBalance

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
            val safeToSpend = lowestBalance.coerceAtLeast(0L)
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
                shortfallCents = (-lowestBalance).coerceAtLeast(0L),
                days = days,
            )
        }
    }

    /**
     * Computes the worst-case buffer improvement that upcoming income events provide
     * over a bills-only scenario.
     */
    fun calculateIncomeContribution(balanceCents: Long, events: List<ForecastEvent>): Long {
        val sorted = events.sortedWith(EVENT_COMPARATOR)
        var runningProjected = balanceCents
        var runningCash = balanceCents
        var lowestProjected = balanceCents
        var minCash = balanceCents

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
        return maxOf(0L, contribution)
    }

    fun buildBalanceForecast(balanceCents: Long, events: List<ForecastEvent>): List<BalanceForecastRow> {
        var runningBalance = balanceCents
        return events.sortedWith(EVENT_COMPARATOR).map { event ->
            runningBalance += if (event.type == "income") event.amount_cents else -event.amount_cents
            BalanceForecastRow(
                date = event.date,
                balanceCents = runningBalance,
            )
        }
    }

    /**
     * Deterministic forecast summary. [asOf] is supplied explicitly by the caller and is never read
     * from a clock (MCD-0001); it anchors [ForecastSummary.firstNegativeDate] when the opening
     * balance is already negative (MCD-0010 / bug B-05).
     */
    fun calculateForecastSummary(
        balanceCents: Long,
        events: List<ForecastEvent>,
        asOf: LocalDate,
    ): ForecastSummary {
        val forecastRows = buildBalanceForecast(balanceCents, events)
        val endingBalance = forecastRows.lastOrNull()?.balanceCents ?: balanceCents

        var lowestBalance = balanceCents
        var lowestBalanceDate: LocalDate? = null
        // MCD-0010 / bug B-05: an already-negative opening balance is negative from `as_of`.
        var firstNegativeDate: LocalDate? = if (balanceCents < 0L) asOf else null

        for (row in forecastRows) {
            if (row.balanceCents < lowestBalance) {
                lowestBalance = row.balanceCents
                lowestBalanceDate = row.date
            }
            if (firstNegativeDate == null && row.balanceCents < 0L) {
                firstNegativeDate = row.date
            }
        }

        return ForecastSummary(
            projectedLowPointCents = lowestBalance,
            lowestBalanceDate = lowestBalanceDate,
            endingBalanceCents = endingBalance,
            firstNegativeDate = firstNegativeDate,
        )
    }
}
