package com.dsmod.probe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns a single free Pinggy HTTP tunnel.
 *
 * <p>Pinggy's documented SSH quickstart accepts an empty password. JSch sends that empty password
 * directly, so this implementation is equivalent to pressing Enter at the terminal prompt without
 * requiring a terminal emulator or an external SSH executable. A free session is deliberately not
 * auto-recreated after it has published a URL: reconnecting would silently change the one-time
 * address the user copied.</p>
 */
final class PinggyTunnelManager {
    static final String PROVIDER = "pinggy";
    static final String JSCH_VERSION = "2.28.2";
    static final long DEFAULT_LIFETIME_MS = 60L * 60L * 1000L;

    private static final String TAG = "DeekseepPinggy";
    private static final String HOST = "free.pinggy.io";
    private static final int PORT = 443;
    private static final String USER = "deekseep";
    private static final String PREFS = "deekseep_pinggy_tunnel";
    private static final String KEY_REQUESTED = "requested";
    private static final String KNOWN_HOSTS = "deekseep_pinggy_known_hosts";
    private static final String LOG_FILE = "deekseep_pinggy.log";
    private static final int MAX_LOG_LINES = 80;
    private static final long MAX_LOG_BYTES = 256L * 1024L;
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?(?::[0-9]{1,5})?");
    private static final Pattern LIFETIME_PATTERN = Pattern.compile(
            "(?:expire|expires)\\s+in\\s+([0-9]{1,4})\\s+minute",
            Pattern.CASE_INSENSITIVE);
    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> recentLogs = new ArrayDeque<String>();
    private static final Set<String> publicUrls = new LinkedHashSet<String>();

    private static volatile Context applicationContext;
    private static volatile Session session;
    private static volatile ChannelShell channel;
    private static volatile Thread worker;
    private static volatile int generation;
    private static volatile boolean gatewayRunning;
    private static volatile int gatewayPort;
    private static volatile String state = "stopped";
    private static volatile String lastError = "";
    private static volatile String primaryRoot = "";
    private static volatile String hostFingerprint = "";
    private static volatile long startedAtWall;
    private static volatile long connectedAtWall;
    private static volatile long expiresAtWall;
    private static volatile int allocatedRemotePort;

    private PinggyTunnelManager() {}

    static Bundle setRequested(Context context, boolean requested) {
        Bundle result = new Bundle();
        if (context == null) {
            result.putBoolean("accepted", false);
            result.putString("error", "模块上下文不可用");
            return result;
        }
        Context app = context.getApplicationContext();
        applicationContext = app;
        synchronized (LOCK) {
            if (!requested) {
                prefs(app).edit().putBoolean(KEY_REQUESTED, false).apply();
                stopLocked("disabled by user", true);
                state = "stopped";
                lastError = "";
            } else {
                boolean alreadyRequested = prefs(app).getBoolean(KEY_REQUESTED, false);
                if (!alreadyRequested) {
                    clearPublishedStateLocked();
                    lastError = "";
                    state = gatewayRunning ? "connecting" : "waiting_local_api";
                    prefs(app).edit().putBoolean(KEY_REQUESTED, true).apply();
                }
            }
        }
        reconcile(app);
        result.putBoolean("accepted", true);
        putStatus(app, result);
        return result;
    }

    static Bundle status(Context context) {
        Bundle result = new Bundle();
        if (context == null) {
            result.putBoolean("accepted", false);
            result.putString("error", "模块上下文不可用");
            return result;
        }
        Context app = context.getApplicationContext();
        applicationContext = app;
        synchronized (LOCK) {
            expireIfNeededLocked(app);
        }
        result.putBoolean("accepted", true);
        putStatus(app, result);
        return result;
    }

    static void onGatewayState(Context context, boolean localApiEnabled,
                               boolean running, int port) {
        if (context == null) return;
        Context app = context.getApplicationContext();
        applicationContext = app;
        synchronized (LOCK) {
            gatewayRunning = localApiEnabled && running && port > 0;
            gatewayPort = port > 0 ? port : 0;
            expireIfNeededLocked(app);
        }
        reconcile(app);
    }

    static void shutdown(Context context) {
        synchronized (LOCK) {
            Context app = context == null ? applicationContext : context.getApplicationContext();
            gatewayRunning = false;
            gatewayPort = 0;
            if (app != null) prefs(app).edit().putBoolean(KEY_REQUESTED, false).apply();
            stopLocked("foreground service stopped", true);
            state = "stopped";
        }
    }

    static boolean isConnected() {
        synchronized (LOCK) {
            return "connected".equals(state) && session != null && session.isConnected()
                    && expiresAtWall > System.currentTimeMillis();
        }
    }

