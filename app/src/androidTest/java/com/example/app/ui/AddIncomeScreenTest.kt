package com.example.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.app.AppTheme
import com.example.app.data.IncomeEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class AddIncomeScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun addIncomeScreen_savesWithTheSimpleDefaultPath() {
        val saved = mutableStateOf<IncomeEntity?>(null)

        composeRule.setContent {
            AppTheme {
                AddIncomeScreen(onSave = { saved.value = it })
            }
        }

        composeRule.onNodeWithContentDescription("Income name input field").performTextInput("Paycheck")
        composeRule.onNodeWithText("Fixed amount").performClick()
        composeRule.onNodeWithContentDescription("Income amount input field").performTextInput("1200")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle {
            val income = saved.value
            assertNotNull(income)
            assertEquals("Paycheck", income!!.name)
            assertEquals(120_000, income.amount_cents)
            assertEquals("Weekly", income.frequency)
        }
    }
}
