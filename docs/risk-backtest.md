# Risk-model backtest and provenance

This note describes the out-of-sample backtest for the Monte Carlo risk model and the provenance the
dashboard carries. It records no measured values: the suite is run by the integrator, and nothing here
claims real-world forecast accuracy.

## Two quantities, two runs

`MonteCarloCalibrator` calibrates **monthly** income/expense variation. The dashboard displays the
**within-month minimum** (trough) band and a path probability:

| Surface | Quantity | Source |
|---|---|---|
| Simulation probability | chance the running balance goes below zero **at any point** | `MonteCarloEngine.runSimulation` (`probability_negative_pct`) |
| Simulation band | percentile of each run's **minimum (trough)** balance | `minimum_balance_p10/p50/p90` → `monteCarlo10th/50th/90th` |
| Calibrator unit | **monthly** income and expense totals | `MonteCarloCalibrator.calibrate` |

`RiskBacktest` runs the model twice per held-out month from one calibration. Both runs disable the
calibrator's surprise layer:

- **Month-end run** — income on day 1, one expense on the last day, zero opening, no surprises. Its
  ending quantiles are the month's net quantiles under the calibrated monthly-total variation. This
  isolates that variation from any timing approximation, so a coverage miss here is attributable to
  calibration.
- **Path run** — income on the training-window payday and four weekly expense buckets, starting from
  the ledger balance before the month, also without surprises. This is the only layout that can
  produce a non-degenerate trough.

The two runs share a calibration but not an outcome distribution. The path run splits the expense
budget across buckets and each bucket receives its own variation draw, so summing independent bucket
draws damps variance (`Σbᵢ² < (Σbᵢ)²`). Its ending quantiles are therefore **not** exactly the
month-end quantiles minus the opening; the runs are separate checks, not one run reused.

Surprises are suppressed in both runs because the baseline expense is the mean of realized monthly
totals, which already includes irregular charges. Injecting the calibrated surprise layer would count
that spend twice, and this harness has no verified recurring/irregular decomposition to subtract the
irregular part first. The path check therefore covers timing plus monthly-total variance, not the
product's surprise-inclusive path.

## Harness

1. Parse rows through `LedgerDate.parseIsoOrNull`, normalize the stored date, and exclude rows dated
   in the as-of month or later. Unparseable rows and excluded rows are counted. `today` is required
   and is also passed to the calibrator, so no result depends on the wall clock.
2. Split distinct months chronologically into training and held-out windows (default 50/50, minimum
   3 months total).
3. Calibrate on training rows only; recurring patterns are detected from those rows, so no holdout
   description, date, amount, or pattern enters the calibration. Only the variation ranges and
   per-category ranges are forwarded; the calibrated surprise fields are intentionally ignored.
4. Derive the intra-month timing baseline from training rows only: the median payday and four weekly
   expense shares, scaled to the month-end expense budget so both runs forecast the same total.
5. Score the holdout month: realized net (income minus expense; adjustment rows excluded), and the
   realized trough — `min(opening, running balance after each row)`, ordered by date with money-in
   before money-out, summed by recorded sign.

`MonteCarloEngine.generateScenarioTimeline` reads `baseTimeline.last()` **before** sorting. The path
list is therefore built chronologically and always ends with a month-end expense; when the last
bucket is empty that event carries zero cents and has no path effect. Surprises are disabled, so the
engine adds no unexpected expenses. Both runs pass the month's first day as the engine's `today`, so
each scored run covers that month — not the engine's wall-clock default and not the product's 90-day
horizon.

### Opening balance

Each scored month starts from `CashForecastTiming.opening` (no confirmed bank balance), the product's
own rule. Every earlier period — including earlier scored months — may feed it: that balance is known
at the forecast cutoff, and nothing at or after the cutoff enters. The earliest month in the ledger
starts from zero, so a partial ledger makes early troughs look deeper; treat the first windows as a
warm-up.

## Metrics

| Metric | Definition | Reading |
|---|---|---|
| `empiricalCoverage` | fraction of held-out months whose **month-end net** fell in the nominal 80% P10-P90 band | ≈ 0.80; checks monthly variance |
| `coverageError` | `abs(empiricalCoverage - 0.80)` | 0 is perfect |
| `troughCoverage` | fraction whose **realized within-month minimum** fell in the model's 80% trough band | ≈ 0.80; first evidence about the displayed band |
| `dipBrier` | mean squared error of `predictedDipProbability` vs `actualDippedNegative` | same event on both sides |
| `dipBaseRateBrier` | Brier of the constant **training** dip rate | reference a skilled model must beat |
| `dipSkillScore` | `1 - dipBrier / dipBaseRateBrier`; forced to `0.0` only when `dipBaseRateBrier` is exactly 0 | positive = beats training climatology; `0.0` can also mean the model merely matches a nonzero baseline |
| `sampleSize` | scored holdout months | at the 3-month floor with a 50/50 split this is **1** |
| `unparseableDateRows`, `excludedIncompleteMonthRows` | rows dropped before scoring | missing-data visibility |

