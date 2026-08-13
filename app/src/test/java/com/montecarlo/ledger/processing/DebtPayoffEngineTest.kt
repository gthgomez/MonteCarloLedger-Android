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
    fun simulateSchedule_flagsDebtsThatDoNotConvergeWithin360Months() {
        val summary = DebtPayoffEngine.simulateSchedule(
            debts = listOf(DebtItem(1, "Trap", 1_000_000L, 2_400, 5_000L)),
            extraMonthlyPaymentCents = 0L,
            strategy = PayoffStrategy.SNOWBALL,
            startDate = today,
        )
        assertTrue(summary.didNotConverge)
    }
}
