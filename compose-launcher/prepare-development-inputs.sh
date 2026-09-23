#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="$PROJECT_DIR/app/build/generated/protectedInputs"

rm -rf "$OUT"
mkdir -p "$OUT/src/com/dsmod/probe" "$OUT/resources"

cat > "$OUT/src/com/dsmod/probe/BuildInfo.java" <<EOF
package com.dsmod.probe;
public final class BuildInfo {
    public static final String API_VERSION = "universal (Xposed API 82-102 verified)";
    public static final String MODULE_VERSION = "1.8";
    public static final String BUILD_EDITION = "Open";
    public static final String DISPLAY_VERSION = MODULE_VERSION + " " + BUILD_EDITION;
    public static final String BUILD_DATE = "$(date '+%Y-%m-%d %H:%M')";
    public static final boolean GOOGLE_PLAY = false;
    public static final boolean PROTECTED_BUILD = false;
    public static final boolean LOCAL_API_INCLUDED = false;
    public static final String CLOUD_LOCAL_API_PAYLOAD_NAME = "";
    public static final boolean GOOGLE_V241_BASIC = false;
    public static final boolean SHI_V5_V241 = false;
    public static final String SHI_V5_HOST_PACKAGE = "";
    public static final String SHI_V5_HOST_VERSION_NAME = "";
    public static final long SHI_V5_HOST_VERSION_CODE = -1L;
    public static final String SHI_V5_HOST_CERT_SHA256 = "";
    public static final String SHI_V5_HOST_CERT_SHA256_ALT = "";
    public static final String SHI_V5_V236_HOST_VERSION_NAME = "";
    public static final long SHI_V5_V236_HOST_VERSION_CODE = -1L;
    public static final String PROTECTED_PAYLOAD_KEY_A = "";
    public static final String PROTECTED_PAYLOAD_KEY_B = "";
    public static final String PROTECTED_PAYLOAD_IV = "";
    public static final String PROTECTED_PAYLOAD_SHA256 = "";
    public static final String PROTECTED_PAYLOAD_NAME = "";
    public static final String LEGACY_PROTECTED_PAYLOAD_NAME = "";
    public static final String SHI_CORE_SHA256 = "";
    private BuildInfo() {}
}
EOF

# This is deliberately an inert source-compatibility shell, not a cloud client.  The shared UI
# source references the type behind Closed-only runtime gates, while the Open artifact must not
# ship the Closed URL, request protocol, device identifiers, authorization flow or payload key
# handling.  Keeping the shell here lets javac compile the shared UI without importing any Closed
# implementation into the Open staged source tree.
cat > "$OUT/src/com/dsmod/probe/CloudPromptClient.java" <<'EOF'
package com.dsmod.probe;

import android.content.Context;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

