package com.example.app.ui

import com.example.app.AppUiState
import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.IncomeEntity
import com.example.app.data.OnboardingProgress
import com.example.app.data.PaymentEntity
import com.example.app.data.SettingsEntity
import com.example.app.data.TransactionRuleEntity
import com.example.app.data.TransactionEntity
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupExportTest {

    @Test
    fun buildLedgerBackupJson_includesTheCoreRecordsAndSummary() {
        val json = buildLedgerBackupJson(
            exportedAtIso = "2026-04-22T12:00:00",
            uiState = AppUiState(
                bankBalanceCents = 12_345,
                ledgerBalanceCents = 11_900,
                isBalanceReconciled = true,
                safeToSpendCents = 9_000,
                incomeContributionCents = 500,
                dailyBudgetCents = 300,
                upcomingBillBurdenCents = 2_500,
                monteCarlo10thCents = 1_000,
                monteCarlo50thCents = 2_000,
                monteCarlo90thCents = 3_000,
                probabilityNegativePct = 7.5,
                projectedTroubleDateLabel = "2026-05-03",
                firstNegativeDateLabel = "2026-05-04",
                lowestBalanceDateLabel = "2026-05-05",
            ),
            incomes = listOf(
                IncomeEntity(
                    id = 1,
                    name = "Paycheck",
                    amount_cents = 120_000,
                    frequency = "Weekly",
                    day_of_month = 12,
                    next_date = "2026-04-29",
                    expectedAmountCents = 118_000,
                )
            ),
            payments = listOf(
                PaymentEntity(
                    id = 2,
                    name = "Rent",
                    amount_cents = 5_000,
                    frequency = "Monthly",
                    day_of_month = 1,
                    next_date = "2026-05-01",
                    is_active = 1,
                    isAutoWithdraw = false,
                )
            ),
            transactions = listOf(
                TransactionEntity(
                    id = 3,
                    description = "Coffee",
                    amount_cents = -450,
                    date = "2026-04-21",
                    type = "expense",
                )
            ),
            billOccurrences = listOf(
                BillOccurrenceEntity(
                    id = 4,
                    payment_id = 2,
                    due_date = "2026-05-01",
                    amount_cents = 5_000,
                    is_paid = 0,
                    transaction_id = null,
                    created_at = "2026-04-01T10:00:00",
                    original_due_date = "2026-05-01",
                    is_user_modified = 1,
                )
            ),
            settings = listOf(
                SettingsEntity("starting_balance", "50000"),
                SettingsEntity("simulation_days", "120"),
            ),
            rules = listOf(
                TransactionRuleEntity(
                    id = 1,
                    match_text = "netflix",
                    category = "subscriptions",
                    is_active = 1,
                    priority = 10,
                    created_at = "2026-04-22T12:00:00",
                )
            ),
            onboardingProgress = OnboardingProgress(
                firstIncomeCompleted = true,
                firstBillCompleted = true,
                firstExpenseCompleted = false,
                reconciliationCompleted = true,
            ),
        )

        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("\"exportedAt\": \"2026-04-22T12:00:00\""))
        assertTrue(json.contains("\"name\": \"Paycheck\""))
        assertTrue(json.contains("\"name\": \"Rent\""))
        assertTrue(json.contains("\"description\": \"Coffee\""))
        assertTrue(json.contains("\"due_date\": \"2026-05-01\""))
        assertTrue(json.contains("\"original_due_date\": \"2026-05-01\""))
        assertTrue(json.contains("\"is_user_modified\": true"))
        assertTrue(json.contains("\"isBalanceReconciled\": true"))
    }

    @Test
    fun parseLedgerBackupJson_roundTripsTheExportedCoreRecords() {
        val exported = buildLedgerBackupJson(
            exportedAtIso = "2026-04-22T12:00:00",
            uiState = AppUiState(
                bankBalanceCents = 12_345,
                ledgerBalanceCents = 11_900,
                isBalanceReconciled = true,
                safeToSpendCents = 9_000,
            ),
            incomes = listOf(
                IncomeEntity(
                    id = 1,
                    name = "Paycheck",
                    amount_cents = 120_000,
                    frequency = "Weekly",
                    day_of_month = 12,
                    next_date = "2026-04-29",
                    expectedAmountCents = 118_000,
                )
            ),
            payments = listOf(
                PaymentEntity(
                    id = 2,
                    name = "Rent",
                    amount_cents = 5_000,
                    frequency = "Monthly",
                    day_of_month = 1,
                    next_date = "2026-05-01",
                    is_active = 1,
                    isAutoWithdraw = false,
                )
            ),
            transactions = listOf(
                TransactionEntity(
                    id = 3,
                    description = "Coffee",
                    amount_cents = -450,
                    date = "2026-04-21",
                    type = "expense",
                )
            ),
            billOccurrences = listOf(
                BillOccurrenceEntity(
                    id = 4,
                    payment_id = 2,
                    due_date = "2026-05-01",
                    amount_cents = 5_000,
                    is_paid = 0,
                    transaction_id = null,
                    created_at = "2026-04-01T10:00:00",
                    original_due_date = "2026-05-01",
                    is_user_modified = 1,
                )
            ),
            settings = listOf(
                SettingsEntity("starting_balance", "50000"),
                SettingsEntity("simulation_days", "120"),
            ),
            rules = listOf(
                TransactionRuleEntity(
                    id = 1,
                    match_text = "netflix",
                    category = "subscriptions",
                    is_active = 1,
                    priority = 10,
                    created_at = "2026-04-22T12:00:00",
                )
            ),
            onboardingProgress = OnboardingProgress(
                firstIncomeCompleted = true,
                firstBillCompleted = false,
                firstExpenseCompleted = false,
                reconciliationCompleted = true,
            ),
        )

        val snapshot = parseLedgerBackupJson(exported)

        assertEquals(1, snapshot.schemaVersion)
        assertEquals("2026-04-22T12:00:00", snapshot.exportedAtIso)
        assertEquals(12_345, snapshot.bankBalanceCents)
        assertTrue(snapshot.isBalanceReconciled)
        assertEquals(1, snapshot.incomes.size)
        assertEquals("Paycheck", snapshot.incomes.single().name)
        assertEquals(1, snapshot.payments.size)
        assertEquals("Rent", snapshot.payments.single().name)
        assertEquals(1, snapshot.transactions.size)
        assertEquals("Coffee", snapshot.transactions.single().description)
        assertEquals(1, snapshot.billOccurrences.size)
        assertEquals(4, snapshot.billOccurrences.single().id)
        assertEquals("2026-05-01", snapshot.billOccurrences.single().original_due_date)
        assertEquals(1, snapshot.billOccurrences.single().is_user_modified)
        assertEquals(2, snapshot.settings.size)
        assertEquals("starting_balance", snapshot.settings[0].key)
        assertEquals("50000", snapshot.settings[0].value)
        assertEquals("simulation_days", snapshot.settings[1].key)
        assertEquals("120", snapshot.settings[1].value)
        assertEquals(1, snapshot.rules.size)
        assertEquals("netflix", snapshot.rules.single().match_text)
        assertTrue(snapshot.onboardingProgress.firstIncomeCompleted)
        assertTrue(snapshot.onboardingProgress.reconciliationCompleted)
    }
}
