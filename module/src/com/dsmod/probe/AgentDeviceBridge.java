package com.dsmod.probe;

import android.content.Context;
import android.content.Intent;
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
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Execution backend for Agent screen, file and basic shell actions.
 *
 * <p>File paths, content sizes and timeouts are strictly validated by
 * {@link HeartbeatToolProtocol}. The caller independently enforces the user's tool allow-list and
 * permission mode before an operation reaches this class.</p>
 */
final class AgentDeviceBridge {
    static final String RISH_RESOURCE =
            "META-INF/com.dsmod.probe.agent/rish_shizuku_runtime_payload.dat";
    private static final String RISH_LEGACY_RESOURCE =
            "META-INF/com.dsmod.probe.agent/.rish_shizuku_runtime_payload.dat";
    private static final String RISH_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_agent/rish_shizuku.dex";
    private static final String SCREENSHOT_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_agent/latest_screen.png";
    private static final int NORMAL_OUTPUT_LIMIT = 64 * 1024;
    private static final int SCREENSHOT_OUTPUT_LIMIT = 24 * 1024 * 1024;
    private static final String SYSTEM_PATH =
            "/system/bin:/system/xbin:/vendor/bin:/product/bin:/system_ext/bin";
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

    interface StatusCallback {
        void onStatus(Status status);
    }

    interface ResultCallback {
        void onResult(ToolResult result);
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
                    CommandResult command = runCommandWithShizukuRecovery(
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
        EXECUTOR.execute(new Runnable() {
            @Override public void run() {
                AgentToolConfig.Snapshot config = AgentToolConfig.load();
                String backend = config.backend;
                if (!AgentToolConfig.PERMISSION_ALL.equals(config.permission)) {
                    backend = AgentToolConfig.BACKEND_IN_APP;
                }
                ToolResult result;
                if (call == null) {
                    result = new ToolResult(false, -1, "",
                            UiLanguage.text(context,
                                    "工具调用为空", "The tool call is empty"),
                            "utf-8", false);
                } else if (HeartbeatToolProtocol.TOOL_READ_FILE.equals(call.tool)) {
                    result = readFile(context, backend, call);
                } else if (HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(call.tool)) {
                    result = writeFile(context, backend, call);
                } else if (HeartbeatToolProtocol.TOOL_SHELL.equals(call.tool)) {
                    result = executeShell(context, backend, call);
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
            String staged = "/data/user/0/com.dsmod.probe/files/agent_audio/current" + suffix;
            String command = "/system/bin/mkdir -p /data/user/0/com.dsmod.probe/files/agent_audio"
                    + " && /system/bin/cp " + shellQuote(path) + " " + shellQuote(staged)
                    + " && owner=$(/system/bin/stat -c %u:%g /data/user/0/com.dsmod.probe)"
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
            bridge.setClassName("com.dsmod.probe",
                    "com.dsmod.probe.LocalAudioControlActivity");
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

    private static ToolResult executeShell(
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
        File target = new File(SCREENSHOT_FILE);
        File temporary = new File(SCREENSHOT_FILE + ".tmp");
        File parent = target.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            return new Status(false, UiLanguage.text(context,
                    "无法创建截图目录", "Could not create the screenshot directory"));
        }
        CommandResult result = runCommandWithShizukuRecovery(
                context, backend, "screencap -p", 10000L, true);
        if (result.exitCode != 0 || result.truncated || result.binary == null
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
            ProcessBuilder builder;
            if (AgentToolConfig.BACKEND_ROOT.equals(backend)) {
                builder = new ProcessBuilder("/system/bin/su", "-c", command);
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
            Map<String, String> processEnvironment = builder.environment();
            processEnvironment.put("PATH", SYSTEM_PATH);
            process = builder.start();
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
            String text = binaryOutput ? ""
                    : new String(stdout, StandardCharsets.UTF_8);
            String errorText = new String(
                    errorBytes.toByteArray(),
                    StandardCharsets.UTF_8);
            return new CommandResult(exit.intValue(), text, errorText,
                    binaryOutput ? stdout : null,
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
