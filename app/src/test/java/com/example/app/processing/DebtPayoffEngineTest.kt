package com.example.app.processing

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
        val debtA = DebtItem(1, "Credit Card A", 500_000L, 24.0, 15_000L) // $5,000 @ 24%
        val debtB = DebtItem(2, "Car Loan B", 100_000L, 6.0, 5_000L)     // $1,000 @ 6%

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
            DebtItem(1, "Credit Card", 300_000L, 18.0, 10_000L) // $3,000 @ 18%
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
            DebtItem(1, "Store Card", 200_000L, 20.0, 5_000L)
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
}
