package com.dsmod.probe;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;

/** Crash history and deliberately destructive diagnostics, kept out of normal feature code. */
final class CrashDiagnosticsUi {
    private static final String PRIVATE_LOG =
            "/data/data/com.deepseek.chat/files/dsprobe_crash.log";
    private static volatile String pendingExport;
    private static final WeakHashMap<Dialog, BottomSheetState> BOTTOM_SHEETS =
            new WeakHashMap<>();

    private static final class BottomSheetState {
        final View scrim;
        final View panel;
        boolean dismissing;

        BottomSheetState(View scrim, View panel) {
            this.scrim = scrim;
            this.panel = panel;
        }
    }

    private CrashDiagnosticsUi() {}

    static void showRecords(final Activity activity) {
        final String report = readCrashReport(activity);
        final Dialog dialog = bottomDialog(activity);
        LinearLayout panel = panel(activity, "记录崩溃",
                "记录 Java 未捕获异常，并读取系统保留的 Native 崩溃与 ANR 记录。");

        TextView body = new TextView(activity);
        body.setText(report.length() == 0 ? "暂无崩溃" : report);
        body.setTextColor(DeekseepUi.isDark(activity) ? 0xFFD6D6D9 : 0xFF333333);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        body.setTypeface(Typeface.MONOSPACE);
        body.setTextIsSelectable(true);
        body.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        GradientDrawable bodyBg = new GradientDrawable();
        bodyBg.setColor(DeekseepUi.isDark(activity) ? 0xFF202023 : 0xFFF4F5F7);
        bodyBg.setCornerRadius(dp(activity, 9));
        body.setBackground(bodyBg);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(body);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 360));
        scrollLp.topMargin = dp(activity, 14);
        panel.addView(scroll, scrollLp);

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END);
        TextView close = action(activity, "关闭", false);
        close.setOnClickListener(v -> dismissBottom(dialog, null));
        actions.addView(close);
        TextView save = action(activity, "保存成文件", true);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        saveLp.leftMargin = dp(activity, 10);
        actions.addView(save, saveLp);
        save.setEnabled(report.length() > 0);
        save.setAlpha(report.length() > 0 ? 1f : 0.45f);
        save.setOnClickListener(v -> {
            if (report.length() == 0) return;
            pendingExport = report;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TITLE, "deekseep-crash-"
                    + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date())
                    + ".log");
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            dismissBottom(dialog, () -> {
                try {
                    activity.startActivityForResult(intent, Main.CRASH_EXPORT_REQUEST);
                } catch (Throwable t) {
                    pendingExport = null;
                    Toast.makeText(activity, "无法打开保存位置", Toast.LENGTH_SHORT).show();
                }
            });
        });
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.topMargin = dp(activity, 14);
        panel.addView(actions, actionsLp);
        showBottom(dialog, panel);
    }

    static void showCrashTests(final Activity activity) {
        final Dialog dialog = bottomDialog(activity);
        LinearLayout panel = panel(activity, "崩溃测试",
                "仅终止 DeepSeek 应用进程。已移除可能牵连系统冻结器的 Native 信号测试。");
        panel.addView(choice(activity, "主线程异常", "8 种 Java 异常与错误",
                v -> dismissBottom(dialog, () -> showMainThreadTests(activity))));
        panel.addView(divider(activity));
        panel.addView(choice(activity, "异步与回调异常", "后台线程、消息队列与绘制帧回调",
                v -> dismissBottom(dialog, () -> showAsyncTests(activity))));
        panel.addView(divider(activity));
        panel.addView(choice(activity, "发送链异常", "下一次真实消息发送时触发 Java 崩溃",
                v -> dismissBottom(dialog, () -> showSendTests(activity))));
        showBottom(dialog, panel);
    }

    private static void showMainThreadTests(final Activity activity) {
        final Dialog dialog = bottomDialog(activity);
        LinearLayout panel = panel(activity, "主线程异常", "点击后立即终止当前 DeepSeek 进程。");
        addCrashChoice(activity, panel, dialog, "主线程 RuntimeException", "java_main");
        addCrashChoice(activity, panel, dialog, "主线程 NullPointerException", "java_null");
        addCrashChoice(activity, panel, dialog, "主线程 IllegalStateException", "java_state");
        addCrashChoice(activity, panel, dialog, "主线程 ClassCastException", "java_cast");
        addCrashChoice(activity, panel, dialog, "主线程 IndexOutOfBoundsException", "java_bounds");
        addCrashChoice(activity, panel, dialog, "主线程 ArithmeticException", "java_arithmetic");
        addCrashChoice(activity, panel, dialog, "主线程 AssertionError", "java_assert");
        addCrashChoice(activity, panel, dialog, "主线程 StackOverflowError", "java_stack");
        showBottom(dialog, panel);
    }

    private static void showAsyncTests(final Activity activity) {
        final Dialog dialog = bottomDialog(activity);
        LinearLayout panel = panel(activity, "异步与回调异常", "覆盖不同的应用线程和主线程调度入口。");
        addCrashChoice(activity, panel, dialog, "后台线程 RuntimeException", "java_worker");
        addCrashChoice(activity, panel, dialog, "线程池任务 RuntimeException", "java_executor");
        addCrashChoice(activity, panel, dialog, "Handler 延迟回调异常", "java_handler");
        addCrashChoice(activity, panel, dialog, "下一绘制帧回调异常", "java_frame");
        showBottom(dialog, panel);
    }

    private static void showSendTests(final Activity activity) {
        final Dialog dialog = bottomDialog(activity);
        LinearLayout panel = panel(activity, "发送链异常", "设置后返回聊天页，在下一次真实发送时触发。");
        addCrashChoice(activity, panel, dialog, "下一次发送消息时 RuntimeException", "java_send");
        showBottom(dialog, panel);
    }

    private static void addCrashChoice(Activity activity, LinearLayout panel,
            final Dialog dialog, String title, final String mode) {
        if (panel.getChildCount() > 2) panel.addView(divider(activity));
        panel.addView(choice(activity, title, mode.endsWith("_send")
                ? "等待下一次真实消息请求" : "立即终止当前 DeepSeek 进程", v -> {
            dismissBottom(dialog, () -> Main.triggerCrashTest(activity, mode));
        }));
    }

    static void handleExportResult(final Activity activity, int resultCode, Intent data) {
        final String content = pendingExport;
        pendingExport = null;
        if (resultCode != Activity.RESULT_OK || content == null || data == null
                || data.getData() == null) return;
        final Uri uri = data.getData();
        new Thread(() -> {
            String error = null;
            try (OutputStream output = activity.getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IllegalStateException("目标文件不可写");
                output.write(content.getBytes("UTF-8"));
                output.flush();
            } catch (Throwable t) {
                error = t.getMessage() == null ? "保存失败" : t.getMessage();
            }
            final String result = error;
            activity.runOnUiThread(() -> Toast.makeText(activity,
                    result == null ? "崩溃日志已保存" : "保存失败：" + result,
                    Toast.LENGTH_SHORT).show());
        }, "deekseep-crash-export").start();
    }

    private static String readCrashReport(Context context) {
        StringBuilder out = new StringBuilder();
        File file = new File(PRIVATE_LOG);
        if (file.isFile() && file.length() > 0) {
            appendBoundedFile(out, file, 384 * 1024);
        }
        if (Build.VERSION.SDK_INT >= 30) appendSystemExitHistory(context, out);
        return out.toString().trim();
    }

    private static void appendSystemExitHistory(Context context, StringBuilder out) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(
                    Context.ACTIVITY_SERVICE);
            if (manager == null) return;
            List<ApplicationExitInfo> history = manager.getHistoricalProcessExitReasons(
                    context.getPackageName(), 0, 12);
            for (ApplicationExitInfo info : history) {
                int reason = info.getReason();
                if (reason != ApplicationExitInfo.REASON_CRASH
                        && reason != ApplicationExitInfo.REASON_CRASH_NATIVE
                        && reason != ApplicationExitInfo.REASON_ANR) continue;
                if (out.length() > 0) out.append("\n\n");
                out.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                        .format(new Date(info.getTimestamp())))
                        .append("  SYSTEM_EXIT reason=").append(reasonName(reason))
                        .append(" status=").append(info.getStatus())
                        .append(" importance=").append(info.getImportance());
                String description = info.getDescription();
                if (description != null && description.length() > 0) {
                    out.append("\n").append(description);
                }
                try (InputStream trace = info.getTraceInputStream()) {
                    if (trace != null) appendBoundedStream(out, trace, 96 * 1024);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
    }

    private static String reasonName(int reason) {
        if (reason == ApplicationExitInfo.REASON_CRASH_NATIVE) return "NATIVE_CRASH";
        if (reason == ApplicationExitInfo.REASON_ANR) return "ANR";
        return "JAVA_CRASH";
    }

    private static void appendBoundedFile(StringBuilder out, File file, int limit) {
        try (InputStream input = new FileInputStream(file)) {
            appendBoundedStream(out, input, limit);
        } catch (Throwable ignored) {}
    }

    private static void appendBoundedStream(StringBuilder out, InputStream input, int limit)
            throws Exception {
        byte[] buffer = new byte[4096];
        int remaining = limit;
        int count;
        while (remaining > 0 && (count = input.read(buffer, 0,
                Math.min(buffer.length, remaining))) > 0) {
            out.append(new String(buffer, 0, count, "UTF-8"));
            remaining -= count;
        }
        if (remaining == 0) out.append("\n…日志已截断…");
    }

    private static Dialog bottomDialog(Activity activity) {
        return new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar);
    }

    private static LinearLayout panel(Context context, String title, String description) {
        boolean dark = DeekseepUi.isDark(context);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(context, 20), dp(context, 18), dp(context, 20),
                dp(context, 18));
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF29292D : 0xFFFFFFFF);
        background.setCornerRadii(new float[]{dp(context, 22), dp(context, 22),
                dp(context, 22), dp(context, 22), 0, 0, 0, 0});
        panel.setBackground(background);
        TextView heading = new TextView(context);
        heading.setText(UiLanguage.dynamic(context, title));
        heading.setTextColor(dark ? 0xFFF0F0F2 : 0xFF191919);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(heading);
        TextView detail = new TextView(context);
        detail.setText(UiLanguage.dynamic(context, description));
        detail.setTextColor(dark ? 0xFFA8A8AD : 0xFF777777);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp(context, 5);
        detailLp.bottomMargin = dp(context, 10);
        panel.addView(detail, detailLp);
        return panel;
    }

    private static View choice(Context context, String title, String description,
            View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 12));
        row.setClickable(true);
        row.setOnClickListener(listener);
        TextView heading = new TextView(context);
        heading.setText(UiLanguage.dynamic(context, title));
        heading.setTextColor(DeekseepUi.isDark(context) ? 0xFFECECEF : 0xFF202020);
        heading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        row.addView(heading);
        TextView detail = new TextView(context);
        detail.setText(UiLanguage.dynamic(context, description));
        detail.setTextColor(DeekseepUi.isDark(context) ? 0xFF99999E : 0xFF888888);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        row.addView(detail);
        return row;
    }

    private static View divider(Context context) {
        View view = new View(context);
        view.setBackgroundColor(DeekseepUi.isDark(context) ? 0xFF414145 : 0xFFE9E9EC);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return view;
    }

    private static TextView action(Context context, String text, boolean primary) {
        TextView view = new TextView(context);
        view.setText(UiLanguage.dynamic(context, text));
        view.setTextColor(primary ? 0xFFFFFFFF
                : (DeekseepUi.isDark(context) ? 0xFFD0D0D3 : 0xFF555555));
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 15), dp(context, 9), dp(context, 15), dp(context, 9));
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? DeekseepUi.BRAND
                : (DeekseepUi.isDark(context) ? 0xFF3A3A3E : 0xFFF0F1F3));
        background.setCornerRadius(dp(context, 9));
        view.setBackground(background);
        return view;
    }

    private static void showBottom(Dialog dialog, LinearLayout panel) {
        FrameLayout root = new FrameLayout(panel.getContext());
        View scrim = new View(panel.getContext());
        scrim.setBackgroundColor(0x66000000);
        scrim.setAlpha(0f);
        root.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        panel.setAlpha(0f);
        root.addView(panel, params);
        scrim.setOnClickListener(v -> dismissBottom(dialog, null));
        panel.setOnClickListener(v -> {});
        dialog.setCancelable(false);
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                dismissBottom(dialog, null);
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_BACK;
        });
        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(0x00000000));
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        synchronized (BOTTOM_SHEETS) {
            BOTTOM_SHEETS.put(dialog, new BottomSheetState(scrim, panel));
        }
        panel.post(() -> {
            if (!dialog.isShowing()) return;
            panel.setTranslationY(Math.max(panel.getHeight(), dp(panel.getContext(), 240)));
            panel.setAlpha(1f);
            panel.animate().translationY(0f).setDuration(280L)
                    .setInterpolator(new DecelerateInterpolator(1.8f)).start();
            scrim.animate().alpha(1f).setDuration(220L).start();
        });
    }

    private static void dismissBottom(final Dialog dialog, final Runnable afterDismiss) {
        final BottomSheetState state;
        synchronized (BOTTOM_SHEETS) {
            state = BOTTOM_SHEETS.get(dialog);
            if (state != null && state.dismissing) return;
            if (state != null) state.dismissing = true;
        }
        if (state == null || !dialog.isShowing()) {
            try { dialog.dismiss(); } catch (Throwable ignored) {}
            if (afterDismiss != null) afterDismiss.run();
            return;
        }
        state.scrim.animate().alpha(0f).setDuration(180L).start();
        state.panel.animate()
                .translationY(Math.max(state.panel.getHeight(), dp(state.panel.getContext(), 240)))
                .setDuration(220L)
                .setInterpolator(new AccelerateInterpolator(1.35f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        synchronized (BOTTOM_SHEETS) { BOTTOM_SHEETS.remove(dialog); }
                        try { dialog.dismiss(); } catch (Throwable ignored) {}
                        if (afterDismiss != null) afterDismiss.run();
                    }
                }).start();
    }

    private static int dp(Context context, float value) {
        return DeekseepUi.dp(context, value);
    }
}
