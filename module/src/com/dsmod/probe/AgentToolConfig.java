package com.dsmod.probe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persistent policy for the small, user-visible Agent toolkit.
 *
 * <p>The file lives in DeepSeek's own private directory because this class normally executes
 * inside the injected host process. Defaults deliberately match the settings page contract:
 * Agent enabled, every listed tool enabled, and the highest permission level selected. Choosing
 * an execution backend is separate; in-app mode remains the safe initial transport until the
 * user explicitly connects Root or Shizuku.</p>
 */
final class AgentToolConfig {
    static final String BACKEND_IN_APP = "in_app";
    static final String BACKEND_ROOT = "root";
    static final String BACKEND_SHIZUKU = "shizuku";

    static final String PERMISSION_EXECUTE = "execute";
    static final String PERMISSION_ALL = "all";

    private static final String DIRECTORY =
            "/data/data/com.deepseek.chat/files/deekseep_agent";
    private static final String FILE_PATH = DIRECTORY + "/settings.json";
    private static final Object LOCK = new Object();

    private static final List<String> TOOLS;
    private static volatile Snapshot cached;
    private static volatile long cachedModified = Long.MIN_VALUE;

    static {
        ArrayList<String> tools = new ArrayList<>();
        tools.add(HeartbeatToolProtocol.TOOL_ASK_USER);
        tools.add(HeartbeatToolProtocol.TOOL_GET_CURRENT_TIME);
        tools.add(HeartbeatToolProtocol.TOOL_READ_FILE);
        tools.add(HeartbeatToolProtocol.TOOL_WRITE_FILE);
        tools.add(HeartbeatToolProtocol.TOOL_SHELL);
        tools.add(HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN);
        tools.add(HeartbeatToolProtocol.TOOL_TAP_SCREEN);
        tools.add(HeartbeatToolProtocol.TOOL_SWIPE_SCREEN);
        tools.add(HeartbeatToolProtocol.TOOL_PRESS_BACK);
        tools.add(HeartbeatToolProtocol.TOOL_SCHEDULE_ONCE);
        tools.add(HeartbeatToolProtocol.TOOL_SET_PLAN);
        tools.add(HeartbeatToolProtocol.TOOL_CLEAR_PLAN);
        tools.add(HeartbeatToolProtocol.TOOL_SET_INTERVAL);
        tools.add(HeartbeatToolProtocol.TOOL_BIND_CHAT);
        tools.add(HeartbeatToolProtocol.TOOL_CANCEL_HEARTBEAT);
        TOOLS = Collections.unmodifiableList(tools);
    }

    private AgentToolConfig() {}

    static final class Snapshot {
        final boolean enabled;
        final String backend;
        final String permission;
        final Set<String> enabledTools;

        Snapshot(boolean enabled, String backend, String permission,
                 Set<String> enabledTools) {
            this.enabled = enabled;
            this.backend = cleanBackend(backend);
            this.permission = cleanPermission(permission);
            LinkedHashSet<String> kept = new LinkedHashSet<>();
            if (enabledTools != null) {
                for (String tool : enabledTools) {
                    if (isKnownTool(tool)) kept.add(tool);
                }
            }
            this.enabledTools = Collections.unmodifiableSet(kept);
        }

        boolean allows(String tool) {
            return enabled && enabledTools.contains(tool);
        }

        Snapshot withEnabled(boolean value) {
            return new Snapshot(value, backend, permission, enabledTools);
        }

        Snapshot withBackend(String value) {
            return new Snapshot(enabled, value, permission, enabledTools);
        }

        Snapshot withPermission(String value) {
            return new Snapshot(enabled, backend, value, enabledTools);
        }

        Snapshot withTool(String tool, boolean value) {
            LinkedHashSet<String> next = new LinkedHashSet<>(enabledTools);
            if (value) next.add(tool);
            else next.remove(tool);
            return new Snapshot(enabled, backend, permission, next);
        }
    }

    static Snapshot defaults() {
        return new Snapshot(true, BACKEND_IN_APP, PERMISSION_ALL,
                new LinkedHashSet<>(TOOLS));
    }

