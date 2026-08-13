package com.montecarlo.ledger.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PaymentScheduleTest {

    @Test
    fun monthlyPaymentsStayAnchoredToTheConfiguredDay() {
        val today = LocalDate.of(2026, 4, 20)
        val nextDate = PaymentSchedule.resolveNextPaymentDate(
            today = today,
            recurrence = "Monthly",
            dueDay = 15,
            dueDate = null,
        )

        assertEquals("2026-05-15", nextDate)
        assertFalse(PaymentSchedule.requiresExplicitDueDate("Monthly"))
    }

    @Test
    fun monthlyPaymentsCanStartFromAnExplicitNextDueDate() {
        val today = LocalDate.of(2026, 4, 20)
        val nextDate = PaymentSchedule.resolveNextPaymentDate(
            today = today,
            recurrence = "Monthly",
            dueDay = 12,
            dueDate = LocalDate.of(2026, 6, 12),
        )

        assertEquals("2026-06-12", nextDate)
    }

    @Test
    fun nonMonthlyPaymentsRequireAnExplicitDueDate() {
        val today = LocalDate.of(2026, 4, 20)
        val nextDate = PaymentSchedule.resolveNextPaymentDate(
            today = today,
            recurrence = "Bi-weekly",
            dueDay = null,
            dueDate = LocalDate.of(2026, 4, 27),
        )

        assertEquals("2026-04-27", nextDate)
        assertTrue(PaymentSchedule.requiresExplicitDueDate("Bi-weekly"))
    }

    @Test
    fun pastDueDatesAdvanceToTheNextOccurrence() {
        val today = LocalDate.of(2026, 4, 20)
        val monthly = PaymentSchedule.resolveNextPaymentDate(
            today = today,
            recurrence = "Monthly",
            dueDay = 15,
            dueDate = LocalDate.of(2026, 4, 15),
        )
        val weekly = PaymentSchedule.resolveNextPaymentDate(
            today = today,
            recurrence = "Weekly",
            dueDay = null,
            dueDate = LocalDate.of(2026, 4, 6),
        )

        assertEquals("2026-05-15", monthly)
        assertEquals("2026-04-20", weekly)
    }

    @Test
    fun recurrenceSummary_readsLikePolishedProductCopy() {
        assertEquals("Every week", PaymentSchedule.recurrenceSummary("Weekly"))
        assertEquals("Every 2 weeks", PaymentSchedule.recurrenceSummary("Bi-weekly"))
        assertEquals("Twice a month", PaymentSchedule.recurrenceSummary("Semi-monthly"))
        assertEquals("Monthly on the 15th", PaymentSchedule.recurrenceSummary("Monthly", dayOfMonth = 15))
        assertEquals("One-time due Apr 20, 2026", PaymentSchedule.recurrenceSummary("One-time", nextDate = "2026-04-20"))
    }
}
