package com.dsmod.probe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Original two-page standalone shell for the Deekseep module APK. */
public class SettingsActivity extends Activity {
    public static final String VERSION = BuildInfo.MODULE_VERSION;

    private static final String TARGET_PACKAGE = "com.deepseek.chat";
    private static final String REPOSITORY = "https://github.com/lllucccian/Deekseep";
    private static final int REQ_STORAGE = 0xD540;
    private static final int REQ_NOTIFICATIONS = 0xD541;
    private static final int REQ_EXPORT = 0xD542;
    private static final int REQ_IMPORT = 0xD543;

    private boolean renderedChinese;
    private boolean renderedDark;
    private boolean rendered;
    private int page;
    private int bg;
    private int card;
    private int cardAlt;
    private int text;
    private int muted;
    private int line;
    private int accent;
    private LinearLayout content;
    private TextView pageTitle;
    private TextView activationTitle;
    private TextView activationDetail;
    private TextView activationMark;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable activationStateChanged = new Runnable() {
        @Override public void run() { refreshActivationState(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiLanguage.refreshSystem(this);
        renderShell();
        ensureStoragePermission();
        ensureNotificationPermission();
        XposedActivationProvider.setStateListener(activationStateChanged);
        handler.postDelayed(activationStateChanged, 300L);
        handler.postDelayed(activationStateChanged, 1200L);
    }

    @Override protected void onResume() {
        super.onResume();
        UiLanguage.refreshSystem(this);
        boolean chinese = UiLanguage.isChinese(this);
        boolean dark = isDark();
        if (!rendered || chinese != renderedChinese || dark != renderedDark) renderShell();
        XposedActivationProvider.setStateListener(activationStateChanged);
        handler.post(activationStateChanged);
        handler.postDelayed(activationStateChanged, 500L);
        ensureNotificationPermission();
    }

    @Override protected void onDestroy() {
        XposedActivationProvider.setStateListener(null);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void renderShell() {
        rendered = true;
        renderedChinese = UiLanguage.isChinese(this);
        renderedDark = isDark();
        bg = renderedDark ? 0xFF101113 : 0xFFF4F6F8;
        card = renderedDark ? 0xFF1B1D21 : 0xFFFFFFFF;
        cardAlt = renderedDark ? 0xFF24272C : 0xFFF0F3F7;
        text = renderedDark ? 0xFFF2F3F5 : 0xFF17191D;
        muted = renderedDark ? 0xFF9AA0AA : 0xFF68707C;
        line = renderedDark ? 0xFF30343A : 0xFFE2E6EB;
        accent = renderedDark ? 0xFF7EA6FF : 0xFF315FBE;
        configureWindow();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        titleBar.setPadding(dp(22), dp(16), dp(22), dp(10));
        pageTitle = text("", 27, text, Typeface.create("sans-serif-medium", 0));
        titleBar.addView(pageTitle, matchWrap());
        root.addView(titleBar, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(6), dp(16), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        root.addView(bottomNavigation());
        setContentView(root);
        showPage(page);
    }

    private void showPage(int next) {
        page = next == 1 ? 1 : 0;
        if (content == null) return;
        content.removeAllViews();
        if (page == 0) showHome(); else showSettings();
    }

    private void showHome() {
        pageTitle.setText("Deekseep");
        LinearLayout hero = card();
        hero.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout mark = new FrameLayout(this);
        GradientDrawable markBg = rounded(accent, 18);
        mark.setBackground(markBg);
        TextView initial = text("D", 25, Color.WHITE,
                Typeface.create("sans-serif-medium", Typeface.BOLD));
        initial.setGravity(Gravity.CENTER);
        mark.addView(initial, new FrameLayout.LayoutParams(-1, -1));
        head.addView(mark, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(14), 0, 0, 0);
        copy.addView(text(UiLanguage.text(this, "模块运行状态", "Module status"),
                13, muted, Typeface.DEFAULT));
        activationTitle = text("", 23, text,
                Typeface.create("sans-serif-medium", Typeface.NORMAL));
        copy.addView(activationTitle);
        activationDetail = text("", 12, muted, Typeface.DEFAULT);
        copy.addView(activationDetail);
        head.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        activationMark = text("", 25, accent, Typeface.DEFAULT);
        activationMark.setGravity(Gravity.CENTER);
        head.addView(activationMark, new LinearLayout.LayoutParams(dp(42), dp(42)));
        hero.addView(head);
        content.addView(hero, cardLp());

        TextView section = sectionTitle(UiLanguage.text(this, "运行信息", "Runtime"));
        content.addView(section);
        LinearLayout info = card();
        info.addView(infoRow(UiLanguage.text(this, "DeepSeek 版本", "DeepSeek version"), deepSeekVersion()));
        info.addView(divider());
        info.addView(infoRow(UiLanguage.text(this, "模块版本", "Module version"), BuildInfo.MODULE_VERSION));
        info.addView(divider());
        info.addView(infoRow(UiLanguage.text(this, "构建渠道", "Build channel"),
                BuildInfo.PROTECTED_BUILD ? "Closed" : "Open"));
        info.addView(divider());
        info.addView(infoRow(UiLanguage.text(this, "编译时间", "Built"), BuildInfo.BUILD_DATE));
        content.addView(info, cardLp());

        content.addView(actionCard(ModuleGlyphView.HOME,
                UiLanguage.text(this, "打开 DeepSeek", "Open DeepSeek"),
                UiLanguage.text(this, "模块设置位于 DeepSeek 设置页", "Module settings are in DeepSeek settings"),
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        Intent launch = getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
                        if (launch != null) startActivity(launch);
                        else toast(UiLanguage.text(SettingsActivity.this, "未安装 DeepSeek", "DeepSeek is not installed"));
                    }
                }));
        content.addView(actionCard(ModuleGlyphView.SETTINGS,
                UiLanguage.text(this, "进程管理", "Process manager"),
                UiLanguage.text(this, "查看、冻结或终止 DeepSeek 与模块进程",
                        "Inspect, freeze, or stop DeepSeek and module processes"),
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        startActivity(new Intent(SettingsActivity.this,
                                ProcessManagerActivity.class));
                    }
                }));
        refreshActivationState();
    }

