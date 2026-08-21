package com.montecarlo.ledger.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import com.montecarlo.ledger.domain.Categories
import com.montecarlo.ledger.domain.DomainRules
import com.montecarlo.ledger.processing.CategoryRuleEngine
import com.montecarlo.ledger.processing.RecurrenceMath
import com.montecarlo.ledger.data.LedgerBackupSnapshot
import com.montecarlo.ledger.security.SecretHash
import com.montecarlo.ledger.security.SecurityUtils
import java.util.Locale
import com.montecarlo.ledger.util.LedgerDate
import com.montecarlo.ledger.util.toPersistedBoolean

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
        const val KEY_APP_LOCK_FAILED_ATTEMPTS = "app_lock_failed_attempts"
        const val KEY_APP_LOCK_LOCKOUT_UNTIL = "app_lock_lockout_until"
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
    suspend fun deletePayment(entity: PaymentEntity) {
        db.withTransaction {
            // Drop unpaid occurrences so the forecast/timeline does not keep orphan bills.
            val unpaid = db.billOccurrenceDao().getForPayment(entity.id).first()
                .filter { it.is_paid == 0 }
            unpaid.forEach { db.billOccurrenceDao().delete(it) }
            db.paymentDao().delete(entity)
        }
    }

    // Accounts (v1 groundwork: primary pipeline still uses bank_balance settings)
    val allAccounts: Flow<List<AccountEntity>> = db.accountDao().getAll()
    suspend fun insertAccount(account: AccountEntity): Long = db.accountDao().insert(account)
    suspend fun updateAccount(account: AccountEntity) = db.accountDao().update(account)
    suspend fun deleteAccount(account: AccountEntity) = db.accountDao().delete(account)
    suspend fun getDefaultAccount(): AccountEntity? = db.accountDao().getDefault()
    // Transactions
    val allTransactions: Flow<List<TransactionEntity>> = db.transactionDao().getAll()
    val allTransactionRules: Flow<List<TransactionRuleEntity>> = db.transactionRuleDao().getAll()
    suspend fun insertTransaction(entity: TransactionEntity) {
        DomainRules.validateTransactionSign(entity.amount_cents, entity.type)
        val normalized = applyTransactionRules(entity).withReviewState(source = SOURCE_MANUAL)
        db.withTransaction {
            db.transactionDao().insert(normalized)
            applyTransactionToBankBalanceIfReconciled(normalized.amount_cents)
            if (normalized.type == "expense") {
                markOnboardingMilestone(OnboardingMilestone.FIRST_EXPENSE)
            }
        }
    }
    /**
     * Bulk-import transactions (CSV). Dedupes against existing rows.
     * When the bank balance is reconciled, applies the net imported delta so
     * ledger sum and bank balance stay aligned (no silent drift).
     */
    suspend fun importTransactions(transactions: List<TransactionEntity>) {
        if (transactions.isEmpty()) return
        transactions.forEach { DomainRules.validateTransactionSign(it.amount_cents, it.type) }
        // Multiset dedupe: skip only as many copies per key as the ledger already
        // holds. Identical same-day charges are real distinct transactions, so they
        // must import, while re-importing the same statement stays a no-op.
        val existingKeyCounts = db.transactionDao().getAll().first()
            .groupingBy { transactionKey(it) }
            .eachCount()
        val skippedPerKey = mutableMapOf<String, Int>()
        val normalizedTransactions = transactions.map {
            applyTransactionRules(it).withReviewState(source = SOURCE_CSV_IMPORT)
        }
        val uniqueTransactions = normalizedTransactions.filter { entity ->
            val key = transactionKey(entity)
            val alreadyHeld = existingKeyCounts[key] ?: 0
            val alreadySkipped = skippedPerKey.getOrDefault(key, 0)
            if (alreadySkipped < alreadyHeld) {
                skippedPerKey[key] = alreadySkipped + 1
                false
            } else {
                true
            }
        }
        if (uniqueTransactions.isEmpty()) return
        db.withTransaction {
            uniqueTransactions.forEach { entity ->
                db.transactionDao().insert(entity)
            }
            // Keep reconciled bank balance in lockstep with imported ledger rows.
            if (isBalanceReconciled()) {
                val importDelta = uniqueTransactions.sumOf { it.amount_cents }
                if (importDelta != 0L) {
                    writeBankBalanceCents(getBankBalanceCents() + importDelta)
                }
            }
        }
        if (uniqueTransactions.any { it.type == "expense" }) {
            markOnboardingMilestone(OnboardingMilestone.FIRST_EXPENSE)
        }
    }
    suspend fun insertTransaction(entity: TransactionEntity, linkedOccurrenceId: Int): Long =
        db.withTransaction {
            DomainRules.validateTransactionSign(entity.amount_cents, entity.type)
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
        DomainRules.validateTransactionSign(entity.amount_cents, entity.type)
        val existing = db.transactionDao().getById(entity.id)
        val reviewed = applyTransactionRules(entity).copy(
            review_status = REVIEW_APPROVED,
            reviewed_at = entity.reviewed_at ?: LocalDateTime.now().toString(),
        )
        db.withTransaction {
            db.transactionDao().update(reviewed)
            if (existing != null) {
                applyTransactionToBankBalanceIfReconciled(reviewed.amount_cents - existing.amount_cents)
            }
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
        db.withTransaction {
            db.transactionDao().delete(entity)
            applyTransactionToBankBalanceIfReconciled(-entity.amount_cents)
        }
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

    // Category Budgets (soft watchlists)
    val allCategoryBudgets: Flow<List<CategoryBudgetEntity>> = db.categoryBudgetDao().getAll()
    suspend fun upsertCategoryBudget(budget: CategoryBudgetEntity) {
        val normalized = budget.copy(category = Categories.normalize(budget.category))
        val existing = db.categoryBudgetDao().getAll().first()
            .firstOrNull { Categories.normalize(it.category) == normalized.category }
        if (existing != null) {
            db.categoryBudgetDao().update(normalized.copy(id = existing.id))
        } else {
            db.categoryBudgetDao().insert(normalized)
        }
    }
    suspend fun deleteCategoryBudget(budget: CategoryBudgetEntity) = db.categoryBudgetDao().delete(budget)

    // Debts
    val allDebts: Flow<List<DebtEntity>> = db.debtDao().getAll()
    suspend fun insertDebt(debt: DebtEntity) = db.debtDao().insert(debt)
    suspend fun updateDebt(debt: DebtEntity) = db.debtDao().update(debt)
    suspend fun deleteDebt(debt: DebtEntity) = db.debtDao().delete(debt)

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
        val bankBalanceCents: Long,
        val isReconciled: Boolean,
    )

    sealed class AppLockVerifyResult {
        data object Success : AppLockVerifyResult()
        data class LockedOut(val remainingSeconds: Long) : AppLockVerifyResult()
        data class Invalid(val message: String) : AppLockVerifyResult()
    }

    val balanceState: Flow<BalanceState> = db.settingsDao().getAll().map { settings ->
        val byKey = settings.associateBy { it.key }
        val bankBalance = byKey[KEY_BANK_BALANCE]?.value?.toLongOrNull()
            ?: byKey[KEY_CURRENT_BALANCE]?.value?.toLongOrNull()
            ?: 0L
        val reconciled = byKey[KEY_BANK_BALANCE_RECONCILED]?.value.toPersistedBoolean() ||
            byKey[KEY_LEGACY_RECONCILED]?.value.toPersistedBoolean()
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
            failedAttempts = byKey[KEY_APP_LOCK_FAILED_ATTEMPTS]?.value?.toIntOrNull() ?: 0,
            lockoutUntilEpochMs = byKey[KEY_APP_LOCK_LOCKOUT_UNTIL]?.value?.toLongOrNull() ?: 0L,
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

    suspend fun getBankBalanceCents(): Long {
        val bankBalance = db.settingsDao().getValue(KEY_BANK_BALANCE)?.toLongOrNull()
        if (bankBalance != null) return bankBalance

        val legacyBalance = db.settingsDao().getValue(KEY_CURRENT_BALANCE)?.toLongOrNull() ?: 0L
        db.settingsDao().setValue(SettingsEntity(KEY_BANK_BALANCE, legacyBalance.toString()))
        return legacyBalance
    }

    suspend fun isBalanceReconciled(): Boolean {
        val reconciled = db.settingsDao().getValue(KEY_BANK_BALANCE_RECONCILED)
        if (reconciled != null) return reconciled.toPersistedBoolean()

        return db.settingsDao().getValue(KEY_LEGACY_RECONCILED).toPersistedBoolean()
    }

    suspend fun setBankBalance(amountCents: Long) =
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
        val parsedDueDate = LedgerDate.parseIsoOrNull(dueDate)
            ?: throw IllegalArgumentException("Enter a valid due date.")
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
        db.withTransaction {
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

    /**
     * Record actual received amount as a transaction and advance [IncomeEntity.next_date].
     *
     * Idempotent per paycheck period: a second call for the same income name + next_date
     * is a no-op (guards double-tap double-counting on recurring income).
     */
    suspend fun processPayday(income: IncomeEntity, actualAmountCents: Long) {
        DomainRules.validateTransactionSign(actualAmountCents, "income")
        val paydayDate = income.next_date
        val description = "Paycheck: ${income.name}"

        db.withTransaction {
            // Already recorded this period (double-tap / stale UI).
            val alreadyRecorded =
                db.transactionDao().countIncomeByDescriptionAndDate(description, paydayDate) > 0
            if (alreadyRecorded) return@withTransaction

            db.transactionDao().insert(
                TransactionEntity(
                    description = description,
                    amount_cents = actualAmountCents,
                    date = paydayDate,
                    type = "income"
                )
            )
            if (isBalanceReconciled()) {
                writeBankBalanceCents(getBankBalanceCents() + actualAmountCents)
            }

            // Re-read so concurrent advances cannot apply twice from a stale entity.
            val current = db.incomeDao().getIncomeById(income.id) ?: return@withTransaction
            if (current.next_date != paydayDate) return@withTransaction

            val isOneTime = RecurrenceMath.normalizeFrequency(current.frequency) == "onetime"
            if (isOneTime) {
                db.incomeDao().deleteIncome(current)
            } else {
                val newNextDate = advanceIncomeDate(paydayDate, current.frequency, current.day_of_month)
                db.incomeDao().updateIncome(
                    current.copy(
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
    suspend fun checkBalanceConsistency(): Triple<Boolean, Long, Long> {
        val bankBalance = getBankBalanceCents()
        val txnSum = db.transactionDao().getTotalBalanceCents().first() ?: 0L
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
            // Clear any stale throttle state when setting a new PIN.
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_FAILED_ATTEMPTS, "0"))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_LOCKOUT_UNTIL, "0"))
        }
    }

    suspend fun disableAppLock() {
        db.withTransaction {
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_ENABLED, "false"))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_PIN_SALT, ""))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_PIN_HASH, ""))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_PIN_ITERATIONS, "0"))
            // Clear throttle state so re-enabling starts fresh.
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_FAILED_ATTEMPTS, "0"))
            db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_LOCKOUT_UNTIL, "0"))
        }
    }

    suspend fun verifyAppLockPin(pin: String): AppLockVerifyResult {
        val now = System.currentTimeMillis()
        val state = readAppLockThrottleState()

        // Reject immediately while locked out.
        if (AppLockThrottle.isLockedOut(now, state.lockoutUntilEpochMs)) {
            val remaining = AppLockThrottle.remainingLockoutMs(now, state.lockoutUntilEpochMs)
            return AppLockVerifyResult.LockedOut(remaining / 1000)
        }

        val salt = db.settingsDao().getValue(KEY_APP_LOCK_PIN_SALT).orEmpty()
        val hash = db.settingsDao().getValue(KEY_APP_LOCK_PIN_HASH).orEmpty()
        val iterations = db.settingsDao().getValue(KEY_APP_LOCK_PIN_ITERATIONS)?.toIntOrNull()
        if (salt.isBlank() || hash.isBlank() || iterations == null || iterations <= 0) {
            return AppLockVerifyResult.Invalid("App lock not configured.")
        }

        val verified = SecurityUtils.verifyAppLockSecret(
            pin.trim().toCharArray(),
            SecretHash(
                saltBase64 = salt,
                hashBase64 = hash,
                iterations = iterations,
            )
        )

        return if (verified) {
            saveAppLockThrottleState(AppLockThrottle.onSuccess())
            AppLockVerifyResult.Success
        } else {
            val newState = AppLockThrottle.onFailure(state, now)
            saveAppLockThrottleState(newState)
            val remaining = AppLockThrottle.remainingLockoutMs(now, newState.lockoutUntilEpochMs)
            if (remaining > 0) {
                AppLockVerifyResult.LockedOut(remaining / 1000)
            } else {
                AppLockVerifyResult.Invalid("Incorrect PIN.")
            }
        }
    }

    private suspend fun readAppLockThrottleState(): AppLockThrottle.ThrottleState {
        val failedAttempts = db.settingsDao().getValue(KEY_APP_LOCK_FAILED_ATTEMPTS)
            ?.toIntOrNull() ?: 0
        val lockoutUntil = db.settingsDao().getValue(KEY_APP_LOCK_LOCKOUT_UNTIL)
            ?.toLongOrNull() ?: 0L
        return AppLockThrottle.ThrottleState(failedAttempts, lockoutUntil)
    }

    private suspend fun saveAppLockThrottleState(state: AppLockThrottle.ThrottleState) {
        db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_FAILED_ATTEMPTS, state.failedAttempts.toString()))
        db.settingsDao().setValue(SettingsEntity(KEY_APP_LOCK_LOCKOUT_UNTIL, state.lockoutUntilEpochMs.toString()))
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

    suspend fun eraseAllData() {
        db.withTransaction {
            db.billOccurrenceDao().deleteAllBillOccurrences()
            db.transactionDao().deleteAllTransactions()
            db.paymentDao().deleteAllPayments()
            db.incomeDao().deleteAllIncomes()
            db.settingsDao().deleteAllSettings()
            db.transactionRuleDao().deleteAll()
            db.assetDao().deleteAllAssets()
            db.goalDao().deleteAllGoals()
            db.categoryBudgetDao().deleteAll()
            db.debtDao().deleteAll()
        }
    }

    suspend fun restoreBackup(snapshot: LedgerBackupSnapshot) {
        db.withTransaction {
            val preservedLockSettings = db.settingsDao().getAll().first()
                .filter { isAppLockSettingKey(it.key) }

            // Full wipe so restore never mixes prior assets/goals with restored ledger rows.
            db.billOccurrenceDao().deleteAllBillOccurrences()
            db.transactionDao().deleteAllTransactions()
            db.paymentDao().deleteAllPayments()
            db.incomeDao().deleteAllIncomes()
            db.settingsDao().deleteAllSettings()
            db.transactionRuleDao().deleteAll()
            db.assetDao().deleteAllAssets()
            db.goalDao().deleteAllGoals()
            db.categoryBudgetDao().deleteAll()
            db.debtDao().deleteAll()

            snapshot.incomes.forEach { db.incomeDao().insertIncome(it) }
            snapshot.payments.forEach { db.paymentDao().insert(it) }
            snapshot.transactions.forEach { db.transactionDao().insert(it) }
            snapshot.billOccurrences.forEach { db.billOccurrenceDao().insert(it) }
            snapshot.rules.forEach { db.transactionRuleDao().upsert(it) }
            snapshot.assets.forEach { db.assetDao().insertAsset(it) }
            snapshot.goals.forEach { db.goalDao().insertGoal(it) }
            snapshot.categoryBudgets.forEach { db.categoryBudgetDao().insert(it) }
            snapshot.debts.forEach { db.debtDao().insert(it) }

            restoreSettings(snapshot)
            preservedLockSettings.forEach { db.settingsDao().setValue(it) }
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
        ensureSetting(KEY_ONBOARDING_FIRST_GOAL, snapshot.onboardingProgress.firstGoalCompleted.toString())
        ensureSetting(KEY_ONBOARDING_RECONCILIATION, snapshot.onboardingProgress.reconciliationCompleted.toString())
        ensureSetting(KEY_ONBOARDING_MONITORING_INTRO_SEEN, snapshot.onboardingProgress.isComplete.toString())
        ensureSetting(KEY_STARTING_BALANCE, "0")
        ensureSetting(KEY_SIMULATION_DAYS, "90")

        settingsByKey.values
            .filterNot { isAppLockSettingKey(it.key) }
            .forEach { setting ->
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

    fun getTransactionRulesFlow(): Flow<List<TransactionRuleEntity>> = db.transactionRuleDao().getAll()

    suspend fun addTransactionRule(matchText: String, category: String, applyRetroactively: Boolean = false): Long {
        val normalizedMatch = normalizeRuleText(matchText)
        require(normalizedMatch.isNotBlank()) { "Rule match text cannot be blank." }
        require(category.isNotBlank() && !category.equals("uncategorized", ignoreCase = true)) {
            "Rule category must be a valid non-uncategorized category."
        }

        val rule = TransactionRuleEntity(
            match_text = normalizedMatch,
            category = category.trim().lowercase(Locale.ROOT),
            is_active = 1,
            priority = 0,
            created_at = LocalDateTime.now().toString(),
        )
        val ruleId = db.transactionRuleDao().upsert(rule)

        if (applyRetroactively) {
            applyRuleRetroactively(rule)
        }
        return ruleId
    }

    suspend fun applyRuleRetroactively(rule: TransactionRuleEntity) {
        val normalizedMatch = normalizeRuleText(rule.match_text)
        if (normalizedMatch.isBlank() || rule.is_active == 0) return

        val allTxns = db.transactionDao().getAll().first()
        val targetTxns = allTxns.filter { txn ->
            val desc = normalizeRuleText(txn.description)
            desc.contains(normalizedMatch) && Categories.isUncategorized(txn.category)
        }

        db.withTransaction {
            targetTxns.forEach { txn ->
                db.transactionDao().update(txn.copy(category = rule.category))
            }
        }
    }

    private suspend fun applyTransactionRules(entity: TransactionEntity): TransactionEntity {
        val category = normalizeTransactionCategory(entity.category)
        if (category != "uncategorized") return entity.copy(category = category)

        val activeRules = db.transactionRuleDao().getActiveRules()
        val result = CategoryRuleEngine.categorize(entity.description, activeRules)
        return entity.copy(category = result.category)
    }

    private suspend fun writeBankBalanceCents(amountCents: Long) {
        db.withTransaction {
            db.settingsDao().setValue(SettingsEntity(KEY_BANK_BALANCE, amountCents.toString()))
            db.settingsDao().setValue(SettingsEntity(KEY_CURRENT_BALANCE, amountCents.toString()))
            db.settingsDao().setValue(SettingsEntity(KEY_BANK_BALANCE_RECONCILED, "true"))
            db.settingsDao().setValue(SettingsEntity(KEY_LEGACY_RECONCILED, "true"))
        }
    }

    private suspend fun applyTransactionToBankBalanceIfReconciled(deltaCents: Long) {
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

    private fun normalizeRuleText(value: String): String = Categories.normalize(value)

    private fun normalizeTransactionCategory(value: String): String = Categories.normalizeOrUncategorized(value)

}
