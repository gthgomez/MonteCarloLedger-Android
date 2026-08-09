package com.montecarlo.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.montecarlo.ledger.data.AppDatabase
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.CategoryRulePresets
import com.montecarlo.ledger.data.CategorySpend
import com.montecarlo.ledger.data.LedgerBackupSnapshot
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.AppLockThrottle
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.OnboardingProgress
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.SettingsEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.data.TransactionRuleEntity
import com.montecarlo.ledger.data.FlowSummary
import com.montecarlo.ledger.domain.DomainRules
import com.montecarlo.ledger.processing.ForecastEngine
import com.montecarlo.ledger.processing.BalanceSeedResolver
import com.montecarlo.ledger.processing.RecurringDetector
import com.montecarlo.ledger.ui.formatDateDisplay
import com.montecarlo.ledger.util.centsToDisplay
import com.montecarlo.ledger.processing.MonteCarloEngine
import com.montecarlo.ledger.processing.MonteCarloParams
import com.montecarlo.ledger.processing.TimelineService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo: LedgerRepository = LedgerRepository(AppDatabase.getInstance(application))

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

    private val _uiState = MutableStateFlow(AppUiState())
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

    init {
        viewModelScope.launch {
            repo.migrateBalanceSettings()
            repo.syncBillOccurrences()
            repo.syncOnboardingMilestones()
        }
        observeDashboardData()
    }

    fun markOccurrencePaid(occurrenceId: Int) {
        viewModelScope.launch {
            try {
                repo.payBillOccurrence(occurrenceId)
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

    fun setBankBalance(amountCents: Long) {
        viewModelScope.launch {
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

    fun updateTransaction(entity: TransactionEntity) {
        viewModelScope.launch { repo.updateTransaction(entity) }
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

    fun deleteTransaction(entity: TransactionEntity) {
        viewModelScope.launch { repo.deleteTransaction(entity) }
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
                onResult(false, throwable.message ?: "Unable to disable app lock.")
            }
        }
    }

    fun unlockApp(pin: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = runCatching { repo.verifyAppLockPin(pin) }.getOrElse { e ->
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
    fun addGoal(goal: com.montecarlo.ledger.data.GoalEntity) {
        viewModelScope.launch { repo.insertGoal(goal) }
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
                _dashboardConfig,
            ) { args: Array<Any?> ->
                @Suppress("UNCHECKED_CAST")
                PlanningReportingData(
                    billOccurrences = args[0] as List<BillOccurrenceEntity>,
                    assets = args[1] as List<com.montecarlo.ledger.data.AssetEntity>,
                    goals = args[2] as List<com.montecarlo.ledger.data.GoalEntity>,
                    categoryBudgets = args[3] as List<com.montecarlo.ledger.data.CategoryBudgetEntity>,
                    rules = args[4] as List<TransactionRuleEntity>,
                    dashboardConfig = args[5] as DashboardConfig,
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
                    rules = planning.rules,
                    dashboardConfig = planning.dashboardConfig,
                )
            }

            combine(dashboardDateFlow(), reportingData) { today, pack ->
                val since30d = today.minusDays(30)
                val recentTransactions = pack.txns.recentTransactionsSince(since30d)
                val categorySpend = recentTransactions
                    .filter { it.type == "expense" }
                    .groupBy { it.category }
                    .map { (category, items) ->
                        CategorySpend(
                            category = category,
                            totalCents = items.sumOf { it.amount_cents }
                        )
                    }
                    .sortedBy { it.totalCents }
                val flowSummary = recentTransactions.toFlowSummary()
                val adjustments = pack.txns.filter { it.type == "adjustment" }
                val recurringCandidates = RecurringDetector.detect(pack.txns)
                val categoryBudgetRows = com.montecarlo.ledger.processing.CategoryBudgetTracker.evaluate(
                    budgets = pack.categoryBudgets,
                    transactions = pack.txns,
                    today = today,
                )

                val ledgerBalanceCents = pack.txns.sumOf { it.amount_cents }
                val bankBalanceCents = pack.balanceState.bankBalanceCents
                val reconciled = pack.balanceState.isReconciled
                val mismatch = reconciled && bankBalanceCents != ledgerBalanceCents
                // Until the user confirms a reconciled balance, keep forecasts anchored to the
                // live transaction ledger so first-run balances don't collapse to zero.
                val forecastSeedCents = BalanceSeedResolver.resolve(
                    ledgerBalanceCents,
                    bankBalanceCents,
                    reconciled
                )
                val events = TimelineService.generateTimeline(pack.incomes, pack.payments, today, 90, pack.billOccurrences)

                val nextPaycheck = events.filter { it.type == "income" }.minByOrNull { it.date }
                val daysUntilPayday = if (nextPaycheck != null) {
                    java.time.temporal.ChronoUnit.DAYS.between(today, nextPaycheck.date).toInt().coerceAtLeast(1)
                } else {
                    30
                }

                val forecastSummary = ForecastEngine.calculateForecastSummary(forecastSeedCents, events)
                val safeToSpend = forecastSummary.safeToSpendCents
                val incomeContribution = ForecastEngine.calculateIncomeContribution(forecastSeedCents, events)
                val cashFlowWindows = ForecastEngine.buildCashFlowWindows(forecastSeedCents, events, today, 90)
                val currentCashFlowWindow = cashFlowWindows.firstOrNull()
                val dailyBudgetCents = currentCashFlowWindow?.dailySafeSpendCents
                    ?: ForecastEngine.calculateDailySafeSpend(forecastSeedCents, events, daysUntilPayday)
                val mc = withContext(Dispatchers.Default) {
                    MonteCarloEngine(MonteCarloParams(includeDailyPercentiles = true)).runSimulation(forecastSeedCents, events, today)
                }
                val scheduledBillBurdenCents = events.filter { it.type == "bill" }.sumOf { it.amount_cents }

                val nextPaydayLabel = nextPaycheck?.let { "Next: ${it.date.formatDateDisplay()} (${daysUntilPayday}d)" } ?: "No upcoming income"
                val upcomingBills = events.filter { it.type == "bill" }.take(5)
                    .map {
                        val recurrenceText = it.recurrenceLabel?.takeIf { label -> label.isNotBlank() }
                        when {
                            recurrenceText == null -> "${it.description} • ${it.date.formatDateDisplay()}"
                            recurrenceText.startsWith("One-time due ") -> "${it.description} • $recurrenceText"
                            else -> "${it.description} • $recurrenceText • ${it.date.formatDateDisplay()}"
                        }
                    }
                val forecastRows = forecastSummary.let { ForecastEngine.buildBalanceForecast(forecastSeedCents, events) }

                if (mismatch) _reconciliationDetails.value = Pair(ledgerBalanceCents, bankBalanceCents)
                _reconciliationMismatch.value = mismatch
                val driftCents = if (mismatch) ledgerBalanceCents - bankBalanceCents else 0L
                val totalAssetBalance = pack.assets.sumOf { it.balanceCents }
                val totalNetWorthCents = ledgerBalanceCents + totalAssetBalance

                val dailyVelocityCents = kotlin.math.abs(flowSummary.outflowCents) / 30
                val runwayDays = if (dailyVelocityCents > 0L) (safeToSpend / dailyVelocityCents).toInt().coerceAtMost(90) else 90
                val reviewItems = buildTransactionReviewItems(
                    transactions = pack.txns,
                    recurringCandidates = recurringCandidates,
                    today = today,
                )
                val moneyBuckets = buildMoneyBuckets(
                    forecastSeedCents = forecastSeedCents,
                    scheduledBillBurdenCents = scheduledBillBurdenCents,
                    goals = pack.goals,
                    safeToSpendCents = safeToSpend,
                    reconciled = reconciled,
                )
                val overdraftRecommendations = com.montecarlo.ledger.processing.OverdraftActionEngine.analyze(
                    mcResult = mc,
                    windows = cashFlowWindows,
                    events = events,
                    billOccurrences = pack.billOccurrences,
                    assets = pack.assets,
                )
                val actionCenter = buildActionCenter(
                    safeToSpendCents = safeToSpend,
                    reviewCount = reviewItems.size + if (mismatch) 1 else 0,
                    nextBillLabel = upcomingBills.firstOrNull() ?: "No upcoming bills",
                    probabilityNegativePct = mc.probability_negative_pct,
                    runwayDays = runwayDays,
                    reconciled = reconciled,
                    hasIncome = pack.incomes.isNotEmpty(),
                    hasBills = pack.payments.isNotEmpty(),
                    overdraftRecommendations = overdraftRecommendations,
                )
                val trustSignals = buildTrustSignals(
                    updatedAt = LocalDateTime.now(),
                    reconciled = reconciled,
                    transactionCount = pack.txns.size,
                    forecastSeedCents = forecastSeedCents,
                    eventCount = events.size,
                    reviewCount = reviewItems.size,
                )

                // Pacing Sparkline Calculation
                val currentMonthTxns = pack.txns.filter { 
                    val d = runCatching { LocalDate.parse(it.date) }.getOrNull()
                    d != null && d.month == today.month && d.year == today.year && it.type == "expense"
                }
                val currentMonthPacing = (1..today.dayOfMonth).map { day ->
                    currentMonthTxns.filter { LocalDate.parse(it.date).dayOfMonth <= day }.sumOf { kotlin.math.abs(it.amount_cents) }
                }
                
                val avgMonthlyOutflow = kotlin.math.abs(flowSummary.outflowCents)
                val avgMonthPacing = (1..today.lengthOfMonth()).map { day ->
                    (avgMonthlyOutflow * (day.toFloat() / today.lengthOfMonth())).toLong()
                }

                val pacingResult = com.montecarlo.ledger.processing.BudgetPacingEngine.calculatePacing(
                    safeToSpendCents = safeToSpend,
                    daysToPayday = daysUntilPayday,
                    transactions = pack.txns,
                    today = today,
                )

                _uiState.value = AppUiState(
                    isLoading = false,
                    bankBalanceCents = bankBalanceCents,
                    ledgerBalanceCents = ledgerBalanceCents,
                    isBalanceReconciled = reconciled,
                    safeToSpendCents = safeToSpend,
                    incomeContributionCents = incomeContribution,
                    upcomingBills = upcomingBills,
                    nextPaydayLabel = nextPaydayLabel,
                    dailyBudgetCents = dailyBudgetCents,
                    upcomingBillBurdenCents = scheduledBillBurdenCents,
                    monteCarlo10thCents = mc.worst_10_balance_cents,
                    monteCarlo50thCents = mc.median_balance_cents,
                    monteCarlo90thCents = mc.best_90_balance_cents,
                    probabilityNegativePct = mc.probability_negative_pct,
                    projectedTroubleDateLabel = mc.most_common_first_negative_date,
                    firstNegativeDateLabel = forecastSummary.firstNegativeDate?.toString(),
                    lowestBalanceDateLabel = forecastSummary.lowestBalanceDate?.toString(),
                    bankLedgerMismatch = mismatch,
                    driftCents = driftCents,
                    transactions = pack.txns,
                    forecastRows = forecastRows,
                    cashFlowWindows = cashFlowWindows,
                    categorySpend = categorySpend,
                    recurringCandidates = recurringCandidates,
                    totalInflowCents = flowSummary.inflowCents,
                    totalOutflowCents = flowSummary.outflowCents,
                    totalNetWorthCents = totalNetWorthCents,
                    runwayDays = runwayDays,
                    assets = pack.assets,
                    goals = pack.goals,
                    payments = pack.payments,
                    adjustments = adjustments,
                    currentMonthPacing = currentMonthPacing,
                    avgMonthPacing = avgMonthPacing,
                    actionCenter = actionCenter,
                    transactionReviewItems = reviewItems,
                    moneyBuckets = moneyBuckets,
                    trustSignals = trustSignals,
                    dashboardConfig = pack.dashboardConfig,
                    categoryBudgets = pack.categoryBudgets,
                    categoryBudgetRows = categoryBudgetRows,
                    transactionRules = pack.rules,
                    pacingResult = pacingResult,
                    monteCarloDailyPercentiles = mc.dailyPercentiles,
                    monteCarloResult = mc,
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

    private fun List<TransactionEntity>.recentTransactionsSince(since: LocalDate): List<TransactionEntity> {
        return filter { transaction ->
            val parsedDate = runCatching { LocalDate.parse(transaction.date) }.getOrNull()
            parsedDate != null && !parsedDate.isBefore(since)
        }
    }

    private fun List<TransactionEntity>.toFlowSummary(): FlowSummary {
        val inflowCents = sumOf { transaction ->
            if (transaction.type == "income") transaction.amount_cents else 0L
        }
        val outflowCents = kotlin.math.abs(sumOf { transaction ->
            if (transaction.type == "expense") transaction.amount_cents else 0L
        })
        return FlowSummary(
            inflowCents = inflowCents,
            outflowCents = outflowCents,
        )
    }

    private fun buildTransactionReviewItems(
        transactions: List<TransactionEntity>,
        recurringCandidates: List<com.montecarlo.ledger.data.RecurringCandidate>,
        today: LocalDate,
    ): List<TransactionReviewItem> {
        val recentCutoff = today.minusDays(14)
        val recurringByPattern = recurringCandidates.associateBy { it.pattern.lowercase(Locale.ROOT) }
        return transactions.asSequence()
            .filter { it.review_status == "pending" || it.type == "expense" }
            .filter { transaction ->
                val parsedDate = runCatching { LocalDate.parse(transaction.date) }.getOrNull()
                transaction.review_status == "pending" ||
                    transaction.category.equals("uncategorized", ignoreCase = true) ||
                    (transaction.reviewed_at == null && parsedDate != null && !parsedDate.isBefore(recentCutoff))
            }
            .sortedWith(
                compareByDescending<TransactionEntity> { it.review_status == "pending" }
                    .thenByDescending { it.category.equals("uncategorized", ignoreCase = true) }
                    .thenByDescending { runCatching { LocalDate.parse(it.date) }.getOrNull() ?: LocalDate.MIN }
                    .thenByDescending { kotlin.math.abs(it.amount_cents) }
            )
            .take(6)
            .map { transaction ->
                val normalizedDescription = transaction.description.trim().lowercase(Locale.ROOT)
                val recurringMatch = recurringByPattern[normalizedDescription]
                val suggestedCategory = when {
                    !transaction.category.equals("uncategorized", ignoreCase = true) -> transaction.category
                    recurringMatch != null && !recurringMatch.category.equals("uncategorized", ignoreCase = true) -> recurringMatch.category
                    else -> inferCategory(transaction.description)
                }
                val reason = when {
                    transaction.source == "csv_import" -> "Imported transaction"
                    transaction.review_status == "pending" -> "New activity"
                    transaction.category.equals("uncategorized", ignoreCase = true) -> "Needs category"
                    recurringMatch != null -> "Recurring pattern"
                    else -> "New activity"
                }
                TransactionReviewItem(
                    transaction = transaction,
                    suggestedCategory = suggestedCategory,
                    reason = reason,
                )
            }
            .toList()
    }

    private fun inferCategory(description: String): String {
        return CategoryRulePresets.inferCategory(description)
    }

    private fun buildMoneyBuckets(
        forecastSeedCents: Long,
        scheduledBillBurdenCents: Long,
        goals: List<com.montecarlo.ledger.data.GoalEntity>,
        safeToSpendCents: Long,
        reconciled: Boolean,
    ): List<MoneyBucketState> {
        val positiveSeed = forecastSeedCents.coerceAtLeast(0L)
        val goalTargetCents = goals.sumOf { it.targetAmountCents }
        val goalCurrentCents = goals.sumOf { it.currentAmountCents }
        val goalRemainingCents = (goalTargetCents - goalCurrentCents).coerceAtLeast(0L)
        val overPlan = safeToSpendCents < 0L
        // When over plan, show the shortfall magnitude rather than hiding it at 0.
        val availableCents = if (overPlan) safeToSpendCents else safeToSpendCents.coerceAtLeast(0L)
        return listOf(
            MoneyBucketState(
                label = "Bills spoken for",
                amountCents = scheduledBillBurdenCents,
                detail = "Scheduled over the forecast window",
                progress = if (positiveSeed > 0) scheduledBillBurdenCents.toFloat() / positiveSeed else 0f,
                accent = MoneyBucketAccent.Bills,
            ),
            MoneyBucketState(
                label = "Goals funded",
                amountCents = goalCurrentCents,
                detail = if (goalRemainingCents > 0) "${centsToDisplay(goalRemainingCents)} still targeted" else "No open goal gap",
                progress = if (goalTargetCents > 0) goalCurrentCents.toFloat() / goalTargetCents else 0f,
                accent = MoneyBucketAccent.Goals,
            ),
            MoneyBucketState(
                label = if (overPlan) "Over plan" else "Available after plan",
                amountCents = availableCents,
                detail = when {
                    !reconciled -> "Confirm bank balance to trust this number"
                    overPlan -> "Reduce bills or add income"
                    else -> "Safe-to-spend reserve"
                },
                progress = if (positiveSeed > 0 && !overPlan) availableCents.toFloat() / positiveSeed else if (overPlan) 1f else 0f,
                accent = MoneyBucketAccent.Available,
            ),
        )
    }

    private fun buildActionCenter(
        safeToSpendCents: Long,
        reviewCount: Int,
        nextBillLabel: String,
        probabilityNegativePct: Double,
        runwayDays: Int,
        reconciled: Boolean,
        hasIncome: Boolean,
        hasBills: Boolean,
        overdraftRecommendations: List<com.montecarlo.ledger.processing.OverdraftRecommendation> = emptyList(),
    ): ActionCenterState {
        val forecastUnlocked = reconciled
        val riskLabel = when {
            !hasIncome || !hasBills -> "Add income and bills"
            !reconciled -> "Confirm bank balance"
            safeToSpendCents < 0 -> "Shortfall projected"
            probabilityNegativePct >= 25.0 -> "High risk (${String.format("%.0f", probabilityNegativePct)}%)"
            runwayDays < 14 -> "Thin runway"
            else -> "Stable forecast"
        }
        val action = when {
            !hasIncome -> DashboardPrimaryAction.AddIncome
            !hasBills -> DashboardPrimaryAction.AddBill
            !reconciled -> DashboardPrimaryAction.ConfirmBalance
            reviewCount > 0 -> DashboardPrimaryAction.ReviewTransactions
            safeToSpendCents < 0 -> DashboardPrimaryAction.OpenForecast
            else -> DashboardPrimaryAction.RecordSpending
        }
        val label = when (action) {
            DashboardPrimaryAction.AddIncome -> "Add income"
            DashboardPrimaryAction.AddBill -> "Add bill"
            DashboardPrimaryAction.ConfirmBalance -> "Confirm balance"
            DashboardPrimaryAction.ReviewTransactions -> "Review transactions"
            DashboardPrimaryAction.OpenForecast -> "Open forecast"
            DashboardPrimaryAction.RecordSpending -> "Record spending"
        }
        val caption = when {
            !reconciled -> "Provisional until balance confirmed"
            safeToSpendCents < 0 -> "Lowest balance over next 90 days"
            else -> "Safe to spend"
        }
        return ActionCenterState(
            safeToSpendCents = safeToSpendCents,
            needsReviewCount = reviewCount,
            nextBillLabel = nextBillLabel,
            forecastRiskLabel = riskLabel,
            primaryActionLabel = label,
            primaryAction = action,
            forecastUnlocked = forecastUnlocked,
            safeToSpendCaption = caption,
            overdraftRecommendations = overdraftRecommendations,
        )
    }

    private fun buildTrustSignals(
        updatedAt: LocalDateTime,
        reconciled: Boolean,
        transactionCount: Int,
        forecastSeedCents: Long,
        eventCount: Int,
        reviewCount: Int,
    ): List<TrustSignal> {
        val formatter = DateTimeFormatter.ofPattern("h:mm a")
        return listOf(
            TrustSignal(
                label = "Data freshness",
                value = "Updated ${updatedAt.format(formatter)}",
                detail = "$transactionCount local entries in the ledger",
                level = if (reviewCount > 0) TrustSignalLevel.Attention else TrustSignalLevel.Good,
            ),
            TrustSignal(
                label = "Storage",
                value = "Local on device",
                detail = "Encrypted export is available from Settings",
                level = TrustSignalLevel.Good,
            ),
            TrustSignal(
                label = "Forecast basis",
                value = if (reconciled) "Bank balance" else "App balance",
                detail = "${centsToDisplay(forecastSeedCents)} seed with $eventCount scheduled events",
                level = if (reconciled) TrustSignalLevel.Good else TrustSignalLevel.Attention,
            ),
        )
    }

    private data class ReportingPackage(
        val incomes: List<com.montecarlo.ledger.data.IncomeEntity>,
        val payments: List<com.montecarlo.ledger.data.PaymentEntity>,
        val txns: List<com.montecarlo.ledger.data.TransactionEntity>,
        val balanceState: LedgerRepository.BalanceState,
        val billOccurrences: List<com.montecarlo.ledger.data.BillOccurrenceEntity>,
        val assets: List<com.montecarlo.ledger.data.AssetEntity>,
        val goals: List<com.montecarlo.ledger.data.GoalEntity>,
        val categoryBudgets: List<com.montecarlo.ledger.data.CategoryBudgetEntity>,
        val rules: List<com.montecarlo.ledger.data.TransactionRuleEntity>,
        val dashboardConfig: DashboardConfig,
    )

    private data class LedgerReportingData(
        val incomes: List<com.montecarlo.ledger.data.IncomeEntity>,
        val payments: List<com.montecarlo.ledger.data.PaymentEntity>,
        val txns: List<com.montecarlo.ledger.data.TransactionEntity>,
        val balanceState: LedgerRepository.BalanceState,
    )

    private data class PlanningReportingData(
        val billOccurrences: List<com.montecarlo.ledger.data.BillOccurrenceEntity>,
        val assets: List<com.montecarlo.ledger.data.AssetEntity>,
        val goals: List<com.montecarlo.ledger.data.GoalEntity>,
        val categoryBudgets: List<com.montecarlo.ledger.data.CategoryBudgetEntity>,
        val rules: List<com.montecarlo.ledger.data.TransactionRuleEntity>,
        val dashboardConfig: DashboardConfig,
    )

    fun addIncome(entity: IncomeEntity) {
        viewModelScope.launch {
            repo.insertIncome(entity)
        }
    }

    fun updateIncome(entity: IncomeEntity) {
        viewModelScope.launch { repo.updateIncome(entity) }
    }

    fun deleteIncome(entity: IncomeEntity) {
        viewModelScope.launch { repo.deleteIncome(entity) }
    }

    fun addPayment(entity: PaymentEntity) {
        viewModelScope.launch {
            repo.insertPayment(entity)
            repo.syncBillOccurrences()
        }
    }

    fun updatePayment(entity: PaymentEntity) {
        viewModelScope.launch {
            repo.updatePayment(entity)
            repo.syncBillOccurrences()
        }
    }

    fun deletePayment(entity: PaymentEntity) {
        viewModelScope.launch { repo.deletePayment(entity) }
    }

    fun processPayday(income: com.montecarlo.ledger.data.IncomeEntity, actualAmountCents: Long) {
        viewModelScope.launch {
            repo.processPayday(income, actualAmountCents)
        }
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
            } catch (e: IllegalArgumentException) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } catch (e: Throwable) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Unable to save transaction."
                )
            }
        }
    }

    fun importTransactions(transactions: List<TransactionEntity>) {
        viewModelScope.launch {
            repo.importTransactions(transactions)
        }
    }

    fun importPayments(payments: List<PaymentEntity>) {
        viewModelScope.launch {
            repo.importPayments(payments)
        }
    }

    fun restoreBackup(snapshot: LedgerBackupSnapshot) {
        viewModelScope.launch {
            repo.restoreBackup(snapshot)
            _reconciliationMismatch.value = false
            _reconciliationDetails.value = null
        }
    }
}
