package com.dsmod.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
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

/** Dedicated hidden-page editor for the experimental spatial wallpaper scene. */
final class SpatialMotionUi {
    interface StateListener {
        void onSpatialStateChanged(boolean enabled);
    }

    private interface ConfigToggle {
        boolean apply(ChatAppearance.Config config, boolean checked);
    }

    private SpatialMotionUi() {}

    static void show(final Activity activity, final StateListener stateListener) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = DeekseepUi.isDark(activity);
        final int pageColor = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFAAAAAF : 0xFF70757D;
        final int dividerColor = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(pageColor);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(barColor);
        int statusTop = DeekseepUi.statusBarHeight(activity);
        top.setPadding(dp(activity, 8), statusTop, dp(activity, 16), 0);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(activity, 56) + statusTop));

        TextView back = label(activity, "\u2039", 28, textColor, false);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        back.setContentDescription(tr(activity, "返回", "Back"));
        top.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40)));
        TextView title = label(
                activity, tr(activity, "空间动效", "Spatial motion"),
                18, textColor, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 8);
        top.addView(title, titleParams);

        ScrollView scroll = new ScrollView(activity);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBackground = new GradientDrawable();
        cardBackground.setColor(cardColor);
        cardBackground.setCornerRadius(dp(activity, 12));
        card.setBackground(cardBackground);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.setMargins(
                dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 20));
        scroll.addView(card, cardParams);

        ChatAppearance.Config initial = ChatAppearance.load();
        boolean backgroundReady = ChatAppearance.motionBackgroundReady(initial);
        boolean sensorReady = ChatAppearance.motionSensorAvailable(activity);
        boolean saver = SpatialMotionController.isPowerSaveMode(activity);
        TextView readiness = label(activity,
                tr(activity,
                        "背景图：" + (backgroundReady ? "已就绪" : "未设置")
                                + "  ·  传感器：" + (sensorReady ? "可用" : "不可用")
                                + "  ·  省电：" + (saver ? "暂停动态" : "无限制"),
                        "Wallpaper: " + (backgroundReady ? "ready" : "not configured")
                                + "  ·  Sensor: " + (sensorReady ? "available" : "unavailable")
                                + "  ·  Power: " + (saver ? "motion paused" : "unrestricted")),
                12, subColor, false);
        readiness.setPadding(dp(activity, 16), dp(activity, 14),
                dp(activity, 16), dp(activity, 14));
        card.addView(readiness);
        card.addView(divider(activity, dividerColor));
        addConfigSwitch(
                activity, card, dark, textColor, subColor,
                tr(activity, "启用空间动效", "Enable spatial motion"),
                tr(activity,
                        "姿态视差直接跟手；侧边栏仍沿用原有平滑延迟",
                        "Pose parallax tracks directly; the sidebar keeps its original smooth lag"),
                initial.spatialDepthEnabled,
                tr(activity,
                        "空间动效设置保存失败",
                        "Could not save spatial-motion settings"),
                new ConfigToggle() {
                    @Override public boolean apply(
                            ChatAppearance.Config config, boolean checked) {
                        if (checked && !ChatAppearance.motionBackgroundReady(config)) {
                            toast(activity,
                                    "请先在聊天外观中设置背景图",
                                    "Set a wallpaper in Chat appearance first");
                            return false;
                        }
                        config.spatialDepthEnabled = checked;
                        if (checked) {
                            config.enabled = true;
                            config.shakeParallaxEnabled = false;
                        }
                        return true;
                    }
                },
                new StateListener() {
                    @Override public void onSpatialStateChanged(boolean enabled) {
                        if (stateListener != null) {
                            stateListener.onSpatialStateChanged(enabled);
                        }
                    }
                });
        card.addView(divider(activity, dividerColor));

        final TextView strengthValue = label(
                activity,
                strengthLabel(activity, initial.spatialStrength),
                12, subColor, false);
        LinearLayout strengthRow = actionRow(
                activity, textColor, subColor,
                tr(activity, "动效强度", "Motion strength"),
                strengthValue);
        strengthRow.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                final String[] values = {"weak", "standard", "strong"};
                final String[] labels = {
                        tr(activity, "弱", "Weak"),
                        tr(activity, "标准", "Standard"),
                        tr(activity, "稍强", "Slightly stronger")
                };
                String current = ChatAppearance.load().spatialStrength;
                int selected = "weak".equals(current)
                        ? 0 : ("strong".equals(current) ? 2 : 1);
                new AlertDialog.Builder(activity)
                        .setTitle(tr(activity, "动效强度", "Motion strength"))
                        .setSingleChoiceItems(
                                labels, selected,
                                new DialogInterface.OnClickListener() {
                                    @Override public void onClick(
                                            DialogInterface chooser, int which) {
                                        if (which < 0 || which >= values.length) return;
                                        ChatAppearance.Config config =
                                                ChatAppearance.load();
                                        config.spatialStrength = values[which];
                                        if (ChatAppearance.save(config)) {
                                            strengthValue.setText(
                                                    strengthLabel(
                                                            activity,
                                                            values[which]));
                                            chooser.dismiss();
                                        } else {
                                            toast(activity,
                                                    "动效强度设置保存失败",
                                                    "Could not save motion strength");
                                        }
                                    }
                                })
                        .setNegativeButton(
                                tr(activity, "取消", "Cancel"), null)
                        .show();
            }
        });
        card.addView(strengthRow);
        card.addView(divider(activity, dividerColor));

        addConfigSwitch(
                activity, card, dark, textColor, subColor,
                tr(activity, "外沿颜色无限延伸", "Unlimited outer-edge color extension"),
                tr(activity,
                        "沿用图片最外圈颜色填满移动范围，防止尺寸不足时露白",
                        "Repeat the image's outermost colors across all motion to prevent white gaps"),
                initial.spatialEdgeExtendEnabled,
                tr(activity,
                        "外沿延伸设置保存失败",
                        "Could not save outer-edge extension"),
                new ConfigToggle() {
                    @Override public boolean apply(
                            ChatAppearance.Config config, boolean checked) {
                        config.spatialEdgeExtendEnabled = checked;
                        return true;
                    }
                }, null);
        card.addView(divider(activity, dividerColor));

        addConfigSwitch(
                activity, card, dark, textColor, subColor,
                tr(activity, "减少动态效果", "Reduce motion"),
                tr(activity,
                        "关闭传感器视差，保留静态分层和背景边缘处理",
                        "Disable sensor parallax while retaining static layers and edge handling"),
                initial.spatialReduceMotion,
                tr(activity,
                        "减少动态效果设置保存失败",
                        "Could not save reduced-motion setting"),
                new ConfigToggle() {
                    @Override public boolean apply(
                            ChatAppearance.Config config, boolean checked) {
                        config.spatialReduceMotion = checked;
                        return true;
                    }
                }, null);
        card.addView(divider(activity, dividerColor));

        addConfigSwitch(
                activity, card, dark, textColor, subColor,
                tr(activity, "自动重新校准", "Automatic recentering"),
                tr(activity,
                        "稳定约 650ms 后只修正极小的传感器零点误差",
                        "After about 650ms of stability, correct only tiny sensor-zero errors"),
                initial.spatialAutoRecenter,
                tr(activity,
                        "自动重新校准设置保存失败",
                        "Could not save automatic recentering"),
                new ConfigToggle() {
                    @Override public boolean apply(
                            ChatAppearance.Config config, boolean checked) {
                        config.spatialAutoRecenter = checked;
                        return true;
                    }
                }, null);
        card.addView(divider(activity, dividerColor));

        addConfigSwitch(
                activity, card, dark, textColor, subColor,
                tr(activity, "反转动效方向", "Reverse motion direction"),
                tr(activity,
                        "统一反转背景图的上下左右视差方向",
                        "Reverse both wallpaper parallax axes together"),
                initial.spatialDirectionMultiplier < 0f,
                tr(activity,
                        "动效方向设置保存失败",
                        "Could not save motion direction"),
                new ConfigToggle() {
                    @Override public boolean apply(
                            ChatAppearance.Config config, boolean checked) {
                        config.spatialDirectionMultiplier =
                                checked ? -1f : 1f;
                        return true;
                    }
                }, null);
        card.addView(divider(activity, dividerColor));

        LinearLayout recenter = actionRow(
                activity, textColor, subColor,
                tr(activity, "立即重新校准", "Recenter now"),
                label(activity,
                        tr(activity,
                                "将当前持机姿态设为视觉中心",
                                "Use the current device pose as the visual center"),
                        12, subColor, false));
        recenter.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                ChatAppearance.recenterSpatialMotion();
                toast(activity,
                        "已请求重新校准",
                        "Recenter requested");
            }
        });
        card.addView(recenter);
        DeekseepUi.addBuildFooter(activity, card, subColor);

        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View ignored) {
                DeekseepUi.slideOutAndDismiss(dialog, root);
            }
        });
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(pageColor));
        }
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(
                    DialogInterface ignored, int code,
                    android.view.KeyEvent event) {
                if (code == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction()
                        == android.view.KeyEvent.ACTION_UP) {
                    DeekseepUi.slideOutAndDismiss(dialog, root);
                    return true;
                }
                return false;
            }
        });
        DeekseepUi.openWithSlide(dialog, root);
    }

    private static void addConfigSwitch(
            final Activity activity, LinearLayout parent,
            boolean dark, int textColor, int subColor,
            String title, String detail, boolean checked,
            final String error, final ConfigToggle mutation,
            final StateListener savedListener) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                dp(activity, 16), dp(activity, 13),
                dp(activity, 12), dp(activity, 13));
        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(label(activity, title, 15, textColor, true));
        labels.addView(label(activity, detail, 12, subColor, false));
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final Switch toggle = new HubInsetSwitch(activity);
        tintSwitch(toggle, dark);
        toggle.setChecked(checked);
        final boolean[] syncing = new boolean[1];
        toggle.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean value) {
                        if (syncing[0]) return;
                        ChatAppearance.Config config =
                                ChatAppearance.load();
                        if (!mutation.apply(config, value)) {
                            syncing[0] = true;
                            button.setChecked(!value);
                            syncing[0] = false;
                            return;
                        }
                        if (!ChatAppearance.save(config)) {
                            syncing[0] = true;
                            button.setChecked(!value);
                            syncing[0] = false;
                            Toast.makeText(
                                    activity, error,
                                    Toast.LENGTH_SHORT).show();
                        } else if (savedListener != null) {
                            savedListener.onSpatialStateChanged(value);
                        }
                    }
                });
        row.addView(toggle);
        parent.addView(row);
    }

    private static LinearLayout actionRow(
            Activity activity, int textColor, int subColor,
            String title, TextView detail) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(
                dp(activity, 16), dp(activity, 13),
                dp(activity, 16), dp(activity, 13));
        LinearLayout labels = new LinearLayout(activity);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(label(activity, title, 15, textColor, true));
        labels.addView(detail);
        row.addView(labels, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(label(activity, "\u203a", 24, subColor, false));
        row.setClickable(true);
        row.setFocusable(true);
        return row;
    }

    private static View divider(Context context, int color) {
        View divider = new View(context);
        divider.setBackgroundColor(color);
        divider.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return divider;
    }

    private static TextView label(
            Context context, String value, float size,
            int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static void tintSwitch(Switch value, boolean dark) {
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            value.setButtonTintList(android.content.res.ColorStateList.valueOf(
                    dark ? 0xFF64B5F6 : 0xFF1976D2));
        }
    }

    private static String strengthLabel(Context context, String value) {
        if ("weak".equals(value)) {
            return tr(context, "弱（0.55×）", "Weak (0.55×)");
        }
        if ("strong".equals(value)) {
            return tr(context, "稍强（1.25×）", "Slightly stronger (1.25×)");
        }
        return tr(context, "标准（1.0×）", "Standard (1.0×)");
    }

    private static String tr(Context context, String chinese, String english) {
        return UiLanguage.text(context, chinese, english);
    }

    private static void toast(
            Context context, String chinese, String english) {
        Toast.makeText(
                context, tr(context, chinese, english),
                Toast.LENGTH_SHORT).show();
    }

    private static int dp(Context context, float value) {
        return DeekseepUi.dp(context, value);
    }
}
