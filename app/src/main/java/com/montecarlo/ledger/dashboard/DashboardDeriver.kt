package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.AppUiState
import com.montecarlo.ledger.ActionCenterState
import com.montecarlo.ledger.AssumptionOrigin
import com.montecarlo.ledger.AssumptionProvenance
import com.montecarlo.ledger.CanonicalForecastStatus
import com.montecarlo.ledger.DataSufficiency
import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.DashboardPrimaryAction
import com.montecarlo.ledger.MoneyBucketAccent
import com.montecarlo.ledger.MoneyBucketState
import com.montecarlo.ledger.TransactionReviewItem
import com.montecarlo.ledger.TrustSignal
import com.montecarlo.ledger.TrustSignalLevel
import com.montecarlo.ledger.adoption.ContractDashboardMapper
import com.montecarlo.ledger.adoption.ContractScenarioBridge
import com.montecarlo.ledger.contract.ContractRunner
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
import com.montecarlo.ledger.data.isCreditAccount
import com.montecarlo.ledger.processing.BalanceForecastRow
import com.montecarlo.ledger.processing.BalanceSeedResolver
import com.montecarlo.ledger.processing.BudgetPacingEngine
import com.montecarlo.ledger.processing.ForecastEngine
import com.montecarlo.ledger.processing.MonteCarloCalibration
import com.montecarlo.ledger.processing.MonteCarloCalibrator
import com.montecarlo.ledger.processing.DailyPercentilePoint
import com.montecarlo.ledger.processing.ForecastSummary
import com.montecarlo.ledger.processing.MonteCarloEngine
import com.montecarlo.ledger.processing.MonteCarloParams
import com.montecarlo.ledger.processing.MonteCarloResult
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
import java.util.Locale

/**
 * Pure-ish derivation pipeline for the dashboard: turns one consistent snapshot of
 * ledger data into [AppUiState] plus reconciliation signals.
 *
 * Extracted from MainViewModel so the pipeline is unit-testable without Android
 * plumbing, and so the ViewModel stays a state holder instead of a calculator.
 */
class DashboardDeriver {

    /**
     * Last canonical (contract) result that succeeded, stamped with when it was computed (WS2).
     *
     * Persistence note: this is intentionally in-memory rather than a settings-table row.
     * [DashboardDeriver] is a deliberately persistence-free, Android-free derivation unit (its whole
     * design goal is to be testable without Room/plumbing), and a canonical result is a rich,
     * structured value (quantile summary, ordered rows, Monte Carlo percentiles) that has no
     * serialization contract in the settings store. [com.montecarlo.ledger.MainViewModel] holds a
     * single long-lived instance, so retention survives every re-derivation for the life of the
     * process. On a cold start there is no retained value and the dashboard fails closed instead
     * (see [CanonicalForecastStatus.Failed]) — it never silently switches engines.
     */
    private var lastKnownCanonical: RetainedCanonical? = null

