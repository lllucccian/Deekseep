package com.dsmod.probe;

import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Manages DeepSeek's native local overrides for verified boolean feature settings. */
final class RemoteFeatureFlags {
    static final int FORCE_OFF = -1;
    static final int FOLLOW = 0;
    static final int FORCE_ON = 1;

    static final String CONFIG_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_remote_feature_overrides.json";
    static final String ATTACHMENT_GUIDE_PROMPTS =
            "deekseep_model_config_attachment_guide_prompts";

    static final class Feature {
        final String key;
        final String zh;
        final String en;
        final String detailZh;
        final String detailEn;
        final boolean inverted;
        final boolean nativeBoolean;

        Feature(String key, String zh, String en, String detailZh, String detailEn,
                boolean inverted, boolean nativeBoolean) {
            this.key = key;
            this.zh = zh;
            this.en = en;
            this.detailZh = detailZh;
            this.detailEn = detailEn;
            this.inverted = inverted;
            this.nativeBoolean = nativeBoolean;
        }
    }

    /*
     * Most entries are DeepSeek Boolean settings. The attachment guide is a 2.3.4 model-config
     * rollout rather than a standalone Boolean key, but is kept in the same manager because it is
     * presented and overridden with the same three-state contract.
     */
    static final Feature[] FEATURES = {
            feature("conversation_search_enabled", "会话搜索", "Conversation search",
                    "控制 DeepSeek 自带的会话搜索入口。",
                    "Controls DeepSeek's built-in conversation search entry."),
            feature("show_new_chat_button_above_input", "输入框上方新建对话",
                    "New chat above input",
                    "控制输入区域上方的原生新建对话按钮。",
                    "Controls the native new-chat button above the composer."),
            feature("voice_input_enabled", "语音输入", "Voice input",
                    "控制 DeepSeek 自带的语音输入能力。",
                    "Controls DeepSeek's built-in voice input."),
            invertedFeature("hide_assistant_avatar", "显示助手头像", "Show assistant avatar",
                    "开启时显示聊天页的助手头像。",
                    "Shows assistant avatars in chat when enabled."),
            feature("copy_text_without_markdown_syntax", "复制纯文本",
                    "Copy without Markdown",
                    "复制消息时移除 Markdown 标记。",
                    "Removes Markdown syntax when copying a message."),
            feature("select_text_without_markdown_syntax", "选择纯文本",
                    "Select without Markdown",
                    "选择消息文字时使用移除 Markdown 后的文本。",
                    "Uses text without Markdown syntax for text selection."),
            feature("optimize_markdown", "Markdown 优化", "Markdown optimization",
                    "控制 DeepSeek 的新版 Markdown 渲染优化。",
                    "Controls DeepSeek's optimized Markdown renderer."),
            feature("sse_auto_scroll_one_screen", "流式回复整屏跟随",
                    "One-screen stream follow",
                    "控制流式生成时的一屏自动滚动策略。",
                    "Controls one-screen auto scrolling while streaming."),
            feature("allow_file_with_search", "联网搜索允许文件", "Files with web search",
                    "控制上传文件与联网搜索能否同时使用。",
                    "Controls whether files and web search can be used together."),
            feature("disable_single_dollar_latex", "禁用单美元公式",
                    "Disable single-dollar LaTeX",
                    "不把单个美元符号包裹的内容解析为公式。",
                    "Prevents single-dollar spans from being parsed as LaTeX."),
            modelFeature(ATTACHMENT_GUIDE_PROMPTS, "上传图片候选语句",
                    "Attachment prompt suggestions",
                    "上传图片或文件后，在输入框上方显示原生候选语句。",
                    "Shows DeepSeek's native prompt suggestions above the composer after "
                            + "an image or file is attached.")
    };

    private static final Object LOCK = new Object();
    private static volatile Map<String, Integer> modes = Collections.emptyMap();
    private static volatile Map<String, Boolean> legacyServerValues = Collections.emptyMap();
    private static volatile boolean loaded;
    private static volatile boolean installed;
    private static volatile boolean migrated;
    private static volatile int loadedFormatVersion;

    private RemoteFeatureFlags() {}

