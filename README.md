# MonteCarloLedger

Modern financial ledger application for Android. Focuses on deterministic forecasting, bill pacing, and secure encrypted backups.

**Tech stack:** Kotlin, Jetpack Compose, Material3, Room (SQLite), AES-GCM encrypted backups, Clean Architecture (Domain/Data/UI).

**Build:** `.\gradlew.bat assembleDebug` (requires JDK 17 + Android SDK; in-repo `DesignSystem/` composite)

**Ship process (PRs to main):** [docs/SHIP_STANDARD.md](docs/SHIP_STANDARD.md)

**Verify:** `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`

**Detailed docs:** [CLAUDE.md](CLAUDE.md) | [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) | [QA_CHECKLIST.md](QA_CHECKLIST.md)
