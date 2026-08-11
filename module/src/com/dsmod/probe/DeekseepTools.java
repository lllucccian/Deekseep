package com.dsmod.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Deekseep 聊天数据工具箱（全部基于已验证的 ChatEditorUi 数据层 + 纯 Android 框架 API，
 * 不依赖任何混淆符号，因此对 build 变化免疫）。五个增强功能：
 *  1) 导出会话为 Markdown   2) 全局搜索   3) 会话数据统计
 *  4) 数据库备份(手动"立即备份" + 自动备份开关)
 */
public final class DeekseepTools {

    static final String DB_DIR = "/data/data/com.deepseek.chat/databases";
    // 自动备份写应用内部目录（无需存储权限，重装应用前用手动备份到外部目录）
    static final String AUTO_BACKUP_DIR = "/data/data/com.deepseek.chat/files/deekseep_backup";

    private DeekseepTools() {}

    // ── 通用：后台跑一个返回提示串的任务，完成后在 UI 线程 Toast ──────────
    interface Job { String run() throws Throwable; }

    private static void runBg(final Activity act, final Job job) {
        new Thread(new Runnable() {
            public void run() {
                String msg;
                try { msg = job.run(); } catch (Throwable t) { msg = "失败: " + t; }
                final String fmsg = msg;
                try {
                    act.runOnUiThread(new Runnable() {
                        public void run() {
                            try { UiLanguage.toast(act, fmsg, Toast.LENGTH_LONG).show(); } catch (Throwable ignored) {}
                        }
                    });
                } catch (Throwable ignored) {}
            }
        }).start();
    }

    private static List<File> chatDbs() {
        // 复用 ChatEditorUi.allDbs()：过滤官方写出的 deepseek_chat_*.db 等坏文件
        return ChatEditorUi.allDbs();
    }

    private static File exportBase(Activity act) {
        File ext = null;
        try { ext = act.getExternalFilesDir(null); } catch (Throwable ignored) {}
        File base = new File(ext != null ? ext : new File("/data/data/com.deepseek.chat/files"), "deekseep");
        base.mkdirs();
        return base;
    }

    private static String sanitize(String s) {
        if (s == null) return "untitled";
        String r = s.replaceAll("[/\\\\:*?\"<>|\\r\\n\\t]", "_").trim();
        if (r.length() == 0) r = "untitled";
        if (r.length() > 60) r = r.substring(0, 60);
        return r;
    }

    private static String shortId(String sid) {
        if (sid == null) return "x";
        return sid.length() > 8 ? sid.substring(0, 8) : sid;
    }

    private static String shortUuid(String uuid) {
        if (uuid == null) return "x";
        return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
    }

    private static void writeText(File f, String text) throws Throwable {
        FileOutputStream fos = new FileOutputStream(f);
        OutputStreamWriter w = new OutputStreamWriter(fos, "UTF-8");
        try { w.write(text); } finally { w.close(); }
    }

