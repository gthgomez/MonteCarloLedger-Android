# MonteCarloLedger

Modern financial ledger application for Android. Focuses on deterministic forecasting, bill pacing, and secure encrypted backups.

**Tech stack:** Kotlin, Jetpack Compose, Material3, Room (SQLite), AES-GCM encrypted backups, Clean Architecture (Domain/Data/UI).

**Build:** `.\gradlew.bat assembleDebug` (requires JDK 17 + Android SDK; in-repo `DesignSystem/` composite)

**Ship process (PRs to main):** [docs/SHIP_STANDARD.md](docs/SHIP_STANDARD.md)

**Verify:** `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`

**Detailed docs:** [CLAUDE.md](CLAUDE.md) | [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) | [STATUS.md](STATUS.md) | [QA_CHECKLIST.md](QA_CHECKLIST.md) | [ROADMAP_HANDOFF.md](ROADMAP_HANDOFF.md)

## License

License: Proprietary — source available for viewing; this project is not open source.

Copyright is retained by the project owner. No permission is granted to
redistribute, modify, sublicense, sell, commercially exploit, or create
derivative works from this project except where required by applicable law.
Third-party dependencies remain under their own licenses. See
[`LICENSE.md`](LICENSE.md).
