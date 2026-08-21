package com.montecarlo.ledger.ui

import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.data.AssetEntity
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.SettingsEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.data.TransactionRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden round-trip contract for the backup codec: export -> import must preserve
 * every persisted field, the writer must be deterministic, and legacy v3 backups
 * must keep importing.
 */
class BackupRoundTripTest {

    private fun sampleJson(): String {
        val uiState = AppUiState(
            bankBalanceCents = 12_345L,
            ledgerBalanceCents = 12_345L,
            isBalanceReconciled = true,
            safeToSpendCents = 9_000L,
        )
        return buildLedgerBackupJson(
            exportedAtIso = "2026-08-21T12:00:00",
            uiState = uiState,
            incomes = listOf(
                IncomeEntity(
                    id = 1,
                    name = "Paycheck",
                    amount_cents = 200_000L,
                    frequency = "Bi-weekly",
                    day_of_month = null,
                    next_date = "2026-09-04",
                    expectedAmountCents = null,
                    payType = "HOURLY",
                )
            ),
            payments = listOf(
                PaymentEntity(
                    id = 2,
                    name = "Rent",
                    amount_cents = 100_000L,
                    frequency = "Monthly",
                    day_of_month = 1,
                    next_date = "2026-09-01",
                    is_active = 1,
                    isAutoWithdraw = true,
                )
            ),
            transactions = listOf(
                TransactionEntity(
                    id = 3,
                    description = "Coffee \"deluxe\" — multi,part",
                    amount_cents = -475L,
                    date = "2026-08-20",
                    type = "expense",
                    category = "food",
                    source = "csv_import",
                    review_status = "pending",
                    reviewed_at = null,
                )
            ),
            billOccurrences = listOf(
                BillOccurrenceEntity(
                    id = 4,
                    payment_id = 2,
                    due_date = "2026-05-01",
                    amount_cents = 100_000L,
                    is_paid = 0,
                    transaction_id = null,
                    created_at = "2026-04-20T10:00:00",
                    original_due_date = "2026-05-01",
                    is_user_modified = 1,
                )
            ),
            onboardingProgress = OnboardingProgress(firstGoalCompleted = true),
            settings = listOf(
                SettingsEntity("starting_balance", "50000"),
                SettingsEntity("simulation_days", "120"),
            ),
            rules = listOf(
                TransactionRuleEntity(1, "netflix", "subscriptions", 1, -10, "2026-01-01T00:00:00")
            ),
            assets = listOf(AssetEntity(7L, "Emergency fund", "savings", 50_000L, "2026-08-01")),
            goals = listOf(GoalEntity(8, "Vacation", 150_000L, 25_000L, "2026-12-31", "2026-02-02")),
            categoryBudgets = emptyList(),
            debts = listOf(
                DebtEntity(9L, "Card", 80_000L, 1850, 5_000L, 15, null, true)
            ),
        )
    }

    @Test
    fun roundTrip_preservesEveryPersistedField() {
        val snapshot = parseLedgerBackupJson(sampleJson())

        assertEquals(BACKUP_SCHEMA_VERSION, snapshot.schemaVersion)
        assertEquals("2026-08-21T12:00:00", snapshot.exportedAtIso)
        assertEquals(12_345L, snapshot.bankBalanceCents)
        assertTrue(snapshot.isBalanceReconciled)

        val income = snapshot.incomes.single()
        assertNull(income.day_of_month)
        assertNull(income.expectedAmountCents)
        assertEquals("HOURLY", income.payType)

        val payment = snapshot.payments.single()
        assertEquals(1, payment.day_of_month)
        assertTrue(payment.isAutoWithdraw)

        val txn = snapshot.transactions.single()
        assertNull(txn.reviewed_at)
        assertEquals("pending", txn.review_status)
        // Hand-rolled escaping bugs used to corrupt exactly this kind of content.
        assertEquals("Coffee \"deluxe\" — multi,part", txn.description)

        val occurrence = snapshot.billOccurrences.single()
        assertNull(occurrence.transaction_id)
        assertEquals(1, occurrence.is_user_modified)

        assertEquals("50000", snapshot.settings.first { it.key == "starting_balance" }.value)
        assertNull(snapshot.debts.single().linkedPaymentId)
        assertEquals("2026-12-31", snapshot.goals.single().deadline)
    }

    @Test
    fun exportIsDeterministic() {
        val first = sampleJson()
        val second = sampleJson()
        assertEquals(first, second)
    }

    @Test
    fun legacyV3BackupStillImports() {
        val legacyV3 = """
            {
              "schemaVersion": 3,
              "exportedAt": "2026-04-22T12:00:00",
              "summary": {"bankBalanceCents": 12345, "isBalanceReconciled": true},
              "onboarding": {},
              "settings": [],
              "rules": [],
              "incomes": [{"id": 1, "name": "Pay", "amount_cents": 100, "frequency": "Monthly",
                           "day_of_month": null, "next_date": "2026-05-01",
                           "expectedAmountCents": null, "payType": "FLAT"}],
              "payments": [],
              "transactions": [],
              "billOccurrences": [],
              "assets": [],
              "goals": [],
              "categoryBudgets": [],
              "debts": []
            }
        """.trimIndent()

        val snapshot = parseLedgerBackupJson(legacyV3)

        assertEquals(3, snapshot.schemaVersion)
        assertEquals(12345L, snapshot.bankBalanceCents)
        assertEquals("Pay", snapshot.incomes.single().name)
    }

    @Test
    fun malformedRootFailsClosed() {
        val broken = "[1,2,3]"
        val error = runCatching { parseLedgerBackupJson(broken) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