    private static Feature feature(String suffix, String zh, String en,
            String detailZh, String detailEn) {
        return new Feature("kv_remote_settings_" + suffix, zh, en, detailZh, detailEn,
                false, true);
    }

    private static Feature invertedFeature(String suffix, String zh, String en,
            String detailZh, String detailEn) {
        return new Feature("kv_remote_settings_" + suffix, zh, en, detailZh, detailEn,
                true, true);
    }

    private static Feature modelFeature(String key, String zh, String en,
            String detailZh, String detailEn) {
        return new Feature(key, zh, en, detailZh, detailEn, false, false);
    }

    static String localKey(String remoteKey) {
        final String prefix = "kv_remote_settings_";
        if (remoteKey == null || !remoteKey.startsWith(prefix)) return remoteKey;
        return "kv_settings_" + remoteKey.substring(prefix.length());
    }

    static Feature featureForKey(String key) {
        for (Feature feature : FEATURES) if (feature.key.equals(key)) return feature;
        return null;
    }

    private static boolean wasManagedByVersion1(String key) {
        if (featureForKey(key) != null) return true;
        return "kv_remote_settings_show_new_chat_button_above_input".equals(key)
                || "kv_remote_settings_hide_assistant_avatar".equals(key)
                || "kv_remote_settings_sse_auto_scroll_one_screen".equals(key);
    }

    static boolean isSupported(Feature feature) {
        return feature != null && (feature.nativeBoolean
                || (ATTACHMENT_GUIDE_PROMPTS.equals(feature.key) && HostCompat.isV234()));
    }

    static boolean hostValue(Feature feature, boolean userValue) {
        return feature != null && feature.inverted ? !userValue : userValue;
    }

    static int userModeFromHost(Feature feature, boolean hostValue) {
        return hostValue(feature, hostValue) ? FORCE_ON : FORCE_OFF;
    }

    /** Config fallback used before the host MMKV is available. */
    static int mode(String key) {
        ensureLoaded();
        Integer value = modes.get(key);
        return value == null ? FOLLOW : value.intValue();
    }

    /** The actual state in DeepSeek's own local-settings layer. */
    static int mode(ClassLoader loader, String key) {
        Feature feature = featureForKey(key);
        if (feature != null && !feature.nativeBoolean) return mode(key);
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        String local = localKey(key);
        if (preferences != null && local != null) {
            try {
                if (preferences.contains(local)) {
                    return userModeFromHost(featureForKey(key),
                            preferences.getBoolean(local, false));
                }
                return FOLLOW;
            } catch (Throwable ignored) {}
        }
        return mode(key);
    }

    static boolean effectiveValue(ClassLoader loader, String key) {
        int actualMode = mode(loader, key);
        if (actualMode != FOLLOW) return actualMode == FORCE_ON;
        return rawValue(loader, key, false);
    }

    /** Reads the untouched value last delivered by DeepSeek's server. */
    static boolean rawValue(ClassLoader loader, String key, boolean fallback) {
        Feature feature = featureForKey(key);
        // 2.3.4 ships both the image/file prompt resources and hard-coded fallback lists. The
        // server can replace those lists through model_configs_v1, but there is no Boolean MMKV
        // key to read. Therefore FOLLOW accurately means using the native available state.
        if (feature != null && !feature.nativeBoolean) return true;
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (preferences == null) return fallback;
        boolean hostFallback = hostValue(feature, fallback);
        try { return hostValue(feature, preferences.getBoolean(key, hostFallback)); }
        catch (Throwable ignored) { return fallback; }
    }

    static boolean setMode(ClassLoader loader, String key, int wanted) {
        Feature feature = featureForKey(key);
        if (feature == null || !isSupported(feature)
                || (wanted != FORCE_OFF && wanted != FOLLOW && wanted != FORCE_ON)) return false;
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (feature.nativeBoolean && preferences == null) return false;
        synchronized (LOCK) {
            ensureLoadedLocked();
            HashMap<String, Integer> next = new HashMap<>(modes);
            if (wanted == FOLLOW) next.remove(key); else next.put(key, wanted);
            if (!saveLocked(next)) return false;
            try {
                if (!feature.nativeBoolean) {
                    modes = Collections.unmodifiableMap(next);
                    return true;
                }
                SharedPreferences.Editor editor = preferences.edit();
                if (wanted == FOLLOW) editor.remove(localKey(key));
                else editor.putBoolean(localKey(key),
                        hostValue(feature, wanted == FORCE_ON));
                if (!editor.commit()) return false;
            } catch (Throwable error) {
                Main.log("native feature-setting write failed key=" + key + ": " + error);
                return false;
            }
            modes = Collections.unmodifiableMap(next);
            return true;
        }
    }

