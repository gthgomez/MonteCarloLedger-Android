package com.montecarlo.ledger.scenario

import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractEngine
import com.montecarlo.ledger.contract.ContractEvent
import com.montecarlo.ledger.contract.ContractRecurrenceExclusion
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.contract.ContractScenario
import com.montecarlo.ledger.contract.ContractSimulationParams
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.PaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * WS6: the "afford this purchase?" / "what if my paycheck is late?" comparisons.
 *
 * Two fixtures, both built the way the app builds a scenario (through
 * [ContractScenarioBridge], so recurrences — not a hand-written event list):
 *
 *  - `deterministicParams` — no variation and no surprises, so a run *is* the plain event path and
 *    every expected delta can be derived by hand.
 *  - `pythonDefaultParams` — the parameters Python's `decisions.DEFAULT_SIM` resolves to
 *    (income ±8%, expense 0%, surprises on), which exercises the RNG draw stream.
 *
 * Python parity is asserted two ways:
 *  1. the *scenario mutation* is pinned structurally ([applyPurchaseMirrorsThePythonDecisionsEvent],
 *     [paycheckDelayMovesExactlyTheEarliestIncomeOccurrence]); and
 *  2. the *numbers* for [pythonDefaultParams] are the ones the Python reference engine actually
 *     produced for the same financial inputs — see the harness and its output in
 *     `docs/ws6-scenario-ops.md`, quoted on
 *     [purchaseComparisonMatchesThePythonReferenceNumbers].
 */
class ScenarioOpsTest {

    private val asOf = LocalDate.of(2026, 1, 1)

    /** $2,000 starting balance, $1,500 rent due the 10th, $1,000 paycheck the 20th, 90 days. */
    private fun inputs(simulation: ContractSimulationParams?): ContractScenarioBridge.Inputs =
        ContractScenarioBridge.Inputs(
            startingBalanceCents = 200_000L,
            asOf = asOf,
            incomes = listOf(
                IncomeEntity(
                    id = 1,
                    name = "Paycheck",
                    amount_cents = 100_000L,
                    frequency = "Monthly",
                    day_of_month = 20,
                    next_date = "2026-01-20",
                )
            ),
            payments = listOf(
                PaymentEntity(
                    id = 1,
                    name = "Rent",
                    amount_cents = 150_000L,
                    frequency = "Monthly",
                    day_of_month = 10,
                    next_date = "2026-01-10",
                )
            ),
            simulation = simulation,
            horizonDays = 90,
        )

    private fun baseline(simulation: ContractSimulationParams?) = ContractScenarioBridge.build(inputs(simulation))

    /** Every run is the plain event path: no income/expense variation, no surprises. */
    private val deterministicParams = ContractSimulationParams(
        runs = 500,
        seed = 42,
        incomeVariationMin = 0,
        incomeVariationMax = 0,
        expenseVariationMin = 0,
        expenseVariationMax = 0,
        surpriseProbabilityPpm = 0,
    )

    /**
     * Python's `decisions.DEFAULT_SIM` ({"runs": 500, "seed": 42}) merged over the contract's
     * `SIMULATION_DEFAULTS`; [ContractSimulationParams] defaults are the same values.
     */
    private val pythonDefaultParams = ContractSimulationParams(runs = 500, seed = 42)

    /** A calibrated-style range that is NOT Python's default, used to pin the draw-alignment rule. */
    private val calibratedStyleParams = pythonDefaultParams.copy(
        expenseVariationMin = -10,
        expenseVariationMax = 10,
    )

    private val rentDates = listOf(
        LocalDate.of(2026, 1, 10),
        LocalDate.of(2026, 2, 10),
        LocalDate.of(2026, 3, 10),
    )

    // ── purchase ────────────────────────────────────────────────────────────────

