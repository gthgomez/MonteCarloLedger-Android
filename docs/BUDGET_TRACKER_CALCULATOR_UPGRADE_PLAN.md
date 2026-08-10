# Budget Tracker & Calculator Upgrade Plan

**Project:** MonteCarloLedger (`C:\Workspace\Project_Android\MonteCarloLedger`)
**Goal:** Make the budget tracker simpler to use, and guarantee that **income, expenses, and purchases** calculate correctly.
**Status:** Planning doc (no code changes yet).
**Date:** 2026-07-11

---

## 1. How To Read This Plan

This plan is the implementation guide for the two requests. It is intentionally grounded in the
current source (read directly) and cross-references existing docs so we do not duplicate them:

- `docs/CODE_AUDIT_AND_ROADMAP.md` — the full correctness audit (findings A1–A5, B1–B5, C1–C6, D1–D7).
- `docs/budgeting-roadmap.md` — the competitor-driven UX roadmap (P0/P1 simplification items).
- `docs/fintech-functionality-audit-2026-04-24.md` — product gap analysis ("no formal budget system").
- `QA_CHECKLIST.md` — the ship gate, including the hard rule: **no floating-point currency in any file**.
- `CLAUDE.md` — invariant: *"All calculations must use exact precision (integer cents / Decimal). Do not use floating-point math for currency."*

Where this plan repeats a finding, it is condensed to an action item with a file target.

---

## 2. Scope — What "Budget Tracker" and "Calculator" Mean Here

The app has **three money entities** and one derived balance. The plan maps the user's words to code:

| User term | Code entity | Entry surface | How it becomes ledger state |
|-----------|-------------|---------------|-----------------------------|
| **Income** | `IncomeEntity` (template) + income `TransactionEntity` | `AddIncomeScreen` / `EditIncomeScreen` | A payday is realized via `processPayday()` which inserts an income `TransactionEntity` |
| **Expenses** | `TransactionEntity` (`type = "expense"`) | `AddTransactionScreen` / `EditTransactionScreen` | Inserted directly as a transaction |
| **Purchases (bills)** | `PaymentEntity` + `BillOccurrenceEntity` | `AddPaymentScreen` / `EditPaymentScreen` / `PaymentListScreen` | Paying an occurrence via `payBillOccurrence()` inserts an expense `TransactionEntity` |

**The single source of truth for the running balance is `TransactionEntity`.**
`MainViewModel.observeDashboardData()` computes:

```kotlin
val ledgerBalanceCents = pack.txns.sumOf { it.amount_cents }   // ALL transactions, any date
```

So income, expense, and paid-bill amounts are all correct **only if**:
1. Every money movement ends up as exactly one transaction (no double-count between bills and expenses).
2. No money field silently overflows.
3. No floating-point math corrupts a cent value.
4. The reconciled bank balance cannot drift away from `sum(txns)` without a visible prompt.

That is the "calculator correctness" contract this plan enforces.

---

## 3. Current State (verified from source)

**Strengths (keep):**
- `MoneyUtils.dollarsToCents` is exact (`BigDecimal` + `HALF_UP`) — passes the currency-precision gate.
- AES-GCM backup construction is sound (audit C6 PASS).
- Reconciliation mismatch detection exists (`checkBalanceConsistency()`).
- Sign validation exists in `DomainRules` (income > 0, expense < 0).

**Weaknesses this plan fixes:**
- Balance math uses `Int` cents everywhere → overflow risk above ~$21.4M (audit **A1**).
- `processPayday` double-counts income on a double-tap (audit **A5**, HIGH).
- Reconciled bank balance can drift after edits with no aggressive re-reconcile CTA (audit **A4**, HIGH).
- `AddIncomeScreen` truncates (not rounds) the semi-monthly/monthly rate conversion (audit **D5**).
- **NEW — floating-point in display:** `MainViewModel.formatCurrency` does `"$%.2f".format(cents / 100.0)`,
  a `Double` division, violating `CLAUDE.md` and the QA "no floating-point currency" gate.
- Dashboard is dense and concept-heavy (audit D1/D2; budgeting model rated 4/10 in the fintech audit).
- No first-class "monthly plan" view: income − bills − planned spend = left to spend.

---

## 4. Workstream A — Make The Budget Tracker Simpler (UX)

Ordered by impact, drawn from `budgeting-roadmap.md` P0/P1 and the fintech audit P0/P1.

### A1. Add a single "Monthly Plan" (the budget tracker home)
Replace the scattered income/bills/transactions overview with one plain-language plan:
**Income this month − Planned bills − Planned spend = Left to spend.**
Borrow Simplifi "Spending Plan" / YNAB clarity. This is the surface the user calls the "budget tracker."
- Files: `PlanningScreen.kt`, `AppView.kt` (nav entry), `AppUiState.kt` (add plan fields), `MainViewModel.kt`.
- Definition of done: a user sees one number ("Left to spend") and the three components that built it.

