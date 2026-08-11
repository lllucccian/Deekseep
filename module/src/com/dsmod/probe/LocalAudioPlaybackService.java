package com.dsmod.probe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import java.io.File;

/** Small foreground audio player used by the Agent music tool for files already on-device. */
public final class LocalAudioPlaybackService extends Service {
    static final String ACTION_CONTROL = "com.dsmod.probe.action.LOCAL_AUDIO_CONTROL";
    static final String EXTRA_TOKEN = "local_audio_control_token";
    static final String EXTRA_REQUEST_ID = "request_id";
    static final String EXTRA_ACTION = "action";
    static final String EXTRA_PATH = "path";
    static final String CONTROL_TOKEN = "deekseep-local-audio-v1";

    private static final String CHANNEL = "deekseep_agent_local_audio";
    private static final int NOTIFICATION_ID = 0xD5A3;
    private static final Object LOCK = new Object();
    private static MediaPlayer player;
    private static String currentPath = "";
    private static volatile String lastRequestId = "";
    private static volatile boolean lastDone;
    private static volatile boolean lastSuccess;
    private static volatile String lastDetail = "";

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(NOTIFICATION_ID, buildNotification("Local audio player"));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_CONTROL.equals(intent.getAction())
                || !CONTROL_TOKEN.equals(intent.getStringExtra(EXTRA_TOKEN))) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        final String requestId = safe(intent.getStringExtra(EXTRA_REQUEST_ID));
        final String action = safe(intent.getStringExtra(EXTRA_ACTION));
        final String path = safe(intent.getStringExtra(EXTRA_PATH));
        if (!requestId.matches("[A-Za-z0-9_-]{4,80}")) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        lastRequestId = requestId;
        lastDone = false;
        lastSuccess = false;
        lastDetail = "starting";
        try {
            control(requestId, action, path);
        } catch (Throwable error) {
            finish(requestId, false, error.getClass().getSimpleName() + ": "
                    + String.valueOf(error.getMessage()));
        }
        return START_NOT_STICKY;
    }

    private void control(final String requestId, String action, String path) throws Exception {
        synchronized (LOCK) {
            if ("play".equals(action) && path.length() > 0) {
                releaseLocked();
                File file = new File(path);
                if (!file.isFile()) {
                    finish(requestId, false, "Audio file does not exist");
                    return;
                }
                MediaPlayer next = new MediaPlayer();
                next.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                next.setDataSource(this, Uri.fromFile(file));
                next.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override public void onPrepared(MediaPlayer ready) {
                        try {
                            ready.start();
                            finish(requestId, true, "Local audio playback started");
                        } catch (Throwable error) {
                            finish(requestId, false, error.getClass().getSimpleName() + ": "
                                    + String.valueOf(error.getMessage()));
                        }
                    }
                });
                next.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override public boolean onError(MediaPlayer failed, int what, int extra) {
                        finish(requestId, false,
                                "Media decoder error what=" + what + " extra=" + extra);
                        return true;
                    }
                });
                next.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override public void onCompletion(MediaPlayer completed) {
                        updateNotification("Playback completed");
                    }
                });
                player = next;
                currentPath = path;
                updateNotification(file.getName());
                next.prepareAsync();
                return;
            }
            if (player == null) {
                finish(requestId, false, "No local audio is loaded");
                return;
            }
            if ("play".equals(action)) {
                player.start();
                finish(requestId, true, "Local audio playback resumed");
            } else if ("pause".equals(action)) {
                if (player.isPlaying()) player.pause();
                finish(requestId, true, "Local audio playback paused");
            } else if ("toggle".equals(action)) {
                if (player.isPlaying()) player.pause(); else player.start();
                finish(requestId, true, player.isPlaying()
                        ? "Local audio playback resumed" : "Local audio playback paused");
            } else if ("stop".equals(action)) {
                releaseLocked();
                finish(requestId, true, "Local audio playback stopped");
                stopForeground(true);
                stopSelf();
            } else {
                finish(requestId, false, "Unsupported local audio action");
            }
        }
    }

    private void updateNotification(String text) {
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        return builder.setSmallIcon(NotificationIcons.smallIcon(this))
                .setContentTitle("Deekseep Agent")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Agent local audio", NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    private static void finish(String requestId, boolean success, String detail) {
        if (!requestId.equals(lastRequestId)) return;
        lastSuccess = success;
        lastDetail = safe(detail);
        lastDone = true;
    }

    static Bundle status(String requestId) {
        Bundle result = new Bundle();
        boolean matching = requestId != null && requestId.equals(lastRequestId);
        result.putBoolean("matching", matching);
        result.putBoolean("done", matching && lastDone);
        result.putBoolean("success", matching && lastDone && lastSuccess);
        result.putString("detail", matching ? lastDetail : "unknown request");
        result.putString("path", currentPath);
        return result;
    }

    private static void releaseLocked() {
        MediaPlayer old = player;
        player = null;
        currentPath = "";
        if (old == null) return;
        try { old.stop(); } catch (Throwable ignored) {}
        try { old.reset(); } catch (Throwable ignored) {}
        try { old.release(); } catch (Throwable ignored) {}
    }

    @Override public void onDestroy() {
        synchronized (LOCK) { releaseLocked(); }
        super.onDestroy();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
