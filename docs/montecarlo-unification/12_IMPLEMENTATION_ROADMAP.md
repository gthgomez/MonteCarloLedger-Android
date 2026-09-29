# Implementation roadmap — bounded logical stages

These IDs are **not GitHub PR numbers**. Each stage may split into smaller child PRs while preserving its acceptance gate. The machine-readable DAG is in artifacts/pr-dependency-graph.json. The expanded downloadable packet contains a longer per-stage checklist; this repository edition preserves the same architecture, IDs, sequencing and first-stage mission.

## Global stage contract

Every stage inherits exact cents/checked overflow, explicit dates, no data loss/duplicate posting, stable identity/revision/receipts where applicable, and honest capability/qualification. Each implementation PR reports base/head, paths, model/contract/schema/fixture versions, tests actually run, old/new behavior, migration/compatibility/security effects, remaining findings and next eligible stage. Independent fresh-context review inspects financial meaning, not auto-regenerated goldens. Follow applicable human approval for high-risk money/Room/security changes.

“Python portable target” means D00's selected location: MIT only after a named grant, otherwise a separately licensed proprietary package. It never implicitly authorizes Android-derived code in MIT. Existing MIT bootstrap/atomicity/paths work is independent of that gate.

Verification profiles used below:

- **P:** python -m pytest -q; python -m ruff check .; python -m pyright; git diff --check. Add each named regression suite before the full run.
- **A:** ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon; git diff --check.
- **AM:** A plus ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.montecarlo.ledger.data.MigrationMatrixTest --no-daemon on a recorded emulator/API/JDK.
- **PC/AC:** P/A plus the conformance commands in05 and an exact fixture-digest report.
- **WEB:** npm ci; npm run typecheck; npm run lint; npm run test -- --run; npx playwright test; npm run build; git diff --check. Introduce these scripts with the proposed React/TypeScript/Vite presentation build; pin versions after runtime qualification. No financial reducer in frontend.
- **DIST:** python -m build; python -m twine check dist/*; isolated pip/wheel, pipx, uv and native OS artifact smoke, then P. Declare supported architectures and signing evidence.
- **DOC:** schema/JSON/link/DAG checks and git diff --check; owner approval where stated, not a fabricated product test run.

New paths/tests below are proposals and become runnable in their stage. One owner allocates migrations per repository. Parallel work means isolated worktrees with stable interfaces, not simultaneous edits to the same financial file.

## Safety stages — can start now

### P00_BOOTSTRAP — Python MIT; no prerequisites

Files: db_manager.py, schema.sql, new tests/test_migration_bootstrap.py and existing persistence-orphan regressions. Read version/fingerprint before current DDL. Red tests: legacy duplicate nominal keys, future/unknown schemas, malformed money/links, fresh/repeated init, valid v7/v8/v9 upgrades and failure injection. Ambiguous data fails before writes; valid version-specific old dollar formats use explicit bounded migration rules, not blanket rejection or float-to-int truncation. No new schema version for routing alone. Compatibility risk: unsafe legacy data now requires repair rather than silently opening. Security: private consistent backups/redacted reports. Acceptance: no automatic zero/default/dedup loss, recoverable source, correct version markers. Verify P and focused tests. Android safety/D00 parallel; P01/P02 wait on this shared file. See14 for exact mission.

### P01_ATOMIC_POSTING — Python MIT; after P00

Files: db_manager.py, workflow_payments.py, workflow_income.py, domain_rules.py, new atomic-posting tests. One UOW includes receipt/ledger/cache/link/cursor. Allocate additive operation-receipt migration; keep existing IDs and initial exact-amount bill policy. Test crash at every write, retry/conflicting key, invalid amount before insert and same-name distinct sources. Acceptance: one financial movement per operation, explicit actual date, no partial posting. Risk is behavior/storage compatibility; no UI rewrite. Verify P. Serialize migration/db_manager owner with P02/P04.

### P02_USER_DATA — Python MIT; after P00

Files: db_manager.py, cli.py, api.py, new storage/paths.py, pyproject.toml, path tests. OS paths/overrides, explicit init/migrate and side-effect-free help/read/server opening. No automatic move/delete: adopt copies after confirmation. Test all OS paths/permissions/Unicode/unwritable/read-only installed tree and multiple legacy candidates. Acceptance: no package/cwd data and resources/subpackages included. Security: ACL/ownership/traversal/redacted paths. Verify P. Coordinate db_manager with P01.

### A00_RESTORE_VALIDATION — Android; no prerequisites

Files: ui/BackupImport.kt, ui/BackupExport.kt, data/LedgerRepository.kt, new backup-preflight tests. Reject malformed required arrays/money/IDs/refs before replacement. Valid old1–6 defaults only through explicit version adapters. No Room bump unless a separately reviewed receipt schema requires it. Test rejection leaves data/lock unchanged and bounded input. Acceptance: no wrong-type-to-empty wipe. Verify A. A01 shares repository file and follows sequentially.

### A01_RECONCILIATION — Android; after A00

Files: LedgerRepository.kt, AccountEntity.kt, DashboardDeriver.kt, reconciliation tests. Default selection cannot grant observed confirmation; expose unresolved account/global mismatch without invented adjustment. Prefer behavior-only fix; additive observation schema belongs A06. Test cash/credit/default changes. Compatibility: some formerly falsely confirmed states now show unresolved. Verify A; no network change.

### A02_CALIBRATION_HOTFIX — Android; no prerequisites

Files: MonteCarloCalibrator.kt and MonteCarloCategoryVariationTest.kt. Replace fixed positive range with symmetric bounds and meaning-based tests; retain explicit sparse/constant fallback policy. No history edits; record model behavior change. Acceptance: regression fails on old range..range, both signs possible, no debt/UI rewrite. Verify A. Independent of restore/reconciliation and A03.

### A03_RECOMMENDATION_HONESTY — Android; no prerequisites

Files: OverdraftActionEngine.kt and its UI consumers (locate exact usages), new action-evidence tests. Omit uncomputed numerical risk reduction; hypothetical candidates remain labeled. No schema changes. Tests prevent zero/fabricated substitute or implied payee agreement/liquidity. Acceptance: every number has computed receipt or is absent. Verify A. Independent file owner from A02.

### A04_MIGRATION_CI — Android; no prerequisites

Files: .github/workflows/android-ci.yml, existing MigrationMatrixTest.kt and exported app/schemas, STATUS.md. Run representative9→16/legacy chains in emulator CI; preserve large money, links, clearing and reconciliation defaults. No product migration changes unless separately isolated by a failing test. Acceptance: recorded device/API/JDK/revision reports and accurate status; synthetic DBs only. Verify AM. Can build test infrastructure while domain decision proceeds.

## Contract and storage

### D00_LICENSE — owner decision; no prerequisites

Record LICENSE_DECISION.md in this packet: pathA/B, exact granted/excluded files, provenance/contributor/dependency rights, fixture license and portable package location/terms. No root license changes by implication. Acceptance is explicit owner decision; DOC. Parallel with safety. Blocks cross-boundary reuse, not independent fixes.

### C00_CONTRACT — selected contract distribution; after D00

Files: new conformance/v1/contract.md, manifest.json, fixtures.json, schemas. Review exact expected behavior, semantic versioning, capabilities and old/new differences. No automatic DB migration. Acceptance: approved legally consumable bundle/digest and no accidental equivalence of different percentile metrics. DOC. One contract owner; downstream implementations can parallelize after publication.

### P03_CONFORMANCE — Python portable target; after C00,P00

New tests/conformance/test_contract.py and conformance adapter/resources; pyproject discovery. Separate characterization/target reports, reject missing mandatory cases and identify current clock/horizon/percentile mismatches. No storage changes. Synthetic fixtures only, pinned digest. Acceptance: runner detects known nonconformance and advertises only passed profile. PC.

### A05_CONFORMANCE — Android; after C00,A02

New pure domain/contract DTOs, ContractFixtureTest.kt, fixture resources and build wiring. No Compose/Room in pure DTOs; numeric JSON money cannot round through Double. No migration. Acceptance: same digest and explicit missing/mismatched cases. AC. Parallel with P03.

### P04_DOMAIN_STORAGE — Python portable target; after P03,P01,P02

New domain/application/storage modules; db_manager compatibility; migration tests. Add stable IDs, account/clearing/review/observation/receipt state, preserving old IDs and unresolved cache gaps. Tests cover UUID retry, balanced transfer, pending replacement and no guessed adjustment. High-risk additive migration; split identity/account/observation child PRs as needed. Acceptance: one balance owner per account and immutable snapshot service. PC. Serialize migration ownership.

### A06_DOMAIN_STORAGE — Android; after A05,A01,A04

AppDatabase.kt, LedgerRepository.kt, TransactionEntity.kt, BillOccurrenceEntity.kt, new application services and exported schema. Allocate next migration after actual branch check. All entry paths share account/UOW; preserve unknown account/paid-null legacy states. Tests: same-name income, refresh identity, credit refund, single liability counting and every supported historical chain. Acceptance: no global cache as independent truth. AC+AM. No concurrent Room version allocation.

## Financial behavior and portability

### P05_DETERMINISTIC — Python portable target; after P04

budget_engine.py, timeline_service.py, forecasting.py/new engine/forecast.py and fixtures. Half-open/anchor/overdue/hold/phase/initial-negative/scope tests. No historical money rewrite; version model and temporary legacy profile. Acceptance: exact deterministic fixtures, pure checked reducer and qualified headroom/windows. PC. Parallel with A07.

### A07_DETERMINISTIC — Android; after A06

RecurrenceMath.kt, TimelineService.kt, ForecastEngine.kt, DashboardDeriver.kt. Same exact deterministic profile; no unconfirmed arrears income or pooled masking; visible intraday/qualification differences. No history mutation or network. Acceptance: dashboard consumes canonical results. AC.

### P06_PORTABILITY — Python portable target; after P05

New application/import_export.py, storage/backup.py, adapters/imports and tests. Strict versioned string-money/ID/ref restore, confirmed digest/revision, idempotent import and recovery. Approved Android adapters only under D00. Preserve source and old valid formats. Acceptance: semantic round trip above2^53, invalid input cannot write, derived summaries never authoritative. Security: private files/keys/resource bounds; crypto separate. PC.

### A08_PORTABILITY — Android; after A07,A00

BackupExport.kt/BackupImport.kt/LedgerRepository.kt and tests; SecurityUtils.kt only for separately reviewed compatibility. New interchange version independent of Room. Keep valid1–6 readers and local secret exclusions. Acceptance: Android↔canonical↔Python exact money/ID/link/state round trip, rejected restore preserves target. AC+AM where schema changes. Parallel with manual simulation after stable DTOs.

### P07_STOCHASTIC — Python portable target; after P05

risk.py, monte_carlo_config.py/new engine/simulation.py and conformance. Shared reducer, explicit drivers, full requested horizon, named min/end/daily metrics, no clock, stable common draws. Test tapes, zero noise, empty events, opening negative, chart/batch invariance and native law/replay. No ledger edits; model version changes. Acceptance: all advertised stochastic profiles pass and invalid work fails. PC plus declared statistical lane.

### A09_STOCHASTIC — Android; after A07,A02

MonteCarloEngine.kt/ForecastEngine.kt/fan-chart consumers and tests. Remove duplicate reducer, hardcoded daily horizon/phase ambiguity and unchecked aggregate narrowing. Same tape/zero-noise results; chart switch cannot change financial answer. No history migration. Acceptance: requested horizon and correct quantile semantics. AC plus statistical lane.

### P08_CALIBRATION — Python portable target; after P07,P06

New engine/calibration.py, domain/coverage.py and tests. Covered zero versus missing/partial/future months, category bootstrap, matched income admission and explicit units. Add coverage provenance if needed, never infer full history from transaction endpoints. Acceptance: reported sample/exclusion/fallback/model; no double-counted shocks or false personalized certainty. PC. Local histories remain private.

### A10_CALIBRATION — Android; after A09,A08

MonteCarloCalibrator.kt, BudgetPacingEngine.kt, DashboardDeriver.kt and tests. Approved coverage-aware model replaces heuristic units/ranges; pacing has account/clearing/coverage policy. Add metadata only, preserve history. Acceptance: same calibration fixtures and explicit manual fallback; no generalized correlation magic. AC.

### P09_DEBT_BUDGETS — Python portable target; after P05,P07

New engine/debt.py, engine/budgets.py/domain terms and tests. Installment/revolving minimum, APR0/unknown, negative amortization/null nonconvergence, transfer/refund/goal/reservation behavior. Add terms without duplicate liability balance. Cash guard includes ordinary minima with extra0; obligation ID dedupe. Acceptance: exact conservation and named monthly-simple qualification. PC. Split budget/debt child PRs; no money execution.

### A11_DEBT_BUDGETS — Android; after A07,A09,A03

DebtPayoffEngine.kt, MonthlySpendingPlan.kt, OverdraftActionEngine.kt, BudgetPacingEngine.kt and consumers. Fix skipped occurrence accounting and obligation-ID matching; computed action benefit needs baseline/proposal receipt. Preserve historical values. Acceptance: exact debt/budget fixtures, payoff null if not converged, no heuristic numeric savings. AC. Separate arithmetic and unrelated UX refactors.

## Surfaces, release and retirement

### P10_CLI — MIT shell plus licensed adapter; after P05,P06

cli.py/new adapters/cli.py, workflows facade, pyproject and command tests. Full command/JSON/no-input/exit/confirmation contract; legacy interactive mode remains. No new schema. Test outside checkout and unsupported capability behavior. Acceptance: machine-readable results and no forced prompts/writes on help. P/PC. Redacted errors/password handling.

### P11_SERVICE — Python portable target; after P05,P07,P06

api.py/new application/snapshot.py/adapters/http.py and service/security tests. Stateless calculation schemas and budgets, consistent snapshot, no startup writes; local authorization separate from hosted router. No permanent hosted ledger DB. Acceptance: TLS/auth/origin/host/result isolation and body-log evidence, no unchanged public local API. P/PC plus security suite. Debt route waits for P09.

### D01_PRIVACY — product/security decision; after C00

PRIVACY_DECISION.md, relevant notices/SECURITY and deployment data-flow settings. Review fields/destinations, region/retention/analytics/auth/deletion/incident support and terms with legal review where needed. No persistence added. Acceptance: explicit approve/decline real-data hosted processing with truthful claims. DOC plus privacy canaries. Negative decision leaves local/demo products valid.

### S00_DEMO — Site presentation; after C00,P07

New site/package.json, src/app.tsx/components Overview/Timeline/ScenarioComparison/Assumptions, engine-generated synthetic bundles and tests. React/TS/Vite presentation only, exact versions/runtime validated at implementation. No user data/migration. Acceptance: synthetic labels, preset engine receipts, accessibility and no uncomputed arbitrary inputs. WEB. Can design from approved bundles while service/privacy proceeds.

### M00_MCP — licensed portable adapter; after P11,D01

New adapters/mcp.py, tool descriptions, tool/authorization tests. Four goal tools, text+structured receipt, bounded synthetic anonymous mode; proper OAuth/resource/tenant checks for personal access. No stored ledger. Acceptance: selection/negative prompts, no mutation/exfiltration/localhost tunnel. P plus MCP integration/auth evals.

### P12_RELEASE — distribution; after P10,P07

pyproject, new release workflow/packaging/tests/distribution, README. Actual wheel/sdist/pipx/uv/native artifacts per declared OS/arch; data survives upgrade and stays out of package. No financial schema change. Acceptance: install/resource/permission/restore smoke, hashes/license/SBOM/signing status, no artifactless installability claim. DIST. Signing/publishing secrets protected; independent OS lanes parallel.

### S01_EPHEMERAL — Site/service; after S00,P11,D01

New site/scenarios/data-disclosure/privacy tests and approved HTTP adapter. Exact transmission preview, minimal in-memory request, no default financial localStorage/D1/R2, clear/stale/error tests. No permanent ledgers. Acceptance: actual backend receipt, approved retention disclosure and no real-payment function. WEB+service/security tests.

### M01_UI — MCP presentation; after M00

New mcp-ui/src, renderer adapter and token/bridge tests. Authorized expiring verified result token, optional MCP Apps resource UI; text survives. No permanent ledger state. Acceptance: bridge, forged/other-user/expired token, CSP/escaping/accessibility; no model-recomputed numbers. WEB+host integration tests.

### P13_RETIRE — Python portable target; after P08,P09,P12,P11

risk/forecast/timeline/db_manager facades and docs. All supported paths use canonical services; preserve migrations and valid old backup readers. No premature deletion of recovery data. Acceptance: no independent old reducer/cache authority and current evidence docs. PC+distribution compatibility. Require at least two conforming releases and explicit compatibility policy before removing legacy adapters.

### A12_RETIRE — Android; after A08,A10,A11

processing, DashboardDeriver, LedgerRepository and status/QA/privacy/security docs. Remove obsolete runtime authority, not supported migrations/ID mappings/readers. Acceptance: one Kotlin path per capability, full migration/backup/financial evidence, offline posture and responsible support policy. AC+AM. Keep visual polish separate from high-risk retirement.
