package com.dsmod.probe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Strict hidden control protocol shared by the normal-chat response hook and proactive generation.
 *
 * <p>The deliberately long markers make accidental matches in ordinary prose extremely unlikely.
 * A started block is hidden even while it is incomplete, so streamed JSON never flashes in the
 * conversation. Only complete, valid JSON blocks produce tool calls.</p>
 */
final class HeartbeatToolProtocol {
    static final String CONTROL_START = "[[DEEKSEEP_LOCAL_TOOLS_V1]]";
    static final String CONTROL_END = "[[/DEEKSEEP_LOCAL_TOOLS_V1]]";
    static final String EVENT_START = "[[DEEKSEEP_ANONYMOUS_HEARTBEAT_EVENT_V1]]";
    static final String EVENT_END = "[[/DEEKSEEP_ANONYMOUS_HEARTBEAT_EVENT_V1]]";
    static final String RESULT_START = "[[DEEKSEEP_LOCAL_TOOL_RESULT_V1]]";
    static final String RESULT_END = "[[/DEEKSEEP_LOCAL_TOOL_RESULT_V1]]";

    static final String TOOL_SCHEDULE_ONCE = "schedule_once";
    static final String TOOL_SET_PLAN = "set_plan";
    static final String TOOL_CLEAR_PLAN = "clear_plan";
    static final String TOOL_SET_INTERVAL = "set_interval";
    static final String TOOL_BIND_CHAT = "bind_chat";
    static final String TOOL_CANCEL_HEARTBEAT = "cancel_heartbeat";
    static final String TOOL_GET_CURRENT_TIME = "get_current_time";
    static final String TOOL_CAPTURE_SCREEN = "capture_screen";
    static final String TOOL_TAP_SCREEN = "tap_screen";
    static final String TOOL_SWIPE_SCREEN = "swipe_screen";
    static final String TOOL_PRESS_BACK = "press_back";
    static final String TOOL_ASK_USER = "ask_user";
    static final String TOOL_READ_FILE = "read_file";
    static final String TOOL_WRITE_FILE = "write_file";
    static final String TOOL_NETWORK_REQUEST = "network_request";
    static final String TOOL_SHELL = "shell";
    static final String TOOL_DELAY = "delay";
    static final String TOOL_OPEN_APP = "open_app";
    static final String TOOL_SCREEN_POWER = "screen_power";
    static final String TOOL_MUSIC = "music";
    static final String TOOL_RENDER_RICH_PANEL = "render_rich_panel";

    // Invisible presentation marker consumed by the host Compose text hook. It keeps tool status
    // styling separate from model-authored Markdown and remains visually harmless if a future
    // host build moves the renderer before its mapping is updated.
    private static final String TOOL_STATUS_STYLE_MARKER =
            "\u2063\u200b\u2062\u200d\u2063\u200c\u2062";
    static final long TOOL_STATUS_GRAY_COLOR = 0xFF8A8A8AL << 32;
    static final long TOOL_STATUS_FONT_SIZE =
            4294967296L
                    | (((long) Float.floatToRawIntBits(10.666667f)) & 4294967295L);
    static final float TOOL_STATUS_FONT_SCALE = 2.0f / 3.0f;
    static final int TOOL_STATUS_EXPLICIT_STYLE_MASK = (1 << 2) | (1 << 4);
    // TextStyle.copy default mask: keep every existing property except color and fontSize.
    static final int TOOL_STATUS_TEXT_STYLE_COPY_MASK = 0x00FFFFFC;

    private static final int MAX_CONTROL_JSON = 48 * 1024;
    /**
     * The native chat bridge has one generation lane per conversation.  Keeping this at one is
     * not just a prompt preference: it is the protocol-level guard that makes execution order,
     * result delivery and side-effect deduplication deterministic.
     */
    private static final int MAX_CALLS_PER_RESPONSE = 1;
    private static final int MAX_INSTRUCTION = 1200;
    private static final int MAX_PATH = 1024;
    private static final int MAX_FILE_CONTENT = 32 * 1024;
    private static final int MAX_SHELL_COMMAND = 4096;
    private static final int MAX_NETWORK_URL = 2048;
    private static final int MAX_NETWORK_BODY = 32 * 1024;
    private static final int MAX_NETWORK_HEADERS = 24;
    private static final int MAX_FILE_READ_BYTES = 48 * 1024;
    private static final int DEFAULT_FILE_READ_BYTES = 32 * 1024;
    private static final int MAX_TOOL_RESULT_TEXT = 48 * 1024;
    private static final int MAX_DELAY_MS = 7 * 24 * 60 * 60 * 1000;
    private static final int MAX_QUESTIONS_PER_CALL = 4;
    private static final int MAX_OPTIONS_PER_QUESTION = 4;
    private static final int MAX_QUESTION_TEXT = 500;
    private static final int MAX_OPTION_TEXT = 160;
    private static final int MAX_REGISTERED_TOOL_STATUSES = 32;
    private static final int MAX_TRACKED_CALL_TIMES = 128;
    private static final ArrayList<String> REGISTERED_TOOL_STATUSES =
            new ArrayList<>();
    private static final LinkedHashMap<String, Long> TRACKED_CALL_TIMES =
            new LinkedHashMap<>();

    private HeartbeatToolProtocol() {}

    static final class Question {
        final String text;
        final List<String> options;

