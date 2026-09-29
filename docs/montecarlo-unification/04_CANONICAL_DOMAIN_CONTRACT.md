# Canonical contract v1 — target semantics

This is a proposed normative contract, not a description of either current implementation. It must be adopted with a licensed fixture manifest and capability profile. JSON schema sketches supplement, not replace, these semantic rules.

## Money

USD-only v1, exponent2. Authoritative persisted/core money is integer cents in symmetric range -9223372036854775807 through9223372036854775807; exclude Long.MIN_VALUE so negation/absolute value can be checked. Python enforces the same limits. Use arbitrary-width intermediate arithmetic and checked narrowing for sums, differences, rates, medians, imports and simulation aggregates. Overflow is MONEY_OVERFLOW, never wrap, clamp or zero.

All JSON/MCP/backup money is a canonical decimal string: `0|-?[1-9][0-9]*`. Reject negative zero, plus signs, whitespace, exponent/fractional notation, numeric JSON money, booleans and required nulls. Bound input length. Rates use integer basis points; probabilities use numerator/denominator or count/runs. Nonfinancial chart geometry/statistical diagnostics may use float but cannot feed currency calculations.

Decimal-dollar CLI input has at most two fractional digits after explicit locale interpretation. Additional precision requires a named rounding preview. Percentage/interest scales the entire amount once using rational HALF_UP (ties away from zero). Median uses floor of an exact midpoint, not that rule.

## Dates and recurrence

Financial dates are strict ISO calendar dates; timezone is explicit IANA. Core gets injected as_of, horizon and snapshot revision, never now/today. Audit instants are UTC RFC3339 metadata. Opening state describes the snapshot cutoff, not an invented midnight balance. Already posted/received linked events are not replayed later that same day.

All horizons are [start,end_exclusive). N days produce N daily-close points plus a separate opening point. Invalid dates/schedules fail rather than silently disappear. Human relative dates are resolved visibly by adapters before mutation.

A recurrence has stable ID/version, immutable anchor, interval, nominal day/slot, start and optional exclusive end. V1: once, weekly7days, biweekly14days, monthly, every2months, quarterly, yearly, and explicit semimonthly1/15 or15/EOM. Monthly clamps to valid day but preserves anchor: Jan31→Feb28→Mar31. Annual Feb29 returns to Feb29 in leap years after nonleap clamping. Semi-monthly slots are never inferred anew from a moving cursor. Arbitrary slots/holiday calendars are deferred.

Occurrence identity is schedule ID/version + nominal date + slot. Rescheduled effective date is separate. Editing a schedule creates an effective-dated version and supersedes only eligible future open occurrences; paid/skipped history survives.

## Identity and command receipts

New entity IDs are UUID strings. Preserve local integer primary keys during migration, add portable IDs and mappings. Persist a ledger UUID namespace; migrate UUIDv5(namespace, entity-type + ':' + legacy-id). Backup copies preserve IDs/namespace; new independent ledgers get a new namespace. Merge/import mode is explicit.

Each mutation has operation_id, expected ledger revision and relevant entity revision. Database uniqueness enforces receipts. Same key+same input returns original result; changed payload gives IDEMPOTENCY_CONFLICT. Receipt, posting, links, cursor/cache effects and revision commit in one UOW. Names/descriptions or amount/date coincidence never identify a financial operation.

## Accounts, transactions and credit

Accounts have stable identity, kind, currency, active status and inclusion policy. Cash signed balance is ledger net value. Credit net value is negative when owed; a refund may produce positive credit without increasing checking cash. Credit limits are not assets. Debt terms reference one liability account rather than adding a second authoritative balance.

Transactions have account, signed cents, kind, effective date, independent clearing and review states, provenance/import identity and optional transfer/obligation/reversal links. Income/refund positive; expense/fee/interest negative; adjustment nonzero with reason; transfer legs opposite and equal. Opening entries are explicitly classified, not income. Unknown types fail. A credit purchase is an expense on the credit account; principal repayment is a cash-negative/credit-positive transfer. Only interest/fees add another expense. Net worth includes each underlying balance once.