    private static void reconcile(Context app) {
        synchronized (LOCK) {
            expireIfNeededLocked(app);
            boolean requested = prefs(app).getBoolean(KEY_REQUESTED, false);
            if (!requested) {
                if (!"expired".equals(state) && !"error".equals(state)) state = "stopped";
                return;
            }
            if (!gatewayRunning || gatewayPort <= 0) {
                if (session != null || channel != null || worker != null) {
                    stopLocked("local API unavailable", true);
                }
                state = "waiting_local_api";
                return;
            }
            if (session != null || worker != null) return;
            startWorkerLocked(app, gatewayPort);
        }
    }

    private static void startWorkerLocked(final Context app, final int localPort) {
        final int runGeneration = ++generation;
        state = "connecting";
        lastError = "";
        startedAtWall = System.currentTimeMillis();
        connectedAtWall = 0L;
        expiresAtWall = 0L;
        allocatedRemotePort = 0;
        primaryRoot = "";
        publicUrls.clear();
        appendLog(app, "START host=" + HOST + ":" + PORT
                + " origin=http://127.0.0.1:" + localPort
                + " auth=empty-password");

        Thread launched = new Thread(new Runnable() {
            @Override public void run() {
                runSession(app, localPort, runGeneration);
            }
        }, "Deekseep-Pinggy");
        launched.setDaemon(true);
        worker = launched;
        launched.start();
    }

    private static void runSession(Context app, int localPort, int runGeneration) {
        Session openedSession = null;
        ChannelShell openedChannel = null;
        boolean published = false;
        Throwable failure = null;
        try {
            JSch jsch = new JSch();
            File knownHosts = knownHostsFile(app);
            ensurePrivateFile(knownHosts);
            jsch.setKnownHosts(knownHosts.getAbsolutePath());

            openedSession = jsch.getSession(USER, HOST, PORT);
            openedSession.setConfig("StrictHostKeyChecking", "ask");
            openedSession.setConfig("HashKnownHosts", "no");
            openedSession.setConfig("PreferredAuthentications", "password");
            openedSession.setConfig("MaxAuthTries", "1");
            openedSession.setPassword("");
            openedSession.setUserInfo(new EmptyPasswordUserInfo(app, runGeneration));
            openedSession.setServerAliveInterval(30_000);
            openedSession.setServerAliveCountMax(2);

            synchronized (LOCK) {
                if (runGeneration != generation
                        || !prefs(app).getBoolean(KEY_REQUESTED, false)) return;
                session = openedSession;
            }
            openedSession.connect(20_000);
            synchronized (LOCK) {
                if (runGeneration != generation
                        || !prefs(app).getBoolean(KEY_REQUESTED, false)) return;
                try {
                    hostFingerprint = openedSession.getHostKey().getFingerPrint(jsch);
                } catch (Throwable ignored) {
                    hostFingerprint = "";
                }
            }

            int remotePort = openedSession.setPortForwardingR(
                    "0:127.0.0.1:" + localPort);
            openedChannel = (ChannelShell) openedSession.openChannel("shell");
            openedChannel.setPty(false);
            BufferedReader input = new BufferedReader(new InputStreamReader(
                    openedChannel.getInputStream(), StandardCharsets.UTF_8));
            synchronized (LOCK) {
                if (runGeneration != generation
                        || !prefs(app).getBoolean(KEY_REQUESTED, false)) return;
                channel = openedChannel;
                allocatedRemotePort = remotePort;
            }
            openedChannel.connect(12_000);

            String line;
            while ((line = input.readLine()) != null) {
                handleLine(app, runGeneration, line);
                synchronized (LOCK) {
                    if (runGeneration != generation) break;
                    published = connectedAtWall > 0L && primaryRoot.length() > 0;
                }
            }
        } catch (Throwable t) {
            failure = t;
        } finally {
            try {
                if (openedChannel != null) openedChannel.disconnect();
            } catch (Throwable ignored) {}
            try {
                if (openedSession != null) openedSession.disconnect();
            } catch (Throwable ignored) {}
            finishWorker(app, runGeneration, published, failure);
        }
    }

