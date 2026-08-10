#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APK="$ROOT/module-universal/ds-probe-universal.apk"
PROMPT_ENTRY="META-INF/com.github.mwiede.jsch/internal/transport/authentication/runtime_policy_extension_20260727_v2.dat"
REPORT_DIR="$ROOT/build/test-all-features"
INSTALL_DEVICE=true
THEME_SMOKE=true
REQUIRE_MAINLAND=true
ORIGINAL_NIGHT_MODE=""
SMOKE_PACKAGE="com.dsmod.probe.backupsmoke"
SMOKE_PACKAGE_INSTALLED=false

usage() {
    echo "Usage: $0 [--no-install] [--no-theme-smoke] [--allow-google-play]"
}

while (($#)); do
    case "$1" in
        --no-install) INSTALL_DEVICE=false ;;
        --no-theme-smoke) THEME_SMOKE=false ;;
        --allow-google-play) REQUIRE_MAINLAND=false ;;
        -h|--help) usage; exit 0 ;;
        *) echo "Unknown option: $1" >&2; usage >&2; exit 2 ;;
    esac
    shift
done

step() {
    echo
    echo "[$1] $2"
}

fail() {
    echo "FAIL: $*" >&2
    exit 1
}

require_text() {
    local file="$1"
    local pattern="$2"
    local label="$3"
    rg -q "$pattern" "$file" || fail "$label"
}

restore_night_mode() {
    if [[ -z "$ORIGINAL_NIGHT_MODE" ]]; then
        return
    fi
    case "$ORIGINAL_NIGHT_MODE" in
        yes|no|auto|custom_yes|custom_no)
            su -c "cmd uimode night $ORIGINAL_NIGHT_MODE" >/dev/null 2>&1 || true
            ;;
    esac
}

cleanup_smoke_package() {
    if [[ "$SMOKE_PACKAGE_INSTALLED" == true ]]; then
        su -c "pm uninstall '$SMOKE_PACKAGE'" >/dev/null 2>&1 || true
        SMOKE_PACKAGE_INSTALLED=false
    fi
}

cleanup_runtime_state() {
    restore_night_mode
    cleanup_smoke_package
}
trap cleanup_runtime_state EXIT

mkdir -p "$REPORT_DIR"

if [[ -z "${ANDROID_SDK_ROOT:-}" && -z "${ANDROID_HOME:-}" ]]; then
    TASK_SDK_CANDIDATE="/data/data/com.termux/files/home/avf-nonprotected-poc/.termux-sdk"
    if [[ -d "$TASK_SDK_CANDIDATE" ]]; then
        export ANDROID_SDK_ROOT="$TASK_SDK_CANDIDATE"
    fi
fi
source "$ROOT/scripts/android-tools.sh"

step 1 "Static feature contracts and all supported host mappings"
bash "$ROOT/scripts/test-remote-feature-compat.sh"
require_text "$ROOT/module/src/com/dsmod/probe/HostCompat.java" \
    '2\.3\.4/code245-cn' "missing mainland 2.3.4 mapping"
require_text "$ROOT/module/src/com/dsmod/probe/HostCompat.java" \
    '2\.3\.4/code246-gp' "missing Google Play 2.3.4 mapping"
require_text "$ROOT/module/src/com/dsmod/probe/HostCompat.java" \
    '2\.3\.0/code237' "missing 2.3.0 mapping"
require_text "$ROOT/module/src/com/dsmod/probe/HostCompat.java" \
    '2\.2\.x' "missing 2.2.x mapping"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'NATIVE_SETTINGS_ENTRY_ENABLED_FILE\)\.isFile' \
    "native settings entry is not opt-in"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'headerGap=4dp' "native plugin header gap is not 4dp"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'final long frameDelay = saver \? 50L : 16L' \
    "whale battery-saver frame policy is missing"
