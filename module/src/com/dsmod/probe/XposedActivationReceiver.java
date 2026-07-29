package com.dsmod.probe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * Explicit-component fallback for hosts that cannot resolve the module provider because their
 * original manifest has no package-visibility query for an add-on installed later.
 */
public final class XposedActivationReceiver extends BroadcastReceiver {
    static final String ACTION = "com.dsmod.probe.action.REPORT_DEEPSEEK_ACTIVE";
    static final String EXTRA_TOKEN = "deekseep_activation_token";
    static final String REPORT_TOKEN =
            "deekseep-target-heartbeat-1f73-7c94d286b51a";
    private static final String TAG = "DeekseepActivation";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) return;
        int sendingUid;
        try {
            // Android 14 added an authenticated sender UID for manifest receivers. Older systems
            // keep using the provider path; never accept an unverifiable fallback heartbeat.
            if (android.os.Build.VERSION.SDK_INT < 34) return;
            sendingUid = getSentFromUid();
        } catch (Throwable error) {
            Log.w(TAG, "cannot identify activation broadcast sender", error);
            return;
        }
        Bundle extras = intent.getExtras();
        if (sendingUid >= 0) {
            XposedActivationProvider.recordTargetHeartbeat(
                    context, sendingUid, extras, "explicit-broadcast");
            return;
        }
        // Some Android 14/15 framework builds return UID_UNKNOWN for explicit broadcasts sent
        // by an injected Context. The component is explicit and carries a module-private
        // capability token, so preserve target verification without leaving the launcher stuck
        // on "Waiting for verification".
        String token = intent.getStringExtra(EXTRA_TOKEN);
        String reportedPackage = extras == null ? null : extras.getString("package");
        if (REPORT_TOKEN.equals(token) && "com.deepseek.chat".equals(reportedPackage)) {
            XposedActivationProvider.recordTrustedTargetHeartbeat(
                    context, extras, "explicit-token");
        } else {
            Log.w(TAG, "rejected unverifiable activation fallback");
        }
    }
}