The previous revision estimated the base rate from the holdout and scored the same holdout against it.
That in-sample baseline is gone; `trainingDipRate` is the only baseline.

`dipBaseRateBrier` is exactly 0 only when the constant training rate already matches every scored
outcome: all held-out months dip and `trainingDipRate == 1`, or none dip and `trainingDipRate == 0`.
If the training rate is strictly between 0 and 1, or the holdout disagrees with an extreme rate, the
baseline Brier is positive and the formula is reported unchanged. A reported `0.0` can therefore mean
either an undefined skill (zero baseline) or a model that merely matched a nonzero baseline; a
negative score means the model did worse than the constant.

## Independent oracles

`RiskBacktestTest` pins the behaviour with hand-computed ledgers and injected zero-variation
calibrations, so no assertion depends on the RNG:

- **Aligned payday** — income on the 20th, rent on the 5th. The holdout month opens at +20 000, dips
  to −20 000 and ends at +30 000; both bands contain the actual and the model predicts the dip with
  probability 1.0.
- **Shifted payday (sign-changing)** — training paydays are on the 1st, so training never dips. The
  holdout month moves its payday to the 20th: it dips to −50 000 and ends at 0, its net. Month-end
  coverage is 1.0, trough coverage 0.0, dip Brier 1.0, and `dipBaseRateBrier` is 1.0 — only true with a
  training-only baseline.
- **Surprise suppression** — a synthetic ledger whose totals already include an irregular charge is
  run with a calibration that would inject a guaranteed 500 000-cent surprise at every check. The
  month-end band stays at the realized net and the path dip probability stays at 0.0. Forwarding the
  surprise fields would move the band to −950 000 and turn the non-dipping path negative.
- **Brier degeneracy** — a report built directly from observations scores a zero-baseline report as
  `0.0`, and a nonzero baseline (training rate 0, holdout dip) with a 0.5 forecast as `0.75`; the
  formula is not suppressed for a positive baseline.
- **No lookahead** — doubling the last holdout month's amounts changes its realized net and leaves the
  training calibration, its opening, both bands and its dip probability identical.
- **No wall clock** — a training window in the future relative to the machine clock still reports
  `monthsCovered == trainingMonths`.

## Limits

- **No production 90-day calibration.** The harness scores one realized month at a time with a
  one-month horizon and no surprise injection. It does not exercise the product's 90-day
  scheduled-event path, its 14-day surprise cadence, or the engine's wall-clock defaults, so nothing
  here validates the headline 90-day probability.
- **Very small samples.** A personal ledger has few months. At `MIN_MONTHS = 3` with the default split
  there is one holdout month, so coverage is 0.0 or 1.0. Daily minima are not independent, so the
  sample is months, never days.
- **Timing is a coarse training baseline, not a schedule.** The shipped forecast uses a scheduled
  bill/payday/debt timeline; a trough miss here is evidence about the model's assumptions, not a
  measurement of that timeline.
- **Path variance is damped.** The aggregate monthly expense range is applied to each weekly bucket
  independently (`Σbᵢ² < (Σbᵢ)²`), and bucket events carry no category, so per-category ranges are
  unused. Month-end coverage remains the authoritative variance check.
- **No account routing.** Every row is treated as a bank-cash event, so card charges enter the path on
  their transaction date, not the card payment date.
- **Net excludes adjustment rows** while the trough path sums every row by sign; the two numbers
  answer different questions.
- **No verified recurring/irregular decomposition.** Baseline income and expense are whole monthly
  means. The harness cannot separate recurring from irregular spend with verification, so it disables
  surprises instead of adding them to totals that already contain them. The path check is not a test
  of the surprise layer.
- **Exact Brier condition.** The skill score is forced to 0.0 only when `dipBaseRateBrier` is exactly
  0. A training window with no dips (or all dips) does not by itself make the baseline degenerate; a
  disagreeing holdout still produces a positive baseline Brier. A `0.0` score can also mean the model
  matched a nonzero baseline.
- The fixed-law synthetic fixture checks five covered months out of six against a hand-derived
  band. The separate mixed-expense fixture measured three of six with the revised harness.
  Neither small sample establishes coverage for a real household.

## Provenance

`DashboardDeriver` labels the displayed probability **Unverified** in every case (default assumptions
are additionally `InsufficientData`); the card renders "Unverified — …" and the help dialog defines
the 90-day, any-point-below-zero meaning. This backtest does not change that label: a month-end
coverage check and a reported trough coverage are not a validation of the 90-day headline.
