package com.dsmod.probe;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Full-screen, auto-scrolling execution console for one-shot module jobs. */
final class TaskExecutionUi {
    interface Logger { void line(String value); }
    interface Task { String run(Logger logger) throws Throwable; }

    private TaskExecutionUi() {}

    static void show(final Activity activity, String rawTitle, String rawHint,
                     final Task task) {
        if (activity == null || activity.isFinishing() || task == null) return;
        final boolean dark = DeekseepUi.isDark(activity);
        final int canvas = dark ? 0xFF1B1B1D : 0xFFF1F2F4;
        final int bar = dark ? 0xFF202023 : 0xFFF7F7F8;
        final int text = dark ? 0xFFECECEE : 0xFF202024;
        final String englishTitle = UiLanguageCatalog.toEnglish(rawTitle == null ? "" : rawTitle);
        final String englishHint = UiLanguageCatalog.toEnglish(rawHint == null ? "" : rawHint);
        final AtomicBoolean complete = new AtomicBoolean(false);

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.setCanceledOnTouchOutside(false);

        final FrameLayout root = new FrameLayout(activity);
        root.setBackgroundColor(canvas);
        LinearLayout page = new LinearLayout(activity);
        page.setOrientation(LinearLayout.VERTICAL);
        root.addView(page, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(bar);
        int statusTop = statusBarHeight(activity);
        top.setPadding(DeekseepUi.dp(activity, 8), statusTop,
                DeekseepUi.dp(activity, 8), 0);
        page.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DeekseepUi.dp(activity, 64) + statusTop));

        FrameLayout back = new FrameLayout(activity);
        back.setClickable(true);
        back.setFocusable(true);
        View backGlyph = new HubMaterialGlyphView(activity, "ds_action_back", text);
        back.addView(backGlyph, new FrameLayout.LayoutParams(
                DeekseepUi.dp(activity, 24), DeekseepUi.dp(activity, 24), Gravity.CENTER));
        top.addView(back, new LinearLayout.LayoutParams(
                DeekseepUi.dp(activity, 48), DeekseepUi.dp(activity, 48)));

        TextView heading = new TextView(activity);
        heading.setText(UiLanguage.text(activity, "执行", "Action"));
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        heading.setTypeface(Typeface.DEFAULT);
        heading.setTextColor(text);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headingLp.leftMargin = DeekseepUi.dp(activity, 4);
        top.addView(heading, headingLp);

        FrameLayout save = new FrameLayout(activity);
        save.setClickable(true);
        save.setFocusable(true);
        save.setContentDescription(UiLanguage.text(activity, "保存日志", "Save log"));
        View saveGlyph = new HubMaterialGlyphView(activity, "ds_action_save", text);
        save.addView(saveGlyph, new FrameLayout.LayoutParams(
                DeekseepUi.dp(activity, 24), DeekseepUi.dp(activity, 24), Gravity.CENTER));
        top.addView(save, new LinearLayout.LayoutParams(
                DeekseepUi.dp(activity, 48), DeekseepUi.dp(activity, 48)));

