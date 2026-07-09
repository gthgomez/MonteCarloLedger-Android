package com.example.app.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import com.example.app.domain.DomainRules
import com.example.app.processing.RecurrenceMath
import com.example.app.data.LedgerBackupSnapshot
import com.example.app.security.SecretHash
import com.example.app.security.SecurityUtils
import java.util.Locale

class LedgerRepository(private val db: AppDatabase) {

    private companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_CSV_IMPORT = "csv_import"
        const val REVIEW_APPROVED = "approved"
        const val REVIEW_PENDING = "pending"
        const val KEY_BANK_BALANCE = "bank_balance_cents"
        const val KEY_CURRENT_BALANCE = "current_balance"
        const val KEY_BANK_BALANCE_RECONCILED = "bank_balance_reconciled"
        const val KEY_LEGACY_RECONCILED = "balance_reconciled"
        const val KEY_STARTING_BALANCE = "starting_balance"
        const val KEY_SIMULATION_DAYS = "simulation_days"
        const val KEY_REMINDERS_ENABLED = "reminders_enabled"
        const val KEY_WEEKLY_CHECKIN_ENABLED = "weekly_checkin_enabled"
        const val KEY_BILL_REMINDERS_ENABLED = "bill_reminders_enabled"
        const val KEY_BILL_REMINDER_DAYS_BEFORE = "bill_reminder_days_before"
        const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        const val KEY_APP_LOCK_PIN_SALT = "app_lock_pin_salt"
        const val KEY_APP_LOCK_PIN_HASH = "app_lock_pin_hash"
        const val KEY_APP_LOCK_PIN_ITERATIONS = "app_lock_pin_iterations"
        const val KEY_ONBOARDING_FIRST_INCOME = "onboarding_first_income_completed"
        const val KEY_ONBOARDING_FIRST_BILL = "onboarding_first_bill_completed"
        const val KEY_ONBOARDING_FIRST_EXPENSE = "onboarding_first_expense_completed"
        const val KEY_ONBOARDING_RECONCILIATION = "onboarding_reconciliation_completed"
        const val KEY_ONBOARDING_FIRST_GOAL = "onboarding_first_goal_completed"
        const val KEY_ONBOARDING_MONITORING_INTRO_SEEN = "onboarding_monitoring_intro_seen"
    }

    // Income
    val allIncome: Flow<List<IncomeEntity>> = db.incomeDao().getAllIncomes()
    suspend fun insertIncome(entity: IncomeEntity) {
        db.incomeDao().insertIncome(entity)
        markOnboardingMilestone(OnboardingMilestone.FIRST_INCOME)
    }
    suspend fun updateIncome(entity: IncomeEntity) = db.incomeDao().updateIncome(entity)
    suspend fun deleteIncome(entity: IncomeEntity) = db.incomeDao().deleteIncome(entity)

    // Payments
    val allPayments: Flow<List<PaymentEntity>> = db.paymentDao().getAll()
    suspend fun insertPayment(entity: PaymentEntity) {
        db.paymentDao().insert(entity)
        markOnboardingMilestone(OnboardingMilestone.FIRST_BILL)
    }
    suspend fun importPayments(payments: List<PaymentEntity>) {
        if (payments.isEmpty()) return
        val existingKeys = db.paymentDao().getAll().first()
            .map { paymentKey(it) }
            .toMutableSet()
        val uniquePayments = payments.filter { existingKeys.add(paymentKey(it)) }
        if (uniquePayments.isEmpty()) return
        db.withTransaction {
            uniquePayments.forEach { payment ->
                db.paymentDao().insert(payment)
            }
        }
        markOnboardingMilestone(OnboardingMilestone.FIRST_BILL)
        syncBillOccurrences()
    }
    suspend fun updatePayment(entity: PaymentEntity) = db.paymentDao().update(entity)
    suspend fun deletePayment(entity: PaymentEntity) = db.paymentDao().delete(entity)

    // Transactions
    val allTransactions: Flow<List<TransactionEntity>> = db.transactionDao().getAll()
    val allTransactionRules: Flow<List<TransactionRuleEntity>> = db.transactionRuleDao().getAll()
    suspend fun insertTransaction(entity: TransactionEntity) {
        val normalized = applyTransactionRules(entity).withReviewState(source = SOURCE_MANUAL)
        db.transactionDao().insert(normalized)
        applyTransactionToBankBalanceIfReconciled(normalized.amount_cents)
        if (normalized.type == "expense") {
            markOnboardingMilestone(OnboardingMilestone.FIRST_EXPENSE)
        }
    }
    suspend fun importTransactions(transactions: List<TransactionEntity>) {
        if (transactions.isEmpty()) return
        val existingKeys = db.transactionDao().getAll().first()
            .map { transactionKey(it) }
            .toMutableSet()
        val normalizedTransactions = transactions.map {
            applyTransactionRules(it).withReviewState(source = SOURCE_CSV_IMPORT)
        }
        val uniqueTransactions = normalizedTransactions.filter { existingKeys.add(transactionKey(it)) }
        if (uniqueTransactions.isEmpty()) return
        db.withTransaction {
            uniqueTransactions.forEach { entity ->
                db.transactionDao().insert(entity)
            }
        }
        if (uniqueTransactions.any { it.type == "expense" }) {
            markOnboardingMilestone(OnboardingMilestone.FIRST_EXPENSE)
        }
    }
    suspend fun insertTransaction(entity: TransactionEntity, linkedOccurrenceId: Int): Long =
        db.withTransaction {
            val normalized = applyTransactionRules(entity)
                .withReviewState(source = SOURCE_MANUAL)
                .copy(review_status = REVIEW_APPROVED, reviewed_at = LocalDateTime.now().toString())
            val transactionId = db.transactionDao().insert(normalized)
            markOccurrencePaid(linkedOccurrenceId, transactionId.toInt())
            applyTransactionToBankBalanceIfReconciled(normalized.amount_cents)
            if (normalized.type == "expense") {
                markOnboardingMilestone(OnboardingMilestone.FIRST_EXPENSE)
            }
            transactionId
        }

    suspend fun payBillOccurrence(id: Int): Long =
        db.withTransaction {
            val occurrence = db.billOccurrenceDao().getById(id)
                ?: throw IllegalArgumentException("Occurrence not found.")
            val existingTransactionId = occurrence.transaction_id
            if (existingTransactionId != null) {
                markOccurrencePaid(id, existingTransactionId)
                return@withTransaction existingTransactionId.toLong()
            }

            val paymentName = db.paymentDao().getById(occurrence.payment_id)?.name ?: "Bill"
            val normalized = applyTransactionRules(
                TransactionEntity(
                    description = "Bill paid: $paymentName",
                    amount_cents = -occurrence.amount_cents,
                    date = LocalDate.now().toString(),
                    type = "expense",
                    category = "bills",
                )
            )
                .withReviewState(source = SOURCE_MANUAL)
                .copy(review_status = REVIEW_APPROVED, reviewed_at = LocalDateTime.now().toString())

            val transactionId = db.transactionDao().insert(normalized)
            markOccurrencePaid(id, transactionId.toInt())
            if (isBalanceReconciled()) {
                writeBankBalanceCents(getBankBalanceCents() - occurrence.amount_cents)
            }
            markOnboardingMilestone(OnboardingMilestone.FIRST_EXPENSE)
            transactionId
        }

    suspend fun updateTransaction(entity: TransactionEntity) {
        val existing = db.transactionDao().getById(entity.id)
        val reviewed = applyTransactionRules(entity).copy(
            review_status = REVIEW_APPROVED,
            reviewed_at = entity.reviewed_at ?: LocalDateTime.now().toString(),
        )
        db.transactionDao().update(reviewed)
        if (existing != null) {
            applyTransactionToBankBalanceIfReconciled(reviewed.amount_cents - existing.amount_cents)
        }
    }
    suspend fun approveTransactionReview(transactionId: Int) {
        val transaction = db.transactionDao().getById(transactionId) ?: return
        db.transactionDao().update(
            transaction.copy(
                review_status = REVIEW_APPROVED,
                reviewed_at = LocalDateTime.now().toString(),
            )
        )
    }
    suspend fun deleteTransaction(entity: TransactionEntity) {
        db.transactionDao().delete(entity)
        applyTransactionToBankBalanceIfReconciled(-entity.amount_cents)
    }

    // Assets
    val allAssets: Flow<List<AssetEntity>> = db.assetDao().getAllAssets()
    val totalAssetBalance: Flow<Long> = db.assetDao().getTotalAssetBalance().map { it ?: 0L }
    suspend fun insertAsset(entity: AssetEntity) = db.assetDao().insertAsset(entity)
    suspend fun updateAsset(entity: AssetEntity) = db.assetDao().updateAsset(entity)
    suspend fun deleteAsset(entity: AssetEntity) = db.assetDao().deleteAsset(entity)

    // Goals
    val allGoals: Flow<List<GoalEntity>> = db.goalDao().getAllGoals()
    suspend fun insertGoal(goal: GoalEntity) {
        db.goalDao().insertGoal(goal)
        markOnboardingMilestone(OnboardingMilestone.FIRST_GOAL)
    }
    suspend fun updateGoal(goal: GoalEntity) = db.goalDao().updateGoal(goal)
    suspend fun deleteGoal(goal: GoalEntity) = db.goalDao().deleteGoal(goal)

    val onboardingProgress: Flow<OnboardingProgress> = db.settingsDao().getAll().map { settings ->
        val byKey = settings.associateBy { it.key }
        OnboardingProgress(
            reconciliationCompleted = byKey[KEY_ONBOARDING_RECONCILIATION]?.value?.toBoolean() == true,
            firstIncomeCompleted = byKey[KEY_ONBOARDING_FIRST_INCOME]?.value?.toBoolean() == true,
            firstBillCompleted = byKey[KEY_ONBOARDING_FIRST_BILL]?.value?.toBoolean() == true,
            firstGoalCompleted = byKey[KEY_ONBOARDING_FIRST_GOAL]?.value?.toBoolean() == true,
            firstExpenseCompleted = byKey[KEY_ONBOARDING_FIRST_EXPENSE]?.value?.toBoolean() == true,
        )
    }

    val monitoringModeIntroSeen: Flow<Boolean> = db.settingsDao().getAll().map { settings ->
        settings.any { it.key == KEY_ONBOARDING_MONITORING_INTRO_SEEN && it.value.toBoolean() }
    }

    data class BalanceState(
        val bankBalanceCents: Int,
        val isReconciled: Boolean,
    )

    val balanceState: Flow<BalanceState> = db.settingsDao().getAll().map { settings ->
        val byKey = settings.associateBy { it.key }
        val bankBalance = byKey[KEY_BANK_BALANCE]?.value?.toIntOrNull()
            ?: byKey[KEY_CURRENT_BALANCE]?.value?.toIntOrNull()
            ?: 0
        val reconciled = byKey[KEY_BANK_BALANCE_RECONCILED]?.value?.toBoolean()
            ?: byKey[KEY_LEGACY_RECONCILED]?.value?.toBoolean()
            ?: false
        BalanceState(
            bankBalanceCents = bankBalance,
            isReconciled = reconciled,
        )
    }

    // Settings / Balance
    val allSettings: Flow<List<SettingsEntity>> = db.settingsDao().getAll()
    val reminderPreferences: Flow<ReminderPreferences> = db.settingsDao().getAll().map { settings ->
        val byKey = settings.associateBy { it.key }
        ReminderPreferences(
            enabled = byKey[KEY_REMINDERS_ENABLED]?.value?.toBoolean() == true,
            weeklyCheckInEnabled = byKey[KEY_WEEKLY_CHECKIN_ENABLED]?.value?.toBoolean() == true,
            billRemindersEnabled = byKey[KEY_BILL_REMINDERS_ENABLED]?.value?.toBoolean() == true,
            billReminderDaysBefore = byKey[KEY_BILL_REMINDER_DAYS_BEFORE]?.value?.toIntOrNull() ?: 3,
        )
    }

    val appLockPreferences: Flow<AppLockPreferences> = db.settingsDao().getAll().map { settings ->
        val byKey = settings.associateBy { it.key }
        val hasPin = !byKey[KEY_APP_LOCK_PIN_SALT]?.value.isNullOrBlank() &&
            !byKey[KEY_APP_LOCK_PIN_HASH]?.value.isNullOrBlank()
        AppLockPreferences(
            enabled = byKey[KEY_APP_LOCK_ENABLED]?.value?.toBoolean() == true && hasPin,
            hasPin = hasPin,
        )
    }

    suspend fun migrateBalanceSettings() {
        val bankBalance = db.settingsDao().getValue(KEY_BANK_BALANCE)
        val legacyBalance = db.settingsDao().getValue(KEY_CURRENT_BALANCE)
        if (bankBalance == null && legacyBalance != null) {
            db.settingsDao().setValue(SettingsEntity(KEY_BANK_BALANCE, legacyBalance))
        }

        val reconciled = db.settingsDao().getValue(KEY_BANK_BALANCE_RECONCILED)
        val legacyReconciled = db.settingsDao().getValue(KEY_LEGACY_RECONCILED)
        if (reconciled == null && legacyReconciled != null) {
            db.settingsDao().setValue(SettingsEntity(KEY_BANK_BALANCE_RECONCILED, legacyReconciled))
        }
    }

    suspend fun getBankBalanceCents(): Int {
        val bankBalance = db.settingsDao().getValue(KEY_BANK_BALANCE)?.toIntOrNull()
        if (bankBalance != null) return bankBalance

        val legacyBalance = db.settingsDao().getValue(KEY_CURRENT_BALANCE)?.toIntOrNull() ?: 0
        db.settingsDao().setValue(SettingsEntity(KEY_BANK_BALANCE, legacyBalance.toString()))
        return legacyBalance
    }

    suspend fun isBalanceReconciled(): Boolean {
        val reconciled = db.settingsDao().getValue(KEY_BANK_BALANCE_RECONCILED)?.toBoolean()
        if (reconciled != null) return reconciled

        return db.settingsDao().getValue(KEY_LEGACY_RECONCILED)?.toBoolean() == true
    }

    suspend fun setBankBalance(amountCents: Int) =
        run {
            writeBankBalanceCents(amountCents)
            markOnboardingMilestone(OnboardingMilestone.RECONCILIATION)
        }

    // Bill Occurrences
    val allBillOccurrences: Flow<List<BillOccurrenceEntity>> = db.billOccurrenceDao().getAll()
    suspend fun markOccurrencePaid(id: Int, transactionId: Int? = null) {
        val occurrence = db.billOccurrenceDao().getById(id)
            ?: throw IllegalArgumentException("Occurrence not found.")
        val resolvedTransactionId = transactionId ?: occurrence.transaction_id
        val transaction = resolvedTransactionId?.let { db.transactionDao().getById(it) }

        if (resolvedTransactionId != null) {
            DomainRules.validateOccurrenceLink(occurrence, transaction)
        }

        db.billOccurrenceDao().markPaid(id, resolvedTransactionId)
    }

    suspend fun skipBillOccurrence(id: Int) {
        val occurrence = db.billOccurrenceDao().getById(id)
            ?: throw IllegalArgumentException("Occurrence not found.")
        db.billOccurrenceDao().markPaid(occurrence.id, null)
    }

    suspend fun rescheduleBillOccurrence(id: Int, dueDate: String) {
        val parsedDueDate = runCatching { LocalDate.parse(dueDate.trim()) }
            .getOrElse { throw IllegalArgumentException("Enter a valid due date.") }
        val occurrence = db.billOccurrenceDao().getById(id)
            ?: throw IllegalArgumentException("Occurrence not found.")
        db.billOccurrenceDao().update(
            occurrence.copy(
                due_date = parsedDueDate.toString(),
                original_due_date = occurrence.original_due_date ?: occurrence.due_date,
                is_user_modified = 1,
            )
        )
    }

    suspend fun syncBillOccurrences() {
        val today = LocalDate.now()
        // 30-day lookback: catches past-due unpaid bills (matches original timeline_service.py behaviour)
        val lookback = today.minusDays(30)
        val horizon = today.plusDays(90)
        val payments = db.paymentDao().getActive().first()
        for (payment in payments) {
            var cursor = LocalDate.parse(payment.next_date)
            val freq = RecurrenceMath.normalizeFrequency(payment.frequency)
            val isOneTime = freq == "onetime"
            val dayOfMonth = payment.day_of_month
            db.billOccurrenceDao().deleteUnpaidForPaymentBetween(payment.id, lookback, horizon)
            // Rewind cursor to cover the 30-day lookback window for recurring payments
            if (!isOneTime) {
                while (cursor.isAfter(lookback)) {
                    val prev = RecurrenceMath.previousDate(cursor, payment.frequency, dayOfMonth) ?: break
                    if (prev.isBefore(lookback)) break
                    cursor = prev
                }
            }
            while (!cursor.isAfter(horizon)) {
                if (!cursor.isBefore(lookback)) {
                    val already = db.billOccurrenceDao().countForPaymentOnScheduleDate(payment.id, cursor.toString())
                    if (already == 0) {
                        db.billOccurrenceDao().insert(
                            BillOccurrenceEntity(
                                payment_id = payment.id,
                                due_date = cursor.toString(),
                                amount_cents = payment.amount_cents,
                                is_paid = 0,
                                original_due_date = cursor.toString(),
                                is_user_modified = 0,
                                created_at = LocalDateTime.now().toString()
                            )
                        )
                    }
                }
                cursor = RecurrenceMath.nextDate(cursor, payment.frequency, dayOfMonth) ?: break
            }
        }
    }

    suspend fun syncOnboardingMilestones() {
        val incomes = db.incomeDao().getAllIncomes().first()
        val payments = db.paymentDao().getAll().first()
        val transactions = db.transactionDao().getAll().first()
        val goals = db.goalDao().getAllGoals().first()
        if (isBalanceReconciled()) {
            markOnboardingMilestone(OnboardingMilestone.RECONCILIATION)
        }
        if (incomes.isNotEmpty()) {
            markOnboardingMilestone(OnboardingMilestone.FIRST_INCOME)
        }
        if (payments.isNotEmpty()) {
            markOnboardingMilestone(OnboardingMilestone.FIRST_BILL)
        }
        if (goals.isNotEmpty()) {
            markOnboardingMilestone(OnboardingMilestone.FIRST_GOAL)
        }
        // Legacy: keep expense milestone in sync for backup compatibility
        if (transactions.any { it.type == "expense" }) {
            markOnboardingMilestone(OnboardingMilestone.FIRST_EXPENSE)
        }
    }

    // Payday processing: record actual received amount as a transaction and advance next_date.
    suspend fun processPayday(income: IncomeEntity, actualAmountCents: Int) {
        val paydayDate = income.next_date
        val isOneTime = RecurrenceMath.normalizeFrequency(income.frequency) == "onetime"

        db.withTransaction {
            db.transactionDao().insert(
                TransactionEntity(
                    description = "Paycheck: ${income.name}",
                    amount_cents = actualAmountCents,
                    date = paydayDate,
                    type = "income"
                )
            )
            if (isBalanceReconciled()) {
                writeBankBalanceCents(getBankBalanceCents() + actualAmountCents)
            }
            if (isOneTime) {
                db.incomeDao().deleteIncome(income)
            } else {
                val newNextDate = advanceIncomeDate(paydayDate, income.frequency, income.day_of_month)
                db.incomeDao().updateIncome(
                    income.copy(
                        next_date = newNextDate,
                        expectedAmountCents = null,
                    )
                )
            }
        }
    }

    private fun advanceIncomeDate(fromDate: String, frequency: String, dayOfMonth: Int? = null): String {
        val date = LocalDate.parse(fromDate)
        val next = RecurrenceMath.nextDate(date, frequency, dayOfMonth) ?: date.plusMonths(1)
        return next.toString()
    }

    // Reconciliation
    suspend fun checkBalanceConsistency(): Triple<Boolean, Int, Int> {
        val bankBalance = getBankBalanceCents()
        val txnSum = db.transactionDao().getTotalBalanceCents().first() ?: 0
        val mismatch = isBalanceReconciled() && bankBalance != txnSum
        return Triple(mismatch, txnSum, bankBalance)
    }

    suspend fun markOnboardingMilestone(milestone: OnboardingMilestone) {
        val key = when (milestone) {
            OnboardingMilestone.RECONCILIATION -> KEY_ONBOARDING_RECONCILIATION
            OnboardingMilestone.FIRST_INCOME -> KEY_ONBOARDING_FIRST_INCOME
            OnboardingMilestone.FIRST_BILL -> KEY_ONBOARDING_FIRST_BILL
            OnboardingMilestone.FIRST_GOAL -> KEY_ONBOARDING_FIRST_GOAL
            OnboardingMilestone.FIRST_EXPENSE -> KEY_ONBOARDING_FIRST_EXPENSE
        }
        db.settingsDao().setValue(SettingsEntity(key, "true"))
    }

    suspend fun markMonitoringModeIntroSeen() {
        db.settingsDao().setValue(SettingsEntity(KEY_ONBOARDING_MONITORING_INTRO_SEEN, "true"))
    }

    suspend fun updateReminderPreferences(preferences: ReminderPreferences) {
        db.settingsDao().setValue(SettingsEntity(KEY_REMINDERS_ENABLED, preferences.enabled.toString()))
        db.settingsDao().setValue(SettingsEntity(KEY_WEEKLY_CHECKIN_ENABLED, preferences.weeklyCheckInEnabled.toString()))
        db.settingsDao().setValue(SettingsEntity(KEY_BILL_REMINDERS_ENABLED, preferences.billRemindersEnabled.toString()))
        db.settingsDao().setValue(
            SettingsEntity(
                KEY_BILL_REMINDER_DAYS_BEFORE,
                preferences.billReminderDaysBefore.coerceIn(1, 14).toString()
            )
        )
    }

    suspend fun enableAppLock(pin: String) {
        val trimmedPin = pin.trim()
        require(trimmedPin.length >= 4) { "App lock PIN must be at least 4 digits." }
        require(trimmedPin.all { it.isDigit() }) { "App lock PIN can only use digits." }

        val hash = SecurityUtils.hashAppLockSecret(trimmedPin.toCharArray())
        db.withTransaction {
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_PIN_SALT, hash.saltBase64))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_PIN_HASH, hash.hashBase64))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_PIN_ITERATIONS, hash.iterations.toString()))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_ENABLED, "true"))
        }
    }

    suspend fun disableAppLock() {
        db.withTransaction {
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_ENABLED, "false"))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_PIN_SALT, ""))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_PIN_HASH, ""))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_PIN_ITERATIONS, "0"))
        }
    }

    suspend fun verifyAppLockPin(pin: String): Boolean {
        val salt = db.settingsDao().getValue(KEY_APP_LOCK_PIN_SALT).orEmpty()
        val hash = db.settingsDao().getValue(KEY_APP_LOCK_PIN_HASH).orEmpty()
        val iterations = db.settingsDao().getValue(KEY_APP_LOCK_PIN_ITERATIONS)?.toIntOrNull() ?: return false
        if (salt.isBlank() || hash.isBlank() || iterations <= 0) return false

        return SecurityUtils.verifyAppLockSecret(
            pin.trim().toCharArray(),
            SecretHash(
                saltBase64 = salt,
                hashBase64 = hash,
                iterations = iterations,
            )
        )
    }

    suspend fun saveTransactionRule(description: String, category: String, priority: Int = 0) {
        val normalizedDescription = normalizeRuleText(description)
        val normalizedCategory = category.trim()
        if (normalizedDescription.isBlank() || normalizedCategory.isBlank()) return
        val existing = db.transactionRuleDao().getByMatchText(normalizedDescription)
        db.transactionRuleDao().upsert(
            TransactionRuleEntity(
                id = existing?.id ?: 0,
                match_text = normalizedDescription,
                category = normalizedCategory,
                is_active = 1,
                priority = priority,
                created_at = existing?.created_at?.takeIf { it.isNotBlank() } ?: LocalDateTime.now().toString(),
            )
        )
    }

    suspend fun installCategoryRulePresets(): Int {
        var installed = 0
        db.withTransaction {
            CategoryRulePresets.rulePairs().forEach { (keyword, category) ->
                val normalizedKeyword = normalizeRuleText(keyword)
                if (normalizedKeyword.isNotBlank()) {
                    val existing = db.transactionRuleDao().getByMatchText(normalizedKeyword)
                    db.transactionRuleDao().upsert(
                        TransactionRuleEntity(
                            id = existing?.id ?: 0,
                            match_text = normalizedKeyword,
                            category = category,
                            is_active = 1,
                            priority = existing?.priority ?: -10,
                            created_at = existing?.created_at?.takeIf { it.isNotBlank() } ?: LocalDateTime.now().toString(),
                        )
                    )
                    if (existing == null) installed += 1
                }
            }
        }
        return installed
    }

    suspend fun deleteTransactionRule(rule: TransactionRuleEntity) {
        db.transactionRuleDao().delete(rule)
    }

    suspend fun restoreBackup(snapshot: LedgerBackupSnapshot) {
        db.withTransaction {
            db.billOccurrenceDao().deleteAllBillOccurrences()
            db.transactionDao().deleteAllTransactions()
            db.paymentDao().deleteAllPayments()
            db.incomeDao().deleteAllIncomes()
            db.settingsDao().deleteAllSettings()
            db.transactionRuleDao().deleteAll()

            snapshot.incomes.forEach { db.incomeDao().insertIncome(it) }
            snapshot.payments.forEach { db.paymentDao().insert(it) }
            snapshot.transactions.forEach { db.transactionDao().insert(it) }
            snapshot.billOccurrences.forEach { db.billOccurrenceDao().insert(it) }
            snapshot.rules.forEach { db.transactionRuleDao().upsert(it) }

            restoreSettings(snapshot)
        }
    }

    private suspend fun restoreSettings(snapshot: LedgerBackupSnapshot) {
        val settingsByKey = snapshot.settings.associateBy { it.key }.toMutableMap()

        fun ensureSetting(key: String, value: String) {
            if (!settingsByKey.containsKey(key)) {
                settingsByKey[key] = SettingsEntity(key, value)
            }
        }

        ensureSetting(KEY_BANK_BALANCE, snapshot.bankBalanceCents.toString())
        ensureSetting(KEY_CURRENT_BALANCE, snapshot.bankBalanceCents.toString())
        ensureSetting(KEY_BANK_BALANCE_RECONCILED, snapshot.isBalanceReconciled.toString())
        ensureSetting(KEY_LEGACY_RECONCILED, snapshot.isBalanceReconciled.toString())
        ensureSetting(KEY_ONBOARDING_FIRST_INCOME, snapshot.onboardingProgress.firstIncomeCompleted.toString())
        ensureSetting(KEY_ONBOARDING_FIRST_BILL, snapshot.onboardingProgress.firstBillCompleted.toString())
        ensureSetting(KEY_ONBOARDING_FIRST_EXPENSE, snapshot.onboardingProgress.firstExpenseCompleted.toString())
        ensureSetting(KEY_ONBOARDING_RECONCILIATION, snapshot.onboardingProgress.reconciliationCompleted.toString())
        ensureSetting(KEY_ONBOARDING_MONITORING_INTRO_SEEN, snapshot.onboardingProgress.isComplete.toString())
        ensureSetting(KEY_STARTING_BALANCE, "0")
        ensureSetting(KEY_SIMULATION_DAYS, "90")

        settingsByKey.values.forEach { setting ->
            db.settingsDao().setValue(setting)
        }
    }

    private fun paymentKey(payment: PaymentEntity): String {
        return listOf(
            payment.name.trim().lowercase(Locale.ROOT),
            payment.amount_cents.toString(),
            payment.frequency.trim().lowercase(Locale.ROOT),
            payment.day_of_month?.toString().orEmpty(),
            payment.next_date.trim(),
            payment.isAutoWithdraw.toString(),
        ).joinToString("|")
    }

    private fun transactionKey(transaction: TransactionEntity): String {
        return listOf(
            transaction.date.trim(),
            transaction.description.trim().lowercase(Locale.ROOT),
            transaction.amount_cents.toString(),
            transaction.type.trim().lowercase(Locale.ROOT),
            transaction.category.trim().lowercase(Locale.ROOT),
        ).joinToString("|")
    }

    private suspend fun applyTransactionRules(entity: TransactionEntity): TransactionEntity {
        val category = normalizeTransactionCategory(entity.category)
        if (category != "uncategorized") return entity.copy(category = category)

        val description = normalizeRuleText(entity.description)
        val rule = db.transactionRuleDao().getActiveRules()
            .firstOrNull { description.contains(it.match_text) }

        return if (rule != null) {
            entity.copy(category = rule.category)
        } else {
            val presetCategory = CategoryRulePresets.inferCategory(entity.description)
            entity.copy(category = presetCategory)
        }
    }

    private suspend fun writeBankBalanceCents(amountCents: Int) {
        db.settingsDao().setValue(SettingsEntity(KEY_BANK_BALANCE, amountCents.toString()))
        db.settingsDao().setValue(SettingsEntity(KEY_CURRENT_BALANCE, amountCents.toString()))
        db.settingsDao().setValue(SettingsEntity(KEY_BANK_BALANCE_RECONCILED, "true"))
        db.settingsDao().setValue(SettingsEntity(KEY_LEGACY_RECONCILED, "true"))
    }

    private suspend fun applyTransactionToBankBalanceIfReconciled(deltaCents: Int) {
        if (isBalanceReconciled()) {
            writeBankBalanceCents(getBankBalanceCents() + deltaCents)
        }
    }

    private fun TransactionEntity.withReviewState(source: String): TransactionEntity {
        val shouldReview = source == SOURCE_CSV_IMPORT || type == "expense"
        return copy(
            source = source,
            review_status = if (shouldReview) REVIEW_PENDING else REVIEW_APPROVED,
            reviewed_at = if (shouldReview) null else LocalDateTime.now().toString(),
        )
    }

    private fun normalizeRuleText(value: String): String {
        return value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
    }

    private fun normalizeTransactionCategory(value: String): String {
        val normalized = value.trim()
        return if (normalized.isBlank() || normalized.equals("uncategorized", ignoreCase = true)) {
            "uncategorized"
        } else {
            normalized
        }
    }

}
