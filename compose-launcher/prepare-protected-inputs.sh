#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$PROJECT_DIR/.." && pwd)"
source "$ROOT_DIR/scripts/android-tools.sh"

OUT="$PROJECT_DIR/app/build/generated/protectedInputs"
SOURCE="$ROOT_DIR/module-universal/protected-payload-src"
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/src/com/dsmod/probe" "$OUT/resources"

KEY="$(openssl rand -hex 32)"
KEY_A="${KEY:0:32}"
KEY_B="${KEY:32:32}"
IV="$(openssl rand -hex 16)"

find "$SOURCE" -name '*.java' -print > "$OUT/sources.txt"
javac -source 8 -target 8 -cp "$ANDROID_JAR" \
    -d "$OUT/classes" @"$OUT/sources.txt"
PAYLOAD_CLASSES="$(find "$OUT/classes" -name '*.class' -print)"
"$D8" --min-api 24 --output "$OUT/dex" $PAYLOAD_CLASSES --lib "$ANDROID_JAR"
openssl enc -aes-256-ctr -K "$KEY" -iv "$IV" \
    -in "$OUT/dex/classes.dex" -out "$OUT/resources/runtime_payload.dat"

cat > "$OUT/src/com/dsmod/probe/BuildInfo.java" <<EOF
package com.dsmod.probe;
public final class BuildInfo {
    public static final String API_VERSION = "universal (Xposed API 82-102 verified)";
    public static final String MODULE_VERSION = "1.7.4";
    public static final String BUILD_DATE = "$(date '+%Y-%m-%d %H:%M')";
    public static final boolean GOOGLE_PLAY = true;
    public static final boolean PROTECTED_BUILD = true;
    public static final String PROTECTED_PAYLOAD_KEY_A = "$KEY_A";
    public static final String PROTECTED_PAYLOAD_KEY_B = "$KEY_B";
    public static final String PROTECTED_PAYLOAD_IV = "$IV";
    private BuildInfo() {}
}
EOF