    static boolean resetAll(ClassLoader loader) {
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (preferences == null) return false;
        synchronized (LOCK) {
            ensureLoadedLocked();
            if (!saveLocked(Collections.<String, Integer>emptyMap())) return false;
            try {
                SharedPreferences.Editor editor = preferences.edit();
                for (Feature feature : FEATURES) {
                    if (feature.nativeBoolean) editor.remove(localKey(feature.key));
                }
                if (!editor.commit()) return false;
            } catch (Throwable error) {
                Main.log("native feature-setting reset failed: " + error);
                return false;
            }
            modes = Collections.emptyMap();
            return true;
        }
    }

    static int overriddenCount() {
        ensureLoaded();
        return modes.size();
    }

    static int overriddenCount(ClassLoader loader) {
        int count = 0;
        for (Feature feature : FEATURES) {
            if (isSupported(feature) && mode(loader, feature.key) != FOLLOW) count++;
        }
        return count;
    }

    /**
     * Applies overrides before DeepSeek constructs its feature repositories. This is the same
     * layer used by DeepSeek's own internal settings screen, so cached server values cannot win.
     */
    static void install(Main module, ClassLoader loader) {
        if (module == null || loader == null || installed) return;
        synchronized (LOCK) {
            if (installed) return;
            ensureLoadedLocked();
            installed = true;
            Main.log("installed DeepSeek native feature-setting manager (2.2.x/2.3.x)");
        }
    }

    static void enforce(ClassLoader loader) {
        synchronized (LOCK) {
            ensureLoadedLocked();
            // MMKV may not yet be initialised at the package-load callback. Activity resume is the
            // first stable host lifecycle point; perform the one-time v1 migration here.
            if (!migrated) migrated = migrateAndEnforceLocked(loader);
            else enforceLocked(loader, false);
        }
    }

    private static boolean migrateAndEnforceLocked(ClassLoader loader) {
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (preferences == null) return false;
        try {
            SharedPreferences.Editor editor = preferences.edit();

            // Version 1 incorrectly overwrote the server layer. Restore its remembered value once.
            for (Map.Entry<String, Boolean> entry : legacyServerValues.entrySet()) {
                if (wasManagedByVersion1(entry.getKey()) && entry.getValue() != null) {
                    editor.putBoolean(entry.getKey(), entry.getValue().booleanValue());
                }
            }

            // v2/v3 could leave this remote key carrying the module's former forced value. The
            // host default is hide=true (show=false) in every inspected generation. Remove the
            // polluted value and its rollout id once, then let DeepSeek refresh it normally.
            if (loadedFormatVersion >= 2 && loadedFormatVersion < 4) {
                editor.remove("kv_remote_settings_hide_assistant_avatar");
                editor.remove("kv_remote_settings_id_hide_assistant_avatar");
            }

            // Preserve overrides made by DeepSeek's own hidden settings UI when adopting v2.
            HashMap<String, Integer> adopted = new HashMap<>(modes);
            for (Feature feature : FEATURES) {
                if (!feature.nativeBoolean) continue;
                String local = localKey(feature.key);
                if (!adopted.containsKey(feature.key) && preferences.contains(local)) {
                    adopted.put(feature.key, userModeFromHost(feature,
                            preferences.getBoolean(local, false)));
                }
            }
            modes = Collections.unmodifiableMap(adopted);
            for (Map.Entry<String, Integer> entry : adopted.entrySet()) {
                Feature feature = featureForKey(entry.getKey());
                if (feature != null && feature.nativeBoolean && isSupported(feature)) {
                    editor.putBoolean(localKey(entry.getKey()),
                            hostValue(feature, entry.getValue() == FORCE_ON));
                }
            }
            if (!editor.commit()) return false;
            for (Map.Entry<String, Integer> entry : adopted.entrySet()) {
                Feature feature = featureForKey(entry.getKey());
                if (feature != null && feature.nativeBoolean && isSupported(feature)) {
                    boolean user = entry.getValue() == FORCE_ON;
                    Main.log("native feature override applied key=" + entry.getKey()
                            + " user=" + user + " host=" + hostValue(feature, user));
                }
            }
            legacyServerValues = Collections.emptyMap();
            return saveLocked(adopted);
        } catch (Throwable error) {
            Main.log("native feature-setting migration failed: " + error);
            return false;
        }
    }

