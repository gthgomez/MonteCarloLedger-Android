# Factory plan — Handoff remaining items (Tier A)

**Project:** MonteCarloLedger  
**Source:** `handoff-20260718-184716.md` Next actions  
**Date:** 2026-07-18  

## Goal

Implement remaining handoff next-actions after Phase 0–2 (backup/payday/import + monthly plan + provisional safe-to-spend).

## Decisions locked

- Soft **watchlists** (spend vs limit, no hard block at checkout). [DECIDED]
- Forecast-first local-first cash-flow planner. [DECIDED]
- Backup schema v2 already present; extend for category budgets if stored. [DECIDED]
- Display: integer cents only — no `cents / 100.0` Double. [DECIDED]
- Package rename `com.example.app` is **out of scope** for this tier (too large; separate campaign). [CONSTRAINED]

## Tier A slices

### A1 — Category budgets / soft watchlists

Room entity + migration (v9→v10) + repository APIs + Planning/Dashboard spend-vs-limit UI + unit tests.

### A2 — App lock throttle

Attempt counter + exponential backoff on failed PIN; surface lockout UI. Optional biometric gate if AndroidX Biometric can be added with minimal blast radius; otherwise backoff only is enough for DoD.

### A3 — centsToDisplay helper

Central integer-safe currency display; replace remaining `cents / 100.0` / Double `formatCurrency` display paths.

### A4 — Room schema export + migration tests + release minify

`exportSchema = true`, ship `schemas/`, MigrationTestHelper (or equivalent) for latest migration; enable release minify.

## Exit gate

```text
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```
