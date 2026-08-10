# Code Audit & "Superior Budgeting App" Findings

> Scope: full `app/` tree of MonteCarloLedger (commit `9c77ac5`), reviewed across four lenses —
> financial math/precision, forecasting engine, persistence/backup/security, and UI/UX domain edge cases.
> Goal: enumerate concrete bugs + edge cases and turn them into a roadmap for a best-in-class
> income-management & budgeting app. Every finding cites a file/line in the current source.

---

## 0. How this audit was produced

The environment does not expose sub-agent tooling, so the four "sub-agent" lenses below were run as
independent, sequential deep-reads of the codebase, each with its own focus and a separate bug list.
Each lens traces real code paths (not guesses). Severity is: **CRITICAL** (silent money loss / data
loss / security), **HIGH** (wrong numbers or misleading UX on common paths), **MEDIUM** (edge-case
correctness), **LOW** (hardening / polish).

---

## LENS A — Financial Math & Precision  (`Security: MoneyUtils`, `ForecastEngine`, `LedgerRepository`)

**A1. [MEDIUM] `Int` cents overflow on realistic net-worth / large balance math.**
All money is `Int` cents. `MainViewModel` computes `totalNetWorthCents = ledgerBalanceCents.toLong() +
totalAssetBalance` (MainViewModel.kt:454), but `ledgerBalanceCents` itself is `Int` and every other
money field is `Int`. A balance of ~$21.4M (2,147,483,647 cents) overflows `Int`. Power users with
investment assets will silently wrap to negative. **Fix:** standardize on `Long` cents everywhere, or
`BigInteger`/`BigDecimal` for net worth.

**A2. [LOW] `toFloat()` progress ratios can divide by zero / produce NaN.**
`DashboardScreen.kt:1385` does `"${(progress * 100).toInt()}%"`, and several `MoneyBucketState`
progress values are `count / max(…,1)` (MainViewModel.kt:577-580). `positiveSeed>0` guards exist, but
`totalAssetBalance`/`goalTarget` sums are `Int`. Mixed `Int`/`Long` math is fragile. **Fix:** central
`Long`-based money type with explicit formatting helpers.

**A3. [PASS] Dollars→cents conversion is now exact.**
After the previous review, `MoneyUtils.dollarsToCents` uses `BigDecimal` + `HALF_UP`, and every entry
screen routes through it. `CsvImport.parseMoneyCents` already used `BigDecimal`. This was the biggest
"no floating-point currency" gate risk and is resolved (QA_CHECKLIST item 1).

**A4. [MEDIUM] Bank-balance reconciliation is "sticky" and can lie after edits.**
`writeBankBalanceCents` (LedgerRepository.kt:632) unconditionally sets `bank_balance_reconciled=true`
and mirrors both `bank_balance` and `current_balance`. `updateTransaction` (LedgerRepository.kt:160)
applies `deltaCents = reviewed.amount_cents - existing.amount_cents` only `if (existing != null)`.
If a transaction's `amount_cents` is changed via edit, the bank balance is patched by the delta — but
`deleteTransaction` subtracts `entity.amount_cents` unconditionally (LedgerRepository.kt:185), while
`insertTransaction` adds. Any inconsistency (e.g. reconciled balance edited directly, or a transaction
that was imported then deleted) can drift the reconciled bank balance away from `sum(txns)` with no
reconciliation prompt. `checkBalanceConsistency()` (LedgerRepository.kt:418) detects the mismatch but
nothing surfaces a "re-reconcile" CTA aggressively. **Fix:** treat reconciled bank balance as derived
+ an offset; recompute drift on every mutation; show a prominent "Balances drifted by $X" banner.

**A5. [HIGH] `processPayday` records the *actual* amount as income but advances using the *expected*
schedule; if user edits the paycheck amount repeatedly, no guard prevents double-counting.**
`processPayday` (LedgerRepository.kt:387) inserts an income transaction of `actualAmountCents` and
advances `next_date`. If a user "processes payday" twice (e.g. tapped twice before the UI updates) on a
one-time income, the income is deleted on first call so the second is a no-op — good. But for recurring
income, two rapid taps create two income transactions for the *same* pay period and advance the date
twice, double-counting income. **Fix:** make `processPayday` idempotent per `next_date` via a
`processed_date` guard and run inside the existing `withTransaction` (already wrapped) plus a uniqueness
check.

---

## LENS B — Forecasting & Monte Carlo  (`ForecastEngine`, `MonteCarloEngine`, `TimelineService`)

