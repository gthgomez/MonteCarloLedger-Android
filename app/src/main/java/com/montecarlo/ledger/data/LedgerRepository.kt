package com.montecarlo.ledger.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import com.montecarlo.ledger.contract.ContractMoney
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
import com.montecarlo.ledger.util.toPersistedValue
import com.montecarlo.ledger.sync.CanonicalVaultMapper
import com.montecarlo.ledger.sync.CanonicalIdMapping
import com.montecarlo.ledger.sync.LocalLedgerPreservation
import com.montecarlo.ledger.sync.VaultDestructivePullGuard
import com.montecarlo.ledger.sync.VaultPullRestoreBaselines
import com.montecarlo.ledger.sync.VaultPullRestoreGuardState
import com.montecarlo.ledger.sync.VaultSnapshotFingerprint
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

class LedgerRepository(private val db: AppDatabase) {

    private suspend fun <T> exclusiveLedgerWrite(block: suspend () -> T): T =
        ledgerWriteMutex.withLock { block() }

    private companion object {
        private val ledgerWriteMutex = Mutex()
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
        const val KEY_ONBOARDING_DISMISSED = "onboarding_dismissed"
        const val KEY_ONBOARDING_MONITORING_INTRO_SEEN = "onboarding_monitoring_intro_seen"
        const val KEY_CONTRACT_FORECAST_ENABLED = "contract_forecast_enabled"
    }

