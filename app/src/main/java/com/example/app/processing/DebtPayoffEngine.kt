package com.example.app.processing

import com.example.app.util.centsToDisplay
import java.time.LocalDate

enum class PayoffStrategy {
    SNOWBALL, // Lowest balance first
    AVALANCHE // Highest APR first
}

data class DebtItem(
    val id: Long,
    val name: String,
    val balanceCents: Long,
    val aprPercent: Double,
    val minPaymentCents: Long,
)

data class MonthlyPayoffStep(
    val monthNumber: Int,
    val date: LocalDate,
    val debtId: Long,
    val debtName: String,
    val startingBalanceCents: Long,
    val paymentCents: Long,
    val interestCents: Long,
    val principalCents: Long,
    val endingBalanceCents: Long,
)

data class DebtPayoffSummary(
    val strategy: PayoffStrategy,
    val monthsToPayoff: Int,
    val totalInterestCents: Long,
    val totalPaidCents: Long,
    val payoffDate: LocalDate,
    val monthlySchedule: List<MonthlyPayoffStep>,
)

data class DebtSimulationResult(
    val strategy: PayoffStrategy,
    val extraMonthlyPaymentCents: Long,
    val baselineSummary: DebtPayoffSummary,
    val acceleratedSummary: DebtPayoffSummary,
    val monthsSaved: Int,
    val interestSavedCents: Long,
    val causesOverdraft: Boolean,
    val overdraftDate: LocalDate?,
    val overdraftShortfallCents: Long,
    val warningMessage: String?,
)

object DebtPayoffEngine {

    fun runSimulation(
        debts: List<DebtItem>,
        extraMonthlyPaymentCents: Long,
        strategy: PayoffStrategy,
        currentBalanceCents: Long,
        forecastEvents: List<ForecastEvent>,
        today: LocalDate = LocalDate.now(),
    ): DebtSimulationResult {
        val baseline = simulateSchedule(debts, 0L, strategy, today)
        val accelerated = simulateSchedule(debts, extraMonthlyPaymentCents, strategy, today)

        val monthsSaved = maxOf(0, baseline.monthsToPayoff - accelerated.monthsToPayoff)
        val interestSaved = maxOf(0L, baseline.totalInterestCents - accelerated.totalInterestCents)

        // Cash Flow Safety Guard: Simulate adding extra monthly payment to cash flow forecast timeline
        var causesOverdraft = false
        var overdraftDate: LocalDate? = null
        var shortfallCents = 0L
        var warningMessage: String? = null

        if (extraMonthlyPaymentCents > 0L && debts.isNotEmpty()) {
            val updatedEvents = forecastEvents.toMutableList()
            var currentMonth = today.withDayOfMonth(1).plusMonths(1)
            val endDate = today.plusDays(90)

            while (!currentMonth.isAfter(endDate)) {
                updatedEvents.add(
                    ForecastEvent(
                        date = currentMonth,
                        description = "Extra Debt Payment",
                        amount_cents = extraMonthlyPaymentCents.coerceAtLeast(0L),
                        type = "expense",
                    )
                )
                currentMonth = currentMonth.plusMonths(1)
            }

            val forecastSummary = ForecastEngine.calculateForecastSummary(
                currentBalanceCents.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt(),
                updatedEvents,
            )

            if (forecastSummary.lowestBalanceCents < 0) {
                causesOverdraft = true
                overdraftDate = forecastSummary.firstNegativeDate ?: today
                shortfallCents = (-forecastSummary.lowestBalanceCents).toLong()
                val shortfallDisplay = centsToDisplay(shortfallCents.toInt())
                val extraDisplay = centsToDisplay(extraMonthlyPaymentCents.toInt())
                warningMessage = "Extra payment of $extraDisplay risks an overdraft shortfall of $shortfallDisplay on $overdraftDate."
            }
        }

        return DebtSimulationResult(
            strategy = strategy,
            extraMonthlyPaymentCents = extraMonthlyPaymentCents,
            baselineSummary = baseline,
            acceleratedSummary = accelerated,
            monthsSaved = monthsSaved,
            interestSavedCents = interestSaved,
            causesOverdraft = causesOverdraft,
            overdraftDate = overdraftDate,
            overdraftShortfallCents = shortfallCents,
            warningMessage = warningMessage,
        )
    }

    fun simulateSchedule(
        debts: List<DebtItem>,
        extraMonthlyPaymentCents: Long,
        strategy: PayoffStrategy,
        startDate: LocalDate,
    ): DebtPayoffSummary {
        if (debts.isEmpty()) {
            return DebtPayoffSummary(
                strategy = strategy,
                monthsToPayoff = 0,
                totalInterestCents = 0L,
                totalPaidCents = 0L,
                payoffDate = startDate,
                monthlySchedule = emptyList(),
            )
        }

        data class DebtState(
            val item: DebtItem,
            var currentBalanceCents: Long,
        )

        val debtStates = debts.map { DebtState(it, it.balanceCents) }.toMutableList()
        val schedule = mutableListOf<MonthlyPayoffStep>()
        var monthCount = 0
        var totalInterestPaidCents = 0L
        var totalPaidCents = 0L
        var currentDate = startDate

        val maxMonths = 360 // 30-year safety cap

        while (debtStates.any { it.currentBalanceCents > 0L } && monthCount < maxMonths) {
            monthCount++
            currentDate = currentDate.plusMonths(1)

            // 1. Sort active debts by strategy
            val activeDebts = debtStates.filter { it.currentBalanceCents > 0L }
            val targetOrder = when (strategy) {
                PayoffStrategy.SNOWBALL -> activeDebts.sortedBy { it.currentBalanceCents }
                PayoffStrategy.AVALANCHE -> activeDebts.sortedByDescending { it.item.aprPercent }
            }

            var extraPool = extraMonthlyPaymentCents

            // 2. Accrue interest & apply minimum payments
            for (debt in targetOrder) {
                val monthlyRate = (debt.item.aprPercent / 100.0) / 12.0
                val interestCents = (debt.currentBalanceCents * monthlyRate).toLong()
                debt.currentBalanceCents += interestCents
                totalInterestPaidCents += interestCents

                val minPayment = minOf(debt.item.minPaymentCents, debt.currentBalanceCents)
                val principal = minPayment - minOf(minPayment, interestCents)
                debt.currentBalanceCents -= minPayment
                totalPaidCents += minPayment

                schedule.add(
                    MonthlyPayoffStep(
                        monthNumber = monthCount,
                        date = currentDate,
                        debtId = debt.item.id,
                        debtName = debt.item.name,
                        startingBalanceCents = debt.currentBalanceCents + minPayment - interestCents,
                        paymentCents = minPayment,
                        interestCents = interestCents,
                        principalCents = principal,
                        endingBalanceCents = debt.currentBalanceCents,
                    )
                )
            }

            // 3. Apply extra payment pool to primary target debt
            for (debt in targetOrder) {
                if (extraPool <= 0L) break
                if (debt.currentBalanceCents <= 0L) continue

                val extraPayment = minOf(extraPool, debt.currentBalanceCents)
                debt.currentBalanceCents -= extraPayment
                extraPool -= extraPayment
                totalPaidCents += extraPayment
            }
        }

        return DebtPayoffSummary(
            strategy = strategy,
            monthsToPayoff = monthCount,
            totalInterestCents = totalInterestPaidCents,
            totalPaidCents = totalPaidCents,
            payoffDate = currentDate,
            monthlySchedule = schedule,
        )
    }
}
