# Required instrumented-test gate — corrected workflow (installation BLOCKED)

## Why this directory exists

`connectedDebugAndroidTest (API 34 emulator)` is a **required** branch-protection
check on `main`. The installed workflow
(`.github/workflows/android-instrumented-tests.yml`) can be disabled with the
repository variable `INSTRUMENTED_TESTS_ENABLED=false`. In that case its gate step
sets `run=false`, every substantive step is skipped, and the job still reports
**success**. A required check can therefore be green without executing a single
instrumented test.

`android-instrumented-tests.yml` in this directory is the corrected workflow: when
a PR event sees `INSTRUMENTED_TESTS_ENABLED=false` it emits `::error::` and exits 1.
`workflow_dispatch` still runs. Device tests must either run or the check must fail;
if device tests are intentionally optional they must also be removed from branch
protection.

## Verification performed here

- YAML parses; one job (`connected-android-tests`) with the same check name kept.
- Gate logic executed locally for three cases:
  - `pull_request` + `INSTRUMENTED_TESTS_ENABLED=false` → exits 1 (job fails),
  - `pull_request` + unset → `run=true`,
  - `workflow_dispatch` + `false` → `run=true`.
- The real executed run on the final campaign head
  (`e7157fc6baa2ce4552551ae94e810ea15546c718`, workflow run `42394918343`)
  completed **39 instrumented tests, 0 failures/errors/skips, exit code 0**. The
  downloaded `instrumented-test-reports` artifact contains
  `tests="39" failures="0" errors="0" skipped="0"`.

## Why it is not installed

The repository credential available to this campaign does not carry the GitHub
`workflow` OAuth scope. A push that creates or updates `.github/workflows/` is
rejected:

```text
refusing to allow an OAuth App to create or update workflow
`.github/workflows/android-instrumented-tests.yml` without `workflow` scope
```

This is an external credential limitation. The required check is **BLOCKED**, not
PASS, until a workflow-capable credential or the GitHub UI installs the file.

## Install (maintainer, with `workflow` scope or GitHub UI)

```bash
cp docs/ci/android-instrumented-tests.yml .github/workflows/android-instrumented-tests.yml
git add .github/workflows/android-instrumented-tests.yml
git commit -m "ci(android): fail the required emulator gate instead of skipping green"
git push
```

No branch-protection change is needed: the check name
`connectedDebugAndroidTest (API 34 emulator)` is unchanged, so the existing
required check now means what it says.

Then verify:

```bash
# 1) A normal PR event runs and reports the emulator job.
gh run list -R gthgomez/MonteCarloLedger-Android \
  --workflow android-instrumented-tests.yml --limit 1

# 2) The loophole is closed: with the variable set false, the job must FAIL.
gh variable set INSTRUMENTED_TESTS_ENABLED --body false -R gthgomez/MonteCarloLedger-Android
# open/retrigger a PR; expect the required job to fail with the ::error:: message
gh variable delete INSTRUMENTED_TESTS_ENABLED -R gthgomez/MonteCarloLedger-Android
```
