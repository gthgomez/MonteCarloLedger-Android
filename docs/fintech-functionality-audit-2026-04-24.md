# Fintech Functionality Audit

Date: 2026-04-24

Scope: Current Android app functionality in `C:\Workspace\MonteCarlo-Ledger-app`, compared against leading budgeting, money-management, and consumer fintech apps.

Verification:

- `.\gradlew.bat testDebugUnitTest` passed on 2026-04-24.
- `.\gradlew.bat assembleDebug` passed on 2026-04-24.
- This is a code and product audit, not a live emulator UX audit.

## Benchmark Set

Primary benchmark competitors:

- YNAB: budgeting method, bank import, targets, loan planner, reports, sharing, multi-device sync.
- Monarch Money: account aggregation, AI categorization, recurring calendar, reports, collaboration, investments, customizable dashboard.
- Copilot Money: account/investment tracking, automatic categorization, rollovers, cash flow, subscriptions, polished multi-platform UX.
- Quicken Simplifi: Spending Plan, bills, goals, planned spend, other spend, left-to-spend, projected spend.
- Rocket Money: subscription management, cancellation help, spending alerts, bill negotiation, automated savings.
- SoFi, Cash App, Chime, Revolut-style apps: broader banking, payments, cards, credit, investing, crypto, and real account services.

Sources checked:

- https://www.ynab.com/features
- https://www.monarch.com/features/tracking
- https://www.copilot.money/
- https://support.simplifi.quicken.com/en/articles/4212702-understanding-your-spending-plan
- https://www.rocketmoney.com/
- https://www.nerdwallet.com/finance/learn/best-budget-apps
- https://apps.apple.com/us/app/sofi-bank-invest-crypto/id1191985736
- https://cash.app/press/cash-releases-see-whats-new

## Current Product Read

MonteCarlo Ledger is no longer just the manual-first forecasting ledger described in the older roadmap. The current app is best described as:

> A local-first Android cash-flow forecasting ledger with manual entry, CSV import, recurring bill timelines, reconciliation, review workflows, basic categorization/rules, goals/assets, reminders, and backup/restore.

That is a credible niche. It is not yet a top-tier fintech app in the broad market sense because it does not connect accounts, move money, authenticate users, sync across devices, manage cards, monitor credit, automate subscriptions, or support household collaboration.

## What Exists Now

Strong current surfaces:

- Manual income, bills, and transaction entry.
- Local Room persistence for income, payments, transactions, bill occurrences, settings, transaction rules, assets, and goals.
- Bank-balance reconciliation and mismatch detection.
- 90-day forecast, safe-to-spend, daily budget, lowest balance, first negative date, and Monte Carlo risk projections.
- Bill occurrence generation with overdue/current timeline and mark-paid actions.
- CSV import for transactions and bills with column mapping, duplicate detection, and preview.
- Transaction review inbox with basic category suggestions and reusable rules.
- Category spend summaries, recurring transaction detection, flow summaries, runway, money buckets, and trust signals.
- Manual net-worth assets and savings goals.
- Reminder preferences and WorkManager-driven bill notifications.
- Plain JSON backup/restore and password-protected AES-GCM backup file export/import.
- Adaptive dashboard widgets and Android debug APK build.

Key code anchors:

- `AppUiState.kt`: dashboard state includes balances, safe-to-spend, Monte Carlo bands, category spend, net worth, review items, buckets, trust signals, and widget config.
- `LedgerRepository.kt`: imports, transaction review state, bill occurrence sync, rules, assets, goals, backup restore, reconciliation settings.
- `PaymentListScreen.kt`: bill timeline for overdue and next 30 days.
- `SettingsScreen.kt`: CSV import, backup/restore, encrypted export/import, reminders, dashboard widgets, assets, and goals.
- `SecurityUtils.kt`: password-derived AES-GCM backup encryption.

## Market Fit Scorecard

Scores are relative to top consumer fintech expectations, not relative to a small local-first app.

| Area | Current | Benchmark | Rating |
| --- | --- | --- | --- |
| Forecasting and cash-flow risk | Strong: forecast, safe-to-spend, Monte Carlo, trouble dates | Simplifi cash flow, Copilot summaries, Monarch dashboard | 8/10 |
| Manual ledger basics | Solid: income, bills, transactions, edit/delete, recurrence | YNAB/manual budgeting apps | 7/10 |
| Onboarding clarity | Improved with action center, but still concept-heavy | YNAB/EveryDollar/Goodbudget clarity | 6/10 |
| Budgeting model | Partial: categories, buckets, goals, safe-to-spend | YNAB targets, Simplifi Spending Plan, Copilot rollovers | 4/10 |
| Import and automation | CSV import exists; no live bank sync | Monarch, Copilot, Simplifi, Rocket Money automation | 4/10 |
| Categorization | Rules plus simple heuristics | AI/ML categorization and review loops | 4/10 |
| Bills and subscriptions | Good local bill timeline; no account-backed bill sync or cancellation | Monarch recurring calendar, Rocket subscriptions/cancellation | 5/10 |
| Net worth and investing | Manual assets/goals only | Monarch/Copilot/SoFi investment and holdings tracking | 3/10 |
| Collaboration | None | Monarch/YNAB household sharing | 0/10 |
| Cross-device/platform | Android local only | Web/iOS/Android/Mac sync | 1/10 |
| Security and trust | Local-only privacy, backup disabled, encrypted export; no app lock/auth; weak KDF iteration count | Bank-grade security, 2FA, biometric/app lock, audited sync | 5/10 |
| Monetization | Billing interface only | Subscription/paywall entitlement handling | 2/10 |
| Broad fintech services | None | SoFi/Cash App/Chime/Revolut banking, payments, card, credit, investing | 0/10 |

