# Architecture decisions and gates

Status: proposed architecture requiring owner acceptance at C00; existing behavior is not automatically conforming.

## ADR01 — behavior and evidence are authoritative

Keep both repositories and one versioned domain contract, fixture manifest and capability/version policy. Each result identifies implementation build, model/contract versions, input digest, ledger revision, as_of/horizon/scope, assumptions and qualification. Golden changes require reviewed semantic decisions; neither Python nor Kotlin output alone is the oracle.

Pure financial code cannot import Room/SQLite handles, UI, FastAPI, network clients, clocks or model SDKs. Adapters produce immutable DTOs. Application services own consistent reads, authorization, transactions, idempotency and receipts. Rendering must not implement another financial reducer.

## ADR02 — retain Python plus Kotlin now

Python+Kotlin conformance preserves both working surfaces and offline Android with the smallest migration. Its cost is two implementations and disciplined anti-drift governance. Reject a third TypeScript financial implementation. Reject remote-only Python as Android's mandatory engine because that sacrifices local-first operation and creates financial-data/network dependency.

KMP may later reuse extracted Kotlin; Rust may later provide Python/JNI/WASM bindings. Neither removes licensing or migration qualification. Reconsider only after at least two contract releases with measured duplicate-fix/conformance-escape/performance costs, a real offline-browser need, and a prototype passing the same fixtures. No rewrite for elegance.

## ADR03 — licensing is a product decision, not a code cleanup

Python MIT and Android proprietary notices are verified. Android public visibility grants no reuse permission. This audit has read Android source; do not claim a subsequent implementation is automatically clean-room. Root licenses remain unchanged. [P-LICENSE/A-LICENSE]

**PathA:** owner explicitly grants MIT rights for a named set of portable contracts, fixtures and corrected implementation, after contributor/third-party rights review. The Python portable engine can then ship publicly under MIT; Android UX/platform integration stays proprietary.

**PathB:** retain existing MIT code and its grant; place enriched portable implementation in a separately licensed proprietary Python package/repository. An owner-approved `portable-engine/` area in this Android repository is one initial location, not an assumed decision. The MIT CLI may use an expressly licensed adapter/plugin contract and must report unavailable capabilities without that package. Do not hide proprietary files in an MIT wheel or test suite.

A separate permissive specification/fixture license or dual/commercial licensing can be considered only where rights permit. D00 records selected path, exact files, provenance, contributors/dependencies, distribution location/terms and excluded files. Shared fixtures must be legally consumable by both implementations. The combined packet stays in Android's proprietary planning branch, not silently MIT.

Safe before D00: Python-only bootstrap/atomicity/path fixes based on its MIT source; Android-only restore/reconciliation/calibration fixes; synthetic characterization within each repo; draft contracts and tests in this planning area; packaging experiments without proprietary ports. Do not let licensing block unrelated safety improvements.

## ADR04 — relational integrity, not a wholesale event-sourcing rewrite

Use additive stable identities, observed reconciliation records, operation receipts, revisions and sufficient audit history. Never rewrite a ledger to make a cache agree. Preserve ambiguous records and explain qualification. Actual money values require an explicit repair decision, not invalid-to-zero conversion or arbitrary duplicate deletion.

## ADR05 — semantic changes are versioned

The target changes horizon inclusion, overdue income, pending treatment, unknown same-day ordering, quantile labels and overflow handling. Capture old/new outputs on synthetic and locally consented datasets, retain a temporary explicitly named legacy profile, and avoid rewriting historical amounts because forecast semantics change. Storage, contract, model and export versions are independent.

## ADR06 — thin optional external interfaces

CLI/Android need no cloud account, model or network. A local web UI may share presentation code against loopback services. A hosted Site is not local merely because it renders in a browser; remote MCP cannot reach a laptop DB without an explicit connection/data-transfer design.

Site sequence: synthetic demo, then privacy-gated minimal ephemeral scenarios, with persistent ledgers/sync postponed. MCP initially has four user-goal calculation tools, not every DAO. The model remains outside the monetary authority boundary. Site payment/bank-transfer execution is excluded; current platform terms require a launch-specific check.

## ADR07 — explicitly deferred

FX/multi-currency, arbitrary banking-day calendars, partial bill settlement, issuer-exact credit statements/grace periods, bank connectivity, automatic financial transactions, cloud sync, generalized correlations/outlier fitting, stochastic spending optimization and portable PRNG replacement are not v1 guarantees. Return unsupported/insufficient-evidence states instead of silent approximations. Name monthly-simple debt estimates as approximations.