    private static String sessionMarkdown(SQLiteDatabase db, String title, String sid) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title == null || title.length() == 0 ? sid : title).append("\n\n");
        for (ChatEditorUi.Msg m : ChatEditorUi.loadThread(db, sid)) {
            sb.append("USER".equals(m.role)
                    ? UiLanguage.text("**用户**", "**User**")
                    : UiLanguage.text("**助手**", "**Assistant**")).append("\n\n");
            if (m.think != null && m.think.length() > 0) {
                for (String line : m.think.split("\n")) sb.append("> ").append(line).append("\n");
                sb.append("\n");
            }
            sb.append(m.body == null ? "" : m.body).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    // ── 功能 1：导出全部会话为 Markdown ─────────────────────────────────
    static void exportAll(final Activity act) {
        UiLanguage.toast(act, "正在导出…", Toast.LENGTH_SHORT).show();
        runBg(act, new Job() {
            public String run() throws Throwable {
                return exportAllForConsole(act, null);
            }
        });
    }

    static String exportAllForConsole(Activity act, TaskExecutionUi.Logger logger)
            throws Throwable {
        File base = exportBase(act);
        List<File> databases = chatDbs();
        line(logger, UiLanguage.text(act,
                "找到 " + databases.size() + " 个账号数据库",
                "Found " + databases.size() + " account databases"));
        int exported = 0;
        int skipped = 0;
        for (File file : databases) {
            SQLiteDatabase database = null;
            Cursor cursor = null;
            try {
                line(logger, UiLanguage.text(act, "读取：", "Reading: ") + file.getName());
                database = SQLiteDatabase.openDatabase(
                        file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                cursor = database.rawQuery(
                        "SELECT id,title FROM chat_session_list ORDER BY updated_at DESC", null);
                while (cursor.moveToNext()) {
                    String sid = cursor.getString(0);
                    String title = cursor.getString(1);
                    if (sid == null) continue;
                    String markdown = sessionMarkdown(database, title, sid);
                    writeText(new File(base,
                            sanitize(title != null && title.length() > 0 ? title : sid)
                                    + "_" + shortId(sid) + ".md"), markdown);
                    exported++;
                }
            } catch (Throwable error) {
                skipped++;
                line(logger, UiLanguage.text(act, "跳过：", "Skipped: ")
                        + file.getName() + " · " + safeMessage(error));
            } finally {
                if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
                if (database != null) try { database.close(); } catch (Throwable ignored) {}
            }
        }
        String result = exported == 0
                ? UiLanguage.text(act, "没有可导出的本地会话", "No local chats to export")
                : UiLanguage.text(act,
                "已导出 " + exported + " 个会话",
                "Exported " + exported + " chats");
        line(logger, result);
        if (skipped > 0) {
            line(logger, UiLanguage.text(act,
                    "有 " + skipped + " 个数据库未能读取",
                    skipped + " databases could not be read"));
        }
        line(logger, UiLanguage.text(act, "输出目录：", "Output: ") + base.getPath());
        String portable = exportPortableChatBackup(act, logger);
        return result + "\n" + base.getPath() + "\n" + portable;
    }

    private static final class BackupTarget {
        final Activity activity;
        final Uri uri;
        final File file;
        final OutputStream output;
        final String label;

        BackupTarget(Activity activity, Uri uri, File file, OutputStream output, String label) {
            this.activity = activity;
            this.uri = uri;
            this.file = file;
            this.output = output;
            this.label = label;
        }

        void finish() {
            if (Build.VERSION.SDK_INT >= 29 && uri != null) {
                try {
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                    activity.getContentResolver().update(uri, values, null, null);
                } catch (Throwable ignored) {}
            }
        }

        void abort() {
            try {
                if (uri != null) activity.getContentResolver().delete(uri, null, null);
                else if (file != null) file.delete();
            } catch (Throwable ignored) {}
        }
    }

    private static BackupTarget openPortableBackup(Activity act, String name) throws Throwable {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Deekseep");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = act.getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new java.io.IOException("cannot create download");
            OutputStream output = act.getContentResolver().openOutputStream(uri, "w");
            if (output == null) {
                act.getContentResolver().delete(uri, null, null);
                throw new java.io.IOException("cannot open download");
            }
            return new BackupTarget(act, uri, null, output,
                    "Download/Deekseep/" + name);
        }
        File file = new File(exportBase(act), name);
        return new BackupTarget(act, null, file, new FileOutputStream(file), file.getPath());
    }

    /** Adds a portable, lossless archive beside the human-readable Markdown export. */
    private static String exportPortableChatBackup(Activity act,
                                                    TaskExecutionUi.Logger logger)
            throws Throwable {
        String name = "deekseep_chat_" + stamp() + ".ds-chat-backup.zip";
        BackupTarget target = openPortableBackup(act, name);
        ZipOutputStream zip = null;
        int sessions = 0;
        try {
            zip = new ZipOutputStream(target.output);
            JSONObject manifest = new JSONObject();
            manifest.put("format", "deekseep-chat-backup");
            manifest.put("version", 1);
            manifest.put("created_at", System.currentTimeMillis());
            putZipText(zip, "manifest.json", manifest.toString());
            for (File file : chatDbs()) {
                SQLiteDatabase database = null;
                Cursor cursor = null;
                try {
                    database = SQLiteDatabase.openDatabase(
                            file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                    cursor = database.rawQuery("SELECT id FROM chat_session_list", null);
                    while (cursor.moveToNext()) {
                        String sid = cursor.getString(0);
                        if (!ChatEditorUi.validSid(sid)) continue;
                        try {
                            JSONObject snapshot = ChatEditorUi.snapshotSession(database, sid);
                            if (snapshot == null) continue;
                            String entry = "sessions/" + sanitize(snapshot.optString("db_id", ""))
                                    + "__" + sid + ".json";
                            putZipText(zip, entry, snapshot.toString());
                            sessions++;
                        } catch (Throwable skipped) {
                            line(logger, UiLanguage.text(act,
                                    "备份包跳过会话：", "Backup archive skipped chat: ") + sid);
                        }
                    }
                } finally {
                    if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
                    if (database != null) try { database.close(); } catch (Throwable ignored) {}
                }
            }
            zip.finish();
            zip.close();
            zip = null;
            target.finish();
            String status = UiLanguage.text(act,
                    "聊天备份包：" + target.label + "（" + sessions + " 个会话）",
                    "Chat backup: " + target.label + " (" + sessions + " chats)");
            line(logger, status);
            return status;
        } catch (Throwable error) {
            if (zip != null) try { zip.close(); } catch (Throwable ignored) {}
            target.abort();
            throw error;
        }
    }

    private static void putZipText(ZipOutputStream zip, String name, String value)
            throws Throwable {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(value.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    static void showChatImport(final Activity act, final Uri uri) {
        TaskExecutionUi.show(act, "导入聊天记录",
                "只覆盖服务器已重新下发、且会话 ID 与备份命中的记录。导入后请完整重启 DeepSeek。",
                new TaskExecutionUi.Task() {
                    @Override public String run(TaskExecutionUi.Logger logger) throws Throwable {
                        return importChatBackupForConsole(act, uri, logger);
                    }
                });
    }

    static String importChatBackupForConsole(Activity act, Uri uri,
                                             TaskExecutionUi.Logger logger) throws Throwable {
        if (uri == null) throw new IllegalArgumentException("missing backup file");
        Map<String, File> databases = new HashMap<>();
        for (File file : chatDbs()) databases.put(ChatEditorUi.uuidOf(file), file);
        InputStream raw = act.getContentResolver().openInputStream(uri);
        if (raw == null) throw new java.io.IOException("cannot open backup file");
        ZipInputStream zip = new ZipInputStream(raw);
        boolean manifestOk = false;
        int matched = 0;
        int missing = 0;
        int failed = 0;
        int entries = 0;
        long total = 0L;
        try {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 10000) throw new java.io.IOException("too many backup entries");
                if (entry.isDirectory()) continue;
                byte[] bytes = readZipEntry(zip, 32 * 1024 * 1024);
                total += bytes.length;
                if (total > 512L * 1024L * 1024L) {
                    throw new java.io.IOException("backup is too large");
                }
                if ("manifest.json".equals(entry.getName())) {
                    JSONObject manifest = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                    manifestOk = validBackupManifest(manifest);
                    continue;
                }
                if (!manifestOk || !portableSessionEntry(entry.getName())) continue;
                JSONObject snapshot = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
                String dbId = snapshot.optString("db_id", "");
                String sid = snapshot.optString("sid", "");
                File file = databases.get(dbId);
                if (file == null || !ChatEditorUi.validSid(sid)) {
                    missing++;
                    continue;
                }
                SQLiteDatabase database = null;
                try {
                    database = SQLiteDatabase.openDatabase(
                            file.getPath(), null, SQLiteDatabase.OPEN_READWRITE);
                    if (ChatEditorUi.overwriteMatchingSession(database, snapshot)) matched++;
                    else missing++;
                } catch (Throwable error) {
                    failed++;
                    line(logger, UiLanguage.text(act,
                            "覆盖失败：", "Overwrite failed: ") + sid + " · "
                            + safeMessage(error));
                } finally {
                    if (database != null) try { database.close(); } catch (Throwable ignored) {}
                }
            }
        } finally {
            try { zip.close(); } catch (Throwable ignored) {}
        }
        if (!manifestOk) throw new java.io.IOException("not a Deekseep chat backup");
        String summary = UiLanguage.text(act,
                "已覆盖 " + matched + " 个命中会话；未命中 " + missing
                        + " 个；失败 " + failed + " 个。请完整重启 DeepSeek。",
                "Overwrote " + matched + " matching chats; " + missing
                        + " unmatched; " + failed + " failed. Fully restart DeepSeek.");
        line(logger, summary);
        return summary;
    }

    static boolean validBackupManifest(JSONObject manifest) {
        return manifest != null && "deekseep-chat-backup".equals(
                manifest.optString("format")) && manifest.optInt("version") == 1;
    }

    static boolean portableSessionEntry(String name) {
        if (name == null || !name.startsWith("sessions/") || !name.endsWith(".json")
                || name.contains("..") || name.indexOf('\\') >= 0) return false;
        String leaf = name.substring("sessions/".length(), name.length() - 5);
        return leaf.length() > 4 && leaf.indexOf('/') < 0;
    }

    private static byte[] readZipEntry(ZipInputStream zip, int limit) throws Throwable {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int count;
        while ((count = zip.read(buffer)) >= 0) {
            if (count == 0) continue;
            total += count;
            if (total > limit) throw new java.io.IOException("backup entry is too large");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    // ── 功能 2：全局搜索聊天记录（圆角 DeepSeek 风格 + 点击结果跳转到消息）──
    static final class Hit {
        String dbPath, sid, title, role, snippet; long msgId; int hlStart, hlLen;
    }

    private static int dp(Activity a, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, a.getResources().getDisplayMetrics()));
    }

    static void showSearch(final Activity act) {
        ChatSearchUi.show(act);
    }

    private static void showSearchLegacy(final Activity act) {
        final boolean dark = DeekseepUi.isDark(act);
        final int card = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int sub  = dark ? 0xFF9A9A9E : 0xFF888888;

        final Dialog dlg = new Dialog(act);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(act, 20), dp(act, 18), dp(act, 20), dp(act, 14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(card); bg.setCornerRadius(dp(act, 22));
        box.setBackground(bg);

        TextView h = new TextView(act);
        h.setText("搜索聊天记录");
        h.setTextColor(text); h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        box.addView(h);

        final EditText input = new EditText(act);
        input.setHint("输入关键词");
        input.setTextColor(text); input.setHintTextColor(sub);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        GradientDrawable ib = new GradientDrawable();
        ib.setColor(dark ? 0xFF1F1F22 : 0xFFF2F3F5); ib.setCornerRadius(dp(act, 12));
        input.setBackground(ib);
        input.setPadding(dp(act, 12), dp(act, 10), dp(act, 12), dp(act, 10));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ilp.topMargin = dp(act, 14);
        box.addView(input, ilp);

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(act, 14);
        box.addView(btns, blp);

        TextView cancel = pillButton(act, "取消", sub, 0);
        cancel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dlg.dismiss(); } });
        btns.addView(cancel);

        TextView go = pillButton(act, "搜索", 0xFFFFFFFF, DeekseepUi.BRAND);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        glp.leftMargin = dp(act, 8);
        go.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String kw = input.getText().toString().trim();
                if (kw.length() == 0) return;
                dlg.dismiss();
                runSearch(act, kw);
            } });
        btns.addView(go, glp);

        UiLanguage.localizeTree(act, box);
        dlg.setContentView(box);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (act.getResources().getDisplayMetrics().widthPixels * 0.86f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dlg.show();
    }

    private static TextView pillButton(Activity act, String label, int fg, int bgColor) {
        TextView tv = new TextView(act);
        tv.setText(UiLanguage.dynamic(act, label));
        tv.setTextColor(fg);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(act, 18), dp(act, 8), dp(act, 18), dp(act, 8));
        tv.setClickable(true);
        if (bgColor != 0) {
            GradientDrawable g = new GradientDrawable();
            g.setColor(bgColor); g.setCornerRadius(dp(act, 18));
            tv.setBackground(g);
        }
        return tv;
    }

    private static void runSearch(final Activity act, final String kw) {
        UiLanguage.toast(act, "搜索中…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                final List<Hit> hits = new ArrayList<Hit>();
                String low = kw.toLowerCase(Locale.getDefault());
                for (File f : chatDbs()) {
                    SQLiteDatabase d = null; Cursor c = null;
                    try {
                        d = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                        c = d.rawQuery("SELECT id,title FROM chat_session_list ORDER BY updated_at DESC", null);
                        while (c.moveToNext() && hits.size() < 200) {
                            String sid = c.getString(0), title = c.getString(1);
                            if (sid == null) continue;
                            for (ChatEditorUi.Msg m : ChatEditorUi.loadThread(d, sid)) {
                                String body = m.body == null ? "" : m.body;
                                int idx = body.toLowerCase(Locale.getDefault()).indexOf(low);
                                if (idx < 0) continue;
                                int s = Math.max(0, idx - 24), e = Math.min(body.length(), idx + kw.length() + 40);
                                String snip = body.substring(s, e).replaceAll("\\s+", " ");
                                int rel = body.substring(s, idx).replaceAll("\\s+", " ").length();
                                Hit hit = new Hit();
                                hit.dbPath = f.getPath(); hit.sid = sid;
                                hit.title = (title == null || title.length() == 0) ? "未命名对话" : title;
                                hit.role = m.role; hit.msgId = m.id; hit.snippet = snip;
                                hit.hlStart = rel; hit.hlLen = kw.length();
                                hits.add(hit);
                                if (hits.size() >= 200) break;
                            }
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        if (c != null) try { c.close(); } catch (Throwable ig) {}
                        if (d != null) try { d.close(); } catch (Throwable ig) {}
                    }
                }
                act.runOnUiThread(new Runnable() {
                    public void run() { showSearchResults(act, kw, hits); }
                });
            }
        }).start();
    }

    private static void showSearchResults(final Activity act, String kw, final List<Hit> hits) {
        final boolean dark = DeekseepUi.isDark(act);
        final int card = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int rowBg = dark ? 0xFF1F1F22 : 0xFFF6F7F9;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int sub  = dark ? 0xFF9A9A9E : 0xFF888888;
        final int hl   = dark ? 0x66C08A2E : 0x66FFE08A;

        final Dialog dlg = new Dialog(act);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 12));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(card); bg.setCornerRadius(dp(act, 22));
        box.setBackground(bg);

        TextView h = new TextView(act);
        h.setText(hits.isEmpty()
                ? UiLanguage.text(act, "未找到「" + kw + "」", "No results for “" + kw + "”")
                : UiLanguage.text(act, "「" + kw + "」命中 " + hits.size() + " 条",
                        hits.size() + " results for “" + kw + "”"));
        h.setTextColor(text); h.setTypeface(Typeface.DEFAULT_BOLD);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        h.setPadding(dp(act, 4), 0, dp(act, 4), dp(act, 10));
        box.addView(h);

        ScrollView sv = new ScrollView(act);
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        int maxH = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.6f);
        LinearLayout.LayoutParams svlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                hits.size() > 6 ? maxH : ViewGroup.LayoutParams.WRAP_CONTENT);
        box.addView(sv, svlp);

        for (final Hit hit : hits) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable rb = new GradientDrawable();
            rb.setColor(rowBg); rb.setCornerRadius(dp(act, 12));
            row.setBackground(rb);
            row.setPadding(dp(act, 12), dp(act, 10), dp(act, 12), dp(act, 10));
            row.setClickable(true);

            TextView tt = new TextView(act);
            tt.setText(("USER".equals(hit.role)
                    ? UiLanguage.text(act, "我 · ", "Me · ") : "AI · ") + hit.title);
            tt.setTextColor(sub); tt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tt.setSingleLine(true); tt.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(tt);

            TextView sn = new TextView(act);
            SpannableStringBuilder ssb = new SpannableStringBuilder(hit.snippet);
            int a = Math.max(0, Math.min(hit.hlStart, hit.snippet.length()));
            int b = Math.max(a, Math.min(hit.hlStart + hit.hlLen, hit.snippet.length()));
            if (b > a) {
                ssb.setSpan(new BackgroundColorSpan(hl), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), a, b, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            sn.setText(ssb);
            sn.setTextColor(text); sn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            sn.setMaxLines(2); sn.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams snlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            snlp.topMargin = dp(act, 4);
            row.addView(sn, snlp);

            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    dlg.dismiss();
                    ChatEditorUi.showAt(act, hit.dbPath, hit.sid, hit.msgId);
                } });
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(act, 8);
            list.addView(row, rlp);
        }

        TextView close = pillButton(act, "关闭", sub, 0);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dlg.dismiss(); } });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.END; clp.topMargin = dp(act, 4);
        box.addView(close, clp);

        UiLanguage.localizeTree(act, box);
        dlg.setContentView(box);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0x00000000));
            w.setLayout((int) (act.getResources().getDisplayMetrics().widthPixels * 0.9f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dlg.show();
    }

    // ── 功能 3：数据库备份（手动 + 自动）────────────────────────────────
    static void backupNow(final Activity act) {
        UiLanguage.toast(act, "正在备份…", Toast.LENGTH_SHORT).show();
        runBg(act, new Job() {
            public String run() throws Throwable {
                return backupForConsole(act, null);
            }
        });
    }

    static String backupForConsole(Activity act, TaskExecutionUi.Logger logger)
            throws Throwable {
        File base = new File(exportBase(act), "backup_" + stamp());
        line(logger, UiLanguage.text(act,
                "正在扫描聊天数据库", "Scanning chat databases"));
        int count = doBackupTo(base);
        if (count == 0) {
            line(logger, UiLanguage.text(act,
                    "没有可备份的数据库", "No databases to back up"));
            return UiLanguage.text(act, "没有可备份的数据库", "No databases to back up");
        }
        String result = UiLanguage.text(act,
                "已备份 " + count + " 个数据库",
                "Backed up " + count + " databases");
        line(logger, result);
        line(logger, UiLanguage.text(act, "备份目录：", "Backup directory: ")
                + base.getPath());
        return result + "\n" + base.getPath();
    }

    private static int doBackupTo(File dst) {
        dst.mkdirs();
        int n = 0;
        File[] fs = new File(DB_DIR).listFiles();
        if (fs != null) for (File f : fs) {
            String nm = f.getName();
            if (f.isFile() && nm.startsWith("deepseek_chat") && nm.endsWith(".db")) {
                String uuid = nm.startsWith("deepseek_chat_")
                        ? nm.substring("deepseek_chat_".length(), nm.length() - ".db".length())
                        : nm.substring("deepseek_chat".length(), nm.length() - ".db".length());
                if (!ChatEditorUi.validDbUuid(uuid)) continue;
                try { copyFile(f, new File(dst, nm)); n++; } catch (Throwable ignored) {}
            }
        }
        return n;
    }

    private static void copyFile(File src, File dst) throws Throwable {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        try {
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) out.write(buf, 0, r);
        } finally {
            try { in.close(); } catch (Throwable ig) {}
            try { out.close(); } catch (Throwable ig) {}
        }
    }

    /** 每次应用启动时调用（后台线程）：开启自动备份且距上次 >24h 才执行，写内部目录。 */
    static void maybeAutoBackup() {
        try {
            if (!Main.isAutoBackup()) return;
            File root = new File(AUTO_BACKUP_DIR);
            long newest = 0;
            File[] subs = root.listFiles();
            if (subs != null) for (File s : subs) if (s.lastModified() > newest) newest = s.lastModified();
            if (System.currentTimeMillis() - newest < 24L * 3600 * 1000) return;
            File dst = new File(root, stamp());
            int n = doBackupTo(dst);
            // 只保留最近 5 份
            File[] all = root.listFiles();
            if (all != null && all.length > 5) {
                java.util.Arrays.sort(all, new java.util.Comparator<File>() {
                    public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
                });
                for (int i = 0; i < all.length - 5; i++) deleteRec(all[i]);
            }
            Main.log("auto backup done: " + n + " dbs -> " + dst.getName());
        } catch (Throwable t) {
            try { Main.log("auto backup failed: " + t); } catch (Throwable ignored) {}
        }
    }

    private static void deleteRec(File f) {
        if (f == null) return;
        File[] cs = f.listFiles();
        if (cs != null) for (File c : cs) deleteRec(c);
        f.delete();
    }

    // ── 功能 4：会话数据统计 ────────────────────────────────────────────
    static void showStats(final Activity act) {
        UiLanguage.toast(act, "统计中…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                final String text = statisticsForConsole(act, null);
                act.runOnUiThread(new Runnable() {
                    public void run() { showScrollDialog(act,
                            UiLanguage.text(act, "会话数据统计", "Chat data statistics"), text); }
                });
            }
        }).start();
    }

    static String statisticsForConsole(Activity act, TaskExecutionUi.Logger logger) {
        Map<String, String> accounts = ChatEditorUi.loadAccountLabels();
        List<File> databases = chatDbs();
        int sessions = 0;
        int messages = 0;
        long characters = 0;
        StringBuilder perAccount = new StringBuilder();
        line(logger, UiLanguage.text(act,
                "正在读取 " + databases.size() + " 个账号",
                "Reading " + databases.size() + " accounts"));
        for (File file : databases) {
            String label = accounts.get(ChatEditorUi.uuidOf(file));
            if (label == null) label = shortUuid(ChatEditorUi.uuidOf(file));
            int accountSessions = 0;
            int accountMessages = 0;
            SQLiteDatabase database = null;
            Cursor cursor = null;
            try {
                database = SQLiteDatabase.openDatabase(
                        file.getPath(), null, SQLiteDatabase.OPEN_READONLY);
                cursor = database.rawQuery("SELECT id FROM chat_session_list", null);
                List<String> ids = new ArrayList<String>();
                while (cursor.moveToNext()) {
                    if (cursor.getString(0) != null) ids.add(cursor.getString(0));
                }
                for (String sid : ids) {
                    accountSessions++;
                    for (ChatEditorUi.Msg message : ChatEditorUi.loadThread(database, sid)) {
                        accountMessages++;
                        if (message.body != null) characters += message.body.length();
                        if (message.think != null) characters += message.think.length();
                    }
                }
            } catch (Throwable error) {
                line(logger, UiLanguage.text(act, "读取失败：", "Read failed: ")
                        + label + " · " + safeMessage(error));
            } finally {
                if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
                if (database != null) try { database.close(); } catch (Throwable ignored) {}
            }
            sessions += accountSessions;
            messages += accountMessages;
            String accountLine = "• " + label + UiLanguage.text(act, "：", ": ")
                    + accountSessions + UiLanguage.text(act, " 会话 / ", " chats / ")
                    + accountMessages + UiLanguage.text(act, " 消息", " messages");
            perAccount.append(accountLine).append('\n');
            line(logger, accountLine);
        }
        String result = UiLanguage.text(act,
                "本地账号数：" + databases.size() + "\n"
                        + "会话总数：" + sessions + "\n"
                        + "消息总数：" + messages + "\n"
                        + "正文+思考总字数：" + characters + "\n\n"
                        + "按账号：\n" + perAccount,
                "Local accounts: " + databases.size() + "\n"
                        + "Total chats: " + sessions + "\n"
                        + "Total messages: " + messages + "\n"
                        + "Body + reasoning characters: " + characters + "\n\n"
                        + "By account:\n" + perAccount);
        line(logger, UiLanguage.text(act,
                "汇总：" + sessions + " 个会话，" + messages + " 条消息，"
                        + characters + " 字",
                "Summary: " + sessions + " chats, " + messages + " messages, "
                        + characters + " characters"));
        return result;
    }

    private static void line(TaskExecutionUi.Logger logger, String value) {
        if (logger != null) logger.line(UiLanguageCatalog.toEnglish(value));
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "unknown";
        String message = error.getMessage();
        return message == null || message.trim().length() == 0
                ? error.getClass().getSimpleName() : message.trim();
    }

    // ── 通用滚动结果对话框 ─────────────────────────────────────────────
    private static void showScrollDialog(Activity act, String title, String text) {
        try {
            ScrollView sv = new ScrollView(act);
            TextView tv = new TextView(act);
            int pad = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16,
                    act.getResources().getDisplayMetrics());
            tv.setPadding(pad, pad, pad, pad);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setTextIsSelectable(true);
            tv.setText(text);
            sv.addView(tv);
            new AlertDialog.Builder(act).setTitle(UiLanguage.dynamic(act, title)).setView(sv)
                    .setPositiveButton(UiLanguage.text(act, "关闭", "Close"), null).show();
        } catch (Throwable ignored) {}
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
    }
}
