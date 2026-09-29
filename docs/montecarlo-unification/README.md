# MonteCarlo unification implementation packet

Audit: September 29, 2026. Documentation and synthetic planning artifacts only.

Start with [00 executive](00_EXECUTIVE_SUMMARY.md), [03 architecture/licensing decisions](03_ARCHITECTURE_DECISIONS.md), and [14 agent handoff](14_AGENT_HANDOFF.md).

The packet covers current state, behavioral parity, canonical contracts, conformance, migration, CLI distribution, services, a standalone ChatGPT Site, conversational MCP tools, security/privacy, and dependency-valid implementation stages. All conclusions refer to exact audited revisions, not stale status prose.

- Python `gthgomez/MonteCarlo-Ledger`, `master`: `cbe0dec0ae7f329a76e7d849475711a011792942`.
- Android `gthgomez/MonteCarloLedger-Android`, `main`: `6e418928f429b9be8deffb62525ba51f6017d50f`.

The architecture preserves Python and Kotlin implementations behind one versioned behavioral contract. Neither existing engine is an unquestioned oracle. No production financial logic or repository license is changed by this packet. Cross-repository proprietary reuse remains gated by a named-file owner decision.

First implementation stage: **P00_BOOTSTRAP**. Independent Android restore/calibration/action-evidence/migration-CI work can proceed as described in the handoff. Do not implement the whole roadmap in one large PR.

Source and remote CI were inspected; local product suites, native distributions, deployed Sites/MCP, and real-data hosted privacy were not verified in this planning mission. Synthetic fixture examples define proposed behavior, not current conformance certification.
