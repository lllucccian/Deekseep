package com.dsmod.probe;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional companion heartbeat for AI-initiated messages.
 *
 * <p>The alarm lives in the module app so Android can wake an idle DeepSeek process. Generation
 * still runs inside DeepSeek through the injected native transport; the finished message is
 * returned here for a system notification in both foreground and background.</p>
 */
public final class ProactiveHeartbeatReceiver extends BroadcastReceiver {
    static final String ACTION_CONFIG =
            "com.dsmod.probe.action.CONFIGURE_PROACTIVE_HEARTBEAT";
    static final String ACTION_ALARM =
            "com.dsmod.probe.action.PROACTIVE_HEARTBEAT_ALARM";
    static final String ACTION_REQUEST =
            "com.dsmod.probe.action.PROACTIVE_HEARTBEAT_REQUEST";
    static final String ACTION_RESPONSE =
            "com.dsmod.probe.action.PROACTIVE_HEARTBEAT_RESPONSE";
    static final String ACTION_REPLY_READY =
            "com.dsmod.probe.action.DEEPSEEK_REPLY_READY";
    static final String ACTION_TASK_CONFIG =
            "com.dsmod.probe.action.CONFIGURE_PROACTIVE_TASK";
    static final String ACTION_TASK_CANCEL =
            "com.dsmod.probe.action.CANCEL_PROACTIVE_TASK";
    static final String ACTION_TASK_ALARM =
            "com.dsmod.probe.action.PROACTIVE_TASK_ALARM";
    static final String EXTRA_TOKEN = "deekseep_proactive_token";
    static final String EXTRA_ENABLED = "enabled";
    static final String EXTRA_REQUEST_ID = "request_id";
    static final String EXTRA_MESSAGE = "message";
    static final String EXTRA_FOREGROUND = "deepseek_foreground";
    static final String EXTRA_INTERVAL_MINUTES = "interval_minutes";
    static final String EXTRA_TASK_ID = "task_id";
    static final String EXTRA_TASK_TEXT = "task_text";
    static final String EXTRA_TRIGGER_AT = "trigger_at";
    static final String EXTRA_TASK_REMINDER = "task_reminder";
    static final String EXTRA_TASK_KIND = "task_kind";
    static final String EXTRA_CONVERSATION_ID = "conversation_id";
    static final String EXTRA_RESPONSE_ID = "response_id";
    static final String EXTRA_CANCEL_MODE = "cancel_mode";
    static final String EXTRA_CANCEL_TARGET_ID = "cancel_target_id";
    static final String TASK_KIND_REMINDER = "reminder";
    static final String TASK_KIND_HEARTBEAT = "heartbeat";
    static final String TOKEN =
            "deekseep-proactive-heartbeat-1f73-19c8bda62374";