require_text "$ROOT/module/src/com/dsmod/probe/TextWaveEngine.java" \
    'Main\.isSystemPowerSaver\(activity\) \? 66L : 33L' \
    "text-wave battery-saver frame policy is missing"
require_text "$ROOT/module/src/com/dsmod/probe/DeekseepUi.java" \
    'setMinimumHeight\(dp\(act, 48\)\)' "compact 48dp hub rows are missing"
require_text "$ROOT/module/src/com/dsmod/probe/DeekseepUi.java" \
    'dp\(act, 68\), dp\(act, 36\)' "horizontal KSU-style execute face is missing"
require_text "$ROOT/module/src/com/dsmod/probe/DeekseepUi.java" \
    'label\.setSingleLine\(true\)' "execute label is allowed to wrap vertically"
require_text "$ROOT/module/src/com/dsmod/probe/DeekseepUi.java" \
    'COMPLEX_UNIT_SP, 12' "KernelSU-size execute label is missing"
require_text "$ROOT/module/src/com/dsmod/probe/DeekseepUi.java" \
    '已开启其他功能，请先关闭后再使用' "easter-egg prompt state can leave the chat category"
require_text "$ROOT/module/src/com/dsmod/probe/HostCompat.java" \
    'trainingControlMethod' "native data-optimization lock mappings are missing"
require_text "$ROOT/module/src/com/dsmod/probe/HostCompat.java" \
    'updateDialogMethod' "native client-update mappings are missing"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'DATA_OPT_OUT_ENFORCED_FILE' "data-optimization auto-disable is missing"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'HOT_UPDATE_DISABLED_FILE' "hot-update opt-in marker is missing"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'FAKE_MUTE_ENABLED_FILE' "local-mute deadline is not separated from enable state"
require_text "$ROOT/module/src/com/dsmod/probe/DeekseepUi.java" \
    'mFlingScroller' "local-mute wheels have no fast-fling tuning"
require_text "$ROOT/module/src/com/dsmod/probe/DeekseepUi.java" \
    '搜索功能' "cross-category feature search is missing"
require_text "$ROOT/module/src/com/dsmod/probe/DeekseepTools.java" \
    'deekseep-chat-backup' "portable chat backup format is missing"
require_text "$ROOT/module/src/com/dsmod/probe/ChatEditorUi.java" \
    'overwriteMatchingSession' "matching chat overwrite import is missing"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'CHAT_IMPORT_REQUEST' "chat backup document picker result is missing"
require_text "$ROOT/module/src/com/dsmod/probe/HubMaterialGlyphView.java" \
    'androidx\.core\.graphics\.PathParser' "official AndroidX path parser is missing"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'HostCompat\.messageMethod\("A"\)' "2.3 role mapping for hidden Agent results is missing"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'HostCompat\.messageMethod\("l"\)' "2.3 fragment mapping for hidden Agent results is missing"
require_text "$ROOT/module/src/com/dsmod/probe/HostCompat.java" \
    'case "tc": return "sc"' "mainland 2.3.4 sidebar row mapping is missing"
require_text "$ROOT/module/src/com/dsmod/probe/HostCompat.java" \
    'case "tc": return "ika"' "Google Play 2.3.4 sidebar row mapping is missing"
require_text "$ROOT/module/src/com/dsmod/probe/ShakeParallaxUi.java" \
    'Power-saving limit|省电限制' "dedicated gyroscope compatibility page is missing"
require_text "$ROOT/module/src/com/dsmod/probe/RemoteFeatureFlags.java" \
    'conversation_search_enabled' \
    "DeepSeek-native remote feature manager is missing"
require_text "$ROOT/module/src/com/dsmod/probe/RemoteFeatureFlags.java" \
    '"kv_settings_".*remoteKey\.substring' \
    "DeepSeek native local feature-setting override is missing"
require_text "$ROOT/module/src/com/dsmod/probe/ProcessManagerActivity.java" \
    'cgroup\.freeze' "process manager cgroup freezer action is missing"
