package com.dsmod.probe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Small durable ledger for interactive Agent steps and private result delivery.
 *
 * <p>The actual tool executor remains in {@link Main}; this class stores only bounded metadata and
 * an already-sanitized private result envelope.  A process death therefore cannot make a finished
 * side effect disappear and tempt the model to repeat it.  Pending envelopes are replayed only to
 * their original conversation scope.</p>
 */
final class AgentRunStore {
    static final String STATE_EXECUTING = "executing";
    static final String STATE_WAITING_USER = "waiting_user";
    static final String STATE_RESULT_READY = "result_ready";
    static final String STATE_DELIVERING = "delivering";
    static final String STATE_WAITING_CHAT = "waiting_chat";
    static final String STATE_COMPLETED = "completed";
    static final String STATE_FAILED = "failed";
    static final String STATE_CANCELLED = "cancelled";

    private static final String DIRECTORY =
            "/data/data/com.deepseek.chat/files/deekseep_agent";
    private static final File DEFAULT_FILE = new File(DIRECTORY, "runs.json");
    private static final Store DEFAULT = new Store(DEFAULT_FILE);

    private AgentRunStore() {}

    static final class Record {
        String key = "";
        String outboxId = "";
        String scope = "";
        String callId = "";
        String tool = "";
        String state = STATE_EXECUTING;
        String detail = "";
        String event = "";
        boolean resultSuccess;
        boolean resultKnown;
        long createdAt;
        long updatedAt;
        int deliveryAttempts;
        boolean hidden;

        Record copy() {
            Record out = new Record();
            out.key = key;
            out.outboxId = outboxId;
            out.scope = scope;
            out.callId = callId;
            out.tool = tool;
            out.state = state;
            out.detail = detail;
            out.event = event;
            out.resultSuccess = resultSuccess;
            out.resultKnown = resultKnown;
            out.createdAt = createdAt;
            out.updatedAt = updatedAt;
            out.deliveryAttempts = deliveryAttempts;
            out.hidden = hidden;
            return out;
        }

        boolean hasPendingResult() {
            return outboxId.length() > 0 && event.length() > 0
                    && !STATE_CANCELLED.equals(state)
                    && !STATE_COMPLETED.equals(state)
                    && !STATE_FAILED.equals(state);
        }

        boolean isFinished() {
            return STATE_COMPLETED.equals(state)
                    || STATE_FAILED.equals(state)
                    || STATE_CANCELLED.equals(state);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("key", key);
                json.put("outbox_id", outboxId);
                json.put("scope", scope);
                json.put("call_id", callId);
                json.put("tool", tool);
                json.put("state", state);
                json.put("detail", detail);
                json.put("event", event);
                json.put("result_success", resultSuccess);
                json.put("result_known", resultKnown);
                json.put("created_at", createdAt);
                json.put("updated_at", updatedAt);
                json.put("delivery_attempts", deliveryAttempts);
                json.put("hidden", hidden);
            } catch (Throwable ignored) {}
            return json;
        }

