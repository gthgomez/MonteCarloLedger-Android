package com.montecarlo.ledger.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.montecarlo.ledger.AppTheme
import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.data.OnboardingProgress
import dev.chrisbanes.haze.HazeState
import org.junit.Rule
import org.junit.Test

class MonitoringModeIntroScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsFullScreenMonitoringModeHandoff() {
        composeRule.setContent {
            AppTheme {
                MonitoringModeIntroScreen(
                    uiState = AppUiState(
                        bankBalanceCents = 12_345,
                        ledgerBalanceCents = 12_100,
                        safeToSpendCents = 8_400,
                    ),
                    onboardingProgress = OnboardingProgress(
                        firstIncomeCompleted = true,
                        firstBillCompleted = true,
                        firstExpenseCompleted = true,
                        reconciliationCompleted = true,
                    ),
                    hazeState = HazeState(),
                    onContinue = {},
                )
            }
        }

        composeRule.onNodeWithText("Setup complete").assertIsDisplayed()
        composeRule.onNodeWithText("You're ready").assertIsDisplayed()
        composeRule.onNodeWithText("Enter dashboard").assertIsDisplayed()
    }
}
