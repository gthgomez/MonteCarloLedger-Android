package com.example.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.app.AppTheme
import com.example.app.data.PaymentEntity
import com.example.app.data.TransactionEntity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CsvImportDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun csvImportDialog_showsPreviewAndInvokesImport() {
        var clicked = false
        val preview = CsvImportPreview(
            sourceName = "bank.csv",
            importedTransactions = listOf(
                TransactionEntity(
                    description = "Groceries",
                    amount_cents = -2_500,
                    date = "2026-04-10",
                    type = "expense",
                )
            ),
            skippedRows = 1,
            totalRows = 2,
        )

        composeRule.setContent {
            AppTheme {
                CsvImportSheetContent(
                    preview = preview,
                    onDismiss = {},
                    onImport = { _: CsvImportPreview -> clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Import CSV").assertIsDisplayed()
        composeRule.onNodeWithText("Import").performClick()

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }

    @Test
    fun billCsvImportDialog_showsPreviewAndInvokesImport() {
        var clicked = false
        val preview = BillCsvImportPreview(
            sourceName = "bills.csv",
            importedPayments = listOf(
                PaymentEntity(
                    name = "Rent",
                    amount_cents = 150_000,
                    frequency = "Monthly",
                    day_of_month = 30,
                    next_date = "2026-04-30",
                    is_active = 1,
                    isAutoWithdraw = true,
                )
            ),
            skippedRows = 0,
            totalRows = 1,
        )

        composeRule.setContent {
            AppTheme {
                BillCsvImportSheetContent(
                    preview = preview,
                    onDismiss = {},
                    onImport = { _: BillCsvImportPreview -> clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Import bills CSV").assertIsDisplayed()
        composeRule.onNodeWithText("Import").performClick()

        composeRule.runOnIdle {
            assertTrue(clicked)
        }
    }
}
