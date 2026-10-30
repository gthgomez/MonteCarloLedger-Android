# MonteCarloLedger

Modern financial ledger application for Android. Focuses on deterministic forecasting, bill pacing, and secure encrypted backups.

> **Status: proprietary.** This repository is public for source visibility and
> transparency. It is **not open source** — there is no license grant to reuse,
> modify, or redistribute this code. See [LICENSE](LICENSE).

Companion app to the [MonteCarlo-Ledger CLI and Monte Carlo engine](https://github.com/gthgomez/MonteCarlo-Ledger): the same ledger-first, deterministic-forecasting philosophy on Android.

## Showcase — See your ledger and upcoming bills

An Android companion to [MonteCarlo-Ledger](https://github.com/gthgomez/MonteCarlo-Ledger), with a ledger dashboard, bill pacing, deterministic forecasting, and encrypted backup workflows. The companion relationship does not establish calculation parity between the two implementations.

| Explore | What the app provides |
| --- | --- |
| Ledger | Enter and review transactions and balances |
| Planning | Upcoming payments, bill pacing, and forecast views |
| Backup | AES-GCM backup workflows; restoration needs its own verification |

**Demo status:** runtime screenshots and video are pending. The README showcase pass
on 2026-10-30 inspected source at [`6cadf72c50ca`](https://github.com/gthgomez/MonteCarloLedger-Android/tree/6cadf72c50ca6466f342a7263bacacf9145b28d3);
this cloud environment has no Android SDK, emulator, or attached Android device.
See the [capture checklist and evidence limits](docs/showcase/README.md).

### Try it locally

JDK 17, Android SDK platform 36, the checked-in DesignSystem/ composite, and an Android 8.0+ device or emulator.

From the repository root (Windows: `gradlew.bat`):

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

**Tech stack:** Kotlin, Jetpack Compose, Material3, Room (SQLite), AES-GCM encrypted backups, Clean Architecture (Domain/Data/UI).

**Build:** `.\gradlew.bat assembleDebug` (requires JDK 17 + Android SDK; in-repo `DesignSystem/` composite)

**Ship process (PRs to main):** [docs/SHIP_STANDARD.md](docs/SHIP_STANDARD.md)

**Verify:** `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon`

**Project docs:** [docs/SHIP_STANDARD.md](docs/SHIP_STANDARD.md) | [STATUS.md](STATUS.md) | [QA_CHECKLIST.md](QA_CHECKLIST.md) | [ROADMAP_HANDOFF.md](ROADMAP_HANDOFF.md)

Internal agent notes (not project documentation): [docs/agent/](docs/agent/)

## License

License: Proprietary — source available for viewing; this project is not open source.

Copyright is retained by the project owner. No permission is granted to
redistribute, modify, sublicense, sell, commercially exploit, or create
derivative works from this project except where required by applicable law.
Third-party dependencies remain under their own licenses. See
[`LICENSE`](LICENSE).
