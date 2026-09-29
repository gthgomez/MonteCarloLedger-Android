# CLI product and distribution

## Current blockers

P-PACKAGE already has setuptools/wheel metadata, Python>=3.10, a console entry point and schema package data. P-CLI remains menu/onboarding-oriented. P-DB writes beside the package parent. CI verifies editable installs, not wheels/pipx/uv/frozen binaries. The v0.1.0 GitHub release has no assets; PyPI name ownership/publication was not verified. Do not characterize this as an absent packaging system.

Work required: user-data paths; side-effect-free help/read/startup; explicit init/migrate; deterministic commands/JSON; package discovery for new subpackages; optional service dependency extra; installed-artifact tests and a release pipeline.

## Data storage

Use `platformdirs.user_data_path('MonteCarlo', appauthor=False, version=None, roaming=False)` or a tested equivalent. Expected OS paths: XDG data home/fallback ~/.local/share/MonteCarlo; ~/Library/Application Support/MonteCarlo; %LOCALAPPDATA%\MonteCarlo. Never version the data directory with the package version. Configuration/logs use appropriate separate paths.

Resolution contract: explicit --db > MONTECARLO_DB > explicit --data-dir > MONTECARLO_DATA_DIR > OS default. Reject simultaneous --db/--data-dir flags. Surface effective override selection in local diagnostics, especially an existing DB environment override. Profiles cannot traverse outside approved directories. Test ownership/symlinks, permissions/ACLs, spaces/Unicode and unwritable paths. No fallback to cwd on failure.

Help/version/import/reads must not initialize or migrate. `init` is explicit; `db migrate` is explicit. `db adopt --source` inspects and copies a consistent legacy DB after preview/confirmation, never deletes it. Multiple legacy candidates require selection. Upgrade/uninstall never deletes user data.

## Commands and output

Implement testable `main(argv=None)` with injected services. Keep monte-carlo-ledger entry point; optional montecarlo alias after collision review. Retain old menu under interactive. Non-TTY bare invocation prints help/error rather than blocking for input.

| Group | Read/calculation | Explicit writes |
|---|---|---|
| init/db/doctor | inspect, version, migrate --plan, doctor | init, migrate --apply, adopt |
| accounts | list/show/balances | add/edit/archive/set-default |
| transactions | list/show/pending | add/amend-or-reverse/post/void/transfer |
| income | list/occurrences | add/edit/archive/receive/override |
| bills | list/occurrences/pressure | add/edit/archive/pay/skip/reschedule |
| reconcile | status/observations/plan | observation/confirmed adjustment |
| forecast/risk | cash flow/windows/headroom/risk | none |
| scenarios | validate/run/compare | explicitly save/delete scenario files |
| debt/budgets/goals | compare/status/funding plan | terms/reservations |
| import/export/backup | preview/export/inspect | confirmed import/restore/backup creation |

Advertise only supported conformance profiles. Unsupported debt does not fall back to an LLM answer. Recording a ledger payment does not execute a bank transfer.

Common flags: --json, --no-input, --as-of, --timezone, --horizon-days, --account, --seed, --db/--data-dir. Writes carry operation ID, expected revision and confirmation/plan digest. Money uses explicit cent strings or strictly parsed decimal dollars; no binary float conversion. Passwords are secure prompts/protected descriptors, not argv.

JSON mode emits one document on stdout, no banners/ANSI/progress/migration chatter. Errors go to stderr with stable redacted codes. Include schema/contract/model/engine version, revision, scope/currency/date/horizon, assumptions and qualification. Money is string; no NaN/Infinity. Unknown/unbounded values are null with reason. Proposed exit codes:0 success;2 validation;3 uninitialized;4 required qualification/reconciliation missing;5 revision/idempotency conflict;6 IO/integrity;7 unsupported schema/capability. A valid forecast predicting shortfall is not itself a command error. --require-qualified can make an unqualified calculation exit4.

## Distribution qualification

Normal installation: build wheel+sdist; install wheel into fresh environment from outside checkout; verify subpackages/resources/console help/init/forecast JSON while install tree is read-only. Use importlib.resources rather than editable-checkout paths. Update setuptools enumeration before new subpackages ship. Move FastAPI/Uvicorn/HTTPX to an optional extra after testing CLI without them.

pipx and uv tool install: test actual built wheel in isolated tool environments, command discovery, upgrades and persistence outside that environment. No claim of publication until registry ownership and uploaded artifact are verified.

Windows: native x64 initially, ordinary-user/ACL/path/SQLite/locked-file tests; explicit Authenticode policy. macOS: separately qualified arm64/x64 or a genuinely tested universal build, signing/notarization/quarantine launch. Linux: declared oldest-supported glibc and architecture, native dependency and non-root smoke. PyInstaller is a packaging candidate; frozen artifacts are platform-specific. Start onedir for diagnostics, assess onefile temp/startup separately. One Linux build is not cross-OS qualification.

CI publishes per-target test receipts, artifact hashes, dependency/SBOM/license metadata and protected signing/publishing. No production DB or secrets in artifacts. A tag or editable CI badge is not installation proof. Under pathB the proprietary engine has separate distribution terms/artifacts; MIT branding must not obscure it.
