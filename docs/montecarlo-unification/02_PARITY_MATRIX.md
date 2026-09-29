# Behavioral parity matrix

Pins and evidence IDs:00 and16. These are source-derived observations, not executed conformance results. Target behavior is normative candidate design in04. Equivalent filenames do not establish equivalent calculations. The expanded downloadable packet also includes a machine-readable46-row matrix.

| Dimension | Python current | Android current | Target / stage |
|---|---|---|---|
| Persisted domain | Five-table ledger/settings domain | Eleven Room entity types | DTO contract independent of SQL; C00/P04/A06 |
| Currency representation | int/INTEGER, permissive migration sanitation | Long/BigDecimal helpers, unchecked aggregates | Checked symmetric int64; JSON strings; C00 |
| Money parsing/signs | Decimal helpers; migration float/zero fallbacks; unknown sign-kind weakness | BigDecimal exact narrowing; legacy zero wrapper and aggregate edges | Strict grammar/type/sign/range; P00/P04/A06 |
| Date boundary | Inclusive schedule end despite prose | Timeline end exclusive | Half-open range, injected cutoff; P05/A07 |
| Recurrence grammar | Weekly/biweekly/monthly/once-oriented | Adds semimonthly,2-month,quarterly,annual | Explicit versioned grammar; P05/A07 |
| Semi-monthly | Not equivalent supported domain | Inferred slots from moving dates | Persist1/15 or15/EOM; P05/A07 |
| Month-end/leap | Income cursor can drift; bill day anchor differs | Optional day anchors | Immutable nominal anchor/clamp/recover; P05/A07 |
| Ledger atomicity | Individual insert/cache atomic; whole bill/payday split | Room UOW for several workflows, inconsistent entry paths | Commands+receipts+revision; P01/P04/A06 |
| Reconciliation | Ledger/cache mismatch checks | Observed/global/account state mixed; default can reconcile | Separate observations/posted/available; P04/A01/A06 |
| Accounts | No persisted account domain | Account model with legacy default/global side effects | Per-account routing and unassigned legacy state; P04/A06 |
| Clearing/review | No equivalent | Separate fields; pending already affects current flows | Pending hold once, posting replaces identity; P04/A06 |
| Income sources | Multiple rows, expected next amount | Multiple rows/types; name+date duplicate guard | Source/occurrence identity and one-off overrides; P01/P04/A06 |
| Past-due income | Skips earlier scheduled paydays | Outstanding scheduled paydays can be credited today | Overdue_unconfirmed, no invented available cash; P05/A07 |
| Bill templates | Mutable template-joined amount | Occurrences snapshot amounts | Preserve planned/actual and schedule versions; P04/A06 |
| Occurrence identity | Unique payment/date, local IDs | Original/effective date, weaker nominal uniqueness | Stable nominal occurrence identity; P04/A06 |
| Paid/skipped/moved | Paid flag/link and limited override model | Paid flag includes skipped/null-link state | Explicit open/pending/paid/skipped/legacy_unknown; P04/A06 |
| Past-due bills |30-day lookback, retains past date | Projection clamps to today; refresh policy differs | Known obligations carried once, unknown history qualified; P05/A07 |
| Categories | Free transaction labels | Normalized labels/presets | Stable IDs/aliases, no uncertain merge; P04/A06 |
| Category rules | No persisted equivalent | Exact→keyword→preset; ties input-order-dependent | Explicit priority/stable-ID ties; P09/A11 |
| Budgets | No persisted budget domain | Category/month plans and watchlists | Monitor/reservation distinction, refund/card rules; P09/A11 |
| Assets | No persisted equivalent | Dated assets and action usage | Valuation/liquidity not assumed checking cash; P09/A11 |
| Goals | No persisted equivalent | Goals and proposed funding | Advisory until backed; preserve overfunding; P09/A11 |
| Installment debt | No engine/storage equivalent | Monthly interest/payoff/cash guard | Named approximation, conservation/nonconvergence; P09/A11 |
| Revolving credit | No equivalent | Terms/account linkage, refund/routing edges | One liability owner, transfers not double expense; P04/A06/P09/A11 |
| Deterministic forecast | Reducer exists; opening-negative edge | Reducer/windows exist; unchecked Long sums | Canonical checked account reducer; P05/A07 |
| Cash-flow windows | Limited summary/negative handling | Payday windows and MC date envelopes | Distinguish real intervals from cross-run date envelope; P05/A07/P07/A09 |
| Safe-to-spend | Full-horizon min may be negative despite before-payday wording | Related min/window/plan metrics | Nonnegative headroom, separate shortfall, qualification; P05/A07 |
| MC inputs | Smaller variance/shock configuration | Calibration/category/daily inputs | Typed explicit drivers/units/version; P07/A09 |
| MC algorithm | Forecast reuse but last-event surprise horizon | Duplicate reducer and hardcoded daily horizon edges | Same reducer; requested full horizon, even empty; P07/A09 |
| RNG/replay | Python Random plus wall clock | Kotlin Random; different sequences | Version-scoped seed replay; shared keyed tapes; P07/A09 |
| Percentiles | Headline ending balance | Headline minimum balance, separate endings | Named minimum/end/daily; nearest-rank tails; P07/A09 |
| Historical calibration | No equivalent | Monthly CV/heuristics, incomplete coverage/units | Complete covered periods and transparent fallback; P08/A10 |
| Category variance | No equivalent per-category history model | range..range fixed uplift; test codifies lower bound | Narrow symmetric hotfix; later coverage bootstrap; A02/P08/A10 |
| Daily risk bands | No equivalent rich daily result | Bands with hardcoded day count/phase ambiguity | One close per requested day + opening; P07/A09 |
| Pacing | Basic UI planning |7-day expense velocity and thresholds | Coverage/account-aware daily budget, no float currency; P08/A10 |
| Overdraft actions | No comparable action engine | Heuristic riskReductionPct without rerun | No numeric benefit without counterfactual receipt; A03/P09/A11 |
| Backup portability | No full matching Android interchange | JSON6/Long numbers/type fallbacks | Strict versioned string-money schema, preflight; P06/A00/A08 |
| Encrypted backup | No equivalent surface verified | AES-GCM, optional extra HMAC, PIN separation | Preserve readers; reviewed envelope metadata; P06/A08 |
| CSV/import | No equivalent rich mapped workflow verified | Column preview/cap; account/dedup caveats | Account/provider/batch identity, ambiguity review; P06/A08 |
| Local security | Plain SQLite, package-relative path, local unauth API | Plain Room, PIN, encrypted export, no INTERNET in main manifest | Distinct controls, OS paths, protected local adapter; P02/P11/A00 |
| Native UX/platform | Terminal workflows | Compose, reminders, widgets, SAF, app lock | Remain Android-specific; no wholesale port |
| CLI/API | Entry point/menu and one local GET | No Python console/service equivalent | Stable CLI JSON + stateless adapter; P10/P11 |
| Tests | Eight files; meaningful but incomplete boundary properties | Broader unit/instrumented suites; flawed semantic assertion exists | Shared exact/statistical/migration evidence; P03/A05 |
| Migration coverage | v1→10 paths with bootstrap/sanitation risks |1→16 explicit chain, newer matrix | Non-lossy preflight + instrumented matrix; P00/A04 |
| CI/release | Ubuntu editable3.10/3.11; no installed-wheel proof | Unit/debug assembly; no emulator lane | Artifact/OS/device qualification; A04/P12 |
| Documentation | End-date/horizon/storage drift | Old status/release/privacy placeholders/support issues | Revision-generated evidence; P13/A12 |

## Port, preserve, retire, postpone

Port **corrected behavior after licensing approval**, not source wholesale: richer recurrence, occurrence semantics, category rules/budgets, debt, coverage-aware calibration, daily bands, pacing and backed counterfactuals. Preserve Android Compose/Glance/SAF/reminders/app-lock integration as native platform features. Preserve CLI JSON/batch/local HTTP/diagnostics/distribution as portable-specific adapters.

Share contracts, identity/sign/date rules, fixture manifest, calculation/result semantics and interchange. Retire duplicate reducers, implicit clocks, global caches as independent truth, heuristic numerical benefits and permissive invalid-to-zero parsing only after replacement/compatibility evidence. Retain supported historical migrations and backup readers. Postpone cloud persistence/sync, payment execution, FX, issuer-exact statements and language extraction.
