package com.dsmod.probe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Pure-JVM regression coverage for hidden heartbeat tool framing and strict JSON parsing. */
public final class HeartbeatToolProtocolRegressionTest {
    static final class FoldFragment {
        String a;
        String c;
        FoldFragment(String type, String content) { a = type; c = content; }
    }

    static final class FoldMessage {
        int f;
        Integer g;
        String h;
        Boolean u;
        List<FoldFragment> t;
        FoldMessage(int id, Integer parent, String role, String type, String content) {
            this(id, parent, role, type, content, null);
        }
        FoldMessage(int id, Integer parent, String role, String type, String content,
                    Boolean thinkingEnabled) {
            f = id; g = parent; h = role;
            u = thinkingEnabled;
            t = new ArrayList<>(Arrays.asList(new FoldFragment(type, content)));
        }
    }

    static final class FoldSession {
        Integer d;
        FoldSession(Integer current) { d = current; }
    }

    static final class FoldResponse {
        FoldSession a;
        List<FoldMessage> b;
        FoldResponse(FoldSession session, FoldMessage... messages) {
            a = session;
            b = new ArrayList<>(Arrays.asList(messages));
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int cursor = 0;
        while (value != null && needle != null && needle.length() > 0) {
            int found = value.indexOf(needle, cursor);
            if (found < 0) return count;
            count++;
            cursor = found + needle.length();
        }
        return count;
    }

