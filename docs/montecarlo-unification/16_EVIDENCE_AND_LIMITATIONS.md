# Revision-pinned evidence and official research

Checked September29,2026. Source facts below are distinguished from proposed contracts/tests. Critical implementation sections were read, not every UI/test line. No real ledger was accessed; fixtures are synthetic. No independent subagent runtime was available. Local product execution was not performed; see15.

## Repository pins and remote evidence

Python: master at cbe0dec0ae7f329a76e7d849475711a011792942. Android: main at6e418928f429b9be8deffb62525ba51f6017d50f. Metadata, branches, open issue/PR searches, recent commits, trees, releases, schemas/migrations, critical source, representative tests and CI were inspected.

[Python pinned source root](https://github.com/gthgomez/MonteCarlo-Ledger/tree/cbe0dec0ae7f329a76e7d849475711a011792942)

[Android pinned source root](https://github.com/gthgomez/MonteCarloLedger-Android/tree/6e418928f429b9be8deffb62525ba51f6017d50f)

[Python CI run36514761028](https://github.com/gthgomez/MonteCarlo-Ledger/actions/runs/36514761028): success at audited head, Ubuntu/Python3.10 and3.11/editable pytest/Ruff/Pyright.

[Android CI run36514848244](https://github.com/gthgomez/MonteCarloLedger-Android/actions/runs/36514848244): success at audited head, unit tests/debug assembly; no instrumented migration lane.

Both v0.1.0 prereleases have no attached assets. Python release target71f64e63c3c5b21191b28e33f80cf564f2ac29f2; Android targeta70125de523a0eb2064d1f7993b948fd3c79e70a. Android release text repeats old status and is not proof of current Room16 migration qualification.

## Python evidence IDs

All paths below use the Python pin above; identifiers name inspected symbols/behavior.

| ID | Path / inspected area |
|---|---|
| P-DB | monte_carlo_ledger/db_manager.py — init_db, versioned migrations, conversions, add_transaction, occurrence links, cache/consistency |
| P-SCHEMA | monte_carlo_ledger/schema.sql — tables, indexes, foreign keys |
| P-MONEY | monte_carlo_ledger/budget_engine.py — Decimal parsing, recurrence/paydays/schedule end |
| P-RULES | monte_carlo_ledger/domain_rules.py — sign/link validation |
| P-TIMELINE | monte_carlo_ledger/timeline_service.py — read-only projection, income/overdue bills |
| P-FORECAST | monte_carlo_ledger/forecasting.py — reducer, minimum, first-negative and summary |
| P-RISK | monte_carlo_ledger/risk.py — random draws, clock, percentile and shock horizon |
| P-CLI | monte_carlo_ledger/cli.py — main/menu/onboarding/errors |
| P-API | monte_carlo_ledger/api.py — lifespan and safe-to-spend GET |
| P-PAY | monte_carlo_ledger/workflow_payments.py — handle_mark_paid |
| P-INCOME | monte_carlo_ledger/workflow_income.py — process_payday_flow |
| P-PACKAGE | pyproject.toml — entry point, package data/discovery/dependencies |
| P-PROPERTY | tests/test_property_invariants.py — bounded money/calendar properties |
| P-HARDENING | tests/test_hardening.py — mismatch, past due and SQLite gate |
| P-CI | .github/workflows/ci.yml |
| P-README | README.md — scope/install/horizon/storage prose |
| P-LICENSE | LICENSE — MIT |
| P-SECURITY | SECURITY.md — plaintext/local API/reporting/support policy |

Direct key references: [database](https://github.com/gthgomez/MonteCarlo-Ledger/blob/cbe0dec0ae7f329a76e7d849475711a011792942/monte_carlo_ledger/db_manager.py), [bill workflow](https://github.com/gthgomez/MonteCarlo-Ledger/blob/cbe0dec0ae7f329a76e7d849475711a011792942/monte_carlo_ledger/workflow_payments.py), [risk](https://github.com/gthgomez/MonteCarlo-Ledger/blob/cbe0dec0ae7f329a76e7d849475711a011792942/monte_carlo_ledger/risk.py), [package](https://github.com/gthgomez/MonteCarlo-Ledger/blob/cbe0dec0ae7f329a76e7d849475711a011792942/pyproject.toml).

## Android evidence IDs

Source prefix A is app/src/main/java/com/montecarlo/ledger/ at the Android pin.

| ID | Path / inspected area |
|---|---|
| A-DB | A/data/AppDatabase.kt — Room16, migrations1→16 |
| A-REPO | A/data/LedgerRepository.kt — CRUD, accounts/credit, reconciliation, payday, occurrence sync, import/restore |
| A-OCC | A/data/BillOccurrenceEntity.kt — amount snapshot, nominal/rescheduled dates, indexes/links |
| A-RULES | A/domain/DomainRules.kt — sign/type rules |
| A-MONEY | A/util/MoneyUtils.kt — BigDecimal parsing, HALF_UP/interest, legacy zero wrapper |
| A-RECURRENCE | A/processing/RecurrenceMath.kt — slots/anchors/frequencies |
| A-TIMELINE | A/processing/TimelineService.kt — exclusive horizon, overdue income/bills and overrides |
| A-FORECAST | A/processing/ForecastEngine.kt — balances/windows/initial negativity |
| A-MC | A/processing/MonteCarloEngine.kt — duplicate reducer, min/end/daily, RNG/shocks |
| A-CAL | A/processing/MonteCarloCalibrator.kt — monthly CV, fixed positive ranges, shock units/coverage |
| A-CAL-TEST | app/src/test/java/com/montecarlo/ledger/processing/MonteCarloCategoryVariationTest.kt — positive-lower-bound expectation |
| A-DEBT | A/processing/DebtPayoffEngine.kt — interest/minimum/extra/cash guard/nonconvergence |
| A-PLAN | A/processing/MonthlySpendingPlan.kt — bill/category burden, skipped-state interaction |
| A-PACING | A/processing/BudgetPacingEngine.kt —7-day velocity/runway/thresholds |
| A-CATEGORY | A/processing/CategoryRuleEngine.kt — matching tiers/input-order ties |
| A-ACTION | A/processing/OverdraftActionEngine.kt — heuristic numerical reduction/candidate matching |
| A-DASH | A/dashboard/DashboardDeriver.kt — account/ledger seed, net worth, engine wiring |
| A-EXPORT | A/ui/BackupExport.kt — JSON6, numeric Longs, array helpers/defaults |
| A-IMPORT | A/ui/BackupImport.kt — version readers/validation |
| A-CSV | A/ui/CsvImport.kt — mapping/preview/row cap |
| A-CRYPTO | A/security/SecurityUtils.kt — MCL1, AES-GCM, PBKDF2, optional HMAC/PIN |
| A-MANIFEST | app/src/main/AndroidManifest.xml — permissions/backup/widget |
| A-MIGRATION-TEST | app/src/androidTest/java/com/montecarlo/ledger/data/MigrationMatrixTest.kt — representative9→16 and legacy chains |
| A-BUILD | app/build.gradle.kts — SDK/JDK/Room/version/build configuration |
| A-CI | .github/workflows/android-ci.yml |
| A-STATUS | STATUS.md — historical schema/test report |
| A-README | README.md — proprietary banner/platform/build |
| A-LICENSE | LICENSE — proprietary original code/docs |
| A-PRIVACY | PRIVACY.md — local posture/export/PIN/privacy placeholder |
| A-SECURITY | SECURITY.md — proprietary support disclaimer/high-risk process |

Direct key references: [repository](https://github.com/gthgomez/MonteCarloLedger-Android/blob/6e418928f429b9be8deffb62525ba51f6017d50f/app/src/main/java/com/montecarlo/ledger/data/LedgerRepository.kt), [calibration](https://github.com/gthgomez/MonteCarloLedger-Android/blob/6e418928f429b9be8deffb62525ba51f6017d50f/app/src/main/java/com/montecarlo/ledger/processing/MonteCarloCalibrator.kt), [simulation](https://github.com/gthgomez/MonteCarloLedger-Android/blob/6e418928f429b9be8deffb62525ba51f6017d50f/app/src/main/java/com/montecarlo/ledger/processing/MonteCarloEngine.kt), [actions](https://github.com/gthgomez/MonteCarloLedger-Android/blob/6e418928f429b9be8deffb62525ba51f6017d50f/app/src/main/java/com/montecarlo/ledger/processing/OverdraftActionEngine.kt).

## Official research — checked September29,2026

[OAI-MODEL](https://developers.openai.com/api/docs/guides/latest-model): current GPT-6 guidance. [OAI-REASONING](https://developers.openai.com/api/docs/guides/reasoning-best-practices): direct/explicit prompting; historical examples are not current capability limits.

[OAI-SITES](https://learn.chatgpt.com/docs/sites): runtime/deployment/secrets/analytics/residency limitations. [Sites help](https://help.openai.com/en/articles/20001339-creating-and-managing-chatgpt-sites): access/authoring/privacy distinctions. [Data-protection responsibilities](https://help.openai.com/en/articles/20001340-chatgpt-sites-complying-with-data-protection-laws) and [privacy-notice guidance](https://help.openai.com/en/articles/20001341-chatgpt-sites-how-to-prepare-a-privacy-policy). [Sites terms](https://openai.com/policies/chatgpt-sites-terms/), July23,2026: financial-transaction and sensitive-data restrictions; recheck at launch.

[Use cases](https://developers.openai.com/plugins/plan/use-case), [tool design](https://developers.openai.com/plugins/plan/tools), [MCP server](https://developers.openai.com/plugins/build/mcp-server), [authorization](https://developers.openai.com/plugins/build/auth), [ChatGPT/MCP UI](https://developers.openai.com/plugins/build/chatgpt-ui), and [security/privacy](https://developers.openai.com/plugins/guides/security-privacy). Current documentation uses Plugins/MCP routes; do not rely on stale third-party Apps SDK tutorials.

[Responses](https://developers.openai.com/api/docs/guides/migrate-to-responses), [Agents SDK](https://developers.openai.com/api/docs/guides/agents/sdk), [Agents API](https://developers.openai.com/api/docs/guides/agents-api/overview), and [data controls](https://developers.openai.com/api/docs/guides/your-data). Application state, abuse monitoring and caching are separate; store=false is not a universal retention guarantee.

[platformdirs](https://platformdirs.readthedocs.io/en/latest/api.html), [pipx](https://pipx.pypa.io/stable/), [uv tools](https://docs.astral.sh/uv/guides/tools/), [PyInstaller](https://pyinstaller.org/en/stable/operating-mode.html): primary distribution/path references. Actual install/native qualification remains work, not verified in this audit.
