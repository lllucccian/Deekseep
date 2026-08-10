package com.dsmod.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

/** Full-screen wallpaper and sticker editor drawn with Android Views. */
final class ChatAppearanceUi {
    private ChatAppearanceUi() {}

    static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        UiLanguage.refreshHost(activity);
        new Page(activity, ChatAppearance.load()).show();
    }

    static void showAssistantAvatar(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        UiLanguage.refreshHost(activity);
        final boolean dark = DeekseepUi.isDark(activity);
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFA8A8AD : 0xFF737780;
        final int dp = Math.max(1, Math.round(activity.getResources()
                .getDisplayMetrics().density));
        final java.io.File custom = ChatAppearance.assistantAvatarFileForRender();

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(22 * dp, 8 * dp, 22 * dp, 6 * dp);

        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable previewBackground = new GradientDrawable();
        previewBackground.setShape(GradientDrawable.OVAL);
        previewBackground.setColor(dark ? 0xFF343944 : 0xFFF0F3FA);
        previewBackground.setStroke(dp, dark ? 0xFF596477 : 0xFFD6DDEB);
        preview.setBackground(previewBackground);
        preview.setClipToOutline(true);
        if (custom != null) {
            Bitmap bitmap = ChatAppearance.loadAssistantAvatarBitmap(custom, 256);
            if (bitmap != null) preview.setImageBitmap(bitmap);
        } else {
            int resource = activity.getResources().getIdentifier(
                    "assistant_message_avatar", "drawable", activity.getPackageName());
            if (resource != 0) preview.setImageResource(resource);
        }
        content.addView(preview, new LinearLayout.LayoutParams(72 * dp, 72 * dp));

        TextView status = avatarText(activity,
                custom == null ? "当前使用 DeepSeek 默认头像" : "已使用自定义头像",
                15, textColor, true);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = 12 * dp;
        content.addView(status, statusParams);

        TextView requirement = avatarText(activity,
                "需要在灰度功能管理里面开启“显示助手头像”，否则聊天页不会显示自定义头像。",
                13, subColor, false);
        requirement.setGravity(Gravity.CENTER);
        requirement.setLineSpacing(0f, 1.12f);
        LinearLayout.LayoutParams requirementParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        requirementParams.topMargin = 8 * dp;
        content.addView(requirement, requirementParams);

        TextView applyHint = avatarText(activity,
                "选择后返回聊天或重新进入会话即可生效。", 12, subColor, false);
        applyHint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = 5 * dp;
        content.addView(applyHint, hintParams);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = 18 * dp;
        content.addView(actions, actionsParams);

        TextView choose = avatarButton(activity, "选择图片", true, dark);
        actions.addView(choose, new LinearLayout.LayoutParams(
                0, 42 * dp, 1f));
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(UiLanguage.text(activity,
                        "自定义 DeepSeek 头像", "Custom DeepSeek avatar"))
                .setView(content)
                .setNegativeButton(UiLanguage.text(activity, "关闭", "Close"), null)
                .create();
        choose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                dialog.dismiss();
                Main.pickGalleryImage(activity, new Main.GalleryPickCallback() {
                    @Override public void onPicked(Uri uri) {
                        if (uri == null) {
                            showAssistantAvatar(activity);
                            return;
                        }
                        ChatAppearance.ImportResult result =
                                ChatAppearance.importAssistantAvatar(activity, uri);
                        UiLanguage.toast(activity, result.message,
                                Toast.LENGTH_SHORT).show();
                        showAssistantAvatar(activity);
                    }
                });
            }
        });
        if (custom != null) {
            TextView reset = avatarButton(activity, "恢复默认", false, dark);
            LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                    0, 42 * dp, 1f);
            resetParams.leftMargin = 8 * dp;
            actions.addView(reset, resetParams);
            reset.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (ChatAppearance.removeAssistantAvatar()) {
                        UiLanguage.toast(activity, "已恢复 DeepSeek 默认头像",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        UiLanguage.toast(activity, "恢复默认头像失败",
                                Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                    showAssistantAvatar(activity);
                }
            });
        }
        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override public void onShow(DialogInterface ignored) {
                Window window = dialog.getWindow();
                if (window != null) {
                    GradientDrawable background = new GradientDrawable();
                    background.setColor(cardColor);
                    background.setCornerRadius(18 * dp);
                    window.setBackgroundDrawable(background);
                }
            }
        });
        dialog.show();
    }

    private static TextView avatarText(Context context, String value, float size,
            int color, boolean medium) {
        TextView text = new TextView(context);
        text.setText(UiLanguage.dynamic(context, value));
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        text.setTextColor(color);
        if (medium) {
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return text;
    }

    private static TextView avatarButton(
            Context context, String value, boolean primary, boolean dark) {
        TextView button = avatarText(context, value, 14,
                primary ? Color.WHITE : (dark ? 0xFFE6E8ED : 0xFF315BCB), true);
        button.setGravity(Gravity.CENTER);
        int normal = primary ? 0xFF315FE6 : (dark ? 0xFF34363B : 0xFFEDF1FB);
        int pressed = primary ? 0xFF254AB7 : (dark ? 0xFF474A50 : 0xFFDCE4F7);
        button.setBackground(DeekseepUi.controlBackground(
                normal, pressed, DeekseepUi.dp(context, 10)));
        button.setClickable(true);
        button.setFocusable(true);
        return button;
    }

    private static final class Page implements EditorCanvas.Listener {
        final Activity activity;
        final ChatAppearance.Config config;
        final boolean dark;
        final int pageColor;
        final int barColor;
        final int cardColor;
        final int textColor;
        final int subColor;
        final int dividerColor;
        final Dialog dialog;
        final LinearLayout root;
        final LinearLayout card;
        LinearLayout masterContent;

        EditorCanvas editor;
        String selectedStickerId;
        TextView selectedLabel;
        TextView backgroundOpacityLabel;
        TextView backgroundRotationLabel;
        TextView backgroundFocusXLabel;
        TextView backgroundFocusYLabel;
        TextView backgroundScaleLabel;
        TextView motionAmountLabel;
        TextView chatMotionLabel;
        TextView sidebarMotionLabel;
        TextView settingsMotionLabel;
        TextView stickerSizeLabel;
        TextView stickerOpacityLabel;
        TextView stickerRotationLabel;
        SeekBar backgroundOpacity;
        SeekBar backgroundRotation;
        SeekBar backgroundFocusX;
        SeekBar backgroundFocusY;
        SeekBar backgroundScale;
        SeekBar motionAmount;
        SeekBar chatMotionAmount;
        SeekBar sidebarMotionAmount;
        SeekBar settingsMotionAmount;
        SeekBar stickerSize;
        SeekBar stickerOpacity;
        SeekBar stickerRotation;
        TextView fitModeButton;
        TextView backgroundExtentButton;
        TextView backgroundEdgeButton;
        TextView backgroundScaleButton;
        TextView advancedOptionsButton;
        TextView backgroundFramingButton;
        TextView backgroundDetailsButton;
        TextView stickerDetailsButton;
        TextView bubbleDetailsButton;
        TextView glassQualityButton;
        TextView glassCapabilityLabel;
        TextView motionPreviewChatButton;
        TextView motionPreviewSidebarButton;
        TextView motionPreviewSettingsButton;
        TextView bringFrontButton;
        TextView deleteStickerButton;
        BubblePreview bubblePreview;
        TextView userBubbleButton;
        TextView assistantBubbleButton;
        TextView bubblePresetButton;
        TextView bubbleOpacityLabel;
        TextView bubbleRadiusLabel;
        TextView bubbleBorderLabel;
        TextView bubbleDecorationLabel;
        TextView bubbleDecorationSizeLabel;
        TextView bubbleDecorationXLabel;
        TextView bubbleDecorationOpacityLabel;
        TextView bubbleDecorationRotationLabel;
        TextView removeBubbleDecorationButton;
        TextView bubbleCutoutButton;
        TextView stickerCutoutButton;
        SeekBar bubbleOpacity;
        SeekBar bubbleRadius;
        SeekBar bubbleBorder;
        SeekBar bubbleDecorationSize;
        SeekBar bubbleDecorationX;
        SeekBar bubbleDecorationOpacity;
        SeekBar bubbleDecorationRotation;
        Switch bubbleToggle;
        Switch glassToggle;
        LinearLayout bubbleStyleControls;
        LinearLayout bubbleDecorationControls;
        LinearLayout backgroundFramingControls;
        LinearLayout backgroundDetailsControls;
        LinearLayout stickerDetailsControls;
        boolean editingUserBubble = true;
        Switch motionToggle;
        Switch perScreenMotionToggle;
        Switch depthToggle;
        LinearLayout perScreenMotionControls;
        boolean backgroundFramingExpanded;
        boolean backgroundDetailsExpanded;
        boolean stickerDetailsExpanded;
        boolean bubbleDetailsExpanded;
        boolean bindingControls;
        float backgroundScaleGestureBase = 1f;

        Page(Activity activity, ChatAppearance.Config config) {
            this.activity = activity;
            this.config = config == null ? new ChatAppearance.Config() : config;
            dark = DeekseepUi.isDark(activity);
            pageColor = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
            barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
            cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
            textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
            subColor = dark ? 0xFFA8A8AD : 0xFF777777;
            dividerColor = dark ? 0xFF414145 : 0xFFE8E8EB;
            dialog = new Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
            root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setBackgroundColor(pageColor);
            card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL);
            if (!this.config.stickers.isEmpty()) {
                selectedStickerId = this.config.stickers.get(this.config.stickers.size() - 1).id;
            }
        }

        void show() {
            buildHeader();
            buildBody();
            dialog.setContentView(root);
            Window window = dialog.getWindow();
            if (window != null) {
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                window.setBackgroundDrawable(new ColorDrawable(pageColor));
            }
            dialog.setOnKeyListener(new Dialog.OnKeyListener() {
                @Override public boolean onKey(DialogInterface ignored, int keyCode,
                                                android.view.KeyEvent event) {
                    if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                            && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                        DeekseepUi.slideOutAndDismiss(dialog, root);
                        return true;
                    }
                    return false;
                }
            });
            DeekseepUi.trackChildDialog(dialog);
            DeekseepUi.openWithSlide(dialog, root);
        }

        private void buildHeader() {
            LinearLayout bar = new LinearLayout(activity);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setBackgroundColor(barColor);
            int statusTop = DeekseepUi.statusBarHeight(activity);
            bar.setPadding(dp(8), statusTop, dp(16), 0);
            root.addView(bar, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(56) + statusTop));

            TextView back = new TextView(activity);
            back.setText("\u2039");
            back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
            back.setTextColor(textColor);
            back.setGravity(Gravity.CENTER);
            back.setPadding(dp(8), 0, dp(8), 0);
            back.setClickable(true);
            back.setContentDescription(t("返回", "Back"));
            back.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    DeekseepUi.slideOutAndDismiss(dialog, root);
                }
            });
            bar.addView(back, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)));

            TextView title = new TextView(activity);
            title.setText(t("聊天外观", "Chat appearance"));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setTypeface(Typeface.DEFAULT_BOLD);
            title.setTextColor(textColor);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            titleParams.leftMargin = dp(8);
            bar.addView(title, titleParams);
        }

        private void buildBody() {
            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            root.addView(scroll, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            GradientDrawable cardBackground = new GradientDrawable();
            cardBackground.setColor(cardColor);
            cardBackground.setCornerRadius(dp(8));
            card.setBackground(cardBackground);
            FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(dp(14), dp(14), dp(14), dp(18));
            scroll.addView(card, cardParams);

            addMasterSwitch();

            masterContent = new LinearLayout(activity);
            masterContent.setOrientation(LinearLayout.VERTICAL);
            card.addView(masterContent, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            masterContent.addView(divider());
            addLiquidGlassControls();
            masterContent.addView(divider());
            addBubbleControls();
            masterContent.addView(divider());
            addPreview();
            masterContent.addView(divider());
            addBackgroundFramingControls();
            masterContent.addView(divider());
            addImportActions();
            masterContent.addView(divider());
            addBackgroundControls();
            masterContent.addView(divider());
            addStickerControls();
            masterContent.addView(divider());
            addResetAction();
            DeekseepUi.addBuildFooter(activity, card, subColor);
            masterContent.setVisibility(config.enabled ? View.VISIBLE : View.GONE);
            bindControls();
        }

        private void addMasterSwitch() {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(14), dp(12), dp(14));

            LinearLayout labels = new LinearLayout(activity);
            labels.setOrientation(LinearLayout.VERTICAL);
            TextView title = label("启用聊天背景与贴纸", 16, textColor, true);
            labels.addView(title);
            TextView detail = label("背景可在高级选项中绑定界面；贴纸会保留在聊天与设置页。覆盖层不会拦截输入、滚动或操作。",
                    12, subColor, false);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = dp(4);
            labels.addView(detail, detailParams);
            LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelsParams.rightMargin = dp(10);
            row.addView(labels, labelsParams);

            Switch toggle = new HubInsetSwitch(activity);
            tintSwitch(toggle);
            toggle.setChecked(config.enabled);
            toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                    config.enabled = checked;
                    if (masterContent != null) {
                        masterContent.setVisibility(checked ? View.VISIBLE : View.GONE);
                    }
                    persist();
                }
            });
            row.addView(toggle);
            card.addView(row);
        }

        private void addBubbleControls() {
            LinearLayout section = section();
            section.addView(sectionTitle("聊天气泡"));

            LinearLayout enabledRow = new LinearLayout(activity);
            enabledRow.setOrientation(LinearLayout.HORIZONTAL);
            enabledRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout enabledLabels = new LinearLayout(activity);
            enabledLabels.setOrientation(LinearLayout.VERTICAL);
            enabledLabels.addView(label("启用气泡定制", 14, textColor, true));
            enabledLabels.addView(label(
                    "分别修改用户与 DeepSeek 消息；样式和贴纸绑定到真实消息节点，会随聊天内容一起滚动。",
                    12, subColor, false), top(3));
            LinearLayout.LayoutParams enabledLabelParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            enabledLabelParams.rightMargin = dp(10);
            enabledRow.addView(enabledLabels, enabledLabelParams);
            bubbleToggle = new HubInsetSwitch(activity);
            tintSwitch(bubbleToggle);
            bubbleToggle.setChecked(config.bubbleEnabled);
            bubbleToggle.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(
                                CompoundButton button, boolean checked) {
                            if (bindingControls) return;
                            config.bubbleEnabled = checked;
                            bindBubbleControls();
                            refreshBubblePreview();
                            persist();
                        }
                    });
            enabledRow.addView(bubbleToggle);
            section.addView(enabledRow, top(8));

            bubblePreview = new BubblePreview(activity, config, dark);
            section.addView(bubblePreview, top(12));

            LinearLayout sides = new LinearLayout(activity);
            sides.setOrientation(LinearLayout.HORIZONTAL);
            section.addView(sides, top(12));
            userBubbleButton = actionButton("用户消息", false);
            sides.addView(userBubbleButton, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            userBubbleButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    editingUserBubble = true;
                    bindBubbleControls();
                }
            });
            assistantBubbleButton = actionButton("DeepSeek 消息", false);
            LinearLayout.LayoutParams assistantParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            assistantParams.leftMargin = dp(8);
            sides.addView(assistantBubbleButton, assistantParams);
            assistantBubbleButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    editingUserBubble = false;
                    bindBubbleControls();
                }
            });

            bubbleDetailsButton = actionButton("", false);
            section.addView(bubbleDetailsButton, top(10));
            bubbleDetailsButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    bubbleDetailsExpanded = !bubbleDetailsExpanded;
                    updateCollapsedSections();
                }
            });

            bubbleStyleControls = new LinearLayout(activity);
            bubbleStyleControls.setOrientation(LinearLayout.VERTICAL);
            section.addView(bubbleStyleControls, top(7));

            bubblePresetButton = actionButton("", false);
            bubbleStyleControls.addView(bubblePresetButton);
            bubblePresetButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { chooseBubblePreset(); }
            });

            bubbleOpacityLabel = label("", 13, subColor, false);
            bubbleStyleControls.addView(bubbleOpacityLabel, top(9));
            bubbleOpacity = seekBar(100);
            bubbleStyleControls.addView(bubbleOpacity);
            bubbleOpacity.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    bubbleStyle().opacity = progress / 100f;
                    updateBubbleLabels();
                    refreshBubblePreview();
                }

                @Override public void stopped() {
                    if (!bindingControls) persist();
                }
            });

            bubbleRadiusLabel = label("", 13, subColor, false);
            bubbleStyleControls.addView(bubbleRadiusLabel, top(7));
            bubbleRadius = seekBar(Math.round(ChatAppearance.MAX_BUBBLE_RADIUS));
            bubbleStyleControls.addView(bubbleRadius);
            bubbleRadius.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    bubbleStyle().radius = progress;
                    updateBubbleLabels();
                    refreshBubblePreview();
                }

                @Override public void stopped() {
                    if (!bindingControls) persist();
                }
            });

            bubbleBorderLabel = label("", 13, subColor, false);
            bubbleStyleControls.addView(bubbleBorderLabel, top(7));
            bubbleBorder = seekBar(Math.round(ChatAppearance.MAX_BUBBLE_BORDER * 10f));
            bubbleStyleControls.addView(bubbleBorder);
            bubbleBorder.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    bubbleStyle().borderWidth = progress / 10f;
                    updateBubbleLabels();
                    refreshBubblePreview();
                }

                @Override public void stopped() {
                    if (!bindingControls) persist();
                }
            });

            bubbleDecorationLabel = label("", 13, textColor, true);
            bubbleStyleControls.addView(bubbleDecorationLabel, top(12));
            LinearLayout decorationActions = new LinearLayout(activity);
            decorationActions.setOrientation(LinearLayout.HORIZONTAL);
            bubbleStyleControls.addView(decorationActions, top(7));
            TextView importDecoration = actionButton("导入顶部贴纸", true);
            decorationActions.addView(importDecoration, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            importDecoration.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    final boolean user = editingUserBubble;
                    Main.pickGalleryImage(activity, new Main.GalleryPickCallback() {
                        @Override public void onPicked(Uri uri) {
                            if (uri != null) importBubbleDecoration(uri, user);
                        }
                    });
                }
            });
            removeBubbleDecorationButton = actionButton("移除", false);
            removeBubbleDecorationButton.setTextColor(0xFFE14B4B);
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            removeParams.leftMargin = dp(8);
            decorationActions.addView(removeBubbleDecorationButton, removeParams);
            removeBubbleDecorationButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    final boolean user = editingUserBubble;
                    if (!bubbleStyle().hasDecoration()) return;
                    confirm("移除气泡贴纸？",
                            "导入的贴纸副本会从 DeepSeek 私有目录删除。",
                            new Runnable() {
                                @Override public void run() {
                                    if (ChatAppearance.removeBubbleDecoration(user)) recreate();
                                    else toast("移除气泡贴纸失败");
                                }
                            });
                }
            });

            bubbleCutoutButton = actionButton("抠出透明贴纸", false);
            bubbleStyleControls.addView(bubbleCutoutButton, top(7));
            bubbleCutoutButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (!bubbleStyle().hasDecoration()) return;
                    final boolean user = editingUserBubble;
                    ImageCutoutUi.showBubble(activity, user, new Runnable() {
                        @Override public void run() {
                            recreate();
                        }
                    });
                }
            });

            bubbleDecorationControls = new LinearLayout(activity);
            bubbleDecorationControls.setOrientation(LinearLayout.VERTICAL);
            bubbleStyleControls.addView(bubbleDecorationControls, top(7));

            bubbleDecorationSizeLabel = label("", 13, subColor, false);
            bubbleDecorationControls.addView(bubbleDecorationSizeLabel);
            bubbleDecorationSize = seekBar(Math.round(
                    ChatAppearance.MAX_BUBBLE_DECORATION_SIZE
                            - ChatAppearance.MIN_BUBBLE_DECORATION_SIZE));
            bubbleDecorationControls.addView(bubbleDecorationSize);
            bubbleDecorationSize.setOnSeekBarChangeListener(
                    bubbleDecorationListener(0));

            bubbleDecorationXLabel = label("", 13, subColor, false);
            bubbleDecorationControls.addView(bubbleDecorationXLabel, top(5));
            bubbleDecorationX = seekBar(100);
            bubbleDecorationControls.addView(bubbleDecorationX);
            bubbleDecorationX.setOnSeekBarChangeListener(
                    bubbleDecorationListener(1));

            bubbleDecorationOpacityLabel = label("", 13, subColor, false);
            bubbleDecorationControls.addView(bubbleDecorationOpacityLabel, top(5));
            bubbleDecorationOpacity = seekBar(100);
            bubbleDecorationControls.addView(bubbleDecorationOpacity);
            bubbleDecorationOpacity.setOnSeekBarChangeListener(
                    bubbleDecorationListener(2));

            bubbleDecorationRotationLabel = label("", 13, subColor, false);
            bubbleDecorationControls.addView(bubbleDecorationRotationLabel, top(5));
            bubbleDecorationRotation = seekBar(360);
            bubbleDecorationControls.addView(bubbleDecorationRotation);
            bubbleDecorationRotation.setOnSeekBarChangeListener(
                    bubbleDecorationListener(3));

            masterContent.addView(section);
        }

        private void addLiquidGlassControls() {
            LinearLayout section = section();
            section.addView(sectionTitle("全局液态玻璃"));

            LinearLayout enabledRow = new LinearLayout(activity);
            enabledRow.setOrientation(LinearLayout.HORIZONTAL);
            enabledRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout labels = new LinearLayout(activity);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.addView(label("启用全局液态玻璃", 14, textColor, true));
            labels.addView(label(
                    "共享一张背板纹理，在气泡、输入区和侧栏边缘产生柔和折射。",
                    12, subColor, false), top(3));
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            labelParams.rightMargin = dp(10);
            enabledRow.addView(labels, labelParams);

            glassToggle = new HubInsetSwitch(activity);
            tintSwitch(glassToggle);
            glassToggle.setChecked(config.liquidGlassEnabled);
            glassToggle.setEnabled(true);
            glassToggle.setAlpha(1f);
            glassToggle.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                @Override public void onCheckedChanged(
                        CompoundButton button, boolean checked) {
                    if (bindingControls) return;
                    config.liquidGlassEnabled = checked;
                    bindGlassControls();
                    persist();
                }
            });
            enabledRow.addView(glassToggle);
            section.addView(enabledRow, top(8));

            glassQualityButton = actionButton("", false);
            section.addView(glassQualityButton, top(10));
            glassQualityButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    chooseGlassQuality();
                }
            });

            glassCapabilityLabel = label("", 12, subColor, false);
            section.addView(glassCapabilityLabel, top(7));
            masterContent.addView(section);
        }

        private void addPreview() {
            LinearLayout section = section();
            section.addView(sectionTitle("布局预览"));
            TextView hint = label("点按选中贴纸，直接拖动调整位置；下面可以继续调整大小、旋转和不透明度。",
                    12, subColor, false);
            LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hintParams.topMargin = dp(4);
            section.addView(hint, hintParams);

            editor = new EditorCanvas(activity, config, dark, this);
            editor.setSelectedSticker(selectedStickerId);
            int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
            int previewHeight = Math.max(dp(360), Math.min(dp(520),
                    Math.round(screenHeight * 0.58f)));
            LinearLayout.LayoutParams editorParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, previewHeight);
            editorParams.topMargin = dp(12);
            section.addView(editor, editorParams);
            masterContent.addView(section);
        }

        private void addBackgroundFramingControls() {
            LinearLayout section = section();
            backgroundFramingButton = actionButton("", false);
            styleArrowToggle(backgroundFramingButton);
            section.addView(backgroundFramingButton);
            backgroundFramingButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    backgroundFramingExpanded = !backgroundFramingExpanded;
                    updateCollapsedSections();
                }
            });

            backgroundFramingControls = new LinearLayout(activity);
            backgroundFramingControls.setOrientation(LinearLayout.VERTICAL);
            section.addView(backgroundFramingControls, top(5));

            backgroundFocusXLabel = compactSliderLabel();
            backgroundFocusX = seekBar(100);
            addCompactSliderRow(
                    backgroundFramingControls, backgroundFocusXLabel, backgroundFocusX);
            backgroundFocusX.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    config.backgroundFocusX = progress / 100f;
                    updateBackgroundFocusLabels();
                    editor.applyBackground();
                }

                @Override public void stopped() {
                    if (!bindingControls) persist();
                }
            });

            backgroundFocusYLabel = compactSliderLabel();
            backgroundFocusY = seekBar(100);
            addCompactSliderRow(
                    backgroundFramingControls, backgroundFocusYLabel, backgroundFocusY);
            backgroundFocusY.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    config.backgroundFocusY = progress / 100f;
                    updateBackgroundFocusLabels();
                    editor.applyBackground();
                }

                @Override public void stopped() {
                    if (!bindingControls) persist();
                }
            });

            backgroundRotationLabel = compactSliderLabel();
            backgroundRotation = seekBar(360);
            addCompactSliderRow(
                    backgroundFramingControls, backgroundRotationLabel, backgroundRotation);
            backgroundRotation.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    config.backgroundRotation = progress - 180f;
                    updateBackgroundRotationLabel();
                    editor.applyBackground();
                }

                @Override public void stopped() {
                    if (!bindingControls) persist();
                }
            });

            masterContent.addView(section);
        }

        private void addImportActions() {
            LinearLayout section = section();
            section.addView(sectionTitle("导入图片"));
            TextView detail = label("从系统相册选择一次，然后决定设为背景图还是添加为贴纸。"
                    + "模块会保存私有副本，删除相册原图后仍可显示。",
                    12, subColor, false);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            detailParams.topMargin = dp(4);
            section.addView(detail, detailParams);

            TextView importButton = actionButton("选择图片", true);
            LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            buttonParams.topMargin = dp(12);
            section.addView(importButton, buttonParams);
            importButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    Main.pickGalleryImage(activity, new Main.GalleryPickCallback() {
                        @Override public void onPicked(Uri uri) {
                            if (uri != null) chooseImportRole(uri);
                        }
                    });
                }
            });
            masterContent.addView(section);
        }

        private void addBackgroundControls() {
            LinearLayout section = section();
            section.addView(sectionTitle("背景图"));

            backgroundOpacityLabel = label("", 13, textColor, false);
            section.addView(backgroundOpacityLabel, top(8));
            backgroundOpacity = seekBar(100);
            section.addView(backgroundOpacity);
            backgroundOpacity.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    config.backgroundOpacity = progress / 100f;
                    updateBackgroundLabel();
                    editor.applyBackground();
                }

                @Override public void stopped() {
                    if (!bindingControls) persist();
                }
            });

            fitModeButton = actionButton("", false);
            section.addView(fitModeButton, top(8));
            fitModeButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { chooseBackgroundMode(); }
            });

            int backgroundDetailsStart = section.getChildCount();
            backgroundExtentButton = actionButton("", false);
            section.addView(backgroundExtentButton, top(10));
            backgroundExtentButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    chooseBackgroundExtent();
                }
            });

            backgroundEdgeButton = actionButton("", false);
            section.addView(backgroundEdgeButton, top(7));
            backgroundEdgeButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    chooseBackgroundEdgeMode();
                }
            });

            backgroundScaleLabel = label("", 13, subColor, false);
            section.addView(backgroundScaleLabel, top(10));
            backgroundScale = seekBar(200);
            backgroundScale.setProgress(100);
            section.addView(backgroundScale);
            backgroundScale.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void started() {
                    backgroundScaleGestureBase = config.backgroundScale;
                }

                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    double multiplier = Math.pow(2d, (progress - 100) / 50d);
                    double candidate = backgroundScaleGestureBase * multiplier;
                    if (Double.isNaN(candidate) || candidate <= 0d) return;
                    config.backgroundScale = candidate >= Float.MAX_VALUE
                            ? Float.MAX_VALUE
                            : Math.max(ChatAppearance.MIN_BACKGROUND_SCALE,
                                    (float) candidate);
                    updateBackgroundGeometryLabels();
                    editor.applyBackground();
                }

                @Override public void stopped() {
                    if (bindingControls) return;
                    backgroundScaleGestureBase = config.backgroundScale;
                    bindingControls = true;
                    backgroundScale.setProgress(100);
                    bindingControls = false;
                    persist();
                }
            });

            backgroundScaleButton = actionButton("", false);
            section.addView(backgroundScaleButton, top(5));
            backgroundScaleButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    chooseExactBackgroundScale();
                }
            });

            LinearLayout depthRow = new LinearLayout(activity);
            depthRow.setOrientation(LinearLayout.HORIZONTAL);
            depthRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout depthLabels = new LinearLayout(activity);
            depthLabels.setOrientation(LinearLayout.VERTICAL);
            depthLabels.addView(label("背景景深", 14, textColor, true));
            depthLabels.addView(label(
                    "只轻微虚化、降对比并放大背景，让原生聊天框看起来悬浮在图片上方；不会修改 DeepSeek UI。",
                    12, subColor, false), top(3));
            LinearLayout.LayoutParams depthLabelsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            depthLabelsParams.rightMargin = dp(10);
            depthRow.addView(depthLabels, depthLabelsParams);
            depthToggle = new HubInsetSwitch(activity);
            tintSwitch(depthToggle);
            depthToggle.setChecked(config.depthEnabled);
            depthToggle.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(
                                CompoundButton button, boolean checked) {
                            if (bindingControls) return;
                            config.depthEnabled = checked;
                            editor.applyBackground();
                            persist();
                        }
                    });
            depthRow.addView(depthToggle);
            section.addView(depthRow, top(12));

            LinearLayout motionRow = new LinearLayout(activity);
            motionRow.setOrientation(LinearLayout.HORIZONTAL);
            motionRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout motionLabels = new LinearLayout(activity);
            motionLabels.setOrientation(LinearLayout.VERTICAL);
            motionLabels.addView(label("动态背景位移", 14, textColor, true));
            TextView motionDetail = label(
                    "打开侧栏时背景随主界面向右，进入设置时向左；画布会预留边缘，避免露白。",
                    12, subColor, false);
            motionLabels.addView(motionDetail, top(3));
            LinearLayout.LayoutParams motionLabelsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            motionLabelsParams.rightMargin = dp(10);
            motionRow.addView(motionLabels, motionLabelsParams);

            motionToggle = new HubInsetSwitch(activity);
            tintSwitch(motionToggle);
            motionToggle.setChecked(config.motionEnabled);
            motionToggle.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(
                                CompoundButton button, boolean checked) {
                            if (bindingControls) return;
                            config.motionEnabled = checked;
                            bindMotionControls();
                            editor.applyMotionLayout(true);
                            persist();
                        }
                    });
            motionRow.addView(motionToggle);
            section.addView(motionRow, top(12));

            LinearLayout perScreenRow = new LinearLayout(activity);
            perScreenRow.setOrientation(LinearLayout.HORIZONTAL);
            perScreenRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout perScreenLabels = new LinearLayout(activity);
            perScreenLabels.setOrientation(LinearLayout.VERTICAL);
            perScreenLabels.addView(label("分别设置各界面位移", 14, textColor, true));
            perScreenLabels.addView(label(
                    "关闭时只显示统一强度；打开后分别设置聊天、侧栏和设置界面的水平位移。",
                    12, subColor, false), top(3));
            LinearLayout.LayoutParams perScreenLabelsParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            perScreenLabelsParams.rightMargin = dp(10);
            perScreenRow.addView(perScreenLabels, perScreenLabelsParams);
            perScreenMotionToggle = new HubInsetSwitch(activity);
            tintSwitch(perScreenMotionToggle);
            perScreenMotionToggle.setChecked(config.perScreenMotionEnabled);
            perScreenMotionToggle.setOnCheckedChangeListener(
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override public void onCheckedChanged(
                                CompoundButton button, boolean checked) {
                            if (bindingControls) return;
                            config.perScreenMotionEnabled = checked;
                            bindMotionControls();
                            editor.applyMotionLayout(true);
                            persist();
                        }
                    });
            perScreenRow.addView(perScreenMotionToggle);
            section.addView(perScreenRow, top(9));

            motionAmountLabel = label("", 13, subColor, false);
            section.addView(motionAmountLabel, top(8));
            motionAmount = seekBar(Math.round(
                    ChatAppearance.MAX_MOTION_AMOUNT * 100f));
            section.addView(motionAmount);
            motionAmount.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    config.motionAmount = progress / 100f;
                    updateMotionLabel();
                    editor.applyMotionLayout(false);
                }

                @Override public void stopped() {
                    if (!bindingControls) persist();
                }
            });

            perScreenMotionControls = new LinearLayout(activity);
            perScreenMotionControls.setOrientation(LinearLayout.VERTICAL);
            section.addView(perScreenMotionControls, top(7));

            chatMotionLabel = label("", 13, subColor, false);
            perScreenMotionControls.addView(chatMotionLabel);
            chatMotionAmount = seekBar(
                    Math.round(ChatAppearance.MAX_MOTION_AMOUNT * 200f));
            perScreenMotionControls.addView(chatMotionAmount);
            chatMotionAmount.setOnSeekBarChangeListener(
                    signedMotionListener(0));

            sidebarMotionLabel = label("", 13, subColor, false);
            perScreenMotionControls.addView(sidebarMotionLabel, top(5));
            sidebarMotionAmount = seekBar(
                    Math.round(ChatAppearance.MAX_MOTION_AMOUNT * 200f));
            perScreenMotionControls.addView(sidebarMotionAmount);
            sidebarMotionAmount.setOnSeekBarChangeListener(
                    signedMotionListener(1));

            settingsMotionLabel = label("", 13, subColor, false);
            perScreenMotionControls.addView(settingsMotionLabel, top(5));
            settingsMotionAmount = seekBar(
                    Math.round(ChatAppearance.MAX_MOTION_AMOUNT * 200f));
            perScreenMotionControls.addView(settingsMotionAmount);
            settingsMotionAmount.setOnSeekBarChangeListener(
                    signedMotionListener(2));

            TextView previewHint = label(
                    "动态效果预览（侧栏向右 / 设置向左）", 12, subColor, false);
            section.addView(previewHint, top(8));
            LinearLayout previews = new LinearLayout(activity);
            previews.setOrientation(LinearLayout.HORIZONTAL);
            section.addView(previews, top(7));

            motionPreviewSidebarButton = actionButton("侧栏", false);
            previews.addView(motionPreviewSidebarButton,
                    new LinearLayout.LayoutParams(
                            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            motionPreviewSidebarButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    editor.previewMotion(1, true);
                }
            });

            motionPreviewChatButton = actionButton("聊天", false);
            LinearLayout.LayoutParams chatPreviewParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            chatPreviewParams.leftMargin = dp(7);
            previews.addView(motionPreviewChatButton, chatPreviewParams);
            motionPreviewChatButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    editor.previewMotion(0, true);
                }
            });

            motionPreviewSettingsButton = actionButton("设置", false);
            LinearLayout.LayoutParams settingsPreviewParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            settingsPreviewParams.leftMargin = dp(7);
            previews.addView(motionPreviewSettingsButton, settingsPreviewParams);
            motionPreviewSettingsButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    editor.previewMotion(-1, true);
                }
            });

            TextView advancedHint = label(
                    "选择背景图在哪些界面显示；未选中的界面仍会保留贴纸。",
                    12, subColor, false);
            section.addView(advancedHint, top(12));
            advancedOptionsButton = actionButton("", false);
            section.addView(advancedOptionsButton, top(7));
            advancedOptionsButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    chooseAdvancedOptions();
                }
            });

            backgroundDetailsControls = new LinearLayout(activity);
            backgroundDetailsControls.setOrientation(LinearLayout.VERTICAL);
            while (section.getChildCount() > backgroundDetailsStart) {
                View child = section.getChildAt(backgroundDetailsStart);
                section.removeViewAt(backgroundDetailsStart);
                backgroundDetailsControls.addView(child);
            }
            backgroundDetailsButton = actionButton("", false);
            styleArrowToggle(backgroundDetailsButton);
            section.addView(backgroundDetailsButton, top(9));
            section.addView(backgroundDetailsControls, top(4));
            backgroundDetailsButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    backgroundDetailsExpanded = !backgroundDetailsExpanded;
                    updateCollapsedSections();
                }
            });

            TextView clear = actionButton("清除背景图", false);
            clear.setTextColor(0xFFE14B4B);
            section.addView(clear, top(8));
            clear.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (config.backgroundFile.length() == 0) return;
                    confirm("清除背景图？", "已导入的背景副本会从 DeepSeek 私有目录删除。",
                            new Runnable() {
                                @Override public void run() {
                                    if (ChatAppearance.removeBackground()) recreate();
                                    else toast("清除背景图失败");
                                }
                            });
                }
            });
            masterContent.addView(section);
        }

        private void addStickerControls() {
            LinearLayout section = section();
            section.addView(sectionTitle("贴纸"));
            selectedLabel = label("", 13, textColor, true);
            section.addView(selectedLabel, top(8));

            stickerCutoutButton = actionButton("抠出透明贴纸", true);
            section.addView(stickerCutoutButton, top(9));
            stickerCutoutButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    final ChatAppearance.Sticker sticker = selectedSticker();
                    if (sticker == null) return;
                    ImageCutoutUi.showSticker(
                            activity, sticker.id, new Runnable() {
                                @Override public void run() {
                                    recreate();
                                }
                            });
                }
            });

            int stickerDetailsStart = section.getChildCount();
            stickerSizeLabel = label("", 13, subColor, false);
            section.addView(stickerSizeLabel, top(10));
            stickerSize = seekBar(100);
            section.addView(stickerSize);
            stickerSize.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    ChatAppearance.Sticker sticker = selectedSticker();
                    if (sticker == null) return;
                    sticker.size = 0.08f + progress / 100f * 0.57f;
                    updateStickerLabels(sticker);
                    editor.applySticker(sticker.id);
                }

                @Override public void stopped() {
                    if (!bindingControls && selectedSticker() != null) persist();
                }
            });

            stickerOpacityLabel = label("", 13, subColor, false);
            section.addView(stickerOpacityLabel, top(8));
            stickerOpacity = seekBar(100);
            section.addView(stickerOpacity);
            stickerOpacity.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    ChatAppearance.Sticker sticker = selectedSticker();
                    if (sticker == null) return;
                    sticker.opacity = progress / 100f;
                    updateStickerLabels(sticker);
                    editor.applySticker(sticker.id);
                }

                @Override public void stopped() {
                    if (!bindingControls && selectedSticker() != null) persist();
                }
            });

            stickerRotationLabel = label("", 13, subColor, false);
            section.addView(stickerRotationLabel, top(8));
            stickerRotation = seekBar(360);
            section.addView(stickerRotation);
            stickerRotation.setOnSeekBarChangeListener(new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    ChatAppearance.Sticker sticker = selectedSticker();
                    if (sticker == null) return;
                    sticker.rotation = progress - 180f;
                    updateStickerLabels(sticker);
                    editor.applySticker(sticker.id);
                }

                @Override public void stopped() {
                    if (!bindingControls && selectedSticker() != null) persist();
                }
            });

            stickerDetailsControls = new LinearLayout(activity);
            stickerDetailsControls.setOrientation(LinearLayout.VERTICAL);
            while (section.getChildCount() > stickerDetailsStart) {
                View child = section.getChildAt(stickerDetailsStart);
                section.removeViewAt(stickerDetailsStart);
                stickerDetailsControls.addView(child);
            }
            stickerDetailsButton = actionButton("", false);
            section.addView(stickerDetailsButton, top(8));
            section.addView(stickerDetailsControls, top(4));
            stickerDetailsButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    stickerDetailsExpanded = !stickerDetailsExpanded;
                    updateCollapsedSections();
                }
            });

            LinearLayout actions = new LinearLayout(activity);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams actionsParams = top(10);
            section.addView(actions, actionsParams);

            bringFrontButton = actionButton("移到最上层", false);
            actions.addView(bringFrontButton,
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            bringFrontButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    ChatAppearance.Sticker sticker = selectedSticker();
                    if (sticker == null) return;
                    config.stickers.remove(sticker);
                    config.stickers.add(sticker);
                    persist();
                    editor.renderAll();
                    bindControls();
                }
            });

            deleteStickerButton = actionButton("删除贴纸", false);
            deleteStickerButton.setTextColor(0xFFE14B4B);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            deleteParams.leftMargin = dp(8);
            actions.addView(deleteStickerButton, deleteParams);
            deleteStickerButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    final ChatAppearance.Sticker sticker = selectedSticker();
                    if (sticker == null) return;
                    confirm("删除这张贴纸？", "已导入的贴纸副本会从 DeepSeek 私有目录删除。",
                            new Runnable() {
                                @Override public void run() {
                                    if (ChatAppearance.removeSticker(sticker.id)) recreate();
                                    else toast("删除贴纸失败");
                                }
                            });
                }
            });
            masterContent.addView(section);
        }

        private void addResetAction() {
            LinearLayout section = section();
            TextView reset = actionButton("重置全部聊天外观", false);
            reset.setTextColor(0xFFE14B4B);
            section.addView(reset);
            reset.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    confirm("重置全部聊天外观？",
                            "背景图、全部贴纸、气泡样式和布局设置都会清除；聊天记录不会受到影响。",
                            new Runnable() {
                                @Override public void run() {
                                    if (ChatAppearance.clearAll()) recreate();
                                    else toast("重置聊天外观失败");
                                }
                            });
                }
            });
            masterContent.addView(section);
        }

        private void chooseBubblePreset() {
            final String[] values = {
                    "original", "soft", "outline", "glass", "liquid"
            };
            String[] labels = {
                    t("跟随原版", "Original"),
                    t("柔和纯色", "Soft color"),
                    t("轻描边", "Outline"),
                    t("磨砂玻璃", "Frosted glass"),
                    t("液态玻璃", "Liquid glass")
            };
            new AlertDialog.Builder(activity)
                    .setTitle(t("选择气泡风格", "Choose bubble style"))
                    .setSingleChoiceItems(labels, bubblePresetIndex(bubbleStyle().preset),
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(
                                        DialogInterface chooser, int which) {
                                    ChatAppearance.BubbleStyle style = bubbleStyle();
                                    style.preset = values[Math.max(0,
                                            Math.min(values.length - 1, which))];
                                    if ("outline".equals(style.preset)
                                            && style.borderWidth < 1f) {
                                        style.borderWidth = 1f;
                                    } else if ("liquid".equals(style.preset)
                                            && style.borderWidth < 1.2f) {
                                        style.borderWidth = 1.2f;
                                    }
                                    bindBubbleControls();
                                    refreshBubblePreview();
                                    persist();
                                    chooser.dismiss();
                                }
                            })
                    .setNegativeButton(t("取消", "Cancel"), null)
                    .show();
        }

        private void chooseGlassQuality() {
            final String[] values = {"auto", "high", "balanced", "saver"};
            String[] labels = {
                    t("自动（推荐）", "Auto (recommended)"),
                    t("高画质", "High quality"),
                    t("均衡", "Balanced"),
                    t("省电", "Battery saver")
            };
            int selected = 0;
            for (int i = 0; i < values.length; i++) {
                if (values[i].equals(config.glassQuality)) {
                    selected = i;
                    break;
                }
            }
            new AlertDialog.Builder(activity)
                    .setTitle(t("液态玻璃画质", "Liquid glass quality"))
                    .setSingleChoiceItems(labels, selected,
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(
                                        DialogInterface chooser, int which) {
                                    config.glassQuality = values[Math.max(
                                            0, Math.min(values.length - 1, which))];
                                    bindGlassControls();
                                    persist();
                                    chooser.dismiss();
                                }
                            })
                    .setNegativeButton(t("取消", "Cancel"), null)
                    .show();
        }

        private void chooseImportRole(final Uri uri) {
            String[] options = {
                    t("设为背景图", "Use as wallpaper"),
                    t("添加为页面贴纸", "Add as page sticker"),
                    t("设为用户气泡顶部贴纸", "User bubble decoration"),
                    t("设为 DeepSeek 气泡顶部贴纸", "DeepSeek bubble decoration")
            };
            new AlertDialog.Builder(activity)
                    .setTitle(t("这张图片怎么使用？", "How should this image be used?"))
                    .setItems(options, new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface ignored, int which) {
                            if (which <= 1) importImage(uri, which == 0);
                            else importBubbleDecoration(uri, which == 2);
                        }
                    })
                    .setNegativeButton(t("取消", "Cancel"), null)
                    .show();
        }

        private void importImage(final Uri uri, final boolean background) {
            toast("正在导入图片…");
            new Thread(new Runnable() {
                @Override public void run() {
                    final ChatAppearance.ImportResult result =
                            ChatAppearance.importImage(activity, uri, background);
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (activity.isFinishing()
                                    || (android.os.Build.VERSION.SDK_INT >= 17
                                    && activity.isDestroyed())) {
                                return;
                            }
                            toast(result.message);
                            if (result.ok) {
                                if (background) recreateWithFocusChooser();
                                else recreate();
                            }
                        }
                    });
                }
            }, "Deekseep-appearance-import").start();
        }

        private void importBubbleDecoration(final Uri uri, final boolean user) {
            toast("正在导入图片…");
            new Thread(new Runnable() {
                @Override public void run() {
                    final ChatAppearance.ImportResult result =
                            ChatAppearance.importBubbleDecoration(activity, uri, user);
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (activity.isFinishing()
                                    || (android.os.Build.VERSION.SDK_INT >= 17
                                    && activity.isDestroyed())) {
                                return;
                            }
                            toast(result.message);
                            if (result.ok) recreate();
                        }
                    });
                }
            }, "Deekseep-bubble-decoration-import").start();
        }

        private void recreateWithFocusChooser() {
            try { dialog.dismiss(); } catch (Throwable ignored) {}
            final Page next = new Page(activity, ChatAppearance.load());
            next.show();
            next.root.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!activity.isFinishing()) {
                        next.showInitialBackgroundExtentChooser();
                    }
                }
            }, 260L);
        }

        private void showInitialBackgroundExtentChooser() {
            final String[] values = {
                    "full", "half_top", "half_center", "half_bottom"
            };
            String[] labels = {
                    t("全屏", "Full screen"),
                    t("上半屏", "Top half"),
                    t("中间半屏", "Center half"),
                    t("下半屏", "Bottom half")
            };
            new AlertDialog.Builder(activity)
                    .setTitle(t("先选择背景显示范围",
                            "First choose the wallpaper display area"))
                    .setSingleChoiceItems(labels, extentIndex(config.backgroundExtent),
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(
                                        DialogInterface chooser, int which) {
                                    config.backgroundExtent = values[Math.max(
                                            0, Math.min(values.length - 1, which))];
                                    bindControls();
                                    editor.applyBackground();
                                    persist();
                                    chooser.dismiss();
                                    showFocusPresetChooser();
                                }
                            })
                    .setNegativeButton(t("稍后调整", "Adjust later"),
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(
                                        DialogInterface ignored, int which) {
                                    showFocusPresetChooser();
                                }
                            })
                    .show();
        }

        private void showFocusPresetChooser() {
            final float[][] points = {
                    {0f, 0f}, {0.5f, 0f}, {1f, 0f},
                    {0f, 0.5f}, {0.5f, 0.5f}, {1f, 0.5f},
                    {0f, 1f}, {0.5f, 1f}, {1f, 1f}
            };
            String[] labels = {
                    t("左上", "Top left"), t("上方", "Top"),
                    t("右上", "Top right"), t("左侧", "Left"),
                    t("中央", "Center"), t("右侧", "Right"),
                    t("左下", "Bottom left"), t("下方", "Bottom"),
                    t("右下", "Bottom right")
            };
            new AlertDialog.Builder(activity)
                    .setTitle(t("选择背景取景区域", "Choose wallpaper focus"))
                    .setItems(labels, new DialogInterface.OnClickListener() {
                        @Override public void onClick(
                                DialogInterface ignored, int which) {
                            int index = Math.max(0,
                                    Math.min(points.length - 1, which));
                            config.backgroundFocusX = points[index][0];
                            config.backgroundFocusY = points[index][1];
                            bindControls();
                            editor.applyBackground();
                            persist();
                        }
                    })
                    .setNegativeButton(t("稍后调整", "Adjust later"), null)
                    .show();
        }

        private void chooseBackgroundMode() {
            if (config.backgroundFile.length() == 0) return;
            final String[] values = {"crop", "fit", "stretch"};
            String[] labels = {
                    t("裁剪填充", "Crop to fill"),
                    t("完整显示", "Fit entire image"),
                    t("拉伸填满", "Stretch to fill")
            };
            new AlertDialog.Builder(activity)
                    .setTitle(t("背景图显示方式", "Wallpaper display mode"))
                    .setSingleChoiceItems(labels, modeIndex(config.backgroundMode),
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(DialogInterface chooser, int which) {
                                    config.backgroundMode = values[Math.max(0,
                                            Math.min(values.length - 1, which))];
                                    updateFitModeButton();
                                    bindCropControls();
                                    editor.applyBackground();
                                    persist();
                                    chooser.dismiss();
                                }
                            })
                    .setNegativeButton(t("取消", "Cancel"), null)
                    .show();
        }

        private void chooseBackgroundExtent() {
            if (config.backgroundFile.length() == 0) return;
            final String[] values = {
                    "full", "half_top", "half_center", "half_bottom"
            };
            String[] labels = {
                    t("全屏", "Full screen"),
                    t("上半屏", "Top half"),
                    t("中间半屏", "Center half"),
                    t("下半屏", "Bottom half")
            };
            new AlertDialog.Builder(activity)
                    .setTitle(t("背景显示范围", "Wallpaper display area"))
                    .setSingleChoiceItems(labels, extentIndex(config.backgroundExtent),
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(
                                        DialogInterface chooser, int which) {
                                    config.backgroundExtent = values[Math.max(
                                            0, Math.min(values.length - 1, which))];
                                    updateBackgroundGeometryLabels();
                                    editor.applyBackground();
                                    persist();
                                    chooser.dismiss();
                                }
                            })
                    .setNegativeButton(t("取消", "Cancel"), null)
                    .show();
        }

        private void chooseBackgroundEdgeMode() {
            if (config.backgroundFile.length() == 0) return;
            final String[] values = {"clip", "mirror", "extend"};
            String[] labels = {
                    t("截断：范围外透明", "Clip: transparent outside"),
                    t("镜像：反射延展边缘", "Mirror: reflect image edges"),
                    t("边缘延展：沿用最外圈像素",
                            "Edge extend: repeat outermost pixels")
            };
            int selected = "mirror".equals(config.backgroundEdgeMode)
                    ? 1 : ("extend".equals(config.backgroundEdgeMode) ? 2 : 0);
            new AlertDialog.Builder(activity)
                    .setTitle(t("背景边界处理", "Wallpaper edge handling"))
                    .setSingleChoiceItems(labels, selected,
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(
                                        DialogInterface chooser, int which) {
                                    config.backgroundEdgeMode =
                                            values[Math.max(
                                                    0, Math.min(values.length - 1, which))];
                                    updateBackgroundGeometryLabels();
                                    editor.applyBackground();
                                    persist();
                                    chooser.dismiss();
                                }
                            })
                    .setNegativeButton(t("取消", "Cancel"), null)
                    .show();
        }

        private void chooseExactBackgroundScale() {
            if (config.backgroundFile.length() == 0) return;
            final EditText input = new EditText(activity);
            input.setSingleLine(true);
            input.setSelectAllOnFocus(true);
            input.setInputType(InputType.TYPE_CLASS_NUMBER
                    | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(String.format(
                    Locale.US, "%.7g", config.backgroundScale));
            input.setHint(t("例如 0.5、1、3、20", "For example 0.5, 1, 3, or 20"));
            FrameLayout inputContainer = new FrameLayout(activity);
            inputContainer.setPadding(dp(20), 0, dp(20), 0);
            inputContainer.addView(input, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            new AlertDialog.Builder(activity)
                    .setTitle(t("输入缩放倍率（无固定上限）",
                            "Enter zoom multiplier (no fixed maximum)"))
                    .setMessage(t(
                            "1 为原始适配大小；小于 1 会缩小，大于 1 会放大。",
                            "1 is the fitted size; values below 1 zoom out and values above 1 zoom in."))
                    .setView(inputContainer)
                    .setNegativeButton(t("取消", "Cancel"), null)
                    .setNeutralButton(t("重置为 1", "Reset to 1"),
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(
                                        DialogInterface ignored, int which) {
                                    config.backgroundScale = 1f;
                                    bindControls();
                                    editor.applyBackground();
                                    persist();
                                }
                            })
                    .setPositiveButton(t("确定", "OK"),
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(
                                        DialogInterface ignored, int which) {
                                    try {
                                        double parsed = Double.parseDouble(
                                                input.getText().toString().trim());
                                        if (Double.isNaN(parsed)
                                                || Double.isInfinite(parsed)
                                                || parsed <= 0d) {
                                            throw new NumberFormatException();
                                        }
                                        config.backgroundScale =
                                                parsed >= Float.MAX_VALUE
                                                ? Float.MAX_VALUE
                                                : Math.max(
                                                        ChatAppearance.MIN_BACKGROUND_SCALE,
                                                        (float) parsed);
                                        bindControls();
                                        editor.applyBackground();
                                        persist();
                                    } catch (Throwable invalid) {
                                        toast("请输入大于 0 的缩放倍率");
                                    }
                                }
                            })
                    .show();
        }

        private void chooseAdvancedOptions() {
            if (config.backgroundFile.length() == 0) return;
            final boolean[] selected = {
                    config.backgroundOnChat,
                    config.backgroundOnSidebar,
                    config.backgroundOnSettings
            };
            String[] labels = {
                    t("聊天界面", "Chat screen"),
                    t("会话侧栏", "Conversation sidebar"),
                    t("设置界面", "Settings screen")
            };
            new AlertDialog.Builder(activity)
                    .setTitle(t("背景图绑定界面", "Wallpaper screen binding"))
                    .setMultiChoiceItems(labels, selected,
                            new DialogInterface.OnMultiChoiceClickListener() {
                                @Override public void onClick(
                                        DialogInterface ignored, int which, boolean checked) {
                                    if (which >= 0 && which < selected.length) {
                                        selected[which] = checked;
                                    }
                                }
                            })
                    .setNegativeButton(t("取消", "Cancel"), null)
                    .setPositiveButton(t("确定", "OK"),
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(
                                        DialogInterface ignored, int which) {
                                    config.backgroundOnChat = selected[0];
                                    config.backgroundOnSidebar = selected[1];
                                    config.backgroundOnSettings = selected[2];
                                    updateAdvancedOptionsButton();
                                    editor.applyBackground();
                                    persist();
                                }
                            })
                    .show();
        }

        private void bindControls() {
            bindingControls = true;
            boolean hasBackground = config.backgroundFile.length() > 0;
            backgroundOpacity.setEnabled(hasBackground);
            backgroundRotation.setEnabled(hasBackground);
            backgroundScale.setEnabled(hasBackground);
            depthToggle.setEnabled(hasBackground);
            fitModeButton.setEnabled(hasBackground);
            backgroundExtentButton.setEnabled(hasBackground);
            backgroundEdgeButton.setEnabled(hasBackground);
            backgroundScaleButton.setEnabled(hasBackground);
            advancedOptionsButton.setEnabled(hasBackground);
            backgroundFramingButton.setEnabled(hasBackground);
            backgroundFramingButton.setAlpha(hasBackground ? 1f : 0.45f);
            backgroundDetailsButton.setEnabled(hasBackground);
            backgroundDetailsButton.setAlpha(hasBackground ? 1f : 0.45f);
            backgroundOpacity.setProgress(Math.round(config.backgroundOpacity * 100f));
            backgroundRotation.setProgress(
                    Math.round(config.backgroundRotation + 180f));
            backgroundFocusX.setProgress(Math.round(config.backgroundFocusX * 100f));
            backgroundFocusY.setProgress(Math.round(config.backgroundFocusY * 100f));
            backgroundScaleGestureBase = config.backgroundScale;
            backgroundScale.setProgress(100);
            depthToggle.setChecked(config.depthEnabled);
            motionToggle.setEnabled(hasBackground);
            motionToggle.setChecked(config.motionEnabled);
            perScreenMotionToggle.setChecked(config.perScreenMotionEnabled);
            motionAmount.setProgress(Math.round(config.motionAmount * 100f));
            int signedCenter = Math.round(
                    ChatAppearance.MAX_MOTION_AMOUNT * 100f);
            chatMotionAmount.setProgress(
                    signedCenter + Math.round(config.chatMotionAmount * 100f));
            sidebarMotionAmount.setProgress(
                    signedCenter + Math.round(config.sidebarMotionAmount * 100f));
            settingsMotionAmount.setProgress(
                    signedCenter + Math.round(config.settingsMotionAmount * 100f));
            updateBackgroundLabel();
            updateBackgroundRotationLabel();
            updateBackgroundFocusLabels();
            updateFitModeButton();
            updateBackgroundGeometryLabels();
            updateAdvancedOptionsButton();
            updateMotionLabel();
            updateSignedMotionLabels();
            bindCropControls();
            bindMotionControls();
            bindGlassControls();

            ChatAppearance.Sticker selected = selectedSticker();
            boolean hasSticker = selected != null;
            stickerSize.setEnabled(hasSticker);
            stickerOpacity.setEnabled(hasSticker);
            stickerRotation.setEnabled(hasSticker);
            bringFrontButton.setEnabled(hasSticker);
            deleteStickerButton.setEnabled(hasSticker);
            stickerCutoutButton.setEnabled(hasSticker);
            stickerCutoutButton.setAlpha(hasSticker ? 1f : 0.45f);
            float size = hasSticker ? selected.size : 0.24f;
            stickerSize.setProgress(Math.round((size - 0.08f) / 0.57f * 100f));
            stickerOpacity.setProgress(hasSticker ? Math.round(selected.opacity * 100f) : 100);
            stickerRotation.setProgress(hasSticker ? Math.round(selected.rotation + 180f) : 180);
            if (hasSticker) updateStickerLabels(selected);
            else {
                selectedLabel.setText(t("尚未添加贴纸", "No stickers added"));
                stickerSizeLabel.setText(t("大小", "Size"));
                stickerOpacityLabel.setText(t("贴纸不透明度", "Sticker opacity"));
                stickerRotationLabel.setText(t("旋转", "Rotation"));
            }
            bindBubbleControls();
            updateCollapsedSections();
            bindingControls = false;
        }

        private void bindGlassControls() {
            if (glassToggle == null || glassQualityButton == null) return;
            boolean previousBinding = bindingControls;
            bindingControls = true;
            glassToggle.setChecked(config.liquidGlassEnabled);
            glassToggle.setEnabled(true);
            glassToggle.setAlpha(1f);
            glassQualityButton.setText(
                    t("渲染画质：", "Rendering quality: ")
                            + glassQualityLabel(config.glassQuality));
            glassQualityButton.setEnabled(config.liquidGlassEnabled);
            glassQualityButton.setAlpha(config.liquidGlassEnabled ? 1f : 0.45f);
            glassCapabilityLabel.setText(
                    t("当前设备将使用：", "This device will use: ")
                            + LiquidGlassEngine.capabilitySummary(activity, config));
            glassCapabilityLabel.setAlpha(config.liquidGlassEnabled ? 1f : 0.55f);
            bindingControls = previousBinding;
        }

        private String glassQualityLabel(String quality) {
            if ("high".equals(quality)) return t("高画质", "High");
            if ("balanced".equals(quality)) return t("均衡", "Balanced");
            if ("saver".equals(quality)) return t("省电", "Battery saver");
            return t("自动", "Auto");
        }

        private ChatAppearance.BubbleStyle bubbleStyle() {
            return config.bubble(editingUserBubble);
        }

        private int bubblePresetIndex(String preset) {
            if ("soft".equals(preset)) return 1;
            if ("outline".equals(preset)) return 2;
            if ("glass".equals(preset)) return 3;
            if ("liquid".equals(preset)) return 4;
            return 0;
        }

        private String bubblePresetLabel(String preset) {
            if ("soft".equals(preset)) return t("柔和纯色", "Soft color");
            if ("outline".equals(preset)) return t("轻描边", "Outline");
            if ("glass".equals(preset)) return t("磨砂玻璃", "Frosted glass");
            if ("liquid".equals(preset)) return t("液态玻璃", "Liquid glass");
            return t("跟随原版", "Original");
        }

        private void bindBubbleControls() {
            if (bubbleToggle == null) return;
            boolean previousBinding = bindingControls;
            bindingControls = true;
            ChatAppearance.BubbleStyle style = bubbleStyle();
            boolean enabled = config.bubbleEnabled;
            boolean customSurface = !"original".equals(style.preset);
            boolean hasDecoration = style.hasDecoration();

            bubbleToggle.setChecked(enabled);
            styleBubbleTab(userBubbleButton, editingUserBubble);
            styleBubbleTab(assistantBubbleButton, !editingUserBubble);
            bubblePresetButton.setText(
                    (editingUserBubble
                            ? t("用户消息风格：", "User message style: ")
                            : t("DeepSeek 消息风格：", "DeepSeek message style: "))
                            + bubblePresetLabel(style.preset));
            bubblePresetButton.setEnabled(enabled);
            bubblePresetButton.setAlpha(enabled ? 1f : 0.45f);

            bubbleOpacity.setProgress(Math.round(style.opacity * 100f));
            bubbleRadius.setProgress(Math.round(style.radius));
            bubbleBorder.setProgress(Math.round(style.borderWidth * 10f));
            bubbleOpacity.setEnabled(enabled && customSurface);
            bubbleRadius.setEnabled(enabled && customSurface);
            bubbleBorder.setEnabled(enabled && customSurface);

            bubbleDecorationSize.setProgress(Math.round(style.decorationSize
                    - ChatAppearance.MIN_BUBBLE_DECORATION_SIZE));
            bubbleDecorationX.setProgress(Math.round(style.decorationX * 100f));
            bubbleDecorationOpacity.setProgress(
                    Math.round(style.decorationOpacity * 100f));
            bubbleDecorationRotation.setProgress(
                    Math.round(style.decorationRotation + 180f));
            bubbleDecorationControls.setVisibility(
                    hasDecoration ? View.VISIBLE : View.GONE);
            bubbleDecorationControls.setAlpha(enabled ? 1f : 0.45f);
            bubbleDecorationSize.setEnabled(enabled && hasDecoration);
            bubbleDecorationX.setEnabled(enabled && hasDecoration);
            bubbleDecorationOpacity.setEnabled(enabled && hasDecoration);
            bubbleDecorationRotation.setEnabled(enabled && hasDecoration);
            removeBubbleDecorationButton.setEnabled(enabled && hasDecoration);
            removeBubbleDecorationButton.setAlpha(
                    enabled && hasDecoration ? 1f : 0.45f);
            bubbleCutoutButton.setEnabled(enabled && hasDecoration);
            bubbleCutoutButton.setAlpha(
                    enabled && hasDecoration ? 1f : 0.45f);
            bubbleDetailsButton.setEnabled(enabled);
            bubbleDetailsButton.setAlpha(enabled ? 1f : 0.45f);
            bubbleStyleControls.setAlpha(enabled ? 1f : 0.52f);
            updateBubbleLabels();
            updateCollapsedSections();
            bindingControls = previousBinding;
        }

        private void updateCollapsedSections() {
            if (backgroundFramingButton != null
                    && backgroundFramingControls != null) {
                backgroundFramingButton.setText(
                        backgroundFramingExpanded ? "▴" : "▾");
                backgroundFramingButton.setContentDescription(
                        backgroundFramingExpanded
                                ? t("收起取景与旋转", "Hide focus and rotation")
                                : t("展开取景与旋转", "Show focus and rotation"));
                backgroundFramingControls.setVisibility(
                        backgroundFramingExpanded ? View.VISIBLE : View.GONE);
            }
            if (backgroundDetailsButton != null
                    && backgroundDetailsControls != null) {
                backgroundDetailsButton.setText(
                        backgroundDetailsExpanded ? "▴" : "▾");
                backgroundDetailsButton.setContentDescription(
                        backgroundDetailsExpanded
                                ? t("收起背景选项", "Hide wallpaper options")
                                : t("展开背景选项", "Show wallpaper options"));
                backgroundDetailsControls.setVisibility(
                        backgroundDetailsExpanded ? View.VISIBLE : View.GONE);
            }
            if (stickerDetailsButton != null && stickerDetailsControls != null) {
                stickerDetailsButton.setText(stickerDetailsExpanded
                        ? t("收起大小、透明度与旋转 ▴",
                                "Hide size, opacity, and rotation ▴")
                        : t("大小、透明度与旋转 ▾",
                                "Size, opacity, and rotation ▾"));
                stickerDetailsControls.setVisibility(
                        stickerDetailsExpanded ? View.VISIBLE : View.GONE);
            }
            if (bubbleDetailsButton != null && bubbleStyleControls != null) {
                bubbleDetailsButton.setText(bubbleDetailsExpanded
                        ? t("收起气泡样式与贴纸参数 ▴",
                                "Hide bubble and decoration details ▴")
                        : t("气泡样式与贴纸参数 ▾",
                                "Bubble and decoration details ▾"));
                bubbleStyleControls.setVisibility(
                        bubbleDetailsExpanded ? View.VISIBLE : View.GONE);
            }
        }

        private void styleBubbleTab(TextView button, boolean selected) {
            if (button == null) return;
            GradientDrawable background = new GradientDrawable();
            background.setColor(selected
                    ? (dark ? 0xFF3A3A40 : 0xFFE4E6EC)
                    : (dark ? 0xFF2A2A2E : 0xFFF0F1F4));
            background.setCornerRadius(dp(6));
            button.setBackground(background);
            button.setTextColor(dark ? 0xFFE8E8E8 : 0xFF202124);
            button.setAlpha(selected ? 1f : 0.7f);
        }

        private void updateBubbleLabels() {
            ChatAppearance.BubbleStyle style = bubbleStyle();
            bubbleOpacityLabel.setText(t("填充不透明度：", "Fill opacity: ")
                    + Math.round(style.opacity * 100f) + "%");
            bubbleRadiusLabel.setText(t("圆角：", "Corner radius: ")
                    + Math.round(style.radius) + " dp");
            bubbleBorderLabel.setText(t("描边宽度：", "Border width: ")
                    + oneDecimal(style.borderWidth) + " dp");
            bubbleDecorationLabel.setText(
                    (editingUserBubble
                            ? t("用户气泡顶部贴纸", "User bubble top decoration")
                            : t("DeepSeek 气泡顶部贴纸",
                                    "DeepSeek bubble top decoration"))
                            + (style.hasDecoration()
                            ? t("（已导入）", " (imported)")
                            : t("（未设置）", " (not set)")));
            bubbleDecorationSizeLabel.setText(t("贴纸大小：", "Decoration size: ")
                    + Math.round(style.decorationSize) + " dp");
            bubbleDecorationXLabel.setText(t("横向位置：", "Horizontal position: ")
                    + Math.round(style.decorationX * 100f) + "%");
            bubbleDecorationOpacityLabel.setText(
                    t("贴纸不透明度：", "Decoration opacity: ")
                            + Math.round(style.decorationOpacity * 100f) + "%");
            bubbleDecorationRotationLabel.setText(t("贴纸旋转：", "Decoration rotation: ")
                    + Math.round(style.decorationRotation) + "°");
        }

        private String oneDecimal(float value) {
            int tenths = Math.round(value * 10f);
            return (tenths / 10) + "." + Math.abs(tenths % 10);
        }

        private void refreshBubblePreview() {
            if (bubblePreview != null) bubblePreview.refresh();
        }

        private SimpleSeekListener bubbleDecorationListener(final int target) {
            return new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    ChatAppearance.BubbleStyle style = bubbleStyle();
                    if (target == 0) {
                        style.decorationSize =
                                ChatAppearance.MIN_BUBBLE_DECORATION_SIZE + progress;
                    } else if (target == 1) {
                        style.decorationX = progress / 100f;
                    } else if (target == 2) {
                        style.decorationOpacity = progress / 100f;
                    } else {
                        style.decorationRotation = progress - 180f;
                    }
                    updateBubbleLabels();
                    refreshBubblePreview();
                }

                @Override public void stopped() {
                    if (!bindingControls) persist();
                }
            };
        }

        private void updateBackgroundLabel() {
            if (config.backgroundFile.length() == 0) {
                backgroundOpacityLabel.setText(t("尚未设置背景图", "No wallpaper selected"));
            } else {
                backgroundOpacityLabel.setText(t("背景图不透明度：", "Wallpaper opacity: ")
                        + Math.round(config.backgroundOpacity * 100f) + "%");
            }
        }

        private void updateFitModeButton() {
            fitModeButton.setText(t("显示方式：", "Display mode: ")
                    + modeLabel(config.backgroundMode));
            fitModeButton.setAlpha(config.backgroundFile.length() > 0 ? 1f : 0.45f);
        }

        private void updateBackgroundGeometryLabels() {
            boolean enabled = config.backgroundFile.length() > 0;
            backgroundExtentButton.setText(t("显示范围：", "Display area: ")
                    + extentLabel(config.backgroundExtent));
            backgroundEdgeButton.setText(t("边界处理：", "Edge handling: ")
                    + edgeModeLabel(config.backgroundEdgeMode));
            String scale = formatBackgroundScale(config.backgroundScale);
            backgroundScaleLabel.setText(t(
                    "连续缩放：", "Continuous zoom: ") + scale
                    + t("（滑动后回中，可反复缩放）",
                            " (recenters after release; repeat as needed)"));
            backgroundScaleButton.setText(t(
                    "精确倍率：", "Exact multiplier: ") + scale
                    + t("（点按输入，无固定上限）",
                            " (tap to enter; no fixed maximum)"));
            float alpha = enabled ? 1f : 0.45f;
            backgroundExtentButton.setAlpha(alpha);
            backgroundEdgeButton.setAlpha(alpha);
            backgroundScaleButton.setAlpha(alpha);
            backgroundScaleLabel.setAlpha(alpha);
        }

        private void updateBackgroundRotationLabel() {
            backgroundRotationLabel.setText(t("背景图旋转：", "Wallpaper rotation: ")
                    + Math.round(config.backgroundRotation) + "°");
            backgroundRotationLabel.setAlpha(
                    config.backgroundFile.length() > 0 ? 1f : 0.45f);
        }

        private void updateBackgroundFocusLabels() {
            backgroundFocusXLabel.setText(t("横向取景：", "Horizontal focus: ")
                    + Math.round(config.backgroundFocusX * 100f) + "%");
            backgroundFocusYLabel.setText(t("纵向取景：", "Vertical focus: ")
                    + Math.round(config.backgroundFocusY * 100f) + "%");
        }

        private void bindCropControls() {
            // Focus also positions a fitted or freely scaled image inside transparent/mirrored
            // space, so it remains useful for every display mode.
            boolean enabled = config.backgroundFile.length() > 0;
            backgroundFocusX.setEnabled(enabled);
            backgroundFocusY.setEnabled(enabled);
            float alpha = enabled ? 1f : 0.45f;
            backgroundFocusXLabel.setAlpha(alpha);
            backgroundFocusYLabel.setAlpha(alpha);
        }

        private void updateAdvancedOptionsButton() {
            ArrayList<String> names = new ArrayList<>();
            if (config.backgroundOnChat) names.add(t("聊天", "Chat"));
            if (config.backgroundOnSidebar) names.add(t("侧栏", "Sidebar"));
            if (config.backgroundOnSettings) names.add(t("设置", "Settings"));
            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < names.size(); i++) {
                if (i > 0) summary.append(t("、", ", "));
                summary.append(names.get(i));
            }
            if (summary.length() == 0) summary.append(t("未绑定", "None"));
            advancedOptionsButton.setText(
                    t("高级选项：", "Advanced: ") + summary.toString());
            advancedOptionsButton.setAlpha(
                    config.backgroundFile.length() > 0 ? 1f : 0.45f);
        }

        private void updateMotionLabel() {
            motionAmountLabel.setText(t("统一位移强度：", "Unified motion: ")
                    + Math.round(config.motionAmount * 100f) + "%");
        }

        private void updateSignedMotionLabels() {
            chatMotionLabel.setText(t("聊天界面位移：", "Chat offset: ")
                    + signedPercent(config.chatMotionAmount));
            sidebarMotionLabel.setText(t("侧栏界面位移：", "Sidebar offset: ")
                    + signedPercent(config.sidebarMotionAmount));
            settingsMotionLabel.setText(t("设置界面位移：", "Settings offset: ")
                    + signedPercent(config.settingsMotionAmount));
        }

        private String signedPercent(float value) {
            int percent = Math.round(value * 100f);
            return (percent > 0 ? "+" : "") + percent + "%";
        }

        private void bindMotionControls() {
            boolean hasBackground = config.backgroundFile.length() > 0;
            boolean motionEnabled = hasBackground && config.motionEnabled;
            perScreenMotionToggle.setEnabled(motionEnabled);
            boolean separate = config.perScreenMotionEnabled;
            motionAmountLabel.setVisibility(separate ? View.GONE : View.VISIBLE);
            motionAmount.setVisibility(separate ? View.GONE : View.VISIBLE);
            perScreenMotionControls.setVisibility(separate ? View.VISIBLE : View.GONE);
            motionAmount.setEnabled(motionEnabled && !separate);
            motionAmountLabel.setAlpha(motionEnabled ? 1f : 0.45f);
            chatMotionAmount.setEnabled(motionEnabled && separate);
            sidebarMotionAmount.setEnabled(motionEnabled && separate);
            settingsMotionAmount.setEnabled(motionEnabled && separate);
            perScreenMotionControls.setAlpha(motionEnabled ? 1f : 0.45f);
            motionPreviewChatButton.setEnabled(hasBackground);
            motionPreviewSidebarButton.setEnabled(hasBackground);
            motionPreviewSettingsButton.setEnabled(hasBackground);
            float previewAlpha = hasBackground ? 1f : 0.45f;
            motionPreviewChatButton.setAlpha(previewAlpha);
            motionPreviewSidebarButton.setAlpha(previewAlpha);
            motionPreviewSettingsButton.setAlpha(previewAlpha);
        }

        private SimpleSeekListener signedMotionListener(final int target) {
            return new SimpleSeekListener() {
                @Override public void changed(int progress, boolean fromUser) {
                    if (bindingControls || !fromUser) return;
                    int center = Math.round(
                            ChatAppearance.MAX_MOTION_AMOUNT * 100f);
                    float value = (progress - center) / 100f;
                    if (target == 0) config.chatMotionAmount = value;
                    else if (target == 1) config.sidebarMotionAmount = value;
                    else config.settingsMotionAmount = value;
                    updateSignedMotionLabels();
                    editor.applyMotionLayout(false);
                }

                @Override public void stopped() {
                    if (!bindingControls) persist();
                }
            };
        }

        private void updateStickerLabels(ChatAppearance.Sticker sticker) {
            int index = config.stickers.indexOf(sticker);
            selectedLabel.setText(t("已选贴纸 ", "Selected sticker ")
                    + (index + 1) + " / " + config.stickers.size());
            stickerSizeLabel.setText(t("大小：", "Size: ")
                    + Math.round(sticker.size * 100f) + "%");
            stickerOpacityLabel.setText(t("贴纸不透明度：", "Sticker opacity: ")
                    + Math.round(sticker.opacity * 100f) + "%");
            stickerRotationLabel.setText(t("旋转：", "Rotation: ")
                    + Math.round(sticker.rotation) + "°");
        }

        private ChatAppearance.Sticker selectedSticker() {
            return config.sticker(selectedStickerId);
        }

        private void persist() {
            if (!ChatAppearance.save(config)) toast("聊天外观设置保存失败");
        }

        private void recreate() {
            try { dialog.dismiss(); } catch (Throwable ignored) {}
            ChatAppearanceUi.show(activity);
        }

        private void confirm(String title, String message, final Runnable accepted) {
            new AlertDialog.Builder(activity)
                    .setTitle(t(title, english(title)))
                    .setMessage(t(message, english(message)))
                    .setNegativeButton(t("取消", "Cancel"), null)
                    .setPositiveButton(t("确定", "OK"), new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface ignored, int which) {
                            if (accepted != null) accepted.run();
                        }
                    })
                    .show();
        }

        @Override public void onStickerSelected(String id) {
            selectedStickerId = id;
            editor.setSelectedSticker(id);
            bindControls();
        }

        @Override public void onStickerMoved(String id) {
            selectedStickerId = id;
            persist();
            bindControls();
        }

        private String modeLabel(String mode) {
            if ("fit".equals(mode)) return t("完整显示", "Fit entire image");
            if ("stretch".equals(mode)) return t("拉伸填满", "Stretch to fill");
            return t("裁剪填充", "Crop to fill");
        }

        private int modeIndex(String mode) {
            if ("fit".equals(mode)) return 1;
            if ("stretch".equals(mode)) return 2;
            return 0;
        }

        private String extentLabel(String extent) {
            if ("half_top".equals(extent)) return t("上半屏", "Top half");
            if ("half_center".equals(extent)) return t("中间半屏", "Center half");
            if ("half_bottom".equals(extent)) return t("下半屏", "Bottom half");
            return t("全屏", "Full screen");
        }

        private String edgeModeLabel(String edgeMode) {
            if ("mirror".equals(edgeMode)) {
                return t("镜像延展", "Mirrored extension");
            }
            if ("extend".equals(edgeMode)) {
                return t("边缘像素延展", "Outermost-pixel extension");
            }
            return t("截断", "Clip");
        }

        private int extentIndex(String extent) {
            if ("half_top".equals(extent)) return 1;
            if ("half_center".equals(extent)) return 2;
            if ("half_bottom".equals(extent)) return 3;
            return 0;
        }

        private String formatBackgroundScale(float value) {
            double scale = value > 0f && !Float.isInfinite(value)
                    && !Float.isNaN(value) ? value : 1d;
            if (scale >= 10000d || scale < 0.1d) {
                return String.format(Locale.US, "%.3g×", scale);
            }
            if (scale >= 100d) return String.format(Locale.US, "%.1f×", scale);
            return String.format(Locale.US, "%.2f×", scale);
        }

        private String english(String chinese) {
            return UiLanguageCatalog.toEnglish(chinese);
        }

        private String t(String chinese, String english) {
            return UiLanguage.text(activity, chinese, english);
        }

        private void toast(String value) {
            UiLanguage.toast(activity, value, Toast.LENGTH_SHORT).show();
        }

        private int dp(float value) {
            return DeekseepUi.dp(activity, value);
        }

        private LinearLayout section() {
            LinearLayout section = new LinearLayout(activity);
            section.setOrientation(LinearLayout.VERTICAL);
            section.setPadding(dp(16), dp(14), dp(16), dp(14));
            return section;
        }

        private TextView sectionTitle(String title) {
            return label(title, 16, textColor, true);
        }

        private TextView label(String value, int size, int color, boolean bold) {
            TextView label = new TextView(activity);
            label.setText(t(value, english(value)));
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
            label.setTextColor(color);
            if (bold) label.setTypeface(Typeface.DEFAULT_BOLD);
            return label;
        }

        private TextView actionButton(String value, boolean primary) {
            TextView button = new TextView(activity);
            button.setText(t(value, english(value)));
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            button.setTypeface(Typeface.DEFAULT);
            button.setGravity(Gravity.CENTER);
            button.setPadding(dp(14), dp(9), dp(14), dp(9));
            button.setTextColor(primary
                    ? (dark ? 0xFFF2F2F2 : 0xFF202124)
                    : (dark ? 0xFFDDDDDD : 0xFF303034));
            GradientDrawable background = new GradientDrawable();
            background.setColor(primary
                    ? (dark ? 0xFF3A3A40 : 0xFFE4E6EC)
                    : (dark ? 0xFF303034 : 0xFFF0F1F4));
            background.setCornerRadius(dp(6));
            button.setBackground(background);
            button.setClickable(true);
            button.setFocusable(true);
            return button;
        }

        private void styleArrowToggle(TextView button) {
            if (button == null) return;
            button.setTypeface(Typeface.DEFAULT);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            button.setTextColor(subColor);
            button.setGravity(Gravity.CENTER);
            button.setMinHeight(dp(30));
            button.setPadding(0, 0, 0, 0);
            button.setBackgroundColor(Color.TRANSPARENT);
        }

        private SeekBar seekBar(int max) {
            SeekBar seekBar = new SeekBar(activity);
            seekBar.setMax(max);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(
                        DeekseepUi.BRAND));
                seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(
                        DeekseepUi.BRAND));
            }
            return seekBar;
        }

        private TextView compactSliderLabel() {
            TextView value = label("", 12, subColor, false);
            value.setSingleLine(true);
            value.setGravity(Gravity.CENTER_VERTICAL);
            return value;
        }

        private void addCompactSliderRow(
                LinearLayout parent, TextView value, SeekBar control) {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                    0, dp(36), 0.44f);
            row.addView(value, valueParams);
            control.setPadding(dp(2), 0, dp(2), 0);
            LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
                    0, dp(36), 0.56f);
            controlParams.leftMargin = dp(4);
            row.addView(control, controlParams);
            parent.addView(row);
        }

        private void tintSwitch(Switch toggle) {
            int[][] states = {
                    {android.R.attr.state_checked},
                    {-android.R.attr.state_checked}
            };
            toggle.setThumbTintList(new android.content.res.ColorStateList(states,
                    new int[]{DeekseepUi.BRAND, dark ? 0xFFCCCCCC : 0xFFFFFFFF}));
            toggle.setTrackTintList(new android.content.res.ColorStateList(states,
                    new int[]{0xFFADBFFF, dark ? 0xFF555555 : 0xFFBFBFBF}));
            toggle.setBackground(null);
        }

        private View divider() {
            View divider = new View(activity);
            divider.setBackgroundColor(dividerColor);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            params.setMargins(dp(16), 0, dp(16), 0);
            divider.setLayoutParams(params);
            return divider;
        }

        private LinearLayout.LayoutParams top(int top) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(top);
            return params;
        }
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override public final void onProgressChanged(SeekBar seekBar, int progress,
                                                      boolean fromUser) {
            changed(progress, fromUser);
        }

        @Override public final void onStartTrackingTouch(SeekBar seekBar) {
            started();
        }

        @Override public final void onStopTrackingTouch(SeekBar seekBar) {
            stopped();
        }

        public abstract void changed(int progress, boolean fromUser);
        public void started() {}
        public void stopped() {}
    }

    private static final class BubblePreview extends LinearLayout {
        final ChatAppearance.Config config;
        final boolean dark;
        String userDecorationFile = "";
        String assistantDecorationFile = "";
        Bitmap userDecoration;
        Bitmap assistantDecoration;

        BubblePreview(Context context, ChatAppearance.Config config, boolean dark) {
            super(context);
            this.config = config;
            this.dark = dark;
            setOrientation(VERTICAL);
            setPadding(dp(12), dp(10), dp(12), dp(10));
            setClipChildren(false);
            setClipToPadding(false);
            GradientDrawable background = new GradientDrawable();
            background.setColor(dark ? 0xFF1A1B1F : 0xFFF6F7FA);
            background.setCornerRadius(dp(14));
            background.setStroke(dp(1), dark ? 0xFF44464D : 0xFFE0E3EA);
            setBackground(background);
            refresh();
        }

        void refresh() {
            removeAllViews();
            setAlpha(config.bubbleEnabled ? 1f : 0.58f);
            addBubble(false,
                    UiLanguage.text(getContext(),
                            "可以分别给两边的消息换风格。",
                            "Each side can use its own style."));
            addBubble(true,
                    UiLanguage.text(getContext(),
                            "贴纸也会跟着每条气泡移动。",
                            "Decorations follow every bubble."));
        }

        private void addBubble(final boolean user, String text) {
            final ChatAppearance.BubbleStyle style = config.bubble(user);
            final FrameLayout row = new FrameLayout(getContext());
            row.setClipChildren(false);
            row.setClipToPadding(false);
            addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

            final TextView bubble = new TextView(getContext());
            bubble.setText(text);
            bubble.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            bubble.setTextColor(dark ? 0xFFF1F2F5 : 0xFF202126);
            bubble.setPadding(dp(13), dp(10), dp(13), dp(10));
            bubble.setMaxWidth(dp(270));
            bubble.setBackground(bubbleBackground(style, user));
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                bubble.setElevation(dp("liquid".equals(style.preset) ? 4f : 2f));
            }
            FrameLayout.LayoutParams bubbleParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    (user ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            bubbleParams.topMargin = dp(8);
            row.addView(bubble, bubbleParams);

            if (!style.hasDecoration()) return;
            final Bitmap bitmap = decorationBitmap(user, style.decorationFile);
            if (bitmap == null) return;
            final ImageView decoration = new ImageView(getContext());
            decoration.setImageBitmap(bitmap);
            decoration.setScaleType(ImageView.ScaleType.FIT_CENTER);
            decoration.setAlpha(style.decorationOpacity);
            decoration.setRotation(style.decorationRotation);
            final int size = dp(style.decorationSize);
            row.addView(decoration, new FrameLayout.LayoutParams(size, size));
            row.post(new Runnable() {
                @Override public void run() {
                    float available = Math.max(0f, bubble.getWidth() - size);
                    decoration.setX(bubble.getX() + available * style.decorationX);
                    decoration.setY(bubble.getY() - size * 0.42f);
                    decoration.bringToFront();
                }
            });
        }

        private GradientDrawable bubbleBackground(
                ChatAppearance.BubbleStyle style, boolean user) {
            GradientDrawable background;
            int fill;
            if ("original".equals(style.preset)) {
                fill = user
                        ? (dark ? 0xFF30466F : 0xFFE5ECFF)
                        : (dark ? 0xFF292A2E : 0xFFFFFFFF);
                background = new GradientDrawable();
                background.setColor(fill);
                background.setCornerRadius(dp(18));
                return background;
            }
            fill = ChatAppearance.bubbleFillColor(style, user, dark);
            if ("liquid".equals(style.preset)) {
                int highlight = mixWithWhite(fill, dark ? 0.18f : 0.34f);
                background = new GradientDrawable(
                        GradientDrawable.Orientation.TL_BR,
                        new int[]{highlight, fill});
            } else {
                background = new GradientDrawable();
                background.setColor(fill);
            }
            background.setCornerRadius(dp(style.radius));
            int border = ChatAppearance.bubbleBorderColor(style, user, dark);
            if (border != 0 && style.borderWidth > 0f) {
                background.setStroke(Math.max(1, dp(style.borderWidth)), border);
            }
            return background;
        }

        private Bitmap decorationBitmap(boolean user, String file) {
            if (file == null || file.length() == 0) return null;
            if (user) {
                if (!file.equals(userDecorationFile)) {
                    userDecorationFile = file;
                    userDecoration = ChatAppearance.loadBitmap(
                            ChatAppearance.assetFile(file), dp(144), dp(144));
                }
                return userDecoration;
            }
            if (!file.equals(assistantDecorationFile)) {
                assistantDecorationFile = file;
                assistantDecoration = ChatAppearance.loadBitmap(
                        ChatAppearance.assetFile(file), dp(144), dp(144));
            }
            return assistantDecoration;
        }

        private static int mixWithWhite(int color, float amount) {
            float mix = Math.max(0f, Math.min(1f, amount));
            int alpha = Color.alpha(color);
            int red = Math.round(Color.red(color) * (1f - mix) + 255f * mix);
            int green = Math.round(Color.green(color) * (1f - mix) + 255f * mix);
            int blue = Math.round(Color.blue(color) * (1f - mix) + 255f * mix);
            return Color.argb(alpha, red, green, blue);
        }

        private int dp(float value) {
            return DeekseepUi.dp(getContext(), value);
        }
    }

    private static final class EditorCanvas extends FrameLayout {
        interface Listener {
            void onStickerSelected(String id);
            void onStickerMoved(String id);
        }

        final ChatAppearance.Config config;
        final boolean dark;
        final Listener listener;
        String selectedStickerId;
        int generation;
        int motionPreviewDirection;
        FrameLayout wallpaperViewport;
        ImageView wallpaperView;
        Bitmap wallpaperBitmap;

        EditorCanvas(Context context, ChatAppearance.Config config, boolean dark,
                     Listener listener) {
            super(context);
            this.config = config;
            this.dark = dark;
            this.listener = listener;
            setClipChildren(false);
            setClipToPadding(false);
            GradientDrawable background = new GradientDrawable();
            background.setColor(dark ? 0xFF171719 : 0xFFFFFFFF);
            background.setStroke(DeekseepUi.dp(context, 1),
                    dark ? 0xFF4A4A50 : 0xFFD9DBE2);
            background.setCornerRadius(DeekseepUi.dp(context, 14));
            setBackground(background);
            setClipToOutline(true);
            post(new Runnable() {
                @Override public void run() { renderAll(); }
            });
        }

        void setSelectedSticker(String id) {
            selectedStickerId = id;
            updateSelectionBorders();
        }

        void renderAll() {
            final int renderGeneration = ++generation;
            removeAllViews();
            wallpaperViewport = null;
            wallpaperView = null;
            wallpaperBitmap = null;
            post(new Runnable() {
                @Override public void run() {
                    if (renderGeneration != generation || getWidth() <= 0 || getHeight() <= 0) {
                        return;
                    }
                    addBackground();
                    addGuide();
                    addStickers();
                }
            });
        }

        void applyBackground() {
            if (wallpaperView == null || wallpaperViewport == null) {
                renderAll();
                return;
            }
            applyMotionLayout(false);
        }

        void previewMotion(int direction, boolean animate) {
            motionPreviewDirection = direction < 0 ? -1 : (direction > 0 ? 1 : 0);
            applyMotionLayout(animate);
        }

        void applyMotionLayout(boolean animate) {
            if (wallpaperView == null || wallpaperViewport == null
                    || getWidth() <= 0 || getHeight() <= 0) {
                if (config.backgroundFile.length() > 0) renderAll();
                return;
            }
            int shift = Math.round(
                    getWidth() * config.maxMotionMagnitude());
            int viewportHeight = ChatAppearance.wallpaperViewportHeight(
                    getHeight(), config.backgroundExtent);
            int viewportTop = ChatAppearance.wallpaperViewportTop(
                    getHeight(), config.backgroundExtent);
            int[] canvas = ChatAppearance.wallpaperCanvasSize(
                    getWidth(), viewportHeight, shift, config.backgroundRotation);
            FrameLayout.LayoutParams viewportParams =
                    (FrameLayout.LayoutParams) wallpaperViewport.getLayoutParams();
            viewportParams.width = getWidth();
            viewportParams.height = viewportHeight;
            viewportParams.leftMargin = 0;
            viewportParams.topMargin = viewportTop;
            wallpaperViewport.setLayoutParams(viewportParams);
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) wallpaperView.getLayoutParams();
            params.width = canvas[0];
            params.height = canvas[1];
            params.leftMargin = Math.round((getWidth() - canvas[0]) / 2f);
            params.topMargin = Math.round((viewportHeight - canvas[1]) / 2f);
            wallpaperView.setLayoutParams(params);
            if (wallpaperBitmap != null) {
                ChatAppearance.applyWallpaperPresentation(
                        wallpaperView, wallpaperBitmap, config,
                        getWidth(), viewportHeight);
            }
            boolean settingsPreview = motionPreviewDirection < 0;
            float drawerProgress = motionPreviewDirection > 0 ? 1f : 0f;
            float target = getWidth()
                    * config.motionFraction(settingsPreview, drawerProgress);
            boolean bound = motionPreviewDirection > 0
                    ? config.backgroundOnSidebar
                    : (motionPreviewDirection < 0
                    ? config.backgroundOnSettings : config.backgroundOnChat);
            wallpaperViewport.setAlpha(bound ? config.backgroundOpacity : 0f);
            wallpaperView.animate().cancel();
            if (animate) {
                wallpaperView.animate()
                        .translationX(target)
                        .setDuration(440L)
                        .setInterpolator(new DecelerateInterpolator(2.4f))
                        .start();
            } else {
                wallpaperView.setTranslationX(target);
            }
        }

        void applySticker(String id) {
            ChatAppearance.Sticker sticker = config.sticker(id);
            if (sticker == null || getWidth() <= 0 || getHeight() <= 0) return;
            StickerNode node = findNode(id);
            if (node == null) {
                renderAll();
                return;
            }
            node.applyModel(sticker);
        }

        private void addBackground() {
            if (config.backgroundFile.length() == 0) return;
            int shift = Math.round(
                    getWidth() * config.maxMotionMagnitude());
            int viewportHeight = ChatAppearance.wallpaperViewportHeight(
                    getHeight(), config.backgroundExtent);
            int viewportTop = ChatAppearance.wallpaperViewportTop(
                    getHeight(), config.backgroundExtent);
            int[] canvas = ChatAppearance.wallpaperCanvasSize(
                    getWidth(), viewportHeight, shift, config.backgroundRotation);
            int canvasWidth = canvas[0];
            int canvasHeight = canvas[1];
            Bitmap bitmap = ChatAppearance.loadBitmap(
                    ChatAppearance.assetFile(config.backgroundFile),
                    canvasWidth, canvasHeight);
            if (bitmap == null) return;
            FrameLayout viewport = new FrameLayout(getContext());
            viewport.setClipChildren(true);
            viewport.setClipToPadding(true);
            viewport.setClickable(false);
            viewport.setFocusable(false);
            ImageView image = new ImageView(getContext());
            image.setContentDescription(t("背景图预览", "Wallpaper preview"));
            ChatAppearance.applyWallpaperPresentation(
                    image, bitmap, config, getWidth(), viewportHeight);
            FrameLayout.LayoutParams imageParams = new FrameLayout.LayoutParams(
                    canvasWidth, canvasHeight);
            imageParams.leftMargin = Math.round((getWidth() - canvasWidth) / 2f);
            imageParams.topMargin = Math.round((viewportHeight - canvasHeight) / 2f);
            viewport.addView(image, imageParams);
            FrameLayout.LayoutParams viewportParams = new FrameLayout.LayoutParams(
                    getWidth(), viewportHeight);
            viewportParams.topMargin = viewportTop;
            wallpaperViewport = viewport;
            wallpaperView = image;
            wallpaperBitmap = bitmap;
            addView(viewport, viewportParams);
            applyMotionLayout(false);
        }

        private void addGuide() {
            if (!config.hasVisuals()) {
                TextView empty = new TextView(getContext());
                empty.setText(t("导入一张图片开始设置", "Import an image to begin"));
                empty.setTextColor(dark ? 0xFF99999F : 0xFF85858B);
                empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                empty.setGravity(Gravity.CENTER);
                addView(empty, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                return;
            }
            int lineColor = dark ? 0x28FFFFFF : 0x22000000;
            View vertical = new View(getContext());
            vertical.setBackgroundColor(lineColor);
            FrameLayout.LayoutParams verticalParams = new FrameLayout.LayoutParams(
                    1, ViewGroup.LayoutParams.MATCH_PARENT);
            verticalParams.gravity = Gravity.CENTER_HORIZONTAL;
            addView(vertical, verticalParams);
            View horizontal = new View(getContext());
            horizontal.setBackgroundColor(lineColor);
            FrameLayout.LayoutParams horizontalParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            horizontalParams.gravity = Gravity.CENTER_VERTICAL;
            addView(horizontal, horizontalParams);
        }

        private void addStickers() {
            for (ChatAppearance.Sticker sticker : config.stickers) {
                StickerNode node = new StickerNode(getContext(), sticker, this);
                node.applyModel(sticker);
                addView(node);
            }
            updateSelectionBorders();
        }

        private StickerNode findNode(String id) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof StickerNode
                        && ((StickerNode) child).sticker.id.equals(id)) {
                    return (StickerNode) child;
                }
            }
            return null;
        }

        private void updateSelectionBorders() {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof StickerNode) {
                    StickerNode node = (StickerNode) child;
                    node.showSelectedBorder(node.sticker.id.equals(selectedStickerId));
                }
            }
        }

        void select(StickerNode node) {
            if (node == null) return;
            selectedStickerId = node.sticker.id;
            updateSelectionBorders();
            if (listener != null) listener.onStickerSelected(selectedStickerId);
        }

        void moved(StickerNode node) {
            if (node != null && listener != null) listener.onStickerMoved(node.sticker.id);
        }

        private String t(String chinese, String english) {
            return UiLanguage.text(getContext(), chinese, english);
        }
    }

    private static final class StickerNode extends FrameLayout {
        final ChatAppearance.Sticker sticker;
        final EditorCanvas canvas;
        final ImageView image;
        final View border;
        float touchRawX;
        float touchRawY;
        float startX;
        float startY;
        boolean moved;

        StickerNode(Context context, ChatAppearance.Sticker sticker, EditorCanvas canvas) {
            super(context);
            this.sticker = sticker;
            this.canvas = canvas;
            setClipChildren(false);
            setClipToPadding(false);
            setClickable(true);
            setFocusable(true);

            image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setContentDescription(UiLanguage.text(context, "贴纸", "Sticker"));
            addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            border = new View(context);
            addView(border, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View view, MotionEvent event) {
                    return handleTouch(event);
                }
            });
        }

        void applyModel(ChatAppearance.Sticker model) {
            int shortSide = Math.max(1, Math.min(canvas.getWidth(), canvas.getHeight()));
            int size = Math.max(1, Math.round(shortSide * model.size));
            ViewGroup.LayoutParams raw = getLayoutParams();
            FrameLayout.LayoutParams params = raw instanceof FrameLayout.LayoutParams
                    ? (FrameLayout.LayoutParams) raw
                    : new FrameLayout.LayoutParams(size, size);
            params.width = size;
            params.height = size;
            setLayoutParams(params);
            setX(model.x * canvas.getWidth() - size / 2f);
            setY(model.y * canvas.getHeight() - size / 2f);
            image.setAlpha(model.opacity);
            image.setRotation(model.rotation);
            if (image.getDrawable() == null) {
                Bitmap bitmap = ChatAppearance.loadBitmap(
                        ChatAppearance.assetFile(model.file), size, size);
                if (bitmap != null) image.setImageBitmap(bitmap);
            }
        }

        void showSelectedBorder(boolean selected) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(Color.TRANSPARENT);
            if (selected) {
                drawable.setStroke(DeekseepUi.dp(getContext(), 2), DeekseepUi.BRAND);
                drawable.setCornerRadius(DeekseepUi.dp(getContext(), 8));
            }
            border.setBackground(drawable);
        }

        private boolean handleTouch(MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    canvas.select(this);
                    getParent().requestDisallowInterceptTouchEvent(true);
                    touchRawX = event.getRawX();
                    touchRawY = event.getRawY();
                    startX = getX();
                    startY = getY();
                    moved = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - touchRawX;
                    float dy = event.getRawY() - touchRawY;
                    if (Math.abs(dx) > 2f || Math.abs(dy) > 2f) moved = true;
                    float centerX = clamp(startX + dx + getWidth() / 2f,
                            0f, canvas.getWidth());
                    float centerY = clamp(startY + dy + getHeight() / 2f,
                            0f, canvas.getHeight());
                    setX(centerX - getWidth() / 2f);
                    setY(centerY - getHeight() / 2f);
                    sticker.x = canvas.getWidth() <= 0 ? 0.5f : centerX / canvas.getWidth();
                    sticker.y = canvas.getHeight() <= 0 ? 0.5f : centerY / canvas.getHeight();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    getParent().requestDisallowInterceptTouchEvent(false);
                    if (moved) canvas.moved(this);
                    performClick();
                    return true;
                default:
                    return true;
            }
        }

        @Override public boolean performClick() {
            super.performClick();
            canvas.select(this);
            return true;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
