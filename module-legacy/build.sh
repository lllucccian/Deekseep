#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

source ../scripts/android-tools.sh
source ../scripts/androidx-path-parser.sh
OUT=build
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/generated-src/com/dsmod/probe"
ANDROIDX_PATH_PARSER_JAR="$(prepare_androidx_path_parser "$OUT")"

cat > "$OUT/generated-src/com/dsmod/probe/BuildInfo.java" <<EOF
package com.dsmod.probe;
public final class BuildInfo {
    public static final String API_VERSION = "82+ (legacy adapter)";
    public static final String MODULE_VERSION = "1.7.4-fix Open";
    public static final String BUILD_DATE = "$(date '+%Y-%m-%d %H:%M')";
    public static final boolean GOOGLE_PLAY = false;
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

find ../module/src/com/dsmod/probe -maxdepth 1 -name '*.java' \
  ! -name Main.java ! -name BuildInfo.java > "$OUT/sources.txt"
find ../module/src/com/dsmod/relay -name '*.java' >> "$OUT/sources.txt"
find compat -name '*.java' >> "$OUT/sources.txt"
find src/de -name '*.java' >> "$OUT/sources.txt"
find "$OUT/generated-src" -name '*.java' >> "$OUT/sources.txt"

if ! javac -source 8 -target 8 \
    -cp "$ANDROID_JAR:$ANDROIDX_PATH_PARSER_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt" 2> "$OUT/javac.err"; then
  cat "$OUT/javac.err"
  exit 1
fi
MODCLASSES=$(find "$OUT/classes/com/dsmod" -name '*.class')
$D8 --min-api 24 --output "$OUT/dex" $MODCLASSES \
  "$ANDROIDX_PATH_PARSER_JAR" --lib "$ANDROID_JAR"
$AAPT2 compile --dir res -o "$OUT/res.zip"
$AAPT2 link -o "$OUT/base.apk" -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml -R "$OUT/res.zip" --auto-add-overlay
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q ../unsigned.apk classes.dex )
mkdir -p "$OUT/xstage/assets"
cp assets/xposed_init "$OUT/xstage/assets/xposed_init"
( cd "$OUT/xstage" && zip -q -9 -r ../unsigned.apk assets )
$ZIPALIGN -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
if [ ! -f legacy.keystore ]; then
  keytool -genkeypair -keystore legacy.keystore -storepass deekseep \
    -keypass deekseep -alias deekseeplegacy \
    -dname "CN=Deekseep Legacy,O=Deekseep,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null
fi
$APKSIGNER sign --ks legacy.keystore --ks-pass pass:deekseep \
  --key-pass pass:deekseep --out ds-probe-legacy.apk "$OUT/aligned.apk"
echo "DONE -> $(pwd)/ds-probe-legacy.apk"
