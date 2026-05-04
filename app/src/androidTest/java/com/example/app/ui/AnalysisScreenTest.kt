package com.example.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.example.app.AppTheme
import com.example.app.AppUiState
import com.example.app.data.CategorySpend
import com.example.app.data.TransactionEntity
import com.example.app.processing.BalanceForecastRow
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AnalysisScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun analysisContent_showsOneScrollOfInsightsAndForecast() {
        val today = LocalDate.now()

        composeRule.setContent {
            AppTheme {
                AnalysisContent(
                    uiState = AppUiState(
                        totalInflowCents = 24_000,
                        totalOutflowCents = 13_500,
                        categorySpend = listOf(
                            CategorySpend(category = "Groceries", totalCents = -6_250),
                            CategorySpend(category = "Fuel", totalCents = -2_500),
                        ),
                        adjustments = listOf(
                            TransactionEntity(
                                id = 1,
                                description = "Balance correction",
                                amount_cents = 250,
                                date = today.minusDays(1).toString(),
                                type = "adjustment",
                            )
                        ),
                        forecastRows = listOf(
                            BalanceForecastRow(
                                date = today.plusDays(1),
                                balanceCents = 12_000,
                            ),
                            BalanceForecastRow(
                                date = today.plusDays(2),
                                balanceCents = 9_500,
                            ),
                        ),
                    )
                )
            }
        }

        composeRule.onNodeWithText("Analysis").assertIsDisplayed()
        composeRule.onNodeWithText("Cash flow overview").assertIsDisplayed()
        composeRule.onNodeWithText("Spending by category (30d)").assertIsDisplayed()
        composeRule.onNodeWithText("Recent manual adjustments").assertIsDisplayed()
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onNodeWithText("90-day forecast").assertIsDisplayed()
    }
}
