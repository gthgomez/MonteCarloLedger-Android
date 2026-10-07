package com.montecarlo.ledger.adoption

import com.montecarlo.ledger.contract.ContractEngine
import com.montecarlo.ledger.contract.ContractMoney
import com.montecarlo.ledger.contract.ContractResult
import com.montecarlo.ledger.contract.ContractScenario
import com.montecarlo.ledger.processing.BalanceForecastRow
import com.montecarlo.ledger.processing.ForecastSummary
import com.montecarlo.ledger.processing.MonteCarloResult

/**
 * Maps canonical [ContractResult] values onto the existing dashboard state types so the
 * Compose layer keeps working unchanged (MC-06b, AD-2).
 *
 * Field mapping (contract name -> product field):
 *  - `forecast.minimum_balance_cents/date` -> `lowestBalanceCents/Date` (the deterministic trough)
 *  - `forecast.ending_balance_cents`        -> `endingBalanceCents`
 *  - `forecast.first_negative_date`         -> `firstNegativeDate`
 *  - `risk.minimum_balance_p10/p50/p90`     -> `monteCarlo10th/50th/90th` (trough percentiles)
 *  - `risk.ending_balance_p10/p50/p90`      -> `worst_10/median/best_90_ending_balance`
 *  - `risk.negative_balance_probability_ppm`-> `probabilityNegativePct` (ppm / 10_000)
 *  - `risk.safe_to_spend_cents` (signed)    -> `safeToSpendCents`
 *
 * Note the deliberate split mandated by MCD-0008/MCD-0009: safe-to-spend is the quantile-based
 * `minimum_balance_p{q} - reserve`, **not** the deterministic low point, and trough and ending
 * percentiles are distinct families.
 */
object ContractDashboardMapper {

    /**
     * Canonical display rows: the contract's ordered in-window events with running balances
     * (MCD-0002/0003/0021). A display adapter over [ContractEngine.inWindowBaseEvents] and
     * [ContractMoney.checkedAdd] — it reuses the contract's own recurrence and ordering, so the
     * rendered rows cannot disagree with the canonical forecast.
     */
    fun toBalanceForecastRows(scenario: ContractScenario): List<BalanceForecastRow> {
        var balance = scenario.startingBalanceCents
        return ContractEngine.inWindowBaseEvents(scenario).map { event ->
            balance = ContractMoney.checkedAdd(balance, event.amountCents)
            BalanceForecastRow(date = event.date, balanceCents = balance)
        }
    }

    fun toForecastSummary(result: ContractResult): ForecastSummary {
        val forecast = result.forecast
        return ForecastSummary(
            // MCD-0008: safe-to-spend is quantile-based and signed; the low point is surfaced
            // separately as lowestBalanceCents.
            safeToSpendCents = result.risk?.safeToSpendCents ?: forecast.minimumBalanceCents,
            lowestBalanceCents = forecast.minimumBalanceCents,
            lowestBalanceDate = forecast.minimumBalanceDate,
            endingBalanceCents = forecast.endingBalanceCents,
            firstNegativeDate = forecast.firstNegativeDate,
        )
    }

    /**
     * Builds a product [MonteCarloResult] from the contract risk block. [runs] must match the
     * scenario's simulation runs so the reconstructed `negative_runs`/probability are consistent.
     * Contract 1.0 has no per-day path percentiles, so [MonteCarloResult.dailyPercentiles] is
     * empty here; the dashboard may overlay a non-normative fan chart separately.
     */
    fun toMonteCarloResult(result: ContractResult, runs: Int): MonteCarloResult {
        val forecast = result.forecast
        val risk = result.risk
        val ppm = risk?.negativeBalanceProbabilityPpm ?: 0L
        val safeRuns = runs.coerceAtLeast(1)
        val negativeRuns = ((ppm * safeRuns + 500_000L) / 1_000_000L).toInt()
        return MonteCarloResult(
            worst_10_balance_cents = risk?.minimumBalanceP10Cents ?: forecast.minimumBalanceCents,
            median_balance_cents = risk?.minimumBalanceP50Cents ?: forecast.minimumBalanceCents,
            best_90_balance_cents = risk?.minimumBalanceP90Cents ?: forecast.minimumBalanceCents,
            worst_10_ending_balance_cents = risk?.endingBalanceP10Cents ?: forecast.endingBalanceCents,
            median_ending_balance_cents = risk?.endingBalanceP50Cents ?: forecast.endingBalanceCents,
            best_90_ending_balance_cents = risk?.endingBalanceP90Cents ?: forecast.endingBalanceCents,
            probability_negative_pct = ppm / 10_000.0,
            runs = safeRuns,
            negative_runs = negativeRuns,
            // Contract exposes the deterministic first-negative date, not a modal simulated one.
            most_common_first_negative_date = forecast.firstNegativeDate?.toString(),
            negative_window_start = null,
            negative_window_end = null,
            dailyPercentiles = emptyList(),
        )
    }
}
