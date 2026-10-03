# AGENTS.md — MonteCarlo Ledger Android

Read [project context](docs/agent/PROJECT_CONTEXT.md), then touched source/tests.
All commands run from this repository root. Optional workspace policy cannot be a
missing dependency.

## Invariants

- Use integer cents or explicit decimal precision for money; no floating-point
  monetary arithmetic in ledger or persisted balances.
- Treat reconciliation, financial math, Room migrations, encrypted backups,
  AES-GCM key handling, and manifest/permission changes (Play Store compliance)
  as high risk. Preserve user data and fail-closed checks.
- Preserve the in-repo `DesignSystem/` composite required by the current Android CI.
  Never substitute an assumed sibling directory to claim standalone builds.
- Do not commit credentials, keystores, private financial data, or machine SDK paths.

## Verification

Windows: `./gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug`.
POSIX: `./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug`.
Use `:app:lint` for relevant Android changes. Inspect the current Gradle build before
changing tasks. Packaging and physical-device behavior require their own evidence.
Instruction-only edits require path/conflict/diff checks; preserve required CI.

## Architecture and change discipline

For substantive code changes, identify the owning domain, contract, and callers;
search for existing rules before adding another formula, threshold, or schema fact.
Keep domain decisions out of presentation/transport and use narrow contracts.
Concretely: extend the relevant `domain/` or `processing/` owner under
`app/src/main/java/com/montecarlo/ledger/` for financial rules; persist through
the existing `data/LedgerRepository.kt` and Room contracts. Compose screens render
derived results — keep presentation-only formatting/layout there; do not copy
forecast, recurrence, budget, debt, or balance formulas into screens.
Engine convergence with the Python repository requires an explicit migration and
agreed cross-implementation fixtures; do not assert existing parity, replace
Kotlin calculations wholesale, or alter Room/backup contracts under this rule.
An owner can contain several cohesive modules; prefer simple functions/composition
and avoid speculative abstraction or sharing coincidentally similar code.

If a feature requires substantial consolidation or boundary repair, first make
the smallest behavior-preserving refactor in a separate PR. Otherwise implement
directly; contained fixes and instruction edits need no preliminary refactor.
Preserve outputs, errors, rounding, ordering, cancellation, and side effects;
use representative characterization/differential checks where coverage is weak.
Fix discovered bugs as explicit behavior changes. Add focused executable prevention
for demonstrated failures, without weakening existing gates. Audit painful domains
with paths/counts and compare the same measures after repair; avoid unrelated cleanup.

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
