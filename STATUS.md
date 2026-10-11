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

---

## WS5 Update — 2026-10-11 (signed-release plumbing + doc truth)

**Scope:** workstream WS5 (branch `ci/signed-release-candidate`, based on `origin/main` `6cadf72`).
This section is additive; the historical text above is preserved as written but is **superseded** on the points below.

### Corrections to previously recorded facts

- **Unit tests:** the "182 unit tests" figure above is stale. Measured on 2026-10-11:
  **368 tests** across 56 suite files, 0 failures / 0 errors / 0 skipped
  (`./gradlew --no-daemon :app:testDebugUnitTest`, exit 0; JUnit XML under `app/build/test-results/testDebugUnitTest/`).
- **Room DB version:** the DB is at **version 17** (`AppDatabase.kt`, `@Database(version = 17, exportSchema = true)`),
  not the "v10→v11 / v12" state described above. The v10→v11 Long-cents metadata bump and the v12 `debts`
  table are historical milestones; versions 13–17 were added afterwards.
- **Schema range:** exported schema JSON covers **versions 9–17** (`app/schemas/com.montecarlo.ledger.data.AppDatabase/`,
  files `9.json` … `17.json`), not "9/10/11".
- **Release signing:** a real signing path now exists (see below). The statement "Not store-signed" is still
  true **for the artifacts produced in this environment**, but it is no longer true that no signing plumbing exists.
- **Repository location:** the `C:\Workspace\Project_Android\MonteCarloLedger` / `.\gradlew.bat` paths above are a
  legacy Windows-local checkout. The canonical repo is `github.com/gthgomez/MonteCarloLedger-Android`; use
  `./gradlew` from the repo root.

### What changed in WS5

1. **Signed-release plumbing** — `app/build.gradle.kts` gains `signingConfigs.release`, fed from
   `ANDROID_KEYSTORE_PATH` / `ANDROID_KEY_ALIAS` / `ANDROID_KEYSTORE_PASSWORD` / `ANDROID_KEY_PASSWORD`
   env vars or a gitignored root `keystore.properties`. Applied to the release build **only when all four
   values are present**; otherwise the release build still succeeds and emits `app-release-unsigned.apk`.
   No keystore or secret is committed; `keystore.properties` is added to `.gitignore`.
2. **Versioning scheme** — `versionCode = 2`, `versionName = "1.1.0"`. Scheme documented in
   [docs/RELEASE.md](docs/RELEASE.md) (`versionName` = SemVer, `versionCode` = strictly increasing upload counter).
3. **Release CI gate** — new workflow `.github/workflows/android-release.yml` ("Android Release") runs
   `:app:assembleRelease --no-daemon` (no `continue-on-error`). The required **"Android CI"** check and its
   workflow file are untouched.
4. **Diagnostics** — Gradle configuration now logs `Release signing: ENABLED|DISABLED …` once per build,
   so an unsigned artifact can never be mistaken for a signed one.
5. **Docs** — `PRIVACY.md` placeholders replaced with the live public URL
   <https://github.com/gthgomez/MonteCarloLedger-Android/blob/main/PRIVACY.md> (reachable, HTTP 200 on 2026-10-11);
   `QA_CHECKLIST.md` now records real executed results and marks device/Play items NOT RUN.
   New `docs/RELEASE.md` is the authoritative release/signing document.

### Verification evidence (2026-10-11, Linux + Android SDK 36)

| Check | Command | Exit code | Result |
|-------|---------|-----------|--------|
| Release + unit tests | `./gradlew --no-daemon :app:assembleRelease :app:testDebugUnitTest` | `0` | BUILD SUCCESSFUL (7m 7s); 368/368 tests pass; `app-release-unsigned.apk` (5.35 MB) |
| Signed release path | `./gradlew --no-daemon :app:assembleRelease` with throwaway `ANDROID_KEYSTORE_*` env key | `0` | `app-release.apk` produced; `apksigner verify --print-certs` PASS |
| Workflow YAML | `python3 -c "import yaml; yaml.safe_load(open('…'))"` on both files | `0` | both parse |
| Privacy URL | `curl -sI https://github.com/gthgomez/MonteCarloLedger-Android/blob/main/PRIVACY.md` | `0` | HTTP 200 |

### Still blocked (unchanged, human-owned)

- No production release keystore exists in the repo (by design). A **signed** production artifact therefore
  could not be produced here; the signed-path proof used a throwaway test key that lives only in the
  session scratchpad and is not committed. Producing a real signed artifact requires the owner's keystore +
  Play credentials (see [docs/RELEASE.md](docs/RELEASE.md) §4).
- Instrumented migration/UI verification still needs a device/emulator.

