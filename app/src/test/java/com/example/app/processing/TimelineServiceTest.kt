package com.example.app.processing

import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.IncomeEntity
import com.example.app.data.PaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimelineServiceTest {

    @Test
    fun generateTimeline_treats90DaysAsATrue90DayWindow() {
        val startDate = LocalDate.of(2026, 1, 1)
        val income = IncomeEntity(
            id = 1,
            name = "Salary",
            amount_cents = 100_00,
            frequency = "monthly",
            day_of_month = null,
            next_date = startDate.toString(),
            expectedAmountCents = null,
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income),
            payments = emptyList(),
            startDate = startDate,
            daysAhead = 90,
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 3, 1),
            ),
            events.map { it.date },
        )
        assertFalse(events.any { it.date == LocalDate.of(2026, 4, 1) })
    }

    @Test
    fun generateTimeline_includesBillsAndIncomeInThe90DaySimulationWindow() {
        val startDate = LocalDate.of(2026, 4, 24)
        val income = IncomeEntity(
            id = 3,
            name = "Paycheck",
            amount_cents = 2_000_00,
            frequency = "bi-weekly",
            day_of_month = null,
            next_date = LocalDate.of(2026, 5, 1).toString(),
        )
        val bill = PaymentEntity(
            id = 4,
            name = "Rent",
            amount_cents = 1_100_00,
            frequency = "monthly",
            day_of_month = 5,
            next_date = LocalDate.of(2026, 5, 5).toString(),
            is_active = 1,
            isAutoWithdraw = true,
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income),
            payments = listOf(bill),
            startDate = startDate,
            daysAhead = 90,
        )

        assertTrue(events.any { it.type == "income" && it.date == LocalDate.of(2026, 5, 1) })
        assertTrue(events.any { it.type == "bill" && it.date == LocalDate.of(2026, 5, 5) })
        assertTrue(events.none { it.date >= startDate.plusDays(90) })
    }

    @Test
    fun generateTimeline_keepsMonthlyBillsOnTheConfiguredDay() {
        val startDate = LocalDate.of(2026, 1, 1)
        val bill = PaymentEntity(
            id = 1,
            name = "Rent",
            amount_cents = 1_000_00,
            frequency = "monthly",
            day_of_month = 31,
            next_date = LocalDate.of(2026, 1, 31).toString(),
            is_active = 1,
            isAutoWithdraw = true,
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(),
            payments = listOf(bill),
            startDate = startDate,
            daysAhead = 100,
        )

        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
            ),
            events.map { it.date },
        )
    }

    @Test
    fun generateTimeline_skipsPaidBillOccurrences() {
        val startDate = LocalDate.now()
        val payment = PaymentEntity(
            id = 7,
            name = "Streaming",
            amount_cents = 1_200,
            frequency = "weekly",
            day_of_month = null,
            next_date = startDate.plusDays(1).toString(),
            is_active = 1,
            isAutoWithdraw = true,
        )
        val paidOccurrence = BillOccurrenceEntity(
            id = 1,
            payment_id = payment.id,
            due_date = startDate.plusDays(1).toString(),
            amount_cents = payment.amount_cents,
            is_paid = 1,
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(),
            payments = listOf(payment),
            startDate = startDate,
            daysAhead = 14,
            paidOccurrences = listOf(paidOccurrence),
        )

        assertFalse(events.any { it.date == startDate.plusDays(1) })
        assertTrue(events.any { it.date == startDate.plusDays(8) })
    }

    @Test
    fun generateTimeline_movesAUserModifiedBillOccurrenceWithoutDuplicatingTheOriginalDate() {
        val startDate = LocalDate.of(2026, 4, 24)
        val originalDueDate = LocalDate.of(2026, 5, 1)
        val movedDueDate = LocalDate.of(2026, 5, 4)
        val payment = PaymentEntity(
            id = 12,
            name = "Car note",
            amount_cents = 25_000,
            frequency = "monthly",
            day_of_month = 1,
            next_date = originalDueDate.toString(),
            is_active = 1,
            isAutoWithdraw = true,
        )
        val movedOccurrence = BillOccurrenceEntity(
            id = 2,
            payment_id = payment.id,
            due_date = movedDueDate.toString(),
            amount_cents = payment.amount_cents,
            is_paid = 0,
            original_due_date = originalDueDate.toString(),
            is_user_modified = 1,
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(),
            payments = listOf(payment),
            startDate = startDate,
            daysAhead = 45,
            paidOccurrences = listOf(movedOccurrence),
        )

        assertFalse(events.any { it.type == "bill" && it.date == originalDueDate })
        assertTrue(events.any { it.type == "bill" && it.date == movedDueDate })
        assertTrue(events.any { it.type == "bill" && it.date == LocalDate.of(2026, 6, 1) })
    }

    @Test
    fun generateTimeline_surfacesOverdueOneTimeBillsOnToday() {
        val today = LocalDate.now()
        val payment = PaymentEntity(
            id = 9,
            name = "License fee",
            amount_cents = 2_500,
            frequency = "onetime",
            day_of_month = null,
            next_date = today.minusDays(3).toString(),
            is_active = 1,
            isAutoWithdraw = false,
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(),
            payments = listOf(payment),
            startDate = today,
            daysAhead = 7,
        )

        assertEquals(1, events.size)
        assertEquals(today, events.single().date)
        assertEquals("bill", events.single().type)
    }

    @Test
    fun generateTimeline_usesExpectedIncomeAmountForFirstOccurrenceOnly() {
        val startDate = LocalDate.of(2026, 1, 1)
        val income = IncomeEntity(
            id = 2,
            name = "Bonus",
            amount_cents = 10_000,
            frequency = "monthly",
            day_of_month = 1,
            next_date = startDate.toString(),
            expectedAmountCents = 12_000,
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income),
            payments = emptyList(),
            startDate = startDate,
            daysAhead = 40,
        )

        assertEquals(2, events.size)
        assertEquals(12_000, events[0].amount_cents)
        assertEquals(10_000, events[1].amount_cents)
    }

    @Test
    fun generateTimeline_attachesPolishedRecurrenceLabelsToBills() {
        val startDate = LocalDate.of(2026, 4, 20)
        val payment = PaymentEntity(
            id = 11,
            name = "Gym",
            amount_cents = 5_000,
            frequency = "Monthly",
            day_of_month = 15,
            next_date = LocalDate.of(2026, 5, 15).toString(),
            is_active = 1,
            isAutoWithdraw = true,
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(),
            payments = listOf(payment),
            startDate = startDate,
            daysAhead = 40,
        )

        assertEquals("Monthly on the 15th", events.single().recurrenceLabel)
    }
}