    private static void handleLine(Context app, int runGeneration, String raw) {
        String line = sanitize(raw);
        if (line.length() == 0) return;
        appendLog(app, line);
        synchronized (LOCK) {
            if (runGeneration != generation) return;

            Matcher lifetime = LIFETIME_PATTERN.matcher(line);
            if (lifetime.find()) {
                try {
                    int minutes = Integer.parseInt(lifetime.group(1));
                    if (minutes > 0 && minutes <= 24 * 60) {
                        long base = connectedAtWall > 0L
                                ? connectedAtWall : System.currentTimeMillis();
                        expiresAtWall = base + minutes * 60_000L;
                    }
                } catch (Throwable ignored) {}
            }

            Matcher urls = URL_PATTERN.matcher(line);
            while (urls.find()) {
                String url = normalizePublicUrl(urls.group());
                if (url.length() == 0 || url.contains("dashboard.pinggy.io")) continue;
                publicUrls.add(url);
                if (url.startsWith("https://")
                        && (primaryRoot.length() == 0
                        || (!primaryRoot.contains("run.pinggy-free.link")
                        && url.contains("run.pinggy-free.link")))) {
                    primaryRoot = url;
                }
            }
            if (primaryRoot.length() > 0) {
                if (connectedAtWall <= 0L) connectedAtWall = System.currentTimeMillis();
                if (expiresAtWall <= connectedAtWall) {
                    expiresAtWall = connectedAtWall + DEFAULT_LIFETIME_MS;
                }
                state = "connected";
                lastError = "";
            }
        }
    }

    private static void finishWorker(Context app, int runGeneration,
                                     boolean published, Throwable failure) {
        synchronized (LOCK) {
            if (runGeneration != generation) return;
            session = null;
            channel = null;
            worker = null;
            boolean requested = prefs(app).getBoolean(KEY_REQUESTED, false);
            if (!requested) return;

            prefs(app).edit().putBoolean(KEY_REQUESTED, false).apply();
            long now = System.currentTimeMillis();
            boolean expired = expiresAtWall > 0L && now + 5_000L >= expiresAtWall;
            if (expired) {
                state = "expired";
                lastError = "Pinggy 临时网址已到期，请重新申请";
            } else if (published) {
                state = "error";
                lastError = "Pinggy 临时公网会话已断开，请重新申请网址";
            } else {
                state = "error";
                lastError = compactError(failure);
            }
            primaryRoot = "";
            publicUrls.clear();
            appendLog(app, "EXIT state=" + state + " reason=" + lastError);
        }
    }

    private static void expireIfNeededLocked(Context app) {
        if (expiresAtWall <= 0L || System.currentTimeMillis() < expiresAtWall) return;
        prefs(app).edit().putBoolean(KEY_REQUESTED, false).apply();
        stopLocked("free-session lifetime reached", false);
        state = "expired";
        lastError = "Pinggy 临时网址已到期，请重新申请";
        primaryRoot = "";
        publicUrls.clear();
    }

    private static void stopLocked(String reason, boolean clearPublished) {
        ++generation;
        ChannelShell activeChannel = channel;
        Session activeSession = session;
        channel = null;
        session = null;
        worker = null;
        appendLog(applicationContext, "STOP reason=" + reason);
        try {
            if (activeChannel != null) activeChannel.disconnect();
        } catch (Throwable ignored) {}
        try {
            if (activeSession != null) activeSession.disconnect();
        } catch (Throwable ignored) {}
        if (clearPublished) clearPublishedStateLocked();
    }

    private static void clearPublishedStateLocked() {
        primaryRoot = "";
        publicUrls.clear();
        startedAtWall = 0L;
        connectedAtWall = 0L;
        expiresAtWall = 0L;
        allocatedRemotePort = 0;
    }

    private static void putStatus(Context app, Bundle out) {
        synchronized (LOCK) {
            boolean requested = prefs(app).getBoolean(KEY_REQUESTED, false);
            long remaining = expiresAtWall <= 0L ? 0L
                    : Math.max(0L, expiresAtWall - System.currentTimeMillis());
            out.putString("provider", PROVIDER);
            out.putBoolean("requested", requested);
            out.putString("state", state);
            out.putString("error", lastError == null ? "" : lastError);
            out.putString("primary_root",
                    "connected".equals(state) ? primaryRoot : "");
            out.putString("public_urls",
                    "connected".equals(state) ? publicUrlsTextLocked() : "");
            out.putLong("started_at", startedAtWall);
            out.putLong("connected_at", connectedAtWall);
            out.putLong("expires_at", expiresAtWall);
            out.putLong("remaining_ms", remaining);
            out.putBoolean("gateway_running", gatewayRunning);
            out.putInt("gateway_port", gatewayPort);
            out.putInt("remote_port", allocatedRemotePort);
            out.putString("origin", "http://127.0.0.1:"
                    + (gatewayPort > 0 ? gatewayPort : 8765));
            out.putString("host", HOST + ":" + PORT);
            out.putString("host_fingerprint", hostFingerprint);
            out.putString("ssh_client", "JSch " + JSCH_VERSION);
            out.putString("recent_log", recentLogText());
            out.putString("log_file",
                    new File(app.getFilesDir(), LOG_FILE).getAbsolutePath());
        }
    }

