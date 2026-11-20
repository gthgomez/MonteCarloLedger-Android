package com.montecarlo.ledger.adoption

import com.montecarlo.ledger.contract.ContractDebt
import com.montecarlo.ledger.contract.ContractEngine
import com.montecarlo.ledger.contract.ContractEvent
import com.montecarlo.ledger.contract.ContractMoney
import com.montecarlo.ledger.contract.ContractRecurrence
import com.montecarlo.ledger.contract.ContractRecurrenceExclusion
import com.montecarlo.ledger.contract.ContractScenario
import com.montecarlo.ledger.contract.ContractSimulationParams
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.processing.MonteCarloCalibration
import com.montecarlo.ledger.processing.RecurrenceMath
import com.montecarlo.ledger.processing.ForecastEvent
import com.montecarlo.ledger.sync.CanonicalVaultMapper
import com.montecarlo.ledger.util.LedgerDate
import java.time.LocalDate
import java.time.YearMonth
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

    /**
     * Contract revision these scenarios declare.
     *
     * MCD-0027: product-built scenarios stamp the latest released version, exactly like the Python
     * and TypeScript builders. Declaring 1.2 here made an Android scenario carry different version
     * metadata from the identical scenario built elsewhere.
     */
    private const val CONTRACT_VERSION = "2.0"
    // Other bills use the contract's default expense sequence of 1.
    private const val DEBT_MINIMUM_SEQUENCE = 2

    data class Inputs(
        val startingBalanceCents: Long,
        val asOf: LocalDate,
        val incomes: List<IncomeEntity>,
        val payments: List<PaymentEntity>,
        val billOccurrences: List<BillOccurrenceEntity> = emptyList(),
        val simulation: ContractSimulationParams? = null,
        val horizonDays: Int = 90,
        val scenarioId: String = "dashboard",
        /** Resolved bill categories (contract 1.2): payment id -> category. */
        val paymentCategories: Map<Int, String> = emptyMap(),
        val debts: List<DebtEntity> = emptyList(),
    )

    /** Builds the canonical scenario. Callers pass `asOf` from the UI/ViewModel clock boundary. */
    fun build(inputs: Inputs): ContractScenario {
        val endExclusive = inputs.asOf.plusDays(inputs.horizonDays.toLong())
        val activePayments = inputs.payments.filter { it.is_active != 0 }
        val paymentById = activePayments.associateBy { it.id }
        val suppressed = suppressedDates(inputs.billOccurrences)
        val representedPayments = representedPaymentMonths(inputs, activePayments, endExclusive, suppressed)

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
                    category = inputs.paymentCategories[payment.id],
                )
            )
        }
        explicit.addAll(debtMinimumEvents(inputs, representedPayments, endExclusive))

        val recurrences = ArrayList<ContractRecurrence>()
        val exclusions = ArrayList<ContractRecurrenceExclusion>()
        inputs.incomes.forEach { income ->
            recurrenceForIncome(income, inputs.asOf, endExclusive)?.let(recurrences::add)
        }
        activePayments.forEach { payment ->
            val category = inputs.paymentCategories[payment.id]
            buildPaymentRecurrence(payment, inputs.asOf, endExclusive, suppressed, category)?.let {
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
     * Debt cash events in the same form used by the contract scenario, for native dashboard
     * summaries and widgets. This keeps the no-contract path from silently dropping obligations.
     */
    fun debtMinimumForecastEvents(inputs: Inputs): List<ForecastEvent> {
        val endExclusive = inputs.asOf.plusDays(inputs.horizonDays.toLong())
        val activePayments = inputs.payments.filter { it.is_active != 0 }
        val representedPayments = representedPaymentMonths(
            inputs,
            activePayments,
            endExclusive,
            suppressedDates(inputs.billOccurrences),
        )
        return debtMinimumEvents(inputs, representedPayments, endExclusive).map { event ->
            ForecastEvent(
                date = event.date,
                description = "Debt payment: ${event.name}",
                amount_cents = -event.amountCents,
                type = "bill",
                recurrenceLabel = "Debt minimum",
            )
        }
    }

    private fun representedPaymentMonths(
        inputs: Inputs,
        activePayments: List<PaymentEntity>,
        endExclusive: LocalDate,
        suppressed: Set<Pair<Int, String>>,
    ): Map<Int, Set<YearMonth>> {
        val monthsByPayment = mutableMapOf<Int, MutableSet<YearMonth>>()
        activePayments.forEach { payment ->
            val recurrence = buildPaymentRecurrence(
                payment = payment,
                asOf = inputs.asOf,
                endExclusive = endExclusive,
                suppressed = suppressed,
                category = inputs.paymentCategories[payment.id],
            }
            if (recurrence != null) {
                ContractEngine.generateOccurrences(recurrence.recurrence, endExclusive)
                    .filter { !it.isBefore(inputs.asOf) }
                    .forEach { date -> monthsByPayment.getOrPut(payment.id) { mutableSetOf() }.add(YearMonth.from(date)) }
            }
        }
        inputs.billOccurrences.forEach { occurrence ->
            val payment = activePayments.firstOrNull { it.id == occurrence.payment_id }
                ?.takeIf { it.amount_cents > 0L } ?: return@forEach
            val original = LedgerDate.parseIsoOrNull(occurrence.original_due_date ?: occurrence.due_date)
                ?: return@forEach
            if (original.isBefore(inputs.asOf) || !original.isBefore(endExclusive)) return@forEach
            val months = monthsByPayment.getOrPut(payment.id) { mutableSetOf() }
            if (occurrence.is_paid != 0) months.add(YearMonth.from(original))
            if (occurrence.is_paid == 0 && occurrence.is_user_modified != 0) {
                val moved = LedgerDate.parseIsoOrNull(occurrence.due_date) ?: return@forEach
                if (!moved.isBefore(inputs.asOf) && moved.isBefore(endExclusive) && occurrence.amount_cents > 0L) {
                    months.add(YearMonth.from(original))
                    months.add(YearMonth.from(moved))
                }
            }
        }
        return monthsByPayment.mapValues { (_, months) -> months.toSet() }
    }

    /**
     * Turns debt minimums not already represented by a linked bill in that month into cash events.
     * The opening debt balance estimates the first minimum.
     * Later months accrue interest on the remaining balance before computing their minimum.
     */
    private fun debtMinimumEvents(
        inputs: Inputs,
        representedPaymentMonths: Map<Int, Set<YearMonth>>,
        endExclusive: LocalDate,
    ): List<ContractEvent> {
        val debts = inputs.debts
        val liabilities = CanonicalVaultMapper.liabilitiesFromDebts(debts).sortedBy { it.id }
        val linkedPaymentByDebtId = debts.associate { it.id.toString() to it.linkedPaymentId }
        ContractDebt.validateLiabilities(liabilities)
        val events = ArrayList<ContractEvent>()
        for (liability in liabilities) {
            var month = YearMonth.from(inputs.asOf)
            var first = true
            var balance = liability.balanceCents
            while (balance > 0L) {
                val due = month.atDay(minOf(liability.dueDayOfMonth, month.lengthOfMonth()))
                if (!due.isBefore(endExclusive)) break
                month = month.plusMonths(1)
                if (due.isBefore(inputs.asOf)) continue

                if (!first) {
                    val interest = ContractMoney.toLong(
                        ContractDebt.monthlyInterest(balance, liability.aprBasisPoints),
                    )
                    balance = ContractMoney.checkedAdd(balance, interest)
                }
                first = false
                val minimum = ContractDebt.minimumPaymentCents(liability, balance)
                balance -= minimum
                val linkedBillCoversMonth = linkedPaymentByDebtId[liability.id]
                    ?.let { paymentId -> YearMonth.from(due) in representedPaymentMonths[paymentId].orEmpty() }
                    ?: false
                if (minimum > 0L && !linkedBillCoversMonth) {
                    events.add(
                        ContractEvent(
                            id = "debt-${liability.id}-$due",
                            name = liability.name,
                            date = due,
                            amountCents = -minimum,
                            type = "expense",
                            sequence = DEBT_MINIMUM_SEQUENCE,
                            inputIndex = events.size,
                        ),
                    )
                }
            }
        }
        return events
    }

    /**
     * Maps a [MonteCarloCalibration] onto the contract's simulation block.
     *
     * Contract 1.2 (MCD-0025) represents per-category expense variation; the calibrator's ranges
     * are carried through, with the scalar range as the fallback for uncategorized bills.
     */
    fun simulationParams(
        calibration: MonteCarloCalibration,
        runs: Int = CanonicalSimulationConfig.RUNS,
        seed: Long = CanonicalSimulationConfig.SEED,
        reserveCents: Long = 0,
    ): ContractSimulationParams = ContractSimulationParams(
        runs = runs,
        seed = seed,
        incomeVariationMin = calibration.incomeVariationMin,
        incomeVariationMax = calibration.incomeVariationMax,
        expenseVariationMin = calibration.expenseVariationMin,
        expenseVariationMax = calibration.expenseVariationMax,
        expenseCategoryVariation = calibration.expenseCategoryVariation,
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
        category: String?,
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
            category = category,
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