        static Record fromJson(JSONObject json) {
            if (json == null) return null;
            Record out = new Record();
            out.key = clean(json.optString("key", ""), 2300);
            out.outboxId = clean(json.optString("outbox_id", ""), 180);
            out.scope = clean(json.optString("scope", ""), 2048);
            out.callId = clean(json.optString("call_id", ""), 128);
            out.tool = clean(json.optString("tool", ""), 64);
            out.state = cleanState(json.optString("state", STATE_EXECUTING));
            out.detail = clean(json.optString("detail", ""), 1200);
            out.event = cleanEvent(json.optString("event", ""));
            out.resultSuccess = json.optBoolean("result_success", false);
            out.resultKnown = json.optBoolean("result_known", false);
            out.createdAt = Math.max(0L, json.optLong("created_at", 0L));
            out.updatedAt = Math.max(out.createdAt,
                    json.optLong("updated_at", out.createdAt));
            out.deliveryAttempts = Math.max(0,
                    Math.min(100_000, json.optInt("delivery_attempts", 0)));
            out.hidden = json.optBoolean("hidden", false);
            if (out.scope.length() == 0 || out.callId.length() == 0
                    || out.tool.length() == 0) return null;
            if (out.key.length() == 0) out.key = callKey(out.scope, out.callId);
            if (out.createdAt == 0L) out.createdAt = out.updatedAt;
            return out;
        }
    }

    /** Package-visible store implementation so persistence behavior can be regression-tested. */
    static final class Store {
        private static final int MAX_RECORDS = 80;
        private static final int MAX_PENDING = 12;
        private static final long MAX_FILE_BYTES = 8L * 1024L * 1024L;

        private final File file;
        private ArrayList<Record> cached;

        Store(File file) {
            this.file = file;
        }

        /** Atomically reserves a call id across process lifetimes. */
        synchronized boolean claim(HeartbeatToolProtocol.ToolCall call) {
            if (!valid(call)) return false;
            ArrayList<Record> records = records();
            String key = callKey(call.scope, call.id);
            if (findByKey(records, key) != null) return false;
            Record record = new Record();
            long now = System.currentTimeMillis();
            record.key = key;
            record.scope = clean(call.scope, 2048);
            record.callId = clean(call.id, 128);
            record.tool = clean(call.tool, 64);
            record.state = STATE_EXECUTING;
            record.createdAt = now;
            record.updatedAt = now;
            records.add(0, record);
            persist(records);
            return true;
        }

        synchronized void start(HeartbeatToolProtocol.ToolCall call) {
            if (!valid(call)) return;
            ArrayList<Record> records = records();
            String key = callKey(call.scope, call.id);
            Record record = findByKey(records, key);
            long now = System.currentTimeMillis();
            if (record == null) {
                record = new Record();
                records.add(0, record);
            } else if (record.hasPendingResult()) {
                // The side effect has already completed. Never erase its outbox on a duplicate
                // stream fragment or process-restored hook.
                return;
            }
            record.key = key;
            record.scope = clean(call.scope, 2048);
            record.callId = clean(call.id, 128);
            record.tool = clean(call.tool, 64);
            record.state = STATE_EXECUTING;
            record.detail = "";
            record.event = "";
            record.outboxId = "";
            record.resultKnown = false;
            record.resultSuccess = false;
            record.deliveryAttempts = 0;
            record.hidden = false;
            record.createdAt = now;
            record.updatedAt = now;
            persist(records);
        }

        synchronized void waitingUser(HeartbeatToolProtocol.ToolCall call) {
            updateCallState(call, STATE_WAITING_USER, "");
        }

        synchronized String queueResult(
                HeartbeatToolProtocol.ToolCall call, boolean success,
                String event, String detail) {
            if (!valid(call) || event == null || event.length() == 0) return "";
            ArrayList<Record> records = records();
            String key = callKey(call.scope, call.id);
            Record record = findByKey(records, key);
            if (record == null) {
                record = new Record();
                record.key = key;
                record.scope = clean(call.scope, 2048);
                record.callId = clean(call.id, 128);
                record.tool = clean(call.tool, 64);
                record.createdAt = System.currentTimeMillis();
                records.add(0, record);
            }
            if (record.hasPendingResult()) return record.outboxId;
            long now = System.currentTimeMillis();
            record.outboxId = "result-" + Long.toHexString(now)
                    + "-" + Integer.toHexString(key.hashCode());
            record.event = cleanEvent(event);
            if (record.event.length() == 0) return "";
            record.resultKnown = true;
            record.resultSuccess = success;
            record.state = STATE_RESULT_READY;
            record.detail = clean(detail, 1200);
            record.updatedAt = now;
            record.deliveryAttempts = 0;
            persist(records);
            return record.outboxId;
        }

        synchronized void complete(
                HeartbeatToolProtocol.ToolCall call, String detail) {
            updateCallState(call, STATE_COMPLETED, detail);
        }

        synchronized List<Record> snapshot() {
            ArrayList<Record> out = new ArrayList<>();
            for (Record record : records()) {
                if (!record.hidden) out.add(record.copy());
            }
            Collections.sort(out, NEWEST_FIRST);
            return Collections.unmodifiableList(out);
        }

        synchronized List<Record> pending() {
            ArrayList<Record> out = new ArrayList<>();
            for (Record record : records()) {
                if (record.hasPendingResult()) out.add(record.copy());
            }
            Collections.sort(out, OLDEST_FIRST);
            return Collections.unmodifiableList(out);
        }

        synchronized Record findOutbox(String outboxId) {
            Record record = findByOutbox(records(), clean(outboxId, 180));
            return record == null ? null : record.copy();
        }

        synchronized boolean markDelivering(String outboxId, int attempts) {
            ArrayList<Record> records = records();
            Record record = findByOutbox(records, clean(outboxId, 180));
            if (record == null || !record.hasPendingResult()) return false;
            record.state = STATE_DELIVERING;
            record.deliveryAttempts = Math.max(record.deliveryAttempts, attempts);
            record.updatedAt = System.currentTimeMillis();
            persist(records);
            return true;
        }

        synchronized void waitingForChat(String outboxId, String detail) {
            ArrayList<Record> records = records();
            Record record = findByOutbox(records, clean(outboxId, 180));
            if (record == null || !record.hasPendingResult()) return;
            record.state = STATE_WAITING_CHAT;
            record.detail = clean(detail, 1200);
            record.updatedAt = System.currentTimeMillis();
            persist(records);
        }

        synchronized void delivered(String outboxId) {
            ArrayList<Record> records = records();
            Record record = findByOutbox(records, clean(outboxId, 180));
            if (record == null) return;
            record.event = "";
            record.state = record.resultKnown && !record.resultSuccess
                    ? STATE_FAILED : STATE_COMPLETED;
            record.updatedAt = System.currentTimeMillis();
            persist(records);
        }

        synchronized boolean cancel(String outboxId) {
            ArrayList<Record> records = records();
            Record record = findByOutbox(records, clean(outboxId, 180));
            if (record == null || !record.hasPendingResult()) return false;
            record.event = "";
            record.state = STATE_CANCELLED;
            record.detail = "Result delivery cancelled by user";
            record.updatedAt = System.currentTimeMillis();
            persist(records);
            return true;
        }

        synchronized int clearFinished() {
            ArrayList<Record> records = records();
            int cleared = 0;
            for (Record record : records) {
                if (record.isFinished() && !record.hidden) {
                    record.hidden = true;
                    cleared++;
                }
            }
            if (cleared > 0) persist(records);
            return cleared;
        }

        synchronized void invalidateCacheForTest() {
            cached = null;
        }

        private void updateCallState(
                HeartbeatToolProtocol.ToolCall call, String state, String detail) {
            if (!valid(call)) return;
            ArrayList<Record> records = records();
            Record record = findByKey(records, callKey(call.scope, call.id));
            if (record == null) return;
            record.state = state;
            record.detail = clean(detail, 1200);
            record.updatedAt = System.currentTimeMillis();
            persist(records);
        }

        private ArrayList<Record> records() {
            if (cached != null) return cached;
            cached = readFile();
            return cached;
        }

        private ArrayList<Record> readFile() {
            ArrayList<Record> out = new ArrayList<>();
            if (file == null || !file.isFile() || file.length() <= 0L
                    || file.length() > MAX_FILE_BYTES) return out;
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(file), StandardCharsets.UTF_8));
                StringBuilder text = new StringBuilder((int) file.length());
                char[] buffer = new char[8192];
                int count;
                while ((count = reader.read(buffer)) >= 0) {
                    if (count > 0) text.append(buffer, 0, count);
                    if (text.length() > MAX_FILE_BYTES) return new ArrayList<>();
                }
                JSONArray records = new JSONObject(text.toString())
                        .optJSONArray("records");
                if (records == null) return out;
                for (int i = 0; i < records.length() && out.size() < MAX_RECORDS; i++) {
                    Record record = Record.fromJson(records.optJSONObject(i));
                    if (record == null || findByKey(out, record.key) != null) continue;
                    out.add(record);
                }
            } catch (Throwable ignored) {
                out.clear();
            } finally {
                if (reader != null) try { reader.close(); } catch (Throwable ignored) {}
            }
            trim(out);
            return out;
        }

        private void persist(ArrayList<Record> records) {
            trim(records);
            if (file == null) return;
            File parent = file.getParentFile();
            File temporary = new File(file.getAbsolutePath() + ".tmp");
            OutputStreamWriter writer = null;
            FileOutputStream output = null;
            try {
                if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return;
                JSONObject root = new JSONObject();
                JSONArray array = new JSONArray();
                for (Record record : records) array.put(record.toJson());
                root.put("version", 1);
                root.put("records", array);
                output = new FileOutputStream(temporary, false);
                writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
                writer.write(root.toString());
                writer.write('\n');
                writer.flush();
                output.getFD().sync();
                writer.close();
                writer = null;
                output = null;
                if (!temporary.renameTo(file)) {
                    // Linux/Android normally atomically replaces the destination. Preserve the
                    // previous valid ledger if an unusual filesystem refuses the rename.
                    return;
                }
            } catch (Throwable ignored) {
                // Delivery still proceeds from memory; the next mutation retries persistence.
            } finally {
                if (writer != null) try { writer.close(); } catch (Throwable ignored) {}
                if (output != null) try { output.close(); } catch (Throwable ignored) {}
                if (temporary.exists()) try { temporary.delete(); } catch (Throwable ignored) {}
            }
        }

        private static void trim(ArrayList<Record> records) {
            Collections.sort(records, NEWEST_FIRST);
            int pendingKept = 0;
            for (int i = 0; i < records.size(); i++) {
                Record record = records.get(i);
                if (record.hasPendingResult() && ++pendingKept > MAX_PENDING) {
                    record.event = "";
                    record.state = STATE_FAILED;
                    record.detail = "Pending result expired because the outbox was full";
                }
            }
            while (records.size() > MAX_RECORDS) {
                int removable = records.size() - 1;
                for (int i = records.size() - 1; i >= 0; i--) {
                    if (!records.get(i).hasPendingResult()) {
                        removable = i;
                        break;
                    }
                }
                records.remove(removable);
            }
        }
    }

    static void start(HeartbeatToolProtocol.ToolCall call) {
        DEFAULT.start(call);
    }

    static boolean claim(HeartbeatToolProtocol.ToolCall call) {
        return DEFAULT.claim(call);
    }

    static void waitingUser(HeartbeatToolProtocol.ToolCall call) {
        DEFAULT.waitingUser(call);
    }

    static String queueResult(
            HeartbeatToolProtocol.ToolCall call, boolean success,
            String event, String detail) {
        return DEFAULT.queueResult(call, success, event, detail);
    }

    static void complete(HeartbeatToolProtocol.ToolCall call, String detail) {
        DEFAULT.complete(call, detail);
    }

    static List<Record> snapshot() {
        return DEFAULT.snapshot();
    }

    static List<Record> pending() {
        return DEFAULT.pending();
    }

    static Record findOutbox(String outboxId) {
        return DEFAULT.findOutbox(outboxId);
    }

    static boolean markDelivering(String outboxId, int attempts) {
        return DEFAULT.markDelivering(outboxId, attempts);
    }

    static void waitingForChat(String outboxId, String detail) {
        DEFAULT.waitingForChat(outboxId, detail);
    }

    static void delivered(String outboxId) {
        DEFAULT.delivered(outboxId);
    }

    static boolean cancel(String outboxId) {
        return DEFAULT.cancel(outboxId);
    }

    static int clearFinished() {
        return DEFAULT.clearFinished();
    }

    private static boolean valid(HeartbeatToolProtocol.ToolCall call) {
        return call != null && clean(call.scope, 2048).length() > 0
                && clean(call.id, 128).length() > 0
                && clean(call.tool, 64).length() > 0;
    }

    private static String callKey(String scope, String callId) {
        return clean(scope, 2048) + "|" + clean(callId, 128);
    }

    private static Record findByKey(List<Record> records, String key) {
        if (records == null || key == null) return null;
        for (Record record : records) {
            if (record != null && key.equals(record.key)) return record;
        }
        return null;
    }

    private static Record findByOutbox(List<Record> records, String outboxId) {
        if (records == null || outboxId == null || outboxId.length() == 0) return null;
        for (Record record : records) {
            if (record != null && outboxId.equals(record.outboxId)) return record;
        }
        return null;
    }

    private static String cleanState(String value) {
        String state = value == null ? "" : value.trim().toLowerCase(Locale.US);
        if (STATE_WAITING_USER.equals(state) || STATE_RESULT_READY.equals(state)
                || STATE_DELIVERING.equals(state) || STATE_WAITING_CHAT.equals(state)
                || STATE_COMPLETED.equals(state) || STATE_FAILED.equals(state)
                || STATE_CANCELLED.equals(state)) return state;
        return STATE_EXECUTING;
    }

    private static String clean(String value, int max) {
        String out = value == null ? "" : value.trim();
        if (out.length() > max) out = out.substring(0, max);
        return out;
    }

    private static String cleanEvent(String value) {
        String event = value == null ? "" : value.trim();
        // A 48 KiB tool output can grow substantially when JSON escapes control characters.
        // This ceiling is above the protocol's worst practical envelope, so recovery never stores
        // a truncated marker or half a JSON string.
        if (event.length() > 512 * 1024) event = event.substring(0, 512 * 1024);
        return event;
    }

    private static final Comparator<Record> NEWEST_FIRST = new Comparator<Record>() {
        @Override public int compare(Record left, Record right) {
            return Long.compare(right == null ? 0L : right.updatedAt,
                    left == null ? 0L : left.updatedAt);
        }
    };

    private static final Comparator<Record> OLDEST_FIRST = new Comparator<Record>() {
        @Override public int compare(Record left, Record right) {
            return Long.compare(left == null ? 0L : left.createdAt,
                    right == null ? 0L : right.createdAt);
        }
    };
}
