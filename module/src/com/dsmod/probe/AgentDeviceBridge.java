package com.dsmod.probe;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Bundle;
import android.os.Looper;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.system.Os;
import android.util.Base64;
import android.view.KeyEvent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Execution backend for Agent screen, file and basic shell actions.
 *
 * <p>File paths, content sizes and timeouts are strictly validated by
 * {@link HeartbeatToolProtocol}. The caller independently enforces the user's tool allow-list and
 * permission mode before an operation reaches this class.</p>
 */
final class AgentDeviceBridge {
    static final String RISH_RESOURCE =
            "META-INF/com.dsmod.probe.agent/rish_shizuku_rt.dat";
    private static final String RISH_LEGACY_RESOURCE =
            "META-INF/com.dsmod.probe.agent/.rish_shizuku_rt.dat";
    private static final String RISH_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_agent/rish_shizuku.dex";
    private static final String SCREENSHOT_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_agent/latest_screen.png";
    private static final String V241_WORKSPACE_DIR = "agent_workspace";
    static final String V241_TERMUX_REPOSITORY =
            "https://packages.termux.dev/apt/termux-main";
    static final String V241_TERMUX_BOOTSTRAP_RELEASE =
            "bootstrap-2026.08.23-r1%2Bapt.android-7";
    private static final String V241_TERMUX_BOOTSTRAP_BASE =
            "https://github.com/termux/termux-packages/releases/download/"
                    + V241_TERMUX_BOOTSTRAP_RELEASE + "/bootstrap-";
    private static final long V241_TERMUX_BOOTSTRAP_MAX_DOWNLOAD = 40L * 1024L * 1024L;
    private static final long V241_TERMUX_BOOTSTRAP_MAX_EXTRACTED = 160L * 1024L * 1024L;
    private static final String V241_WORKSPACE_SWITCH =
            "/data/data/com.deepseek.chat/files/deekseep_agent/workspace_v241.enabled";
    private static final String V241_WORKSPACE_POLICY =
            "/data/data/com.deepseek.chat/files/deekseep_agent/workspace_policy_v241.json";
    static final String WORKSPACE_TOOL_READ = "read";
    static final String WORKSPACE_TOOL_WRITE = "write";
    static final String WORKSPACE_TOOL_SHELL = "shell";
    static final String WORKSPACE_TOOL_DELETE = "delete";
    static final String WORKSPACE_TOOL_TRANSFER = "transfer";
    private static final int NORMAL_OUTPUT_LIMIT = 64 * 1024;
    private static final int SCREENSHOT_OUTPUT_LIMIT = 24 * 1024 * 1024;
    private static final String SYSTEM_PATH =
            "/system/bin:/system/xbin:/vendor/bin:/product/bin:/system_ext/bin";
    private static final String[] ROOT_BINARIES = new String[]{
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/debug_ramdisk/su",
            "/data/adb/ksu/bin/su", "/data/adb/ap/bin/su", "/data/adb/magisk/su"
    };
    private static final String SHIZUKU_START_COMMAND =
            "apk=$(/system/bin/pm path moe.shizuku.privileged.api 2>/dev/null"
                    + " | /system/bin/head -n 1); "
                    + "apk=${apk#package:}; base=${apk%/base.apk}; starter=''; "
                    + "for candidate in \"$base\"/lib/*/libshizuku.so"
                    + " \"$base\"/libshizuku.so; do "
                    + "if [ -x \"$candidate\" ]; then starter=$candidate; break; fi; done; "
                    + "if [ -z \"$starter\" ]; then "
                    + "echo 'Shizuku starter not found' >&2; exit 44; fi; "
                    + "\"$starter\"";
    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor();
    // A model may issue up to four independent reads/requests in one turn. Keeping those on the
    // single control executor made a four-file Claude Code batch behave serially and guaranteed
    // the six-second partial-result timer would fire. Data tools have their own bounded pool;
    // status probes, screen actions and music remain ordered on EXECUTOR.
    private static final ExecutorService DATA_EXECUTOR =
            Executors.newFixedThreadPool(4);
    private static final Object V241_TERMINAL_LOCK = new Object();
    private static final Object V241_WORKSPACE_CONFIRM_LOCK = new Object();
    private static final AtomicBoolean V241_TERMUX_INSTALLING = new AtomicBoolean();
    private static final String V241_TERMINAL_PROMPT_MARKER = "__DEEKSEEP_PROMPT__";
    private static Process activeWorkspaceProcessV241;
    private static OutputStream activeWorkspaceInputV241;
    private static volatile boolean activeWorkspaceAtPromptV241;
    private static String activeWorkspacePromptTailV241 = "";

    interface StatusCallback {
        void onStatus(Status status);
    }

    interface ResultCallback {
        void onResult(ToolResult result);
    }

    interface WorkspaceStreamCallback {
        void onOutput(String chunk);
        void onComplete(ToolResult result);
    }

    /**
     * The workspace is a module-owned executable sandbox, not a Compose/host hook.  Both
     * supported domestic hosts use explicit adapters here; unknown hosts never inherit it.
     */
    static boolean workspaceSupported() {
        if (!BuildInfo.PROTECTED_BUILD) return false;
        if (HostCompat.isV236()) return true; // code249 adapter: module-owned process + files
        if (HostCompat.isV241()) return true; // code257 adapter: module-owned process + files
        return false;
    }

    // Kept as a source-compatible internal alias while the existing terminal implementation is
    // progressively named by capability rather than by its original code257 rollout.
    static boolean workspaceSupportedV241() { return workspaceSupported(); }

    static boolean workspaceEnabledV241() {
        if (!workspaceSupported()) return false;
        File marker = new File(V241_WORKSPACE_SWITCH);
        if (!marker.isFile()) return true;
        FileInputStream input = null;
        try {
            input = new FileInputStream(marker);
            return input.read() != '0';
        } catch (Throwable ignored) {
            return true;
        } finally {
            try { if (input != null) input.close(); } catch (Throwable ignored) {}
        }
    }

    static boolean setWorkspaceEnabledV241(boolean enabled) {
        if (!workspaceSupported()) return false;
        File marker = new File(V241_WORKSPACE_SWITCH);
        File parent = marker.getParentFile();
        FileOutputStream output = null;
        try {
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            output = new FileOutputStream(marker, false);
            output.write(enabled ? '1' : '0');
            output.getFD().sync();
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            try { if (output != null) output.close(); } catch (Throwable ignored) {}
        }
    }

    static File workspaceV241(Context context) {
        if (!workspaceSupported() || context == null || context.getFilesDir() == null) return null;
        File workspace = new File(context.getFilesDir(), V241_WORKSPACE_DIR);
        File bin = new File(workspace, "bin");
        if ((!workspace.isDirectory() && !workspace.mkdirs())
                || (!bin.isDirectory() && !bin.mkdirs())) return null;
        return workspace;
    }

    static boolean termuxEnvironmentInstalledV241(Context context) {
        File workspace = workspaceV241(context);
        if (workspace == null) return false;
        File prefix = termuxPrefixV241(workspace);
        return new File(prefix, ".deekseep-bootstrap").isFile()
                && new File(prefix, "bin/bash").canExecute()
                && new File(prefix, "bin/pkg").canExecute()
                && new File(prefix, "bin/curl").canExecute();
    }

    static String termuxEnvironmentStatusV241(Context context) {
        if (!workspaceSupported()) return "unsupported";
        if (V241_TERMUX_INSTALLING.get()) return "installing";
        return termuxEnvironmentInstalledV241(context) ? "installed" : "not_installed";
    }

