# MC-06b — Android contract adoption

**Campaign:** MonteCarlo semantic foundation
**Contract:** 1.0, pinned at `d2e621c` (vendored in `app/src/test/resources/contract/`)
**Engine (native Kotlin):** `app/src/main/java/com/montecarlo/ledger/contract/`

This document describes how the Android product adopts the canonical contract engine for its
user-visible forecast, Monte Carlo and safe-to-spend numbers, and how to toggle it.

## Feature flag

Persisted setting `contract_forecast_enabled` in the `settings` table (default **true**), read
through `LedgerRepository.contractForecastEnabled` / `getContractForecastEnabled()`.

- When **true**, `DashboardDeriver` and `MonteCarloLedgerGlanceWidget` build a canonical
  `ContractScenario` from repository state, run `ContractRunner`, and source every headline
  number from the resulting `ContractResult`.
- When **false**, the legacy native `ForecastEngine` / `MonteCarloEngine` numbers are used.
- The flag is stored in Room (DB version 17) and survives restarts; an absent row means ON, so
  existing installs adopt the contract engine without a manual opt-in. `DashboardDeriver.derive`
  takes the flag as an explicit parameter and `MainViewModel` supplies it from repository state.

## Bridge: repository state -> `ContractScenario`

`com.montecarlo.ledger.adoption.ContractScenarioBridge` is the single translation point:

| Product row | Contract primitive |
|---|---|
| resolved seed balance (`BalanceSeedResolver`) | `starting_balance_cents` |
| UI `as_of` (supplied by ViewModel/deriver; never a clock) | `as_of` (MCD-0001) |
| `IncomeEntity` | `income` recurrence (`start_date = next_date`, `anchor_day = day_of_month ?: start.day`, `expected_amount_cents`) |
| `PaymentEntity` | `expense` recurrence (`amount = -abs(amount_cents)`) |
| user-moved unpaid `BillOccurrenceEntity` | explicit `expense` event |
| paid / moved occurrence dates | suppressed by advancing the recurrence lower bound |
| `MonteCarloCalibration` | `simulation` block (ppm scaling, aggregate expense variation) |

Adopted rules:

- **MCD-0013** starting balance is explicit; posted transactions are already folded in and are
  not replayed as events.
- **MCD-0014** occurrences dated before `as_of` are not projected. The legacy native timeline
  hoisted overdue bills to `as_of`; the adopted path does not.
- **MCD-0022 / B-07** `start_date` is a lower bound and `anchor_day` sets the day of month; the
  contract engine projects from the original start, so a clamped February cannot move a
  monthly anchor (e.g. Jan 31 -> Feb 28 -> **Mar 31**).
- **MCD-0023** the simulation is defined for any scenario. Surprise generation depends only on the
  horizon and surprise parameters, so an empty ledger still yields a genuine risk distribution.
  The earlier "empty ledger -> drop simulation -> 0% risk" guard was removed because it fabricated
  a number the engine would not report.

## Mapping: `ContractResult` -> dashboard state

`com.montecarlo.ledger.adoption.ContractDashboardMapper` maps onto the existing Compose state
types so the UI keeps working:

| Contract field | Dashboard field |
|---|---|
| `forecast.minimum_balance_cents/date` | `projectedLowPointCents` / `lowestBalanceDateLabel` (deterministic trough) |
| `forecast.ending_balance_cents` | ending balance |
| `forecast.first_negative_date` | `firstNegativeDateLabel` / `projectedTroubleDateLabel` |
| `risk.minimum_balance_p10/p50/p90` | `monteCarlo10th/50th/90thCents` (trough percentiles) |
| `risk.ending_balance_p10/p50/p90` | `worst_10/median/best_90_ending_balance_cents` |
| `risk.negative_balance_probability_ppm` | `probabilityNegativePct` (ppm / 10 000) |
| `risk.safe_to_spend_cents` (signed) | `safeToSpendCents` |