**B1. [HIGH] Surprise expenses in Monte Carlo use type `"expense"`, but real forecast bills are `"bill"`.**
`MonteCarloEngine` injects `"Unexpected Expense"` as `type="expense"` (MonteCarloEngine.kt:125) while
`TimelineService` emits bills as `type="bill"`. Simulation subtracts for anything not `"income"`, so the
math is fine, but this means the *same* semantic concept ("a bill") is encoded with two different type
strings. Filters like `MainViewModel` `scheduledBillBurdenCents = events.filter{type=="bill"}` will
never include surprise expenses (correct for the deterministic forecast), yet the Monte Carlo "risk %"
implicitly includes them. The split is undocumented and easy to break. **Fix:** add a `subtype`/`source`
field to `ForecastEvent` so `type` stays consistent ("bill" everywhere) and filtering is explicit.

**B2. [MEDIUM] `calculateIncomeContribution` semantics are ambiguous and under-tested.**
`ForecastEngine.calculateIncomeContribution` (ForecastEngine.kt:127) returns
`max(0, lowestProjected − minCash)` where `lowestProjected` walks all events and `minCash` walks
bills-only. The dashboard then shows "Includes $X projected income" (DashboardScreen.kt:751). It's
unclear whether this is "extra spendable amount attributable to upcoming income" or "worst-case buffer
improvement." Only 3 unit tests cover it, none with a non-trivial multi-income/multi-bill scenario.
**Fix:** document the metric precisely and add a parametrized test; or replace with a clearer
"income-covered buffer" computation.

**B3. [MEDIUM] Monte Carlo uses `LocalDate.now()` inside the engine, hurting determinism for testing &
reproducibility.**
`MonteCarloEngine.generateScenarioTimeline` calls `val today = LocalDate.now()` (MonteCarloEngine.kt:
103). Despite a fixed `seed`, the result depends on the calendar date the simulation runs. Two runs on
different days (or a unit test vs prod) differ. **Fix:** inject `today` as a parameter (deterministic by
default, `LocalDate.now()` at the call site in `MainViewModel`).

**B4. [LOW] `percentile` can mis-handle tiny run counts; `runs` default 500 is fine but not surfaced.**
`MonteCarloEngine.percentile` (MonteCarloEngine.kt:170) uses `ceil(size*p).toInt()-1`; for `p=0.1` and
`size=1` that yields `max(0,-1)=0` → index 0, OK, but the formula is non-standard. Acceptable; just note
for reviewer.

**B5. [MEDIUM] Forecast horizon is hardcoded to 90 days / 90-day window in many places.**
`MainViewModel` builds events with `daysAhead=90` (MainViewModel.kt:368) and `simulation_days` default
90 (LedgerRepository migrate). `buildCashFlowWindows` is also 90. But `syncBillOccurrences` uses
lookback 30 / horizon 90 (LedgerRepository.kt:329). If a user has a quarterly bill (every 90–92 days),
the 90-day horizon may exclude the next occurrence, making the forecast miss a large bill. **Fix:**
derive horizon from the max recurrence cadence present (e.g. include at least 2 cycles of the longest
recurrence), or make it user-configurable.

---

## LENS C — Persistence, Migrations, Backups & Security  (`AppDatabase`, `SecurityUtils`, `BackupExport/Import`, `AppLock`)

**C1. [HIGH] Backup import trusts `schemaVersion==1` only and has NO integrity MAC / version
negotiation beyond a string match.**
`BackupImport.parseLedgerBackupJson` requires `schemaVersion == 1` (BackupImport.kt:9) and otherwise
throws. Good that it validates, but: (a) decryption errors from `SecurityUtils.decrypt` (wrong password)
surface as a generic `IllegalArgumentException` caught upstream — the UI must distinguish "wrong
password" vs "corrupt file" per QA_CHECKLIST item 4. Confirm `BackupImport`/UI maps GCM auth-failure
(`AEADBadTagException`) to "wrong password" and JSON parse failure to "corrupt." (b) There is no
checksum/MAC *inside* the plaintext payload, so a partially-written or truncated file can import with
missing transactions silently. **Fix:** add a top-level `hmac` field over the plaintext (key-derived)
and verify before parsing; give explicit error states.

**C2. [MEDIUM] `SecurityUtils.decrypt` silently accepts legacy 10k-iteration backups with no migration
path.**
`LEGACY_ITERATIONS = 10000` is used when the `MCL1:` header is absent (SecurityUtils.kt:60). If a user
imports an old backup, it decrypts at weak params but is then re-encrypted on export at 310k — fine — but
the code path means *any* non-`MCL1` string is treated as legacy, including a totally unrelated blob.
**Fix:** reject non-`MCL1` payloads unless an explicit "import legacy" toggle is on; document the
version.