    private static final String TARGET_PACKAGE = "com.deepseek.chat";
    private static final String TARGET_RECEIVER =
            "com.deepseek.chat.system.ShareResultReceiver";
    private static final String PREFS = "deekseep_proactive_heartbeat";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_INTERVAL_MINUTES = "interval_minutes";
    private static final String KEY_CONVERSATION_ID = "conversation_id";
    private static final String TASK_KEY_PREFIX = "task_";
    private static final String CHANNEL_ID = "deekseep_proactive_messages";
    private static final String REPLY_CHANNEL_ID = "deekseep_reply_ready";
    private static final int ALARM_REQUEST_CODE = 0xD5B1;
    private static final int NOTIFICATION_ID = 0xD5B2;
    private static final int DEFAULT_INTERVAL_MINUTES = 180;
    private static final int MIN_INTERVAL_MINUTES = 15;
    private static final int MAX_INTERVAL_MINUTES = 7 * 24 * 60;
    private static final long GENERATION_WAKE_TIMEOUT_MS = 3L * 60L * 1000L;
    private static final String TAG = "DeekseepHeartbeat";
    private static final Map<String, PowerManager.WakeLock> GENERATION_WAKE_LOCKS =
            new LinkedHashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            if (isEnabled(context)) schedule(context, intervalMs(context));
            restoreTaskAlarms(context);
            return;
        }
        if (!TOKEN.equals(intent.getStringExtra(EXTRA_TOKEN))) {
            Log.w(TAG, "ignored heartbeat command with invalid token");
            return;
        }
        if (ACTION_CONFIG.equals(action)) {
            boolean enabled = intent.getBooleanExtra(EXTRA_ENABLED, false);
            int interval = clampInterval(intent.getIntExtra(
                    EXTRA_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES));
            String conversationId = normalizeConversationId(
                    intent.getStringExtra(EXTRA_CONVERSATION_ID));
            prefs(context).edit().putBoolean(KEY_ENABLED, enabled)
                    .putInt(KEY_INTERVAL_MINUTES, interval)
                    .putString(KEY_CONVERSATION_ID, conversationId).apply();
            if (enabled) schedule(context, intervalMs(context));
            else cancel(context);
            Log.i(TAG, "proactive heartbeat configured enabled=" + enabled
                    + " interval_minutes=" + interval);
            return;
        }
        if (ACTION_TASK_CONFIG.equals(action)) {
            String taskId = intent.getStringExtra(EXTRA_TASK_ID);
            String taskText = intent.getStringExtra(EXTRA_TASK_TEXT);
            String taskKind = normalizeTaskKind(
                    intent.getStringExtra(EXTRA_TASK_KIND));
            String conversationId = normalizeConversationId(
                    intent.getStringExtra(EXTRA_CONVERSATION_ID));
            long triggerAt = intent.getLongExtra(EXTRA_TRIGGER_AT, 0L);
            if (taskId == null || taskId.length() == 0 || taskText == null
                    || taskText.trim().length() == 0
                    || triggerAt <= System.currentTimeMillis()) {
                Log.w(TAG, "ignored invalid proactive reminder task");
                return;
            }
            if (TASK_KIND_HEARTBEAT.equals(taskKind)
                    && conversationId.length() == 0) {
                Log.w(TAG, "ignored unbound proactive heartbeat task");
                return;
            }
            storeTask(context, taskId, triggerAt, taskKind,
                    conversationId, taskText.trim());
            scheduleTask(context, taskId, triggerAt, taskKind,
                    conversationId, taskText.trim());
            Log.i(TAG, "proactive reminder scheduled id=" + taskId
                    + " trigger=" + triggerAt);
            return;
        }
        if (ACTION_TASK_CANCEL.equals(action)) {
            String mode = intent.getStringExtra(EXTRA_CANCEL_MODE);
            String targetId = intent.getStringExtra(EXTRA_CANCEL_TARGET_ID);
            String conversationId = normalizeConversationId(
                    intent.getStringExtra(EXTRA_CONVERSATION_ID));
            int cancelled = cancelHeartbeatTasks(
                    context, mode, targetId, conversationId);
            Log.i(TAG, "proactive heartbeat tasks cancelled mode=" + mode
                    + " count=" + cancelled + " scope=" + conversationId);
            return;
        }
        if (ACTION_ALARM.equals(action)) {
            if (!isEnabled(context)) return;
            String conversationId = prefs(context).getString(
                    KEY_CONVERSATION_ID, "");
            if (normalizeConversationId(conversationId).length() > 0) {
                dispatchGeneration(context, null, null, TASK_KIND_HEARTBEAT,
                        conversationId, false);
            } else {
                Log.i(TAG, "periodic heartbeat skipped until a conversation is bound");
            }
            schedule(context, intervalMs(context));
            return;
        }
        if (ACTION_TASK_ALARM.equals(action)) {
            String taskId = intent.getStringExtra(EXTRA_TASK_ID);
            String taskText = intent.getStringExtra(EXTRA_TASK_TEXT);
            String taskKind = normalizeTaskKind(
                    intent.getStringExtra(EXTRA_TASK_KIND));
            String conversationId = normalizeConversationId(
                    intent.getStringExtra(EXTRA_CONVERSATION_ID));
            if (taskId == null || taskText == null) return;
            removeStoredTask(context, taskId);
            dispatchGeneration(context, taskId, taskText, taskKind,
                    conversationId, true);
            return;
        }
        if (ACTION_RESPONSE.equals(action)) {
            releaseGenerationWakeLock(intent.getStringExtra(EXTRA_REQUEST_ID));
            boolean taskReminder = intent.getBooleanExtra(EXTRA_TASK_REMINDER, false);
            String taskKind = normalizeTaskKind(
                    intent.getStringExtra(EXTRA_TASK_KIND));
            String conversationId = normalizeConversationId(
                    intent.getStringExtra(EXTRA_CONVERSATION_ID));
            if (!isEnabled(context) && !taskReminder) return;
            String message = intent.getStringExtra(EXTRA_MESSAGE);
            if (message != null && message.trim().length() > 0) {
                postMessageNotification(context, message.trim(), taskReminder,
                        taskKind, conversationId);
            }
            return;
        }
        if (ACTION_REPLY_READY.equals(action)) {
            postReplyReadyNotification(context,
                    normalizeConversationId(intent.getStringExtra(EXTRA_CONVERSATION_ID)),
                    intent.getStringExtra(EXTRA_RESPONSE_ID));
        }
    }

    private static void dispatchGeneration(Context context, String taskId,
                                           String taskText, String taskKind,
                                           String conversationId,
                                           boolean taskReminder) {
        String requestId = taskReminder && taskId != null
                ? "reminder-" + taskId
                : "heartbeat-" + Long.toHexString(System.currentTimeMillis());
        try {
            Intent request = new Intent(ACTION_REQUEST)
                    .setComponent(new ComponentName(TARGET_PACKAGE, TARGET_RECEIVER))
                    .putExtra(EXTRA_TOKEN, TOKEN)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            request.putExtra(EXTRA_REQUEST_ID, requestId);
            request.putExtra(EXTRA_TASK_REMINDER, taskReminder);
            request.putExtra(EXTRA_TASK_KIND, normalizeTaskKind(taskKind));
            request.putExtra(EXTRA_CONVERSATION_ID,
                    normalizeConversationId(conversationId));
            if (taskId != null) request.putExtra(EXTRA_TASK_ID, taskId);
            if (taskText != null) request.putExtra(EXTRA_TASK_TEXT, taskText);
            acquireGenerationWakeLock(context, requestId);
            context.sendBroadcast(request);
            Log.i(TAG, "proactive heartbeat dispatched");
        } catch (Throwable t) {
            releaseGenerationWakeLock(requestId);
            Log.w(TAG, "could not dispatch proactive heartbeat", t);
        }
    }

    private static void acquireGenerationWakeLock(Context context, String requestId) {
        if (context == null || requestId == null) return;
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (power == null) return;
        synchronized (GENERATION_WAKE_LOCKS) {
            Iterator<Map.Entry<String, PowerManager.WakeLock>> iterator =
                    GENERATION_WAKE_LOCKS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, PowerManager.WakeLock> entry = iterator.next();
                if (!entry.getValue().isHeld()) iterator.remove();
            }
            PowerManager.WakeLock previous = GENERATION_WAKE_LOCKS.remove(requestId);
            if (previous != null && previous.isHeld()) previous.release();
            while (GENERATION_WAKE_LOCKS.size() >= 16) {
                Iterator<Map.Entry<String, PowerManager.WakeLock>> oldest =
                        GENERATION_WAKE_LOCKS.entrySet().iterator();
                if (!oldest.hasNext()) break;
                PowerManager.WakeLock stale = oldest.next().getValue();
                oldest.remove();
                if (stale.isHeld()) stale.release();
            }
            PowerManager.WakeLock wakeLock = power.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Deekseep:proactive:" + Integer.toHexString(requestId.hashCode()));
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(GENERATION_WAKE_TIMEOUT_MS);
            GENERATION_WAKE_LOCKS.put(requestId, wakeLock);
        }
    }

    private static void releaseGenerationWakeLock(String requestId) {
        if (requestId == null) return;
        synchronized (GENERATION_WAKE_LOCKS) {
            PowerManager.WakeLock wakeLock = GENERATION_WAKE_LOCKS.remove(requestId);
            if (wakeLock != null && wakeLock.isHeld()) {
                try {
                    wakeLock.release();
                } catch (Throwable ignored) {}
            }
        }
    }

    private static void schedule(Context context, long delayMs) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        long trigger = SystemClock.elapsedRealtime() + Math.max(30_000L, delayMs);
        PendingIntent pending = alarmPendingIntent(context);
        if (Build.VERSION.SDK_INT >= 23) {
            alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending);
        } else {
            alarms.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending);
        }
    }

    private static void scheduleTask(Context context, String taskId,
                                     long triggerAt, String taskKind,
                                     String conversationId, String taskText) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent alarm = new Intent(context, ProactiveHeartbeatReceiver.class)
                .setAction(ACTION_TASK_ALARM)
                .putExtra(EXTRA_TOKEN, TOKEN)
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TASK_TEXT, taskText)
                .putExtra(EXTRA_TASK_KIND, normalizeTaskKind(taskKind))
                .putExtra(EXTRA_CONVERSATION_ID,
                        normalizeConversationId(conversationId))
                .putExtra(EXTRA_TASK_REMINDER, true);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(context,
                taskId.hashCode(), alarm, flags);
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else if (Build.VERSION.SDK_INT >= 23) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else {
                alarms.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
        } catch (SecurityException denied) {
            if (Build.VERSION.SDK_INT >= 23) {
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            } else {
                alarms.set(AlarmManager.RTC_WAKEUP, triggerAt, pending);
            }
        }
    }

    private static void cancel(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(alarmPendingIntent(context));
    }

    private static int cancelHeartbeatTasks(Context context, String mode,
                                            String targetId, String conversationId) {
        String scope = normalizeConversationId(conversationId);
        if (scope.length() == 0) return 0;
        boolean one = "once".equals(mode);
        boolean all = "all_once".equals(mode) || "all".equals(mode);
        if (!one && !all) return 0;
        String wanted = targetId == null ? "" : targetId.trim();
        if (one) {
            if (wanted.startsWith("ai-")) wanted = wanted.substring(3);
            if (!wanted.matches("[A-Za-z0-9_.:-]{4,80}")) return 0;
            wanted = "ai-" + wanted;
        }
        int cancelled = 0;
        SharedPreferences state = prefs(context);
        SharedPreferences.Editor editor = state.edit();
        for (Map.Entry<String, ?> entry : state.getAll().entrySet()) {
            if (!entry.getKey().startsWith(TASK_KEY_PREFIX)
                    || !(entry.getValue() instanceof String)) continue;
            String taskId = entry.getKey().substring(TASK_KEY_PREFIX.length());
            if (one && !wanted.equals(taskId)) continue;
            StoredTask task = decodeStoredTask((String) entry.getValue());
            if (task == null || !TASK_KIND_HEARTBEAT.equals(task.taskKind)
                    || !scope.equals(task.conversationId)) continue;
            cancelTaskAlarm(context, taskId);
            editor.remove(entry.getKey());
            cancelled++;
            if (one) break;
        }
        if (cancelled > 0) editor.apply();
        return cancelled;
    }

    private static void cancelTaskAlarm(Context context, String taskId) {
        if (context == null || taskId == null) return;
        Intent alarm = new Intent(context, ProactiveHeartbeatReceiver.class)
                .setAction(ACTION_TASK_ALARM);
        int flags = PendingIntent.FLAG_NO_CREATE;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getBroadcast(
                context, taskId.hashCode(), alarm, flags);
        if (pending == null) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pending);
        pending.cancel();
    }

    private static PendingIntent alarmPendingIntent(Context context) {
        Intent alarm = new Intent(context, ProactiveHeartbeatReceiver.class)
                .setAction(ACTION_ALARM)
                .putExtra(EXTRA_TOKEN, TOKEN);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(context, ALARM_REQUEST_CODE, alarm, flags);
    }

    private static void postMessageNotification(Context context, String message,
                                                boolean taskReminder, String taskKind,
                                                String conversationId) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    UiLanguage.text(context, "DeepSeek 主动消息", "DeepSeek proactive messages"),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(UiLanguage.text(context,
                    "接收 DeepSeek 心跳生成的主动问候",
                    "Receive AI-initiated messages generated by the DeepSeek heartbeat"));
            manager.createNotificationChannel(channel);
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        PendingIntent content = null;
        if (launch != null) {
            String scope = normalizeConversationId(conversationId);
            if (scope.length() > 0) {
                launch.putExtra(EXTRA_CONVERSATION_ID, scope);
                launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            content = PendingIntent.getActivity(context,
                    scope.length() == 0 ? 0 : scope.hashCode(), launch, flags);
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(NotificationIcons.smallIcon(context))
                .setContentTitle(taskReminder
                        && TASK_KIND_REMINDER.equals(normalizeTaskKind(taskKind))
                        ? UiLanguage.text(context, "DeepSeek 提醒你", "DeepSeek reminder")
                        : "DeepSeek")
                .setContentText(message)
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setColor(0xFF426EFE)
                .setShowWhen(true);
        if (content != null) builder.setContentIntent(content);
        try {
            manager.notify(NOTIFICATION_ID, builder.build());
        } catch (Throwable t) {
            Log.w(TAG, "could not post proactive message notification", t);
        }
    }

    private static void postReplyReadyNotification(Context context,
                                                   String conversationId,
                                                   String responseId) {
        NotificationManager manager = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(REPLY_CHANNEL_ID,
                    UiLanguage.text(context, "DeepSeek 回复完成", "DeepSeek replies"),
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription(UiLanguage.text(context,
                    "切到后台后，在模型完成回答时通知你",
                    "Notify when a model response finishes while DeepSeek is in the background"));
            manager.createNotificationChannel(channel);
        }
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(TARGET_PACKAGE);
        PendingIntent content = null;
        if (launch != null) {
            if (conversationId.length() > 0) {
                launch.putExtra(EXTRA_CONVERSATION_ID, conversationId);
                launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            }
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            int requestCode = conversationId.length() > 0
                    ? conversationId.hashCode()
                    : responseId == null ? 0 : responseId.hashCode();
            content = PendingIntent.getActivity(context, requestCode, launch, flags);
        }
        String title = UiLanguage.text(context,
                "DeepSeek 回复已就绪", "DeepSeek reply is ready");
        String detail = UiLanguage.text(context,
                "模型已完成回答，点按返回对话查看",
                "The model has finished. Tap to return to the conversation.");
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, REPLY_CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(NotificationIcons.smallIcon(context))
                .setContentTitle(title)
                .setContentText(detail)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setColor(0xFF426EFE)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setShowWhen(true);
        if (content != null) builder.setContentIntent(content);
        String tag = "reply-ready-" + (conversationId.length() > 0
                ? conversationId : String.valueOf(responseId));
        try {
            manager.notify(tag, NOTIFICATION_ID + 1, builder.build());
        } catch (Throwable t) {
            Log.w(TAG, "could not post reply-ready notification", t);
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    private static long intervalMs(Context context) {
        int minutes = clampInterval(prefs(context).getInt(
                KEY_INTERVAL_MINUTES, DEFAULT_INTERVAL_MINUTES));
        return minutes * 60_000L;
    }

    private static int clampInterval(int minutes) {
        return Math.max(MIN_INTERVAL_MINUTES, Math.min(MAX_INTERVAL_MINUTES, minutes));
    }

    private static void storeTask(Context context, String taskId,
                                  long triggerAt, String taskKind,
                                  String conversationId, String taskText) {
        String encoded = "v2\n" + triggerAt + "\n" + normalizeTaskKind(taskKind)
                + "\n" + normalizeConversationId(conversationId)
                + "\n" + taskText;
        prefs(context).edit().putString(TASK_KEY_PREFIX + taskId, encoded).apply();
    }

    private static void removeStoredTask(Context context, String taskId) {
        prefs(context).edit().remove(TASK_KEY_PREFIX + taskId).apply();
    }

    private static void restoreTaskAlarms(Context context) {
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            if (!entry.getKey().startsWith(TASK_KEY_PREFIX)
                    || !(entry.getValue() instanceof String)) continue;
            String taskId = entry.getKey().substring(TASK_KEY_PREFIX.length());
            String encoded = (String) entry.getValue();
            try {
                StoredTask task = decodeStoredTask(encoded);
                if (task == null) continue;
                if (task.triggerAt > now) {
                    scheduleTask(context, taskId, task.triggerAt, task.taskKind,
                            task.conversationId, task.taskText);
                }
                else {
                    removeStoredTask(context, taskId);
                    if (now - task.triggerAt <= 7L * 24L * 60L * 60_000L) {
                        dispatchGeneration(
                                context, taskId, task.taskText, task.taskKind,
                                task.conversationId, true);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private static StoredTask decodeStoredTask(String encoded) {
        if (encoded == null || encoded.length() == 0) return null;
        try {
            if (encoded.startsWith("v2\n")) {
                int triggerEnd = encoded.indexOf('\n', 3);
                int kindEnd = triggerEnd < 0 ? -1
                        : encoded.indexOf('\n', triggerEnd + 1);
                int scopeEnd = kindEnd < 0 ? -1
                        : encoded.indexOf('\n', kindEnd + 1);
                if (triggerEnd < 0 || kindEnd < 0 || scopeEnd < 0) return null;
                long triggerAt = Long.parseLong(encoded.substring(3, triggerEnd));
                String kind = normalizeTaskKind(
                        encoded.substring(triggerEnd + 1, kindEnd));
                String scope = normalizeConversationId(
                        encoded.substring(kindEnd + 1, scopeEnd));
                String text = encoded.substring(scopeEnd + 1);
                return text.trim().length() == 0 ? null
                        : new StoredTask(triggerAt, kind, scope, text);
            }
            int triggerEnd = encoded.indexOf('\n');
            if (triggerEnd <= 0) return null;
            long triggerAt = Long.parseLong(encoded.substring(0, triggerEnd));
            String remainder = encoded.substring(triggerEnd + 1);
            int kindEnd = remainder.indexOf('\n');
            String first = kindEnd < 0 ? "" : remainder.substring(0, kindEnd);
            boolean hasKind = TASK_KIND_REMINDER.equals(first)
                    || TASK_KIND_HEARTBEAT.equals(first);
            String kind = hasKind ? normalizeTaskKind(first) : TASK_KIND_REMINDER;
            String text = hasKind ? remainder.substring(kindEnd + 1) : remainder;
            return text.trim().length() == 0 ? null
                    : new StoredTask(triggerAt, kind, "", text);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class StoredTask {
        final long triggerAt;
        final String taskKind;
        final String conversationId;
        final String taskText;

        StoredTask(long triggerAt, String taskKind,
                   String conversationId, String taskText) {
            this.triggerAt = triggerAt;
            this.taskKind = taskKind;
            this.conversationId = conversationId;
            this.taskText = taskText;
        }
    }

    private static String normalizeTaskKind(String value) {
        return TASK_KIND_HEARTBEAT.equals(value)
                ? TASK_KIND_HEARTBEAT : TASK_KIND_REMINDER;
    }

    private static String normalizeConversationId(String value) {
        if (value == null) return "";
        String out = value.trim();
        return out.matches("[A-Za-z0-9_.:-]{4,160}") ? out : "";
    }
}
