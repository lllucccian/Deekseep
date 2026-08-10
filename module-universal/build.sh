#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

source ../scripts/android-tools.sh
source ../scripts/androidx-path-parser.sh
JSCH_JAR="../third_party/jsch/jsch-2.28.2.jar"
RISH_DEX="../third_party/shizuku/rish_shizuku.dex"
RISH_SHA256="1953c1fd9708904f8fc1f67774843b4cc3d03e5f2a578ff4d654d0625456bc28"
GOOGLE_PLAY_BUILD="${GOOGLE_PLAY_BUILD:-false}"
PROTECTED_BUILD="${PROTECTED_BUILD:-false}"
if [[ "$GOOGLE_PLAY_BUILD" == "true" ]]; then
  GOOGLE_PLAY_VALUE=true
  if [[ "$PROTECTED_BUILD" == "true" ]]; then
    APK_NAME="ds-probe-universal-google-play-protected.apk"
  else
    APK_NAME="ds-probe-universal-google-play.apk"
  fi
else
  GOOGLE_PLAY_VALUE=false
  if [[ "$PROTECTED_BUILD" == "true" ]]; then
    APK_NAME="ds-probe-universal-protected.apk"
  else
    APK_NAME="ds-probe-universal.apk"
  fi
fi
if [[ "$PROTECTED_BUILD" == "true" ]]; then
  PROTECTED_VALUE=true
  OUT=build-protected
  command -v openssl >/dev/null 2>&1 || {
    echo "OpenSSL is required for the protected DEX payload" >&2
    exit 1
  }
  PROTECTED_PAYLOAD_KEY=$(openssl rand -hex 32)
  PROTECTED_PAYLOAD_KEY_A=${PROTECTED_PAYLOAD_KEY:0:32}
  PROTECTED_PAYLOAD_KEY_B=${PROTECTED_PAYLOAD_KEY:32:32}
  PROTECTED_PAYLOAD_IV=$(openssl rand -hex 16)
else
  PROTECTED_VALUE=false
  OUT=build
  PROTECTED_PAYLOAD_KEY=""
  PROTECTED_PAYLOAD_KEY_A=""
  PROTECTED_PAYLOAD_KEY_B=""
  PROTECTED_PAYLOAD_IV=""
fi

if [ ! -f ../module/debug.keystore ]; then
  # Keep clean checkouts buildable in CI while preserving the developer's existing
  # signing key when one is present locally.
  keytool -genkeypair -v \
    -keystore ../module/debug.keystore \
    -storepass android -keypass android \
    -alias androiddebugkey \
    -dname 'CN=Android Debug,O=Android,C=US' \
    -keyalg RSA -keysize 2048 -validity 10000 >/dev/null 2>&1
fi
if [ ! -f "$RISH_DEX" ] \
    || ! printf '%s  %s\n' "$RISH_SHA256" "$RISH_DEX" \
        | sha256sum -c - >/dev/null 2>&1; then
  echo "Missing or modified verified Shizuku rish payload: $RISH_DEX" >&2
  exit 1
fi

rm -rf $OUT
mkdir -p $OUT/classes $OUT/dex $OUT/generated-src/com/dsmod/probe
ANDROIDX_PATH_PARSER_JAR="$(prepare_androidx_path_parser "$OUT")"

echo "[0/7] generate universal adapter and BuildInfo.java"
MODULE_VER=$(grep -oE 'android:versionName="[^"]+"' AndroidManifest.xml | head -n1 | cut -d'"' -f2)
cat > $OUT/generated-src/com/dsmod/probe/BuildInfo.java <<EOF
package com.dsmod.probe;
public final class BuildInfo {
    public static final String API_VERSION = "universal (Xposed API 82-102 verified)";
    public static final String MODULE_VERSION = "${MODULE_VER:-unknown}";
    public static final String BUILD_DATE = "$(date '+%Y-%m-%d %H:%M')";
    public static final boolean GOOGLE_PLAY = ${GOOGLE_PLAY_VALUE};
    public static final boolean PROTECTED_BUILD = ${PROTECTED_VALUE};
    public static final String PROTECTED_PAYLOAD_KEY_A = "${PROTECTED_PAYLOAD_KEY_A}";
    public static final String PROTECTED_PAYLOAD_KEY_B = "${PROTECTED_PAYLOAD_KEY_B}";
    public static final String PROTECTED_PAYLOAD_IV = "${PROTECTED_PAYLOAD_IV}";
    private BuildInfo() {}
}
EOF

cp ../module/src/com/dsmod/probe/Main.java \
  $OUT/generated-src/com/dsmod/probe/Main.java

echo "[1/7] collect canonical sources plus the universal Xposed adapter"
find ../module/src/com/dsmod/probe -maxdepth 1 -name "*.java" \
  ! -name Main.java ! -name BuildInfo.java > $OUT/sources.txt
find ../module/src/com/dsmod/relay -name "*.java" >> $OUT/sources.txt
find ../module-legacy/compat -name "*.java" >> $OUT/sources.txt
find ../module-legacy/src/de -name "*.java" >> $OUT/sources.txt
find $OUT/generated-src -name "*.java" >> $OUT/sources.txt

echo "[2/7] javac (universal core + bundled JSch)"
if ! javac -source 8 -target 8 -cp "$ANDROID_JAR:$JSCH_JAR:$ANDROIDX_PATH_PARSER_JAR" \
        -d $OUT/classes @$OUT/sources.txt 2> $OUT/javac.err; then
  cat $OUT/javac.err
  exit 1