    public static void main(String[] args) {
        String ordinary = "普通回答，不含任何本地控制块。";
        HeartbeatToolProtocol.Result plain = HeartbeatToolProtocol.parse(ordinary);
        require(ordinary.equals(plain.visibleText), "ordinary assistant text changed");
        require(plain.calls.isEmpty(), "ordinary text produced a tool call");

        String payload = HeartbeatToolProtocol.CONTROL_START + "\n"
                + "{\"calls\":["
                + "{\"id\":\"once_001\",\"tool\":\"schedule_once\","
                + "\"scope\":\"conversation-1234\","
                + "\"at\":\"2026-08-02T18:37:00+08:00\",\"instruction\":\"来找用户闲聊\"},"
                + "{\"id\":\"plan_001\",\"tool\":\"set_plan\","
                + "\"scope\":\"conversation-1234\","
                + "\"instruction\":\"每次主动分享一个轻松话题\"},"
                + "{\"id\":\"int_001\",\"tool\":\"set_interval\","
                + "\"scope\":\"conversation-1234\",\"minutes\":90},"
                + "{\"id\":\"bind_001\",\"tool\":\"bind_chat\","
                + "\"scope\":\"conversation-1234\"},"
                + "{\"id\":\"cancel_001\",\"tool\":\"cancel_heartbeat\","
                + "\"scope\":\"conversation-1234\",\"mode\":\"all_once\"}"
                + "]}\n" + HeartbeatToolProtocol.CONTROL_END;
        HeartbeatToolProtocol.Result parsed =
                HeartbeatToolProtocol.parse("已经替你安排好了。\n" + payload + "\n晚点见。");
        require(!parsed.visibleText.contains("DEEKSEEP_LOCAL_TOOLS"),
                "complete control markers leaked into visible text");
        require(!parsed.visibleText.contains("schedule_once"),
                "control JSON leaked into visible text");
        require(parsed.visibleText.contains("已经替你安排好了。")
                        && parsed.visibleText.contains("晚点见。"),
                "normal assistant text around a control block was damaged");
        require(parsed.calls.size() == 5, "valid calls were not parsed");
        require("2026-08-02T18:37:00+08:00".equals(parsed.calls.get(0).at),
                "absolute one-time heartbeat was not retained");
        require("conversation-1234".equals(parsed.calls.get(0).scope),
                "one-time heartbeat lost its source conversation binding");
        require(parsed.calls.get(2).minutes == 90,
                "heartbeat interval was not parsed");
        require("all_once".equals(parsed.calls.get(4).mode),
                "heartbeat cancellation mode was not parsed");

        Locale previousLocale = Locale.getDefault();
        TimeZone previousTimeZone = TimeZone.getDefault();
        HeartbeatToolProtocol.Result presented;
        try {
            Locale.setDefault(Locale.CHINA);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            presented = HeartbeatToolProtocol.parseForConversation(
                    "已经替你安排好了。\n" + payload + "\n晚点见。");
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousTimeZone);
        }
        require(HeartbeatToolProtocol.hasToolStatusStyleMarker(presented.visibleText),
                "conversation heartbeat status omitted its private style marker");
        require(!HeartbeatToolProtocol.isIsolatedToolStatusText(presented.visibleText),
                "mixed assistant response was mistaken for an isolated tool status");
        String isolatedStatus = null;
        for (String line : presented.visibleText.split("\n")) {
            if (line.startsWith("> ") && line.contains("\u8bbe\u7f6e\u5fc3\u8df3\uff1a")) {
                isolatedStatus = line.substring(2);
                break;
            }
        }
        require(HeartbeatToolProtocol.isIsolatedToolStatusText(isolatedStatus),
                "isolated heartbeat status was not recognized for styling");
        String presentedText =
                HeartbeatToolProtocol.stripToolStatusStyleMarkers(presented.visibleText);
        require(HeartbeatToolProtocol.isRegisteredToolStatusText(isolatedStatus),
                "generated heartbeat status was not registered for Markdown fallback");
        require(!HeartbeatToolProtocol.isRegisteredToolStatusText(
                        "\u6a21\u578b\u8bf4\u6211\u53ef\u4ee5\u8bbe\u7f6e\u5fc3\u8df3\uff0c\u4f46\u8fd9\u4e0d\u662f\u5de5\u5177\u72b6\u6001\u3002"),
                "ordinary model prose was mistaken for a registered tool status");
        require(presentedText.contains("> \u8bbe\u7f6e\u5fc3\u8df3\uff1a8\u67082\u65e5 18:37:00"),
                "one-time heartbeat did not use the compact time status");
        require(presentedText.contains("> \u8bbe\u7f6e\u5fc3\u8df3\uff1a\u6bcf90\u5206\u949f"),
                "heartbeat interval did not use the compact status");
        require(presentedText.contains(
                        "> \u53d6\u6d88\u5fc3\u8df3\uff1a\u5168\u90e8\u4e00\u6b21\u6027\u4efb\u52a1"),
                "heartbeat cancellation did not use the compact status");
        require(!presentedText.contains("\u25cc")
                        && !presentedText.contains("\u8c03\u7528\u4e86")
                        && !presentedText.contains("**"),
                "removed icon or agent-style wording remained in the status row");
        require(!HeartbeatToolProtocol.hasToolStatusStyleMarker(
                        HeartbeatToolProtocol.stripControlBlocks(
                                presented.visibleText)),
                "private style marker leaked through the model-context sanitizer");
        require((HeartbeatToolProtocol.TOOL_STATUS_GRAY_COLOR >>> 32)
                        == 0xFF8A8A8AL,
                "tool status color is not the configured neutral gray");
        float statusSp = Float.intBitsToFloat(
                (int) HeartbeatToolProtocol.TOOL_STATUS_FONT_SIZE);
        require(statusSp > 10.6f && statusSp < 10.8f,
                "tool status fallback font size is not two thirds of 16sp");
        require(HeartbeatToolProtocol.TOOL_STATUS_FONT_SCALE > 0.666f
                        && HeartbeatToolProtocol.TOOL_STATUS_FONT_SCALE < 0.667f,
                "tool status font scale is not exactly one third smaller");
        require(HeartbeatToolProtocol.explicitToolStatusStyleMask(0x3affc)
                        == (0x3affc
                        & ~HeartbeatToolProtocol.TOOL_STATUS_EXPLICIT_STYLE_MASK),
                "Compose default mask did not expose color and font size");
        require((HeartbeatToolProtocol.TOOL_STATUS_TEXT_STYLE_COPY_MASK & 0x3) == 0
                        && (HeartbeatToolProtocol.TOOL_STATUS_TEXT_STYLE_COPY_MASK
                        & 0x00FFFFFC) == 0x00FFFFFC,
                "TextStyle copy mask does not preserve non-color typography");
        require(!presented.visibleText.contains("schedule_once")
                        && !presented.visibleText.contains("DEEKSEEP_LOCAL_TOOLS")
                        && !presented.visibleText.contains("conversation-1234"),
                "private heartbeat protocol data leaked through the tool activity row");
        require(HeartbeatToolProtocol.stripControlBlocks(payload).length() == 0,
                "history/context sanitizer unexpectedly retained presentation rows");

