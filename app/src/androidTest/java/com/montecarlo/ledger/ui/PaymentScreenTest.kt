package com.montecarlo.ledger.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createComposeRule
import com.montecarlo.ledger.AppTheme
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.processing.TimelineService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class PaymentScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addPaymentScreen_allowsChoosingARecurringFrequencyAndProjectsIntoOverview() {
        var saved = mutableStateOf<PaymentEntity?>(null)

        composeRule.setContent {
            AppTheme {
                AddPaymentScreen(
                    onSave = { saved.value = it },
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Bill name input field").performTextInput("Gym")
        composeRule.onNodeWithContentDescription("Bill amount input field").performTextInput("50")
        composeRule.onNodeWithContentDescription("Bill recurrence selector").performClick()
        composeRule.onNodeWithText("Weekly").performClick()
        composeRule.onNodeWithText("Auto-pay from bank").assertIsDisplayed()
        composeRule.onAllNodesWithText("Auto-pay from bank").assertCountEquals(1)
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle {
            val payment = saved.value
            assertNotNull(payment)
            assertEquals("Weekly", payment!!.frequency)
            assertTrue(
                TimelineService.generateTimeline(
                    incomes = emptyList(),
                    payments = listOf(payment),
                    startDate = LocalDate.now(),
                    daysAhead = 90,
                ).any { it.type == "bill" && it.description == "Gym" }
            )
        }
    }

    @Test
    fun editPaymentScreen_allowsChangingTheRecurringFrequency() {
        var saved = mutableStateOf<PaymentEntity?>(null)
        val payment = PaymentEntity(
            id = 7,
            name = "Streaming",
            amount_cents = 1_200,
            frequency = "Monthly",
            day_of_month = 15,
            next_date = LocalDate.now().plusDays(14).toString(),
            is_active = 1,
            isAutoWithdraw = true,
        )

        composeRule.setContent {
            AppTheme {
                EditPaymentScreen(
                    payment = payment,
                    onSave = { saved.value = it },
                    onDelete = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Bill recurrence selector").performClick()
        composeRule.onNodeWithText("Bi-weekly").performClick()
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle {
            val updated = saved.value
            assertNotNull(updated)
            assertEquals("Bi-weekly", updated!!.frequency)
            assertTrue(
                TimelineService.generateTimeline(
                    incomes = emptyList(),
                    payments = listOf(updated),
                    startDate = LocalDate.now(),
                    daysAhead = 90,
                ).any { it.type == "bill" && it.description == "Streaming" }
            )
        }
    }

    @Test
    fun addPaymentScreen_appliesRecurringCandidatePrefill() {
        composeRule.setContent {
            AppTheme {
                AddPaymentScreen(
                    initialDraft = BillPrefill(
                        name = "Streaming",
                        suggestedCategory = "subscriptions",
                        recurrence = "Monthly",
                        nextDate = "2026-09-15",
                    ),
                    onSave = {},
                    onDismiss = {},
                )
            }
        }

        composeRule.onNodeWithText("Streaming").assertIsDisplayed()
        composeRule.onNodeWithText("Suggested category: subscriptions").assertIsDisplayed()
        composeRule.onNodeWithText("Monthly").assertIsDisplayed()
    }
}
