package com.montecarlo.ledger.adoption

import com.montecarlo.ledger.contract.ContractEngine
import com.montecarlo.ledger.contract.ContractEvent
import com.montecarlo.ledger.contract.ContractRecurrence
import com.montecarlo.ledger.contract.ContractRecurrenceExclusion
import com.montecarlo.ledger.contract.ContractScenario
import com.montecarlo.ledger.contract.ContractSimulationParams
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.processing.MonteCarloCalibration
import com.montecarlo.ledger.processing.RecurrenceMath
import com.montecarlo.ledger.util.LedgerDate
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bridges repository/DB state into a canonical [ContractScenario] for the Kotlin contract
 * engine (MC-06b, AD-1). This is the only place product rows are translated into contract
 * primitives, so the contract engine itself stays a pure, clock-free library.
 *
 * Semantics adopted from MonteCarlo Contract 1.0:
 *  - `as_of` is supplied explicitly by the caller; this bridge never reads a clock (MCD-0001).
 *  - `starting_balance_cents` is the resolved seed balance; posted transactions are already
 *    folded into it and are not replayed as events (MCD-0013).
 *  - Recurrence `start_date` is a lower bound and `anchor_day` sets the day of month; the
 *    contract engine projects from the original start, so a clamped month cannot move the
 *    anchor (MCD-0022 / bug B-07).
 *  - Occurrences dated before `as_of` are **not** projected (MCD-0014 / bug D-14). Paid or
 *    user-moved template dates are suppressed by contract 1.1 `occurrence_exclusions` (MCD-0024);
 *    user-moved unpaid occurrences become explicit events.
 *
 * Suppression (paid / user-moved occurrences) is expressed with contract 1.1
 * `occurrence_exclusions` (MCD-0024), so an occurrence anywhere in the window — not only a prefix —
 * can be removed. The recurrence keeps its original `start_date` so the month anchor stays exact;
 * individual suppressed occurrences are excluded by `(recurrence_id, date)`.
 */
object ContractScenarioBridge {

    /** Contract revision these scenarios declare (occurrence exclusions, MCD-0024). */
    private const val CONTRACT_VERSION = "1.1"

    data class Inputs(
        val startingBalanceCents: Long,
        val asOf: LocalDate,
        val incomes: List<IncomeEntity>,
        val payments: List<PaymentEntity>,
        val billOccurrences: List<BillOccurrenceEntity> = emptyList(),
        val simulation: ContractSimulationParams? = null,
        val horizonDays: Int = 90,
        val scenarioId: String = "dashboard",
    )

    /** Builds the canonical scenario. Callers pass `asOf` from the UI/ViewModel clock boundary. */
    fun build(inputs: Inputs): ContractScenario {
        val endExclusive = inputs.asOf.plusDays(inputs.horizonDays.toLong())
        val activePayments = inputs.payments.filter { it.is_active != 0 }
        val paymentById = activePayments.associateBy { it.id }
        val suppressed = suppressedDates(inputs.billOccurrences)

        val explicit = ArrayList<ContractEvent>()
        var inputIndex = 0

        // User-moved (unpaid, modified) occurrences are explicit events; their original
        // template date is suppressed below so it is not projected twice.
        for (occurrence in inputs.billOccurrences) {
            if (occurrence.is_paid != 0 || occurrence.is_user_modified == 0) continue
            val payment = paymentById[occurrence.payment_id] ?: continue
            val due = LedgerDate.parseIsoOrNull(occurrence.due_date) ?: continue
            if (due.isBefore(inputs.asOf) || !due.isBefore(endExclusive)) continue
            val amount = -abs(occurrence.amount_cents)
            if (amount == 0L) continue
            explicit.add(
                ContractEvent(
                    id = "occurrence-${occurrence.id}",
                    name = payment.name,
                    date = due,
                    amountCents = amount,
                    type = "expense",
                    sequence = null,
                    inputIndex = inputIndex++,
                )
            )
        }

        val recurrences = ArrayList<ContractRecurrence>()
        val exclusions = ArrayList<ContractRecurrenceExclusion>()
        inputs.incomes.forEach { income ->
            recurrenceForIncome(income, inputs.asOf, endExclusive)?.let(recurrences::add)
        }
        activePayments.forEach { payment ->
            buildPaymentRecurrence(payment, inputs.asOf, endExclusive, suppressed)?.let {
                recurrences.add(it.recurrence)
                exclusions.addAll(it.exclusions)
            }
        }

        return ContractScenario(
            scenarioId = inputs.scenarioId,
            asOf = inputs.asOf,
            startingBalanceCents = inputs.startingBalanceCents,
            horizonDays = inputs.horizonDays,
            events = explicit,
            recurrences = recurrences,
            simulation = inputs.simulation,
            contractVersion = CONTRACT_VERSION,
            occurrenceExclusions = exclusions,
        )
    }

