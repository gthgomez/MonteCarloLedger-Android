package com.example.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.example.app.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class BankBalanceDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bankBalanceDialog_savesTheEnteredBalance() {
        var savedAmount = -1

        composeRule.setContent {
            AppTheme {
                BankBalanceDialog(
                    initialAmountCents = 12_345,
                    onDismiss = {},
                    onConfirm = { savedAmount = it },
                )
            }
        }

        composeRule.onNodeWithText("Confirm bank balance").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performClick()
        composeRule.onNode(hasSetTextAction()).performTextClearance()
        composeRule.onNode(hasSetTextAction()).performTextInput("99.10")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle {
            assertEquals(9_910, savedAmount)
        }
    }
}
