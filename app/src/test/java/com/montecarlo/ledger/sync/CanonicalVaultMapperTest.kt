package com.montecarlo.ledger.sync

import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.PaymentEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalVaultMapperTest {
    private val bill = PaymentEntity(
        id = 7,
        name = "Card payment",
        amount_cents = 2_500L,
        frequency = "Monthly",
        day_of_month = 10,
        next_date = "2026-12-10",
    )

    private fun debt(
        id: Long,
        active: Boolean = true,
        linkedPaymentId: Int? = null,
    ) = DebtEntity(
        id = id,
        name = "Card $id",
        balanceCents = 50_000L,
        aprBasisPoints = 1_900,
        minimumPaymentCents = 2_500L,
        dueDayOfMonth = 10,
        linkedPaymentId = linkedPaymentId,
        isActive = active,
    )

    private fun snapshot(debts: List<DebtEntity>, payments: List<PaymentEntity> = emptyList()) =
        Json.parseToJsonElement(
            CanonicalVaultMapper.buildSnapshot(
                ledgerBalanceCents = 0L,
                incomes = emptyList(),
                payments = payments,
                transactions = emptyList(),
                occurrences = emptyList(),
                debts = debts,
                updatedAtIso = "2026-11-21T00:00:00Z",
            ).toString(),
        ).jsonObject

    @Test
    fun simpleActiveDebtsRemainV21AndOmitV22Fields() {
        val liability = snapshot(listOf(debt(1))).getValue("liabilities").jsonArray.single().jsonObject
        assertEquals("2.1", snapshot(listOf(debt(1))).getValue("version").toString().trim('"'))
        assertFalse("is_active" in liability)
        assertFalse("linked_payment_id" in liability)
    }

    @Test
    fun linkedAndInactiveDebtsUseV22AndPreserveFields() {
        val root = snapshot(listOf(debt(1, linkedPaymentId = 7), debt(2, active = false)), listOf(bill))
        assertEquals("2.2", root.getValue("version").toString().trim('"'))
        val rows = root.getValue("liabilities").jsonArray.map { it.jsonObject }
        assertEquals(2, rows.size)
        assertEquals(7, rows[0].getValue("linked_payment_id").toString().toInt())
        assertTrue(rows[0].getValue("is_active").toString().toBoolean())
        assertFalse(rows[1].getValue("is_active").toString().toBoolean())
        assertNull(rows[1]["linked_payment_id"])
    }

    @Test
    fun accountLinkedDebtStillFailsClosed() {
        val failure = runCatching {
            snapshot(listOf(debt(1).copy(linkedAccountId = 9L)))
        }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
    }

    @Test
    fun duplicateOrInactiveLinkedPaymentsFailClosed() {
        val duplicate = runCatching {
            snapshot(listOf(debt(1, linkedPaymentId = 7), debt(2, linkedPaymentId = 7)), listOf(bill))
        }.exceptionOrNull()
        assertTrue(duplicate is IllegalArgumentException)

        val inactive = runCatching {
            snapshot(listOf(debt(1, linkedPaymentId = 7)), listOf(bill.copy(is_active = 0)))
        }.exceptionOrNull()
        assertTrue(inactive is IllegalArgumentException)
    }
}
