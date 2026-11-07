package com.montecarlo.ledger.scenario

import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractEngine
import com.montecarlo.ledger.contract.ContractEvent
import com.montecarlo.ledger.contract.ContractMoney
import com.montecarlo.ledger.contract.ContractRecurrenceExclusion
import com.montecarlo.ledger.contract.ContractResult
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.contract.ContractScenario
import com.montecarlo.ledger.contract.ContractSimulationParams
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Headline contract conclusions for one scenario, in contract terms only (MC-06b / WS6).
 *
 * Every field is copied straight out of a canonical [ContractResult]; nothing here is recomputed.
 * `safeToSpendCents` and `probabilityNegativePpm` are null when the scenario carried no simulation
 * block, so a caller can never mistake "no risk model ran" for "zero risk".
 */
data class ScenarioHeadline(
    /** `risk.safe_to_spend_cents` — the quantile-based safe-to-spend (MCD-0008); null without a simulation. */
    val safeToSpendCents: Long?,
    /** `forecast.minimum_balance_cents` — the deterministic projected low point. */
    val projectedLowPointCents: Long,
    /** `forecast.minimum_balance_date`. */
    val projectedLowPointDate: LocalDate,
    /** `risk.negative_balance_probability_ppm`; null without a simulation. */
    val probabilityNegativePpm: Long?,
    /** `forecast.first_negative_date`; null when the projection never goes negative. */
    val firstNegativeDate: LocalDate?,
)

/**
 * Modified minus baseline for each headline conclusion.
 *
 * A positive `firstNegativeDateShiftDays` means the modified scenario first goes negative *later*
 * (the same date as the baseline is `0`; earlier is negative). It is null unless both scenarios
 * have a first-negative date, so "appeared" and "disappeared" are never encoded as a fake zero.
 */
data class ScenarioHeadlineDelta(
    val safeToSpendCents: Long?,
    val projectedLowPointCents: Long,
    val probabilityNegativePpm: Long?,
    val firstNegativeDateShiftDays: Long?,
)

/** Baseline vs. baseline-plus-one-purchase, mirroring Python `decisions.simulate_purchase`. */
data class PurchaseSimulation(
    /** The purchase magnitude in cents, always the positive magnitude that was injected. */
    val amountCents: Long,
    val purchaseDate: LocalDate,
    val baseline: ScenarioHeadline,
    val withPurchase: ScenarioHeadline,
    val change: ScenarioHeadlineDelta,
)

/** Baseline vs. next-paycheck-arrives-late, mirroring Python `decisions.simulate_paycheck_delay`. */
data class PaycheckDelaySimulation(
    val delayDays: Int,
    val baseline: ScenarioHeadline,
    val delayed: ScenarioHeadline,
    val change: ScenarioHeadlineDelta,
)

