package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MonthlySpendingPlanCalculatorTest {

    private val today = LocalDate.of(2026, 4, 15)

    @Test
    fun compute_separatesVariableSpendFromPaidBills() {
        val events = listOf(
            ForecastEvent(today.withDayOfMonth(1), "Paycheck", 300_000, "income"),
            ForecastEvent(today.withDayOfMonth(25), "Internet", 8_000, "bill"),
        )
        val transactions = listOf(
            // Paid rent already posted as expense + paid occurrence
            TransactionEntity(
                description = "Bill paid: Rent",
                amount_cents = -120_000,
                date = "2026-04-05",
                type = "expense",
                category = "bills",
            ),
            TransactionEntity(
                description = "Coffee",
                amount_cents = -450,
                date = "2026-04-10",
                type = "expense",
                category = "food",
            ),
            TransactionEntity(
                description = "Paycheck: Job",
                amount_cents = 300_000,
                date = "2026-04-01",
                type = "income",
            ),
        )
        val occurrences = listOf(
            BillOccurrenceEntity(
                id = 1,
                payment_id = 1,
                due_date = "2026-04-05",
                amount_cents = 120_000,
                is_paid = 1,
            ),
        )

        val plan = MonthlySpendingPlanCalculator.compute(
            monthlyEvents = events,
            monthTransactions = transactions,
            billOccurrences = occurrences,
            goals = emptyList(),
            today = today,
        )

        assertEquals(300_000, plan.expectedIncomeCents)
        assertEquals(300_000, plan.actualIncomeCents)
        assertEquals(8_000, plan.remainingBillsCents)
        assertEquals(120_000, plan.paidBillsCents)
        // Variable = coffee only (paid rent excluded via bill category / paid occurrence)
        assertEquals(450, plan.variableSpendCents)
        assertEquals(0, plan.goalPlanCents)
        // left = posted 300000 - paid rent 120000 - remaining 8000 - coffee 450
        assertEquals(171_550, plan.leftAfterPlanCents)
        assertFalse(plan.isOverPlan)
    }

    @Test
    fun compute_usesPostedIncomeWhenPaycheckHasLeftTheTimeline() {
        val events = listOf(
            ForecastEvent(today.withDayOfMonth(25), "Internet", 8_000, "bill"),
        )
        val transactions = listOf(
            TransactionEntity(
                description = "Paycheck: Job",
                amount_cents = 300_000,
                date = "2026-04-01",
                type = "income",
            ),
            TransactionEntity(
                description = "Bill paid: Rent",
                amount_cents = -120_000,
                date = "2026-04-05",
                type = "expense",
                category = "bills",
            ),
            TransactionEntity(
                description = "Coffee",
                amount_cents = -450,
                date = "2026-04-10",
                type = "expense",
                category = "food",
            ),
        )
        val occurrences = listOf(
            BillOccurrenceEntity(
                id = 1,
                payment_id = 1,
                due_date = "2026-04-05",
                amount_cents = 120_000,
                is_paid = 1,
            ),
        )

        val plan = MonthlySpendingPlanCalculator.compute(
            monthlyEvents = events,
            monthTransactions = transactions,
            billOccurrences = occurrences,
            goals = emptyList(),
            today = today,
        )

        assertEquals(0, plan.expectedIncomeCents)
        assertEquals(300_000, plan.actualIncomeCents)
        assertEquals(171_550, plan.leftAfterPlanCents)
    }

    @Test
    fun compute_doesNotSubtractBillPlanFromTotalExpenses_oldBug() {
        // Old bug: variable = actualExpense - billPlan → understated variable / optimistic left.
        val events = listOf(
            ForecastEvent(today.withDayOfMonth(1), "Pay", 100_000, "income"),
            ForecastEvent(today.withDayOfMonth(10), "Rent", 80_000, "bill"),
        )
        val transactions = listOf(
            // Only variable grocery spend; rent not paid yet
            TransactionEntity(
                description = "Groceries",
                amount_cents = -20_000,
                date = "2026-04-08",
                type = "expense",
                category = "food",
            ),
        )

        val plan = MonthlySpendingPlanCalculator.compute(
            monthlyEvents = events,
            monthTransactions = transactions,
            billOccurrences = emptyList(),
            goals = emptyList(),
            today = today,
        )

        // Old formula: variable = max(0, 20000 - 80000) = 0 → left = 100000 - 80000 = 20000 (wrong)
        // Correct: variable = 20000, remaining bills = 80000 → left = 0
        assertEquals(20_000, plan.variableSpendCents)
        assertEquals(80_000, plan.remainingBillsCents)
        assertEquals(0, plan.leftAfterPlanCents)
    }

    @Test
    fun compute_marksOverPlanWhenBillsExceedIncome() {
        val events = listOf(
            ForecastEvent(today.withDayOfMonth(1), "Pay", 100_000, "income"),
            ForecastEvent(today.withDayOfMonth(5), "Rent", 150_000, "bill"),
        )
        val plan = MonthlySpendingPlanCalculator.compute(
            monthlyEvents = events,
            monthTransactions = emptyList(),
            billOccurrences = emptyList(),
            goals = emptyList(),
            today = today,
        )
        assertTrue(plan.isOverPlan)
        assertEquals(-50_000, plan.leftAfterPlanCents)
    }

    @Test
    fun suggestedMonthlyGoalFunding_dividesByMonthsUntilDeadline() {
        val goals = listOf(
            GoalEntity(
                name = "Vacation",
                targetAmountCents = 120_000,
                currentAmountCents = 0,
                deadline = "2026-10-15", // 6 months from April 2026 month-start
                createdAt = "2026-01-01",
            )
        )
        val monthly = MonthlySpendingPlanCalculator.suggestedMonthlyGoalFunding(goals, today)
        assertEquals(20_000, monthly)
    }

    @Test
    fun compute_includesGoalPlanInLeftAfterPlan() {
        val events = listOf(
            ForecastEvent(today.withDayOfMonth(1), "Pay", 200_000, "income"),
            ForecastEvent(today.withDayOfMonth(10), "Rent", 50_000, "bill"),
        )
        val goals = listOf(
            GoalEntity(
                name = "Emergency",
                targetAmountCents = 60_000,
                currentAmountCents = 0,
                deadline = "2026-10-01",
                createdAt = "2026-01-01",
            )
        )
        val plan = MonthlySpendingPlanCalculator.compute(
            monthlyEvents = events,
            monthTransactions = emptyList(),
            billOccurrences = emptyList(),
            goals = goals,
            today = today,
        )
        // 6 months Apr→Oct → 10000/mo goal plan
        assertEquals(10_000, plan.goalPlanCents)
        // 200000 - 50000 - 0 - 10000
        assertEquals(140_000, plan.leftAfterPlanCents)
    }

    @Test
    fun suggestedMonthlyGoalFunding_overdueDeadlineIsDueNow() {
        val goals = listOf(
            GoalEntity(
                name = "Emergency",
                targetAmountCents = 60_000,
                currentAmountCents = 10_000,
                deadline = "2026-03-01",
                createdAt = "2026-01-01",
            )
        )
        val monthly = MonthlySpendingPlanCalculator.suggestedMonthlyGoalFunding(goals, today)
        assertEquals(50_000, monthly)
    }
}
