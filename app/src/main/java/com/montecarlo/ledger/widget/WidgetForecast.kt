package com.montecarlo.ledger.widget

import com.montecarlo.ledger.CanonicalForecastStatus
import com.montecarlo.ledger.adoption.CanonicalSimulationConfig
import com.montecarlo.ledger.adoption.ContractDashboardMapper
import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractRunner
import com.montecarlo.ledger.dashboard.CanonicalAdoption
import com.montecarlo.ledger.dashboard.ContractCalculationResult
import com.montecarlo.ledger.dashboard.canonicalCalculation
import com.montecarlo.ledger.data.BillOccurrenceEntity
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.data.TransactionRuleEntity
import com.montecarlo.ledger.data.isCreditAccount
import com.montecarlo.ledger.processing.ForecastEngine
import com.montecarlo.ledger.processing.MonteCarloCalibration
import com.montecarlo.ledger.processing.MonteCarloCalibrator
import com.montecarlo.ledger.processing.MonteCarloEngine
import com.montecarlo.ledger.processing.MonteCarloParams
import com.montecarlo.ledger.processing.TimelineService
import java.time.LocalDate

/** Neutral label shown when the canonical calculation is withheld, instead of a native number. */
internal const val WIDGET_WITHHELD_LABEL = "Unavailable"

/**
 * Cash-ledger routing for the widget seed, mirroring DashboardDeriver exactly: only the account's
 * TYPE decides cash-vs-card. A credit-type account's charges live on the card liability (counted
 * once, as debt) and never move the bank, so they must not lower the widget's cash seed — otherwise
 * the widget would disagree with the dashboard for the same ledger.
 */
internal fun widgetCashLedgerCents(
    txns: List<TransactionEntity>,
    accounts: List<com.montecarlo.ledger.data.AccountEntity>,
): Long {
    val creditAccountIds = accounts.filter { it.isCreditAccount() }.map { it.id }.toSet()
    return txns
        .filter { it.account_id == null || it.account_id !in creditAccountIds }
        .sumOf { it.amount_cents }
}

/**
 * The home-screen widget's headline numbers together with how they were produced.
 *
 * [status] mirrors [com.montecarlo.ledger.dashboard.DashboardDeriver]'s
 * [CanonicalForecastStatus]; [available] is false only when the canonical calculation was withheld
 * ([CanonicalForecastStatus.Failed]) and therefore no number may be shown.
 */
internal data class WidgetForecast(
    /** Headline safe-to-spend; 0 when [available] is false and must not be displayed. */
    val safeToSpendCents: Long,
    val probabilityNegativePct: Double,
    val riskLabel: String,
    val status: CanonicalForecastStatus,
    val available: Boolean,
    /**
     * Simulation-provenance parity with the dashboard: true when the widget's risk numbers were
     * calibrated from the user's history rather than the hardcoded default assumptions. A widget
     * must never claim a different basis than the dashboard would for the same ledger.
     */
    val calibrated: Boolean,
)

/**
 * WS2/F1: the widget's headline derivation, extracted so it is unit-testable without Android/Glance
 * plumbing.
 *
 * It mirrors the dashboard's fail-closed policy through the SAME
 * [ContractCalculationResult]/[CanonicalForecastStatus] mechanism: while `contractForecastEnabled`
 * is true the canonical contract engine is the only source of the widget's headline numbers. A
 * canonical failure yields the neutral [WIDGET_WITHHELD_LABEL] state — the legacy `ForecastEngine` /
 * `MonteCarloEngine` are never substituted behind the user's back. (The widget has no in-memory
 * retention across refreshes, so unlike the dashboard it fails closed rather than showing the last
 * known valid result.)
 *
 * It also mirrors the dashboard's simulation provenance: the same [MonteCarloCalibrator] history
 * calibration and the same per-bill category resolution feed the scenario, so the widget cannot
 * disagree with the dashboard because it invented different variance assumptions.
 *
 * The native engines remain the intended source only when the user has turned the contract engine
 * off ([CanonicalForecastStatus.Native]).
 */