### A2. Simplify income entry
Required fields only: source, amount, frequency, last payday. Keep "expected/usual amount" optional and hidden.
- Files: `AddIncomeScreen.kt`, `IncomeEntity.kt`.
- DoD: first paycheck entered in under a minute; optional stays truly optional.

### A3. Simplify bill / purchase entry
Default recurrence to `Monthly`; hide due-date picker until the recurrence needs it; keep auto-pay behind an advanced toggle; allow save with name + amount + recurrence only.
- Files: `AddPaymentScreen.kt`, `PaymentSchedule.kt`, `ScheduleDatePickerField.kt`.
- DoD: a bill can be added without understanding the recurrence engine.

### A4. De-clutter the dashboard
One obvious primary action at a time; compress the forecast explanation to one sentence; remove duplicate
explanations shared with the Analysis screen.
- Files: `DashboardScreen.kt`, `AppView.kt`, `OnboardingProgress.kt`.

### A5. Honest "safe to spend" when unreconciled
When not reconciled, show a distinct "Connect your balance to unlock forecasting" state instead of a scary
`$0.00` / negative number (audit **D1/D2**).
- Files: `DashboardScreen.kt`, `MainViewModel.buildActionCenter`, `BalanceSeedResolver.kt`.

### A6. Clear over-plan state for goals
If bills exceed income, show a distinct red "you are over plan" state with a "reduce bills / add income" CTA
(audit **D3**) instead of hiding the shortfall with `coerceAtLeast(0)`.
- Files: `PlanningScreen.kt`, `MainViewModel.buildMoneyBuckets`.

> Note: keep the three add-screens but consider one "Add money movement" funnel (Income / Bill / Expense)
> that routes to the simplified forms above to reduce first-run confusion.

---

## 5. Workstream B — Make The Calculator Correct (Math)

These are the "income, expenses and purchases calculate correctly" guarantees. Ordered by severity.
Each item is a HIGH/MEDIUM correctness fix. Treat any change touching > 2 files as HIGH blast radius:
build + unit tests after each.

### B1. [HIGH] Eliminate floating-point currency in display — `formatCurrency`
`MainViewModel.formatCurrency` uses `cents / 100.0` (Double). Fix with integer math:

```kotlin
// BEFORE (violates CLAUDE.md + QA gate)
return "\$${String.format("%.2f", cents / 100.0)}"
// AFTER (exact)
val sign = if (cents < 0) "-" else ""
val abs = kotlin.math.abs(cents)
val dollars = abs / 100
val rem = abs % 100
return "$sign$$dollars.%02d".format(rem)
```

Grep the whole `app/src` tree for `/ 100.0`, `* 100`, `toFloat()`, `toDouble()` in money paths and fix
each; the QA gate is "no floating-point currency in any file." Also add a `MoneyUtils.centsToDisplay()`
helper so every screen formats identically.

