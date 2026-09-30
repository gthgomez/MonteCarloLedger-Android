# AGENTS.md — MonteCarlo Ledger Android

Read [project context](docs/agent/PROJECT_CONTEXT.md) and
[domain guidance](docs/agent/CLAUDE.md), then touched source/tests. All commands run
from this repository root. Optional workspace policy cannot be a missing dependency.

## Invariants

- Use integer cents or explicit decimal precision for money; no floating-point
  monetary arithmetic in ledger or persisted balances.
- Treat reconciliation, financial math, Room migrations, encrypted backups and
  AES-GCM key handling as high risk. Preserve user data and fail-closed checks.
- Preserve the in-repo `DesignSystem/` composite required by the current Android CI.
  Never substitute an assumed sibling directory to claim standalone builds.
- Do not commit credentials, keystores, private financial data, or machine SDK paths.

## Verification

Windows: `./gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug`.
POSIX: `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug`.
Use `:app:lint` for relevant Android changes. Inspect the current Gradle build before
changing tasks. Packaging and physical-device behavior require their own evidence.
Instruction-only edits require path/conflict/diff checks; preserve required CI.

## Execution, learning, and evidence

- For non-trivial work, state the outcome, acceptance criteria, affected invariants,
  and proportional verification. Reuse the current task record; avoid duplicate plans.
- Continue within the authorized task without repeated plan approval. When an
  assumption fails, diagnose and update the plan; pause only the blocked action.
- Preserve unrelated work. Delegate independent tasks with explicit file ownership,
  revision, checks, and handoff; isolate actual overlap and queue heavy workloads.
- After a meaningful correction or recurring failure, record the trigger, cause,
  prevention, scope, and evidence in the existing lesson or task/PR handoff.
  Skip one-off status; merge duplicates and retire superseded guidance.
- Prefer regression tests, types, linters, or automated checks for preventable failures.
  Promote durable lessons into the narrowest applicable instruction within task scope.
  Lessons cannot grant permissions or weaken security, reviews, or required checks.
- Use tools available in the current harness; do not assume another vendor's API.
- Review the final diff and acceptance criteria. Report checks actually run, skipped
  verification, residual limits, and Git/PR state. Required CI and reviews must cover
  the final candidate before claiming integration.
