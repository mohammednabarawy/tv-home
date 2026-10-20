# StarNet Twin 4000 Plus — experimental tv-home port

This branch adapts [tv-home](https://github.com/mihirgupta0900/tv-home) for the StarNet Twin 4000 Plus hybrid satellite/Android receiver. It is an **experimental test app**, not a replacement firmware. Upstream remains credited under MIT.

## Changes
- The upstream launcher requires Android 11 (API 30). This build has min/target SDK 28 for Android 9, **subject to on-device version verification**.
- Discovers both TV/Leanback and ordinary Android application launcher activities.
- Adds LAUNCHER and LEANBACK_LAUNCHER entries for safe testing. Deliberately omits HOME so the app cannot replace the stock StarNet home during receiver validation.
- Uses safe default favorite applications; never hides/disables any StarNet or satellite/tuner component.
- Keeps the original UI and rotating wallpapers.
- All stock applications, IPTV settings and tuner services remain untouched; StarNet vendor features may be embedded rather than distinct Android apps.

## Diagnostic first

Connect the device to an authorized ADB session and run:
- Windows PowerShell: `powershell -ExecutionPolicy Bypass -File tools/starnet-probe.ps1`
- Linux/macOS: `bash tools/starnet-probe.sh`

Inspect `02-api-level.txt`, `03-device-model.txt`, `09-home-resolution.txt`, `10-launcher-activities.txt`. Some vendor firmwares do not support ADB or third-party HOME. Avoid sharing identifying information from device diagnostics.

## Build

GitHub Actions: select **StarNet Android 9 test APK**, run workflow (or push to the branch), download the artifact after a green run.

Local: JDK 17; Android SDK platform 34, build tools 34.0.0; Bash; `bash launcher/build-and-install.sh --build`.

## Safe installation and testing

```bash
adb devices
adb install -r launcher/build/tvhome-starnet-android9.apk
adb shell am start -n com.mihir.tvhome/.MainActivity
```

Test D-pad, OK, Back, Wi-Fi, app dock, installed apps, remote HOME/EXIT, channel selection, satellite TV/IPTV and settings. Press Home to return to the vendor home. If needed, remove with `adb uninstall com.mihir.tvhome`.

**Do not run upstream TCL debloat scripts or disable any stock receiver/system packages.** This variant's installer never calls `pm disable` or `set-home-activity`; the test app doesn't declare HOME and cannot be selected as default via HOME chooser.

## What remains unverified

Android version/firmware on the actual receiver; tuner app package/component, home-key interception, third-party app permissions, TV inputs APIs, screen resolution, and real remote key mapping. Do not set a default HOME until TV/tuner access and exit path are verified. A hardware test is required before calling the port compatible.

To add the satellite-TV app after diagnosis: set the exact installed package in `launcher/res/values/config.xml` under `live_tv_package`, then rebuild. Some proprietary satellite apps are not exposed as launchable Android activities and will need a vendor-specific intent/shortcut.

## Manufacturer UI integration

The StarNet 4000 Twin Plus has a proprietary Android 9 satellite receiver launcher with firmware-specific functions (live TV, services, IPTV, satellite lists, favorites, EPG, recording, SAT/EPG/APP remote keys). Public firmware reports mention V4.0.0 dated 2026-10-17. These controls may live inside one vendor-owned package or may be private internal Activities, so generic app discovery alone does not guarantee them. Before any attempt to replace HOME, collect diagnostics (read-only), inspect package/activity wiring, map remote key behavior, and test dedicated links back to the vendor UI. This project should implement a cosmetic home-screen replacement, not any package debloat or stock system modification. Do not flash firmware to use the launcher.