        String incomplete = "先正常回复\n" + HeartbeatToolProtocol.CONTROL_START
                + "\n{\"calls\":[";
        HeartbeatToolProtocol.Result streaming =
                HeartbeatToolProtocol.parse(incomplete);
        require("先正常回复\n".equals(streaming.visibleText),
                "incomplete streamed JSON was not fully hidden");
        require(streaming.incompleteControlBlock,
                "incomplete control block was not reported");
        require(streaming.calls.isEmpty(),
                "incomplete JSON produced a tool call");
        require(!HeartbeatToolProtocol.parseForConversation(incomplete).visibleText
                        .contains("\u8bbe\u7f6e\u5fc3\u8df3"),
                "incomplete streamed tool call displayed a premature activity row");

        String firstSingleCall = HeartbeatToolProtocol.CONTROL_START + "\n"
                + "{\"call\":{\"id\":\"stream_plan\",\"tool\":\"set_plan\","
                + "\"scope\":\"conversation-1234\","
                + "\"instruction\":\"每次主动分享一个轻松话题\"}}\n"
                + HeartbeatToolProtocol.CONTROL_END;
        String secondSingleCall = HeartbeatToolProtocol.CONTROL_START + "\n"
                + "{\"call\":{\"id\":\"stream_interval\",\"tool\":\"set_interval\","
                + "\"scope\":\"conversation-1234\",\"minutes\":90}}\n"
                + HeartbeatToolProtocol.CONTROL_END;
        String thirdSingleCall = HeartbeatToolProtocol.CONTROL_START + "\n"
                + "{\"call\":{\"id\":\"stream_bind\",\"tool\":\"bind_chat\","
                + "\"scope\":\"conversation-1234\"}}\n"
                + HeartbeatToolProtocol.CONTROL_END;
        String nextCallPrefix = HeartbeatToolProtocol.CONTROL_START
                + "\n{\"call\":{\"id\":\"stream_interval\"";
        HeartbeatToolProtocol.Result firstStreamingFrame =
                HeartbeatToolProtocol.parseForConversation(
                        "正在处理。\n" + firstSingleCall + "\n" + nextCallPrefix);
        require(firstStreamingFrame.calls.size() == 1
                        && "stream_plan".equals(firstStreamingFrame.calls.get(0).id),
                "the first singular call was not available before the next call completed");
        require(firstStreamingFrame.incompleteControlBlock,
                "the unfinished second singular call was not reported");
        require(occurrences(firstStreamingFrame.visibleText, "> ") == 1,
                "the first completed call did not render immediately and independently");

        String thirdCallPrefix = HeartbeatToolProtocol.CONTROL_START
                + "\n{\"call\":{\"id\":\"stream_bind\"";
        HeartbeatToolProtocol.Result secondStreamingFrame =
                HeartbeatToolProtocol.parseForConversation(
                        "正在处理。\n" + firstSingleCall + "\n"
                                + secondSingleCall + "\n" + thirdCallPrefix);
        require(secondStreamingFrame.calls.size() == 2
                        && "stream_plan".equals(secondStreamingFrame.calls.get(0).id)
                        && "stream_interval".equals(secondStreamingFrame.calls.get(1).id),
                "sequential singular calls were not parsed in emission order");
        require(secondStreamingFrame.incompleteControlBlock,
                "the unfinished third singular call was not reported");
        require(occurrences(secondStreamingFrame.visibleText, "> ") == 2,
                "the second completed call waited for the remaining calls");

        HeartbeatToolProtocol.Result finalStreamingFrame =
                HeartbeatToolProtocol.parseForConversation(
                        "正在处理。\n" + firstSingleCall + "\n"
                                + secondSingleCall + "\n" + thirdSingleCall);
        require(finalStreamingFrame.calls.size() == 3
                        && "stream_bind".equals(finalStreamingFrame.calls.get(2).id),
                "the final singular call was not parsed");
        require(!finalStreamingFrame.incompleteControlBlock
                        && occurrences(finalStreamingFrame.visibleText, "> ") == 3,
                "independent call rows were not retained after the stream completed");

