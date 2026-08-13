package com.dsmod.probe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Runtime symbols for the supported DeepSeek host generations.
 *
 * <p>DeepSeek is minified with R8, so an application update can rename every host class while
 * keeping the actual contract unchanged.  Callers always use the 2.2.x symbol as the stable
 * logical key; this class translates it only when the installed host is the 2.3.0 generation.
 * Keeping the legacy path as an identity mapping is intentional: one module APK therefore works
 * with both host families.</p>
 */
final class HostCompat {
    private static volatile boolean initialized;
    private static volatile boolean v230;
    private static volatile boolean v234;
    private static volatile boolean v236;
    private static volatile boolean googlePlay;
    /**
     * 2.2.1 moved Kotlin Unit out of the Compose helper named {@code ui8}.  The
     * 2.2.2 host restored the old static singleton shape, so treating both 2.2
     * builds as one mapping would make reflection call a non-static field with a
     * null receiver and disable the entire bubble/input hook.
     */
    private static volatile boolean legacyUnitUsesTi8;

    private HostCompat() {}

    static synchronized void initialize(ClassLoader loader) {
        if (initialized) return;
        googlePlay = classExists(loader, "com.pairip.licensecheck.LicenseActivity");
        v234 = hasCompletionRequest(loader, googlePlay ? "gz0" : "nx0");
        // Mainland 2.3.6 keeps the 2.3.4 completion request name but moves the chat ViewModel
        // from kd1 to td1. Do not use mere kd1 absence as a marker: 2.3.6 reuses kd1 for the
        // ViewModel's coroutine continuation, so both names legitimately exist in that APK.
        // Match td1's actual ViewModel contract instead. A GP 2.3.6 table is intentionally not
        // guessed without its APK.
        v236 = !googlePlay && v234 && hasV236ChatViewModel(loader);
        v230 = v234 || hasV230CompletionRequest(loader);
        legacyUnitUsesTi8 = !v230 && hasNonStaticUi8Unit(loader);
        initialized = true;
    }

    static boolean isV230() {
        return v230;
    }

    static boolean isV234() {
        return v234;
    }

    static boolean isV236() {
        return v236;
    }

    static boolean isGooglePlay() {
        return googlePlay;
    }

    static String diagnosticSummary() {
        return "channel=" + (googlePlay ? "google-play" : "mainland")
                + "\ngeneration=" + (v236 ? "2.3.6" : v234 ? "2.3.4"
                : v230 ? "2.3.0" : "2.2.x")
                + "\nlegacyUnit=" + (legacyUnitUsesTi8 ? "ti8" : "ui8");
    }

    /** 2.3.0 moved the canonical editor session state list from field e to f. */
    static String editorSessionStateField() {
        return v230 ? "f" : "e";
    }

    static String generationName() {
        if (v236) return "2.3.6/code249-cn";
        if (v234) return googlePlay ? "2.3.4/code246-gp" : "2.3.4/code245-cn";
        return v230 ? "2.3.0/code237" : "2.2.x";
    }

    static boolean supportsHostVersionName(String versionName) {
        if (versionName == null) return true;
        String value = versionName.trim();
        int suffix = value.indexOf('-');
        if (suffix > 0) value = value.substring(0, suffix);
        return "2.2.0".equals(value) || "2.2.1".equals(value)
                || "2.2.2".equals(value) || "2.3.0".equals(value)
                || "2.3.4".equals(value) || "2.3.6".equals(value);
    }

    static String localApiAuthInterceptorClass() {
        if (v236) return "se0";
        if (v234) return googlePlay ? "eg0" : "se0";
        return v230 ? "td0" : "id0";
    }

    static String localApiHeaderBuilderClass() {
        if (v236) return "lq3";
        if (v234) return googlePlay ? "gs3" : "cq3";
        return v230 ? "tm3" : "jk3";
    }

    static String localApiHeaderSetterMethod() {
        return v230 && !v234 ? "k0" : "l0";
    }

    /** Native event dispatched by DeepSeek's own "continue generating" button. */
    static String resumeMessageEventClass() {
        if (v236) return "xc1";
        if (v234) return googlePlay ? "ce1" : "oc1";
        return v230 ? "ab1" : "ba1";
    }

