package com.montecarlo.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.montecarlo.ledger.dashboard.DashboardDeriver
import com.montecarlo.ledger.dashboard.LedgerReportingData
import com.montecarlo.ledger.dashboard.PlanningReportingData
import com.montecarlo.ledger.dashboard.ReportingPackage
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.LedgerBackupSnapshot
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.AppLockThrottle
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.SettingsEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.data.TransactionRuleEntity
import com.montecarlo.ledger.domain.DomainRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.glance.appwidget.updateAll
import com.montecarlo.ledger.widget.MonteCarloLedgerGlanceWidget
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class MainViewModel @JvmOverloads constructor(
    application: Application,
    database: AppDatabase = AppDatabase.getInstance(application),
) : AndroidViewModel(application) {

    private val repo: LedgerRepository = LedgerRepository(database)

    val allIncome: StateFlow<List<IncomeEntity>> = repo.allIncome
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPayments: StateFlow<List<PaymentEntity>> = repo.allPayments
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allBillOccurrences: StateFlow<List<BillOccurrenceEntity>> = repo.allBillOccurrences
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allTransactionRules: StateFlow<List<TransactionRuleEntity>> = repo.allTransactionRules
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allTransactions: StateFlow<List<TransactionEntity>> = repo.allTransactions
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val onboardingProgress: StateFlow<OnboardingProgress> = repo.onboardingProgress
        .stateIn(viewModelScope, SharingStarted.Eagerly, OnboardingProgress())

    val monitoringModeIntroSeen: StateFlow<Boolean> = repo.monitoringModeIntroSeen
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val allSettings: StateFlow<List<SettingsEntity>> = repo.allSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val reminderPreferences: StateFlow<com.montecarlo.ledger.data.ReminderPreferences> = repo.reminderPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.montecarlo.ledger.data.ReminderPreferences())

    val appLockPreferences: StateFlow<com.montecarlo.ledger.data.AppLockPreferences> = repo.appLockPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.montecarlo.ledger.data.AppLockPreferences())

    val allCategoryBudgets: StateFlow<List<com.montecarlo.ledger.data.CategoryBudgetEntity>> = repo.allCategoryBudgets
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allAssets: StateFlow<List<com.montecarlo.ledger.data.AssetEntity>> = repo.allAssets
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allDebts: StateFlow<List<DebtEntity>> = repo.allDebts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow(AppUiState(isLoading = true))
    val uiState: StateFlow<AppUiState> = _uiState

    private val _reconciliationMismatch = MutableStateFlow(false)
    val reconciliationMismatch: StateFlow<Boolean> = _reconciliationMismatch

    private val _dashboardConfig = MutableStateFlow(DashboardConfig())
    val dashboardConfig = _dashboardConfig.asStateFlow()

    private val _reconciliationDetails = MutableStateFlow<Pair<Long, Long>?>(null)
    val reconciliationDetails: StateFlow<Pair<Long, Long>?> = _reconciliationDetails

    private val _appLockUnlocked = MutableStateFlow(false)
    val appLockUnlocked: StateFlow<Boolean> = _appLockUnlocked.asStateFlow()

    private val _appLockThrottleState = MutableStateFlow(AppLockThrottle.ThrottleState())
    val appLockThrottleState: StateFlow<AppLockThrottle.ThrottleState> = _appLockThrottleState.asStateFlow()
    private val externalActivityDepth = AtomicInteger(0)
    private val dashboardDeriver = DashboardDeriver()

    init {
        viewModelScope.launch {
            repo.migrateBalanceSettings()
            repo.syncBillOccurrences()
            repo.syncOnboardingMilestones()
        }
        observeDashboardData()
    }

    private fun launchPersistence(
        onResult: (Result<Unit>) -> Unit = {},
        operation: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                operation()
                onResult(Result.success(Unit))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unable to save changes."
                )
                onResult(Result.failure(e))
            }
        }
    }

    fun markOccurrencePaid(occurrenceId: Int) {
        viewModelScope.launch {
            try {
                repo.payBillOccurrence(occurrenceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unable to record bill payment."
                )
            }
        }
    }

    fun skipBillOccurrence(occurrenceId: Int) {
        viewModelScope.launch {
            try {
                repo.skipBillOccurrence(occurrenceId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unable to skip bill occurrence."
                )
            }
        }
    }

    fun rescheduleBillOccurrence(occurrenceId: Int, dueDate: String) {
        viewModelScope.launch {
            try {
                repo.rescheduleBillOccurrence(occurrenceId, dueDate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unable to move bill occurrence."
                )
            }
        }
    }

    fun applyOverdraftRecommendation(recommendation: com.montecarlo.ledger.processing.OverdraftRecommendation) {
        viewModelScope.launch {
            when (recommendation) {
                is com.montecarlo.ledger.processing.OverdraftRecommendation.RescheduleBill -> {
                    val id = recommendation.occurrenceId
                    if (id != null) {
                        rescheduleBillOccurrence(id, recommendation.suggestedDueDate.toString())
                    }
                }
                is com.montecarlo.ledger.processing.OverdraftRecommendation.CapDailySpend -> {
                    // Informational cap indicator
                }
                is com.montecarlo.ledger.processing.OverdraftRecommendation.TransferFromAsset -> {
                    // Informational transfer indicator
                }
            }
        }
    }

    fun checkBalanceConsistency() {
        viewModelScope.launch {
            val result = repo.checkBalanceConsistency()
            _reconciliationMismatch.value = result.first
            _reconciliationDetails.value = if (result.first) Pair(result.second, result.third) else null
        }
    }

    fun setBankBalance(amountCents: Long, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) {
            repo.setBankBalance(amountCents)
            _reconciliationMismatch.value = false
            _reconciliationDetails.value = null
        }
    }

    fun confirmReconciliationMismatch() {
        val details = _reconciliationDetails.value ?: return
        viewModelScope.launch {
            repo.setBankBalance(details.first)
            _reconciliationMismatch.value = false
            _reconciliationDetails.value = null
        }
    }

    fun dismissReconciliationMismatch() {
        _reconciliationMismatch.value = false
        _reconciliationDetails.value = null
    }

    fun acknowledgeMonitoringModeIntro() {
        viewModelScope.launch {
            repo.markMonitoringModeIntroSeen()
        }
    }

    fun updateTransaction(entity: TransactionEntity, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) { repo.updateTransaction(entity) }
    }

    fun approveTransactionReview(transactionId: Int) {
        viewModelScope.launch {
            repo.approveTransactionReview(transactionId)
        }
    }

    fun createRuleFromTransactionReview(transactionId: Int, category: String) {
        val trimmedCategory = category.trim()
        val transaction = allTransactions.value.firstOrNull { it.id == transactionId } ?: return
        if (trimmedCategory.isBlank()) {
            approveTransactionReview(transactionId)
            return
        }
        viewModelScope.launch {
            repo.saveTransactionRule(transaction.description, trimmedCategory)
            repo.updateTransaction(transaction.copy(category = trimmedCategory))
        }
    }

    fun deleteTransaction(entity: TransactionEntity, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) { repo.deleteTransaction(entity) }
    }

    fun saveTransactionRule(description: String, category: String, applyRetroactively: Boolean = true) {
        viewModelScope.launch {
            repo.addTransactionRule(description, category, applyRetroactively)
        }
    }

    fun installCategoryRulePresets() {
        viewModelScope.launch {
            repo.installCategoryRulePresets()
        }
    }

    fun deleteTransactionRule(rule: TransactionRuleEntity) {
        viewModelScope.launch {
            repo.deleteTransactionRule(rule)
        }
    }

    fun updateReminderPreferences(preferences: com.montecarlo.ledger.data.ReminderPreferences) {
        viewModelScope.launch {
            repo.updateReminderPreferences(preferences)
        }
    }

    fun enableAppLock(pin: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repo.enableAppLock(pin)
            }.onSuccess {
                _appLockUnlocked.value = true
                _appLockThrottleState.value = AppLockThrottle.ThrottleState()
                onResult(true, null)
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                onResult(false, throwable.message ?: "Unable to enable app lock.")
            }
        }
    }

    fun disableAppLock(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repo.disableAppLock()
            }.onSuccess {
                _appLockUnlocked.value = false
                _appLockThrottleState.value = AppLockThrottle.ThrottleState()
                onResult(true, null)
            }.onFailure { throwable ->
                if (throwable is CancellationException) throw throwable
                onResult(false, throwable.message ?: "Unable to disable app lock.")
            }
        }
    }

    fun unlockApp(pin: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repo.verifyAppLockPin(pin) }.getOrElse { e ->
                if (e is CancellationException) throw e
                LedgerRepository.AppLockVerifyResult.Invalid(e.message ?: "Verification failed.")
            }
            when (result) {
                is LedgerRepository.AppLockVerifyResult.Success -> {
                    _appLockUnlocked.value = true
                    _appLockThrottleState.value = AppLockThrottle.onSuccess()
                    onResult(true, null)
                }
                is LedgerRepository.AppLockVerifyResult.LockedOut -> {
                    _appLockThrottleState.value = AppLockThrottle.ThrottleState(
                        failedAttempts = _appLockThrottleState.value.failedAttempts,
                        lockoutUntilEpochMs = System.currentTimeMillis() + result.remainingSeconds * 1000,
                    )
                    onResult(false, "Too many attempts. Try again in ${formatLockoutTime(result.remainingSeconds)}.")
                }
                is LedgerRepository.AppLockVerifyResult.Invalid -> {
                    _appLockUnlocked.value = false
                    onResult(false, result.message)
                }
            }
        }
    }

    private fun formatLockoutTime(totalSeconds: Long): String {
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return when {
            minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }

    fun lockApp() {
        _appLockUnlocked.value = false
    }

    fun beginExternalActivity() {
        externalActivityDepth.incrementAndGet()
    }

    fun endExternalActivity() {
        externalActivityDepth.updateAndGet { depth -> maxOf(0, depth - 1) }
    }

    fun shouldLockOnBackground(): Boolean = externalActivityDepth.get() == 0

    fun addAsset(name: String, type: String, balanceCents: Long) {
        viewModelScope.launch {
            repo.insertAsset(
                com.montecarlo.ledger.data.AssetEntity(
                    name = name,
                    type = type,
                    balanceCents = balanceCents,
                    lastUpdated = LocalDate.now().toString()
                )
            )
        }
    }

    fun updateAsset(asset: com.montecarlo.ledger.data.AssetEntity) {
        viewModelScope.launch {
            repo.updateAsset(asset.copy(lastUpdated = LocalDate.now().toString()))
        }
    }

    fun deleteAsset(asset: com.montecarlo.ledger.data.AssetEntity) {
        viewModelScope.launch { repo.deleteAsset(asset) }
    }

    fun updateDashboardConfig(config: DashboardConfig) {
        _dashboardConfig.value = config
    }

    // Goal Management
    fun addGoal(goal: com.montecarlo.ledger.data.GoalEntity, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) { repo.insertGoal(goal) }
    }
    fun updateGoal(goal: com.montecarlo.ledger.data.GoalEntity) {
        viewModelScope.launch { repo.updateGoal(goal) }
    }
    fun deleteGoal(goal: com.montecarlo.ledger.data.GoalEntity) {
        viewModelScope.launch { repo.deleteGoal(goal) }
    }

    // Category Budgets (soft watchlists)
    fun upsertCategoryBudget(budget: com.montecarlo.ledger.data.CategoryBudgetEntity) {
        viewModelScope.launch { repo.upsertCategoryBudget(budget) }
    }
    fun deleteCategoryBudget(budget: com.montecarlo.ledger.data.CategoryBudgetEntity) {
        viewModelScope.launch { repo.deleteCategoryBudget(budget) }
    }

    fun addDebt(debt: DebtEntity) {
        viewModelScope.launch { repo.insertDebt(debt) }
    }

    fun updateDebt(debt: DebtEntity) {
        viewModelScope.launch { repo.updateDebt(debt) }
    }

    fun deleteDebt(debt: DebtEntity) {
        viewModelScope.launch { repo.deleteDebt(debt) }
    }

    private fun observeDashboardData() {
        viewModelScope.launch {
            val ledgerData = combine(
                repo.allIncome,
                repo.allPayments,
                repo.allTransactions,
                repo.balanceState,
            ) { incomes, payments, txns, balanceState ->
                LedgerReportingData(incomes, payments, txns, balanceState)
            }
            val planningCore = combine(
                repo.allBillOccurrences,
                repo.allAssets,
                repo.allGoals,
                repo.allCategoryBudgets,
                repo.allTransactionRules,
                repo.allDebts,
                _dashboardConfig,
            ) { args: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                PlanningReportingData(
                    billOccurrences = args[0] as List<BillOccurrenceEntity>,
                    assets = args[1] as List<com.montecarlo.ledger.data.AssetEntity>,
                    goals = args[2] as List<com.montecarlo.ledger.data.GoalEntity>,
                    categoryBudgets = args[3] as List<com.montecarlo.ledger.data.CategoryBudgetEntity>,
                    rules = args[4] as List<TransactionRuleEntity>,
                    debts = args[5] as List<DebtEntity>,
                    dashboardConfig = args[6] as DashboardConfig,
                )
            }
            val reportingData = combine(ledgerData, planningCore) { ledger, planning ->
                ReportingPackage(
                    incomes = ledger.incomes,
                    payments = ledger.payments,
                    txns = ledger.txns,
                    balanceState = ledger.balanceState,
                    billOccurrences = planning.billOccurrences,
                    assets = planning.assets,
                    goals = planning.goals,
                    categoryBudgets = planning.categoryBudgets,
                    debts = planning.debts,
                    rules = planning.rules,
                    dashboardConfig = planning.dashboardConfig,
                )
            }

            combine(dashboardDateFlow(), reportingData) { today, pack ->
                // Derivation lives in DashboardDeriver so the pipeline stays testable
                // without Android plumbing; this ViewModel only holds and applies state.
                val derivation = dashboardDeriver.derive(pack, today)
                _reconciliationDetails.value = derivation.reconciliationDetails
                _reconciliationMismatch.value = derivation.reconciliationMismatch
                _uiState.value = derivation.uiState
                refreshGlanceWidget()
            }.catch { e ->
                if (e is CancellationException) throw e
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unable to update dashboard.",
                )
            }.collect { }
        }
    }

    private fun dashboardDateFlow() = flow {
        while (true) {
            emit(LocalDate.now())
            delay(60 * 60 * 1000L)
        }
    }

    fun addIncome(entity: IncomeEntity, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) { repo.insertIncome(entity) }
    }

    fun updateIncome(entity: IncomeEntity, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) { repo.updateIncome(entity) }
    }

    fun deleteIncome(entity: IncomeEntity, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) { repo.deleteIncome(entity) }
    }

    fun addPayment(entity: PaymentEntity, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) {
            repo.insertPayment(entity)
            repo.syncBillOccurrences()
        }
    }

    fun updatePayment(entity: PaymentEntity, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) {
            repo.updatePayment(entity)
            repo.syncBillOccurrences()
        }
    }

    fun deletePayment(entity: PaymentEntity, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) { repo.deletePayment(entity) }
    }

    fun processPayday(income: com.montecarlo.ledger.data.IncomeEntity, actualAmountCents: Long) {
        launchPersistence { repo.processPayday(income, actualAmountCents) }
    }

    fun addTransaction(
        description: String,
        amountCents: Long,
        type: String,
        linkedOccurrenceId: Int? = null,
        category: String = "uncategorized",
        date: String = LocalDate.now().toString(),
        onSaved: () -> Unit = {},
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            try {
                DomainRules.validateTransactionSign(amountCents, type)
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(error = e.message)
                return@launch
            }
            try {
                if (linkedOccurrenceId != null) {
                    repo.insertTransaction(
                        TransactionEntity(
                            description = description,
                            amount_cents = amountCents,
                            date = date,
                            type = type,
                            category = category
                        ),
                        linkedOccurrenceId
                    )
                } else {
                    repo.insertTransaction(
                        TransactionEntity(
                            description = description,
                            amount_cents = amountCents,
                            date = date,
                            type = type,
                            category = category
                        )
                    )
                }
                onSaved()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unable to save transaction."
                )
            }
        }
    }

    fun importTransactions(transactions: List<TransactionEntity>, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) {
            repo.importTransactions(transactions)
        }
    }

    fun importPayments(payments: List<PaymentEntity>, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) {
            repo.importPayments(payments)
        }
    }

    fun restoreBackup(snapshot: LedgerBackupSnapshot, onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) {
            repo.restoreBackup(snapshot)
            _reconciliationMismatch.value = false
            _reconciliationDetails.value = null
        }
    }

    fun eraseAllData(onResult: (Result<Unit>) -> Unit = {}) {
        launchPersistence(onResult) {
            repo.eraseAllData()
            _reconciliationMismatch.value = false
            _reconciliationDetails.value = null
            _appLockUnlocked.value = false
            _appLockThrottleState.value = AppLockThrottle.ThrottleState()
        }
    }

    private fun refreshGlanceWidget() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { MonteCarloLedgerGlanceWidget().updateAll(getApplication()) }
        }
    }
}
