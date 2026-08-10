#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APKS=(
    "$ROOT/module-universal/ds-probe-universal.apk"
    "$ROOT/module-universal/ds-probe-universal-google-play.apk"
)

for apk in "${APKS[@]}"; do
    [[ -f "$apk" ]] || {
        echo "FAIL: missing universal APK: $apk" >&2
        exit 1
    }
done

mkdir -p "$ROOT/build"
source_list="$(mktemp "$ROOT/build/universal-core-sources.XXXXXX")"
trap 'rm -f "$source_list"' EXIT
find "$ROOT/module/src/com/dsmod/probe" -maxdepth 1 -name '*.java' \
    ! -name 'BuildInfo.java' -printf '%f\n' | sed 's/[.]java$//' | sort > "$source_list"

expected="$(wc -l < "$source_list" | tr -d '[:space:]')"
[[ "$expected" -gt 0 ]] || {
    echo "FAIL: canonical feature core is empty" >&2
    exit 1
}

for apk in "${APKS[@]}"; do
    dex_strings="$(mktemp "$ROOT/build/universal-dex-strings.XXXXXX")"
    unzip -p "$apk" classes.dex | strings -n 8 > "$dex_strings"
    while IFS= read -r class_name; do
        rg -Fq "Lcom/dsmod/probe/${class_name};" "$dex_strings" || {
            echo "FAIL: $(basename "$apk") is missing canonical class ${class_name}" >&2
            rm -f "$dex_strings"
            exit 1
        }
    done < "$source_list"
    rg -Fq 'Lcom/dsmod/probe/BuildInfo;' "$dex_strings" || {
        echo "FAIL: $(basename "$apk") is missing generated BuildInfo" >&2
        rm -f "$dex_strings"
        exit 1
    }
    if rg -q 'io/github/libxposed|META-INF/xposed/java_init[.]list' "$dex_strings"; then
        echo "FAIL: $(basename "$apk") contains a modern-only Xposed entry" >&2
        rm -f "$dex_strings"
        exit 1
    fi
    rm -f "$dex_strings"

    manifest="$(aapt2 dump xmltree "$apk" --file AndroidManifest.xml)"
    printf '%s\n' "$manifest" | rg -q '"xposedminversion"' || {
        echo "FAIL: $(basename "$apk") has no xposedminversion" >&2
        exit 1
    }
    printf '%s\n' "$manifest" | rg -q 'android:value.*=82$' || {
        echo "FAIL: $(basename "$apk") does not start at Xposed API 82" >&2
        exit 1
    }
    if printf '%s\n' "$manifest" | rg -q '"xposedmaxversion"'; then
        echo "FAIL: $(basename "$apk") unexpectedly caps the Xposed API" >&2
        exit 1
    fi
    unzip -p "$apk" assets/xposed_init | rg -qx 'com[.]dsmod[.]probe[.]Main' || {
        echo "FAIL: $(basename "$apk") has an invalid traditional Xposed entry" >&2
        exit 1
    }
done

echo "Universal feature parity passed: ${expected} canonical classes in both channels; Xposed API 82-102 uses one uncapped traditional entry"
