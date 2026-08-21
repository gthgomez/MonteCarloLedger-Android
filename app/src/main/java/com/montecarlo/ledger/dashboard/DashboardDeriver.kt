package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.ActionCenterState
import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.DashboardPrimaryAction
import com.montecarlo.ledger.MoneyBucketAccent
import com.montecarlo.ledger.MoneyBucketState
import com.montecarlo.ledger.TransactionReviewItem
import com.montecarlo.ledger.TrustSignal
import com.montecarlo.ledger.TrustSignalLevel
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.CategoryBudgetEntity
import com.montecarlo.ledger.data.CategoryRulePresets
import com.montecarlo.ledger.data.CategorySpend
import com.montecarlo.ledger.domain.Categories
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.FlowSummary
import com.montecarlo.ledger.data.GoalEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.RecurringCandidate
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.data.TransactionRuleEntity
import com.montecarlo.ledger.processing.BalanceSeedResolver
import com.montecarlo.ledger.processing.BudgetPacingEngine
import com.montecarlo.ledger.processing.ForecastEngine
import com.montecarlo.ledger.processing.MonteCarloCalibration
import com.montecarlo.ledger.processing.MonteCarloCalibrator
import com.montecarlo.ledger.processing.MonteCarloEngine
import com.montecarlo.ledger.processing.MonteCarloParams
import com.montecarlo.ledger.processing.OverdraftActionEngine
import com.montecarlo.ledger.processing.RecurringDetector
import com.montecarlo.ledger.processing.TimelineService
import com.montecarlo.ledger.ui.formatDateDisplay
import com.montecarlo.ledger.util.LedgerDate
import com.montecarlo.ledger.util.centsToDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Pure-ish derivation pipeline for the dashboard: turns one consistent snapshot of
 * ledger data into [AppUiState] plus reconciliation signals.
 *
 * Extracted from MainViewModel so the pipeline is unit-testable without Android
 * plumbing, and so the ViewModel stays a state holder instead of a calculator.
 */
class DashboardDeriver {

