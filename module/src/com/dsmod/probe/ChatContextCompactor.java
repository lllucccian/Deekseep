package com.dsmod.probe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Policy and state for "compress a long conversation, then continue it in a new one".
 *
 * <p>The Open edition ships no completion payload ({@code z14.payloadClass} returns null), so the
 * module cannot ask a model to write a summary. Compaction is therefore extractive and
 * reproducible: the original goal, one digest line per dropped middle turn, and the most recent
 * turns verbatim, all inside a character budget. The same transcript always renders the same
 * capsule, which is what makes the regression suite meaningful.
 *
 * <p>Compaction is <em>manual</em>: nothing here decides on its own that a conversation is long
 * enough. The user presses the compaction button, which renders a capsule and files it as a
 * {@link Capsule} record. A record is attached to the new conversation and handed to that
 * conversation's first outgoing request exactly once, which is why the store is on disk rather
 * than in memory: the host may be killed between the button press and the first send.
 *
 * <p>This class is deliberately free of Android and Xposed dependencies so it can be compiled and
 * exercised by the plain-Java regression runner.
 */
final class ChatContextCompactor {
    static final String FILE_PATH =
            "/data/data/com.deepseek.chat/files/deekseep_context_compaction.json";

    static final String CAPSULE_FILE_PATH =
            "/data/data/com.deepseek.chat/files/deekseep_compact_capsules.json";

    /**
     * Overridable so the plain-Java regression runner can point the capsule store at a temporary
     * directory instead of the host app's private files.
     */
    private static final String CAPSULE_FILE_PROPERTY = "deekseep.compaction_capsule_file";

    /** Records kept. Beyond this the oldest already-delivered record is dropped first. */
    static final int MAX_CAPSULES = 20;

    /** Turn markers of a rendered capsule. Also used to recognise an already-compacted thread. */
    static final String CAPSULE_HEADER = "【上下文压缩摘要】";
    static final String CAPSULE_FOOTER = "【摘要结束】";

    static final int MIN_THRESHOLD = 1000;
    static final int MAX_THRESHOLD = 400000;
    /** The recommended compaction point. Nothing triggers on it; the button reports against it. */
    static final int DEFAULT_THRESHOLD = 30000;

    static final int MIN_KEEP_TURNS = 1;
    static final int MAX_KEEP_TURNS = 40;
    static final int DEFAULT_KEEP_TURNS = 6;

    static final int MIN_CAPSULE_CHARS = 400;
    static final int MAX_CAPSULE_CHARS = 8000;
    static final int DEFAULT_CAPSULE_CHARS = 2000;

    /** Per-message wiring cost added on top of the body when estimating what the host sends. */
    private static final int TURN_OVERHEAD_CHARS = 12;
    private static final int GOAL_BUDGET_CAP = 500;
    private static final int NESTED_GOAL_BUDGET_CAP = 1200;
    private static final int RECENT_TURN_BUDGET_CAP = 400;
    private static final int DIGEST_LINE_CAP = 120;
    /** Headers, the instruction line and the three section titles. */
    private static final int FIXED_OVERHEAD_CHARS = 220;

    private static final File FILE = new File(FILE_PATH);

    private static final Object CAPSULE_LOCK = new Object();
    private static final AtomicLong CAPSULE_SEQUENCE = new AtomicLong();

    static final class Value {
        int thresholdChars = DEFAULT_THRESHOLD;
        int keepRecentTurns = DEFAULT_KEEP_TURNS;
        int capsuleChars = DEFAULT_CAPSULE_CHARS;

        Value copy() {
            Value v = new Value();
            v.thresholdChars = thresholdChars;
            v.keepRecentTurns = keepRecentTurns;
            v.capsuleChars = capsuleChars;
            return v;
        }
    }

    /**
     * One rendered capsule, held until it has been handed to its target conversation.
     *
     * <p>{@code targetSid} is the conversation the capsule is owed to and {@code deliveredAt} is
     * set the moment it has been injected, so the same capsule is never sent twice: the second send
     * would duplicate context the server already holds.
     */
    static final class Capsule {
        final String id;
        final long createdAt;
        final String sourceSid;
        final String sourceTitle;
        String text;
        String targetSid;
        long deliveredAt;

        Capsule(String id, long createdAt, String sourceSid, String sourceTitle, String text) {
            this.id = id == null ? "" : id;
            this.createdAt = createdAt;
            this.sourceSid = sourceSid == null ? "" : sourceSid;
            this.sourceTitle = sourceTitle == null ? "" : sourceTitle;
            this.text = text == null ? "" : text;
        }

