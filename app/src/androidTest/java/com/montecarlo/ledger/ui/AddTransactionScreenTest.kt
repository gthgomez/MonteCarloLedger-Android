package com.montecarlo.ledger.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.montecarlo.ledger.AppTheme
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.PaymentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AddTransactionScreenTest {

    private data class SavedTransaction(
        val description: String,
        val amountCents: Int,
        val linkedOccurrenceId: Int?,
        val category: String,
        val date: String
    )

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addTransactionScreen_canLinkAMatchingBillOccurrence() {
        val saved = mutableStateOf<SavedTransaction?>(null)
        val today = LocalDate.now()
        val payment = PaymentEntity(
            id = 10,
            name = "Rent",
            amount_cents = 5_000,
            frequency = "Monthly",
            day_of_month = 21,
            next_date = today.toString(),
            is_active = 1,
            isAutoWithdraw = false,
        )
        val occurrence = BillOccurrenceEntity(
            id = 44,
            payment_id = payment.id,
            due_date = today.toString(),
            amount_cents = 5_000,
            is_paid = 0,
        )

        composeRule.setContent {
            AppTheme {
                AddTransactionScreen(
                    payments = listOf(payment),
                    billOccurrences = listOf(occurrence),
                    externalErrorMessage = null,
                    onSave = { description, amountCents, type, linkedOccurrenceId, category, date ->
                        saved.value = SavedTransaction(description, amountCents, linkedOccurrenceId, category, date)
                    }
                )
            }
        }

        composeRule.onNodeWithText("What was this for?").performTextInput("Rent")
        composeRule.onNodeWithText("Amount ($)").performTextInput("50")
        composeRule.onNodeWithContentDescription("Bill link selector").performClick()
        composeRule.onNodeWithText("Rent • due", substring = true).performClick()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle {
            val result = saved.value
            assertNotNull(result)
            assertEquals("Rent", result!!.description)
            assertEquals(-5_000, result.amountCents)
            assertEquals(44, result.linkedOccurrenceId)
            assertEquals("", result.category)
            assertEquals(today.toString(), result.date)
        }
    }

    @Test
    fun addTransactionScreen_hidesAdvancedTypesByDefault() {
        composeRule.setContent {
            AppTheme {
                AddTransactionScreen(
                    payments = emptyList(),
                    billOccurrences = emptyList(),
                    externalErrorMessage = null,
                    onSave = { _, _, _, _, _, _ -> }
                )
            }
        }

        composeRule.onNodeWithText("Show advanced entry types").assertIsDisplayed()
        composeRule.onAllNodesWithText("Adjustment").assertCountEquals(0)
    }
}
