package com.dsmod.probe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** A small authenticated bridge between the launcher APK and the injected target process. */
final class ModuleConfigBridge {
    static final String ACTION = "com.dsmod.probe.action.CONFIG_TRANSFER";
    static final String EXTRA_MODE = "mode";
    static final String EXTRA_JSON = "json";
    static final String EXTRA_REPLY = "reply";
    static final String MODE_EXPORT = "export";
    static final String MODE_IMPORT = "import";
    static final int RESULT_OK = 1;
    static final int RESULT_ERROR = -1;

    private static final String TARGET = "com.deepseek.chat";
    private static final String ROOT = "/data/data/com.deepseek.chat/files";
    private static final String TOKEN = "ds-config-v1-7-4-20260808";
    private static final String EXTRA_TOKEN = "token";
    private static final int MAX_FILE = 96 * 1024;
    private static volatile boolean installed;
    private static BroadcastReceiver receiver;

    /* Deliberately excludes prompts, Local API credentials, conversations, images and logs. */
    private static final String[] ALLOW = {
            "deekseep_enabled",
            "deekseep_google_login_unlock",
            "deekseep_wechat_mobile_login_unlock",
            "deekseep_nocensor",
            "deekseep_expert_unlock",
            "deekseep_chat_multiselect",
            "deekseep_message_details",
            "deekseep_fake_mute_until",
            "deekseep_fake_mute_enabled",
            "deekseep_force_training_disabled",
            "deekseep_hot_update_disabled",
            "deekseep_remote_feature_overrides.json",
            "deekseep_auto_cache_clean",
            "deekseep_auto_cache_days",
            "deekseep_language",
            "deekseep_proactive_interval_minutes",
            "deekseep_proactive_plan.txt",
            "deekseep_proactive_binding.json",
            "deekseep_agent/settings.json",
            "deekseep_appearance/config.json",
            "deekseep_hook_overlay"
    };

    private ModuleConfigBridge() {}

    static void installTarget(Context context) {
        if (context == null || installed || !TARGET.equals(context.getPackageName())) return;
        synchronized (ModuleConfigBridge.class) {
            if (installed) return;
            final Context app = context.getApplicationContext();
            receiver = new BroadcastReceiver() {
                @Override public void onReceive(Context ignored, Intent intent) {
                    if (intent == null || !TOKEN.equals(intent.getStringExtra(EXTRA_TOKEN))) return;
                    ResultReceiver reply = intent.getParcelableExtra(EXTRA_REPLY);
                    if (reply == null) return;
                    try {
                        String mode = intent.getStringExtra(EXTRA_MODE);
                        String json;
                        if (MODE_EXPORT.equals(mode)) {
                            json = exportJson();
                        } else if (MODE_IMPORT.equals(mode)) {
                            importJson(intent.getStringExtra(EXTRA_JSON));
                            json = "ok";
                        } else {
                            throw new IllegalArgumentException("unknown mode");
                        }
                        Bundle out = new Bundle();
                        out.putString(EXTRA_JSON, json);
                        reply.send(RESULT_OK, out);
                    } catch (Throwable error) {
                        Bundle out = new Bundle();
                        out.putString("error", error.getClass().getSimpleName()
                                + ": " + String.valueOf(error.getMessage()));
                        reply.send(RESULT_ERROR, out);
                    }
                }
            };
            IntentFilter filter = new IntentFilter(ACTION);
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                app.registerReceiver(receiver, filter);
            }
            installed = true;
        }
    }

    static Intent request(String mode, String json, ResultReceiver reply) {
        Intent intent = new Intent(ACTION).setPackage(TARGET);
        intent.putExtra(EXTRA_TOKEN, TOKEN);
        intent.putExtra(EXTRA_MODE, mode);
        if (json != null) intent.putExtra(EXTRA_JSON, json);
        intent.putExtra(EXTRA_REPLY, reply);
        return intent;
    }

    private static String exportJson() throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", "deekseep-config");
        root.put("version", 1);
        root.put("module", BuildInfo.MODULE_VERSION);
        root.put("exported_at", System.currentTimeMillis());
        JSONArray files = new JSONArray();
        for (String relative : ALLOW) {
            File file = resolve(relative);
            if (!file.isFile() || file.length() > MAX_FILE) continue;
            JSONObject entry = new JSONObject();
            entry.put("path", relative);
            entry.put("data", Base64.encodeToString(read(file), Base64.NO_WRAP));
            files.put(entry);
        }
        root.put("files", files);
        return root.toString(2);
    }

    private static void importJson(String json) throws Exception {
        JSONObject root = new JSONObject(json == null ? "" : json);
        if (!"deekseep-config".equals(root.optString("format"))) {
            throw new IllegalArgumentException("invalid config file");
        }
        Set<String> allowed = new HashSet<>();
        for (String value : ALLOW) allowed.add(value);
        JSONArray files = root.optJSONArray("files");
        if (files == null) throw new IllegalArgumentException("missing files");
        for (int i = 0; i < files.length(); i++) {
            JSONObject entry = files.optJSONObject(i);
            if (entry == null) continue;
            String relative = entry.optString("path", "");
            if (!allowed.contains(relative)) continue;
            byte[] data = Base64.decode(entry.optString("data", ""), Base64.DEFAULT);
            if (data.length > MAX_FILE) throw new IllegalArgumentException("file too large");
            File file = resolve(relative);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IllegalStateException("cannot create config directory");
            }
            File temp = new File(file.getAbsolutePath() + ".import");
            FileOutputStream output = new FileOutputStream(temp, false);
            output.write(data);
            output.flush();
            output.close();
            if (file.exists() && !file.delete()) throw new IllegalStateException("replace failed");
            if (!temp.renameTo(file)) throw new IllegalStateException("commit failed");
        }
    }

    private static File resolve(String relative) throws Exception {
        File root = new File(ROOT).getCanonicalFile();
        File file = new File(root, relative).getCanonicalFile();
        if (!file.getPath().startsWith(root.getPath() + File.separator)) {
            throw new SecurityException("invalid path");
        }
        return file;
    }

    private static byte[] read(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length());
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
        input.close();
        return output.toByteArray();
    }
}
