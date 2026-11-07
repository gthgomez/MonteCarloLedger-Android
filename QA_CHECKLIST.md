# Monte Carlo Ledger — QA Checklist

Execution record for this revision:
commit `6cadf72` + WS5 working tree · run 2026-10-11 · Linux/CI-hosted, **no device/emulator attached**.

| Legend | Meaning |
|--------|---------|
| `[x]` | Verified in this run, with the named evidence. |
| `[ ] NOT RUN` | Not executable in this environment (device, emulator, Play Console, or manual UI). Reason stated. |

Commands actually executed for the build/unit/CI-level items:

| Command | Exit code | Result |
|---------|-----------|--------|
| `./gradlew --no-daemon :app:assembleRelease :app:testDebugUnitTest` | `0` | BUILD SUCCESSFUL in 7m 7s; 368 tests, 0 failures, 0 errors, 0 skipped (56 suite files in `app/build/test-results/testDebugUnitTest/`); `app/build/outputs/apk/release/app-release-unsigned.apk` produced |
| `./gradlew --no-daemon :app:assembleRelease` (throwaway test keystore via `ANDROID_KEYSTORE_*` env) | `0` | `app-release.apk` produced; `apksigner verify` PASS |

---

## 1. Financial Precision

- [x] Decimal/integer cents used for all currency — no floating point
      — evidence: `MoneyArithmeticTest`, `MoneyDisplayTest`, `DollarParseResultTest`, `BankBalanceParserTest` pass; static scan of `processing/`, `domain/`, `data/`, `util/` finds no `Float`/`Double` in monetary storage or arithmetic (18 `Double` hits are probabilities/percentiles/normalized UI fractions). `MoneyUtils.kt` documents the Long-cent rule explicitly.
- [x] Rounding is deterministic (consistent tie-breaking rule)
      — evidence: `MoneyArithmeticTest.scaleCentsByPercent_usesDeterministicCentRounding`, `MoneyArithmeticTest.monthlyInterestCents_usesAprBasisPoints` pass.
- [x] Negative amounts handled correctly
      — evidence: `FinancialPropertyTest` (random signed amounts; totals move by exactly the delta), `ForecastEngineTest.calculateForecastSummary_openingNegativeEmptyEvents_stillNegativeFromAsOf` pass.
- [ ] NOT RUN — Zero-balance edge case **renders** correctly. Reason: rendering assertion requires a device/emulator or a Compose UI test host; no such host available. Numeric zero-balance paths are covered by `ForecastEngineTest`/`LedgerRepositoryTest`, but the rendering claim is unverified.
- [x] Large-value precision maintained (no overflow or rounding errors)
      — evidence: `MoneyDisplayTest` near-`Int.MAX_VALUE` display cases, `ContractSemanticsTest` (`ContractMoney.checkedAdd(Long.MAX_VALUE, 1L)` overflow guard), `MonteCarloEngineCategoryVariationTest` pass.

**Pass criteria: 5/5** → 4/5 executed and passing; 1 item requires a device.

---

## 2. Ledger Core

- [ ] NOT RUN — Transaction entry works correctly (UI flow). Reason: the entry flow is exercised by `app/src/androidTest/.../AddTransactionScreenTest.kt`, which requires a device/emulator (`connectedDebugAndroidTest`). Repository-level mutation is covered by `LedgerRepositoryProductDepthTest` / `FinancialPropertyTest` (passing), but the UI flow is unverified here.
- [x] Balance calculation matches expected values
      — evidence: `LedgerRepositoryTest`, `LedgerRepositoryBalanceStateTest`, `FinancialPropertyTest` (add-then-delete restores starting balance), `LedgerRepositoryProductDepthTest` (credit/cash deltas) pass.
- [x] Reconciliation matches Room DB state
      — evidence: Robolectric-backed Room tests in `data/` (`LedgerRepositoryTest`, `LedgerRepositoryProductDepthTest`, `LedgerRepositoryBalanceStateTest`, `FinancialPropertyTest`) read back persisted state and pass.
- [ ] NOT RUN — Category filtering works (in-app list filter). Reason: no unit/Robolectric test asserts the filter; requires device/emulator UI run. Categorization itself is covered by `CategoryRuleEngineTest` and `TimelineServiceCategoryTest` (passing).
- [ ] NOT RUN — Date range filtering works. Reason: no unit/Robolectric test covers the date-range filter; requires device/emulator UI run.
- [ ] NOT RUN — Search returns correct results. Reason: no unit/Robolectric test covers ledger search; requires device/emulator UI run.

**Pass criteria: 6/6** → 2/6 executed and passing; 4 items are device/UI-only.

---

## 3. Monte Carlo Forecast

- [x] 90-day forecast uses deterministic seed
      — evidence: `MonteCarloEngineTest` (explicit `seed = 1` / `seed = 999` runs) passes.
- [x] Repeatable results with same seed
      — evidence: `MonteCarloEngineTest` asserts identical median / worst-10 / best-90 / negative-probability across two runs with same seed; `MonteCarloCalibratorTest` passes.
- [x] Configurable parameters work
      — evidence: `MonteCarloEngineTest`, `MonteCarloCalibratorTest`, `MonteCarloCategoryVariationTest`, `MonteCarloEngineCategoryVariationTest`, `ContractSimulationInvariantTest` pass.
