package com.example.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.app.AppTheme
import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.PaymentEntity
import com.example.app.data.TransactionEntity
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TransactionHistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun transactionHistory_showsTheClosedBillForLinkedExpenses() {
        val today = LocalDate.now()
        val payment = PaymentEntity(
            id = 1,
            name = "Rent",
            amount_cents = 5_000,
            frequency = "Monthly",
            day_of_month = 21,
            next_date = today.toString(),
            is_active = 1,
            isAutoWithdraw = false,
        )
        val transaction = TransactionEntity(
            id = 7,
            description = "Rent payment",
            amount_cents = -5_000,
            date = today.toString(),
            type = "expense",
        )
        val occurrence = BillOccurrenceEntity(
            id = 44,
            payment_id = payment.id,
            due_date = today.toString(),
            amount_cents = 5_000,
            is_paid = 1,
            transaction_id = transaction.id,
        )

        composeRule.setContent {
            AppTheme {
                TransactionHistoryContent(
                    transactions = listOf(transaction),
                    payments = listOf(payment),
                    billOccurrences = listOf(occurrence),
                    onEditTransaction = {}
                )
            }
        }

        composeRule.onNodeWithText("Rent payment").assertIsDisplayed()
        composeRule.onNodeWithText("Closed bill: Rent • due ${today.formatDateDisplay()}").assertIsDisplayed()
    }
}
