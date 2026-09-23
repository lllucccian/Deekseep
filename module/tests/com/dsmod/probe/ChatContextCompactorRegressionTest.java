package com.dsmod.probe;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Regression harness for the pure compaction policy layer. Runs on a desktop JVM, so the capsule
 * store is redirected to a scratch file through the {@code deekseep.compaction_capsule_file} system
 * property; the same override the module honours on the device.
 */
public final class ChatContextCompactorRegressionTest {
    /** Mirrors {@code ChatContextCompactor.CAPSULE_FILE_PROPERTY}, which is private to the module. */
    private static final String CAPSULE_FILE_PROPERTY = "deekseep.compaction_capsule_file";

    public static void main(String[] args) {
        String override = System.getProperty(CAPSULE_FILE_PROPERTY);
        if (override == null || override.length() == 0) {
            // Never fall back to the device path: this harness must not be able to touch the host
            // application's data directory just because a flag was forgotten.
            File dir = new File(System.getProperty("java.io.tmpdir"),
                    "deekseep-compaction-test-" + System.nanoTime());
            override = new File(dir, "capsules.json").getAbsolutePath();
            System.setProperty(CAPSULE_FILE_PROPERTY, override);
        }
        System.out.println("capsule store: " + override);

        clampsConfiguration();
        estimatesCharacters();
        rendersBoundedCapsule();
        capsulesNeverSplitSurrogates();
        keepsGoalAndRecentTurns();
        reportsDroppedTurns();
        nestedCapsuleKeepsMoreOfTheGoal();
        isDeterministic();
        wrapsInjection();
        labelsAndTitles();
        truncationBoundaries();

        capsuleStoreRoundTrips();
        capsuleStoreBoundsHistory();
        capsuleStoreEvictsDeliveredFirst();
        capsuleDeliveryIsExactlyOnce();
        oneCapsulePerConversation();
        capsulesAreEditableAndDeletable();
        unwritableStoreNeverThrows();

        System.out.println("Context compaction regression passed.");
    }

    private static void clampsConfiguration() {
        require(ChatContextCompactor.clampThreshold(1) == ChatContextCompactor.MIN_THRESHOLD,
                "a below-range threshold must clamp up");
        require(ChatContextCompactor.clampThreshold(1 << 30) == ChatContextCompactor.MAX_THRESHOLD,
                "an above-range threshold must clamp down");
        require(ChatContextCompactor.clampKeepTurns(0) == ChatContextCompactor.MIN_KEEP_TURNS,
                "keeping zero recent turns must clamp up");
        require(ChatContextCompactor.clampKeepTurns(999) == ChatContextCompactor.MAX_KEEP_TURNS,
                "keeping too many recent turns must clamp down");
        require(ChatContextCompactor.clampCapsuleChars(0) == ChatContextCompactor.MIN_CAPSULE_CHARS,
                "a tiny capsule budget must clamp up");
        require(ChatContextCompactor.clampCapsuleChars(1 << 20) == ChatContextCompactor.MAX_CAPSULE_CHARS,
                "an oversized capsule budget must clamp down");

        ChatContextCompactor.Value defaults = new ChatContextCompactor.Value();
        require(defaults.thresholdChars == ChatContextCompactor.DEFAULT_THRESHOLD,
                "the default threshold must come from the policy constant");
        require(defaults.keepRecentTurns > 0 && defaults.capsuleChars > 0,
                "a default configuration must be usable without any user input");
    }

    private static void estimatesCharacters() {
        require(ChatContextCompactor.estimateChars(null) == 0, "null must estimate as zero");
        require(ChatContextCompactor.estimateChars(new ArrayList<ChatContextCompactor.Turn>()) == 0,
                "an empty thread must estimate as zero");

        List<ChatContextCompactor.Turn> plain = turns("USER", "hello world");
        List<ChatContextCompactor.Turn> wrapped = turns("USER",
                HistoryBridge.wrapSystemPrompt("system text", "hello world"));
        require(ChatContextCompactor.estimateChars(plain)
                        == ChatContextCompactor.estimateChars(wrapped),
                "an injected wrapper must not count toward the context estimate");

        require(ChatContextCompactor.estimateChars(turns("USER", "a", "ASSISTANT", "b"))
                        > ChatContextCompactor.estimateChars(turns("USER", "a")),
                "a longer thread must estimate higher");
    }

