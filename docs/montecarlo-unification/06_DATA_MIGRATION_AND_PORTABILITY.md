# Migration and portability

Evidence: P-DB/P-SCHEMA; A-DB/A-REPO/A-OCC/A-EXPORT/A-IMPORT/A-CRYPTO. Python storage10, Room16 and Android backup6 are unrelated versions. Neither SQL layout is the interchange contract.

## Sequence

First prevent unsafe bootstrap and restore. Then inventory a consistent local snapshot, exact totals/row counts, schema fingerprint and anomalies; retain a restricted backup. Add portable IDs/namespace/provenance/receipts alongside legacy columns. Shadow-read through canonical adapters and compare deliberate old/new semantics. Cut over capability by capability; legacy fields become derived compatibility state rather than another financial authority. Retire runtime duplication only after supported release evidence; keep historical migrations/readers.

Allocate each next schema number on the actual implementation branch, with one owner. Do not hard-code a conflicting future migration number in parallel PRs. Do not copy only a live SQLite main file while WAL may contain writes; use SQLite backup or a proven closed/snapshot protocol.

## Mapping policy

Python ledger maps into one stable legacy cash account. A cached balance discrepancy becomes an unresolved observed discrepancy, not an invented opening asset/income. Android transactions without reliable account identity remain explicitly unassigned or use a reviewed deterministic mapping with provenance; never assign all history to today's default.

A credit account and debt model must have one balance owner, not summed duplicate liabilities. Paid+linked occurrences retain settlement; paid+null becomes legacy_settled_unknown, not assumed skipped/paid with a fabricated expense. Rescheduling preserves nominal identity. Case-colliding budgets and ambiguous labels are preserved for conflict review. Unknown dates/schedules/money fail or are quarantined with recoverable source evidence, never monthly/today/zero defaults.

Already lost rows cannot be reconstructed from the surviving DB. State that limitation and use an earlier user-held backup when available. Do not infer missing financial history. Detailed local repair reports remain private; exported diagnostics are redacted.

## Restore protocol

`inspect → validate → plan → confirm → apply → verify`.

Inspect bounds bytes/decompression/nesting/row/string counts and KDF work. Validate declared schema and every required array's actual type, exact monetary strings/ranges, signs, keys, relationships, account/debt/clearing/settlement consistency and supported extensions. Old optional defaults belong only in explicit old-version adapters. Wrong-type arrays cannot become empty replacement collections.

Plan includes source digest, target revision, counts/totals and unresolved conflicts. Confirmation binds exact plan/digest/revision. Apply under exclusive application operation lock with a consistent pre-restore backup, then one Room/SQLite UOW or separately crash-qualified shadow DB promotion. A stale revision conflicts instead of overwriting new activity. Test failure at each destructive step. Verify integrity_check, foreign_key_check, identities/links/totals and receipt before success. Keep local PIN/keys separate; never import another device's app-lock secret.

Default restore is explicit replacement, not merge. Merge requires stable IDs and reviewed conflict policy. Repeated import batches have operation receipts. A fresh-target round trip preserves semantic IDs/fields even if local integer IDs differ.

## Interchange

Format version, contract/model version and source storage version are independent. Export stable IDs, ledger namespace/revision/currency/timezone, account/clearing/review state, observations/coverage, schedules/occurrences, categories/rules, budgets/reservations, assets/goals, debts, links and provenance. All money uses decimal strings, including values above2^53. Derived charts/STS/calibration summaries are not restorable financial truth; a result receipt may retain them as historical non-authoritative output.

Unknown material versions/extensions fail, not lossy import. Full backup schema is a C00/P06/A08 deliverable separate from the calculation-request sketch. Valid Android1–6 readers remain supported through strict adapters. Android-derived conversion code reaches MIT only after D00 approval; pathB puts enhanced conversion in the proprietary portable package.

## CSV

Require explicit account/date/column mapping and exact decimal parsing; preview accepted/rejected/ambiguous rows. Provider IDs are strongest duplicate evidence. Without them, account-scoped candidate/multiplicity matching needs review: identical date/merchant/amount is not proof of duplication. Preserve import batch digest/source identity; pending-to-posted matching must replace the same economic movement. Transfers need linked sides; inflows are not automatically salary.

Apply validated batches atomically with receipts. Same confirmed batch is idempotent; changed mappings create a new plan. No silent row dropping. Spreadsheet-oriented CSV exports escape formula-capable text; canonical JSON preserves typed/raw data safely. Never route data labels into executable code.

## Encryption

Retain MCL1 AES-GCM and extra-HMAC readers. GCM already authenticates ciphertext; absence of the added HMAC is not absence of integrity. Bound envelope/KDF sizes before work, use generic wrong-password/corruption errors and avoid plaintext temp files.

A future envelope authenticates version/algorithm/KDF metadata as associated data with fresh nonce/salt and reviewed libraries. KDF changes require a separate benchmark/security-reviewed PR; no crypto rewrite mixed with account migration. Keys/passwords never enter logs, argv, exports or model context. Encrypted backups do not make current plaintext SQLite/Room or a compromised running device encrypted.