        String partialMarker = "先等等\n[[DEEKSEEP_LOC";
        require("先等等\n".equals(
                        HeartbeatToolProtocol.stripControlBlocks(partialMarker)),
                "partial opening marker flashed into visible output");

        String malformed = HeartbeatToolProtocol.CONTROL_START
                + "\nnot-json\n" + HeartbeatToolProtocol.CONTROL_END;
        HeartbeatToolProtocol.Result bad = HeartbeatToolProtocol.parse(malformed);
        require(bad.visibleText.length() == 0,
                "malformed control payload was not hidden");
        require(bad.calls.isEmpty(),
                "malformed control payload was executed");
        require(!HeartbeatToolProtocol.renderConversationToolRows(malformed)
                        .contains("\u8bbe\u7f6e\u5fc3\u8df3"),
                "malformed control payload displayed a tool activity row");

        String globalCall = HeartbeatToolProtocol.CONTROL_START
                + "\n{\"calls\":[{\"id\":\"once_002\",\"tool\":\"schedule_once\","
                + "\"at\":\"2026-08-02T18:37:00+08:00\","
                + "\"instruction\":\"没有 scope 的任务\"}]}\n"
                + HeartbeatToolProtocol.CONTROL_END;
        require(HeartbeatToolProtocol.parse(globalCall).calls.isEmpty(),
                "an unscoped/global heartbeat tool call was accepted");

        String echoedEvent = "前文"
                + HeartbeatToolProtocol.EVENT_START
                + "\ntype=heartbeat\ninstruction=内部约定\n"
                + HeartbeatToolProtocol.EVENT_END
                + "后文";
        require("前文后文".equals(
                        HeartbeatToolProtocol.stripControlBlocks(echoedEvent)),
                "an echoed anonymous heartbeat event leaked into assistant text");

        String partialEvent = "问候\n[[DEEKSEEP_ANONYMOUS_HEART";
        require("问候\n".equals(
                        HeartbeatToolProtocol.stripControlBlocks(partialEvent)),
                "partial event marker flashed into visible output");

        String prompt = HeartbeatToolProtocol.systemPrompt(
                1785247200000L, "找用户闲聊", 180, "conversation-1234");
        require(prompt.contains(HeartbeatToolProtocol.CONTROL_START)
                        && prompt.contains(HeartbeatToolProtocol.EVENT_START)
                        && prompt.contains("schedule_once")
                        && prompt.contains("bind_chat")
                        && prompt.contains("cancel_heartbeat")
                        && prompt.contains("180 分钟")
                        && prompt.contains("\"scope\":\"conversation-1234\""),
                "default heartbeat system prompt omitted the local tool contract");
        require(prompt.contains("每个工具调用必须单独使用一组完整控制块")
                        && prompt.contains("{\"call\":")
                        && prompt.contains("写完一个 call 后立刻写结束标记")
                        && !prompt.contains("{\"calls\":["),
                "default heartbeat prompt still permits delayed multi-call batches");

