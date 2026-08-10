package com.dsmod.probe;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;

/** Optional non-touchable rolling overlay for live module hook diagnostics. */
final class HookLogOverlay {
    private static final String MARKER =
            "/data/data/com.deepseek.chat/files/deekseep_hook_overlay";
    /** Enough history for a useful full-screen trace without retaining an unbounded session. */
    private static final int MAX_LINES = 1200;
    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> LINES = new ArrayDeque<>();
    private static final LinkedHashMap<String, Long> RECENT = new LinkedHashMap<>();
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static WeakReference<Activity> activity = new WeakReference<>(null);
    private static WeakReference<TextView> text = new WeakReference<>(null);
    private static Handler main;
    private static long lastServerEventAt;
    private static int collapsedServerEvents;
    private static boolean refreshPending;

    private HookLogOverlay() {}

    static boolean enabled() { return new File(MARKER).exists(); }

    /** Starts a fresh trace for every new DeepSeek process before the first hook is logged. */
    static void resetSession() {
        synchronized (LOCK) {
            LINES.clear();
            RECENT.clear();
            lastServerEventAt = 0L;
            collapsedServerEvents = 0;
            refreshPending = false;
        }
        TextView overlay = text.get();
        if (overlay != null) postRefresh();
    }

    static boolean setEnabled(boolean value) {
        try {
            File marker = new File(MARKER);
            if (value) {
                FileWriter writer = new FileWriter(marker, false);
                writer.write("1\n");
                writer.close();
            } else if (marker.exists() && !marker.delete()) {
                return false;
            }
            Activity present = activity.get();
            if (present != null) {
                if (value) attach(present); else detachView(present);
            }
            return true;
        } catch (Throwable ignored) { return false; }
    }

    static void onActivityResumed(Activity value) {
        if (value == null) return;
        activity = new WeakReference<>(value);
        if (enabled()) attach(value);
    }

    static void onActivityDestroyed(Activity value) {
        if (value != null && activity.get() == value) {
            detachView(value);
            activity = new WeakReference<>(null);
        }
    }

    static void onLog(String message) {
        if (!enabled() || message == null || message.length() == 0) return;
        String clean = englishOnly(message.replace('\n', ' ').replace('\r', ' ')).trim();
        if (clean.length() == 0 || isNoisy(clean)) return;
        if (clean.length() > 520) clean = clean.substring(0, 517) + "…";
        long now = android.os.SystemClock.uptimeMillis();
        synchronized (LOCK) {
            Long previous = RECENT.get(clean);
            if (previous != null && now - previous.longValue() < 1500L) return;
            RECENT.put(clean, Long.valueOf(now));
            while (RECENT.size() > 192) {
                String oldest = RECENT.keySet().iterator().next();
                RECENT.remove(oldest);
            }
            LINES.addLast(TIME.format(new Date()) + "  " + classify(clean));
            while (LINES.size() > MAX_LINES) LINES.removeFirst();
        }
        postRefresh();
    }

    /** Structured event path used by high-value request, response, and Agent boundaries. */
    static void event(String area, String action, String detail) {
        StringBuilder line = new StringBuilder();
        if (area != null && area.length() > 0) line.append('[').append(area).append("] ");
        if (action != null) line.append(action);
        if (detail != null && detail.length() > 0) line.append(" · ").append(detail);
        onLog(line.toString());
    }

    /** Keeps streaming diagnostics readable without posting one UI update for every token. */
    static synchronized void serverEvent(String type, int chars) {
        String value = type == null ? "unknown" : type;
        String lower = value.toLowerCase(Locale.US);
        boolean terminal = lower.contains("finish") || lower.contains("done")
                || lower.contains("complete") || lower.contains("close")
                || lower.contains("error") || lower.contains("fail");
        long now = android.os.SystemClock.uptimeMillis();
        if (!terminal && now - lastServerEventAt < 180L) {
            collapsedServerEvents++;
            return;
        }
        int collapsed = collapsedServerEvents;
        collapsedServerEvents = 0;
        lastServerEventAt = now;
        event("NETWORK", terminal && (lower.contains("error") || lower.contains("fail"))
                        ? "Response failed" : terminal ? "Response completed" : "Response event",
                "event=" + value + " chars=" + Math.max(0, chars)
                        + (collapsed > 0 ? " merged=" + collapsed : ""));
    }

