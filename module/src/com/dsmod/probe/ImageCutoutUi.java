package com.dsmod.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Offline sticker cutout editor.
 *
 * <p>Automatic removal estimates the dominant colors around the image border and flood-fills
 * only edge-connected pixels. Manual erase/restore brushes then edit the same alpha mask. No
 * image, mask or pixel sample leaves the device.</p>
 */
final class ImageCutoutUi {
    private static final int MAX_EDITOR_SIDE = 1400;

    private ImageCutoutUi() {}

    static void showSticker(
            Activity activity, String stickerId, Runnable onSaved) {
        if (activity == null || stickerId == null) return;
        ChatAppearance.Config config = ChatAppearance.load();
        ChatAppearance.Sticker sticker = config.sticker(stickerId);
        if (sticker == null) {
            toast(activity, "找不到要编辑的贴纸", "The sticker could not be found");
            return;
        }
        show(activity, ChatAppearance.assetFile(sticker.file),
                new SaveTarget(stickerId, false, false), onSaved);
    }

    static void showBubble(
            Activity activity, boolean user, Runnable onSaved) {
        if (activity == null) return;
        ChatAppearance.Config config = ChatAppearance.load();
        ChatAppearance.BubbleStyle bubble = config.bubble(user);
        if (bubble == null || !bubble.hasDecoration()) {
            toast(activity, "请先导入气泡贴纸", "Import a bubble decoration first");
            return;
        }
        show(activity, ChatAppearance.assetFile(bubble.decorationFile),
                new SaveTarget(null, true, user), onSaved);
    }

    private static void show(
            Activity activity, File file, SaveTarget target, Runnable onSaved) {
        Bitmap decoded = ChatAppearance.loadBitmap(file, MAX_EDITOR_SIDE, MAX_EDITOR_SIDE);
        if (decoded == null) {
            toast(activity, "无法读取贴纸图片", "Could not read the sticker image");
            return;
        }
        Bitmap prepared = limitBitmap(decoded, MAX_EDITOR_SIDE);
        if (prepared != decoded) decoded.recycle();
        try {
            new Editor(activity, prepared, target, onSaved).show();
        } catch (Throwable t) {
            prepared.recycle();
            Main.log("cutout editor open failed: " + t);
            toast(activity, "抠图编辑器打开失败", "Could not open the cutout editor");
        }
    }

