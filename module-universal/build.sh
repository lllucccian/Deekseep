#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

source ../scripts/android-tools.sh
JSCH_JAR="../third_party/jsch/jsch-2.28.2.jar"

OUT=build
rm -rf $OUT
mkdir -p $OUT/classes $OUT/dex $OUT/generated-src/com/dsmod/probe

echo "[0/7] generate API 82+ universal adapter and BuildInfo.java"
MODULE_VER=$(grep -oE 'android:versionName="[^"]+"' AndroidManifest.xml | head -n1 | cut -d'"' -f2)
cat > $OUT/generated-src/com/dsmod/probe/BuildInfo.java <<EOF
package com.dsmod.probe;
public final class BuildInfo {
    public static final String API_VERSION = "82+ (universal)";
    public static final String MODULE_VERSION = "${MODULE_VER:-unknown}";
    public static final String BUILD_DATE = "$(date '+%Y-%m-%d %H:%M')";
    public static final boolean GOOGLE_PLAY = true;
    private BuildInfo() {}
}
EOF

sed \
  -e 's#import io.github.libxposed.api.XposedModule;#import de.robv.android.xposed.IXposedHookLoadPackage;#' \
  -e 's#import io.github.libxposed.api.XposedInterface.Chain;#import com.dsmod.probe.LegacyXposedModule.Chain;#' \
  -e 's#import io.github.libxposed.api.XposedInterface.Hooker;#import com.dsmod.probe.LegacyXposedModule.Hooker;#' \
  -e 's#import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;#import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;#' \
  -e 's#public class Main extends XposedModule#public class Main extends LegacyXposedModule implements IXposedHookLoadPackage#' \
  -e 's#public void onPackageLoaded(PackageLoadedParam param)#public void handleLoadPackage(LoadPackageParam param)#' \
  -e 's#param.getDefaultClassLoader()#param.classLoader#g' \
  -e 's#param.getPackageName()#param.packageName#g' \
  -e 's#module loaded (modern)#module loaded (universal API 82+)#g' \
  ../module/src/com/dsmod/probe/Main.java \
  > $OUT/generated-src/com/dsmod/probe/Main.java

echo "[1/7] collect canonical sources plus the universal Xposed adapter"
find ../module/src/com/dsmod/probe -maxdepth 1 -name "*.java" \
  ! -name Main.java ! -name BuildInfo.java > $OUT/sources.txt
find ../module/src/com/dsmod/relay -name "*.java" >> $OUT/sources.txt
find ../module-legacy/compat -name "*.java" >> $OUT/sources.txt
find ../module-legacy/src/de -name "*.java" >> $OUT/sources.txt
find $OUT/generated-src -name "*.java" >> $OUT/sources.txt

echo "[2/7] javac (API 82+ universal core + bundled JSch)"
if ! javac -source 8 -target 8 -cp "$ANDROID_JAR:$JSCH_JAR" \
        -d $OUT/classes @$OUT/sources.txt 2> $OUT/javac.err; then
  cat $OUT/javac.err
  exit 1
fi
grep -v "warning:" $OUT/javac.err || true

echo "[3/7] d8 (module classes + JSch; Xposed API provided by framework)"
MODCLASSES=$(find $OUT/classes/com/dsmod -name "*.class")
$D8 --min-api 24 --output $OUT/dex $MODCLASSES "$JSCH_JAR" --lib "$ANDROID_JAR"

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
CLOUDFLARED_NATIVE=../third_party/cloudflared/android
# The tested Google Play target is arm64-v8a. "Universal" describes Xposed
# API compatibility, so unrelated CPU payloads must not inflate the APK.
for ABI in arm64-v8a; do
  SOURCE="$CLOUDFLARED_NATIVE/$ABI/libcloudflared.so"
  if [ ! -f "$SOURCE" ]; then
    echo "Missing bundled cloudflared for $ABI: $SOURCE" >&2
    exit 1
  fi
  mkdir -p "$OUT/xstage/lib/$ABI"
  cp "$SOURCE" "$OUT/xstage/lib/$ABI/libcloudflared.so"
done
( cd $OUT/xstage && zip -q -9 -r ../unsigned.apk assets lib )

echo "[6/7] zipalign"
$ZIPALIGN -f -p 4 $OUT/unsigned.apk $OUT/aligned.apk

echo "[7/7] sign with the primary APK key"
if [ ! -f ../module/debug.keystore ]; then
  echo "Primary signing key is missing: ../module/debug.keystore" >&2
  exit 1
fi
$APKSIGNER sign --ks ../module/debug.keystore --ks-pass pass:android \
    --key-pass pass:android --out ds-probe-universal.apk $OUT/aligned.apk

echo "DONE -> $(pwd)/ds-probe-universal.apk"
for PUB in /storage/emulated/0 /sdcard; do
  if [ -d "$PUB" ] \
      && cp -f ds-probe-universal.apk "$PUB/ds-probe-universal.apk" 2>/dev/null; then
    echo "COPIED -> $PUB/ds-probe-universal.apk"
    break
  fi
done