Transfers are atomic linked pairs. Scope does not pool savings into checking by default. Asset liquidation or moving funds requires a dated settlement/transfer scenario; neither is assumed instantly available.

Clearing: pending, posted, voided. Review: unreviewed, reviewed, flagged; review never changes money. Pending outflows reserve once; pending inflows do not increase spendable cash until receipt/availability is established. Pending→posted preserves movement identity, releases hold and posts delta atomically. A matched pending bill suppresses its future debit. Voiding/expiring pending payment reopens the obligation unless independent settlement exists.

## Reconciliation

Keep ledger posted balance, pending reservations and dated observed posted/available bank balance separate. Default-account selection is not an observation and cannot reconcile. Compare available observations against pending-aware balances, not blind transaction sum. Corrections are explicit audited transaction repairs or confirmed adjustments with reason/provenance, never invented income. Receipt links observation, revision and correction. Unknown opening/history/account assignment yields unresolved qualification. Read operations do not repair data.

## Income and bills

Bill templates carry positive planned amount and settlement account. Occurrences snapshot planned amount, nominal/effective dates and schedule identity. States: open, payment_pending, paid, skipped, legacy_settled_unknown. Paid requires posted settlement; payment_pending requires pending link; skipped has no cash movement. Legacy paid/null is ambiguous, not proof of skipping or license to create an expense. Archiving templates preserves settlement history.

One full settlement relationship per occurrence in v1. Partial settlement is unsupported and rejected before insert. A deliberately accepted full-settlement variance preserves planned and actual amounts; the initial safety patch retains current exact-amount policy but makes failure atomic. Actual date is confirmed separately from due date.

Income has source/occurrence IDs, standard amount, typed recurrence, and occurrence-specific amount/date overrides. Zero simulated income denotes missed paycheck, not a zero persisted receipt. Receiving and cursor advancement are atomic. Same-named sources remain distinct. Overdue unconfirmed income is not credited today; require a revised expected availability or actual receipt. Known unpaid overdue bills carry into today once with nominal date retained, not discarded by a30-day cutoff. Unknown historical schedule coverage is a gap, not permission to invent unbounded arrears.

## Deterministic forecast and spend capacity

Build a validated immutable snapshot, exclude already represented settlements/holds, expand canonical occurrences and order events deterministically. Known settlement timing can establish phase; otherwise v1 conservatively applies same-day debits before credits with stable identity tie breaks. This deliberately differs from the existing income-first paths. Report intraday caveat explicitly.

Opening negativity counts, including no-event horizons. Reduce checked signed cents per account. Report opening, event states, daily close, minimum and ending balance separately. Given reserve R, modeled headroom=max(0,min_balance-R); shortfall=max(0,R-min_balance). Scope/horizon/reserve/pending policy are mandatory. No negative amount is presented as safely spendable. Unreconciled/incomplete data suppresses a definitive safe-to-spend amount; modeled headroom may still be shown with qualification=false. Missing income/history is not silently zero certainty.

Daily safe pacing is floor(nonnegative qualified headroom / explicitly chosen positive remaining days), with separate observed spending coverage. Contiguous negative windows contain phased boundaries, minimum, first crossing and contributing obligation IDs. Payday partitions are a separate concept. Do not convert earliest/latest first-negative dates across different simulation runs into one actual cash-flow interval.

Counterfactual numerical benefits require re-running the same model/reducer on baseline/proposal. A heuristic can propose a candidate but must omit an uncomputed percentage reduction.

## Categories, budgets, assets and goals

Category identity is stable; ASCII canonical keys and NFC display labels. Legacy aliases are explicit; uncertain Unicode/case collisions are not silently merged. V1 matching uses NFC+ASCII casefold, exact→keyword→preset→fallback, descending priority then stable rule ID. Empty keywords cannot match everything.

