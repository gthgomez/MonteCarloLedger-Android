# Risk-model backtest and provenance (WS4)

This note documents the out-of-sample backtest for the Monte Carlo risk model and the honest
provenance the dashboard now carries, so the numbers on screen are not read as more trustworthy
than they are.

## Why

`MonteCarloCalibrator` derives income/expense variation from monthly ledger totals and surprise
statistics from irregular charges, and `MonteCarloEngine` runs ~500 scenarios. The dashboard shows
a **"Risk of running low"** percentage and a P10-P90 balance band. Before WS4 nothing demonstrated
that the displayed probability matched real-world frequency, and the coarse `monteCarloBasisLabel`
only distinguished *calibrated* from *default inputs* — never the probability itself.

## Harness

`app/src/main/java/com/montecarlo/ledger/processing/RiskBacktest.kt` (invoked from
`RiskBacktestTest`). Given a ledger it:

1. Splits the distinct calendar months into a **training** window and a **held-out** window
   (default 50/50, minimum 3 months total).
2. Calibrates `MonteCarloCalibration` on the training window **only** — recurring patterns are
   detected from training rows too, so there is no lookahead.
3. Replays `MonteCarloEngine` once per held-out month (income = training mean, expense = training
   mean, seeded per month) and compares the model's forecast to what actually happened.

Because it is a pure function of the ledger and the seed, the report is byte-for-byte reproducible.

## Metrics

| Metric | Definition | Reading |
|---|---|---|
| `empiricalCoverage` | Fraction of held-out months whose actual net fell inside the model's nominal **80%** P10-P90 band | Should be ≈ `nominalCoverage` (0.80) |
| `coverageError` | `abs(empiricalCoverage - 0.80)` | 0 is perfect; used as the pass tolerance |
| `brierScore` | Mean squared error of the predicted negative-net probability vs the realised outcome (0 good, 0.25 = coin flip) | Lower is better |
| `baseRateBrier` / `brierSkillScore` | Brier of the constant base-rate forecast and `1 - model/base` | Skill > 0 beats always guessing the historic negative rate |

The forecast unit is the **monthly net cash flow** (income minus expense), because that is the unit
the calibrator actually calibrates. From a zero starting balance the model's ending-balance
quantiles are its predicted net; the ending quantiles are used (not the running-minimum quantiles,
which collapse to `[0, 0]` for a net-positive zero-start month).

### Observed (deterministic, 12-month synthetic stationary ledger, seed 42, 500 runs)

```
holdout=6  coverage=0.833  nominal=0.80  coverageError=0.033
brier=0.0043  baseRateBrier=0.0000  skill=0.000
```

A deliberately **misspecified** model (zero variance, no surprises) covers 0.0 of the same months,
so the harness detects miscalibration rather than rubber-stamping it.

## Asserted properties (`RiskBacktestTest`)

- `backtest_wellSpecifiedHistory_coversTheNominalBand` — non-degenerate, ordered P10-P90 band;
  `empiricalCoverage >= 0.5` and `coverageError <= 0.25`.
- `backtest_isDeterministicForAFixedSeed` — identical report for the same seed.
- `backtest_detectsMisspecification_flatModelUndercovers` — a flat model under-covers relative to
  the calibrated model and below 0.5.
- `backtest_insufficientHistory_reportsNothingToScore` — < 3 months yields an empty report.

## Limits

- It evaluates a single forecast unit at a horizon the model was **not** designed to headline (90
  days). It is a necessary check, not a sufficient one.
- The holdout is small by construction (a personal ledger has few months). Six months cannot
  resolve a coverage difference smaller than ~1/6 ≈ 0.17, so the tolerances are deliberately loose.
- It validates the *calibration of the model's own assumptions*, not whether those assumptions are
  the right model of a person's finances.
- When no holdout month's actual net is negative (`baseRateBrier = 0`) the Brier skill score is
  undefined and reported as `0.0`; that is a degenerate comparison, not a failure.

## Provenance (honest labelling)

`DashboardDeriver` now builds a typed `List<AssumptionProvenance>` on `AppUiState` and mirrors it
into the trust layer:

- **UserEntered** — forecast seed anchored to a confirmed bank balance.
- **HistoryDerived** — variation ranges calibrated from enough logged history.
- **Unverified** — default assumptions (also flagged `DataSufficiency.InsufficientData`) **and,
  always, the displayed overdraft probability**, which has not been validated out-of-sample.

`AppUiState.riskModelInsufficientData` is the explicit INSUFFICIENT-DATA state; the Monte Carlo card
renders "Unverified — …" under the probability, and the trust layer shows a per-assumption row.
