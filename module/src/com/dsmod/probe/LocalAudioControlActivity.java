package com.dsmod.probe;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ResultReceiver;

/** Explicit bridge from the injected host process to the module-owned audio service. */
public final class LocalAudioControlActivity extends Activity {
    static final String EXTRA_RECEIVER = "local_audio_result_receiver";

    private final Handler main = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Intent source = getIntent();
        final String requestId = source == null ? ""
                : source.getStringExtra(LocalAudioPlaybackService.EXTRA_REQUEST_ID);
        final ResultReceiver receiver = source == null ? null
                : source.getParcelableExtra(EXTRA_RECEIVER);
        if (source == null
                || !LocalAudioPlaybackService.CONTROL_TOKEN.equals(
                source.getStringExtra(LocalAudioPlaybackService.EXTRA_TOKEN))
                || requestId == null
                || !requestId.matches("[A-Za-z0-9_-]{4,80}")) {
            deliver(receiver, false, "Invalid local audio request");
            finish();
            return;
        }
        try {
            Intent control = new Intent(this, LocalAudioPlaybackService.class);
            control.setAction(LocalAudioPlaybackService.ACTION_CONTROL);
            control.putExtra(LocalAudioPlaybackService.EXTRA_TOKEN,
                    LocalAudioPlaybackService.CONTROL_TOKEN);
            control.putExtra(LocalAudioPlaybackService.EXTRA_REQUEST_ID, requestId);
            control.putExtra(LocalAudioPlaybackService.EXTRA_ACTION,
                    source.getStringExtra(LocalAudioPlaybackService.EXTRA_ACTION));
            control.putExtra(LocalAudioPlaybackService.EXTRA_PATH,
                    source.getStringExtra(LocalAudioPlaybackService.EXTRA_PATH));
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(control);
            else startService(control);
        } catch (Throwable error) {
            deliver(receiver, false, error.getClass().getSimpleName() + ": "
                    + String.valueOf(error.getMessage()));
            finish();
            return;
        }
        main.post(new Runnable() {
            int attempts;
            @Override public void run() {
                Bundle status = LocalAudioPlaybackService.status(requestId);
                if (status.getBoolean("matching", false)
                        && status.getBoolean("done", false)) {
                    deliver(receiver, status.getBoolean("success", false),
                            status.getString("detail", "Local audio request completed"));
                    finish();
                    return;
                }
                if (++attempts >= 60) {
                    deliver(receiver, false, "Timed out waiting for local audio player");
                    finish();
                    return;
                }
                main.postDelayed(this, 100L);
            }
        });
    }

    private static void deliver(ResultReceiver receiver, boolean success, String detail) {
        if (receiver == null) return;
        Bundle result = new Bundle();
        result.putBoolean("success", success);
        result.putString("detail", detail == null ? "" : detail);
        receiver.send(success ? RESULT_OK : RESULT_CANCELED, result);
    }
}
