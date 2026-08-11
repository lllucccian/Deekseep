package com.dsmod.probe;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONObject;

/** Runs with app_process against an isolated Android SQLite database, never a user database. */
public final class ChatBackupDeviceSmoke {
    public static void main(String[] args) throws Throwable {
        if (args.length != 1) throw new IllegalArgumentException("database path required");
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(args[0], null);
        try {
            db.execSQL("CREATE TABLE chat_session_list(id TEXT PRIMARY KEY,title TEXT,"
                    + "titleType TEXT,cache_version INTEGER,cache_reset_at INTEGER,"
                    + "inserted_at REAL,updated_at REAL,current_message_id INTEGER,"
                    + "schema_version INTEGER,pinned INTEGER,model_type TEXT)");
            db.execSQL("INSERT INTO chat_session_list VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    new Object[]{"smoke-session", "source title", "SYSTEM", 7, 0,
                            1d, 2d, 1, 1, 0, "default"});
            ChatBackupStore.createMessageTable(db, "smoke-session");
            db.execSQL("INSERT INTO \"chat_session_messages_smoke-session\""
                            + "(message_id,parent_id,role,thinking_enabled,status,inserted_at,"
                            + "feedback_type,accumulated_token_usage,ban_edit,ban_regenerate,"
                            + "tips,fragments,conversation_mode) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    new Object[]{1, null, "USER", 0, "FINISHED", 1d, null, 0, 0, 0,
                            null, "source fragments", null});

            JSONObject snapshot = ChatBackupStore.snapshot(
                    db, "smoke-session", "smoke-account");
            check(snapshot != null, "snapshot created");
            db.execSQL("UPDATE chat_session_list SET title='target changed'");
            db.execSQL("UPDATE \"chat_session_messages_smoke-session\""
                    + " SET fragments='target changed'");
            check(ChatBackupStore.overwriteMatching(db, snapshot, Integer.MAX_VALUE),
                    "matching import applied");
            check("source title".equals(value(db,
                    "SELECT title FROM chat_session_list WHERE id='smoke-session'")),
                    "session row restored");
            check(String.valueOf(Integer.MAX_VALUE).equals(value(db,
                    "SELECT cache_version FROM chat_session_list WHERE id='smoke-session'")),
                    "imported session frozen");
            check("source fragments".equals(value(db,
                    "SELECT fragments FROM \"chat_session_messages_smoke-session\" WHERE message_id=1")),
                    "message rows restored");

            JSONObject missing = new JSONObject(snapshot.toString());
            missing.put("sid", "server-did-not-restore-this-session");
            check(!ChatBackupStore.overwriteMatching(db, missing, Integer.MAX_VALUE),
                    "unmatched session rejected");
            System.out.println("Chat backup device smoke passed");
        } finally {
            db.close();
        }
    }

    private static String value(SQLiteDatabase db, String sql) {
        Cursor cursor = db.rawQuery(sql, null);
        try { return cursor.moveToFirst() ? cursor.getString(0) : null; }
        finally { cursor.close(); }
    }

    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
}
