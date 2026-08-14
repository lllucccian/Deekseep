#!/usr/bin/env bash
set -e
cd "$(dirname "$0")"

source ../scripts/android-tools.sh
OUT="build/thinking-test"
JSON_JAR="$OUT/lib/json-20240303.jar"
DEPS_DIR="build/test-deps"
JSON_CACHE="$DEPS_DIR/json-20240303.jar"
JSON_SHA256="3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/lib" "$DEPS_DIR"

# Tests use the already-built universal Main/adapter classes; this keeps
# the regression suite aligned with the APK that is actually distributed.
UNIVERSAL_CLASSES="../module-universal/build/classes"
if [[ ! -f "$UNIVERSAL_CLASSES/com/dsmod/probe/Main.class" ]]; then
    echo "Universal classes are missing; build module-universal before running tests" >&2
    exit 1
fi

if [[ ! -f "$JSON_CACHE" ]] || ! printf '%s  %s\n' "$JSON_SHA256" "$JSON_CACHE" \
        | sha256sum -c - >/dev/null 2>&1; then
    tmp="$JSON_CACHE.tmp"
    rm -f "$tmp"
    downloaded=false
    for base in \
        https://repo.maven.apache.org/maven2 \
        https://repo1.maven.org/maven2 \
        https://maven.aliyun.com/repository/public; do
        if curl -fsSL --connect-timeout 15 --max-time 60 \
                "$base/org/json/json/20240303/json-20240303.jar" -o "$tmp" \
                && printf '%s  %s\n' "$JSON_SHA256" "$tmp" \
                    | sha256sum -c - >/dev/null 2>&1; then
            mv "$tmp" "$JSON_CACHE"
            downloaded=true
            break
        fi
        rm -f "$tmp"
    done
    if [[ "$downloaded" != true ]]; then
        echo "Could not download a verified org.json test dependency" >&2
        exit 1
    fi
fi
cp "$JSON_CACHE" "$JSON_JAR"

javac -source 8 -target 8 -cp "$JSON_JAR:$ANDROID_JAR:$UNIVERSAL_CLASSES:build/classes" \
    -d "$OUT/classes" \
    tests/com/dsmod/probe/ChatEditorThinkingRegressionTest.java \
    tests/com/dsmod/probe/ChatEditorHistoryImageRegressionTest.java \
    tests/com/dsmod/probe/HistoryBridgeRegressionTest.java \
    tests/com/dsmod/probe/NativeSessionDeleteRegressionTest.java \
    tests/com/dsmod/probe/NativeSessionRefreshRegressionTest.java \
    tests/com/dsmod/probe/ResponsePreserverRegressionTest.java \
    tests/com/dsmod/probe/AccountCredentialCodecRegressionTest.java \
    tests/com/dsmod/probe/AccountServerValidationRegressionTest.java \
    tests/com/dsmod/probe/GoogleLoginUnlockRegressionTest.java \
    tests/com/dsmod/probe/NativeApiPatchDecoderRegressionTest.java \
    tests/com/dsmod/probe/HostCompatRegressionTest.java \
    tests/com/dsmod/probe/UiLanguagePolicyRegressionTest.java \
    tests/com/dsmod/probe/ChatAppearanceConfigRegressionTest.java \
    tests/com/dsmod/probe/ImageCutoutRegressionTest.java \
    tests/com/dsmod/probe/HeartbeatToolProtocolRegressionTest.java \
    tests/com/dsmod/probe/RichPanelRendererRegressionTest.java \
    tests/com/dsmod/probe/AgentRunStoreRegressionTest.java \
    tests/com/dsmod/probe/ChatBackupFormatRegressionTest.java \
    tests/com/dsmod/probe/DeepSeekCacheCleanerRegressionTest.java \
    tests/com/dsmod/probe/RemoteFeatureFlagsRegressionTest.java \
    tests/com/dsmod/probe/ProcessManagerRegressionTest.java \
    tests/com/dsmod/probe/ReplyReadyPolicyRegressionTest.java \
    tests/com/dsmod/probe/AutoContinuePolicyRegressionTest.java \
    src/com/dsmod/probe/NativeApiPatchDecoder.java \
    src/com/dsmod/probe/SpatialMotionController.java \
    src/com/dsmod/probe/SpatialLayerCache.java \
    src/com/dsmod/probe/ChatAppearance.java \
    src/com/dsmod/probe/ImageCutoutUi.java \
    src/com/dsmod/probe/HeartbeatToolProtocol.java \
    src/com/dsmod/probe/RichPanelRenderer.java \
    src/com/dsmod/probe/AgentRunStore.java \
    src/com/dsmod/probe/ChatBackupStore.java \
    src/com/dsmod/probe/DeekseepTools.java \
    src/com/dsmod/probe/AgentToolConfig.java \
    src/com/dsmod/probe/AgentQuestionUi.java \
    src/com/dsmod/probe/AgentDeviceBridge.java \
    src/com/dsmod/probe/ProactiveHeartbeatReceiver.java \
    src/com/dsmod/probe/QqMusicPlaybackService.java \
    src/com/dsmod/probe/LocalAudioPlaybackService.java \
    src/com/dsmod/probe/LocalAudioControlActivity.java \
    src/com/dsmod/probe/QqMusicSessionAccessService.java \
    src/com/dsmod/probe/HistoryBridge.java \
    src/com/dsmod/probe/XposedActivationProvider.java \
    src/com/dsmod/probe/XposedActivationReceiver.java \
    src/com/dsmod/probe/AccountCredentialCodec.java \
    src/com/dsmod/probe/AccountManager.java \
    src/com/dsmod/probe/RemoteFeatureFlags.java \
    src/com/dsmod/probe/ProcessManagerActivity.java \
    src/com/dsmod/probe/ReplyReadyPolicy.java \
    src/com/dsmod/probe/AutoContinuePolicy.java \
    src/com/dsmod/probe/GoogleLoginUnlock.java \
    src/com/dsmod/probe/ResponsePreserver.java \
    src/com/dsmod/probe/HostCompat.java \
    tests/tp.java \
    tests/h61.java \
    tests/sl8.java \
    tests/kv.java \
    tests/lq.java \
    tests/kv0.java \
    tests/iu0.java \
    tests/td1.java \
    tests/hv.java \
    tests/x94.java \
    tests/n02.java \
    tests/p64.java \
    tests/c74.java

# Current sources compiled by this run must precede the previous universal APK classes.
# Otherwise Java loads a stale HostCompat/Main from module-universal/build/classes and the
# compatibility regression tests validate yesterday's APK instead of the pending build.
TEST_CP="$JSON_JAR:$ANDROID_JAR:$OUT/classes:$UNIVERSAL_CLASSES:build/classes"

java -cp "$TEST_CP" \
    com.dsmod.probe.ChatEditorThinkingRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.ChatEditorHistoryImageRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.HistoryBridgeRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.NativeSessionDeleteRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.NativeSessionRefreshRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.ResponsePreserverRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.AccountCredentialCodecRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.AccountServerValidationRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.GoogleLoginUnlockRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.NativeApiPatchDecoderRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.HostCompatRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.UiLanguagePolicyRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.ChatAppearanceConfigRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.ImageCutoutRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.HeartbeatToolProtocolRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.RichPanelRendererRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.AgentRunStoreRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.ChatBackupFormatRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.DeepSeekCacheCleanerRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.RemoteFeatureFlagsRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.ProcessManagerRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.ReplyReadyPolicyRegressionTest

java -cp "$TEST_CP" \
    com.dsmod.probe.AutoContinuePolicyRegressionTest

./test-language-catalog.sh