    /**
     * The deterministic fixture, hand-derivable: 200000 → 50000 (1/10 rent) → 150000 (1/20 income)
     * → 0 (2/10) → 100000 (2/20) → -50000 (3/10) → 50000 (3/20). With $200 spent on 1/15 every
     * balance from 1/15 on is $200 lower, so the low point moves from -$500 to -$700 and the first
     * negative day moves from 3/10 to 2/10.
     */
    @Test
    fun purchaseComparisonReportsTheExactContractDeltas() {
        val simulation = ScenarioOps.simulatePurchase(20_000L, LocalDate.of(2026, 1, 15), inputs(deterministicParams))

        assertEquals(20_000L, simulation.amountCents)
        assertEquals(LocalDate.of(2026, 1, 15), simulation.purchaseDate)
        assertEquals(
            ScenarioHeadline(
                safeToSpendCents = -50_000L,
                projectedLowPointCents = -50_000L,
                projectedLowPointDate = LocalDate.of(2026, 3, 10),
                probabilityNegativePpm = 1_000_000L,
                firstNegativeDate = LocalDate.of(2026, 3, 10),
            ),
            simulation.baseline,
        )
        assertEquals(
            ScenarioHeadline(
                safeToSpendCents = -70_000L,
                projectedLowPointCents = -70_000L,
                projectedLowPointDate = LocalDate.of(2026, 3, 10),
                probabilityNegativePpm = 1_000_000L,
                firstNegativeDate = LocalDate.of(2026, 2, 10),
            ),
            simulation.withPurchase,
        )
        assertEquals(
            ScenarioHeadlineDelta(
                safeToSpendCents = -20_000L,
                projectedLowPointCents = -20_000L,
                probabilityNegativePpm = 0L,
                firstNegativeDateShiftDays = -28L,
            ),
            simulation.change,
        )
    }

    /**
     * The comparison reports the contract's own numbers: running the two scenarios separately (here
     * through the engine's *materialized* event form, the representation Python uses) reproduces the
     * [ScenarioOps.simulatePurchase] headlines and deltas exactly.
     */
    @Test
    fun purchaseComparisonEqualsTheDifferenceOfTwoCanonicalRuns() {
        val baselineScenario = baseline(pythonDefaultParams)
        val explicit = ContractEngine.inWindowBaseEvents(baselineScenario)
        val oracleBaseline = ContractScenario(
            scenarioId = baselineScenario.scenarioId,
            asOf = baselineScenario.asOf,
            startingBalanceCents = baselineScenario.startingBalanceCents,
            horizonDays = baselineScenario.horizonDays,
            events = explicit,
            recurrences = emptyList(),
            simulation = baselineScenario.simulation,
            contractVersion = "1.2",
        )
        val oracleWithPurchase = oracleBaseline.copy(
            events = oracleBaseline.events + ContractEvent(
                id = null,
                name = ScenarioOps.PURCHASE_EVENT_NAME,
                date = LocalDate.of(2026, 1, 15),
                amountCents = -20_000L,
                type = "expense",
                sequence = null,
                inputIndex = oracleBaseline.events.size,
            ),
        )

        val expectedBaseline = ContractRunner.run(oracleBaseline)
        val expectedWithPurchase = ContractRunner.run(oracleWithPurchase)
        val comparison = ScenarioOps.simulatePurchase(20_000L, LocalDate.of(2026, 1, 15), baselineScenario)

        assertEquals(expectedBaseline.forecast.minimumBalanceCents, comparison.baseline.projectedLowPointCents)
        assertEquals(expectedBaseline.forecast.minimumBalanceDate, comparison.baseline.projectedLowPointDate)
        assertEquals(expectedBaseline.forecast.firstNegativeDate, comparison.baseline.firstNegativeDate)
        assertEquals(expectedBaseline.risk!!.safeToSpendCents, comparison.baseline.safeToSpendCents)
        assertEquals(expectedBaseline.risk!!.negativeBalanceProbabilityPpm, comparison.baseline.probabilityNegativePpm)

        assertEquals(expectedWithPurchase.forecast.minimumBalanceCents, comparison.withPurchase.projectedLowPointCents)
        assertEquals(expectedWithPurchase.forecast.firstNegativeDate, comparison.withPurchase.firstNegativeDate)
        assertEquals(expectedWithPurchase.risk!!.safeToSpendCents, comparison.withPurchase.safeToSpendCents)
        assertEquals(
            expectedWithPurchase.risk!!.negativeBalanceProbabilityPpm,
            comparison.withPurchase.probabilityNegativePpm,
        )

        // Deltas are the difference of the two canonical results — nothing else.
        assertEquals(
            expectedWithPurchase.risk!!.safeToSpendCents - expectedBaseline.risk!!.safeToSpendCents,
            comparison.change.safeToSpendCents,
        )
        assertEquals(
            expectedWithPurchase.forecast.minimumBalanceCents - expectedBaseline.forecast.minimumBalanceCents,
            comparison.change.projectedLowPointCents,
        )
        assertEquals(
            expectedWithPurchase.risk!!.negativeBalanceProbabilityPpm - expectedBaseline.risk!!.negativeBalanceProbabilityPpm,
            comparison.change.probabilityNegativePpm,
        )
    }

