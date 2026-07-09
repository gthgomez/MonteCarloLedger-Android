# CLAUDE.md — MonteCarlo Ledger App

## Context Stack

1. Read this file (`MonteCarloLedger/CLAUDE.md`) — project-local agent guidance.
2. Read `MonteCarloLedger/PROJECT_CONTEXT.md` — directory map and invariants.
3. Read `C:\Workspace\Project_Android\PROJECT_CONTEXT.md` — workspace-wide context.
4. Read `C:\Workspace\Project_Android\CLAUDE.md` — behavioral rules and Android patterns.
5. Review `C:\Workspace\Project_Android\tasks\lessons.md` if it exists.

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

- Build: `.\gradlew assembleDebug`
- Test: `.\gradlew testDebugUnitTest`
- Lint: `.\gradlew lint`
- Never claim build or test success unless the command was actually run and passed.
