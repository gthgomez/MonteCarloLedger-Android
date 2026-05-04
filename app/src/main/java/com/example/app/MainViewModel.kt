package com.example.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.app.data.AppDatabase
import com.example.app.data.BillOccurrenceEntity
import com.example.app.data.CategoryRulePresets
import com.example.app.data.CategorySpend
import com.example.app.data.LedgerBackupSnapshot
import com.example.app.data.IncomeEntity
import com.example.app.data.LedgerRepository
import com.example.app.data.OnboardingProgress
import com.example.app.data.PaymentEntity
import com.example.app.data.SettingsEntity
import com.example.app.data.TransactionEntity
import com.example.app.data.TransactionRuleEntity
import com.example.app.data.FlowSummary
import com.example.app.domain.DomainRules
import com.example.app.processing.ForecastEngine
import com.example.app.processing.BalanceSeedResolver
import com.example.app.processing.RecurringDetector
import com.example.app.ui.formatDateDisplay
import com.example.app.processing.MonteCarloEngine
import com.example.app.processing.MonteCarloParams
import com.example.app.processing.TimelineService
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

    val reminderPreferences: StateFlow<com.example.app.data.ReminderPreferences> = repo.reminderPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.app.data.ReminderPreferences())

    val appLockPreferences: StateFlow<com.example.app.data.AppLockPreferences> = repo.appLockPreferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.example.app.data.AppLockPreferences())

    val allAssets: StateFlow<List<com.example.app.data.AssetEntity>> = repo.allAssets
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState

    private val _reconciliationMismatch = MutableStateFlow(false)
    val reconciliationMismatch: StateFlow<Boolean> = _reconciliationMismatch

    private val _dashboardConfig = MutableStateFlow(DashboardConfig())
    val dashboardConfig = _dashboardConfig.asStateFlow()

    private val _reconciliationDetails = MutableStateFlow<Pair<Int, Int>?>(null)
    val reconciliationDetails: StateFlow<Pair<Int, Int>?> = _reconciliationDetails

    private val _appLockUnlocked = MutableStateFlow(false)
    val appLockUnlocked: StateFlow<Boolean> = _appLockUnlocked.asStateFlow()

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

    fun checkBalanceConsistency() {
        viewModelScope.launch {
            val result = repo.checkBalanceConsistency()
            _reconciliationMismatch.value = result.first
            _reconciliationDetails.value = if (result.first) Pair(result.second, result.third) else null
        }
    }

    fun setBankBalance(amountCents: Int) {
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

    fun saveTransactionRule(description: String, category: String) {
        viewModelScope.launch {
            repo.saveTransactionRule(description, category)
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

    fun updateReminderPreferences(preferences: com.example.app.data.ReminderPreferences) {
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
                onResult(true, null)
            }.onFailure { throwable ->
                onResult(false, throwable.message ?: "Unable to disable app lock.")
            }
        }
    }

    fun unlockApp(pin: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val unlocked = runCatching { repo.verifyAppLockPin(pin) }.getOrDefault(false)
            _appLockUnlocked.value = unlocked
            onResult(unlocked)
        }
    }

    fun lockApp() {
        _appLockUnlocked.value = false
    }

    fun addAsset(name: String, type: String, balanceCents: Long) {
        viewModelScope.launch {
            repo.insertAsset(
                com.example.app.data.AssetEntity(
                    name = name,
                    type = type,
                    balanceCents = balanceCents,
                    lastUpdated = LocalDate.now().toString()
                )
            )
        }
    }

    fun updateAsset(asset: com.example.app.data.AssetEntity) {
        viewModelScope.launch {
            repo.updateAsset(asset.copy(lastUpdated = LocalDate.now().toString()))
        }
    }

    fun deleteAsset(asset: com.example.app.data.AssetEntity) {
        viewModelScope.launch { repo.deleteAsset(asset) }
    }

    fun updateDashboardConfig(config: DashboardConfig) {
        _dashboardConfig.value = config
    }

    // Goal Management
    fun addGoal(goal: com.example.app.data.GoalEntity) {
        viewModelScope.launch { repo.insertGoal(goal) }
    }
    fun updateGoal(goal: com.example.app.data.GoalEntity) {
        viewModelScope.launch { repo.updateGoal(goal) }
    }
    fun deleteGoal(goal: com.example.app.data.GoalEntity) {
        viewModelScope.launch { repo.deleteGoal(goal) }
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
                _dashboardConfig,
            ) { billOccurrences, assets, goals, config ->
                PlanningReportingData(billOccurrences, assets, goals, config)
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
                val mc = MonteCarloEngine(MonteCarloParams()).runSimulation(forecastSeedCents, events)
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
                val totalAssetBalance = pack.assets.sumOf { it.balanceCents }
                val totalNetWorthCents = ledgerBalanceCents.toLong() + totalAssetBalance

                val dailyVelocityCents = kotlin.math.abs(flowSummary.outflowCents) / 30
                val runwayDays = if (dailyVelocityCents > 0) (safeToSpend / dailyVelocityCents).coerceAtMost(90) else 90
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
                    (avgMonthlyOutflow * (day.toFloat() / today.lengthOfMonth())).toInt()
                }

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
                    dashboardConfig = pack.dashboardConfig
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
            if (transaction.type == "income") transaction.amount_cents else 0
        }
        val outflowCents = kotlin.math.abs(sumOf { transaction ->
            if (transaction.type == "expense") transaction.amount_cents else 0
        })
        return FlowSummary(
            inflowCents = inflowCents,
            outflowCents = outflowCents,
        )
    }

    private fun buildTransactionReviewItems(
        transactions: List<TransactionEntity>,
        recurringCandidates: List<com.example.app.data.RecurringCandidate>,
        today: LocalDate,
    ): List<TransactionReviewItem> {
        val recentCutoff = today.minusDays(14)
        val recurringByPattern = recurringCandidates.associateBy { it.pattern.lowercase() }
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
                val normalizedDescription = transaction.description.trim().lowercase()
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
        forecastSeedCents: Int,
        scheduledBillBurdenCents: Int,
        goals: List<com.example.app.data.GoalEntity>,
        safeToSpendCents: Int,
    ): List<MoneyBucketState> {
        val positiveSeed = forecastSeedCents.coerceAtLeast(0)
        val goalTargetCents = goals.sumOf { it.targetAmountCents }
        val goalCurrentCents = goals.sumOf { it.currentAmountCents }
        val goalRemainingCents = (goalTargetCents - goalCurrentCents).coerceAtLeast(0)
        val availableCents = safeToSpendCents.coerceAtLeast(0)
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
                detail = if (goalRemainingCents > 0) "${formatCurrency(goalRemainingCents)} still targeted" else "No open goal gap",
                progress = if (goalTargetCents > 0) goalCurrentCents.toFloat() / goalTargetCents else 0f,
                accent = MoneyBucketAccent.Goals,
            ),
            MoneyBucketState(
                label = "Available after plan",
                amountCents = availableCents,
                detail = if (safeToSpendCents < 0) "Forecast is below zero" else "Safe-to-spend reserve",
                progress = if (positiveSeed > 0) availableCents.toFloat() / positiveSeed else 0f,
                accent = MoneyBucketAccent.Available,
            ),
        )
    }

    private fun buildActionCenter(
        safeToSpendCents: Int,
        reviewCount: Int,
        nextBillLabel: String,
        probabilityNegativePct: Double,
        runwayDays: Int,
        reconciled: Boolean,
        hasIncome: Boolean,
        hasBills: Boolean,
    ): ActionCenterState {
        val riskLabel = when {
            safeToSpendCents < 0 -> "Overdraft projected"
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
        return ActionCenterState(
            safeToSpendCents = safeToSpendCents,
            needsReviewCount = reviewCount,
            nextBillLabel = nextBillLabel,
            forecastRiskLabel = riskLabel,
            primaryActionLabel = label,
            primaryAction = action,
        )
    }

    private fun buildTrustSignals(
        updatedAt: LocalDateTime,
        reconciled: Boolean,
        transactionCount: Int,
        forecastSeedCents: Int,
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
                detail = "${formatCurrency(forecastSeedCents)} seed with $eventCount scheduled events",
                level = if (reconciled) TrustSignalLevel.Good else TrustSignalLevel.Attention,
            ),
        )
    }

    private fun formatCurrency(cents: Int): String {
        return "\$${String.format("%.2f", cents / 100.0)}"
    }

    private data class ReportingPackage(
        val incomes: List<com.example.app.data.IncomeEntity>,
        val payments: List<com.example.app.data.PaymentEntity>,
        val txns: List<com.example.app.data.TransactionEntity>,
        val balanceState: LedgerRepository.BalanceState,
        val billOccurrences: List<com.example.app.data.BillOccurrenceEntity>,
        val assets: List<com.example.app.data.AssetEntity>,
        val goals: List<com.example.app.data.GoalEntity>,
        val dashboardConfig: DashboardConfig,
    )

    private data class LedgerReportingData(
        val incomes: List<com.example.app.data.IncomeEntity>,
        val payments: List<com.example.app.data.PaymentEntity>,
        val txns: List<com.example.app.data.TransactionEntity>,
        val balanceState: LedgerRepository.BalanceState,
    )

    private data class PlanningReportingData(
        val billOccurrences: List<com.example.app.data.BillOccurrenceEntity>,
        val assets: List<com.example.app.data.AssetEntity>,
        val goals: List<com.example.app.data.GoalEntity>,
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

    fun processPayday(income: com.example.app.data.IncomeEntity, actualAmountCents: Int) {
        viewModelScope.launch {
            repo.processPayday(income, actualAmountCents)
        }
    }

    fun addTransaction(
        description: String,
        amountCents: Int,
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
