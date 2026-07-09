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

    // ──────────────────────────────────────────────
    // Existing tests (preserved)
    // ──────────────────────────────────────────────

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

    // ──────────────────────────────────────────────
    // generateTimeline — daysAhead boundary
    // ──────────────────────────────────────────────

    @Test
    fun generateTimeline_daysAheadNegative_returnsEmpty() {
        val startDate = LocalDate.of(2026, 1, 1)
        val income = IncomeEntity(
            id = 1, name = "Salary", amount_cents = 100_00,
            frequency = "monthly", day_of_month = 1,
            next_date = startDate.toString(),
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income),
            payments = emptyList(),
            startDate = startDate,
            daysAhead = -1,
        )
        assertTrue(events.isEmpty())
    }

    @Test
    fun generateTimeline_zeroDaysAhead_returnsEmpty() {
        val startDate = LocalDate.of(2026, 1, 1)
        val income = IncomeEntity(
            id = 1, name = "Salary", amount_cents = 100_00,
            frequency = "monthly", day_of_month = 1,
            next_date = startDate.toString(),
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income),
            payments = emptyList(),
            startDate = startDate,
            daysAhead = 0,
        )
        assertTrue(events.isEmpty())
    }

    // ──────────────────────────────────────────────
    // generateTimeline — sort order
    // ──────────────────────────────────────────────

    @Test
    fun generateTimeline_eventsSortedByDateThenIncomeBeforeBill() {
        val startDate = LocalDate.of(2026, 1, 1)
        val income = IncomeEntity(
            id = 1, name = "Salary", amount_cents = 500_00,
            frequency = "monthly", day_of_month = 1,
            next_date = startDate.toString(),
        )
        val bill = PaymentEntity(
            id = 2, name = "Rent", amount_cents = 1000_00,
            frequency = "monthly", day_of_month = 1,
            next_date = startDate.toString(), is_active = 1,
        )
        val bill2 = PaymentEntity(
            id = 3, name = "Electric", amount_cents = 50_00,
            frequency = "monthly", day_of_month = 2,
            next_date = LocalDate.of(2026, 1, 2).toString(), is_active = 1,
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income),
            payments = listOf(bill, bill2),
            startDate = startDate,
            daysAhead = 5,
        )

        assertEquals(3, events.size)
        // Day 1: income before bill
        assertEquals(LocalDate.of(2026, 1, 1), events[0].date)
        assertEquals("income", events[0].type)
        assertEquals("Salary", events[0].description)
        // Day 1: bill follows income on same date
        assertEquals(LocalDate.of(2026, 1, 1), events[1].date)
        assertEquals("bill", events[1].type)
        assertEquals("Rent", events[1].description)
        // Day 2: different date sorts after day 1
        assertEquals(LocalDate.of(2026, 1, 2), events[2].date)
        assertEquals("bill", events[2].type)
        assertEquals("Electric", events[2].description)
    }

    // ──────────────────────────────────────────────
    // generateTimeline — one-time income
    // ──────────────────────────────────────────────

    @Test
    fun generateTimeline_oneTimeIncomeInFuture_showsOnDueDate() {
        val startDate = LocalDate.of(2026, 1, 1)
        val dueDate = LocalDate.of(2026, 1, 15)
        val income = IncomeEntity(
            id = 1, name = "Bonus", amount_cents = 500_00,
            frequency = "onetime", day_of_month = null,
            next_date = dueDate.toString(),
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income), payments = emptyList(),
            startDate = startDate, daysAhead = 30,
        )

        assertEquals(1, events.size)
        assertEquals(dueDate, events.single().date)
        assertEquals("income", events.single().type)
        assertEquals(500_00, events.single().amount_cents)
    }

    @Test
    fun generateTimeline_oneTimeIncomePastDue_surfacesOnStartDate() {
        val startDate = LocalDate.of(2026, 1, 10)
        val pastDate = LocalDate.of(2026, 1, 5)
        val income = IncomeEntity(
            id = 1, name = "Old Bonus", amount_cents = 500_00,
            frequency = "onetime", day_of_month = null,
            next_date = pastDate.toString(),
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income), payments = emptyList(),
            startDate = startDate, daysAhead = 30,
        )

        assertEquals(1, events.size)
        assertEquals(startDate, events.single().date)
        assertEquals("income", events.single().type)
        assertEquals(500_00, events.single().amount_cents)
    }

    @Test
    fun generateTimeline_oneTimeIncomePastDueWithExpectedAmount_usesExpectedAmount() {
        val startDate = LocalDate.of(2026, 1, 10)
        val pastDate = LocalDate.of(2026, 1, 5)
        val income = IncomeEntity(
            id = 1, name = "Estimated Bonus", amount_cents = 10_000,
            frequency = "onetime", day_of_month = null,
            next_date = pastDate.toString(),
            expectedAmountCents = 12_000,
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income), payments = emptyList(),
            startDate = startDate, daysAhead = 30,
        )

        assertEquals(1, events.size)
        assertEquals(12_000, events.single().amount_cents)
    }

    // ──────────────────────────────────────────────
    // generateTimeline — one-time bill
    // ──────────────────────────────────────────────

    @Test
    fun generateTimeline_oneTimeBillInFuture_showsOnDueDate() {
        val startDate = LocalDate.of(2026, 1, 1)
        val dueDate = LocalDate.of(2026, 1, 15)
        val bill = PaymentEntity(
            id = 1, name = "Consulting Fee", amount_cents = 100_00,
            frequency = "onetime", day_of_month = null,
            next_date = dueDate.toString(), is_active = 1,
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(), payments = listOf(bill),
            startDate = startDate, daysAhead = 30,
        )

        assertEquals(1, events.size)
        assertEquals(dueDate, events.single().date)
        assertEquals("bill", events.single().type)
        assertEquals(100_00, events.single().amount_cents)
    }

    @Test
    fun generateTimeline_paidOneTimeBillFuture_suppressed() {
        val startDate = LocalDate.of(2026, 1, 1)
        val dueDate = LocalDate.of(2026, 1, 15)
        val bill = PaymentEntity(
            id = 1, name = "Consulting Fee", amount_cents = 100_00,
            frequency = "onetime", day_of_month = null,
            next_date = dueDate.toString(), is_active = 1,
        )
        val paidOccurrence = BillOccurrenceEntity(
            id = 1, payment_id = 1,
            due_date = dueDate.toString(),
            amount_cents = 100_00, is_paid = 1,
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(), payments = listOf(bill),
            startDate = startDate, daysAhead = 30,
            paidOccurrences = listOf(paidOccurrence),
        )

        assertTrue(events.none { it.type == "bill" && it.date == dueDate })
    }

    @Test
    fun generateTimeline_paidOneTimeBillPastDue_suppressed() {
        val startDate = LocalDate.of(2026, 1, 10)
        val pastDueDate = LocalDate.of(2026, 1, 5)
        val bill = PaymentEntity(
            id = 1, name = "Old Fee", amount_cents = 50_00,
            frequency = "onetime", day_of_month = null,
            next_date = pastDueDate.toString(), is_active = 1,
        )
        val paidOccurrence = BillOccurrenceEntity(
            id = 1, payment_id = 1,
            due_date = pastDueDate.toString(),
            amount_cents = 50_00, is_paid = 1,
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(), payments = listOf(bill),
            startDate = startDate, daysAhead = 30,
            paidOccurrences = listOf(paidOccurrence),
        )

        // Past-due one-time bill that's paid: should NOT surface on startDate
        assertTrue(events.isEmpty())
    }

    // ──────────────────────────────────────────────
    // generateTimeline — inactive bill filtering
    // ──────────────────────────────────────────────

    @Test
    fun generateTimeline_inactiveBills_filteredOut() {
        val startDate = LocalDate.of(2026, 1, 1)
        val activeBill = PaymentEntity(
            id = 1, name = "Rent", amount_cents = 1000_00,
            frequency = "monthly", day_of_month = 1,
            next_date = startDate.toString(), is_active = 1,
        )
        val inactiveBill = PaymentEntity(
            id = 2, name = "Old Subscription", amount_cents = 10_00,
            frequency = "monthly", day_of_month = 15,
            next_date = LocalDate.of(2026, 1, 15).toString(), is_active = 0,
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(),
            payments = listOf(activeBill, inactiveBill),
            startDate = startDate,
            daysAhead = 30,
        )

        assertTrue(events.any { it.type == "bill" && it.description == "Rent" })
        assertTrue(events.none { it.description == "Old Subscription" })
    }

    @Test
    fun generateTimeline_allBillsInactive_returnsNoBillEvents() {
        val startDate = LocalDate.of(2026, 1, 1)
        val inactiveBill = PaymentEntity(
            id = 1, name = "Some Bill", amount_cents = 50_00,
            frequency = "monthly", day_of_month = 1,
            next_date = startDate.toString(), is_active = 0,
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(),
            payments = listOf(inactiveBill),
            startDate = startDate,
            daysAhead = 30,
        )

        assertTrue(events.isEmpty())
    }

    // ──────────────────────────────────────────────
    // generateTimeline — modified bill occurrences
    // ──────────────────────────────────────────────

    @Test
    fun generateTimeline_modifiedBillOccurrenceFromInactivePayment_skipped() {
        val startDate = LocalDate.of(2026, 4, 24)
        val originalDueDate = LocalDate.of(2026, 5, 1)
        val movedDueDate = LocalDate.of(2026, 5, 4)
        val payment = PaymentEntity(
            id = 12, name = "Old Car note", amount_cents = 25_000,
            frequency = "monthly", day_of_month = 1,
            next_date = originalDueDate.toString(), is_active = 0,
        )
        val movedOccurrence = BillOccurrenceEntity(
            id = 2, payment_id = payment.id,
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

        // Inactive payment: neither template events nor modified occurrences appear
        assertTrue(events.isEmpty())
    }

    @Test
    fun generateTimeline_paidOccurrenceWithOriginalDueDate_suppressesTemplateEventByOriginalDate() {
        val startDate = LocalDate.of(2026, 1, 1)
        val originalDueDate = LocalDate.of(2026, 1, 15)
        val bill = PaymentEntity(
            id = 1, name = "Loan", amount_cents = 200_00,
            frequency = "monthly", day_of_month = 15,
            next_date = originalDueDate.toString(), is_active = 1,
        )
        // Paid bill with an original_due_date set (e.g. user moved it, then paid it)
        val paidOccurrence = BillOccurrenceEntity(
            id = 1, payment_id = 1,
            due_date = LocalDate.of(2026, 1, 18).toString(),
            amount_cents = 200_00,
            is_paid = 1,
            original_due_date = originalDueDate.toString(),
        )

        val events = TimelineService.generateTimeline(
            incomes = emptyList(), payments = listOf(bill),
            startDate = startDate, daysAhead = 46,
            paidOccurrences = listOf(paidOccurrence),
        )

        // Jan 15 template event should be suppressed (paid with original_due_date)
        assertFalse(events.any { it.date == originalDueDate })
        // Subsequent month should still appear
        assertTrue(events.any { it.date == LocalDate.of(2026, 2, 15) })
    }

    // ──────────────────────────────────────────────
    // generateTimeline — multi-occurrence scenarios
    // ──────────────────────────────────────────────

    @Test
    fun generateTimeline_recurringBiWeeklyIncome_generatesCorrectDates() {
        val startDate = LocalDate.of(2026, 1, 1)
        val income = IncomeEntity(
            id = 1, name = "BiWeekly Pay", amount_cents = 1500_00,
            frequency = "bi-weekly", day_of_month = null,
            next_date = LocalDate.of(2026, 1, 9).toString(),
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income), payments = emptyList(),
            startDate = startDate, daysAhead = 30,
        )

        // Bi-weekly from Jan 9: Jan 9, Jan 23
        assertEquals(2, events.size)
        assertEquals(LocalDate.of(2026, 1, 9), events[0].date)
        assertEquals(LocalDate.of(2026, 1, 23), events[1].date)
    }

    @Test
    fun generateTimeline_incomeAndBillsMixed_producesBothTypes() {
        val startDate = LocalDate.of(2026, 1, 1)
        val income = IncomeEntity(
            id = 1, name = "Paycheck", amount_cents = 2000_00,
            frequency = "monthly", day_of_month = 1,
            next_date = startDate.toString(),
        )
        val bill = PaymentEntity(
            id = 1, name = "Rent", amount_cents = 1200_00,
            frequency = "monthly", day_of_month = 5,
            next_date = LocalDate.of(2026, 1, 5).toString(), is_active = 1,
        )

        val events = TimelineService.generateTimeline(
            incomes = listOf(income), payments = listOf(bill),
            startDate = startDate, daysAhead = 60,
        )

        val incomeDates = events.filter { it.type == "income" }.map { it.date }
        val billDates = events.filter { it.type == "bill" }.map { it.date }

        assertTrue(incomeDates.contains(LocalDate.of(2026, 1, 1)))
        assertTrue(incomeDates.contains(LocalDate.of(2026, 2, 1)))
        assertTrue(billDates.contains(LocalDate.of(2026, 1, 5)))
        assertTrue(billDates.contains(LocalDate.of(2026, 2, 5)))
    }
}
