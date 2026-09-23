#!/usr/bin/env bash
# Local verification build: compiles the complete module source tree the same way
# build.sh does in steps [0/7]-[2/7], but stops before dex/apk packaging (d8, aapt2,
# zipalign, apksigner and `zip` are not available on this Windows host).
set -e
cd "$(dirname "$0")"

SDK_JAR="${ANDROID_JAR:-d:/Android_SDK/platforms/android-35/android.jar}"
JSON_JAR="../module/build/test-deps/json-20240303.jar"
OUT=build

if [[ ! -f "$SDK_JAR" ]]; then
    echo "android.jar not found at $SDK_JAR" >&2
    exit 1
fi
if [[ ! -f "$JSON_JAR" ]]; then
    echo "org.json not found at $JSON_JAR" >&2
    exit 1
fi

rm -rf "$OUT/classes"
mkdir -p "$OUT/classes" "$OUT/generated-src/com/dsmod/probe"

echo "[1/3] generate BuildInfo.java"
MODULE_VER=$(grep -oE 'android:versionName="[^"]+"' AndroidManifest.xml \
  | head -n1 | cut -d'"' -f2)
cat > "$OUT/generated-src/com/dsmod/probe/BuildInfo.java" <<EOF
package com.dsmod.probe;
public final class BuildInfo {
    public static final String API_VERSION = "universal (Xposed API 82-102 verified)";
    public static final String MODULE_VERSION = "${MODULE_VER:-unknown}";
    public static final String BUILD_EDITION = "Open";
    public static final String DISPLAY_VERSION = MODULE_VERSION + " " + BUILD_EDITION;
    public static final String BUILD_DATE = "$(date '+%Y-%m-%d %H:%M')";
    public static final boolean GOOGLE_PLAY = false;
    public static final boolean PROTECTED_BUILD = false;
    public static final boolean LOCAL_API_INCLUDED = false;
    public static final String CLOUD_LOCAL_API_PAYLOAD_NAME = "";
    public static final boolean GOOGLE_V241_BASIC = false;
    public static final boolean SHI_V5_V241 = false;
    public static final String SHI_V5_HOST_PACKAGE = "";
    public static final String SHI_V5_HOST_VERSION_NAME = "";
    public static final long SHI_V5_HOST_VERSION_CODE = -1L;
    public static final String SHI_V5_HOST_CERT_SHA256 = "";
    public static final String SHI_V5_HOST_CERT_SHA256_ALT = "";
    public static final String SHI_V5_V236_HOST_VERSION_NAME = "";
    public static final long SHI_V5_V236_HOST_VERSION_CODE = -1L;
    public static final String PROTECTED_PAYLOAD_KEY_A = "";
    public static final String PROTECTED_PAYLOAD_KEY_B = "";
    public static final String PROTECTED_PAYLOAD_IV = "";
    public static final String PROTECTED_PAYLOAD_SHA256 = "";
    public static final String PROTECTED_PAYLOAD_NAME = "";
    public static final String LEGACY_PROTECTED_PAYLOAD_NAME = "";
    public static final String SHI_CORE_SHA256 = "";
    private BuildInfo() {}
}
EOF

cp ../module/src/com/dsmod/probe/Main.java "$OUT/generated-src/com/dsmod/probe/Main.java"

echo "[2/3] collect sources"
find ../module/src/com/dsmod/probe -maxdepth 1 -name '*.java' \
  ! -name Main.java ! -name BuildInfo.java > "$OUT/sources.txt"
find ../module/src/com/dsmod/relay -name '*.java' >> "$OUT/sources.txt"
find ../module-legacy/compat -name '*.java' >> "$OUT/sources.txt"
find ../module-legacy/src/de -name '*.java' >> "$OUT/sources.txt"
find "$OUT/generated-src" -name '*.java' >> "$OUT/sources.txt"
echo "     $(wc -l < "$OUT/sources.txt") source files"

echo "[3/3] javac"
if ! javac -source 8 -target 8 -nowarn \
    -cp "$SDK_JAR;$JSON_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt" 2> "$OUT/javac.err"; then
  cat "$OUT/javac.err"
  exit 1
fi
echo "COMPILE OK -> $(pwd)/$OUT/classes"
