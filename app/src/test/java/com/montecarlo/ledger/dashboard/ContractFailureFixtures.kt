package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.DashboardConfig
import com.montecarlo.ledger.data.IncomeEntity
import com.montecarlo.ledger.data.LedgerRepository
import com.montecarlo.ledger.data.PaymentEntity
import com.montecarlo.ledger.data.TransactionEntity
import com.montecarlo.ledger.processing.BalanceSeedResolver
import com.montecarlo.ledger.processing.ForecastEngine
import com.montecarlo.ledger.processing.ForecastSummary
import com.montecarlo.ledger.processing.MonteCarloCalibrator
import com.montecarlo.ledger.processing.MonteCarloEngine
import com.montecarlo.ledger.processing.MonteCarloParams
import com.montecarlo.ledger.processing.MonteCarloResult
import com.montecarlo.ledger.processing.RecurringDetector
import com.montecarlo.ledger.processing.TimelineService
import java.time.LocalDate

/**
 * Shared fixtures for the WS2 "no silent engine fallback" tests.
 *
 * The failing pack forces a *genuine* contract failure — a reconciled balance of `Long.MAX_VALUE`
 * makes the contract's overflow-checked addition throw `MONEY_OVERFLOW` — rather than mocking the
 * contract runner. [nativeForecastSummary] / [nativeMonteCarlo] recompute exactly what the legacy
 * native engines produce for the same inputs, so a test can prove the deriver did NOT silently
 * substitute them.
 */
internal object ContractFailureFixtures {

    val today: LocalDate = LocalDate.of(2026, 1, 1)

    /** A reconciled balance of `Long.MAX_VALUE` overflows the contract's checked money addition. */
    const val OVERFLOWING_BALANCE_CENTS: Long = Long.MAX_VALUE

    fun pack(bankBalanceCents: Long): ReportingPackage {
        val txns = listOf(
            TransactionEntity(
                description = "Starting balance",
                amount_cents = 200_000L,
                date = "2025-12-01",
                type = "income",
                category = "income",
            )
        )
        return ReportingPackage(
            incomes = listOf(
                IncomeEntity(
                    id = 1,
                    name = "Paycheck",
                    amount_cents = 100_000L,
                    frequency = "Monthly",
                    day_of_month = 5,
                    next_date = "2026-01-05",
                )
            ),
            payments = listOf(
                PaymentEntity(
                    id = 1,
                    name = "Rent",
                    amount_cents = 60_000L,
                    frequency = "Monthly",
                    day_of_month = 3,
                    next_date = "2026-01-03",
                )
            ),
            txns = txns,
            accounts = emptyList(),
            balanceState = LedgerRepository.BalanceState(
                bankBalanceCents = bankBalanceCents,
                isReconciled = true,
            ),
            billOccurrences = emptyList(),
            assets = emptyList(),
            goals = emptyList(),
            categoryBudgets = emptyList(),
            debts = emptyList(),
            rules = emptyList(),
            dashboardConfig = DashboardConfig(),
        )
    }

    /** Contract path succeeds: a normal, reconciled seed balance. */
    fun healthyPack(): ReportingPackage = pack(200_000L)

    /** Contract path fails: the seed balance overflows the contract's checked arithmetic. */
    fun failingPack(): ReportingPackage = pack(OVERFLOWING_BALANCE_CENTS)

    fun seedOf(pack: ReportingPackage): Long = BalanceSeedResolver.resolve(
        pack.txns.sumOf { it.amount_cents },
        pack.balanceState.bankBalanceCents,
        pack.balanceState.isReconciled,
    )

    private fun eventsOf(pack: ReportingPackage) = TimelineService.generateTimeline(
        pack.incomes, pack.payments, today, 90, pack.billOccurrences, pack.rules,
    )

    /** The legacy native forecast summary the dashboard would show on silent fallback. */
    fun nativeForecastSummary(pack: ReportingPackage): ForecastSummary =
        ForecastEngine.calculateForecastSummary(seedOf(pack), eventsOf(pack), today)

    /** The headline native safe-to-spend the dashboard would show on silent fallback. */
    fun nativeSafeToSpend(pack: ReportingPackage): Long {
        val summary = nativeForecastSummary(pack)
        return summary.safeToSpendCents ?: summary.projectedLowPointCents
    }

    /** The legacy native Monte Carlo result the dashboard would show on silent fallback. */
    fun nativeMonteCarlo(pack: ReportingPackage): MonteCarloResult {
        val calibration = MonteCarloCalibrator.calibrate(
            transactions = pack.txns,
            today = today,
            recurringPatterns = RecurringDetector.detect(pack.txns).map { it.pattern }.toSet(),
        )
        val params = MonteCarloParams(
            incomeVariationMin = calibration.incomeVariationMin,
            incomeVariationMax = calibration.incomeVariationMax,
            expenseVariationMin = calibration.expenseVariationMin,
            expenseVariationMax = calibration.expenseVariationMax,
            expenseCategoryVariation = calibration.expenseCategoryVariation,
            surpriseProbability = calibration.surpriseProbability,
            surpriseAmountMin = calibration.surpriseAmountMin,
            surpriseAmountMax = calibration.surpriseAmountMax,
            includeDailyPercentiles = true,
        )
        return MonteCarloEngine(params).runSimulation(seedOf(pack), eventsOf(pack), today)
    }
}
