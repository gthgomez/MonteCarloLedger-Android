# AGENTS.md - MonteCarlo Ledger App

Agent-neutral startup router for this project. Root `ENGINEERING.md` and root `AGENTS.md` remain authoritative for safety, verification, deletion, scope, and truthfulness.

## Startup Sequence

1. Read `C:\Workspace\ENGINEERING.md`.
2. Read `C:\Workspace\AGENTS.md`.
3. Read `PROJECT_CONTEXT.md` in this directory.
4. Read a model adapter only when it applies to the active tool:
   - `CLAUDE.md` for Claude
   - `CODEX.md` for Codex, if present
   - `GEMINI.md` for Gemini, if present

## Local Rules

- `PROJECT_CONTEXT.md` is the canonical project context for all agents.
- Keep model adapters as adapters. Do not flatten, delete, or replace them.
- Treat financial math, balances, reconciliation, Room schema, and persisted ledger behavior as high risk.
- Do not use floating-point math for currency.

## Verification

Use the commands in `PROJECT_CONTEXT.md`. Do not claim build or test success unless the command was actually run and passed.
