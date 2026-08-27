package com.montecarlo.ledger.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DebtPayoffEngineTest {

    private val today = LocalDate.of(2026, 7, 31)

    @Test
    fun simulateSchedule_snowballSortsByLowestBalanceFirst() {
        val debtA = DebtItem(1, "Credit Card A", 500_000L, 2_400, 15_000L) // $5,000 @ 24%
        val debtB = DebtItem(2, "Car Loan B", 100_000L, 600, 5_000L)     // $1,000 @ 6%

        val summary = DebtPayoffEngine.simulateSchedule(
            debts = listOf(debtA, debtB),
            extraMonthlyPaymentCents = 10_000L,
            strategy = PayoffStrategy.SNOWBALL,
            startDate = today,
        )

        assertTrue(summary.monthsToPayoff > 0)
        assertTrue(summary.totalPaidCents > 0)
    }

    @Test
    fun runSimulation_calculatesAcceleratedPayoffAndInterestSaved() {
        val debts = listOf(
            DebtItem(1, "Credit Card", 300_000L, 1_800, 10_000L) // $3,000 @ 18%
        )

        val result = DebtPayoffEngine.runSimulation(
            debts = debts,
            extraMonthlyPaymentCents = 20_000L, // $200 extra per month
            strategy = PayoffStrategy.AVALANCHE,
            currentBalanceCents = 500_000L,
            forecastEvents = emptyList(),
            today = today,
        )

        assertTrue("Accelerated payoff should be faster than baseline",
            result.acceleratedSummary.monthsToPayoff < result.baselineSummary.monthsToPayoff)
        assertTrue("Accelerated interest should be lower than baseline interest",
            result.acceleratedSummary.totalInterestCents < result.baselineSummary.totalInterestCents)
        assertTrue(result.monthsSaved > 0)
        assertTrue(result.interestSavedCents > 0)
        assertFalse(result.causesOverdraft)
    }

    @Test
    fun runSimulation_cashFlowSafetyGuardFlagsOverdraftWhenExtraPaymentExceedsReserves() {
        val debts = listOf(
            DebtItem(1, "Store Card", 200_000L, 2_000, 5_000L)
        )

        // Starting balance $100 (10,000 cents), but extra payment proposed is $500 (50,000 cents/mo)
        val result = DebtPayoffEngine.runSimulation(
            debts = debts,
            extraMonthlyPaymentCents = 50_000L,
            strategy = PayoffStrategy.SNOWBALL,
            currentBalanceCents = 10_000L,
            forecastEvents = emptyList(),
            today = today,
        )

        assertTrue(result.causesOverdraft)
        assertNotNull(result.overdraftDate)
        assertNotNull(result.warningMessage)
        assertTrue(result.warningMessage!!.contains("risks an overdraft shortfall"))
    }

    @Test
    fun simulateSchedule_snowballAppliesExtraToLowestBalanceFirst() {
        val debtA = DebtItem(1, "Credit Card A", 500_000L, 2_400, 15_000L)
        val debtB = DebtItem(2, "Car Loan B", 100_000L, 600, 5_000L)

        val summary = DebtPayoffEngine.simulateSchedule(
            debts = listOf(debtA, debtB),
            extraMonthlyPaymentCents = 10_000L,
            strategy = PayoffStrategy.SNOWBALL,
            startDate = today,
        )

        val firstMonth = summary.monthlySchedule.filter { it.monthNumber == 1 }
        val lowest = firstMonth.minByOrNull { it.startingBalanceCents }
        assertEquals(2L, lowest?.debtId)
        assertTrue(lowest!!.paymentCents > 5_000L)
        assertEquals(lowest.startingBalanceCents + lowest.interestCents - lowest.paymentCents, lowest.endingBalanceCents)
    }

    @Test
    fun runSimulation_includesCurrentMonthExtraPaymentInOverdraftGuard() {
        val debts = listOf(
            DebtItem(1, "Store Card", 200_000L, 2_000, 5_000L)
        )
        val result = DebtPayoffEngine.runSimulation(
            debts = debts,
            extraMonthlyPaymentCents = 50_000L,
            strategy = PayoffStrategy.SNOWBALL,
            currentBalanceCents = 10_000L,
            forecastEvents = emptyList(),
            today = LocalDate.of(2026, 7, 2),
        )
        assertTrue(result.causesOverdraft)
    }

    @Test
    fun runSimulation_doesNotDoubleCountMinimumAlreadyOnTheTimeline() {
        val debts = listOf(
            DebtItem(1, "Card", 200_000L, 2_000, 5_000L, dueDayOfMonth = 1)
        )
        val result = DebtPayoffEngine.runSimulation(
            debts = debts,
            extraMonthlyPaymentCents = 1_000L,
            strategy = PayoffStrategy.SNOWBALL,
            currentBalanceCents = 20_000L,
            forecastEvents = listOf(
                ForecastEvent(
                    date = LocalDate.of(2026, 8, 1),
                    description = "Card",
                    amount_cents = 5_000L,
                    type = "bill",
                )
            ),
            today = today,
        )
        assertFalse(result.causesOverdraft)
    }

    @Test
    fun runSimulation_siblingDebtsWithSameMinimumAndDueDayAreBothCounted() {
        val debts = listOf(
            DebtItem(1, "Card A", 200_000L, 2_000, 5_000L, dueDayOfMonth = 15),
            DebtItem(2, "Card B", 150_000L, 2_000, 5_000L, dueDayOfMonth = 15),
        )

        // $200 balance vs $50/mo per debt of minimums + $10 extra: survives if only ONE
        // debt's minimums are counted ($190 out), overdrafts when BOTH are counted ($340).
        // The old amount-based suppression hid the second debt and cleared the warning.
        val result = DebtPayoffEngine.runSimulation(
            debts = debts,
            extraMonthlyPaymentCents = 1_000L,
            strategy = PayoffStrategy.SNOWBALL,
            currentBalanceCents = 20_000L,
            forecastEvents = emptyList(),
            today = LocalDate.of(2026, 7, 2),
        )

        assertTrue(result.causesOverdraft)
    }

    @Test
    fun simulateSchedule_rowsReconcileWhenPaymentIsBelowAccruedInterest() {
        // $10,000 @ 24% APR = $200/mo interest against a $50 minimum: balance must grow.
        val summary = DebtPayoffEngine.simulateSchedule(
            debts = listOf(DebtItem(1, "Trap", 1_000_000L, 2_400, 5_000L)),
            extraMonthlyPaymentCents = 0L,
            strategy = PayoffStrategy.SNOWBALL,
            startDate = today,
        )

        assertTrue(summary.didNotConverge)
        val firstMonth = summary.monthlySchedule.filter { it.monthNumber == 1 }.single()
        assertEquals(5_000L, firstMonth.paymentCents)
        assertTrue(firstMonth.principalCents < 0)
        assertEquals(
            "starting - principal must equal ending on every schedule row",
            firstMonth.startingBalanceCents - firstMonth.principalCents,
            firstMonth.endingBalanceCents,
        )
    }

    @Test
    fun simulateSchedule_flagsDebtsThatDoNotConvergeWithin360Months() {
        val summary = DebtPayoffEngine.simulateSchedule(
            debts = listOf(DebtItem(1, "Trap", 1_000_000L, 2_400, 5_000L)),
            extraMonthlyPaymentCents = 0L,
            strategy = PayoffStrategy.SNOWBALL,
            startDate = today,
        )
        assertTrue(summary.didNotConverge)
    }

    @Test
    fun simulateSchedule_flagsNonConvergenceWhenCompoundingOverflowsLong() {
        // $10,000 @ 240% APR with a $50 minimum compounds ~19.5% per month and
        // would overflow Long around month ~168; the wrapped negative balance used
        // to fake a "converged" result instead of flagging non-convergence.
        val summary = DebtPayoffEngine.simulateSchedule(
            debts = listOf(DebtItem(1, "Trap", 1_000_000L, 24_000, 5_000L)),
            extraMonthlyPaymentCents = 0L,
            strategy = PayoffStrategy.SNOWBALL,
            startDate = today,
        )

        assertTrue(summary.didNotConverge)
        assertTrue("overflow must stop the simulation before the 360-month cap",
            summary.monthsToPayoff < 360)
    }

    @Test
    fun simulateSchedule_installmentFinalPaymentNeverDrivesBalanceNegative() {
        // Remaining balance $10.00 with a $50.00 fixed minimum: the final payment
        // must pay off exactly, not drive the balance to -$40.00.
        val summary = DebtPayoffEngine.simulateSchedule(
            debts = listOf(DebtItem(1, "Installment", 1_000L, 0, 5_000L)),
            extraMonthlyPaymentCents = 0L,
            strategy = PayoffStrategy.SNOWBALL,
            startDate = today,
        )

        assertFalse("a $10 balance with a $50 minimum must still converge", summary.didNotConverge)
        assertEquals("total paid must equal the starting balance for a 0% APR loan", 1_000L, summary.totalPaidCents)
        assertTrue(
            "every schedule row must end at a non-negative balance",
            summary.monthlySchedule.all { it.endingBalanceCents >= 0L }
        )
        val finalRow = summary.monthlySchedule.last()
        assertEquals(0L, finalRow.endingBalanceCents)
    }

    @Test
    fun minimumPaymentCents_installmentIsCappedAtRemainingBalance() {
        assertEquals(0L, DebtPayoffEngine.minimumPaymentCents(
            DebtItem(1, "Paid Off", 100_000L, 600, 5_000L), balanceCents = 0L
        ))
        assertEquals(800L, DebtPayoffEngine.minimumPaymentCents(
            DebtItem(1, "Nearly Done", 100_000L, 600, 5_000L), balanceCents = 800L
        ))
    }
}
