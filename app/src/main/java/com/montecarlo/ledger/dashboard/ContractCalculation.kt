package com.montecarlo.ledger.dashboard

import com.montecarlo.ledger.processing.BalanceForecastRow
import com.montecarlo.ledger.processing.ForecastSummary
import com.montecarlo.ledger.processing.MonteCarloResult
import kotlinx.coroutines.CancellationException
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
internal sealed interface ContractCalculationResult<out T> {

    data class Success<T>(val value: T) : ContractCalculationResult<T>

    data class Failure(
        val reason: String,
        val at: LocalDateTime,
    ) : ContractCalculationResult<Nothing>
}

/**
 * Runs the canonical contract calculation and turns a genuine failure into an explicit
 * [ContractCalculationResult.Failure] carrying its reason and timestamp.
 *
 * Both [contract] and the home-screen widget route their canonical calculation through this helper,
 * so neither surface can silently collapse a failure into `null` and substitute native-engine
 * numbers (WS2/F1). Coroutine cancellation is deliberately re-thrown — never mistaken for a contract
 * failure — and only [Exception] is caught, so genuine fatal errors (`OutOfMemoryError`,
 * `StackOverflowError`) propagate instead of being reported as a benign "calculation failed"
 * (WS2/F4).
 */
internal inline fun <T> canonicalCalculation(block: () -> T): ContractCalculationResult<T> =
    try {
        ContractCalculationResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Exception) {
        ContractCalculationResult.Failure(
            reason = failure.message ?: failure::class.java.simpleName,
            at = LocalDateTime.now(),
        )
    }
