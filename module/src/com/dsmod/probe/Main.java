package com.dsmod.probe;

import com.dsmod.relay.ExpertRelayGate;

import android.app.Activity;
import android.app.Application;
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
public class Main extends MainReflectionSupport implements IXposedHookLoadPackage {

    private static final String TAG = "DSPROBE";

    private static final String TARGET = "com.deepseek.chat";

    static final String SELF = "com.dsmod.probe";

    /** Exact-code257 passive Loader components live in the host; normal/legacy paths stay put. */
    static String runtimeComponentPackage() {
        return RuntimeEmbeddingMode.componentPackage(SELF);
    }

    private static Intent routePassiveHostComponent(Intent intent, Class<?> component) {
        if (intent != null && component != null) {
            intent.setClassName(runtimeComponentPackage(), component.getName());
        }
        return intent;
    }

    private static final String LOG_PATH = "/data/data/com.deepseek.chat/files/dsprobe.log";

    static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    // 存储在 DeepSeek 自己的 files 目录，hook 进程和 UI 都能直接读写
    static final String PROMPT_FILE       = "/data/data/com.deepseek.chat/files/deekseep_prompt.txt";

    static final String PROMPT_SOURCE_FILE = "/data/data/com.deepseek.chat/files/deekseep_prompt_source.txt";

    private static final String EMBEDDED_PROMPT_RESOURCE =
            "META-INF/com.github.mwiede.jsch/internal/transport/authentication/"
            + "runtime_policy_extension_20260727_v2.dat";

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

    static final String LOCAL_API_PROMPT_ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_local_api_prompt_enabled";

    static final String LOCAL_API_PROMPT_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_local_api_prompt.txt";

    static final String LOCAL_API_PROMPT_SOURCE_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_local_api_prompt_source.txt";

    static final int LOCAL_API_PROMPT_IMPORT_REQUEST = 9127;

    static final String SRVLOG_FILE       = "/data/data/com.deepseek.chat/files/deekseep_srvlog";

    static final String AUTO_BACKUP_FILE  = "/data/data/com.deepseek.chat/files/deekseep_auto_backup";

    static final String PASSWORD_LOGIN_UNLOCK_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_password_login_unlock";

    private static final String LOCAL_API_FLOATING_WINDOW_FILE =
            "/data/data/com.deepseek.chat/files/dq0_floating_window";

    private static final String LOCAL_API_KEEPALIVE_DISABLED_FILE =
            "/data/data/com.deepseek.chat/files/dq0_keepalive_disabled";

    private static final String LOCAL_API_CONTEXT_RELAY_DISABLED_FILE =
            "/data/data/com.deepseek.chat/files/dq0_context_relay_disabled";

    // Code257 attachments are scoped to the host process account. Keep relay disabled by
    // default on this exact host; users may explicitly opt in after its upload path is stable.
    private static final String LOCAL_API_CONTEXT_RELAY_V241_ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/dq0_context_relay_v241_disabled_v2";

    private static final String LOCAL_API_SERIAL_DISABLED_FILE =
            "/data/data/com.deepseek.chat/files/dq0_serial_disabled";

    private static final String LOCAL_API_FORCE_REASONING_FILE =
            "/data/data/com.deepseek.chat/files/dq0_force_reasoning";

    private static final String LOCAL_API_FORCE_REASONING_MODELS_FILE =
            "/data/data/com.deepseek.chat/files/dq0_force_reasoning_models";

    private static final String LOCAL_API_CUSTOM_MODELS_FILE =
            "/data/data/com.deepseek.chat/files/dq0_custom_models.json";

    private static final String LOCAL_API_MODEL_CATALOG_FILE =
            "/data/data/com.deepseek.chat/files/dq0_model_catalog.json";

    private static final String V241_LOCAL_API_LEGACY_MODEL_ROUTE_FILE =
            "/data/data/com.deepseek.chat/files/dq0_legacy_model_route_code257";

    private static final String V236_LOCAL_API_LEGACY_MODEL_ROUTE_FILE =
            "/data/data/com.deepseek.chat/files/dq0_legacy_model_route_code249";

    private static final String LOCAL_API_EXPERT_BUSY_FALLBACK_FILE =
            "/data/data/com.deepseek.chat/files/dq0_expert_busy_fallback";

    private static final String LOCAL_API_AUTO_RECOVERY_FILE =
            "/data/data/com.deepseek.chat/files/dq0_auto_recovery";

    private static final String LOCAL_API_RECOVERY_COUNT_FILE =
            "/data/data/com.deepseek.chat/files/dq0_recovery_count";

    public static volatile long localApiRecoveryCount = -1L;

    private static final String LOCAL_API_BATTERY_REMINDER_DISMISSED_FILE =
            "/data/data/com.deepseek.chat/files/dq0_battery_reminder_dismissed";

    private static final String MESSAGE_DETAILS_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_message_details";

    private static final String V241_LOCAL_CHAT_QUOTA_UNLOCK_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_local_chat_quota_unlock_code257";

    private static final String V236_LOCAL_CHAT_QUOTA_UNLOCK_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_local_chat_quota_unlock_code249";

    private static final String THINKING_CODE_COPY_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_thinking_code_copy_code257";

    private static final String V236_THINKING_CODE_COPY_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_thinking_code_copy_code249";

    private static final String WHALE_MOTION_SPEED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_whale_motion_speed";

    private static final String SWIPE_SETTINGS_ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_swipe_settings";

    private static final String DUAL_CHAT_ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_dual_chat";

    /** Escape hatch for the chat-page compaction chip; see {@link #isChatCompactionButtonEnabled()}. */
    private static final String CHAT_COMPACTION_BUTTON_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_chat_compaction_button";

    /**
     * Where the user last dragged the compaction chip, stored as two fractions of the chip's travel
     * range rather than as pixels so the position survives a density or window-size change.
     */
    private static final String CHAT_COMPACTION_CHIP_POS_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_chat_compaction_button_pos";

    /** Default resting place: hard right, a third of the way down, clear of the host's top bar. */
    private static final float CHIP_DEFAULT_FRACTION_X = 1f;
    private static final float CHIP_DEFAULT_FRACTION_Y = 0.34f;

    private static final String DATA_OPT_OUT_ENFORCED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_force_training_disabled";

    static final String PROACTIVE_HEARTBEAT_ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_proactive_heartbeat";

    private static final String PROACTIVE_HEARTBEAT_INTERVAL_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_proactive_interval_minutes";

    static final int    PICK_REQUEST      = 0xDE3E;

    static final int    PICK_IMAGE_REQUEST = 0xDE3F;

    static final int    ACCOUNT_IMPORT_REQUEST = 0xDE40;

    static final int    ACCOUNT_EXPORT_REQUEST = 0xDE41;

    static final int    LOCAL_API_BATTERY_REQUEST = 0xDE42;

    static final int    CRASH_EXPORT_REQUEST = 0xDE43;

    static final int    CHAT_IMPORT_REQUEST = 0xDE44;

    static final int    LOG_EXPORT_REQUEST = 0xDE45;

    static final int    CLOUD_PROMPT_UPLOAD_REQUEST = 0xDE46;

    public static final int FULL_DATA_BACKUP_REQUEST = 0xDE47;

    public static final int FULL_DATA_RESTORE_REQUEST = 0xDE48;

    private static final String EDITOR_IMAGE_MASTER_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_editor_images";

    private static final String EDITOR_IMAGE_CACHE_DIR =
            "/data/data/com.deepseek.chat/cache/captured";

    interface GalleryPickCallback {
        void onPicked(Uri uri);
    }

    interface CloudPromptPickCallback {
        void onPicked(Uri uri);
    }

    private static volatile GalleryPickCallback galleryPickCallback;

    private static volatile CloudPromptPickCallback cloudPromptPickCallback;

    // 视觉探针诊断日志（私有目录，直写，最可靠）
    static final String RELAY_LOG_PATH = "/data/data/com.deepseek.chat/files/deekseep_vision.log";

    private static final String[] IMAGE_EXTS = {"jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"};

    public static volatile Object liveQ71;

    public static volatile ClassLoader hostClassLoader;

    static ClassLoader hostClassLoaderForUi() {
        return hostClassLoader;
    }

    public static volatile Context hostApplicationContext;

    private static final AtomicBoolean DATA_OPT_OUT_SYNC_RUNNING = new AtomicBoolean();

    private static final AtomicBoolean DATA_OPT_OUT_SYNCED = new AtomicBoolean();

    private static final AtomicInteger HEARTBEAT_OPEN_GENERATION = new AtomicInteger();

    static final ConcurrentHashMap<String, NativeUiHeartbeatRequest>
            PENDING_NATIVE_UI_HEARTBEATS = new ConcurrentHashMap<>();

    private static final Object AGENT_UI_ACTION_LOCK = new Object();

    private static final String AGENT_SCREENSHOT_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_agent";

    /** code257 may run under a secondary Android user; /data/data is not its real sandbox. */
    private static File agentScreenshotFile(Context context) {
        if (HostCompat.isV241() && context != null) {
            return new File(new File(context.getFilesDir(), "deekseep_agent"),
                    "latest_screen.png");
        }
        return new File(AGENT_SCREENSHOT_DIR, "latest_screen.png");
    }

    // read_file 内容超过该长度时打包成 TXT 附件随结果发回，避免大文件内容撑爆对话上下文。
    private static final int AGENT_TXT_ATTACH_THRESHOLD = 1024;

    private static final ConcurrentHashMap<String, Object>
            AGENT_SCREENSHOT_UPLOAD_TOKENS = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, Object>
            AGENT_TOOL_RESULT_TOKENS = new ConcurrentHashMap<>();

    static final java.util.Set<String> ACTIVE_HIDDEN_ATTACHMENT_NAMES =
            java.util.Collections.newSetFromMap(
                    new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    // Only one hidden result may enter a conversation's idle window at a time.
    private static final java.util.Set<String> AGENT_RESULT_SEND_LOCKED =
            java.util.Collections.newSetFromMap(
                    new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    private static final AtomicBoolean AGENT_OUTBOX_RECOVERED = new AtomicBoolean();

    private static final AtomicBoolean AGENT_PRIVILEGED_BACKEND_PROBED =
            new AtomicBoolean();

    private static volatile long agentUiActionNotBefore;

    public static final AtomicInteger LOCAL_API_AGENT_WAITERS = new AtomicInteger();

    public static volatile long localApiAgentPriorityUntil;

    public static volatile long localApiNextAuxiliaryStartAt;

    public static final long LOCAL_API_TIMEOUT_SECONDS = 120L;

    // 整体请求预算（包含 PoW、重试、长流式正文），用 5 分钟兜底；卡死由 120s 空闲超时兜底。
    // DeepSeek 长上下文/思考阶段可在首个 token 前静默 1-2 分钟，20s 会误判 upstream_timeout
    // 并触发一次 30s 重试，让工具调用显得"卡住"。
    public static final long LOCAL_API_REQUEST_BUDGET_MS = 300_000L;

    public static final ThreadLocal<Long> tlLocalApiDeadline = new ThreadLocal<>();

    public static final ThreadLocal<z2.DeltaSink> tlLocalApiSink =
            new ThreadLocal<>();

    // code257 distinguishes image uploads from ordinary files through p22 width/height metadata.
    // This flag is set only around its Local API image upload; TXT relay keeps the original
    // zero-dimension file metadata path.
    private static final ThreadLocal<Boolean> tlV241LocalApiImageUpload =
            new ThreadLocal<>();

    // code249's dz1/n51 image path also requires real dimensions, but its per-model file-id
    // conversion uses n51.a rather than code257's g71.a. Keep a separate exact-version marker so
    // neither implementation can enter the other host branch and TXT uploads remain unchanged.
    private static final ThreadLocal<Boolean> tlV236LocalApiImageUpload =
            new ThreadLocal<>();

    // Legacy hosts keep their original token-only builder binding. code257 requires the full
    // route so its PoW and lazy completion factories can preserve the same account namespace.
    private static final Map<Object, String> LOCAL_API_ROUTED_HEADER_BUILDERS =
            Collections.synchronizedMap(new WeakHashMap<Object, String>());

    public static final long LOCAL_API_AGENT_QUEUE_WAIT_MS = 60_000L;

    public static final long LOCAL_API_CHAT_QUEUE_WAIT_MS = 30_000L;

    public static final long LOCAL_API_AUX_QUEUE_WAIT_MS = 8_000L;

    public static final long LOCAL_API_QUEUE_POLL_MS = 250L;

    // The native service rejects bursts even when they are serialized. Space completion starts
    // apart and extend the not-before time after an explicit upstream rate-limit event.
    public static final long LOCAL_API_MIN_START_INTERVAL_MS = 200L;

    // A completed upstream tool-call stream has already released the native lane. Keep only a
    // small hand-off guard before the client's tool-result turn; the old 1800 ms global cooldown
    // was pure user-visible latency and stacked on every Agent step.
    public static final long LOCAL_API_TOOL_HANDOFF_COOLDOWN_MS = 250L;

    public static final long LOCAL_API_NORMAL_COOLDOWN_MS = 500L;

    public static final Object LOCAL_API_RATE_LOCK = new Object();

    public static volatile long localApiNextNativeStartAt;

    public static volatile int localApiRateLimitStreak;

    public static final Object LOCAL_API_POW_LOCK = new Object();

    public static volatile LocalApiPowTask localApiPowTask;

    public static final long LOCAL_API_SESSION_TTL_MS = 24L * 60L * 60L * 1000L;

    public static final long LOCAL_API_SESSION_TOUCH_PERSIST_MS = 5L * 60L * 1000L;

    public static final int LOCAL_API_SESSION_PRUNE_BATCH = 4;

    public static final AtomicInteger LOCAL_API_SESSION_MAINTENANCE_RUNNING =
            new AtomicInteger();

    // 发送点(fu0.y/uu0.y)捕获的完整附件 fp 列表与当前会话模型：主线程同栈传给紧随其后的 transport hook。
    static final ThreadLocal<List> tlPendingFps = new ThreadLocal<>();

    static final ThreadLocal<String> tlPendingModel = new ThreadLocal<>();

    // 诊断：记录服务器返回的 SSE 原始事件（受 SRVLOG_FILE 开关控制）
    static final String SRV_LOG_PATH = "/data/data/com.deepseek.chat/files/deekseep_srv.log";

    static final String SRV_LOG_EXT  = "/storage/emulated/0/deekseep_srv.log";

    // DeekseepUi 选完文件后的 UI 刷新回调
    static volatile Runnable onPickComplete;

    static volatile Runnable localApiPromptPickComplete;

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

    private static final AtomicBoolean UNSUPPORTED_VERSION_DIALOG_SHOWN =
            new AtomicBoolean();

    private static volatile long activationHeartbeatAttemptAt = 0L;

    private static volatile boolean activationHeartbeatLogged = false;

    private static volatile boolean proactiveHeartbeatConfigSynced = false;

    private static volatile String lastUiLanguageLog = "";

    private static final AtomicBoolean WHALE_HOOKS_INSTALLED = new AtomicBoolean(false);

    private static final AtomicBoolean TEXT_WAVE_HOOKS_INSTALLED = new AtomicBoolean(false);

    private static final AtomicBoolean AGENT_RENDER_HOOKS_INSTALLED = new AtomicBoolean(false);

    private static final AtomicBoolean APPEARANCE_HOOKS_INSTALLED = new AtomicBoolean(false);

    private static final AtomicBoolean V241_LOCAL_CHAT_QUOTA_HOOKS_INSTALLED =
            new AtomicBoolean(false);

    private static final AtomicBoolean V236_LOCAL_CHAT_QUOTA_HOOKS_INSTALLED =
            new AtomicBoolean(false);

    private static final AtomicBoolean V241_THINKING_CODE_COPY_HOOK_INSTALLED =
            new AtomicBoolean(false);

    private static final AtomicBoolean V236_THINKING_CODE_COPY_HOOK_INSTALLED =
            new AtomicBoolean(false);

    private static final String V236_ALL_FILE_TYPES_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_v236_all_file_types";
    private static final String V241_ALL_FILE_TYPES_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_v241_all_file_types";

    private static final AtomicBoolean V236_ALL_FILE_TYPES_HOOK_INSTALLED =
            new AtomicBoolean(false);
    private static final AtomicBoolean V241_ALL_FILE_TYPES_HOOK_INSTALLED =
            new AtomicBoolean(false);

    private static volatile Object v236FileExtensionRepository;
    private static volatile Object v241FileExtensionRepository;

    private static final class AllFileExtensionsSet extends HashSet<String> {
        @Override
        public boolean contains(Object o) {
            return true;
        }

        @Override
        public boolean isEmpty() {
            return false;
        }
    }

    private static final Map<Object, Object[]> V241_LOCAL_CHAT_QUOTA_ORIGINALS =
            Collections.synchronizedMap(new WeakHashMap<Object, Object[]>());

    private static final Map<Object, Object[]> V236_LOCAL_CHAT_QUOTA_ORIGINALS =
            Collections.synchronizedMap(new WeakHashMap<Object, Object[]>());

    private static volatile Field V241_EDIT_QUOTA_FIELD;

    private static volatile Field V241_REGENERATE_QUOTA_FIELD;

    private static volatile Field V236_EDIT_QUOTA_FIELD;

    private static volatile Field V236_REGENERATE_QUOTA_FIELD;

    private static final AtomicBoolean LOCAL_API_HOOKS_INSTALLED = new AtomicBoolean(false);

    private static final AtomicBoolean V236_LOCAL_API_HOOKS_INSTALLED = new AtomicBoolean(false);

    private static volatile boolean localApiKeepAliveControlLogged;

    private static volatile long localApiKeepAliveLaunchAt;

    private static volatile boolean localApiKeepAliveLaunchState;

    private static volatile long localApiFloatingWindowLaunchAt;

    private static volatile IBinder publicTunnelBridgeBinder;

    private static volatile boolean publicTunnelBridgeBinding;

    private static volatile long publicTunnelBridgeRequestAt;

    private static volatile ResultReceiver publicTunnelBridgeReceiver;

    private static volatile boolean publicTunnelProviderUnavailable;

    static volatile Method welcomeWhaleDrawNodeInvalidate;

    static volatile WeakReference<Object> welcomeWhaleDrawNode =
            new WeakReference<>(null);

    private static volatile WeakReference<Activity> welcomeWhaleActivity =
            new WeakReference<>(null);

    private static volatile List<WeakReference<View>> welcomeWhaleRenderViews =
            Collections.emptyList();

    private static final AtomicInteger welcomeWhaleFrameGeneration = new AtomicInteger();

    private static final AtomicBoolean welcomeWhaleInvalidateLogged = new AtomicBoolean(false);

    private static final AtomicBoolean welcomeWhaleDrawNodeLogged = new AtomicBoolean(false);

    private static volatile long welcomeWhaleAngleAt;

    private static volatile float welcomeWhaleMotionSpeedCache = Float.NaN;

    // Loaded once before WCDB starts, then refreshed from p68's already-materialised local rows.
    static final ConcurrentHashMap<String, Integer> FROZEN_SESSION_HEADS =
            new ConcurrentHashMap<>();

    // Marker-gated real-flow probe; removed after the failing device path is captured.
    private static final String REAL_SESSION_PROBE_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_real_session_probe";

    private static volatile long composerTriggerAt;

    // 现代 API：模块实例，供静态 log 走框架日志
    static volatile Main MODULE;

    // Traditional Xposed may instantiate the entry class while the process is still being
    // specialized from a USAP, before ActivityThread has prepared the main Looper.  Creating a
    // Handler here used to make the API 82+ compatibility APK fail before handleLoadPackage().
    // Initialize it only after the target package callback is delivered.
    Handler main;

    WeakReference<Activity> curAct = new WeakReference<>(null);

    private static final Map<View, HashSet<String>> DEFAULT_COLLAPSED_THINKING =
            Collections.synchronizedMap(new WeakHashMap<View, HashSet<String>>());

    private static final Map<View, ViewTreeObserver.OnGlobalLayoutListener>
            DEFAULT_THINKING_LAYOUT_LISTENERS =
            Collections.synchronizedMap(new WeakHashMap<View, ViewTreeObserver.OnGlobalLayoutListener>());

    private WeakReference<TextView> btn = new WeakReference<>(null);

    /**
     * The chat page's compaction chip. It overlaps the settings entry's resting area, so the two are
     * kept mutually exclusive by route: the settings entry owns the settings root route and this one
     * owns everything else. The host keeps its own controls along the top bar — the new-conversation
     * button among them — so the chip starts at the right edge about a third of the way down rather
     * than in the corner, and the user can drag it anywhere; where it was left is remembered in
     * {@link #CHAT_COMPACTION_CHIP_POS_FILE}.
     */
    private WeakReference<TextView> compactBtn = new WeakReference<>(null);

    /** Last route observed by {@link #syncButtonWithRoute}, so the chip can be re-evaluated later. */
    private volatile String lastRoute;

    WeakReference<Object> navController = new WeakReference<>(null);

    // ---------------------------------------------------------------------------------------
    // Compatibility forwarders: after the JavaHookGuide-driven split moved these members into
    // sibling Hook*.java classes, other pre-existing package files (DeekseepUi, ChatEditorUi,
    // ChatAppearance, HookLogOverlay, z7, z19, AgentToolConfig, AccountUi, ...) and Main's own
    // remaining "Main module = MODULE; ... module.xxx()" idiom still reference these names as
    // members of Main. These thin forwarders preserve that surface without duplicating logic.
    // ---------------------------------------------------------------------------------------

    static final String PROMPT_LINK_FILE = HookChatPipeline.PROMPT_LINK_FILE;

    static void setEnabled(boolean on) { HookAccountLoginSecurity.setEnabled(on); }
    static boolean isNoCensor() { return HookChatPipeline.isNoCensor(); }
    static boolean isChatMultiSelect() { return HookSessionManagement.isChatMultiSelect(); }
    static boolean isWechatMobileLoginUnlock() { return HookAccountLoginSecurity.isWechatMobileLoginUnlock(); }
    static boolean isGoogleLoginUnlock() { return HookAccountLoginSecurity.isGoogleLoginUnlock(); }
    static boolean isRiskBypassEnabled() { return HookAccountLoginSecurity.isRiskBypassEnabled(); }
    static boolean isFakeMuteEnabled() { return HookAccountLoginSecurity.isFakeMuteEnabled(); }
    static boolean isAutoContinueEnabled() { return HookChatPipeline.isAutoContinueEnabled(); }
    static boolean isReplyReadyNotificationsEnabled() { return HookChatPipeline.isReplyReadyNotificationsEnabled(); }
    static boolean isWelcomeWhaleMotionEnabled() { return HookUiSurface.isWelcomeWhaleMotionEnabled(); }
    static boolean isExpertRelayEnabled() { return HookAttachmentPipeline.isExpertRelayEnabled(); }
    static boolean isExpertUnlock() { return HookAttachmentPipeline.isExpertUnlock(); }
    static boolean isHotUpdateDisabled() { return HookConfigRollout.isHotUpdateDisabled(); }
    static boolean hasProactiveHeartbeatBinding() { return HookChatPipeline.hasProactiveHeartbeatBinding(); }
    static String localApiRuntimeStatus() { return HookAccountLoginSecurity.localApiRuntimeStatus(); }
    static boolean setLocalApiEnabled(boolean on) { return HookAccountLoginSecurity.setLocalApiEnabled(on); }
    static boolean isNoCensorContextRetentionEnabled() { return HookChatPipeline.isNoCensorContextRetentionEnabled(); }
    static long fakeMuteUntilMillis() { return HookAccountLoginSecurity.fakeMuteUntilMillis(); }
    static String homeGreeting() { return HookUiSurface.homeGreeting(); }
    static boolean isPromptSplitInjectionEnabled() { return HookChatPipeline.isPromptSplitInjectionEnabled(); }
    static int configuredPromptInjectionInterval() { return HookChatPipeline.configuredPromptInjectionInterval(); }
    static void activateOptionalHooksNow() { HookAccountLoginSecurity.activateOptionalHooksNow(); }
    static void markSessionDeletedLocally(String sid) { HookSessionManagement.markSessionDeletedLocally(sid); }
    static List<Object[]> nativeSessionDirectory() { return HookSessionManagement.nativeSessionDirectory(); }
    static void refreshNativeHistorySnapshot(String sid) { HookAccountLoginSecurity.refreshNativeHistorySnapshot(sid); }
    static boolean isLocalApiLogMuted() { return HookChatPipeline.isLocalApiLogMuted(); }
    static String setLocalApiHttpsEnabled(Context context, boolean enabled) {
        return HookAccountLoginSecurity.setLocalApiHttpsEnabled(context, enabled);
    }
    static boolean isLocalApiNoCensor() { return HookChatPipeline.isLocalApiNoCensor(); }

    z2.CompletionResult executeLocalApiCompletion(z2.CompletionRequest request, z2.DeltaSink sink)
            throws Exception {
        return HookAccountLoginSecurity.INSTANCE.executeLocalApiCompletion(request, sink);
    }
    void deleteReusableApiSessions() { HookAccountLoginSecurity.INSTANCE.deleteReusableApiSessions(); }
    HookSessionManagement.NativeHeartbeatHistory fetchNativeHeartbeatHistory(String conversationId)
            throws Throwable {
        return HookAccountLoginSecurity.INSTANCE.fetchNativeHeartbeatHistory(conversationId);
    }
    boolean dispatchProactiveThroughNativeUi(
            Context context, String requestId, boolean taskReminder, String taskKind,
            String sid, Integer previousHead, boolean reasoning, String prompt,
            String fallbackText) {
        return HookAccountLoginSecurity.INSTANCE.dispatchProactiveThroughNativeUi(
                context, requestId, taskReminder, taskKind, sid, previousHead, reasoning, prompt,
                fallbackText);
    }
    HookSessionManagement.NativeHeartbeatHistory refreshNativeHeartbeatHistory(
            String conversationId, Integer previousHead) throws Throwable {
        return HookAccountLoginSecurity.INSTANCE.refreshNativeHeartbeatHistory(conversationId, previousHead);
    }
    boolean persistNativeHeartbeatHistory(HookSessionManagement.NativeHeartbeatHistory history)
            throws Throwable {
        return HookAccountLoginSecurity.INSTANCE.persistNativeHeartbeatHistory(history);
    }
    boolean applyNativeHeartbeatHistory(final HookSessionManagement.NativeHeartbeatHistory history) {
        return HookAccountLoginSecurity.INSTANCE.applyNativeHeartbeatHistory(history);
    }

    // ---------------------------------------------------------------------------------------
    // Fields moved back from sibling Hook*.java classes: R8-symbol-mapped bytecode patching
    // (the "unsafe move" list) references these by unqualified name at very specific offsets
    // inside protected-payload-src, so they must remain physically declared in Main.java even
    // though most of their behavioral logic now lives in the Hook* split files.
    // ---------------------------------------------------------------------------------------
    public static volatile boolean localApiSessionsLoaded;
    public static long localApiSessionStatePersistedAt;
    public static volatile Set<String> localApiInternalSessionIds = Collections.emptySet();
    public static volatile Method cachedRunBlocking;
    public static volatile String localApiLastSessionError = "not attempted";
    public static volatile Object liveR92;
    public static volatile Object ACTIVE_LOCAL_API_CANCEL_JOB;

    // ---------------------------------------------------------------------------------------
    // Aliases for sibling Hook*.java fields that stay effectively-final references (only their
    // contents mutate via clear()/add()/set()/get()/remove()/tryAcquire(), never reassigned).
    // Unlike the volatile fields above these are safe to alias rather than physically move,
    // because the alias and the original always denote the exact same live object.
    // ---------------------------------------------------------------------------------------
    public static final AtomicInteger LOCAL_API_LOG_MUTE_DEPTH = HookChatPipeline.LOCAL_API_LOG_MUTE_DEPTH;
    public static final Semaphore LOCAL_API_NATIVE_PERMITS = HookAccountLoginSecurity.LOCAL_API_NATIVE_PERMITS;
    public static final int LOCAL_API_NATIVE_PERMIT_COUNT = HookAccountLoginSecurity.LOCAL_API_NATIVE_PERMIT_COUNT;
    public static final Set<String> LOCAL_API_RETIRED_SESSION_IDS =
            HookAccountLoginSecurity.LOCAL_API_RETIRED_SESSION_IDS;
    public static final Map<Object, z12.Route> LOCAL_API_ROUTED_NATIVE_REQUESTS =
            HookAccountLoginSecurity.LOCAL_API_ROUTED_NATIVE_REQUESTS;
    public static final ConcurrentHashMap<String, z12.Route> LOCAL_API_ROUTED_POW =
            HookAccountLoginSecurity.LOCAL_API_ROUTED_POW;
    public static final Map<String, String> LOCAL_API_SESSIONS = HookAccountLoginSecurity.LOCAL_API_SESSIONS;
    public static final String LOCAL_API_SESSION_FILE = HookAccountLoginSecurity.LOCAL_API_SESSION_FILE;
    public static final Map<String, Long> LOCAL_API_SESSION_LAST_USED =
            HookAccountLoginSecurity.LOCAL_API_SESSION_LAST_USED;
    public static final Object LOCAL_API_SESSION_LOCK = HookAccountLoginSecurity.LOCAL_API_SESSION_LOCK;
    public static final int LOCAL_API_SESSION_MAX = HookAccountLoginSecurity.LOCAL_API_SESSION_MAX;
    public static final String LOCAL_API_SESSION_META_KEY = HookAccountLoginSecurity.LOCAL_API_SESSION_META_KEY;
    public static final String LOCAL_API_SESSION_RETIRED_KEY =
            HookAccountLoginSecurity.LOCAL_API_SESSION_RETIRED_KEY;
    public static final ThreadLocal<z12.Route> tlLocalApiAccountRoute =
            HookAccountLoginSecurity.tlLocalApiAccountRoute;
    public static final ThreadLocal<Boolean> tlLocalApiRequest = HookChatPipeline.tlLocalApiRequest;
    public static final ThreadLocal<Boolean> tlProactiveHeartbeatRequest =
            HookAccountLoginSecurity.tlProactiveHeartbeatRequest;

    /** Resolved session endpoint. The service owner is deliberately kept with the method because
     * obfuscated host generations can reuse the same short class name for unrelated data models.
     * Lives on Main (not HookAttachmentPipeline, where the resolution logic itself still lives)
     * because protected-payload-src declares locals of type `Main.NativeSessionEndpoint`. */
    public static final class NativeSessionEndpoint {
        public final Object service;
        public final Method method;

        public NativeSessionEndpoint(Object service, Method method) {
            this.service = service;
            this.method = method;
        }
    }

    // Method forwarders for the same "unsafe move" bytecode-patching list (unqualified `Main.`
    // call sites baked into protected-payload-src / R8-symbol-mapped offsets).
    public static void cancelLocalApiCancellationJob(Object job) {
        HookChatPipeline.cancelLocalApiCancellationJob(job);
    }
    public static Throwable deepestCause(Throwable throwable) {
        return HookAttachmentPipeline.deepestCause(throwable);
    }
    public static String extractSessionId(Object response) {
        return HookAttachmentPipeline.extractSessionId(response);
    }
    public static boolean isUsableSessionId(String sid) {
        return HookSessionManagement.isUsableSessionId(sid);
    }
    public static NativeSessionEndpoint resolveSessionCreateEndpoint(Object transport, String createName) {
        return HookAttachmentPipeline.resolveSessionCreateEndpoint(transport, createName);
    }
    public static Object ui8Unit(ClassLoader cl) {
        return HookSessionManagement.ui8Unit(cl);
    }

    public static synchronized void log(String msg) {
        if (z8.shouldSuppressGeneralMessage(msg)) return;
        if (isLocalApiExecutionLogContext() && !z8.isError(msg)) return;
        try { DeveloperDiagnostics.record(msg); } catch (Throwable ignored) {}
        try { Main m = MODULE; if (m != null) m.log(Log.INFO, TAG, msg); } catch (Throwable ignored) {}
        try { HookLogOverlay.onLog(msg); } catch (Throwable ignored) {}
        try { RawHookTrace.module(msg); } catch (Throwable ignored) {}
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
    public static synchronized void srvLog(String msg) {
        if (z8.shouldSuppressGeneralMessage(msg)) return;
        if (isLocalApiExecutionLogContext() && !z8.isError(msg)) return;
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
    public static synchronized void extLog(String msg) {
        if (z8.shouldSuppressGeneralMessage(msg)) return;
        if (isLocalApiExecutionLogContext() && !z8.isError(msg)) return;
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

    static void triggerCrashTest(Context context, String mode) {
        if (mode == null) return;
        if (mode.startsWith("native_")) {
            try { new File(HookChatPipeline.CRASH_TEST_ARM_FILE).delete(); } catch (Throwable ignored) {}
            HookChatPipeline.recordCrashTest("blocked-unsafe", mode);
            Toast.makeText(context, "该 Native 测试已因系统稳定性风险移除",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (mode.endsWith("_send")) {
            try {
                overwriteTextFile(HookChatPipeline.CRASH_TEST_ARM_FILE, mode);
                Toast.makeText(context, "已等待下一次发送消息触发崩溃",
                        Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(context, "无法设置崩溃测试", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        HookChatPipeline.recordCrashTest("trigger", mode);
        if ("java_worker".equals(mode)) {
            new Thread(new Runnable() {
                @Override public void run() {
                    throw new RuntimeException("Deekseep Java worker crash test");
                }
            }, "deekseep-crash-test").start();
            return;
        }
        if ("java_executor".equals(mode)) {
            java.util.concurrent.Executors.newSingleThreadExecutor().execute(new Runnable() {
                @Override public void run() {
                    throw new RuntimeException("Deekseep executor crash test");
                }
            });
            return;
        }
        if ("java_handler".equals(mode)) {
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() {
                    throw new RuntimeException("Deekseep Handler callback crash test");
                }
            }, 350L);
            return;
        }
        if ("java_frame".equals(mode)) {
            android.view.Choreographer.getInstance().postFrameCallback(frameTimeNanos -> {
                throw new RuntimeException("Deekseep frame callback crash test");
            });
            return;
        }
        if ("java_null".equals(mode)) {
            Object value = null;
            value.toString();
            return;
        }
        if ("java_stack".equals(mode)) {
            crashStackTest(0L);
            return;
        }
        if ("java_state".equals(mode)) {
            throw new IllegalStateException("Deekseep IllegalStateException crash test");
        }
        if ("java_cast".equals(mode)) {
            Integer ignored = (Integer) (Object) "Deekseep ClassCastException crash test";
            return;
        }
        if ("java_bounds".equals(mode)) {
            int ignored = (new int[0])[0];
            return;
        }
        if ("java_arithmetic".equals(mode)) {
            int zero = mode.length() - mode.length();
            int ignored = 1 / zero;
            return;
        }
        if ("java_assert".equals(mode)) {
            throw new AssertionError("Deekseep AssertionError crash test");
        }
        throw new RuntimeException("Deekseep Java main crash test");
    }

    private static long crashStackTest(long value) {
        return crashStackTest(value + 1L) + value;
    }

    public static boolean isSrvLog() {
        return new File(SRVLOG_FILE).exists();
    }

    static void setSrvLog(boolean on) {
        try {
            File ef = new File(SRVLOG_FILE);
            if (on) overwriteTextFile(SRVLOG_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    static boolean isRawHookTraceEnabled() { return RawHookTrace.enabled(); }

    static boolean setRawHookTraceEnabled(boolean on) { return RawHookTrace.setEnabled(on); }

    static boolean clearRawHookTrace() { return RawHookTrace.clear(); }

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

    static boolean proactiveHeartbeatBoundToCurrentConversation() {
        String current = HeartbeatToolProtocol.cleanScope(
                HookSessionManagement.sidebarCurrentSid != null ? HookSessionManagement.sidebarCurrentSid
                        : HookChatPipeline.lastInteractiveConversationId);
        return current.length() > 0
                && current.equals(readHeartbeatBinding().conversationId);
    }

    static HeartbeatBinding readHeartbeatBinding() {
        synchronized (HookChatPipeline.HEARTBEAT_BINDING_LOCK) {
            String text = readSmallText(HookChatPipeline.PROACTIVE_HEARTBEAT_BINDING_FILE);
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

    static String heartbeatPlanForConversation(String conversationId) {
        String sid = HeartbeatToolProtocol.cleanScope(conversationId);
        HeartbeatBinding binding = readHeartbeatBinding();
        return sid.equals(binding.conversationId) ? binding.instruction : "";
    }

    static final class HeartbeatBinding {
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
            // code257 used to accept a new interval while the periodic task had no target chat.
            // The companion receiver then correctly woke up but skipped every run, which looked
            // like a dead heartbeat.  Bind the visible chat at save time or fail visibly instead
            // of persisting a schedule that can never produce a message.  code249 is unchanged.
            if (HostCompat.isV241() && isProactiveHeartbeatEnabled()
                    && !ensureV241HeartbeatBinding()) {
                log("code257 heartbeat interval rejected: no current chat binding");
                return false;
            }
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
                if (HostCompat.isV241()) {
                    if (!ensureV241HeartbeatBinding()) {
                        log("code257 heartbeat enable rejected: no current chat binding");
                        return false;
                    }
                } else if (!HookChatPipeline.hasProactiveHeartbeatBinding()) {
                    String candidate = HeartbeatToolProtocol.cleanScope(
                            HookSessionManagement.sidebarCurrentSid != null ? HookSessionManagement.sidebarCurrentSid
                                    : HookChatPipeline.lastInteractiveConversationId);
                    if (candidate.length() > 0) {
                        HookChatPipeline.writeHeartbeatBinding(candidate, HookChatPipeline.legacyHeartbeatPlan());
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

    /** Exact code257 adapter: a periodic heartbeat cannot be meaningful without a chat scope. */
    private static boolean ensureV241HeartbeatBinding() {
        if (!HostCompat.isV241()) return true;
        if (HookChatPipeline.hasProactiveHeartbeatBinding()) return true;
        String candidate = HeartbeatToolProtocol.cleanScope(
                HookSessionManagement.sidebarCurrentSid != null ? HookSessionManagement.sidebarCurrentSid : HookChatPipeline.lastInteractiveConversationId);
        return candidate.length() > 0 && HookChatPipeline.writeHeartbeatBinding(candidate, HookChatPipeline.legacyHeartbeatPlan());
    }

    static void dispatchProactiveHeartbeatConfig(Context context, boolean enabled) {
        try {
            Intent config = new Intent(ProactiveHeartbeatReceiver.ACTION_CONFIG);
            config.setClassName(runtimeComponentPackage(),
                    ProactiveHeartbeatReceiver.class.getName());
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
    public void handleLoadPackage(LoadPackageParam param) {
        MODULE = this;
        if (param == null) DeekseepLetter.getLetter();
        final ClassLoader cl = param.classLoader;
        final String pkg = param.packageName;

        // The embedded coexistence build uses a suffixed application id for cold-start
        // validation. It runs the same DeepSeek classes, so allow it through the hook bootstrap;
        // production remains com.deepseek.chat and keeps the existing storage contract.
        if (!TARGET.equals(pkg) && !"com.deepseek.chat.embedded".equals(pkg)) return;
        final String currentPid = String.valueOf(android.os.Process.myPid());
        if (currentPid.equals(System.getProperty("deekseep.module.injected.pid"))) {
            log("handleLoadPackage: Deekseep already loaded in pid " + currentPid + ", skipping duplicate injection");
            return;
        }
        System.setProperty("deekseep.module.injected.pid", currentPid);
        // Loader/embedding/anti-tamper admission is a Closed-edition feature.  Open builds must
        // remain usable with ordinary Xposed/compatible loaders and must not perform anti-root or
        // anti-Frida checks or terminate the host process.
        if (BuildInfo.PROTECTED_BUILD) {
            if (!LoaderSecurityGuard.checkAndEnforceGenericLoaderRejection(cl, null)) {
                return;
            }
            if (!LoaderSecurityGuard.checkAndEnforceAntiEmbedding(cl, null,
                    param.appInfo != null ? param.appInfo.sourceDir : null)) {
                return;
            }
        }
        // Clear before early compatibility hooks so their installation diagnostics survive.
        try { new FileWriter(LOG_PATH, false).close(); } catch (Throwable ignored) {}
        HookLogOverlay.resetSession();
        HostCompat.initialize(cl);
        try { HookUiSurface.INSTANCE.hookNativeDualChatRoot(cl); }
        catch (Throwable t) { log("early native dual root hook failed: " + t); }
        // SSL trust bypass intentionally removed: was diagnostic-only for GP 2.5.0 symbol capture.
        final String bootstrapVersion = AndroidManifestVersion.readFromApk(
                param.appInfo == null ? null : param.appInfo.sourceDir);
        if (HostCompat.isRetiredVersionName(bootstrapVersion)) {
            installRetiredHostNotice(bootstrapVersion);
            log("host maintenance ended; feature bootstrap suppressed version="
                    + bootstrapVersion);
            return;
        }
        // A re-signed/repacked host can retain com.deepseek.chat while no longer matching any
        // verified symbol table.  In particular, do not fall back to the historical 2.2 mapping:
        // it installs obsolete hooks and lets the protected watchdog kill an otherwise unrelated
        // host process.  2.3.4, 2.3.6 and 2.4.1 retain their established independent paths.
        if (!HostCompat.supportsMaintainedFeatureRuntime()) {
            String unsupported = bootstrapVersion == null || bootstrapVersion.length() == 0
                    ? HostCompat.generationName() : bootstrapVersion;
            installRetiredHostNotice(unsupported);
            log("host symbol table is not maintained; feature bootstrap suppressed version="
                    + unsupported + ", detected=" + HostCompat.generationName());
            return;
        }
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

        // 服务器返回诊断日志：每次应用启动清空重记（与主日志一致）
        if (isSrvLog()) {
            try { new FileWriter(SRV_LOG_PATH, false).close(); } catch (Throwable ignored) {}
            try { new FileWriter(SRV_LOG_EXT, false).close(); } catch (Throwable ignored) {}
        }
        log("module loaded (universal), package=" + pkg
                + ", hostGeneration=" + HostCompat.generationName());

        // 外部可见加载标记：证明模块确实被注入进了 DeepSeek 进程
        try {
            FileWriter w = new FileWriter(LOADED_MARK_EXT, false);
            w.write(TS.format(new Date()) + "  loaded into " + pkg + "\n");
            w.close();
        } catch (Throwable ignored) {}

        // 跟踪当前 Activity via ActivityLifecycleCallbacks（libxposed modern 无法 hook 框架类
        // Activity.onResume，改用 Application.registerActivityLifecycleCallbacks）
        main.post(new Runnable() {
            @Override public void run() {
                try {
                    Application app = (Application) Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication").invoke(null);
                    if (app == null) { log("lifecycle registration: Application not ready"); return; }
                    if (hostApplicationContext == null) {
                        hostApplicationContext = app;
                    }
                    if ((!BuildInfo.PROTECTED_BUILD || CloudPromptClient.hasValidLicense(app))
                            && !FEATURE_HOOKS_INSTALLED.get()) {
                        log("lifecycle bootstrap: license verified, installing feature hooks");
                        installAllFeatureHooks(cl);
                    }
                    app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                        @Override public void onActivityCreated(Activity act, android.os.Bundle b) {}
                        @Override public void onActivityStarted(Activity act) {}
                        @Override public void onActivityPaused(Activity act) {
                            try {
                                JavaPluginManager.onActivityPaused(act);
                                ChatAppearance.onActivityPaused(act);
                                TextWaveEngine.stop(act);
                            } catch (Throwable ignored) {}
                        }
                        @Override public void onActivityStopped(Activity act) {}
                        @Override public void onActivitySaveInstanceState(Activity act, android.os.Bundle b) {}
                        @Override public void onActivityDestroyed(Activity act) {
                            DeekseepUi.onActivityDestroyed(act);
                            if (curAct.get() == act) {
                                hideButton();
                                hideCompactionButton();
                            }
                        }
                        @Override public void onActivityResumed(Activity act) {
                            onActivityResumedInternal(act, cl);
                        }
                    });
                    log("activity lifecycle callbacks registered");
                    // The main activity may have resumed before we subscribed; scan now.
                    try {
                        Object thread = Class.forName("android.app.ActivityThread")
                                .getMethod("currentActivityThread").invoke(null);
                        java.lang.reflect.Field fMap = thread.getClass()
                                .getDeclaredField("mActivities");
                        fMap.setAccessible(true);
                        Object map = fMap.get(thread);
                        java.lang.reflect.Method vals = map.getClass()
                                .getDeclaredMethod("values");
                        for (Object rec : (java.util.Collection<?>) vals.invoke(map)) {
                            java.lang.reflect.Field fAct = rec.getClass()
                                    .getDeclaredField("activity");
                            fAct.setAccessible(true);
                            Activity found = (Activity) fAct.get(rec);
                            if (found != null && !found.isFinishing()) {
                                log("lifecycle: bootstrapping from existing activity "
                                        + found.getClass().getSimpleName());
                                onActivityResumedInternal(found, cl);
                                break;
                            }
                        }
                    } catch (Throwable t) { log("lifecycle bootstrap scan failed: " + t); }
                } catch (Throwable t) { log("lifecycle registration failed: " + t); }
            }
        });
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            hook(onResume).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object r = chain.proceed();
                    try {
                        Activity act = (Activity) chain.getThisObject();
                        onActivityResumedInternal(act, cl);
                    } catch (Throwable ignored) {}
                    return r;
                }
            });
        } catch (Throwable ignored) {}

        try {
            Method onPause = Activity.class.getDeclaredMethod("onPause");
            hook(onPause).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    try {
                        JavaPluginManager.onActivityPaused(
                                (Activity) chain.getThisObject());
                        ChatAppearance.onActivityPaused(
                                (Activity) chain.getThisObject());
                        // A translucent permission/Binder helper and screen recorder both pause
                        // MainActivity without hiding or destroying it. Cancelling here repeatedly
                        // replaced the frame driver and made the whale freeze. The driver already
                        // self-suspends while decor is invisible and terminates on destruction.
                        TextWaveEngine.stop((Activity) chain.getThisObject());
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
            Method onBackPressed = Activity.class.getDeclaredMethod("onBackPressed");
            hook(onBackPressed).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    ChatAppearance.onNativeBackNavigation(
                            (Activity) chain.getThisObject());
                    return chain.proceed();
                }
            });
        } catch (Throwable t) {
            log("hook image-preview back boundary failed: " + t);
        }

        try {
            Method onDestroy = Activity.class.getDeclaredMethod("onDestroy");
            hook(onDestroy).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    try {
                        Activity act = (Activity) chain.getThisObject();
                        DeekseepUi.onActivityDestroyed(act);
                        ChatAppearance.onActivityDestroyed(act);
                        SettingsSwipeGesture.forget(act);
                        DualChatUi.forget(act);
                        SystemBarCompat.forget(act);
                        HookLogOverlay.onActivityDestroyed(act);
                        if (curAct.get() == act) {
                            hideButton();
                            hideCompactionButton();
                        }
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
                            final Activity activity = (Activity) chain.getThisObject();
                            ChatAppearance.onHostPointerEvent(activity, (MotionEvent) event);
                            if (SettingsSwipeGesture.observe(
                                    activity, (MotionEvent) event)) {
                                main.post(new Runnable() {
                                    @Override public void run() {
                                        try {
                                            if (BuildInfo.PROTECTED_BUILD
                                                    && !CloudPromptClient.hasValidLicense(activity)) {
                                                DeekseepUi.showActivationDialog(activity, hostClassLoader, () -> {
                                                    performModuleInjection(activity, hostClassLoader);
                                                });
                                                return;
                                            }
                                            if (isDualChatEnabled()) {
                                                DualChatUi.show(activity);
                                            } else {
                                                openNativeDeepSeekSettings(activity);
                                            }
                                        }
                                        catch (Throwable error) {
                                            log("right-edge entry failed: "
                                                    + safeThrowableMessage(error));
                                        }
                                    }
                                });
                            }
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
                        } else if (req == CRASH_EXPORT_REQUEST) {
                            CrashDiagnosticsUi.handleExportResult(
                                    (Activity) chain.getThisObject(), res,
                                    dataArg instanceof Intent ? (Intent) dataArg : null);
                        } else if (req == CHAT_IMPORT_REQUEST) {
                            if (res == Activity.RESULT_OK && dataArg instanceof Intent) {
                                Intent data = (Intent) dataArg;
                                Uri uri = data.getData();
                                if (uri != null) {
                                    persistReadGrant((Activity) chain.getThisObject(), data, uri);
                                    DeekseepTools.showChatImport(
                                            (Activity) chain.getThisObject(), uri);
                                }
                            }
                        } else if (req == LOG_EXPORT_REQUEST) {
                            LogExportUi.handleResult((Activity) chain.getThisObject(), res,
                                    dataArg instanceof Intent ? (Intent) dataArg : null);
                        } else if (req == FULL_DATA_BACKUP_REQUEST) {
                            ClosedFullDataBackupBridge.handleExportResult(
                                    (Activity) chain.getThisObject(), res,
                                    dataArg instanceof Intent ? (Intent) dataArg : null);
                        } else if (req == FULL_DATA_RESTORE_REQUEST) {
                            ClosedFullDataBackupBridge.handleRestoreResult(
                                    (Activity) chain.getThisObject(), res,
                                    dataArg instanceof Intent ? (Intent) dataArg : null);
                        } else if (req == JavaPluginManager.IMPORT_REQUEST) {
                            JavaPluginUi.handleImportResult(
                                    (Activity) chain.getThisObject(), res,
                                    dataArg instanceof Intent ? (Intent) dataArg : null);
                        } else if (req == JavaPluginManager.IMPORT_REQUEST + 1) {
                            JavaPluginUi.handleTutorialExportResult(
                                    (Activity) chain.getThisObject(), res,
                                    dataArg instanceof Intent ? (Intent) dataArg : null);
                        } else if (req == JavaPluginManager.IMPORT_REQUEST + 2) {
                            JavaPluginUi.handleAiReferenceExportResult(
                                    (Activity) chain.getThisObject(), res,
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
                        } else if (req == CLOUD_PROMPT_UPLOAD_REQUEST) {
                            CloudPromptPickCallback callback = cloudPromptPickCallback;
                            cloudPromptPickCallback = null;
                            Uri uri = null;
                            if (res == Activity.RESULT_OK && dataArg instanceof Intent) {
                                Intent data = (Intent) dataArg;
                                uri = data.getData();
                                if (uri != null) persistReadGrant((Activity) chain.getThisObject(), data, uri);
                            }
                            if (callback != null) callback.onPicked(uri);
                        } else if (req == LOCAL_API_PROMPT_IMPORT_REQUEST) {
                            if (res == Activity.RESULT_OK && dataArg instanceof Intent) {
                                Intent data = (Intent) dataArg;
                                Uri uri = data.getData();
                                if (uri != null) {
                                    persistReadGrant((Activity) chain.getThisObject(), data, uri);
                                    handleLocalApiPromptPickedFile(
                                            (Activity) chain.getThisObject(), uri);
                                }
                            }
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

        Context earlyCtx = hostApplicationContext;
        if (earlyCtx == null) {
            try {
                Application app = (Application) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null);
                if (app != null) {
                    earlyCtx = app;
                    hostApplicationContext = app;
                }
            } catch (Throwable ignored) {}
        }
        if (!BuildInfo.PROTECTED_BUILD
                || (earlyCtx != null && CloudPromptClient.hasValidLicense(earlyCtx))) {
            log("Cold start: license verified, installing feature hooks early");
            installAllFeatureHooks(cl);
        } else {
            log("Cold start: unauthenticated, feature hooks suppressed pending activation");
        }
    }

    private void hookSettingsComposable(ClassLoader cl) {
        try {
            String settingsClass;
            String settingsMethod;
            if (HostCompat.isV241()) {
                settingsClass = "fd5";
                settingsMethod = "c";
            } else if (HostCompat.isV236()) {
                settingsClass = "wc5";
                settingsMethod = "c";
            } else if (HostCompat.isV234()) {
                settingsClass = HostCompat.isGooglePlay() ? "pf6" : "qc5";
                settingsMethod = HostCompat.isGooglePlay() ? "i" : "c";
            } else {
                String settingsLegacyClass = HostCompat.isGooglePlay() ? "ph6" : "u25";
                String settingsLegacyMethod = HostCompat.isGooglePlay() ? "d" : "i";
                settingsClass = HostCompat.name(settingsLegacyClass);
                settingsMethod = HostCompat.method(
                        settingsLegacyClass, settingsLegacyMethod);
            }
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

    private final AtomicBoolean FEATURE_HOOKS_INSTALLED = new AtomicBoolean(false);

    public synchronized void installAllFeatureHooks(ClassLoader cl) {
        if (cl == null) cl = hostClassLoader;
        if (cl == null) return;
        if (!FEATURE_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        log("installAllFeatureHooks: installing all feature hooks (authorized)");
        try {
            if (HostCompat.isV236() || HostCompat.isV241()) {
                HostNavigationBridge.install(this, cl);
            }
            HookAgentPipeline.INSTANCE.hookAgentToolLogRoundedRect(cl);
            RemoteFeatureFlags.install(this, cl);
            HookConfigRollout.INSTANCE.hookAttachmentGuidePromptRollout(cl);
            HookConfigRollout.INSTANCE.hookHomeWelcomeRollout(cl);
            HookUiSurface.INSTANCE.hookNativeDualChatRoot(cl);
            HookAttachmentPipeline.INSTANCE.hookNativeImagePreviewBoundary(cl);

            if (HostCompat.isV236() && BuildInfo.PROTECTED_BUILD
                    && BuildInfo.LOCAL_API_INCLUDED
                    && V236_LOCAL_API_HOOKS_INSTALLED.compareAndSet(false, true)) {
                try {
                    hookLegacyLocalApiAccountRouting(cl);
                } catch (Throwable error) {
                    V236_LOCAL_API_HOOKS_INSTALLED.set(false);
                    log("code249 Local API credential hook bootstrap failed: "
                            + safeThrowableMessage(error));
                }
            } else if (HostCompat.isV241()
                    && BuildInfo.PROTECTED_BUILD && BuildInfo.LOCAL_API_INCLUDED
                    && LOCAL_API_HOOKS_INSTALLED.compareAndSet(false, true)) {
                try {
                    HookAccountLoginSecurity.INSTANCE.hookLocalApiAccountRouting(cl);
                } catch (Throwable error) {
                    LOCAL_API_HOOKS_INSTALLED.set(false);
                    log("Local API credential hook bootstrap failed: "
                            + safeThrowableMessage(error));
                }
            }
            HookAccountLoginSecurity.INSTANCE.hookNativeFakeMute(cl);
            HookAccountLoginSecurity.INSTANCE.hookTrainingOptOutControl(cl);
            HookConfigRollout.INSTANCE.hookHotUpdateDialog(cl);
            HookConfigRollout.INSTANCE.hookRemoteConfigHotUpdates(cl);
            HookAccountLoginSecurity.INSTANCE.hookCandidateLoginErrorMessages();
            HookAccountLoginSecurity.INSTANCE.fa(cl);
            restoreLocalEditorImages();

            File obsoleteTriggerCleanup = new File(
                    "/data/data/com.deepseek.chat/files/deekseep_trigger_cleanup_v1");
            if (!obsoleteTriggerCleanup.exists()) {
                int obsoleteTriggers = ChatEditorUi.removeObsoleteLocalSessionProtection();
                if (obsoleteTriggers > 0) {
                    log("removed obsolete local-session triggers=" + obsoleteTriggers);
                }
                try { overwriteTextFile(obsoleteTriggerCleanup.getPath(), "1"); }
                catch (Throwable ignored) {}
            }
            boolean hasLocalSessionBackups = ChatEditorUi.hasLocalSessionBackups();
            int restoredLocal = hasLocalSessionBackups
                    ? ChatEditorUi.restoreLocalConversations() : 0;
            if (restoredLocal > 0) {
                log("restored local conversations before WCDB startup=" + restoredLocal);
            }
            int repairedHeads = hasLocalSessionBackups
                    ? ChatEditorUi.repairFrozenCurrentMessageIds() : 0;
            if (repairedHeads > 0) {
                log("repaired frozen conversation heads before WCDB startup=" + repairedHeads);
            }
            FROZEN_SESSION_HEADS.clear();
            if (hasLocalSessionBackups) {
                FROZEN_SESSION_HEADS.putAll(ChatEditorUi.frozenCurrentMessageIds());
            }
            if (isAutoBackup()) {
                new Thread(new Runnable() { public void run() {
                    try { DeekseepTools.maybeAutoBackup(); } catch (Throwable ignored) {}
                }}, "Deekseep-Auto-Backup").start();
            }

            HookUiSurface.INSTANCE.hookHomeGreeting(cl);
            HookChatPipeline.INSTANCE.hookChatRequest(cl);
            ensureOptionalHooksForEnabledFeatures(cl);
            try { HookAttachmentPipeline.INSTANCE.installExpertHistoryImagePreserver(cl); }
            catch (Throwable t) { log("install history bridge wiring failed: " + t); }

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

            HookChatPipeline.INSTANCE.hookSafetyRetraction(cl);
            HookChatPipeline.INSTANCE.installServerCapture(cl);
            HookChatPipeline.INSTANCE.hookContentFilterApply(cl);
            HookChatPipeline.INSTANCE.installMsgRebuildCapture(cl);
            HookChatPipeline.INSTANCE.hookStatusWrite(cl);
            HookChatPipeline.INSTANCE.hookTemplateProbe(cl);
            HookChatPipeline.INSTANCE.hookFinalMessageMerge(cl);
            HookChatPipeline.INSTANCE.hookFinalMessageApply(cl);
            HookAttachmentPipeline.INSTANCE.hookExpertUnlock(cl);
            hookV236LocalChatQuotaUnlock(cl);
            hookV241LocalChatQuotaUnlock(cl);
            hookV236ThinkingCodeCopy(cl);
            hookV241ThinkingCodeCopy(cl);
            hookV236AllFileTypes(cl);
            hookV241AllFileTypes(cl);
            HookAccountLoginSecurity.INSTANCE.hookRegionalLoginUnlock(cl);
            HookAccountLoginSecurity.INSTANCE.hookLoginEntryPasswordUnlock(cl);
            HookAccountLoginSecurity.INSTANCE.hookRegionOverride(cl);
            HookAccountLoginSecurity.INSTANCE.hookCandidatePasswordLoginCapture(cl);
            HookAccountLoginSecurity.INSTANCE.hookCandidateRegistrationCaptchaCallbacks(cl);
            try { HookAccountLoginSecurity.INSTANCE.installRiskSdkNeutralizer(cl); }
            catch (Throwable t) { log("installRiskSdkNeutralizer wiring failed: " + t); }
            try { HookAccountLoginSecurity.INSTANCE.installLoginRiskLogger(cl); }
            catch (Throwable t) { log("installLoginRiskLogger wiring failed: " + t); }
            try { HookAttachmentPipeline.INSTANCE.installExpertUploadGate(cl); }
            catch (Throwable t) { log("installExpertUploadGate wiring failed: " + t); }
            try { installNetworkPayloadCapture(cl); } catch (Throwable t) { log("installNetworkPayloadCapture wiring failed: " + t); }
            try { HookChatPipeline.INSTANCE.installPowManagerCapture(cl); } catch (Throwable t) { log("installPowManagerCapture wiring failed: " + t); }
            try { HookAttachmentPipeline.INSTANCE.f9(cl); }
            catch (Throwable t) { log("f9 wiring failed: " + t); }
            try { installExpertImageFpCapture(cl); } catch (Throwable t) { log("installExpertImageFpCapture wiring failed: " + t); }
            try { HookAttachmentPipeline.INSTANCE.installImageCredentialBridge(cl); }
            catch (Throwable t) { log("installImageCredentialBridge wiring failed: " + t); }
            HookAttachmentPipeline.INSTANCE.hookLocalEditorImageUris(cl);
            HookSessionManagement.INSTANCE.hookLocalSessionDirectoryMerge(cl);
            HookSessionManagement.INSTANCE.hookLocalNativeSessionRefresh(cl);
            hookV236LiveSessionPublish(cl);
            hookV241LiveSessionPublish(cl);
            HookSessionManagement.INSTANCE.hookLocalSessionRemoteReload(cl);
            HookSessionManagement.INSTANCE.hookNativeDetailRequest(cl);
            HookSessionManagement.INSTANCE.hookLocalSessionDeletedFlow(cl);
            HookSessionManagement.INSTANCE.hookLocalSessionDeletedResponse(cl);
            HookSessionManagement.INSTANCE.hookV236FeedbackDeletedFlow(cl);
            hookV241FeedbackDeletedFlow(cl);
            HookSessionManagement.INSTANCE.hookActiveChatSessionCapture(cl);
            HookAgentPipeline.INSTANCE.hookProactiveVisibleThreadFilter(cl);
            HookUiSurface.INSTANCE.hookHostThemeColor(cl);
            HookSessionManagement.INSTANCE.hookComposeVisibleThreadState(cl);
            HookChatPipeline.INSTANCE.hookS11RenderFilter(cl);
            HookChatPipeline.INSTANCE.hookCs1RenderFilter(cl);
            HookAgentPipeline.INSTANCE.hookNativeUiHeartbeatCompletion(cl);
            HookSessionManagement.INSTANCE.hookNativeSessionNavigator(cl);
            HookSessionManagement.INSTANCE.hookHistoryLoadDiagnostics(cl);
            scheduleRealSessionProbe();
            HookUiSurface.INSTANCE.hookSettingsNavigation(cl);
            HookUiSurface.INSTANCE.hookNativeSettingsEntry(cl);
            try { HookSessionManagement.INSTANCE.hookSidebarMultiSelectDelete(cl); } catch (Throwable t) { log("hookSidebarMultiSelectDelete wiring failed: " + t); }
            try { HookSessionManagement.INSTANCE.hookSidebarToggleCleanup(cl); } catch (Throwable t) { log("hookSidebarToggleCleanup wiring failed: " + t); }

            hookSettingsComposable(cl);
        } catch (Throwable error) {
            log("installAllFeatureHooks failed: " + safeThrowableMessage(error));
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean CARRIER_WELCOME_PROMPTED =
            new java.util.concurrent.atomic.AtomicBoolean();
    private static volatile android.app.AlertDialog sCarrierWelcomeDialog = null;

    private void showCarrierWelcomeNoticeIfNeeded(final Activity act) {
        if (act == null || act.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= 17 && act.isDestroyed()) return;
        if (!RuntimeEmbeddingMode.isPassiveHostLoader()) return;
        if (CARRIER_WELCOME_PROMPTED.get()) return;

        try {
            final android.content.SharedPreferences sp =
                    act.getSharedPreferences("deekseep_carrier_prefs", Context.MODE_PRIVATE);
            if (sp.getBoolean("first_open_welcome_shown", false)) {
                CARRIER_WELCOME_PROMPTED.set(true);
                return;
            }

            if (!CARRIER_WELCOME_PROMPTED.compareAndSet(false, true)) {
                return;
            }
            // Immediately persist so re-entrant lifecycle callbacks never stack multiple dialogs
            sp.edit().putBoolean("first_open_welcome_shown", true).apply();

            act.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (act.isFinishing()) return;
                        if (Build.VERSION.SDK_INT >= 17 && act.isDestroyed()) return;
                        if (sCarrierWelcomeDialog != null && sCarrierWelcomeDialog.isShowing()) return;

                        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(act);
                        builder.setTitle("欢迎使用 Deekseep 底包");
                        builder.setMessage("欢迎使用 Deekseep 底包！\n\n"
                                + "- 开源仓库：https://github.com/lllucccian/Deekseep\n"
                                + "- 作者：@lllucccian\n"
                                + "- QQ：1106465300\n"
                                + "- Telegram：https://t.me/Deekseepapp\n\n"
                                + "本底包仅供学习交流，严禁倒卖。");
                        builder.setPositiveButton("我知道了", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                dialog.dismiss();
                                sCarrierWelcomeDialog = null;
                            }
                        });
                        builder.setCancelable(false);
                        android.app.AlertDialog dialog = builder.create();
                        sCarrierWelcomeDialog = dialog;
                        dialog.show();
                        try {
                            android.widget.TextView msg = dialog.findViewById(android.R.id.message);
                            if (msg != null) {
                                android.text.util.Linkify.addLinks(msg, android.text.util.Linkify.WEB_URLS);
                                msg.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
                            }
                        } catch (Throwable ignored) {}
                    } catch (Throwable t) {
                        log("Show carrier welcome dialog failed: " + t);
                    }
                }
            });
        } catch (Throwable t) {
            log("Carrier welcome notice check failed: " + t);
        }
    }

    private static final java.util.concurrent.atomic.AtomicBoolean OPEN_LETTER_PROMPTED =
            new java.util.concurrent.atomic.AtomicBoolean();

    private void showOpenSourceLetterNoticeIfNeeded(final Activity act) {
        if (act == null || act.isFinishing()) return;
        if (Build.VERSION.SDK_INT >= 17 && act.isDestroyed()) return;
        if (!isDeepSeekMainActivity(act)) return;
        final String pid = String.valueOf(android.os.Process.myPid());
        if (OPEN_LETTER_PROMPTED.get() || pid.equals(System.getProperty("deekseep.letter.shown.pid"))) return;

        try {
            final android.content.SharedPreferences sp =
                    act.getSharedPreferences("deekseep_open_prefs", Context.MODE_PRIVATE);
            if (sp.getBoolean("dont_show_letter_v175", false)) {
                OPEN_LETTER_PROMPTED.set(true);
                System.setProperty("deekseep.letter.shown.pid", pid);
                return;
            }

            if (!OPEN_LETTER_PROMPTED.compareAndSet(false, true)) {
                return;
            }
            System.setProperty("deekseep.letter.shown.pid", pid);

            main.postDelayed(new Runnable() {
                @Override public void run() {
                    DeekseepUi.showLetterToUsersDialog(act, false);
                }
            }, 600);
        } catch (Throwable t) {
            log("open letter check skipped: " + t);
        }
    }

    private final AtomicBoolean MODULE_INJECTED = new AtomicBoolean(false);

    public void performModuleInjection(Activity act, ClassLoader cl) {
        if (!MODULE_INJECTED.compareAndSet(false, true)) return;
        try {
            installAllFeatureHooks(cl);
            ensureOptionalHooksForEnabledFeatures(cl);
            RemoteFeatureFlags.enforce(cl);
            DeepSeekCacheCleaner.schedule(act);
            HookLogOverlay.onActivityResumed(act);
            z16.initialize(act);
            if (!z16.allowSensitiveFeature(act)) return;
            JavaPluginManager.initialize(act);
            JavaPluginManager.onActivityResumed(act);
            if (AgentToolConfig.enabledFast()) {
                probeConfiguredAgentBackendOnce(act);
                AgentMcpManager.startIfEnabled(act);
                recoverAgentRuns(act, AGENT_OUTBOX_RECOVERED.compareAndSet(false, true));
            }
            installDefaultThinkingCollapse(act);
            consumeHeartbeatConversationIntent(act, act.getIntent());
            if (ChatAppearance.hasEnabledRuntimeEffects()) {
                ChatAppearance.onActivityResumed(act);
            }
            startWelcomeWhaleFrames(act);
            TextWaveEngine.start(act);
            scheduleRouteCheck(navController.get());
            UiLanguage.refreshHost(act);
            SystemBarCompat.apply(act);
            applyThemeSystemBars(act);
            if (isDataOptOutEnforced()) requestTrainingOptOut(act, false);
            HookUiSurface.INSTANCE.maybeInstallAdaptedSettingsEntry(act, cl);
            String languageState = "mode=" + UiLanguage.currentMode(act)
                    + ", host=" + UiLanguage.detectedLanguage(act)
                    + ", effective=" + (UiLanguage.isChinese(act) ? "Chinese" : "English");
            if (!languageState.equals(lastUiLanguageLog)) {
                lastUiLanguageLog = languageState;
                log("UI language " + languageState);
            }
            reportActivationHeartbeat(act);
            ModuleUpdateChecker.checkOnStartup(act);
            if (!proactiveHeartbeatConfigSynced) {
                proactiveHeartbeatConfigSynced = true;
                dispatchProactiveHeartbeatConfig(act, isProactiveHeartbeatEnabled());
            }
            if (isLocalApiEnabled()) {
                requestLocalApiKeepAlive(act, true);
                startz1(act);
                requestPublicTunnelBridge(act);
            } else {
                requestLocalApiKeepAlive(act, false);
                if (z13.isRunning()) z13.stop();
                startz1(act);
            }
            if (HostCompat.isV241() && isLocalApiFloatingWindowEnabled()) {
                requestLocalApiFloatingWindow(act, true);
            }
            final String pid = String.valueOf(android.os.Process.myPid());
            if (!loadToastShown && !pid.equals(System.getProperty("deekseep.toast.shown.pid"))) {
                loadToastShown = true;
                System.setProperty("deekseep.toast.shown.pid", pid);
                try {
                    UiLanguage.toast(act,
                            UiLanguage.text(act,
                                    "Deekseep 已注入 (v" + BuildInfo.DISPLAY_VERSION + ")",
                                    "Deekseep injected (v" + BuildInfo.DISPLAY_VERSION + ")"),
                            android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
            }
            showOpenSourceLetterNoticeIfNeeded(act);
            if (!maybeShowUnsupportedHostVersion(act)) {
                maybeShowDisclaimer(act);
            }
        } catch (Throwable error) {
            log("performModuleInjection failed: " + safeThrowableMessage(error));
        }
    }

    private void onActivityResumedInternal(Activity act, ClassLoader cl) {
        try {
            curAct = new WeakReference<>(act);
            hostApplicationContext = act.getApplicationContext();
            if (!isDeepSeekMainActivity(act)) return;

            // 模块使用凭证门禁：未通过认证前不注入任何功能
            if (BuildInfo.PROTECTED_BUILD && !CloudPromptClient.hasValidLicense(act)) {
                if (DeekseepUi.sActiveActivationDialog == null || !DeekseepUi.sActiveActivationDialog.isShowing()) {
                    main.post(() -> {
                        if (act.isFinishing() || (Build.VERSION.SDK_INT >= 17 && act.isDestroyed())) return;
                        if (CloudPromptClient.hasValidLicense(act)) {
                            ModuleConfigBridge.installTarget(act);
                            showCarrierWelcomeNoticeIfNeeded(act);
                            showOpenSourceLetterNoticeIfNeeded(act);
                            performModuleInjection(act, cl);
                            return;
                        }
                        if (DeekseepUi.sActiveActivationDialog != null && DeekseepUi.sActiveActivationDialog.isShowing()) return;
                        DeekseepUi.showActivationDialog(act, cl, () -> {
                            ModuleConfigBridge.installTarget(act);
                            showCarrierWelcomeNoticeIfNeeded(act);
                            showOpenSourceLetterNoticeIfNeeded(act);
                            performModuleInjection(act, cl);
                        });
                    });
                }
                return;
            }

            // 已持有合法凭证：静默完成注入，无需反复认证
            ModuleConfigBridge.installTarget(act);
            showCarrierWelcomeNoticeIfNeeded(act);
            showOpenSourceLetterNoticeIfNeeded(act);
            performModuleInjection(act, cl);
        } catch (Throwable ignored) {}
    }

    private static void probeConfiguredAgentBackendOnce(Context context) {
        if (!AGENT_PRIVILEGED_BACKEND_PROBED.compareAndSet(false, true)) return;
        String backend = AgentToolConfig.load().backend;
        if (AgentToolConfig.BACKEND_IN_APP.equals(backend)) return;
        AgentDeviceBridge.probe(context, backend,
                new AgentDeviceBridge.StatusCallback() {
                    @Override public void onStatus(AgentDeviceBridge.Status status) {
                        log("Agent configured backend probe connected="
                                + status.connected + " detail=" + status.detail);
                    }
                });
    }

    static void setChatMultiSelect(boolean on) {
        try {
            File ef = new File(HookSessionManagement.CHAT_MULTISELECT_FILE);
            if (on) overwriteTextFile(HookSessionManagement.CHAT_MULTISELECT_FILE, "");
            else {
                ef.delete();
                HookSessionManagement.exitSidebarSelectMode();
            }
        } catch (Throwable ignored) {}
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
            HookSessionManagement.addSidebarCheckMark(act, marks, sessions.get(i), title, delete, sidebarW, checkColor, y, rowH, 0);
        }
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

    static void handleLocalApiPromptPickedFile(Activity act, Uri uri) {
        try {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(act.getContentResolver().openInputStream(uri), "UTF-8"))) {
                String ln;
                while ((ln = br.readLine()) != null) sb.append(ln).append('\n');
            }
            String content = sb.toString().trim();
            overwriteTextFile(LOCAL_API_PROMPT_FILE, content);
            String displayPath = resolveDisplayPath(act, uri);
            writeText(LOCAL_API_PROMPT_SOURCE_FILE, displayPath);
            log("local API prompt imported, length=" + content.length()
                    + ", source=" + displayPath);
            Runnable cb = localApiPromptPickComplete;
            if (cb != null) act.runOnUiThread(cb);
        } catch (Throwable t) { log("handleLocalApiPromptPickedFile err: " + t); }
    }

    static String getLocalApiDefaultPromptPath() {
        try {
            String source = readSmallText(LOCAL_API_PROMPT_SOURCE_FILE);
            if (source != null && source.length() > 0) return source;
        } catch (Throwable ignored) {}
        File pf = new File(LOCAL_API_PROMPT_FILE);
        return pf.exists() && pf.length() > 0 ? pf.getAbsolutePath() : "";
    }

    static void setLocalApiDefaultPromptPath(String path) {
        try {
            if (path == null || path.length() == 0) {
                new File(LOCAL_API_PROMPT_SOURCE_FILE).delete();
            } else {
                overwriteTextFile(LOCAL_API_PROMPT_SOURCE_FILE, path);
            }
        } catch (Throwable ignored) {}
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
        new File(HookChatPipeline.PROMPT_LINK_FILE).delete();
        new File(PROMPT_SOURCE_FILE).delete();
        new File(HookChatPipeline.ENABLED_FILE).delete();
    }

    static boolean isEnabled() {
        return new File(HookChatPipeline.ENABLED_FILE).exists();
    }

    static void setWelcomeWhaleMotionEnabled(boolean enabled) {
        try {
            if (enabled) overwriteTextFile(HookUiSurface.WHALE_MOTION_FILE, "1");
            else new File(HookUiSurface.WHALE_MOTION_FILE).delete();
            HookUiSurface.welcomeWhaleMotionEnabledCache = enabled ? 1 : 0;
        } catch (Throwable error) {
            log("welcome whale setting failed: " + safeThrowableMessage(error));
        }
        Main module = MODULE;
        Activity activity = module == null ? null : module.curAct.get();
        if (enabled) HookUiSurface.welcomeWhaleDrawCount.set(0);
        if (enabled) HookAccountLoginSecurity.activateOptionalHooksNow();
        if (enabled) startWelcomeWhaleFrames(activity);
        else stopWelcomeWhaleFrames(activity);
        if (activity != null && activity.getWindow() != null) {
            activity.getWindow().getDecorView().invalidate();
        }
    }

    static float welcomeWhaleMotionSpeed() {
        float cached = welcomeWhaleMotionSpeedCache;
        if (!Float.isNaN(cached)) return cached;
        try {
            String value = readSmallText(WHALE_MOTION_SPEED_FILE);
            float parsed = value == null ? 0.35f : Float.parseFloat(value.trim());
            cached = Math.max(0.1f, Math.min(1.0f, parsed));
        } catch (Throwable ignored) {
            cached = 0.35f;
        }
        welcomeWhaleMotionSpeedCache = cached;
        return cached;
    }

    static void setWelcomeWhaleMotionSpeed(float speed) {
        float safe = Math.max(0.1f, Math.min(1.0f, speed));
        try {
            overwriteTextFile(WHALE_MOTION_SPEED_FILE,
                    String.format(Locale.US, "%.2f", safe));
            welcomeWhaleMotionSpeedCache = safe;
        } catch (Throwable error) {
            log("welcome whale speed setting failed: " + safeThrowableMessage(error));
        }
    }

    static boolean isTextWaveEnabled() {
        return TextWaveEngine.isEnabled();
    }

    static boolean isMessageDetailsEnabled() {
        return new File(MESSAGE_DETAILS_FILE).isFile();
    }

    static boolean setMessageDetailsEnabled(boolean enabled) {
        try {
            File marker = new File(MESSAGE_DETAILS_FILE);
            if (enabled) overwriteTextFile(MESSAGE_DETAILS_FILE, "1");
            else if (marker.exists() && !marker.delete()) return false;
            return true;
        } catch (Throwable error) {
            log("message details setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean setAutoContinueEnabled(boolean enabled) {
        try {
            File marker = new File(HookChatPipeline.AUTO_CONTINUE_FILE);
            if (enabled) overwriteTextFile(HookChatPipeline.AUTO_CONTINUE_FILE, "1");
            else if (marker.exists() && !marker.delete()) return false;
            if (!enabled) HookChatPipeline.AUTO_CONTINUE_DISPATCHED.clear();
            return true;
        } catch (Throwable error) {
            log("auto-continue setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean setReplyReadyNotificationsEnabled(boolean enabled) {
        try {
            File marker = new File(HookChatPipeline.REPLY_READY_DISABLED_FILE);
            if (enabled) {
                if (marker.exists() && !marker.delete()) return false;
            } else {
                overwriteTextFile(HookChatPipeline.REPLY_READY_DISABLED_FILE, "1");
            }
            return HookChatPipeline.isReplyReadyNotificationsEnabled() == enabled;
        } catch (Throwable error) {
            log("reply-ready notification setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean isLocalChatQuotaUnlockEnabled() {
        if (HostCompat.isV236()) return new File(V236_LOCAL_CHAT_QUOTA_UNLOCK_FILE).isFile();
        if (HostCompat.isV241()) return new File(V241_LOCAL_CHAT_QUOTA_UNLOCK_FILE).isFile();
        return false;
    }

    static boolean setLocalChatQuotaUnlockEnabled(boolean enabled) {
        try {
            final String markerPath;
            if (HostCompat.isV236()) markerPath = V236_LOCAL_CHAT_QUOTA_UNLOCK_FILE;
            else if (HostCompat.isV241()) markerPath = V241_LOCAL_CHAT_QUOTA_UNLOCK_FILE;
            else return false;
            File marker = new File(markerPath);
            if (enabled) overwriteTextFile(markerPath, "1");
            else if (marker.exists() && !marker.delete()) return false;
            if (HostCompat.isV236()) {
                synchronized (V236_LOCAL_CHAT_QUOTA_ORIGINALS) {
                    for (Object config : new ArrayList<Object>(
                            V236_LOCAL_CHAT_QUOTA_ORIGINALS.keySet())) {
                        applyV236LocalChatQuota(config, enabled);
                    }
                }
            } else {
                synchronized (V241_LOCAL_CHAT_QUOTA_ORIGINALS) {
                    for (Object config : new ArrayList<Object>(
                            V241_LOCAL_CHAT_QUOTA_ORIGINALS.keySet())) {
                        applyV241LocalChatQuota(config, enabled);
                    }
                }
            }
            return isLocalChatQuotaUnlockEnabled() == enabled;
        } catch (Throwable error) {
            log("local chat quota setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean isAllFileTypesEnabled() {
        if (HostCompat.isV236()) return new File(V236_ALL_FILE_TYPES_FILE).isFile();
        if (HostCompat.isV241()) return new File(V241_ALL_FILE_TYPES_FILE).isFile();
        return false;
    }

    static boolean setAllFileTypesEnabled(boolean enabled) {
        try {
            final String marker;
            if (HostCompat.isV236()) marker = V236_ALL_FILE_TYPES_FILE;
            else if (HostCompat.isV241()) marker = V241_ALL_FILE_TYPES_FILE;
            else return false;
            File file = new File(marker);
            if (enabled) overwriteTextFile(marker, "1");
            else if (file.exists() && !file.delete()) return false;
            applyAllFileTypesToRepository(HostCompat.isV236()
                    ? v236FileExtensionRepository : v241FileExtensionRepository, enabled);
            return isAllFileTypesEnabled() == enabled;
        } catch (Throwable error) {
            log("all file types setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    private static void applyAllFileTypesToRepository(Object repository, boolean enabled) {
        if (repository == null) return;
        try {
            Field mapField = repository.getClass().getDeclaredField("i");
            mapField.setAccessible(true);
            Object map = mapField.get(repository);
            if (map == null) return;
            Method put = map.getClass().getMethod("put", Object.class, Object.class);
            put.setAccessible(true);
            // Disabling intentionally removes our cached value, so the host reloads its own list.
            if (enabled) put.invoke(map, "support_chat_file_exts", new AllFileExtensionsSet());
            else {
                Method remove = map.getClass().getMethod("remove", Object.class);
                remove.setAccessible(true);
                remove.invoke(map, "support_chat_file_exts");
            }
        } catch (Throwable error) {
            log("all file types repository update failed: " + safeThrowableMessage(error));
        }
    }

    /** Exact code249 adapter: pp1.i supplies the Set consumed by n51.x. */
    private void hookV236AllFileTypes(final ClassLoader cl) {
        if (!HostCompat.isV236()
                || !V236_ALL_FILE_TYPES_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            final Class<?> repository = cl.loadClass("pp1");
            for (Constructor<?> constructor : repository.getDeclaredConstructors()) {
                hook(constructor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object value = chain.proceed();
                        v236FileExtensionRepository = chain.getThisObject();
                        if (isAllFileTypesEnabled()) {
                            applyAllFileTypesToRepository(v236FileExtensionRepository, true);
                        }
                        return value;
                    }
                });
            }
            final Class<?> validator = cl.loadClass("n51");
            int count = 0;
            for (Method method : validator.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!"x".equals(method.getName()) || p.length != 3 || p[0] != int.class
                        || p[1] != String.class || !List.class.isAssignableFrom(p[2])) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        if (isAllFileTypesEnabled()) applyAllFileTypesToRepository(
                                v236FileExtensionRepository, true);
                        return chain.proceed();
                    }
                });
                try { deoptimize(method); } catch (Throwable ignored) {}
                count++;
            }
            if (count == 0) throw new NoSuchMethodException("n51.x(int,String,List)");
            log("installed code249 all-file-types adapter");
        } catch (Throwable error) {
            V236_ALL_FILE_TYPES_HOOK_INSTALLED.set(false);
            log("code249 all-file-types adapter unavailable: " + safeThrowableMessage(error));
        }
    }

    /** Exact code257 adapter: ys1.i supplies the Set consumed by g71.y. */
    private void hookV241AllFileTypes(final ClassLoader cl) {
        if (!HostCompat.isV241()
                || !V241_ALL_FILE_TYPES_HOOK_INSTALLED.compareAndSet(false, true)) return;
        try {
            final Class<?> repository = cl.loadClass("ys1");
            for (Constructor<?> constructor : repository.getDeclaredConstructors()) {
                hook(constructor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object value = chain.proceed();
                        v241FileExtensionRepository = chain.getThisObject();
                        if (isAllFileTypesEnabled()) {
                            applyAllFileTypesToRepository(v241FileExtensionRepository, true);
                        }
                        return value;
                    }
                });
            }
            final Class<?> validator = cl.loadClass("g71");
            int count = 0;
            for (Method method : validator.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!"y".equals(method.getName()) || p.length != 3 || p[0] != int.class
                        || p[1] != String.class || !List.class.isAssignableFrom(p[2])) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        if (isAllFileTypesEnabled()) applyAllFileTypesToRepository(
                                v241FileExtensionRepository, true);
                        return chain.proceed();
                    }
                });
                try { deoptimize(method); } catch (Throwable ignored) {}
                count++;
            }
            if (count == 0) throw new NoSuchMethodException("g71.y(int,String,List)");
            log("installed code257 all-file-types adapter");
        } catch (Throwable error) {
            V241_ALL_FILE_TYPES_HOOK_INSTALLED.set(false);
            log("code257 all-file-types adapter unavailable: " + safeThrowableMessage(error));
        }
    }

    /** Exact 2.3.6/code249 adapter. uo5.p/q are consumed by l10. */
    private void hookV236LocalChatQuotaUnlock(final ClassLoader cl) {
        if (!HostCompat.isV236()
                || !V236_LOCAL_CHAT_QUOTA_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        try {
            final Class<?> configType = cl.loadClass("uo5");
            Field edit = configType.getDeclaredField("p");
            Field regenerate = configType.getDeclaredField("q");
            edit.setAccessible(true);
            regenerate.setAccessible(true);
            V236_EDIT_QUOTA_FIELD = edit;
            V236_REGENERATE_QUOTA_FIELD = regenerate;

            int constructors = 0;
            for (Constructor<?> constructor : configType.getDeclaredConstructors()) {
                hook(constructor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        if (isLocalChatQuotaUnlockEnabled()) {
                            applyV236LocalChatQuota(chain.getThisObject(), true);
                        }
                        return result;
                    }
                });
                try { deoptimize(constructor); } catch (Throwable ignored) {}
                constructors++;
            }

            Class<?> controls = cl.loadClass("l10");
            int consumers = 0;
            for (Method method : controls.getDeclaredMethods()) {
                final int configIndex = findExactParameter(method.getParameterTypes(), configType);
                if (configIndex < 0) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        if (isLocalChatQuotaUnlockEnabled()) {
                            applyV236LocalChatQuota(chain.getArg(configIndex), true);
                        }
                        return chain.proceed();
                    }
                });
                try { deoptimize(method); } catch (Throwable ignored) {}
                consumers++;
            }
            log("installed code249 local chat quota unlock constructors=" + constructors
                    + " consumers=" + consumers);
        } catch (Throwable error) {
            V236_LOCAL_CHAT_QUOTA_HOOKS_INSTALLED.set(false);
            log("code249 local chat quota unlock unavailable: "
                    + safeThrowableMessage(error));
        }
    }

    static boolean isThinkingCodeCopyEnabled() {
        if (HostCompat.isV236()) return new File(V236_THINKING_CODE_COPY_FILE).isFile();
        if (HostCompat.isV241()) return new File(THINKING_CODE_COPY_FILE).isFile();
        return false;
    }

    static boolean setThinkingCodeCopyEnabled(boolean enabled) {
        try {
            final String markerPath;
            if (HostCompat.isV236()) markerPath = V236_THINKING_CODE_COPY_FILE;
            else if (HostCompat.isV241()) markerPath = THINKING_CODE_COPY_FILE;
            else return false;
            File marker = new File(markerPath);
            if (enabled) overwriteTextFile(markerPath, "1");
            else if (marker.exists() && !marker.delete()) return false;
            return isThinkingCodeCopyEnabled() == enabled;
        } catch (Throwable error) {
            log("thinking code copy setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean setTextWaveEnabled(boolean enabled) {
        Main module = MODULE;
        Activity activity = module == null ? null : module.curAct.get();
        boolean saved = TextWaveEngine.setEnabled(activity, enabled);
        if (saved && enabled) HookAccountLoginSecurity.activateOptionalHooksNow();
        return saved;
    }

    static float textWaveSpeed() {
        return TextWaveEngine.speed();
    }

    static boolean setTextWaveSpeed(float speed) {
        return TextWaveEngine.setSpeed(speed);
    }

    static boolean setHomeGreeting(String greeting) {
        String safe = HookUiSurface.sanitizeHomeGreeting(greeting);
        try {
            File file = new File(HookUiSurface.HOME_GREETING_FILE);
            if (safe.length() == 0) {
                return !file.exists() || file.delete();
            }
            overwriteTextFile(HookUiSurface.HOME_GREETING_FILE, safe);
            return true;
        } catch (Throwable error) {
            log("home greeting setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean isLegacySettingsEntryEnabled() {
        return !HookUiSurface.isNativeSettingsEntryEnabled();
    }

    static boolean isSwipeSettingsEnabled() {
        return new File(SWIPE_SETTINGS_ENABLED_FILE).isFile();
    }

    static boolean isDualChatEnabled() {
        return new File(DUAL_CHAT_ENABLED_FILE).isFile();
    }

    static boolean setDualChatEnabled(boolean enabled) {
        try {
            if (enabled && isSwipeSettingsEnabled()) {
                setSwipeSettingsEnabled(false);
            }
            File marker = new File(DUAL_CHAT_ENABLED_FILE);
            if (enabled) overwriteTextFile(marker.getPath(), "1");
            else if (marker.exists() && !marker.delete()) return false;
            boolean saved = isDualChatEnabled() == enabled;
            if (saved && enabled) HookAccountLoginSecurity.activateOptionalHooksNow();
            return saved;
        } catch (Throwable error) {
            log("dual chat update failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    /**
     * Whether the chat page shows the compaction chip. On by default — the marker file records an
     * opt-<em>out</em>, because whether the route predicate recognises a build's chat route can only
     * be judged on the device.
     */
    static boolean isChatCompactionButtonEnabled() {
        return !new File(CHAT_COMPACTION_BUTTON_FILE).isFile();
    }

    static boolean setChatCompactionButtonEnabled(boolean enabled) {
        try {
            File marker = new File(CHAT_COMPACTION_BUTTON_FILE);
            if (enabled) {
                if (marker.exists() && !marker.delete()) return false;
            } else {
                overwriteTextFile(marker.getPath(), "1");
            }
            boolean saved = isChatCompactionButtonEnabled() == enabled;
            if (saved) {
                final Main module = MODULE;
                if (module != null && module.main != null) {
                    module.main.post(new Runnable() {
                        @Override public void run() { module.syncCompactionButtonWithRoute(); }
                    });
                }
            }
            return saved;
        } catch (Throwable error) {
            log("chat compaction button update failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean setSwipeSettingsEnabled(boolean enabled) {
        try {
            if (enabled && isDualChatEnabled()) {
                setDualChatEnabled(false);
            }
            File marker = new File(SWIPE_SETTINGS_ENABLED_FILE);
            if (enabled) overwriteTextFile(marker.getPath(), "1");
            else if (marker.exists() && !marker.delete()) return false;
            return isSwipeSettingsEnabled() == enabled;
        } catch (Throwable error) {
            log("swipe settings entry update failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean setNativeSettingsEntryEnabled(boolean enabled) {
        try {
            File enabledMarker = new File(HookUiSurface.NATIVE_SETTINGS_ENTRY_ENABLED_FILE);
            File disabled = new File(HookUiSurface.NATIVE_SETTINGS_ENTRY_DISABLED_FILE);
            if (enabled) {
                overwriteTextFile(HookUiSurface.NATIVE_SETTINGS_ENTRY_ENABLED_FILE, "1");
                if (disabled.exists() && !disabled.delete()) return false;
            } else {
                if (enabledMarker.exists() && !enabledMarker.delete()) return false;
                overwriteTextFile(HookUiSurface.NATIVE_SETTINGS_ENTRY_DISABLED_FILE, "1");
            }
            Main module = MODULE;
            if (module != null && module.main != null) {
                module.main.post(new Runnable() {
                    @Override public void run() {
                        if (NativeSettingsEntryPolicy.showFloating(
                                enabled, HookUiSurface.nativeSettingsRowHooked,
                                HookUiSurface.nativeSettingsRowEmitted)) {
                            module.showButton();
                        } else {
                            module.hideButton();
                        }
                    }
                });
            }
            return true;
        } catch (Throwable error) {
            log("native settings entry setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean setLegacySettingsEntryEnabled(boolean enabled) {
        return setNativeSettingsEntryEnabled(!enabled);
    }

    static boolean isDataOptOutEnforced() {
        return new File(DATA_OPT_OUT_ENFORCED_FILE).isFile();
    }

    static boolean setDataOptOutEnforced(Context context, boolean enabled) {
        try {
            File marker = new File(DATA_OPT_OUT_ENFORCED_FILE);
            if (enabled) overwriteTextFile(marker.getPath(), "1");
            else if (marker.exists() && !marker.delete()) return false;
            DATA_OPT_OUT_SYNCED.set(false);
            if (enabled && context != null) requestTrainingOptOut(context, true);
            return true;
        } catch (Throwable error) {
            log("data optimization lock setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean setHotUpdateDisabled(boolean disabled) {
        try {
            File marker = new File(HookConfigRollout.HOT_UPDATE_DISABLED_FILE);
            if (disabled) overwriteTextFile(marker.getPath(), "1");
            else if (marker.exists() && !marker.delete()) return false;
            return true;
        } catch (Throwable error) {
            log("hot-update setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    private static void requestTrainingOptOut(final Context context, final boolean showResult) {
        if (context == null || !isDataOptOutEnforced()
                || (!showResult && DATA_OPT_OUT_SYNCED.get())
                || !DATA_OPT_OUT_SYNC_RUNNING.compareAndSet(false, true)) return;
        final Context app = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                AccountManager.ServerValidation result = AccountManager.setTrainingAllowed(
                        app, hostClassLoader, false);
                if (result.valid) DATA_OPT_OUT_SYNCED.set(true);
                DATA_OPT_OUT_SYNC_RUNNING.set(false);
                log("data optimization opt-out sync=" + result.valid
                        + (result.error == null ? "" : ", error=" + result.error));
                if (!showResult) return;
                final String message = result.valid
                        ? UiLanguage.text(app, "已关闭数据用于优化体验",
                                "Data use for service improvement is now off")
                        : UiLanguage.text(app, "禁用已启用，但服务器同步失败：",
                                "Data-use blocking is enabled, but server sync failed: ")
                                + (result.error == null ? "unknown" : result.error);
                Main module = MODULE;
                Handler handler = module == null ? null : module.main;
                if (handler == null) handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override public void run() {
                        try { Toast.makeText(app, message, Toast.LENGTH_LONG).show(); }
                        catch (Throwable ignored) {}
                    }
                });
            }
        }, "deekseep-training-opt-out").start();
    }

    private static float nextWelcomeWhaleAngle() {
        long now = SystemClock.uptimeMillis();
        long previous = welcomeWhaleAngleAt;
        welcomeWhaleAngleAt = now;
        if (previous <= 0L || now <= previous) return HookUiSurface.welcomeWhaleAngle;
        long elapsed = Math.min(100L, now - previous);
        float degreesPerSecond = 90f * welcomeWhaleMotionSpeed();
        HookUiSurface.welcomeWhaleAngle = (HookUiSurface.welcomeWhaleAngle
                + degreesPerSecond * elapsed / 1000f) % 360f;
        return HookUiSurface.welcomeWhaleAngle;
    }

    private static void startWelcomeWhaleFrames(final Activity activity) {
        if (activity == null || !HookUiSurface.isWelcomeWhaleMotionEnabled()) return;
        Activity existing = welcomeWhaleActivity.get();
        if (existing == activity && !welcomeWhaleRenderViews.isEmpty()) {
            // Activity.onResume can be delivered repeatedly while the Local API companion binds.
            // Restarting the frame driver every time cancels it before its first long-running
            // cadence and presents exactly as a whale that moves briefly, then freezes.
            return;
        }
        welcomeWhaleActivity = new WeakReference<>(activity);
        final int generation = welcomeWhaleFrameGeneration.incrementAndGet();
        final View decor = activity.getWindow() == null
                ? null : activity.getWindow().getDecorView();
        if (decor == null) return;
        ArrayList<WeakReference<View>> renderViews = new ArrayList<>();
        collectWelcomeWhaleRenderViews(decor, renderViews);
        if (renderViews.isEmpty()) return;
        // ComposeView and AndroidComposeView are commonly nested. Invalidating both doubles the
        // frame workload, so keep only the deepest render target.
        welcomeWhaleRenderViews = Collections.singletonList(
                renderViews.get(renderViews.size() - 1));
        welcomeWhaleAngleAt = SystemClock.uptimeMillis();
        log("welcome whale frame driver started views=1");
        decor.post(new Runnable() {
            int frame;
            @Override public void run() {
                Activity current = welcomeWhaleActivity.get();
                if (generation != welcomeWhaleFrameGeneration.get()
                        || current != activity || !HookUiSurface.isWelcomeWhaleMotionEnabled()
                        || activity.isFinishing()
                        || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
                // Screen recorders, accessibility overlays and system bubbles can temporarily
                // take window focus while the activity remains visible.  Focus is therefore not
                // a valid animation lifecycle signal.
                if (!decor.isShown()
                        || decor.getWindowVisibility() != View.VISIBLE) {
                    decor.postDelayed(this, 500L);
                    return;
                }
                // Compose can replace AndroidComposeView while screen recording, changing the
                // display refresh mode, or recreating the navigation root.  Refresh forever at a
                // low cadence instead of assuming the first captured view remains authoritative.
                if (frame == 0 || frame == 30 || frame % 120 == 0) {
                    ArrayList<WeakReference<View>> refreshed = new ArrayList<>();
                    collectWelcomeWhaleRenderViews(decor, refreshed);
                    if (!refreshed.isEmpty()) {
                        welcomeWhaleRenderViews = Collections.singletonList(
                                refreshed.get(refreshed.size() - 1));
                    }
                }
                frame++;
                // Advance time from the lifecycle driver, not from Painter.draw(). Render-node
                // caching can legitimately skip draw calls during recording; tying the angle to
                // draw made the whale appear frozen until the next full recomposition.
                nextWelcomeWhaleAngle();
                long sinceDraw = SystemClock.uptimeMillis() - HookUiSurface.welcomeWhaleLastDrawAt;
                boolean activelyDrawing = HookUiSurface.welcomeWhaleLastDrawAt != 0L
                        && sinceDraw <= 1_500L && !HookUiSurface.WELCOME_WHALE_PAINTERS.isEmpty();
                // The captured painter remains alive after navigating away from Home. Continuing
                // to invalidate its hidden Compose tree at animation cadence wasted CPU/GPU in
                // chat and settings. Probe at 2 Hz while dormant; the first draw after returning
                // to Home switches immediately back to the visible cadence.
                boolean dormant = HookUiSurface.welcomeWhaleLastDrawAt != 0L && !activelyDrawing;
                Method invalidateScope = HookUiSurface.welcomeWhaleScopeInvalidate;
                Object previouslyCapturedDrawNode = welcomeWhaleDrawNode.get();
                // The captured draw node is the exact rotating logo. Recompose its surrounding
                // scope only as a one-Hz recovery probe; doing both on every tick needlessly
                // recomposed the home page twenty times per second.
                boolean needsScopeRecovery = dormant || previouslyCapturedDrawNode == null
                        || frame == 0 || frame % 20 == 0;
                if (needsScopeRecovery && invalidateScope != null
                        && !HookUiSurface.WELCOME_WHALE_SCOPES.isEmpty()) {
                    ArrayList<Object> scopes;
                    synchronized (HookUiSurface.WELCOME_WHALE_SCOPES) {
                        scopes = new ArrayList<>(HookUiSurface.WELCOME_WHALE_SCOPES.keySet());
                    }
                    for (Object scope : scopes) {
                        if (scope == null) continue;
                        try {
                            Object invalidation = invalidateScope.invoke(
                                    scope, Long.valueOf(frame));
                            if (welcomeWhaleInvalidateLogged.compareAndSet(false, true)) {
                                log("welcome whale scope invalidate result="
                                        + String.valueOf(invalidation));
                            }
                        } catch (Throwable ignored) {
                            HookUiSurface.WELCOME_WHALE_SCOPES.remove(scope);
                        }
                    }
                }
                if (frame == 30) {
                    log("welcome whale frame driver tick scopes="
                            + HookUiSurface.WELCOME_WHALE_SCOPES.size()
                            + " draws=" + HookUiSurface.welcomeWhaleDrawCount.get());
                }
                Method invalidateDrawNode = welcomeWhaleDrawNodeInvalidate;
                Object drawNode = welcomeWhaleDrawNode.get();
                boolean drawNodeRequested = false;
                // A restart scope can report success while Compose keeps the already-cached
                // Painter draw node.  That was the regression which made the whale move for a
                // handful of frames and then freeze.  Invalidate the captured whale draw node on
                // every active tick, independently of the recomposition result.
                if (invalidateDrawNode != null && drawNode != null) {
                    try {
                        invalidateDrawNode.invoke(null, drawNode);
                        drawNodeRequested = true;
                        if (welcomeWhaleDrawNodeLogged.compareAndSet(false, true)) {
                            log("welcome whale draw node invalidation active type="
                                    + drawNode.getClass().getName());
                        }
                    } catch (Throwable ignored) {
                        welcomeWhaleDrawNode = new WeakReference<>(null);
                    }
                }
                final boolean saver = isSystemPowerSaver(activity);
                // A slowly rotating logo remains smooth at 20 FPS. Power saver uses 10 FPS.
                // Keeping this below the display refresh rate bounds Compose and driver work.
                final long frameDelay = dormant ? 500L : (saver ? 125L : 67L);
                // The targeted draw-node invalidation is enough during normal operation.  Wake
                // the containing Android view only as a low-frequency recovery fallback, which
                // avoids continuously redrawing the entire Compose hierarchy.
                if (!drawNodeRequested || !activelyDrawing || frame % 15 == 0) {
                    for (WeakReference<View> reference : welcomeWhaleRenderViews) {
                        View renderView = reference == null ? null : reference.get();
                        if (renderView != null && renderView.isShown()) {
                            renderView.postInvalidateOnAnimation();
                            break;
                        }
                    }
                }
                if (!activelyDrawing && frame % 10 == 0) {
                    // Rare recovery path for recorders which keep a stale RenderNode despite a
                    // child invalidation. This runs at <=2Hz and only after draw activity stops.
                    decor.postInvalidateOnAnimation();
                }
                decor.postDelayed(this, frameDelay);
            }
        });
    }

    private static void collectWelcomeWhaleRenderViews(
            View view, List<WeakReference<View>> result) {
        if (view == null || result == null) return;
        String name = view.getClass().getName();
        if (name.contains("ComposeView") || name.contains("AndroidComposeView")) {
            result.add(new WeakReference<View>(view));
        }
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            collectWelcomeWhaleRenderViews(group.getChildAt(index), result);
        }
    }

    private static void stopWelcomeWhaleFrames(Activity activity) {
        Activity current = welcomeWhaleActivity.get();
        if (activity == null || current == activity) {
            welcomeWhaleFrameGeneration.incrementAndGet();
            welcomeWhaleActivity = new WeakReference<>(null);
            welcomeWhaleRenderViews = Collections.emptyList();
            welcomeWhaleDrawNode = new WeakReference<>(null);
            welcomeWhaleAngleAt = 0L;
            HookUiSurface.welcomeWhaleLastDrawAt = 0L;
        }
    }

    /** True when the opt-in bundled prompt, rather than an imported prompt, is active. */
    static boolean isEmbeddedPromptEnabled() {
        if (!isEnabled()) return false;
        String source = readSmallText(PROMPT_SOURCE_FILE);
        File stored = new File(EMBEDDED_PROMPT_FILE);
        File activeLink = new File(HookChatPipeline.PROMPT_LINK_FILE);
        File activeCopy = new File(PROMPT_FILE);
        return "内置隐藏提示词".equals(source)
                && stored.isFile() && stored.length() > 0L
                && ((activeCopy.isFile() && activeCopy.length() == stored.length())
                // Read old installations without forcing a migration until the next toggle.
                || (activeLink.exists() && activeLink.length() == stored.length()));
    }

    /** Ensures the opaque bundled prompt is present in DeepSeek's own private no-backup area. */
    static boolean ensureEmbeddedPromptInstalled(Context host) {
        if (HostCompat.isGooglePlay() || host == null) return false;
        File destination = new File(EMBEDDED_PROMPT_FILE);
        InputStream input = null;
        FileOutputStream output = null;
        File temporary = new File(EMBEDDED_PROMPT_FILE + ".new");
        try {
            ClassLoader moduleLoader = Main.class.getClassLoader();
            if (moduleLoader == null) throw new IOException("module class loader unavailable");
            input = moduleLoader.getResourceAsStream(EMBEDDED_PROMPT_RESOURCE);
            if (input == null) throw new IOException("bundled prompt resource unavailable");
            File directory = new File(EMBEDDED_PROMPT_DIR);
            if (!directory.exists() && !directory.mkdirs()) {
                throw new IOException("private prompt directory unavailable");
            }
            output = new FileOutputStream(temporary, false);
            byte[] buffer = new byte[8192];
            int count;
            long total = 0L;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                output.write(buffer, 0, count);
                total += count;
            }
            output.flush();
            try { output.getFD().sync(); } catch (Throwable ignored) {}
            output.close();
            output = null;
            if (total <= 0L || temporary.length() != total) {
                throw new IOException("private prompt copy was empty or incomplete");
            }
            if (destination.exists() && !destination.delete()) {
                throw new IOException("old private prompt could not be replaced");
            }
            if (!temporary.renameTo(destination)) {
                copyPromptFile(temporary, destination);
                temporary.delete();
            }
            log("bundled prompt provisioned in private store, bytes=" + total);
            return true;
        } catch (Throwable t) {
            log("bundled prompt provisioning failed: " + t);
            return false;
        } finally {
            try { if (output != null) output.close(); } catch (Throwable ignored) {}
            try { if (input != null) input.close(); } catch (Throwable ignored) {}
            if (temporary.exists()) temporary.delete();
        }
    }

    static boolean isSystemPowerSaver(Context context) {
        if (context == null) return false;
        try {
            PowerManager manager = (PowerManager) context.getSystemService(
                    Context.POWER_SERVICE);
            return manager != null && manager.isPowerSaveMode();
        } catch (Throwable ignored) {
            return false;
        }
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
        File activeLink = new File(HookChatPipeline.PROMPT_LINK_FILE);
        File copiedPrompt = new File(PROMPT_FILE);
        // Legacy builds kept a symlink at the link path into no_backup. Android 15/SELinux
        // denies following that symlink (EACCES at open), which used to fail the whole snapshot.
        // Treat the link as evidence of an active embedded prompt without copying through it;
        // restore re-copies from the private store instead.
        boolean linkedPrompt = activeLink.exists();
        File effectivePrompt = linkedPrompt ? null : copiedPrompt;
        boolean hadPrompt = linkedPrompt
                || (copiedPrompt.exists() && copiedPrompt.length() > 0L);
        if (hadPrompt && effectivePrompt != null) {
            copyPromptFile(effectivePrompt, previousPrompt);
        }
        File source = new File(PROMPT_SOURCE_FILE);
        if (source.exists() && source.length() > 0L) copyPromptFile(source, previousSource);
        overwriteTextFile(EMBEDDED_PREVIOUS_STATE_FILE,
                "enabled=" + (isEnabled() ? "1" : "0") + "\n"
                        + "prompt=" + (hadPrompt ? "1" : "0") + "\n"
                        + "linked=" + (linkedPrompt ? "1" : "0"));
        log("previous prompt state snapshotted enabled=" + isEnabled()
                + " prompt=" + hadPrompt + " linked=" + linkedPrompt);
    }

    private static void restorePreviousPromptState() throws Throwable {
        String state = readSmallText(EMBEDDED_PREVIOUS_STATE_FILE);
        new File(HookChatPipeline.PROMPT_LINK_FILE).delete();
        new File(PROMPT_FILE).delete();
        new File(PROMPT_SOURCE_FILE).delete();
        HookAccountLoginSecurity.setEnabled(false);
        if (state == null) return;
        if (state.contains("prompt=1")) {
            if (state.contains("linked=1")) {
                // The previous active prompt was the embedded one reached through the legacy
                // symlink; re-copy it from the private store without following any symlink.
                File stored = new File(EMBEDDED_PROMPT_FILE);
                if (stored.isFile() && stored.length() > 0L) {
                    copyPromptFile(stored, new File(PROMPT_FILE));
                }
            } else {
                copyPromptFile(new File(EMBEDDED_PREVIOUS_PROMPT_FILE),
                        new File(PROMPT_FILE));
            }
        }
        File previousSource = new File(EMBEDDED_PREVIOUS_SOURCE_FILE);
        if (previousSource.exists() && previousSource.length() > 0L) {
            copyPromptFile(previousSource, new File(PROMPT_SOURCE_FILE));
        }
        HookAccountLoginSecurity.setEnabled(state.contains("enabled=1"));
        new File(EMBEDDED_PREVIOUS_PROMPT_FILE).delete();
        new File(EMBEDDED_PREVIOUS_SOURCE_FILE).delete();
        new File(EMBEDDED_PREVIOUS_STATE_FILE).delete();
        log("previous prompt state restored enabled=" + isEnabled());
    }

    /** Enables the mainland-only hidden embedded prompt without exposing its source in the UI. */
    static boolean setEmbeddedPromptEnabled(Context host, boolean enabled) {
        if (HostCompat.isGooglePlay()) return false;
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
            // Android 15/SELinux can deny following an absolute symlink from files/ into
            // no_backup/ even though both paths belong to DeepSeek. The old implementation then
            // failed its own length verification with EACCES. Keep the opaque canonical resource
            // in no_backup, but atomically copy its bytes to the injector's normal prompt file.
            new File(HookChatPipeline.PROMPT_LINK_FILE).delete();
            new File(PROMPT_FILE).delete();
            copyPromptFile(new File(EMBEDDED_PROMPT_FILE), new File(PROMPT_FILE));
            writeText(PROMPT_SOURCE_FILE, "内置隐藏提示词");
            HookAccountLoginSecurity.setEnabled(true);
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

    static boolean setRiskBypassEnabled(boolean on) {
        try {
            File flag = new File(HookAccountLoginSecurity.RISK_BYPASS_FILE);
            if (on) {
                overwriteTextFile(HookAccountLoginSecurity.RISK_BYPASS_FILE, "1");
                return flag.isFile();
            }
            return !flag.exists() || flag.delete();
        } catch (Throwable t) {
            log("risk bypass setting failed: " + safeThrowableMessage(t));
            return false;
        }
    }

    static void setNoCensor(boolean on) {
        try {
            File ef = new File(HookChatPipeline.NO_CENSOR_FILE);
            if (on) overwriteTextFile(HookChatPipeline.NO_CENSOR_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    static void setNoCensorContextRetentionEnabled(boolean on) {
        try {
            File disabled = new File(HookChatPipeline.NO_CENSOR_CONTEXT_DISABLED_FILE);
            if (on) disabled.delete();
            else overwriteTextFile(HookChatPipeline.NO_CENSOR_CONTEXT_DISABLED_FILE, "1");
        } catch (Throwable ignored) {}
    }

    static void setLocalApiNoCensor(boolean on) {
        try {
            File ef = new File(HookChatPipeline.LOCAL_API_NO_CENSOR_FILE);
            if (on) overwriteTextFile(HookChatPipeline.LOCAL_API_NO_CENSOR_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    static boolean isLocalApiDefaultPromptEnabled() {
        return new File(LOCAL_API_PROMPT_ENABLED_FILE).exists();
    }

    static void setLocalApiDefaultPromptEnabled(boolean on) {
        try {
            File ef = new File(LOCAL_API_PROMPT_ENABLED_FILE);
            if (on) overwriteTextFile(LOCAL_API_PROMPT_ENABLED_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    static String getLocalApiDefaultPrompt() {
        return readSmallText(LOCAL_API_PROMPT_FILE);
    }

    static void setLocalApiDefaultPrompt(String prompt) {
        try {
            if (prompt == null || prompt.trim().length() == 0) {
                new File(LOCAL_API_PROMPT_FILE).delete();
            } else {
                overwriteTextFile(LOCAL_API_PROMPT_FILE, prompt.trim());
            }
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

    static void setExpertUnlock(boolean on) {
        if (HostCompat.isV236()) {
            RemoteFeatureFlags.setMode(hostClassLoader,
                    RemoteFeatureFlags.V236_FORCE_EXPERT_MODEL,
                    on ? RemoteFeatureFlags.FORCE_ON : RemoteFeatureFlags.FOLLOW);
            return;
        }
        if (HostCompat.isV241()) {
            RemoteFeatureFlags.setMode(hostClassLoader,
                    RemoteFeatureFlags.V241_FORCE_EXPERT_MODEL,
                    on ? RemoteFeatureFlags.FORCE_ON : RemoteFeatureFlags.FOLLOW);
            return;
        }
        try {
            File ef = new File(HookAttachmentPipeline.EXPERT_UNLOCK_FILE);
            if (on) overwriteTextFile(HookAttachmentPipeline.EXPERT_UNLOCK_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
    }

    /** Saves only the configured deadline. Enabling is deliberately a separate user action. */
    static boolean setFakeMuteUntilMillis(long until) {
        try {
            if (until <= System.currentTimeMillis()) {
                return false;
            }
            overwriteTextFile(HookAccountLoginSecurity.FAKE_MUTE_UNTIL_FILE, Long.toString(until));
            HookAccountLoginSecurity.cachedFakeMuteUntil = until;
            return HookAccountLoginSecurity.fakeMuteUntilMillis() == until;
        } catch (Throwable error) {
            log("native fake mute setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean setFakeMuteEnabled(boolean enabled) {
        try {
            File marker = new File(HookAccountLoginSecurity.FAKE_MUTE_ENABLED_FILE);
            if (!enabled) return !marker.exists() || marker.delete();
            if (HookAccountLoginSecurity.fakeMuteUntilMillis() <= System.currentTimeMillis()) return false;
            overwriteTextFile(marker.getPath(), "1");
            return marker.isFile();
        } catch (Throwable error) {
            log("native fake mute enable failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static void clearFakeMute() {
        setFakeMuteEnabled(false);
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

    static void setGoogleLoginUnlock(boolean on) {
        try {
            File flag = new File(HookAccountLoginSecurity.GOOGLE_LOGIN_UNLOCK_FILE);
            if (on) overwriteTextFile(HookAccountLoginSecurity.GOOGLE_LOGIN_UNLOCK_FILE, "");
            else flag.delete();
            if (on) overwriteTextFile(PASSWORD_LOGIN_UNLOCK_FILE, "");
            else new File(PASSWORD_LOGIN_UNLOCK_FILE).delete();
        } catch (Throwable ignored) {}
    }

    static boolean isPasswordLoginUnlock() {
        return new File(PASSWORD_LOGIN_UNLOCK_FILE).exists();
    }

    static void setWechatMobileLoginUnlock(boolean on) {
        try {
            File flag = new File(HookAccountLoginSecurity.WECHAT_MOBILE_LOGIN_UNLOCK_FILE);
            if (on) overwriteTextFile(HookAccountLoginSecurity.WECHAT_MOBILE_LOGIN_UNLOCK_FILE, "");
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

        String describe() {
            StringBuilder out = new StringBuilder();
            out.append(UiLanguage.text("电池优化：", "Battery optimization: "))
                    .append(dozeExempt
                            ? UiLanguage.text("已设为不优化/不限制", "Unrestricted")
                            : UiLanguage.text("仍受电池优化限制", "Still restricted"))
                    .append(UiLanguage.text("\n后台活动：", "\nBackground activity: "))
                    .append(backgroundRestricted
                            ? UiLanguage.text("系统禁止后台活动", "Blocked by the system")
                            : UiLanguage.text("系统允许后台活动", "Allowed by the system"))
                    .append(UiLanguage.text("\n系统白名单：", "\nSystem allowlist: "))
                    .append(allowed()
                            ? UiLanguage.text("已启用（推荐）", "Enabled (recommended)")
                            : UiLanguage.text("未启用（不影响启动）",
                                    "Not enabled (startup is still allowed)"))
                    .append(UiLanguage.text("\n保活方式：前台服务 + CPU WakeLock + 心跳",
                            "\nKeepalive: foreground service + CPU wake lock + heartbeat"));
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

    static String localApiBackgroundStatus(Context context) {
        return localApiBackgroundState(context).describe();
    }

    static boolean verifyLocalApiBackground(Activity activity) {
        LocalApiBackgroundState state = localApiBackgroundState(activity);
        // Battery exemption is a stability recommendation, not a hard gate. The companion
        // foreground service owns the wake lock and foreground heartbeat in rootless mode.
        if (isLocalApiEnabled()) {
            requestLocalApiKeepAlive(activity, true);
            startz1(activity);
        }
        return state.allowed();
    }

    static boolean isLocalApiKeepAliveDisabled() {
        return new File(LOCAL_API_KEEPALIVE_DISABLED_FILE).isFile();
    }

    static boolean setLocalApiKeepAliveDisabled(Context context, boolean disabled) {
        try {
            File marker = new File(LOCAL_API_KEEPALIVE_DISABLED_FILE);
            if (disabled) overwriteTextFile(LOCAL_API_KEEPALIVE_DISABLED_FILE, "1");
            else marker.delete();
        } catch (Throwable error) {
            log("local API keepalive preference update failed: " + error);
            return false;
        }
        if (context == null) return true;
        if (disabled) {
            // This setting only changes process-retention policy. The HTTP gateway remains in the
            // host process until Android stops it; send an explicit stop even when this process
            // has not yet observed a companion heartbeat.
            if (isLocalApiFloatingWindowEnabled()) {
                try { new File(LOCAL_API_FLOATING_WINDOW_FILE).delete(); }
                catch (Throwable ignored) {}
                requestLocalApiFloatingWindow(context, false);
            }
            return requestLocalApiKeepAlive(context, false, true);
        }
        return !isLocalApiEnabled() || requestLocalApiKeepAlive(context, true, true);
    }

    static String applyz18() {
        try {
            return z18.apply();
        } catch (Throwable error) {
            return UiLanguage.text("执行失败：", "Execution failed: ")
                    + safeThrowableMessage(error);
        }
    }

    static boolean shouldShowLocalApiBatteryReminder(Context context) {
        if (new File(LOCAL_API_BATTERY_REMINDER_DISMISSED_FILE).isFile()) return false;
        LocalApiBackgroundState state = localApiBackgroundState(context);
        // The entry reminder deliberately checks only the OS battery/background policy. It does
        // not re-run any first-launch grant or block the Local API service from starting.
        return !state.dozeExempt || state.backgroundRestricted;
    }

    static void dismissLocalApiBatteryReminder() {
        try {
            overwriteTextFile(LOCAL_API_BATTERY_REMINDER_DISMISSED_FILE, "1");
        } catch (Throwable error) {
            log("local API battery reminder dismissal failed: "
                    + safeThrowableMessage(error));
        }
    }

    static boolean openLocalApiBatterySettings(Activity activity) {
        if (activity == null || activity.isFinishing()) return false;
        Intent[] intents = new Intent[]{
                // Global background-app/battery-usage list. On this ColorOS 16 device it resolves
                // to Settings$AppBatteryUsageActivity, which is the requested management page.
                new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                // ColorOS/OxygenOS background-start management fallback.
                new Intent("com.oplus.battery.permission.startup.StartupAppListActivity")
                        .setPackage("com.oplus.battery"),
                new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
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
        return BuildInfo.LOCAL_API_INCLUDED
                && new File(HookAccountLoginSecurity.LOCAL_API_ENABLED_FILE).exists()
                && (!BuildInfo.PROTECTED_BUILD || (!HostCompat.isV236() && !HostCompat.isV241())
                    || CloudPromptClient.hasLocalApiGrant(hostApplicationContext))
                && z16.allowSensitiveFeature(hostApplicationContext);
    }

    static boolean isLocalApiContextRelayEnabled() {
        if (HostCompat.isV241()) {
            return !new File(LOCAL_API_CONTEXT_RELAY_V241_ENABLED_FILE).isFile();
        }
        return !new File(LOCAL_API_CONTEXT_RELAY_DISABLED_FILE).isFile();
    }

    static boolean isLocalApiLegacyModelRouteEnabled() {
        if (!BuildInfo.PROTECTED_BUILD || !BuildInfo.LOCAL_API_INCLUDED) return false;
        if (HostCompat.isV236()) return new File(V236_LOCAL_API_LEGACY_MODEL_ROUTE_FILE).isFile();
        if (HostCompat.isV241()) return new File(V241_LOCAL_API_LEGACY_MODEL_ROUTE_FILE).isFile();
        return false;
    }

    static boolean isLocalApiUnifiedFlashRouteEnabled() {
        if (!BuildInfo.PROTECTED_BUILD || !BuildInfo.LOCAL_API_INCLUDED) return false;
        if (HostCompat.isV236()) return !isLocalApiLegacyModelRouteEnabled();
        if (HostCompat.isV241()) return !isLocalApiLegacyModelRouteEnabled();
        if (HostCompat.isV250()) return !isLocalApiLegacyModelRouteEnabled();
        return false;
    }

    static boolean setLocalApiLegacyModelRouteEnabled(boolean enabled) {
        if (!BuildInfo.PROTECTED_BUILD || !BuildInfo.LOCAL_API_INCLUDED) return false;
        try {
            final String markerPath;
            if (HostCompat.isV236()) markerPath = V236_LOCAL_API_LEGACY_MODEL_ROUTE_FILE;
            else if (HostCompat.isV241()) markerPath = V241_LOCAL_API_LEGACY_MODEL_ROUTE_FILE;
            else return false;
            File marker = new File(markerPath);
            if (enabled) overwriteTextFile(markerPath, "1");
            else if (marker.exists() && !marker.delete()) return false;
            synchronized (HookAccountLoginSecurity.LOCAL_API_SESSION_LOCK) {
                HookAccountLoginSecurity.LOCAL_API_SESSIONS.clear();
                HookAccountLoginSecurity.LOCAL_API_SESSION_LAST_USED.clear();
                HookAccountLoginSecurity.LOCAL_API_RETIRED_SESSION_IDS.clear();
                localApiSessionsLoaded = true;
                HookAccountLoginSecurity.publishLocalApiInternalSessionIdsLocked();
                HookAccountLoginSecurity.persistReusableApiSessionsLocked();
            }
            return isLocalApiLegacyModelRouteEnabled() == enabled;
        } catch (Throwable error) {
            log("local API legacy model route setting failed: "
                    + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean isLocalApiSerialEnabled() {
        // Preserve the proven, interruption-resistant behaviour for existing installations.
        return !new File(LOCAL_API_SERIAL_DISABLED_FILE).isFile();
    }

    static boolean isLocalApiForceReasoningEnabled() {
        return new File(LOCAL_API_FORCE_REASONING_FILE).isFile();
    }

    static void setLocalApiForceReasoningEnabled(boolean enabled) {
        try {
            if (enabled) overwriteTextFile(LOCAL_API_FORCE_REASONING_FILE, "1");
            else new File(LOCAL_API_FORCE_REASONING_FILE).delete();
        } catch (Throwable error) {
            log("local API forced reasoning setting failed: "
                    + safeThrowableMessage(error));
        }
    }

    static Set<String> localApiForceReasoningModels() {
        HashSet<String> selected = new HashSet<String>();
        String raw = readSmallText(LOCAL_API_FORCE_REASONING_MODELS_FILE);
        // Migration/default: the old switch applied to every advertised model.
        if (raw == null) {
            if (isLocalApiUnifiedFlashRouteEnabled()) {
                selected.add("deepseek-flash");
                return selected;
            }
            selected.add("deepseek-flash");
            selected.add("deepseek-v4-flash");
            selected.add("deepseek-v4-pro");
            selected.add("deepseek-vision");
            return selected;
        }
        for (String line : raw.split("\\r?\\n")) {
            String model = line.trim().toLowerCase(Locale.US);
            if (model.length() > 0) {
                if ("deespeek-v4.1-flash".equals(model) || "deepseek-v4.1-flash".equals(model)) {
                    selected.add("deepseek-flash");
                } else {
                    selected.add(model);
                }
            }
        }
        if (isLocalApiUnifiedFlashRouteEnabled()) {
            boolean selectedUnified = selected.contains("deepseek-flash")
                    || selected.contains("deespeek-v4.1-flash")
                    || selected.contains("deepseek-v4.1-flash")
                    || selected.contains("deepseek-v4-flash")
                    || selected.contains("deepseek-v4-pro")
                    || selected.contains("deepseek-vision");
            if (selectedUnified) selected.add("deepseek-flash");
        }
        return selected;
    }

    static void setLocalApiForceReasoningModels(Set<String> models) {
        ArrayList<String> sorted = new ArrayList<String>();
        if (models != null) {
            for (String model : models) {
                if (model != null && model.trim().length() > 0) {
                    sorted.add(model.trim().toLowerCase(Locale.US));
                }
            }
        }
        Collections.sort(sorted);
        StringBuilder out = new StringBuilder();
        for (String model : sorted) out.append(model).append('\n');
        try {
            overwriteTextFile(LOCAL_API_FORCE_REASONING_MODELS_FILE, out.toString());
        } catch (Throwable error) {
            log("local API forced reasoning model setting failed: "
                    + safeThrowableMessage(error));
        }
    }

    static boolean shouldForceLocalApiReasoning(String requestedModel) {
        if (!isLocalApiForceReasoningEnabled()) return false;
        String model = requestedModel == null ? ""
                : requestedModel.trim().toLowerCase(Locale.US);
        if ("deepseek-flash".equals(model)
                || "deespeek-v4.1-flash".equals(model)
                || "deepseek-v4.1-flash".equals(model)
                || "deepseek-v4-flash".equals(model)
                || "deepseek-chat".equals(model)) {
            model = "deepseek-flash";
        }
        Set<String> selected = localApiForceReasoningModels();
        if (selected.contains(model)) return true;
        if ("deepseek-reasoner".equals(model) || "deepseek-r1".equals(model)
                || "reasoner".equals(model)) model = "deepseek-flash";
        else if ("deepseek-vl".equals(model) || model.contains("vision")) {
            model = "deepseek-vision";
        }
        return selected.contains(model);
    }

    private static boolean isBuiltinModelId(String id) {
        if (id == null) return false;
        String lower = id.trim().toLowerCase(Locale.US);
        return "deepseek-flash".equals(lower)
                || "deepseek-chat".equals(lower)
                || "deepseek-v4-flash".equals(lower)
                || "deepseek-v4-pro".equals(lower)
                || "deepseek-vision".equals(lower)
                || "deespeek-v4.1-flash".equals(lower)
                || "deepseek-v4.1-flash".equals(lower);
    }

    static String localApiCustomModelsJson() {
        String raw = readSmallText(LOCAL_API_MODEL_CATALOG_FILE);
        if (raw == null || raw.trim().length() == 0) {
            raw = readSmallText(LOCAL_API_CUSTOM_MODELS_FILE);
        }
        if (raw == null || raw.trim().length() == 0) return "[]";
        try {
            JSONArray input = new JSONArray(raw);
            JSONArray custom = new JSONArray();
            HashSet<String> seen = new HashSet<String>();
            for (int i = 0; i < input.length(); i++) {
                JSONObject item = input.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "").trim().toLowerCase(Locale.US);
                if (id.length() == 0 || seen.contains(id) || isBuiltinModelId(id)) continue;
                seen.add(id);
                custom.put(item);
            }
            return custom.toString();
        } catch (Throwable ignored) {
            return "[]";
        }
    }

    static boolean setLocalApiCustomModelsJson(String raw) {
        return setLocalApiModelCatalogJson(raw);
    }

    static String localApiModelCatalogJson() {
        JSONArray catalog = new JSONArray();
        HashSet<String> seen = new HashSet<String>();
        try {
            catalog.put(new JSONObject().put("id", "deepseek-flash").put("native_model", "default"));
            seen.add("deepseek-flash");

            if (!isLocalApiUnifiedFlashRouteEnabled()) {
                catalog.put(new JSONObject().put("id", "deepseek-v4-flash").put("native_model", "default"));
                seen.add("deepseek-v4-flash");
                catalog.put(new JSONObject().put("id", "deepseek-v4-pro").put("native_model", "expert"));
                seen.add("deepseek-v4-pro");
                catalog.put(new JSONObject().put("id", "deepseek-vision").put("native_model", "vision"));
                seen.add("deepseek-vision");
            }

            String raw = readSmallText(LOCAL_API_MODEL_CATALOG_FILE);
            if (raw == null || raw.trim().length() == 0) {
                raw = readSmallText(LOCAL_API_CUSTOM_MODELS_FILE);
            }
            if (raw != null && raw.trim().length() > 0) {
                JSONArray saved = new JSONArray(raw);
                for (int i = 0; i < saved.length(); i++) {
                    JSONObject item = saved.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", "").trim().toLowerCase(Locale.US);
                    if (id.length() == 0 || seen.contains(id)
                            || "deespeek-v4.1-flash".equals(id)) continue;
                    seen.add(id);
                    catalog.put(item);
                }
            }
        } catch (Throwable ignored) {}
        return catalog.toString();
    }

    static boolean setLocalApiModelCatalogJson(String raw) {
        try {
            JSONArray input = new JSONArray(raw == null ? "[]" : raw);
            JSONArray clean = new JSONArray();
            HashSet<String> seen = new HashSet<String>();
            for (int i = 0; i < input.length(); i++) {
                JSONObject item = input.optJSONObject(i);
                if (item == null) continue;
                String id = item.optString("id", "").trim().toLowerCase(Locale.US);
                String target = item.optString("native_model", "default").trim();
                if (id.length() == 0 || id.length() > 96 || seen.contains(id)) continue;
                if (!"expert".equals(target) && !"vision".equals(target)) target = "default";
                seen.add(id);
                clean.put(new JSONObject().put("id", id).put("native_model", target));
            }
            overwriteTextFile(LOCAL_API_MODEL_CATALOG_FILE, clean.toString());
            overwriteTextFile(LOCAL_API_CUSTOM_MODELS_FILE, clean.toString());
            return true;
        } catch (Throwable error) {
            log("local API model catalog setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static boolean isLocalApiExpertBusyFallbackEnabled() {
        return new File(LOCAL_API_EXPERT_BUSY_FALLBACK_FILE).isFile();
    }

    static void setLocalApiExpertBusyFallbackEnabled(boolean enabled) {
        try {
            if (enabled) overwriteTextFile(LOCAL_API_EXPERT_BUSY_FALLBACK_FILE, "1");
            else new File(LOCAL_API_EXPERT_BUSY_FALLBACK_FILE).delete();
        } catch (Throwable error) {
            log("local API expert-busy fallback setting failed: "
                    + safeThrowableMessage(error));
        }
    }

    static boolean isLocalApiAutoRecoveryEnabled() {
        return new File(LOCAL_API_AUTO_RECOVERY_FILE).isFile();
    }

    static void setLocalApiAutoRecoveryEnabled(boolean enabled) {
        try {
            if (enabled) overwriteTextFile(LOCAL_API_AUTO_RECOVERY_FILE, "1");
            else new File(LOCAL_API_AUTO_RECOVERY_FILE).delete();
        } catch (Throwable error) {
            log("local API auto-recovery setting failed: " + safeThrowableMessage(error));
        }
    }

    /** 本次进程累计的补救次数（无正文重试 / 流中断续接）。持久化到文件供设置页展示。 */
    static long localApiRecoveryCount() {
        if (localApiRecoveryCount >= 0L) return localApiRecoveryCount;
        synchronized (HookAccountLoginSecurity.LOCAL_API_SESSION_LOCK) {
            if (localApiRecoveryCount >= 0L) return localApiRecoveryCount;
            String text = readSmallText(LOCAL_API_RECOVERY_COUNT_FILE);
            long value = 0L;
            if (text != null) {
                try { value = Long.parseLong(text.trim()); } catch (Throwable ignored) {}
            }
            localApiRecoveryCount = value;
            return value;
        }
    }

    static void bumpLocalApiRecoveryCount() {
        synchronized (HookAccountLoginSecurity.LOCAL_API_SESSION_LOCK) {
            long value = localApiRecoveryCount();
            value++;
            localApiRecoveryCount = value;
            try {
                overwriteTextFile(LOCAL_API_RECOVERY_COUNT_FILE, String.valueOf(value));
            } catch (Throwable ignored) {}
        }
    }

    static void setLocalApiSerialEnabled(boolean enabled) {
        try {
            if (enabled) new File(LOCAL_API_SERIAL_DISABLED_FILE).delete();
            else overwriteTextFile(LOCAL_API_SERIAL_DISABLED_FILE, "1");
        } catch (Throwable error) {
            log("local API serial policy update failed: " + safeThrowableMessage(error));
        }
    }

    static boolean isLocalApiMultiAccountRoutingEnabled() {
        return z12.enabled();
    }

    static int localApiRoutingAccountCount() {
        return z12.accountCount(hostClassLoader);
    }

    static boolean setLocalApiMultiAccountRoutingEnabled(boolean enabled) {
        boolean applied = z12.setEnabled(enabled, hostClassLoader);
        if (applied && enabled) HookAccountLoginSecurity.activateOptionalHooksNow();
        return applied;
    }

    static void setLocalApiContextRelayEnabled(boolean enabled) {
        try {
            if (HostCompat.isV241()) {
                File flag = new File(LOCAL_API_CONTEXT_RELAY_V241_ENABLED_FILE);
                if (enabled) {
                    if (flag.exists()) flag.delete();
                } else {
                    overwriteTextFile(flag.getAbsolutePath(), "1");
                }
                return;
            }
            if (enabled) new File(LOCAL_API_CONTEXT_RELAY_DISABLED_FILE).delete();
            else overwriteTextFile(LOCAL_API_CONTEXT_RELAY_DISABLED_FILE, "1");
        } catch (Throwable error) {
            log("local API context relay setting failed: "
                    + safeThrowableMessage(error));
        }
    }

    static boolean isLocalApiFloatingWindowEnabled() {
        return new File(LOCAL_API_FLOATING_WINDOW_FILE).isFile();
    }

    static boolean setLocalApiFloatingWindowEnabled(Context context, boolean enabled) {
        if (enabled && isLocalApiKeepAliveDisabled()) {
            HookAccountLoginSecurity.localApiKeepAliveError =
                    "已关闭保活；恢复保活后才能启用小窗";
            return false;
        }
        if (HostCompat.isV236()) {
            return setLocalApiFloatingWindowEnabledV236(context, enabled);
        }
        if (HostCompat.isV241()) {
            return setLocalApiFloatingWindowEnabledV241(context, enabled);
        }
        return false;
    }

    private static boolean setLocalApiFloatingWindowEnabledV236(
            Context context, boolean enabled) {
        return persistAndDispatchFloatingWindow(context, enabled, "code249");
    }

    private static boolean setLocalApiFloatingWindowEnabledV241(
            Context context, boolean enabled) {
        return persistAndDispatchFloatingWindow(context, enabled, "code257");
    }

    private static boolean persistAndDispatchFloatingWindow(
            Context context, boolean enabled, String adapter) {
        try {
            if (enabled) overwriteTextFile(LOCAL_API_FLOATING_WINDOW_FILE, "1");
            else new File(LOCAL_API_FLOATING_WINDOW_FILE).delete();
        } catch (Throwable error) {
            HookAccountLoginSecurity.localApiKeepAliveError = "小窗设置保存失败：" + safeThrowableMessage(error);
            return false;
        }
        boolean launched = requestLocalApiFloatingWindow(context, enabled);
        if (!launched && enabled) {
            try { new File(LOCAL_API_FLOATING_WINDOW_FILE).delete(); } catch (Throwable ignored) {}
        }
        if (launched) log(adapter + " floating keeper setting applied enabled=" + enabled);
        return launched;
    }

    static String localApiFloatingWindowStatus() {
        if (!(HostCompat.isV236() || HostCompat.isV241())) {
            return UiLanguage.text("小窗保活：当前宿主版本不支持",
                    "Floating keeper: unsupported by this host version");
        }
        if (!isLocalApiFloatingWindowEnabled()) {
            return UiLanguage.text("小窗保活：未启用", "Floating keeper: Disabled");
        }
        if (!HookAccountLoginSecurity.localApiFloatingWindowPermission) {
            return UiLanguage.text(
                    "小窗保活：等待悬浮窗权限（已打开系统授权页）",
                    "Floating keeper: waiting for overlay permission");
        }
        if (HookAccountLoginSecurity.localApiFloatingWindowRequested) {
            return UiLanguage.text(
                    "小窗保活：已显示；拖动可吸附屏幕边缘",
                    "Floating keeper: Visible; drag it to either screen edge");
        }
        return UiLanguage.text(
                "小窗保活：正在连接模块前台服务",
                "Floating keeper: connecting to the module foreground service");
    }

    static String localApiConnectionInfo() {
        return z13.connectionInfo();
    }

    static String rotateLocalApiKey(Activity activity) {
        String key = z13.rotateKey(activity);
        return key == null ? UiLanguage.text(activity,
                "密钥轮换失败：请先打开 DeepSeek",
                "Could not rotate the key: open DeepSeek first")
                : z13.connectionInfo();
    }

    static String setCustomLocalApiKey(Activity activity, String key) {
        String error = z13.setCustomKey(activity, key);
        return error == null ? UiLanguage.text(activity, "保存成功", "Saved") : error;
    }

    static String localApiEndpoint() { return z13.endpoint(); }

    /** The one-tap generic API URL is an OpenAI-compatible base and always ends in /v1. */
    static String localApiCopyEndpoint() {
        String value = z13.rootEndpoint();
        if (value == null) return "";
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value.endsWith("/v1") ? value : value + "/v1";
    }

    static String localApiRootEndpoint() { return z13.rootEndpoint(); }

    static String localApiLanEndpoint() { return z13.lanEndpoint(); }

    static boolean isLocalApiHttpsEnabled(Context context) {
        return z5.isEnabled(context);
    }

    static String localApiHttpsStatus(Context context) {
        return z5.status(context);
    }

    static String validateLocalApiHttps(Context context) {
        try {
            return z5.validate(context);
        } catch (Throwable error) {
            return UiLanguage.text(context, "校验失败：", "Verification failed: ")
                    + safeThrowableMessage(error);
        }
    }

    static int localApiPreferredPort(Activity activity) {
        return z13.preferredPort(activity);
    }

    static String setLocalApiPreferredPort(Activity activity, String value) {
        int requested;
        try {
            requested = Integer.parseInt(value == null ? "" : value.trim());
        } catch (Throwable t) {
            return UiLanguage.text(activity, "监听端口必须是数字",
                    "Listener port must be a number");
        }
        String error = z13.setPreferredPort(activity, requested);
        if (error != null) return error;
        boolean restart = z13.isRunning() && isLocalApiEnabled();
        if (restart) {
            z13.stop();
            startz1(activity);
        }
        return UiLanguage.text(activity,
                restart ? "端口已保存，本地 API 已重新监听"
                        : "端口已保存，下次启动本地 API 时生效",
                restart ? "Port saved and the local API listener restarted"
                        : "Port saved; it will apply on the next local API start");
    }

    static Bundle localApiPublicTunnelStatus(Activity activity) {
        return callPublicTunnelProvider(activity,
                XposedActivationProvider.M5, null);
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
                XposedActivationProvider.M3, extras);
    }

    static Bundle setLocalApiPublicTunnelEnabled(Activity activity, boolean enabled) {
        Bundle extras = new Bundle();
        extras.putBoolean("enabled", enabled);
        return callPublicTunnelProvider(activity,
                XposedActivationProvider.M4, extras);
    }

    static Bundle cancelLocalApiPublicTunnelProvisioning(Activity activity) {
        return callPublicTunnelProvider(activity,
                XposedActivationProvider.M8, null);
    }

    static Bundle localApiPinggyTunnelStatus(Activity activity) {
        return callPublicTunnelProvider(activity,
                XposedActivationProvider.M7, null);
    }

    static Bundle setLocalApiPinggyTunnelEnabled(Activity activity, boolean enabled) {
        Bundle extras = new Bundle();
        extras.putBoolean("enabled", enabled);
        return callPublicTunnelProvider(activity,
                XposedActivationProvider.M6, extras);
    }

    static String localApiEndpointForPublicRoot(String root) {
        String value = root == null ? "" : root.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.length() == 0) return "";
        return z2.PROTOCOL_ANTHROPIC.equals(localApiProtocol())
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
        if (XposedActivationProvider.M3.equals(method)) {
            transaction = PublicTunnelBinderBridge.TRANSACTION_CONFIGURE;
        } else if (XposedActivationProvider.M4.equals(method)) {
            transaction = PublicTunnelBinderBridge.TRANSACTION_SET_REQUESTED;
        } else if (XposedActivationProvider.M6.equals(method)) {
            transaction = PublicTunnelBinderBridge.TRANSACTION_SET_PINGGY_REQUESTED;
        } else if (XposedActivationProvider.M7.equals(method)) {
            transaction = PublicTunnelBinderBridge.TRANSACTION_PINGGY_STATUS;
        } else if (XposedActivationProvider.M8.equals(method)) {
            transaction = PublicTunnelBinderBridge.TRANSACTION_CANCEL_PROVISIONING;
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
                .scheme(z20.SCHEME)
                .authority(z20.HOST)
                .appendQueryParameter(z20.QUERY_MODE,
                        z20.MODE_PUBLIC_TUNNEL_BIND)
                .appendQueryParameter(z20.QUERY_TOKEN,
                        z21.CONTROL_TOKEN)
                .build();
        Intent bridge = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        routePassiveHostComponent(bridge, z20.class);
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
                        z20.EXTRA_PUBLIC_TUNNEL_BINDER);
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
                        XposedActivationProvider.M5, null);
                log("public tunnel Binder bridge connected, status="
                        + (status != null && status.getBoolean("accepted", false))
                        + ", binary=" + (status != null
                        && status.getBoolean("binary_available", false)));
            }
        };
        publicTunnelBridgeReceiver = receiver;
        intent.putExtra(z20.EXTRA_PUBLIC_TUNNEL_RECEIVER, receiver);
    }

    static String localApiProtocol() { return z13.protocolMode(); }

    static void setLocalApiProtocol(Activity activity, String protocol) {
        z13.setProtocolMode(activity, protocol);
    }

    static String localApiKey() { return z13.apiKey(); }

    static boolean setPromptSplitInjectionEnabled(boolean enabled) {
        if (!HostCompat.isV236() && !HostCompat.isV241()) return false;
        String path = HostCompat.isV236()
                ? HookChatPipeline.PROMPT_SPLIT_V236_FILE : HookChatPipeline.PROMPT_SPLIT_V241_FILE;
        try {
            File marker = new File(path);
            if (enabled) overwriteTextFile(path, "1");
            else if (marker.exists() && !marker.delete()) return false;
            clearPromptTurnCounters();
            return true;
        } catch (Throwable error) {
            log("prompt split setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    static int promptInjectionInterval() {
        return HookChatPipeline.isPromptSplitInjectionEnabled()
                ? HookChatPipeline.configuredPromptInjectionInterval() : 1;
    }

    static boolean setPromptInjectionInterval(int requested) {
        if (!HostCompat.isV236() && !HostCompat.isV241()) return false;
        int interval = Math.max(1, Math.min(30, requested));
        String path = HostCompat.isV236()
                ? HookChatPipeline.PROMPT_INTERVAL_V236_FILE : HookChatPipeline.PROMPT_INTERVAL_V241_FILE;
        try {
            overwriteTextFile(path, String.valueOf(interval));
            if (HostCompat.isV236()) {
                HookChatPipeline.promptIntervalV236 = interval;
            } else {
                // The code257 UI exposes the interval directly. Saving it must activate the
                // corresponding policy instead of silently retaining every-turn injection.
                overwriteTextFile(HookChatPipeline.PROMPT_SPLIT_V241_FILE, "1");
                HookChatPipeline.promptIntervalV241 = interval;
            }
            clearPromptTurnCounters();
            return true;
        } catch (Throwable error) {
            log("prompt interval save failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    private static void clearPromptTurnCounters() {
        synchronized (HookChatPipeline.PROMPT_TURN_LOCK) {
            HookChatPipeline.PROMPT_TURN_COUNTS.clear();
            HookChatPipeline.PROMPT_LAST_TURN_KEYS.clear();
            HookChatPipeline.PROMPT_LAST_TURN_DECISIONS.clear();
        }
    }

    static void startz1(Context context) {
        // Use a coarser guard here: the enabled file and z16 are sufficient to enter this method.
        // isLocalApiEnabled() also requires hasLocalApiGrant, but the grant is intentionally
        // not persisted across process restarts.  The inner re-enrollment block below handles the
        // no-grant case so that a process restart automatically re-enrolls rather than silently
        // leaving the local API stuck in a disabled state until the user manually toggles.
        if (context == null || !BuildInfo.LOCAL_API_INCLUDED
                || !new File(HookAccountLoginSecurity.LOCAL_API_ENABLED_FILE).exists()
                || !z16.allowSensitiveFeature(context)) return;
        final Context appContext = context.getApplicationContext();
        // The Local API payload key is intentionally not persisted.  After a host process
        // restart, re-enroll before resolving z1; otherwise the facade reports port=0/key empty
        // and the UI misleadingly looks like a broken configuration.  Activation is idempotent
        // and server-bound, so this also keeps a rotated payload key from being reused offline.
        if (!CloudPromptClient.hasLocalApiGrant(appContext)) {
            CloudPromptClient.activate(appContext, new CloudPromptClient.Callback<Boolean>() {
                @Override public void done(Boolean ok, Throwable error) {
                    if (Boolean.TRUE.equals(ok)) {
                        Main.startz1(appContext);
                    } else if (error != null) {
                        Main.log("Local API cloud re-enrollment failed: "
                                + Main.safeThrowableMessage(error));
                    }
                }
            });
            return;
        }
        // startz1 is also reached from non-Activity startup paths. Start the companion here so
        // an enabled gateway can never be left in a cached/frozen DeepSeek process merely because
        // onResume did not run after an APK/module update. Heartbeat callbacks update the age
        // before re-entering this method, so this does not create a control loop.
        if (!Boolean.TRUE.equals(HookAccountLoginSecurity.LOCAL_API_COMPANION_CONTROL.get())) {
            requestLocalApiKeepAlive(appContext, true);
        }
        z13.start(appContext, new z2.Backend() {
            @Override public boolean isReady() {
                return hostClassLoader != null && liveR92 != null && liveQ71 != null;
            }

            @Override public String readinessDetail() {
                if (hostClassLoader == null) return UiLanguage.text(
                        "等待宿主类加载器", "Waiting for host class loader");
                if (liveR92 == null && liveQ71 == null) return UiLanguage.text(
                        "等待原生传输与 PoW 初始化", "Waiting for native transport and PoW");
                if (liveR92 == null) return UiLanguage.text(
                        "等待原生传输初始化", "Waiting for native transport");
                if (liveQ71 == null) return UiLanguage.text(
                        "等待 PoW 初始化", "Waiting for PoW");
                return UiLanguage.text(
                        "原生传输已就绪（排队 " + HookAccountLoginSecurity.LOCAL_API_NATIVE_PERMITS.getQueueLength() + "）",
                        "Native transport ready (queued "
                                + HookAccountLoginSecurity.LOCAL_API_NATIVE_PERMITS.getQueueLength() + ")");
            }

            @Override public z2.CompletionResult complete(
                    z2.CompletionRequest request,
                    z2.DeltaSink sink) throws Exception {
                Main module = MODULE;
                if (module == null) {
                    throw new z2.GatewayException(503, "host_not_ready",
                            "server_error", "Deekseep hook instance is unavailable");
                }
                return module.executeLocalApiCompletion(request, sink);
            }
        });
    }

    static void onRuntimeProtectionRecovered(Context context) {
        if ((!HostCompat.isV236() && !HostCompat.isV241()) || context == null
                || !BuildInfo.LOCAL_API_INCLUDED
                || !new File(HookAccountLoginSecurity.LOCAL_API_ENABLED_FILE).isFile()) return;
        log("runtime protection recovered; restarting enabled Local API");
        requestLocalApiKeepAlive(context, true);
        startz1(context);
    }

    /**
     * Compatibility alias for {@link HookAccountLoginSecurity.CandidateLoginCallback}: the account
     * UI (outside the split hook groups) still references this callback type as a Main member.
     */
    interface CandidateLoginCallback extends HookAccountLoginSecurity.CandidateLoginCallback {}

    /**
     * Runs DeepSeek 2.3.6's own password-login ViewModel without opening its page. The password is
     * passed directly to the host event and is never written by the module.
     */
    static String addCandidateAccountWithPassword(final Activity activity,
            final String identity, final String password, final HookAccountLoginSecurity.CandidateLoginCallback callback) {
        if (activity == null) return "DeepSeek 上下文尚未就绪";
        final String account = identity == null ? "" : identity.trim();
        final String secret = password == null ? "" : password;
        if (account.length() == 0) return "请输入邮箱或手机号";
        if (secret.length() < 8 || secret.length() > 50) return "密码长度必须为 8 到 50 位";
        synchronized (HookAccountLoginSecurity.CANDIDATE_LOGIN_LOCK) {
            if (HookAccountLoginSecurity.candidateLoginCallback != null) return "已有账号正在验证，请稍候";
            HookAccountLoginSecurity.candidateLoginOriginalJson = AccountManager.readCurrentJson(activity.getClassLoader());
            if (HookAccountLoginSecurity.candidateLoginOriginalJson == null) return "当前账号状态不可用，无法安全添加候选账号";
            HookAccountLoginSecurity.candidateLoginCallback = callback;
            HookAccountLoginSecurity.candidateLoginStartedAt = System.currentTimeMillis();
        }
        try {
            ClassLoader cl = activity.getClassLoader();
            Class<?> vmType = HostCompat.load(cl, "ge6");
            Class<?> identityEvent = HostCompat.load(cl, "ae6");
            Class<?> passwordEvent = HostCompat.load(cl, "be6");
            Class<?> submitEvents = HostCompat.load(cl, "qv9");
            Object vm = vmType.getDeclaredConstructor().newInstance();
            Method dispatch = vmType.getDeclaredMethod("e", HostCompat.load(cl, "ce6"));
            dispatch.setAccessible(true);
            Constructor<?> identityCtor = identityEvent.getDeclaredConstructor(String.class);
            Constructor<?> passwordCtor = passwordEvent.getDeclaredConstructor(String.class);
            identityCtor.setAccessible(true);
            passwordCtor.setAccessible(true);
            dispatch.invoke(vm, identityCtor.newInstance(account));
            dispatch.invoke(vm, passwordCtor.newInstance(secret));
            Field submitField = submitEvents.getDeclaredField("t");
            submitField.setAccessible(true);
            dispatch.invoke(vm, submitField.get(null));
            activity.getWindow().getDecorView().postDelayed(new Runnable() {
                @Override public void run() {
                    HookAccountLoginSecurity.CandidateLoginCallback pending;
                    synchronized (HookAccountLoginSecurity.CANDIDATE_LOGIN_LOCK) {
                        if (HookAccountLoginSecurity.candidateLoginCallback == null
                                || System.currentTimeMillis() - HookAccountLoginSecurity.candidateLoginStartedAt < 30000L) return;
                        pending = HookAccountLoginSecurity.candidateLoginCallback;
                        HookAccountLoginSecurity.clearCandidateLoginLocked();
                    }
                    if (pending != null) pending.onResult(false,
                            "登录验证超时，请检查网络、账号密码或验证码要求");
                }
            }, 30500L);
            return null;
        } catch (Throwable t) {
            synchronized (HookAccountLoginSecurity.CANDIDATE_LOGIN_LOCK) { HookAccountLoginSecurity.clearCandidateLoginLocked(); }
            log("candidate password login start failed: " + safeThrowableMessage(t));
            return "当前 DeepSeek 版本不支持后台密码登录";
        }
    }

    static final class NativeUiHeartbeatRequest {
        final Context context;
        final String requestId;
        final boolean taskReminder;
        final String taskKind;
        final String sid;
        final Integer previousHead;
        final boolean reasoning;
        final String fallbackText;
        final long startedAt;
        final AtomicBoolean completing = new AtomicBoolean();

        NativeUiHeartbeatRequest(Context context, String requestId,
                                 boolean taskReminder, String taskKind,
                                 String sid, Integer previousHead,
                                 boolean reasoning, String fallbackText) {
            Context source = context == null ? HookSessionManagement.currentHostContext() : context;
            Context application = source == null ? null : source.getApplicationContext();
            this.context = application == null ? source : application;
            this.requestId = requestId;
            this.taskReminder = taskReminder;
            this.taskKind = taskKind;
            this.sid = sid;
            this.previousHead = previousHead;
            this.reasoning = reasoning;
            this.fallbackText = fallbackText == null ? "" : fallbackText.trim();
            this.startedAt = System.currentTimeMillis();
        }
    }

    /**
     * Only the three model_type values accepted by DeepSeek's completion endpoint may leave the
     * module. ServerChatSession.g is title_type (for example SYSTEM), not model_type; accepting an
     * arbitrary metadata string here turns a due reminder into a notification-only fallback.
     */
    static String normalizeNativeHeartbeatModel(String value) {
        String model = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if ("expert".equals(model) || "vision".equals(model)) return model;
        return "default";
    }

    static int executeHeartbeatToolCalls(
            Context context, List<HeartbeatToolProtocol.ToolCall> calls,
            boolean announce) {
        if (!AgentToolConfig.load().enabled || calls == null || calls.isEmpty()) return 0;
        Context effective = context != null ? context : HookSessionManagement.currentHostContext();
        if (effective == null) return 0;
        HookAccountLoginSecurity.AgentBatchResult batch = calls.size() > 1
                ? new HookAccountLoginSecurity.AgentBatchResult(effective,
                        HeartbeatToolProtocol.cleanScope(calls.get(0).scope))
                : null;
        int completed = 0;
        for (HeartbeatToolProtocol.ToolCall call : calls) {
            if (call == null) continue;
            HookAccountLoginSecurity.AgentStepResult step = null;
            try {
                boolean success = false;
                String scope = HeartbeatToolProtocol.cleanScope(call.scope);
                if (scope.length() == 0) continue;
                HookLogOverlay.event("AGENT", "Tool call",
                        "name=" + call.tool + " id=" + call.id
                                + " scope=" + scope);
                if (!HookAgentPipeline.claimAgentToolExecution(call)) {
                    log("ignored duplicate local tool call tool="
                            + call.tool + " id=" + call.id
                            + " scope=" + scope);
                    HookLogOverlay.event("AGENT", "Tool call ignored",
                            "name=" + call.tool + " id=" + call.id
                                    + " reason=duplicate");
                    continue;
                }
                AgentToolTraceStore.begin(call);
                if (batch != null) batch.register(call);
                step = new HookAccountLoginSecurity.AgentStepResult(effective, scope, call, batch);
                if (!AgentToolConfig.allows(call.tool)) {
                    log("local tool blocked by Agent settings tool=" + call.tool);
                    HookLogOverlay.event("ERROR", "Tool blocked",
                            "name=" + call.tool
                                    + " reason=disabled in Agent settings");
                    HookAgentPipeline.queueSimpleAgentToolResult(effective, step, call, false, "",
                            UiLanguage.text(effective,
                                    "此工具已在 Agent 设置中关闭",
                                    "This tool is disabled in Agent settings"));
                    continue;
                }
                if (AgentToolConfig.isHeartbeatTool(call.tool)
                        && !isProactiveHeartbeatEnabled()) {
                    log("heartbeat tool blocked because proactive messages are disabled tool="
                            + call.tool);
                    HookLogOverlay.event("ERROR", "Tool blocked",
                            "name=" + call.tool
                                    + " reason=proactive heartbeat disabled");
                    HookAgentPipeline.queueSimpleAgentToolResult(effective, step, call, false, "",
                            UiLanguage.text(effective,
                                    "心跳功能当前未开启",
                                    "The heartbeat feature is currently disabled"));
                    continue;
                }
                boolean resultWillArriveSeparately = false;
                String resultOutput = "";
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
                    resultOutput = call.at;
                } else if (HeartbeatToolProtocol.TOOL_SET_PLAN.equals(call.tool)) {
                    String plan = HeartbeatToolProtocol.cleanInstruction(call.instruction);
                    if (plan.length() > 0) success =
                            HookChatPipeline.writeHeartbeatBinding(scope, plan);
                    if (success) dispatchProactiveHeartbeatConfig(effective, true);
                    if (announce) showHeartbeatToolToast(effective, success
                            ? UiLanguage.text(effective,
                            "AI 已更新周期心跳约定",
                            "AI updated the recurring-heartbeat plan")
                            : UiLanguage.text(effective,
                            "周期心跳约定保存失败",
                            "Could not save the recurring-heartbeat plan"));
                } else if (HeartbeatToolProtocol.TOOL_CLEAR_PLAN.equals(call.tool)) {
                    success = HookChatPipeline.writeHeartbeatBinding(scope, "");
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
                    success = HookChatPipeline.writeHeartbeatBinding(scope, keptPlan);
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
                    boolean bound = HookChatPipeline.writeHeartbeatBinding(scope, keptPlan);
                    success = bound
                            && setProactiveHeartbeatInterval(effective, call.minutes);
                    if (announce) showHeartbeatToolToast(effective, success
                            ? UiLanguage.text(effective,
                            "AI 已把心跳间隔设为 " + call.minutes + " 分钟",
                            "AI set the heartbeat interval to " + call.minutes + " minutes")
                            : UiLanguage.text(effective,
                            "AI 设置心跳间隔失败",
                            "AI could not set the heartbeat interval"));
                    resultOutput = String.valueOf(call.minutes);
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
                    resultOutput = call.mode;
                } else if (HeartbeatToolProtocol.TOOL_GET_CURRENT_TIME.equals(call.tool)) {
                    success = true;
                    resultOutput = formatAgentToolResultTime(
                            System.currentTimeMillis());
                } else if (HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL.equals(call.tool)) {
                    success = RichPanelRenderer.isRenderedPanel(call.content);
                    resultOutput = success
                            ? "rendered=true; title=" + call.instruction
                            : "rendered=false";
                } else if (HeartbeatToolProtocol.TOOL_SEARCH_WEB.equals(call.tool)) {
                    success = queueAgentWebSearch(effective, step, call, announce);
                    resultWillArriveSeparately = success;
                } else if (HeartbeatToolProtocol.TOOL_ASK_USER.equals(call.tool)) {
                    success = queueAgentQuestion(effective, step, call, announce);
                    resultWillArriveSeparately = success;
                } else if (HeartbeatToolProtocol.TOOL_DELAY.equals(call.tool)) {
                    success = queueAgentDelay(effective, step, call, announce);
                    resultWillArriveSeparately = success;
                } else if (HeartbeatToolProtocol.TOOL_MUSIC.equals(call.tool)) {
                    success = queueAgentMusic(effective, step, call, announce);
                    resultWillArriveSeparately = success;
                } else if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_TAP_SCREEN.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_SWIPE_SCREEN.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_PRESS_BACK.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_OPEN_APP.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_SCREEN_POWER.equals(call.tool)) {
                    success = queueAgentUiTool(effective, step, call, announce);
                    resultWillArriveSeparately = success;
                } else if (HeartbeatToolProtocol.TOOL_READ_FILE.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_WRITE_FILE.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_DELETE_FILE.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_TRANSFER_FILE.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_SHELL.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_NETWORK_REQUEST.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_LIST_APPS.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_APP_INFO.equals(call.tool)
                        || HeartbeatToolProtocol.TOOL_UNINSTALL_APP.equals(call.tool)) {
                    success = queueAgentDataTool(effective, step, call, announce);
                    resultWillArriveSeparately = success;
                } else if (AgentMcpManager.isDynamicTool(call.tool)) {
                    success = queueAgentMcpTool(effective, step, call, announce);
                    resultWillArriveSeparately = success;
                } else if (JavaPluginPlatform.isAgentTool(call.tool)) {
                    success = queueAgentPluginTool(effective, step, call, announce);
                    resultWillArriveSeparately = success;
                }
                if (success) {
                    completed++;
                    log("local tool completed tool=" + call.tool
                            + " id=" + call.id);
                    HookLogOverlay.event("AGENT", "Tool accepted",
                            "name=" + call.tool + " id=" + call.id
                                    + " result=" + (resultWillArriveSeparately
                                    ? "pending" : "completed"));
                } else {
                    HookLogOverlay.event("ERROR", "Tool failed",
                            "name=" + call.tool + " id=" + call.id
                                    + " reason=operation returned false");
                }
                if (!resultWillArriveSeparately) {
                    HookAgentPipeline.queueSimpleAgentToolResult(
                            effective, step, call, success, resultOutput,
                            success
                                    ? UiLanguage.text(effective,
                                    "工具执行完成", "Tool completed")
                                    : UiLanguage.text(effective,
                                    "工具执行失败", "Tool failed"));
                }
            } catch (Throwable t) {
                log("local tool failed tool=" + call.tool + ": " + t);
                HookLogOverlay.event("ERROR", "Tool exception",
                        "name=" + call.tool + " id=" + call.id
                                + " reason=" + safeThrowableMessage(t));
                if (step != null) {
                    HookAgentPipeline.queueSimpleAgentToolResult(effective, step, call, false, "",
                            UiLanguage.text(effective,
                                    "工具执行异常：" + safeThrowableMessage(t),
                                    "Tool execution failed: " + safeThrowableMessage(t)));
                }
            }
            if (step != null) scheduleAgentStepTimeout(step);
        }
        if (batch != null) batch.seal();
        return completed;
    }

    private static boolean queueAgentQuestion(
            final Context context, final HookAccountLoginSecurity.AgentStepResult step,
            final HeartbeatToolProtocol.ToolCall call,
            final boolean announce) {
        final Activity activity = HookAgentPipeline.currentHostActivity();
        if (activity == null || activity.isFinishing()
                || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) {
            if (announce) showHeartbeatToolToast(context,
                    UiLanguage.text(context,
                            "当前没有可显示问题的 DeepSeek 界面",
                            "There is no active DeepSeek screen for the question"));
            return false;
        }
        boolean queued = AgentQuestionUi.enqueue(
                activity, call, new AgentQuestionUi.AnswerListener() {
                    @Override public void onAnswer(
                            String scope, String visibleAnswer) {
                        queueVisibleAgentAnswer(
                                context, scope, visibleAnswer,
                                new Runnable() {
                                    @Override public void run() {
                                        if (step != null) step.release(call.id);
                                    }
                                },
                                new Runnable() {
                                    @Override public void run() {
                                        HookAgentPipeline.queueSimpleAgentToolResult(
                                                context, step, call, false, "",
                                                UiLanguage.text(context,
                                                        "用户答案未能发送",
                                                        "The user's answer could not be sent"));
                                    }
                                });
                    }

                    @Override public void onCancel(String scope) {
                        HookAgentPipeline.queueSimpleAgentToolResult(
                                context, step, call, false, "",
                                UiLanguage.text(context,
                                        "用户关闭了问题，未提供答案",
                                        "The user dismissed the question without answering"));
                    }
                });
        if (queued) AgentRunStore.waitingUser(call);
        return queued;
    }

    private static boolean queueAgentDelay(
            Context context, HookAccountLoginSecurity.AgentStepResult step,
            HeartbeatToolProtocol.ToolCall call, boolean announce) {
        if (context == null || step == null || call == null
                || call.durationMs < 1) return false;
        Uri uri = new Uri.Builder()
                .scheme(AgentDelayActivity.SCHEME)
                .authority(AgentDelayActivity.HOST)
                .appendQueryParameter("token", AgentDelayActivity.TOKEN)
                .appendQueryParameter("id", call.id)
                .appendQueryParameter("scope", call.scope)
                .appendQueryParameter("duration_ms",
                        String.valueOf(call.durationMs))
                .build();
        Intent schedule = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        routePassiveHostComponent(schedule, AgentDelayActivity.class);
        String key = HookAccountLoginSecurity.agentDelayKey(call.scope, call.id);
        HookAccountLoginSecurity.AGENT_DELAY_STEPS.put(key, step);
        try {
            context.startActivity(schedule);
            if (announce) showHeartbeatToolToast(context,
                    UiLanguage.text(context,
                            "已开始等待 " + call.durationMs + " 毫秒",
                            "Waiting for " + call.durationMs + " milliseconds"));
            log("Agent durable delay scheduled id=" + call.id
                    + " ms=" + call.durationMs + " scope=" + call.scope);
            return true;
        } catch (Throwable error) {
            HookAccountLoginSecurity.AGENT_DELAY_STEPS.remove(key, step);
            log("Agent durable delay schedule failed: "
                    + safeThrowableMessage(error));
            return false;
        }
    }

    private static boolean queueAgentMcpTool(
            final Context context, final HookAccountLoginSecurity.AgentStepResult step,
            final HeartbeatToolProtocol.ToolCall call,
            final boolean announce) {
        if (!AgentMcpManager.isToolEnabled(call.tool)) return false;
        AgentMcpManager.executeAsync(call.tool, call.content,
                new AgentMcpManager.ResultCallback() {
                    @Override public void onResult(
                            boolean ok, String output, String detail) {
                        HookAgentPipeline.queueSimpleAgentToolResult(
                                context, step, call, ok, output, detail);
                        if (!ok && announce) showHeartbeatToolToast(context, detail);
                    }
                });
        return true;
    }

    private static boolean queueAgentWebSearch(
            final Context context, final HookAccountLoginSecurity.AgentStepResult step,
            final HeartbeatToolProtocol.ToolCall call,
            final boolean announce) {
        String query = HeartbeatToolProtocol.cleanInstruction(call.instruction);
        if (query.length() == 0) return false;
        AgentBingSearch.searchAsync(query, new AgentBingSearch.Callback() {
            @Override public void onResult(AgentBingSearch.SearchResult search) {
                boolean chinese = Locale.getDefault().getLanguage()
                        .toLowerCase(Locale.US).startsWith("zh");
                String detail = search.detail(chinese);
                HookAgentPipeline.queueSimpleAgentToolResult(context, step, call,
                        search.success, search.success ? search.toModelJson() : "", detail);
                HookLogOverlay.event(search.success ? "AGENT" : "ERROR",
                        search.success ? "Web search completed" : "Web search failed",
                        "query=" + truncateForLog(call.instruction, 160)
                                + " count=" + search.items.size()
                                + (search.error.length() == 0 ? ""
                                : " reason=" + truncateForLog(search.error, 240)));
                log("Agent web search " + (search.success ? "completed" : "failed")
                        + " id=" + call.id
                        + " query=" + truncateForLog(call.instruction, 160)
                        + " count=" + search.items.size()
                        + (search.error.length() == 0 ? ""
                        : " reason=" + truncateForLog(search.error, 240)));
                if (!search.success && announce) showHeartbeatToolToast(context, detail);
            }
        });
        return true;
    }

    private static boolean queueAgentPluginTool(
            final Context context, final HookAccountLoginSecurity.AgentStepResult step,
            final HeartbeatToolProtocol.ToolCall call,
            final boolean announce) {
        if (!JavaPluginPlatform.isAgentTool(call.tool)) return false;
        JavaPluginManager.emit("agent.tool.before", JavaPluginManager.eventData(
                "tool", call.tool, "scope", call.scope));
        JavaPluginPlatform.executeAgentToolAsync(call.tool, call.content,
                new JavaPluginApi.ResultCallback() {
                    @Override public void onResult(JavaPluginApi.Result result) {
                        boolean ok = result != null && result.success;
                        String output = result == null ? "" : result.data.optString(
                                "output", result.data.toString());
                        String detail = result == null ? "插件工具没有返回结果"
                                : result.message.length() == 0
                                ? (ok ? "插件工具执行完成" : result.code)
                                : result.message;
                        HookAgentPipeline.queueSimpleAgentToolResult(
                                context, step, call, ok, output, detail);
                        JavaPluginManager.emit("agent.tool.completed",
                                JavaPluginManager.eventData(
                                        "tool", call.tool, "success", ok,
                                        "scope", call.scope));
                        if (!ok && announce) showHeartbeatToolToast(context, detail);
                    }
                });
        return true;
    }

    private static boolean queueAgentDataTool(
            final Context context, final HookAccountLoginSecurity.AgentStepResult step,
            final HeartbeatToolProtocol.ToolCall call,
            final boolean announce) {
        AgentDeviceBridge.executeDataTool(
                context, call, new AgentDeviceBridge.ResultCallback() {
                    @Override public void onResult(
                            AgentDeviceBridge.ToolResult result) {
                        log("Agent data tool result tool=" + call.tool
                                + " id=" + call.id
                                + " ok=" + result.success
                                + " exit=" + result.exitCode
                                + " chars=" + result.output.length()
                                + " truncated=" + result.truncated
                                + " detail=" + truncateForLog(
                                result.detail, 320));
                        if (!result.success && announce) {
                            showHeartbeatToolToast(context, result.detail);
                        }
                        if (HeartbeatToolProtocol.TOOL_READ_FILE.equals(call.tool)
                                && result.success
                                && result.output.length() > AGENT_TXT_ATTACH_THRESHOLD) {
                            queueAgentTextFileUpload(context, step, call, result);
                        } else if (step != null) {
                            step.addResult(call, result);
                        } else {
                            HookAccountLoginSecurity.queueHiddenAgentToolResult(context, call, result);
                        }
                    }
                });
        return true;
    }

    private static String formatAgentToolResultTime(long value) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(value));
    }

    /** Restores results that were durable before this DeepSeek process or Activity resumed. */
    private static void recoverAgentRuns(Context context, boolean recoverInterrupted) {
        try {
            if (recoverInterrupted && HostCompat.isV236()) {
                // code249 can be relaunched long after an external side effect (for example
                // opening another app) already completed. Replaying that old private result as
                // a fresh user turn makes the model append a second "tool completed" message.
                // Keep the durable execution/trace record, but never resume an outbox that has
                // been dormant for ten minutes. Live and short Activity recreation still retain
                // the normal result-continuation path.
                int expired = AgentRunStore.expirePendingBefore(
                        System.currentTimeMillis() - 10L * 60L * 1000L,
                        "2.3.6 stale result archived without model replay");
                if (expired > 0) {
                    log("code249 archived stale Agent outbox results=" + expired);
                }
            }
            if (recoverInterrupted) {
                for (AgentRunStore.Record record : AgentRunStore.snapshot()) {
                    if (!AgentRunStore.STATE_EXECUTING.equals(record.state)
                            && !AgentRunStore.STATE_WAITING_USER.equals(record.state)) {
                        continue;
                    }
                    HeartbeatToolProtocol.ToolCall call = restoredAgentCall(record);
                    if (call == null) continue;
                    // The module-side AlarmManager owns durable delay completion and will wake
                    // DeepSeek through an explicit receiver even after this process restarts.
                    if (HeartbeatToolProtocol.TOOL_DELAY.equals(call.tool)) continue;
                    String detail = UiLanguage.text(context,
                            "DeepSeek 进程在工具返回前重启，原操作结果无法确认；"
                                    + "为避免重复副作用，本次不会自动重执行",
                            "DeepSeek restarted before the tool returned. The outcome is unknown; "
                                    + "the operation will not be repeated automatically");
                    String event = HeartbeatToolProtocol.toolResultEvent(
                            call, false, -3, "", detail, "utf-8", false);
                    AgentRunStore.queueResult(call, false, event, detail);
                }
            }
            if (!AgentToolConfig.enabledFast()) return;
            for (AgentRunStore.Record record : AgentRunStore.pending()) {
                queueHiddenAgentEvent(
                        context, record.scope, record.event, record.outboxId);
            }
        } catch (Throwable error) {
            log("Agent outbox recovery failed: " + safeThrowableMessage(error));
        }
    }

    private static HeartbeatToolProtocol.ToolCall restoredAgentCall(
            AgentRunStore.Record record) {
        if (record == null || record.scope.length() == 0
                || record.callId.length() == 0 || record.tool.length() == 0) {
            return null;
        }
        return new HeartbeatToolProtocol.ToolCall(
                record.callId, record.tool, record.scope,
                "", "", 0, "", "");
    }

    static void resumeAgentOutbox(Context context) {
        recoverAgentRuns(context, false);
    }

    static boolean retryAgentOutbox(Context context, String outboxId) {
        if (!AgentToolConfig.enabledFast()) return false;
        AgentRunStore.Record record = AgentRunStore.findOutbox(outboxId);
        if (record == null || !record.hasPendingResult()) return false;
        queueHiddenAgentEvent(context, record.scope, record.event, record.outboxId);
        return true;
    }

    static boolean cancelAgentOutbox(String outboxId) {
        AgentRunStore.Record record = AgentRunStore.findOutbox(outboxId);
        if (record == null || !AgentRunStore.cancel(outboxId)) return false;
        String key = record.scope + "|outbox|" + record.outboxId;
        AGENT_TOOL_RESULT_TOKENS.remove(key);
        AGENT_RESULT_SEND_LOCKED.remove(record.scope);
        return true;
    }

    static int clearFinishedAgentRuns() {
        return AgentRunStore.clearFinished();
    }

    /** Queues a private tool-result turn that waits for the originating stream to go idle. */
    static void queueHiddenAgentEvent(
            Context context, String scope, String event, String outboxId) {
        if (event == null || event.length() == 0) return;
        if ("agent-command-preview".equals(scope)) {
            log("Agent command preview result chars=" + event.length());
            return;
        }
        Context source = context == null ? HookSessionManagement.currentHostContext() : context;
        Context application = source == null ? null : source.getApplicationContext();
        Context safeContext = application == null ? source : application;
        Handler handler = currentMainHandler();
        if (handler == null) {
            log("Agent tool result could not queue: main handler unavailable");
            return;
        }
        String durableId = outboxId == null ? "" : outboxId.trim();
        String key = scope + "|outbox|" + (durableId.length() == 0
                ? Long.toHexString(System.nanoTime()) : durableId);
        Object token = new Object();
        if (AGENT_TOOL_RESULT_TOKENS.putIfAbsent(key, token) != null) return;
        if (durableId.length() > 0) {
            AgentRunStore.markDelivering(durableId, 0);
        }
        handler.post(new HiddenAgentToolResultAttempt(
                safeContext, scope, key, token, event, durableId, 0));
    }

    private static void scheduleAgentStepTimeout(final HookAccountLoginSecurity.AgentStepResult step) {
        Handler handler = currentMainHandler();
        if (handler == null) return;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                step.flushIfPending();
            }
        }, step.deadlineMs());
    }

    /**
     * Sends a private tool-result turn only after the originating assistant stream is idle. The
     * request is filtered out of Compose and folded from history, while its assistant child uses
     * DeepSeek's normal streaming pipeline. A per-scope lock keeps two result turns from
     * racing through the same idle window.
     */
    private static final class HiddenAgentToolResultAttempt implements Runnable {
        private static final int MAX_ATTEMPTS = 200;
        private static final long RETRY_MS = 300L;

        final Context context;
        final String scope;
        final String key;
        final Object token;
        final String event;
        final String outboxId;
        final int attempt;

        HiddenAgentToolResultAttempt(
                Context context, String scope, String key,
                Object token, String event, String outboxId, int attempt) {
            this.context = context;
            this.scope = scope;
            this.key = key;
            this.token = token;
            this.event = event;
            this.outboxId = outboxId == null ? "" : outboxId;
            this.attempt = attempt;
        }

        @Override public void run() {
            if (AGENT_TOOL_RESULT_TOKENS.get(key) != token) return;
            boolean sent = false;
            boolean locked = false;
            boolean terminalFailure = false;
            boolean missingViewModel = false;
            try {
                WeakReference<Object> reference = HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.get(scope);
                Object viewModel = reference == null ? null : reference.get();
                if (reference != null && viewModel == null) {
                    HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.remove(scope, reference);
                }
                missingViewModel = viewModel == null;
                if (viewModel != null) {
                    Object session = HookAccountLoginSecurity.nativeUiChatSession(viewModel);
                    String activeScope = String.valueOf(
                            readHostField(session, "a"));
                    Object state = invokeNoArg(
                            readHostField(session, "i"), "getValue");
                    boolean idle = scope.equals(activeScope)
                            && HookAccountLoginSecurity.isIdleGenerationState(state);
                    if (!idle && AGENT_RESULT_SEND_LOCKED.contains(scope)) {
                        // The lock owner's send took effect: generation is running, so the
                        // lock has served its purpose and the next result may be delivered
                        // once this turn ends.
                        AGENT_RESULT_SEND_LOCKED.remove(scope);
                    }
                    if (idle && AGENT_RESULT_SEND_LOCKED.add(scope)) {
                        locked = true;
                        sent = HookAccountLoginSecurity.invokeNativeUiTextSend(viewModel, event);
                    }
                }
            } catch (Throwable error) {
                terminalFailure = isAgentHostStructureFailure(error);
                log("Agent tool result send failed sid=" + scope
                        + " attempt=" + attempt + ": "
                        + safeThrowableMessage(error)
                        + (terminalFailure ? " (stopped: incompatible host mapping)" : ""));
            }
            if (sent) {
                AGENT_TOOL_RESULT_TOKENS.remove(key, token);
                if (outboxId.length() > 0) AgentRunStore.delivered(outboxId);
                if (HostCompat.isV241()) {
                    HookChatPipeline.V241_AGENT_RESULT_CONTINUATIONS.put(scope,
                            Long.valueOf(System.currentTimeMillis()
                                    + TimeUnit.MINUTES.toMillis(4)));
                }
                log("Agent hidden tool result sent sid=" + scope
                        + " chars=" + event.length());
                scheduleAgentResultLockRelease(scope);
                return;
            }
            if (locked) {
                AGENT_RESULT_SEND_LOCKED.remove(scope);
            }
            // Missing host symbols cannot recover by retrying every 300 ms. Apart from wasting
            // CPU, the former 200-attempt loop flooded the on-screen diagnostics and could leave
            // multiple recovered outbox rows competing for the same chat. Keep the durable row
            // for a future compatible build, but stop this attempt immediately.
            if (terminalFailure) {
                AGENT_TOOL_RESULT_TOKENS.remove(key, token);
                if (outboxId.length() > 0) {
                    AgentRunStore.waitingForChat(outboxId,
                            "Host message interface is incompatible with this version");
                }
                if (context != null) showHeartbeatToolToast(context,
                        UiLanguage.text(context,
                                "工具已执行，但当前版本的结果回传接口不兼容",
                                "The tool ran, but this host version has an incompatible result interface"));
                return;
            }
            // Recovered rows may belong to chats that are not currently open. Poll briefly to
            // cover Activity recreation, then leave the durable row dormant until that exact
            // conversation registers again. This avoids 200 wakeups per historical result.
            if (missingViewModel && attempt >= 19) {
                AGENT_TOOL_RESULT_TOKENS.remove(key, token);
                if (outboxId.length() > 0) {
                    AgentRunStore.waitingForChat(outboxId,
                            "Return to the original chat to deliver this result");
                }
                log("Agent hidden tool result waiting for original chat sid=" + scope);
                return;
            }
            if (attempt + 1 < MAX_ATTEMPTS) {
                Handler handler = currentMainHandler();
                if (handler != null) {
                    handler.postDelayed(new HiddenAgentToolResultAttempt(
                            context, scope, key, token,
                            event, outboxId, attempt + 1), RETRY_MS);
                    return;
                }
            }
            AGENT_TOOL_RESULT_TOKENS.remove(key, token);
            if (outboxId.length() > 0) {
                AgentRunStore.waitingForChat(outboxId,
                        "Return to the original chat to deliver this result");
            }
            if (context != null) showHeartbeatToolToast(context,
                    UiLanguage.text(context,
                            "工具已执行，但结果未能回传：请返回原对话",
                            "The tool ran, but its result could not be returned; "
                                    + "go back to the original chat"));
            log("Agent hidden tool result paused sid=" + scope
                    + " attempts=" + (attempt + 1));
        }
    }

    /**
     * Frees the per-scope result lock once the sent turn actually begins generating. If the
     * generation transition is missed (very fast reply), the timeout still unblocks the queue.
     */
    private static void scheduleAgentResultLockRelease(final String scope) {
        final Handler handler = currentMainHandler();
        if (handler == null) return;
        handler.post(new Runnable() {
            int polls;

            @Override public void run() {
                if (!AGENT_RESULT_SEND_LOCKED.contains(scope)) return;
                boolean busy = false;
                try {
                    WeakReference<Object> reference = HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.get(scope);
                    Object viewModel = reference == null ? null : reference.get();
                    if (viewModel != null) {
                        Object session = HookAccountLoginSecurity.nativeUiChatSession(viewModel);
                        Object state = invokeNoArg(
                                readHostField(session, "i"), "getValue");
                        busy = !HookAccountLoginSecurity.isIdleGenerationState(state);
                    }
                } catch (Throwable ignored) {}
                if (busy || ++polls >= 60) {
                    AGENT_RESULT_SEND_LOCKED.remove(scope);
                    return;
                }
                handler.postDelayed(this, 250L);
            }
        });
    }

    /**
     * The tool block can finish slightly before DeepSeek marks the assistant stream idle. Retry
     * the native composer for a short bounded window instead of dropping a fast user selection.
     */
    static void queueVisibleAgentAnswer(
            Context context, String scope, String visibleAnswer) {
        queueVisibleAgentAnswer(context, scope, visibleAnswer, null, null);
    }

    static void queueVisibleAgentAnswer(
            Context context, String scope, String visibleAnswer,
            Runnable onSent, Runnable onFailed) {
        String safeScope = HeartbeatToolProtocol.cleanScope(scope);
        String safeAnswer = visibleAnswer == null ? "" : visibleAnswer.trim();
        if (safeScope.length() == 0 || safeAnswer.length() == 0) {
            runAgentDeliveryCallback(onFailed);
            return;
        }
        Context source = context == null ? HookSessionManagement.currentHostContext() : context;
        Context application = source == null ? null : source.getApplicationContext();
        Context safeContext = application == null ? source : application;
        Handler handler = currentMainHandler();
        if (handler == null) {
            if (safeContext != null) showHeartbeatToolToast(safeContext,
                    UiLanguage.text(safeContext,
                            "答案发送失败：主界面尚未就绪",
                            "Could not send the answer: the UI is not ready"));
            runAgentDeliveryCallback(onFailed);
            return;
        }
        handler.post(new VisibleAgentAnswerAttempt(
                safeContext, safeScope, safeAnswer, onSent, onFailed, 0));
    }

    private static void runAgentDeliveryCallback(Runnable callback) {
        if (callback == null) return;
        try {
            callback.run();
        } catch (Throwable error) {
            log("Agent delivery callback failed: " + safeThrowableMessage(error));
        }
    }

    private static final class VisibleAgentAnswerAttempt implements Runnable {
        private static final int MAX_ATTEMPTS = 150;
        private static final long RETRY_MS = 300L;

        final Context context;
        final String scope;
        final String answer;
        final Runnable onSent;
        final Runnable onFailed;
        final int attempt;

        VisibleAgentAnswerAttempt(
                Context context, String scope, String answer,
                Runnable onSent, Runnable onFailed, int attempt) {
            this.context = context;
            this.scope = scope;
            this.answer = answer;
            this.onSent = onSent;
            this.onFailed = onFailed;
            this.attempt = attempt;
        }

        @Override public void run() {
            boolean sent = false;
            boolean retryable = true;
            try {
                WeakReference<Object> reference = HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.get(scope);
                Object viewModel = reference == null ? null : reference.get();
                if (reference != null && viewModel == null) {
                    HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.remove(scope, reference);
                }
                if (viewModel != null) {
                    Object session = HookAccountLoginSecurity.nativeUiChatSession(viewModel);
                    String currentScope = String.valueOf(readHostField(session, "a"));
                    if (!scope.equals(currentScope)) {
                        retryable = false;
                    } else {
                        Object state = invokeNoArg(
                                readHostField(session, "i"), "getValue");
                        if (HookAccountLoginSecurity.isIdleGenerationState(state)) {
                            sent = HookAccountLoginSecurity.invokeNativeUiTextSend(viewModel, answer);
                            retryable = !sent;
                        }
                    }
                }
            } catch (Throwable error) {
                retryable = !isAgentHostStructureFailure(error);
                log("Agent visible answer send failed sid=" + scope
                        + " attempt=" + attempt + ": "
                        + safeThrowableMessage(error)
                        + (!retryable ? " (stopped: incompatible host mapping)" : ""));
            }
            if (sent) {
                log("Agent visible answer sent sid=" + scope
                        + " chars=" + answer.length());
                runAgentDeliveryCallback(onSent);
                return;
            }
            if (retryable && attempt + 1 < MAX_ATTEMPTS) {
                Handler handler = currentMainHandler();
                if (handler != null) {
                    handler.postDelayed(new VisibleAgentAnswerAttempt(
                            context, scope, answer,
                            onSent, onFailed, attempt + 1), RETRY_MS);
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
            runAgentDeliveryCallback(onFailed);
        }
    }

    private static boolean queueAgentUiTool(
            final Context context, final HookAccountLoginSecurity.AgentStepResult step,
            final HeartbeatToolProtocol.ToolCall call,
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
                                        context, step, call,
                                        agentScreenshotFile(context), null);
                            } else {
                                HookAgentPipeline.queueSimpleAgentToolResult(
                                        context, step, call, status.connected, "",
                                        status.detail);
                            }
                            if (!status.connected && announce) {
                                showHeartbeatToolToast(context, status.detail);
                            }
                        }
                    });
            return true;
        }
        final Activity activity = HookAgentPipeline.currentHostActivity();
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
                            && performAgentUiTool(live, context, step, call);
                } catch (Throwable error) {
                    log("agent UI tool failed tool=" + call.tool
                            + " id=" + call.id + ": " + error);
                }
                if (!success && announce) {
                    showHeartbeatToolToast(context, UiLanguage.text(context,
                            "界面工具执行失败：" + call.tool,
                            "UI tool failed: " + call.tool));
                }
                if (!HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(call.tool)
                        || !success) {
                    HookAgentPipeline.queueSimpleAgentToolResult(
                            context, step, call, success, "",
                            success
                                    ? UiLanguage.text(context,
                                    "界面工具执行完成", "UI tool completed")
                                    : UiLanguage.text(context,
                                    "界面工具执行失败", "UI tool failed"));
                }
            }
        }, delay);
    }

    private static boolean queueAgentMusic(
            final Context context, final HookAccountLoginSecurity.AgentStepResult step,
            final HeartbeatToolProtocol.ToolCall call,
            final boolean announce) {
        AgentDeviceBridge.executeMusic(context, call,
                new AgentDeviceBridge.StatusCallback() {
                    @Override public void onStatus(AgentDeviceBridge.Status status) {
                        log("music Agent tool result action=" + call.mode
                                + " ok=" + status.connected
                                + " detail=" + status.detail);
                        HookAgentPipeline.queueSimpleAgentToolResult(context, step, call,
                                status.connected, "", status.detail);
                        if (!status.connected && announce) {
                            showHeartbeatToolToast(context, status.detail);
                        }
                    }
                });
        return true;
    }

    private static boolean performAgentUiTool(
            Activity activity, Context context, HookAccountLoginSecurity.AgentStepResult step,
            HeartbeatToolProtocol.ToolCall call) {
        if (HeartbeatToolProtocol.TOOL_OPEN_APP.equals(call.tool) && HostCompat.isV241()) {
            return launchInstalledAppV241(activity, call.targetId);
        }
        if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(call.tool)) {
            return captureAgentScreenshot(activity, context, step, call);
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

    /**
     * code257-only ordinary app launch.  This uses Android's launcher resolver inside the host
     * UID and therefore needs neither Root nor Shizuku.  It is limited to a package name already
     * validated by the Agent tool protocol and to activities the system exposes as launchable.
     */
    private static boolean launchInstalledAppV241(Activity activity, String packageName) {
        if (activity == null || packageName == null
                || !packageName.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+")) {
            return false;
        }
        try {
            Intent launch = activity.getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            activity.startActivity(launch);
            log("code257 Agent opened installed app package=" + packageName);
            return true;
        } catch (Throwable error) {
            log("code257 Agent open app failed package=" + packageName + " error="
                    + safeThrowableMessage(error));
            return false;
        }
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
            Activity activity, Context context, HookAccountLoginSecurity.AgentStepResult step,
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
        final HeartbeatToolProtocol.ToolCall safeCall = call;
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
                                            safeContext, bitmap, step, safeCall);
                                    return;
                                }
                                log("agent screenshot PixelCopy failed code=" + result);
                                if (drawAgentScreenshotFromDecor(
                                        decor, bitmap, scale)) {
                                    log("agent screenshot used View-draw fallback after PixelCopy"
                                            + " code=" + result);
                                    writeAgentScreenshotAsync(
                                            safeContext, bitmap, step, safeCall);
                                    return;
                                }
                                try { bitmap.recycle(); } catch (Throwable ignored) {}
                                showHeartbeatToolToast(safeContext,
                                        UiLanguage.text(safeContext,
                                                "截图失败：窗口画面暂不可用",
                                                "Capture failed: the window surface "
                                                        + "is not available"));
                                HookAgentPipeline.queueSimpleAgentToolResult(
                                        safeContext, step, safeCall, false, "",
                                        UiLanguage.text(safeContext,
                                                "截图失败：窗口画面暂不可用",
                                                "Capture failed: the window surface "
                                                        + "is not available"));
                            }
                        }, handler);
                return true;
            } catch (Throwable error) {
                log("agent screenshot PixelCopy request failed: " + error);
                if (drawAgentScreenshotFromDecor(decor, bitmap, scale)) {
                    log("agent screenshot used View-draw fallback after request failure");
                    writeAgentScreenshotAsync(
                            safeContext, bitmap, step, safeCall);
                    return true;
                }
                try { bitmap.recycle(); } catch (Throwable ignored) {}
                return false;
            }
        }
        if (!drawAgentScreenshotFromDecor(decor, bitmap, scale)) {
            try { bitmap.recycle(); } catch (Throwable ignored) {}
            return false;
        }
        writeAgentScreenshotAsync(
                safeContext, bitmap, step, safeCall);
        return true;
    }

    private static boolean drawAgentScreenshotFromDecor(
            View decor, Bitmap bitmap, float scale) {
        if (decor == null || bitmap == null || bitmap.isRecycled()) return false;
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            canvas.scale(scale, scale);
            decor.draw(canvas);
            return true;
        } catch (Throwable error) {
            log("agent screenshot View-draw fallback failed: " + error);
            return false;
        }
    }

    private static void writeAgentScreenshotAsync(
            final Context context, final Bitmap bitmap,
            final HookAccountLoginSecurity.AgentStepResult step,
            final HeartbeatToolProtocol.ToolCall call) {
        Thread writer = new Thread(new Runnable() {
            @Override public void run() {
                saveAgentScreenshot(
                        context, bitmap, step, call);
            }
        }, "Deekseep-Agent-Screenshot");
        writer.setDaemon(true);
        writer.start();
    }

    private static void saveAgentScreenshot(
            Context context, Bitmap bitmap, HookAccountLoginSecurity.AgentStepResult step,
            HeartbeatToolProtocol.ToolCall call) {
        String callId = call == null || call.id == null ? "" : call.id;
        String scope = call == null
                ? "" : HeartbeatToolProtocol.cleanScope(call.scope);
        boolean privateSaved = false;
        Uri galleryUri = null;
        OutputStream output = null;
        try {
            File screenshotFile = agentScreenshotFile(context);
            File directory = screenshotFile.getParentFile();
            if (directory.exists() || directory.mkdirs()) {
                output = new FileOutputStream(screenshotFile, false);
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
                        context, step, call, screenshotFile, galleryUri);
            } else {
                HookAgentPipeline.queueSimpleAgentToolResult(
                        context, step, call, false, "",
                        UiLanguage.text(context,
                                "截图未能上传回原对话",
                                "The screenshot could not be uploaded to the source chat"));
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
            HookAgentPipeline.queueSimpleAgentToolResult(
                    context, step, call, false, "",
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
            Context context, HookAccountLoginSecurity.AgentStepResult step,
            HeartbeatToolProtocol.ToolCall call,
            File screenshot, Uri sourceUri) {
        String safeScope = call == null ? ""
                : HeartbeatToolProtocol.cleanScope(call.scope);
        if (safeScope.length() == 0 || screenshot == null) return;
        Context source = context == null ? HookSessionManagement.currentHostContext() : context;
        Context application = source == null ? null : source.getApplicationContext();
        Context safeContext = application == null ? source : application;
        Object token = new Object();
        AGENT_SCREENSHOT_UPLOAD_TOKENS.put(safeScope, token);
        Handler handler = currentMainHandler();
        if (handler == null) {
            AGENT_SCREENSHOT_UPLOAD_TOKENS.remove(safeScope, token);
            HookAgentPipeline.queueSimpleAgentToolResult(
                    safeContext, step, call, false, "",
                    UiLanguage.text(safeContext,
                            "截图无法回传：主界面尚未就绪",
                            "The capture could not be returned because the UI is not ready"));
            return;
        }
        handler.post(new AgentScreenshotUploadAttempt(
                safeContext, safeScope, screenshot, sourceUri,
                token, step, call));
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
        final Uri sourceUri;
        final Object token;
        final HookAccountLoginSecurity.AgentStepResult step;
        final HeartbeatToolProtocol.ToolCall call;
        int attempts;
        Object composer;
        Object attachment;
        String uploadKey = "";
        String fileName = "";

        AgentScreenshotUploadAttempt(
                Context context, String scope, File screenshot,
                Uri sourceUri,
                Object token, HookAccountLoginSecurity.AgentStepResult step,
                HeartbeatToolProtocol.ToolCall call) {
            this.context = context;
            this.scope = scope;
            this.screenshot = screenshot;
            this.sourceUri = sourceUri;
            this.token = token;
            this.step = step;
            this.call = call;
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
                WeakReference<Object> reference = HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.get(scope);
                Object viewModel = reference == null ? null : reference.get();
                if (reference != null && viewModel == null) {
                    HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.remove(scope, reference);
                }
                if (viewModel == null) {
                    retryOrFail(UiLanguage.text(context,
                            "原对话尚未就绪", "The original chat is not ready"));
                    return;
                }
                Object session = HookAccountLoginSecurity.nativeUiChatSession(viewModel);
                if (session == null || !scope.equals(String.valueOf(
                        readHostField(session, "a")))) {
                    fail(UiLanguage.text(context,
                            "已离开截图所属对话，未自动上传",
                            "The source chat is no longer active; the capture was not uploaded"));
                    return;
                }
                Object generation = invokeNoArg(
                        readHostField(session, "i"), "getValue");
                if (!HookAccountLoginSecurity.isIdleGenerationState(generation)) {
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
                            composer, screenshot, sourceUri, fileName);
                    if (metadata == null
                            || !invokeNativeScreenshotUploader(
                            composer, metadata, scope)) {
                        fail(UiLanguage.text(context,
                                "无法启动 DeepSeek 图片上传",
                                "Could not start DeepSeek's image upload"));
                        return;
                    }
                    ACTIVE_HIDDEN_ATTACHMENT_NAMES.add(fileName);
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
                // 截图以私有工具结果事件体发送：Compose 过滤整条消息（tp.s 隐藏），
                // 模型仍收到事件体 + 图片附件，可自然回复而不暴露用户侧的任何发送。
                String prompt = HeartbeatToolProtocol.toolResultEvent(
                        call, true, 0, "",
                        "截图已上传并附加为本消息的图片附件（" + fileName + "）。"
                                + "请基于图片内容继续处理上一条请求；如果图片信息不足，请明确说明。",
                        "utf-8", false);
                if (!HookAccountLoginSecurity.invokeNativeUiTextSend(viewModel, prompt, attachments)) {
                    retryOrFail(UiLanguage.text(context,
                            "截图已上传，正在等待发送",
                            "The screenshot is uploaded and waiting to send"));
                    return;
                }
                ACTIVE_HIDDEN_ATTACHMENT_NAMES.remove(fileName);
                AGENT_SCREENSHOT_UPLOAD_TOKENS.remove(scope, token);
                if (step != null) step.release(call.id);
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
            if (fileName != null && fileName.length() > 0) {
                ACTIVE_HIDDEN_ATTACHMENT_NAMES.remove(fileName);
            }
            log("Agent screenshot upload abandoned sid=" + scope
                    + " attempts=" + attempts + " detail=" + detail);
            if (context != null) showHeartbeatToolToast(context, detail);
            HookAgentPipeline.queueSimpleAgentToolResult(
                    context, step, call, false, "", detail);
        }
    }

    /**
     * Packages a large read_file payload as a real TXT attachment through DeepSeek's composer
     * uploader, so the file body does not occupy the conversation text context. The private
     * result event then points the model at the attachment with only a short preview inline.
     */
    private static void queueAgentTextFileUpload(
            Context context, HookAccountLoginSecurity.AgentStepResult step,
            HeartbeatToolProtocol.ToolCall call,
            AgentDeviceBridge.ToolResult result) {
        String safeScope = call == null ? ""
                : HeartbeatToolProtocol.cleanScope(call.scope);
        if (safeScope.length() == 0 || result == null) return;
        Context source = context == null ? HookSessionManagement.currentHostContext() : context;
        Context application = source == null ? null : source.getApplicationContext();
        Context safeContext = application == null ? source : application;
        File textFile = writeAgentTextAttachment(safeContext, call, result);
        if (textFile == null) {
            if (step != null) {
                step.addResult(call, result);
            } else {
                HookAccountLoginSecurity.queueHiddenAgentToolResult(context, call, result);
            }
            return;
        }
        Object token = new Object();
        AGENT_SCREENSHOT_UPLOAD_TOKENS.put(safeScope, token);
        Handler handler = currentMainHandler();
        if (handler == null) {
            AGENT_SCREENSHOT_UPLOAD_TOKENS.remove(safeScope, token);
            try { textFile.delete(); } catch (Throwable ignored) {}
            if (step != null) {
                step.addResult(call, result);
            } else {
                HookAccountLoginSecurity.queueHiddenAgentToolResult(context, call, result);
            }
            return;
        }
        handler.post(new AgentTextFileUploadAttempt(
                safeContext, safeScope, textFile, token, step, call, result));
    }

    private static File writeAgentTextAttachment(
            Context context, HeartbeatToolProtocol.ToolCall call,
            AgentDeviceBridge.ToolResult result) {
        try {
            if (context == null) return null;
            File dir = new File(context.getCacheDir(), "ds_agent_txt");
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            String base = "file";
            String path = call == null ? "" : call.path;
            if (path.length() > 0) {
                int slash = path.lastIndexOf('/');
                if (slash >= 0 && slash + 1 < path.length()) {
                    base = path.substring(slash + 1);
                }
                int dot = base.lastIndexOf('.');
                if (dot > 0) base = base.substring(0, dot);
            }
            String safeBase = base.replaceAll("[^A-Za-z0-9._-]", "_");
            if (safeBase.length() == 0) safeBase = "file";
            if (safeBase.length() > 48) safeBase = safeBase.substring(0, 48);
            String name = safeBase + "_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss",
                    Locale.US).format(new Date()) + ".txt";
            File target = new File(dir, name);
            java.io.FileOutputStream output = new java.io.FileOutputStream(target);
            try {
                output.write(result.output.getBytes("UTF-8"));
            } finally {
                output.close();
            }
            return target;
        } catch (Throwable error) {
            log("Agent text attachment write failed: "
                    + safeThrowableMessage(error));
            return null;
        }
    }

    private static final class AgentTextFileUploadAttempt implements Runnable {
        private static final int MAX_ATTEMPTS = 240;
        private static final long RETRY_MS = 250L;

        final Context context;
        final String scope;
        final File textFile;
        final Object token;
        final HookAccountLoginSecurity.AgentStepResult step;
        final HeartbeatToolProtocol.ToolCall call;
        final AgentDeviceBridge.ToolResult result;
        int attempts;
        Object composer;
        Object attachment;
        String uploadKey = "";
        String fileName = "";

        AgentTextFileUploadAttempt(
                Context context, String scope, File textFile,
                Object token, HookAccountLoginSecurity.AgentStepResult step,
                HeartbeatToolProtocol.ToolCall call,
                AgentDeviceBridge.ToolResult result) {
            this.context = context;
            this.scope = scope;
            this.textFile = textFile;
            this.token = token;
            this.step = step;
            this.call = call;
            this.result = result;
        }

        @Override public void run() {
            if (AGENT_SCREENSHOT_UPLOAD_TOKENS.get(scope) != token) return;
            attempts++;
            try {
                if (textFile == null || !textFile.isFile()
                        || textFile.length() < 1L) {
                    fail(UiLanguage.text(context,
                            "文本附件不可用", "The text attachment is unavailable"));
                    return;
                }
                WeakReference<Object> reference = HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.get(scope);
                Object viewModel = reference == null ? null : reference.get();
                if (reference != null && viewModel == null) {
                    HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.remove(scope, reference);
                }
                if (viewModel == null) {
                    retryOrFail(UiLanguage.text(context,
                            "原对话尚未就绪", "The original chat is not ready"));
                    return;
                }
                Object session = HookAccountLoginSecurity.nativeUiChatSession(viewModel);
                if (session == null || !scope.equals(String.valueOf(
                        readHostField(session, "a")))) {
                    fail(UiLanguage.text(context,
                            "已离开文件所属对话，未自动上传",
                            "The source chat is no longer active; the file was not uploaded"));
                    return;
                }
                Object generation = invokeNoArg(
                        readHostField(session, "i"), "getValue");
                if (!HookAccountLoginSecurity.isIdleGenerationState(generation)) {
                    retryOrFail(UiLanguage.text(context,
                            "正在等待当前回复结束",
                            "Waiting for the current response to finish"));
                    return;
                }
                if (attachment == null) {
                    composer = invokeNoArg(viewModel, "K");
                    if (composer == null) {
                        retryOrFail(UiLanguage.text(context,
                                "文件上传器尚未就绪",
                                "The file uploader is not ready"));
                        return;
                    }
                    Object currentAttachments =
                            snapshotComposerAttachments(composer);
                    if (currentAttachments instanceof List
                            && !((List) currentAttachments).isEmpty()) {
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
                    fileName = textFile.getName();
                    Object metadata = createNativeFileMetadata(
                            composer, textFile, fileName);
                    if (metadata == null
                            || !invokeNativeScreenshotUploader(
                            composer, metadata, scope)) {
                        fail(UiLanguage.text(context,
                                "无法启动 DeepSeek 文件上传",
                                "Could not start DeepSeek's file upload"));
                        return;
                    }
                    ACTIVE_HIDDEN_ATTACHMENT_NAMES.add(fileName);
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
                            "正在等待文件进入输入框",
                            "Waiting for the file to enter the composer"));
                    return;
                }
                String remoteId = nativeAttachmentRemoteId(
                        attachment, uploadKey);
                if (remoteId.length() == 0) {
                    retryOrFail(UiLanguage.text(context,
                            "正在上传文本附件",
                            "Uploading the text attachment"));
                    return;
                }
                String preview = result.output;
                if (preview.length() > 600) {
                    preview = preview.substring(0, 600)
                            + "\n…（内容已截断，以附件为准）";
                }
                String prompt = HeartbeatToolProtocol.toolResultEvent(
                        call, true, 0, preview,
                        "文件内容已作为 TXT 附件上传并附加为本消息的文件附件（"
                                + fileName + "，共 " + result.output.length()
                                + " 字符）。请读取附件内容后继续处理上一条请求。",
                        result.encoding, result.truncated);
                if (!HookAccountLoginSecurity.invokeNativeUiTextSend(viewModel, prompt, attachments)) {
                    retryOrFail(UiLanguage.text(context,
                            "文本附件已上传，正在等待发送",
                            "The text attachment is uploaded and waiting to send"));
                    return;
                }
                ACTIVE_HIDDEN_ATTACHMENT_NAMES.remove(fileName);
                AGENT_SCREENSHOT_UPLOAD_TOKENS.remove(scope, token);
                if (step != null) step.release(call.id);
                log("Agent text attachment uploaded and sent sid=" + scope
                        + " file=" + fileName
                        + " chars=" + result.output.length()
                        + " remote=" + truncateForLog(remoteId, 120));
                deleteAgentTextAttachment();
            } catch (Throwable error) {
                log("Agent text file upload attempt failed sid=" + scope
                        + " attempt=" + attempts + ": "
                        + safeThrowableMessage(error));
                retryOrFail(UiLanguage.text(context,
                        "文本附件自动上传失败", "Automatic text upload failed"));
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

        private void deleteAgentTextAttachment() {
            try {
                if (textFile != null) textFile.delete();
            } catch (Throwable ignored) {}
        }

        private void fail(String detail) {
            if (!AGENT_SCREENSHOT_UPLOAD_TOKENS.remove(scope, token)) return;
            if (fileName != null && fileName.length() > 0) {
                ACTIVE_HIDDEN_ATTACHMENT_NAMES.remove(fileName);
            }
            deleteAgentTextAttachment();
            log("Agent text attachment abandoned sid=" + scope
                    + " attempts=" + attempts + " detail=" + detail);
            if (context != null) showHeartbeatToolToast(context, detail);
            // 上传失败时回退为内联结果，保证模型仍能拿到文件内容。
            if (step != null) {
                step.addResult(call, result);
            } else {
                HookAccountLoginSecurity.queueHiddenAgentToolResult(context, call, result);
            }
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

    private static Object createNativeFileMetadata(
            Object composer, File file, String fileName) {
        try {
            Method uploader = findNativeAttachmentUploader(composer);
            if (uploader == null) throw new NoSuchMethodException(
                    "native attachment uploader");
            Class<?> metadataType = uploader.getParameterTypes()[0];
            Uri uri = Uri.fromFile(file);
            Object metadata = newNativeAttachmentMetadata(
                    metadataType, uri, fileName, file.length(), 0, 0);
            logNativeAttachmentAdapterOnce(composer, uploader, metadataType);
            return metadata;
        } catch (Throwable error) {
            log("native file metadata creation failed: "
                    + safeThrowableMessage(error));
            return null;
        }
    }

    private static Object createNativeScreenshotMetadata(
            Object composer, File screenshot,
            Uri sourceUri, String fileName) {
        try {
            Method uploader = findNativeAttachmentUploader(composer);
            if (uploader == null) throw new NoSuchMethodException(
                    "native attachment uploader");
            Class<?> metadataType = uploader.getParameterTypes()[0];
            // The in-app capture path already owns this MediaStore URI. Feeding that URI into
            // DeepSeek's uploader matches the native gallery path and avoids file:// handling.
            Uri uri = sourceUri == null ? Uri.fromFile(screenshot) : sourceUri;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(screenshot.getAbsolutePath(), options);
            Object metadata = newNativeAttachmentMetadata(
                    metadataType, uri, fileName, screenshot.length(),
                    Math.max(1, options.outWidth), Math.max(1, options.outHeight));
            logNativeAttachmentAdapterOnce(composer, uploader, metadataType);
            return metadata;
        } catch (Throwable error) {
            log("native screenshot metadata creation failed: "
                    + safeThrowableMessage(error));
            return null;
        }
    }

    private static boolean invokeNativeScreenshotUploader(
            Object composer, Object metadata, String scope) {
        if (composer == null || metadata == null) return false;
        Method method = findNativeAttachmentUploader(composer);
        if (method == null || !method.getParameterTypes()[0].isInstance(metadata)) {
            log("native screenshot uploader unavailable composer="
                    + composer.getClass().getName() + " metadata="
                    + metadata.getClass().getName());
            return false;
        }
        try {
            method.setAccessible(true);
            Class<?>[] parameters = method.getParameterTypes();
            if (parameters.length == 4 && parameters[1] == int.class
                    && parameters[2] == String.class) {
                // code257 g71.t(p22, source, sessionId, replacement). A null replacement
                // appends a new draft item, matching code249 n51.u(dz1, sessionId).
                method.invoke(composer, metadata, 1, scope, null);
            } else {
                method.invoke(composer, metadata, scope);
            }
            return true;
        } catch (Throwable error) {
            log("native screenshot uploader call failed: "
                    + safeThrowableMessage(error));
            return false;
        }
    }

    private static volatile String nativeAttachmentAdapterLogged = "";

    /** Resolve the contract from the live composer instead of trusting an R8 class name. This
     * covers 2.3.4 CN (k51.u(vy1,String)), 2.3.4 GP (d71.u(r02,String)) and 2.3.6 CN
     * (n51.u(dz1,String)), plus 2.4.1 CN (g71.t(p22,int,String,w11)). */
    private static Method findNativeAttachmentUploader(Object composer) {
        if (composer == null) return null;
        Method fallback = null;
        for (Class<?> owner = composer.getClass(); owner != null;
                owner = owner.getSuperclass()) {
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (types.length == 4 && types[1] == int.class
                        && types[2] == String.class
                        && method.getReturnType() == void.class
                        && hasNativeAttachmentMetadataConstructor(types[0])) {
                    if ("t".equals(method.getName())) return method;
                    if (fallback == null) fallback = method;
                    continue;
                }
                if (types.length != 2 || types[1] != String.class
                        || method.getReturnType() != void.class
                        || !hasNativeAttachmentMetadataConstructor(types[0])) continue;
                if ("u".equals(method.getName())) return method;
                if (fallback == null) fallback = method;
            }
        }
        return fallback;
    }

    private static boolean hasNativeAttachmentMetadataConstructor(Class<?> type) {
        if (type == null) return false;
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            Class<?>[] p = constructor.getParameterTypes();
            if (p.length == 5 && p[0] == Uri.class && p[1] == String.class
                    && p[2] == long.class && p[3] == int.class && p[4] == int.class) return true;
            if (p.length == 3 && p[0] == Uri.class && p[1] == String.class
                    && p[2] == long.class) return true;
        }
        return false;
    }

    private static Object newNativeAttachmentMetadata(
            Class<?> type, Uri uri, String name, long size, int width, int height)
            throws Exception {
        try {
            Constructor<?> constructor = type.getDeclaredConstructor(
                    Uri.class, String.class, long.class, int.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(uri, name, Long.valueOf(size),
                    Integer.valueOf(width), Integer.valueOf(height));
        } catch (NoSuchMethodException legacy) {
            Constructor<?> constructor = type.getDeclaredConstructor(
                    Uri.class, String.class, long.class);
            constructor.setAccessible(true);
            return constructor.newInstance(uri, name, Long.valueOf(size));
        }
    }

    private static void logNativeAttachmentAdapterOnce(
            Object composer, Method uploader, Class<?> metadata) {
        String value = composer.getClass().getName() + "." + uploader.getName()
                + "(" + metadata.getName() + ",String)";
        if (value.equals(nativeAttachmentAdapterLogged)) return;
        nativeAttachmentAdapterLogged = value;
        log("native Agent attachment adapter=" + value);
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
            task.setClassName(runtimeComponentPackage(),
                    ProactiveHeartbeatReceiver.class.getName());
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
            cancel.setClassName(runtimeComponentPackage(),
                    ProactiveHeartbeatReceiver.class.getName());
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

    static Activity currentHostActivityForAgent() {
        return HookAgentPipeline.currentHostActivity();
    }

    static void openSettingsFromExternal() {
        Handler handler = currentMainHandler();
        Runnable action = new Runnable() {
            @Override public void run() {
                Activity activity = HookAgentPipeline.currentHostActivity();
                if (activity == null || activity.isFinishing()) {
                    log("openSettingsFromExternal: no current host activity");
                    return;
                }
                try {
                    DeekseepUi.showPage(activity);
                } catch (Throwable t) {
                    log("openSettingsFromExternal failed: " + t);
                }
            }
        };
        if (handler != null) {
            handler.post(action);
        } else {
            action.run();
        }
    }

    static Handler currentMainHandler() {
        Main module = MODULE;
        return module == null ? null : module.main;
    }

    /**
     * DeepSeek exposes each thought block through Compose semantics' native Expand/Collapse
     * actions.  We invoke that exact Collapse action once per newly seen block; no text is
     * removed and a user can still expand it normally afterwards.
     */
    private static void installDefaultThinkingCollapse(final Activity activity) {
        if (activity == null || !AgentToolConfig.collapseThinkingByDefault()) return;
        final View decor;
        try { decor = activity.getWindow().getDecorView(); } catch (Throwable ignored) { return; }
        if (decor == null) return;
        final Handler handler = currentMainHandler() == null
                ? new Handler(Looper.getMainLooper()) : currentMainHandler();
        if (!DEFAULT_THINKING_LAYOUT_LISTENERS.containsKey(decor)) {
            ViewTreeObserver.OnGlobalLayoutListener listener =
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override public void onGlobalLayout() {
                    if (!AgentToolConfig.collapseThinkingByDefault()) return;
                    handler.postDelayed(new Runnable() { @Override public void run() {
                        collapseDefaultThinkingInDecor(decor);
                    }}, 120L);
                }
            };
            DEFAULT_THINKING_LAYOUT_LISTENERS.put(decor, listener);
            decor.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        }
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                collapseDefaultThinkingInDecor(decor);
            }
        }, 260L);
    }

    private static void collapseDefaultThinkingInDecor(View decor) {
        if (decor == null || !AgentToolConfig.collapseThinkingByDefault()) return;
        AccessibilityNodeInfo root = null;
        try {
            root = decor.createAccessibilityNodeInfo();
            HashSet<String> seen = DEFAULT_COLLAPSED_THINKING.get(decor);
            if (seen == null) { seen = new HashSet<>(); DEFAULT_COLLAPSED_THINKING.put(decor, seen); }
            collapseThinkingNodes(root, seen, 0);
        } catch (Throwable error) {
            log("default thinking collapse scan failed: " + safeThrowableMessage(error));
        } finally { if (root != null) try { root.recycle(); } catch (Throwable ignored) {} }
    }

    private static void collapseThinkingNodes(AccessibilityNodeInfo node,
                                              HashSet<String> seen, int depth) {
        if (node == null || depth > 80) return;
        try {
            List<AccessibilityNodeInfo.AccessibilityAction> actions = node.getActionList();
            AccessibilityNodeInfo.AccessibilityAction collapse = null;
            if (actions != null) for (AccessibilityNodeInfo.AccessibilityAction action : actions) {
                CharSequence label = action == null ? null : action.getLabel();
                String value = label == null ? "" : label.toString().trim();
                if (action.getId() == AccessibilityNodeInfo.ACTION_COLLAPSE
                        || "Collapse".equalsIgnoreCase(value) || "收起".equals(value)) {
                    collapse = action; break;
                }
            }
            if (collapse != null) {
                String title = String.valueOf(node.getText()) + '|' + String.valueOf(node.getContentDescription());
                Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                String key = title + '|' + bounds.left + '|' + bounds.top;
                if (!seen.contains(key)) {
                    seen.add(key);
                    node.performAction(collapse.getId());
                    log("default thinking collapse applied key=" + Integer.toHexString(key.hashCode()));
                }
            } else {
                CharSequence description = node.getContentDescription();
                String state = description == null ? "" : description.toString().trim();
                // DeepSeek 2.3.6 exposes the chevron itself as content-desc="折叠" while the
                // clickable semantics live on its parent row. This is the actual expanded state.
                if ("折叠".equals(state) || "Collapse".equalsIgnoreCase(state)) {
                    AccessibilityNodeInfo parent = null;
                    try {
                        parent = node.getParent();
                        AccessibilityNodeInfo target = parent != null && parent.isClickable()
                                ? parent : node;
                        Rect bounds = new Rect(); target.getBoundsInScreen(bounds);
                        String key = "thinking-chevron|" + bounds.left + '|' + bounds.top
                                + '|' + bounds.right + '|' + bounds.bottom;
                        if (!seen.contains(key)) {
                            if (target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                                seen.add(key);
                                log("default thinking chevron collapsed key="
                                        + Integer.toHexString(key.hashCode()));
                            }
                        }
                    } finally {
                        if (parent != null) try { parent.recycle(); } catch (Throwable ignored) {}
                    }
                }
            }
            int count = node.getChildCount();
            for (int i = 0; i < count; i++) {
                AccessibilityNodeInfo child = null;
                try { child = node.getChild(i); collapseThinkingNodes(child, seen, depth + 1); }
                finally { if (child != null) try { child.recycle(); } catch (Throwable ignored) {} }
            }
        } catch (Throwable ignored) {}
    }

    static boolean isDeepSeekForeground() {
        try {
            Activity activity = HookAgentPipeline.currentHostActivity();
            return activity != null && !activity.isFinishing()
                    && activity.hasWindowFocus();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isDeepSeekMainActivity(Activity activity) {
        return activity != null
                && "com.deepseek.chat.MainActivity".equals(
                        activity.getClass().getName());
    }

    private static void showProactiveMessageInForeground(final String message) {
        Handler handler = currentMainHandler();
        if (handler == null) return;
        handler.post(new Runnable() {
            @Override public void run() {
                try {
                    Activity activity = HookAgentPipeline.currentHostActivity();
                    if (activity == null || activity.isFinishing()) return;
                    Toast.makeText(activity, "DeepSeek："
                            + (message.length() > 180
                            ? message.substring(0, 180) + "…" : message),
                            Toast.LENGTH_LONG).show();
                } catch (Throwable ignored) {}
            }
        });
    }

    static boolean requestLocalApiKeepAlive(Context context, boolean enabled) {
        return requestLocalApiKeepAlive(context, enabled, false);
    }

    private static boolean requestLocalApiKeepAlive(
            Context context, boolean enabled, boolean forceControl) {
        if (context == null) {
            HookAccountLoginSecurity.localApiKeepAliveError = "DeepSeek 上下文尚未就绪";
            return false;
        }
        if (enabled && isLocalApiKeepAliveDisabled()) {
            enabled = false;
            forceControl = true;
        }
        long now = SystemClock.elapsedRealtime();
        long heartbeatAge = HookAccountLoginSecurity.localApiKeepAliveHeartbeatAt <= 0L ? Long.MAX_VALUE
                : Math.max(0L, now - HookAccountLoginSecurity.localApiKeepAliveHeartbeatAt);
        if (LocalApiKeepAliveDecision.heartbeatAlreadySatisfies(
                enabled, heartbeatAge, HookAccountLoginSecurity.localApiKeepAliveRequested)) return true;
        if (!enabled && !forceControl && heartbeatAge == Long.MAX_VALUE
                && !localApiKeepAliveControlLogged) {
            return true;
        }
        // The trampoline finishes immediately and resumes DeepSeek. Throttle that onResume so it
        // cannot open the trampoline again before the first five-second heartbeat arrives.
        if (LocalApiKeepAliveDecision.throttleSameDirection(
                now, localApiKeepAliveLaunchAt,
                localApiKeepAliveLaunchState, enabled)) return true;
        Uri uri = new Uri.Builder()
                .scheme(z20.SCHEME)
                .authority(z20.HOST)
                .appendQueryParameter(z20.QUERY_MODE,
                        enabled ? z20.MODE_START
                                : z20.MODE_STOP)
                .appendQueryParameter(z20.QUERY_TOKEN,
                        z21.CONTROL_TOKEN)
                .build();
        Intent control = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        routePassiveHostComponent(control, z20.class);
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
            localApiKeepAliveLaunchState = enabled;
            HookAccountLoginSecurity.localApiKeepAliveRequested = enabled;
            if (enabled && !localApiKeepAliveControlLogged) {
                localApiKeepAliveControlLogged = true;
                log("local API keepalive trampoline launched");
            } else if (!enabled && localApiKeepAliveControlLogged) {
                log("local API keepalive stop trampoline launched");
                localApiKeepAliveControlLogged = false;
                HookAccountLoginSecurity.localApiKeepAliveHeartbeatAt = 0L;
            }
            HookAccountLoginSecurity.localApiKeepAliveError = "";
            return true;
        } catch (Throwable t) {
            if (control.hasExtra(z20.EXTRA_PUBLIC_TUNNEL_RECEIVER)) {
                publicTunnelBridgeBinding = false;
                publicTunnelBridgeReceiver = null;
            }
            HookAccountLoginSecurity.localApiKeepAliveError = UiLanguage.text(
                    (enabled ? "启动" : "停止") + "前台保活失败：",
                    (enabled ? "Start" : "Stop") + " foreground keepalive failed: ")
                    + safeThrowableMessage(t);
            log("local API keepalive control failed enabled=" + enabled + ": " + t);
            return false;
        }
    }

    private static boolean requestLocalApiFloatingWindow(Context context, boolean enabled) {
        if (HostCompat.isV236()) {
            return requestLocalApiFloatingWindowV236(context, enabled);
        }
        if (HostCompat.isV241()) {
            return requestLocalApiFloatingWindowV241(context, enabled);
        }
        return false;
    }

    private static boolean requestLocalApiFloatingWindowV236(
            Context context, boolean enabled) {
        return requestLocalApiFloatingWindowExact(context, enabled, "code249");
    }

    private static boolean requestLocalApiFloatingWindowV241(
            Context context, boolean enabled) {
        return requestLocalApiFloatingWindowExact(context, enabled, "code257");
    }

    private static boolean requestLocalApiFloatingWindowExact(
            Context context, boolean enabled, String adapter) {
        if (context == null) {
            HookAccountLoginSecurity.localApiKeepAliveError = "DeepSeek 上下文尚未就绪";
            return false;
        }
        long now = SystemClock.elapsedRealtime();
        if (now - localApiFloatingWindowLaunchAt < 1_200L
                && enabled == HookAccountLoginSecurity.localApiFloatingWindowRequested) return true;
        Uri uri = new Uri.Builder()
                .scheme(z20.SCHEME)
                .authority(z20.HOST)
                .appendQueryParameter(z20.QUERY_MODE,
                        enabled ? z20.MODE_OVERLAY_START : z20.MODE_OVERLAY_STOP)
                .appendQueryParameter(z20.QUERY_TOKEN, z21.CONTROL_TOKEN)
                .build();
        Intent control = new Intent(Intent.ACTION_VIEW, uri)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_NO_ANIMATION
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        routePassiveHostComponent(control, z20.class);
        try {
            context.startActivity(control);
            localApiFloatingWindowLaunchAt = now;
            HookAccountLoginSecurity.localApiFloatingWindowRequested = enabled;
            log(adapter + " local API floating keeper requested enabled=" + enabled);
            return true;
        } catch (Throwable error) {
            HookAccountLoginSecurity.localApiKeepAliveError = "小窗保活启动失败：" + safeThrowableMessage(error);
            log("local API floating keeper launch failed: " + error);
            return false;
        }
    }

    static String localApiKeepAliveStatus() {
        if (!isLocalApiEnabled()) return UiLanguage.text(
                "前台保活：未启用", "Foreground keepalive: Disabled");
        if (isLocalApiKeepAliveDisabled()) return UiLanguage.text(
                "前台保活：已由用户关闭（API 仅随 DeepSeek 进程运行）",
                "Foreground keepalive: disabled by user (API follows the DeepSeek process)");
        long heartbeat = HookAccountLoginSecurity.localApiKeepAliveHeartbeatAt;
        long age = heartbeat <= 0L ? -1L
                : Math.max(0L, SystemClock.elapsedRealtime() - heartbeat);
        if (age >= 0L && age <= 15_000L) {
            return UiLanguage.text(
                    "前台保活：已连接（最近心跳 " + Math.max(0L, age / 1000L) + " 秒前）",
                    "Foreground keepalive: Connected (last heartbeat "
                            + Math.max(0L, age / 1000L) + "s ago)");
        }
        String error = HookAccountLoginSecurity.localApiKeepAliveError;
        if (error != null && error.length() > 0) return UiLanguage.text(
                "前台保活：异常 ", "Foreground keepalive: Failed ") + error;
        return UiLanguage.text("前台保活：正在等待 DeepSeek 心跳",
                "Foreground keepalive: waiting for a DeepSeek heartbeat");
    }

    /**
     * Reports actual target-scope injection to the module app.  The exported provider validates
     * the Binder caller UID against com.deepseek.chat before persisting this heartbeat, so an
     * arbitrary app cannot make the launcher claim that the DeepSeek scope is active.
     */
    private static void reportActivationHeartbeat(Activity act) {
        if (!BuildInfo.PROTECTED_BUILD || act == null) return;
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

    static boolean setExpertRelayEnabled(boolean enabled) {
        if (!BuildInfo.PROTECTED_BUILD || !BuildInfo.LOCAL_API_INCLUDED || (!HostCompat.isV236() && !HostCompat.isV241())) return false;
        try {
            String markerPath = HostCompat.isV236() ? HookAttachmentPipeline.V236_EXPERT_RELAY_FILE : HookAttachmentPipeline.EXPERT_RELAY_FILE;
            File marker = new File(markerPath);
            if (enabled) overwriteTextFile(marker.getPath(), "1");
            else if (marker.exists() && !marker.delete()) return false;
            return HookAttachmentPipeline.isExpertRelayEnabled() == enabled;
        } catch (Throwable error) {
            log("expert image relay setting failed: " + safeThrowableMessage(error));
            return false;
        }
    }

    private static void persistReadGrant(Activity act, Intent data, Uri uri) {
        try {
            int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
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
            File link = new File(HookChatPipeline.PROMPT_LINK_FILE);
            link.delete();
            if (displayPath == null || !displayPath.startsWith("/")) return;
            Os.symlink(displayPath, HookChatPipeline.PROMPT_LINK_FILE);
            log("prompt symlink -> " + displayPath);
        } catch (Throwable t) {
            log("prompt symlink skipped: " + t);
        }
    }

    static void writeText(String path, String text) {
        try {
            overwriteTextFile(path, text == null ? "" : text);
        } catch (Throwable ignored) {}
    }

    public static void overwriteTextFile(String path, String text) throws Throwable {
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

    public static String readSmallText(String path) {
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

    /** Installs optional hook groups once, only after their feature becomes active. */
    void ensureOptionalHooksForEnabledFeatures(final ClassLoader cl) {
        if (cl == null) return;
        if (HookUiSurface.isWelcomeWhaleMotionEnabled()
                && WHALE_HOOKS_INSTALLED.compareAndSet(false, true)) {
            HookUiSurface.INSTANCE.hookWelcomeWhaleMotion(cl);
        }
        if (isTextWaveEnabled()
                && TEXT_WAVE_HOOKS_INSTALLED.compareAndSet(false, true)) {
            HookUiSurface.INSTANCE.hookTextWaveMotion(cl);
        }
        if ((AgentToolConfig.enabledFast() || isDualChatEnabled())
                && AGENT_RENDER_HOOKS_INSTALLED.compareAndSet(false, true)) {
            HookAgentPipeline.INSTANCE.hookHeartbeatToolResponses(cl);
            HookAgentPipeline.INSTANCE.hookHeartbeatPatchDispatcher(cl);
            HookAgentPipeline.INSTANCE.hookTrackedHeartbeatStateWrites(cl);
            HookAgentPipeline.INSTANCE.hookHeartbeatFragmentRenderBoundary(cl);
            HookAgentPipeline.INSTANCE.hookHeartbeatMarkdownInputBoundary(cl);
            HookAgentPipeline.INSTANCE.hookHeartbeatNativeMarkdownParser(cl);
            HookAgentPipeline.INSTANCE.hookAgentToolLogRoundedRect(cl);
            HookAttachmentPipeline.INSTANCE.hookNativeModelFileCards(cl);
            HookAgentPipeline.INSTANCE.hookHeartbeatToolStatusStyle(
                    cl, HostCompat.name("i68"), HostCompat.name("h78"));
            HookAgentPipeline.INSTANCE.hookHeartbeatToolStatusBasicText(
                    cl, HostCompat.isV241() ? "ox1" : HostCompat.name("yg8"),
                    HostCompat.isV241() ? "e" : HostCompat.method("yg8", "b"),
                    HostCompat.name("h78"));
            HookAttachmentPipeline.INSTANCE.hookModelFileLinks();
        }
        if (ChatAppearance.hasEnabledRuntimeEffects()
                && APPEARANCE_HOOKS_INSTALLED.compareAndSet(false, true)) {
            final boolean targetGooglePlay = HostCompat.isGooglePlay();
            try {
                HookUiSurface.INSTANCE.hookChatBubbleCustomization(cl, targetGooglePlay);
            } catch (Throwable channelError) {
                if (HostCompat.isV234()) {
                    log("hookChatBubbleCustomization wiring failed: " + channelError);
                } else {
                    try { HookUiSurface.INSTANCE.hookChatBubbleCustomization(cl, !targetGooglePlay); }
                    catch (Throwable alternateError) {
                        log("hookChatBubbleCustomization wiring failed: " + alternateError);
                    }
                }
            }
            try { HookUiSurface.INSTANCE.hookAssistantAvatarPainter(cl); }
            catch (Throwable error) {
                log("hookAssistantAvatarPainter wiring failed: " + error);
            }
        }
        // Install the credential bridge whenever this build contains the Local API.  Tying the
        // hook to a marker-file read made persisted installations timing-dependent: the first
        // optional-hook pass can run before the protected gateway has reconciled its settings,
        // and the listener then starts without the account-specific Authorization replacement.
        // The two hooks are inert unless z18 binds a request-scoped Route, so installing them
        // eagerly has no effect on ordinary DeepSeek traffic.
        if (HostCompat.isV241()
                && BuildInfo.PROTECTED_BUILD && BuildInfo.LOCAL_API_INCLUDED
                && LOCAL_API_HOOKS_INSTALLED.compareAndSet(false, true)) {
            try {
                log("activating Local API credential hooks on demand");
                HookAccountLoginSecurity.INSTANCE.hookLocalApiAccountRouting(cl);
            } catch (Throwable error) {
                LOCAL_API_HOOKS_INSTALLED.set(false);
                log("Local API credential hook activation failed: "
                        + safeThrowableMessage(error));
            }
        }
    }

    /** Replaces DSLG2 transport boxes before their first frame; unrelated JLaTeX boxes pass on. */
    /**
     * Diagnostic-only: forces every {@code SSLContext.init(...)} call in the host to use a
     * permissive TrustManager, so a local MITM capture proxy's certificate is accepted even by
     * network clients that supply their own (pinned/stricter) TrustManager instead of relying on
     * the platform default driven by network_security_config.xml. Never ship this in a release
     * build; it exists solely to bootstrap the GP 2.5.0 symbol table from captured traffic.
     */
    private void installSslTrustBypassDiagnostic(ClassLoader cl) {
        try {
            final Method init = javax.net.ssl.SSLContext.class.getDeclaredMethod(
                    "init", javax.net.ssl.KeyManager[].class,
                    javax.net.ssl.TrustManager[].class, java.security.SecureRandom.class);
            final javax.net.ssl.TrustManager permissive = new javax.net.ssl.X509TrustManager() {
                @Override public void checkClientTrusted(
                        java.security.cert.X509Certificate[] chain, String authType) {}
                @Override public void checkServerTrusted(
                        java.security.cert.X509Certificate[] chain, String authType) {}
                @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return new java.security.cert.X509Certificate[0];
                }
            };
            hook(init).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object[] args = chain.getArgs().toArray();
                    args[1] = new javax.net.ssl.TrustManager[]{permissive};
                    return chain.proceed(args);
                }
            });
            log("ssl trust bypass installed (diagnostic capture mode)");
        } catch (Throwable t) {
            log("ssl trust bypass install failed: " + t);
        }
    }

    static void registerNativeSearchCalls(
            List<HeartbeatToolProtocol.ToolCall> calls, String visibleLabel) {
        if (calls == null || calls.isEmpty()
                || visibleLabel == null || visibleLabel.length() == 0) return;
        ArrayList<String> keys = new ArrayList<>();
        ArrayList<String> queries = new ArrayList<>();
        for (HeartbeatToolProtocol.ToolCall call : calls) {
            String key = AgentToolTraceStore.key(call);
            if (key.length() == 0) continue;
            keys.add(key);
            String query = HeartbeatToolProtocol.cleanInstruction(call.instruction);
            if (query.length() > 0) queries.add(query);
        }
        if (keys.isEmpty()) return;
        HookAgentPipeline.AGENT_NATIVE_SEARCH_CALLS.put(
                visibleLabel, new HookAgentPipeline.NativeSearchGroup(keys, queries));
        if (HookAgentPipeline.AGENT_NATIVE_SEARCH_CALLS.size() > 96) {
            int remove = HookAgentPipeline.AGENT_NATIVE_SEARCH_CALLS.size() - 72;
            for (String label : HookAgentPipeline.AGENT_NATIVE_SEARCH_CALLS.keySet()) {
                if (remove-- <= 0) break;
                HookAgentPipeline.AGENT_NATIVE_SEARCH_CALLS.remove(label);
            }
        }
    }

    static void registerNativeSearchCall(
            HeartbeatToolProtocol.ToolCall call, String visibleLabel) {
        registerNativeSearchCalls(Collections.singletonList(call), visibleLabel);
    }

    /**
     * Cleans the active 2.3.4 Compose message tree that survives after the server's final static
     * fragment has been constructed.  The database/static object may already be clean while the
     * open conversation still owns an older mutable us2 RESPONSE fragment, which is why reopening
     * the conversation used to fix the leak.  This boundary is invoked only for an assistant body
     * and only writes fragment state when an actual private transport marker is present.
     */
    private static void sanitizeAssistantToolTransportAtRenderBoundary(Object message) {
        if (message == null
                || (!AgentToolConfig.enabledFast() && !NativeDualChatBridge.isActive())) return;
        try {
            List fragments = HookChatPipeline.messageFragments(message);
            if (fragments == null || fragments.isEmpty()) return;
            for (Object fragment : fragments) {
                HookAgentPipeline.sanitizeHeartbeatFragmentAtRenderBoundary(fragment);
            }
        } catch (Throwable error) {
            log("heartbeat active Compose response filter failed: "
                    + safeThrowableMessage(error));
        }
    }

    static void dispatchHeartbeatStateUpdate(
            HeartbeatSanitizedUpdate update) {
        if (update == null) return;
        if (!update.calls.isEmpty()) {
            executeHeartbeatToolCalls(HookSessionManagement.currentHostContext(), update.calls, true);
        }
        for (HeartbeatToolProtocol.RejectedCall rejected : update.rejectedCalls) {
            HookAgentPipeline.queueRejectedAgentToolResult(HookSessionManagement.currentHostContext(), rejected);
        }
    }

    static final class HeartbeatSanitizedUpdate {
        final String safe;
        final ArrayList<HeartbeatToolProtocol.ToolCall> calls;
        final ArrayList<HeartbeatToolProtocol.RejectedCall> rejectedCalls;

        HeartbeatSanitizedUpdate(
                String safe, ArrayList<HeartbeatToolProtocol.ToolCall> calls,
                ArrayList<HeartbeatToolProtocol.RejectedCall> rejectedCalls) {
            this.safe = safe == null ? "" : safe;
            this.calls = calls;
            this.rejectedCalls = rejectedCalls;
        }
    }

    /**
     * Exact 2.4.1/code257 adapter. ou5.p is edit_quota and ou5.q is regenerate_quota;
     * v10 consumes q directly while composing the long-press/regenerate controls. Hook both the
     * producer and consumers so an already-created config is fixed on its next frame. The
     * original nullable values are weakly retained and restored when the setting is switched off.
     */
    private void hookV241LocalChatQuotaUnlock(final ClassLoader cl) {
        if (!HostCompat.isV241()
                || !V241_LOCAL_CHAT_QUOTA_HOOKS_INSTALLED.compareAndSet(false, true)) return;
        try {
            final Class<?> configType = cl.loadClass("ou5");
            Field edit = configType.getDeclaredField("p");
            Field regenerate = configType.getDeclaredField("q");
            edit.setAccessible(true);
            regenerate.setAccessible(true);
            V241_EDIT_QUOTA_FIELD = edit;
            V241_REGENERATE_QUOTA_FIELD = regenerate;

            int constructors = 0;
            for (Constructor<?> constructor : configType.getDeclaredConstructors()) {
                hook(constructor).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        if (isLocalChatQuotaUnlockEnabled()) {
                            applyV241LocalChatQuota(chain.getThisObject(), true);
                        }
                        return result;
                    }
                });
                try { deoptimize(constructor); } catch (Throwable ignored) {}
                constructors++;
            }

            Class<?> controls = cl.loadClass("v10");
            int consumers = 0;
            for (Method method : controls.getDeclaredMethods()) {
                final int configIndex = findExactParameter(method.getParameterTypes(), configType);
                if (configIndex < 0) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        if (isLocalChatQuotaUnlockEnabled()) {
                            applyV241LocalChatQuota(chain.getArg(configIndex), true);
                        }
                        return chain.proceed();
                    }
                });
                try { deoptimize(method); } catch (Throwable ignored) {}
                consumers++;
            }
            log("installed code257 local chat quota unlock constructors=" + constructors
                    + " consumers=" + consumers);
        } catch (Throwable error) {
            V241_LOCAL_CHAT_QUOTA_HOOKS_INSTALLED.set(false);
            log("code257 local chat quota unlock unavailable: "
                    + safeThrowableMessage(error));
        }
    }

    /**
     * Exact 2.3.6/code249 adapter. fp9.e creates m95 code actions; aa5.a is the reasoning action
     * provider surviving R8 inlining.
     */
    private void hookV236ThinkingCodeCopy(final ClassLoader cl) {
        if (!HostCompat.isV236()
                || !V236_THINKING_CODE_COPY_HOOK_INSTALLED.compareAndSet(false, true)) return;
        int installed = 0;
        try {
            Class<?> renderer = cl.loadClass("fp9");
            Method target = null;
            for (Method method : renderer.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if ("e".equals(method.getName())
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && p.length == 5 && p[0] == int.class && p[1] == int.class
                        && p[4] == boolean.class) {
                    target = method;
                    break;
                }
            }
            if (target == null) throw new NoSuchMethodException("fp9.e(int,int,*,*,boolean)");
            hook(target).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    if (!isThinkingCodeCopyEnabled()
                            || Boolean.TRUE.equals(chain.getArg(4))) return chain.proceed();
                    Object[] args = chain.getArgs().toArray(new Object[5]);
                    args[4] = Boolean.TRUE;
                    return chain.proceed(args);
                }
            });
            try { deoptimize(target); } catch (Throwable ignored) {}
            installed++;
        } catch (Throwable error) {
            log("code249 reasoning code-copy factory unavailable: "
                    + safeThrowableMessage(error));
        }
        try {
            final Class<?> actionType = cl.loadClass("m95");
            final Field visible = actionType.getDeclaredField("d");
            visible.setAccessible(true);
            Class<?> provider = cl.loadClass("aa5");
            for (Method method : provider.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!"a".equals(method.getName()) || p.length != 4
                        || p[0] != int.class || p[3] != boolean.class
                        || !List.class.isAssignableFrom(method.getReturnType())) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        if (!isThinkingCodeCopyEnabled() || !(result instanceof List)) return result;
                        for (Object action : new ArrayList<Object>((List) result)) {
                            if (actionType.isInstance(action)) visible.setBoolean(action, true);
                        }
                        return result;
                    }
                });
                try { deoptimize(method); } catch (Throwable ignored) {}
                installed++;
            }
        } catch (Throwable error) {
            log("code249 reasoning code-copy result adapter unavailable: "
                    + safeThrowableMessage(error));
        }
        if (installed == 0) V236_THINKING_CODE_COPY_HOOK_INSTALLED.set(false);
        log("installed code249 reasoning code-copy adapters=" + installed);
    }

    /**
     * Exact 2.4.1/code257 adapter. cy9.i creates the host Markdown code-copy action used by both
     * response and reasoning renderers. Its fifth argument is copied to sf5.d (action visible).
     * Only force that argument on; the host still owns the label, icon, clipboard callback and
     * feedback UI, keeping this visually and behaviorally identical to response code blocks.
     */
    private void hookV241ThinkingCodeCopy(final ClassLoader cl) {
        if (!HostCompat.isV241()
                || !V241_THINKING_CODE_COPY_HOOK_INSTALLED.compareAndSet(false, true)) return;
        int installed = 0;
        try {
            Class<?> renderer = cl.loadClass("cy9");
            Method target = null;
            for (Method method : renderer.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if ("i".equals(method.getName())
                        && java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        && parameters.length == 5
                        && parameters[0] == int.class
                        && parameters[1] == int.class
                        && parameters[4] == boolean.class) {
                    target = method;
                    break;
                }
            }
            if (target == null) throw new NoSuchMethodException("cy9.i(int,int,*,*,boolean)");
            hook(target).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    if (!isThinkingCodeCopyEnabled()
                            || Boolean.TRUE.equals(chain.getArg(4))) return chain.proceed();
                    Object[] args = chain.getArgs().toArray(new Object[5]);
                    args[4] = Boolean.TRUE;
                    return chain.proceed(args);
                }
            });
            try { deoptimize(target); } catch (Throwable ignored) {}
            installed++;
        } catch (Throwable error) {
            log("code257 reasoning code-copy factory unavailable: "
                    + safeThrowableMessage(error));
        }
        try {
            // code257/R8 inlines cy9.i at some Compose call sites.  xf5.a is the surviving
            // action-list boundary: force only sf5.d on the actions it actually returns, while
            // retaining the host's own icon, text, clipboard callback and success feedback.
            final Class<?> actionType = cl.loadClass("sf5");
            final Field visible = actionType.getDeclaredField("d");
            visible.setAccessible(true);
            Class<?> provider = cl.loadClass("xf5");
            for (Method method : provider.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!"a".equals(method.getName()) || p.length != 4
                        || p[0] != int.class || p[3] != boolean.class
                        || !List.class.isAssignableFrom(method.getReturnType())) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        Object result = chain.proceed();
                        if (!isThinkingCodeCopyEnabled() || !(result instanceof List)) {
                            return result;
                        }
                        for (Object action : new ArrayList<Object>((List) result)) {
                            if (actionType.isInstance(action)) visible.setBoolean(action, true);
                        }
                        return result;
                    }
                });
                try { deoptimize(method); } catch (Throwable ignored) {}
                installed++;
            }
        } catch (Throwable error) {
            log("code257 reasoning code-copy result adapter unavailable: "
                    + safeThrowableMessage(error));
        }
        if (installed == 0) {
            V241_THINKING_CODE_COPY_HOOK_INSTALLED.set(false);
        }
        log("installed code257 reasoning code-copy adapters=" + installed);
    }

    private static int findExactParameter(Class<?>[] parameters, Class<?> expected) {
        if (parameters == null || expected == null) return -1;
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i] == expected) return i;
        }
        return -1;
    }

    private static void applyV241LocalChatQuota(Object config, boolean unlock) {
        if (config == null || V241_EDIT_QUOTA_FIELD == null
                || V241_REGENERATE_QUOTA_FIELD == null) return;
        synchronized (V241_LOCAL_CHAT_QUOTA_ORIGINALS) {
            Object[] original = V241_LOCAL_CHAT_QUOTA_ORIGINALS.get(config);
            try {
                if (unlock) {
                    if (original == null) {
                        original = new Object[]{V241_EDIT_QUOTA_FIELD.get(config),
                                V241_REGENERATE_QUOTA_FIELD.get(config)};
                        V241_LOCAL_CHAT_QUOTA_ORIGINALS.put(config, original);
                    }
                    Integer unlimited = Integer.valueOf(Integer.MAX_VALUE);
                    V241_EDIT_QUOTA_FIELD.set(config, unlimited);
                    V241_REGENERATE_QUOTA_FIELD.set(config, unlimited);
                } else if (original != null) {
                    V241_EDIT_QUOTA_FIELD.set(config, original[0]);
                    V241_REGENERATE_QUOTA_FIELD.set(config, original[1]);
                    V241_LOCAL_CHAT_QUOTA_ORIGINALS.remove(config);
                }
            } catch (Throwable error) {
                log("code257 local chat quota apply failed: "
                        + safeThrowableMessage(error));
            }
        }
    }

    private static void applyV236LocalChatQuota(Object config, boolean unlock) {
        if (config == null || V236_EDIT_QUOTA_FIELD == null
                || V236_REGENERATE_QUOTA_FIELD == null) return;
        synchronized (V236_LOCAL_CHAT_QUOTA_ORIGINALS) {
            Object[] original = V236_LOCAL_CHAT_QUOTA_ORIGINALS.get(config);
            try {
                if (unlock) {
                    if (original == null) {
                        original = new Object[]{V236_EDIT_QUOTA_FIELD.get(config),
                                V236_REGENERATE_QUOTA_FIELD.get(config)};
                        V236_LOCAL_CHAT_QUOTA_ORIGINALS.put(config, original);
                    }
                    Integer unlimited = Integer.valueOf(Integer.MAX_VALUE);
                    V236_EDIT_QUOTA_FIELD.set(config, unlimited);
                    V236_REGENERATE_QUOTA_FIELD.set(config, unlimited);
                } else if (original != null) {
                    V236_EDIT_QUOTA_FIELD.set(config, original[0]);
                    V236_REGENERATE_QUOTA_FIELD.set(config, original[1]);
                    V236_LOCAL_CHAT_QUOTA_ORIGINALS.remove(config);
                }
            } catch (Throwable error) {
                log("code249 local chat quota apply failed: "
                        + safeThrowableMessage(error));
            }
        }
    }

    static void pickGalleryImage(Activity act, GalleryPickCallback callback) {
        if (act == null || callback == null) return;
        galleryPickCallback = callback;
        try {
            if (HostCompat.isV241() && Build.VERSION.SDK_INT >= 33) {
                // code257 copies the result into DeepSeek-private storage immediately. Use the
                // system photo picker first: ACTION_OPEN_DOCUMENT is why avatar and wallpaper
                // selection appeared as a generic file browser.
                try {
                    Intent picker = new Intent(MediaStore.ACTION_PICK_IMAGES);
                    picker.setType("image/*");
                    picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    act.startActivityForResult(picker, PICK_IMAGE_REQUEST);
                    return;
                } catch (Throwable photoPickerError) {
                    log("code257 photo picker unavailable, using image document fallback: "
                            + photoPickerError);
                }
            }
            // Preserve the verified legacy path for every older host; code257 also uses it when
            // the device has no system photo picker.
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            act.startActivityForResult(intent, PICK_IMAGE_REQUEST);
        } catch (Throwable t) {
            galleryPickCallback = null;
            log("open gallery picker failed: " + t);
            callback.onPicked(null);
        }
    }

    static void pickCloudPromptFile(Activity act, CloudPromptPickCallback callback) {
        if (act == null || callback == null) return;
        cloudPromptPickCallback = callback;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            // "text/plain" alone hides .md files: many providers report them as
            // text/markdown (or text/x-markdown), not text/plain. "*/*" + EXTRA_MIME_TYPES
            // lets the picker show both while still filtering out unrelated file types.
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"text/plain", "text/markdown", "text/x-markdown"});
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            act.startActivityForResult(intent, CLOUD_PROMPT_UPLOAD_REQUEST);
        } catch (Throwable error) {
            cloudPromptPickCallback = null;
            callback.onPicked(null);
        }
    }

    /** Account marketplace accepts JSON exports and ZIP bundles in addition to TXT. */
    static void pickCloudAccountPackageFile(Activity act, CloudPromptPickCallback callback) {
        if (act == null || callback == null) return;
        cloudPromptPickCallback = callback;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "application/zip", "text/plain", "application/octet-stream"});
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            act.startActivityForResult(intent, CLOUD_PROMPT_UPLOAD_REQUEST);
        } catch (Throwable error) {
            cloudPromptPickCallback = null;
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
            out.put("signed_path", HookAttachmentPipeline.EDITOR_IMAGE_URI_PREFIX + Uri.encode(storedName));
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
        if (copyUriToFileAttempt(act, uri, target, false)) return true;
        // Several OEM photo providers return a stream which opens successfully but fails on its
        // first cloud-backed read. Reopen the same grant as a descriptor before reporting an
        // intermittent "image save failed" to the editor.
        log("retry selected image through file descriptor uri=" + uri);
        return copyUriToFileAttempt(act, uri, target, true);
    }

    private static boolean copyUriToFileAttempt(Activity act, Uri uri, File target,
                                                boolean descriptorFirst) {
        InputStream in = null;
        OutputStream out = null;
        ParcelFileDescriptor descriptor = null;
        try {
            if (!descriptorFirst) {
                try {
                    in = act.getContentResolver().openInputStream(uri);
                } catch (Throwable ignored) {}
            }
            // Some OEM/Android Photo Picker providers expose only a file descriptor. Their
            // ContentResolver.openInputStream() can return null even while the user grant is
            // valid, which made custom image upload fail on otherwise readable selections.
            if (in == null) {
                descriptor = act.getContentResolver().openFileDescriptor(uri, "r");
                if (descriptor != null) in = new FileInputStream(descriptor.getFileDescriptor());
            }
            if (in == null) return false;
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            File temporary = new File(parent, target.getName() + ".part");
            out = new FileOutputStream(temporary, false);
            byte[] buffer = new byte[32768];
            int count;
            long copied = 0L;
            while ((count = in.read(buffer)) >= 0) {
                if (count > 0) {
                    copied += count;
                    // Keep malformed/cloud-provider selections from filling DeepSeek's private
                    // storage. Native image uploads are already bounded well below this value.
                    if (copied > 64L * 1024L * 1024L) return false;
                    out.write(buffer, 0, count);
                }
            }
            out.flush();
            try { ((FileOutputStream) out).getFD().sync(); } catch (Throwable ignored) {}
            try { out.close(); } catch (Throwable ignored) {}
            out = null;
            if (copied <= 0L || temporary.length() != copied) return false;
            if (target.exists() && !target.delete()) return false;
            if (!temporary.renameTo(target)) {
                if (!copyFile(temporary, target)) return false;
                temporary.delete();
            }
            return target.length() == copied;
        } catch (Throwable error) {
            log("copy selected image failed route="
                    + (descriptorFirst ? "descriptor" : "stream")
                    + " uri=" + uri + ": " + error);
            return false;
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) {}
            if (out != null) try { out.close(); } catch (Throwable ignored) {}
            if (descriptor != null) try { descriptor.close(); } catch (Throwable ignored) {}
            File parent = target == null ? null : target.getParentFile();
            File partial = parent == null ? null : new File(parent, target.getName() + ".part");
            if (partial != null && partial.exists()) partial.delete();
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
        if (!path.startsWith(HookAttachmentPipeline.EDITOR_IMAGE_URI_PREFIX)) return null;
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
            Object composer = HookAttachmentPipeline.IMAGE_COMPOSER;
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
        final Object composer = HookAttachmentPipeline.IMAGE_COMPOSER;
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
        Object api = HookAttachmentPipeline.IMAGE_FILE_API;
        ClassLoader cl = HookAttachmentPipeline.IMAGE_HOST_CL;
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

    /** Makes a freshly committed editor conversation visible to runtime guards immediately. */
    static synchronized void registerEditorLocalSession(String sid, Integer currentHead) {
        if (sid == null || sid.length() == 0) return;
        HookSessionManagement.RECENTLY_DELETED_SESSION_IDS.remove(sid);
        HashSet<String> next = ChatEditorUi.localSessionDisplayIdsFromAllBackups();
        if (currentHead != null && currentHead.intValue() > 0) next.add(sid);
        else next.remove(sid);
        HookSessionManagement.LOCAL_SESSION_IDS = next;
        // Force one account-scoped refresh on the next native boundary. The just-created row is
        // already published below, so this never delays its appearance.
        HookSessionManagement.LOCAL_SESSION_IDS_AT = 0L;
        HookSessionManagement.LOCAL_SESSION_IDS_DB_PATH = null;
        if (currentHead != null && currentHead.intValue() > 0) {
            FROZEN_SESSION_HEADS.put(sid, currentHead);
            publishEditorLocalNativeSession(sid);
        } else {
            FROZEN_SESSION_HEADS.remove(sid);
            synchronized (HookSessionManagement.LOCAL_NATIVE_SESSIONS) {
                HookSessionManagement.LOCAL_NATIVE_SESSIONS.remove(sid);
            }
        }
    }

    /** Re-reads a just-edited code257 sidecar/WCDB row into the already-rendered native session. */
    static void refreshV241EditorLocalSessionAfterEdit(final String sid) {
        if (!HostCompat.isV241() || sid == null || sid.length() == 0) return;
        Main module = MODULE;
        final Handler handler = module == null ? null : module.main;
        final ClassLoader loader = hostClassLoader;
        if (handler == null || loader == null) return;
        handler.post(new Runnable() {
            @Override public void run() {
                if (!HostCompat.isV241()) return;
                Object session = null;
                synchronized (HookSessionManagement.LOCAL_NATIVE_SESSIONS) {
                    session = HookSessionManagement.LOCAL_NATIVE_SESSIONS.get(sid);
                }
                if (session == null) {
                    Object state = HookSessionManagement.NATIVE_SESSION_STATE != null
                            ? HookSessionManagement.NATIVE_SESSION_STATE : HookSessionManagement.NATIVE_SESSION_LIST;
                    if (state instanceof List) {
                        for (Object candidate : new ArrayList<Object>((List) state)) {
                            if (sid.equals(String.valueOf(readHostField(candidate, "a")))) {
                                session = candidate;
                                break;
                            }
                        }
                    }
                }
                if (session != null) {
                    boolean refreshed = HookSessionManagement.hydrateFrozenNativeSession(loader, session, sid);
                    log("code257 editor save native refresh sid=" + sid
                            + " ok=" + refreshed);
                }
            }
        });
    }

    private static void publishEditorLocalNativeSession(final String sid) {
        if (sid == null || sid.length() == 0) return;
        Main module = MODULE;
        Handler handler = module == null ? null : module.main;
        if (handler != null && Looper.myLooper() != Looper.getMainLooper()) {
            handler.post(new Runnable() {
                @Override public void run() { publishEditorLocalNativeSession(sid); }
            });
            return;
        }
        Object state = HookSessionManagement.NATIVE_SESSION_STATE != null ? HookSessionManagement.NATIVE_SESSION_STATE : HookSessionManagement.NATIVE_SESSION_LIST;
        if (!(state instanceof List)) return;
        List sessions = (List) state;
        synchronized (HookSessionManagement.LOCAL_NATIVE_SESSIONS) {
            if (HookSessionManagement.LOCAL_NATIVE_SESSIONS.containsKey(sid)) return;
        }
        Object nativeSession = HookSessionManagement.createEditorLocalNativeSession(sid, sessions);
        if (nativeSession == null) return;
        synchronized (HookSessionManagement.LOCAL_NATIVE_SESSIONS) {
            HookSessionManagement.LOCAL_NATIVE_SESSIONS.put(sid, nativeSession);
        }
        try {
            boolean present = false;
            for (Object session : new ArrayList<Object>(sessions)) {
                if (sid.equals(String.valueOf(readHostField(session, "a")))) {
                    present = true;
                    break;
                }
            }
            if (!present) sessions.add(nativeSession);
            HookSessionManagement.sortNativeSessionDirectory(sessions);
            HookSessionManagement.NATIVE_SESSION_STATE = sessions;
            HookSessionManagement.NATIVE_SESSION_LIST = sessions;
            log("published editor-local conversation to native history sid=" + sid);
        } catch (Throwable error) {
            log("publish editor-local conversation failed sid=" + sid + " err=" + error);
        }
    }

    static synchronized void unregisterEditorLocalSession(String sid) {
        if (sid == null || sid.length() == 0) return;
        HashSet<String> next = ChatEditorUi.localSessionIdsFromAllBackups();
        next.addAll(HookSessionManagement.LOCAL_SESSION_IDS);
        next.remove(sid);
        HookSessionManagement.LOCAL_SESSION_IDS = next;
        HookSessionManagement.LOCAL_SESSION_IDS_AT = 0L;
        HookSessionManagement.LOCAL_SESSION_IDS_DB_PATH = null;
        FROZEN_SESSION_HEADS.remove(sid);
        synchronized (HookSessionManagement.LOCAL_NATIVE_SESSIONS) {
            HookSessionManagement.LOCAL_NATIVE_SESSIONS.remove(sid);
        }
    }

    /**
     * Exact code249 hot-rollout repair. fh.e(lq) is the 2.3.6 optimistic publish entry and bh1
     * appends the row into fh.f, that host's canonical yz7 sidebar state list.
     */
    private void hookV236LiveSessionPublish(final ClassLoader cl) {
        if (!BuildInfo.PROTECTED_BUILD || !BuildInfo.LOCAL_API_INCLUDED || !HostCompat.isV236()) return;
        try {
            final Class<?> repository = Class.forName("fh", false, cl);
            final Class<?> sessionType = Class.forName("lq", false, cl);
            final Field stateField = repository.getDeclaredField("f");
            stateField.setAccessible(true);
            int installed = 0;
            for (Method method : repository.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!"e".equals(method.getName()) || method.getReturnType() != void.class
                        || p.length != 1 || p[0] != sessionType) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        final Object owner = chain.getThisObject();
                        final Object session = chain.getArg(0);
                        Object result = chain.proceed();
                        scheduleV236LiveSessionReconcile(owner, session, stateField, 120L);
                        scheduleV236LiveSessionReconcile(owner, session, stateField, 900L);
                        return result;
                    }
                });
                try { deoptimize(method); } catch (Throwable ignored) {}
                installed++;
            }
            log("installed code249 live session publish repair fh.e x" + installed);
        } catch (Throwable error) {
            log("code249 live session publish repair unavailable: "
                    + safeThrowableMessage(error));
        }
    }

    private static void scheduleV236LiveSessionReconcile(final Object repository,
                                                          final Object session,
                                                          final Field stateField,
                                                          long delayMs) {
        Main module = MODULE;
        Handler handler = module == null ? null : module.main;
        if (handler == null || repository == null || session == null) return;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!BuildInfo.PROTECTED_BUILD || !BuildInfo.LOCAL_API_INCLUDED || !HostCompat.isV236()) return;
                try {
                    Object idValue = readHostField(session, "a");
                    String sid = idValue == null ? "" : String.valueOf(idValue);
                    if (sid.length() == 0 || "null".equals(sid)
                            || isLocalApiInternalSession(sid)) return;
                    Object value = stateField.get(repository);
                    if (!(value instanceof List)) return;
                    List state = (List) value;
                    for (Object current : new ArrayList<Object>(state)) {
                        if (sid.equals(String.valueOf(readHostField(current, "a")))) {
                            HookSessionManagement.NATIVE_SESSION_STATE = state;
                            HookSessionManagement.NATIVE_SESSION_LIST = state;
                            return;
                        }
                    }
                    state.add(session);
                    HookSessionManagement.sortNativeSessionDirectory(state);
                    HookSessionManagement.NATIVE_SESSION_STATE = state;
                    HookSessionManagement.NATIVE_SESSION_LIST = state;
                    log("repaired code249 live sidebar session sid=" + sid
                            + " size=" + state.size());
                } catch (Throwable error) {
                    log("repair code249 live sidebar session failed: "
                            + safeThrowableMessage(error));
                }
            }
        }, delayMs);
    }

    /**
     * code257 hot-rollout repair. jh.e(pq) is the repository's optimistic session-publish entry.
     * Its coroutine can persist the row while failing to append it to jh.f, leaving the already
     * composed sidebar stale until a process restart reconstructs the directory. Reconcile only
     * the exact pq passed through that native entry, on the main thread, and never touch storage.
     */
    private void hookV241LiveSessionPublish(final ClassLoader cl) {
        if (!BuildInfo.PROTECTED_BUILD || !BuildInfo.LOCAL_API_INCLUDED || !HostCompat.isV241()) return;
        try {
            final Class<?> repository = Class.forName("jh", false, cl);
            final Class<?> sessionType = Class.forName("pq", false, cl);
            final Field stateField = repository.getDeclaredField("f");
            stateField.setAccessible(true);
            int installed = 0;
            for (Method method : repository.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!"e".equals(method.getName()) || method.getReturnType() != void.class
                        || p.length != 1 || p[0] != sessionType) continue;
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        final Object owner = chain.getThisObject();
                        final Object session = chain.getArg(0);
                        Object result = chain.proceed();
                        scheduleV241LiveSessionReconcile(owner, session, stateField, 120L);
                        scheduleV241LiveSessionReconcile(owner, session, stateField, 900L);
                        return result;
                    }
                });
                try { deoptimize(method); } catch (Throwable ignored) {}
                installed++;
            }
            log("installed code257 live session publish repair jh.e x" + installed);
        } catch (Throwable error) {
            log("code257 live session publish repair unavailable: "
                    + safeThrowableMessage(error));
        }
    }

    private static void scheduleV241LiveSessionReconcile(final Object repository,
                                                          final Object session,
                                                          final Field stateField,
                                                          long delayMs) {
        Main module = MODULE;
        Handler handler = module == null ? null : module.main;
        if (handler == null || repository == null || session == null) return;
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!BuildInfo.PROTECTED_BUILD || !BuildInfo.LOCAL_API_INCLUDED || !HostCompat.isV241()) return;
                try {
                    Object idValue = readHostField(session, "a");
                    String sid = idValue == null ? "" : String.valueOf(idValue);
                    if (sid.length() == 0 || "null".equals(sid)
                            || isLocalApiInternalSession(sid)) return;
                    Object value = stateField.get(repository);
                    if (!(value instanceof List)) return;
                    List state = (List) value;
                    for (Object current : new ArrayList<Object>(state)) {
                        if (sid.equals(String.valueOf(readHostField(current, "a")))) {
                            HookSessionManagement.NATIVE_SESSION_STATE = state;
                            HookSessionManagement.NATIVE_SESSION_LIST = state;
                            return;
                        }
                    }
                    state.add(session);
                    HookSessionManagement.sortNativeSessionDirectory(state);
                    HookSessionManagement.NATIVE_SESSION_STATE = state;
                    HookSessionManagement.NATIVE_SESSION_LIST = state;
                    log("repaired code257 live sidebar session sid=" + sid
                            + " size=" + state.size());
                } catch (Throwable error) {
                    log("repair code257 live sidebar session failed: "
                            + safeThrowableMessage(error));
                }
            }
        }, delayMs);
    }

    /** code257's independent successor of code249 p41.a; never installed on older hosts. */
    private void hookV241FeedbackDeletedFlow(final ClassLoader cl) {
        if (!HostCompat.isV241()) return;
        try {
            Class<?> component = Class.forName("h61", false, cl);
            Class<?> sessionType = Class.forName("pq", false, cl);
            Class<?> feedbackType = Class.forName("tn5", false, cl);
            Class<?> resultType = Class.forName("p46", false, cl);
            int installed = 0;
            for (Method method : component.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"a".equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != void.class
                        || types.length != 4 || types[0] != component
                        || types[1] != sessionType || types[2] != feedbackType
                        || types[3] != resultType) continue;
                try { deoptimize(method); } catch (Throwable ignored) {}
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        try {
                            Object session = chain.getArg(1);
                            Object result = chain.getArg(3);
                            if (result == null || !"m46".equals(
                                    result.getClass().getSimpleName())) {
                                return chain.proceed();
                            }
                            Object codeHolder = readHostField(result, "a");
                            Object code = readHostField(codeHolder, "a");
                            Object id = readHostField(session, "a");
                            String sid = id == null ? null : String.valueOf(id);
                            if (code instanceof Number
                                    && ((Number) code).intValue() == 1
                                    && sid != null
                                    && ChatEditorUi.localSessionIdsFromAllBackups()
                                            .contains(sid)) {
                                log("suppressed code257 feedback-deleted result for editor-local"
                                        + " sid=" + sid);
                                return null;
                            }
                        } catch (Throwable error) {
                            log("inspect code257 feedback deletion failed: " + error);
                        }
                        return chain.proceed();
                    }
                });
                installed++;
            }
            log("installed code257 feedback deletion guard h61.a x" + installed);
        } catch (Throwable error) {
            log("hookV241FeedbackDeletedFlow failed: " + error);
        }
    }

    private static void applyThemeSystemBars(Activity activity) {
        if (activity == null) return;
        ThemeColorConfig.Value config = ThemeColorConfig.get();
        if (!config.enabled) return;
        try {
            boolean dark = (activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            int base = dark ? 0xFF101116 : 0xFFF9F9FC;
            int bar = mixColor(base, config.primary, dark ? 0.24f : 0.20f);
            Window window = activity.getWindow();
            window.setStatusBarColor(bar);
            window.setNavigationBarColor(bar);
            if (Build.VERSION.SDK_INT >= 29) {
                window.setStatusBarContrastEnforced(false);
                window.setNavigationBarContrastEnforced(false);
            }
            int flags = window.getDecorView().getSystemUiVisibility();
            if (dark) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
            installThemeStatusBarUnderlay(activity, bar);
        } catch (Throwable error) {
            log("theme system bars failed: " + safeThrowableMessage(error));
        }
    }

    private static final String THEME_STATUS_BAR_TAG =
            "deekseep_theme_status_bar_underlay_v1";

    private static void installThemeStatusBarUnderlay(Activity activity, int color) {
        try {
            View decor = activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) return;
            ViewGroup group = (ViewGroup) decor;
            View underlay = group.findViewWithTag(THEME_STATUS_BAR_TAG);
            int height = 0;
            int resource = activity.getResources().getIdentifier(
                    "status_bar_height", "dimen", "android");
            if (resource != 0) height = activity.getResources().getDimensionPixelSize(resource);
            if (height <= 0) height = Math.round(24f
                    * activity.getResources().getDisplayMetrics().density);
            if (underlay == null) {
                underlay = new View(activity);
                underlay.setTag(THEME_STATUS_BAR_TAG);
                underlay.setClickable(false);
                underlay.setFocusable(false);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, height, Gravity.TOP);
                group.addView(underlay, lp);
            } else {
                ViewGroup.LayoutParams raw = underlay.getLayoutParams();
                if (raw != null && raw.height != height) {
                    raw.height = height;
                    underlay.setLayoutParams(raw);
                }
                underlay.bringToFront();
            }
            underlay.setBackgroundColor(color);
            underlay.setVisibility(View.VISIBLE);
        } catch (Throwable error) {
            log("theme status underlay failed: " + safeThrowableMessage(error));
        }
    }

    static int mixColor(int first, int second, float amount) {
        float a = Math.max(0f, Math.min(1f, amount));
        return Color.argb(255,
                Math.round(Color.red(first) * (1-a) + Color.red(second) * a),
                Math.round(Color.green(first) * (1-a) + Color.green(second) * a),
                Math.round(Color.blue(first) * (1-a) + Color.blue(second) * a));
    }

    static boolean isHiddenAgentTransportUserMessage(Object message) {
        if (message == null) return false;
        Object role = HookAgentPipeline.privateTransportMessageRole(message);
        return "USER".equals(String.valueOf(role))
                && HookAttachmentPipeline.messageContainsHiddenAgentTransport(message);
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
                    if (HookSessionManagement.NATIVE_SESSION_LIST instanceof List && HookSessionManagement.NATIVE_SESSION_CLICK != null) {
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
        try { HistoryBridge.processNativeSessions(HookSessionManagement.NATIVE_SESSION_LIST); }
        catch (Throwable t) { log("refresh native history snapshots failed: " + t); }
    }

    static boolean openNativeSession(final String sid) {
        if (sid == null || sid.length() == 0) return false;
        if (HookSessionManagement.isSessionRecentlyDeleted(sid)) return false;
        Object sessions = HookSessionManagement.NATIVE_SESSION_LIST;
        Object click = HookSessionManagement.NATIVE_SESSION_CLICK;
        if (!(sessions instanceof List) || click == null) {
            log("native session navigation unavailable: host sidebar state not captured");
            return false;
        }
        final Object session = findNativeSession(sid);
        if (session == null) return false;
        final Handler handler = currentMainHandler();
        if (handler == null) return invokeHostOneArg(click, session);
        // 宿主若停在设置路由，侧栏点击只加载会话不切页，设置页仍盖在中间。
        // 先按宿主返回逻辑退出设置路由（最多 6 层），回到 chat 主路由后再触发点击。
        handler.post(new Runnable() {
            public void run() { navigateHomeThenOpen(handler, click, session, sid, 0); }
        });
        return true;
    }

    /** Opens DeepSeek's own SettingsNestedGraph through the host navigation controller. */
    static boolean openNativeDeepSeekSettings(Activity activity) {
        final Main module = MODULE;
        final Object nav = module == null ? null : module.navController.get();
        final ClassLoader loader = hostClassLoader;
        if (activity == null || activity.isFinishing() || nav == null || loader == null) {
            if (activity != null) {
                Toast.makeText(activity, UiLanguage.text(activity,
                        "DeepSeek 设置尚未准备好，请回到主页后重试",
                        "DeepSeek settings are not ready; return home and try again"),
                        Toast.LENGTH_SHORT).show();
            }
            log("native DeepSeek settings navigation unavailable: nav=" + (nav != null));
            return false;
        }
        Runnable open = new Runnable() {
            @Override public void run() {
                try {
                    if (navigateToNativeSettingsGraph(nav, loader)) {
                        log("opened native DeepSeek settings from right-edge gesture");
                    } else {
                        log("native DeepSeek settings route method not found");
                    }
                } catch (Throwable error) {
                    log("native DeepSeek settings navigation failed: "
                            + safeThrowableMessage(error));
                }
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) open.run();
        else module.main.post(open);
        return true;
    }

    private static boolean navigateToNativeSettingsGraph(Object nav, ClassLoader loader)
            throws Throwable {
        String[] routeNames;
        if (HostCompat.isV241()) {
            // code257 SettingsNestedGraph.SettingsRoute, verified from ca7's serializer table.
            routeNames = new String[]{"hv7"};
        } else if (HostCompat.isV236()) {
            // code249: hn7/og7 are SettingsRoute. dn7 is AccountDeletionRoute.
            routeNames = HostCompat.isGooglePlay()
                    ? new String[]{"og7"} : new String[]{"hn7"};
        } else if (HostCompat.isV234()) {
            routeNames = HostCompat.isGooglePlay()
                    ? new String[]{"vq7"} : new String[]{"an7"};
        } else if (HostCompat.isV230()) {
            routeNames = new String[]{"sf7"};
        } else {
            routeNames = new String[]{"yc7"};
        }
        Object route = null;
        for (String name : routeNames) {
            try {
                Class<?> type = loader.loadClass(name);
                Field instance = type.getDeclaredField("INSTANCE");
                instance.setAccessible(true);
                route = instance.get(null);
                if (route != null) break;
            } catch (Throwable ignored) {}
        }
        if (route == null) return false;
        for (Class<?> type = nav.getClass(); type != null; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                Class<?>[] parameters = method.getParameterTypes();
                if (!"n".equals(method.getName()) || parameters.length != 2
                        || !parameters[0].isInstance(route)) continue;
                method.setAccessible(true);
                method.invoke(nav, route, null);
                return true;
            }
        }
        return false;
    }

    private static void navigateHomeThenOpen(final Handler handler, final Object click,
                                             final Object session, final String sid, final int attempt) {
        try {
            Object nav = MODULE == null ? null : MODULE.navController.get();
            String route = nav == null ? null : currentRoute(nav);
            if (route == null || !isSettingsRootRouteName(route) || attempt >= 6) {
                if (invokeHostOneArg(click, session)) {
                    log("native session navigation sid=" + sid);
                } else {
                    log("native session click failed sid=" + sid);
                }
                return;
            }
            if (invokeNavPop(nav)) {
                handler.postDelayed(new Runnable() {
                    public void run() { navigateHomeThenOpen(handler, click, session, sid, attempt + 1); }
                }, 120L);
            } else {
                if (invokeHostOneArg(click, session)) {
                    log("native session navigation sid=" + sid);
                }
            }
        } catch (Throwable t) {
            log("exit settings route failed: " + t);
            try {
                if (invokeHostOneArg(click, session)) log("native session navigation sid=" + sid);
            } catch (Throwable ignored) {}
        }
    }

    private static Method findNavPopMethod() {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = Main.class.getClassLoader();
            Class<?> gf8 = HostCompat.load(cl, "gf8");
            for (Method m : gf8.getDeclaredMethods()) {
                if (m.getName().equals("A0") && m.getParameterTypes().length == 1) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean invokeNavPop(Object nav) {
        if (HostCompat.isV241() && nav != null) {
            // code257 uses androidx Navigation's p16.b() as its instance popBackStack entry.
            // Keep this lookup wholly separate from the older gf8/ii8 extension path.
            try {
                Method pop = nav.getClass().getDeclaredMethod("b");
                if (pop.getReturnType() == boolean.class
                        && pop.getParameterTypes().length == 0) {
                    pop.setAccessible(true);
                    return Boolean.TRUE.equals(pop.invoke(nav));
                }
            } catch (Throwable ignored) {}
            return false;
        }
        Method pop = findNavPopMethod();
        if (pop == null || nav == null) return false;
        try {
            if (Modifier.isStatic(pop.getModifiers())) {
                pop.invoke(null, nav);
                return true;
            }
            // 实例方法：宿主通常以单例持有（如 ii8.a）
            try {
                java.lang.reflect.Field singleton = pop.getDeclaringClass().getDeclaredField("a");
                singleton.setAccessible(true);
                pop.invoke(singleton.get(null), nav);
                return true;
            } catch (Throwable ignored) {
                return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
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
                if (HookSessionManagement.NATIVE_SESSION_LIST instanceof List
                        && HookSessionManagement.NATIVE_SESSION_CLICK != null
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
        if (NATIVE_SESSION_LIST != null) HookSessionManagement.NATIVE_SESSION_LIST = NATIVE_SESSION_LIST;
        if (NATIVE_SESSION_EVENTS != null) HookSessionManagement.NATIVE_SESSION_EVENTS = NATIVE_SESSION_EVENTS;
        if (LOCAL_SESSION_IDS != null) HookSessionManagement.LOCAL_SESSION_IDS = LOCAL_SESSION_IDS;
        Object action;
        synchronized (HookSessionManagement.SIDEBAR_DELETE_ACTIONS) {
            action = HookSessionManagement.SIDEBAR_DELETE_ACTIONS.get(sid);
        }
        return HookSessionManagement.executeNativeDelete(new HookSessionManagement.NativeDeleteRequest(
                sid, findNativeSession(sid), HookSessionManagement.NATIVE_SESSION_EVENTS, action));
    }

    static Object findNativeSession(String sid) {
        Object sessions = HookSessionManagement.NATIVE_SESSION_LIST;
        if (sessions instanceof List) {
            try {
                for (Object session : new ArrayList<Object>((List) sessions)) {
                    if (sid.equals(String.valueOf(readHostField(session, "a")))) return session;
                }
            } catch (Throwable ignored) {}
        }
        synchronized (HookSessionManagement.LOCAL_NATIVE_SESSIONS) {
            return HookSessionManagement.LOCAL_NATIVE_SESSIONS.get(sid);
        }
    }

    public static Object readHostField(Object target, String name) {
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

    static boolean invokeHostOneArg(Object action, Object value) {
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

    private static boolean isSettingsRootRoute(Object route) {
        if (route == null) return false;
        String n = route.getClass().getName();
        if (HostCompat.isV241()) {
            return n.endsWith(".hv7") || n.equals("hv7");
        }
        return n.endsWith(".yc7") || n.endsWith(".vc7") || n.equals("yc7") || n.equals("vc7");
    }

    void scheduleRouteCheck(final Object nav) {
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
            lastRoute = route;
            ChatAppearance.onRouteChanged(curAct.get(), route);
            syncCompactionButtonWithRoute();
            if (route == null || route.length() == 0) return;
            if (btn.get() == null) return;
            if (!isSettingsRootRouteName(route)) {
                log("route left settings: " + route);
                hideButton();
            } else {
                log("route still settings: " + route);
                // The settings composable is renamed independently of the navigation route on
                // recent Play builds.  Once the route itself is identified, add the entry here as
                // a second stable path instead of waiting for a brittle method-name hook.
                main.post(new Runnable() { public void run() { showButton(); } });
            }
        } catch (Throwable t) { log("sync route failed: " + t); }
    }

    /**
     * Chat-page compaction chip visibility. Only the settings root route hides it, because that is
     * where the settings entry chip lives; every other route is either the chat destination or a
     * page where tapping the chip says there is no conversation open yet. Whether each build's route
     * string really distinguishes those cases can only be checked on a device, which is what
     * {@link #isChatCompactionButtonEnabled()} is the escape hatch for.
     */
    void syncCompactionButtonWithRoute() {
        try {
            String route = lastRoute;
            boolean settings = route != null && route.length() > 0
                    && isSettingsRootRouteName(route);
            if (!isChatCompactionButtonEnabled() || settings) {
                hideCompactionButton();
                return;
            }
            showCompactionButton();
        } catch (Throwable t) { log("sync compaction button failed: " + t); }
    }

    private static boolean isSettingsRootRouteName(String route) {
        if (HostCompat.isV241()) {
            return route.contains("SettingsNestedGraph.SettingsRoute")
                    || route.equals("hv7")
                    || route.endsWith(".hv7")
                    || route.contains(" route=hv7");
        }
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

            String route = HookAttachmentPipeline.stringField(dest, "g");
            if (route != null && route.length() > 0) return route;
            return String.valueOf(dest);
        } catch (Throwable ignored) {
            return null;
        }
    }

    // 首次注入时显示一份简短使用说明；“稍后”不会退出宿主，“我知道了”后不再提示。
    private static final String SUPPORTED_DEEPSEEK_DOWNLOAD_URL =
            "https://1852762731.share.123pan.cn/123pan/i1Tgvd-YEDCA";

    private void installRetiredHostNotice(final String versionName) {
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            hook(onResume).intercept(new Hooker() {
                @Override public Object intercept(Chain chain) throws Throwable {
                    Object result = chain.proceed();
                    if (!UNSUPPORTED_VERSION_DIALOG_SHOWN.compareAndSet(false, true)) return result;
                    final Activity activity = (Activity) chain.getThisObject();
                    if (activity == null || activity.isFinishing()) return result;
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            try {
                                new android.app.AlertDialog.Builder(activity)
                                        .setTitle(UiLanguage.text(activity,
                                                "此版本已停止维护",
                                                "This version is no longer maintained"))
                                        .setMessage(UiLanguage.text(activity,
                                                "DeepSeek " + versionName
                                                        + " 已移出支持范围，模块功能不会启动。请升级到 DeepSeek 2.3.4 或 2.3.6。",
                                                "DeepSeek " + versionName
                                                        + " is no longer supported, so module features will not start. Upgrade to DeepSeek 2.3.4 or 2.3.6."))
                                        .setPositiveButton(UiLanguage.text(activity,
                                                "知道了", "OK"), null)
                                        .show();
                            } catch (Throwable ignored) {}
                        }
                    });
                    return result;
                }
            });
        } catch (Throwable error) {
            log("retired host notice hook failed: " + error);
        }
    }

    private boolean maybeShowUnsupportedHostVersion(final Activity activity) {
        if (activity == null || activity.isFinishing()) return false;
        // Structural runtime detection is authoritative. Some vendor/repacked supported APKs
        // decorate versionName, while their verified code245/246/249/257 symbol table still
        // matches exactly. Do not show an unsupported warning after that branch was identified.
        if (HostCompat.supportsMaintainedFeatureRuntime()) return false;
        String versionName;
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            versionName = info == null ? null : info.versionName;
        } catch (Throwable ignored) {
            return false;
        }
        if (HostCompat.supportsHostVersionName(versionName)) return false;
        if (!UNSUPPORTED_VERSION_DIALOG_SHOWN.compareAndSet(false, true)) return true;
        final String actual = versionName == null ? "unknown" : versionName;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                DeekseepUi.showCustomConfirm(activity,
                        UiLanguage.text(activity,
                                "DeepSeek 版本不受支持", "Unsupported DeepSeek version"),
                        UiLanguage.text(activity,
                                "当前版本 " + actual + " 不受支持，很可能无法正常使用。建议下载并安装受支持的 DeepSeek 2.3.4；也可以忽略并自行承担风险。",
                                "DeepSeek " + actual + " is unsupported and is unlikely to work correctly. Download the supported DeepSeek 2.3.4 build, or ignore this warning at your own risk."),
                        UiLanguage.text(activity, "忽略", "Ignore"),
                        UiLanguage.text(activity, "下载", "Download"),
                        false, null, new Runnable() {
                            @Override public void run() {
                                try {
                                    Intent download = new Intent(Intent.ACTION_VIEW,
                                            Uri.parse(SUPPORTED_DEEPSEEK_DOWNLOAD_URL));
                                    download.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                    activity.startActivity(download);
                                } catch (Throwable error) {
                                    try {
                                        Toast.makeText(activity,
                                                SUPPORTED_DEEPSEEK_DOWNLOAD_URL,
                                                Toast.LENGTH_LONG).show();
                                    } catch (Throwable ignored) {}
                                }
                            }
                        });
            }
        });
        return true;
    }

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

    void showButton() {
        try {
            if (!NativeSettingsEntryPolicy.showFloating(
                    HookUiSurface.isNativeSettingsEntryEnabled(), HookUiSurface.nativeSettingsRowHooked,
                    HookUiSurface.nativeSettingsRowEmitted)) {
                hideButton();
                return;
            }
            final Activity act = curAct.get();
            if (act == null || act.isFinishing()) return;
            if (BuildInfo.PROTECTED_BUILD && !CloudPromptClient.hasValidLicense(act)) {
                hideButton();
                return;
            }

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

    void hideButton() {
        try {
            TextView existing = btn.get();
            if (existing == null) return;
            ViewGroup parent = (ViewGroup) existing.getParent();
            if (parent != null) parent.removeView(existing);
            btn = new WeakReference<>(null);
            log("button removed");
        } catch (Throwable t) { log("hideButton failed: " + t); }
    }

    /**
     * The chat page's compaction chip. Unlike the settings entry it is not tied to a composable
     * hook: it is added to whatever activity is current and taken away again when the route says so.
     */
    void showCompactionButton() {
        try {
            if (!isChatCompactionButtonEnabled()) {
                hideCompactionButton();
                return;
            }
            final Activity act = curAct.get();
            if (act == null || act.isFinishing()) return;
            if (BuildInfo.PROTECTED_BUILD && !CloudPromptClient.hasValidLicense(act)) {
                hideCompactionButton();
                return;
            }

            TextView existing = compactBtn.get();
            if (existing != null && existing.getContext() == act && existing.getParent() != null) {
                existing.setTextColor(DeekseepUi.isDark(act) ? 0xFFECECEC : 0xFF1A1A1A);
                existing.setVisibility(View.VISIBLE);
                existing.bringToFront();
                return;
            }

            ViewGroup content = act.findViewById(android.R.id.content);
            if (content == null) return;

            TextView b = DeekseepUi.createEntryButton(act,
                    UiLanguage.dynamic(act, "压缩对话"), new View.OnClickListener() {
                public void onClick(View v) {
                    try { ChatCompactionEntryUi.showCompactConfirm(act); }
                    catch (Throwable t) { log("compaction confirm failed: " + t); }
                }
            });

            // Measured up front because the placement needs the chip's own size to compute the travel
            // range, and a WRAP_CONTENT child has none until it has been through a layout pass. The
            // host's content may also have no size yet on a cold start; the placement listener below
            // redoes this placement as soon as a real size exists.
            b.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int[] resting = compactionChipMargins(
                    act, content, b.getMeasuredWidth(), b.getMeasuredHeight());

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            // Left rather than end: the drag maths needs a margin that grows towards the right.
            lp.gravity = Gravity.TOP | Gravity.LEFT;
            lp.leftMargin = resting[0];
            lp.topMargin = resting[1];
            content.addView(b, lp);
            b.bringToFront();
            installCompactionChipDrag(b, act, content);
            installCompactionChipPlacement(b, act, content);
            compactBtn = new WeakReference<>(b);
            log("compaction button added on " + act.getClass().getName());
        } catch (Throwable t) { log("showCompactionButton failed: " + t); }
    }

    private static int compactionChipMargin(Context ctx) {
        return DeekseepUi.dp(ctx, 12);
    }

    /** Top of the chip's travel: below the status bar, where the entry chip also rests. */
    private static int compactionChipTopBase(Context ctx) {
        return DeekseepUi.statusBarHeight(ctx) + DeekseepUi.dp(ctx, 8);
    }

    private static int clampChip(int value, int low, int high) {
        return value < low ? low : (value > high ? high : value);
    }

    private static float clampChipFraction(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    /** The remembered chip fraction, or the default resting place when nothing is stored. */
    private static float[] compactionChipFraction() {
        float x = CHIP_DEFAULT_FRACTION_X;
        float y = CHIP_DEFAULT_FRACTION_Y;
        try {
            String value = readSmallText(CHAT_COMPACTION_CHIP_POS_FILE);
            if (value != null && value.length() > 0) {
                String[] parts = value.trim().split("\\s+");
                if (parts.length >= 2) {
                    x = clampChipFraction(Float.parseFloat(parts[0]));
                    y = clampChipFraction(Float.parseFloat(parts[1]));
                }
            }
        } catch (Throwable ignored) { }
        return new float[] { x, y };
    }

    /** Turns the remembered fraction into margins for a chip of the given measured size. */
    private static int[] compactionChipMargins(Context ctx, ViewGroup content,
                                               int width, int height) {
        int margin = compactionChipMargin(ctx);
        int topBase = compactionChipTopBase(ctx);
        float[] fraction = compactionChipFraction();
        int travelX = Math.max(0, content.getWidth() - width - 2 * margin);
        int travelY = Math.max(0, content.getHeight() - height - topBase - margin);
        return new int[] {
                margin + Math.round(fraction[0] * travelX),
                topBase + Math.round(fraction[1] * travelY) };
    }

    /**
     * Re-places the chip once — and every time — the host's content reports a size the placement can
     * be computed against. Two cases need this. A cold start can add the chip before the host has
     * measured anything, and {@link #compactionChipMargins} would then multiply the remembered
     * fraction into a travel range of zero, pinning the chip to the top-left corner where nothing
     * would ever move it again. And after a rotation or a split-screen resize the fraction has to be
     * turned into margins again, which is the whole reason the position is stored as a fraction. The
     * listener ignores callbacks for a content size it has already placed against, so a window that
     * keeps relaying out the same size cannot spin here.
     */
    private void installCompactionChipPlacement(final TextView chip, final Activity act,
                                                final ViewGroup content) {
        chip.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            private int placedWidth;
            private int placedHeight;

            @Override public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                                 int oldLeft, int oldTop, int oldRight,
                                                 int oldBottom) {
                int w = content.getWidth();
                int h = content.getHeight();
                if (w <= 0 || h <= 0) return;
                if (w == placedWidth && h == placedHeight) return;
                if (v.getParent() != content) return;
                ViewGroup.LayoutParams raw = v.getLayoutParams();
                if (!(raw instanceof ViewGroup.MarginLayoutParams)) return;
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
                // Recorded before the write: setLayoutParams asks for another traversal, and the
                // re-entry must fall out at the size check above rather than re-place the chip.
                placedWidth = w;
                placedHeight = h;
                int[] wanted = compactionChipMargins(act, content,
                        v.getMeasuredWidth(), v.getMeasuredHeight());
                if (wanted[0] == lp.leftMargin && wanted[1] == lp.topMargin) return;
                lp.leftMargin = wanted[0];
                lp.topMargin = wanted[1];
                v.setLayoutParams(lp);
            }
        });
    }

    /**
     * Makes the chip draggable. The chip is a sibling of the host's content, so the gesture arrives
     * here whole and the host never scrolls underneath it. A gesture shorter than the touch slop is
     * replayed as a click, so tapping still opens the confirmation.
     */
    private void installCompactionChipDrag(final TextView chip, final Activity act,
                                           final ViewGroup content) {
        final int margin = compactionChipMargin(act);
        final int topBase = compactionChipTopBase(act);
        final int slop = Math.max(1, DeekseepUi.dp(act, 8));
        chip.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startLeft;
            private int startTop;
            private boolean dragging;

            @Override public boolean onTouch(View v, MotionEvent event) {
                ViewGroup.LayoutParams raw = v.getLayoutParams();
                if (!(raw instanceof ViewGroup.MarginLayoutParams)) return false;
                ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startLeft = lp.leftMargin;
                        startTop = lp.topMargin;
                        dragging = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dx = Math.round(event.getRawX() - downX);
                        int dy = Math.round(event.getRawY() - downY);
                        if (!dragging && Math.abs(dx) + Math.abs(dy) < slop) return true;
                        dragging = true;
                        lp.leftMargin = clampChip(startLeft + dx, margin,
                                Math.max(margin, content.getWidth() - v.getWidth() - margin));
                        lp.topMargin = clampChip(startTop + dy, topBase,
                                Math.max(topBase, content.getHeight() - v.getHeight() - margin));
                        v.setLayoutParams(lp);
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!dragging) {
                            v.performClick();
                            return true;
                        }
                        dragging = false;
                        rememberCompactionChipPosition(v, content);
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        // A revoked gesture is not a tap. The host can cancel the stream at any
                        // moment — window focus loss, or the chip being removed by a route change —
                        // and a click synthesised here would open the dialog for a touch the user
                        // never completed. Keep a move that did happen; never invent a click.
                        if (dragging) {
                            dragging = false;
                            rememberCompactionChipPosition(v, content);
                        }
                        return true;
                    default:
                        // We own the whole gesture once ACTION_DOWN has been consumed, so the extra
                        // pointer actions are swallowed here rather than leaking to onTouchEvent.
                        return true;
                }
            }
        });
    }

    /** Stores the dragged position as a fraction of the travel range. */
    private static void rememberCompactionChipPosition(View chip, ViewGroup content) {
        try {
            ViewGroup.LayoutParams raw = chip.getLayoutParams();
            if (!(raw instanceof ViewGroup.MarginLayoutParams)) return;
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) raw;
            Context ctx = chip.getContext();
            int margin = compactionChipMargin(ctx);
            int topBase = compactionChipTopBase(ctx);
            int travelX = Math.max(1, content.getWidth() - chip.getWidth() - 2 * margin);
            int travelY = Math.max(1, content.getHeight() - chip.getHeight() - topBase - margin);
            overwriteTextFile(CHAT_COMPACTION_CHIP_POS_FILE, String.format(Locale.US, "%.4f %.4f",
                    clampChipFraction((lp.leftMargin - margin) / (float) travelX),
                    clampChipFraction((lp.topMargin - topBase) / (float) travelY)));
        } catch (Throwable t) {
            log("compaction chip position save failed: " + safeThrowableMessage(t));
        }
    }

    void hideCompactionButton() {
        try {
            TextView existing = compactBtn.get();
            compactBtn = new WeakReference<>(null);
            if (existing == null) return;
            ViewGroup parent = (ViewGroup) existing.getParent();
            if (parent != null) parent.removeView(existing);
        } catch (Throwable t) { log("hideCompactionButton failed: " + t); }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 专家图片 → 视觉描述中继（从 legacy 移植；此段为纯反射+现代 hook API）
    // ══════════════════════════════════════════════════════════════════════

    // 1) transport 入口 r92.b：捕获活着的 r92、把发送点图片挂到 ew0、返回时包装 Flow 跑中继
    private void installNetworkPayloadCapture(ClassLoader cl) {
        try {
            Class<?> rs0 = HostCompat.isV241()
                    ? cl.loadClass("nv0") : HostCompat.load(cl, "rs0");
            int n = 0;
            if (HostCompat.isV241()) {
                Class<?> transport = cl.loadClass("ai2");
                for (Method method : transport.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if ("b".equals(method.getName()) && parameters.length == 2
                            && parameters[0] == rs0 && parameters[1] == Long.class) {
                        HookChatPipeline.INSTANCE.hookTransport(method);
                        n++;
                        log("installed network payload capture on ai2.b(code257)");
                    }
                }
            }
            // 快路径：transport 类 b(rs0,Long)。build 间该类改名(2.2.1=r92 / 2.2.2=s92)，两名都试。
            for (String legacyTxName : new String[]{"r92", "s92", "t92", "q92"}) {
                if (n > 0) break;
                String txName = HostCompat.name(legacyTxName);
                try {
                    Class<?> txc = cl.loadClass(txName);
                    for (Method m : txc.getDeclaredMethods()) {
                        Class<?>[] pts = m.getParameterTypes();
                        if (!m.getName().equals("b") || pts.length != 2 || !rs0.isAssignableFrom(pts[0])) continue;
                        HookChatPipeline.INSTANCE.hookTransport(m); n++;
                        log("installed network payload capture on " + txName + ".b");
                    }
                } catch (Throwable ignored) {}
                if (n > 0) break;
            }
            // 兜底：设备上 DeepSeek 有另一个 build（transport 类被改名），r92 变空类。
            // rs0(接口)与 Long 跨 build 稳定 → 按结构签名 (rs0,Long) 在运行时 dex 里扫出真正的 transport 方法。
            if (n == 0) {
                Method tx = findTransportByStructure(cl, rs0);
                if (tx != null) { HookChatPipeline.INSTANCE.hookTransport(tx); n = 1;
                    log("installed network payload capture via structural scan x1"); }
                else log("structural transport scan found nothing");
            }
            // 中继实现：collect 时机的 hook(见 registerRelayFlow)。返回值是 Object，不会被强转闪退。
            HookAttachmentPipeline.INSTANCE.installExpertFlowCollectHook(cl);
        } catch (Throwable t) { log("installNetworkPayloadCapture failed: " + t); }
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

    // 3) 发送点捕获完整 List<fp> 及 tp.f() 当前会话模型。
    // 普通文件必须保留为 DeepSeek 原生附件；只有 is_image=true 的 fp 才进入视觉中继。
    private void installExpertImageFpCapture(final ClassLoader cl) {
        if (HostCompat.isV234()) {
            if (HostCompat.isGooglePlay()) {
                HookAttachmentPipeline.INSTANCE.hookSendPointFps234(cl, "xx0");
                HookAttachmentPipeline.INSTANCE.hookSendPointFps234(cl, "ix0");
                HookAttachmentPipeline.INSTANCE.hookSendPointFps234(cl, "ox0");
            } else {
                HookAttachmentPipeline.INSTANCE.hookSendPointFps234(cl, "ew0");
                HookAttachmentPipeline.INSTANCE.hookSendPointFps234(cl, "pv0");
                HookAttachmentPipeline.INSTANCE.hookSendPointFps234(cl, "vv0");
            }
            return;
        }
        HookAttachmentPipeline.INSTANCE.hookSendPointFps(cl, "fu0", true);
        HookAttachmentPipeline.INSTANCE.hookSendPointFps(cl, "uu0", false);
    }

    public static Object invokeNoArg(Object target, String name) {
        try {
            Method m = target.getClass().getMethod(
                    HostCompat.instanceMethod(target, name));
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable t) { return null; }
    }

    static Integer intField(Object obj, String name) {
        Object value = fieldByName(obj, name);
        return value instanceof Number ? Integer.valueOf(((Number) value).intValue()) : null;
    }

    static boolean forceSetObjectField(Object obj, String name, Object value) {
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

    /** Native completion entry used only by the in-app dual workspace. Never opens a socket. */
    static z2.CompletionResult completeDualChat(
            String scope, String model, String userText) throws Exception {
        Main module = MODULE;
        if (module == null) {
            throw new IOException("Deekseep hook is not ready");
        }
        String selected = model == null ? "deepseek-chat" : model;
        boolean reasoning = "deepseek-reasoner".equals(selected);
        String nativeModel = "deepseek-expert".equals(selected) ? "expert" : "default";
        String user = HistoryBridge.stripInjectedSystemPrompts(
                userText == null ? "" : userText).trim();
        if (user.length() == 0) {
            throw new IOException("Message is empty");
        }
        String prompt = HistoryBridge.wrapSystemPrompt(ModelFileOutput.PROMPT, user);
        String id = "dual-" + Long.toHexString(System.currentTimeMillis()) + "-"
                + Integer.toHexString(System.identityHashCode(Thread.currentThread()));
        z2.CompletionRequest request =
                new z2.CompletionRequest(
                        id, selected, nativeModel, prompt, prompt, reasoning, false,
                        4096, null, null, false)
                        .withClientSessionScope(scope);
        HookAccountLoginSecurity.tlProactiveHeartbeatRequest.set(Boolean.TRUE);
        try {
            return module.executeLocalApiCompletion(request, null);
        } finally {
            HookAccountLoginSecurity.tlProactiveHeartbeatRequest.remove();
        }
    }

    /** Stable in-process AI service for Java plugins. No loopback socket or API key is exposed. */
    static z2.CompletionResult completePluginAi(String pluginId, String model,
            String systemPrompt, String userText, int maxOutputTokens) throws Exception {
        Main module = MODULE;
        if (module == null || hostClassLoader == null || liveR92 == null || liveQ71 == null) {
            throw new IOException("DeepSeek native AI backend is not ready");
        }
        String selected = model == null || model.trim().length() == 0
                ? "deepseek-chat" : model.trim();
        boolean reasoning = "deepseek-reasoner".equals(selected);
        String nativeModel = "deepseek-expert".equals(selected) ? "expert" : "default";
        String user = HistoryBridge.stripInjectedSystemPrompts(
                userText == null ? "" : userText).trim();
        if (user.length() == 0) throw new IOException("Prompt is empty");
        String system = systemPrompt == null ? "" : systemPrompt.trim();
        String prompt = system.length() == 0 ? user
                : HistoryBridge.wrapSystemPrompt(system, user);
        String id = "plugin-" + Long.toHexString(System.currentTimeMillis()) + "-"
                + Integer.toHexString(System.identityHashCode(Thread.currentThread()));
        String safePlugin = pluginId == null ? "unknown"
                : pluginId.replaceAll("[^A-Za-z0-9_.-]", "_");
        z2.CompletionRequest request = new z2.CompletionRequest(
                id, selected, nativeModel, prompt, prompt, reasoning, false,
                Math.max(128, Math.min(32768, maxOutputTokens)),
                null, null, false).withClientSessionScope(
                        "plugin-" + safePlugin + "-" + id);
        HookAccountLoginSecurity.tlProactiveHeartbeatRequest.set(Boolean.TRUE);
        try {
            return module.executeLocalApiCompletion(request, null);
        } finally {
            HookAccountLoginSecurity.tlProactiveHeartbeatRequest.remove();
        }
    }

    /** Original pre-code257 account-header bridge, retained as an independent legacy adapter. */
    void hookLegacyLocalApiAccountRouting(final ClassLoader loader) {
        String authOwner = HostCompat.localApiAuthInterceptorClass();
        String headerOwner = HostCompat.localApiHeaderBuilderClass();
        String setterName = HostCompat.localApiHeaderSetterMethod();
        int marked = 0;
        int replaced = 0;
        try {
            Class<?> owner = Class.forName(authOwner, false, loader);
            for (Method method : owner.getDeclaredMethods()) {
                if (!"a".equals(method.getName()) || method.getParameterTypes().length != 2) {
                    continue;
                }
                hook(method).intercept(new Hooker() {
                    @Override public Object intercept(Chain chain) throws Throwable {
                        z12.Route route = HookAccountLoginSecurity.tlLocalApiAccountRoute.get();
                        if (route != null) {
                            Object request = chain.getArg(0);
                            Object builder = readHostField(request, "c");
                            if (builder != null) {
                                LOCAL_API_ROUTED_HEADER_BUILDERS.put(builder, route.token);
                            }
                        }
                        return chain.proceed();
                    }
                });
                marked++;
            }
        } catch (Throwable error) {
            log("local API account auth marker unavailable " + authOwner + ": "
                    + safeThrowableMessage(error));
        }
        try {
            Class<?> builder = Class.forName(headerOwner, false, loader);
            // code249's lq3 only declares validation methods; l0(String,String) is inherited
            // from js1. Walk the hierarchy so the independently verified legacy adapter hooks
            // the method that se0 actually invokes.
            for (Class<?> type = builder; type != null && type != Object.class;
                    type = type.getSuperclass()) {
                for (Method method : type.getDeclaredMethods()) {
                    Class<?>[] parameters = method.getParameterTypes();
                    if (!setterName.equals(method.getName()) || parameters.length != 2
                            || parameters[0] != String.class || parameters[1] != String.class) {
                        continue;
                    }
                    hook(method).intercept(new Hooker() {
                        @Override public Object intercept(Chain chain) throws Throwable {
                            Object key = chain.getArg(0);
                            String token = LOCAL_API_ROUTED_HEADER_BUILDERS.get(
                                    chain.getThisObject());
                            if (token == null || !"Authorization".equals(key)) {
                                return chain.proceed();
                            }
                            java.util.List<Object> args = new ArrayList<Object>(chain.getArgs());
                            args.set(1, "Bearer " + token);
                            String applied = "ACCOUNT_HEADER_APPLIED generation="
                                    + HostCompat.generationName();
                            log(applied);
                            z13.diagnostic(applied);
                            return chain.proceed(args.toArray(new Object[args.size()]));
                        }
                    });
                    replaced++;
                }
            }
        } catch (Throwable error) {
            log("local API account header hook unavailable " + headerOwner + ": "
                    + safeThrowableMessage(error));
        }
        log("local API multi-account hooks auth=" + marked + " header=" + replaced
                + " generation=" + HostCompat.generationName());
        z13.diagnostic("ROUTING_HOOK_READY auth=" + marked + " header=" + replaced
                + " generation=" + HostCompat.generationName());
    }

    private static boolean isLocalApiExecutionLogContext() {
        return Boolean.TRUE.equals(HookChatPipeline.tlLocalApiRequest.get())
                || tlLocalApiDeadline.get() != null
                || tlLocalApiSink.get() != null;
    }

    static boolean isNoCensorForSession(String sid) {
        if (HookChatPipeline.isNoCensor()) return true;
        return HookChatPipeline.isLocalApiNoCensor() && sid != null && sid.length() > 0
                && isLocalApiInternalSession(sid);
    }

    private static long remainingLocalApiTimeMs() {
        Long deadline = tlLocalApiDeadline.get();
        if (deadline == null) return LOCAL_API_REQUEST_BUDGET_MS;
        return Math.max(0L, deadline.longValue() - System.currentTimeMillis());
    }

    private static void ensureLocalApiClientActive(String stage)
            throws z2.GatewayException {
        z2.DeltaSink sink = tlLocalApiSink.get();
        if (sink != null && sink.isCancelled()) {
            throw new z2.GatewayException(499, "client_closed_request",
                    "server_error", "Client disconnected while " + stage);
        }
    }

    private static void ensureLocalApiTime(String stage)
            throws z2.GatewayException {
        ensureLocalApiClientActive(stage);
        if (remainingLocalApiTimeMs() <= 1000L) {
            throw new z2.GatewayException(504, "request_deadline_exceeded",
                    "server_error", "Local API request deadline exceeded while " + stage);
        }
    }

    private static void sleepLocalApi(long delayMs, String stage)
            throws z2.GatewayException {
        if (delayMs <= 0L) return;
        long remaining = remainingLocalApiTimeMs();
        if (remaining <= delayMs + 1000L) {
            throw new z2.GatewayException(504, "request_deadline_exceeded",
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
                throw new z2.GatewayException(503, "request_interrupted",
                        "server_error", "Interrupted while " + stage);
            }
        }
    }

    public static final class LocalApiPowTask {
        public final CountDownLatch done = new CountDownLatch(1);
        public volatile Thread thread;
        public volatile Object result;
        public volatile Throwable failure;
    }

    static boolean isLocalApiInternalSession(String sid) {
        // History can render before the first API request. Load the durable session index lazily
        // at the visibility boundary so sessions created during an earlier process are hidden on
        // the very first cloud page instead of briefly leaking into the sidebar.
        if (!localApiSessionsLoaded) {
            synchronized (HookAccountLoginSecurity.LOCAL_API_SESSION_LOCK) {
                HookAccountLoginSecurity.loadReusableApiSessionsLocked();
            }
        }
        return HookSessionManagement.isUsableSessionId(sid)
                && (localApiInternalSessionIds.contains(sid)
                || NativeDualChatBridge.isHiddenSession(sid));
    }

    /**
     * DeepSeek's native prompt endpoint can reject a very large inline context even though its
     * file parser accepts the same bytes. The conversation HEAD (everything before the last
     * {@code inlineLimit} characters) goes into one native TXT attachment while the recent tail
     * stays inline; a caller-provided output limit is also respected as an input budget when it
     * is smaller, otherwise the conservative default is 8,000 chars.
     *
     * <p>On legacy hosts the uploaded head is PERSISTED per session+model: resend-style clients
     * submit the same
     * growing transcript every turn, and the conversation prefix is immutable, so the next turns
     * reuse the cached file id without touching the composer again. A new upload happens only
     * when the inline tail outgrows the limit (roughly every 8,000 new characters). This keeps
     * the relay working across turns and process restarts instead of re-uploading and leaving
     * draft residue every request. code257 multi-account routing intentionally uploads a fresh
     * file for every request because its file id is bound to both the routed account and native
     * session, and the process-wide composer invalidates the prior id at the next upload.</p>
     *
     * <p>Every exit path records a reason code on the shared status line so a rejected request
     * can be diagnosed from the advanced status page instead of guessing at DeepSeek's opaque
     * length error. When the relay cannot engage at all, the request fails fast with that
     * reason: sending the oversized inline prompt anyway only guarantees the same upstream
     * rejection the caller is trying to avoid.</p>
     */
    private static int snapContextRelayBoundary(String prompt, int naiveCovered) {
        int lookback = Math.min(4000, naiveCovered - 1);
        if (lookback <= 0) return naiveCovered;
        int limit = naiveCovered - lookback;
        int doubleBreak = prompt.lastIndexOf("\n\n", naiveCovered - 1);
        if (doubleBreak >= limit) return doubleBreak + 2;
        int singleBreak = prompt.lastIndexOf('\n', naiveCovered - 1);
        if (singleBreak >= limit) return singleBreak + 1;
        return naiveCovered;
    }

    public static z2.CompletionRequest prepareOversizedLocalApiPrompt(
            z2.CompletionRequest request, String sid, String sessionKey)
            throws z2.GatewayException {
        if (request == null || request.prompt == null) return request;
        boolean relayEnabled = isLocalApiContextRelayEnabled();
        if (request.prompt.length() > 8000) {
            z13.diagnostic("CONTEXT_RELAY_CHECK id=" + request.requestId
                    + " chars=" + request.prompt.length()
                    + " enabled=" + relayEnabled
                    + " composer=" + (HookAttachmentPipeline.IMAGE_COMPOSER == null ? "missing" : "ready"));
        }
        int inlineLimit = 8000;
        if (request.maxOutputTokens > 0) {
            inlineLimit = Math.max(1024, Math.min(inlineLimit, request.maxOutputTokens));
        }
        if (request.prompt.length() <= inlineLimit) return request;
        z13.ioDiagnostic(request.requestId, "RELAY_INPUT_PROMPT", request.prompt);
        if (!relayEnabled) {
            z13.ioDiagnostic(request.requestId, "RELAY_SKIPPED_DISABLED", request.prompt);
            return request;
        }
        Context context = hostApplicationContext;
        if (context == null || Looper.myLooper() == Looper.getMainLooper()) {
            HookAccountLoginSecurity.lastContextRelayStatus = "main-thread caller, inline fallback";
            z13.diagnostic("CONTEXT_RELAY_DIAG id=" + request.requestId
                    + " chars=" + request.prompt.length() + " reason=main_thread");
            z13.ioDiagnostic(request.requestId, "RELAY_SKIPPED_MAIN_THREAD", request.prompt);
            return request;
        }

        int covered = snapContextRelayBoundary(
                request.prompt, Math.max(1, request.prompt.length() - inlineLimit));
        String cacheKey = contextRelayCacheKey(sessionKey, request.nativeModel);
        if (HostCompat.isV241()) {
            // code257 file ids are native-session scoped. Keep every older host's cache key
            // byte-for-byte unchanged while preventing a recreated code257 session from reusing
            // an id issued to its predecessor.
            cacheKey = cacheKey + "|sid:"
                    + (sid == null || sid.length() == 0 ? "#none" : sid);
        }
        final z12.Route routedContextAccount = HostCompat.isV241()
                && isLocalApiMultiAccountRoutingEnabled()
                ? HookAccountLoginSecurity.tlLocalApiAccountRoute.get() : null;
        final boolean v241RoutedFresh = routedContextAccount != null;
        String headHash = HookAccountLoginSecurity.sha256Hex(request.prompt.substring(0, covered));
        ContextRelayCacheEntry cached = v241RoutedFresh
                ? null : contextRelayCacheLookup(cacheKey);
        boolean prefixMatch = cached != null
                && request.prompt.length() > cached.coveredChars
                && request.prompt.length() - cached.coveredChars <= 24000
                && HookAccountLoginSecurity.sha256Hex(request.prompt.substring(0, cached.coveredChars))
                        .equals(cached.headHash);
        if (cached != null && ((cached.coveredChars == covered && headHash.equals(cached.headHash)) || prefixMatch)) {
            int reuseCovered = prefixMatch ? cached.coveredChars : covered;
            String inline = contextRelayInlinePrompt(request, reuseCovered,
                    cached.fileName);
            HookAccountLoginSecurity.contextRelayCacheHits.incrementAndGet();
            HookAccountLoginSecurity.lastContextRelayStatus = "缓存复用 cache_hit covered=" + reuseCovered;
            z13.diagnostic("CONTEXT_RELAY_CACHE_HIT id=" + request.requestId
                    + " covered=" + reuseCovered + " inline="
                    + (request.prompt.length() - reuseCovered));
            z13.ioDiagnostic(request.requestId, "RELAY_CACHE_HIT_INLINE_PROMPT", inline);
            return request.withPromptFiles(inline,
                    Collections.singletonList(cached.fileId));
        }

        File relay = null;
        String reason = "unknown";
        try {
            File dir = new File(context.getCacheDir(), "ds_local_api_files");
            if (!dir.isDirectory() && !dir.mkdirs()) {
                reason = "cache_dir_unavailable";
            } else {
                String fileName = "context_"
                        + Integer.toHexString(cacheKey.hashCode())
                        + (v241RoutedFresh
                                ? "_" + Integer.toHexString(request.requestId.hashCode()) : "")
                        + ".txt";
                if (v241RoutedFresh) {
                    z13.diagnostic("CONTEXT_RELAY_ROUTED_FRESH id=" + request.requestId
                            + " ns=" + routedContextAccount.sessionNamespace);
                }
                relay = new File(dir, fileName);
                FileOutputStream output = new FileOutputStream(relay, false);
                try {
                    output.write(request.prompt.substring(0, covered).getBytes("UTF-8"));
                    output.flush();
                } finally {
                    output.close();
                }
                // The composer bridge is captured from host constructors and can legitimately be
                // missing when the first request arrives before the chat screen initialized.
                // Recover it from live view models and keep retrying for a bounded window so a
                // freshly-restarted host still gets its long-context TXT attached.
                // code257 can be left on SettingsNestedGraph after configuring the gateway. Its
                // composer is lazy and therefore absent until the native chat route resumes.
                // triggerComposerCreation() now exits that route on the main thread; poll it in
                // short slices instead of blocking one Claude request for the historical 62s.
                // Every older host keeps the established retry schedule byte-for-byte.
                long[] uploadWaits = HostCompat.isV241()
                        ? new long[]{0L, 250L, 350L, 500L, 750L, 1000L, 1500L, 2000L}
                        : new long[]{0L, 2000L, 4000L, 8000L, 12000L, 16000L, 20000L};
                String fileId = null;
                long uploadStartedAtMs = System.currentTimeMillis();
                for (int attempt = 0; attempt < uploadWaits.length; attempt++) {
                    if (attempt > 0) {
                        ensureLocalApiTime("retrying context relay upload");
                        sleepLocalApi(uploadWaits[attempt], "retrying context relay upload");
                    }
                    recoverNativeComposerBridge();
                    String[] attemptReason = {""};
                    fileId = uploadLocalApiContextFile(
                            relay, sid, request.nativeModel, attemptReason);
                    reason = attemptReason[0];
                    z13.ioDiagnostic(request.requestId, "RELAY_UPLOAD_ATTEMPT",
                            "attempt=" + attempt + " reason=" + reason
                                    + " ok=" + (fileId != null && fileId.length() > 0)
                                    + " elapsed_ms=" + (System.currentTimeMillis() - uploadStartedAtMs));
                    if (fileId != null && fileId.length() > 0) break;
                    if (attempt + 1 >= uploadWaits.length) break;
                    if (!"composer_missing".equals(reason)
                            && !"upload_timeout".equals(reason)
                            && !"draft_present".equals(reason)) break;
                }
                if (fileId == null || fileId.length() == 0) {
                    HookAccountLoginSecurity.contextRelayFailed.incrementAndGet();
                    HookAccountLoginSecurity.lastContextRelayStatus = "降级 inline (" + reason + ")";
                    z13.diagnostic("CONTEXT_RELAY_DEGRADED id=" + request.requestId
                            + " chars=" + request.prompt.length() + " reason=" + reason);
                    z13.ioDiagnostic(request.requestId, "RELAY_CONTEXT_OMITTED_HEAD",
                            "reason=" + reason + " total_elapsed_ms="
                                    + (System.currentTimeMillis() - uploadStartedAtMs)
                                    + "\n" + request.prompt.substring(0, covered));
                    if (v241RoutedFresh) {
                        throw new z2.GatewayException(502, "context_relay_failed",
                                "server_error",
                                "The code257 overflow TXT could not be attached (" + reason
                                        + "); the request was stopped without truncating context");
                    }
                    return request.withPrompt(request.prompt.substring(covered)
                            + "\n\n[The first " + covered
                            + " characters of this conversation were omitted because the "
                            + "overflow file could not be attached (" + reason
                            + "). Continue from the visible text above without omitting "
                            + "content.]");
                }
                String inline = contextRelayInlinePrompt(request, covered, fileName);
                if (!v241RoutedFresh) {
                    contextRelayCachePut(cacheKey, new ContextRelayCacheEntry(
                            fileId, headHash, covered, fileName,
                            System.currentTimeMillis()));
                }
                HookAccountLoginSecurity.contextRelayOk.incrementAndGet();
                HookAccountLoginSecurity.lastContextRelayStatus = "成功 ok (covered=" + covered
                        + " inline=" + (request.prompt.length() - covered) + ")";
                z13.diagnostic("CONTEXT_RELAY_OK id=" + request.requestId
                        + " covered_chars=" + covered + " inline_chars="
                        + (request.prompt.length() - covered));
                z13.ioDiagnostic(request.requestId, "RELAY_UPLOADED_HEAD",
                        request.prompt.substring(0, covered));
                z13.ioDiagnostic(request.requestId, "RELAY_FINAL_INLINE_PROMPT", inline);
                return request.withPromptFiles(inline,
                        Collections.singletonList(fileId));
            }
        } catch (z2.GatewayException error) {
            throw error;
        } catch (Throwable error) {
            reason = "io_failed: " + safeThrowableMessage(error);
            log("local API context relay failed: " + reason);
        } finally {
            if (relay != null) try { relay.delete(); } catch (Throwable ignored) {}
        }
        HookAccountLoginSecurity.contextRelayFailed.incrementAndGet();
        HookAccountLoginSecurity.lastContextRelayStatus = "降级 inline (" + reason + ")";
        z13.diagnostic("CONTEXT_RELAY_DEGRADED id=" + request.requestId
                + " chars=" + request.prompt.length() + " reason=" + reason);
        z13.ioDiagnostic(request.requestId, "RELAY_CONTEXT_OMITTED_HEAD",
                "reason=" + reason + "\n" + request.prompt.substring(0, covered));
        if (v241RoutedFresh) {
            throw new z2.GatewayException(502, "context_relay_failed", "server_error",
                    "The code257 overflow TXT could not be staged (" + reason
                            + "); the request was stopped without truncating context");
        }
        return request.withPrompt(request.prompt.substring(covered)
                + "\n\n[The first " + covered
                + " characters of this conversation were omitted because the overflow file "
                + "could not be staged (" + reason + "). Continue from the visible text above "
                + "without omitting content.]");
    }

    private static String contextRelayInlinePrompt(
            z2.CompletionRequest request, int covered, String fileName) {
        String suffix = request.prompt.substring(Math.max(0, covered));
        if (!HostCompat.isV241()) {
            return suffix + "\n\n[The first " + covered
                    + " characters of this conversation are attached as " + fileName
                    + ". Read that file first, then continue with the text above without "
                    + "omitting content.]";
        }
        // Claude Code appends a large tool catalog after the conversation. A simple tail split
        // therefore puts the current user turn only in the attachment, where the model can treat
        // it as reference material instead of the instruction to answer. Duplicate a bounded
        // recent conversation tail inline, while the attachment remains the authoritative full
        // prefix and the original prompt suffix keeps the tool protocol intact.
        //
        // Some calling apps (e.g. MT Manager AI assistant) embed "Generate only the next
        // assistant turn." at the very end of their basePrompt, immediately before our
        // [TOOL USE INSTRUCTIONS] block. Reading that instruction first causes the model to
        // produce text and ignore the tool requirement. Fix: hoist [TOOL USE INSTRUCTIONS]
        // to the top of the inline prompt so it is read before any app-level prose directive.
        String base = request.basePrompt == null ? "" : request.basePrompt;
        int recentStart = Math.max(0, base.length() - 16000);
        String recent = base.substring(recentStart);
        int toolInsStart = suffix.lastIndexOf("[TOOL USE INSTRUCTIONS]");
        String hoisted = toolInsStart >= 0 ? suffix.substring(toolInsStart) + "\n\n" : "";
        return hoisted
                + "[RECENT CONVERSATION — answer the final user message below]\n"
                + recent
                + "\n[/RECENT CONVERSATION]\n\n"
                + suffix
                + "\n\n[Earlier context and complete instructions are attached as " + fileName
                + ". Use that file as prior context. The RECENT CONVERSATION block above "
                + "contains the current turn.]";
    }

    /** Persisted per-session+model relay file record. */
    private static final class ContextRelayCacheEntry {
        final String fileId;
        final String headHash;
        final int coveredChars;
        final String fileName;
        final long updatedAt;

        ContextRelayCacheEntry(String fileId, String headHash, int coveredChars,
                               String fileName, long updatedAt) {
            this.fileId = fileId;
            this.headHash = headHash;
            this.coveredChars = coveredChars;
            this.fileName = fileName;
            this.updatedAt = updatedAt;
        }
    }

    private static final Object CONTEXT_RELAY_CACHE_LOCK = new Object();

    private static final ConcurrentHashMap<String, ContextRelayCacheEntry>
            CONTEXT_RELAY_CACHE = new ConcurrentHashMap<>();

    private static volatile boolean contextRelayCacheLoaded;

    private static volatile String contextRelayCacheJson = "{}";

    static volatile Object NATIVE_SESSION_LIST;
    static volatile Object NATIVE_SESSION_EVENTS;
    static volatile HashSet<String> LOCAL_SESSION_IDS;

    static int preserveEditorLocalNativeSessions(List state, HashSet<String> localIds) {
        return HookSessionManagement.preserveEditorLocalNativeSessions(state, localIds);
    }

    static List copyListForHook(List source, Class<?> parameterType) {
        return HookSessionManagement.copyListForHook(source, parameterType);
    }

    public static String contextRelayCacheKey(String sessionKey, String model) {
        return (sessionKey == null || sessionKey.length() == 0 ? "#none" : sessionKey)
                + "|" + (model == null || model.length() == 0 ? "default" : model);
    }

    public static ContextRelayCacheEntry contextRelayCacheLookup(String key) {
        if (!contextRelayCacheLoaded) loadContextRelayCache();
        return CONTEXT_RELAY_CACHE.get(key);
    }

    public static void contextRelayCachePut(String key, ContextRelayCacheEntry entry) {
        if (!contextRelayCacheLoaded) loadContextRelayCache();
        CONTEXT_RELAY_CACHE.put(key, entry);
        while (CONTEXT_RELAY_CACHE.size() > 16) {
            String oldestKey = null;
            long oldestAt = Long.MAX_VALUE;
            for (Map.Entry<String, ContextRelayCacheEntry> item
                    : CONTEXT_RELAY_CACHE.entrySet()) {
                if (item.getValue().updatedAt < oldestAt) {
                    oldestAt = item.getValue().updatedAt;
                    oldestKey = item.getKey();
                }
            }
            if (oldestKey != null) CONTEXT_RELAY_CACHE.remove(oldestKey);
        }
        persistContextRelayCache();
    }

    private static void loadContextRelayCache() {
        synchronized (CONTEXT_RELAY_CACHE_LOCK) {
            if (contextRelayCacheLoaded) return;
            contextRelayCacheLoaded = true;
            Context context = hostApplicationContext;
            if (context == null) return;
            // code257 file_ref_ids are tied to the current native upload/session lifetime.
            // Reusing an id persisted by the previous app process produces
            // file_ref_ids_invalid. Keep same-process reuse, but force a fresh upload after
            // restart. Older host branches retain their established persisted-cache behavior.
            if (HostCompat.isV241()) {
                contextRelayCacheJson = "{}";
                return;
            }
            try {
                File file = new File(context.getFilesDir(),
                        "dq0_context_files.json");
                String text = readSmallText(file.getAbsolutePath());
                if (text == null || text.trim().length() == 0) return;
                contextRelayCacheJson = text;
                JSONObject root = new JSONObject(text);
                JSONObject entries = root.optJSONObject("entries");
                if (entries == null) return;
                java.util.Iterator<String> keys = entries.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject value = entries.optJSONObject(key);
                    if (value == null) continue;
                    String fileId = value.optString("id", "").trim();
                    String headHash = value.optString("h", "").trim();
                    int covered = value.optInt("c", 0);
                    String fileName = value.optString("n", "").trim();
                    long updatedAt = value.optLong("t", 0L);
                    if (fileId.length() == 0 || headHash.length() == 0
                            || covered <= 0) continue;
                    CONTEXT_RELAY_CACHE.put(key, new ContextRelayCacheEntry(
                            fileId, headHash, covered, fileName, updatedAt));
                }
                log("local API context relay cache loaded entries="
                        + CONTEXT_RELAY_CACHE.size());
            } catch (Throwable error) {
                log("local API context relay cache load failed: "
                        + safeThrowableMessage(error));
            }
        }
    }

    private static void persistContextRelayCache() {
        synchronized (CONTEXT_RELAY_CACHE_LOCK) {
            Context context = hostApplicationContext;
            if (context == null) return;
            try {
                JSONObject root = new JSONObject();
                JSONObject entries = new JSONObject();
                for (Map.Entry<String, ContextRelayCacheEntry> item
                        : CONTEXT_RELAY_CACHE.entrySet()) {
                    ContextRelayCacheEntry entry = item.getValue();
                    entries.put(item.getKey(), new JSONObject()
                            .put("id", entry.fileId)
                            .put("h", entry.headHash)
                            .put("c", entry.coveredChars)
                            .put("n", entry.fileName)
                            .put("t", entry.updatedAt));
                }
                root.put("v", 1).put("entries", entries);
                contextRelayCacheJson = root.toString();
                FileOutputStream output = new FileOutputStream(
                        new File(context.getFilesDir(),
                                "dq0_context_files.json"), false);
                try {
                    output.write(contextRelayCacheJson.getBytes(StandardCharsets.UTF_8));
                    output.flush();
                } finally {
                    output.close();
                }
            } catch (Throwable error) {
                log("local API context relay cache save failed: "
                        + safeThrowableMessage(error));
            }
        }
    }

    /**
     * Recovers the host composer/uploader bridge when the constructor hooks fired before the
     * module loaded (first-run attachment case). Live chat view models expose the same composer
     * instance the constructor hook would have captured, so the relay does not stay dead for the
     * whole process lifetime just because the first capture was missed.
     */
    private static void recoverNativeComposerBridge() {
        if (HookAttachmentPipeline.IMAGE_COMPOSER != null && HookAttachmentPipeline.IMAGE_HOST_CL != null) return;
        try {
            for (Map.Entry<String, WeakReference<Object>> entry
                    : HookSessionManagement.ACTIVE_CHAT_VIEW_MODELS.entrySet()) {
                WeakReference<Object> reference = entry.getValue();
                Object viewModel = reference == null ? null : reference.get();
                if (viewModel == null) continue;
                Object composer = invokeNoArg(viewModel, "K");
                if (composer == null) continue;
                HookAttachmentPipeline.IMAGE_COMPOSER = composer;
                try {
                    Object repository = readHostField(composer, "c");
                    Object api = readHostField(repository, "d");
                    if (api != null) HookAttachmentPipeline.IMAGE_FILE_API = api;
                } catch (Throwable ignored) {}
                if (HookAttachmentPipeline.IMAGE_HOST_CL == null) {
                    HookAttachmentPipeline.IMAGE_HOST_CL = composer.getClass().getClassLoader();
                }
                log("local API composer bridge recovered from a live view model");
                break;
            }
        } catch (Throwable ignored) {}
        // The composer (n51) is created lazily only when a chat screen loads.  When the local API
        // runs in the background with no open chat, launch the host activity once so the DI
        // constructs the composer and the relay can upload its overflow TXT on the next retry.
        if (HookAttachmentPipeline.IMAGE_COMPOSER == null) {
            triggerComposerCreation();
        }
    }

    private static void triggerComposerCreation() {
        // Re-trigger on a cooldown instead of once-per-process: a freshly-restarted host may
        // need several launches before a chat screen actually loads and constructs the composer.
        long now = System.currentTimeMillis();
        if (composerTriggerAt != 0 && now - composerTriggerAt < 30000L) return;
        composerTriggerAt = now;
        Context context = hostApplicationContext;
        if (context == null) return;
        if (HostCompat.isV241() && requestV241ChatRouteForComposer()) {
            log("code257 local API composer trigger requested native chat route");
            return;
        }
        try {
            Intent intent = new Intent();
            intent.setClassName("com.deepseek.chat", "com.deepseek.chat.MainActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(intent);
            log("local API composer trigger launched");
        } catch (Throwable t) {
            log("local API composer trigger failed: " + safeThrowableMessage(t));
        }
    }

    /**
     * code257 does not construct g71 while SettingsNestedGraph covers the chat destination.
     * Resume the already-existing native back stack instead of launching another MainActivity
     * instance that immediately restores the same settings route. This adapter is intentionally
     * exact-version: older hosts retain their previous activity-launch recovery path.
     */
    private static boolean requestV241ChatRouteForComposer() {
        if (!HostCompat.isV241()) return false;
        final Main module = MODULE;
        final Object nav = module == null ? null : module.navController.get();
        final Handler handler = currentMainHandler();
        if (module == null || nav == null || handler == null) return false;
        final String route = currentRoute(nav);
        if (route == null || !isSettingsRootRouteName(route)) return false;
        handler.post(new Runnable() {
             public void run() {
                exitV241SettingsForComposer(nav, handler, 0);
            }
        });
        return true;
    }

    private static void exitV241SettingsForComposer(final Object nav,
                                                     final Handler handler,
                                                     final int attempt) {
        if (!HostCompat.isV241() || nav == null || attempt >= 8
                || HookAttachmentPipeline.IMAGE_COMPOSER != null) return;
        String route = currentRoute(nav);
        if (route == null || !isSettingsRootRouteName(route)) {
            z13.diagnostic("V241_COMPOSER_CHAT_ROUTE_READY attempt=" + attempt
                    + " route=" + (route == null ? "unknown" : route));
            return;
        }
        if (!invokeNavPop(nav)) {
            z13.diagnostic("V241_COMPOSER_CHAT_ROUTE_POP_FAILED attempt=" + attempt);
            return;
        }
        handler.postDelayed(new Runnable() {
             public void run() {
                exitV241SettingsForComposer(nav, handler, attempt + 1);
            }
        }, 100L);
    }

    /** Uploads validated API image parts through the same native attachment path as DeepSeek UI. */
    public static z2.CompletionRequest prepareLocalApiInputImages(
            z2.CompletionRequest request, String sid)
            throws z2.GatewayException {
        if (request == null || request.inputImages == null || request.inputImages.isEmpty()) {
            log("local API prepareLocalApiInputImages: inputImages empty, skipping upload");
            return request;
        }
        Context context = HookSessionManagement.currentHostContext();
        if (context == null) {
            throw new z2.GatewayException(503, "image_upload_unavailable",
                    "server_error", "DeepSeek native image uploader is not ready");
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new z2.GatewayException(503, "image_upload_unavailable",
                    "server_error", "Image upload cannot run on the main thread");
        }
        recoverNativeComposerBridge();
        if (HookAttachmentPipeline.IMAGE_COMPOSER == null) {
            // The composer is created only when the host app navigates to a chat screen.
            // Launch the main activity so the composer materialises; the local API may be
            // receiving an image request while the app is on the settings page or background.
            triggerComposerCreation();
            for (int attempt = 0; attempt < 16 && HookAttachmentPipeline.IMAGE_COMPOSER == null; attempt++) {
                sleepLocalApi(attempt == 0 ? 1500L : 2000L, "waiting for native composer bridge");
                recoverNativeComposerBridge();
            }
        }
        if (HookAttachmentPipeline.IMAGE_COMPOSER == null) {
            throw new z2.GatewayException(503, "image_upload_unavailable",
                    "server_error", "DeepSeek native image uploader is not ready; "
                            + "open a chat screen and retry");
        }
        File dir = new File(context.getCacheDir(), "ds_local_api_files");
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new z2.GatewayException(500, "image_cache_unavailable",
                    "server_error", "Could not create the temporary image directory");
        }
        ArrayList<String> uploaded = new ArrayList<String>();
        int index = 0;
        for (z4.Attachment image : request.inputImages) {
            index++;
            File temporary = new File(dir, "api_"
                    + request.requestId.replaceAll("[^A-Za-z0-9._-]", "_")
                    + "_" + index + "_" + image.fileName);
            try {
                FileOutputStream output = new FileOutputStream(temporary, false);
                try {
                    output.write(image.bytes);
                    output.flush();
                } finally {
                    output.close();
                }
                ensureLocalApiTime("uploading image " + index);
                String fileId;
                if (HostCompat.isV241()) {
                    tlV241LocalApiImageUpload.set(Boolean.TRUE);
                    try {
                        fileId = uploadLocalApiContextFile(
                                temporary, sid, request.nativeModel);
                    } finally {
                        tlV241LocalApiImageUpload.remove();
                    }
                } else if (HostCompat.isV236()) {
                    tlV236LocalApiImageUpload.set(Boolean.TRUE);
                    try {
                        fileId = uploadLocalApiContextFile(
                                temporary, sid, request.nativeModel);
                    } finally {
                        tlV236LocalApiImageUpload.remove();
                    }
                } else {
                    fileId = uploadLocalApiContextFile(
                            temporary, sid, request.nativeModel);
                }
                if (fileId == null || fileId.length() == 0) {
                    throw new z2.GatewayException(503,
                            "image_upload_failed", "server_error",
                            "DeepSeek could not upload image " + index);
                }
                uploaded.add(fileId);
            } catch (z2.GatewayException error) {
                throw error;
            } catch (Throwable error) {
                throw new z2.GatewayException(503,
                        "image_upload_failed", "server_error",
                        "Image " + index + " upload failed: "
                                + safeThrowableMessage(error));
            } finally {
                try { temporary.delete(); } catch (Throwable ignored) {}
            }
        }
        z13.diagnostic("IMAGE_INPUT_OK id=" + request.requestId
                + " count=" + uploaded.size());
        log("local API image upload complete id=" + request.requestId
                + " count=" + uploaded.size()
                + " fileIds=" + uploaded.toString()
                + " nativeModel=" + request.nativeModel);
        return request.withUploadedImages(uploaded);
    }

    /** Uploads one private temporary TXT through the host's real composer and removes only the
     * temporary draft item afterwards. Existing user attachments are never touched. */
    public static String uploadLocalApiContextFile(
            final File file, final String sid, final String targetModel) throws Exception {
        return uploadLocalApiContextFile(file, sid, targetModel, new String[]{""});
    }

    /** Reason-coded variant: every silent exit records why the relay gave up so the request can
     * report the real failure instead of DeepSeek's opaque length rejection. */
    private static String uploadLocalApiContextFile(
            final File file, final String sid, final String targetModel,
            final String[] reasonOut) throws Exception {
        if (file == null || !file.isFile()) {
            reasonOut[0] = "file_missing";
            return null;
        }
        final Object composer = HookAttachmentPipeline.IMAGE_COMPOSER;
        final ClassLoader classLoader = HookAttachmentPipeline.IMAGE_HOST_CL;
        if (composer == null || classLoader == null) {
            reasonOut[0] = "composer_missing";
            return null;
        }
        Object before = callOnMainThread(new java.util.concurrent.Callable<Object>() {
            @Override public Object call() { return snapshotComposerAttachments(composer); }
        });
        if (before instanceof List && !((List) before).isEmpty()) {
            if (removeStaleRelayAttachments(composer, (List) before)) {
                Object after = callOnMainThread(new java.util.concurrent.Callable<Object>() {
                    @Override public Object call() {
                        return snapshotComposerAttachments(composer);
                    }
                });
                if (after instanceof List && ((List) after).isEmpty()) {
                    log("local API context relay cleared stale module attachments");
                } else {
                    log("local API context relay deferred: native composer contains a user draft");
                    reasonOut[0] = "draft_present";
                    return null;
                }
            } else {
                log("local API context relay deferred: native composer contains a user draft");
                reasonOut[0] = "draft_present";
                return null;
            }
        }
        final String fileName = file.getName();
        final boolean v241Image = HostCompat.isV241()
                && Boolean.TRUE.equals(tlV241LocalApiImageUpload.get());
        final boolean v236Image = HostCompat.isV236()
                && Boolean.TRUE.equals(tlV236LocalApiImageUpload.get());
        // Real code257 images are already pinned to the process account. Only a synthetic TXT
        // reaches this branch with a selected imported route; seed g71.t while it constructs the
        // mapped rz upload coroutine, whose later resumes are handled by the code257 hook above.
        final z12.Route v241ContextUploadRoute = HostCompat.isV241() && !v241Image
                ? HookAccountLoginSecurity.tlLocalApiAccountRoute.get() : null;
        final Object metadata = v241Image || v236Image
                ? createNativeScreenshotMetadata(composer, file, null, fileName)
                : createNativeFileMetadata(composer, file, fileName);
        if (metadata == null) {
            reasonOut[0] = "metadata_failed";
            return null;
        }
        final String uploadModel = String.valueOf(callOnMainThread(
                new java.util.concurrent.Callable<Object>() {
                    @Override public Object call() { return invokeNoArg(composer, "d"); }
                }));
        ACTIVE_HIDDEN_ATTACHMENT_NAMES.add(fileName);
        if (v241ContextUploadRoute != null) {
            // b90 caches authed file-PoW solutions only by endpoint, not by account. A cached
            // process-account solution must never be reused after routing the upload bearer.
            try {
                Object repository = readHostField(composer, "c");
                Object fileApi = readHostField(repository, "d");
                Object filePow = readHostField(fileApi, "c");
                Object cache = readHostField(filePow, "c");
                if (cache instanceof Map) {
                    ((Map<?, ?>) cache).remove("/api/v0/file/upload_file");
                    z13.diagnostic("V241_FILE_POW_CACHE_CLEARED ns="
                            + v241ContextUploadRoute.sessionNamespace);
                }
            } catch (Throwable ignored) {}
            HookAccountLoginSecurity.V241_ACTIVE_ROUTED_CONTEXT_UPLOAD = v241ContextUploadRoute;
        }
        Object nativeAttachment = null;
        try {
            Object started = callOnMainThread(new java.util.concurrent.Callable<Object>() {
                @Override public Object call() {
                    z12.Route previous = HookAccountLoginSecurity.tlLocalApiAccountRoute.get();
                    if (v241ContextUploadRoute != null) {
                        HookAccountLoginSecurity.tlLocalApiAccountRoute.set(v241ContextUploadRoute);
                    }
                    try {
                        return Boolean.valueOf(invokeNativeScreenshotUploader(
                                composer, metadata, sid == null ? "" : sid));
                    } finally {
                        if (previous == null) HookAccountLoginSecurity.tlLocalApiAccountRoute.remove();
                        else HookAccountLoginSecurity.tlLocalApiAccountRoute.set(previous);
                    }
                }
            });
            if (!Boolean.TRUE.equals(started)) {
                reasonOut[0] = "upload_start_failed";
                return null;
            }
            long deadline = SystemClock.elapsedRealtime() + 60000L;
            while (SystemClock.elapsedRealtime() < deadline) {
                final Object attachments = callOnMainThread(
                        new java.util.concurrent.Callable<Object>() {
                            @Override public Object call() {
                                return snapshotComposerAttachments(composer);
                            }
                        });
                nativeAttachment = findNativeAttachment(attachments, fileName);
                String remote = nativeAttachmentRemoteId(nativeAttachment, uploadModel);
                if (remote.length() > 0) {
                    if (HostCompat.isV241()) {
                        z13.diagnostic("V241_ATTACHMENT_ID_READY file=" + fileName
                                + " source_model=" + uploadModel
                                + " target_model=" + targetModel
                                + " id_len=" + remote.length());
                    }
                    if (HostCompat.isV241()
                            && targetModel != null && targetModel.length() > 0
                            && !targetModel.equals(uploadModel)) {
                        String converted = convertV241LocalApiImageModel(
                                composer, nativeAttachment, uploadModel, targetModel);
                        if (converted == null || converted.length() == 0) {
                            reasonOut[0] = v241Image
                                    ? "image_model_conversion_failed"
                                    : "file_model_conversion_failed";
                            return null;
                        }
                        return converted;
                    }
                    if (HostCompat.isV236()
                            && Boolean.TRUE.equals(tlV236LocalApiImageUpload.get())
                            && targetModel != null && targetModel.length() > 0
                            && !targetModel.equals(uploadModel)) {
                        String converted = convertV236LocalApiImageModel(
                                composer, nativeAttachment, uploadModel, targetModel);
                        if (converted == null || converted.length() == 0) {
                            reasonOut[0] = "image_model_conversion_failed";
                            return null;
                        }
                        return converted;
                    }
                    // Same-model ids can be sent directly. code257 cross-model ids were converted
                    // above for both ordinary files and images.
                    return remote;
                }
                Thread.sleep(200L);
            }
            reasonOut[0] = "upload_timeout";
            return null;
        } finally {
            if (v241ContextUploadRoute != null
                    && HookAccountLoginSecurity.V241_ACTIVE_ROUTED_CONTEXT_UPLOAD == v241ContextUploadRoute) {
                HookAccountLoginSecurity.V241_ACTIVE_ROUTED_CONTEXT_UPLOAD = null;
            }
            if (HostCompat.isV241()) {
                // code257's composer removal is not merely visual: f(w11) cancels/releases the
                // uploaded remote file. The completion request is constructed only after this
                // method returns, so removing here made every freshly uploaded id invalid before
                // transport saw it. Keep this module-owned draft hidden and alive for the current
                // generation; removeStaleRelayAttachments clears it at the next upload boundary.
                log("code257 local API attachment retained until next request file=" + fileName);
            } else {
            // The uploader mutates the real host composer asynchronously. Keep the temporary
            // filename hidden until the removal has actually propagated; otherwise the next
            // blank chat can render the stale file chip (and sometimes its opaque local id).
            Object removable = nativeAttachment;
            if (removable == null) {
                try {
                    Object current = callOnMainThread(
                            new java.util.concurrent.Callable<Object>() {
                                @Override public Object call() {
                                    return snapshotComposerAttachments(composer);
                                }
                            });
                    removable = findNativeAttachment(current, fileName);
                } catch (Throwable ignored) {}
            }
            removeNativeComposerAttachment(composer, removable);
            for (int attempt = 0; attempt < 12; attempt++) {
                try {
                    Object current = callOnMainThread(
                            new java.util.concurrent.Callable<Object>() {
                                @Override public Object call() {
                                    return snapshotComposerAttachments(composer);
                                }
                            });
                    if (findNativeAttachment(current, fileName) == null) break;
                    Thread.sleep(100L);
                } catch (Throwable ignored) { break; }
            }
            ACTIVE_HIDDEN_ATTACHMENT_NAMES.remove(fileName);
            }
        }
    }

    /**
     * code257 stores attachment credentials per native model. Reuse the host's own model-switch
     * coroutine (g71.a) and return only the target model's id for both images and ordinary files.
     * This is never called for a pre-code257 host.
     */
    private static String convertV241LocalApiImageModel(
            final Object composer, final Object attachment,
            final String sourceModel, final String targetModel) {
        if (!HostCompat.isV241() || composer == null || attachment == null
                || sourceModel == null || targetModel == null
                || sourceModel.equals(targetModel)) return null;
        final ClassLoader cl = HookAttachmentPipeline.IMAGE_HOST_CL;
        final Object api = HookAttachmentPipeline.IMAGE_FILE_API;
        if (cl == null || api == null) return null;
        try {
            Method candidate = null;
            for (Method method : composer.getClass().getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!"a".equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())
                        || p.length != 5 || !p[0].isInstance(composer)
                        || !p[1].isInstance(attachment)
                        || p[2] != String.class || p[3] != String.class) continue;
                candidate = method;
                break;
            }
            if (candidate == null) return null;
            candidate.setAccessible(true);
            final Method convert = candidate;
            Class<?> blockClass = HostCompat.load(cl, "mb3");
            Object block = Proxy.newProxyInstance(cl, new Class<?>[]{blockClass},
                    new InvocationHandler() {
                        @Override public Object invoke(
                                Object proxy, Method method, Object[] args) throws Throwable {
                            if (isObjectMethod(method)) return objectMethod(proxy, method, args);
                            Object continuation = args == null || args.length == 0
                                    ? null : args[args.length - 1];
                            try {
                                return convert.invoke(null, composer, attachment,
                                        sourceModel, targetModel, continuation);
                            } catch (java.lang.reflect.InvocationTargetException error) {
                                throw error.getCause() == null ? error : error.getCause();
                            }
                        }
                    });
            // A routed TXT belongs to the imported account selected before session creation.
            // The model-conversion call builds its own request inside runHostCoroutine; restore
            // that Route while the request/header builder is created. Real composer images have
            // no routed marker and retain their original process-account path.
            final z12.Route conversionRoute = HookAccountLoginSecurity.V241_ACTIVE_ROUTED_CONTEXT_UPLOAD;
            z12.Route previousRoute = HookAccountLoginSecurity.tlLocalApiAccountRoute.get();
            if (conversionRoute != null) {
                HookAccountLoginSecurity.tlLocalApiAccountRoute.set(conversionRoute);
                z13.diagnostic("V241_FILE_MODEL_CONVERSION_BEGIN source=" + sourceModel
                        + " target=" + targetModel
                        + " ns=" + conversionRoute.sessionNamespace);
            }
            try {
                runHostCoroutine(cl, api, block);
            } finally {
                if (previousRoute == null) HookAccountLoginSecurity.tlLocalApiAccountRoute.remove();
                else HookAccountLoginSecurity.tlLocalApiAccountRoute.set(previousRoute);
            }
            // Routed Local-API conversions normally update the attachment immediately. Avoid the
            // historical 50-second image wait when an account binding cannot produce a target id.
            long conversionWaitMs = conversionRoute == null ? 50000L : 8000L;
            long deadline = SystemClock.elapsedRealtime() + conversionWaitMs;
            while (SystemClock.elapsedRealtime() < deadline) {
                String targetId = nativeAttachmentRemoteId(attachment, targetModel);
                if (targetId.length() > 0) {
                    if (conversionRoute != null) {
                        z13.diagnostic("V241_FILE_MODEL_CONVERSION_OK source=" + sourceModel
                                + " target=" + targetModel
                                + " ns=" + conversionRoute.sessionNamespace);
                    }
                    return targetId;
                }
                Thread.sleep(200L);
            }
            if (conversionRoute != null) {
                z13.diagnostic("V241_FILE_MODEL_CONVERSION_TIMEOUT source=" + sourceModel
                        + " target=" + targetModel
                        + " wait_ms=" + conversionWaitMs
                        + " ns=" + conversionRoute.sessionNamespace);
            }
        } catch (Throwable error) {
            log("code257 Local API image model conversion failed: "
                    + safeThrowableMessage(error));
        }
        return null;
    }

    /**
     * code249 keeps the same per-model attachment contract behind n51.a. This adapter is
     * intentionally separate from code257's g71 path so a future signature change in either host
     * cannot leak across version branches.
     */
    private static String convertV236LocalApiImageModel(
            final Object composer, final Object attachment,
            final String sourceModel, final String targetModel) {
        if (!HostCompat.isV236() || composer == null || attachment == null
                || sourceModel == null || targetModel == null
                || sourceModel.equals(targetModel)) return null;
        final ClassLoader cl = HookAttachmentPipeline.IMAGE_HOST_CL;
        final Object api = HookAttachmentPipeline.IMAGE_FILE_API;
        if (cl == null || api == null) return null;
        try {
            Method candidate = null;
            for (Method method : composer.getClass().getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!"a".equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())
                        || p.length != 5 || !p[0].isInstance(composer)
                        || !p[1].isInstance(attachment)
                        || p[2] != String.class || p[3] != String.class) continue;
                candidate = method;
                break;
            }
            if (candidate == null) return null;
            candidate.setAccessible(true);
            final Method convert = candidate;
            Class<?> blockClass = HostCompat.load(cl, "mb3");
            Object block = Proxy.newProxyInstance(cl, new Class<?>[]{blockClass},
                    new InvocationHandler() {
                        @Override public Object invoke(
                                Object proxy, Method method, Object[] args) throws Throwable {
                            if (isObjectMethod(method)) return objectMethod(proxy, method, args);
                            Object continuation = args == null || args.length == 0
                                    ? null : args[args.length - 1];
                            try {
                                return convert.invoke(null, composer, attachment,
                                        sourceModel, targetModel, continuation);
                            } catch (java.lang.reflect.InvocationTargetException error) {
                                throw error.getCause() == null ? error : error.getCause();
                            }
                        }
                    });
            runHostCoroutine(cl, api, block);
            long deadline = SystemClock.elapsedRealtime() + 50000L;
            while (SystemClock.elapsedRealtime() < deadline) {
                String targetId = nativeAttachmentRemoteId(attachment, targetModel);
                if (targetId.length() > 0) return targetId;
                Thread.sleep(200L);
            }
        } catch (Throwable error) {
            log("code249 Local API image model conversion failed: "
                    + safeThrowableMessage(error));
        }
        return null;
    }

    /**
     * Removes draft items left behind by a previous relay upload. Returns true only when every
     * item in the draft is a module-created relay file (a later request can then reuse the
     * composer); a genuine user attachment makes this return false and the relay defers instead.
     */
    private static boolean removeStaleRelayAttachments(Object composer, List<?> attachments) {
        if (attachments == null || attachments.isEmpty()) return true;
        List<Object> moduleOwned = new ArrayList<Object>();
        for (Object item : attachments) {
            if (item == null) continue;
            String name = String.valueOf(readHostField(item, "a"));
            boolean ours = name.startsWith("context_")
                    || ACTIVE_HIDDEN_ATTACHMENT_NAMES.contains(name);
            if (!ours) {
                log("local API context relay kept a user attachment: " + name);
                return false;
            }
            moduleOwned.add(item);
        }
        for (Object item : moduleOwned) {
            removeNativeComposerAttachment(composer, item);
        }
        return true;
    }

    /**
     * Removes only module-owned code257 context relay items after their native completion has
     * ended. Removing an item earlier invalidates its remote id; leaving it until the next API
     * call exposes a synthetic TXT chip in the real chat composer. This exact code257 cleanup is
     * invoked while z18 still owns the process-wide context lease, so a following upload cannot
     * race with it. User attachments are identified independently and are never removed.
     */
    public static void cleanupV241LocalApiContextDraft() {
        if (!HostCompat.isV241()) return;
        final Object composer = HookAttachmentPipeline.IMAGE_COMPOSER;
        if (composer == null) return;
        try {
            Object raw = callOnMainThread(new java.util.concurrent.Callable<Object>() {
                @Override public Object call() {
                    return snapshotComposerAttachments(composer);
                }
            });
            if (!(raw instanceof List)) return;
            List<?> attachments = new ArrayList<Object>((List<?>) raw);
            int removed = 0;
            for (Object item : attachments) {
                if (item == null) continue;
                Object rawName = readHostField(item, "a");
                String name = rawName instanceof String ? (String) rawName : "";
                if (!name.startsWith("context_")
                        || !ACTIVE_HIDDEN_ATTACHMENT_NAMES.contains(name)) continue;
                removeNativeComposerAttachment(composer, item);
                ACTIVE_HIDDEN_ATTACHMENT_NAMES.remove(name);
                removed++;
            }
            if (removed > 0) {
                z13.diagnostic("V241_CONTEXT_DRAFT_CLEANED count=" + removed);
            }
        } catch (Throwable error) {
            log("code257 Local API context draft cleanup failed: "
                    + safeThrowableMessage(error));
        }
    }

    private static void removeNativeComposerAttachment(Object composer, Object attachment) {
        if (composer == null || attachment == null) return;
        // The host composer removes a draft item through its item-typed f() method, which also
        // cancels the item's upload job and drops its per-model id maps. The legacy String-typed
        // path is a TEXT setter on current host generations: invoking it polluted the draft with
        // a file id instead of removing anything, which is why residue kept blocking the relay.
        for (Class<?> owner = composer.getClass(); owner != null;
                owner = owner.getSuperclass()) {
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"f".equals(method.getName()) || types.length != 1
                        || method.getReturnType() != void.class
                        || !types[0].isInstance(attachment)) continue;
                try {
                    final Method removal = method;
                    callOnMainThread(new java.util.concurrent.Callable<Object>() {
                        @Override public Object call() throws Exception {
                            removal.setAccessible(true);
                            removal.invoke(composer, attachment);
                            return null;
                        }
                    });
                    return;
                } catch (Throwable error) {
                    log("local API temporary attachment removal failed: "
                            + safeThrowableMessage(error));
                    return;
                }
            }
        }
        log("local API temporary attachment remover unavailable");
    }

    private static Object callOnMainThread(
            final java.util.concurrent.Callable<Object> callable) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) return callable.call();
        Handler handler = currentMainHandler();
        if (handler == null) return null;
        final java.util.concurrent.atomic.AtomicReference<Object> value =
                new java.util.concurrent.atomic.AtomicReference<Object>();
        final java.util.concurrent.atomic.AtomicReference<Throwable> failure =
                new java.util.concurrent.atomic.AtomicReference<Throwable>();
        final CountDownLatch latch = new CountDownLatch(1);
        handler.post(new Runnable() {
            @Override public void run() {
                try { value.set(callable.call()); }
                catch (Throwable error) { failure.set(error); }
                finally { latch.countDown(); }
            }
        });
        if (!latch.await(3L, TimeUnit.SECONDS)) return null;
        Throwable error = failure.get();
        if (error instanceof Exception) throw (Exception) error;
        if (error != null) throw new RuntimeException(error);
        return value.get();
    }

    public static Throwable coroutineFailure(Object value) {
        if (value instanceof Throwable) return (Throwable) value;
        if (HostCompat.simpleNameIs(value, "fx6")) {
            Object failure = fieldByName(value, "a");
            if (failure instanceof Throwable) return (Throwable) failure;
        }
        return null;
    }

    public static String safeThrowableMessage(Throwable throwable) {
        if (throwable == null) return "unknown error";
        Throwable value = HookAttachmentPipeline.deepestCause(throwable);
        String message = value.getMessage();
        String result = value.getClass().getSimpleName()
                + (message == null || message.length() == 0 ? "" : ": " + message);
        return result.length() > 500 ? result.substring(0, 500) : result;
    }

    private static boolean isAgentHostStructureFailure(Throwable throwable) {
        Throwable current = throwable;
        int depth = 0;
        while (current != null && depth++ < 16) {
            if (current instanceof ClassNotFoundException
                    || current instanceof NoSuchFieldException
                    || current instanceof NoSuchMethodException) {
                return true;
            }
            current = current.getCause();
        }
        String message = safeThrowableMessage(throwable);
        return message.contains("ClassNotFoundException")
                || message.contains("NoSuchFieldException")
                || message.contains("NoSuchMethodException");
    }

    public static z2.GatewayException localApiStreamFailure(Throwable failure) {
        Throwable cursor = failure;
        HashSet<Throwable> seen = new HashSet<>();
        while (cursor != null && seen.add(cursor)) {
            if (cursor instanceof LocalApiUpstreamException) {
                LocalApiUpstreamException upstream = (LocalApiUpstreamException) cursor;
                return new z2.GatewayException(upstream.status, upstream.code,
                        upstream.type, "DeepSeek stream failed: " + upstream.getMessage());
            }
            cursor = cursor.getCause();
        }
        return new z2.GatewayException(502, "upstream_stream_failed",
                "server_error", "DeepSeek stream failed: " + safeThrowableMessage(failure));
    }

    public static final class LocalApiUpstreamException extends RuntimeException {
        public final int status;
        public final String code;
        public final String type;

        public LocalApiUpstreamException(int status, String code, String type, String message) {
            super(message);
            this.status = status;
            this.code = code;
            this.type = type;
        }
    }

    public static String applyApiSet(StringBuilder current, String replacement) {
        if (replacement == null) return "";
        String before = current.toString();
        if (replacement.equals(before)) return "";
        if (replacement.startsWith(before)) {
            String delta = replacement.substring(before.length());
            current.append(delta);
            return delta;
        }
        // 正文流里有时会插入一条「本回答由 AI 生成 / 回答由 AI 生成，仅供参考」的页脚 SET，
        // 它的值比已累计的正文短得多。若把它当权威值会清空完整回复，这里忽略它。
        if (isDisclaimerFooter(replacement) && before.length() > replacement.trim().length()) {
            return "";
        }
        current.setLength(0);
        current.append(replacement);
        // A divergent SET is unusual but represents the authoritative upstream value. Streaming
        // cannot retract bytes already delivered, so emit the replacement as the safest signal.
        return replacement;
    }

    public static boolean isDisclaimerFooter(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.length() == 0 || t.length() > 64) return false;
        boolean aiGenerated = t.contains("AI 生成") || t.contains("AI生成")
                || t.contains("AI 回复") || t.contains("AI回复");
        boolean disclaimer = t.contains("仅供参考") || t.contains("请仔细甄别")
                || t.contains("合法使用") || t.contains("请注意甄别");
        return aiGenerated && disclaimer;
    }

    public static final class LocalApiClientCancelled extends RuntimeException {
        public LocalApiClientCancelled() { super("local API client disconnected"); }
    }

    public static final class LocalApiGenerationSatisfied extends RuntimeException {
        public LocalApiGenerationSatisfied() { super("complete local tool action captured"); }
    }

    private static Object newSessionDeleteRequest(Class<?> type, String sid) throws Throwable {
        try {
            Constructor<?> direct = type.getDeclaredConstructor(String.class);
            direct.setAccessible(true);
            return direct.newInstance(sid);
        } catch (NoSuchMethodException missing) {
            if (HostCompat.isV241()) {
                for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                    Class<?>[] p = constructor.getParameterTypes();
                    if (p.length != 3 || p[0] != int.class || p[1] != String.class
                            || !java.util.List.class.isAssignableFrom(p[2])) continue;
                    constructor.setAccessible(true);
                    // code257 jh1(mask, id, ids): mask=2 keeps the single id and defaults ids.
                    return constructor.newInstance(2, sid, null);
                }
            }
            throw missing;
        }
    }

    public static NativeSessionEndpoint resolveSessionDeleteEndpoint(Object transport,
                                                                        String preferred) {
        if (transport == null) return null;
        NativeSessionEndpoint create = HookAttachmentPipeline.resolveSessionCreateEndpoint(
                transport, HostCompat.localApiSessionCreateMethod());
        if (create == null) return null;
        Method best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Method candidate : create.service.getClass().getDeclaredMethods()) {
            Class<?>[] p = candidate.getParameterTypes();
            if (p.length != 2 || !HookAttachmentPipeline.isSessionDeleteRequestType(p[0])) continue;
            String name = candidate.getName();
            int score = name.equals(preferred) ? 100 : 0;
            if ("c".equals(name) || "w".equals(name) || "s".equals(name)
                    || "f".equals(name)) score += 40;
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best == null ? null : new NativeSessionEndpoint(create.service, best);
    }

    boolean deleteThrowawaySession(ClassLoader cl, Object r92, String sid) {
        try {
            String deleteName = HostCompat.localApiSessionDeleteMethod();
            NativeSessionEndpoint endpoint = resolveSessionDeleteEndpoint(r92, deleteName);
            if (endpoint == null) {
                extLog("[RELAY] session delete method 未找到: "
                        + r92.getClass().getName() + "." + deleteName);
                return false;
            }
            Object deleteRequest = newSessionDeleteRequest(
                    endpoint.method.getParameterTypes()[0], sid);
            Object response = HookAccountLoginSecurity.INSTANCE.driveSuspend(
                    cl, endpoint.method, endpoint.service, new Object[]{deleteRequest});
            Object bodyValue = fieldByName(response, "j");
            if (bodyValue instanceof String) {
                JSONObject envelope = new JSONObject((String) bodyValue);
                if (envelope.optInt("code", Integer.MIN_VALUE) != 0) return false;
                JSONObject data = envelope.optJSONObject("data");
                return data == null || !data.has("biz_code") || data.optInt(
                        "biz_code", Integer.MIN_VALUE) == 0;
            }
            return localApiSessionOperationSucceeded(response);
        } catch (Throwable t) { extLog("[RELAY] deleteThrowawaySession err: " + t); return false; }
    }

    public static boolean localApiSessionOperationSucceeded(Object response) {
        if (response == null) return false;
        Object serverCode = fieldByName(response, "a");
        if (serverCode instanceof Number && ((Number) serverCode).intValue() != 0) {
            return false;
        }
        Object wrapper = fieldByName(response, "c");
        Object bizCode = fieldByName(wrapper, "a");
        if (bizCode != null && !(bizCode instanceof Number)) {
            Object value = invokeNoArg(bizCode, "getValue");
            if (!(value instanceof Number)) value = fieldByName(bizCode, "a");
            bizCode = value;
        }
        return !(bizCode instanceof Number) || ((Number) bizCode).intValue() == 0;
    }

    public static String stackToString(Throwable t) {
        if (t == null) return "";
        StringBuilder sb = new StringBuilder();
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < st.length && i < 18; i++) sb.append("    at ").append(st[i]).append('\n');
        Throwable cause = t.getCause();
        if (cause != null && cause != t) sb.append("  caused by: ").append(cause).append('\n');
        return sb.toString();
    }

    public static Object emptyContextProxy(ClassLoader cl, final Class<?> ccCls) {
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
    public static Object newLocalApiCancellationJob(ClassLoader cl, Class<?> contextClass) {
        try {
            Class<?> jobClass = HostCompat.load(cl, "c74");
            Constructor<?> constructor = jobClass.getDeclaredConstructor(boolean.class);
            constructor.setAccessible(true);
            Object job = constructor.newInstance(true);
            return contextClass.isInstance(job) ? job : null;
        } catch (Throwable ignored) { return null; }
    }

}
