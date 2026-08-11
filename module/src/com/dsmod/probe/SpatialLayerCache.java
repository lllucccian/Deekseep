package com.dsmod.probe;

import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashSet;

/**
 * Builds two derived planes from a simple imported wallpaper without adding an ML dependency.
 *
 * <p>The existing edge-connected cutout mask supplies the subject plane. Removed subject pixels
 * in the back plate are reconstructed from a coarse field of known background samples. This is
 * deliberately a derived cache: the original user image is never modified.</p>
 */
final class SpatialLayerCache {
    private static final String CACHE_VERSION = "v2";
    private static final String CACHE_DIR =
            ChatAppearance.ROOT_DIR + "/spatial_cache";
    private static final Object LOCK = new Object();
    private static final HashSet<String> IN_FLIGHT = new HashSet<>();

    interface Completion {
        void onComplete(boolean ready);
    }

    static final class Layers {
        final String key;
        final File backplate;
        final File midground;

        Layers(String key, File backplate, File midground) {
            this.key = key;
            this.backplate = backplate;
            this.midground = midground;
        }

        boolean ready() {
            return backplate.isFile() && backplate.length() > 0L
                    && midground.isFile() && midground.length() > 0L;
        }
    }

    private SpatialLayerCache() {}

    static Layers filesFor(File source) {
        String identity = source == null ? CACHE_VERSION + "|missing"
                : CACHE_VERSION + "|" + source.getAbsolutePath() + "|" + source.length()
                + "|" + source.lastModified();
        String key = Integer.toHexString(identity.hashCode())
                + "_" + Long.toHexString(source == null ? 0L : source.length());
        File directory = new File(CACHE_DIR);
        return new Layers(
                key,
                new File(directory, "back_" + key + ".png"),
                new File(directory, "mid_" + key + ".png"));
    }

