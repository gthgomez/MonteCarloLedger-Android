# MonteCarlo Ledger — Release & Signing

This document is the single source of truth for how a release build is produced,
versioned, and signed. It is deliberately explicit about what is **not** yet done
(store signing / Play upload), so the release docs stay truthful.

## 1. Versioning scheme

Declared in `app/build.gradle.kts` → `android.defaultConfig`:

| Field | Current value | Rule |
|-------|---------------|------|
| `versionName` | `1.1.0` | `MAJOR.MINOR.PATCH` (SemVer-style, human-facing). MAJOR = breaking data/behavior change, MINOR = new feature, PATCH = fix. |
| `versionCode` | `2` | Strictly increasing positive integer. Bumped on **every** Play upload; never reused and never decreased (Google Play rejects both). This is a monotonic upload counter, independent of `versionName` arithmetic. |

Both values must be updated in the same commit as the release notes. The release
CI job (`Android Release`) prints them on every run so the artifact is traceable.

## 2. Signing

Release signing is **optional and gated**. Credentials are read, in order of
precedence, from:

1. Environment variables (used by CI when secrets are configured):
   - `ANDROID_KEYSTORE_PATH` — path to the JKS/P12 keystore
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_PASSWORD`
2. A repo-root `keystore.properties` file (gitignored — **never committed**):
   ```properties
   storeFile=/absolute/or/repo-relative/path/release.jks
   storePassword=********
   keyAlias=montecarlo-release
   keyPassword=********
   ```

The `signingConfigs.release` block and the `release { signingConfig = ... }`
assignment only take effect when **all four** values are present. When they are
absent:

- `assembleRelease` still succeeds and produces
  `app/build/outputs/apk/release/app-release-unsigned.apk`.
- The Gradle configuration logs
  `Release signing: DISABLED — ... release output will be UNSIGNED`.

This is intentional: missing signing is a **distribution** blocker (Play upload),
not a **build** blocker. Local builds and the required `Android CI` check must
never break because a developer lacks the keystore.

### Secret hygiene

- `keystore.properties`, `*.keystore`, `*.jks`, `*.p12`, `*.pem`, `*.key` are all
  in `.gitignore`.
- The build never logs passwords or key material — only the keystore path and
  alias, and only when signing is enabled.
- Do not commit a keystore. Back it up in a password manager / secret store: a
  lost Play signing key cannot be recovered (only rotated via Play App Signing).

## 3. Commands

```bash
# Unsigned release, works with zero credentials:
./gradlew :app:assembleRelease --no-daemon

# Signed release (keystore.properties present) — same command, signed output:
./gradlew :app:assembleRelease --no-daemon

# Full local pre-flight:
./gradlew --no-daemon :app:assembleRelease :app:testDebugUnitTest
```

CI:

- `.github/workflows/android-ci.yml` — required check **"Android CI"**
  (`:app:testDebugUnitTest` + `:app:assembleDebug`). Unchanged by design.
- `.github/workflows/android-release.yml` — **"Android Release"** — runs
  `:app:assembleRelease --no-daemon` to keep R8/minify compiling. No
  `continue-on-error`.

## 4. Still human-blocked

| Item | Owner | Blocking |
|------|-------|----------|
| Generate / provide the release keystore and populate `keystore.properties` (or the four CI secrets). | Repo owner | Signed `.aab`/`.apk`, Play upload |
| Play Console app entry, store listing, data-safety form, privacy-policy URL in console. | Repo owner | Public release |
| Instrumented Room migration test (`connectedDebugAndroidTest`) on a device/emulator. | Repo owner | Migration confidence evidence |
