# MonteCarloLedger — Ship Standard

Normative process for every PR to `main`. This is production hygiene for a **finance** app: money math, Room, and backups are HIGH risk by default.

Authority floor: workspace `ENGINEERING.md`. This file is project-local process.

---

## 1. Principles

1. **Evidence before merge** — record commands, exit codes, and HEAD SHA in the PR.
2. **CI is a floor, not a ceiling** — green Actions is required when workflows exist; local verification still required for HIGH surfaces.
3. **HIGH surfaces get explicit review** — money, Room, security/backup, lock, forecast, minify.
4. **Scope honesty** — deferred work is named; never silent “fixed.”
5. **Merge ≠ store release** — landing on `main` is integration only. Play Store is a separate CRITICAL gate.
6. **One approval is not a blank check** — commit ≠ push ≠ merge ≠ deploy.

---

## 2. Branch and PR flow

| Step | Rule |
|------|------|
| Branch | `feat/`, `fix/`, `chore/`, `docs/`, `ci/` prefixes |
| Open | Draft PR until V1+V2 recorded (or doc-only review) |
| Template | Use `.github/PULL_REQUEST_TEMPLATE.md` — fill every section |
| Ready | Mark ready only when verification table is honest |
| CI | Must be green before merge when `Android CI` runs on the PR |
| Merge | Prefer **squash** + delete branch |
| HIGH merge | Explicit human approval after structured review |

Forbidden in commits:

- `local.properties`, keystores, `.env*`, secrets
- `handoff-*.md`, agent session dumps
- `.factory-wt/`, build outputs, IDE caches

---

## 3. Verification ladder

Apply the **highest** tier that matches the change.

| Tier | When | Command |
|------|------|---------|
| **V0 Doc** | Markdown only | Human review of links/claims |
| **V1 Unit** | Any logic / tests | `./gradlew :app:testDebugUnitTest --no-daemon` |
| **V2 Assemble** | Any `app/src/main`, gradle, resources | `./gradlew :app:assembleDebug --no-daemon` |
| **V3 Lint** | UI / resources / broad Kotlin | `./gradlew :app:lintDebug --no-daemon` |
| **V4 Release** | minify, ProGuard, release config | `./gradlew :app:assembleRelease --no-daemon` |
| **V5 Instrumented** | Migrations / UI instrumentation | `:app:connectedDebugAndroidTest` (device) |

**Minimums:**

| Change class | Minimum |
|--------------|---------|
| Docs only | V0 |
| Tests only | V1 |
| Production `main/` | V1 + V2 |
| Money / Room / backup / lock / forecast | V1 + V2 + HIGH checklist (+ V3 preferred) |
| Minify / ProGuard | V1 + V2 + V4 |

Record results in the PR body:

```text
VERIFICATION RECORD
Date: <ISO date>
HEAD: <sha>

V1 unit:     PASS|FAIL|SKIP — exit N — wall …
V2 assemble: PASS|FAIL|SKIP — exit N
V3 lint:     PASS|FAIL|SKIP — …
V4 release:  PASS|FAIL|SKIP — …
V5 androidTest: PASS|FAIL|SKIP — …
CI: PASS|FAIL|PENDING|N/A
```

---

## 4. HIGH-surface checklist

When the PR touches a surface, that row must be PASS or an accepted residual.

| Surface | Pass criteria |
|---------|----------------|
| Money | Integer/Long cents; no float currency math; display helpers covered by tests where claimed |
| Room | Schema version intentional; `exportSchema` artifacts updated; migration does not wipe data |
| Backup / security | AES-GCM path intact; integrity/HMAC tests green; wrong password / corrupt handling preserved |
| App lock | Throttle/backoff tested; no permanent lock without recovery path |
| Forecast / MC | Deterministic under fixed seed / injected clock as designed |
| Permissions | No surprise INTERNET or broad storage; manifest reviewed |
| Release minify | V4 green or residual documented with follow-up |

Structured review comment (required for HIGH):

```markdown
## Structured review
- Money: PASS | FAIL | NITS — …
- Room: PASS | FAIL | NITS — …
- Backup/security: PASS | FAIL | NITS — …
- Lock: PASS | FAIL | NITS — …
- Forecast: PASS | FAIL | NITS — …
- Release minify: PASS | FAIL | SKIP — …
Verdict: APPROVE | APPROVE_WITH_NITS | REQUEST_CHANGES
```

Do not merge on `REQUEST_CHANGES`. Do not APPROVE without V1+V2 green.

---

## 5. CI

Workflow: `.github/workflows/android-ci.yml`

- **Triggers:** PR to `main`, push to `main`, manual dispatch
- **Gates:** `:app:testDebugUnitTest`, `:app:assembleDebug`
- **JDK:** 17

### DesignSystem composite

This repo **vendors** `DesignSystem/` for standalone clone and CI.

- `settings.gradle.kts` prefers `./DesignSystem`, else monorepo `../DesignSystem`.
- Updating shared design: change vendored copy intentionally (or sync from monorepo) and note in PR.
- Do not rely on a secret multi-repo checkout for the default CI path.

### Branch protection

Private free GitHub may lack required checks / branch protection API. Policy still applies:

- **Do not merge red CI.**
- **Do not push directly to `main`** for product work — use PRs.

When Pro/public enables protection: require `Android CI` status checks on `main`.

---

## 6. Risk tiers and approval

| Tier | Examples | Merge |
|------|----------|--------|
| LOW | Docs, comments | After review; CI if code paths untouched |
| MEDIUM | Multi-file non-money logic | CI green + verification table |
| HIGH | Money, Room, backup, lock, forecast, minify | CI green + V1–V2 + structured review + **explicit human OK** |

---

## 7. Post-merge

1. Confirm `main` SHA.
2. Optionally re-run V1 on `main` once.
3. Delete remote feature branch (squash merge should).
4. File follow-ups as issues or roadmap bullets — not silent debt.
5. Store / Play release remains a separate CRITICAL checklist (`QA_CHECKLIST.md` full run).

### Rollback

```text
git revert <squash_or_merge_sha>
# or restore previous known-good tag/APK for distribution builds
```

---

## 8. Size and scope discipline

- Prefer **one concern per PR** after the initial large Tier A/B land.
- Soft flags: >500 LOC or >15 files → justify in Summary why not split.
- Do not mix package rename with feature work.

---

## 9. Quick commands (Windows)

From repo root `MonteCarloLedger`:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon
.\gradlew.bat :app:assembleDebug --no-daemon
.\gradlew.bat :app:lintDebug --no-daemon
.\gradlew.bat :app:assembleRelease --no-daemon
```

---

_Last updated: 2026-07-21 — established with PR #1 standards-first ship._
