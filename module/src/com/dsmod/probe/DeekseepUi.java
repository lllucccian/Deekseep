package com.dsmod.probe;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** 纯原生 View 构建入口按钮与 Deekseep 子页面，不依赖宿主 compose。 */
public final class DeekseepUi {

    static final int BRAND = 0xFF4D6BFE;
    static final String ENTRY_BUTTON_TAG = "deekseep_settings_entry_button_v1";
    private static volatile Dialog activePageDialog;
    private static volatile Dialog rootPageDialog;
    private static volatile TextView activePromptImportButton;
    private static volatile TextView activePromptPathText;
    private static volatile View activePromptResetRow;
    private static volatile Switch activePromptInjectionSwitch;
    private static volatile boolean refreshingPromptControls;
    private static final int CATEGORY_CHAT = 1;
    private static final int CATEGORY_ACCOUNT = 2;
    private static final int CATEGORY_APPEARANCE = 3;
    private static final int CATEGORY_DEBUG = 4;
    private static final int CATEGORY_ENGINEERING = 5;
    private static final String REPOSITORY = "https://github.com/lllucccian/Deekseep";

    private static final class FeatureSearchEntry {
        final String title;
        final String keywords;
        final int category;

        FeatureSearchEntry(String title, String keywords, int category) {
            this.title = title;
            this.keywords = keywords;
            this.category = category;
        }
    }

    private static final FeatureSearchEntry[] FEATURE_SEARCH = {
            new FeatureSearchEntry("系统提示词注入", "提示词 prompt 导入", CATEGORY_CHAT),
            new FeatureSearchEntry("去除安全审查", "内容替换 一键破甲", CATEGORY_CHAT),
            new FeatureSearchEntry("聊天记录多选", "批量 删除", CATEGORY_CHAT),
            new FeatureSearchEntry("编辑聊天记录", "修改 新建对话", CATEGORY_CHAT),
            new FeatureSearchEntry("消息时间与详情", "时间戳 message details", CATEGORY_CHAT),
            new FeatureSearchEntry("自动继续生成", "继续生成 长思考 后台 续写", CATEGORY_CHAT),
            new FeatureSearchEntry("导出会话为 Markdown", "导出 备份包", CATEGORY_CHAT),
            new FeatureSearchEntry("导入聊天记录", "恢复 覆盖 备份包", CATEGORY_CHAT),
            new FeatureSearchEntry("全局搜索聊天记录", "消息 搜索", CATEGORY_CHAT),
            new FeatureSearchEntry("会话数据统计", "消息 字数", CATEGORY_CHAT),
            new FeatureSearchEntry("立即备份聊天数据库", "数据库 backup", CATEGORY_CHAT),
            new FeatureSearchEntry("自动备份聊天数据库", "每日", CATEGORY_CHAT),
            new FeatureSearchEntry("解锁专家模式与图片上传", "expert 图片", CATEGORY_CHAT),
            new FeatureSearchEntry("AI 心跳", "主动消息 间隔", CATEGORY_CHAT),
            new FeatureSearchEntry("多账号管理", "账号 切换 导入", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("解锁 Google 登录", "谷歌 国内版", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("解锁微信与手机号登录", "海外版 短信", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("本地禁言", "时间 截止日期", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("禁用数据用于优化体验", "隐私 training", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("主页欢迎语", "首页 文案", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("原生设置入口", "插件 悬浮", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("外观设置", "背景 气泡 液态玻璃 空间动效", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("自定义 DeepSeek 头像",
                    "助手头像 灰度 显示助手头像", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("鲸鱼图标动效", "旋转", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("深海文字波纹", "字体 渐变", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("记录服务器返回", "诊断 SSE", CATEGORY_DEBUG),
            new FeatureSearchEntry("Hook 日志显示在屏幕", "日志 overlay", CATEGORY_DEBUG),
            new FeatureSearchEntry("记录崩溃", "crash", CATEGORY_DEBUG),
            new FeatureSearchEntry("兼容性诊断报告", "版本 映射", CATEGORY_DEBUG),
            new FeatureSearchEntry("Hook 性能统计", "耗时", CATEGORY_DEBUG),
            new FeatureSearchEntry("脱敏事件追踪与导出", "trace", CATEGORY_DEBUG),
            new FeatureSearchEntry("发送自定义请求", "网络 API", CATEGORY_DEBUG),
            new FeatureSearchEntry("语言", "中文 English", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("禁用热更新", "更新 强制更新", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("灰度功能管理器", "远程配置 feature flags", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("自动清理缓存", "图片 Mermaid Coil", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("进程管理", "进程 冻结 解冻 杀死 Root", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("Agent", "工具 Root Shizuku", CATEGORY_ENGINEERING)
    };

    private static void refreshPromptControls() {
        boolean embedded = Main.isEmbeddedPromptEnabled();
        if (embedded) Main.setEnabled(true);
        TextView importButton = activePromptImportButton;
        if (importButton != null) {
            importButton.setText(embedded
                    ? "已开启其他功能，请先关闭后再使用" : "导入提示词");
            importButton.setEnabled(!embedded);
            importButton.setAlpha(embedded ? 0.45f : 1f);
        }
        TextView path = activePromptPathText;
        if (path != null) path.setText(embedded ? "" : Main.getPromptDisplayPath());
        View reset = activePromptResetRow;
        if (reset != null) {
            reset.setEnabled(!embedded);
            reset.setAlpha(embedded ? 0.45f : 1f);
        }
        Switch injection = activePromptInjectionSwitch;
        if (injection != null) {
            refreshingPromptControls = true;
            injection.setChecked(embedded || Main.isEnabled());
            injection.setEnabled(!embedded);
            injection.setAlpha(embedded ? 0.55f : 1f);
            refreshingPromptControls = false;
        }
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    static boolean isDark(Context c) {
        // Deekseep deliberately follows the device color scheme.  DeepSeek can maintain a
        // separate in-app theme, so its wrapped Activity resources are not authoritative here.
        try {
            int systemMode = Resources.getSystem().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            if (systemMode == Configuration.UI_MODE_NIGHT_YES) return true;
            if (systemMode == Configuration.UI_MODE_NIGHT_NO) return false;
        } catch (Throwable ignored) {}
        try {
            return c != null && (c.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static void dismissForNativeNavigation() {
        Dialog dialog = activePageDialog;
        activePageDialog = null;
        if (dialog != null) {
            try { dialog.dismiss(); } catch (Throwable ignored) {}
        }
        Dialog root = rootPageDialog;
        rootPageDialog = null;
        if (root != null && root != dialog) {
            try { root.dismiss(); } catch (Throwable ignored) {}
        }
    }

    /** Marks a full-screen module child so native navigation can close the complete module stack. */
    static void trackChildDialog(Dialog dialog) {
        if (dialog != null) activePageDialog = dialog;
    }

    /** 右上角的文字入口 "Deekseep"（无背景）。 */
    static TextView createEntryButton(Context ctx, View.OnClickListener onClick) {
        TextView b = new TextView(ctx);
        b.setTag(ENTRY_BUTTON_TAG);
        b.setText("Deekseep");
        b.setTextColor(isDark(ctx) ? 0xFFECECEC : 0xFF1A1A1A);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(ctx, 8), dp(ctx, 4), dp(ctx, 8), dp(ctx, 4));
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        return b;
    }

    /** Deekseep 首页只负责分组；具体开关和工具进入对应子页。 */
    static void showPage(final Activity act) {
        UiLanguage.refreshHost(act);
        final boolean dark = isDark(act);
        final int bgColor = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFF9A9A9E : 0xFF888888;
        final int divColor = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        final LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(barColor);
        int statusTop = statusBarHeight(act);
        bar.setPadding(dp(act, 8), statusTop, dp(act, 16), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 56) + statusTop));

        final Dialog dlg = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        activePageDialog = dlg;
        rootPageDialog = dlg;
        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface ignored) {
                if (activePageDialog == dlg) activePageDialog = null;
                if (rootPageDialog == dlg) rootPageDialog = null;
            }
        });
        TextView back = new TextView(act);
        back.setText("\u2039");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setTextColor(textColor);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(act, 8), 0, dp(act, 8), 0);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { slideOutAndDismiss(dlg, root); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(act, 40)));
        TextView title = new TextView(act);
        title.setText("Deekseep");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(act, 8);
        bar.addView(title, titleLp);

        android.widget.ScrollView scroll = new android.widget.ScrollView(act);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new android.widget.ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final EditText featureSearch = new EditText(act);
        featureSearch.setSingleLine(true);
        featureSearch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        featureSearch.setTextColor(textColor);
        featureSearch.setHintTextColor(subColor);
        featureSearch.setHint(UiLanguage.text(act, "搜索功能", "Search features"));
        featureSearch.setPadding(dp(act, 16), 0, dp(act, 16), 0);
        featureSearch.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        GradientDrawable searchBackground = new GradientDrawable();
        searchBackground.setColor(dark ? 0xFF151517 : 0xFFECEEF2);
        searchBackground.setCornerRadius(dp(act, 12));
        featureSearch.setBackground(searchBackground);
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 48));
        searchLp.setMargins(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 8));
        content.addView(featureSearch, searchLp);

        final LinearLayout resultsCard = new LinearLayout(act);
        resultsCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable resultsBackground = new GradientDrawable();
        resultsBackground.setColor(cardColor);
        resultsBackground.setCornerRadius(dp(act, 12));
        resultsCard.setBackground(resultsBackground);
        resultsCard.setVisibility(View.GONE);
        LinearLayout.LayoutParams resultsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        resultsLp.setMargins(dp(act, 16), dp(act, 8), dp(act, 16), dp(act, 20));
        content.addView(resultsCard, resultsLp);

        final LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(dp(act, 12));
        card.setBackground(cardBg);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(act, 16), dp(act, 8), dp(act, 16), dp(act, 20));
        content.addView(card, cardLp);

        addCategoryEntry(act, dlg, card, "聊天", "ds_category_chat",
                "", CATEGORY_CHAT,
                textColor, subColor);
        card.addView(makeDivider(act, divColor));
        addCategoryEntry(act, dlg, card, "账号与隐私", "ds_category_account",
                "", CATEGORY_ACCOUNT,
                textColor, subColor);
        card.addView(makeDivider(act, divColor));
        addCategoryEntry(act, dlg, card, "界面美化", "ds_category_appearance",
                "", CATEGORY_APPEARANCE,
                textColor, subColor);
        card.addView(makeDivider(act, divColor));
        addCategoryEntry(act, dlg, card, "调试", "ds_category_debug",
                "", CATEGORY_DEBUG,
                textColor, subColor);
        card.addView(makeDivider(act, divColor));
        addCategoryEntry(act, dlg, card, "工程", "ds_category_engineering",
                "", CATEGORY_ENGINEERING,
                textColor, subColor);
        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "ds_category_help", "帮助与问题",
                "", textColor, subColor,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { showHelpPage(act); }
                }));
        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "ds_project_sponsor", "赞助开发者",
                "", textColor, subColor,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { showSponsorDialog(act); }
                }));
        card.addView(makeDivider(act, divColor));
        addBuildFooter(act, card, subColor);

        featureSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start,
                                                    int count, int after) {}
            @Override public void onTextChanged(CharSequence value, int start,
                                                int before, int count) {
                String query = value == null ? "" : value.toString().trim();
                if (query.length() == 0) {
                    resultsCard.removeAllViews();
                    resultsCard.setVisibility(View.GONE);
                    card.setVisibility(View.VISIBLE);
                    return;
                }
                card.setVisibility(View.GONE);
                resultsCard.setVisibility(View.VISIBLE);
                populateFeatureSearchResults(act, dlg, resultsCard, query,
                        textColor, subColor, divColor);
            }
            @Override public void afterTextChanged(Editable value) {}
        });

