package com.dsmod.probe;

import java.util.HashSet;

public final class RemoteFeatureFlagsRegressionTest {
    public static void main(String[] args) {
        HashSet<String> keys = new HashSet<>();
        for (RemoteFeatureFlags.Feature feature : RemoteFeatureFlags.FEATURES) {
            if (feature.nativeBoolean) {
                require(feature.key.startsWith("kv_remote_settings_"),
                        "non-DeepSeek remote key " + feature.key);
                require(RemoteFeatureFlags.localKey(feature.key).startsWith("kv_settings_"),
                        "non-DeepSeek local override key " + feature.key);
            }
            require(keys.add(feature.key), "duplicate key " + feature.key);
            require(feature.zh.length() > 0 && feature.en.length() > 0,
                    "missing label " + feature.key);
        }
        require(keys.size() == 11, "unexpected feature count " + keys.size());
        require(keys.contains("kv_remote_settings_conversation_search_enabled"),
                "conversation search missing");
        require(keys.contains("kv_remote_settings_voice_input_enabled"),
                "voice input missing");
        require(keys.contains("kv_remote_settings_allow_file_with_search"),
                "file + search flag missing");
        require(keys.contains("kv_remote_settings_show_new_chat_button_above_input"),
                "new-chat flag missing");
        require(keys.contains("kv_remote_settings_hide_assistant_avatar"),
                "assistant-avatar flag missing");
        require(keys.contains("kv_remote_settings_sse_auto_scroll_one_screen"),
                "SSE scroll flag missing");
        require(keys.contains(RemoteFeatureFlags.ATTACHMENT_GUIDE_PROMPTS),
                "attachment guide-prompt rollout missing");
        RemoteFeatureFlags.Feature guidePrompts = RemoteFeatureFlags.featureForKey(
                RemoteFeatureFlags.ATTACHMENT_GUIDE_PROMPTS);
        require(guidePrompts != null && !guidePrompts.nativeBoolean,
                "attachment prompts must stay model-config managed");
        RemoteFeatureFlags.Feature avatar = RemoteFeatureFlags.featureForKey(
                "kv_remote_settings_hide_assistant_avatar");
        require(avatar != null && avatar.inverted, "avatar flag must expose positive semantics");
        require("显示助手头像".equals(avatar.zh), "avatar label must remain positive");
        require(!RemoteFeatureFlags.hostValue(avatar, true),
                "show-avatar ON must write hide-avatar=false");
        require(RemoteFeatureFlags.hostValue(avatar, false),
                "show-avatar OFF must write hide-avatar=true");
        require(RemoteFeatureFlags.userModeFromHost(avatar, false)
                        == RemoteFeatureFlags.FORCE_ON,
                "hide-avatar=false must display show-avatar ON");
        System.out.println("Remote feature flag regression passed");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
