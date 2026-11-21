package com.montecarlo.ledger.adoption

import com.montecarlo.ledger.contract.ContractEngine
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.contract.ContractSimulationParams
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.DebtKind
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
        paymentCategories: Map<Int, String> = emptyMap(),
        debts: List<DebtEntity> = emptyList(),
        startDate: LocalDate = asOf,
    ) = ContractScenarioBridge.build(
        ContractScenarioBridge.Inputs(
            startingBalanceCents = startingBalanceCents,
            asOf = startDate,
            incomes = incomes,
            payments = payments,
            billOccurrences = occurrences,
            simulation = simulation,
            horizonDays = horizonDays,
            paymentCategories = paymentCategories,
            debts = debts,
        )
    )

    @Test
    fun debtMinimumKeepsTheLeapMonthAnchorAndCapsTheFinalPayment() {
        val debt = DebtEntity(
            id = 1,
            name = "Loan",
            balanceCents = 60_000L,
            aprBasisPoints = 1_200,
            minimumPaymentCents = 20_000L,
            dueDayOfMonth = 31,
        )
        val scenario = build(debts = listOf(debt), startDate = LocalDate.of(2028, 1, 31))
        assertEquals(
            listOf(
                LocalDate.of(2028, 1, 31) to -20_000L,
                LocalDate.of(2028, 2, 29) to -20_000L,
                LocalDate.of(2028, 3, 31) to -20_000L,
            ),
            ContractEngine.inWindowBaseEvents(scenario).map { it.date to it.amountCents },
        )

        val paidOff = build(debts = listOf(debt.copy(balanceCents = 21_000L, dueDayOfMonth = 1)))
        assertEquals(
            listOf(asOf to -20_000L, asOf.plusMonths(1) to -1_010L),
            ContractEngine.inWindowBaseEvents(paidOff).map { it.date to it.amountCents },
        )
    }

    @Test
    fun onlyAnExplicitActiveBillLinkReplacesTheEstimatedDebtSchedule() {
        val bill = payment(id = 7, amountCents = 30_000L, dayOfMonth = 10, nextDate = "2026-01-10")
        val debt = DebtEntity(
            id = 1,
            name = bill.name,
            balanceCents = 50_000L,
            aprBasisPoints = 0,
            minimumPaymentCents = bill.amount_cents,
            dueDayOfMonth = 10,
        )
        val linked = debt.copy(linkedPaymentId = bill.id)
        val unrelated = build(payments = listOf(bill), debts = listOf(debt), horizonDays = 30)
        val oneObligation = build(payments = listOf(bill), debts = listOf(linked), horizonDays = 30)
        val inactiveLink = build(
            payments = listOf(bill.copy(is_active = 0)),
            debts = listOf(linked, debt.copy(id = 2, isActive = false)),
            horizonDays = 30,
        )
        val separateEvents = ContractEngine.inWindowBaseEvents(unrelated)
        assertEquals(listOf(-30_000L, -30_000L), separateEvents.map { it.amountCents })
        assertEquals(listOf("payment-7", "debt-1-2026-01-10"), separateEvents.map { it.id })
        assertEquals(listOf(-30_000L), ContractEngine.inWindowBaseEvents(oneObligation).map { it.amountCents })
        assertEquals(listOf(-30_000L), ContractEngine.inWindowBaseEvents(inactiveLink).map { it.amountCents })
    }

    @Test
    fun unusableLinkedBillDoesNotEraseDebtMinimum() {
        val debt = DebtEntity(
            id = 1,
            name = "Card",
            balanceCents = 50_000L,
            aprBasisPoints = 0,
            minimumPaymentCents = 30_000L,
            dueDayOfMonth = 10,
            linkedPaymentId = 9,
        )
        val linkedBill = payment(
            id = 9,
            amountCents = 0L,
            frequency = "One-time",
            nextDate = "2026-01-10",
        ).copy(name = "Card")

        val scenario = build(payments = listOf(linkedBill), debts = listOf(debt), horizonDays = 30)

        assertEquals(listOf("debt-1-2026-01-10"), ContractEngine.inWindowBaseEvents(scenario).map { it.id })
        assertEquals(
            listOf(30_000L),
            ContractScenarioBridge.debtMinimumForecastEvents(
                ContractScenarioBridge.Inputs(
                    startingBalanceCents = 50_000L,
                    asOf = asOf,
                    incomes = emptyList(),
                    payments = listOf(linkedBill),
                    debts = listOf(debt),
                    horizonDays = 30,
                ),
            ).map { it.amount_cents },
        )
    }

    @Test
    fun oneTimeLinkedBillOnlyReplacesThatMonthsDebtMinimum() {
        val bill = payment(
            id = 12,
            amountCents = 30_000L,
            frequency = "One-time",
            nextDate = "2026-01-10",
        ).copy(name = "Loan")
        val debt = DebtEntity(
            id = 2,
            name = "Loan",
            balanceCents = 100_000L,
            aprBasisPoints = 0,
            minimumPaymentCents = 30_000L,
            dueDayOfMonth = 10,
            linkedPaymentId = bill.id,
        )

        val scenario = build(payments = listOf(bill), debts = listOf(debt), horizonDays = 90)

        assertEquals(
            listOf("payment-12", "debt-2-2026-02-10", "debt-2-2026-03-10"),
            ContractEngine.inWindowBaseEvents(scenario).map { it.id },
        )
    }

    @Test
    fun revolvingDebtUsesTheOpeningBalanceAndAnExclusiveEndDate() {
        val debt = DebtEntity(
            id = 1,
            name = "Card",
            balanceCents = 50_000L,
            aprBasisPoints = 1_200,
            minimumPaymentCents = 0,
            dueDayOfMonth = 10,
            kind = DebtKind.REVOLVING,
            minPaymentPercentBps = 400,
            minPaymentFloorCents = 1_500,
        )
        val scenario = build(debts = listOf(debt), horizonDays = 40)
        assertEquals(
            listOf(LocalDate.of(2026, 1, 10) to -2_000L),
            ContractEngine.inWindowBaseEvents(scenario).map { it.date to it.amountCents },
        )
    }

    @Test
    fun overdueDebtMinimumIsProjectedNowUnlessItsLinkedBillWasPaid() {
        val today = LocalDate.of(2026, 1, 20)
        val smallDebt = DebtEntity(
            id = 9,
            name = "Small Loan",
            balanceCents = 5_000L,
            aprBasisPoints = 0,
            minimumPaymentCents = 3_000L,
            dueDayOfMonth = 10,
        )
        val unlinkedOverdue = build(
            debts = listOf(smallDebt),
            horizonDays = 40,
            startDate = today,
        )
        assertEquals(
            listOf(
                today to -3_000L,
                LocalDate.of(2026, 2, 10) to -2_000L,
            ),
            ContractEngine.inWindowBaseEvents(unlinkedOverdue).map { it.date to it.amountCents },
        )

        val bill = payment(id = 7, amountCents = 30_000L, dayOfMonth = 10, nextDate = "2026-02-10")
        val debt = DebtEntity(
            id = 1,
            name = "Rent card",
            balanceCents = 50_000L,
            aprBasisPoints = 0,
            minimumPaymentCents = 30_000L,
            dueDayOfMonth = 10,
            linkedPaymentId = bill.id,
        )
        val overdue = build(
            payments = listOf(bill),
            debts = listOf(debt),
            horizonDays = 40,
            startDate = today,
        )

        assertEquals(
            listOf(today to -30_000L, LocalDate.of(2026, 2, 10) to -30_000L),
            ContractEngine.inWindowBaseEvents(overdue).map { it.date to it.amountCents },
        )

        val paidThisMonth = build(
            payments = listOf(bill),
            occurrences = listOf(
                BillOccurrenceEntity(
                    payment_id = bill.id,
                    due_date = "2026-01-10",
                    original_due_date = "2026-01-10",
                    amount_cents = bill.amount_cents,
                    is_paid = 1,
                ),
            ),
            debts = listOf(debt),
            horizonDays = 40,
            startDate = today,
        )
        assertEquals(
            listOf(LocalDate.of(2026, 2, 10) to -30_000L),
            ContractEngine.inWindowBaseEvents(paidThisMonth).map { it.date to it.amountCents },
        )
    }

    @Test
    fun movedLinkedBillFromEarlierThisMonthReplacesTheOverdueDebtMinimum() {
        val today = LocalDate.of(2026, 1, 20)
        val bill = payment(
            id = 7,
            amountCents = 30_000L,
            dayOfMonth = 10,
            nextDate = "2026-02-10",
        )
        val occurrence = BillOccurrenceEntity(
            payment_id = bill.id,
            due_date = "2026-01-25",
            original_due_date = "2026-01-10",
            amount_cents = bill.amount_cents,
            is_user_modified = 1,
        )
        val debt = DebtEntity(
            id = 1,
            name = "Rent card",
            balanceCents = 50_000L,
            aprBasisPoints = 0,
            minimumPaymentCents = 30_000L,
            dueDayOfMonth = 10,
            linkedPaymentId = bill.id,
        )
        val scenario = build(
            payments = listOf(bill),
            occurrences = listOf(occurrence),
            debts = listOf(debt),
            horizonDays = 40,
            startDate = today,
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 25) to -30_000L,
                LocalDate.of(2026, 2, 10) to -30_000L,
            ),
            ContractEngine.inWindowBaseEvents(scenario).map { it.date to it.amountCents },
        )
    }

    @Test
    fun linkedBillShortfallUsesOccurrenceAmountWithoutChargingPaidAmountAgain() {
        val bill = payment(
            id = 7,
            amountCents = 10_000L,
            dayOfMonth = 10,
            nextDate = "2026-02-10",
        )
        val debt = DebtEntity(
            id = 1,
            name = "Card",
            balanceCents = 100_000L,
            aprBasisPoints = 0,
            minimumPaymentCents = 30_000L,
            dueDayOfMonth = 10,
            linkedPaymentId = bill.id,
        )
        val inputs = ContractScenarioBridge.Inputs(
            startingBalanceCents = 100_000L,
            asOf = LocalDate.of(2026, 1, 20),
            incomes = emptyList(),
            payments = listOf(bill),
            billOccurrences = listOf(
                BillOccurrenceEntity(
                    payment_id = bill.id,
                    due_date = "2026-01-10",
                    original_due_date = "2026-01-10",
                    amount_cents = 10_000L,
                    is_paid = 1,
                ),
            ),
            debts = listOf(debt),
            horizonDays = 40,
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 20) to 20_000L,
                LocalDate.of(2026, 2, 10) to 20_000L,
            ),
            ContractScenarioBridge.debtMinimumForecastEvents(inputs).map { it.date to it.amount_cents },
        )
    }

    @Test
    fun revolvingMinimumsAccrueMonthlyInterestBeforeLaterDueDates() {
        val debt = DebtEntity(
            id = 1,
            name = "Card",
            balanceCents = 100_000L,
            aprBasisPoints = 12_000,
            minimumPaymentCents = 0L,
            dueDayOfMonth = 10,
            kind = DebtKind.REVOLVING,
            minPaymentPercentBps = 1_000,
        )

        val scenario = build(debts = listOf(debt), horizonDays = 90)

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 10) to -10_000L,
                LocalDate.of(2026, 2, 10) to -9_900L,
                LocalDate.of(2026, 3, 10) to -9_801L,
            ),
            ContractEngine.inWindowBaseEvents(scenario).map { it.date to it.amountCents },
        )
    }

    /** MCD-0027: every product builder stamps the same (latest released) contract version. */
    @Test
    fun builtScenarioDeclaresTheLatestReleasedContractVersion() {
        val scenario = build(
            incomes = listOf(income()),
            payments = listOf(payment(1)),
        )
        assertEquals("2.0", scenario.contractVersion)
    }

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

    /** MCD-0025: resolved bill categories reach the recurrence and calibrator ranges reach params. */
    @Test
    fun billCategoriesAndCalibrationRangesReachTheContract() {
        val scenario = build(
            payments = listOf(payment(id = 7, dayOfMonth = 10, nextDate = "2026-01-10")),
            paymentCategories = mapOf(7 to "housing"),
        )
        assertEquals("housing", scenario.recurrences.single().category)

        val params = ContractScenarioBridge.simulationParams(
            MonteCarloCalibration.defaults().copy(expenseCategoryVariation = mapOf("food" to 10..20))
        )
        assertEquals(10..20, params.expenseCategoryVariation["food"])
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
