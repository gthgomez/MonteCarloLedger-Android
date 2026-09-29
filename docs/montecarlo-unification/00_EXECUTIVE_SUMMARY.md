# MonteCarlo unification — executive decision

Planning date: September 29, 2026. No production changes.

## Architecture

Create one authoritative **versioned financial contract and conformance system**, initially implemented in Python and Kotlin. Keep native/offline Android; make Python the portable CLI/service implementation after correction and conformance. Do not add a third TypeScript financial engine or start a Rust/KMP rewrite. AI translates intent into validated scenario inputs and explains computed outputs; it never supplies balances, interest, risk probabilities or purported savings itself.

The working hypothesis is substantially correct: Android has richer product capability, while Python has the more convenient portable packaging/service shape. The important correction is that **neither existing engine is safe to designate an unchanged oracle**. Unification must correct semantics, not simply port the larger codebase.

## Exact audited revisions

| Repository | Default | Audited HEAD | Persistence |
|---|---|---|---|
| gthgomez/MonteCarlo-Ledger | master | cbe0dec0ae7f329a76e7d849475711a011792942 | SQLite user_version 10 |
| gthgomez/MonteCarloLedger-Android | main | 6e418928f429b9be8deffb62525ba51f6017d50f | Room16; backup JSON6 |

Both repositories are public and writable; Python is MIT, Android proprietary/source-visible. Open issue/PR searches returned no entries at audit. Hosted CI succeeded for both pinned heads, but its scope does not establish conformance, instrumented migration or installed distribution. Both v0.1.0 prereleases have no downloadable assets. See16 for source and verification scope.

## Findings that change priorities

Python already has pyproject metadata, a console entry point and packaged schema; its blockers are package-relative DB storage, menu-only behavior and missing installed-artifact qualification. Bill and payday workflows commit ledger insertion separately from occurrence/cursor updates. Bootstrap executes current schema/indexes before identifying the old schema, while migration sanitation can truncate through floats or replace bad values with zero. Seeded risk still reads the wall clock. [P-PACKAGE, P-DB, P-PAY, P-INCOME, P-RISK]

Android category calibration constructs `range..range`, a fixed positive uplift, and an existing test codifies a positive lower bound. Action recommendations publish a heuristic numerical risk reduction without counterfactual simulation. Account/credit paths coexist with global cached-balance paths; selecting a default account can mark it reconciled without a new observation. Malformed backup arrays can become empty collections before full restore replacement. [A-CAL, A-CAL-TEST, A-ACTION, A-REPO, A-EXPORT]

The engines disagree about end-date inclusion, overdue income, rounding and percentile meaning: Python emphasizes ending balances; Android minimum balances along a run. Shared names are not parity. [P-TIMELINE, A-TIMELINE, P-RISK, A-MC]

## License gate

PathA: owner expressly approves named portable contracts/fixtures/implementation for MIT; native Android UX/platform code stays proprietary. PathB: enhanced portable engine remains a separately licensed proprietary Python package, with the existing MIT CLI/core retained and advanced capabilities explicitly unavailable without that package. Verify contribution/dependency rights. No automatic clean-room claim, no implicit relicensing. Preserve this combined packet in Android's proprietary repository. D00 must resolve destination and rights before any cross-boundary port.

## Order

Begin P00_BOOTSTRAP. Parallel independent work: Android strict restore, narrow calibration correction, removal of unsupported numerical action claims, emulator migration CI, and owner licensing review. Follow with atomic posting and user-data paths, contract adoption, stable identities/account semantics, deterministic conformance, portability, stochastic conformance, CLI/services and thin external interfaces.

The first Site is synthetic/demo only. Ephemeral real-data scenarios require a separate privacy gate; persistent cloud ledgers are postponed. Site functionality remains calculation-only under current platform terms. MCP starts with four user-goal tools that work without UI.

Owner decisions: licensing/file grants; approval of intentional financial-semantic changes; real-data hosting/privacy; supported signed distribution targets and security-support policy. None blocks the independent within-repository safety fixes. Read14 to start implementation without repeating the architecture investigation.
