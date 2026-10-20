#!/usr/bin/env bash
# Read-only diagnostic collection for a receiver connected via ADB.
set -euo pipefail
command -v adb >/dev/null || { echo "adb missing" >&2; exit 1; }
adb devices
OUT="starnet-diagnostics-$(date '+%Y%m%d-%H%M%S')"
mkdir -p "$OUT"
adb shell getprop ro.build.version.release > "$OUT/01-android-version.txt"
adb shell getprop ro.build.version.sdk > "$OUT/02-api-level.txt"
adb shell getprop ro.product.model > "$OUT/03-device-model.txt"
adb shell getprop ro.product.manufacturer > "$OUT/04-manufacturer.txt"
adb shell getprop ro.board.platform > "$OUT/05-platform.txt"
adb shell getprop ro.product.cpu.abi > "$OUT/06-cpu-abi.txt"
adb shell getprop ro.build.display.id > "$OUT/06b-android-build.txt"
adb shell getprop ro.build.version.incremental > "$OUT/06c-android-incremental.txt"
adb shell getprop ro.build.version.security_patch > "$OUT/06d-security-patch.txt"
adb shell pm list packages > "$OUT/07-packages.txt"
adb shell pm list packages -3 > "$OUT/08-user-apps.txt"
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$OUT/09-home-resolution.txt" 2>&1 || true
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER > "$OUT/10-launcher-activities.txt" 2>&1 || true
adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME > "$OUT/11-home-activities.txt" 2>&1 || true
adb shell dumpsys activity activities > "$OUT/12-current-activity.txt" 2>&1 || true
adb shell dumpsys window windows > "$OUT/13-window-focus.txt" 2>&1 || true
echo "Saved read-only diagnostics to $OUT."
echo "Run this while the receiver displays its original Glide home screen."
echo "Look for mResumedActivity and mCurrentFocus in files 12/13."
echo "Review/redact package and device details before sharing. Do not share identifiers or activation data."
