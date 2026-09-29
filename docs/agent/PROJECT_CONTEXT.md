# PROJECT_CONTEXT.md - MonteCarloLedger

## What This Is

Modern financial ledger application for Android. Focuses on deterministic forecasting, bill pacing, and secure backups.

This file is the agent-neutral project context.

## Startup Sequence

1. Read `CLAUDE.md` in this directory — project-local agent guidance.
2. Read this file (`PROJECT_CONTEXT.md`) — directory map and invariants.
3. Read `C:\Workspace\Project_Android\PROJECT_CONTEXT.md` — workspace-wide context.
4. Read `C:\Workspace\Project_Android\CLAUDE.md` — behavioral rules and Android patterns.
5. Review `C:\Workspace\Project_Android\tasks\lessons.md` if it exists.

## Local Rules

- Use Room for persistence.
- Follow Clean Architecture patterns (Domain, Data, UI).
- Use AES-GCM for encrypted backups.

## Verification & Commands

Run from `C:\Workspace\Project_Android\MonteCarloLedger`.

- Build: `.\gradlew assembleDebug`
- Test: `.\gradlew testDebugUnitTest`
- Lint: `.\gradlew lint`
