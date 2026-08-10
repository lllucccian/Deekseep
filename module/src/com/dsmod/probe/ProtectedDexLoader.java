package com.dsmod.probe;

import android.content.Context;
import android.os.Build;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import dalvik.system.DexClassLoader;

/** Loads the protected-build environment probe from an encrypted secondary DEX. */
final class ProtectedDexLoader {
    private static final String PAYLOAD_RESOURCE =
            "META-INF/com.dsmod.protected/.runtime_payload.dat";
    private static final String PAYLOAD_CLASS =
            "com.dsmod.protectedpayload.EnvironmentProbe";

    private static volatile Method detectMethod;
    private static volatile boolean attempted;
    private static volatile boolean loggedFailure;
    // InMemoryDexClassLoader may lazily read its ByteBuffer after construction. Keep only that
    // plaintext backing alive on API 26+; it never touches disk. Keys, IV and encrypted input are
    // still cleared immediately. API 24/25 maps a temporary DEX file and needs no backing array.
    private static volatile byte[] inMemoryDexBacking;

    private ProtectedDexLoader() {}

    static String detect(Context context) {
        if (!BuildInfo.PROTECTED_BUILD || context == null) return null;
        Method method = resolve(context);
        if (method == null) return null;
        try {
            Object value = method.invoke(null);
            return value instanceof String ? (String) value : null;
        } catch (Throwable error) {
            logFailure("encrypted probe invocation failed", error);
            return null;
        }
    }

    private static Method resolve(Context context) {
        Method cached = detectMethod;
        if (cached != null || attempted) return cached;
        synchronized (ProtectedDexLoader.class) {
            if (detectMethod != null || attempted) return detectMethod;
            attempted = true;
            byte[] encrypted = null;
            byte[] decrypted = null;
            byte[] key = null;
            byte[] iv = null;
            try {
                encrypted = readPayload(ProtectedDexLoader.class.getClassLoader());
                if (encrypted == null || encrypted.length < 64) {
                    throw new IllegalStateException("protected payload missing");
                }
                key = hex(BuildInfo.PROTECTED_PAYLOAD_KEY_A
                        + BuildInfo.PROTECTED_PAYLOAD_KEY_B);
                iv = hex(BuildInfo.PROTECTED_PAYLOAD_IV);
                if (key.length != 32 || iv.length != 16) {
                    throw new IllegalStateException("protected payload key unavailable");
                }
                Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                        new IvParameterSpec(iv));
                decrypted = cipher.doFinal(encrypted);
                ClassLoader loader = createLoader(context, decrypted);
                Class<?> probe = Class.forName(PAYLOAD_CLASS, true, loader);
                detectMethod = probe.getDeclaredMethod("detect");
                detectMethod.setAccessible(true);
                return detectMethod;
            } catch (Throwable error) {
                logFailure("encrypted probe load failed", error);
                return null;
            } finally {
                wipe(encrypted);
                if (decrypted != inMemoryDexBacking) wipe(decrypted);
                wipe(key);
                wipe(iv);
            }
        }
    }

    private static ClassLoader createLoader(Context context, byte[] dex) throws Exception {
        ClassLoader parent = ProtectedDexLoader.class.getClassLoader();
        if (Build.VERSION.SDK_INT >= 26) {
            Class<?> type = Class.forName("dalvik.system.InMemoryDexClassLoader");
            Constructor<?> constructor = type.getConstructor(ByteBuffer.class, ClassLoader.class);
            inMemoryDexBacking = dex;
            return (ClassLoader) constructor.newInstance(ByteBuffer.wrap(dex), parent);
        }
        File directory = new File(context.getCodeCacheDir(), ".ds_runtime");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("code cache unavailable");
        }
        File payload = new File(directory, ".runtime.dex");
        try {
            try (FileOutputStream output = new FileOutputStream(payload)) {
                output.write(dex);
                output.flush();
            }
            return new DexClassLoader(payload.getAbsolutePath(), directory.getAbsolutePath(),
                    null, parent);
        } finally {
            // API 24/25 must load from a file; remove the plaintext copy immediately afterwards.
            try { payload.delete(); } catch (Throwable ignored) {}
        }
    }

    private static byte[] readPayload(ClassLoader loader) throws Exception {
        InputStream input = loader == null ? null : loader.getResourceAsStream(PAYLOAD_RESOURCE);
        if (input == null) input = ProtectedDexLoader.class.getResourceAsStream(
                "/" + PAYLOAD_RESOURCE);
        if (input == null) return null;
        try (InputStream closeable = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream(16 * 1024)) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = closeable.read(buffer)) >= 0) {
                if (count > 0) output.write(buffer, 0, count);
                if (output.size() > 1024 * 1024) {
                    throw new IllegalStateException("protected payload too large");
                }
            }
            return output.toByteArray();
        }
    }

    private static byte[] hex(String value) {
        if (value == null || (value.length() & 1) != 0) return new byte[0];
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int high = Character.digit(value.charAt(i * 2), 16);
            int low = Character.digit(value.charAt(i * 2 + 1), 16);
            if (high < 0 || low < 0) return new byte[0];
            out[i] = (byte) ((high << 4) | low);
        }
        return out;
    }

    private static void wipe(byte[] value) {
        if (value != null) Arrays.fill(value, (byte) 0);
    }

    private static void logFailure(String message, Throwable error) {
        if (loggedFailure) return;
        loggedFailure = true;
        Main.log(message + ": " + error.getClass().getSimpleName());
    }
}