/**
 * Goal-oriented "afford this?" / "what if my paycheck is late?" comparisons (WS6, P2).
 *
 * These are the Kotlin counterparts of Python `monte_carlo_ledger/decisions.py`'
 * `simulate_purchase` / `simulate_paycheck_delay`, and they follow the same division of labour
 * (MCD-0006..0010): this object builds the *current* scenario with
 * [ContractScenarioBridge], expresses the hypothetical as a canonical scenario mutation, and then
 * delegates every financial conclusion to [ContractRunner]. There is deliberately no forecast,
 * probability, or safe-to-spend arithmetic in this file — only the subtraction of two canonical
 * results into [ScenarioHeadlineDelta].
 *
 * Python parity, operation by operation:
 *
 *  - `simulate_purchase` appends one explicit expense event
 *    `{"date": d, "amount_cents": -abs(amount), "type": "expense", "name": "Planned purchase"}` to
 *    a copy of the materialized scenario and forces the scalar expense variation range to zero in
 *    the shared simulation params (`setdefault("expense_variation_min", 0)` /
 *    `expense_variation_max`). The variation is forced off precisely so the injected purchase
 *    consumes no RNG draw, which keeps the two runs on one draw stream and makes the delta
 *    attributable to the purchase alone. [applyPurchase] mirrors both halves: the event is appended
 *    with `sequence = null` (contract default `1` for an expense) and `inputIndex = events.size`,
 *    exactly the position/lower tie-break the Python event gets when it is appended after the
 *    existing events, and the scalar expense range is zeroed on both runs.
 *    Documented deviation: contract 1.2 per-category variation is *not* cleared, because the
 *    injected purchase carries no category and therefore still consumes no draw; the category draws
 *    taken by other events are identical and identically ordered in both runs, so they cancel in
 *    the delta. Clearing them would change the baseline away from the scenario the dashboard shows.
 *
 *  - `simulate_paycheck_delay` shifts the date of the *earliest materialized income event* later by
 *    N days and leaves everything else alone (Python `_shift_first_income`); it does not touch the
 *    simulation params. [applyPaycheckDelay] mirrors that at the contract level: the occurrence the
 *    income recurrence would otherwise generate at the original date is suppressed with a contract
 *    1.1 `occurrence_exclusions` entry (MCD-0024) and a single explicit income event carries the
 *    shifted date, so exactly one paycheck moves and the rest of the schedule keeps its anchor.
 *    Python shifts a materialized event to an arbitrary date, so a shift that leaves the window
 *    removes the paycheck from both implementations (out-of-window events are not projected).
 *    Documented edge case: with two incomes starting on the same date, Python's stable sort keeps
 *    list order while this implementation breaks the tie on `input_index` — both pick "the first
 *    one", and the choice is only observable when both incomes land on the same day.
 */
object ScenarioOps {

    /** Canonical name of the hypothetical purchase event (identical to Python's). */
    const val PURCHASE_EVENT_NAME = "Planned purchase"

    /**
     * "Can I afford this purchase?": compares the current scenario with the same scenario plus one
     * dated expense of [amountCents] (a positive magnitude — the sign is applied here).
     *
     * @param inputs the current scenario's bridge inputs; the baseline is built with
     *   [ContractScenarioBridge.build] so the comparison starts from exactly the canonical scenario
     *   the rest of the app runs.
     */
    fun simulatePurchase(
        amountCents: Long,
        date: LocalDate,
        inputs: ContractScenarioBridge.Inputs,
    ): PurchaseSimulation = simulatePurchase(amountCents, date, ContractScenarioBridge.build(inputs))

    /** As above, for a caller that already holds the canonical baseline scenario. */
    fun simulatePurchase(
        amountCents: Long,
        date: LocalDate,
        baselineScenario: ContractScenario,
    ): PurchaseSimulation {
        val baseline = baselineScenario.copy(simulation = purchaseParams(baselineScenario.simulation))
        val withPurchaseScenario = applyPurchase(baseline, amountCents, date)
        val baselineHeadline = headline(ContractRunner.run(baseline))
        val withPurchaseHeadline = headline(ContractRunner.run(withPurchaseScenario))
        return PurchaseSimulation(
            amountCents = abs(amountCents),
            purchaseDate = date,
            baseline = baselineHeadline,
            withPurchase = withPurchaseHeadline,
            change = change(baselineHeadline, withPurchaseHeadline),
        )
    }

    /**
     * "What if my next paycheck is [days] late?": compares the current scenario with the same
     * scenario where the earliest in-window income lands [days] later.
     */
    fun simulatePaycheckDelay(
        days: Int,
        inputs: ContractScenarioBridge.Inputs,
    ): PaycheckDelaySimulation = simulatePaycheckDelay(days, ContractScenarioBridge.build(inputs))

    /** As above, for a caller that already holds the canonical baseline scenario. */
    fun simulatePaycheckDelay(days: Int, baselineScenario: ContractScenario): PaycheckDelaySimulation {
        val delayedScenario = applyPaycheckDelay(baselineScenario, days)
        val baselineHeadline = headline(ContractRunner.run(baselineScenario))
        val delayedHeadline = headline(ContractRunner.run(delayedScenario))
        return PaycheckDelaySimulation(
            delayDays = days,
            baseline = baselineHeadline,
            delayed = delayedHeadline,
            change = change(baselineHeadline, delayedHeadline),
        )
    }