fi
grep -v "warning:" $OUT/javac.err || true

if [[ "$PROTECTED_BUILD" == "true" ]]; then
  echo "[3/7] r8 protected mode (obfuscation + optimization)"
  R8_BIN="${R8:-$SDK_ROOT/cmdline-tools/latest/bin/r8}"
  if [ ! -x "$R8_BIN" ]; then
    echo "R8 not found: $R8_BIN" >&2
    exit 1
  fi
  jar cf $OUT/program.jar -C $OUT/classes com/dsmod
  jar cf $OUT/xposed-api.jar -C $OUT/classes de
  "$R8_BIN" --release --min-api 24 --output $OUT/dex \
      --pg-conf protected-rules.pro --pg-map-output $OUT/mapping.txt \
      --lib "$ANDROID_JAR" --lib $OUT/xposed-api.jar \
      $OUT/program.jar "$JSCH_JAR" "$ANDROIDX_PATH_PARSER_JAR"

  echo "[3.5/7] compile and encrypt executable secondary DEX payload"
  mkdir -p $OUT/protected-payload/classes $OUT/protected-payload/dex
  find protected-payload-src -name "*.java" > $OUT/protected-payload/sources.txt
  javac -source 8 -target 8 -cp "$ANDROID_JAR" \
      -d $OUT/protected-payload/classes @$OUT/protected-payload/sources.txt
  PAYLOAD_CLASSES=$(find $OUT/protected-payload/classes -name "*.class")
  $D8 --min-api 24 --output $OUT/protected-payload/dex $PAYLOAD_CLASSES \
      --lib "$ANDROID_JAR"
  openssl enc -aes-256-ctr -K "$PROTECTED_PAYLOAD_KEY" -iv "$PROTECTED_PAYLOAD_IV" \
      -in $OUT/protected-payload/dex/classes.dex \
      -out $OUT/protected-payload/runtime_payload.dat
else
  echo "[3/7] d8 (module classes + JSch; Xposed API provided by framework)"
  MODCLASSES=$(find $OUT/classes/com/dsmod -name "*.class")
  $D8 --min-api 24 --output $OUT/dex $MODCLASSES "$JSCH_JAR" \
    "$ANDROIDX_PATH_PARSER_JAR" \
    --lib "$ANDROID_JAR"
fi

echo "[4/7] aapt2 link (manifest + res -> base.apk)"
$AAPT2 compile --dir res -o $OUT/res.zip
$AAPT2 link -o $OUT/base.apk -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -R $OUT/res.zip \
    --auto-add-overlay

echo "[5/7] add dex + assets/xposed_init into APK"
cp $OUT/base.apk $OUT/unsigned.apk
( cd $OUT/dex && zip -q ../unsigned.apk classes.dex )
mkdir -p $OUT/xstage/assets
cp assets/xposed_init $OUT/xstage/assets/xposed_init
PROMPT_META="$OUT/xstage/META-INF/com.github.mwiede.jsch/internal/transport/authentication"
mkdir -p "$PROMPT_META"
cp ../third_party/jsch/bundled-meta/.com_github_mwiede_jsch_transport_authentication_negotiation_runtime_policy_extension_20260727_v2.dat \
  "$PROMPT_META/runtime_policy_extension_20260727_v2.dat"
RISH_META="$OUT/xstage/META-INF/com.dsmod.probe.agent"
mkdir -p "$RISH_META"
cp "$RISH_DEX" "$RISH_META/.rish_shizuku_runtime_payload.dat"
if [[ "$PROTECTED_BUILD" == "true" ]]; then
  PROTECTED_META="$OUT/xstage/META-INF/com.dsmod.protected"
  mkdir -p "$PROTECTED_META"
  cp $OUT/protected-payload/runtime_payload.dat "$PROTECTED_META/.runtime_payload.dat"
fi
CLOUDFLARED_NATIVE=../third_party/cloudflared/android
# Build for the connected arm64-v8a device; the universal label refers to
# Xposed API compatibility, not unrelated CPU payloads.
for ABI in arm64-v8a; do
  SOURCE="$CLOUDFLARED_NATIVE/$ABI/libcloudflared.so"
  if [ ! -f "$SOURCE" ]; then
    echo "Missing bundled cloudflared for $ABI: $SOURCE" >&2
    exit 1
  fi
  mkdir -p "$OUT/xstage/lib/$ABI"
  cp "$SOURCE" "$OUT/xstage/lib/$ABI/libcloudflared.so"
done
( cd $OUT/xstage && zip -q -9 -r ../unsigned.apk META-INF assets lib )

echo "[6/7] zipalign"
$ZIPALIGN -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk

echo "[7/7] sign with the primary APK key"
$APKSIGNER sign --ks ../module/debug.keystore --ks-pass pass:android \
    --key-pass pass:android --out "$APK_NAME" $OUT/aligned.apk

echo "DONE -> $(pwd)/$APK_NAME"
for PUB in /storage/emulated/0 /sdcard; do
  if [ -d "$PUB" ] \
      && cp -f "$APK_NAME" "$PUB/$APK_NAME" 2>/dev/null; then
    echo "COPIED -> $PUB/$APK_NAME"
    break
  fi
done