    private static void rendersBoundedCapsule() {
        ChatContextCompactor.Value config = new ChatContextCompactor.Value();
        List<ChatContextCompactor.Turn> thread = conversation(60, 400);
        for (int budget = ChatContextCompactor.MIN_CAPSULE_CHARS;
             budget <= ChatContextCompactor.MAX_CAPSULE_CHARS; budget += 200) {
            config.capsuleChars = budget;
            String capsule = ChatContextCompactor.buildCapsule(thread, config);
            require(capsule.length() <= budget,
                    "capsule exceeded its budget: " + capsule.length() + " > " + budget);
            require(capsule.startsWith(ChatContextCompactor.CAPSULE_HEADER),
                    "every capsule must open with its header");
            require(capsule.endsWith(ChatContextCompactor.CAPSULE_FOOTER),
                    "every capsule must close with its footer");
        }

        config.capsuleChars = ChatContextCompactor.DEFAULT_CAPSULE_CHARS;
        require(ChatContextCompactor.buildCapsule(new ArrayList<ChatContextCompactor.Turn>(),
                config).length() == 0,
                "an empty thread must not render a capsule");
    }

    /**
     * The budget clamp is the cut that actually fires for real threads, so drive it with
     * astral-plane bodies where every code-unit boundary falls inside a surrogate pair.
     */
    private static void capsulesNeverSplitSurrogates() {
        List<ChatContextCompactor.Turn> thread = new ArrayList<ChatContextCompactor.Turn>();
        for (int i = 0; i < 60; i++) {
            StringBuilder body = new StringBuilder();
            for (int j = 0; j < 40; j++) body.append(i % 2 == 0 ? "\uD83D\uDE00" : "\uD83D\uDCA9");
            thread.add(new ChatContextCompactor.Turn(i % 2 == 0 ? "USER" : "ASSISTANT",
                    body.toString()));
        }
        ChatContextCompactor.Value config = new ChatContextCompactor.Value();
        config.keepRecentTurns = 4;
        for (int budget = ChatContextCompactor.MIN_CAPSULE_CHARS; budget <= 600; budget++) {
            config.capsuleChars = budget;
            String capsule = ChatContextCompactor.buildCapsule(thread, config);
            require(!hasLoneSurrogate(capsule),
                    "a capsule cut must not split a surrogate pair at budget " + budget);
            require(capsule.length() <= budget, "capsule exceeded its budget at " + budget);
            require(capsule.endsWith(ChatContextCompactor.CAPSULE_FOOTER),
                    "every capsule must close with its footer at budget " + budget);
        }
    }

