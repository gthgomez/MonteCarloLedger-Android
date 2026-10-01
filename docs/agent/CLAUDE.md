# CLAUDE.md — MonteCarlo Ledger App

## Context Stack

1. Follow [repository-root AGENTS.md](../../AGENTS.md), skipping loaded guidance.
2. Read adjacent [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) and touched source/tests.
3. Parent workspace context and lessons are optional when actually present;
   a standalone clone does not require a particular drive or sibling checkout.

## Core Directives

1. **Financial Precision:** All calculations must use exact precision (e.g., `Decimal` types or integer cents). Do not use floating-point math for currency.
2. **Security:** Maintain strict boundaries for any user data.
3. **Verification:** Rely on the local test suite for validation before considering any task complete.

## High-Risk Zones

- Financial math, balances, reconciliation — any calculation error creates incorrect ledger state.
- Room schema — destructive migrations lose user financial data.
- Encrypted backups — AES-GCM key management and integrity checks.
- Manifest and permissions — changes affect Play Store compliance.

## Verification

Run from the repository root. Windows uses `./gradlew.bat`; POSIX uses `./gradlew`.
- Unit tests: `./gradlew --no-daemon :app:testDebugUnitTest`
- Debug build: `./gradlew --no-daemon :app:assembleDebug`
- Relevant Android lint: `./gradlew --no-daemon :app:lint`
- Preserve the in-repo `DesignSystem/` build included by CI.
- Device, release-signing and packaging claims need corresponding evidence.
- Instruction-only edits need path/conflict/diff checks, not unrelated builds.
Never claim a check passed unless it actually ran and passed.

## Financial domain ownership

- Inspect `app/src/main/java/com/montecarlo/ledger/`: extend the relevant
  `domain/` or `processing/` owner for financial rules; use the existing
  `data/LedgerRepository.kt` and Room contracts for persistence.
- Compose screens render derived results. Keep presentation-only formatting/layout
  there; place financial decisions in their current tested owner rather than copying
  forecast, recurrence, budget, debt, or balance formulas into screens.
- Preserve exact money, date ordering, rounding, reconciliation, and failure behavior
  during structural moves; use representative fixtures and seeds where supported.
  Bugs and migration changes are explicit behavior changes with their own evidence.
- Engine convergence with the Python repository requires an explicit migration and
  agreed cross-implementation fixtures. This guidance does not assert existing
  parity, replace Kotlin calculations wholesale, or alter Room/backup contracts.