require_text "$ROOT/module/src/com/dsmod/probe/ProcessManagerActivity.java" \
    "tr -d '\[:space:\]'" "process manager ps-output normalization is missing"
require_text "$ROOT/module/src/com/dsmod/probe/ProcessManagerActivity.java" \
    'cgroup\.kill' "process manager precise cgroup kill action is missing"
require_text "$ROOT/module/src/com/dsmod/probe/ProcessManagerActivity.java" \
    'expected_cg=/apps/uid_' "process manager PID/cgroup validation is missing"
require_text "$ROOT/module-universal/AndroidManifest.xml" \
    'com\.dsmod\.probe\.ProcessManagerActivity' "process manager activity is missing"
require_text "$ROOT/module/src/com/dsmod/probe/DeepSeekCacheCleaner.java" \
    '"coil3_disk_cache", "image_cache", "images", "mermaid_cache"' \
    "verified DeepSeek cache allowlist is missing"
require_text "$ROOT/module/src/com/dsmod/probe/ChatEditorUi.java" \
    'fragments,inserted_at' "message timestamp/details query is missing"
require_text "$ROOT/module/src/com/dsmod/probe/ChatEditorUi.java" \
    'm\.insertedAt = row\.insertedAt' \
    "online snapshot drops message timestamps before editor rendering"
require_text "$ROOT/module/src/com/dsmod/probe/ChatAppearance.java" \
    'assistant_avatar_file' "custom assistant avatar persistence is missing"
require_text "$ROOT/module/src/com/dsmod/probe/ChatAppearance.java" \
    'loadAssistantAvatarBitmap' \
    "custom assistant avatar is not normalized at render time"
require_text "$ROOT/module/src/com/dsmod/probe/ChatAppearance.java" \
    'Bitmap\.Config\.ARGB_8888' \
    "custom assistant avatar has no transparent circular output"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'chain\.getThisObject\(\) == assistantAvatarPainter' \
    "custom assistant avatar is still vulnerable to the host Icon tint"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'ReplyReadyPolicy\.shouldNotify' \
    "background reply completion is not connected to the host status hook"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'AutoContinuePolicy\.shouldResume' \
    "automatic continue is not connected to the host status hook"
require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
    'dispatchNativeResume' \
    "automatic continue does not use DeepSeek's native resume event"
require_text "$ROOT/module/src/com/dsmod/probe/DeekseepUi.java" \
    '长思考被服务器暂停时自动继续，切到后台也会生效' \
    "automatic continue switch or background explanation is missing"
for RESUME_EVENT in ba1 ab1 oc1 ce1; do
    require_text "$ROOT/module/src/com/dsmod/probe/HostCompat.java" \
        "$RESUME_EVENT" "missing native resume event mapping: $RESUME_EVENT"
done
require_text "$ROOT/module/src/com/dsmod/probe/ProactiveHeartbeatReceiver.java" \
    'DeepSeek 回复已就绪' \
    "reply-ready notification copy is missing"
require_text "$ROOT/module/src/com/dsmod/probe/NotificationIcons.java" \
    'chat_welcome_logo' \
    "notification icon does not prefer DeepSeek's official whale resource"
for NOTIFICATION_SOURCE in LocalApiKeepAliveService.java LocalAudioPlaybackService.java \
        QqMusicPlaybackService.java ProactiveHeartbeatReceiver.java; do
    require_text "$ROOT/module/src/com/dsmod/probe/$NOTIFICATION_SOURCE" \
        'NotificationIcons\.smallIcon' \
        "$NOTIFICATION_SOURCE still uses a generic system notification icon"
done
[[ -f "$ROOT/module-universal/res/drawable/ds_notification_whale.xml" ]] \
    || fail "standalone notification whale resource is missing"
