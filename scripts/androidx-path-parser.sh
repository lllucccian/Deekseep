#!/usr/bin/env bash

# Resolve the official Google AndroidX Core implementation used to parse Material icon paths,
# then stage only PathParser and its two implementation classes for the hand-built Xposed APKs.
# The Compose build consumes the normal Maven dependency directly.
prepare_androidx_path_parser() {
    local output_root="$1"
    local version="1.18.0"
    local cache_root="${GRADLE_USER_HOME:-$HOME/.gradle}"
    local aar="${ANDROIDX_CORE_AAR:-}"
    local classes_jar="$output_root/androidx-core-classes.jar"
    local stage="$output_root/androidx-path-parser-classes"
    local result="$output_root/androidx-path-parser.jar"

    if [[ -z "$output_root" || "$output_root" == "/" ]]; then
        echo "Invalid AndroidX staging directory" >&2
        return 1
    fi
    mkdir -p "$output_root" "$stage"
    output_root="$(cd "$output_root" && pwd)"
    classes_jar="$output_root/androidx-core-classes.jar"
    stage="$output_root/androidx-path-parser-classes"
    result="$output_root/androidx-path-parser.jar"
    if [[ -z "$aar" ]]; then
        aar="$(find "$cache_root/caches/modules-2/files-2.1/androidx.core/core/$version" \
                -type f -name "core-$version.aar" 2>/dev/null | head -n1)"
    fi
    if [[ -z "$aar" || ! -f "$aar" ]]; then
        aar="$output_root/core-$version.aar"
        curl -fsSL --connect-timeout 15 --max-time 120 \
            "https://dl.google.com/dl/android/maven2/androidx/core/core/$version/core-$version.aar" \
            -o "$aar"
    fi
    unzip -p "$aar" classes.jar > "$classes_jar"
    (
        cd "$stage"
        jar xf "$classes_jar" \
            'androidx/core/graphics/PathParser.class' \
            'androidx/core/graphics/PathParser$ExtractFloatResult.class' \
            'androidx/core/graphics/PathParser$PathDataNode.class'
    )
    jar cf "$result" -C "$stage" androidx/core/graphics
    printf '%s\n' "$result"
}
