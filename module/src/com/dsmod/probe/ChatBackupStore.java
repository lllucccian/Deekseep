package com.dsmod.probe;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

/** Portable chat snapshot codec and matching-row overwrite engine. */
final class ChatBackupStore {
    private ChatBackupStore() {}

    static JSONObject snapshot(SQLiteDatabase db, String sid, String dbId) throws Throwable {
        if (db == null || !validSid(sid)) return null;
        Cursor session = null;
        Cursor messages = null;
        try {
            session = db.rawQuery("SELECT id,title,titleType,cache_version,cache_reset_at,"
                            + "inserted_at,updated_at,current_message_id,schema_version,pinned,"
                            + "model_type FROM chat_session_list WHERE id=?",
                    new String[]{sid});
            if (!session.moveToFirst()) return null;
            JSONArray sessionRow = cursorRow(session, 11);
            JSONArray messageRows = new JSONArray();
            messages = db.rawQuery("SELECT message_id,parent_id,role,thinking_enabled,status,"
                    + "inserted_at,feedback_type,accumulated_token_usage,ban_edit,"
                    + "ban_regenerate,tips,fragments,conversation_mode FROM "
                    + quote("chat_session_messages_" + sid) + " ORDER BY message_id", null);
            while (messages.moveToNext()) messageRows.put(cursorRow(messages, 13));
            return new JSONObject().put("db_id", dbId == null ? "" : dbId)
                    .put("sid", sid).put("session", sessionRow).put("messages", messageRows);
        } finally {
            if (session != null) try { session.close(); } catch (Throwable ignored) {}
            if (messages != null) try { messages.close(); } catch (Throwable ignored) {}
        }
    }

    static boolean overwriteMatching(SQLiteDatabase db, JSONObject root, int freezeVersion)
            throws Throwable {
        if (db == null || root == null) return false;
        String sid = root.optString("sid", "");
        JSONArray session = root.optJSONArray("session");
        JSONArray messages = root.optJSONArray("messages");
        if (!validSid(sid) || session == null || session.length() != 11 || messages == null
                || !sessionExists(db, sid)) return false;
        boolean began = false;
        try {
            db.beginTransactionNonExclusive();
            began = true;
            Object[] sessionValues = new Object[11];
            for (int i = 1; i < 11; i++) sessionValues[i - 1] = jsonValue(session, i);
            sessionValues[2] = freezeVersion;
            sessionValues[10] = sid;
            db.execSQL("UPDATE chat_session_list SET title=?,titleType=?,cache_version=?,"
                            + "cache_reset_at=?,inserted_at=?,updated_at=?,current_message_id=?,"
                            + "schema_version=?,pinned=?,model_type=? WHERE id=?",
                    sessionValues);
            createMessageTable(db, sid);
            String table = quote("chat_session_messages_" + sid);
            db.execSQL("DELETE FROM " + table);
            for (int i = 0; i < messages.length(); i++) {
                JSONArray row = messages.optJSONArray(i);
                if (row == null || row.length() != 13) continue;
                Object[] values = new Object[13];
                for (int j = 0; j < values.length; j++) values[j] = jsonValue(row, j);
                db.execSQL("INSERT OR REPLACE INTO " + table
                                + "(message_id,parent_id,role,thinking_enabled,status,inserted_at,"
                                + "feedback_type,accumulated_token_usage,ban_edit,ban_regenerate,"
                                + "tips,fragments,conversation_mode) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        values);
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            if (began) try { db.endTransaction(); } catch (Throwable ignored) {}
        }
    }

    static void createMessageTable(SQLiteDatabase db, String sid) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + quote("chat_session_messages_" + sid)
                + "(message_id INTEGER PRIMARY KEY NOT NULL, parent_id INTEGER, role TEXT,"
                + " thinking_enabled INTEGER, status TEXT, inserted_at REAL, feedback_type TEXT,"
                + " accumulated_token_usage INTEGER, ban_edit INTEGER, ban_regenerate INTEGER,"
                + " tips TEXT, fragments TEXT, conversation_mode TEXT)");
    }

    private static boolean sessionExists(SQLiteDatabase db, String sid) {
        Cursor cursor = db.rawQuery("SELECT 1 FROM chat_session_list WHERE id=? LIMIT 1",
                new String[]{sid});
        try { return cursor.moveToFirst(); }
        finally { cursor.close(); }
    }

    private static JSONArray cursorRow(Cursor cursor, int count) throws Throwable {
        JSONArray row = new JSONArray();
        for (int i = 0; i < count; i++) {
            if (cursor.isNull(i)) row.put(JSONObject.NULL);
            else if (cursor.getType(i) == Cursor.FIELD_TYPE_INTEGER) row.put(cursor.getLong(i));
            else if (cursor.getType(i) == Cursor.FIELD_TYPE_FLOAT) row.put(cursor.getDouble(i));
            else if (cursor.getType(i) == Cursor.FIELD_TYPE_BLOB) {
                row.put(Base64.encodeToString(cursor.getBlob(i), Base64.NO_WRAP));
            } else row.put(cursor.getString(i));
        }
        return row;
    }

    private static Object jsonValue(JSONArray row, int index) throws Throwable {
        return row.isNull(index) ? null : row.get(index);
    }

    private static boolean validSid(String sid) {
        if (sid == null || sid.length() == 0 || sid.length() > 128) return false;
        for (int i = 0; i < sid.length(); i++) {
            char c = sid.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_')) return false;
        }
        return true;
    }

    private static String quote(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }
}
