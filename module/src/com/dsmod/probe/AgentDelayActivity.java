package com.dsmod.probe;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;

/** Invisible bridge that schedules a durable Agent delay in the module process. */
public final class AgentDelayActivity extends Activity {
    static final String SCHEME = "deekseep-module";
    static final String HOST = "agent-delay";
    static final String TOKEN = "deekseep-agent-delay-v1";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        schedule(getIntent());
        finishImmediately();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        schedule(intent);
        finishImmediately();
    }

    private void schedule(Intent source) {
        Uri data = source == null ? null : source.getData();
        if (data == null || !SCHEME.equals(data.getScheme())
                || !HOST.equals(data.getHost())
                || !TOKEN.equals(data.getQueryParameter("token"))) return;
        String id = clean(data.getQueryParameter("id"), 120);
        String scope = clean(data.getQueryParameter("scope"), 2048);
        long duration;
        try { duration = Long.parseLong(data.getQueryParameter("duration_ms")); }
        catch (Throwable ignored) { return; }
        if (id.length() == 0 || scope.length() == 0
                || duration < 1L || duration > 604_800_000L) return;

        Intent alarm = new Intent(this, AgentDelayReceiver.class)
                .setAction(AgentDelayReceiver.ACTION_FIRE)
                .setData(Uri.parse("deekseep-delay://" + Integer.toHexString(
                        (scope + "|" + id).hashCode())))
                .putExtra(AgentDelayReceiver.EXTRA_TOKEN, TOKEN)
                .putExtra(AgentDelayReceiver.EXTRA_ID, id)
                .putExtra(AgentDelayReceiver.EXTRA_SCOPE, scope)
                .putExtra(AgentDelayReceiver.EXTRA_DURATION_MS, duration)
                .putExtra(AgentDelayReceiver.EXTRA_STARTED_AT,
                        System.currentTimeMillis());
        PendingIntent pending = PendingIntent.getBroadcast(this,
                (scope + "|" + id).hashCode(), alarm,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager manager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        long trigger = SystemClock.elapsedRealtime() + duration;
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    trigger, pending);
        } else if (Build.VERSION.SDK_INT >= 23) {
            manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    trigger, pending);
        } else {
            manager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending);
        }
    }

    private static String clean(String value, int max) {
        if (value == null) return "";
        String result = value.trim();
        return result.length() > max ? "" : result;
    }

    private void finishImmediately() {
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
        finish();
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
    }
}
