package com.dsmod.probe;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

/** Delivers a completed durable delay back to the injected DeepSeek bridge. */
public final class AgentDelayReceiver extends BroadcastReceiver {
    static final String ACTION_FIRE = "com.dsmod.probe.action.AGENT_DELAY_FIRE";
    static final String ACTION_COMPLETE = "com.dsmod.probe.action.AGENT_DELAY_COMPLETE";
    static final String EXTRA_TOKEN = "delay_token";
    static final String EXTRA_ID = "call_id";
    static final String EXTRA_SCOPE = "scope";
    static final String EXTRA_DURATION_MS = "duration_ms";
    static final String EXTRA_STARTED_AT = "started_at";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION_FIRE.equals(intent.getAction())
                || !AgentDelayActivity.TOKEN.equals(
                        intent.getStringExtra(EXTRA_TOKEN))) return;
        Intent complete = new Intent(ACTION_COMPLETE)
                .setComponent(new ComponentName(
                        "com.deepseek.chat",
                        "com.deepseek.chat.system.ShareResultReceiver"))
                .putExtra(EXTRA_TOKEN, AgentDelayActivity.TOKEN)
                .putExtra(EXTRA_ID, intent.getStringExtra(EXTRA_ID))
                .putExtra(EXTRA_SCOPE, intent.getStringExtra(EXTRA_SCOPE))
                .putExtra(EXTRA_DURATION_MS,
                        intent.getLongExtra(EXTRA_DURATION_MS, 0L))
                .putExtra(EXTRA_STARTED_AT,
                        intent.getLongExtra(EXTRA_STARTED_AT, 0L))
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND
                        | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        context.sendBroadcast(complete);
    }
}