        UiLanguage.localizeTree(act, root);
        dlg.setContentView(root);
        Window window = dlg.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(bgColor));
        }
        openWithSlide(dlg, root);
    }

    private static void addCategoryEntry(final Activity act, final Dialog parent,
            LinearLayout card, String title, String iconName, String description, final int category,
            int textColor, int subColor) {
        card.addView(toolActionRow(act, iconName, title, description, textColor, subColor,
                new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        // The hub remains underneath the child. This prevents a one-frame flash
                        // of DeepSeek's native settings and gives the module a real back stack.
                        showCategoryPage(act, category, parent);
                    }
                }));
    }

    private static void populateFeatureSearchResults(final Activity act, final Dialog parent,
            LinearLayout results, String query, int textColor, int subColor, int dividerColor) {
        results.removeAllViews();
        String needle = query.toLowerCase(java.util.Locale.ROOT);
        int found = 0;
        for (final FeatureSearchEntry entry : FEATURE_SEARCH) {
            String english = UiLanguageCatalog.toEnglish(entry.title + " " + entry.keywords);
            String haystack = (entry.title + " " + entry.keywords + " " + english)
                    .toLowerCase(java.util.Locale.ROOT);
            if (!haystack.contains(needle)) continue;
            if (found > 0) results.addView(makeDivider(act, dividerColor));
            String destination = UiLanguage.text(act, "位于：", "In: ")
                    + UiLanguage.dynamic(act, categoryTitle(entry.category))
                    + UiLanguage.text(act, " · 点击进入", " · Tap to open");
            results.addView(toolActionRow(act, categorySearchIcon(entry.category),
                    entry.title, destination, textColor, subColor,
                    new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            showCategoryPage(act, entry.category, parent);
                        }
                    }));
            found++;
        }
        if (found == 0) {
            TextView empty = new TextView(act);
            empty.setText(UiLanguage.text(act,
                    "没有找到相关功能", "No matching features"));
            empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            empty.setTextColor(subColor);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(act, 16), dp(act, 24), dp(act, 16), dp(act, 24));
            results.addView(empty);
        }
    }

    private static String categorySearchIcon(int category) {
        switch (category) {
            case CATEGORY_CHAT: return "ds_category_chat";
            case CATEGORY_ACCOUNT: return "ds_category_account";
            case CATEGORY_APPEARANCE: return "ds_category_appearance";
            case CATEGORY_DEBUG: return "ds_category_debug";
            default: return "ds_category_engineering";
        }
    }

    /** 全屏分类页；旧功能控件仍复用同一套实现，再按功能归属筛选。 */
    private static void showCategoryPage(final Activity act, final int category,
            final Dialog parent) {
        // Re-read DeepSeek's MMKV language tag at the moment the page is opened.  This also covers
        // hosts that change language without recreating or resuming their current Activity.
        UiLanguage.refreshHost(act);
        boolean dark = isDark(act);
        int bgColor   = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        int barColor  = dark ? 0xFF232326 : 0xFFFFFFFF;
        int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        int subColor  = dark ? 0xFF9A9A9E : 0xFF888888;
        int divColor  = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);

        // 顶部栏
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(barColor);
        int barH = dp(act, 56);
        int statusTop = statusBarHeight(act);
        bar.setPadding(dp(act, 8), statusTop, dp(act, 16), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barH + statusTop));

        final Dialog dlg = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        activePageDialog = dlg;
        dlg.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface ignored) {
                if (activePageDialog == dlg) {
                    activePageDialog = parent != null && parent.isShowing() ? parent : null;
                    activePromptImportButton = null;
                    activePromptPathText = null;
                    activePromptResetRow = null;
                    activePromptInjectionSwitch = null;
                }
            }
        });

        TextView back = new TextView(act);
        back.setText("\u2039");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setTextColor(textColor);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(act, 8), 0, dp(act, 8), 0);
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { slideOutAndDismiss(dlg, root); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(act, 40)));

        TextView title = new TextView(act);
        title.setText(categoryTitle(category));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(act, 8);
        bar.addView(title, tlp);

        // 可滚动区域（内容变多/帮助折叠展开时不会溢出屏幕）
        android.widget.ScrollView scroll = new android.widget.ScrollView(act);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 主卡片
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(dp(act, 12));
        card.setBackground(cardBg);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16));
        scroll.addView(card, clp);

        // ── Section 1: 导入按钮 + 路径 ─────────────────────────────
        LinearLayout importSection = new LinearLayout(act);
        importSection.setOrientation(LinearLayout.VERTICAL);
        importSection.setGravity(Gravity.CENTER_HORIZONTAL);
        importSection.setPadding(dp(act, 16), dp(act, 18), dp(act, 16), dp(act, 14));

        final TextView importBtn = new TextView(act);
        importBtn.setText("导入提示词");
        importBtn.setTextColor(BRAND);
        importBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        importBtn.setTypeface(Typeface.DEFAULT);
        importBtn.setGravity(Gravity.CENTER);
        importBtn.setPadding(dp(act, 18), dp(act, 8), dp(act, 18), dp(act, 8));
        GradientDrawable importBg = new GradientDrawable();
        importBg.setColor(dark ? 0xFF252545 : 0xFFEEF1FF);
        importBg.setCornerRadius(dp(act, 6));
        importBtn.setBackground(importBg);
        importBtn.setClickable(true);
        importBtn.setFocusable(true);
        importSection.addView(importBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 路径文字
        final TextView pathText = new TextView(act);
        pathText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pathText.setTextColor(subColor);
        pathText.setGravity(Gravity.CENTER);
        pathText.setText(Main.getPromptDisplayPath());
        LinearLayout.LayoutParams ptlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ptlp.topMargin = dp(act, 8);
        importSection.addView(pathText, ptlp);
        card.addView(importSection, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        importBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Main.isEmbeddedPromptEnabled()) {
                    refreshPromptControls();
                    return;
                }
                Main.onPickComplete = new Runnable() {
                    public void run() {
                        pathText.setText(Main.getPromptDisplayPath());
                    }
                };
                Intent i = new Intent();
                i.setClassName(Main.SELF, Main.SELF + ".PromptPickerActivity");
                try {
                    act.startActivityForResult(i, Main.PICK_REQUEST);
                } catch (ActivityNotFoundException e) {
                    Intent fallback = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    fallback.addCategory(Intent.CATEGORY_OPENABLE);
                    fallback.setType("text/*");
                    fallback.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    fallback.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                    act.startActivityForResult(fallback, Main.PICK_REQUEST);
                }
            }
        });

        // ── 分割线 ──────────────────────────────────────────────────
        card.addView(makeDivider(act, divColor));

        // ── Section 2: 还原设置 ─────────────────────────────────────
        LinearLayout resetRow = new LinearLayout(act);
        resetRow.setOrientation(LinearLayout.HORIZONTAL);
        resetRow.setGravity(Gravity.CENTER_VERTICAL);
        resetRow.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16));
        resetRow.setClickable(true);
        resetRow.setFocusable(true);

        TextView resetLabel = new TextView(act);
        resetLabel.setText("还原设置");
        resetLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        resetLabel.setTextColor(0xFFE53935);
        resetRow.addView(resetLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        resetRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (Main.isEmbeddedPromptEnabled()) {
                    refreshPromptControls();
                    return;
                }
                Main.clearPromptFiles();
                pathText.setText("");
            }
        });
        card.addView(resetRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 分割线 ──────────────────────────────────────────────────
        card.addView(makeDivider(act, divColor));

        // ── Section 3: 系统提示词注入开关 ───────────────────────────
        LinearLayout toggleRow = new LinearLayout(act);
        toggleRow.setOrientation(LinearLayout.HORIZONTAL);
        toggleRow.setGravity(Gravity.CENTER_VERTICAL);
        toggleRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));

        TextView toggleLabel = new TextView(act);
        toggleLabel.setText("系统提示词注入");
        toggleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        toggleLabel.setTextColor(textColor);
        toggleRow.addView(toggleLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Switch sw = new HubInsetSwitch(act);
        sw.setChecked(Main.isEnabled());
        int[][] ss = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
        // ON: thumb=蓝色, track=浅蓝; OFF: thumb=白色/灰, track=灰
        sw.setThumbTintList(new android.content.res.ColorStateList(ss,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        sw.setTrackTintList(new android.content.res.ColorStateList(ss,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        sw.setBackground(null);
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (refreshingPromptControls) return;
                if (Main.isEmbeddedPromptEnabled()) {
                    b.setChecked(true);
                    Main.setEnabled(true);
                    return;
                }
                Main.setEnabled(checked);
            }
        });
        toggleRow.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(toggleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        activePromptImportButton = importBtn;
        activePromptPathText = pathText;
        activePromptResetRow = resetRow;
        activePromptInjectionSwitch = sw;
        refreshPromptControls();

        // ── 分割线 ──────────────────────────────────────────────────
        card.addView(makeDivider(act, divColor));

        // ── Section 4: 去他妈的安全审查（阻止内容擦除）────────────────
        LinearLayout censorRow = new LinearLayout(act);
        censorRow.setOrientation(LinearLayout.HORIZONTAL);
        censorRow.setGravity(Gravity.CENTER_VERTICAL);
        censorRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));

        LinearLayout censorLabels = new LinearLayout(act);
        censorLabels.setOrientation(LinearLayout.VERTICAL);

        TextView censorLabel = new TextView(act);
        censorLabel.setText("去他妈的安全审查");
        censorLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        censorLabel.setTypeface(Typeface.DEFAULT_BOLD);
        censorLabel.setTextColor(textColor);
        censorLabels.addView(censorLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView censorDesc = new TextView(act);
        censorDesc.setText("保留被替换前的完整回答");
        censorDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        censorDesc.setTextColor(subColor);
        LinearLayout.LayoutParams cdlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cdlp.topMargin = dp(act, 4);
        censorLabels.addView(censorDesc, cdlp);

        LinearLayout.LayoutParams cllp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cllp.rightMargin = dp(act, 12);
        censorRow.addView(censorLabels, cllp);

        Switch censorSw = new HubInsetSwitch(act);
        censorSw.setChecked(Main.isNoCensor());
        censorSw.setThumbTintList(new android.content.res.ColorStateList(ss,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        censorSw.setTrackTintList(new android.content.res.ColorStateList(ss,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        censorSw.setBackground(null);
        censorSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Main.setNoCensor(checked);
            }
        });
        censorRow.addView(censorSw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(censorRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 分割线 ──────────────────────────────────────────────────
        card.addView(makeDivider(act, divColor));

        // ── Section 4a: 聊天记录多选 ────────────────────────────────
        LinearLayout multiRow = new LinearLayout(act);
        multiRow.setOrientation(LinearLayout.HORIZONTAL);
        multiRow.setGravity(Gravity.CENTER_VERTICAL);
        multiRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));

        LinearLayout multiLabels = new LinearLayout(act);
        multiLabels.setOrientation(LinearLayout.VERTICAL);

        TextView multiLabel = new TextView(act);
        multiLabel.setText("聊天记录多选");
        multiLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        multiLabel.setTypeface(Typeface.DEFAULT_BOLD);
        multiLabel.setTextColor(textColor);
        multiLabels.addView(multiLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView multiDesc = new TextView(act);
        multiDesc.setText("长按会话进入多选");
        multiDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        multiDesc.setTextColor(subColor);
        LinearLayout.LayoutParams mdlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mdlp.topMargin = dp(act, 4);
        multiLabels.addView(multiDesc, mdlp);

        LinearLayout.LayoutParams mllp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mllp.rightMargin = dp(act, 12);
        multiRow.addView(multiLabels, mllp);

        Switch multiSw = new HubInsetSwitch(act);
        multiSw.setChecked(Main.isChatMultiSelect());
        multiSw.setThumbTintList(new android.content.res.ColorStateList(ss,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        multiSw.setTrackTintList(new android.content.res.ColorStateList(ss,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        multiSw.setBackground(null);
        multiSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Main.setChatMultiSelect(checked);
            }
        });
        multiRow.addView(multiSw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(multiRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 分割线 ──────────────────────────────────────────────────
        card.addView(makeDivider(act, divColor));

        // ── Section 4c: 解锁原生 Google 登录入口 ────────────────────
        LinearLayout googleRow = new LinearLayout(act);
        googleRow.setOrientation(LinearLayout.HORIZONTAL);
        googleRow.setGravity(Gravity.CENTER_VERTICAL);
        googleRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));

        LinearLayout googleLabels = new LinearLayout(act);
        googleLabels.setOrientation(LinearLayout.VERTICAL);

        TextView googleLabel = new TextView(act);
        googleLabel.setText("解锁 Google 登录");
        googleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        googleLabel.setTypeface(Typeface.DEFAULT_BOLD);
        googleLabel.setTextColor(textColor);
        googleLabels.addView(googleLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView googleDesc = new TextView(act);
        googleDesc.setText("解锁国内用户的 Google 登录入口。");
        googleDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        googleDesc.setTextColor(subColor);
        LinearLayout.LayoutParams gdlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        gdlp.topMargin = dp(act, 4);
        googleLabels.addView(googleDesc, gdlp);

        LinearLayout.LayoutParams gllp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        gllp.rightMargin = dp(act, 12);
        googleRow.addView(googleLabels, gllp);

        Switch googleSw = new HubInsetSwitch(act);
        googleSw.setChecked(Main.isGoogleLoginUnlock());
        googleSw.setThumbTintList(new android.content.res.ColorStateList(ss,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        googleSw.setTrackTintList(new android.content.res.ColorStateList(ss,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        googleSw.setBackground(null);
        googleSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Main.setGoogleLoginUnlock(checked);
            }
        });
        googleRow.addView(googleSw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(googleRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── 分割线 ──────────────────────────────────────────────────
        card.addView(makeDivider(act, divColor));

        // ── Section 4d: 海外环境恢复微信 + 手机号登录（一个联合开关）────
        LinearLayout cnLoginRow = new LinearLayout(act);
        cnLoginRow.setOrientation(LinearLayout.HORIZONTAL);
        cnLoginRow.setGravity(Gravity.CENTER_VERTICAL);
        cnLoginRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));

        LinearLayout cnLoginLabels = new LinearLayout(act);
        cnLoginLabels.setOrientation(LinearLayout.VERTICAL);
        TextView cnLoginLabel = new TextView(act);
        cnLoginLabel.setText("解锁微信与手机号登录");
        cnLoginLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        cnLoginLabel.setTypeface(Typeface.DEFAULT_BOLD);
        cnLoginLabel.setTextColor(textColor);
        cnLoginLabels.addView(cnLoginLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView cnLoginDesc = new TextView(act);
        cnLoginDesc.setText("解锁海外用户的微信与手机号登录入口。");
        cnLoginDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        cnLoginDesc.setTextColor(subColor);
        LinearLayout.LayoutParams cndlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cndlp.topMargin = dp(act, 4);
        cnLoginLabels.addView(cnLoginDesc, cndlp);

        LinearLayout.LayoutParams cnllp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cnllp.rightMargin = dp(act, 12);
        cnLoginRow.addView(cnLoginLabels, cnllp);

        Switch cnLoginSw = new HubInsetSwitch(act);
        cnLoginSw.setChecked(Main.isWechatMobileLoginUnlock());
        cnLoginSw.setThumbTintList(new android.content.res.ColorStateList(ss,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        cnLoginSw.setTrackTintList(new android.content.res.ColorStateList(ss,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        cnLoginSw.setBackground(null);
        cnLoginSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Main.setWechatMobileLoginUnlock(checked);
            }
        });
        cnLoginRow.addView(cnLoginSw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(cnLoginRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(makeDivider(act, divColor));
        card.addView(simpleSwitchRow(act, "禁用数据用于优化体验",
                "开启后立即关闭 DeepSeek 的“数据用于优化体验”，并阻止再次开启。",
                Main.isDataOptOutEnforced(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        if (reverting) return;
                        if (Main.setDataOptOutEnforced(act, checked)) return;
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                        Toast.makeText(act, UiLanguage.text(act,
                                "禁用设置保存失败",
                                "Could not save the disable-data setting"),
                                Toast.LENGTH_SHORT).show();
                    }
                }));

        card.addView(makeDivider(act, divColor));
        final TextView[] fakeMuteDetail = new TextView[1];
        card.addView(configurableSwitchRow(act, "本地禁言 · 实验性",
                fakeMuteDescription(), Main.isFakeMuteEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                        if (!checked) {
                            Main.setFakeMuteEnabled(false);
                            if (fakeMuteDetail[0] != null) {
                                fakeMuteDetail[0].setText(fakeMuteDescription());
                            }
                            Toast.makeText(act, "本地禁言已关闭", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!Main.setFakeMuteEnabled(true)) {
                            button.setOnCheckedChangeListener(null);
                            button.setChecked(false);
                            button.setOnCheckedChangeListener(this);
                            Toast.makeText(act, UiLanguage.text(act,
                                    "请先设置一个未来的截止时间",
                                    "Set a future deadline first"),
                                    Toast.LENGTH_SHORT).show();
                        }
                        if (fakeMuteDetail[0] != null) {
                            fakeMuteDetail[0].setText(fakeMuteDescription());
                        }
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        showFakeMuteTimePicker(act, fakeMuteDetail[0]);
                    }
                }, fakeMuteDetail));

        // ── 分割线 ──────────────────────────────────────────────────
        card.addView(makeDivider(act, divColor));

        // ── Section 5: 记录服务器返回（诊断）─────────────────────────
        LinearLayout srvRow = new LinearLayout(act);
        srvRow.setOrientation(LinearLayout.HORIZONTAL);
        srvRow.setGravity(Gravity.CENTER_VERTICAL);
        srvRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));

        LinearLayout srvLabels = new LinearLayout(act);
        srvLabels.setOrientation(LinearLayout.VERTICAL);

        TextView srvLabel = new TextView(act);
        srvLabel.setText("记录服务器返回（诊断）");
        srvLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        srvLabel.setTypeface(Typeface.DEFAULT_BOLD);
        srvLabel.setTextColor(textColor);
        srvLabels.addView(srvLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView srvDesc = new TextView(act);
        srvDesc.setText("");
        srvDesc.setVisibility(View.GONE);
        srvDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        srvDesc.setTextColor(subColor);
        LinearLayout.LayoutParams sdlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sdlp.topMargin = dp(act, 4);
        srvLabels.addView(srvDesc, sdlp);

        LinearLayout.LayoutParams sllp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        sllp.rightMargin = dp(act, 12);
        srvRow.addView(srvLabels, sllp);

        Switch srvSw = new HubInsetSwitch(act);
        srvSw.setChecked(Main.isSrvLog());
        srvSw.setThumbTintList(new android.content.res.ColorStateList(ss,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        srvSw.setTrackTintList(new android.content.res.ColorStateList(ss,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        srvSw.setBackground(null);
        srvSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Main.setSrvLog(checked);
            }
        });
        srvRow.addView(srvSw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(srvRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(makeDivider(act, divColor));
        LinearLayout overlayRow = new LinearLayout(act);
        overlayRow.setOrientation(LinearLayout.HORIZONTAL);
        overlayRow.setGravity(Gravity.CENTER_VERTICAL);
        overlayRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));
        LinearLayout overlayLabels = new LinearLayout(act);
        overlayLabels.setOrientation(LinearLayout.VERTICAL);
        TextView overlayTitle = new TextView(act);
        overlayTitle.setText("Hook 日志显示在屏幕");
        overlayTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        overlayTitle.setTextColor(textColor);
        overlayLabels.addView(overlayTitle);
        TextView overlayDesc = new TextView(act);
        overlayDesc.setText("透明显示 Hook 日志");
        overlayDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        overlayDesc.setTextColor(subColor);
        overlayLabels.addView(overlayDesc);
        overlayRow.addView(overlayLabels, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch overlaySwitch = new HubInsetSwitch(act);
        overlaySwitch.setChecked(HookLogOverlay.enabled());
        overlaySwitch.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                        HookLogOverlay.setEnabled(checked);
                        if (checked) HookLogOverlay.onActivityResumed(act);
                    }
                });
        overlayRow.addView(overlaySwitch);
        card.addView(overlayRow);

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "记录崩溃",
                "",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        CrashDiagnosticsUi.showRecords(act);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "崩溃测试",
                "",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        CrashDiagnosticsUi.showCrashTests(act);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "兼容性诊断报告",
                "",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        DeveloperDiagnostics.showCompatibility(act);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "Hook 性能统计",
                "",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        DeveloperDiagnostics.showPerformance(act);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "脱敏事件追踪与导出",
                "",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        DeveloperDiagnostics.exportTrace(act);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "发送自定义请求",
                "",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        DeepSeekRequestDebugUi.show(act);
                    }
                }));

        // ── 分割线 ──────────────────────────────────────────────────
        card.addView(makeDivider(act, divColor));

        // ── Section 6: 编辑聊天记录 ─────────────────────────────────
        LinearLayout editRow = new LinearLayout(act);
        editRow.setOrientation(LinearLayout.HORIZONTAL);
        editRow.setGravity(Gravity.CENTER_VERTICAL);
        editRow.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16));
        editRow.setClickable(true);
        editRow.setFocusable(true);

        LinearLayout editLabels = new LinearLayout(act);
        editLabels.setOrientation(LinearLayout.VERTICAL);
        TextView editLabel = new TextView(act);
        editLabel.setText("编辑聊天记录");
        editLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        editLabel.setTextColor(textColor);
        editLabels.addView(editLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView editDesc = new TextView(act);
        editDesc.setText("");
        editDesc.setVisibility(View.GONE);
        editDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        editDesc.setTextColor(subColor);
        LinearLayout.LayoutParams edlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        edlp.topMargin = dp(act, 4);
        editLabels.addView(editDesc, edlp);
        editRow.addView(editLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView editArrow = new TextView(act);
        editArrow.setText("\u203A");
        editArrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        editArrow.setTextColor(subColor);
        editRow.addView(editArrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        editRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { ChatEditorUi.show(act); }
        });
        card.addView(editRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(makeDivider(act, divColor));
        card.addView(simpleSwitchRow(act, "消息时间与详情",
                "在聊天编辑器中显示每条本地消息的时间，点击时间可查看消息 ID 与详情。",
                Main.isMessageDetailsEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        if (reverting || Main.setMessageDetailsEnabled(checked)) return;
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(simpleSwitchRow(act, "自动继续生成",
                "长思考被服务器暂停时自动继续，切到后台也会生效。",
                Main.isAutoContinueEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        if (reverting || Main.setAutoContinueEnabled(checked)) return;
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(simpleSwitchRow(act, "回复完成通知",
                "切到后台后，模型完成回答时发送系统通知。",
                Main.isReplyReadyNotificationsEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        if (reverting || Main.setReplyReadyNotificationsEnabled(checked)) return;
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                    }
                }));

        // ── Section 6: 多账号管理（切换/添加账号）────────────────────────
        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "多账号管理",
                "",
                textColor, subColor, new View.OnClickListener() {
                    public void onClick(View v) { AccountUi.show(act); }
                }));

        // ── Section 7: 聊天数据工具箱（导出/搜索/统计/复制/备份）──────────
        card.addView(makeDivider(act, divColor));
        card.addView(taskActionRow(act, "导出会话为 Markdown",
                "导出 Markdown，并在下载目录生成可恢复的聊天备份包。",
                "导出期间请保持 DeepSeek 运行；备份包卸载后仍保留在 Download/Deekseep。",
                textColor, subColor, new TaskExecutionUi.Task() {
                    @Override public String run(TaskExecutionUi.Logger logger) throws Throwable {
                        return DeekseepTools.exportAllForConsole(act, logger);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "导入聊天记录",
                "选择导出的聊天备份包，只覆盖服务器已重新下发的同 ID 会话。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        intent.addCategory(Intent.CATEGORY_OPENABLE);
                        intent.setType("application/zip");
                        try {
                            act.startActivityForResult(intent, Main.CHAT_IMPORT_REQUEST);
                        } catch (ActivityNotFoundException unavailable) {
                            intent.setType("*/*");
                            act.startActivityForResult(intent, Main.CHAT_IMPORT_REQUEST);
                        }
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "全局搜索聊天记录",
                "",
                textColor, subColor, new View.OnClickListener() {
                    public void onClick(View v) { ChatSearchUi.show(act); }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(taskActionRow(act, "会话数据统计",
                "统计本地会话数、消息数、总字数，并按账号分组。",
                "仅统计设备上的聊天数据库，不会上传聊天内容。",
                textColor, subColor, new TaskExecutionUi.Task() {
                    @Override public String run(TaskExecutionUi.Logger logger) {
                        return DeekseepTools.statisticsForConsole(act, logger);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(taskActionRow(act, "立即备份聊天数据库",
                "复制全部聊天数据库到应用外部目录。",
                "备份不会修改原数据库；完成后日志会显示保存目录。",
                textColor, subColor, new TaskExecutionUi.Task() {
                    @Override public String run(TaskExecutionUi.Logger logger) throws Throwable {
                        return DeekseepTools.backupForConsole(act, logger);
                    }
                }));

        // ── Section 8: 自动备份开关 ─────────────────────────────────────
        card.addView(makeDivider(act, divColor));
        LinearLayout bkRow = new LinearLayout(act);
        bkRow.setOrientation(LinearLayout.HORIZONTAL);
        bkRow.setGravity(Gravity.CENTER_VERTICAL);
        bkRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));
        LinearLayout bkLabels = new LinearLayout(act);
        bkLabels.setOrientation(LinearLayout.VERTICAL);
        TextView bkLabel = new TextView(act);
        bkLabel.setText("自动备份聊天数据库");
        bkLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        bkLabel.setTypeface(Typeface.DEFAULT_BOLD);
        bkLabel.setTextColor(textColor);
        bkLabels.addView(bkLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView bkDesc = new TextView(act);
        bkDesc.setText("每日自动备份");
        bkDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        bkDesc.setTextColor(subColor);
        LinearLayout.LayoutParams bkdlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bkdlp.topMargin = dp(act, 4);
        bkLabels.addView(bkDesc, bkdlp);
        LinearLayout.LayoutParams bkllp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bkllp.rightMargin = dp(act, 12);
        bkRow.addView(bkLabels, bkllp);
        Switch bkSw = new HubInsetSwitch(act);
        bkSw.setChecked(Main.isAutoBackup());
        bkSw.setThumbTintList(new android.content.res.ColorStateList(ss,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        bkSw.setTrackTintList(new android.content.res.ColorStateList(ss,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        bkSw.setBackground(null);
        bkSw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                Main.setAutoBackup(checked);
            }
        });
        bkRow.addView(bkSw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(bkRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── Section 9: language ──────────────────────────────────────────
        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act,
                UiLanguage.text(act, "语言", "Language"),
                UiLanguage.effectiveSummary(act), textColor, subColor,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        showLanguagePicker(act, new Runnable() {
                            @Override public void run() {
                                try { dlg.dismiss(); } catch (Throwable ignored) {}
                                showPage(act);
                            }
                        });
                    }
                }));

        // Optional features now live in their normal categories.  There is no separate
        // experimental page; only the individual feature keeps a concise suffix.
        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "主页欢迎语",
                homeGreetingDescription(act), textColor, subColor,
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        showHomeGreetingDialog(act, greetingDescriptionView(view));
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(simpleSwitchRow(act, "原生设置入口 · 实验性",
                "开启：置于 DeepSeek 设置顶部的独立“插件”分组；关闭：回退为悬浮入口。"
                        + "宿主版本变化时可能导致应用闪退，切换后重新进入设置生效。",
                Main.isNativeSettingsEntryEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (reverting) return;
                        if (Main.setNativeSettingsEntryEnabled(checked)) {
                            Toast.makeText(act, UiLanguage.text(act,
                                    "入口模式已保存，重新进入 DeepSeek 设置后生效",
                                    "Entry mode saved; reopen DeepSeek settings to apply"),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                        Toast.makeText(act, UiLanguage.text(act,
                                "入口模式保存失败", "Could not save the entry mode"),
                                Toast.LENGTH_SHORT).show();
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "外观设置",
                "自定义背景、贴纸、气泡、透明度、取景和空间动效。",
                textColor, subColor, new View.OnClickListener() {
                    public void onClick(View v) { ChatAppearanceUi.show(act); }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "自定义 DeepSeek 头像",
                "需要在灰度功能管理里面开启“显示助手头像”，否则聊天页不会显示自定义头像。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        ChatAppearanceUi.showAssistantAvatar(act);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        final TextView[] whaleMotionDetail = new TextView[1];
        card.addView(configurableSwitchRow(act, "鲸鱼图标动效",
                whaleMotionDescription(),
                Main.isWelcomeWhaleMotionEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        Main.setWelcomeWhaleMotionEnabled(checked);
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        showWhaleMotionSpeedSheet(act, whaleMotionDetail[0]);
                    }
                }, whaleMotionDetail));

        card.addView(makeDivider(act, divColor));
        final TextView[] textWaveDetail = new TextView[1];
        final boolean[] restoringTextWave = new boolean[1];
        card.addView(configurableSwitchRow(act, "深海文字波纹",
                textWaveDescription(), Main.isTextWaveEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        if (restoringTextWave[0]) return;
                        if (!Main.setTextWaveEnabled(checked)) {
                            restoringTextWave[0] = true;
                            button.setChecked(!checked);
                            restoringTextWave[0] = false;
                            Toast.makeText(act, UiLanguage.text(act,
                                    "文字波纹设置保存失败",
                                    "Could not save the text-wave setting"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        showTextWaveSpeedSheet(act, textWaveDetail[0]);
                    }
                }, textWaveDetail));

        card.addView(makeDivider(act, divColor));
        card.addView(simpleSwitchRow(act, "解锁专家模式与图片上传 · 实验性",
                "启用专家模型能力，并通过视觉描述中继图片。",
                Main.isExpertUnlock(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        Main.setExpertUnlock(checked);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(simpleSwitchRow(act, "禁用热更新 · 实验性",
                "阻止 DeepSeek 展示普通或强制更新弹窗；不影响商店手动更新。",
                Main.isHotUpdateDisabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        if (reverting) return;
                        if (Main.setHotUpdateDisabled(checked)) return;
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                        Toast.makeText(act, UiLanguage.text(act,
                                "热更新设置保存失败",
                                "Could not save the update setting"),
                                Toast.LENGTH_SHORT).show();
                    }
                }));

        card.addView(makeDivider(act, divColor));
        final int nativeFeatureOverrideCount =
                RemoteFeatureFlags.overriddenCount(act.getClassLoader());
        card.addView(toolActionRow(act, "灰度功能管理器 · 实验性",
                nativeFeatureOverrideCount == 0
                        ? "使用 DeepSeek 原生设置覆盖层；保留宿主自带的全部灰度项目。"
                        : "原生覆盖已管理 " + nativeFeatureOverrideCount + " 项。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        RemoteFeatureFlagsUi.show(act);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        final TextView[] cacheCleanerDetail = new TextView[1];
        card.addView(configurableSwitchRow(act, "自动清理缓存",
                cacheCleanerDescription(), DeepSeekCacheCleaner.isEnabled(),
                textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        if (reverting || DeepSeekCacheCleaner.setEnabled(checked)) return;
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        showCacheCleanerDaysPicker(act, cacheCleanerDetail[0]);
                    }
                }, cacheCleanerDetail));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "进程管理 · Root",
                "查看 DeepSeek 与模块进程，并精确冻结、解冻或杀死。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            Intent intent = new Intent();
                            intent.setClassName("com.dsmod.probe",
                                    "com.dsmod.probe.ProcessManagerActivity");
                            act.startActivity(intent);
                        } catch (Throwable error) {
                            Toast.makeText(act, UiLanguage.text(act,
                                    "无法打开进程管理，请确认模块 APK 已更新",
                                    "Could not open process manager; update the module APK"),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }));

        card.addView(makeDivider(act, divColor));
        final TextView[] heartbeatDetail = new TextView[1];
        card.addView(configurableSwitchRow(act, "AI 心跳 · 实验性",
                proactiveHeartbeatDescription(act), Main.isProactiveHeartbeatEnabled(),
                textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                        if (reverting || Main.setProactiveHeartbeatEnabled(act, checked)) return;
                        reverting = true;
                        b.setChecked(!checked);
                        reverting = false;
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        showProactiveHeartbeatIntervalDialog(act, heartbeatDetail[0]);
                    }
                }, heartbeatDetail));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "Agent · 实验性",
                "管理本地工具、权限模式以及 Root / Shizuku 后端。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View v) { AgentSettingsUi.show(act); }
                }));

        filterCategoryRows(card, category);

        // Footer is appended after filtering so it remains present on every category page.
        card.addView(makeDivider(act, divColor));
        addBuildFooter(act, card, subColor);

        UiLanguage.localizeTree(act, root);
        dlg.setContentView(root);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));
        }
        // 仿 DeepSeek 子页面：从右向左滑入，而非直接覆盖
        trackChildDialog(dlg);
        openWithSlide(dlg, root);
        dlg.setOnKeyListener(new Dialog.OnKeyListener() {
            public boolean onKey(android.content.DialogInterface d, int code, android.view.KeyEvent e) {
                if (code == android.view.KeyEvent.KEYCODE_BACK
                        && e.getAction() == android.view.KeyEvent.ACTION_UP) {
                    slideOutAndDismiss(dlg, root);
                    return true;
                }
                return false;
            }
        });
    }

    /** 从右向左滑入（仿 DeepSeek 子页面转场），dlg 须已 setContentView。 */
    static void openWithSlide(Dialog dlg, View root) {
        int w = root.getResources().getDisplayMetrics().widthPixels;
        root.setTranslationX(w);
        dlg.show();
        root.animate().translationX(0).setDuration(360)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    /** 向右滑出后 dismiss。 */
    static void slideOutAndDismiss(final Dialog dlg, final View root) {
        int w = root.getWidth() > 0 ? root.getWidth()
                : root.getResources().getDisplayMetrics().widthPixels;
        root.animate().translationX(w).setDuration(300)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(new Runnable() {
                    public void run() { try { dlg.dismiss(); } catch (Throwable ignored) {} }
                }).start();
    }

    private static String proactiveHeartbeatDescription(Context context) {
        String interval = String.format(java.util.Locale.getDefault(), UiLanguage.text(context,
                        "当前间隔：%d 分钟。点按此处修改；模型主动消息会写入绑定对话，"
                                + "并在前后台都发送系统通知。聊天中可直接约定每次心跳要做什么，"
                                + "也可让 AI 安排或取消指定时间的一次性心跳。",
                        "Current interval: %d minutes. Tap here to change it. Proactive model "
                                + "messages are added to the bound chat and always produce a "
                                + "system notification. In chat, you can agree on what each "
                                + "heartbeat should do or ask the AI to schedule or cancel a "
                                + "one-time heartbeat for a specific time."),
                Main.proactiveHeartbeatIntervalMinutes());
        String binding = !Main.hasProactiveHeartbeatBinding()
                ? UiLanguage.text(context,
                        "尚未绑定；请在目标对话中告诉 AI 心跳要做什么。",
                        "Not bound yet; tell the AI what heartbeats should do in the target chat.")
                : Main.proactiveHeartbeatBoundToCurrentConversation()
                        ? UiLanguage.text(context,
                                "已绑定当前对话。",
                                "Bound to the current chat.")
                        : UiLanguage.text(context,
                                "已绑定一个对话；在目标对话中重新约定即可切换。",
                                "Bound to a chat; make a new heartbeat agreement in the target chat to switch.");
        return interval + " " + binding;
    }

    private static void showProactiveHeartbeatIntervalDialog(
            final Activity act, final TextView description) {
        final android.widget.EditText input = new android.widget.EditText(act);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(Main.proactiveHeartbeatIntervalMinutes()));
        input.setHint(UiLanguage.text(act, "例如 30、180、1440",
                "For example 30, 180, or 1440"));
        FrameLayout container = new FrameLayout(act);
        container.setPadding(dp(act, 20), 0, dp(act, 20), 0);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new android.app.AlertDialog.Builder(act)
                .setTitle(UiLanguage.text(act,
                        "设置主动消息间隔", "Set proactive-message interval"))
                .setMessage(UiLanguage.text(act,
                        "请输入 15 到 10080 分钟（最长 7 天）。从保存时重新计时；"
                                + "系统省电策略可能让实际触发略有延迟。",
                        "Enter 15 to 10080 minutes (up to 7 days). Timing restarts when "
                                + "you save; Android battery policies may delay delivery slightly."))
                .setView(container)
                .setNegativeButton(UiLanguage.text(act, "取消", "Cancel"), null)
                .setPositiveButton(UiLanguage.text(act, "保存", "Save"),
                        new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(
                                    android.content.DialogInterface ignored, int which) {
                                try {
                                    int minutes = Integer.parseInt(
                                            input.getText().toString().trim());
                                    if (!Main.setProactiveHeartbeatInterval(act, minutes)) {
                                        throw new IllegalArgumentException("out of range");
                                    }
                                    if (description != null) {
                                        description.setText(
                                                proactiveHeartbeatDescription(act));
                                    }
                                    Toast.makeText(act, UiLanguage.text(act,
                                            "主动消息间隔已保存",
                                            "Proactive-message interval saved"),
                                            Toast.LENGTH_SHORT).show();
                                } catch (Throwable error) {
                                    Toast.makeText(act, UiLanguage.text(act,
                                            "请输入 15 到 10080 之间的整数分钟",
                                            "Enter a whole number from 15 to 10080 minutes"),
                                            Toast.LENGTH_LONG).show();
                                }
                            }
                        })
                .show();
    }

    private static TextView protocolPickerOption(Activity act, String title,
            String description, boolean selected, boolean dark) {
        TextView option = new TextView(act);
        option.setText(UiLanguage.dynamic(act,
                title + (selected ? "   ✓" : "") + "\n" + description));
        option.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        option.setTextColor(dark ? 0xFFECECEC : 0xFF1A1A1A);
        option.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        option.setLineSpacing(dp(act, 2), 1f);
        option.setPadding(dp(act, 14), dp(act, 12), dp(act, 14), dp(act, 12));
        option.setClickable(true);
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(act, 6));
        background.setColor(selected ? (dark ? 0xFF2F2F33 : 0xFFEFEFF2)
                : (dark ? 0xFF232326 : 0xFFF7F7F9));
        option.setBackground(background);
        return option;
    }

    private static void showLanguagePicker(final Activity act, final Runnable onChanged) {
        if (act == null || act.isFinishing()) return;
        final boolean dark = isDark(act);
        final Dialog dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 18), dp(act, 16), dp(act, 18), dp(act, 18));
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(dark ? 0xFF2A2A2D : 0xFFFFFFFF);
        rootBg.setCornerRadius(dp(act, 10));
        root.setBackground(rootBg);

        TextView title = new TextView(act);
        title.setText(UiLanguage.text(act, "选择 Deekseep 语言", "Choose Deekseep language"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? 0xFFECECEC : 0xFF1A1A1A);
        title.setPadding(0, 0, 0, dp(act, 12));
        root.addView(title);

        final String current = UiLanguage.currentMode(act);
        TextView automatic = protocolPickerOption(act,
                UiLanguage.text(act, "跟随 DeepSeek（自动）", "Follow DeepSeek (Auto)"),
                UiLanguage.text(act,
                        "DeepSeek 为中文时使用中文；其他任何语言使用英文。",
                        "Use Chinese when DeepSeek is Chinese; use English for every other language."),
                UiLanguage.MODE_AUTO.equals(current), dark);
        TextView chinese = protocolPickerOption(act, "Chinese",
                UiLanguage.text(act, "始终显示中文", "Always display Chinese"),
                UiLanguage.MODE_CHINESE.equals(current), dark);
        TextView english = protocolPickerOption(act, "English",
                UiLanguage.text(act, "始终显示英文", "Always display English"),
                UiLanguage.MODE_ENGLISH.equals(current), dark);
        root.addView(automatic, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams optionLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        optionLp.topMargin = dp(act, 10);
        root.addView(chinese, optionLp);
        LinearLayout.LayoutParams englishLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        englishLp.topMargin = dp(act, 10);
        root.addView(english, englishLp);

        View.OnClickListener choose = new View.OnClickListener() {
            @Override public void onClick(View view) {
                String requested = view.getTag() instanceof String
                        ? (String) view.getTag() : UiLanguage.MODE_AUTO;
                if (!UiLanguage.setMode(act, requested)) {
                    showCustomConfirm(act,
                            UiLanguage.text(act, "语言设置保存失败", "Could not save language"),
                            UiLanguage.text(act,
                                    "DeepSeek 私有目录暂时不可写，请完整重启后重试。",
                                    "DeepSeek's private directory is temporarily unavailable. Fully restart the app and try again."),
                            null, UiLanguage.text(act, "知道了", "Got it"),
                            true, null, null);
                    return;
                }
                dialog.dismiss();
                if (onChanged != null) onChanged.run();
            }
        };
        automatic.setTag(UiLanguage.MODE_AUTO);
        chinese.setTag(UiLanguage.MODE_CHINESE);
        english.setTag(UiLanguage.MODE_ENGLISH);
        automatic.setOnClickListener(choose);
        chinese.setOnClickListener(choose);
        english.setOnClickListener(choose);

        UiLanguage.localizeTree(act, root);
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.42f;
            window.setAttributes(attrs);
            int width = act.getResources().getDisplayMetrics().widthPixels - dp(act, 48);
            window.setLayout(Math.max(dp(act, 280), width),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static TextView sectionTitle(Context context, String value, int color) {
        TextView title = new TextView(context);
        title.setText(UiLanguage.dynamic(context, value));
        title.setTextColor(color);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 8));
        return title;
    }

    private static TextView infoBox(Context context, String value, int color, boolean dark) {
        TextView info = new TextView(context);
        info.setText(UiLanguage.dynamic(context, value));
        info.setTextColor(color);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        info.setTextIsSelectable(true);
        info.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12));
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF202024 : 0xFFF4F6FA);
        background.setCornerRadius(dp(context, 6));
        info.setBackground(background);
        return info;
    }

    private static LinearLayout.LayoutParams insetParams(Context context, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(dp(context, 16), dp(context, top), dp(context, 16), dp(context, bottom));
        return params;
    }

    private static void copyText(Context context, String label, String value) {
        android.content.ClipboardManager clipboard = (android.content.ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(
                android.content.ClipData.newPlainText(label, value == null ? "" : value));
    }

    /** 手工绘制的 API 连接面板，不使用 AlertDialog 或系统确认弹窗。 */
    private static TextView dialogAction(Context context, String label, int color, boolean dark) {
        TextView button = new TextView(context);
        button.setText(UiLanguage.dynamic(context, label));
        button.setTextColor(color);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTypeface(Typeface.DEFAULT);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF2A2A2E : 0xFFF0F2F7);
        background.setCornerRadius(dp(context, 6));
        button.setBackground(background);
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private static View makeDivider(Context c, int color) {
        View v = new View(c);
        v.setBackgroundColor(color);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(dp(c, 16), 0, dp(c, 16), 0);
        v.setLayoutParams(lp);
        return v;
    }

    private static String categoryTitle(int category) {
        switch (category) {
            case CATEGORY_CHAT: return "聊天";
            case CATEGORY_ACCOUNT: return "账号与隐私";
            case CATEGORY_APPEARANCE: return "界面美化";
            case CATEGORY_DEBUG: return "调试";
            default: return "工程";
        }
    }

    private static View simpleSwitchRow(final Activity act, String title, String desc,
            boolean checked, int textColor, int subColor, boolean dark,
            CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));
        LinearLayout labels = new LinearLayout(act);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(act);
        heading.setText(UiLanguage.dynamic(act, title));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        heading.setTextColor(textColor);
        labels.addView(heading);
        TextView detail = new TextView(act);
        detail.setText(UiLanguage.dynamic(act, desc));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setTextColor(subColor);
        labels.addView(detail);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsLp.rightMargin = dp(act, 12);
        row.addView(labels, labelsLp);
        Switch toggle = new HubInsetSwitch(act);
        int[][] states = {{android.R.attr.state_checked},
                {-android.R.attr.state_checked}};
        toggle.setChecked(checked);
        toggle.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        toggle.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        toggle.setBackground(null);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle);
        return row;
    }

    private static View configurableSwitchRow(final Activity act,
            String title, String desc, boolean checked,
            int textColor, int subColor, boolean dark,
            CompoundButton.OnCheckedChangeListener listener,
            View.OnClickListener settingsClick, TextView[] detailOut) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(act, 16), dp(act, 14), dp(act, 8), dp(act, 14));

        LinearLayout labels = new LinearLayout(act);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout titleLine = new LinearLayout(act);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = new TextView(act);
        heading.setText(UiLanguage.dynamic(act, title));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        heading.setTextColor(textColor);
        titleLine.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View settings = plainGearControl(act, textColor,
                UiLanguage.text(act, "设置", "Settings"), settingsClick);
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(
                dp(act, 36), dp(act, 36));
        settingsLp.leftMargin = dp(act, 2);
        titleLine.addView(settings, settingsLp);
        labels.addView(titleLine);
        TextView detail = new TextView(act);
        detail.setText(UiLanguage.dynamic(act, desc));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setTextColor(subColor);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(act, 4);
        labels.addView(detail, detailLp);
        if (detailOut != null && detailOut.length > 0) detailOut[0] = detail;
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsLp.rightMargin = dp(act, 8);
        row.addView(labels, labelsLp);

        Switch toggle = new HubInsetSwitch(act);
        int[][] states = {{android.R.attr.state_checked},
                {-android.R.attr.state_checked}};
        toggle.setChecked(checked);
        toggle.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        toggle.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        toggle.setBackground(null);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle);
        return row;
    }

    private static View taskActionRow(final Activity act, final String title,
            String description, final String hint,
            int textColor, int subColor, final TaskExecutionUi.Task task) {
        final boolean dark = isDark(act);
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(act, 16), dp(act, 14), dp(act, 8), dp(act, 14));
        row.setClickable(true);
        row.setFocusable(true);

        LinearLayout labels = new LinearLayout(act);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView heading = new TextView(act);
        heading.setText(UiLanguage.dynamic(act, title));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        heading.setTextColor(textColor);
        labels.addView(heading);
        TextView detail = new TextView(act);
        detail.setText(UiLanguage.dynamic(act, description));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setTextColor(subColor);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(act, 4);
        labels.addView(detail, detailLp);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsLp.rightMargin = dp(act, 10);
        row.addView(labels, labelsLp);

        View.OnClickListener execute = new View.OnClickListener() {
            @Override public void onClick(View view) {
                TaskExecutionUi.show(act, title, hint, task);
            }
        };
        View control = executeControl(act, textColor, dark,
                UiLanguage.text(act, "执行", "Run"), execute);
        row.addView(control, new LinearLayout.LayoutParams(
                dp(act, 72), dp(act, 40)));
        row.setOnClickListener(execute);
        return row;
    }

    private static View plainGearControl(Activity act, int iconColor,
            String description, View.OnClickListener listener) {
        FrameLayout hit = new FrameLayout(act);
        hit.setClickable(true);
        hit.setFocusable(true);
        hit.setContentDescription(description);
        hit.setOnClickListener(listener);

        View glyph = new HubMaterialGlyphView(act, "ds_action_settings", iconColor);
        FrameLayout.LayoutParams glyphLp = new FrameLayout.LayoutParams(
                dp(act, 18), dp(act, 18), Gravity.CENTER);
        hit.addView(glyph, glyphLp);
        return hit;
    }

    private static View executeControl(Activity act, int iconColor,
            boolean dark, String description, View.OnClickListener listener) {
        FrameLayout hit = new FrameLayout(act);
        hit.setClickable(true);
        hit.setFocusable(true);
        hit.setContentDescription(description);
        hit.setOnClickListener(listener);

        LinearLayout face = new LinearLayout(act);
        face.setOrientation(LinearLayout.HORIZONTAL);
        face.setGravity(Gravity.CENTER);
        face.setPadding(dp(act, 8), 0, dp(act, 10), 0);
        face.setDuplicateParentStateEnabled(true);
        int normal = dark ? 0xFF3A3A3E : 0xFFE8E9EC;
        int pressed = dark ? 0xFF505056 : 0xFFD7D9DE;
        // KSU-style compact action pill: horizontal icon + label, a little larger than a switch.
        face.setBackground(controlBackground(normal, pressed, dp(act, 18)));
        FrameLayout.LayoutParams faceLp = new FrameLayout.LayoutParams(
                dp(act, 68), dp(act, 36), Gravity.CENTER);
        hit.addView(face, faceLp);

        View glyph = new HubMaterialGlyphView(act, "ds_action_execute", iconColor);
        LinearLayout.LayoutParams glyphLp = new LinearLayout.LayoutParams(
                dp(act, 20), dp(act, 20));
        face.addView(glyph, glyphLp);
        TextView label = new TextView(act);
        label.setText(UiLanguage.text(act, "执行", "Action"));
        label.setTextColor(iconColor);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        label.setSingleLine(true);
        label.setMaxLines(1);
        label.setHorizontallyScrolling(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelLp.leftMargin = dp(act, 4);
        face.addView(label, labelLp);
        return hit;
    }

    static android.graphics.drawable.StateListDrawable controlBackground(
            int normal, int pressed, float radius) {
        android.graphics.drawable.StateListDrawable states =
                new android.graphics.drawable.StateListDrawable();
        GradientDrawable down = new GradientDrawable();
        down.setColor(pressed);
        down.setCornerRadius(radius);
        states.addState(new int[]{android.R.attr.state_pressed}, down);
        GradientDrawable idle = new GradientDrawable();
        idle.setColor(normal);
        idle.setCornerRadius(radius);
        states.addState(new int[0], idle);
        states.setEnterFadeDuration(80);
        states.setExitFadeDuration(120);
        return states;
    }

    private static void filterCategoryRows(LinearLayout card, int category) {
        View pendingDivider = null;
        boolean hasVisibleRow = false;
        for (int i = 0; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (!(child instanceof ViewGroup) && !(child instanceof TextView)) {
                child.setVisibility(View.GONE);
                pendingDivider = child;
                continue;
            }
            String text = collectText(child, new StringBuilder()).toString();
            int owner = categoryForText(text);
            boolean visible = owner == category;
            child.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible) {
                if (hasVisibleRow && pendingDivider != null) {
                    pendingDivider.setVisibility(View.VISIBLE);
                }
                hasVisibleRow = true;
                pendingDivider = null;
            }
        }
    }

    private static StringBuilder collectText(View view, StringBuilder out) {
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null) out.append(text).append('\n');
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectText(group.getChildAt(i), out);
            }
        }
        return out;
    }

    private static int categoryForText(String text) {
        if (text == null) return CATEGORY_ENGINEERING;
        if (text.contains("聊天外观") || text.contains("外观设置")
                || text.contains("自定义 DeepSeek 头像")
                || text.contains("鲸鱼图标动效")
                || text.contains("深海文字波纹")
                || text.contains("主页欢迎语")
                || text.contains("原生设置入口")) return CATEGORY_APPEARANCE;
        if (text.contains("记录服务器返回") || text.contains("Hook 日志") || text.contains("记录崩溃")
                || text.contains("崩溃测试") || text.contains("诊断")
                || text.contains("性能统计") || text.contains("事件追踪")
                || text.contains("发送自定义请求")) {
            return CATEGORY_DEBUG;
        }
        if (text.contains("Google 登录") || text.contains("微信与手机号")
                || text.contains("多账号") || text.contains("安全审查")
                || text.contains("本地禁言") || text.contains("伪禁言")
                || text.contains("数据用于优化体验")) {
            return CATEGORY_ACCOUNT;
        }
        if (text.contains("导入提示词") || text.contains("还原设置")
                || text.contains("已开启其他功能，请先关闭后再使用")
                || text.contains("系统提示词") || text.contains("聊天记录多选")
                || text.contains("编辑聊天记录") || text.contains("消息时间与详情")
                || text.contains("自动继续生成")
                || text.contains("导出会话")
                || text.contains("导入聊天记录")
                || text.contains("全局搜索") || text.contains("会话数据统计")
                || text.contains("专家模式") || text.contains("AI 心跳")
                || text.contains("主动消息")) {
            return CATEGORY_CHAT;
        }
        return CATEGORY_ENGINEERING;
    }

    private static String fakeMuteDescription() {
        long until = Main.fakeMuteUntilMillis();
        if (until <= System.currentTimeMillis()) {
            return "尚未设置有效截止时间";
        }
        return (Main.isFakeMuteEnabled() ? "已开启至 " : "已保存，开关未开启 · ")
                + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                .format(new java.util.Date(until));
    }

    private static String cacheCleanerDescription() {
        return "每天检查一次，仅清理 DeepSeek 已确认的图片、Markdown 图表缓存中超过 "
                + DeepSeekCacheCleaner.days() + " 天的文件。";
    }

    private static void showCacheCleanerDaysPicker(final Activity act, final TextView detail) {
        final int[] values = {3, 7, 30};
        final String[] labels = {
                UiLanguage.text(act, "保留 3 天", "Keep 3 days"),
                UiLanguage.text(act, "保留 7 天", "Keep 7 days"),
                UiLanguage.text(act, "保留 30 天", "Keep 30 days")
        };
        int current = DeepSeekCacheCleaner.days();
        int selected = current == 3 ? 0 : current == 30 ? 2 : 1;
        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(act)
                .setTitle(UiLanguage.text(act, "缓存保留时间", "Cache retention"))
                .setSingleChoiceItems(labels, selected, null)
                .setNegativeButton(UiLanguage.text(act, "取消", "Cancel"), null)
                .setPositiveButton(UiLanguage.text(act, "保存", "Save"), null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override public void onShow(android.content.DialogInterface ignored) {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                        .setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View view) {
                                int which = dialog.getListView().getCheckedItemPosition();
                                if (which < 0 || which >= values.length
                                        || !DeepSeekCacheCleaner.setDays(values[which])) {
                                    Toast.makeText(act, UiLanguage.text(act,
                                            "保存失败", "Save failed"),
                                            Toast.LENGTH_SHORT).show();
                                    return;
                                }
                                if (detail != null) detail.setText(
                                        UiLanguage.dynamic(act, cacheCleanerDescription()));
                                dialog.dismiss();
                            }
                        });
            }
        });
        dialog.show();
    }

    private static String whaleMotionDescription() {
        return String.format(java.util.Locale.US,
                "首页鲸鱼持续旋转 · %.1f×", Main.welcomeWhaleMotionSpeed());
    }

    private static String textWaveDescription() {
        return String.format(java.util.Locale.US,
                "右上向左下斜落 · 宽波峰约占字宽 1/3 · %.1f×",
                Main.textWaveSpeed());
    }

    private static String homeGreetingDescription(Context context) {
        String custom = Main.homeGreeting();
        return custom.length() == 0
                ? UiLanguage.text(context, "当前：跟随 DeepSeek 默认文案",
                        "Current: follow DeepSeek's default copy")
                : UiLanguage.text(context, "当前：", "Current: ") + custom;
    }

    private static TextView greetingDescriptionView(View row) {
        if (!(row instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) row;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (!(child instanceof ViewGroup)) continue;
            ViewGroup labels = (ViewGroup) child;
            if (labels.getChildCount() > 1 && labels.getChildAt(1) instanceof TextView) {
                return (TextView) labels.getChildAt(1);
            }
        }
        return null;
    }

    private static void showHomeGreetingDialog(
            final Activity act, final TextView description) {
        final android.widget.EditText input = new android.widget.EditText(act);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setMaxLines(1);
        input.setFilters(new android.text.InputFilter[]{
                new android.text.InputFilter.LengthFilter(60)});
        input.setText(Main.homeGreeting());
        input.setHint(UiLanguage.text(act, "例如：今天想我了没？",
                "For example: Did you miss me today?"));
        FrameLayout container = new FrameLayout(act);
        container.setPadding(dp(act, 20), 0, dp(act, 20), 0);
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        new android.app.AlertDialog.Builder(act)
                .setTitle(UiLanguage.text(act, "自定义主页欢迎语", "Custom home greeting"))
                .setMessage(UiLanguage.text(act,
                        "保存后返回主页即可看到新文案；留空会恢复 DeepSeek 默认文案。",
                        "Return to the home screen after saving to see the new copy. "
                                + "Leave it blank to restore DeepSeek's default."))
                .setView(container)
                .setNegativeButton(UiLanguage.text(act, "取消", "Cancel"), null)
                .setNeutralButton(UiLanguage.text(act, "恢复默认", "Restore default"),
                        new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(
                                    android.content.DialogInterface ignored, int which) {
                                saveHomeGreeting(act, "", description);
                            }
                        })
                .setPositiveButton(UiLanguage.text(act, "保存", "Save"),
                        new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(
                                    android.content.DialogInterface ignored, int which) {
                                saveHomeGreeting(
                                        act, input.getText().toString(), description);
                            }
                        })
                .show();
    }

    private static void saveHomeGreeting(
            Activity act, String value, TextView description) {
        if (!Main.setHomeGreeting(value)) {
            Toast.makeText(act, UiLanguage.text(act,
                    "主页欢迎语保存失败", "Could not save the home greeting"),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (description != null) description.setText(homeGreetingDescription(act));
        Toast.makeText(act, UiLanguage.text(act,
                "主页欢迎语已保存，返回主页后生效",
                "Home greeting saved; return home to apply"),
                Toast.LENGTH_SHORT).show();
    }

    private static void showTextWaveSpeedSheet(
            final Activity act, final TextView description) {
        final float[] selected = new float[]{Main.textWaveSpeed()};
        LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(act, 4), dp(act, 2), dp(act, 4), dp(act, 4));
        final TextView value = new TextView(act);
        value.setText(String.format(java.util.Locale.US, "%.1f×", selected[0]));
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        value.setTextColor(isDark(act) ? 0xFFF2F2F3 : 0xFF1B1B1D);
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, dp(act, 8), 0, dp(act, 8));
        content.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.SeekBar speed = new android.widget.SeekBar(act);
        speed.setMax(130);
        speed.setProgress(Math.round((selected[0] - 0.2f) * 100f));
        speed.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(
                    android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = 0.2f + progress / 100f;
                value.setText(String.format(java.util.Locale.US, "%.1f×", selected[0]));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        content.addView(speed, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 48)));
        TextView range = new TextView(act);
        range.setText(UiLanguage.text(act,
                "0.2× 舒缓                                      1.5× 灵动",
                "0.2× Calm                                      1.5× Lively"));
        range.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        range.setTextColor(isDark(act) ? 0xFFA8A8AD : 0xFF72767D);
        range.setGravity(Gravity.CENTER);
        content.addView(range, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        showMutePickerSheet(act,
                UiLanguage.text(act, "文字波纹速度", "Text-wave speed"), content,
                UiLanguage.text(act, "取消", "Cancel"), null,
                UiLanguage.text(act, "保存", "Save"), new MuteSheetAction() {
                    @Override public void run(Dialog dialog) {
                        if (Main.setTextWaveSpeed(selected[0])) {
                            if (description != null) {
                                description.setText(textWaveDescription());
                            }
                            dialog.dismiss();
                        } else {
                            Toast.makeText(act, UiLanguage.text(act,
                                    "文字波纹速度保存失败",
                                    "Could not save the text-wave speed"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private static void showWhaleMotionSpeedSheet(
            final Activity act, final TextView description) {
        final float[] selected = new float[]{Main.welcomeWhaleMotionSpeed()};
        LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(act, 4), dp(act, 2), dp(act, 4), dp(act, 4));
        final TextView value = new TextView(act);
        value.setText(String.format(java.util.Locale.US, "%.1f×", selected[0]));
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        value.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        value.setTextColor(isDark(act) ? 0xFFF2F2F3 : 0xFF1B1B1D);
        value.setGravity(Gravity.CENTER);
        value.setPadding(0, dp(act, 8), 0, dp(act, 8));
        content.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        android.widget.SeekBar speed = new android.widget.SeekBar(act);
        speed.setMax(90);
        speed.setProgress(Math.round((selected[0] - 0.1f) * 100f));
        speed.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(
                    android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                selected[0] = 0.1f + progress / 100f;
                value.setText(String.format(java.util.Locale.US, "%.1f×", selected[0]));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });
        content.addView(speed, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 48)));
        TextView range = new TextView(act);
        range.setText(UiLanguage.text(act,
                "0.1× 慢速                                      1.0× 快速",
                "0.1× Slow                                      1.0× Fast"));
        range.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        range.setTextColor(isDark(act) ? 0xFFA8A8AD : 0xFF72767D);
        range.setGravity(Gravity.CENTER);
        content.addView(range, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        showMutePickerSheet(act,
                UiLanguage.text(act, "鲸鱼旋转速度", "Whale rotation speed"), content,
                UiLanguage.text(act, "取消", "Cancel"), null,
                UiLanguage.text(act, "保存", "Save"), new MuteSheetAction() {
                    @Override public void run(Dialog dialog) {
                        Main.setWelcomeWhaleMotionSpeed(selected[0]);
                        if (description != null) description.setText(whaleMotionDescription());
                        dialog.dismiss();
                    }
                });
    }

    private static void showFakeMuteTimePicker(final Activity act) {
        showFakeMuteTimePicker(act, null);
    }

    private static void showFakeMuteTimePicker(final Activity act,
                                                final TextView description) {
        final java.util.Calendar selected = java.util.Calendar.getInstance();
        long current = Main.fakeMuteUntilMillis();
        selected.setTimeInMillis(current > System.currentTimeMillis()
                ? current : System.currentTimeMillis() + 24L * 60L * 60L * 1000L);
        showFakeMuteDateStep(act, selected, description);
    }

    private static void showFakeMuteDateStep(final Activity act,
                                             final java.util.Calendar selected,
                                             final TextView description) {
        final android.widget.NumberPicker year = new android.widget.NumberPicker(act);
        final android.widget.NumberPicker month = new android.widget.NumberPicker(act);
        final android.widget.NumberPicker day = new android.widget.NumberPicker(act);
        tuneMutePickerFling(year);
        tuneMutePickerFling(month);
        tuneMutePickerFling(day);
        year.setMinValue(1900);
        year.setMaxValue(3000);
        year.setValue(Math.max(1900, Math.min(3000,
                selected.get(java.util.Calendar.YEAR))));
        year.setWrapSelectorWheel(false);
        month.setMinValue(1);
        month.setMaxValue(12);
        month.setValue(selected.get(java.util.Calendar.MONTH) + 1);
        day.setMinValue(1);
        day.setMaxValue(selected.getActualMaximum(java.util.Calendar.DAY_OF_MONTH));
        day.setValue(Math.min(selected.get(java.util.Calendar.DAY_OF_MONTH), day.getMaxValue()));

        android.widget.NumberPicker.OnValueChangeListener updateDays =
                new android.widget.NumberPicker.OnValueChangeListener() {
                    @Override public void onValueChange(android.widget.NumberPicker picker,
                                                        int oldValue, int newValue) {
                        java.util.Calendar probe = java.util.Calendar.getInstance();
                        probe.clear();
                        probe.set(java.util.Calendar.YEAR, year.getValue());
                        probe.set(java.util.Calendar.MONTH, month.getValue() - 1);
                        int maximum = probe.getActualMaximum(java.util.Calendar.DAY_OF_MONTH);
                        int previous = day.getValue();
                        day.setMaxValue(maximum);
                        day.setValue(Math.min(previous, maximum));
                    }
                };
        year.setOnValueChangedListener(updateDays);
        month.setOnValueChangedListener(updateDays);

        LinearLayout wheels = new LinearLayout(act);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        wheels.setPadding(dp(act, 12), dp(act, 6), dp(act, 12), 0);
        wheels.addView(muteWheelColumn(act, year,
                        UiLanguage.text(act, "\u5e74", "Year")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        wheels.addView(muteWheelColumn(act, month,
                        UiLanguage.text(act, "\u6708", "Month")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        wheels.addView(muteWheelColumn(act, day,
                        UiLanguage.text(act, "\u65e5", "Day")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        showMutePickerSheet(act,
                UiLanguage.text(act, "选择截止日期", "Choose end date"), wheels,
                UiLanguage.text(act, "取消", "Cancel"), null,
                UiLanguage.text(act, "下一步", "Next"), new MuteSheetAction() {
                    @Override public void run(Dialog dialog) {
                        selected.set(java.util.Calendar.YEAR, year.getValue());
                        selected.set(java.util.Calendar.MONTH, month.getValue() - 1);
                        selected.set(java.util.Calendar.DAY_OF_MONTH, day.getValue());
                        dialog.dismiss();
                        showFakeMuteClockStep(act, selected, description);
                    }
                });
    }

    private static void showFakeMuteClockStep(final Activity act,
                                              final java.util.Calendar selected,
                                              final TextView description) {
        final android.widget.NumberPicker hour = new android.widget.NumberPicker(act);
        final android.widget.NumberPicker minute = new android.widget.NumberPicker(act);
        tuneMutePickerFling(hour);
        tuneMutePickerFling(minute);
        hour.setMinValue(0);
        hour.setMaxValue(23);
        hour.setValue(selected.get(java.util.Calendar.HOUR_OF_DAY));
        hour.setFormatter(twoDigitFormatter());
        minute.setMinValue(0);
        minute.setMaxValue(59);
        minute.setValue(selected.get(java.util.Calendar.MINUTE));
        minute.setFormatter(twoDigitFormatter());

        LinearLayout wheels = new LinearLayout(act);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        wheels.setPadding(dp(act, 28), dp(act, 6), dp(act, 28), 0);
        wheels.addView(muteWheelColumn(act, hour,
                        UiLanguage.text(act, "\u65f6", "Hour")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        wheels.addView(muteWheelColumn(act, minute,
                        UiLanguage.text(act, "\u5206", "Minute")),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        showMutePickerSheet(act,
                UiLanguage.text(act, "选择截止时间", "Choose end time"), wheels,
                UiLanguage.text(act, "上一步", "Back"), new MuteSheetAction() {
                    @Override public void run(Dialog dialog) {
                        dialog.dismiss();
                        showFakeMuteDateStep(act, selected, description);
                    }
                }, UiLanguage.text(act, "确定", "OK"), new MuteSheetAction() {
                    @Override public void run(Dialog dialog) {
                        selected.set(java.util.Calendar.HOUR_OF_DAY, hour.getValue());
                        selected.set(java.util.Calendar.MINUTE, minute.getValue());
                        selected.set(java.util.Calendar.SECOND, 0);
                        selected.set(java.util.Calendar.MILLISECOND, 0);
                        if (Main.setFakeMuteUntilMillis(selected.getTimeInMillis())) {
                            dialog.dismiss();
                            if (description != null) {
                                description.setText(fakeMuteDescription());
                            }
                            Toast.makeText(act, Main.isFakeMuteEnabled()
                                            ? UiLanguage.text(act,
                                            "截止时间已更新，本地禁言保持开启",
                                            "Deadline updated; local mute remains enabled")
                                            : UiLanguage.text(act,
                                            "截止时间已保存，打开开关后启用",
                                            "Deadline saved; turn on the switch to enable it"),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(act, "截止时间必须晚于当前时间",
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    private static LinearLayout muteWheelColumn(Activity act,
                                                android.widget.NumberPicker picker,
                                                String localizedLabel) {
        LinearLayout column = new LinearLayout(act);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView label = new TextView(act);
        label.setText(localizedLabel);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTextColor(isDark(act) ? 0xFFA8A8AD : 0xFF72767D);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, 0, 0, dp(act, 4));
        column.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        column.addView(picker, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return column;
    }

    /** Keep the native wheel but let a decisive swipe travel farther and retain momentum. */
    private static void tuneMutePickerFling(android.widget.NumberPicker picker) {
        if (picker == null) return;
        picker.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        try {
            java.lang.reflect.Field field = android.widget.NumberPicker.class
                    .getDeclaredField("mFlingScroller");
            field.setAccessible(true);
            Object value = field.get(picker);
            if (value instanceof android.widget.Scroller) {
                ((android.widget.Scroller) value).setFriction(
                        android.view.ViewConfiguration.getScrollFriction() * 0.42f);
            }
        } catch (Throwable ignored) {}
        try {
            java.lang.reflect.Field maximum = android.widget.NumberPicker.class
                    .getDeclaredField("mMaximumFlingVelocity");
            maximum.setAccessible(true);
            int current = maximum.getInt(picker);
            if (current > 0) maximum.setInt(picker, Math.min(32000, current * 2));
        } catch (Throwable ignored) {}
    }

    private interface MuteSheetAction {
        void run(Dialog dialog);
    }

    private static void showMutePickerSheet(
            final Activity act, String title, View content,
            String secondaryLabel, final MuteSheetAction secondary,
            String primaryLabel, final MuteSheetAction primary) {
        final boolean dark = isDark(act);
        final Dialog dialog = new Dialog(
                act, android.R.style.Theme_Translucent_NoTitleBar);
        final FrameLayout backdrop = new FrameLayout(act);
        backdrop.setBackgroundColor(0x52000000);
        final LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(act, 20), dp(act, 18), dp(act, 20), dp(act, 18));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(dark ? 0xFF28282B : 0xFFFFFFFF);
        panelBackground.setCornerRadii(new float[]{
                dp(act, 24), dp(act, 24), dp(act, 24), dp(act, 24), 0, 0, 0, 0});
        panel.setBackground(panelBackground);
        if (android.os.Build.VERSION.SDK_INT >= 21) panel.setElevation(dp(act, 16));

        TextView heading = new TextView(act);
        heading.setText(title);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        heading.setTextColor(dark ? 0xFFF2F2F3 : 0xFF1B1B1D);
        heading.setPadding(dp(act, 2), 0, dp(act, 2), dp(act, 12));
        panel.addView(heading);
        panel.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(act);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(act, 14), 0, 0);
        TextView secondaryButton = muteSheetButton(
                act, secondaryLabel, dark, false);
        TextView primaryButton = muteSheetButton(
                act, primaryLabel, dark, true);
        actions.addView(secondaryButton);
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(act, 40));
        primaryParams.leftMargin = dp(act, 8);
        actions.addView(primaryButton, primaryParams);
        panel.addView(actions);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        backdrop.addView(panel, panelParams);
        backdrop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) { dialog.dismiss(); }
        });
        panel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {}
        });
        secondaryButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                if (secondary == null) dialog.dismiss();
                else secondary.run(dialog);
            }
        });
        primaryButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                if (primary != null) primary.run(dialog);
            }
        });
        dialog.setContentView(backdrop);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        panel.setTranslationY(dp(act, 36));
        panel.animate().translationY(0f).setDuration(220L).start();
    }

    private static TextView muteSheetButton(
            Activity act, String label, boolean dark, boolean primary) {
        TextView button = new TextView(act);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setTextColor(primary ? 0xFFFFFFFF
                : (dark ? 0xFFE1E1E4 : 0xFF3E4249));
        button.setPadding(dp(act, 16), 0, dp(act, 16), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? BRAND : (dark ? 0xFF38383C : 0xFFF0F1F4));
        background.setCornerRadius(dp(act, 10));
        button.setBackground(background);
        button.setClickable(true);
        button.setFocusable(true);
        button.setMinHeight(dp(act, 40));
        return button;
    }

    private static android.widget.NumberPicker.Formatter twoDigitFormatter() {
        return new android.widget.NumberPicker.Formatter() {
            @Override public String format(int value) {
                return String.format(java.util.Locale.US, "%02d", value);
            }
        };
    }

    /** 仿"编辑聊天记录"样式的可点击行（标题+说明+右箭头）。 */
    private static View toolActionRow(final Activity act, String title, String desc,
                                      int textColor, int subColor, View.OnClickListener onClick) {
        return toolActionRow(act, null, title, desc, textColor, subColor, onClick);
    }

    private static View toolActionRow(final Activity act, String iconName,
                                      String title, String desc,
                                      int textColor, int subColor,
                                      View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        // The home hub uses DeepSeek's compact native settings-row rhythm. Rows elsewhere keep
        // their roomier reading layout because they often carry longer explanations.
        final boolean compactHubRow = iconName != null && iconName.length() > 0;
        final int verticalPadding = compactHubRow ? 10 : 16;
        row.setPadding(dp(act, 16), dp(act, verticalPadding),
                dp(act, 16), dp(act, verticalPadding));
        if (compactHubRow) row.setMinimumHeight(dp(act, 48));
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(controlBackground(
                0x00000000,
                isDark(act) ? 0x24FFFFFF : 0x14000000,
                0f));

        if (iconName != null && iconName.length() > 0) {
            View icon = createHubIcon(act, iconName, textColor);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    dp(act, 27), dp(act, 27));
            iconLp.rightMargin = dp(act, 14);
            row.addView(icon, iconLp);
        }

        if (iconName != null && iconName.length() > 0) {
            android.widget.ImageView icon = new android.widget.ImageView(act);
            android.graphics.drawable.Drawable drawable = moduleDrawable(act, iconName);
            if (drawable != null) {
                drawable = drawable.mutate();
                drawable.setTint(textColor);
                icon.setImageDrawable(drawable);
            }
            icon.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
                    dp(act, 27), dp(act, 27));
            iconLp.rightMargin = dp(act, 14);
            row.addView(icon, iconLp);
        }

        LinearLayout labels = new LinearLayout(act);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(act);
        t.setText(UiLanguage.dynamic(act, title));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, compactHubRow ? 15 : 16);
        t.setTextColor(textColor);
        labels.addView(t, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView d = new TextView(act);
        d.setText(UiLanguage.dynamic(act, desc));
        d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        d.setTextColor(subColor);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(act, compactHubRow ? 2 : 4);
        if (desc == null || desc.trim().length() == 0) d.setVisibility(View.GONE);
        labels.addView(d, dlp);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(act);
        arrow.setText("\u203A");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        arrow.setTextColor(subColor);
        row.addView(arrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setOnClickListener(onClick);
        return row;
    }

    /** Uses DeepSeek's own vectors first; the bundled official Material path is the fallback. */
    private static View createHubIcon(Context context, String name, int color) {
        FrameLayout holder = new FrameLayout(context);
        String hostName = hostDrawableForHubIcon(name);
        View glyph = null;
        if (hostName != null) {
            try {
                int id = context.getResources().getIdentifier(
                        hostName, "drawable", "com.deepseek.chat");
                if (id != 0) {
                    android.graphics.drawable.Drawable drawable =
                            context.getResources().getDrawable(id).mutate();
                    if (android.os.Build.VERSION.SDK_INT >= 21) drawable.setTint(color);
                    android.widget.ImageView image = new android.widget.ImageView(context);
                    image.setImageDrawable(drawable);
                    image.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
                    image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    glyph = image;
                }
            } catch (Throwable ignored) {}
        }
        if (glyph == null) glyph = new HubMaterialGlyphView(context, name, color);
        // Previous category glyphs filled 27dp. An 18dp visual is exactly one third smaller,
        // while the 27dp holder preserves the established text alignment and hit rhythm.
        FrameLayout.LayoutParams visual = new FrameLayout.LayoutParams(
                dp(context, 18), dp(context, 18), Gravity.CENTER);
        holder.addView(glyph, visual);
        return holder;
    }

    private static String hostDrawableForHubIcon(String name) {
        if ("ds_category_chat".equals(name)) return "ic_ds_new_chat_outline_20";
        if ("ds_category_account".equals(name)) return "ic_profile";
        if ("ds_category_appearance".equals(name)) return "ic_enhance_outline_20";
        if ("ds_category_debug".equals(name)) return "ic_warning_outline_20";
        if ("ds_category_engineering".equals(name)) return "ic_branch_outline_20";
        if ("ds_category_help".equals(name)) return "ic_help_outline";
        return null;
    }

    private static android.graphics.drawable.Drawable moduleDrawable(
            Context context, String name) {
        try {
            Context module = context.createPackageContext(
                    "com.dsmod.probe", Context.CONTEXT_IGNORE_SECURITY);
            int id = module.getResources().getIdentifier(
                    name, "drawable", "com.dsmod.probe");
            if (id != 0) {
                return android.os.Build.VERSION.SDK_INT >= 21
                        ? module.getResources().getDrawable(id, module.getTheme())
                        : module.getResources().getDrawable(id);
            }
        } catch (Throwable ignored) {
            // The host may not be allowed to create the module package context.
        }
        java.io.InputStream input = null;
        try {
            ClassLoader loader = DeekseepUi.class.getClassLoader();
            if (loader == null) return null;
            input = loader.getResourceAsStream("sponsor_qr".equals(name)
                    ? "META-INF/com.dsmod.probe.project/sponsor_qr.png"
                    : "META-INF/com.dsmod.probe.icons/" + name + ".png");
            if (input == null) return null;
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(input);
            return bitmap == null ? null
                    : new android.graphics.drawable.BitmapDrawable(
                            context.getResources(), bitmap);
        } catch (Throwable ignored) {
            return null;
        } finally {
            if (input != null) try { input.close(); } catch (Throwable ignored) {}
        }
    }

    private static void showSponsorDialog(final Activity act) {
        final String sponsor = "https://afdian.com/a/lllucccian";
        new android.app.AlertDialog.Builder(act)
                .setTitle(UiLanguage.text(act, "赞助开发者", "Sponsor the developer"))
                .setItems(new String[]{
                                UiLanguage.text(act, "通过爱发电赞助", "Sponsor via Afdian"),
                                UiLanguage.text(act, "通过微信赞助", "Sponsor via WeChat")},
                        new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(
                                    android.content.DialogInterface dialog, int which) {
                                if (which == 0) {
                                    try {
                                        act.startActivity(new Intent(
                                                Intent.ACTION_VIEW, Uri.parse(sponsor)));
                                    } catch (Throwable error) {
                                        Toast.makeText(act, sponsor, Toast.LENGTH_LONG).show();
                                    }
                                    return;
                                }
                                android.widget.ImageView image = new android.widget.ImageView(act);
                                image.setAdjustViewBounds(true);
                                image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                                image.setPadding(dp(act, 16), dp(act, 8), dp(act, 16), 0);
                                android.graphics.drawable.Drawable qr =
                                        moduleDrawable(act, "sponsor_qr");
                                if (qr != null) image.setImageDrawable(qr);
                                new android.app.AlertDialog.Builder(act)
                                        .setTitle(UiLanguage.text(act,
                                                "微信赞赏码", "WeChat donation code"))
                                        .setView(image)
                                        .setPositiveButton(UiLanguage.text(
                                                act, "完成", "Done"), null)
                                        .show();
                            }
                        })
                .setNegativeButton(UiLanguage.text(act, "取消", "Cancel"), null)
                .show();
    }

    private static void showExperimentalHelpPage(final Activity act) {
        final String[][] items = {
            {"【功能】聊天背景、贴纸与气泡", "导入图片并调整取景、缩放、透明度和界面。"},
            {"【功能】空间动效", "控制背景视差、层次和动态强度。"},
            {"【功能】专家模式图片上传", "把会话图片交给视觉模型，再继续专家对话。"},
            {"【功能】AI 主动消息", "按当前对话设置周期或一次性心跳提醒。"},
            {"【功能】问答工具", "模型提出选项，你选择或输入后继续。"},
            {"【功能】Agent 工具与权限", "管理工具开关、执行权限和 Shizuku/Root 模式。"},
            {"【问题】背景图或贴纸没有显示？", "检查聊天外观开关和当前界面绑定，必要时重启 DeepSeek。"},
            {"【问题】心跳没有写入对话？", "确认目标对话已绑定，并允许通知和后台活动。"},
            {"【问题】Agent 只输出调用文字？", "开启 Agent 和对应工具权限，再重新发送请求。"},
        };

        showHelpItemsPage(act, "实验性功能 · 帮助与问题",
                "功能说明与必要排查。点一下条目展开。", items);
    }

    private static void showHelpItemsPage(final Activity act, String pageTitle,
                                          String hint, String[][] items) {
        final boolean dark = isDark(act);
        final int bgColor = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFF9A9A9E : 0xFF888888;
        final int divColor = dark ? 0xFF3A3A3D : 0xFFEEEEEE;
        final LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);
        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(barColor);
        int statusTop = statusBarHeight(act);
        bar.setPadding(dp(act, 8), statusTop, dp(act, 16), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 56) + statusTop));
        final Dialog dialog = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        TextView back = new TextView(act);
        back.setText("\u2039");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setTextColor(textColor);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(act, 8), 0, dp(act, 8), 0);
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { slideOutAndDismiss(dialog, root); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(act, 40)));
        TextView title = new TextView(act);
        title.setText(UiLanguage.dynamic(act, pageTitle));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = dp(act, 8);
        bar.addView(title, titleLp);
        android.widget.ScrollView scroll = new android.widget.ScrollView(act);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(dp(act, 12));
        card.setBackground(cardBg);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16));
        scroll.addView(card, cardLp);
        buildAccordionItems(act, card, textColor, subColor, divColor, hint, items);
        addBuildFooter(act, card, subColor);
        UiLanguage.localizeTree(act, root);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));
        }
        openWithSlide(dialog, root);
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(android.content.DialogInterface d, int code,
                                           android.view.KeyEvent event) {
                if (code == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    slideOutAndDismiss(dialog, root);
                    return true;
                }
                return false;
            }
        });
    }

    private static void buildAccordionItems(final Activity act, LinearLayout card,
                                            final int textColor, final int subColor,
                                            int divColor, String hint, final String[][] items) {
        TextView headerHint = new TextView(act);
        headerHint.setText(UiLanguage.dynamic(act, hint));
        headerHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        headerHint.setTextColor(subColor);
        headerHint.setPadding(dp(act, 16), dp(act, 14), dp(act, 16), dp(act, 8));
        card.addView(headerHint);
        final java.util.List<View> bodies = new java.util.ArrayList<View>();
        final java.util.List<TextView> arrows = new java.util.ArrayList<TextView>();
        for (int i = 0; i < items.length; i++) {
            if (i > 0) card.addView(makeDivider(act, divColor));
            LinearLayout titleRow = new LinearLayout(act);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 16), dp(act, 14));
            titleRow.setClickable(true);
            TextView itemTitle = new TextView(act);
            itemTitle.setText("• " + UiLanguage.dynamic(act, items[i][0]));
            itemTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            itemTitle.setTextColor(textColor);
            titleRow.addView(itemTitle, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            TextView arrow = new TextView(act);
            arrow.setText("\u203A");
            arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            arrow.setTextColor(subColor);
            titleRow.addView(arrow);
            card.addView(titleRow);
            TextView itemBody = new TextView(act);
            itemBody.setText(UiLanguage.dynamic(act, items[i][1]));
            itemBody.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            itemBody.setTextColor(subColor);
            itemBody.setLineSpacing(dp(act, 2), 1f);
            itemBody.setPadding(dp(act, 16), 0, dp(act, 16), dp(act, 14));
            itemBody.setVisibility(View.GONE);
            card.addView(itemBody);
            final int index = i;
            bodies.add(itemBody);
            arrows.add(arrow);
            titleRow.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    boolean open = bodies.get(index).getVisibility() != View.VISIBLE;
                    for (int j = 0; j < bodies.size(); j++) {
                        if (j != index && bodies.get(j).getVisibility() == View.VISIBLE) {
                            animateExpand(bodies.get(j), false);
                            arrows.get(j).animate().rotation(0f).setDuration(200).start();
                        }
                    }
                    animateExpand(bodies.get(index), open);
                    arrows.get(index).animate().rotation(open ? 90f : 0f)
                            .setDuration(200).start();
                }
            });
        }
    }

    /** 主页仅放一行"帮助与问题"入口；点后从右滑入二级页，标题在二级页里。 */
    private static void addHelpSection(final Activity act, LinearLayout card,
                                       final int textColor, final int subColor,
                                       int divColor, boolean dark) {
        card.addView(toolActionRow(act, "帮助与问题", "功能说明、常见提示与对应解决办法",
                textColor, subColor, new View.OnClickListener() {
            public void onClick(View v) { showHelpPage(act); }
        }));
    }

    static void addBuildFooter(Activity act, LinearLayout card, int subColor) {
        TextView info = new TextView(act);
        info.setText(UiLanguage.dynamic(act, "模块版本：" + BuildInfo.MODULE_VERSION
                + "\nXposed interface：" + BuildInfo.API_VERSION
                + "\n编译时间：" + BuildInfo.BUILD_DATE
                + "\nDeepSeek 版本：" + installedVersion(act, act.getPackageName())));
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        info.setTextColor(subColor);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(act, 16), dp(act, 14), dp(act, 16), dp(act, 18));
        final long[] clickWindow = new long[]{0L};
        final int[] clickCount = new int[]{0};
        info.setClickable(true);
        info.setFocusable(true);
        info.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                long now = System.currentTimeMillis();
                clickCount[0] = (now - clickWindow[0] <= 1200L)
                        ? clickCount[0] + 1 : 1;
                clickWindow[0] = now;
                if (clickCount[0] >= 3) {
                    clickCount[0] = 0;
                    android.widget.Toast.makeText(act,
                            "被你发现彩蛋了喵～", android.widget.Toast.LENGTH_SHORT).show();
                    showEasterEggPage(act);
                }
            }
        });
        card.addView(info, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Hidden page reached only through the three-tap footer easter egg on every channel. */
    private static void showEasterEggPage(final Activity act) {
        if (act == null || act.isFinishing()) return;
        if (!HostCompat.isGooglePlay()) Main.ensureEmbeddedPromptInstalled(act);
        final boolean dark = isDark(act);
        final int bg = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int bar = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int sub = dark ? 0xFFAAAAAF : 0xFF70757D;
        final int divider = dark ? 0xFF3A3A3D : 0xFFEEEEEE;
        final Dialog dialog = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(bar);
        int statusTop = statusBarHeight(act);
        top.setPadding(dp(act, 8), statusTop, dp(act, 16), 0);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 56) + statusTop));
        TextView back = new TextView(act);
        back.setText("\u2039");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setTextColor(text);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(act, 8), 0, dp(act, 8), 0);
        top.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(act, 40)));
        TextView title = new TextView(act);
        title.setText("隐藏彩蛋");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(text);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(act, 8);
        top.addView(title, titleParams);

        android.widget.ScrollView scroll = new android.widget.ScrollView(act);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(dp(act, 12));
        card.setBackground(cardBg);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 20));
        scroll.addView(card, cardParams);

        TextView note = new TextView(act);
        note.setText("此页面保留快捷开关。液态玻璃会按设备能力选择实时折射、共享模糊或静态磨砂。");
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setTextColor(sub);
        note.setLineSpacing(dp(act, 2), 1f);
        note.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 12));
        card.addView(note);

        LinearLayout glassRow = new LinearLayout(act);
        glassRow.setOrientation(LinearLayout.HORIZONTAL);
        glassRow.setGravity(Gravity.CENTER_VERTICAL);
        glassRow.setPadding(dp(act, 16), dp(act, 13), dp(act, 12), dp(act, 13));
        LinearLayout glassLabels = new LinearLayout(act);
        glassLabels.setOrientation(LinearLayout.VERTICAL);
        glassLabels.addView(labelText(act, "启用全局液态玻璃", 15, text, true));
        glassLabels.addView(labelText(act,
                "按 Android 版本、性能和节电状态自动降级",
                12, sub, false));
        glassRow.addView(glassLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch glass = new HubInsetSwitch(act);
        tintSwitch(glass, dark);
        glass.setChecked(ChatAppearance.load().liquidGlassEnabled);
        glass.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                ChatAppearance.Config config = ChatAppearance.load();
                config.liquidGlassEnabled = checked;
                if (!ChatAppearance.save(config)) {
                    button.setChecked(!checked);
                    android.widget.Toast.makeText(act, "液态玻璃设置保存失败",
                            android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
        glassRow.addView(glass);
        card.addView(glassRow);
        card.addView(makeDivider(act, divider));

        LinearLayout shakeRow = new LinearLayout(act);
        shakeRow.setOrientation(LinearLayout.HORIZONTAL);
        shakeRow.setGravity(Gravity.CENTER_VERTICAL);
        shakeRow.setPadding(dp(act, 16), dp(act, 13), dp(act, 12), dp(act, 13));
        LinearLayout shakeLabels = new LinearLayout(act);
        shakeLabels.setOrientation(LinearLayout.VERTICAL);
        shakeLabels.addView(labelText(act, "陀螺仪背景", 15, text, true));
        final TextView shakeStateLabel = labelText(act,
                ChatAppearance.load().shakeParallaxEnabled
                        ? "已开启 · 点按进入专属设置"
                        : "已关闭 · 点按进入专属设置",
                12, sub, false);
        shakeLabels.addView(shakeStateLabel);
        shakeRow.addView(shakeLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final boolean[] spatialToggleSync = new boolean[1];
        final Switch[] spatialToggleRef = new Switch[1];
        final TextView[] spatialStateRef = new TextView[1];
        final Switch shake = new HubInsetSwitch(act);
        tintSwitch(shake, dark);
        shake.setChecked(ChatAppearance.load().shakeParallaxEnabled);
        shake.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (spatialToggleSync[0]) return;
                ChatAppearance.Config config = ChatAppearance.load();
                config.shakeParallaxEnabled = checked;
                if (checked) config.spatialDepthEnabled = false;
                if (!ChatAppearance.save(config)) {
                    spatialToggleSync[0] = true;
                    button.setChecked(!checked);
                    spatialToggleSync[0] = false;
                    android.widget.Toast.makeText(act, "陀螺仪背景设置保存失败",
                            android.widget.Toast.LENGTH_SHORT).show();
                } else if (checked && spatialToggleRef[0] != null) {
                    spatialToggleSync[0] = true;
                    spatialToggleRef[0].setChecked(false);
                    spatialToggleSync[0] = false;
                    if (spatialStateRef[0] != null) {
                        spatialStateRef[0].setText(
                                "已关闭 · 点按进入专属设置");
                    }
                }
                shakeStateLabel.setText(checked
                        ? "已开启 · 点按进入专属设置"
                        : "已关闭 · 点按进入专属设置");
            }
        });
        shakeRow.addView(shake);
        shakeRow.setClickable(true);
        shakeRow.setFocusable(true);
        shakeRow.setBackground(controlBackground(
                0x00000000, dark ? 0x24FFFFFF : 0x14000000, 0f));
        shakeRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                ShakeParallaxUi.show(act,
                        new ShakeParallaxUi.StateListener() {
                            @Override public void onShakeStateChanged(boolean enabled) {
                                spatialToggleSync[0] = true;
                                shake.setChecked(enabled);
                                if (enabled && spatialToggleRef[0] != null) {
                                    spatialToggleRef[0].setChecked(false);
                                }
                                spatialToggleSync[0] = false;
                                shakeStateLabel.setText(enabled
                                        ? "已开启 · 点按进入专属设置"
                                        : "已关闭 · 点按进入专属设置");
                                if (enabled && spatialStateRef[0] != null) {
                                    spatialStateRef[0].setText(
                                            "已关闭 · 点按进入专属设置");
                                }
                            }
                        });
            }
        });
        card.addView(shakeRow);
        card.addView(makeDivider(act, divider));

        LinearLayout spatialRow = new LinearLayout(act);
        spatialRow.setOrientation(LinearLayout.HORIZONTAL);
        spatialRow.setGravity(Gravity.CENTER_VERTICAL);
        spatialRow.setPadding(dp(act, 16), dp(act, 13), dp(act, 12), dp(act, 13));
        LinearLayout spatialLabels = new LinearLayout(act);
        spatialLabels.setOrientation(LinearLayout.VERTICAL);
        spatialLabels.addView(labelText(
                act, "空间动效（实验）", 15, text, true));
        final TextView spatialStateLabel = labelText(
                act,
                ChatAppearance.load().spatialDepthEnabled
                        ? "已开启 · 点按进入专属设置"
                        : "已关闭 · 点按进入专属设置",
                12, sub, false);
        spatialStateRef[0] = spatialStateLabel;
        spatialLabels.addView(spatialStateLabel);
        spatialRow.addView(spatialLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch spatial = new HubInsetSwitch(act);
        spatialToggleRef[0] = spatial;
        tintSwitch(spatial, dark);
        spatial.setChecked(ChatAppearance.load().spatialDepthEnabled);
        spatial.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (spatialToggleSync[0]) return;
                        ChatAppearance.Config config = ChatAppearance.load();
                        config.spatialDepthEnabled = checked;
                        if (checked) config.shakeParallaxEnabled = false;
                        if (!ChatAppearance.save(config)) {
                            spatialToggleSync[0] = true;
                            button.setChecked(!checked);
                            spatialToggleSync[0] = false;
                            android.widget.Toast.makeText(
                                    act, "空间动效设置保存失败",
                                    android.widget.Toast.LENGTH_SHORT).show();
                        } else if (checked) {
                            spatialToggleSync[0] = true;
                            shake.setChecked(false);
                            spatialToggleSync[0] = false;
                        }
                    }
                });
        spatialRow.addView(labelText(act, "›", 24, sub, false));
        spatialRow.setClickable(true);
        spatialRow.setFocusable(true);
        spatialRow.setBackground(controlBackground(
                0x00000000, dark ? 0x24FFFFFF : 0x14000000, 0f));
        spatialRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                SpatialMotionUi.show(
                        act, new SpatialMotionUi.StateListener() {
                            @Override public void onSpatialStateChanged(
                                    boolean enabled) {
                                spatialToggleSync[0] = true;
                                spatial.setChecked(enabled);
                                if (enabled) shake.setChecked(false);
                                spatialToggleSync[0] = false;
                                spatialStateLabel.setText(enabled
                                        ? "已开启 · 点按进入专属设置"
                                        : "已关闭 · 点按进入专属设置");
                            }
                        });
            }
        });
        card.addView(spatialRow);
        card.addView(makeDivider(act, divider));

        LinearLayout strengthRow = new LinearLayout(act);
        strengthRow.setOrientation(LinearLayout.HORIZONTAL);
        strengthRow.setGravity(Gravity.CENTER_VERTICAL);
        strengthRow.setPadding(dp(act, 16), dp(act, 13), dp(act, 16), dp(act, 13));
        LinearLayout strengthLabels = new LinearLayout(act);
        strengthLabels.setOrientation(LinearLayout.VERTICAL);
        strengthLabels.addView(labelText(act, "动效强度", 15, text, true));
        final TextView strengthValue = labelText(
                act, spatialStrengthLabel(
                        act, ChatAppearance.load().spatialStrength),
                12, sub, false);
        strengthLabels.addView(strengthValue);
        strengthRow.addView(strengthLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView strengthChevron = labelText(act, "›", 24, sub, false);
        strengthRow.addView(strengthChevron);
        strengthRow.setClickable(true);
        strengthRow.setFocusable(true);
        strengthRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                final String[] values = {"weak", "standard", "strong"};
                final String[] labels = {
                        UiLanguage.text(act, "弱", "Weak"),
                        UiLanguage.text(act, "标准", "Standard"),
                        UiLanguage.text(act, "稍强", "Slightly stronger")
                };
                String current = ChatAppearance.load().spatialStrength;
                int selected = "weak".equals(current)
                        ? 0 : ("strong".equals(current) ? 2 : 1);
                new android.app.AlertDialog.Builder(act)
                        .setTitle(UiLanguage.text(
                                act, "动效强度", "Motion strength"))
                        .setSingleChoiceItems(labels, selected,
                                new android.content.DialogInterface.OnClickListener() {
                                    @Override public void onClick(
                                            android.content.DialogInterface dialog,
                                            int which) {
                                        if (which < 0 || which >= values.length) return;
                                        ChatAppearance.Config config =
                                                ChatAppearance.load();
                                        config.spatialStrength = values[which];
                                        if (ChatAppearance.save(config)) {
                                            strengthValue.setText(
                                                    spatialStrengthLabel(
                                                            act, values[which]));
                                            dialog.dismiss();
                                        } else {
                                            Toast.makeText(act,
                                                    "动效强度设置保存失败",
                                                    Toast.LENGTH_SHORT).show();
                                        }
                                    }
                                })
                        .setNegativeButton(
                                UiLanguage.text(act, "取消", "Cancel"), null)
                        .show();
            }
        });

        LinearLayout reduceRow = new LinearLayout(act);
        reduceRow.setOrientation(LinearLayout.HORIZONTAL);
        reduceRow.setGravity(Gravity.CENTER_VERTICAL);
        reduceRow.setPadding(dp(act, 16), dp(act, 13), dp(act, 12), dp(act, 13));
        LinearLayout reduceLabels = new LinearLayout(act);
        reduceLabels.setOrientation(LinearLayout.VERTICAL);
        reduceLabels.addView(labelText(act, "减少动态效果", 15, text, true));
        reduceLabels.addView(labelText(act,
                "关闭传感器视差，保留背景防露边处理",
                12, sub, false));
        reduceRow.addView(reduceLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch reduceMotion = new HubInsetSwitch(act);
        final boolean[] reduceMotionSync = new boolean[1];
        tintSwitch(reduceMotion, dark);
        reduceMotion.setChecked(
                ChatAppearance.load().spatialReduceMotion);
        reduceMotion.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (reduceMotionSync[0]) return;
                        ChatAppearance.Config config =
                                ChatAppearance.load();
                        config.spatialReduceMotion = checked;
                        if (!ChatAppearance.save(config)) {
                            reduceMotionSync[0] = true;
                            button.setChecked(!checked);
                            reduceMotionSync[0] = false;
                            Toast.makeText(act,
                                    "减少动态效果设置保存失败",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        reduceRow.addView(reduceMotion);

        LinearLayout recenterRow = new LinearLayout(act);
        recenterRow.setOrientation(LinearLayout.HORIZONTAL);
        recenterRow.setGravity(Gravity.CENTER_VERTICAL);
        recenterRow.setPadding(dp(act, 16), dp(act, 13), dp(act, 12), dp(act, 13));
        LinearLayout recenterLabels = new LinearLayout(act);
        recenterLabels.setOrientation(LinearLayout.VERTICAL);
        recenterLabels.addView(labelText(
                act, "自动重新校准", 15, text, true));
        recenterLabels.addView(labelText(act,
                "稳定约 650ms 后缓慢修正小范围零点误差",
                12, sub, false));
        recenterRow.addView(recenterLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch autoRecenter = new HubInsetSwitch(act);
        final boolean[] autoRecenterSync = new boolean[1];
        tintSwitch(autoRecenter, dark);
        autoRecenter.setChecked(
                ChatAppearance.load().spatialAutoRecenter);
        autoRecenter.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (autoRecenterSync[0]) return;
                        ChatAppearance.Config config =
                                ChatAppearance.load();
                        config.spatialAutoRecenter = checked;
                        if (!ChatAppearance.save(config)) {
                            autoRecenterSync[0] = true;
                            button.setChecked(!checked);
                            autoRecenterSync[0] = false;
                            Toast.makeText(act,
                                    "自动重新校准设置保存失败",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        recenterRow.addView(autoRecenter);

        LinearLayout directionRow = new LinearLayout(act);
        directionRow.setOrientation(LinearLayout.HORIZONTAL);
        directionRow.setGravity(Gravity.CENTER_VERTICAL);
        directionRow.setPadding(dp(act, 16), dp(act, 13), dp(act, 12), dp(act, 13));
        LinearLayout directionLabels = new LinearLayout(act);
        directionLabels.setOrientation(LinearLayout.VERTICAL);
        directionLabels.addView(labelText(
                act, "反转动效方向", 15, text, true));
        directionLabels.addView(labelText(act,
                "统一反转背景图的上下左右视差方向",
                12, sub, false));
        directionRow.addView(directionLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch reverseDirection = new HubInsetSwitch(act);
        final boolean[] reverseDirectionSync = new boolean[1];
        tintSwitch(reverseDirection, dark);
        reverseDirection.setChecked(
                ChatAppearance.load().spatialDirectionMultiplier < 0f);
        reverseDirection.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (reverseDirectionSync[0]) return;
                        ChatAppearance.Config config =
                                ChatAppearance.load();
                        config.spatialDirectionMultiplier =
                                checked ? -1f : 1f;
                        if (!ChatAppearance.save(config)) {
                            reverseDirectionSync[0] = true;
                            button.setChecked(!checked);
                            reverseDirectionSync[0] = false;
                            Toast.makeText(act,
                                    "动效方向设置保存失败",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        directionRow.addView(reverseDirection);

        LinearLayout manualRecenterRow = new LinearLayout(act);
        manualRecenterRow.setOrientation(LinearLayout.VERTICAL);
        manualRecenterRow.setPadding(
                dp(act, 16), dp(act, 13), dp(act, 16), dp(act, 13));
        manualRecenterRow.addView(labelText(
                act, "立即重新校准", 15, text, true));
        manualRecenterRow.addView(labelText(act,
                "将当前持机姿态设为视觉中心",
                12, sub, false));
        manualRecenterRow.setClickable(true);
        manualRecenterRow.setFocusable(true);
        manualRecenterRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                ChatAppearance.recenterSpatialMotion();
                Toast.makeText(act, "已请求重新校准",
                        Toast.LENGTH_SHORT).show();
            }
        });

        if (!HostCompat.isGooglePlay()) {
            LinearLayout promptRow = new LinearLayout(act);
            promptRow.setOrientation(LinearLayout.HORIZONTAL);
            promptRow.setGravity(Gravity.CENTER_VERTICAL);
            promptRow.setPadding(dp(act, 16), dp(act, 13), dp(act, 12), dp(act, 13));
            LinearLayout promptLabels = new LinearLayout(act);
            promptLabels.setOrientation(LinearLayout.VERTICAL);
            promptLabels.addView(labelText(act, "一键破甲", 15, text, true));
            promptLabels.addView(labelText(act,
                    "懂你意思喵～",
                    12, sub, false));
            promptRow.addView(promptLabels, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final Switch prompt = new HubInsetSwitch(act);
            tintSwitch(prompt, dark);
            // The bundled prompt is opt-in and must start disabled on a fresh install.
            prompt.setChecked(Main.isEmbeddedPromptEnabled());
            prompt.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                    if (!Main.setEmbeddedPromptEnabled(act, checked)) {
                        button.setChecked(!checked);
                        android.widget.Toast.makeText(act, "内置提示词启用失败",
                                android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        refreshPromptControls();
                    }
                }
            });
            promptRow.addView(prompt);
            card.addView(promptRow);
        }

        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { slideOutAndDismiss(dialog, root); }
        });
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
        }
        openWithSlide(dialog, root);
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(android.content.DialogInterface d, int code,
                                           android.view.KeyEvent event) {
                if (code == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    slideOutAndDismiss(dialog, root);
                    return true;
                }
                return false;
            }
        });
    }

    private static TextView labelText(Activity act, String value, float size,
                                      int color, boolean bold) {
        TextView view = new TextView(act);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static String spatialStrengthLabel(
            Context context, String value) {
        if ("weak".equals(value)) {
            return UiLanguage.text(
                    context, "弱（0.55×）", "Weak (0.55×)");
        }
        if ("strong".equals(value)) {
            return UiLanguage.text(
                    context, "稍强（1.25×）", "Slightly stronger (1.25×)");
        }
        return UiLanguage.text(
                context, "标准（1.0×）", "Standard (1.0×)");
    }

    private static void tintSwitch(Switch sw, boolean dark) {
        int[][] states = new int[][]{new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}};
        sw.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        sw.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        sw.setBackground(null);
    }

    private static String installedVersion(Context context, String packageName) {
        try {
            android.content.pm.PackageInfo info = context.getPackageManager()
                    .getPackageInfo(packageName, 0);
            long code = android.os.Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode() : info.versionCode;
            String name = info.versionName == null ? "未知" : info.versionName;
            return name + " (" + code + ")";
        } catch (Throwable t) {
            return "读取失败";
        }
    }

    /** 二级页：仿 DeepSeek 子页面从右向左滑入，内容为帮助手风琴。 */
    static void showHelpPage(final Activity act) {
        boolean dark = isDark(act);
        int bgColor   = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        int barColor  = dark ? 0xFF232326 : 0xFFFFFFFF;
        int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        int subColor  = dark ? 0xFF9A9A9E : 0xFF888888;
        int divColor  = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bgColor);

        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(barColor);
        int barH = dp(act, 56);
        int statusTop = statusBarHeight(act);
        bar.setPadding(dp(act, 8), statusTop, dp(act, 16), 0);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, barH + statusTop));

        final Dialog dlg = new Dialog(act, android.R.style.Theme_Black_NoTitleBar_Fullscreen);

        TextView back = new TextView(act);
        back.setText("\u2039");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setTextColor(textColor);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(act, 8), 0, dp(act, 8), 0);
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { slideOutAndDismiss(dlg, root); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(act, 40)));

        TextView title = new TextView(act);
        title.setText("帮助与问题");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(act, 8);
        bar.addView(title, tlp);

        android.widget.ScrollView scroll = new android.widget.ScrollView(act);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout hcard = new LinearLayout(act);
        hcard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(cardColor);
        cardBg.setCornerRadius(dp(act, 12));
        hcard.setBackground(cardBg);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16));
        scroll.addView(hcard, clp);

        buildHelpAccordion(act, hcard, textColor, subColor, divColor, dark);
        addBuildFooter(act, hcard, subColor);

        UiLanguage.localizeTree(act, root);
        dlg.setContentView(root);
        Window w = dlg.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));
        }
        trackChildDialog(dlg);
        openWithSlide(dlg, root);
        dlg.setOnKeyListener(new Dialog.OnKeyListener() {
            public boolean onKey(android.content.DialogInterface d, int code, android.view.KeyEvent e) {
                if (code == android.view.KeyEvent.KEYCODE_BACK
                        && e.getAction() == android.view.KeyEvent.ACTION_UP) {
                    slideOutAndDismiss(dlg, root);
                    return true;
                }
                return false;
            }
        });
    }

    /** 帮助手风琴：点标题行，正文向下延展展开（高度动画）+ 箭头旋转；展开一条自动收起其它。 */
    private static void buildHelpAccordion(final Activity act, LinearLayout card,
                                           final int textColor, final int subColor,
                                           int divColor, boolean dark) {
        TextView headerHint = new TextView(act);
        headerHint.setText("功能说明与常见问题");
        headerHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        headerHint.setTextColor(subColor);
        headerHint.setPadding(dp(act, 16), dp(act, 14), dp(act, 16), dp(act, 8));
        card.addView(headerHint, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // {标题, 简短说明}
        final String[][] items = {
            {"【功能】系统提示词注入", "把选定的提示词附加到请求中。"},
            {"【功能】心跳与定时提醒", "按当前对话发送主动消息，并可设置或取消提醒。"},
            {"【功能】Agent 工具", "在实验性功能中管理工具和权限，支持基础操作。"},
            {"【功能】工具调用日志", "显示工具名称、调用时间和执行结果。"},
            {"【功能】问答工具", "模型提出选项或问题，你选择或输入后继续对话。"},
            {"【功能】面板与图形输出", "模型可生成信息面板、进度条和简单图形。"},
            {"【功能】聊天外观", "自定义气泡、背景图、贴纸和透明度。"},
            {"【问题】工具只显示文字，没有真正执行？", "确认实验性功能中的 Agent 开关已开启，并检查工具权限。"},
            {"【问题】心跳提醒没有出现在对话？", "确认已绑定目标对话，并允许通知和后台运行。"},
            {"【问题】背景图或聊天外观没有生效？", "重新打开对应页面；部分设置需要重启 DeepSeek。"},
            {"【问题】编辑或历史记录显示异常？", "先在 DeepSeek 原生页面打开目标对话并等待加载，再重试。"},
            {"【问题】账号导入失败？", "使用完整 UTF-8 JSON，并确认凭证仍有效。"},
        };

        final java.util.List<View> bodies = new java.util.ArrayList<View>();
        final java.util.List<TextView> arrows = new java.util.ArrayList<TextView>();

        for (int i = 0; i < items.length; i++) {
            if (i > 0) card.addView(makeDivider(act, divColor));

            // 标题行
            LinearLayout titleRow = new LinearLayout(act);
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            titleRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 16), dp(act, 14));
            titleRow.setClickable(true);
            titleRow.setFocusable(true);

            TextView t = new TextView(act);
            t.setText("• " + UiLanguage.dynamic(act, items[i][0]));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            t.setTextColor(textColor);
            titleRow.addView(t, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView arrow = new TextView(act);
            arrow.setText("\u203A"); // › 收起态指向右，展开时旋转 90° 指向下
            arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            arrow.setTextColor(subColor);
            titleRow.addView(arrow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            card.addView(titleRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // 正文（默认收起）
            TextView body = new TextView(act);
            body.setText(UiLanguage.dynamic(act, items[i][1]));
            body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            body.setTextColor(subColor);
            body.setLineSpacing(dp(act, 2), 1f);
            body.setPadding(dp(act, 16), 0, dp(act, 16), dp(act, 14));
            body.setVisibility(View.GONE);
            card.addView(body, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            final int idx = i;
            bodies.add(body);
            arrows.add(arrow);
            titleRow.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    boolean willOpen = bodies.get(idx).getVisibility() != View.VISIBLE;
                    for (int j = 0; j < bodies.size(); j++) {
                        if (j != idx && bodies.get(j).getVisibility() == View.VISIBLE) {
                            animateExpand(bodies.get(j), false);
                            arrows.get(j).animate().rotation(0f).setDuration(200).start();
                        }
                    }
                    animateExpand(bodies.get(idx), willOpen);
                    arrows.get(idx).animate().rotation(willOpen ? 90f : 0f).setDuration(200).start();
                }
            });
        }
    }

    /** 正文向下延展/收起：动画其 layoutParams.height，0 ↔ 测量高度。 */
    private static void animateExpand(final View body, final boolean open) {
        final ViewGroup.LayoutParams lp = body.getLayoutParams();
        int parentW = ((View) body.getParent()).getWidth();
        int wSpec = View.MeasureSpec.makeMeasureSpec(
                parentW > 0 ? parentW : 0,
                parentW > 0 ? View.MeasureSpec.EXACTLY : View.MeasureSpec.UNSPECIFIED);
        int hSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        body.measure(wSpec, hSpec);
        final int target = body.getMeasuredHeight();

        int start = open ? 0 : (body.getHeight() > 0 ? body.getHeight() : target);
        int end   = open ? target : 0;

        if (open) {
            lp.height = 0;
            body.setLayoutParams(lp);
            body.setVisibility(View.VISIBLE);
        }

        android.animation.ValueAnimator va = android.animation.ValueAnimator.ofInt(start, end);
        va.setDuration(220);
        va.setInterpolator(new android.view.animation.DecelerateInterpolator());
        va.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                lp.height = (Integer) a.getAnimatedValue();
                body.setLayoutParams(lp);
            }
        });
        va.addListener(new android.animation.AnimatorListenerAdapter() {
            public void onAnimationEnd(android.animation.Animator a) {
                if (open) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    body.setLayoutParams(lp);
                } else {
                    body.setVisibility(View.GONE);
                }
            }
        });
        va.start();
    }

    /**
     * 项目统一的自绘确认弹窗。内容、圆角和按钮均由普通 View 构建，不使用系统 AlertDialog。
     * negativeText 可为 null（只显示一个确认按钮）；cancelable=false 时只能点击显式按钮。
     */
    static Dialog showCustomConfirm(final Activity act, String titleText, String messageText,
                                    String negativeText, String positiveText, boolean cancelable,
                                    final Runnable onNegative, final Runnable onPositive) {
        if (act == null || act.isFinishing()) return null;
        final boolean dark = isDark(act);
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFB5B5B9 : 0xFF666666;

        final Dialog dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(cancelable);
        dialog.setCanceledOnTouchOutside(cancelable);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 20), dp(act, 18), dp(act, 20), dp(act, 16));
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(cardColor);
        rootBg.setCornerRadius(dp(act, 18));
        root.setBackground(rootBg);

        TextView title = new TextView(act);
        title.setText(UiLanguage.dynamic(act, titleText == null ? "提示" : titleText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        android.widget.ScrollView messageScroll = new android.widget.ScrollView(act) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                        dp(act, 430), View.MeasureSpec.AT_MOST));
            }
        };
        TextView message = new TextView(act);
        message.setText(UiLanguage.dynamic(act, messageText == null ? "" : messageText));
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        message.setTextColor(subColor);
        message.setLineSpacing(dp(act, 2), 1f);
        message.setPadding(0, dp(act, 12), 0, dp(act, 12));
        messageScroll.addView(message, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(messageScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout buttons = new LinearLayout(act);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);

        if (negativeText != null) {
            TextView negative = popupButton(act, negativeText, textColor, dark ? 0xFF38383C : 0xFFF0F1F4);
            negative.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try { dialog.dismiss(); } catch (Throwable ignored) {}
                    if (onNegative != null) onNegative.run();
                }
            });
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, dp(act, 44), 1f);
            nlp.rightMargin = dp(act, 10);
            buttons.addView(negative, nlp);
        }

        TextView positive = popupButton(act,
                positiveText == null ? "确定" : positiveText, 0xFFFFFFFF, BRAND);
        positive.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try { dialog.dismiss(); } catch (Throwable ignored) {}
                if (onPositive != null) onPositive.run();
            }
        });
        buttons.addView(positive, new LinearLayout.LayoutParams(0, dp(act, 44), 1f));
        root.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        UiLanguage.localizeTree(act, root);
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.48f;
            window.setAttributes(attrs);
            int width = act.getResources().getDisplayMetrics().widthPixels - dp(act, 32);
            window.setLayout(Math.max(dp(act, 280), width), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private static TextView popupButton(Activity act, String label, int textColor, int bgColor) {
        TextView button = new TextView(act);
        button.setText(UiLanguage.dynamic(act, label));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(textColor);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(bgColor);
        background.setCornerRadius(dp(act, 12));
        button.setBackground(background);
        return button;
    }

    static int statusBarHeight(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) return c.getResources().getDimensionPixelSize(id);
        return dp(c, 28);
    }

    private DeekseepUi() {}
}