    private void showSettings() {
        pageTitle.setText(UiLanguage.text(this, "设置", "Settings"));
        content.addView(sectionTitle(UiLanguage.text(this, "配置", "Configuration")));
        LinearLayout config = card();
        config.addView(actionRow(ModuleGlyphView.EXPORT,
                UiLanguage.text(this, "导出配置", "Export configuration"),
                UiLanguage.text(this, "保存当前功能开关，不包含提示词、密钥和聊天", "Save feature switches without prompts, keys or chats"),
                new View.OnClickListener() { @Override public void onClick(View v) { chooseExport(); } }));
        config.addView(divider());
        config.addView(actionRow(ModuleGlyphView.IMPORT,
                UiLanguage.text(this, "导入配置", "Import configuration"),
                UiLanguage.text(this, "从配置文件恢复功能开关", "Restore feature switches from a config file"),
                new View.OnClickListener() { @Override public void onClick(View v) { chooseImport(); } }));
        content.addView(config, cardLp());

        content.addView(sectionTitle(UiLanguage.text(this, "项目", "Project")));
        LinearLayout project = card();
        project.addView(actionRow(ModuleGlyphView.SPONSOR,
                UiLanguage.text(this, "赞助开发", "Sponsor development"),
                UiLanguage.text(this, "支持更快地维护和适配", "Help speed up maintenance and compatibility work"),
                new View.OnClickListener() { @Override public void onClick(View v) { showSponsor(); } }));
        project.addView(divider());
        project.addView(actionRow(ModuleGlyphView.REPOSITORY,
                UiLanguage.text(this, "GitHub 仓库", "GitHub repository"), REPOSITORY,
                new View.OnClickListener() { @Override public void onClick(View v) { openUrl(REPOSITORY); } }));
        content.addView(project, cardLp());
    }