    private static String publicUrlsTextLocked() {
        StringBuilder out = new StringBuilder();
        for (String url : publicUrls) {
            if (out.length() > 0) out.append('\n');
            out.append(url);
        }
        return out.toString();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static File knownHostsFile(Context context) {
        return new File(context.getNoBackupFilesDir(), KNOWN_HOSTS);
    }

    private static void ensurePrivateFile(File file) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("无法创建 Pinggy 私有目录");
        }
        if (!file.isFile()) {
            FileOutputStream output = new FileOutputStream(file, false);
            output.close();
        }
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setExecutable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }

    private static String normalizePublicUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        while (value.endsWith("/") || value.endsWith(".") || value.endsWith(",")) {
            value = value.substring(0, value.length() - 1);
        }
        String lower = value.toLowerCase(Locale.US);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return "";
        if (!lower.contains(".pinggy") && !lower.contains(".pinggy-free.link")) return "";
        return value;
    }

    private static String compactError(Throwable failure) {
        if (failure == null) return "Pinggy 未返回公网网址，请检查网络后重试";
        String message = failure.getMessage();
        String lower = message == null ? "" : message.toLowerCase(Locale.US);
        if (lower.contains("auth fail")) {
            return "Pinggy 拒绝了空密码认证，请稍后重试";
        }
        if (lower.contains("timeout") || lower.contains("timed out")) {
            return "连接 Pinggy 超时，请检查能否访问 free.pinggy.io:443";
        }
        if (lower.contains("algorithm") || lower.contains("kex")) {
            return "当前系统缺少 Pinggy 所需的 SSH 加密算法";
        }
        String safe = sanitize(message == null
                ? failure.getClass().getSimpleName() : message);
        return "启动 Pinggy 临时网址失败：" + safe;
    }

    private static String sanitize(String raw) {
        if (raw == null) return "";
        String line = raw.replaceAll("\\u001B\\[[;\\d]*[ -/]*[@-~]", "")
                .replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", "")
                .trim();
        if (line.length() > 1200) line = line.substring(0, 1200) + "…";
        return line;
    }

    private static void appendLog(Context context, String raw) {
        String line = sanitize(raw);
        if (line.length() == 0) return;
        synchronized (recentLogs) {
            recentLogs.addLast(line);
            while (recentLogs.size() > MAX_LOG_LINES) recentLogs.removeFirst();
        }
        if (context == null) return;
        try {
            File log = new File(context.getFilesDir(), LOG_FILE);
            if (log.isFile() && log.length() > MAX_LOG_BYTES) {
                File old = new File(context.getFilesDir(), LOG_FILE + ".old");
                if (old.exists()) old.delete();
                log.renameTo(old);
            }
            FileWriter writer = new FileWriter(log, true);
            try {
                writer.write(System.currentTimeMillis() + " " + line + "\n");
            } finally {
                writer.close();
            }
        } catch (Throwable ignored) {}
    }

    private static String recentLogText() {
        StringBuilder out = new StringBuilder();
        synchronized (recentLogs) {
            int skip = Math.max(0, recentLogs.size() - 12);
            int index = 0;
            for (String line : recentLogs) {
                if (index++ < skip) continue;
                if (out.length() > 0) out.append('\n');
                out.append(line);
            }
        }
        return out.toString();
    }

    private static final class EmptyPasswordUserInfo
            implements UserInfo, UIKeyboardInteractive {
        private final Context context;
        private final int runGeneration;

        EmptyPasswordUserInfo(Context context, int runGeneration) {
            this.context = context;
            this.runGeneration = runGeneration;
        }

        @Override public String getPassphrase() { return ""; }

        @Override public String getPassword() { return ""; }

        @Override public boolean promptPassword(String message) {
            appendLog(context, "AUTH password prompt answered with an empty password");
            return true;
        }

        @Override public boolean promptPassphrase(String message) { return false; }

        @Override public boolean promptYesNo(String message) {
            appendLog(context, "HOSTKEY accepted on first use for " + HOST);
            return true;
        }

        @Override public void showMessage(String message) {
            // Pinggy may publish the assigned URL in an SSH authentication banner rather than
            // on the shell channel. Feed both transports through the same parser.
            handleLine(context, runGeneration, message);
        }

        @Override public String[] promptKeyboardInteractive(
                String destination, String name, String instruction,
                String[] prompt, boolean[] echo) {
            String[] answers = new String[prompt == null ? 0 : prompt.length];
            for (int i = 0; i < answers.length; i++) answers[i] = "";
            appendLog(context, "AUTH keyboard prompt answered with Enter");
            return answers;
        }
    }
}
