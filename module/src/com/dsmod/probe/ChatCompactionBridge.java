package com.dsmod.probe;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Host-side glue for {@link ChatContextCompactor}: folds the open conversation into a capsule on
 * demand, and hands a stored capsule to the conversation it is owed to.
 *
 * <p>Only the host may create a conversation: a session row written straight into the database
 * exists locally but has no server-side counterpart, so a request carrying its id is rejected.
 * The module therefore creates an <em>empty</em> conversation and lets the host build that
 * conversation's first request itself, against a session it owns. The capsule rides that request as
 * a prompt injection, which is also what puts it into the server-side history.
 *
 * <p>Compaction is manual. Nothing here inspects how long a conversation has grown.
 */
final class ChatCompactionBridge {
    private ChatCompactionBridge() {}

    /** Outcome of one manual fold. */
    static final class Fold {
        final String recordId;
        final String targetSid;
        final int usedChars;
        final int capsuleChars;

        Fold(String recordId, String targetSid, int usedChars, int capsuleChars) {
            this.recordId = recordId;
            this.targetSid = targetSid;
            this.usedChars = usedChars;
            this.capsuleChars = capsuleChars;
        }
    }

    /**
     * What a fold would produce, rendered without changing anything. The confirmation dialog shows
     * this, and {@link #commit} turns the very same capsule into a record, so what the user
     * approves is what gets filed.
     */
    static final class Plan {
        final String sourceSid;
        final String sourceTitle;
        final String capsule;
        final int usedChars;

        Plan(String sourceSid, String sourceTitle, String capsule, int usedChars) {
            this.sourceSid = sourceSid;
            this.sourceTitle = sourceTitle;
            this.capsule = capsule;
            this.usedChars = usedChars;
        }

        int capsuleChars() {
            return capsule.length();
        }
    }

    /**
     * Delivery only: hands {@code conversationId} the capsule it is owed, once. Called once per
     * outgoing interactive send, before the prompt is rewritten. Never throws — failing to deliver
     * must not stop the user's message from being sent.
     */
    static String claimCapsule(String conversationId) {
        try {
            if (conversationId == null || conversationId.length() == 0) return "";
            String injection = ChatContextCompactor.claimCapsuleFor(conversationId);
            if (injection.length() == 0) return "";
            Main.log("context compaction: delivered capsule sid=" + conversationId
                    + " injection_chars=" + injection.length());
            HookLogOverlay.event("HOST", "Compacted context delivered",
                    "sid=" + conversationId + " chars=" + injection.length());
            return injection;
        } catch (Throwable t) {
            Main.log("context compaction skipped: " + Main.safeThrowableMessage(t));
            return "";
        }
    }