Overall: 4.5/10 against broad top fintech apps, 6/10 against budgeting-only apps, and 7/10 for a local-first forecasting ledger.

## Most Important Gaps

1. No account aggregation or live transaction sync.

This is the biggest market gap. Top apps now assume automatic transaction ingestion through Plaid/MX/Finicity-style providers, plus fallback manual/import flows. CSV import is a good bridge, but it is not enough for mainstream retention.

2. No formal budget system.

The app has safe-to-spend and categories, but not budgets users can intentionally manage: category limits, rollover, planned spend, monthly targets, watchlists, sinking funds, or envelope-style allocation.

3. No real subscription management.

Recurring detection and bills exist, but there is no subscription review flow, cancellation state, forgotten-subscription alert, annual-renewal handling, or bill-negotiation path.

4. Net worth is too shallow.

Manual asset rows are useful, but top apps aggregate investment accounts, holdings, allocation, performance, real estate values, debts, loans, and credit cards.

5. Trust/security is incomplete for financial data.

Local-first privacy is a strength, but the app lacks app lock, biometric gate, account identity, 2FA, cloud sync security posture, or strong backup KDF parameters. `SecurityUtils` uses PBKDF2-HMAC-SHA256 with only 10,000 iterations, which is low for password-protected financial backups in 2026.

6. Settings overclaims "Cloud Sync".

The current "Cloud Sync (Encrypted)" setting exports an encrypted local file. It does not sync. Rename this unless real sync is added.

7. Goals exist but appear under Settings.

Top fintech apps put goals in the planning loop. In this app, goals are stored and displayed, but goal creation is not first-class in the main money workflow.

8. Billing is only an interface.

`BillingGateway` defines purchase methods, but there is no concrete entitlement, paywall, product config, restore purchase UX, or gated premium behavior visible in the inspected code.

## Best Strategic Position

Do not chase SoFi/Cash App/Revolut breadth first. MonteCarlo Ledger's defensible angle is:

> Forecast-first, local-first, privacy-respecting cash-flow planning for people who want to know whether their money will survive the next 30 to 90 days.

Trying to become a full fintech super-app would dilute the product. The strongest path is to beat budgeting apps on forward-looking cash-flow clarity while gradually removing manual work.

## Recommended Build Order

### P0: Make the current product feel finished

1. Rename "Cloud Sync (Encrypted)" to "Encrypted Backup".
2. Move goals out of Settings into the planning/dashboard flow.
3. Add explicit category budgets or watchlists.
4. Add app lock with biometric/PIN.
5. Increase encrypted backup KDF strength and version the backup envelope.
6. Add transaction search/filter and category editing ergonomics.

### P1: Match serious budgeting apps

1. Add monthly budget plans: income, recurring bills, planned spend, goals, left-to-spend.
2. Add rollover/sinking-fund support.
3. Add recurring/subscription review: detected, active, cancelled, annual, price-change notes.
4. Add smarter transaction matching for bills and subscriptions.
5. Add better reports: category trends, month-over-month cash flow, Sankey/inflow-outflow view.

### P2: Add automation without losing local-first trust

1. Add optional bank sync through an aggregator.
2. Keep manual and CSV import fully supported.
3. Add account model: checking, savings, credit card, loan, investment, cash/manual.
4. Add sync-health and "last updated" indicators per account.
5. Add dedupe/reconciliation across manual, CSV, and synced sources.

### P3: Expand toward top fintech expectations

1. Household/collaboration support.
2. Cross-device sync or web companion.
3. Credit/debt payoff planning.
4. Investment holdings and allocation views.
5. Widgets and quick actions.
6. Premium entitlement implementation if monetization is intended.

## Priority Verdict

The app is much stronger than the old roadmap implies. It already has the hard part many consumer finance apps fake: actual forward-looking cash-flow math.

The market gap is not math. It is automation, trust, and planning vocabulary.

The next best sprint is:

1. Rename encrypted backup UI and harden backup encryption.
2. Add app lock.
3. Promote goals/planning out of Settings.
4. Implement a real monthly Spending Plan view.
5. Improve transaction review into a subscription/bill/category command center.

That sequence makes the current product feel legitimate before taking on live bank sync.