    suspend fun derive(reporting: ReportingPackage, today: LocalDate): DashboardDerivation {
        val pack = reporting
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
        val events = TimelineService.generateTimeline(pack.incomes, pack.payments, today, 90, pack.billOccurrences, pack.rules)

        val nextPaycheck = events.filter { it.type == "income" }.minByOrNull { it.date }
        val daysUntilPayday = if (nextPaycheck != null) {
            ChronoUnit.DAYS.between(today, nextPaycheck.date).toInt().coerceAtLeast(1)
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

        // Calibrate simulation ranges from the user's own history instead of hardcoded
        // assumptions; fall back to defaults until enough months exist.
        val calibration = MonteCarloCalibrator.calibrate(
            transactions = pack.txns,
            today = today,
            recurringPatterns = recurringCandidates.map { it.pattern }.toSet(),
        )
        val mc = withContext(Dispatchers.Default) {
            MonteCarloEngine(calibration.toParams(includeDailyPercentiles = true))
                .runSimulation(forecastSeedCents, events, today)
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

        val driftCents = if (mismatch) ledgerBalanceCents - bankBalanceCents else 0L
        val totalAssetBalance = pack.assets.sumOf { it.balanceCents }
        val totalDebtBalance = pack.debts.filter { it.isActive }.sumOf { it.balanceCents }
        val totalNetWorthCents = ledgerBalanceCents + totalAssetBalance - totalDebtBalance

        val dailyVelocityCents = kotlin.math.abs(flowSummary.outflowCents) / 30
        val runwayDays = BudgetPacingEngine.clampedRunwayDays(
            safeToSpendCents = safeToSpend,
            dailyVelocityCents = dailyVelocityCents,
        )
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
        val overdraftRecommendations = OverdraftActionEngine.analyze(
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
            val d = LedgerDate.parseIsoOrNull(it.date)
            d != null && d.month == today.month && d.year == today.year && it.type == "expense"
        }
        val currentMonthPacing = (1..today.dayOfMonth).map { day ->
            currentMonthTxns.filter { LocalDate.parse(it.date).dayOfMonth <= day }.sumOf { kotlin.math.abs(it.amount_cents) }
        }

        val avgMonthlyOutflow = kotlin.math.abs(flowSummary.outflowCents)
        val avgMonthPacing = (1..today.lengthOfMonth()).map { day ->
            (avgMonthlyOutflow * (day.toFloat() / today.lengthOfMonth())).toLong()
        }

        val pacingResult = BudgetPacingEngine.calculatePacing(
            safeToSpendCents = safeToSpend,
            daysToPayday = daysUntilPayday,
            transactions = pack.txns,
            today = today,
        )

        val uiState = AppUiState(
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
            debts = pack.debts,
            categoryBudgetRows = categoryBudgetRows,
            transactionRules = pack.rules,
            pacingResult = pacingResult,
            monteCarloDailyPercentiles = mc.dailyPercentiles,
            monteCarloResult = mc,
            monteCarloBasisLabel = basisLabel(calibration),
            forecastInsights = com.montecarlo.ledger.processing.MonteCarloInsights.generate(
                transactions = pack.txns,
                today = today,
                calibration = calibration,
            ),
        )

        return DashboardDerivation(
            uiState = uiState,
            reconciliationMismatch = mismatch,
            reconciliationDetails = if (mismatch) Pair(ledgerBalanceCents, bankBalanceCents) else null,
        )
    }

    /**
     * Honest provenance for the simulation: calibrated from real history, or an
     * explicit statement that defaults are in play until enough history exists.
     */
    private fun basisLabel(calibration: MonteCarloCalibration): String {
        return if (calibration.isCalibrated) {
            "Based on ${calibration.monthsCovered} months of your history"
        } else if (calibration.monthsCovered == 0) {
            "Default assumptions — your history personalizes this as you log spending"
        } else {
            "Default assumptions — ${MonteCarloCalibration.MIN_CALIBRATED_MONTHS - calibration.monthsCovered} more month(s) personalizes this"
        }
    }

    private fun MonteCarloCalibration.toParams(includeDailyPercentiles: Boolean): MonteCarloParams =
        MonteCarloParams(
            incomeVariationMin = incomeVariationMin,
            incomeVariationMax = incomeVariationMax,
            expenseVariationMin = expenseVariationMin,
            expenseVariationMax = expenseVariationMax,
            expenseCategoryVariation = expenseCategoryVariation,
            surpriseProbability = surpriseProbability,
            surpriseAmountMin = surpriseAmountMin,
            surpriseAmountMax = surpriseAmountMax,
            includeDailyPercentiles = includeDailyPercentiles,
        )

    private fun List<TransactionEntity>.recentTransactionsSince(since: LocalDate): List<TransactionEntity> {
        return filter { transaction ->
            val parsedDate = LedgerDate.parseIsoOrNull(transaction.date)
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
        recurringCandidates: List<RecurringCandidate>,
        today: LocalDate,
    ): List<TransactionReviewItem> {
        val recentCutoff = today.minusDays(14)
        val recurringByPattern = recurringCandidates.associateBy { Categories.normalize(it.pattern) }
        return transactions.asSequence()
            .filter { it.review_status == "pending" || it.type == "expense" }
            .filter { transaction ->
                val parsedDate = LedgerDate.parseIsoOrNull(transaction.date)
                transaction.review_status == "pending" ||
                    Categories.isUncategorized(transaction.category) ||
                    (transaction.reviewed_at == null && parsedDate != null && !parsedDate.isBefore(recentCutoff))
            }
            .sortedWith(
                compareByDescending<TransactionEntity> { it.review_status == "pending" }
                    .thenByDescending { it.category.equals("uncategorized", ignoreCase = true) }
                    .thenByDescending { LedgerDate.parseIsoOrNull(it.date) ?: LocalDate.MIN }
                    .thenByDescending { kotlin.math.abs(it.amount_cents) }
            )
            .take(6)
            .map { transaction ->
                val normalizedDescription = Categories.normalize(transaction.description)
                val recurringMatch = recurringByPattern[normalizedDescription]
                val suggestedCategory = when {
                    !Categories.isUncategorized(transaction.category) -> transaction.category
                    recurringMatch != null && !recurringMatch.category.equals("uncategorized", ignoreCase = true) -> recurringMatch.category
                    else -> inferCategory(transaction.description)
                }
                val reason = when {
                    transaction.source == "csv_import" -> "Imported transaction"
                    transaction.review_status == "pending" -> "New activity"
                    Categories.isUncategorized(transaction.category) -> "Needs category"
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
        goals: List<GoalEntity>,
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
}

data class DashboardDerivation(
    val uiState: AppUiState,
    val reconciliationMismatch: Boolean,
    /** Pair(ledgerBalanceCents, bankBalanceCents) while mismatched. */
    val reconciliationDetails: Pair<Long, Long>?,
)

data class ReportingPackage(
    val incomes: List<IncomeEntity>,
    val payments: List<PaymentEntity>,
    val txns: List<TransactionEntity>,
    val balanceState: LedgerRepository.BalanceState,
    val billOccurrences: List<BillOccurrenceEntity>,
    val assets: List<com.montecarlo.ledger.data.AssetEntity>,
    val goals: List<GoalEntity>,
    val categoryBudgets: List<CategoryBudgetEntity>,
    val debts: List<DebtEntity>,
    val rules: List<TransactionRuleEntity>,
    val dashboardConfig: DashboardConfig,
)

data class LedgerReportingData(
    val incomes: List<IncomeEntity>,
    val payments: List<PaymentEntity>,
    val txns: List<TransactionEntity>,
    val balanceState: LedgerRepository.BalanceState,
)

data class PlanningReportingData(
    val billOccurrences: List<BillOccurrenceEntity>,
    val assets: List<com.montecarlo.ledger.data.AssetEntity>,
    val goals: List<GoalEntity>,
    val categoryBudgets: List<CategoryBudgetEntity>,
    val debts: List<DebtEntity>,
    val rules: List<TransactionRuleEntity>,
    val dashboardConfig: DashboardConfig,
)
