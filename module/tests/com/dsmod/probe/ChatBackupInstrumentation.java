package com.dsmod.probe;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

import java.io.File;

/** Runs the chat backup smoke test inside a disposable Android test package. */
public final class ChatBackupInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        String databaseName = "deekseep_chat_backup_device_smoke.db";
        try {
            File database = getTargetContext().getDatabasePath(databaseName);
            File parent = database.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new IllegalStateException("cannot create isolated database directory");
            }
            ChatBackupDeviceSmoke.main(new String[]{database.getAbsolutePath()});
            result.putString("stream", "Chat backup device smoke passed\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "Chat backup device smoke failed: " + failure + "\n");
            finish(Activity.RESULT_CANCELED, result);
        } finally {
            try { getTargetContext().deleteDatabase(databaseName); } catch (Throwable ignored) {}
        }
    }
}