    /**
     * [contractForecastEnabled] is the persisted adoption flag (MC-06b) supplied by the caller from
     * repository state; it defaults to ON. When false, the legacy native engines supply every
     * headline number.
     */
    suspend fun derive(
        reporting: ReportingPackage,
        today: LocalDate,
        contractForecastEnabled: Boolean = true,
    ): DashboardDerivation {
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

        // Cash vs card routing mirrors LedgerRepository.applyTransactionSideEffects exactly: it is
        // the ACCOUNT TYPE that decides, never whether some debt happens to be linked. A txn with no
        // account id is always cash; a credit-type account's charges live on the card liability
        // (counted once, as debt) instead of the bank. Deriving this from debt linkage instead would
        // both invent a drift for a non-credit account with a linked debt and count a credit account
        // without a linked debt as cash — the deposit/withdrawal surfaces the repo never moved.
        val creditAccountIds = pack.accounts
            .filter { it.isCreditAccount() }
            .map { it.id }
            .toSet()
        val ledgerBalanceCents = pack.txns.sumOf { it.amount_cents }
        val cashLedgerCents = pack.txns
            .filter { it.account_id == null || it.account_id !in creditAccountIds }
            .sumOf { it.amount_cents }
        val bankBalanceCents = pack.balanceState.bankBalanceCents
        val reconciled = pack.balanceState.isReconciled
        val mismatch = reconciled && bankBalanceCents != cashLedgerCents
        // Until the user confirms a reconciled balance, keep forecasts anchored to the live CASH
        // ledger so first-run balances don't collapse to zero and a card charge (which never left
        // the bank) cannot understate the forecast, safe-to-spend or the money buckets.
        val forecastSeedCents = BalanceSeedResolver.resolve(
            cashLedgerCents,
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

        val nativeForecastSummary = ForecastEngine.calculateForecastSummary(forecastSeedCents, events, today)
        val incomeContribution = ForecastEngine.calculateIncomeContribution(forecastSeedCents, events)
        val cashFlowWindows = ForecastEngine.buildCashFlowWindows(forecastSeedCents, events, today, 90)
        val currentCashFlowWindow = cashFlowWindows.firstOrNull()
        // Native per-window pacing heuristic; used only when the contract path is inactive.
        val nativeDailyBudgetCents = currentCashFlowWindow?.dailySafeSpendCents
            ?: ForecastEngine.calculateDailySafeSpend(forecastSeedCents, events, daysUntilPayday)

        // Calibrate simulation ranges from the user's own history instead of hardcoded
        // assumptions; fall back to defaults until enough months exist.
        val calibration = MonteCarloCalibrator.calibrate(
            transactions = pack.txns,
            today = today,
            recurringPatterns = recurringCandidates.map { it.pattern }.toSet(),
        )
        // The non-normative daily fan chart is not part of Contract 1.0; the native engine
        // still supplies it (with B-05/B-06 fixed) while the contract governs every headline
        // number below when the adoption flag is on.
        val nativeMc = withContext(Dispatchers.Default) {
            MonteCarloEngine(calibration.toParams(includeDailyPercentiles = true))
                .runSimulation(forecastSeedCents, events, today)
        }
        val contractOutcome: ContractCalculationResult<CanonicalAdoption>? = if (contractForecastEnabled) {
            withContext(Dispatchers.Default) {
                adoptContract(pack, forecastSeedCents, today, calibration, nativeMc.dailyPercentiles)
            }
        } else {
            null
        }

        // WS2: when the contract fails, keep the last known valid canonical result so the headline
        // numbers never silently degrade onto the legacy native engines.
        val canonical: CanonicalAdoption? = when (contractOutcome) {
            is ContractCalculationResult.Success -> {
                lastKnownCanonical = RetainedCanonical(contractOutcome.value, LocalDateTime.now())
                contractOutcome.value
            }
            is ContractCalculationResult.Failure -> lastKnownCanonical?.value
            null -> null
        }

        val canonicalFailure = contractOutcome as? ContractCalculationResult.Failure
        val canonicalStatus = when {
            !contractForecastEnabled -> CanonicalForecastStatus.Native
            contractOutcome is ContractCalculationResult.Success -> CanonicalForecastStatus.Live
            lastKnownCanonical != null -> CanonicalForecastStatus.Stale
            else -> CanonicalForecastStatus.Failed
        }
        val canonicalAsOf = when (canonicalStatus) {
            CanonicalForecastStatus.Live, CanonicalForecastStatus.Stale ->
                lastKnownCanonical?.computedAt?.format(CANONICAL_TIMESTAMP)
            CanonicalForecastStatus.Failed, CanonicalForecastStatus.Native -> null
        }

        // Headline conclusions. The canonical contract engine supplies them whenever it is the
        // active engine — live on success, retained (marked stale) on failure. The native engines are
        // used ONLY in the deliberate user-selected `Native` mode. A failed canonical engine with no
        // retained result fails closed with neutral values; native numbers are never substituted.
        val forecastSummary: ForecastSummary
        val mc: MonteCarloResult
        val forecastRows: List<BalanceForecastRow>
        val dailyBudgetCents: Long
        when (canonicalStatus) {
            CanonicalForecastStatus.Live, CanonicalForecastStatus.Stale -> {
                val canonicalResult = checkNotNull(canonical) {
                    "canonical headline values missing for status $canonicalStatus"
                }
                forecastSummary = canonicalResult.forecastSummary
                mc = canonicalResult.monteCarloResult
                forecastRows = canonicalResult.forecastRows
                // MC-07/C2: the daily budget is a product heuristic derived from the canonical
                // safe-to-spend so it cannot contradict the value shown beside it.
                val canonicalSafeToSpend =
                    forecastSummary.safeToSpendCents ?: forecastSummary.projectedLowPointCents
                dailyBudgetCents = if (canonicalSafeToSpend > 0L) {
                    canonicalSafeToSpend / daysUntilPayday
                } else {
                    0L
                }
            }
            CanonicalForecastStatus.Failed -> {
                forecastSummary = FAIL_CLOSED_FORECAST_SUMMARY
                mc = FAIL_CLOSED_MONTE_CARLO
                forecastRows = emptyList()
                dailyBudgetCents = 0L
            }
            CanonicalForecastStatus.Native -> {
                forecastSummary = nativeForecastSummary
                mc = nativeMc
                forecastRows = ForecastEngine.buildBalanceForecast(forecastSeedCents, events)
                dailyBudgetCents = nativeDailyBudgetCents
            }
        }
        // MCD-0008: only the contract path models a trough distribution, so only it can supply a
        // genuine quantile-based safe-to-spend. The native path falls back to its deterministic
        // low point, which the UI already labels "lowest balance over next 90 days".
        val safeToSpend = forecastSummary.safeToSpendCents ?: forecastSummary.projectedLowPointCents
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

        val driftCents = if (mismatch) cashLedgerCents - bankBalanceCents else 0L
        val totalAssetBalance = pack.assets.sumOf { it.balanceCents }
        val totalDebtBalance = pack.debts.filter { it.isActive }.sumOf { it.balanceCents }
        val cashBalanceCents = if (reconciled) bankBalanceCents else cashLedgerCents
        val totalNetWorthCents = cashBalanceCents + totalAssetBalance - totalDebtBalance

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
            canonicalForecastStatus = canonicalStatus,
        )
        // WS4: honest provenance for the risk model. The displayed probability is always labelled
        // "Unverified" until it has been validated out-of-sample, and defaults are flagged
        // InsufficientData so they are never presented as calibrated.
        val assumptionProvenance = buildAssumptionProvenance(
            calibration = calibration,
            reconciled = reconciled,
            forecastSeedCents = forecastSeedCents,
            probabilityNegativePct = mc.probability_negative_pct,
        )
        val trustSignals = buildTrustSignals(
            updatedAt = LocalDateTime.now(),
            reconciled = reconciled,
            transactionCount = pack.txns.size,
            forecastSeedCents = forecastSeedCents,
            eventCount = events.size,
            reviewCount = reviewItems.size,
            canonicalStatus = canonicalStatus,
            canonicalFailureReason = canonicalFailure?.reason,
            canonicalAsOf = canonicalAsOf,
            assumptionProvenance = assumptionProvenance,
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
        val daysInMonth = today.lengthOfMonth()
        val avgMonthPacing = (1..daysInMonth).map { day ->
            java.math.BigDecimal.valueOf(avgMonthlyOutflow)
                .multiply(java.math.BigDecimal.valueOf(day.toLong()))
                .divide(java.math.BigDecimal.valueOf(daysInMonth.toLong()), 0, java.math.RoundingMode.HALF_UP)
                .longValueExact()
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
            cashLedgerCents = cashLedgerCents,
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
            // WS2/F3: the fan chart is gated on the canonical status. When the contract engine is
            // the active engine the daily percentiles come from the canonical result (which carries
            // the non-normative overlay captured at the last success, so a Stale chart stays
            // internally consistent); when the calculation is withheld the fan is empty rather than
            // silently showing native-engine paths. Native mode keeps the native percentiles.
            monteCarloDailyPercentiles = mc.dailyPercentiles,
            monteCarloResult = mc,
            monteCarloBasisLabel = basisLabel(calibration),
            forecastInsights = com.montecarlo.ledger.processing.MonteCarloInsights.generate(
                transactions = pack.txns,
                today = today,
                calibration = calibration,
            ),
            canonicalForecastStatus = canonicalStatus,
            canonicalForecastError = canonicalFailure?.reason,
            canonicalForecastAsOf = canonicalAsOf,
            riskModelInsufficientData = !calibration.isCalibrated,
            assumptionProvenance = assumptionProvenance,
        )

        return DashboardDerivation(
            uiState = uiState,
            reconciliationMismatch = mismatch,
            reconciliationDetails = if (mismatch) Pair(cashLedgerCents, bankBalanceCents) else null,
        )
    }

    /**
     * Runs the canonical contract engine over repository data and maps the result onto the
     * dashboard's state types.
     *
     * WS2: a contract failure is returned as an explicit [ContractCalculationResult.Failure] carrying
     * its reason and timestamp — it is never collapsed into `null` and never silently swallowed. The
     * caller decides how to degrade (fail closed, or show a retained stale result); it must not fall
     * back to the native engines while the contract engine is the active, enabled engine.
     *
     * The run is routed through the shared [canonicalCalculation] helper so it catches only
     * [Exception] (WS2/F4: fatal `Error`s propagate) and re-throws coroutine cancellation, and so the
     * same failure contract is shared with the home-screen widget (WS2/F1).
     */
    private fun adoptContract(
        pack: ReportingPackage,
        startingBalanceCents: Long,
        asOf: LocalDate,
        calibration: MonteCarloCalibration,
        nativeDailyPercentiles: List<DailyPercentilePoint>,
    ): ContractCalculationResult<CanonicalAdoption> = canonicalCalculation {
        // Contract 1.2: carry each bill's resolved category so per-category variation can match.
        val paymentCategories = pack.payments
            .mapNotNull { payment ->
                TimelineService.resolveCategory(payment.name, pack.rules)?.let { payment.id to it }
            }
            .toMap()
        val built = ContractScenarioBridge.build(
            ContractScenarioBridge.Inputs(
                startingBalanceCents = startingBalanceCents,
                asOf = asOf,
                incomes = pack.incomes,
                payments = pack.payments,
                billOccurrences = pack.billOccurrences,
                simulation = ContractScenarioBridge.simulationParams(calibration),
                paymentCategories = paymentCategories,
            )
        )
        // Contract MCD-0023: the simulation is defined for any scenario. Surprise generation
        // depends only on the horizon and surprise parameters, never on whether scheduled
        // activity exists, so we must not zero out risk for an empty ledger here.
        val scenario = built
        val result = ContractRunner.run(scenario)
        val runs = scenario.simulation?.runs ?: 500
        CanonicalAdoption(
            forecastSummary = ContractDashboardMapper.toForecastSummary(result),
            monteCarloResult = ContractDashboardMapper.toMonteCarloResult(result, runs)
                .copy(dailyPercentiles = nativeDailyPercentiles),
            // MC-07/C1: the display rows are the contract's own ordered events, not a second
            // recurrence/forecast interpretation.
            forecastRows = ContractDashboardMapper.toBalanceForecastRows(scenario),
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

    /**
     * WS4: typed provenance for the risk model. Three distinct origins are surfaced explicitly:
     *
     *  - [AssumptionOrigin.UserEntered] — the forecast seed anchored to a bank balance the user confirmed.
     *  - [AssumptionOrigin.HistoryDerived] — variation ranges calibrated from enough logged history.
     *  - [AssumptionOrigin.Unverified] — default assumptions (with [DataSufficiency.InsufficientData])
     *    and, always, the displayed overdraft probability, which has not been validated out-of-sample.
     *
     * The probability row is deliberately `Unverified` even when the inputs are calibrated: having
     * enough history to calibrate variance does not prove the resulting probability is accurate.
     */
    private fun buildAssumptionProvenance(
        calibration: MonteCarloCalibration,
        reconciled: Boolean,
        forecastSeedCents: Long,
        probabilityNegativePct: Double,
    ): List<AssumptionProvenance> {
        val sufficiency =
            if (calibration.isCalibrated) DataSufficiency.Sufficient else DataSufficiency.InsufficientData

        val seedProvenance = if (reconciled) {
            AssumptionProvenance(
                label = "Forecast balance",
                value = centsToDisplay(forecastSeedCents),
                origin = AssumptionOrigin.UserEntered,
                sufficiency = DataSufficiency.Sufficient,
                detail = "Anchored to the bank balance you confirmed.",
            )
        } else {
            AssumptionProvenance(
                label = "Forecast balance",
                value = centsToDisplay(forecastSeedCents),
                origin = AssumptionOrigin.HistoryDerived,
                sufficiency = DataSufficiency.Sufficient,
                detail = "Derived from your logged transactions; confirm your bank balance to pin it.",
            )
        }

        val variationProvenance = AssumptionProvenance(
            label = "Income & expense variation",
            value = "±${calibration.incomeVariationMax}% income · ±${calibration.expenseVariationMax}% expense",
            origin = if (calibration.isCalibrated) AssumptionOrigin.HistoryDerived else AssumptionOrigin.Unverified,
            sufficiency = sufficiency,
            detail = when {
                calibration.isCalibrated ->
                    "Calibrated from ${calibration.monthsCovered} months of your logged history."
                calibration.monthsCovered == 0 ->
                    "Default assumptions — no history to calibrate from yet."
                else ->
                    "Default assumptions — only ${calibration.monthsCovered} month(s) logged; " +
                        "${MonteCarloCalibration.MIN_CALIBRATED_MONTHS} months calibrates the bands."
            },
        )

        val probabilityProvenance = AssumptionProvenance(
            label = AssumptionProvenance.RISK_PROBABILITY_LABEL,
            value = "${String.format(Locale.ROOT, "%.1f", probabilityNegativePct)}%",
            origin = AssumptionOrigin.Unverified,
            sufficiency = sufficiency,
            detail = if (calibration.isCalibrated) {
                "Not validated out-of-sample — treat as indicative, not calibrated."
            } else {
                "Built on default assumptions and not validated out-of-sample — treat as indicative."
            },
        )

        return listOf(seedProvenance, variationProvenance, probabilityProvenance)
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
        canonicalForecastStatus: CanonicalForecastStatus = CanonicalForecastStatus.Live,
    ): ActionCenterState {
        val forecastUnlocked = reconciled
        // WS2/F2: a withheld canonical calculation must not read as "no risk". When the contract
        // engine failed with no retained result the headline numbers are neutral zeros, so the risk
        // label and the safe-to-spend caption explicitly say the forecast is unavailable instead of
        // rendering "Stable forecast" / "Safe to spend" beside $0.
        val forecastAvailable = canonicalForecastStatus != CanonicalForecastStatus.Failed
        val riskLabel = when {
            !forecastAvailable -> "Unavailable"
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
            !forecastAvailable -> "Forecast unavailable"
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
            forecastAvailable = forecastAvailable,
        )
    }

    private fun buildTrustSignals(
        updatedAt: LocalDateTime,
        reconciled: Boolean,
        transactionCount: Int,
        forecastSeedCents: Long,
        eventCount: Int,
        reviewCount: Int,
        canonicalStatus: CanonicalForecastStatus,
        canonicalFailureReason: String?,
        canonicalAsOf: String?,
        assumptionProvenance: List<AssumptionProvenance>,
    ): List<TrustSignal> {
        val formatter = DateTimeFormatter.ofPattern("h:mm a")
        val signals = mutableListOf(
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
        // WS2: make a canonical-calculation failure visible instead of hiding a silent engine switch.
        when (canonicalStatus) {
            CanonicalForecastStatus.Stale -> signals += TrustSignal(
                label = "Forecast engine",
                value = "Using last valid result",
                detail = buildString {
                    append("Canonical calculation failed")
                    canonicalAsOf?.let { append(" — showing result as of $it") }
                    canonicalFailureReason?.let { append(". $it") }
                },
                level = TrustSignalLevel.Warning,
            )
            CanonicalForecastStatus.Failed -> signals += TrustSignal(
                label = "Forecast engine",
                value = "Calculation failed",
                detail = "Canonical calculation failed and no previous result is available; " +
                    "forecast, safe-to-spend and risk are withheld." +
                    (canonicalFailureReason?.let { " $it" } ?: ""),
                level = TrustSignalLevel.Warning,
            )
            CanonicalForecastStatus.Live, CanonicalForecastStatus.Native -> Unit
        }
        // WS4: surface where each risk-model assumption came from. This is the visible half of the
        // provenance contract — the probability reads "Unverified" even when the inputs are calibrated.
        assumptionProvenance.forEach { provenance ->
            signals += TrustSignal(
                label = provenance.label,
                value = when (provenance.origin) {
                    AssumptionOrigin.UserEntered -> "User-entered"
                    AssumptionOrigin.HistoryDerived -> "From history"
                    AssumptionOrigin.Unverified -> "Unverified"
                },
                detail = provenance.detail,
                level = when {
                    provenance.sufficiency == DataSufficiency.InsufficientData -> TrustSignalLevel.Warning
                    provenance.origin == AssumptionOrigin.Unverified -> TrustSignalLevel.Attention
                    else -> TrustSignalLevel.Good
                },
            )
        }
        return signals
    }

    private companion object {
        /** Timestamp format for the retained canonical result's "as of" label. */
        val CANONICAL_TIMESTAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        /**
         * Fail-closed forecast summary used when the canonical engine failed and no prior valid
         * result exists: no low point, no ending balance, and no implied spendable amount.
         */
        val FAIL_CLOSED_FORECAST_SUMMARY = ForecastSummary(
            projectedLowPointCents = 0L,
            lowestBalanceDate = null,
            endingBalanceCents = 0L,
            firstNegativeDate = null,
            safeToSpendCents = null,
        )

        /**
         * Fail-closed Monte Carlo result used when the canonical engine failed and no prior valid
         * result exists. The neutral zeros are deliberate: no native-engine number is presented, and
         * [AppUiState.canonicalForecastStatus] = [CanonicalForecastStatus.Failed] plus the
         * `Forecast engine` trust signal are the authoritative signals that risk is *unavailable*,
         * not zero.
         */
        val FAIL_CLOSED_MONTE_CARLO = MonteCarloResult(
            worst_10_balance_cents = 0L,
            median_balance_cents = 0L,
            best_90_balance_cents = 0L,
            probability_negative_pct = 0.0,
            runs = 0,
            negative_runs = 0,
            most_common_first_negative_date = null,
            negative_window_start = null,
            negative_window_end = null,
            dailyPercentiles = emptyList(),
        )
    }
}

/** In-memory retention of the last known valid canonical result, stamped with its compute time. */
private data class RetainedCanonical(
    val value: CanonicalAdoption,
    val computedAt: LocalDateTime,
)

data class DashboardDerivation(
    val uiState: AppUiState,
    val reconciliationMismatch: Boolean,
    /** Pair(cashLedgerCents, bankBalanceCents) while mismatched. */
    val reconciliationDetails: Pair<Long, Long>?,
)

data class ReportingPackage(
    val incomes: List<IncomeEntity>,
    val payments: List<PaymentEntity>,
    val txns: List<TransactionEntity>,
    /**
     * Money accounts. Their `type` is the single source of truth for cash-vs-card routing and
     * must mirror what the repository saw when it applied the transaction side effects.
     */
    val accounts: List<com.montecarlo.ledger.data.AccountEntity>,
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
    val accounts: List<com.montecarlo.ledger.data.AccountEntity>,
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
