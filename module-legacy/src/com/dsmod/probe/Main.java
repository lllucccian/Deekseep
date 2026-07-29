package com.dsmod.probe;

import android.app.Activity;
import android.database.Cursor;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.system.Os;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
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

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class Main implements IXposedHookLoadPackage {

    private static final String TAG = "DSPROBE";
    private static final String TARGET = "com.deepseek.chat";
    static final String SELF = "com.dsmod.probe";
    private static final String LOG_PATH = "/data/data/com.deepseek.chat/files/dsprobe.log";
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    // 存储在 DeepSeek 自己的 files 目录，hook 进程和 UI 都能直接读写
    static final String PROMPT_FILE       = "/data/data/com.deepseek.chat/files/deekseep_prompt.txt";
    static final String PROMPT_LINK_FILE  = "/data/data/com.deepseek.chat/files/deekseep_prompt_link.txt";
    static final String PROMPT_SOURCE_FILE = "/data/data/com.deepseek.chat/files/deekseep_prompt_source.txt";
    static final String ENABLED_FILE      = "/data/data/com.deepseek.chat/files/deekseep_enabled";
    static final String NO_CENSOR_FILE    = "/data/data/com.deepseek.chat/files/deekseep_nocensor";
    static final String SRVLOG_FILE       = "/data/data/com.deepseek.chat/files/deekseep_srvlog";
    static final int    PICK_REQUEST      = 0xDE3E;
    static final int    PICK_IMAGE_REQUEST = 0xDE3F;
    private static final String EDITOR_IMAGE_MASTER_DIR =
            "/data/data/com.deepseek.chat/files/deekseep_editor_images";
    private static final String EDITOR_IMAGE_CACHE_DIR =
            "/data/data/com.deepseek.chat/cache/captured";
    private static final String EDITOR_IMAGE_URI_PREFIX =
            "content://com.deepseek.chat.provider/tmp_captured_images/";

    interface GalleryPickCallback { void onPicked(Uri uri); }
    private static volatile GalleryPickCallback galleryPickCallback;

    // 诊断：记录服务器返回的 SSE 原始事件（受 SRVLOG_FILE 开关控制）
    static final String SRV_LOG_PATH = "/data/data/com.deepseek.chat/files/deekseep_srv.log";
    static final String SRV_LOG_EXT  = "/storage/emulated/0/deekseep_srv.log";

    // DeekseepUi 选完文件后的 UI 刷新回调
    static volatile Runnable onPickComplete;

    // 诊断：模块加载到 DeepSeek 后，首个 Activity 弹一次 Toast 确认注入生效（无需 root/日志）
    private static boolean loadToastShown = false;
    // 每个 DeepSeek 进程只向模块 StatusProvider 握手一次，写激活标记（供 SettingsActivity 判活）
    private static volatile boolean selfPinged = false;
    // 外部可见的加载标记（best-effort，宿主有存储权限时才写得进去）
    static final String LOADED_MARK_EXT = "/storage/emulated/0/deekseep_loaded.txt";

    // 首次注入 DeepSeek 时弹出的免责声明；同意后写此标记，之后不再弹
    static final String DISCLAIMER_FILE = "/data/data/com.deepseek.chat/files/deekseep_disclaimer_ok";
    private static volatile boolean disclaimerHandled = false;
    // 模块自身进程被注入后写入的激活标记，供 SettingsActivity 二次判定“已激活”
    static final String SELF_ACTIVE_MARK = "/data/data/" + SELF + "/files/deekseep_active";

    private static final String SETTINGS_CLASS = "u25";
    private static final String SETTINGS_METHOD = "i";

    // Captured from mc.f: DeepSeek's complete native session list and its own click handler.
    private static volatile Object NATIVE_SESSION_LIST;
    private static volatile Object NATIVE_SESSION_STATE;
    private static volatile Object NATIVE_SESSION_CLICK;
    private static volatile Object NATIVE_SESSION_EVENTS;
    private static final java.util.concurrent.ConcurrentHashMap<String, Long>
            RECENTLY_DELETED_SESSION_IDS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long DELETED_SESSION_VISIBILITY_GRACE_MS = 120000L;
    private static final Map<Object, Boolean> FILTERED_ORIGINAL_MESSAGES =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    private static final Map<String, Object> LOCAL_NATIVE_SESSIONS = new HashMap<>();
    private static volatile HashSet<String> LOCAL_SESSION_IDS = new HashSet<>();
    private static volatile long LOCAL_SESSION_IDS_AT;
    private static volatile long LOCAL_NATIVE_MERGE_LOG_AT;
    private static volatile long LOCAL_NATIVE_STATE_REPAIR_LOG_AT;
    private static volatile long LOCAL_DIRECTORY_MERGE_LOG_AT;
    private static final ThreadLocal<Boolean> LOCAL_DIRECTORY_SYNC = new ThreadLocal<>();
    private static volatile Object IMAGE_FILE_API;
    private static volatile Object IMAGE_COMPOSER;
    private static volatile ClassLoader IMAGE_HOST_CL;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<Activity> curAct = new WeakReference<>(null);
    private WeakReference<TextView> btn = new WeakReference<>(null);
    private WeakReference<Object> navController = new WeakReference<>(null);

    static synchronized void log(String msg) {
        try { XposedBridge.log(TAG + " " + msg); } catch (Throwable ignored) {}
        String line = TS.format(new Date()) + "  " + msg + "\n";
        try {
            FileWriter w = new FileWriter(LOG_PATH, true);
            w.write(line);
            w.close();
        } catch (Throwable ignored) {}
        try {
            FileWriter w = new FileWriter("/storage/emulated/0/dsprobe.log", true);
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
        try { XposedBridge.log(TAG + " SRV " + msg); } catch (Throwable ignored) {}
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

    @Override
    public void handleLoadPackage(LoadPackageParam lp) {
        final ClassLoader cl = lp.classLoader;
        final String pkg = lp.packageName;

        if (SELF.equals(pkg)) { markSelfActive(cl); return; }
        if (!TARGET.equals(pkg)) return;

        try { new FileWriter(LOG_PATH, false).close(); } catch (Throwable ignored) {}
        // 服务器返回诊断日志：每次应用启动清空重记（与主日志一致）
        if (isSrvLog()) {
            try { new FileWriter(SRV_LOG_PATH, false).close(); } catch (Throwable ignored) {}
            try { new FileWriter(SRV_LOG_EXT, false).close(); } catch (Throwable ignored) {}
        }
        log("module loaded (legacy), package=" + pkg);
        restoreLocalEditorImages();
        int obsoleteTriggers = ChatEditorUi.removeObsoleteLocalSessionProtection();
        if (obsoleteTriggers > 0) {
            log("removed obsolete local-session triggers=" + obsoleteTriggers);
        }
        // Repair sidecars before the host starts WCDB. Never open Android SQLite from a delayed
        // worker after startup: it can block WCDB and make intact conversations render empty.
        int restoredLocal = ChatEditorUi.restoreLocalConversations();
        if (restoredLocal > 0) {
            log("restored local conversations before WCDB startup=" + restoredLocal);
        }
        // 外部可见加载标记：证明模块确实被注入进了 DeepSeek 进程
        try {
            FileWriter w = new FileWriter(LOADED_MARK_EXT, false);
            w.write(TS.format(new Date()) + "  loaded into " + pkg + "\n");
            w.close();
        } catch (Throwable ignored) {}

        // 跟踪当前 Activity（并在首个 Activity 弹一次 Toast 确认注入生效）
        try {
            Method onResume = Activity.class.getDeclaredMethod("onResume");
            XposedBridge.hookMethod(onResume, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        Activity act = (Activity) param.thisObject;
                        curAct = new WeakReference<>(act);
                        pingSelfActive(act);
                        if (!loadToastShown) {
                            loadToastShown = true;
                            try {
                                android.widget.Toast.makeText(act,
                                        "Deekseep 已注入 (v" + SettingsActivity.VERSION + ")",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            } catch (Throwable ignored) {}
                        }
                        maybeShowDisclaimer(act);
                    } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) { log("hook onResume failed: " + t); }

        try {
            Method onDestroy = Activity.class.getDeclaredMethod("onDestroy");
            XposedBridge.hookMethod(onDestroy, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    try { if (curAct.get() == param.thisObject) hideButton(); } catch (Throwable ignored) {}
                }
            });
        } catch (Throwable t) { log("hook onDestroy failed: " + t); }

        // 拦截 onActivityResult，捕获文件选择器结果
        try {
            Method oar = Activity.class.getDeclaredMethod("onActivityResult",
                    int.class, int.class, Intent.class);
            XposedBridge.hookMethod(oar, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    try {
                        int req = (int) param.args[0];
                        int res = (int) param.args[1];
                        Object dataArg = param.args[2];
                        if (req == PICK_IMAGE_REQUEST) {
                            GalleryPickCallback callback = galleryPickCallback;
                            galleryPickCallback = null;
                            Uri uri = null;
                            if (res == Activity.RESULT_OK && dataArg instanceof Intent) {
                                Intent data = (Intent) dataArg;
                                uri = data.getData();
                                if (uri != null) persistReadGrant((Activity) param.thisObject, data, uri);
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
                                    persistReadGrant((Activity) param.thisObject, data, uri);
                                    handlePickedFile((Activity) param.thisObject, uri);
                                }
                            }
                        }
                    } catch (Throwable t) { log("onActivityResult err: " + t); }
                }
            });
        } catch (Throwable t) { log("hook onActivityResult failed: " + t); }

        // hook ChatFullCompletionRequest 构造，注入系统提示词到 prompt 字段
        hookChatRequest(cl);
        installHistoryBridge(cl);
        File historyMigration=new File("/data/data/com.deepseek.chat/files/deekseep_history_migration_v3");
        if(!historyMigration.exists()){
            boolean migrationOk=true;
            try { int n=ChatEditorUi.repairMalformedThinkFragmentsAllSessions();if(n<0)migrationOk=false;log("repairMalformedThinkFragments fixed="+n); }
            catch(Throwable t){migrationOk=false;log("repairMalformedThinkFragments err: "+t);}
            try { int n=ChatEditorUi.stripAllSessions();if(n<0)migrationOk=false;log("stripAllSessions cleaned="+n); }
            catch(Throwable t){migrationOk=false;log("stripAllSessions err: "+t);}
            if(migrationOk)try{overwriteTextFile(historyMigration.getPath(),"3");}
            catch(Throwable t){log("history migration marker err: "+t);}
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
        try { installImageCredentialBridge(cl); }
        catch (Throwable t) { log("installImageCredentialBridge wiring failed: " + t); }
        hookLocalSessionDirectoryMerge(cl);
        hookLocalNativeSessionRefresh(cl);
        hookLocalSessionDeletedResponse(cl);
        hookNativeSessionNavigator(cl);
        // hook 导航变化，离开设置页时移除入口按钮
        hookSettingsNavigation(cl);

        // hook 设置页主 Composable -> 显示 Deekseep 按钮
        try {
            Class<?> k = cl.loadClass(SETTINGS_CLASS);
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (m.getName().equals(SETTINGS_METHOD)) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            main.post(new Runnable() { public void run() { showButton(); } });
                        }
                    });
                    n++;
                }
            }
            log("hooked settings composable " + SETTINGS_CLASS + "." + SETTINGS_METHOD + " x" + n);
        } catch (Throwable t) { log("hook settings composable failed: " + t); }
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
        new File(PROMPT_FILE).delete();
        new File(PROMPT_LINK_FILE).delete();
        new File(PROMPT_SOURCE_FILE).delete();
        new File(ENABLED_FILE).delete();
    }

    static boolean isEnabled() {
        return new File(ENABLED_FILE).exists();
    }

    static void setEnabled(boolean on) {
        try {
            File ef = new File(ENABLED_FILE);
            if (on) overwriteTextFile(ENABLED_FILE, "");
            else ef.delete();
        } catch (Throwable ignored) {}
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
            Class<?> k = cl.loadClass("ew0");
            int n = 0;
            for (Constructor<?> ctor : k.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                // 合成构造器首参为 int（kotlinx 序列化标志位），普通构造器首参为 String
                final boolean isSynthetic = pts.length > 0 && pts[0] == int.class;
                final int promptIdx = isSynthetic ? 3 : 2;
                if (pts.length <= promptIdx) continue;
                if (pts[promptIdx] != String.class) continue;
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            String sysPrompt = readPrompt();
                            if (sysPrompt != null && !sysPrompt.isEmpty()) {
                                String orig = (String) param.args[promptIdx];
                                if (orig == null) orig = "";
                                param.args[promptIdx] = HistoryBridge.wrapSystemPrompt(sysPrompt, orig);
                                log("injected system prompt (synthetic=" + isSynthetic + ")");
                            }
                        } catch (Throwable t) { log("inject err: " + t); }
                    }
                });
                n++;
            }
            log("hooked ew0 constructors x" + n);
        } catch (Throwable t) { log("hookChatRequest failed: " + t); }
    }

    // DeepSeek 2.2.1 使用 fm8，2.2.2 使用 gm8；按写方法结构同时兼容两版。
    private void installHistoryBridge(final ClassLoader cl) {
        int writes = 0;
        for (String name : new String[]{"gm8", "fm8"}) {
            try {
                Class<?> repo = cl.loadClass(name);
                for (Method m : repo.getDeclaredMethods()) {
                    Class<?>[] pts = m.getParameterTypes();
                    if (!"b".equals(m.getName()) || pts.length != 7
                            || pts[0] != String.class || pts[1] != int.class
                            || !List.class.isAssignableFrom(pts[4])) continue;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (isNoCensor()) {
                                    String sid = param.args.length > 0 && param.args[0] instanceof String
                                            ? (String) param.args[0] : null;
                                    Object rows = param.args.length > 4 ? param.args[4] : null;
                                    int restored = ResponsePreserver.restoreRepositoryRows(cl, sid, rows);
                                    if (restored > 0) log("restored preserved responses before history write="
                                            + restored + " sid=" + sid);
                                }
                                int n = HistoryBridge.sanitizeRepositoryRows(param.args);
                                if (n > 0) log("history repository prompts cleaned=" + n);
                            } catch (Throwable t) { log("history repository sanitize failed: " + t); }
                        }
                    });
                    writes++;
                }
            } catch (Throwable ignored) {}
        }
        try {
            Class<?> response = cl.loadClass("pw0");
            int constructors = 0;
            for (Constructor<?> ctor : response.getDeclaredConstructors()) {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            if (isNoCensor()) {
                                int restored = ResponsePreserver.restoreHistoryResponse(cl, param.thisObject);
                                if (restored > 0) log("restored preserved responses in online history="
                                        + restored);
                            }
                            HistoryBridge.Result r = HistoryBridge.processHistoryResponse(param.thisObject);
                            if (r.cleaned > 0) log("online history prompts cleaned=" + r.cleaned);
                        } catch (Throwable t) { log("online history bridge failed: " + t); }
                    }
                });
                constructors++;
            }
            log("installed history bridge pw0=" + constructors + " repositoryWrites=" + writes);
        } catch (Throwable t) { log("install history bridge failed: " + t); }
    }

    // ── 阻止内容安全审查擦除（clear_response 拦截）─────────────────
    private void hookSafetyRetraction(ClassLoader cl) {
        try {
            Class<?> k = cl.loadClass("kb7");
            int n = 0;
            for (Constructor<?> ctor : k.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                int boolIdx = -1;
                for (int i = 0; i < pts.length; i++) {
                    if (pts[i] == boolean.class) { boolIdx = i; break; }
                }
                if (boolIdx < 0) continue;
                final int idx = boolIdx;
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object[] a = param.args;
                            if (isSrvLog()) {
                                StringBuilder sb = new StringBuilder("kb7(hint)");
                                for (int i = 0; i < a.length; i++) {
                                    sb.append(" arg").append(i).append('=').append(a[i]);
                                }
                                srvLog(sb.toString());
                            }
                            if (isNoCensor()) {
                                Object cur = a[idx];
                                if (Boolean.TRUE.equals(cur)) {
                                    a[idx] = Boolean.FALSE;
                                    log("blocked clear_response (kb7.arg" + idx + ")");
                                }
                            }
                        } catch (Throwable t) { log("clear_response block err: " + t); }
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
            Class<?> k = cl.loadClass("lv7");
            int n = 0;
            for (Constructor<?> ctor : k.getDeclaredConstructors()) {
                Class<?>[] pts = ctor.getParameterTypes();
                if (pts.length != 2 || pts[0] != String.class || pts[1] != String.class) continue;
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (isSrvLog()) {
                                String evt = String.valueOf(param.args[0]);
                                Object d = param.args[1];
                                String data = String.valueOf(d);
                                if (data != null && data.length() > 4000) {
                                    data = data.substring(0, 4000) + "...<truncated len=" + String.valueOf(d).length() + ">";
                                }
                                srvLog("evt=" + evt + "  data=" + data);
                            }
                        } catch (Throwable t) { srvLog("lv7 capture err: " + t); }
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
            Class<?> k = cl.loadClass("mv");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals("i")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 4 || pts[0] != String.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object a0 = param.args[0];
                            String path = a0 instanceof String ? (String) a0 : "";
                            String val = String.valueOf(param.args[1]);
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
                                    srvLog("[CF] this.m.a@skip " + dumpMv(param.thisObject));
                                    srvLog(dumpStack());
                                }
                                if (isNoCensor()) {
                                    markFilteredOriginal(cl, param.thisObject, "mv.i/" + path);
                                    log("skipped CONTENT_FILTER patch mv.i(" + path + ")");
                                    if (isSrvLog()) srvLog("[CF] skipped mv.i(" + path + ")");
                                    param.setResult(null); // 跳过原 void 方法
                                }
                            }
                        } catch (Throwable t) { log("content-filter block err: " + t); }
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
            Field mf = mvObj.getClass().getDeclaredField("m");
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
            Class<?> k = cl.loadClass("vv7");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals("e") || m.getParameterTypes().length != 1) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object r = param.getResult();
                            if (isSrvLog() && r != null) srvLog("[VV7] new mv " + dumpMv(r));
                        } catch (Throwable t) { srvLog("[VV7] err " + t); }
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
            Class<?> k = cl.loadClass("mv");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                final String mn = m.getName();
                if (!mn.equals("S") && !mn.equals("R")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 1 || pts[0] != String.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object a0 = param.args[0];
                            String v = a0 instanceof String ? (String) a0 : "";
                            boolean cf = v.contains("CONTENT_FILTER");
                            if (isSrvLog()) srvLog("[SR] mv." + mn + "(" + v + ") nocensor=" + isNoCensor());
                            if (cf && isNoCensor()) {
                                markFilteredOriginal(cl, param.thisObject, "mv." + mn);
                                log("blocked mv." + mn + "(" + v + ")");
                                if (isSrvLog()) srvLog("[SR] blocked mv." + mn);
                                param.setResult(null);
                            }
                        } catch (Throwable t) { log("status-write block err: " + t); }
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
            Class<?> k = cl.loadClass("h83");
            int n = 0;
            for (Method m : k.getDeclaredMethods()) {
                if (!m.getName().equals("h")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 1) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            if (isSrvLog()) {
                                String v = String.valueOf(param.args[0]);
                                if (v.contains("TEMPLATE_RESPONSE")) {
                                    srvLog("[TPL] h83.h TEMPLATE_RESPONSE seen");
                                    srvLog(dumpStack());
                                }
                            }
                        } catch (Throwable t) { srvLog("[TPL] err " + t); }
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
            final Class<?> tpk = cl.loadClass("tp");
            final Field fField = tpk.getDeclaredField("f");
            fField.setAccessible(true);
            int n = 0;
            for (Method m : tpk.getDeclaredMethods()) {
                if (!m.getName().equals("u")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length != 2 || !List.class.isAssignableFrom(pts[1])) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object tp = param.args[0];
                            Object rawList = param.args[1];
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
                                    param.args[1] = copy;
                                }
                            }
                        } catch (Throwable t) { log("final-merge guard err: " + t); }
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
            final Class<?> tpk = cl.loadClass("tp");
            final Field fField = tpk.getDeclaredField("f");
            fField.setAccessible(true);
            final Class<?> uok = cl.loadClass("uo");
            int n = 0;
            for (Method m : tpk.getDeclaredMethods()) {
                final String mn = m.getName();
                if (!mn.equals("q") && !mn.equals("p") && !mn.equals("a")) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length < 1 || !uok.isAssignableFrom(pts[0])) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        try {
                            Object tp = param.thisObject;
                            Object msg = param.args[0];
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
                                        param.args[0] = durable;
                                        log("restored preserved response sid=" + sid + " msg=" + id
                                                + " in tp." + mn);
                                        return;
                                    }
                                    if (existing != null && existing != msg) {
                                        String exS = callStr(existing, "D");
                                        String exQ = callStr(existing, "x");
                                        boolean exCf = ResponsePreserver.isFilteredHostMessage(existing);
                                        if (!exCf) {
                                            param.args[0] = existing;
                                            log("tp." + mn + " kept original id=" + id + " over CONTENT_FILTER");
                                            if (isSrvLog()) srvLog("[FA] kept original id=" + id + " origStatus=" + exS);
                                        }
                                    }
                                }
                            }
                        } catch (Throwable t) { log("final-apply guard err: " + t); }
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
            Method m = obj.getClass().getMethod(method);
            Object r = m.invoke(obj);
            return r == null ? null : String.valueOf(r);
        } catch (Throwable t) { return null; }
    }

    // 反射调用无参方法返回 int（uo.u()=消息id）
    private static Integer callInt(Object obj, String method) {
        try {
            Method m = obj.getClass().getMethod(method);
            Object r = m.invoke(obj);
            if (r instanceof Integer) return (Integer) r;
            if (r instanceof Number) return ((Number) r).intValue();
            return null;
        } catch (Throwable t) { return null; }
    }

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
                    if (nativeSessionContainsMessage(entry.getValue(), message)) return entry.getKey();
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
            Class<?> apiClass = cl.loadClass("pv0");
            for (Constructor<?> ctor : apiClass.getDeclaredConstructors()) {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        IMAGE_FILE_API = param.thisObject;
                        IMAGE_HOST_CL = cl;
                    }
                });
                installed++;
            }
        } catch (Throwable t) { log("capture pv0 failed: " + t); }
        try {
            Class<?> composerClass = cl.loadClass("k31");
            for (Constructor<?> ctor : composerClass.getDeclaredConstructors()) {
                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            IMAGE_COMPOSER = param.thisObject;
                            Object repository = readHostField(param.thisObject, "c");
                            Object api = readHostField(repository, "d");
                            if (api != null) {
                                IMAGE_FILE_API = api;
                                IMAGE_HOST_CL = cl;
                            }
                        } catch (Throwable ignored) {}
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
                found = method;
                break;
            }
        }
        if (found == null) return null;
        found.setAccessible(true);
        final Method preprocess = found;
        Class<?> blockClass = cl.loadClass("mb3");
        Object block = Proxy.newProxyInstance(cl, new Class<?>[]{blockClass},
                new InvocationHandler() {
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        if (galleryProxyObjectMethod(method)) {
                            return galleryProxyObject(proxy, method, args);
                        }
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

    private static boolean galleryProxyObjectMethod(Method method) {
        return method != null && method.getDeclaringClass() == Object.class;
    }

    private static Object galleryProxyObject(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        if ("toString".equals(name)) return "DeekseepGalleryProxy";
        if ("hashCode".equals(name)) return Integer.valueOf(System.identityHashCode(proxy));
        if ("equals".equals(name)) return Boolean.valueOf(args != null && args.length > 0
                && proxy == args[0]);
        return null;
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
        Class<?> coroutine = cl.loadClass("a60");
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
        if (!"kp5".equals(imageSimpleName(result))) {
            log("fork_file_task rejected " + fromModel + "->" + toModel
                    + " result=" + String.valueOf(result));
            return null;
        }
        Object fp = readHostField(result, "b");
        if (!"fp".equals(imageSimpleName(fp))) {
            log("fork_file_task success wrapper had no fp: " + String.valueOf(result));
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
        for (Constructor<?> ctor : cl.loadClass("u40").getDeclaredConstructors()) {
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
        if (!"kp5".equals(imageSimpleName(result))) {
            log("fetch_files rejected file=" + fileId + " result=" + String.valueOf(result));
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
        for (Method method : cl.loadClass("u82").getDeclaredMethods()) {
            if ("K".equals(method.getName()) && method.getParameterTypes().length == 2
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

    private static String imageSimpleName(Object value) {
        if (value == null) return "null";
        String name = value.getClass().getName();
        int split = name.lastIndexOf('.');
        return split >= 0 ? name.substring(split + 1) : name;
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

    /**
     * DeepSeek's p68 cloud-directory transaction asks aw.a() for every local session, then drops
     * tables whose ids are absent from the server response. Hide only editor-owned sidecar ids from
     * that one comparison. Incoming server rows and ordinary server-side deletions stay untouched.
     */
    private void hookLocalSessionDirectoryMerge(final ClassLoader cl) {
        try {
            Class<?> transaction = cl.loadClass("p68");
            Class<?> directoryDao = cl.loadClass("aw");
            int transactionHooks = 0;
            int directoryHooks = 0;
            for (Method method : transaction.getDeclaredMethods()) {
                if (!"a".equals(method.getName()) || method.getParameterTypes().length != 0
                        || method.getReturnType() != void.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        LOCAL_DIRECTORY_SYNC.set(Boolean.TRUE);
                    }
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        LOCAL_DIRECTORY_SYNC.remove();
                    }
                });
                transactionHooks++;
            }
            for (Method method : directoryDao.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"a".equals(method.getName())
                        || !java.lang.reflect.Modifier.isStatic(method.getModifiers())
                        || types.length != 1 || types[0] != directoryDao
                        || !List.class.isAssignableFrom(method.getReturnType())) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object result = param.getResult();
                            if (!Boolean.TRUE.equals(LOCAL_DIRECTORY_SYNC.get())
                                    || !(result instanceof List)) return;
                            HashSet<String> localIds = ChatEditorUi.localSessionIdsFromAllBackups();
                            if (localIds.isEmpty()) return;
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
                        } catch (Throwable t) {
                            log("filter local cloud-directory rows failed: " + t);
                        }
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

    /** Keep editor-owned tp objects in ed0.e, the state observed by navigation after cloud sync. */
    private void hookLocalNativeSessionRefresh(final ClassLoader cl) {
        try {
            Class<?> repository = cl.loadClass("ed0");
            Class<?> continuation = cl.loadClass("uz1");
            int installed = 0;
            for (Method method : repository.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"h".equals(method.getName()) || types.length != 1
                        || types[0] != continuation) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object state = readHostField(param.thisObject, "e");
                            if (state instanceof List
                                    && "uo7".equals(state.getClass().getName())) {
                                preserveEditorLocalNativeSessions((List) state,
                                        localOnlySessionIds(cl));
                            }
                        } catch (Throwable t) {
                            log("capture editor-local native state failed: " + t);
                        }
                    }

                    @Override protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Object state = readHostField(param.thisObject, "e");
                            if (!(state instanceof List)
                                    || !"uo7".equals(state.getClass().getName())) return;
                            int restored = preserveEditorLocalNativeSessions((List) state,
                                    localOnlySessionIds(cl));
                            if (restored > 0) {
                                long now = System.currentTimeMillis();
                                if (now - LOCAL_NATIVE_STATE_REPAIR_LOG_AT > 1000L) {
                                    LOCAL_NATIVE_STATE_REPAIR_LOG_AT = now;
                                    log("restored editor-local sessions into native state="
                                            + restored + " host sessions="
                                            + ((List) state).size());
                                }
                            }
                        } catch (Throwable t) {
                            log("restore editor-local native state failed: " + t);
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
            boolean present = false;
            try {
                for (Object current : new ArrayList<Object>(state)) {
                    if (sid.equals(String.valueOf(readHostField(current, "a")))) {
                        present = true;
                        break;
                    }
                }
                if (!present && state.add(session)) restored++;
            } catch (Throwable t) {
                log("restore editor-local native session failed sid=" + sid + ": " + t);
            }
        }
        if (restored > 0) {
            try {
                Collections.sort(state, new Comparator<Object>() {
                    @Override public int compare(Object left, Object right) {
                        boolean lp = Boolean.TRUE.equals(invokeHostNoArg(left, "h"));
                        boolean rp = Boolean.TRUE.equals(invokeHostNoArg(right, "h"));
                        if (lp != rp) return lp ? -1 : 1;
                        Object lv = readHostField(left, "c");
                        Object rv = readHostField(right, "c");
                        double l = lv instanceof Number ? ((Number) lv).doubleValue() : 0d;
                        double r = rv instanceof Number ? ((Number) rv).doubleValue() : 0d;
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

    /** Keep an editor-only local session when its expected detail request has no cloud row. */
    private void hookLocalSessionDeletedResponse(final ClassLoader cl) {
        try {
            Class<?> handler = cl.loadClass("at0");
            Class<?> resultType = cl.loadClass("op5");
            Class<?> ownerType = cl.loadClass("yg3");
            int installed = 0;
            for (Method method : handler.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"a".equals(method.getName()) || types.length != 3
                        || types[0] != resultType || types[1] != boolean.class
                        || types[2] != ownerType || method.getReturnType() != void.class) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object status = readHostField(param.args[0], "a");
                            Object code = readHostField(status, "a");
                            if (!(code instanceof Number) || ((Number) code).intValue() != 1) return;
                            Object viewModel = readHostField(param.args[2], "b");
                            Object session = invokeHostNoArg(viewModel, "G");
                            Object id = readHostField(session, "a");
                            String sid = id == null ? null : String.valueOf(id);
                            if (sid != null && localOnlySessionIds(cl).contains(sid)) {
                                log("suppressed server-deleted result for editor-local sid=" + sid);
                                param.setResult(null);
                            }
                        } catch (Throwable t) {
                            log("inspect local session deleted result failed: " + t);
                        }
                    }
                });
                installed++;
            }
            log("installed editor-local deleted-response guard at0.a x" + installed);
        } catch (Throwable t) {
            log("hookLocalSessionDeletedResponse failed: " + t);
        }
    }

    private void hookNativeSessionNavigator(final ClassLoader cl) {
        try {
            Class<?> mc = cl.loadClass("mc");
            Class<?> ib3 = cl.loadClass("ib3");
            int installed = 0;
            for (Method method : mc.getDeclaredMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (!"f".equals(method.getName()) || types.length != 13) continue;
                if (!List.class.isAssignableFrom(types[0])
                        || !ib3.isAssignableFrom(types[4])
                        || !ib3.isAssignableFrom(types[5])) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            if (param.args[0] instanceof List && param.args[4] != null) {
                                List source = (List) param.args[0];
                                int serverSize = source.size();
                                HashSet<String> localIds = localOnlySessionIds(cl);
                                HashSet<String> seen = new HashSet<>();
                                List merged = source;
                                try {
                                    Constructor<?> ctor = source.getClass().getDeclaredConstructor();
                                    ctor.setAccessible(true);
                                    Object sameType = ctor.newInstance();
                                    if (sameType instanceof List) {
                                        merged = (List) sameType;
                                        merged.addAll(source);
                                    }
                                } catch (Throwable ignored) {}
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
                                    if (merged != source) param.args[0] = merged;
                                    long now = System.currentTimeMillis();
                                    if (now - LOCAL_NATIVE_MERGE_LOG_AT > 5000L) {
                                        LOCAL_NATIVE_MERGE_LOG_AT = now;
                                        log("preserved local native sessions="
                                                + (merged.size() - serverSize)
                                                + " server sessions=" + serverSize);
                                    }
                                }
                                NATIVE_SESSION_LIST = param.args[0];
                                NATIVE_SESSION_CLICK = param.args[4];
                                NATIVE_SESSION_EVENTS = param.args[5];
                            }
                        } catch (Throwable t) {
                            log("capture native session navigator failed: " + t);
                        }
                    }
                });
                installed++;
            }
            log("installed native session navigator hook mc.f x" + installed);
        } catch (Throwable t) { log("hookNativeSessionNavigator failed: " + t); }
    }

    static void refreshNativeHistorySnapshots() {
        try { HistoryBridge.processNativeSessions(NATIVE_SESSION_LIST); }
        catch (Throwable t) { log("refresh native history snapshots failed: "+t); }
    }

    static void refreshNativeHistorySnapshot(String sid) {
        try { HistoryBridge.processNativeSession(NATIVE_SESSION_LIST, sid); }
        catch (Throwable t) { log("refresh native history snapshot failed: "+t); }
    }

    static List<Object[]> nativeSessionDirectory() {
        ArrayList<Object[]> out = new ArrayList<>();
        Object value = NATIVE_SESSION_LIST;
        if (!(value instanceof List)) return out;
        try {
            for (Object session : new ArrayList<Object>((List) value)) {
                String sid = String.valueOf(readHostField(session, "a"));
                if (sid.length() == 0 || "null".equals(sid)) continue;
                if (isSessionRecentlyDeleted(sid)) continue;
                Object titleState = readHostField(session, "g");
                Object title = invokeHostNoArg(titleState, "getValue");
                Object updated = readHostField(session, "c");
                Object model = invokeHostNoArg(session, "f");
                out.add(new Object[]{sid, title instanceof String ? title : "", updated, model});
            }
        } catch (Throwable t) { log("native session directory failed: " + t); }
        return out;
    }

    private static Object invokeHostNoArg(Object target, String name) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) { return null; }
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

    static boolean requestNativeSessionDelete(String sid) {
        if (sid == null || sid.length() == 0) return false;
        Object session = findNativeSession(sid);
        Object events = NATIVE_SESSION_EVENTS;
        if (session != null && events != null) {
            try {
                Class<?> eventType = session.getClass().getClassLoader().loadClass("h61");
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
                if (invokeHostOneArg(events, eventCtor.newInstance(session))) {
                    markSessionDeletedLocally(sid);
                    log("requested native DeepSeek session delete sid=" + sid);
                    return true;
                }
            } catch (Throwable t) {
                log("native DeepSeek delete event failed sid=" + sid + ": " + t);
            }
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
        synchronized (LOCAL_NATIVE_SESSIONS) { return LOCAL_NATIVE_SESSIONS.get(sid); }
    }

    static synchronized void markSessionDeletedLocally(String sid) {
        if (sid == null || sid.length() == 0) return;
        RECENTLY_DELETED_SESSION_IDS.put(sid, System.currentTimeMillis());
        HashSet<String> localIds = new HashSet<>(LOCAL_SESSION_IDS);
        localIds.remove(sid);
        LOCAL_SESSION_IDS = localIds;
        LOCAL_SESSION_IDS_AT = System.currentTimeMillis();
        HistoryBridge.forgetSession(sid);
        ResponsePreserver.forgetSession(sid);
        synchronized (LOCAL_NATIVE_SESSIONS) { LOCAL_NATIVE_SESSIONS.remove(sid); }
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
        RECENTLY_DELETED_SESSION_IDS.remove(sid);
        return false;
    }

    private static Object readHostField(Object target, String name) {
        if (target == null) return null;
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
            Class<?> nav = cl.loadClass("rm5");
            for (Method m : nav.getDeclaredMethods()) {
                if (!m.getName().equals("n") || m.getParameterTypes().length != 2) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        rememberNavController(param.thisObject);
                        scheduleRouteCheck(param.thisObject);
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
            Class<?> gf8 = cl.loadClass("gf8");
            for (Method m : gf8.getDeclaredMethods()) {
                if (!m.getName().equals("A0") || m.getParameterTypes().length != 1) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object nav = param.args[0];
                        if (nav != null) {
                            rememberNavController(nav);
                            scheduleRouteCheck(nav);
                        } else {
                            main.post(new Runnable() { public void run() { hideButton(); } });
                        }
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
            XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    rememberNavController(param.thisObject);
                    scheduleRouteCheck(param.thisObject);
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
        main.postDelayed(new Runnable() {
            public void run() { syncButtonWithRoute(nav); }
        }, 120);
    }

    private void syncButtonWithRoute(Object nav) {
        try {
            if (btn.get() == null) return;
            String route = currentRoute(nav != null ? nav : navController.get());
            if (route == null || route.length() == 0) return;
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
            Field f = obj.getClass().getDeclaredField(name);
            f.setAccessible(true);
            Object v = f.get(obj);
            return v instanceof String ? (String) v : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ── 自我激活标记 ──────────────────────────────────────────────

    // 从 DeepSeek 进程（已确认被注入）向模块 StatusProvider 握手一次，
    // 由模块进程以自身 uid 写激活标记——FPA 场景下这是唯一无 root 可靠通道。
    private static void pingSelfActive(final Activity act) {
        if (selfPinged || act == null) return;
        selfPinged = true;
        try {
            Uri uri = Uri.parse("content://com.dsmod.probe.status");
            android.os.Bundle r = act.getContentResolver().call(uri, "ping", null, null);
            log("selfActive ping ok=" + (r != null && r.getBoolean("ok")));
        } catch (Throwable t) {
            // 多为 Android 11+ 包可见性过滤（DeepSeek 未在 <queries> 声明模块）；记录以便排查
            selfPinged = false;
            log("selfActive ping failed: " + t);
        }
    }

    private void markSelfActive(ClassLoader cl) {
        try {
            Class<?> a = cl.loadClass("com.dsmod.probe.SettingsActivity");
            for (Method m : a.getDeclaredMethods()) {
                if (m.getName().equals("isModuleActive")) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            param.setResult(Boolean.TRUE);
                        }
                    });
                }
            }
        } catch (Throwable ignored) {}
        // 二次判据：在模块自身进程写一个新鲜的激活标记，SettingsActivity 读它兜底
        try {
            File mf = new File(SELF_ACTIVE_MARK);
            File dir = mf.getParentFile();
            if (dir != null && !dir.exists()) dir.mkdirs();
            FileWriter w = new FileWriter(mf, false);
            w.write(String.valueOf(System.currentTimeMillis()));
            w.close();
        } catch (Throwable ignored) {}
    }

    // 首次注入 DeepSeek 时弹出免责声明：拒绝退出，同意后写标记不再弹
    private void maybeShowDisclaimer(final Activity act) {
        if (disclaimerHandled) return;
        try {
            if (new File(DISCLAIMER_FILE).exists()) { disclaimerHandled = true; return; }
        } catch (Throwable ignored) {}
        disclaimerHandled = true;
        if (act == null || act.isFinishing()) return;
        act.runOnUiThread(new Runnable() {
            @Override public void run() {
                try {
                    String msg =
                        "本模块（Deekseep）通过 Xposed 框架修改 DeepSeek 的运行行为，使用前请知悉：\n\n"
                        + "• 风险自担：因使用本模块产生的一切后果，均由你本人承担。\n"
                        + "• 封号风险：修改客户端行为可能违反 DeepSeek 用户协议，账号存在被限制或封禁的风险。\n"
                        + "• 数据风险：注入过程可能影响消息、历史记录等数据，请自行备份。\n"
                        + "• 恶意用途：本模块仅供个人学习与研究，切勿用于任何违法或恶意行为。\n\n"
                        + "点击“同意”表示你已阅读并接受上述风险；点击“拒绝”将退出 DeepSeek。";
                    new android.app.AlertDialog.Builder(act)
                        .setTitle("Deekseep 免责声明")
                        .setMessage(msg)
                        .setCancelable(false)
                        .setPositiveButton("同意", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int which) {
                                try {
                                    FileWriter w = new FileWriter(DISCLAIMER_FILE, false);
                                    w.write(String.valueOf(System.currentTimeMillis()));
                                    w.close();
                                } catch (Throwable ignored) {}
                                d.dismiss();
                            }
                        })
                        .setNegativeButton("拒绝", new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int which) {
                                try { d.dismiss(); } catch (Throwable ignored) {}
                                try { act.finishAffinity(); } catch (Throwable ignored) {}
                                android.os.Process.killProcess(android.os.Process.myPid());
                                System.exit(0);
                            }
                        })
                        .show();
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
                existing.setVisibility(View.VISIBLE);
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
}
