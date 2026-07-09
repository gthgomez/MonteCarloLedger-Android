# AGENTS.md - MonteCarlo Ledger App

Agent-neutral startup router for this project. Root `ENGINEERING.md` and root `AGENTS.md` remain authoritative for safety, verification, deletion, scope, and truthfulness.

## Startup Sequence

1. Read `CLAUDE.md` in this directory — project-local Claude agent guidance (primary agent file).
2. Read `PROJECT_CONTEXT.md` in this directory — directory map and invariants.
3. Read `C:\Workspace\Project_Android\PROJECT_CONTEXT.md` — workspace-wide context.
4. Read `C:\Workspace\Project_Android\CLAUDE.md` — behavioral rules and Android patterns.
5. Review `C:\Workspace\Project_Android\tasks\lessons.md` if it exists.
6. Read a model adapter only when it applies to the active tool:
   - `CLAUDE.md` for Claude (already loaded in step 1)
   - `CODEX.md` for Codex, if present
   - `GEMINI.md` for Gemini, if present

## Local Rules

- `PROJECT_CONTEXT.md` is the canonical project context for all agents.
- Keep model adapters as adapters. Do not flatten, delete, or replace them.
- Treat financial math, balances, reconciliation, Room schema, and persisted ledger behavior as high risk.
- Do not use floating-point math for currency.

## Verification

Use the commands in `PROJECT_CONTEXT.md`. Do not claim build or test success unless the command was actually run and passed.