**C3. [MEDIUM] App lock is a local gate only; `unlockApp` success is in-memory and can be bypassed by
process death without re-auth in some flows.**
`AppView` gates on `appLockPreferences.enabled && !appLockUnlocked` (AppView.kt:137). `appLockUnlocked`
is a `MutableStateFlow` in the VM. If the OS kills & restarts the app, `appLockUnlocked` resets to
`false` → relock (good). But `enableAppLock`/`disableAppLock` write the PIN hash to `settings` table;
there's no biometric option, no lockout/backoff after N wrong PINs (AppLockScreen enables unlock at
`pin.length >= 4` with no attempt counter), enabling offline PIN brute-force (4–12 digits, no throttle).
**Fix:** add attempt counter + exponential backoff; optionally offer biometrics (AndroidX
Biometric); store lock state so background return also re-prompts.

**C4. [MEDIUM] Migrations are `exportSchema = false` and use only `ALTER`/`CREATE`; a destructive or
lossy migration would lose financial data silently.**
`AppDatabase` is at version 9 with 8 migrations (AppDatabase.kt). All current ones are additive
(`ALTER TABLE ... ADD COLUMN`, `CREATE TABLE`), which is safe. **Risk is forward-looking:** the QA gate
"Room migration doesn't destroy data" is only as good as the next migration. **Fix:** enable
`exportSchema = true` + ship `schemas/` so migrations can be diff-reviewed; add a migration test
(`MigrationTest` with `Room`'s `MigrationTestHelper`) that asserts row counts are preserved across every
version pair.

**C5. [LOW] `settings` table is the single source of truth for balance AND feature flags; a bad write
races.**
`balanceState` Flow reads 3+ keys (LedgerRepository.kt:204). Writes are individual `setValue` calls, not
atomic. A crash between `writeBankBalanceCents` steps (LedgerRepository.kt:632) could leave
`bank_balance` and `current_balance` inconsistent. **Fix:** store as a single JSON/`balance` row or wrap
in `withTransaction`.

**C6. [PASS] AES-GCM params are sound.**
`SecurityUtils.encrypt` uses PBKDF2-HMAC-SHA256 (310k iters for backup, 210k for app lock), random
16-byte salt + 12-byte IV, `AES/GCM/NoPadding`, 128-bit auth tag, and embeds salt+IV+iterations in the
`MCL1:` payload. This is a strong, correct construction. App lock PIN verification uses constant-time
`MessageDigest.isEqual`. Good.

---

## LENS D — UI/UX & Domain Edge Cases  (`DashboardScreen`, `PlanningScreen`, `AppLock`, onboarding)

**D1. [HIGH] "Safe to spend" can be misleading when NOT reconciled.**
When `!reconciled`, `BalanceSeedResolver.resolve` uses `ledgerBalanceCents` (the sum of transactions),
so `safeToSpendCents` is anchored to the *transaction ledger*, which for a brand-new user is often `0`
or near-zero even if they have money. The dashboard shows "Safe to spend: $0.00" with no strong cue
that this is because they haven't reconciled. The overdraft UX ("Projected shortfall") then looks
alarming for a healthy user. **Fix:** when unreconciled, show a distinct "Connect your balance to unlock
forecasting" state instead of a scary $0 / negative number.

**D2. [MEDIUM] `SetupCompleteCard` shortfall wording conflates forecast overdraft with real overdraft.**
DashboardScreen.kt:1238 says "You're projected short by $X" using `safeToSpend` (the 90-day window
minimum). That's a *projection*, not a current-account overdraw. Users may panic. **Fix:** wording like
"Based on upcoming bills, your balance could dip to −$X around <date>" tied to `firstNegativeDateLabel`.

**D3. [MEDIUM] Goal funding math can go negative / unbounded.**
`PlanningScreen.leftAfterPlanCents = expectedIncome − billPlan − variableSpend` (PlanningScreen.kt:88)
has no floor; if bills exceed income the card shows a large negative with no actionable "you're over
plan" treatment. `MoneyBucketState` "Available after plan" uses `availableCents = safeToSpendCents
.coerceAtLeast(0)` (MainViewModel.kt:579) which hides the shortfall. **Fix:** show over-plan as a
distinct red state with a "reduce bills / add income" CTA.

**D4. [MEDIUM] Date/timezone & "today" boundary bugs.**
Many paths use `LocalDate.now()` and `LocalDateTime.now().toString()` (e.g. `payBillOccurrence` stamps
`LocalDate.now()` as the transaction date, LedgerRepository.kt:138). Users near midnight or in non-UTC
timezones can get transactions dated to the wrong day; `reviewed_at` uses device-local `LocalDateTime`
string with no zone, so sorting/parsing across timezones is ambiguous. **Fix:** store `Instant`/
`OffsetDateTime` (or at least persist the zone) and use a single `Clock` instance injected for testability.

**D5. [LOW] `AddIncomeScreen` hourly math divides by 24/12 for semi-monthly/monthly from a *weekly*
rate — correct but non-obvious; also `BigDecimal` divide uses scale 10 then `.toInt()` truncating, not
rounding, for those two branches (AddIncomeScreen.kt:78-79).**
`weeklyCents * 52/24` with `setScale(0, HALF_UP)` is applied only to the final product? No — the divide
uses `HALF_UP` at scale 10, then `.toInt()` truncates the scale-10 result. For most inputs fine, but
`*.toInt()` truncates rather than rounds the remaining fraction. **Fix:** `setScale(0, HALF_UP).toInt()`
on the final value (consistency with `dollarsToCents`).

**D6. [LOW] Onboarding milestone for expense is set on import but "first expense" can be auto-reached
without user intent; review inbox could flood.**
`importTransactions` sets `FIRST_EXPENSE` if any imported txn is an expense (LedgerRepository.kt:120), and
every imported/CSV expense gets `review_status = pending` (LedgerRepository.kt:555). A large CSV import
creates a huge review queue with no batch-approve. **Fix:** batch-approve on import with a confirmation,
or a "review later" mode; cap the visible review inbox.

**D7. [MEDIUM] No multi-currency / no account model.**
Everything assumes one currency and one "bank balance." A superior budgeting app needs accounts
(checking/savings/credit) and per-currency. Today `totalNetWorthCents = ledger + assets` mixes semantics
(ledger is flow-sum, assets are balances). **Fix:** model `Account` entities; net worth = sum of account
balances; transactions post to accounts; reconciliation per account.

---

## 1. Prioritized roadmap to a "superior" app

**P0 — Correctness & trust (do first)**
1. Migrate all money to `Long` cents (A1, A2). Prevents silent overflow for real users.
2. Make `processPayday` idempotent per period (A5) and add balance-drift detection + re-reconcile CTA
   (A4).
3. Distinguish backup "wrong password" vs "corrupt" errors and add an in-payload HMAC (C1).
4. Fix unreconciled "safe to spend" UX so it never shows a scary $0/negative for healthy users (D1, D2).

**P1 — Forecasting quality**
5. Unify bill event typing via `subtype`/`source` (B1); inject `today` into Monte Carlo for determinism
   (B3).
6. Derive forecast horizon from recurrence cadence (B5); document + test `calculateIncomeContribution`
   (B2).
7. Add scenario comparison (optimistic / expected / pessimistic) as a first-class feature, not just the
   Monte Carlo percentile endpoints.

**P2 — Data model & scale**
8. Add `Account` + multi-currency model; net worth = account balances (D7, A1).
9. Enable Room `exportSchema`, ship `schemas/`, add a `MigrationTest` preserving row counts (C4).
10. Atomic balance writes (C5); single injected `Clock` for all timestamps (D4).

**P3 — Security & retention**
11. App lock: attempt counter + backoff, biometric option (C3).
12. Explicit legacy-backup rejection toggle (C2); document `MCL1` versioning.

**P3 — Delight**
13. Goal over-plan red state + CTA (D3); batch CSV review (D6); clearer "income-covered buffer" metric
    (B2); spend-by-category insights with trends.

---

## 2. Test gaps to close
- `ForecastEngine.calculateIncomeContribution` non-trivial scenario test (B2).
- Monte Carlo determinism test with injected `today` (B3).
- Backup round-trip: wrong password → explicit error; truncated file → integrity failure (C1).
- Migration test across all version pairs preserving rows (C4).
- `processPayday` double-tap idempotency test (A5).
- Reconciliation drift detection test (A4).

---

## 3. Verification note
This audit is static (the sandbox blocks Gradle/AGP download, so `assembleDebug`/`testDebugUnitTest`
were not executed). Findings are derived from reading the current committed source. Run
`.\gradlew testDebugUnitTest` after applying P0 fixes to confirm.