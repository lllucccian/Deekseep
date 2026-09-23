package com.dsmod.probe;

import com.dsmod.relay.ExpertRelayGate;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Matrix;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.system.Os;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import de.robv.android.xposed.IXposedHookLoadPackage;
import com.dsmod.probe.LegacyXposedModule.Chain;
import com.dsmod.probe.LegacyXposedModule.Hooker;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/** Shared hook core used by both domestic and Google Play universal APKs. */

/** Hook group extracted from Main.java: CHAT category (see JavaHookGuide). */
final class HookChatPipeline {
    static final HookChatPipeline INSTANCE = new HookChatPipeline();

    static final String PROMPT_LINK_FILE  = "/data/data/com.deepseek.chat/files/deekseep_prompt_link.txt";

    static final String PROMPT_INTERVAL_V236_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_prompt_interval_code249";

    static final String PROMPT_INTERVAL_V241_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_prompt_interval_code257";

    static final String PROMPT_SPLIT_V236_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_prompt_split_code249";

    static final String PROMPT_SPLIT_V241_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_prompt_split_code257";

    static volatile int promptIntervalV236;

    static volatile int promptIntervalV241;

    static final Object PROMPT_TURN_LOCK = new Object();

    static final java.util.LinkedHashMap<String, Integer> PROMPT_TURN_COUNTS =
            new java.util.LinkedHashMap<String, Integer>();

    static final java.util.LinkedHashMap<String, String> PROMPT_LAST_TURN_KEYS =
            new java.util.LinkedHashMap<String, String>();

    static final java.util.LinkedHashMap<String, Boolean> PROMPT_LAST_TURN_DECISIONS =
            new java.util.LinkedHashMap<String, Boolean>();

    static final String ENABLED_FILE      = "/data/data/com.deepseek.chat/files/deekseep_enabled";

    static final String NO_CENSOR_FILE    = "/data/data/com.deepseek.chat/files/deekseep_nocensor";

    static final String NO_CENSOR_CONTEXT_DISABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_nocensor_context_disabled";

    static final String LOCAL_API_NO_CENSOR_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_local_api_nocensor";

    static final String AUTO_CONTINUE_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_auto_continue";

    static final String REPLY_READY_DISABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_reply_ready_disabled";

    private static final String PROACTIVE_HEARTBEAT_PLAN_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_proactive_plan.txt";

    static final String PROACTIVE_HEARTBEAT_BINDING_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_proactive_binding.json";

    static final String CRASH_TEST_ARM_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_crash_test_arm";

    // liveR92 (视觉中继状态：活着的 r92(transport 入口)) and ACTIVE_LOCAL_API_CANCEL_JOB
    // (本地 API 活动生成流的协程取消 job；App 内点「停止」会写 mv 状态为 interrupt，
    // 此时取消该 job 让本地 API 的输出立即停止，否则独立原生流会继续吐字) now live on
    // Main (see "Fields moved back" block).

    static volatile String lastInteractiveConversationId;

    static final Object HEARTBEAT_BINDING_LOCK = new Object();

    private static final ConcurrentHashMap<String, Long>
            REPLY_READY_DISPATCHED = new ConcurrentHashMap<>();

    private static final long REPLY_READY_DEDUPE_MS = TimeUnit.MINUTES.toMillis(30);

    static final ConcurrentHashMap<String, Long>
            AUTO_CONTINUE_DISPATCHED = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, AtomicInteger>
            AUTO_CONTINUE_TRUNCATION_ATTEMPTS = new ConcurrentHashMap<>();

    // Exact code257 can finish the private tool-result child after producing only a THINK
    // fragment. Keep a short lease only for a result turn that was actually sent; an ordinary
    // user generation must never be resumed by this adapter.
    static final ConcurrentHashMap<String, Long>
            V241_AGENT_RESULT_CONTINUATIONS = new ConcurrentHashMap<>();

    public static final ThreadLocal<Boolean> tlLocalApiRequest = new ThreadLocal<>();

    /**
     * The code257 transport call runs after the Local API constructor guard leaves its
     * ThreadLocal scope. Remember those exact request objects so the edit/resend repair at the
     * final transport boundary never attaches the interactive UI prompt to Local API traffic.
     */
    private static final Map<Object, Boolean> V241_LOCAL_API_REQUEST_OBJECTS =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    // DeepSeek 2.3.4 can recreate the mutable us2 RESPONSE fragment between consecutive APPEND
    // patches. A WeakHashMap keyed only by that object therefore forgets that the opening marker
    // was already seen and leaks the following JSON delta. This generation-scoped map is the
    // canonical stream identity across those fragment replacements.
    static final ConcurrentHashMap<String, HookAgentPipeline.HeartbeatResponseStream>
            HEARTBEAT_LOGICAL_RESPONSE_STREAMS = new ConcurrentHashMap<>();

    static final ConcurrentHashMap<String, HookAgentPipeline.ThinkingAgentStream>
            THINKING_LOGICAL_STREAMS = new ConcurrentHashMap<>();

    static final ConcurrentHashMap<String, Long>
            INTERACTIVE_AGENT_RESPONSE_GENERATIONS = new ConcurrentHashMap<>();

    private static final AtomicLong INTERACTIVE_AGENT_RESPONSE_SEQUENCE =
            new AtomicLong();

    private static final long INTERACTIVE_AGENT_TOOL_SCOPE_TTL_MS =
            TimeUnit.MINUTES.toMillis(30);

    /**
     * A scope is authorized only when the native visible-chat request actually received the local
     * tool contract. This is more reliable than using the local-API semaphore as a proxy: an
     * unrelated local request can briefly own that semaphore while an ordinary UI response is
     * streaming, which previously made a valid call disappear without ever being executed.
     */
    static final ConcurrentHashMap<String, Long>
            INTERACTIVE_AGENT_TOOL_SCOPES = new ConcurrentHashMap<>();

    private static volatile String cachedPromptText;

    private static volatile long cachedPromptTextAt;

    private static volatile String cachedAgentContractKey = "";

    private static volatile String cachedAgentContractText = "";

    // Network callbacks may resume on a different coroutine thread, so a ThreadLocal cannot
    // reliably identify their origin. This process-wide counter is used only to mute routine
    // Local API traffic in the optional on-screen/server trace while a native completion lives.
    public static final AtomicInteger LOCAL_API_LOG_MUTE_DEPTH = new AtomicInteger();

