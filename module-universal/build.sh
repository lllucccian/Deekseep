#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

source ../scripts/android-tools.sh
source ../scripts/androidx-path-parser.sh
RISH_DEX="../third_party/shizuku/rish_shizuku.dex"
RISH_SHA256="1953c1fd9708904f8fc1f67774843b4cc3d03e5f2a578ff4d654d0625456bc28"
GOOGLE_PLAY_BUILD="${GOOGLE_PLAY_BUILD:-false}"
if [[ "$GOOGLE_PLAY_BUILD" == "true" ]]; then
  GOOGLE_PLAY_VALUE=true
  APK_NAME="ds-probe-universal-google-play.apk"
else
  GOOGLE_PLAY_VALUE=false
  APK_NAME="ds-probe-universal.apk"
fi
OUT=build

if [ ! -f "$RISH_DEX" ] \
    || ! printf '%s  %s\n' "$RISH_SHA256" "$RISH_DEX" \
        | sha256sum -c - >/dev/null 2>&1; then
  echo "Missing or modified verified Shizuku rish payload: $RISH_DEX" >&2
  exit 1
fi
if [ ! -f ../module/debug.keystore ]; then
  keytool -genkeypair -keystore ../module/debug.keystore -storepass android \
    -keypass android -alias androiddebugkey \
    -dname "CN=Deekseep Source Build,O=Deekseep,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null
fi

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/generated-src/com/dsmod/probe"
ANDROIDX_PATH_PARSER_JAR="$(prepare_androidx_path_parser "$OUT")"

echo "[0/7] generate BuildInfo.java"
MODULE_VER=$(grep -oE 'android:versionName="[^"]+"' AndroidManifest.xml \
  | head -n1 | cut -d'"' -f2)
cat > "$OUT/generated-src/com/dsmod/probe/BuildInfo.java" <<EOF
package com.dsmod.probe;
public final class BuildInfo {
    public static final String API_VERSION = "universal (Xposed API 82-102 verified)";
    public static final String MODULE_VERSION = "${MODULE_VER:-unknown} Open";
    public static final String BUILD_DATE = "$(date '+%Y-%m-%d %H:%M')";
    public static final boolean GOOGLE_PLAY = ${GOOGLE_PLAY_VALUE};
    public static final boolean PROTECTED_BUILD = false;
    public static final String PROTECTED_PAYLOAD_KEY_A = "";
    public static final String PROTECTED_PAYLOAD_KEY_B = "";
    public static final String PROTECTED_PAYLOAD_IV = "";
    public static final String PROTECTED_PAYLOAD_SHA256 = "";
    private BuildInfo() {}
}
EOF

cp ../module/src/com/dsmod/probe/Main.java \
  "$OUT/generated-src/com/dsmod/probe/Main.java"

echo "[1/7] collect open-source core and Xposed adapter"
find ../module/src/com/dsmod/probe -maxdepth 1 -name '*.java' \
  ! -name Main.java ! -name BuildInfo.java > "$OUT/sources.txt"
find ../module/src/com/dsmod/relay -name '*.java' >> "$OUT/sources.txt"
find ../module-legacy/compat -name '*.java' >> "$OUT/sources.txt"
find ../module-legacy/src/de -name '*.java' >> "$OUT/sources.txt"
find "$OUT/generated-src" -name '*.java' >> "$OUT/sources.txt"

echo "[2/7] javac"
if ! javac -source 8 -target 8 \
    -cp "$ANDROID_JAR:$ANDROIDX_PATH_PARSER_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt" 2> "$OUT/javac.err"; then
  cat "$OUT/javac.err"
  exit 1
fi
grep -v 'warning:' "$OUT/javac.err" || true

echo "[3/7] d8"
MODCLASSES=$(find "$OUT/classes/com/dsmod" -name '*.class')
$D8 --min-api 24 --output "$OUT/dex" $MODCLASSES \
  "$ANDROIDX_PATH_PARSER_JAR" --lib "$ANDROID_JAR"

echo "[4/7] aapt2"
$AAPT2 compile --dir res -o "$OUT/res.zip"
$AAPT2 link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml -R "$OUT/res.zip" --auto-add-overlay

echo "[5/7] package Xposed metadata and Agent runtime"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex )
mkdir -p "$OUT/xstage/assets" "$OUT/xstage/META-INF/com.dsmod.probe.agent"
cp assets/xposed_init "$OUT/xstage/assets/xposed_init"
cp "$RISH_DEX" \
  "$OUT/xstage/META-INF/com.dsmod.probe.agent/.rish_shizuku_runtime_payload.dat"
( cd "$OUT/xstage" && zip -q -9 -r ../unsigned.apk META-INF assets )

echo "[6/7] zipalign"
$ZIPALIGN -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[7/7] sign"
$APKSIGNER sign --ks ../module/debug.keystore --ks-pass pass:android \
  --key-pass pass:android --out "$APK_NAME" "$OUT/aligned.apk"
echo "DONE -> $(pwd)/$APK_NAME"
