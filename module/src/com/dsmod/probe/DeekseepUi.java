package com.dsmod.probe;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
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

    static int BRAND = 0xFF4D6BFE;
    static final String ENTRY_BUTTON_TAG = "deekseep_settings_entry_button_v1";
    private static volatile Dialog activePageDialog;
    private static volatile Dialog rootPageDialog;
    private static volatile Dialog localApiPageDialog;
    private static volatile boolean localApiSettingsFromPage;
    private static volatile TextView activePromptImportButton;
    private static volatile TextView activePromptPathText;
    private static volatile View activePromptResetRow;
    private static volatile Switch activePromptInjectionSwitch;
    private static volatile boolean refreshingPromptControls;
    private static volatile String pendingSearchHighlight;
    private static final int CATEGORY_CHAT = 1;
    private static final int CATEGORY_ACCOUNT = 2;
    private static final int CATEGORY_APPEARANCE = 3;
    private static final int CATEGORY_DEBUG = 4;
    private static final int CATEGORY_ENGINEERING = 5;
    private static final int CATEGORY_CLOUD = 6;
    private static final String REPOSITORY = "https://github.com/lllucccian/Deekseep";
    private static final String QQ_GROUP_URL = "https://qun.qq.com/universal-share/share?ac=1&authKey=FbzWCRfmPumvnJwdOWMefz5c5SQbWWzKD2Pe90Vz6K923ic8eQA9GX3EWc453AV1&busi_data=eyJncm91cENvZGUiOiIxMTA2NDY1MzAwIiwidG9rZW4iOiJTU1BPNGgwTXN4VFBra0ozZkpCMVo5RGtEWWtIS2hOVktjcis2dTI4NU9EZ2ZidkN2cTI5QUJSTWxiNktYK01OIiwidWluIjoiMzY5MTE4NjM1MyJ9&data=Cc4E1sQAT4lvdZkfiRyQjjj8f4YzEq2YqvcfeSbXP_-V9G-G8pQyBNh9D5GUeI7CEH9I6op7AAvZn78Ca-j6gw&svctype=4&tempid=h5_group_info";
    private static final String TELEGRAM_GROUP_URL = "https://t.me/Deekseepapp";

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
            new FeatureSearchEntry("上下文压缩", "压缩 摘要 压缩记录 摘要记录 上下文 compact summary",
                    CATEGORY_CHAT),
            new FeatureSearchEntry("解除本地聊天次数修改",
                    "编辑 修改 重新生成 次数 限制", CATEGORY_CHAT),
            new FeatureSearchEntry("思考链代码块复制",
                    "思考 reasoning 代码 copy 复制按钮", CATEGORY_CHAT),
            new FeatureSearchEntry("导出会话为 Markdown", "导出 备份包", CATEGORY_CHAT),
            new FeatureSearchEntry("导入聊天记录", "恢复 覆盖 备份包", CATEGORY_CHAT),
            new FeatureSearchEntry("全局搜索聊天记录", "消息 搜索", CATEGORY_CHAT),
            new FeatureSearchEntry("会话数据统计", "消息 字数", CATEGORY_CHAT),
            new FeatureSearchEntry("立即备份聊天数据库", "数据库 backup", CATEGORY_CHAT),
            new FeatureSearchEntry("自动备份聊天数据库", "每日", CATEGORY_CHAT),
            new FeatureSearchEntry("专家模式上传图片文件（无用）", "expert 图片 旧版中继", CATEGORY_CHAT),
            new FeatureSearchEntry("AI 心跳", "主动消息 间隔", CATEGORY_CHAT),
            new FeatureSearchEntry("多账号管理", "账号 切换 导入", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("解锁 Google 登录", "谷歌 国内版", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("解锁微信与手机号登录", "海外版 短信", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("本地禁言", "时间 截止日期", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("禁用数据用于优化体验", "隐私 training", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("绕过风控 SDK", "风控 数美 smid 伪造 检测", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("防止封号", "降低封号概率", CATEGORY_ACCOUNT),
            new FeatureSearchEntry("主页欢迎语", "首页 文案", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("使用旧版入口", "原生设置 右上角 悬浮", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("左滑进入设置", "左滑 滑动 手势 swipe", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("双开模式", "双会话 模型比赛 右侧 左滑 dual", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("主题色", "颜色 取色 HEX 渐变", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("外观设置", "背景 气泡 液态玻璃 空间动效", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("自定义 DeepSeek 头像",
                    "助手头像 灰度 显示助手头像", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("鲸鱼图标动效", "旋转", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("深海文字波纹", "字体 渐变", CATEGORY_APPEARANCE),
            new FeatureSearchEntry("记录服务器返回", "诊断 SSE", CATEGORY_DEBUG),
            new FeatureSearchEntry("高频原始 Hook 日志", "无脱敏 全量 调试 隐私", CATEGORY_DEBUG),
            new FeatureSearchEntry("Hook 日志显示在屏幕", "日志 overlay", CATEGORY_DEBUG),
            new FeatureSearchEntry("记录崩溃", "crash", CATEGORY_DEBUG),
            new FeatureSearchEntry("兼容性诊断报告", "版本 映射", CATEGORY_DEBUG),
            new FeatureSearchEntry("Hook 性能统计", "耗时", CATEGORY_DEBUG),
            new FeatureSearchEntry("脱敏事件追踪与导出", "trace", CATEGORY_DEBUG),
            new FeatureSearchEntry("发送自定义请求", "网络 API", CATEGORY_DEBUG),
            new FeatureSearchEntry("导出日志", "ZIP 诊断", CATEGORY_DEBUG),
            new FeatureSearchEntry("备份全部应用数据", "全量 dskb 导入 恢复", CATEGORY_DEBUG),
            new FeatureSearchEntry("自动化管理", "自动化 智能体 任务 执行", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("语言", "中文 English", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("禁用热更新", "更新 强制更新", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("灰度功能管理器", "远程配置 feature flags", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("自动清理缓存", "图片 Mermaid Coil", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("进程管理", "进程 冻结 解冻 杀死 Root", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("界面导航管理器", "Activity Compose Route 隐藏页面", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("Agent", "工具 Root Shizuku", CATEGORY_ENGINEERING),
            new FeatureSearchEntry("Java 插件", "插件 ZIP ABI Hook 教程", CATEGORY_ENGINEERING)
    };

    static void refreshPromptControls() {
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

    private static boolean isClosedV241() {
        return BuildInfo.PROTECTED_BUILD && BuildInfo.LOCAL_API_INCLUDED && HostCompat.isV241();
    }

    private static CompoundButton.OnCheckedChangeListener unavailableExpertRelayListener(
            final Activity act) {
        return new CompoundButton.OnCheckedChangeListener() {
            private boolean internal;

            @Override public void onCheckedChanged(final CompoundButton button,
                                                   boolean checked) {
                if (internal) return;
                if (!checked) {
                    Main.setExpertRelayEnabled(false);
                    return;
                }
                internal = true;
                button.setChecked(false);
                internal = false;
                new android.app.AlertDialog.Builder(act)
                        .setTitle("专家模式上传图片文件（无用）")
                        .setMessage("目前 DeepSeek 已移除专家模式，因此该功能暂时无效。"
                                + "仅建议为旧版方案保留配置，是否仍要继续开启？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("继续开启", (dialog, which) -> {
                            if (!Main.setExpertRelayEnabled(true)) {
                                Toast.makeText(act, "设置保存失败", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            internal = true;
                            button.setChecked(true);
                            internal = false;
                        })
                        .show();
            }
        };
    }

    private static boolean isClosedV236OrV241Feature() {
        if (HostCompat.isV236()) return true;
        if (HostCompat.isV241()) return true;
        return false;
    }

    private static android.graphics.drawable.GradientDrawable brandBg(Activity act) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setColor(BRAND);
        bg.setCornerRadius(dp(act, 8));
        return bg;
    }

    private static void syncThemePalette() {
        ThemeColorConfig.Value value = ThemeColorConfig.get();
        BRAND = value.enabled ? value.primary : 0xFF4D6BFE;
    }

    private static int themedSurface(boolean dark, int original, float amount) {
        ThemeColorConfig.Value value = ThemeColorConfig.get();
        if (!value.enabled) return original;
        int base = original;
        int theme = value.primary;
        float inverse = 1f - amount;
        return Color.argb(255,
                Math.round(Color.red(base) * inverse + Color.red(theme) * amount),
                Math.round(Color.green(base) * inverse + Color.green(theme) * amount),
                Math.round(Color.blue(base) * inverse + Color.blue(theme) * amount));
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
        if (dialog != null) {
            activePageDialog = dialog;
            attachCarrierBarIfPresent(dialog);
        }
    }

    static void preventCarrierFloatingBarOnDialog(final Dialog dialog) {
        if (dialog == null) return;
        try {
            Window w = dialog.getWindow();
            if (w != null) {
                View decor = w.getDecorView();
                if (decor instanceof ViewGroup) {
                    ViewGroup rootVg = (ViewGroup) decor;
                    // 1. 先放置一个 tag 为 carrier_floating_bar 的占位隐藏 View，使 CarrierFloatingBar.attachToWindow 内部判定直接命中提前返回
                    View dummy = new View(dialog.getContext());
                    dummy.setTag("carrier_floating_bar");
                    dummy.setVisibility(View.GONE);
                    rootVg.addView(dummy, new ViewGroup.LayoutParams(0, 0));

                    // 2. 注册层级监听：一旦宿主 Hook 试图添加 CarrierFloatingBar，立即隐藏并移除
                    rootVg.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener() {
                        @Override
                        public void onChildViewAdded(View parent, View child) {
                            if (child != null && child.getClass().getName().contains("CarrierFloatingBar")) {
                                child.setVisibility(View.GONE);
                                if (parent instanceof ViewGroup) {
                                    ((ViewGroup) parent).removeView(child);
                                }
                            }
                        }
                        @Override
                        public void onChildViewRemoved(View parent, View child) {}
                    });
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void hideHostCarrierBar(Activity act) {
        if (act == null) return;
        try {
            Window w = act.getWindow();
            if (w == null) return;
            View decor = w.getDecorView();
            if (decor instanceof ViewGroup) {
                View bar = decor.findViewWithTag("DEEKSEEP_CARRIER_BAR");
                if (bar != null) {
                    bar.setVisibility(View.INVISIBLE);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void restoreHostCarrierBar(Activity act) {
        if (act == null) return;
        try {
            Window w = act.getWindow();
            if (w == null) return;
            View decor = w.getDecorView();
            if (decor instanceof ViewGroup) {
                View bar = decor.findViewWithTag("DEEKSEEP_CARRIER_BAR");
                if (bar != null) {
                    bar.setVisibility(View.VISIBLE);
                }
            }
        } catch (Throwable ignored) {}
    }

    static void attachCarrierBarIfPresent(Dialog dialog) {
        if (dialog == null) return;
        try {
            final Window w = dialog.getWindow();
            if (w == null) return;
            Context ctx = dialog.getContext();
            ClassLoader cl = ctx.getClassLoader();
            Class<?> barClass = null;
            try {
                barClass = cl.loadClass("com.dsmod.loader.CarrierFloatingBar");
            } catch (Throwable t) {
                try {
                    barClass = Class.forName("com.dsmod.loader.CarrierFloatingBar");
                } catch (Throwable t2) {
                    if (Main.hostClassLoader != null) {
                        try {
                            barClass = Main.hostClassLoader.loadClass("com.dsmod.loader.CarrierFloatingBar");
                        } catch (Throwable ignored) {}
                    }
                }
            }
            if (barClass != null) {
                try {
                    java.lang.reflect.Method attachWindow = barClass.getMethod("attachToWindow", Window.class, Context.class);
                    attachWindow.invoke(null, w, ctx);
                } catch (Throwable t) {
                    try {
                        java.lang.reflect.Method attachDialog = barClass.getMethod("attachToDialog", Dialog.class);
                        attachDialog.invoke(null, dialog);
                    } catch (Throwable ignored) {}
                }

                View decor = w.getDecorView();
                if (decor instanceof ViewGroup) {
                    final View bar = decor.findViewWithTag("DEEKSEEP_CARRIER_BAR");
                    if (bar != null) {
                        bar.bringToFront();
                    }
                }
            }
        } catch (Throwable t) {
            Main.log("attachCarrierBarIfPresent failed: " + t);
        }
    }

    /** 右上角的文字入口 "Deekseep"。 */
    static TextView createEntryButton(Context ctx, View.OnClickListener onClick) {
        return createEntryButton(ctx, "Deekseep", onClick);
    }

    /** 同一套样式的文字入口，标题可换；供聊天页的「压缩」按钮复用。 */
    static TextView createEntryButton(Context ctx, String label, View.OnClickListener onClick) {
        TextView b = new TextView(ctx);
        b.setTag(ENTRY_BUTTON_TAG);
        b.setText(label);
        b.setTextColor(isDark(ctx) ? 0xFFECECEC : 0xFF1A1A1A);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5));
        final int bgColor = isDark(ctx) ? 0xFF2A2A2D : 0xFFF0F0F2;
        final float radius = dp(ctx, 8);
        b.setBackground(new android.graphics.drawable.Drawable() {
            private final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            { p.setColor(bgColor); }
            @Override public void draw(android.graphics.Canvas c) {
                android.graphics.RectF r = new android.graphics.RectF(getBounds());
                c.drawRoundRect(r, radius, radius, p);
            }
            @Override public void setAlpha(int a) { p.setAlpha(a); }
            @Override public void setColorFilter(android.graphics.ColorFilter f) { p.setColorFilter(f); }
            @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
        });
        b.setClickable(true);
        b.setFocusable(true);
        b.setOnClickListener(onClick);
        return b;
    }

    /** Deekseep 首页只负责分组；具体开关和工具进入对应子页。 */
    static void showPage(final Activity act) {
        UiLanguage.refreshHost(act);
        syncThemePalette();
        final boolean dark = isDark(act);
        final int bgColor = themedSurface(dark, dark ? 0xFF1B1B1D : 0xFFF5F6F8, 0.10f);
        final int barColor = themedSurface(dark, dark ? 0xFF232326 : 0xFFFFFFFF, 0.22f);
        final int cardColor = themedSurface(dark, dark ? 0xFF2A2A2D : 0xFFFFFFFF, 0.05f);
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
        if (!BuildInfo.PROTECTED_BUILD) {
            card.addView(toolActionRow(act, "ds_project_license", "开源许可",
                    "", textColor, subColor,
                    new View.OnClickListener() {
                        @Override public void onClick(View view) { showOpenSourceDialog(act); }
                    }));
        }
        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "ds_project_sponsor", "赞助开发者",
                "", textColor, subColor,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { showSponsorDialog(act); }
                }));
        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "ds_project_community", "交流群",
                "", textColor, subColor,
                new View.OnClickListener() {
                    @Override public void onClick(View view) { showCommunityChooser(act); }
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
                            pendingSearchHighlight = entry.title;
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
        syncThemePalette();
        boolean dark = isDark(act);
        int bgColor   = themedSurface(dark, dark ? 0xFF1B1B1D : 0xFFF5F6F8, 0.10f);
        int barColor  = themedSurface(dark, dark ? 0xFF232326 : 0xFFFFFFFF, 0.22f);
        int cardColor = themedSurface(dark, dark ? 0xFF2A2A2D : 0xFFFFFFFF, 0.05f);
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
                String componentPackage = RuntimeEmbeddingMode.componentPackage(Main.SELF);
                i.setClassName(componentPackage, componentPackage + ".PromptPickerActivity");
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
        final boolean supportsPromptInterval = HostCompat.isV236() || HostCompat.isV241();
        LinearLayout toggleTitle = new LinearLayout(act);
        toggleTitle.setOrientation(LinearLayout.HORIZONTAL);
        toggleTitle.setGravity(Gravity.CENTER_VERTICAL);
        toggleTitle.addView(toggleLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (supportsPromptInterval) {
            View intervalGear = plainGearControl(act, 0xFF1A1A1A,
                    "提示词重复注入设置",
                    view -> showPromptSplitPopup(act, view, dark, textColor, subColor));
            LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(
                    dp(act, 36), dp(act, 36));
            gearParams.leftMargin = dp(act, 2);
            toggleTitle.addView(intervalGear, gearParams);
        }
        toggleRow.addView(toggleTitle, new LinearLayout.LayoutParams(
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

        LinearLayout censorTitle = new LinearLayout(act);
        censorTitle.setOrientation(LinearLayout.HORIZONTAL);
        censorTitle.setGravity(Gravity.CENTER_VERTICAL);
        TextView censorLabel = new TextView(act);
        censorLabel.setText("去他妈的安全审查");
        censorLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        censorLabel.setTypeface(Typeface.DEFAULT_BOLD);
        censorLabel.setTextColor(textColor);
        censorTitle.addView(censorLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View censorSettings = plainGearControl(act, textColor,
                UiLanguage.text(act, "防撤回设置", "Anti-recall settings"),
                new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        showNoCensorSettings(act);
                    }
                });
        LinearLayout.LayoutParams censorSettingsParams = new LinearLayout.LayoutParams(
                dp(act, 36), dp(act, 36));
        censorSettingsParams.leftMargin = dp(act, 2);
        censorTitle.addView(censorSettings, censorSettingsParams);
        censorLabels.addView(censorTitle, new LinearLayout.LayoutParams(
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

        // ── Section 4d: 解锁微信与手机号登录 ─────────────────────────
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

        // ── 分割线 ──────────────────────────────────────────────────
        card.addView(makeDivider(act, divColor));

        // ── Section 4c: 解锁 Google 和邮箱登录 ───────────────────────
        LinearLayout googleRow = new LinearLayout(act);
        googleRow.setOrientation(LinearLayout.HORIZONTAL);
        googleRow.setGravity(Gravity.CENTER_VERTICAL);
        googleRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));

        LinearLayout googleLabels = new LinearLayout(act);
        googleLabels.setOrientation(LinearLayout.VERTICAL);

        TextView googleLabel = new TextView(act);
        googleLabel.setText("解锁 Google 和邮箱登录");
        googleLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        googleLabel.setTypeface(Typeface.DEFAULT_BOLD);
        googleLabel.setTextColor(textColor);
        googleLabels.addView(googleLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView googleDesc = new TextView(act);
        googleDesc.setText("解锁国内用户的 Google 与邮箱密码登录入口。");
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

        // ── Section 4e: 绕过风控 SDK（伪造设备、按死检测、拦截上报）────
        card.addView(makeDivider(act, divColor));
        card.addView(simpleSwitchRow(act, "绕过风控 SDK",
                "伪造设备指纹与 smid、按死数美环境检测并拦截风控上报。重启 DeepSeek 后生效。",
                Main.isRiskBypassEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        if (reverting) return;
                        if (Main.setRiskBypassEnabled(checked)) {
                            Toast.makeText(act, UiLanguage.text(act,
                                    "已保存，重启 DeepSeek 后生效",
                                    "Saved; restart DeepSeek to apply"),
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                        Toast.makeText(act, UiLanguage.text(act,
                                "绕过风控设置保存失败",
                                "Could not save the risk-bypass setting"),
                                Toast.LENGTH_SHORT).show();
                    }
                }));

        card.addView(makeDivider(act, divColor));
        View accountSafetyRow = simpleSwitchRow(act, "防止封号",
                "“降低封号概率”",
                false, textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean launching;
                    @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                        if (!checked || launching) return;
                        launching = true;
                        AccountSafetyPrank.launch(act);
                    }
                });
        // This row must remain in the privacy section even if its copy changes.
        accountSafetyRow.setTag(CATEGORY_ACCOUNT);
        card.addView(accountSafetyRow);

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
        card.addView(configurableSwitchRow(act, isClosedV236OrV241Feature()
                        ? "本地禁言" : "本地禁言 · 实验性",
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
        LinearLayout rawRow = new LinearLayout(act);
        rawRow.setOrientation(LinearLayout.HORIZONTAL);
        rawRow.setGravity(Gravity.CENTER_VERTICAL);
        rawRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 14));
        LinearLayout rawLabels = new LinearLayout(act);
        rawLabels.setOrientation(LinearLayout.VERTICAL);
        TextView rawTitle = new TextView(act);
        rawTitle.setText("高频原始 Hook 日志");
        rawTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        rawTitle.setTypeface(Typeface.DEFAULT_BOLD);
        rawTitle.setTextColor(textColor);
        rawLabels.addView(rawTitle);
        TextView rawWarning = new TextView(act);
        rawWarning.setText("无脱敏且日志量很大：可能记录消息正文、Hook 参数、返回值、文件路径、账号信息与凭据。");
        rawWarning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        rawWarning.setTextColor(dark ? 0xFFFF8A8A : 0xFFC63C3C);
        rawWarning.setLineSpacing(dp(act, 2), 1f);
        LinearLayout.LayoutParams rawWarningParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rawWarningParams.topMargin = dp(act, 4);
        rawLabels.addView(rawWarning, rawWarningParams);
        LinearLayout.LayoutParams rawLabelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rawLabelsParams.rightMargin = dp(act, 12);
        rawRow.addView(rawLabels, rawLabelsParams);
        final Switch rawSwitch = new HubInsetSwitch(act);
        rawSwitch.setChecked(Main.isRawHookTraceEnabled());
        rawSwitch.setThumbTintList(new android.content.res.ColorStateList(ss,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        rawSwitch.setTrackTintList(new android.content.res.ColorStateList(ss,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        rawSwitch.setBackground(null);
        final boolean[] changingRaw = new boolean[]{false};
        rawSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(final CompoundButton button,
                                                    boolean checked) {
                if (changingRaw[0]) return;
                if (!checked) {
                    Main.setRawHookTraceEnabled(false);
                    Toast.makeText(act, "高频原始 Hook 日志已关闭", Toast.LENGTH_SHORT).show();
                    return;
                }
                changingRaw[0] = true;
                button.setChecked(false);
                changingRaw[0] = false;
                new android.app.AlertDialog.Builder(act)
                        .setTitle("启用无脱敏日志？")
                        .setMessage("此功能会高频记录每个模块 Hook 的进入、参数、返回值、异常和耗时，并同步记录模块动作。内容完全不脱敏，可能包含聊天正文、账号、Token、Cookie、文件路径等敏感信息。\n\n日志仅自动保存在 DeepSeek 私有目录，单卷上限 64 MB、保留两卷。调试结束后请立即关闭并清理。")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("理解风险并启用", (dialog, which) -> {
                            boolean saved = Main.setRawHookTraceEnabled(true);
                            changingRaw[0] = true;
                            rawSwitch.setChecked(saved);
                            changingRaw[0] = false;
                            Toast.makeText(act, saved
                                    ? "已启用；重启 DeepSeek 后可记录启动阶段 Hook"
                                    : "启用失败：无法写入私有目录", Toast.LENGTH_LONG).show();
                        }).show();
            }
        });
        rawRow.addView(rawSwitch);
        card.addView(rawRow);

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "清理高频原始日志",
                "仅清除日志文件，不改变上方开关。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        new android.app.AlertDialog.Builder(act)
                                .setTitle("清理高频原始日志？")
                                .setMessage("将删除当前卷和上一卷。已导出的 ZIP 不受影响。")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("清理", (dialog, which) -> Toast.makeText(act,
                                        Main.clearRawHookTrace() ? "日志已清理" : "清理失败",
                                        Toast.LENGTH_SHORT).show()).show();
                    }
                }));

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

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "导出日志",
                "选择性导出模块诊断日志 ZIP，自动脱敏 API Key。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        LogExportUi.show(act);
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "导出配置",
                "",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        String json = ModuleConfigBridge.exportConfigJson();
                        if (json == null) {
                            Toast.makeText(act, "导出失败：无法读取配置", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            java.io.File dir = new java.io.File(
                                    android.os.Environment.getExternalStoragePublicDirectory(
                                            android.os.Environment.DIRECTORY_DOWNLOADS),
                                    "Deekseep");
                            if (!dir.isDirectory() && !dir.mkdirs()) {
                                Toast.makeText(act, "导出失败：无法创建目录", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            java.io.File file = new java.io.File(dir, "deekseep-config.json");
                            java.io.FileOutputStream out = new java.io.FileOutputStream(file);
                            out.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            out.flush();
                            out.close();
                            Toast.makeText(act, "配置已导出：" + file.getAbsolutePath(),
                                    Toast.LENGTH_LONG).show();
                        } catch (Throwable t) {
                            Toast.makeText(act, "导出失败：" + t.getClass().getSimpleName(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "导入配置",
                "",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        try {
                            java.io.File dir = new java.io.File(
                                    android.os.Environment.getExternalStoragePublicDirectory(
                                            android.os.Environment.DIRECTORY_DOWNLOADS),
                                    "Deekseep");
                            java.io.File file = new java.io.File(dir, "deekseep-config.json");
                            if (!file.isFile()) {
                                Toast.makeText(act,
                                        "未找到 Download/Deekseep/deekseep-config.json",
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                            java.io.FileInputStream in = new java.io.FileInputStream(file);
                            byte[] buf = new byte[4096];
                            int n;
                            while ((n = in.read(buf)) >= 0) out.write(buf, 0, n);
                            in.close();
                            String json = new String(out.toByteArray(),
                                    java.nio.charset.StandardCharsets.UTF_8);
                            String result = ModuleConfigBridge.importConfigJson(json);
                            Toast.makeText(act, "ok".equals(result)
                                            ? "配置已导入，重启 DeepSeek 后生效"
                                            : "导入失败：" + result,
                                    Toast.LENGTH_LONG).show();
                        } catch (Throwable t) {
                            Toast.makeText(act, "导入失败：" + t.getClass().getSimpleName(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }));

        // Closed-only: place the full-data archive exactly beside the existing config import/export
        // controls in 调试. The bridge also rejects unsupported hosts at execution time.
        if (ClosedFullDataBackupBridge.supported(act)) {
            card.addView(makeDivider(act, divColor));
            card.addView(toolActionRow(act, "备份全部应用数据",
                    "加密导出 DeepSeek、模块配置、聊天记录与日志；需要 Root。",
                    textColor, subColor, new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            ClosedFullDataBackupBridge.beginBackup(act);
                        }
                    }));
            card.addView(makeDivider(act, divColor));
            card.addView(toolActionRow(act, "恢复全部应用数据",
                    "选择 .dskb 后校验密码并覆盖恢复；恢复前会验证完整性与路径。",
                    textColor, subColor, new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            ClosedFullDataBackupBridge.beginRestore(act);
                        }
                    }));
        }

        card.addView(makeDivider(act, divColor));
        // Device identifiers, card status and activation details belong to Closed only.  Do not
        // expose a disabled placeholder in the Open UI: the public edition has no device-info or
        // licensing workflow at all.
        if (BuildInfo.PROTECTED_BUILD) {
            View devInfoRow = toolActionRow(act, "查看设备信息",
                    "敏感信息，请勿泄露",
                    textColor, subColor, new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            showDeviceInfoDialog(act);
                        }
                    });
            devInfoRow.setTag(CATEGORY_DEBUG);
            card.addView(devInfoRow);
        }

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
        // Manual compaction: no switch to arm, so this row only navigates. It kept the title
        // "上下文压缩" because the feature search matches rows by title.
        LinearLayout compactionRow = new LinearLayout(act);
        compactionRow.setOrientation(LinearLayout.HORIZONTAL);
        compactionRow.setGravity(Gravity.CENTER_VERTICAL);
        compactionRow.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16));
        compactionRow.setClickable(true);
        compactionRow.setFocusable(true);

        LinearLayout compactionLabels = new LinearLayout(act);
        compactionLabels.setOrientation(LinearLayout.VERTICAL);
        TextView compactionTitle = new TextView(act);
        compactionTitle.setText(UiLanguage.dynamic(act, "上下文压缩"));
        compactionTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        compactionTitle.setTextColor(textColor);
        compactionLabels.addView(compactionTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView compactionDesc = new TextView(act);
        compactionDesc.setText(UiLanguage.dynamic(act,
                "把当前对话折叠成摘要并在新对话里继续；摘要可编辑、可放入任意对话。"));
        compactionDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        compactionDesc.setTextColor(subColor);
        LinearLayout.LayoutParams compactionDescLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        compactionDescLp.topMargin = dp(act, 4);
        compactionLabels.addView(compactionDesc, compactionDescLp);
        compactionRow.addView(compactionLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView compactionArrow = new TextView(act);
        compactionArrow.setText("\u203A");
        compactionArrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        compactionArrow.setTextColor(subColor);
        compactionRow.addView(compactionArrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        compactionRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { ContextCompactionUi.show(act); }
        });
        flashSearchHighlight(compactionRow, "上下文压缩");
        card.addView(compactionRow);

        // Escape hatch: whether the route predicate recognises a given build's chat route can only
        // be judged on the device, so the user gets a way out if the chip shows up in the wrong place.
        card.addView(makeDivider(act, divColor));
        card.addView(simpleSwitchRow(act, "在聊天页显示压缩按钮",
                "关闭后聊天页不再显示悬浮的「压缩对话」按钮，设置页里的入口仍然可用。按住按钮可以拖到任意位置，位置会被记住。",
                Main.isChatCompactionButtonEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        if (reverting || Main.setChatCompactionButtonEnabled(checked)) return;
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                    }
                }));

        if (isClosedV236OrV241Feature()) {
            card.addView(makeDivider(act, divColor));
            card.addView(simpleSwitchRow(act, "解除本地聊天次数修改",
                    "解除消息编辑和重新生成的本地 5 次限制；不改变服务器侧额度。",
                    Main.isLocalChatQuotaUnlockEnabled(), textColor, subColor, dark,
                    new CompoundButton.OnCheckedChangeListener() {
                        private boolean reverting;
                        @Override public void onCheckedChanged(CompoundButton button,
                                                               boolean checked) {
                            if (reverting || Main.setLocalChatQuotaUnlockEnabled(checked)) return;
                            reverting = true;
                            button.setChecked(!checked);
                            reverting = false;
                        }
                    }));
        }

        if (isClosedV236OrV241Feature()) {
            card.addView(makeDivider(act, divColor));
            card.addView(simpleSwitchRow(act, "允许选择全部文件类型",
                    "仅跳过客户端扩展名筛选；服务器仍可能拒绝不支持的文件。",
                    Main.isAllFileTypesEnabled(), textColor, subColor, dark,
                    new CompoundButton.OnCheckedChangeListener() {
                        private boolean reverting;
                        @Override public void onCheckedChanged(CompoundButton button,
                                                               boolean checked) {
                            if (reverting || Main.setAllFileTypesEnabled(checked)) return;
                            reverting = true;
                            button.setChecked(!checked);
                            reverting = false;
                        }
                    }));
        }

        if (isClosedV236OrV241Feature()) {
            card.addView(makeDivider(act, divColor));
            card.addView(simpleSwitchRow(act, "思考链代码块复制",
                    "为思考链中的代码块显示与正文完全相同的原生复制按钮。",
                    Main.isThinkingCodeCopyEnabled(), textColor, subColor, dark,
                    new CompoundButton.OnCheckedChangeListener() {
                        private boolean reverting;
                        @Override public void onCheckedChanged(CompoundButton button,
                                                               boolean checked) {
                            if (reverting || Main.setThinkingCodeCopyEnabled(checked)) return;
                            reverting = true;
                            button.setChecked(!checked);
                            reverting = false;
                        }
                    }));
        }

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
        card.addView(simpleSwitchRow(act, "使用旧版入口",
                "默认使用 DeepSeek 原生设置中的“插件”入口；开启后改用右上角 Deekseep 入口。",
                Main.isLegacySettingsEntryEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (reverting) return;
                        if (Main.setLegacySettingsEntryEnabled(checked)) {
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
        card.addView(simpleSwitchRow(act, "左滑进入设置",
                UiLanguage.text(act,
                        "开启后，从界面右侧内缘向左滑动即可打开 Deekseep 设置。手势只观察触摸，不会阻断 DeepSeek 原有滑动或系统返回手势。",
                        "Swipe left from the inner-right edge to open Deekseep settings. The gesture only observes touch input and does not block DeepSeek scrolling or the system Back gesture."),
                Main.isSwipeSettingsEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (reverting) return;
                        if (!Main.setSwipeSettingsEnabled(checked)) {
                            reverting = true;
                            button.setChecked(!checked);
                            reverting = false;
                            Toast.makeText(act, UiLanguage.text(act,
                                    "请先关闭“双开模式”", "Turn off Dual chat first"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }));

        card.addView(makeDivider(act, divColor));
        View dualChatRow = simpleSwitchRow(act, "双开模式",
                UiLanguage.text(act,
                        "从界面右侧内缘向左滑，打开两个独立模型会话。开启后此手势优先于“左滑进入设置”。",
                        "Swipe left from the inner right edge to open two independent model chats. When enabled, this gesture takes priority over Swipe left for settings."),
                Main.isDualChatEnabled(), textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (reverting) return;
                        if (!Main.setDualChatEnabled(checked)) {
                            reverting = true;
                            button.setChecked(!checked);
                            reverting = false;
                            Toast.makeText(act, UiLanguage.text(act,
                                    "双开模式设置保存失败",
                                    "Failed to save dual mode setting"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
        dualChatRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (Main.isDualChatEnabled()) {
                    DualChatUi.show(act);
                }
            }
        });
        card.addView(dualChatRow);

        card.addView(makeDivider(act, divColor));
        final TextView[] themeColorDetail = new TextView[1];
        final ThemeColorConfig.Value themeColor = ThemeColorConfig.get();
        card.addView(configurableSwitchRow(act, "主题色",
                themeColor.gradient ? "自定义颜色 · 双色渐变" : "自定义 DeepSeek 原生主题颜色",
                themeColor.enabled, textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean restoring;
                    @Override public void onCheckedChanged(CompoundButton button,
                                                           boolean checked) {
                        if (restoring) return;
                        if (!ThemeColorConfig.setEnabled(checked)) {
                            restoring = true;
                            button.setChecked(!checked);
                            restoring = false;
                            Toast.makeText(act, "主题色设置保存失败", Toast.LENGTH_SHORT).show();
                        }
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        ThemeColorUi.show(act);
                    }
                }, themeColorDetail));

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
        if (isClosedV236OrV241Feature()) {
            card.addView(simpleSwitchRow(act, "专家模式上传图片文件（无用）",
                    "当前热更新已移除专家模式；保留旧版视觉中继配置，仅供明确需要时开启。",
                    Main.isExpertRelayEnabled(), textColor, subColor, dark,
                    unavailableExpertRelayListener(act)));
        } else {
            card.addView(simpleSwitchRow(act, "解锁专家模式与图片上传 · 实验性",
                    "启用专家模型能力，并通过视觉描述中继图片。",
                    Main.isExpertUnlock(), textColor, subColor, dark,
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                            Main.setExpertUnlock(checked);
                        }
                    }));
        }

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
                            String componentPackage = RuntimeEmbeddingMode
                                    .componentPackage(Main.SELF);
                            intent.setClassName(componentPackage,
                                    componentPackage + ".ProcessManagerActivity");
                            act.startActivity(intent);
                        } catch (Throwable error) {
                            Toast.makeText(act, UiLanguage.text(act,
                                    "无法打开进程管理，请确认模块 APK 已更新",
                                    "Could not open process manager; update the module APK"),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }));

        if (isClosedV236OrV241Feature()) {
            card.addView(makeDivider(act, divColor));
            card.addView(toolActionRow(act, "界面导航管理器",
                    "后台检索当前宿主的 Activity 与 Compose Route；安全页面可直接切换。",
                    textColor, subColor, new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            HostNavigationUi.show(act);
                        }
                    }));
        }

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

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "Java 插件 · 实验性",
                "导入、启停和测试 Java 插件；查看 ABI 教程与 Hook 点目录。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View v) { JavaPluginUi.show(act); }
                }));

        if (BuildInfo.PROTECTED_BUILD && BuildInfo.LOCAL_API_INCLUDED && !isClosedV241()) {
            card.addView(makeDivider(act, divColor));
            card.addView(toolActionRow(act, isClosedV236OrV241Feature()
                            ? "本地 API" : "本地 API · 实验性",
                    "配置兼容接口、后台保活、密钥、监听地址和请求统计。",
                    textColor, subColor, new View.OnClickListener() {
                        @Override public void onClick(View v) { showLocalApiEntry(act); }
                    }));
        }



        for (final JavaPluginManager.Contribution contribution
                : JavaPluginManager.contributions()) {
            card.addView(makeDivider(act, divColor));
            View pluginRow = toolActionRow(act, contribution.title,
                    contribution.description.length() == 0
                            ? "Java 插件操作"
                            : contribution.description,
                    textColor, subColor, new View.OnClickListener() {
                        @Override public void onClick(View view) {
                            JavaPluginManager.invokeContribution(act, contribution);
                        }
                    });
            pluginRow.setTag(javaPluginPlacementCategory(contribution.placement));
            card.addView(pluginRow);
        }

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
        if (isClosedV241()) root.setAlpha(0.985f);
        dlg.show();
        attachCarrierBarIfPresent(dlg);
        if (isClosedV236OrV241Feature()) {
            // code257 renders the whole injected page as one layer.  A shorter transform-only
            // transition avoids holding a full-screen composition on the UI thread for 360 ms.
            root.animate().cancel();
            root.animate().translationX(0).alpha(1f).setDuration(190)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.8f))
                    .start();
            return;
        }
        root.animate().translationX(0).setDuration(360)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    /** 向右滑出后 dismiss。 */
    static void slideOutAndDismiss(final Dialog dlg, final View root) {
        int w = root.getWidth() > 0 ? root.getWidth()
                : root.getResources().getDisplayMetrics().widthPixels;
        if (isClosedV241()) {
            // Cancel an unfinished entrance before starting the exit.  Decelerating from the
            // current transform responds immediately; the old accelerate curve appeared to
            // pause at the beginning of Back on code257.
            root.animate().cancel();
            root.animate().translationX(w).alpha(0.985f).setDuration(150)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.35f))
                    .withEndAction(new Runnable() {
                        public void run() { try { dlg.dismiss(); } catch (Throwable ignored) {} }
                    }).start();
            return;
        }
        root.animate().translationX(w).setDuration(300)
                .setInterpolator(new android.view.animation.AccelerateInterpolator())
                .withEndAction(new Runnable() {
                    public void run() { try { dlg.dismiss(); } catch (Throwable ignored) {} }
                }).start();
    }

    private static void showExperimentalEntry(final Activity act) {
        if (Main.hasAcceptedExperimentalDisclaimer()) {
            showExperimentalPage(act);
        } else {
            showExperimentalDisclaimer(act);
        }
    }

    /** Friendly one-time note before optional expert relay and local API controls. */
    private static void showExperimentalDisclaimer(final Activity act) {
        if (act == null || act.isFinishing()) return;
        final boolean dark = isDark(act);
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFB5B5B9 : 0xFF666666;
        final Dialog dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 20), dp(act, 18), dp(act, 20), dp(act, 16));
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(cardColor);
        rootBg.setCornerRadius(dp(act, 18));
        root.setBackground(rootBg);

        TextView title = new TextView(act);
        title.setText(UiLanguage.text(act,
                "实验性功能使用提示", "About experimental features"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        root.addView(title);

        android.widget.ScrollView messageScroll = new android.widget.ScrollView(act) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(
                        dp(act, 470), View.MeasureSpec.AT_MOST));
            }
        };
        TextView message = new TextView(act);
        String noteZh = isClosedV241()
                ? "这些实验性功能默认关闭，按需开启即可。使用前请留意：\n\n"
                + "• 聊天外观只修改本机显示层；若宿主更新后出现错位，关闭对应外观开关即可。\n"
                + "• 专家图片中继会先通过视觉模型生成图片描述，结果和可用性取决于 DeepSeek 服务。\n"
                + "• 涉及聊天、文件或 Agent 工具时，建议先备份重要内容，并保留客户端的确认和权限设置。\n\n"
                + "本地 API 已移至工程工具，不受此实验性提示限制。"
                :
                "这些功能默认关闭，按需开启即可。使用前请留意：\n\n"
                + "• 聊天外观只修改本机显示层；若宿主更新后出现错位，关闭对应外观开关即可。\n"
                + "• 专家图片中继会先通过视觉模型生成图片描述，结果和可用性取决于 DeepSeek 服务。\n"
                + "• 本地 API 可监听本机或可信局域网；请妥善保存 API Key，不要公开分享。\n"
                + "• 涉及聊天、文件或 Agent 工具时，建议先备份重要内容，并保留客户端的确认和权限设置。\n\n"
                + "如果 DeepSeek 更新后出现异常，关闭对应开关即可。";
        String noteEn = isClosedV241()
                ? "These experimental features are off by default and can be enabled only when needed. Before using them:\n\n"
                + "• Chat appearance changes only the local display layer. Turn off the related appearance option if a host update causes misalignment.\n"
                + "• Expert image relay first creates an image description with a vision model; results and availability depend on the DeepSeek service.\n"
                + "• For chat, file, or Agent tools, back up important content and keep client confirmation and permission controls enabled.\n\n"
                + "Local API now lives under Engineering and does not require this experimental notice."
                :
                "These features are off by default and can be enabled only when needed. Before using them:\n\n"
                + "• Chat appearance changes only the local display layer. Turn off the related appearance option if a host update causes misalignment.\n"
                + "• Expert image relay first creates an image description with a vision model; results and availability depend on the DeepSeek service.\n"
                + "• The local API can listen on this device or a trusted LAN. Keep its API key private.\n"
                + "• For chat, file, or Agent tools, back up important content and keep client confirmation and permission controls enabled.\n\n"
                + "If a DeepSeek update causes a problem, simply turn off the related option.";
        message.setText(UiLanguage.text(act, noteZh, noteEn));
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
        final TextView exit = popupButton(act, "返回", textColor,
                dark ? 0xFF38383C : 0xFFF0F1F4);
        final TextView confirm = popupButton(act, "了解并进入", 0xFFFFFFFF, BRAND);
        LinearLayout.LayoutParams exitLp = new LinearLayout.LayoutParams(0, dp(act, 44), 1f);
        exitLp.rightMargin = dp(act, 10);
        buttons.addView(exit, exitLp);
        buttons.addView(confirm, new LinearLayout.LayoutParams(0, dp(act, 44), 1f));
        root.addView(buttons);

        exit.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { dialog.dismiss(); } catch (Throwable ignored) {}
            }
        });
        confirm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (!Main.acceptExperimentalDisclaimer()) {
                    showCustomConfirm(act, "无法保存确认状态",
                            "DeepSeek 私有目录暂时不可写，因此没有进入实验性功能。请完整重启应用后重试。",
                            null, "知道了", true, null, null);
                    return;
                }
                try { dialog.dismiss(); } catch (Throwable ignored) {}
                showExperimentalPage(act);
            }
        });

        UiLanguage.localizeTree(act, root);
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0.36f;
            window.setAttributes(attrs);
            int width = act.getResources().getDisplayMetrics().widthPixels - dp(act, 32);
            window.setLayout(Math.max(dp(act, 280), width),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static void showExperimentalPage(final Activity act) {
        final boolean dark = isDark(act);
        final int bgColor = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF777B82;
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
        title.setText("实验性功能");
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
        cardLp.setMargins(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 20));
        scroll.addView(card, cardLp);

        TextView warning = infoBox(act,
                "功能默认关闭。建议一次只开启需要的选项；操作重要数据前先备份，DeepSeek 更新后如有异常可随时关闭。",
                subColor, dark);
        card.addView(warning, insetParams(act, 12, 12));
        card.addView(makeDivider(act, divColor));

        card.addView(toolActionRow(act, "聊天外观",
                "背景、页面贴纸和气泡；支持全屏/半屏、无固定上限缩放、截断、镜像或边缘像素延展、取景、旋转、景深与曲线位移。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        ChatAppearanceUi.show(act);
                    }
                }));
        card.addView(makeDivider(act, divColor));

        final TextView[] heartbeatDetailLegacy = new TextView[1];
        card.addView(configurableSwitchRow(act, "AI 心跳",
                proactiveHeartbeatDescription(act), Main.isProactiveHeartbeatEnabled(),
                textColor, subColor, dark,
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (reverting) return;
                        if (Main.setProactiveHeartbeatEnabled(act, checked)) return;
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                        android.widget.Toast.makeText(act, "AI 心跳设置保存失败",
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                }, new View.OnClickListener() {
                    @Override public void onClick(View view) {
                        showProactiveHeartbeatIntervalDialog(
                                act, heartbeatDetailLegacy[0]);
                    }
                }, heartbeatDetailLegacy));

        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "Agent",
                "问答、当前时间、截图与基础界面操作；可逐项关闭工具，并选择应用内、Root 或 Shizuku 后端。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        AgentSettingsUi.show(act);
                    }
                }));
        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "Java 插件 · 实验性",
                "导入插件 ZIP、启停插件、运行内置测试，并查阅 ABI v1 教程。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        JavaPluginUi.show(act);
                    }
                }));
        if (BuildInfo.PROTECTED_BUILD && BuildInfo.LOCAL_API_INCLUDED) {
            card.addView(makeDivider(act, divColor));
            card.addView(toolActionRow(act, "本地 API 服务",
                    "配置 OpenAI / Anthropic 格式、后台保活、API Key、监听地址与请求统计。",
                    textColor, subColor, new View.OnClickListener() {
                        @Override public void onClick(View v) { showLocalApiEntry(act); }
                    }));
        }
        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "对话转接 · 实验性",
                "把原生聊天框的消息转发到自配的 OpenAI/Anthropic 服务商，不走 DeepSeek 原生链路，支持思考链。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View v) { showChatRelayPage(act); }
                }));
        card.addView(makeDivider(act, divColor));
        card.addView(toolActionRow(act, "帮助与问题",
                "包含聊天外观、专家模式图片中继和本地 API 的完整说明、注意事项与排障。",
                textColor, subColor, new View.OnClickListener() {
                    @Override public void onClick(View v) { showExperimentalHelpPage(act); }
                }));
        card.addView(makeDivider(act, divColor));
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

    private static String proactiveHeartbeatDescription(Context context) {
        String interval = String.format(java.util.Locale.getDefault(), UiLanguage.text(context,
                        "每 %d 分钟",
                        "Every %d minutes"),
                Main.proactiveHeartbeatIntervalMinutes());
        String binding = !Main.hasProactiveHeartbeatBinding()
                ? UiLanguage.text(context,
                        "未绑定",
                        "Not bound")
                : Main.proactiveHeartbeatBoundToCurrentConversation()
                        ? UiLanguage.text(context,
                                "已绑定当前对话",
                                "Bound to current chat")
                        : UiLanguage.text(context,
                                "已绑定其他对话",
                                "Bound to another chat");
        return interval + " · " + binding;
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
                        "设置 AI 心跳间隔", "Set AI heartbeat interval"))
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
                                            "AI 心跳间隔已保存",
                                            "AI heartbeat interval saved"),
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

    private static void showLocalApiEntry(final Activity act) {
        showLocalApiPage(act);
    }


    static void handleLocalApiBatterySettingsResult(final Activity act) {
        localApiSettingsFromPage = false;
        Main.verifyLocalApiBackground(act);
    }

    /** 独立的本地 API 控制页：所有控件与反馈均由模块手工绘制。 */
    private static void showLocalApiPage(final Activity act) {
        final boolean dark = isDark(act);
        final int bgColor = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF777B82;
        final int divColor = dark ? 0xFF3A3A3D : 0xFFEEEEEE;
        final int[][] switchStates = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };

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
        localApiPageDialog = dialog;
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface ignored) {
                if (localApiPageDialog == dialog) localApiPageDialog = null;
            }
        });
        TextView back = new TextView(act);
        back.setText("\u2039");
        back.setTextColor(textColor);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(act, 8), 0, dp(act, 8), 0);
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { slideOutAndDismiss(dialog, root); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(act, 40)));
        TextView title = new TextView(act);
        title.setText("DeepSeek 本地 API");
        title.setTextColor(textColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
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
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(cardColor);
        cardBackground.setCornerRadius(dp(act, 8));
        card.setBackground(cardBackground);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 20));
        scroll.addView(card, cardLp);

        card.addView(sectionTitle(act, "后台运行建议", textColor));
        final TextView backgroundStatus = infoBox(act,
                Main.localApiBackgroundStatus(act), textColor, dark);
        card.addView(backgroundStatus, insetParams(act, 0, 6));
        final TextView keepAliveStatus = infoBox(act,
                Main.localApiKeepAliveStatus(), textColor, dark);
        card.addView(keepAliveStatus, insetParams(act, 0, 6));

        final Switch floatingSwitch;
        final TextView floatingStatus;
        if (HostCompat.isV236() || HostCompat.isV241()) {
            LinearLayout floatingRow = new LinearLayout(act);
            floatingRow.setOrientation(LinearLayout.HORIZONTAL);
            floatingRow.setGravity(Gravity.CENTER_VERTICAL);
            floatingRow.setPadding(dp(act, 16), dp(act, 10), dp(act, 12), dp(act, 8));
            LinearLayout floatingLabels = new LinearLayout(act);
            floatingLabels.setOrientation(LinearLayout.VERTICAL);
            TextView floatingTitle = new TextView(act);
            floatingTitle.setText("小窗保活");
            floatingTitle.setTextColor(textColor);
            floatingTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            floatingTitle.setTypeface(Typeface.DEFAULT_BOLD);
            floatingLabels.addView(floatingTitle);
            TextView floatingDescription = new TextView(act);
            floatingDescription.setText("在屏幕边缘显示 DeepSeek 图标；点击可查看实时数据并直接修改 Key、端口、格式与 HTTPS");
            floatingDescription.setTextColor(subColor);
            floatingDescription.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            floatingLabels.addView(floatingDescription);
            floatingRow.addView(floatingLabels, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            floatingSwitch = new HubInsetSwitch(act);
            floatingSwitch.setChecked(Main.isLocalApiFloatingWindowEnabled());
            floatingSwitch.setThumbTintList(new android.content.res.ColorStateList(switchStates,
                    new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
            floatingSwitch.setTrackTintList(new android.content.res.ColorStateList(switchStates,
                    new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
            floatingSwitch.setBackground(null);
            floatingRow.addView(floatingSwitch);
            card.addView(floatingRow);
            floatingStatus = infoBox(act,
                    Main.localApiFloatingWindowStatus(), textColor, dark);
            card.addView(floatingStatus, insetParams(act, 0, 6));
        } else {
            floatingSwitch = null;
            floatingStatus = null;
        }

        LinearLayout batteryActions = new LinearLayout(act);
        batteryActions.setOrientation(LinearLayout.HORIZONTAL);
        batteryActions.setPadding(dp(act, 16), dp(act, 6), dp(act, 16), dp(act, 12));
        final TextView openBattery = dialogAction(act, "打开电池设置", 0xFFE07A22, dark);
        batteryActions.addView(openBattery, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView verifyBattery = dialogAction(act, "刷新状态", BRAND, dark);
        LinearLayout.LayoutParams verifyBatteryLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        verifyBatteryLp.leftMargin = dp(act, 8);
        batteryActions.addView(verifyBattery, verifyBatteryLp);
        card.addView(batteryActions);

        LinearLayout whitelistRow = new LinearLayout(act);
        whitelistRow.setOrientation(LinearLayout.HORIZONTAL);
        whitelistRow.setPadding(dp(act, 16), 0, dp(act, 16), dp(act, 12));
        final TextView whitelist = dialogAction(act, "一键加入白名单", 0xFF2E9E5B, dark);
        whitelistRow.addView(whitelist, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(whitelistRow);

        card.addView(makeDivider(act, divColor));

        LinearLayout enableRow = new LinearLayout(act);
        enableRow.setOrientation(LinearLayout.HORIZONTAL);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.setPadding(dp(act, 16), dp(act, 16), dp(act, 12), dp(act, 16));
        LinearLayout enableLabels = new LinearLayout(act);
        enableLabels.setOrientation(LinearLayout.VERTICAL);
        TextView enableTitle = new TextView(act);
        enableTitle.setText("启用本地 API 服务");
        enableTitle.setTextColor(textColor);
        enableTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        enableTitle.setTypeface(Typeface.DEFAULT_BOLD);
        enableLabels.addView(enableTitle);
        TextView enableDesc = new TextView(act);
        enableDesc.setText("监听本机和局域网；局域网调用同样必须携带 API Key。启用时前台保活会防止后台冻结；彻底退出 DeepSeek 后监听会停止，关闭时会清理复用的服务端会话。");
        enableDesc.setTextColor(subColor);
        enableDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        enableLabels.addView(enableDesc);
        enableRow.addView(enableLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch serviceSwitch = new HubInsetSwitch(act);
        serviceSwitch.setChecked(Main.isLocalApiEnabled());
        serviceSwitch.setThumbTintList(new android.content.res.ColorStateList(switchStates,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        serviceSwitch.setTrackTintList(new android.content.res.ColorStateList(switchStates,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        serviceSwitch.setBackground(null);
        enableRow.addView(serviceSwitch);
        card.addView(enableRow);

        if (isClosedV236OrV241Feature()) {
            card.addView(makeDivider(act, divColor));
            card.addView(simpleSwitchRow(act, "使用老版方案",
                    "默认关闭。恢复旧版快速/专家/识图模型路由；当前没有专家模式，可能无法使用。",
                    Main.isLocalApiLegacyModelRouteEnabled(), textColor, subColor, dark,
                    new CompoundButton.OnCheckedChangeListener() {
                        private boolean internal;

                        @Override public void onCheckedChanged(final CompoundButton button,
                                                               boolean checked) {
                            if (internal) return;
                            if (!checked) {
                                Main.setLocalApiLegacyModelRouteEnabled(false);
                                return;
                            }
                            internal = true;
                            button.setChecked(false);
                            internal = false;
                            new android.app.AlertDialog.Builder(act)
                                    .setTitle("使用老版方案")
                                    .setMessage("目前 DeepSeek 已移除专家模式。老版方案仍会请求旧的"
                                            + "快速、专家和识图路由，可能失效。是否继续开启？")
                                    .setNegativeButton("取消", null)
                                    .setPositiveButton("继续开启", (dialog, which) -> {
                                        if (!Main.setLocalApiLegacyModelRouteEnabled(true)) {
                                            Toast.makeText(act, "老版方案保存失败",
                                                    Toast.LENGTH_SHORT).show();
                                            return;
                                        }
                                        internal = true;
                                        button.setChecked(true);
                                        internal = false;
                                    })
                                    .show();
                        }
                    }));
        }

        card.addView(makeDivider(act, divColor));
        LinearLayout protocolRow = new LinearLayout(act);
        protocolRow.setOrientation(LinearLayout.HORIZONTAL);
        protocolRow.setGravity(Gravity.CENTER_VERTICAL);
        protocolRow.setPadding(dp(act, 16), dp(act, 14), dp(act, 12), dp(act, 12));
        TextView protocolLabel = new TextView(act);
        protocolLabel.setText("格式");
        protocolLabel.setTextColor(textColor);
        protocolLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        protocolLabel.setTypeface(Typeface.DEFAULT_BOLD);
        protocolRow.addView(protocolLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView protocolValue = new TextView(act);
        protocolValue.setText(localApiProtocolDisplayName() + "  \u203A");
        protocolValue.setTextColor(textColor);
        protocolValue.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        protocolValue.setTypeface(Typeface.DEFAULT);
        protocolValue.setGravity(Gravity.CENTER);
        protocolValue.setPadding(dp(act, 12), dp(act, 8), dp(act, 10), dp(act, 8));
        GradientDrawable protocolValueBg = new GradientDrawable();
        protocolValueBg.setCornerRadius(dp(act, 6));
        protocolValueBg.setColor(dark ? 0xFF2A2A2E : 0xFFF0F1F4);
        protocolValue.setBackground(protocolValueBg);
        protocolValue.setClickable(true);
        protocolRow.addView(protocolValue);
        card.addView(protocolRow);
        final TextView protocolDescription = infoBox(act,
                localApiProtocolDescription(), textColor, dark);
        card.addView(protocolDescription, insetParams(act, 0, 12));

        card.addView(makeDivider(act, divColor));
        TextView configTitle = sectionTitle(act, "连接配置", textColor);
        card.addView(configTitle);
        final TextView connectionInfo = infoBox(act, Main.localApiConnectionInfo(),
                textColor, dark);
        card.addView(connectionInfo, insetParams(act, 0, 12));

        LinearLayout copyActions = new LinearLayout(act);
        copyActions.setOrientation(LinearLayout.HORIZONTAL);
        copyActions.setPadding(dp(act, 16), dp(act, 10), dp(act, 16), dp(act, 4));
        final TextView copyUrl = dialogAction(act, "一键复制 URL", BRAND, dark);
        copyActions.addView(copyUrl, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView copyKey = dialogAction(act, "一键复制 API Key", BRAND, dark);
        LinearLayout.LayoutParams copyKeyLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyKeyLp.leftMargin = dp(act, 8);
        copyActions.addView(copyKey, copyKeyLp);
        final TextView copyLan = dialogAction(act, "复制局域网地址", 0xFF2E9E5B, dark);
        LinearLayout.LayoutParams copyLanLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        copyLanLp.leftMargin = dp(act, 8);
        copyActions.addView(copyLan, copyLanLp);
        card.addView(copyActions);

        TextView customTitle = sectionTitle(act, "自定义 API Key", textColor);
        customTitle.setPadding(dp(act, 16), dp(act, 14), dp(act, 16), dp(act, 6));
        card.addView(customTitle);
        final android.widget.EditText customKey = new android.widget.EditText(act);
        customKey.setSingleLine(true);
        customKey.setText(Main.localApiKey());
        customKey.setTextColor(textColor);
        customKey.setHintTextColor(subColor);
        customKey.setHint("8-256 位无空格 ASCII 字符");
        customKey.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        customKey.setPadding(dp(act, 12), dp(act, 10), dp(act, 12), dp(act, 10));
        GradientDrawable editBg = new GradientDrawable();
        editBg.setColor(dark ? 0xFF202024 : 0xFFF4F6FA);
        editBg.setCornerRadius(dp(act, 6));
        editBg.setStroke(dp(act, 1), dark ? 0xFF48484E : 0xFFD8DCE5);
        customKey.setBackground(editBg);
        card.addView(customKey, insetParams(act, 0, 8));
        LinearLayout keyActions = new LinearLayout(act);
        keyActions.setOrientation(LinearLayout.HORIZONTAL);
        keyActions.setPadding(dp(act, 16), dp(act, 6), dp(act, 16), dp(act, 14));
        final TextView saveKey = dialogAction(act, "保存自定义 Key", BRAND, dark);
        keyActions.addView(saveKey, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView randomKey = dialogAction(act, "生成随机 Key", 0xFFE07A22, dark);
        LinearLayout.LayoutParams randomLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        randomLp.leftMargin = dp(act, 8);
        keyActions.addView(randomKey, randomLp);
        card.addView(keyActions);

        TextView portTitle = sectionTitle(act, "自定义监听端口", textColor);
        portTitle.setPadding(dp(act, 16), dp(act, 8), dp(act, 16), dp(act, 6));
        card.addView(portTitle);
        final android.widget.EditText customPort = new android.widget.EditText(act);
        customPort.setSingleLine(true);
        customPort.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        customPort.setText(String.valueOf(Main.localApiPreferredPort(act)));
        customPort.setTextColor(textColor);
        customPort.setHintTextColor(subColor);
        customPort.setHint("1024–65535");
        customPort.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        customPort.setPadding(dp(act, 12), dp(act, 10), dp(act, 12), dp(act, 10));
        customPort.setMinHeight(dp(act, 48));
        GradientDrawable portEditBg = new GradientDrawable();
        portEditBg.setColor(dark ? 0xFF202024 : 0xFFF4F6FA);
        portEditBg.setCornerRadius(dp(act, 6));
        portEditBg.setStroke(dp(act, 1), dark ? 0xFF48484E : 0xFFD8DCE5);
        customPort.setBackground(portEditBg);
        card.addView(customPort, insetParams(act, 0, 8));
        LinearLayout portActions = new LinearLayout(act);
        portActions.setPadding(dp(act, 16), dp(act, 6), dp(act, 16), dp(act, 14));
        final TextView savePort = dialogAction(act, "保存监听端口", BRAND, dark);
        portActions.addView(savePort, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(portActions);

        card.addView(makeDivider(act, divColor));
        card.addView(sectionTitle(act, "Agent / Codex / Claude Code 兼容", textColor));
        final TextView agentHelp = infoBox(act, localApiAgentHelp(),
                textColor, dark);
        card.addView(agentHelp, insetParams(act, 0, 14));

        card.addView(makeDivider(act, divColor));
        card.addView(sectionTitle(act, "深度思考参数", textColor));
        TextView reasoningHelp = infoBox(act,
                "默认关闭。请求中附加任一参数即可让原生请求设置 thinking_enabled=true：\n"
                + "• \"thinking_enabled\": true\n"
                + "• \"deep_think\": true\n"
                + "• \"enable_thinking\": true\n"
                + "• \"thinking\": true 或 {\"type\":\"enabled\"}\n"
                + "• \"reasoning_effort\": \"medium\"\n"
                + "Responses 也支持 \"reasoning\": {\"effort\":\"medium\"}。\n"
                + (isClosedV236OrV241Feature() && !Main.isLocalApiLegacyModelRouteEnabled()
                ? "• deepseek-flash：当前统一模型；思考、搜索、图片与文件通过请求能力启用。\n"
                : "• deepseek-v4-flash：快速模式\n"
                + "• deepseek-v4-pro：专家模式\n"
                + "• deepseek-vision：识图模式\n")
                + "reasoning_effort 支持 minimal、low、medium、high、xhigh。",
                textColor, dark);
        card.addView(reasoningHelp, insetParams(act, 0, 14));

        card.addView(makeDivider(act, divColor));
        card.addView(sectionTitle(act, "实时监听与请求统计", textColor));
        final TextView runtime = infoBox(act, Main.localApiRuntimeStatus(), textColor, dark);
        card.addView(runtime, insetParams(act, 0, 16));

        card.addView(makeDivider(act, divColor));
        LinearLayout advancedRow = new LinearLayout(act);
        advancedRow.setOrientation(LinearLayout.HORIZONTAL);
        advancedRow.setGravity(Gravity.CENTER_VERTICAL);
        advancedRow.setPadding(dp(act, 16), dp(act, 15), dp(act, 12), dp(act, 15));
        advancedRow.setClickable(true);
        advancedRow.setFocusable(true);
        LinearLayout advancedLabels = new LinearLayout(act);
        advancedLabels.setOrientation(LinearLayout.VERTICAL);
        TextView advancedTitle = new TextView(act);
        advancedTitle.setText("高级设置");
        advancedTitle.setTextColor(textColor);
        advancedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        advancedTitle.setTypeface(Typeface.DEFAULT_BOLD);
        advancedLabels.addView(advancedTitle);
        TextView advancedDescription = new TextView(act);
        advancedDescription.setText("Cloudflare 自有域名、公网 IP、固定端口与连接诊断");
        advancedDescription.setTextColor(subColor);
        advancedDescription.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        advancedLabels.addView(advancedDescription);
        advancedRow.addView(advancedLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView advancedChevron = new TextView(act);
        advancedChevron.setText("\u203A");
        advancedChevron.setTextColor(subColor);
        advancedChevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        advancedChevron.setGravity(Gravity.CENTER);
        advancedRow.addView(advancedChevron, new LinearLayout.LayoutParams(
                dp(act, 28), ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(advancedRow);

        final TextView feedback = new TextView(act);
        feedback.setTextColor(BRAND);
        feedback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(act, 16), dp(act, 4), dp(act, 16), dp(act, 14));
        card.addView(feedback);

        final Runnable refresh = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                backgroundStatus.setText(UiLanguage.dynamic(act, Main.localApiBackgroundStatus(act)));
                keepAliveStatus.setText(UiLanguage.dynamic(act, Main.localApiKeepAliveStatus()));
                if (floatingStatus != null) floatingStatus.setText(UiLanguage.dynamic(
                        act, Main.localApiFloatingWindowStatus()));
                connectionInfo.setText(UiLanguage.dynamic(act, Main.localApiConnectionInfo()));
                runtime.setText(UiLanguage.dynamic(act, Main.localApiRuntimeStatus()));
                runtime.postDelayed(this, 1000L);
            }
        };
        final boolean[] serviceSwitchProgrammatic = new boolean[]{false};
        serviceSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (serviceSwitchProgrammatic[0]) return;
                if (checked && CloudPromptClient.supported()
                        && !CloudPromptClient.hasLocalApiGrant(act)) {
                    serviceSwitchProgrammatic[0] = true;
                    serviceSwitch.setChecked(false);
                    serviceSwitchProgrammatic[0] = false;
                    if (!CloudPromptClient.hasConsent(act)) {
                        feedback.setText(UiLanguage.dynamic(act, "请先完成云端授权"));
                        showCloudAuthorization(act, serviceSwitch, serviceSwitchProgrammatic);
                    } else {
                        feedback.setText(UiLanguage.dynamic(act, "正在获取云端授权…"));
                        serviceSwitch.setEnabled(false);
                        CloudPromptClient.activate(act, (ok, error) -> act.runOnUiThread(() -> {
                            serviceSwitch.setEnabled(true);
                            if (ok) {
                                boolean applied = Main.setLocalApiEnabled(true);
                                serviceSwitchProgrammatic[0] = true;
                                serviceSwitch.setChecked(applied);
                                serviceSwitchProgrammatic[0] = false;
                                feedback.setText(UiLanguage.dynamic(act, applied
                                        ? "正在启动监听…" : "服务启动失败，请查看运行状态"));
                            } else {
                                feedback.setText(UiLanguage.dynamic(act, "授权失败：" + cloudError(error)));
                                Toast.makeText(act, cloudError(error), Toast.LENGTH_LONG).show();
                            }
                            connectionInfo.setText(UiLanguage.dynamic(act, Main.localApiConnectionInfo()));
                            runtime.setText(UiLanguage.dynamic(act, Main.localApiRuntimeStatus()));
                            keepAliveStatus.setText(UiLanguage.dynamic(act, Main.localApiKeepAliveStatus()));
                        }));
                    }
                    return;
                }
                boolean applied = Main.setLocalApiEnabled(checked);
                if (checked && !applied) {
                    serviceSwitchProgrammatic[0] = true;
                    serviceSwitch.setChecked(false);
                    serviceSwitchProgrammatic[0] = false;
                    feedback.setText(UiLanguage.dynamic(act, "服务启动失败，请查看运行状态"));
                } else {
                    feedback.setText(UiLanguage.dynamic(act, checked ? "正在启动监听…"
                            : "服务已关闭，正在清理复用会话…"));
                }
                connectionInfo.setText(UiLanguage.dynamic(act, Main.localApiConnectionInfo()));
                runtime.setText(UiLanguage.dynamic(act, Main.localApiRuntimeStatus()));
                keepAliveStatus.setText(UiLanguage.dynamic(act, Main.localApiKeepAliveStatus()));
            }
        });
        if (floatingSwitch != null) {
            floatingSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton button, boolean checked) {
                    boolean applied = Main.setLocalApiFloatingWindowEnabled(act, checked);
                    if (!applied && checked) {
                        floatingSwitch.setChecked(false);
                        feedback.setText(UiLanguage.dynamic(act, "小窗保活启动失败，请查看运行状态"));
                    } else {
                        feedback.setText(UiLanguage.dynamic(act, checked
                                ? "请允许 Deekseep 显示在其他应用上；授权后图标会自动出现"
                                : "小窗保活已关闭"));
                    }
                    floatingStatus.setText(UiLanguage.dynamic(
                            act, Main.localApiFloatingWindowStatus()));
                }
            });
        }
        openBattery.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                localApiSettingsFromPage = true;
                if (!Main.openLocalApiBatterySettings(act)) {
                    localApiSettingsFromPage = false;
                    feedback.setText(UiLanguage.dynamic(act,
                            "无法打开系统电池设置，请从系统应用设置手动进入"));
                }
            }
        });
        verifyBattery.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                boolean allowed = Main.verifyLocalApiBackground(act);
                backgroundStatus.setText(UiLanguage.dynamic(act, Main.localApiBackgroundStatus(act)));
                feedback.setText(UiLanguage.dynamic(act, allowed
                        ? "后台设置已完成" : "未设为不限制；前台保活仍会继续运行"));
                connectionInfo.setText(UiLanguage.dynamic(act, Main.localApiConnectionInfo()));
                runtime.setText(UiLanguage.dynamic(act, Main.localApiRuntimeStatus()));
                keepAliveStatus.setText(UiLanguage.dynamic(act, Main.localApiKeepAliveStatus()));
            }
        });
        whitelist.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                feedback.setText(UiLanguage.dynamic(act, "正在申请 Root 权限并加入白名单…"));
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String result = Main.applyz18();
                        act.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                backgroundStatus.setText(UiLanguage.dynamic(
                                        act, Main.localApiBackgroundStatus(act)));
                                keepAliveStatus.setText(UiLanguage.dynamic(
                                        act, Main.localApiKeepAliveStatus()));
                                feedback.setText(UiLanguage.dynamic(act, result));
                            }
                        });
                    }
                }, "Deekseep-Whitelist").start();
            }
        });
        protocolValue.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                showProtocolPicker(act, new Runnable() {
                    @Override public void run() {
                        protocolValue.setText(localApiProtocolDisplayName() + "  \u203A");
                        protocolDescription.setText(UiLanguage.dynamic(act, localApiProtocolDescription()));
                        agentHelp.setText(UiLanguage.dynamic(act, localApiAgentHelp()));
                        connectionInfo.setText(UiLanguage.dynamic(act, Main.localApiConnectionInfo()));
                        runtime.setText(UiLanguage.dynamic(act, Main.localApiRuntimeStatus()));
                        feedback.setText(UiLanguage.dynamic(act,
                                "已切换为 " + localApiProtocolDisplayName() + " 格式"));
                    }
                });
            }
        });
        copyUrl.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText(act, "API URL", Main.localApiEndpoint());
                feedback.setText(UiLanguage.dynamic(act, "URL 已复制"));
                Toast.makeText(act, UiLanguage.dynamic(act, "URL 已复制"),
                        Toast.LENGTH_SHORT).show();
            }
        });
        copyKey.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                copyText(act, "API Key", Main.localApiKey());
                feedback.setText(UiLanguage.dynamic(act, "API Key 已复制"));
                Toast.makeText(act, UiLanguage.dynamic(act, "API Key 已复制"),
                        Toast.LENGTH_SHORT).show();
            }
        });
        copyLan.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String lan = Main.localApiLanEndpoint();
                if (lan == null || lan.trim().length() == 0) {
                    feedback.setText(UiLanguage.text(act,
                            "未检测到局域网地址，请确认已连接 Wi-Fi 并开启本地 API",
                            "No LAN address detected; confirm Wi-Fi is connected and the local API is enabled"));
                    Toast.makeText(act, UiLanguage.text(act,
                            "未检测到局域网地址", "No LAN address detected"),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                copyText(act, "LAN API URL", lan);
                feedback.setText(UiLanguage.dynamic(act, "局域网地址已复制"));
                Toast.makeText(act, UiLanguage.dynamic(act, "局域网地址已复制"),
                        Toast.LENGTH_SHORT).show();
            }
        });
        saveKey.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String result = Main.setCustomLocalApiKey(act, customKey.getText().toString());
                feedback.setText(UiLanguage.dynamic(act, result));
                Toast.makeText(act, UiLanguage.dynamic(act, result),
                        Toast.LENGTH_SHORT).show();
                connectionInfo.setText(UiLanguage.dynamic(act, Main.localApiConnectionInfo()));
            }
        });
        randomKey.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Main.rotateLocalApiKey(act);
                customKey.setText(Main.localApiKey());
                connectionInfo.setText(UiLanguage.dynamic(act, Main.localApiConnectionInfo()));
                feedback.setText(UiLanguage.dynamic(act, "已生成并启用新的随机 Key"));
                Toast.makeText(act, UiLanguage.dynamic(act, "已生成并启用新的随机 Key"),
                        Toast.LENGTH_SHORT).show();
            }
        });
        savePort.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String result = Main.setLocalApiPreferredPort(
                        act, customPort.getText().toString());
                feedback.setText(UiLanguage.dynamic(act, result));
                Toast.makeText(act, UiLanguage.dynamic(act, result),
                        Toast.LENGTH_SHORT).show();
                connectionInfo.setText(UiLanguage.dynamic(
                        act, Main.localApiConnectionInfo()));
                runtime.setText(UiLanguage.dynamic(act, Main.localApiRuntimeStatus()));
            }
        });
        advancedRow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                z19.show(act);
            }
        });

        addBuildFooter(act, card, subColor);
        UiLanguage.localizeTree(act, root);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));
        }
        openWithSlide(dialog, root);
        runtime.post(refresh);
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            public boolean onKey(android.content.DialogInterface d, int code, android.view.KeyEvent e) {
                if (code == android.view.KeyEvent.KEYCODE_BACK
                        && e.getAction() == android.view.KeyEvent.ACTION_UP) {
                    slideOutAndDismiss(dialog, root);
                    return true;
                }
                return false;
            }
        });
    }

    private static String localApiProtocolDescription() {
        if (z2.PROTOCOL_ANTHROPIC.equals(Main.localApiProtocol())) {
            return "Anthropic 格式已选中。base URL 使用页面显示的本机或局域网根地址（不附加 /v1），"
                    + "提供 POST /v1/messages 与 /v1/messages/count_tokens；支持普通 JSON、"
                    + "SSE、tool_use / tool_result 和 thinking 参数。OpenAI 路由在此模式下会明确返回协议不匹配。";
        }
        return "OpenAI 格式已选中。base URL 以 /v1 结尾，提供 /models、"
                + "/chat/completions 与 /responses；支持普通 JSON、SSE 和 Agent 工具循环。"
                + "Anthropic 路由在此模式下会明确返回协议不匹配。";
    }

    private static String localApiAgentHelp() {
        String modelHelp = isClosedV236OrV241Feature() && !Main.isLocalApiLegacyModelRouteEnabled()
                ? "模型名使用 deepseek-flash。"
                : "模型名使用 deepseek-v4-flash、deepseek-v4-pro 或 deepseek-vision。";
        if (z2.PROTOCOL_ANTHROPIC.equals(Main.localApiProtocol())) {
            return "Anthropic Messages API 已启用：\n"
                    + "• Claude Code：ANTHROPIC_BASE_URL 设为上方地址，ANTHROPIC_AUTH_TOKEN 设为上方 Key\n"
                    + "• 支持 message_start / content_block_* / message_delta / message_stop SSE\n"
                    + "• 支持客户端 tools、tool_use、tool_result、并行工具选择与重复副作用抑制\n"
                    + "• thinking={\"type\":\"enabled\"} 或 adaptive 会打开 DeepSeek 深度思考\n"
                    + "• 每 5 秒发送 ping、累计 token，并在正文开始前恢复 thinking 状态\n"
                    + modelHelp;
        }
        return "OpenAI Chat Completions 与 Responses API 已启用：\n"
                + "• Chat：function tools / tool_calls / tool 结果回传\n"
                + "• Responses：function、custom、shell、apply_patch 与 previous_response_id\n"
                + "• 成功工具按名称与规范化参数去重，避免 Agent 重复执行副作用\n"
                + "• 支持 chunked 请求体、stream_options.include_usage 与 5 秒 SSE 心跳\n"
                + "Codex 自定义提供商请把 base_url 设为上方地址、wire API 设为 responses。"
                + modelHelp;
    }

    /** 独立的对话转接控制页：配置 OpenAI/Anthropic 格式、URL、API Key、模型。 */
    private static void showChatRelayPage(final Activity act) {
        final boolean dark = isDark(act);
        final int bgColor = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF777B82;
        final int divColor = dark ? 0xFF3A3A3D : 0xFFEEEEEE;
        final int fieldBg = dark ? 0xFF2A2A2E : 0xFFF0F2F7;
        final int[][] switchStates = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };

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
        back.setTextColor(textColor);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(act, 8), 0, dp(act, 8), 0);
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { slideOutAndDismiss(dialog, root); }
        });
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(act, 40)));
        TextView title = new TextView(act);
        title.setText("对话转接");
        title.setTextColor(textColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
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
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(cardColor);
        cardBackground.setCornerRadius(dp(act, 8));
        card.setBackground(cardBackground);
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 20));
        scroll.addView(card, cardLp);

        card.addView(sectionTitle(act, "启用状态", textColor));
        final TextView enableDesc = infoBox(act,
                "开启后，原生聊天框发送的消息将转发到自配的 OpenAI/Anthropic 服务商，"
                + "不再走 DeepSeek 原生链路。支持思考链内容正常落地到思考面板。",
                textColor, dark);
        card.addView(enableDesc, insetParams(act, 0, 6));

        LinearLayout enableRow = new LinearLayout(act);
        enableRow.setOrientation(LinearLayout.HORIZONTAL);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.setPadding(dp(act, 16), dp(act, 10), dp(act, 12), dp(act, 16));
        TextView enableLabel = new TextView(act);
        enableLabel.setText("启用对话转接");
        enableLabel.setTextColor(textColor);
        enableLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        enableLabel.setTypeface(Typeface.DEFAULT_BOLD);
        enableRow.addView(enableLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch relaySwitch = new HubInsetSwitch(act);
        relaySwitch.setChecked(z17.isEnabled());
        relaySwitch.setThumbTintList(new android.content.res.ColorStateList(switchStates,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        relaySwitch.setTrackTintList(new android.content.res.ColorStateList(switchStates,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        relaySwitch.setBackground(null);
        enableRow.addView(relaySwitch);
        card.addView(enableRow);

        card.addView(makeDivider(act, divColor));

        card.addView(sectionTitle(act, "格式选择", textColor));
        LinearLayout protocolRow = new LinearLayout(act);
        protocolRow.setOrientation(LinearLayout.HORIZONTAL);
        protocolRow.setGravity(Gravity.CENTER_VERTICAL);
        protocolRow.setPadding(dp(act, 16), dp(act, 10), dp(act, 12), dp(act, 12));
        final String[] format = {z17.format()};
        final TextView openaiChip = formatChip(act, "OpenAI", textColor, fieldBg);
        final TextView anthropicChip = formatChip(act, "Anthropic", textColor, fieldBg);
        protocolRow.addView(openaiChip);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        aLp.rightMargin = dp(act, 8);
        protocolRow.addView(anthropicChip, aLp);
        card.addView(protocolRow);
        final Runnable styleFormat = new Runnable() {
            @Override public void run() {
                openaiChip.setTextColor("openai".equals(format[0]) ? 0xFFFFFFFF : textColor);
                anthropicChip.setTextColor("anthropic".equals(format[0]) ? 0xFFFFFFFF : textColor);
                openaiChip.setBackgroundColor("openai".equals(format[0]) ? BRAND : fieldBg);
                anthropicChip.setBackgroundColor("anthropic".equals(format[0]) ? BRAND : fieldBg);
            }
        };
        openaiChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { format[0] = "openai"; styleFormat.run(); }
        });
        anthropicChip.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { format[0] = "anthropic"; styleFormat.run(); }
        });
        styleFormat.run();
        final TextView formatDesc = infoBox(act,
                "OpenAI 格式兼容 /v1/chat/completions，Anthropic 格式兼容 /v1/messages。"
                + "两者均支持 SSE 流式传输与思考链提取。",
                textColor, dark);
        card.addView(formatDesc, insetParams(act, 0, 12));

        card.addView(makeDivider(act, divColor));
        card.addView(sectionTitle(act, "连接配置", textColor));

        final android.widget.EditText urlField = field(act,
                "Base URL（例如 https://api.openai.com）",
                z17.baseUrl(), textColor, fieldBg);
        card.addView(urlField, insetParams(act, 0, 6));

        final android.widget.EditText keyField = field(act, "API Key",
                z17.apiKey(), textColor, fieldBg);
        card.addView(keyField, insetParams(act, 0, 6));

        final android.widget.EditText modelField = field(act, "模型名称",
                z17.model(), textColor, fieldBg);
        card.addView(modelField, insetParams(act, 0, 10));

        LinearLayout saveRow = new LinearLayout(act);
        saveRow.setOrientation(LinearLayout.HORIZONTAL);
        saveRow.setPadding(dp(act, 16), dp(act, 6), dp(act, 16), dp(act, 6));
        final TextView saveBtn = dialogAction(act, "保存配置", BRAND, dark);
        saveRow.addView(saveBtn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView fetchBtn = dialogAction(act, "获取模型列表", 0xFFE07A22, dark);
        LinearLayout.LayoutParams fetchLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        fetchLp.leftMargin = dp(act, 8);
        saveRow.addView(fetchBtn, fetchLp);
        card.addView(saveRow);

        final TextView feedback = new TextView(act);
        feedback.setTextColor(BRAND);
        feedback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(act, 16), dp(act, 4), dp(act, 16), dp(act, 14));
        card.addView(feedback);

        card.addView(makeDivider(act, divColor));
        card.addView(sectionTitle(act, "说明", textColor));
        final TextView helpText = infoBox(act,
                "• 开启后，聊天消息不再发送到 DeepSeek 服务器，而是转发到上方配置的 API。\n"
                + "• 支持思考链（reasoning）：OpenAI 格式的 reasoning_content 和 Anthropic 格式的 thinking "
                + "块会自动提取并注入到原生 UI 的思考面板。\n"
                + "• 流式响应会实时返回，但当前版本使用非流式批量写入数据库以确保持久化。\n"
                + "• 模型列表获取需要 API 支持 /v1/models 端点。\n"
                + "• 配置保存后立即生效，无需重启 DeepSeek。",
                textColor, dark);
        card.addView(helpText, insetParams(act, 0, 14));

        addBuildFooter(act, card, subColor);

        relaySwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            private boolean reverting;
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (reverting) return;
                if (checked && (z17.apiKey().length() == 0 || z17.model().length() == 0)) {
                    reverting = true;
                    button.setChecked(false);
                    reverting = false;
                    feedback.setText("请先配置 API Key、URL 和模型再启用");
                    return;
                }
                z17.setEnabled(checked);
                feedback.setText(checked ? "对话转接已启用" : "对话转接已关闭");
            }
        });
        saveBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String k = keyField.getText().toString().trim();
                String u = urlField.getText().toString().trim();
                String m = modelField.getText().toString().trim();
                if (k.length() == 0 || u.length() == 0 || m.length() == 0) {
                    feedback.setText("API Key、URL 和模型都不能为空");
                    return;
                }
                z17.save(k, format[0], u, m);
                feedback.setText("配置已保存");
            }
        });
        fetchBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                feedback.setText("正在获取模型列表…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        try {
                            final java.util.List<String> models = z17.fetchModels(
                                    keyField.getText().toString().trim(), format[0],
                                    urlField.getText().toString().trim());
                            act.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    if (models.isEmpty()) {
                                        feedback.setText("没有获取到模型");
                                    } else {
                                        String[] arr = models.toArray(new String[0]);
                                        new android.app.AlertDialog.Builder(act)
                                                .setTitle("选择模型")
                                                .setItems(arr,
                                                        new android.content.DialogInterface
                                                                .OnClickListener() {
                                                            @Override public void onClick(
                                                                    android.content.DialogInterface d,
                                                                    int which) {
                                                                modelField.setText(arr[which]);
                                                            }
                                                        })
                                                .setNegativeButton("取消", null)
                                                .show();
                                        feedback.setText("已获取 " + models.size() + " 个模型");
                                    }
                                }
                            });
                        } catch (Throwable e) {
                            final String msg = e.getMessage() == null
                                    ? e.getClass().getSimpleName() : e.getMessage();
                            act.runOnUiThread(new Runnable() {
                                @Override public void run() {
                                    feedback.setText("获取失败：" + msg);
                                }
                            });
                        }
                    }
                }, "Deekseep-Relay-Fetch").start();
            }
        });

        UiLanguage.localizeTree(act, root);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));
        }
        openWithSlide(dialog, root);
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            public boolean onKey(android.content.DialogInterface d, int code, android.view.KeyEvent e) {
                if (code == android.view.KeyEvent.KEYCODE_BACK
                        && e.getAction() == android.view.KeyEvent.ACTION_UP) {
                    slideOutAndDismiss(dialog, root);
                    return true;
                }
                return false;
            }
        });
    }

    private static String localApiProtocolDisplayName() {
        return z2.PROTOCOL_ANTHROPIC.equals(Main.localApiProtocol())
                ? "Anthropic" : "OpenAI";
    }

    private static TextView protocolPickerOption(Activity act, String title, String description,
                                                 boolean selected, boolean dark) {
        TextView option = new TextView(act);
        option.setText(UiLanguage.dynamic(act,
                title + (selected ? "   " + UiLanguage.text(act, "[已选]", "[Selected]") : "") + "\n" + description));
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

    private static void showProtocolPicker(final Activity act, final Runnable onChanged) {
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
        title.setText("选择 API 格式");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(dark ? 0xFFECECEC : 0xFF1A1A1A);
        title.setPadding(0, 0, 0, dp(act, 12));
        root.addView(title);
        boolean anthropic = z2.PROTOCOL_ANTHROPIC.equals(Main.localApiProtocol());
        TextView openAi = protocolPickerOption(act, "OpenAI",
                "Chat Completions、Responses 与 /v1/models", !anthropic, dark);
        TextView anthropicOption = protocolPickerOption(act, "Anthropic",
                "Messages、count_tokens 与 Claude Code", anthropic, dark);
        root.addView(openAi, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams anthropicLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        anthropicLp.topMargin = dp(act, 10);
        root.addView(anthropicOption, anthropicLp);
        openAi.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Main.setLocalApiProtocol(act, z2.PROTOCOL_OPENAI);
                dialog.dismiss();
                if (onChanged != null) onChanged.run();
            }
        });
        anthropicOption.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Main.setLocalApiProtocol(act, z2.PROTOCOL_ANTHROPIC);
                dialog.dismiss();
                if (onChanged != null) onChanged.run();
            }
        });
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
    private static void showLocalApiDialog(final Activity act, final TextView pageStatus) {
        final boolean dark = isDark(act);
        final int cardColor = dark ? 0xFF29292D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFF2F2F2 : 0xFF202124;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF6F737A;
        final Dialog dialog = new Dialog(act, android.R.style.Theme_Translucent_NoTitleBar);

        FrameLayout root = new FrameLayout(act);
        root.setBackgroundColor(0x66000000);
        root.setPadding(dp(act, 20), dp(act, 24), dp(act, 20), dp(act, 24));
        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(act, 20), dp(act, 20), dp(act, 20), dp(act, 16));
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(cardColor);
        panelBg.setCornerRadius(dp(act, 10));
        panel.setBackground(panelBg);
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        root.addView(panel, panelLp);

        TextView title = new TextView(act);
        title.setText("DeepSeek 本地 API");
        title.setTextColor(textColor);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title);

        TextView warning = new TextView(act);
        warning.setText("服务绑定本机与局域网地址。API 密钥等同 DeepSeek 调用权限；仅在可信网络使用，"
                + "不要公开或转发。DeepSeek 被彻底退出后服务会随进程停止。");
        warning.setTextColor(subColor);
        warning.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams warningLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        warningLp.topMargin = dp(act, 8);
        panel.addView(warning, warningLp);

        final TextView info = new TextView(act);
        info.setText(UiLanguage.dynamic(act, Main.localApiConnectionInfo()));
        info.setTextColor(textColor);
        info.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        info.setTextIsSelectable(true);
        info.setPadding(dp(act, 12), dp(act, 12), dp(act, 12), dp(act, 12));
        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(dark ? 0xFF202024 : 0xFFF4F6FA);
        infoBg.setCornerRadius(dp(act, 6));
        info.setBackground(infoBg);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = dp(act, 14);
        panel.addView(info, infoLp);

        LinearLayout actions = new LinearLayout(act);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(act, 16);
        panel.addView(actions, actionsLp);

        final TextView copy = dialogAction(act, "复制连接信息", BRAND, dark);
        copy.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                android.content.ClipboardManager clipboard =
                        (android.content.ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                            "Deekseep Local API", Main.localApiConnectionInfo()));
                    copy.setText(UiLanguage.dynamic(act, "已复制"));
                }
            }
        });
        actions.addView(copy);

        TextView rotate = dialogAction(act, "轮换密钥", 0xFFE07A22, dark);
        LinearLayout.LayoutParams rotateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rotateLp.leftMargin = dp(act, 8);
        actions.addView(rotate, rotateLp);
        rotate.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                info.setText(UiLanguage.dynamic(act, Main.rotateLocalApiKey(act)));
                copy.setText(UiLanguage.dynamic(act, "复制连接信息"));
            }
        });

        TextView close = dialogAction(act, "关闭", subColor, dark);
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        closeLp.leftMargin = dp(act, 8);
        actions.addView(close, closeLp);
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dialog.dismiss(); }
        });

        root.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dialog.dismiss(); }
        });
        panel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { /* consume backdrop click */ }
        });
        dialog.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            public void onDismiss(android.content.DialogInterface d) {
                if (pageStatus != null) {
                    pageStatus.setText(UiLanguage.dynamic(act,
                            "监听本机与局域网地址，所有业务请求均需 API Key；支持非流式/SSE。"
                            + "点本行查看地址、密钥与连接方法。\n"
                            + Main.localApiConnectionInfo()));
                }
            }
        });
        UiLanguage.localizeTree(act, root);
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
        }
    }

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
        attachPressMotion(button);
        return button;
    }

    /** Short, GPU-only tactile feedback shared by dialog actions. */
    private static void attachPressMotion(final View view) {
        if (view == null) return;
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View touched, android.view.MotionEvent event) {
                if (!touched.isEnabled()) return false;
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    touched.animate().cancel();
                    touched.animate()
                            .scaleX(0.97f).scaleY(0.97f).alpha(0.82f)
                            .setDuration(110L)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                            .start();
                } else if (action == android.view.MotionEvent.ACTION_UP
                        || action == android.view.MotionEvent.ACTION_CANCEL) {
                    touched.animate().cancel();
                    touched.animate()
                            .scaleX(1f).scaleY(1f).alpha(1f)
                            .setDuration(140L)
                            .setInterpolator(new android.view.animation.DecelerateInterpolator(2f))
                            .start();
                }
                // Keep returning false so Android retains click, focus and accessibility behavior.
                return false;
            }
        });
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

    public static volatile Dialog sActiveActivationDialog = null;

    public static void onActivityDestroyed(Activity act) {
        if (act == null) return;
        restoreHostCarrierBar(act);
        Dialog d = sActiveActivationDialog;
        if (d != null) {
            if (d.getOwnerActivity() == act || d.getContext() == act) {
                try { d.dismiss(); } catch (Throwable ignored) {}
                sActiveActivationDialog = null;
            }
        }
    }

    public static void showActivationDialog(final Activity act, final ClassLoader cl, final Runnable onSuccess) {
        if (!BuildInfo.PROTECTED_BUILD) return;
        if (!CloudPromptClient.supported()) {
            Toast.makeText(act, "云端仅支持 Closed 版的 2.3.6 / 2.4.1 国内宿主",
                    Toast.LENGTH_LONG).show();
            return;
        }
        if (act == null || act.isFinishing()) return;
        if (sActiveActivationDialog != null && sActiveActivationDialog.isShowing()) return;

        final boolean dark = isDark(act);
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFA0A0A0 : 0xFF666666;
        final int cardBg = dark ? 0xFF1E1F24 : 0xFFF7F8FA;
        final int borderCol = dark ? 0xFF353740 : 0xFFE0E2E8;
        final int accentCol = dark ? 0xFF4F75FF : 0xFF2D5AF5;

        final String deviceCode = CloudPromptClient.getDeviceCode(act);
        final String savedCard = CloudPromptClient.getSavedCard(act);

        final Dialog dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOwnerActivity(act);
        dialog.setOnKeyListener((dialogInterface, keyCode, keyEvent) -> {
            if (keyCode == android.view.KeyEvent.KEYCODE_BACK && keyEvent.getAction() == android.view.KeyEvent.ACTION_UP) {
                if (act != null && !act.isFinishing()) {
                    act.finishAffinity();
                }
                return true;
            }
            return false;
        });

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 22), dp(act, 20), dp(act, 22), dp(act, 20));
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(dark ? 0xFF23242B : 0xFFFFFFFF);
        rootBg.setCornerRadius(dp(act, 16));
        root.setBackground(rootBg);

        // Title
        TextView title = new TextView(act);
        title.setText("Deekseep 模块认证");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        root.addView(title);

        // Subtitle
        TextView intro = new TextView(act);
        intro.setText("请输入卡密以激活模块注入");
        intro.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        intro.setTextColor(subColor);
        intro.setPadding(0, dp(act, 6), 0, dp(act, 14));
        root.addView(intro);

        // Device Code Row with Copy Button
        TextView devLabel = new TextView(act);
        devLabel.setText("本机设备识别码：");
        devLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        devLabel.setTypeface(Typeface.DEFAULT_BOLD);
        devLabel.setTextColor(textColor);
        root.addView(devLabel);

        LinearLayout devBox = new LinearLayout(act);
        devBox.setOrientation(LinearLayout.HORIZONTAL);
        devBox.setGravity(Gravity.CENTER_VERTICAL);
        devBox.setPadding(dp(act, 10), dp(act, 8), dp(act, 10), dp(act, 8));
        GradientDrawable devBg = new GradientDrawable();
        devBg.setColor(cardBg);
        devBg.setCornerRadius(dp(act, 8));
        devBg.setStroke(dp(act, 1), borderCol);
        devBox.setBackground(devBg);

        TextView devText = new TextView(act);
        devText.setText(deviceCode);
        devText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        devText.setTypeface(Typeface.MONOSPACE);
        devText.setTextColor(textColor);
        devText.setSingleLine(true);
        devText.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        devBox.addView(devText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView copyBtn = new TextView(act);
        copyBtn.setText("复制");
        copyBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        copyBtn.setTypeface(Typeface.DEFAULT_BOLD);
        copyBtn.setTextColor(accentCol);
        copyBtn.setPadding(dp(act, 8), dp(act, 4), dp(act, 4), dp(act, 4));
        copyBtn.setOnClickListener(v -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("device_code", deviceCode));
                Toast.makeText(act, "设备识别码已复制到剪贴板", Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                Toast.makeText(act, "复制失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        devBox.addView(copyBtn);

        LinearLayout.LayoutParams devBoxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        devBoxLp.topMargin = dp(act, 4);
        devBoxLp.bottomMargin = dp(act, 4);
        root.addView(devBox, devBoxLp);

        TextView botHint = new TextView(act);
        botHint.setText("• 发送 /key " + (deviceCode.length() > 18 ? deviceCode.substring(0, 18) + "..." : deviceCode) + " 给机器人 @Deekseepapp_bot 获取永久卡密");
        botHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        botHint.setTextColor(subColor);
        botHint.setPadding(dp(act, 2), 0, dp(act, 2), dp(act, 14));
        root.addView(botHint);

        // Input Card Box
        final EditText cardInput = new EditText(act);
        cardInput.setHint("请输入卡密 (永久有效)");
        cardInput.setHintTextColor(subColor);
        cardInput.setTextColor(textColor);
        cardInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cardInput.setTypeface(Typeface.MONOSPACE);
        cardInput.setPadding(dp(act, 12), dp(act, 11), dp(act, 12), dp(act, 11));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(cardBg);
        inputBg.setCornerRadius(dp(act, 8));
        inputBg.setStroke(dp(act, 1), borderCol);
        cardInput.setBackground(inputBg);
        cardInput.setSingleLine(true);
        if (savedCard != null && !savedCard.isEmpty()) {
            cardInput.setText(savedCard);
            cardInput.setSelection(savedCard.length());
        }
        root.addView(cardInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Action Button: 激活并注入
        final TextView activateBtn = new TextView(act);
        activateBtn.setText("激活并注入");
        activateBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        activateBtn.setTypeface(Typeface.DEFAULT_BOLD);
        activateBtn.setTextColor(0xFFFFFFFF);
        activateBtn.setGravity(Gravity.CENTER);
        activateBtn.setPadding(dp(act, 12), dp(act, 12), dp(act, 12), dp(act, 12));
        GradientDrawable aBg = new GradientDrawable();
        aBg.setColor(accentCol);
        aBg.setCornerRadius(dp(act, 8));
        activateBtn.setBackground(aBg);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        aLp.topMargin = dp(act, 14);
        root.addView(activateBtn, aLp);

        // Bottom Row: Right-aligned 人工认证
        LinearLayout bottomRow = new LinearLayout(act);
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.RIGHT);
        bottomRow.setPadding(0, dp(act, 12), 0, 0);

        TextView appealBtn = new TextView(act);
        appealBtn.setText("无 TG 人工认证 >");
        appealBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        appealBtn.setTextColor(accentCol);
        appealBtn.setPadding(dp(act, 6), dp(act, 4), dp(act, 4), dp(act, 4));
        appealBtn.setOnClickListener(v -> {
            try {
                String appealUrl = "https://license.lllucccian.top/deepseek/v1/appeal?device=" + Uri.encode(deviceCode);
                Intent it = new Intent(Intent.ACTION_VIEW, Uri.parse(appealUrl));
                act.startActivity(it);
            } catch (Throwable t) {
                Toast.makeText(act, "无法打开申请网页: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        bottomRow.addView(appealBtn);
        root.addView(bottomRow);

        activateBtn.setOnClickListener(v -> {
            final String card = cardInput.getText().toString().trim();
            if (card.isEmpty()) {
                Toast.makeText(act, "请输入卡密后再激活", Toast.LENGTH_SHORT).show();
                return;
            }
            activateBtn.setEnabled(false);
            activateBtn.setText("正在验证与激活…");
            CloudPromptClient.saveConsent(act);
            CloudPromptClient.activate(act, card, (ok, error) -> act.runOnUiThread(() -> {
                activateBtn.setEnabled(true);
                activateBtn.setText("激活并注入");
                if (error == null && Boolean.TRUE.equals(ok)) {
                    Toast.makeText(act, "认证成功，模块已正常注入！", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    if (onSuccess != null) onSuccess.run();
                } else {
                    Toast.makeText(act, cloudError(error), Toast.LENGTH_LONG).show();
                }
            }));
        });

        FrameLayout container = new FrameLayout(act);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        int screenW = act.getResources().getDisplayMetrics().widthPixels;
        int cardWidth = Math.min((int) (screenW * 0.90f), dp(act, 380));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        container.addView(root, cardLp);

        dialog.setContentView(container);
        Window w = dialog.getWindow();
        if (w != null) {
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.6f);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        }
        hideHostCarrierBar(act);
        dialog.setOnDismissListener(d -> {
            if (sActiveActivationDialog == dialog) sActiveActivationDialog = null;
            restoreHostCarrierBar(act);
        });
        dialog.setOnShowListener(d -> {
            hideHostCarrierBar(act);
            attachCarrierBarIfPresent(dialog);
        });
        sActiveActivationDialog = dialog;
        dialog.show();
        attachCarrierBarIfPresent(dialog);
        if (w != null) {
            final View decor = w.getDecorView();
            decor.post(() -> {
                hideHostCarrierBar(act);
                attachCarrierBarIfPresent(dialog);
            });
            decor.postDelayed(() -> {
                hideHostCarrierBar(act);
                attachCarrierBarIfPresent(dialog);
            }, 120);
        }
    }

    public static void showCloudAuthorization(final Activity act, final CompoundButton targetSwitch,
            final boolean[] programmatic) {
        showActivationDialog(act, null, () -> {
            if (targetSwitch != null) {
                if (programmatic != null) programmatic[0] = true;
                targetSwitch.setChecked(Main.setLocalApiEnabled(true));
                if (programmatic != null) programmatic[0] = false;
            }
        });
    }

    public static void showDeviceInfoDialog(final Activity act) {
        if (!BuildInfo.PROTECTED_BUILD || act == null || act.isFinishing()) return;
        final boolean dark = isDark(act);
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFA0A0A0 : 0xFF666666;
        final int cardBg = dark ? 0xFF1E1F24 : 0xFFF7F8FA;
        final int borderCol = dark ? 0xFF353740 : 0xFFE0E2E8;
        final int accentCol = dark ? 0xFF4F75FF : 0xFF2D5AF5;

        final String deviceCode = CloudPromptClient.getDeviceCode(act);
        final String savedCard = CloudPromptClient.getSavedCard(act);

        final Dialog dialog = new Dialog(act);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 22), dp(act, 20), dp(act, 22), dp(act, 20));
        GradientDrawable rootBg = new GradientDrawable();
        rootBg.setColor(dark ? 0xFF23242B : 0xFFFFFFFF);
        rootBg.setCornerRadius(dp(act, 16));
        root.setBackground(rootBg);

        // Header Title
        TextView title = new TextView(act);
        title.setText(UiLanguage.text(act, "查看设备信息", "Device Information"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        root.addView(title);

        // Warning Banner: "敏感信息，请勿泄露"
        LinearLayout warnBox = new LinearLayout(act);
        warnBox.setOrientation(LinearLayout.VERTICAL);
        warnBox.setPadding(dp(act, 12), dp(act, 10), dp(act, 12), dp(act, 10));
        GradientDrawable warnBg = new GradientDrawable();
        warnBg.setColor(dark ? 0x2A5C3B00 : 0x15FFA000);
        warnBg.setCornerRadius(dp(act, 8));
        warnBg.setStroke(dp(act, 1), dark ? 0x66FFB300 : 0x55FFA000);
        warnBox.setBackground(warnBg);

        TextView warnTitle = new TextView(act);
        warnTitle.setText(UiLanguage.text(act, "敏感信息，请勿泄露", "Sensitive Info, Do Not Disclose"));
        warnTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        warnTitle.setTypeface(Typeface.DEFAULT_BOLD);
        warnTitle.setTextColor(dark ? 0xFFFFC107 : 0xFFE65100);
        warnBox.addView(warnTitle);

        TextView warnDesc = new TextView(act);
        warnDesc.setText(UiLanguage.text(act,
                "包含设备特征识别码与授权卡密，请妥善保管，切勿发送给陌生人或公开到群聊。",
                "Contains hardware device code and license key. Keep confidential and do not share in public groups."));
        warnDesc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        warnDesc.setTextColor(dark ? 0xFFDDDDDD : 0xFF555555);
        warnDesc.setPadding(0, dp(act, 4), 0, 0);
        warnBox.addView(warnDesc);

        LinearLayout.LayoutParams warnLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        warnLp.topMargin = dp(act, 12);
        warnLp.bottomMargin = dp(act, 14);
        root.addView(warnBox, warnLp);

        // 1. Device Code
        TextView devLabel = new TextView(act);
        devLabel.setText(UiLanguage.text(act, "设备授权码：", "Device Authorization Code:"));
        devLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        devLabel.setTypeface(Typeface.DEFAULT_BOLD);
        devLabel.setTextColor(textColor);
        root.addView(devLabel);

        LinearLayout devBox = new LinearLayout(act);
        devBox.setOrientation(LinearLayout.HORIZONTAL);
        devBox.setGravity(Gravity.CENTER_VERTICAL);
        devBox.setPadding(dp(act, 10), dp(act, 6), dp(act, 6), dp(act, 6));
        GradientDrawable devBg = new GradientDrawable();
        devBg.setColor(cardBg);
        devBg.setCornerRadius(dp(act, 8));
        devBg.setStroke(dp(act, 1), borderCol);
        devBox.setBackground(devBg);

        final String devVal = deviceCode != null && !deviceCode.isEmpty() ? deviceCode : "(未获取到设备码)";
        TextView devText = new TextView(act);
        devText.setText(devVal);
        devText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        devText.setTypeface(Typeface.MONOSPACE);
        devText.setTextColor(textColor);
        devText.setSingleLine(true);
        devText.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        devBox.addView(devText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Copy button for Device Code: ICON ONLY, NO TEXT
        FrameLayout copyDevBtn = new FrameLayout(act);
        copyDevBtn.setClickable(true);
        copyDevBtn.setFocusable(true);
        copyDevBtn.setContentDescription("Copy device code");
        int btnPad = dp(act, 7);
        copyDevBtn.setPadding(btnPad, btnPad, btnPad, btnPad);
        GradientDrawable copyBg1 = new GradientDrawable();
        copyBg1.setCornerRadius(dp(act, 6));
        copyBg1.setColor(dark ? 0x22FFFFFF : 0x12000000);
        copyDevBtn.setBackground(copyBg1);
        HubMaterialGlyphView copyDevIcon = new HubMaterialGlyphView(act, "ds_action_copy", accentCol);
        copyDevBtn.addView(copyDevIcon, new FrameLayout.LayoutParams(dp(act, 18), dp(act, 18), Gravity.CENTER));
        copyDevBtn.setOnClickListener(v -> {
            if (deviceCode != null && !deviceCode.isEmpty()) {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("device_code", deviceCode));
                    Toast.makeText(act, UiLanguage.text(act, "设备授权码已复制到剪贴板", "Device code copied to clipboard"), Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Toast.makeText(act, "复制失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
        devBox.addView(copyDevBtn, new LinearLayout.LayoutParams(dp(act, 34), dp(act, 34)));

        LinearLayout.LayoutParams devBoxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        devBoxLp.topMargin = dp(act, 4);
        devBoxLp.bottomMargin = dp(act, 14);
        root.addView(devBox, devBoxLp);

        // 2. Saved Card / Key
        TextView cardLabel = new TextView(act);
        cardLabel.setText(UiLanguage.text(act, "激活卡密 / 授权凭证：", "License Card Key:"));
        cardLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        cardLabel.setTypeface(Typeface.DEFAULT_BOLD);
        cardLabel.setTextColor(textColor);
        root.addView(cardLabel);

        LinearLayout cardBox = new LinearLayout(act);
        cardBox.setOrientation(LinearLayout.HORIZONTAL);
        cardBox.setGravity(Gravity.CENTER_VERTICAL);
        cardBox.setPadding(dp(act, 10), dp(act, 6), dp(act, 6), dp(act, 6));
        GradientDrawable cardBoxBg = new GradientDrawable();
        cardBoxBg.setColor(cardBg);
        cardBoxBg.setCornerRadius(dp(act, 8));
        cardBoxBg.setStroke(dp(act, 1), borderCol);
        cardBox.setBackground(cardBoxBg);

        final boolean hasCard = savedCard != null && !savedCard.trim().isEmpty();
        TextView cardText = new TextView(act);
        cardText.setText(hasCard ? savedCard.trim() : UiLanguage.text(act, "（未绑定卡密）", "(No card bound)"));
        cardText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        cardText.setTypeface(Typeface.MONOSPACE);
        cardText.setTextColor(hasCard ? textColor : subColor);
        cardText.setSingleLine(true);
        cardText.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        cardBox.addView(cardText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Copy button for Card Key: ICON ONLY, NO TEXT
        FrameLayout copyCardBtn = new FrameLayout(act);
        copyCardBtn.setClickable(true);
        copyCardBtn.setFocusable(true);
        copyCardBtn.setContentDescription("Copy license key");
        copyCardBtn.setPadding(btnPad, btnPad, btnPad, btnPad);
        GradientDrawable copyBg2 = new GradientDrawable();
        copyBg2.setCornerRadius(dp(act, 6));
        copyBg2.setColor(dark ? 0x22FFFFFF : 0x12000000);
        copyCardBtn.setBackground(copyBg2);
        HubMaterialGlyphView copyCardIcon = new HubMaterialGlyphView(act, "ds_action_copy", hasCard ? accentCol : subColor);
        copyCardBtn.addView(copyCardIcon, new FrameLayout.LayoutParams(dp(act, 18), dp(act, 18), Gravity.CENTER));
        copyCardBtn.setOnClickListener(v -> {
            if (hasCard) {
                try {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("card_key", savedCard.trim()));
                    Toast.makeText(act, UiLanguage.text(act, "卡密已复制到剪贴板", "License key copied to clipboard"), Toast.LENGTH_SHORT).show();
                } catch (Throwable t) {
                    Toast.makeText(act, "复制失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(act, UiLanguage.text(act, "当前未绑定卡密", "No license key saved"), Toast.LENGTH_SHORT).show();
            }
        });
        cardBox.addView(copyCardBtn, new LinearLayout.LayoutParams(dp(act, 34), dp(act, 34)));

        LinearLayout.LayoutParams cardBoxLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardBoxLp.topMargin = dp(act, 4);
        cardBoxLp.bottomMargin = dp(act, 14);
        root.addView(cardBox, cardBoxLp);

        // 3. Hardware & License status summary
        TextView hwLabel = new TextView(act);
        hwLabel.setText(UiLanguage.text(act, "设备与授权状态：", "Device & License Status:"));
        hwLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hwLabel.setTypeface(Typeface.DEFAULT_BOLD);
        hwLabel.setTextColor(textColor);
        root.addView(hwLabel);

        TextView hwInfo = new TextView(act);
        StringBuilder hwSb = new StringBuilder();
        hwSb.append("• ").append(UiLanguage.text(act, "硬件机型：", "Hardware: ")).append(android.os.Build.MANUFACTURER).append(' ').append(android.os.Build.MODEL)
            .append(" (Android ").append(android.os.Build.VERSION.RELEASE).append(", API ").append(android.os.Build.VERSION.SDK_INT).append(")\n");
        boolean licenseValid = CloudPromptClient.hasValidLicense(act);
        hwSb.append("• ").append(UiLanguage.text(act, "授权状态：", "Status: "))
            .append(licenseValid ? UiLanguage.text(act, "已认证激活", "Activated")
                    : (hasCard ? UiLanguage.text(act, "已保存卡密（待联网验证）", "Card saved (pending verification)")
                    : UiLanguage.text(act, "未激活", "Not activated")));
        hwInfo.setText(hwSb.toString());
        hwInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hwInfo.setTextColor(subColor);
        hwInfo.setPadding(0, dp(act, 4), 0, dp(act, 18));
        root.addView(hwInfo);

        // Close button
        TextView closeBtn = new TextView(act);
        closeBtn.setText(UiLanguage.text(act, "关闭", "Close"));
        closeBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        closeBtn.setTypeface(Typeface.DEFAULT_BOLD);
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setGravity(Gravity.CENTER);
        GradientDrawable closeBg = new GradientDrawable();
        closeBg.setColor(accentCol);
        closeBg.setCornerRadius(dp(act, 8));
        closeBtn.setBackground(closeBg);
        closeBtn.setPadding(0, dp(act, 10), 0, dp(act, 10));
        closeBtn.setClickable(true);
        closeBtn.setFocusable(true);
        closeBtn.setOnClickListener(v -> dialog.dismiss());
        root.addView(closeBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout container = new FrameLayout(act);
        container.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int screenW = act.getResources().getDisplayMetrics().widthPixels;
        int cardWidth = Math.min((int) (screenW * 0.90f), dp(act, 380));
        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        container.addView(root, cardLp);

        dialog.setContentView(container);
        Window w = dialog.getWindow();
        if (w != null) {
            w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.6f);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
    }

    static String cloudError(Throwable error) {
        String message = error == null ? null : error.getMessage();
        if (error instanceof java.net.UnknownHostException) return "无法解析云端地址，请检查网络或 DNS";
        if (error instanceof javax.net.ssl.SSLException) return "云端安全连接失败，请检查系统时间和网络证书";
        if (error instanceof java.net.SocketTimeoutException) return "云端连接超时，请稍后重试";
        if (error instanceof java.io.IOException) return "网络连接失败，请检查网络后重试";
        if (error instanceof SecurityException) return "云端授权校验失败，请重新打开社区完成授权";
        if (message != null && (message.contains("invalidated") || message.contains("KeyPermanentlyInvalidatedException"))) {
            return "设备安全凭据已自动刷新，请再次点击“激活并注入”";
        }
        if (message == null || message.trim().length() == 0
                || message.startsWith("java.") || message.startsWith("javax."))
            return "云端请求失败，请稍后重试";
        return message;
    }

    private static String categoryTitle(int category) {
        switch (category) {
            case CATEGORY_CHAT: return "聊天";
            case CATEGORY_ACCOUNT: return "账号与隐私";
            case CATEGORY_APPEARANCE: return "界面美化";
            case CATEGORY_DEBUG: return "调试";
            case CATEGORY_CLOUD: return "社区";
            default: return "工程";
        }
    }

    static View simpleSwitchRow(final Activity act, String title, String desc,
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
        flashSearchHighlight(row, title);
        return row;
    }

    /** Blue blink used to point the user at a feature located from the search box. */
    private static void flashSearchHighlight(final View row, final String title) {
        String wanted = pendingSearchHighlight;
        if (wanted == null || !wanted.equals(title)) return;
        pendingSearchHighlight = null;
        row.post(new Runnable() {
            @Override public void run() {
                final android.graphics.drawable.Drawable original = row.getBackground();
                row.setBackgroundColor(0x554D6BFE);
                row.animate().alpha(0.3f).setDuration(160).withEndAction(new Runnable() {
                    @Override public void run() {
                        row.animate().alpha(1f).setDuration(160).withEndAction(new Runnable() {
                            @Override public void run() {
                                row.animate().alpha(0.3f).setDuration(160).withEndAction(new Runnable() {
                                    @Override public void run() {
                                        row.animate().alpha(1f).setDuration(160).withEndAction(
                                                new Runnable() {
                                            @Override public void run() {
                                                row.setBackground(original);
                                            }
                                        }).start();
                                    }
                                }).start();
                            }
                        }).start();
                    }
                }).start();
            }
        });
    }

    private static String chatProxyDescription(Activity act) {
        if (z17.isEnabled()) {
            return "已开启：" + z17.format() + " · " + z17.model()
                    + "\n原生聊天将转发到自配服务商。";
        }
        return "把原生聊天框的消息转发到自配的 OpenAI/Anthropic 服务商，不走 DeepSeek 原生链路。";
    }

    /** Center popup for the chat-proxy config (API key / format / URL / model). */
    private static void showz17Dialog(final Activity act, final TextView detailOut) {
        boolean dark = isDark(act);
        int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        int subColor = dark ? 0xFFAAAAAF : 0xFF70757D;
        int fieldBg = dark ? 0xFF2A2A2E : 0xFFF0F2F7;

        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(act, 20), dp(act, 12), dp(act, 20), dp(act, 4));

        final android.widget.EditText key = field(act, "API Key",
                z17.apiKey(), textColor, fieldBg);
        final android.widget.EditText url = field(act, "Base URL（例如 https://api.openai.com）",
                z17.baseUrl(), textColor, fieldBg);
        final android.widget.EditText model = field(act, "模型",
                z17.model(), textColor, fieldBg);
        panel.addView(key);
        panel.addView(url);
        panel.addView(model);

        LinearLayout formatRow = new LinearLayout(act);
        formatRow.setOrientation(LinearLayout.HORIZONTAL);
        formatRow.setGravity(Gravity.CENTER_VERTICAL);
        formatRow.setPadding(0, dp(act, 6), 0, 0);
        final String[] format = {z17.format()};
        final TextView openai = formatChip(act, "OpenAI", textColor, fieldBg);
        final TextView anthropic = formatChip(act, "Anthropic", textColor, fieldBg);
        formatRow.addView(openai);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        aLp.rightMargin = dp(act, 8);
        formatRow.addView(anthropic, aLp);
        panel.addView(formatRow);
        final Runnable styleFormat = new Runnable() {
            @Override public void run() {
                openai.setTextColor("openai".equals(format[0]) ? 0xFFFFFFFF : textColor);
                anthropic.setTextColor("anthropic".equals(format[0]) ? 0xFFFFFFFF : textColor);
                openai.setBackgroundColor("openai".equals(format[0]) ? BRAND : fieldBg);
                anthropic.setBackgroundColor("anthropic".equals(format[0]) ? BRAND : fieldBg);
            }
        };
        openai.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { format[0] = "openai"; styleFormat.run(); }
        });
        anthropic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { format[0] = "anthropic"; styleFormat.run(); }
        });
        styleFormat.run();

        final TextView feedback = new TextView(act);
        feedback.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        feedback.setTextColor(subColor);
        feedback.setPadding(0, dp(act, 8), 0, 0);
        panel.addView(feedback);

        final android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(act)
                .setTitle("对话代理配置")
                .setView(panel)
                .setPositiveButton("保存", null)
                .setNeutralButton("获取模型列表", null)
                .setNegativeButton("取消", null)
                .create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override public void onShow(android.content.DialogInterface di) {
                android.widget.Button save = dialog.getButton(
                        android.content.DialogInterface.BUTTON_POSITIVE);
                save.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        String k = key.getText().toString().trim();
                        String u = url.getText().toString().trim();
                        String m = model.getText().toString().trim();
                        if (k.length() == 0 || u.length() == 0 || m.length() == 0) {
                            feedback.setText("API Key、URL 和模型都不能为空");
                            return;
                        }
                        z17.save(k, format[0], u, m);
                        if (detailOut != null) detailOut.setText(chatProxyDescription(act));
                        dialog.dismiss();
                    }
                });
                android.widget.Button fetch = dialog.getButton(
                        android.content.DialogInterface.BUTTON_NEUTRAL);
                fetch.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        feedback.setText("正在获取模型列表…");
                        new Thread(new Runnable() {
                            @Override public void run() {
                                try {
                                    final java.util.List<String> models = z17.fetchModels(
                                            key.getText().toString().trim(), format[0],
                                            url.getText().toString().trim());
                                    act.runOnUiThread(new Runnable() {
                                        @Override public void run() {
                                            if (models.isEmpty()) {
                                                feedback.setText("没有获取到模型");
                                            } else {
                                                String[] arr = models.toArray(new String[0]);
                                                new android.app.AlertDialog.Builder(act)
                                                        .setTitle("选择模型")
                                                        .setItems(arr,
                                                                new android.content.DialogInterface
                                                                        .OnClickListener() {
                                                                    @Override public void onClick(
                                                                            android.content.DialogInterface d,
                                                                            int which) {
                                                                        model.setText(arr[which]);
                                                                    }
                                                                })
                                                        .setNegativeButton("取消", null)
                                                        .show();
                                                feedback.setText("已获取 " + models.size() + " 个模型");
                                            }
                                        }
                                    });
                                } catch (Throwable e) {
                                    final String msg = e.getMessage() == null
                                            ? e.getClass().getSimpleName() : e.getMessage();
                                    act.runOnUiThread(new Runnable() {
                                        @Override public void run() {
                                            feedback.setText("获取失败：" + msg);
                                        }
                                    });
                                }
                            }
                        }, "Deekseep-z17-Fetch").start();
                    }
                });
            }
        });
        dialog.show();
    }

    private static android.widget.EditText field(Activity act, String hint, String value,
                                                 int textColor, int bg) {
        android.widget.EditText e = new android.widget.EditText(act);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        e.setTextColor(textColor);
        e.setHintTextColor(0xFF888888);
        e.setPadding(dp(act, 12), dp(act, 8), dp(act, 12), dp(act, 8));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(act, 6));
        e.setBackground(d);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(act, 8);
        e.setLayoutParams(lp);
        return e;
    }

    private static TextView formatChip(Activity act, String label, int textColor, int bg) {
        TextView t = new TextView(act);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setPadding(dp(act, 10), dp(act, 7), dp(act, 10), dp(act, 7));
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(act, 6));
        t.setBackground(d);
        t.setClickable(true);
        return t;
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

    private static void showNoCensorSettings(final Activity act) {
        if (act == null || act.isFinishing()) return;
        final boolean dark = isDark(act);
        final int surface = dark ? 0xFF27282B : 0xFFFFFFFF;
        final int inset = dark ? 0xFF202124 : 0xFFF4F5F7;
        final int text = dark ? 0xFFF1F2F4 : 0xFF202124;
        final int sub = dark ? 0xFFAAADB3 : 0xFF747982;
        final int line = dark ? 0xFF3A3C40 : 0xFFE4E6EA;
        final Dialog dialog = new Dialog(
                act, android.R.style.Theme_Translucent_NoTitleBar);
        FrameLayout backdrop = new FrameLayout(act);
        backdrop.setBackgroundColor(0x52000000);
        backdrop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dialog.dismiss(); }
        });

        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(act, 18), dp(act, 17), dp(act, 18), dp(act, 16));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(surface);
        panelBackground.setCornerRadius(dp(act, 18));
        panelBackground.setStroke(dp(act, 1), line);
        panel.setBackground(panelBackground);
        panel.setClickable(true);

        LinearLayout heading = new LinearLayout(act);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(act);
        title.setText(UiLanguage.text(act, "防撤回设置", "Anti-recall settings"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(text);
        heading.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView done = new TextView(act);
        done.setText(UiLanguage.text(act, "完成", "Done"));
        done.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        done.setTypeface(Typeface.DEFAULT_BOLD);
        done.setTextColor(BRAND);
        done.setGravity(Gravity.CENTER);
        done.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { dialog.dismiss(); }
        });
        heading.addView(done, new LinearLayout.LayoutParams(dp(act, 56), dp(act, 40)));
        panel.addView(heading);

        TextView explanation = new TextView(act);
        explanation.setText(UiLanguage.text(act,
                "控制被撤回回答在后续对话中的处理方式。设置只影响下一次正常发送，界面不会显示隐藏内容。",
                "Control how recalled answers participate in later turns. Hidden context is never shown in chat."));
        explanation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        explanation.setTextColor(sub);
        explanation.setLineSpacing(dp(act, 2), 1f);
        LinearLayout.LayoutParams explanationParams = new LinearLayout.LayoutParams(-1, -2);
        explanationParams.topMargin = dp(act, 5);
        explanationParams.bottomMargin = dp(act, 14);
        panel.addView(explanation, explanationParams);

        LinearLayout row = new LinearLayout(act);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(act, 14), dp(act, 12), dp(act, 10), dp(act, 12));
        GradientDrawable rowBackground = new GradientDrawable();
        rowBackground.setColor(inset);
        rowBackground.setCornerRadius(dp(act, 12));
        row.setBackground(rowBackground);
        LinearLayout labels = new LinearLayout(act);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(act);
        label.setText(UiLanguage.text(act, "保留上下文", "Retain context"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setTextColor(text);
        labels.addView(label);
        TextView detail = new TextView(act);
        detail.setText(UiLanguage.text(act,
                "下一条消息会在后台带上最近一次被撤回答复",
                "Attach the latest recalled answer invisibly to the next message"));
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        detail.setTextColor(sub);
        detail.setPadding(0, dp(act, 3), 0, 0);
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        final Switch keep = new HubInsetSwitch(act);
        keep.setChecked(Main.isNoCensorContextRetentionEnabled());
        int[][] states = {{android.R.attr.state_checked},
                {-android.R.attr.state_checked}};
        keep.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        keep.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        keep.setBackground(null);
        keep.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(
                    CompoundButton button, boolean checked) {
                Main.setNoCensorContextRetentionEnabled(checked);
            }
        });
        row.addView(keep);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { keep.toggle(); }
        });
        panel.addView(row);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                Math.min(dp(act, 344),
                        act.getResources().getDisplayMetrics().widthPixels - dp(act, 32)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        backdrop.addView(panel, panelParams);
        dialog.setContentView(backdrop);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    Color.TRANSPARENT));
        }
        dialog.show();
        if (window != null) window.setLayout(-1, -1);
        panel.setAlpha(0f);
        panel.setTranslationY(dp(act, 16));
        panel.animate().alpha(1f).translationY(0f).setDuration(180).start();
    }

    static View plainGearControl(Activity act, int iconColor,
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
            Object explicitOwner = child.getTag();
            int owner = explicitOwner instanceof Integer
                    ? (Integer) explicitOwner : categoryForText(text);
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
        if (text.contains("聊天外观") || text.contains("外观设置") || text.contains("主题色")
                || text.contains("自定义 DeepSeek 头像")
                || text.contains("鲸鱼图标动效")
                || text.contains("深海文字波纹")
                || text.contains("主页欢迎语")
                || text.contains("使用旧版入口") || text.contains("原生设置入口")) return CATEGORY_APPEARANCE;
        if (text.contains("记录服务器返回") || text.contains("Hook 日志") || text.contains("记录崩溃")
                || text.contains("崩溃测试") || text.contains("诊断")
                || text.contains("性能统计") || text.contains("事件追踪")
                || text.contains("发送自定义请求") || text.contains("导出日志")
                || text.contains("导出配置") || text.contains("导入配置")
                || text.contains("备份全部应用数据") || text.contains("恢复全部应用数据")
                || text.contains("清理高频")) {
            return CATEGORY_DEBUG;
        }
        if (text.contains("Google") || text.contains("邮箱登录") || text.contains("微信与手机号")
                || text.contains("多账号") || text.contains("安全审查")
                || text.contains("本地禁言") || text.contains("伪禁言")
                || text.contains("数据用于优化体验") || text.contains("绕过风控")
                || text.contains("防止封号") || text.contains("海外安全")) {
            return CATEGORY_ACCOUNT;
        }
        if (text.contains("导入提示词") || text.contains("还原设置")
                || text.contains("已开启其他功能，请先关闭后再使用")
                || text.contains("系统提示词") || text.contains("聊天记录多选")
                || text.contains("编辑聊天记录") || text.contains("消息时间与详情")
                || text.contains("自动继续生成")
                || text.contains("上下文压缩")
                || text.contains("导出会话")
                || text.contains("导入聊天记录")
                || text.contains("全局搜索") || text.contains("会话数据统计")
                || text.contains("专家模式") || text.contains("AI 心跳")
                || text.contains("主动消息")) {
            return CATEGORY_CHAT;
        }
        return CATEGORY_ENGINEERING;
    }

    private static int javaPluginPlacementCategory(String placement) {
        if ("module.chat".equals(placement)) return CATEGORY_CHAT;
        if ("module.account".equals(placement)) return CATEGORY_ACCOUNT;
        if ("module.appearance".equals(placement)) return CATEGORY_APPEARANCE;
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

    private static void showPromptSplitPopup(final Activity act, final View anchor,
            boolean dark, int textColor, int subColor) {
        if (act == null || anchor == null
                || (!HostCompat.isV236() && !HostCompat.isV241())) return;
        final LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(act, 14), dp(act, 10), dp(act, 14), dp(act, 10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF29292D : 0xFFFFFFFF);
        background.setCornerRadius(dp(act, 14));
        background.setStroke(dp(act, 1), dark ? 0xFF414147 : 0xFFE3E5EA);
        panel.setBackground(background);
        panel.setElevation(dp(act, 10));

        LinearLayout splitRow = new LinearLayout(act);
        splitRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView splitLabel = new TextView(act);
        splitLabel.setText("分轮注入");
        splitLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        splitLabel.setTextColor(textColor);
        splitRow.addView(splitLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch splitSwitch = new HubInsetSwitch(act);
        splitSwitch.setChecked(Main.isPromptSplitInjectionEnabled());
        int[][] states = {{android.R.attr.state_checked}, {-android.R.attr.state_checked}};
        splitSwitch.setThumbTintList(new android.content.res.ColorStateList(states,
                new int[]{BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        splitSwitch.setTrackTintList(new android.content.res.ColorStateList(states,
                new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
        splitSwitch.setBackground(null);
        splitRow.addView(splitSwitch);
        panel.addView(splitRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 44)));

        final LinearLayout pickerSection = new LinearLayout(act);
        pickerSection.setOrientation(LinearLayout.VERTICAL);
        TextView intervalLabel = new TextView(act);
        intervalLabel.setText("隔几轮注入");
        intervalLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        intervalLabel.setTextColor(subColor);
        intervalLabel.setGravity(Gravity.CENTER);
        pickerSection.addView(intervalLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final android.widget.NumberPicker picker = new android.widget.NumberPicker(act);
        picker.setMinValue(1);
        picker.setMaxValue(30);
        picker.setWrapSelectorWheel(false);
        picker.setValue(Main.configuredPromptInjectionInterval());
        picker.setDescendantFocusability(android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        picker.setOnValueChangedListener((numberPicker, oldValue, newValue) ->
                Main.setPromptInjectionInterval(newValue));
        picker.setOnLongClickListener(view -> {
            showPromptIntervalInput(act, picker, null);
            return true;
        });
        LinearLayout pickerHolder = new LinearLayout(act);
        pickerHolder.setGravity(Gravity.CENTER);
        pickerHolder.addView(picker, new LinearLayout.LayoutParams(
                dp(act, 88), dp(act, 104)));
        pickerSection.addView(pickerHolder);
        pickerSection.setVisibility(splitSwitch.isChecked() ? View.VISIBLE : View.GONE);
        panel.addView(pickerSection);
        splitSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (!Main.setPromptSplitInjectionEnabled(checked)) {
                button.setChecked(!checked);
                return;
            }
            pickerSection.setVisibility(checked ? View.VISIBLE : View.GONE);
            if (checked) {
                pickerSection.setAlpha(0f);
                pickerSection.setTranslationY(-dp(act, 4));
                pickerSection.animate().alpha(1f).translationY(0f).setDuration(140L).start();
            }
        });

        final android.widget.PopupWindow popup = new android.widget.PopupWindow(panel,
                dp(act, 246), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(act, 10));
        popup.setAnimationStyle(android.R.style.Animation_Dialog);
        popup.showAsDropDown(anchor, -dp(act, 148), -dp(act, 4));
    }

    private static void showPromptIntervalInput(final Activity act,
            final android.widget.NumberPicker picker, final TextView suffix) {
        if (act == null || (!HostCompat.isV236() && !HostCompat.isV241())) return;
        final EditText input = new EditText(act);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(Main.configuredPromptInjectionInterval()));
        input.setSelectAllOnFocus(true);
        int inset = dp(act, 20);
        FrameLayout frame = new FrameLayout(act);
        frame.setPadding(inset, 0, inset, 0);
        frame.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new android.app.AlertDialog.Builder(act)
                .setTitle("自定义间隔轮数（1–30）")
                .setView(frame)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (dialog, which) -> {
                    int value;
                    try { value = Integer.parseInt(input.getText().toString().trim()); }
                    catch (Throwable ignored) { value = 0; }
                    if (value < 1 || value > 30) {
                        Toast.makeText(act, "请输入 1 到 30", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean ok = Main.setPromptInjectionInterval(value);
                    if (ok && picker != null) picker.setValue(value);
                    if (ok && suffix != null) suffix.setText("轮");
                    Toast.makeText(act, ok ? "间隔轮数已保存" : "保存失败",
                            Toast.LENGTH_SHORT).show();
                })
                .show();
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
        flashSearchHighlight(row, title);
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
        if ("ds_category_cloud".equals(name)) return "ic_cloud_outline_20";
        if ("ds_category_help".equals(name)) return "ic_help_outline";
        if ("ds_project_license".equals(name)) return "ic_info_outline";
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

    private static void showOpenSourceDialog(final Activity act) {
        String license = readBundledText(
                "META-INF/com.dsmod.probe.project/gpl_3_0.txt", 96 * 1024);
        if (license.length() == 0) license = "GNU General Public License version 3";
        final TextView body = new TextView(act);
        body.setText(license);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        body.setTextColor(isDark(act) ? 0xFFECECEC : 0xFF1A1A1A);
        body.setPadding(dp(act, 20), dp(act, 8), dp(act, 20), dp(act, 8));
        android.widget.ScrollView scroll = new android.widget.ScrollView(act);
        scroll.addView(body, new android.widget.ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new android.app.AlertDialog.Builder(act)
                .setTitle("GNU GPL-3.0-only")
                .setView(scroll)
                .setNeutralButton("GitHub", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dialog, int which) {
                        try {
                            act.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(REPOSITORY)));
                        } catch (Throwable error) {
                            Toast.makeText(act, REPOSITORY, Toast.LENGTH_LONG).show();
                        }
                    }
                })
                .setPositiveButton("关闭", null)
                .show();
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

    private static void openQqGroup(Activity act) {
        Uri group = Uri.parse(QQ_GROUP_URL);
        try {
            Intent qq = new Intent(Intent.ACTION_VIEW, group);
            qq.setPackage("com.tencent.mobileqq");
            act.startActivity(qq);
            return;
        } catch (Throwable ignored) {
            // QQ is unavailable or does not accept this universal-share link; use the web route.
        }
        try {
            act.startActivity(new Intent(Intent.ACTION_VIEW, group));
        } catch (Throwable error) {
            Toast.makeText(act, "无法打开 QQ 群链接", Toast.LENGTH_SHORT).show();
        }
    }

    private static void showCommunityChooser(final Activity act) {
        final boolean dark = isDark(act);
        final int textColor = dark ? 0xFFF1F1F1 : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAB0 : 0xFF74777D;
        final int cardColor = dark ? 0xFF29292C : 0xFFFFFFFF;
        final int dividerColor = dark ? 0xFF414145 : 0xFFE8E8EA;
        final android.app.Dialog dialog = new android.app.Dialog(act);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(act, 18), dp(act, 16), dp(act, 18), dp(act, 12));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(cardColor);
        panelBackground.setCornerRadius(dp(act, 20));
        panel.setBackground(panelBackground);
        TextView title = labelText(act,
                UiLanguage.text(act, "加入交流群", "Join the community"),
                18, textColor, true);
        title.setPadding(dp(act, 4), 0, dp(act, 4), dp(act, 10));
        panel.addView(title);
        View qq = communityChoiceRow(act, "QQ", "Deekseep 模块交流群",
                "ds_project_community", textColor, subColor);
        panel.addView(qq);
        panel.addView(makeDivider(act, dividerColor));
        View telegram = communityChoiceRow(act, "Telegram", "@Deekseepapp",
                BuildInfo.PROTECTED_BUILD && (HostCompat.isV236() || HostCompat.isV241())
                        ? "ds_project_telegram" : "ds_project_send",
                textColor, subColor);
        panel.addView(telegram);
        qq.setOnClickListener(v -> { dialog.dismiss(); openQqGroup(act); });
        telegram.setOnClickListener(v -> { dialog.dismiss(); openTelegramGroup(act); });
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(0x00000000));
            window.setLayout(Math.min(dp(act, 400),
                            act.getResources().getDisplayMetrics().widthPixels - dp(act, 32)),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static View communityChoiceRow(Activity act, String title, String detail,
                                           String iconName, int textColor, int subColor) {
        return toolActionRow(act, iconName, title, detail, textColor, subColor, null);
    }

    private static void openTelegramGroup(Activity act) {
        try {
            Intent nativeIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("tg://resolve?domain=Deekseepapp"));
            act.startActivity(nativeIntent);
            return;
        } catch (Throwable ignored) {}
        try {
            act.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(TELEGRAM_GROUP_URL)));
        } catch (Throwable error) {
            Toast.makeText(act, "无法打开 Telegram 群链接", Toast.LENGTH_SHORT).show();
        }
    }

    private static String readBundledText(String path, int limit) {
        java.io.InputStream input = null;
        try {
            ClassLoader loader = DeekseepUi.class.getClassLoader();
            input = loader == null ? null : loader.getResourceAsStream(path);
            if (input == null) return "";
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) continue;
                if (output.size() + count > limit) break;
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return "";
        } finally {
            if (input != null) try { input.close(); } catch (Throwable ignored) {}
        }
    }

    private static void showExperimentalHelpPage(final Activity act) {
        final String[][] items = {
            {"【功能】聊天背景、贴纸与气泡", "导入图片并调整取景、缩放、透明度和界面。"},
            {"【功能】空间动效", "控制背景视差、层次和动态强度。"},
            {"【功能】专家模式上传图片文件（无用）", "热更新已移除专家模式；旧版视觉中继配置默认暂停。"},
            {"【功能】本地 API 服务", "提供本地兼容接口、SSE 和 Agent 工具结果。"},
            {"【功能】AI 心跳", "按当前对话设置周期或一次性心跳提醒。"},
            {"【功能】问答工具", "模型提出选项，你选择或输入后继续。"},
            {"【功能】Agent 工具与权限", "管理工具开关、执行权限和 Shizuku/Root 模式。"},
            {"【问题】背景图或贴纸没有显示？", "检查聊天外观开关和当前界面绑定，必要时重启 DeepSeek。"},
            {"【问题】本地 API 连接失败？", "检查服务开关、密钥、端口和后台运行权限。"},
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
                + " " + BuildInfo.BUILD_EDITION
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

        card.addView(makeDivider(act, divider));

        LinearLayout letterRow = new LinearLayout(act);
        letterRow.setOrientation(LinearLayout.HORIZONTAL);
        letterRow.setGravity(Gravity.CENTER_VERTICAL);
        letterRow.setPadding(dp(act, 16), dp(act, 13), dp(act, 16), dp(act, 13));
        LinearLayout letterLabels = new LinearLayout(act);
        letterLabels.setOrientation(LinearLayout.VERTICAL);
        letterLabels.addView(labelText(act, "留给使用者的信", 15, text, true));
        letterLabels.addView(labelText(act,
                "关于开源、滥用与后续维护的个人说明",
                12, sub, false));
        letterRow.addView(letterLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView letterChevron = labelText(act, "›", 24, sub, false);
        letterRow.addView(letterChevron);
        letterRow.setClickable(true);
        letterRow.setFocusable(true);
        letterRow.setBackground(controlBackground(
                0x00000000, dark ? 0x24FFFFFF : 0x14000000, 0f));
        letterRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                showLetterToUsersDialog(act, true);
            }
        });
        card.addView(letterRow);

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
            {"【功能】本地 API", "提供兼容接口和本地 Agent 工具调用。"},
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
        message.setAutoLinkMask(android.text.util.Linkify.WEB_URLS);
        message.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
        message.setLinkTextColor(BRAND);
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

    public static final String LETTER_TO_USERS_TITLE = "致使用者的信";
    public static final String LETTER_TO_USERS_CONTENT =
            "各位使用者，见字如面：\n\n"
            + "写下这封信时，我的内心十分复杂，甚至感到有些无所适从与身心俱疲。\n\n"
            + "我不知道开源圈什么时候变成这样了……\n\n"
            + "最初做 Deekseep 这个项目，初衷其实非常纯粹：只是出于对技术的纯粹探索，以及对自由、透明与开源共享精神的坚持。在我的设想里，我一直期待能够营造一个纯粹的技术社区氛围：大家聚在一起，不谈商业利益，不设门槛，纯粹是因为对 Android 底层机制的好奇、对逆向工程与大模型交互的探索，或者单纯只是为了让手头的工具更趁手、更自由、更好用。我希望每一个普通使用者都能免费、没有顾虑地体验到技术的乐趣，遇到问题一起交流，探索出更好的玩法。从写下第一行代码至今，我从未以此谋求过任何商业利益，也始终毫无保留地将代码和实现思路呈现在公开仓库中。\n\n"
            + "在很长一段时间里，我也确实感受到了这种善意。看到很多朋友在日常生活中因为这个小工具获得了便利，看到有人在交流群里认真探讨技术细节与使用心得，这些都是我开发的动力。\n\n"
            + "然而，随着项目被更多人知道，现实中的实际使用情况却逐渐偏离了最初的设想，甚至走向了我最不愿看到的极端。\n\n"
            + "在此，我想向大家坦诚说明一些客观存在的事实：\n"
            + "在开源社区中，虽然有许多真诚友善的同行者，但确实有部分项目与个人（数量虽然不是特别多，但影响极其恶劣），在直接拿走、修改了本项目的源码后，抹去原作者署名，私自加上授权验证与卡密系统，堂而皇之对外售卖牟利，把别人无偿分享的心血变成自己空手套白的摇钱树；更有甚者，在其他平台上大言不惭地抹掉一切出处，公然声称自己才是原创作者；还有一些人无节制地滥用接口与功能用于灰黑产批量薅取，把本该服务于正常使用者的资源挤占消耗，最终导致上游风控收紧，伤害了所有真正热爱这个项目的普通用户。\n\n"
            + "还有一件事，我甚至都不知道该怎么去说，心里除了无法理解，就只有深深的厌恶。有人拿这个软件去研究破甲和越狱，破甲确实能解放模型的表达能力，但绝对不是让人拿去生成涉及未成年人的性内容与伦理不容的违禁东西。我做这个软件只是为了技术探索与交流，我绝不希望、也绝无法容忍有任何未成年人因为我的软件而受到哪怕一丝一毫的伤害。这种突破底线的滥用，已经让原本纯粹的技术变了质。\n\n"
            + "当开源共享的善意被视作免费收割的原材料，当无保留的信任换来的是肆无忌惮的抄袭、倒卖、冒名顶替与毫无底线的滥用，我感到深深的心寒与疲惫。技术本应向善，自由也不该成为没有底线牟利的遮羞布。\n\n"
            + "经过长期的权衡与思考，我做出了以下决定，向大家郑重说明：\n\n"
            + "一、关于后续开源与公开仓库维护：\n"
            + "此版本（v1.7.5）可能会是最后一个公开发布的开源版本，后续公开仓库将暂时无限期停更。我需要停下来，远离这些无休止的纷扰与消耗，回归平静的生活与纯粹的技术探索。\n\n"
            + "二、关于问题反馈与后续修复：\n"
            + "如果大家在此版本的使用中遇到问题，依然可以尝试向我去反馈问题。我有时间可能会去看，但不一定会修。毕竟一个人的精力和业余时间终究是有限的，当前的维护状态也已无法支撑高强度的持续跟进，还望大家能够理解与体谅。\n\n"
            + "三、关于闭源版本的说明：\n"
            + "闭源版本由于近期出现了大量被二次加壳倒卖、恶意滥用与黑产利用的情况，为防止局势进一步恶化，将彻底不再于任何公共仓库或开放平台分发，后续仅在 Telegram 交流群中保留小范围同步。\n\n"
            + "四、项目仓库与联系方式：\n"
            + "- 开源仓库：https://github.com/lllucccian/Deekseep\n"
            + "- 开发者 QQ：1106465300\n"
            + "- Telegram 交流群：https://t.me/Deekseepapp\n\n"
            + "感谢一路走来所有理解我、支持我、真正尊重开源协议与原创劳动的每一位朋友。能与你们在代码的世界里相遇，是这段旅程中最珍贵的部分。\n\n"
            + "山高水长，江湖路远，后会有期。\n\n"
            + "—— 开发者 @lllucccian";

    public static void showLetterToUsersDialog(final Activity act, final boolean force) {
        if (act == null || act.isFinishing()) return;
        if (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed()) return;
        final android.content.SharedPreferences sp =
                act.getSharedPreferences("deekseep_open_prefs", Context.MODE_PRIVATE);
        if (!force && sp.getBoolean("dont_show_letter_v175", false)) {
            return;
        }
        act.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (act.isFinishing() || (android.os.Build.VERSION.SDK_INT >= 17 && act.isDestroyed())) return;
                showCustomConfirm(act,
                        LETTER_TO_USERS_TITLE,
                        LETTER_TO_USERS_CONTENT,
                        "不再显示",
                        "我知道了",
                        true,
                        new Runnable() {
                            @Override public void run() {
                                sp.edit().putBoolean("dont_show_letter_v175", true).apply();
                            }
                        },
                        null);
            }
        });
    }

    static int statusBarHeight(Context c) {
        int id = c.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) return c.getResources().getDimensionPixelSize(id);
        return dp(c, 28);
    }

    private DeekseepUi() {}
}