        final ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        final TextView logView = new TextView(activity);
        logView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        logView.setTextColor(text);
        logView.setTypeface(Typeface.MONOSPACE);
        logView.setTextIsSelectable(true);
        logView.setGravity(Gravity.START);
        logView.setLineSpacing(DeekseepUi.dp(activity, 1), 1.05f);
        logView.setPadding(DeekseepUi.dp(activity, 8), DeekseepUi.dp(activity, 8),
                DeekseepUi.dp(activity, 8),
                DeekseepUi.dp(activity, 64) + navigationBarHeight(activity));
        scroll.addView(logView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final FrameLayout close = new FrameLayout(activity);
        close.setClickable(true);
        close.setFocusable(true);
        close.setContentDescription(UiLanguage.text(activity, "关闭", "Close"));
        close.setBackground(roundRect(DeekseepUi.BRAND, DeekseepUi.dp(activity, 28)));
        View closeGlyph = new HubMaterialGlyphView(activity, "ds_action_close", 0xFFFFFFFF);
        close.addView(closeGlyph, new FrameLayout.LayoutParams(
                DeekseepUi.dp(activity, 40), DeekseepUi.dp(activity, 40), Gravity.CENTER));
        close.setVisibility(View.GONE);
        FrameLayout.LayoutParams closeLp = new FrameLayout.LayoutParams(
                DeekseepUi.dp(activity, 56), DeekseepUi.dp(activity, 56),
                Gravity.END | Gravity.BOTTOM);
        closeLp.setMargins(DeekseepUi.dp(activity, 20), 0,
                DeekseepUi.dp(activity, 20),
                DeekseepUi.dp(activity, 20) + navigationBarHeight(activity));
        root.addView(close, closeLp);

        final StringBuilder logText = new StringBuilder();
        final Logger logger = new Logger() {
            @Override public void line(final String value) {
                final String safe = UiLanguageCatalog.toEnglish(value == null ? "" : value);
                synchronized (logText) {
                    if (safe.startsWith("\u001B[H\u001B[J")) {
                        logText.setLength(0);
                        logText.append(safe.substring(6));
                    } else {
                        logText.append(safe).append('\n');
                    }
                }
                activity.runOnUiThread(new Runnable() {
                    @Override public void run() {
                        synchronized (logText) { logView.setText(logText.toString()); }
                        scroll.post(new Runnable() {
                            @Override public void run() {
                                scroll.smoothScrollTo(0, Math.max(0, logView.getHeight()));
                            }
                        });
                    }
                });
            }
        };

        View.OnClickListener dismissWhenComplete = new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (complete.get()) DeekseepUi.slideOutAndDismiss(dialog, root);
            }
        };
        back.setOnClickListener(dismissWhenComplete);
        close.setOnClickListener(dismissWhenComplete);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                saveLog(activity, logText);
            }
        });

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(canvas));
        }
        DeekseepUi.openWithSlide(dialog, root);
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(android.content.DialogInterface ignored,
                                           int keyCode, android.view.KeyEvent event) {
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK
                        && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    if (complete.get()) DeekseepUi.slideOutAndDismiss(dialog, root);
                    return true;
                }
                return false;
            }
        });

        new Thread(new Runnable() {
            @Override public void run() {
                if (englishTitle.length() > 0) logger.line("Task: " + englishTitle);
                if (englishHint.length() > 0) logger.line("Note: " + englishHint);
                logger.line("Execution started.");
                try {
                    final String result = task.run(logger);
                    logger.line("Execution completed successfully.");
                    if (result != null && result.trim().length() > 0) {
                        activity.runOnUiThread(new Runnable() {
                            @Override public void run() {
                                showResultDialog(activity, rawTitle, result, true);
                            }
                        });
                    }
                } catch (Throwable error) {
                    String message = error.getMessage();
                    if (message == null || message.trim().length() == 0) {
                        message = error.getClass().getSimpleName();
                    }
                    final String failure = message;
                    logger.line("Execution failed: " + failure);
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() {
                            showResultDialog(activity, rawTitle, failure, false);
                        }
                    });
                } finally {
                    complete.set(true);
                    activity.runOnUiThread(new Runnable() {
                        @Override public void run() { close.setVisibility(View.VISIBLE); }
                    });
                }
            }
        }, "Deekseep-task-console").start();
    }

    private static void saveLog(Activity activity, StringBuilder source) {
        try {
            String snapshot;
            synchronized (source) { snapshot = source.toString(); }
            File external = activity.getExternalFilesDir(null);
            File root = new File(external != null ? external : activity.getFilesDir(),
                    "deekseep/task_logs");
            if (!root.exists() && !root.mkdirs()) throw new IllegalStateException("mkdir failed");
            File output = new File(root, "task_"
                    + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date())
                    + ".log");
            FileOutputStream stream = new FileOutputStream(output, false);
            try {
                stream.write(snapshot.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            } finally {
                stream.close();
            }
            Toast.makeText(activity, "Log saved: " + output.getPath(), Toast.LENGTH_LONG).show();
        } catch (Throwable error) {
            Toast.makeText(activity, "Failed to save log: " + error.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private static void showResultDialog(Activity activity, String rawTitle,
                                         String result, boolean success) {
        if (activity == null || activity.isFinishing()) return;
        TextView value = new TextView(activity);
        value.setText(result);
        value.setTextIsSelectable(true);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setPadding(DeekseepUi.dp(activity, 20), DeekseepUi.dp(activity, 8),
                DeekseepUi.dp(activity, 20), DeekseepUi.dp(activity, 8));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(value, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String title = success
                ? UiLanguage.dynamic(activity, rawTitle)
                : UiLanguage.text(activity, "执行失败", "Execution failed");
        new android.app.AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(scroll)
                .setPositiveButton(UiLanguage.text(activity, "确定", "OK"), null)
                .show();
    }

    private static GradientDrawable roundRect(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int statusBarHeight(Activity activity) {
        return systemBarHeight(activity, "status_bar_height");
    }

    private static int navigationBarHeight(Activity activity) {
        return systemBarHeight(activity, "navigation_bar_height");
    }

    private static int systemBarHeight(Activity activity, String name) {
        try {
            int id = activity.getResources().getIdentifier(name, "dimen", "android");
            return id == 0 ? 0 : activity.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
