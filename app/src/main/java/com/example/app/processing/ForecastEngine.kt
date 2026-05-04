package com.example.app.processing

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max

data class BalanceForecastRow(
    val date: LocalDate,
    val balanceCents: Int,
)

data class ForecastSummary(
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

        for (event in events.sortedWith(compareBy({ it.date }, { if (it.type == "income") 0 else 1 }))) {
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
            .sortedWith(compareBy({ it.date }, { if (it.type == "income") 0 else 1 }))
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

    fun calculateIncomeContribution(balanceCents: Int, events: List<ForecastEvent>): Int {
        var runningProjectedBalance = balanceCents
        var lowestProjectedBalance = balanceCents

        for (event in events.sortedWith(compareBy({ it.date }, { if (it.type == "income") 0 else 1 }))) {
            runningProjectedBalance += if (event.type == "income") event.amount_cents else -event.amount_cents
            if (runningProjectedBalance < lowestProjectedBalance) {
                lowestProjectedBalance = runningProjectedBalance
            }
        }

        var runningCash = balanceCents
        var minCash = balanceCents
        for (event in events.sortedWith(compareBy({ it.date }, { if (it.type == "income") 0 else 1 }))) {
            if (event.type == "bill") {
                runningCash -= event.amount_cents
            }
            if (runningCash < minCash) minCash = runningCash
        }

        val safeToSpend = max(0, lowestProjectedBalance)
        val currentCashContribution = max(0, minCash)

        return max(0, safeToSpend - currentCashContribution)
    }

    fun buildBalanceForecast(balanceCents: Int, events: List<ForecastEvent>): List<BalanceForecastRow> {
        var runningBalance = balanceCents
        return events.sortedWith(compareBy({ it.date }, { if (it.type == "income") 0 else 1 })).map { event ->
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
