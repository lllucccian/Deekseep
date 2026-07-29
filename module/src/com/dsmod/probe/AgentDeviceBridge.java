package com.dsmod.probe;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.system.Os;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Narrow privileged backend for Agent screen actions.
 *
 * <p>This deliberately does not expose an arbitrary shell tool. Every command is assembled from
 * already validated numeric coordinates or a fixed key event, which keeps Root/Shizuku useful
 * without turning a model-emitted string into unrestricted command execution.</p>
 */
final class AgentDeviceBridge {
    static final String RISH_RESOURCE =
            "META-INF/com.dsmod.probe.agent/.rish_shizuku_runtime_payload.dat";
    private static final String RISH_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_agent/rish_shizuku.dex";
    private static final String SCREENSHOT_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_agent/latest_screen.png";
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();

    interface StatusCallback {
        void onStatus(Status status);
    }

    static final class Status {
        final boolean connected;
        final String detail;

        Status(boolean connected, String detail) {
            this.connected = connected;
            this.detail = detail == null ? "" : detail;
        }
    }

    private AgentDeviceBridge() {}

    static void probe(final Context context, final String backend,
                      final StatusCallback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                Status result;
                if (AgentToolConfig.BACKEND_IN_APP.equals(backend)) {
                    result = new Status(true, UiLanguage.text(context,
                            "应用内后端已就绪", "In-app backend is ready"));
                } else {
                    CommandResult command = runCommand(
                            context, backend, "id", 5000L, false);
                    boolean connected = command.exitCode == 0
                            && (command.output.contains("uid=0")
                            || command.output.contains("uid=2000")
                            || command.output.contains("uid="));
                    String detail = connected
                            ? UiLanguage.text(context,
                            AgentToolConfig.BACKEND_ROOT.equals(backend)
                                    ? "Root 已连接"
                                    : "Shizuku 已连接（shell 权限）",
                            AgentToolConfig.BACKEND_ROOT.equals(backend)
                                    ? "Root connected"
                                    : "Shizuku connected (shell identity)")
                            : friendlyFailure(context, backend, command);
                    result = new Status(connected, detail);
                }
                final Status delivered = result;
                Handler main = new Handler(Looper.getMainLooper());
                main.post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onStatus(delivered);
                    }
                });
            }
        });
    }

    static void execute(final Context context,
                        final HeartbeatToolProtocol.ToolCall call,
                        final StatusCallback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                AgentToolConfig.Snapshot config = AgentToolConfig.load();
                Status status;
                if (AgentToolConfig.PERMISSION_EXECUTE.equals(config.permission)) {
                    status = new Status(false, UiLanguage.text(context,
                            "当前权限级别只允许应用内执行",
                            "The current permission level only allows in-app execution"));
                } else if (AgentToolConfig.BACKEND_IN_APP.equals(config.backend)) {
                    status = new Status(false, UiLanguage.text(context,
                            "尚未选择 Root 或 Shizuku 后端",
                            "No Root or Shizuku backend is selected"));
                } else if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(call.tool)) {
                    status = captureScreen(context, config.backend);
                } else {
                    String command = commandFor(call);
                    if (command.length() == 0) {
                        status = new Status(false, UiLanguage.text(context,
                                "此工具不支持高权限执行",
                                "This tool has no privileged implementation"));
                    } else {
                        CommandResult result = runCommand(
                                context, config.backend, command, 7000L, false);
                        status = new Status(result.exitCode == 0,
                                result.exitCode == 0
                                        ? UiLanguage.text(context,
                                        "工具执行完成", "Tool completed")
                                        : friendlyFailure(
                                        context, config.backend, result));
                    }
                }
                final Status delivered = status;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onStatus(delivered);
                    }
                });
            }
        });
    }

    private static String commandFor(HeartbeatToolProtocol.ToolCall call) {
        int width = Math.max(1,
                Resources.getSystem().getDisplayMetrics().widthPixels);
        int height = Math.max(1,
                Resources.getSystem().getDisplayMetrics().heightPixels);
        int x = normalized(call.x, width);
        int y = normalized(call.y, height);
        if (HeartbeatToolProtocol.TOOL_TAP_SCREEN.equals(call.tool)) {
            return "input tap " + x + " " + y;
        }
        if (HeartbeatToolProtocol.TOOL_SWIPE_SCREEN.equals(call.tool)) {
            return "input swipe " + x + " " + y + " "
                    + normalized(call.toX, width) + " "
                    + normalized(call.toY, height) + " "
                    + Math.max(120, Math.min(1200, call.durationMs));
        }
        if (HeartbeatToolProtocol.TOOL_PRESS_BACK.equals(call.tool)) {
            return "input keyevent 4";
        }
        return "";
    }

    private static int normalized(int value, int size) {
        int safe = Math.max(0, Math.min(1000, value));
        return Math.round((safe / 1000.0f) * Math.max(0, size - 1));
    }

    private static Status captureScreen(Context context, String backend) {
        File target = new File(SCREENSHOT_FILE);
        File temporary = new File(SCREENSHOT_FILE + ".tmp");
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            return new Status(false, UiLanguage.text(context,
                    "无法创建截图目录", "Could not create the screenshot directory"));
        }
        CommandResult result = runCommand(
                context, backend, "screencap -p", 10000L, true);
        if (result.exitCode != 0 || result.binary == null
                || result.binary.length < 1024) {
            return new Status(false, friendlyFailure(context, backend, result));
        }
        OutputStream output = null;
        try {
            output = new FileOutputStream(temporary, false);
            output.write(result.binary);
            output.flush();
            output.close();
            output = null;
            if (!temporary.renameTo(target)) {
                throw new IOException("screenshot rename failed");
            }
            return new Status(true, UiLanguage.text(context,
                    "屏幕截图已更新", "Screen capture updated"));
        } catch (Throwable error) {
            return new Status(false, UiLanguage.text(context,
                    "截图保存失败：", "Could not save screenshot: ")
                    + error.getClass().getSimpleName());
        } finally {
            if (output != null) try { output.close(); } catch (Throwable ignored) {}
            if (temporary.exists()) try { temporary.delete(); } catch (Throwable ignored) {}
        }
    }

    private static CommandResult runCommand(
            Context context, String backend, String command,
            long timeoutMs, boolean binaryOutput) {
        Process process = null;
        InputStream standard = null;
        InputStream error = null;
        try {
            ProcessBuilder builder;
            if (AgentToolConfig.BACKEND_ROOT.equals(backend)) {
                builder = new ProcessBuilder("su", "-c", command);
            } else if (AgentToolConfig.BACKEND_SHIZUKU.equals(backend)) {
                File dex = ensureRishDex();
                if (dex == null) {
                    return new CommandResult(-1,
                            "rish_shizuku.dex is unavailable", null);
                }
                builder = new ProcessBuilder(
                        "/system/bin/app_process",
                        "-Djava.class.path=" + dex.getAbsolutePath(),
                        "/system/bin",
                        "--nice-name=deekseep-rish",
                        "rikka.shizuku.shell.ShizukuShellLoader",
                        "-c", command);
                Map<String, String> environment = builder.environment();
                environment.put("RISH_APPLICATION_ID",
                        context == null ? "com.deepseek.chat"
                                : context.getPackageName());
                environment.put("RISH_PRESERVE_ENV", "0");
            } else {
                return new CommandResult(-1, "in-app backend", null);
            }
            process = builder.start();
            standard = process.getInputStream();
            error = process.getErrorStream();
            ByteArrayOutputStream standardBytes = new ByteArrayOutputStream();
            ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
            StreamCollector standardCollector =
                    new StreamCollector(standard, standardBytes);
            StreamCollector errorCollector = new StreamCollector(error, errorBytes);
            Thread outThread = new Thread(
                    standardCollector, "Deekseep-Agent-stdout");
            Thread errThread = new Thread(
                    errorCollector, "Deekseep-Agent-stderr");
            outThread.start();
            errThread.start();
            long deadline = SystemClock.elapsedRealtime()
                    + Math.max(500L, timeoutMs);
            Integer exit = null;
            while (SystemClock.elapsedRealtime() < deadline) {
                try {
                    exit = Integer.valueOf(process.exitValue());
                    break;
                } catch (IllegalThreadStateException running) {
                    SystemClock.sleep(25L);
                }
            }
            if (exit == null) {
                process.destroy();
                return new CommandResult(-2, "command timed out", null);
            }
            outThread.join(1000L);
            errThread.join(1000L);
            byte[] stdout = standardBytes.toByteArray();
            String text = binaryOutput ? ""
                    : new String(stdout, java.nio.charset.StandardCharsets.UTF_8).trim();
            String errorText = new String(
                    errorBytes.toByteArray(),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            if (text.length() == 0) text = errorText;
            return new CommandResult(exit.intValue(), text,
                    binaryOutput ? stdout : null);
        } catch (Throwable errorValue) {
            return new CommandResult(-1,
                    errorValue.getClass().getSimpleName() + ": "
                            + String.valueOf(errorValue.getMessage()), null);
        } finally {
            if (standard != null) try { standard.close(); } catch (Throwable ignored) {}
            if (error != null) try { error.close(); } catch (Throwable ignored) {}
            if (process != null) try { process.destroy(); } catch (Throwable ignored) {}
        }
    }

    private static File ensureRishDex() {
        OutputStream output = null;
        File destination = new File(RISH_FILE);
        File temporary = new File(RISH_FILE + ".tmp");
        try {
            if (destination.isFile() && destination.length() > 1024L) {
                Os.chmod(destination.getAbsolutePath(), 0400);
                return destination;
            }
            byte[] payload = loadBundledRish();
            if (!isValidRishPayload(payload)) return null;
            File parent = destination.getParentFile();
            if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) return null;
            output = new FileOutputStream(temporary, false);
            output.write(payload);
            output.flush();
            output.close();
            output = null;
            if (!temporary.renameTo(destination)) return null;
            Os.chmod(destination.getAbsolutePath(), 0400);
            return destination;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (output != null) try { output.close(); } catch (Throwable ignored) {}
            if (temporary.exists()) try { temporary.delete(); } catch (Throwable ignored) {}
        }
    }

    /** Loads the verified payload from the module class loader without shared storage. */
    private static byte[] loadBundledRish() {
        InputStream input = null;
        try {
            ClassLoader loader = AgentDeviceBridge.class.getClassLoader();
            input = loader == null ? null : loader.getResourceAsStream(RISH_RESOURCE);
            byte[] payload = readPayload(input);
            input = null;
            return isValidRishPayload(payload) ? payload : null;
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (input != null) try { input.close(); } catch (Throwable ignored) {}
        }
    }

    private static byte[] readPayload(InputStream input) throws IOException {
        if (input == null) return null;
        ByteArrayOutputStream output = new ByteArrayOutputStream(64 * 1024);
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count > 0) output.write(buffer, 0, count);
            if (output.size() > 256 * 1024) return null;
        }
        input.close();
        return output.toByteArray();
    }

    private static boolean isValidRishPayload(byte[] payload) {
        return payload != null && payload.length == 59672
                && payload[0] == 'd' && payload[1] == 'e'
                && payload[2] == 'x' && payload[3] == '\n';
    }

    private static String friendlyFailure(
            Context context, String backend, CommandResult result) {
        String detail = result == null ? "" : result.output;
        if (detail.length() > 180) detail = detail.substring(0, 180);
        if (AgentToolConfig.BACKEND_SHIZUKU.equals(backend)) {
            if (detail.contains("Server is not running")
                    || detail.contains("binder")) {
                return UiLanguage.text(context,
                        "Shizuku 服务未运行，请先在 Shizuku 中启动服务",
                        "Shizuku is not running; start it in the Shizuku app");
            }
            if (detail.contains("permission")
                    || detail.contains("denied")
                    || detail.contains("not allowed")) {
                return UiLanguage.text(context,
                        "请在 Shizuku 中允许 DeepSeek 使用服务",
                        "Allow DeepSeek to use Shizuku");
            }
        }
        if (AgentToolConfig.BACKEND_ROOT.equals(backend)
                && (detail.contains("denied")
                        || detail.contains("not found")
                        || detail.contains("No such file")
                        || detail.contains("Cannot run program")
                        || detail.contains("permission"))) {
            return UiLanguage.text(context,
                    "Root 未授权，请在 Root 管理器中允许 DeepSeek",
                    "Root is not authorized; allow DeepSeek in your root manager");
        }
        return UiLanguage.text(context,
                "连接失败", "Connection failed")
                + (detail.length() == 0 ? "" : "\uff1a" + detail);
    }

    private static final class CommandResult {
        final int exitCode;
        final String output;
        final byte[] binary;

        CommandResult(int exitCode, String output, byte[] binary) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.binary = binary;
        }
    }

    private static final class StreamCollector implements Runnable {
        final InputStream input;
        final OutputStream output;

        StreamCollector(InputStream input, OutputStream output) {
            this.input = input;
            this.output = output;
        }

        @Override public void run() {
            byte[] buffer = new byte[8192];
            try {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) output.write(buffer, 0, count);
                }
            } catch (Throwable ignored) {}
        }
    }
}