Budgets specify monitor-only versus reservation, [start,end), category/account scope. Refunds reduce spend; credit purchases count once; principal transfers do not count again. Planned expenses/reservations cannot be deducted twice. Assets are dated valuations with liquidity/settlement metadata, not available cash. Goal funding is advisory unless backed by a real reservation/transfer; preserve overfunded amounts without clipping.

## Debt

Named approximation `monthly_simple_v1`: interest=HALF_UP(balance*APR_bps/120000), then minimum and optional extra capped by amount owed. Installment minimum is fixed; revolving minimum=max(floor, HALF_UP(post-interest balance*minimum_bps/10000)), capped. Future issuer-specific interest/grace rules require a new model.

Opening+interest+fees+purchases-payments=closing each period. Negative amortization can increase principal. Unknown APR is not zero. If max horizon is reached, payoff_date=null with remaining balance/reason, not a false payoff date. Cash guard includes normal minimums even when extra=0. Dedupe debt-payment bills by obligation identity, never amount/date.

## Simulation and results

Build deterministic baseline first; perturb typed inputs; reuse the same reducer. Horizon, not last base event, bounds shocks. Empty schedules still admit explicit shocks and opening-negative risk. Invalid runs/horizons fail, not zero-filled success. Charts/batching cannot change outcomes.

Manual v1: discrete integer-bps income variation by occurrence; category/month expense variation; optional integer-day income delay; optional daily Bernoulli shock with rational probability and bounded cents. Defaults have zero uncertainty until explained assumptions are selected. Do not silently convert legacy14-day shock rates into daily rates. Shared category/month drivers and independence choices are recorded.

Native seed replay is exact only within implementation/RNG/model version. Cross-language exact tests use keyed draw tapes by run, driver and stable target. Scenario comparisons use common keyed draws so inserting a purchase does not shift unrelated payroll randomness. A portable PRNG is optional later; sampling transforms also need standardization before identical-seed cross-language claims.

Expose minimum-balance, ending-balance and daily-close quantiles separately. p10/p90 use nearest rank sorted[ceil(p*N)-1]. Median is middle or floor((lower+upper)/2) using wide arithmetic. Probabilities are counts/runs; opening negativity contributes. Pointwise daily bands are not a whole-path80% guarantee.

## Calibration, scenarios and interchange

Calibration follows manual-model conformance. Coverage distinguishes missing history from covered zero-spend months. Use complete covered periods before as_of, exclude future/partial intervals, and apply account/transfer/bill inclusion rules. Category empirical month bootstrap uses HALF_UP(plan*sampled_history*N/sum_history); distribute monthly cents by quotient/remainder. Three complete months are minimum admission, not a confidence guarantee. All-zero positive history preserves planned baseline with insufficient-positive-history warning. Income residual/delay fitting needs matched occurrences; fewer than six yields insufficient evidence. Report exclusions/counts/fallbacks/model; no double-counting irregular spend as both bootstrap and shock. Correlation/outlier modeling is deferred.

Scenarios are immutable overlays: purchase, income occurrence override, bill move, transfer, reserve change, debt extra payment. Reject missing targets/conflicts/wrong accounts; comparison shares snapshot, horizon, policy and random drivers. Return exact computed deltas and changed assumptions, not an LLM estimate.

Storage, calculation contract/model and backup versions are independent. Export exact strings, stable IDs, observations/coverage/receipts/provenance and links. Validate complete input shape and references before restore. Derived charts are never financial truth. Unknown material versions/extensions fail instead of lossy import. Errors include INVALID_SCHEMA, INVALID_REFERENCE, INVALID_MONEY, INVALID_SIGN, MONEY_OVERFLOW, INVALID_DATE, UNSUPPORTED_RECURRENCE, RECONCILIATION_REQUIRED, AMBIGUOUS_LEGACY_DATA, IDEMPOTENCY_CONFLICT, REVISION_CONFLICT, UNSUPPORTED_SCHEMA and INSUFFICIENT_HISTORY.
