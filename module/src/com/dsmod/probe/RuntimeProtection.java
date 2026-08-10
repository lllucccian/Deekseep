package com.dsmod.probe;

import android.content.Context;
/** Compatibility facade retained for older call sites in the now fully open-source build. */
final class RuntimeProtection {
    private RuntimeProtection() {}

    static boolean enabled() {
        return false;
    }

    static void initialize(Context context) {}

    static boolean allowSensitiveFeature(Context context) {
        return true;
    }

    static String status() {
        return "open-source";
    }
}
