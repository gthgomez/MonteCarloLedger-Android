package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.TransactionRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimelineServiceCategoryTest {

    private val today = LocalDate.of(2026, 8, 21)

    private fun payment(
        id: Int,
        name: String,
        nextDate: String,
    ) = com.montecarlo.ledger.data.PaymentEntity(
        id = id,
        name = name,
        amount_cents = 10_000L,
        frequency = "Monthly",
        day_of_month = 1,
        next_date = nextDate,
    )

    private val rule = TransactionRuleEntity(
        id = 1,
        match_text = "netflix",
        category = "subscriptions",
        is_active = 1,
        priority = 0,
        created_at = "",
    )

    @Test
    fun generateTimeline_attachesRuleCategoryToBillEvents() {
        val events = TimelineService.generateTimeline(
            incomes = emptyList(),
            payments = listOf(payment(1, "Netflix", "2026-09-01")),
            startDate = today,
            daysAhead = 40,
            rules = listOf(rule),
        )

        assertTrue(events.isNotEmpty())
        events.forEach { event ->
            assertEquals("subscriptions", event.category)
        }
    }

    @Test
    fun generateTimeline_leavesUncategorizedBillsNull() {
        val events = TimelineService.generateTimeline(
            incomes = emptyList(),
            payments = listOf(payment(2, "Mystery Utility", "2026-09-01")),
            startDate = today,
            daysAhead = 40,
            rules = listOf(rule),
        )

        assertTrue(events.isNotEmpty())
        events.forEach { event ->
            assertNull(event.category)
        }
    }

    @Test
    fun generateTimeline_incomeEventsCarryNoCategory() {
        val income = com.montecarlo.ledger.data.IncomeEntity(
            id = 1,
            name = "Paycheck",
            amount_cents = 200_000L,
            frequency = "Bi-weekly",
            day_of_month = null,
            next_date = "2026-09-04",
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income),
            payments = emptyList(),
            startDate = today,
            daysAhead = 20,
            rules = listOf(rule),
        )

        assertTrue(events.isNotEmpty())
        events.forEach { event ->
            assertNull(event.category)
        }
    }
}
