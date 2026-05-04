# PROJECT_CONTEXT.md - MonteCarlo Ledger Android App Module

## What This Is

Android application module for `MonteCarlo-Ledger-app`.

## Startup Sequence

1. Read `C:\Workspace\ENGINEERING.md`.
2. Read `C:\Workspace\AGENTS.md`.
3. Read `C:\Workspace\MonteCarlo-Ledger-app\PROJECT_CONTEXT.md`.
4. Read this file.

## Architecture & Invariants

- Parent project context remains authoritative for financial precision rules and build/test guidance.
- Keep financial calculations deterministic and precision-safe.
- Do not alter persistence schema, rounding, or forecast behavior without targeted verification.

## Verification & Commands

- From parent root: `.\gradlew.bat assembleDebug`
- From parent root: `.\gradlew.bat test`
- From parent root: `.\gradlew.bat connectedAndroidTest` when device/emulator verification is needed.
