package com.dsmod.probe;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedHashMap;
import java.util.Map;

/** Experimental Features → Agent settings page. */
final class AgentSettingsUi {
    private AgentSettingsUi() {}

    static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = DeekseepUi.isDark(activity);
        final int background = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFF0F0F0 : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF777B82;
        final int dividerColor = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(background);

        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(activity, 8), statusBarHeight(activity),
                dp(activity, 16), 0);
        bar.setBackgroundColor(barColor);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 56) + statusBarHeight(activity)));
        TextView back = text(activity, "\u2039", 28, textColor, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        back.setClickable(true);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                close(dialog, root);
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40)));
        TextView title = text(activity, "Agent", 18, textColor, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 8);
        bar.addView(title, titleParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 28));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final AgentToolConfig.Snapshot initial = AgentToolConfig.load();
        final LinearLayout mainCard = card(activity, cardColor);
        content.addView(mainCard);
        TextView note = text(activity,
                UiLanguage.text(activity,
                        "Agent 工具只作用于发起调用的当前对话。问答结果会作为可见消息发送；"
                                + "屏幕操作默认仅限 DeepSeek，连接 Root 或 Shizuku 后才可作用于系统前台界面。",
                        "Agent tools stay scoped to the chat that invoked them. Question answers "
                                + "are sent visibly. Screen actions are limited to DeepSeek until "
                                + "Root or Shizuku is connected."),
                13, subColor, false);
        note.setLineSpacing(dp(activity, 2), 1f);
        note.setPadding(dp(activity, 16), dp(activity, 15),
                dp(activity, 16), dp(activity, 14));
        mainCard.addView(note);
        mainCard.addView(divider(activity, dividerColor));

        final Switch master = switchView(activity, dark);
        master.setChecked(initial.enabled);
        mainCard.addView(switchRow(activity,
                UiLanguage.text(activity, "启用 Agent", "Enable Agent"),
                UiLanguage.text(activity,
                        "关闭后不会向模型提供本地工具，也不会执行工具控制块",
                        "When off, local tools are neither offered nor executed"),
                textColor, subColor, master));
        master.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    private boolean reverting;

                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        if (reverting) return;
                        if (AgentToolConfig.setEnabled(checked)) return;
                        reverting = true;
                        button.setChecked(!checked);
                        reverting = false;
                        Toast.makeText(activity, UiLanguage.text(activity,
                                "Agent 设置保存失败", "Could not save Agent settings"),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        sectionTitle(content, activity,
                UiLanguage.text(activity, "执行后端", "Execution backend"),
                textColor);
        final LinearLayout backendCard = card(activity, cardColor);
        content.addView(backendCard);
        final TextView backendStatus = text(activity, "", 12, subColor, false);
        backendStatus.setPadding(dp(activity, 16), dp(activity, 13),
                dp(activity, 16), dp(activity, 4));
        backendCard.addView(backendStatus);

        final LinkedHashMap<String, TextView> backendButtons = new LinkedHashMap<>();
        LinearLayout backendChoices = new LinearLayout(activity);
        backendChoices.setOrientation(LinearLayout.HORIZONTAL);
        backendChoices.setPadding(dp(activity, 12), dp(activity, 8),
                dp(activity, 12), dp(activity, 14));
        addBackendButton(activity, backendChoices, backendButtons,
                AgentToolConfig.BACKEND_IN_APP,
                UiLanguage.text(activity, "应用内", "In app"),
                textColor, dark);
        addBackendButton(activity, backendChoices, backendButtons,
                AgentToolConfig.BACKEND_ROOT, "Root", textColor, dark);
        addBackendButton(activity, backendChoices, backendButtons,
                AgentToolConfig.BACKEND_SHIZUKU, "Shizuku", textColor, dark);
        backendCard.addView(backendChoices);
        updateBackendButtons(activity, backendButtons,
                initial.backend, dark, textColor);
        backendStatus.setText(backendDescription(
                activity, initial.backend));
        for (final Map.Entry<String, TextView> entry : backendButtons.entrySet()) {
            entry.getValue().setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View ignored) {
                    final String requested = entry.getKey();
                    backendStatus.setText(UiLanguage.text(activity,
                            "正在检测连接\u2026", "Checking connection\u2026"));
                    AgentDeviceBridge.probe(
                            activity, requested,
                            new AgentDeviceBridge.StatusCallback() {
                                @Override public void onStatus(
                                        AgentDeviceBridge.Status status) {
                                    if (status.connected
                                            && AgentToolConfig.setBackend(requested)) {
                                        updateBackendButtons(
                                                activity, backendButtons,
                                                requested, dark, textColor);
                                        backendStatus.setText(status.detail);
                                    } else {
                                        backendStatus.setText(status.detail);
                                    }
                                }
                            });
                }
            });
        }

        sectionTitle(content, activity,
                UiLanguage.text(activity, "权限模式", "Permission mode"),
                textColor);
        final LinearLayout permissionCard = card(activity, cardColor);
        content.addView(permissionCard);
        LinearLayout permissionRow = new LinearLayout(activity);
        permissionRow.setOrientation(LinearLayout.HORIZONTAL);
        permissionRow.setGravity(Gravity.CENTER_VERTICAL);
        permissionRow.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 12), dp(activity, 14));
        LinearLayout permissionLabels = new LinearLayout(activity);
        permissionLabels.setOrientation(LinearLayout.VERTICAL);
        permissionLabels.addView(text(activity,
                UiLanguage.text(activity, "访问授权", "Access authorization"),
                16, textColor, true));
        final TextView permissionDescription = text(activity,
                permissionDescription(activity, initial.permission),
                12, subColor, false);
        permissionLabels.addView(permissionDescription);
        permissionRow.addView(permissionLabels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView permissionButton = text(activity,
                permissionLabel(activity, initial.permission) + "  \u25be",
                14, textColor, true);
        permissionButton.setGravity(Gravity.CENTER);
        permissionButton.setPadding(dp(activity, 12), dp(activity, 9),
                dp(activity, 12), dp(activity, 9));
        permissionButton.setBackground(rounded(
                dark ? 0xFF38383C : 0xFFF0F1F4, dp(activity, 10)));
        permissionButton.setClickable(true);
        permissionRow.addView(permissionButton);
        permissionCard.addView(permissionRow);
        permissionButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View anchor) {
                showPermissionPopup(activity, anchor, dark,
                        textColor, subColor,
                        permissionButton, permissionDescription);
            }
        });

        sectionTitle(content, activity,
                UiLanguage.text(activity, "工具", "Tools"), textColor);
        LinearLayout toolsCard = card(activity, cardColor);
        content.addView(toolsCard);
        boolean chinese = UiLanguage.isChinese(activity);
        int index = 0;
        for (final String tool : AgentToolConfig.tools()) {
            if (index++ > 0) toolsCard.addView(
                    divider(activity, dividerColor));
            final Switch toggle = switchView(activity, dark);
            toggle.setChecked(initial.enabledTools.contains(tool));
            toolsCard.addView(switchRow(activity,
                    AgentToolConfig.displayName(tool, chinese),
                    AgentToolConfig.description(tool, chinese),
                    textColor, subColor, toggle));
            toggle.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        private boolean reverting;

                        @Override public void onCheckedChanged(
                                CompoundButton button, boolean checked) {
                            if (reverting) return;
                            if (AgentToolConfig.setToolEnabled(tool, checked)) return;
                            reverting = true;
                            button.setChecked(!checked);
                            reverting = false;
                            Toast.makeText(activity, UiLanguage.text(activity,
                                    "工具设置保存失败",
                                    "Could not save tool settings"),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        TextView footer = text(activity,
                UiLanguage.text(activity,
                        "当前为 1.7.4 实验分支。高权限后端只接受固定的点击、滑动、"
                                + "返回和截图动作，不向模型开放任意 shell。",
                        "This is the experimental 1.7.4 branch. Privileged backends only accept "
                                + "fixed tap, swipe, back, and capture actions; arbitrary shell "
                                + "is never exposed to the model."),
                12, subColor, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(activity, 14), dp(activity, 22),
                dp(activity, 14), dp(activity, 6));
        content.addView(footer);

        UiLanguage.localizeTree(activity, root);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(background));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        dialog.show();
        root.setTranslationX(activity.getResources()
                .getDisplayMetrics().widthPixels);
        root.animate().translationX(0f).setDuration(230L).start();
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(
                    android.content.DialogInterface ignored,
                    int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    close(dialog, root);
                    return true;
                }
                return false;
            }
        });
    }

    private static void showPermissionPopup(
            final Activity activity, final View anchor,
            boolean dark, int textColor, int subColor,
            final TextView label, final TextView description) {
        final LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(activity, 8), dp(activity, 8),
                dp(activity, 8), dp(activity, 8));
        panel.setBackground(rounded(
                dark ? 0xFF343438 : 0xFFFFFFFF, dp(activity, 14)));
        if (Build.VERSION.SDK_INT >= 21) panel.setElevation(dp(activity, 12));
        final PopupWindow popup = new PopupWindow(
                panel, dp(activity, 224),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setOutsideTouchable(true);
        addPermissionOption(activity, panel, popup,
                AgentToolConfig.PERMISSION_EXECUTE,
                UiLanguage.text(activity, "允许执行", "Allow execution"),
                UiLanguage.text(activity,
                        "界面动作仅在 DeepSeek 内执行",
                        "UI actions stay inside DeepSeek"),
                textColor, subColor, label, description);
        addPermissionOption(activity, panel, popup,
                AgentToolConfig.PERMISSION_ALL,
                UiLanguage.text(activity, "全部允许", "Allow all"),
                UiLanguage.text(activity,
                        "允许已连接后端操作系统前台界面",
                        "Allow the connected backend to control the foreground UI"),
                textColor, subColor, label, description);
        panel.measure(
                View.MeasureSpec.makeMeasureSpec(
                        dp(activity, 224), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(
                        dp(activity, 260), View.MeasureSpec.AT_MOST));
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int x = Math.max(dp(activity, 8),
                location[0] + anchor.getWidth() - dp(activity, 224));
        int y = Math.max(dp(activity, 8),
                location[1] - panel.getMeasuredHeight() - dp(activity, 8));
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }

    private static void addPermissionOption(
            final Activity activity, LinearLayout panel,
            final PopupWindow popup, final String value,
            String title, String detail, int textColor, int subColor,
            final TextView label, final TextView description) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(activity, 12), dp(activity, 10),
                dp(activity, 12), dp(activity, 10));
        row.addView(text(activity, title, 15, textColor, true));
        row.addView(text(activity, detail, 11, subColor, false));
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                if (AgentToolConfig.setPermission(value)) {
                    label.setText(permissionLabel(activity, value) + "  \u25be");
                    description.setText(permissionDescription(activity, value));
                    popup.dismiss();
                } else {
                    Toast.makeText(activity, UiLanguage.text(activity,
                            "权限设置保存失败",
                            "Could not save permission settings"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        panel.addView(row);
    }

    private static LinearLayout switchRow(
            Context context, String title, String description,
            int textColor, int subColor, Switch toggle) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 16), dp(context, 13),
                dp(context, 12), dp(context, 13));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(text(context, title, 15, textColor, true));
        TextView detail = text(context, description, 12, subColor, false);
        detail.setLineSpacing(dp(context, 1), 1f);
        labels.addView(detail);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelsParams.rightMargin = dp(context, 12);
        row.addView(labels, labelsParams);
        row.addView(toggle);
        return row;
    }

    private static void addBackendButton(
            Context context, LinearLayout parent,
            Map<String, TextView> targets, String key, String label,
            int textColor, boolean dark) {
        TextView button = text(context, label, 14, textColor, true);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 6), dp(context, 10),
                dp(context, 6), dp(context, 10));
        button.setClickable(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        if (!targets.isEmpty()) params.leftMargin = dp(context, 8);
        parent.addView(button, params);
        targets.put(key, button);
    }

    private static void updateBackendButtons(
            Context context, Map<String, TextView> buttons,
            String selected, boolean dark, int textColor) {
        for (Map.Entry<String, TextView> entry : buttons.entrySet()) {
            boolean active = entry.getKey().equals(selected);
            entry.getValue().setTextColor(active
                    ? (dark ? 0xFFFFFFFF : 0xFF2948D8) : textColor);
            entry.getValue().setBackground(rounded(
                    active
                            ? (dark ? 0xFF3D4A79 : 0xFFE6EBFF)
                            : (dark ? 0xFF363639 : 0xFFF0F1F4),
                    dp(context, 11)));
        }
    }

    private static String backendDescription(Context context, String backend) {
        if (AgentToolConfig.BACKEND_ROOT.equals(backend)) {
            return UiLanguage.text(context,
                    "已选择 Root；点按可重新校验授权",
                    "Root selected; tap it to verify authorization again");
        }
        if (AgentToolConfig.BACKEND_SHIZUKU.equals(backend)) {
            return UiLanguage.text(context,
                    "已选择 Shizuku；需先在 Shizuku 中启动服务并允许 DeepSeek",
                    "Shizuku selected; start its service and allow DeepSeek first");
        }
        return UiLanguage.text(context,
                "应用内模式无需额外权限，只操作当前 DeepSeek 窗口",
                "In-app mode needs no extra permission and only controls DeepSeek");
    }

    private static String permissionLabel(Context context, String value) {
        return AgentToolConfig.PERMISSION_EXECUTE.equals(value)
                ? UiLanguage.text(context, "允许执行", "Allow execution")
                : UiLanguage.text(context, "全部允许", "Allow all");
    }

    private static String permissionDescription(Context context, String value) {
        return AgentToolConfig.PERMISSION_EXECUTE.equals(value)
                ? UiLanguage.text(context,
                "只在 DeepSeek 应用内执行点击、滑动、返回和截图",
                "Tap, swipe, back, and capture only inside DeepSeek")
                : UiLanguage.text(context,
                "允许所选 Root/Shizuku 后端操作当前系统前台界面",
                "Allow the selected Root/Shizuku backend to control the foreground UI");
    }

    private static void sectionTitle(
            LinearLayout parent, Context context, String value, int color) {
        TextView title = text(context, value, 14, color, true);
        title.setPadding(dp(context, 4), dp(context, 20),
                dp(context, 4), dp(context, 8));
        parent.addView(title);
    }

    private static LinearLayout card(Context context, int color) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(rounded(color, dp(context, 13)));
        if (Build.VERSION.SDK_INT >= 21) card.setElevation(dp(context, 1));
        return card;
    }

    private static View divider(Context context, int color) {
        View divider = new View(context);
        divider.setBackgroundColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1));
        params.leftMargin = dp(context, 16);
        divider.setLayoutParams(params);
        return divider;
    }

    private static Switch switchView(Context context, boolean dark) {
        Switch value = new Switch(context);
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{-android.R.attr.state_checked}
        };
        value.setThumbTintList(new android.content.res.ColorStateList(
                states, new int[]{DeekseepUi.BRAND,
                dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
        value.setTrackTintList(new android.content.res.ColorStateList(
                states, new int[]{0xFFADBFFF,
                dark ? 0xFF555555 : 0xFFBFBFBF}));
        value.setBackground(null);
        return value;
    }

    private static TextView text(
            Context context, String value, float sp,
            int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static GradientDrawable rounded(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int statusBarHeight(Context context) {
        int resource = context.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        return resource > 0
                ? context.getResources().getDimensionPixelSize(resource) : 0;
    }

    private static int dp(Context context, float value) {
        return DeekseepUi.dp(context, value);
    }

    private static void close(final Dialog dialog, View root) {
        root.animate()
                .translationX(root.getResources().getDisplayMetrics().widthPixels)
                .setDuration(190L)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                    }
                })
                .start();
    }
}
