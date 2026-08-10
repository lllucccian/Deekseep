package com.dsmod.probe;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Authenticated request console limited to DeepSeek's official chat API origin. */
final class DeepSeekRequestDebugUi {
    private static final String ORIGIN = "https://chat.deepseek.com";
    private static final int MAX_BODY = 64 * 1024;
    private static final int MAX_RESPONSE = 512 * 1024;

    private DeepSeekRequestDebugUi() {}

    static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = DeekseepUi.isDark(activity);
        final int canvas = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int barColor = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int surface = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int inputColor = dark ? 0xFF202023 : 0xFFF0F1F3;
        final int textColor = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int subColor = dark ? 0xFFA0A0A5 : 0xFF737378;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(canvas);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(barColor);
        int statusTop = statusBarHeight(activity);
        top.setPadding(DeekseepUi.dp(activity, 8), statusTop,
                DeekseepUi.dp(activity, 16), 0);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DeekseepUi.dp(activity, 56) + statusTop));

        TextView back = new TextView(activity);
        back.setText("\u2039");
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setTextColor(textColor);
        back.setGravity(Gravity.CENTER);
        back.setPadding(DeekseepUi.dp(activity, 8), 0,
                DeekseepUi.dp(activity, 8), 0);
        top.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, DeekseepUi.dp(activity, 40)));

        TextView title = new TextView(activity);
        title.setText(UiLanguage.text(activity,
                "DeepSeek 请求调试", "DeepSeek request debugger"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textColor);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.leftMargin = DeekseepUi.dp(activity, 8);
        top.addView(title, titleLp);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(DeekseepUi.dp(activity, 16), DeekseepUi.dp(activity, 16),
                DeekseepUi.dp(activity, 16), DeekseepUi.dp(activity, 24));
        scroll.addView(content);

        TextView note = new TextView(activity);
        note.setText(UiLanguage.text(activity,
                "仅允许请求 chat.deepseek.com 的 /api/ 路径。使用当前登录账号，"
                        + "不会在日志中显示鉴权信息。",
                "Requests are limited to /api/ paths on chat.deepseek.com. The current "
                        + "account is used and authentication is never printed in logs."));
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        note.setTextColor(subColor);
        note.setLineSpacing(DeekseepUi.dp(activity, 2), 1f);
        note.setPadding(DeekseepUi.dp(activity, 14), DeekseepUi.dp(activity, 12),
                DeekseepUi.dp(activity, 14), DeekseepUi.dp(activity, 12));
        note.setBackground(roundRect(surface, DeekseepUi.dp(activity, 12)));
        content.addView(note, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final String[] method = new String[]{"GET"};
        final TextView methodButton = control(activity, "GET", textColor, inputColor);
        methodButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                method[0] = "GET".equals(method[0]) ? "POST" : "GET";
                methodButton.setText(method[0]);
            }
        });
        LinearLayout.LayoutParams methodLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, DeekseepUi.dp(activity, 40));
        methodLp.topMargin = DeekseepUi.dp(activity, 16);
        content.addView(methodButton, methodLp);

        final EditText path = new EditText(activity);
        path.setSingleLine(true);
        path.setText("/api/v0/users/current");
        path.setHint("/api/v0/...");
        styleInput(activity, path, textColor, subColor, inputColor);
        LinearLayout.LayoutParams pathLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DeekseepUi.dp(activity, 48));
        pathLp.topMargin = DeekseepUi.dp(activity, 10);
        content.addView(path, pathLp);

        final EditText body = new EditText(activity);
        body.setGravity(Gravity.TOP | Gravity.START);
        body.setMinLines(7);
        body.setHint(UiLanguage.text(activity,
                "POST 请求的 JSON 正文", "JSON body for POST requests"));
        body.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        body.setTypeface(Typeface.MONOSPACE);
        styleInput(activity, body, textColor, subColor, inputColor);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = DeekseepUi.dp(activity, 10);
        content.addView(body, bodyLp);

        TextView send = control(activity,
                UiLanguage.text(activity, "发送请求", "Send request"),
                0xFFFFFFFF, DeekseepUi.BRAND);
        send.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, DeekseepUi.dp(activity, 44));
        sendLp.topMargin = DeekseepUi.dp(activity, 14);
        content.addView(send, sendLp);
        DeekseepUi.addBuildFooter(activity, content, subColor);

        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                DeekseepUi.slideOutAndDismiss(dialog, root);
            }
        });
        send.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                final String selectedMethod = method[0];
                final String selectedPath = path.getText().toString().trim();
                final String selectedBody = body.getText().toString();
                DeekseepUi.slideOutAndDismiss(dialog, root);
                TaskExecutionUi.show(activity,
                        UiLanguage.text(activity, "发送自定义请求", "Send custom request"),
                        UiLanguage.text(activity,
                                "响应将显示在此页。鉴权头会自动添加且不会输出。",
                                "The response appears here. Authentication headers are added "
                                        + "automatically and are never printed."),
                        new TaskExecutionUi.Task() {
                            @Override public String run(TaskExecutionUi.Logger logger)
                                    throws Throwable {
                                return execute(activity, selectedMethod, selectedPath,
                                        selectedBody, logger);
                            }
                        });
            }
        });

        UiLanguage.localizeTree(activity, root);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(canvas));
        }
        DeekseepUi.openWithSlide(dialog, root);
    }

    private static String execute(Activity activity, String method, String path,
                                  String body, TaskExecutionUi.Logger logger) throws Throwable {
        String safePath = normalizePath(path);
        if (!"GET".equals(method) && !"POST".equals(method)) {
            throw new IllegalArgumentException("Only GET and POST are supported");
        }
        byte[] payload = body == null
                ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
        if (payload.length > MAX_BODY) {
            throw new IllegalArgumentException("Request body exceeds 64 KiB");
        }
        if ("POST".equals(method) && payload.length > 0) {
            // Validate locally so a typo is reported before reaching the server.
            new org.json.JSONTokener(body).nextValue();
        }
        logger.line(method + " " + safePath);
        HttpURLConnection connection = null;
        InputStream stream = null;
        try {
            connection = (HttpURLConnection) new URL(ORIGIN + safePath).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setUseCaches(false);
            for (Map.Entry<String, String> header
                    : AccountManager.currentRequestHeaders(
                    activity, Main.hostClassLoaderForUi()).entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            if ("POST".equals(method)) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(payload.length);
                OutputStream output = connection.getOutputStream();
                try { output.write(payload); } finally { output.close(); }
            }
            int status = connection.getResponseCode();
            logger.line("HTTP " + status);
            stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String response = stream == null ? "" : readUtf8(stream, MAX_RESPONSE);
            if (response.length() == 0) {
                logger.line(UiLanguage.text(activity,
                        "服务器没有返回正文", "The server returned no response body"));
            } else {
                logger.line(response);
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status);
            }
            return response.length() == 0
                    ? "The server returned no response body" : response;
        } finally {
            if (stream != null) try { stream.close(); } catch (Throwable ignored) {}
            if (connection != null) connection.disconnect();
        }
    }

    private static String normalizePath(String value) {
        String path = value == null ? "" : value.trim();
        if (!path.startsWith("/api/") || path.contains("\\")
                || path.contains("\r") || path.contains("\n")
                || path.contains("..") || path.contains("://")) {
            throw new IllegalArgumentException("Path must start with /api/");
        }
        return path;
    }

    private static String readUtf8(InputStream input, int maximum) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) continue;
            int accepted = Math.min(read, maximum - total);
            if (accepted > 0) output.write(buffer, 0, accepted);
            total += accepted;
            if (total >= maximum) break;
        }
        String text = output.toString("UTF-8");
        return total >= maximum ? text + "\n…[response truncated]" : text;
    }

    private static void styleInput(Activity activity, EditText input,
                                   int text, int hint, int background) {
        input.setTextColor(text);
        input.setHintTextColor(hint);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setPadding(DeekseepUi.dp(activity, 12), DeekseepUi.dp(activity, 10),
                DeekseepUi.dp(activity, 12), DeekseepUi.dp(activity, 10));
        input.setBackground(roundRect(background, DeekseepUi.dp(activity, 10)));
    }

    private static TextView control(Activity activity, String label,
                                    int foreground, int background) {
        TextView view = new TextView(activity);
        view.setText(label);
        view.setTextColor(foreground);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setGravity(Gravity.CENTER);
        view.setPadding(DeekseepUi.dp(activity, 14), 0,
                DeekseepUi.dp(activity, 14), 0);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(roundRect(background, DeekseepUi.dp(activity, 10)));
        return view;
    }

    private static GradientDrawable roundRect(int color, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private static int statusBarHeight(Activity activity) {
        try {
            int id = activity.getResources().getIdentifier(
                    "status_bar_height", "dimen", "android");
            return id == 0 ? 0 : activity.getResources().getDimensionPixelSize(id);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
