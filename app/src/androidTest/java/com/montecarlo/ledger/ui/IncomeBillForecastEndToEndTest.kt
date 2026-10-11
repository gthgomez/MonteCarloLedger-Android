package com.montecarlo.ledger.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.montecarlo.ledger.AppTheme
import com.montecarlo.ledger.MainViewModel
import com.montecarlo.ledger.data.AppDatabase
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
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java).build()
        viewModel = MainViewModel(app, db)

        composeRule.setContent {
            AppTheme {
                AppView(viewModel = viewModel)
            }
        }
    }

    @Test
    fun addingIncomeAndBillPersistsAndFeedsTheForecast() {
        // AppView shows the dashboard's shimmer loading body until the ViewModel's first read from
        // the in-memory database lands, so wait for the real dashboard before interacting with it.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Add paycheck first").fetchSemanticsNodes().isNotEmpty()
        }

        // On a phone-sized viewport the dashboard is a LazyColumn, so targets below the fold (the
        // upcoming-bills list especially) are not composed yet and `performScrollTo()` cannot find
        // them. Scroll the lazy container to each target instead, the way the other instrumented
        // tests in HEAD do.
        val dashboardList = composeRule.onNode(hasScrollAction() and hasScrollToIndexAction())

        dashboardList.performScrollToNode(hasText("Add paycheck first"))
        composeRule.onNodeWithText("Add paycheck first")
            .performClick()

        composeRule.onNodeWithText("Fixed amount").performClick()
        composeRule.onNodeWithContentDescription("Income name input field")
            .performTextInput("Paycheck")
        composeRule.onNodeWithContentDescription("Income amount input field")
            .performTextInput("1200")
        composeRule.onNodeWithContentDescription("Save income entry").performClick()

        waitForViewModelState("Paycheck should be persisted") {
            viewModel.allIncome.value.any { it.name == "Paycheck" && it.amount_cents == 120_000L }
        }

        dashboardList.performScrollToNode(hasText("Add a bill first"))
        composeRule.onNodeWithText("Add a bill first")
            .performClick()

        composeRule.onNodeWithContentDescription("Bill name input field")
            .performTextInput("Rent")
        composeRule.onNodeWithContentDescription("Bill amount input field")
            .performTextInput("50")
        composeRule.onNodeWithContentDescription("Bill recurrence selector").performClick()
        composeRule.onNodeWithText("One-time").performClick()
        composeRule.onNodeWithText("Save").performClick()

        waitForViewModelState("Rent bill should be persisted") {
            viewModel.allPayments.value.any { it.name == "Rent" && it.amount_cents == 5_000L }
        }

        composeRule.onNodeWithText("Done").performClick()

        waitForViewModelState("Forecast should include the saved income and bill") {
            viewModel.uiState.value.upcomingBillBurdenCents == 5_000L &&
                viewModel.uiState.value.forecastRows.any { it.balanceCents >= 120_000L }
        }

        dashboardList.performScrollToNode(hasText("Rent", substring = true))
        composeRule.onNodeWithText("Rent", substring = true)
            .assertIsDisplayed()

        val uiState = viewModel.uiState.value
        assertEquals(1, viewModel.allIncome.value.size)
        assertEquals(1, viewModel.allPayments.value.size)
        assertEquals(5_000L, uiState.upcomingBillBurdenCents)
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
