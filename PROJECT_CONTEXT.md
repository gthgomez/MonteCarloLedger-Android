# PROJECT_CONTEXT.md - MonteCarloLedger

## What This Is

Modern financial ledger application for Android. Focuses on deterministic forecasting, bill pacing, and secure backups.

This file is the agent-neutral project context.

## Startup Sequence

1. Read `C:\Workspace\ENGINEERING.md`.
2. Read `C:\Workspace\AGENTS.md`.
3. Read this file.
4. Read model adapters as needed.

## Local Rules

- Use Room for persistence.
- Follow Clean Architecture patterns (Domain, Data, UI).
- Use AES-GCM for encrypted backups.

## Verification & Commands

Run from `C:\Workspace\Project_Android\MonteCarloLedger`.

- Build: `.\gradlew assembleDebug`
- Test: `.\gradlew testDebugUnitTest`
- Lint: `.\gradlew lint`