    private static Bitmap limitBitmap(Bitmap source, int maxSide) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longest = Math.max(width, height);
        if (longest <= maxSide) return source;
        float scale = maxSide / (float) longest;
        return Bitmap.createScaledBitmap(
                source, Math.max(1, Math.round(width * scale)),
                Math.max(1, Math.round(height * scale)), true);
    }

    private static final class SaveTarget {
        final String stickerId;
        final boolean bubble;
        final boolean user;

        SaveTarget(String stickerId, boolean bubble, boolean user) {
            this.stickerId = stickerId;
            this.bubble = bubble;
            this.user = user;
        }
    }

    private static final class Editor {
        final Activity activity;
        final SaveTarget target;
        final Runnable onSaved;
        final boolean dark;
        final Dialog dialog;
        final LinearLayout root;
        final LinearLayout header;
        final FrameLayout canvasFrame;
        final HorizontalScrollView toolScroll;
        final LinearLayout bottom;
        final CutoutCanvas canvas;
        final TextView fullscreenButton;
        final TextView fullscreenExitButton;
        final TextView fullscreenBrushButton;
        final TextView brushButton;
        final TextView autoButton;
        final TextView undoButton;
        final TextView saveButton;
        TextView brushLabel;
        PopupWindow brushPopup;
        boolean fullscreen;
        volatile boolean closed;
        volatile boolean busy;

        Editor(
                Activity activity, Bitmap bitmap,
                SaveTarget target, Runnable onSaved) {
            this.activity = activity;
            this.target = target;
            this.onSaved = onSaved;
            dark = DeekseepUi.isDark(activity);
            dialog = new Dialog(activity);
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

            root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(10), dp(8), dp(10), dp(10));
            applyRootBackground();

            header = new LinearLayout(activity);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setOrientation(LinearLayout.HORIZONTAL);
            root.addView(header, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

            TextView close = textButton("×", false);
            close.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
            close.setContentDescription(t("关闭", "Close"));
            close.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (!busy) dialog.dismiss();
                }
            });
            header.addView(close, new LinearLayout.LayoutParams(dp(44), dp(42)));

            TextView title = new TextView(activity);
            title.setText(t("贴纸抠图", "Sticker cutout"));
            title.setTextColor(dark ? 0xFFF4F5F7 : 0xFF17181B);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            LinearLayout.LayoutParams titleParams =
                    new LinearLayout.LayoutParams(0, dp(42), 1f);
            titleParams.leftMargin = dp(8);
            header.addView(title, titleParams);

            fullscreenButton = edgeIconButton("\u26F6");
            fullscreenButton.setContentDescription(t("全屏", "Full screen"));
            fullscreenButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    setFullscreen(!fullscreen);
                }
            });
            header.addView(fullscreenButton, new LinearLayout.LayoutParams(
                    dp(44), dp(42)));

            canvas = new CutoutCanvas(activity, bitmap);
            canvasFrame = new FrameLayout(activity);
            applyCanvasBackground();
            canvasFrame.setClipToOutline(Build.VERSION.SDK_INT >= 21);
            canvasFrame.addView(canvas, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            undoButton = edgeIconButton("↶");
            undoButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            undoButton.setPadding(0, 0, 0, 0);
            undoButton.setContentDescription(t("撤销", "Undo"));
            undoButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    canvas.undo();
                    updateToolState();
                }
            });
            FrameLayout.LayoutParams undoParams =
                    new FrameLayout.LayoutParams(dp(42), dp(42));
            undoParams.gravity = Gravity.TOP | Gravity.RIGHT;
            undoParams.topMargin = dp(8);
            undoParams.rightMargin = dp(8);
            canvasFrame.addView(undoButton, undoParams);

            fullscreenExitButton = edgeIconButton("×");
            fullscreenExitButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
            fullscreenExitButton.setContentDescription(
                    t("退出全屏", "Exit full screen"));
            fullscreenExitButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    setFullscreen(false);
                }
            });
            FrameLayout.LayoutParams exitParams =
                    new FrameLayout.LayoutParams(dp(42), dp(42));
            exitParams.gravity = Gravity.TOP | Gravity.LEFT;
            exitParams.topMargin = dp(8);
            exitParams.leftMargin = dp(8);
            canvasFrame.addView(fullscreenExitButton, exitParams);

            fullscreenBrushButton = edgeIconButton("✎");
            fullscreenBrushButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            fullscreenBrushButton.setContentDescription(t("画笔", "Brush"));
            fullscreenBrushButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    showBrushMenu(view);
                }
            });
            FrameLayout.LayoutParams sideBrushParams =
                    new FrameLayout.LayoutParams(dp(46), dp(52));
            sideBrushParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            sideBrushParams.rightMargin = dp(8);
            canvasFrame.addView(fullscreenBrushButton, sideBrushParams);

            LinearLayout.LayoutParams canvasParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            canvasParams.topMargin = dp(6);
            root.addView(canvasFrame, canvasParams);

            toolScroll = new HorizontalScrollView(activity);
            toolScroll.setHorizontalScrollBarEnabled(false);
            LinearLayout tools = new LinearLayout(activity);
            tools.setOrientation(LinearLayout.HORIZONTAL);
            tools.setGravity(Gravity.CENTER_VERTICAL);
            toolScroll.addView(tools, new HorizontalScrollView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams toolScrollParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
            toolScrollParams.topMargin = dp(7);
            root.addView(toolScroll, toolScrollParams);

            autoButton = textButton("一键抠图", true);
            autoButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    runAutomaticCutout();
                }
            });
            tools.addView(autoButton);

            brushButton = edgeIconButton("✎");
            brushButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
            brushButton.setContentDescription(t("画笔", "Brush"));
            brushButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    showBrushMenu(view);
                }
            });
            tools.addView(brushButton, left(7));

            bottom = new LinearLayout(activity);
            bottom.setOrientation(LinearLayout.HORIZONTAL);
            bottom.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams bottomParams =
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(50));
            bottomParams.topMargin = dp(2);
            root.addView(bottom, bottomParams);

            TextView cancel = textButton("取消", false);
            cancel.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (!busy) dialog.dismiss();
                }
            });
            bottom.addView(cancel, new LinearLayout.LayoutParams(
                    0, dp(44), 1f));

            saveButton = textButton("保存透明贴纸", true);
            saveButton.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    save();
                }
            });
            LinearLayout.LayoutParams saveParams =
                    new LinearLayout.LayoutParams(0, dp(44), 1.3f);
            saveParams.leftMargin = dp(8);
            bottom.addView(saveButton, saveParams);

            dialog.setContentView(root);
            dialog.setCanceledOnTouchOutside(false);
            dialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override public boolean onKey(
                        DialogInterface ignored, int keyCode, KeyEvent event) {
                    if (fullscreen && keyCode == KeyEvent.KEYCODE_BACK
                            && event.getAction() == KeyEvent.ACTION_UP) {
                        setFullscreen(false);
                        return true;
                    }
                    return false;
                }
            });
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override public void onDismiss(DialogInterface ignored) {
                    closed = true;
                    dismissBrushMenu();
                    canvas.release();
                }
            });
            canvas.setHistoryChangedListener(new Runnable() {
                @Override public void run() {
                    updateToolState();
                }
            });
            updateToolState();
            setFullscreen(false);
        }

        void show() {
            dialog.show();
            setFullscreen(false);
        }

        private void setFullscreen(boolean enabled) {
            fullscreen = enabled;
            dismissBrushMenu();
            fullscreenButton.setContentDescription(t(
                    enabled ? "退出全屏" : "全屏",
                    enabled ? "Exit full screen" : "Full screen"));
            header.setVisibility(enabled ? View.GONE : View.VISIBLE);
            toolScroll.setVisibility(enabled ? View.GONE : View.VISIBLE);
            bottom.setVisibility(enabled ? View.GONE : View.VISIBLE);
            fullscreenExitButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
            fullscreenBrushButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
            root.setPadding(enabled ? 0 : dp(10), enabled ? 0 : dp(8),
                    enabled ? 0 : dp(10), enabled ? 0 : dp(10));
            ViewGroup.LayoutParams rawCanvasParams = canvasFrame.getLayoutParams();
            if (rawCanvasParams instanceof LinearLayout.LayoutParams) {
                LinearLayout.LayoutParams canvasParams =
                        (LinearLayout.LayoutParams) rawCanvasParams;
                canvasParams.topMargin = enabled ? 0 : dp(6);
                canvasFrame.setLayoutParams(canvasParams);
            }
            applyCanvasBackground();
            applyRootBackground();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.55f);
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                if (enabled) {
                    window.setGravity(Gravity.FILL);
                    window.setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                } else {
                    int width = activity.getResources().getDisplayMetrics().widthPixels;
                    int height = activity.getResources().getDisplayMetrics().heightPixels;
                    window.setLayout(
                            Math.round(width * 0.94f),
                            Math.round(height * 0.84f));
                    window.setGravity(Gravity.CENTER);
                }
            }
            root.requestLayout();
            canvas.requestLayout();
            canvas.invalidate();
        }

        private void applyRootBackground() {
            GradientDrawable background = new GradientDrawable();
            background.setColor(dark ? 0xFF202126 : 0xFFF9FAFC);
            background.setCornerRadius(fullscreen ? 0f : dp(20));
            if (!fullscreen) {
                background.setStroke(
                        dp(1), dark ? 0xFF444750 : 0xFFE1E4EA);
            }
            root.setBackground(background);
        }

        private void applyCanvasBackground() {
            GradientDrawable background = new GradientDrawable();
            background.setColor(dark ? 0xFF121316 : 0xFFE7E9EE);
            background.setCornerRadius(fullscreen ? 0f : dp(14));
            canvasFrame.setBackground(background);
        }

        private void runAutomaticCutout() {
            if (busy) return;
            busy = true;
            canvas.saveUndo();
            setBusyUi(true, t("正在识别背景…", "Detecting background…"));
            new Thread(new Runnable() {
                @Override public void run() {
                    final byte[] mask = canvas.automaticMask();
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (closed) return;
                            busy = false;
                            if (mask == null) {
                                setBusyUi(false, null);
                                toast(activity,
                                        "一键抠图失败，可继续手动擦除",
                                        "Automatic cutout failed; manual erase is still available");
                                return;
                            }
                            canvas.applyMask(mask);
                            setBusyUi(false, null);
                            updateToolState();
                        }
                    });
                }
            }, "Deekseep-cutout-auto").start();
        }

        private void save() {
            if (busy) return;
            busy = true;
            final Bitmap result = canvas.buildResultBitmap();
            if (result == null) {
                busy = false;
                toast(activity, "没有可保存的结果", "There is no result to save");
                return;
            }
            setBusyUi(true, t("正在保存…", "Saving…"));
            new Thread(new Runnable() {
                @Override public void run() {
                    final ChatAppearance.ImportResult saved = target.bubble
                            ? ChatAppearance.saveBubbleCutout(target.user, result)
                            : ChatAppearance.saveStickerCutout(
                                    target.stickerId, result);
                    result.recycle();
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            if (closed) return;
                            busy = false;
                            toast(activity, saved.message, saved.message);
                            if (saved.ok) {
                                dialog.dismiss();
                                if (onSaved != null) onSaved.run();
                            } else {
                                setBusyUi(false, null);
                            }
                        }
                    });
                }
            }, "Deekseep-cutout-save").start();
        }

        private void setBusyUi(boolean value, String label) {
            autoButton.setEnabled(!value);
            brushButton.setEnabled(!value);
            fullscreenBrushButton.setEnabled(!value);
            undoButton.setEnabled(!value && canvas.canUndo());
            saveButton.setEnabled(!value);
            if (value) dismissBrushMenu();
            if (label != null) saveButton.setText(label);
            else saveButton.setText(t("保存透明贴纸", "Save transparent sticker"));
        }

        private void updateToolState() {
            undoButton.setEnabled(!busy && canvas.canUndo());
        }

        private String viewModeLabel() {
            if (canvas.getViewMode() == 1) {
                return t("仅涂抹区", "Painted only");
            }
            if (canvas.getViewMode() == 2) {
                return t("仅未涂抹区", "Unpainted only");
            }
            return t("最终结果", "Final result");
        }

        private void showBrushMenu(View anchor) {
            if (busy) return;
            dismissBrushMenu();

            LinearLayout menu = new LinearLayout(activity);
            menu.setOrientation(LinearLayout.VERTICAL);
            menu.setPadding(dp(4), dp(5), dp(4), dp(7));
            GradientDrawable menuBackground = new GradientDrawable();
            menuBackground.setColor(dark ? 0xFF25262A : 0xFFFAFAFB);
            menuBackground.setCornerRadius(dp(8));
            menuBackground.setStroke(
                    dp(1), dark ? 0xFF4B4D53 : 0xFFD9DADF);
            menu.setBackground(menuBackground);

            menu.addView(simpleMenuRow(
                    t("擦除", "Erase"), canvas.isErase(), new Runnable() {
                        @Override public void run() {
                            canvas.setErase(true);
                            dismissBrushMenu();
                            updateToolState();
                        }
                    }));
            menu.addView(menuDivider());
            menu.addView(simpleMenuRow(
                    t("还原", "Restore"), !canvas.isErase(), new Runnable() {
                        @Override public void run() {
                            canvas.setErase(false);
                            dismissBrushMenu();
                            updateToolState();
                        }
                    }));
            menu.addView(menuDivider());
            menu.addView(simpleMenuRow(
                    t("显示范围", "Display area") + "  ·  " + viewModeLabel(),
                    false, new Runnable() {
                        @Override public void run() {
                            dismissBrushMenu();
                            showViewModeChooser();
                        }
                    }));
            menu.addView(menuDivider());
            menu.addView(simpleMenuRow(
                    t("画笔粗细", "Brush size") + "  ·  "
                            + Math.round(canvas.getBrushDp()) + " dp",
                    false, new Runnable() {
                        @Override public void run() {
                            dismissBrushMenu();
                            showBrushSizeDialog();
                        }
                    }));

            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int popupWidth = Math.min(dp(286), screenWidth - dp(28));
            final PopupWindow popup = new PopupWindow(
                    menu, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popup.setOutsideTouchable(true);
            popup.setClippingEnabled(true);
            if (Build.VERSION.SDK_INT >= 21) popup.setElevation(dp(8));
            popup.setOnDismissListener(new PopupWindow.OnDismissListener() {
                @Override public void onDismiss() {
                    if (brushPopup == popup) brushPopup = null;
                    brushLabel = null;
                    canvas.setBrushPreview(false);
                }
            });
            brushPopup = popup;
            if (fullscreen) {
                // The brush button sits on the right edge; open below it instead of covering the
                // canvas centre (and therefore keep the live brush-size cursor visible).
                popup.showAsDropDown(anchor,
                        -popupWidth + anchor.getWidth(), dp(8));
            } else {
                popup.showAtLocation(
                        root, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, 0, dp(66));
            }
        }

        private void showBrushSizeDialog() {
            final LinearLayout body = new LinearLayout(activity);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(22), dp(4), dp(22), dp(2));
            final TextView value = new TextView(activity);
            value.setText(t("画笔粗细：", "Brush size: ")
                    + Math.round(canvas.getBrushDp()) + " dp");
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            body.addView(value);
            final SeekBar size = new SeekBar(activity);
            size.setMax(116);
            size.setProgress(Math.round(canvas.getBrushDp() - 4f));
            size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress,
                                                         boolean fromUser) {
                    canvas.setBrushDp(4f + progress);
                    value.setText(t("画笔粗细：", "Brush size: ")
                            + Math.round(canvas.getBrushDp()) + " dp");
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {
                    canvas.setBrushPreview(true);
                }
                @Override public void onStopTrackingTouch(SeekBar seekBar) {
                    canvas.setBrushPreview(false);
                }
            });
            body.addView(size, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            final AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle(t("画笔粗细", "Brush size"))
                    .setView(body)
                    .setPositiveButton(t("完成", "Done"), null)
                    .create();
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override public void onDismiss(DialogInterface ignored) {
                    canvas.setBrushPreview(false);
                }
            });
            dialog.show();
            Window window = dialog.getWindow();
            if (window != null) {
                window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = Math.min(activity.getResources().getDisplayMetrics().widthPixels
                        - dp(28), dp(420));
                params.y = dp(82);
                window.setAttributes(params);
            }
        }

        private void dismissBrushMenu() {
            PopupWindow popup = brushPopup;
            brushPopup = null;
            brushLabel = null;
            canvas.setBrushPreview(false);
            if (popup != null && popup.isShowing()) popup.dismiss();
        }

        private void showViewModeChooser() {
            final String[] labels = {
                    t("最终结果", "Final result"),
                    t("仅涂抹区", "Painted only"),
                    t("仅未涂抹区", "Unpainted only")
            };
            new AlertDialog.Builder(activity)
                    .setTitle(t("显示范围", "Display area"))
                    .setSingleChoiceItems(labels, canvas.getViewMode(),
                            new DialogInterface.OnClickListener() {
                                @Override public void onClick(
                                        DialogInterface chooser, int which) {
                                    canvas.setViewMode(which);
                                    updateToolState();
                                    chooser.dismiss();
                                }
                            })
                    .setNegativeButton(t("取消", "Cancel"), null)
                    .show();
        }

        private TextView simpleMenuRow(
                String label, boolean selected, final Runnable action) {
            TextView row = new TextView(activity);
            row.setText(label + (selected ? "   ✓" : ""));
            row.setTextColor(dark ? 0xFFF0F1F3 : 0xFF202126);
            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinHeight(dp(46));
            row.setPadding(dp(14), dp(8), dp(14), dp(8));
            row.setClickable(true);
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) {
                    if (action != null) action.run();
                }
            });
            return row;
        }

        private View menuDivider() {
            View divider = new View(activity);
            divider.setBackgroundColor(dark ? 0xFF3B3D42 : 0xFFE2E3E7);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            params.leftMargin = dp(12);
            params.rightMargin = dp(12);
            divider.setLayoutParams(params);
            return divider;
        }

        private TextView textButton(String value, boolean primary) {
            TextView button = new TextView(activity);
            button.setText(t(value, english(value)));
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            button.setGravity(Gravity.CENTER);
            button.setMinHeight(dp(40));
            button.setPadding(dp(12), dp(7), dp(12), dp(7));
            button.setTextColor(primary
                    ? Color.WHITE : (dark ? 0xFFE7E8EB : 0xFF30333A));
            GradientDrawable background = new GradientDrawable();
            background.setColor(primary
                    ? DeekseepUi.BRAND : (dark ? 0xFF303239 : 0xFFEEF0F4));
            background.setCornerRadius(dp(12));
            button.setBackground(background);
            button.setClickable(true);
            return button;
        }

        private TextView edgeIconButton(String glyph) {
            TextView button = new TextView(activity);
            button.setText(glyph);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            button.setGravity(Gravity.CENTER);
            button.setPadding(0, 0, 0, 0);
            button.setTextColor(dark ? 0xFFF2F3F5 : 0xFF24262B);
            GradientDrawable background = new GradientDrawable();
            background.setColor(dark ? 0xD82C2D31 : 0xEAF7F7F8);
            background.setCornerRadius(dp(9));
            background.setStroke(
                    dp(1), dark ? 0xFF55575D : 0xFFD5D7DC);
            button.setBackground(background);
            button.setClickable(true);
            return button;
        }

        private LinearLayout.LayoutParams left(int dp) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            params.leftMargin = dp(dp);
            return params;
        }

        private int dp(float value) {
            return Math.round(value
                    * activity.getResources().getDisplayMetrics().density);
        }

        private String t(String chinese, String english) {
            return UiLanguage.text(activity, chinese, english);
        }

        private String english(String chinese) {
            return UiLanguageCatalog.toEnglish(chinese);
        }
    }

    private static final class CutoutCanvas extends View {
        private final int imageWidth;
        private final int imageHeight;
        private final int[] sourcePixels;
        private final int[] resultPixels;
        private final int[] removedPixels;
        private final byte[] mask;
        private final Bitmap resultBitmap;
        private final Bitmap removedBitmap;
        private final Paint bitmapPaint = new Paint(
                Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint checkerLight = new Paint();
        private final Paint checkerDark = new Paint();
        private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF destination = new RectF();
        private final ArrayList<byte[]> undo = new ArrayList<>();
        private float baseFitScale = 1f;
        private float fitScale = 1f;
        private float userScale = 1f;
        private float panX;
        private float panY;
        private float brushDp = 36f;
        private boolean erase = true;
        private int viewMode;
        private float previousImageX;
        private float previousImageY;
        private float cursorX;
        private float cursorY;
        private boolean drawing;
        private boolean brushPreview;
        private boolean scalingGesture;
        private boolean suppressPaintUntilUp;
        private float previousSpan;
        private float previousFocusX;
        private float previousFocusY;
        private byte[] strokeBefore;
        private boolean strokeChanged;
        private Runnable historyChangedListener;
        private boolean released;

        CutoutCanvas(Activity activity, Bitmap source) {
            super(activity);
            setFocusable(true);
            setClickable(true);
            imageWidth = source.getWidth();
            imageHeight = source.getHeight();
            int count = imageWidth * imageHeight;
            sourcePixels = new int[count];
            resultPixels = new int[count];
            removedPixels = new int[count];
            mask = new byte[count];
            source.getPixels(
                    sourcePixels, 0, imageWidth,
                    0, 0, imageWidth, imageHeight);
            Arrays.fill(mask, (byte) 0xFF);
            resultBitmap = Bitmap.createBitmap(
                    imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
            removedBitmap = Bitmap.createBitmap(
                    imageWidth, imageHeight, Bitmap.Config.ARGB_8888);
            checkerLight.setColor(0xFFE3E5E9);
            checkerDark.setColor(0xFFBEC2C9);
            cursorPaint.setStyle(Paint.Style.STROKE);
            cursorPaint.setStrokeWidth(Math.max(2f,
                    activity.getResources().getDisplayMetrics().density));
            rebuild(0, 0, imageWidth, imageHeight);
            source.recycle();
        }

        void setErase(boolean value) {
            erase = value;
        }

        boolean isErase() {
            return erase;
        }

        void setBrushDp(float value) {
            brushDp = Math.max(4f, Math.min(120f, value));
            invalidate();
        }

        float getBrushDp() {
            return brushDp;
        }

        void setBrushPreview(boolean value) {
            brushPreview = value;
            invalidate();
        }

        void setHistoryChangedListener(Runnable listener) {
            historyChangedListener = listener;
        }

        void setViewMode(int value) {
            viewMode = Math.max(0, Math.min(2, value));
            invalidate();
        }

        int getViewMode() {
            return viewMode;
        }

        boolean canUndo() {
            return !undo.isEmpty();
        }

        void saveUndo() {
            if (released) return;
            pushUndoSnapshot(mask.clone());
        }

        void undo() {
            if (undo.isEmpty() || released) return;
            byte[] previous = undo.remove(undo.size() - 1);
            System.arraycopy(previous, 0, mask, 0, mask.length);
            rebuild(0, 0, imageWidth, imageHeight);
            invalidate();
            notifyHistoryChanged();
        }

        byte[] automaticMask() {
            if (released) return null;
            try {
                return detectBackgroundMask(sourcePixels, imageWidth, imageHeight);
            } catch (Throwable t) {
                Main.log("automatic cutout failed: " + t);
                return null;
            }
        }

        void applyMask(byte[] value) {
            if (released || value == null || value.length != mask.length) return;
            System.arraycopy(value, 0, mask, 0, mask.length);
            rebuild(0, 0, imageWidth, imageHeight);
            invalidate();
        }

        Bitmap buildResultBitmap() {
            if (released) return null;
            rebuild(0, 0, imageWidth, imageHeight);
            return Bitmap.createBitmap(
                    resultPixels, imageWidth, imageHeight,
                    Bitmap.Config.ARGB_8888);
        }

        void release() {
            released = true;
            undo.clear();
            strokeBefore = null;
            historyChangedListener = null;
            if (!resultBitmap.isRecycled()) resultBitmap.recycle();
            if (!removedBitmap.isRecycled()) removedBitmap.recycle();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (released) return;
            calculateDestination();
            drawChecker(canvas);
            if (viewMode == 1) {
                canvas.drawColor(0x16000000);
                canvas.drawBitmap(removedBitmap, null, destination, bitmapPaint);
            } else {
                if (viewMode == 2) {
                    Paint surround = new Paint();
                    surround.setColor(0x66000000);
                    canvas.drawRect(0f, 0f, getWidth(), getHeight(), surround);
                    drawChecker(canvas);
                }
                canvas.drawBitmap(resultBitmap, null, destination, bitmapPaint);
            }
            if (drawing) drawBrushCursor(canvas, cursorX, cursorY);
            else if (brushPreview) {
                drawBrushCursor(
                        canvas, getWidth() * 0.5f, getHeight() * 0.5f);
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (released) return false;
            calculateDestination();
            int action = event.getActionMasked();
            cursorX = event.getX();
            cursorY = event.getY();
            float imageX = imageX(cursorX);
            float imageY = imageY(cursorY);
            boolean inside = imageX >= 0f && imageX < imageWidth
                    && imageY >= 0f && imageY < imageHeight;
            if (action == MotionEvent.ACTION_DOWN) {
                if (!inside) return false;
                disallowParentIntercept(true);
                scalingGesture = false;
                suppressPaintUntilUp = false;
                previousSpan = 0f;
                drawing = true;
                strokeBefore = mask.clone();
                strokeChanged = false;
                previousImageX = imageX;
                previousImageY = imageY;
                paintStroke(imageX, imageY, imageX, imageY);
                strokeChanged = true;
                invalidate();
                return true;
            }

            if (action == MotionEvent.ACTION_POINTER_DOWN
                    && event.getPointerCount() >= 2) {
                cancelStrokeForPinch();
                scalingGesture = true;
                suppressPaintUntilUp = true;
                previousSpan = pointerSpan(event);
                previousFocusX = pointerFocusX(event);
                previousFocusY = pointerFocusY(event);
                disallowParentIntercept(true);
                invalidate();
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE
                    && (scalingGesture || event.getPointerCount() >= 2)) {
                if (event.getPointerCount() >= 2) updatePinch(event);
                return true;
            }

            if (action == MotionEvent.ACTION_MOVE
                    && drawing && !suppressPaintUntilUp) {
                if (inside) {
                    paintStroke(
                            previousImageX, previousImageY, imageX, imageY);
                    previousImageX = imageX;
                    previousImageY = imageY;
                    strokeChanged = true;
                }
                invalidate();
                return true;
            }

            if (action == MotionEvent.ACTION_POINTER_UP) {
                scalingGesture = true;
                suppressPaintUntilUp = true;
                previousSpan = 0f;
                invalidate();
                return true;
            }

            if (action == MotionEvent.ACTION_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                finishStroke();
                scalingGesture = false;
                suppressPaintUntilUp = false;
                previousSpan = 0f;
                disallowParentIntercept(false);
                invalidate();
                return true;
            }
            return drawing || scalingGesture;
        }

        private void calculateDestination() {
            float availableWidth = Math.max(1f, getWidth());
            float availableHeight = Math.max(1f, getHeight());
            baseFitScale = Math.min(
                    availableWidth / imageWidth,
                    availableHeight / imageHeight);
            fitScale = baseFitScale * userScale;
            float width = imageWidth * fitScale;
            float height = imageHeight * fitScale;
            float maxPanX = Math.max(0f, (width - availableWidth) * 0.5f);
            float maxPanY = Math.max(0f, (height - availableHeight) * 0.5f);
            panX = clamp(panX, -maxPanX, maxPanX);
            panY = clamp(panY, -maxPanY, maxPanY);
            float left = (availableWidth - width) * 0.5f + panX;
            float top = (availableHeight - height) * 0.5f + panY;
            destination.set(left, top, left + width, top + height);
        }

        private void updatePinch(MotionEvent event) {
            float span = pointerSpan(event);
            float focusX = pointerFocusX(event);
            float focusY = pointerFocusY(event);
            if (span <= 0f) return;
            if (previousSpan <= 0f) {
                previousSpan = span;
                previousFocusX = focusX;
                previousFocusY = focusY;
                return;
            }

            calculateDestination();
            float anchorImageX = imageX(previousFocusX);
            float anchorImageY = imageY(previousFocusY);
            float nextUserScale = clamp(
                    userScale * span / previousSpan, 1f, 6f);
            float nextFitScale = baseFitScale * nextUserScale;
            float centeredLeft =
                    (getWidth() - imageWidth * nextFitScale) * 0.5f;
            float centeredTop =
                    (getHeight() - imageHeight * nextFitScale) * 0.5f;
            panX = focusX - centeredLeft - anchorImageX * nextFitScale;
            panY = focusY - centeredTop - anchorImageY * nextFitScale;
            userScale = nextUserScale;
            calculateDestination();
            previousSpan = span;
            previousFocusX = focusX;
            previousFocusY = focusY;
            invalidate();
        }

        private void cancelStrokeForPinch() {
            if (strokeBefore != null) {
                System.arraycopy(
                        strokeBefore, 0, mask, 0, mask.length);
                rebuild(0, 0, imageWidth, imageHeight);
            }
            strokeBefore = null;
            strokeChanged = false;
            drawing = false;
        }

        private void finishStroke() {
            if (drawing && strokeChanged && strokeBefore != null) {
                pushUndoSnapshot(strokeBefore);
            }
            strokeBefore = null;
            strokeChanged = false;
            drawing = false;
        }

        private void pushUndoSnapshot(byte[] snapshot) {
            if (released || snapshot == null || snapshot.length != mask.length) {
                return;
            }
            if (undo.size() >= 12) undo.remove(0);
            undo.add(snapshot);
            notifyHistoryChanged();
        }

        private void notifyHistoryChanged() {
            if (historyChangedListener != null) historyChangedListener.run();
        }

        private void disallowParentIntercept(boolean disallow) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(disallow);
            }
        }

        private float imageX(float viewX) {
            return (viewX - destination.left) / Math.max(0.0001f, fitScale);
        }

        private float imageY(float viewY) {
            return (viewY - destination.top) / Math.max(0.0001f, fitScale);
        }

        private static float pointerSpan(MotionEvent event) {
            if (event.getPointerCount() < 2) return 0f;
            return (float) Math.hypot(
                    event.getX(1) - event.getX(0),
                    event.getY(1) - event.getY(0));
        }

        private static float pointerFocusX(MotionEvent event) {
            return event.getPointerCount() < 2
                    ? event.getX() : (event.getX(0) + event.getX(1)) * 0.5f;
        }

        private static float pointerFocusY(MotionEvent event) {
            return event.getPointerCount() < 2
                    ? event.getY() : (event.getY(0) + event.getY(1)) * 0.5f;
        }

        private void drawBrushCursor(Canvas canvas, float x, float y) {
            float density = getResources().getDisplayMetrics().density;
            float radius = brushDp * density * 0.5f;
            cursorPaint.setStyle(Paint.Style.STROKE);
            cursorPaint.setStrokeWidth(Math.max(2f, density));
            cursorPaint.setColor(erase ? 0xFFFF4D67 : 0xFF45D483);
            canvas.drawCircle(x, y, radius, cursorPaint);
            cursorPaint.setColor(Color.WHITE);
            cursorPaint.setStrokeWidth(Math.max(1f, density * 0.55f));
            canvas.drawCircle(x, y, Math.max(1f, radius - density * 1.5f),
                    cursorPaint);
            cursorPaint.setStrokeWidth(Math.max(2f, density));
        }

        private void drawChecker(Canvas canvas) {
            float size = Math.max(8f,
                    12f * getResources().getDisplayMetrics().density);
            int left = (int) Math.floor(destination.left / size);
            int top = (int) Math.floor(destination.top / size);
            int right = (int) Math.ceil(destination.right / size);
            int bottom = (int) Math.ceil(destination.bottom / size);
            for (int y = top; y < bottom; y++) {
                for (int x = left; x < right; x++) {
                    float l = Math.max(destination.left, x * size);
                    float t = Math.max(destination.top, y * size);
                    float r = Math.min(destination.right, (x + 1) * size);
                    float b = Math.min(destination.bottom, (y + 1) * size);
                    canvas.drawRect(l, t, r, b,
                            ((x + y) & 1) == 0 ? checkerLight : checkerDark);
                }
            }
        }

        private void paintStroke(float fromX, float fromY, float toX, float toY) {
            float density = getResources().getDisplayMetrics().density;
            float radius = Math.max(1.5f, brushDp * density * 0.5f / fitScale);
            float distance = (float) Math.hypot(toX - fromX, toY - fromY);
            int steps = Math.max(1, (int) Math.ceil(distance
                    / Math.max(1f, radius * 0.34f)));
            int dirtyLeft = imageWidth;
            int dirtyTop = imageHeight;
            int dirtyRight = 0;
            int dirtyBottom = 0;
            for (int step = 0; step <= steps; step++) {
                float fraction = step / (float) steps;
                float cx = fromX + (toX - fromX) * fraction;
                float cy = fromY + (toY - fromY) * fraction;
                int left = Math.max(0, (int) Math.floor(cx - radius - 1f));
                int top = Math.max(0, (int) Math.floor(cy - radius - 1f));
                int right = Math.min(
                        imageWidth, (int) Math.ceil(cx + radius + 1f));
                int bottom = Math.min(
                        imageHeight, (int) Math.ceil(cy + radius + 1f));
                dirtyLeft = Math.min(dirtyLeft, left);
                dirtyTop = Math.min(dirtyTop, top);
                dirtyRight = Math.max(dirtyRight, right);
                dirtyBottom = Math.max(dirtyBottom, bottom);
                float radiusSquared = radius * radius;
                for (int y = top; y < bottom; y++) {
                    float dy = y + 0.5f - cy;
                    for (int x = left; x < right; x++) {
                        float dx = x + 0.5f - cx;
                        float squared = dx * dx + dy * dy;
                        if (squared > radiusSquared) continue;
                        float normalized = (float) Math.sqrt(squared) / radius;
                        float coverage = clamp((1f - normalized) * 4.2f, 0f, 1f);
                        int index = y * imageWidth + x;
                        int old = mask[index] & 0xFF;
                        int target = erase ? 0 : 255;
                        int next = Math.round(old + (target - old) * coverage);
                        mask[index] = (byte) next;
                    }
                }
            }
            if (dirtyRight > dirtyLeft && dirtyBottom > dirtyTop) {
                rebuild(dirtyLeft, dirtyTop, dirtyRight, dirtyBottom);
            }
        }

        private void rebuild(int left, int top, int right, int bottom) {
            if (released) return;
            int width = Math.max(0, right - left);
            int height = Math.max(0, bottom - top);
            if (width == 0 || height == 0) return;
            for (int y = top; y < bottom; y++) {
                int row = y * imageWidth;
                for (int x = left; x < right; x++) {
                    int index = row + x;
                    int source = sourcePixels[index];
                    int sourceAlpha = source >>> 24;
                    int keep = mask[index] & 0xFF;
                    int resultAlpha = sourceAlpha * keep / 255;
                    resultPixels[index] =
                            (source & 0x00FFFFFF) | (resultAlpha << 24);

                    int removedAlpha = sourceAlpha * (255 - keep) / 255;
                    int red = Color.red(source);
                    int green = Color.green(source);
                    int blue = Color.blue(source);
                    red = Math.min(255, Math.round(red * 0.42f + 150f));
                    green = Math.round(green * 0.36f + 20f);
                    blue = Math.round(blue * 0.36f + 32f);
                    removedPixels[index] = (removedAlpha << 24)
                            | (red << 16) | (green << 8) | blue;
                }
            }
            resultBitmap.setPixels(
                    resultPixels, top * imageWidth + left, imageWidth,
                    left, top, width, height);
            removedBitmap.setPixels(
                    removedPixels, top * imageWidth + left, imageWidth,
                    left, top, width, height);
        }
    }

    static byte[] detectBackgroundMask(
            int[] pixels, int width, int height) {
        int count = width * height;
        byte[] output = new byte[count];
        Arrays.fill(output, (byte) 0xFF);
        if (width < 2 || height < 2) return output;

        int[] histogram = new int[4096];
        for (int x = 0; x < width; x++) {
            incrementBin(histogram, pixels[x]);
            incrementBin(histogram, pixels[(height - 1) * width + x]);
        }
        for (int y = 1; y < height - 1; y++) {
            incrementBin(histogram, pixels[y * width]);
            incrementBin(histogram, pixels[y * width + width - 1]);
        }
        int[] paletteBins = backgroundPaletteBins(histogram);
        int[] palette = new int[paletteBins.length];
        for (int i = 0; i < paletteBins.length; i++) {
            palette[i] = colorForBin(paletteBins[i]);
        }

        byte[] visited = new byte[count];
        int[] queue = new int[count];
        int head = 0;
        int tail = 0;
        for (int x = 0; x < width; x++) {
            tail = seed(pixels, visited, queue, tail, x, palette);
            tail = seed(pixels, visited, queue, tail,
                    (height - 1) * width + x, palette);
        }
        for (int y = 1; y < height - 1; y++) {
            tail = seed(pixels, visited, queue, tail, y * width, palette);
            tail = seed(pixels, visited, queue, tail,
                    y * width + width - 1, palette);
        }

        // Background may drift gently, but it must never jump across a subject edge. The old
        // unconditional 52-RGB palette match could enter through a one-pixel gap and consume all
        // skin/clothing colors until only a dark outline remained.
        int globalSoft = 44 * 44;
        int localTight = 10 * 10;
        int edgeLimit = 18 * 18;
        while (head < tail) {
            int index = queue[head++];
            output[index] = 0;
            int x = index % width;
            int y = index / width;
            if (x > 0) {
                tail = visitNeighbor(
                        pixels, visited, queue, tail, index, index - 1,
                        palette, globalSoft, localTight, edgeLimit);
            }
            if (x + 1 < width) {
                tail = visitNeighbor(
                        pixels, visited, queue, tail, index, index + 1,
                        palette, globalSoft, localTight, edgeLimit);
            }
            if (y > 0) {
                tail = visitNeighbor(
                        pixels, visited, queue, tail, index, index - width,
                        palette, globalSoft, localTight, edgeLimit);
            }
            if (y + 1 < height) {
                tail = visitNeighbor(
                        pixels, visited, queue, tail, index, index + width,
                        palette, globalSoft, localTight, edgeLimit);
            }
        }

        // One-pixel feathering keeps the automatic boundary from looking like a hard binary cut.
        byte[] feathered = output.clone();
        for (int y = 1; y < height - 1; y++) {
            int row = y * width;
            for (int x = 1; x < width - 1; x++) {
                int index = row + x;
                if ((output[index] & 0xFF) == 0) continue;
                int removed = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0) continue;
                        if ((output[index + dy * width + dx] & 0xFF) == 0) {
                            removed++;
                        }
                    }
                }
                if (removed > 0) {
                    feathered[index] = (byte) Math.max(
                            36, 255 - removed * 27);
                }
            }
        }
        return feathered;
    }

    private static void incrementBin(int[] histogram, int color) {
        if ((color >>> 24) < 32) return;
        int bin = ((((color >> 16) & 0xFF) >> 4) << 8)
                | ((((color >> 8) & 0xFF) >> 4) << 4)
                | ((color & 0xFF) >> 4);
        histogram[bin]++;
    }

    private static int[] topBins(int[] histogram, int requested) {
        int[] bins = new int[requested];
        int[] counts = new int[requested];
        for (int bin = 0; bin < histogram.length; bin++) {
            int count = histogram[bin];
            if (count <= counts[requested - 1]) continue;
            int position = requested - 1;
            while (position > 0 && count > counts[position - 1]) {
                counts[position] = counts[position - 1];
                bins[position] = bins[position - 1];
                position--;
            }
            counts[position] = count;
            bins[position] = bin;
        }
        int actual = requested;
        int minimumCluster = Math.max(2,
                Math.round(counts[0] * 0.12f));
        while (actual > 1 && counts[actual - 1] < minimumCluster) actual--;
        return Arrays.copyOf(bins, actual);
    }

    /**
     * Keep the dominant border cluster and only nearby shade variants. A foreground object that
     * touches one edge can be frequent enough to enter a generic "top six" palette; treating that
     * object color as background is the destructive failure this editor must avoid.
     */
    private static int[] backgroundPaletteBins(int[] histogram) {
        int[] candidates = topBins(histogram, 4);
        if (candidates.length <= 1) return candidates;
        int dominant = colorForBin(candidates[0]);
        int[] accepted = new int[candidates.length];
        int count = 0;
        accepted[count++] = candidates[0];
        for (int i = 1; i < candidates.length; i++) {
            if (colorDistance(colorForBin(candidates[i]), dominant) <= 48 * 48) {
                accepted[count++] = candidates[i];
            }
        }
        return Arrays.copyOf(accepted, count);
    }

    private static int colorForBin(int bin) {
        int red = ((bin >> 8) & 0xF) * 17;
        int green = ((bin >> 4) & 0xF) * 17;
        int blue = (bin & 0xF) * 17;
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int seed(
            int[] pixels, byte[] visited, int[] queue,
            int tail, int index, int[] palette) {
        if (visited[index] != 0) return tail;
        int color = pixels[index];
        if ((color >>> 24) < 32
                || paletteDistance(color, palette) <= 34 * 34) {
            visited[index] = 1;
            queue[tail++] = index;
        }
        return tail;
    }

    private static int visitNeighbor(
            int[] pixels, byte[] visited, int[] queue, int tail,
            int current, int next, int[] palette,
            int globalSoft, int localTight, int edgeLimit) {
        if (visited[next] != 0) return tail;
        int color = pixels[next];
        boolean transparent = (color >>> 24) < 32;
        int paletteDistance = paletteDistance(color, palette);
        int localDistance = colorDistance(color, pixels[current]);
        boolean flatBackground = paletteDistance <= 30 * 30
                && localDistance <= edgeLimit;
        boolean gentleBackgroundGradient = paletteDistance <= globalSoft
                && localDistance <= localTight;
        if (transparent || flatBackground || gentleBackgroundGradient) {
            visited[next] = 1;
            queue[tail++] = next;
        }
        return tail;
    }

    private static int paletteDistance(int color, int[] palette) {
        int best = Integer.MAX_VALUE;
        for (int item : palette) {
            best = Math.min(best, colorDistance(color, item));
        }
        return best;
    }

    private static int colorDistance(int first, int second) {
        int red = ((first >> 16) & 0xFF) - ((second >> 16) & 0xFF);
        int green = ((first >> 8) & 0xFF) - ((second >> 8) & 0xFF);
        int blue = (first & 0xFF) - (second & 0xFF);
        return red * red + green * green + blue * blue;
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    private static void toast(
            Activity activity, String chinese, String english) {
        Toast.makeText(
                activity, UiLanguage.text(activity, chinese, english),
                Toast.LENGTH_SHORT).show();
    }
}
