package com.montecarlo.ledger.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.montecarlo.ledger.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DestructiveVaultPullConfirmDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cancel_doesNotInvokeReplace() {
        var confirmCount = 0
        composeRule.setContent {
            AppTheme {
                DestructiveVaultPullConfirmDialog(
                    onConfirmReplace = { confirmCount++ },
                    onCancel = { },
                )
            }
        }
        composeRule.onNodeWithText(VaultDestructivePullMessaging.OVERWRITE_DIALOG_TITLE).assertIsDisplayed()
        composeRule.onNodeWithText("category budgets", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle { assertEquals(0, confirmCount) }
    }

    @Test
    fun confirmReplace_invokesCallback() {
        var confirmCount = 0
        composeRule.setContent {
            AppTheme {
                DestructiveVaultPullConfirmDialog(
                    onConfirmReplace = { confirmCount++ },
                    onCancel = { },
                )
            }
        }
        composeRule.onNodeWithText("Replace and pull").performClick()
        composeRule.runOnIdle { assertEquals(1, confirmCount) }
    }
}