    private static boolean enforceLocked(ClassLoader loader, boolean commit) {
        SharedPreferences preferences = AccountManager.defaultMmkv(loader);
        if (preferences == null) return false;
        try {
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, Integer> entry : modes.entrySet()) {
                Feature feature = featureForKey(entry.getKey());
                if (feature != null && feature.nativeBoolean && isSupported(feature)) {
                    editor.putBoolean(localKey(entry.getKey()),
                            hostValue(feature, entry.getValue() == FORCE_ON));
                }
            }
            if (commit) return editor.commit();
            editor.apply();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (LOCK) { ensureLoadedLocked(); }
    }

    private static void ensureLoadedLocked() {
        if (loaded) return;
        HashMap<String, Integer> loadedModes = new HashMap<>();
        HashMap<String, Boolean> loadedLegacyServer = new HashMap<>();
        int formatVersion = 0;
        File file = new File(CONFIG_FILE);
        if (file.isFile() && file.length() <= 64 * 1024L) {
            FileReader reader = null;
            try {
                reader = new FileReader(file);
                StringBuilder json = new StringBuilder((int) file.length());
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    if (count > 0) json.append(buffer, 0, count);
                }
                JSONObject root = new JSONObject(json.length() == 0 ? "{}" : json.toString());
                formatVersion = root.optInt("version", 0);
                JSONObject overrides = root.optJSONObject("overrides");
                if (overrides != null) {
                    Iterator<String> keys = overrides.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        int value = overrides.optInt(key, FOLLOW);
                        if (featureForKey(key) != null && value != FOLLOW) {
                            loadedModes.put(key, value > 0 ? FORCE_ON : FORCE_OFF);
                        }
                    }
                }
                // Only v1 wrote this object. It is consumed during install and omitted thereafter.
                JSONObject originals = root.optJSONObject("server_values");
                if (originals != null) {
                    Iterator<String> keys = originals.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        if (wasManagedByVersion1(key) && originals.has(key)) {
                            loadedLegacyServer.put(key, originals.optBoolean(key));
                        }
                    }
                }
            } catch (Throwable ignored) {
                loadedModes.clear();
                loadedLegacyServer.clear();
                formatVersion = 0;
            } finally {
                if (reader != null) try { reader.close(); } catch (Throwable ignored) {}
            }
        }
        modes = Collections.unmodifiableMap(loadedModes);
        legacyServerValues = Collections.unmodifiableMap(loadedLegacyServer);
        loadedFormatVersion = formatVersion;
        loaded = true;
    }

    private static boolean saveLocked(Map<String, Integer> nextModes) {
        File target = new File(CONFIG_FILE);
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        File temp = new File(CONFIG_FILE + ".tmp");
        FileWriter writer = null;
        try {
            JSONObject root = new JSONObject();
            root.put("format", "deekseep-native-feature-overrides");
            root.put("version", 4);
            JSONObject overrides = new JSONObject();
            for (Map.Entry<String, Integer> entry : nextModes.entrySet()) {
                if (featureForKey(entry.getKey()) != null && entry.getValue() != FOLLOW) {
                    overrides.put(entry.getKey(), entry.getValue().intValue());
                }
            }
            root.put("overrides", overrides);
            writer = new FileWriter(temp, false);
            writer.write(root.toString());
            writer.flush();
            writer.close();
            writer = null;
            if (target.exists() && !target.delete()) return false;
            return temp.renameTo(target);
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (writer != null) try { writer.close(); } catch (Throwable ignored) {}
            if (temp.exists() && !temp.equals(target)) {
                try { temp.delete(); } catch (Throwable ignored) {}
            }
        }
    }
}