    /**
     * The purchase mutation: append the dated expense event Python appends, and force the scalar
     * expense variation off so the extra event consumes no RNG draw in either run.
     */
    fun applyPurchase(
        scenario: ContractScenario,
        amountCents: Long,
        date: LocalDate,
    ): ContractScenario = scenario.copy(
        events = scenario.events + ContractEvent(
            // No id and no sequence: the contract type default (`1` for an expense) applies, and the
            // appended index is the tie-break the Python event receives when it is appended last.
            id = null,
            name = PURCHASE_EVENT_NAME,
            date = date,
            amountCents = -abs(amountCents),
            type = "expense",
            sequence = null,
            inputIndex = scenario.events.size,
        ),
        simulation = purchaseParams(scenario.simulation),
    )

    /**
     * The paycheck-delay mutation: move the earliest in-window income [days] later, leaving every
     * other occurrence (and the rest of the schedule's month anchor) untouched. Returns the scenario
     * unchanged when it has no in-window income.
     */
    fun applyPaycheckDelay(scenario: ContractScenario, days: Int): ContractScenario {
        if (days == 0) return scenario
        val firstIncome = ContractEngine.inWindowBaseEvents(scenario)
            .filter { it.type == "income" }
            .minWithOrNull(compareBy({ it.date }, { it.inputIndex }))
            ?: return scenario
        val shiftedDate = firstIncome.date.plusDays(days.toLong())
        val recurrenceId = scenario.recurrences.firstOrNull { it.id == firstIncome.id }?.id
        return if (recurrenceId != null) {
            scenario.copy(
                events = scenario.events + ContractEvent(
                    id = null,
                    name = firstIncome.name,
                    date = shiftedDate,
                    amountCents = firstIncome.amountCents,
                    type = "income",
                    sequence = null,
                    inputIndex = scenario.events.size,
                    category = firstIncome.category,
                ),
                // Suppress the occurrence the recurrence still generates at the original date so the
                // paycheck moves rather than duplicates (contract 1.1, MCD-0024).
                occurrenceExclusions = scenario.occurrenceExclusions +
                    ContractRecurrenceExclusion(recurrenceId = recurrenceId, date = firstIncome.date),
            )
        } else {
            // An income event supplied explicitly by the caller is moved in place.
            scenario.copy(
                events = scenario.events.mapIndexed { index, event ->
                    if (index == firstIncome.inputIndex) event.copy(date = shiftedDate) else event
                },
            )
        }
    }

    /** The simulation params shared by both runs of a purchase comparison. */
    internal fun purchaseParams(params: ContractSimulationParams?): ContractSimulationParams? =
        params?.copy(expenseVariationMin = 0, expenseVariationMax = 0)

    private fun headline(result: ContractResult) = ScenarioHeadline(
        safeToSpendCents = result.risk?.safeToSpendCents,
        projectedLowPointCents = result.forecast.minimumBalanceCents,
        projectedLowPointDate = result.forecast.minimumBalanceDate,
        probabilityNegativePpm = result.risk?.negativeBalanceProbabilityPpm,
        firstNegativeDate = result.forecast.firstNegativeDate,
    )

    private fun change(baseline: ScenarioHeadline, modified: ScenarioHeadline) = ScenarioHeadlineDelta(
        // Overflow-checked so a nonsensical pair is an explicit contract money error, not a wrap.
        safeToSpendCents = bothPresent(baseline.safeToSpendCents, modified.safeToSpendCents)
            ?.let { (a, b) -> ContractMoney.checkedSubtract(b, a) },
        projectedLowPointCents = ContractMoney.checkedSubtract(
            modified.projectedLowPointCents,
            baseline.projectedLowPointCents,
        ),
        probabilityNegativePpm = bothPresent(
            baseline.probabilityNegativePpm,
            modified.probabilityNegativePpm,
        )?.let { (a, b) -> ContractMoney.checkedSubtract(b, a) },
        firstNegativeDateShiftDays = bothPresent(baseline.firstNegativeDate, modified.firstNegativeDate)
            ?.let { (a, b) -> ChronoUnit.DAYS.between(a, b) },
    )

    private fun <T> bothPresent(a: T?, b: T?): Pair<T, T>? = if (a != null && b != null) Pair(a, b) else null
}