    /** A purchase can only make the projection worse: lower low point, no less overdraft risk. */
    @Test
    fun purchaseComparisonMovesRiskInTheExpectedDirection() {
        val simulation = ScenarioOps.simulatePurchase(20_000L, LocalDate.of(2026, 1, 15), inputs(pythonDefaultParams))

        assertTrue(
            "low point should fall: ${simulation.change.projectedLowPointCents}",
            simulation.change.projectedLowPointCents < 0L,
        )
        assertTrue(
            "safe to spend should fall: ${simulation.change.safeToSpendCents}",
            (simulation.change.safeToSpendCents ?: 0L) < 0L,
        )
        assertTrue(
            "overdraft probability must not fall",
            (simulation.change.probabilityNegativePpm ?: 0L) >= 0L,
        )
        val shift = simulation.change.firstNegativeDateShiftDays
        assertTrue("first negative day must not move later: $shift", shift == null || shift <= 0L)
        assertTrue(
            "this fixture must actually reach a negative balance",
            (simulation.baseline.probabilityNegativePpm ?: 0L) > 0L,
        )
    }

    /**
     * The draw-alignment rule (Python's stated reason for forcing expense variation off): the
     * injected purchase consumes no RNG draw, so every simulated trough moves by exactly the expense.
     *
     * This fixture keeps a non-zero *scalar* expense range (as a calibrated ledger would), which the
     * comparison forces off for both runs — see the class doc of [ScenarioOps]. If the purchase
     * consumed a draw, every later expense draw would shift and the trough percentile could not move
     * by exactly -$200.
     */
    @Test
    fun purchaseDatedOnTheAsOfDayShiftsEverySimulatedTroughByExactlyTheAmount() {
        val simulation = ScenarioOps.simulatePurchase(
            20_000L,
            asOf,
            inputs(calibratedStyleParams),
        )

        assertEquals(-20_000L, simulation.change.projectedLowPointCents)
        assertEquals(-20_000L, simulation.change.safeToSpendCents)
        assertEquals(0L, simulation.change.probabilityNegativePpm)
        assertEquals(0L, simulation.baseline.probabilityNegativePpm!! - simulation.withPurchase.probabilityNegativePpm!!)
        assertEquals(
            simulation.baseline.safeToSpendCents!! - 20_000L,
            simulation.withPurchase.safeToSpendCents!!,
        )
    }

    /**
     * Scenario-mutation parity with Python `decisions.simulate_purchase`:
     * `scenario["events"].append({"date": d, "amount_cents": -abs(amount), "type": "expense",
     * "name": "Planned purchase"})` on a copy of the baseline, with the scalar expense variation
     * forced to zero so the injected event consumes no draw.
     */
    @Test
    fun applyPurchaseMirrorsThePythonDecisionsEvent() {
        val scenario = baseline(pythonDefaultParams)
        val modified = ScenarioOps.applyPurchase(scenario, 20_000L, LocalDate.of(2026, 1, 15))

        assertEquals(scenario.events.size + 1, modified.events.size)
        val injected = modified.events.last()
        assertEquals("Planned purchase", injected.name)
        assertEquals("expense", injected.type)
        assertEquals(LocalDate.of(2026, 1, 15), injected.date)
        assertEquals(-20_000L, injected.amountCents)
        // Python appends the event last, so it has no explicit sequence and the contract's default
        // expense sequence (1) applies; the appended index is the ordering tie-break.
        assertNull(injected.sequence)
        assertEquals(1, injected.effectiveSequence)
        assertEquals(scenario.events.size, injected.inputIndex)
        // Unrelated parts of the scenario are untouched.
        assertEquals(scenario.recurrences, modified.recurrences)
        assertEquals(scenario.startingBalanceCents, modified.startingBalanceCents)
        assertEquals(scenario.horizonDays, modified.horizonDays)
        assertEquals(scenario.contractVersion, modified.contractVersion)
        // The shared params keep everything except the scalar expense range, which is the forced-off
        // part of the Python semantics (`setdefault("expense_variation_min", 0)`).
        assertEquals(
            scenario.simulation!!.copy(expenseVariationMin = 0, expenseVariationMax = 0),
            modified.simulation,
        )
    }

