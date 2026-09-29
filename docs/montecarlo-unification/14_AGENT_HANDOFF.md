# Agent handoff — execute P00 first

Read00,03,04,06 and P00 in12. Python audited master: cbe0dec0ae7f329a76e7d849475711a011792942. Android audited main:6e418928f429b9be8deffb62525ba51f6017d50f. Recheck current heads/open PRs and inspect only the delta from these pins before implementation. Do not repeat the whole architecture audit or begin a broad feature rewrite.

## First mission: fail-closed Python bootstrap

Repository: gthgomez/MonteCarlo-Ledger. Branch from current reviewed master in an isolated worktree. Scope is bootstrap/preflight and tests only, using existing MIT source. No Android copying or licensing decision is needed for this safety fix.

Problem: init_db runs current schema.sql before reading user_version. Current unique indexes can fail before old repair migrations run. `_to_int_safe` and migrations include truncation/default-zero behavior; deduplication can discard financial records. Do not silently modify a user's only ledger to get startup green. [P-DB/P-SCHEMA]

Read targeted source: init_db, `_to_int_safe`, v1→2, v6→7, v7→8, v8→9, v9→10 and schema-index order; confirm changes since pin. Follow repository instructions. Never test with or attach the user's real ledger.db.

## Red tests first

Create tests/test_migration_bootstrap.py using explicit synthetic old schemas and temporary SQLite files.

1. Old supported database with duplicate payment/date occurrence keys must not fail incidentally because current unique DDL ran before preflight. If merging would lose paid/link/amount information, return structured AMBIGUOUS_LEGACY_DATA and preserve rows/version. Do not assert arbitrary deletion is correct.
2. Future user_version, unknown fingerprint, malformed money, ambiguous units/precision, dangling links and conflicting IDs fail before writes. Compare logical/schema/version dumps and closed-file hashes where WAL is absent. Version-specific valid legacy dollar formats use explicit bounded documented conversion, not a blanket rejection of legitimate old data or generic float-to-int truncation.
3. Fresh initialization reaches the intended current schema; repeated initialization is idempotent. Valid representative v7/v8/v9 upgrade fixtures preserve exact cents/references/counts. Include >2^31 and >2^53 money.
4. Inject failures after preflight and at migration boundaries. Assert recoverability and the actual transaction guarantees. A caught exception must not advance a version or report success.

Record expected regression failure on the base. Do not change golden money values to accommodate an unsafe migration.

## Minimal implementation

Read version/schema fingerprint before current DDL. Empty DB routes to clean creation; recognized supported DB to explicit version preflight/migration; future/unknown/ambiguous DB to nonmutating error. Preflight every monetary/identity condition that existing migration would otherwise truncate, zero or discard. A future repair command can present ambiguous choices; this PR does not invent the answers.

Use a consistent restricted-permission SQLite backup before allowed legacy migration, not a naive live main-file copy. Account for sqlite3 executescript commit behavior. Do not claim whole-chain atomicity without crash tests. If a path cannot be safely demonstrated, refuse it with preserved source and honest recovery instructions instead of declaring all paths green.

No accounts/debts/new CLI framework, Android imports, Site or unrelated formatting. Routing-only correction need not bump storage version; allocate a new version only for actual schema change and coordinate it. Preserve migration history unless a specifically reviewed correction is required.

## Verification

```sh
python -m pip install -e '.[dev]'
python -m pytest tests/test_migration_bootstrap.py tests/test_persistence_orphans.py -q
python -m pytest -q
python -m ruff check .
python -m pyright
git diff --check
git status --short
```

Report exact base/head/paths, red evidence, green outputs, unsupported/ambiguous cases, backup behavior and demonstrated crash/transaction limits. Fresh-context reviewer checks preservation/failure behavior. Follow owner/repo approval rules for money/migrations; do not self-certify/auto-merge against them.

## Following seams

P01 introduces atomic commands:

```text
post_bill_payment(occurrence_id, actual_cents, actual_date,
                  operation_id, expected_revision) -> PostingReceipt
receive_income(source_id, occurrence_id, actual_cents, actual_date,
               operation_id, expected_revision) -> PostingReceipt
```

Validate before insert; one UOW for receipt/posting/cache/link/cursor. Same key/input returns original receipt, changed input conflicts. P02 adds user-data paths/explicit lifecycle with coordinated db_manager ownership. Neither requires proprietary code.

Parallel briefs: A00 restore validation before wipe; A02 symmetric category range with meaning-based tests; A03 remove unsupported numerical benefit; A04 emulator migration evidence. Primary owner handles D00/C00. Separate worktrees and central contract/schema ownership prevent incompatible agent decisions.

Stop before merge for lost/duplicated money, guessed adjustments, unversioned semantic change, unapproved proprietary-to-MIT copying, real data in tests/logs, or claimed tests not run. A missing owner decision blocks its boundary only; continue independent safety work.

Planning verification is not product verification. Hosted CI at the audited heads was green within its configured scope; this planning session did not execute local Python/Android suites, release installs, Site/MCP deployments or real-data privacy tests. Implementation agents must produce that evidence for their changes.
