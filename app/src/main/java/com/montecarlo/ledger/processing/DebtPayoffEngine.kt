package com.montecarlo.ledger.processing

import com.montecarlo.ledger.contract.ContractDebt
import com.montecarlo.ledger.contract.ContractEvent
import com.montecarlo.ledger.contract.ContractLiability
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.contract.ContractScenario
import com.montecarlo.ledger.data.DebtKind
import com.montecarlo.ledger.util.centsToDisplay
import java.time.LocalDate
import kotlin.math.abs

enum class PayoffStrategy {
    SNOWBALL, // Lowest balance first
    AVALANCHE // Highest APR first
}

data class DebtItem(
    val id: Long,
    val name: String,
    val balanceCents: Long,
    val aprBasisPoints: Int,
    val minPaymentCents: Long,
    val dueDayOfMonth: Int = 1,
    val linkedPaymentId: Int? = null,
    /** installment | revolving (credit-card style). */
    val kind: String = DebtKind.INSTALLMENT,
    /** Revolving: percent-of-balance minimum in basis points; 0 disables the percentage leg. */
    val minPaymentPercentBps: Int = 0,
    /** Revolving: flat floor for the computed minimum. */
    val minPaymentFloorCents: Long = 0L,
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
    val didNotConverge: Boolean = false,
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

/**
 * Product adapter over the canonical contract 2.0 debt domain (`contracts/debt.md`).
 *
 * Amortization and the minimum-payment formula both delegate to [ContractDebt]; the cash-flow
 * overdraft guard runs the canonical forecast ([ContractRunner]) rather than the native
 * [ForecastEngine]. The public surface ([DebtItem], [DebtPayoffSummary], [MonthlyPayoffStep],
 * [DebtSimulationResult], and every function below) is unchanged so the Compose screens keep working.
 */
object DebtPayoffEngine {

    /** The forecast horizon (days) used by the cash-flow overdraft guard. */
    private const val OVERDRAFT_HORIZON_DAYS = 90

    /**
     * Minimum due this month for [debt] at [balanceCents].
     *
     * Installment debts pay their fixed minimum. Revolving debts (credit cards) use
     * max(flat floor, percent of balance), and a card below its floor is simply paid
     * in full — matching how issuers actually compute statement minimums. Delegates to the
     * canonical formula in [ContractDebt.minimumPaymentCents].
     */
    fun minimumPaymentCents(debt: DebtItem, balanceCents: Long): Long =
        ContractDebt.minimumPaymentCents(debt.toContractLiability(), balanceCents)

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
            // Synthetic minimum-payment events are recognizable by description, so two
            // unlinked debts that share a minimum amount and due date never suppress
            // each other; only a real user bill on the timeline counts as a duplicate.
            val syntheticMinimumDescriptions = debts.map { "${it.name} minimum payment" }.toSet()
            debts.filter { it.linkedPaymentId == null }.forEach { debt ->
                val representativeMinimum = minimumPaymentCents(debt, debt.balanceCents)
                var dueDate = today.withDayOfMonth(debt.dueDayOfMonth.coerceAtMost(today.lengthOfMonth()))
                if (!dueDate.isAfter(today)) dueDate = dueDate.plusMonths(1)
                while (!dueDate.isAfter(today.plusDays(OVERDRAFT_HORIZON_DAYS.toLong()))) {
                    val alreadyOnTimeline = updatedEvents.any { event ->
                        event.date == dueDate &&
                            (event.type == "bill" || event.type == "expense") &&
                            event.amount_cents == representativeMinimum &&
                            event.description !in syntheticMinimumDescriptions
                    }
                    if (!alreadyOnTimeline) {
                        updatedEvents.add(
                            ForecastEvent(
                                date = dueDate,
                                description = "${debt.name} minimum payment",
                                amount_cents = representativeMinimum,
                                type = "expense",
                            ),
                        )
                    }
                    val nextMonth = dueDate.plusMonths(1)
                    dueDate = nextMonth.withDayOfMonth(debt.dueDayOfMonth.coerceAtMost(nextMonth.lengthOfMonth()))
                }
            }
            var extraDate = today
            val endDate = today.plusDays(OVERDRAFT_HORIZON_DAYS.toLong())

            while (!extraDate.isAfter(endDate)) {
                updatedEvents.add(
                    ForecastEvent(
                        date = extraDate,
                        description = "Extra Debt Payment",
                        amount_cents = extraMonthlyPaymentCents.coerceAtLeast(0L),
                        type = "expense",
                    )
                )
                extraDate = extraDate.plusMonths(1)
            }

            // Canonical forecast (contract 2.0): the synthetic plus real events are projected as a
            // scenario with `as_of = today`, and the low point / first-negative date drive the guard.
            val forecast = ContractRunner.run(overdraftGuardScenario(updatedEvents, currentBalanceCents, today)).forecast

            // MCD-0010 / bug B-05: an already-negative opening balance is negative from `today`,
            // so the guard fires even before any synthetic payment lands.
            if (forecast.minimumBalanceCents < 0L) {
                causesOverdraft = true
                overdraftDate = forecast.firstNegativeDate ?: today
                shortfallCents = -forecast.minimumBalanceCents
                val shortfallDisplay = centsToDisplay(shortfallCents)
                val extraDisplay = centsToDisplay(extraMonthlyPaymentCents)
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

    /**
     * Builds the canonical scenario used by the overdraft guard. Income events add their magnitude;
     * every other event (expense/bill) subtracts its magnitude — the same running-balance rule the
     * native forecast used. Liabilities/recurrences/simulation are intentionally omitted: the guard
     * only needs the deterministic 90-day forecast.
     */
    private fun overdraftGuardScenario(
        events: List<ForecastEvent>,
        currentBalanceCents: Long,
        today: LocalDate,
    ): ContractScenario {
        val contractEvents = events.mapIndexed { index, event ->
            val income = event.type == "income"
            ContractEvent(
                id = null,
                name = event.description,
                date = event.date,
                amountCents = if (income) abs(event.amount_cents) else -abs(event.amount_cents),
                type = if (income) "income" else "expense",
                sequence = null,
                inputIndex = index,
            )
        }
        return ContractScenario(
            scenarioId = "debt-overdraft-guard",
            asOf = today,
            startingBalanceCents = currentBalanceCents,
            horizonDays = OVERDRAFT_HORIZON_DAYS,
            events = contractEvents,
            recurrences = emptyList(),
            simulation = null,
        )
    }

    fun simulateSchedule(
        debts: List<DebtItem>,
        extraMonthlyPaymentCents: Long,
        strategy: PayoffStrategy,
        startDate: LocalDate,
    ): DebtPayoffSummary {
        val liabilities = debts.map { it.toContractLiability() }
        val result = ContractDebt.amortize(
            asOf = startDate,
            liabilities = liabilities,
            strategy = strategy.toContractStrategy(),
            extraMonthlyPaymentCents = extraMonthlyPaymentCents,
        )

        val byId = debts.associateBy { it.id.toString() }
        val schedule = result.schedule.map { step ->
            val debt = byId.getValue(step.liabilityId)
            MonthlyPayoffStep(
                monthNumber = step.month,
                date = step.date,
                debtId = debt.id,
                debtName = debt.name,
                startingBalanceCents = step.startingBalanceCents,
                paymentCents = step.paymentCents,
                interestCents = step.interestCents,
                principalCents = step.principalCents,
                endingBalanceCents = step.endingBalanceCents,
            )
        }

        return DebtPayoffSummary(
            strategy = result.strategy.toPayoffStrategy(),
            monthsToPayoff = result.monthsToPayoff,
            totalInterestCents = result.totalInterestCents,
            totalPaidCents = result.totalPaidCents,
            payoffDate = result.payoffDate,
            monthlySchedule = schedule,
            didNotConverge = result.didNotConverge,
        )
    }

    /** Maps a product liability onto the canonical contract liability (`contracts/debt.md`). */
    private fun DebtItem.toContractLiability(): ContractLiability = ContractLiability(
        id = id.toString(),
        name = name,
        balanceCents = balanceCents,
        aprBasisPoints = aprBasisPoints,
        minPaymentCents = minPaymentCents,
        kind = kind,
        minPaymentPercentBps = minPaymentPercentBps,
        minPaymentFloorCents = minPaymentFloorCents,
        dueDayOfMonth = dueDayOfMonth,
    )

    private fun PayoffStrategy.toContractStrategy(): String = when (this) {
        PayoffStrategy.SNOWBALL -> "snowball"
        PayoffStrategy.AVALANCHE -> "avalanche"
    }

    private fun String.toPayoffStrategy(): PayoffStrategy = when (this) {
        "avalanche" -> PayoffStrategy.AVALANCHE
        else -> PayoffStrategy.SNOWBALL
    }
}
