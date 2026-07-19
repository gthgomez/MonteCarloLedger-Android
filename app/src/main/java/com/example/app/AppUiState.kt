package com.example.app

import com.example.app.data.CategoryBudgetEntity
import com.example.app.data.CategorySpend
import com.example.app.data.RecurringCandidate
import com.example.app.data.TransactionEntity
import com.example.app.processing.BalanceForecastRow
import com.example.app.processing.CashFlowWindow
import com.example.app.processing.CategoryBudgetRow

data class AppUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val bankBalanceCents: Int = 0,
    val ledgerBalanceCents: Int = 0,
    val isBalanceReconciled: Boolean = false,
    val safeToSpendCents: Int = 0,
    val incomeContributionCents: Int = 0,
    val upcomingBills: List<String> = emptyList(),
    val nextPaydayLabel: String = "",
    val dailyBudgetCents: Int = 0,
    val upcomingBillBurdenCents: Int = 0,
    val monteCarlo10thCents: Int = 0,
    val monteCarlo50thCents: Int = 0,
    val monteCarlo90thCents: Int = 0,
    val probabilityNegativePct: Double = 0.0,
    val projectedTroubleDateLabel: String? = null,
    val firstNegativeDateLabel: String? = null,
    val lowestBalanceDateLabel: String? = null,
    val bankLedgerMismatch: Boolean = false,
    /** cents: ledgerBalanceCents - bankBalanceCents when bankLedgerMismatch is true, else 0 */
    val driftCents: Int = 0,
    val transactions: List<TransactionEntity> = emptyList(),
    val forecastRows: List<BalanceForecastRow> = emptyList(),
    val cashFlowWindows: List<CashFlowWindow> = emptyList(),
    val categorySpend: List<CategorySpend> = emptyList(),
    val recurringCandidates: List<RecurringCandidate> = emptyList(),
    val totalInflowCents: Int = 0,
    val totalOutflowCents: Int = 0,
    val totalNetWorthCents: Long = 0,
    val runwayDays: Int = 0,
    val assets: List<com.example.app.data.AssetEntity> = emptyList(),
    val goals: List<com.example.app.data.GoalEntity> = emptyList(),
    val payments: List<com.example.app.data.PaymentEntity> = emptyList(),
    val adjustments: List<TransactionEntity> = emptyList(),
    val currentMonthPacing: List<Int> = emptyList(),
    val avgMonthPacing: List<Int> = emptyList(),
    val actionCenter: ActionCenterState = ActionCenterState(),
    val transactionReviewItems: List<TransactionReviewItem> = emptyList(),
    val moneyBuckets: List<MoneyBucketState> = emptyList(),
    val trustSignals: List<TrustSignal> = emptyList(),
    val dashboardConfig: DashboardConfig = DashboardConfig(),
    val categoryBudgets: List<CategoryBudgetEntity> = emptyList(),
    val categoryBudgetRows: List<CategoryBudgetRow> = emptyList(),
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
    val safeToSpendCents: Int = 0,
    val needsReviewCount: Int = 0,
    val nextBillLabel: String = "No upcoming bills",
    val forecastRiskLabel: String = "Add income and bills",
    val primaryActionLabel: String = "Start setup",
    val primaryAction: DashboardPrimaryAction = DashboardPrimaryAction.AddIncome,
    /** False until the user confirms bank balance — safe-to-spend is provisional. */
    val forecastUnlocked: Boolean = false,
    val safeToSpendCaption: String = "Safe to spend",
)

data class TransactionReviewItem(
    val transaction: TransactionEntity,
    val suggestedCategory: String,
    val reason: String,
)

data class MoneyBucketState(
    val label: String,
    val amountCents: Int,
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