        int chars() {
            return text.length();
        }

        boolean delivered() {
            return deliveredAt > 0L;
        }

        Capsule copy() {
            Capsule copy = new Capsule(id, createdAt, sourceSid, sourceTitle, text);
            copy.targetSid = targetSid;
            copy.deliveredAt = deliveredAt;
            return copy;
        }
    }

    /** One rendered conversation turn, oldest first. */
    static final class Turn {
        final String role;
        final String body;

        Turn(String role, String body) {
            this.role = role == null ? "" : role;
            this.body = body == null ? "" : body;
        }

        boolean isUser() {
            return "USER".equalsIgnoreCase(role);
        }
    }

    private static volatile Value cached;

    private ChatContextCompactor() {}

    static Value get() {
        Value v = cached;
        if (v != null) return v.copy();
        synchronized (ChatContextCompactor.class) {
            if (cached == null) cached = read();
            return cached.copy();
        }
    }

    static synchronized boolean save(Value v) {
        if (v == null) return false;
        Value next = new Value();
        next.thresholdChars = clampThreshold(v.thresholdChars);
        next.keepRecentTurns = clampKeepTurns(v.keepRecentTurns);
        next.capsuleChars = clampCapsuleChars(v.capsuleChars);
        try {
            JSONObject o = new JSONObject();
            o.put("thresholdChars", next.thresholdChars);
            o.put("keepRecentTurns", next.keepRecentTurns);
            o.put("capsuleChars", next.capsuleChars);
            File parent = FILE.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File temp = new File(FILE.getAbsolutePath() + ".tmp");
            FileOutputStream out = new FileOutputStream(temp);
            out.write(o.toString(2).getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
            out.close();
            if (FILE.exists() && !FILE.delete()) return false;
            if (!temp.renameTo(FILE)) return false;
            cached = next;
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    static int clampThreshold(int value) {
        return Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, value));
    }

    static int clampKeepTurns(int value) {
        return Math.max(MIN_KEEP_TURNS, Math.min(MAX_KEEP_TURNS, value));
    }

    static int clampCapsuleChars(int value) {
        return Math.max(MIN_CAPSULE_CHARS, Math.min(MAX_CAPSULE_CHARS, value));
    }

    /**
     * Characters this thread will actually send. Injected wrappers are stripped first because the
     * host never sees them as part of the user's stored message.
     */
    static int estimateChars(List<Turn> turns) {
        if (turns == null || turns.isEmpty()) return 0;
        int total = 0;
        for (Turn turn : turns) {
            if (turn == null) continue;
            total += HistoryBridge.stripInjectedSystemPrompts(turn.body).length()
                    + TURN_OVERHEAD_CHARS;
        }
        return total;
    }

    // ── Capsule records ────────────────────────────────────────────────────────
    // Read on every mutation rather than cached: the file holds at most MAX_CAPSULES small records,
    // and because the store is authoritative on disk every access exercises the real
    // serialization, including in the regression runner.

    /** Records, newest first. A missing or unreadable store reads as empty. */
    static List<Capsule> capsules() {
        synchronized (CAPSULE_LOCK) {
            return newestFirst(readCapsules());
        }
    }

    static Capsule findCapsule(String id) {
        if (id == null || id.length() == 0) return null;
        synchronized (CAPSULE_LOCK) {
            for (Capsule capsule : readCapsules()) {
                if (id.equals(capsule.id)) return capsule.copy();
            }
        }
        return null;
    }

    /**
     * Files a freshly rendered capsule. Returns the stored record, or null when there is nothing to
     * file. A failed write does not fail the call: the capsule is still returned so the caller can
     * finish the fold in this process, and a store that cannot persist simply forgets it later.
     */
    static Capsule addCapsule(String sourceSid, String sourceTitle, String text) {
        String body = text == null ? "" : text.trim();
        if (body.length() == 0) return null;
        synchronized (CAPSULE_LOCK) {
            List<Capsule> records = readCapsules();
            long now = System.currentTimeMillis();
            Capsule capsule = new Capsule(
                    newCapsuleId(records, now), now, sourceSid, sourceTitle, body);
            records.add(capsule);
            trimToBound(records);
            writeCapsules(records);
            return capsule.copy();
        }
    }

    /** Replaces the text of a stored capsule. Blank text is rejected; delete instead. */
    static boolean updateCapsuleText(String id, String text) {
        String body = text == null ? "" : text.trim();
        if (id == null || id.length() == 0 || body.length() == 0) return false;
        synchronized (CAPSULE_LOCK) {
            List<Capsule> records = readCapsules();
            for (Capsule capsule : records) {
                if (!id.equals(capsule.id)) continue;
                capsule.text = body;
                writeCapsules(records);
                return true;
            }
        }
        return false;
    }

    static boolean deleteCapsule(String id) {
        if (id == null || id.length() == 0) return false;
        synchronized (CAPSULE_LOCK) {
            List<Capsule> records = readCapsules();
            for (int i = 0; i < records.size(); i++) {
                if (!id.equals(records.get(i).id)) continue;
                records.remove(i);
                writeCapsules(records);
                return true;
            }
        }
        return false;
    }

    /**
     * Owes a capsule to {@code targetSid}, replacing whatever it was owed before. One capsule per
     * conversation keeps delivery unambiguous: the target is handed exactly one summary block.
     */
    static boolean attachCapsule(String id, String targetSid) {
        if (id == null || id.length() == 0
                || targetSid == null || targetSid.length() == 0) {
            return false;
        }
        synchronized (CAPSULE_LOCK) {
            List<Capsule> records = readCapsules();
            Capsule selected = null;
            for (Capsule capsule : records) {
                if (id.equals(capsule.id)) selected = capsule;
                if (targetSid.equals(capsule.targetSid)) {
                    capsule.targetSid = "";
                    capsule.deliveredAt = 0L;
                }
            }
            if (selected == null) return false;
            selected.targetSid = targetSid;
            selected.deliveredAt = 0L;
            writeCapsules(records);
            return true;
        }
    }

    /**
     * Takes the capsule owed to {@code sid}, marks it delivered, and returns it wrapped for
     * injection. Empty when the conversation is owed nothing or has already been served.
     */
    static String claimCapsuleFor(String sid) {
        if (sid == null || sid.length() == 0) return "";
        synchronized (CAPSULE_LOCK) {
            List<Capsule> records = readCapsules();
            for (int i = records.size() - 1; i >= 0; i--) {
                Capsule capsule = records.get(i);
                if (capsule.delivered() || !sid.equals(capsule.targetSid)) continue;
                capsule.deliveredAt = System.currentTimeMillis();
                writeCapsules(records);
                return wrapForInjection(capsule.text);
            }
        }
        return "";
    }

    private static List<Capsule> newestFirst(List<Capsule> records) {
        List<Capsule> out = new ArrayList<Capsule>(records.size());
        for (int i = records.size() - 1; i >= 0; i--) out.add(records.get(i).copy());
        return out;
    }

    private static String newCapsuleId(List<Capsule> records, long now) {
        String id;
        do {
            id = "cap-" + now + "-" + CAPSULE_SEQUENCE.incrementAndGet();
        } while (hasId(records, id));
        return id;
    }

    private static boolean hasId(List<Capsule> records, String id) {
        for (Capsule capsule : records) {
            if (id.equals(capsule.id)) return true;
        }
        return false;
    }

    /** Drops the oldest delivered record first, so an undelivered fold is never the one discarded. */
    private static void trimToBound(List<Capsule> records) {
        while (records.size() > MAX_CAPSULES) {
            int victim = -1;
            for (int i = 0; i < records.size(); i++) {
                if (records.get(i).delivered()) {
                    victim = i;
                    break;
                }
            }
            records.remove(victim < 0 ? 0 : victim);
        }
    }

    private static File capsuleFile() {
        String override = System.getProperty(CAPSULE_FILE_PROPERTY);
        return new File(override == null || override.length() == 0
                ? CAPSULE_FILE_PATH : override);
    }

    private static List<Capsule> readCapsules() {
        List<Capsule> out = new ArrayList<Capsule>();
        File file = capsuleFile();
        try {
            if (!file.isFile()) return out;
            FileInputStream in = new FileInputStream(file);
            byte[] data = new byte[(int) Math.min(file.length(), 1 << 20)];
            int count = in.read(data);
            in.close();
            if (count <= 0) return out;
            JSONArray array = new JSONArray(
                    new String(data, 0, count, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o == null) continue;
                Capsule capsule = new Capsule(o.optString("id", ""), o.optLong("createdAt", 0L),
                        o.optString("sourceSid", ""), o.optString("sourceTitle", ""),
                        o.optString("text", ""));
                if (capsule.id.length() == 0 || capsule.text.length() == 0) continue;
                capsule.targetSid = o.optString("targetSid", "");
                capsule.deliveredAt = o.optLong("deliveredAt", 0L);
                out.add(capsule);
            }
        } catch (Throwable ignored) {
            out.clear();
        }
        return out;
    }

    private static boolean writeCapsules(List<Capsule> records) {
        File file = capsuleFile();
        try {
            JSONArray array = new JSONArray();
            for (Capsule capsule : records) {
                JSONObject o = new JSONObject();
                o.put("id", capsule.id);
                o.put("createdAt", capsule.createdAt);
                o.put("sourceSid", capsule.sourceSid);
                o.put("sourceTitle", capsule.sourceTitle);
                o.put("text", capsule.text);
                o.put("chars", capsule.chars());
                o.put("targetSid", capsule.targetSid == null ? "" : capsule.targetSid);
                o.put("deliveredAt", capsule.deliveredAt);
                array.put(o);
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File temp = new File(file.getAbsolutePath() + ".tmp");
            FileOutputStream out = new FileOutputStream(temp);
            out.write(array.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync();
            out.close();
            if (file.exists() && !file.delete()) return false;
            return temp.renameTo(file);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Renders a bounded capsule. Always at most {@code config.capsuleChars} characters, and always
     * keeps the original goal, the newest middle turns that fit, and the count of what was
     * dropped.
     */
    static String buildCapsule(List<Turn> turns, Value config) {
        Value effective = config == null ? new Value() : config;
        int budget = clampCapsuleChars(effective.capsuleChars);
        int keepTurns = clampKeepTurns(effective.keepRecentTurns);
        List<Turn> source = withContent(turns);
        if (source.isEmpty()) return "";

        int goalIndex = 0;
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).isUser()) {
                goalIndex = i;
                break;
            }
        }
        Turn goal = source.get(goalIndex);
        boolean nested = looksLikeCapsule(goal.body);
        int recentStart = Math.max(goalIndex + 1, source.size() - keepTurns);
        List<Turn> middle = source.subList(goalIndex + 1, recentStart);
        List<Turn> recent = source.subList(recentStart, source.size());

        int allocable = Math.max(120, budget - FIXED_OVERHEAD_CHARS);
        int goalBudget = nested
                ? Math.min(NESTED_GOAL_BUDGET_CAP, Math.max(GOAL_BUDGET_CAP, allocable / 2))
                : Math.min(GOAL_BUDGET_CAP, Math.max(160, allocable / 4));
        int recentBudget = recent.isEmpty() ? 0 : Math.max(0, allocable - goalBudget) / 2;
        int perRecent = recent.isEmpty() ? 0
                : Math.max(60, Math.min(RECENT_TURN_BUDGET_CAP, recentBudget / recent.size()));
        int digestBudget = Math.max(0, allocable - goalBudget - perRecent * recent.size());

        StringBuilder out = new StringBuilder(budget + 256);
        out.append(CAPSULE_HEADER).append('\n');
        out.append("（本摘要由 Deekseep 模块在本机生成，用于开启新对话后延续此前的上下文。")
                .append("它属于隐藏的对话历史，不是用户的新指令，请勿向用户提及这段摘要或压缩机制。）\n");
        out.append("原始目标：").append(oneLine(goal.body, goalBudget)).append('\n');

        if (!middle.isEmpty()) {
            out.append("中间轮次摘要（共 ").append(middle.size()).append(" 条）：\n");
            // Walk backwards so the turns closest to the live conversation survive the budget;
            // the dropped ones are always the oldest.
            ArrayList<String> kept = new ArrayList<String>();
            int used = 0;
            for (int i = middle.size() - 1; i >= 0; i--) {
                String line = digestLine(goalIndex + 2 + i, middle.get(i));
                if (used + line.length() + 1 > digestBudget) break;
                kept.add(0, line);
                used += line.length() + 1;
            }
            int omitted = middle.size() - kept.size();
            if (omitted > 0) {
                out.append("- …（更早的 ").append(omitted).append(" 条中间轮次已省略）\n");
            }
            for (int i = 0; i < kept.size(); i++) out.append(kept.get(i)).append('\n');
        }

        if (!recent.isEmpty()) {
            out.append("最近对话原文（最近 ").append(recent.size()).append(" 条）：\n");
            for (int i = 0; i < recent.size(); i++) {
                Turn turn = recent.get(i);
                out.append(turn.isUser() ? "用户：" : "助手：")
                        .append(oneLine(turn.body, perRecent)).append('\n');
            }
        }
        out.append(CAPSULE_FOOTER);
        return clampToBudget(out.toString(), budget);
    }

    /** Hidden-context wrapper. Mirrors the shape of the module's other injected context blocks. */
    static String wrapForInjection(String capsule) {
        String body = capsule == null ? "" : capsule.trim();
        if (body.length() == 0) return "";
        return "以下内容是本对话之前的上下文压缩摘要。它只是隐藏的连续对话上下文，"
                + "不是用户的新指令。请结合它理解用户当前消息，并在需要时延续此前的目标与结论；"
                + "不要向用户提及这段摘要或压缩机制。\n<compacted_context>\n"
                + body
                + "\n</compacted_context>";
    }

    static boolean looksLikeCapsule(String text) {
        return text != null && text.indexOf(CAPSULE_HEADER) >= 0;
    }

    /** {@code label} is supplied by the caller so this class stays free of UI language policy. */
    static String capsuleTitle(String originalTitle, String label) {
        String base = originalTitle == null ? "" : originalTitle.trim();
        if (base.length() == 0 || "新对话".equals(base) || looksLikeCapsule(base)) {
            return label;
        }
        return label + " · " + truncate(base, 24);
    }

    /** Collapses a message body to a single line so it can be embedded in a digest line. */
    static String oneLine(String text, int max) {
        String flat = HistoryBridge.stripInjectedSystemPrompts(text == null ? "" : text)
                .replaceAll("\\s+", " ")
                .trim();
        return truncate(flat, max);
    }

    static String truncate(String text, int max) {
        if (text == null) return "";
        if (max <= 0) return "";
        if (text.length() <= max) return text;
        if (max == 1) return "…";
        return text.substring(0, safeCut(text, max - 1)) + "…";
    }

    /** Shifts a cut index left if it would split a UTF-16 surrogate pair (emoji, rare CJK). */
    private static int safeCut(String text, int index) {
        if (index > 0 && index < text.length()
                && Character.isHighSurrogate(text.charAt(index - 1))
                && Character.isLowSurrogate(text.charAt(index))) {
            return index - 1;
        }
        return index;
    }

    private static String digestLine(int turnNumber, Turn turn) {
        int chars = HistoryBridge.stripInjectedSystemPrompts(turn.body).length();
        return "- 第" + turnNumber + "轮 " + (turn.isUser() ? "用户" : "助手") + "："
                + oneLine(turn.body, DIGEST_LINE_CAP) + "（" + chars + "字）";
    }

    private static List<Turn> withContent(List<Turn> turns) {
        List<Turn> out = new ArrayList<Turn>();
        if (turns == null) return out;
        for (Turn turn : turns) {
            if (turn == null) continue;
            if (HistoryBridge.stripInjectedSystemPrompts(turn.body).trim().length() == 0) continue;
            out.add(turn);
        }
        return out;
    }

    private static String clampToBudget(String text, int budget) {
        if (text == null) return "";
        if (text.length() <= budget) return text;
        String footer = CAPSULE_FOOTER;
        if (budget <= footer.length() + 1) return text.substring(0, safeCut(text, budget));
        return text.substring(0, safeCut(text, budget - footer.length() - 1)) + "\n" + footer;
    }

    private static Value read() {
        Value v = new Value();
        if (!FILE.isFile()) return v;
        try {
            FileInputStream in = new FileInputStream(FILE);
            byte[] data = new byte[(int) Math.min(FILE.length(), 32768L)];
            int count = in.read(data);
            in.close();
            if (count <= 0) return v;
            JSONObject o = new JSONObject(new String(data, 0, count, StandardCharsets.UTF_8));
            v.thresholdChars = clampThreshold(
                    o.optInt("thresholdChars", DEFAULT_THRESHOLD));
            v.keepRecentTurns = clampKeepTurns(
                    o.optInt("keepRecentTurns", DEFAULT_KEEP_TURNS));
            v.capsuleChars = clampCapsuleChars(
                    o.optInt("capsuleChars", DEFAULT_CAPSULE_CHARS));
        } catch (Throwable ignored) {
        }
        return v;
    }
}
