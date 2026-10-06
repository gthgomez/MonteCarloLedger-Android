# PROJECT_CONTEXT.md - MonteCarloLedger

## What This Is

Modern financial ledger application for Android. Focuses on deterministic forecasting, bill pacing, and secure backups.

This file is the agent-neutral project context.

## Startup Sequence

1. Follow [repository-root AGENTS.md](../../AGENTS.md), skipping loaded guidance.
2. Read this context and touched source/tests.
3. Parent workspace context is optional. Do not require a fixed Windows path.

## Local Rules

- Use Room for persistence.
- Follow Clean Architecture patterns (Domain, Data, UI).
- Use AES-GCM for encrypted backups.

## Verification & Commands

Run from the repository root. Use `./gradlew.bat` on Windows and `./gradlew` on POSIX.

- Unit tests: `./gradlew --no-daemon :app:testDebugUnitTest`
- Debug build: `./gradlew --no-daemon :app:assembleDebug`
- Android lint when relevant: `./gradlew --no-daemon :app:lint`

CI requires the in-repo `DesignSystem/` composite. Tests/builds do not prove a
physical-device flow, financial parity with another repo, or signed release status.