    /**
     * Maps a [MonteCarloCalibration] onto the contract's simulation block.
     *
     * Contract 1.0 models a single aggregate expense variation scalar; per-category variation
     * is a deferred 2.0 candidate (MCD-0015) and is intentionally not representable here.
     */
    fun simulationParams(
        calibration: MonteCarloCalibration,
        runs: Int = 500,
        seed: Long = 42,
        reserveCents: Long = 0,
    ): ContractSimulationParams = ContractSimulationParams(
        runs = runs,
        seed = seed,
        incomeVariationMin = calibration.incomeVariationMin,
        incomeVariationMax = calibration.incomeVariationMax,
        expenseVariationMin = calibration.expenseVariationMin,
        expenseVariationMax = calibration.expenseVariationMax,
        surpriseProbabilityPpm = (calibration.surpriseProbability * 1_000_000.0).roundToInt().coerceIn(0, 1_000_000),
        surpriseAmountMin = calibration.surpriseAmountMin,
        surpriseAmountMax = calibration.surpriseAmountMax,
        quantileNum = 1,
        quantileDen = 10,
        reserveCents = reserveCents,
    )

    private fun recurrenceForIncome(
        income: IncomeEntity,
        asOf: LocalDate,
        endExclusive: LocalDate,
    ): ContractRecurrence? {
        val start = LedgerDate.parseIsoOrNull(income.next_date) ?: return null
        if (income.amount_cents <= 0L) return null
        val frequency = contractFrequency(income.frequency) ?: return null
        val recurrence = ContractRecurrence(
            id = "income-${income.id}",
            name = income.name,
            type = "income",
            amountCents = income.amount_cents,
            frequency = frequency,
            startDate = start,
            anchorDay = income.day_of_month ?: start.dayOfMonth,
            endDate = null,
            expectedAmountCents = income.expectedAmountCents,
        )
        // Drop recurrences that never land inside the half-open window. Overdue occurrences
        // are intentionally excluded rather than hoisted to `as_of` (MCD-0014).
        val dates = ContractEngine.generateOccurrences(recurrence, endExclusive)
        return recurrence.takeIf { dates.any { !it.isBefore(asOf) } }
    }

    private data class PaymentRecurrence(
        val recurrence: ContractRecurrence,
        val exclusions: List<ContractRecurrenceExclusion>,
    )

    private fun buildPaymentRecurrence(
        payment: PaymentEntity,
        asOf: LocalDate,
        endExclusive: LocalDate,
        suppressed: Set<Pair<Int, String>>,
    ): PaymentRecurrence? {
        val start = LedgerDate.parseIsoOrNull(payment.next_date) ?: return null
        val amount = -abs(payment.amount_cents)
        if (amount >= 0L) return null
        val frequency = contractFrequency(payment.frequency) ?: return null
        val recurrence = ContractRecurrence(
            id = "payment-${payment.id}",
            name = payment.name,
            type = "expense",
            amountCents = amount,
            frequency = frequency,
            startDate = start,
            anchorDay = payment.day_of_month ?: start.dayOfMonth,
            endDate = null,
            expectedAmountCents = null,
        )
        val inWindow = ContractEngine.generateOccurrences(recurrence, endExclusive)
            .filter { !it.isBefore(asOf) }
        // Keep the original start date (so `anchor_day` stays exact) and suppress individual
        // occurrences with exclusions rather than advancing the lower bound, so a suppression in
        // the MIDDLE of the window is representable (contract 1.1, MCD-0024 / MC-07 C5).
        val exclusions = inWindow
            .filter { date -> Pair(payment.id, date.toString()) in suppressed }
            .map { date -> ContractRecurrenceExclusion(recurrenceId = recurrence.id, date = date) }
        if (exclusions.size >= inWindow.size) return null
        return PaymentRecurrence(recurrence = recurrence, exclusions = exclusions)
    }

    /** Mirrors [com.montecarlo.ledger.processing.TimelineService] paid/skipped/moved suppression. */
    private fun suppressedDates(occurrences: List<BillOccurrenceEntity>): Set<Pair<Int, String>> {
        val result = HashSet<Pair<Int, String>>()
        for (occurrence in occurrences) {
            if (occurrence.is_paid != 0) {
                result.add(Pair(occurrence.payment_id, occurrence.original_due_date ?: occurrence.due_date))
            }
            if (occurrence.is_user_modified != 0) {
                result.add(Pair(occurrence.payment_id, occurrence.original_due_date ?: occurrence.due_date))
                result.add(Pair(occurrence.payment_id, occurrence.due_date))
            }
        }
        return result
    }

    /** Product spellings -> contract frequency names. Unknown/unsupported values are dropped. */
    private fun contractFrequency(raw: String): String? = when (RecurrenceMath.normalizeFrequency(raw)) {
        "weekly" -> "weekly"
        "biweekly" -> "biweekly"
        "semimonthly" -> "semimonthly"
        "monthly" -> "monthly"
        "bimonthly" -> "bimonthly"
        "quarterly" -> "quarterly"
        "semiannual", "semiannually" -> "semiannually"
        "annual", "annually", "yearly" -> "annually"
        "onetime" -> "onetime"
        else -> null
    }
}
