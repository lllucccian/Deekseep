#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DIST="$ROOT/dist"
source "$ROOT/scripts/android-tools.sh"

rm -rf "$DIST"
mkdir -p "$DIST"

for variant in module module-universal; do
    echo
    echo "=== Building $variant ==="
    (cd "$ROOT/$variant" && bash build.sh)
done

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
    test -f "$ROOT/module/build/classes/com/dsmod/probe/${class_name}.class"
    test -f "$ROOT/module-universal/build/classes/com/dsmod/probe/${class_name}.class"
done
if grep -q '^import io\.github\.libxposed' \
        "$ROOT/module-universal/build/generated-src/com/dsmod/probe/Main.java"; then
    echo "Generated universal entry still imports modern libxposed APIs" >&2
    exit 1
fi
"$APKSIGNER" verify "$ROOT/module/ds-probe-api102.apk"
"$APKSIGNER" verify "$ROOT/module-universal/ds-probe-universal.apk"

CERT_API102=$("$APKSIGNER" verify --print-certs \
    "$ROOT/module/ds-probe-api102.apk" \
    | awk -F': ' '/certificate SHA-256 digest/ {print $2; exit}')
CERT_UNIVERSAL=$("$APKSIGNER" verify --print-certs \
    "$ROOT/module-universal/ds-probe-universal.apk" \
    | awk -F': ' '/certificate SHA-256 digest/ {print $2; exit}')
if [ -z "$CERT_API102" ] || [ "$CERT_API102" != "$CERT_UNIVERSAL" ]; then
    echo "API 102-only and universal APKs are not signed by the same certificate" >&2
    exit 1
fi

cp "$ROOT/module/ds-probe-api102.apk" \
    "$DIST/deekseep-api102-only-v1.7.3.apk"
cp "$ROOT/module-universal/ds-probe-universal.apk" \
    "$DIST/deekseep-universal-api82-100-101-102-v1.7.3.apk"

if find "$DIST" -maxdepth 1 -type f \( -name '*test*' -o -name '*probe*' \) | grep -q .; then
    echo "Refusing to publish retired test/diagnostic APKs in the 1.7.3 release" >&2
    exit 1
fi

for manifest in "$ROOT/module/AndroidManifest.xml" \
        "$ROOT/module-universal/AndroidManifest.xml"; do
    if ! grep -q 'android:versionName="1.7.3"' "$manifest"; then
        echo "Release manifest is not version 1.7.3: $manifest" >&2
        exit 1
    fi
done

API102_APK="$DIST/deekseep-api102-only-v1.7.3.apk"
UNIVERSAL_APK="$DIST/deekseep-universal-api82-100-101-102-v1.7.3.apk"
unzip -l "$API102_APK" | grep -q 'META-INF/xposed/java_init.list'
unzip -l "$API102_APK" | grep -q 'META-INF/xposed/module.prop'
if unzip -l "$API102_APK" | grep -q 'assets/xposed_init'; then
    echo "API 102-only APK unexpectedly contains a traditional Xposed entry" >&2
    exit 1
fi
unzip -p "$API102_APK" META-INF/xposed/module.prop \
    | grep -q '^minApiVersion=102$'
unzip -p "$API102_APK" META-INF/xposed/module.prop \
    | grep -q '^targetApiVersion=102$'

unzip -l "$UNIVERSAL_APK" | grep -q 'assets/xposed_init'
if unzip -l "$UNIVERSAL_APK" | grep -q 'META-INF/xposed/java_init.list'; then
    echo "Universal APK unexpectedly contains a modern libxposed entry" >&2
    exit 1
fi
grep -q 'android:name="xposedminversion" android:value="82"' \
    "$ROOT/module-universal/AndroidManifest.xml"

(cd "$DIST" && sha256sum *.apk > SHA256SUMS.txt)
echo
echo "API 102-only and API 82/100/101/102 universal APKs are in $DIST"
