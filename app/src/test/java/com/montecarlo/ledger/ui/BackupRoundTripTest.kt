package com.montecarlo.ledger.ui

import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.data.AccountEntity
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
                    clearing_status = "pending",
                    account_id = 11L,
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
                DebtEntity(
                    id = 9L,
                    name = "Card",
                    balanceCents = 80_000L,
                    aprBasisPoints = 1850,
                    minimumPaymentCents = 5_000L,
                    dueDayOfMonth = 15,
                    linkedPaymentId = null,
                    isActive = true,
                    kind = "revolving",
                    statementDayOfMonth = 22,
                    minPaymentPercentBps = 300,
                    minPaymentFloorCents = 2_500L,
                    linkedAccountId = 11L,
                )
            ),
            accounts = listOf(
                AccountEntity(11L, "Checking", "checking", 12_345L, isReconciled = true, isDefault = true, lastUpdated = "2026-08-21")
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
        assertEquals("pending", txn.clearing_status)
        assertEquals(11L, txn.account_id)
        // Hand-rolled escaping bugs used to corrupt exactly this kind of content.
        assertEquals("Coffee \"deluxe\" — multi,part", txn.description)

        val occurrence = snapshot.billOccurrences.single()
        assertNull(occurrence.transaction_id)
        assertEquals(1, occurrence.is_user_modified)

        assertEquals("50000", snapshot.settings.first { it.key == "starting_balance" }.value)
        assertNull(snapshot.debts.single().linkedPaymentId)
        val debt = snapshot.debts.single()
        assertEquals("revolving", debt.kind)
        assertEquals(22, debt.statementDayOfMonth)
        assertEquals(300, debt.minPaymentPercentBps)
        assertEquals(2_500L, debt.minPaymentFloorCents)
        assertEquals(11L, debt.linkedAccountId)
        val account = snapshot.accounts.single()
        assertTrue(account.isDefault && account.isReconciled)
        assertEquals(12_345L, account.balanceCents)
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
    fun earlyBackupsImportWithoutSectionsIntroducedInLaterVersions() {
        for (version in 1..2) {
            val json = """
                {
                  "schemaVersion": $version,
                  "summary": {"bankBalanceCents": 350, "isBalanceReconciled": true},
                  "settings": [], "rules": [], "incomes": [], "payments": [],
                  "transactions": [{"id": 1, "description": "Opening balance",
                                    "amount_cents": 350, "date": "2026-04-22", "type": "adjustment"}],
                  "billOccurrences": []
                }
            """.trimIndent()

            val snapshot = parseLedgerBackupJson(json)
            assertEquals(350L, snapshot.bankBalanceCents)
            assertEquals(350L, snapshot.transactions.single().amount_cents)
            assertTrue(snapshot.assets.isEmpty())
            assertTrue(snapshot.accounts.isEmpty())
        }
    }

    @Test
    fun legacyV5BackupImportsWithProductDepthDefaults() {
        val legacyV5 = """
            {
              "schemaVersion": 5,
              "exportedAt": "2026-08-01T12:00:00",
              "summary": {"bankBalanceCents": 5000, "isBalanceReconciled": false},
              "onboarding": {},
              "settings": [],
              "rules": [],
              "incomes": [],
              "payments": [],
              "transactions": [{"id": 1, "description": "Swipe", "amount_cents": -1200,
                                "date": "2026-07-30", "type": "expense", "category": "food",
                                "source": "manual", "review_status": "approved", "reviewed_at": null}],
              "billOccurrences": [],
              "assets": [],
              "goals": [],
              "categoryBudgets": [],
              "debts": [{"id": 1, "name": "Card", "balanceCents": 40000, "aprBasisPoints": 2100,
                         "minimumPaymentCents": 2500, "dueDayOfMonth": 10,
                         "linkedPaymentId": null, "isActive": true}],
              "accounts": []
            }
        """.trimIndent()

        val snapshot = parseLedgerBackupJson(legacyV5)

        assertEquals(5, snapshot.schemaVersion)
        // Statement history predates clearing states: everything restores as posted.
        assertEquals("posted", snapshot.transactions.single().clearing_status)
        assertNull(snapshot.transactions.single().account_id)
        // Pre-revolving debts restore as plain installments.
        val debt = snapshot.debts.single()
        assertEquals("installment", debt.kind)
        assertNull(debt.statementDayOfMonth)
        assertEquals(0, debt.minPaymentPercentBps)
        assertEquals(0L, debt.minPaymentFloorCents)
        assertNull(debt.linkedAccountId)
    }

    @Test
    fun malformedRootFailsClosed() {
        val broken = "[1,2,3]"
        val error = runCatching { parseLedgerBackupJson(broken) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun versionOnlyBackupCannotBecomeAnEmptyRestore() {
        for (version in 1..BACKUP_SCHEMA_VERSION) {
            val error = runCatching {
                parseLedgerBackupJson("""{"schemaVersion":$version}""")
            }.exceptionOrNull()
            assertTrue("Backup version $version must reject an incomplete snapshot", error is IllegalArgumentException)
        }
    }

    @Test
    fun currentBackupRejectsMissingAndMalformedSections() {
        val exported = parseBackupJsonToElement(sampleJson())
        val sections = listOf(
            "summary", "onboarding", "settings", "rules", "incomes", "payments", "transactions",
            "billOccurrences", "assets", "goals", "categoryBudgets", "debts", "accounts",
        )
        for (section in sections) {
            val missing = JsonObject(exported - section).toString()
            val wrongType = JsonObject(exported + (section to JsonPrimitive("invalid"))).toString()
            assertThrows("Missing $section", IllegalArgumentException::class.java) { parseLedgerBackupJson(missing) }
            assertThrows("Malformed $section", IllegalArgumentException::class.java) { parseLedgerBackupJson(wrongType) }
        }
    }

    @Test
    fun currentBackupRejectsMissingBalanceAndInvalidReconciliationFlag() {
        val exported = parseBackupJsonToElement(sampleJson())
        val summary = exported.getValue("summary") as JsonObject
        val invalidSummaries = listOf(
            JsonObject(summary - "bankBalanceCents"),
            JsonObject(summary + ("isBalanceReconciled" to JsonPrimitive("unknown"))),
        )
        invalidSummaries.forEach { invalid ->
            val json = JsonObject(exported + ("summary" to invalid)).toString()
            assertThrows(IllegalArgumentException::class.java) { parseLedgerBackupJson(json) }
        }
    }

    @Test
    fun currentBackupRejectsMissingOrMalformedAccountAndAssetBalances() {
        val exported = parseBackupJsonToElement(sampleJson())
        for (section in listOf("accounts", "assets")) {
            val records = exported.getValue(section) as JsonArray
            val original = records.first() as JsonObject
            val invalidRecords = listOf(
                JsonObject(original - "balanceCents"),
                JsonObject(original + ("balanceCents" to JsonPrimitive("unknown"))),
            )
            for (invalidRecord in invalidRecords) {
                val broken = JsonObject(exported + (section to JsonArray(listOf(invalidRecord)))).toString()
                assertThrows("Malformed $section balance", IllegalArgumentException::class.java) {
                    parseLedgerBackupJson(broken)
                }
            }
        }
    }

    @Test
    fun explicitlyEmptyCurrentBackupStillImports() {
        val json = buildLedgerBackupJson(
            exportedAtIso = "2026-08-21T12:00:00",
            uiState = AppUiState(),
            incomes = emptyList(),
            payments = emptyList(),
            transactions = emptyList(),
            billOccurrences = emptyList(),
            onboardingProgress = OnboardingProgress(),
            settings = emptyList(),
            rules = emptyList(),
        )

        val snapshot = parseLedgerBackupJson(json)
        assertEquals(0L, snapshot.bankBalanceCents)
        assertTrue(snapshot.transactions.isEmpty())
        assertTrue(snapshot.accounts.isEmpty())
    }
}
