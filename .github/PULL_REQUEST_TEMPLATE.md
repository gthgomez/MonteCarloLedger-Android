## Summary

<!-- What this PR does and why (user-visible + internal). -->

## Scope

- **In:**
- **Out (explicit deferred):**

## Risk tier

<!-- LOW | MEDIUM | HIGH — with one-line justification -->

- [ ] LOW — docs, isolated non-money UI, no schema
- [ ] MEDIUM — multi-file logic without money/Room/security
- [ ] HIGH — money math, Room/migrations, backup/security, app lock, minify/proguard, permissions

## Verification

| Gate | Command | Result | HEAD |
|------|---------|--------|------|
| V1 Unit | `./gradlew :app:testDebugUnitTest --no-daemon` | PASS / FAIL / SKIP | |
| V2 Assemble | `./gradlew :app:assembleDebug --no-daemon` | PASS / FAIL / SKIP | |
| V3 Lint | `./gradlew :app:lintDebug --no-daemon` | PASS / FAIL / SKIP | |
| V4 Release | `./gradlew :app:assembleRelease --no-daemon` | PASS / FAIL / SKIP | |
| V5 androidTest | focused instrumented (if device) | PASS / FAIL / SKIP | |
| CI | GitHub Actions Android CI | PASS / FAIL / PENDING / N/A | |

**Rule:** Production `main/` or money/Room/security → V1+V2 minimum before ready-for-review. HIGH → structured review below.

## HIGH surfaces (check all that apply)

- [ ] N/A — no HIGH surfaces in this PR
- [ ] Money / cents display / no float currency
- [ ] Room schema / migrations / exportSchema
- [ ] Backup export-import / crypto / HMAC
- [ ] App lock / throttle
- [ ] Forecast / Monte Carlo determinism
- [ ] Permissions (still no INTERNET unless intentional)
- [ ] Minify / ProGuard

## Risks and residual

| Risk | Severity | Mitigation / follow-up |
|------|----------|------------------------|
| | | |

## Excluded from this PR

<!-- handoffs, local.properties, keystores, unrelated refactors — or None -->

## Rollback

<!-- How to reverse: revert merge SHA / previous known-good APK -->

## Follow-ups

<!-- Package rename, store QA, etc. — bullets or issue links -->