require_text "$ROOT/module/src/com/dsmod/probe/DeekseepUi.java" \
    '需要在灰度功能管理里面开启“显示助手头像”' \
    "custom avatar prerequisite is not explained in the UI"
for AVATAR_MAPPING in \
        'loaderOwner = "z45"' \
        'loaderOwner = "t75"' \
        'loaderOwner = "ye5"' \
        'loaderOwner = "ms9"'; do
    require_text "$ROOT/module/src/com/dsmod/probe/Main.java" \
        "$AVATAR_MAPPING" "missing assistant-avatar host mapping: $AVATAR_MAPPING"
done

# All display-modification screens must resolve colors from the current configuration instead of
# assuming one theme. This is deliberately a contract check in addition to the device screenshots.
for THEMED_SOURCE in DeekseepUi.java ChatAppearanceUi.java TaskExecutionUi.java \
        SpatialMotionUi.java ShakeParallaxUi.java ImageCutoutUi.java; do
    require_text "$ROOT/module/src/com/dsmod/probe/$THEMED_SOURCE" \
        'isDark|UI_MODE_NIGHT' "$THEMED_SOURCE has no light/dark resolver"
done

step 2 "Build domestic and Google Play universal adapters"
(cd "$ROOT/module-universal" && bash build.sh)
(cd "$ROOT/module-universal" && GOOGLE_PLAY_BUILD=true bash build.sh)
bash "$ROOT/scripts/test-universal-feature-parity.sh"

step 3 "Run the full JVM and compatibility regression suite"
(cd "$ROOT/module" && bash test-thinking-regression.sh)
(cd "$ROOT/module" && bash test-expert-relay-regression.sh)
(cd "$ROOT/module-legacy" && bash test-adapter-regression.sh)

step 4 "Verify the original standalone module UI package"
[[ -f "$APK" ]] || fail "release APK was not produced"
"$APKSIGNER" verify "$APK"
unzip -l "$APK" | rg -q "${PROMPT_ENTRY//./\.}" \
    || fail "mainland one-tap prompt is absent from the APK"
SOURCE_PROMPT="$ROOT/third_party/jsch/bundled-meta/.com_github_mwiede_jsch_transport_authentication_negotiation_runtime_policy_extension_20260727_v2.dat"
SOURCE_SHA="$(sha256sum "$SOURCE_PROMPT" | awk '{print $1}')"
APK_SHA="$(unzip -p "$APK" "$PROMPT_ENTRY" | sha256sum | awk '{print $1}')"
[[ "$SOURCE_SHA" == "$APK_SHA" ]] || fail "bundled prompt checksum changed during packaging"
sha256sum "$APK" | tee "$REPORT_DIR/app-release.sha256"

if [[ "$INSTALL_DEVICE" != true ]]; then
    step 5 "Device checks skipped by --no-install"
    echo "ALL AUTOMATED TESTS PASSED"
    exit 0
fi

command -v su >/dev/null 2>&1 || fail "root shell is required for device installation"
HOST_DUMP="$(su -c 'dumpsys package com.deepseek.chat' 2>/dev/null)"
[[ -n "$HOST_DUMP" ]] || fail "DeepSeek is not installed"
HOST_VERSION="$(printf '%s\n' "$HOST_DUMP" | rg -m1 'versionName=' | sed 's/.*versionName=//')"
HOST_CODE="$(printf '%s\n' "$HOST_DUMP" | rg -m1 'versionCode=' | sed -E 's/.*versionCode=([0-9]+).*/\1/')"
if [[ "$REQUIRE_MAINLAND" == true && "$HOST_CODE" != "245" ]]; then
    fail "current device is not the requested mainland 2.3.4 host (code=$HOST_CODE)"
fi
echo "Host: DeepSeek $HOST_VERSION (code $HOST_CODE)"

