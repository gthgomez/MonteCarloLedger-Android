package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.processing.BalanceForecastRow
import com.montecarlo.ledger.processing.ForecastSummary
import com.montecarlo.ledger.processing.MonteCarloResult
import java.time.LocalDateTime

/**
 * Canonical, contract-derived headline results mapped onto the dashboard's existing state types.
 *
 * These are the only values allowed to feed the headline financial conclusions (safe-to-spend,
 * forecast summary, Monte Carlo risk) while `contractForecastEnabled` is true.
 */
internal data class CanonicalAdoption(
    val forecastSummary: ForecastSummary,
    val monteCarloResult: MonteCarloResult,
    val forecastRows: List<BalanceForecastRow>,
)

/**
 * Explicit outcome of running the canonical contract calculation (WS2).
 *
 * Replaces the previous `runCatching { ... }.getOrNull()`, which collapsed every contract failure
 * into `null` and let the caller silently substitute the legacy native engines. The failure branch
 * always carries a human-readable [Failure.reason] and the [Failure.at] timestamp so the condition
 * can be surfaced in the UI instead of hidden.
 */
internal sealed interface ContractCalculationResult {

    data class Success(val value: CanonicalAdoption) : ContractCalculationResult

    data class Failure(
        val reason: String,
        val at: LocalDateTime,
    ) : ContractCalculationResult
}
