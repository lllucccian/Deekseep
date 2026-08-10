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

    private static int timestampedToolRows(String value) {
        int count = 0;
        if (value == null) return count;
        for (String line : value.split("\n")) {
            if (line.matches("> \\d{2}:\\d{2}:\\d{2}  .+")) count++;
        }
        return count;
    }

    private static String singleCallBlock(String jsonObject) {
        return HeartbeatToolProtocol.CONTROL_START + "\n"
                + "{\"call\":" + jsonObject + "}\n"
                + HeartbeatToolProtocol.CONTROL_END;
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
                        && !parsed.visibleText.contains("晚点见。"),
                "text after a tool call was not held until the real result returned");
        require(parsed.calls.size() == 1,
                "one response accepted more than one Agent tool call");
        require("2026-08-02T18:37:00+08:00".equals(parsed.calls.get(0).at),
                "absolute one-time heartbeat was not retained");
        require("conversation-1234".equals(parsed.calls.get(0).scope),
                "one-time heartbeat lost its source conversation binding");

        String markdownEscaped = singleCallBlock(
                "{\"id\":\"escaped_001\",\"tool\":\"get_current_time\","
                        + "\"scope\":\"conversation-1234\"}")
                .replace("DEEKSEEP_LOCAL_TOOLS_V1",
                        "DEEKSEEP\\_LOCAL\\_TOOLS\\_V1");
        HeartbeatToolProtocol.Result escapedParsed =
                HeartbeatToolProtocol.parseForConversation(markdownEscaped);
        require(escapedParsed.calls.size() == 1
                        && "escaped_001".equals(escapedParsed.calls.get(0).id),
                "Markdown-escaped exact control marker was not recognized");
        require(!escapedParsed.visibleText.contains("DEEKSEEP")
                        && !escapedParsed.visibleText.contains("get_current_time"),
                "Markdown-escaped private control text leaked into the conversation");

        String fencedPayload = HeartbeatToolProtocol.CONTROL_START + "\n"
                + "```json\n{\"call\":{\"id\":\"fenced_001\","
                + "\"tool\":\"get_current_time\","
                + "\"scope\":\"conversation-1234\"}}\n```\n"
                + HeartbeatToolProtocol.CONTROL_END;
        HeartbeatToolProtocol.Result fencedParsed =
                HeartbeatToolProtocol.parseForConversation(
                        "```json\n" + fencedPayload + "\n```\n伪造的完成说明");
        require(fencedParsed.calls.size() == 1
                        && "fenced_001".equals(fencedParsed.calls.get(0).id),
                "a JSON-fenced payload inside the exact control markers was not recovered");
        require(!fencedParsed.visibleText.contains("```")
                        && !fencedParsed.visibleText.contains("伪造的完成说明"),
                "a wrapping Markdown fence or premature post-call text leaked to the user");

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
            if (line.startsWith("> ") && line.contains("\u8bbe\u7f6e\u5fc3\u8df3 to ")) {
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
        require(presentedText.contains("\u8bbe\u7f6e\u5fc3\u8df3 to 8\u67082\u65e5 18:37:00"),
                "one-time heartbeat did not clearly label its trigger time");
        require(!presentedText.contains("\u6bcf90\u5206\u949f")
                        && !presentedText.contains("\u53d6\u6d88\u5fc3\u8df3"),
                "later calls from a multi-tool batch leaked into the single-step UI");
        require(timestampedToolRows(presentedText) == 1,
                "single-step heartbeat response rendered more than one activity row");
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
        String incompleteDiagnostic = HeartbeatToolProtocol.stripToolStatusStyleMarkers(
                HeartbeatToolProtocol.renderConversationToolRows(incomplete));
        require(incompleteDiagnostic.contains("工具调用不完整，未执行")
                        || incompleteDiagnostic.contains("Incomplete tool call; not run"),
                "final static rendering did not explain an interrupted tool call");
        String unbackedDiagnostic = HeartbeatToolProtocol.stripToolStatusStyleMarkers(
                HeartbeatToolProtocol.renderConversationToolRows("已经打开微信了。"));
        require(unbackedDiagnostic.contains("未检测到完整工具调用，操作未执行")
                        || unbackedDiagnostic.contains("No complete tool call detected"),
                "an unbacked completion claim was not diagnosed honestly");

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
        require(!firstStreamingFrame.incompleteControlBlock,
                "content after the first call kept the accepted step artificially open");
        require(occurrences(firstStreamingFrame.visibleText, "> ") == 1,
                "the first completed call did not render immediately and independently");

        String thirdCallPrefix = HeartbeatToolProtocol.CONTROL_START
                + "\n{\"call\":{\"id\":\"stream_bind\"";
        HeartbeatToolProtocol.Result secondStreamingFrame =
                HeartbeatToolProtocol.parseForConversation(
                        "正在处理。\n" + firstSingleCall + "\n"
                                + secondSingleCall + "\n" + thirdCallPrefix);
        require(secondStreamingFrame.calls.size() == 1
                        && "stream_plan".equals(secondStreamingFrame.calls.get(0).id),
                "a second tool call escaped the single-step response gate");
        require(!secondStreamingFrame.incompleteControlBlock,
                "content after the accepted tool call affected stream state");
        require(occurrences(secondStreamingFrame.visibleText, "> ") == 1,
                "a later tool call rendered before the first result returned");

        HeartbeatToolProtocol.Result finalStreamingFrame =
                HeartbeatToolProtocol.parseForConversation(
                        "正在处理。\n" + firstSingleCall + "\n"
                                + secondSingleCall + "\n" + thirdSingleCall);
        require(finalStreamingFrame.calls.size() == 1
                        && "stream_plan".equals(finalStreamingFrame.calls.get(0).id),
                "multiple complete tool blocks bypassed the single-step gate");
        require(!finalStreamingFrame.incompleteControlBlock
                        && occurrences(finalStreamingFrame.visibleText, "> ") == 1,
                "later call rows remained after the stream completed");

        String[] agentBlocks = new String[]{
                singleCallBlock("{\"id\":\"time_001\",\"tool\":\"get_current_time\","
                        + "\"scope\":\"conversation-1234\"}"),
                singleCallBlock("{\"id\":\"screen_001\",\"tool\":\"capture_screen\","
                        + "\"scope\":\"conversation-1234\"}"),
                singleCallBlock("{\"id\":\"tap_001\",\"tool\":\"tap_screen\","
                        + "\"scope\":\"conversation-1234\",\"x\":250,\"y\":750}"),
                singleCallBlock("{\"id\":\"swipe_001\",\"tool\":\"swipe_screen\","
                        + "\"scope\":\"conversation-1234\",\"x\":500,\"y\":800,"
                        + "\"to_x\":500,\"to_y\":200,\"duration_ms\":420}"),
                singleCallBlock("{\"id\":\"back_001\",\"tool\":\"press_back\","
                        + "\"scope\":\"conversation-1234\"}"),
                singleCallBlock("{\"id\":\"read_001\",\"tool\":\"read_file\","
                        + "\"scope\":\"conversation-1234\","
                        + "\"path\":\"/data/local/tmp/agent-demo.txt\","
                        + "\"offset\":12,\"max_bytes\":4096}"),
                singleCallBlock("{\"id\":\"write_001\",\"tool\":\"write_file\","
                        + "\"scope\":\"conversation-1234\","
                        + "\"path\":\"/data/local/tmp/agent-demo.txt\","
                        + "\"content\":\"hello 世界\",\"mode\":\"append\","
                        + "\"create_parents\":true}"),
                singleCallBlock("{\"id\":\"shell_001\",\"tool\":\"shell\","
                        + "\"scope\":\"conversation-1234\","
                        + "\"command\":\"which cp && id\",\"timeout_ms\":7000}"),
                singleCallBlock("{\"id\":\"delay_001\",\"tool\":\"delay\","
                        + "\"scope\":\"conversation-1234\",\"duration_ms\":5000}"),
                singleCallBlock("{\"id\":\"open_001\",\"tool\":\"open_app\","
                        + "\"scope\":\"conversation-1234\","
                        + "\"package\":\"com.tencent.mm\"}"),
                singleCallBlock("{\"id\":\"power_001\",\"tool\":\"screen_power\","
                        + "\"scope\":\"conversation-1234\",\"mode\":\"sleep\"}"),
                singleCallBlock("{\"id\":\"music_001\",\"tool\":\"music\","
                        + "\"scope\":\"conversation-1234\",\"action\":\"search\","
                        + "\"provider\":\"qq\",\"query\":\"夜曲 周杰伦\"}"),
                singleCallBlock("{\"id\":\"music_local_001\",\"tool\":\"music\","
                        + "\"scope\":\"conversation-1234\",\"action\":\"play\","
                        + "\"source\":\"local\","
                        + "\"path\":\"/storage/emulated/0/Music/demo.flac\"}")
        };
        ArrayList<HeartbeatToolProtocol.ToolCall> agentCalls = new ArrayList<>();
        StringBuilder agentVisible = new StringBuilder();
        String screenshotStatus = null;
        previousLocale = Locale.getDefault();
        previousTimeZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.CHINA);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            for (String block : agentBlocks) {
                HeartbeatToolProtocol.Result one =
                        HeartbeatToolProtocol.parseForConversation(block);
                require(one.calls.size() == 1,
                        "a singular basic Agent call was not parsed: " + block);
                agentCalls.add(one.calls.get(0));
                agentVisible.append(one.visibleText).append('\n');
                if (HeartbeatToolProtocol.TOOL_CAPTURE_SCREEN.equals(
                        one.calls.get(0).tool)) {
                    for (String line : one.visibleText.split("\n")) {
                        if (line.startsWith("> ")) {
                            screenshotStatus = line.substring(2);
                            break;
                        }
                    }
                }
            }
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousTimeZone);
        }
        require(agentCalls.size() == 13,
                "valid basic Agent calls were not parsed");
        require(agentCalls.get(2).x == 250
                        && agentCalls.get(2).y == 750,
                "tap coordinates were not retained");
        require(agentCalls.get(3).toX == 500
                        && agentCalls.get(3).toY == 200
                        && agentCalls.get(3).durationMs == 420,
                "swipe geometry or duration was not retained");
        require("/data/local/tmp/agent-demo.txt".equals(
                        agentCalls.get(5).path)
                        && agentCalls.get(5).offset == 12L
                        && agentCalls.get(5).maxBytes == 4096,
                "read_file path, offset, or byte limit was not retained");
        require(agentCalls.get(6).append
                        && agentCalls.get(6).createParents
                        && "hello 世界".equals(agentCalls.get(6).content),
                "write_file content or mode was not retained");
        require("which cp && id".equals(agentCalls.get(7).command)
                        && agentCalls.get(7).timeoutMs == 7000,
                "shell command or timeout was not retained");
        require(agentCalls.get(8).durationMs == 5000,
                "delay duration was not retained with millisecond precision");
        require("com.tencent.mm".equals(agentCalls.get(9).targetId),
                "open_app package was not retained");
        require("sleep".equals(agentCalls.get(10).mode),
                "screen_power mode was not retained");
        require("search".equals(agentCalls.get(11).mode)
                        && "qq".equals(agentCalls.get(11).targetId)
                        && "夜曲 周杰伦".equals(agentCalls.get(11).instruction),
                "music action, provider, or query was not retained");
        require("play".equals(agentCalls.get(12).mode)
                        && "local".equals(agentCalls.get(12).targetId)
                        && "/storage/emulated/0/Music/demo.flac".equals(
                        agentCalls.get(12).path),
                "local music source or path was not retained");
        String agentText = HeartbeatToolProtocol.stripToolStatusStyleMarkers(
                agentVisible.toString());
        require(agentText.contains("\u83b7\u53d6\u5f53\u524d\u65f6\u95f4\uff1a")
                        && agentText.contains("\u83b7\u53d6\u622a\u56fe")
                        && agentText.contains("\u70b9\u51fb\u5c4f\u5e55\uff1ax=250, y=750")
                        && agentText.contains("\u6ed1\u52a8\u5c4f\u5e55\uff1a(500,800)")
                        && agentText.contains("\u8fd4\u56de\u4e0a\u4e00\u5c42")
                        && agentText.contains("\u8bfb\u53d6\u6587\u4ef6\uff1a")
                        && agentText.contains("\u8ffd\u52a0\u6587\u4ef6\uff1a")
                        && agentText.contains("Shell\uff1awhich cp && id")
                        && agentText.contains("延迟执行\uff1a5秒")
                        && agentText.contains("打开应用\uff1acom.tencent.mm")
                        && agentText.contains("熄灭屏幕")
                        && agentText.contains("音乐：夜曲 周杰伦")
                        && agentText.contains("demo.flac"),
                "basic Agent calls did not receive compact activity rows");
        require(timestampedToolRows(agentText) == 13,
                "basic Agent activity rows do not all start with invocation times");
        require(HeartbeatToolProtocol.isRegisteredToolStatusText(screenshotStatus),
                "non-heartbeat Agent status was not registered for gray small-text styling");

        StringBuilder illegalMultiTool = new StringBuilder("开始\n");
        for (String block : agentBlocks) {
            illegalMultiTool.append(block).append('\n');
        }
        illegalMultiTool.append("已经全部完成");
        HeartbeatToolProtocol.Result gatedAgent =
                HeartbeatToolProtocol.parseForConversation(
                        illegalMultiTool.toString());
        require(gatedAgent.calls.size() == 1
                        && HeartbeatToolProtocol.TOOL_GET_CURRENT_TIME.equals(
                        gatedAgent.calls.get(0).tool)
                        && timestampedToolRows(
                        HeartbeatToolProtocol.stripToolStatusStyleMarkers(
                                gatedAgent.visibleText)) == 1
                        && !gatedAgent.visibleText.contains("已经全部完成"),
                "multi-tool output or premature final text bypassed the single-step gate");

        String askUser = singleCallBlock(
                "{\"id\":\"ask_001\",\"tool\":\"ask_user\","
                        + "\"scope\":\"conversation-1234\",\"questions\":["
                        + "{\"question\":\"你希望先做哪一部分？\","
                        + "\"options\":[\"先完成界面\",\"先完成执行器\",\"先写测试\"]},"
                        + "{\"question\":\"动效强度怎么选？\","
                        + "\"options\":[\"弱\",\"标准\",\"稍强\"]}]}");
        HeartbeatToolProtocol.Result askParsed;
        previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.CHINA);
            askParsed = HeartbeatToolProtocol.parseForConversation(askUser);
        } finally {
            Locale.setDefault(previousLocale);
        }
        require(askParsed.calls.size() == 1
                        && HeartbeatToolProtocol.TOOL_ASK_USER.equals(
                        askParsed.calls.get(0).tool),
                "ask_user call was not parsed");
        require(askParsed.calls.get(0).questions.size() == 2
                        && askParsed.calls.get(0).questions.get(0).options.size() == 3
                        && "标准".equals(
                        askParsed.calls.get(0).questions.get(1).options.get(1)),
                "ask_user questions or options were not retained");
        String askStatus = HeartbeatToolProtocol.stripToolStatusStyleMarkers(
                askParsed.visibleText);
        require(askStatus.contains("询问用户：你希望先做哪一部分？")
                        && timestampedToolRows(askStatus) == 1,
                "ask_user did not render one immediate compact activity row");
        String visibleAnswer = AgentQuestionUi.buildVisibleAnswer(
                askParsed.calls.get(0).questions,
                new String[]{"先完成执行器", "标准"}, "");
        require(("Question：你希望先做哪一部分？\n"
                        + "Answer：先完成执行器\n\n"
                        + "Question：动效强度怎么选？\n"
                        + "Answer：标准").equals(visibleAnswer),
                "ask_user answer was not converted to visible Question/Answer text");
        String customAnswer = AgentQuestionUi.buildVisibleAnswer(
                askParsed.calls.get(0).questions,
                new String[]{"先写测试", null}, "第二题我想先实际体验再决定");
        require(customAnswer.contains("Answer：先写测试")
                        && customAnswer.endsWith(
                        "Answer：第二题我想先实际体验再决定"),
                "free-form answer did not fill an unanswered question");
        String invalidAsk = singleCallBlock(
                "{\"id\":\"ask_bad\",\"tool\":\"ask_user\","
                        + "\"scope\":\"conversation-1234\",\"questions\":["
                        + "{\"question\":\"无效问题\",\"options\":[\"只有一个\"]}]}");
        require(HeartbeatToolProtocol.parse(invalidAsk).calls.isEmpty(),
                "ask_user accepted fewer than two answer options");

        String compactAsk = singleCallBlock(
                "{\"id\":\"ask_compact\",\"tool\":\"ask_user\","
                        + "\"scope\":\"conversation-1234\","
                        + "\"question\":\"选择下一步\",\"options\":["
                        + "{\"label\":\"先改界面\",\"description\":\"完善交互\"},"
                        + "{\"text\":\"先修执行器\"}]}");
        HeartbeatToolProtocol.Result compactAskParsed =
                HeartbeatToolProtocol.parse(compactAsk);
        require(compactAskParsed.calls.size() == 1
                        && compactAskParsed.calls.get(0).questions.size() == 1
                        && compactAskParsed.calls.get(0).questions.get(0).options.size() == 2
                        && "先改界面".equals(compactAskParsed.calls.get(0)
                        .questions.get(0).options.get(0))
                        && "先修执行器".equals(compactAskParsed.calls.get(0)
                        .questions.get(0).options.get(1)),
                "compact ask_user schema or object options were not normalized");

        AgentToolConfig.Snapshot defaults = AgentToolConfig.defaults();
        require(defaults.enabled
                        && defaults.promptStrength
                        == AgentToolConfig.PROMPT_STRENGTH_BASIC
                        && AgentToolConfig.PERMISSION_ALL.equals(
                        defaults.permission)
                        && defaults.enabledTools.containsAll(
                        AgentToolConfig.tools()),
                "Agent defaults are not enabled/all-allowed with every tool on");
        AgentToolConfig.Snapshot roundTrip = AgentToolConfig.decode(
                AgentToolConfig.encode(defaults
                        .withBackend(AgentToolConfig.BACKEND_ROOT)
                        .withPromptStrength(
                                AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE)
                        .withTool(HeartbeatToolProtocol.TOOL_TAP_SCREEN, false)));
        require(AgentToolConfig.BACKEND_ROOT.equals(roundTrip.backend)
                        && roundTrip.promptStrength
                        == AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE
                        && !roundTrip.enabledTools.contains(
                        HeartbeatToolProtocol.TOOL_TAP_SCREEN)
                        && roundTrip.enabledTools.contains(
                        HeartbeatToolProtocol.TOOL_ASK_USER),
                "Agent settings JSON round-trip changed backend or tool toggles");
        AgentToolConfig.Snapshot migrated = AgentToolConfig.decode(
                "{\"version\":1,\"enabled\":true,\"backend\":\"in_app\","
                        + "\"permission\":\"all\",\"enabled_tools\":[\"ask_user\"]}");
        require(migrated.enabledTools.contains(HeartbeatToolProtocol.TOOL_READ_FILE)
                        && migrated.enabledTools.contains(
                        HeartbeatToolProtocol.TOOL_WRITE_FILE)
                        && migrated.enabledTools.contains(
                        HeartbeatToolProtocol.TOOL_SHELL),
                "v1 Agent settings did not enable newly added file and shell tools");
        require(AgentToolConfig.decode(
                        "{\"version\":4,\"enabled\":true,"
                                + "\"prompt_strength\":99}").promptStrength
                        == AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE
                        && AgentToolConfig.decode(
                        "{\"version\":4,\"enabled\":true,"
                                + "\"prompt_strength\":-8}").promptStrength
                        == AgentToolConfig.PROMPT_STRENGTH_BASIC,
                "out-of-range prompt intensity was not clamped");

        require("'a'\\''b'".equals(AgentDeviceBridge.shellQuote("a'b")),
                "shell path quoting did not escape a single quote safely");

        String invalidTap = singleCallBlock(
                "{\"id\":\"tap_bad\",\"tool\":\"tap_screen\","
                        + "\"scope\":\"conversation-1234\",\"x\":1001,\"y\":500}");
        HeartbeatToolProtocol.Result rejectedTap =
                HeartbeatToolProtocol.parse(invalidTap);
        require(rejectedTap.calls.isEmpty(),
                "out-of-range normalized tap coordinates were accepted");
        require(rejectedTap.rejectedCalls.size() == 1
                        && HeartbeatToolProtocol.TOOL_TAP_SCREEN.equals(
                        rejectedTap.rejectedCalls.get(0).call.tool),
                "a recognized invalid call did not retain safe correction metadata");
        require(HeartbeatToolProtocol.parseForConversation(invalidTap)
                        .visibleText.length() > 0,
                "a recognized invalid call was still silently hidden from the user");
        require(HeartbeatToolProtocol.parse(singleCallBlock(
                        "{\"id\":\"read_bad\",\"tool\":\"read_file\","
                                + "\"scope\":\"conversation-1234\","
                                + "\"path\":\"relative.txt\"}")).calls.isEmpty(),
                "read_file accepted a relative path");
        require(HeartbeatToolProtocol.parse(singleCallBlock(
                        "{\"id\":\"shell_bad\",\"tool\":\"shell\","
                                + "\"scope\":\"conversation-1234\","
                                + "\"command\":\"id\",\"timeout_ms\":60000}"))
                        .calls.isEmpty(),
                "shell accepted an excessive timeout");

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
        require(bad.rejectedCalls.isEmpty(),
                "unparseable JSON was incorrectly promoted to a rejected tool step");
        require(!HeartbeatToolProtocol.renderConversationToolRows(malformed)
                        .contains("\u8bbe\u7f6e\u5fc3\u8df3"),
                "malformed control payload displayed a tool activity row");

        String identifiableMalformed = HeartbeatToolProtocol.CONTROL_START
                + "\n{\"call\":{\"id\":\"visual_bad_json\","
                + "\"tool\":\"render_rich_panel\","
                + "\"scope\":\"conversation-1234\",\"panel\": }}\n"
                + HeartbeatToolProtocol.CONTROL_END;
        HeartbeatToolProtocol.Result identifiableBad =
                HeartbeatToolProtocol.parse(identifiableMalformed);
        require(identifiableBad.calls.isEmpty()
                        && identifiableBad.rejectedCalls.size() == 1
                        && "malformed_json".equals(
                        identifiableBad.rejectedCalls.get(0).reason),
                "malformed JSON with trusted tool metadata remained silently unreportable");

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

        HeartbeatToolProtocol.ToolCall shellCall =
                HeartbeatToolProtocol.parse(singleCallBlock(
                        "{\"id\":\"shell_result\",\"tool\":\"shell\","
                                + "\"scope\":\"conversation-1234\","
                                + "\"command\":\"which cp\"}")).calls.get(0);
        String toolResult = HeartbeatToolProtocol.toolResultEvent(
                shellCall, true, 0, "/system/bin/cp\n",
                "Shell command completed", "utf-8", false);
        require(toolResult.startsWith(HeartbeatToolProtocol.RESULT_START)
                        && toolResult.contains("\"ok\":true")
                        && toolResult.contains("/system/bin/cp")
                        && HeartbeatToolProtocol.isCompleteToolResultBody(toolResult),
                "tool result event omitted its private framing or payload");
        require("前文后文".equals(
                        HeartbeatToolProtocol.stripControlBlocks(
                                "前文" + toolResult + "后文")),
                "an echoed local tool result leaked into assistant text");
        require("问候\n".equals(HeartbeatToolProtocol.stripControlBlocks(
                        "问候\n[[DEEKSEEP_LOCAL_TOOL_RES")),
                "partial tool-result marker flashed into visible output");

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
                        && prompt.contains("get_current_time")
                        && prompt.contains("capture_screen")
                        && prompt.contains("tap_screen")
                        && prompt.contains("swipe_screen")
                        && prompt.contains("press_back")
                        && prompt.contains("ask_user")
                        && prompt.contains("read_file")
                        && prompt.contains("write_file")
                        && prompt.contains("shell")
                        && prompt.contains("render_rich_panel")
                        && prompt.contains("独立图形示例")
                        && prompt.contains("\"mode\":\"art\"")
                        && prompt.contains("\"type\":\"lantern\"")
                        && prompt.contains("有效 pixel_art 行示例")
                        && prompt.contains("参数校验失败")
                        && prompt.contains("不能提前说‘已经画好’")
                        && prompt.contains(HeartbeatToolProtocol.RESULT_START)
                        && prompt.contains("180 分钟")
                        && prompt.contains("\"scope\":\"conversation-1234\""),
                "default heartbeat system prompt omitted the local tool contract");
        require(prompt.contains("严格使用单步 Agent 循环")
                        && prompt.contains("{\"call\":")
                        && prompt.contains("每一轮回复最多只能调用一个工具")
                        && prompt.contains("控制块结束后必须立即停止输出")
                        && !prompt.contains("{\"calls\":["),
                "default heartbeat prompt still permits multi-tool batches");
        require(prompt.contains("坐标统一为 0 到 1000")
                        && prompt.contains("不会在同一轮把像素返回给你")
                        && prompt.contains("不得假装已经看见截图内容")
                        && prompt.contains("Android 系统 PATH")
                        && prompt.contains("收到结果前不得假装操作成功"),
                "basic Agent prompt omitted coordinate or screenshot-result boundaries");

        java.util.LinkedHashSet<String> toolsWithoutHeartbeat =
                new java.util.LinkedHashSet<>();
        for (String tool : AgentToolConfig.tools()) {
            if (!AgentToolConfig.isHeartbeatTool(tool)) toolsWithoutHeartbeat.add(tool);
        }
        String heartbeatDisabledPrompt = HeartbeatToolProtocol.systemPrompt(
                1785247200000L, "不应注入的旧约定", 180, "conversation-1234",
                toolsWithoutHeartbeat, AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE);
        require(heartbeatDisabledPrompt.contains("真实本地 Agent 工具")
                        && heartbeatDisabledPrompt.contains("get_current_time")
                        && heartbeatDisabledPrompt.contains("render_rich_panel")
                        && heartbeatDisabledPrompt.contains(HeartbeatToolProtocol.RESULT_START),
                "disabling heartbeat also removed the ordinary Agent contract");
        require(!heartbeatDisabledPrompt.contains("心跳")
                        && !heartbeatDisabledPrompt.contains("heartbeat")
                        && !heartbeatDisabledPrompt.contains("schedule_once")
                        && !heartbeatDisabledPrompt.contains("set_plan")
                        && !heartbeatDisabledPrompt.contains("clear_plan")
                        && !heartbeatDisabledPrompt.contains("set_interval")
                        && !heartbeatDisabledPrompt.contains("bind_chat")
                        && !heartbeatDisabledPrompt.contains("cancel_heartbeat")
                        && !heartbeatDisabledPrompt.contains(HeartbeatToolProtocol.EVENT_START)
                        && !heartbeatDisabledPrompt.contains("不应注入的旧约定"),
                "heartbeat-disabled Agent prompt still exposed a heartbeat agreement or tool");

        String enhancedPrompt = HeartbeatToolProtocol.systemPrompt(
                1785247200000L, "找用户闲聊", 180, "conversation-1234",
                new java.util.LinkedHashSet<>(AgentToolConfig.tools()),
                AgentToolConfig.PROMPT_STRENGTH_ENHANCED);
        require(enhancedPrompt.contains("第二档（增强）")
                        && enhancedPrompt.contains("get_current_time")
                        && enhancedPrompt.contains("ask_user")
                        && enhancedPrompt.contains("read_file、write_file 或 shell")
                        && enhancedPrompt.contains("capture_screen")
                        && enhancedPrompt.contains("render_rich_panel")
                        && !enhancedPrompt.contains("第三档（沉浸）"),
                "level-two guidance did not cover the complete Agent toolkit");
        String immersivePrompt = HeartbeatToolProtocol.systemPrompt(
                1785247200000L, "找用户闲聊", 180, "conversation-1234",
                new java.util.LinkedHashSet<>(AgentToolConfig.tools()),
                AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE);
        require(immersivePrompt.contains("第三档（沉浸）")
                        && immersivePrompt.contains("心情怎么样")
                        && immersivePrompt.contains("当前好感度")
                        && immersivePrompt.contains("首次记录")
                        && immersivePrompt.contains("截图、文件、Shell、问答、时间、心跳与富面板")
                        && immersivePrompt.contains("不会绕过工具开关"),
                "level-three guidance omitted immersive or all-tool behavior boundaries");

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

            FoldMessage resultEvent = new FoldMessage(
                    13, 12, "USER", "REQUEST",
                    "<system>\nprivate\n</system>\n\n" + toolResult);
            FoldMessage resultReply = new FoldMessage(
                    14, 13, "ASSISTANT", "RESPONSE",
                    "cp 位于 /system/bin/cp");
            FoldResponse resultHistory = new FoldResponse(
                    new FoldSession(14), visibleUser, proactiveReply,
                    resultEvent, resultReply);
            int resultCount =
                    ((Number) fold.invoke(null, resultHistory)).intValue();
            require(resultCount == 1 && resultHistory.b.size() == 3
                            && resultHistory.b.get(2) == resultReply
                            && Integer.valueOf(12).equals(resultReply.g),
                    "hidden tool-result request was not folded and reparented");

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
