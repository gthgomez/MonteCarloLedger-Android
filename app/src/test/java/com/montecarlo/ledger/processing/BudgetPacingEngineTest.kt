package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BudgetPacingEngineTest {

    private val today = LocalDate.of(2026, 7, 31)

    @Test
    fun calculatePacing_returnsOnTrackWhenActualVelocityIsBelowTarget() {
        // Safe to spend: $1,400 (140,000 cents) over 14 days => Target velocity: $100/day (10,000 cents/day)
        // Spending in last 7 days: $560 (56,000 cents) => Actual velocity: $80/day (8,000 cents/day)
        val transactions = listOf(
            TransactionEntity(1, "Groceries", -56_000, today.minusDays(2).toString(), "expense")
        )

        val result = BudgetPacingEngine.calculatePacing(
            safeToSpendCents = 140_000L,
            daysToPayday = 14,
            transactions = transactions,
            today = today,
        )

        assertEquals(10_000L, result.targetDailyVelocityCents)
        assertEquals(8_000L, result.actualDailyVelocityCents)
        assertEquals(PacingStatus.ON_TRACK, result.pacingStatus)
    }

    @Test
    fun calculatePacing_returnsWarningWhenActualVelocityExceeds15PercentOverTarget() {
        // Safe to spend: $1,400 over 14 days => Target velocity: $100/day (10,000 cents/day)
        // Spending in last 7 days: $840 (84,000 cents) => Actual velocity: $120/day (12,000 cents/day, 20% over target)
        val transactions = listOf(
            TransactionEntity(1, "Shopping", -84_000, today.minusDays(1).toString(), "expense")
        )

        val result = BudgetPacingEngine.calculatePacing(
            safeToSpendCents = 140_000L,
            daysToPayday = 14,
            transactions = transactions,
            today = today,
        )

        assertEquals(10_000L, result.targetDailyVelocityCents)
        assertEquals(12_000L, result.actualDailyVelocityCents)
        assertEquals(PacingStatus.WARNING, result.pacingStatus)
    }

    @Test
    fun calculatePacing_returnsCriticalWhenActualVelocityExceeds40PercentOverTarget() {
        // Target velocity: $100/day
        // Spending in last 7 days: $1,050 (105,000 cents) => Actual velocity: $150/day (50% over target)
        val transactions = listOf(
            TransactionEntity(1, "Dining", -105_000, today.minusDays(3).toString(), "expense")
        )

        val result = BudgetPacingEngine.calculatePacing(
            safeToSpendCents = 140_000L,
            daysToPayday = 14,
            transactions = transactions,
            today = today,
        )

        assertEquals(15_000L, result.actualDailyVelocityCents)
        assertEquals(PacingStatus.CRITICAL, result.pacingStatus)
    }

    @Test
    fun calculatePacing_returnsCriticalWhenSafeToSpendIsZeroOrNegative() {
        val result = BudgetPacingEngine.calculatePacing(
            safeToSpendCents = -5_000L,
            daysToPayday = 10,
            transactions = emptyList(),
            today = today,
        )

        assertEquals(PacingStatus.CRITICAL, result.pacingStatus)
    }

    @Test
    fun calculatePacing_returnsCriticalWhenRunwayIsLessThan7Days() {
        // Safe to spend: $200 (20,000 cents) over 14 days => Target velocity: $14.28/day
        // Spending in last 7 days: $350 (35,000 cents) => Actual velocity: $50/day (5,000 cents/day)
        // Runway: 20,000 / 5,000 = 4 days (< 7 days)
        val transactions = listOf(
            TransactionEntity(1, "Impulse", -35_000, today.minusDays(1).toString(), "expense")
        )

        val result = BudgetPacingEngine.calculatePacing(
            safeToSpendCents = 20_000L,
            daysToPayday = 14,
            transactions = transactions,
            today = today,
        )

        assertTrue(result.runwayDays < 7.0)
        assertEquals(PacingStatus.CRITICAL, result.pacingStatus)
    }
}
