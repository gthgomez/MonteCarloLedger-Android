package com.montecarlo.ledger.processing

import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.util.LedgerDate
import java.time.LocalDate

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
        var currentDate = LedgerDate.parseIsoOrNull(income.next_date) ?: return emptyList()
        var firstOccurrence = true

        // Unpaid occurrences before the window still hit cash today (one-time and recurring).
        while (currentDate < startDate) {
            val amount = if (firstOccurrence && income.expectedAmountCents != null) {
                income.expectedAmountCents
            } else {
                income.amount_cents
            }
            events += ForecastEvent(
                date = startDate,
                description = income.name,
                amount_cents = amount.toLong(),
                type = "income",
            )
            firstOccurrence = false
            currentDate = RecurrenceMath.nextDate(currentDate, income.frequency, income.day_of_month) ?: return events
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
                    amount_cents = amount.toLong(),
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
        var currentDate = LedgerDate.parseIsoOrNull(payment.next_date) ?: return emptyList()

        // Unpaid bills before the window still hit cash today (one-time and recurring).
        while (currentDate < startDate) {
            val key = Pair(payment.id, currentDate.toString())
            if (key !in suppressedSet) {
                events += ForecastEvent(
                    date = startDate,
                    description = payment.name,
                    amount_cents = payment.amount_cents.toLong(),
                    type = "bill",
                    recurrenceLabel = PaymentSchedule.recurrenceSummary(
                        payment.frequency,
                        payment.day_of_month,
                        payment.next_date
                    ),
                )
            }
            currentDate = RecurrenceMath.nextDate(currentDate, payment.frequency, payment.day_of_month) ?: return events
        }

        while (currentDate < endDate) {
            if (currentDate >= startDate) {
                // Skip dates that have already been paid
                if (Pair(payment.id, currentDate.toString()) !in suppressedSet) {
                    events += ForecastEvent(
                        date = currentDate,
                        description = payment.name,
                        amount_cents = payment.amount_cents.toLong(),
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
            val dueDate = LedgerDate.parseIsoOrNull(occurrence.due_date) ?: return@mapNotNull null
            val displayDate = if (dueDate < startDate) startDate else dueDate
            if (displayDate >= endDate) return@mapNotNull null

            ForecastEvent(
                date = displayDate,
                description = payment.name,
                amount_cents = occurrence.amount_cents.toLong(),
                type = "bill",
                recurrenceLabel = PaymentSchedule.recurrenceSummary(
                    payment.frequency,
                    payment.day_of_month,
                    occurrence.due_date
                ),
            )
        }
    }
}
