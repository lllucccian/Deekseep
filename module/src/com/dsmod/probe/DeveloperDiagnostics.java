package com.dsmod.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Environment;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, content-redacted diagnostics for compatibility and performance maintenance. */
final class DeveloperDiagnostics {
    private static final Object LOCK = new Object();
    private static final int MAX_EVENTS = 320;
    private static final ArrayDeque<String> EVENTS = new ArrayDeque<>();
    private static final long STARTED_AT = SystemClock.elapsedRealtime();
    private static final Pattern COST = Pattern.compile("(?:cost|elapsed|duration)=([0-9]{1,7})ms", Pattern.CASE_INSENSITIVE);
    private static final Pattern BEARER = Pattern.compile("(?i)(authorization|bearer|api[_-]?key|token)(\\s*[:=]\\s*|\\s+)[^\\s,;]{6,}");
    private static final Pattern PRIVATE_PATH = Pattern.compile("/(?:data|storage)/[^\\s,;]+", Pattern.CASE_INSENSITIVE);
    private static long total;
    private static long errors;
    private static long hooks;
    private static long network;
    private static long agent;
    private static long timed;
    private static long totalCostMs;
    private static long maxCostMs;
    private static long slow;

    private DeveloperDiagnostics() {}

    static void record(String raw) {
        if (raw == null || raw.length() == 0) return;
        String lower = raw.toLowerCase(Locale.US);
        long cost = parseCost(raw);
        String event = sanitize(raw);
        synchronized (LOCK) {
            total++;
            if (lower.contains("fail") || lower.contains("error")
                    || lower.contains("exception") || lower.contains("crash")) errors++;
            if (lower.contains("hook") || lower.contains("inject")) hooks++;
            if (lower.contains("server") || lower.contains("request")
                    || lower.contains("response") || lower.contains("sse")) network++;
            if (lower.contains("agent") || lower.contains("tool")) agent++;
            if (cost >= 0L) {
                timed++;
                totalCostMs += cost;
                if (cost > maxCostMs) maxCostMs = cost;
                if (cost >= 32L) slow++;
            }
            if (isUseful(lower)) {
                EVENTS.addLast(new SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
                        .format(new Date()) + "  " + event);
                while (EVENTS.size() > MAX_EVENTS) EVENTS.removeFirst();
            }
        }
    }

    static void showCompatibility(Activity activity) {
        StringBuilder report = new StringBuilder();
        report.append("Deekseep ").append(BuildInfo.MODULE_VERSION).append('\n');
        report.append("Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        report.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            long code = Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            report.append("host=").append(info.versionName).append(" (").append(code).append(")\n");
        } catch (Throwable error) {
            report.append("host=unknown\n");
        }
        report.append(HostCompat.diagnosticSummary());
        showReport(activity, "兼容性诊断报告", report.toString());
    }

    static void showPerformance(Activity activity) {
        String report;
        synchronized (LOCK) {
            long uptime = Math.max(1L, SystemClock.elapsedRealtime() - STARTED_AT);
            long average = timed == 0L ? 0L : totalCostMs / timed;
            report = "session=" + formatDuration(uptime)
                    + "\nevents=" + total
                    + "\nhooks=" + hooks
                    + "\nnetwork=" + network
                    + "\nagent=" + agent
                    + "\nerrors=" + errors
                    + "\ntimedEvents=" + timed
                    + "\naverageCost=" + average + "ms"
                    + "\nmaxCost=" + maxCostMs + "ms"
                    + "\nslowEvents(>=32ms)=" + slow;
        }
        showReport(activity, "Hook 性能统计", report);
    }

    static void exportTrace(Activity activity) {
        String report = traceReport(activity);
        File base = activity.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = activity.getFilesDir();
        File dir = new File(base, "Deekseep");
        File output = new File(dir, "developer-trace-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".txt");
        try {
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("mkdir failed");
            FileWriter writer = new FileWriter(output, false);
            writer.write(report);
            writer.close();
            copy(activity, report);
            Toast.makeText(activity, "已导出并复制：" + output.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Throwable error) {
            copy(activity, report);
            Toast.makeText(activity, "文件保存失败，报告已复制", Toast.LENGTH_LONG).show();
        }
    }

    private static String traceReport(Activity activity) {
        StringBuilder out = new StringBuilder();
        out.append("Deekseep sanitized developer trace\n")
                .append(HostCompat.diagnosticSummary()).append("\n\n");
        synchronized (LOCK) {
            for (String event : EVENTS) out.append(event).append('\n');
        }
        return out.toString();
    }

    private static void showReport(final Activity activity, String title, final String report) {
        TextView body = new TextView(activity);
        body.setText(report);
        body.setTextIsSelectable(true);
        body.setTypeface(android.graphics.Typeface.MONOSPACE);
        body.setTextSize(12f);
        int padding = DeekseepUi.dp(activity, 20);
        body.setPadding(padding, padding / 2, padding, padding / 2);
        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(body)
                .setNeutralButton("复制", (dialog, which) -> copy(activity, report))
                .setPositiveButton("完成", null)
                .show();
    }

    private static void copy(Context context, String text) {
        try {
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Deekseep diagnostics", text));
        } catch (Throwable ignored) {}
    }

    private static long parseCost(String raw) {
        Matcher matcher = COST.matcher(raw);
        if (!matcher.find()) return -1L;
        try { return Long.parseLong(matcher.group(1)); }
        catch (Throwable ignored) { return -1L; }
    }

    private static String sanitize(String raw) {
        String value = raw.replace('\n', ' ').replace('\r', ' ');
        value = BEARER.matcher(value).replaceAll("$1$2[redacted]");
        value = PRIVATE_PATH.matcher(value).replaceAll("/[private-path]");
        if (value.length() > 420) value = value.substring(0, 417) + "...";
        return value;
    }

    private static boolean isUseful(String lower) {
        if (lower.contains("progress=") || lower.contains("sensor sample")
                || lower.contains("recomposition") || lower.contains("frame=")) return false;
        return lower.contains("hook") || lower.contains("inject") || lower.contains("request")
                || lower.contains("response") || lower.contains("server") || lower.contains("agent")
                || lower.contains("tool") || lower.contains("fail") || lower.contains("error")
                || lower.contains("activity") || lower.contains("adaptation");
    }

    private static String formatDuration(long millis) {
        long seconds = millis / 1000L;
        return (seconds / 3600L) + "h " + ((seconds / 60L) % 60L) + "m " + (seconds % 60L) + "s";
    }
}
