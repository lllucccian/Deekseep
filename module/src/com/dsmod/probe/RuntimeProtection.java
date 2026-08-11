package com.dsmod.probe;

import android.content.Context;

/** The public source edition has no protected runtime or closed sensitive feature. */
final class RuntimeProtection {
    private RuntimeProtection() {}

    static boolean enabled() { return false; }

    static void initialize(Context context) {}

    static boolean allowSensitiveFeature(Context context) { return true; }

    static String status() { return "open-source"; }
}
