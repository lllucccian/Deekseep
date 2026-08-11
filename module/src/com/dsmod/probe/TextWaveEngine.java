package com.dsmod.probe;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.File;
import java.io.FileWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared black-to-ocean-blue travelling highlight for host Compose and Android text. */
final class TextWaveEngine {
    private static final String ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_text_wave";
    private static final String SPEED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_text_wave_speed";
    // A broad ocean-blue crest: the saturated section spans about 48% of one repeat,
    // which reads as roughly one third of a glyph at the wavelength used below.
    private static final float[] STOPS = {0f, 0.12f, 0.26f, 0.50f, 0.74f, 1f};
    private static final float DIAGONAL = 0.70710677f;
    private static final Map<Paint, ShaderEntry> SHADERS =
            Collections.synchronizedMap(new WeakHashMap<Paint, ShaderEntry>());
    private static final Map<Object, Boolean> COMPOSE_TEXT_NODES =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());
    private static volatile Method composeNodeInvalidate;
    private static volatile WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static final AtomicInteger frameGeneration = new AtomicInteger();
    private static final AtomicBoolean activeLogged = new AtomicBoolean(false);
    private static final AtomicBoolean framesLogged = new AtomicBoolean(false);
    private static volatile float cachedSpeed = Float.NaN;

    private TextWaveEngine() {}

    static boolean isEnabled() {
        return new File(ENABLED_FILE).isFile();
    }

    static boolean setEnabled(Activity activity, boolean enabled) {
        try {
            File marker = new File(ENABLED_FILE);
            if (enabled) {
                File parent = marker.getParentFile();
                if (parent != null && !parent.isDirectory()) parent.mkdirs();
                FileWriter writer = new FileWriter(marker, false);
                writer.write("1");
                writer.close();
                activeLogged.set(false);
                framesLogged.set(false);
                start(activity);
            } else {
                invalidateComposeNodes();
                if (marker.exists() && !marker.delete()) return false;
                stop(activity);
                invalidateNativeText(activity);
            }
            return true;
        } catch (Throwable error) {
            Main.log("text wave setting failed: " + Main.safeThrowableMessage(error));
            return false;
        }
    }

    static float speed() {
        float value = cachedSpeed;
        if (!Float.isNaN(value)) return value;
        value = 0.55f;
        try {
            File file = new File(SPEED_FILE);
            if (file.isFile() && file.length() <= 64L) {
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.FileReader(file));
                String line = reader.readLine();
                reader.close();
                value = Float.parseFloat(line == null ? "" : line.trim());
            }
        } catch (Throwable ignored) {}
        cachedSpeed = clamp(value, 0.2f, 1.5f);
        return cachedSpeed;
    }

    static boolean setSpeed(float speed) {
        float value = clamp(speed, 0.2f, 1.5f);
        try {
            File file = new File(SPEED_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) parent.mkdirs();
            FileWriter writer = new FileWriter(file, false);
            writer.write(String.format(java.util.Locale.US, "%.2f", value));
            writer.close();
            cachedSpeed = value;
            return true;
        } catch (Throwable error) {
            Main.log("text wave speed setting failed: " + Main.safeThrowableMessage(error));
            return false;
        }
    }

    static void captureComposeTextNode(Object node, Method invalidator) {
        if (node == null || invalidator == null) return;
        invalidator.setAccessible(true);
        composeNodeInvalidate = invalidator;
        COMPOSE_TEXT_NODES.put(node, Boolean.TRUE);
    }

    static PaintState apply(Paint paint, float density) {
        if (!isEnabled() || paint == null || paint.getAlpha() <= 8
                || paint.getTextSize() <= 0f || paint.getShader() != null
                || isProtectedSemanticColor(paint.getColor())) {
            return null;
        }
        boolean lightGlyph = luminance(paint.getColor()) >= 0.48f;
        float resolvedDensity = Math.max(0.5f, density);
        float wavelength = clamp(
                paint.getTextSize() * 0.82f,
                9.5f * resolvedDensity,
                24f * resolvedDensity);
        int alpha = Color.alpha(paint.getColor());
        ShaderEntry entry;
        synchronized (SHADERS) {
            entry = SHADERS.get(paint);
            if (entry == null || Math.abs(entry.wavelength - wavelength) > 0.5f
                    || entry.lightGlyph != lightGlyph || entry.alpha != alpha) {
                entry = new ShaderEntry(wavelength, lightGlyph, alpha);
                SHADERS.put(paint, entry);
            }
        }
        long period = Math.max(360L, Math.round(1180f / speed()));
        float phase = (SystemClock.uptimeMillis() % period) / (float) period * wavelength;
        entry.matrix.reset();
        // Travel from the upper-right toward the lower-left, like refracted light sinking
        // through deep water. Moving along the gradient axis keeps every crest coherent.
        entry.matrix.setTranslate(-phase * DIAGONAL, phase * DIAGONAL);
        entry.shader.setLocalMatrix(entry.matrix);
        Shader previous = paint.getShader();
        paint.setShader(entry.shader);
        if (activeLogged.compareAndSet(false, true)) {
            Main.log("text wave paint active wavelength=" + Math.round(wavelength)
                    + " speed=" + speed());
        }
        return new PaintState(paint, previous);
    }

    static void restore(PaintState state) {
        if (state != null && state.paint != null) state.paint.setShader(state.previous);
    }

    static void start(final Activity activity) {
        if (activity == null || !isEnabled()) return;
        activityRef = new WeakReference<>(activity);
        final int generation = frameGeneration.incrementAndGet();
        final View decor = activity.getWindow() == null
                ? null : activity.getWindow().getDecorView();
        if (decor == null) return;
        decor.post(new Runnable() {
            int frame;
            @Override public void run() {
                Activity current = activityRef.get();
                if (generation != frameGeneration.get() || current != activity
                        || !isEnabled() || activity.isFinishing()
                        || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;
                invalidateComposeNodes();
                invalidateNativeText(decor);
                frame++;
                if (frame == 30 && framesLogged.compareAndSet(false, true)) {
                    Main.log("text wave continuous frames confirmed nodes="
                            + COMPOSE_TEXT_NODES.size());
                }
                // 30 fps is visually continuous for a broad colour wave. Android battery saver
                // halves that again and avoids walking the entire text tree unnecessarily.
                decor.postDelayed(this, Main.isSystemPowerSaver(activity) ? 66L : 33L);
            }
        });
    }

    static void stop(Activity activity) {
        Activity current = activityRef.get();
        if (current == null || current == activity) {
            frameGeneration.incrementAndGet();
            activityRef = new WeakReference<>(null);
        }
    }

    private static void invalidateComposeNodes() {
        Method invalidate = composeNodeInvalidate;
        if (invalidate == null || COMPOSE_TEXT_NODES.isEmpty()) return;
        List<Object> nodes;
        synchronized (COMPOSE_TEXT_NODES) {
            nodes = new ArrayList<>(COMPOSE_TEXT_NODES.keySet());
        }
        for (Object node : nodes) {
            if (node == null) continue;
            try {
                invalidate.invoke(null, node);
            } catch (Throwable ignored) {
                COMPOSE_TEXT_NODES.remove(node);
            }
        }
    }

    private static void invalidateNativeText(Activity activity) {
        if (activity == null || activity.getWindow() == null) return;
        invalidateNativeText(activity.getWindow().getDecorView());
    }

    private static void invalidateNativeText(View view) {
        if (view == null || !view.isShown()) return;
        if (view instanceof TextView) view.postInvalidateOnAnimation();
        if (!(view instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            invalidateNativeText(group.getChildAt(i));
        }
    }

    private static boolean isProtectedSemanticColor(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        if (hsv[1] < 0.42f) return false;
        float hue = hsv[0];
        // Preserve destructive, warning and success text. Existing blue accents join the wave.
        return hue < 185f || hue > 255f;
    }

    private static float luminance(int color) {
        float r = linear(Color.red(color) / 255f);
        float g = linear(Color.green(color) / 255f);
        float b = linear(Color.blue(color) / 255f);
        return r * 0.2126f + g * 0.7152f + b * 0.0722f;
    }

    private static float linear(float value) {
        return value <= 0.04045f
                ? value / 12.92f
                : (float) Math.pow((value + 0.055f) / 1.055f, 2.4f);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (clampInt(alpha, 0, 255) << 24);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return min;
        return Math.max(min, Math.min(max, value));
    }

    static final class PaintState {
        final Paint paint;
        final Shader previous;

        PaintState(Paint paint, Shader previous) {
            this.paint = paint;
            this.previous = previous;
        }
    }

    private static final class ShaderEntry {
        final float wavelength;
        final boolean lightGlyph;
        final int alpha;
        final LinearGradient shader;
        final Matrix matrix = new Matrix();

        ShaderEntry(float wavelength, boolean lightGlyph, int alpha) {
            this.wavelength = wavelength;
            this.lightGlyph = lightGlyph;
            this.alpha = alpha;
            int[] colors = lightGlyph
                    ? new int[]{
                            withAlpha(0xFF9BBDE8, alpha),
                            withAlpha(0xFFBFD8F5, alpha),
                            withAlpha(0xFF2D8FFF, alpha),
                            withAlpha(0xFF9EDAFF, alpha),
                            withAlpha(0xFF3E96F3, alpha),
                            withAlpha(0xFF9BBDE8, alpha)}
                    : new int[]{
                            withAlpha(0xFF07111F, alpha),
                            withAlpha(0xFF0A2346, alpha),
                            withAlpha(0xFF126DE1, alpha),
                            withAlpha(0xFF68BFFF, alpha),
                            withAlpha(0xFF1677FF, alpha),
                            withAlpha(0xFF07111F, alpha)};
            shader = new LinearGradient(
                    0f, 0f,
                    wavelength * DIAGONAL, -wavelength * DIAGONAL,
                    colors, STOPS, Shader.TileMode.REPEAT);
        }
    }
}
