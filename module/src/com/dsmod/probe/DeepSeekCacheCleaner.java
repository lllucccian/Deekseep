package com.dsmod.probe;

import android.content.Context;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

/** Opt-in age-based cleanup limited to cache directories confirmed in DeepSeek's APK. */
final class DeepSeekCacheCleaner {
    static final String ENABLED_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_auto_cache_clean";
    static final String DAYS_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_auto_cache_days";
    private static final String LAST_RUN_FILE =
            "/data/data/com.deepseek.chat/files/deekseep_auto_cache_last_run";
    private static final long DAY_MS = 24L * 60L * 60L * 1000L;
    private static final String[] VERIFIED_CACHE_DIRS = {
            "coil3_disk_cache", "image_cache", "images", "mermaid_cache"
    };
    private static volatile boolean scheduled;

    private DeepSeekCacheCleaner() {}

    static boolean isEnabled() { return new File(ENABLED_FILE).isFile(); }

    static boolean setEnabled(boolean enabled) {
        try {
            File marker = new File(ENABLED_FILE);
            if (enabled) write(marker, "1");
            else if (marker.exists() && !marker.delete()) return false;
            return true;
        } catch (Throwable ignored) { return false; }
    }

    static int days() {
        try {
            int parsed = Integer.parseInt(read(new File(DAYS_FILE)).trim());
            return parsed == 3 || parsed == 7 || parsed == 30 ? parsed : 7;
        } catch (Throwable ignored) { return 7; }
    }

    static boolean setDays(int days) {
        if (days != 3 && days != 7 && days != 30) return false;
        try { write(new File(DAYS_FILE), Integer.toString(days)); return true; }
        catch (Throwable ignored) { return false; }
    }

    static void schedule(final Context context) {
        if (context == null || !isEnabled() || scheduled) return;
        synchronized (DeepSeekCacheCleaner.class) {
            if (scheduled) return;
            long now = System.currentTimeMillis();
            long last = readLong(new File(LAST_RUN_FILE));
            if (last > 0L && now - last < DAY_MS) return;
            scheduled = true;
        }
        final Context app = context.getApplicationContext() == null
                ? context : context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    Result result = clean(app.getCacheDir(), days(), System.currentTimeMillis());
                    Main.log("DeepSeek cache cleanup files=" + result.files
                            + " bytes=" + result.bytes + " ageDays=" + days());
                } catch (Throwable error) {
                    Main.log("DeepSeek cache cleanup failed: " + error);
                } finally {
                    try { write(new File(LAST_RUN_FILE),
                            Long.toString(System.currentTimeMillis())); } catch (Throwable ignored) {}
                    scheduled = false;
                }
            }
        }, "deekseep-cache-cleaner").start();
    }

    static Result clean(File cacheRoot, int ageDays, long now) {
        Result total = new Result();
        if (cacheRoot == null || !cacheRoot.isDirectory()) return total;
        long cutoff = now - Math.max(1, ageDays) * DAY_MS;
        try {
            File canonicalRoot = cacheRoot.getCanonicalFile();
            for (String name : VERIFIED_CACHE_DIRS) {
                File candidate = new File(canonicalRoot, name);
                if (!candidate.exists()) continue;
                File canonical = candidate.getCanonicalFile();
                if (!canonical.getPath().startsWith(
                        canonicalRoot.getPath() + File.separator)) continue;
                cleanChildren(canonicalRoot, canonical, cutoff, total);
            }
        } catch (Throwable ignored) {}
        return total;
    }

    private static void cleanChildren(File cacheRoot, File file, long cutoff, Result result) {
        try {
            File canonical = file.getCanonicalFile();
            if (!canonical.getPath().startsWith(cacheRoot.getPath() + File.separator)) return;
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) for (File child : children) {
                    cleanChildren(cacheRoot, child, cutoff, result);
                }
                return;
            }
            long modified = file.lastModified();
            if (!file.isFile() || modified <= 0L || modified >= cutoff) return;
            long size = file.length();
            if (file.delete()) { result.files++; result.bytes += Math.max(0L, size); }
        } catch (Throwable ignored) {}
    }

    static final class Result {
        int files;
        long bytes;
    }

    private static long readLong(File file) {
        try { return Long.parseLong(read(file).trim()); }
        catch (Throwable ignored) { return 0L; }
    }

    private static String read(File file) throws Exception {
        if (!file.isFile() || file.length() <= 0L || file.length() > 128L) return "";
        FileReader reader = new FileReader(file);
        char[] value = new char[(int) file.length()];
        int count = reader.read(value);
        reader.close();
        return count <= 0 ? "" : new String(value, 0, count);
    }

    private static void write(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IllegalStateException("cannot create config directory");
        }
        File temp = new File(file.getPath() + ".tmp");
        FileWriter writer = new FileWriter(temp, false);
        writer.write(value);
        writer.flush();
        writer.close();
        if (file.exists() && !file.delete()) throw new IllegalStateException("replace failed");
        if (!temp.renameTo(file)) throw new IllegalStateException("commit failed");
    }
}
