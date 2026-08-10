# MonteCarloLedger Status

**Last verified:** 2026-08-10
**Status:** release candidate / local build verified
**Confidence:** high (unit + assembleRelease); medium (instrumented migration — not yet device-run)

## Purpose

Kotlin/Compose Android financial ledger app combining a 90-day cash flow engine with Monte Carlo simulations to detect overdraft risk, project safe-to-spend reserves, automate budget pacing, and simulate debt payoff strategies.

## Current State

Feature-complete through Phases 1–4 and P1–P5: Room DB v10→v11 64-bit `Long` cents precision, production package namespace `com.montecarlo.ledger`, and local release assembly (`assembleRelease` → unsigned APK). Room v12 adds a persisted `debts` table for debt management. Uses Room for offline ledger storage, AES-GCM backups with HMAC verification, Long cent math, 4-tier category rules, budget pacing, debt payoff simulator, and an Android Glance widget.

Not store-signed and not claimed Play-ready until signing, store listing, and instrumented migration evidence are in place.

## Verified Capabilities

- Clean Architecture under `com.montecarlo.ledger` with Room (SQLite) and no floating-point currency math.
- 64-bit `Long` cents across Room entities, DAOs, backup snapshot import/export, domain engines, view models, and money display paths.
- Room DB Migration v10 → v11 metadata version bump (`MIGRATION_10_11`) using SQLite’s native 64-bit `INTEGER` affinity (no-op SQL body; type change is Kotlin/Room-side).
- 90-day Monte Carlo cash flow engine (`MonteCarloEngine.kt`, `ForecastEngine.kt`) with deterministic seed capability.
- AES-GCM backup envelope with HMAC-SHA256 verification (`SecurityUtils.kt`) and `Long` balance export/import.
- Merchant auto-categorization (`CategoryRuleEngine.kt`) with 4-tier matching precedence.
- Proactive Overdraft Action Center (`OverdraftActionEngine.kt`).
- Budget pacing / safe-spend velocity alerts (`BudgetPacingEngine.kt`).
- Debt payoff simulator (`DebtPayoffEngine.kt`, `DebtPayoffScreen.kt`).
- Glance home screen widget (`MonteCarloLedgerGlanceWidget.kt`, `GlanceWidgetReceiver.kt`).
- Local release hardening: R8 minify + Compose mapping workaround; produces `app-release-unsigned.apk`.

## Recent Evidence

- 182 unit tests via `.\gradlew.bat :app:testDebugUnitTest --no-daemon` (BUILD SUCCESSFUL).
- Release APK via `.\gradlew.bat :app:assembleRelease --no-daemon` (BUILD SUCCESSFUL, unsigned).
- Room schemas for versions 9/10/11 live only under `app/schemas/com.montecarlo.ledger.data.AppDatabase/`.
- `MigrationTest.migrate10To11_updatesVersionAndPreservesData` fixed to match schema v10 columns and assert amount `> Int.MAX_VALUE` via `getLong` — **needs a device/emulator `connectedDebugAndroidTest` run** before treating as verified.

## In Progress

- Device/emulator instrumented verification of Room 10→11 migration test.
- Store signing / Play release packaging (out of scope for core Long + package work).

## Blockers

- None for local development builds.

## Verification Commands

- `.\gradlew.bat :app:testDebugUnitTest --no-daemon` (182 unit tests)
- `.\gradlew.bat :app:assembleRelease --no-daemon` (unsigned release APK)
- `.\gradlew.bat :app:connectedDebugAndroidTest --tests com.montecarlo.ledger.data.MigrationTest --no-daemon` (when device/emulator available)

## Evidence Sources

- [README.md](file:///C:/Workspace/Project_Android/MonteCarloLedger/README.md)
- [QA_CHECKLIST.md](file:///C:/Workspace/Project_Android/MonteCarloLedger/QA_CHECKLIST.md)
- [ROADMAP_HANDOFF.md](file:///C:/Workspace/Project_Android/MonteCarloLedger/ROADMAP_HANDOFF.md)
