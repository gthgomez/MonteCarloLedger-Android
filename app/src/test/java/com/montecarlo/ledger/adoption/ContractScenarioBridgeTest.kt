package com.montecarlo.ledger.adoption

import com.montecarlo.ledger.contract.ContractEngine
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.contract.ContractSimulationParams
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.processing.MonteCarloCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * MC-06b: repository-style rows -> canonical [com.montecarlo.ledger.contract.ContractScenario].
 * These tests pin the bug-clearing semantics (B-05/B-07) and MCD-0014 overdue exclusion at the
 * bridge boundary.
 */
class ContractScenarioBridgeTest {

    private val asOf = LocalDate.of(2026, 1, 1)

    private fun payment(
        id: Int,
        amountCents: Long = 10_000L,
        frequency: String = "Monthly",
        dayOfMonth: Int? = null,
        nextDate: String = "2026-01-31",
    ) = PaymentEntity(
        id = id,
        name = "Rent",
        amount_cents = amountCents,
        frequency = frequency,
        day_of_month = dayOfMonth,
        next_date = nextDate,
    )

    private fun income(
        id: Int = 1,
        amountCents: Long = 100_000L,
        frequency: String = "Monthly",
        dayOfMonth: Int? = null,
        nextDate: String = "2026-01-05",
        expected: Long? = null,
    ) = IncomeEntity(
        id = id,
        name = "Paycheck",
        amount_cents = amountCents,
        frequency = frequency,
        day_of_month = dayOfMonth,
        next_date = nextDate,
        expectedAmountCents = expected,
    )

    private fun build(
        payments: List<PaymentEntity> = emptyList(),
        incomes: List<IncomeEntity> = emptyList(),
        occurrences: List<BillOccurrenceEntity> = emptyList(),
        startingBalanceCents: Long = 100_000L,
        horizonDays: Int = 90,
        simulation: ContractSimulationParams? = null,
    ) = ContractScenarioBridge.build(
        ContractScenarioBridge.Inputs(
            startingBalanceCents = startingBalanceCents,
            asOf = asOf,
            incomes = incomes,
            payments = payments,
            billOccurrences = occurrences,
            simulation = simulation,
            horizonDays = horizonDays,
        )
    )