internal fun deriveWidgetForecast(
    today: LocalDate,
    contractForecastEnabled: Boolean,
    forecastSeedCents: Long,
    incomes: List<IncomeEntity>,
    payments: List<PaymentEntity>,
    occurrences: List<BillOccurrenceEntity>,
    txns: List<TransactionEntity>,
    rules: List<TransactionRuleEntity>,
): WidgetForecast {
    val events = TimelineService.generateTimeline(incomes, payments, today, 90, occurrences, rules)
    val calibration = MonteCarloCalibrator.calibrate(transactions = txns, today = today)

    if (!contractForecastEnabled) {
        val summary = ForecastEngine.calculateForecastSummary(forecastSeedCents, events, today)
        val safeToSpend = summary.safeToSpendCents ?: summary.projectedLowPointCents
        val probability = MonteCarloEngine(calibration.toWidgetParams())
            .runSimulation(forecastSeedCents, events, today)
            .probability_negative_pct
        return WidgetForecast(
            safeToSpendCents = safeToSpend,
            probabilityNegativePct = probability,
            riskLabel = riskLabel(safeToSpend, probability),
            status = CanonicalForecastStatus.Native,
            available = true,
            calibrated = calibration.isCalibrated,
        )
    }

    val outcome: ContractCalculationResult<CanonicalAdoption> = canonicalCalculation {
        // Contract 1.2: carry each bill's resolved category so per-category variation can match,
        // exactly as DashboardDeriver.adoptContract does.
        val paymentCategories = payments
            .mapNotNull { payment ->
                TimelineService.resolveCategory(payment.name, rules)?.let { payment.id to it }
            }
            .toMap()
        val built = ContractScenarioBridge.build(
            ContractScenarioBridge.Inputs(
                startingBalanceCents = forecastSeedCents,
                asOf = today,
                incomes = incomes,
                payments = payments,
                billOccurrences = occurrences,
                // Contract MCD-0023: the simulation is defined for any scenario; an empty ledger
                // must not be given a fabricated 0% risk.
                simulation = ContractScenarioBridge.simulationParams(
                    calibration,
                    runs = CanonicalSimulationConfig.RUNS,
                ),
                paymentCategories = paymentCategories,
            )
        )
        val result = ContractRunner.run(built)
        CanonicalAdoption(
            forecastSummary = ContractDashboardMapper.toForecastSummary(result),
            monteCarloResult = ContractDashboardMapper.toMonteCarloResult(
                result,
                CanonicalSimulationConfig.RUNS,
            ),
            forecastRows = ContractDashboardMapper.toBalanceForecastRows(built),
        )
    }

    return when (outcome) {
        is ContractCalculationResult.Success -> {
            val summary = outcome.value.forecastSummary
            val probability = outcome.value.monteCarloResult.probability_negative_pct
            val safeToSpend = summary.safeToSpendCents ?: summary.projectedLowPointCents
            WidgetForecast(
                safeToSpendCents = safeToSpend,
                probabilityNegativePct = probability,
                riskLabel = riskLabel(safeToSpend, probability),
                status = CanonicalForecastStatus.Live,
                available = true,
                calibrated = calibration.isCalibrated,
            )
        }
        is ContractCalculationResult.Failure -> WidgetForecast(
            safeToSpendCents = 0L,
            probabilityNegativePct = 0.0,
            riskLabel = WIDGET_WITHHELD_LABEL,
            status = CanonicalForecastStatus.Failed,
            available = false,
            calibrated = calibration.isCalibrated,
        )
    }
}

/** The widget's native-path parameters: calibrated like the dashboard, without the daily fan chart. */
private fun MonteCarloCalibration.toWidgetParams(): MonteCarloParams =
    MonteCarloParams(
        incomeVariationMin = incomeVariationMin,
        incomeVariationMax = incomeVariationMax,
        expenseVariationMin = expenseVariationMin,
        expenseVariationMax = expenseVariationMax,
        expenseCategoryVariation = expenseCategoryVariation,
        surpriseProbability = surpriseProbability,
        surpriseAmountMin = surpriseAmountMin,
        surpriseAmountMax = surpriseAmountMax,
        includeDailyPercentiles = false,
    )

private fun riskLabel(safeToSpendCents: Long, probabilityNegativePct: Double): String = when {
    safeToSpendCents < 0 -> "Shortfall Projected"
    probabilityNegativePct >= 25.0 -> "High Risk (${String.format("%.0f", probabilityNegativePct)}%)"
    else -> "Stable Forecast"
}
