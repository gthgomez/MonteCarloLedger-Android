# Dependencies and parallel ownership

Machine DAG: artifacts/pr-dependency-graph.json. Logical IDs are not existing PR numbers. The JSON lists all direct prerequisites; this overview groups related stages.

```mermaid
flowchart TD
 P00 --> P01
 P00 --> P02
 D00 --> C00
 C00 --> P03
 C00 --> A05
 P01 --> P04
 P02 --> P04
 P03 --> P04
 A00 --> A01
 A01 --> A06
 A04 --> A06
 A05 --> A06
 A02 --> A05
 P04 --> P05
 A06 --> A07
 P05 --> P06
 P05 --> P07
 A07 --> A08
 A07 --> A09
 P06 --> P08
 P07 --> P08
 A08 --> A10
 A09 --> A10
 P05 --> P09
 P07 --> P09
 A07 --> A11
 A09 --> A11
 A03 --> A11
 P05 --> P10
 P06 --> P10
 P06 --> P11
 P07 --> P11
 C00 --> D01
 P07 --> S00
 P11 --> M00
 D01 --> M00
 P10 --> P12
 P07 --> P12
 S00 --> S01
 P11 --> S01
 D01 --> S01
 M00 --> M01
 P08 --> P13
 P09 --> P13
 P12 --> P13
 P11 --> P13
 A08 --> A12
 A10 --> A12
 A11 --> A12
```

Primary session owns contract synthesis, licensing interpretation, fixture publication and migration version allocation. Subagents may investigate/test independently but cannot choose incompatible schemas or models. Fresh-context review should come from the current execution harness and inspect exact diff/test evidence, not merely trust the implementer's report.

Initial independent lanes: Python bootstrap; Android strict restore; Android calibration correction; Android numerical-action honesty; Android emulator CI; owner rights/privacy-policy preparation. Restore/reconciliation share LedgerRepository and merge sequentially. P01/P02 share db_manager and need one integration owner. Use isolated worktrees; no shared writable checkout.

After C00, Python and Kotlin DTO/runner implementations can run in parallel against one approved manifest. Storage migrations are sequential inside each repo. Deterministic implementations can parallelize, then compare exact reports. Portability and manual simulation can proceed after stable DTO/reducer interfaces. Calibration and debt may have separate owners, both using the same identity/account contract.

Frontend workers can build synthetic accessible presentation from approved engine bundles; arbitrary personal inputs wait for service/privacy. No temporary TypeScript arithmetic. MCP descriptions/UI tests can prepare independently, but real-data deployment waits for authorization and D01.

Sequential operations: versioned schema/ID backfill, ambiguity repair, financial cutover, fixture release and retirement. Do not allocate duplicate migration numbers or remove historical readers because a new schema is current. A stage split must retain all parent dependencies and acceptance gates.

Deferred: persistent cloud ledger/sync, automatic bank connectivity/payment execution, FX, issuer-exact statements, generalized correlated Monte Carlo, Rust/KMP extraction and third financial implementation. They are not hidden prerequisites for shipping a useful local CLI and native app.
