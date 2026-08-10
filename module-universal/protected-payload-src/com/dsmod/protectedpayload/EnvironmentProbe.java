package com.dsmod.protectedpayload;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.util.Locale;

/** Runtime environment checks stored only inside the encrypted protected-build payload. */
public final class EnvironmentProbe {
    private static final int MAX_SCAN_BYTES = 512 * 1024;
    private static final byte XOR_KEY = 0x5A;
    private static final byte[] MARKER_FRIDA = {60, 40, 51, 62, 59};
    private static final byte[] MARKER_GUM_LOOP = {61, 47, 55, 119, 48, 41, 119, 54, 53, 53, 42};
    private static final byte[] MARKER_LINJECTOR = {54, 51, 52, 48, 63, 57, 46, 53, 40};

    private EnvironmentProbe() {}

    public static String detect() {
        int tracer = tracerPid();
        if (tracer > 0) return "tracer";
        String frida = decode(MARKER_FRIDA);
        String gumLoop = decode(MARKER_GUM_LOOP);
        String linjector = decode(MARKER_LINJECTOR);
        if (fileContains(new File("/proc/self/maps"), frida, MAX_SCAN_BYTES)) {
            return "instrumentation-map";
        }
        if (fileContains(new File("/proc/net/unix"), linjector, 256 * 1024)
                || fileContains(new File("/proc/net/unix"), frida, 256 * 1024)) {
            return "instrumentation-socket";
        }
        if (hasThreadMarker(gumLoop, frida)) return "instrumentation-thread";
        return null;
    }

    private static int tracerPid() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/status"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("TracerPid:")) continue;
                return Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static boolean hasThreadMarker(String gumLoop, String frida) {
        File[] tasks;
        try { tasks = new File("/proc/self/task").listFiles(); }
        catch (Throwable ignored) { return false; }
        if (tasks == null) return false;
        for (File task : tasks) {
            if (task == null) continue;
            File comm = new File(task, "comm");
            if (fileContains(comm, gumLoop, 256) || fileContains(comm, frida, 256)) return true;
        }
        return false;
    }

    private static boolean fileContains(File file, String marker, int limit) {
        if (file == null || marker == null || marker.length() == 0) return false;
        try (InputStream input = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            StringBuilder text = new StringBuilder();
            int remaining = limit;
            int count;
            while (remaining > 0 && (count = input.read(buffer, 0,
                    Math.min(buffer.length, remaining))) > 0) {
                text.append(new String(buffer, 0, count, "ISO-8859-1"));
                if (text.toString().toLowerCase(Locale.US).contains(marker)) return true;
                if (text.length() > marker.length() + 4096) {
                    text.delete(0, text.length() - marker.length() - 1);
                }
                remaining -= count;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static String decode(byte[] encoded) {
        char[] decoded = new char[encoded.length];
        for (int i = 0; i < encoded.length; i++) {
            decoded[i] = (char) ((encoded[i] & 0xFF) ^ XOR_KEY);
        }
        return new String(decoded).toLowerCase(Locale.US);
    }
}