step 5 "Install final APK and verify the current mainland runtime"
[[ "$APK" == "$ROOT"/* ]] || fail "unexpected APK path"
su -c "pm install -r '$APK'" | rg -q 'Success' || fail "APK installation failed"

# Exercise the actual Android SQLite implementation inside a disposable test package.
# This never requests access to DeepSeek's package or database.
SMOKE_BUILD="$(mktemp -d "$REPORT_DIR/chat-backup-device-smoke.XXXXXX")"
[[ "$SMOKE_BUILD" == "$REPORT_DIR/chat-backup-device-smoke."* ]] \
    || fail "unexpected chat backup smoke build path"
mkdir -p "$SMOKE_BUILD/classes" "$SMOKE_BUILD/dex"
javac -source 8 -target 8 -cp "$ANDROID_JAR" -d "$SMOKE_BUILD/classes" \
    "$ROOT/module/src/com/dsmod/probe/ChatBackupStore.java" \
    "$ROOT/module/tests/com/dsmod/probe/ChatBackupDeviceSmoke.java" \
    "$ROOT/module/tests/com/dsmod/probe/ChatBackupInstrumentation.java"
"$D8" --min-api 24 --lib "$ANDROID_JAR" --output "$SMOKE_BUILD/dex" \
    "$SMOKE_BUILD/classes/com/dsmod/probe/ChatBackupStore.class" \
    "$SMOKE_BUILD/classes/com/dsmod/probe/ChatBackupDeviceSmoke.class" \
    "$SMOKE_BUILD/classes/com/dsmod/probe/ChatBackupInstrumentation.class"
"$AAPT2" link -o "$SMOKE_BUILD/base.apk" -I "$ANDROID_JAR" \
    --manifest "$ROOT/module/tests/chat-backup-device-manifest.xml"
cp "$SMOKE_BUILD/base.apk" "$SMOKE_BUILD/unsigned.apk"
(cd "$SMOKE_BUILD/dex" && zip -q "$SMOKE_BUILD/unsigned.apk" classes.dex)
"$ZIPALIGN" -f -p 4 "$SMOKE_BUILD/unsigned.apk" "$SMOKE_BUILD/aligned.apk"
"$APKSIGNER" sign --ks "$ROOT/module/debug.keystore" --ks-pass pass:android \
    --key-pass pass:android --out "$SMOKE_BUILD/chat-backup-smoke.apk" \
    "$SMOKE_BUILD/aligned.apk"
if su -c "pm path '$SMOKE_PACKAGE'" 2>/dev/null | rg -q '^package:'; then
    fail "reserved chat backup smoke package is already installed"
fi
su -c "pm install '$SMOKE_BUILD/chat-backup-smoke.apk'" | rg -q 'Success' \
    || fail "chat backup smoke APK installation failed"
SMOKE_PACKAGE_INSTALLED=true
SMOKE_OUTPUT="$(su -c "am instrument -w '$SMOKE_PACKAGE/com.dsmod.probe.ChatBackupInstrumentation'")"
printf '%s\n' "$SMOKE_OUTPUT" | tee "$REPORT_DIR/chat-backup-device-smoke.log"
printf '%s\n' "$SMOKE_OUTPUT" | rg -q 'Chat backup device smoke passed' \
    || fail "chat backup Android SQLite smoke failed"
cleanup_smoke_package

su -c 'am force-stop com.deepseek.chat'
su -c 'am start -W -n com.deepseek.chat/.MainActivity' >/dev/null
sleep 8
RUNTIME_LOG="$(su -c 'tail -n 240 /data/data/com.deepseek.chat/files/dsprobe.log' 2>/dev/null)"
printf '%s\n' "$RUNTIME_LOG" > "$REPORT_DIR/mainland-runtime.log"
printf '%s\n' "$RUNTIME_LOG" | rg -q 'hostGeneration=2\.3\.4/code245-cn' \
    || fail "mainland 2.3.4 runtime generation was not detected"
for NEEDLE in 'text wave hooked' 'home greeting hooked' \
        'installed DeepSeek native feature-setting manager' \
        'installed custom assistant avatar painter ye5.C -> rz2' \
        'native settings section hooked' 'chat bubble customization \(mainland\)' \
        'installed sidebar multi-select delete hook mc\.e x1' \
        'installed sidebar toggle cleanup hook mq5\.i x1' \
        'installed native session navigator hook mc\.f x1' \
        'installed native proactive visible-thread filter lq\.v x1' \
        'welcome whale continuous frames confirmed'; do
    printf '%s\n' "$RUNTIME_LOG" | rg -q "$NEEDLE" \
        || fail "runtime hook missing: $NEEDLE"
done
if su -c 'test -e /data/data/com.deepseek.chat/files/deekseep_native_settings_entry_enabled'; then
    echo "Native settings entry: explicitly enabled by user marker"
else
    echo "Native settings entry: OFF by default; floating fallback active"
fi

if [[ "$THEME_SMOKE" == true ]]; then
    step 6 "Capture real light and dark UI smoke tests, then restore system mode"
    ORIGINAL_NIGHT_MODE="$(su -c 'cmd uimode night' | sed -E 's/^Night mode: //; s/ .*//')"
    for MODE in no yes; do
        if [[ "$MODE" == "yes" ]]; then LABEL="dark"; else LABEL="light"; fi
        su -c "cmd uimode night $MODE" >/dev/null
        su -c 'am force-stop com.dsmod.probe'
        FOCUSED=false
        for _ in 1 2 3 4 5; do
            su -c 'am start -W -n com.dsmod.probe/.SettingsActivity' >/dev/null
            sleep 1
            RESUMED_ACTIVITY="$(su -c 'dumpsys activity activities' 2>/dev/null \
                    | rg -m1 'topResumedActivity=' || true)"
            if [[ "$RESUMED_ACTIVITY" == *'com.dsmod.probe/.SettingsActivity'* ]]; then
                FOCUSED=true
                break
            fi
        done
        [[ "$FOCUSED" == true ]] \
            || fail "$LABEL theme smoke did not bring the module settings to foreground"
        # Activity resume can precede the first layout frame after a system theme change.
        # Wait for a stable frame and require real module text in the accessibility tree so
        # a black transition frame or an unrelated foreground app cannot pass as a screenshot.
        sleep 2
        DEVICE_SHOT="/sdcard/deekseep-theme-$LABEL.png"
        LOCAL_SHOT="$REPORT_DIR/theme-$LABEL.png"
        DEVICE_TREE="/sdcard/deekseep-theme-$LABEL.xml"
        LOCAL_TREE="$REPORT_DIR/theme-$LABEL.xml"
        RESUMED_ACTIVITY="$(su -c 'dumpsys activity activities' 2>/dev/null \
                | rg -m1 'topResumedActivity=' || true)"
        [[ "$RESUMED_ACTIVITY" == *'com.dsmod.probe/.SettingsActivity'* ]] \
            || fail "$LABEL theme smoke lost foreground before capture"
        su -c "uiautomator dump '$DEVICE_TREE'" >/dev/null
        su -c "cp '$DEVICE_TREE' '$LOCAL_TREE'"
        su -c "chmod 644 '$LOCAL_TREE'"
        require_text "$LOCAL_TREE" '模块已激活|DeepSeek 2\.3\.4' \
            "$LABEL theme module content did not finish rendering"
        su -c "screencap -p '$DEVICE_SHOT'"
        su -c "cp '$DEVICE_SHOT' '$LOCAL_SHOT'"
        su -c "chmod 644 '$LOCAL_SHOT'"
        [[ -s "$LOCAL_SHOT" ]] || fail "$LABEL theme screenshot is empty"
    done
    restore_night_mode
    ORIGINAL_NIGHT_MODE=""
fi

echo
echo "ALL AUTOMATED TESTS PASSED"
echo "APK: $APK"
echo "Reports: $REPORT_DIR"