    /** A negative or zero amount is still a purchase: the injected event is `-abs(amount)`. */
    @Test
    fun applyPurchaseAlwaysInjectsANegativeExpense() {
        val scenario = baseline(deterministicParams)

        assertEquals(
            -20_000L,
            ScenarioOps.applyPurchase(scenario, -20_000L, asOf).events.last().amountCents,
        )
    }

    /** Without a simulation block the comparison still reports the deterministic forecast. */
    @Test
    fun purchaseWithoutASimulationComparesTheForecastOnly() {
        val simulation = ScenarioOps.simulatePurchase(20_000L, LocalDate.of(2026, 1, 15), inputs(null))

        assertNull(simulation.baseline.safeToSpendCents)
        assertNull(simulation.baseline.probabilityNegativePpm)
        assertNull(simulation.change.safeToSpendCents)
        assertNull(simulation.change.probabilityNegativePpm)
        assertEquals(-50_000L, simulation.baseline.projectedLowPointCents)
        assertEquals(-70_000L, simulation.withPurchase.projectedLowPointCents)
        assertEquals(-20_000L, simulation.change.projectedLowPointCents)
        assertEquals(-28L, simulation.change.firstNegativeDateShiftDays)
    }

    /**
     * Cross-language parity: these are the numbers the Python reference engine produced for the same
     * financial inputs and the same post-mutation scenario. The harness (`docs/ws6-scenario-ops.md`,
     * "Python parity harness") loads the vendored reference `monte_carlo_ledger/contract/engine.py`
     * (contract 1.2) and runs it on the materialized fixture:
     *
     *   baseline      risk.minimum_balance_p10_cents = -70343, safe_to_spend_cents = -70343,
     *                 negative_balance_probability_ppm = 1000000,
     *                 forecast.minimum_balance_cents = -50000 @ 2026-03-10, first_negative 2026-03-10
     *   + purchase    safe_to_spend_cents = -90343, forecast.minimum_balance_cents = -70000,
     *                 first_negative 2026-02-10, change {p10: -20000, safe_to_spend: -20000, ppm: 0}
     *
     * The Android fixture reaches the same in-window events through the bridge's recurrences
     * (rent 1/10, 2/10, 3/10; paycheck 1/20, 2/20, 3/20) with no ordering ties, so the canonical
     * ordering — and therefore the RNG draw stream — is identical to Python's materialized event list.
     */
    @Test
    fun purchaseComparisonMatchesThePythonReferenceNumbers() {
        val simulation = ScenarioOps.simulatePurchase(20_000L, LocalDate.of(2026, 1, 15), inputs(pythonDefaultParams))

        assertEquals(
            ScenarioHeadline(
                safeToSpendCents = -70_343L,
                projectedLowPointCents = -50_000L,
                projectedLowPointDate = LocalDate.of(2026, 3, 10),
                probabilityNegativePpm = 1_000_000L,
                firstNegativeDate = LocalDate.of(2026, 3, 10),
            ),
            simulation.baseline,
        )
        assertEquals(
            ScenarioHeadline(
                safeToSpendCents = -90_343L,
                projectedLowPointCents = -70_000L,
                projectedLowPointDate = LocalDate.of(2026, 3, 10),
                probabilityNegativePpm = 1_000_000L,
                firstNegativeDate = LocalDate.of(2026, 2, 10),
            ),
            simulation.withPurchase,
        )
        assertEquals(
            ScenarioHeadlineDelta(
                safeToSpendCents = -20_000L,
                projectedLowPointCents = -20_000L,
                probabilityNegativePpm = 0L,
                firstNegativeDateShiftDays = -28L,
            ),
            simulation.change,
        )
    }

    // ── paycheck delay ──────────────────────────────────────────────────────────