    /** B-07 / MCD-0022: `anchor_day` is derived from the start day and preserved across clamping. */
    @Test
    fun monthlyPaymentWithNullDayOfMonthKeepsTheAnchorAcrossClampedMonths() {
        val scenario = build(payments = listOf(payment(id = 1, dayOfMonth = null, nextDate = "2026-01-31")))

        val dates = ContractEngine.inWindowBaseEvents(scenario).map { it.date }

        assertEquals(
            listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31)),
            dates,
        )
        assertEquals(31, scenario.recurrences.single().anchorDay)
    }

    /** MCD-0014: overdue occurrences are not projected into the window. */
    @Test
    fun overdueRecurrenceOccurrencesAreNotProjected() {
        val scenario = build(
            payments = listOf(payment(id = 2, dayOfMonth = 15, nextDate = "2025-12-15")),
            horizonDays = 60,
        )

        val dates = ContractEngine.inWindowBaseEvents(scenario).map { it.date }

        assertEquals(listOf(LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15)), dates)
        assertFalse(dates.contains(LocalDate.of(2025, 12, 15)))
    }

    /** Paid template dates are suppressed with contract 1.1 `occurrence_exclusions` (MCD-0024). */
    @Test
    fun paidOccurrenceSuppressesTheMatchingTemplateDate() {
        val scenario = build(
            payments = listOf(payment(id = 3, dayOfMonth = 1, nextDate = "2026-01-01")),
            occurrences = listOf(
                BillOccurrenceEntity(
                    id = 10,
                    payment_id = 3,
                    due_date = "2026-01-01",
                    amount_cents = 10_000L,
                    is_paid = 1,
                )
            ),
        )

        val dates = ContractEngine.inWindowBaseEvents(scenario).map { it.date }

        assertEquals(listOf(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1)), dates)
    }

    /**
     * MC-07/C5 + MCD-0024: a paid occurrence in the MIDDLE of the window is suppressed.
     * The old prefix-only suppression (advancing the lower bound) could not express this.
     */
    @Test
    fun paidMiddleOccurrenceIsSuppressedNotOnlyAPrefix() {
        val scenario = build(
            payments = listOf(payment(id = 5, dayOfMonth = 1, nextDate = "2026-01-01")),
            occurrences = listOf(
                BillOccurrenceEntity(
                    id = 12,
                    payment_id = 5,
                    due_date = "2026-02-01",
                    amount_cents = 10_000L,
                    is_paid = 1,
                )
            ),
            horizonDays = 90,
        )

        val dates = ContractEngine.inWindowBaseEvents(scenario).map { it.date }

        assertEquals(
            listOf(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)),
            dates,
        )
        assertEquals(1, scenario.occurrenceExclusions.size)
    }

    /** User-moved unpaid occurrences become explicit events. */
    @Test
    fun userMovedOccurrenceBecomesAnExplicitEvent() {
        val scenario = build(
            payments = listOf(payment(id = 4, dayOfMonth = 1, nextDate = "2026-01-01")),
            occurrences = listOf(
                BillOccurrenceEntity(
                    id = 11,
                    payment_id = 4,
                    due_date = "2026-01-20",
                    original_due_date = "2026-01-01",
                    amount_cents = 12_000L,
                    is_paid = 0,
                    is_user_modified = 1,
                )
            ),
            horizonDays = 40,
        )

        val events = ContractEngine.inWindowBaseEvents(scenario).map { it.date.toString() to it.amountCents }

        assertTrue(events.contains("2026-01-20" to -12_000L))
        assertFalse(events.any { it.first == "2026-01-01" })
    }

    /** The first in-window income occurrence uses `expected_amount_cents`, later ones do not. */
    @Test
    fun expectedIncomeAmountAppliesToFirstInWindowOccurrenceOnly() {
        val scenario = build(
            incomes = listOf(income(id = 1, expected = 80_000L, nextDate = "2026-01-05")),
            horizonDays = 45,
        )

        val incomes = ContractEngine.inWindowBaseEvents(scenario)
            .filter { it.type == "income" }
            .map { it.date to it.amountCents }

        assertEquals(
            listOf(LocalDate.of(2026, 1, 5) to 80_000L, LocalDate.of(2026, 2, 5) to 100_000L),
            incomes,
        )
    }

    /** B-05 / MCD-0010: an already-negative opening balance is negative from `as_of`. */
    @Test
    fun negativeOpeningBalanceIsFirstNegativeAtAsOf() {
        val scenario = build(
            incomes = listOf(income(nextDate = "2026-01-10")),
            startingBalanceCents = -5_000L,
        )

        val result = ContractRunner.run(scenario)

        assertEquals(asOf, result.forecast.firstNegativeDate)
    }

    /** B-05: opening-negative counts toward the negative-balance probability. */
    @Test
    fun negativeOpeningBalanceCountsTowardNegativeProbability() {
        val simulation = ContractSimulationParams(
            runs = 50,
            seed = 1,
            incomeVariationMin = 0,
            incomeVariationMax = 0,
            surpriseProbabilityPpm = 0,
        )
        val scenario = build(
            incomes = listOf(income(nextDate = "2026-01-10")),
            startingBalanceCents = -5_000L,
            simulation = simulation,
        )

        val risk = ContractRunner.run(scenario).risk!!

        assertEquals(1_000_000L, risk.negativeBalanceProbabilityPpm)
        assertTrue(risk.safeToSpendCents < 0L)
    }

    /** Calibration defaults map onto the contract simulation block (ppm scaling). */
    @Test
    fun calibrationMapsOntoContractSimulation() {
        val params = ContractScenarioBridge.simulationParams(MonteCarloCalibration.defaults())

        assertEquals(500, params.runs)
        assertEquals(42L, params.seed)
        assertEquals(-8, params.incomeVariationMin)
        assertEquals(8, params.incomeVariationMax)
        assertEquals(150_000, params.surpriseProbabilityPpm)
        assertEquals(2_000, params.surpriseAmountMin)
        assertEquals(15_000, params.surpriseAmountMax)
    }
}
