#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REPORT="$ROOT/build/remote-feature-compat.tsv"
mkdir -p "$ROOT/build"

APKS=(
    "$ROOT/ds.apk"
    "$ROOT/ds-222.apk"
    "$ROOT/build/DeepSeek_2.3.0-installed.apk"
    "$ROOT/.cache/deepseek-cn-234-code245.apk"
    "$ROOT/.cache/deepseek-gp-234-base.apk"
)
NATIVE_KEYS=(
    conversation_search_enabled
    show_new_chat_button_above_input
    voice_input_enabled
    hide_assistant_avatar
    copy_text_without_markdown_syntax
    select_text_without_markdown_syntax
    optimize_markdown
    sse_auto_scroll_one_screen
    allow_file_with_search
    disable_single_dollar_latex
)

# Files that actually consume each key, rather than merely listing it in DeepSeek's hidden
# developer-settings catalogue. This guards against shipping another switch that writes nowhere.
SOURCE_SPECS=(
    "2.2.2|$ROOT/jadx-222/sources|conversation_search_enabled:defpackage/h21.java,voice_input_enabled:defpackage/xz3.java,copy_text_without_markdown_syntax:defpackage/jl1.java,optimize_markdown:defpackage/jl1.java,allow_file_with_search:defpackage/jl1.java,disable_single_dollar_latex:defpackage/jl1.java"
    "2.3.0|$ROOT/jadx-230/src/sources|conversation_search_enabled:defpackage/e31.java,voice_input_enabled:defpackage/m24.java,copy_text_without_markdown_syntax:defpackage/on1.java,select_text_without_markdown_syntax:defpackage/a71.java,optimize_markdown:defpackage/on1.java,allow_file_with_search:defpackage/on1.java,disable_single_dollar_latex:defpackage/on1.java"
    "2.3.4-CN|$ROOT/.cache/deepseek234cn/full/sources|conversation_search_enabled:defpackage/h41.java,voice_input_enabled:defpackage/t54.java,copy_text_without_markdown_syntax:defpackage/gp1.java,select_text_without_markdown_syntax:defpackage/g81.java,optimize_markdown:defpackage/gp1.java,allow_file_with_search:defpackage/gp1.java,disable_single_dollar_latex:defpackage/gp1.java"
    "2.3.4-GP|$ROOT/.cache/deepseek234gp/full/sources|conversation_search_enabled:defpackage/a61.java,voice_input_enabled:defpackage/x74.java,copy_text_without_markdown_syntax:defpackage/cr1.java,select_text_without_markdown_syntax:defpackage/z91.java,optimize_markdown:defpackage/cr1.java,allow_file_with_search:defpackage/cr1.java,disable_single_dollar_latex:defpackage/cr1.java"
)

printf 'apk\tversion\tcode\tnative_boolean_keys\n' > "$REPORT"
validated=0
for apk in "${APKS[@]}"; do
    [[ -f "$apk" ]] || continue
    keys_file="$(mktemp "$ROOT/build/remote-feature-keys.XXXXXX")"
    trap 'rm -f "$keys_file"' EXIT
    for dex in classes.dex classes2.dex classes3.dex; do
        unzip -p "$apk" "$dex" 2>/dev/null | strings -n 12 \
            | rg -o 'kv_(remote_)?settings_[a-zA-Z0-9_]+' || true
    done | sort -u > "$keys_file"
    badging="$(aapt dump badging "$apk" 2>/dev/null | sed -n '1p')"
    version="$(printf '%s\n' "$badging" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
    code="$(printf '%s\n' "$badging" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p")"
    for suffix in "${NATIVE_KEYS[@]}"; do
        rg -qx "kv_remote_settings_${suffix}" "$keys_file" || {
            echo "FAIL: $(basename "$apk") lacks remote value key for ${suffix}" >&2
            exit 1
        }
        rg -qx "kv_settings_${suffix}" "$keys_file" || {
            echo "FAIL: $(basename "$apk") lacks native local override for ${suffix}" >&2
            exit 1
        }
    done
    printf '%s\t%s\t%s\t%s\n' "$(basename "$apk")" "$version" "$code" \
        "${#NATIVE_KEYS[@]}" | tee -a "$REPORT"
    rm -f "$keys_file"
    trap - EXIT
    validated=$((validated + 1))
done

[[ "$validated" -ge 5 ]] || {
    echo "FAIL: expected five DeepSeek channel/version APKs, validated $validated" >&2
    exit 1
}

for spec in "${SOURCE_SPECS[@]}"; do
    IFS='|' read -r label source_dir readers <<< "$spec"
    [[ -d "$source_dir" ]] || { echo "FAIL: missing decompile tree $source_dir" >&2; exit 1; }
    IFS=',' read -ra reader_list <<< "$readers"
    for mapping in "${reader_list[@]}"; do
        suffix="${mapping%%:*}"
        relative="${mapping#*:}"
        source="$source_dir/$relative"
        [[ -f "$source" ]] || { echo "FAIL: $label lacks business reader $relative" >&2; exit 1; }
        rg -q "kv_settings_${suffix}" "$source" || {
            echo "FAIL: $label reader $relative lacks local override for $suffix" >&2
            exit 1
        }
        rg -q "kv_remote_settings_${suffix}" "$source" || {
            echo "FAIL: $label reader $relative lacks remote fallback for $suffix" >&2
            exit 1
        }
    done
done

echo "Native feature compatibility passed for $validated APKs and 4 direct-reader maps"