## Native bug fixes (B-05 / B-06 / B-07)

- **B-05** (`MonteCarloEngine.simulateScenarioWithDaily`): an already-negative opening balance
  now sets `firstNegativeDate = today` and counts toward `negative_runs` (MCD-0010).
- **B-06** (`MonteCarloEngine.getMedian`, `MonteCarloCalibrator.percentile`,
  `MonteCarloInsights.percentileOf`): one nearest-rank rule for all percentiles including P50
  (`index = ceil(N*q)-1`, lower middle for even N). No midpoint averaging, no `(N-1)`
  interpolation (MCD-0005).
- **B-07** (`TimelineService`, `LedgerRepository.syncBillOccurrences`, `advanceIncomeDate`):
  when `day_of_month` is null, the anchor is taken from the schedule's start date and passed to
  `RecurrenceMath` on every step so clamped months do not drift the day (MCD-0022).

B-05 follow-up clears the opening-negative omission in the remaining native paths too:

- `ForecastEngine.calculateForecastSummary(balance, events, asOf)` now takes an explicit `asOf`
  (MCD-0001) and reports `firstNegativeDate = asOf` when the opening balance is already negative.
- `ForecastEngine.buildCashFlowWindows` counts the window's opening balance as a low-point
  candidate, so a same-day paycheck no longer hides an overdrawn window start.
- `DebtPayoffEngine.runSimulation` reports the overdraft guard from `today` for an opening-negative
  balance instead of waiting for the first synthetic payment.
- `ForecastSummary` renames the deterministic trough to `projectedLowPointCents`; the
  quantile-based `safeToSpendCents` is a separate, nullable field populated only by the contract
  path (MCD-0008). The native path has no distribution, so it exposes no safe-to-spend.

Non-conformant paths for the adopted outputs are therefore unreachable: the dashboard/widget
run the contract engine, and the remaining native paths (calendar seasoning, cash-flow windows,
debt guard) are fixed.

## Product decisions (beyond the contract)

These are product-level choices, flagged here rather than added to the contract:

1. **Empty ledger.** Previously the product suppressed simulation so a first-run dashboard showed
   0% risk. This was removed: `MCD-0023` makes the engine's answer authoritative (surprises depend
   only on the horizon), and reporting a fake 0% would violate the "contract defines truth" rule.
   A dedicated "not enough information yet" UX for an empty ledger is a candidate future
   presentation task, not a change to engine semantics.
2. **Per-category expense variation is not representable.** Contract 1.0 models a single
   aggregate expense-variation scalar (per-category deferred per MCD-0015), so the adopted
   simulation uses the aggregate range only.
3. **Daily fan chart is non-normative.** Contract 1.0 exposes no per-day path percentiles, so
   the fan chart continues to use the native daily walk (with B-05/B-06 fixed) while all
   headline aggregates come from the contract. `MonteCarloResult.most_common_first_negative_date`
   is mapped to the contract deterministic first-negative date, and the UI label changed from
   "Most likely first negative-balance date" to "Projected first negative-balance date" to avoid
   implying a modal simulation value the contract does not expose.

## Known limitations

- Suppression (paid / user-moved occurrences) is honored for a suppressed **prefix** of a
  payment recurrence's in-window occurrences only. Contract 1.0 has no per-occurrence exclusion
  list, so a suppressed occurrence in the middle of the window is not yet representable.
- The native fallback still labels its deterministic low point as the dashboard's "safe to spend"
  because the product has no native distribution to take a quantile of; only the contract path
  supplies a genuine quantile-based safe-to-spend (MCD-0008).

## Tests

```
./gradlew :app:testDebugUnitTest --no-daemon
```

- `app/src/test/java/com/montecarlo/ledger/adoption/ContractScenarioBridgeTest.kt`
- `app/src/test/java/com/montecarlo/ledger/dashboard/ContractAdoptionDashboardTest.kt`