- [ ] NOT RUN — Visual chart renders correctly. Reason: requires device/emulator rendering; chart geometry helpers only are unit-tested (`DashboardScreenTest.normalizedSparklineX_*`).
- [x] Edge cases handled (empty ledger)
      — evidence: `MonteCarloCalibratorTest.calibrate_emptyLedgerFallsBackToDefaults`, `MonteCarloInsightsTest.emptyLedgerProducesNoInsights`, `ForecastEngineTest.calculateSafeToSpend_emptyEvents_returnsBalance`, `CategoryBudgetTrackerTest.compute_emptyBudgetsReturnsEmptyList` pass.

**Pass criteria: 5/5** → 4/5 executed and passing; 1 item requires a device.

---

## 4. Encrypted Backups

- [x] AES-GCM export works
      — evidence: `SecurityUtilsTest.encryptedBackup_usesVersionedEnvelopeAndRoundTrips`, `encryptWithHmac_roundTripsAndVerifiesIntegrity`, `BackupExportTest` pass.
- [x] Import restores data correctly
      — evidence: `BackupRestoreIntegrationTest.exportThenRestoreIntoDirtyDatabaseReproducesTheLedger`, `BackupRoundTripTest.roundTrip_preservesEveryPersistedField`, `FinancialPropertyTest.backupRestore_preservesLogicalEqualityAcrossARandomLedger` pass.
- [x] Wrong password rejected
      — evidence: `SecurityUtilsTest.verifyIntegrity_detectsWrongPasswordViaAesGcm` passes.
- [x] Corrupted file rejected
      — evidence: `SecurityUtilsTest.verifyIntegrity_detectsTamperedPlaintext`, `SecurityUtilsTest.verifyIntegrity_failsClosedOnHeaderIterationsAboveTheSupportedBound`, `BackupRoundTripTest.malformedRootFailsClosed` pass.

**Pass criteria: 4/4** → 4/4 executed and passing.

---

## 5. UI

All items in this section require a device/emulator run (`connectedDebugAndroidTest`) or manual QA. The instrumented suite exists (13 classes in `app/src/androidTest/`), but no device/emulator is available in this environment.

- [ ] NOT RUN — Compose screens render correctly. Reason: device/emulator required.
- [ ] NOT RUN — Navigation works. Reason: device/emulator required.
- [ ] NOT RUN — State preserved on rotation. Reason: device/emulator required (manual or instrumented).
- [ ] NOT RUN — Dark theme renders correctly. Reason: device/emulator (or screenshot test infra) required.
- [ ] NOT RUN — Accessibility labels present. Reason: manual/instrumented accessibility audit required.
- [ ] NOT RUN — No crashes. Reason: requires an interactive session; unit/CI level cannot assert this.

**Pass criteria: 6/6** → 0/6 executed. **Blocked on device.**

---

## 6. Permissions

Source of truth: `app/src/main/AndroidManifest.xml` (read directly).

- [x] Storage access scoped to backups only
      — evidence: the manifest declares **no** `READ_/WRITE_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE`; exports use the Storage Access Framework (user-picked URI). Verified by manifest inspection.
- [x] No internet permission
      — evidence: no `android.permission.INTERNET` in the manifest (grep confirmed). Matches PRIVACY.md's "local-only" claim.
- [x] No unnecessary permissions
      — evidence: the only declared permission is `POST_NOTIFICATIONS`, used for local bill/risk reminders; `com.android.vending.BILLING` is commented out pending Play Billing. Reviewable statically; no runtime prompt behavior was tested on device.

**Pass criteria: 3/3** → 3/3 verified statically.

---

## 7. Go / No-Go Gate

**Ship when all items pass.**

- [x] No floating-point currency in any file
      — evidence: static scan (see §1). All `Double`/`Float` occurrences in money-adjacent packages are probabilities, percentiles, calibration statistics (rounded back to `Long`) or normalized UI fractions — none persist or accumulate currency.
- [ ] NOT RUN — Room migration doesn't destroy data. Reason: the migration tests (`app/src/androidTest/.../MigrationTest.kt`, `MigrationMatrixTest.kt`) are **instrumented** and require a device/emulator. Room schema JSON for versions 9–17 is present and the DB is at version 17, but the destructive-migration guarantee is unproven until `connectedDebugAndroidTest` runs.
- [x] Forecast deterministic with same seed
      — evidence: `MonteCarloEngineTest` same-seed repeatability assertions pass.

**Result: 2/3 executed and passing; 1 device-only item outstanding → NOT GO for public release.**

---

## Outstanding before release (owner action)

| Item | Why it is not done | Owner |
|------|--------------------|-------|
| `connectedDebugAndroidTest` (migration + UI suites) | No device/emulator in this environment | Repo owner |
| Manual UI pass (§5, §2 filters/search) | Needs a running app | Repo owner / tester |
| Play Console data-safety form, store listing, privacy URL entry | Console access required; policy URL is now public (see PRIVACY.md) | Repo owner |
| Release keystore / signing secrets | Human-held secret; never committed (see docs/RELEASE.md) | Repo owner |
