#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
source "$ROOT/scripts/android-tools.sh"

rm -rf "$DIST"
mkdir -p "$DIST"

echo
echo "=== Building universal module ==="
(cd "$ROOT/module-universal" && bash build.sh)
echo
echo "=== Building Google Play universal module ==="
(cd "$ROOT/module-universal" && GOOGLE_PLAY_BUILD=true bash build.sh)

echo
echo "=== Verifying complete feature parity and Xposed API 82-102 entry ==="
bash "$ROOT/scripts/test-universal-feature-parity.sh"

echo
echo "=== Running chat-editor regression test ==="
(cd "$ROOT/module" && bash test-thinking-regression.sh)

echo
echo "=== Running expert relay multi-turn regression test ==="
(cd "$ROOT/module" && bash test-expert-relay-regression.sh)

echo
echo "=== Running traditional-Xposed adapter regression test ==="
(cd "$ROOT/module-legacy" && bash test-adapter-regression.sh)

echo
echo "=== Verifying shared-core parity, entry formats, and APK signatures ==="
for class_name in AccountManager ChatAppearance ChatAppearanceUi DeekseepUi \
        LocalApiGateway OpenAiToolBridge ResponsePreserver; do
    test -f "$ROOT/module-universal/build/classes/com/dsmod/probe/${class_name}.class"
done
if grep -q '^import io\.github\.libxposed' \
        "$ROOT/module-universal/build/generated-src/com/dsmod/probe/Main.java"; then
    echo "Generated universal entry still imports modern libxposed APIs" >&2
    exit 1
fi
"$APKSIGNER" verify "$ROOT/module-universal/ds-probe-universal.apk"
cp "$ROOT/module-universal/ds-probe-universal.apk" \
    "$DIST/deekseep-universal-v1.7.4.apk"
cp "$ROOT/module-universal/ds-probe-universal-google-play.apk" \
    "$DIST/deekseep-google-play-universal-v1.7.4.apk"

if find "$DIST" -maxdepth 1 -type f \( -name '*test*' -o -name '*probe*' \) | grep -q .; then
    echo "Refusing to publish retired test/diagnostic APKs in the 1.7.4 release" >&2
    exit 1
fi

MANIFEST="$ROOT/module-universal/AndroidManifest.xml"
if ! grep -q 'android:versionName="1.7.4"' "$MANIFEST"; then
    echo "Release manifest is not version 1.7.4: $MANIFEST" >&2
    exit 1
fi

UNIVERSAL_APK="$DIST/deekseep-universal-v1.7.4.apk"
GOOGLE_PLAY_APK="$DIST/deekseep-google-play-universal-v1.7.4.apk"

for apk in "$UNIVERSAL_APK" "$GOOGLE_PLAY_APK"; do
    unzip -l "$apk" | grep -q 'assets/xposed_init'
    if unzip -l "$apk" | grep -q 'META-INF/xposed/java_init.list'; then
        echo "Universal APK unexpectedly contains a modern libxposed entry: $apk" >&2
        exit 1
    fi
done
grep -q 'android:name="xposedminversion" android:value="82"' \
    "$ROOT/module-universal/AndroidManifest.xml"

(cd "$DIST" && sha256sum *.apk > SHA256SUMS.txt)
echo
echo "Domestic and Google Play universal APKs are in $DIST"