    static Snapshot load() {
        synchronized (LOCK) {
            File file = new File(FILE_PATH);
            long modified = file.isFile() ? file.lastModified() : -1L;
            Snapshot present = cached;
            if (present != null && modified == cachedModified) return present;
            Snapshot loaded = defaults();
            if (file.isFile() && file.length() > 0L && file.length() <= 64L * 1024L) {
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(file));
                    StringBuilder text = new StringBuilder((int) file.length());
                    String line;
                    while ((line = reader.readLine()) != null) text.append(line);
                    loaded = decode(text.toString());
                } catch (Throwable ignored) {
                    loaded = defaults();
                } finally {
                    if (reader != null) try { reader.close(); } catch (Throwable ignored) {}
                }
            }
            cached = loaded;
            cachedModified = modified;
            return loaded;
        }
    }

    static boolean save(Snapshot value) {
        Snapshot safe = value == null ? defaults() : value;
        synchronized (LOCK) {
            File directory = new File(DIRECTORY);
            File destination = new File(FILE_PATH);
            File temporary = new File(FILE_PATH + ".tmp");
            FileWriter writer = null;
            try {
                if (!directory.isDirectory() && !directory.mkdirs()) return false;
                writer = new FileWriter(temporary, false);
                writer.write(encode(safe));
                writer.write('\n');
                writer.flush();
                writer.close();
                writer = null;
                if (!temporary.renameTo(destination)) return false;
                cached = safe;
                cachedModified = destination.lastModified();
                return true;
            } catch (Throwable ignored) {
                return false;
            } finally {
                if (writer != null) try { writer.close(); } catch (Throwable ignored) {}
                if (temporary.exists() && !temporary.equals(destination)) {
                    try { temporary.delete(); } catch (Throwable ignored) {}
                }
            }
        }
    }

    static boolean setEnabled(boolean value) {
        return save(load().withEnabled(value));
    }

    static boolean setBackend(String value) {
        return save(load().withBackend(value));
    }

    static boolean setPermission(String value) {
        return save(load().withPermission(value));
    }

    static boolean setToolEnabled(String tool, boolean value) {
        if (!isKnownTool(tool)) return false;
        return save(load().withTool(tool, value));
    }

    static boolean allows(String tool) {
        return isKnownTool(tool) && load().allows(tool);
    }

    static List<String> tools() {
        return TOOLS;
    }

    static boolean isKnownTool(String tool) {
        return tool != null && TOOLS.contains(tool);
    }

    static boolean isHeartbeatTool(String tool) {
        return HeartbeatToolProtocol.TOOL_SCHEDULE_ONCE.equals(tool)
                || HeartbeatToolProtocol.TOOL_SET_PLAN.equals(tool)
                || HeartbeatToolProtocol.TOOL_CLEAR_PLAN.equals(tool)
                || HeartbeatToolProtocol.TOOL_SET_INTERVAL.equals(tool)
                || HeartbeatToolProtocol.TOOL_BIND_CHAT.equals(tool)
                || HeartbeatToolProtocol.TOOL_CANCEL_HEARTBEAT.equals(tool);
    }

    static Set<String> effectiveTools(boolean heartbeatFeatureEnabled) {
        Snapshot snapshot = load();
        if (!snapshot.enabled) return Collections.emptySet();
        LinkedHashSet<String> enabled = new LinkedHashSet<>(snapshot.enabledTools);
        if (!heartbeatFeatureEnabled) {
            enabled.remove(HeartbeatToolProtocol.TOOL_SCHEDULE_ONCE);
            enabled.remove(HeartbeatToolProtocol.TOOL_SET_PLAN);
            enabled.remove(HeartbeatToolProtocol.TOOL_CLEAR_PLAN);
            enabled.remove(HeartbeatToolProtocol.TOOL_SET_INTERVAL);
            enabled.remove(HeartbeatToolProtocol.TOOL_BIND_CHAT);
            enabled.remove(HeartbeatToolProtocol.TOOL_CANCEL_HEARTBEAT);
        }
        return Collections.unmodifiableSet(enabled);
    }

    static String displayName(String tool, boolean chinese) {
        if (HeartbeatToolProtocol.TOOL_ASK_USER.equals(tool)) {
            return chinese ? "询问用户" : "Ask user";
        }
        if (HeartbeatToolProtocol.TOOL_GET_CURRENT_TIME.equals(tool)) {
            return chinese ? "获取当前时间" : "Get current time";
        }
        if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(tool)) {
            return chinese ? "获取屏幕截图" : "Capture screen";
        }
        if (HeartbeatToolProtocol.TOOL_READ_FILE.equals(tool)) {
            return chinese ? "读取文件" : "Read file";
        }
        if (HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(tool)) {
            return chinese ? "写入文件" : "Write file";
        }
        if (HeartbeatToolProtocol.TOOL_SHELL.equals(tool)) {
            return chinese ? "基础 Shell" : "Basic shell";
        }
        if (HeartbeatToolProtocol.TOOL_TAP_SCREEN.equals(tool)) {
            return chinese ? "点击屏幕" : "Tap screen";
        }
        if (HeartbeatToolProtocol.TOOL_SWIPE_SCREEN.equals(tool)) {
            return chinese ? "滑动屏幕" : "Swipe screen";
        }
        if (HeartbeatToolProtocol.TOOL_PRESS_BACK.equals(tool)) {
            return chinese ? "返回上一层" : "Back";
        }
        if (HeartbeatToolProtocol.TOOL_SCHEDULE_ONCE.equals(tool)) {
            return chinese ? "设置一次性心跳" : "Schedule one-time heartbeat";
        }
        if (HeartbeatToolProtocol.TOOL_SET_PLAN.equals(tool)) {
            return chinese ? "更新心跳约定" : "Update heartbeat plan";
        }
        if (HeartbeatToolProtocol.TOOL_CLEAR_PLAN.equals(tool)) {
            return chinese ? "清除心跳约定" : "Clear heartbeat plan";
        }
        if (HeartbeatToolProtocol.TOOL_SET_INTERVAL.equals(tool)) {
            return chinese ? "设置心跳间隔" : "Set heartbeat interval";
        }
        if (HeartbeatToolProtocol.TOOL_BIND_CHAT.equals(tool)) {
            return chinese ? "绑定当前对话" : "Bind current chat";
        }
        if (HeartbeatToolProtocol.TOOL_CANCEL_HEARTBEAT.equals(tool)) {
            return chinese ? "取消心跳" : "Cancel heartbeat";
        }
        return tool == null ? "" : tool;
    }

    static String description(String tool, boolean chinese) {
        if (HeartbeatToolProtocol.TOOL_ASK_USER.equals(tool)) {
            return chinese ? "以底部选项卡询问，答案会作为可见消息发回当前对话"
                    : "Ask with a bottom sheet; the answer is sent visibly to this chat";
        }
        if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(tool)) {
            return chinese ? "截取当前屏幕；外部屏幕需要 Root 或 Shizuku"
                    : "Capture the screen; external screens require Root or Shizuku";
        }
        if (HeartbeatToolProtocol.TOOL_READ_FILE.equals(tool)) {
            return chinese ? "读取文本或二进制文件片段；高权限路径需要 Root 或 Shizuku"
                    : "Read text or binary file slices; privileged paths need Root or Shizuku";
        }
        if (HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(tool)) {
            return chinese ? "覆盖或追加 UTF-8 文本；高权限路径需要 Root 或 Shizuku"
                    : "Overwrite or append UTF-8 text; privileged paths need Root or Shizuku";
        }
        if (HeartbeatToolProtocol.TOOL_SHELL.equals(tool)) {
            return chinese ? "使用 Android 系统 PATH 执行 which、cp、cat 等基础命令"
                    : "Run which, cp, cat, and other commands on Android's system PATH";
        }
        if (HeartbeatToolProtocol.TOOL_TAP_SCREEN.equals(tool)
                || HeartbeatToolProtocol.TOOL_SWIPE_SCREEN.equals(tool)
                || HeartbeatToolProtocol.TOOL_PRESS_BACK.equals(tool)) {
            return chinese ? "应用内可直接执行，外部界面需要高权限后端"
                    : "Works in-app; external UI needs a privileged backend";
        }
        if (HeartbeatToolProtocol.TOOL_GET_CURRENT_TIME.equals(tool)) {
            return chinese ? "读取设备本地时间，精确到秒"
                    : "Read local device time to the second";
        }
        return chinese ? "作用域固定为当前对话，不创建全局任务"
                : "Scoped to the current chat; never creates a global task";
    }

    static String encode(Snapshot snapshot) {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 2);
            root.put("enabled", snapshot.enabled);
            root.put("backend", cleanBackend(snapshot.backend));
            root.put("permission", cleanPermission(snapshot.permission));
            JSONArray tools = new JSONArray();
            for (String tool : TOOLS) {
                if (snapshot.enabledTools.contains(tool)) tools.put(tool);
            }
            root.put("enabled_tools", tools);
            return root.toString();
        } catch (Throwable ignored) {
            return "{\"version\":2,\"enabled\":true,\"backend\":\"in_app\","
                    + "\"permission\":\"all\",\"enabled_tools\":[]}";
        }
    }

    static Snapshot decode(String value) {
        try {
            JSONObject root = new JSONObject(value == null ? "" : value);
            boolean enabled = root.optBoolean("enabled", true);
            String backend = root.optString("backend", BACKEND_IN_APP);
            String permission = root.optString("permission", PERMISSION_ALL);
            int version = root.optInt("version", 1);
            JSONArray array = root.optJSONArray("enabled_tools");
            LinkedHashSet<String> tools = new LinkedHashSet<>();
            if (array == null) {
                tools.addAll(TOOLS);
            } else {
                for (int index = 0; index < array.length(); index++) {
                    String tool = array.optString(index, "");
                    if (isKnownTool(tool)) tools.add(tool);
                }
                if (version < 2) {
                    tools.add(HeartbeatToolProtocol.TOOL_READ_FILE);
                    tools.add(HeartbeatToolProtocol.TOOL_WRITE_FILE);
                    tools.add(HeartbeatToolProtocol.TOOL_SHELL);
                }
            }
            return new Snapshot(enabled, backend, permission, tools);
        } catch (Throwable ignored) {
            return defaults();
        }
    }

    private static String cleanBackend(String value) {
        if (BACKEND_ROOT.equals(value) || BACKEND_SHIZUKU.equals(value)) return value;
        return BACKEND_IN_APP;
    }

    private static String cleanPermission(String value) {
        return PERMISSION_EXECUTE.equals(value) ? PERMISSION_EXECUTE : PERMISSION_ALL;
    }
}
