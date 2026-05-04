package com.example.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.app.data.LedgerBackupSnapshot
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import com.example.app.data.OnboardingProgress

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LedgerRepositoryTest {

    @Test
    fun syncBillOccurrences_refreshesFutureUnpaidOccurrencesWhenPaymentIsEdited() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)
            val today = LocalDate.now()
            val firstDue = today.plusDays(1)
            val secondDue = today.plusDays(8)
            val initialPayment = PaymentEntity(
                id = 1,
                name = "Gym",
                amount_cents = 5_000,
                frequency = "weekly",
                day_of_month = null,
                next_date = firstDue.toString(),
                is_active = 1,
                isAutoWithdraw = true,
            )

            db.paymentDao().insert(initialPayment)

            repo.syncBillOccurrences()

            val initialOccurrences = db.billOccurrenceDao().getForPayment(initialPayment.id).first()
            val firstOccurrence = initialOccurrences.single { it.due_date == firstDue.toString() }
            assertEquals(firstDue.toString(), firstOccurrence.due_date)
            assertEquals(5_000, firstOccurrence.amount_cents)
            assertEquals(0, firstOccurrence.is_paid)

            repo.markOccurrencePaid(firstOccurrence.id)
            repo.updatePayment(initialPayment.copy(amount_cents = 6_000))
            repo.syncBillOccurrences()

            val refreshed = db.billOccurrenceDao().getForPayment(initialPayment.id).first()
            val paidFirst = refreshed.single { it.due_date == firstDue.toString() }
            val updatedSecond = refreshed.single { it.due_date == secondDue.toString() }

            assertEquals(1, refreshed.count { it.due_date == firstDue.toString() })
            assertEquals(1, refreshed.count { it.due_date == secondDue.toString() })
            assertEquals(1, paidFirst.is_paid)
            assertEquals(5_000, paidFirst.amount_cents)
            assertEquals(0, updatedSecond.is_paid)
            assertEquals(6_000, updatedSecond.amount_cents)
            assertTrue(refreshed.size >= 2)
        } finally {
            db.close()
        }
    }

    @Test
    fun insertTransaction_linksMatchingBillOccurrenceAndMarksItPaid() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)
            val today = LocalDate.now()
            val payment = PaymentEntity(
                id = 5,
                name = "Rent",
                amount_cents = 1_200_00,
                frequency = "one-time",
                day_of_month = null,
                next_date = today.toString(),
                is_active = 1,
                isAutoWithdraw = false,
            )

            repo.setBankBalance(2_000_00)
            db.paymentDao().insert(payment)
            repo.syncBillOccurrences()

            val occurrence = db.billOccurrenceDao().getForPayment(payment.id).first().single()
            val transactionId = repo.insertTransaction(
                TransactionEntity(
                    description = "Rent payment",
                    amount_cents = -1_200_00,
                    date = today.toString(),
                    type = "expense",
                ),
                occurrence.id
            )

            val linkedOccurrence = db.billOccurrenceDao().getById(occurrence.id)
            val transaction = db.transactionDao().getById(transactionId.toInt())

            assertEquals(1, linkedOccurrence?.is_paid)
            assertEquals(transactionId.toInt(), linkedOccurrence?.transaction_id)
            assertEquals("Rent payment", transaction?.description)
            assertEquals(-1_200_00, transaction?.amount_cents)
            assertEquals(800_00, repo.getBankBalanceCents())
        } finally {
            db.close()
        }
    }

    @Test
    fun payBillOccurrence_recordsExpenseAndSubtractsReconciledBankBalance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)
            val today = LocalDate.now()
            val payment = PaymentEntity(
                id = 8,
                name = "Electric",
                amount_cents = 18_250,
                frequency = "one-time",
                day_of_month = null,
                next_date = today.toString(),
                is_active = 1,
                isAutoWithdraw = true,
            )

            repo.setBankBalance(100_000)
            db.paymentDao().insert(payment)
            repo.syncBillOccurrences()

            val occurrence = db.billOccurrenceDao().getForPayment(payment.id).first().single()
            val transactionId = repo.payBillOccurrence(occurrence.id)
            val transaction = db.transactionDao().getById(transactionId.toInt())
            val linkedOccurrence = db.billOccurrenceDao().getById(occurrence.id)

            assertEquals("Bill paid: Electric", transaction?.description)
            assertEquals(-18_250, transaction?.amount_cents)
            assertEquals("expense", transaction?.type)
            assertEquals("bills", transaction?.category)
            assertEquals("approved", transaction?.review_status)
            assertEquals(1, linkedOccurrence?.is_paid)
            assertEquals(transactionId.toInt(), linkedOccurrence?.transaction_id)
            assertEquals(81_750, repo.getBankBalanceCents())
            assertEquals(-18_250, db.transactionDao().getTotalBalanceCents().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun skipBillOccurrence_removesOneBillFromForecastWithoutRecordingSpend() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)
            val dueDate = LocalDate.now().plusDays(3)
            val payment = PaymentEntity(
                id = 9,
                name = "Doctor",
                amount_cents = 7_500,
                frequency = "one-time",
                day_of_month = null,
                next_date = dueDate.toString(),
                is_active = 1,
                isAutoWithdraw = false,
            )

            repo.setBankBalance(100_000)
            db.paymentDao().insert(payment)
            repo.syncBillOccurrences()

            val occurrence = db.billOccurrenceDao().getForPayment(payment.id).first().single()
            repo.skipBillOccurrence(occurrence.id)

            val skipped = db.billOccurrenceDao().getById(occurrence.id)
            assertEquals(1, skipped?.is_paid)
            assertEquals(null, skipped?.transaction_id)
            assertEquals(0, db.transactionDao().getAll().first().size)
            assertEquals(100_000, repo.getBankBalanceCents())
        } finally {
            db.close()
        }
    }

    @Test
    fun rescheduleBillOccurrence_movesOneOccurrenceWithoutChangingTheRecurringBill() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)
            val today = LocalDate.now()
            val originalDue = today.plusDays(5)
            val movedDue = today.plusDays(12)
            val payment = PaymentEntity(
                id = 10,
                name = "Internet",
                amount_cents = 6_500,
                frequency = "monthly",
                day_of_month = originalDue.dayOfMonth,
                next_date = originalDue.toString(),
                is_active = 1,
                isAutoWithdraw = true,
            )

            db.paymentDao().insert(payment)
            repo.syncBillOccurrences()

            val occurrence = db.billOccurrenceDao().getForPayment(payment.id).first()
                .single { it.due_date == originalDue.toString() }
            repo.rescheduleBillOccurrence(occurrence.id, movedDue.toString())
            repo.syncBillOccurrences()

            val updatedPayment = db.paymentDao().getById(payment.id)
            val occurrences = db.billOccurrenceDao().getForPayment(payment.id).first()
            val movedOccurrence = occurrences.single { it.due_date == movedDue.toString() }

            assertEquals(originalDue.toString(), updatedPayment?.next_date)
            assertEquals(originalDue.dayOfMonth, updatedPayment?.day_of_month)
            assertEquals(originalDue.toString(), movedOccurrence.original_due_date)
            assertEquals(1, movedOccurrence.is_user_modified)
            assertFalse(occurrences.any { it.due_date == originalDue.toString() && it.is_paid == 0 })
        } finally {
            db.close()
        }
    }

    @Test
    fun insertTransaction_appliesSavedRulesToMatchingDescriptions() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)
            val today = LocalDate.now().toString()

            repo.saveTransactionRule("Netflix", "subscriptions")
            repo.insertTransaction(
                TransactionEntity(
                    description = "Netflix monthly",
                    amount_cents = -1_499,
                    date = today,
                    type = "expense",
                )
            )

            val stored = db.transactionDao().getAll().first().single()
            assertEquals("subscriptions", stored.category)
        } finally {
            db.close()
        }
    }

    @Test
    fun insertTransaction_appliesPresetKeywordsWhenNoUserRuleExists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)
            val today = LocalDate.now().toString()

            repo.insertTransaction(
                TransactionEntity(
                    description = "STARBUCKS STORE 123",
                    amount_cents = -650,
                    date = today,
                    type = "expense",
                )
            )

            val stored = db.transactionDao().getAll().first().single()
            assertEquals("dining", stored.category)
        } finally {
            db.close()
        }
    }

    @Test
    fun manualTransactionsAdjustReconciledBankBalance() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)
            val today = LocalDate.now().toString()

            repo.setBankBalance(100_00)
            repo.insertTransaction(
                TransactionEntity(
                    description = "Lunch",
                    amount_cents = -12_50,
                    date = today,
                    type = "expense",
                )
            )
            assertEquals(87_50, repo.getBankBalanceCents())

            val stored = db.transactionDao().getAll().first().single()
            repo.updateTransaction(stored.copy(amount_cents = -10_00))
            assertEquals(90_00, repo.getBankBalanceCents())

            repo.insertTransaction(
                TransactionEntity(
                    description = "Cashback",
                    amount_cents = 5_00,
                    date = today,
                    type = "income",
                )
            )
            assertEquals(95_00, repo.getBankBalanceCents())

            val lunch = db.transactionDao().getAll().first().single { it.description == "Lunch" }
            repo.deleteTransaction(lunch)
            assertEquals(105_00, repo.getBankBalanceCents())
        } finally {
            db.close()
        }
    }

    @Test
    fun installCategoryRulePresets_isRepeatableWithoutDuplicatingRules() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)

            val firstInstallCount = repo.installCategoryRulePresets()
            val secondInstallCount = repo.installCategoryRulePresets()
            val rules = db.transactionRuleDao().getAll().first()

            assertEquals(CategoryRulePresets.totalKeywordCount, firstInstallCount)
            assertEquals(0, secondInstallCount)
            assertEquals(CategoryRulePresets.totalKeywordCount, rules.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun syncOnboardingMilestones_backfillsPersistedProgressFromExistingData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)
            val today = LocalDate.now().toString()

            db.incomeDao().insertIncome(
                IncomeEntity(
                    name = "Paycheck",
                    amount_cents = 2_000_00,
                    frequency = "monthly",
                    day_of_month = 1,
                    next_date = today,
                )
            )
            db.paymentDao().insert(
                PaymentEntity(
                    name = "Rent",
                    amount_cents = 1_000_00,
                    frequency = "monthly",
                    day_of_month = 1,
                    next_date = today,
                    is_active = 1,
                    isAutoWithdraw = false,
                )
            )
            db.transactionDao().insert(
                TransactionEntity(
                    description = "Coffee",
                    amount_cents = -500,
                    date = today,
                    type = "expense",
                )
            )
            db.settingsDao().setValue(SettingsEntity("bank_balance_cents", "150000"))
            db.settingsDao().setValue(SettingsEntity("bank_balance_reconciled", "true"))

            val before = repo.onboardingProgress.first()
            assertFalse(before.firstIncomeCompleted)
            assertFalse(before.firstBillCompleted)
            assertFalse(before.firstExpenseCompleted)
            assertFalse(before.reconciliationCompleted)

            repo.syncOnboardingMilestones()

            val after = repo.onboardingProgress.first()
            assertTrue(after.firstIncomeCompleted)
            assertTrue(after.firstBillCompleted)
            assertTrue(after.firstExpenseCompleted)
            assertTrue(after.reconciliationCompleted)
            assertFalse(after.firstGoalCompleted)
            assertFalse(after.isComplete)
            assertEquals(3, after.completedCount)
        } finally {
            db.close()
        }
    }

    @Test
    fun processPayday_deletesOneTimeIncomeAfterRecordingTheTransaction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)
            val today = LocalDate.now().toString()
            val income = IncomeEntity(
                name = "Refund",
                amount_cents = 4_500,
                frequency = "One-time",
                day_of_month = null,
                next_date = today,
                expectedAmountCents = 4_500,
            )

            db.incomeDao().insertIncome(income)
            val storedIncome = db.incomeDao().getAllIncomes().first().single()

            repo.processPayday(storedIncome, 4_500)

            val incomes = db.incomeDao().getAllIncomes().first()
            val transactions = db.transactionDao().getAll().first()

            assertTrue(incomes.isEmpty())
            assertEquals(1, transactions.size)
            assertEquals("Paycheck: Refund", transactions.single().description)
            assertEquals(4_500, transactions.single().amount_cents)
            assertEquals("income", transactions.single().type)
        } finally {
            db.close()
        }
    }

    @Test
    fun restoreBackup_replacesLocalDataAndRestoresCoreSettings() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)

            db.incomeDao().insertIncome(
                IncomeEntity(
                    name = "Old income",
                    amount_cents = 1_000,
                    frequency = "weekly",
                    day_of_month = null,
                    next_date = LocalDate.now().toString(),
                )
            )
            db.settingsDao().setValue(SettingsEntity("bank_balance_cents", "100"))

            val snapshot = LedgerBackupSnapshot(
                schemaVersion = 1,
                exportedAtIso = "2026-04-22T12:00:00",
                bankBalanceCents = 12_345,
                isBalanceReconciled = true,
                onboardingProgress = OnboardingProgress(
                    firstIncomeCompleted = true,
                    firstBillCompleted = true,
                    firstExpenseCompleted = false,
                    reconciliationCompleted = true,
                ),
                settings = listOf(
                    SettingsEntity("starting_balance", "12345"),
                    SettingsEntity("simulation_days", "120"),
                    SettingsEntity("bank_balance_cents", "12345"),
                    SettingsEntity("current_balance", "12345"),
                    SettingsEntity("bank_balance_reconciled", "true"),
                    SettingsEntity("balance_reconciled", "true"),
                    SettingsEntity("onboarding_first_income_completed", "true"),
                    SettingsEntity("onboarding_first_bill_completed", "true"),
                    SettingsEntity("onboarding_first_expense_completed", "false"),
                    SettingsEntity("onboarding_reconciliation_completed", "true"),
                    SettingsEntity("onboarding_monitoring_intro_seen", "false"),
                ),
                incomes = listOf(
                    IncomeEntity(
                        id = 7,
                        name = "Paycheck",
                        amount_cents = 2_500_00,
                        frequency = "monthly",
                        day_of_month = 1,
                        next_date = "2026-05-01",
                        expectedAmountCents = 2_450_00,
                    )
                ),
                payments = listOf(
                    PaymentEntity(
                        id = 11,
                        name = "Rent",
                        amount_cents = 1_200_00,
                        frequency = "Monthly",
                        day_of_month = 1,
                        next_date = "2026-05-01",
                        is_active = 1,
                        isAutoWithdraw = false,
                    )
                ),
                transactions = listOf(
                    TransactionEntity(
                        id = 19,
                        description = "Coffee",
                        amount_cents = -450,
                        date = "2026-04-21",
                        type = "expense",
                    )
                ),
                billOccurrences = listOf(
                    BillOccurrenceEntity(
                        id = 22,
                        payment_id = 11,
                        due_date = "2026-05-01",
                        amount_cents = 1_200_00,
                        is_paid = 0,
                        transaction_id = null,
                        created_at = "2026-04-01T10:00:00",
                    )
                ),
            )

            repo.restoreBackup(snapshot)

            val incomes = db.incomeDao().getAllIncomes().first()
            val payments = db.paymentDao().getAll().first()
            val transactions = db.transactionDao().getAll().first()
            val occurrences = db.billOccurrenceDao().getAll().first()
            val settings = db.settingsDao().getAll().first().associateBy { it.key }

            assertEquals(1, incomes.size)
            assertEquals("Paycheck", incomes.single().name)
            assertEquals(1, payments.size)
            assertEquals("Rent", payments.single().name)
            assertEquals(1, transactions.size)
            assertEquals("Coffee", transactions.single().description)
            assertEquals(1, occurrences.size)
            assertEquals(11, occurrences.single().payment_id)
            assertEquals("12345", settings["bank_balance_cents"]?.value)
            assertEquals("12345", settings["current_balance"]?.value)
            assertEquals("true", settings["bank_balance_reconciled"]?.value)
            assertEquals("true", settings["onboarding_first_income_completed"]?.value)
            assertEquals("true", settings["onboarding_first_bill_completed"]?.value)
            assertEquals("false", settings["onboarding_first_expense_completed"]?.value)
            assertEquals("true", settings["onboarding_reconciliation_completed"]?.value)
            assertEquals("false", settings["onboarding_monitoring_intro_seen"]?.value)
            assertEquals("true", settings["balance_reconciled"]?.value)
            assertEquals("12345", settings["starting_balance"]?.value)
            assertEquals("120", settings["simulation_days"]?.value)
        } finally {
            db.close()
        }
    }

    @Test
    fun monitoringModeIntroSeen_canBePersistedIndependently() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = LedgerRepository(db)

            assertFalse(repo.monitoringModeIntroSeen.first())
            repo.markMonitoringModeIntroSeen()
            assertTrue(repo.monitoringModeIntroSeen.first())
        } finally {
            db.close()
        }
    }
}