        try {
            FoldMessage visibleUser = new FoldMessage(
                    10, null, "USER", "REQUEST", "正常消息");
            FoldMessage internalEvent = new FoldMessage(
                    11, 10, "USER", "REQUEST",
                    "<system>\nprivate\n</system>\n\n"
                            + HeartbeatToolProtocol.EVENT_START + "\ntype=heartbeat\n"
                            + HeartbeatToolProtocol.EVENT_END);
            FoldMessage proactiveReply = new FoldMessage(
                    12, 11, "ASSISTANT", "RESPONSE", "该喝水啦");
            FoldResponse history = new FoldResponse(
                    new FoldSession(12), visibleUser, internalEvent, proactiveReply);
            Method fold = Main.class.getDeclaredMethod(
                    "foldProactiveHeartbeatHistory", Object.class);
            fold.setAccessible(true);
            int count = ((Number) fold.invoke(null, history)).intValue();
            require(count == 1 && history.b.size() == 2,
                    "anonymous heartbeat request was not removed from history");
            require(history.b.get(1) == proactiveReply
                            && Integer.valueOf(10).equals(proactiveReply.g),
                    "proactive assistant reply was not reparented to the visible chat head");
            require(Integer.valueOf(12).equals(history.a.d),
                    "assistant response stopped being the visible conversation head");

            FoldMessage ordinaryInjectedUser = new FoldMessage(
                    20, null, "USER", "REQUEST",
                    "<system>\n以后若收到以 " + HeartbeatToolProtocol.EVENT_START
                            + " 开头的消息再处理\n</system>\n\n两分钟后提醒我");
            FoldResponse ordinaryHistory = new FoldResponse(
                    new FoldSession(20), ordinaryInjectedUser);
            int ordinaryCount =
                    ((Number) fold.invoke(null, ordinaryHistory)).intValue();
            require(ordinaryCount == 0 && ordinaryHistory.b.size() == 1
                            && ordinaryHistory.b.get(0) == ordinaryInjectedUser,
                    "a normal user message containing the documented marker was folded");

            Method reasoning = Main.class.getDeclaredMethod(
                    "nativeHistoryReasoning", List.class, Integer.class);
            reasoning.setAccessible(true);
            FoldMessage thinkingUser = new FoldMessage(
                    30, null, "USER", "REQUEST", "开启深度思考的请求", Boolean.TRUE);
            FoldMessage thinkingReply = new FoldMessage(
                    31, 30, "ASSISTANT", "RESPONSE", "回答");
            require(Boolean.TRUE.equals(reasoning.invoke(
                            null, Arrays.asList(thinkingUser, thinkingReply),
                            Integer.valueOf(31))),
                    "heartbeat did not inherit enabled deep thinking from the visible branch");

            FoldMessage normalUser = new FoldMessage(
                    40, null, "USER", "REQUEST", "普通请求", Boolean.FALSE);
            FoldMessage normalReply = new FoldMessage(
                    41, 40, "ASSISTANT", "RESPONSE", "回答");
            require(Boolean.FALSE.equals(reasoning.invoke(
                            null, Arrays.asList(normalUser, normalReply),
                            Integer.valueOf(41))),
                    "heartbeat enabled deep thinking for a conversation where it was disabled");

            Method normalizeModel = Main.class.getDeclaredMethod(
                    "normalizeNativeHeartbeatModel", String.class);
            normalizeModel.setAccessible(true);
            require("default".equals(normalizeModel.invoke(null, "SYSTEM")),
                    "session title_type leaked into heartbeat model_type");
            require("default".equals(normalizeModel.invoke(null, "DEFAULT"))
                            && "expert".equals(normalizeModel.invoke(null, "EXPERT"))
                            && "vision".equals(normalizeModel.invoke(null, "vision")),
                    "valid DeepSeek heartbeat model types were not normalized");
        } catch (Throwable error) {
            throw new AssertionError("proactive history folding failed", error);
        }

        long now = 1785240000000L;
        long absolute = Main.parseHeartbeatToolTime(
                "2026-08-02T18:37:00+08:00", now);
        require(absolute > now,
                "valid absolute one-time heartbeat timestamp was rejected");
        require(Main.parseHeartbeatToolTime(
                        "2026-07-01T18:37:00+08:00", now) == 0L,
                "past one-time heartbeat timestamp was accepted");
        require(Main.parseHeartbeatToolTime("five days later", now) == 0L,
                "non-absolute timestamp was accepted");

        try {
            Method decode = ProactiveHeartbeatReceiver.class.getDeclaredMethod(
                    "decodeStoredTask", String.class);
            decode.setAccessible(true);
            Object stored = decode.invoke(null,
                    "v2\n1785667020000\nheartbeat\nconversation-1234\n"
                            + "按先前约定来聊天");
            require(stored != null, "conversation-bound task state was not decoded");
            Field scopeField = stored.getClass().getDeclaredField("conversationId");
            Field textField = stored.getClass().getDeclaredField("taskText");
            scopeField.setAccessible(true);
            textField.setAccessible(true);
            require("conversation-1234".equals(scopeField.get(stored)),
                    "persisted one-time task lost its conversation binding");
            require("按先前约定来聊天".equals(textField.get(stored)),
                    "persisted one-time task lost its instruction");

            Object legacy = decode.invoke(null,
                    "1785667020000\n旧提醒第一行\n旧提醒第二行");
            require(legacy != null && "旧提醒第一行\n旧提醒第二行"
                            .equals(textField.get(legacy)),
                    "legacy multiline reminder migration dropped its first line");
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("task-state migration test failed", error);
        }

        System.out.println("PASS: hidden heartbeat tool protocol is strict and stream-safe");
    }
}
