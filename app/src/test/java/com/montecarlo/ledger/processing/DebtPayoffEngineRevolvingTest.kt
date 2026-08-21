package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.DebtKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Revolving (credit-card) liabilities: percent-of-balance minimums with a flat
 * floor, paid-in-full below the floor, and declining payments inside the schedule.
 */
class DebtPayoffEngineRevolvingTest {

    private fun revolvingCard(
        balanceCents: Long,
        aprBps: Int = 2400,
        percentBps: Int = 300,
        floorCents: Long = 2_500L,
    ) = DebtItem(
        id = 1L,
        name = "Visa",
        balanceCents = balanceCents,
        aprBasisPoints = aprBps,
        minPaymentCents = 0L,
        dueDayOfMonth = 15,
        kind = DebtKind.REVOLVING,
        minPaymentPercentBps = percentBps,
        minPaymentFloorCents = floorCents,
    )

    @Test
    fun minimum_isPercentOfBalance_whenThatExceedsFloor() {
        val card = revolvingCard(balanceCents = 100_000L, percentBps = 300, floorCents = 2_500L)
        // 3% of $1000 = $30 beats the $25 floor.
        assertEquals(3_000L, DebtPayoffEngine.minimumPaymentCents(card, 100_000L))
    }

    @Test
    fun minimum_isFlatFloor_whenPercentFallsShort() {
        val card = revolvingCard(balanceCents = 20_000L, percentBps = 300, floorCents = 2_500L)
        // 3% of $200 = $6 loses to the $25 floor.
        assertEquals(2_500L, DebtPayoffEngine.minimumPaymentCents(card, 20_000L))
    }

    @Test
    fun minimum_paysBalanceInFull_belowFloor() {
        val card = revolvingCard(balanceCents = 1_000L, floorCents = 2_500L)
        assertEquals(1_000L, DebtPayoffEngine.minimumPaymentCents(card, 1_000L))
    }

    @Test
    fun minimum_isZero_atZeroBalance() {
        val card = revolvingCard(balanceCents = 0L)
        assertEquals(0L, DebtPayoffEngine.minimumPaymentCents(card, 0L))
    }

    @Test
    fun minimum_roundingIsHalfUp_notFloatingPoint() {
        val card = revolvingCard(balanceCents = 999L, percentBps = 500, floorCents = 0L)
        // 5% of 999 = 49.95 -> 50 (HALF_UP), never float truncation to 49.
        assertEquals(50L, DebtPayoffEngine.minimumPaymentCents(card, 999L))
    }

    @Test
    fun installment_keepsFixedMinimum_ignoringRevolvingFields() {
        val loan = DebtItem(
            id = 2L,
            name = "Auto loan",
            balanceCents = 500_000L,
            aprBasisPoints = 600,
            minPaymentCents = 12_000L,
            dueDayOfMonth = 5,
            kind = DebtKind.INSTALLMENT,
            minPaymentPercentBps = 900,
            minPaymentFloorCents = 99_00L,
        )
        assertEquals(12_000L, DebtPayoffEngine.minimumPaymentCents(loan, 500_000L))
    }

    @Test
    fun revolvingSchedule_declinesTowardPayoff_andConverges() {
        val start = LocalDate.of(2026, 8, 21)
        val summary = DebtPayoffEngine.simulateSchedule(
            debts = listOf(revolvingCard(balanceCents = 200_000L)),
            extraMonthlyPaymentCents = 0L,
            strategy = PayoffStrategy.AVALANCHE,
            startDate = start,
        )

        assertFalse("revolving card with percent minimums must converge", summary.didNotConverge)

        val cardSteps = summary.monthlySchedule.filter { it.debtId == 1L }
        assertTrue(cardSteps.isNotEmpty())
        assertTrue("interest should be charged before payoff", cardSteps.any { it.interestCents > 0 })

        val payments = cardSteps.map { it.paymentCents }
        // Percent-of-balance minimums shrink as the balance shrinks (until the floor).
        assertEquals(
            "payments must be non-increasing for a percent-minimum card",
            payments,
            payments.sortedDescending(),
        )

        // Ledger identity holds row-by-row: start + interest - payment == end.
        cardSteps.forEach { step ->
            assertEquals(
                step.startingBalanceCents + step.interestCents - step.paymentCents,
                step.endingBalanceCents,
            )
        }
        assertEquals(0L, cardSteps.last().endingBalanceCents)
    }

    @Test
    fun revolvingSchedule_floorDominance_stillPaysOffSmallBalancesInOneMonth() {
        val start = LocalDate.of(2026, 8, 21)
        val summary = DebtPayoffEngine.simulateSchedule(
            debts = listOf(revolvingCard(balanceCents = 1_500L, floorCents = 2_500L)),
            extraMonthlyPaymentCents = 0L,
            strategy = PayoffStrategy.SNOWBALL,
            startDate = start,
        )
        assertFalse(summary.didNotConverge)
        assertEquals(1, summary.monthsToPayoff)
        assertEquals(0L, summary.monthlySchedule.last().endingBalanceCents)
    }
}