    static void generateAsync(
            final File source, final Completion completion) {
        final Layers layers = filesFor(source);
        if (layers.ready()) {
            if (completion != null) completion.onComplete(true);
            return;
        }
        synchronized (LOCK) {
            if (!IN_FLIGHT.add(layers.key)) return;
        }
        Thread worker = new Thread(new Runnable() {
            @Override public void run() {
                boolean ready = false;
                try {
                    ready = generate(source, layers);
                } catch (Throwable t) {
                    Main.log("spatial layer generation failed: " + t);
                } finally {
                    synchronized (LOCK) {
                        IN_FLIGHT.remove(layers.key);
                    }
                    if (completion != null) {
                        try {
                            completion.onComplete(ready);
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }, "Deekseep-spatial-layers");
        worker.setDaemon(true);
        worker.start();
    }

    private static boolean generate(File source, Layers layers)
            throws Exception {
        if (source == null || !source.isFile()) return false;
        File directory = layers.backplate.getParentFile();
        if (!directory.isDirectory() && !directory.mkdirs()) return false;

        Bitmap sourceBitmap = ChatAppearance.loadBitmap(source, 1800, 1800);
        if (sourceBitmap == null) return false;
        int width = sourceBitmap.getWidth();
        int height = sourceBitmap.getHeight();
        int count = width * height;
        int[] sourcePixels = new int[count];
        sourceBitmap.getPixels(
                sourcePixels, 0, width, 0, 0, width, height);
        byte[] keepMask = ImageCutoutUi.detectBackgroundMask(
                sourcePixels, width, height);
        if (keepMask == null || keepMask.length != count) {
            sourceBitmap.recycle();
            return false;
        }

        int kept = 0;
        for (byte value : keepMask) {
            if ((value & 0xFF) >= 128) kept++;
        }
        float keptFraction = kept / (float) Math.max(1, count);
        // A failed segmentation that keeps almost nothing/everything is worse than a flat plane.
        if (keptFraction < 0.02f || keptFraction > 0.92f) {
            sourceBitmap.recycle();
            Main.log("spatial layer mask rejected fraction=" + keptFraction);
            return false;
        }

        int[] midgroundPixels = buildMidgroundPixels(
                sourcePixels, keepMask);
        int[] backplatePixels = buildBackplatePixels(
                sourcePixels, keepMask, width, height);

        Bitmap midground = Bitmap.createBitmap(
                midgroundPixels, width, height, Bitmap.Config.ARGB_8888);
        Bitmap backplate = Bitmap.createBitmap(
                backplatePixels, width, height, Bitmap.Config.ARGB_8888);
        boolean wroteBack = writePng(backplate, layers.backplate);
        boolean wroteMid = writePng(midground, layers.midground);
        backplate.recycle();
        midground.recycle();
        sourceBitmap.recycle();
        boolean ready = wroteBack && wroteMid && layers.ready();
        if (ready) {
            Main.log("spatial optical planes ready key=" + layers.key
                    + " subject=" + Math.round(keptFraction * 100f) + "%");
        }
        return ready;
    }

    static int[] buildMidgroundPixels(
            int[] pixels, byte[] keepMask) {
        int count = Math.min(pixels.length, keepMask.length);
        int[] result = new int[count];
        for (int index = 0; index < count; index++) {
            int sourceColor = pixels[index];
            int keep = keepMask[index] & 0xFF;
            int sourceAlpha = sourceColor >>> 24;
            int midAlpha = sourceAlpha * keep / 255;
            result[index] =
                    (sourceColor & 0x00FFFFFF) | (midAlpha << 24);
        }
        return result;
    }

    static int[] buildBackplatePixels(
            int[] pixels, byte[] keepMask, int width, int height) {
        int[] backgroundField = buildBackgroundField(
                pixels, keepMask, width, height);
        int count = Math.min(
                Math.min(pixels.length, keepMask.length),
                backgroundField.length);
        int[] result = new int[count];
        for (int index = 0; index < count; index++) {
            int keep = keepMask[index] & 0xFF;
            result[index] = keep == 0
                    ? pixels[index]
                    : blend(pixels[index], backgroundField[index], keep);
        }
        return result;
    }

    static int[] buildBackgroundField(
            int[] pixels, byte[] keepMask, int width, int height) {
        int columns = Math.max(8, Math.min(48, (width + 31) / 32));
        int rows = Math.max(8, Math.min(48, (height + 31) / 32));
        int cells = columns * rows;
        long[] red = new long[cells];
        long[] green = new long[cells];
        long[] blue = new long[cells];
        int[] samples = new int[cells];
        long globalRed = 0L;
        long globalGreen = 0L;
        long globalBlue = 0L;
        int globalSamples = 0;

        for (int y = 0; y < height; y++) {
            int cellY = Math.min(rows - 1, y * rows / height);
            int row = y * width;
            for (int x = 0; x < width; x++) {
                int index = row + x;
                if ((keepMask[index] & 0xFF) > 16) continue;
                int color = pixels[index];
                int cell = cellY * columns
                        + Math.min(columns - 1, x * columns / width);
                int r = red(color);
                int g = green(color);
                int b = blue(color);
                red[cell] += r;
                green[cell] += g;
                blue[cell] += b;
                samples[cell]++;
                globalRed += r;
                globalGreen += g;
                globalBlue += b;
                globalSamples++;
            }
        }
        int fallback = globalSamples == 0 ? 0xFF202020
                : rgb(
                        (int) (globalRed / globalSamples),
                        (int) (globalGreen / globalSamples),
                        (int) (globalBlue / globalSamples));
        int[] colors = new int[cells];
        for (int cell = 0; cell < cells; cell++) {
            if (samples[cell] > 0) {
                colors[cell] = rgb(
                        (int) (red[cell] / samples[cell]),
                        (int) (green[cell] / samples[cell]),
                        (int) (blue[cell] / samples[cell]));
                continue;
            }
            int row = cell / columns;
            int column = cell % columns;
            int left = column - 1;
            while (left >= 0
                    && samples[row * columns + left] == 0) left--;
            int right = column + 1;
            while (right < columns
                    && samples[row * columns + right] == 0) right++;
            if (left >= 0 && right < columns) {
                float fraction = (column - left) / (float) (right - left);
                colors[cell] = mix(
                        averageColor(red, green, blue, samples,
                                row * columns + left, fallback),
                        averageColor(red, green, blue, samples,
                                row * columns + right, fallback),
                        fraction);
            } else if (left >= 0) {
                colors[cell] = averageColor(
                        red, green, blue, samples,
                        row * columns + left, fallback);
            } else if (right < columns) {
                colors[cell] = averageColor(
                        red, green, blue, samples,
                        row * columns + right, fallback);
            } else {
                colors[cell] = fallback;
            }
        }

        int[] field = new int[width * height];
        for (int y = 0; y < height; y++) {
            float gridY = (y + 0.5f) * rows / height - 0.5f;
            int top = Math.max(0, Math.min(rows - 1,
                    (int) Math.floor(gridY)));
            int bottom = Math.min(rows - 1, top + 1);
            float fy = Math.max(0f, Math.min(1f, gridY - top));
            int row = y * width;
            for (int x = 0; x < width; x++) {
                float gridX = (x + 0.5f) * columns / width - 0.5f;
                int left = Math.max(0, Math.min(columns - 1,
                        (int) Math.floor(gridX)));
                int right = Math.min(columns - 1, left + 1);
                float fx = Math.max(0f, Math.min(1f, gridX - left));
                int topColor = mix(
                        colors[top * columns + left],
                        colors[top * columns + right], fx);
                int bottomColor = mix(
                        colors[bottom * columns + left],
                        colors[bottom * columns + right], fx);
                field[row + x] = mix(topColor, bottomColor, fy);
            }
        }
        return field;
    }

    private static int averageColor(
            long[] red, long[] green, long[] blue, int[] samples,
            int index, int fallback) {
        int count = samples[index];
        if (count <= 0) return fallback;
        return rgb(
                (int) (red[index] / count),
                (int) (green[index] / count),
                (int) (blue[index] / count));
    }

    private static int blend(int original, int replacement, int amount) {
        float fraction = Math.max(0f, Math.min(1f, amount / 255f));
        int mixed = mix(original, replacement, fraction);
        return (original & 0xFF000000) | (mixed & 0x00FFFFFF);
    }

    private static int mix(int first, int second, float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        float inverse = 1f - f;
        return rgb(
                Math.round(red(first) * inverse
                        + red(second) * f),
                Math.round(green(first) * inverse
                        + green(second) * f),
                Math.round(blue(first) * inverse
                        + blue(second) * f));
    }

    private static int red(int color) {
        return (color >>> 16) & 0xFF;
    }

    private static int green(int color) {
        return (color >>> 8) & 0xFF;
    }

    private static int blue(int color) {
        return color & 0xFF;
    }

    private static int rgb(int red, int green, int blue) {
        return 0xFF000000
                | ((red & 0xFF) << 16)
                | ((green & 0xFF) << 8)
                | (blue & 0xFF);
    }

    private static boolean writePng(Bitmap bitmap, File target) {
        File temporary = new File(
                target.getParentFile(), target.getName() + ".tmp");
        FileOutputStream output = null;
        try {
            output = new FileOutputStream(temporary, false);
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                return false;
            }
            output.flush();
            try {
                output.getFD().sync();
            } catch (Throwable ignored) {}
            output.close();
            output = null;
            if (target.isFile() && !target.delete()) return false;
            return temporary.renameTo(target);
        } catch (Throwable t) {
            Main.log("spatial cache write failed: " + t);
            return false;
        } finally {
            if (output != null) {
                try {
                    output.close();
                } catch (Throwable ignored) {}
            }
            if (temporary.isFile() && !target.isFile()) {
                try {
                    temporary.delete();
                } catch (Throwable ignored) {}
            }
        }
    }
}