    static void installTermuxEnvironmentV241(
            final Context context, final ResultCallback callback) {
        DATA_EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                final ToolResult result = installTermuxEnvironmentNowV241(context, null);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onResult(result);
                    }
                });
            }
        });
    }

    static void removeTermuxEnvironmentV241(
            final Context context, final ResultCallback callback) {
        DATA_EXECUTOR.execute(() -> {
            ToolResult result;
            if (!workspaceSupported()) {
                result = new ToolResult(false, -1, "",
                        "Termux environment is unavailable for this host", "utf-8", false);
            } else if (V241_TERMUX_INSTALLING.get()) {
                result = new ToolResult(false, -1, "",
                        "Termux environment installation is still running", "utf-8", false);
            } else {
                cancelWorkspaceCommandV241();
                try {
                    File workspace = workspaceV241(context);
                    File container = workspace == null ? null : new File(workspace, ".termux");
                    if (container != null && container.isDirectory()) {
                        File[] children = container.listFiles();
                        if (children != null) {
                            for (File child : children) {
                                safeDeleteTermuxTreeV241(container, child);
                            }
                        }
                        if (!container.delete() && container.exists()) {
                            throw new IOException("could not remove Termux environment directory");
                        }
                    }
                    result = new ToolResult(true, 0, "",
                            "Termux environment removed; workspace files were kept",
                            "utf-8", false);
                } catch (Throwable error) {
                    result = new ToolResult(false, -1, "",
                            "Termux environment removal failed: "
                                    + error.getClass().getSimpleName() + ": "
                                    + String.valueOf(error.getMessage()), "utf-8", false);
                }
            }
            final ToolResult delivered = result;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (callback != null) callback.onResult(delivered);
            });
        });
    }

    private static File termuxPrefixV241(File workspace) {
        return new File(new File(workspace, ".termux"), "usr");
    }

    private static ToolResult installTermuxEnvironmentNowV241(
            Context context, WorkspaceStreamCallback progress) {
        if (!workspaceSupported()) return new ToolResult(false, -1, "",
                "Termux environment is unavailable for this host", "utf-8", false);
        if (!V241_TERMUX_INSTALLING.compareAndSet(false, true)) {
            return new ToolResult(false, -1, "",
                    "Termux environment installation is already running", "utf-8", false);
        }
        File archive = null;
        File staging = null;
        try {
            File workspace = workspaceV241(context);
            if (workspace == null) throw new IOException("workspace is unavailable");
            if (termuxEnvironmentInstalledV241(context)) {
                return new ToolResult(true, 0, "pkg, apt and curl are ready",
                        "Termux environment is already installed", "utf-8", false);
            }
            TermuxBootstrapV241 bootstrap = termuxBootstrapV241();
            if (bootstrap == null) throw new IOException(
                    "unsupported CPU architecture: " + android.os.Build.SUPPORTED_ABIS[0]);
            File container = new File(workspace, ".termux");
            if (!container.isDirectory() && !container.mkdirs()) {
                throw new IOException("could not create Termux environment directory");
            }
            archive = new File(container, "bootstrap.zip.download");
            staging = new File(container, "usr.staging");
            safeDeleteTermuxTreeV241(container, archive);
            safeDeleteTermuxTreeV241(container, staging);
            File prefix = termuxPrefixV241(workspace);
            if (prefix.exists()) safeDeleteTermuxTreeV241(container, prefix);

            progressV241(progress, "Downloading official Termux bootstrap from GitHub…\n");
            downloadTermuxBootstrapV241(bootstrap, archive);
            progressV241(progress, "Checksum verified. Extracting package environment…\n");
            extractTermuxBootstrapV241(archive, staging);
            File marker = new File(staging, ".deekseep-bootstrap");
            FileOutputStream markerOutput = new FileOutputStream(marker, false);
            try {
                markerOutput.write((bootstrap.arch + "\n"
                        + V241_TERMUX_BOOTSTRAP_RELEASE + "\n"
                        + bootstrap.sha256 + "\n").getBytes(StandardCharsets.UTF_8));
                markerOutput.getFD().sync();
            } finally {
                markerOutput.close();
            }
            if (!staging.renameTo(prefix)) {
                throw new IOException("could not publish extracted Termux environment");
            }
            staging = null;
            safeDeleteTermuxTreeV241(container, archive);
            archive = null;
            progressV241(progress, "Termux environment installed. pkg/apt/curl are ready.\n");
            return new ToolResult(true, 0,
                    "pkg, apt and curl are ready; use pkg install -y python git",
                    "official Termux bootstrap installed", "utf-8", false);
        } catch (Throwable error) {
            return new ToolResult(false, -1, "",
                    "Termux environment installation failed: "
                            + error.getClass().getSimpleName() + ": "
                            + String.valueOf(error.getMessage()), "utf-8", false);
        } finally {
            try {
                File workspace = workspaceV241(context);
                File container = workspace == null ? null : new File(workspace, ".termux");
                if (container != null && archive != null) {
                    safeDeleteTermuxTreeV241(container, archive);
                }
                if (container != null && staging != null) {
                    safeDeleteTermuxTreeV241(container, staging);
                }
            } catch (Throwable ignored) {}
            V241_TERMUX_INSTALLING.set(false);
        }
    }

    private static void progressV241(WorkspaceStreamCallback callback, String value) {
        if (callback != null) deliverWorkspaceOutput(callback, value);
    }

    private static TermuxBootstrapV241 termuxBootstrapV241() {
        String abi = android.os.Build.SUPPORTED_ABIS == null
                || android.os.Build.SUPPORTED_ABIS.length == 0
                ? "" : android.os.Build.SUPPORTED_ABIS[0];
        if ("arm64-v8a".equals(abi)) return new TermuxBootstrapV241(
                "aarch64", 32672724L,
                "f902017cf09c84189732b6174b56d69b9890468f4fa7394fc1354b573153688e");
        if ("armeabi-v7a".equals(abi) || "armeabi".equals(abi)) {
            return new TermuxBootstrapV241("arm", 29365088L,
                    "27fb2eaaf2ebb579e65cefcbfc7dab44ea9f2d1a0c7e73efe3a2f87a8ebfd359");
        }
        if ("x86_64".equals(abi)) return new TermuxBootstrapV241(
                "x86_64", 32584674L,
                "5f7c54e860df1ef5146b8475dc90e69af666da6588ca1fd47be8f7036b07e8cb");
        if ("x86".equals(abi)) return new TermuxBootstrapV241(
                "i686", 31788083L,
                "3037032a906b19e75fd49bbfbbb77db06f247ff6385b66e6f090f0726f691447");
        return null;
    }

    private static void downloadTermuxBootstrapV241(
            TermuxBootstrapV241 bootstrap, File destination) throws Exception {
        URL url = new URL(V241_TERMUX_BOOTSTRAP_BASE + bootstrap.arch + ".zip");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileOutputStream output = new FileOutputStream(destination, false);
        byte[] buffer = new byte[32768];
        long total = 0L;
        int failures = 0;
        try {
            while (total < bootstrap.size) {
                HttpURLConnection connection = null;
                InputStream input = null;
                try {
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(20000);
                    // GitHub's asset connection can occasionally stop producing bytes without
                    // closing. Reconnect quickly and continue with Range instead of hanging the
                    // workspace terminal indefinitely.
                    connection.setReadTimeout(30000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setRequestProperty("User-Agent", "Deekseep-Workspace/1.8");
                    if (total > 0L) connection.setRequestProperty("Range", "bytes=" + total + "-");
                    int status = connection.getResponseCode();
                    if (total > 0L && status == HttpURLConnection.HTTP_OK) {
                        // A mirror stripped Range. Restart safely rather than concatenating two
                        // complete archives and relying on the checksum to catch it later.
                        output.close();
                        output = new FileOutputStream(destination, false);
                        digest.reset();
                        total = 0L;
                    } else if (total > 0L && status == HttpURLConnection.HTTP_PARTIAL) {
                        String range = connection.getHeaderField("Content-Range");
                        if (range == null || !range.startsWith("bytes " + total + "-")) {
                            throw new IOException("invalid bootstrap resume range");
                        }
                    } else if (status < 200 || status >= 300) {
                        throw new IOException("HTTP " + status);
                    }
                    long declared = connection.getContentLengthLong();
                    if (declared > V241_TERMUX_BOOTSTRAP_MAX_DOWNLOAD
                            || (declared > 0L && total + declared > bootstrap.size)) {
                        throw new IOException("unexpected bootstrap download size " + declared);
                    }
                    input = connection.getInputStream();
                    int count;
                    long before = total;
                    while ((count = input.read(buffer)) >= 0) {
                        if (count == 0) continue;
                        total += count;
                        if (total > bootstrap.size
                                || total > V241_TERMUX_BOOTSTRAP_MAX_DOWNLOAD) {
                            throw new IOException("bootstrap download exceeds safety limit");
                        }
                        digest.update(buffer, 0, count);
                        output.write(buffer, 0, count);
                    }
                    if (total == before && total < bootstrap.size) {
                        throw new IOException("bootstrap connection returned no data");
                    }
                    failures = 0;
                } catch (IOException transientFailure) {
                    if (++failures >= 6) throw transientFailure;
                    try { Thread.sleep(Math.min(5000L, failures * 750L)); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new IOException("bootstrap download interrupted", interrupted);
                    }
                } finally {
                    if (input != null) try { input.close(); } catch (Throwable ignored) {}
                    if (connection != null) connection.disconnect();
                }
            }
            output.flush();
            output.getFD().sync();
            if (total != bootstrap.size) throw new IOException(
                    "incomplete bootstrap download: " + total + "/" + bootstrap.size);
            String actual = hexV241(digest.digest());
            if (!bootstrap.sha256.equals(actual)) throw new IOException(
                    "bootstrap checksum mismatch");
        } finally {
            try { output.close(); } catch (Throwable ignored) {}
        }
    }

    private static void extractTermuxBootstrapV241(File archive, File staging)
            throws Exception {
        if (!staging.mkdirs()) throw new IOException("could not create extraction directory");
        String root = staging.getCanonicalPath() + File.separator;
        ZipInputStream zip = new ZipInputStream(new FileInputStream(archive));
        ArrayList<String[]> symlinks = new ArrayList<>();
        byte[] buffer = new byte[16384];
        long extracted = 0L;
        int entries = 0;
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 10000) throw new IOException("too many bootstrap entries");
                String name = entry.getName();
                if (name == null || name.length() == 0) continue;
                if ("SYMLINKS.txt".equals(name)) {
                    ByteArrayOutputStream links = new ByteArrayOutputStream(65536);
                    int count;
                    while ((count = zip.read(buffer)) >= 0) {
                        if (count > 0) links.write(buffer, 0, count);
                    }
                    String[] lines = new String(links.toByteArray(), StandardCharsets.UTF_8)
                            .split("\\r?\\n");
                    for (String line : lines) {
                        int separator = line.indexOf('←');
                        if (separator <= 0 || separator >= line.length() - 1) {
                            if (line.trim().length() > 0) {
                                throw new IOException("malformed bootstrap symlink");
                            }
                            continue;
                        }
                        symlinks.add(new String[]{line.substring(0, separator),
                                line.substring(separator + 1)});
                    }
                    continue;
                }
                File target = new File(staging, name);
                String canonical = target.getCanonicalPath();
                if (!canonical.startsWith(root)) throw new IOException("unsafe zip path");
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) {
                        throw new IOException("could not create bootstrap directory");
                    }
                    Os.chmod(target.getAbsolutePath(), 0700);
                    continue;
                }
                File parent = target.getParentFile();
                if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
                    throw new IOException("could not create bootstrap parent");
                }
                FileOutputStream output = new FileOutputStream(target, false);
                try {
                    int count;
                    while ((count = zip.read(buffer)) >= 0) {
                        if (count == 0) continue;
                        extracted += count;
                        if (extracted > V241_TERMUX_BOOTSTRAP_MAX_EXTRACTED) {
                            throw new IOException("bootstrap exceeds extraction limit");
                        }
                        output.write(buffer, 0, count);
                    }
                } finally {
                    output.close();
                }
                if (name.startsWith("bin/") || name.startsWith("libexec/")
                        || name.startsWith("lib/apt/apt-helper")
                        || name.startsWith("lib/apt/methods/")) {
                    Os.chmod(target.getAbsolutePath(), 0700);
                }
            }
        } finally {
            zip.close();
        }
        if (symlinks.isEmpty()) throw new IOException("bootstrap symlink manifest missing");
        for (String[] link : symlinks) {
            String relative = link[1].startsWith("./") ? link[1].substring(2) : link[1];
            File target = new File(staging, relative);
            String canonicalParent = target.getParentFile().getCanonicalPath() + File.separator;
            if (!canonicalParent.startsWith(root)) throw new IOException("unsafe symlink path");
            File parent = target.getParentFile();
            if (!parent.isDirectory() && !parent.mkdirs()) {
                throw new IOException("could not create symlink parent");
            }
            Os.symlink(link[0], target.getAbsolutePath());
        }
    }

    private static void safeDeleteTermuxTreeV241(File container, File target)
            throws Exception {
        if (target == null) return;
        // Keep both sides in the same Android data-directory namespace. On many
        // devices /data/user/0 is an alias of /data/data; canonicalising only the
        // container therefore makes a legitimate child look outside the root.
        // lstat below still prevents following a symlink during recursive cleanup.
        String root = container.getAbsoluteFile().getPath() + File.separator;
        String path = target.getAbsoluteFile().getPath();
        if (!path.startsWith(root)) throw new IOException("unsafe Termux cleanup path");
        android.system.StructStat stat;
        try {
            stat = Os.lstat(path);
        } catch (android.system.ErrnoException missing) {
            if (missing.errno == android.system.OsConstants.ENOENT) return;
            throw missing;
        }
        if (android.system.OsConstants.S_ISDIR(stat.st_mode)) {
            File[] children = target.listFiles();
            if (children != null) {
                for (File child : children) safeDeleteTermuxTreeV241(container, child);
            }
        }
        if (!target.delete() && target.exists()) {
            throw new IOException("could not clean " + target.getName());
        }
    }

    private static String hexV241(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format(Locale.US, "%02x", item & 0xff));
        return result.toString();
    }

    private static final class TermuxBootstrapV241 {
        final String arch;
        final long size;
        final String sha256;

        TermuxBootstrapV241(String arch, long size, String sha256) {
            this.arch = arch;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    static void executeWorkspaceCommandV241(final Context context, final String command,
                                             final ResultCallback callback) {
        DATA_EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                ToolResult delivered;
                File workspace = workspaceV241(context);
                String clean = command == null ? "" : command.trim();
                if (workspace == null || clean.length() == 0 || clean.length() > 4096) {
                    delivered = new ToolResult(false, -1, "",
                            "workspace or command unavailable", "utf-8", false);
                } else if ("termux-setup".equals(clean)
                        || "termux-install".equals(clean)) {
                    delivered = installTermuxEnvironmentNowV241(context, null);
                } else if ("termux-status".equals(clean)) {
                    boolean ready = termuxEnvironmentInstalledV241(context);
                    delivered = new ToolResult(ready, ready ? 0 : 1,
                            termuxEnvironmentStatusV241(context),
                            ready ? "Termux environment is ready"
                                    : "Termux environment is not installed",
                            "utf-8", false);
                } else if (clean.startsWith("install ")) {
                    delivered = installWorkspaceToolV241(context, workspace, clean.substring(8).trim());
                } else {
                    CommandResult result = runWorkspaceShellV241(
                            context, workspace, clean, 30000L, NORMAL_OUTPUT_LIMIT);
                    delivered = new ToolResult(result.exitCode == 0, result.exitCode,
                            combinedCommandOutput(result), result.exitCode == 0
                            ? "command completed" : shellFailureV241(context,
                            AgentToolConfig.BACKEND_IN_APP, result), "utf-8", result.truncated);
                }
                final ToolResult result = delivered;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() { if (callback != null) callback.onResult(result); }
                });
            }
        });
    }

    static void streamWorkspaceCommandV241(final Context context, final String command,
                                            final WorkspaceStreamCallback callback) {
        DATA_EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                File workspace = workspaceV241(context);
                String clean = command == null ? "" : command.trim();
                if (!workspaceEnabledV241() || workspace == null
                        || clean.length() == 0 || clean.length() > 4096) {
                    deliverWorkspaceComplete(callback, new ToolResult(false, -1, "",
                            "workspace is disabled or unavailable", "utf-8", false));
                    return;
                }
                if (clean.startsWith("install ")) {
                    deliverWorkspaceOutput(callback, "Downloading from HTTPS resource…\n");
                    deliverWorkspaceComplete(callback, installWorkspaceToolV241(
                            context, workspace, clean.substring(8).trim()));
                    return;
                }
                if ("termux-repo".equals(clean)) {
                    deliverWorkspaceOutput(callback, V241_TERMUX_REPOSITORY + "\n");
                    deliverWorkspaceComplete(callback, new ToolResult(
                            true, 0, "", "repository displayed", "utf-8", false));
                    return;
                }
                if ("termux-setup".equals(clean) || "termux-install".equals(clean)) {
                    ToolResult result = installTermuxEnvironmentNowV241(context, callback);
                    if (result.output.length() > 0) {
                        deliverWorkspaceOutput(callback, result.output + "\n");
                    }
                    deliverWorkspaceComplete(callback, result);
                    return;
                }
                if ("termux-status".equals(clean)) {
                    boolean ready = termuxEnvironmentInstalledV241(context);
                    deliverWorkspaceOutput(callback, ready
                            ? "Termux environment ready · pkg apt curl\n"
                            : "Termux environment not installed\n");
                    deliverWorkspaceComplete(callback, new ToolResult(
                            ready, ready ? 0 : 1, "",
                            ready ? "environment ready" : "environment not installed",
                            "utf-8", false));
                    return;
                }
                Process process = null;
                InputStream input = null;
                OutputStream processInput = null;
                int exitCode = -1;
                boolean truncated = false;
                try {
                    final boolean interactiveRoot = "su".equals(clean)
                            || "/system/bin/su".equals(clean)
                            || "su -".equals(clean)
                            || "/system/bin/su -".equals(clean);
                    final boolean interactiveRish = "rish".equals(clean);
                    final boolean rishCommand = clean.startsWith("rish -c ");
                    ProcessBuilder builder;
                    if (interactiveRoot) {
                        builder = workspaceRootBuilderV241(clean.endsWith(" -"));
                    } else if (interactiveRish || rishCommand) {
                        // Exercise the existing recovery path once before handing the same
                        // bundled loader to the interactive process. This keeps the terminal's
                        // bare `rish` command consistent with Agent's Shizuku backend.
                        prepareWorkspaceRishV241(context);
                        builder = workspaceRishBuilderV241(context,
                                rishCommand ? clean.substring("rish -c ".length()) : null);
                    } else if (termuxEnvironmentInstalledV241(context)) {
                        builder = workspaceRootCommandBuilderV241(
                                termuxNamespaceCommandV241(workspace, clean));
                    } else {
                        builder = new ProcessBuilder("/system/bin/sh", "-c", clean);
                    }
                    builder.directory(workspace);
                    builder.redirectErrorStream(true);
                    Map<String, String> environment = builder.environment();
                    environment.put("HOME", workspace.getAbsolutePath());
                    environment.put("PATH", new File(workspace, "bin").getAbsolutePath()
                            + ":" + SYSTEM_PATH);
                    process = builder.start();
                    input = process.getInputStream();
                    processInput = process.getOutputStream();
                    synchronized (V241_TERMINAL_LOCK) {
                        if (activeWorkspaceProcessV241 != null) {
                            process.destroy();
                            deliverWorkspaceComplete(callback, new ToolResult(false, -1, "",
                                    "another terminal command is still running",
                                    "utf-8", false));
                            return;
                        }
                        activeWorkspaceProcessV241 = process;
                        activeWorkspaceInputV241 = processInput;
                    }
                    if (interactiveRoot) {
                        deliverWorkspaceOutput(callback,
                                "[Root shell active · type exit or press Ctrl+C to leave]\n");
                    } else if (interactiveRish) {
                        deliverWorkspaceOutput(callback,
                                "[Shizuku shell active · type exit or press Ctrl+C to leave]\n");
                    }
                    byte[] buffer = new byte[4096];
                    Utf8ChunkDecoderV241 decoder = new Utf8ChunkDecoderV241();
                    int total = 0;
                    long deadline = interactiveRoot || interactiveRish ? Long.MAX_VALUE
                            : SystemClock.elapsedRealtime()
                            + (isTermuxPackageCommandV241(clean)
                                    ? 20L * 60L * 1000L : 30000L);
                    while (true) {
                        while (input.available() > 0) {
                            int count = input.read(buffer, 0,
                                    Math.min(buffer.length, input.available()));
                            if (count < 0) break;
                            total += count;
                            if (total <= 1024 * 1024) {
                                deliverWorkspaceOutput(callback,
                                        decoder.decode(buffer, count, false));
                            } else {
                                truncated = true;
                            }
                        }
                        try {
                            exitCode = process.exitValue();
                            int count;
                            while ((count = input.read(buffer)) > 0) {
                                total += count;
                                if (total <= 1024 * 1024) deliverWorkspaceOutput(callback,
                                        decoder.decode(buffer, count, false));
                                else truncated = true;
                            }
                            break;
                        } catch (IllegalThreadStateException running) {
                            if (SystemClock.elapsedRealtime() >= deadline) {
                                process.destroy();
                                exitCode = -2;
                                break;
                            }
                            SystemClock.sleep(25L);
                        }
                    }
                    String decoderTail = decoder.decode(new byte[0], 0, true);
                    if (decoderTail.length() > 0) deliverWorkspaceOutput(callback, decoderTail);
                    deliverWorkspaceComplete(callback, new ToolResult(exitCode == 0,
                            exitCode, "", exitCode == 0 ? "command completed"
                            : exitCode == -2 ? "command timed out"
                            : "command exited with " + exitCode, "utf-8", truncated));
                } catch (Throwable error) {
                    deliverWorkspaceComplete(callback, new ToolResult(false, -1, "",
                            "terminal failed: " + error.getClass().getSimpleName()
                                    + ": " + String.valueOf(error.getMessage()),
                            "utf-8", truncated));
                } finally {
                    synchronized (V241_TERMINAL_LOCK) {
                        if (activeWorkspaceProcessV241 == process) {
                            activeWorkspaceProcessV241 = null;
                            activeWorkspaceInputV241 = null;
                        }
                    }
                    try { if (processInput != null) processInput.close(); }
                    catch (Throwable ignored) {}
                    try { if (input != null) input.close(); } catch (Throwable ignored) {}
                    if (process != null) process.destroy();
                }
            }
        });
    }

    static void startWorkspaceTerminalSessionV241(final Context context,
            final WorkspaceStreamCallback callback) {
        if (!workspaceSupported() || !workspaceEnabledV241()
                || !termuxEnvironmentInstalledV241(context)) {
            deliverWorkspaceComplete(callback, new ToolResult(false, -1, "",
                    "Termux environment is not installed", "utf-8", false));
            return;
        }
        DATA_EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                File workspace = workspaceV241(context);
                Process process = null;
                InputStream input = null;
                OutputStream processInput = null;
                int exitCode = -1;
                boolean truncated = false;
                try {
                    if (workspace == null) throw new IOException("workspace is unavailable");
                    ensureTermuxTerminalRcV241(workspace);
                    ProcessBuilder builder = workspaceRootCommandBuilderV241(
                            termuxInteractiveNamespaceCommandV241(workspace));
                    builder.directory(workspace);
                    builder.redirectErrorStream(true);
                    process = builder.start();
                    input = process.getInputStream();
                    processInput = process.getOutputStream();
                    synchronized (V241_TERMINAL_LOCK) {
                        if (activeWorkspaceProcessV241 != null) {
                            process.destroy();
                            deliverWorkspaceComplete(callback, new ToolResult(false, -1, "",
                                    "another terminal command is still running",
                                    "utf-8", false));
                            return;
                        }
                        activeWorkspaceProcessV241 = process;
                        activeWorkspaceInputV241 = processInput;
                        activeWorkspaceAtPromptV241 = false;
                        activeWorkspacePromptTailV241 = "";
                    }
                    byte[] buffer = new byte[4096];
                    Utf8ChunkDecoderV241 decoder = new Utf8ChunkDecoderV241();
                    int total = 0;
                    while (true) {
                        while (input.available() > 0) {
                            int count = input.read(buffer, 0,
                                    Math.min(buffer.length, input.available()));
                            if (count < 0) break;
                            total += count;
                            if (total <= 4 * 1024 * 1024) {
                                deliverTerminalOutputV241(callback,
                                        decoder.decode(buffer, count, false));
                            } else {
                                truncated = true;
                            }
                        }
                        try {
                            exitCode = process.exitValue();
                            int count;
                            while ((count = input.read(buffer)) > 0) {
                                total += count;
                                if (total <= 4 * 1024 * 1024) {
                                    deliverTerminalOutputV241(callback,
                                            decoder.decode(buffer, count, false));
                                } else {
                                    truncated = true;
                                }
                            }
                            break;
                        } catch (IllegalThreadStateException running) {
                            SystemClock.sleep(20L);
                        }
                    }
                    String decoderTail = decoder.decode(new byte[0], 0, true);
                    if (decoderTail.length() > 0) {
                        deliverTerminalOutputV241(callback, decoderTail);
                    }
                    deliverWorkspaceComplete(callback, new ToolResult(exitCode == 0,
                            exitCode, "", exitCode == 0 ? "terminal closed"
                            : "terminal exited with " + exitCode, "utf-8", truncated));
                } catch (Throwable error) {
                    deliverWorkspaceComplete(callback, new ToolResult(false, -1, "",
                            "terminal failed: " + error.getClass().getSimpleName()
                                    + ": " + String.valueOf(error.getMessage()),
                            "utf-8", truncated));
                } finally {
                    synchronized (V241_TERMINAL_LOCK) {
                        if (activeWorkspaceProcessV241 == process) {
                            activeWorkspaceProcessV241 = null;
                            activeWorkspaceInputV241 = null;
                            activeWorkspaceAtPromptV241 = false;
                            activeWorkspacePromptTailV241 = "";
                        }
                    }
                    try { if (processInput != null) processInput.close(); }
                    catch (Throwable ignored) {}
                    try { if (input != null) input.close(); } catch (Throwable ignored) {}
                    if (process != null) process.destroy();
                }
            }
        });
    }

    private static void deliverTerminalOutputV241(
            WorkspaceStreamCallback callback, String chunk) {
        if (chunk == null || chunk.length() == 0) return;
        String value = activeWorkspacePromptTailV241 + chunk;
        activeWorkspacePromptTailV241 = "";
        if (value.contains(V241_TERMINAL_PROMPT_MARKER)) {
            activeWorkspaceAtPromptV241 = true;
            value = value.replace(V241_TERMINAL_PROMPT_MARKER, "");
        } else {
            int keep = 0;
            int maximum = Math.min(value.length(), V241_TERMINAL_PROMPT_MARKER.length() - 1);
            for (int length = 1; length <= maximum; length++) {
                if (V241_TERMINAL_PROMPT_MARKER.startsWith(
                        value.substring(value.length() - length))) keep = length;
            }
            if (keep > 0) {
                activeWorkspacePromptTailV241 = value.substring(value.length() - keep);
                value = value.substring(0, value.length() - keep);
            }
        }
        // util-linux script uses CRLF on its pseudo-terminal. EditText already lays out LF;
        // retaining CR produces visible empty cursor jumps rather than terminal semantics.
        value = value.replace("\r", "");
        deliverWorkspaceOutput(callback, value);
    }

    static final class Utf8ChunkDecoderV241 {
        private byte[] pending = new byte[0];

        String decode(byte[] source, int count, boolean end) {
            int safeCount = source == null ? 0 : Math.max(0, Math.min(count, source.length));
            byte[] joined = new byte[pending.length + safeCount];
            System.arraycopy(pending, 0, joined, 0, pending.length);
            if (safeCount > 0) System.arraycopy(source, 0, joined, pending.length, safeCount);
            int complete = end ? joined.length : completeUtf8PrefixV241(joined);
            pending = Arrays.copyOfRange(joined, complete, joined.length);
            return complete == 0 ? ""
                    : new String(joined, 0, complete, StandardCharsets.UTF_8);
        }
    }

    private static int completeUtf8PrefixV241(byte[] value) {
        int length = value == null ? 0 : value.length;
        if (length == 0) return 0;
        int lead = length - 1;
        while (lead >= 0 && (value[lead] & 0xC0) == 0x80) lead--;
        if (lead < 0) return 0;
        int first = value[lead] & 0xFF;
        int expected = first < 0x80 ? 1
                : first >= 0xC2 && first <= 0xDF ? 2
                : first >= 0xE0 && first <= 0xEF ? 3
                : first >= 0xF0 && first <= 0xF4 ? 4 : 1;
        return length - lead < expected ? lead : length;
    }

    private static void ensureTermuxTerminalRcV241(File workspace) throws IOException {
        File prefix = termuxPrefixV241(workspace);
        File rc = new File(new File(prefix, "etc"), "deekseep-terminal.bashrc");
        File parent = rc.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            throw new IOException("could not create terminal configuration directory");
        }
        String value = "PS1='" + V241_TERMINAL_PROMPT_MARKER + "\\W \\$ '\n"
                + "HISTFILE=\"$HOME/.bash_history\"\n"
                + "HISTCONTROL=ignoredups:erasedups\n"
                + "HISTSIZE=500\nHISTFILESIZE=1000\n"
                + "shopt -s histappend\n"
                + "stty rows 30 cols 100 2>/dev/null\n"
                + "bind 'set enable-bracketed-paste off' 2>/dev/null\n"
                + "PROMPT_COMMAND='history -a'\n"
                + "su() {\n"
                + "  if [ \"$#\" -eq 0 ]; then\n"
                + "    command /system/bin/su -p -s \"$PREFIX/bin/bash\" 0"
                + " --noprofile --rcfile \"$PREFIX/etc/deekseep-terminal.bashrc\" -i\n"
                + "  else\n"
                + "    command /system/bin/su \"$@\"\n"
                + "  fi\n"
                + "}\n";
        FileOutputStream output = new FileOutputStream(rc, false);
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        } finally {
            output.close();
        }
    }

    private static ProcessBuilder workspaceRootBuilderV241(boolean login)
            throws IOException {
        IOException last = null;
        for (String binary : ROOT_BINARIES) {
            try {
                ProcessBuilder builder = login
                        ? new ProcessBuilder(binary, "-")
                        : new ProcessBuilder(binary);
                // Validate the executable now so the caller can fall through to the next known
                // root implementation instead of failing after ProcessBuilder is returned.
                if (!new File(binary).canExecute()) continue;
                return builder;
            } catch (Throwable error) {
                if (error instanceof IOException) last = (IOException) error;
            }
        }
        try {
            return login ? new ProcessBuilder("su", "-") : new ProcessBuilder("su");
        } catch (Throwable error) {
            throw last == null ? new IOException("no executable su") : last;
        }
    }

    private static ProcessBuilder workspaceRootCommandBuilderV241(String command)
            throws IOException {
        for (String binary : ROOT_BINARIES) {
            if (new File(binary).canExecute()) {
                return new ProcessBuilder(binary, "-c", command);
            }
        }
        return new ProcessBuilder("su", "-c", command);
    }

    private static CommandResult runWorkspaceShellV241(
            Context context, File workspace, String command,
            long timeoutMs, int outputLimit) {
        if (termuxEnvironmentInstalledV241(context)) {
            long timeout = isTermuxPackageCommandV241(command)
                    ? Math.max(timeoutMs, 20L * 60L * 1000L) : timeoutMs;
            return runCommand(context, AgentToolConfig.BACKEND_ROOT,
                    termuxNamespaceCommandV241(workspace, command),
                    timeout, false, outputLimit);
        }
        return runCommand(context, AgentToolConfig.BACKEND_IN_APP,
                workspaceCommandV241(workspace, command), timeoutMs, false, outputLimit);
    }

    private static String termuxNamespaceCommandV241(File workspace, String command) {
        File prefix = termuxPrefixV241(workspace);
        String workspacePath = workspace.getAbsolutePath();
        String prefixPath = prefix.getAbsolutePath();
        String stagedWorkspace = "/mnt/deekseep-workspace";
        String inner = "mount none / -o rprivate || exit 70; "
                // /data/user/0 aliases /data/data on Android. Preserve the workspace before
                // covering /data/data with the private Termux namespace.
                + "mkdir -p " + stagedWorkspace + " || exit 71; "
                + "mount " + shellQuote(workspacePath) + " " + stagedWorkspace
                + " -o bind || exit 72; "
                + "mount -t tmpfs -o mode=0755 tmpfs /data/data || exit 73; "
                + "mkdir -p /data/data/com.termux/files/usr"
                + " /data/data/com.termux/files/home"
                + " /data/data/com.termux/cache/apt/archives/partial || exit 74; "
                + "chown -R \"$DS_UID:$DS_UID\" /data/data/com.termux/cache || exit 74; "
                + "mount " + stagedWorkspace + "/.termux/usr"
                + " /data/data/com.termux/files/usr -o bind || exit 75; "
                + "mount " + stagedWorkspace
                + " /data/data/com.termux/files/home -o bind || exit 76; "
                + "export HOME=/data/data/com.termux/files/home"
                + " PREFIX=/data/data/com.termux/files/usr"
                + " TMPDIR=/data/data/com.termux/files/usr/tmp"
                + " SHELL=/data/data/com.termux/files/usr/bin/bash"
                + " PATH=/data/data/com.termux/files/usr/bin:/system/bin"
                + " LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib"
                + " LANG=en_US.UTF-8 TERMUX_VERSION=0.118.3; "
                + "cd /data/data/com.termux/files/home || exit 77; "
                + "exec /system/bin/su \"$DS_UID\" -g \"$DS_UID\" -G 3003 -p"
                + " -s /data/data/com.termux/files/usr/bin/bash -c "
                + shellQuote(command);
        return "DS_UID=$(/system/bin/stat -c %u " + shellQuote(workspacePath)
                + ") || exit 76; export DS_UID; "
                + "exec /system/bin/unshare -m /system/bin/sh -c " + shellQuote(inner);
    }

    private static String termuxInteractiveNamespaceCommandV241(File workspace) {
        String workspacePath = workspace.getAbsolutePath();
        String stagedWorkspace = "/mnt/deekseep-workspace";
        String prefix = "/data/data/com.termux/files/usr";
        String interactive = "exec " + prefix + "/bin/script -qefc "
                + shellQuote(prefix + "/bin/bash --noprofile --rcfile "
                        + prefix + "/etc/deekseep-terminal.bashrc -i")
                + " /dev/null";
        String inner = "mount none / -o rprivate || exit 70; "
                + "mkdir -p " + stagedWorkspace + " || exit 71; "
                + "mount " + shellQuote(workspacePath) + " " + stagedWorkspace
                + " -o bind || exit 72; "
                + "mount -t tmpfs -o mode=0755 tmpfs /data/data || exit 73; "
                + "mkdir -p /data/data/com.termux/files/usr"
                + " /data/data/com.termux/files/home"
                + " /data/data/com.termux/cache/apt/archives/partial || exit 74; "
                + "chown -R \"$DS_UID:$DS_UID\" /data/data/com.termux/cache || exit 74; "
                + "mount " + stagedWorkspace + "/.termux/usr"
                + " /data/data/com.termux/files/usr -o bind || exit 75; "
                + "mount " + stagedWorkspace
                + " /data/data/com.termux/files/home -o bind || exit 76; "
                + "export HOME=/data/data/com.termux/files/home"
                + " PREFIX=" + prefix
                + " TMPDIR=" + prefix + "/tmp"
                + " SHELL=" + prefix + "/bin/bash"
                + " PATH=" + prefix + "/bin:/system/bin"
                + " LD_LIBRARY_PATH=" + prefix + "/lib"
                + " LANG=en_US.UTF-8 TERM=xterm-256color TERMUX_VERSION=0.118.3; "
                + "cd /data/data/com.termux/files/home || exit 77; "
                + "exec /system/bin/su \"$DS_UID\" -g \"$DS_UID\" -G 3003 -p"
                + " -s " + prefix + "/bin/bash -c " + shellQuote(interactive);
        return "DS_UID=$(/system/bin/stat -c %u " + shellQuote(workspacePath)
                + ") || exit 78; export DS_UID; "
                + "exec /system/bin/unshare -m /system/bin/sh -c " + shellQuote(inner);
    }

    static boolean isLongWorkspaceCommandV241(String command) {
        String clean = command == null ? "" : command.trim();
        return "termux-setup".equals(clean) || "termux-install".equals(clean)
                || isTermuxPackageCommandV241(clean);
    }

    private static boolean isTermuxPackageCommandV241(String command) {
        String clean = command == null ? "" : command.trim();
        return clean.equals("pkg") || clean.startsWith("pkg ")
                || clean.equals("apt") || clean.startsWith("apt ")
                || clean.equals("apt-get") || clean.startsWith("apt-get ")
                || clean.equals("pip") || clean.startsWith("pip ")
                || clean.equals("pip3") || clean.startsWith("pip3 ");
    }

    private static ProcessBuilder workspaceRishBuilderV241(
            Context context, String command) throws IOException {
        File dex = ensureRishDex();
        if (dex == null) throw new IOException("rish_shizuku.dex is unavailable");
        ProcessBuilder builder = command == null
                ? new ProcessBuilder(
                        "/system/bin/app_process",
                        "-Djava.class.path=" + dex.getAbsolutePath(),
                        "/system/bin", "--nice-name=deekseep-rish",
                        "rikka.shizuku.shell.ShizukuShellLoader")
                : new ProcessBuilder(
                        "/system/bin/app_process",
                        "-Djava.class.path=" + dex.getAbsolutePath(),
                        "/system/bin", "--nice-name=deekseep-rish",
                        "rikka.shizuku.shell.ShizukuShellLoader", "-c", command);
        Map<String, String> environment = builder.environment();
        environment.put("RISH_APPLICATION_ID", context == null
                ? "com.deepseek.chat" : context.getPackageName());
        environment.put("RISH_PRESERVE_ENV", "0");
        return builder;
    }

    private static void prepareWorkspaceRishV241(Context context) {
        CommandResult readiness = runCommandWithShizukuRecovery(context,
                AgentToolConfig.BACKEND_SHIZUKU, ":", 12000L, false);
        String detail = combinedCommandOutput(readiness);
        if (readiness.exitCode == 0 || (!detail.contains("Request timeout")
                && !detail.contains("connection between the current app"))) return;
        // Some Shizuku builds report a binder request timeout when their service was frozen,
        // rather than the legacy "Server is not running" string handled by the shared backend.
        // Recover only this code257 workspace entry; legacy Agent execution remains untouched.
        CommandResult started = runCommand(context, AgentToolConfig.BACKEND_ROOT,
                SHIZUKU_START_COMMAND, 12000L, false, NORMAL_OUTPUT_LIMIT);
        if (started.exitCode == 0) SystemClock.sleep(2500L);
    }

    static boolean sendWorkspaceInputV241(String value) {
        if (!workspaceSupportedV241() || value == null) return false;
        synchronized (V241_TERMINAL_LOCK) {
            if (activeWorkspaceProcessV241 == null || activeWorkspaceInputV241 == null) {
                return false;
            }
            try {
                activeWorkspaceInputV241.write(value.getBytes(StandardCharsets.UTF_8));
                activeWorkspaceInputV241.flush();
                if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                    activeWorkspaceAtPromptV241 = false;
                }
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    static boolean cancelWorkspaceCommandV241() {
        if (!workspaceSupportedV241()) return false;
        synchronized (V241_TERMINAL_LOCK) {
            Process process = activeWorkspaceProcessV241;
            if (process == null) return false;
            try {
                if (activeWorkspaceInputV241 != null) {
                    activeWorkspaceInputV241.write(3);
                    activeWorkspaceInputV241.flush();
                }
            } catch (Throwable ignored) {}
            process.destroy();
            return true;
        }
    }

    static boolean interruptWorkspaceCommandV241() {
        if (!workspaceSupportedV241()) return false;
        synchronized (V241_TERMINAL_LOCK) {
            if (activeWorkspaceProcessV241 == null || activeWorkspaceInputV241 == null) {
                return false;
            }
            try {
                activeWorkspaceInputV241.write(3);
                activeWorkspaceInputV241.flush();
                activeWorkspaceAtPromptV241 = false;
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        }
    }

    static boolean workspaceTerminalAtPromptV241() {
        return workspaceSupportedV241() && activeWorkspaceAtPromptV241;
    }

    static boolean workspaceCommandRunningV241() {
        synchronized (V241_TERMINAL_LOCK) {
            return activeWorkspaceProcessV241 != null;
        }
    }

    private static void deliverWorkspaceOutput(
            final WorkspaceStreamCallback callback, final String chunk) {
        if (callback == null || chunk == null || chunk.length() == 0) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { callback.onOutput(chunk); }
        });
    }

    private static void deliverWorkspaceComplete(
            final WorkspaceStreamCallback callback, final ToolResult result) {
        if (callback == null) return;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override public void run() { callback.onComplete(result); }
        });
    }

    private static ToolResult installWorkspaceToolV241(
            Context context, File workspace, String arguments) {
        String[] parts = arguments.split("\\s+", 2);
        if (parts.length != 2 || !parts[0].matches("[A-Za-z0-9._-]{1,64}")) {
            return new ToolResult(false, 2, "", "usage: install <name> <https-url>", "utf-8", false);
        }
        HttpURLConnection connection = null;
        InputStream input = null;
        File pending = new File(new File(workspace, "bin"), parts[0] + ".download");
        File target = new File(new File(workspace, "bin"), parts[0]);
        try {
            URL url = new URL(parts[1]);
            if (!"https".equalsIgnoreCase(url.getProtocol())) throw new IOException("HTTPS is required");
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setInstanceFollowRedirects(true);
            input = connection.getInputStream();
            FileOutputStream output = new FileOutputStream(pending, false);
            byte[] buffer = new byte[16384];
            long total = 0;
            try {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    total += count;
                    if (total > 64L * 1024L * 1024L) throw new IOException("tool exceeds 64 MiB");
                    output.write(buffer, 0, count);
                }
                output.getFD().sync();
            } finally { output.close(); }
            if (target.exists() && !target.delete()) throw new IOException("cannot replace existing tool");
            if (!pending.renameTo(target) || !target.setExecutable(true, false)) {
                throw new IOException("cannot activate downloaded tool");
            }
            return new ToolResult(true, 0, target.getAbsolutePath(),
                    "installed " + parts[0] + " (" + total + " bytes)", "utf-8", false);
        } catch (Throwable error) {
            pending.delete();
            return new ToolResult(false, 1, "", "install failed: "
                    + error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage()),
                    "utf-8", false);
        } finally {
            try { if (input != null) input.close(); } catch (Throwable ignored) {}
            if (connection != null) connection.disconnect();
        }
    }

    private static String workspaceCommandV241(File workspace, String command) {
        String root = shellQuote(workspace.getAbsolutePath());
        return "HOME=" + root + "; PATH=" + root + "/bin:" + SYSTEM_PATH
                + "; export HOME PATH; cd " + root + " && "
                + shellLocalePrefixV241(command) + command;
    }

    private static boolean workspacePathAndCommandAllowedV241(
            Context context, HeartbeatToolProtocol.ToolCall call) {
        if (!HostCompat.isV241() || call == null) return false;
        File workspace = workspaceV241(context);
        if (workspace == null) return false;
        try {
            String root = workspace.getCanonicalPath();
            if (HeartbeatToolProtocol.TOOL_READ_FILE.equals(call.tool)
                    || HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(call.tool)) {
                String path = new File(call.path).getCanonicalPath();
                return path.equals(root) || path.startsWith(root + File.separator);
            }
            if (HeartbeatToolProtocol.TOOL_DELETE_FILE.equals(call.tool)) {
                String path = new File(call.path).getCanonicalPath();
                return !path.equals(root) && path.startsWith(root + File.separator);
            }
            if (HeartbeatToolProtocol.TOOL_TRANSFER_FILE.equals(call.tool)) {
                String source = new File(call.path).getCanonicalPath();
                String destination = new File(call.targetId).getCanonicalPath();
                boolean sourceInside = source.equals(root)
                        || source.startsWith(root + File.separator);
                boolean destinationInside = destination.equals(root)
                        || destination.startsWith(root + File.separator);
                return sourceInside != destinationInside;
            }
            if (HeartbeatToolProtocol.TOOL_SHELL.equals(call.tool)) {
                String command = call.command == null ? "" : call.command;
                String lower = command.toLowerCase(Locale.US);
                return command.indexOf("../") < 0 && command.indexOf("..\\") < 0
                        && lower.indexOf("/storage/") < 0
                        && lower.indexOf("/sdcard/") < 0
                        && lower.indexOf("/data/media/") < 0
                        && lower.indexOf("/data/user/") < 0
                        && lower.indexOf("/data/data/") < 0;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean confirmWorkspaceToolV241(Context context, String tool,
            HeartbeatToolProtocol.ToolCall call) {
        if (!HostCompat.isV241()) return false;
        final android.app.Activity activity = context instanceof android.app.Activity
                ? (android.app.Activity) context : Main.currentHostActivityForAgent();
        if (activity == null || activity.isFinishing()) return false;
        synchronized (V241_WORKSPACE_CONFIRM_LOCK) {
            final boolean[] accepted = new boolean[]{false};
            final CountDownLatch done = new CountDownLatch(1);
            final String target = call.path != null && call.path.length() > 0
                    ? call.path : UiLanguage.text(context,
                    "工作区命令", "Workspace command");
            new Handler(Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing()) {
                    done.countDown();
                    return;
                }
                new android.app.AlertDialog.Builder(activity)
                        .setTitle(UiLanguage.text(activity,
                                "确认 Agent 工作区操作", "Confirm Agent Workspace action"))
                        .setMessage(UiLanguage.text(activity,
                                "工具：" + tool + "\n目标：" + target
                                        + "\n\n仅在你确认后执行这一次。",
                                "Tool: " + tool + "\nTarget: " + target
                                        + "\n\nThis operation runs once after confirmation."))
                        .setNegativeButton(UiLanguage.text(activity,
                                "拒绝", "Deny"), (dialog, which) -> done.countDown())
                        .setPositiveButton(UiLanguage.text(activity,
                                "允许一次", "Allow once"), (dialog, which) -> {
                                    accepted[0] = true;
                                    done.countDown();
                                })
                        .setOnCancelListener(dialog -> done.countDown())
                        .show();
            });
            try { done.await(45L, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return accepted[0];
        }
    }

    static final class WorkspacePolicyV241 {
        final boolean read;
        final boolean write;
        final boolean shell;
        final boolean delete;
        final boolean transfer;
        final boolean allowAll;

        WorkspacePolicyV241(boolean read, boolean write, boolean shell,
                boolean delete, boolean transfer, boolean allowAll) {
            this.read = read;
            this.write = write;
            this.shell = shell;
            this.delete = delete;
            this.transfer = transfer;
            this.allowAll = allowAll;
        }

        boolean allows(String tool) {
            if (WORKSPACE_TOOL_READ.equals(tool)) return read;
            if (WORKSPACE_TOOL_WRITE.equals(tool)) return write;
            if (WORKSPACE_TOOL_SHELL.equals(tool)) return shell;
            if (WORKSPACE_TOOL_DELETE.equals(tool)) return delete;
            if (WORKSPACE_TOOL_TRANSFER.equals(tool)) return transfer;
            return false;
        }

        WorkspacePolicyV241 withTool(String tool, boolean enabled) {
            return new WorkspacePolicyV241(
                    WORKSPACE_TOOL_READ.equals(tool) ? enabled : read,
                    WORKSPACE_TOOL_WRITE.equals(tool) ? enabled : write,
                    WORKSPACE_TOOL_SHELL.equals(tool) ? enabled : shell,
                    WORKSPACE_TOOL_DELETE.equals(tool) ? enabled : delete,
                    WORKSPACE_TOOL_TRANSFER.equals(tool) ? enabled : transfer,
                    allowAll);
        }

        WorkspacePolicyV241 withAllowAll(boolean enabled) {
            return new WorkspacePolicyV241(read, write, shell, delete, transfer, enabled);
        }
    }

    static WorkspacePolicyV241 workspacePolicyV241() {
        if (!workspaceSupportedV241()) {
            return new WorkspacePolicyV241(false, false, false, false, false, false);
        }
        File file = new File(V241_WORKSPACE_POLICY);
        if (!file.isFile() || file.length() <= 0L || file.length() > 16384L) {
            return new WorkspacePolicyV241(true, false, false, false, false, false);
        }
        FileInputStream input = null;
        try {
            input = new FileInputStream(file);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream((int) file.length());
            byte[] buffer = new byte[2048];
            int count;
            while ((count = input.read(buffer)) > 0) bytes.write(buffer, 0, count);
            JSONObject value = new JSONObject(bytes.toString("UTF-8"));
            return new WorkspacePolicyV241(
                    value.optBoolean("read", true),
                    value.optBoolean("write", false),
                    value.optBoolean("shell", false),
                    value.optBoolean("delete", false),
                    value.optBoolean("transfer", false),
                    value.optBoolean("allow_all", false));
        } catch (Throwable ignored) {
            return new WorkspacePolicyV241(true, false, false, false, false, false);
        } finally {
            try { if (input != null) input.close(); } catch (Throwable ignored) {}
        }
    }

    static boolean setWorkspaceToolEnabledV241(String tool, boolean enabled) {
        if (!workspaceSupportedV241()) return false;
        return saveWorkspacePolicyV241(workspacePolicyV241().withTool(tool, enabled));
    }

    static boolean setWorkspaceAllowAllV241(boolean enabled) {
        if (!workspaceSupportedV241()) return false;
        return saveWorkspacePolicyV241(workspacePolicyV241().withAllowAll(enabled));
    }

    private static boolean saveWorkspacePolicyV241(WorkspacePolicyV241 policy) {
        if (!workspaceSupportedV241() || policy == null) return false;
        File target = new File(V241_WORKSPACE_POLICY);
        File parent = target.getParentFile();
        File pending = new File(V241_WORKSPACE_POLICY + ".tmp");
        FileOutputStream output = null;
        try {
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
            JSONObject value = new JSONObject();
            value.put("version", 1);
            value.put("read", policy.read);
            value.put("write", policy.write);
            value.put("shell", policy.shell);
            value.put("delete", policy.delete);
            value.put("transfer", policy.transfer);
            value.put("allow_all", policy.allowAll);
            output = new FileOutputStream(pending, false);
            output.write((value.toString() + "\n").getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
            output.close();
            output = null;
            if (target.exists() && !target.delete()) return false;
            return pending.renameTo(target);
        } catch (Throwable ignored) {
            return false;
        } finally {
            try { if (output != null) output.close(); } catch (Throwable ignored) {}
            if (pending.exists() && !pending.equals(target)) pending.delete();
        }
    }

    static final class Status {
        final boolean connected;
        final String detail;

        Status(boolean connected, String detail) {
            this.connected = connected;
            this.detail = detail == null ? "" : detail;
        }
    }

    static final class ToolResult {
        final boolean success;
        final int exitCode;
        final String output;
        final String detail;
        final String encoding;
        final boolean truncated;

        ToolResult(boolean success, int exitCode, String output,
                   String detail, String encoding, boolean truncated) {
            this.success = success;
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.detail = detail == null ? "" : detail;
            this.encoding = encoding == null ? "utf-8" : encoding;
            this.truncated = truncated;
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
                    String probeCommand = AgentToolConfig.BACKEND_ROOT.equals(backend)
                            ? "root_version=$(/system/bin/su -v 2>/dev/null || true); "
                                    + "echo \"$root_version\"; id; "
                                    + "case \"$root_version\" in "
                                    + "*SukiSU*|*sukisu*) echo __DEEKSEEP_ROOT__=SukiSU_Ultra;; "
                                    + "*APatch*|*apatch*) echo __DEEKSEEP_ROOT__=APatch;; "
                                    + "*KernelSU*|*kernelsu*) echo __DEEKSEEP_ROOT__=KernelSU;; "
                                    + "*Kitsune*|*kitsune*|*Alpha*|*alpha*) echo __DEEKSEEP_ROOT__=Magisk_Alpha;; "
                                    + "*Magisk*|*magisk*) echo __DEEKSEEP_ROOT__=Magisk;; "
                                    + "*) if [ -d /data/adb/ap ] || [ -d /data/adb/apatch ]; then echo __DEEKSEEP_ROOT__=APatch; "
                                    + "elif [ -d /data/adb/ksu ]; then echo __DEEKSEEP_ROOT__=KernelSU; "
                                    + "elif [ -d /data/adb/magisk ]; then echo __DEEKSEEP_ROOT__=Magisk; "
                                    + "else echo __DEEKSEEP_ROOT__=Root; fi"
                                    + ";; esac"
                            : "id";
                    CommandResult command = runCommandWithShizukuRecovery(
                            context, backend, probeCommand, 5000L, false);
                    String identity = combinedCommandOutput(command);
                    boolean connected = command.exitCode == 0
                            && (AgentToolConfig.BACKEND_ROOT.equals(backend)
                            ? identity.matches("(?s).*(^|\\s)uid=0(?:\\D|$).*")
                            : identity.matches("(?s).*(^|\\s)uid=2000(?:\\D|$).*"));
                    Main.log("agent privilege probe backend=" + backend
                            + " appUid=" + android.os.Process.myUid()
                            + " exit=" + command.exitCode
                            + " identity=" + diagnosticOutput(identity));
                    String rootManager = rootManagerName(identity);
                    String detail = connected
                            ? UiLanguage.text(context,
                            AgentToolConfig.BACKEND_ROOT.equals(backend)
                                    ? rootManager + " 已连接"
                                    : "Shizuku 已连接（shell 权限）",
                            AgentToolConfig.BACKEND_ROOT.equals(backend)
                                    ? rootManager + " connected"
                                    : "Shizuku connected (shell identity)")
                            : friendlyFailure(context, backend, command);
                    if (connected) {
                        boolean backgroundAllowed = allowDeepSeekBackground(
                                context, backend);
                        if (backgroundAllowed) {
                            detail += UiLanguage.text(context,
                                    "，已自动允许后台运行",
                                    "; background execution allowed automatically");
                        } else {
                            detail += UiLanguage.text(context,
                                    "，后台白名单未变更（不影响连接）",
                                    "; background allow-list was unchanged");
                        }
                    }
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
                        CommandResult result = runCommandWithShizukuRecovery(
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

    static void executeDataTool(
            final Context context,
            final HeartbeatToolProtocol.ToolCall call,
            final ResultCallback callback) {
        DATA_EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                AgentToolConfig.Snapshot config = AgentToolConfig.load();
                String backend = config.backend;
                if (!AgentToolConfig.PERMISSION_ALL.equals(config.permission)) {
                    backend = AgentToolConfig.BACKEND_IN_APP;
                }
                ToolResult result;
                // Both explicitly supported hosts run the same module-owned workspace policy;
                // host-specific shell dispatch below remains separate.
                WorkspacePolicyV241 workspacePolicy = workspaceSupported()
                        && workspaceEnabledV241() ? workspacePolicyV241() : null;
                String workspaceTool = call == null ? "" :
                        HeartbeatToolProtocol.TOOL_READ_FILE.equals(call.tool)
                                ? WORKSPACE_TOOL_READ
                        : HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(call.tool)
                                ? WORKSPACE_TOOL_WRITE
                        : HeartbeatToolProtocol.TOOL_SHELL.equals(call.tool)
                                ? WORKSPACE_TOOL_SHELL
                        : HeartbeatToolProtocol.TOOL_DELETE_FILE.equals(call.tool)
                                ? WORKSPACE_TOOL_DELETE
                        : HeartbeatToolProtocol.TOOL_TRANSFER_FILE.equals(call.tool)
                                ? WORKSPACE_TOOL_TRANSFER : "";
                if (call == null) {
                    result = new ToolResult(false, -1, "",
                            UiLanguage.text(context,
                                    "工具调用为空", "The tool call is empty"),
                            "utf-8", false);
                } else if (workspacePolicy != null && workspaceTool.length() > 0
                        && !workspacePolicy.allows(workspaceTool)) {
                    result = new ToolResult(false, -3, "",
                            UiLanguage.text(context,
                                    "工作区设置未允许此工具",
                                    "This tool is disabled in Workspace settings"),
                            "utf-8", false);
                } else if (workspacePolicy != null && workspaceTool.length() > 0
                        && !workspacePolicy.allowAll
                        && !workspacePathAndCommandAllowedV241(context, call)) {
                    result = new ToolResult(false, -3, "",
                            UiLanguage.text(context,
                                    "默认模式只允许操作工作区；外部目录修改需要在工作区设置中开启完整权限",
                                    "Default mode is restricted to the workspace; external changes require Full access"),
                            "utf-8", false);
                } else if (workspacePolicy != null && workspaceTool.length() > 0
                        && !workspacePolicy.allowAll
                        && !confirmWorkspaceToolV241(context, workspaceTool, call)) {
                    result = new ToolResult(false, -4, "",
                            UiLanguage.text(context,
                                    "用户未确认此次工作区操作",
                                    "The user did not confirm this Workspace operation"),
                            "utf-8", false);
                } else if (HeartbeatToolProtocol.TOOL_READ_FILE.equals(call.tool)) {
                    result = readFile(context, backend, call);
                } else if (HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(call.tool)) {
                    result = writeFile(context, backend, call);
                } else if (HeartbeatToolProtocol.TOOL_SHELL.equals(call.tool)) {
                    if (HostCompat.isV236()) {
                        result = executeShellV236(context, backend, call);
                    } else if (HostCompat.isV241()) {
                        result = executeShellV241(context, backend, call);
                    } else {
                        result = executeShellLegacy(context, backend, call);
                    }
                } else if (HeartbeatToolProtocol.TOOL_DELETE_FILE.equals(call.tool)) {
                    result = deleteWorkspacePathV241(context, call);
                } else if (HeartbeatToolProtocol.TOOL_TRANSFER_FILE.equals(call.tool)) {
                    result = transferWorkspaceFileV241(context, call);
                } else if (HeartbeatToolProtocol.TOOL_NETWORK_REQUEST.equals(call.tool)) {
                    result = executeNetworkRequest(context, backend, call);
                } else if (HeartbeatToolProtocol.TOOL_LIST_APPS.equals(call.tool)) {
                    result = listInstalledApps(context, call);
                } else if (HeartbeatToolProtocol.TOOL_APP_INFO.equals(call.tool)) {
                    result = installedAppInfo(context, call);
                } else if (HeartbeatToolProtocol.TOOL_UNINSTALL_APP.equals(call.tool)) {
                    result = requestSystemUninstall(context, call);
                } else {
                    result = new ToolResult(false, -1, "",
                            UiLanguage.text(context,
                                    "此工具没有文件或 Shell 实现",
                                    "This tool has no file or shell implementation"),
                            "utf-8", false);
                }
                final ToolResult delivered = result;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onResult(delivered);
                    }
                });
            }
        });
    }

    /** Read-only in-app inventory. Limit output to preserve the model's context window. */
    private static ToolResult listInstalledApps(Context context,
            HeartbeatToolProtocol.ToolCall call) {
        if (context == null) return new ToolResult(false, -1, "", "context unavailable", "utf-8", false);
        try {
            PackageManager manager = context.getPackageManager();
            List<PackageInfo> packages = manager.getInstalledPackages(0);
            JSONArray result = new JSONArray();
            int limit = Math.max(1, Math.min(200, call == null ? 80 : call.minutes));
            for (PackageInfo info : packages) {
                if (info == null || info.applicationInfo == null) continue;
                ApplicationInfo app = info.applicationInfo;
                JSONObject item = new JSONObject();
                item.put("package", info.packageName);
                item.put("name", String.valueOf(manager.getApplicationLabel(app)));
                item.put("version", info.versionName == null ? "" : info.versionName);
                item.put("enabled", app.enabled);
                item.put("system", (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
                result.put(item);
                if (result.length() >= limit) break;
            }
            JSONObject envelope = new JSONObject();
            envelope.put("apps", result);
            envelope.put("returned", result.length());
            envelope.put("limited", packages.size() > result.length());
            return new ToolResult(true, 0, envelope.toString(), "installed apps listed", "utf-8",
                    packages.size() > result.length());
        } catch (Throwable error) {
            return failureResult(context, AgentToolConfig.BACKEND_IN_APP, error);
        }
    }

    private static ToolResult installedAppInfo(Context context,
            HeartbeatToolProtocol.ToolCall call) {
        if (context == null || call == null || call.targetId.length() == 0) {
            return new ToolResult(false, -1, "", "package is required", "utf-8", false);
        }
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(call.targetId, 0);
            ApplicationInfo app = info.applicationInfo;
            JSONObject result = new JSONObject();
            result.put("package", info.packageName);
            result.put("name", String.valueOf(manager.getApplicationLabel(app)));
            result.put("version", info.versionName == null ? "" : info.versionName);
            result.put("enabled", app.enabled);
            result.put("system", (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0);
            result.put("launchable", manager.getLaunchIntentForPackage(info.packageName) != null);
            return new ToolResult(true, 0, result.toString(), "app details read", "utf-8", false);
        } catch (PackageManager.NameNotFoundException missing) {
            return new ToolResult(false, 1, "", "package is not installed", "utf-8", false);
        } catch (Throwable error) {
            return failureResult(context, AgentToolConfig.BACKEND_IN_APP, error);
        }
    }

    /** Android owns the destructive decision: this only opens its confirmation UI. */
    private static ToolResult requestSystemUninstall(Context context,
            HeartbeatToolProtocol.ToolCall call) {
        if (context == null || call == null || call.targetId.length() == 0) {
            return new ToolResult(false, -1, "", "package is required", "utf-8", false);
        }
        if ("com.deepseek.chat".equals(call.targetId) || Main.SELF.equals(call.targetId)) {
            return new ToolResult(false, -3, "", "protected package cannot be uninstalled", "utf-8", false);
        }
        try {
            context.getPackageManager().getPackageInfo(call.targetId, 0);
            Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE,
                    Uri.parse("package:" + call.targetId));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return new ToolResult(true, 0, "", "system uninstall confirmation opened", "utf-8", false);
        } catch (PackageManager.NameNotFoundException missing) {
            return new ToolResult(false, 1, "", "package is not installed", "utf-8", false);
        } catch (Throwable error) {
            return failureResult(context, AgentToolConfig.BACKEND_IN_APP, error);
        }
    }

    private static ToolResult deleteWorkspacePathV241(Context context,
            HeartbeatToolProtocol.ToolCall call) {
        if (!workspaceSupportedV241() || call == null) {
            return new ToolResult(false, -1, "", "workspace delete unavailable",
                    "utf-8", false);
        }
        try {
            File workspace = workspaceV241(context).getCanonicalFile();
            File target = new File(call.path).getCanonicalFile();
            if (target.equals(workspace)
                    || !target.getPath().startsWith(workspace.getPath() + File.separator)) {
                return new ToolResult(false, -3, "",
                        "delete_file only accepts paths inside the workspace",
                        "utf-8", false);
            }
            if (!target.exists()) {
                return new ToolResult(false, 1, "", "path does not exist",
                        "utf-8", false);
            }
            if (target.isDirectory()) {
                String[] children = target.list();
                if (children == null || children.length != 0) {
                    return new ToolResult(false, 1, "",
                            "only empty workspace directories can be deleted",
                            "utf-8", false);
                }
            }
            if (!target.delete()) {
                return new ToolResult(false, 1, "", "could not delete workspace path",
                        "utf-8", false);
            }
            return new ToolResult(true, 0, "", "deleted " + target.getPath(),
                    "utf-8", false);
        } catch (Throwable error) {
            return failureResult(context, AgentToolConfig.BACKEND_IN_APP, error);
        }
    }

    private static ToolResult transferWorkspaceFileV241(Context context,
            HeartbeatToolProtocol.ToolCall call) {
        if (!workspaceSupportedV241() || call == null) {
            return new ToolResult(false, -1, "", "workspace transfer unavailable",
                    "utf-8", false);
        }
        File pending = null;
        FileInputStream input = null;
        FileOutputStream output = null;
        try {
            File workspace = workspaceV241(context).getCanonicalFile();
            File source = new File(call.path).getCanonicalFile();
            File destination = new File(call.targetId).getCanonicalFile();
            String prefix = workspace.getPath() + File.separator;
            boolean sourceInside = source.equals(workspace)
                    || source.getPath().startsWith(prefix);
            boolean destinationInside = destination.equals(workspace)
                    || destination.getPath().startsWith(prefix);
            if (sourceInside == destinationInside) {
                return new ToolResult(false, -3, "",
                        "transfer_file requires exactly one workspace endpoint",
                        "utf-8", false);
            }
            if (!source.isFile()) {
                return new ToolResult(false, 1, "", "source is not a regular file",
                        "utf-8", false);
            }
            if (source.length() > 128L * 1024L * 1024L) {
                return new ToolResult(false, 1, "", "source exceeds 128 MiB",
                        "utf-8", false);
            }
            if (destination.exists()) {
                return new ToolResult(false, 1, "",
                        "destination already exists; external files are never overwritten",
                        "utf-8", false);
            }
            File parent = destination.getParentFile();
            if (parent == null) throw new IOException("destination has no parent");
            if (!parent.isDirectory()) {
                if (!destinationInside || !parent.mkdirs()) {
                    throw new IOException("destination parent is unavailable");
                }
            }
            pending = new File(parent, ".deekseep-copy-"
                    + Long.toHexString(System.nanoTime()) + ".tmp");
            input = new FileInputStream(source);
            output = new FileOutputStream(pending, false);
            byte[] buffer = new byte[32768];
            long copied = 0L;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                copied += count;
                if (copied > 128L * 1024L * 1024L) {
                    throw new IOException("source exceeds 128 MiB");
                }
                output.write(buffer, 0, count);
            }
            output.flush();
            output.getFD().sync();
            output.close();
            output = null;
            input.close();
            input = null;
            if (destination.exists() || !pending.renameTo(destination)) {
                throw new IOException("could not publish copied file without overwrite");
            }
            pending = null;
            return new ToolResult(true, 0, destination.getPath(),
                    "copied " + copied + " bytes", "utf-8", false);
        } catch (Throwable error) {
            return failureResult(context, AgentToolConfig.BACKEND_IN_APP, error);
        } finally {
            if (input != null) try { input.close(); } catch (Throwable ignored) {}
            if (output != null) try { output.close(); } catch (Throwable ignored) {}
            if (pending != null && pending.exists()) pending.delete();
        }
    }

    static void executeMusic(final Context context,
                             final HeartbeatToolProtocol.ToolCall call,
                             final StatusCallback callback) {
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                Status result;
                try {
                    if ("local".equals(call.targetId)) {
                        result = controlLocalAudio(context, call);
                    } else if ("search".equals(call.mode)) {
                        result = searchAndPlayMusic(context, call.targetId, call.instruction);
                    } else {
                        int code = musicKey(call.mode);
                        if (code == 0) {
                            result = new Status(false, UiLanguage.text(context,
                                    "不支持的音乐操作", "Unsupported music action"));
                        } else {
                            AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
                            if (audio == null) throw new IllegalStateException("AudioManager unavailable");
                            long now = SystemClock.uptimeMillis();
                            audio.dispatchMediaKeyEvent(new KeyEvent(now, now,
                                    KeyEvent.ACTION_DOWN, code, 0));
                            audio.dispatchMediaKeyEvent(new KeyEvent(now, now,
                                    KeyEvent.ACTION_UP, code, 0));
                            result = new Status(true, UiLanguage.text(context,
                                    "已发送媒体控制指令", "Media control command sent"));
                        }
                    }
                } catch (Throwable error) {
                    result = new Status(false, error.getClass().getSimpleName()
                            + ": " + String.valueOf(error.getMessage()));
                }
                final Status delivered = result;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override public void run() {
                        if (callback != null) callback.onStatus(delivered);
                    }
                });
            }
        });
    }

    private static Status controlLocalAudio(
            Context context, HeartbeatToolProtocol.ToolCall call) {
        if (context == null) return new Status(false, "Context unavailable");
        String path = call.path;
        AgentToolConfig.Snapshot settings = AgentToolConfig.load();
        if ("play".equals(call.mode) && path.length() > 0
                && AgentToolConfig.BACKEND_ROOT.equals(settings.backend)) {
            String lower = path.toLowerCase(Locale.US);
            int dot = lower.lastIndexOf('.');
            String suffix = dot >= 0 && lower.substring(dot + 1).matches("[a-z0-9]{1,8}")
                    ? lower.substring(dot) : ".audio";
            String componentPackage = RuntimeEmbeddingMode.componentPackage("com.dsmod.probe");
            String appDataRoot = "/data/user/0/" + componentPackage;
            String staged = appDataRoot + "/files/agent_audio/current" + suffix;
            String command = "/system/bin/mkdir -p " + shellQuote(appDataRoot + "/files/agent_audio")
                    + " && /system/bin/cp " + shellQuote(path) + " " + shellQuote(staged)
                    + " && owner=$(/system/bin/stat -c %u:%g " + shellQuote(appDataRoot) + ")"
                    + " && /system/bin/chown \"$owner\" " + shellQuote(staged)
                    + " && /system/bin/chmod 600 " + shellQuote(staged);
            CommandResult copy = runCommand(context, AgentToolConfig.BACKEND_ROOT,
                    command, 30_000L, false, 4096);
            if (copy.exitCode != 0) {
                return new Status(false, "Could not stage local audio: "
                        + combinedCommandOutput(copy));
            }
            path = staged;
        }
        String requestId = "audio_" + Long.toHexString(System.nanoTime());
        final String playbackPath = path;
        try {
            final CountDownLatch latch = new CountDownLatch(1);
            final boolean[] succeeded = new boolean[]{false};
            final String[] detail = new String[]{"Local audio bridge returned no result"};
            ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
                @Override protected void onReceiveResult(int code, Bundle data) {
                    succeeded[0] = data != null && data.getBoolean("success", false);
                    detail[0] = data == null ? "Local audio bridge returned no data"
                            : data.getString("detail", "Local audio request completed");
                    latch.countDown();
                }
            };
            final Intent bridge = new Intent();
            String componentPackage = RuntimeEmbeddingMode.componentPackage("com.dsmod.probe");
            bridge.setClassName(componentPackage,
                    componentPackage + ".LocalAudioControlActivity");
            bridge.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_NO_ANIMATION
                    | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            bridge.putExtra(LocalAudioPlaybackService.EXTRA_TOKEN,
                    LocalAudioPlaybackService.CONTROL_TOKEN);
            bridge.putExtra(LocalAudioPlaybackService.EXTRA_REQUEST_ID, requestId);
            bridge.putExtra(LocalAudioPlaybackService.EXTRA_ACTION, call.mode);
            bridge.putExtra(LocalAudioPlaybackService.EXTRA_PATH, playbackPath);
            bridge.putExtra(LocalAudioControlActivity.EXTRA_RECEIVER, receiver);
            final Context launchContext = context;
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override public void run() {
                    try { launchContext.startActivity(bridge); }
                    catch (Throwable error) {
                        detail[0] = error.getClass().getSimpleName() + ": "
                                + String.valueOf(error.getMessage());
                        latch.countDown();
                    }
                }
            });
            if (!latch.await(8L, TimeUnit.SECONDS)) {
                return new Status(false, "Timed out waiting for local audio player");
            }
            return new Status(succeeded[0], detail[0]);
        } catch (Throwable error) {
            return new Status(false, error.getClass().getSimpleName() + ": "
                    + String.valueOf(error.getMessage()));
        }
    }

    private static Status searchAndPlayMusic(Context context, String requested, String query) {
        String provider = requested == null ? "auto" : requested;
        boolean neteaseInstalled = installed(context, "com.netease.cloudmusic");
        if ("qq".equals(provider) || "auto".equals(provider)) {
            try {
                MusicMatch match = searchQqMusic(query);
                if (match != null && launchQqMusic(context, match.songMid, query)) {
                    String display = match.title;
                    if (match.artist.length() > 0) display += " - " + match.artist;
                    return new Status(true, UiLanguage.text(context,
                            "已通过 QQ 音乐开始播放：" + display,
                            "Started playback with QQ Music: " + display));
                }
            } catch (Throwable ignored) {
                // An explicit package intent does not depend on Android package visibility.
            }
            return new Status(false, UiLanguage.text(context,
                    "QQ 音乐未能接收直接播放请求：" + query,
                    "QQ Music could not accept the direct playback request: " + query));
        }
        String packageName;
        String url;
        if ("netease".equals(provider) && neteaseInstalled) {
            packageName = "com.netease.cloudmusic";
            url = "https://music.163.com/#/search/m/?s=" + Uri.encode(query);
        } else {
            packageName = "com.tencent.qqmusic";
            url = "https://y.qq.com/n/ryqq/search?w=" + Uri.encode(query);
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.setPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (Throwable first) {
            return new Status(false, UiLanguage.text(context,
                    "无法在音乐客户端中播放或搜索：" + query,
                    "Could not play or search in the music app: " + query));
        }
        String providerName = "com.tencent.qqmusic".equals(packageName)
                ? "QQ 音乐" : "网易云音乐";
        return new Status(true, UiLanguage.text(context,
                "未能自动播放，已打开" + providerName + "搜索：" + query,
                "Automatic playback was unavailable; opened "
                        + providerName + " search for: " + query));
    }

    private static MusicMatch searchQqMusic(String query) throws Exception {
        String endpoint = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
                + "?p=1&n=1&format=json&w=" + Uri.encode(query);
        HttpURLConnection connection = null;
        InputStream input = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 Android");
            if (connection.getResponseCode() / 100 != 2) return null;
            input = connection.getInputStream();
            byte[] payload = readLimited(input, 256 * 1024);
            JSONObject root = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            JSONObject data = root.optJSONObject("data");
            JSONObject songData = data == null ? null : data.optJSONObject("song");
            JSONArray list = songData == null ? null : songData.optJSONArray("list");
            if (list == null || list.length() == 0) return null;
            JSONObject song = list.optJSONObject(0);
            if (song == null) return null;
            String mid = song.optString("songmid", "").trim();
            if (!mid.matches("[A-Za-z0-9]{6,32}")) return null;
            String artist = "";
            JSONArray singers = song.optJSONArray("singer");
            if (singers != null && singers.length() > 0) {
                JSONObject singer = singers.optJSONObject(0);
                if (singer != null) artist = singer.optString("name", "").trim();
            }
            return new MusicMatch(mid, song.optString("songname", query).trim(), artist);
        } finally {
            if (input != null) try { input.close(); } catch (Throwable ignored) {}
            if (connection != null) connection.disconnect();
        }
    }

    private static byte[] readLimited(InputStream input, int limit) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 8192));
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            if (count == 0) continue;
            if (output.size() + count > limit) throw new IOException("response too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static boolean launchQqMusic(
            Context context, String songMid, String searchQuery) {
        try {
            JSONObject song = new JSONObject();
            song.put("type", "0");
            song.put("songid", "");
            song.put("songmid", songMid);
            JSONArray songs = new JSONArray();
            songs.put(song);
            JSONObject request = new JSONObject();
            request.put("song", songs);
            request.put("action", "play");
            Uri uri = Uri.parse("qqmusic://qq.com/media/playSonglist?p="
                    + Uri.encode(request.toString())
                    + "&source=deekseep_agent");
            AgentToolConfig.Snapshot settings = AgentToolConfig.load();
            if (AgentToolConfig.BACKEND_ROOT.equals(settings.backend)) {
                // QQ Music 20.7 exposes a Binder service, but its package whitelist rejects a
                // third-party module UID. MediaSession.playFromSearch is also ignored by this
                // release. Its documented song-list deep link does accept songmid reliably, so
                // use it under Root, immediately restore DeepSeek, and verify QQ's own session.
                String command = "/system/bin/am start --user 0 --activity-no-animation"
                        + " -a android.intent.action.VIEW -d " + shellQuote(uri.toString())
                        + " -p com.tencent.qqmusic >/dev/null 2>&1 || exit $?; "
                        + "/system/bin/sleep 1; "
                        + "/system/bin/am start --user 0 --activity-no-animation -n "
                        + "com.deepseek.chat/com.deepseek.chat.MainActivity"
                        + " >/dev/null 2>&1 || true; /system/bin/sleep 1; "
                        + "/system/bin/dumpsys media_session"
                        + " | /system/bin/sed -n '/QQMusicMediaSession/,/Sessions Stack/p'"
                        + " | /system/bin/grep -q 'state=PLAYING(3)'"
                        + " && echo success=true || echo success=false";
                CommandResult result = runCommand(context,
                        AgentToolConfig.BACKEND_ROOT, command,
                        8000L, false, 4096);
                return result.exitCode == 0
                        && result.output.contains("success=true");
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setPackage("com.tencent.qqmusic");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class MusicMatch {
        final String songMid;
        final String title;
        final String artist;

        MusicMatch(String songMid, String title, String artist) {
            this.songMid = songMid;
            this.title = title == null || title.length() == 0 ? songMid : title;
            this.artist = artist == null ? "" : artist;
        }
    }

    private static boolean installed(Context context, String packageName) {
        try { context.getPackageManager().getPackageInfo(packageName, 0); return true; }
        catch (Throwable ignored) { return false; }
    }

    private static int musicKey(String action) {
        if ("play".equals(action)) return KeyEvent.KEYCODE_MEDIA_PLAY;
        if ("pause".equals(action)) return KeyEvent.KEYCODE_MEDIA_PAUSE;
        if ("toggle".equals(action)) return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
        if ("next".equals(action)) return KeyEvent.KEYCODE_MEDIA_NEXT;
        if ("previous".equals(action)) return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
        if ("stop".equals(action)) return KeyEvent.KEYCODE_MEDIA_STOP;
        return 0;
    }

    private static ToolResult readFile(
            Context context, String backend,
            HeartbeatToolProtocol.ToolCall call) {
        if (AgentToolConfig.BACKEND_IN_APP.equals(backend)) {
            FileInputStream input = null;
            try {
                input = new FileInputStream(call.path);
                if (!skipFully(input, call.offset)) {
                    return new ToolResult(true, 0, "",
                            UiLanguage.text(context,
                                    "已到达文件末尾", "Reached end of file"),
                            "utf-8", false);
                }
                byte[] buffer = new byte[call.maxBytes + 1];
                int size = 0;
                while (size < buffer.length) {
                    int count = input.read(buffer, size, buffer.length - size);
                    if (count < 0) break;
                    if (count > 0) size += count;
                }
                boolean truncated = size > call.maxBytes;
                int kept = Math.min(size, call.maxBytes);
                return decodedFileResult(
                        Arrays.copyOf(buffer, kept), call.path, truncated);
            } catch (Throwable error) {
                return failureResult(context, backend, error);
            } finally {
                if (input != null) try { input.close(); } catch (Throwable ignored) {}
            }
        }

        String command = "/system/bin/dd if=" + shellQuote(call.path)
                + " bs=1 skip=" + call.offset
                + " count=" + (call.maxBytes + 1);
        CommandResult result = runCommandWithShizukuRecovery(
                context, backend, command, 12000L, true,
                call.maxBytes + 1);
        if (result.exitCode != 0) {
            return commandFailure(context, backend, result);
        }
        byte[] bytes = result.binary == null ? new byte[0] : result.binary;
        boolean truncated = bytes.length > call.maxBytes || result.truncated;
        if (bytes.length > call.maxBytes) {
            bytes = Arrays.copyOf(bytes, call.maxBytes);
        }
        return decodedFileResult(bytes, call.path, truncated);
    }

    private static boolean skipFully(InputStream input, long count)
            throws IOException {
        long remaining = Math.max(0L, count);
        byte[] discard = new byte[8192];
        while (remaining > 0L) {
            long skipped = input.skip(remaining);
            if (skipped > 0L) {
                remaining -= skipped;
                continue;
            }
            int read = input.read(
                    discard, 0, (int) Math.min(discard.length, remaining));
            if (read < 0) return false;
            remaining -= read;
        }
        return true;
    }

    private static ToolResult decodedFileResult(
            byte[] bytes, String path, boolean truncated) {
        if (HostCompat.isV236()) {
            return decodedFileResultV236(bytes, path, truncated);
        }
        if (HostCompat.isV241()) {
            return decodedFileResultV241(bytes, path, truncated);
        }
        return decodedFileResultLegacy(bytes, path, truncated);
    }

    private static ToolResult decodedFileResultV236(
            byte[] bytes, String path, boolean truncated) {
        return decodedChineseTextResult(bytes, path, truncated);
    }

    private static ToolResult decodedFileResultV241(
            byte[] bytes, String path, boolean truncated) {
        return decodedChineseTextResult(bytes, path, truncated);
    }

    /** Preserves the pre-code249 byte-for-byte decoding behavior for older host branches. */
    private static ToolResult decodedFileResultLegacy(
            byte[] bytes, String path, boolean truncated) {
        byte[] safe = bytes == null ? new byte[0] : bytes;
        String encoding = "utf-8";
        String output;
        try {
            output = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(safe)).toString();
            if (output.indexOf('\u0000') >= 0) {
                throw new CharacterCodingException();
            }
        } catch (CharacterCodingException binary) {
            output = Base64.encodeToString(safe, Base64.NO_WRAP);
            encoding = "base64";
        }
        return new ToolResult(true, 0, output,
                "read " + safe.length + " bytes from " + path,
                encoding, truncated);
    }

    /**
     * Text reads are byte-ranged, so a page can begin or end inside a three-byte Chinese UTF-8
     * character. The former strict decoder treated that entire page as binary. For the two
     * supported hosts, discard only incomplete edge bytes, then try common Chinese text encodings
     * before falling back to the unchanged Base64 result.
     */
    private static ToolResult decodedChineseTextResult(
            byte[] bytes, String path, boolean truncated) {
        byte[] safe = bytes == null ? new byte[0] : bytes;
        DecodedText decoded = decodeUtf8Page(safe);
        if (decoded == null) decoded = decodeBomUnicodeText(safe);
        if (decoded == null) decoded = decodeChineseLegacyText(safe);
        if (decoded == null) return decodedFileResultLegacy(safe, path, truncated);
        return new ToolResult(true, 0, decoded.text,
                "read " + safe.length + " bytes from " + path,
                decoded.encoding, truncated);
    }

    private static DecodedText decodeUtf8Page(byte[] value) {
        int start = 0;
        while (start < value.length && start < 3
                && (value[start] & 0xc0) == 0x80) start++;
        for (int removed = 0; removed <= 3; removed++) {
            int end = value.length - removed;
            if (end < start) break;
            try {
                String text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(value, start, end - start)).toString();
                if (text.indexOf('\u0000') < 0) {
                    return new DecodedText(text, "utf-8");
                }
            } catch (CharacterCodingException ignored) {}
        }
        return null;
    }

    private static DecodedText decodeBomUnicodeText(byte[] value) {
        try {
            if (value.length >= 2 && value[0] == (byte) 0xff && value[1] == (byte) 0xfe) {
                return new DecodedText(new String(value, 2, value.length - 2,
                        Charset.forName("UTF-16LE")), "utf-16le");
            }
            if (value.length >= 2 && value[0] == (byte) 0xfe && value[1] == (byte) 0xff) {
                return new DecodedText(new String(value, 2, value.length - 2,
                        Charset.forName("UTF-16BE")), "utf-16be");
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static DecodedText decodeChineseLegacyText(byte[] value) {
        try {
            String text = Charset.forName("GB18030").newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value)).toString();
            if (containsCjk(text) && mostlyReadableText(text)) {
                return new DecodedText(text, "gb18030");
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean containsCjk(String value) {
        for (int index = 0; index < value.length(); index++) {
            char code = value.charAt(index);
            if ((code >= '\u3400' && code <= '\u4dbf')
                    || (code >= '\u4e00' && code <= '\u9fff')) return true;
        }
        return false;
    }

    private static boolean mostlyReadableText(String value) {
        if (value.length() == 0) return true;
        int readable = 0;
        for (int index = 0; index < value.length(); index++) {
            char code = value.charAt(index);
            if (!Character.isISOControl(code) || code == '\n' || code == '\r'
                    || code == '\t') readable++;
        }
        return readable * 100 >= value.length() * 90;
    }

    private static final class DecodedText {
        final String text;
        final String encoding;

        DecodedText(String text, String encoding) {
            this.text = text == null ? "" : text;
            this.encoding = encoding;
        }
    }

    private static ToolResult writeFile(
            Context context, String backend,
            HeartbeatToolProtocol.ToolCall call) {
        byte[] bytes = call.content.getBytes(StandardCharsets.UTF_8);
        if (AgentToolConfig.BACKEND_IN_APP.equals(backend)) {
            FileOutputStream output = null;
            try {
                File target = new File(call.path);
                File parent = target.getParentFile();
                if (call.createParents && parent != null
                        && !parent.isDirectory() && !parent.mkdirs()) {
                    throw new IOException("could not create parent directories");
                }
                output = new FileOutputStream(target, call.append);
                output.write(bytes);
                output.flush();
                return new ToolResult(true, 0, "",
                        (call.append ? "appended " : "wrote ")
                                + bytes.length + " bytes to " + call.path,
                        "utf-8", false);
            } catch (Throwable error) {
                return failureResult(context, backend, error);
            } finally {
                if (output != null) try { output.close(); } catch (Throwable ignored) {}
            }
        }

        StringBuilder command = new StringBuilder(bytes.length * 2 + 256);
        if (call.createParents) {
            File parent = new File(call.path).getParentFile();
            if (parent != null) {
                command.append("/system/bin/mkdir -p ")
                        .append(shellQuote(parent.getAbsolutePath()))
                        .append(" && ");
            }
        }
        String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
        command.append("/system/bin/printf %s ")
                .append(shellQuote(encoded))
                .append(" | /system/bin/base64 -d ")
                .append(call.append ? ">> " : "> ")
                .append(shellQuote(call.path));
        CommandResult result = runCommandWithShizukuRecovery(
                context, backend, command.toString(), 12000L, false);
        if (result.exitCode != 0) {
            return commandFailure(context, backend, result);
        }
        return new ToolResult(true, 0, result.output,
                (call.append ? "appended " : "wrote ")
                        + bytes.length + " bytes to " + call.path,
                "utf-8", result.truncated);
    }

    /** Preserved pre-code249 behavior for every older host branch. */
    private static ToolResult executeShellLegacy(
            Context context, String backend,
            HeartbeatToolProtocol.ToolCall call) {
        String command = "PATH=" + SYSTEM_PATH
                + "; export PATH; " + call.command;
        CommandResult result = runCommandWithShizukuRecovery(
                context, backend, command, call.timeoutMs, false);
        String output = combinedCommandOutput(result);
        String detail = result.exitCode == 0
                ? UiLanguage.text(context,
                "Shell 执行完成", "Shell command completed")
                : friendlyFailure(context, backend, result);
        return new ToolResult(result.exitCode == 0, result.exitCode,
                output, detail, "utf-8", result.truncated);
    }

    /** Exact code249 adapter: distinguish command failure from backend/permission failure. */
    private static ToolResult executeShellV236(
            Context context, String backend,
            HeartbeatToolProtocol.ToolCall call) {
        String command = "PATH=" + SYSTEM_PATH
                + "; export PATH; " + shellLocalePrefixV236(call.command)
                + call.command;
        CommandResult result = runCommandWithShizukuRecovery(
                context, backend, command, call.timeoutMs, false);
        String output = combinedCommandOutput(result);
        String detail = result.exitCode == 0
                ? UiLanguage.text(context,
                "Shell 执行完成", "Shell command completed")
                : shellFailureV236(context, backend, result);
        return new ToolResult(result.exitCode == 0, result.exitCode,
                output, detail, "utf-8", result.truncated);
    }

    /** Exact code257 adapter, verified separately from code249. */
    private static ToolResult executeShellV241(
            Context context, String backend,
            HeartbeatToolProtocol.ToolCall call) {
        File workspace = workspaceEnabledV241() ? workspaceV241(context) : null;
        String requested = call == null ? "" : call.command == null ? "" : call.command.trim();
        if ("termux-setup".equals(requested)
                || "termux-install".equals(requested)) {
            return installTermuxEnvironmentNowV241(context, null);
        }
        if ("termux-status".equals(requested)) {
            boolean ready = termuxEnvironmentInstalledV241(context);
            return new ToolResult(ready, ready ? 0 : 1,
                    termuxEnvironmentStatusV241(context),
                    ready ? "Termux environment is ready"
                            : "Termux environment is not installed",
                    "utf-8", false);
        }
        if (workspace != null && requested.startsWith("install ")) {
            return installWorkspaceToolV241(context, workspace, requested.substring(8).trim());
        }
        CommandResult result;
        if (usesDirectRootShellV241(backend, workspace, requested)) {
            String command = "PATH=" + SYSTEM_PATH + "; export PATH; "
                    + shellLocalePrefixV241(requested) + requested;
            result = runCommand(context, AgentToolConfig.BACKEND_ROOT, command,
                    call.timeoutMs, false, NORMAL_OUTPUT_LIMIT);
        } else if (workspace != null && termuxEnvironmentInstalledV241(context)) {
            long timeout = isTermuxPackageCommandV241(requested)
                    ? Math.max(call.timeoutMs, 20L * 60L * 1000L) : call.timeoutMs;
            result = runCommand(context, AgentToolConfig.BACKEND_ROOT,
                    termuxNamespaceCommandV241(workspace, requested),
                    timeout, false, NORMAL_OUTPUT_LIMIT);
        } else {
            String command = workspace == null
                    ? "PATH=" + SYSTEM_PATH + "; export PATH; "
                    + shellLocalePrefixV241(requested) + requested
                    : workspaceCommandV241(workspace, requested);
            result = runCommandWithShizukuRecovery(
                    context, backend, command, call.timeoutMs, false);
        }
        String output = combinedCommandOutput(result);
        String detail = result.exitCode == 0
                ? UiLanguage.text(context,
                "Shell 执行完成", "Shell command completed")
                : shellFailureV241(context, backend, result);
        return new ToolResult(result.exitCode == 0, result.exitCode,
                output, detail, "utf-8", result.truncated);
    }

    /**
     * A Termux namespace intentionally drops to the app UID so package installs stay owned by
     * the workspace.  A fully authorized Root request that explicitly targets an external
     * directory must not take that path, otherwise shell redirection silently loses Root.
     */
    private static boolean usesDirectRootShellV241(String backend, File workspace, String command) {
        if (!AgentToolConfig.BACKEND_ROOT.equals(backend) || workspace == null
                || !AgentToolConfig.PERMISSION_ALL.equals(AgentToolConfig.load().permission)
                || !workspacePolicyV241().allowAll) return false;
        String value = command == null ? "" : command;
        return value.contains("/storage/") || value.contains("/sdcard/")
                || value.contains("/data/") || value.contains("/mnt/");
    }

    private static String shellLocalePrefixV236(String command) {
        return needsByteStableChineseSearch(command)
                ? "LC_ALL=C; export LC_ALL; " : "";
    }

    private static String shellLocalePrefixV241(String command) {
        return needsByteStableChineseSearch(command)
                ? "LC_ALL=C; export LC_ALL; " : "";
    }

    private static boolean needsByteStableChineseSearch(String command) {
        if (command == null || command.indexOf("grep") < 0) return false;
        for (int index = 0; index < command.length(); index++) {
            if (command.charAt(index) > 0x7f) return true;
        }
        return false;
    }

    private static String shellFailureV236(
            Context context, String backend, CommandResult result) {
        return detailedShellFailure(context, backend, result, "code249");
    }

    private static String shellFailureV241(
            Context context, String backend, CommandResult result) {
        return detailedShellFailure(context, backend, result, "code257");
    }

    private static String detailedShellFailure(
            Context context, String backend, CommandResult result, String adapter) {
        int exitCode = result == null ? -1 : result.exitCode;
        String raw = combinedCommandOutput(result).trim();
        String evidence = raw.length() > 600 ? raw.substring(0, 600) + "…" : raw;
        String backendName = AgentToolConfig.BACKEND_ROOT.equals(backend) ? "Root"
                : AgentToolConfig.BACKEND_SHIZUKU.equals(backend) ? "Shizuku"
                : AgentToolConfig.BACKEND_IN_APP.equals(backend) ? "应用内" : "未知后端";

        if (exitCode == -2) {
            return UiLanguage.text(context,
                    "Shell 命令执行超时；后端=" + backendName + "，适配=" + adapter,
                    "Shell command timed out; backend=" + backendName
                            + ", adapter=" + adapter);
        }
        if (exitCode == 126) {
            return UiLanguage.text(context,
                    "Shell 已启动，但目标命令无执行权限（exit 126）；后端=" + backendName,
                    "Shell started, but the target command is not executable (exit 126); backend="
                            + backendName) + (evidence.length() == 0 ? "" : "：" + evidence);
        }
        if (exitCode == 127) {
            return UiLanguage.text(context,
                    "Shell 已启动，但命令不存在或不在 Android 系统 PATH 中（exit 127）；后端="
                            + backendName,
                    "Shell started, but the command was not found in the Android system PATH "
                            + "(exit 127); backend=" + backendName)
                    + (evidence.length() == 0 ? "" : "：" + evidence);
        }
        if (exitCode == 1 && evidence.length() == 0) {
            return UiLanguage.text(context,
                    "Shell 与 " + backendName + " 权限均已正常建立，但命令自身返回 exit 1，"
                            + "且没有输出错误；通常表示 grep 未匹配、文件/区域不存在或判断条件为假。",
                    "Shell and " + backendName + " authorization were established, but the "
                            + "command itself returned exit 1 without an error message; this "
                            + "usually means no grep match, a missing file/region, or a false condition.");
        }
        if (evidence.length() > 0) {
            return UiLanguage.text(context,
                    "Shell 命令已运行但失败（exit " + exitCode + "，后端=" + backendName + "）：",
                    "Shell command ran but failed (exit " + exitCode
                            + ", backend=" + backendName + "): ") + evidence;
        }
        return UiLanguage.text(context,
                "Shell 后端未返回诊断信息（exit " + exitCode + "，后端=" + backendName
                        + "，适配=" + adapter + "）",
                "Shell backend returned no diagnostics (exit " + exitCode
                        + ", backend=" + backendName + ", adapter=" + adapter + ")");
    }

    private static ToolResult executeNetworkRequest(
            Context context, String backend,
            HeartbeatToolProtocol.ToolCall call) {
        CommandResult result = runCommandWithShizukuRecovery(
                context, backend, networkCurlCommand(call),
                call.timeoutMs + 1500L, false);
        String output = combinedCommandOutput(result);
        String detail = result.exitCode == 0
                ? UiLanguage.text(context,
                "网络请求已完成", "Network request completed")
                : friendlyFailure(context, backend, result);
        return new ToolResult(result.exitCode == 0, result.exitCode,
                output, detail, "utf-8", result.truncated);
    }

    /** Builds an argv-safe curl invocation from the already validated structured call. */
    static String networkCurlCommand(HeartbeatToolProtocol.ToolCall call) {
        int seconds = Math.max(1, Math.min(30, call.timeoutMs / 1000));
        StringBuilder command = new StringBuilder(512);
        command.append("PATH=").append(SYSTEM_PATH).append("; export PATH; ")
                .append("curl --silent --show-error --location ")
                .append("--max-time ").append(seconds).append(' ')
                .append("--connect-timeout ").append(Math.min(10, seconds)).append(' ')
                .append("--request ").append(shellQuote(call.mode)).append(' ')
                .append("--dump-header - ");
        if (call.instruction.length() > 0) {
            for (String header : call.instruction.split("\\n", -1)) {
                if (header.length() > 0) {
                    command.append("--header ").append(shellQuote(header)).append(' ');
                }
            }
        }
        if (call.content.length() > 0) {
            command.append("--data-binary ").append(shellQuote(call.content)).append(' ');
        }
        command.append("--write-out ")
                .append(shellQuote("\\n[deekseep_http_status] %{http_code}\\n"))
                .append(' ').append(shellQuote(call.path));
        return command.toString();
    }

    private static ToolResult failureResult(
            Context context, String backend, Throwable error) {
        String detail = error == null ? "" : error.getClass().getSimpleName()
                + ": " + String.valueOf(error.getMessage());
        return new ToolResult(false, -1, "",
                friendlyFailure(context, backend,
                        new CommandResult(-1, "", detail, null, false)),
                "utf-8", false);
    }

    private static ToolResult commandFailure(
            Context context, String backend, CommandResult result) {
        return new ToolResult(false,
                result == null ? -1 : result.exitCode,
                combinedCommandOutput(result),
                friendlyFailure(context, backend, result),
                "utf-8", result != null && result.truncated);
    }

    private static String combinedCommandOutput(CommandResult result) {
        if (result == null) return "";
        if (result.output.length() == 0) return result.error;
        if (result.error.length() == 0) return result.output;
        return result.output + "\n[stderr]\n" + result.error;
    }

    private static String rootManagerName(String output) {
        String value = output == null ? "" : output;
        if (value.contains("__DEEKSEEP_ROOT__=SukiSU_Ultra")) return "SukiSU Ultra";
        if (value.contains("__DEEKSEEP_ROOT__=Magisk_Alpha")) return "Magisk Alpha/Kitsune";
        if (value.contains("__DEEKSEEP_ROOT__=KernelSU")) return "KernelSU";
        if (value.contains("__DEEKSEEP_ROOT__=APatch")) return "APatch";
        if (value.contains("__DEEKSEEP_ROOT__=Magisk")) return "Magisk";
        return "Root";
    }

    static String shellQuote(String value) {
        String safe = value == null ? "" : value;
        return "'" + safe.replace("'", "'\\''") + "'";
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
        if (HeartbeatToolProtocol.TOOL_OPEN_APP.equals(call.tool)) {
            return "monkey -p " + shellQuote(call.targetId)
                    + " -c android.intent.category.LAUNCHER 1";
        }
        if (HeartbeatToolProtocol.TOOL_SCREEN_POWER.equals(call.tool)) {
            return "input keyevent " + ("sleep".equals(call.mode) ? "223" : "224");
        }
        return "";
    }

    /** Best-effort OEM-independent background allow-list setup after real authorization. */
    private static boolean allowDeepSeekBackground(Context context, String backend) {
        String packageName = "com.deepseek.chat";
        String command = "cmd deviceidle whitelist +" + packageName
                + " >/dev/null 2>&1; allow=$?; "
                + "cmd appops set " + packageName
                + " RUN_IN_BACKGROUND allow >/dev/null 2>&1 || true; "
                + "cmd appops set " + packageName
                + " RUN_ANY_IN_BACKGROUND allow >/dev/null 2>&1 || true; "
                + "am set-inactive " + packageName
                + " false >/dev/null 2>&1 || true; exit $allow";
        CommandResult result = runCommandWithShizukuRecovery(
                context, backend, command, 8000L, false);
        return result.exitCode == 0;
    }

    private static int normalized(int value, int size) {
        int safe = Math.max(0, Math.min(1000, value));
        return Math.round((safe / 1000.0f) * Math.max(0, size - 1));
    }

    private static Status captureScreen(Context context, String backend) {
        File target = HostCompat.isV241() && context != null
                ? new File(new File(context.getFilesDir(), "deekseep_agent"),
                        "latest_screen.png")
                : new File(SCREENSHOT_FILE);
        File temporary = new File(target.getAbsolutePath() + ".tmp");
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            return new Status(false, UiLanguage.text(context,
                    "无法创建截图目录", "Could not create the screenshot directory"));
        }
        CommandResult result = runCommandWithShizukuRecovery(
                context, backend, "screencap -p", 10000L, true);
        // Some rish/Shizuku builds forward the remote command's stdout through the local
        // loader's stderr and then exit non-zero after the payload has already arrived.  A
        // structurally complete PNG is the authoritative result; never stringify its pixels as
        // a connection error.
        if (result.truncated || result.binary == null
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
            if (target.isFile() && !target.delete()) {
                throw new IOException("stale screenshot could not be replaced");
            }
            if (!temporary.renameTo(target)) throw new IOException("screenshot rename failed");
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

    private static CommandResult runCommandWithShizukuRecovery(
            Context context, String backend, String command,
            long timeoutMs, boolean binaryOutput) {
        return runCommandWithShizukuRecovery(
                context, backend, command, timeoutMs, binaryOutput,
                binaryOutput ? SCREENSHOT_OUTPUT_LIMIT : NORMAL_OUTPUT_LIMIT);
    }

    private static CommandResult runCommandWithShizukuRecovery(
            Context context, String backend, String command,
            long timeoutMs, boolean binaryOutput, int outputLimit) {
        CommandResult first = runCommand(
                context, backend, command, timeoutMs, binaryOutput, outputLimit);
        if (!AgentToolConfig.BACKEND_SHIZUKU.equals(backend)
                || !shizukuServerIsStopped(first)) {
            return first;
        }
        CommandResult started = runCommand(
                context, AgentToolConfig.BACKEND_ROOT,
                SHIZUKU_START_COMMAND, 12000L, false, NORMAL_OUTPUT_LIMIT);
        if (started.exitCode != 0) {
            String startFailure = combinedCommandOutput(started);
            return new CommandResult(first.exitCode, first.output,
                    first.error + (startFailure.length() == 0 ? ""
                            : "\nRoot auto-start failed: " + startFailure),
                    first.binary, first.truncated || started.truncated);
        }
        /*
         * The native starter exits after spawning the server, before its binder
         * service is necessarily published. A single short sleep was racy on
         * slower devices: the first rish retry could still report that the
         * server was not running. Retry only that transient state and keep the
         * total readiness window bounded.
         */
        CommandResult retried = first;
        long[] readinessWaits = { 1500L, 900L, 1200L };
        for (long readinessWait : readinessWaits) {
            SystemClock.sleep(readinessWait);
            retried = runCommand(
                    context, backend, command, timeoutMs,
                    binaryOutput, outputLimit);
            if (!shizukuServerIsStopped(retried)) {
                return retried;
            }
        }
        return retried;
    }

    private static boolean shizukuServerIsStopped(CommandResult result) {
        String detail = combinedCommandOutput(result);
        return detail.contains("Server is not running")
                || detail.contains("Shizuku service not running");
    }

    private static CommandResult runCommand(
            Context context, String backend, String command,
            long timeoutMs, boolean binaryOutput, int outputLimit) {
        Process process = null;
        InputStream standard = null;
        InputStream error = null;
        try {
            ProcessBuilder builder = null;
            if (AgentToolConfig.BACKEND_ROOT.equals(backend)) {
                process = startRootProcess(command);
            } else if (AgentToolConfig.BACKEND_SHIZUKU.equals(backend)) {
                File dex = ensureRishDex();
                if (dex == null) {
                    return new CommandResult(-1,
                            "", "rish_shizuku.dex is unavailable",
                            null, false);
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
            } else if (AgentToolConfig.BACKEND_IN_APP.equals(backend)) {
                builder = new ProcessBuilder(
                        "/system/bin/sh", "-c", command);
                if (context != null && context.getFilesDir() != null
                        && context.getFilesDir().isDirectory()) {
                    builder.directory(context.getFilesDir());
                }
            } else {
                return new CommandResult(
                        -1, "", "unknown execution backend", null, false);
            }
            if (process == null) {
                Map<String, String> processEnvironment = builder.environment();
                processEnvironment.put("PATH", SYSTEM_PATH);
                process = builder.start();
            }
            standard = process.getInputStream();
            error = process.getErrorStream();
            int limit = Math.max(1024, outputLimit);
            ByteArrayOutputStream standardBytes =
                    new ByteArrayOutputStream(Math.min(limit, 8192));
            ByteArrayOutputStream errorBytes =
                    new ByteArrayOutputStream(Math.min(NORMAL_OUTPUT_LIMIT, 8192));
            StreamCollector standardCollector =
                    new StreamCollector(standard, standardBytes, limit);
            StreamCollector errorCollector = new StreamCollector(
                    error, errorBytes, NORMAL_OUTPUT_LIMIT);
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
                return new CommandResult(
                        -2, "", "command timed out", null,
                        standardCollector.truncated || errorCollector.truncated);
            }
            outThread.join(1000L);
            errThread.join(1000L);
            byte[] stdout = standardBytes.toByteArray();
            byte[] stderr = errorBytes.toByteArray();
            byte[] binary = binaryOutput
                    ? extractPngPayload(stdout, stderr) : null;
            String text = binaryOutput ? ""
                    : new String(stdout, StandardCharsets.UTF_8);
            String errorText = binaryOutput && binary != null ? ""
                    : new String(stderr, StandardCharsets.UTF_8);
            return new CommandResult(exit.intValue(), text, errorText,
                    binary,
                    standardCollector.truncated || errorCollector.truncated);
        } catch (Throwable errorValue) {
            return new CommandResult(-1, "",
                    errorValue.getClass().getSimpleName() + ": "
                            + String.valueOf(errorValue.getMessage()),
                    null, false);
        } finally {
            if (standard != null) try { standard.close(); } catch (Throwable ignored) {}
            if (error != null) try { error.close(); } catch (Throwable ignored) {}
            if (process != null) try { process.destroy(); } catch (Throwable ignored) {}
        }
    }

    private static Process startRootProcess(String command) throws IOException {
        IOException last = null;
        StringBuilder failures = new StringBuilder();
        for (String binary : ROOT_BINARIES) {
            try {
                ProcessBuilder builder = new ProcessBuilder(binary, "-c", command);
                builder.environment().put("PATH", SYSTEM_PATH);
                return builder.start();
            } catch (IOException error) {
                last = error;
                if (failures.length() > 0) failures.append("; ");
                failures.append(binary).append('=')
                        .append(error.getClass().getSimpleName());
            }
        }
        try {
            ProcessBuilder builder = new ProcessBuilder("su", "-c", command);
            builder.environment().put("PATH", SYSTEM_PATH);
            return builder.start();
        } catch (IOException error) {
            if (last != null) error.addSuppressed(last);
            throw new IOException("no executable su (" + failures + ")", error);
        }
    }

    private static String diagnosticOutput(String value) {
        String text = value == null ? ""
                : value.replace('\r', ' ').replace('\n', ' ').trim();
        if (text.length() > 300) text = text.substring(0, 300) + "…";
        return text.length() == 0 ? "<empty>" : text;
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
            if (input == null && loader != null) {
                // Older universal builds used the dot-prefixed resource name directly.
                input = loader.getResourceAsStream(RISH_LEGACY_RESOURCE);
            }
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

    /** Selects and bounds a real PNG transported on either rish output channel. */
    static byte[] extractPngPayloadForTest(byte[] stdout, byte[] stderr) {
        return extractPngPayload(stdout, stderr);
    }

    private static byte[] extractPngPayload(byte[] first, byte[] second) {
        byte[] png = extractSinglePng(first);
        return png != null ? png : extractSinglePng(second);
    }

    private static byte[] extractSinglePng(byte[] source) {
        if (source == null || source.length < 20) return null;
        final byte[] signature = new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10
        };
        for (int start = 0; start <= source.length - signature.length; start++) {
            boolean matches = true;
            for (int index = 0; index < signature.length; index++) {
                if (source[start + index] != signature[index]) {
                    matches = false;
                    break;
                }
            }
            if (!matches) continue;
            int cursor = start + signature.length;
            boolean sawHeader = false;
            while (cursor <= source.length - 12) {
                long length = ((long) (source[cursor] & 0xff) << 24)
                        | ((long) (source[cursor + 1] & 0xff) << 16)
                        | ((long) (source[cursor + 2] & 0xff) << 8)
                        | (long) (source[cursor + 3] & 0xff);
                if (length > SCREENSHOT_OUTPUT_LIMIT) break;
                long endLong = (long) cursor + 12L + length;
                if (endLong > source.length || endLong > Integer.MAX_VALUE) break;
                int type = cursor + 4;
                boolean header = source[type] == 'I' && source[type + 1] == 'H'
                        && source[type + 2] == 'D' && source[type + 3] == 'R';
                boolean finalChunk = source[type] == 'I' && source[type + 1] == 'E'
                        && source[type + 2] == 'N' && source[type + 3] == 'D';
                if (!sawHeader && !header) break;
                if (header) {
                    if (sawHeader || length != 13L) break;
                    sawHeader = true;
                }
                int end = (int) endLong;
                if (finalChunk && sawHeader && length == 0L) {
                    return Arrays.copyOfRange(source, start, end);
                }
                cursor = end;
            }
        }
        return null;
    }

    private static String friendlyFailure(
            Context context, String backend, CommandResult result) {
        String detail = combinedCommandOutput(result).trim();
        if (detail.length() > 180) detail = detail.substring(0, 180);
        if (AgentToolConfig.BACKEND_SHIZUKU.equals(backend)) {
            if (detail.contains("Server is not running")
                    || detail.contains("binder")) {
                if (detail.contains("Root auto-start failed")
                        && (detail.contains("No such file")
                        || detail.contains("denied")
                        || detail.contains("permission"))) {
                    return UiLanguage.text(context,
                            "Shizuku 服务未运行，且 DeepSeek 尚未获 Root；"
                                    + "已为你准备打开 Shizuku 启动页",
                            "Shizuku is not running and DeepSeek has no Root access; "
                                    + "open Shizuku to start it");
                }
                return UiLanguage.text(context,
                        "Shizuku 服务未运行，Root 自动启动也未成功；请先在 Shizuku 中启动服务",
                        "Shizuku is not running and Root auto-start failed; "
                                + "start it in the Shizuku app");
            }
            if (detail.contains("Request timeout")) {
                return UiLanguage.text(context,
                        "Shizuku 服务已启动，但 rish 连接超时；请关闭 DeepSeek 与 Shizuku "
                                + "的电池优化后重试",
                        "Shizuku is running but rish timed out; disable battery optimization "
                                + "for DeepSeek and Shizuku, then retry");
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
        final String error;
        final byte[] binary;
        final boolean truncated;

        CommandResult(int exitCode, String output, String error,
                      byte[] binary, boolean truncated) {
            this.exitCode = exitCode;
            this.output = output == null ? "" : output;
            this.error = error == null ? "" : error;
            this.binary = binary;
            this.truncated = truncated;
        }
    }

    private static final class StreamCollector implements Runnable {
        final InputStream input;
        final ByteArrayOutputStream output;
        final int limit;
        volatile boolean truncated;

        StreamCollector(
                InputStream input, ByteArrayOutputStream output, int limit) {
            this.input = input;
            this.output = output;
            this.limit = Math.max(0, limit);
        }

        @Override public void run() {
            byte[] buffer = new byte[8192];
            try {
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count <= 0) continue;
                    int remaining = Math.max(0, limit - output.size());
                    if (remaining > 0) {
                        output.write(buffer, 0, Math.min(count, remaining));
                    }
                    if (count > remaining) truncated = true;
                }
            } catch (Throwable ignored) {}
        }
    }
}
