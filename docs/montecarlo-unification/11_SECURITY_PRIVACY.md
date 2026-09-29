# Threat model and privacy gates

Architectural assessment, not penetration-test certification or a legal-compliance opinion. No real financial records were needed. Source boundaries: P-DB/P-API/P-SECURITY and A-REPO/A-EXPORT/A-IMPORT/A-CRYPTO/A-MANIFEST/A-PRIVACY/A-SECURITY.

| Boundary / threat | Required control | Verification/residual limit |
|---|---|---|
| Plain SQLite/Room, WAL and temp files | Per-user paths, restrictive permissions/ACLs, OS/device protection, consistent backups | Cross-user tests; same-user/root compromise remains; PIN is not DB encryption |
| Export/backup disclosure | Explicit action, encrypted option, restricted output, plaintext warning | All fields inspected; keys/PIN excluded; SAF/cloud destination is external disclosure |
| Malformed restore | Bound sizes/KDF work; strict version/shape/money/ref validation before writes; atomic recovery | Wrong arrays/overflow/duplicates/orphans/crashes preserve target |
| CLI/error/terminal leakage | Redacted codes, escaped labels, JSON-only stdout, secure password input | Canary values absent from logs; no argv secrets or raw support dumps |
| CSV formula injection | Safe spreadsheet-text export, canonical typed JSON alternative | Formula/control/multiline cases; no executing imported labels |
| Local HTTP | Explicit loopback/token/pairing; Host/Origin/CSRF/DNS-rebinding defense; no startup writes | Loopback/CORS alone are insufficient |
| Hosted compute | TLS, ownership/auth, bounded work/queues, request isolation, no payload logs | Token/tenant/load/cancellation tests; provider retention documented |
| Site/authoring disclosure | Synthetic first; minimal reviewed submission; no secrets/ledger in prompts/URLs | Inspect analytics/network/support retention and residency limits |
| MCP confused deputy/exfiltration | Scoped tokens, account checks, minimal output, typed inputs, no arbitrary URLs/code | Merchant prompt injection cannot grant authority or read others' records |
| UI/result injection | Escaping/CSP/origin/resource checks; verified result tokens | Forged/expired/other-user results fail; _meta is not secret storage |
| Repeated/unauthorized writes | Exact confirmation, operation ID, target revision, UOW receipt | Retry/double-tap/crash tests; broad chat sentiment is not action approval |
| Telemetry/support bundles | Local off by default, opt-in redacted preview, minimal counters | No names/amounts/paths/DB copies by default |
| Deletion/portability | Copy/retention inventory, export/delete procedures, expiry verification | No forensic SSD/SQLite erase promise or deletion of independently held exports |

## Integrity before network expansion

Python bootstrap can apply current indexes before old-schema preflight; sanitation can truncate/zero money. Bill/payday workflows have split commits. Android parsing can turn malformed collections empty before replacement; account routing/default reconciliation is not uniform; numerical action benefit is not computed. These are release priorities even without an external attacker. Correct state/identity/transactions rather than adding disclaimers beneath incorrect numbers.

## Encryption distinctions

MCL1 uses AES-GCM with derived keys and an optional added HMAC. GCM already authenticates ciphertext; legacy extra-HMAC absence is not absence of all integrity. Review metadata/KDF/envelope bounds and cross-version reads before changing crypto. App-lock PIN hashing gates UI, not Room encryption. Device protection, export encryption and server authentication protect different boundaries.

The inspected source manifest omits INTERNET and disables ordinary backup. Verify merged release manifest/dependency behavior before claiming that posture for a shipped APK. No current build/crypto implementation is certified by this packet.

## Hosted/model responsibilities

Inventory browser, Site hosting, calculation provider, optional model, logs/analytics/support and backups: fields, purpose, access, roles where applicable, region, retention, deletion. Sites guidance assigns owner privacy responsibilities. Obtain legal/privacy review for intended users/jurisdictions; do not claim GDPR/PCI compliance from a design alone.

No bank passwords/PAN/unnecessary identifiers. Current Sites terms restrict sensitive data and financial-transaction functionality. Keep real-data persistence/payment execution outside initial Site. Transmission consent must be understandable and specific, not a buried new destination. API store=false does not eliminate every abuse-monitoring/cache/feature retention pathway. Durable agents/files/vector stores are unnecessary for calculations and add exposure.

## Incident policy

Android SECURITY.md currently disclaims triage/remediation/support and incorrectly connects proprietary status to whether defects can be vulnerabilities. Licensing does not prevent security defects. Owner must choose reporting route, supported versions, escalation/remediation practice and notification process before broader release. Do not promise an unapproved SLA. Keep licensing intact while correcting the security claim.

Resolve TODO_PRIVACY_URL when a published policy is required. Align status/release notes with actual revisions/tests/schema and privacy behavior. Private concerns should avoid public real data or weaponized exploit payloads; this packet contains source-level architectural findings and synthetic cases only.

## Gates

D01 blocks real-data hosted Site/MCP until data flow/notice, auth/tenant tests, retention/logging evidence, export/deletion, incident owner and platform terms/runtime are reviewed. Local release requires preservation/restore/upgrade/permission tests. Every safe-spend surface must show account scope, horizon and qualification; no guaranteed financial outcome is claimed.
