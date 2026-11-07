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
    /**
     * Cash basis of the app ledger: transactions with no account or a non-credit account.
     * A credit account's charges live on the card liability instead, mirroring the repository's
     * transaction side effects — this is the number the bank balance is reconciled against.
     */
    val cashLedgerCents: Long = 0L,
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
    /** cents: cash ledger balance - bankBalanceCents when bankLedgerMismatch is true, else 0 */
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
    /** Honest provenance for the simulation ranges (calibrated from history vs defaults). */
    val monteCarloBasisLabel: String? = null,
    /** Plain-language explanations of what drives the 3-month estimate. */
    val forecastInsights: List<com.montecarlo.ledger.processing.ForecastInsight> = emptyList(),
    /**
     * Which engine produced the headline financial conclusions (WS2). When the contract engine is
     * enabled and its calculation fails, the UI shows the last known valid canonical result marked
     * [CanonicalForecastStatus.Stale] (or fails closed as [CanonicalForecastStatus.Failed]) instead
     * of silently substituting the legacy native engines.
     */
    val canonicalForecastStatus: CanonicalForecastStatus = CanonicalForecastStatus.Live,
    /** Human-readable reason the canonical contract calculation failed; null when it succeeded. */
    val canonicalForecastError: String? = null,
    /**
     * Timestamp (ISO-like `yyyy-MM-dd HH:mm`) of the canonical result backing the headline numbers.
     * Set for [CanonicalForecastStatus.Live] and, importantly, [CanonicalForecastStatus.Stale] so a
     * retained value is always labelled with its as-of time. Null when no canonical value is shown.
     */
    val canonicalForecastAsOf: String? = null,
    /**
     * True when the risk model is running on default assumptions because the ledger does not yet
     * cover [com.montecarlo.ledger.processing.MonteCarloCalibration.MIN_CALIBRATED_MONTHS] months
     * (WS4). This is the explicit INSUFFICIENT-DATA state: defaults are never presented as calibrated.
     */
    val riskModelInsufficientData: Boolean = false,
    /**
     * Per-assumption provenance for the risk model and its displayed probability (WS4): where each
     * input came from (user-entered / history-derived / unverified) and whether the probability has
     * been validated out-of-sample.
     */
    val assumptionProvenance: List<AssumptionProvenance> = emptyList(),
) {
    /** The provenance row for the displayed overdraft probability, if any (WS4). */
    val riskProbabilityProvenance: AssumptionProvenance?
        get() = assumptionProvenance.firstOrNull { it.label == AssumptionProvenance.RISK_PROBABILITY_LABEL }
}

/**
 * Provenance of the headline financial conclusions (WS2).
 *
 * [Native] is a deliberate, user-selected mode (contract engine disabled) — not a fallback. Every
 * other state means the contract engine is (or was) the canonical engine.
 */
enum class CanonicalForecastStatus {
    /** Contract engine enabled and its latest calculation succeeded. */
    Live,

    /** Contract engine enabled but the latest calculation failed; the retained last known valid result is shown, marked stale. */
    Stale,

    /** Contract engine enabled, the calculation failed, and there is no prior valid result — headline numbers fail closed. */
    Failed,

    /** Contract engine disabled by the user; the native engine is the intended engine. */
    Native,
}

/**
 * Where a headline risk-model assumption comes from (WS4).
 *
 * Deliberately orthogonal to [CanonicalForecastStatus]: that records *which engine* produced a
 * number, while this records whether the *inputs — and the probability itself —* are trusted.
 */
enum class AssumptionOrigin {
    /** Supplied directly by the user (e.g. a bank balance they confirmed). */
    UserEntered,

    /** Derived from the user's own logged history (calibrated statistics). */
    HistoryDerived,

    /**
     * A default/assumed value, or a probability that has not been validated out-of-sample.
     * This is the honest label for anything the app cannot yet stand behind.
     */
    Unverified,
}

/** Whether the risk model had enough evidence to make a claim at all (WS4). */
enum class DataSufficiency {
    Sufficient,
    /** Running on defaults because history is below the calibrated floor — never "calibrated". */
    InsufficientData,
}

/**
 * One line of honest provenance for a risk-model assumption or output (WS4).
 *
 * @param label short human-readable name, also used as the stable key for the displayed probability.
 * @param value the value the label describes (already display-formatted).
 * @param origin where the value came from.
 * @param sufficiency whether there was enough data to support it.
 * @param detail a one-sentence explanation for the trust layer.
 */
data class AssumptionProvenance(
    val label: String,
    val value: String,
    val origin: AssumptionOrigin,
    val sufficiency: DataSufficiency,
    val detail: String,
) {
    companion object {
        /** Stable label/key for the displayed overdraft probability row. */
        const val RISK_PROBABILITY_LABEL = "Risk probability"
    }
}

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
    /**
     * WS2/F2: false when the canonical contract calculation was withheld, so the neutral zeros in
     * [safeToSpendCents]/[forecastRiskLabel] must be presented as "unavailable" rather than as a
     * genuine "no risk" / "$0" result.
     */
    val forecastAvailable: Boolean = true,
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