    private static String classify(String message) {
        if (message.startsWith("[")) return message;
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("failed") || lower.contains("failure")
                || lower.contains("error") || lower.contains("exception")
                || lower.contains("crash")) {
            return "[ERROR] " + message;
        }
        if (lower.contains("tool") || lower.contains("agent")) return "[AGENT] " + message;
        if (lower.contains("server") || lower.contains("response")
                || lower.contains("request") || lower.contains("sse")) {
            return "[NETWORK] " + message;
        }
        if (lower.startsWith("hooked ") || lower.startsWith("installed ")
                || lower.contains(" hook ")) return "[MODULE] " + message;
        if (lower.contains("message") || lower.contains("status")
                || lower.contains("activity") || lower.contains("deepseek")) {
            return "[HOST] " + message;
        }
        return "[MODULE] " + message;
    }

    /** The on-screen trace is intentionally English-only, independent of app language. */
    private static String englishOnly(String value) {
        if (value == null || value.length() == 0) return "";
        String translated = value;
        if (containsHan(value)) {
            try { translated = UiLanguageCatalog.toEnglish(value); }
            catch (Throwable ignored) { translated = value; }
        }
        if (!containsHan(translated)) return translated;
        StringBuilder out = new StringBuilder(translated.length());
        boolean masked = false;
        for (int i = 0; i < translated.length(); i++) {
            char c = translated.charAt(i);
            if (isHan(c)) {
                if (!masked) out.append("[localized text]");
                masked = true;
            } else {
                out.append(c);
                masked = false;
            }
        }
        return out.toString();
    }

    private static boolean containsHan(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (isHan(value.charAt(i))) return true;
        }
        return false;
    }

    private static boolean isHan(char c) {
        return (c >= '\u3400' && c <= '\u4dbf')
                || (c >= '\u4e00' && c <= '\u9fff')
                || (c >= '\uf900' && c <= '\ufaff');
    }

    /** Drops known per-frame/per-layout chatter while retaining failures from those paths. */
    private static boolean isNoisy(String message) {
        String lower = message.toLowerCase(Locale.US);
        if (lower.contains("fail") || lower.contains("error")
                || lower.contains("exception") || lower.contains("crash")
                || lower.contains("rejected") || lower.contains("unavailable")) {
            return false;
        }
        return lower.contains("route still settings")
                || lower.contains("sidebar liquid seam progress=")
                || lower.contains("appearance render body cost=")
                || lower.contains("liquid glass draw active")
                || lower.contains("search glass native anchor bounds=")
                || lower.contains("expert tplfile captured")
                || lower.contains("spatial compose state update")
                || lower.contains("spatial graphics layer")
                || lower.contains("recomposition")
                || lower.contains("sensor sample=")
                || lower.contains("frame progress=")
                || lower.contains("animation progress=");
    }

    private static void attach(final Activity value) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            ensureMain().post(new Runnable() { @Override public void run() { attach(value); } });
            return;
        }
        TextView existing = text.get();
        if (existing != null && existing.getContext() == value && existing.getParent() != null) {
            applyTextTheme(value, existing);
            existing.setVisibility(View.VISIBLE);
            refresh(existing);
            return;
        }
        View decor = value.getWindow() == null ? null : value.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        TextView overlay = new TextView(value);
        overlay.setTag("deekseep_hook_log_overlay_v2_fullscreen");
        overlay.setTextSize(TypedValue.COMPLEX_UNIT_SP, 4.75f);
        applyTextTheme(value, overlay);
        overlay.setTypeface(Typeface.MONOSPACE);
        overlay.setLineSpacing(0f, 1.02f);
        overlay.setGravity(Gravity.TOP | Gravity.START);
        overlay.setPadding(dp(value, 4), dp(value, 2), dp(value, 4), dp(value, 2));
        overlay.setClickable(false);
        overlay.setLongClickable(false);
        overlay.setFocusable(false);
        overlay.setFocusableInTouchMode(false);
        overlay.setVerticalScrollBarEnabled(false);
        overlay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // The debug plane itself must be invisible. Only glyphs are drawn; no panel, tint,
        // blur, corner, or dim layer may reveal the overlay's bounds.
        overlay.setBackgroundColor(Color.TRANSPARENT);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.TOP);
        ((ViewGroup) decor).addView(overlay, lp);
        overlay.bringToFront();
        text = new WeakReference<>(overlay);
        refresh(overlay);
    }

    private static void applyTextTheme(Activity activity, TextView overlay) {
        boolean dark;
        try {
            dark = DeekseepUi.isDark(activity);
        } catch (Throwable ignored) {
            int night = activity.getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            dark = night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        // Keep the diagnostic text at 50% opacity while retaining contrast in both themes.
        overlay.setTextColor(dark ? 0x80FFFFFF : 0x80000000);
        overlay.setShadowLayer(1.25f, 0f, 0f,
                dark ? 0xB0000000 : 0xB0FFFFFF);
    }

    private static void detachView(Activity value) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final Activity target = value;
            ensureMain().post(new Runnable() { @Override public void run() { detachView(target); } });
            return;
        }
        TextView overlay = text.get();
        if (overlay != null && (value == null || overlay.getContext() == value)) {
            if (overlay.getParent() instanceof ViewGroup) {
                ((ViewGroup) overlay.getParent()).removeView(overlay);
            }
            text = new WeakReference<>(null);
        }
    }

    private static void postRefresh() {
        synchronized (LOCK) {
            if (refreshPending) return;
            refreshPending = true;
        }
        ensureMain().post(new Runnable() {
            @Override public void run() {
                synchronized (LOCK) { refreshPending = false; }
                TextView overlay = text.get();
                Activity owner = activity.get();
                if (overlay == null && owner != null) attach(owner);
                else if (overlay != null) refresh(overlay);
            }
        });
    }

    private static void refresh(TextView overlay) {
        StringBuilder value = new StringBuilder();
        synchronized (LOCK) {
            for (String line : LINES) {
                if (value.length() > 0) value.append('\n');
                value.append(line);
            }
        }
        overlay.setText(value);
        // Keep the first line fixed at the physical top while content fits. Only after the
        // rendered text reaches the bottom do old lines move above the screen.
        overlay.post(new Runnable() {
            @Override public void run() {
                try {
                    android.text.Layout layout = overlay.getLayout();
                    if (layout == null) return;
                    int viewport = overlay.getHeight()
                            - overlay.getPaddingTop() - overlay.getPaddingBottom();
                    int overflow = Math.max(0, layout.getHeight() - viewport);
                    overlay.scrollTo(0, overflow);
                } catch (Throwable ignored) {}
            }
        });
    }

    private static Handler ensureMain() {
        if (main == null) main = new Handler(Looper.getMainLooper());
        return main;
    }

    private static int dp(Activity activity, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                value, activity.getResources().getDisplayMetrics()));
    }
}