    /**
     * Mutation parity with Python `decisions._shift_first_income`: exactly the earliest materialized
     * income event moves by N days; the rest of the schedule keeps its month anchor (so the later
     * paychecks stay on the 20th rather than sliding to the 25th).
     */
    @Test
    fun paycheckDelayMovesExactlyTheEarliestIncomeOccurrence() {
        val scenario = baseline(deterministicParams)
        val delayed = ScenarioOps.applyPaycheckDelay(scenario, 25)

        val events = ContractEngine.inWindowBaseEvents(delayed)
        assertEquals(
            listOf(LocalDate.of(2026, 2, 14), LocalDate.of(2026, 2, 20), LocalDate.of(2026, 3, 20)),
            events.filter { it.type == "income" }.map { it.date },
        )
        assertEquals(listOf(100_000L, 100_000L, 100_000L), events.filter { it.type == "income" }.map { it.amountCents })
        assertEquals(rentDates, events.filter { it.type == "expense" }.map { it.date })

        // The occurrence that would still be generated on the original date is suppressed, and the
        // recurrence itself (and its anchor) is untouched.
        assertEquals(
            listOf(ContractRecurrenceExclusion(recurrenceId = "income-1", date = LocalDate.of(2026, 1, 20))),
            delayed.occurrenceExclusions,
        )
        assertEquals(scenario.recurrences, delayed.recurrences)
        assertEquals(scenario.events.size + 1, delayed.events.size)
        assertEquals("income", delayed.events.last().type)
        assertEquals(LocalDate.of(2026, 2, 14), delayed.events.last().date)
    }

    /** Delay 25 days: the paycheck due 1/20 arrives 2/14, so the 2/10 rent is uncovered. */
    @Test
    fun paycheckDelayComparisonReportsTheExactContractDeltas() {
        val simulation = ScenarioOps.simulatePaycheckDelay(25, inputs(deterministicParams))

        assertEquals(25, simulation.delayDays)
        assertEquals(-50_000L, simulation.baseline.projectedLowPointCents)
        assertEquals(LocalDate.of(2026, 3, 10), simulation.baseline.firstNegativeDate)
        assertEquals(
            ScenarioHeadline(
                safeToSpendCents = -100_000L,
                projectedLowPointCents = -100_000L,
                projectedLowPointDate = LocalDate.of(2026, 2, 10),
                probabilityNegativePpm = 1_000_000L,
                firstNegativeDate = LocalDate.of(2026, 2, 10),
            ),
            simulation.delayed,
        )
        assertEquals(
            ScenarioHeadlineDelta(
                safeToSpendCents = -50_000L,
                projectedLowPointCents = -50_000L,
                probabilityNegativePpm = 0L,
                firstNegativeDateShiftDays = -28L,
            ),
            simulation.change,
        )
    }

    /** A zero-day delay is not a mutation at all: the same scenario runs twice. */
    @Test
    fun paycheckDelayOfZeroDaysChangesNothing() {
        val scenario = baseline(deterministicParams)

        val delayed = ScenarioOps.applyPaycheckDelay(scenario, 0)
        assertSame(scenario, delayed)

        val simulation = ScenarioOps.simulatePaycheckDelay(0, inputs(deterministicParams))
        assertEquals(simulation.baseline, simulation.delayed)
        assertEquals(
            ScenarioHeadlineDelta(
                safeToSpendCents = 0L,
                projectedLowPointCents = 0L,
                probabilityNegativePpm = 0L,
                firstNegativeDateShiftDays = 0L,
            ),
            simulation.change,
        )
    }

    /** With no in-window income there is nothing to delay, so the scenario is returned unchanged. */
    @Test
    fun paycheckDelayWithoutIncomeChangesNothing() {
        val scenario = ContractScenarioBridge.build(
            ContractScenarioBridge.Inputs(
                startingBalanceCents = 200_000L,
                asOf = asOf,
                incomes = emptyList(),
                payments = inputs(deterministicParams).payments,
                simulation = deterministicParams,
                horizonDays = 90,
            )
        )

        assertSame(scenario, ScenarioOps.applyPaycheckDelay(scenario, 7))
    }