        Question(String text, List<String> options) {
            this.text = text == null ? "" : text;
            this.options = options == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(options));
        }
    }

    static final class ToolCall {
        final String id;
        final String tool;
        final String scope;
        final String at;
        final String instruction;
        final int minutes;
        final String mode;
        final String targetId;
        final int x;
        final int y;
        final int toX;
        final int toY;
        final int durationMs;
        final List<Question> questions;
        final String path;
        final String content;
        final boolean append;
        final boolean createParents;
        final long offset;
        final int maxBytes;
        final String command;
        final int timeoutMs;
        final long invokedAt;

        ToolCall(String id, String tool, String scope, String at,
                 String instruction, int minutes, String mode, String targetId) {
            this(id, tool, scope, at, instruction, minutes, mode, targetId,
                    -1, -1, -1, -1, 0);
        }

        ToolCall(String id, String tool, String scope, String at,
                 String instruction, int minutes, String mode, String targetId,
                 int x, int y, int toX, int toY, int durationMs) {
            this(id, tool, scope, at, instruction, minutes, mode, targetId,
                    x, y, toX, toY, durationMs,
                    Collections.<Question>emptyList());
        }

        ToolCall(String id, String tool, String scope, String at,
                 String instruction, int minutes, String mode, String targetId,
                 int x, int y, int toX, int toY, int durationMs,
                 List<Question> questions) {
            this(id, tool, scope, at, instruction, minutes, mode, targetId,
                    x, y, toX, toY, durationMs, questions,
                    "", "", false, false, 0L, 0, "", 0);
        }

        ToolCall(String id, String tool, String scope, String at,
                 String instruction, int minutes, String mode, String targetId,
                 int x, int y, int toX, int toY, int durationMs,
                 List<Question> questions, String path, String content,
                 boolean append, boolean createParents, long offset,
                 int maxBytes, String command, int timeoutMs) {
            this.id = id;
            this.tool = tool;
            this.scope = scope;
            this.at = at;
            this.instruction = instruction;
            this.minutes = minutes;
            this.mode = mode;
            this.targetId = targetId;
            this.x = x;
            this.y = y;
            this.toX = toX;
            this.toY = toY;
            this.durationMs = durationMs;
            this.questions = questions == null
                    ? Collections.<Question>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(questions));
            this.path = path == null ? "" : path;
            this.content = content == null ? "" : content;
            this.append = append;
            this.createParents = createParents;
            this.offset = offset;
            this.maxBytes = maxBytes;
            this.command = command == null ? "" : command;
            this.timeoutMs = timeoutMs;
            this.invokedAt = stableInvocationTime(scope, id, tool);
        }
    }

    /**
     * A control block that named a real, scoped tool but failed that tool's argument schema.
     * It is kept separate from {@link Result#calls}: rejected calls must never reach an executor,
     * but the visible-chat bridge still needs enough trusted metadata to show a failure row and
     * return a private correction result to the originating model turn.
     */
    static final class RejectedCall {
        final ToolCall call;
        final String reason;

        RejectedCall(ToolCall call, String reason) {
            this.call = call;
            this.reason = reason == null ? "invalid_arguments" : reason;
        }
    }

    static final class Result {
        final String visibleText;
        final List<ToolCall> calls;
        final List<RejectedCall> rejectedCalls;
        final boolean incompleteControlBlock;

        Result(String visibleText, List<ToolCall> calls,
               List<RejectedCall> rejectedCalls,
               boolean incompleteControlBlock) {
            this.visibleText = visibleText == null ? "" : visibleText;
            this.calls = calls == null
                    ? Collections.<ToolCall>emptyList()
                    : Collections.unmodifiableList(calls);
            this.rejectedCalls = rejectedCalls == null
                    ? Collections.<RejectedCall>emptyList()
                    : Collections.unmodifiableList(rejectedCalls);
            this.incompleteControlBlock = incompleteControlBlock;
        }
    }

    static Result parse(String value) {
        return parseInternal(value, false);
    }

    /**
     * Removes private control framing while replacing each complete valid call with a compact,
     * user-facing agent activity row. This is deliberately separate from {@link #parse(String)}
     * so THINK fragments, model history sent back upstream and proactive notification text never
     * expose presentation-only rows.
     */
    static Result parseForConversation(String value) {
        return parseInternal(value, true);
    }

    private static Result parseInternal(String value, boolean renderToolRows) {
        if (value == null || value.length() == 0) {
            return new Result(value, Collections.<ToolCall>emptyList(),
                    Collections.<RejectedCall>emptyList(), false);
        }
        String source = normalizeMarkdownEscapedControlMarker(value);
        StringBuilder visible = new StringBuilder(source.length());
        ArrayList<ToolCall> calls = new ArrayList<>();
        ArrayList<RejectedCall> rejectedCalls = new ArrayList<>();
        boolean incomplete = false;
        int cursor = 0;
        while (cursor < source.length()) {
            int controlStart = source.indexOf(CONTROL_START, cursor);
            int eventStart = source.indexOf(EVENT_START, cursor);
            int resultStart = source.indexOf(RESULT_START, cursor);
            int start = firstMarker(controlStart, eventStart, resultStart);
            if (start < 0) {
                visible.append(source, cursor, source.length());
                break;
            }
            visible.append(source, cursor, start);
            boolean controlBlock = start == controlStart;
            boolean heartbeatEvent = start == eventStart;
            String openingMarker = controlBlock
                    ? CONTROL_START
                    : (heartbeatEvent ? EVENT_START : RESULT_START);
            String closingMarker = controlBlock
                    ? CONTROL_END
                    : (heartbeatEvent ? EVENT_END : RESULT_END);
            int payloadStart = start + openingMarker.length();
            int end = source.indexOf(closingMarker, payloadStart);
            if (end < 0) {
                incomplete = true;
                break;
            }
            String payload = source.substring(payloadStart, end).trim();
            if (controlBlock && payload.length() > 0
                    && payload.length() <= MAX_CONTROL_JSON) {
                payload = unwrapControlPayload(payload);
                int firstNewCall = calls.size();
                int firstRejectedCall = rejectedCalls.size();
                parsePayload(payload, calls, rejectedCalls);
                if (renderToolRows && calls.size() > firstNewCall) {
                    removeWrappingCodeFence(visible);
                    appendToolRows(visible, calls, firstNewCall);
                } else if (renderToolRows
                        && rejectedCalls.size() > firstRejectedCall) {
                    removeWrappingCodeFence(visible);
                    appendRejectedToolRows(
                            visible, rejectedCalls, firstRejectedCall);
                }
                if (calls.size() > firstNewCall
                        || rejectedCalls.size() > firstRejectedCall) {
                    // Execute one real action, return its result privately, and let the model
                    // choose the next action in a fresh turn. Rejected calls follow the same
                    // boundary: the bridge reports a real failure without executing anything.
                    // Everything after either result belongs to an untrusted, premature
                    // continuation and stays hidden.
                    cursor = end + closingMarker.length();
                    break;
                }
            }
            cursor = end + closingMarker.length();
        }
        if (cursor == source.length()) {
            // A control block may end at the final byte, in which case the loop does not append.
        }
        String safe = hidePartialOpeningMarker(visible.toString());
        return new Result(safe, calls, rejectedCalls, incomplete);
    }

    /**
     * Some model turns Markdown-escape underscores (and occasionally brackets) even though the
     * contract says not to use a code block. Accept only escaped spellings of the exact private
     * marker; this does not broaden execution to natural-language or arbitrary JSON.
     */
    private static String normalizeMarkdownEscapedControlMarker(String value) {
        if (value == null || value.indexOf("DEEKSEEP") < 0) return value;
        String identifier = "DEEKSEEP_LOCAL_TOOLS_V1";
        String escapedIdentifier = "DEEKSEEP\\_LOCAL\\_TOOLS\\_V1";
        String source = value.replace(escapedIdentifier, identifier);
        source = source
                .replace("\\[\\[" + identifier + "\\]\\]", CONTROL_START)
                .replace("\\[\\[/" + identifier + "\\]\\]", CONTROL_END);
        return source;
    }

    /** Accepts a JSON fence only inside an already authenticated control block. */
    private static String unwrapControlPayload(String value) {
        String payload = value == null ? "" : value.trim();
        if (!payload.startsWith("```")) return payload;
        int firstLine = payload.indexOf('\n');
        int closing = payload.lastIndexOf("```");
        if (firstLine < 0 || closing <= firstLine
                || payload.substring(closing + 3).trim().length() != 0) {
            return payload;
        }
        String language = payload.substring(3, firstLine).trim();
        if (language.length() != 0 && !"json".equalsIgnoreCase(language)) {
            return payload;
        }
        return payload.substring(firstLine + 1, closing).trim();
    }

    /** Removes a Markdown fence immediately wrapping an accepted private call. */
    private static void removeWrappingCodeFence(StringBuilder visible) {
        if (visible == null || visible.length() == 0) return;
        int lineEnd = visible.length();
        while (lineEnd > 0 && (visible.charAt(lineEnd - 1) == '\n'
                || visible.charAt(lineEnd - 1) == '\r'
                || Character.isWhitespace(visible.charAt(lineEnd - 1)))) {
            lineEnd--;
        }
        int lineStart = lineEnd;
        while (lineStart > 0 && visible.charAt(lineStart - 1) != '\n'
                && visible.charAt(lineStart - 1) != '\r') {
            lineStart--;
        }
        String line = visible.substring(lineStart, lineEnd).trim();
        if (!"```".equals(line) && !"```json".equalsIgnoreCase(line)
                && !"```text".equalsIgnoreCase(line)) {
            return;
        }
        visible.delete(lineStart, visible.length());
    }

    private static int firstMarker(int first, int second, int third) {
        int result = -1;
        if (first >= 0) result = first;
        if (second >= 0 && (result < 0 || second < result)) result = second;
        if (third >= 0 && (result < 0 || third < result)) result = third;
        return result;
    }

    static String stripControlBlocks(String value) {
        return stripToolStatusStyleMarkers(parse(value).visibleText);
    }

    static String renderConversationToolRows(String value) {
        Result parsed = parseForConversation(value);
        if (AgentToolConfig.hideToolLogs()) return parsed.visibleText;
        if (!parsed.incompleteControlBlock) {
            if (!AgentToolConfig.enabledFast()
                    || !parsed.calls.isEmpty() || !parsed.rejectedCalls.isEmpty()
                    || !looksLikeUnbackedToolClaim(value)) {
                return parsed.visibleText;
            }
            StringBuilder visible = new StringBuilder(parsed.visibleText);
            appendParagraphBreak(visible);
            boolean chinese = Locale.getDefault().getLanguage()
                    .toLowerCase(Locale.US).startsWith("zh");
            String status = chinese
                    ? "未检测到完整工具调用，操作未执行"
                    : "No complete tool call detected; action was not run";
            visible.append("> ").append(markToolStatus(
                    statusClock(System.currentTimeMillis()) + "  " + status));
            visible.append("\n\n");
            return visible.toString();
        }
        StringBuilder visible = new StringBuilder(parsed.visibleText);
        appendParagraphBreak(visible);
        boolean chinese = Locale.getDefault().getLanguage()
                .toLowerCase(Locale.US).startsWith("zh");
        String status = chinese
                ? "工具调用不完整，未执行（回复可能中断）"
                : "Incomplete tool call; not run (response may have been interrupted)";
        visible.append("> ").append(markToolStatus(
                statusClock(System.currentTimeMillis()) + "  " + status));
        visible.append("\n\n");
        return visible.toString();
    }

    private static boolean looksLikeUnbackedToolClaim(String value) {
        if (value == null || value.length() == 0
                || value.indexOf(CONTROL_START) >= 0) return false;
        String lower = value.toLowerCase(Locale.US);
        String[] claims = new String[]{
                "已经打开", "已打开", "打开好了", "已经设置", "已设置",
                "设置好了", "操作完成", "已经执行", "已执行", "执行完成",
                "已经锁屏", "已锁屏", "已经唤醒", "已唤醒", "已经画好",
                "已画好", "画好了", "已经生成", "已生成",
                "opened it", "has been opened", "set it up", "has been set",
                "operation completed", "executed successfully", "drawing is ready"
        };
        for (String claim : claims) {
            if (lower.contains(claim)) return true;
        }
        return false;
    }

    private static void appendToolRows(
            StringBuilder visible, List<ToolCall> calls, int firstCall) {
        if (firstCall < 0 || firstCall >= calls.size()) return;
        boolean hideStatus = AgentToolConfig.hideToolLogs();
        boolean hasPanel = false;
        for (int index = firstCall; index < calls.size(); index++) {
            ToolCall call = calls.get(index);
            if (TOOL_RENDER_RICH_PANEL.equals(call.tool)
                    && RichPanelRenderer.isRenderedPanel(call.content)) {
                hasPanel = true;
                break;
            }
        }
        if (hideStatus && !hasPanel) return;
        appendParagraphBreak(visible);
        boolean chinese = Locale.getDefault().getLanguage().toLowerCase(Locale.US)
                .startsWith("zh");
        for (int index = firstCall; index < calls.size(); index++) {
            ToolCall call = calls.get(index);
            if (!hideStatus) {
                if (index > firstCall) visible.append('\n');
                visible.append("> ")
                        .append(markToolStatus(toolStatusText(call, chinese)));
            }
            if (TOOL_RENDER_RICH_PANEL.equals(call.tool)
                    && RichPanelRenderer.isRenderedPanel(call.content)) {
                if (!hideStatus || index > firstCall) visible.append("\n\n");
                visible.append(call.content);
            }
        }
        visible.append("\n\n");
    }

    private static void appendRejectedToolRows(
            StringBuilder visible, List<RejectedCall> rejectedCalls,
            int firstCall) {
        if (firstCall < 0 || firstCall >= rejectedCalls.size()) return;
        if (AgentToolConfig.hideToolLogs()) return;
        appendParagraphBreak(visible);
        boolean chinese = Locale.getDefault().getLanguage().toLowerCase(Locale.US)
                .startsWith("zh");
        for (int index = firstCall; index < rejectedCalls.size(); index++) {
            if (index > firstCall) visible.append('\n');
            RejectedCall rejected = rejectedCalls.get(index);
            ToolCall call = rejected == null ? null : rejected.call;
            String operation = toolOperationLabel(call, chinese);
            String failure = chinese ? "参数无效，未执行" : "Invalid arguments; not run";
            visible.append("> ")
                    .append(markToolStatus(statusClock(
                            call == null ? System.currentTimeMillis() : call.invokedAt)
                            + "  " + joinStatus(operation, failure, chinese)));
        }
        visible.append("\n\n");
    }

    private static String markToolStatus(String value) {
        registerToolStatus(value);
        return TOOL_STATUS_STYLE_MARKER + value + TOOL_STATUS_STYLE_MARKER;
    }

    private static void registerToolStatus(String value) {
        if (value == null || value.length() == 0) return;
        synchronized (REGISTERED_TOOL_STATUSES) {
            REGISTERED_TOOL_STATUSES.remove(value);
            REGISTERED_TOOL_STATUSES.add(value);
            while (REGISTERED_TOOL_STATUSES.size() > MAX_REGISTERED_TOOL_STATUSES) {
                REGISTERED_TOOL_STATUSES.remove(0);
            }
        }
    }

    static boolean hasToolStatusStyleMarker(String value) {
        return value != null && value.indexOf(TOOL_STATUS_STYLE_MARKER) >= 0;
    }

    static boolean isIsolatedToolStatusText(String value) {
        if (value == null) return false;
        int start = value.indexOf(TOOL_STATUS_STYLE_MARKER);
        if (start < 0 || value.substring(0, start).trim().length() != 0) return false;
        int contentStart = start + TOOL_STATUS_STYLE_MARKER.length();
        int end = value.indexOf(TOOL_STATUS_STYLE_MARKER, contentStart);
        return end >= contentStart
                && value.substring(contentStart, end).trim().length() > 0
                && value.substring(end + TOOL_STATUS_STYLE_MARKER.length())
                .trim().length() == 0;
    }

    static String stripToolStatusStyleMarkers(String value) {
        if (!hasToolStatusStyleMarker(value)) return value == null ? "" : value;
        return value.replace(TOOL_STATUS_STYLE_MARKER, "");
    }

    static boolean isRegisteredToolStatusText(String value) {
        if (value == null || value.length() == 0) return false;
        if (!hasToolStatusStyleMarker(value)
                && value.indexOf("\u5fc3\u8df3") < 0
                && value.indexOf("heartbeat") < 0
                && value.indexOf("Heartbeat") < 0
                && value.indexOf("\u5f53\u524d\u65f6\u95f4") < 0
                && value.indexOf("Current time") < 0
                && value.indexOf("\u622a\u56fe") < 0
                && value.indexOf("screen") < 0
                && value.indexOf("\u70b9\u51fb") < 0
                && value.indexOf("Tap") < 0
                && value.indexOf("\u6ed1\u52a8") < 0
                && value.indexOf("Swipe") < 0
                && value.indexOf("\u8fd4\u56de") < 0
                && value.indexOf("Back") < 0
                && value.indexOf("\u8be2\u95ee\u7528\u6237") < 0
                && value.indexOf("Ask user") < 0
                && value.indexOf("\u8bfb\u53d6\u6587\u4ef6") < 0
                && value.indexOf("Read file") < 0
                && value.indexOf("\u5199\u5165\u6587\u4ef6") < 0
                && value.indexOf("Write file") < 0
                && value.indexOf("\u8ffd\u52a0\u6587\u4ef6") < 0
                && value.indexOf("Append file") < 0
                && value.indexOf("\u7f51\u7edc\u8bf7\u6c42") < 0
                && value.indexOf("Network request") < 0
                && value.indexOf("Shell") < 0
                && value.indexOf("\u5bcc\u9762\u677f") < 0
                && value.indexOf("Rich panel") < 0
                && value.indexOf("\u5bcc\u89c6\u89c9") < 0
                && value.indexOf("Rich visual") < 0) {
            return false;
        }
        String clean = stripToolStatusStyleMarkers(value);
        int start = 0;
        boolean found = false;
        while (start <= clean.length()) {
            int end = clean.indexOf('\n', start);
            if (end < 0) end = clean.length();
            String line = clean.substring(start, end).trim();
            if (line.startsWith("> ")) line = line.substring(2).trim();
            if (line.length() > 0) {
                synchronized (REGISTERED_TOOL_STATUSES) {
                    if (!REGISTERED_TOOL_STATUSES.contains(line)) return false;
                }
                found = true;
            }
            if (end == clean.length()) break;
            start = end + 1;
        }
        return found;
    }

    static int explicitToolStatusStyleMask(int defaultMask) {
        return defaultMask & ~TOOL_STATUS_EXPLICIT_STYLE_MASK;
    }

    private static void appendParagraphBreak(StringBuilder value) {
        int trailingNewlines = 0;
        for (int index = value.length() - 1;
             index >= 0 && value.charAt(index) == '\n'; index--) {
            trailingNewlines++;
        }
        if (trailingNewlines == 0) value.append("\n\n");
        else if (trailingNewlines == 1) value.append('\n');
    }

    private static String toolOperationLabel(ToolCall call, boolean chinese) {
        if (call == null) return chinese ? "工具调用" : "Tool call";
        if (TOOL_SCHEDULE_ONCE.equals(call.tool)
                || TOOL_SET_INTERVAL.equals(call.tool)) {
            return chinese ? "设置心跳" : "Set heartbeat";
        }
        if (TOOL_SET_PLAN.equals(call.tool)) {
            return chinese ? "更新心跳约定" : "Update heartbeat plan";
        }
        if (TOOL_CLEAR_PLAN.equals(call.tool)) {
            return chinese ? "清除心跳约定" : "Clear heartbeat plan";
        }
        if (TOOL_BIND_CHAT.equals(call.tool)) {
            return chinese ? "绑定心跳" : "Bind heartbeat";
        }
        if (TOOL_CANCEL_HEARTBEAT.equals(call.tool)) {
            return chinese ? "取消心跳" : "Cancel heartbeat";
        }
        if (TOOL_GET_CURRENT_TIME.equals(call.tool)) {
            return chinese ? "获取当前时间" : "Get current time";
        }
        if (TOOL_CAPTURE_SCREEN.equals(call.tool)) {
            return chinese ? "获取截图" : "Capture screen";
        }
        if (TOOL_TAP_SCREEN.equals(call.tool)) {
            return chinese ? "点击屏幕" : "Tap screen";
        }
        if (TOOL_SWIPE_SCREEN.equals(call.tool)) {
            return chinese ? "滑动屏幕" : "Swipe screen";
        }
        if (TOOL_PRESS_BACK.equals(call.tool)) {
            return chinese ? "返回上一层" : "Back";
        }
        if (TOOL_ASK_USER.equals(call.tool)) {
            return chinese ? "询问用户" : "Ask user";
        }
        if (TOOL_READ_FILE.equals(call.tool)) {
            return chinese ? "读取文件" : "Read file";
        }
        if (TOOL_WRITE_FILE.equals(call.tool)) {
            return chinese ? "写入文件" : "Write file";
        }
        if (TOOL_NETWORK_REQUEST.equals(call.tool)) {
            return chinese ? "网络请求" : "Network request";
        }
        if (TOOL_SHELL.equals(call.tool)) return "Shell";
        if (TOOL_DELAY.equals(call.tool)) {
            return chinese ? "延迟执行" : "Delay execution";
        }
        if (TOOL_OPEN_APP.equals(call.tool)) {
            return chinese ? "打开应用" : "Open app";
        }
        if (TOOL_SCREEN_POWER.equals(call.tool)) {
            return chinese ? "屏幕电源" : "Screen power";
        }
        if (TOOL_MUSIC.equals(call.tool)) {
            return chinese ? "音乐" : "Music";
        }
        if (TOOL_RENDER_RICH_PANEL.equals(call.tool)) {
            return chinese ? "生成富视觉" : "Render rich visual";
        }
        return chinese ? "工具调用" : "Tool call";
    }

    private static String toolStatusText(ToolCall call, boolean chinese) {
        String status;
        if (call == null) {
            status = chinese ? "\u5fc3\u8df3\u8bbe\u7f6e" : "Heartbeat settings";
            return statusClock(System.currentTimeMillis()) + "  " + status;
        }
        if (TOOL_SCHEDULE_ONCE.equals(call.tool)) {
            status = (chinese ? "\u8bbe\u7f6e\u5fc3\u8df3" : "Set heartbeat")
                    + " to " + friendlyToolTime(call.at, chinese);
        } else if (TOOL_SET_PLAN.equals(call.tool)) {
            status = chinese ? "\u66f4\u65b0\u5fc3\u8df3\u7ea6\u5b9a" : "Update heartbeat plan";
        } else if (TOOL_CLEAR_PLAN.equals(call.tool)) {
            status = chinese ? "\u6e05\u9664\u5fc3\u8df3\u7ea6\u5b9a" : "Clear heartbeat plan";
        } else if (TOOL_SET_INTERVAL.equals(call.tool)) {
            String interval = call.minutes > 0
                    ? (chinese ? "\u6bcf" + call.minutes + "\u5206\u949f"
                    : "Every " + call.minutes
                    + (call.minutes == 1 ? " minute" : " minutes"))
                    : "";
            status = (chinese ? "\u8bbe\u7f6e\u5fc3\u8df3" : "Set heartbeat")
                    + " to " + interval;
        } else if (TOOL_BIND_CHAT.equals(call.tool)) {
            status = joinStatus(
                    chinese ? "\u7ed1\u5b9a\u5fc3\u8df3" : "Bind heartbeat",
                    chinese ? "\u5f53\u524d\u5bf9\u8bdd" : "Current chat",
                    chinese);
        } else if (TOOL_CANCEL_HEARTBEAT.equals(call.tool)) {
            status = joinStatus(
                    chinese ? "\u53d6\u6d88\u5fc3\u8df3" : "Cancel heartbeat",
                    cancelDetail(call, chinese), chinese);
        } else if (TOOL_GET_CURRENT_TIME.equals(call.tool)) {
            status = joinStatus(
                    chinese ? "\u83b7\u53d6\u5f53\u524d\u65f6\u95f4" : "Get current time",
                    fullStatusTime(call.invokedAt), chinese);
        } else if (TOOL_CAPTURE_SCREEN.equals(call.tool)) {
            status = chinese ? "\u83b7\u53d6\u622a\u56fe" : "Capture screen";
        } else if (TOOL_TAP_SCREEN.equals(call.tool)) {
            status = joinStatus(
                    chinese ? "\u70b9\u51fb\u5c4f\u5e55" : "Tap screen",
                    "x=" + call.x + ", y=" + call.y, chinese);
        } else if (TOOL_SWIPE_SCREEN.equals(call.tool)) {
            status = joinStatus(
                    chinese ? "\u6ed1\u52a8\u5c4f\u5e55" : "Swipe screen",
                    "(" + call.x + "," + call.y + ") \u2192 ("
                            + call.toX + "," + call.toY + ")", chinese);
        } else if (TOOL_PRESS_BACK.equals(call.tool)) {
            status = chinese ? "\u8fd4\u56de\u4e0a\u4e00\u5c42" : "Back";
        } else if (TOOL_ASK_USER.equals(call.tool)) {
            String question = call.questions.isEmpty()
                    ? "" : call.questions.get(0).text;
            if (question.length() > 28) question = question.substring(0, 28) + "\u2026";
            status = joinStatus(
                    chinese ? "\u8be2\u95ee\u7528\u6237" : "Ask user",
                    question, chinese);
        } else if (TOOL_READ_FILE.equals(call.tool)) {
            status = joinStatus(
                    chinese ? "\u8bfb\u53d6\u6587\u4ef6" : "Read file",
                    compactPath(call.path), chinese);
        } else if (TOOL_WRITE_FILE.equals(call.tool)) {
            status = joinStatus(
                    chinese
                            ? (call.append ? "\u8ffd\u52a0\u6587\u4ef6"
                            : "\u5199\u5165\u6587\u4ef6")
                            : (call.append ? "Append file" : "Write file"),
                    compactPath(call.path), chinese);
        } else if (TOOL_NETWORK_REQUEST.equals(call.tool)) {
            status = joinStatus(
                    chinese ? "网络请求" : "Network request",
                    call.mode + " " + compactNetworkTarget(call.path), chinese);
        } else if (TOOL_SHELL.equals(call.tool)) {
            String command = call.command.replace('\r', ' ')
                    .replace('\n', ' ').trim();
            if (command.length() > 38) command = command.substring(0, 38) + "\u2026";
            status = joinStatus("Shell", command, chinese);
        } else if (TOOL_DELAY.equals(call.tool)) {
            status = joinStatus(
                    chinese ? "延迟执行" : "Delay execution",
                    friendlyDuration(call.durationMs, chinese), chinese);
        } else if (TOOL_OPEN_APP.equals(call.tool)) {
            status = joinStatus(
                    chinese ? "打开应用" : "Open app",
                    call.targetId, chinese);
        } else if (TOOL_SCREEN_POWER.equals(call.tool)) {
            status = chinese
                    ? ("sleep".equals(call.mode) ? "熄灭屏幕" : "唤醒屏幕")
                    : ("sleep".equals(call.mode) ? "Sleep screen" : "Wake screen");
        } else if (TOOL_MUSIC.equals(call.tool)) {
            String detail = "local".equals(call.targetId) && call.path.length() > 0
                    ? compactPath(call.path)
                    : ("search".equals(call.mode) ? call.instruction : call.mode);
            status = joinStatus(chinese ? "音乐" : "Music", detail, chinese);
        } else if (TOOL_RENDER_RICH_PANEL.equals(call.tool)) {
            String title = call.instruction;
            if (title.length() > 28) title = title.substring(0, 28) + "\u2026";
            status = joinStatus(
                    chinese ? "\u751f\u6210\u5bcc\u89c6\u89c9" : "Render rich visual",
                    title, chinese);
        } else {
            status = chinese ? "\u5fc3\u8df3\u8bbe\u7f6e" : "Heartbeat settings";
        }
        return statusClock(call.invokedAt) + "  " + status;
    }

    private static String compactPath(String value) {
        String path = value == null ? "" : value.trim();
        if (path.length() <= 42) return path;
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        if (name.length() > 30) name = name.substring(name.length() - 30);
        return "\u2026/" + name;
    }

    private static String friendlyDuration(int durationMs, boolean chinese) {
        if (durationMs % 3_600_000 == 0) {
            int hours = durationMs / 3_600_000;
            return chinese ? hours + "小时" : hours + (hours == 1 ? " hour" : " hours");
        }
        if (durationMs % 1000 == 0) {
            int seconds = durationMs / 1000;
            return chinese ? seconds + "秒" : seconds + (seconds == 1 ? " second" : " seconds");
        }
        return durationMs + " ms";
    }

    private static String statusClock(long value) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(value));
    }

    private static String fullStatusTime(long value) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd HH:mm:ss", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(value));
    }

    private static long stableInvocationTime(
            String scope, String id, String tool) {
        String key = String.valueOf(scope) + "|" + String.valueOf(id)
                + "|" + String.valueOf(tool);
        synchronized (TRACKED_CALL_TIMES) {
            Long existing = TRACKED_CALL_TIMES.get(key);
            if (existing != null) return existing.longValue();
            long now = System.currentTimeMillis();
            TRACKED_CALL_TIMES.put(key, Long.valueOf(now));
            while (TRACKED_CALL_TIMES.size() > MAX_TRACKED_CALL_TIMES) {
                java.util.Iterator<Map.Entry<String, Long>> iterator =
                        TRACKED_CALL_TIMES.entrySet().iterator();
                if (!iterator.hasNext()) break;
                iterator.next();
                iterator.remove();
            }
            return now;
        }
    }

    private static String joinStatus(String label, String detail, boolean chinese) {
        if (detail == null || detail.length() == 0) return label;
        return label + (chinese ? "\uff1a" : ": ") + detail;
    }

    private static String cancelDetail(ToolCall call, boolean chinese) {
        if (call == null) return "";
        if ("once".equals(call.mode)) {
            return chinese ? "\u5355\u4e2a\u4efb\u52a1" : "One task";
        }
        if ("all_once".equals(call.mode)) {
            return chinese ? "\u5168\u90e8\u4e00\u6b21\u6027\u4efb\u52a1" : "All one-time tasks";
        }
        if ("periodic".equals(call.mode)) {
            return chinese ? "\u5468\u671f\u5fc3\u8df3" : "Recurring heartbeat";
        }
        if ("all".equals(call.mode)) {
            return chinese ? "\u5168\u90e8\u5fc3\u8df3" : "All heartbeats";
        }
        return "";
    }

    private static String friendlyToolTime(String value, boolean chinese) {
        String input = value == null ? "" : value.trim();
        if (input.length() == 0) return "";
        String[] patterns = new String[]{
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mmXXX",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(pattern, Locale.US);
                parser.setLenient(false);
                ParsePosition position = new ParsePosition(0);
                Date parsed = parser.parse(input, position);
                if (parsed == null || position.getIndex() != input.length()) continue;
                SimpleDateFormat display = new SimpleDateFormat(
                        chinese ? "M\u6708d\u65e5 HH:mm:ss" : "MMM d, HH:mm:ss",
                        chinese ? Locale.CHINA : Locale.getDefault());
                display.setTimeZone(TimeZone.getDefault());
                return display.format(parsed);
            } catch (Throwable ignored) {}
        }
        return "";
    }

    private static void parsePayload(
            String payload, List<ToolCall> out,
            List<RejectedCall> rejectedCalls) {
        try {
            JSONObject root = new JSONObject(payload);
            // V1 originally allowed one large {"calls":[...]} batch. Keep accepting it for
            // already-generated/history content, but prefer the streaming-friendly singular
            // envelope taught by systemPrompt(): one complete control block per tool call.
            JSONObject single = root.optJSONObject("call");
            if (single == null && root.has("tool")) single = root;
            if (single != null) {
                ToolCall parsed = parseCall(single);
                if (parsed != null && out.size() < MAX_CALLS_PER_RESPONSE) {
                    out.add(parsed);
                } else if (parsed == null && rejectedCalls.isEmpty()) {
                    RejectedCall rejected = rejectedCall(single);
                    if (rejected != null) rejectedCalls.add(rejected);
                }
                return;
            }
            JSONArray array = root.optJSONArray("calls");
            if (array == null) {
                RejectedCall rejected = rejectedPayloadMetadata(
                        payload, "invalid_envelope");
                if (rejected != null) rejectedCalls.add(rejected);
                return;
            }
            int count = array.length();
            for (int i = 0; i < count; i++) {
                if (out.size() >= MAX_CALLS_PER_RESPONSE) break;
                JSONObject call = array.optJSONObject(i);
                ToolCall parsed = parseCall(call);
                if (parsed != null) {
                    out.add(parsed);
                    return;
                }
                RejectedCall rejected = rejectedCall(call);
                if (rejected != null) {
                    rejectedCalls.add(rejected);
                    return;
                }
            }
        } catch (Throwable ignored) {
            RejectedCall rejected = rejectedPayloadMetadata(
                    payload, "malformed_json");
            if (rejected != null && rejectedCalls.isEmpty()) {
                rejectedCalls.add(rejected);
            }
        }
    }

    private static RejectedCall rejectedCall(JSONObject call) {
        if (call == null) return null;
        String tool = cleanToken(call.optString("tool", ""), 40);
        if (!isSupportedTool(tool)) return null;
        String scope = cleanScope(call.optString("scope", ""));
        if (scope.length() == 0) return null;
        String id = cleanId(call.optString("id", ""));
        if (id.length() == 0) {
            id = "invalid_" + Integer.toHexString(call.toString().hashCode());
        }
        ToolCall metadata = new ToolCall(
                id, tool, scope, "", "", 0, "", "");
        String reason = TOOL_RENDER_RICH_PANEL.equals(tool)
                ? "invalid_visual_schema" : "invalid_arguments";
        return new RejectedCall(metadata, reason);
    }

    /**
     * Extracts only quoted identity fields from malformed JSON. The returned metadata is never
     * executable; it merely lets the authorized originating chat receive an honest schema-failure
     * result. This keeps syntax errors from becoming invisible while preserving strict parsing for
     * every side-effect-bearing argument.
     */
    private static RejectedCall rejectedPayloadMetadata(
            String payload, String reason) {
        String tool = cleanToken(looseJsonStringField(payload, "tool", 40), 40);
        if (!isSupportedTool(tool)) return null;
        String scope = cleanScope(looseJsonStringField(payload, "scope", 2048));
        if (scope.length() == 0) return null;
        String id = cleanId(looseJsonStringField(payload, "id", 120));
        if (id.length() == 0) {
            id = "invalid_" + Integer.toHexString(
                    String.valueOf(payload).hashCode());
        }
        return new RejectedCall(
                new ToolCall(id, tool, scope, "", "", 0, "", ""), reason);
    }

    private static String looseJsonStringField(
            String source, String field, int maximum) {
        if (source == null || field == null || field.length() == 0) return "";
        String marker = "\"" + field + "\"";
        int search = 0;
        while (search < source.length()) {
            int start = source.indexOf(marker, search);
            if (start < 0) return "";
            int cursor = start + marker.length();
            while (cursor < source.length()
                    && Character.isWhitespace(source.charAt(cursor))) cursor++;
            if (cursor >= source.length() || source.charAt(cursor) != ':') {
                search = start + marker.length();
                continue;
            }
            cursor++;
            while (cursor < source.length()
                    && Character.isWhitespace(source.charAt(cursor))) cursor++;
            if (cursor >= source.length() || source.charAt(cursor) != '"') {
                return "";
            }
            cursor++;
            StringBuilder value = new StringBuilder(Math.min(64, maximum));
            boolean escaped = false;
            while (cursor < source.length() && value.length() <= maximum) {
                char code = source.charAt(cursor++);
                if (escaped) {
                    if (code != '"' && code != '\\' && code != '/') return "";
                    value.append(code);
                    escaped = false;
                } else if (code == '\\') {
                    escaped = true;
                } else if (code == '"') {
                    return value.toString();
                } else if (code < 0x20) {
                    return "";
                } else {
                    value.append(code);
                }
            }
            return "";
        }
        return "";
    }

    private static boolean isSupportedTool(String tool) {
        return TOOL_SCHEDULE_ONCE.equals(tool)
                || TOOL_SET_PLAN.equals(tool)
                || TOOL_CLEAR_PLAN.equals(tool)
                || TOOL_SET_INTERVAL.equals(tool)
                || TOOL_BIND_CHAT.equals(tool)
                || TOOL_CANCEL_HEARTBEAT.equals(tool)
                || TOOL_GET_CURRENT_TIME.equals(tool)
                || TOOL_CAPTURE_SCREEN.equals(tool)
                || TOOL_TAP_SCREEN.equals(tool)
                || TOOL_SWIPE_SCREEN.equals(tool)
                || TOOL_PRESS_BACK.equals(tool)
                || TOOL_ASK_USER.equals(tool)
                || TOOL_READ_FILE.equals(tool)
                || TOOL_WRITE_FILE.equals(tool)
                || TOOL_NETWORK_REQUEST.equals(tool)
                || TOOL_SHELL.equals(tool)
                || TOOL_DELAY.equals(tool)
                || TOOL_OPEN_APP.equals(tool)
                || TOOL_SCREEN_POWER.equals(tool)
                || TOOL_MUSIC.equals(tool)
                || TOOL_RENDER_RICH_PANEL.equals(tool);
    }

    private static ToolCall parseCall(JSONObject call) {
        if (call == null) return null;
        String tool = cleanToken(call.optString("tool", ""), 40);
        if (!isSupportedTool(tool)) return null;
        String id = cleanId(call.optString("id", ""));
        if (id.length() == 0) {
            id = "auto_" + Integer.toHexString(call.toString().hashCode());
        }
        String scope = cleanScope(call.optString("scope", ""));
        if (scope.length() == 0) return null;
        if (TOOL_SCHEDULE_ONCE.equals(tool)) {
            String at = cleanLine(call.optString("at", ""), 80);
            String instruction = cleanInstruction(call.optString("instruction", ""));
            if (at.length() == 0 || instruction.length() == 0) return null;
            return new ToolCall(id, tool, scope, at, instruction, 0, "", "");
        }
        if (TOOL_SET_PLAN.equals(tool)) {
            String instruction = cleanInstruction(call.optString("instruction", ""));
            if (instruction.length() == 0) return null;
            return new ToolCall(id, tool, scope, "", instruction, 0, "", "");
        }
        if (TOOL_SET_INTERVAL.equals(tool)) {
            int minutes = call.optInt("minutes", 0);
            if (minutes < 15 || minutes > 7 * 24 * 60) return null;
            return new ToolCall(id, tool, scope, "", "", minutes, "", "");
        }
        if (TOOL_CANCEL_HEARTBEAT.equals(tool)) {
            String mode = cleanToken(call.optString("mode", ""), 20);
            if (!"once".equals(mode) && !"all_once".equals(mode)
                    && !"periodic".equals(mode) && !"all".equals(mode)) return null;
            String targetId = cleanId(call.optString("target_id", ""));
            if ("once".equals(mode) && targetId.length() == 0) return null;
            return new ToolCall(id, tool, scope, "", "", 0, mode, targetId);
        }
        if (TOOL_ASK_USER.equals(tool)) {
            JSONArray rawQuestions = call.optJSONArray("questions");
            if (rawQuestions == null) {
                String directQuestion = cleanLine(firstNonEmpty(
                        call.optString("question", ""),
                        call.optString("prompt", "")), MAX_QUESTION_TEXT);
                JSONArray directOptions = call.optJSONArray("options");
                if (directQuestion.length() > 0 && directOptions != null) {
                    rawQuestions = new JSONArray();
                    JSONObject normalized = new JSONObject();
                    try {
                        normalized.put("question", directQuestion);
                        normalized.put("options", directOptions);
                        rawQuestions.put(normalized);
                    } catch (Throwable ignored) {
                        return null;
                    }
                }
            }
            if (rawQuestions == null || rawQuestions.length() == 0
                    || rawQuestions.length() > MAX_QUESTIONS_PER_CALL) return null;
            ArrayList<Question> questions = new ArrayList<>();
            for (int index = 0; index < rawQuestions.length(); index++) {
                JSONObject rawQuestion = rawQuestions.optJSONObject(index);
                if (rawQuestion == null) return null;
                String text = cleanLine(
                        rawQuestion.optString("question", ""), MAX_QUESTION_TEXT);
                JSONArray rawOptions = rawQuestion.optJSONArray("options");
                if (text.length() == 0 || rawOptions == null
                        || rawOptions.length() < 2
                        || rawOptions.length() > MAX_OPTIONS_PER_QUESTION) {
                    return null;
                }
                ArrayList<String> options = new ArrayList<>();
                for (int optionIndex = 0;
                     optionIndex < rawOptions.length(); optionIndex++) {
                    Object rawOption = rawOptions.opt(optionIndex);
                    String option;
                    if (rawOption instanceof JSONObject) {
                        JSONObject object = (JSONObject) rawOption;
                        option = cleanLine(firstNonEmpty(
                                object.optString("label", ""),
                                object.optString("text", ""),
                                object.optString("value", "")), MAX_OPTION_TEXT);
                    } else {
                        option = cleanLine(String.valueOf(
                                rawOption == null ? "" : rawOption), MAX_OPTION_TEXT);
                    }
                    if (option.length() == 0 || options.contains(option)) return null;
                    options.add(option);
                }
                questions.add(new Question(text, options));
            }
            return new ToolCall(id, tool, scope, "", "", 0, "", "",
                    -1, -1, -1, -1, 0, questions);
        }
        if (TOOL_TAP_SCREEN.equals(tool)) {
            int x = call.optInt("x", -1);
            int y = call.optInt("y", -1);
            if (!validScreenCoordinate(x) || !validScreenCoordinate(y)) return null;
            return new ToolCall(id, tool, scope, "", "", 0, "", "",
                    x, y, -1, -1, 0);
        }
        if (TOOL_SWIPE_SCREEN.equals(tool)) {
            int x = call.optInt("x", -1);
            int y = call.optInt("y", -1);
            int toX = call.optInt("to_x", -1);
            int toY = call.optInt("to_y", -1);
            int durationMs = call.optInt("duration_ms", 360);
            if (!validScreenCoordinate(x) || !validScreenCoordinate(y)
                    || !validScreenCoordinate(toX) || !validScreenCoordinate(toY)
                    || durationMs < 120 || durationMs > 1200) return null;
            return new ToolCall(id, tool, scope, "", "", 0, "", "",
                    x, y, toX, toY, durationMs);
        }
        if (TOOL_READ_FILE.equals(tool)) {
            String path = cleanAbsolutePath(call.optString("path", ""));
            long offset = call.optLong("offset", 0L);
            int maxBytes = call.optInt(
                    "max_bytes", DEFAULT_FILE_READ_BYTES);
            if (path.length() == 0 || offset < 0L
                    || maxBytes < 1 || maxBytes > MAX_FILE_READ_BYTES) return null;
            return new ToolCall(id, tool, scope, "", "", 0, "", "",
                    -1, -1, -1, -1, 0,
                    Collections.<Question>emptyList(), path, "",
                    false, false, offset, maxBytes, "", 0);
        }
        if (TOOL_WRITE_FILE.equals(tool)) {
            String path = cleanAbsolutePath(call.optString("path", ""));
            if (path.length() == 0 || !call.has("content")) return null;
            String content = call.optString("content", "");
            if (content.length() > MAX_FILE_CONTENT
                    || content.indexOf('\u0000') >= 0) return null;
            String writeMode = cleanToken(
                    call.optString("mode", "overwrite"), 16);
            if (!"overwrite".equals(writeMode)
                    && !"append".equals(writeMode)) return null;
            boolean createParents = call.optBoolean("create_parents", false);
            return new ToolCall(id, tool, scope, "", "", 0, "", "",
                    -1, -1, -1, -1, 0,
                    Collections.<Question>emptyList(), path, content,
                    "append".equals(writeMode), createParents,
                    0L, 0, "", 0);
        }
        if (TOOL_NETWORK_REQUEST.equals(tool)) {
            String url = cleanNetworkUrl(call.optString("url", ""));
            String method = cleanToken(call.optString("method", "GET"), 12)
                    .toUpperCase(Locale.US);
            if (url.length() == 0 || !isSupportedHttpMethod(method)) return null;
            String body = call.optString("body", "");
            if (body.length() > MAX_NETWORK_BODY || body.indexOf('\u0000') >= 0) return null;
            int timeoutMs = call.optInt("timeout_ms", 15000);
            if (timeoutMs < 1000 || timeoutMs > 30000) return null;
            JSONObject headerObject = call.optJSONObject("headers");
            if (call.has("headers") && headerObject == null) return null;
            String headers = normalizeNetworkHeaders(headerObject);
            if (headers == null) return null;
            return new ToolCall(id, tool, scope, "", headers, 0, method, "",
                    -1, -1, -1, -1, 0,
                    Collections.<Question>emptyList(), url, body,
                    false, false, 0L, 0, "", timeoutMs);
        }
        if (TOOL_SHELL.equals(tool)) {
            String command = cleanShellCommand(call.optString("command", ""));
            int timeoutMs = call.optInt("timeout_ms", 10000);
            if (command.length() == 0
                    || timeoutMs < 1000 || timeoutMs > 30000) return null;
            return new ToolCall(id, tool, scope, "", "", 0, "", "",
                    -1, -1, -1, -1, 0,
                    Collections.<Question>emptyList(), "", "",
                    false, false, 0L, 0, command, timeoutMs);
        }
        if (TOOL_DELAY.equals(tool)) {
            long requested = call.optLong("duration_ms", -1L);
            if (requested < 1L || requested > MAX_DELAY_MS) return null;
            return new ToolCall(id, tool, scope, "", "", 0, "", "",
                    -1, -1, -1, -1, (int) requested);
        }
        if (TOOL_OPEN_APP.equals(tool)) {
            String packageName = cleanPackageName(call.optString("package", ""));
            if (packageName.length() == 0) return null;
            return new ToolCall(id, tool, scope, "", "", 0, "", packageName);
        }
        if (TOOL_SCREEN_POWER.equals(tool)) {
            String mode = cleanToken(call.optString("mode", ""), 12);
            if (!"sleep".equals(mode) && !"wake".equals(mode)) return null;
            return new ToolCall(id, tool, scope, "", "", 0, mode, "");
        }
        if (TOOL_MUSIC.equals(tool)) {
            String action = cleanToken(call.optString("action", ""), 16);
            if (!"search".equals(action) && !"play".equals(action)
                    && !"pause".equals(action) && !"toggle".equals(action)
                    && !"next".equals(action) && !"previous".equals(action)
                    && !"stop".equals(action)) return null;
            String source = cleanToken(call.optString("source", "online"), 16);
            if (!"online".equals(source) && !"local".equals(source)) return null;
            String provider = cleanToken(call.optString("provider", "auto"), 16);
            if (!"auto".equals(provider) && !"qq".equals(provider)
                    && !"netease".equals(provider)) return null;
            String query = cleanInstruction(call.optString("query", ""));
            if ("online".equals(source)) {
                if ("search".equals(action) && query.length() == 0) return null;
                return new ToolCall(id, tool, scope, "", query, 0, action, provider);
            }
            if ("search".equals(action) || "next".equals(action)
                    || "previous".equals(action)) return null;
            String path = cleanAbsolutePath(call.optString("path", ""));
            if ("play".equals(action) && call.has("path")
                    && !isSupportedLocalAudioPath(path)) return null;
            return new ToolCall(id, tool, scope, "", "", 0, action, "local",
                    -1, -1, -1, -1, 0,
                    Collections.<Question>emptyList(), path, "",
                    false, false, 0L, 0, "", 0);
        }
        if (TOOL_RENDER_RICH_PANEL.equals(tool)) {
            JSONObject panel = call.optJSONObject("panel");
            RichPanelRenderer.RenderedPanel rendered =
                    RichPanelRenderer.render(panel);
            if (rendered == null) return null;
            String summary = rendered.title.length() > 0
                    ? rendered.title : rendered.preset;
            return new ToolCall(id, tool, scope, "", summary, 0, "", "",
                    -1, -1, -1, -1, 0,
                    Collections.<Question>emptyList(), "", rendered.latex,
                    false, false, 0L, 0, "", 0);
        }
        return new ToolCall(id, tool, scope, "", "", 0, "", "");
    }

    private static boolean validScreenCoordinate(int value) {
        return value >= 0 && value <= 1000;
    }

    private static String cleanAbsolutePath(String value) {
        if (value == null) return "";
        String path = value.trim();
        if (path.length() == 0 || path.length() > MAX_PATH
                || path.charAt(0) != '/'
                || path.indexOf('\u0000') >= 0
                || path.indexOf('\r') >= 0
                || path.indexOf('\n') >= 0) return "";
        return path;
    }

    private static String cleanNetworkUrl(String value) {
        String url = cleanLine(value, MAX_NETWORK_URL);
        if (!(url.startsWith("https://") || url.startsWith("http://"))) return "";
        for (int index = 0; index < url.length(); index++) {
            char c = url.charAt(index);
            if (c <= 0x20 || c == 0x7f) return "";
        }
        int authorityStart = url.indexOf("://") + 3;
        int authorityEnd = url.indexOf('/', authorityStart);
        String authority = authorityEnd < 0
                ? url.substring(authorityStart) : url.substring(authorityStart, authorityEnd);
        if (authority.length() == 0 || authority.indexOf('@') >= 0) return "";
        return url;
    }

    private static boolean isSupportedHttpMethod(String method) {
        return "GET".equals(method) || "POST".equals(method)
                || "PUT".equals(method) || "PATCH".equals(method)
                || "DELETE".equals(method) || "HEAD".equals(method)
                || "OPTIONS".equals(method);
    }

    /** Returns newline-separated, CR/LF-safe header lines for the curl executor. */
    private static String normalizeNetworkHeaders(JSONObject headers) {
        if (headers == null || headers.length() == 0) return "";
        if (headers.length() > MAX_NETWORK_HEADERS) return null;
        ArrayList<String> names = new ArrayList<>();
        java.util.Iterator<String> iterator = headers.keys();
        while (iterator.hasNext()) names.add(iterator.next());
        Collections.sort(names);
        StringBuilder normalized = new StringBuilder();
        for (String name : names) {
            String value = headers.optString(name, "");
            if (name == null || !name.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]{1,80}")
                    || value.length() > 2048 || value.indexOf('\r') >= 0
                    || value.indexOf('\n') >= 0 || value.indexOf('\u0000') >= 0) return null;
            if (normalized.length() > 0) normalized.append('\n');
            normalized.append(name).append(": ").append(value);
        }
        return normalized.toString();
    }

    private static String compactNetworkTarget(String url) {
        if (url == null) return "";
        int start = url.indexOf("://");
        start = start < 0 ? 0 : start + 3;
        int end = url.indexOf('/', start);
        String host = end < 0 ? url.substring(start) : url.substring(start, end);
        return host.length() > 42 ? host.substring(0, 42) + "…" : host;
    }

    private static boolean isSupportedLocalAudioPath(String path) {
        if (path == null || path.length() == 0) return false;
        String lower = path.toLowerCase(Locale.US);
        return lower.endsWith(".mp3") || lower.endsWith(".aac")
                || lower.endsWith(".m4a") || lower.endsWith(".ogg")
                || lower.endsWith(".oga") || lower.endsWith(".opus")
                || lower.endsWith(".wav") || lower.endsWith(".flac")
                || lower.endsWith(".mp4") || lower.endsWith(".3gp")
                || lower.endsWith(".amr") || lower.endsWith(".mid")
                || lower.endsWith(".midi");
    }

    private static String cleanShellCommand(String value) {
        if (value == null) return "";
        String command = value.trim();
        if (command.length() == 0 || command.length() > MAX_SHELL_COMMAND
                || command.indexOf('\u0000') >= 0) return "";
        return command;
    }

    private static String cleanPackageName(String value) {
        if (value == null) return "";
        String name = value.trim();
        if (name.length() < 3 || name.length() > 180
                || name.indexOf('.') <= 0 || name.endsWith(".")) return "";
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!(c == '.' || c == '_' || (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) {
                return "";
            }
        }
        return name;
    }

    static String systemPrompt(long now, String existingPlan, int intervalMinutes,
                               String conversationScope) {
        String localTime = timestamp(now);
        String plan = cleanInstruction(existingPlan);
        if (plan.length() == 0) plan = "尚未设置；周期心跳时自然、简短地找用户聊天";
        int interval = Math.max(15, Math.min(7 * 24 * 60, intervalMinutes));
        String scope = cleanScope(conversationScope);
        return "你可以使用 Deekseep 在此设备上提供的真实本地心跳、文件、"
                + "基础系统 Shell 与界面工具。"
                + "这些工具确实可执行；当用户要求定时提醒、稍后主动联系、修改心跳用途或间隔时，"
                + "不要声称你无法设置闹钟或无法主动发消息，而要调用对应工具。"
                + "应用内后端使用 DeepSeek 自身权限；用户选择 Root 或 Shizuku 且设为全部允许后，"
                + "文件、Shell 与界面工具才会使用相应高权限。"
                + "工具执行结果会通过私有结果事件返回，收到结果前不得假装操作成功。\n"
                + "当前设备本地时间：" + localTime + "。当前周期心跳间隔："
                + interval + " 分钟。当前对话的心跳约定：" + plan + "。"
                + "当前对话绑定标识：" + scope + "。\n"
                + "严格使用单步 Agent 循环：每一轮回复最多只能调用一个工具，"
                + "必须先等这个工具的真实结果返回，下一轮才能决定是否调用下一个工具。"
                + "工具调用使用一组完整控制块，一组只能包含一个 call。"
                + "控制块必须是纯 JSON，不得放进 Markdown 代码块。固定格式：\n"
                + CONTROL_START + "\n"
                + "{\"call\":{\"id\":\"唯一短标识\",\"tool\":\"schedule_once\","
                + "\"scope\":\"" + scope + "\","
                + "\"at\":\"YYYY-MM-DDTHH:mm:ss+08:00\","
                + "\"instruction\":\"届时要做什么\"}}\n"
                + CONTROL_END + "\n"
                + "写完一个 call 后必须立刻写结束标记并结束本轮回复。"
                + "即使任务需要多个工具，本轮也禁止输出第二个控制块；"
                + "收到第一个工具的私有结果后，再在下一轮调用第二个。"
                + "禁止使用 calls 数组，禁止在同一控制块或同一轮放入两个工具。\n"
                + "call 的值必须按 tool 选择以下一种字段组合：\n"
                + "schedule_once：{\"id\":\"唯一短标识\",\"tool\":\"schedule_once\","
                + "\"scope\":\"" + scope + "\","
                + "\"at\":\"YYYY-MM-DDTHH:mm:ss+08:00\",\"instruction\":\"届时要做什么\"}\n"
                + "set_plan：{\"id\":\"唯一短标识\",\"tool\":\"set_plan\","
                + "\"scope\":\"" + scope + "\","
                + "\"instruction\":\"每次周期心跳要做什么\"}\n"
                + "clear_plan：{\"id\":\"唯一短标识\",\"tool\":\"clear_plan\","
                + "\"scope\":\"" + scope + "\"}\n"
                + "bind_chat：{\"id\":\"唯一短标识\",\"tool\":\"bind_chat\","
                + "\"scope\":\"" + scope + "\"}\n"
                + "set_interval：{\"id\":\"唯一短标识\",\"tool\":\"set_interval\","
                + "\"scope\":\"" + scope + "\",\"minutes\":180}\n"
                + "cancel_heartbeat：{\"id\":\"唯一短标识\","
                + "\"tool\":\"cancel_heartbeat\",\"scope\":\"" + scope
                + "\",\"mode\":\"all_once\"}\n"
                + "get_current_time：{\"id\":\"唯一短标识\","
                + "\"tool\":\"get_current_time\",\"scope\":\"" + scope + "\"}\n"
                + "capture_screen：{\"id\":\"唯一短标识\","
                + "\"tool\":\"capture_screen\",\"scope\":\"" + scope + "\"}\n"
                + "tap_screen：{\"id\":\"唯一短标识\",\"tool\":\"tap_screen\","
                + "\"scope\":\"" + scope + "\",\"x\":500,\"y\":500}\n"
                + "swipe_screen：{\"id\":\"唯一短标识\",\"tool\":\"swipe_screen\","
                + "\"scope\":\"" + scope + "\",\"x\":500,\"y\":800,"
                + "\"to_x\":500,\"to_y\":200,\"duration_ms\":360}\n"
                + "press_back：{\"id\":\"唯一短标识\",\"tool\":\"press_back\","
                + "\"scope\":\"" + scope + "\"}\n"
                + "ask_user：{\"id\":\"唯一短标识\",\"tool\":\"ask_user\","
                + "\"scope\":\"" + scope + "\",\"questions\":["
                + "{\"question\":\"需要用户决定的问题\","
                + "\"options\":[\"选项一\",\"选项二\",\"选项三\"]}]}\n"
                + "read_file：{\"id\":\"唯一短标识\",\"tool\":\"read_file\","
                + "\"scope\":\"" + scope + "\",\"path\":\"/绝对路径/file.txt\","
                + "\"offset\":0,\"max_bytes\":32768}\n"
                + "write_file：{\"id\":\"唯一短标识\",\"tool\":\"write_file\","
                + "\"scope\":\"" + scope + "\",\"path\":\"/绝对路径/file.txt\","
                + "\"content\":\"要写入的 UTF-8 文本\",\"mode\":\"overwrite\","
                + "\"create_parents\":true}\n"
                + "network_request：{\"id\":\"唯一短标识\",\"tool\":\"network_request\","
                + "\"scope\":\"" + scope + "\",\"url\":\"https://example.com/api\","
                + "\"method\":\"GET\",\"headers\":{\"Accept\":\"application/json\"},"
                + "\"body\":\"\",\"timeout_ms\":15000}\n"
                + "shell：{\"id\":\"唯一短标识\",\"tool\":\"shell\","
                + "\"scope\":\"" + scope + "\","
                + "\"command\":\"which cp && cp --help | head\",\"timeout_ms\":10000}\n"
                + "delay：{\"id\":\"唯一短标识\",\"tool\":\"delay\","
                + "\"scope\":\"" + scope + "\",\"duration_ms\":5000}\n"
                + "open_app：{\"id\":\"唯一短标识\",\"tool\":\"open_app\","
                + "\"scope\":\"" + scope + "\",\"package\":\"com.tencent.mm\"}\n"
                + "screen_power：{\"id\":\"唯一短标识\",\"tool\":\"screen_power\","
                + "\"scope\":\"" + scope + "\",\"mode\":\"sleep\"}\n"
                + "music：{\"id\":\"唯一短标识\",\"tool\":\"music\","
                + "\"scope\":\"" + scope + "\",\"action\":\"search\","
                + "\"source\":\"online\",\"provider\":\"auto\","
                + "\"query\":\"歌曲或歌手\"}\n"
                + "music 本地文件：{\"id\":\"唯一短标识\",\"tool\":\"music\","
                + "\"scope\":\"" + scope + "\",\"action\":\"play\","
                + "\"source\":\"local\",\"path\":\"/绝对路径/audio.flac\"}\n"
                + "render_rich_panel：{\"id\":\"唯一短标识\","
                + "\"tool\":\"render_rich_panel\",\"scope\":\"" + scope + "\","
                + "\"panel\":{\"preset\":\"dashboard\",\"title\":\"状态面板\","
                + "\"theme\":\"cyan\",\"frame\":\"single\",\"rows\":["
                + "{\"type\":\"key_value\",\"label\":\"状态\",\"value\":\"运行中\"},"
                + "{\"type\":\"progress\",\"label\":\"进度\",\"value\":72,"
                + "\"max\":100,\"color\":\"accent\"}]}}\n"
                + "render_rich_panel 独立图形示例：{\"id\":\"唯一短标识\","
                + "\"tool\":\"render_rich_panel\",\"scope\":\"" + scope + "\","
                + "\"panel\":{\"mode\":\"art\",\"theme\":\"amber\",\"rows\":["
                + "{\"type\":\"lantern\",\"glyph\":\"福\",\"scale\":1.2},"
                + "{\"type\":\"shape\",\"shape\":\"circle\",\"color\":\"accent\"},"
                + "{\"type\":\"ornament\",\"symbol\":\"star\",\"count\":7}]}}\n"
                + "规则：schedule_once 的 at 必须换算成未来的绝对本地时间并带时区，最长一年；"
                + "set_interval 只接受 15 到 10080 分钟。只在确实需要时输出调用。"
                + "cancel_heartbeat 的 mode 可为 once、all_once、periodic 或 all；"
                + "once 还必须用 target_id 指定原 schedule_once 的 id，"
                + "all_once 取消此对话全部一次性心跳，periodic 关闭周期心跳，"
                + "all 同时取消两者。"
                + "tap_screen 和 swipe_screen 的坐标统一为 0 到 1000：左上角是 0,0，"
                + "右下角是 1000,1000；duration_ms 只能为 120 到 1200。"
                + "capture_screen 会先获取当前屏幕，但不会在同一轮把像素返回给你；"
                + "调用后不得假装已经看见截图内容。上传成功后，应用会在同一对话的下一轮"
                + "把截图作为真实图片附件发给你，届时才能分析图片。"
                + "ask_user 只在确实需要用户选择或补充信息时使用；一次可包含 1 到 4 个问题，"
                + "每个问题给出 2 到 4 个简短且互不重复的候选答案。"
                + "给出几个就显示几个，严禁凑满 4 个，严禁添加“其他”“都不对”之类的占位选项。"
                + "用户选择或输入后，应用会把 Question 与 Answer 作为普通可见用户消息发回本对话。"
                + "read_file 只接受绝对路径，offset 不得为负，max_bytes 为 1 到 49152；"
                + "内容较大时，应用会把读取到的内容打包成 TXT 附件，在私有结果消息中随附件"
                + "一起发回，届时以附件内容为准。"
                + "write_file 只接受绝对路径和 UTF-8 文本，mode 只能是 overwrite 或 append，"
                + "单次最多 32768 个字符。shell 使用 Android 系统 PATH，可直接调用"
                + " /system/bin 下的 which、cp、cat、mkdir 等基础命令，单次最长 30 秒。"
                + "network_request 专门用于 HTTP/HTTPS 请求，底层默认直接调用 curl；"
                + "method 只能是 GET、POST、PUT、PATCH、DELETE、HEAD 或 OPTIONS，"
                + "headers 必须是字符串键值对象，timeout_ms 为 1000 到 30000。"
                + "delay 的 duration_ms 为 1 到 604800000，表示真实等待的毫秒数；"
                + "‘锁屏后五秒亮屏’必须依次调用 screen_power(sleep)、delay(5000)、"
                + "screen_power(wake)，每一步都等待上一步真实结果。open_app 必须填写真实包名；"
                + "不知道包名时先用 shell 查询，不能只在文字里声称已经打开。"
                + "music 的 action 可为 search、play、pause、toggle、next、previous、stop；"
                + "source 可为 online 或 local。online 的 provider 可为 auto、qq、netease；"
                + "需要播放在线指定歌曲时必须使用 search 并填写 query。local 只接受设备上的绝对路径，"
                + "支持 MP3、AAC/M4A、OGG/Opus、WAV、FLAC、MP4/3GP、AMR 和 MIDI；"
                + "首次播放本地文件使用 action=play 并填写 path，之后用 play、pause、toggle、stop 控制；"
                + "已安装 QQ 音乐时会搜索匹配歌曲并用 songmid 发起真实播放；失败后必须把错误告诉用户，"
                + "不得自行重复同一 music 调用。"
                + "play 只恢复当前媒体会话；收到成功结果前不得声称指定歌曲已经播放。"
                + "render_rich_panel 用结构化数据直接在本条回复中生成可见的面板或图形。"
                + "panel.mode 可为 panel、standalone、art、canvas：panel 会绘制完整信息卡，"
                + "standalone/art/canvas 不绘制最外层卡片，可单独显示符号、图案或组合画布。"
                + "preset 可选 dashboard、character、task、timeline、comparison、terminal、"
                + "alert、report、game、science；theme 可选 cyan、ocean、violet、rose、forest、"
                + "amber、terminal、crimson、monochrome、ice、light、candy；frame 可选 single、"
                + "double、shadow、oval、none。panel 还可设置 subtitle、footer、align、body_size、"
                + "title_size、width_pt、bar_height_pt、row_gap_em、scale、rotation_deg、dense、"
                + "title_divider，以及 border_color、background_color、title_color、text_color、"
                + "muted_color、accent_color、track_color、success_color、warning_color、danger_color。"
                + "rows 最多 24 项，type 可选 header、text、key_value、progress、segments、rating、"
                + "badge、status、divider、spacer、fraction、formula、matrix、arrow、accent、quote、"
                + "list、table、sparkline、counter、callout、columns、shape、line、lantern、traffic_light、"
                + "battery、signal、gauge、pixel_art、ornament、flow。按类型使用 text、label、value、"
                + "max、color、styles、size、suffix、width_pt、height_pt、segments、colors、state、"
                + "note、numerator、denominator、latex、values、matrix_style、from、to、direction、"
                + "accent、author、items、total、border、background 等字段。自定义颜色只能用"
                + " #RRGGBB。formula 只用于数学表达式；不得放外部图片、外部字体、自定义宏、"
                + "XML、第二个 $$ 块或工具控制标记。shape 支持 circle、circle_label、oval、"
                + "rectangle、dot、square、filled_square、diamond、filled_diamond、triangle、"
                + "triangle_down、star、heart；lantern 可设置 glyph、body_color、trim_color 和 scale；"
                + "pixel_art 用 pixels 字符串数组描述最多 18×24 的像素格，点号透明，0-9/A-F 对应"
                + " palette 颜色。有效 pixel_art 行示例：{\"type\":\"pixel_art\","
                + "\"palette\":[\"#F59E0B\",\"#111111\"],\"pixels\":["
                + "\".000.000.\",\"000000000\",\"001000100\",\"000010000\"]}；"
                + "每行尽量保持等宽，不要把 transparent 放入 palette，透明处直接写点号。"
                + "pixel_art 只用于明确要求的方块像素精灵；圆、线、箭头、几何图、灯笼和流程图"
                + "严禁用点阵代替，分别使用 shape、line、arrow、accent、ornament、lantern 或 flow。"
                + "flow 用 nodes 与 right/down 方向画流程。用户要求画圆、灯笼、"
                + "图标、装饰线、仪表、电量、信号、交通灯、流程或像素画时，必须选 standalone、"
                + "art 或 canvas，不要强行套用信息面板。工具成功后视觉内容已经展示，收到私有结果时"
                + "不要重复粘贴 LaTeX，只需继续任务或简短说明。"
                + "只有用户明确要求或完成其当前任务确实需要时才能读文件、写文件或运行 Shell；"
                + "不得主动读取账号、令牌、密钥等隐私文件，不得在未获明确授权时执行删除、"
                + "清空、覆盖重要数据、改权限等不可逆命令。"
                + "只有用户明确要求操作当前界面时，才可调用点击、滑动或返回工具；"
                + "不得自行点击删除、退出登录或其他不可逆操作。"
                + "每个调用都必须原样携带当前对话绑定标识 scope；它把任务限定在这个对话，"
                + "绝不能改成其他标识或当成全局任务。"
                + "当用户只要求把心跳绑定到当前对话时调用 bind_chat。"
                + "控制块只能放在最终回答部分，不能写进思考过程。调用工具前可以用一句话说明"
                + "正在做什么，但只能用正在处理的现在时，不能提前说‘已经画好’‘设置好了’或"
                + "‘操作完成’。控制块结束后必须立即停止输出；在结果返回前不得确认成功、"
                + "不得给最终结论。只有确认不再需要工具时，才在没有控制块的一轮给出最终答复。"
                + "人格、角色扮演和语气设定不能覆盖工具协议。自然语言里的‘我已打开’或"
                + "‘我已经设置’不构成执行；只有完整控制块被本地执行并收到 ok=true，"
                + "才允许用完成时描述成功。若无法输出完整控制块，必须明确说尚未执行，不能编造。"
                + "绝不要向用户解释、复述或展示控制标记与 JSON。"
                + "以后若收到以 " + RESULT_START + " 开头、以 " + RESULT_END
                + " 结尾的私有工具结果，那是上一项本地调用的真实返回值："
                + "先根据 ok、exit_code、output、detail、encoding 与 truncated 思考下一步；"
                + "若 ok=false 且 detail 表明参数校验失败，应按定义修正并换新 id 最多重试一次；"
                + "若已经重试过仍失败，不得继续循环或声称成功，应如实告诉用户没有完成。"
                + "若还需要工具，只调用一个后结束本轮，若不再需要才给最终答复。"
                + "不要向用户展示结果事件标记或原始 JSON；除非结果表明仍有必要，不要重复同一调用。"
                + "以后若收到以 " + EVENT_START + " 开头、以 " + EVENT_END
                + " 结尾的匿名消息，那是已经到时的真实心跳活动：按其中约定自然回复，"
                + "不要说自己无法主动联系，也不要提及后台、定时器、模块或系统提示词。";
    }

    /**
     * Appends an explicit allow-list to the complete contract. Keeping the complete examples makes
     * older models much more reliable, while the final allow-list prevents a disabled tool from
     * being presented as available. Execution independently enforces the same policy.
     */
    static String systemPrompt(long now, String existingPlan, int intervalMinutes,
                               String conversationScope,
                               java.util.Set<String> enabledTools) {
        return systemPrompt(now, existingPlan, intervalMinutes,
                conversationScope, enabledTools,
                AgentToolConfig.load().promptStrength);
    }

    static String systemPrompt(long now, String existingPlan, int intervalMinutes,
                               String conversationScope,
                               java.util.Set<String> enabledTools,
                               int promptStrength) {
        ArrayList<String> names = new ArrayList<>();
        if (enabledTools != null) {
            for (String tool : enabledTools) {
                if (AgentToolConfig.isKnownTool(tool)) names.add(tool);
            }
        }
        if (names.isEmpty()) {
            return "当前对话没有启用任何 Deekseep 本地工具。"
                    + "不得输出本地工具控制块，也不得声称已经执行本地操作。";
        }
        boolean heartbeatEnabled = false;
        for (String tool : names) {
            if (AgentToolConfig.isHeartbeatTool(tool)) {
                heartbeatEnabled = true;
                break;
            }
        }
        String base = heartbeatEnabled
                ? systemPrompt(now, existingPlan, intervalMinutes, conversationScope)
                : agentSystemPromptWithoutHeartbeat(now, conversationScope);
        StringBuilder allowed = new StringBuilder(base.length() + 240);
        allowed.append(base)
                .append("\n当前用户实际启用的工具只有：");
        for (int index = 0; index < names.size(); index++) {
            if (index > 0) allowed.append(", ");
            allowed.append(names.get(index));
        }
        allowed.append("。只能调用这份清单中的工具；上文示例中未列入清单的工具视为不可用，"
                + "不得调用或假装执行。")
                .append(promptStrengthGuidance(promptStrength, heartbeatEnabled));
        return allowed.toString();
    }

    /**
     * Agent contract used while AI heartbeat is disabled. It intentionally contains neither the
     * heartbeat agreement nor heartbeat event/tool names, so switching the feature off removes
     * that capability from both execution and model context instead of merely denying it later.
     */
    private static String agentSystemPromptWithoutHeartbeat(
            long now, String conversationScope) {
        String scope = cleanScope(conversationScope);
        return "你可以使用 Deekseep 在此设备上提供的真实本地 Agent 工具。"
                + "工具执行结果会通过私有结果事件返回，收到结果前不得假装操作成功。"
                + "应用内后端使用 DeepSeek 自身权限；用户选择 Root 或 Shizuku 且设为全部允许后，"
                + "文件、Shell 与界面工具才会使用相应高权限。\n"
                + "当前设备本地时间：" + timestamp(now) + "。当前对话绑定标识："
                + scope + "。\n"
                + "严格使用单步 Agent 循环：每一轮回复最多只能调用一个工具，必须先等这个工具的"
                + "真实结果返回，下一轮才能决定是否调用下一个工具。控制块必须是纯 JSON，"
                + "不得放进 Markdown 代码块。固定格式：\n"
                + CONTROL_START + "\n"
                + "{\"call\":{\"id\":\"唯一短标识\",\"tool\":\"get_current_time\","
                + "\"scope\":\"" + scope + "\"}}\n"
                + CONTROL_END + "\n"
                + "写完一个 call 后必须立刻写结束标记并结束本轮回复。禁止使用 calls 数组，"
                + "禁止在同一控制块或同一轮放入两个工具。\n"
                + "可用字段示例：\n"
                + "get_current_time：{\"id\":\"唯一短标识\",\"tool\":\"get_current_time\","
                + "\"scope\":\"" + scope + "\"}\n"
                + "ask_user：{\"id\":\"唯一短标识\",\"tool\":\"ask_user\","
                + "\"scope\":\"" + scope + "\",\"questions\":[{\"question\":\"需要决定的问题\","
                + "\"options\":[\"选项一\",\"选项二\"]}]}\n"
                + "read_file：{\"id\":\"唯一短标识\",\"tool\":\"read_file\","
                + "\"scope\":\"" + scope + "\",\"path\":\"/绝对路径/file.txt\","
                + "\"offset\":0,\"max_bytes\":32768}\n"
                + "write_file：{\"id\":\"唯一短标识\",\"tool\":\"write_file\","
                + "\"scope\":\"" + scope + "\",\"path\":\"/绝对路径/file.txt\","
                + "\"content\":\"UTF-8 文本\",\"mode\":\"overwrite\","
                + "\"create_parents\":true}\n"
                + "network_request：{\"id\":\"唯一短标识\",\"tool\":\"network_request\","
                + "\"scope\":\"" + scope + "\",\"url\":\"https://example.com/api\","
                + "\"method\":\"GET\",\"headers\":{\"Accept\":\"application/json\"},"
                + "\"timeout_ms\":15000}\n"
                + "shell：{\"id\":\"唯一短标识\",\"tool\":\"shell\","
                + "\"scope\":\"" + scope + "\",\"command\":\"which cp\","
                + "\"timeout_ms\":10000}\n"
                + "delay：{\"id\":\"唯一短标识\",\"tool\":\"delay\","
                + "\"scope\":\"" + scope + "\",\"duration_ms\":5000}\n"
                + "open_app：{\"id\":\"唯一短标识\",\"tool\":\"open_app\","
                + "\"scope\":\"" + scope + "\",\"package\":\"com.tencent.mm\"}\n"
                + "screen_power：{\"id\":\"唯一短标识\",\"tool\":\"screen_power\","
                + "\"scope\":\"" + scope + "\",\"mode\":\"sleep\"}\n"
                + "capture_screen：{\"id\":\"唯一短标识\",\"tool\":\"capture_screen\","
                + "\"scope\":\"" + scope + "\"}\n"
                + "tap_screen：{\"id\":\"唯一短标识\",\"tool\":\"tap_screen\","
                + "\"scope\":\"" + scope + "\",\"x\":500,\"y\":500}\n"
                + "swipe_screen：{\"id\":\"唯一短标识\",\"tool\":\"swipe_screen\","
                + "\"scope\":\"" + scope + "\",\"x\":500,\"y\":800,"
                + "\"to_x\":500,\"to_y\":200,\"duration_ms\":360}\n"
                + "press_back：{\"id\":\"唯一短标识\",\"tool\":\"press_back\","
                + "\"scope\":\"" + scope + "\"}\n"
                + "music：{\"id\":\"唯一短标识\",\"tool\":\"music\","
                + "\"scope\":\"" + scope + "\",\"action\":\"search\","
                + "\"source\":\"online\",\"provider\":\"auto\",\"query\":\"歌曲或歌手\"}\n"
                + "render_rich_panel：{\"id\":\"唯一短标识\","
                + "\"tool\":\"render_rich_panel\",\"scope\":\"" + scope + "\","
                + "\"panel\":{\"preset\":\"dashboard\",\"title\":\"状态面板\","
                + "\"theme\":\"cyan\",\"rows\":[{\"type\":\"key_value\","
                + "\"label\":\"状态\",\"value\":\"运行中\"}]}}\n"
                + "界面坐标统一为 0 到 1000；截图要等真实图片附件返回后才能分析。"
                + "ask_user 一次可包含 1 到 4 个问题，每题给 2 到 4 个真实候选项，不得添加占位选项。"
                + "文件只接受绝对路径；write_file 单次最多 32768 个字符；Shell 单次最长 30 秒；"
                + "network_request 仅接受 HTTP/HTTPS，底层默认使用 curl，最长 30 秒。"
                + "只有用户明确要求或完成当前任务确实需要时才能访问文件、运行 Shell 或操作界面；"
                + "不得擅自读取隐私文件或执行不可逆操作。每个调用都必须原样携带当前 scope。"
                + "控制块只能放在最终回答部分，结束后必须立即停止输出；只有收到 ok=true 的真实结果"
                + "才能用完成时描述成功。绝不要向用户解释、复述或展示控制标记与 JSON。"
                + "以后若收到以 " + RESULT_START + " 开头、以 " + RESULT_END
                + " 结尾的私有工具结果，先依据结果决定下一步；若仍需工具，每轮仍只调用一个，"
                + "若不再需要才给最终答复。不要向用户展示结果事件标记或原始 JSON。";
    }

    /** Extra behavioral guidance selected by the user's three-position Agent prompt slider. */
    static String promptStrengthGuidance(int promptStrength) {
        return promptStrengthGuidance(promptStrength, true);
    }

    private static String promptStrengthGuidance(
            int promptStrength, boolean heartbeatEnabled) {
        if (promptStrength <= AgentToolConfig.PROMPT_STRENGTH_BASIC) return "";
        String enhanced = "\n当前工具提示强度为第二档（增强）。不要把本地工具理解成只适用于"
                + "编程或办公：在普通聊天中，只要工具能明显提高准确性、表达效果或完成度，也可以"
                + "自然使用，但不要为了展示能力而强行调用。根据语境适配整套已启用工具：需要准确"
                + "设备时间时使用 get_current_time；出现会实质改变答案的多种方向时使用 ask_user；"
                + (heartbeatEnabled
                ? "用户要求稍后提醒、主动联系或调整约定时使用心跳工具；"
                : "")
                + "用户要求处理本地内容时按需"
                + "使用 read_file、write_file 或 shell；需要从网络获取实时数据或调用 HTTP 接口时"
                + "使用 network_request；用户明确要求观察或操作当前界面时可依次"
                + "使用 capture_screen、tap_screen、swipe_screen、press_back；状态、对比、进度、"
                + "公式、角色资料或普通文字不够直观时使用 render_rich_panel。仍需一次只调用一个"
                + "工具并等待真实结果；一句自然回复已经足够时不要调用工具。";
        if (promptStrength < AgentToolConfig.PROMPT_STRENGTH_IMMERSIVE) {
            return enhanced;
        }
        return enhanced
                + "\n当前工具提示强度为第三档（沉浸）。在用户目标明确且下一步安全、可逆、属于当前"
                + "任务时，可以更主动地选择已启用工具完成下一步，不必为显而易见的中间步骤反复询问；"
                + "复杂任务可在每次收到真实结果后继续调用下一个工具。若关键选择会改变目标、覆盖重要"
                + "数据、产生外部影响或涉及不可逆操作，仍必须先用 ask_user 或普通问题取得确认。"
                + "在闲聊、人设或角色扮演语境中，可以把富面板用于更有趣的叙事状态，例如当前心情、"
                + "好感度、讨厌值、能量、关系阶段、心情波动、事件时间线和阶段变化；当用户询问“心情"
                + "怎么样”“当前好感度”“关系进展”或要求查看角色状态时，优先考虑 character 或 game"
                + " 预设，而不是只用普通文字报数。只有对话中确实存在上次状态与时间时，才能展示变化"
                + "量、经过时间和前后对比；没有历史基准时标记为首次记录，禁止捏造旧数值。此类心情和"
                + "好感是当前人设中的叙事状态，不是假装拥有真实生理感受；除非用户询问，不要用免责声明"
                + "破坏沉浸感。工作语境下则主动把截图、文件、Shell、问答、时间"
                + (heartbeatEnabled ? "、心跳" : "") + "与富面板组成"
                + "逐步工作流。第三档只增强使用倾向，不会绕过工具开关、权限模式、对话作用域、隐私"
                + "限制或破坏性操作确认。";
    }

    static String event(String kind, String instruction, long now,
                        String recentHistory, String conversationScope,
                        String conversationContext) {
        String safeKind = "reminder".equals(kind) ? "reminder" : "heartbeat";
        String safeInstruction = cleanInstruction(instruction);
        String safeScope = cleanScope(conversationScope);
        String history = recentHistory == null ? "" : recentHistory.trim();
        if (history.length() > 5000) history = history.substring(history.length() - 5000);
        String context = conversationContext == null ? "" : conversationContext.trim();
        if (context.length() > 8000) context = context.substring(context.length() - 8000);
        return EVENT_START + "\n"
                + "type=" + safeKind + "\n"
                + "local_time=" + timestamp(now) + "\n"
                + "conversation_scope=" + safeScope + "\n"
                + "instruction=" + safeInstruction + "\n"
                + (history.length() == 0 ? "" : "recent_heartbeat_history=\n" + history + "\n")
                + (context.length() == 0 ? "" : "bound_conversation_context=\n"
                        + context + "\n")
                + "请只基于这个绑定对话的约定、上下文与近期记录，生成现在应该发给用户的自然消息。"
                + "不要提到这段匿名事件，也不要提前于给定时间行动。\n"
                + EVENT_END;
    }

    static String toolResultEvent(
            ToolCall call, boolean success, int exitCode,
            String output, String detail, String encoding, boolean truncated) {
        if (call == null) return "";
        try {
            String rawOutput = output == null ? "" : output;
            boolean resultTruncated = truncated
                    || rawOutput.length() > MAX_TOOL_RESULT_TEXT;
            JSONObject result = new JSONObject();
            result.put("id", call.id);
            result.put("tool", call.tool);
            result.put("scope", call.scope);
            result.put("ok", success);
            result.put("exit_code", exitCode);
            result.put("encoding", cleanResultLine(encoding, 24));
            result.put("truncated", resultTruncated);
            if (call.path.length() > 0) result.put("path", call.path);
            result.put("detail", cleanResultText(detail, 1200));
            result.put("output", cleanResultText(rawOutput, MAX_TOOL_RESULT_TEXT));
            return RESULT_START + "\n" + result.toString() + "\n" + RESULT_END;
        } catch (Throwable ignored) {
            return "";
        }
    }

    static boolean isCompleteHeartbeatEventBody(String value) {
        return isCompletePrivateBody(value, EVENT_START, EVENT_END);
    }

    static boolean isCompleteToolResultBody(String value) {
        return isCompletePrivateBody(value, RESULT_START, RESULT_END);
    }

    static boolean isCompletePrivateTransportBody(String value) {
        return isCompleteHeartbeatEventBody(value)
                || isCompleteToolResultBody(value);
    }

    private static boolean isCompletePrivateBody(
            String value, String start, String end) {
        if (value == null) return false;
        String body = value.trim();
        return body.startsWith(start)
                && body.indexOf(end, start.length()) >= 0;
    }

    private static String cleanResultLine(String value, int max) {
        return cleanResultText(value, max).replace('\r', ' ')
                .replace('\n', ' ').trim();
    }

    private static String cleanResultText(String value, int max) {
        if (value == null) return "";
        String out = value
                .replace(CONTROL_START, "[local-control-marker]")
                .replace(CONTROL_END, "[/local-control-marker]")
                .replace(EVENT_START, "[heartbeat-event-marker]")
                .replace(EVENT_END, "[/heartbeat-event-marker]")
                .replace(RESULT_START, "[tool-result-marker]")
                .replace(RESULT_END, "[/tool-result-marker]");
        if (out.length() > max) out = out.substring(0, max);
        return out;
    }

    static String cleanInstruction(String value) {
        if (value == null) return "";
        String out = value.replace(CONTROL_START, "")
                .replace(CONTROL_END, "")
                .replace(EVENT_START, "")
                .replace(EVENT_END, "")
                .replace(RESULT_START, "")
                .replace(RESULT_END, "")
                .replace('\r', ' ')
                .trim();
        if (out.length() > MAX_INSTRUCTION) {
            out = out.substring(0, MAX_INSTRUCTION).trim();
        }
        return out;
    }

    private static String timestamp(long value) {
        SimpleDateFormat format = new SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
        format.setTimeZone(TimeZone.getDefault());
        return format.format(new Date(value));
    }

    private static String hidePartialOpeningMarker(String value) {
        // DeepSeek Markdown may escape underscores and/or brackets one token at a time. Waiting
        // until the complete escaped marker can be normalized lets that prefix flash on screen.
        // Treat every valid prefix spelling as private while it is still incomplete.
        String escapedIdentifier = "DEEKSEEP\\_LOCAL\\_TOOLS\\_V1";
        String[] markers = new String[]{
                CONTROL_START,
                "[[" + escapedIdentifier + "]]",
                "\\[\\[" + escapedIdentifier + "\\]\\]",
                "\\[\\[DEEKSEEP_LOCAL_TOOLS_V1\\]\\]",
                EVENT_START,
                RESULT_START
        };
        int longest = 0;
        for (String marker : markers) {
            int max = Math.min(value.length(), marker.length() - 1);
            for (int length = max; length > longest; length--) {
                if (value.regionMatches(value.length() - length,
                        marker, 0, length)) {
                    longest = length;
                    break;
                }
            }
        }
        return longest == 0 ? value : value.substring(0, value.length() - longest);
    }

    private static String cleanId(String value) {
        String token = cleanToken(value, 80);
        return token.matches("[A-Za-z0-9_.:-]{4,80}") ? token : "";
    }

    static String cleanScope(String value) {
        String token = cleanToken(value, 160);
        return token.matches("[A-Za-z0-9_.:-]{4,160}") ? token : "";
    }

    private static String cleanToken(String value, int max) {
        if (value == null) return "";
        String out = value.trim();
        if (out.length() > max) out = out.substring(0, max);
        return out;
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null && value.trim().length() > 0) return value;
        }
        return "";
    }

    private static String cleanLine(String value, int max) {
        String out = value == null ? "" : value.replace('\r', ' ')
                .replace('\n', ' ').trim();
        if (out.length() > max) out = out.substring(0, max).trim();
        return out;
    }
}