    private static boolean hasLoneSurrogate(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(i + 1))) {
                    return true;
                }
                i++;
            } else if (Character.isLowSurrogate(c)) {
                return true;
            }
        }
        return false;
    }

    private static void keepsGoalAndRecentTurns() {
        ChatContextCompactor.Value config = new ChatContextCompactor.Value();
        config.capsuleChars = ChatContextCompactor.MAX_CAPSULE_CHARS;
        config.keepRecentTurns = 3;
        List<ChatContextCompactor.Turn> thread = new ArrayList<ChatContextCompactor.Turn>();
        thread.add(new ChatContextCompactor.Turn("ASSISTANT", "greeting before the goal"));
        thread.add(new ChatContextCompactor.Turn("USER", "GOAL_MARKER please refactor the parser"));
        for (int i = 0; i < 30; i++) {
            thread.add(new ChatContextCompactor.Turn(i % 2 == 0 ? "ASSISTANT" : "USER",
                    "middle turn number " + i + " with some filler text"));
        }
        thread.add(new ChatContextCompactor.Turn("ASSISTANT", "penultimate answer"));
        thread.add(new ChatContextCompactor.Turn("USER", "RECENT_MARKER latest question"));

        String capsule = ChatContextCompactor.buildCapsule(thread, config);
        require(capsule.contains("GOAL_MARKER"), "the original goal must survive compaction");
        require(capsule.contains("RECENT_MARKER"), "the newest turn must survive verbatim");
        require(!capsule.contains("greeting before the goal"),
                "the pre-goal preamble must be dropped");
        require(capsule.contains("中间轮次摘要"), "the digest section must be labelled");
        require(capsule.contains("最近对话原文"), "the verbatim section must be labelled");
    }

    private static void reportsDroppedTurns() {
        ChatContextCompactor.Value config = new ChatContextCompactor.Value();
        config.capsuleChars = 600;
        config.keepRecentTurns = 2;
        String capsule = ChatContextCompactor.buildCapsule(conversation(80, 900), config);
        require(capsule.contains("已省略"),
                "a bounded digest must report how many turns were dropped");
    }

    private static void nestedCapsuleKeepsMoreOfTheGoal() {
        StringBuilder body = new StringBuilder(ChatContextCompactor.CAPSULE_HEADER);
        for (int i = 0; i < 700; i++) body.append('x');
        body.append("DEEP_MARKER");
        List<ChatContextCompactor.Turn> nested = new ArrayList<ChatContextCompactor.Turn>();
        nested.add(new ChatContextCompactor.Turn("USER", body.toString()));
        nested.add(new ChatContextCompactor.Turn("USER", "a later question"));

        ChatContextCompactor.Value config = new ChatContextCompactor.Value();
        config.capsuleChars = 2000;
        require(ChatContextCompactor.buildCapsule(nested, config).contains("DEEP_MARKER"),
                "an already-compacted goal must keep more of its text");

        List<ChatContextCompactor.Turn> plain = new ArrayList<ChatContextCompactor.Turn>();
        plain.add(new ChatContextCompactor.Turn("USER", body.substring(
                ChatContextCompactor.CAPSULE_HEADER.length())));
        plain.add(new ChatContextCompactor.Turn("USER", "a later question"));
        require(!ChatContextCompactor.buildCapsule(plain, config).contains("DEEP_MARKER"),
                "a first-time goal is capped by the ordinary goal budget");
    }

    private static void isDeterministic() {
        ChatContextCompactor.Value config = new ChatContextCompactor.Value();
        config.capsuleChars = 1600;
        List<ChatContextCompactor.Turn> thread = conversation(25, 120);
        require(ChatContextCompactor.buildCapsule(thread, config)
                        .equals(ChatContextCompactor.buildCapsule(thread, config)),
                "the same transcript must always render the same capsule");
    }

    private static void wrapsInjection() {
        require(ChatContextCompactor.wrapForInjection(null).length() == 0,
                "a missing capsule must not be injected");
        require(ChatContextCompactor.wrapForInjection("   ").length() == 0,
                "a blank capsule must not be injected");
        String wrapped = ChatContextCompactor.wrapForInjection("CAPSULE_BODY");
        require(wrapped.contains("<compacted_context>"), "the injection must be delimited");
        require(wrapped.endsWith("</compacted_context>"), "the injection must be closed");
        require(wrapped.contains("CAPSULE_BODY"), "the capsule body must be preserved");
    }

    private static void labelsAndTitles() {
        require(ChatContextCompactor.looksLikeCapsule(
                        ChatContextCompactor.CAPSULE_HEADER + "body"),
                "a capsule must be recognised by its header");
        require(!ChatContextCompactor.looksLikeCapsule("ordinary message"),
                "an ordinary message must not look like a capsule");
        require(!ChatContextCompactor.looksLikeCapsule(null),
                "a missing body must not look like a capsule");

        require("压缩对话".equals(ChatContextCompactor.capsuleTitle(null, "压缩对话")),
                "an untitled conversation must fall back to the default title");
        require("压缩对话".equals(ChatContextCompactor.capsuleTitle("新对话", "压缩对话")),
                "a blank host conversation must fall back to the default title");
        require("压缩对话 · 周报整理".equals(ChatContextCompactor.capsuleTitle("周报整理", "压缩对话")),
                "an ordinary title must be carried into the new conversation");
        require("Compacted chat · 周报整理".equals(
                        ChatContextCompactor.capsuleTitle("周报整理", "Compacted chat")),
                "the caller-supplied label must be used verbatim so the title can be localized");
        require(ChatContextCompactor.capsuleTitle(repeat('t', 200), "压缩对话").length() <= 32,
                "a long title must be truncated");
    }

    private static void truncationBoundaries() {
        require("".equals(ChatContextCompactor.truncate(null, 8)), "null must truncate to empty");
        require("".equals(ChatContextCompactor.truncate("abc", 0)), "a zero budget is empty");
        require("abc".equals(ChatContextCompactor.truncate("abc", 3)),
                "an exact-fit string must be returned unchanged");
        require("ab…".equals(ChatContextCompactor.truncate("abcd", 3)),
                "truncation must mark the cut without exceeding the budget");
        require("…".equals(ChatContextCompactor.truncate("abc", 1)),
                "a one-character budget is a bare ellipsis");
        require("a b c".equals(ChatContextCompactor.oneLine("a\n  b\t c", 100)),
                "a multi-line body must collapse to one line");
        require(ChatContextCompactor.oneLine("a\n  b\t c", 100).length() <= 100,
                "the collapsed line must respect the budget");
        // "ab" + U+1F600 + "cd": cutting at 4 would land between the surrogate halves.
        require("ab…".equals(ChatContextCompactor.truncate("ab\uD83D\uDE00cd", 4)),
                "truncation must not split a surrogate pair");
    }

    // ── Capsule store ──────────────────────────────────────────────────────────

    /** Files three records and reads them back, including astral-plane text. */
    private static void capsuleStoreRoundTrips() {
        wipe();
        String astral = "目标 \uD83D\uDE00 \uD83D\uDCA9 摘要";
        ChatContextCompactor.Capsule a =
                ChatContextCompactor.addCapsule("s1", "周报整理", "A " + astral);
        ChatContextCompactor.Capsule b = ChatContextCompactor.addCapsule("s2", "", "B");
        ChatContextCompactor.Capsule c = ChatContextCompactor.addCapsule("s3", "第三个", "C");
        require(a != null && b != null && c != null, "every fold must file a record");
        require(a.chars() == ("A " + astral).length(),
                "a record must report the length of its own text");

        List<ChatContextCompactor.Capsule> records = ChatContextCompactor.capsules();
        require(records.size() == 3, "all three records must survive: " + records.size());
        require(c.id.equals(records.get(0).id) && b.id.equals(records.get(1).id)
                        && a.id.equals(records.get(2).id),
                "records must read back newest first");
        ChatContextCompactor.Capsule reread = records.get(2);
        require(("A " + astral).equals(reread.text),
                "astral-plane text must survive the round trip");
        require(a.createdAt == reread.createdAt, "the timestamp must survive the round trip");
        require("s1".equals(reread.sourceSid) && "周报整理".equals(reread.sourceTitle),
                "the source must survive the round trip");
        require(!reread.delivered() && reread.targetSid.length() == 0,
                "a fresh record must be undelivered and unattached");
        require(ChatContextCompactor.findCapsule(a.id) != null
                        && ChatContextCompactor.findCapsule(a.id).text.equals("A " + astral),
                "a record must be findable by id");
        require(ChatContextCompactor.findCapsule("no-such-id") == null,
                "an unknown id must find nothing");
        require(ChatContextCompactor.findCapsule(null) == null, "a null id must find nothing");
        require(ChatContextCompactor.addCapsule("s1", "t", "   ") == null,
                "a blank capsule must not be filed");
    }

    /** The store is bounded, and it discards its oldest records first. */
    private static void capsuleStoreBoundsHistory() {
        wipe();
        List<String> ids = new ArrayList<String>();
        for (int i = 0; i < 25; i++) {
            ChatContextCompactor.Capsule added =
                    ChatContextCompactor.addCapsule("s" + i, "t" + i, "body " + i);
            require(added != null, "record " + i + " must be filed");
            ids.add(added.id);
        }
        List<ChatContextCompactor.Capsule> records = ChatContextCompactor.capsules();
        require(records.size() == 20, "the store must keep its bound: " + records.size());
        require(records.get(0).id.equals(ids.get(24)), "the newest record must be kept");
        require(records.get(19).id.equals(ids.get(5)), "the oldest kept record must be the sixth");
        for (int i = 0; i < 5; i++) {
            for (ChatContextCompactor.Capsule record : records) {
                require(!record.id.equals(ids.get(i)),
                        "the five oldest records must be the ones dropped");
            }
        }
    }

    /** A fold the user has not sent yet outlives an older fold that has already been delivered. */
    private static void capsuleStoreEvictsDeliveredFirst() {
        wipe();
        List<String> ids = new ArrayList<String>();
        for (int i = 0; i < 20; i++) {
            ids.add(ChatContextCompactor.addCapsule("s", "t", "b" + i).id);
        }
        require(ChatContextCompactor.attachCapsule(ids.get(0), "target"),
                "the oldest record must attach");
        require(ChatContextCompactor.claimCapsuleFor("target").length() > 0,
                "the oldest record must be deliverable");
        ChatContextCompactor.addCapsule("s", "t", "newest");

        List<ChatContextCompactor.Capsule> records = ChatContextCompactor.capsules();
        require(records.size() == 20, "the bound must hold after eviction: " + records.size());
        require(records.get(0).text.equals("newest"), "the newest record must survive");
        for (ChatContextCompactor.Capsule record : records) {
            require(!record.id.equals(ids.get(0)),
                    "the delivered record must be evicted before any undelivered one");
        }
        require(ChatContextCompactor.findCapsule(ids.get(1)) != null,
                "an undelivered record must survive eviction");
    }

    private static void capsuleDeliveryIsExactlyOnce() {
        wipe();
        ChatContextCompactor.Capsule record =
                ChatContextCompactor.addCapsule("src", "周报整理", "BODY_MARKER");
        require(ChatContextCompactor.attachCapsule(record.id, "t1"), "attach must succeed");
        String first = ChatContextCompactor.claimCapsuleFor("t1");
        require(first.contains("BODY_MARKER"), "the owed capsule must be delivered");
        require(first.contains("<compacted_context>") && first.endsWith("</compacted_context>"),
                "delivery must be wrapped for injection");
        require(ChatContextCompactor.claimCapsuleFor("t1").length() == 0,
                "a delivered capsule must not be sent a second time");
        require(ChatContextCompactor.claimCapsuleFor("other").length() == 0,
                "a conversation that is owed nothing must receive nothing");
        require(ChatContextCompactor.claimCapsuleFor("").length() == 0,
                "an empty conversation id must claim nothing");
        require(ChatContextCompactor.claimCapsuleFor(null).length() == 0,
                "a missing conversation id must claim nothing");
        require(ChatContextCompactor.findCapsule(record.id).delivered(),
                "delivery must be recorded on disk, not only in this process");
        require(!ChatContextCompactor.attachCapsule("no-such-id", "t1"),
                "attaching a record that does not exist must fail");
        require(!ChatContextCompactor.attachCapsule(record.id, ""),
                "attaching to no conversation must fail");
        require(!ChatContextCompactor.attachCapsule(null, "t1"),
                "attaching a null record must fail");
    }

    private static void oneCapsulePerConversation() {
        wipe();
        ChatContextCompactor.Capsule first =
                ChatContextCompactor.addCapsule("s", "t", "FIRST_BODY");
        ChatContextCompactor.Capsule second =
                ChatContextCompactor.addCapsule("s", "t", "SECOND_BODY");
        require(ChatContextCompactor.attachCapsule(first.id, "t1"), "the first attach must succeed");
        require(ChatContextCompactor.attachCapsule(second.id, "t1"),
                "the second attach must succeed");
        String injection = ChatContextCompactor.claimCapsuleFor("t1");
        require(injection.contains("SECOND_BODY"),
                "the most recently attached capsule must be the one delivered");
        require(!injection.contains("FIRST_BODY"),
                "re-attaching must replace the previous capsule instead of stacking two");
        require(!ChatContextCompactor.findCapsule(first.id).delivered(),
                "the replaced capsule must stay undelivered so it can be attached elsewhere");
        require(ChatContextCompactor.claimCapsuleFor("t1").length() == 0,
                "the replacement must still be delivered only once");

        // Re-attaching a capsule that was already delivered owes it again: the user carried the same
        // summary into another conversation on purpose.
        require(ChatContextCompactor.attachCapsule(second.id, "t2"),
                "re-attaching a delivered capsule must succeed");
        require(ChatContextCompactor.claimCapsuleFor("t2").contains("SECOND_BODY"),
                "a re-attached capsule must be delivered to its new target");
    }

    private static void capsulesAreEditableAndDeletable() {
        wipe();
        ChatContextCompactor.Capsule record =
                ChatContextCompactor.addCapsule("s", "t", "ORIGINAL");
        require(ChatContextCompactor.attachCapsule(record.id, "t1"), "attach must succeed");
        require(ChatContextCompactor.updateCapsuleText(record.id, "EDITED_BODY"),
                "editing a stored record must succeed");
        require(ChatContextCompactor.claimCapsuleFor("t1").contains("EDITED_BODY"),
                "an edit must take effect on a capsule that has not been sent yet");
        require("EDITED_BODY".equals(ChatContextCompactor.findCapsule(record.id).text),
                "the edit must reach the store, not just this process");
        require(!ChatContextCompactor.updateCapsuleText(record.id, "   "),
                "a blank edit must be rejected rather than emptying the record");
        require(!ChatContextCompactor.updateCapsuleText("no-such-id", "x"),
                "editing a missing record must fail");
        require("EDITED_BODY".equals(ChatContextCompactor.findCapsule(record.id).text),
                "a rejected edit must leave the record alone");

        ChatContextCompactor.Capsule second =
                ChatContextCompactor.addCapsule("s", "t", "SECOND");
        require(ChatContextCompactor.deleteCapsule(second.id), "deleting a record must succeed");
        require(ChatContextCompactor.findCapsule(second.id) == null,
                "a deleted record must be gone");
        require(!ChatContextCompactor.deleteCapsule(second.id), "deleting twice must fail");
        require(!ChatContextCompactor.deleteCapsule(null), "a null id must delete nothing");
        require(ChatContextCompactor.capsules().size() == 1,
                "only the surviving record must remain");
    }

    /**
     * The send path reads this store on every outgoing message, so a store that cannot be written has
     * to degrade to "nothing owed" instead of throwing into the caller.
     */
    private static void unwritableStoreNeverThrows() {
        wipe();
        String previous = System.getProperty(CAPSULE_FILE_PROPERTY);
        File scratch = new File(previous);
        File blocker = new File(scratch.getParentFile(), "blocker");
        writeText(blocker, "not a directory");
        System.setProperty(CAPSULE_FILE_PROPERTY,
                new File(blocker, "nested/capsules.json").getAbsolutePath());
        try {
            require(ChatContextCompactor.capsules().isEmpty(),
                    "an unreadable store must read as empty");
            require(ChatContextCompactor.claimCapsuleFor("t1").length() == 0,
                    "an unwritable store must owe nothing rather than throw");
            require(!ChatContextCompactor.attachCapsule("id", "t1"),
                    "attaching against an unwritable store must report failure");
            require(!ChatContextCompactor.updateCapsuleText("id", "x"),
                    "editing against an unwritable store must report failure");
            require(!ChatContextCompactor.deleteCapsule("id"),
                    "deleting against an unwritable store must report failure");
            ChatContextCompactor.Capsule added =
                    ChatContextCompactor.addCapsule("s", "t", "BODY");
            require(added != null && added.text.equals("BODY"),
                    "a fold in progress must still yield its capsule when the store cannot persist");
            require(ChatContextCompactor.findCapsule(added.id) == null,
                    "a record that could not be persisted must not be found");
        } finally {
            if (previous == null) System.clearProperty(CAPSULE_FILE_PROPERTY);
            else System.setProperty(CAPSULE_FILE_PROPERTY, previous);
        }
        require(ChatContextCompactor.capsules().isEmpty(),
                "the store must be empty again once the redirected path is restored");
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static void wipe() {
        File file = new File(System.getProperty(CAPSULE_FILE_PROPERTY));
        File temp = new File(file.getAbsolutePath() + ".tmp");
        if (temp.exists() && !temp.delete()) {
            throw new AssertionError("could not clear the scratch temp file: " + temp);
        }
        if (file.exists() && !file.delete()) {
            throw new AssertionError("could not clear the scratch store: " + file);
        }
    }

    private static void writeText(File file, String text) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                throw new AssertionError("could not create " + parent);
            }
            FileOutputStream out = new FileOutputStream(file);
            try {
                out.write(text.getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (AssertionError error) {
            throw error;
        } catch (Exception error) {
            throw new AssertionError("could not prepare the scratch file: " + error);
        }
    }

    private static List<ChatContextCompactor.Turn> conversation(int turns, int bodyChars) {
        List<ChatContextCompactor.Turn> thread = new ArrayList<ChatContextCompactor.Turn>();
        for (int i = 0; i < turns; i++) {
            thread.add(new ChatContextCompactor.Turn(i % 2 == 0 ? "USER" : "ASSISTANT",
                    "turn " + i + " " + repeat('x', bodyChars)));
        }
        return thread;
    }

    private static List<ChatContextCompactor.Turn> turns(String... roleBodyPairs) {
        List<ChatContextCompactor.Turn> thread = new ArrayList<ChatContextCompactor.Turn>();
        for (int i = 0; i + 1 < roleBodyPairs.length; i += 2) {
            thread.add(new ChatContextCompactor.Turn(roleBodyPairs[i], roleBodyPairs[i + 1]));
        }
        return thread;
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) builder.append(value);
        return builder.toString();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