    /** A late paycheck can only make the projection worse. */
    @Test
    fun paycheckDelayMovesRiskInTheExpectedDirection() {
        val simulation = ScenarioOps.simulatePaycheckDelay(25, inputs(pythonDefaultParams))

        assertTrue(
            "low point should fall: ${simulation.change.projectedLowPointCents}",
            simulation.change.projectedLowPointCents < 0L,
        )
        assertTrue(
            "safe to spend should fall: ${simulation.change.safeToSpendCents}",
            (simulation.change.safeToSpendCents ?: 0L) < 0L,
        )
        assertTrue(
            "the first negative day should move earlier: ${simulation.change.firstNegativeDateShiftDays}",
            (simulation.change.firstNegativeDateShiftDays ?: 0L) < 0L,
        )
    }

    /**
     * Cross-language parity for the delay, on the same harness as
     * [purchaseComparisonMatchesThePythonReferenceNumbers]: Python's `_shift_first_income` moves the
     * 1/20 paycheck to 2/14 and the reference engine then reports
     * `safe_to_spend_cents -113696` (change `-43353`), `minimum_balance_cents -100000` @ 2026-02-10
     * and `first_negative_date 2026-02-10`.
     */
    @Test
    fun paycheckDelayComparisonMatchesThePythonReferenceNumbers() {
        val simulation = ScenarioOps.simulatePaycheckDelay(25, inputs(pythonDefaultParams))

        assertEquals(
            ScenarioHeadline(
                safeToSpendCents = -113_696L,
                projectedLowPointCents = -100_000L,
                projectedLowPointDate = LocalDate.of(2026, 2, 10),
                probabilityNegativePpm = 1_000_000L,
                firstNegativeDate = LocalDate.of(2026, 2, 10),
            ),
            simulation.delayed,
        )
        assertEquals(
            ScenarioHeadlineDelta(
                safeToSpendCents = -43_353L,
                projectedLowPointCents = -50_000L,
                probabilityNegativePpm = 0L,
                firstNegativeDateShiftDays = -28L,
            ),
            simulation.change,
        )
    }

    // ── display ────────────────────────────────────────────────────────────────

    @Test
    fun verdictAndRowsDescribeTheComparisonInContractTerms() {
        val purchase = PurchaseSimulation(
            amountCents = 20_000L,
            purchaseDate = LocalDate.of(2026, 1, 15),
            baseline = ScenarioHeadline(-70_343L, -50_000L, LocalDate.of(2026, 3, 10), 1_000_000L, LocalDate.of(2026, 3, 10)),
            withPurchase = ScenarioHeadline(-90_343L, -70_000L, LocalDate.of(2026, 3, 10), 1_000_000L, LocalDate.of(2026, 2, 10)),
            change = ScenarioHeadlineDelta(-20_000L, -20_000L, 0L, -28L),
        )

        assertEquals(
            "Not covered: this purchase drops safe to spend by \$200.00.",
            ScenarioComparisonText.verdict(purchase),
        )
        val rows = ScenarioComparisonText.rows(purchase).toMap()
        assertEquals("-\$703.43 → -\$903.43", rows["Safe to spend"])
        assertEquals("-\$500.00 → -\$700.00", rows["Projected low point"])
        assertEquals("100.0% → 100.0%", rows["Odds of going negative"])
        assertEquals("2026-03-10 → 2026-02-10", rows["First negative day"])
        assertEquals("-\$200.00", rows["Low point change"])
        assertEquals("28 day(s) earlier", rows["First negative change"])
        assertEquals("\$200.00 on 2026-01-15", rows["Purchase"])

        val delay = PaycheckDelaySimulation(
            delayDays = 25,
            baseline = purchase.baseline,
            delayed = ScenarioHeadline(-113_696L, -100_000L, LocalDate.of(2026, 2, 10), 1_000_000L, LocalDate.of(2026, 2, 10)),
            change = ScenarioHeadlineDelta(-43_353L, -50_000L, 0L, -28L),
        )
        assertEquals(
            "A 25-day delay means safe to spend falls by \$433.53 and the projected low point drops by \$500.00.",
            ScenarioComparisonText.verdict(delay),
        )
        assertEquals("25 day(s)", ScenarioComparisonText.rows(delay).toMap()["Delay"])
        assertEquals(
            "A zero-day delay leaves the schedule alone",
            "No delay applied — the schedules are identical.",
            ScenarioComparisonText.verdict(delay.copy(delayDays = 0)),
        )
    }
}
