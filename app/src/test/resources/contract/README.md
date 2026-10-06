# Vendored MonteCarlo Contract snapshot (pinned)

This directory is a **read-only, hash-pinned snapshot** of the normative
[`MonteCarlo-Ledger`](https://github.com/gthgomez/MonteCarlo-Ledger) contract at a single commit.
The Android conformance runner reads these files as test resources; it never fetches `main` at test
time.

| Field | Value |
|---|---|
| Source repo | `https://github.com/gthgomez/MonteCarlo-Ledger` |
| Source commit | `eea85f0` (branch `campaign/semantic-contract`) |
| Contract version | `1.0` |
| Layout | `contracts/`, `schemas/`, `fixtures/` copied verbatim from the source commit |

> **Pin history.** The milestone brief named `5dfb40c`. Before this snapshot was finalized, a
> parallel `MC-03` commit (`eea85f0`) amended `MCD-0007` (the percentage-scaling rule in
> `contracts/money.md`) and froze the four `stochastic/` fixtures with reference values. The pin was
> advanced to `eea85f0` so the Kotlin conformance runner can independently reproduce the frozen
> corpus. `5dfb40c`'s `money.md` defined `scale_cents_by_percent` as a delta
> (`amount * percent / 100`), which is internally inconsistent with `simulation.md`'s variation
> usage; `eea85f0` is the corrected rule
> (`amount * (100 + percent) / 100`). This is recorded in the deviation notes for MC-06.

`contract-pin.json` records the SHA-256 of every vendored file. `ContractPinTest` fails the build if
any vendored file's digest changes, so Android can never silently drift onto a mutable contract
branch.

## Why a snapshot instead of a Git submodule?

A submodule would add a network fetch and a recursive-clone requirement to every CI run, and would
let the working tree point at a moving branch. A pinned copy plus a digest test gives the same
guarantee with no extra tooling and works offline.

## How to update the pin

1. In a clone of `MonteCarlo-Ledger`, check out the newly tagged/released contract commit (do not
   use a moving branch tip; the pin must name an immutable commit).
2. Replace the vendored trees:

   ```bash
   DST=app/src/test/resources/contract
   rm -rf "$DST/contracts" "$DST/schemas" "$DST/fixtures"
   cp -r /path/to/MonteCarlo-Ledger/contracts "$DST/contracts"
   cp -r /path/to/MonteCarlo-Ledger/schemas  "$DST/schemas"
   cp -r /path/to/MonteCarlo-Ledger/fixtures "$DST/fixtures"
   ```

3. Regenerate `contract-pin.json`: update `source_commit` and `contract_version`, then hash every
   file under `contracts/`, `schemas/`, and `fixtures/` (SHA-256, lower-case hex) into `files`. See
   the one-liner in the commit that introduced this directory, or the equivalent Python:

   ```python
   import hashlib, json, os
   dst = "app/src/test/resources/contract"
   files = {}
   for root in ("contracts", "schemas", "fixtures"):
       for dp, _, fns in os.walk(os.path.join(dst, root)):
           for fn in fns:
               p = os.path.join(dp, fn)
               rel = os.path.relpath(p, dst).replace(os.sep, "/")
               files[rel] = hashlib.sha256(open(p, "rb").read()).hexdigest()
   json.dump({"source_repo": "...", "source_commit": "...", "contract_version": "...",
              "files": dict(sorted(files.items()))},
             open(os.path.join(dst, "contract-pin.json"), "w"), indent=2)
   ```

4. Update the table above and run `./gradlew :app:testDebugUnitTest` — `ContractPinTest` should pass
   against the regenerated pin.

Any change to financial expectations (a new fixture, a newly frozen `pending-generation` fixture)
requires a contract-version change and an MCD record in the source repository **before** re-pinning.