### B2. [HIGH] Make `processPayday` idempotent (no income double-count)
Audit **A5**: two rapid taps on recurring income insert two income transactions for the same period.
Add a `processed_date` guard inside the existing `withTransaction` and a uniqueness check on `next_date`.
- File: `LedgerRepository.processPayday` + a unit test (B-test #2).

### B3. [HIGH] Reconciliation drift detection + re-reconcile CTA
Audit **A4**: `updateTransaction`/`deleteTransaction` patch the reconciled bank balance by a delta; any
inconsistency drifts `bank_balance` away from `sum(txns)` with no prompt. After every mutation, recompute
drift and surface a prominent "Balances drifted by $X — re-reconcile" banner.
- Files: `LedgerRepository.kt` (mutations + `checkBalanceConsistency`), `DashboardScreen.kt`, `MainViewModel`.
- Test: B-test #5.

### B4. [HIGH] Standardize money on `Long` cents (overflow safety)
Audit **A1**: `ledgerBalanceCents`, `safeToSpendCents`, Monte Carlo bands, `scheduledBillBurdenCents` are
all `Int`. A balance near 2,147,483,647 cents wraps silently. Promote money math to `Long`; net worth is
already `Long`. Recommended path: introduce an inline `value class Money(val cents: Long)` and migrate the
hot paths (ViewModel dashboard computation, forecast inputs) first, then the UI state fields. Do this as a
dedicated, test-first refactor — do not mix with UX changes.

### B5. [MEDIUM] Fix income rate conversion rounding
Audit **D5**: `AddIncomeScreen` converts weekly→semi-monthly/monthly and calls `.toInt()` (truncates)
instead of `setScale(0, HALF_UP)`. Change to round like `dollarsToCents`.
- File: `AddIncomeScreen.kt`. Test: B-test #3.

### B6. [MEDIUM] Align flow windows / clarify labels
`totalInflowCents` / `totalOutflowCents` are computed over the **last 30 days** (`recentTransactionsSince`),
while `ledgerBalanceCents` is **all-time**. Either relabel as "last 30 days" or make the window explicit so
the budget tracker never shows a misleading "total." Decide one window per surface and document it.
- Files: `MainViewModel.toFlowSummary`, `DashboardScreen.kt`.

### B7. [MEDIUM] Single timestamp/clock for date correctness
Audit **D4**: transactions stamped with `LocalDate.now()` can land on the wrong day near midnight or across
timezones. Inject one `Clock` (testable) and store dates consistently so income/expense/purchase dates are
always correct.
- Files: `LedgerRepository.payBillOccurrence`, `MainViewModel`, a `ClockProvider`.

### B8. [TEST-ONLY] Prove the single-source-of-truth contract
Add a focused unit test that builds income + expense + a paid bill and asserts
`sum(txns) == income − expense − billAmount` and that paying a bill creates **exactly one** expense
transaction (no double-count between `PaymentEntity` and `TransactionEntity`).
- File: `LedgerRepositoryTest.kt` / `MainViewModel` test. Test: B-test #1 + #4.

---

## 6. Phased Rollout (recommended order)

**Phase 0 — Correctness foundation (do first; highest risk if skipped):**
B1 (float display) → B2 (payday idempotency) → B3 (reconciliation drift) → B4 (Long cents) →
B5/B6/B7 (income rounding, window labels, clock) → B8 (contract tests).

**Phase 1 — Simpler tracker (UX), can run in parallel with Phase 0's test-only items:**
A1 (Monthly Plan) → A3 (bill entry) → A2 (income entry) → A4 (dashboard declutter) → A5/A6 (honest safe-to-spend, over-plan state).

**Phase 2 — Verify & polish:** run full suite, lint, manual reconciliation walkthrough, update QA_CHECKLIST ticks.

---

## 7. Correctness Test Plan (the "calculate correctly" guarantee)

Add/extend unit tests under `app/src/test`:

1. **Single source of truth:** insert income +1000, expense −300, pay a $200 bill → `ledgerBalance == 500`
   and exactly one expense transaction exists for the bill (no double-count).
2. **Payday idempotency:** call `processPayday` twice rapidly for the same recurring income → exactly one
   income transaction; `next_date` advanced once.
3. **Income rounding:** weekly→monthly conversion rounds to nearest cent (HALF_UP), not truncate.
4. **Display exactness:** `centsToDisplay(1001) == "$10.01"`, `centsToDisplay(10005) == "$100.05"`,
   negative `-250` → `"-$2.50"`.
5. **Reconciliation drift:** edit an expense after reconcile → drift detected and banner state set.
6. **Overflow safety (after B4):** a `Long` balance of 2_500_000_000 cents computes and formats without wrap.
7. **Window clarity (after B6):** `totalInflowCents` over the chosen window matches the label.

---

## 8. Verification Commands (run from project root)

```powershell
cd C:\Workspace\Project_Android\MonteCarloLedger
.\gradlew.bat assembleDebug        # must build clean after any code change
.\gradlew.bat testDebugUnitTest    # all unit tests above must pass
.\gradlew.bat lint                 # no new lint warnings on touched files
```

Per `CLAUDE.md` and `AGENTS.md`: never claim build/test success unless the command was actually run and
passed. Do **not** mix the B4 `Long`-cents refactor with UX edits in the same change set (HIGH blast radius).

---

## 9. Definition Of Done

- [ ] No floating-point currency remains in `app/src` (grep clean; QA gate passes).
- [ ] `processPayday` cannot double-count income.
- [ ] Reconciled balance cannot silently drift; a drift banner appears when it does.
- [ ] All money math uses `Long` cents (or `Money` value class); no `Int` overflow on realistic balances.
- [ ] A unit test proves income + expenses + purchases sum to one correct balance with no double-count.
- [ ] A single "Monthly Plan" view shows Income − Bills − Planned spend = Left to spend.
- [ ] Income and bill entry each need only the essential fields.
- [ ] Dashboard shows one primary action and an honest safe-to-spend state when unreconciled.
- [ ] `assembleDebug`, `testDebugUnitTest`, and `lint` all pass.

---

## 10. References

- `docs/CODE_AUDIT_AND_ROADMAP.md` — findings A1, A4, A5, B1–B5, C1, D1–D7 (cited above).
- `docs/budgeting-roadmap.md` — P0 simplification items (bill/income/dashboard).
- `docs/fintech-functionality-audit-2026-04-24.md` — "no formal budget system" gap; recommended build order.
- `QA_CHECKLIST.md` — ship gate (financial precision, no float currency, Room migration safety).
- `CLAUDE.md` / `PROJECT_CONTEXT.md` — invariants and verification commands.