final class CloudPromptClient {
    interface Callback<T> { void done(T value, Throwable error); }
    static final class Prompt {
        final String id = "", title = "", note = "";
        final int size = 0, likes = 0, comments = 0, views = 0;
        final boolean pinned = false;
    }
    static final class Account {
        final String username = "", role = "member";
        final int points = 0, streak = 0, experience = 0, level = 0;
    }
    static final class ForumPost {
        final String id = "", title = "", content = "", username = "";
        final long createdAt = 0;
    }
    static final class Comment {
        final String id = "", username = "", content = "";
        final int likes = 0;
    }
    private CloudPromptClient() {}
    static boolean supported() { return false; }
    static boolean hasConsent(Context context) { return false; }
    static boolean hasLocalApiGrant(Context context) { return false; }
    // Open builds have no activation gate.  This method only exists so the shared source tree
    // remains source-compatible; no card, device identifier, or network request is performed.
    static boolean hasValidLicense(Context context) { return true; }
    static boolean isLicenseValidSilent(Context context) { return true; }
    static String getDeviceCode(Context context) { return ""; }
    static String getSavedCard(Context context) { return ""; }
    static byte[] localApiPayloadKey(Context context) { return null; }
    static String localApiPayloadSha256(Context context) { return ""; }
    static void saveConsent(Context context) { }
    static String privacySummary() { return ""; }
    static void activate(Context context, Callback<Boolean> callback) {
        callback.done(Boolean.FALSE, new UnsupportedOperationException("Closed edition only"));
    }
    static void activate(Context context, String card, Callback<Boolean> callback) {
        callback.done(Boolean.FALSE, new UnsupportedOperationException("Closed edition only"));
    }
    static void list(Context context, Callback<List<Prompt>> callback) {
        callback.done(Collections.emptyList(), null);
    }
    static void upload(Context context, String title, String note, byte[] bytes, Callback<String> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void download(Context context, Prompt prompt, Callback<String> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void register(Context context, String username, String password, Callback<Account> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void login(Context context, String username, String password, Callback<Account> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void checkin(Context context, Callback<Account> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void forumList(Context context, Callback<List<ForumPost>> callback) {
        callback.done(Collections.emptyList(), null);
    }
    static void forumPost(Context context, String title, String content, Callback<Boolean> callback) {
        callback.done(Boolean.FALSE, new UnsupportedOperationException("Closed edition only"));
    }
    static void promptAction(Context context, String action, String promptId, String content, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void comments(Context context, String promptId, Callback<List<Comment>> callback) {
        callback.done(Collections.emptyList(), null);
    }
    static void commentLike(Context context, String id, Callback<Boolean> callback) {
        callback.done(Boolean.FALSE, new UnsupportedOperationException("Closed edition only"));
    }
    static void communityHome(Context context, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void notifications(Context context, boolean markRead, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void adminAccounts(Context context, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void adminModerate(Context context, String accountId, boolean canComment, boolean canLike,
            boolean canPost, String role, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
    static void adminAnnouncement(Context context, String title, String content, Callback<JSONObject> callback) {
        callback.done(null, new UnsupportedOperationException("Closed edition only"));
    }
}
EOF


cat > "$OUT/src/com/dsmod/probe/CommunityUi.java" <<'EOF'
package com.dsmod.probe;

import android.app.Activity;

final class CommunityUi {
    private CommunityUi() {}
    static void open(Activity act) {}
}
EOF



cat > "$OUT/src/com/dsmod/probe/LoaderSecurityGuard.java" <<'EOF'
package com.dsmod.probe;

import android.content.Context;

/** Open-edition compatibility shell.  No loader, root, Frida, signature, or host termination
 * checks are present in the public artifact. */
public final class LoaderSecurityGuard {
    public static final String PROPRIETARY_LOADER_FACTORY = "";
    public static final String PROPRIETARY_LOADER_PROVIDER_SUFFIX = "";
    public static final String HANDSHAKE_METHOD = "";
    public static final String SCOPE_METHOD = "";
    private LoaderSecurityGuard() {}
    public static boolean checkAndEnforceGenericLoaderRejection(ClassLoader cl, Context context) { return true; }
    public static boolean checkAndEnforceAntiEmbedding(ClassLoader cl, Context context, String hostApk) { return true; }
    public static boolean isModuleEmbeddedInHostApk(ClassLoader cl, String hostApk) { return false; }
    public static boolean isGenericUnauthorizedLoader(ClassLoader cl, Context context) { return false; }
    public static boolean verifyProprietaryLoaderOrKill(Context context) { return true; }
    public static void schedulePostAttachBindingVerification(Context context) { }
    public static String computeLoaderDexSha256(Context context) { return ""; }
    public static String computeHmacSha256(String data) { return ""; }
    public static void killHost() { }
}
EOF

cat > "$OUT/src/com/dsmod/probe/z16.java" <<'EOF'
package com.dsmod.probe;

import android.content.Context;

/** Open-edition compatibility shell; runtime instrumentation/root checks are Closed-only. */
public final class z16 {
    private z16() {}
    static boolean enabled() { return false; }
    static void initialize(Context context) { }
    static boolean allowSensitiveFeature(Context context) { return true; }
    static String status() { return "open"; }
}
EOF

cat > "$OUT/src/com/dsmod/probe/RuntimeProofPartA.java" <<'EOF'
package com.dsmod.probe;
final class RuntimeProofPartA {
    static byte[] material() { return null; }
    private RuntimeProofPartA() {}
}
EOF

cat > "$OUT/src/com/dsmod/probe/RuntimeProofPartB.java" <<'EOF'
package com.dsmod.probe;
final class RuntimeProofPartB {
    static byte[] material() { return null; }
    private RuntimeProofPartB() {}
}
EOF
