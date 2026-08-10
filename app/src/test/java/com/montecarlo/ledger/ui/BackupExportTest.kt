package com.montecarlo.ledger.ui

import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.data.AssetEntity
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.SettingsEntity
import com.montecarlo.ledger.data.TransactionRuleEntity
import com.montecarlo.ledger.data.TransactionEntity
import org.junit.Assert.assertFalse
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
                debts = listOf(DebtEntity(7L, "Visa", 250_000L, 1_850, 10_000L, 15, 2, true)),
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
                    payType = "HOURLY",
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
            assets = listOf(
                AssetEntity(
                    id = 9,
                    name = "Emergency fund",
                    type = "Cash",
                    balanceCents = 500_00L,
                    lastUpdated = "2026-04-22",
                )
            ),
            goals = listOf(
                GoalEntity(
                    id = 8,
                    name = "Vacation",
                    targetAmountCents = 200_000,
                    currentAmountCents = 50_000,
                    deadline = "2026-12-01",
                    createdAt = "2026-01-01",
                )
            ),
            onboardingProgress = OnboardingProgress(
                firstIncomeCompleted = true,
                firstBillCompleted = true,
                firstExpenseCompleted = false,
                firstGoalCompleted = true,
                reconciliationCompleted = true,
            ),
        )

        assertTrue(json.contains("\"schemaVersion\": 3"))
        assertTrue(json.contains("\"exportedAt\": \"2026-04-22T12:00:00\""))
        assertTrue(json.contains("\"name\": \"Paycheck\""))
        assertTrue(json.contains("\"payType\": \"HOURLY\""))
        assertTrue(json.contains("\"name\": \"Rent\""))
        assertTrue(json.contains("\"description\": \"Coffee\""))
        assertTrue(json.contains("\"due_date\": \"2026-05-01\""))
        assertTrue(json.contains("\"original_due_date\": \"2026-05-01\""))
        assertTrue(json.contains("\"is_user_modified\": true"))
        assertTrue(json.contains("\"isBalanceReconciled\": true"))
        assertTrue(json.contains("\"name\": \"Emergency fund\""))
        assertTrue(json.contains("\"name\": \"Vacation\""))
        assertTrue(json.contains("\"aprBasisPoints\": 1850"))
        assertTrue(json.contains("\"firstGoalCompleted\": true"))
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
                debts = listOf(DebtEntity(7L, "Visa", 250_000L, 1_850, 10_000L, 15, 2, true)),
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
                    payType = "FLAT",
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
            assets = listOf(
                AssetEntity(
                    id = 9,
                    name = "Brokerage",
                    type = "Stock",
                    balanceCents = 1_000_00L,
                    lastUpdated = "2026-04-22",
                )
            ),
            goals = listOf(
                GoalEntity(
                    id = 8,
                    name = "Car",
                    targetAmountCents = 500_000,
                    currentAmountCents = 100_000,
                    deadline = null,
                    createdAt = "2026-02-01",
                )
            ),
            onboardingProgress = OnboardingProgress(
                firstIncomeCompleted = true,
                firstBillCompleted = false,
                firstExpenseCompleted = false,
                firstGoalCompleted = true,
                reconciliationCompleted = true,
            ),
        )

        val snapshot = parseLedgerBackupJson(exported)

        assertEquals(3, snapshot.schemaVersion)
        assertEquals("2026-04-22T12:00:00", snapshot.exportedAtIso)
        assertEquals(12_345, snapshot.bankBalanceCents)
        assertTrue(snapshot.isBalanceReconciled)
        assertEquals(1, snapshot.incomes.size)
        assertEquals("Paycheck", snapshot.incomes.single().name)
        assertEquals("FLAT", snapshot.incomes.single().payType)
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
        assertEquals(1, snapshot.assets.size)
        assertEquals("Brokerage", snapshot.assets.single().name)
        assertEquals(1_000_00L, snapshot.assets.single().balanceCents)
        assertEquals(1, snapshot.goals.size)
        assertEquals("Car", snapshot.goals.single().name)
        assertTrue(snapshot.onboardingProgress.firstIncomeCompleted)
        assertTrue(snapshot.onboardingProgress.firstGoalCompleted)
        assertTrue(snapshot.onboardingProgress.reconciliationCompleted)
        assertEquals(1, snapshot.debts.size)
        assertEquals("Visa", snapshot.debts.single().name)
        assertEquals(1_850, snapshot.debts.single().aprBasisPoints)
    }

    @Test
    fun buildLedgerBackupJson_includesCategoryBudgetsWhenProvided() {
        val json = buildLedgerBackupJson(
            exportedAtIso = "2026-04-22T12:00:00",
            uiState = AppUiState(),
            incomes = emptyList(),
            payments = emptyList(),
            transactions = emptyList(),
            billOccurrences = emptyList(),
            settings = emptyList(),
            rules = emptyList(),
            onboardingProgress = OnboardingProgress(),
            categoryBudgets = listOf(
                com.montecarlo.ledger.data.CategoryBudgetEntity(
                    id = 1,
                    category = "dining",
                    limitCents = 50_000,
                    enabled = 1,
                    createdAt = "2026-04-01",
                )
            ),
        )

        assertTrue(json.contains("\"categoryBudgets\""))
        assertTrue(json.contains("\"category\": \"dining\""))
        assertTrue(json.contains("\"limitCents\": 50000"))
    }

    @Test
    fun parseLedgerBackupJson_roundTripsCategoryBudgets() {
        val exported = buildLedgerBackupJson(
            exportedAtIso = "2026-04-22T12:00:00",
            uiState = AppUiState(),
            incomes = emptyList(),
            payments = emptyList(),
            transactions = emptyList(),
            billOccurrences = emptyList(),
            settings = emptyList(),
            rules = emptyList(),
            assets = emptyList(),
            goals = emptyList(),
            categoryBudgets = listOf(
                com.montecarlo.ledger.data.CategoryBudgetEntity(
                    id = 3,
                    category = "groceries",
                    limitCents = 80_000,
                    enabled = 0,
                    createdAt = "2026-03-15",
                ),
                com.montecarlo.ledger.data.CategoryBudgetEntity(
                    id = 4,
                    category = "dining",
                    limitCents = 50_000,
                    enabled = 1,
                    createdAt = "2026-04-01",
                ),
            ),
            onboardingProgress = OnboardingProgress(),
        )

        val snapshot = parseLedgerBackupJson(exported)

        assertEquals(2, snapshot.categoryBudgets.size)
        val dining = snapshot.categoryBudgets.first { it.category == "dining" }
        assertEquals(50_000, dining.limitCents)
        assertEquals(1, dining.enabled)
        val groceries = snapshot.categoryBudgets.first { it.category == "groceries" }
        assertEquals(80_000, groceries.limitCents)
        assertEquals(0, groceries.enabled)
    }

    @Test
    fun parseLedgerBackupJson_acceptsLegacySchema1WithoutAssetsOrGoals() {
        val legacy = """
            {
              "schemaVersion": 1,
              "exportedAt": "2026-01-01T00:00:00",
              "summary": {
                "bankBalanceCents": 100,
                "isBalanceReconciled": false
              },
              "onboarding": {
                "firstIncomeCompleted": true,
                "firstBillCompleted": false,
                "firstExpenseCompleted": false,
                "reconciliationCompleted": false
              },
              "settings": [],
              "rules": [],
              "incomes": [
                {
                  "id": 1,
                  "name": "Job",
                  "amount_cents": 1000,
                  "frequency": "weekly",
                  "day_of_month": null,
                  "next_date": "2026-01-08",
                  "expectedAmountCents": null
                }
              ],
              "payments": [],
              "transactions": [],
              "billOccurrences": []
            }
        """.trimIndent()

        val snapshot = parseLedgerBackupJson(legacy)
        assertEquals(1, snapshot.schemaVersion)
        assertEquals(1, snapshot.incomes.size)
        assertEquals("FLAT", snapshot.incomes.single().payType)
        assertTrue(snapshot.assets.isEmpty())
        assertTrue(snapshot.goals.isEmpty())
    }

    @Test
    fun integrityField_insertAndStrip_roundTripsWithRealBackupJson() {
        val json = buildLedgerBackupJson(
            exportedAtIso = "2026-07-19T12:00:00",
            uiState = com.montecarlo.ledger.AppUiState(bankBalanceCents = 5000),
            incomes = listOf(
                IncomeEntity(
                    id = 1, name = "Job", amount_cents = 100000,
                    frequency = "Monthly", day_of_month = 15,
                    next_date = "2026-08-01", expectedAmountCents = 100000,
                    payType = "FLAT",
                )
            ),
            payments = emptyList(),
            transactions = emptyList(),
            billOccurrences = emptyList(),
            settings = emptyList(),
            rules = emptyList(),
            onboardingProgress = OnboardingProgress(),
        )

        val hmac = "testHmacBase64Value+/="
        val withIntegrity = com.montecarlo.ledger.security.SecurityUtils.insertIntegrityField(json, hmac)

        // Integrity field should be present
        assertTrue(withIntegrity.contains("\"integrity\""))
        assertTrue(withIntegrity.contains(hmac))

        // The JSON should still parse correctly (parseLedgerBackupJson ignores integrity)
        val snapshot = parseLedgerBackupJson(withIntegrity)
        assertEquals(3, snapshot.schemaVersion)
        assertEquals(1, snapshot.incomes.size)
        assertEquals("Job", snapshot.incomes.single().name)

        // Strip integrity and verify it's gone
        val stripped = com.montecarlo.ledger.security.SecurityUtils.stripIntegrityField(withIntegrity)
        assertFalse(stripped.contains("\"integrity\""))

        // Stripped JSON should still parse correctly
        val strippedSnapshot = parseLedgerBackupJson(stripped)
        assertEquals(snapshot.schemaVersion, strippedSnapshot.schemaVersion)
        assertEquals(snapshot.incomes.size, strippedSnapshot.incomes.size)
        assertEquals(snapshot.incomes.single().name, strippedSnapshot.incomes.single().name)
    }

    @Test
    fun integrityField_doesNotBreakLegacySchema1Parsing() {
        val legacy = """
            {
              "schemaVersion": 1,
              "exportedAt": "2026-01-01T00:00:00",
              "summary": { "bankBalanceCents": 100, "isBalanceReconciled": false },
              "onboarding": { "firstIncomeCompleted": true, "firstBillCompleted": false, "firstExpenseCompleted": false, "reconciliationCompleted": false },
              "settings": [], "rules": [], "incomes": [], "payments": [], "transactions": [], "billOccurrences": [],
              "integrity": "someHmacValue"
            }
        """.trimIndent()

        // Should parse without error — integrity field is ignored
        val snapshot = parseLedgerBackupJson(legacy)
        assertEquals(1, snapshot.schemaVersion)
        assertTrue(snapshot.incomes.isEmpty())
    }

    @Test
    fun integrityField_doesNotBreakSchema2Parsing() {
        val json = buildLedgerBackupJson(
            exportedAtIso = "2026-07-19T12:00:00",
            uiState = com.montecarlo.ledger.AppUiState(bankBalanceCents = 5000),
            incomes = emptyList(),
            payments = emptyList(),
            transactions = emptyList(),
            billOccurrences = emptyList(),
            settings = emptyList(),
            rules = emptyList(),
            onboardingProgress = OnboardingProgress(),
        )

        val withIntegrity = com.montecarlo.ledger.security.SecurityUtils.insertIntegrityField(json, "testHmac123+/=")
        val snapshot = parseLedgerBackupJson(withIntegrity)

        assertEquals(3, snapshot.schemaVersion)
        assertEquals("2026-07-19T12:00:00", snapshot.exportedAtIso)
    }
}
