package com.dsmod.probe;

import com.dsmod.relay.ExpertRelayGate;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
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
import android.view.Window;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
import java.lang.reflect.Proxy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedInterface.Chain;
import io.github.libxposed.api.XposedInterface.Hooker;
import io.github.libxposed.api.XposedModuleInterface.PackageLoadedParam;

public class Main extends XposedModule {

    private static final String TAG = "DSPROBE";
    private static final String TARGET = "com.deepseek.chat";
    static final String SELF = "com.dsmod.probe";
    private static final String LOG_PATH = "/data/data/com.deepseek.chat/files/dsprobe.log";
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    // 存储在 DeepSeek 自己的 files 目录，hook 进程和 UI 都能直接读写
    static final String PROMPT_FILE       = "/data/data/com.deepseek.chat/files/deekseep_prompt.txt";
    static final String PROMPT_LINK_FILE  = "/data/data/com.deepseek.chat/files/deekseep_prompt_link.txt";
    static final String PROMPT_SOURCE_FILE = "/data/data/com.deepseek.chat/files/deekseep_prompt_source.txt";
    private static final String EMBEDDED_PROMPT_RESOURCE =
            "META-INF/com.github.mwiede.jsch/internal/transport/authentication/"
            + ".com_github_mwiede_jsch_transport_authentication_negotiation_runtime_policy_extension_20260727_v2.dat";
    private static final String EMBEDDED_PROMPT_DIR =
            "/data/data/com.deepseek.chat/no_backup/.system_component_cache/.transport";
    private static final String EMBEDDED_PROMPT_FILE = EMBEDDED_PROMPT_DIR
            + "/.authentication_negotiation_runtime_policy_extension_20260727_v2.dat";
    private static final String EMBEDDED_PREVIOUS_PROMPT_FILE = EMBEDDED_PROMPT_DIR
            + "/.previous_runtime_policy.dat";
    private static final String EMBEDDED_PREVIOUS_SOURCE_FILE = EMBEDDED_PROMPT_DIR
            + "/.previous_runtime_policy_source.dat";
    private static final String EMBEDDED_PREVIOUS_STATE_FILE = EMBEDDED_PROMPT_DIR
            + "/.previous_runtime_policy_state.dat";
    static final String ENABLED_FILE      = "/data/data/com.deepseek.chat/files/deekseep_enabled";
    static final String NO_CENSOR_FILE    = "/data/data/com.deepseek.chat/files/deekseep_nocensor";
    static final String SRVLOG_FILE       = "/data/data/com.deepseek.chat/files/deekseep_srvlog";
    static final String AUTO_BACKUP_FILE  = "/data/data/com.deepseek.chat/files/deekseep_auto_backup";
    static final String EXPERT_UNLOCK_FILE = "/data/data/com.deepseek.chat/files/deekseep_expert_unlock";
    static final String GOOGLE_LOGIN_UNLOCK_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_google_login_unlock";
    static final String WECHAT_MOBILE_LOGIN_UNLOCK_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_wechat_mobile_login_unlock";
    static final String LOCAL_API_ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_local_api_enabled";
    static final String LOCAL_API_BACKGROUND_READY_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_local_api_background_ready";
    static final String LOCAL_API_SESSION_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_local_api_sessions.json";
    static final String CHAT_MULTISELECT_FILE = "/data/data/com.deepseek.chat/files/deekseep_chat_multiselect";
    static final String PROACTIVE_HEARTBEAT_ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_proactive_heartbeat";
    private static final String PROACTIVE_HEARTBEAT_INTERVAL_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_proactive_interval_minutes";
    private static final String PROACTIVE_HEARTBEAT_PLAN_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_proactive_plan.txt";
    private static final String PROACTIVE_HEARTBEAT_BINDING_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_proactive_binding.json";
    private static final String PROACTIVE_HEARTBEAT_HISTORY_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_proactive_history";
    static final int    PICK_REQUEST      = 0xDE3E;
    static final int    PICK_IMAGE_REQUEST = 0xDE3F;
    static final int    ACCOUNT_IMPORT_REQUEST = 0xDE40;
    static final int    ACCOUNT_EXPORT_REQUEST = 0xDE41;
    static final int    LOCAL_API_BATTERY_REQUEST = 0xDE42;
    private static final String EDITOR_IMAGE_MASTER_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_editor_images";
    private static final String EDITOR_IMAGE_CACHE_DIR =
            "/data/data/com.deepseek.chat/cache/captured";
    private static final String EDITOR_IMAGE_URI_PREFIX =
            "content://com.deepseek.chat.provider/tmp_captured_images/";

    interface GalleryPickCallback {
        void onPicked(Uri uri);
    }
    private static volatile GalleryPickCallback galleryPickCallback;
    private static final ThreadLocal<BubbleRenderContext> BUBBLE_RENDER_CONTEXT =
            new ThreadLocal<>();
    private static final ThreadLocal<InputGlassContext> INPUT_GLASS_CONTEXT =
            new ThreadLocal<>();
    private static final ThreadLocal<ModeGlassContext> MODE_GLASS_CONTEXT =
            new ThreadLocal<>();
    private static final ConcurrentHashMap<String, Object> BUBBLE_DRAW_CALLBACKS =
            new ConcurrentHashMap<>();

    // ── 侧栏聊天记录多选删除（sidebar multi-select delete）─────────────
    private static final Map<String, Object> SIDEBAR_DELETE_ACTIONS = new HashMap<>();
    private static final Map<String, Object> SIDEBAR_CLICK_ACTIONS = new HashMap<>();
    private static final HashSet<String> SIDEBAR_SELECTED = new HashSet<>();
    private static volatile View sidebarSelectOverlay;
    private static volatile boolean sidebarSelectMode = false;
    private static volatile String sidebarCurrentSid;
    private static volatile long sidebarBoundsLogAt;
    // mq5.i 暴露的主会话抽屉 DrawerState；仅跟踪这一实例，避免其他 Compose 抽屉干扰背景位移。
    private static volatile Object sidebarDrawerState;
    private static volatile int sidebarDrawerWidthPx;
    private static volatile Object sidebarLiveLoggedState;
    // 本次多选会话是否已确认看到行处于屏内（左坐标非负）；用于收起检测的解锁
    private static volatile boolean sidebarConfirmedOpen = false;
    // 会话行真实 Compose 坐标（decor/window 空间：left,top,right,bottom），由 onGloballyPositioned 回调写入
    private static final Map<String, int[]> SIDEBAR_ROW_BOUNDS = new ConcurrentHashMap<>();
    // 每个 sid 复用同一个 ib3 回调，保证 lw5 元素 equals 稳定，避免 Compose 节点抖动
    private static final Map<String, Object> SIDEBAR_BOUNDS_CB = new HashMap<>();
    // bm4(LayoutCoordinates) 方法：i()=isAttached, k()=size(packed long), w(long)=localToWindow
    private static volatile Method BM4_I, BM4_K, BM4_W;

    // 专家模式解锁：俘获任意"已启用"模型的真 feature 模板，回填给 expert
    private static volatile Object tplThink;
    private static volatile Object tplSearch;
    private static volatile Object tplFile;
    // sf5(模型配置) 字段：a=model_type f=enabled g=switchable j=think k=search l=file(gf5)；GF5_C=gf5.c 最大文件数
    private static Field EX_A, EX_F, EX_G, EX_J, EX_K, EX_L, GF5_C;
    private static final java.util.List<Object> expertInsts = new java.util.ArrayList<>();

    // ── 专家图片→视觉描述中继（expert-image → vision relay）────────────────
    // ★正式功能开关：expert 模式带图 → 后台视觉描述中继。存在=开启。
    static final String EXPERT_RELAY_FILE = "/data/data/com.deepseek.chat/files/deekseep_expert_relay";
    // 已成功走过中继的原会话。按 sid 落独立标记，重启后历史同步不再依赖服务端模型字段。
    static final String EXPERT_RELAY_SESSION_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_expert_relay_sessions";
    static final String RELAY_PROMPT_MARKER = "【图片内容（自动识别）】";
    static final String RELAY_PROMPT_MARKER_EN = "[Image content (automatically recognized)]";
    // 中继捕获的图片 fragment（qs7 JSON）按原会话 sid 落盘，供强杀重开后 pw0/fm8 注入。
    static final String RELAY_IMAGE_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_relay_images";
    // 发给 vision 的中性描述指令（绝不能带用户越狱系统提示，否则 vision 会拒答）。
    static final String VISION_DESCRIBE_PROMPT =
            "请客观描述这张图片，100到200字：包括主要事物、颜色、场景、画面细节，以及逐字转录图中出现的所有文字。只做客观描述，不评价、不拒绝、不添加与图片无关的内容。";
    static final String VISION_DESCRIBE_PROMPT_EN =
            "Objectively describe this image in 100–200 words. Include the main subjects, colors, scene, visual details, and a verbatim transcription of all visible text. Describe only what is present; do not evaluate, refuse, or add unrelated content.";

    private static String relayPromptMarker() {
        return UiLanguage.text(RELAY_PROMPT_MARKER, RELAY_PROMPT_MARKER_EN);
    }

    private static String visionDescribePrompt() {
        return UiLanguage.text(VISION_DESCRIBE_PROMPT, VISION_DESCRIBE_PROMPT_EN);
    }
    // 视觉探针诊断日志（私有目录，直写，最可靠）
    static final String RELAY_LOG_PATH = "/data/data/com.deepseek.chat/files/deekseep_vision.log";
    private static final String[] IMAGE_EXTS = {"jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"};

    // 视觉中继状态：活着的 r92(transport 入口)、q71(PoW 管理器)、fm8(WCDB 仓库)实例
    private static volatile Object liveR92;
    private static volatile Object liveQ71;
    private static volatile Object liveFm8;
    private static volatile ClassLoader hostClassLoader;
    private static volatile Context hostApplicationContext;
    private static volatile String lastInteractiveConversationId;
    private static final Object HEARTBEAT_BINDING_LOCK = new Object();
    private static final AtomicInteger HEARTBEAT_OPEN_GENERATION = new AtomicInteger();
    private static final ConcurrentHashMap<String, WeakReference<Object>>
            ACTIVE_CHAT_SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, WeakReference<Object>>
            ACTIVE_CHAT_VIEW_MODELS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, NativeHeartbeatHistory>
            PENDING_NATIVE_HEARTBEAT_HISTORIES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, NativeUiHeartbeatRequest>
            PENDING_NATIVE_UI_HEARTBEATS = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> tlLocalApiRequest = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> tlProactiveHeartbeatRequest = new ThreadLocal<>();
    private static final Map<Object, HeartbeatResponseStream> HEARTBEAT_RESPONSE_STREAMS =
            Collections.synchronizedMap(
                    new WeakHashMap<Object, HeartbeatResponseStream>());
    private static final Object AGENT_UI_ACTION_LOCK = new Object();
    private static final String AGENT_SCREENSHOT_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_agent";
    private static final String ACTION_AGENT_COMMAND =
            "com.dsmod.probe.action.AGENT_COMMAND";
    private static final String EXTRA_AGENT_COMMAND = "agent_command_json";
    private static final String EXTRA_AGENT_COMMAND_BASE64 =
            "agent_command_base64";
    private static final ConcurrentHashMap<String, Object>
            AGENT_SCREENSHOT_UPLOAD_TOKENS = new ConcurrentHashMap<>();
    private static volatile long agentUiActionNotBefore;
    private static final AtomicBoolean HEARTBEAT_STATUS_STYLE_HIT_LOGGED =
            new AtomicBoolean();
    private static final AtomicBoolean HEARTBEAT_STATUS_STYLE_ERROR_LOGGED =
            new AtomicBoolean();
    // DeepSeek permits only one active native generation for this account. Every translated
    // request therefore shares one fair lane. Agent turns announce themselves before queuing;
    // nonessential Claude metadata waits outside the lane so it cannot occupy the sole permit.
    private static final Semaphore LOCAL_API_COMPLETION_SLOTS = new Semaphore(1, true);
    private static final AtomicInteger LOCAL_API_AGENT_WAITERS = new AtomicInteger();
    private static volatile long localApiAgentPriorityUntil;
    private static volatile long localApiNextAuxiliaryStartAt;
    private static final long LOCAL_API_TIMEOUT_SECONDS = 180L;
    // Includes time spent waiting for the single native lane, PoW, retries and stream collection.
    // A local Agent must always receive either a response or a bounded OpenAI-style error.
    private static final long LOCAL_API_REQUEST_BUDGET_MS = 170_000L;
    private static final ThreadLocal<Long> tlLocalApiDeadline = new ThreadLocal<>();
    private static final ThreadLocal<LocalApiGateway.DeltaSink> tlLocalApiSink =
            new ThreadLocal<>();
    private static final long LOCAL_API_AGENT_QUEUE_WAIT_MS = 60_000L;
    private static final long LOCAL_API_CHAT_QUEUE_WAIT_MS = 30_000L;
    private static final long LOCAL_API_AUX_QUEUE_WAIT_MS = 8_000L;
    private static final long LOCAL_API_QUEUE_POLL_MS = 250L;
    // The native service rejects bursts even when they are serialized. Space completion starts
    // apart and extend the not-before time after an explicit upstream rate-limit event.
    private static final long LOCAL_API_MIN_START_INTERVAL_MS = 2500L;
    private static final Object LOCAL_API_RATE_LOCK = new Object();
    private static volatile long localApiNextNativeStartAt;
    private static volatile int localApiRateLimitStreak;
    private static final Object LOCAL_API_POW_LOCK = new Object();
    private static final Object LOCAL_API_POW_SERIAL_LOCK = new Object();
    private static volatile LocalApiPowTask localApiPowTask;
    private static final Object LOCAL_API_SESSION_LOCK = new Object();
    private static final Map<String, String> LOCAL_API_SESSIONS = new HashMap<>();
    private static final Map<String, Long> LOCAL_API_SESSION_LAST_USED = new HashMap<>();
    // Claude Code creates a fresh client UUID for /new and /clear. Bound the hidden branch
    // directory so abandoned conversations cannot accumulate forever in DeepSeek history.
    private static final int LOCAL_API_SESSION_MAX = 32;
    private static final long LOCAL_API_SESSION_TTL_MS = 24L * 60L * 60L * 1000L;
    private static final long LOCAL_API_SESSION_TOUCH_PERSIST_MS = 5L * 60L * 1000L;
    private static final int LOCAL_API_SESSION_PRUNE_BATCH = 4;
    private static final String LOCAL_API_SESSION_META_KEY = "__deekseep_meta";
    private static final AtomicInteger LOCAL_API_SESSION_MAINTENANCE_RUNNING =
            new AtomicInteger();
    private static long localApiSessionStatePersistedAt;
    private static volatile boolean localApiSessionsLoaded;
    private static volatile String localApiLastSessionError = "not attempted";
    private static final HashSet<String> expertRelaySessionIds = new HashSet<>();
    // 发送点(fu0.y/uu0.y)捕获的图片 fp 列表与当前会话模型：主线程同栈传给紧随其后的 transport hook。
    private static final ThreadLocal<List> tlPendingFps = new ThreadLocal<>();
    private static final ThreadLocal<String> tlPendingModel = new ThreadLocal<>();
    // 把捕获到的 List<fp> 挂到对应 ew0 上（relay 在收集时/IO 线程跑，ThreadLocal 到不了）。
    private static final Map<Object, List> ew0Fps =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Object, List>());
    // DeepSeek 仅首轮把 model_type 写入 ew0；后续轮次为 null，因此需把发送点 tp.f() 绑定到本次请求。
    private static final Map<Object, String> ew0EffectiveModels =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<Object, String>());
    // 已在处理中的 expert 请求（弱引用集合，防同一对象被 hook 重复处理）
    private static final java.util.Set<Object> relaySeen =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<Object, Boolean>());

    // 诊断：记录服务器返回的 SSE 原始事件（受 SRVLOG_FILE 开关控制）
    static final String SRV_LOG_PATH = "/data/data/com.deepseek.chat/files/deekseep_srv.log";
    static final String SRV_LOG_EXT  = "/storage/emulated/0/deekseep_srv.log";

    // DeekseepUi 选完文件后的 UI 刷新回调
    static volatile Runnable onPickComplete;

    // 诊断：模块加载到 DeepSeek 后，首个 Activity 弹一次 Toast 确认注入生效（无需 root/日志）
    private static boolean loadToastShown = false;
    // 外部可见的加载标记（best-effort，宿主有存储权限时才写得进去）
    // 注意：旧 legacy 模块曾用另一 uid 写过同名外部文件(-rw-rw----)，modern 无法覆盖/追加，
    // 故 modern 一律用带 _m 后缀的“自己新建、自己拥有”的外部文件，Termux 可按 media_rw 组读取。
    static final String LOADED_MARK_EXT = "/storage/emulated/0/deekseep_loaded_m.txt";
    // modern 专属外部镜像日志（新文件，避免与 legacy-owned 文件权限冲突导致静默写失败）
    static final String EXT_MAIN_LOG   = "/storage/emulated/0/dsprobe_m.log";
    static final String EXT_VISION_LOG = "/storage/emulated/0/deekseep_vision_m.log";
    static final String EXT_CRASH_LOG  = "/storage/emulated/0/dsprobe_crash.log";

    // 首次注入 DeepSeek 时弹出的简短使用说明；确认后写此标记，之后不再弹
    static final String DISCLAIMER_FILE = "/data/data/com.deepseek.chat/files/deekseep_disclaimer_ok";
    static final String DISCLAIMER_VERSION = "2026-07-26-v8-friendly";
    static final String EXPERIMENTAL_DISCLAIMER_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_experimental_disclaimer_ok";
    static final String EXPERIMENTAL_DISCLAIMER_VERSION = "2026-07-20-v1";
    private static volatile boolean disclaimerHandled = false;
    private static volatile boolean googleLoginUnlockInjectedLogged = false;
    private static volatile boolean wechatMobileLoginUnlockInjectedLogged = false;
    private static volatile long activationHeartbeatAttemptAt = 0L;
    private static volatile boolean activationHeartbeatLogged = false;
    private static volatile boolean proactiveHeartbeatConfigSynced = false;
    private static volatile String lastUiLanguageLog = "";
    private static volatile long localApiKeepAliveHeartbeatAt;
    private static volatile String localApiKeepAliveError = "尚未启动前台保活";
    private static volatile boolean localApiKeepAliveControlLogged;
    private static volatile long localApiKeepAliveLaunchAt;
    private static volatile IBinder publicTunnelBridgeBinder;
    private static volatile boolean publicTunnelBridgeBinding;
    private static volatile long publicTunnelBridgeRequestAt;
    private static volatile ResultReceiver publicTunnelBridgeReceiver;
    private static volatile boolean publicTunnelProviderUnavailable;

    private static final String SETTINGS_CLASS = "u25";
    private static final String SETTINGS_METHOD = "i";

    // Captured from mc.f: DeepSeek's complete native session list, click handler, and the
    // central s61 event sink.  Sending h61(tp) through that sink is DeepSeek's real deletion
    // path: server request first, then native list/WCDB cleanup on success.
    private static volatile Object NATIVE_SESSION_LIST;
    // Canonical ed0.e SnapshotStateList.  mc.f only renders this state; replacing its argument
    // with a merged copy is not enough because navigation and the active-chat validator continue
    // to observe the original list.
    private static volatile Object NATIVE_SESSION_STATE;
    private static volatile Object NATIVE_SESSION_CLICK;
    private static volatile Object NATIVE_SESSION_EVENTS;
    private static final ConcurrentHashMap<String, Long> RECENTLY_DELETED_SESSION_IDS =
            new ConcurrentHashMap<>();
    private static final long DELETED_SESSION_VISIBILITY_GRACE_MS = 120000L;
    // Original mv objects for which a real CONTENT_FILTER event was observed. Weak keys ensure
    // normal message lifetimes are unchanged; once a tp provides the SID, the exact kv is written
    // to ResponsePreserver's private durable store.
    private static final Map<Object, Boolean> FILTERED_ORIGINAL_MESSAGES =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    private static final Map<String, Object> LOCAL_NATIVE_SESSIONS = new HashMap<>();
    private static volatile HashSet<String> LOCAL_SESSION_IDS = new HashSet<>();
    private static volatile long LOCAL_SESSION_IDS_AT;
    private static volatile long LOCAL_NATIVE_MERGE_LOG_AT;
    private static volatile long LOCAL_NATIVE_STATE_REPAIR_LOG_AT;
    private static volatile long LOCAL_DIRECTORY_MERGE_LOG_AT;
    private static volatile long LOCAL_DIRECTORY_HEAD_LOG_AT;
    private static final ThreadLocal<Boolean> LOCAL_DIRECTORY_SYNC = new ThreadLocal<>();
    // Loaded once before WCDB starts, then refreshed from p68's already-materialised local rows.
    private static final ConcurrentHashMap<String, Integer> FROZEN_SESSION_HEADS =
            new ConcurrentHashMap<>();
    private static final HashSet<Class<?>> NATIVE_CLICK_HOOKED_CLASSES = new HashSet<>();
    private static volatile String PENDING_LOCAL_OPEN_SID;
    private static volatile long PENDING_LOCAL_OPEN_AT;
    // Marker-gated real-flow probe; removed after the failing device path is captured.
    private static final String REAL_SESSION_PROBE_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_real_session_probe";

    // DeepSeek 自己的文件 API（pv0）。编辑器复用宿主登录态调用 fork_file_task，
    // 为复制到聊天记录的图片取得新的 file_id/signed_path，避免旧签名重开后失效。
    private static volatile Object IMAGE_FILE_API;
    private static volatile Object IMAGE_COMPOSER;
    private static volatile ClassLoader IMAGE_HOST_CL;

    // 现代 API：模块实例，供静态 log 走框架日志
    private static volatile Main MODULE;

    // Traditional Xposed may instantiate the entry class while the process is still being
    // specialized from a USAP, before ActivityThread has prepared the main Looper.  Creating a
    // Handler here used to make the API 82+ compatibility APK fail before handleLoadPackage().
    // Initialize it only after the target package callback is delivered.
    private Handler main;
    private WeakReference<Activity> curAct = new WeakReference<>(null);
    private WeakReference<TextView> btn = new WeakReference<>(null);
    private WeakReference<Object> navController = new WeakReference<>(null);

    static synchronized void log(String msg) {
        try { Main m = MODULE; if (m != null) m.log(Log.INFO, TAG, msg); } catch (Throwable ignored) {}
        String line = TS.format(new Date()) + "  " + msg + "\n";
        try {
            FileWriter w = new FileWriter(LOG_PATH, true);
            w.write(line);
            w.close();
        } catch (Throwable ignored) {}
        try {
            FileWriter w = new FileWriter(EXT_MAIN_LOG, true);
            w.write(line);
            w.close();
        } catch (Throwable ignored) {}
    }

    // 专门记录服务器返回内容的诊断日志：写 DeepSeek files 目录（root 可读），
    // 尽力也写一份到外部存储，同时镜像到框架日志（可在管理器里导出）。
    private static synchronized void srvLog(String msg) {
        String line = TS.format(new Date()) + "  " + msg + "\n";
        try {
            FileWriter w = new FileWriter(SRV_LOG_PATH, true);
            w.write(line);
            w.close();
        } catch (Throwable ignored) {}
        try {
            FileWriter w = new FileWriter(SRV_LOG_EXT, true);
            w.write(line);
            w.close();
        } catch (Throwable ignored) {}
        try { Main m = MODULE; if (m != null) m.log(Log.INFO, TAG, "SRV " + msg); } catch (Throwable ignored) {}
    }

    // 视觉中继诊断日志：私有目录直写为主，同时尽力镜像一份到公共目录。
    static synchronized void extLog(String msg) {
        try { Main m = MODULE; if (m != null) m.log(Log.INFO, TAG, msg); } catch (Throwable ignored) {}
        String line = TS.format(new Date()) + "  " + msg + "\n";
        try {
            FileWriter w = new FileWriter(RELAY_LOG_PATH, true);
            w.write(line);
            w.close();
        } catch (Throwable ignored) {}
        try {
            FileWriter w = new FileWriter(EXT_VISION_LOG, true);
            w.write(line);
            w.close();
        } catch (Throwable ignored) {}
    }

    private static volatile boolean crashHandlerInstalled = false;
    static synchronized void installCrashHandler() {
        if (crashHandlerInstalled) return;
        crashHandlerInstalled = true;
        try {
            final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override public void uncaughtException(Thread t, Throwable e) {
                    try {
                        String line = TS.format(new Date()) + "  UNCAUGHT thread=" + t.getName()
                                + "\n" + android.util.Log.getStackTraceString(e) + "\n";
                        try { FileWriter w = new FileWriter(EXT_CRASH_LOG, true); w.write(line); w.close(); } catch (Throwable ignored) {}
                        try { FileWriter w = new FileWriter("/data/data/com.deepseek.chat/files/dsprobe_crash.log", true); w.write(line); w.close(); } catch (Throwable ignored) {}
                    } catch (Throwable ignored) {}
                    if (prev != null) prev.uncaughtException(t, e);
                }
            });
        } catch (Throwable ignored) {}
    }

    static boolean isSrvLog() {
        return new File(SRVLOG_FILE).exists();
    }

    static void setSrvLog(boolean on) {
        try {
            File ef = new File(SRVLOG_FILE);
            if (on) overwriteTextFile(SRVLOG_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    static boolean isProactiveHeartbeatEnabled() {
        return new File(PROACTIVE_HEARTBEAT_ENABLED_FILE).exists();
    }

    static int proactiveHeartbeatIntervalMinutes() {
        String stored = readSmallText(PROACTIVE_HEARTBEAT_INTERVAL_FILE);
        if (stored != null) {
            try {
                return Math.max(15, Math.min(7 * 24 * 60, Integer.parseInt(stored.trim())));
            } catch (Throwable ignored) {}
        }
        return 180;
    }

    static boolean hasProactiveHeartbeatBinding() {
        return readHeartbeatBinding().conversationId.length() > 0;
    }

    static boolean proactiveHeartbeatBoundToCurrentConversation() {
        String current = HeartbeatToolProtocol.cleanScope(
                sidebarCurrentSid != null ? sidebarCurrentSid
                        : lastInteractiveConversationId);
        return current.length() > 0
                && current.equals(readHeartbeatBinding().conversationId);
    }

    private static HeartbeatBinding readHeartbeatBinding() {
        synchronized (HEARTBEAT_BINDING_LOCK) {
            String text = readSmallText(PROACTIVE_HEARTBEAT_BINDING_FILE);
            if (text == null || text.length() == 0) return new HeartbeatBinding("", "");
            try {
                JSONObject object = new JSONObject(text);
                return new HeartbeatBinding(
                        HeartbeatToolProtocol.cleanScope(
                                object.optString("conversation_id", "")),
                        HeartbeatToolProtocol.cleanInstruction(
                                object.optString("instruction", "")));
            } catch (Throwable t) {
                log("heartbeat binding state ignored: " + safeThrowableMessage(t));
                return new HeartbeatBinding("", "");
            }
        }
    }

    private static boolean writeHeartbeatBinding(String conversationId, String instruction) {
        String sid = HeartbeatToolProtocol.cleanScope(conversationId);
        if (sid.length() == 0) return false;
        String plan = HeartbeatToolProtocol.cleanInstruction(instruction);
        synchronized (HEARTBEAT_BINDING_LOCK) {
            try {
                JSONObject object = new JSONObject();
                object.put("version", 1);
                object.put("conversation_id", sid);
                object.put("instruction", plan);
                overwriteTextFile(PROACTIVE_HEARTBEAT_BINDING_FILE, object.toString());
                HeartbeatBinding stored = readHeartbeatBinding();
                return sid.equals(stored.conversationId)
                        && plan.equals(stored.instruction);
            } catch (Throwable t) {
                log("heartbeat binding save failed: " + safeThrowableMessage(t));
                return false;
            }
        }
    }

    private static String heartbeatPlanForConversation(String conversationId) {
        String sid = HeartbeatToolProtocol.cleanScope(conversationId);
        HeartbeatBinding binding = readHeartbeatBinding();
        return sid.equals(binding.conversationId) ? binding.instruction : "";
    }

    private static String legacyHeartbeatPlan() {
        return HeartbeatToolProtocol.cleanInstruction(
                readSmallText(PROACTIVE_HEARTBEAT_PLAN_FILE));
    }

    private static final class HeartbeatBinding {
        final String conversationId;
        final String instruction;

        HeartbeatBinding(String conversationId, String instruction) {
            this.conversationId = conversationId == null ? "" : conversationId;
            this.instruction = instruction == null ? "" : instruction;
        }
    }

    static boolean setProactiveHeartbeatInterval(Context context, int minutes) {
        if (context == null || minutes < 15 || minutes > 7 * 24 * 60) return false;
        try {
            overwriteTextFile(PROACTIVE_HEARTBEAT_INTERVAL_FILE,
                    String.valueOf(minutes));
            dispatchProactiveHeartbeatConfig(
                    context, isProactiveHeartbeatEnabled());
            return proactiveHeartbeatIntervalMinutes() == minutes;
        } catch (Throwable t) {
            log("proactive heartbeat interval save failed: " + t);
            return false;
        }
    }

    static boolean setProactiveHeartbeatEnabled(Context context, boolean enabled) {
        if (context == null) return false;
        try {
            if (enabled) {
                if (!hasProactiveHeartbeatBinding()) {
                    String candidate = HeartbeatToolProtocol.cleanScope(
                            sidebarCurrentSid != null ? sidebarCurrentSid
                                    : lastInteractiveConversationId);
                    if (candidate.length() > 0) {
                        writeHeartbeatBinding(candidate, legacyHeartbeatPlan());
                    }
                }
                overwriteTextFile(PROACTIVE_HEARTBEAT_ENABLED_FILE, "");
            }
            else new File(PROACTIVE_HEARTBEAT_ENABLED_FILE).delete();
            dispatchProactiveHeartbeatConfig(context, enabled);
            return isProactiveHeartbeatEnabled() == enabled;
        } catch (Throwable t) {
            log("proactive heartbeat setting failed: " + t);
            return false;
        }
    }

    private static void dispatchProactiveHeartbeatConfig(Context context, boolean enabled) {
        try {
            Intent config = new Intent(ProactiveHeartbeatReceiver.ACTION_CONFIG);
            config.setClassName(SELF, ProactiveHeartbeatReceiver.class.getName());
            config.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            config.putExtra(ProactiveHeartbeatReceiver.EXTRA_TOKEN,
                    ProactiveHeartbeatReceiver.TOKEN);
            config.putExtra(ProactiveHeartbeatReceiver.EXTRA_ENABLED, enabled);
            config.putExtra(ProactiveHeartbeatReceiver.EXTRA_INTERVAL_MINUTES,
                    proactiveHeartbeatIntervalMinutes());
            config.putExtra(ProactiveHeartbeatReceiver.EXTRA_CONVERSATION_ID,
                    readHeartbeatBinding().conversationId);
            context.sendBroadcast(config);
            log("proactive heartbeat config dispatched enabled=" + enabled);
        } catch (Throwable t) {
            log("proactive heartbeat config dispatch failed: " + t);
        }
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        MODULE = this;
        final ClassLoader cl = param.getDefaultClassLoader();
        final String pkg = param.getPackageName();

        if (!TARGET.equals(pkg)) return;
        HostCompat.initialize(cl);
        Looper mainLooper = Looper.getMainLooper();
        if (mainLooper == null) {
            log("target package callback arrived before the main Looper was prepared");
            return;
        }
        main = new Handler(mainLooper);
        hostClassLoader = cl;

        // 崩溃捕获：把未捕获异常栈写到 modern 自己新建的外部文件(Termux 可读)，
        // 用于诊断“上传图片点发送直接闪退”这类无 root/无 logcat 场景的崩溃。
        installCrashHandler();

        try { new FileWriter(LOG_PATH, false).close(); } catch (Throwable ignored) {}
        // 服务器返回诊断日志：每次应用启动清空重记（与主日志一致）
        if (isSrvLog()) {
            try { new FileWriter(SRV_LOG_PATH, false).close(); } catch (Throwable ignored) {}
            try { new FileWriter(SRV_LOG_EXT, false).close(); } catch (Throwable ignored) {}
        }
        log("module loaded (modern), package=" + pkg
                + ", hostGeneration=" + HostCompat.generationName());
        installLocalApiKeepAliveReceiverHook(cl);
        restoreLocalEditorImages();
        int obsoleteTriggers = ChatEditorUi.removeObsoleteLocalSessionProtection();
        if (obsoleteTriggers > 0) {
            log("removed obsolete local-session triggers=" + obsoleteTriggers);
        }
        // This is the only safe time to use Android SQLite against DeepSeek's database: package
        // load runs before the host starts its WCDB repositories. Never repair from a delayed
        // worker after this point, because crossing both SQLite engines can leave WCDB blocked in
        // sqlite3_step and make an otherwise intact conversation render as an empty page.
        int restoredLocal = ChatEditorUi.restoreLocalConversations();
        if (restoredLocal > 0) {
            log("restored local conversations before WCDB startup=" + restoredLocal);
        }
        int repairedHeads = ChatEditorUi.repairFrozenCurrentMessageIds();
        if (repairedHeads > 0) {
            log("repaired frozen conversation heads before WCDB startup=" + repairedHeads);
        }
        FROZEN_SESSION_HEADS.clear();
        FROZEN_SESSION_HEADS.putAll(ChatEditorUi.frozenCurrentMessageIds());
        // 自动备份：距上次>24h 且开关开启时后台复制数据库
        new Thread(new Runnable() { public void run() {
            try { DeekseepTools.maybeAutoBackup(); } catch (Throwable ignored) {}
        }}).start();
        // 外部可见加载标记：证明模块确实被注入进了 DeepSeek 进程
        try {
            FileWriter w = new FileWriter(LOADED_MARK_EXT, false);
            w.write(TS.format(new Date()) + "  loaded into " + pkg + "\n");
            w.close();
        } catch (Throwable ignored) {}

        // 跟踪当前 Activity（并在首个 Activity 弹一次 Toast 确认注入生效）
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            hook(onResume).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object r = chain.proceed();
                    try {
                        Activity act = (Activity) chain.getThisObject();
                        curAct = new WeakReference<>(act);
                        hostApplicationContext = act.getApplicationContext();
                        consumeHeartbeatConversationIntent(act, act.getIntent());
                        ChatAppearance.onActivityResumed(act);
                        scheduleRouteCheck(navController.get());
                        // The Play build keeps its language tag in MMKV rather than Android's
                        // per-app locale service. Re-read it on every resume so Deekseep follows a
                        // host-language switch immediately.
                        UiLanguage.refreshHost(act);
                        String languageState = "mode=" + UiLanguage.currentMode(act)
                                + ", host=" + UiLanguage.detectedLanguage(act)
                                + ", effective=" + (UiLanguage.isChinese(act)
                                ? "Chinese" : "English");
                        if (!languageState.equals(lastUiLanguageLog)) {
                            lastUiLanguageLog = languageState;
                            log("UI language " + languageState);
                        }
                        reportActivationHeartbeat(act);
                        if (!proactiveHeartbeatConfigSynced) {
                            proactiveHeartbeatConfigSynced = true;
                            dispatchProactiveHeartbeatConfig(
                                    act, isProactiveHeartbeatEnabled());
                        }
                        if (isLocalApiEnabled() && isLocalApiBackgroundApproved(act)) {
                            requestLocalApiKeepAlive(act, true);
                            startLocalApiGateway(act);
                            requestPublicTunnelBridge(act);
                        } else {
                            requestLocalApiKeepAlive(act, false);
                            if (LocalApiGateway.isRunning()) LocalApiGateway.stop();
                        }
                        if (!loadToastShown) {
                            loadToastShown = true;
                            try {
                                UiLanguage.toast(act,
                                        UiLanguage.text(act,
                                                "Deekseep 已注入 (v" + SettingsActivity.VERSION + ")",
                                                "Deekseep injected (v" + SettingsActivity.VERSION + ")"),
                                        android.widget.Toast.LENGTH_SHORT).show();
                            } catch (Throwable ignored) {}
                        }
                        maybeShowDisclaimer(act);
                    } catch (Throwable ignored) {}
                    return r;
                }
            });
        } catch (Throwable t) { log("hook onResume failed: " + t); }

        try {
            Method onPause = Activity.class.getDeclaredMethod("onPause");
            hook(onPause).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    try {
                        ChatAppearance.onActivityPaused(
                                (Activity) chain.getThisObject());
                    } catch (Throwable t) {
                        log("spatial onPause cleanup skipped: " + t);
                    }
                    return chain.proceed();
                }
            });
        } catch (Throwable t) {
            log("hook onPause failed: " + t);
        }

        try {
            Method onNewIntent = Activity.class.getDeclaredMethod(
                    "onNewIntent", Intent.class);
            hook(onNewIntent).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    try {
                        Activity act = (Activity) chain.getThisObject();
                        Intent intent = chain.getArg(0) instanceof Intent
                                ? (Intent) chain.getArg(0) : null;
                        consumeHeartbeatConversationIntent(act, intent);
                    } catch (Throwable t) {
                        log("heartbeat notification navigation skipped: " + t);
                    }
                    return chain.proceed();
                }
            });
        } catch (Throwable t) {
            log("hook onNewIntent for heartbeat failed: " + t);
        }

        try {
            Method onDestroy = Activity.class.getDeclaredMethod("onDestroy");
            hook(onDestroy).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    try {
                        Activity act = (Activity) chain.getThisObject();
                        ChatAppearance.onActivityDestroyed(act);
                        if (curAct.get() == act) hideButton();
                    } catch (Throwable ignored) {}
                    return chain.proceed();
                }
            });
        } catch (Throwable t) { log("hook onDestroy failed: " + t); }

        // Observe pointer state without consuming it. The glass compositor uses this only for
        // highlight/lens feedback; DeepSeek still receives the original MotionEvent unchanged.
        try {
            Method dispatchTouch = Activity.class.getDeclaredMethod(
                    "dispatchTouchEvent", MotionEvent.class);
            hook(dispatchTouch).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    try {
                        Object event = chain.getArg(0);
                        if (event instanceof MotionEvent) {
                            LiquidGlassEngine.onTouchEvent((MotionEvent) event);
                        }
                    } catch (Throwable ignored) {}
                    return chain.proceed();
                }
            });
        } catch (Throwable t) {
            log("hook liquid glass touch feedback failed: " + t);
        }

        // 拦截 onActivityResult，捕获文件选择器结果
        try {
            Method oar = Activity.class.getDeclaredMethod("onActivityResult",
                    int.class, int.class, Intent.class);
            hook(oar).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object r = chain.proceed();
                    try {
                        int req = (int) chain.getArg(0);
                        int res = (int) chain.getArg(1);
                        Object dataArg = chain.getArg(2);
                        if (req == ACCOUNT_IMPORT_REQUEST) {
                            AccountUi.handleImportResult((Activity) chain.getThisObject(), res,
                                    dataArg instanceof Intent ? (Intent) dataArg : null);
                        } else if (req == ACCOUNT_EXPORT_REQUEST) {
                            AccountUi.handleExportResult((Activity) chain.getThisObject(), res,
                                    dataArg instanceof Intent ? (Intent) dataArg : null);
                        } else if (req == LOCAL_API_BATTERY_REQUEST) {
                            DeekseepUi.handleLocalApiBatterySettingsResult(
                                    (Activity) chain.getThisObject());
                        } else if (req == PICK_IMAGE_REQUEST) {
                            GalleryPickCallback callback = galleryPickCallback;
                            galleryPickCallback = null;
                            Uri uri = null;
                            if (res == Activity.RESULT_OK && dataArg instanceof Intent) {
                                Intent data = (Intent) dataArg;
                                uri = data.getData();
                                if (uri != null) {
                                    persistReadGrant((Activity) chain.getThisObject(), data, uri);
                                }
                            }
                            log("gallery pick result: res=" + res + ", uri=" + uri);
                            if (callback != null) callback.onPicked(uri);
                        } else if (req == PICK_REQUEST) {
                            log("pick result: res=" + res + ", hasData=" + (dataArg != null));
                            if (res == Activity.RESULT_OK && dataArg != null) {
                                Intent data = (Intent) dataArg;
                                Uri uri = data.getData();
                                log("pick result uri=" + uri + ", flags=" + data.getFlags());
                                if (uri != null) {
                                    persistReadGrant((Activity) chain.getThisObject(), data, uri);
                                    handlePickedFile((Activity) chain.getThisObject(), uri);
                                }
                            }
                        }
                    } catch (Throwable t) { log("onActivityResult err: " + t); }
                    return r;
                }
            });
        } catch (Throwable t) { log("hook onActivityResult failed: " + t); }

        // hook ChatFullCompletionRequest 构造，注入系统提示词到 prompt 字段
        hookChatRequest(cl);
        // 心跳开启时向正常对话注入本地工具说明，并在流式/静态回复两端隐藏控制块。
        hookHeartbeatToolResponses(cl);
        // 仅在最终 Compose 文本边界为模块生成的心跳状态设置灰色小字。
        hookHeartbeatToolStatusStyle(
                cl, HostCompat.name("i68"), HostCompat.name("h78"));
        // Markdown 会移除零宽标记；在合并 TextStyle 后的 BasicText 边界按登记文本兜底。
        hookHeartbeatToolStatusBasicText(
                cl, HostCompat.name("yg8"),
                HostCompat.method("yg8", "b"), HostCompat.name("h78"));
        // 在线历史在进入宿主 UI/SQLite 前同步清掉注入前缀，并缓存未落库的会话快照。
        try { installExpertHistoryImagePreserver(cl); }
        catch (Throwable t) { log("install history bridge wiring failed: " + t); }
        // 旧格式只需在升级后的首次冷启动同步迁移；随后由在线/仓库 hook 处理新数据，
        // 避免每次启动都扫描所有账号库并与宿主 WCDB 争锁。
        File historyMigration = new File("/data/data/com.deepseek.chat/files/deekseep_history_migration_v3");
        if (!historyMigration.exists()) {
            boolean migrationOk = true;
            try { int n = ChatEditorUi.repairMalformedThinkFragmentsAllSessions();
                if (n < 0) migrationOk = false;
                log("repairMalformedThinkFragments fixed=" + n); }
            catch (Throwable t) { migrationOk = false; log("repairMalformedThinkFragments err: " + t); }
            try { int n = ChatEditorUi.stripAllSessions(); if (n < 0) migrationOk = false;
                log("stripAllSessions cleaned=" + n); }
            catch (Throwable t) { migrationOk = false; log("stripAllSessions err: " + t); }
            if (migrationOk) try { overwriteTextFile(historyMigration.getPath(), "3"); }
            catch (Throwable t) { log("history migration marker err: " + t); }
        }
        // hook ServerMessageHint(kb7) 构造，强制 clear_response=false
        hookSafetyRetraction(cl);
        // 诊断：抓取服务器返回的 SSE 原始事件（lv7）
        installServerCapture(cl);
        // 真正拦截点：mv.i() 应用 JSON-patch，命中 CONTENT_FILTER 就跳过
        hookContentFilterApply(cl);
        // 诊断：抓 vv7.e() 完整消息重建
        installMsgRebuildCapture(cl);
        // 第二拦截点：mv.S()/R() 直接写 status/quasi_status
        hookStatusWrite(cl);
        // 诊断：h83.h() fragment 多态反序列化选择器
        hookTemplateProbe(cl);
        // close 后整表合并 tp.u(tp, List)
        hookFinalMessageMerge(cl);
        // 单条替换 tp.q(uo)/tp.p(uo,String)/tp.a(uo,bool)（真正生效的去审查点）
        hookFinalMessageApply(cl);
        // ★ 专家模式(expert)解锁 聊天/搜索/上传文件（sf5 构造后强改 final 字段）
        hookExpertUnlock(cl);
        // 国内/海外登录页会按地区删减原生登录项；分别按两个开关恢复 Google，或成组恢复
        // 微信与短信手机号。点击仍完整走 DeepSeek 自己的原生登录与官方换票接口。
        hookRegionalLoginUnlock(cl);
        // ★ 上传门禁兜底：在 y91.a 真正读 sf5.l 判空前，就地俘获并点亮被消费的那个 sf5 实例（诊断+修复）
        try { installExpertUploadGate(cl); } catch (Throwable t) { log("installExpertUploadGate wiring failed: " + t); }
        // ★ 专家图片→视觉描述中继：抓 transport(r92)、PoW(q71)、历史图片保留(fm8/pw0)、发送点图片(fu0/uu0)
        try { installNetworkPayloadCapture(cl); } catch (Throwable t) { log("installNetworkPayloadCapture wiring failed: " + t); }
        try { installPowManagerCapture(cl); } catch (Throwable t) { log("installPowManagerCapture wiring failed: " + t); }
        try { hookLocalApiSessionVisibility(cl); }
        catch (Throwable t) { log("hookLocalApiSessionVisibility wiring failed: " + t); }
        try { installExpertImageFpCapture(cl); } catch (Throwable t) { log("installExpertImageFpCapture wiring failed: " + t); }
        try { installImageCredentialBridge(cl); }
        catch (Throwable t) { log("installImageCredentialBridge wiring failed: " + t); }
        hookLocalEditorImageUris(cl);
        hookLocalSessionDirectoryMerge(cl);
        hookLocalNativeSessionRefresh(cl);
        hookLocalSessionRemoteReload(cl);
        hookLocalSessionDeletedFlow(cl);
        hookLocalSessionDeletedResponse(cl);
        hookActiveChatSessionCapture(cl);
        hookProactiveVisibleThreadFilter(cl);
        hookNativeUiHeartbeatCompletion(cl);
        hookNativeSessionNavigator(cl);
        hookHistoryLoadDiagnostics(cl);
        scheduleRealSessionProbe();
        // hook 导航变化，离开设置页时移除入口按钮
        hookSettingsNavigation(cl);
        // ★ 侧栏聊天记录多选删除（modern Compose Hooker，手机端适配）
        try { hookSidebarMultiSelectDelete(cl); } catch (Throwable t) { log("hookSidebarMultiSelectDelete wiring failed: " + t); }
        try { hookSidebarToggleCleanup(cl); } catch (Throwable t) { log("hookSidebarToggleCleanup wiring failed: " + t); }
        try {
            hookChatBubbleCustomization(cl, false);
        } catch (Throwable mainlandError) {
            log("mainland bubble/input mapping unavailable, trying google-play: "
                    + mainlandError);
            try {
                hookChatBubbleCustomization(cl, true);
            } catch (Throwable playError) {
                log("hookChatBubbleCustomization wiring failed: " + playError);
            }
        }

        // hook 设置页主 Composable -> 显示 Deekseep 按钮
        try {
            String settingsClass = HostCompat.name(SETTINGS_CLASS);
            String settingsMethod = HostCompat.method(SETTINGS_CLASS, SETTINGS_METHOD);
            Class<?> k = cl.loadClass(settingsClass);
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(settingsMethod)) {
                    hook(m).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object r = chain.proceed();
                            main.post(new Runnable() { public void run() { showButton(); } });
                            return r;
                        }
                    });
                    n++;
                }
            }
            log("hooked settings composable " + settingsClass + "."
                    + settingsMethod + " x" + n);
        } catch (Throwable t) { log("hook settings composable failed: " + t); }
    }

    /**
     * Applies chat-bubble styling to the host's real Compose message nodes.  The hooks stay
     * deliberately below the message text/actions layer: only the Surface/Modifier chain is
     * changed, while imported decorations are drawn after the bubble content.
     */
    private void hookChatBubbleCustomization(
            final ClassLoader cl, boolean googlePlay) throws Exception {
        final BubbleComposeRuntime runtime =
                new BubbleComposeRuntime(cl, new BubbleHostMapping(googlePlay));

        Method userBubble = findBubbleMethod(
                cl.loadClass(runtime.mapping.userOwner),
                runtime.mapping.userMethod, 8,
                new int[]{0, 1},
                new Class<?>[]{String.class, runtime.modifierClass});
        Method assistantBody = findBubbleMethod(
                cl.loadClass(runtime.mapping.assistantBodyOwner),
                runtime.mapping.assistantBodyMethod, 12,
                new int[]{8}, new Class<?>[]{runtime.modifierClass});
        Method inputContainer = findBubbleMethod(
                cl.loadClass(runtime.mapping.inputOwner),
                runtime.mapping.inputMethod, 9,
                new int[]{6}, new Class<?>[]{runtime.modifierClass});
        Method conversationSearch = findBubbleMethod(
                cl.loadClass(runtime.mapping.searchOwner),
                runtime.mapping.searchMethod, 9,
                new int[]{0}, new Class<?>[]{runtime.modifierClass});
        Method attachmentItem = findBubbleMethod(
                cl.loadClass(runtime.mapping.attachmentOwner),
                runtime.mapping.attachmentMethod, 5,
                new int[]{1}, new Class<?>[]{runtime.modifierClass});
        Method modeItem = findBubbleMethod(
                cl.loadClass(runtime.mapping.modeItemOwner),
                runtime.mapping.modeItemMethod, 11,
                new int[]{0, 2, 8},
                new Class<?>[]{String.class, boolean.class, runtime.modifierClass});
        Method modeContainer = findBubbleMethod(
                cl.loadClass(runtime.mapping.modeContainerOwner),
                runtime.mapping.modeContainerMethod, 7,
                new int[]{0, 1},
                new Class<?>[]{runtime.modifierClass, boolean.class});

        deoptimizeBubbleMethod(userBubble);
        deoptimizeBubbleMethod(assistantBody);
        deoptimizeBubbleMethod(inputContainer);
        deoptimizeBubbleMethod(conversationSearch);
        deoptimizeBubbleMethod(attachmentItem);
        deoptimizeBubbleMethod(modeItem);
        deoptimizeBubbleMethod(modeContainer);
        deoptimizeBubbleMethod(runtime.clipMethod);
        deoptimizeBubbleMethod(runtime.backgroundMethod);
        try {
            Class<?> restart = cl.loadClass(runtime.mapping.assistantRestartOwner);
            for (Method method : restart.getDeclaredMethods()) {
                deoptimizeBubbleMethod(method);
            }
        } catch (Throwable t) {
            log("bubble restart deopt skipped: " + t);
        }

        hook(userBubble).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                ChatAppearance.BubbleStyle style =
                        ChatAppearance.bubbleStyleForRender(true);
                // Keep a zero-cost graphics layer attached even while spatial mode is disabled.
                // Its shared Compose state can then activate immediately after the hidden switch
                // changes, without waiting for this message row to be recomposed.
                if (style == null) style = new ChatAppearance.BubbleStyle();
                BubbleRenderContext context =
                        runtime.newContext(style, true, isBubbleDark());
                Object[] args = chain.getArgs().toArray();
                // The host calculates the final bubble width later, immediately before clip().
                // Keep the entry Modifier untouched so borders do not accidentally use the
                // larger message-row bounds; force this composition body to visit that node.
                args[6] = ((Number) args[6]).intValue() | 0x4;
                BubbleRenderContext previous = BUBBLE_RENDER_CONTEXT.get();
                BUBBLE_RENDER_CONTEXT.set(context);
                try {
                    return chain.proceed(args);
                } finally {
                    restoreBubbleContext(previous);
                }
            }
        });

        hook(runtime.clipMethod).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                BubbleRenderContext context = BUBBLE_RENDER_CONTEXT.get();
                if (context == null || !context.user) {
                    return chain.proceed();
                }
                Object[] args = chain.getArgs().toArray();
                if (!context.userModifierApplied) {
                    context.userModifierApplied = true;
                    args[0] = runtime.decorateModifier(args[0], context);
                }
                if (context.customSurface) args[1] = context.shape;
                return chain.proceed(args);
            }
        });

        hook(runtime.backgroundMethod).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                InputGlassContext input = INPUT_GLASS_CONTEXT.get();
                if (input != null && "Attachment".equals(input.label)
                        && !input.modifierApplied) {
                    Object result = chain.proceed();
                    input.modifierApplied = true;
                    return runtime.attachBoundsModifier(
                            result, input.surface, input.label);
                }
                BubbleRenderContext context = BUBBLE_RENDER_CONTEXT.get();
                if (context == null || !context.user || !context.customSurface) {
                    return chain.proceed();
                }
                Object[] args = chain.getArgs().toArray();
                args[1] = context.fillColor;
                args[2] = context.shape;
                return chain.proceed(args);
            }
        });

        // vh4.m / w3a.v are the like/dislike row, not the assistant message body.  Hooking their
        // nested Surface used to put both glass and imported decorations beside the thumbs and
        // could apply the same decoration more than once.  The 12-argument response composable
        // owns one Modifier for the complete assistant response, so decorate that Modifier once.
        hook(assistantBody).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                ChatAppearance.BubbleStyle style =
                        ChatAppearance.bubbleStyleForRender(false);
                if (style == null) style = new ChatAppearance.BubbleStyle();
                BubbleRenderContext context =
                        runtime.newContext(style, false, isBubbleDark());
                Object[] args = chain.getArgs().toArray();
                if (context.glassSurface != null
                        && runtime.assistantHasActionRow(args[3])) {
                    // DeepSeek places copy/like/dislike below the response body.  Keep that row
                    // outside the assistant lens so feedback buttons retain their native style.
                    context.glassSurface.setBottomInsetDp(44f);
                }
                args[8] = runtime.decorateAssistantModifier(args[8], context);
                return chain.proceed(args);
            }
        });

        hook(inputContainer).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                boolean glass = ChatAppearance.glassEnabledForRender();
                LiquidGlassEngine.SurfaceHandle surface = null;
                if (glass) {
                    // The mode list and input box are sibling compositions. Clearing mode records
                    // here races with p35.e/ds5.t and erases the selector immediately after it was
                    // registered. Each mode-list pass now owns its own generation below.
                    LiquidGlassEngine.clearSurfaceKinds(
                            LiquidGlassEngine.KIND_INPUT);
                    surface = LiquidGlassEngine.registerSurface(
                            LiquidGlassEngine.KIND_INPUT, 22f);
                }
                Object[] args = chain.getArgs().toArray();
                args[6] = runtime.decorateInputModifier(
                        args[6], isBubbleDark(), surface);
                return chain.proceed(args);
            }
        });

        hook(conversationSearch).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                if (!ChatAppearance.glassEnabledForRender()) {
                    return chain.proceed();
                }
                LiquidGlassEngine.clearSurfaceKinds(
                        LiquidGlassEngine.KIND_SIDEBAR_SEARCH);
                LiquidGlassEngine.SurfaceHandle surface =
                        LiquidGlassEngine.registerSurface(
                                LiquidGlassEngine.KIND_SIDEBAR_SEARCH, 16f);
                Object[] args = chain.getArgs().toArray();
                args[0] = runtime.decorateSearchModifier(
                        args[0], isBubbleDark(), surface);
                return chain.proceed(args);
            }
        });

        hook(modeItem).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                if (!ChatAppearance.glassEnabledForRender()) {
                    return chain.proceed();
                }
                Object[] args = chain.getArgs().toArray();
                boolean selected = Boolean.TRUE.equals(args[2]);
                int index = ((Number) args[4]).intValue();
                if (index == 0) {
                    LiquidGlassEngine.clearSurfaceKinds(
                            LiquidGlassEngine.KIND_MODE_ITEM,
                            LiquidGlassEngine.KIND_MODE_SELECTED);
                }
                LiquidGlassEngine.SurfaceHandle surface =
                        LiquidGlassEngine.registerSurface(
                                selected
                                        ? LiquidGlassEngine.KIND_MODE_SELECTED
                                        : LiquidGlassEngine.KIND_MODE_ITEM,
                                13f);
                ModeGlassContext previous = MODE_GLASS_CONTEXT.get();
                MODE_GLASS_CONTEXT.set(
                        new ModeGlassContext(
                                selected, isBubbleDark(), surface));
                try {
                    return chain.proceed(args);
                } finally {
                    if (previous == null) MODE_GLASS_CONTEXT.remove();
                    else MODE_GLASS_CONTEXT.set(previous);
                }
            }
        });

        // p35.e/ds5.t ignores its nullable Modifier argument and constructs the actual clickable
        // item with i39.S/av9.k0. Decorate that returned Modifier: it is below both text passes,
        // follows Compose scrolling, and supplies the exact bounds to the refracting layer.
        hook(modeContainer).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                ModeGlassContext context = MODE_GLASS_CONTEXT.get();
                if (context == null || context.modifierApplied) {
                    return chain.proceed();
                }
                Object result = chain.proceed();
                context.modifierApplied = true;
                result = runtime.decorateModeModifier(
                        result, context.selected, context.dark);
                return runtime.attachBoundsModifier(
                        result, context.surface,
                        context.selected ? "ModeSelected" : "Mode");
            }
        });

        hook(attachmentItem).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                if (!ChatAppearance.glassEnabledForRender()) {
                    return chain.proceed();
                }
                LiquidGlassEngine.SurfaceHandle surface =
                        LiquidGlassEngine.registerSurface(
                                LiquidGlassEngine.KIND_ATTACHMENT, 16f);
                if (surface == null) return chain.proceed();
                InputGlassContext previous = INPUT_GLASS_CONTEXT.get();
                INPUT_GLASS_CONTEXT.set(
                        new InputGlassContext(surface, "Attachment"));
                try {
                    return chain.proceed();
                } finally {
                    if (previous == null) INPUT_GLASS_CONTEXT.remove();
                    else INPUT_GLASS_CONTEXT.set(previous);
                }
            }
        });

        log("installed chat bubble customization ("
                + (googlePlay ? "google-play" : "mainland")
                + "), lifecycle-bound input/search/mode glass and attachment glass enabled");
    }

    private boolean isBubbleDark() {
        Activity activity = curAct.get();
        if (activity != null) {
            try { return DeekseepUi.isDark(activity); }
            catch (Throwable ignored) {}
        }
        try {
            int mode = android.content.res.Resources.getSystem()
                    .getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void deoptimizeBubbleMethod(Method method) {
        if (method == null) return;
        try {
            method.setAccessible(true);
            deoptimize(method);
        } catch (Throwable t) {
            log("bubble deopt failed " + method + ": " + t);
        }
    }

    private static void restoreBubbleContext(BubbleRenderContext previous) {
        if (previous == null) BUBBLE_RENDER_CONTEXT.remove();
        else BUBBLE_RENDER_CONTEXT.set(previous);
    }

    private static Method findBubbleMethod(
            Class<?> owner, String name, int parameterCount,
            int[] typeIndexes, Class<?>[] expectedTypes) throws NoSuchMethodException {
        for (Method method : owner.getDeclaredMethods()) {
            if (!name.equals(method.getName())
                    || method.getParameterTypes().length != parameterCount) {
                continue;
            }
            Class<?>[] actual = method.getParameterTypes();
            boolean matches = true;
            if (typeIndexes != null && expectedTypes != null) {
                for (int i = 0; i < typeIndexes.length; i++) {
                    if (actual[typeIndexes[i]] != expectedTypes[i]) {
                        matches = false;
                        break;
                    }
                }
            }
            if (matches) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name
                + "/" + parameterCount);
    }

    private static final class BubbleHostMapping {
        final boolean googlePlay;
        final String userOwner;
        final String userMethod;
        final String assistantBodyOwner;
        final String assistantBodyMethod;
        final String assistantOuterOwner;
        final String assistantOuterMethod;
        final String assistantResolvedOwner;
        final String assistantResolvedMethod;
        final String assistantRestartOwner;
        final String surfaceOwner;
        final String surfaceMethod;
        final String modifierClass;
        final String callbackClass;
        final String shapeClass;
        final String roundedOwner;
        final String roundedMethod;
        final String clipOwner;
        final String clipMethod;
        final String backgroundOwner;
        final String backgroundMethod;
        final String borderOwner;
        final String borderMethod;
        final String drawOwner;
        final String drawMethod;
        final String imageClass;
        final String drawScopeClass;
        final String drawImageMethod;
        final String unitClass;
        final String unitField;
        final String attachmentOwner;
        final String attachmentMethod;
        final String inputOwner;
        final String inputMethod;
        final String searchOwner;
        final String searchMethod;
        final String modeItemOwner;
        final String modeItemMethod;
        final String modeLabelOwner;
        final String modeLabelMethod;
        final String modeContainerOwner;
        final String modeContainerMethod;
        final String positionElementClass;
        final String coordinatesClass;
        final String coordinatesAttachedMethod;
        final String coordinatesSizeMethod;
        final String coordinatesWindowMethod;
        final String spatialElementClass;
        final String spatialScopeClass;
        final String spatialStateClass;
        final String spatialStatePolicyOwner;
        final String spatialStatePolicyField;
        final String spatialScaleXMethod;
        final String spatialScaleYMethod;
        final String spatialTranslationXMethod;
        final String spatialTranslationYMethod;
        final String spatialRotationXMethod;

        BubbleHostMapping(boolean googlePlay) {
            this.googlePlay = googlePlay;
            if (googlePlay) {
                userOwner = "xz9";
                userMethod = "c";
                assistantBodyOwner = "w3a";
                assistantBodyMethod = "b";
                assistantOuterOwner = "be4";
                assistantOuterMethod = "g";
                assistantResolvedOwner = "be4";
                assistantResolvedMethod = "f";
                assistantRestartOwner = "jt";
                surfaceOwner = "mz5";
                surfaceMethod = "F";
                modifierClass = "ci5";
                callbackClass = "kd3";
                shapeClass = "yh7";
                roundedOwner = "m27";
                roundedMethod = "a";
                clipOwner = "fa9";
                clipMethod = "F";
                backgroundOwner = "t59";
                backgroundMethod = "n";
                borderOwner = "u55";
                borderMethod = "o";
                drawOwner = "m12";
                drawMethod = "B";
                imageClass = "fe";
                drawScopeClass = "yo4";
                drawImageMethod = "f";
                unitClass = "vm8";
                unitField = "a";
                attachmentOwner = "ph6";
                attachmentMethod = "e";
                inputOwner = "oo0";
                inputMethod = "d";
                searchOwner = "g54";
                searchMethod = "m";
                modeItemOwner = "ds5";
                modeItemMethod = "t";
                modeLabelOwner = "ds5";
                modeLabelMethod = "v";
                modeContainerOwner = "av9";
                modeContainerMethod = "k0";
                positionElementClass = "dy5";
                coordinatesClass = "ho4";
                coordinatesAttachedMethod = "h";
                coordinatesSizeMethod = "j";
                coordinatesWindowMethod = "t";
                spatialElementClass = "bg0";
                spatialScopeClass = "b17";
                spatialStateClass = "v56";
                spatialStatePolicyOwner = "nr9";
                spatialStatePolicyField = "Y";
                spatialScaleXMethod = "j";
                spatialScaleYMethod = "k";
                spatialTranslationXMethod = "w";
                spatialTranslationYMethod = "x";
                spatialRotationXMethod = "h";
            } else if (HostCompat.isV230()) {
                // DeepSeek 2.3.0 upgraded Compose and R8 split several helpers that lived in
                // large 2.2.x utility classes.  Keep this mapping separate from the legacy
                // mainland table so one module APK can safely drive both host generations.
                userOwner = "g55";
                userMethod = "d";
                assistantBodyOwner = "le4";
                assistantBodyMethod = "d";
                assistantOuterOwner = "zj8";
                assistantOuterMethod = "M";
                assistantResolvedOwner = "zj8";
                assistantResolvedMethod = "M";
                assistantRestartOwner = "qt";
                surfaceOwner = "kt9";
                surfaceMethod = "b";
                modifierClass = "lj5";
                callbackClass = "td3";
                shapeClass = "ch7";
                roundedOwner = "y17";
                roundedMethod = "a";
                clipOwner = "nn0";
                clipMethod = "D";
                backgroundOwner = "vd0";
                backgroundMethod = "j";
                borderOwner = "cs1";
                borderMethod = "C";
                drawOwner = "vd0";
                drawMethod = "t";
                imageClass = "je";
                drawScopeClass = "hp4";
                drawImageMethod = "f";
                unitClass = "vl8";
                unitField = "a";
                attachmentOwner = "ab5";
                attachmentMethod = "f";
                inputOwner = "nn0";
                inputMethod = "a";
                searchOwner = "ky1";
                searchMethod = "q";
                modeItemOwner = "j65";
                modeItemMethod = "b";
                modeLabelOwner = "j65";
                modeLabelMethod = "d";
                modeContainerOwner = "zj8";
                modeContainerMethod = "M";
                positionElementClass = "fz5";
                coordinatesClass = "qo4";
                coordinatesAttachedMethod = "h";
                coordinatesSizeMethod = "j";
                coordinatesWindowMethod = "q";
                spatialElementClass = "bf0";
                spatialScopeClass = "o07";
                spatialStateClass = "w66";
                spatialStatePolicyOwner = "yt9";
                spatialStatePolicyField = "t";
                spatialScaleXMethod = "j";
                spatialScaleYMethod = "k";
                spatialTranslationXMethod = "s";
                spatialTranslationYMethod = "t";
                spatialRotationXMethod = "h";
            } else {
                userOwner = "dc5";
                userMethod = "c";
                assistantBodyOwner = "vh4";
                assistantBodyMethod = "f";
                assistantOuterOwner = "i39";
                assistantOuterMethod = "d";
                assistantResolvedOwner = "i39";
                assistantResolvedMethod = "c";
                assistantRestartOwner = "gt";
                surfaceOwner = "uq9";
                surfaceMethod = "h";
                modifierClass = "qg5";
                callbackClass = "ib3";
                shapeClass = "fe7";
                roundedOwner = "fz6";
                roundedMethod = "a";
                clipOwner = "uf0";
                clipMethod = "y";
                backgroundOwner = "i39";
                backgroundMethod = "u";
                borderOwner = "zp1";
                borderMethod = "o";
                drawOwner = "ld0";
                drawMethod = "A";
                imageClass = "ce";
                drawScopeClass = "sm4";
                drawImageMethod = "h";
                unitClass = "ui8";
                unitField = "a";
                attachmentOwner = "i85";
                attachmentMethod = "g";
                inputOwner = "uf0";
                inputMethod = "b";
                searchOwner = "fq1";
                searchMethod = "k";
                modeItemOwner = "p35";
                modeItemMethod = "e";
                modeLabelOwner = "p35";
                modeLabelMethod = "g";
                modeContainerOwner = "i39";
                modeContainerMethod = "S";
                positionElementClass = "lw5";
                coordinatesClass = "bm4";
                coordinatesAttachedMethod = "i";
                coordinatesSizeMethod = "k";
                coordinatesWindowMethod = "t";
                spatialElementClass = "re0";
                spatialScopeClass = "ux6";
                spatialStateClass = "c46";
                spatialStatePolicyOwner = "gn9";
                spatialStatePolicyField = "X";
                spatialScaleXMethod = "k";
                spatialScaleYMethod = "m";
                spatialTranslationXMethod = "u";
                spatialTranslationYMethod = "w";
                spatialRotationXMethod = "i";
            }
        }
    }

    private static final class InputGlassContext {
        final LiquidGlassEngine.SurfaceHandle surface;
        final String label;
        boolean modifierApplied;

        InputGlassContext(
                LiquidGlassEngine.SurfaceHandle surface, String label) {
            this.surface = surface;
            this.label = label;
        }
    }

    private static final class ModeGlassContext {
        final boolean selected;
        final boolean dark;
        final LiquidGlassEngine.SurfaceHandle surface;
        boolean modifierApplied;

        ModeGlassContext(
                boolean selected, boolean dark,
                LiquidGlassEngine.SurfaceHandle surface) {
            this.selected = selected;
            this.dark = dark;
            this.surface = surface;
        }
    }

    private static final class BubbleRenderContext {
        final ChatAppearance.BubbleStyle style;
        final boolean user;
        final boolean customSurface;
        final Object shape;
        final long fillColor;
        final long borderColor;
        final LiquidGlassEngine.SurfaceHandle glassSurface;
        boolean userModifierApplied;

        BubbleRenderContext(
                ChatAppearance.BubbleStyle style, boolean user,
                boolean customSurface, Object shape,
                long fillColor, long borderColor,
                LiquidGlassEngine.SurfaceHandle glassSurface) {
            this.style = style;
            this.user = user;
            this.customSurface = customSurface;
            this.shape = shape;
            this.fillColor = fillColor;
            this.borderColor = borderColor;
            this.glassSurface = glassSurface;
        }
    }

    private static final class BubbleComposeRuntime {
        private static final int SPATIAL_USER = 1;
        private static final int SPATIAL_ASSISTANT = 2;
        private static final int SPATIAL_INPUT = 3;

        final ClassLoader classLoader;
        final BubbleHostMapping mapping;
        final Class<?> modifierClass;
        final Class<?> callbackClass;
        final Class<?> shapeClass;
        final Method roundedMethod;
        final Method clipMethod;
        final Method backgroundMethod;
        final Method borderMethod;
        final Method drawWithContentMethod;
        final Constructor<?> imageConstructor;
        final Method drawContentMethod;
        final Method drawSizeMethod;
        final Method densityMethod;
        final Method drawImageMethod;
        final Constructor<?> positionElementConstructor;
        final Method modifierThenMethod;
        final Method coordinatesAttachedMethod;
        final Method coordinatesSizeMethod;
        final Method coordinatesWindowMethod;
        final Object unit;
        Constructor<?> spatialElementConstructor;
        Object spatialState;
        Method spatialStateGetValue;
        Method spatialStateSetValue;
        Method spatialScopeDensity;
        Method spatialScaleX;
        Method spatialScaleY;
        Method spatialTranslationX;
        Method spatialTranslationY;
        Method spatialRotationX;
        final Object[] spatialElements = new Object[4];
        ChatAppearance.SpatialPoseListener spatialPoseListener;
        boolean inputCoordinateProbeLogged;
        boolean inputLocalGlassLogged;
        boolean searchLocalGlassLogged;
        boolean modeLocalGlassLogged;
        boolean assistantModifierLogged;
        boolean assistantActionStateFailureLogged;
        boolean spatialLayerFailureLogged;
        int spatialLayerAppliedMask;

        BubbleComposeRuntime(
                ClassLoader classLoader, BubbleHostMapping mapping) throws Exception {
            this.classLoader = classLoader;
            this.mapping = mapping;
            modifierClass = classLoader.loadClass(mapping.modifierClass);
            callbackClass = classLoader.loadClass(mapping.callbackClass);
            shapeClass = classLoader.loadClass(mapping.shapeClass);
            roundedMethod = classLoader.loadClass(mapping.roundedOwner)
                    .getDeclaredMethod(mapping.roundedMethod, float.class);
            clipMethod = classLoader.loadClass(mapping.clipOwner)
                    .getDeclaredMethod(mapping.clipMethod, modifierClass, shapeClass);
            backgroundMethod = classLoader.loadClass(mapping.backgroundOwner)
                    .getDeclaredMethod(
                            mapping.backgroundMethod,
                            modifierClass, long.class, shapeClass);
            borderMethod = classLoader.loadClass(mapping.borderOwner)
                    .getDeclaredMethod(
                            mapping.borderMethod,
                            modifierClass, float.class, long.class, shapeClass);
            drawWithContentMethod = classLoader.loadClass(mapping.drawOwner)
                    .getDeclaredMethod(
                            mapping.drawMethod, modifierClass, callbackClass);
            imageConstructor = classLoader.loadClass(mapping.imageClass)
                    .getDeclaredConstructor(android.graphics.Bitmap.class);
            Class<?> drawScope = classLoader.loadClass(mapping.drawScopeClass);
            drawContentMethod = drawScope.getDeclaredMethod("a");
            drawSizeMethod = drawScope.getDeclaredMethod("d");
            densityMethod = drawScope.getDeclaredMethod("getDensity");
            drawImageMethod = findBubbleMethod(
                    drawScope, mapping.drawImageMethod, 9, null, null);
            Class<?> positionElementClass =
                    classLoader.loadClass(mapping.positionElementClass);
            positionElementConstructor =
                    positionElementClass.getDeclaredConstructor(callbackClass);
            modifierThenMethod = modifierClass.getMethod(
                    !mapping.googlePlay && HostCompat.isV230() ? "s" : "w",
                    modifierClass);
            Class<?> coordinatesClass =
                    classLoader.loadClass(mapping.coordinatesClass);
            coordinatesAttachedMethod = coordinatesClass.getMethod(
                    mapping.coordinatesAttachedMethod);
            coordinatesSizeMethod = coordinatesClass.getMethod(
                    mapping.coordinatesSizeMethod);
            coordinatesWindowMethod = coordinatesClass.getMethod(
                    mapping.coordinatesWindowMethod, long.class);
            Field unitField = classLoader.loadClass(mapping.unitClass)
                    .getDeclaredField(mapping.unitField);
            unitField.setAccessible(true);
            unit = unitField.get(null);
            roundedMethod.setAccessible(true);
            clipMethod.setAccessible(true);
            backgroundMethod.setAccessible(true);
            borderMethod.setAccessible(true);
            drawWithContentMethod.setAccessible(true);
            imageConstructor.setAccessible(true);
            drawContentMethod.setAccessible(true);
            drawSizeMethod.setAccessible(true);
            densityMethod.setAccessible(true);
            drawImageMethod.setAccessible(true);
            positionElementConstructor.setAccessible(true);
            modifierThenMethod.setAccessible(true);
            coordinatesAttachedMethod.setAccessible(true);
            coordinatesSizeMethod.setAccessible(true);
            coordinatesWindowMethod.setAccessible(true);
            // Foreground parallax is owned by one host View root. Do not register a per-frame
            // Compose state listener or attach child graphicsLayer modifiers to chat/input nodes.
            if (ChatAppearance.composeSpatialModifiersEnabled()) {
                initializeSpatialRuntime();
            }
        }

        private void initializeSpatialRuntime() {
            try {
                Class<?> elementClass =
                        classLoader.loadClass(mapping.spatialElementClass);
                spatialElementConstructor =
                        elementClass.getDeclaredConstructor(callbackClass);
                spatialElementConstructor.setAccessible(true);

                Class<?> stateClass =
                        classLoader.loadClass(mapping.spatialStateClass);
                Field policyField = classLoader
                        .loadClass(mapping.spatialStatePolicyOwner)
                        .getDeclaredField(mapping.spatialStatePolicyField);
                policyField.setAccessible(true);
                Object policy = policyField.get(null);
                Constructor<?> stateConstructor = null;
                for (Constructor<?> candidate
                        : stateClass.getDeclaredConstructors()) {
                    Class<?>[] types = candidate.getParameterTypes();
                    if (types.length == 2 && types[0] == Object.class
                            && policy != null
                            && types[1].isInstance(policy)) {
                        stateConstructor = candidate;
                        break;
                    }
                }
                if (stateConstructor == null) {
                    throw new NoSuchMethodException(
                            stateClass.getName() + "(Object, policy)");
                }
                stateConstructor.setAccessible(true);
                spatialState = stateConstructor.newInstance(
                        ChatAppearance.currentSpatialPose(), policy);
                spatialStateGetValue = stateClass.getMethod("getValue");
                spatialStateSetValue =
                        stateClass.getMethod("setValue", Object.class);

                Class<?> scopeClass =
                        classLoader.loadClass(mapping.spatialScopeClass);
                spatialScopeDensity = scopeClass.getMethod("getDensity");
                spatialScaleX = scopeClass.getMethod(
                        mapping.spatialScaleXMethod, float.class);
                spatialScaleY = scopeClass.getMethod(
                        mapping.spatialScaleYMethod, float.class);
                spatialTranslationX = scopeClass.getMethod(
                        mapping.spatialTranslationXMethod, float.class);
                spatialTranslationY = scopeClass.getMethod(
                        mapping.spatialTranslationYMethod, float.class);
                spatialRotationX = scopeClass.getMethod(
                        mapping.spatialRotationXMethod, float.class);

                spatialPoseListener =
                        new ChatAppearance.SpatialPoseListener() {
                            @Override public void onSpatialPose(
                                    ChatAppearance.SpatialPose pose) {
                                try {
                                    spatialStateSetValue.invoke(
                                            spatialState, pose);
                                } catch (Throwable t) {
                                    if (!spatialLayerFailureLogged) {
                                        spatialLayerFailureLogged = true;
                                        log("spatial Compose state update failed: " + t);
                                    }
                                }
                            }
                        };
                ChatAppearance.registerSpatialPoseListener(
                        spatialPoseListener);
                log("spatial Compose layer ready ("
                        + (mapping.googlePlay ? "google-play" : "mainland")
                        + ")");
            } catch (Throwable t) {
                spatialElementConstructor = null;
                spatialState = null;
                log("spatial Compose layer unavailable: " + t);
            }
        }

        BubbleRenderContext newContext(
                ChatAppearance.BubbleStyle style, boolean user, boolean dark)
                throws Exception {
            boolean custom = !"original".equals(style.preset);
            Object shape = custom
                    ? roundedMethod.invoke(null, style.radius)
                    : null;
            if (custom && shape == null) custom = false;
            int fill = custom
                    ? ChatAppearance.bubbleFillColor(style, user, dark)
                    : 0;
            int border = custom
                    ? ChatAppearance.bubbleBorderColor(style, user, dark)
                    : 0;
            LiquidGlassEngine.SurfaceHandle glassSurface =
                    ChatAppearance.glassEnabledForRender()
                    ? LiquidGlassEngine.registerSurface(
                            user ? LiquidGlassEngine.KIND_USER_BUBBLE
                                    : LiquidGlassEngine.KIND_ASSISTANT_BUBBLE,
                            style.radius)
                    : null;
            return new BubbleRenderContext(
                    style, user, custom, shape,
                    composeColor(fill), composeColor(border), glassSurface);
        }

        private Object attachSpatialModifier(
                Object modifier, final int layerKind) {
            if (modifier == null || !modifierClass.isInstance(modifier)
                    || spatialElementConstructor == null
                    || spatialState == null
                    || layerKind <= 0 || layerKind >= spatialElements.length) {
                return modifier;
            }
            // The single host Compose root is the coherent middle plane. Per-message graphics
            // layers made scrolling conversations look gelatinous and prevented one clean
            // foreground occlusion edge, so only the nearest input plane gets a local delta.
            if (layerKind != SPATIAL_INPUT) return modifier;
            try {
                Object element = spatialElements[layerKind];
                if (element == null) {
                    synchronized (spatialElements) {
                        element = spatialElements[layerKind];
                        if (element == null) {
                            Object callback = Proxy.newProxyInstance(
                                    classLoader,
                                    new Class<?>[]{callbackClass},
                                    new InvocationHandler() {
                                        @Override public Object invoke(
                                                Object proxy, Method method,
                                                Object[] args) throws Throwable {
                                            String name = method.getName();
                                            if ("toString".equals(name)) {
                                                return "DeekseepSpatialLayer("
                                                        + layerKind + ")";
                                            }
                                            if ("hashCode".equals(name)) {
                                                return System.identityHashCode(proxy);
                                            }
                                            if ("equals".equals(name)) {
                                                return proxy == (args == null
                                                        || args.length == 0
                                                        ? null : args[0]);
                                            }
                                            if ("g".equals(name)
                                                    && args != null
                                                    && args.length == 1
                                                    && args[0] != null) {
                                                try {
                                                    Object value =
                                                            spatialStateGetValue.invoke(
                                                                    spatialState);
                                                    ChatAppearance.SpatialPose pose =
                                                            value instanceof
                                                                    ChatAppearance.SpatialPose
                                                            ? (ChatAppearance.SpatialPose) value
                                                            : ChatAppearance.SpatialPose.DISABLED;
                                                    applySpatialLayer(
                                                            args[0], pose, layerKind);
                                                } catch (Throwable t) {
                                                    if (!spatialLayerFailureLogged) {
                                                        spatialLayerFailureLogged = true;
                                                        log("spatial graphics layer failed: " + t);
                                                    }
                                                }
                                            }
                                            return unit;
                                        }
                                    });
                            element = spatialElementConstructor.newInstance(
                                    callback);
                            spatialElements[layerKind] = element;
                        }
                    }
                }
                return modifierThenMethod.invoke(modifier, element);
            } catch (Throwable t) {
                if (!spatialLayerFailureLogged) {
                    spatialLayerFailureLogged = true;
                    log("spatial modifier attach failed: " + t);
                }
                return modifier;
            }
        }

        private void applySpatialLayer(
                Object scope, ChatAppearance.SpatialPose pose,
                int layerKind) throws Exception {
            boolean active = pose != null && pose.active;
            float distanceXDp;
            float distanceYDp;
            float maxPitchDegrees;
            float baseScale;
            if (layerKind == SPATIAL_INPUT) {
                // This node is nested in the transformed middle plane; add only near minus middle.
                // With the 1.25x preset the combined maxima are 5.0/3.375 dp and 0.25 degrees.
                distanceXDp = ChatAppearance.SPATIAL_INPUT_X_DP
                        - ChatAppearance.SPATIAL_CONTENT_X_DP;
                distanceYDp = ChatAppearance.SPATIAL_INPUT_Y_DP
                        - ChatAppearance.SPATIAL_CONTENT_Y_DP;
                maxPitchDegrees =
                        ChatAppearance.SPATIAL_INPUT_ROTATION_DEGREES
                        - ChatAppearance.SPATIAL_CONTENT_ROTATION_DEGREES;
                baseScale = ChatAppearance.SPATIAL_INPUT_EXTRA_BASE_SCALE;
            } else {
                distanceXDp = 0f;
                distanceYDp = 0f;
                maxPitchDegrees = 0f;
                baseScale = 1f;
            }
            float x = active ? pose.x : 0f;
            float y = active ? pose.y : 0f;
            float density = ((Number) spatialScopeDensity.invoke(
                    scope)).floatValue();
            if (Float.isNaN(density) || Float.isInfinite(density)
                    || density <= 0f) {
                density = android.content.res.Resources.getSystem()
                        .getDisplayMetrics().density;
            }
            float magnitude = Math.min(
                    1.25f, (float) Math.sqrt(x * x + y * y));
            float scale = active
                    ? baseScale + magnitude * 0.0008f : 1f;
            spatialScaleX.invoke(scope, scale);
            spatialScaleY.invoke(scope, scale);
            spatialTranslationX.invoke(
                    scope, x * distanceXDp * density);
            spatialTranslationY.invoke(
                    scope, y * distanceYDp * density);
            // The host's other exposed rotation setter turns the node in the screen plane. It is
            // intentionally never resolved or invoked; the spatial scene has no planar rotation.
            spatialRotationX.invoke(
                    scope, -y * maxPitchDegrees);
            int appliedBit = 1 << layerKind;
            if ((spatialLayerAppliedMask & appliedBit) == 0) {
                spatialLayerAppliedMask |= appliedBit;
                String label = layerKind == SPATIAL_INPUT
                        ? "input" : (layerKind == SPATIAL_USER
                        ? "user-bubble" : "assistant-bubble");
                log("spatial graphics layer applied: "
                        + label + " active=" + active);
            }
        }

        Object decorateModifier(Object modifier, BubbleRenderContext context) {
            if (modifier == null || !modifierClass.isInstance(modifier)) return modifier;
            Object result = attachSpatialModifier(
                    modifier, context.user ? SPATIAL_USER : SPATIAL_ASSISTANT);
            try {
                Object positionElement = positionElement(
                        context.glassSurface, "Bubble");
                if (positionElement != null) {
                    result = modifierThenMethod.invoke(result, positionElement);
                }
                Object callback = decorationCallback(context);
                if (callback != null) {
                    result = drawWithContentMethod.invoke(null, result, callback);
                }
                if (context.customSurface
                        && context.style.borderWidth > 0f
                        && context.borderColor != 0L
                        && context.glassSurface == null) {
                    result = borderMethod.invoke(
                            null, result, context.style.borderWidth,
                            context.borderColor, context.shape);
                }
            } catch (Throwable t) {
                log("bubble modifier decoration failed: " + t);
            }
            return result;
        }

        /**
         * Applies assistant styling at the response container, once per message.  In particular,
         * this deliberately does not enter DeepSeek's feedback-button Surface calls.
         */
        Object decorateAssistantModifier(
                Object modifier, BubbleRenderContext context) {
            if (modifier == null || !modifierClass.isInstance(modifier)) {
                return modifier;
            }
            Object result = attachSpatialModifier(
                    modifier, SPATIAL_ASSISTANT);
            try {
                // When global glass is disabled, retain the configured solid/soft bubble style.
                // With glass enabled the shared compositor supplies the material, so adding an
                // opaque outer background here would also tint the native action row.
                if (context.customSurface && context.glassSurface == null) {
                    result = backgroundMethod.invoke(
                            null, result, context.fillColor, context.shape);
                    if (context.style.borderWidth > 0f
                            && context.borderColor != 0L) {
                        result = borderMethod.invoke(
                                null, result, context.style.borderWidth,
                                context.borderColor, context.shape);
                    }
                }
                Object positionElement = positionElement(
                        context.glassSurface, "AssistantBubble");
                if (positionElement != null) {
                    result = modifierThenMethod.invoke(result, positionElement);
                }
                Object callback = decorationCallback(context);
                if (callback != null) {
                    result = drawWithContentMethod.invoke(null, result, callback);
                }
                if (!assistantModifierLogged) {
                    assistantModifierLogged = true;
                    log("assistant bubble modifier applied once at response container");
                }
            } catch (Throwable t) {
                log("assistant bubble modifier decoration failed: " + t);
            }
            return result;
        }

        boolean assistantHasActionRow(Object responseState) {
            if (responseState == null) return false;
            try {
                Field field = responseState.getClass().getDeclaredField("d");
                field.setAccessible(true);
                return field.getBoolean(responseState);
            } catch (Throwable t) {
                if (!assistantActionStateFailureLogged) {
                    assistantActionStateFailureLogged = true;
                    log("assistant action-row state probe unavailable: " + t);
                }
                return false;
            }
        }

        Object attachBoundsModifier(
                Object modifier, LiquidGlassEngine.SurfaceHandle surface,
                String label) {
            if (modifier == null || !modifierClass.isInstance(modifier)
                    || surface == null) {
                return modifier;
            }
            try {
                Object element = positionElement(surface, label);
                return element == null
                        ? modifier : modifierThenMethod.invoke(modifier, element);
            } catch (Throwable t) {
                log("glass bounds modifier attach failed for " + label + ": " + t);
                return modifier;
            }
        }

        /**
         * A very light neutral base follows the real selector node. The shared refracting layer
         * now covers the text and supplies the visible material; this base only prevents a
         * one-frame colour hole while Compose moves the selected item.
         */
        Object decorateModeModifier(
                Object modifier, boolean selected, boolean dark) {
            if (modifier == null || !modifierClass.isInstance(modifier)) {
                return modifier;
            }
            try {
                Object shape = roundedMethod.invoke(null, 13f);
                int fill;
                int edge;
                if (dark) {
                    fill = selected ? 0x18FFFFFF : 0x03FFFFFF;
                    edge = selected ? 0x34FFFFFF : 0x10FFFFFF;
                } else {
                    fill = selected ? 0x20FFFFFF : 0x03FFFFFF;
                    edge = selected ? 0x38FFFFFF : 0x10FFFFFF;
                }
                Object result = backgroundMethod.invoke(
                        null, modifier, composeColor(fill), shape);
                Object decorated = borderMethod.invoke(
                        null, result, selected ? 0.72f : 0.45f,
                        composeColor(edge), shape);
                if (!modeLocalGlassLogged) {
                    modeLocalGlassLogged = true;
                    log("mode glass local material applied behind text");
                }
                return decorated;
            } catch (Throwable t) {
                log("mode glass modifier decoration failed: " + t);
                return modifier;
            }
        }

        Object decorateInputModifier(
                Object modifier, boolean dark,
                LiquidGlassEngine.SurfaceHandle surface) {
            Object result = attachSpatialModifier(
                    modifier, SPATIAL_INPUT);
            if (surface == null) return result;
            result = decorateLocalGlass(
                    result, 22f,
                    dark ? 0x10FFFFFF : 0x12FFFFFF,
                    dark ? 0x32FFFFFF : 0x36FFFFFF,
                    0.52f, "input");
            return attachBoundsModifier(result, surface, "Input");
        }

        Object decorateSearchModifier(
                Object modifier, boolean dark,
                LiquidGlassEngine.SurfaceHandle surface) {
            Object result = decorateLocalGlass(
                    modifier, 16f,
                    dark ? 0x0EFFFFFF : 0x10FFFFFF,
                    dark ? 0x2EFFFFFF : 0x32FFFFFF,
                    0.48f, "conversation search");
            return attachBoundsModifier(result, surface, "SidebarSearch");
        }

        /** Adds only the almost-transparent neutral base below the global refracting layer. */
        private Object decorateLocalGlass(
                Object modifier, float radiusDp, int fill, int edge,
                float edgeWidthDp, String label) {
            if (modifier == null || !modifierClass.isInstance(modifier)) {
                return modifier;
            }
            try {
                Object shape = roundedMethod.invoke(null, radiusDp);
                Object result = backgroundMethod.invoke(
                        null, modifier, composeColor(fill), shape);
                Object decorated = borderMethod.invoke(
                        null, result, edgeWidthDp, composeColor(edge), shape);
                if ("input".equals(label) && !inputLocalGlassLogged) {
                    inputLocalGlassLogged = true;
                    log("input glass local material applied behind text");
                } else if ("conversation search".equals(label)
                        && !searchLocalGlassLogged) {
                    searchLocalGlassLogged = true;
                    log("sidebar search glass local material applied behind text");
                }
                return decorated;
            } catch (Throwable t) {
                log(label + " local glass decoration failed: " + t);
                return modifier;
            }
        }

        private Object positionElement(
                final LiquidGlassEngine.SurfaceHandle surface,
                final String label) {
            if (surface == null) return null;
            try {
                Object callback = Proxy.newProxyInstance(
                        classLoader, new Class<?>[]{callbackClass},
                        new InvocationHandler() {
                            @Override public Object invoke(
                                    Object proxy, Method method, Object[] args)
                                    throws Throwable {
                                String name = method.getName();
                                if ("toString".equals(name)) {
                                    return "DeekseepLiquidGlassBounds(" + label + ")";
                                }
                                if ("hashCode".equals(name)) {
                                    return System.identityHashCode(proxy);
                                }
                                if ("equals".equals(name)) {
                                    return proxy == (args == null || args.length == 0
                                            ? null : args[0]);
                                }
                                if ("g".equals(name) && args != null
                                        && args.length == 1 && args[0] != null) {
                                    captureGlassBounds(surface, args[0], label);
                                }
                                return unit;
                            }
                        });
                Object element = positionElementConstructor.newInstance(callback);
                surface.bindOwner(element);
                return element;
            } catch (Throwable t) {
                log("glass bounds modifier failed for " + label + ": " + t);
                return null;
            }
        }

        private void captureGlassBounds(
                LiquidGlassEngine.SurfaceHandle surface, Object coordinates,
                String label) {
            try {
                if (!Boolean.TRUE.equals(
                        coordinatesAttachedMethod.invoke(coordinates))) {
                    return;
                }
                long size = ((Number) coordinatesSizeMethod.invoke(
                        coordinates)).longValue();
                int width = (int) (size >> 32);
                int height = (int) (size & 0xFFFFFFFFL);
                long position = ((Number) coordinatesWindowMethod.invoke(
                        coordinates, 0L)).longValue();
                if ("Input".equals(label) && !inputCoordinateProbeLogged) {
                    inputCoordinateProbeLogged = true;
                    logCoordinateProbe(coordinates, size);
                }
                float rawX = Float.intBitsToFloat((int) (position >> 32));
                float rawY = Float.intBitsToFloat(
                        (int) (position & 0xFFFFFFFFL));
                int directLeft = Math.round(rawX);
                int directTop = Math.round(rawY);
                int inverseLeft = -directLeft;
                int inverseTop = -directTop;
                android.util.DisplayMetrics metrics =
                        android.content.res.Resources.getSystem()
                                .getDisplayMetrics();
                float directScore = visibleBoundsScore(
                        directLeft, directTop, width, height,
                        metrics.widthPixels, metrics.heightPixels);
                float inverseScore = visibleBoundsScore(
                        inverseLeft, inverseTop, width, height,
                        metrics.widthPixels, metrics.heightPixels);
                int left = directScore >= inverseScore
                        ? directLeft : inverseLeft;
                int top = directScore >= inverseScore
                        ? directTop : inverseTop;
                if (width > 0 && height > 0) {
                    surface.setBounds(left, top, left + width, top + height);
                }
            } catch (Throwable t) {
                log("glass bounds capture failed for " + label + ": " + t);
            }
        }

        private void logCoordinateProbe(Object coordinates, long size) {
            try {
                StringBuilder out = new StringBuilder(
                        "input coordinate probe class=")
                        .append(coordinates.getClass().getName())
                        .append(" size=")
                        .append((int) (size >> 32))
                        .append("x")
                        .append((int) (size & 0xFFFFFFFFL));
                String[] names = new String[]{"F", "H", "b", "t", "w"};
                for (String name : names) {
                    try {
                        Method method = coordinates.getClass()
                                .getMethod(name, long.class);
                        long packed = ((Number) method.invoke(
                                coordinates, 0L)).longValue();
                        out.append(" ")
                                .append(name)
                                .append("=")
                                .append(Float.intBitsToFloat(
                                        (int) (packed >> 32)))
                                .append(",")
                                .append(Float.intBitsToFloat(
                                        (int) (packed & 0xFFFFFFFFL)));
                    } catch (Throwable ignored) {}
                }
                Main.log(out.toString());
            } catch (Throwable t) {
                Main.log("input coordinate probe failed: " + t);
            }
        }

        private static float visibleBoundsScore(
                int left, int top, int width, int height,
                int screenWidth, int screenHeight) {
            int right = left + Math.max(1, width);
            int bottom = top + Math.max(1, height);
            int intersectionWidth = Math.max(
                    0, Math.min(right, screenWidth) - Math.max(left, 0));
            int intersectionHeight = Math.max(
                    0, Math.min(bottom, screenHeight) - Math.max(top, 0));
            float area = Math.max(1f, (float) width * (float) height);
            float score = intersectionWidth * (float) intersectionHeight / area * 10f;
            if (left >= -2) score += 1f;
            if (top >= -2) score += 1f;
            if (right <= screenWidth + 2) score += 1f;
            if (bottom <= screenHeight + 2) score += 1f;
            return score;
        }

        private Object decorationCallback(final BubbleRenderContext context) {
            final ChatAppearance.BubbleStyle style = context.style;
            if (!style.hasDecoration()) return null;
            File file = ChatAppearance.assetFile(style.decorationFile);
            if (!file.isFile()) return null;
            String key = (mapping.googlePlay ? "gp|" : "cn|")
                    + (context.user ? "u|" : "a|")
                    + file.getAbsolutePath() + "|" + file.lastModified() + "|"
                    + Float.floatToIntBits(style.decorationSize) + "|"
                    + Float.floatToIntBits(style.decorationX) + "|"
                    + Float.floatToIntBits(style.decorationOpacity) + "|"
                    + Float.floatToIntBits(style.decorationRotation);
            Object cached = BUBBLE_DRAW_CALLBACKS.get(key);
            if (cached != null) return cached;

            android.graphics.Bitmap bitmap =
                    ChatAppearance.loadBitmap(file, 512, 512);
            if (bitmap == null) return null;
            if (Math.abs(style.decorationRotation) > 0.05f) {
                try {
                    android.graphics.Matrix matrix = new android.graphics.Matrix();
                    matrix.postRotate(style.decorationRotation);
                    android.graphics.Bitmap rotated = android.graphics.Bitmap.createBitmap(
                            bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                            matrix, true);
                    if (rotated != bitmap) bitmap = rotated;
                } catch (Throwable t) {
                    log("bubble decoration rotation failed: " + t);
                }
            }
            final android.graphics.Bitmap renderedBitmap = bitmap;
            final Object image;
            try {
                image = imageConstructor.newInstance(renderedBitmap);
            } catch (Throwable t) {
                log("bubble decoration image wrapper failed: " + t);
                return null;
            }

            Object callback = Proxy.newProxyInstance(
                    classLoader, new Class<?>[]{callbackClass},
                    new InvocationHandler() {
                        boolean drawFailureLogged;

                        @Override public Object invoke(
                                Object proxy, Method method, Object[] args)
                                throws Throwable {
                            String name = method.getName();
                            if ("toString".equals(name)) {
                                return "DeekseepBubbleDecoration";
                            }
                            if ("hashCode".equals(name)) {
                                return System.identityHashCode(proxy);
                            }
                            if ("equals".equals(name)) {
                                return proxy == (args == null || args.length == 0
                                        ? null : args[0]);
                            }
                            if ("g".equals(name) && args != null
                                    && args.length == 1 && args[0] != null) {
                                Object scope = args[0];
                                try {
                                    drawContentMethod.invoke(scope);
                                } catch (java.lang.reflect.InvocationTargetException t) {
                                    Throwable cause = t.getCause();
                                    throw cause == null ? t : cause;
                                }
                                try {
                                    drawDecoration(
                                            scope, image, renderedBitmap, style);
                                } catch (Throwable t) {
                                    if (!drawFailureLogged) {
                                        drawFailureLogged = true;
                                        log("bubble decoration draw failed: " + t);
                                    }
                                }
                                return unit;
                            }
                            return unit;
                        }
                    });
            if (BUBBLE_DRAW_CALLBACKS.size() > 48) {
                BUBBLE_DRAW_CALLBACKS.clear();
            }
            Object previous = BUBBLE_DRAW_CALLBACKS.putIfAbsent(key, callback);
            return previous == null ? callback : previous;
        }

        private void drawDecoration(
                Object scope, Object image, android.graphics.Bitmap bitmap,
                ChatAppearance.BubbleStyle style) throws Exception {
            float density = ((Number) densityMethod.invoke(scope)).floatValue();
            long packedSize = ((Number) drawSizeMethod.invoke(scope)).longValue();
            float bubbleWidth =
                    Float.intBitsToFloat((int) (packedSize >> 32));
            float box = Math.max(1f, style.decorationSize * density);
            float scale = Math.min(
                    box / Math.max(1, bitmap.getWidth()),
                    box / Math.max(1, bitmap.getHeight()));
            int width = Math.max(1, Math.round(bitmap.getWidth() * scale));
            int height = Math.max(1, Math.round(bitmap.getHeight() * scale));
            int x = Math.round(Math.max(0f, bubbleWidth - width)
                    * style.decorationX);
            int y = -Math.round(height * 0.30f);
            long sourceSize = packIntPair(bitmap.getWidth(), bitmap.getHeight());
            long destinationOffset = packIntPair(x, y);
            long destinationSize = packIntPair(width, height);
            drawImageMethod.invoke(
                    scope, image, 0L, sourceSize,
                    destinationOffset, destinationSize,
                    style.decorationOpacity, null, 3, 1);
        }

        private static long packIntPair(int first, int second) {
            return (((long) first) << 32) | (((long) second) & 0xFFFFFFFFL);
        }

        private static long composeColor(int argb) {
            return (((long) argb) & 0xFFFFFFFFL) << 32;
        }
    }

    /**
     * The host normally stores a server-relative value in fp.signed_path.  us.a(host) then
     * turns that value into https://host/api{signed_path}.  Editor gallery images deliberately
     * use the app's own FileProvider instead, so passing them through the server URL builder
     * produces an invalid https URL even though the durable file and cache mirror are intact.
     * Keep the host path untouched for every normal attachment and unwrap only our private,
     * narrowly-scoped FileProvider prefix.
     */
    private void hookLocalEditorImageUris(final ClassLoader cl) {
        try {
            Class<?> imagePath = HostCompat.load(cl, "us");
            final Field signedPath = imagePath.getDeclaredField("b");
            signedPath.setAccessible(true);
            Method resolve = imagePath.getDeclaredMethod("a", String.class);
            resolve.setAccessible(true);
            hook(resolve).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object raw = signedPath.get(chain.getThisObject());
                    if (raw instanceof String
                            && ((String) raw).startsWith(EDITOR_IMAGE_URI_PREFIX)) {
                        Uri local = Uri.parse((String) raw);
                        log("resolved local editor image uri=" + local.getLastPathSegment());
                        return local;
                    }
                    return chain.proceed();
                }
            });
            log("hooked local editor image URI resolver");
        } catch (Throwable t) {
            log("hook local editor image URI resolver failed: " + t);
        }
    }

    // ── 侧栏聊天记录多选删除（modern Compose Hooker 版）────────────────

    static boolean isChatMultiSelect() {
        return new File(CHAT_MULTISELECT_FILE).exists();
    }

    static void setChatMultiSelect(boolean on) {
        try {
            File ef = new File(CHAT_MULTISELECT_FILE);
            if (on) overwriteTextFile(CHAT_MULTISELECT_FILE, "");
            else {
                ef.delete();
                exitSidebarSelectMode();
            }
        } catch (Throwable ignored) {}
    }

    // 会话行渲染器 mc.e(tp,..,xa3 click,..,xa3 delete,..,qg5 modifier,..) 12 参。
    // modern：拦到后按需改 args[4]=长按代理、args[9]=追加坐标捕获的 Modifier，再一次性 proceed(args)。
    private void hookSidebarMultiSelectDelete(final ClassLoader cl) {
        try {
            final Class<?> mc = HostCompat.load(cl, "mc");
            final Class<?> tp = HostCompat.load(cl, "tp");
            final Class<?> xa3 = HostCompat.load(cl, "xa3");
            int n = 0;
            for (Method m : mc.getDeclaredMethods()) {
                Class<?>[] pts = m.getParameterTypes();
                if (!m.getName().equals(HostCompat.method("mc", "e"))
                        || pts.length != 12 || pts[0] != tp) continue;
                if (!xa3.isAssignableFrom(pts[4]) || !xa3.isAssignableFrom(pts[7])) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object[] args = null;
                        try {
                            Object[] a = chain.getArgs().toArray();
                            final Object tpObj = a[0];
                            final String sid = String.valueOf(fieldByName(tpObj, "a"));
                            if (sid != null && sid.length() > 0 && !"null".equals(sid)) {
                                boolean active = Boolean.TRUE.equals(a[2]);
                                if (active) {
                                    String oldSid = sidebarCurrentSid;
                                    sidebarCurrentSid = sid;
                                    if (sidebarSelectMode && oldSid != null && !oldSid.equals(sid)) {
                                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                                            public void run() { slideOutSidebarOverlayAndExit(); }
                                        });
                                    }
                                }
                                synchronized (SIDEBAR_DELETE_ACTIONS) {
                                    if (a[3] != null) SIDEBAR_CLICK_ACTIONS.put(sid, a[3]);
                                    if (a[7] != null) SIDEBAR_DELETE_ACTIONS.put(sid, a[7]);
                                }
                                boolean multiSelect = isChatMultiSelect();
                                boolean changed = false;
                                if (multiSelect) {
                                    a[4] = buildSidebarLongPressProxy(cl, sid);
                                    changed = true;
                                }
                                if (multiSelect && a.length > 9 && a[9] != null) {
                                    Object wrapped = wrapModifierWithBoundsCapture(cl, sid, a[9]);
                                    if (wrapped != null) {
                                        a[9] = wrapped;
                                        changed = true;
                                    }
                                }
                                if (changed) {
                                    args = a;
                                }
                            }
                        } catch (Throwable t) { log("sidebar multi-select hook row err: " + t); }
                        return args != null ? chain.proceed(args) : chain.proceed();
                    }
                });
                n++;
            }
            log("installed sidebar multi-select delete hook mc.e x" + n);
        } catch (Throwable t) { log("hookSidebarMultiSelectDelete failed: " + t); }
    }

    private Object buildSidebarLongPressProxy(final ClassLoader cl, final String sid) throws Exception {
        final Class<?> xa3 = HostCompat.load(cl, "xa3");
        return Proxy.newProxyInstance(cl, new Class[]{xa3}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("toString".equals(name)) return "DeekseepSidebarMultiSelect";
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                if ("u".equals(name) && method.getParameterTypes().length == 0) {
                    final Activity act = curAct.get();
                    if (act != null) {
                        act.runOnUiThread(new Runnable() {
                            public void run() { enterSidebarSelectMode(act, sid); }
                        });
                    }
                    return ui8Unit(cl);
                }
                return ui8Unit(cl);
            }
        });
    }

    // 把 onGloballyPositioned(callback) 追加到会话行的 Modifier(qg5) 上：modifier.then(new lw5(cb))
    private Object wrapModifierWithBoundsCapture(ClassLoader cl, String sid, Object modifier) {
        try {
            Class<?> qg5 = HostCompat.load(cl, "qg5");
            if (!qg5.isInstance(modifier)) return null;
            Class<?> ib3 = HostCompat.load(cl, "ib3");
            Class<?> lw5 = HostCompat.load(cl, "lw5");
            Object cb;
            synchronized (SIDEBAR_BOUNDS_CB) {
                cb = SIDEBAR_BOUNDS_CB.get(sid);
                if (cb == null) { cb = buildBoundsCallback(cl, sid); SIDEBAR_BOUNDS_CB.put(sid, cb); }
            }
            java.lang.reflect.Constructor<?> ctor = lw5.getDeclaredConstructor(ib3);
            ctor.setAccessible(true);
            Object element = ctor.newInstance(cb);
            Method w = qg5.getMethod(HostCompat.method("qg5", "w"), qg5);
            return w.invoke(modifier, element);
        } catch (Throwable t) { log("wrap sidebar bounds capture failed: " + t); return null; }
    }

    // ib3(Function1) 代理：Compose 布局后回调 g(bm4 coords)，把行的窗口坐标写入 SIDEBAR_ROW_BOUNDS
    private Object buildBoundsCallback(final ClassLoader cl, final String sid) throws Exception {
        final Class<?> ib3 = HostCompat.load(cl, "ib3");
        final Class<?> bm4 = HostCompat.load(cl, "bm4");
        if (BM4_I == null) {
            BM4_I = bm4.getMethod(HostCompat.method("bm4", "i"));
            BM4_K = bm4.getMethod(HostCompat.method("bm4", "k"));
            BM4_W = bm4.getMethod(HostCompat.method("bm4", "w"), long.class);
        }
        return Proxy.newProxyInstance(cl, new Class[]{ib3}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("toString".equals(name)) return "DeekseepSidebarBounds";
                if ("hashCode".equals(name)) return sid.hashCode();
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                if ("g".equals(name) && args != null && args.length == 1 && args[0] != null) {
                    try {
                        Object coords = args[0];
                        if (Boolean.TRUE.equals(BM4_I.invoke(coords))) {
                            long size = (Long) BM4_K.invoke(coords);
                            int wpx = (int) (size >> 32);
                            int hpx = (int) (size & 0xFFFFFFFFL);
                            // 本 build 的 bm4.w(long) 实为 windowToLocal：w(0,0) 返回行窗口坐标的负值，取负还原。
                            long pos = (Long) BM4_W.invoke(coords, 0L);
                            int x = -(int) Float.intBitsToFloat((int) (pos >> 32));
                            int y = -(int) Float.intBitsToFloat((int) (pos & 0xFFFFFFFFL));
                            if (wpx > 0 && hpx > 0) SIDEBAR_ROW_BOUNDS.put(
                                    sid, new int[]{x, y, x + wpx, y + hpx});
                        }
                    } catch (Throwable ignored) {}
                }
                return ui8Unit(cl);
            }
        });
    }

    // 从捕获到的真实坐标构造 sid→Rect（仅当前会话列表里的）
    private static Map<String, Rect> captureBoundsFor(List<ChatEditorUi.Session> sessions) {
        Map<String, Rect> out = new HashMap<>();
        for (int i = 0; i < sessions.size(); i++) {
            String id = sessions.get(i).id;
            int[] b = SIDEBAR_ROW_BOUNDS.get(id);
            if (b != null && b[3] > b[1]) out.put(id, new Rect(b[0], b[1], b[2], b[3]));
        }
        return out;
    }

    // 侧栏收起时 mq5.i 的 toggle 回调(xa3)：包一层，收起动作触发时把多选覆盖层滑出并退出。
    private void hookSidebarToggleCleanup(final ClassLoader cl) {
        try {
            Class<?> mq5 = HostCompat.load(cl, "mq5");
            final Class<?> xa3 = HostCompat.load(cl, "xa3");
            int n = 0;
            for (Method m : mq5.getDeclaredMethods()) {
                Class<?>[] pts = m.getParameterTypes();
                if (!m.getName().equals(HostCompat.method("mq5", "i"))
                        || pts.length != 6 || !xa3.isAssignableFrom(pts[2])) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object[] args = null;
                        try {
                            Object[] a = chain.getArgs().toArray();
                            Object drawerHost = a[0];
                            Object state = readHostField(drawerHost, "a");
                            if (HostCompat.simpleNameIs(state, "bn2")) {
                                if (sidebarDrawerState != state) {
                                    sidebarDrawerWidthPx = 0;
                                    sidebarLiveLoggedState = null;
                                }
                                sidebarDrawerState = state;
                                int width = resolveSidebarDrawerWidth(state, cl);
                                if (width > 0 && width != sidebarDrawerWidthPx) {
                                    boolean firstResolvedWidth = sidebarDrawerWidthPx <= 0;
                                    sidebarDrawerWidthPx = width;
                                    if (firstResolvedWidth) {
                                        log("sidebar drawer anchors resolved, width=" + width);
                                    }
                                }
                            }
                            if (a[2] != null) { a[2] = buildSidebarToggleProxy(cl, a[2]); args = a; }
                        } catch (Throwable t) { log("sidebar toggle cleanup row err: " + t); }
                        return args != null ? chain.proceed(args) : chain.proceed();
                    }
                });
                n++;
            }
            log("installed sidebar toggle cleanup hook mq5.i x" + n);
        } catch (Throwable t) { log("hookSidebarToggleCleanup failed: " + t); }

        // DrawerState.c() is the exact animated pixel offset: closed≈-width, open=0. It is read
        // on every native drawer frame and therefore also covers closing, swipe gestures, and
        // interrupted/reversed animations.
        try {
            Class<?> bn2 = HostCompat.load(cl, "bn2");
            int n = 0;
            for (Method m : bn2.getDeclaredMethods()) {
                if (!m.getName().equals("c") || m.getParameterTypes().length != 0
                        || m.getReturnType() != float.class) {
                    continue;
                }
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try {
                            if (result instanceof Number) {
                                Object state = chain.getThisObject();
                                float offset = ((Number) result).floatValue();
                                // mq5 normally supplies the exact conversation DrawerState. If
                                // that capture happens late, a valid pair of Closed/Open anchors
                                // lets the live getter safely identify the same state itself.
                                if (state != sidebarDrawerState) {
                                    int candidateWidth =
                                            resolveSidebarDrawerWidth(state, cl);
                                    if (candidateWidth > 0
                                            && offset >= -candidateWidth * 1.05f
                                            && offset <= candidateWidth * 0.05f) {
                                        sidebarDrawerState = state;
                                        sidebarDrawerWidthPx = candidateWidth;
                                        sidebarLiveLoggedState = null;
                                        log("sidebar drawer live candidate resolved, width="
                                                + candidateWidth);
                                    }
                                }
                                if (state != sidebarDrawerState) return result;
                                int width = sidebarDrawerWidthPx;
                                if (width <= 0) {
                                    width = resolveSidebarDrawerWidth(
                                            state, cl);
                                    if (width > 0) sidebarDrawerWidthPx = width;
                                }
                                if (sidebarLiveLoggedState != state) {
                                    sidebarLiveLoggedState = state;
                                    log("sidebar live curve active, width=" + width);
                                }
                                ChatAppearance.onSidebarOffset(offset, width);
                            }
                        } catch (Throwable ignored) {}
                        return result;
                    }
                });
                n++;
            }
            log("installed sidebar live-offset hook bn2.c x" + n);
        } catch (Throwable t) {
            log("hook sidebar live offset failed: " + t);
        }

        // mq5.i creates n51(case 0) as the icon's real click action. Keep this only as a diagnostic
        // destination signal. The supported host's later DrawerState.c() frames exclusively drive
        // the follower target, preventing an eager endpoint from erasing the visible lag.
        try {
            Class<?> n51 = HostCompat.load(cl, "n51");
            int n = 0;
            for (Method m : n51.getDeclaredMethods()) {
                if (!m.getName().equals("u") || m.getParameterTypes().length != 0) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object action = chain.getThisObject();
                            Object kind = readHostField(action, "a");
                            Object drawerState = readHostField(action, "c");
                            Object drawerHost = readHostField(action, "f");
                            if (Integer.valueOf(0).equals(kind)
                                    && drawerState != null && drawerHost != null
                                    && HostCompat.simpleNameIs(drawerState, "bn2")
                                    && HostCompat.simpleNameIs(drawerHost, "zm2")) {
                                if (sidebarDrawerState != drawerState) {
                                    sidebarDrawerState = drawerState;
                                    sidebarDrawerWidthPx = 0;
                                    sidebarLiveLoggedState = null;
                                }
                                int resolvedWidth =
                                        resolveSidebarDrawerWidth(drawerState, cl);
                                if (resolvedWidth > 0) {
                                    sidebarDrawerWidthPx = resolvedWidth;
                                }
                            }
                        } catch (Throwable t) {
                            log("sidebar appearance toggle signal failed: " + t);
                        }
                        return chain.proceed();
                    }
                });
                n++;
            }
            log("installed sidebar appearance click hook n51.u x" + n);
        } catch (Throwable t) {
            log("hook sidebar appearance click failed: " + t);
        }
    }

    private static int resolveSidebarDrawerWidth(Object drawerState, ClassLoader cl) {
        if (drawerState == null || cl == null) return 0;
        try {
            Object anchored = readHostField(drawerState, "c");
            if (anchored == null) return 0;
            Method anchorsMethod = anchored.getClass().getDeclaredMethod("b");
            anchorsMethod.setAccessible(true);
            Object anchors = anchorsMethod.invoke(anchored);
            if (anchors == null) return 0;
            Class<?> cn2 = HostCompat.load(cl, "cn2");
            Field closedField = cn2.getDeclaredField("a");
            Field openField = cn2.getDeclaredField("b");
            closedField.setAccessible(true);
            openField.setAccessible(true);
            Object closed = closedField.get(null);
            Object open = openField.get(null);
            Method anchorMethod =
                    anchors.getClass().getDeclaredMethod("d", Object.class);
            anchorMethod.setAccessible(true);
            float closedOffset =
                    ((Number) anchorMethod.invoke(anchors, closed)).floatValue();
            float openOffset =
                    ((Number) anchorMethod.invoke(anchors, open)).floatValue();
            if (Float.isNaN(closedOffset) || Float.isNaN(openOffset)) return 0;
            return Math.max(0, Math.round(Math.abs(openOffset - closedOffset)));
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private Object buildSidebarToggleProxy(final ClassLoader cl, final Object original) throws Exception {
        final Class<?> xa3 = HostCompat.load(cl, "xa3");
        return Proxy.newProxyInstance(cl, new Class[]{xa3}, new InvocationHandler() {
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                String name = method.getName();
                if ("toString".equals(name)) return "DeekseepSidebarToggleCleanup";
                if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                if ("equals".equals(name)) return proxy == (args == null ? null : args[0]);
                if ("u".equals(name) && method.getParameterTypes().length == 0) {
                    if (sidebarSelectMode) slideOutSidebarOverlayAndExit();
                    return invokeXa3Returning(original, cl);
                }
                return invokeXa3Returning(original, cl);
            }
        });
    }

    // 2.2.2：Kotlin Unit 是 ui8（静态字段 a）；legacy 的 ti8 在本 build 不是 Unit。
    private static Object ui8Unit(ClassLoader cl) {
        try {
            Field f = HostCompat.load(cl, "ui8").getDeclaredField("a");
            f.setAccessible(true);
            return f.get(null);
        } catch (Throwable ignored) { return null; }
    }

    private static void enterSidebarSelectMode(final Activity act, String startSid) {
        SIDEBAR_SELECTED.clear();
        if (startSid != null && startSid.length() > 0) SIDEBAR_SELECTED.add(startSid);
        sidebarSelectMode = true;
        sidebarConfirmedOpen = false;
        showSidebarSelectOverlay(act);
    }

    private static void showSidebarSelectOverlay(final Activity act) {
        final List<ChatEditorUi.Session> sessions = loadCurrentSidebarSessions(act);
        if (sessions.isEmpty()) {
            UiLanguage.toast(act, "没有可删除的本地对话", Toast.LENGTH_SHORT).show();
            return;
        }

        removeSidebarSelectOverlay();

        final boolean dark = DeekseepUi.isDark(act);
        final int cardBg = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int div = dark ? 0xFF3A3A3D : 0xFFEAEAEA;
        final int brand = DeekseepUi.BRAND;
        final int danger = 0xFFE53935;
        final int checkColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int screenW = act.getResources().getDisplayMetrics().widthPixels;
        final float screenDp = screenW / act.getResources().getDisplayMetrics().density;
        // 手机端(<600dp)侧栏并非铺满屏宽：右侧约 1/5 仍露出聊天区，故取约 4/5 屏宽；平板/大屏限 320dp。
        final int sidebarW = screenDp < 600.0f
                ? Math.round(screenW * 0.8f)
                : Math.min(DeekseepUi.dp(act, 320), screenW);

        final FrameLayout root = new FrameLayout(act);
        root.setClickable(false);
        root.setFocusable(false);
        sidebarSelectOverlay = root;

        final FrameLayout marks = new FrameLayout(act);
        marks.setClickable(false);
        marks.setFocusable(false);
        root.addView(marks, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(DeekseepUi.dp(act, 12), 0, DeekseepUi.dp(act, 10), 0);
        top.setClickable(true);
        GradientDrawable topBg = new GradientDrawable();
        topBg.setColor(cardBg);
        topBg.setCornerRadius(DeekseepUi.dp(act, 16));
        topBg.setStroke(1, div);
        top.setBackground(topBg);
        if (android.os.Build.VERSION.SDK_INT >= 21) top.setElevation(DeekseepUi.dp(act, 8));
        FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(sidebarW - DeekseepUi.dp(act, 20), DeekseepUi.dp(act, 46));
        topLp.leftMargin = DeekseepUi.dp(act, 10);
        topLp.topMargin = DeekseepUi.statusBarHeight(act) + DeekseepUi.dp(act, 8);
        root.addView(top, topLp);

        TextView cancel = new TextView(act);
        cancel.setText("取消");
        cancel.setTextColor(brand);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(DeekseepUi.dp(act, 4), 0, DeekseepUi.dp(act, 10), 0);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { exitSidebarSelectMode(); }
        });
        top.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final TextView title = new TextView(act);
        title.setTextColor(text);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setSingleLine(true);
        top.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView delete = new TextView(act);
        delete.setTextColor(danger);
        delete.setTypeface(Typeface.DEFAULT_BOLD);
        delete.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        delete.setGravity(Gravity.CENTER);
        delete.setPadding(DeekseepUi.dp(act, 10), 0, DeekseepUi.dp(act, 4), 0);
        top.addView(delete, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        delete.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final int n = SIDEBAR_SELECTED.size();
                if (n <= 0) {
                    UiLanguage.toast(act, "先勾选要删除的对话", Toast.LENGTH_SHORT).show();
                    return;
                }
                confirmSidebarBatchDelete(act, sessions, n);
            }
        });
        updateSidebarSelectTitle(title, delete);

        UiLanguage.localizeTree(act, root);
        ViewGroup decor = (ViewGroup) act.getWindow().getDecorView();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                sidebarW,
                ViewGroup.LayoutParams.MATCH_PARENT);
        lp.gravity = Gravity.START | Gravity.TOP;
        decor.addView(root, lp);

        final Runnable[] refresh = new Runnable[1];
        refresh[0] = new Runnable() {
            public void run() {
                if (!sidebarSelectMode || sidebarSelectOverlay != root || root.getParent() == null) return;
                refreshSidebarMarkLayer(act, marks, sessions, title, delete, sidebarW, checkColor);
                root.postDelayed(this, 300);
            }
        };
        root.post(refresh[0]);
    }

    private static void updateSidebarSelectTitle(TextView title, TextView delete) {
        int n = SIDEBAR_SELECTED.size();
        Context context = title.getContext();
        title.setText(n > 0
                ? UiLanguage.text(context, "已选择 " + n, n + " selected")
                : UiLanguage.text(context, "选择对话", "Select chats"));
        delete.setText(n > 0
                ? UiLanguage.text(context, "删除(" + n + ")", "Delete (" + n + ")")
                : UiLanguage.text(context, "删除", "Delete"));
    }

    private static void refreshSidebarMarkLayer(final Activity act, final FrameLayout marks,
                                                final List<ChatEditorUi.Session> sessions,
                                                final TextView title, final TextView delete,
                                                final int sidebarW, final int checkColor) {
        if (marks == null || marks.getParent() == null) return;
        marks.removeAllViews();
        Map<String, Rect> bounds = captureBoundsFor(sessions);
        if (sidebarRowsOnScreen(bounds, sidebarW)) sidebarConfirmedOpen = true;
        else if (sidebarConfirmedOpen && isSidebarCollapsed(bounds, sidebarW)) {
            logSidebarBoundsState("sidebar collapsed detected (rows off-screen) -> slide out overlay");
            slideOutSidebarOverlayAndExit();
            return;
        }
        if (bounds.isEmpty()) bounds = resolveSidebarSessionBounds(act, sessions, sidebarW);
        if (bounds.isEmpty()) {
            logSidebarBoundsState("sidebar marks fallback: no bounds (capture+a11y empty)");
            addFallbackSidebarMarks(act, marks, sessions, title, delete, sidebarW, checkColor);
            return;
        }
        StringBuilder dbg = new StringBuilder("sidebar marks: matched=" + bounds.size() + " raw=");
        for (int i = 0; i < sessions.size() && i < 4; i++) {
            Rect rr = bounds.get(sessions.get(i).id);
            if (rr != null) dbg.append("[").append(rr.left).append(",").append(rr.top)
                    .append(",").append(rr.width()).append("x").append(rr.height()).append("]");
        }
        logSidebarBoundsState(dbg.toString());
        for (int i = 0; i < sessions.size(); i++) {
            ChatEditorUi.Session s = sessions.get(i);
            Rect r = bounds.get(s.id);
            if (r == null) continue;
            int markH = Math.max(DeekseepUi.dp(act, 18), r.height());
            int top = Math.max(0, r.top + (r.height() - markH) / 2);
            // 手机端适配：勾选热区对齐到该行真实右边缘 r.right，而非全局 sidebarW。
            addSidebarCheckMark(act, marks, s, title, delete, sidebarW, checkColor, top, markH, r.right);
        }
    }

    // 侧栏收起检测：收起抽屉不走 mq5.i.u 回调，但 onGloballyPositioned 会把行左坐标从 0 平移到 -sidebarW。
    private static boolean isSidebarCollapsed(Map<String, Rect> bounds, int sidebarW) {
        if (bounds == null || bounds.isEmpty()) return false;
        int threshold = -sidebarW / 2;
        for (Rect r : bounds.values()) {
            if (r != null && r.left <= threshold) return true;
        }
        return false;
    }

    // 有任一行左坐标接近屏内（> -1/4 sidebarW）即视为侧栏已展开，用于解锁收起检测
    private static boolean sidebarRowsOnScreen(Map<String, Rect> bounds, int sidebarW) {
        if (bounds == null || bounds.isEmpty()) return false;
        int threshold = -sidebarW / 4;
        for (Rect r : bounds.values()) {
            if (r != null && r.left > threshold) return true;
        }
        return false;
    }

    private static void logSidebarBoundsState(String msg) {
        long now = System.currentTimeMillis();
        if (now - sidebarBoundsLogAt < 2500) return;
        sidebarBoundsLogAt = now;
        log(msg);
    }

    private static void addFallbackSidebarMarks(final Activity act, final FrameLayout marks,
                                                final List<ChatEditorUi.Session> sessions,
                                                final TextView title, final TextView delete,
                                                final int sidebarW, final int checkColor) {
        int rowH = DeekseepUi.dp(act, 44);
        int top = DeekseepUi.statusBarHeight(act) + DeekseepUi.dp(act, 96);
        int screenH = act.getResources().getDisplayMetrics().heightPixels;
        for (int i = 0; i < sessions.size(); i++) {
            int y = top + i * rowH;
            if (y > screenH) break;
            // 无真实坐标兜底：rowRight=0，退回对齐 sidebarW。
            addSidebarCheckMark(act, marks, sessions.get(i), title, delete, sidebarW, checkColor, y, rowH, 0);
        }
    }

    private static void addSidebarCheckMark(final Activity act, final FrameLayout marks,
                                            final ChatEditorUi.Session s,
                                            final TextView title, final TextView delete,
                                            final int sidebarW, final int checkColor,
                                            int top, int rowH, int rowRight) {
        final TextView mark = new TextView(act);
        // 行右侧透明可点击热区：对勾靠右显示，触摸区向左延伸约 40% 行宽；左侧仍可点标题切换会话。
        mark.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        mark.setIncludeFontPadding(false);
        mark.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        mark.setTypeface(Typeface.DEFAULT_BOLD);
        mark.setPadding(0, 0, DeekseepUi.dp(act, 19), 0);
        mark.setClickable(true);
        mark.setFocusable(false);
        updateSidebarMarkState(mark, s.id, checkColor);
        // 手机端适配：热区右缘对齐真实行右边缘（有坐标时），钳到 sidebarW；无坐标时退回全宽右缘。
        int rightEdge = rowRight > 0 ? Math.min(rowRight, sidebarW) : sidebarW;
        int touchW = Math.max(DeekseepUi.dp(act, 96), sidebarW * 2 / 5);
        if (touchW > rightEdge) touchW = rightEdge;
        FrameLayout.LayoutParams markLp = new FrameLayout.LayoutParams(touchW, rowH);
        markLp.leftMargin = Math.max(0, rightEdge - touchW);
        markLp.topMargin = top;
        marks.addView(mark, markLp);
        mark.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toggleSidebarSelection(s.id);
                updateSidebarSelectTitle(title, delete);
                updateSidebarMarkState(mark, s.id, checkColor);
            }
        });
    }

    private static Map<String, Rect> resolveSidebarSessionBounds(Activity act,
                                                                 List<ChatEditorUi.Session> sessions,
                                                                 int sidebarW) {
        Map<String, Rect> out = new HashMap<>();
        HashSet<String> wanted = new HashSet<>();
        for (int i = 0; i < sessions.size(); i++) {
            String k = sidebarTitleKey(sessions.get(i));
            if (k.length() > 0) wanted.add(k);
        }
        if (wanted.isEmpty()) return out;

        Map<String, ArrayList<Rect>> byTitle = new HashMap<>();
        AccessibilityNodeInfo root = null;
        try {
            View decor = act.getWindow().getDecorView();
            int[] decorLoc = new int[2];
            decor.getLocationOnScreen(decorLoc);
            root = decor.createAccessibilityNodeInfo();
            int minTop = DeekseepUi.statusBarHeight(act) + DeekseepUi.dp(act, 70);
            collectSidebarTitleBounds(root, decorLoc, sidebarW, minTop, wanted, byTitle, 0);
        } catch (Throwable t) {
            log("resolve sidebar a11y bounds failed: " + t);
        } finally {
            if (root != null) try { root.recycle(); } catch (Throwable ignored) {}
        }

        for (ArrayList<Rect> list : byTitle.values()) {
            Collections.sort(list, new Comparator<Rect>() {
                public int compare(Rect a, Rect b) {
                    if (a.top != b.top) return a.top - b.top;
                    return a.left - b.left;
                }
            });
        }
        for (int i = 0; i < sessions.size(); i++) {
            ChatEditorUi.Session s = sessions.get(i);
            String k = sidebarTitleKey(s);
            if (k.length() == 0) continue;
            ArrayList<Rect> list = byTitle.get(k);
            if (list == null || list.isEmpty()) continue;
            out.put(s.id, list.remove(0));
        }
        return out;
    }

    private static void collectSidebarTitleBounds(AccessibilityNodeInfo node, int[] decorLoc,
                                                  int sidebarW, int minTop, HashSet<String> wanted,
                                                  Map<String, ArrayList<Rect>> byTitle,
                                                  int depth) {
        if (node == null || depth > 80) return;
        try {
            collectSidebarTextBound(node, node.getText(), decorLoc, sidebarW, minTop, wanted, byTitle);
            collectSidebarTextBound(node, node.getContentDescription(), decorLoc, sidebarW, minTop, wanted, byTitle);
            int n = node.getChildCount();
            for (int i = 0; i < n; i++) {
                AccessibilityNodeInfo child = null;
                try {
                    child = node.getChild(i);
                    collectSidebarTitleBounds(child, decorLoc, sidebarW, minTop, wanted, byTitle, depth + 1);
                } catch (Throwable ignored) {
                } finally {
                    if (child != null) try { child.recycle(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void collectSidebarTextBound(AccessibilityNodeInfo node, CharSequence cs,
                                                int[] decorLoc, int sidebarW, int minTop,
                                                HashSet<String> wanted,
                                                Map<String, ArrayList<Rect>> byTitle) {
        if (node == null || cs == null) return;
        String text = cs.toString().trim();
        if (!wanted.contains(text)) return;
        Rect r = new Rect();
        node.getBoundsInScreen(r);
        r.offset(-decorLoc[0], -decorLoc[1]);
        if (isLikelySidebarTitleBounds(r, sidebarW, minTop)) putSidebarTitleRect(byTitle, text, r);
    }

    private static boolean isLikelySidebarTitleBounds(Rect r, int sidebarW, int minTop) {
        if (r == null || r.isEmpty()) return false;
        if (r.top < minTop) return false;
        if (r.right <= 0 || r.left >= sidebarW) return false;
        if (r.height() <= 0 || r.height() > 80) return false;
        return r.width() > 0;
    }

    private static void putSidebarTitleRect(Map<String, ArrayList<Rect>> byTitle,
                                            String title, Rect r) {
        ArrayList<Rect> list = byTitle.get(title);
        if (list == null) {
            list = new ArrayList<>();
            byTitle.put(title, list);
        }
        for (int i = 0; i < list.size(); i++) {
            Rect old = list.get(i);
            if (Math.abs(old.centerY() - r.centerY()) <= 3 && Math.abs(old.left - r.left) <= 3) return;
        }
        list.add(new Rect(r));
    }

    private static String sidebarTitleKey(ChatEditorUi.Session s) {
        if (s == null || s.title == null) return "";
        return s.title.trim();
    }

    private static void updateSidebarMarkState(TextView mark, String sid, int checkColor) {
        boolean checked = sid != null && SIDEBAR_SELECTED.contains(sid);
        mark.setText(checked ? "\u2713" : "");
        mark.setTextColor(checkColor);
        mark.setBackground(null);
    }

    private static void toggleSidebarSelection(String sid) {
        if (sid == null) return;
        if (SIDEBAR_SELECTED.contains(sid)) SIDEBAR_SELECTED.remove(sid);
        else SIDEBAR_SELECTED.add(sid);
    }

    private static void exitSidebarSelectMode() {
        sidebarSelectMode = false;
        SIDEBAR_SELECTED.clear();
        removeSidebarSelectOverlay();
    }

    // 侧边栏收回时调用：多选覆盖层向上滑出并淡出后再移除。
    private static void slideOutSidebarOverlayAndExit() {
        sidebarSelectMode = false;
        SIDEBAR_SELECTED.clear();
        final View v = sidebarSelectOverlay;
        sidebarSelectOverlay = null;
        if (v == null) return;
        final Runnable anim = new Runnable() {
            public void run() {
                try {
                    int dist = v.getHeight() > 0 ? v.getHeight()
                            : v.getResources().getDisplayMetrics().heightPixels;
                    v.animate().translationY(-dist).alpha(0f).setDuration(220)
                            .setInterpolator(new android.view.animation.AccelerateInterpolator())
                            .withEndAction(new Runnable() {
                                public void run() {
                                    try {
                                        ViewGroup p = (ViewGroup) v.getParent();
                                        if (p != null) p.removeView(v);
                                    } catch (Throwable ignored) {}
                                }
                            }).start();
                } catch (Throwable t) {
                    try {
                        ViewGroup p = (ViewGroup) v.getParent();
                        if (p != null) p.removeView(v);
                    } catch (Throwable ignored) {}
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) anim.run();
        else new Handler(Looper.getMainLooper()).post(anim);
    }

    private static void removeSidebarSelectOverlay() {
        View v = sidebarSelectOverlay;
        sidebarSelectOverlay = null;
        if (v == null) return;
        try {
            ViewGroup p = (ViewGroup) v.getParent();
            if (p != null) p.removeView(v);
        } catch (Throwable ignored) {}
    }

    private static void confirmSidebarBatchDelete(final Activity act,
                                                  final List<ChatEditorUi.Session> sessions,
                                                  int n) {
        final Dialog dlg = new Dialog(act);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        boolean dark = DeekseepUi.isDark(act);
        int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        int subColor = dark ? 0xFFB0B0B4 : 0xFF666666;
        int divColor = dark ? 0xFF3A3A3D : 0xFFEAEAEA;

        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(DeekseepUi.dp(act, 22), DeekseepUi.dp(act, 20),
                DeekseepUi.dp(act, 22), DeekseepUi.dp(act, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(cardColor);
        bg.setCornerRadius(DeekseepUi.dp(act, 18));
        card.setBackground(bg);

        TextView title = new TextView(act);
        title.setText(UiLanguage.text(act,
                "删除 " + n + " 个对话", "Delete " + n + " chats"));
        title.setTextColor(textColor);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView msg = new TextView(act);
        msg.setText("删除后会从当前列表移除。未被原版列表加载的条目会用本地数据库删除兜底。");
        msg.setTextColor(subColor);
        msg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        msg.setLineSpacing(DeekseepUi.dp(act, 2), 1.0f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.topMargin = DeekseepUi.dp(act, 10);
        card.addView(msg, mlp);

        View line = new View(act);
        line.setBackgroundColor(divColor);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        llp.topMargin = DeekseepUi.dp(act, 18);
        card.addView(line, llp);

        LinearLayout buttons = new LinearLayout(act);
        buttons.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DeekseepUi.dp(act, 48));
        card.addView(buttons, blp);

        TextView cancel = new TextView(act);
        cancel.setText("取消");
        cancel.setTextColor(subColor);
        cancel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(DeekseepUi.dp(act, 14), 0, DeekseepUi.dp(act, 14), 0);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dlg.dismiss(); }
        });
        buttons.addView(cancel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView del = new TextView(act);
        del.setText("删除");
        del.setTextColor(0xFFE53935);
        del.setTypeface(Typeface.DEFAULT_BOLD);
        del.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        del.setGravity(Gravity.CENTER);
        del.setPadding(DeekseepUi.dp(act, 14), 0, DeekseepUi.dp(act, 4), 0);
        del.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                dlg.dismiss();
                deleteSidebarSelected(act, sessions);
            }
        });
        buttons.addView(del, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        UiLanguage.localizeTree(act, card);
        dlg.setContentView(card);
        dlg.show();
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setDimAmount(0.32f);
            w.setLayout(Math.min(DeekseepUi.dp(act, 320),
                    act.getResources().getDisplayMetrics().widthPixels - DeekseepUi.dp(act, 48)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static List<ChatEditorUi.Session> loadCurrentSidebarSessions(Activity act) {
        List<ChatEditorUi.Session> out = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();
        File f = ChatEditorUi.currentDb(act.getClassLoader());
        if (f == null) return out;
        SQLiteDatabase d = null;
        Cursor c = null;
        try {
            d = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            c = d.rawQuery("SELECT id,title FROM chat_session_list ORDER BY updated_at DESC", null);
            while (c.moveToNext()) {
                ChatEditorUi.Session s = new ChatEditorUi.Session();
                s.id = c.getString(0);
                s.title = c.getString(1);
                s.dbPath = f.getPath();
                if (s.id != null) {
                    out.add(s);
                    seen.add(s.id);
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignored) {}
            if (d != null) try { d.close(); } catch (Throwable ignored) {}
        }
        // A just-synchronized cloud conversation may be visible in the native sidebar before its
        // directory row reaches SQLite. Include it so batch selection/deletion is not silently
        // limited to the older database snapshot.
        for (Object[] row : nativeSessionDirectory()) {
            if (row == null || row.length < 2 || row[0] == null) continue;
            String sid = String.valueOf(row[0]);
            if (sid.length() == 0 || !seen.add(sid)) continue;
            ChatEditorUi.Session s = new ChatEditorUi.Session();
            s.id = sid;
            s.title = row[1] == null ? "" : String.valueOf(row[1]);
            s.dbPath = f.getPath();
            s.nativeOnly = true;
            out.add(s);
        }
        return out;
    }

    private static void deleteSidebarSelected(final Activity act, List<ChatEditorUi.Session> sessions) {
        int nativeRequested = 0;
        int localOk = 0;
        int fail = 0;
        int matched = 0;
        Map<String, List<String>> local = new HashMap<>();
        HashSet<String> selected = new HashSet<>(SIDEBAR_SELECTED);
        for (int i = 0; i < sessions.size(); i++) {
            ChatEditorUi.Session s = sessions.get(i);
            if (s.id == null || !selected.contains(s.id)) continue;
            matched++;
            // Always use DeepSeek's authenticated h61(tp) route when it is available. Local
            // cleanup still runs afterwards: the host success path does not know about Deekseep
            // sidecars, and leaving one behind resurrects the conversation on cold start.
            if (requestNativeSessionDelete(s.id)) nativeRequested++;
            List<String> ids = local.get(s.dbPath);
            if (ids == null) {
                ids = new ArrayList<>();
                local.put(s.dbPath, ids);
            }
            ids.add(s.id);
        }

        for (Map.Entry<String, List<String>> e : local.entrySet()) {
            SQLiteDatabase d = null;
            try {
                d = SQLiteDatabase.openDatabase(e.getKey(), null, SQLiteDatabase.OPEN_READWRITE);
                for (String sid : e.getValue()) {
                    if (ChatEditorUi.deleteSessionLocal(d, sid)) localOk++;
                    else fail++;
                }
            } catch (Throwable ignored) {
                fail += e.getValue().size();
            } finally {
                if (d != null) try { d.close(); } catch (Throwable ignored) {}
            }
        }
        fail += Math.max(0, selected.size() - matched);

        String msg;
        msg = "已请求 DeepSeek 删除 " + nativeRequested + " 个，本地已移除 "
                + localOk + " 个";
        int nativeUnavailable = Math.max(0, matched - nativeRequested);
        if (nativeUnavailable > 0) msg += "，未取得原生链路 " + nativeUnavailable + " 个";
        if (fail > 0) msg += "，本地失败 " + fail + " 个";
        exitSidebarSelectMode();
        UiLanguage.toast(act, msg, Toast.LENGTH_SHORT).show();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                try { act.recreate(); } catch (Throwable ignored) {}
            }
        }, 700);
    }

    private static boolean invokeXa3(Object action) {
        if (action == null) return false;
        try {
            for (Method m : action.getClass().getMethods()) {
                if (!m.getName().equals("u") || m.getParameterTypes().length != 0) continue;
                m.setAccessible(true);
                m.invoke(action);
                return true;
            }
            for (Method m : action.getClass().getDeclaredMethods()) {
                if (!m.getName().equals("u") || m.getParameterTypes().length != 0) continue;
                m.setAccessible(true);
                m.invoke(action);
                return true;
            }
        } catch (Throwable t) { log("invoke sidebar delete action failed: " + t); }
        return false;
    }

    private static Object invokeXa3Returning(Object action, ClassLoader cl) {
        if (action == null) return ui8Unit(cl);
        try {
            for (Method m : action.getClass().getMethods()) {
                if (!m.getName().equals("u") || m.getParameterTypes().length != 0) continue;
                m.setAccessible(true);
                return m.invoke(action);
            }
            for (Method m : action.getClass().getDeclaredMethods()) {
                if (!m.getName().equals("u") || m.getParameterTypes().length != 0) continue;
                m.setAccessible(true);
                return m.invoke(action);
            }
        } catch (Throwable t) { log("invoke sidebar toggle action failed: " + t); }
        return ui8Unit(cl);
    }

    // ── 文件操作（静态，供 DeekseepUi 调用）────────────────────────

    static void handlePickedFile(Activity act, Uri uri) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(act.getContentResolver().openInputStream(uri), "UTF-8"))) {
                String ln;
                while ((ln = br.readLine()) != null) sb.append(ln).append('\n');
            }
            String content = sb.toString().trim();
            overwriteTextFile(PROMPT_FILE, content);

            String displayPath = resolveDisplayPath(act, uri);
            writeText(PROMPT_SOURCE_FILE, displayPath);
            refreshPromptSymlink(displayPath);

            File promptFile = new File(PROMPT_FILE);
            log("prompt imported, length=" + content.length()
                    + ", fileExists=" + promptFile.exists()
                    + ", fileSize=" + promptFile.length()
                    + ", source=" + displayPath);
            Runnable cb = onPickComplete;
            if (cb != null) act.runOnUiThread(cb);
        } catch (Throwable t) { log("handlePickedFile err: " + t); }
    }

    static String getPromptDisplayPath() {
        try {
            String source = readSmallText(PROMPT_SOURCE_FILE);
            if (source != null && source.length() > 0) return source;
        } catch (Throwable ignored) {}
        File pf = new File(PROMPT_FILE);
        return pf.exists() && pf.length() > 0 ? pf.getAbsolutePath() : "";
    }

    static void clearPromptFiles() {
        if (isEmbeddedPromptEnabled()) return;
        new File(PROMPT_FILE).delete();
        new File(PROMPT_LINK_FILE).delete();
        new File(PROMPT_SOURCE_FILE).delete();
        new File(ENABLED_FILE).delete();
    }

    static boolean isEnabled() {
        return new File(ENABLED_FILE).exists();
    }

    /** True when the opt-in bundled prompt, rather than an imported prompt, is active. */
    static boolean isEmbeddedPromptEnabled() {
        if (!isEnabled()) return false;
        String source = readSmallText(PROMPT_SOURCE_FILE);
        File stored = new File(EMBEDDED_PROMPT_FILE);
        File activeLink = new File(PROMPT_LINK_FILE);
        return "内置隐藏提示词".equals(source)
                && stored.isFile() && stored.length() > 0L
                && activeLink.exists() && activeLink.length() == stored.length();
    }

    /** Ensures the opaque bundled prompt is present in DeepSeek's own private no-backup area. */
    static boolean ensureEmbeddedPromptInstalled(Context host) {
        if (BuildInfo.GOOGLE_PLAY || host == null) return false;
        File destination = new File(EMBEDDED_PROMPT_FILE);
        if (destination.isFile() && destination.length() > 0L) return true;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            ClassLoader moduleLoader = Main.class.getClassLoader();
            if (moduleLoader == null) throw new IOException("module class loader unavailable");
            input = moduleLoader.getResourceAsStream(EMBEDDED_PROMPT_RESOURCE);
            if (input == null) throw new IOException("bundled prompt resource unavailable");
            File directory = new File(EMBEDDED_PROMPT_DIR);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("private prompt directory unavailable");
            }
            output = new FileOutputStream(destination, false);
            byte[] buffer = new byte[8192];
            int count;
            long total = 0L;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                output.write(buffer, 0, count);
                total += count;
            }
            output.flush();
            if (total <= 0L || destination.length() != total) {
                throw new IOException("private prompt copy was empty or incomplete");
            }
            log("bundled prompt provisioned in private store, bytes=" + total);
            return true;
        } catch (Throwable t) {
            log("bundled prompt provisioning failed: " + t);
            return false;
        } finally {
            try { if (output != null) output.close(); } catch (Throwable ignored) {}
            try { if (input != null) input.close(); } catch (Throwable ignored) {}
        }
    }

    static void setEnabled(boolean on) {
        try {
            File ef = new File(ENABLED_FILE);
            if (on) overwriteTextFile(ENABLED_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    private static void copyPromptFile(File source, File destination) throws Throwable {
        if (source == null || !source.exists() || source.length() <= 0L) return;
        ensureWritableFile(destination);
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count > 0) output.write(buffer, 0, count);
            }
            output.flush();
        }
        if (destination.length() != source.length()) {
            throw new IOException("prompt snapshot copy was incomplete");
        }
    }

    private static void snapshotPreviousPromptState() throws Throwable {
        File state = new File(EMBEDDED_PREVIOUS_STATE_FILE);
        if (state.exists()) return;
        File directory = new File(EMBEDDED_PROMPT_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("private prompt snapshot directory unavailable");
        }
        File previousPrompt = new File(EMBEDDED_PREVIOUS_PROMPT_FILE);
        File previousSource = new File(EMBEDDED_PREVIOUS_SOURCE_FILE);
        previousPrompt.delete();
        previousSource.delete();
        File activeLink = new File(PROMPT_LINK_FILE);
        File copiedPrompt = new File(PROMPT_FILE);
        File effectivePrompt = activeLink.exists() && activeLink.length() > 0L
                ? activeLink : copiedPrompt;
        boolean hadPrompt = effectivePrompt.exists() && effectivePrompt.length() > 0L;
        if (hadPrompt) copyPromptFile(effectivePrompt, previousPrompt);
        File source = new File(PROMPT_SOURCE_FILE);
        if (source.exists() && source.length() > 0L) copyPromptFile(source, previousSource);
        overwriteTextFile(EMBEDDED_PREVIOUS_STATE_FILE,
                "enabled=" + (isEnabled() ? "1" : "0") + "\n"
                        + "prompt=" + (hadPrompt ? "1" : "0"));
        log("previous prompt state snapshotted enabled=" + isEnabled()
                + " prompt=" + hadPrompt);
    }

    private static void restorePreviousPromptState() throws Throwable {
        String state = readSmallText(EMBEDDED_PREVIOUS_STATE_FILE);
        new File(PROMPT_LINK_FILE).delete();
        new File(PROMPT_FILE).delete();
        new File(PROMPT_SOURCE_FILE).delete();
        setEnabled(false);
        if (state == null) return;
        if (state.contains("prompt=1")) {
            copyPromptFile(new File(EMBEDDED_PREVIOUS_PROMPT_FILE), new File(PROMPT_FILE));
        }
        File previousSource = new File(EMBEDDED_PREVIOUS_SOURCE_FILE);
        if (previousSource.exists() && previousSource.length() > 0L) {
            copyPromptFile(previousSource, new File(PROMPT_SOURCE_FILE));
        }
        setEnabled(state.contains("enabled=1"));
        new File(EMBEDDED_PREVIOUS_PROMPT_FILE).delete();
        new File(EMBEDDED_PREVIOUS_SOURCE_FILE).delete();
        new File(EMBEDDED_PREVIOUS_STATE_FILE).delete();
        log("previous prompt state restored enabled=" + isEnabled());
    }

    /** Enables the mainland-only hidden embedded prompt without exposing its source in the UI. */
    static boolean setEmbeddedPromptEnabled(Context host, boolean enabled) {
        if (BuildInfo.GOOGLE_PLAY) return false;
        if (!enabled) {
            try {
                restorePreviousPromptState();
                return true;
            } catch (Throwable t) {
                log("previous prompt restore failed: " + t);
                return false;
            }
        }
        if (host == null) return false;
        try {
            if (!ensureEmbeddedPromptInstalled(host)) return false;
            snapshotPreviousPromptState();
            // Link the injector directly to the private copy. This works in rootless injected
            // processes because the file is created by DeepSeek under its own app UID.
            new File(PROMPT_LINK_FILE).delete();
            new File(PROMPT_FILE).delete();
            Os.symlink(EMBEDDED_PROMPT_FILE, PROMPT_LINK_FILE);
            writeText(PROMPT_SOURCE_FILE, "内置隐藏提示词");
            setEnabled(true);
            if (!isEmbeddedPromptEnabled()) {
                throw new IOException("prompt flag or source marker was not persisted");
            }
            log("embedded prompt enabled from private store");
            return true;
        } catch (Throwable t) {
            log("embedded prompt enable failed: " + t);
            try { restorePreviousPromptState(); } catch (Throwable ignored) {}
            return false;
        }
    }

    static boolean isNoCensor() {
        return new File(NO_CENSOR_FILE).exists();
    }

    static void setNoCensor(boolean on) {
        try {
            File ef = new File(NO_CENSOR_FILE);
            if (on) overwriteTextFile(NO_CENSOR_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    static boolean isAutoBackup() {
        return new File(AUTO_BACKUP_FILE).exists();
    }

    static void setAutoBackup(boolean on) {
        try {
            File ef = new File(AUTO_BACKUP_FILE);
            if (on) overwriteTextFile(AUTO_BACKUP_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    // 专家模式解锁旗标（hookExpertUnlock 读它决定是否给 expert 回填 feature 模板）
    static boolean isExpertUnlock() {
        return new File(EXPERT_UNLOCK_FILE).exists();
    }

    static void setExpertUnlock(boolean on) {
        try {
            File ef = new File(EXPERT_UNLOCK_FILE);
            if (on) overwriteTextFile(EXPERT_UNLOCK_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    static boolean hasAcceptedExperimentalDisclaimer() {
        BufferedReader reader = null;
        try {
            File marker = new File(EXPERIMENTAL_DISCLAIMER_FILE);
            if (!marker.isFile()) return false;
            reader = new BufferedReader(new FileReader(marker));
            return EXPERIMENTAL_DISCLAIMER_VERSION.equals(reader.readLine());
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (reader != null) try { reader.close(); } catch (Throwable ignored) {}
        }
    }

    static boolean acceptExperimentalDisclaimer() {
        try {
            FileWriter writer = new FileWriter(EXPERIMENTAL_DISCLAIMER_FILE, false);
            writer.write(EXPERIMENTAL_DISCLAIMER_VERSION);
            writer.write('\n');
            writer.close();
            return true;
        } catch (Throwable t) {
            log("experimental disclaimer marker err: " + safeThrowableMessage(t));
            return false;
        }
    }

    static boolean isGoogleLoginUnlock() {
        return new File(GOOGLE_LOGIN_UNLOCK_FILE).exists();
    }

    static void setGoogleLoginUnlock(boolean on) {
        try {
            File flag = new File(GOOGLE_LOGIN_UNLOCK_FILE);
            if (on) overwriteTextFile(GOOGLE_LOGIN_UNLOCK_FILE, "");
            else flag.delete();
        } catch (Throwable ignored) {}
    }

    static boolean isWechatMobileLoginUnlock() {
        return new File(WECHAT_MOBILE_LOGIN_UNLOCK_FILE).exists();
    }

    static void setWechatMobileLoginUnlock(boolean on) {
        try {
            File flag = new File(WECHAT_MOBILE_LOGIN_UNLOCK_FILE);
            if (on) overwriteTextFile(WECHAT_MOBILE_LOGIN_UNLOCK_FILE, "");
            else flag.delete();
        } catch (Throwable ignored) {}
    }

    static final class LocalApiBackgroundState {
        final boolean dozeExempt;
        final boolean backgroundRestricted;
        final String error;

        LocalApiBackgroundState(boolean dozeExempt, boolean backgroundRestricted, String error) {
            this.dozeExempt = dozeExempt;
            this.backgroundRestricted = backgroundRestricted;
            this.error = error == null ? "" : error;
        }

        boolean allowed() {
            return dozeExempt && !backgroundRestricted && error.length() == 0;
        }

        String describe(boolean approved) {
            StringBuilder out = new StringBuilder();
            out.append(UiLanguage.text("电池优化：", "Battery optimization: "))
                    .append(dozeExempt
                            ? UiLanguage.text("✓ 已设为不优化/不限制", "✓ Unrestricted")
                            : UiLanguage.text("✗ 仍受电池优化限制", "✗ Still restricted"))
                    .append(UiLanguage.text("\n后台活动：", "\nBackground activity: "))
                    .append(backgroundRestricted
                            ? UiLanguage.text("✗ 系统禁止后台活动", "✗ Blocked by the system")
                            : UiLanguage.text("✓ 系统允许后台活动", "✓ Allowed by the system"))
                    .append(UiLanguage.text("\n首次放行：", "\nInitial approval: "))
                    .append(approved && allowed()
                            ? UiLanguage.text("✓ 校验通过", "✓ Approved")
                            : UiLanguage.text("✗ 尚未通过校验", "✗ Not approved"));
            if (error.length() > 0) out.append(UiLanguage.text(
                    "\n检测错误：", "\nDetection error: ")).append(UiLanguage.dynamic(error));
            return out.toString();
        }
    }

    static LocalApiBackgroundState localApiBackgroundState(Context context) {
        if (context == null) {
            return new LocalApiBackgroundState(false, true,
                    UiLanguage.text("DeepSeek 上下文尚未就绪",
                            "DeepSeek context is not ready"));
        }
        boolean dozeExempt = false;
        boolean restricted = false;
        String error = "";
        try {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power == null) error = UiLanguage.text(context,
                    "无法读取电池优化状态", "Could not read battery optimization status");
            else dozeExempt = power.isIgnoringBatteryOptimizations(TARGET);
        } catch (Throwable t) {
            error = UiLanguage.text(context,
                    "电池优化检测失败：", "Battery optimization check failed: ")
                    + safeThrowableMessage(t);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                ActivityManager manager = (ActivityManager)
                        context.getSystemService(Context.ACTIVITY_SERVICE);
                restricted = manager == null || manager.isBackgroundRestricted();
                if (manager == null && error.length() == 0) error = UiLanguage.text(context,
                        "无法读取后台活动状态", "Could not read background activity status");
            } catch (Throwable t) {
                restricted = true;
                if (error.length() == 0) {
                    error = UiLanguage.text(context,
                            "后台活动检测失败：", "Background activity check failed: ")
                            + safeThrowableMessage(t);
                }
            }
        }
        return new LocalApiBackgroundState(dozeExempt, restricted, error);
    }

    static boolean isLocalApiBackgroundApproved(Context context) {
        return new File(LOCAL_API_BACKGROUND_READY_FILE).exists()
                && localApiBackgroundState(context).allowed();
    }

    static String localApiBackgroundStatus(Context context) {
        return localApiBackgroundState(context).describe(
                new File(LOCAL_API_BACKGROUND_READY_FILE).exists());
    }

    static boolean verifyLocalApiBackground(Activity activity) {
        LocalApiBackgroundState state = localApiBackgroundState(activity);
        try {
            if (state.allowed()) overwriteTextFile(LOCAL_API_BACKGROUND_READY_FILE,
                    String.valueOf(System.currentTimeMillis()));
            else new File(LOCAL_API_BACKGROUND_READY_FILE).delete();
        } catch (Throwable t) {
            log("local API background marker update failed: " + t);
            return false;
        }
        if (!state.allowed()) {
            LocalApiGateway.stop();
            requestLocalApiKeepAlive(activity, false);
            return false;
        }
        if (isLocalApiEnabled()) {
            requestLocalApiKeepAlive(activity, true);
            startLocalApiGateway(activity);
        }
        return true;
    }

    static boolean openLocalApiBatterySettings(Activity activity) {
        if (activity == null || activity.isFinishing()) return false;
        Intent[] intents = new Intent[]{
                new Intent("android.settings.VIEW_ADVANCED_POWER_USAGE_DETAIL")
                        .setData(Uri.parse("package:" + TARGET)),
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:" + TARGET)),
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        };
        for (Intent intent : intents) {
            try {
                activity.startActivityForResult(intent, LOCAL_API_BATTERY_REQUEST);
                return true;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    static boolean isLocalApiEnabled() {
        return new File(LOCAL_API_ENABLED_FILE).exists();
    }

    static boolean setLocalApiEnabled(boolean on) {
        Main module = MODULE;
        Activity activity = module == null ? null : module.curAct.get();
        if (on && !isLocalApiBackgroundApproved(activity)) {
            log("local API enable rejected: unrestricted background access not verified");
            LocalApiGateway.stop();
            return false;
        }
        try {
            File flag = new File(LOCAL_API_ENABLED_FILE);
            if (on) overwriteTextFile(LOCAL_API_ENABLED_FILE, "");
            else flag.delete();
        } catch (Throwable t) {
            log("local API marker update failed: " + t);
            return false;
        }
        if (on && activity != null) {
            requestLocalApiKeepAlive(activity, true);
            startLocalApiGateway(activity);
        }
        if (!on) {
            LocalApiGateway.stop();
            if (activity != null) requestLocalApiKeepAlive(activity, false);
            if (module != null) {
                new Thread(new Runnable() {
                    @Override public void run() {
                        Main current = MODULE;
                        if (current != null) current.deleteReusableApiSessions();
                    }
                }, "Deekseep-API-Cleanup").start();
            }
        }
        return true;
    }

    static String localApiConnectionInfo() {
        return LocalApiGateway.connectionInfo();
    }

    static String rotateLocalApiKey(Activity activity) {
        String key = LocalApiGateway.rotateKey(activity);
        return key == null ? UiLanguage.text(activity,
                "密钥轮换失败：请先打开 DeepSeek",
                "Could not rotate the key: open DeepSeek first")
                : LocalApiGateway.connectionInfo();
    }

    static String setCustomLocalApiKey(Activity activity, String key) {
        String error = LocalApiGateway.setCustomKey(activity, key);
        return error == null ? UiLanguage.text(activity, "保存成功", "Saved") : error;
    }

    static String localApiEndpoint() { return LocalApiGateway.endpoint(); }

    static String localApiRootEndpoint() { return LocalApiGateway.rootEndpoint(); }

    static int localApiPreferredPort(Activity activity) {
        return LocalApiGateway.preferredPort(activity);
    }

    static String setLocalApiPreferredPort(Activity activity, String value) {
        int requested;
        try {
            requested = Integer.parseInt(value == null ? "" : value.trim());
        } catch (Throwable t) {
            return UiLanguage.text(activity, "监听端口必须是数字",
                    "Listener port must be a number");
        }
        String error = LocalApiGateway.setPreferredPort(activity, requested);
        if (error != null) return error;
        boolean restart = LocalApiGateway.isRunning() && isLocalApiEnabled();
        if (restart) {
            LocalApiGateway.stop();
            startLocalApiGateway(activity);
        }
        return UiLanguage.text(activity,
                restart ? "端口已保存，本地 API 已重新监听"
                        : "端口已保存，下次启动本地 API 时生效",
                restart ? "Port saved and the local API listener restarted"
                        : "Port saved; it will apply on the next local API start");
    }

    static Bundle localApiPublicTunnelStatus(Activity activity) {
        return callPublicTunnelProvider(activity,
                XposedActivationProvider.METHOD_GET_PUBLIC_TUNNEL, null);
    }

    static Bundle configureLocalApiPublicTunnel(Activity activity, String token,
                                                String domains, String transport,
                                                String directRoot) {
        Bundle extras = new Bundle();
        extras.putString("token", token == null ? "" : token);
        extras.putString("domains", domains == null ? "" : domains);
        extras.putString("transport", transport == null
                ? PublicTunnelManager.TRANSPORT_AUTO : transport);
        extras.putString("direct_root", directRoot == null ? "" : directRoot);
        return callPublicTunnelProvider(activity,
                XposedActivationProvider.METHOD_CONFIGURE_PUBLIC_TUNNEL, extras);
    }

    static Bundle setLocalApiPublicTunnelEnabled(Activity activity, boolean enabled) {
        Bundle extras = new Bundle();
        extras.putBoolean("enabled", enabled);
        return callPublicTunnelProvider(activity,
                XposedActivationProvider.METHOD_SET_PUBLIC_TUNNEL, extras);
    }

    static Bundle localApiPinggyTunnelStatus(Activity activity) {
        return callPublicTunnelProvider(activity,
                XposedActivationProvider.METHOD_GET_PINGGY_TUNNEL, null);
    }

    static Bundle setLocalApiPinggyTunnelEnabled(Activity activity, boolean enabled) {
        Bundle extras = new Bundle();
        extras.putBoolean("enabled", enabled);
        return callPublicTunnelProvider(activity,
                XposedActivationProvider.METHOD_SET_PINGGY_TUNNEL, extras);
    }

    static String localApiEndpointForPublicRoot(String root) {
        String value = root == null ? "" : root.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.length() == 0) return "";
        return LocalApiGateway.PROTOCOL_ANTHROPIC.equals(localApiProtocol())
                ? value : value + "/v1";
    }

    private static Bundle callPublicTunnelProvider(Activity activity, String method,
                                                   Bundle extras) {
        Bundle unavailable = new Bundle();
        unavailable.putBoolean("accepted", false);
        if (activity == null) {
            unavailable.putString("error", UiLanguage.text(
                    "DeepSeek 界面尚未就绪", "DeepSeek UI is not ready"));
            return unavailable;
        }
        Bundle bridged = callPublicTunnelBinder(method, extras);
        if (bridged != null) return bridged;
        if (!publicTunnelProviderUnavailable) {
            try {
                Bundle reply = activity.getContentResolver().call(
                        Uri.parse("content://" + XposedActivationProvider.AUTHORITY),
                        method, null, extras);
                if (reply != null) return reply;
                publicTunnelProviderUnavailable = true;
            } catch (Throwable t) {
                publicTunnelProviderUnavailable = true;
                log("public tunnel provider unavailable; using explicit Binder bridge: " + t);
            }
        }
        requestPublicTunnelBridge(activity);
        unavailable.putString("error", UiLanguage.text(activity,
                "正在连接模块公网服务，请稍候…",
                "Connecting to the module public service; please wait…"));
        return unavailable;
    }

    private static Bundle callPublicTunnelBinder(String method, Bundle extras) {
        IBinder binder = publicTunnelBridgeBinder;
        if (binder == null || !binder.isBinderAlive()) return null;
        int transaction;
        if (XposedActivationProvider.METHOD_CONFIGURE_PUBLIC_TUNNEL.equals(method)) {
            transaction = PublicTunnelBinderBridge.TRANSACTION_CONFIGURE;
        } else if (XposedActivationProvider.METHOD_SET_PUBLIC_TUNNEL.equals(method)) {
            transaction = PublicTunnelBinderBridge.TRANSACTION_SET_REQUESTED;
        } else if (XposedActivationProvider.METHOD_SET_PINGGY_TUNNEL.equals(method)) {
            transaction = PublicTunnelBinderBridge.TRANSACTION_SET_PINGGY_REQUESTED;
        } else if (XposedActivationProvider.METHOD_GET_PINGGY_TUNNEL.equals(method)) {
            transaction = PublicTunnelBinderBridge.TRANSACTION_PINGGY_STATUS;
        } else {
            transaction = PublicTunnelBinderBridge.TRANSACTION_STATUS;
        }
        Parcel request = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            request.writeInterfaceToken(PublicTunnelBinderBridge.DESCRIPTOR);
            request.writeBundle(extras);
            if (!binder.transact(transaction, request, reply, 0)) return null;
            reply.readException();
            Bundle result = reply.readBundle(Main.class.getClassLoader());
            if (result != null) result.setClassLoader(Main.class.getClassLoader());
            return result;
        } catch (Throwable t) {
            if (!binder.isBinderAlive()) publicTunnelBridgeBinder = null;
            log("public tunnel Binder transaction failed: " + t);
            return null;
        } finally {
            reply.recycle();
            request.recycle();
        }
    }

    private static void requestPublicTunnelBridge(Activity activity) {
        IBinder existing = publicTunnelBridgeBinder;
        if (existing != null && existing.isBinderAlive()) return;
        if (activity == null) return;
        long now = SystemClock.elapsedRealtime();
        synchronized (Main.class) {
            if (publicTunnelBridgeBinding && now - publicTunnelBridgeRequestAt < 5_000L) {
                return;
            }
            publicTunnelBridgeBinding = true;
            publicTunnelBridgeRequestAt = now;
        }
        Uri uri = new Uri.Builder()
                .scheme(LocalApiKeepAliveActivity.SCHEME)
                .authority(LocalApiKeepAliveActivity.HOST)
                .appendQueryParameter(LocalApiKeepAliveActivity.QUERY_MODE,
                        LocalApiKeepAliveActivity.MODE_PUBLIC_TUNNEL_BIND)
                .appendQueryParameter(LocalApiKeepAliveActivity.QUERY_TOKEN,
                        LocalApiKeepAliveService.CONTROL_TOKEN)
                .build();
        Intent bridge = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        attachPublicTunnelReceiver(bridge);
        try {
            activity.startActivity(bridge);
            log("public tunnel Binder trampoline requested");
        } catch (Throwable t) {
            publicTunnelBridgeBinding = false;
            publicTunnelBridgeReceiver = null;
            log("public tunnel Binder trampoline failed: " + t);
        }
    }

    private static void attachPublicTunnelReceiver(Intent intent) {
        if (intent == null) return;
        IBinder existing = publicTunnelBridgeBinder;
        if (existing != null && existing.isBinderAlive()) return;
        final ResultReceiver receiver = new ResultReceiver(
                new Handler(Looper.getMainLooper())) {
            @Override protected void onReceiveResult(int resultCode, Bundle resultData) {
                publicTunnelBridgeBinding = false;
                publicTunnelBridgeReceiver = null;
                IBinder binder = resultData == null ? null : resultData.getBinder(
                        LocalApiKeepAliveActivity.EXTRA_PUBLIC_TUNNEL_BINDER);
                if (binder == null || !binder.isBinderAlive()) {
                    log("public tunnel Binder trampoline returned no live Binder");
                    return;
                }
                publicTunnelBridgeBinder = binder;
                try {
                    final IBinder connected = binder;
                    binder.linkToDeath(new IBinder.DeathRecipient() {
                        @Override public void binderDied() {
                            if (publicTunnelBridgeBinder == connected) {
                                publicTunnelBridgeBinder = null;
                            }
                        }
                    }, 0);
                } catch (Throwable t) {
                    publicTunnelBridgeBinder = null;
                    log("public tunnel Binder death link failed: " + t);
                    return;
                }
                Bundle status = callPublicTunnelBinder(
                        XposedActivationProvider.METHOD_GET_PUBLIC_TUNNEL, null);
                log("public tunnel Binder bridge connected, status="
                        + (status != null && status.getBoolean("accepted", false))
                        + ", binary=" + (status != null
                        && status.getBoolean("binary_available", false)));
            }
        };
        publicTunnelBridgeReceiver = receiver;
        intent.putExtra(LocalApiKeepAliveActivity.EXTRA_PUBLIC_TUNNEL_RECEIVER, receiver);
    }

    static String localApiProtocol() { return LocalApiGateway.protocolMode(); }

    static void setLocalApiProtocol(Activity activity, String protocol) {
        LocalApiGateway.setProtocolMode(activity, protocol);
    }

    static String localApiKey() { return LocalApiGateway.apiKey(); }

    static String localApiRuntimeStatus() { return LocalApiGateway.runtimeStatus(); }

    private static void startLocalApiGateway(Context context) {
        if (context == null || !isLocalApiEnabled()
                || !isLocalApiBackgroundApproved(context)) return;
        final Context appContext = context.getApplicationContext();
        LocalApiGateway.start(appContext, new LocalApiGateway.Backend() {
            @Override public boolean isReady() {
                return isLocalApiBackgroundApproved(appContext)
                        && hostClassLoader != null && liveR92 != null && liveQ71 != null;
            }

            @Override public String readinessDetail() {
                if (!isLocalApiBackgroundApproved(appContext)) return UiLanguage.text(
                        "后台运行权限未通过校验", "Background permission is not approved");
                if (hostClassLoader == null) return UiLanguage.text(
                        "等待宿主类加载器", "Waiting for host class loader");
                if (liveR92 == null && liveQ71 == null) return UiLanguage.text(
                        "等待原生传输与 PoW 初始化", "Waiting for native transport and PoW");
                if (liveR92 == null) return UiLanguage.text(
                        "等待原生传输初始化", "Waiting for native transport");
                if (liveQ71 == null) return UiLanguage.text(
                        "等待 PoW 初始化", "Waiting for PoW");
                return UiLanguage.text(
                        "原生传输已就绪（排队 " + LOCAL_API_COMPLETION_SLOTS.getQueueLength() + "）",
                        "Native transport ready (queued "
                                + LOCAL_API_COMPLETION_SLOTS.getQueueLength() + ")");
            }

            @Override public LocalApiGateway.CompletionResult complete(
                    LocalApiGateway.CompletionRequest request,
                    LocalApiGateway.DeltaSink sink) throws Exception {
                Main module = MODULE;
                if (module == null) {
                    throw new LocalApiGateway.GatewayException(503, "host_not_ready",
                            "server_error", "Deekseep hook instance is unavailable");
                }
                return module.executeLocalApiCompletion(request, sink);
            }
        });
    }

    /**
     * DeepSeek login mapping:
     *   cy4.b = List&lt;px4&gt;, px4.a = Google, px4.b = SMS/mobile, px4.f = WeChat.
     * dy4 only changes which native items are present for a region; gy4 keeps the real click
     * routes. Hook both the copy method and constructors so interpreted, JIT and inlined state
     * creation paths all converge on the same two-switch policy.
     */
    private void hookRegionalLoginUnlock(final ClassLoader cl) {
        try {
            final Class<?> stateType = HostCompat.load(cl, "cy4");
            final Class<?> optionType = HostCompat.load(cl, "px4");
            Field googleField = optionType.getDeclaredField("a");
            Field mobileField = optionType.getDeclaredField("b");
            Field wechatField = optionType.getDeclaredField("f");
            googleField.setAccessible(true);
            mobileField.setAccessible(true);
            wechatField.setAccessible(true);
            final Object googleOption = googleField.get(null);
            final Object mobileOption = mobileField.get(null);
            final Object wechatOption = wechatField.get(null);
            int constructors = 0;
            int copies = 0;

            for (Constructor<?> ctor : stateType.getDeclaredConstructors()) {
                final int listIndex = findAssignableParameter(ctor.getParameterTypes(), List.class);
                if (listIndex < 0) continue;
                hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        return proceedWithRegionalLoginOptions(chain, listIndex, googleOption,
                                wechatOption, mobileOption, optionType);
                    }
                });
                try { deoptimize(ctor); } catch (Throwable t) {
                    log("regional login ctor deopt skipped: " + t);
                }
                constructors++;
            }

            for (Method method : stateType.getDeclaredMethods()) {
                if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != stateType) continue;
                final int listIndex = findAssignableParameter(method.getParameterTypes(), List.class);
                if (listIndex < 0) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        return proceedWithRegionalLoginOptions(chain, listIndex, googleOption,
                                wechatOption, mobileOption, optionType);
                    }
                });
                try { deoptimize(method); } catch (Throwable t) {
                    log("regional login state-copy deopt skipped: " + t);
                }
                copies++;
            }
            log("hooked native regional login options: cy4 ctors=" + constructors
                    + ", copies=" + copies + ", google=" + isGoogleLoginUnlock()
                    + ", wechatMobile=" + isWechatMobileLoginUnlock());
        } catch (Throwable t) {
            log("hookRegionalLoginUnlock failed: " + t);
        }
    }

    private Object proceedWithRegionalLoginOptions(Chain chain, int listIndex,
                                                   Object googleOption, Object wechatOption,
                                                   Object mobileOption, Class<?> optionType)
            throws Throwable {
        boolean unlockGoogle = isGoogleLoginUnlock();
        boolean unlockWechatMobile = isWechatMobileLoginUnlock();
        if (!unlockGoogle && !unlockWechatMobile) return chain.proceed();
        try {
            Object[] args = chain.getArgs().toArray();
            List<?> original = args[listIndex] instanceof List ? (List<?>) args[listIndex] : null;
            List<?> unlocked = original;
            if (unlockGoogle) {
                unlocked = GoogleLoginUnlock.ensureGoogleFirst(
                        unlocked, googleOption, optionType);
            }
            if (unlockWechatMobile) {
                unlocked = GoogleLoginUnlock.ensureWechatAndMobile(
                        unlocked, googleOption, wechatOption, mobileOption, optionType);
            }
            if (unlocked != null && unlocked != original) {
                args[listIndex] = unlocked;
                if (unlockGoogle && !googleLoginUnlockInjectedLogged
                        && unlocked.contains(googleOption) && !original.contains(googleOption)) {
                    googleLoginUnlockInjectedLogged = true;
                    log("native Google login option injected; preserved domestic options="
                            + original.size());
                }
                if (unlockWechatMobile && !wechatMobileLoginUnlockInjectedLogged
                        && (unlocked.contains(wechatOption) || unlocked.contains(mobileOption))) {
                    wechatMobileLoginUnlockInjectedLogged = true;
                    log("native WeChat + mobile login options enabled; original options="
                            + original.size() + ", unlocked options=" + unlocked.size());
                }
                return chain.proceed(args);
            }
        } catch (Throwable t) {
            log("regional login option injection skipped: " + t);
        }
        return chain.proceed();
    }

    private static int findAssignableParameter(Class<?>[] types, Class<?> wanted) {
        if (types == null || wanted == null) return -1;
        for (int i = 0; i < types.length; i++) {
            if (wanted.isAssignableFrom(types[i])) return i;
        }
        return -1;
    }

    /** Installs the no-op endpoint used by the module's foreground keepalive service. */
    private void installLocalApiKeepAliveReceiverHook(ClassLoader cl) {
        try {
            Class<?> receiverClass = Class.forName(
                    LocalApiKeepAliveService.TARGET_RECEIVER, false, cl);
            Method onReceive = receiverClass.getDeclaredMethod(
                    "onReceive", Context.class, Intent.class);
            onReceive.setAccessible(true);
            hook(onReceive).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Intent intent = chain.getArg(1) instanceof Intent
                            ? (Intent) chain.getArg(1) : null;
                    if (intent == null) {
                        return chain.proceed();
                    }
                    String action = intent.getAction();
                    boolean heartbeatAction = LocalApiKeepAliveService.ACTION_HEARTBEAT
                            .equals(action);
                    boolean controlAction = LocalApiKeepAliveService.ACTION_CONTROL
                            .equals(action);
                    boolean proactiveAction = ProactiveHeartbeatReceiver.ACTION_REQUEST
                            .equals(action);
                    boolean agentCommandAction = ACTION_AGENT_COMMAND.equals(action);
                    if (!heartbeatAction && !controlAction && !proactiveAction
                            && !agentCommandAction) {
                        return chain.proceed();
                    }
                    if (agentCommandAction) {
                        if (!LocalApiKeepAliveService.CONTROL_TOKEN.equals(
                                intent.getStringExtra(
                                        LocalApiKeepAliveService.EXTRA_CONTROL_TOKEN))) {
                            log("rejected unauthenticated Agent command");
                            return null;
                        }
                        Context context = chain.getArg(0) instanceof Context
                                ? (Context) chain.getArg(0) : null;
                        String command = intent.getStringExtra(
                                EXTRA_AGENT_COMMAND);
                        if (command == null || command.length() == 0) {
                            String encoded = intent.getStringExtra(
                                    EXTRA_AGENT_COMMAND_BASE64);
                            if (encoded != null && encoded.length() <= 32 * 1024) {
                                try {
                                    command = new String(
                                            android.util.Base64.decode(
                                                    encoded,
                                                    android.util.Base64.DEFAULT),
                                            java.nio.charset.StandardCharsets.UTF_8);
                                } catch (Throwable ignored) {}
                            }
                        }
                        handleAgentCommand(context, command);
                        return null;
                    }
                    if (proactiveAction) {
                        if (!ProactiveHeartbeatReceiver.TOKEN.equals(
                                intent.getStringExtra(ProactiveHeartbeatReceiver.EXTRA_TOKEN))) {
                            log("rejected unauthenticated proactive heartbeat");
                            return null;
                        }
                        Context context = chain.getArg(0) instanceof Context
                                ? (Context) chain.getArg(0) : null;
                        String requestId = intent.getStringExtra(
                                ProactiveHeartbeatReceiver.EXTRA_REQUEST_ID);
                        boolean taskReminder = intent.getBooleanExtra(
                                ProactiveHeartbeatReceiver.EXTRA_TASK_REMINDER, false);
                        String taskText = intent.getStringExtra(
                                ProactiveHeartbeatReceiver.EXTRA_TASK_TEXT);
                        String taskKind = intent.getStringExtra(
                                ProactiveHeartbeatReceiver.EXTRA_TASK_KIND);
                        String conversationId = intent.getStringExtra(
                                ProactiveHeartbeatReceiver.EXTRA_CONVERSATION_ID);
                        runProactiveHeartbeat(
                                context, requestId, taskText, taskReminder, taskKind,
                                conversationId);
                        return null;
                    }
                    if (!LocalApiKeepAliveService.CONTROL_TOKEN.equals(
                            intent.getStringExtra(LocalApiKeepAliveService.EXTRA_CONTROL_TOKEN))) {
                        log("rejected unauthenticated local API internal control");
                        return null;
                    }
                    Context context = chain.getArg(0) instanceof Context
                            ? (Context) chain.getArg(0) : null;
                    if (controlAction) {
                        String protocol = intent.getStringExtra(
                                LocalApiKeepAliveService.EXTRA_PROTOCOL);
                        if (LocalApiGateway.PROTOCOL_OPENAI.equals(protocol)
                                || LocalApiGateway.PROTOCOL_ANTHROPIC.equals(protocol)) {
                            LocalApiGateway.setProtocolMode(context, protocol);
                        }
                        return null;
                    }
                    boolean active = context != null && isLocalApiEnabled()
                            && isLocalApiBackgroundApproved(context);
                    localApiKeepAliveHeartbeatAt = SystemClock.elapsedRealtime();
                    localApiKeepAliveError = "";
                    if (active) {
                        startLocalApiGateway(context);
                    } else if (LocalApiGateway.isRunning()) {
                        LocalApiGateway.stop();
                    }
                    Object receiver = chain.getThisObject();
                    if (receiver instanceof BroadcastReceiver
                            && ((BroadcastReceiver) receiver).isOrderedBroadcast()) {
                        BroadcastReceiver ordered = (BroadcastReceiver) receiver;
                        ordered.setResultCode(Activity.RESULT_OK);
                        ordered.setResultData((active ? "enabled" : "disabled") + "|"
                                + (LocalApiGateway.isRunning() ? "running" : "stopped"));
                        Bundle details = new Bundle();
                        details.putInt("gateway_port",
                                LocalApiGateway.isRunning() ? LocalApiGateway.port() : 0);
                        ordered.setResultExtras(details);
                    }
                    return null;
                }
            });
            log("local API cached-freezer keepalive receiver installed");
        } catch (Throwable t) {
            localApiKeepAliveError = "保活接收器安装失败：" + safeThrowableMessage(t);
            log("local API keepalive receiver hook failed: " + t);
        }
    }

    private static void runProactiveHeartbeat(final Context context, final String requestId,
                                              final String taskText,
                                              final boolean taskReminder,
                                              final String requestedTaskKind,
                                              final String requestedConversationId) {
        if (context == null || (!taskReminder && !isProactiveHeartbeatEnabled())) return;
        final String reminderText = normalizeReminderTask(taskText);
        if (taskReminder && reminderText.length() == 0) return;
        final String taskKind = ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT
                .equals(requestedTaskKind)
                ? ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT
                : ProactiveHeartbeatReceiver.TASK_KIND_REMINDER;
        HeartbeatBinding activeBinding = readHeartbeatBinding();
        String suppliedConversation = HeartbeatToolProtocol.cleanScope(
                requestedConversationId);
        final String conversationId = suppliedConversation.length() > 0
                ? suppliedConversation
                : activeBinding.conversationId;
        if (ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT.equals(taskKind)
                && conversationId.length() == 0) {
            log("proactive heartbeat skipped because no conversation is bound");
            return;
        }
        new Thread(new Runnable() {
            @Override public void run() {
                String id = requestId == null || requestId.length() == 0
                        ? (taskReminder ? "reminder-" : "heartbeat-")
                        + Long.toHexString(System.currentTimeMillis())
                        : requestId;
                try {
                    Main module = awaitProactiveRuntime(15_000L);
                    Integer nativeParent = null;
                    boolean nativeReasoning = false;
                    String nativeModel = "default";
                    NativeHeartbeatHistory beforeHistory = null;
                    if (conversationId.length() > 0) {
                        try {
                            beforeHistory = module.fetchNativeHeartbeatHistory(conversationId);
                            nativeParent = beforeHistory.head;
                            nativeReasoning = beforeHistory.reasoning;
                            nativeModel = beforeHistory.nativeModel;
                        } catch (Throwable historyError) {
                            log("proactive history prefetch failed sid=" + conversationId
                                    + ": " + safeThrowableMessage(historyError));
                        }
                        Object nativeSession = findNativeSession(conversationId);
                        if (nativeSession != null) {
                            if (nativeParent == null) {
                            Object current = invokeNoArg(nativeSession, "t");
                            if (!(current instanceof Number)) {
                                current = invokeNoArg(nativeSession, "e");
                            }
                            if (current instanceof Number
                                    && ((Number) current).intValue() > 0) {
                                nativeParent = Integer.valueOf(
                                        ((Number) current).intValue());
                            }
                            }
                            Object messages = fieldByName(nativeSession, "f");
                            if (messages instanceof Map) {
                                nativeReasoning = nativeHistoryReasoning(
                                        new ArrayList(((Map) messages).values()),
                                        nativeParent);
                            }
                            Object selectedModel = invokeNoArg(nativeSession, "f");
                            if (selectedModel instanceof String
                                    && ((String) selectedModel).trim().length() > 0) {
                                nativeModel = normalizeNativeHeartbeatModel(
                                        (String) selectedModel);
                            }
                        }
                        if (nativeParent == null) {
                            nativeParent = ChatEditorUi.conversationHeadFromAllDbs(
                                    conversationId);
                        }
                        HistoryBridge.Snapshot snapshot =
                                HistoryBridge.snapshot(conversationId);
                        if (snapshot != null) {
                            for (int index = snapshot.rows.size() - 1;
                                 index >= 0; index--) {
                                HistoryBridge.Row row = snapshot.rows.get(index);
                                if (row != null && "USER".equals(row.role)
                                        && row.thinkingEnabled != null) {
                                    // The authenticated history endpoint may omit this nullable
                                    // field. WCDB retains the exact setting used by the visible
                                    // user turn, so it is the reliable final fallback.
                                    nativeReasoning =
                                            row.thinkingEnabled.booleanValue();
                                    break;
                                }
                            }
                        }
                        if (nativeParent == null || nativeParent.intValue() <= 0) {
                            throw new IOException("The bound DeepSeek conversation has no "
                                    + "usable server message head");
                        }
                    }
                    String previous = readHeartbeatHistory(conversationId);
                    if (previous == null) previous = "";
                    if (previous.length() > 5000) {
                        previous = previous.substring(previous.length() - 5000);
                    }
                    long now = System.currentTimeMillis();
                    String instruction;
                    if (taskReminder && ProactiveHeartbeatReceiver.TASK_KIND_REMINDER
                            .equals(taskKind)) {
                        instruction = UiLanguage.text(context,
                                "用户先前明确设置了一个提醒，现在已经到约定时间。提醒事项："
                                        + reminderText + "。请像熟悉的聊天伙伴一样直接、自然、简短地"
                                        + "提醒用户去做这件事。必须说清楚要做什么；不要说时间还没到，"
                                        + "不要提到心跳、定时器、后台、系统提示词或实现方式。"
                                        + "不要使用 Markdown，不超过 100 个汉字。",
                                "The user explicitly scheduled a reminder and its due time has now "
                                        + "arrived. Reminder: " + reminderText
                                        + ". Remind the user directly, naturally, and briefly, like "
                                        + "a familiar conversation partner. Clearly say what they "
                                        + "need to do. Do not say it is too early and do not mention "
                                        + "heartbeats, timers, background work, system prompts, or "
                                        + "implementation details. Use no Markdown and stay under "
                                        + "80 words.");
                    } else {
                        instruction = taskReminder ? reminderText
                                : heartbeatPlanForConversation(conversationId);
                        if (instruction.length() == 0) {
                            instruction = UiLanguage.text(context,
                                    "像熟悉的朋友一样自然、简短地找用户聊聊天；"
                                            + "内容要温暖且具体，不要假装知道未提供的现实情况",
                                    "Start a brief, warm, specific conversation like a familiar "
                                            + "friend, without pretending to know real-world facts "
                                            + "that were not provided");
                        }
                    }
                    String event = HeartbeatToolProtocol.event(
                            taskKind, instruction, now, previous, conversationId,
                            recentBoundConversationContext(conversationId));
                    String prompt = HistoryBridge.wrapSystemPrompt(
                            HeartbeatToolProtocol.systemPrompt(
                                    now, heartbeatPlanForConversation(conversationId),
                                    proactiveHeartbeatIntervalMinutes(), conversationId,
                                    AgentToolConfig.effectiveTools(
                                            isProactiveHeartbeatEnabled())),
                            event);
                    if (conversationId.length() > 0
                            && module.dispatchProactiveThroughNativeUi(
                                    context, id, taskReminder, taskKind,
                                    conversationId, nativeParent,
                                    nativeReasoning, prompt)) {
                        log("proactive heartbeat handed to native chat stream id=" + id
                                + " sid=" + conversationId);
                        return;
                    }
                    LocalApiGateway.CompletionRequest request =
                            new LocalApiGateway.CompletionRequest(
                                    id, taskReminder
                                    ? "deepseek-aux-reminder" : "deepseek-aux-heartbeat",
                                    nativeModel,
                                    prompt, prompt, nativeReasoning, false, 256,
                                    null, null, false)
                                    .withClientSessionScope(
                                            "deekseep-proactive-"
                                                    + conversationId)
                                    .withNativeConversation(
                                            conversationId, nativeParent);
                    tlProactiveHeartbeatRequest.set(Boolean.TRUE);
                    LocalApiGateway.CompletionResult result;
                    try {
                        result = module.executeLocalApiCompletion(request, null);
                    } finally {
                        tlProactiveHeartbeatRequest.remove();
                    }
                    HeartbeatToolProtocol.Result parsed =
                            HeartbeatToolProtocol.parse(
                                    result == null ? null : result.text);
                    executeHeartbeatToolCalls(context, parsed.calls, false);
                    String message = normalizeProactiveMessage(parsed.visibleText);
                    if (message.length() == 0) {
                        throw new IOException("DeepSeek returned an empty proactive message");
                    }
                    if (ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT
                            .equals(taskKind)) {
                        rememberProactiveMessage(conversationId, message);
                    }
                    boolean attached = false;
                    if (conversationId.length() > 0 && nativeParent != null) {
                        try {
                            NativeHeartbeatHistory refreshed =
                                    module.refreshNativeHeartbeatHistory(
                                            conversationId,
                                            beforeHistory == null
                                                    ? nativeParent : beforeHistory.head);
                            boolean persisted =
                                    module.persistNativeHeartbeatHistory(refreshed);
                            if (refreshed != null) {
                                PENDING_NATIVE_HEARTBEAT_HISTORIES.put(
                                        refreshed.sid, refreshed);
                            }
                            boolean applied =
                                    module.applyNativeHeartbeatHistory(refreshed);
                            attached = refreshed != null
                                    && refreshed.head != null
                                    && !nativeParent.equals(refreshed.head);
                            log("proactive response attached sid=" + conversationId
                                    + " head=" + (refreshed == null
                                            ? "null" : refreshed.head)
                                    + " persisted=" + persisted
                                    + " applied=" + applied
                                    + " new_head=" + attached);
                        } catch (Throwable historyError) {
                            // The server has already stored the turn. A later normal history load
                            // will pass through the same folding hook, so notification delivery
                            // must not be lost merely because this eager refresh failed.
                            log("proactive history refresh failed sid=" + conversationId
                                    + ": " + safeThrowableMessage(historyError));
                        }
                    }
                    boolean foreground = isDeepSeekForeground();
                    dispatchProactiveHeartbeatResponse(
                            context, id, message, foreground, taskReminder, taskKind,
                            conversationId);
                    log("proactive heartbeat completed id=" + id
                            + " chars=" + message.length()
                            + " reminder=" + taskReminder
                            + " reasoning=" + nativeReasoning
                            + " model=" + nativeModel
                            + " attached=" + attached
                            + " foreground=" + foreground);
                } catch (Throwable t) {
                    tlProactiveHeartbeatRequest.remove();
                    log("proactive heartbeat failed id=" + id + ": " + t);
                    if (taskReminder) {
                        boolean reminderKind =
                                ProactiveHeartbeatReceiver.TASK_KIND_REMINDER
                                        .equals(taskKind);
                        String fallback = reminderKind
                                ? UiLanguage.text(context,
                                "到时间啦，记得" + reminderText,
                                "It's time — remember to " + reminderText)
                                : UiLanguage.text(context,
                                "来找你啦～" + reminderText,
                                "I'm here — " + reminderText);
                        boolean foreground = isDeepSeekForeground();
                        dispatchProactiveHeartbeatResponse(
                                context, id, fallback, foreground, true, taskKind,
                                conversationId);
                    } else {
                        String fallback = UiLanguage.text(context,
                                "来找你聊聊天啦～",
                                "I'm here to chat with you.");
                        boolean foreground = isDeepSeekForeground();
                        dispatchProactiveHeartbeatResponse(
                                context, id, fallback, foreground, false,
                                ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT,
                                conversationId);
                    }
                }
            }
        }, taskReminder ? "Deekseep-proactive-reminder"
                : "Deekseep-proactive-heartbeat").start();
    }

    private static Main awaitProactiveRuntime(long timeoutMs) throws IOException {
        long deadline = SystemClock.elapsedRealtime() + Math.max(0L, timeoutMs);
        while (true) {
            Main module = MODULE;
            if (module != null && hostClassLoader != null
                    && liveR92 != null && liveQ71 != null) {
                return module;
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                throw new IOException("DeepSeek native transport did not initialize in time");
            }
            try {
                Thread.sleep(250L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("proactive heartbeat initialization was interrupted");
            }
        }
    }

    private static boolean isIdleGenerationState(Object state) {
        String name = simpleName(state);
        return HostCompat.isV230() ? "np".equals(name) : "gp".equals(name);
    }

    /**
     * When the bound conversation is still the active Chat ViewModel, use DeepSeek's own send
     * pipeline. That pipeline owns the Compose message state and SSE reducer, so the assistant
     * bubble appears and streams exactly like an ordinary reply. Background/cold-process cases
     * fall back to the direct native transport and eager history refresh below.
     */
    private boolean dispatchProactiveThroughNativeUi(
            Context context, String requestId, boolean taskReminder, String taskKind,
            String sid, Integer previousHead, boolean reasoning, String prompt) {
        WeakReference<Object> reference = ACTIVE_CHAT_VIEW_MODELS.get(sid);
        final Object viewModel = reference == null ? null : reference.get();
        if (reference != null && viewModel == null) {
            ACTIVE_CHAT_VIEW_MODELS.remove(sid, reference);
        }
        if (viewModel == null || prompt == null || prompt.length() == 0) return false;

        Object selected = invokeNoArg(viewModel, "G");
        if (selected == null || !sid.equals(String.valueOf(
                readHostField(selected, "a")))) return false;
        Object generationState = invokeNoArg(readHostField(selected, "i"), "getValue");
        if (!isIdleGenerationState(generationState)) {
            log("native proactive stream unavailable because chat is busy sid=" + sid
                    + " state=" + simpleName(generationState));
            return false;
        }

        NativeUiHeartbeatRequest existing = PENDING_NATIVE_UI_HEARTBEATS.get(sid);
        if (existing != null
                && System.currentTimeMillis() - existing.startedAt < 4L * 60L * 1000L) {
            log("native proactive stream already pending sid=" + sid);
            return false;
        }
        if (existing != null) PENDING_NATIVE_UI_HEARTBEATS.remove(sid, existing);

        final NativeUiHeartbeatRequest pending = new NativeUiHeartbeatRequest(
                context, requestId, taskReminder, taskKind, sid,
                previousHead, reasoning);
        if (PENDING_NATIVE_UI_HEARTBEATS.putIfAbsent(sid, pending) != null) return false;

        final AtomicBoolean invoked = new AtomicBoolean();
        final CountDownLatch completed = new CountDownLatch(1);
        Runnable send = new Runnable() {
            @Override public void run() {
                try {
                    Object current = invokeNoArg(viewModel, "G");
                    if (current == null || !pending.sid.equals(String.valueOf(
                            readHostField(current, "a")))) return;
                    Object state = invokeNoArg(readHostField(current, "i"), "getValue");
                    if (!isIdleGenerationState(state)) return;

                    invoked.set(invokeNativeUiTextSend(viewModel, prompt));
                } catch (Throwable error) {
                    log("native proactive stream start failed sid=" + pending.sid
                            + ": " + safeThrowableMessage(error));
                } finally {
                    completed.countDown();
                }
            }
        };
        Handler handler = currentMainHandler();
        if (Looper.myLooper() == Looper.getMainLooper() || handler == null) {
            send.run();
        } else {
            handler.post(send);
            try {
                completed.await(4L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (!invoked.get()) {
            PENDING_NATIVE_UI_HEARTBEATS.remove(sid, pending);
            return false;
        }
        log("native proactive stream started id=" + requestId + " sid=" + sid);
        return true;
    }

    /**
     * Sends a normal, visible user text through the host's own composer pipeline. The mappings are
     * shared by proactive heartbeats and Agent question answers so 2.2.x and 2.3.0 cannot silently
     * diverge.
     */
    private static boolean invokeNativeUiTextSend(
            Object viewModel, String prompt) throws Exception {
        if (viewModel == null || prompt == null || prompt.trim().length() == 0) return false;
        ClassLoader cl = viewModel.getClass().getClassLoader();
        Field emptyField = HostCompat.load(cl, "jm7").getDeclaredField("b");
        emptyField.setAccessible(true);
        Object emptyAttachments = emptyField.get(null);
        if (emptyAttachments == null) return false;
        return invokeNativeUiTextSend(viewModel, prompt, emptyAttachments);
    }

    private static boolean invokeNativeUiTextSend(
            Object viewModel, String prompt, Object attachments) throws Exception {
        if (viewModel == null || prompt == null || prompt.trim().length() == 0
                || attachments == null) return false;
        ClassLoader cl = viewModel.getClass().getClassLoader();
        Method sendMethod = null;
        if (HostCompat.isV230()) {
            Class<?> persistentList = HostCompat.load(cl, "h1");
            for (Method method : viewModel.getClass().getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("R".equals(method.getName())
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && types.length == 5
                        && types[0] == viewModel.getClass()
                        && types[1] == String.class
                        && types[2] == persistentList
                        && types[3] == String.class
                        && types[4] == int.class) {
                    sendMethod = method;
                    break;
                }
            }
        } else {
            for (Method method : viewModel.getClass().getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("Q".equals(method.getName()) && types.length == 4
                        && types[0] == String.class
                        && types[2] == String.class) {
                    sendMethod = method;
                    break;
                }
            }
        }
        if (sendMethod == null) return false;
        sendMethod.setAccessible(true);
        // za1.Q(String, h1, String, yq7): the first String is the actual user prompt and the
        // third is an optional audio id. 2.3.0 cc1.R is the Kotlin default bridge; bit 8 supplies
        // the absent audio id while retaining the prompt and immutable attachment list.
        if (HostCompat.isV230()) {
            sendMethod.invoke(null, viewModel, prompt, attachments, null, 8);
        } else {
            sendMethod.invoke(viewModel, prompt, attachments, null, null);
        }
        return true;
    }

    private static final class NativeUiHeartbeatRequest {
        final Context context;
        final String requestId;
        final boolean taskReminder;
        final String taskKind;
        final String sid;
        final Integer previousHead;
        final boolean reasoning;
        final long startedAt;
        final AtomicBoolean completing = new AtomicBoolean();

        NativeUiHeartbeatRequest(Context context, String requestId,
                                 boolean taskReminder, String taskKind,
                                 String sid, Integer previousHead,
                                 boolean reasoning) {
            Context source = context == null ? currentHostContext() : context;
            Context application = source == null ? null : source.getApplicationContext();
            this.context = application == null ? source : application;
            this.requestId = requestId;
            this.taskReminder = taskReminder;
            this.taskKind = taskKind;
            this.sid = sid;
            this.previousHead = previousHead;
            this.reasoning = reasoning;
            this.startedAt = System.currentTimeMillis();
        }
    }

    private static final class NativeHeartbeatHistory {
        final Object response;
        final Object session;
        final String sid;
        final List messages;
        final Integer head;
        final Integer cacheVersion;
        final Integer cacheReset;
        final boolean reasoning;
        final String nativeModel;

        NativeHeartbeatHistory(Object response, Object session, String sid,
                               List messages, Integer head,
                               Integer cacheVersion, Integer cacheReset,
                               boolean reasoning, String nativeModel) {
            this.response = response;
            this.session = session;
            this.sid = sid;
            this.messages = messages;
            this.head = head;
            this.cacheVersion = cacheVersion;
            this.cacheReset = cacheReset;
            this.reasoning = reasoning;
            this.nativeModel = normalizeNativeHeartbeatModel(nativeModel);
        }
    }

    /**
     * Only the three model_type values accepted by DeepSeek's completion endpoint may leave the
     * module. ServerChatSession.g is title_type (for example SYSTEM), not model_type; accepting an
     * arbitrary metadata string here turns a due reminder into a notification-only fallback.
     */
    private static String normalizeNativeHeartbeatModel(String value) {
        String model = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if ("expert".equals(model) || "vision".equals(model)) return model;
        return "default";
    }

    /**
     * Uses DeepSeek's authenticated history endpoint and its own Kotlin serializer. Constructing
     * pw0 also runs the global folding hook, so callers receive only the visible conversation
     * chain even though the server retains the anonymous trigger as the transport parent.
     */
    private NativeHeartbeatHistory fetchNativeHeartbeatHistory(String conversationId)
            throws Throwable {
        String sid = HeartbeatToolProtocol.cleanScope(conversationId);
        ClassLoader cl = hostClassLoader;
        Object q71 = liveQ71;
        if (sid.length() == 0 || cl == null || q71 == null) {
            throw new IOException("DeepSeek history transport is not ready");
        }
        Object services = fieldByName(q71, "f");
        Object historyApi = fieldByName(services, "a");
        if (historyApi == null) throw new IOException("DeepSeek history API is unavailable");

        Class<?> requestType = HostCompat.load(cl, "lj9");
        Constructor<?> requestConstructor =
                requestType.getDeclaredConstructor(
                        Object.class, Object.class, Object.class, Object.class, int.class);
        requestConstructor.setAccessible(true);
        Object historyRequest = requestConstructor.newInstance(
                sid, "stream_close", null, null, Integer.valueOf(7));

        Class<?> continuation = HostCompat.load(cl, "uz1");
        Method fetch = null;
        for (Method method : historyApi.getClass().getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if ("b".equals(method.getName()) && types.length == 2
                    && types[0] == requestType && types[1] == continuation) {
                fetch = method;
                break;
            }
        }
        if (fetch == null) throw new NoSuchMethodException("DeepSeek history fetch");
        Object raw = driveSuspend(cl, fetch, historyApi, new Object[]{historyRequest});
        if (raw == null) throw new IOException("DeepSeek returned no history response");

        Class<?> parserContext = HostCompat.load(cl, "pl9");
        Method parse = raw.getClass().getDeclaredMethod(
                "a", boolean.class, parserContext, continuation);
        Object wrapper = driveSuspend(
                cl, parse, raw, new Object[]{Boolean.FALSE, null});
        if (wrapper == null) throw new IOException("DeepSeek history response was empty");
        Object biz = fieldByName(wrapper, "a");
        Object bizValue = invokeNoArg(biz, "getValue");
        if (!(bizValue instanceof Number)) bizValue = fieldByName(biz, "a");
        if (bizValue instanceof Number && ((Number) bizValue).intValue() != 0) {
            throw new IOException("DeepSeek history rejected the request: "
                    + String.valueOf(fieldByName(wrapper, "b")));
        }

        Object jsonValue = fieldByName(wrapper, "c");
        Class<?> x94 = HostCompat.load(cl, "x94");
        Field codecField = x94.getDeclaredField("a");
        codecField.setAccessible(true);
        Object codec = codecField.get(null);
        Class<?> pw0 = HostCompat.load(cl, "pw0");
        Field companionField = pw0.getDeclaredField("Companion");
        companionField.setAccessible(true);
        Object companion = companionField.get(null);
        Method serializerMethod = companion.getClass().getMethod("serializer");
        serializerMethod.setAccessible(true);
        Object serializer = serializerMethod.invoke(companion);
        Method decode = codec.getClass().getMethod(
                "a", HostCompat.load(cl, "ch4"), HostCompat.load(cl, "m84"));
        decode.setAccessible(true);
        Object response = decode.invoke(codec, serializer, jsonValue);
        if (response == null) throw new IOException("DeepSeek history could not be decoded");

        Object session = fieldByName(response, "a");
        String responseSid = stringField(session, "a");
        if (!sid.equals(responseSid)) {
            throw new IOException("DeepSeek returned history for a different conversation");
        }
        Object messagesValue = fieldByName(response, "b");
        if (!(messagesValue instanceof List)) {
            throw new IOException("DeepSeek returned no history messages");
        }
        List messages = (List) messagesValue;
        Integer head = intField(session, "d");
        if (head == null || head.intValue() <= 0) {
            for (Object message : messages) {
                Integer id = intField(message, "f");
                if (id != null && id.intValue() > 0
                        && (head == null || id.intValue() > head.intValue())) {
                    head = id;
                }
            }
        }
        // za7.i is model_type. za7.g is title_type and commonly contains SYSTEM.
        String model = stringField(session, "i");
        return new NativeHeartbeatHistory(
                response, session, sid, messages, head,
                intField(session, "c"), intField(response, "d"),
                nativeHistoryReasoning(messages, head), model);
    }

    private static boolean nativeHistoryReasoning(List messages, Integer head) {
        if (messages == null || messages.isEmpty()) return false;
        HashMap<Integer, Object> byId = new HashMap<>();
        for (Object message : messages) {
            Integer id = intField(message, "f");
            if (id != null) byId.put(id, message);
        }
        Integer cursor = head;
        HashSet<Integer> seen = new HashSet<>();
        while (cursor != null && seen.add(cursor)) {
            Object message = byId.get(cursor);
            if (message == null) break;
            if ("USER".equals(String.valueOf(fieldByName(message, "h")))) {
                Object thinking = fieldByName(message, "u");
                if (thinking instanceof Boolean) {
                    return ((Boolean) thinking).booleanValue();
                }
            }
            cursor = intField(message, "g");
        }
        for (int index = messages.size() - 1; index >= 0; index--) {
            Object message = messages.get(index);
            if (!"USER".equals(String.valueOf(fieldByName(message, "h")))) continue;
            Object thinking = fieldByName(message, "u");
            if (thinking instanceof Boolean) {
                return ((Boolean) thinking).booleanValue();
            }
        }
        return false;
    }

    private NativeHeartbeatHistory refreshNativeHeartbeatHistory(
            String conversationId, Integer previousHead) throws Throwable {
        NativeHeartbeatHistory latest = null;
        Throwable lastError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(350L * attempt);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw interrupted;
                }
            }
            try {
                latest = fetchNativeHeartbeatHistory(conversationId);
                if (latest.head != null && (previousHead == null
                        || !previousHead.equals(latest.head))) {
                    return latest;
                }
            } catch (Throwable error) {
                lastError = error;
            }
        }
        if (latest != null) return latest;
        throw lastError == null
                ? new IOException("DeepSeek history refresh failed") : lastError;
    }

    /** Persists the exact server IDs and visible parent chain through DeepSeek's own gm8 writer. */
    private boolean persistNativeHeartbeatHistory(NativeHeartbeatHistory history)
            throws Throwable {
        Object repository = liveFm8;
        ClassLoader cl = hostClassLoader;
        if (history == null || repository == null || cl == null
                || history.cacheVersion == null) return false;
        ArrayList rows = new ArrayList(history.messages.size());
        for (Object message : history.messages) {
            if (message == null) continue;
            Method toRow = HostCompat.publicMessageMethod(message, "O");
            toRow.setAccessible(true);
            Object row = toRow.invoke(message);
            if (row != null) rows.add(row);
        }

        Class<?> metadataType = HostCompat.load(cl, "am8");
        Object insertedValue = fieldByName(history.session, "e");
        Object updatedValue = fieldByName(history.session, "f");
        double inserted = insertedValue instanceof Number
                ? ((Number) insertedValue).doubleValue() : 0D;
        double updated = updatedValue instanceof Number
                ? ((Number) updatedValue).doubleValue() : inserted;
        // am8 is a mutable WCDB entity. Its Kotlin constructor changed parameter ordering between
        // host branches, while the persisted fields a..k stayed stable. Populate the no-arg
        // entity by field name so a successful proactive generation can never be lost merely
        // because a Boolean/Integer constructor slot moved.
        Constructor<?> metadataConstructor = metadataType.getDeclaredConstructor();
        metadataConstructor.setAccessible(true);
        Object metadata = metadataConstructor.newInstance();
        if (!forceSetObjectField(metadata, "a", history.sid)
                || !forceSetObjectField(metadata, "d", history.cacheVersion)
                || !forceSetObjectField(metadata, "f", Double.valueOf(inserted))
                || !forceSetObjectField(metadata, "g", Double.valueOf(updated))
                || !forceSetObjectField(metadata, "h", history.head)) {
            throw new IOException("DeepSeek session metadata fields are incompatible");
        }
        forceSetObjectField(metadata, "b", fieldByName(history.session, "b"));
        forceSetObjectField(metadata, "c", fieldByName(history.session, "g"));
        forceSetObjectField(metadata, "e", history.cacheReset);
        forceSetObjectField(metadata, "i", Integer.valueOf(5));
        forceSetObjectField(metadata, "j",
                Boolean.valueOf(Boolean.TRUE.equals(fieldByName(history.session, "h"))));
        forceSetObjectField(metadata, "k", history.nativeModel);

        Method writer = null;
        for (Method method : repository.getClass().getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if ("b".equals(method.getName()) && types.length == 7
                    && types[0] == String.class && types[1] == int.class
                    && List.class.isAssignableFrom(types[4])) {
                writer = method;
                break;
            }
        }
        if (writer == null) throw new NoSuchMethodException("DeepSeek history writer");
        writer.setAccessible(true);
        writer.invoke(repository, history.sid, history.cacheVersion.intValue(),
                history.cacheReset, history.head, rows,
                fieldByName(history.response, "c"), metadata);
        return true;
    }

    /** Applies the refreshed messages on the main thread so an already-open chat updates at once. */
    private boolean applyNativeHeartbeatHistory(final NativeHeartbeatHistory history) {
        if (history == null) return false;
        final ArrayList<Object> sessions = new ArrayList<>();
        java.util.IdentityHashMap<Object, Boolean> seen = new java.util.IdentityHashMap<>();
        Object directorySession = findNativeSession(history.sid);
        if (directorySession != null) {
            sessions.add(directorySession);
            seen.put(directorySession, Boolean.TRUE);
        }
        WeakReference<Object> activeReference = ACTIVE_CHAT_SESSIONS.get(history.sid);
        Object activeSession = activeReference == null ? null : activeReference.get();
        if (activeReference != null && activeSession == null) {
            ACTIVE_CHAT_SESSIONS.remove(history.sid, activeReference);
        } else if (activeSession != null && !seen.containsKey(activeSession)) {
            sessions.add(activeSession);
            seen.put(activeSession, Boolean.TRUE);
        }
        if (sessions.isEmpty()) return false;
        final AtomicInteger applied = new AtomicInteger();
        final CountDownLatch completed = new CountDownLatch(1);
        Runnable update = new Runnable() {
            @Override public void run() {
                try {
                    for (Object session : sessions) {
                        if (mergeNativeHeartbeatHistoryIntoSession(
                                history, session)) {
                            applied.incrementAndGet();
                        }
                    }
                } finally {
                    completed.countDown();
                }
            }
        };
        Handler handler = currentMainHandler();
        if (Looper.myLooper() == Looper.getMainLooper() || handler == null) {
            update.run();
        } else {
            handler.post(update);
            try {
                completed.await(4L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        return applied.get() > 0;
    }

    private static boolean mergeNativeHeartbeatHistoryIntoSession(
            NativeHeartbeatHistory history, Object session) {
        if (history == null || session == null
                || !history.sid.equals(String.valueOf(
                        readHostField(session, "a")))) return false;
        try {
            Method merge = session.getClass().getMethod(
                    "v", List.class, Integer.class, boolean.class);
            merge.setAccessible(true);
            merge.invoke(session, history.messages, history.head, false);
            forceSetObjectField(session, "n", history.cacheVersion);
            forceSetObjectField(session, "o", history.cacheReset);
            HistoryBridge.processNativeSession(session, history.sid);
            return true;
        } catch (Throwable error) {
            log("proactive native session apply failed sid=" + history.sid
                    + ": " + safeThrowableMessage(error));
            return false;
        }
    }

    private static String normalizeReminderTask(String value) {
        return HeartbeatToolProtocol.cleanInstruction(value);
    }

    /**
     * Local command seam for repeatable device tests. It is consumed by the already hooked
     * DeepSeek receiver and accepts the same narrow, validated call schema as model output.
     */
    private static void handleAgentCommand(Context context, String json) {
        if (json == null || json.trim().length() == 0 || json.length() > 16 * 1024) {
            log("ignored empty/oversized Agent command");
            return;
        }
        try {
            JSONObject root = new JSONObject(json);
            String visibleText = root.optString("visible_text", "").trim();
            String scope = HeartbeatToolProtocol.cleanScope(
                    root.optString("scope", ""));
            if (scope.length() == 0) {
                scope = currentAgentConversationScope();
            }
            if (visibleText.length() > 0) {
                if (visibleText.length() > 4000) {
                    visibleText = visibleText.substring(0, 4000);
                }
                queueVisibleAgentAnswer(context, scope, visibleText);
                log("Agent command queued visible text sid=" + scope);
                return;
            }
            JSONObject call = root.optJSONObject("call");
            if (call == null) call = root;
            if (scope.length() == 0
                    && HeartbeatToolProtocol.TOOL_ASK_USER.equals(
                    call.optString("tool", ""))) {
                // Preview-only scope lets the shell command verify the bottom sheet on a blank
                // new-chat screen. A real model call always carries the server conversation id.
                scope = "agent-command-preview";
            }
            if (!call.has("scope")) call.put("scope", scope);
            if (!call.has("id")) {
                call.put("id", "cmd_" + Long.toHexString(
                        System.currentTimeMillis()));
            }
            String framed = HeartbeatToolProtocol.CONTROL_START
                    + "\n{\"call\":" + call.toString() + "}\n"
                    + HeartbeatToolProtocol.CONTROL_END;
            HeartbeatToolProtocol.Result parsed =
                    HeartbeatToolProtocol.parse(framed);
            int completed = executeHeartbeatToolCalls(
                    context, parsed.calls, true);
            log("Agent command executed calls=" + parsed.calls.size()
                    + " completed=" + completed + " sid=" + scope);
        } catch (Throwable error) {
            log("Agent command failed: " + safeThrowableMessage(error));
        }
    }

    private static String currentAgentConversationScope() {
        String latest = HeartbeatToolProtocol.cleanScope(
                lastInteractiveConversationId);
        if (latest.length() > 0) return latest;
        String sidebar = HeartbeatToolProtocol.cleanScope(sidebarCurrentSid);
        if (sidebar.length() > 0) return sidebar;
        for (Map.Entry<String, WeakReference<Object>> entry
                : ACTIVE_CHAT_VIEW_MODELS.entrySet()) {
            WeakReference<Object> reference = entry.getValue();
            Object viewModel = reference == null ? null : reference.get();
            if (viewModel == null) continue;
            Object session = invokeNoArg(viewModel, "G");
            String scope = HeartbeatToolProtocol.cleanScope(
                    String.valueOf(readHostField(session, "a")));
            if (scope.length() > 0 && scope.equals(entry.getKey())) return scope;
        }
        return "";
    }

    private static int executeHeartbeatToolCalls(
            Context context, List<HeartbeatToolProtocol.ToolCall> calls,
            boolean announce) {
        if (!AgentToolConfig.load().enabled || calls == null || calls.isEmpty()) return 0;
        Context effective = context != null ? context : currentHostContext();
        if (effective == null) return 0;
        int completed = 0;
        for (HeartbeatToolProtocol.ToolCall call : calls) {
            if (call == null) continue;
            try {
                boolean success = false;
                String scope = HeartbeatToolProtocol.cleanScope(call.scope);
                if (scope.length() == 0) continue;
                if (!AgentToolConfig.allows(call.tool)) {
                    log("local tool blocked by Agent settings tool=" + call.tool);
                    continue;
                }
                if (AgentToolConfig.isHeartbeatTool(call.tool)
                        && !isProactiveHeartbeatEnabled()) {
                    log("heartbeat tool blocked because proactive messages are disabled tool="
                            + call.tool);
                    continue;
                }
                if (HeartbeatToolProtocol.TOOL_SCHEDULE_ONCE.equals(call.tool)) {
                    long triggerAt = parseHeartbeatToolTime(
                            call.at, System.currentTimeMillis());
                    success = triggerAt > 0L && dispatchProactiveTask(
                            effective, "ai-" + call.id, triggerAt,
                            ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT,
                            call.instruction, scope);
                    if (announce) {
                        showHeartbeatToolToast(effective, success
                                ? UiLanguage.text(effective,
                                "AI 已安排一次性心跳：" + formatHeartbeatTime(triggerAt),
                                "AI scheduled a one-time heartbeat: "
                                        + formatHeartbeatTime(triggerAt))
                                : UiLanguage.text(effective,
                                "AI 给出的时间无效，未安排心跳",
                                "The AI supplied an invalid time; no heartbeat was scheduled"));
                    }
                } else if (HeartbeatToolProtocol.TOOL_SET_PLAN.equals(call.tool)) {
                    String plan = HeartbeatToolProtocol.cleanInstruction(call.instruction);
                    if (plan.length() > 0) success =
                            writeHeartbeatBinding(scope, plan);
                    if (success) dispatchProactiveHeartbeatConfig(effective, true);
                    if (announce) showHeartbeatToolToast(effective, success
                            ? UiLanguage.text(effective,
                            "AI 已更新周期心跳约定",
                            "AI updated the recurring-heartbeat plan")
                            : UiLanguage.text(effective,
                            "周期心跳约定保存失败",
                            "Could not save the recurring-heartbeat plan"));
                } else if (HeartbeatToolProtocol.TOOL_CLEAR_PLAN.equals(call.tool)) {
                    success = writeHeartbeatBinding(scope, "");
                    if (success) dispatchProactiveHeartbeatConfig(effective, true);
                    if (announce) showHeartbeatToolToast(effective, success
                            ? UiLanguage.text(effective,
                            "已清除周期心跳约定",
                            "Recurring-heartbeat plan cleared")
                            : UiLanguage.text(effective,
                            "周期心跳约定清除失败",
                            "Could not clear the recurring-heartbeat plan"));
                } else if (HeartbeatToolProtocol.TOOL_BIND_CHAT.equals(call.tool)) {
                    HeartbeatBinding binding = readHeartbeatBinding();
                    String keptPlan = scope.equals(binding.conversationId)
                            ? binding.instruction : "";
                    success = writeHeartbeatBinding(scope, keptPlan);
                    if (success) dispatchProactiveHeartbeatConfig(effective, true);
                    if (announce) showHeartbeatToolToast(effective, success
                            ? UiLanguage.text(effective,
                            "心跳已绑定当前对话",
                            "Heartbeat bound to this chat")
                            : UiLanguage.text(effective,
                            "心跳绑定失败",
                            "Could not bind heartbeat to this chat"));
                } else if (HeartbeatToolProtocol.TOOL_SET_INTERVAL.equals(call.tool)) {
                    HeartbeatBinding binding = readHeartbeatBinding();
                    String keptPlan = scope.equals(binding.conversationId)
                            ? binding.instruction : "";
                    boolean bound = writeHeartbeatBinding(scope, keptPlan);
                    success = bound
                            && setProactiveHeartbeatInterval(effective, call.minutes);
                    if (announce) showHeartbeatToolToast(effective, success
                            ? UiLanguage.text(effective,
                            "AI 已把心跳间隔设为 " + call.minutes + " 分钟",
                            "AI set the heartbeat interval to " + call.minutes + " minutes")
                            : UiLanguage.text(effective,
                            "AI 设置心跳间隔失败",
                            "AI could not set the heartbeat interval"));
                } else if (HeartbeatToolProtocol.TOOL_CANCEL_HEARTBEAT.equals(call.tool)) {
                    boolean cancelOnce = "once".equals(call.mode)
                            || "all_once".equals(call.mode) || "all".equals(call.mode);
                    boolean cancelPeriodic = "periodic".equals(call.mode)
                            || "all".equals(call.mode);
                    boolean oneShotResult = !cancelOnce
                            || dispatchHeartbeatCancellation(
                                    effective, call.mode, call.targetId, scope);
                    boolean periodicResult = !cancelPeriodic
                            || setProactiveHeartbeatEnabled(effective, false);
                    success = oneShotResult && periodicResult;
                    if (announce) showHeartbeatToolToast(effective, success
                            ? UiLanguage.text(effective,
                            "AI 已取消指定的心跳",
                            "AI cancelled the requested heartbeat")
                            : UiLanguage.text(effective,
                            "取消心跳失败",
                            "Could not cancel the heartbeat"));
                } else if (HeartbeatToolProtocol.TOOL_GET_CURRENT_TIME.equals(call.tool)) {
                    // The exact device time is already rendered into this call's activity row.
                    success = true;
                } else if (HeartbeatToolProtocol.TOOL_ASK_USER.equals(call.tool)) {
                    success = queueAgentQuestion(effective, call, announce);
                } else if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_TAP_SCREEN.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_SWIPE_SCREEN.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_PRESS_BACK.equals(call.tool)) {
                    success = queueAgentUiTool(effective, call, announce);
                }
                if (success) {
                    completed++;
                    log("local tool completed tool=" + call.tool
                            + " id=" + call.id);
                }
            } catch (Throwable t) {
                log("local tool failed tool=" + call.tool + ": " + t);
            }
        }
        return completed;
    }

    private static boolean queueAgentQuestion(
            final Context context, final HeartbeatToolProtocol.ToolCall call,
            final boolean announce) {
        final Activity activity = currentHostActivity();
        if (activity == null || activity.isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
            if (announce) showHeartbeatToolToast(context,
                    UiLanguage.text(context,
                            "当前没有可显示问题的 DeepSeek 界面",
                            "There is no active DeepSeek screen for the question"));
            return false;
        }
        return AgentQuestionUi.enqueue(
                activity, call, new AgentQuestionUi.AnswerListener() {
                    @Override public void onAnswer(
                            String scope, String visibleAnswer) {
                        queueVisibleAgentAnswer(context, scope, visibleAnswer);
                    }
                });
    }

    /**
     * The tool block can finish slightly before DeepSeek marks the assistant stream idle. Retry
     * the native composer for a short bounded window instead of dropping a fast user selection.
     */
    private static void queueVisibleAgentAnswer(
            Context context, String scope, String visibleAnswer) {
        String safeScope = HeartbeatToolProtocol.cleanScope(scope);
        String safeAnswer = visibleAnswer == null ? "" : visibleAnswer.trim();
        if (safeScope.length() == 0 || safeAnswer.length() == 0) return;
        Context source = context == null ? currentHostContext() : context;
        Context application = source == null ? null : source.getApplicationContext();
        Context safeContext = application == null ? source : application;
        Handler handler = currentMainHandler();
        if (handler == null) {
            if (safeContext != null) showHeartbeatToolToast(safeContext,
                    UiLanguage.text(safeContext,
                            "答案发送失败：主界面尚未就绪",
                            "Could not send the answer: the UI is not ready"));
            return;
        }
        handler.post(new VisibleAgentAnswerAttempt(
                safeContext, safeScope, safeAnswer, 0));
    }

    private static final class VisibleAgentAnswerAttempt implements Runnable {
        private static final int MAX_ATTEMPTS = 150;
        private static final long RETRY_MS = 300L;

        final Context context;
        final String scope;
        final String answer;
        final int attempt;

        VisibleAgentAnswerAttempt(
                Context context, String scope, String answer, int attempt) {
            this.context = context;
            this.scope = scope;
            this.answer = answer;
            this.attempt = attempt;
        }

        @Override public void run() {
            boolean sent = false;
            boolean retryable = true;
            try {
                WeakReference<Object> reference = ACTIVE_CHAT_VIEW_MODELS.get(scope);
                Object viewModel = reference == null ? null : reference.get();
                if (reference != null && viewModel == null) {
                    ACTIVE_CHAT_VIEW_MODELS.remove(scope, reference);
                }
                if (viewModel != null) {
                    Object session = invokeNoArg(viewModel, "G");
                    String currentScope = String.valueOf(readHostField(session, "a"));
                    if (!scope.equals(currentScope)) {
                        retryable = false;
                    } else {
                        Object state = invokeNoArg(
                                readHostField(session, "i"), "getValue");
                        if (isIdleGenerationState(state)) {
                            sent = invokeNativeUiTextSend(viewModel, answer);
                            retryable = !sent;
                        }
                    }
                }
            } catch (Throwable error) {
                log("Agent visible answer send failed sid=" + scope
                        + " attempt=" + attempt + ": "
                        + safeThrowableMessage(error));
            }
            if (sent) {
                log("Agent visible answer sent sid=" + scope
                        + " chars=" + answer.length());
                return;
            }
            if (retryable && attempt + 1 < MAX_ATTEMPTS) {
                Handler handler = currentMainHandler();
                if (handler != null) {
                    handler.postDelayed(new VisibleAgentAnswerAttempt(
                            context, scope, answer, attempt + 1), RETRY_MS);
                    return;
                }
            }
            if (context != null) showHeartbeatToolToast(context,
                    UiLanguage.text(context,
                            "答案未发送：请保持在原对话并等待当前回复结束",
                            "Answer not sent: stay in the original chat and wait "
                                    + "for the current response to finish"));
            log("Agent visible answer abandoned sid=" + scope
                    + " attempts=" + (attempt + 1));
        }
    }

    private static boolean queueAgentUiTool(
            final Context context, final HeartbeatToolProtocol.ToolCall call,
            final boolean announce) {
        AgentToolConfig.Snapshot config = AgentToolConfig.load();
        if (!AgentToolConfig.BACKEND_IN_APP.equals(config.backend)
                && AgentToolConfig.PERMISSION_ALL.equals(config.permission)) {
            AgentDeviceBridge.execute(
                    context, call, new AgentDeviceBridge.StatusCallback() {
                        @Override public void onStatus(
                                AgentDeviceBridge.Status status) {
                            log("privileged Agent tool result tool=" + call.tool
                                    + " ok=" + status.connected
                                    + " detail=" + status.detail);
                            if (status.connected
                                    && HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN
                                    .equals(call.tool)) {
                                queueAgentScreenshotUpload(
                                        context, call.scope,
                                        new File(AGENT_SCREENSHOT_DIR,
                                                "latest_screen.png"));
                            }
                            if (!status.connected && announce) {
                                showHeartbeatToolToast(context, status.detail);
                            }
                        }
                    });
            return true;
        }
        final Activity activity = currentHostActivity();
        final Handler handler = currentMainHandler();
        if (activity == null || handler == null || activity.isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
            if (announce) showHeartbeatToolToast(context,
                    UiLanguage.text(context,
                            "当前没有可操作的 DeepSeek 界面",
                            "There is no active DeepSeek screen to operate"));
            return false;
        }
        int actionSpan = 240;
        if (HeartbeatToolProtocol.TOOL_SWIPE_SCREEN.equals(call.tool)) {
            actionSpan = call.durationMs + 180;
        } else if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(call.tool)) {
            actionSpan = 520;
        }
        final long delay;
        synchronized (AGENT_UI_ACTION_LOCK) {
            long now = SystemClock.uptimeMillis();
            long scheduledAt = Math.max(now + 180L, agentUiActionNotBefore);
            agentUiActionNotBefore = scheduledAt + actionSpan;
            delay = Math.max(0L, scheduledAt - now);
        }
        final WeakReference<Activity> reference = new WeakReference<>(activity);
        return handler.postDelayed(new Runnable() {
            @Override public void run() {
                Activity live = reference.get();
                boolean success = false;
                try {
                    success = live != null && !live.isFinishing()
                            && (Build.VERSION.SDK_INT < 17 || !live.isDestroyed())
                            && performAgentUiTool(live, context, call);
                } catch (Throwable error) {
                    log("agent UI tool failed tool=" + call.tool
                            + " id=" + call.id + ": " + error);
                }
                if (!success && announce) {
                    showHeartbeatToolToast(context, UiLanguage.text(context,
                            "界面工具执行失败：" + call.tool,
                            "UI tool failed: " + call.tool));
                }
            }
        }, delay);
    }

    private static boolean performAgentUiTool(
            Activity activity, Context context,
            HeartbeatToolProtocol.ToolCall call) {
        if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(call.tool)) {
            return captureAgentScreenshot(activity, context, call);
        }
        if (HeartbeatToolProtocol.TOOL_PRESS_BACK.equals(call.tool)) {
            activity.onBackPressed();
            return true;
        }
        Window window = activity.getWindow();
        View decor = window == null ? null : window.getDecorView();
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) return false;
        if (HeartbeatToolProtocol.TOOL_TAP_SCREEN.equals(call.tool)) {
            return dispatchAgentTap(activity,
                    normalizedScreenCoordinate(call.x, decor.getWidth()),
                    normalizedScreenCoordinate(call.y, decor.getHeight()));
        }
        if (HeartbeatToolProtocol.TOOL_SWIPE_SCREEN.equals(call.tool)) {
            return dispatchAgentSwipe(activity,
                    normalizedScreenCoordinate(call.x, decor.getWidth()),
                    normalizedScreenCoordinate(call.y, decor.getHeight()),
                    normalizedScreenCoordinate(call.toX, decor.getWidth()),
                    normalizedScreenCoordinate(call.toY, decor.getHeight()),
                    call.durationMs);
        }
        return false;
    }

    private static float normalizedScreenCoordinate(int value, int size) {
        if (size <= 1) return 0.0f;
        int bounded = Math.max(0, Math.min(1000, value));
        return (bounded / 1000.0f) * (size - 1);
    }

    private static boolean dispatchAgentTap(
            Activity activity, float x, float y) {
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(
                now, now + 48L, MotionEvent.ACTION_UP, x, y, 0);
        try {
            boolean accepted = activity.dispatchTouchEvent(down);
            return activity.dispatchTouchEvent(up) || accepted;
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static boolean dispatchAgentSwipe(
            final Activity activity, final float fromX, final float fromY,
            final float toX, final float toY, final int durationMs) {
        final Handler handler = currentMainHandler();
        if (handler == null) return false;
        final long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, fromX, fromY, 0);
        boolean accepted;
        try {
            accepted = activity.dispatchTouchEvent(down);
        } finally {
            down.recycle();
        }
        final int steps = Math.max(4, Math.min(18, durationMs / 40));
        for (int step = 1; step <= steps; step++) {
            final int index = step;
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (activity.isFinishing()
                            || (Build.VERSION.SDK_INT >= 17
                            && activity.isDestroyed())) return;
                    float fraction = index / (float) steps;
                    float x = fromX + ((toX - fromX) * fraction);
                    float y = fromY + ((toY - fromY) * fraction);
                    int action = index == steps
                            ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
                    long eventTime = SystemClock.uptimeMillis();
                    MotionEvent event = MotionEvent.obtain(
                            downTime, eventTime, action, x, y, 0);
                    try {
                        activity.dispatchTouchEvent(event);
                    } finally {
                        event.recycle();
                    }
                }
            }, Math.max(1L, (durationMs * step) / steps));
        }
        return accepted;
    }

    private static boolean captureAgentScreenshot(
            Activity activity, Context context,
            HeartbeatToolProtocol.ToolCall call) {
        Window window = activity.getWindow();
        final View decor = window == null ? null : window.getDecorView();
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) {
            return false;
        }
        int sourceWidth = decor.getWidth();
        int sourceHeight = decor.getHeight();
        float scale = Math.min(1.0f,
                Math.min(1080.0f / sourceWidth, 2400.0f / sourceHeight));
        int width = Math.max(1, Math.round(sourceWidth * scale));
        int height = Math.max(1, Math.round(sourceHeight * scale));
        final Bitmap bitmap;
        try {
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        } catch (Throwable error) {
            log("agent screenshot allocation failed: " + error);
            return false;
        }
        Context source = context == null ? activity : context;
        Context application = source.getApplicationContext();
        final Context safeContext = application == null ? source : application;
        final String safeCallId = call == null || call.id == null ? "" : call.id;
        final String safeScope = call == null
                ? "" : HeartbeatToolProtocol.cleanScope(call.scope);
        if (Build.VERSION.SDK_INT >= 26) {
            Handler handler = currentMainHandler();
            if (handler == null) {
                bitmap.recycle();
                return false;
            }
            try {
                PixelCopy.request(window, bitmap,
                        new PixelCopy.OnPixelCopyFinishedListener() {
                            @Override public void onPixelCopyFinished(int result) {
                                if (result == PixelCopy.SUCCESS) {
                                    writeAgentScreenshotAsync(
                                            safeContext, bitmap,
                                            safeCallId, safeScope);
                                    return;
                                }
                                try { bitmap.recycle(); } catch (Throwable ignored) {}
                                log("agent screenshot PixelCopy failed code=" + result);
                                showHeartbeatToolToast(safeContext,
                                        UiLanguage.text(safeContext,
                                                "截图失败：窗口画面暂不可用",
                                                "Capture failed: the window surface "
                                                        + "is not available"));
                            }
                        }, handler);
                return true;
            } catch (Throwable error) {
                try { bitmap.recycle(); } catch (Throwable ignored) {}
                log("agent screenshot PixelCopy request failed: " + error);
                return false;
            }
        }
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.scale(scale, scale);
            decor.draw(canvas);
        } catch (Throwable error) {
            try { bitmap.recycle(); } catch (Throwable ignored) {}
            log("agent screenshot legacy render failed: " + error);
            return false;
        }
        writeAgentScreenshotAsync(
                safeContext, bitmap, safeCallId, safeScope);
        return true;
    }

    private static void writeAgentScreenshotAsync(
            final Context context, final Bitmap bitmap,
            final String callId, final String scope) {
        Thread writer = new Thread(new Runnable() {
            @Override public void run() {
                saveAgentScreenshot(
                        context, bitmap, callId, scope);
            }
        }, "Deekseep-Agent-Screenshot");
        writer.setDaemon(true);
        writer.start();
    }

    private static void saveAgentScreenshot(
            Context context, Bitmap bitmap, String callId, String scope) {
        boolean privateSaved = false;
        Uri galleryUri = null;
        OutputStream output = null;
        try {
            File directory = new File(AGENT_SCREENSHOT_DIR);
            if (directory.exists() || directory.mkdirs()) {
                File latest = new File(directory, "latest_screen.png");
                output = new FileOutputStream(latest, false);
                privateSaved = bitmap.compress(
                        Bitmap.CompressFormat.PNG, 100, output);
                output.flush();
                output.close();
                output = null;
            }
            if (Build.VERSION.SDK_INT >= 29) {
                String stamp = new SimpleDateFormat(
                        "yyyyMMdd_HHmmss", Locale.US).format(new Date());
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME,
                        "DeepSeek_Agent_" + stamp + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                values.put(MediaStore.Images.Media.RELATIVE_PATH,
                        "Pictures/DeekseepAgent");
                values.put(MediaStore.Images.Media.IS_PENDING, Integer.valueOf(1));
                galleryUri = context.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (galleryUri != null) {
                    output = context.getContentResolver().openOutputStream(
                            galleryUri, "w");
                    if (output == null || !bitmap.compress(
                            Bitmap.CompressFormat.PNG, 100, output)) {
                        throw new IOException("MediaStore screenshot write failed");
                    }
                    output.flush();
                    output.close();
                    output = null;
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, Integer.valueOf(0));
                    context.getContentResolver().update(
                            galleryUri, ready, null, null);
                }
            }
            boolean success = privateSaved || galleryUri != null;
            log("agent screenshot saved=" + success
                    + " call=" + callId + " gallery=" + galleryUri);
            showHeartbeatToolToast(context, success
                    ? UiLanguage.text(context,
                    "截图已保存到 Pictures/DeekseepAgent",
                    "Screenshot saved to Pictures/DeekseepAgent")
                    : UiLanguage.text(context,
                    "截图保存失败", "Could not save screenshot"));
            if (privateSaved && scope != null && scope.length() > 0) {
                queueAgentScreenshotUpload(
                        context, scope, new File(
                                AGENT_SCREENSHOT_DIR, "latest_screen.png"));
            }
        } catch (Throwable error) {
            log("agent screenshot save failed call=" + callId + ": " + error);
            if (galleryUri != null) {
                try {
                    context.getContentResolver().delete(galleryUri, null, null);
                } catch (Throwable ignored) {}
            }
            showHeartbeatToolToast(context,
                    UiLanguage.text(context,
                            "截图保存失败", "Could not save screenshot"));
        } finally {
            if (output != null) {
                try { output.close(); } catch (Throwable ignored) {}
            }
            try { bitmap.recycle(); } catch (Throwable ignored) {}
        }
    }

    private static void queueAgentScreenshotUpload(
            Context context, String scope, File screenshot) {
        String safeScope = HeartbeatToolProtocol.cleanScope(scope);
        if (safeScope.length() == 0 || screenshot == null) return;
        Context source = context == null ? currentHostContext() : context;
        Context application = source == null ? null : source.getApplicationContext();
        Context safeContext = application == null ? source : application;
        Object token = new Object();
        AGENT_SCREENSHOT_UPLOAD_TOKENS.put(safeScope, token);
        Handler handler = currentMainHandler();
        if (handler == null) return;
        handler.post(new AgentScreenshotUploadAttempt(
                safeContext, safeScope, screenshot, token));
    }

    /**
     * Adds the captured PNG through DeepSeek's real composer uploader, waits for its server file
     * id, and then sends the image as the next visible turn in the same conversation.
     */
    private static final class AgentScreenshotUploadAttempt implements Runnable {
        private static final int MAX_ATTEMPTS = 240;
        private static final long RETRY_MS = 250L;

        final Context context;
        final String scope;
        final File screenshot;
        final Object token;
        int attempts;
        Object composer;
        Object attachment;
        String uploadKey = "";
        String fileName = "";

        AgentScreenshotUploadAttempt(
                Context context, String scope, File screenshot, Object token) {
            this.context = context;
            this.scope = scope;
            this.screenshot = screenshot;
            this.token = token;
        }

        @Override public void run() {
            if (AGENT_SCREENSHOT_UPLOAD_TOKENS.get(scope) != token) return;
            attempts++;
            try {
                if (!screenshot.isFile() || screenshot.length() < 1024L) {
                    fail(UiLanguage.text(context,
                            "截图文件不可用", "The screenshot file is unavailable"));
                    return;
                }
                WeakReference<Object> reference = ACTIVE_CHAT_VIEW_MODELS.get(scope);
                Object viewModel = reference == null ? null : reference.get();
                if (reference != null && viewModel == null) {
                    ACTIVE_CHAT_VIEW_MODELS.remove(scope, reference);
                }
                if (viewModel == null) {
                    retryOrFail(UiLanguage.text(context,
                            "原对话尚未就绪", "The original chat is not ready"));
                    return;
                }
                Object session = invokeNoArg(viewModel, "G");
                if (session == null || !scope.equals(String.valueOf(
                        readHostField(session, "a")))) {
                    fail(UiLanguage.text(context,
                            "已离开截图所属对话，未自动上传",
                            "The source chat is no longer active; the capture was not uploaded"));
                    return;
                }
                Object generation = invokeNoArg(
                        readHostField(session, "i"), "getValue");
                if (!isIdleGenerationState(generation)) {
                    retryOrFail(UiLanguage.text(context,
                            "正在等待当前回复结束",
                            "Waiting for the current response to finish"));
                    return;
                }
                if (attachment == null) {
                    composer = invokeNoArg(viewModel, "K");
                    if (composer == null) {
                        retryOrFail(UiLanguage.text(context,
                                "图片上传器尚未就绪",
                                "The image uploader is not ready"));
                        return;
                    }
                    Object currentAttachments =
                            snapshotComposerAttachments(composer);
                    if (currentAttachments instanceof List
                            && !((List) currentAttachments).isEmpty()) {
                        // Never mix an Agent capture into a draft the user is already composing.
                        retryOrFail(UiLanguage.text(context,
                                "输入框已有待发送附件",
                                "The composer already contains an unsent attachment"));
                        return;
                    }
                    uploadKey = String.valueOf(invokeNoArg(composer, "d"));
                    if (uploadKey == null || "null".equals(uploadKey)
                            || uploadKey.length() == 0) {
                        retryOrFail(UiLanguage.text(context,
                                "当前模型标识尚未就绪",
                                "The current model identity is not ready"));
                        return;
                    }
                    fileName = "DeepSeek_Agent_"
                            + new SimpleDateFormat(
                            "yyyyMMdd_HHmmss", Locale.US).format(new Date())
                            + ".png";
                    Object metadata = createNativeScreenshotMetadata(
                            viewModel.getClass().getClassLoader(),
                            screenshot, fileName);
                    if (metadata == null
                            || !invokeNativeScreenshotUploader(
                            composer, metadata, scope)) {
                        fail(UiLanguage.text(context,
                                "无法启动 DeepSeek 图片上传",
                                "Could not start DeepSeek's image upload"));
                        return;
                    }
                    Object after = snapshotComposerAttachments(composer);
                    attachment = findNativeAttachment(after, fileName);
                    schedule();
                    return;
                }

                Object attachments = snapshotComposerAttachments(composer);
                Object liveAttachment = findNativeAttachment(
                        attachments, fileName);
                if (liveAttachment != null) attachment = liveAttachment;
                if (attachment == null || !(attachments instanceof List)) {
                    retryOrFail(UiLanguage.text(context,
                            "正在等待图片进入输入框",
                            "Waiting for the image to enter the composer"));
                    return;
                }
                String remoteId = nativeAttachmentRemoteId(
                        attachment, uploadKey);
                if (remoteId.length() == 0) {
                    retryOrFail(UiLanguage.text(context,
                            "正在上传截图",
                            "Uploading the screenshot"));
                    return;
                }
                String prompt = UiLanguage.text(context,
                        "【Agent 截图】这是刚刚按你的工具调用获取的当前屏幕截图。"
                                + "请基于图片内容继续处理上一条请求；如果图片信息不足，请明确说明。",
                        "[Agent screenshot] This is the current screen captured by your tool call. "
                                + "Continue the previous request using the image. If the image is "
                                + "insufficient, say so clearly.");
                if (!invokeNativeUiTextSend(viewModel, prompt, attachments)) {
                    retryOrFail(UiLanguage.text(context,
                            "截图已上传，正在等待发送",
                            "The screenshot is uploaded and waiting to send"));
                    return;
                }
                AGENT_SCREENSHOT_UPLOAD_TOKENS.remove(scope, token);
                log("Agent screenshot uploaded and sent sid=" + scope
                        + " remote=" + truncateForLog(remoteId, 120));
            } catch (Throwable error) {
                log("Agent screenshot upload attempt failed sid=" + scope
                        + " attempt=" + attempts + ": "
                        + safeThrowableMessage(error));
                retryOrFail(UiLanguage.text(context,
                        "截图自动上传失败", "Automatic screenshot upload failed"));
            }
        }

        private void retryOrFail(String detail) {
            if (attempts < MAX_ATTEMPTS) {
                schedule();
            } else {
                fail(detail);
            }
        }

        private void schedule() {
            Handler handler = currentMainHandler();
            if (handler == null) {
                fail(UiLanguage.text(context,
                        "主界面已关闭", "The main UI was closed"));
                return;
            }
            handler.postDelayed(this, RETRY_MS);
        }

        private void fail(String detail) {
            if (!AGENT_SCREENSHOT_UPLOAD_TOKENS.remove(scope, token)) return;
            log("Agent screenshot upload abandoned sid=" + scope
                    + " attempts=" + attempts + " detail=" + detail);
            if (context != null) showHeartbeatToolToast(context, detail);
        }
    }

    private static Object snapshotComposerAttachments(Object composer) {
        if (composer == null) return null;
        Object draft = invokeNoArg(composer, "l");
        if (draft == null) return null;
        Object mutable = readHostField(
                draft, HostCompat.isV230() ? "a" : "b");
        if (mutable == null) return null;
        Object snapshot = invokeNoArg(mutable, "i");
        return snapshot == null && mutable instanceof List ? mutable : snapshot;
    }

    private static Object createNativeScreenshotMetadata(
            ClassLoader classLoader, File screenshot, String fileName) {
        try {
            Class<?> metadataType = HostCompat.load(classLoader, "wu1");
            Uri uri = Uri.fromFile(screenshot);
            if (HostCompat.isV230()) {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(screenshot.getAbsolutePath(), options);
                Constructor<?> constructor = metadataType.getDeclaredConstructor(
                        Uri.class, String.class, long.class,
                        int.class, int.class);
                constructor.setAccessible(true);
                return constructor.newInstance(
                        uri, fileName, Long.valueOf(screenshot.length()),
                        Integer.valueOf(Math.max(1, options.outWidth)),
                        Integer.valueOf(Math.max(1, options.outHeight)));
            }
            Constructor<?> constructor = metadataType.getDeclaredConstructor(
                    Uri.class, String.class, long.class);
            constructor.setAccessible(true);
            return constructor.newInstance(
                    uri, fileName, Long.valueOf(screenshot.length()));
        } catch (Throwable error) {
            log("native screenshot metadata creation failed: "
                    + safeThrowableMessage(error));
            return null;
        }
    }

    private static boolean invokeNativeScreenshotUploader(
            Object composer, Object metadata, String scope) {
        if (composer == null || metadata == null) return false;
        for (Method method : composer.getClass().getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!"u".equals(method.getName()) || types.length != 2
                    || types[1] != String.class
                    || !types[0].isInstance(metadata)) continue;
            try {
                method.setAccessible(true);
                method.invoke(composer, metadata, scope);
                return true;
            } catch (Throwable error) {
                log("native screenshot uploader call failed: "
                        + safeThrowableMessage(error));
                return false;
            }
        }
        return false;
    }

    private static Object findNativeAttachment(
            Object attachments, String fileName) {
        if (!(attachments instanceof List)) return null;
        List values = (List) attachments;
        for (int index = values.size() - 1; index >= 0; index--) {
            Object candidate = values.get(index);
            if (fileName.equals(String.valueOf(
                    readHostField(candidate, "a")))) return candidate;
        }
        return null;
    }

    private static String nativeAttachmentRemoteId(
            Object attachment, String uploadKey) {
        if (attachment == null || uploadKey == null) return "";
        String methodName = HostCompat.isV230() ? "f" : "e";
        for (Method method : attachment.getClass().getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if (!methodName.equals(method.getName())
                    || types.length != 1 || types[0] != String.class
                    || method.getReturnType() != String.class) continue;
            try {
                method.setAccessible(true);
                Object value = method.invoke(attachment, uploadKey);
                return value == null ? "" : String.valueOf(value).trim();
            } catch (Throwable ignored) {}
        }
        return "";
    }

    private static boolean dispatchProactiveTask(
            Context context, String taskId, long triggerAt,
            String taskKind, String instruction, String conversationId) {
        String safe = HeartbeatToolProtocol.cleanInstruction(instruction);
        String scope = HeartbeatToolProtocol.cleanScope(conversationId);
        if (context == null || taskId == null || taskId.length() == 0
                || safe.length() == 0 || scope.length() == 0
                || triggerAt <= System.currentTimeMillis()) return false;
        try {
            Intent task = new Intent(ProactiveHeartbeatReceiver.ACTION_TASK_CONFIG);
            task.setClassName(SELF, ProactiveHeartbeatReceiver.class.getName());
            task.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            task.putExtra(ProactiveHeartbeatReceiver.EXTRA_TOKEN,
                    ProactiveHeartbeatReceiver.TOKEN);
            task.putExtra(ProactiveHeartbeatReceiver.EXTRA_TASK_ID, taskId);
            task.putExtra(ProactiveHeartbeatReceiver.EXTRA_TASK_TEXT, safe);
            task.putExtra(ProactiveHeartbeatReceiver.EXTRA_TASK_KIND, taskKind);
            task.putExtra(ProactiveHeartbeatReceiver.EXTRA_CONVERSATION_ID, scope);
            task.putExtra(ProactiveHeartbeatReceiver.EXTRA_TRIGGER_AT, triggerAt);
            context.sendBroadcast(task);
            log("proactive task requested id=" + taskId + " kind=" + taskKind
                    + " trigger=" + triggerAt);
            return true;
        } catch (Throwable t) {
            log("proactive task scheduling failed: " + t);
            return false;
        }
    }

    private static boolean dispatchHeartbeatCancellation(
            Context context, String mode, String targetId, String conversationId) {
        String scope = HeartbeatToolProtocol.cleanScope(conversationId);
        boolean validMode = "once".equals(mode) || "all_once".equals(mode)
                || "all".equals(mode);
        String target = targetId == null ? "" : targetId.trim();
        if (context == null || scope.length() == 0 || !validMode
                || ("once".equals(mode)
                        && !target.matches("[A-Za-z0-9_.:-]{4,80}"))) return false;
        try {
            Intent cancel = new Intent(
                    ProactiveHeartbeatReceiver.ACTION_TASK_CANCEL);
            cancel.setClassName(SELF, ProactiveHeartbeatReceiver.class.getName());
            cancel.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            cancel.putExtra(ProactiveHeartbeatReceiver.EXTRA_TOKEN,
                    ProactiveHeartbeatReceiver.TOKEN);
            cancel.putExtra(ProactiveHeartbeatReceiver.EXTRA_CANCEL_MODE, mode);
            cancel.putExtra(
                    ProactiveHeartbeatReceiver.EXTRA_CANCEL_TARGET_ID, target);
            cancel.putExtra(
                    ProactiveHeartbeatReceiver.EXTRA_CONVERSATION_ID, scope);
            context.sendBroadcast(cancel);
            log("proactive task cancellation requested mode=" + mode
                    + " target=" + target + " scope=" + scope);
            return true;
        } catch (Throwable error) {
            log("proactive task cancellation failed: " + error);
            return false;
        }
    }

    static long parseHeartbeatToolTime(String value, long now) {
        if (value == null) return 0L;
        String input = value.trim();
        String[] formats = new String[]{
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mmXXX",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm"
        };
        for (String pattern : formats) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                format.setLenient(false);
                java.text.ParsePosition position = new java.text.ParsePosition(0);
                Date parsed = format.parse(input, position);
                if (parsed == null || position.getIndex() != input.length()) continue;
                long at = parsed.getTime();
                if (at <= now + 10_000L
                        || at > now + 366L * 24L * 60L * 60_000L) return 0L;
                return at;
            } catch (Throwable ignored) {}
        }
        return 0L;
    }

    private static String formatHeartbeatTime(long triggerAt) {
        return new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(new Date(triggerAt));
    }

    private static void showHeartbeatToolToast(
            final Context context, final String message) {
        Handler handler = currentMainHandler();
        if (handler == null || message == null || message.length() == 0) return;
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {}
            }
        });
    }

    private static Activity currentHostActivity() {
        Main module = MODULE;
        if (module == null || module.curAct == null) return null;
        return module.curAct.get();
    }

    private static Context currentHostContext() {
        Context application = hostApplicationContext;
        return application != null ? application : currentHostActivity();
    }

    private static Handler currentMainHandler() {
        Main module = MODULE;
        return module == null ? null : module.main;
    }

    private static String normalizeProactiveMessage(String value) {
        if (value == null) return "";
        String out = value.trim();
        if (out.startsWith("\"") && out.endsWith("\"") && out.length() > 1) {
            out = out.substring(1, out.length() - 1).trim();
        }
        if (out.length() > 600) out = out.substring(0, 600).trim();
        return out;
    }

    private static String readHeartbeatHistory(String conversationId) {
        File file = heartbeatHistoryFile(conversationId);
        return file == null ? null : readSmallText(file.getAbsolutePath());
    }

    private static File heartbeatHistoryFile(String conversationId) {
        String sid = HeartbeatToolProtocol.cleanScope(conversationId);
        if (sid.length() == 0) return null;
        String name = sid.matches("[A-Za-z0-9._-]{4,120}")
                ? sid : Integer.toHexString(sid.hashCode());
        return new File(PROACTIVE_HEARTBEAT_HISTORY_DIR, name + ".txt");
    }

    private static void rememberProactiveMessage(
            String conversationId, String message) {
        try {
            File file = heartbeatHistoryFile(conversationId);
            if (file == null) return;
            String previous = readSmallText(file.getAbsolutePath());
            String line = TS.format(new Date()) + "  " + message;
            String next = previous == null || previous.length() == 0
                    ? line : previous + "\n" + line;
            if (next.length() > 6000) next = next.substring(next.length() - 6000);
            overwriteTextFile(file.getAbsolutePath(), next);
        } catch (Throwable t) {
            log("proactive heartbeat history write failed: " + t);
        }
    }

    private static String recentBoundConversationContext(String conversationId) {
        String sid = HeartbeatToolProtocol.cleanScope(conversationId);
        if (sid.length() == 0) return "";
        try {
            refreshNativeHistorySnapshot(sid);
            HistoryBridge.Snapshot snapshot = HistoryBridge.snapshot(sid);
            List<ChatEditorUi.Msg> thread = ChatEditorUi.loadSnapshotThread(snapshot);
            if (thread == null || thread.isEmpty()) return "";
            StringBuilder context = new StringBuilder();
            int start = Math.max(0, thread.size() - 12);
            for (int i = start; i < thread.size(); i++) {
                ChatEditorUi.Msg message = thread.get(i);
                if (message == null) continue;
                String body = HistoryBridge.stripInjectedSystemPrompts(message.body);
                body = HeartbeatToolProtocol.stripControlBlocks(body).trim();
                if (body.length() == 0) continue;
                if (body.length() > 1200) {
                    body = body.substring(body.length() - 1200);
                }
                String role = "USER".equals(message.role) ? "用户" : "AI";
                context.append(role).append("：").append(body).append('\n');
            }
            String result = context.toString().trim();
            return result.length() <= 8000
                    ? result : result.substring(result.length() - 8000);
        } catch (Throwable t) {
            log("bound heartbeat context read failed: " + safeThrowableMessage(t));
            return "";
        }
    }

    private static boolean isDeepSeekForeground() {
        try {
            Activity activity = currentHostActivity();
            return activity != null && !activity.isFinishing()
                    && activity.hasWindowFocus();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void showProactiveMessageInForeground(final String message) {
        Handler handler = currentMainHandler();
        if (handler == null) return;
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    Activity activity = currentHostActivity();
                    if (activity == null || activity.isFinishing()) return;
                    Toast.makeText(activity, "DeepSeek："
                            + (message.length() > 180
                            ? message.substring(0, 180) + "…" : message),
                            Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {}
            }
        });
    }

    private static void dispatchProactiveHeartbeatResponse(
            Context context, String requestId, String message, boolean foreground,
            boolean taskReminder, String taskKind, String conversationId) {
        try {
            Intent response = new Intent(ProactiveHeartbeatReceiver.ACTION_RESPONSE);
            response.setClassName(SELF, ProactiveHeartbeatReceiver.class.getName());
            response.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_TOKEN,
                    ProactiveHeartbeatReceiver.TOKEN);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_REQUEST_ID, requestId);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_MESSAGE, message);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_FOREGROUND, foreground);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_TASK_REMINDER, taskReminder);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_TASK_KIND, taskKind);
            response.putExtra(ProactiveHeartbeatReceiver.EXTRA_CONVERSATION_ID,
                    HeartbeatToolProtocol.cleanScope(conversationId));
            context.sendBroadcast(response);
        } catch (Throwable t) {
            log("proactive heartbeat response dispatch failed: " + t);
        }
    }

    private static boolean requestLocalApiKeepAlive(Context context, boolean enabled) {
        if (context == null) {
            localApiKeepAliveError = "DeepSeek 上下文尚未就绪";
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        long heartbeatAge = localApiKeepAliveHeartbeatAt <= 0L ? Long.MAX_VALUE
                : Math.max(0L, now - localApiKeepAliveHeartbeatAt);
        if (enabled && heartbeatAge <= 15_000L) return true;
        if (!enabled && heartbeatAge == Long.MAX_VALUE && !localApiKeepAliveControlLogged) {
            return true;
        }
        // The trampoline finishes immediately and resumes DeepSeek. Throttle that onResume so it
        // cannot open the trampoline again before the first five-second heartbeat arrives.
        if (now - localApiKeepAliveLaunchAt < 3_000L) return true;
        Uri uri = new Uri.Builder()
                .scheme(LocalApiKeepAliveActivity.SCHEME)
                .authority(LocalApiKeepAliveActivity.HOST)
                .appendQueryParameter(LocalApiKeepAliveActivity.QUERY_MODE,
                        enabled ? LocalApiKeepAliveActivity.MODE_START
                                : LocalApiKeepAliveActivity.MODE_STOP)
                .appendQueryParameter(LocalApiKeepAliveActivity.QUERY_TOKEN,
                        LocalApiKeepAliveService.CONTROL_TOKEN)
                .build();
        Intent control = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        IBinder publicBinder = publicTunnelBridgeBinder;
        if (publicBinder == null || !publicBinder.isBinderAlive()) {
            synchronized (Main.class) {
                if (!publicTunnelBridgeBinding
                        || now - publicTunnelBridgeRequestAt >= 5_000L) {
                    publicTunnelBridgeBinding = true;
                    publicTunnelBridgeRequestAt = now;
                    attachPublicTunnelReceiver(control);
                }
            }
        }
        try {
            context.startActivity(control);
            localApiKeepAliveLaunchAt = now;
            if (enabled && !localApiKeepAliveControlLogged) {
                localApiKeepAliveControlLogged = true;
                log("local API keepalive trampoline launched");
            } else if (!enabled && localApiKeepAliveControlLogged) {
                log("local API keepalive stop trampoline launched");
                localApiKeepAliveControlLogged = false;
                localApiKeepAliveHeartbeatAt = 0L;
            }
            localApiKeepAliveError = "";
            return true;
        } catch (Throwable t) {
            if (control.hasExtra(LocalApiKeepAliveActivity.EXTRA_PUBLIC_TUNNEL_RECEIVER)) {
                publicTunnelBridgeBinding = false;
                publicTunnelBridgeReceiver = null;
            }
            localApiKeepAliveError = UiLanguage.text(
                    (enabled ? "启动" : "停止") + "前台保活失败：",
                    (enabled ? "Start" : "Stop") + " foreground keepalive failed: ")
                    + safeThrowableMessage(t);
            log("local API keepalive control failed enabled=" + enabled + ": " + t);
            return false;
        }
    }

    static String localApiKeepAliveStatus() {
        if (!isLocalApiEnabled()) return UiLanguage.text(
                "前台保活：未启用", "Foreground keepalive: Disabled");
        long heartbeat = localApiKeepAliveHeartbeatAt;
        long age = heartbeat <= 0L ? -1L
                : Math.max(0L, SystemClock.elapsedRealtime() - heartbeat);
        if (age >= 0L && age <= 15_000L) {
            return UiLanguage.text(
                    "前台保活：✓ 已连接（最近心跳 "
                            + Math.max(0L, age / 1000L) + " 秒前）",
                    "Foreground keepalive: ✓ Connected (last heartbeat "
                            + Math.max(0L, age / 1000L) + "s ago)");
        }
        String error = localApiKeepAliveError;
        if (error != null && error.length() > 0) return UiLanguage.text(
                "前台保活：✗ ", "Foreground keepalive: ✗ ") + error;
        return UiLanguage.text("前台保活：正在等待 DeepSeek 心跳",
                "Foreground keepalive: waiting for a DeepSeek heartbeat");
    }

    /**
     * Reports actual target-scope injection to the module app.  The exported provider validates
     * the Binder caller UID against com.deepseek.chat before persisting this heartbeat, so an
     * arbitrary app cannot make the launcher claim that the DeepSeek scope is active.
     */
    private static void reportActivationHeartbeat(Activity act) {
        if (act == null) return;
        long now = System.currentTimeMillis();
        if (now - activationHeartbeatAttemptAt < 60_000L) return;
        activationHeartbeatAttemptAt = now;
        Bundle extras = new Bundle();
        try {
            extras.putString("package", act.getPackageName());
            try {
                android.content.pm.PackageInfo info = act.getPackageManager()
                        .getPackageInfo(act.getPackageName(), 0);
                extras.putString("versionName", info.versionName);
                extras.putLong("versionCode", Build.VERSION.SDK_INT >= 28
                        ? info.getLongVersionCode() : info.versionCode);
            } catch (Throwable ignored) {}
            Bundle reply = act.getContentResolver().call(
                    Uri.parse("content://" + XposedActivationProvider.AUTHORITY),
                    XposedActivationProvider.METHOD_REPORT_TARGET_ACTIVE, null, extras);
            boolean accepted = reply != null && reply.getBoolean("accepted", false);
            if (accepted && !activationHeartbeatLogged) {
                activationHeartbeatLogged = true;
                log("activation heartbeat accepted by module provider");
            }
            if (accepted) return;
        } catch (Throwable t) {
            publicTunnelProviderUnavailable = true;
            if (!activationHeartbeatLogged) {
                log("activation heartbeat unavailable: " + t);
            }
        }
        // An unmodified host manifest cannot name a module installed later in its package-
        // visibility queries. Explicit components remain addressable, and the receiver validates
        // the real sender UID before recording the heartbeat.
        try {
            Intent fallback = new Intent(XposedActivationReceiver.ACTION);
            fallback.setClassName(SELF, XposedActivationReceiver.class.getName());
            fallback.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            fallback.putExtras(extras);
            fallback.putExtra(XposedActivationReceiver.EXTRA_TOKEN,
                    XposedActivationReceiver.REPORT_TOKEN);
            act.sendBroadcast(fallback);
            if (!activationHeartbeatLogged) {
                log("activation heartbeat dispatched through explicit broadcast fallback");
            }
        } catch (Throwable t) {
            if (!activationHeartbeatLogged) {
                log("activation heartbeat broadcast unavailable: " + t);
            }
        }
    }

    // 视觉中继开关：与 expert 解锁同一个开关（解锁开启即中继开启）。
    private static boolean isExpertRelayEnabled() {
        return new File(EXPERT_UNLOCK_FILE).exists();
    }

    private static void persistReadGrant(Activity act, Intent data, Uri uri) {
        try {
            int flags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
            if (flags != 0) {
                act.getContentResolver().takePersistableUriPermission(uri, flags);
            }
        } catch (Throwable t) {
            log("takePersistableUriPermission skipped: " + t);
        }
    }

    private static String resolveDisplayPath(Activity act, Uri uri) {
        String realPath = resolveRealPath(uri);
        if (realPath != null && realPath.length() > 0) return realPath;

        String name = queryDisplayName(act, uri);
        if (name != null && name.length() > 0) return name + " (" + uri + ")";
        return uri.toString();
    }

    private static String resolveRealPath(Uri uri) {
        try {
            if ("file".equals(uri.getScheme())) return uri.getPath();
            if (!"content".equals(uri.getScheme())) return null;

            String authority = uri.getAuthority();
            if ("com.android.externalstorage.documents".equals(authority)) {
                String docId = DocumentsContract.getDocumentId(uri);
                String[] parts = docId.split(":", 2);
                String volume = parts.length > 0 ? parts[0] : "";
                String rel = parts.length > 1 ? parts[1] : "";
                if ("primary".equalsIgnoreCase(volume)) {
                    return "/storage/emulated/0/" + rel;
                }
                if ("home".equalsIgnoreCase(volume)) {
                    return "/storage/emulated/0/Documents/" + rel;
                }
                if (volume.length() > 0 && rel.length() > 0) {
                    return "/storage/" + volume + "/" + rel;
                }
            }

            if ("com.android.providers.downloads.documents".equals(authority)) {
                String docId = DocumentsContract.getDocumentId(uri);
                if (docId != null && docId.startsWith("raw:")) {
                    return docId.substring(4);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String queryDisplayName(Activity act, Uri uri) {
        Cursor c = null;
        try {
            c = act.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Throwable ignored) {
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private static void refreshPromptSymlink(String displayPath) {
        try {
            File link = new File(PROMPT_LINK_FILE);
            link.delete();
            if (displayPath == null || !displayPath.startsWith("/")) return;
            Os.symlink(displayPath, PROMPT_LINK_FILE);
            log("prompt symlink -> " + displayPath);
        } catch (Throwable t) {
            log("prompt symlink skipped: " + t);
        }
    }

    private static void writeText(String path, String text) {
        try {
            overwriteTextFile(path, text == null ? "" : text);
        } catch (Throwable ignored) {}
    }

    private static void overwriteTextFile(String path, String text) throws Throwable {
        File file = new File(path);
        ensureWritableFile(file);
        try (FileWriter fw = new FileWriter(file, false)) {
            fw.write(text == null ? "" : text);
            fw.flush();
        }
        if (!file.exists()) {
            throw new IllegalStateException("file was not created: " + path);
        }
    }

    private static void ensureWritableFile(File file) throws Throwable {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw new IllegalStateException("cannot create dir: " + parent.getAbsolutePath());
        }
        if (file.exists()) {
            if (file.isDirectory() && !file.delete()) {
                throw new IllegalStateException("path is directory and cannot delete: " + file.getAbsolutePath());
            }
            return;
        }
        if (!file.createNewFile() && !file.exists()) {
            throw new IllegalStateException("cannot create file: " + file.getAbsolutePath());
        }
    }

    private static String readSmallText(String path) {
        File f = new File(path);
        if (!f.exists() || f.length() <= 0) return null;
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String ln;
            while ((ln = br.readLine()) != null) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(ln);
            }
        } catch (Throwable ignored) {
            return null;
        }
        return sb.toString().trim();
    }

    // ── ChatFullCompletionRequest 系统提示词注入 ─────────────────────

    private void hookChatRequest(ClassLoader cl) {
        try {
            Class<?> k = HostCompat.load(cl, "ew0");
            int n = 0;
            for (Constructor<?> ctor : k.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                // 合成构造器首参为 int（kotlinx 序列化标志位），普通构造器首参为 String
                final boolean isSynthetic = pts.length > 0 && pts[0] == int.class;
                final int promptIdx = isSynthetic ? 3 : 2;
                if (pts.length <= promptIdx) continue;
                if (pts[promptIdx] != String.class) continue;
                hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
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
                            // API callers already supplied their system/developer messages in the
                            // translated prompt. Do not silently prepend the UI's global prompt.
                            if (Boolean.TRUE.equals(tlLocalApiRequest.get())) {
                                return chain.proceed();
                            }
                            String conversationId = !isSynthetic
                                    && originalArgs.length > 0
                                    && originalArgs[0] instanceof String
                                    ? HeartbeatToolProtocol.cleanScope(
                                            (String) originalArgs[0]) : "";
                            if (conversationId.length() > 0) {
                                lastInteractiveConversationId = conversationId;
                            }
                            boolean forcedNativeReasoning = false;
                            if (nativeProactiveEvent && !isSynthetic
                                    && originalArgs.length > 4) {
                                NativeUiHeartbeatRequest pending =
                                        PENDING_NATIVE_UI_HEARTBEATS.get(
                                                conversationId);
                                if (pending != null) {
                                    originalArgs[4] = Boolean.valueOf(
                                            pending.reasoning);
                                    forcedNativeReasoning = true;
                                }
                            }
                            String sysPrompt = readPrompt();
                            java.util.Set<String> enabledLocalTools =
                                    AgentToolConfig.effectiveTools(
                                            isProactiveHeartbeatEnabled());
                            String heartbeatTools = !isSynthetic
                                    && conversationId.length() > 0
                                    && !enabledLocalTools.isEmpty()
                                    ? HeartbeatToolProtocol.systemPrompt(
                                            System.currentTimeMillis(),
                                            heartbeatPlanForConversation(conversationId),
                                            proactiveHeartbeatIntervalMinutes(),
                                            conversationId,
                                            enabledLocalTools)
                                    : "";
                            String combinedPrompt = combineSystemPrompts(
                                    sysPrompt, heartbeatTools);
                            if (combinedPrompt.length() > 0) {
                                Object[] args = originalArgs;
                                String orig = originalPrompt;
                                if (orig == null) orig = "";
                                args[promptIdx] = HistoryBridge.wrapSystemPrompt(
                                        combinedPrompt, orig);
                                if (nativeProactiveEvent && !isSynthetic) {
                                    log("native proactive ew0 sid="
                                            + logValue(args[0])
                                            + " parent=" + logValue(args[1])
                                            + " prompt_chars="
                                            + String.valueOf(args[promptIdx]).length()
                                            + " files=" + logValue(args[3])
                                            + " thinking=" + logValue(args[4])
                                            + " search=" + logValue(args[5])
                                            + " audio=" + logValue(args[6])
                                            + " preempt=" + logValue(args[7])
                                            + " model=" + logValue(args[8])
                                            + " pow_chars=" + (args[9] instanceof String
                                            ? ((String) args[9]).length() : -1)
                                            + " mask=" + logValue(args[10])
                                            + " forced_thinking="
                                            + forcedNativeReasoning);
                                }
                                log("injected system prompt (synthetic=" + isSynthetic
                                        + ", heartbeat_tools="
                                        + (heartbeatTools.length() > 0) + ")");
                                return chain.proceed(args);
                            }
                            if (forcedNativeReasoning) {
                                return chain.proceed(originalArgs);
                            }
                        } catch (Throwable t) { log("inject err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            log("hooked ew0 constructors x" + n);
        } catch (Throwable t) { log("hookChatRequest failed: " + t); }
    }

    private static String combineSystemPrompts(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.length() == 0) return right;
        if (right.length() == 0) return left;
        return left + "\n\n" + right;
    }

    private void hookHeartbeatToolResponses(ClassLoader cl) {
        int liveHooks = 0;
        int staticHooks = 0;
        String[] liveClasses = new String[]{"fo2", "ho2"};
        for (String legacyClassName : liveClasses) {
            final boolean executeTools = "fo2".equals(legacyClassName);
            String className = HostCompat.name(legacyClassName);
            final String appendMethod =
                    HostCompat.method(legacyClassName, "g");
            try {
                Class<?> liveResponse = cl.loadClass(className);
                for (Constructor<?> ctor : liveResponse.getDeclaredConstructors()) {
                    hook(ctor).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            try {
                                sanitizeLiveHeartbeatResponse(
                                        chain.getThisObject(), false, executeTools);
                            } catch (Throwable t) {
                                log("heartbeat response constructor filter failed: " + t);
                            }
                            return result;
                        }
                    });
                    liveHooks++;
                }
                for (Method method : liveResponse.getDeclaredMethods()) {
                    final String name = method.getName();
                    Class<?>[] types = method.getParameterTypes();
                    if ((!appendMethod.equals(name) && !"i".equals(name))
                            || types.length == 0 || types[0] != String.class) continue;
                    hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            try {
                                if ("content".equals(chain.getArg(0))) {
                                    sanitizeLiveHeartbeatResponse(
                                            chain.getThisObject(), appendMethod.equals(name),
                                            executeTools);
                                }
                            } catch (Throwable t) {
                                log("heartbeat streaming response filter failed: " + t);
                            }
                            return result;
                        }
                    });
                    liveHooks++;
                }
            } catch (Throwable t) {
                log("heartbeat live response hook unavailable for "
                        + className + ": " + t);
            }
        }
        String[] staticClasses = new String[]{"at7", "ht7"};
        for (String legacyClassName : staticClasses) {
            final boolean renderToolRows = "at7".equals(legacyClassName);
            String className = HostCompat.name(legacyClassName);
            try {
                Class<?> staticResponse = cl.loadClass(className);
                for (Constructor<?> ctor : staticResponse.getDeclaredConstructors()) {
                    Class<?>[] types = ctor.getParameterTypes();
                    final int contentIndex;
                    if ((types.length == 3 || types.length == 4)
                            && types[1] == String.class) {
                        contentIndex = 1;
                    } else if (types.length >= 4 && types[3] == String.class) {
                        contentIndex = 3;
                    } else {
                        continue;
                    }
                    hook(ctor).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object raw = chain.getArg(contentIndex);
                            if (!(raw instanceof String)) return chain.proceed();
                            String safe = renderToolRows
                                    ? HeartbeatToolProtocol.renderConversationToolRows(
                                            (String) raw)
                                    : HeartbeatToolProtocol.stripControlBlocks((String) raw);
                            if (safe.equals(raw)) return chain.proceed();
                            Object[] args = chain.getArgs().toArray();
                            args[contentIndex] = safe;
                            return chain.proceed(args);
                        }
                    });
                    staticHooks++;
                }
            } catch (Throwable t) {
                log("heartbeat static response hook unavailable for "
                        + className + ": " + t);
            }
        }
        log("heartbeat hidden-tool response hooks live=" + liveHooks
                + " static=" + staticHooks);
    }

    private void hookHeartbeatToolStatusStyle(
            ClassLoader cl, String rendererClassName, String styleClassName) {
        try {
            Class<?> renderer = cl.loadClass(rendererClassName);
            Class<?> styleClass = cl.loadClass(styleClassName);
            final Method styleCopy = findHeartbeatToolStatusStyleCopy(styleClass);
            int stringHooks = 0;
            int annotatedHooks = 0;
            for (Method method : renderer.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("b".equals(method.getName()) && types.length == 18
                        && types[0] == String.class
                        && types[2] == long.class && types[4] == long.class
                        && types[13] == styleClass
                        && types[15] == int.class && types[16] == int.class
                        && types[17] == int.class) {
                    hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object raw = chain.getArg(0);
                            if (!(raw instanceof String)
                                    || !HeartbeatToolProtocol.hasToolStatusStyleMarker(
                                            (String) raw)) {
                                return chain.proceed();
                            }
                            String marked = (String) raw;
                            Object[] args = chain.getArgs().toArray();
                            args[0] = HeartbeatToolProtocol
                                    .stripToolStatusStyleMarkers(marked);
                            if (HeartbeatToolProtocol.isIsolatedToolStatusText(marked)) {
                                try {
                                    args[2] = Long.valueOf(
                                            HeartbeatToolProtocol.TOOL_STATUS_GRAY_COLOR);
                                    Object style = args[13];
                                    long fontSize =
                                            scaledHeartbeatToolStatusFontSize(style);
                                    args[4] = Long.valueOf(fontSize);
                                    if (style != null) {
                                        args[13] = copyHeartbeatToolStatusTextStyle(
                                                styleCopy, style, fontSize);
                                    }
                                    Object mask = args[17];
                                    if (mask instanceof Number) {
                                        args[17] = Integer.valueOf(
                                                HeartbeatToolProtocol
                                                        .explicitToolStatusStyleMask(
                                                                ((Number) mask).intValue()));
                                    }
                                    logHeartbeatToolStatusStyleHit(
                                            rendererClassName, "String");
                                } catch (Throwable t) {
                                    logHeartbeatToolStatusStyleError(
                                            rendererClassName, "String", t);
                                }
                            }
                            return chain.proceed(args);
                        }
                    });
                    stringHooks++;
                    continue;
                }
                if (!"c".equals(method.getName()) || types.length != 17
                        || !CharSequence.class.isAssignableFrom(types[0])
                        || types[2] != long.class || types[3] != long.class
                        || types[12] != styleClass
                        || types[14] != int.class || types[15] != int.class
                        || types[16] != int.class) {
                    continue;
                }
                final Constructor<?> annotatedTextConstructor =
                        types[0].getDeclaredConstructor(String.class);
                annotatedTextConstructor.setAccessible(true);
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object raw = chain.getArg(0);
                        if (!(raw instanceof CharSequence)) return chain.proceed();
                        String marked = raw.toString();
                        if (!HeartbeatToolProtocol.hasToolStatusStyleMarker(marked)) {
                            return chain.proceed();
                        }
                        Object[] args = chain.getArgs().toArray();
                        String clean =
                                HeartbeatToolProtocol.stripToolStatusStyleMarkers(marked);
                        try {
                            args[0] = annotatedTextConstructor.newInstance(clean);
                            if (HeartbeatToolProtocol.isIsolatedToolStatusText(marked)) {
                                Object style = args[12];
                                if (style != null) {
                                    long fontSize =
                                            scaledHeartbeatToolStatusFontSize(style);
                                    args[12] = copyHeartbeatToolStatusTextStyle(
                                            styleCopy, style, fontSize);
                                }
                                logHeartbeatToolStatusStyleHit(
                                        rendererClassName, "AnnotatedString");
                            }
                        } catch (Throwable t) {
                            logHeartbeatToolStatusStyleError(
                                    rendererClassName, "AnnotatedString", t);
                        }
                        return chain.proceed(args);
                    }
                });
                annotatedHooks++;
            }
            log("heartbeat tool status Compose hooks string=" + stringHooks
                    + " annotated=" + annotatedHooks
                    + " renderer=" + rendererClassName);
        } catch (Throwable t) {
            log("heartbeat tool status renderer unavailable "
                    + rendererClassName + ": " + t);
        }
    }

    private static Method findHeartbeatToolStatusStyleCopy(
            Class<?> styleClass) throws NoSuchMethodException {
        for (Method method : styleClass.getDeclaredMethods()) {
            Class<?>[] types = method.getParameterTypes();
            if ("e".equals(method.getName()) && types.length == 13
                    && types[0] == styleClass
                    && types[1] == long.class && types[2] == long.class
                    && types[12] == int.class
                    && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(styleClass.getName() + ".e(TextStyle copy)");
    }

    private void hookHeartbeatToolStatusBasicText(
            ClassLoader cl, String rendererClassName, String methodName,
            String styleClassName) {
        try {
            Class<?> renderer = cl.loadClass(rendererClassName);
            Class<?> styleClass = cl.loadClass(styleClassName);
            final Method styleCopy = findHeartbeatToolStatusStyleCopy(styleClass);
            int hooked = 0;
            for (Method method : renderer.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!methodName.equals(method.getName()) || types.length != 14
                        || !CharSequence.class.isAssignableFrom(types[0])
                        || types[2] != styleClass
                        || types[4] != int.class || types[5] != boolean.class
                        || types[6] != int.class || types[7] != int.class
                        || !Map.class.isAssignableFrom(types[8])
                        || types[11] != int.class || types[12] != int.class
                        || types[13] != int.class) {
                    continue;
                }
                final Constructor<?> annotatedTextConstructor =
                        types[0].getDeclaredConstructor(String.class);
                annotatedTextConstructor.setAccessible(true);
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object raw = chain.getArg(0);
                        if (!(raw instanceof CharSequence)) return chain.proceed();
                        String rendered = raw.toString();
                        boolean marked =
                                HeartbeatToolProtocol.hasToolStatusStyleMarker(rendered);
                        boolean registered =
                                HeartbeatToolProtocol.isRegisteredToolStatusText(rendered);
                        if (!marked && !registered) return chain.proceed();
                        Object[] args = chain.getArgs().toArray();
                        try {
                            if (marked || registered) {
                                args[0] = annotatedTextConstructor.newInstance(
                                        HeartbeatToolProtocol
                                                .stripToolStatusStyleMarkers(rendered));
                            }
                            if (registered) {
                                Object style = args[2];
                                if (style != null) {
                                    long fontSize =
                                            scaledHeartbeatToolStatusFontSize(style);
                                    args[2] = copyHeartbeatToolStatusTextStyle(
                                            styleCopy, style, fontSize);
                                }
                                logHeartbeatToolStatusStyleHit(
                                        rendererClassName, "BasicText");
                            }
                        } catch (Throwable t) {
                            logHeartbeatToolStatusStyleError(
                                    rendererClassName, "BasicText", t);
                        }
                        return chain.proceed(args);
                    }
                });
                hooked++;
            }
            log("heartbeat tool status BasicText hooks=" + hooked
                    + " renderer=" + rendererClassName + "." + methodName);
        } catch (Throwable t) {
            log("heartbeat tool status BasicText renderer unavailable "
                    + rendererClassName + "." + methodName + ": " + t);
        }
    }

    private static Object copyHeartbeatToolStatusTextStyle(
            Method styleCopy, Object style, long fontSize) throws Exception {
        return styleCopy.invoke(null, new Object[]{
                style,
                Long.valueOf(HeartbeatToolProtocol.TOOL_STATUS_GRAY_COLOR),
                Long.valueOf(fontSize),
                null,
                null,
                null,
                Long.valueOf(0L),
                Integer.valueOf(0),
                Integer.valueOf(0),
                Long.valueOf(0L),
                null,
                Integer.valueOf(0),
                Integer.valueOf(
                        HeartbeatToolProtocol.TOOL_STATUS_TEXT_STYLE_COPY_MASK)
        });
    }

    private static long scaledHeartbeatToolStatusFontSize(Object style) {
        try {
            Object spanStyle = readHostField(style, "a");
            Object packedValue = readHostField(spanStyle, "b");
            if (packedValue instanceof Number) {
                long packed = ((Number) packedValue).longValue();
                float source = Float.intBitsToFloat((int) packed);
                long unit = packed & 0xFFFFFFFF00000000L;
                if (unit != 0L && !Float.isNaN(source)
                        && !Float.isInfinite(source) && source > 0.0f) {
                    float target = source
                            * HeartbeatToolProtocol.TOOL_STATUS_FONT_SCALE;
                    return unit
                            | (((long) Float.floatToRawIntBits(target))
                            & 0xFFFFFFFFL);
                }
            }
        } catch (Throwable ignored) {}
        return HeartbeatToolProtocol.TOOL_STATUS_FONT_SIZE;
    }

    private static void logHeartbeatToolStatusStyleHit(
            String rendererClassName, String overload) {
        if (HEARTBEAT_STATUS_STYLE_HIT_LOGGED.compareAndSet(false, true)) {
            log("heartbeat tool status style applied renderer="
                    + rendererClassName + " overload=" + overload);
        }
    }

    private static void logHeartbeatToolStatusStyleError(
            String rendererClassName, String overload, Throwable error) {
        if (HEARTBEAT_STATUS_STYLE_ERROR_LOGGED.compareAndSet(false, true)) {
            log("heartbeat tool status style failed renderer="
                    + rendererClassName + " overload=" + overload + ": " + error);
        }
    }

    private static void sanitizeLiveHeartbeatResponse(
            Object fragment, boolean appendUpdate, boolean executeTools) {
        if (fragment == null) return;
        Object stateValue = readHostField(fragment, "c");
        Object current = invokeNoArg(stateValue, "getValue");
        if (!(current instanceof String)) return;
        String hostText = (String) current;
        ArrayList<HeartbeatToolProtocol.ToolCall> freshCalls = new ArrayList<>();
        String safe;
        synchronized (HEARTBEAT_RESPONSE_STREAMS) {
            HeartbeatResponseStream stream = HEARTBEAT_RESPONSE_STREAMS.get(fragment);
            if (stream == null) {
                stream = new HeartbeatResponseStream();
                HEARTBEAT_RESPONSE_STREAMS.put(fragment, stream);
            }
            if (appendUpdate && stream.initialized
                    && hostText.startsWith(stream.visible)) {
                stream.raw = stream.raw
                        + hostText.substring(stream.visible.length());
            } else {
                stream.raw = hostText;
            }
            HeartbeatToolProtocol.Result parsed = executeTools
                    ? HeartbeatToolProtocol.parseForConversation(stream.raw)
                    : HeartbeatToolProtocol.parse(stream.raw);
            safe = parsed.visibleText;
            stream.visible = safe;
            stream.initialized = true;
            // API/proactive generations hold the private native lane and parse their own output.
            // Only an ordinary visible chat response may execute a hidden heartbeat call here.
            if (executeTools && AgentToolConfig.load().enabled
                    && LOCAL_API_COMPLETION_SLOTS.availablePermits() > 0) {
                for (HeartbeatToolProtocol.ToolCall call : parsed.calls) {
                    String fingerprint = call.scope + "|" + call.id + "|" + call.tool;
                    if (stream.executed.add(fingerprint)) freshCalls.add(call);
                }
            }
        }
        if (!safe.equals(hostText)) setMutableStateValue(stateValue, safe);
        if (!freshCalls.isEmpty()) {
            executeHeartbeatToolCalls(currentHostContext(), freshCalls, true);
        }
    }

    private static boolean setMutableStateValue(Object state, Object value) {
        if (state == null) return false;
        for (Class<?> type = state.getClass(); type != null;
             type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!"l".equals(method.getName())
                        || method.getParameterTypes().length != 1) continue;
                try {
                    method.setAccessible(true);
                    method.invoke(state, value);
                    return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private static final class HeartbeatResponseStream {
        String raw = "";
        String visible = "";
        boolean initialized;
        final HashSet<String> executed = new HashSet<>();
    }

    // ── 专家模式解锁：sf5(模型配置)构造后强改 final 字段点亮思考/搜索/上传 ──
    // 服务器默认给 expert 返回 f/g=true 但 j/k/l=null(禁思考/搜索/文件)；构造后回填真模板即本地点亮。
    private void hookExpertUnlock(ClassLoader cl) {
        try {
            final Class<?> sf5 = HostCompat.load(cl, "sf5");
            final Class<?> gf5c = HostCompat.load(cl, "gf5");
            EX_A = sf5.getDeclaredField("a"); EX_A.setAccessible(true);
            EX_F = sf5.getDeclaredField("f"); EX_F.setAccessible(true);
            EX_G = sf5.getDeclaredField("g"); EX_G.setAccessible(true);
            EX_J = sf5.getDeclaredField("j"); EX_J.setAccessible(true);
            EX_K = sf5.getDeclaredField("k"); EX_K.setAccessible(true);
            EX_L = sf5.getDeclaredField("l"); EX_L.setAccessible(true);
            try { GF5_C = gf5c.getDeclaredField("c"); GF5_C.setAccessible(true); } catch (Throwable ignored) {}
            int n = 0;
            for (Constructor<?> ctor : sf5.getDeclaredConstructors()) {
                Class<?>[] pt = ctor.getParameterTypes();
                // synthetic 反序列化构造器：sf5(int i, String a, ... , of5 j[10], lf5 k[11], gf5 l[12], ...)
                // i 是 kotlinx bitmask，位缺失时字段被置 null。构造后再反射写 final 对 App 编译读取点不可见，
                // 故改为「构造前」把模板塞进 args 并置位 bitmask → 字段出生即非空，任何读取路径都能看到。
                final boolean synth = pt.length >= 13 && pt[0] == int.class;
                hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object r;
                        if (synth) {
                            Object[] a = chain.getArgs().toArray();
                            try {
                                if (a != null && a.length >= 13 && "expert".equals(a[1])
                                        && new File(EXPERT_UNLOCK_FILE).exists()) {
                                    int mask = (a[0] instanceof Integer) ? (Integer) a[0] : 0;
                                    // f(32)/g(64) 位缺失时构造器默认 true，无需动；只补 j(512)/k(1024)/l(2048)
                                    if (tplThink != null)  { a[10] = tplThink;  mask |= 512; }
                                    if (tplSearch != null) { a[11] = tplSearch; mask |= 1024; }
                                    if (tplFile != null)   { a[12] = tplFile;   mask |= 2048; }
                                    a[0] = mask;
                                    log("expert ctor-inject (j=" + (tplThink!=null) + " k=" + (tplSearch!=null)
                                            + " file=" + gf5Info(tplFile) + ")");
                                }
                            } catch (Throwable t) { log("expert ctor-inject err: " + t); }
                            r = chain.proceed(a);
                        } else {
                            r = chain.proceed();
                        }
                        try { onSf5Built(chain.getThisObject()); }
                        catch (Throwable t) { log("expert unlock err: " + t); }
                        return r;
                    }
                });
                // API 102 坑：调用方若把 sf5 <init> 内联，构造 hook 不会触发 → 该实例 k/l 仍为 null。
                // deoptimize 强制运行时不内联该构造器，让所有构造路径都走进 hook。
                try { boolean d = deoptimize(ctor); log("deopt sf5 ctor ok=" + d); }
                catch (Throwable t) { log("deopt sf5 ctor err: " + t); }
                n++;
            }
            log("hooked sf5 ctors x" + n + " (expert unlock)");
            // 兜底：构造 hook 可能漏掉「模块加载前已反序列化」的实例，而 UI 门禁读的正是那个旧实例。
            // sf5.b(boolean,bu1) 是模型芯片渲染时取图标的方法，选中的模型必然被渲染 → 借此俘获真正被消费的实例并即时点亮。
            int m = 0;
            for (java.lang.reflect.Method mtd : sf5.getDeclaredMethods()) {
                if (!"b".equals(mtd.getName())) continue;
                Class<?>[] pt = mtd.getParameterTypes();
                if (pt.length != 2 || pt[0] != boolean.class) continue;
                hook(mtd).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object self = chain.getThisObject();
                            if (self != null) {
                                // 无论哪个模型渲染，先尝试俘获模板(default/vision 的 j/k/l 真货)
                                Object j = EX_J.get(self), k = EX_K.get(self), l = EX_L.get(self);
                                if (j != null && tplThink == null) tplThink = j;
                                if (k != null && tplSearch == null) tplSearch = k;
                                if (l != null && gf5Count(l) > 0 && l != tplFile) tplFile = l;
                                if ("expert".equals(EX_A.get(self)) && new File(EXPERT_UNLOCK_FILE).exists()) {
                                    if (EX_L.get(self) == null || gf5Count(EX_L.get(self)) <= 0
                                            || EX_J.get(self) == null || EX_K.get(self) == null) {
                                        synchronized (expertInsts) {
                                            boolean has = false;
                                            for (Object e : expertInsts) if (e == self) { has = true; break; }
                                            if (!has) expertInsts.add(self);
                                        }
                                        applyExpert(self);
                                    }
                                }
                            }
                        } catch (Throwable t) { log("expert b() patch err: " + t); }
                        return chain.proceed();
                    }
                });
                m++;
            }
            log("hooked sf5.b() x" + m + " (expert gate catch)");
        } catch (Throwable t) { log("hookExpertUnlock failed: " + t); }
    }

    // 上传门禁 y91.a(Object,uz1)：事件对象里携带被 UI 消费的真实 sf5。在判空前扫描 arg0 的字段找到 sf5，
    // 打印它的 identityHashCode + l/k/j 状态（对比构造时 patch 的 @hash），并就地点亮 → 直接命中真正被读的实例。
    private void installExpertUploadGate(ClassLoader cl) {
        try {
            final Class<?> sf5 = HostCompat.load(cl, "sf5");
            final Class<?> y91 = HostCompat.load(cl, "y91");
            int n = 0;
            for (final java.lang.reflect.Method mtd : y91.getDeclaredMethods()) {
                if (!"a".equals(mtd.getName())) continue;
                Class<?>[] pt = mtd.getParameterTypes();
                if (pt.length != 2 || pt[0] != Object.class) continue;
                hook(mtd).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object ev = chain.getArg(0);
                            if (ev != null) {
                                for (Field f : ev.getClass().getDeclaredFields()) {
                                    if (!sf5.isAssignableFrom(f.getType())) continue;
                                    f.setAccessible(true);
                                    Object s = f.get(ev);
                                    if (s == null) continue;
                                    boolean isExpert = "expert".equals(EX_A.get(s));
                                    log("[GATE] y91.a sf5 @" + Integer.toHexString(System.identityHashCode(s))
                                            + " a=" + EX_A.get(s) + " l=" + gf5Info(EX_L.get(s))
                                            + " k=" + (EX_K.get(s)!=null) + " j=" + (EX_J.get(s)!=null));
                                    if (isExpert && new File(EXPERT_UNLOCK_FILE).exists()) applyExpert(s);
                                }
                            }
                        } catch (Throwable t) { log("[GATE] err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            log("installed expert upload gate on y91.a x" + n);
        } catch (Throwable t) { log("installExpertUploadGate failed: " + t); }
    }

    // 每个 sf5(模型配置)构造后回调：俘获可用模板 + 给 expert 回填 + 事后 back-fill
    private static void onSf5Built(Object o) throws Exception {
        Object j = EX_J.get(o), k = EX_K.get(o), l = EX_L.get(o);
        if (j != null && tplThink == null) tplThink = j;
        if (k != null && tplSearch == null) tplSearch = k;
        if (l != null && gf5Count(l) > 0 && l != tplFile) {
            tplFile = l;   // c>0 才是真能上传的配置
            log("expert tplFile captured model=" + EX_A.get(o) + " " + gf5Info(l));
        }
        boolean isExpert = "expert".equals(EX_A.get(o));
        if (isExpert && new File(EXPERT_UNLOCK_FILE).exists()) {
            synchronized (expertInsts) {
                boolean has = false;
                for (Object e : expertInsts) if (e == o) { has = true; break; }
                if (!has) expertInsts.add(o);
            }
            applyExpert(o);
        }
        backfillExperts();  // 模板可能晚于 expert 才构造出来，事后统一回填
    }

    private static void applyExpert(Object o) throws Exception {
        EX_F.set(o, Boolean.TRUE);
        EX_G.set(o, Boolean.TRUE);
        if (EX_J.get(o) == null && tplThink != null) EX_J.set(o, tplThink);
        if (EX_K.get(o) == null && tplSearch != null) EX_K.set(o, tplSearch);
        Object curL = EX_L.get(o);
        if (tplFile != null && (curL == null || gf5Count(curL) <= 0)) EX_L.set(o, tplFile);
        log("expert applied @" + Integer.toHexString(System.identityHashCode(o))
                + " (j=" + (EX_J.get(o)!=null) + " k=" + (EX_K.get(o)!=null)
                + " file=" + gf5Info(EX_L.get(o)) + ")");
    }

    private static void backfillExperts() {
        if (tplFile == null && tplThink == null && tplSearch == null) return;
        synchronized (expertInsts) {
            for (Object o : expertInsts) {
                try {
                    if (EX_L.get(o) == null || gf5Count(EX_L.get(o)) <= 0
                            || EX_J.get(o) == null || EX_K.get(o) == null) applyExpert(o);
                } catch (Throwable ignored) {}
            }
        }
    }

    // 读 gf5.c(最大文件数)；读不到返回 -1，null 返回 0
    private static int gf5Count(Object gf5) {
        if (gf5 == null) return 0;
        if (GF5_C == null) return -1;
        try { Object v = GF5_C.get(gf5); return (v instanceof Integer) ? (Integer) v : -1; }
        catch (Throwable t) { return -1; }
    }

    private static String gf5Info(Object gf5) {
        if (gf5 == null) return "null";
        return "{c=" + gf5Count(gf5) + " cls=" + gf5.getClass().getName() + "}";
    }

    // ── 阻止内容安全审查擦除（clear_response 拦截）─────────────────
    private void hookSafetyRetraction(ClassLoader cl) {
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
                hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            List<Object> a = chain.getArgs();
                            if (isSrvLog()) {
                                StringBuilder sb = new StringBuilder("kb7(hint)");
                                for (int i = 0; i < a.size(); i++) {
                                    sb.append(" arg").append(i).append('=').append(a.get(i));
                                }
                                srvLog(sb.toString());
                            }
                            if (isNoCensor()) {
                                Object cur = a.get(idx);
                                if (Boolean.TRUE.equals(cur)) {
                                    Object[] args = a.toArray();
                                    args[idx] = Boolean.FALSE;
                                    log("blocked clear_response (kb7.arg" + idx + ")");
                                    return chain.proceed(args);
                                }
                            }
                        } catch (Throwable t) { log("clear_response block err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            log("hooked kb7 constructors x" + n + " (clear_response guard)");
        } catch (Throwable t) { log("hookSafetyRetraction failed: " + t); }
    }

    // ── 诊断：抓取服务器返回的 SSE 原始事件 ─────────────────────────
    private void installServerCapture(ClassLoader cl) {
        try {
            Class<?> k = HostCompat.load(cl, "lv7");
            int n = 0;
            for (Constructor<?> ctor : k.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 2 || pts[0] != String.class || pts[1] != String.class) continue;
                hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object r = chain.proceed();
                        try {
                            if (isSrvLog()) {
                                String evt = String.valueOf(chain.getArg(0));
                                Object d = chain.getArg(1);
                                String data = String.valueOf(d);
                                if (data != null && data.length() > 4000) {
                                    data = data.substring(0, 4000) + "...<truncated len=" + String.valueOf(d).length() + ">";
                                }
                                srvLog("evt=" + evt + "  data=" + data);
                            }
                        } catch (Throwable t) { srvLog("lv7 capture err: " + t); }
                        return r;
                    }
                });
                n++;
            }
            log("installed server capture on lv7 x" + n);
        } catch (Throwable t) { log("installServerCapture failed: " + t); }
    }

    // ── 真正的替换拦截：mv.i() JSON-patch 应用点 ────────────────────
    private void hookContentFilterApply(ClassLoader cl) {
        try {
            Class<?> k = HostCompat.load(cl, "mv");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals("i")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 4 || pts[0] != String.class) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object a0 = chain.getArg(0);
                            String path = a0 instanceof String ? (String) a0 : "";
                            String val = String.valueOf(chain.getArg(1));
                            boolean isFilter =
                                    (path.equals("fragments") && val.contains("TEMPLATE_RESPONSE"))
                                 || ((path.equals("status") || path.equals("quasi_status"))
                                        && val.contains("CONTENT_FILTER"));
                            if (isSrvLog() && (isFilter || path.equals("fragments")
                                    || path.equals("status") || path.equals("quasi_status"))) {
                                String v = val.length() > 300 ? val.substring(0, 300) + "..." : val;
                                srvLog("[CF] mv.i path=" + path + " filter=" + isFilter
                                        + " nocensor=" + isNoCensor() + " val=" + v);
                            }
                            if (isFilter) {
                                if (isSrvLog() && path.equals("fragments")) {
                                    srvLog("[CF] this.m.a@skip " + dumpMv(chain.getThisObject()));
                                    srvLog(dumpStack());
                                }
                                if (isNoCensor()) {
                                    markFilteredOriginal(cl, chain.getThisObject(),
                                            "mv.i/" + path);
                                    log("skipped CONTENT_FILTER patch mv.i(" + path + ")");
                                    if (isSrvLog()) srvLog("[CF] skipped mv.i(" + path + ")");
                                    return null; // 跳过原 void 方法
                                }
                            }
                        } catch (Throwable t) { log("content-filter block err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            log("hooked mv.i x" + n + " (content-filter guard)");
        } catch (Throwable t) { log("hookContentFilterApply failed: " + t); }
    }

    // 诊断：dump 当前线程调用栈
    private static String dumpStack() {
        StringBuilder sb = new StringBuilder("[CF] stack:");
        int n = 0;
        for (StackTraceElement e : Thread.currentThread().getStackTrace()) {
            String cn = e.getClassName();
            if (cn.startsWith("de.robv") || cn.startsWith("java.lang.reflect")
                    || cn.startsWith("io.github.libxposed") || cn.startsWith("LSPHooker")
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
    private void installMsgRebuildCapture(ClassLoader cl) {
        try {
            Class<?> k = HostCompat.load(cl, "vv7");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals("e") || m.getParameterTypes().length != 1) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object r = chain.proceed();
                        try {
                            if (isSrvLog() && r != null) srvLog("[VV7] new mv " + dumpMv(r));
                        } catch (Throwable t) { srvLog("[VV7] err " + t); }
                        return r;
                    }
                });
                n++;
            }
            log("installed msg-rebuild capture on vv7.e x" + n);
        } catch (Throwable t) { log("installMsgRebuildCapture failed: " + t); }
    }

    // 第二拦截点：mv.S(status)/mv.R(quasi_status) 直接状态写入
    private void hookStatusWrite(ClassLoader cl) {
        try {
            Class<?> k = HostCompat.load(cl, "mv");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                final String mn = m.getName();
                if (!mn.equals(HostCompat.messageMethod("S"))
                        && !mn.equals(HostCompat.messageMethod("R"))) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 1 || pts[0] != String.class) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object a0 = chain.getArg(0);
                            String v = a0 instanceof String ? (String) a0 : "";
                            boolean cf = v.contains("CONTENT_FILTER");
                            if (isSrvLog()) srvLog("[SR] mv." + mn + "(" + v + ") nocensor=" + isNoCensor());
                            if (cf && isNoCensor()) {
                                markFilteredOriginal(cl, chain.getThisObject(), "mv." + mn);
                                log("blocked mv." + mn + "(" + v + ")");
                                if (isSrvLog()) srvLog("[SR] blocked mv." + mn);
                                return null;
                            }
                        } catch (Throwable t) { log("status-write block err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            log("hooked mv.S/R x" + n + " (status-write guard)");
        } catch (Throwable t) { log("hookStatusWrite failed: " + t); }
    }

    // 诊断：hook h83.h(l84) fragment 反序列化选择器
    private void hookTemplateProbe(ClassLoader cl) {
        try {
            Class<?> k = HostCompat.load(cl, "h83");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals("h")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 1) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            if (isSrvLog()) {
                                String v = String.valueOf(chain.getArg(0));
                                if (v.contains("TEMPLATE_RESPONSE")) {
                                    srvLog("[TPL] h83.h TEMPLATE_RESPONSE seen");
                                    srvLog(dumpStack());
                                }
                            }
                        } catch (Throwable t) { srvLog("[TPL] err " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            log("hooked h83.h x" + n + " (template probe)");
        } catch (Throwable t) { log("hookTemplateProbe failed: " + t); }
    }

    // ── close 后整表合并 tp.u(tp, List) ──────────────────
    private void hookFinalMessageMerge(ClassLoader cl) {
        try {
            final Class<?> tpk = HostCompat.load(cl, "tp");
            final Field fField = tpk.getDeclaredField("f");
            fField.setAccessible(true);
            int n = 0;
            for (Method m : tpk.getDeclaredMethods()) {
                if (!m.getName().equals("u")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 2 || !List.class.isAssignableFrom(pts[1])) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object tp = chain.getArg(0);
                            Object rawList = chain.getArg(1);
                            if (tp != null && rawList instanceof List) {
                                List<?> list = (List<?>) rawList;
                                String sid = String.valueOf(readHostField(tp, "a"));
                                Map<?, ?> fmap = null;
                                try { fmap = (Map<?, ?>) fField.get(tp); } catch (Throwable ignored) {}
                                boolean nc = isNoCensor();
                                ArrayList<Object> copy = new ArrayList<>(list);
                                boolean changed = false;
                                for (int i = 0; i < copy.size(); i++) {
                                    Object msg = copy.get(i);
                                    if (msg == null) continue;
                                    preservePendingFilteredOriginal(cl, tp, msg);
                                    String status = callStr(msg, "D");
                                    String quasi = callStr(msg, "x");
                                    boolean cf = ResponsePreserver.isFilteredHostMessage(msg);
                                    Integer id = callInt(msg, "u");
                                    if (isSrvLog()) {
                                        srvLog("[FM] merge idx=" + i + " id=" + id
                                                + " status=" + status + " quasi=" + quasi + " cf=" + cf);
                                    }
                                    Object existing = id != null && fmap != null ? fmap.get(id) : null;
                                    if (existing != null && existing != msg) {
                                        preservePendingFilteredOriginal(cl, tp, existing);
                                    }
                                    Object durable = nc
                                            ? ResponsePreserver.restoreHostMessage(cl, sid, msg) : null;
                                    if (durable != null) {
                                        copy.set(i, durable);
                                        changed = true;
                                        log("restored preserved response sid=" + sid + " msg=" + id
                                                + " before final merge");
                                        if (isSrvLog()) srvLog("[FM] restored durable id=" + id);
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
                                    log("kept original msg id=" + id + " over CONTENT_FILTER");
                                    if (isSrvLog()) srvLog("[FM] kept original id=" + id
                                            + " origStatus=" + exStatus);
                                }
                                if (changed) {
                                    Object[] args = chain.getArgs().toArray();
                                    args[1] = copy;
                                    return chain.proceed(args);
                                }
                            }
                        } catch (Throwable t) { log("final-merge guard err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            log("hooked tp.u x" + n + " (final-merge guard)");
        } catch (Throwable t) { log("hookFinalMessageMerge failed: " + t); }
    }

    // ── 单条消息替换拦截：tp.q(uo)/tp.p(uo,String)/tp.a(uo,bool) ─────────
    private void hookFinalMessageApply(ClassLoader cl) {
        try {
            final Class<?> tpk = HostCompat.load(cl, "tp");
            final Field fField = tpk.getDeclaredField("f");
            fField.setAccessible(true);
            final Class<?> uok = HostCompat.load(cl, "uo");
            int n = 0;
            for (Method m : tpk.getDeclaredMethods()) {
                final String mn = m.getName();
                if (!mn.equals("q") && !mn.equals("p") && !mn.equals("a")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length < 1 || !uok.isAssignableFrom(pts[0])) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object tp = chain.getThisObject();
                            Object msg = chain.getArg(0);
                            if (tp != null && msg != null) {
                                String sid = String.valueOf(readHostField(tp, "a"));
                                preservePendingFilteredOriginal(cl, tp, msg);
                                String status = callStr(msg, "D");
                                String quasi = callStr(msg, "x");
                                boolean cf = ResponsePreserver.isFilteredHostMessage(msg);
                                Integer id = callInt(msg, "u");
                                if (isSrvLog())
                                    srvLog("[FA] tp." + mn + " id=" + id + " status=" + status
                                            + " quasi=" + quasi + " cf=" + cf);
                                if (cf && isNoCensor() && id != null) {
                                    Map<?, ?> fmap = (Map<?, ?>) fField.get(tp);
                                    Object existing = fmap != null ? fmap.get(id) : null;
                                    if (existing != null && existing != msg) {
                                        preservePendingFilteredOriginal(cl, tp, existing);
                                    }
                                    Object durable = ResponsePreserver.restoreHostMessage(cl, sid, msg);
                                    if (durable != null) {
                                        Object[] args = chain.getArgs().toArray();
                                        args[0] = durable;
                                        log("restored preserved response sid=" + sid + " msg=" + id
                                                + " in tp." + mn);
                                        if (isSrvLog()) srvLog("[FA] restored durable id=" + id);
                                        return chain.proceed(args);
                                    }
                                    if (existing != null && existing != msg) {
                                        String exS = callStr(existing, "D");
                                        String exQ = callStr(existing, "x");
                                        boolean exCf = ResponsePreserver.isFilteredHostMessage(existing);
                                        if (!exCf) {
                                            Object[] args = chain.getArgs().toArray();
                                            args[0] = existing;
                                            log("tp." + mn + " kept original id=" + id + " over CONTENT_FILTER");
                                            if (isSrvLog()) srvLog("[FA] kept original id=" + id + " origStatus=" + exS);
                                            return chain.proceed(args);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) { log("final-apply guard err: " + t); }
                        return chain.proceed();
                    }
                });
                n++;
            }
            log("hooked tp.q/p/a x" + n + " (final-apply guard)");
        } catch (Throwable t) { log("hookFinalMessageApply failed: " + t); }
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
        String sid = findNativeSessionContainingMessage(message);
        if (sid != null && ResponsePreserver.saveHostMessage(cl, sid, message)) {
            log("preserved original response sid=" + sid + " msg=" + callInt(message, "u")
                    + " after " + source);
        }
    }

    private static void preservePendingFilteredOriginal(ClassLoader cl, Object session,
                                                         Object message) {
        if (session == null || message == null
                || !FILTERED_ORIGINAL_MESSAGES.containsKey(message)) return;
        String sid = String.valueOf(readHostField(session, "a"));
        if (ResponsePreserver.saveHostMessage(cl, sid, message)) {
            FILTERED_ORIGINAL_MESSAGES.remove(message);
            log("finalized preserved response sid=" + sid + " msg=" + callInt(message, "u"));
        }
    }

    private static String findNativeSessionContainingMessage(Object message) {
        Object sessions = NATIVE_SESSION_LIST;
        if (sessions instanceof List) {
            try {
                for (Object session : new ArrayList<Object>((List) sessions)) {
                    if (nativeSessionContainsMessage(session, message)) {
                        return String.valueOf(readHostField(session, "a"));
                    }
                }
            } catch (Throwable ignored) {}
        }
        synchronized (LOCAL_NATIVE_SESSIONS) {
            try {
                for (Map.Entry<String, Object> entry : LOCAL_NATIVE_SESSIONS.entrySet()) {
                    if (nativeSessionContainsMessage(entry.getValue(), message)) {
                        return entry.getKey();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean nativeSessionContainsMessage(Object session, Object message) {
        Object messages = readHostField(session, "f");
        if (!(messages instanceof Map)) return false;
        try {
            for (Object candidate : new ArrayList<Object>(((Map) messages).values())) {
                if (candidate == message) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private String readPrompt() {
        try {
            File ef = new File(ENABLED_FILE);
            if (!ef.exists()) return null;

            String linked = readSmallText(PROMPT_LINK_FILE);
            if (linked != null && linked.length() > 0) return linked;

            String copied = readSmallText(PROMPT_FILE);
            if (copied != null && copied.length() > 0) return copied;
        } catch (Throwable t) { return null; }
        return null;
    }

    // ── 设置页入口生命周期 ─────────────────────────────────────────

    private void installImageCredentialBridge(final ClassLoader cl) {
        int installed = 0;
        try {
            Class<?> apiClass = HostCompat.load(cl, "pv0");
            for (Constructor<?> ctor : apiClass.getDeclaredConstructors()) {
                hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        IMAGE_FILE_API = chain.getThisObject();
                        IMAGE_HOST_CL = cl;
                        return result;
                    }
                });
                installed++;
            }
        } catch (Throwable t) { log("capture pv0 failed: " + t); }

        // 兜底：即使 pv0 比模块安装钩子更早构造，也能从之后创建的 k31.c.d 取回同一实例。
        try {
            Class<?> composerClass = HostCompat.load(cl, "k31");
            for (Constructor<?> ctor : composerClass.getDeclaredConstructors()) {
                hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try {
                            IMAGE_COMPOSER = chain.getThisObject();
                            Object repository = readHostField(chain.getThisObject(), "c");
                            Object api = readHostField(repository, "d");
                            if (api != null) {
                                IMAGE_FILE_API = api;
                                IMAGE_HOST_CL = cl;
                            }
                        } catch (Throwable ignored) {}
                        return result;
                    }
                });
                installed++;
            }
        } catch (Throwable t) { log("capture k31 file api failed: " + t); }
        log("installed image credential bridge constructors=" + installed);
    }

    static void pickGalleryImage(Activity act, GalleryPickCallback callback) {
        if (act == null || callback == null) return;
        galleryPickCallback = callback;
        try {
            Intent intent;
            if (Build.VERSION.SDK_INT >= 33) {
                intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
                intent.setType("image/*");
            } else {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            }
            act.startActivityForResult(intent, PICK_IMAGE_REQUEST);
        } catch (Throwable t) {
            galleryPickCallback = null;
            log("open gallery picker failed: " + t);
            callback.onPicked(null);
        }
    }

    /** Persists a newly selected gallery image for stable local-history rendering. */
    static JSONObject uploadGalleryImage(Activity act, final Uri uri, final String model) {
        if (act == null || uri == null) return null;
        Cursor cursor = null;
        String name = null;
        long size = -1L;
        try {
            cursor = act.getContentResolver().query(uri,
                    new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE},
                    null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int sizeCol = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (nameCol >= 0 && !cursor.isNull(nameCol)) name = cursor.getString(nameCol);
                if (sizeCol >= 0 && !cursor.isNull(sizeCol)) size = cursor.getLong(sizeCol);
            }
        } catch (Throwable ignored) {
        } finally { if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {} }
        if (size < 0) {
            try {
                android.content.res.AssetFileDescriptor descriptor =
                        act.getContentResolver().openAssetFileDescriptor(uri, "r");
                if (descriptor != null) {
                    size = descriptor.getLength();
                    descriptor.close();
                }
            } catch (Throwable ignored) {}
        }
        if (name == null || name.trim().length() == 0) name = "gallery_image.jpg";
        if (size < 0) size = 0L;
        final String uploadName = name;
        final long uploadSize = size;
        final JSONObject durable = persistGalleryImage(act, uri, uploadName, uploadSize);
        if (durable == null) {
            log("gallery persistence failed name=" + uploadName);
            return null;
        }
        log("gallery stored durably name=" + uploadName
                + " id=" + durable.optString("id", "")
                + " path=" + durable.optString("signed_path", ""));
        return durable;
    }

    /**
     * Keeps a master copy under files/ and a FileProvider-visible mirror under cache/captured/.
     * The cache mirror is restored on every process start, so Android cache eviction cannot turn
     * an edited historical message into a broken image after DeepSeek is reopened.
     */
    private static JSONObject persistGalleryImage(Activity act, Uri uri, String displayName,
                                                  long reportedSize) {
        File master = null;
        try {
            File masterDir = new File(EDITOR_IMAGE_MASTER_DIR);
            File cacheDir = new File(EDITOR_IMAGE_CACHE_DIR);
            if ((!masterDir.exists() && !masterDir.mkdirs())
                    || (!cacheDir.exists() && !cacheDir.mkdirs())) return null;
            String extension = galleryExtension(act, uri, displayName);
            String storedName = "deekseep_editor_"
                    + java.util.UUID.randomUUID().toString().replace("-", "") + extension;
            master = new File(masterDir, storedName);
            if (!copyUriToFile(act, uri, master) || master.length() <= 0) return null;
            File mirror = new File(cacheDir, storedName);
            if (!copyFile(master, mirror)) return null;

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(master.getPath(), bounds);
            double now = System.currentTimeMillis() / 1000.0d;
            JSONObject out = new JSONObject();
            out.put("id", "deekseep-local-" + java.util.UUID.randomUUID());
            out.put("status", "SUCCESS");
            out.put("file_name", displayName == null || displayName.trim().length() == 0
                    ? storedName : displayName);
            out.put("file_size", master.length() > 0 ? master.length() : reportedSize);
            out.put("inserted_at", now);
            out.put("updated_at", now);
            out.put("token_usage", JSONObject.NULL);
            out.put("previewable", true);
            out.put("from_share", false);
            out.put("signed_path", EDITOR_IMAGE_URI_PREFIX + Uri.encode(storedName));
            out.put("is_image", true);
            out.put("audit_result", "pass");
            out.put("width", bounds.outWidth > 0 ? Integer.valueOf(bounds.outWidth) : JSONObject.NULL);
            out.put("height", bounds.outHeight > 0 ? Integer.valueOf(bounds.outHeight) : JSONObject.NULL);
            out.put("retryable", false);
            return out;
        } catch (Throwable t) {
            log("persist gallery image failed: " + t);
            return null;
        }
    }

    private static String galleryExtension(Activity act, Uri uri, String displayName) {
        String ext = "";
        if (displayName != null) {
            int dot = displayName.lastIndexOf('.');
            if (dot >= 0 && dot + 1 < displayName.length()) {
                String candidate = displayName.substring(dot + 1).toLowerCase(Locale.US);
                if (candidate.matches("[a-z0-9]{1,5}")) ext = "." + candidate;
            }
        }
        if (ext.length() == 0) {
            String mime = null;
            try { mime = act.getContentResolver().getType(uri); } catch (Throwable ignored) {}
            if ("image/png".equals(mime)) ext = ".png";
            else if ("image/webp".equals(mime)) ext = ".webp";
            else if ("image/gif".equals(mime)) ext = ".gif";
            else ext = ".jpg";
        }
        return ext;
    }

    private static boolean copyUriToFile(Activity act, Uri uri, File target) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = act.getContentResolver().openInputStream(uri);
            if (in == null) return false;
            out = new FileOutputStream(target, false);
            byte[] buffer = new byte[32768];
            int count;
            while ((count = in.read(buffer)) >= 0) {
                if (count > 0) out.write(buffer, 0, count);
            }
            out.flush();
            return true;
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
            if (out != null) try { out.close(); } catch (Throwable ignored) {}
        }
    }

    private static boolean copyFile(File source, File target) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(source);
            out = new FileOutputStream(target, false);
            byte[] buffer = new byte[32768];
            int count;
            while ((count = in.read(buffer)) >= 0) {
                if (count > 0) out.write(buffer, 0, count);
            }
            out.flush();
            target.setLastModified(source.lastModified());
            return target.length() == source.length();
        } catch (Throwable ignored) {
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
            if (out != null) try { out.close(); } catch (Throwable ignored) {}
        }
    }

    static void restoreLocalEditorImages() {
        int restored = 0;
        try {
            File masterDir = new File(EDITOR_IMAGE_MASTER_DIR);
            File cacheDir = new File(EDITOR_IMAGE_CACHE_DIR);
            File[] files = masterDir.listFiles();
            if (files == null || (!cacheDir.exists() && !cacheDir.mkdirs())) return;
            for (File master : files) {
                if (master == null || !master.isFile()
                        || !master.getName().startsWith("deekseep_editor_")) continue;
                File mirror = new File(cacheDir, master.getName());
                if ((!mirror.isFile() || mirror.length() != master.length())
                        && copyFile(master, mirror)) restored++;
            }
        } catch (Throwable t) {
            log("restore local editor images failed: " + t);
        }
        if (restored > 0) log("restored local editor image mirrors=" + restored);
    }

    private static JSONObject ensureLocalEditorImage(JSONObject file) {
        if (file == null) return null;
        String path = file.optString("signed_path", "");
        if (!path.startsWith(EDITOR_IMAGE_URI_PREFIX)) return null;
        try {
            String name = Uri.parse(path).getLastPathSegment();
            if (name == null || !name.startsWith("deekseep_editor_")
                    || name.contains("/") || name.contains("\\")) return null;
            File master = new File(EDITOR_IMAGE_MASTER_DIR, name);
            File mirror = new File(EDITOR_IMAGE_CACHE_DIR, name);
            if (!mirror.isFile() || mirror.length() <= 0) {
                if (!master.isFile() || master.length() <= 0 || !copyFile(master, mirror)) return null;
            }
            return new JSONObject(file.toString());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object readStaticHostField(Class<?> cls, String name) {
        try {
            Field field = cls.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (Throwable ignored) { return null; }
    }

    /** Mirrors k31.s(): upload behavior must follow the host's current R1 switch. */
    private static boolean readGalleryThinkingEnabled() {
        try {
            Object composer = IMAGE_COMPOSER;
            Object settings = readHostField(composer, "a");
            Method method = settings.getClass().getDeclaredMethod("c");
            method.setAccessible(true);
            return Boolean.TRUE.equals(method.invoke(settings));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object prepareGallerySource(final ClassLoader cl, final Object api,
                                               final Object source, Class<?> sourceClass)
            throws Throwable {
        final Object composer = IMAGE_COMPOSER;
        if (composer == null) return null;
        Method found = null;
        for (Method method : composer.getClass().getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if ("o".equals(method.getName()) && p.length == 2
                    && p[0].getName().equals(sourceClass.getName())) {
                found = method; break;
            }
        }
        if (found == null) return null;
        found.setAccessible(true);
        final Method preprocess = found;
        Class<?> blockClass = HostCompat.load(cl, "mb3");
        Object block = Proxy.newProxyInstance(cl, new Class<?>[]{blockClass},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (isObjectMethod(method)) return objectMethod(proxy, method, args);
                        Object continuation = args == null || args.length == 0
                                ? null : args[args.length - 1];
                        try {
                            return preprocess.invoke(composer, source, continuation);
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            throw e.getCause() == null ? e : e.getCause();
                        }
                    }
                });
        Object ready = runHostCoroutine(cl, api, block);
        if (ready == null || !source.getClass().isInstance(ready)) return null;
        log("gallery preprocessed name=" + readHostField(ready, "a")
                + " size=" + readHostField(ready, "c") + " uri=" + readHostField(ready, "b"));
        return ready;
    }

    /**
     * Must run off the Android main thread. Returns a freshly signed host fp JSON, or null while
     * leaving the caller's database untouched. If source and target models are equal, use a
     * supported intermediate model because DeepSeek normally only forks when switching models.
     */
    static JSONObject refreshUploadedImageCredential(JSONObject oldFile,
                                                       String sourceModel,
                                                       String targetModel) {
        if (oldFile == null) return null;
        JSONObject local = ensureLocalEditorImage(oldFile);
        if (local != null) return local;
        String fileId = oldFile.optString("id", "").trim();
        if (fileId.length() == 0) return null;
        Object api = IMAGE_FILE_API;
        ClassLoader cl = IMAGE_HOST_CL;
        if (api == null || cl == null) {
            log("image credential refresh unavailable: host pv0 not captured");
            return null;
        }
        String from = sourceModel == null || sourceModel.trim().length() == 0
                ? "default" : sourceModel.trim();
        String to = targetModel == null || targetModel.trim().length() == 0
                ? "default" : targetModel.trim();
        try {
            Object fresh;
            if (from.equals(to)) {
                String intermediate = "vision".equals(to) ? "default" : "vision";
                Object midway = forkUploadedImageOnce(cl, api, fileId, from, intermediate);
                if (midway == null) return null;
                String midwayId = String.valueOf(readHostField(midway, "a"));
                if (midwayId.length() == 0 || "null".equals(midwayId)) return null;
                fresh = forkUploadedImageOnce(cl, api, midwayId, intermediate, to);
            } else {
                fresh = forkUploadedImageOnce(cl, api, fileId, from, to);
            }
            if (fresh == null) return null;
            fresh = waitForUploadedImageReady(cl, api, fresh);
            if (fresh == null) return null;
            JSONObject json = hostFileToJson(fresh);
            log("image credential refreshed from=" + from + " to=" + to
                    + " old=" + fileId + " new=" + json.optString("id", ""));
            return json;
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException
                    && ((java.lang.reflect.InvocationTargetException) t).getCause() != null
                    ? ((java.lang.reflect.InvocationTargetException) t).getCause() : t;
            log("image credential refresh failed: " + cause);
            return null;
        }
    }

    private static Object forkUploadedImageOnce(ClassLoader cl, Object api, String fileId,
                                                 String fromModel, String toModel) throws Throwable {
        Class<?> coroutine = HostCompat.load(cl, "a60");
        Constructor<?> forkCtor = null;
        for (Constructor<?> ctor : coroutine.getDeclaredConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length == 6 && p[1] == String.class && p[2] == String.class
                    && p[3] == String.class && p[5] == int.class) {
                forkCtor = ctor;
                break;
            }
        }
        if (forkCtor == null) throw new NoSuchMethodException("a60 fork constructor");
        forkCtor.setAccessible(true);
        Object task = forkCtor.newInstance(api, fileId, fromModel, toModel, null, 2);
        Object result = runHostCoroutine(cl, api, task);
        if (!HostCompat.simpleNameIs(result, "kp5")) {
            log("fork_file_task rejected " + fromModel + "->" + toModel
                    + " result=" + logValue(result));
            return null;
        }
        Object fp = readHostField(result, "b");
        if (!HostCompat.simpleNameIs(fp, "fp")) {
            log("fork_file_task success wrapper had no fp: " + logValue(result));
            return null;
        }
        return fp;
    }

    private static Object waitForUploadedImageReady(ClassLoader cl, Object api, Object initial)
            throws Throwable {
        Object current = initial;
        int transientErrors = 0;
        long deadline = System.currentTimeMillis() + 50000L;
        for (int attempt = 0; attempt < 60 && System.currentTimeMillis() < deadline; attempt++) {
            String status = hostEnumName(readHostField(current, "b"));
            Object signed = readHostField(current, "j");
            Object audit = readHostField(current, "l");
            if ("SUCCESS".equals(status) && signed instanceof String
                    && ((String) signed).trim().length() > 0
                    && "pass".equals(String.valueOf(audit))) {
                return current;
            }
            if (!"PENDING".equals(status) && !"PARSING".equals(status)
                    && !"SUCCESS".equals(status)) {
                log("fetch_files stopped at status=" + status
                        + " file=" + readHostField(current, "a"));
                return null;
            }
            String id = String.valueOf(readHostField(current, "a"));
            if (id.length() == 0 || "null".equals(id)) return null;
            Thread.sleep(attempt == 0 ? 1000L : 700L);
            Object updated = fetchUploadedImageOnce(cl, api, id);
            if (updated == null) {
                if (++transientErrors >= 30) return null;
                continue;
            }
            transientErrors = 0;
            current = updated;
        }
        log("fetch_files timed out file=" + readHostField(current, "a")
                + " status=" + hostEnumName(readHostField(current, "b")));
        return null;
    }

    private static Object fetchUploadedImageOnce(ClassLoader cl, Object api, String fileId)
            throws Throwable {
        Constructor<?> fetchCtor = null;
        for (Constructor<?> ctor : HostCompat.load(cl, "u40").getDeclaredConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length == 4 && p[0] == Object.class && p[1] == Object.class
                    && p[3] == int.class) {
                fetchCtor = ctor;
                break;
            }
        }
        if (fetchCtor == null) throw new NoSuchMethodException("u40 fetch constructor");
        fetchCtor.setAccessible(true);
        Object task = fetchCtor.newInstance(api, Collections.singleton(fileId), null, 1);
        Object result = runHostCoroutine(cl, api, task);
        if (!HostCompat.simpleNameIs(result, "kp5")) {
            log("fetch_files rejected file=" + fileId + " result=" + deepDump(result, 4));
            return null;
        }
        Object wrapper = readHostField(result, "b");
        Object files = readHostField(wrapper, "a");
        if (!(files instanceof List)) return null;
        for (Object fp : (List) files) {
            if (fileId.equals(String.valueOf(readHostField(fp, "a")))) return fp;
        }
        log("fetch_files omitted file=" + fileId);
        return null;
    }

    private static Object runHostCoroutine(ClassLoader cl, Object api, Object task)
            throws Throwable {
        Object context = readHostField(api, "a");
        if (context == null) throw new IllegalStateException("pv0 dispatcher missing");
        Method runBlocking = null;
        for (Method method : HostCompat.load(cl, "u82").getDeclaredMethods()) {
            if (HostCompat.method("u82", "K").equals(method.getName())
                    && method.getParameterTypes().length == 2
                    && java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
                runBlocking = method;
                break;
            }
        }
        if (runBlocking == null) throw new NoSuchMethodException("u82.K");
        runBlocking.setAccessible(true);
        return runBlocking.invoke(null, context, task);
    }

    private static String hostEnumName(Object value) {
        if (value instanceof Enum) return ((Enum) value).name();
        return value == null ? "" : String.valueOf(value);
    }

    private static JSONObject hostFileToJson(Object fp) throws Throwable {
        JSONObject out = new JSONObject();
        Object status = readHostField(fp, "b");
        if (status instanceof Enum) status = ((Enum) status).name();
        else if (status != null) status = String.valueOf(status);
        putJson(out, "id", readHostField(fp, "a"));
        putJson(out, "status", status);
        putJson(out, "file_name", readHostField(fp, "c"));
        putJson(out, "file_size", readHostField(fp, "d"));
        putJson(out, "inserted_at", readHostField(fp, "e"));
        putJson(out, "updated_at", readHostField(fp, "f"));
        putJson(out, "token_usage", readHostField(fp, "g"));
        putJson(out, "previewable", readHostField(fp, "h"));
        putJson(out, "from_share", readHostField(fp, "i"));
        putJson(out, "signed_path", readHostField(fp, "j"));
        putJson(out, "is_image", readHostField(fp, "k"));
        putJson(out, "audit_result", readHostField(fp, "l"));
        putJson(out, "width", readHostField(fp, "m"));
        putJson(out, "height", readHostField(fp, "n"));
        putJson(out, "retryable", readHostField(fp, "o"));
        Object signedPath = out.opt("signed_path");
        if (!"SUCCESS".equals(out.optString("status", ""))
                || out.optString("id", "").length() == 0
                || !(signedPath instanceof String)
                || ((String) signedPath).trim().length() == 0) {
            throw new IllegalStateException("fresh fp missing id/signed_path");
        }
        return out;
    }

    private static void putJson(JSONObject object, String key, Object value) throws Throwable {
        object.put(key, value == null ? JSONObject.NULL : value);
    }

    private static HashSet<String> localOnlySessionIds(ClassLoader cl) {
        long now = System.currentTimeMillis();
        HashSet<String> cached = LOCAL_SESSION_IDS;
        if (now - LOCAL_SESSION_IDS_AT < 1200L) return new HashSet<>(cached);
        HashSet<String> found = new HashSet<>();
        try {
            File file = ChatEditorUi.currentDb(cl);
            found = ChatEditorUi.localSessionIdsFromBackups(file);
        } catch (Throwable t) {
            log("read local-only sidecars failed: " + t);
        }
        LOCAL_SESSION_IDS = found;
        LOCAL_SESSION_IDS_AT = now;
        return new HashSet<>(found);
    }

    /** Makes a freshly committed editor conversation visible to runtime guards immediately. */
    static synchronized void registerEditorLocalSession(String sid, Integer currentHead) {
        if (sid == null || sid.length() == 0) return;
        RECENTLY_DELETED_SESSION_IDS.remove(sid);
        HashSet<String> next = ChatEditorUi.localSessionIdsFromAllBackups();
        next.addAll(LOCAL_SESSION_IDS);
        next.add(sid);
        LOCAL_SESSION_IDS = next;
        LOCAL_SESSION_IDS_AT = System.currentTimeMillis();
        if (currentHead != null && currentHead.intValue() > 0) {
            FROZEN_SESSION_HEADS.put(sid, currentHead);
        }
    }

    static synchronized void unregisterEditorLocalSession(String sid) {
        if (sid == null || sid.length() == 0) return;
        HashSet<String> next = ChatEditorUi.localSessionIdsFromAllBackups();
        next.addAll(LOCAL_SESSION_IDS);
        next.remove(sid);
        LOCAL_SESSION_IDS = next;
        LOCAL_SESSION_IDS_AT = System.currentTimeMillis();
        FROZEN_SESSION_HEADS.remove(sid);
        synchronized (LOCAL_NATIVE_SESSIONS) {
            LOCAL_NATIVE_SESSIONS.remove(sid);
        }
    }

    /**
     * DeepSeek's p68 cloud-directory transaction asks aw.a() for every local session, then drops
     * tables whose ids are absent from the server response. Hide only editor-owned sidecar ids from
     * that one comparison. Incoming server rows and ordinary server-side deletions stay untouched.
     */
    private void hookLocalSessionDirectoryMerge(final ClassLoader cl) {
        try {
            Class<?> transaction = HostCompat.load(cl, "p68");
            Class<?> directoryDao = HostCompat.load(cl, "aw");
            int transactionHooks = 0;
            int directoryHooks = 0;
            for (Method method : transaction.getDeclaredMethods()) {
                if (!HostCompat.method("p68", "a").equals(method.getName())
                        || method.getParameterTypes().length != 0
                        || method.getReturnType() != void.class) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        int preservedHeads = preserveFrozenDirectoryHeads(chain.getThisObject());
                        if (preservedHeads > 0) {
                            long now = System.currentTimeMillis();
                            if (now - LOCAL_DIRECTORY_HEAD_LOG_AT > 5000L) {
                                LOCAL_DIRECTORY_HEAD_LOG_AT = now;
                                log("preserved frozen conversation heads during cloud sync="
                                        + preservedHeads);
                            }
                        }
                        Boolean previous = LOCAL_DIRECTORY_SYNC.get();
                        LOCAL_DIRECTORY_SYNC.set(Boolean.TRUE);
                        try {
                            return chain.proceed();
                        } finally {
                            if (previous == null) LOCAL_DIRECTORY_SYNC.remove();
                            else LOCAL_DIRECTORY_SYNC.set(previous);
                        }
                    }
                });
                transactionHooks++;
            }
            for (Method method : directoryDao.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!HostCompat.method("aw", "a").equals(method.getName())
                        || !java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        || types.length != 1 || types[0] != directoryDao
                        || !List.class.isAssignableFrom(method.getReturnType())) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        if (!Boolean.TRUE.equals(LOCAL_DIRECTORY_SYNC.get())
                                || !(result instanceof List)) return result;
                        HashSet<String> localIds = ChatEditorUi.localSessionIdsFromAllBackups();
                        if (localIds.isEmpty()) return result;
                        List rows = (List) result;
                        int removed = 0;
                        for (int i = rows.size() - 1; i >= 0; i--) {
                            Object row = rows.get(i);
                            Object value = readHostField(row, "a");
                            String sid = value == null ? null : String.valueOf(value);
                            if (sid != null && localIds.contains(sid)) {
                                rows.remove(i);
                                removed++;
                            }
                        }
                        if (removed > 0) {
                            long now = System.currentTimeMillis();
                            if (now - LOCAL_DIRECTORY_MERGE_LOG_AT > 5000L) {
                                LOCAL_DIRECTORY_MERGE_LOG_AT = now;
                                log("excluded editor-local sessions from cloud prune=" + removed);
                            }
                        }
                        return result;
                    }
                });
                directoryHooks++;
            }
            log("installed local cloud-directory merge p68=" + transactionHooks
                    + " aw=" + directoryHooks);
        } catch (Throwable t) {
            log("hookLocalSessionDirectoryMerge failed: " + t);
        }
    }

    /**
     * The delayed server refresh is applied in ed0.h.  That method mutates ed0.e, the canonical
     * SnapshotStateList observed by navigation, before p68 updates the WCDB directory.  Keeping a
     * local tp only in mc.f's render argument therefore leaves the active-chat validator looking
     * at a server-only list and the editor-created conversation disappears a few seconds after a
     * cold start.  Capture editor-owned tp objects before every coroutine leg and put only those
     * missing objects back into the same state list after the leg completes.  Server additions,
     * metadata updates, ordering, and ordinary server-side deletions remain host-owned.
     */
    private void hookLocalNativeSessionRefresh(final ClassLoader cl) {
        try {
            Class<?> repository = HostCompat.load(cl, "ed0");
            Class<?> continuation = HostCompat.load(cl, "uz1");
            int installed = 0;
            for (Method method : repository.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!HostCompat.method("ed0", "h").equals(method.getName())
                        || types.length != 1
                        || types[0] != continuation) continue;
                try { deoptimize(method); } catch (Throwable ignored) {}
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object before = readHostField(chain.getThisObject(), "e");
                        HashSet<String> localIds = localOnlySessionIds(cl);
                        if (before instanceof List
                                && HostCompat.simpleNameIs(before, "uo7")) {
                            preserveEditorLocalNativeSessions((List) before, localIds);
                        }
                        try {
                            return chain.proceed();
                        } finally {
                            Object after = readHostField(chain.getThisObject(), "e");
                            if (after instanceof List
                                    && HostCompat.simpleNameIs(after, "uo7")) {
                                int restored = preserveEditorLocalNativeSessions(
                                        (List) after, localIds);
                                if (restored > 0) {
                                    long now = System.currentTimeMillis();
                                    if (now - LOCAL_NATIVE_STATE_REPAIR_LOG_AT > 1000L) {
                                        LOCAL_NATIVE_STATE_REPAIR_LOG_AT = now;
                                        log("restored editor-local sessions into native state="
                                                + restored + " host sessions="
                                                + ((List) after).size());
                                    }
                                }
                            }
                        }
                    }
                });
                installed++;
            }
            log("installed editor-local native-state refresh guard ed0.h x" + installed);
        } catch (Throwable t) {
            log("hookLocalNativeSessionRefresh failed: " + t);
        }
    }

    /** Package-visible for the JVM regression: merge into the canonical host list, not a copy. */
    static int preserveEditorLocalNativeSessions(List state, HashSet<String> localIds) {
        if (state == null || localIds == null || localIds.isEmpty()) return 0;
        HashSet<String> seen = new HashSet<>();
        ArrayList<Object> missing = new ArrayList<>();
        synchronized (LOCAL_NATIVE_SESSIONS) {
            LOCAL_NATIVE_SESSIONS.keySet().retainAll(localIds);
            try {
                for (Object session : new ArrayList<Object>(state)) {
                    Object value = readHostField(session, "a");
                    String sid = value == null ? null : String.valueOf(value);
                    if (sid == null || sid.length() == 0 || "null".equals(sid)) continue;
                    seen.add(sid);
                    if (localIds.contains(sid) && !isSessionRecentlyDeleted(sid)) {
                        LOCAL_NATIVE_SESSIONS.put(sid, session);
                    }
                }
                for (String sid : localIds) {
                    if (seen.contains(sid) || isSessionRecentlyDeleted(sid)) continue;
                    Object session = LOCAL_NATIVE_SESSIONS.get(sid);
                    if (session != null) missing.add(session);
                }
            } catch (Throwable t) {
                log("capture editor-local native state failed: " + t);
                return 0;
            }
        }
        int restored = 0;
        for (Object session : missing) {
            String sid = String.valueOf(readHostField(session, "a"));
            if (isSessionRecentlyDeleted(sid)) continue;
            boolean alreadyPresent = false;
            try {
                for (Object current : new ArrayList<Object>(state)) {
                    if (sid.equals(String.valueOf(readHostField(current, "a")))) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent && state.add(session)) restored++;
            } catch (Throwable t) {
                log("restore editor-local native session failed sid=" + sid + ": " + t);
            }
        }
        if (restored > 0) {
            try {
                Collections.sort(state, new Comparator<Object>() {
                    @Override public int compare(Object left, Object right) {
                        boolean leftPinned = Boolean.TRUE.equals(invokeNoArg(left, "h"));
                        boolean rightPinned = Boolean.TRUE.equals(invokeNoArg(right, "h"));
                        if (leftPinned != rightPinned) return leftPinned ? -1 : 1;
                        Object leftUpdated = readHostField(left, "c");
                        Object rightUpdated = readHostField(right, "c");
                        double l = leftUpdated instanceof Number
                                ? ((Number) leftUpdated).doubleValue() : 0d;
                        double r = rightUpdated instanceof Number
                                ? ((Number) rightUpdated).doubleValue() : 0d;
                        return l == r ? 0 : (l > r ? -1 : 1);
                    }
                });
            } catch (Throwable t) {
                log("sort restored editor-local native sessions failed: " + t);
            }
        }
        NATIVE_SESSION_STATE = state;
        NATIVE_SESSION_LIST = state;
        return restored;
    }

    /**
     * p68 deliberately keeps cache_version but overwrites current_message_id from the lightweight
     * server directory.  Some directory entries omit that field.  Copy the valid local head into
     * only those null incoming entries before WCDB applies the normal title/count merge.
     */
    private static int preserveFrozenDirectoryHeads(Object transaction) {
        try {
            Object incomingValue = readHostField(transaction, "a");
            Object repository = readHostField(transaction, "b");
            Object directory = readHostField(repository, "d");
            if (!(incomingValue instanceof List) || directory == null) return 0;

            Method reader = null;
            for (Method method : directory.getClass().getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (HostCompat.method("aw", "a").equals(method.getName())
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && types.length == 1 && types[0] == directory.getClass()
                        && List.class.isAssignableFrom(method.getReturnType())) {
                    reader = method;
                    break;
                }
            }
            if (reader == null) return 0;
            reader.setAccessible(true);
            Object localValue = reader.invoke(null, directory);
            if (!(localValue instanceof List)) return 0;

            HashMap<String, Object> frozenHeads = new HashMap<>();
            for (Object local : (List) localValue) {
                Object version = readHostField(local, "d");
                Object head = readHostField(local, "h");
                Object id = readHostField(local, "a");
                if (version instanceof Number
                        && ((Number) version).intValue() == Integer.MAX_VALUE
                        && head != null && id != null) {
                    String sid = String.valueOf(id);
                    frozenHeads.put(sid, head);
                    if (head instanceof Number) {
                        FROZEN_SESSION_HEADS.put(sid, ((Number) head).intValue());
                    }
                }
            }
            if (frozenHeads.isEmpty()) return 0;

            int preserved = 0;
            for (Object incoming : (List) incomingValue) {
                Object id = readHostField(incoming, "a");
                if (id == null || readHostField(incoming, "h") != null) continue;
                Object head = frozenHeads.get(String.valueOf(id));
                if (head == null) continue;
                if (forceSetObjectField(incoming, "h", head)) preserved++;
            }
            return preserved;
        } catch (Throwable t) {
            log("preserve frozen conversation heads failed: " + t);
            return 0;
        }
    }

    /** Local-only editor conversations have no detail endpoint; their za1 constructor already
     * loads the WCDB table.  Suppress the redundant fa1 remote reload that otherwise reports the
     * session as deleted and replaces the successfully loaded local state with an empty chat. */
    private void hookLocalSessionRemoteReload(final ClassLoader cl) {
        try {
            Class<?> viewModel = HostCompat.load(cl, "za1");
            Class<?> action = HostCompat.load(cl, "na1");
            int installed = 0;
            for (Method method : viewModel.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"E".equals(method.getName()) || types.length != 1 || types[0] != action
                        || method.getReturnType() != void.class) continue;
                try { deoptimize(method); } catch (Throwable ignored) {}
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object event = chain.getArg(0);
                            String remoteReloadEvent =
                                    HostCompat.isV230() ? "eb1" : "fa1";
                            if (event != null && remoteReloadEvent.equals(
                                    event.getClass().getSimpleName())) {
                                Object session = invokeNoArg(chain.getThisObject(), "G");
                                Object id = readHostField(session, "a");
                                String sid = id == null ? null : String.valueOf(id);
                                if (sid != null && FROZEN_SESSION_HEADS.containsKey(sid)) {
                                    boolean localOnly = ChatEditorUi
                                            .localSessionIdsFromAllBackups().contains(sid);
                                    if (localOnly || isFrozenNativeSessionHydrated(session)) {
                                        log("skipped remote detail reload for editor-frozen sid="
                                                + sid + " hydrated="
                                                + isFrozenNativeSessionHydrated(session));
                                        return null;
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            log("inspect editor-local remote reload failed: " + t);
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            log("installed editor-local remote reload guard za1.E x" + installed);
        } catch (Throwable t) {
            log("hookLocalSessionRemoteReload failed: " + t);
        }
    }

    /**
     * A conversation created by the editor intentionally has no cloud counterpart. DeepSeek still
     * performs its normal detail request when that row is opened; biz code 1 is handled by at0.a()
     * as a server-side deletion, which shows a toast and removes the otherwise valid local tp.
     * Suppress only that exact result for ids owned by our sidecars. All cloud conversations and
     * every other error continue through the host unchanged.
     */
    private void hookLocalSessionDeletedResponse(final ClassLoader cl) {
        try {
            Class<?> handler = HostCompat.load(cl, "at0");
            Class<?> resultType = HostCompat.load(cl, "op5");
            Class<?> ownerType = HostCompat.load(cl, "yg3");
            int installed = 0;
            for (Method method : handler.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"a".equals(method.getName()) || types.length != 3
                        || types[0] != resultType || types[1] != boolean.class
                        || types[2] != ownerType || method.getReturnType() != void.class) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object[] args = chain.getArgs().toArray();
                            Object status = readHostField(args[0], "a");
                            Object code = readHostField(status, "a");
                            if (code instanceof Number && ((Number) code).intValue() == 1) {
                            Object viewModel = readHostField(args[2], "b");
                            Object session = invokeNoArg(viewModel, "G");
                            Object id = readHostField(session, "a");
                            String sid = id == null ? null : String.valueOf(id);
                            HashSet<String> localIds = ChatEditorUi.localSessionIdsFromAllBackups();
                            String pending = PENDING_LOCAL_OPEN_SID;
                            boolean pendingFresh = pending != null
                                    && System.currentTimeMillis() - PENDING_LOCAL_OPEN_AT < 30000L
                                    && localIds.contains(pending);
                            boolean directLocal = sid != null && localIds.contains(sid);
                            log("observed server-deleted result currentSid=" + sid
                                    + " pendingLocal=" + pending + " localIds=" + localIds.size()
                                    + " direct=" + directLocal + " pendingFresh=" + pendingFresh);
                            if (directLocal || ((sid == null || sid.length() == 0
                                    || "null".equals(sid)) && pendingFresh)) {
                                log("suppressed server-deleted result for editor-local sid="
                                        + (directLocal ? sid : pending));
                                return null;
                            }
                            }
                        } catch (Throwable t) {
                            log("inspect local session deleted result failed: " + t);
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            log("installed editor-local deleted-response guard at0.a x" + installed);
        } catch (Throwable t) {
            log("hookLocalSessionDeletedResponse failed: " + t);
        }
    }

    /**
     * Real UI traffic reaches the deletion branch through za1.N(). ART may inline the tiny at0.a
     * helper into that caller, so hooking at0 alone is insufficient even though reflective probes
     * hit it. Stop the exact code-1 event at the ViewModel boundary before it can show the toast or
     * replace the selected conversation with a new empty session.
     */
    private void hookLocalSessionDeletedFlow(final ClassLoader cl) {
        try {
            Class<?> viewModelType = HostCompat.load(cl, "za1");
            Class<?> eventType = HostCompat.load(cl, "bu0");
            Class<?> optionType = HostCompat.load(cl, "zs0");
            Class<?> envelopeType = HostCompat.load(cl, "au0");
            Class<?> errorType = HostCompat.load(cl, "op5");
            int installed = 0;
            for (Method method : viewModelType.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"N".equals(method.getName()) || types.length != 2
                        || types[0] != eventType || types[1] != optionType
                        || method.getReturnType() != void.class) continue;
                try { log("deopt za1.N ok=" + deoptimize(method)); }
                catch (Throwable t) { log("deopt za1.N failed: " + t); }
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object event = chain.getArg(0);
                            if (envelopeType.isInstance(event)) {
                                Object error = readHostField(event, "a");
                                if (errorType.isInstance(error)) {
                                    Object status = readHostField(error, "a");
                                    Object code = readHostField(status, "a");
                                    if (code instanceof Number
                                            && ((Number) code).intValue() == 1) {
                                        Object session = invokeNoArg(
                                                chain.getThisObject(), "G");
                                        Object id = readHostField(session, "a");
                                        String sid = id == null ? null : String.valueOf(id);
                                        HashSet<String> localIds =
                                                ChatEditorUi.localSessionIdsFromAllBackups();
                                        String pending = PENDING_LOCAL_OPEN_SID;
                                        boolean pendingFresh = pending != null
                                                && System.currentTimeMillis()
                                                - PENDING_LOCAL_OPEN_AT < 30000L
                                                && localIds.contains(pending);
                                        boolean directLocal = sid != null
                                                && localIds.contains(sid);
                                        log("observed ViewModel deleted event currentSid=" + sid
                                                + " pendingLocal=" + pending
                                                + " direct=" + directLocal
                                                + " pendingFresh=" + pendingFresh);
                                        if (directLocal || pendingFresh) {
                                            log("suppressed ViewModel deleted event for "
                                                    + "editor-local sid="
                                                    + (directLocal ? sid : pending));
                                            return null;
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) {
                            log("inspect ViewModel deleted event failed: " + t);
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            log("installed editor-local ViewModel deletion guard za1.N x" + installed);
        } catch (Throwable t) {
            log("hookLocalSessionDeletedFlow failed: " + t);
        }
    }

    /** Removes the gateway's reusable server sessions before DeepSeek persists/renders its page. */
    private void hookLocalApiSessionVisibility(final ClassLoader cl) {
        try {
            Class<?> pageType = HostCompat.load(cl, "sb1");
            int installed = 0;
            for (Constructor<?> ctor : pageType.getDeclaredConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length != 3 || types[0] != int.class
                        || !List.class.isAssignableFrom(types[1])
                        || types[2] != boolean.class) continue;
                hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object raw = chain.getArg(1);
                        if (!(raw instanceof List)) return chain.proceed();
                        List source = (List) raw;
                        ArrayList filtered = null;
                        for (int i = 0; i < source.size(); i++) {
                            Object session = source.get(i);
                            String sid = String.valueOf(readHostField(session, "a"));
                            if (isLocalApiInternalSession(sid)) {
                                if (filtered == null) filtered = new ArrayList(source);
                                filtered.remove(session);
                            }
                        }
                        if (filtered == null) return chain.proceed();
                        Object[] args = chain.getArgs().toArray();
                        args[1] = filtered;
                        log("[LOCAL_API] hidden reusable session(s) from cloud page="
                                + (source.size() - filtered.size()));
                        return chain.proceed(args);
                    }
                });
                installed++;
            }
            log("installed local API session visibility filter sb1 x" + installed);
        } catch (Throwable t) {
            log("hookLocalApiSessionVisibility failed: " + t);
        }
    }

    private void hookNativeSessionNavigator(final ClassLoader cl) {
        try {
            Class<?> mc = HostCompat.load(cl, "mc");
            Class<?> ib3 = HostCompat.load(cl, "ib3");
            int installed = 0;
            for (Method method : mc.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!HostCompat.method("mc", "f").equals(method.getName())
                        || types.length != 13) continue;
                if (!List.class.isAssignableFrom(types[0])
                        || !ib3.isAssignableFrom(types[4])
                        || !ib3.isAssignableFrom(types[5])) continue;
                final Class<?> sessionListType = types[0];
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object[] replacement = null;
                        try {
                            Object[] args = chain.getArgs().toArray();
                            if (args[0] instanceof List && args[4] != null) {
                                hookNativeSessionClickCallback(args[4], cl);
                                List source = (List) args[0];
                                List visible = null;
                                for (Object session : new ArrayList(source)) {
                                    String id = String.valueOf(readHostField(session, "a"));
                                    if (isLocalApiInternalSession(id)) {
                                        if (visible == null) {
                                            visible = copyListForHook(source, sessionListType);
                                            if (visible == null) {
                                                log("cannot preserve concrete session-list type "
                                                        + sessionListType.getName()
                                                        + "; keeping the host list unchanged");
                                                break;
                                            }
                                        }
                                        visible.remove(session);
                                    }
                                }
                                if (visible != null) {
                                    source = visible;
                                    args[0] = source;
                                    replacement = args;
                                }
                                int serverSize = source.size();
                                HashSet<String> localIds = localOnlySessionIds(cl);
                                HashSet<String> seen = new HashSet<>();
                                List mergedCopy = copyListForHook(source, sessionListType);
                                List merged = mergedCopy == null ? source : mergedCopy;
                                synchronized (LOCAL_NATIVE_SESSIONS) {
                                    LOCAL_NATIVE_SESSIONS.keySet().retainAll(localIds);
                                    for (Object session : new ArrayList(source)) {
                                        String sid = String.valueOf(readHostField(session, "a"));
                                        if (sid == null || sid.length() == 0 || "null".equals(sid)) continue;
                                        seen.add(sid);
                                        if (localIds.contains(sid)) {
                                            LOCAL_NATIVE_SESSIONS.put(sid, session);
                                        }
                                    }
                                    for (String sid : localIds) {
                                        if (seen.contains(sid)) continue;
                                        Object localSession = LOCAL_NATIVE_SESSIONS.get(sid);
                                        if (localSession != null) {
                                            merged.add(localSession);
                                            seen.add(sid);
                                        }
                                    }
                                }
                                if (merged.size() != serverSize) {
                                    if (merged != source) args[0] = merged;
                                    long now = System.currentTimeMillis();
                                    if (now - LOCAL_NATIVE_MERGE_LOG_AT > 5000L) {
                                        LOCAL_NATIVE_MERGE_LOG_AT = now;
                                        log("preserved local native sessions="
                                                + (merged.size() - serverSize)
                                                + " server sessions=" + serverSize);
                                    }
                                }
                                NATIVE_SESSION_LIST = args[0];
                                NATIVE_SESSION_CLICK = args[4];
                                NATIVE_SESSION_EVENTS = args[5];
                                if (args[0] != source) replacement = args;
                            }
                        } catch (Throwable t) { log("capture native session navigator failed: " + t); }
                        return replacement == null ? chain.proceed() : chain.proceed(replacement);
                    }
                });
                installed++;
            }
            log("installed native session navigator hook mc.f x" + installed);
        } catch (Throwable t) { log("hookNativeSessionNavigator failed: " + t); }
    }

    /**
     * Copies a host list without erasing a concrete parameter type such as Compose's
     * SnapshotStateList. Returning {@code null} tells the caller to fail open with the original
     * list when that host version offers no safe no-argument copy path.
     */
    static List copyListForHook(List source, Class<?> parameterType) {
        if (source == null || parameterType == null) return null;
        try {
            Constructor<?> constructor = source.getClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            Object value = constructor.newInstance();
            if (value instanceof List && parameterType.isInstance(value)) {
                List copy = (List) value;
                copy.addAll(source);
                return copy;
            }
        } catch (Throwable ignored) {}
        if (parameterType.isAssignableFrom(ArrayList.class)) {
            return new ArrayList(source);
        }
        return null;
    }

    private void hookNativeSessionClickCallback(Object callback, final ClassLoader cl) {
        if (callback == null) return;
        Class<?> callbackClass = callback.getClass();
        synchronized (NATIVE_CLICK_HOOKED_CLASSES) {
            if (!NATIVE_CLICK_HOOKED_CLASSES.add(callbackClass)) return;
        }
        int installed = 0;
        try {
            for (Class<?> type = callbackClass; type != null; type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    if (!"g".equals(method.getName())
                            || method.getParameterTypes().length != 1) continue;
                    hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            if (chain.getThisObject() != NATIVE_SESSION_CLICK) {
                                return chain.proceed();
                            }
                            Object session = chain.getArg(0);
                            if (session == null
                                    || !HostCompat.simpleNameIs(session, "tp")) {
                                return chain.proceed();
                            }
                            try {
                                Object id = readHostField(session, "a");
                                String sid = id == null ? null : String.valueOf(id);
                                if (sid != null && sid.length() > 0 && !"null".equals(sid)) {
                                    if (FROZEN_SESSION_HEADS.containsKey(sid)) {
                                        hydrateFrozenNativeSession(cl, session, sid);
                                    }
                                    HashSet<String> locals =
                                            ChatEditorUi.localSessionIdsFromAllBackups();
                                    Object messages = readHostField(session, "f");
                                    Object transactions = readHostField(session, "q");
                                    Object messageState = readHostField(session, "j");
                                    Object stateValue = messageState == null ? null
                                            : invokeNoArg(messageState, "getValue");
                                    Object stateRows = readHostField(stateValue, "a");
                                    log("native click state sid=" + sid
                                            + " messages=" + (messages instanceof Map
                                            ? ((Map) messages).size() : -1)
                                            + " transactions=" + (transactions instanceof Map
                                            ? ((Map) transactions).size() : -1)
                                            + " head=" + invokeNoArg(session, "t")
                                            + " n=" + readHostField(session, "n")
                                            + " o=" + readHostField(session, "o")
                                            + " state=" + (stateValue == null ? "null"
                                            : stateValue.getClass().getName())
                                            + " rows=" + (stateRows instanceof List
                                            ? ((List) stateRows).size() : -1));
                                    if (locals.contains(sid)) {
                                        PENDING_LOCAL_OPEN_SID = sid;
                                        PENDING_LOCAL_OPEN_AT = System.currentTimeMillis();
                                        log("native click selected editor-local sid=" + sid);
                                    } else {
                                        PENDING_LOCAL_OPEN_SID = null;
                                        PENDING_LOCAL_OPEN_AT = 0L;
                                        log("native click selected server sid=" + sid);
                                    }
                                }
                            } catch (Throwable t) {
                                log("inspect native session click failed: " + t);
                            }
                            return chain.proceed();
                        }
                    });
                    installed++;
                }
            }
            log("installed native session click callback hooks=" + installed
                    + " class=" + callbackClass.getName());
        } catch (Throwable t) {
            log("hook native session click callback failed: " + t);
        }
    }

    /**
     * Reuses DeepSeek's own gm8 -> sl8 -> kv pipeline to materialise an editor-frozen WCDB table
     * into the exact tp object selected by the sidebar.  This avoids both Android-SQLite/WCDB
     * cross-engine reads and hand-built host message objects.
     */
    private static boolean hydrateFrozenNativeSession(ClassLoader cl, Object session, String sid) {
        if (session == null || sid == null) return false;
        try {
            Object messages = readHostField(session, "f");
            Object head = invokeNoArg(session, "t");
            if (messages instanceof Map && ((Map) messages).size() > 1 && head != null) return true;
            Object repository = liveFm8;
            Integer localHead = FROZEN_SESSION_HEADS.get(sid);
            if (repository == null || localHead == null) return false;

            Class<?> continuation = HostCompat.load(cl, "uz1");
            Class<?> unitType = HostCompat.load(cl, "ui8");
            Field unitField = unitType.getDeclaredField("a");
            unitField.setAccessible(true);
            Object unit = unitField.get(null);

            Class<?> loaderType = HostCompat.load(cl, "ve1");
            Constructor<?> loaderCtor = loaderType.getDeclaredConstructor(
                    HostCompat.load(cl, "gm8"), String.class, continuation, int.class);
            loaderCtor.setAccessible(true);
            Object loader = loaderCtor.newInstance(repository, sid, null, 0);
            Method executeLoader = loaderType.getDeclaredMethod("y", Object.class);
            executeLoader.setAccessible(true);
            Object rows = executeLoader.invoke(loader, unit);
            if (!(rows instanceof List) || ((List) rows).isEmpty()) {
                log("frozen native hydration found no WCDB rows sid=" + sid);
                return false;
            }

            Class<?> mapperType = HostCompat.load(cl, "ie");
            Constructor<?> mapperCtor = null;
            for (Constructor<?> ctor : mapperType.getDeclaredConstructors()) {
                Class<?>[] types = ctor.getParameterTypes();
                if (types.length == 5 && types[4] == int.class) {
                    mapperCtor = ctor;
                    break;
                }
            }
            if (mapperCtor == null) throw new NoSuchMethodException("ie case-7 constructor");
            mapperCtor.setAccessible(true);
            Object mapper = mapperCtor.newInstance(session, rows, localHead, null, 7);
            Method executeMapper = mapperType.getDeclaredMethod("y", Object.class);
            executeMapper.setAccessible(true);
            executeMapper.invoke(mapper, unit);

            Object after = readHostField(session, "f");
            Object afterHead = invokeNoArg(session, "t");
            boolean hydrated = after instanceof Map && ((Map) after).size() > 1
                    && afterHead != null;
            log("frozen native hydration sid=" + sid + " rows=" + ((List) rows).size()
                    + " messages=" + (after instanceof Map ? ((Map) after).size() : -1)
                    + " head=" + afterHead + " ok=" + hydrated);
            return hydrated;
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException
                    && ((java.lang.reflect.InvocationTargetException) t).getCause() != null
                    ? ((java.lang.reflect.InvocationTargetException) t).getCause() : t;
            log("frozen native hydration failed sid=" + sid + ": " + cause);
            return false;
        }
    }

    private static boolean isFrozenNativeSessionHydrated(Object session) {
        Object messages = readHostField(session, "f");
        return messages instanceof Map && ((Map) messages).size() > 1
                && invokeNoArg(session, "t") != null;
    }

    private void hookHistoryLoadDiagnostics(final ClassLoader cl) {
        try {
            Class<?> rawLoader = HostCompat.load(cl, "ve1");
            int rawHooks = 0;
            for (Method method : rawLoader.getDeclaredMethods()) {
                if (!"y".equals(method.getName())
                        || method.getParameterTypes().length != 1) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        try {
                            Object kind = readHostField(chain.getThisObject(), "e");
                            Object sid = readHostField(chain.getThisObject(), "g");
                            if (kind instanceof Number && ((Number) kind).intValue() == 0) {
                                log("WCDB raw message load sid=" + sid + " rows="
                                        + (result instanceof List ? ((List) result).size() : -1)
                                        + " result=" + (result == null ? "null"
                                        : result.getClass().getName()));
                            }
                        } catch (Throwable t) {
                            log("inspect WCDB raw load failed: " + t);
                        }
                        return result;
                    }
                });
                rawHooks++;
            }

            Class<?> mapper = HostCompat.load(cl, "ie");
            int mapperHooks = 0;
            for (Method method : mapper.getDeclaredMethods()) {
                if (!"y".equals(method.getName())
                        || method.getParameterTypes().length != 1) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object self = chain.getThisObject();
                        Object kind = readHostField(self, "e");
                        if (!(kind instanceof Number) || ((Number) kind).intValue() != 7) {
                            return chain.proceed();
                        }
                        Object session = readHostField(self, "f");
                        Object rows = readHostField(self, "g");
                        Object messages = readHostField(session, "f");
                        String sid = String.valueOf(readHostField(session, "a"));
                        log("native message map begin sid=" + sid + " rows="
                                + (rows instanceof List ? ((List) rows).size() : -1)
                                + " cache=" + (messages instanceof Map
                                ? ((Map) messages).size() : -1));
                        try {
                            Object result = chain.proceed();
                            Object after = readHostField(session, "f");
                            log("native message map end sid=" + sid + " cache="
                                    + (after instanceof Map ? ((Map) after).size() : -1));
                            return result;
                        } catch (Throwable t) {
                            log("native message map failed sid=" + sid + " error=" + t);
                            throw t;
                        }
                    }
                });
                mapperHooks++;
            }
            log("installed history-load diagnostics ve1=" + rawHooks
                    + " ie=" + mapperHooks);
        } catch (Throwable t) {
            log("hook history-load diagnostics failed: " + t);
        }
    }

    /**
     * The sidebar and the chat ViewModel can hold distinct tp instances for the same session ID.
     * Capture za1.G() so a proactive response updates the instance actually observed by the open
     * conversation instead of waiting for process recreation to reload WCDB.
     */
    private void hookActiveChatSessionCapture(final ClassLoader cl) {
        try {
            Class<?> viewModel = HostCompat.load(cl, "za1");
            Class<?> sessionType = HostCompat.load(cl, "tp");
            int installed = 0;
            for (Method method : viewModel.getDeclaredMethods()) {
                if (!"G".equals(method.getName())
                        || method.getParameterTypes().length != 0
                        || method.getReturnType() != sessionType) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object session = chain.proceed();
                        try {
                            String sid = String.valueOf(readHostField(session, "a"));
                            if (isUsableSessionId(sid)) {
                                ACTIVE_CHAT_SESSIONS.put(
                                        sid, new WeakReference<Object>(session));
                                ACTIVE_CHAT_VIEW_MODELS.put(
                                        sid, new WeakReference<Object>(
                                                chain.getThisObject()));
                                NativeHeartbeatHistory pending =
                                        PENDING_NATIVE_HEARTBEAT_HISTORIES.get(sid);
                                if (pending != null
                                        && mergeNativeHeartbeatHistoryIntoSession(
                                                pending, session)) {
                                    PENDING_NATIVE_HEARTBEAT_HISTORIES.remove(
                                            sid, pending);
                                    log("proactive history applied to active ViewModel sid="
                                            + sid + " head=" + pending.head);
                                }
                            }
                        } catch (Throwable error) {
                            log("active chat session capture failed: "
                                    + safeThrowableMessage(error));
                        }
                        return session;
                    }
                });
                installed++;
            }
            log("installed active chat session capture za1.G x" + installed);
        } catch (Throwable error) {
            log("hook active chat session capture failed: " + error);
        }
    }

    /** Keeps the anonymous transport request out of Compose while retaining its assistant child. */
    private void hookProactiveVisibleThreadFilter(final ClassLoader cl) {
        try {
            Class<?> sessionType = HostCompat.load(cl, "tp");
            int installed = 0;
            for (Method method : sessionType.getDeclaredMethods()) {
                if (!"s".equals(method.getName())
                        || method.getParameterTypes().length != 0
                        || !List.class.isAssignableFrom(method.getReturnType())) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        if (!(result instanceof List)) return result;
                        List source = (List) result;
                        ArrayList<Object> kept = null;
                        for (int index = 0; index < source.size(); index++) {
                            Object message = source.get(index);
                            if (!isAnonymousHeartbeatUserMessage(message)) continue;
                            if (kept == null) kept = new ArrayList<Object>(source);
                            kept.remove(message);
                        }
                        if (kept == null) return result;
                        try {
                            Constructor<?> constructor =
                                    result.getClass().getDeclaredConstructor();
                            constructor.setAccessible(true);
                            Object filtered = constructor.newInstance();
                            if (!(filtered instanceof List)) return result;
                            ((List) filtered).addAll(kept);
                            return filtered;
                        } catch (Throwable copyError) {
                            log("native proactive visible-thread copy failed: "
                                    + safeThrowableMessage(copyError));
                            return result;
                        }
                    }
                });
                installed++;
            }
            log("installed native proactive visible-thread filter tp.s x" + installed);
        } catch (Throwable error) {
            log("hook proactive visible-thread filter failed: " + error);
        }
    }

    /**
     * DeepSeek's native pipeline performs the actual SSE reduction. Observe its final apply only
     * to post the notification and replace the local database with the folded visible chain.
     */
    private void hookNativeUiHeartbeatCompletion(final ClassLoader cl) {
        try {
            Class<?> sessionType = HostCompat.load(cl, "tp");
            Class<?> messageType = HostCompat.load(cl, "uo");
            Class<?> viewModelType = HostCompat.load(cl, "za1");
            Class<?> outcomeType = HostCompat.load(cl, "bu0");
            int installed = 0;
            for (Method method : sessionType.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if ("p".equals(method.getName()) && types.length == 2
                        && messageType.isAssignableFrom(types[0])) {
                    hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            maybeCompleteNativeUiHeartbeat(
                                    chain.getThisObject(), chain.getArg(0));
                            return result;
                        }
                    });
                    installed++;
                } else if ("u".equals(method.getName()) && types.length == 2
                        && types[0] == sessionType
                        && List.class.isAssignableFrom(types[1])) {
                    hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object result = chain.proceed();
                            Object session = chain.getArg(0);
                            Object values = chain.getArg(1);
                            if (values instanceof List) {
                                for (Object message : (List) values) {
                                    maybeCompleteNativeUiHeartbeat(session, message);
                                }
                            }
                            return result;
                        }
                    });
                    installed++;
                }
            }
            for (Method method : viewModelType.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"N".equals(method.getName()) || types.length != 2
                        || types[0] != outcomeType) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object viewModel = chain.getThisObject();
                        Object session = invokeNoArg(viewModel, "G");
                        String sid = String.valueOf(readHostField(session, "a"));
                        NativeUiHeartbeatRequest pending =
                                PENDING_NATIVE_UI_HEARTBEATS.get(sid);
                        if (pending != null) {
                            log("native proactive outcome id=" + pending.requestId
                                    + " event=" + truncateForLog(
                                            deepDump(chain.getArg(0), 4), 1800));
                        }
                        Object result = chain.proceed();
                        if (pending != null) {
                            Object values = readHostField(session, "f");
                            if (values instanceof Map) {
                                for (Object message : ((Map) values).values()) {
                                    maybeCompleteNativeUiHeartbeat(session, message);
                                }
                            }
                        }
                        return result;
                    }
                });
                installed++;
            }
            log("installed native proactive stream completion hooks x" + installed);
        } catch (Throwable error) {
            log("hook native proactive stream completion failed: " + error);
        }
    }

    private static void maybeCompleteNativeUiHeartbeat(
            Object session, Object assistantMessage) {
        if (session == null || assistantMessage == null) return;
        String sid = String.valueOf(readHostField(session, "a"));
        NativeUiHeartbeatRequest pending = PENDING_NATIVE_UI_HEARTBEATS.get(sid);
        if (pending == null) return;
        if (System.currentTimeMillis() - pending.startedAt > 4L * 60L * 1000L) {
            PENDING_NATIVE_UI_HEARTBEATS.remove(sid, pending);
            return;
        }
        Object roleValue = invokeNoArg(assistantMessage, "A");
        if (roleValue == null) roleValue = fieldByName(assistantMessage, "h");
        if (!"ASSISTANT".equals(String.valueOf(roleValue))) return;

        Integer parentId = intField(assistantMessage, "g");
        if (parentId == null) {
            Object parentValue = invokeNoArg(assistantMessage, "w");
            if (parentValue instanceof Number) {
                parentId = Integer.valueOf(((Number) parentValue).intValue());
            }
        }
        Object messages = readHostField(session, "f");
        Object parent = messages instanceof Map && parentId != null
                ? ((Map) messages).get(parentId) : null;
        if (!isAnonymousHeartbeatUserMessage(parent)) return;
        if (!pending.completing.compareAndSet(false, true)) return;
        PENDING_NATIVE_UI_HEARTBEATS.remove(sid, pending);

        final Object finalMessage = assistantMessage;
        new Thread(new Runnable() {
            @Override public void run() {
                completeNativeUiHeartbeat(pending, finalMessage);
            }
        }, "Deekseep-native-proactive-finish").start();
    }

    private static void completeNativeUiHeartbeat(
            NativeUiHeartbeatRequest pending, Object assistantMessage) {
        String message = visibleAssistantMessageText(assistantMessage);
        boolean persisted = false;
        boolean applied = false;
        Integer head = null;
        NativeHeartbeatHistory refreshed = null;
        try {
            // Let the host finish its own final reducer/write before replacing the transport-only
            // user event with the folded visible server branch.
            Thread.sleep(250L);
            Main module = MODULE;
            if (module != null) {
                refreshed = module.refreshNativeHeartbeatHistory(
                        pending.sid, pending.previousHead);
                persisted = module.persistNativeHeartbeatHistory(refreshed);
                if (refreshed != null) {
                    head = refreshed.head;
                    PENDING_NATIVE_HEARTBEAT_HISTORIES.put(
                            refreshed.sid, refreshed);
                }
                applied = module.applyNativeHeartbeatHistory(refreshed);
            }
        } catch (Throwable error) {
            log("native proactive final history refresh failed sid=" + pending.sid
                    + ": " + safeThrowableMessage(error));
        }
        // tp.p may expose the newly-created assistant shell before the final SSE
        // fragments have been copied onto that particular object. The refreshed
        // server history is authoritative and already contains the completed
        // response, so use its head message when the early object was empty.
        if (message.length() == 0) {
            message = visibleHeadAssistantMessageText(refreshed);
        }
        if (ProactiveHeartbeatReceiver.TASK_KIND_HEARTBEAT
                .equals(pending.taskKind) && message.length() > 0) {
            rememberProactiveMessage(pending.sid, message);
        }
        Context context = pending.context == null
                ? currentHostContext() : pending.context;
        if (context != null && message.length() > 0) {
            dispatchProactiveHeartbeatResponse(
                    context, pending.requestId, message,
                    isDeepSeekForeground(), pending.taskReminder,
                    pending.taskKind, pending.sid);
        }
        log("native proactive stream completed id=" + pending.requestId
                + " sid=" + pending.sid
                + " chars=" + message.length()
                + " head=" + head
                + " persisted=" + persisted
                + " applied=" + applied);
    }

    private static String visibleHeadAssistantMessageText(
            NativeHeartbeatHistory history) {
        if (history == null || history.messages == null
                || history.messages.isEmpty()) return "";
        if (history.head != null) {
            for (Object candidate : history.messages) {
                if (!history.head.equals(intField(candidate, "f"))) continue;
                String text = visibleAssistantMessageText(candidate);
                if (text.length() > 0) return text;
            }
        }
        for (int index = history.messages.size() - 1; index >= 0; index--) {
            Object candidate = history.messages.get(index);
            Object role = fieldByName(candidate, "h");
            if (role == null) role = invokeNoArg(candidate, "A");
            if (!"ASSISTANT".equals(String.valueOf(role))) continue;
            String text = visibleAssistantMessageText(candidate);
            if (text.length() > 0) return text;
        }
        return "";
    }

    private static String visibleAssistantMessageText(Object message) {
        Object fragmentsValue = readHostField(message, "t");
        if (!(fragmentsValue instanceof List)) {
            fragmentsValue = invokeNoArg(message, "l");
        }
        if (!(fragmentsValue instanceof List)) return "";
        StringBuilder text = new StringBuilder();
        for (Object fragment : (List) fragmentsValue) {
            String type = String.valueOf(readHostField(fragment, "a"));
            if (!"RESPONSE".equals(type)
                    && !"TEMPLATE_RESPONSE".equals(type)) continue;
            Object content = readHostField(fragment, "c");
            if (!(content instanceof String)) continue;
            text.append((String) content);
        }
        return normalizeProactiveMessage(
                HeartbeatToolProtocol.stripControlBlocks(text.toString()));
    }

    private static boolean isAnonymousHeartbeatUserMessage(Object message) {
        if (message == null) return false;
        Object role = invokeNoArg(message, "A");
        if (role == null) role = fieldByName(message, "h");
        return "USER".equals(String.valueOf(role))
                && messageContainsAnonymousHeartbeatEvent(message);
    }

    private void scheduleRealSessionProbe() {
        final File marker = new File(REAL_SESSION_PROBE_FILE);
        if (!marker.isFile()) return;
        final String raw = readSmallText(REAL_SESSION_PROBE_FILE);
        final String sid = raw == null ? "" : raw.trim();
        marker.delete();
        if (!sid.matches("[0-9a-fA-F-]{36}")) {
            log("real session probe invalid sid");
            return;
        }
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                for (int i = 0; i < 60; i++) {
                    if (NATIVE_SESSION_LIST instanceof List && NATIVE_SESSION_CLICK != null) {
                        try { Thread.sleep(4000L); }
                        catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                        main.post(new Runnable() {
                            @Override public void run() {
                                log("real session probe navigation sid=" + sid
                                        + " opened=" + openNativeSession(sid));
                            }
                        });
                        return;
                    }
                    try { Thread.sleep(250L); }
                    catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                log("real session probe timed out sid=" + sid);
            }
        }, "Deekseep-real-session-probe");
        worker.setDaemon(true);
        worker.start();
    }

    static void refreshNativeHistorySnapshots() {
        try { HistoryBridge.processNativeSessions(NATIVE_SESSION_LIST); }
        catch (Throwable t) { log("refresh native history snapshots failed: " + t); }
    }

    static void refreshNativeHistorySnapshot(String sid) {
        try { HistoryBridge.processNativeSession(NATIVE_SESSION_LIST, sid); }
        catch (Throwable t) { log("refresh native history snapshot failed: " + t); }
    }

    // 当前侧栏的 tp 目录可能比 SQLite 的 chat_session_list 更早拿到新会话。
    // 编辑器每次打开时合并这份只读元数据，避免刚创建的对话暂时消失。
    static List<Object[]> nativeSessionDirectory() {
        ArrayList<Object[]> out = new ArrayList<>();
        Object value = NATIVE_SESSION_LIST;
        if (!(value instanceof List)) return out;
        try {
            for (Object session : new ArrayList<Object>((List) value)) {
                String sid = String.valueOf(readHostField(session, "a"));
                if (sid == null || sid.length() == 0 || "null".equals(sid)) continue;
                if (isSessionRecentlyDeleted(sid)) continue;
                Object titleState = readHostField(session, "g");
                Object title = titleState == null ? null : invokeNoArg(titleState, "getValue");
                Object updated = readHostField(session, "c");
                Object model = invokeNoArg(session, "f");
                out.add(new Object[]{sid, title instanceof String ? title : "", updated, model});
            }
        } catch (Throwable t) { log("native session directory failed: " + t); }
        return out;
    }

    static boolean openNativeSession(String sid) {
        if (sid == null || sid.length() == 0) return false;
        if (isSessionRecentlyDeleted(sid)) return false;
        Object sessions = NATIVE_SESSION_LIST;
        Object click = NATIVE_SESSION_CLICK;
        if (!(sessions instanceof List) || click == null) {
            log("native session navigation unavailable: host sidebar state not captured");
            return false;
        }
        try {
            for (Object session : (List) sessions) {
                Object id = readHostField(session, "a");
                if (!sid.equals(String.valueOf(id))) continue;
                if (invokeHostOneArg(click, session)) {
                    log("native session navigation sid=" + sid);
                    return true;
                }
                break;
            }
        } catch (Throwable t) {
            log("native session navigation failed: " + t);
        }
        return false;
    }

    private static void consumeHeartbeatConversationIntent(
            final Activity activity, Intent intent) {
        if (activity == null || intent == null) return;
        final String sid = HeartbeatToolProtocol.cleanScope(
                intent.getStringExtra(
                        ProactiveHeartbeatReceiver.EXTRA_CONVERSATION_ID));
        if (sid.length() == 0) return;
        intent.removeExtra(ProactiveHeartbeatReceiver.EXTRA_CONVERSATION_ID);
        final int generation = HEARTBEAT_OPEN_GENERATION.incrementAndGet();
        final long deadline = SystemClock.elapsedRealtime() + 12_000L;
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (HEARTBEAT_OPEN_GENERATION.get() != generation
                        || activity.isFinishing()) return;
                if (NATIVE_SESSION_LIST instanceof List
                        && NATIVE_SESSION_CLICK != null
                        && openNativeSession(sid)) {
                    log("heartbeat notification opened bound conversation sid=" + sid);
                    return;
                }
                if (SystemClock.elapsedRealtime() < deadline) {
                    handler.postDelayed(this, 300L);
                } else {
                    log("heartbeat notification could not open bound conversation sid="
                            + sid);
                }
            }
        }, 350L);
    }

    /**
     * Sends DeepSeek's own h61(tp) deletion event.  This is the same path used by the original
     * sidebar delete item and therefore keeps the authenticated server deletion, native list
     * update, and WCDB cleanup behavior.  The per-row xa3 is retained only as a compatibility
     * fallback for builds whose event class was renamed.
     */
    static boolean requestNativeSessionDelete(String sid) {
        if (sid == null || sid.length() == 0) return false;
        Object session = findNativeSession(sid);
        Object events = NATIVE_SESSION_EVENTS;
        if (session != null && events != null) {
            try {
                ClassLoader cl = session.getClass().getClassLoader();
                Class<?> eventType = HostCompat.load(cl, "h61");
                Constructor<?> eventCtor = null;
                for (Constructor<?> ctor : eventType.getDeclaredConstructors()) {
                    Class<?>[] types = ctor.getParameterTypes();
                    if (types.length == 1 && types[0].isAssignableFrom(session.getClass())) {
                        eventCtor = ctor;
                        break;
                    }
                }
                if (eventCtor == null) throw new NoSuchMethodException("h61(tp)");
                eventCtor.setAccessible(true);
                Object event = eventCtor.newInstance(session);
                if (invokeHostOneArg(events, event)) {
                    markSessionDeletedLocally(sid);
                    log("requested native DeepSeek session delete sid=" + sid);
                    return true;
                }
            } catch (Throwable t) {
                log("native DeepSeek delete event failed sid=" + sid + ": " + t);
            }
        }

        Object action;
        synchronized (SIDEBAR_DELETE_ACTIONS) {
            action = SIDEBAR_DELETE_ACTIONS.get(sid);
        }
        if (invokeXa3(action)) {
            markSessionDeletedLocally(sid);
            log("requested native sidebar delete fallback sid=" + sid);
            return true;
        }
        log("native DeepSeek delete unavailable sid=" + sid);
        return false;
    }

    private static Object findNativeSession(String sid) {
        Object sessions = NATIVE_SESSION_LIST;
        if (sessions instanceof List) {
            try {
                for (Object session : new ArrayList<Object>((List) sessions)) {
                    if (sid.equals(String.valueOf(readHostField(session, "a")))) return session;
                }
            } catch (Throwable ignored) {}
        }
        synchronized (LOCAL_NATIVE_SESSIONS) {
            return LOCAL_NATIVE_SESSIONS.get(sid);
        }
    }

    /**
     * Optimistically removes an explicitly deleted session from captured in-memory directories.
     * The real host request still decides server state.  The short tombstone only prevents the
     * editor from immediately re-merging a stale tp while that request is in flight.
     */
    static synchronized void markSessionDeletedLocally(String sid) {
        if (sid == null || sid.length() == 0) return;
        RECENTLY_DELETED_SESSION_IDS.put(sid, System.currentTimeMillis());
        HashSet<String> localIds = new HashSet<>(LOCAL_SESSION_IDS);
        localIds.remove(sid);
        LOCAL_SESSION_IDS = localIds;
        LOCAL_SESSION_IDS_AT = System.currentTimeMillis();
        FROZEN_SESSION_HEADS.remove(sid);
        HistoryBridge.forgetSession(sid);
        ResponsePreserver.forgetSession(sid);
        synchronized (LOCAL_NATIVE_SESSIONS) {
            LOCAL_NATIVE_SESSIONS.remove(sid);
        }
        Object sessions = NATIVE_SESSION_LIST;
        if (sessions instanceof List) {
            try {
                Object match = null;
                for (Object session : new ArrayList<Object>((List) sessions)) {
                    if (sid.equals(String.valueOf(readHostField(session, "a")))) {
                        match = session;
                        break;
                    }
                }
                if (match != null) ((List) sessions).remove(match);
            } catch (Throwable t) {
                log("remove deleted native session failed sid=" + sid + ": " + t);
            }
        }
    }

    private static boolean isSessionRecentlyDeleted(String sid) {
        Long at = RECENTLY_DELETED_SESSION_IDS.get(sid);
        if (at == null) return false;
        if (System.currentTimeMillis() - at.longValue()
                <= DELETED_SESSION_VISIBILITY_GRACE_MS) return true;
        RECENTLY_DELETED_SESSION_IDS.remove(sid, at);
        return false;
    }

    private static Object readHostField(Object target, String name) {
        if (target == null) return null;
        name = HostCompat.staticMessageField(target, name);
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean invokeHostOneArg(Object action, Object value) {
        if (action == null) return false;
        for (Class<?> type = action.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!"g".equals(method.getName()) || method.getParameterTypes().length != 1) continue;
                try {
                    method.setAccessible(true);
                    method.invoke(action, value);
                    return true;
                } catch (Throwable ignored) {}
            }
        }
        return false;
    }

    private void hookSettingsNavigation(ClassLoader cl) {
        try {
            Class<?> nav = HostCompat.load(cl, "rm5");
            for (Method m : nav.getDeclaredMethods()) {
                if (!m.getName().equals("n") || m.getParameterTypes().length != 2) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object r = chain.proceed();
                        rememberNavController(chain.getThisObject());
                        scheduleRouteCheck(chain.getThisObject());
                        return r;
                    }
                });
                log("hooked nav route rm5.n");
                break;
            }
            hookNavStateMethod(nav, "b");
            hookNavStateMethod(nav, "m");
            hookNavStateMethod(nav, "q");
            hookNavStateMethod(nav, "r");
            hookNavStateMethod(nav, "u");
        } catch (Throwable t) { log("hook nav route failed: " + t); }

        try {
            Class<?> gf8 = HostCompat.load(cl, "gf8");
            for (Method m : gf8.getDeclaredMethods()) {
                if (!m.getName().equals("A0") || m.getParameterTypes().length != 1) continue;
                hook(m).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object r = chain.proceed();
                        Object nav = chain.getArg(0);
                        if (nav != null) {
                            rememberNavController(nav);
                            scheduleRouteCheck(nav);
                        } else {
                            main.post(new Runnable() {
                                public void run() {
                                    ChatAppearance.onRouteChanged(curAct.get(), null);
                                    hideButton();
                                }
                            });
                        }
                        return r;
                    }
                });
                log("hooked nav pop gf8.A0");
                break;
            }
        } catch (Throwable t) { log("hook nav pop failed: " + t); }
    }

    private static boolean isSettingsRootRoute(Object route) {
        if (route == null) return false;
        String n = route.getClass().getName();
        return n.endsWith(".yc7") || n.endsWith(".vc7") || n.equals("yc7") || n.equals("vc7");
    }

    private void hookNavStateMethod(Class<?> nav, String name) {
        int count = 0;
        for (Method m : nav.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            hook(m).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object r = chain.proceed();
                    rememberNavController(chain.getThisObject());
                    scheduleRouteCheck(chain.getThisObject());
                    return r;
                }
            });
            count++;
        }
        log("hooked nav state rm5." + name + " x" + count);
    }

    private void rememberNavController(Object nav) {
        if (nav != null) navController = new WeakReference<>(nav);
    }

    private void scheduleRouteCheck(final Object nav) {
        // Read once on the next main-loop turn so wallpaper parallax can start with the native
        // navigation transition, then read again after host state has fully settled.
        main.post(new Runnable() {
            public void run() { syncButtonWithRoute(nav); }
        });
        main.postDelayed(new Runnable() {
            public void run() { syncButtonWithRoute(nav); }
        }, 120);
    }

    private void syncButtonWithRoute(Object nav) {
        try {
            String route = currentRoute(nav != null ? nav : navController.get());
            ChatAppearance.onRouteChanged(curAct.get(), route);
            if (route == null || route.length() == 0) return;
            if (btn.get() == null) return;
            if (!isSettingsRootRouteName(route)) {
                log("route left settings: " + route);
                hideButton();
            } else {
                log("route still settings: " + route);
            }
        } catch (Throwable t) { log("sync route failed: " + t); }
    }

    private static boolean isSettingsRootRouteName(String route) {
        return route.contains("SettingsNestedGraph.SettingsRoute")
                || route.equals("vc7")
                || route.endsWith(".vc7")
                || route.contains(" route=vc7");
    }

    private static String currentRoute(Object nav) {
        if (nav == null) return null;
        try {
            Method i = nav.getClass().getDeclaredMethod("i");
            i.setAccessible(true);
            Object dest = i.invoke(nav);
            if (dest == null) return null;

            String route = stringField(dest, "g");
            if (route != null && route.length() > 0) return route;
            return String.valueOf(dest);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String stringField(Object obj, String name) {
        try {
            name = HostCompat.staticMessageField(obj, name);
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(obj);
            return v instanceof String ? (String) v : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // 首次注入时显示一份简短使用说明；“稍后”不会退出宿主，“我知道了”后不再提示。
    private void maybeShowDisclaimer(final Activity act) {
        if (disclaimerHandled) return;
        try {
            File marker = new File(DISCLAIMER_FILE);
            if (marker.exists()) {
                BufferedReader reader = null;
                try {
                    reader = new BufferedReader(new FileReader(marker));
                    if (DISCLAIMER_VERSION.equals(reader.readLine())) {
                        disclaimerHandled = true;
                        return;
                    }
                } finally {
                    if (reader != null) try { reader.close(); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        disclaimerHandled = true;
        if (act == null || act.isFinishing()) return;
        act.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    String msgZh =
                        "欢迎使用 Deekseep。它是面向 DeepSeek Android 的独立增强模块，不是官方功能。\n\n"
                        + "为了更顺利地使用：\n"
                        + "• 请安装与 DeepSeek 渠道和 versionCode 匹配的模块；App 更新后，部分功能可能需要重新适配。\n"
                        + "• 编辑或删除会话、切换账号前，建议先备份重要数据。\n"
                        + "• 账号导出、API Key 和诊断日志可能包含私密信息，请只保存在可信位置。\n"
                        + "• 实验性功能默认关闭，可按需开启；实际能力仍由 DeepSeek 服务器和账号权限决定。\n\n"
                        + "点击“我知道了”后不再提示；选择“稍后”也可以继续使用 DeepSeek。";
                    String msgEn =
                        "Welcome to Deekseep. It is an independent enhancement module for DeepSeek Android, not an official feature.\n\n"
                        + "For a smoother experience:\n"
                        + "• Install the module that matches your DeepSeek channel and versionCode. Some features may need adaptation after an app update.\n"
                        + "• Back up important data before editing or deleting chats or switching accounts.\n"
                        + "• Account exports, API keys, and diagnostic logs may contain private information; keep them only in trusted locations.\n"
                        + "• Experimental features are off by default and can be enabled as needed. Actual availability still depends on DeepSeek servers and account permissions.\n\n"
                        + "Select “Got it” to hide this note in the future. “Later” also lets you continue using DeepSeek.";
                    DeekseepUi.showCustomConfirm(act,
                        UiLanguage.text(act, "Deekseep 首次使用说明", "Getting started with Deekseep"),
                        UiLanguage.text(act, msgZh, msgEn),
                        UiLanguage.text(act, "稍后", "Later"),
                        UiLanguage.text(act, "我知道了", "Got it"), true,
                        null,
                        new Runnable() {
                            @Override public void run() {
                                try {
                                    FileWriter w = new FileWriter(DISCLAIMER_FILE, false);
                                    w.write(DISCLAIMER_VERSION);
                                    w.close();
                                } catch (Throwable ignored) {}
                            }
                        });
                } catch (Throwable t) { log("disclaimer show err: " + t); }
            }
        });
    }

    private void showButton() {
        try {
            final Activity act = curAct.get();
            if (act == null || act.isFinishing()) return;

            TextView existing = btn.get();
            if (existing != null && existing.getContext() == act && existing.getParent() != null) {
                existing.setTextColor(DeekseepUi.isDark(act) ? 0xFFECECEC : 0xFF1A1A1A);
                existing.setVisibility(View.VISIBLE);
                existing.bringToFront();
                return;
            }

            ViewGroup content = act.findViewById(android.R.id.content);
            if (content == null) return;

            TextView b = DeekseepUi.createEntryButton(act, new View.OnClickListener() {
                public void onClick(View v) {
                    try { DeekseepUi.showPage(act); }
                    catch (Throwable t) { log("showPage failed: " + t); }
                }
            });

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.TOP | Gravity.END;
            lp.topMargin = DeekseepUi.statusBarHeight(act) + DeekseepUi.dp(act, 8);
            lp.rightMargin = DeekseepUi.dp(act, 12);
            content.addView(b, lp);
            b.bringToFront();
            btn = new WeakReference<>(b);
            log("button added on " + act.getClass().getName());
            scheduleRouteCheck(navController.get());
        } catch (Throwable t) { log("showButton failed: " + t); }
    }

    private void hideButton() {
        try {
            TextView existing = btn.get();
            if (existing == null) return;
            ViewGroup parent = (ViewGroup) existing.getParent();
            if (parent != null) parent.removeView(existing);
            btn = new WeakReference<>(null);
            log("button removed");
        } catch (Throwable t) { log("hideButton failed: " + t); }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 专家图片 → 视觉描述中继（从 legacy 移植；此段为纯反射+现代 hook API）
    // ══════════════════════════════════════════════════════════════════════

    // 1) transport 入口 r92.b：捕获活着的 r92、把发送点图片挂到 ew0、返回时包装 Flow 跑中继
    private void installNetworkPayloadCapture(ClassLoader cl) {
        try {
            Class<?> rs0 = HostCompat.load(cl, "rs0");
            int n = 0;
            // 快路径：transport 类 b(rs0,Long)。build 间该类改名(2.2.1=r92 / 2.2.2=s92)，两名都试。
            for (String legacyTxName : new String[]{"r92", "s92", "t92", "q92"}) {
                String txName = HostCompat.name(legacyTxName);
                try {
                    Class<?> txc = cl.loadClass(txName);
                    for (Method m : txc.getDeclaredMethods()) {
                        Class<?>[] pts = m.getParameterTypes();
                        if (!m.getName().equals("b") || pts.length != 2 || !rs0.isAssignableFrom(pts[0])) continue;
                        hookTransport(m); n++;
                        log("installed network payload capture on " + txName + ".b");
                    }
                } catch (Throwable ignored) {}
                if (n > 0) break;
            }
            // 兜底：设备上 DeepSeek 有另一个 build（transport 类被改名），r92 变空类。
            // rs0(接口)与 Long 跨 build 稳定 → 按结构签名 (rs0,Long) 在运行时 dex 里扫出真正的 transport 方法。
            if (n == 0) {
                Method tx = findTransportByStructure(cl, rs0);
                if (tx != null) { hookTransport(tx); n = 1;
                    log("installed network payload capture via structural scan x1"); }
                else log("structural transport scan found nothing");
            }
            // 中继实现：collect 时机的 hook(见 registerRelayFlow)。返回值是 Object，不会被强转闪退。
            installExpertFlowCollectHook(cl);
        } catch (Throwable t) { log("installNetworkPayloadCapture failed: " + t); }
    }

    // 给定 transport 方法(签名 (rs0,Long)->Flow) 装上中继包装 hook
    private void hookTransport(Method m) {
        hook(m).intercept(new Hooker() {
            @Override public Object intercept(Chain chain) throws Throwable {
                Object[] args = chain.getArgs().toArray();
                try { if (liveR92 == null) liveR92 = chain.getThisObject(); } catch (Throwable ignored) {}
                try {
                    Object req = args != null && args.length > 0 ? args[0] : null;
                    List fps = tlPendingFps.get();
                    String effectiveModel = tlPendingModel.get();
                    tlPendingFps.remove();
                    tlPendingModel.remove();
                    if (req != null) {
                        if (fps != null) ew0Fps.put(req, fps);
                        if (effectiveModel != null) ew0EffectiveModels.put(req, effectiveModel);
                    }
                } catch (Throwable ignored) {}
                Object r = chain.proceed();
                try {
                    Object reqObj = args != null && args.length > 0 ? args[0] : null;
                    // 关键：不能把返回值换成 Proxy(会被 libxposed 强转成声明返回类型 b41 而 CCE 闪退)。
                    // 改为：原样返回真实 b41，但把该 b41 实例登记下来；等它被 collect(b41.b) 时再跑中继。
                    registerRelayFlow(reqObj, r, chain.getThisObject());
                } catch (Throwable t) { extLog("[RELAY] register err " + t + "\n" + stackToString(t)); }
                return r;
            }
        });
    }

    // 运行时(app 进程内)扫描自身 dex，按结构签名 (rs0,Long)->非void 找 transport 方法。build 无关。
    private Method findTransportByStructure(ClassLoader cl, Class<?> rs0) {
        try {
            java.util.List<String> names = listDexClasses(cl);
            int scanned = 0;
            for (String nm : names) {
                if (nm.indexOf('.') >= 0) continue;   // defpackage 混淆类无包名
                if (nm.length() > 6) continue;         // 混淆名很短，跳过长名降负载
                Class<?> c;
                try { c = Class.forName(nm, false, cl); }  // false=不初始化，避免静态副作用
                catch (Throwable t) { continue; }
                scanned++;
                for (Method m : c.getDeclaredMethods()) {
                    Class<?>[] pt = m.getParameterTypes();
                    if (pt.length == 2 && pt[0] == rs0 && pt[1] == Long.class
                            && m.getReturnType() != void.class && !m.getReturnType().isPrimitive()) {
                        log("[TX] found transport " + c.getName() + "." + m.getName()
                                + "(rs0,Long)->" + m.getReturnType().getName());
                        return m;
                    }
                }
            }
            log("[TX] scanned=" + scanned + "/" + names.size() + " no (rs0,Long) match");
        } catch (Throwable t) { log("[TX] scan failed: " + t); }
        return null;
    }

    @SuppressWarnings("unchecked")
    private java.util.List<String> listDexClasses(ClassLoader cl) throws Exception {
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        Class<?> bdcl = Class.forName("dalvik.system.BaseDexClassLoader");
        Field plF = bdcl.getDeclaredField("pathList"); plF.setAccessible(true);
        Object pl = plF.get(cl);
        Field deF = pl.getClass().getDeclaredField("dexElements"); deF.setAccessible(true);
        Object[] els = (Object[]) deF.get(pl);
        for (Object el : els) {
            Field dfF = el.getClass().getDeclaredField("dexFile"); dfF.setAccessible(true);
            Object df = dfF.get(el);
            if (df == null) continue;
            Method entries = df.getClass().getDeclaredMethod("entries"); entries.setAccessible(true);
            java.util.Enumeration<String> en = (java.util.Enumeration<String>) entries.invoke(df);
            while (en.hasMoreElements()) out.add(en.nextElement());
        }
        return out;
    }

    // 2) 通用历史清理/快照 + 专家图片保留。2.2.1=fm8/rl8，2.2.2=gm8/sl8。
    private void installExpertHistoryImagePreserver(final ClassLoader cl) {
        int repoCount = 0;
        int ctorCount = 0;
        int writeCount = 0;
        for (String legacyRepoName : new String[]{"gm8", "fm8"}) {
            String repoName = HostCompat.name(legacyRepoName);
            try {
                final Class<?> repo = cl.loadClass(repoName);
                ArrayList<Method> writers = new ArrayList<>();
                for (Method m : repo.getDeclaredMethods()) {
                    Class<?>[] pts = m.getParameterTypes();
                    if ("b".equals(m.getName()) && pts.length == 7
                            && pts[0] == String.class && pts[1] == int.class
                            && List.class.isAssignableFrom(pts[4])) writers.add(m);
                }
                if (writers.isEmpty()) continue; // 当前 fm8 是 synthetic Transaction，不能当仓库捕获。
                repoCount++;
                for (Constructor<?> ctor : repo.getDeclaredConstructors()) {
                    hook(ctor).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object r = chain.proceed();
                            liveFm8 = chain.getThisObject();
                            return r;
                        }
                    });
                    ctorCount++;
                }
                for (Method writer : writers) {
                    hook(writer).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object[] args = chain.getArgs().toArray();
                            try {
                                liveFm8 = chain.getThisObject();
                                if (isNoCensor()) {
                                    String sid = args.length > 0 && args[0] instanceof String
                                            ? (String) args[0] : null;
                                    Object rows = args.length > 4 ? args[4] : null;
                                    int restored = ResponsePreserver.restoreRepositoryRows(cl, sid, rows);
                                    if (restored > 0) {
                                        log("restored preserved responses before history write=" + restored
                                                + " sid=" + sid);
                                    }
                                }
                                int cleaned = HistoryBridge.sanitizeRepositoryRows(args);
                                if (cleaned > 0) log("history repository prompts cleaned=" + cleaned);
                                preserveImagesBeforeLocalWrite(cl, chain.getThisObject(), args);
                            } catch (Throwable t) {
                                extLog("[HISTORY] repository preserve err: " + t + "\n" + stackToString(t));
                            }
                            return chain.proceed();
                        }
                    });
                    writeCount++;
                }
            } catch (Throwable ignored) {}
        }
        log("installed history repositories=" + repoCount + " ctor=" + ctorCount + " write=" + writeCount);

        try {
            Class<?> pw0 = HostCompat.load(cl, "pw0");
            int n = 0;
            for (Constructor<?> ctor : pw0.getDeclaredConstructors()) {
                hook(ctor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object r = chain.proceed();
                        try {
                            if (isNoCensor()) {
                                int restored = ResponsePreserver.restoreHistoryResponse(
                                        cl, chain.getThisObject());
                                if (restored > 0) {
                                    log("restored preserved responses in online history=" + restored);
                                }
                            }
                        } catch (Throwable t) {
                            extLog("[HISTORY] response restore err: " + t + "\n" + stackToString(t));
                        }
                        try {
                            // Capture the final form after expert relay restores FILE fragments
                            // and removes its internal vision-description text.
                            preserveImagesInHistoryResponse(cl, chain.getThisObject());
                        } catch (Throwable t) {
                            extLog("[HISTORY] pw0 image preserve err: " + t + "\n" + stackToString(t));
                        }
                        try {
                            int folded = foldProactiveHeartbeatHistory(chain.getThisObject());
                            if (folded > 0) {
                                log("folded internal proactive history turns=" + folded);
                            }
                        } catch (Throwable t) {
                            extLog("[HISTORY] proactive fold err: " + t + "\n"
                                    + stackToString(t));
                        }
                        try {
                            HistoryBridge.Result bridge = HistoryBridge.processHistoryResponse(chain.getThisObject());
                            if (bridge.cleaned > 0) log("online history prompts cleaned=" + bridge.cleaned);
                        }
                        catch (Throwable t) {
                            extLog("[HISTORY] pw0 bridge err: " + t + "\n" + stackToString(t));
                        }
                        return r;
                    }
                });
                n++;
            }
            log("installed online history bridge pw0 ctor x" + n);
        } catch (Throwable t) {
            log("installExpertHistoryImagePreserver pw0 failed: " + t);
        }
    }

    /**
     * A proactive completion is submitted to the real bound conversation so the resulting
     * assistant message remains part of that chat. The synthetic user event is transport-only:
     * remove it from every server-history response and connect its assistant child directly to
     * the previously visible message. Repeating this on every history load keeps the server's
     * canonical branch intact while ensuring the internal event is never rendered or persisted.
     */
    private static int foldProactiveHeartbeatHistory(Object historyResponse) {
        if (historyResponse == null) return 0;
        Object messagesValue = fieldByName(historyResponse, "b");
        if (!(messagesValue instanceof List)) return 0;
        List messages = (List) messagesValue;
        HashMap<Integer, Integer> hiddenParents = new HashMap<>();
        for (Object message : messages) {
            if (message == null
                    || !"USER".equals(String.valueOf(fieldByName(message, "h")))
                    || !messageContainsAnonymousHeartbeatEvent(message)) continue;
            Integer id = intField(message, "f");
            if (id != null) {
                hiddenParents.put(id, intField(message, "g"));
            }
        }
        if (hiddenParents.isEmpty()) return 0;

        ArrayList kept = new ArrayList(Math.max(0, messages.size() - hiddenParents.size()));
        for (Object message : messages) {
            Integer id = intField(message, "f");
            if (id != null && hiddenParents.containsKey(id)) continue;
            Integer parent = resolveVisibleHeartbeatParent(
                    intField(message, "g"), hiddenParents);
            Integer originalParent = intField(message, "g");
            if (originalParent == null ? parent != null : !originalParent.equals(parent)) {
                forceSetObjectField(message, "g", parent);
            }
            kept.add(message);
        }
        forceSetObjectField(historyResponse, "b", kept);

        Object session = fieldByName(historyResponse, "a");
        Integer current = intField(session, "d");
        Integer visibleCurrent = resolveVisibleHeartbeatParent(current, hiddenParents);
        if (current == null ? visibleCurrent != null : !current.equals(visibleCurrent)) {
            forceSetObjectField(session, "d", visibleCurrent);
        }
        return hiddenParents.size();
    }

    private static Integer resolveVisibleHeartbeatParent(
            Integer parent, Map<Integer, Integer> hiddenParents) {
        Integer result = parent;
        HashSet<Integer> seen = new HashSet<>();
        while (result != null && hiddenParents.containsKey(result) && seen.add(result)) {
            result = hiddenParents.get(result);
        }
        return result;
    }

    private static boolean messageContainsAnonymousHeartbeatEvent(Object message) {
        Object fragmentsValue = fieldByName(message, "t");
        if (!(fragmentsValue instanceof List)) {
            fragmentsValue = invokeNoArg(message, "l");
        }
        if (!(fragmentsValue instanceof List)) return false;
        for (Object fragment : (List) fragmentsValue) {
            boolean request = HostCompat.simpleNameIs(fragment, "xs7")
                    || "REQUEST".equals(String.valueOf(fieldByName(fragment, "a")));
            if (!request) continue;
            Object content = fieldByName(fragment, "c");
            if (!(content instanceof String)) continue;
            // Normal chat requests receive a system prompt that documents EVENT_START, so a
            // broad contains() check would erase the user's real message on the next history
            // sync. Only the post-system-wrapper body of a transport event may be folded.
            String body = HistoryBridge.stripInjectedSystemPrompts(
                    (String) content).trim();
            if (body.startsWith(HeartbeatToolProtocol.EVENT_START)
                    && body.indexOf(HeartbeatToolProtocol.EVENT_END,
                            HeartbeatToolProtocol.EVENT_START.length()) >= 0) {
                return true;
            }
        }
        return false;
    }

    // 3) 发送点捕获完整 List<fp>（图片唯一完整来源）及 tp.f() 当前会话模型。
    private void installExpertImageFpCapture(final ClassLoader cl) {
        hookSendPointFps(cl, "fu0", true);
        hookSendPointFps(cl, "uu0", false);
    }

    private void hookSendPointFps(final ClassLoader cl, final String cls, final boolean directList) {
        try {
            Class<?> c = HostCompat.load(cl, cls);
            final Method y = c.getDeclaredMethod("y", Object.class);
            hook(y).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    if (!isExpertRelayEnabled()) return chain.proceed();
                    tlPendingFps.remove();
                    tlPendingModel.remove();
                    try {
                        try {
                            List fps = null;
                            if (directList) {
                                Object v = fieldByName(chain.getThisObject(), "i");   // fu0.i = List<fp>
                                if (v instanceof List) fps = (List) v;
                            } else {
                                Object kv = fieldByName(chain.getThisObject(), "f");   // uu0.f = kv 消息
                                Object v = kv == null ? null : invokeNoArg(kv, "l");    // kv.l() = List<fp>
                                if (v instanceof List) fps = (List) v;
                            }
                            int imageCount = countImageFpList(fps);
                            if (imageCount > 0) {
                                String model = readSendPointModel(chain.getThisObject(), directList);
                                tlPendingFps.set(fps);
                                if (model != null) tlPendingModel.set(model);
                                extLog("[RELAY] send-point " + cls + " images=" + imageCount
                                        + " effectiveModel=" + model);
                            }
                        } catch (Throwable t) {
                            extLog("[RELAY] fp/model capture(" + cls + ") err: " + t);
                        }
                        return chain.proceed();
                    } finally {
                        // transport normally consumes both values synchronously; clear leftovers on every exit.
                        tlPendingFps.remove();
                        tlPendingModel.remove();
                    }
                }
            });
            log("installed send-point fp capture on "
                    + HostCompat.name(cls) + ".y");
        } catch (Throwable t) { log("hookSendPointFps " + cls + " failed: " + t); }
    }

    private static String readSendPointModel(Object sendPoint, boolean directList) {
        Object session = fieldByName(sendPoint, directList ? "g" : "h"); // fu0.g / uu0.h = tp
        Object model = session == null ? null : invokeNoArg(session, "f"); // tp.f() = current model
        return model instanceof String ? (String) model : null;
    }

    // 4) 捕获一个活着的 q71（completion PoW 管理器）实例
    private void installPowManagerCapture(ClassLoader cl) {
        try {
            Class<?> q71 = HostCompat.load(cl, "q71");
            int n = 0;
            for (Constructor<?> ctor : q71.getDeclaredConstructors()) {
                hook(ctor).intercept(new Hooker() {
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
                    hook(m).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            captureApiManagers(chain.getThisObject());
                            return chain.proceed();
                        }
                    });
                    n++;
                }
            }
            log("installed pow manager capture on q71 x" + n);
        } catch (Throwable t) { log("installPowManagerCapture failed: " + t); }
    }

    private static void captureApiManagers(Object q71) {
        if (q71 == null) return;
        boolean firstQ = liveQ71 == null;
        liveQ71 = q71;
        Object transport = fieldByName(q71, "f");
        boolean firstTransport = liveR92 == null && transport != null;
        if (transport != null) liveR92 = transport;
        if (firstQ || firstTransport) {
            extLog("[VP] captured API managers q71=" + (liveQ71 != null)
                    + " transport=" + (liveR92 != null));
        }
    }

    private static int countImageFpList(List fps) {
        if (fps == null) return 0;
        int n = 0;
        for (Object fp : fps) if (Boolean.TRUE.equals(fieldByName(fp, "k"))) n++;
        return n;
    }

    private static Object invokeNoArg(Object target, String name) {
        try {
            Method m = target.getClass().getMethod(
                    HostCompat.instanceMethod(target, name));
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) { return null; }
    }

    private void preserveImagesInHistoryResponse(ClassLoader cl, Object pw0) throws Throwable {
        if (!isExpertRelayEnabled() || pw0 == null) return;
        Object session = fieldByName(pw0, "a");
        String sid = stringField(session, "a");
        String model = stringField(session, "i");
        if (!isUsableSessionId(sid)) {
            extLog("[HISTORY] pw0 skip: sid 无效 model=" + String.valueOf(model));
            return;
        }

        Object messagesObj = fieldByName(pw0, "b");
        List messages = messagesObj instanceof List ? (List) messagesObj : null;
        boolean tracked = isTrackedExpertRelaySession(sid);
        boolean marker = historyMessagesContainRelayMarker(messages);
        extLog("[HISTORY] pw0 seen sid=" + sid + " model=" + String.valueOf(model)
                + " tracked=" + tracked + " marker=" + marker
                + " messages=" + (messages == null ? -1 : messages.size())
                + " liveFm8=" + (liveFm8 != null));
        if (!marker) {
            extLog("[HISTORY] pw0 scope skip sid=" + sid + " model=" + String.valueOf(model));
            return;
        }
        if (messages == null) {
            extLog("[HISTORY] pw0 skip: messages 不是 List sid=" + sid
                    + " actual=" + simpleName(messagesObj));
            return;
        }

        Object fm8 = liveFm8;
        if (fm8 == null) {
            extLog("[HISTORY] pw0 skip: fm8 尚未捕获 sid=" + sid);
            return;
        }
        Map<Integer, Object> localRows = indexLocalRows(readLocalRl8Rows(fm8, sid));
        boolean hasPersisted = relayImageFile(sid) != null && relayImageFile(sid).isFile();
        extLog("[HISTORY] pw0 local sid=" + sid + " rows=" + localRows.size()
                + " persistedImages=" + hasPersisted);
        if (localRows.isEmpty() && !hasPersisted) {
            extLog("[HISTORY] pw0 skip: 本地历史为空且无落盘图片 sid=" + sid);
            return;
        }

        int changed = 0;
        int imageFiles = 0;
        int candidates = 0;
        int detailLogs = 0;
        for (Object message : messages) {
            if (!HostCompat.simpleNameIs(message, "kv")) continue;
            Integer messageId = intField(message, "f");
            if (messageId == null) continue;
            Object serverObj = fieldByName(message, "t");
            List serverFragments = serverObj instanceof List ? (List) serverObj : Collections.emptyList();
            boolean messageMarker = fragmentListContainsRelayMarker(serverFragments);
            int serverImages = countImageFiles(serverFragments);
            if (!messageMarker) continue;

            Object oldRow = localRows.get(messageId);
            String oldJson = stringField(oldRow, "l");
            List oldFragments = decodeStaticFragments(cl, oldJson);
            int oldImages = oldFragments == null ? 0 : countImageFiles(oldFragments);
            if (oldImages == 0) {
                List persisted = loadPersistedImageFragments(cl, sid);
                if (persisted != null) { oldFragments = persisted; oldImages = countImageFiles(persisted); }
            }
            candidates++;
            if (detailLogs++ < 16) {
                extLog("[HISTORY] pw0 msg sid=" + sid + " id=" + messageId
                        + " relayMarker=" + messageMarker + " serverImages=" + serverImages
                        + " localRow=" + (oldRow != null)
                        + " localJsonLen=" + (oldJson == null ? 0 : oldJson.length())
                        + " imageSrc=" + oldImages);
            }
            if (serverImages > 0 || oldFragments == null || oldImages == 0) continue;

            ArrayList merged = mergeLocalImageFragments(serverFragments, oldFragments);
            if (forceSetObjectField(message, "t", merged)) {
                if (!tracked) {
                    rememberExpertRelaySession(sid, "pw0-verified-merge");
                    tracked = true;
                }
                changed++;
                imageFiles += oldImages;
                extLog("[HISTORY] 内存回填 sid=" + sid + " msg=" + messageId
                        + " images=" + oldImages + " fragments=" + merged.size());
            }
        }
        if (changed > 0) {
            extLog("[HISTORY] ✓ pw0 expert 图片保留完成 sid=" + sid
                    + " messages=" + changed + " images=" + imageFiles);
        } else {
            extLog("[HISTORY] pw0 done sid=" + sid + " candidates=" + candidates
                    + " changed=0");
        }
    }

    private void preserveImagesBeforeLocalWrite(ClassLoader cl, Object fm8, Object[] args) throws Throwable {
        if (!isExpertRelayEnabled() || fm8 == null || args == null || args.length < 7) return;
        Object sessionMeta = args[6];
        String model = stringField(sessionMeta, "k");
        String sid = args[0] instanceof String ? (String) args[0] : null;
        if (!isUsableSessionId(sid)) {
            extLog("[HISTORY] fm8 skip: sid 无效 model=" + String.valueOf(model));
            return;
        }
        List incomingRows = args[4] instanceof List ? (List) args[4] : null;
        boolean tracked = isTrackedExpertRelaySession(sid);
        Map<Object, List> decodedIncoming = new java.util.IdentityHashMap<>();
        boolean marker = false;
        if (incomingRows != null) {
            for (Object incoming : incomingRows) {
                if (!isHistoryPersistenceRow(incoming)) continue;
                String json = stringField(incoming, "l");
                if (!serializedMayContainRelayMarker(json)) continue;
                List fragments = decodeStaticFragments(cl, json);
                if (fragmentListContainsRelayMarker(fragments)) {
                    decodedIncoming.put(incoming, fragments);
                    marker = true;
                }
            }
        }
        extLog("[HISTORY] fm8 seen sid=" + sid + " model=" + String.valueOf(model)
                + " tracked=" + tracked + " marker=" + marker
                + " incoming=" + (incomingRows == null ? -1 : incomingRows.size()));
        if (incomingRows == null) {
            extLog("[HISTORY] fm8 skip: incoming 不是 List sid=" + sid
                    + " actual=" + simpleName(args[4]));
            return;
        }
        if (!marker) {
            extLog("[HISTORY] fm8 scope skip sid=" + sid + " model=" + String.valueOf(model));
            return;
        }

        Map<Integer, Object> localRows = indexLocalRows(readLocalRl8Rows(fm8, sid));
        boolean hasPersisted = relayImageFile(sid) != null && relayImageFile(sid).isFile();
        extLog("[HISTORY] fm8 local sid=" + sid + " rows=" + localRows.size()
                + " persistedImages=" + hasPersisted);
        if (localRows.isEmpty() && !hasPersisted) {
            extLog("[HISTORY] fm8 skip: 本地历史为空且无落盘图片 sid=" + sid);
            return;
        }
        int changed = 0;
        int candidates = 0;
        int detailLogs = 0;
        for (Object incoming : incomingRows) {
            if (!isHistoryPersistenceRow(incoming)) continue;
            Integer messageId = intField(incoming, "a");
            if (messageId == null) continue;
            List serverFragments = decodedIncoming.get(incoming);
            if (serverFragments == null) continue;
            boolean messageMarker = fragmentListContainsRelayMarker(serverFragments);
            int serverImages = countImageFiles(serverFragments);

            Object oldRow = localRows.get(messageId);
            String oldJson = stringField(oldRow, "l");
            List oldFragments = decodeStaticFragments(cl, oldJson);
            int oldImages = oldFragments == null ? 0 : countImageFiles(oldFragments);
            if (oldImages == 0) {
                List persisted = loadPersistedImageFragments(cl, sid);
                if (persisted != null) { oldFragments = persisted; oldImages = countImageFiles(persisted); }
            }
            candidates++;
            if (detailLogs++ < 16) {
                extLog("[HISTORY] fm8 msg sid=" + sid + " id=" + messageId
                        + " relayMarker=" + messageMarker + " serverImages=" + serverImages
                        + " localRow=" + (oldRow != null)
                        + " localJsonLen=" + (oldJson == null ? 0 : oldJson.length())
                        + " imageSrc=" + oldImages);
            }
            if (!messageMarker || serverImages > 0 || oldFragments == null || oldImages == 0) continue;

            ArrayList merged = mergeLocalImageFragments(serverFragments, oldFragments);
            String mergedJson = encodeStaticFragments(cl, merged);
            if (mergedJson == null || mergedJson.length() == 0) continue;
            if (forceSetObjectField(incoming, "l", mergedJson)) {
                if (!tracked) {
                    rememberExpertRelaySession(sid, "fm8-verified-merge");
                    tracked = true;
                }
                changed++;
                extLog("[HISTORY] 落库回填 sid=" + sid + " msg=" + messageId
                        + " images=" + oldImages + " jsonLen=" + mergedJson.length());
            }
        }
        if (changed > 0) {
            extLog("[HISTORY] ✓ fm8 expert 图片落库保护完成 sid=" + sid + " messages=" + changed);
        } else {
            extLog("[HISTORY] fm8 done sid=" + sid + " candidates=" + candidates
                    + " changed=0");
        }
    }

    private static boolean historyMessagesContainRelayMarker(List messages) {
        if (messages == null) return false;
        for (Object message : messages) {
            Object fragments = fieldByName(message, "t");
            if (fragments instanceof List && fragmentListContainsRelayMarker((List) fragments)) return true;
        }
        return false;
    }

    private static boolean serializedMayContainRelayMarker(String json) {
        return json != null && (json.contains(RELAY_PROMPT_MARKER)
                || json.contains(RELAY_PROMPT_MARKER_EN) || json.contains("\\u3010"));
    }

    private static boolean fragmentListContainsRelayMarker(List fragments) {
        if (fragments == null) return false;
        for (Object fragment : fragments) {
            boolean request = HostCompat.simpleNameIs(fragment, "xs7")
                    || "REQUEST".equals(String.valueOf(fieldByName(fragment, "a")));
            if (!request) continue;
            Object content = fieldByName(fragment, "c");
            if (content instanceof String && (((String) content).contains(RELAY_PROMPT_MARKER)
                    || ((String) content).contains(RELAY_PROMPT_MARKER_EN))) return true;
        }
        return false;
    }

    private static boolean isUsableSessionId(String sid) {
        return sid != null && sid.length() > 0 && !"null".equals(sid);
    }

    private static File relaySessionMarkerFile(String sid) {
        if (!isUsableSessionId(sid) || ".".equals(sid) || "..".equals(sid) || sid.length() > 160
                || !sid.matches("[A-Za-z0-9._-]+")) return null;
        return new File(EXPERT_RELAY_SESSION_DIR, sid);
    }

    private static boolean isTrackedExpertRelaySession(String sid) {
        if (!isUsableSessionId(sid)) return false;
        synchronized (expertRelaySessionIds) {
            if (expertRelaySessionIds.contains(sid)) return true;
        }
        File marker = relaySessionMarkerFile(sid);
        if (marker == null || !marker.isFile()) return false;
        synchronized (expertRelaySessionIds) {
            expertRelaySessionIds.add(sid);
        }
        return true;
    }

    private static void rememberExpertRelaySession(String sid, String source) {
        if (!isUsableSessionId(sid)) return;
        synchronized (expertRelaySessionIds) {
            expertRelaySessionIds.add(sid);
        }
        File marker = relaySessionMarkerFile(sid);
        if (marker == null) {
            extLog("[HISTORY] relay sid 仅内存登记（文件名不安全） source=" + source
                    + " sid=" + truncateForLog(sid, 80));
            return;
        }
        try {
            overwriteTextFile(marker.getAbsolutePath(), sid);
            extLog("[HISTORY] relay sid 已登记 source=" + source + " sid=" + sid);
        } catch (Throwable t) {
            extLog("[HISTORY] relay sid 落盘失败 source=" + source + " sid=" + sid + ": " + t);
        }
    }

    private static File relayImageFile(String sid) {
        if (!isUsableSessionId(sid) || ".".equals(sid) || "..".equals(sid) || sid.length() > 160
                || !sid.matches("[A-Za-z0-9._-]+")) return null;
        return new File(RELAY_IMAGE_DIR, sid + ".json");
    }

    private void persistRelayImages(ClassLoader cl, String sid, Object expertReq) {
        List fps = ew0Fps.remove(expertReq);
        if (fps == null) { extLog("[HISTORY] persistImages skip: 无捕获 fp sid=" + sid); return; }
        ArrayList imageFps = new ArrayList();
        for (Object fp : fps) if (Boolean.TRUE.equals(fieldByName(fp, "k"))) imageFps.add(fp);
        if (imageFps.isEmpty()) { extLog("[HISTORY] persistImages skip: 无图片 fp sid=" + sid); return; }
        File out = relayImageFile(sid);
        if (out == null) { extLog("[HISTORY] persistImages skip: sid 文件名不安全 sid=" + truncateForLog(sid, 80)); return; }
        try {
            Class<?> fileFragment = HostCompat.load(cl, "rs7");
            Constructor<?> ctor;
            Object frag;
            if (HostCompat.isV230()) {
                ctor = fileFragment.getDeclaredConstructor(
                        int.class, String.class, List.class);
                ctor.setAccessible(true);
                frag = ctor.newInstance(1, "FILE", imageFps);
            } else {
                ctor = fileFragment.getDeclaredConstructor(List.class);
                ctor.setAccessible(true);
                frag = ctor.newInstance(imageFps);
            }
            String json = encodeStaticFragments(cl, java.util.Collections.singletonList(frag));
            if (json == null || json.length() == 0) { extLog("[HISTORY] persistImages 编码失败 sid=" + sid); return; }
            File dir = out.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            overwriteTextFile(out.getAbsolutePath(), json);
            extLog("[HISTORY] persistImages ✓ sid=" + sid + " images=" + imageFps.size()
                    + " jsonLen=" + json.length());
            for (int i = 0; i < imageFps.size(); i++) {
                extLog("[HISTORY] persistImages fp[" + i + "]=" + summarizeFp(imageFps.get(i)));
            }
        } catch (Throwable t) { extLog("[HISTORY] persistImages err sid=" + sid + ": " + t); }
    }

    private static List loadPersistedImageFragments(ClassLoader cl, String sid) {
        File f = relayImageFile(sid);
        if (f == null || !f.isFile()) return null;
        try {
            String json = readSmallText(f.getAbsolutePath());
            List frags = decodeStaticFragments(cl, json);
            if (frags == null || countImageFiles(frags) == 0) return null;
            return frags;
        } catch (Throwable t) { extLog("[HISTORY] loadPersistedImages err sid=" + sid + ": " + t); return null; }
    }

    private static ArrayList readLocalRl8Rows(Object fm8, String sid) throws Throwable {
        if (fm8 == null || sid == null) return new ArrayList();
        Method tableForSession = fm8.getClass().getDeclaredMethod("a", String.class);
        tableForSession.setAccessible(true);
        Object sl8 = tableForSession.invoke(fm8, sid);
        Object table = fieldByName(sl8, "b");
        if (table == null) return new ArrayList();

        Object binding = fieldByName(table, "d");
        if (binding == null) return new ArrayList();
        Method allColumns = binding.getClass().getDeclaredMethod("c");
        allColumns.setAccessible(true);
        Object columns = allColumns.invoke(binding);
        if (columns == null || !columns.getClass().isArray()) return new ArrayList();

        Method selectFactory = table.getClass().getDeclaredMethod("U");
        selectFactory.setAccessible(true);
        Object select = selectFactory.invoke(table);
        Method selectColumns = select.getClass().getDeclaredMethod("z", columns.getClass());
        selectColumns.setAccessible(true);
        selectColumns.invoke(select, new Object[]{columns});
        Method allRows = select.getClass().getDeclaredMethod("x");
        allRows.setAccessible(true);
        Object rows = allRows.invoke(select);
        return rows instanceof ArrayList ? (ArrayList) rows : new ArrayList();
    }

    private static Map<Integer, Object> indexLocalRows(List rows) {
        HashMap<Integer, Object> out = new HashMap<>();
        if (rows == null) return out;
        for (Object row : rows) {
            Integer id = intField(row, "a");
            if (id != null) out.put(id, row);
        }
        return out;
    }

    private static List decodeStaticFragments(ClassLoader cl, String json) {
        if (cl == null || json == null || json.trim().length() == 0) return null;
        try {
            Class<?> ch4 = HostCompat.load(cl, "ch4");
            Class<?> x94 = HostCompat.load(cl, "x94");
            Field jsonField = x94.getDeclaredField("a");
            jsonField.setAccessible(true);
            Object jsonCodec = jsonField.get(null);
            Class<?> xv0 = HostCompat.load(cl, "xv0");
            Field serializerField = xv0.getDeclaredField("a");
            serializerField.setAccessible(true);
            Object serializer = serializerField.get(null);
            Method decode = jsonCodec.getClass().getMethod("b", ch4, String.class);
            decode.setAccessible(true);
            Object wrapper = decode.invoke(jsonCodec, serializer, json);
            Object list = fieldByName(wrapper, "a");
            return list instanceof List ? (List) list : null;
        } catch (Throwable t) {
            extLog("[HISTORY] fragments decode skip: " + t + " json=" + truncateForLog(json, 180));
            return null;
        }
    }

    private static String encodeStaticFragments(ClassLoader cl, List fragments) {
        if (cl == null || fragments == null) return null;
        try {
            Class<?> ch4 = HostCompat.load(cl, "ch4");
            Class<?> x94 = HostCompat.load(cl, "x94");
            Field jsonField = x94.getDeclaredField("a");
            jsonField.setAccessible(true);
            Object jsonCodec = jsonField.get(null);
            Class<?> xv0 = HostCompat.load(cl, "xv0");
            Field serializerField = xv0.getDeclaredField("a");
            serializerField.setAccessible(true);
            Object serializer = serializerField.get(null);
            Class<?> zv0 = HostCompat.load(cl, "zv0");
            Constructor<?> wrapperCtor = zv0.getDeclaredConstructor(List.class);
            wrapperCtor.setAccessible(true);
            Object wrapper = wrapperCtor.newInstance(fragments);
            Method encode = jsonCodec.getClass().getMethod("c", ch4, Object.class);
            encode.setAccessible(true);
            return String.valueOf(encode.invoke(jsonCodec, serializer, wrapper));
        } catch (Throwable t) {
            extLog("[HISTORY] fragments encode skip: " + t);
            return null;
        }
    }

    private static ArrayList mergeLocalImageFragments(List serverFragments, List oldFragments) {
        ArrayList merged = new ArrayList();
        if (serverFragments != null) merged.addAll(serverFragments);
        stripRelayDescriptionText(merged);
        HashSet<Integer> usedIds = new HashSet<>();
        int nextId = 1;
        for (Object fragment : merged) {
            Integer id = intField(fragment, "b");
            if (id == null) continue;
            usedIds.add(id);
            if (id.intValue() >= nextId) nextId = id.intValue() + 1;
        }
        int insertAt = 0;
        while (insertAt < merged.size() && isFileFragment(merged.get(insertAt))) insertAt++;
        if (oldFragments != null) {
            for (Object fragment : oldFragments) {
                if (!retainOnlyImageFiles(fragment)) continue;
                Integer id = intField(fragment, "b");
                if (id == null || usedIds.contains(id)) {
                    while (usedIds.contains(Integer.valueOf(nextId))) nextId++;
                    id = Integer.valueOf(nextId++);
                    if (!forceSetObjectField(fragment, "b", id)) continue;
                }
                usedIds.add(id);
                merged.add(insertAt++, fragment);
            }
        }
        return merged;
    }

    private static void stripRelayDescriptionText(List fragments) {
        if (fragments == null) return;
        for (Object fragment : fragments) {
            boolean request = HostCompat.simpleNameIs(fragment, "xs7")
                    || "REQUEST".equals(String.valueOf(fieldByName(fragment, "a")));
            if (!request) continue;
            Object content = fieldByName(fragment, "c");
            if (!(content instanceof String)) continue;
            String text = (String) content;
            int zhIndex = text.indexOf(RELAY_PROMPT_MARKER);
            int enIndex = text.indexOf(RELAY_PROMPT_MARKER_EN);
            int idx = zhIndex < 0 ? enIndex : (enIndex < 0 ? zhIndex : Math.min(zhIndex, enIndex));
            if (idx < 0) continue;
            String kept = text.substring(0, idx);
            kept = stripInjectedSystemPrompt(kept);
            int nl = kept.length();
            while (nl > 0 && (kept.charAt(nl - 1) == '\n' || kept.charAt(nl - 1) == '\r'
                    || kept.charAt(nl - 1) == ' ')) nl--;
            kept = kept.substring(0, nl);
            forceSetObjectField(fragment, "c", kept);
        }
    }

    private static String stripInjectedSystemPrompt(String text) {
        return HistoryBridge.stripInjectedSystemPrompts(text);
    }

    private static boolean isHistoryPersistenceRow(Object row) {
        if (row == null) return false;
        String name = simpleName(row);
        if ("rl8".equals(name) || "sl8".equals(name)) return true;
        return intField(row, "a") != null && fieldByName(row, "l") instanceof String;
    }

    private static boolean retainOnlyImageFiles(Object fragment) {
        if (!isFileFragment(fragment)) return false;
        Object filesObj = fieldByName(fragment, "c");
        if (!(filesObj instanceof List)) return false;
        List files = (List) filesObj;
        ArrayList images = new ArrayList();
        for (Object file : files) {
            if (Boolean.TRUE.equals(fieldByName(file, "k"))) images.add(file);
        }
        if (images.isEmpty()) return false;
        return images.size() == files.size() || forceSetObjectField(fragment, "c", images);
    }

    private static int countImageFiles(List fragments) {
        if (fragments == null) return 0;
        int count = 0;
        for (Object fragment : fragments) count += countImageFilesInFragment(fragment);
        return count;
    }

    private static int countImageFilesInFragment(Object fragment) {
        if (!isFileFragment(fragment)) return 0;
        Object filesObj = fieldByName(fragment, "c");
        if (!(filesObj instanceof List)) return 0;
        int count = 0;
        for (Object file : (List) filesObj) {
            if (Boolean.TRUE.equals(fieldByName(file, "k"))) count++;
        }
        return count;
    }

    private static boolean isFileFragment(Object fragment) {
        if (fragment == null) return false;
        if (HostCompat.simpleNameIs(fragment, "rs7")) return true;
        return "FILE".equals(String.valueOf(fieldByName(fragment, "a")));
    }

    private static Integer intField(Object obj, String name) {
        Object value = fieldByName(obj, name);
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }

    private static boolean forceSetObjectField(Object obj, String name, Object value) {
        if (obj == null) return false;
        name = HostCompat.staticMessageField(obj, name);
        try {
            Field field = obj.getClass().getDeclaredField(name);
            field.setAccessible(true);
            try {
                if (field.getType() == int.class && value instanceof Number) {
                    field.setInt(obj, ((Number) value).intValue());
                } else {
                    field.set(obj, value);
                }
                return true;
            } catch (Throwable reflectionFailure) {
                Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
                Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                Object unsafe = unsafeField.get(null);
                long offset = ((Number) unsafeClass.getMethod("objectFieldOffset", Field.class)
                        .invoke(unsafe, field)).longValue();
                if (field.getType() == int.class && value instanceof Number) {
                    unsafeClass.getMethod("putInt", Object.class, long.class, int.class)
                            .invoke(unsafe, obj, offset, ((Number) value).intValue());
                } else if (!field.getType().isPrimitive()) {
                    unsafeClass.getMethod("putObject", Object.class, long.class, Object.class)
                            .invoke(unsafe, obj, offset, value);
                } else {
                    extLog("[HISTORY] forceSet unsupported primitive " + field.getType().getName()
                            + " for " + simpleName(obj) + "." + name);
                    return false;
                }
                return true;
            }
        } catch (Throwable t) {
            extLog("[HISTORY] forceSet " + simpleName(obj) + "." + name + " failed: " + t);
            return false;
        }
    }

    // ── ★正式功能：expert 模式带图 → 后台视觉描述中继（同步就地改写请求）────────
    private boolean relayGateMatches(Object reqObj) {
        if (!isExpertRelayEnabled()) return false;
        if (reqObj == null) return false;
        // One-shot association: every transport call consumes the send-point model captured for this request.
        String capturedModel = ew0EffectiveModels.remove(reqObj);
        if (!HostCompat.simpleNameIs(reqObj, "ew0")) return false;
        Object files = fieldByName(reqObj, "d");
        boolean hasFiles = files instanceof java.util.List && !((java.util.List) files).isEmpty();
        Object explicitModel = fieldByName(reqObj, "i");
        boolean matches = ExpertRelayGate.matches(explicitModel, capturedModel, hasFiles);
        if (matches && explicitModel == null) {
            extLog("[RELAY] 续轮 model_type=null，使用发送点 effectiveModel=" + capturedModel
                    + " req=" + System.identityHashCode(reqObj)
                    + " parent=" + (fieldByName(reqObj, "b") != null)
                    + " files=" + ((List) files).size());
        }
        return matches;
    }

    // 已登记待中继的冷 Flow(b41 实例) -> {expertReq, r92}。等下游 collect(b41.b) 时才跑中继。
    private final java.util.Map<Object, Object[]> relayFlowMap =
            new java.util.IdentityHashMap<Object, Object[]>();

    // 命中 expert+图片时：不改返回值(避免 libxposed 把 Proxy 强转 b41 而 CCE)，
    // 只把真实 b41 实例登记下来，交给 b41.b 的 collect hook 处理。
    private void registerRelayFlow(Object reqObj, Object flow, Object r92This) {
        if (!relayGateMatches(reqObj)) return;
        synchronized (relaySeen) {
            if (relaySeen.contains(reqObj)) return;
            relaySeen.add(reqObj);
        }
        final Object r92 = (r92This != null) ? r92This : liveR92;
        if (r92 == null || flow == null) { extLog("[RELAY] register skip: r92/flow null"); return; }
        synchronized (relayFlowMap) { relayFlowMap.put(flow, new Object[]{ reqObj, r92 }); }
        extLog("[RELAY] 已登记冷 Flow=" + System.identityHashCode(flow)
                + "，等下游 collect(b41.b) 时跑中继");
    }

    // hook b41.b(q03,uz1)=Flow.collect。返回类型是 Object，返回真实 Flow 不会触发返回值强转。
    // 仅当 this 是已登记的 expert 带图冷 Flow 时介入；否则原样放行(热路径，identity 命中开销 O(1))。
    private void installExpertFlowCollectHook(ClassLoader cl) {
        try {
            Class<?> b41 = HostCompat.load(cl, "b41");
            Class<?> q03 = HostCompat.load(cl, "q03");
            Method bColl = null;
            for (Method m : b41.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (m.getName().equals("b") && p.length == 2 && p[0] == q03) { bColl = m; break; }
            }
            if (bColl == null) { log("expert flow collect hook: b41.b(q03,uz1) 未找到"); return; }
            hook(bColl).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object self = chain.getThisObject();
                    Object[] entry;
                    synchronized (relayFlowMap) { entry = relayFlowMap.remove(self); }
                    if (entry == null) return chain.proceed();          // 非中继流，原样放行
                    Object[] a = chain.getArgs().toArray();
                    Object collector = a.length > 0 ? a[0] : null;
                    Object cont = a.length > 1 ? a[1] : null;
                    final Object expertReq = entry[0];
                    final Object r92 = entry[1];
                    try {
                        if (Looper.getMainLooper() != null
                                && Looper.getMainLooper().getThread() == Thread.currentThread()) {
                            // 主线程不能阻塞跑中继(7s 网络=ANR)，直接转发原流(服务端会拒但不闪退)
                            extLog("[RELAY] collect 在主线程，跳过中继直接转发原 Flow");
                            return chain.proceed();
                        }
                        extLog("[RELAY] collect 命中(flow=" + System.identityHashCode(self)
                                + " thread=" + Thread.currentThread().getName() + ")，开始中继");
                        runExpertImageRelay(r92, expertReq);
                        // 用改写后的 expertReq 重建一个新冷 Flow，collect 它(而不是带图的原流)
                        Object freshFlow = null;
                        Method bM = null;
                        for (Method mm : r92.getClass().getDeclaredMethods()) {
                            if (mm.getName().equals("b") && mm.getParameterTypes().length == 2) { bM = mm; break; }
                        }
                        if (bM != null) { bM.setAccessible(true); freshFlow = bM.invoke(r92, expertReq, null); }
                        if (freshFlow == null) {
                            extLog("[RELAY] 重取 expert Flow 失败，转发原 Flow");
                            return chain.proceed();
                        }
                        // freshFlow 也是 b41，反射调用其 b() 会再次进本 hook；但它未登记 → 直接放行原始 collect
                        return bCollInvoke(chain, freshFlow, collector, cont);
                    } catch (Throwable t) {
                        extLog("[RELAY] collect 中继异常，转发原 Flow: " + t + "\n" + stackToString(t));
                        return chain.proceed();
                    }
                }
            });
            log("installed expert flow collect hook on b41.b x1");
        } catch (Throwable t) { log("installExpertFlowCollectHook failed: " + t); }
    }

    private Object bCollInvoke(Chain chain, Object flow, Object collector, Object cont) throws Throwable {
        Method m = (Method) chain.getExecutable();
        m.setAccessible(true);
        return m.invoke(flow, collector, cont);
    }

    private String describeOneImage(Object r92, ClassLoader cl, Object expertReq,
                                    List fileIds, String label, long t0) {
        String sid = null;
        try {
            Object pow = mintCompletionPow(cl, liveQ71);
            if (!(pow instanceof String) || ((String) pow).length() == 0) {
                extLog("[RELAY]" + label + " 铸 PoW 失败；abort"); return null;
            }
            sid = createThrowawaySession(cl, r92);
            if (sid == null) { extLog("[RELAY]" + label + " 建临时会话失败；abort"); return null; }
            extLog("[RELAY]" + label + " 临时会话=" + sid
                    + " (setup " + (System.currentTimeMillis() - t0) + "ms)");
            Object visionReq = shallowCloneEw0(expertReq);
            if (visionReq == null) { extLog("[RELAY]" + label + " clone 失败；abort"); return null; }
            setFieldByName(visionReq, "a", sid);
            setFieldByName(visionReq, "b", null);
            setFieldByName(visionReq, "c", visionDescribePrompt());
            setFieldByName(visionReq, "i", "vision");
            setFieldByName(visionReq, "e", Boolean.FALSE);
            setFieldByName(visionReq, "f", Boolean.FALSE);
            setFieldByName(visionReq, "k", pow);
            if (fileIds != null) setFieldByName(visionReq, "d", new ArrayList(fileIds));

            Method bM = null;
            for (Method m : r92.getClass().getDeclaredMethods()) {
                if (m.getName().equals("b") && m.getParameterTypes().length == 2) { bM = m; break; }
            }
            if (bM == null) { extLog("[RELAY]" + label + " r92.b 未找到；abort"); return null; }
            bM.setAccessible(true);
            Object flow = bM.invoke(r92, visionReq, null);
            if (flow == null) { extLog("[RELAY]" + label + " vision r92.b 返回 null；abort"); return null; }
            String desc = collectFlow(cl, flow);
            extLog("[RELAY]" + label + " 描述 len=" + (desc == null ? 0 : desc.length())
                    + " total=" + (System.currentTimeMillis() - t0) + "ms : "
                    + truncateForLog(String.valueOf(desc), 240));
            return desc;
        } catch (Throwable t) {
            extLog("[RELAY]" + label + " describeOneImage threw: " + t);
            return null;
        } finally {
            if (sid != null) {
                try {
                    boolean del = deleteThrowawaySession(cl, r92, sid);
                    extLog("[RELAY]" + label + " 删除临时会话 " + sid + " -> " + del);
                } catch (Throwable t) { extLog("[RELAY]" + label + " 删除临时会话失败: " + t); }
            }
        }
    }

    private String describeImagesParallel(final Object r92, final ClassLoader cl,
                                          final Object expertReq, final List<String> fileIds,
                                          final long t0) {
        final int n = fileIds.size();
        final String[] results = new String[n];
        Thread[] threads = new Thread[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            final String fileId = fileIds.get(i);
            threads[i] = new Thread(new Runnable() {
                public void run() {
                    results[idx] = describeOneImage(r92, cl, expertReq,
                            java.util.Collections.singletonList(fileId), " 图" + (idx + 1), t0);
                }
            });
            threads[i].start();
        }
        for (int i = 0; i < n; i++) {
            try { threads[i].join(120000); } catch (Throwable ignored) {}
        }
        StringBuilder sb = new StringBuilder();
        int ok = 0;
        for (int i = 0; i < n; i++) {
            String d = results[i];
            if (d == null || d.trim().length() == 0) continue;
            if (sb.length() > 0) sb.append("\n\n");
            sb.append("图").append(i + 1).append("：\n").append(d.trim());
            ok++;
        }
        extLog("[RELAY] 并行描述完成 images=" + n + " ok=" + ok
                + " total=" + (System.currentTimeMillis() - t0) + "ms");
        return sb.length() > 0 ? sb.toString() : null;
    }

    private void runExpertImageRelay(Object r92, Object expertReq) throws Throwable {
        if (r92 == null) { extLog("[RELAY] no live r92; abort"); return; }
        final ClassLoader cl = r92.getClass().getClassLoader();
        long t0 = System.currentTimeMillis();

        if (liveQ71 == null) { extLog("[RELAY] liveQ71 未捕获；abort（保持带图 expert 不动）"); return; }

        Object dOld0 = fieldByName(expertReq, "d");
        ArrayList<String> fileIds = new ArrayList<String>();
        if (dOld0 instanceof List) {
            for (Object o : (List) dOld0) if (o != null) fileIds.add(String.valueOf(o));
        }

        String desc;
        if (fileIds.size() <= 1) {
            desc = describeOneImage(r92, cl, expertReq,
                    fileIds.isEmpty() ? null : fileIds, "", t0);
        } else {
            desc = describeImagesParallel(r92, cl, expertReq, fileIds, t0);
        }

        if (desc != null && desc.trim().length() > 0) {
            Object cOld = fieldByName(expertReq, "c");
            Object dOld = fieldByName(expertReq, "d");
            ArrayList filesOld = dOld instanceof List ? new ArrayList((List) dOld) : null;
            String newC = String.valueOf(cOld) + "\n\n" + relayPromptMarker() + "\n" + desc.trim();
            setFieldByName(expertReq, "c", newC);
            if (dOld instanceof java.util.List) {
                try { ((java.util.List) dOld).clear(); }
                catch (Throwable t) { setFieldByName(expertReq, "d", new java.util.ArrayList()); }
            } else {
                setFieldByName(expertReq, "d", new java.util.ArrayList());
            }
            Object cAfter = fieldByName(expertReq, "c");
            Object dAfter = fieldByName(expertReq, "d");
            boolean promptOk = newC.equals(cAfter);
            boolean filesOk = dAfter instanceof List && ((List) dAfter).isEmpty();
            if (promptOk && filesOk) {
                String relaySid = stringField(expertReq, "a");
                rememberExpertRelaySession(relaySid, "relay-success");
                try { persistRelayImages(cl, relaySid, expertReq); }
                catch (Throwable t) { extLog("[RELAY] persistRelayImages err: " + t); }
                extLog("[RELAY] ✓ expert 已改写为纯文本，newPromptLen=" + newC.length()
                        + " 文件已清空");
            } else {
                setFieldByName(expertReq, "c", cOld);
                if (dOld instanceof List) {
                    try {
                        ((List) dOld).clear();
                        ((List) dOld).addAll(filesOld);
                        setFieldByName(expertReq, "d", dOld);
                    } catch (Throwable ignored) {
                        setFieldByName(expertReq, "d", filesOld);
                    }
                } else {
                    setFieldByName(expertReq, "d", dOld);
                }
                extLog("[RELAY] expert 改写校验失败，已尝试恢复原请求 promptOk="
                        + promptOk + " filesOk=" + filesOk);
            }
        } else {
            extLog("[RELAY] 描述为空；保持带图 expert 不动（服务端仍会拒，与未开启前一致）");
        }
    }

    private LocalApiGateway.CompletionResult executeLocalApiCompletion(
            LocalApiGateway.CompletionRequest request,
            LocalApiGateway.DeltaSink sink) throws Exception {
        final long requestStarted = System.currentTimeMillis();
        final long deadline = request != null && request.deadlineAtMs > 0L
                ? request.deadlineAtMs : requestStarted + LOCAL_API_REQUEST_BUDGET_MS;
        tlLocalApiDeadline.set(deadline);
        if (request == null || request.prompt == null || request.prompt.trim().length() == 0) {
            tlLocalApiDeadline.remove();
            throw new LocalApiGateway.GatewayException(400, "empty_prompt",
                    "Prompt translated to an empty string");
        }
        if (!isLocalApiEnabled()
                && !Boolean.TRUE.equals(tlProactiveHeartbeatRequest.get())) {
            tlLocalApiDeadline.remove();
            throw new LocalApiGateway.GatewayException(503, "gateway_disabled",
                    "server_error", "Local API has been disabled");
        }
        ClassLoader cl = hostClassLoader;
        Object transport = liveR92;
        Object powManager = liveQ71;
        if (cl == null || transport == null || powManager == null) {
            tlLocalApiDeadline.remove();
            throw new LocalApiGateway.GatewayException(503, "host_not_ready",
                    "server_error", "DeepSeek native transport is still initializing");
        }
        tlLocalApiSink.set(sink);

        final boolean agentRequest = request.agentic();
        if (agentRequest) LOCAL_API_AGENT_WAITERS.incrementAndGet();
        boolean acquired = false;
        try {
            acquired = awaitLocalApiCompletionSlot(request);
        } catch (LocalApiGateway.GatewayException e) {
            if (agentRequest) LOCAL_API_AGENT_WAITERS.decrementAndGet();
            tlLocalApiDeadline.remove();
            tlLocalApiSink.remove();
            throw e;
        }
        if (!acquired) {
            if (agentRequest) LOCAL_API_AGENT_WAITERS.decrementAndGet();
            tlLocalApiDeadline.remove();
            tlLocalApiSink.remove();
            if (request.auxiliary() && !request.agentic()) {
                LocalApiGateway.diagnostic("AUXILIARY_SKIPPED id=" + request.requestId
                        + " reason=native_lane_busy");
                return new LocalApiGateway.CompletionResult("", "", "stop");
            }
            throw new LocalApiGateway.GatewayException(429, "too_many_requests",
                    "rate_limit_error", "The native completion lane is busy; retry shortly");
        }

        long started = requestStarted;
        try {
            ensureLocalApiTime("starting the native request");
            // Ordinary local-API calls reuse a hidden session. A proactive heartbeat instead
            // targets the chat that scheduled it and supplies that chat's current server head;
            // this makes the assistant message a real member of the original conversation.
            boolean boundNativeConversation =
                    isUsableSessionId(request.nativeConversationId)
                            && request.nativeParentMessageId != null
                            && request.nativeParentMessageId.intValue() > 0;
            String sessionKey = boundNativeConversation
                    ? null : localApiSessionKey(request);
            String sid = boundNativeConversation
                    ? request.nativeConversationId
                    : reusableApiSession(cl, transport, sessionKey);
            long[] retryWaits = {0L, 1500L, 3500L};
            LocalApiGateway.GatewayException last = null;
            for (int attempt = 0; attempt < retryWaits.length; attempt++) {
                if (retryWaits[attempt] > 0L) {
                    sleepLocalApi(retryWaits[attempt], "retrying DeepSeek transport");
                }
                ensureLocalApiTime("preparing DeepSeek transport");
                final boolean[] emitted = {false};
                LocalApiGateway.DeltaSink trackedSink = sink == null ? null
                        : new LocalApiGateway.DeltaSink() {
                    @Override public void onUpstreamStarted() throws Exception {
                        sink.onUpstreamStarted();
                    }
                    @Override public boolean onText(String delta) throws Exception {
                        if (delta != null && delta.length() > 0) emitted[0] = true;
                        return sink.onText(delta);
                    }
                    @Override public boolean onReasoning(String delta) throws Exception {
                        if (delta != null && delta.length() > 0) emitted[0] = true;
                        return sink.onReasoning(delta);
                    }
                    @Override public boolean isCancelled() { return sink.isCancelled(); }
                    @Override public boolean isSatisfied() { return sink.isSatisfied(); }
                };
                try {
                    awaitLocalApiNativeStart();
                    String pow = mintApiPowWithRetry(cl, powManager);
                    LocalApiGateway.CompletionResult result = executeNativeApiCompletionOnce(
                            cl, transport, sid, request, pow, trackedSink);
                    resetLocalApiRateLimitStreak();
                    log("[LOCAL_API] native completion id=" + request.requestId
                            + " model=" + request.nativeModel
                            + " thinking=" + request.reasoning
                            + " attempt=" + (attempt + 1)
                            + " text_chars=" + result.text.length()
                            + " reasoning_chars=" + result.reasoning.length()
                            + " ms=" + (System.currentTimeMillis() - started));
                    // A collector exception cancels a captured tool generation locally, but the
                    // server needs a short grace period to clear parallel_chat_limit state.
                    extendLocalApiCooldown("tool_calls".equals(result.finishReason)
                            ? 1800L : 500L);
                    return result;
                } catch (LocalApiGateway.GatewayException e) {
                    last = e;
                    if ("invalid_api_session".equals(e.code)) {
                        if (boundNativeConversation) throw e;
                        invalidateReusableApiSession(sessionKey, sid);
                        sid = reusableApiSession(cl, transport, sessionKey);
                        LocalApiGateway.diagnostic("SESSION_RECREATED id="
                                + request.requestId + " model=" + request.nativeModel);
                    }
                    boolean nativeBusy = "upstream_rate_limit".equals(e.code)
                            || isNativeBusyLimit(e.getMessage());
                    if (nativeBusy) {
                        extendLocalApiRateLimitCooldown();
                    }
                    // A second parallel-chat rejection means the prior native generation has not
                    // released yet. Long 15/25/40-second retries used to hold this permit and
                    // turn one stale request into a minutes-long queue for every later request.
                    if (nativeBusy && attempt >= 1) throw e;
                    if (emitted[0] || !isTransientApiFailure(e)
                            || attempt + 1 >= retryWaits.length) throw e;
                    log("[LOCAL_API] transient completion retry id=" + request.requestId
                            + " attempt=" + (attempt + 1) + " code=" + e.code
                            + " reason=" + safeThrowableMessage(e));
                    LocalApiGateway.diagnostic("NATIVE_RETRY id=" + request.requestId
                            + " attempt=" + (attempt + 1) + " code=" + e.code
                            + " reason=" + safeThrowableMessage(e));
                }
            }
            throw last == null ? new LocalApiGateway.GatewayException(502,
                    "upstream_retry_exhausted", "server_error",
                    "DeepSeek transport retry exhausted") : last;
        } finally {
            LOCAL_API_COMPLETION_SLOTS.release();
            if (agentRequest) {
                LOCAL_API_AGENT_WAITERS.decrementAndGet();
                localApiAgentPriorityUntil = Math.max(localApiAgentPriorityUntil,
                        System.currentTimeMillis() + 1200L);
            }
            tlLocalApiDeadline.remove();
            tlLocalApiSink.remove();
            scheduleReusableApiSessionMaintenance();
        }
    }

    private static boolean awaitLocalApiCompletionSlot(
            LocalApiGateway.CompletionRequest request)
            throws LocalApiGateway.GatewayException {
        boolean auxiliary = request != null && request.auxiliary() && !request.agentic();
        long maxWait = auxiliary ? LOCAL_API_AUX_QUEUE_WAIT_MS
                : (request != null && request.agentic()
                        ? LOCAL_API_AGENT_QUEUE_WAIT_MS : LOCAL_API_CHAT_QUEUE_WAIT_MS);
        long started = System.currentTimeMillis();
        long waitUntil = started
                + Math.min(maxWait, Math.max(0L, remainingLocalApiTimeMs() - 1000L));
        while (System.currentTimeMillis() < waitUntil) {
            ensureLocalApiClientActive("waiting for the native completion lane");
            long now = System.currentTimeMillis();
            boolean agentHasPriority = auxiliary && (LOCAL_API_AGENT_WAITERS.get() > 0
                    || now < localApiAgentPriorityUntil);
            if (!agentHasPriority) {
                long slice = Math.min(LOCAL_API_QUEUE_POLL_MS, waitUntil - now);
                try {
                    if (LOCAL_API_COMPLETION_SLOTS.tryAcquire(
                            Math.max(1L, slice), TimeUnit.MILLISECONDS)) {
                        if (auxiliary) {
                            localApiNextAuxiliaryStartAt = System.currentTimeMillis() + 4000L;
                        }
                        if (System.currentTimeMillis() > started) {
                            LocalApiGateway.diagnostic("NATIVE_QUEUE_WAIT id="
                                    + request.requestId + " wait_ms="
                                    + (System.currentTimeMillis() - started));
                        }
                        return true;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LocalApiGateway.GatewayException(503, "request_interrupted",
                            "server_error", "Request was interrupted before execution");
                }
            } else {
                sleepLocalApi(Math.min(LOCAL_API_QUEUE_POLL_MS, waitUntil - now),
                        "prioritizing the interactive Agent turn");
            }
        }
        return false;
    }

    private static String localApiSessionKey(LocalApiGateway.CompletionRequest request) {
        String model = request == null || request.nativeModel == null
                || request.nativeModel.length() == 0 ? "default" : request.nativeModel;
        String lane = "#chat";
        if (request != null && request.auxiliary()) {
            lane = request.agentic() ? "#aux-agent" : "#aux";
        } else if (request != null && request.agentic()) {
            lane = "#agent";
        }
        String scope = request == null ? null : request.clientSessionScope;
        return model + lane + (scope == null || scope.length() == 0 ? "" : "#s-" + scope);
    }

    private LocalApiGateway.CompletionResult executeNativeApiCompletionOnce(
            ClassLoader cl, Object transport, String sid,
            LocalApiGateway.CompletionRequest request, String pow,
            LocalApiGateway.DeltaSink sink) throws Exception {
        Object nativeRequest = newLocalApiNativeRequest(cl, sid, request, pow);
        Method completion = findNativeCompletionMethod(transport, nativeRequest);
        if (completion == null) {
            throw new LocalApiGateway.GatewayException(503, "transport_method_missing",
                    "server_error", "DeepSeek completion transport method was not found");
        }
        completion.setAccessible(true);
        Object flow;
        try {
            flow = completion.invoke(transport, nativeRequest, null);
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new LocalApiGateway.GatewayException(502, "upstream_start_failed",
                    "server_error", "DeepSeek rejected the completion: "
                            + safeThrowableMessage(cause));
        }
        if (flow == null) {
            throw new LocalApiGateway.GatewayException(503, "empty_upstream_flow",
                    "server_error", "DeepSeek returned no completion stream");
        }
        return collectLocalApiFlow(cl, flow, sink);
    }

    private static boolean isTransientApiFailure(LocalApiGateway.GatewayException error) {
        if (error == null) return false;
        if ("pow_unavailable".equals(error.code)
                || "upstream_start_failed".equals(error.code)
                || "empty_upstream_flow".equals(error.code)
                || "upstream_timeout".equals(error.code)
                || "upstream_rate_limit".equals(error.code)
                || "invalid_api_session".equals(error.code)
                || "empty_completion".equals(error.code)) return true;
        String message = String.valueOf(error.getMessage()).toLowerCase(Locale.US);
        if ("upstream_rejected".equals(error.code)) {
            return isNativeBusyLimit(message) || message.contains("rate_limit")
                    || message.contains("too frequent") || message.contains("过于频繁");
        }
        if (!"upstream_stream_failed".equals(error.code)) return false;
        return message.contains("unknownhost") || message.contains("unable to resolve")
                || message.contains("socket") || message.contains("connection")
                || message.contains("timeout") || message.contains("reset")
                || message.contains("abort") || message.contains("network")
                || message.contains("rate_limit") || message.contains("too frequent")
                || message.contains("过于频繁") || isNativeBusyLimit(message);
    }

    private static void awaitLocalApiNativeStart()
            throws LocalApiGateway.GatewayException {
        long waited = 0L;
        synchronized (LOCAL_API_RATE_LOCK) {
            long now = System.currentTimeMillis();
            long wait = Math.max(0L, localApiNextNativeStartAt - now);
            if (wait > 0L) {
                sleepLocalApi(wait, "pacing DeepSeek requests");
                waited = wait;
            }
            localApiNextNativeStartAt = System.currentTimeMillis()
                    + LOCAL_API_MIN_START_INTERVAL_MS;
        }
        if (waited > 0L) {
            LocalApiGateway.diagnostic("NATIVE_PACED wait_ms=" + waited);
        }
    }

    /**
     * Claude Code sends title/suggestion requests alongside the interactive Agent turn.  Give the
     * tool-bearing turn priority and pace the small-model lane independently so metadata traffic
     * cannot exhaust DeepSeek's account-wide burst limiter.
     */
    private static void awaitLocalApiAuxiliaryTurn()
            throws LocalApiGateway.GatewayException {
        long waited = 0L;
        while (true) {
            ensureLocalApiTime("waiting for the interactive Agent turn");
            long now = System.currentTimeMillis();
            long waitForPriority = LOCAL_API_AGENT_WAITERS.get() > 0
                    ? 500L : Math.max(0L, localApiAgentPriorityUntil - now);
            long waitForPacing = Math.max(0L, localApiNextAuxiliaryStartAt - now);
            long wait = Math.max(waitForPriority, waitForPacing);
            if (wait <= 0L) break;
            long slice = Math.min(500L, wait);
            sleepLocalApi(slice, "pacing Claude auxiliary requests");
            waited += slice;
        }
        localApiNextAuxiliaryStartAt = System.currentTimeMillis() + 4000L;
        if (waited > 0L) {
            LocalApiGateway.diagnostic("AUXILIARY_PACED wait_ms=" + waited);
        }
    }

    private static void extendLocalApiCooldown(long delayMs) {
        synchronized (LOCAL_API_RATE_LOCK) {
            localApiNextNativeStartAt = Math.max(localApiNextNativeStartAt,
                    System.currentTimeMillis() + Math.max(0L, delayMs));
        }
    }

    private static void extendLocalApiRateLimitCooldown() {
        long delay;
        synchronized (LOCAL_API_RATE_LOCK) {
            localApiRateLimitStreak = Math.min(4, localApiRateLimitStreak + 1);
            delay = localApiRateLimitStreak == 1 ? 2_000L
                    : localApiRateLimitStreak == 2 ? 4_000L
                    : localApiRateLimitStreak == 3 ? 8_000L : 12_000L;
            localApiNextNativeStartAt = Math.max(localApiNextNativeStartAt,
                    System.currentTimeMillis() + delay);
        }
        LocalApiGateway.diagnostic("NATIVE_RATE_LIMIT cooldown_ms=" + delay
                + " streak=" + localApiRateLimitStreak);
    }

    private static void resetLocalApiRateLimitStreak() {
        synchronized (LOCAL_API_RATE_LOCK) {
            localApiRateLimitStreak = 0;
        }
    }

    private static long remainingLocalApiTimeMs() {
        Long deadline = tlLocalApiDeadline.get();
        if (deadline == null) return LOCAL_API_REQUEST_BUDGET_MS;
        return Math.max(0L, deadline.longValue() - System.currentTimeMillis());
    }

    private static void ensureLocalApiClientActive(String stage)
            throws LocalApiGateway.GatewayException {
        LocalApiGateway.DeltaSink sink = tlLocalApiSink.get();
        if (sink != null && sink.isCancelled()) {
            throw new LocalApiGateway.GatewayException(499, "client_closed_request",
                    "server_error", "Client disconnected while " + stage);
        }
    }

    private static void ensureLocalApiTime(String stage)
            throws LocalApiGateway.GatewayException {
        ensureLocalApiClientActive(stage);
        if (remainingLocalApiTimeMs() <= 1000L) {
            throw new LocalApiGateway.GatewayException(504, "request_deadline_exceeded",
                    "server_error", "Local API request deadline exceeded while " + stage);
        }
    }

    private static void sleepLocalApi(long delayMs, String stage)
            throws LocalApiGateway.GatewayException {
        if (delayMs <= 0L) return;
        long remaining = remainingLocalApiTimeMs();
        if (remaining <= delayMs + 1000L) {
            throw new LocalApiGateway.GatewayException(504, "request_deadline_exceeded",
                    "server_error", "Local API request deadline exceeded while " + stage);
        }
        long end = System.currentTimeMillis() + delayMs;
        while (System.currentTimeMillis() < end) {
            ensureLocalApiClientActive(stage);
            try {
                Thread.sleep(Math.min(LOCAL_API_QUEUE_POLL_MS,
                        Math.max(1L, end - System.currentTimeMillis())));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LocalApiGateway.GatewayException(503, "request_interrupted",
                        "server_error", "Interrupted while " + stage);
            }
        }
    }

    private String reusableApiSession(ClassLoader cl, Object transport, String model)
            throws LocalApiGateway.GatewayException {
        String key = model == null || model.length() == 0 ? "default" : model;
        synchronized (LOCAL_API_SESSION_LOCK) {
            loadReusableApiSessionsLocked();
            String existing = LOCAL_API_SESSIONS.get(key);
            if (isUsableSessionId(existing)) {
                touchReusableApiSessionLocked(key, System.currentTimeMillis());
                return existing;
            }

            String lastError = "unknown";
            long[] waits = {0L, 1500L, 3000L, 6000L, 12000L, 18000L};
            for (int attempt = 0; attempt < waits.length; attempt++) {
                if (waits[attempt] > 0L) {
                    sleepLocalApi(waits[attempt], "waiting to create an API session");
                }
                ensureLocalApiTime("creating an API session");
                String created = createThrowawaySession(cl, transport);
                if (isUsableSessionId(created)) {
                    LOCAL_API_SESSIONS.put(key, created);
                    LOCAL_API_SESSION_LAST_USED.put(key, System.currentTimeMillis());
                    persistReusableApiSessionsLocked();
                    log("[LOCAL_API] reusable session created model=" + key
                            + " attempt=" + (attempt + 1));
                    return created;
                }
                lastError = localApiLastSessionError;
                log("[LOCAL_API] reusable session create retry model=" + key
                        + " attempt=" + (attempt + 1) + " reason=" + lastError);
            }
            throw new LocalApiGateway.GatewayException(503, "session_create_failed",
                    "server_error", "DeepSeek could not create the reusable API session after retries: "
                            + lastError);
        }
    }

    private String mintApiPowWithRetry(ClassLoader cl, Object powManager)
            throws LocalApiGateway.GatewayException {
        synchronized (LOCAL_API_POW_SERIAL_LOCK) {
            return mintApiPowWithRetrySerial(cl, powManager);
        }
    }

    private String mintApiPowWithRetrySerial(ClassLoader cl, Object powManager)
            throws LocalApiGateway.GatewayException {
        String lastError = "empty PoW response";
        long[] waits = {0L, 1000L, 3000L};
        for (int attempt = 0; attempt < waits.length; attempt++) {
            if (waits[attempt] > 0L) {
                sleepLocalApi(waits[attempt], "waiting for completion PoW");
            }
            ensureLocalApiTime("requesting completion PoW");
            try {
                Object result = mintCompletionPowBounded(cl, powManager,
                        Math.min(10_000L, Math.max(1000L, remainingLocalApiTimeMs() - 1000L)));
                if (result instanceof String && ((String) result).length() > 0) {
                    if (attempt > 0) log("[LOCAL_API] PoW recovered attempt=" + (attempt + 1));
                    return (String) result;
                }
                lastError = "DeepSeek returned an empty PoW token";
            } catch (Throwable t) {
                lastError = safeThrowableMessage(t);
            }
            log("[LOCAL_API] PoW retry attempt=" + (attempt + 1) + " reason=" + lastError);
        }
        throw new LocalApiGateway.GatewayException(503, "pow_unavailable",
                "server_error", "DeepSeek PoW is unavailable after retries: " + lastError);
    }

    /**
     * q71.j is a suspend network call. A broken Android network can leave runBlocking waiting
     * indefinitely, which used to occupy the only native lane forever. Keep at most one PoW call
     * alive and wait for it with a hard bound; a later retry may consume its delayed result.
     */
    private Object mintCompletionPowBounded(final ClassLoader cl, final Object powManager,
                                            long timeoutMs) throws Throwable {
        LocalApiPowTask task;
        synchronized (LOCAL_API_POW_LOCK) {
            task = localApiPowTask;
            if (task == null) {
                task = new LocalApiPowTask();
                final LocalApiPowTask started = task;
                Thread thread = new Thread(new Runnable() {
                    @Override public void run() {
                        try {
                            started.result = mintCompletionPow(cl, powManager);
                        } catch (Throwable t) {
                            started.failure = t;
                        } finally {
                            started.done.countDown();
                        }
                    }
                }, "Deekseep-API-PoW");
                thread.setDaemon(true);
                task.thread = thread;
                localApiPowTask = task;
                thread.start();
            }
        }
        boolean finished;
        try {
            finished = task.done.await(Math.max(1000L, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }
        if (!finished) {
            Thread thread = task.thread;
            if (thread != null) thread.interrupt();
            throw new java.util.concurrent.TimeoutException(
                    "DeepSeek PoW request exceeded " + timeoutMs + " ms");
        }
        synchronized (LOCAL_API_POW_LOCK) {
            if (localApiPowTask == task) localApiPowTask = null;
        }
        if (task.failure != null) throw task.failure;
        return task.result;
    }

    private static final class LocalApiPowTask {
        final CountDownLatch done = new CountDownLatch(1);
        volatile Thread thread;
        volatile Object result;
        volatile Throwable failure;
    }

    private static void loadReusableApiSessionsLocked() {
        if (localApiSessionsLoaded) return;
        localApiSessionsLoaded = true;
        LOCAL_API_SESSIONS.clear();
        LOCAL_API_SESSION_LAST_USED.clear();
        localApiSessionStatePersistedAt = System.currentTimeMillis();
        String text = readSmallText(LOCAL_API_SESSION_FILE);
        if (text == null || text.length() == 0) return;
        try {
            JSONObject object = new JSONObject(text);
            JSONObject metadata = object.optJSONObject(LOCAL_API_SESSION_META_KEY);
            JSONArray names = object.names();
            if (names == null) return;
            long loadedAt = System.currentTimeMillis();
            for (int i = 0; i < names.length(); i++) {
                String model = names.optString(i);
                if (LOCAL_API_SESSION_META_KEY.equals(model)) continue;
                String sid = object.optString(model, null);
                if (isUsableSessionId(sid)) {
                    LOCAL_API_SESSIONS.put(model, sid);
                    long lastUsed = metadata == null ? loadedAt : metadata.optLong(model, loadedAt);
                    LOCAL_API_SESSION_LAST_USED.put(model, lastUsed > 0L ? lastUsed : loadedAt);
                }
            }
        } catch (Throwable t) {
            log("[LOCAL_API] reusable session state ignored: " + safeThrowableMessage(t));
        }
    }

    private static void persistReusableApiSessionsLocked() {
        try {
            JSONObject object = new JSONObject();
            JSONObject metadata = new JSONObject();
            for (Map.Entry<String, String> entry : LOCAL_API_SESSIONS.entrySet()) {
                if (isUsableSessionId(entry.getValue())) {
                    object.put(entry.getKey(), entry.getValue());
                    metadata.put(entry.getKey(), reusableApiSessionLastUsedLocked(entry.getKey()));
                }
            }
            object.put(LOCAL_API_SESSION_META_KEY, metadata);
            overwriteTextFile(LOCAL_API_SESSION_FILE, object.toString());
            localApiSessionStatePersistedAt = System.currentTimeMillis();
        } catch (Throwable t) {
            log("[LOCAL_API] reusable session state write failed: " + safeThrowableMessage(t));
        }
    }

    private static long reusableApiSessionLastUsedLocked(String key) {
        Long value = LOCAL_API_SESSION_LAST_USED.get(key);
        return value == null || value.longValue() <= 0L
                ? System.currentTimeMillis() : value.longValue();
    }

    private static void touchReusableApiSessionLocked(String key, long now) {
        LOCAL_API_SESSION_LAST_USED.put(key, now);
        if (now - localApiSessionStatePersistedAt >= LOCAL_API_SESSION_TOUCH_PERSIST_MS) {
            persistReusableApiSessionsLocked();
        }
    }

    private static boolean isLocalApiInternalSession(String sid) {
        if (!isUsableSessionId(sid)) return false;
        synchronized (LOCAL_API_SESSION_LOCK) {
            loadReusableApiSessionsLocked();
            return LOCAL_API_SESSIONS.containsValue(sid);
        }
    }

    private static void invalidateReusableApiSession(String model, String sid) {
        String key = model == null || model.length() == 0 ? "default" : model;
        synchronized (LOCAL_API_SESSION_LOCK) {
            loadReusableApiSessionsLocked();
            String current = LOCAL_API_SESSIONS.get(key);
            if (sid == null || sid.equals(current)) {
                LOCAL_API_SESSIONS.remove(key);
                LOCAL_API_SESSION_LAST_USED.remove(key);
                persistReusableApiSessionsLocked();
                log("[LOCAL_API] invalid reusable session removed model=" + key);
            }
        }
    }

    private void deleteReusableApiSessions() {
        Object transport = liveR92;
        ClassLoader cl = hostClassLoader;
        if (transport == null || cl == null) return;
        boolean acquired = false;
        try {
            acquired = LOCAL_API_COMPLETION_SLOTS.tryAcquire(30, TimeUnit.SECONDS);
            if (!acquired) {
                log("[LOCAL_API] cleanup skipped: native completion still active");
                return;
            }
            List<String> keys;
            synchronized (LOCAL_API_SESSION_LOCK) {
                loadReusableApiSessionsLocked();
                keys = new ArrayList<>(LOCAL_API_SESSIONS.keySet());
            }
            for (String key : keys) {
                String sid;
                synchronized (LOCAL_API_SESSION_LOCK) {
                    sid = LOCAL_API_SESSIONS.get(key);
                }
                if (!isUsableSessionId(sid)) continue;
                boolean deleted = deleteThrowawaySession(cl, transport, sid);
                log("[LOCAL_API] reusable session deleted=" + deleted);
                if (!deleted) continue;
                synchronized (LOCAL_API_SESSION_LOCK) {
                    if (sid.equals(LOCAL_API_SESSIONS.get(key))) {
                        LOCAL_API_SESSIONS.remove(key);
                        LOCAL_API_SESSION_LAST_USED.remove(key);
                        persistReusableApiSessionsLocked();
                    }
                }
            }
        } catch (Throwable t) {
            log("[LOCAL_API] reusable session cleanup failed: " + safeThrowableMessage(t));
        } finally {
            if (acquired) LOCAL_API_COMPLETION_SLOTS.release();
        }
    }

    private void scheduleReusableApiSessionMaintenance() {
        while (true) {
            int state = LOCAL_API_SESSION_MAINTENANCE_RUNNING.get();
            if (state == 0) {
                if (LOCAL_API_SESSION_MAINTENANCE_RUNNING.compareAndSet(0, 1)) break;
            } else {
                if (state == 1) {
                    LOCAL_API_SESSION_MAINTENANCE_RUNNING.compareAndSet(1, 2);
                }
                return;
            }
        }
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                boolean rerun = false;
                try {
                    rerun = pruneReusableApiSessions();
                } catch (Throwable t) {
                    log("[LOCAL_API] reusable session maintenance failed: "
                            + safeThrowableMessage(t));
                } finally {
                    int state = LOCAL_API_SESSION_MAINTENANCE_RUNNING.getAndSet(0);
                    if (rerun || state == 2) scheduleReusableApiSessionMaintenance();
                }
            }
        }, "Deekseep-API-Session-Prune");
        worker.setDaemon(true);
        worker.start();
    }

    private boolean pruneReusableApiSessions() {
        Object transport = liveR92;
        ClassLoader cl = hostClassLoader;
        if (transport == null || cl == null || !isLocalApiEnabled()) return false;

        final long now = System.currentTimeMillis();
        final ArrayList<String> candidates = new ArrayList<>();
        synchronized (LOCAL_API_SESSION_LOCK) {
            loadReusableApiSessionsLocked();
            ArrayList<String> keys = new ArrayList<>(LOCAL_API_SESSIONS.keySet());
            Collections.sort(keys, new Comparator<String>() {
                @Override public int compare(String left, String right) {
                    return Long.compare(reusableApiSessionLastUsedLocked(left),
                            reusableApiSessionLastUsedLocked(right));
                }
            });
            int excess = Math.max(0, keys.size() - LOCAL_API_SESSION_MAX);
            for (String key : keys) {
                long age = Math.max(0L, now - reusableApiSessionLastUsedLocked(key));
                if (age <= LOCAL_API_SESSION_TTL_MS && excess <= 0) continue;
                candidates.add(key);
                if (excess > 0) excess--;
                if (candidates.size() >= LOCAL_API_SESSION_PRUNE_BATCH) break;
            }
        }
        if (candidates.isEmpty()) return false;

        boolean acquired = false;
        try {
            if (LOCAL_API_AGENT_WAITERS.get() > 0
                    || LOCAL_API_COMPLETION_SLOTS.hasQueuedThreads()) return true;
            acquired = LOCAL_API_COMPLETION_SLOTS.tryAcquire();
            if (!acquired) return true;
            int deleted = 0;
            for (String key : candidates) {
                if (LOCAL_API_AGENT_WAITERS.get() > 0
                        || LOCAL_API_COMPLETION_SLOTS.hasQueuedThreads()) break;
                String sid;
                synchronized (LOCAL_API_SESSION_LOCK) {
                    sid = LOCAL_API_SESSIONS.get(key);
                }
                if (!isUsableSessionId(sid) || !deleteThrowawaySession(cl, transport, sid)) {
                    continue;
                }
                synchronized (LOCAL_API_SESSION_LOCK) {
                    if (sid.equals(LOCAL_API_SESSIONS.get(key))) {
                        LOCAL_API_SESSIONS.remove(key);
                        LOCAL_API_SESSION_LAST_USED.remove(key);
                        persistReusableApiSessionsLocked();
                        deleted++;
                    }
                }
            }
            if (deleted > 0) {
                log("[LOCAL_API] pruned reusable sessions=" + deleted);
            }
            return deleted >= LOCAL_API_SESSION_PRUNE_BATCH;
        } catch (Throwable t) {
            log("[LOCAL_API] reusable session prune failed: " + safeThrowableMessage(t));
            return false;
        } finally {
            if (acquired) LOCAL_API_COMPLETION_SLOTS.release();
        }
    }

    private Object newLocalApiNativeRequest(ClassLoader cl, String sid,
                                             LocalApiGateway.CompletionRequest request,
                                             String pow) throws Exception {
        Class<?> ew0 = HostCompat.load(cl, "ew0");
        Constructor<?> selected = null;
        for (Constructor<?> ctor : ew0.getDeclaredConstructors()) {
            Class<?>[] p = ctor.getParameterTypes();
            if (p.length == 11 && p[0] == String.class && p[2] == String.class
                    && p[4] == boolean.class && p[5] == boolean.class
                    && p[7] == boolean.class && p[8] == String.class
                    && p[9] == String.class && p[10] == int.class) {
                selected = ctor;
                break;
            }
        }
        if (selected == null) {
            throw new LocalApiGateway.GatewayException(503, "request_constructor_missing",
                    "server_error", "DeepSeek request constructor is incompatible");
        }
        selected.setAccessible(true);
        tlLocalApiRequest.set(Boolean.TRUE);
        try {
            // ew0: sid, parent, prompt, files, thinking, search, audio, preempt,
            // model_type, PoW, Kotlin default mask. 512 keeps action unset; PoW lives in k.
            return selected.newInstance(sid, request.nativeParentMessageId,
                    request.prompt, new ArrayList(),
                    request.reasoning, request.search, null, false,
                    request.nativeModel, pow, 512);
        } finally {
            tlLocalApiRequest.remove();
        }
    }

    private static Method findNativeCompletionMethod(Object transport, Object request) {
        if (transport == null || request == null) return null;
        for (Method method : transport.getClass().getDeclaredMethods()) {
            Class<?>[] p = method.getParameterTypes();
            if (method.getName().equals("b") && p.length == 2
                    && p[0].isAssignableFrom(request.getClass())) return method;
        }
        return null;
    }

    private LocalApiGateway.CompletionResult collectLocalApiFlow(
            final ClassLoader cl, Object flow, final LocalApiGateway.DeltaSink sink)
            throws Exception {
        Method collectMethod = null;
        for (Class<?> itf : allInterfaces(flow.getClass())) {
            Method candidate = null;
            int matching = 0;
            for (Method method : itf.getDeclaredMethods()) {
                if (method.getParameterTypes().length == 2) {
                    candidate = method;
                    matching++;
                }
            }
            if (matching == 1 && candidate != null
                    && candidate.getParameterTypes()[0].isInterface()
                    && candidate.getParameterTypes()[1].isInterface()) {
                collectMethod = candidate;
                break;
            }
        }
        if (collectMethod == null) {
            throw new LocalApiGateway.GatewayException(503, "flow_contract_missing",
                    "server_error", "DeepSeek Flow contract was not found");
        }

        final Class<?> collectorClass = collectMethod.getParameterTypes()[0];
        final Class<?> continuationClass = collectMethod.getParameterTypes()[1];
        Class<?> contextClass = null;
        for (Method method : continuationClass.getMethods()) {
            if (method.getParameterTypes().length == 0
                    && method.getReturnType().isInterface()) {
                contextClass = method.getReturnType();
                break;
            }
        }
        final Object cancellationJob = contextClass == null
                ? null : newLocalApiCancellationJob(cl, contextClass);
        final Object context = contextClass == null ? null
                : (cancellationJob == null
                        ? emptyContextProxy(cl, contextClass) : cancellationJob);
        final CountDownLatch completed = new CountDownLatch(1);
        final StringBuilder text = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        final Throwable[] asyncFailure = {null};
        final boolean[] cancelled = {false};
        final boolean[] satisfied = {false};
        final int[] eventCount = {0};
        final NativeApiPatchDecoder patchDecoder = new NativeApiPatchDecoder();

        InvocationHandler continuationHandler = new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args) {
                if (isObjectMethod(method)) return objectMethod(proxy, method, args);
                if (method.getParameterTypes().length == 0) return context;
                if (args != null && args.length > 0) {
                    Throwable failure = coroutineFailure(args[0]);
                    if (failure != null
                            && !(failure instanceof LocalApiClientCancelled)
                            && !(failure instanceof LocalApiGenerationSatisfied)) {
                        asyncFailure[0] = failure;
                    }
                }
                completed.countDown();
                return null;
            }
        };
        Object continuation = Proxy.newProxyInstance(cl,
                new Class<?>[]{continuationClass}, continuationHandler);

        InvocationHandler collectorHandler = new InvocationHandler() {
            @Override public Object invoke(Object proxy, Method method, Object[] args)
                    throws Throwable {
                if (isObjectMethod(method)) return objectMethod(proxy, method, args);
                if (method.getParameterTypes().length != 2) return ui8Unit(cl);
                Object value = args == null || args.length == 0 ? null : args[0];
                eventCount[0]++;
                if (isSrvLog() && eventCount[0] <= 80) {
                    srvLog("[LOCAL_API_EVENT] #" + eventCount[0] + " "
                            + truncateForLog(summarizeFlowEvent(value), 1600));
                }
                ApiEvent event = decodeApiEvent(value, patchDecoder);
                // Once a complete structured action is captured, keep draining the native Flow
                // without accepting more model output. Throwing out of collect here returns the
                // tool quickly but can leave DeepSeek generating server-side, causing the next
                // Claude tool-result turn to hit parallel_chat_limit.
                if (satisfied[0]) return ui8Unit(cl);
                if (event.error != null) {
                    asyncFailure[0] = new LocalApiUpstreamException(event.errorStatus,
                            event.errorCode, event.errorType, event.error);
                    throw asyncFailure[0];
                }
                if (event.reasoningSet != null) {
                    String delta = applyApiSet(reasoning, event.reasoningSet);
                    if (delta.length() > 0 && sink != null && !sink.onReasoning(delta)) {
                        cancelled[0] = true;
                        cancelLocalApiCancellationJob(cancellationJob);
                        throw new LocalApiClientCancelled();
                    }
                }
                if (event.reasoning.length() > 0) {
                    reasoning.append(event.reasoning);
                    if (sink != null && !sink.onReasoning(event.reasoning)) {
                        cancelled[0] = true;
                        cancelLocalApiCancellationJob(cancellationJob);
                        throw new LocalApiClientCancelled();
                    }
                }
                if (event.textSet != null) {
                    String delta = applyApiSet(text, event.textSet);
                    if (delta.length() > 0 && sink != null && !sink.onText(delta)) {
                        cancelled[0] = true;
                        cancelLocalApiCancellationJob(cancellationJob);
                        throw new LocalApiClientCancelled();
                    }
                }
                if (event.text.length() > 0) {
                    text.append(event.text);
                    if (sink != null && !sink.onText(event.text)) {
                        cancelled[0] = true;
                        cancelLocalApiCancellationJob(cancellationJob);
                        throw new LocalApiClientCancelled();
                    }
                }
                if (sink != null && sink.isCancelled()) {
                    cancelled[0] = true;
                    cancelLocalApiCancellationJob(cancellationJob);
                    throw new LocalApiClientCancelled();
                }
                if (sink != null && sink.isSatisfied()) {
                    satisfied[0] = true;
                }
                return ui8Unit(cl);
            }
        };
        Object collector = Proxy.newProxyInstance(cl,
                new Class<?>[]{collectorClass}, collectorHandler);

        collectMethod.setAccessible(true);
        Object immediate;
        try {
            immediate = collectMethod.invoke(flow, collector, continuation);
            // A cold Kotlin Flow begins its network work when collect() is entered. Notify the
            // wire adapter only after that boundary; the collector itself also notifies before a
            // synchronous first event, and adapters are required to make this callback idempotent.
            if (sink != null) sink.onUpstreamStarted();
        } catch (java.lang.reflect.InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof LocalApiClientCancelled) cancelled[0] = true;
            else if (cause instanceof LocalApiGenerationSatisfied) satisfied[0] = true;
            else throw localApiStreamFailure(cause);
            immediate = null;
            completed.countDown();
        }
        Object suspended = null;
        try {
            Field field = HostCompat.load(cl, "w02").getDeclaredField("a");
            field.setAccessible(true);
            suspended = field.get(null);
        } catch (Throwable ignored) {}
        if (suspended == null || immediate != suspended) completed.countDown();

        boolean finished = false;
        long collectionBudget = Math.min(TimeUnit.SECONDS.toMillis(LOCAL_API_TIMEOUT_SECONDS),
                Math.max(1000L, remainingLocalApiTimeMs() - 1000L));
        long collectionEndsAt = System.currentTimeMillis() + collectionBudget;
        while (!finished && System.currentTimeMillis() < collectionEndsAt) {
            if (sink != null && sink.isCancelled()) {
                cancelled[0] = true;
                cancelLocalApiCancellationJob(cancellationJob);
                throw new LocalApiGateway.GatewayException(499, "client_closed_request",
                        "server_error", "Client disconnected while collecting DeepSeek output");
            }
            try {
                finished = completed.await(Math.min(LOCAL_API_QUEUE_POLL_MS,
                        Math.max(1L, collectionEndsAt - System.currentTimeMillis())),
                        TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cancelLocalApiCancellationJob(cancellationJob);
                throw new LocalApiGateway.GatewayException(503, "request_interrupted",
                        "server_error", "Completion collection was interrupted");
            }
        }
        if (!finished) {
            cancelLocalApiCancellationJob(cancellationJob);
            throw new LocalApiGateway.GatewayException(504, "upstream_timeout",
                    "server_error", "DeepSeek did not finish before the local API deadline");
        }
        if (asyncFailure[0] != null && !cancelled[0] && !satisfied[0]) {
            throw localApiStreamFailure(asyncFailure[0]);
        }
        if (!cancelled[0] && !satisfied[0]
                && text.length() == 0 && reasoning.length() == 0) {
            throw new LocalApiGateway.GatewayException(502, "empty_completion",
                    "server_error", "DeepSeek completed without returning text");
        }
        log("[LOCAL_API] collected events=" + eventCount[0]
                + " cancelled=" + cancelled[0] + " satisfied=" + satisfied[0]);
        return new LocalApiGateway.CompletionResult(text.toString(), reasoning.toString(),
                cancelled[0] ? "cancelled" : (satisfied[0] ? "tool_calls" : "stop"));
    }

    private static Throwable coroutineFailure(Object value) {
        if (value instanceof Throwable) return (Throwable) value;
        if (HostCompat.simpleNameIs(value, "fx6")) {
            Object failure = fieldByName(value, "a");
            if (failure instanceof Throwable) return (Throwable) failure;
        }
        return null;
    }

    private static ApiEvent decodeApiEvent(Object value,
                                           NativeApiPatchDecoder patchDecoder) {
        ApiEvent out = new ApiEvent();
        try {
            // The sealed Flow wrapper names are R8-generated and changed in 2.3.0.  Their stable
            // wire shape did not: HTTP terminal events wrap a response whose body is field j;
            // SSE events wrap {eventName=a,data=b}.  Decode that contract directly.
            Object wrapped = fieldByName(value, "a");
            Object bodyValue = fieldByName(wrapped, "j");
            if (bodyValue instanceof String) {
                String body = ((String) bodyValue).trim();
                if (body.startsWith("{")) {
                    JSONObject envelope = new JSONObject(body);
                    int outerCode = envelope.optInt("code", 0);
                    JSONObject data = envelope.optJSONObject("data");
                    int businessCode = data == null ? 0 : data.optInt("biz_code", 0);
                    String message = data == null ? envelope.optString("msg", "")
                            : data.optString("biz_msg", envelope.optString("msg", ""));
                    if (outerCode != 0 || businessCode != 0) {
                        String lower = message.toLowerCase(Locale.US);
                        if (lower.contains("invalid chat session")
                                || lower.contains("session not found")
                                || lower.contains("session deleted")) {
                            out.errorStatus = 409;
                            out.errorCode = "invalid_api_session";
                            out.errorType = "server_error";
                        } else if (isNativeBusyLimit(message)
                                || lower.contains("rate_limit")
                                || lower.contains("too frequent")
                                || message.contains("过于频繁")) {
                            out.errorStatus = 429;
                            out.errorCode = "upstream_rate_limit";
                            out.errorType = "rate_limit_error";
                        } else {
                            out.errorStatus = 502;
                            out.errorCode = "upstream_rejected";
                            out.errorType = "server_error";
                        }
                        out.error = message.length() == 0 ? body : message;
                        return out;
                    }
                }
                return out;
            }
            Object wrapper = wrapped;
            if (wrapper == null) return out;
            Object dataValue = fieldByName(wrapper, "b");
            if (!(dataValue instanceof String)) return out;
            Object rawEventName = fieldByName(wrapper, "a");
            String eventName = rawEventName == null ? "" : String.valueOf(rawEventName);
            String data = dataValue instanceof String ? (String) dataValue : null;
            String lowerEvent = eventName == null ? "" : eventName.toLowerCase(Locale.US);
            if (lowerEvent.contains("error") || lowerEvent.contains("failed")) {
                out.error = data == null ? "DeepSeek returned an upstream error" : data;
                if (isNativeBusyLimit(out.error)) {
                    out.errorStatus = 429;
                    out.errorCode = "upstream_rate_limit";
                    out.errorType = "rate_limit_error";
                }
                return out;
            }
            if (data == null || data.length() == 0) return out;
            Object json;
            String trimmed = data.trim();
            if (trimmed.startsWith("[")) json = new JSONArray(trimmed);
            else if (trimmed.startsWith("{")) json = new JSONObject(trimmed);
            else return out;
            if (lowerEvent.contains("hint") && json instanceof JSONObject) {
                JSONObject hint = (JSONObject) json;
                String hintType = hint.optString("type", "");
                String finishReason = hint.optString("finish_reason", "");
                if ("error".equalsIgnoreCase(hintType)
                        || finishReason.toLowerCase(Locale.US).contains("rate_limit")) {
                    String content = hint.optString("content", "DeepSeek rejected the request");
                    if (isNativeBusyLimit(content + " " + finishReason)
                            || finishReason.toLowerCase(Locale.US).contains("rate_limit")
                            || content.contains("过于频繁")) {
                        out.errorStatus = 429;
                        out.errorCode = "upstream_rate_limit";
                        out.errorType = "rate_limit_error";
                    } else {
                        out.errorStatus = 502;
                        out.errorCode = "upstream_rejected";
                        out.errorType = "server_error";
                    }
                    out.error = content + (finishReason.length() == 0
                            ? "" : " (" + finishReason + ")");
                    return out;
                }
            }
            NativeApiPatchDecoder.Delta delta = (patchDecoder == null
                    ? new NativeApiPatchDecoder() : patchDecoder).decode(json);
            out.text = delta.text;
            out.reasoning = delta.reasoning;
            out.textSet = delta.textSet;
            out.reasoningSet = delta.reasoningSet;
        } catch (Throwable ignored) {}
        return out;
    }

    private static boolean isNativeBusyLimit(String value) {
        if (value == null) return false;
        String lower = value.toLowerCase(Locale.US);
        return lower.contains("parallel_chat_limit")
                || lower.contains("parallel chat limit")
                || lower.contains("message is being generated")
                || value.contains("有消息正在生成")
                || value.contains("消息正在生成");
    }

    private static String safeThrowableMessage(Throwable throwable) {
        if (throwable == null) return "unknown error";
        Throwable value = deepestCause(throwable);
        String message = value.getMessage();
        String result = value.getClass().getSimpleName()
                + (message == null || message.length() == 0 ? "" : ": " + message);
        return result.length() > 500 ? result.substring(0, 500) : result;
    }

    private static Throwable deepestCause(Throwable throwable) {
        if (throwable == null) return null;
        Throwable value = throwable;
        HashSet<Throwable> seen = new HashSet<>();
        while (value.getCause() != null && value.getCause() != value && seen.add(value)) {
            value = value.getCause();
        }
        return value;
    }

    private static final class ApiEvent {
        String text = "";
        String reasoning = "";
        String textSet;
        String reasoningSet;
        String error;
        int errorStatus = 502;
        String errorCode = "upstream_stream_failed";
        String errorType = "server_error";
    }

    private static LocalApiGateway.GatewayException localApiStreamFailure(Throwable failure) {
        Throwable cursor = failure;
        HashSet<Throwable> seen = new HashSet<>();
        while (cursor != null && seen.add(cursor)) {
            if (cursor instanceof LocalApiUpstreamException) {
                LocalApiUpstreamException upstream = (LocalApiUpstreamException) cursor;
                return new LocalApiGateway.GatewayException(upstream.status, upstream.code,
                        upstream.type, "DeepSeek stream failed: " + upstream.getMessage());
            }
            cursor = cursor.getCause();
        }
        return new LocalApiGateway.GatewayException(502, "upstream_stream_failed",
                "server_error", "DeepSeek stream failed: " + safeThrowableMessage(failure));
    }

    private static final class LocalApiUpstreamException extends RuntimeException {
        final int status;
        final String code;
        final String type;

        LocalApiUpstreamException(int status, String code, String type, String message) {
            super(message);
            this.status = status;
            this.code = code;
            this.type = type;
        }
    }

    private static String applyApiSet(StringBuilder current, String replacement) {
        if (replacement == null) return "";
        String before = current.toString();
        if (replacement.equals(before)) return "";
        if (replacement.startsWith(before)) {
            String delta = replacement.substring(before.length());
            current.append(delta);
            return delta;
        }
        current.setLength(0);
        current.append(replacement);
        // A divergent SET is unusual but represents the authoritative upstream value. Streaming
        // cannot retract bytes already delivered, so emit the replacement as the safest signal.
        return replacement;
    }

    private static final class LocalApiClientCancelled extends RuntimeException {
        LocalApiClientCancelled() { super("local API client disconnected"); }
    }

    private static final class LocalApiGenerationSatisfied extends RuntimeException {
        LocalApiGenerationSatisfied() { super("complete local tool action captured"); }
    }

    private String createThrowawaySession(ClassLoader cl, Object r92) {
        try {
            java.lang.reflect.Field bf = r92.getClass().getDeclaredField("b"); // i91
            bf.setAccessible(true);
            Object i91 = bf.get(r92);
            Method createM = null;
            for (Method m : i91.getClass().getDeclaredMethods()) {
                if (m.getName().equals(HostCompat.method("i91", "a"))
                        && m.getParameterTypes().length == 1) {
                    createM = m;
                    break;
                }
            }
            if (createM == null) {
                localApiLastSessionError = "i91.a(create) method missing";
                extLog("[RELAY] i91.a(create) 未找到");
                return null;
            }
            Object res = driveSuspend(cl, createM, i91, new Object[0]);
            String body = String.valueOf(fieldByName(res, "j"));
            String sid = extractSessionId(body);
            if (sid == null) {
                localApiLastSessionError = "create response contained no session id: "
                        + truncateForLog(body, 500);
            } else {
                localApiLastSessionError = "ok";
            }
            return sid;
        } catch (Throwable t) {
            localApiLastSessionError = safeThrowableMessage(t);
            extLog("[RELAY] createThrowawaySession err: " + localApiLastSessionError
                    + "\n" + stackToString(deepestCause(t)));
            return null;
        }
    }

    private static String extractSessionId(String body) {
        if (body == null) return null;
        int cs = body.indexOf("\"chat_session\"");
        if (cs < 0) return null;
        int idk = body.indexOf("\"id\":\"", cs);
        if (idk < 0) return null;
        int start = idk + 6;
        int end = body.indexOf('"', start);
        if (end < 0) return null;
        String id = body.substring(start, end);
        return id.length() > 0 ? id : null;
    }

    private boolean deleteThrowawaySession(ClassLoader cl, Object r92, String sid) {
        try {
            java.lang.reflect.Field bf = r92.getClass().getDeclaredField("b"); // i91
            bf.setAccessible(true);
            Object i91 = bf.get(r92);
            Object jb1 = HostCompat.load(cl, "jb1")
                    .getConstructor(String.class).newInstance(sid);
            Method delM = null;
            for (Method m : i91.getClass().getDeclaredMethods()) {
                if (m.getName().equals(HostCompat.method("i91", "c"))
                        && m.getParameterTypes().length == 2) {
                    delM = m;
                    break;
                }
            }
            if (delM == null) { extLog("[RELAY] i91.c(delete) 未找到"); return false; }
            Object response = driveSuspend(cl, delM, i91, new Object[]{ jb1 });
            Object bodyValue = fieldByName(response, "j");
            if (!(bodyValue instanceof String)) return response != null;
            JSONObject envelope = new JSONObject((String) bodyValue);
            if (envelope.optInt("code", Integer.MIN_VALUE) != 0) return false;
            JSONObject data = envelope.optJSONObject("data");
            return data == null || !data.has("biz_code") || data.optInt(
                    "biz_code", Integer.MIN_VALUE) == 0;
        } catch (Throwable t) { extLog("[RELAY] deleteThrowawaySession err: " + t); return false; }
    }

    private Object mintCompletionPow(ClassLoader cl, Object q71) throws Throwable {
        Method jm = null;
        for (Method m : q71.getClass().getDeclaredMethods()) {
            if (m.getName().equals("j") && m.getParameterTypes().length == 1) { jm = m; break; }
        }
        if (jm == null) { extLog("[VP] q71.j not found"); return null; }
        Object res = driveSuspend(cl, jm, q71, new Object[0]);
        extLog("[VP] q71.j resumed: " + deepDump(res, 2));
        if (res == null) return null;
        Object a = fieldByName(res, "a");   // b36{a=base64 pow, b=error}
        return a;
    }

    private volatile Method cachedRunBlocking;
    private Object driveSuspend(ClassLoader cl, final Method m, final Object target, final Object[] preArgs) throws Throwable {
        Class<?> n02 = HostCompat.load(cl, "n02");
        Class<?> mb3 = HostCompat.load(cl, "mb3");
        // runBlocking(CoroutineContext, Function2)=静态 (n02,mb3)->Object。
        // build 间该 holder 类改名(2.2.1=t82 / 2.2.2=u82)，按候选名 + 结构签名兜底解析。
        Method K = cachedRunBlocking;
        if (K == null) {
            String[] holders = HostCompat.isV230()
                    ? new String[]{HostCompat.name("u82")}
                    : new String[]{"u82", "t82", "v82", "s82", "w82"};
            for (String nm : holders) {
                try {
                    Class<?> holder = cl.loadClass(nm);
                    for (Method mm : holder.getDeclaredMethods()) {
                        Class<?>[] p = mm.getParameterTypes();
                        if (java.lang.reflect.Modifier.isStatic(mm.getModifiers())
                                && p.length == 2 && p[0] == n02 && p[1] == mb3) { K = mm; break; }
                    }
                } catch (Throwable ignored) {}
                if (K != null) { extLog("[VP] runBlocking=" + nm + ".K"); break; }
            }
            if (K != null) cachedRunBlocking = K;
        }
        if (K == null) { extLog("[VP] runBlocking(n02,mb3) not found"); return null; }
        K.setAccessible(true);
        m.setAccessible(true);
        final Object ctx = emptyContextProxy(cl, n02);
        InvocationHandler blockH = new InvocationHandler() {
            public Object invoke(Object proxy, Method mm, Object[] a) throws Throwable {
                if (isObjectMethod(mm)) return objectMethod(proxy, mm, a);
                Object cont = (a != null && a.length > 0) ? a[a.length - 1] : null;
                Object[] args = new Object[preArgs.length + 1];
                System.arraycopy(preArgs, 0, args, 0, preArgs.length);
                args[preArgs.length] = cont;
                try {
                    return m.invoke(target, args);
                } catch (java.lang.reflect.InvocationTargetException ite) {
                    throw (ite.getCause() != null ? ite.getCause() : ite);
                }
            }
        };
        Object block = Proxy.newProxyInstance(cl, new Class<?>[]{mb3}, blockH);
        return K.invoke(null, ctx, block);
    }

    private Object shallowCloneEw0(Object src) {
        if (src == null) return null;
        try {
            Class<?> cls = src.getClass();
            Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
            Field theUnsafe = unsafeCls.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            Object unsafe = theUnsafe.get(null);
            Method alloc = unsafeCls.getMethod("allocateInstance", Class.class);
            Object dst = alloc.invoke(unsafe, cls);
            for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    try { f.set(dst, f.get(src)); } catch (Throwable ignored) {}
                }
            }
            return dst;
        } catch (Throwable t) {
            extLog("[VP] shallowCloneEw0 failed: " + t);
            return null;
        }
    }

    private static void setFieldByName(Object obj, String name, Object val) {
        if (obj == null) return;
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(obj, val);
                return;
            } catch (NoSuchFieldException ignored) {
            } catch (Throwable t) { return; }
        }
    }

    private String collectFlow(ClassLoader cl, Object flow) {
        final StringBuilder descBuf = new StringBuilder();
        try {
            Method collectM = null;
            for (Class<?> itf : allInterfaces(flow.getClass())) {
                Method cand = null; int two = 0;
                for (Method m : itf.getDeclaredMethods()) {
                    if (m.getParameterTypes().length == 2) { cand = m; two++; }
                }
                if (two == 1 && cand.getParameterTypes()[1].isInterface()) { collectM = cand; break; }
            }
            if (collectM == null) { extLog("[VP] Flow interface (1x 2-arg method) not found"); return null; }
            final Class<?> collectorCls = collectM.getParameterTypes()[0];
            final Class<?> contCls = collectM.getParameterTypes()[1];
            Class<?> ccTmp = null;
            for (Method m : contCls.getMethods()) {
                if (m.getParameterTypes().length == 0 && m.getReturnType().isInterface()) { ccTmp = m.getReturnType(); break; }
            }
            final Class<?> ccCls = ccTmp;
            extLog("[VP] collect=" + collectM.getName() + " collector=" + collectorCls.getName()
                    + " cont=" + contCls.getName() + " ctx=" + (ccCls == null ? "null" : ccCls.getName()));
            final Object ctx = (ccCls != null) ? emptyContextProxy(cl, ccCls) : null;

            final java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            final int[] count = {0};
            final StringBuilder acc = new StringBuilder();

            InvocationHandler contH = new InvocationHandler() {
                public Object invoke(Object proxy, Method m, Object[] a) {
                    if (isObjectMethod(m)) return objectMethod(proxy, m, a);
                    int p = m.getParameterTypes().length;
                    if (p == 0) return ctx;                        // getContext()
                    extLog("[VP] flow completed; events=" + count[0]
                            + " resumeArg=" + (a != null && a.length > 0 ? String.valueOf(a[0]) : "?"));
                    latch.countDown();
                    return null;
                }
            };
            final Object rootCont = Proxy.newProxyInstance(cl, new Class<?>[]{contCls}, contH);

            InvocationHandler collH = new InvocationHandler() {
                public Object invoke(Object proxy, Method m, Object[] a) {
                    if (isObjectMethod(m)) return objectMethod(proxy, m, a);
                    if (m.getParameterTypes().length == 2) {       // emit(value, cont)
                        try {
                            Object value = a[0];
                            count[0]++;
                            String s = summarizeFlowEvent(value);
                            if (count[0] <= 80) extLog("[VP] emit#" + count[0] + " " + s);
                            acc.append(s).append('\n');
                            String delta = extractContentDeltaFromEvent(value);
                            if (delta != null) descBuf.append(delta);
                        } catch (Throwable t) { extLog("[VP] emit err " + t); }
                        return null;
                    }
                    return null;
                }
            };
            Object collector = Proxy.newProxyInstance(cl, new Class<?>[]{collectorCls}, collH);

            collectM.setAccessible(true);
            extLog("[VP] invoking collect on " + flow.getClass().getName());
            Object ret;
            try {
                ret = collectM.invoke(flow, collector, rootCont);
            } catch (java.lang.reflect.InvocationTargetException ite) {
                Throwable c = ite.getCause() != null ? ite.getCause() : ite;
                extLog("[VP] collect threw: " + c + "\n" + stackToString(c));
                return descBuf.toString();
            }
            extLog("[VP] collect returned: " + String.valueOf(ret));
            latch.await(90, java.util.concurrent.TimeUnit.SECONDS);
            extLog("[VP] DONE events=" + count[0] + " accLen=" + acc.length()
                    + " descLen=" + descBuf.length()
                    + " acc=" + truncateForLog(acc.toString(), 1200));
        } catch (Throwable t) {
            extLog("[VP] collectFlow failed: " + t + "\n" + stackToString(t));
        }
        return descBuf.toString();
    }

    private String extractContentDeltaFromEvent(Object value) {
        try {
            Object event = fieldByName(value, "a");
            if (event == null || fieldByName(event, "j") instanceof String) return null;
            Object ename = fieldByName(event, "a");
            if (ename != null) return null;
            Object bj = fieldByName(event, "b");
            if (!(bj instanceof String)) return null;
            return extractContentDelta((String) bj);
        } catch (Throwable t) { return null; }
    }

    private static String extractContentDelta(String json) {
        if (json == null) return null;
        int vi = json.indexOf("\"v\":\"");
        if (vi < 0) return null;
        boolean bareDelta = json.startsWith("{\"v\":\"");
        boolean appendContent = json.contains("content") && json.contains("APPEND");
        if (!bareDelta && !appendContent) return null;
        int start = vi + 5;
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (ch == '\\' && i + 1 < json.length()) {
                char nx = json.charAt(i + 1);
                switch (nx) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': break;
                    default:  sb.append(nx);
                }
                i++;
                continue;
            }
            if (ch == '"') break;
            sb.append(ch);
        }
        return sb.toString();
    }

    private static String stackToString(Throwable t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < st.length && i < 18; i++) sb.append("    at ").append(st[i]).append('\n');
        Throwable cause = t.getCause();
        if (cause != null && cause != t) sb.append("  caused by: ").append(cause).append('\n');
        return sb.toString();
    }

    private Object emptyContextProxy(ClassLoader cl, final Class<?> ccCls) {
        InvocationHandler h = new InvocationHandler() {
            public Object invoke(Object proxy, Method m, Object[] a) {
                if (isObjectMethod(m)) return objectMethod(proxy, m, a);
                int p = m.getParameterTypes().length;
                if (p == 2) {
                    boolean a0fn = isFunction2(a[0]);
                    boolean a1fn = isFunction2(a[1]);
                    if (a0fn && !a1fn) return a[1];
                    if (a1fn && !a0fn) return a[0];
                    return a[1];
                }
                if (p == 1) {
                    Class<?> rt = m.getReturnType();
                    if (rt == ccCls) {
                        Object arg = a[0];
                        return (arg != null && ccCls.isInstance(arg)) ? arg : proxy;
                    }
                    return null;
                }
                return null;
            }
        };
        return Proxy.newProxyInstance(cl, new Class<?>[]{ccCls}, h);
    }

    /** Creates the host's real coroutine Job so disconnects cancel the upstream Flow. */
    private static Object newLocalApiCancellationJob(ClassLoader cl, Class<?> contextClass) {
        try {
            Class<?> jobClass = HostCompat.load(cl, "c74");
            Constructor<?> constructor = jobClass.getDeclaredConstructor(boolean.class);
            constructor.setAccessible(true);
            Object job = constructor.newInstance(true);
            return contextClass.isInstance(job) ? job : null;
        } catch (Throwable ignored) { return null; }
    }

    private static void cancelLocalApiCancellationJob(Object job) {
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

    private static boolean isObjectMethod(Method m) {
        return m.getDeclaringClass() == Object.class;
    }

    private static boolean isFunction2(Object o) {
        if (o == null) return false;
        for (Class<?> itf : allInterfaces(o.getClass())) {
            for (Method m : itf.getDeclaredMethods()) {
                if (m.getParameterTypes().length == 2 && !isObjectMethod(m)) return true;
            }
        }
        return false;
    }

    private static Object objectMethod(Object proxy, Method m, Object[] a) {
        String n = m.getName();
        if ("toString".equals(n)) return "VPProxy@" + System.identityHashCode(proxy);
        if ("hashCode".equals(n)) return System.identityHashCode(proxy);
        if ("equals".equals(n)) return proxy == (a != null && a.length > 0 ? a[0] : null);
        return null;
    }

    private static java.util.List<Class<?>> allInterfaces(Class<?> cls) {
        java.util.LinkedHashSet<Class<?>> out = new java.util.LinkedHashSet<>();
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            collectItfs(c, out);
        }
        return new java.util.ArrayList<>(out);
    }

    private static void collectItfs(Class<?> c, java.util.Set<Class<?>> out) {
        for (Class<?> i : c.getInterfaces()) {
            if (out.add(i)) collectItfs(i, out);
        }
    }

    private static String summarizeFlowEvent(Object v) {
        if (v == null) return "null";
        String n = simpleName(v);
        if (HostCompat.simpleNameIs(v, "lv7")) {
            return "lv7{event=" + logValue(fieldByName(v, "a")) + ", data=" + logValue(fieldByName(v, "b")) + "}";
        }
        String nr = summarizeNetworkResult(v);
        if (nr != null) return n + " " + nr;
        return deepDump(v, 3);
    }

    private static String deepDump(Object v, int depth) {
        if (v == null) return "null";
        if (v instanceof String || v instanceof Number || v instanceof Boolean) return logValue(v);
        if (v instanceof java.util.List || v instanceof java.util.Map
                || v instanceof android.net.Uri) return logValue(v);
        String n = simpleName(v);
        if (depth <= 0) return n + "(" + truncateForLog(String.valueOf(v), 80) + ")";
        StringBuilder sb = new StringBuilder(n).append("{");
        int k = 0;
        for (Field f : v.getClass().getDeclaredFields()) {
            try {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                f.setAccessible(true);
                Object fv = f.get(v);
                if (k > 0) sb.append(", ");
                sb.append(f.getName()).append('=').append(deepDump(fv, depth - 1));
                if (++k >= 16) { sb.append(", ..."); break; }
            } catch (Throwable ignored) {}
        }
        return sb.append('}').toString();
    }

    private static String summarizeNetworkResult(Object result) {
        if (result == null) return null;
        String n = simpleName(result);
        if (HostCompat.simpleNameIs(result, "w02")) return null;
        if (HostCompat.simpleNameIs(result, "kp5")) {
            Object biz = fieldByName(result, "a");
            Object data = fieldByName(result, "b");
            String dataName = simpleName(data);
            String bizName = simpleName(biz);
            if (HostCompat.simpleNameIs(data, "fp")
                    || "ul6".equals(dataName) || HostCompat.simpleNameIs(biz, "vx2")) {
                return "ok biz=" + logValue(biz) + " data=" + logValue(data);
            }
            return null;
        }
        if (HostCompat.simpleNameIs(result, "op5")) {
            Object biz = fieldByName(result, "a");
            if (HostCompat.simpleNameIs(biz, "vx2")) {
                return "err biz=" + logValue(biz)
                        + " msg=" + logValue(fieldByName(result, "b"))
                        + " detail=" + logValue(fieldByName(result, "c"));
            }
        }
        return null;
    }

    private static String summarizeFp(Object fp) {
        if (fp == null) return "null";
        return "fp{file_id=" + logValue(fieldByName(fp, "a"))
                + ", status=" + logValue(fieldByName(fp, "b"))
                + ", name=" + logValue(fieldByName(fp, "c"))
                + ", size=" + logValue(fieldByName(fp, "d"))
                + ", inserted_at=" + logValue(fieldByName(fp, "e"))
                + ", updated_at=" + logValue(fieldByName(fp, "f"))
                + ", token_usage=" + logValue(fieldByName(fp, "g"))
                + ", previewable=" + logValue(fieldByName(fp, "h"))
                + ", from_share=" + logValue(fieldByName(fp, "i"))
                + ", signed_path=" + logValue(fieldByName(fp, "j"))
                + ", is_image=" + logValue(fieldByName(fp, "k"))
                + ", audit_result=" + logValue(fieldByName(fp, "l"))
                + ", width=" + logValue(fieldByName(fp, "m"))
                + ", height=" + logValue(fieldByName(fp, "n"))
                + ", retryable=" + logValue(fieldByName(fp, "o")) + "}";
    }

    private static String summarizeUl6(Object ul6) {
        if (ul6 == null) return "null";
        return "ul6{files=" + logValue(fieldByName(ul6, "a")) + "}";
    }

    private static Object fieldByName(Object obj, String name) {
        if (obj == null) return null;
        name = HostCompat.staticMessageField(obj, name);
        try {
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(obj);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String logValue(Object v) {
        if (v == null) return "null";
        if (v instanceof String) {
            String s = (String) v;
            return "String(len=" + s.length() + ", \"" + truncateForLog(s, 320) + "\")";
        }
        if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
        if (v instanceof java.util.List) {
            java.util.List list = (java.util.List) v;
            StringBuilder sb = new StringBuilder("List(size=").append(list.size()).append(", [");
            for (int i = 0; i < list.size() && i < 6; i++) {
                if (i > 0) sb.append(", ");
                sb.append(logValue(list.get(i)));
            }
            if (list.size() > 6) sb.append(", ...");
            return sb.append("])").toString();
        }
        if (v instanceof java.util.Map) {
            return "Map(size=" + ((java.util.Map) v).size() + ")";
        }
        if (v instanceof android.net.Uri) return "Uri(" + truncateForLog(String.valueOf(v), 200) + ")";
        String n = simpleName(v);
        if (HostCompat.simpleNameIs(v, "fp")) return summarizeFp(v);
        if ("ul6".equals(n)) return summarizeUl6(v);
        if (HostCompat.simpleNameIs(v, "jv0")) return String.valueOf(v);
        String s = String.valueOf(v);
        return n + "(" + truncateForLog(s, 160) + ")";
    }

    private static String truncateForLog(String s, int max) {
        if (s == null) return "null";
        String t = s.replace('\n', ' ').replace('\r', ' ');
        if (t.length() <= max) return t;
        return t.substring(0, max) + "...<len=" + t.length() + ">";
    }

    private static String simpleName(Object obj) {
        if (obj == null) return "null";
        String n = obj instanceof Class ? ((Class<?>) obj).getName() : obj.getClass().getName();
        int idx = n.lastIndexOf('.');
        return idx >= 0 ? n.substring(idx + 1) : n;
    }
}
