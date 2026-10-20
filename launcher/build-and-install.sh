#!/usr/bin/env bash
# Safe StarNet launcher build. Never disables, uninstalls or replaces stock TV components.
# Usage: bash launcher/build-and-install.sh --build (default)
#        bash launcher/build-and-install.sh --install (ADB side-load only)
set -euo pipefail
cd "$(dirname "$0")"
MODE="${1:---build}"
if [[ "$MODE" != "--build" && "$MODE" != "--install" ]]; then
  echo "Usage: $0 [--build|--install]" >&2; exit 2
fi
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
BT="${BUILD_TOOLS:-$SDK/build-tools/34.0.0}"
JAR="${ANDROID_PLATFORM_JAR:-$SDK/platforms/android-34/android.jar}"
MIN_SDK="${MIN_SDK:-28}"
TARGET_SDK="${TARGET_SDK:-28}"
for p in "$BT/aapt2" "$BT/d8" "$BT/zipalign" "$BT/apksigner" "$JAR"; do
  [[ -f "$p" ]] || { echo "Missing Android SDK file: $p" >&2; exit 1; }
done
for c in javac keytool zip; do command -v "$c" >/dev/null || { echo "Missing: $c" >&2; exit 1; }; done
B=build
rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/dex"
"$BT/aapt2" compile --dir res -o "$B/res.zip"
"$BT/aapt2" link -o "$B/base.apk" -I "$JAR" --manifest AndroidManifest.xml --java "$B/gen" \
    -A assets --min-sdk-version "$MIN_SDK" --target-sdk-version "$TARGET_SDK" "$B/res.zip"
mapfile -t JAVA_FILES < <(find src "$B/gen" -type f -name '*.java')
javac -nowarn -Xlint:-options --release 11 -classpath "$JAR" -d "$B/classes" "${JAVA_FILES[@]}"
mapfile -t CLASS_FILES < <(find "$B/classes" -type f -name '*.class')
"$BT/d8" --min-api "$MIN_SDK" --lib "$JAR" --output "$B/dex" "${CLASS_FILES[@]}"
cp "$B/base.apk" "$B/unsigned.apk"
(cd "$B/dex" && zip -q -u ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 "$B/unsigned.apk" "$B/aligned.apk"
if [[ ! -f debug.keystore ]]; then
  keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
    -alias tvhome -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=StarNet tv-home test" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks debug.keystore --ks-pass pass:android --out "$B/tvhome-starnet-android9.apk" "$B/aligned.apk"
echo "Built $B/tvhome-starnet-android9.apk"
if [[ "$MODE" == "--install" ]]; then
  command -v adb >/dev/null || { echo "ADB not found" >&2; exit 1; }
  adb devices
  adb install -r "$B/tvhome-starnet-android9.apk"
  adb shell am start -n com.mihir.tvhome/.MainActivity
  echo "Test app launched. Stock home and satellite services unchanged."
fi
