package com.dsmod.probe;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Searchable, per-host cached Activity/Compose-route index for Closed code249/code257. */
final class HostNavigationUi {
    private static final String HOST = "com.deepseek.chat";
    private static volatile List<Item> memory;
    private static volatile String memoryGeneration;

    private HostNavigationUi() {}

    private static boolean supportedBuild() {
        return HostCompat.isV236() || HostCompat.isV241();
    }

    static void show(final Activity activity) {
        if (activity == null || !supportedBuild()) return;
        final String generation = HostCompat.generationName();
        final boolean dark = DeekseepUi.isDark(activity);
        final int background = dark ? 0xFF1B1B1D : 0xFFF6F7F9;
        final int surface = dark ? 0xFF252528 : 0xFFFFFFFF;
        final int ink = dark ? 0xFFF0F0F2 : 0xFF17181A;
        final int secondary = dark ? 0xFFAAAAB0 : 0xFF6E7279;
        final int border = dark ? 0xFF3B3B40 : 0xFFE2E4E8;

        final Dialog dialog = new Dialog(activity,
                android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 8), statusBarHeight(activity), dp(activity, 16), 0);
        bar.setBackgroundColor(surface);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 56) + statusBarHeight(activity)));

        TextView back = text(activity, "‹", 30, ink, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> DeekseepUi.slideOutAndDismiss(dialog, root));
        bar.addView(back, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));
        TextView title = text(activity, "界面导航管理器", 18, ink, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 8);
        bar.addView(title, titleParams);

        LinearLayout body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), 0);
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView summary = text(activity,
                "正在后台检索 " + generation + " 的 Activity 与 Compose Route…",
                12, secondary, false);
        summary.setLineSpacing(dp(activity, 2), 1f);
        body.addView(summary);

        // Module actions, not host pages: they sit above the index so they are reachable without a
        // search. Each owns its whole flow, so this page steps aside first.
        LinearLayout compactCard = actionCard(activity, surface, border, ink, secondary,
                "压缩对话",
                "把当前对话折叠成摘要并在新对话里继续；摘要可编辑、可放入任意对话。",
                () -> {
                    DeekseepUi.slideOutAndDismiss(dialog, root);
                    ChatCompactionEntryUi.showCompactConfirm(activity);
                });
        body.addView(compactCard, actionCardParams(activity));

        LinearLayout recordsCard = actionCard(activity, surface, border, ink, secondary,
                "压缩记录",
                "查看、编辑、放入当前对话或删除已保存的摘要。",
                () -> {
                    DeekseepUi.slideOutAndDismiss(dialog, root);
                    ChatCompactionEntryUi.showRecords(activity);
                });
        body.addView(recordsCard, actionCardParams(activity));

        final EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("搜索页面、Route 或 Activity");
        search.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        search.setTextColor(ink);
        search.setHintTextColor(secondary);
        search.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        GradientDrawable searchBg = outline(surface, border, dp(activity, 10), dp(activity, 1));
        search.setBackground(searchBg);
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 46));
        searchParams.topMargin = dp(activity, 12);
        body.addView(search, searchParams);

        ScrollView scroll = new ScrollView(activity);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(activity, 12);
        body.addView(scroll, scrollParams);

        final List<Item>[] current = new List[]{Collections.<Item>emptyList()};
        Runnable render = () -> render(activity, dialog, list, current[0],
                search.getText() == null ? "" : search.getText().toString(),
                surface, ink, secondary, border);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                render.run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(background));
        }
        DeekseepUi.openWithSlide(dialog, root);
        DeekseepUi.trackChildDialog(dialog);

        List<Item> ready = generation.equals(memoryGeneration) ? memory : null;
        if (ready != null) {
            current[0] = ready;
            summary.setText(summary(ready, true));
            render.run();
            return;
        }
        new Thread(() -> {
            List<Item> indexed = null;
            String failure = null;
            try {
                indexed = loadOrIndex(activity);
                memory = indexed;
                memoryGeneration = generation;
            } catch (Throwable error) {
                failure = safe(error);
            }
            final List<Item> result = indexed;
            final String problem = failure;
            activity.runOnUiThread(() -> {
                if (!dialog.isShowing()) return;
                if (result == null) {
                    summary.setText("索引失败：" + problem);
                    return;
                }
                current[0] = result;
                summary.setText(summary(result, false));
                render.run();
            });
        }, "Deekseep-host-navigation-index").start();
    }

    private static void render(Activity activity, Dialog dialog, LinearLayout list,
                               List<Item> items, String query, int surface, int ink,
                               int secondary, int border) {
        list.removeAllViews();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Item item : items) {
            String haystack = (item.title + " " + item.target + " " + item.kind + " "
                    + policyLabel(item.policy)).toLowerCase(Locale.ROOT);
            if (needle.length() > 0 && !haystack.contains(needle)) continue;
            View row = row(activity, item, surface, ink, secondary, border);
            if (launchable(item)) {
                row.setOnClickListener(v -> launch(activity, dialog, item));
            }
            list.addView(row);
            shown++;
        }
        if (shown == 0) {
            TextView empty = text(activity,
                    items.isEmpty() ? "等待索引结果" : "没有匹配页面", 14, secondary, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(activity, 32), 0, dp(activity, 32));
            list.addView(empty);
        }
    }

    private static View row(Activity activity, Item item, int surface, int ink,
                            int secondary, int border) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        row.setBackground(outline(surface, border, dp(activity, 10), dp(activity, 1)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(activity, 8);
        row.setLayoutParams(params);

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        int titleColor = HostNavigationCatalog.HIGH_RISK.equals(item.policy)
                ? 0xFFE53935 : ink;
        TextView title = text(activity, item.title, 15, titleColor, true);
        heading.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        int policyColor = HostNavigationCatalog.DIRECT.equals(item.policy)
                ? 0xFF3D8B62 : HostNavigationCatalog.HIGH_RISK.equals(item.policy)
                ? 0xFFE53935 : HostNavigationCatalog.BLOCKED.equals(item.policy)
                ? 0xFFC45757 : secondary;
        TextView status = text(activity, policyLabel(item.policy), 11, policyColor, true);
        status.setPadding(dp(activity, 8), dp(activity, 3), dp(activity, 8), dp(activity, 3));
        status.setBackground(outline(Color.TRANSPARENT, policyColor,
                dp(activity, 9), dp(activity, 1)));
        heading.addView(status);
        row.addView(heading);

        TextView target = text(activity, item.target, 11, secondary, false);
        target.setTypeface(Typeface.MONOSPACE);
        target.setPadding(0, dp(activity, 5), 0, 0);
        row.addView(target);
        TextView detail = text(activity, item.kind + " · " + item.detail,
                12, secondary, false);
        detail.setPadding(0, dp(activity, 3), 0, 0);
        row.addView(detail);
        row.setAlpha(launchable(item) ? 1f : 0.72f);
        row.setClickable(launchable(item));
        row.setMinimumHeight(dp(activity, 64));
        return row;
    }

    private static void launch(Activity activity, Dialog dialog, Item item) {
        if ("Activity".equals(item.kind)) {
            if (!"com.deepseek.chat.MainActivity".equals(item.target)) return;
            try {
                Intent intent = new Intent();
                intent.setComponent(new ComponentName(HOST, item.target));
                intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                activity.startActivity(intent);
                DeekseepUi.dismissForNativeNavigation();
            } catch (Throwable error) {
                Toast.makeText(activity, "无法启动页面：" + safe(error), Toast.LENGTH_LONG).show();
            }
            return;
        }
        if (HostNavigationCatalog.HIGH_RISK.equals(item.policy)) {
            DeekseepUi.showCustomConfirm(activity, "高风险操作",
                    "此操作涉及高风险。进入后请再次核对账号，避免误删。",
                    "取消", "继续进入", true, null,
                    () -> navigateRoute(activity, dialog, item));
            return;
        }
        navigateRoute(activity, dialog, item);
    }

    private static void navigateRoute(Activity activity, Dialog dialog, Item item) {
        HostNavigationBridge.navigate(activity, item.target,
                (success, detail) -> {
                    if (success) DeekseepUi.dismissForNativeNavigation();
                    Toast.makeText(activity,
                            success ? "已切换到“" + detail + "”" : detail,
                            success ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG).show();
                });
    }

    private static boolean launchable(Item item) {
        return item != null && (HostNavigationCatalog.DIRECT.equals(item.policy)
                || HostNavigationCatalog.HIGH_RISK.equals(item.policy));
    }

    private static List<Item> loadOrIndex(Activity activity) throws Exception {
        PackageManager pm = activity.getPackageManager();
        PackageInfo info = pm.getPackageInfo(HOST, PackageManager.GET_ACTIVITIES);
        File apk = new File(info.applicationInfo.sourceDir);
        String digest = sha256(apk);
        long versionCode = android.os.Build.VERSION.SDK_INT >= 28
                ? info.getLongVersionCode() : info.versionCode;
        File cache = new File(activity.getFilesDir(), HostCompat.isV236()
                ? "deekseep_host_navigation_code249_v1.json"
                : "deekseep_host_navigation_code257_v2.json");
        List<Item> cached = readCache(cache, digest, versionCode);
        if (cached != null) return cached;

        ArrayList<Item> out = new ArrayList<Item>();
        ActivityInfo[] activities = info.activities == null ? new ActivityInfo[0] : info.activities;
        for (ActivityInfo value : activities) {
            String name = value.name == null ? "" : value.name;
            String policy = "com.deepseek.chat.MainActivity".equals(name)
                    ? HostNavigationCatalog.DIRECT
                    : (name.endsWith("ShareProxyActivity") || name.endsWith("TablePreviewActivity"))
                    ? HostNavigationCatalog.PARAMETERS : HostNavigationCatalog.INTERNAL;
            if (!name.startsWith("com.deepseek.chat")) policy = HostNavigationCatalog.BLOCKED;
            out.add(new Item(activityTitle(name), name, "Activity", policy,
                    activityDetail(name, policy)));
        }

        Set<String> routes = new LinkedHashSet<String>();
        ZipFile archive = new ZipFile(apk);
        try {
            java.util.Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.matches("classes(\\d*)\\.dex")) continue;
                InputStream input = archive.getInputStream(entry);
                try { routes.addAll(HostNavigationCatalog.extractRoutes(input)); }
                finally { input.close(); }
            }
        } finally {
            archive.close();
        }
        for (String route : routes) {
            String policy = HostNavigationCatalog.policy(route);
            out.add(new Item(HostNavigationCatalog.title(route), route, "Compose Route", policy,
                    routeDetail(policy)));
        }
        Collections.sort(out, Comparator.comparingInt(HostNavigationUi::policyRank)
                .thenComparing(value -> value.title));
        writeCache(cache, digest, versionCode, out);
        return Collections.unmodifiableList(out);
    }

    private static int policyRank(Item item) {
        if (HostNavigationCatalog.DIRECT.equals(item.policy)) return 0;
        if (HostNavigationCatalog.HIGH_RISK.equals(item.policy)) return 1;
        if (HostNavigationCatalog.PARAMETERS.equals(item.policy)) return 2;
        if (HostNavigationCatalog.INTERNAL.equals(item.policy)) return 3;
        return 4;
    }

    private static List<Item> readCache(File file, String digest, long versionCode) {
        try {
            if (!file.isFile() || file.length() > 512 * 1024) return null;
            byte[] bytes = new byte[(int) file.length()];
            FileInputStream input = new FileInputStream(file);
            int offset = 0;
            try {
                while (offset < bytes.length) {
                    int read = input.read(bytes, offset, bytes.length - offset);
                    if (read < 0) break;
                    offset += read;
                }
            } finally { input.close(); }
            if (offset != bytes.length) return null;
            JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
            if (!digest.equals(root.optString("apk_sha256"))
                    || versionCode != root.optLong("version_code", -1L)) return null;
            JSONArray values = root.optJSONArray("items");
            if (values == null) return null;
            ArrayList<Item> out = new ArrayList<Item>();
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                out.add(new Item(value.optString("title"), value.optString("target"),
                        value.optString("kind"), value.optString("policy"),
                        value.optString("detail")));
            }
            return Collections.unmodifiableList(out);
        } catch (Throwable ignored) { return null; }
    }

    private static void writeCache(File file, String digest, long versionCode,
                                   List<Item> items) throws Exception {
        JSONObject root = new JSONObject();
        root.put("apk_sha256", digest);
        root.put("version_code", versionCode);
        JSONArray values = new JSONArray();
        for (Item item : items) values.put(new JSONObject()
                .put("title", item.title).put("target", item.target)
                .put("kind", item.kind).put("policy", item.policy)
                .put("detail", item.detail));
        root.put("items", values);
        File temporary = new File(file.getParentFile(), file.getName() + ".tmp");
        FileOutputStream output = new FileOutputStream(temporary, false);
        try {
            output.write(root.toString().getBytes(StandardCharsets.UTF_8));
            output.flush();
            output.getFD().sync();
        } finally { output.close(); }
        if (!temporary.renameTo(file)) {
            if (file.exists() && !file.delete()) throw new IllegalStateException("旧导航缓存不可替换");
            if (!temporary.renameTo(file)) throw new IllegalStateException("导航缓存发布失败");
        }
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
        } finally { input.close(); }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) out.append(String.format(Locale.US, "%02x", value));
        return out.toString();
    }

    private static String summary(List<Item> items, boolean cachedInMemory) {
        int activities = 0, routes = 0, direct = 0, confirmed = 0;
        for (Item item : items) {
            if ("Activity".equals(item.kind)) activities++; else routes++;
            if (HostNavigationCatalog.DIRECT.equals(item.policy)) direct++;
            if (HostNavigationCatalog.HIGH_RISK.equals(item.policy)) confirmed++;
        }
        return "检索到 " + activities + " 个 Activity、" + routes + " 个 Compose Route；"
                + direct + " 个已验证可直接切换、" + confirmed + " 个需二次确认。"
                + (cachedInMemory ? " 已使用本次运行缓存。" : " 索引已按 APK 摘要缓存。");
    }

    private static String policyLabel(String policy) {
        if (HostNavigationCatalog.DIRECT.equals(policy)) return "可直接切换";
        if (HostNavigationCatalog.HIGH_RISK.equals(policy)) return "高风险 · 二次确认";
        if (HostNavigationCatalog.PARAMETERS.equals(policy)) return "需要参数";
        if (HostNavigationCatalog.INTERNAL.equals(policy)) return "内部/状态限制";
        return "已阻止/高风险";
    }

    private static String routeDetail(String policy) {
        if (HostNavigationCatalog.DIRECT.equals(policy)) {
            return HostCompat.generationName() + " 无参数 Route，点击直接导航";
        }
        if (HostNavigationCatalog.HIGH_RISK.equals(policy)) {
            return "已验证为 " + HostCompat.generationName() + " 无参数 Route；确认后可进入";
        }
        if (HostNavigationCatalog.PARAMETERS.equals(policy)) return "缺少宿主业务参数，仅收录不启动";
        if (HostNavigationCatalog.INTERNAL.equals(policy)) return "依赖登录或当前图状态，仅收录不启动";
        return "涉及破坏性操作，导航管理器禁止启动";
    }

    private static String activityTitle(String name) {
        if (name.endsWith("MainActivity")) return "DeepSeek 主界面";
        if (name.endsWith("ShareProxyActivity")) return "分享入口";
        if (name.endsWith("TablePreviewActivity")) return "表格预览 Activity";
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }

    private static String activityDetail(String name, String policy) {
        if (HostNavigationCatalog.DIRECT.equals(policy)) return "Manifest 入口，点击置前";
        if (HostNavigationCatalog.PARAMETERS.equals(policy)) return "需要 Intent/预览状态，仅收录不启动";
        if (HostNavigationCatalog.BLOCKED.equals(policy)) return "第三方或系统辅助 Activity，禁止启动";
        return "宿主内部辅助 Activity，仅收录不启动";
    }

    private static LinearLayout actionCard(Activity activity, int surface, int border,
            int ink, int secondary, String titleValue, String descValue, final Runnable action) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        card.setBackground(outline(surface, border, dp(activity, 12), dp(activity, 1)));
        card.setClickable(true);
        card.setFocusable(true);
        card.addView(text(activity, titleValue, 15, ink, true));
        TextView desc = text(activity, descValue, 12, secondary, false);
        desc.setLineSpacing(dp(activity, 2), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descParams.topMargin = dp(activity, 4);
        card.addView(desc, descParams);
        card.setOnClickListener(v -> action.run());
        return card;
    }

    private static LinearLayout.LayoutParams actionCardParams(Activity activity) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(activity, 12);
        return params;
    }

    private static TextView text(Activity activity, String value, float size,
                                 int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static GradientDrawable outline(int color, int stroke, float radius, int width) {
        GradientDrawable value = new GradientDrawable();
        value.setColor(color);
        value.setCornerRadius(radius);
        value.setStroke(width, stroke);
        return value;
    }

    private static int statusBarHeight(Activity activity) {
        int id = activity.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? activity.getResources().getDimensionPixelSize(id) : 0;
    }

    private static int dp(Activity activity, float value) {
        return DeekseepUi.dp(activity, value);
    }

    private static String safe(Throwable error) {
        String value = error == null ? "unknown" : error.getMessage();
        if (value == null || value.trim().length() == 0) value = error.getClass().getSimpleName();
        return value.length() > 180 ? value.substring(0, 180) : value;
    }

    private static final class Item {
        final String title;
        final String target;
        final String kind;
        final String policy;
        final String detail;

        Item(String title, String target, String kind, String policy, String detail) {
            this.title = title == null ? "" : title;
            this.target = target == null ? "" : target;
            this.kind = kind == null ? "" : kind;
            this.policy = policy == null ? HostNavigationCatalog.BLOCKED : policy;
            this.detail = detail == null ? "" : detail;
        }
    }
}