    /** Chat ViewModel reducer that consumes every native composer event. */
    static String resumeMessageHandlerMethod() {
        return "D";
    }

    static String unitClass() {
        if (v236) return "mu8";
        if (v234) return googlePlay ? "hy8" : "fu8";
        if (v230) return "vl8";
        return legacyUnitUsesTi8 ? "ti8" : "ui8";
    }

    static String unitField() {
        return "a";
    }

    private static boolean hasNonStaticUi8Unit(ClassLoader loader) {
        try {
            Class<?> ui8 = Class.forName("ui8", false, loader);
            java.lang.reflect.Field field = ui8.getDeclaredField("a");
            return !java.lang.reflect.Modifier.isStatic(field.getModifiers());
        } catch (Throwable ignored) {
            // If the probe itself is unavailable, keep the established 2.2.2
            // mapping; the normal constructor will report a precise failure.
            return false;
        }
    }

    private static boolean classExists(ClassLoader loader, String name) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean hasV236ChatViewModel(ClassLoader loader) {
        try {
            Class<?> candidate = Class.forName("td1", false, loader);
            boolean sessionGetter = false;
            boolean reducer = false;
            for (Method method : candidate.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if ("G".equals(method.getName()) && p.length == 0
                        && "lq".equals(method.getReturnType().getSimpleName())) {
                    sessionGetter = true;
                } else if ("M".equals(method.getName()) && p.length == 2
                        && method.getReturnType() == void.class) {
                    reducer = true;
                }
            }
            return sessionGetter && reducer;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasCompletionRequest(ClassLoader loader, String name) {
        try {
            Class<?> candidate = Class.forName(name, false, loader);
            for (Constructor<?> constructor : candidate.getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length == 11
                        && p[0] == String.class
                        && p[2] == String.class
                        && p[4] == boolean.class
                        && p[5] == boolean.class
                        && p[7] == boolean.class
                        && p[8] == String.class
                        && p[9] == String.class
                        && p[10] == int.class) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * The old host also happens to contain an unrelated class named qw0.  Checking the complete
     * request-constructor shape avoids treating that coincidence as a generation marker.
     */
    private static boolean hasV230CompletionRequest(ClassLoader loader) {
        try {
            // 2.2.x keeps the request data class as ew0.  Some 2.2 builds also contain a
            // coroutine helper named qw0 whose constructor happens to resemble the 2.3 request
            // shape when viewed through an obfuscating class loader, so positively identify the
            // legacy request first instead of relying on qw0 alone.
            Class<?> legacy = Class.forName("ew0", false, loader);
            for (Constructor<?> constructor : legacy.getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length == 11
                        && p[0] == int.class
                        && p[1] == String.class
                        && p[2] == Integer.class
                        && p[3] == String.class
                        && p[4] == java.util.List.class
                        && p[5] == boolean.class
                        && p[6] == boolean.class
                        && p[7] == String.class
                        && p[8] == boolean.class
                        && p[9] == String.class
                        && p[10] == String.class) {
                    return false;
                }
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> candidate = Class.forName("qw0", false, loader);
            for (Constructor<?> constructor : candidate.getDeclaredConstructors()) {
                Class<?>[] p = constructor.getParameterTypes();
                if (p.length == 11
                        && p[0] == String.class
                        && p[2] == String.class
                        && p[4] == boolean.class
                        && p[5] == boolean.class
                        && p[7] == boolean.class
                        && p[8] == String.class
                        && p[9] == String.class
                        && p[10] == int.class) {
                    // The 2.3 request class implements the ct0 transport contract. Require that
                    // marker as well so an unrelated old qw0 cannot flip the whole host table.
                    for (Class<?> iface : candidate.getInterfaces()) {
                        if (iface != null && "ct0".equals(iface.getSimpleName())) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    static Class<?> load(ClassLoader loader, String legacyName)
            throws ClassNotFoundException {
        return loader.loadClass(name(legacyName));
    }

    static String name(String legacyName) {
        if (legacyName == null) return null;
        String v230Name = v230 ? name230(legacyName) : legacyName;
        String v234Name = v234 ? name234(v230Name) : v230Name;
        return v236 ? name236(v234Name) : v234Name;
    }

    private static String name230(String legacyName) {
        switch (legacyName) {
            // Settings and heartbeat rendering.
            case "u25": return "t55";
            case "fo2": return "lq2";
            case "ho2": return "nq2";
            case "at7": return "aw7";
            case "ht7": return "hw7";
            case "h78": return "ja8";
            case "i68": return "j98";
            case "yg8": return "ji8";

            // Completion transport and coroutines.
            case "ew0": return "qw0";
            case "rs0": return "ct0";
            case "s92": return "ac2";
            case "r92": return "zb2";
            case "b41": return "y41";
            case "q03": return "x23";
            case "q71": return "n81";
            case "w02": return "e32";
            case "ui8": return "vl8";
            case "n02": return "v22";
            case "uz1": return "c22";
            case "vz1": return "d22";
            case "mb3": return "xd3";
            case "xa3": return "id3";
            case "ib3": return "td3";
            case "u82": return "dq1";
            case "c74": return "r94";
            case "fx6": return "zz6";

            // Chat/session/message.
            case "tp": return "aq";
            case "uo": return "cp";
            case "uo7": return "sr7";
            case "mv": return "vv";
            case "kv": return "tv";
            case "vv7": return "uy7";
            case "lv7": return "ky7";
            case "h83": return "ta3";
            case "za1": return "cc1";
            case "na1": return "nb1";
            case "bu0": return "mu0";
            case "zs0": return "kt0";
            case "au0": return "lu0";
            case "at0": return "lt0";
            case "op5": return "hs5";
            case "kp5": return "ds5";
            case "vx2": return "b03";
            case "yg3": return "v99";
            case "h61": return "g71";
            case "fu0": return "ru0";
            case "uu0": return "hv0";
            case "h1": return "n1";
            case "jm7": return "gp7";
            case "rs7": return "rv7";
            case "xs7": return "xv7";

            // Safety, expert mode, login and serializers.
            case "kb7": return "he7";
            case "sf5": return "ni5";
            case "gf5": return "bi5";
            case "y91": return "xa1";
            case "cy4": return "t05";
            case "px4": return "h05";
            case "x94": return "mc4";
            case "hv": return "qv";
            case "ch4": return "rj4";
            case "m84": return "bb4";

            // History, repository and navigation.
            case "gm8": return "hp8";
            case "pw0": return "bx0";
            case "am8": return "bp8";
            case "sl8": return "so8";
            case "ed0": return "fh";
            case "p68": return "q98";
            case "aw": return "m17";
            case "ve1": return "wg1";
            case "ie": return "pe";
            case "sb1": return "vc1";
            case "rm5": return "mp5";
            case "gf8": return "ii8";
            case "mc": return "tc";

            // Sidebar/Compose primitives.
            case "mq5": return "gt5";
            case "bn2": return "hp2";
            case "n51": return "k61";
            case "cn2": return "ip2";
            case "zm2": return "fp2";
            case "y31": return "v41";
            case "qg5": return "lj5";
            case "lw5": return "fz5";
            case "bm4": return "qo4";
            case "fe7": return "ch7";
            case "sm4": return "hp4";
            case "ce": return "je";
            case "c46": return "w66";
            case "gn9": return "yt9";

            // File/image bridge.
            case "us": return "dt";
            case "pv0": return "cw0";
            case "k31": return "h41";
            case "wu1": return "bx1";
            case "fp": return "mp";
            case "ky2": return "r03";
            case "yu0": return "lv0";
            case "ty0": return "mz0";
            case "su3": return "cx3";
            case "a60": return "m60";
            case "u40": return "e50";
            case "xv0": return "kw0";
            case "jv0": return "xv0";
            case "zv0": return "mw0";

            // Native history endpoint/session maintenance.
            case "lj9": return "vm9";
            case "pl9": return "ml9";
            case "i91": return "fa1";
            case "jb1": return "mc1";
            default: return legacyName;
        }
    }

    /** 2.3.4 re-obfuscated both store channels independently. */
    private static String name234(String name230) {
        if (name230 == null) return null;
        if (googlePlay) {
            switch (name230) {
                case "qw0": return "gz0";
                case "ct0": return "tv0";
                case "aq": return "pq";
                case "cp": return "qp";
                case "vv": return "kw";
                case "tv": return "iw";
                case "he7": return "lp7";
                case "ni5": return "bq5";
                case "bi5": return "jp5";
                case "ky7": return "oa8";
                case "n81": return "mb1";
                case "zb2": return "ag2";
                case "y41": return "x71";
                // x71 is the 2.3.4 GP Flow wrapper; its collector is a83. za5 has the same
                // erased two-argument shape but belongs to an unrelated coroutine contract.
                case "x23": return "a83";
                case "xa1": return "zd1";
                case "t05": return "b75";
                case "h05": return "o65";
                case "cc1": return "ef1";
                case "fh": return "ih";
                case "mp5": return "fx5";
                case "hp2": return "kt2";
                case "k61": return "j91";
                case "tc": return "ika";
                case "gt5": return "vu8";
                case "fp2": return "it2";
                case "v41": return "u71";
                case "id3": return "mi3";
                case "lj5": return "zq5";
                case "fz5": return "e76";
                case "qo4": return "ru4";
                case "hp4": return "iv4";
                case "ii8": return "uu8";
                case "ip2": return "lt2";
                case "lq2": return "tu2";
                case "nq2": return "vu2";
                case "ja8": return "pm8";
                case "j98": return "ql8";
                case "hw7": return "k88";
                case "aw7": return "d88";
                case "hv0": return "xx0";
                case "rv7": return "u78";
                case "sr7": return "n38";
                case "r94": return "af4";
                case "mc4": return "zh4";
                case "vm9": return "c0a";
                case "vl8": return "hy8";
                case "w66": return "we6";
                case "xv0": return "ny0";
                case "xv7": return "a88";
                case "zz6": return "la7";
                case "yt9": return "h7a";
                case "dt": return "tt";
                case "cx3": return "r24";
                case "e32": return "z62";
                case "b03": return "w43";
                case "kw0": return "az0";
                case "lv0": return "by0";
                case "mw0": return "cz0";
                case "qv": return "fw";
                case "rj4": return "ip4";
                case "td3": return "xi3";
                case "xd3": return "bj3";
                case "d22": return "w52";
                case "c22": return "v52";
                case "v22": return "p62";
                case "uy7": return "ya8";
                case "wg1": return "ak1";
                case "pe": return "se";
                case "vc1": return "yf1";
                case "bx0": return "rz0";
                case "bp8": return "s19";
                case "hp8": return "y19";
                case "so8": return "j19";
                case "q98": return "sd7";
                // WCDB chat-session directory DAO.  an9 is an unrelated multi-purpose R8
                // class; using it made the local-conversation cloud-prune guard install x0.
                case "m17": return "g2a";
                case "ml9": return "u4a";
                case "fa1": return "rm1";
                case "dq1": return "lw8";
                case "e50": return "c60";
                case "cw0": return "sy0";
                // Native attachment composer. Its upload entry is
                // d71.u(r02,String) on the 2.3.4 Google Play host.
                case "h41": return "d71";
                // Native composer upload metadata. 2.3.4 reuses bx1 for an unrelated Compose
                // runtime exception, so carrying the 2.3.0 name forward resolves the wrong type.
                case "bx1": return "r02";
                case "ac2": return "bg2";
                // Kotlin immutable empty-list singleton. Do not confuse this with the unrelated
                // 2.3.4 GP coroutine class that also happens to be named gp7.
                case "gp7": return "y08";
                default: return name230;
            }
        }
        switch (name230) {
            case "qw0": return "nx0";
            case "ct0": return "gg7";
            case "aq": return "lq";
            case "cp": return "mp";
            case "vv": return "gw";
            case "tv": return "ew";
            case "he7": return "ql7";
            case "ni5": return "no5";
            case "bi5": return "vn5";
            case "ky7": return "o68";
            case "n81": return "t91";
            case "zb2": return "ce2";
            case "y41": return "e61";
            case "x23": return "v53";
            case "xa1": return "fc1";
            case "t05": return "n45";
            case "h05": return "a45";
            case "cc1": return "kd1";
            case "fh": return "fh";
            case "mp5": return "ov5";
            case "hp2": return "lr2";
            case "k61": return "q71";
            case "tc": return "sc";
            case "gt5": return "gv7";
            case "fp2": return "jr2";
            case "v41": return "b61";
            case "id3": return "ig3";
            case "lj5": return "lp5";
            case "fz5": return "i56";
            case "qo4": return "ks4";
            case "hp4": return "bt4";
            case "ii8": return "rq8";
            case "ip2": return "mr2";
            case "lq2": return "us2";
            case "nq2": return "ws2";
            case "ja8": return "qi8";
            case "j98": return "qh8";
            case "hw7": return "l48";
            case "aw7": return "e48";
            case "hv0": return "ew0";
            case "rv7": return "v38";
            case "sr7": return "sz7";
            case "r94": return "wc4";
            case "mc4": return "sf4";
            case "vm9": return "uv9";
            case "vl8": return "fu8";
            case "w66": return "ad6";
            case "xv0": return "uw0";
            case "xv7": return "b48";
            case "zz6": return "c77";
            case "yt9": return "v2a";
            case "dt": return "pt";
            case "cx3": return "n04";
            case "e32": return "e52";
            case "b03": return "r23";
            case "kw0": return "hx0";
            case "lv0": return "iw0";
            case "mw0": return "jx0";
            case "qv": return "bw";
            case "rj4": return "an4";
            case "td3": return "tg3";
            case "xd3": return "xg3";
            case "d22": return "b42";
            // Kotlin Continuation. a75 is an unrelated callback in the mainland 2.3.4 APK;
            // mapping uz1 to it breaks the editor's WCDB message hydrator at runtime.
            case "c22": return "a42";
            case "v22": return "u42";
            case "uy7": return "y68";
            case "wg1": return "gi1";
            case "pe": return "pe";
            case "vc1": return "ee1";
            case "bx0": return "yx0";
            case "bp8": return "mx8";
            case "hp8": return "sx8";
            case "so8": return "dx8";
            case "q98": return "yh8";
            // WCDB chat-session directory DAO.  zc is an unrelated multi-purpose R8 class.
            case "m17": return "p6a";
            case "ml9": return "tj9";
            case "fa1": return "xk1";
            case "dq1": return "tn4";
            case "e50": return "u50";
            case "cw0": return "zw0";
            // Native attachment composer. Carrying h41 forward resolves an unrelated
            // coroutine and makes every Local API image fail before upload.
            case "h41": return "k51";
            // Native composer upload metadata; bx1 is a Compose exception in mainland 2.3.4.
            case "bx1": return "vy1";
            case "ac2": return "de2";
            case "gp7": return "dx7";
            default: return name230;
        }
    }

    /** Mainland 2.3.6 (code 249) re-obfuscated the 2.3.4 application classes again. */
    private static String name236(String name234) {
        if (name234 == null) return null;
        switch (name234) {
            case "ql7": return "xl7";
            case "no5": return "uo5";
            case "vn5": return "co5";
            case "o68": return "u68";
            case "t91": return "ca1";
            case "ce2": return "le2";
            case "e61": return "h61";
            case "v53": return "c63";
            case "fc1": return "oc1";
            case "n45": return "v45";
            case "a45": return "i45";
            case "kd1": return "td1";
            case "ov5": return "vv5";
            case "lr2": return "tr2";
            // The session-list composables stayed in sc for code249. fh0 is an unrelated
            // runtime helper there; mapping to it made navigation and multi-select hook zero
            // methods even though the class itself existed.
            case "sc": return "sc";
            case "gv7": return "zq8";
            case "ks4": return "ts4";
            case "bt4": return "kt4";
            case "rq8": return "yq8";
            case "mr2": return "ur2";
            case "us2": return "ct2";
            case "ws2": return "et2";
            case "qi8": return "wi8";
            case "qh8": return "wh8";
            case "l48": return "r48";
            case "e48": return "k48";
            case "v38": return "b48";
            // Empty Compose marker implemented by the multi-purpose c3a runtime singleton.
            // The unrelated 2.3.4 Flow class also named e61 is translated to h61 above.
            case "b61": return "e61";
            case "sz7": return "yz7";
            case "wc4": return "gd4";
            case "sf4": return "bg4";
            case "ge4": return "qe4";
            // pv0/zw0 fork-file coroutine. Discriminator 2 in b70 is the ForkFileRequest leg.
            case "m60": return "b70";
            case "uv9": return "aw9";
            case "fu8": return "mu8";
            case "ad6": return "hd6";
            case "b48": return "h48";
            case "c77": return "k77";
            case "v2a": return "c3a";
            case "n04": return "w04";
            case "e52": return "m52";
            case "r23": return "y23";
            case "tg3": return "ch3";
            case "ig3": return "rg3";
            case "xg3": return "gh3";
            case "b42": return "j42";
            case "a42": return "i42";
            case "u42": return "c52";
            case "ee1": return "ed1";
            case "mx8": return "sx8";
            case "sx8": return "yx8";
            case "dx8": return "jx8";
            case "yh8": return "ei8";
            case "p6a": return "v6a";
            case "tj9": return "zj9";
            case "xk1": return "gl1";
            case "tn4": return "rc5";
            case "de2": return "me2";
            case "dx7": return "jx7";
            // Network Result.Success and uploaded FileInfo were both re-obfuscated in
            // code249. Without these two mappings the file fork succeeds on the server but
            // the module discards the returned Vision credential as an unknown wrapper.
            case "ds5": return "oy5";
            case "mp": return "wp";
            // code249 re-obfuscated the 2.3.4 mainland attachment composer.
            case "k51": return "n51";
            // Native image/file picker metadata. 2.3.4 CN calls this vy1, while code249's
            // composer n51.u accepts dz1. Leaving the old name here made Agent screenshots save
            // successfully but fail before DeepSeek's real uploader was invoked.
            case "vy1": return "dz1";
            default: return name234;
        }
    }

    static String method(String legacyOwner, String legacyMethod) {
        if (legacyOwner == null || legacyMethod == null) return legacyMethod;
        String mapped = v230 ? method230(legacyOwner, legacyMethod) : legacyMethod;
        if (!v234) return mapped;
        // Kotlin runBlocking moved from f0 to rc5.c0 in mainland code249.
        if (v236 && "u82".equals(legacyOwner) && "K".equals(legacyMethod)) {
            return "c0";
        }
        if (v236 && "mc".equals(legacyOwner) && "e".equals(legacyMethod)) {
            return "k";
        }
        if (v236 && "mc".equals(legacyOwner) && "f".equals(legacyMethod)) {
            return "l";
        }
        if ("mc".equals(legacyOwner) && "e".equals(legacyMethod)) {
            return googlePlay ? "b" : "d";
        }
        if ("mc".equals(legacyOwner) && "f".equals(legacyMethod)) {
            return googlePlay ? "c" : "e";
        }
        if ("mq5".equals(legacyOwner) && "i".equals(legacyMethod)) {
            return googlePlay ? "m" : "k";
        }
        if ("qg5".equals(legacyOwner) && "w".equals(legacyMethod)) {
            return googlePlay ? "t" : "u";
        }
        if ("bm4".equals(legacyOwner) && "w".equals(legacyMethod)) {
            return googlePlay ? "v" : "u";
        }
        if ("p68".equals(legacyOwner) && "a".equals(legacyMethod)) {
            // 2.3.4 uses a synthetic WCDB Transaction: sd7.d on GP, yh8.b on CN.
            return googlePlay ? "d" : "b";
        }
        if ("aw".equals(legacyOwner) && "a".equals(legacyMethod)) {
            // Static directory reader: g2a.s on GP, p6a.h on CN.
            return googlePlay ? "s" : "h";
        }
        // The Play 2.3.4 live-message class inserted one method before the JSON patch helper.
        if (googlePlay && "mv".equals(legacyOwner) && "i".equals(mapped)) return "j";
        return mapped;
    }

    private static String method230(String legacyOwner, String legacyMethod) {
        if ("u25".equals(legacyOwner) && "i".equals(legacyMethod)) return "l";
        if (("fo2".equals(legacyOwner) || "ho2".equals(legacyOwner))
                && "g".equals(legacyMethod)) return "e";
        if ("yg8".equals(legacyOwner) && "b".equals(legacyMethod)) return "e";
        if ("mc".equals(legacyOwner)) {
            if ("e".equals(legacyMethod)) return "b";
            if ("f".equals(legacyMethod)) return "c";
        }
        if ("mq5".equals(legacyOwner) && "i".equals(legacyMethod)) return "l";
        if ("qg5".equals(legacyOwner) && "w".equals(legacyMethod)) return "s";
        if ("bm4".equals(legacyOwner)) {
            if ("i".equals(legacyMethod)) return "h";
            if ("k".equals(legacyMethod)) return "j";
            if ("t".equals(legacyMethod)) return "q";
            if ("w".equals(legacyMethod)) return "t";
        }
        if ("p68".equals(legacyOwner) && "a".equals(legacyMethod)) return "d";
        if ("aw".equals(legacyOwner) && "a".equals(legacyMethod)) return "g";
        if ("ed0".equals(legacyOwner) && "h".equals(legacyMethod)) return "n";
        if ("i91".equals(legacyOwner)) {
            if ("a".equals(legacyMethod)) return "c";
            if ("b".equals(legacyMethod)) return "e";
            if ("c".equals(legacyMethod)) return "f";
        }
        if ("u82".equals(legacyOwner)) {
            if ("K".equals(legacyMethod)) return "f0";
            if ("P".equals(legacyMethod)) return "p0";
        }
        if ("mv".equals(legacyOwner) || "uo".equals(legacyOwner)) {
            return messageMethod(legacyMethod);
        }
        return legacyMethod;
    }

    static String sessionMergeMethod() {
        return v234 ? "x" : (v230 ? "u" : "u");
    }

    static String sessionReplaceMethod() {
        return v234 ? "t" : "q";
    }

    static String sessionReplaceWithTextMethod() {
        return v234 ? "s" : "p";
    }

    /** Suspend endpoint used to create the hidden session owned by the local API. */
    static String localApiSessionCreateMethod() {
        if (v234) return googlePlay ? "a" : "u";
        return method("i91", "a");
    }

    /** Suspend endpoint used to delete one hidden local-API session. */
    static String localApiSessionDeleteMethod() {
        if (v234) return googlePlay ? "c" : "w";
        return method("i91", "c");
    }

    /** Request data class accepted by {@link #localApiSessionDeleteMethod()}. */
    static String localApiSessionDeleteRequestClass() {
        if (v234) return googlePlay ? "of1" : "ud1";
        return name("jb1");
    }

    static String sessionMessageMapField() {
        return "f";
    }

    /** Built-in settings analytics dispatcher for versions with a verified symbol table. */
    static Method settingsEntryMethod(ClassLoader loader) {
        if (!v234) return null;
        try {
            Class<?> owner = Class.forName(v236 ? "c3a"
                    : googlePlay ? "h7a" : "v2a", false, loader);
            String expected = googlePlay ? "x" : "z";
            for (Method method : owner.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (expected.equals(method.getName())
                        && p.length == 3
                        && p[0] == String.class
                        && p[1] == Boolean.class
                        && p[2] == int.class) {
                    method.setAccessible(true);
                    return method;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Native "data used to improve experience" Compose control for every supported host. */
    static Method trainingControlMethod(ClassLoader loader) {
        String[][] candidates;
        if (v236) {
            candidates = new String[][]{{"no9", "k"}};
        } else if (v234) {
            candidates = googlePlay
                    ? new String[][]{{"fe5", "z"}, {"ym9", "j"}}
                    : new String[][]{{"ym9", "j"}, {"fe5", "z"}};
        } else if (v230) {
            candidates = googlePlay
                    ? new String[][]{{"m12", "r"}, {"zj8", "l"}}
                    : new String[][]{{"zj8", "l"}, {"m12", "r"}};
        } else {
            candidates = new String[][]{{"hf8", "Q"}};
        }
        for (String[] candidate : candidates) {
            Method method = findStaticMethod(loader, candidate[0], candidate[1], 5);
            if (method == null) continue;
            Class<?>[] p = method.getParameterTypes();
            if (p[0] == Boolean.class && p[4] == int.class) return method;
        }
        return null;
    }

    /** Root Compose renderer for the normal/forced client-update dialog. */
    static Method updateDialogMethod(ClassLoader loader) {
        String[][] candidates;
        if (v236) {
            candidates = new String[][]{{"ea5", "g"}};
        } else if (v234) {
            candidates = googlePlay
                    ? new String[][]{{"ss6", "c"}, {"fa5", "i"}}
                    : new String[][]{{"fa5", "i"}, {"ss6", "c"}};
        } else if (v230) {
            candidates = googlePlay
                    ? new String[][]{{"bh6", "c"}, {"w85", "a"}}
                    : new String[][]{{"w85", "a"}, {"bh6", "c"}};
        } else {
            candidates = new String[][]{{"o65", "h"}};
        }
        for (String[] candidate : candidates) {
            Method method = findStaticMethod(loader, candidate[0], candidate[1], 2);
            if (method == null) continue;
            Class<?>[] p = method.getParameterTypes();
            if (p[0] == int.class) return method;
        }
        return null;
    }

    private static Method findStaticMethod(ClassLoader loader, String owner, String name,
                                           int parameterCount) {
        try {
            Class<?> type = Class.forName(owner, false, loader);
            for (Method method : type.getDeclaredMethods()) {
                if (name.equals(method.getName())
                        && Modifier.isStatic(method.getModifiers())
                        && method.getParameterCount() == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** 2.3.0 inserted methods into the abstract chat-message contract. */
    static String messageMethod(String legacyMethod) {
        if (!v230 || legacyMethod == null) return legacyMethod;
        switch (legacyMethod) {
            case "A": return "C";
            case "B": return "D";
            case "C": return "E";
            case "D": return "F";
            case "E": return "G";
            case "F": return "H";
            case "G": return "I";
            case "H": return "J";
            case "I": return "K";
            case "J": return "L";
            case "K": return "M";
            case "L": return "N";
            case "N": return "O";
            case "O": return "P";
            case "P": return "Q";
            case "Q": return "T";
            case "R": return "U";
            case "S": return "V";
            case "g": return "e";
            case "e": return "f";
            case "f": return "g";
            case "l": return "n";
            case "m": return "q";
            case "n": return "r";
            case "q": return "s";
            case "r": return "t";
            case "s": return "u";
            case "t": return "v";
            case "u": return "w";
            case "v": return "x";
            case "w": return "y";
            case "x": return "z";
            case "y": return "A";
            case "z": return "B";
            default: return legacyMethod;
        }
    }

    static Method publicMessageMethod(Object message, String legacyName,
                                      Class<?>... parameterTypes)
            throws NoSuchMethodException {
        return message.getClass().getMethod(messageMethod(legacyName), parameterTypes);
    }

    static String instanceMethod(Object value, String legacyName) {
        if (!v230 || value == null) return legacyName;
        String simple = value.getClass().getSimpleName();
        // 2.3.0 uses vv/tv; 2.3.4 re-obfuscates the same dynamic/static message
        // implementations again (gw/ew on mainland, kw/iw on GP). Comparing only the 2.3.0
        // names made cold-start response preservation call the wrong accessors on 2.3.4.
        return ("vv".equals(simple) || "tv".equals(simple)
                || name("mv").equals(simple) || name("kv").equals(simple))
                ? messageMethod(legacyName) : legacyName;
    }

    static boolean simpleNameIs(Object value, String legacyName) {
        return value != null && name(legacyName).equals(value.getClass().getSimpleName());
    }

    /**
     * Static history messages gained one serialized field before message_id in 2.3.0.  The
     * logical fields used by the module therefore shift by one from f..A.
     */
    static String staticMessageField(Object value, String legacyField) {
        if (!v230 || value == null || legacyField == null
                || (!"tv".equals(value.getClass().getSimpleName())
                && !"vv".equals(value.getClass().getSimpleName())
                && !name("mv").equals(value.getClass().getSimpleName())
                && !name("kv").equals(value.getClass().getSimpleName()))) {
            return legacyField;
        }
        if (legacyField.length() != 1) return legacyField;
        char field = legacyField.charAt(0);
        if (field >= 'f' && field < 'z') return String.valueOf((char) (field + 1));
        if (field == 'z') return "A";
        if (field == 'A') return "B";
        return legacyField;
    }
}
