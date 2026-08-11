package com.dsmod.probe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;

import java.util.List;

/** Executes a short QQ Music MediaSession request without opening a player Activity. */
public final class QqMusicPlaybackService extends Service {
    static final String ACTION_PLAY = "com.dsmod.probe.action.QQ_MUSIC_PLAY";
    static final String EXTRA_TOKEN = "qq_music_control_token";
    static final String EXTRA_REQUEST_ID = "request_id";
    static final String EXTRA_QUERY = "query";
    static final String CONTROL_TOKEN = "deekseep-qq-music-v1";

    private static final String QQ_PACKAGE = "com.tencent.qqmusic";
    private static final String CHANNEL = "deekseep_agent_music";
    private static final int NOTIFICATION_ID = 0xD5A2;
    private static volatile String lastRequestId = "";
    private static volatile boolean lastDone;
    private static volatile boolean lastSuccess;
    private static volatile String lastDetail = "";

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        startForeground(NOTIFICATION_ID, builder
                .setSmallIcon(NotificationIcons.smallIcon(this))
                .setContentTitle("Deekseep Agent")
                .setContentText("Starting background playback")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .build());
    }

    @Override public int onStartCommand(final Intent intent, int flags, final int startId) {
        if (intent == null || !ACTION_PLAY.equals(intent.getAction())
                || !CONTROL_TOKEN.equals(intent.getStringExtra(EXTRA_TOKEN))) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        final String requestId = intent.getStringExtra(EXTRA_REQUEST_ID);
        final String query = intent.getStringExtra(EXTRA_QUERY);
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{4,80}")
                || query == null || query.trim().length() == 0
                || query.length() > 200) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        lastRequestId = requestId;
        lastDone = false;
        lastSuccess = false;
        lastDetail = "starting";
        new Thread(new Runnable() {
            @Override public void run() {
                runPlayback(requestId, query.trim());
                stopSelf(startId);
            }
        }, "Deekseep-QQMusic").start();
        return START_NOT_STICKY;
    }

    private void runPlayback(String requestId, String query) {
        try {
            MediaSessionManager manager = (MediaSessionManager)
                    getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (manager == null) {
                finish(requestId, false, "media session manager unavailable");
                return;
            }
            ComponentName access = new ComponentName(
                    this, QqMusicSessionAccessService.class);
            MediaController qq = null;
            for (int attempt = 0; attempt < 12 && qq == null; attempt++) {
                List<MediaController> sessions = manager.getActiveSessions(access);
                if (sessions != null) {
                    for (MediaController controller : sessions) {
                        if (controller != null
                                && QQ_PACKAGE.equals(controller.getPackageName())) {
                            qq = controller;
                            break;
                        }
                    }
                }
                if (qq == null) Thread.sleep(150L);
            }
            if (qq == null) {
                finish(requestId, false, "QQ Music media session unavailable");
                return;
            }
            Bundle extras = new Bundle();
            extras.putString("query", query);
            extras.putString("android.intent.extra.TEXT", query);
            extras.putString("from", "deekseep_agent");
            qq.getTransportControls().playFromSearch(query, extras);
            boolean accepted = false;
            for (int attempt = 0; attempt < 12; attempt++) {
                Thread.sleep(150L);
                PlaybackState state = qq.getPlaybackState();
                if (state != null && (state.getState() == PlaybackState.STATE_PLAYING
                        || state.getState() == PlaybackState.STATE_BUFFERING
                        || state.getState() == PlaybackState.STATE_CONNECTING)) {
                    accepted = true;
                    break;
                }
            }
            finish(requestId, accepted, accepted
                    ? "QQ Music accepted background playback"
                    : "QQ Music did not enter playback state");
        } catch (Throwable error) {
            finish(requestId, false, error.getClass().getSimpleName() + ": "
                    + String.valueOf(error.getMessage()));
        }
    }

    private static void finish(String requestId, boolean success, String detail) {
        if (!requestId.equals(lastRequestId)) return;
        lastSuccess = success;
        lastDetail = detail == null ? "" : detail;
        lastDone = true;
    }

    static Bundle status(String requestId) {
        Bundle result = new Bundle();
        boolean matching = requestId != null && requestId.equals(lastRequestId);
        result.putBoolean("matching", matching);
        result.putBoolean("done", matching && lastDone);
        result.putBoolean("success", matching && lastDone && lastSuccess);
        result.putString("detail", matching ? lastDetail : "unknown request");
        return result;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL, "Agent music", NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    @Override public IBinder onBind(Intent intent) {
        return null;
    }
}