    /**
     * Renders what a fold would produce without changing anything. Null when the conversation is
     * unreadable or holds fewer than two turns of content.
     */
    static Plan plan(String sourceSid, ClassLoader cl) {
        if (sourceSid == null || sourceSid.length() == 0) return null;
        SQLiteDatabase db = null;
        try {
            db = openDatabase(cl, true);
            if (db == null) return null;
            List<ChatContextCompactor.Turn> turns = transcript(db, sourceSid);
            if (turns.size() < 2) return null;
            String capsule = ChatContextCompactor.buildCapsule(
                    turns, ChatContextCompactor.get());
            if (capsule.length() == 0) return null;
            return new Plan(sourceSid, readTitle(db, sourceSid), capsule,
                    ChatContextCompactor.estimateChars(turns));
        } catch (Throwable t) {
            Main.log("context compaction: fold plan failed: " + Main.safeThrowableMessage(t));
            return null;
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    /**
     * Files {@code plan} as a record, creates an empty conversation and owes the record to it.
     * Nothing is written into the new conversation — its messages, and therefore their parent
     * message ids, are the host's to build.
     *
     * @return the outcome, or null when the host database is unreachable or will not accept a new
     *         conversation. A null return may still have filed a record; see {@link #capsuleRecords}.
     */
    static Fold commit(Plan plan, ClassLoader cl) {
        if (plan == null || plan.capsule.length() == 0) return null;
        SQLiteDatabase db = null;
        try {
            db = openDatabase(cl, false);
            if (db == null) return null;
            // File the record before creating anything: the capsule is the part worth keeping, so a
            // failure past this point still leaves the user something to attach by hand.
            ChatContextCompactor.Capsule record = ChatContextCompactor.addCapsule(
                    plan.sourceSid, plan.sourceTitle, plan.capsule);
            if (record == null) return null;
            String target = ChatEditorUi.createBlankConversation(db);
            if (target == null) {
                Main.log("context compaction: no target conversation; capsule kept as record id="
                        + record.id);
                return null;
            }
            ChatEditorUi.saveTitle(db, target, ChatContextCompactor.capsuleTitle(
                    plan.sourceTitle, UiLanguage.text("压缩对话", "Compacted chat")));
            ChatContextCompactor.attachCapsule(record.id, target);
            scheduleOpen(target);
            report(plan.usedChars, plan.capsuleChars(), target, record.id);
            notifyUser(UiLanguage.text("已压缩，已开启新对话",
                    "Compacted; a new conversation was started"));
            return new Fold(record.id, target, plan.usedChars, plan.capsuleChars());
        } catch (Throwable t) {
            Main.log("context compaction failed: " + Main.safeThrowableMessage(t));
            return null;
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    /**
     * Owes an already-stored record to the conversation the user is looking at, replacing whatever
     * that conversation was owed before. It is delivered with that conversation's next send.
     */
    static boolean injectInto(String recordId, String conversationId) {
        try {
            if (!ChatContextCompactor.attachCapsule(recordId, conversationId)) return false;
            Main.log("context compaction: record " + recordId + " owed to sid=" + conversationId);
            notifyUser(UiLanguage.text("已放入当前对话，发送下一条消息时注入",
                    "Attached; it is injected by your next message"));
            return true;
        } catch (Throwable t) {
            Main.log("context compaction: attach failed: " + Main.safeThrowableMessage(t));
            return false;
        }
    }

    /** Stored records, newest first. */
    static List<ChatContextCompactor.Capsule> capsuleRecords() {
        return ChatContextCompactor.capsules();
    }

    /** Current character estimate for a conversation; 0 when the thread cannot be read. */
    static int usageFor(String conversationId, ClassLoader cl) {
        if (conversationId == null || conversationId.length() == 0) return 0;
        SQLiteDatabase db = null;
        try {
            db = openDatabase(cl, true);
            if (db == null) return 0;
            return ChatContextCompactor.estimateChars(transcript(db, conversationId));
        } catch (Throwable t) {
            return 0;
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    /** Title of a conversation, for labelling its record. Empty when it cannot be read. */
    static String conversationTitle(ClassLoader cl, String conversationId) {
        if (conversationId == null || conversationId.length() == 0) return "";
        SQLiteDatabase db = null;
        try {
            db = openDatabase(cl, true);
            return db == null ? "" : readTitle(db, conversationId);
        } catch (Throwable t) {
            return "";
        } finally {
            if (db != null) try { db.close(); } catch (Throwable ignored) {}
        }
    }

    private static List<ChatContextCompactor.Turn> transcript(
            SQLiteDatabase db, String conversationId) {
        List<ChatContextCompactor.Turn> out = new ArrayList<ChatContextCompactor.Turn>();
        try {
            List<ChatEditorUi.Msg> thread = ChatEditorUi.loadThread(db, conversationId);
            for (ChatEditorUi.Msg message : thread) {
                if (message == null) continue;
                out.add(new ChatContextCompactor.Turn(message.role, message.body));
            }
        } catch (Throwable t) {
            Main.log("context compaction: transcript read failed: "
                    + Main.safeThrowableMessage(t));
            out.clear();
        }
        return out;
    }

    private static SQLiteDatabase openDatabase(ClassLoader cl, boolean readOnly) {
        try {
            File file = ChatEditorUi.currentDb(cl);
            if (file == null || !file.isFile()) return null;
            return SQLiteDatabase.openDatabase(file.getPath(), null,
                    readOnly ? SQLiteDatabase.OPEN_READONLY : SQLiteDatabase.OPEN_READWRITE);
        } catch (Throwable t) {
            Main.log("context compaction: host database unavailable: "
                    + Main.safeThrowableMessage(t));
            return null;
        }
    }

    /**
     * Follows the swap into the new conversation. Delayed so the sidebar row published from the
     * sidecar has already been materialised; the user must be looking at the new conversation for
     * their next message to continue there, which is what carries the capsule.
     */
    private static void scheduleOpen(final String sid) {
        try {
            Main module = Main.MODULE;
            if (module == null || module.main == null) return;
            module.main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!Main.openNativeSession(sid)) {
                        Main.log("context compaction: open new conversation deferred sid=" + sid);
                    }
                }
            }, 600L);
        } catch (Throwable ignored) {
        }
    }

    private static String readTitle(SQLiteDatabase db, String sid) {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT title FROM chat_session_list WHERE id=?",
                    new String[]{sid});
            if (cursor.moveToFirst()) {
                String title = cursor.getString(0);
                if (title != null) return title;
            }
        } catch (Throwable ignored) {
        } finally {
            if (cursor != null) try { cursor.close(); } catch (Throwable ignored) {}
        }
        return "";
    }

    private static void report(int usedChars, int capsuleChars, String newSid, String recordId) {
        String detail = "used_chars=" + usedChars + " capsule_chars=" + capsuleChars
                + " record=" + recordId + " target=" + newSid;
        Main.log("context compaction: " + detail);
        HookLogOverlay.event("HOST", "Context compacted", detail);
    }

    private static void notifyUser(String message) {
        try {
            Main module = Main.MODULE;
            Activity activity = module == null ? null : module.curAct.get();
            if (activity != null && !activity.isFinishing()) {
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        } catch (Throwable ignored) {
        }
    }
}
