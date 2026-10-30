# Showcase capture status

Source inspected: [`6cadf72c50ca6466f342a7263bacacf9145b28d3`](https://github.com/gthgomez/MonteCarloLedger-Android/tree/6cadf72c50ca6466f342a7263bacacf9145b28d3).
Inspection date: 2026-10-30. This is a source/documentation pass, not a recorded app run.

## Evidence available

No verified runtime screenshots or clips are checked into this repository at the
inspected revision. No new runtime media was created for this pass.

## Execution blocker

The selected cloud environment has JDK 21 but no Android SDK installation,
`ANDROID_HOME`/`ANDROID_SDK_ROOT`, `adb`, `emulator`, `sdkmanager`, `/dev/kvm`,
or attached Android device. This repository’s existing JDK 17 setup and Android
prerequisites were inspected rather than replaced with a large toolchain download.

Required next setup: JDK 17, Android SDK platform 36, the checked-in DesignSystem/ composite, and an Android 8.0+ device or emulator.

No Android build, financial workflow, backup, restoration, or physical-device check was executed for this showcase.

## Capture a genuine demonstration

Start in a clean test profile with synthetic accounts only. Add a clearly fictional income and bill, show the dashboard/history and forecast, and label every displayed balance as sample data. Do not import personal bank exports or demonstrate restoration over existing user data.

After installing and manually opening the debug build on the test device, record
screens using Android's actual display output (requires Android platform tools):

```sh
mkdir -p docs/showcase
adb exec-out screencap -p > docs/showcase/screen.png
# Stop recording with Ctrl-C after the short walkthrough.
adb shell screenrecord --time-limit 20 /sdcard/showcase.mp4
adb pull /sdcard/showcase.mp4 docs/showcase/demo.mp4
adb shell rm /sdcard/showcase.mp4
```

Inspect every frame before publication. Keep the clip short and compress it for
GitHub; do not add large model/APK/build/ROM/save files. Add captions identifying
source commit, build variant, device/Android version, fixture origin, and which
steps were really exercised. Scripted UI states and design references must stay
labeled separately from device and end-to-end evidence.

## Source and verification references

[Project context](../agent/PROJECT_CONTEXT.md), [ship standard](../SHIP_STANDARD.md), and [QA checklist](../../QA_CHECKLIST.md).

This pass changes README/capture documentation only. Local paths and links are
checked; Android Gradle, connected, release, and hardware gates remain unrun.
