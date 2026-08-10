package com.dsmod.probe;

import org.json.JSONObject;

public final class ChatBackupFormatRegressionTest {
    public static void main(String[] args) throws Exception {
        JSONObject valid = new JSONObject()
                .put("format", "deekseep-chat-backup")
                .put("version", 1);
        check(DeekseepTools.validBackupManifest(valid), "current manifest accepted");
        check(!DeekseepTools.validBackupManifest(new JSONObject()
                .put("format", "deekseep-chat-backup").put("version", 2)),
                "unknown format version rejected");
        check(DeekseepTools.portableSessionEntry(
                "sessions/account__session-id.json"), "session entry accepted");
        check(!DeekseepTools.portableSessionEntry(
                "sessions/../manifest.json"), "relative traversal rejected");
        check(!DeekseepTools.portableSessionEntry(
                "sessions\\account__session.json"), "backslash traversal rejected");
        System.out.println("Chat backup format regression tests passed");
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