    // Income
    val allIncome: Flow<List<IncomeEntity>> = db.incomeDao().getAllIncomes()
    suspend fun insertIncome(entity: IncomeEntity) {
        db.incomeDao().insertIncome(entity)
        markOnboardingMilestone(OnboardingMilestone.FIRST_INCOME)
    }
    suspend fun updateIncome(entity: IncomeEntity) = exclusiveLedgerWrite {
        db.incomeDao().updateIncome(entity)
    }
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
    suspend fun updatePayment(entity: PaymentEntity) = db.withTransaction {
        val previous = db.paymentDao().getById(entity.id)
            ?: throw IllegalArgumentException("This bill no longer exists.")
        if (CanonicalVaultMapper.isLinkable(previous) && !CanonicalVaultMapper.isLinkable(entity)) {
            require(db.debtDao().getActive().first().none { it.linkedPaymentId == entity.id }) {
                "This bill is linked to an active debt. Remove the link before changing to this schedule."
            }
        }
        db.paymentDao().update(entity)
    }
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
    suspend fun insertAccount(account: AccountEntity): Long = exclusiveLedgerWrite {
        db.accountDao().insert(account)
    }
    suspend fun updateAccount(account: AccountEntity) = db.accountDao().update(account)
    suspend fun deleteAccount(account: AccountEntity) = db.accountDao().delete(account)
    suspend fun upsertAccount(account: AccountEntity) {
        if (account.isDefault && account.id != 0L) { setDefaultAccount(account.id); return }
        db.accountDao().insert(account)
    }
    suspend fun getDefaultAccount(): AccountEntity? = db.accountDao().getDefault()
    // Transactions
    val allTransactions: Flow<List<TransactionEntity>> = db.transactionDao().getAll()
    val allTransactionRules: Flow<List<TransactionRuleEntity>> = db.transactionRuleDao().getAll()
    suspend fun insertTransaction(entity: TransactionEntity) = exclusiveLedgerWrite {
        DomainRules.validateTransactionSign(entity.amount_cents, entity.type)
        val normalized = applyTransactionRules(entity).withReviewState(source = SOURCE_MANUAL)
        db.withTransaction {
            db.transactionDao().insert(normalized)
            applyTransactionSideEffects(normalized.amount_cents, normalized.account_id)
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
            val occurrence = db.billOccurrenceDao().getById(linkedOccurrenceId)
                ?: throw IllegalArgumentException("Occurrence not found.")
            DomainRules.validateOccurrenceLink(occurrence, normalized)

            val existingTransactionId = occurrence.transaction_id
            if (existingTransactionId != null) {
                val existing = db.transactionDao().getById(existingTransactionId)
                DomainRules.validateOccurrenceLink(occurrence, existing)
                if (occurrence.is_paid == 0) {
                    db.billOccurrenceDao().markPaid(linkedOccurrenceId, existingTransactionId)
                }
                return@withTransaction existingTransactionId.toLong()
            }
            if (occurrence.is_paid != 0) {
                throw IllegalArgumentException("This bill occurrence has already been paid.")
            }

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

    suspend fun updateTransaction(
        entity: TransactionEntity,
        expectedOriginal: TransactionEntity? = null,
    ) {
        DomainRules.validateTransactionSign(entity.amount_cents, entity.type)
        val reviewed = applyTransactionRules(entity).copy(
            review_status = REVIEW_APPROVED,
            reviewed_at = entity.reviewed_at ?: LocalDateTime.now().toString(),
        )
        db.withTransaction {
            val existing = db.transactionDao().getById(entity.id)
                ?: throw IllegalArgumentException("This transaction no longer exists.")
            if (expectedOriginal != null && existing != expectedOriginal) {
                throw IllegalArgumentException(
                    "This transaction changed since you opened it. Reopen it and try again."
                )
            }
            if (db.billOccurrenceDao().countLinkedTo(existing.id) > 0) {
                require(entity.type == existing.type) {
                    "This payment is linked to a bill and must remain an expense. " +
                        "Delete the payment to reopen the bill, then record the correction."
                }
                require(entity.amount_cents == existing.amount_cents) {
                    "This payment is linked to a bill. Delete the payment to reopen the bill; " +
                        "its amount stays the scheduled amount, so edit the bill first, " +
                        "then record the payment again."
                }
                require(entity.account_id == existing.account_id) {
                    "This payment is linked to a bill and cannot move to another account. " +
                        "Delete the payment to reopen the bill, then record it again."
                }
            }
            revertTransactionSideEffects(existing.amount_cents, existing.account_id)
            db.transactionDao().update(reviewed)
            applyTransactionSideEffects(reviewed.amount_cents, reviewed.account_id)
        }
    }
    suspend fun approveTransactionReview(transactionId: Int) {
        db.transactionDao().updateReviewState(
            transactionId,
            REVIEW_APPROVED,
            LocalDateTime.now().toString(),
        )
    }

    /**
     * Move a transaction between pending and posted clearing states.
     * Pure state change: pending charges are committed money and already count
     * toward balances, so posting never re-applies an amount.
     */
    suspend fun setTransactionClearingStatus(transactionId: Int, pending: Boolean) {
        val status = if (pending) ClearingStatus.PENDING else ClearingStatus.POSTED
        db.transactionDao().updateClearingStatus(
            transactionId,
            ClearingStatus.normalize(status),
        )
    }

    suspend fun createRuleFromTransactionReview(transactionId: Int, category: String) {
        val trimmedCategory = category.trim()
        if (trimmedCategory.isBlank()) {
            approveTransactionReview(transactionId)
            return
        }

        db.withTransaction {
            val existing = db.transactionDao().getById(transactionId)
                ?: throw IllegalArgumentException("This transaction no longer exists.")
            val categorized = applyTransactionRules(existing.copy(category = trimmedCategory))
            saveTransactionRule(existing.description, trimmedCategory)
            db.transactionDao().updateCategoryAndReviewState(
                id = transactionId,
                category = categorized.category,
                reviewStatus = REVIEW_APPROVED,
                reviewedAt = existing.reviewed_at ?: LocalDateTime.now().toString(),
            )
        }
    }
    suspend fun deleteTransaction(
        entity: TransactionEntity,
        expectedOriginal: TransactionEntity? = null,
    ) {
        db.withTransaction {
            val existing = db.transactionDao().getById(entity.id) ?: return@withTransaction
            if (expectedOriginal != null && existing != expectedOriginal) {
                throw IllegalArgumentException(
                    "This transaction changed since you opened it. Reopen it and try again."
                )
            }
            // A deleted settling transaction must not leave the bill marked paid: reopen every
            // occurrence that used it, preserving its moved slot and amount, then delete the cash row.
            db.billOccurrenceDao().reopenLinkedTo(existing.id)
            db.transactionDao().delete(existing)
            revertTransactionSideEffects(existing.amount_cents, existing.account_id)
        }
    }

    // Assets
    val allAssets: Flow<List<AssetEntity>> = db.assetDao().getAllAssets()
    val totalAssetBalance: Flow<Long> = db.assetDao().getTotalAssetBalance().map { it ?: 0L }
    suspend fun insertAsset(entity: AssetEntity) = exclusiveLedgerWrite {
        db.assetDao().insertAsset(entity)
    }
    suspend fun updateAsset(entity: AssetEntity) = db.assetDao().updateAsset(entity)
    suspend fun deleteAsset(entity: AssetEntity) = db.assetDao().deleteAsset(entity)

    // Goals
    val allGoals: Flow<List<GoalEntity>> = db.goalDao().getAllGoals()
    suspend fun insertGoal(goal: GoalEntity) = exclusiveLedgerWrite {
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
    suspend fun insertDebt(debt: DebtEntity): Long = db.withTransaction {
        validateActiveDebtLink(debt, existing = null)
        db.debtDao().insert(debt)
    }
    suspend fun updateDebt(debt: DebtEntity) = db.withTransaction {
        val existing = db.debtDao().getAll().first().firstOrNull { it.id == debt.id }
            ?: throw IllegalArgumentException("This debt no longer exists.")
        validateActiveDebtLink(debt, existing)
        db.debtDao().update(debt)
    }
    suspend fun deleteDebt(debt: DebtEntity) = db.debtDao().delete(debt)

    private suspend fun validateActiveDebtLink(debt: DebtEntity, existing: DebtEntity?) {
        val activeDebts = db.debtDao().getActive().first()
        activeDebtLinkValidationError(
            candidate = debt,
            existing = existing,
            paymentsById = db.paymentDao().getAllPaymentsList().associateBy { it.id },
            activeDebts = activeDebts,
        )?.let { throw IllegalArgumentException(it) }
    }

    val onboardingProgress: Flow<OnboardingProgress> = db.settingsDao().getAll().map { settings ->
        val byKey = settings.associateBy { it.key }
        OnboardingProgress(
            reconciliationCompleted = byKey[KEY_ONBOARDING_RECONCILIATION]?.value?.toBoolean() == true,
            firstIncomeCompleted = byKey[KEY_ONBOARDING_FIRST_INCOME]?.value?.toBoolean() == true,
            firstBillCompleted = byKey[KEY_ONBOARDING_FIRST_BILL]?.value?.toBoolean() == true,
            firstGoalCompleted = byKey[KEY_ONBOARDING_FIRST_GOAL]?.value?.toBoolean() == true,
            firstExpenseCompleted = byKey[KEY_ONBOARDING_FIRST_EXPENSE]?.value?.toBoolean() == true,
            dismissed = byKey[KEY_ONBOARDING_DISMISSED]?.value?.toBoolean() == true,
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

    /**
     * Persisted contract-adoption flag (MC-06b). Absent means ON so existing installs adopt the
     * contract engine without a manual opt-in; a stored "false" falls back to the native engines.
     */
    val contractForecastEnabled: Flow<Boolean> = db.settingsDao().getAll().map { settings ->
        settings.firstOrNull { it.key == KEY_CONTRACT_FORECAST_ENABLED }?.value?.toPersistedBoolean()
            ?: true
    }

    suspend fun getContractForecastEnabled(): Boolean =
        db.settingsDao().getValue(KEY_CONTRACT_FORECAST_ENABLED)?.toPersistedBoolean() ?: true

    suspend fun setContractForecastEnabled(enabled: Boolean) {
        db.settingsDao().setValue(
            SettingsEntity(KEY_CONTRACT_FORECAST_ENABLED, enabled.toPersistedValue())
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

    suspend fun setBankBalance(amountCents: Long) = exclusiveLedgerWrite {
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

    suspend fun skipBillOccurrence(id: Int) = db.withTransaction {
        val occurrence = db.billOccurrenceDao().getById(id)
            ?: throw IllegalArgumentException("Occurrence not found.")
        require(occurrence.transaction_id == null) {
            "This bill has a recorded payment. Delete the payment to reopen the bill instead of skipping it."
        }
        db.billOccurrenceDao().markPaid(occurrence.id, null)
    }

    suspend fun rescheduleBillOccurrence(id: Int, dueDate: String) = db.withTransaction {
        val parsedDueDate = LedgerDate.parseIsoOrNull(dueDate)
            ?: throw IllegalArgumentException("Enter a valid due date.")
        val occurrence = db.billOccurrenceDao().getById(id)
            ?: throw IllegalArgumentException("Occurrence not found.")
        require(occurrence.is_paid == 0) {
            "This bill has already been recorded. Delete the payment to reopen it before moving the due date."
        }
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
                var cursor = com.montecarlo.ledger.processing.PaymentSchedule.firstDueInOrBeforeWindow(
                    payment.frequency, payment.day_of_month, payment.next_date, today,
                ) ?: throw IllegalArgumentException("Invalid payment schedule.")
                val ruleOnly = payment.next_date.isEmpty()
                val freq = RecurrenceMath.normalizeFrequency(payment.frequency)
                val isOneTime = freq == "onetime"
                // MCD-0022 / bug B-07: anchor to the schedule's day, not the clamped cursor day.
                val dayOfMonth = payment.day_of_month ?: cursor.dayOfMonth
                db.billOccurrenceDao().deleteUnpaidForPaymentBetween(payment.id, lookback, horizon)
                // Rewind cursor to cover the 30-day lookback window for recurring payments
                if (!isOneTime && !ruleOnly) {
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
            // Legacy repair for rows written before deleting a payment reopened its bill.
            // Only links whose transaction is missing are cleared; paid/null skip markers are untouched.
            db.billOccurrenceDao().reopenDanglingLinks()
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
        db.withTransaction {
            // The persisted schedule identifies the payable slot. Advancing or deleting it
            // within this transaction makes a stale retry a no-op, regardless of other names.
            val current = db.incomeDao().getIncomeById(income.id) ?: return@withTransaction
            if (current.next_date != paydayDate) return@withTransaction
            db.transactionDao().insert(
                TransactionEntity(
                    description = "Paycheck: ${current.name}",
                    amount_cents = actualAmountCents,
                    date = paydayDate,
                    type = "income"
                )
            )
            if (isBalanceReconciled()) {
                writeBankBalanceCents(getBankBalanceCents() + actualAmountCents)
            }

            val isOneTime = RecurrenceMath.normalizeFrequency(current.frequency) == "onetime"
            if (isOneTime) {
                db.incomeDao().deleteIncome(current)
            } else {
                val newNextDate = advanceIncomeDate(paydayDate, current.frequency, current.day_of_month)
                db.incomeDao().updateIncome(
                    current.copy(
                        next_date = newNextDate,
                        last_date = paydayDate,
                        expectedAmountCents = null,
                    )
                )
            }
        }
    }

    private fun advanceIncomeDate(fromDate: String, frequency: String, dayOfMonth: Int? = null): String {
        val date = LocalDate.parse(fromDate)
        // MCD-0022 / bug B-07: anchor to the income's day, not the clamped date's day.
        val anchorDay = dayOfMonth ?: date.dayOfMonth
        val next = RecurrenceMath.nextDate(date, frequency, anchorDay) ?: date.plusMonths(1)
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

    /**
     * Records that the user chose to proceed without finishing setup. One-way in
     * the UI: [syncOnboardingMilestones] and milestone marking never clear it, and
     * [OnboardingProgress.isComplete] is unaffected — setup only completes when
     * the underlying milestones are actually done.
     */
    suspend fun setOnboardingDismissed() {
        db.settingsDao().setValue(SettingsEntity(KEY_ONBOARDING_DISMISSED, "true"))
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
            db.accountDao().deleteAll()
        }
        ensureDefaultAccountSeeded()
    }

    suspend fun restoreBackup(snapshot: LedgerBackupSnapshot) = exclusiveLedgerWrite {
        // Re-validate restored rows so a hand-edited or legacy backup cannot smuggle
        // invalid financial signs (e.g. a positive "expense") into the ledger.
        snapshot.transactions.forEach { DomainRules.validateTransactionSign(it.amount_cents, it.type) }
        validateBackupBillLinks(snapshot)
        validateBackupActiveDebtLinks(snapshot)
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
            db.accountDao().deleteAll()

            snapshot.accounts.forEach { db.accountDao().insert(it) }
            snapshot.incomes.forEach { db.incomeDao().insertIncome(it) }
            snapshot.payments.forEach { db.paymentDao().insert(it) }
            snapshot.transactions.forEach { db.transactionDao().insert(it) }
            snapshot.billOccurrences.forEach { db.billOccurrenceDao().insert(it) }
            // Old backups can contain a paid occurrence whose settling transaction was deleted by
            // an earlier build. Reopen only those missing links; paid/null skip rows stay untouched.
            db.billOccurrenceDao().reopenDanglingLinks()
            snapshot.rules.forEach { db.transactionRuleDao().upsert(it) }
            snapshot.assets.forEach { db.assetDao().insertAsset(it) }
            snapshot.goals.forEach { db.goalDao().insertGoal(it) }
            snapshot.categoryBudgets.forEach { db.categoryBudgetDao().insert(it) }
            snapshot.debts.forEach { db.debtDao().insert(it) }

            restoreSettings(snapshot)
            preservedLockSettings.forEach { db.settingsDao().setValue(it) }
            // Accounts reflect the restored state; reseed if the snapshot predates v5.
            ensureDefaultAccountSeeded()
        }
    }

    /**
     * Fail closed before the wipe when a backup carries a link that Room would otherwise
     * preserve but the canonical vault rejects. A link whose settling transaction is
     * genuinely absent stays repairable: [BillOccurrenceDao.reopenDanglingLinks] clears it
     * after insert, matching the legacy-backup repair path.
     */
    private fun validateBackupBillLinks(snapshot: LedgerBackupSnapshot) {
        val paymentsById = snapshot.payments.associateBy { it.id }
        val transactionsById = snapshot.transactions.associateBy { it.id }
        val occupiedOriginalSlots = HashSet<Pair<Int, String>>()
        val linkedTransactionIds = HashSet<Int>()
        snapshot.billOccurrences.forEach { occurrence ->
            require(paymentsById.containsKey(occurrence.payment_id)) {
                "Backup bill occurrence references a missing bill."
            }
            val originalSlot = occurrence.original_due_date ?: occurrence.due_date
            require(occupiedOriginalSlots.add(occurrence.payment_id to originalSlot)) {
                "Backup has duplicate bill occurrence slots for one bill."
            }
            val transactionId = occurrence.transaction_id ?: return@forEach
            val transaction = transactionsById[transactionId] ?: return@forEach
            require(linkedTransactionIds.add(transactionId)) {
                "Backup links one payment transaction to multiple bill occurrences."
            }
            require(occurrence.is_paid != 0) {
                "Backup bill occurrence is unpaid but still linked to a payment."
            }
            // Compare against the occurrence's own amount, not the bill template: a settled
            // occurrence legitimately keeps its historical amount after the bill is edited.
            DomainRules.validateOccurrenceLink(occurrence, transaction)
        }
    }

    private fun validateBackupActiveDebtLinks(snapshot: LedgerBackupSnapshot) {
        val linkedPaymentIds = HashSet<Int>()
        snapshot.debts.filter { it.isActive }.forEach { debt ->
            val linkedPaymentId = debt.linkedPaymentId ?: return@forEach
            require(linkedPaymentIds.add(linkedPaymentId)) {
                "Backup has multiple active debts linked to the same bill."
            }
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
        ensureSetting(KEY_ONBOARDING_DISMISSED, snapshot.onboardingProgress.dismissed.toString())
        ensureSetting(KEY_ONBOARDING_MONITORING_INTRO_SEEN, snapshot.onboardingProgress.isComplete.toString())
        ensureSetting(KEY_CONTRACT_FORECAST_ENABLED, "true")
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
                db.transactionDao().updateCategoryIfUncategorized(txn.id, rule.category)
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
            syncDefaultAccountRow(amountCents, reconciled = true)
        }
    }

    /**
     * Keeps the default account row in lockstep with the primary balance pipeline.
     * Accounts are the adopted surface; settings remain the legacy/backup mirror.
     */
    private suspend fun syncDefaultAccountRow(amountCents: Long, reconciled: Boolean) {
        val today = LocalDate.now().toString()
        val existing = db.accountDao().getDefault()
        if (existing != null) {
            if (existing.balanceCents != amountCents || existing.isReconciled != reconciled) {
                db.accountDao().update(
                    existing.copy(balanceCents = amountCents, isReconciled = reconciled, lastUpdated = today)
                )
            }
        } else {
            db.accountDao().insert(
                AccountEntity(
                    name = "Primary account",
                    type = "checking",
                    balanceCents = amountCents,
                    isDefault = true,
                    isReconciled = reconciled,
                    lastUpdated = today,
                )
            )
        }
    }

    /**
     * Guarantees a default account exists, seeded from the legacy balance settings.
     * Covers fresh installs (no migration seed) and restores of pre-accounts backups.
     */
    suspend fun ensureDefaultAccountSeeded() {
        if (db.accountDao().getDefault() != null) return
        // Read-only seeding: never resurrect settings rows on a wiped database.
        val bank = db.settingsDao().getValue(KEY_BANK_BALANCE)?.toLongOrNull()
            ?: db.settingsDao().getValue(KEY_CURRENT_BALANCE)?.toLongOrNull()
            ?: 0L
        db.accountDao().insert(
            AccountEntity(
                name = "Primary account",
                type = "checking",
                balanceCents = bank,
                isReconciled = isBalanceReconciled(),
                isDefault = true,
                lastUpdated = LocalDate.now().toString(),
            )
        )
    }

    /** Promotes [id] to be the default account and demotes every other account. */
    suspend fun setDefaultAccount(id: Long) {
        db.withTransaction {
            val target = db.accountDao().getById(id)
                ?: throw IllegalArgumentException("Account not found.")
            for (account in db.accountDao().getAll().first()) {
                val shouldBeDefault = account.id == id
                if (account.isDefault != shouldBeDefault) {
                    db.accountDao().update(account.copy(isDefault = shouldBeDefault))
                }
            }
            // The default account drives the primary pipeline; adopt its current row values.
            if (!target.balanceCents.equals(getBankBalanceCents()) || !target.isReconciled) {
                writeBankBalanceCents(target.balanceCents)
            }
        }
    }

    /** Deletes a non-default account. Deleting the default is blocked to protect the pipeline. */
    suspend fun deleteAccountSafe(account: AccountEntity) {
        if (account.isDefault) {
            throw IllegalArgumentException("Set another account as default before deleting this one.")
        }
        val linkedTransactions = db.transactionDao().getAllTransactionsList()
            .any { it.account_id == account.id }
        if (linkedTransactions) {
            throw IllegalArgumentException(
                "This account still has linked transactions. Reassign or delete those transactions before deleting the account."
            )
        }
        db.accountDao().delete(account)
    }

    private suspend fun applyTransactionToBankBalanceIfReconciled(deltaCents: Long) {
        if (isBalanceReconciled()) {
            writeBankBalanceCents(getBankBalanceCents() + deltaCents)
        }
    }

    /**
     * Routes a transaction's balance impact to the right ledger surface.
     *
     * Charges tagged to a credit-type account are not cash: they raise the linked
     * card liability (and lower it on refunds/credits). Everything else keeps the
     * legacy reconciled bank-balance delta. A credit account without a linked debt
     * moves neither — reconciliation surfaces that gap honestly instead of guessing.
     */
    private suspend fun applyTransactionSideEffects(amountCents: Long, accountId: Long?) {
        val cardDebt = linkedCardDebt(accountId)
        if (cardDebt != null) {
            val newBalance = cardDebt.balanceCents - amountCents
            if (newBalance < 0L) {
                throw IllegalArgumentException(
                    "This transaction would reduce the linked credit balance below \$0. " +
                        "Refunds or credits larger than the outstanding balance are not supported " +
                        "because the debt model cannot represent a negative liability."
                )
            }
            db.debtDao().update(cardDebt.copy(balanceCents = newBalance))
        } else if (!isCreditAccount(accountId)) {
            applyTransactionToBankBalanceIfReconciled(amountCents)
        }
    }

    /** Inverse of [applyTransactionSideEffects] for edits and deletes. */
    private suspend fun revertTransactionSideEffects(amountCents: Long, accountId: Long?) {
        applyTransactionSideEffects(-amountCents, accountId)
    }

    private suspend fun isCreditAccount(accountId: Long?): Boolean {
        if (accountId == null) return false
        val account = db.accountDao().getById(accountId) ?: return false
        // Shared with the dashboard's cash ledger so both halves route identically.
        return account.isCreditAccount()
    }

    private suspend fun linkedCardDebt(accountId: Long?): DebtEntity? {
        if (accountId == null || !isCreditAccount(accountId)) return null
        return db.debtDao().getActive().first()
            .firstOrNull { it.linkedAccountId == accountId }
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

    suspend fun getAllGoalsList(): List<GoalEntity> = db.goalDao().getAllGoals().first()
    suspend fun getAllAssetsList(): List<AssetEntity> = db.assetDao().getAllAssets().first()
    suspend fun getAllTransactionRulesList(): List<TransactionRuleEntity> = db.transactionRuleDao().getAll().first()
    suspend fun getAllCategoryBudgetsList(): List<CategoryBudgetEntity> = db.categoryBudgetDao().getAll().first()
    suspend fun getAllAccountsList(): List<AccountEntity> = db.accountDao().getAll().first()
    suspend fun getAllSettingsList(): List<SettingsEntity> = db.settingsDao().getAll().first()

    /**
     * More than one account, a renamed/non-checking primary, a reconciled or non-zero primary,
     * transactions linked to the lone primary, or any balance/reconciliation drift from a fresh install.
     */
    suspend fun hasCustomAccounts(): Boolean {
        val accounts = getAllAccountsList()
        if (accounts.size > 1) return true
        if (accounts.isEmpty()) return false
        val only = accounts.single()
        if (!isPristineDisposablePrimaryAccount(only)) return true
        return db.transactionDao().getAllTransactionsList().any { it.account_id == only.id }
    }

    /**
     * The only account state that vault restore may replace without preservation risk: untouched
     * migration/default seed (Primary checking, zero balance, not reconciled).
     */
    internal fun isPristineDisposablePrimaryAccount(account: AccountEntity): Boolean {
        return account.isDefault &&
            account.name == "Primary account" &&
            account.type.equals("checking", ignoreCase = true) &&
            account.balanceCents == 0L &&
            !account.isReconciled
    }

    /** Settings beyond vault cross-platform restore and app-lock material. */
    suspend fun hasNonVaultLocalSettings(): Boolean {
        return getAllSettingsList().any { row ->
            !isAppLockSettingKey(row.key) &&
                row.key != KEY_CURRENT_BALANCE && row.key != CanonicalIdMapping.SETTINGS_KEY
        }
    }

    suspend fun hasCrossPlatformLedgerContent(): Boolean {
        val transactions = db.transactionDao().getAllTransactionsList()
        if (transactions.isNotEmpty()) return true
        if (db.incomeDao().getAllIncomesList().isNotEmpty()) return true
        if (db.paymentDao().getAllPaymentsList().isNotEmpty()) return true
        if (db.debtDao().getAll().first().isNotEmpty()) return true
        return false
    }

    /** Cross-platform vault snapshot (version 2.1/2.2) for optional cloud sync. */
    suspend fun exportCrossPlatformVaultJson(updatedAtIso: String): String = db.withTransaction {
        val debts = db.debtDao().getAll().first()
        CanonicalVaultMapper.validateLiabilities(debts)
        val balance = db.transactionDao().getTotalBalanceCents().first() ?: 0L
        val snapshot = CanonicalVaultMapper.buildSnapshot(
            ledgerBalanceCents = balance,
            incomes = db.incomeDao().getAllIncomesList(),
            payments = db.paymentDao().getAllPaymentsList(),
            transactions = db.transactionDao().getAllTransactionsList(),
            occurrences = db.billOccurrenceDao().getAllOccurrencesList(),
            debts = debts,
            updatedAtIso = updatedAtIso,
            canonicalIds = CanonicalIdMapping.fromJson(db.settingsDao().getValue(CanonicalIdMapping.SETTINGS_KEY)),
        )
        Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), snapshot)
    }

    /** Direct vault JSON restore (tests / explicit overwrite after UI confirmation). */
    suspend fun restoreCrossPlatformVaultJson(jsonText: String) = exclusiveLedgerWrite {
        restoreCrossPlatformVaultJsonLocked(jsonText, pullGuard = null)
        Unit
    }

    /** Cloud pull: preservation/core guard runs inside the same Room transaction as the wipe. */
    suspend fun restoreCrossPlatformVaultJsonForVaultPull(
        jsonText: String,
        pullGuard: VaultPullRestoreGuardState,
    ): VaultPullRestoreBaselines = exclusiveLedgerWrite {
        restoreCrossPlatformVaultJsonLocked(jsonText, pullGuard = pullGuard)
    }

    private suspend fun restoreCrossPlatformVaultJsonLocked(
        jsonText: String,
        pullGuard: VaultPullRestoreGuardState?,
    ): VaultPullRestoreBaselines {
        val remotePayloadCoreFingerprint = VaultSnapshotFingerprint.ofSnapshotJson(jsonText)
        val root = JSONObject(jsonText)
        val version = root.getString("version")
        require(version == "2.0" || version == "2.1" || version == "2.2") { "Unsupported snapshot version: $version" }
        validateVaultFields(root)
        val declaredBalance = root.vaultCents("current_balance_cents")
        val canonicalIds = buildCanonicalIdMapping(root)
        val payments = root.getJSONArray("payments").toVaultPayments(canonicalIds)
        val paymentsById = payments.associateBy { it.id }
        require(paymentsById.size == payments.size) { "Snapshot payments has duplicate identifiers" }
        val paymentAmounts = paymentsById.mapValues { (_, payment) -> payment.amount_cents }
        val transactions = root.getJSONArray("transactions").toVaultTransactions(canonicalIds)
        val sum = ContractMoney.toLong(transactions.fold(java.math.BigInteger.ZERO) { total, tx ->
            total + java.math.BigInteger.valueOf(tx.amount_cents)
        })
        require(sum == declaredBalance) {
            "Snapshot inconsistent: transactions sum to $sum but declared balance is $declaredBalance"
        }
        transactions.forEach { DomainRules.validateTransactionSign(it.amount_cents, it.type) }
        val incomes = root.getJSONArray("incomes").toVaultIncomes(canonicalIds)
        val occurrences = root.getJSONArray("bill_occurrences").toVaultOccurrences(
            paymentAmounts, transactions.associateBy { it.id }, canonicalIds,
        )
        val liabilities = if (root.has("liabilities")) root.getJSONArray("liabilities") else JSONArray()
        val linkedPaymentIds = HashSet<Int>()
        for (index in 0 until liabilities.length()) {
            val item = liabilities.getJSONObject(index)
            require(item.get("id") is String) { "Snapshot liabilities[$index].id must be a string" }
            val rawActive = item.opt("is_active")
            require(rawActive == null || rawActive is Boolean) {
                "Snapshot liabilities[$index].is_active must be a boolean"
            }
            if (version == "2.1" || version == "2.0") {
                require(!item.has("is_active") && !item.has("linked_payment_id")) {
                    "Snapshot version $version cannot carry v2.2 liability fields"
                }
            } else {
                if (item.has("linked_payment_id") && !item.isNull("linked_payment_id")) {
                    val rawLinkedPaymentId = item.get("linked_payment_id")
                    require(rawLinkedPaymentId is String || (rawLinkedPaymentId is Number && rawLinkedPaymentId !is Float && rawLinkedPaymentId !is Double)) {
                        "Snapshot liabilities[$index].linked_payment_id must be a canonical payment id"
                    }
                    val linkedPaymentCanonical = rawLinkedPaymentId.toString()
                    val linkedPaymentId = canonicalIds.local("payments", linkedPaymentCanonical)
                    require(linkedPaymentId != null) {
                        "Snapshot liabilities[$index] references unknown linked_payment_id $linkedPaymentCanonical"
                    }
                    val payment = requireNotNull(paymentsById[linkedPaymentId]) {
                        "Snapshot liabilities[$index] references unknown linked_payment_id $linkedPaymentId"
                    }
                    require(CanonicalVaultMapper.isLinkable(payment)) {
                        "Snapshot liabilities[$index] must reference a dated monthly or one-time payment"
                    }
                    require(linkedPaymentIds.add(linkedPaymentId)) {
                        "Snapshot liabilities has duplicate linked_payment_id $linkedPaymentId"
                    }
                }
            }
        }

        lateinit var postRestorePreservationFingerprint: String
        lateinit var restoredLocalCoreFingerprint: String
        db.withTransaction {
            if (pullGuard != null) {
                VaultDestructivePullGuard.verifyOrThrow(
                    this@LedgerRepository,
                    pullGuard,
                    VaultDestructivePullGuard.currentCoreFingerprint(this@LedgerRepository),
                )
            }
            val lockSettings = db.settingsDao().getAll().first().filter { isAppLockSettingKey(it.key) }
            db.transactionRuleDao().deleteAll()
            db.assetDao().deleteAllAssets()
            db.goalDao().deleteAllGoals()
            db.categoryBudgetDao().deleteAll()
            db.accountDao().deleteAll()
            db.billOccurrenceDao().deleteAllBillOccurrences()
            db.transactionDao().deleteAllTransactions()
            db.paymentDao().deleteAllPayments()
            db.incomeDao().deleteAllIncomes()
            db.debtDao().deleteAll()

            incomes.forEach { db.incomeDao().insertIncome(it) }
            payments.forEach { db.paymentDao().insert(it) }
            transactions.forEach { db.transactionDao().insert(it) }
            occurrences.forEach { db.billOccurrenceDao().insert(it) }

            for (index in 0 until liabilities.length()) {
                val item = liabilities.getJSONObject(index)
                db.debtDao().insert(
                    DebtEntity(
                        id = requireNotNull(canonicalIds.local("debts", item.getString("id"))).toLong(),
                        name = item.getString("name"),
                        balanceCents = item.vaultCents("balance_cents"),
                        aprBasisPoints = item.getInt("apr_basis_points"),
                        minimumPaymentCents = item.vaultCents("min_payment_cents"),
                        dueDayOfMonth = item.optInt("due_day_of_month", 1),
                        linkedPaymentId = if (version == "2.2" && item.has("linked_payment_id") && !item.isNull("linked_payment_id")) {
                            canonicalIds.local("payments", item.getString("linked_payment_id"))
                        } else {
                            null
                        },
                        isActive = item.optBoolean("is_active", true),
                        kind = item.optString("kind", "installment"),
                        minPaymentPercentBps = item.optInt("min_payment_percent_bps", 0),
                        minPaymentFloorCents = if (item.has("min_payment_floor_cents")) item.vaultCents("min_payment_floor_cents") else 0L,
                    ),
                )

            }

            db.settingsDao().deleteAllSettings()
            lockSettings.forEach { db.settingsDao().setValue(it) }
            db.settingsDao().setValue(SettingsEntity(KEY_CURRENT_BALANCE, declaredBalance.toString()))
            db.settingsDao().setValue(SettingsEntity(CanonicalIdMapping.SETTINGS_KEY, canonicalIds.toJson()))
            ensureDefaultAccountSeeded()
            postRestorePreservationFingerprint = LocalLedgerPreservation.fingerprint(this@LedgerRepository)
            val localExportJson = exportCrossPlatformVaultJson("restore-baseline-capture")
            restoredLocalCoreFingerprint = VaultSnapshotFingerprint.ofSnapshotJson(localExportJson)
        }
        return VaultPullRestoreBaselines(
            restoredLocalCoreFingerprint = restoredLocalCoreFingerprint,
            postRestorePreservationFingerprint = postRestorePreservationFingerprint,
            remotePayloadCoreFingerprint = remotePayloadCoreFingerprint,
        )
    }

    private fun validateVaultFields(root: JSONObject) {
        val rowFields = mapOf(
            "incomes" to setOf("id", "name", "amount_cents", "frequency", "last_payday", "next_payday", "expected_amount"),
            "payments" to setOf("id", "name", "amount_cents", "recurrence", "due_day", "due_date", "is_auto_withdraw"),
            "transactions" to setOf("id", "amount_cents", "category", "type", "date", "description"),
            "bill_occurrences" to setOf("id", "payment_id", "due_date", "paid", "transaction_id", "original_due_date", "is_user_modified"),
            "liabilities" to setOf(
                "id", "name", "balance_cents", "apr_basis_points", "min_payment_cents", "kind",
                "min_payment_percent_bps", "min_payment_floor_cents", "due_day_of_month", "is_active", "linked_payment_id",
            ),
        )
        val top = rowFields.keys + setOf("version", "updated_at", "current_balance_cents")
        require(root.keys().asSequence().all { it in top }) { "The snapshot has unsupported ledger fields" }
        for ((name, allowed) in rowFields) {
            if (name == "liabilities" && !root.has(name)) continue
            val rows = root.getJSONArray(name)
            for (index in 0 until rows.length()) {
                require(rows.getJSONObject(index).keys().asSequence().all { it in allowed }) {
                    "The snapshot has unsupported fields in $name[$index]"
                }
            }
        }
    }

    private fun JSONObject.vaultCents(key: String): Long {
        val value = get(key)
        require(value is Int || value is Long) { "$key must be a whole number of cents" }
        return ContractMoney.requireValid((value as Number).toLong(), key)
    }

    private fun buildCanonicalIdMapping(root: JSONObject): CanonicalIdMapping {
        val localToCanonical = linkedMapOf<String, Map<Int, CanonicalIdMapping.CanonicalValue>>()
        val tables = listOf(
            "incomes" to "incomes", "payments" to "payments", "transactions" to "transactions",
            "bill_occurrences" to "occurrences", "liabilities" to "debts",
        )
        for ((arrayKey, kind) in tables) {
            val rows = if (root.has(arrayKey)) root.getJSONArray(arrayKey) else JSONArray()
            localToCanonical[kind] = CanonicalIdMapping.assign(
                (0 until rows.length()).map { index ->
                    CanonicalIdMapping.readId(rows.getJSONObject(index).opt("id"))
                },
            )
        }
        return CanonicalIdMapping(localToCanonical)
    }

    private fun JSONObject.vaultDate(key: String): LocalDate =
        LedgerDate.parseIsoOrNull(getString(key))
            ?: throw IllegalArgumentException("Invalid vault date for $key.")

    private fun JSONArray.toVaultIncomes(ids: CanonicalIdMapping): List<IncomeEntity> = buildList {
        for (index in 0 until length()) {
            val obj = getJSONObject(index)
            val next = obj.vaultDate("next_payday")
            val last = obj.vaultDate("last_payday")
            val frequency = obj.getString("frequency")
            val anchor = if (frequency == "Monthly") {
                CanonicalVaultMapper.monthlyIncomeAnchor(last, next)
            } else null
            add(
                IncomeEntity(
                    id = ids.local("incomes", obj.getString("id")) ?: error("Unknown income id"),
                    name = obj.getString("name"),
                    amount_cents = obj.vaultCents("amount_cents"),
                    frequency = frequency,
                    day_of_month = anchor,
                    next_date = next.toString(),
                    last_date = last.toString(),
                    expectedAmountCents = if (obj.isNull("expected_amount")) null else obj.vaultCents("expected_amount"),
                ),
            )
        }
    }

    private fun JSONArray.toVaultPayments(ids: CanonicalIdMapping): List<PaymentEntity> = buildList {
        for (index in 0 until length()) {
            val obj = getJSONObject(index)
            val frequency = obj.getString("recurrence")
            val dueDay = if (obj.isNull("due_day")) null else {
                val day = obj.get("due_day")
                require(day is Int && day in 1..31) { "Invalid vault bill due day." }
                day
            }
            val dueDate = if (obj.isNull("due_date")) {
                require(frequency == "Monthly" && dueDay != null) { "A vault bill needs a due date or monthly day rule." }
                ""
            } else obj.vaultDate("due_date").toString()
            add(
                PaymentEntity(
                    id = ids.local("payments", obj.getString("id")) ?: error("Unknown payment id"),
                    name = obj.getString("name"),
                    amount_cents = obj.vaultCents("amount_cents"),
                    frequency = frequency,
                    day_of_month = dueDay,
                    next_date = dueDate,
                    isAutoWithdraw = obj.optBoolean("is_auto_withdraw", true),
                ),
            )
        }
    }

    private fun JSONArray.toVaultTransactions(ids: CanonicalIdMapping): List<TransactionEntity> = buildList {
        for (index in 0 until length()) {
            val obj = getJSONObject(index)
            add(
                TransactionEntity(
                    id = ids.local("transactions", obj.getString("id")) ?: error("Unknown transaction id"),
                    description = obj.optString("description", ""),
                    amount_cents = obj.vaultCents("amount_cents"),
                    date = obj.vaultDate("date").toString(),
                    type = obj.getString("type").lowercase(),
                    category = obj.optString("category", "uncategorized"),
                ),
            )
        }
    }

    private fun JSONArray.toVaultOccurrences(
        paymentAmounts: Map<Int, Long>,
        transactions: Map<Int, TransactionEntity>,
        ids: CanonicalIdMapping,
    ): List<BillOccurrenceEntity> = buildList {
        val occupiedSlots = HashSet<Pair<Int, String>>()
        val linkedTransactions = HashSet<Int>()
        for (index in 0 until length()) {
            val obj = getJSONObject(index)
            val paymentId = ids.local("payments", obj.getString("payment_id"))
                ?: throw IllegalArgumentException("bill_occurrences[$index] references unknown payment_id")
            val dueDate = obj.vaultDate("due_date").toString()
            val originalDueDate = if (obj.isNull("original_due_date")) dueDate else obj.vaultDate("original_due_date").toString()
            require(occupiedSlots.add(paymentId to originalDueDate)) {
                "bill_occurrences has duplicate original slot for payment_id ${obj.getString("payment_id")}"
            }
            val transactionId = if (obj.isNull("transaction_id")) null else {
                ids.local("transactions", obj.getString("transaction_id"))
                    ?: throw IllegalArgumentException("bill_occurrences[$index] references unknown transaction_id")
            }
            val paid = obj.get("paid")
            require(paid is Boolean) { "bill_occurrences[$index].paid must be a boolean" }
            val modified = obj.opt("is_user_modified")
            require(modified == null || modified is Boolean) {
                "bill_occurrences[$index].is_user_modified must be a boolean"
            }
            val amount = paymentAmounts[paymentId]
                ?: throw IllegalArgumentException("bill_occurrences[$index] references an unknown payment")
            if (transactionId != null) {
                require(linkedTransactions.add(transactionId)) {
                    "bill_occurrences[$index] reuses a settling transaction"
                }
                val posted = transactions.getValue(transactionId)
                require(paid && posted.type == "expense" && posted.amount_cents == -amount) {
                    "bill_occurrences[$index] has an invalid settling transaction"
                }
            } else {
                require(!paid || modified != true) {
                    "bill_occurrences[$index] is moved and paid without a settling transaction"
                }
            }
            add(
                BillOccurrenceEntity(
                    id = ids.local("occurrences", obj.getString("id")) ?: error("Unknown occurrence id"),
                    payment_id = paymentId,
                    due_date = dueDate,
                    amount_cents = amount,
                    is_paid = if (paid) 1 else 0,
                    transaction_id = transactionId,
                    original_due_date = originalDueDate,
                    is_user_modified = if (modified == true || (modified == null && originalDueDate != obj.getString("due_date"))) 1 else 0,
                ),
            )
        }
    }

}

/**
 * Shared rule for a newly saved active debt link. An unchanged link on an already-active
 * debt is left alone so legacy rows can still be edited without unrelated failures.
 *
 * @return an actionable message when the save must be rejected, or null when it may proceed.
 */
internal fun activeDebtLinkValidationError(
    candidate: DebtEntity,
    existing: DebtEntity?,
    paymentsById: Map<Int, PaymentEntity>,
    activeDebts: List<DebtEntity>,
): String? {
    if (!candidate.isActive) return null
    val linkedPaymentId = candidate.linkedPaymentId ?: return null
    val unchangedExistingLink = existing != null &&
        existing.isActive &&
        existing.linkedPaymentId == linkedPaymentId
    if (unchangedExistingLink) return null

    val payment = paymentsById[linkedPaymentId]
        ?: return "The linked bill no longer exists. Choose another bill or remove the link."
    if (!CanonicalVaultMapper.isLinkable(payment)) {
        return "An active debt can link only to an active monthly bill with a due day " +
            "or a dated one-time bill."
    }
    if (activeDebts.any { it.id != candidate.id && it.linkedPaymentId == linkedPaymentId }) {
        return "Another active debt is already linked to this bill. " +
            "Unlink or archive one of them first."
    }
    return null
}
