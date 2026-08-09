package com.montecarlo.ledger.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.montecarlo.ledger.AppTheme
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IncomeBillForecastEndToEndTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        runBlocking {
            withContext(Dispatchers.IO) {
                AppDatabase.getInstance(app).clearAllTables()
            }
        }
        viewModel = MainViewModel(app)

        composeRule.setContent {
            AppTheme {
                AppView(viewModel = viewModel)
            }
        }
    }

    @Test
    fun addingIncomeAndBillPersistsAndFeedsTheForecast() {
        composeRule.onNodeWithText("Add paycheck first")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithText("Fixed amount").performClick()
        composeRule.onNodeWithContentDescription("Income name input field")
            .performTextInput("Paycheck")
        composeRule.onNodeWithContentDescription("Income amount input field")
            .performTextInput("1200")
        composeRule.onNodeWithContentDescription("Save income entry").performClick()

        waitForViewModelState("Paycheck should be persisted") {
            viewModel.allIncome.value.any { it.name == "Paycheck" && it.amount_cents == 120_000 }
        }

        composeRule.onNodeWithText("Add a bill first")
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithContentDescription("Bill name input field")
            .performTextInput("Rent")
        composeRule.onNodeWithContentDescription("Bill amount input field")
            .performTextInput("50")
        composeRule.onNodeWithContentDescription("Bill recurrence selector").performClick()
        composeRule.onNodeWithText("One-time").performClick()
        composeRule.onNodeWithText("Save").performClick()

        waitForViewModelState("Rent bill should be persisted") {
            viewModel.allPayments.value.any { it.name == "Rent" && it.amount_cents == 5_000 }
        }

        composeRule.onNodeWithText("Done").performClick()

        waitForViewModelState("Forecast should include the saved income and bill") {
            viewModel.uiState.value.upcomingBillBurdenCents == 5_000 &&
                viewModel.uiState.value.forecastRows.any { it.balanceCents >= 120_000 }
        }

        composeRule.onNodeWithText("Rent", substring = true)
            .performScrollTo()
            .assertIsDisplayed()

        val uiState = viewModel.uiState.value
        assertEquals(1, viewModel.allIncome.value.size)
        assertEquals(1, viewModel.allPayments.value.size)
        assertEquals(5_000, uiState.upcomingBillBurdenCents)
        assertTrue("Forecast should surface the bill", uiState.upcomingBills.any { it.contains("Rent") })
        assertTrue(
            "Forecast should include projected income",
            uiState.forecastRows.any { it.balanceCents >= 120_000 }
        )
        assertTrue("Forecast rows should be generated", uiState.forecastRows.isNotEmpty())
    }

    private fun waitForViewModelState(
        message: String,
        timeoutMillis: Long = 10_000,
        condition: () -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + timeoutMillis
        var met = false
        while (!met && SystemClock.uptimeMillis() < deadline) {
            composeRule.runOnIdle {
                met = condition()
            }
            if (!met) {
                SystemClock.sleep(50)
            }
        }
        assertTrue(message, met)
    }
}
