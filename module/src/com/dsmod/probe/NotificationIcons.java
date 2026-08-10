package com.dsmod.probe;

import android.content.Context;

/** Resolves a valid monochrome DeepSeek whale for every module notification. */
final class NotificationIcons {
    private static final String DEEPSEEK_PACKAGE = "com.deepseek.chat";

    private NotificationIcons() {}

    static int smallIcon(Context context) {
        if (context == null) return android.R.drawable.stat_notify_chat;
        String packageName = context.getPackageName();
        if (DEEPSEEK_PACKAGE.equals(packageName)) {
            int hostWhale = context.getResources().getIdentifier(
                    "chat_welcome_logo", "drawable", packageName);
            if (hostWhale != 0) return hostWhale;
            int hostForeground = context.getResources().getIdentifier(
                    "ic_launcher_foreground", "drawable", packageName);
            if (hostForeground != 0) return hostForeground;
        }
        int moduleWhale = context.getResources().getIdentifier(
                "ds_notification_whale", "drawable", packageName);
        if (moduleWhale != 0) return moduleWhale;
        return android.R.drawable.stat_notify_chat;
    }
}
