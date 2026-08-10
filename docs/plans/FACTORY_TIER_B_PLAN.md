# Factory plan — Tier B (audit follow-through)

**Project:** MonteCarloLedger  
**Depends on:** Tier A on `feat/factory-remaining-p1`  
**Efficiency policy:** sequential only; one WP per slice; brief ≤80 lines; budget ≤$8/slice; verify with focused tests first.

## Out of scope (later tier)

- Package rename `com.example.app`
- Full Long cents storage migration
- Multi-account / bank sync / Plaid
- Biometrics (optional later; needs new dependency)

## Tier B slices

### B1 — Monte Carlo determinism + event typing

Inject `today: LocalDate` into MonteCarloEngine; surprise expenses use consistent type/source; unit tests with fixed today.

### B2 — Income contribution clarity + tests

Document `calculateIncomeContribution` contract; add parametrized unit tests for multi-income/multi-bill cases.

### B3 — Balance drift re-reconcile UX

Surface aggressive "Balances drifted by $X — re-reconcile" when `checkBalanceConsistency` fails after mutations; CTA opens bank balance dialog.

### B4 — Backup plaintext HMAC (optional integrity)

Add HMAC over backup JSON plaintext; verify on import; clear wrong-mac error distinct from wrong password.
