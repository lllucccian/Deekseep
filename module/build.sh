#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

JSCH_JAR="../third_party/jsch/jsch-2.28.2.jar"
API_JAR="libs/api.jar"
source ../scripts/android-tools.sh

OUT=build
rm -rf $OUT
mkdir -p $OUT/classes $OUT/dex

echo "[0/6] generate BuildInfo.java (libxposed API 102)"
API_VER=$(grep -oE 'targetApiVersion=[0-9]+' xposed/module.prop | cut -d= -f2)
MODULE_VER=$(grep -oE 'android:versionName="[^"]+"' AndroidManifest.xml | head -n1 | cut -d'"' -f2)
cat > src/com/dsmod/probe/BuildInfo.java <<EOF
package com.dsmod.probe;
public final class BuildInfo {
    public static final String API_VERSION = "${API_VER:-102}";
    public static final String MODULE_VERSION = "${MODULE_VER:-unknown}";
    public static final String BUILD_DATE = "$(date '+%Y-%m-%d %H:%M')";
    public static final boolean GOOGLE_PLAY = false;
    private BuildInfo() {}
}
EOF

echo "[1/6] javac (libxposed API 102 + bundled JSch)"
find src -name "*.java" > $OUT/sources.txt
if ! javac -source 8 -target 8 -cp "$ANDROID_JAR:$API_JAR:$JSCH_JAR" \
        -d $OUT/classes @$OUT/sources.txt 2> $OUT/javac.err; then
  cat $OUT/javac.err
  exit 1
fi
grep -v "warning:" $OUT/javac.err || true

echo "[2/6] d8 (module classes + JSch; libxposed API provided by framework)"
MODCLASSES=$(find $OUT/classes/com/dsmod -name "*.class")
$D8 --min-api 24 --output $OUT/dex $MODCLASSES "$JSCH_JAR" --lib "$ANDROID_JAR"

echo "[3/6] aapt2 link (manifest + res -> base.apk)"
$AAPT2 compile --dir res -o $OUT/res.zip
$AAPT2 link -o $OUT/base.apk -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -R $OUT/res.zip \
    --auto-add-overlay

echo "[4/6] add dex + META-INF/xposed into apk"
cp $OUT/base.apk $OUT/unsigned.apk
( cd $OUT/dex && zip -q ../unsigned.apk classes.dex )
mkdir -p $OUT/xstage/META-INF/xposed
cp xposed/java_init.list $OUT/xstage/META-INF/xposed/java_init.list
cp xposed/module.prop    $OUT/xstage/META-INF/xposed/module.prop
cp xposed/scope.list     $OUT/xstage/META-INF/xposed/scope.list
PROMPT_META="$OUT/xstage/META-INF/com.github.mwiede.jsch/internal/transport/authentication"
mkdir -p "$PROMPT_META"
cp ../third_party/jsch/bundled-meta/.com_github_mwiede_jsch_transport_authentication_negotiation_runtime_policy_extension_20260727_v2.dat \
  "$PROMPT_META/.com_github_mwiede_jsch_transport_authentication_negotiation_runtime_policy_extension_20260727_v2.dat"
CLOUDFLARED_NATIVE=../third_party/cloudflared/android
# This build targets the connected device (arm64-v8a). Keeping unrelated ABI
# payloads would add roughly 80 MB uncompressed to every universal package.
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

echo "[5/6] zipalign"
$ZIPALIGN -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk

echo "[6/6] sign"
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
    -alias androiddebugkey -dname "CN=Android Debug,O=Android,C=US" \
    -keyalg RSA -keysize 2048 -validity 10000
fi
$APKSIGNER sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out ds-probe-api102.apk $OUT/aligned.apk
cp ds-probe-api102.apk ds-probe.apk

echo "DONE -> $(pwd)/ds-probe-api102.apk"

# Make a best-effort shared-storage copy for direct installation on a device.
for PUB in /storage/emulated/0 /sdcard; do
  if [ -d "$PUB" ] \
      && cp -f ds-probe-api102.apk "$PUB/ds-probe-api102.apk" 2>/dev/null; then
    cp -f ds-probe-api102.apk "$PUB/ds-probe.apk" 2>/dev/null || true
    echo "COPIED -> $PUB/ds-probe-api102.apk"
    break
  fi
done
