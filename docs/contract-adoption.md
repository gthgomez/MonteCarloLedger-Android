# MC-07 — Android contract adoption (complete)

**Campaign:** MonteCarlo product-wide semantic adoption
**Contract:** 1.0 (draft), pinned at `9fd8f74` (`app/src/test/resources/contract/contract-pin.json`)
**Engine (native Kotlin):** `app/src/main/java/com/montecarlo/ledger/contract/`

This document describes how the Android product adopts the canonical contract engine, what remains
non-normative, and the Contract 2.0 candidates surfaced by the MC-07 audit.

## Adoption is unconditional (C/D)

`FeatureFlags.contractForecastEnabled` **has been removed**. Every headline number —
deterministic forecast, Monte Carlo percentiles, probability, and safe-to-spend — is produced by
the Kotlin contract engine via `ContractScenarioBridge` → `ContractRunner` →
`ContractDashboardMapper`, in both the dashboard (`DashboardDeriver`) and the Glance widget.

There is no legacy fallback for those outputs. A contract failure surfaces through the ViewModel's
error state (`MainViewModel.observeDashboardData().catch`) or the widget's "Unable to refresh"
state — never as a second engine's number, because two financial truths are exactly what MC-07
removes.

## C1 — cash-flow rows are canonical

`AppUiState.forecastRows` is now produced by `ContractDashboardMapper.toBalanceForecastRows`,
which walks the contract's own `ContractEngine.inWindowBaseEvents(scenario)` and sums with
`ContractMoney.checkedAdd`. It is a pure display adapter: it reuses the contract's recurrence
expansion and ordering `(date, sequence, input_index)` (MCD-0002/0003/0021), so the rendered rows
cannot disagree with the canonical forecast.

## C2 — `dailyBudgetCents`: PRODUCT_HEURISTIC

`dailyBudgetCents` is classified **PRODUCT_HEURISTIC**. Contract 1.x defines no daily-pacing
concept.

- Definition: the canonical safe-to-spend (quantile trough, MCD-0008) divided across the days
  until the next paycheck, floored at 0 when safe-to-spend is non-positive.
- It is derived from the canonical safe-to-spend, so it cannot contradict the canonical number it
  is displayed beside. It is guidance, not a financial conclusion.
- It does **not** move into the contract; a daily pacing model would be a Contract 2.0 candidate
  if it is ever product-critical.

## C3 — `DebtPayoffEngine`: CONTRACT_2_CANDIDATE (not integrated into 1.0)

Audited and deliberately **not** forced into Contract 1.0. `DebtPayoffEngine` computes amortization,
minimum payments, and payoff strategies (snowball/avalanche) that require a debt/liability domain
Contract 1.0 does not model (MCD-0012 defers multi-account/credit routing). It remains native and
non-normative; it must not be treated as canonical. Registered as a Contract 2.0 candidate.

## C4 — fan chart: non-normative (kept)

Contract 1.0 exposes no per-day stochastic path percentiles, so the fan chart
(`ui/MonteCarloFanChart.kt`, fed by `MonteCarloEngine.dailyPercentiles`) remains native and
non-normative. It is documented as such, and it cannot contradict a named canonical quantity: all
headline aggregates (trough/ending percentiles, probability, safe-to-spend) come from the contract.
A `dailyPercentiles` extension is registered as a Contract 2.0 candidate.

## C5 — middle-window occurrence suppression: documented, moved to a Contract 2.0 proposal

Suppression of paid / user-moved occurrences is honored only for a suppressed **prefix** of a
payment's in-window occurrences (by advancing the recurrence lower bound). A suppressed occurrence
in the **middle** of the window is not representable in Contract 1.0, which has no per-occurrence
exclusion list.

- **User impact:** a user who moves a mid-window bill still sees the original template occurrence
  projected, in addition to the explicit moved event — a double-count.
- **Minimum required extension (Contract 2.0):** an explicit per-occurrence exclusion/override
  mechanism in the scenario (e.g. `exclude_occurrences: [{recurrence_id, date}]`), or first-class
  user-modified occurrences that suppress their generated counterpart. This changes normative
  representable semantics, so it requires an MCD + fixture + contract version bump and is **not**
  added to Contract 1.0 here.

## Product decisions beyond the contract

1. **Empty ledger.** MCD-0023 makes the engine's answer authoritative; no fabricated 0% risk.
2. **Per-category expense variation** is not representable in 1.0 (MCD-0015); the adopted
   simulation uses the aggregate range.
3. **Reconciliation gating** (`forecastUnlocked`) is product UX, not a contract rule.

## Tests

```
./gradlew :app:testDebugUnitTest --no-daemon
```

- `app/src/test/java/com/montecarlo/ledger/dashboard/ContractAdoptionDashboardTest.kt` —
  headline parity with the contract, canonical rows (C1), canonical daily budget (C2).
- `app/src/test/java/com/montecarlo/ledger/adoption/ContractScenarioBridgeTest.kt` — bridge
  semantics (anchor, overdue exclusion, prefix suppression, moved occurrence).
- `app/src/test/java/com/montecarlo/ledger/contract/*` — conformance over the vendored corpus,
  repin/pin verification, recurrence, semantics, simulation invariants.
