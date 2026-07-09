package com.example.app.processing

import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.IncomeEntity
import com.example.app.data.PaymentEntity
import java.time.LocalDate
import java.util.Locale

object TimelineService {

    fun generateTimeline(
        incomes: List<IncomeEntity>,
        payments: List<PaymentEntity>,
        startDate: LocalDate,
        daysAhead: Int,
        paidOccurrences: List<BillOccurrenceEntity> = emptyList(),
    ): List<ForecastEvent> {
        if (daysAhead < 0) return emptyList()

        val endDate = startDate.plusDays(daysAhead.toLong())
        val paymentById = payments.associateBy { it.id }
        // Paid/skipped occurrences and user-moved occurrences suppress the generated template date.
        val suppressedSet: Set<Pair<Int, String>> = paidOccurrences
            .filter { it.is_paid != 0 }
            .map { Pair(it.payment_id, it.original_due_date ?: it.due_date) }
            .plus(
                paidOccurrences
                    .filter { it.is_paid == 0 && it.is_user_modified != 0 }
                    .map { Pair(it.payment_id, it.original_due_date ?: it.due_date) }
            )
            .plus(
                paidOccurrences
                    .filter { it.is_user_modified != 0 }
                    .map { Pair(it.payment_id, it.due_date) }
            )
            .toSet()

        val events = mutableListOf<ForecastEvent>()

        incomes.forEach { income ->
            events += generateIncomeEvents(income, startDate, endDate)
        }

        payments.filter { it.is_active != 0 }.forEach { payment ->
            events += generatePaymentEvents(payment, startDate, endDate, suppressedSet)
        }
        events += generateModifiedBillOccurrenceEvents(paidOccurrences, paymentById, startDate, endDate)

        return events.sortedWith(
            compareBy<ForecastEvent> { it.date }
                .thenBy { if (it.type == "income") 0 else 1 }
                .thenBy { it.description },
        )
    }

    private fun generateIncomeEvents(
        income: IncomeEntity,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ForecastEvent> {
        val events = mutableListOf<ForecastEvent>()
        var currentDate = parseDateOrNull(income.next_date) ?: return emptyList()
        var firstOccurrence = true
        val isOneTime = RecurrenceMath.normalizeFrequency(income.frequency) == "onetime"

        // Unpaid one-time income past its date — surface it on today
        if (isOneTime && currentDate < startDate) {
            val amount = if (income.expectedAmountCents != null) income.expectedAmountCents else income.amount_cents
            events += ForecastEvent(
                date = startDate,
                description = income.name,
                amount_cents = amount,
                type = "income",
            )
            return events
        }

        while (currentDate < endDate) {
            if (currentDate >= startDate) {
                val amount = if (firstOccurrence && income.expectedAmountCents != null) {
                    income.expectedAmountCents
                } else {
                    income.amount_cents
                }
                events += ForecastEvent(
                    date = currentDate,
                    description = income.name,
                    amount_cents = amount,
                    type = "income",
                )
            }

            firstOccurrence = false
            currentDate = RecurrenceMath.nextDate(currentDate, income.frequency, income.day_of_month) ?: break
        }

        return events
    }

    private fun generatePaymentEvents(
        payment: PaymentEntity,
        startDate: LocalDate,
        endDate: LocalDate,
        suppressedSet: Set<Pair<Int, String>> = emptySet(),
    ): List<ForecastEvent> {
        val events = mutableListOf<ForecastEvent>()
        var currentDate = parseDateOrNull(payment.next_date) ?: return emptyList()
        val isOneTime = RecurrenceMath.normalizeFrequency(payment.frequency) == "onetime"

        // Unpaid one-time bills past their due date are overdue — surface them on today
        if (isOneTime && currentDate < startDate) {
            val key = Pair(payment.id, currentDate.toString())
            if (key !in suppressedSet) {
                events += ForecastEvent(
                    date = startDate,
                    description = payment.name,
                    amount_cents = payment.amount_cents,
                    type = "bill",
                    recurrenceLabel = PaymentSchedule.recurrenceSummary(
                        payment.frequency,
                        payment.day_of_month,
                        payment.next_date
                    ),
                )
            }
            return events
        }

        while (currentDate < endDate) {
            if (currentDate >= startDate) {
                // Skip dates that have already been paid
                if (Pair(payment.id, currentDate.toString()) !in suppressedSet) {
                    events += ForecastEvent(
                        date = currentDate,
                        description = payment.name,
                        amount_cents = payment.amount_cents,
                        type = "bill",
                        recurrenceLabel = PaymentSchedule.recurrenceSummary(
                            payment.frequency,
                            payment.day_of_month,
                            payment.next_date
                        ),
                    )
                }
            }

            currentDate = RecurrenceMath.nextDate(currentDate, payment.frequency, payment.day_of_month) ?: break
        }

        return events
    }

    private fun generateModifiedBillOccurrenceEvents(
        occurrences: List<BillOccurrenceEntity>,
        paymentById: Map<Int, PaymentEntity>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<ForecastEvent> {
        return occurrences.mapNotNull { occurrence ->
            if (occurrence.is_paid != 0 || occurrence.is_user_modified == 0) return@mapNotNull null
            val payment = paymentById[occurrence.payment_id]?.takeIf { it.is_active != 0 } ?: return@mapNotNull null
            val dueDate = parseDateOrNull(occurrence.due_date) ?: return@mapNotNull null
            if (dueDate < startDate || dueDate >= endDate) return@mapNotNull null

            ForecastEvent(
                date = dueDate,
                description = payment.name,
                amount_cents = occurrence.amount_cents,
                type = "bill",
                recurrenceLabel = PaymentSchedule.recurrenceSummary(
                    payment.frequency,
                    payment.day_of_month,
                    occurrence.due_date
                ),
            )
        }
    }

    private fun parseDateOrNull(value: String): LocalDate? {
        return runCatching { LocalDate.parse(value) }.getOrNull()
    }
}
