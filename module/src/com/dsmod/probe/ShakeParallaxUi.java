package com.dsmod.probe;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

/** Dedicated compatibility and control page for the elastic wallpaper motion mode. */
final class ShakeParallaxUi {
    interface StateListener {
        void onShakeStateChanged(boolean enabled);
    }

    private ShakeParallaxUi() {}

    static void show(final Activity activity, final StateListener listener) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = DeekseepUi.isDark(activity);
        final int page = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int bar = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int sub = dark ? 0xFFAAAAAF : 0xFF70757D;
        final int divider = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(page);

        LinearLayout top = new LinearLayout(activity);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(bar);
        int statusTop = DeekseepUi.statusBarHeight(activity);
        top.setPadding(dp(activity, 8), statusTop, dp(activity, 16), 0);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56) + statusTop));
        TextView back = label(activity, "\u2039", 28, text, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        top.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40)));
        TextView title = label(activity,
                tr(activity, "陀螺仪背景", "Gyroscope wallpaper"),
                18, text, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 8);
        top.addView(title, titleParams);

        ScrollView scroll = new ScrollView(activity);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(cardColor);
        background.setCornerRadius(dp(activity, 12));
        card.setBackground(background);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 20));
        scroll.addView(card, cardParams);

        final ChatAppearance.Config initial = ChatAppearance.load();
        final boolean backgroundReady = ChatAppearance.motionBackgroundReady(initial);
        final boolean sensorReady = ChatAppearance.motionSensorAvailable(activity);
        final boolean saver = SpatialMotionController.isPowerSaveMode(activity);
        card.addView(statusRow(activity, text, sub,
                tr(activity, "背景图", "Wallpaper"),
                backgroundReady
                        ? tr(activity, "已就绪", "Ready")
                        : tr(activity, "未设置", "Not configured")));
        card.addView(divider(activity, divider));
        card.addView(statusRow(activity, text, sub,
                tr(activity, "动作传感器", "Motion sensor"),
                sensorReady
                        ? tr(activity, "可用", "Available")
                        : tr(activity, "不可用", "Unavailable")));
        card.addView(divider(activity, divider));
        card.addView(statusRow(activity, text, sub,
                tr(activity, "省电限制", "Power-saving limit"),
                saver
                        ? tr(activity, "已暂停动态", "Motion paused")
                        : tr(activity, "无限制", "Unrestricted")));
        card.addView(divider(activity, divider));

        LinearLayout enableRow = new LinearLayout(activity);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.setPadding(dp(activity, 16), dp(activity, 13),
                dp(activity, 12), dp(activity, 13));
        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(label(activity,
                tr(activity, "启用陀螺仪背景", "Enable gyroscope wallpaper"),
                15, text, true));
        labels.addView(label(activity,
                tr(activity, "晃动时轻微漂移，松手后弹回", "Drift on motion and spring back"),
                12, sub, false));
        enableRow.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch toggle = new HubInsetSwitch(activity);
        tint(toggle, dark);
        toggle.setChecked(initial.shakeParallaxEnabled);
        final boolean[] syncing = new boolean[1];
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (syncing[0]) return;
                if (checked && (!backgroundReady || !sensorReady)) {
                    syncing[0] = true;
                    button.setChecked(false);
                    syncing[0] = false;
                    toast(activity, !backgroundReady
                            ? tr(activity, "请先在聊天外观中设置背景图", "Set a wallpaper in Chat appearance first")
                            : tr(activity, "此设备没有可用的动作传感器", "No motion sensor is available on this device"));
                    return;
                }
                ChatAppearance.Config config = ChatAppearance.load();
                config.shakeParallaxEnabled = checked;
                if (checked) {
                    config.enabled = true;
                    config.spatialDepthEnabled = false;
                }
                if (!ChatAppearance.save(config)) {
                    syncing[0] = true;
                    button.setChecked(!checked);
                    syncing[0] = false;
                    toast(activity, tr(activity,
                            "陀螺仪背景设置保存失败",
                            "Could not save gyroscope-wallpaper settings"));
                } else if (listener != null) {
                    listener.onShakeStateChanged(checked);
                }
            }
        });
        enableRow.addView(toggle);
        card.addView(enableRow);

        LinearLayout wallpaper = statusRow(activity, text, sub,
                tr(activity, "设置或更换背景图", "Set or change wallpaper"), "\u203a");
        wallpaper.setClickable(true);
        wallpaper.setFocusable(true);
        wallpaper.setBackground(DeekseepUi.controlBackground(
                0x00000000, dark ? 0x24FFFFFF : 0x14000000, 0f));
        wallpaper.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                ChatAppearanceUi.show(activity);
            }
        });
        card.addView(divider(activity, divider));
        card.addView(wallpaper);
        DeekseepUi.addBuildFooter(activity, card, sub);

        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                DeekseepUi.slideOutAndDismiss(dialog, root);
            }
        });
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(page));
        }
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(DialogInterface ignored, int code,
                    android.view.KeyEvent event) {
                if (code == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    DeekseepUi.slideOutAndDismiss(dialog, root);
                    return true;
                }
                return false;
            }
        });
        DeekseepUi.openWithSlide(dialog, root);
    }

    private static LinearLayout statusRow(Context context, int text, int sub,
            String title, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(context, 16), dp(context, 13),
                dp(context, 16), dp(context, 13));
        row.addView(label(context, title, 15, text, true),
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label(context, value, 12, sub, false));
        return row;
    }

    private static View divider(Context context, int color) {
        View view = new View(context);
        view.setBackgroundColor(color);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return view;
    }

    private static TextView label(Context context, String value, float size,
            int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static void tint(Switch value, boolean dark) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            value.setThumbTintList(ColorStateList.valueOf(
                    dark ? 0xFF64B5F6 : 0xFF1976D2));
        }
    }

    private static String tr(Context context, String zh, String en) {
        return UiLanguage.text(context, zh, en);
    }

    private static void toast(Context context, String value) {
        Toast.makeText(context, value, Toast.LENGTH_SHORT).show();
    }

    private static int dp(Context context, float value) {
        return DeekseepUi.dp(context, value);
    }
}
