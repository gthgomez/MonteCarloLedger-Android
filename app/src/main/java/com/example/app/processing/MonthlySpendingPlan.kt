package com.example.app.processing

import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.GoalEntity
import com.example.app.data.TransactionEntity
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * Monthly spending plan (Simplifi-style):
 *
 * **Left this month = expected income − remaining bills − variable spend − goal plan**
 *
 * - Expected income: scheduled income events for the calendar month.
 * - Remaining bills: unpaid bill events still on the timeline for the month.
 * - Paid bills: paid bill occurrences falling in the month (actuals).
 * - Variable spend: expense transactions this month that are not paid bills
 *   (avoids double-counting bill payments already in the ledger).
 * - Goal plan: suggested monthly funding toward open goals.
 */
data class MonthlySpendingPlan(
    val monthStart: LocalDate,
    val monthEndExclusive: LocalDate,
    val expectedIncomeCents: Int,
    val actualIncomeCents: Int,
    val remainingBillsCents: Int,
    val paidBillsCents: Int,
    val variableSpendCents: Int,
    val goalPlanCents: Int,
    val leftAfterPlanCents: Int,
) {
    val isOverPlan: Boolean get() = leftAfterPlanCents < 0
    val totalObligationsCents: Int
        get() = remainingBillsCents + variableSpendCents + goalPlanCents
}

object MonthlySpendingPlanCalculator {

    fun compute(
        monthlyEvents: List<ForecastEvent>,
        monthTransactions: List<TransactionEntity>,
        billOccurrences: List<BillOccurrenceEntity>,
        goals: List<GoalEntity>,
        today: LocalDate = LocalDate.now(),
    ): MonthlySpendingPlan {
        val monthStart = today.withDayOfMonth(1)
        val monthEndExclusive = monthStart.plusMonths(1)

        val expectedIncomeCents = monthlyEvents
            .filter { it.type.equals("income", ignoreCase = true) }
            .sumOf { it.amount_cents }

        // Timeline already suppresses paid bill dates; remaining = still-scheduled bills.
        val remainingBillsCents = monthlyEvents
            .filter { it.type.equals("bill", ignoreCase = true) }
            .sumOf { it.amount_cents }

        val paidBillsCents = billOccurrences
            .filter { it.is_paid != 0 }
            .mapNotNull { occurrence ->
                val due = parseDateOrNull(occurrence.due_date) ?: return@mapNotNull null
                if (due.isBefore(monthStart) || !due.isBefore(monthEndExclusive)) return@mapNotNull null
                occurrence.amount_cents
            }
            .sum()

        val actualIncomeCents = monthTransactions
            .filter { it.type.equals("income", ignoreCase = true) }
            .sumOf { it.amount_cents }

        val actualExpenseCents = abs(
            monthTransactions
                .filter { it.type.equals("expense", ignoreCase = true) }
                .sumOf { it.amount_cents }
        )

        // Expenses already include paid bills. Variable = non-bill spending only.
        val billRelatedExpenseCents = maxOf(
            paidBillsCents,
            abs(
                monthTransactions
                    .filter {
                        it.type.equals("expense", ignoreCase = true) &&
                            it.category.trim().lowercase(Locale.ROOT) in BILL_CATEGORIES
                    }
                    .sumOf { it.amount_cents }
            ),
        )
        val variableSpendCents = (actualExpenseCents - billRelatedExpenseCents).coerceAtLeast(0)

        val goalPlanCents = suggestedMonthlyGoalFunding(goals, today)

        val leftAfterPlanCents =
            expectedIncomeCents - remainingBillsCents - variableSpendCents - goalPlanCents

        return MonthlySpendingPlan(
            monthStart = monthStart,
            monthEndExclusive = monthEndExclusive,
            expectedIncomeCents = expectedIncomeCents,
            actualIncomeCents = actualIncomeCents,
            remainingBillsCents = remainingBillsCents,
            paidBillsCents = paidBillsCents,
            variableSpendCents = variableSpendCents,
            goalPlanCents = goalPlanCents,
            leftAfterPlanCents = leftAfterPlanCents,
        )
    }

    /**
     * Suggest how much of this month's surplus should go toward goals.
     * - Deadline set: remaining / months left (at least 1 month).
     * - No deadline: remaining / 6 (gentle monthly pace).
     */
    fun suggestedMonthlyGoalFunding(goals: List<GoalEntity>, today: LocalDate): Int {
        return goals.sumOf { goal ->
            val remaining = (goal.targetAmountCents - goal.currentAmountCents).coerceAtLeast(0)
            if (remaining == 0) return@sumOf 0
            val deadline = goal.deadline?.let { parseDateOrNull(it) }
            if (deadline != null && !deadline.isBefore(today)) {
                val months = ChronoUnit.MONTHS
                    .between(today.withDayOfMonth(1), deadline.withDayOfMonth(1))
                    .toInt()
                    .coerceAtLeast(1)
                // Ceiling division so we do not under-fund by a few cents.
                (remaining + months - 1) / months
            } else {
                (remaining + 5) / 6
            }
        }
    }

    private fun parseDateOrNull(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim()) }.getOrNull()

    private val BILL_CATEGORIES = setOf("bills", "bill", "utilities", "rent", "subscriptions")
}
