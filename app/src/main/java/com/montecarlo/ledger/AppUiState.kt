package com.montecarlo.ledger

import com.montecarlo.ledger.data.CategoryBudgetEntity
import com.montecarlo.ledger.data.DebtEntity
import com.montecarlo.ledger.data.CategorySpend
import com.montecarlo.ledger.data.RecurringCandidate
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.processing.BalanceForecastRow
import com.montecarlo.ledger.processing.CashFlowWindow
import com.montecarlo.ledger.processing.CategoryBudgetRow

data class AppUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bankBalanceCents: Long = 0L,
    val ledgerBalanceCents: Long = 0L,
    val isBalanceReconciled: Boolean = false,
    val safeToSpendCents: Long = 0L,
    val incomeContributionCents: Long = 0L,
    val upcomingBills: List<String> = emptyList(),
    val nextPaydayLabel: String = "",
    val dailyBudgetCents: Long = 0L,
    val upcomingBillBurdenCents: Long = 0L,
    val monteCarlo10thCents: Long = 0L,
    val monteCarlo50thCents: Long = 0L,
    val monteCarlo90thCents: Long = 0L,
    val probabilityNegativePct: Double = 0.0,
    val projectedTroubleDateLabel: String? = null,
    val firstNegativeDateLabel: String? = null,
    val lowestBalanceDateLabel: String? = null,
    val bankLedgerMismatch: Boolean = false,
    /** cents: ledgerBalanceCents - bankBalanceCents when bankLedgerMismatch is true, else 0 */
    val driftCents: Long = 0L,
    val transactions: List<TransactionEntity> = emptyList(),
    val forecastRows: List<BalanceForecastRow> = emptyList(),
    val cashFlowWindows: List<CashFlowWindow> = emptyList(),
    val categorySpend: List<CategorySpend> = emptyList(),
    val recurringCandidates: List<RecurringCandidate> = emptyList(),
    val totalInflowCents: Long = 0L,
    val totalOutflowCents: Long = 0L,
    val totalNetWorthCents: Long = 0L,
    val runwayDays: Int = 0,
    val assets: List<com.montecarlo.ledger.data.AssetEntity> = emptyList(),
    val goals: List<com.montecarlo.ledger.data.GoalEntity> = emptyList(),
    val payments: List<com.montecarlo.ledger.data.PaymentEntity> = emptyList(),
    val adjustments: List<TransactionEntity> = emptyList(),
    val currentMonthPacing: List<Long> = emptyList(),
    val avgMonthPacing: List<Long> = emptyList(),
    val actionCenter: ActionCenterState = ActionCenterState(),
    val transactionReviewItems: List<TransactionReviewItem> = emptyList(),
    val moneyBuckets: List<MoneyBucketState> = emptyList(),
    val trustSignals: List<TrustSignal> = emptyList(),
    val dashboardConfig: DashboardConfig = DashboardConfig(),
    val categoryBudgets: List<CategoryBudgetEntity> = emptyList(),
    val debts: List<DebtEntity> = emptyList(),
    val categoryBudgetRows: List<CategoryBudgetRow> = emptyList(),
    val transactionRules: List<com.montecarlo.ledger.data.TransactionRuleEntity> = emptyList(),
    val pacingResult: com.montecarlo.ledger.processing.BudgetPacingResult? = null,
    val monteCarloDailyPercentiles: List<com.montecarlo.ledger.processing.DailyPercentilePoint> = emptyList(),
    val monteCarloResult: com.montecarlo.ledger.processing.MonteCarloResult? = null,
)

enum class DashboardWidget {
    ActionCenter, ReviewInbox, MoneyBuckets, TrustLayer, Balance, Monitoring, NetWorth, Goal, PlanAhead, MonteCarlo
}

data class DashboardConfig(
    val visibleWidgets: Set<DashboardWidget> = DashboardWidget.values().toSet()
)

enum class DashboardPrimaryAction {
    ReviewTransactions,
    ConfirmBalance,
    AddIncome,
    AddBill,
    RecordSpending,
    OpenForecast
}

data class ActionCenterState(
    val safeToSpendCents: Long = 0L,
    val needsReviewCount: Int = 0,
    val nextBillLabel: String = "No upcoming bills",
    val forecastRiskLabel: String = "Add income and bills",
    val primaryActionLabel: String = "Start setup",
    val primaryAction: DashboardPrimaryAction = DashboardPrimaryAction.AddIncome,
    /** False until the user confirms bank balance — safe-to-spend is provisional. */
    val forecastUnlocked: Boolean = false,
    val safeToSpendCaption: String = "Safe to spend",
    val overdraftRecommendations: List<com.montecarlo.ledger.processing.OverdraftRecommendation> = emptyList(),
)

data class TransactionReviewItem(
    val transaction: TransactionEntity,
    val suggestedCategory: String,
    val reason: String,
)

data class MoneyBucketState(
    val label: String,
    val amountCents: Long,
    val detail: String,
    val progress: Float,
    val accent: MoneyBucketAccent,
)

enum class MoneyBucketAccent {
    Bills,
    Goals,
    Available,
}

data class TrustSignal(
    val label: String,
    val value: String,
    val detail: String,
    val level: TrustSignalLevel = TrustSignalLevel.Good,
)

enum class TrustSignalLevel {
    Good,
    Attention,
    Warning,
}