    private View bottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(26), dp(8), dp(26), dp(10));
        nav.setBackgroundColor(card);
        nav.addView(navItem(ModuleGlyphView.HOME,
                UiLanguage.text(this, "首页", "Home"), 0), new LinearLayout.LayoutParams(0, dp(58), 1f));
        nav.addView(navItem(ModuleGlyphView.SETTINGS,
                UiLanguage.text(this, "设置", "Settings"), 1), new LinearLayout.LayoutParams(0, dp(58), 1f));
        return nav;
    }

    private View navItem(int icon, String label, final int target) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        ModuleGlyphView glyph = new ModuleGlyphView(this, icon, target == page ? accent : muted);
        item.addView(glyph, new LinearLayout.LayoutParams(dp(25), dp(25)));
        TextView name = text(label, 11, target == page ? accent : muted, Typeface.DEFAULT);
        item.addView(name);
        item.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { showPage(target); renderShell(); }
        });
        return item;
    }

    private View actionCard(int icon, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout shell = card();
        shell.addView(actionRow(icon, title, subtitle, listener));
        LinearLayout.LayoutParams lp = cardLp();
        lp.topMargin = dp(10);
        shell.setLayoutParams(lp);
        return shell;
    }

    private View actionRow(int icon, String title, String subtitle, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(14), dp(15));
        ModuleGlyphView glyph = new ModuleGlyphView(this, icon, accent);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(27), dp(27)));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.setPadding(dp(14), 0, dp(8), 0);
        labels.addView(text(title, 16, text, Typeface.create("sans-serif-medium", 0)));
        labels.addView(text(subtitle, 12, muted, Typeface.DEFAULT));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = text("›", 25, muted, Typeface.DEFAULT);
        row.addView(arrow);
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(listener);
        return row;
    }

    private LinearLayout infoRow(String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));
        row.addView(text(label, 15, muted, Typeface.DEFAULT), new LinearLayout.LayoutParams(0, -2, 1f));
        TextView data = text(value, 14, text, Typeface.DEFAULT);
        data.setGravity(Gravity.END);
        row.addView(data);
        return row;
    }

    private void showSponsor() {
        final String sponsor = "https://afdian.com/a/lllucccian";
        new AlertDialog.Builder(this)
                .setTitle(UiLanguage.text(this, "赞助开发", "Sponsor development"))
                .setItems(new String[]{
                                UiLanguage.text(this, "通过爱发电赞助", "Sponsor via Afdian"),
                                UiLanguage.text(this, "通过微信赞助", "Sponsor via WeChat")},
                        new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(
                                    android.content.DialogInterface dialog, int which) {
                                if (which == 0) {
                                    try {
                                        startActivity(new Intent(Intent.ACTION_VIEW,
                                                Uri.parse(sponsor)));
                                    } catch (Throwable error) {
                                        Toast.makeText(SettingsActivity.this, sponsor,
                                                Toast.LENGTH_LONG).show();
                                    }
                                } else {
                                    showWechatSponsorCode();
                                }
                            }
                        })
                .setNegativeButton(UiLanguage.text(this, "取消", "Cancel"), null)
                .show();
    }

    private void showWechatSponsorCode() {
        int id = getResources().getIdentifier("sponsor_qr", "drawable", getPackageName());
        ImageView image = new ImageView(this);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setPadding(dp(12), dp(4), dp(12), 0);
        if (id != 0) image.setImageResource(id);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(UiLanguage.text(this, "微信赞赏码", "WeChat donation code"))
                .setMessage(UiLanguage.text(this, "感谢支持持续维护与适配。", "Thank you for supporting ongoing maintenance."))
                .setView(image)
                .setPositiveButton(UiLanguage.text(this, "完成", "Done"), null)
                .create();
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
    }

    private void chooseExport() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "deekseep-config-" + BuildInfo.MODULE_VERSION + ".json");
        startActivityForResult(intent, REQ_EXPORT);
    }

    private void chooseImport() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        startActivityForResult(intent, REQ_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        if (requestCode == REQ_EXPORT) requestExport(uri);
        else if (requestCode == REQ_IMPORT) requestImport(uri);
    }

    private void requestExport(final Uri uri) {
        ResultReceiver reply = new ResultReceiver(handler) {
            @Override protected void onReceiveResult(int code, Bundle data) {
                if (code != ModuleConfigBridge.RESULT_OK) {
                    bridgeError(data); return;
                }
                try {
                    OutputStream out = getContentResolver().openOutputStream(uri, "wt");
                    out.write(data.getString(ModuleConfigBridge.EXTRA_JSON, "").getBytes(StandardCharsets.UTF_8));
                    out.close();
                    toast(UiLanguage.text(SettingsActivity.this, "配置已导出", "Configuration exported"));
                } catch (Throwable error) { toast(error.getMessage()); }
            }
        };
        sendBroadcast(ModuleConfigBridge.request(ModuleConfigBridge.MODE_EXPORT, null, reply));
        toast(UiLanguage.text(this, "正在读取 DeepSeek 配置", "Reading DeepSeek configuration"));
    }

    private void requestImport(Uri uri) {
        try {
            InputStream input = getContentResolver().openInputStream(uri);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0 && output.size() <= 1024 * 1024) output.write(buffer, 0, count);
            input.close();
            final String json = new String(output.toByteArray(), StandardCharsets.UTF_8);
            ResultReceiver reply = new ResultReceiver(handler) {
                @Override protected void onReceiveResult(int code, Bundle data) {
                    if (code != ModuleConfigBridge.RESULT_OK) { bridgeError(data); return; }
                    toast(UiLanguage.text(SettingsActivity.this,
                            "配置已导入，重新打开 DeepSeek 后生效", "Configuration imported; reopen DeepSeek to apply"));
                }
            };
            sendBroadcast(ModuleConfigBridge.request(ModuleConfigBridge.MODE_IMPORT, json, reply));
        } catch (Throwable error) { toast(error.getMessage()); }
    }

    private void bridgeError(Bundle data) {
        String detail = data == null ? "" : data.getString("error", "");
        toast(UiLanguage.text(this, "请先启动一次 DeepSeek，再重试", "Launch DeepSeek once, then try again")
                + (detail.length() == 0 ? "" : "\n" + detail));
    }

    private void refreshActivationState() {
        if (activationTitle == null || isFinishing()) return;
        boolean framework = XposedActivationProvider.isFrameworkConnected();
        boolean target = XposedActivationProvider.isTargetRecentlyActive(this);
        if (framework || target) {
            activationTitle.setText(UiLanguage.text(this, "已激活", "Activated"));
            activationDetail.setText(target ? apiDisplayName() + " · DeepSeek " + deepSeekVersion()
                    : apiDisplayName() + " · " + UiLanguage.text(this, "启动 DeepSeek 生效", "Ready — launch DeepSeek"));
            activationMark.setText("✓");
        } else if (isLegacyBuild()) {
            activationTitle.setText(UiLanguage.text(this, "待验证", "Waiting for verification"));
            activationDetail.setText(apiDisplayName() + " · " + UiLanguage.text(this, "启动 DeepSeek 后确认", "Launch DeepSeek to confirm"));
            activationMark.setText("—");
        } else {
            activationTitle.setText(UiLanguage.text(this, "未激活", "Not activated"));
            activationDetail.setText(apiDisplayName() + " · " + UiLanguage.text(this, "Xposed 框架未连接", "Xposed framework not connected"));
            activationMark.setText("×");
        }
    }

    private String deepSeekVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(TARGET_PACKAGE, 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            String name = info.versionName == null ? "" : info.versionName;
            return code > 0 ? name + " (" + code + ")" : name;
        } catch (Throwable ignored) { return UiLanguage.text(this, "未安装", "Not installed"); }
    }

    private String apiDisplayName() {
        String value = BuildInfo.API_VERSION == null ? "" : BuildInfo.API_VERSION.trim();
        return (isLegacyBuild() ? "Xposed API " : "API ") + value;
    }

    private static boolean isLegacyBuild() {
        return BuildInfo.API_VERSION != null && (BuildInfo.API_VERSION.contains("legacy")
                || BuildInfo.API_VERSION.contains("universal"));
    }

    private void configureWindow() {
        Window window = getWindow();
        if (window == null) return;
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.setStatusBarColor(bg);
        window.setNavigationBarColor(card);
        int flags = window.getDecorView().getSystemUiVisibility();
        if (!renderedDark && Build.VERSION.SDK_INT >= 23) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (!renderedDark && Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        else flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private boolean isDark() {
        int mode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private LinearLayout card() {
        LinearLayout out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        out.setBackground(rounded(card, 18));
        out.setClipToOutline(true);
        out.setElevation(dp(1));
        return out;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(14);
        return lp;
    }

    private TextView sectionTitle(String value) {
        TextView title = text(value, 13, muted, Typeface.create("sans-serif-medium", 0));
        title.setPadding(dp(6), dp(10), 0, dp(8));
        return title;
    }

    private View divider() {
        View view = new View(this);
        view.setBackgroundColor(line);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(.7f));
        lp.leftMargin = dp(56);
        view.setLayoutParams(lp);
        return view;
    }

    private TextView text(String value, int sp, int color, Typeface typeface) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        view.setTypeface(typeface);
        view.setLineSpacing(0, 1.08f);
        return view;
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Throwable error) { toast(error.getMessage()); }
    }

    private void toast(String value) { Toast.makeText(this, value == null ? "" : value, Toast.LENGTH_LONG).show(); }

    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(-1, -2); }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, getResources().getDisplayMetrics()));
    }

    private void ensureStoragePermission() {
        try {
            if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) return;
            if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                    && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            }
        } catch (Throwable ignored) {}
    }

    private void ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIFICATIONS);
        }
    }
}
