# CLAUDE.md — MonteCarlo Ledger App

This file serves as the project-local adapter for Claude in the MonteCarlo Ledger repository.

## Layered Context
This project inherits from the root `ENGINEERING.md` and `AGENTS.md`. 
**Do not flatten or delete this file.** It acts as a context gate to prevent generic SaaS or Game Dev patterns from bleeding into this financial ledger application.

## Core Directives
1. **Financial Precision:** All calculations must use exact precision (e.g., `Decimal` types or integer cents). Do not use floating-point math for currency.
2. **Security:** Maintain strict boundaries for any user data.
3. **Verification:** Rely on the local test suite for validation before considering any task complete.