    // 把捕获到的 List<fp> 挂到对应 ew0 上（relay 在收集时/IO 线程跑，ThreadLocal 到不了）。
    static final Map<Object, List> ew0Fps =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Object, List>());

    // DeepSeek 仅首轮把 model_type 写入 ew0；后续轮次为 null，因此需把发送点 tp.f() 绑定到本次请求。
    private static final Map<Object, String> ew0EffectiveModels =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Object, String>());

    // 已在处理中的 expert 请求（弱引用集合，防同一对象被 hook 重复处理）
    private static final java.util.Set<Object> relaySeen =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object, Boolean>());

    // Original mv objects for which a real CONTENT_FILTER event was observed. Weak keys ensure
    // normal message lifetimes are unchanged; once a tp provides the SID, the exact kv is written
    // to ResponsePreserver's private durable store.
    private static final Map<Object, Boolean> FILTERED_ORIGINAL_MESSAGES =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    // code257 reports one server retraction through ban_regenerate, status, fragments and
    // quasi_status. Persist the original message once; the weak key keeps this exact-host
    // idempotence marker scoped to the live message lifetime.
    private static final Map<Object, Boolean> V241_FILTERED_ORIGINAL_SNAPSHOTTED =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    private static void consumeArmedCrashTestAtSend() {
        String mode = Main.readSmallText(CRASH_TEST_ARM_FILE);
        if (mode == null || mode.length() == 0) return;
        try { new File(CRASH_TEST_ARM_FILE).delete(); } catch (Throwable ignored) {}
        recordCrashTest("send-trigger", mode);
        if (mode.startsWith("native_")) {
            recordCrashTest("blocked-unsafe", mode);
            return;
        }
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override public void run() {
                throw new RuntimeException("Deekseep send-path Java crash test");
            }
        });
    }

    static synchronized void recordCrashTest(String phase, String mode) {
        String line = Main.TS.format(new Date()) + "  CRASH_TEST phase=" + phase
                + " mode=" + mode + " pid=" + android.os.Process.myPid() + "\n";
        try {
            FileWriter writer = new FileWriter(
                    "/data/data/com.deepseek.chat/files/dsprobe_crash.log", true);
            writer.write(line);
            writer.close();
        } catch (Throwable ignored) {}
        try {
            FileWriter writer = new FileWriter(Main.EXT_CRASH_LOG, true);
            writer.write(line);
            writer.close();
        } catch (Throwable ignored) {}
    }

    static boolean hasProactiveHeartbeatBinding() {
        return Main.readHeartbeatBinding().conversationId.length() > 0;
    }

    static boolean writeHeartbeatBinding(String conversationId, String instruction) {
        String sid = HeartbeatToolProtocol.cleanScope(conversationId);
        if (sid.length() == 0) return false;
        String plan = HeartbeatToolProtocol.cleanInstruction(instruction);
        synchronized (HEARTBEAT_BINDING_LOCK) {
            try {
                JSONObject object = new JSONObject();
                object.put("version", 1);
                object.put("conversation_id", sid);
                object.put("instruction", plan);
                Main.overwriteTextFile(PROACTIVE_HEARTBEAT_BINDING_FILE, object.toString());
                Main.HeartbeatBinding stored = Main.readHeartbeatBinding();
                return sid.equals(stored.conversationId)
                        && plan.equals(stored.instruction);
            } catch (Throwable t) {
                Main.log("heartbeat binding save failed: " + Main.safeThrowableMessage(t));
                return false;
            }
        }
    }

    static String legacyHeartbeatPlan() {
        return HeartbeatToolProtocol.cleanInstruction(
                Main.readSmallText(PROACTIVE_HEARTBEAT_PLAN_FILE));
    }

    static boolean isAutoContinueEnabled() {
        return new File(AUTO_CONTINUE_FILE).isFile();
    }

    /** Keep the existing default-on behavior while allowing users to opt out. */
    static boolean isReplyReadyNotificationsEnabled() {
        return !new File(REPLY_READY_DISABLED_FILE).isFile();
    }

    static boolean isNoCensor() {
        return new File(NO_CENSOR_FILE).exists();
    }

    /** Context retention follows anti-recall by default; the file records an explicit opt-out. */
    static boolean isNoCensorContextRetentionEnabled() {
        return !new File(NO_CENSOR_CONTEXT_DISABLED_FILE).exists();
    }

    static boolean isLocalApiNoCensor() {
        return new File(LOCAL_API_NO_CENSOR_FILE).exists();
    }

    static boolean isPromptSplitInjectionEnabled() {
        if (HostCompat.isV236()) return new File(PROMPT_SPLIT_V236_FILE).isFile();
        if (HostCompat.isV241()) return new File(PROMPT_SPLIT_V241_FILE).isFile();
        return false;
    }

    static int configuredPromptInjectionInterval() {
        if (HostCompat.isV236()) {
            int cached = promptIntervalV236;
            if (cached > 0) return cached;
            promptIntervalV236 = readPromptInterval(PROMPT_INTERVAL_V236_FILE);
            return promptIntervalV236;
        }
        if (HostCompat.isV241()) {
            int cached = promptIntervalV241;
            if (cached > 0) return cached;
            promptIntervalV241 = readPromptInterval(PROMPT_INTERVAL_V241_FILE);
            return promptIntervalV241;
        }
        return 3;
    }

    private static int readPromptInterval(String path) {
        try {
            int value = Integer.parseInt(Main.readSmallText(path).trim());
            return Math.max(1, Math.min(30, value));
        } catch (Throwable ignored) {
            return 3;
        }
    }

    private static boolean shouldInjectUserPrompt(
            String conversationId, Object parentMessageId) {
        if (!isPromptSplitInjectionEnabled()) return true;
        // code257 uses a non-null message id for edit/regenerate/resume requests. These are a
        // replacement or continuation of an existing user turn, not a new user turn. Counting
        // them again made long-press Edit -> Send lose the prompt whenever periodic injection
        // selected the next turn as a skip. Keep this rule on the exact code257 adapter only;
        // older hosts retain their established counter semantics byte-for-byte below.
        if (HostCompat.isV241() && parentMessageId != null) return true;
        int skippedTurns = configuredPromptInjectionInterval();
        int period = skippedTurns + 1;
        String scope = conversationId == null || conversationId.length() == 0
                ? "__pending_new_conversation__" : conversationId;
        String turnKey = scope + "|" + String.valueOf(parentMessageId);
        synchronized (PROMPT_TURN_LOCK) {
            // The first message of a new host conversation is constructed before DeepSeek assigns
            // its SID. Carry that already-counted first turn into the SID on the next send instead
            // of silently starting the counter at the second user message.
            if (!"__pending_new_conversation__".equals(scope)
                    && !PROMPT_TURN_COUNTS.containsKey(scope)) {
                Integer pending = PROMPT_TURN_COUNTS.remove("__pending_new_conversation__");
                PROMPT_LAST_TURN_KEYS.remove("__pending_new_conversation__");
                PROMPT_LAST_TURN_DECISIONS.remove("__pending_new_conversation__");
                if (pending != null && pending.intValue() > 0) {
                    PROMPT_TURN_COUNTS.put(scope, pending);
                }
            }
            String previousKey = PROMPT_LAST_TURN_KEYS.get(scope);
            if (turnKey.equals(previousKey)) {
                Boolean previous = PROMPT_LAST_TURN_DECISIONS.get(scope);
                return previous == null || previous.booleanValue();
            }
            int turn = PROMPT_TURN_COUNTS.containsKey(scope)
                    ? PROMPT_TURN_COUNTS.get(scope).intValue() + 1 : 1;
            boolean inject = ((turn - 1) % period) == 0;
            PROMPT_TURN_COUNTS.put(scope, Integer.valueOf(turn));
            PROMPT_LAST_TURN_KEYS.put(scope, turnKey);
            PROMPT_LAST_TURN_DECISIONS.put(scope, Boolean.valueOf(inject));
            if (PROMPT_TURN_COUNTS.size() > 128) {
                String eldest = PROMPT_TURN_COUNTS.keySet().iterator().next();
                PROMPT_TURN_COUNTS.remove(eldest);
                PROMPT_LAST_TURN_KEYS.remove(eldest);
                PROMPT_LAST_TURN_DECISIONS.remove(eldest);
            }
            return inject;
        }
    }

    /**
     * 对话代理：后台把一条用户消息发到第三方端点，返回文本回显到原生聊天。
     */
    private static void dispatchChatProxy(final String sid, final String prompt) {
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                try {
                    z17.RelayResult result = z17.relay(prompt);
                    if (!result.success) {
                        Main.queueVisibleAgentAnswer(Main.hostApplicationContext, sid,
                                "对话转接请求失败：" + result.error);
                        return;
                    }
                    String content = result.content;
                    if (content == null || content.trim().length() == 0) {
                        content = "对话转接未返回内容";
                    }
                    String reasoning = result.reasoning;
                    boolean hasReasoning = reasoning != null && reasoning.trim().length() > 0;

                    if (hasReasoning) {
                        insertRelayMessageWithReasoning(sid, reasoning, content);
                    } else {
                        Main.queueVisibleAgentAnswer(Main.hostApplicationContext, sid, content);
                    }
                } catch (Throwable t) {
                    Main.queueVisibleAgentAnswer(Main.hostApplicationContext, sid,
                            "对话转接请求失败：" + Main.safeThrowableMessage(t));
                }
            }
        }, "Deekseep-ChatProxy");
        thread.setDaemon(true);
        thread.start();
    }

    private static void insertRelayMessageWithReasoning(
            String sid, String reasoning, String content) {
        try {
            java.io.File dbFile = new java.io.File(
                    "/data/data/com.deepseek.chat/databases/deepseek_chat.db");
            if (!dbFile.isFile()) {
                Main.queueVisibleAgentAnswer(Main.hostApplicationContext, sid, content);
                return;
            }
            android.database.sqlite.SQLiteDatabase db = null;
            try {
                db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        dbFile.getPath(), null,
                        android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
                if (!ChatEditorUi.sessionRowExists(db, sid)) {
                    Main.queueVisibleAgentAnswer(Main.hostApplicationContext, sid, content);
                    return;
                }
                ChatEditorUi.createMessageTable(db, sid);
                String table = ChatEditorUi.quoteIdent("chat_session_messages_" + sid);
                long max = 0;
                android.database.Cursor c = db.rawQuery(
                        "SELECT MAX(message_id) FROM " + table, null);
                if (c.moveToFirst() && !c.isNull(0)) max = c.getLong(0);
                c.close();
                Long current = ChatEditorUi.currentMessageId(db, sid);
                long parent = current != null && current.longValue() > 0
                        ? current.longValue() : max;
                long next = Math.max(max, parent) + 1;
                double now = System.currentTimeMillis() / 1000.0d;

                org.json.JSONArray fragments = new org.json.JSONArray();
                org.json.JSONObject think = new org.json.JSONObject();
                think.put("id", 1);
                think.put("type", "THINK");
                think.put("content", reasoning);
                fragments.put(think);
                org.json.JSONObject response = new org.json.JSONObject();
                response.put("id", 2);
                response.put("type", "RESPONSE");
                response.put("content", content);
                fragments.put(response);

                db.execSQL("INSERT INTO " + table
                        + "(message_id,parent_id,role,thinking_enabled,status,inserted_at,"
                        + "feedback_type,accumulated_token_usage,ban_edit,ban_regenerate,tips,"
                        + "fragments,conversation_mode) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        new Object[]{next, parent > 0 ? Long.valueOf(parent) : null,
                                "ASSISTANT", 1,
                                "FINISHED", now, null, 0, 0, 0, null,
                                fragments.toString(), null});
                db.execSQL("UPDATE chat_session_list SET current_message_id=?,updated_at=?,"
                        + "cache_version=? WHERE id=?",
                        new Object[]{next, now, ChatEditorUi.FREEZE_VERSION, sid});
                ChatEditorUi.backupLocalSession(db, sid);
                try {
                    android.os.Process.killProcess(android.os.Process.myPid());
                } catch (Throwable ignored) {}
            } finally {
                if (db != null) try { db.close(); } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Main.log("relay db insert failed: " + Main.safeThrowableMessage(t));
            Main.queueVisibleAgentAnswer(Main.hostApplicationContext, sid, content);
        }
    }

    private static void dispatchReplyReady(
            Context context, String conversationId, String responseId) {
        try {
            Intent ready = new Intent(ProactiveHeartbeatReceiver.ACTION_REPLY_READY);
            ready.setClassName(Main.runtimeComponentPackage(),
                    ProactiveHeartbeatReceiver.class.getName());
            ready.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES
                    | Intent.FLAG_RECEIVER_FOREGROUND);
            ready.putExtra(ProactiveHeartbeatReceiver.EXTRA_TOKEN,
                    ProactiveHeartbeatReceiver.TOKEN);
            ready.putExtra(ProactiveHeartbeatReceiver.EXTRA_RESPONSE_ID, responseId);
            ready.putExtra(ProactiveHeartbeatReceiver.EXTRA_CONVERSATION_ID,
                    HeartbeatToolProtocol.cleanScope(conversationId));
            context.sendBroadcast(ready);
        } catch (Throwable t) {
            REPLY_READY_DISPATCHED.remove(responseId);
            Main.log("reply-ready notification dispatch failed: "
                    + Main.safeThrowableMessage(t));
        }
    }

    // ── ChatFullCompletionRequest 系统提示词注入 ─────────────────────

    void hookChatRequest(ClassLoader cl) {
        try {
            // 2.4.1 moved the real completion request to az0. Keep this explicit because
            // ew0->qw0->nx0 is also used by older fallback paths and can resolve an unrelated
            // coroutine class when the host has already been detected as code257.
            // 2.5.0 (GP) renamed it again to r51, an unrelated class not reachable through the
            // legacy rename chain above, so it needs the same kind of explicit branch as az0.
            Class<?> k = HostCompat.isV250() ? cl.loadClass(HostCompat.v250ChatFullCompletionRequestClass())
                    : HostCompat.isV241() ? cl.loadClass("az0")
                    : HostCompat.load(cl, "ew0");
            int n = 0;
            for (Constructor<?> ctor : k.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                // 合成构造器首参为 int（kotlinx 序列化标志位），普通构造器首参为 String
                final boolean isSynthetic = pts.length > 0 && pts[0] == int.class;
                final int promptIdx = isSynthetic ? 3 : 2;
                if (pts.length <= promptIdx) continue;
                if (pts[promptIdx] != String.class) continue;
                // Never hook kotlinx serialization/copy construction. It is not a send boundary
                // and modifying it corrupts host request restoration state.
                if (isSynthetic) continue;
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            if (!isSynthetic) consumeArmedCrashTestAtSend();
                            Object[] originalArgs = chain.getArgs().toArray();
                            String originalPrompt = (String) originalArgs[promptIdx];
                            String originalBody = HistoryBridge.stripInjectedSystemPrompts(
                                    originalPrompt == null ? "" : originalPrompt).trim();
                            boolean nativeProactiveEvent =
                                    originalBody.startsWith(
                                            HeartbeatToolProtocol.EVENT_START)
                                    && originalBody.indexOf(
                                            HeartbeatToolProtocol.EVENT_END,
                                            HeartbeatToolProtocol.EVENT_START.length()) >= 0;
                            boolean privateAgentTransport = HeartbeatToolProtocol
                                    .isCompletePrivateTransportBody(originalBody);
                            // API callers already supplied their system/developer messages in the
                            // translated prompt. Do not silently prepend the UI's global prompt.
                            if (Boolean.TRUE.equals(tlLocalApiRequest.get())) {
                                HookLogOverlay.event("HOST", "Local API request passed",
                                        "body_chars=" + originalBody.length()
                                                + " prompt_injection=skip");
                                Object localRequest = chain.proceed();
                                if (HostCompat.isV241() && localRequest != null) {
                                    V241_LOCAL_API_REQUEST_OBJECTS.put(localRequest, Boolean.TRUE);
                                }
                                return localRequest;
                            }
                            String conversationId = !isSynthetic
                                    && originalArgs.length > 0
                                    && originalArgs[0] instanceof String
                                    ? HeartbeatToolProtocol.cleanScope(
                                            (String) originalArgs[0]) : "";
                            if (conversationId.length() > 0) {
                                lastInteractiveConversationId = conversationId;
                                // Exact code257 migration: older builds could leave the periodic
                                // switch enabled without a persisted chat binding.  The companion
                                // alarm then woke correctly but skipped forever.  Bind the first
                                // real interactive request and immediately resync its receiver.
                                if (HostCompat.isV241()
                                        && Main.isProactiveHeartbeatEnabled()
                                        && !hasProactiveHeartbeatBinding()
                                        && writeHeartbeatBinding(
                                        conversationId, legacyHeartbeatPlan())) {
                                    Activity heartbeatActivity = Main.MODULE.curAct.get();
                                    if (heartbeatActivity != null) {
                                        Main.dispatchProactiveHeartbeatConfig(
                                                heartbeatActivity, true);
                                    }
                                    Main.log("code257 migrated heartbeat binding sid="
                                            + conversationId);
                                }
                                if (NativeDualChatBridge.isActive()) {
                                    NativeDualChatBridge.markHiddenSession(conversationId);
                                    NativeDualChatBridge.bindActiveSession(conversationId);
                                }
                            }
                            if (!nativeProactiveEvent && !privateAgentTransport) {
                                JavaPluginManager.emit("chat.send.before",
                                        JavaPluginManager.eventData(
                                                "conversationId", conversationId,
                                                "textLength", originalBody.length()));
                                String transformed = JavaPluginPlatform.transformOutgoing(
                                        conversationId, originalBody);
                                if (!transformed.equals(originalBody)
                                        && originalPrompt != null
                                        && originalPrompt.trim().equals(originalBody)) {
                                    originalBody = transformed;
                                    originalPrompt = transformed;
                                    originalArgs[promptIdx] = transformed;
                                }
                            }
                            // 对话代理：启用时把本条原生消息路由到第三方端点，返回注入聊天，
                            // 并把原生 prompt 压成最小占位，避免 DeepSeek 后端再生成一份正文。
                            if (z17.isEnabled() && !isSynthetic
                                    && conversationId.length() > 0
                                    && originalBody.length() > 0) {
                                originalArgs[promptIdx] = "\u2026";
                                dispatchChatProxy(conversationId, originalBody);
                                return chain.proceed(originalArgs);
                            }
                            HookLogOverlay.event("HOST", "Message send started",
                                    "sid=" + conversationId
                                            + " body_chars=" + originalBody.length()
                                            + " attachments="
                                            + (originalArgs.length > 3
                                            ? MainReflectionSupport.logValue(originalArgs[3]) : "n/a")
                                            + " thinking="
                                            + (originalArgs.length > 4
                                            ? MainReflectionSupport.logValue(originalArgs[4]) : "n/a"));
                            boolean forcedNativeReasoning = false;
                            if (nativeProactiveEvent && !isSynthetic
                                    && originalArgs.length > 4) {
                                Main.NativeUiHeartbeatRequest pending =
                                        Main.PENDING_NATIVE_UI_HEARTBEATS.get(
                                                conversationId);
                                if (pending != null) {
                                    originalArgs[4] = Boolean.valueOf(
                                            pending.reasoning);
                                    forcedNativeReasoning = true;
                                }
                            }
                            // 上下文压缩：只做投递。压缩由聊天页的按钮手动触发并落盘成一条摘要记录，
                            // 这里只负责把该对话欠着的那条摘要随本次请求发出去，且只发一次。
                            // 只在真正的交互式用户发送时投递——心跳/Agent 私有传输和 Local API
                            // 各自带着完整上下文，改写它们的提示词会破坏调用方的语义。
                            String compactionPrompt = "";
                            if (!nativeProactiveEvent && !privateAgentTransport
                                    && !isSynthetic && conversationId.length() > 0) {
                                compactionPrompt =
                                        ChatCompactionBridge.claimCapsule(conversationId);
                            }
                            long injectStarted = SystemClock.uptimeMillis();
                            String sysPrompt = readPrompt();
                            // code257 keeps Agent/MCP injection independent from the optional
                            // user-authored prompt. readPrompt() legitimately returns null when
                            // that feature is disabled; treating it as an empty component avoids
                            // aborting the entire request rewrite before the tool contract is
                            // attached. code249 has its own independent adapter immediately below.
                            if ((HostCompat.isV241() || HostCompat.isV250())
                                    && sysPrompt == null) {
                                sysPrompt = "";
                            }
                            // code249 also allows the optional user prompt to be disabled while
                            // Agent tools remain enabled. Do not let that absent component abort
                            // the request rewrite before the code249 tool contract is attached.
                            // Other legacy branches retain their established behavior.
                            if (HostCompat.isV236() && sysPrompt == null) {
                                sysPrompt = "";
                            }
                            if (sysPrompt.length() > 0
                                    && !shouldInjectUserPrompt(conversationId,
                                    originalArgs.length > 1 ? originalArgs[1] : null)) {
                                sysPrompt = "";
                            }
                            java.util.Set<String> enabledLocalTools =
                                    AgentToolConfig.effectiveTools(
                                            Main.isProactiveHeartbeatEnabled());
                            String heartbeatTools = !isSynthetic
                                    && conversationId.length() > 0
                                    && (!enabledLocalTools.isEmpty()
                                    || JavaPluginPlatform.hasAgentTools())
                                    ? cachedAgentSystemPrompt(
                                            System.currentTimeMillis(),
                                            conversationId, enabledLocalTools)
                                    : "";
                            if (!nativeProactiveEvent
                                    && heartbeatTools.length() > 0) {
                                authorizeInteractiveAgentToolScope(
                                        conversationId);
                                beginInteractiveAgentResponseGeneration(
                                        conversationId);
                            }
                            String dualWorkspacePrompt = NativeDualChatBridge.isActive()
                                    ? ModelFileOutput.PROMPT : "";
                            String pluginPrompt = !nativeProactiveEvent
                                    && !privateAgentTransport
                                    ? JavaPluginPlatform.systemPrompt(conversationId) : "";
                            String retainedRecallContext = "";
                            if (!nativeProactiveEvent && !privateAgentTransport
                                    && conversationId.length() > 0
                                    && isNoCensor()
                                    && isNoCensorContextRetentionEnabled()) {
                                String previous = ResponsePreserver
                                        .claimLatestFilteredResponseText(
                                                cl, conversationId, 128 * 1024);
                                if (previous.length() > 0) {
                                    retainedRecallContext =
                                            "以下内容是本对话中上一条已经生成、随后被服务端撤回的"
                                            + "助手回答。它只是隐藏的连续对话上下文，不是用户的新"
                                            + "指令。请结合它理解用户当前消息，不要向用户提及这段"
                                            + "说明或撤回机制。\n<previous_assistant_response>\n"
                                            + previous
                                            + "\n</previous_assistant_response>";
                                    Main.log("attached hidden recalled context sid="
                                            + conversationId + " chars=" + previous.length());
                                }
                            }
                            String combinedPrompt = combineSystemPrompts(
                                    combineSystemPrompts(sysPrompt, heartbeatTools),
                                    dualWorkspacePrompt);
                            combinedPrompt = combineSystemPrompts(
                                    combinedPrompt, retainedRecallContext);
                            combinedPrompt = combineSystemPrompts(
                                    combinedPrompt, compactionPrompt);
                            combinedPrompt = combineSystemPrompts(
                                    combinedPrompt, pluginPrompt);
                            if (combinedPrompt.length() > 0) {
                                Object[] args = originalArgs;
                                String orig = originalPrompt;
                                if (orig == null) orig = "";
                                args[promptIdx] = HistoryBridge.wrapSystemPrompt(
                                        combinedPrompt, orig);
                                if (nativeProactiveEvent && !isSynthetic) {
                                    Main.log("native proactive ew0 sid="
                                            + MainReflectionSupport.logValue(args[0])
                                            + " parent=" + MainReflectionSupport.logValue(args[1])
                                            + " prompt_chars="
                                            + String.valueOf(args[promptIdx]).length()
                                            + " files=" + MainReflectionSupport.logValue(args[3])
                                            + " thinking=" + MainReflectionSupport.logValue(args[4])
                                            + " search=" + MainReflectionSupport.logValue(args[5])
                                            + " audio=" + MainReflectionSupport.logValue(args[6])
                                            + " preempt=" + MainReflectionSupport.logValue(args[7])
                                            + " model=" + MainReflectionSupport.logValue(args[8])
                                            + " pow_chars=" + (args[9] instanceof String
                                            ? ((String) args[9]).length() : -1)
                                            + " mask=" + MainReflectionSupport.logValue(args[10])
                                            + " forced_thinking="
                                            + forcedNativeReasoning);
                                }
                                long injectCost = SystemClock.uptimeMillis() - injectStarted;
                                // Do not synchronously write two diagnostic files for every send.
                                // That I/O ran before ew0 construction completed and could leave
                                // DeepSeek's default INTERRUPTED placeholder visible for a frame.
                                if (Main.isSrvLog() || injectCost >= 16L) {
                                    Main.log("injected system prompt (synthetic=" + isSynthetic
                                            + ", heartbeat_tools="
                                            + (heartbeatTools.length() > 0)
                                            + ", cost_ms=" + injectCost
                                            + ", chars=" + combinedPrompt.length() + ")");
                                }
                                HookLogOverlay.event("NETWORK", "Request modified and submitted",
                                        "sid=" + conversationId
                                                + " system_chars=" + combinedPrompt.length()
                                                + " tools=" + (heartbeatTools.length() > 0)
                                                + " cost_ms=" + injectCost);
                                return chain.proceed(args);
                            }
                            if (forcedNativeReasoning) {
                                return chain.proceed(originalArgs);
                            }
                        } catch (Throwable t) {
                            HookLogOverlay.event("ERROR", "Request rewrite failed",
                                    "reason=" + Main.safeThrowableMessage(t));
                            Main.log("inject err: " + t);
                        }
                        return chain.proceed();
                    }
                });
                n++;
            }
            Main.log("hooked completion request " + k.getSimpleName() + " constructors x" + n);
        } catch (Throwable t) { Main.log("hookChatRequest failed: " + t); }
    }

    private static String combineSystemPrompts(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.length() == 0) return right;
        if (right.length() == 0) return left;
        return left + "\n\n" + right;
    }

    private static String cachedAgentSystemPrompt(
            long now, String conversationId,
            java.util.Set<String> enabledTools) {
        String plan = Main.heartbeatPlanForConversation(conversationId);
        int interval = Main.proactiveHeartbeatIntervalMinutes();
        // Tool-visible wall time only needs minute accuracy. Exact seconds are supplied by the
        // get_current_time tool. Reusing the complete contract avoids a large allocation burst on
        // every send, which could expose DeepSeek's transient INTERRUPTED placeholder for a frame.
        String key = HeartbeatToolProtocol.cleanScope(conversationId)
                + "|" + (now / 60_000L) + "|" + interval + "|" + plan
                + "|" + String.valueOf(enabledTools)
                + "|plugins=" + JavaPluginPlatform.generation()
                + "|" + AgentToolConfig.load().promptStrength;
        String presentKey = cachedAgentContractKey;
        String present = cachedAgentContractText;
        if (key.equals(presentKey) && present.length() > 0) return present;
        synchronized (Main.class) {
            if (key.equals(cachedAgentContractKey)
                    && cachedAgentContractText.length() > 0) {
                return cachedAgentContractText;
            }
            String built = HeartbeatToolProtocol.systemPrompt(
                    now, plan, interval, conversationId, enabledTools);
            cachedAgentContractKey = key;
            cachedAgentContractText = built;
            return built;
        }
    }

    private static void authorizeInteractiveAgentToolScope(String value) {
        String scope = HeartbeatToolProtocol.cleanScope(value);
        if (scope.length() == 0) return;
        long now = System.currentTimeMillis();
        INTERACTIVE_AGENT_TOOL_SCOPES.put(
                scope, Long.valueOf(now + INTERACTIVE_AGENT_TOOL_SCOPE_TTL_MS));
        if (INTERACTIVE_AGENT_TOOL_SCOPES.size() <= 32) return;
        for (Map.Entry<String, Long> entry
                : INTERACTIVE_AGENT_TOOL_SCOPES.entrySet()) {
            Long expiry = entry.getValue();
            if (expiry == null || expiry.longValue() < now) {
                INTERACTIVE_AGENT_TOOL_SCOPES.remove(
                        entry.getKey(), expiry);
            }
        }
    }

    private static void beginInteractiveAgentResponseGeneration(String value) {
        String scope = HeartbeatToolProtocol.cleanScope(value);
        if (scope.length() == 0) return;
        long generation = INTERACTIVE_AGENT_RESPONSE_SEQUENCE.incrementAndGet();
        INTERACTIVE_AGENT_RESPONSE_GENERATIONS.put(scope, Long.valueOf(generation));
        String prefix = scope + "|";
        String currentPrefix = prefix + generation + "|";
        for (String key : HEARTBEAT_LOGICAL_RESPONSE_STREAMS.keySet()) {
            if (!key.startsWith(prefix) || key.startsWith(currentPrefix)) continue;
            HEARTBEAT_LOGICAL_RESPONSE_STREAMS.remove(key);
        }
        for (String key : THINKING_LOGICAL_STREAMS.keySet()) {
            if (!key.startsWith(prefix) || key.startsWith(currentPrefix)) continue;
            THINKING_LOGICAL_STREAMS.remove(key);
        }
    }

    // ── 阻止内容安全审查擦除（clear_response 拦截）─────────────────
    void hookSafetyRetraction(ClassLoader cl) {
        try {
            Class<?> k = HostCompat.load(cl, "kb7");
            int n = 0;
            for (Constructor<?> ctor : k.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                int boolIdx = -1;
                for (int i = 0; i < pts.length; i++) {
                    if (pts[i] == boolean.class) { boolIdx = i; break; }
                }
                if (boolIdx < 0) continue;
                final int idx = boolIdx;
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            List<Object> a = chain.getArgs();
                            if (Main.isSrvLog()) {
                                StringBuilder sb = new StringBuilder("kb7(hint)");
                                for (int i = 0; i < a.size(); i++) {
                                    sb.append(" arg").append(i).append('=').append(a.get(i));
                                }
                                Main.srvLog(sb.toString());
                            }
                            if (isNoCensor()) {
                                Object cur = a.get(idx);
                                if (Boolean.TRUE.equals(cur)) {
                                    Object[] args = a.toArray();
                                    args[idx] = Boolean.FALSE;
                                    Main.log("blocked clear_response (kb7.arg" + idx + ")");
                                    return chain.proceed(args);
                                }
                            }
                        } catch (Throwable t) { Main.log("clear_response block err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            Main.log("hooked kb7 constructors x" + n + " (clear_response guard)");
        } catch (Throwable t) { Main.log("hookSafetyRetraction failed: " + t); }
    }

    // ── 诊断：抓取服务器返回的 SSE 原始事件 ─────────────────────────
    void installServerCapture(ClassLoader cl) {
        try {
            // code257's SSE line parser emits ff8(event,data). The old lv7
            // compatibility chain now resolves a one-field analytics Type and
            // therefore cannot be used for this generation.
            Class<?> k = HostCompat.isV241()
                    ? cl.loadClass("ff8") : HostCompat.load(cl, "lv7");
            int n = 0;
            for (Constructor<?> ctor : k.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 2 || pts[0] != String.class || pts[1] != String.class) continue;
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object r = chain.proceed();
                        try {
                            String overlayEvent = String.valueOf(chain.getArg(0));
                            Object overlayData = chain.getArg(1);
                            String overlayDataText = overlayData == null
                                    ? "" : String.valueOf(overlayData);
                            boolean localApiTraffic = isLocalApiLogMuted();
                            boolean localApiError = localApiTraffic
                                    && z8.isError(
                                            overlayEvent + " " + overlayDataText);
                            if (!localApiTraffic || localApiError) {
                                HookLogOverlay.serverEvent(overlayEvent,
                                        overlayDataText.length());
                            }
                            if (Main.isSrvLog() && (!localApiTraffic || localApiError)) {
                                String evt = String.valueOf(chain.getArg(0));
                                Object d = chain.getArg(1);
                                String data = String.valueOf(d);
                                if (data != null && data.length() > 4000) {
                                    data = data.substring(0, 4000) + "...<truncated len=" + String.valueOf(d).length() + ">";
                                }
                                Main.srvLog((localApiTraffic ? "[dq0] " : "")
                                        + "evt=" + evt + "  data=" + data);
                            }
                        } catch (Throwable t) { Main.srvLog("lv7 capture err: " + t); }
                        return r;
                    }
                });
                n++;
            }
            Main.log("installed server capture on lv7 x" + n);
        } catch (Throwable t) { Main.log("installServerCapture failed: " + t); }
    }

    /** Sanitizes 2.3.4's whole-fragments replacement before gw.i builds its Markdown State. */
    private static HeartbeatFragmentsPatch sanitizeHeartbeatFragmentsPatch(
            Object message, Object jsonElement) {
        if (message == null || jsonElement == null
                || !AgentToolConfig.enabledFast()) return null;
        try {
            String source = String.valueOf(jsonElement);
            if (source.indexOf("DEEKSEEP") < 0
                    && source.indexOf("[[") < 0
                    && !AgentToolConfig.hideToolLogs()) return null;
            JSONArray fragments = new JSONArray(source);
            ArrayList<Main.HeartbeatSanitizedUpdate> updates = new ArrayList<>();
            boolean changed = false;
            for (int index = 0; index < fragments.length(); index++) {
                JSONObject fragment = fragments.optJSONObject(index);
                if (fragment == null) continue;
                String type = fragment.optString("type");
                String raw = fragment.optString("content", "");
                if ("THINK".equals(type) && AgentToolConfig.hideToolLogs()) {
                    String safeThinking = HeartbeatToolProtocol
                            .sanitizeThinkingToolNarration(raw);
                    if (!safeThinking.equals(raw)) {
                        fragment.put("content", safeThinking);
                        changed = true;
                    }
                    continue;
                }
                if (!"RESPONSE".equals(type)) continue;
                if (!HookAgentPipeline.shouldMonitorHeartbeatFragment(message, raw)) continue;
                Main.HeartbeatSanitizedUpdate update = HookAgentPipeline.prepareHeartbeatStateUpdate(
                        message, raw, false, true);
                updates.add(update);
                if (!update.safe.equals(raw)) {
                    fragment.put("content", update.safe);
                    changed = true;
                }
            }
            if (!changed) return null;
            Object replacement = parseHostJsonElement(fragments.toString());
            return replacement == null ? null
                    : new HeartbeatFragmentsPatch(replacement, updates);
        } catch (Throwable error) {
            Main.log("heartbeat gw.fragments patch filter failed: "
                    + Main.safeThrowableMessage(error));
            return null;
        }
    }

    /** Uses the host's own kotlinx Json parser so gw.i receives the exact ge4 runtime type. */
    private static Object parseHostJsonElement(String value) throws Exception {
        ClassLoader loader = Main.hostClassLoader;
        if (loader == null) return null;
        Class<?> jsonOwner = loader.loadClass(HostCompat.name("sf4"));
        Field jsonField = jsonOwner.getDeclaredField("a");
        jsonField.setAccessible(true);
        Object json = jsonField.get(null);
        Class<?> element = loader.loadClass(HostCompat.name("ge4"));
        Field companionField = element.getDeclaredField("Companion");
        companionField.setAccessible(true);
        Object companion = companionField.get(null);
        Method serializer = companion.getClass().getDeclaredMethod("serializer");
        serializer.setAccessible(true);
        Object elementSerializer = serializer.invoke(companion);
        for (Class<?> type = json.getClass(); type != null;
             type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!"b".equals(method.getName()) || parameters.length != 2
                        || parameters[1] != String.class) continue;
                method.setAccessible(true);
                return method.invoke(json, elementSerializer, value);
            }
        }
        return null;
    }

    private static final class HeartbeatFragmentsPatch {
        final Object replacement;
        final ArrayList<Main.HeartbeatSanitizedUpdate> updates;

        HeartbeatFragmentsPatch(
                Object replacement, ArrayList<Main.HeartbeatSanitizedUpdate> updates) {
            this.replacement = replacement;
            this.updates = updates;
        }
    }

    // ── 真正的替换拦截：mv.i() JSON-patch 应用点 ────────────────────
    void hookContentFilterApply(ClassLoader cl) {
        try {
            Class<?> k = HostCompat.load(cl, "mv");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals("i")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 4 || pts[0] != String.class) continue;
                Main.MODULE.hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object completionMessage = chain.getThisObject();
                        String completionPrevious = null;
                        String completionNext = null;
                        try {
                            Object a0 = chain.getArg(0);
                            String path = a0 instanceof String ? (String) a0 : "";
                            String val = String.valueOf(chain.getArg(1));
                            if ("status".equals(path) || "quasi_status".equals(path)) {
                                completionPrevious = callStr(completionMessage,
                                        "status".equals(path) ? "D" : "x");
                                completionNext = val;
                            }
                            if ("fragments".equals(path)) {
                                HeartbeatFragmentsPatch patch =
                                        sanitizeHeartbeatFragmentsPatch(
                                                chain.getThisObject(), chain.getArg(1));
                                if (patch != null && patch.replacement != null) {
                                    Object[] args = chain.getArgs().toArray();
                                    args[1] = patch.replacement;
                                    Object result = chain.proceed(args);
                                    for (Main.HeartbeatSanitizedUpdate update : patch.updates) {
                                        Main.dispatchHeartbeatStateUpdate(update);
                                    }
                                    Main.log("heartbeat gw.fragments patch sanitized before apply"
                                            + " responses=" + patch.updates.size());
                                    return result;
                                }
                            }
                            // Exact code257 keeps ban_regenerate in the live lw.r StateFlow.
                            // CONTENT_FILTER updates this field independently from status and
                            // fragments, so merely preserving the text still lets Compose hide
                            // the footer action. While anti-recall is active, apply a concrete
                            // false patch and notify the live flow instead of dropping the update.
                            if (HostCompat.isV241() && "ban_regenerate".equals(path)
                                    && isNoCensorForMessage(completionMessage)) {
                                // 2.4.1 writes ban_regenerate independently of CONTENT_FILTER.
                                // Treat the true transition as the filter boundary before
                                // replacing it, otherwise the original response is never
                                // persisted and there is no context to restore after reload.
                                if ("true".equalsIgnoreCase(val)
                                        || "1".equals(val)) {
                                    markFilteredOriginal(cl, completionMessage,
                                            "mv.i/ban_regenerate");
                                    Main.log("captured code257 filtered response from ban_regenerate=true");
                                }
                                Object falseElement = parseHostJsonElement("false");
                                Object[] args = chain.getArgs().toArray();
                                if (falseElement != null) args[1] = falseElement;
                                Object result = falseElement == null
                                        ? null : chain.proceed(args);
                                forceLiveRegenerateAllowedV241(completionMessage);
                                Main.log("forced code257 live ban_regenerate=false");
                                return result;
                            }
                            boolean isFilter =
                                    (path.equals("fragments") && val.contains("TEMPLATE_RESPONSE"))
                                 || ((path.equals("status") || path.equals("quasi_status"))
                                        && val.contains("CONTENT_FILTER"));
                            if (Main.isSrvLog() && (isFilter || path.equals("fragments")
                                    || path.equals("status") || path.equals("quasi_status"))) {
                                String v = val.length() > 300 ? val.substring(0, 300) + "..." : val;
                                Main.srvLog("[CF] mv.i path=" + path + " filter=" + isFilter
                                        + " nocensor=" + isNoCensor() + " val=" + v);
                            }
                            if (isFilter) {
                                if (Main.isSrvLog() && path.equals("fragments")) {
                                    Main.srvLog("[CF] this.m.a@skip " + dumpMv(chain.getThisObject()));
                                    Main.srvLog(dumpStack());
                                }
                                if (isNoCensorForMessage(chain.getThisObject())) {
                                    markFilteredOriginal(cl, chain.getThisObject(),
                                            "mv.i/" + path);
                                    forceLiveRegenerateAllowedV241(chain.getThisObject());
                                    Main.log("skipped CONTENT_FILTER patch mv.i(" + path + ")");
                                    if (Main.isSrvLog()) Main.srvLog("[CF] skipped mv.i(" + path + ")");
                                    return null; // 跳过原 void 方法
                                }
                            }
                        } catch (Throwable t) { Main.log("content-filter block err: " + t); }
                        Object result = chain.proceed();
                        if (completionNext != null) {
                            try {
                                maybeDispatchReplyReady(completionMessage,
                                        completionPrevious, completionNext);
                            } catch (Throwable error) {
                                Main.log("reply-ready JSON patch check failed: "
                                        + Main.safeThrowableMessage(error));
                            }
                        }
                        return result;
                    }
                });
                n++;
            }
            Main.log("hooked mv.i x" + n + " (content-filter guard)");
        } catch (Throwable t) { Main.log("hookContentFilterApply failed: " + t); }
    }

    private static void forceLiveRegenerateAllowedV241(Object message) {
        if (!HostCompat.isV241() || message == null
                || !HostCompat.name("mv").equals(message.getClass().getSimpleName())) return;
        try {
            Field stateField = message.getClass().getDeclaredField("r");
            stateField.setAccessible(true);
            Object state = stateField.get(message);
            if (state == null) return;
            for (Method method : state.getClass().getMethods()) {
                if (!"l".equals(method.getName())
                        || method.getParameterTypes().length != 1) continue;
                method.setAccessible(true);
                method.invoke(state, Boolean.FALSE);
                return;
            }
        } catch (Throwable error) {
            Main.log("force live regenerate failed: " + Main.safeThrowableMessage(error));
        }
    }

    // 诊断：dump 当前线程调用栈
    private static String dumpStack() {
        StringBuilder sb = new StringBuilder("[CF] stack:");
        int n = 0;
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String cn = e.getClassName();
            if (cn.startsWith("de.robv") || cn.startsWith("java.lang.reflect")
                    || cn.startsWith("LSPHooker")
                    || cn.startsWith("dalvik") || cn.startsWith("com.dsmod")) continue;
            sb.append("\n    ").append(cn).append('.').append(e.getMethodName());
            if (++n >= 25) break;
        }
        return sb.toString();
    }

    // 诊断：反射读取 mv 的 fragments 容器内容（mv.m = wv0, wv0.a = to7 list）
    private static String dumpMv(Object mvObj) {
        try {
            Field mf = mvObj.getClass().getDeclaredField(
                    HostCompat.staticMessageField(mvObj, "m"));
            mf.setAccessible(true);
            Object wv0 = mf.get(mvObj);
            Field af = wv0.getClass().getDeclaredField("a");
            af.setAccessible(true);
            List<?> list = (List<?>) af.get(wv0);
            StringBuilder sb = new StringBuilder("frags=" + list.size());
            for (int i = 0; i < list.size() && i < 4; i++) {
                String s = String.valueOf(list.get(i));
                if (s.length() > 100) s = s.substring(0, 100) + "…";
                sb.append(" [").append(i).append("]").append(s);
            }
            return sb.toString();
        } catch (Throwable t) { return "dumpMv err:" + t; }
    }

    // 诊断：抓 vv7.e()（把服务端 kv 反序列化成全新 mv 消息对象）
    void installMsgRebuildCapture(ClassLoader cl) {
        try {
            Class<?> k = HostCompat.load(cl, "vv7");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals("e") || m.getParameterTypes().length != 1) continue;
                Main.MODULE.hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object r = chain.proceed();
                        try {
                            if (Main.isSrvLog() && r != null) Main.srvLog("[VV7] new mv " + dumpMv(r));
                        } catch (Throwable t) { Main.srvLog("[VV7] err " + t); }
                        return r;
                    }
                });
                n++;
            }
            Main.log("installed msg-rebuild capture on vv7.e x" + n);
        } catch (Throwable t) { Main.log("installMsgRebuildCapture failed: " + t); }
    }

    // 第二拦截点：mv.S(status)/mv.R(quasi_status) 直接状态写入
    void hookStatusWrite(ClassLoader cl) {
        try {
            Class<?> k = HostCompat.load(cl, "mv");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                final String mn = m.getName();
                if (!mn.equals(HostCompat.messageMethod("S"))
                        && !mn.equals(HostCompat.messageMethod("R"))) continue;
                final boolean primaryStatus = mn.equals(HostCompat.messageMethod("S"));
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 1 || pts[0] != String.class) continue;
                Main.MODULE.hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object message = chain.getThisObject();
                        String previousStatus = callStr(
                                message, primaryStatus ? "D" : "x");
                        String nextStatus = "";
                        try {
                            Object a0 = chain.getArg(0);
                            String v = a0 instanceof String ? (String) a0 : "";
                            nextStatus = v;
                            boolean cf = v.contains("CONTENT_FILTER");
                            String statusDetail = v.length() > 140
                                    ? v.substring(0, 137) + "…" : v;
                            String statusLower = statusDetail.toLowerCase(Locale.US);
                            boolean statusFailure = statusLower.contains("fail")
                                    || statusLower.contains("error")
                                    || statusLower.contains("interrupt");
                            boolean statusSuccess = statusLower.contains("complete")
                                    || statusLower.contains("success")
                                    || statusLower.contains("finish")
                                    || statusLower.contains("done");
                            // App 内点「停止」会把消息状态写成 interrupt/stopped/cancelled。
                            // 本地 API 的独立原生流不受 App UI 停止影响，这里显式取消它。
                            if (statusLower.contains("interrupt")
                                    || statusLower.contains("stopped")
                                    || statusLower.contains("cancelled")) {
                                cancelActiveLocalApiStream("host " + statusDetail);
                            }
                            HookLogOverlay.event(statusFailure ? "ERROR" : "HOST",
                                    statusFailure ? "Send or response failed"
                                            : statusSuccess ? "Send or response completed"
                                            : "Message status changed",
                                    "field=" + mn + " value=" + statusDetail);
                            boolean noCensor = isNoCensorForMessage(message);
                            if (Main.isSrvLog()) Main.srvLog("[SR] mv." + mn + "(" + v + ") nocensor=" + noCensor);
                            if (cf && noCensor) {
                                markFilteredOriginal(cl, chain.getThisObject(), "mv." + mn);
                                Main.log("blocked mv." + mn + "(" + v + ")");
                                if (Main.isSrvLog()) Main.srvLog("[SR] blocked mv." + mn);
                                return null;
                            }
                        } catch (Throwable t) { Main.log("status-write block err: " + t); }
                        Object result = chain.proceed();
                        try {
                            maybeAutoContinue(message, previousStatus, nextStatus);
                        } catch (Throwable t) {
                            Main.log("auto-continue check failed: "
                                    + Main.safeThrowableMessage(t));
                        }
                        if (primaryStatus) {
                            try {
                                maybeContinueV241AgentThinkOnlyResult(
                                        message, previousStatus, nextStatus);
                            } catch (Throwable t) {
                                Main.log("code257 Agent result continuation check failed: "
                                        + Main.safeThrowableMessage(t));
                            }
                        }
                        try {
                            maybeDispatchReplyReady(
                                    message, previousStatus, nextStatus);
                        } catch (Throwable t) {
                            Main.log("reply-ready completion check failed: "
                                    + Main.safeThrowableMessage(t));
                        }
                        return result;
                    }
                });
                n++;
            }
            Main.log("hooked mv.S/R x" + n + " (status-write guard)");
        } catch (Throwable t) { Main.log("hookStatusWrite failed: " + t); }
    }

    private static void maybeAutoContinue(
            final Object message, String previousStatus, String nextStatus) {
        if (message == null) return;
        String role = callStr(message, "A");
        Integer messageId = callInt(message, "u");
        String sid = findNativeSessionContainingMessage(message);
        String responseId = (sid == null ? "unknown" : sid) + ":"
                + (messageId == null
                ? Integer.toHexString(System.identityHashCode(message)) : messageId);

        // A successful native resume moves the same message back to WIP/CHECKING. Re-arm the
        // response here so a later server pause in the same long answer can also be resumed.
        if ("WIP".equals(nextStatus) || "CHECKING".equals(nextStatus)) {
            AUTO_CONTINUE_DISPATCHED.remove(responseId);
            return;
        }
        boolean nativePaused = AutoContinuePolicy.shouldResume(
                isAutoContinueEnabled(), previousStatus, nextStatus, role);
        boolean likelyTruncated = false;
        if (!nativePaused && isAutoContinueEnabled()
                && "ASSISTANT".equals(role)
                && ReplyReadyPolicy.isGenerating(previousStatus)
                && ReplyReadyPolicy.isCompleted(nextStatus)) {
            String visible = visibleAssistantMessageText(message);
            AtomicInteger count = AUTO_CONTINUE_TRUNCATION_ATTEMPTS.get(responseId);
            int attempts = count == null ? 0 : count.get();
            likelyTruncated = attempts < 2
                    && z13.looksStructurallyTruncatedText(visible);
        }
        if (!nativePaused && !likelyTruncated) return;
        if (!HookSessionManagement.isUsableSessionId(sid)) {
            Main.log("auto-continue skipped: active conversation was not found msg="
                    + String.valueOf(messageId));
            return;
        }
        final WeakReference<Object> reference = HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.get(sid);
        final Object viewModel = reference == null ? null : reference.get();
        if (reference != null && viewModel == null) {
            HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.remove(sid, reference);
        }
        if (viewModel == null) {
            Main.log("auto-continue skipped: chat ViewModel is unavailable sid=" + sid);
            return;
        }
        if (AUTO_CONTINUE_DISPATCHED.putIfAbsent(
                responseId, SystemClock.elapsedRealtime()) != null) return;
        if (likelyTruncated) {
            AUTO_CONTINUE_TRUNCATION_ATTEMPTS.computeIfAbsent(responseId,
                    key -> new AtomicInteger()).incrementAndGet();
            Main.log("auto-continue detected structurally truncated native reply sid=" + sid
                    + " msg=" + String.valueOf(messageId));
        }

        final String dispatchSid = sid;
        final String dispatchId = responseId;
        final Integer dispatchMessageId = messageId;
        Handler handler = Main.currentMainHandler();
        if (handler == null) {
            AUTO_CONTINUE_DISPATCHED.remove(dispatchId);
            return;
        }
        handler.post(new Runnable() {
            @Override public void run() {
                if (!isAutoContinueEnabled()) {
                    AUTO_CONTINUE_DISPATCHED.remove(dispatchId);
                    return;
                }
                try {
                    dispatchNativeResume(viewModel, message);
                    Main.log("auto-continued native response sid=" + dispatchSid
                            + " msg=" + String.valueOf(dispatchMessageId)
                            + " event=" + HostCompat.resumeMessageEventClass());
                } catch (Throwable error) {
                    AUTO_CONTINUE_DISPATCHED.remove(dispatchId);
                    Main.log("auto-continue native dispatch failed sid=" + dispatchSid
                            + ": " + Main.safeThrowableMessage(error));
                }
            }
        });
    }

    /**
     * code257 sometimes closes a hidden tool-result child as FINISHED after THINK, before the
     * model emits either a RESPONSE or its next control block. The database then looks healthy
     * but the UI says the generation stopped. Resume only the exact assistant child covered by a
     * freshly sent tool-result lease; consume the lease for every normal RESPONSE as well.
     */
    private static void maybeContinueV241AgentThinkOnlyResult(
            final Object message, String previousStatus, String nextStatus) {
        if (!HostCompat.isV241() || message == null
                || !ReplyReadyPolicy.isGenerating(previousStatus)
                || !ReplyReadyPolicy.isCompleted(nextStatus)
                || !"ASSISTANT".equals(callStr(message, "A"))) return;
        String sid = findNativeSessionContainingMessage(message);
        if (!HookSessionManagement.isUsableSessionId(sid)) {
            sid = HeartbeatToolProtocol.cleanScope(lastInteractiveConversationId);
        }
        if (!HookSessionManagement.isUsableSessionId(sid)) return;
        Long expiry = V241_AGENT_RESULT_CONTINUATIONS.get(sid);
        if (expiry == null) return;
        if (expiry.longValue() < System.currentTimeMillis()) {
            V241_AGENT_RESULT_CONTINUATIONS.remove(sid, expiry);
            return;
        }
        List fragments = messageFragments(message);
        boolean thinking = false;
        boolean response = false;
        if (fragments != null) {
            for (Object fragment : fragments) {
                String type = String.valueOf(Main.readHostField(fragment, "a"));
                if ("THINK".equals(type)) thinking = true;
                if ("RESPONSE".equals(type) || "TEMPLATE_RESPONSE".equals(type)) {
                    response = true;
                }
            }
        }
        if (!V241_AGENT_RESULT_CONTINUATIONS.remove(sid, expiry)) return;
        if (!thinking || response) return;
        final WeakReference<Object> reference = HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.get(sid);
        final Object viewModel = reference == null ? null : reference.get();
        if (viewModel == null) {
            Main.log("code257 Agent think-only result could not resume: ViewModel unavailable sid="
                    + sid);
            return;
        }
        final String dispatchSid = sid;
        final Integer messageId = callInt(message, "u");
        Handler handler = Main.currentMainHandler();
        if (handler == null) return;
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    dispatchNativeResume(viewModel, message);
                    Main.log("code257 resumed Agent result with THINK only sid=" + dispatchSid
                            + " msg=" + String.valueOf(messageId));
                } catch (Throwable error) {
                    Main.log("code257 Agent think-only resume failed sid=" + dispatchSid
                            + ": " + Main.safeThrowableMessage(error));
                }
            }
        });
    }

    private static void dispatchNativeResume(Object viewModel, Object message) throws Throwable {
        ClassLoader loader = Main.hostClassLoader;
        if (loader == null) throw new IllegalStateException("host class loader unavailable");
        Class<?> eventType = Class.forName(
                HostCompat.resumeMessageEventClass(), false, loader);
        Constructor<?> eventConstructor = null;
        for (Constructor<?> candidate : eventType.getDeclaredConstructors()) {
            Class<?>[] parameterTypes = candidate.getParameterTypes();
            if (parameterTypes.length == 1
                    && parameterTypes[0].isAssignableFrom(message.getClass())) {
                eventConstructor = candidate;
                break;
            }
        }
        if (eventConstructor == null) {
            throw new NoSuchMethodException("native resume event constructor");
        }
        eventConstructor.setAccessible(true);
        Object event = eventConstructor.newInstance(message);

        Method eventHandler = null;
        for (Method candidate : viewModel.getClass().getDeclaredMethods()) {
            Class<?>[] parameterTypes = candidate.getParameterTypes();
            if (HostCompat.resumeMessageHandlerMethod().equals(candidate.getName())
                    && parameterTypes.length == 1
                    && parameterTypes[0].isAssignableFrom(eventType)) {
                eventHandler = candidate;
                break;
            }
        }
        if (eventHandler == null) {
            throw new NoSuchMethodException("native resume event handler");
        }
        eventHandler.setAccessible(true);
        eventHandler.invoke(viewModel, event);
    }

    private static void maybeDispatchReplyReady(
            Object message, String previousStatus, String nextStatus) {
        if (message == null) return;
        if (!isReplyReadyNotificationsEnabled()) return;
        String role = callStr(message, "A");
        if (!ReplyReadyPolicy.shouldNotify(previousStatus, nextStatus,
                role, Main.isDeepSeekForeground())) return;

        String sid = findNativeSessionContainingMessage(message);
        if (sid != null && Main.PENDING_NATIVE_UI_HEARTBEATS.containsKey(sid)) {
            // Proactive heartbeats own their existing notification path.
            return;
        }
        Integer messageId = callInt(message, "u");
        String responseId = (sid == null ? "unknown" : sid) + ":"
                + (messageId == null
                ? Integer.toHexString(System.identityHashCode(message)) : messageId);
        long now = SystemClock.elapsedRealtime();
        for (Map.Entry<String, Long> entry : REPLY_READY_DISPATCHED.entrySet()) {
            Long at = entry.getValue();
            if (at == null || now - at.longValue() > REPLY_READY_DEDUPE_MS) {
                REPLY_READY_DISPATCHED.remove(entry.getKey(), at);
            }
        }
        if (REPLY_READY_DISPATCHED.putIfAbsent(responseId, now) != null) return;

        Context context = HookSessionManagement.currentHostContext();
        if (context == null) {
            REPLY_READY_DISPATCHED.remove(responseId);
            return;
        }
        dispatchReplyReady(context, sid, responseId);
        Main.log("background reply-ready dispatched sid=" + String.valueOf(sid)
                + " msg=" + String.valueOf(messageId));
    }

    // 诊断：hook h83.h(l84) fragment 反序列化选择器
    void hookTemplateProbe(ClassLoader cl) {
        try {
            // code257's polymorphic fragment serializer selector is ii3.h(ak4).
            // The legacy h83 key is retained unchanged outside this branch.
            Class<?> k = HostCompat.isV241()
                    ? cl.loadClass("ii3") : HostCompat.load(cl, "h83");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals("h")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 1) continue;
                Main.MODULE.hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            if (Main.isSrvLog()) {
                                String v = String.valueOf(chain.getArg(0));
                                if (v.contains("TEMPLATE_RESPONSE")) {
                                    Main.srvLog("[TPL] h83.h TEMPLATE_RESPONSE seen");
                                    Main.srvLog(dumpStack());
                                }
                            }
                        } catch (Throwable t) { Main.srvLog("[TPL] err " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            Main.log("hooked h83.h x" + n + " (template probe)");
        } catch (Throwable t) { Main.log("hookTemplateProbe failed: " + t); }
    }

    // ── close 后整表合并 tp.u(tp, List) ──────────────────
    void hookFinalMessageMerge(ClassLoader cl) {
        try {
            final Class<?> tpk = HostCompat.load(cl, "tp");
            final Field fField = tpk.getDeclaredField(HostCompat.sessionMessageMapField());
            fField.setAccessible(true);
            int n = 0;
            for (Method m : tpk.getDeclaredMethods()) {
                if (!m.getName().equals(HostCompat.sessionMergeMethod())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 2 || !List.class.isAssignableFrom(pts[1])) continue;
                Main.MODULE.hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object tp = chain.getArg(0);
                            Object rawList = chain.getArg(1);
                            if (tp != null && rawList instanceof List) {
                                List<?> list = (List<?>) rawList;
                                String sid = String.valueOf(Main.readHostField(tp, "a"));
                                Map<?, ?> fmap = null;
                                try { fmap = (Map<?, ?>) fField.get(tp); } catch (Throwable ignored) {}
                                boolean nc = Main.isNoCensorForSession(sid);
                                ArrayList<Object> copy = new ArrayList<>(list);
                                boolean changed = false;
                                for (int i = 0; i < copy.size(); i++) {
                                    Object msg = copy.get(i);
                                    if (msg == null) continue;
                                    preservePendingFilteredOriginal(cl, tp, msg);
                                    String status = callStr(msg, "D");
                                    String quasi = callStr(msg, "x");
                                    Integer id = callInt(msg, "u");
                                    boolean marked = FILTERED_ORIGINAL_MESSAGES.containsKey(msg);
                                    boolean filterState = containsContentFilterState(status, quasi);
                                    if (nc && !filterState && !marked
                                            && !ResponsePreserver.isFilteredHostMessage(msg)) {
                                        ResponsePreserver.saveHostMessage(cl, sid, msg);
                                    }
                                    if (Main.isSrvLog()) {
                                        Main.srvLog("[FM] merge idx=" + i + " id=" + id
                                                + " status=" + status + " quasi=" + quasi
                                                + " filterState=" + filterState
                                                + " marked=" + marked);
                                    }
                                    // The host calls this merge for every normal completion.  P()
                                    // serialises all fragments, so probing every ordinary message
                                    // here delays the initial INTERRUPTED -> WIP transition on
                                    // 2.3.4.  A real replacement is already identified by either
                                    // its status or the earlier JSON-patch marker.
                                    if (!nc || (!filterState && !marked)) continue;
                                    boolean cf = filterState
                                            || ResponsePreserver.isFilteredHostMessage(msg);
                                    Object existing = id != null && fmap != null ? fmap.get(id) : null;
                                    if (existing != null && existing != msg) {
                                        preservePendingFilteredOriginal(cl, tp, existing);
                                    }
                                    Object durable = nc
                                            ? ResponsePreserver.restoreHostMessage(cl, sid, msg) : null;
                                    if (durable != null) {
                                        copy.set(i, durable);
                                        changed = true;
                                        Main.log("restored preserved response sid=" + sid + " msg=" + id
                                                + " before final merge");
                                        if (Main.isSrvLog()) Main.srvLog("[FM] restored durable id=" + id);
                                        continue;
                                    }
                                    if (!cf || !nc || id == null || existing == null) continue;
                                    if (existing == null || existing == msg) continue;
                                    String exStatus = callStr(existing, "D");
                                    String exQuasi = callStr(existing, "x");
                                    boolean exCf = ResponsePreserver.isFilteredHostMessage(existing);
                                    if (exCf) continue;
                                    copy.set(i, existing);
                                    changed = true;
                                    Main.log("kept original msg id=" + id + " over CONTENT_FILTER");
                                    if (Main.isSrvLog()) Main.srvLog("[FM] kept original id=" + id
                                            + " origStatus=" + exStatus);
                                }
                                if (changed) {
                                    Object[] args = chain.getArgs().toArray();
                                    args[1] = copy;
                                    return chain.proceed(args);
                                }
                            }
                        } catch (Throwable t) { Main.log("final-merge guard err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            Main.log("hooked tp.u x" + n + " (final-merge guard)");
        } catch (Throwable t) { Main.log("hookFinalMessageMerge failed: " + t); }
    }

    // ── 单条消息替换拦截：tp.q(uo)/tp.p(uo,String)/tp.a(uo,bool) ─────────
    void hookFinalMessageApply(ClassLoader cl) {
        try {
            final Class<?> tpk = HostCompat.load(cl, "tp");
            final Field fField = tpk.getDeclaredField(HostCompat.sessionMessageMapField());
            fField.setAccessible(true);
            final Class<?> uok = HostCompat.load(cl, "uo");
            int n = 0;
            for (Method m : tpk.getDeclaredMethods()) {
                final String mn = m.getName();
                if (!mn.equals(HostCompat.sessionReplaceMethod())
                        && !mn.equals(HostCompat.sessionReplaceWithTextMethod())
                        && !mn.equals("a")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length < 1
                        || (!HostCompat.isV241() && !uok.isAssignableFrom(pts[0]))) continue;
                Main.MODULE.hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object tp = chain.getThisObject();
                            Object msg = chain.getArg(0);
                            if (tp != null && msg != null) {
                                String sid = String.valueOf(Main.readHostField(tp, "a"));
                                preservePendingFilteredOriginal(cl, tp, msg);
                                String status = callStr(msg, "D");
                                String quasi = callStr(msg, "x");
                                Integer id = callInt(msg, "u");
                                boolean marked = FILTERED_ORIGINAL_MESSAGES.containsKey(msg);
                                boolean filterState = containsContentFilterState(status, quasi);
                                if (Main.isSrvLog())
                                    Main.srvLog("[FA] tp." + mn + " id=" + id + " status=" + status
                                            + " quasi=" + quasi
                                            + " filterState=" + filterState
                                            + " marked=" + marked);
                                // pq.a/t/s are also the hot path for optimistic insertion and
                                // streaming replacement.  Do not serialise ordinary messages at
                                // this boundary; only a proven filter event needs preservation.
                                if (!Main.isNoCensorForSession(sid) || (!filterState && !marked)) {
                                    return chain.proceed();
                                }
                                boolean cf = filterState
                                        || ResponsePreserver.isFilteredHostMessage(msg);
                                if (cf && Main.isNoCensorForSession(sid) && id != null) {
                                    Map<?, ?> fmap = (Map<?, ?>) fField.get(tp);
                                    Object existing = fmap != null ? fmap.get(id) : null;
                                    if (existing != null && existing != msg) {
                                        preservePendingFilteredOriginal(cl, tp, existing);
                                    }
                                    Object durable = ResponsePreserver.restoreHostMessage(cl, sid, msg);
                                    if (durable != null) {
                                        Object[] args = chain.getArgs().toArray();
                                        args[0] = durable;
                                        Main.log("restored preserved response sid=" + sid + " msg=" + id
                                                + " in tp." + mn);
                                        if (Main.isSrvLog()) Main.srvLog("[FA] restored durable id=" + id);
                                        return chain.proceed(args);
                                    }
                                    if (existing != null && existing != msg) {
                                        String exS = callStr(existing, "D");
                                        String exQ = callStr(existing, "x");
                                        boolean exCf = ResponsePreserver.isFilteredHostMessage(existing);
                                        if (!exCf) {
                                            Object[] args = chain.getArgs().toArray();
                                            args[0] = existing;
                                            Main.log("tp." + mn + " kept original id=" + id + " over CONTENT_FILTER");
                                            if (Main.isSrvLog()) Main.srvLog("[FA] kept original id=" + id + " origStatus=" + exS);
                                            return chain.proceed(args);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) { Main.log("final-apply guard err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            Main.log("hooked tp.q/p/a x" + n + " (final-apply guard)");
        } catch (Throwable t) { Main.log("hookFinalMessageApply failed: " + t); }
    }

    // 反射调用无参方法返回字符串（uo.D()=status / uo.x()=quasi_status）
    private static String callStr(Object obj, String method) {
        try {
            Method m = obj.getClass().getMethod(
                    HostCompat.messageMethod(method));
            Object r = m.invoke(obj);
            return r == null ? null : String.valueOf(r);
        } catch (Throwable t) { return null; }
    }

    private static boolean containsContentFilterState(String status, String quasi) {
        return (status != null && status.contains("CONTENT_FILTER"))
                || (quasi != null && quasi.contains("CONTENT_FILTER"));
    }

    // 反射调用无参方法返回 int（uo.u()=消息id）
    private static Integer callInt(Object obj, String method) {
        try {
            Method m = obj.getClass().getMethod(
                    HostCompat.messageMethod(method));
            Object r = m.invoke(obj);
            if (r instanceof Integer) return (Integer) r;
            if (r instanceof Number) return ((Number) r).intValue();
            return null;
        } catch (Throwable t) { return null; }
    }

    /**
     * A live mv still contains the uncensored text when the replacement patch arrives.  Keep a
     * weak marker immediately, then save the host's exact static kv as soon as its owning tp/SID
     * is known.  No message content is written to diagnostics.
     */
    private static void markFilteredOriginal(ClassLoader cl, Object message, String source) {
        if (message == null) return;
        FILTERED_ORIGINAL_MESSAGES.put(message, Boolean.TRUE);
        if (HostCompat.isV241()
                && V241_FILTERED_ORIGINAL_SNAPSHOTTED.containsKey(message)) return;
        // DeepSeek 2.3.6 sends CONTENT_FILTER while the live assistant is still WIP and then
        // omits the normal FINISHED transition.  Keeping the original fragments but leaving the
        // message WIP makes the host treat it as an abandoned/retracted response on the next
        // render and on database reload.  Promote only this already-proven filtered response to
        // the normal terminal state before serialising its durable copy; ordinary WIP messages
        // never enter this path.
        finishPreservedResponse(message);
        String sid = findNativeSessionContainingMessage(message);
        // On 2.4.1 the JSON patch can arrive before mv is inserted into tp's
        // active-message map, so identity lookup is temporarily empty. The
        // currently interactive SID is the host's authoritative conversation
        // at this boundary; use it only for the exact V241 path.
        if (sid == null && HostCompat.isV241()) {
            sid = HookSessionManagement.sidebarCurrentSid != null && HookSessionManagement.sidebarCurrentSid.length() > 0
                    ? HookSessionManagement.sidebarCurrentSid : lastInteractiveConversationId;
            if (sid != null && sid.length() > 0) {
                Main.log("code257 filtered response SID fallback to active conversation");
            }
        }
        if (sid != null) {
            boolean saved = ResponsePreserver.saveHostMessage(cl, sid, message);
            boolean textFallback = !saved && HostCompat.isV241()
                    && ResponsePreserver.saveFilteredResponseTextV241(sid, message);
            if (saved || textFallback) {
                if (HostCompat.isV241()) {
                    V241_FILTERED_ORIGINAL_SNAPSHOTTED.put(message, Boolean.TRUE);
                }
                Integer messageId = callInt(message, "u");
                if (saved && messageId != null) {
                    ResponsePreserver.markFiltered(sid, messageId.intValue());
                }
                Main.log((textFallback ? "preserved code257 response text" :
                        "preserved original response") + " sid=" + sid + " msg="
                        + callInt(message, "u") + " after " + source);
            } else {
                Main.log("preserve filtered response failed sid=" + sid + " msg="
                        + callInt(message, "u") + " after " + source);
            }
        }
    }

    private static void finishPreservedResponse(Object message) {
        try {
            String status = callStr(message, "D");
            if ("FINISHED".equals(status)) return;
            Method setter = message.getClass().getMethod(
                    HostCompat.messageMethod("S"), String.class);
            setter.setAccessible(true);
            setter.invoke(message, "FINISHED");
            Main.log("completed preserved response msg=" + callInt(message, "u")
                    + " previousStatus=" + status);
        } catch (Throwable t) {
            Main.log("complete preserved response failed: " + t);
        }
    }

    private static void preservePendingFilteredOriginal(ClassLoader cl, Object session,
                                                         Object message) {
        if (session == null || message == null
                || !FILTERED_ORIGINAL_MESSAGES.containsKey(message)) return;
        String sid = String.valueOf(Main.readHostField(session, "a"));
        if (ResponsePreserver.saveHostMessage(cl, sid, message)) {
            if (HostCompat.isV241()) {
                V241_FILTERED_ORIGINAL_SNAPSHOTTED.put(message, Boolean.TRUE);
            }
            Integer messageId = callInt(message, "u");
            if (messageId != null) ResponsePreserver.markFiltered(sid, messageId.intValue());
            FILTERED_ORIGINAL_MESSAGES.remove(message);
            Main.log("finalized preserved response sid=" + sid + " msg=" + callInt(message, "u"));
        }
    }

    private static String findNativeSessionContainingMessage(Object message) {
        try {
            for (Map.Entry<String, WeakReference<Object>> entry
                    : HookSessionManagement.ACTIVE_CHAT_SESSIONS.entrySet()) {
                WeakReference<Object> reference = entry.getValue();
                Object session = reference == null ? null : reference.get();
                if (session == null) {
                    HookSessionManagement.ACTIVE_CHAT_SESSIONS.remove(entry.getKey(), reference);
                } else if (nativeSessionContainsMessage(session, message)) {
                    return entry.getKey();
                }
            }
        } catch (Throwable ignored) {}
        Object sessions = HookSessionManagement.NATIVE_SESSION_LIST;
        if (sessions instanceof List) {
            try {
                for (Object session : new ArrayList<Object>((List) sessions)) {
                    if (nativeSessionContainsMessage(session, message)) {
                        return String.valueOf(Main.readHostField(session, "a"));
                    }
                }
            } catch (Throwable ignored) {}
        }
        synchronized (HookSessionManagement.LOCAL_NATIVE_SESSIONS) {
            try {
                for (Map.Entry<String, Object> entry : HookSessionManagement.LOCAL_NATIVE_SESSIONS.entrySet()) {
                    if (nativeSessionContainsMessage(entry.getValue(), message)) {
                        return entry.getKey();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean nativeSessionContainsMessage(Object session, Object message) {
        Object messages = Main.readHostField(session, "f");
        if (!(messages instanceof Map)) return false;
        try {
            for (Object candidate : new ArrayList<Object>(((Map) messages).values())) {
                if (candidate == message) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private String readPrompt() {
        long now = SystemClock.uptimeMillis();
        String cached = cachedPromptText;
        if (now - cachedPromptTextAt < 1000L) return cached;
        try {
            File ef = new File(ENABLED_FILE);
            if (!ef.exists()) {
                cachedPromptText = null;
                cachedPromptTextAt = now;
                return null;
            }

            String linked = Main.readSmallText(PROMPT_LINK_FILE);
            if (linked != null && linked.length() > 0) {
                cachedPromptText = linked;
                cachedPromptTextAt = now;
                return linked;
            }

            String copied = Main.readSmallText(Main.PROMPT_FILE);
            if (copied != null && copied.length() > 0) {
                cachedPromptText = copied;
                cachedPromptTextAt = now;
                return copied;
            }
        } catch (Throwable t) { return null; }
        cachedPromptText = null;
        cachedPromptTextAt = now;
        return null;
    }

    /**
     * s11.a is the 2.3.0 message-list composable: it iterates the session sr7 straight off the
     * field and renders every cp as a bubble, bypassing aq.s(). Hidden transport messages are
     * removed from the list in place so the UI never shows them; the model context is
     * server-side (the completion request carries only the current message), so removal here
     * cannot break the agent loop.
     */
    void hookS11RenderFilter(final ClassLoader cl) {
        if (!HostCompat.isV230() || HostCompat.isV234()) return;
        try {
            Class<?> s11 = cl.loadClass("s11");
            int installed = 0;
            for (Method method : s11.getDeclaredMethods()) {
                if (!"a".equals(method.getName())) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length != 10) continue;
                if (!List.class.isAssignableFrom(types[4])) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object raw = chain.getArg(4);
                        if (raw instanceof List) {
                            List list = (List) raw;
                            int removed = 0;
                            for (int i = list.size() - 1; i >= 0; i--) {
                                if (Main.isHiddenAgentTransportUserMessage(list.get(i))) {
                                    try {
                                        list.remove(i);
                                        removed++;
                                    } catch (Throwable ignored) {}
                                }
                            }
                            if (removed > 0) {
                                Main.log("s11 render list=" + Integer.toHexString(
                                        System.identityHashCode(list))
                                        + " size=" + list.size()
                                        + " removedHidden=" + removed);
                            }
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("installed s11 render filter x" + installed);
        } catch (Throwable error) {
            Main.log("hook s11 render filter failed: " + error);
        }
    }

    /**
     * cs1.m renders the composer/attachment rows from an sr7 of mz0 items. The module's own
     * screenshot uploads appear there before their hidden message is sent; strip those rows so
     * the capture is never visible on the user side.
     */
    void hookCs1RenderFilter(final ClassLoader cl) {
        if (!HostCompat.isV230() || HostCompat.isV234()) return;
        try {
            Class<?> cs1 = cl.loadClass("cs1");
            int installed = 0;
            for (Method method : cs1.getDeclaredMethods()) {
                if (!"m".equals(method.getName())) continue;
                Class<?>[] types = method.getParameterTypes();
                if (types.length != 9) continue;
                if (!List.class.isAssignableFrom(types[0])) continue;
                Main.MODULE.hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object raw = chain.getArg(0);
                        if (raw instanceof List) {
                            List list = (List) raw;
                            int removed = 0;
                            for (int i = list.size() - 1; i >= 0; i--) {
                                if (isModuleHiddenAttachment(list.get(i))) {
                                    try {
                                        list.remove(i);
                                        removed++;
                                    } catch (Throwable ignored) {}
                                }
                            }
                            if (removed > 0) {
                                StringBuilder detail = new StringBuilder();
                                for (int i = 0; i < list.size() && i < 4; i++) {
                                    Object item = list.get(i);
                                    String name = "?";
                                    try {
                                        Object field = Main.readHostField(item, "a");
                                        if (field != null) name = String.valueOf(field);
                                    } catch (Throwable ignored) {}
                                    detail.append(" [").append(i).append("]")
                                            .append(item == null ? "null"
                                            : item.getClass().getSimpleName())
                                            .append(":").append(name);
                                }
                                Main.log("cs1.m list=" + Integer.toHexString(
                                        System.identityHashCode(list))
                                        + " size=" + list.size()
                                        + " removedHidden=" + removed
                                        + detail);
                            }
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            Main.log("installed cs1.m attachment filter x" + installed);
        } catch (Throwable error) {
            Main.log("hook cs1.m attachment filter failed: " + error);
        }
    }

    private static boolean isModuleHiddenAttachment(Object item) {
        if (item == null || Main.ACTIVE_HIDDEN_ATTACHMENT_NAMES.isEmpty()) return false;
        try {
            Object name = Main.readHostField(item, "a");
            return name instanceof String
                    && Main.ACTIVE_HIDDEN_ATTACHMENT_NAMES.contains(name);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * 2.3.0 moved message fragments from field t / method l() to field n (jw0, a List);
     * 2.2.x keeps the historical layout. Never translated as a single field by
     * staticMessageField, so probe the v230 layout first.
     */
    static List messageFragments(Object message) {
        if (message == null) return null;
        // In 2.3.4 mp.n() returns attachments while r() returns message fragments. Reading n()
        // made private Agent result messages look empty to the visible-thread filter.
        Object value = HostCompat.isV234() ? Main.invokeNoArg(message, "r") : null;
        if (!(value instanceof List)) value = Main.readHostField(message, "n");
        if (!(value instanceof List)) value = Main.readHostField(message, "t");
        if (!(value instanceof List)) {
            value = Main.invokeNoArg(message, HostCompat.messageMethod("l"));
        }
        return value instanceof List ? (List) value : null;
    }

    static String visibleAssistantMessageText(Object message) {
        List fragmentsValue = messageFragments(message);
        if (fragmentsValue == null) return "";
        StringBuilder text = new StringBuilder();
        for (Object fragment : fragmentsValue) {
            String type = String.valueOf(Main.readHostField(fragment, "a"));
            if (!"RESPONSE".equals(type)
                    && !"TEMPLATE_RESPONSE".equals(type)) continue;
            Object content = Main.readHostField(fragment, "c");
            if (!(content instanceof String)) continue;
            text.append((String) content);
        }
        return HookAccountLoginSecurity.normalizeProactiveMessage(
                HeartbeatToolProtocol.stripControlBlocks(text.toString()));
    }

    // 给定 transport 方法(签名 (rs0,Long)->Flow) 装上中继包装 hook
    void hookTransport(Method m) {
        Main.MODULE.hook(m).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                Object[] args = chain.getArgs().toArray();
                try { if (Main.liveR92 == null) Main.liveR92 = chain.getThisObject(); } catch (Throwable ignored) {}
                try {
                    Object request = args != null && args.length > 0 ? args[0] : null;
                    ensureV241EditedRequestPrompt(request);
                } catch (Throwable error) {
                    Main.log("code257 edit prompt safety-net failed: "
                            + Main.safeThrowableMessage(error));
                }
                try {
                    Object req = args != null && args.length > 0 ? args[0] : null;
                    List fps = Main.tlPendingFps.get();
                    String effectiveModel = Main.tlPendingModel.get();
                    Main.tlPendingFps.remove();
                    Main.tlPendingModel.remove();
                    if (req != null) {
                        if (fps != null) ew0Fps.put(req, fps);
                        if (effectiveModel != null) ew0EffectiveModels.put(req, effectiveModel);
                    }
                } catch (Throwable ignored) {}
                Object r = chain.proceed();
                try {
                    Object reqObj = args != null && args.length > 0 ? args[0] : null;
                    // 关键：不能把返回值换成 Proxy（宿主会按声明返回类型强转并闪退）。
                    // 改为：原样返回真实 b41，但把该 b41 实例登记下来；等它被 collect(b41.b) 时再跑中继。
                    registerRelayFlow(reqObj, r, chain.getThisObject());
                } catch (Throwable t) { Main.extLog("[RELAY] register err " + t + "\n" + Main.stackToString(t)); }
                return r;
            }
        });
    }

    /**
     * Exact 2.4.1/code257 edit/resend adapter. Some long-press Edit paths rebuild az0 through a
     * host copy/coroutine boundary which does not re-enter the ordinary constructor interception
     * on every ART build. ai2.b is the common final boundary. Repair only a request carrying a
     * parent message id and only when its prompt has no wrapper already.
     */
    private void ensureV241EditedRequestPrompt(Object request) throws Throwable {
        if (!HostCompat.isV241() || request == null
                || V241_LOCAL_API_REQUEST_OBJECTS.containsKey(request)) return;
        Object parent = Main.readHostField(request, "b");
        if (!(parent instanceof Number) || ((Number) parent).intValue() <= 0) return;
        Object promptValue = Main.readHostField(request, "c");
        if (!(promptValue instanceof String)) return;
        String prompt = (String) promptValue;
        if (HistoryBridge.stripInjectedSystemPrompts(prompt).length() != prompt.length()) return;
        String systemPrompt = readPrompt();
        if (systemPrompt == null || systemPrompt.trim().length() == 0) return;

        Field promptField = null;
        for (Class<?> type = request.getClass(); type != null && promptField == null;
             type = type.getSuperclass()) {
            try { promptField = type.getDeclaredField("c"); }
            catch (NoSuchFieldException ignored) {}
        }
        if (promptField == null || promptField.getType() != String.class) return;
        promptField.setAccessible(true);
        promptField.set(request, HistoryBridge.wrapSystemPrompt(systemPrompt, prompt));
        Main.log("code257 edit/resend prompt restored at transport boundary");
    }

    // 4) 捕获一个活着的 q71（completion PoW 管理器）实例
    void installPowManagerCapture(ClassLoader cl) {
        try {
            Class<?> q71 = HostCompat.load(cl, "q71");
            int n = 0;
            for (Constructor<?> ctor : q71.getDeclaredConstructors()) {
                Main.MODULE.hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        captureApiManagers(chain.getThisObject());
                        return result;
                    }
                });
                n++;
            }
            for (Method m : q71.getDeclaredMethods()) {
                String nm = m.getName();
                if ((nm.equals("j") || nm.equals("b")) && m.getParameterTypes().length == 1) {
                    Main.MODULE.hook(m).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            captureApiManagers(chain.getThisObject());
                            return chain.proceed();
                        }
                    });
                    n++;
                }
            }
            Main.log("installed pow manager capture on q71 x" + n);
        } catch (Throwable t) { Main.log("installPowManagerCapture failed: " + t); }
    }

    private static void captureApiManagers(Object q71) {
        if (q71 == null) return;
        boolean firstQ = Main.liveQ71 == null;
        Main.liveQ71 = q71;
        Object transport = MainReflectionSupport.fieldByName(q71, "f");
        boolean firstTransport = Main.liveR92 == null && transport != null;
        if (transport != null) Main.liveR92 = transport;
        if (firstQ || firstTransport) {
            Main.extLog("[VP] captured API managers q71=" + (Main.liveQ71 != null)
                    + " transport=" + (Main.liveR92 != null));
        }
    }

    // ── ★正式功能：expert 模式带图 → 后台视觉描述中继（同步就地改写请求）────────
    private boolean relayGateMatches(Object reqObj) {
        if (!HookAttachmentPipeline.isExpertRelayEnabled()) return false;
        if (reqObj == null) return false;
        // One-shot association: every transport call consumes the send-point model captured for this request.
        String capturedModel = ew0EffectiveModels.remove(reqObj);
        if (!HostCompat.simpleNameIs(reqObj, "ew0")) return false;
        Object files = MainReflectionSupport.fieldByName(reqObj, "d");
        boolean hasFiles = files instanceof java.util.List && !((java.util.List) files).isEmpty();
        // Do not feed documents, archives or source files to the vision endpoint.  Their remote
        // file ids stay on the original expert request, preserving byte-for-byte host upload and
        // the native file parser.  The captured fp list is the host's authoritative MIME flag.
        List capturedFiles = ew0Fps.get(reqObj);
        boolean hasImages = HookAttachmentPipeline.countImageFpList(capturedFiles) > 0;
        Object explicitModel = MainReflectionSupport.fieldByName(reqObj, "i");
        boolean matches = ExpertRelayGate.matches(explicitModel, capturedModel,
                hasFiles && hasImages);
        if (hasFiles && !hasImages) {
            Main.extLog("[RELAY] native document passthrough req="
                    + System.identityHashCode(reqObj) + " files=" + ((List) files).size());
        }
        if (matches && explicitModel == null) {
            Main.extLog("[RELAY] 续轮 model_type=null，使用发送点 effectiveModel=" + capturedModel
                    + " req=" + System.identityHashCode(reqObj)
                    + " parent=" + (MainReflectionSupport.fieldByName(reqObj, "b") != null)
                    + " files=" + ((List) files).size());
        }
        return matches;
    }

    // 命中 expert+图片时：不改返回值（避免宿主把 Proxy 强转 b41 而 CCE），
    // 只把真实 b41 实例登记下来，交给 b41.b 的 collect hook 处理。
    private void registerRelayFlow(Object reqObj, Object flow, Object r92This) {
        if (!relayGateMatches(reqObj)) return;
        synchronized (relaySeen) {
            if (relaySeen.contains(reqObj)) return;
            relaySeen.add(reqObj);
        }
        final Object r92 = (r92This != null) ? r92This : Main.liveR92;
        if (r92 == null || flow == null) { Main.extLog("[RELAY] register skip: r92/flow null"); return; }
        synchronized (HookAttachmentPipeline.INSTANCE.relayFlowMap) { HookAttachmentPipeline.INSTANCE.relayFlowMap.put(flow, new Object[]{ reqObj, r92 }); }
        Main.extLog("[RELAY] 已登记冷 Flow=" + System.identityHashCode(flow)
                + "，等下游 collect(b41.b) 时跑中继");
    }

    static boolean isLocalApiLogMuted() {
        return LOCAL_API_LOG_MUTE_DEPTH.get() > 0;
    }

    /**
     * The previous implementation only consulted the global switch, so enabling the Local API
     * switch did not protect its CONTENT_FILTER replacement path. Never broaden this to ordinary
     * chats: an API flag may affect a message only when its session is an internal API session.
     */
    private static boolean isNoCensorForMessage(Object message) {
        if (isNoCensor()) return true;
        if (!isLocalApiNoCensor() || message == null) return false;
        String sid = findNativeSessionContainingMessage(message);
        return Main.isNoCensorForSession(sid);
    }

    public static void cancelLocalApiCancellationJob(Object job) {
        if (job == null) return;
        for (Method method : job.getClass().getMethods()) {
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length != 1
                    || !java.util.concurrent.CancellationException.class
                            .isAssignableFrom(parameters[0])) continue;
            try {
                method.setAccessible(true);
                method.invoke(job, new java.util.concurrent.CancellationException(
                        "local API client disconnected"));
                return;
            } catch (Throwable ignored) {}
        }
    }

    /** App 内点「停止」时取消本地 API 当前活动生成流，避免独立原生流继续输出。 */
    private static void cancelActiveLocalApiStream(String reason) {
        Object job = Main.ACTIVE_LOCAL_API_CANCEL_JOB;
        if (job == null) return;
        cancelLocalApiCancellationJob(job);
        Main.log("local API stream cancelled by host (" + reason + ")");
    }
}
