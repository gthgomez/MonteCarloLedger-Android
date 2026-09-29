# Current state — executable source outranks status prose

All facts refer to the pins in00; evidence IDs resolve in16. Critical source paths, representative tests, schemas/migrations, metadata and remote CI were inspected. This is not a line-by-line review of every UI file or an executed local build.

## Metadata and branches

Python master is cbe0dec0ae7f329a76e7d849475711a011792942, a September29 security-policy documentation commit. Other observed branch: codex/polish-repo-presentation at aaa0f79ea458cc2b52e62365d491009774323a90. Recent changes largely relocate agent notes and polish documentation.

Android main is 6e418928f429b9be8deffb62525ba51f6017d50f, also a September29 security-policy commit. Other branches: feat/mc-calibration-and-dashboard-deriver at bd5a52795ad190b8693158fe854678b0487438bf; montecarlo-proprietary-license at f9ff2ba563da5579bb5b1f88bbf62ca367f82a56. Recent commits include a52405f79c369294dc2551ab53f0e7dc74281f56 repairing redundant Android SDK setup, and documentation relocation. Branch names are not evidence of newer/default production behavior. Open issue/PR searches returned none; recheck before implementation.

## Python

`db_manager.py` combines dataclasses, connections, migrations, CRUD and cached balance. Five-table domain: income, payments, transactions, settings, bill occurrences. Local integer IDs; signed ledger plus cached current balance; SQLite version10. `expected_amount` is added by migration rather than sufficient inspection of schema.sql alone. INTEGER affinity does not by itself enforce integer financial inputs. `_to_int_safe` can use float conversion then zero; `_to_int_strict` also needs strict null/bool handling. Current DDL runs before version-specific migration. [P-DB/P-SCHEMA]

Individual transaction/cache insertion is atomic. Whole bill/payday workflows are not: transaction insertion precedes occurrence linking or income cursor advancement in a separate transaction. Actual payment date is conflated with scheduled date. Occurrences have payment/date uniqueness but template-joined amounts rather than Android's snapshot model; orphan/paid-link semantics require care. [P-PAY/P-INCOME/P-DB]

`budget_engine.py` contains money/recurrence helpers; `timeline_service.py` produces events; `forecasting.py` reduces deterministic balances; `risk.py` perturbs events. Python is not missing a deterministic baseline. Weaknesses include implicit time, month-anchor drift for income, inclusive horizon despite exclusive prose, finite past-bill lookback, overdue-income skipping, end-balance headline quantiles and surprise generation bounded by last base event. Opening-negative/no-event behavior needs explicit tests. [P-MONEY/P-TIMELINE/P-FORECAST/P-RISK]

No persisted multi-account, clearing, assets, goals, category rules/budgets or debt domain exists in the inspected Python schema. The CLI has forced init/onboarding and an interactive menu rather than deterministic commands/JSON. FastAPI has one unauthenticated local safe-to-spend GET; startup can migrate, and separate reads do not establish one snapshot. [P-CLI/P-API]

Packaging already exists: setuptools/wheel, Python>=3.10, v0.1.0, console entry point, schema package data. Enumerated package discovery must change when subpackages arrive. Service dependencies are mandatory today. DB_PATH resolves beside the installed package's parent, not an OS user-data location. [P-PACKAGE/P-DB]

Eight test files were inventoried. Read property tests cover bounded money and weekly/biweekly/monthly recurrence; they also assert inclusive end. Hardening tests cover mismatch/past due, not concurrent snapshot correctness. Ubuntu CI uses Python3.10/3.11, editable install, pytest/Ruff/Pyright. No wheel/pipx/uv/native binary qualification is established. [P-PROPERTY/P-HARDENING/P-CI]

## Android

Room16 has eleven entity types: accounts, income, payments, transactions, bill occurrences, settings, category rules, assets, goals, budgets and debts. Explicit migrations1→16; no destructive fallback was identified in the read database setup. Recent migration additions include clearing/account links and revolving-credit fields. Legacy global/default balance and nullable transaction account identity remain. Occurrences snapshot amount and original/rescheduled date, but stable nominal uniqueness/settlement linkage differ from Python. [A-DB/A-OCC]

Repository operations include atomic bill payment and payday, duplicate-aware CSV insertion, preserved local app-lock settings and full backup replacement. However, payday duplicate detection uses description/name+date, so distinct same-named sources can collide. Normal CRUD, CSV/bill posting and credit-linked paths do not share one account-routing implementation. Default-account changes can grant reconciliation without observation. A credit refund may exceed owed balance, which the current path rejects rather than representing positive credit. [A-REPO]

Product depth includes richer recurrence, cash-flow windows, calibrated/category Monte Carlo, daily bands, pacing, monthly plans, category rules, installment/revolving payoff, actions, mapped CSV, backups, reminders, Compose and Glance. These are meaningful features but not parity proof. Category matching ties depend on incoming rule order. Seven-day pacing lacks explicit history coverage/account inclusion policy. Monthly plans can treat paid/null skipped occurrences as paid expenses. [A-RECURRENCE/A-MC/A-CAL/A-DEBT/A-PLAN/A-PACING/A-CATEGORY]

Backup JSON6 uses numeric Long values, unsafe for a naive JavaScript Number bridge above2^53. Missing/wrong-type array fallbacks and defaulted financial fields need strict preflight before full replacement. AES-GCM backup encryption and a PIN app lock do not encrypt the Room database. Main manifest omits INTERNET and disables ordinary backup; final merged release permissions were not inspected. [A-EXPORT/A-IMPORT/A-CRYPTO/A-MANIFEST]

Build: JDK17, minSdk26, compile/target36, Room2.8.4, composite DesignSystem, versionCode1/versionName1.0, release minification. CI runs unit tests and debug assembly; it does not run existing instrumented migration matrices. The newer matrix covers representative9→16 and legacy chains, beyond old status claims. No signed/store release was verified. [A-BUILD/A-CI/A-MIGRATION-TEST]

## Documentation drift

Android STATUS.md is dated August10, mentions schemas10/11/12 and182 historical tests, and contains local C: links; source is16. Its v0.1.0 release repeats the old report and has no assets. PRIVACY.md contains TODO_PRIVACY_URL. SECURITY.md disclaims security triage/remediation/support and conflates proprietary status with vulnerability status; those are separate concepts. Correct policy before broader distribution without changing licensing. [A-STATUS/A-PRIVACY/A-SECURITY]

Python before-payday prose does not consistently match full-horizon minimum calculation; schedule end documentation contradicts source/tests. Repository-root storage documentation describes editable layout, not safe installed behavior. Integer field types do not prove complete overflow safety or absence of migration float conversions. [P-README/P-MONEY/P-DB]

Remote CI evidence: Python run36514761028 succeeded; Android run36514848244 succeeded. Both are pinned-head evidence of their configured gates only. Local suites, instrumented device execution, distributions and hosted integrations remain unverified.
