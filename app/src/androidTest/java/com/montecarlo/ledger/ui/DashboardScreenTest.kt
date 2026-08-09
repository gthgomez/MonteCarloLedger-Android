package com.montecarlo.ledger.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.montecarlo.ledger.AppTheme
import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.DashboardWidget
import com.montecarlo.ledger.data.OnboardingProgress
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun balanceCard_showsReconciledBankBalanceAndForecastSeed() {
        composeRule.setContent {
            AppTheme {
                DashboardContent(
                    uiState = AppUiState(
                        bankBalanceCents = 12_345,
                        ledgerBalanceCents = 11_900,
                        isBalanceReconciled = true,
                        safeToSpendCents = 9_000,
                        totalInflowCents = 24_000,
                        upcomingBills = listOf("Rent (Apr 30)"),
                        nextPaydayLabel = "Next: Apr 25 (5d)",
                        upcomingBillBurdenCents = 2_500,
                        dashboardConfig = DashboardConfig(visibleWidgets = setOf(DashboardWidget.Balance)),
                    ),
                    mismatch = false,
                    details = null,
                    onboardingProgress = OnboardingProgress(
                        firstIncomeCompleted = true,
                        firstBillCompleted = true,
                        firstGoalCompleted = true,
                        reconciliationCompleted = true,
                    ),
                    onDismissMismatch = {},
                    onConfirmMismatch = {},
                    onAddIncome = {},
                    onAddPayment = {},
                    onAddTransaction = {},
                    forcedWidthClass = WindowWidthClass.Compact,
                )
            }
        }

        composeRule.onNodeWithTag(DashboardTestTags.BALANCE_CARD).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("\$123.45").assertCountEquals(1)
        composeRule.onAllNodesWithText("App total").assertCountEquals(1)
        composeRule.onAllNodesWithText("\$119.00").assertCountEquals(1)
        composeRule.onAllNodesWithText("Starting point").assertCountEquals(1)
        composeRule.onAllNodesWithText("This is the bank balance you confirmed.").assertCountEquals(1)
        composeRule.onNodeWithContentDescription("How to read these numbers").performClick()
        composeRule.onNodeWithText("What your bank says you have right now. Update this after each paycheck or large purchase.").assertIsDisplayed()
        composeRule.onNodeWithText("What the app calculates by adding up all the paychecks, bills, and spending you've recorded.").assertIsDisplayed()
        composeRule.onNodeWithText("Where the 3-month estimate starts. Uses your confirmed bank balance if available, otherwise uses the app total.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Scheduled bills in forecast: \$25.00").assertCountEquals(1)
        composeRule.onAllNodesWithText("Okay to spend today: \$90.00").assertCountEquals(1)
    }

    @Test
    fun balanceCard_showsUnconfirmedBankBalanceAndLedgerSeed() {
        composeRule.setContent {
            AppTheme {
                DashboardContent(
                    uiState = AppUiState(
                        bankBalanceCents = 12_345,
                        ledgerBalanceCents = 11_900,
                        isBalanceReconciled = false,
                        safeToSpendCents = 7_500,
                        totalInflowCents = 24_000,
                        upcomingBills = listOf("Rent (Apr 30)"),
                        nextPaydayLabel = "Next: Apr 25 (5d)",
                        upcomingBillBurdenCents = 0,
                        dashboardConfig = DashboardConfig(visibleWidgets = setOf(DashboardWidget.Balance)),
                    ),
                    mismatch = false,
                    details = null,
                    onboardingProgress = OnboardingProgress(
                        firstIncomeCompleted = true,
                    ),
                    onDismissMismatch = {},
                    onConfirmMismatch = {},
                    onAddIncome = {},
                    onAddPayment = {},
                    onAddTransaction = {},
                    forcedWidthClass = WindowWidthClass.Expanded,
                )
            }
        }

        composeRule.onNodeWithTag(DashboardTestTags.BALANCE_CARD).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Bank balance not confirmed").assertCountEquals(1)
        composeRule.onAllNodesWithText("\$123.45").assertCountEquals(1)
        composeRule.onAllNodesWithText("Forecasts use your app balance until you confirm a bank balance.").assertCountEquals(1)
        composeRule.onAllNodesWithText("App total").assertCountEquals(1)
        composeRule.onNodeWithText("\$119.00").assertIsDisplayed()
        composeRule.onAllNodesWithText("Starting point").assertCountEquals(1)
        composeRule.onAllNodesWithText("Okay to spend today: \$75.00").assertCountEquals(1)
    }

    @Test
    fun reconciliationDialog_showsMismatchDetailsAndActions() {
        composeRule.setContent {
            AppTheme {
                DashboardContent(
                    uiState = AppUiState(
                        bankBalanceCents = 5_000,
                        ledgerBalanceCents = 7_000,
                        isBalanceReconciled = true,
                        safeToSpendCents = 1_000,
                    ),
                    mismatch = true,
                    details = 7_000 to 5_000,
                    onboardingProgress = OnboardingProgress(
                        firstIncomeCompleted = true,
                        firstBillCompleted = true,
                        firstGoalCompleted = true,
                        reconciliationCompleted = true,
                    ),
                    onDismissMismatch = {},
                    onConfirmMismatch = {},
                    onAddIncome = {},
                    onAddPayment = {},
                    onAddTransaction = {},
                )
            }
        }

        composeRule.onNodeWithTag(DashboardTestTags.RECONCILIATION_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithText("Your bank balance needs a review").assertIsDisplayed()
        composeRule.onNodeWithText("App balance: \$70.00").assertIsDisplayed()
        composeRule.onNodeWithText("Saved bank balance: \$50.00").assertIsDisplayed()
        composeRule.onNodeWithText("The app is using your saved bank balance as the starting point for forecasts. If the app balance is the correct number, use it to update the saved balance.").assertIsDisplayed()
        composeRule.onNodeWithText("Use App Balance").assertIsDisplayed()
        composeRule.onNodeWithText("Review Later").assertIsDisplayed()
    }

    @Test
    fun reconciliationDialog_fixItButtonConfirmsAndDismissesTheDialog() {
        composeRule.setContent {
            AppTheme {
                var mismatch by remember { mutableStateOf(true) }
                var details by remember { mutableStateOf<Pair<Int, Int>?>(7_000 to 5_000) }
                DashboardContent(
                    uiState = AppUiState(
                        bankBalanceCents = 5_000,
                        ledgerBalanceCents = 7_000,
                        isBalanceReconciled = true,
                        safeToSpendCents = 1_000,
                    ),
                    mismatch = mismatch,
                    details = details,
                    onboardingProgress = OnboardingProgress(
                        firstIncomeCompleted = true,
                        firstBillCompleted = true,
                        firstGoalCompleted = true,
                        reconciliationCompleted = true,
                    ),
                    onDismissMismatch = {
                        mismatch = false
                        details = null
                    },
                    onConfirmMismatch = {
                        mismatch = false
                        details = null
                    },
                    onAddIncome = {},
                    onAddPayment = {},
                    onAddTransaction = {},
                )
            }
        }

        composeRule.onNodeWithText("Use App Balance").performClick()
        composeRule.onAllNodesWithText("Your bank balance needs a review").assertCountEquals(0)
        composeRule.onAllNodesWithText("Use App Balance").assertCountEquals(0)
    }

    @Test
    fun emptyDashboard_showsFirstRunGuidance() {
        composeRule.setContent {
            AppTheme {
                DashboardContent(
                    uiState = AppUiState(),
                    mismatch = false,
                    details = null,
                    onboardingProgress = OnboardingProgress(),
                    onDismissMismatch = {},
                    onConfirmMismatch = {},
                    onAddIncome = {},
                    onAddPayment = {},
                    onAddTransaction = {},
                )
            }
        }

        composeRule.onAllNodesWithTag(DashboardTestTags.BALANCE_CARD).assertCountEquals(0)
        composeRule.onNodeWithText("Step 1 of 4").assertIsDisplayed()
        composeRule.onNodeWithText("What does your bank account say right now?").assertIsDisplayed()
        composeRule.onNodeWithText("Enter your current bank balance. This grounds every number — the forecast starts from your real balance, not zero.").assertIsDisplayed()
        composeRule.onNodeWithText("Enter bank balance").assertIsDisplayed()
    }

    @Test
    fun partialSetup_showsForecastCardsWithIncomeOrBills() {
        composeRule.setContent {
            AppTheme {
                DashboardContent(
                    uiState = AppUiState(
                        totalInflowCents = 24_000,
                        dashboardConfig = DashboardConfig(
                            visibleWidgets = setOf(DashboardWidget.PlanAhead)
                        ),
                    ),
                    mismatch = false,
                    details = null,
                    onboardingProgress = OnboardingProgress(
                        reconciliationCompleted = true,
                        firstIncomeCompleted = true,
                    ),
                    onDismissMismatch = {},
                    onConfirmMismatch = {},
                    onAddIncome = {},
                    onAddPayment = {},
                    onAddTransaction = {},
                )
            }
        }

        composeRule.onNodeWithText("Step 3 of 4").assertIsDisplayed()
        composeRule.onAllNodesWithText("Add your first bill").assertCountEquals(2)
        composeRule.onNodeWithText("Add rent, a subscription, or any regular payment. More bills can be added any time.").assertIsDisplayed()
        composeRule.onNodeWithText("Until next payday").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Upcoming Bills").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun expandedLayout_doesNotDuplicateTheBalanceCardDuringOnboarding() {
        composeRule.setContent {
            AppTheme {
                DashboardContent(
                    uiState = AppUiState(
                        totalInflowCents = 24_000,
                        dashboardConfig = DashboardConfig(visibleWidgets = emptySet()),
                    ),
                    mismatch = false,
                    details = null,
                    onboardingProgress = OnboardingProgress(
                        reconciliationCompleted = true,
                        firstIncomeCompleted = true,
                    ),
                    onDismissMismatch = {},
                    onConfirmMismatch = {},
                    onAddIncome = {},
                    onAddPayment = {},
                    onAddTransaction = {},
                    forcedWidthClass = WindowWidthClass.Expanded,
                )
            }
        }

        composeRule.onNodeWithText("Step 3 of 4").assertIsDisplayed()
        composeRule.onAllNodesWithText("Add your first bill").assertCountEquals(2)
        composeRule.onAllNodesWithTag(DashboardTestTags.BALANCE_CARD).assertCountEquals(0)
    }

    @Test
    fun completedSetup_showsMonitoringModeFinishCard() {
        composeRule.setContent {
            AppTheme {
                DashboardContent(
                    uiState = AppUiState(
                        bankBalanceCents = 12_345,
                        ledgerBalanceCents = 12_100,
                        isBalanceReconciled = true,
                        safeToSpendCents = 8_400,
                        totalInflowCents = 24_000,
                        upcomingBills = listOf("Rent (Apr 30)"),
                        upcomingBillBurdenCents = 2_500,
                        dashboardConfig = DashboardConfig(
                            visibleWidgets = setOf(DashboardWidget.Monitoring, DashboardWidget.Balance)
                        ),
                    ),
                    mismatch = false,
                    details = null,
                    onboardingProgress = OnboardingProgress(
                        firstIncomeCompleted = true,
                        firstBillCompleted = true,
                        firstGoalCompleted = true,
                        reconciliationCompleted = true,
                    ),
                    onDismissMismatch = {},
                    onConfirmMismatch = {},
                    onAddIncome = {},
                    onAddPayment = {},
                    onAddTransaction = {},
                    forcedWidthClass = WindowWidthClass.Compact,
                )
            }
        }

        composeRule.onNodeWithText("Setup complete").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Tracking is on").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("The app will watch your balance and forecast without the setup checklist.").assertIsDisplayed()
        composeRule.onAllNodesWithText("Bank balance").assertCountEquals(4)
        composeRule.onAllNodesWithText("App total").assertCountEquals(2)
        composeRule.onNodeWithContentDescription("How to read these numbers").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("Starting point").assertCountEquals(2)
        composeRule.onNodeWithText("Okay to spend today").assertIsDisplayed()
        composeRule.onAllNodesWithText("Log paycheck").assertCountEquals(0)
        composeRule.onAllNodesWithText("Add bill").assertCountEquals(0)
        composeRule.onAllNodesWithText("Record spending").assertCountEquals(0)
    }

    @Test
    fun onboardingCard_checkBankBalanceButtonFiresCallback() {
        var checkBalanceClicked = false

        composeRule.setContent {
            AppTheme {
                DashboardContent(
                    uiState = AppUiState(
                        bankBalanceCents = 12_345,
                        ledgerBalanceCents = 12_100,
                        isBalanceReconciled = false,
                        safeToSpendCents = 8_400,
                    ),
                    mismatch = false,
                    details = null,
                    onboardingProgress = OnboardingProgress(
                        firstIncomeCompleted = true,
                        firstBillCompleted = true,
                        firstGoalCompleted = true,
                    ),
                    onDismissMismatch = {},
                    onConfirmMismatch = {},
                    onCheckBalance = { checkBalanceClicked = true },
                    onAddIncome = {},
                    onAddPayment = {},
                    onAddTransaction = {},
                )
            }
        }

        composeRule.onNodeWithText("Enter bank balance").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertTrue(checkBalanceClicked)
        }
    }
}
