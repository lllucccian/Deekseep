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

    private static final int MAX_CONTROL_JSON = 16 * 1024;
    private static final int MAX_CALLS_PER_BLOCK = 8;
    private static final int MAX_INSTRUCTION = 1200;
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
            this.invokedAt = stableInvocationTime(scope, id, tool);
        }
    }

    static final class Result {
        final String visibleText;
        final List<ToolCall> calls;
        final boolean incompleteControlBlock;

        Result(String visibleText, List<ToolCall> calls, boolean incompleteControlBlock) {
            this.visibleText = visibleText == null ? "" : visibleText;
            this.calls = calls == null
                    ? Collections.<ToolCall>emptyList()
                    : Collections.unmodifiableList(calls);
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
            return new Result(value, Collections.<ToolCall>emptyList(), false);
        }
        StringBuilder visible = new StringBuilder(value.length());
        ArrayList<ToolCall> calls = new ArrayList<>();
        boolean incomplete = false;
        int cursor = 0;
        while (cursor < value.length()) {
            int controlStart = value.indexOf(CONTROL_START, cursor);
            int eventStart = value.indexOf(EVENT_START, cursor);
            boolean controlBlock = controlStart >= 0
                    && (eventStart < 0 || controlStart <= eventStart);
            int start = controlBlock ? controlStart : eventStart;
            if (start < 0) {
                visible.append(value, cursor, value.length());
                break;
            }
            visible.append(value, cursor, start);
            String openingMarker = controlBlock ? CONTROL_START : EVENT_START;
            String closingMarker = controlBlock ? CONTROL_END : EVENT_END;
            int payloadStart = start + openingMarker.length();
            int end = value.indexOf(closingMarker, payloadStart);
            if (end < 0) {
                incomplete = true;
                break;
            }
            String payload = value.substring(payloadStart, end).trim();
            if (controlBlock && payload.length() > 0
                    && payload.length() <= MAX_CONTROL_JSON) {
                int firstNewCall = calls.size();
                parsePayload(payload, calls);
                if (renderToolRows && calls.size() > firstNewCall) {
                    appendToolRows(visible, calls, firstNewCall);
                }
            }
            cursor = end + closingMarker.length();
        }
        if (cursor == value.length()) {
            // A control block may end at the final byte, in which case the loop does not append.
        }
        String safe = hidePartialOpeningMarker(visible.toString());
        return new Result(safe, calls, incomplete);
    }

    static String stripControlBlocks(String value) {
        return stripToolStatusStyleMarkers(parse(value).visibleText);
    }

    static String renderConversationToolRows(String value) {
        return parseForConversation(value).visibleText;
    }

    private static void appendToolRows(
            StringBuilder visible, List<ToolCall> calls, int firstCall) {
        if (firstCall < 0 || firstCall >= calls.size()) return;
        appendParagraphBreak(visible);
        boolean chinese = Locale.getDefault().getLanguage().toLowerCase(Locale.US)
                .startsWith("zh");
        for (int index = firstCall; index < calls.size(); index++) {
            if (index > firstCall) visible.append('\n');
            ToolCall call = calls.get(index);
            visible.append("> ")
                    .append(markToolStatus(toolStatusText(call, chinese)));
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
                && value.indexOf("Ask user") < 0) {
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
        } else {
            status = chinese ? "\u5fc3\u8df3\u8bbe\u7f6e" : "Heartbeat settings";
        }
        return statusClock(call.invokedAt) + "  " + status;
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

    private static void parsePayload(String payload, List<ToolCall> out) {
        try {
            JSONObject root = new JSONObject(payload);
            // V1 originally allowed one large {"calls":[...]} batch. Keep accepting it for
            // already-generated/history content, but prefer the streaming-friendly singular
            // envelope taught by systemPrompt(): one complete control block per tool call.
            JSONObject single = root.optJSONObject("call");
            if (single == null && root.has("tool")) single = root;
            if (single != null) {
                ToolCall parsed = parseCall(single);
                if (parsed != null) out.add(parsed);
                return;
            }
            JSONArray array = root.optJSONArray("calls");
            if (array == null) return;
            int count = Math.min(array.length(), MAX_CALLS_PER_BLOCK);
            for (int i = 0; i < count; i++) {
                JSONObject call = array.optJSONObject(i);
                ToolCall parsed = parseCall(call);
                if (parsed != null) out.add(parsed);
            }
        } catch (Throwable ignored) {}
    }

    private static ToolCall parseCall(JSONObject call) {
        if (call == null) return null;
        String tool = cleanToken(call.optString("tool", ""), 40);
        if (!TOOL_SCHEDULE_ONCE.equals(tool)
                && !TOOL_SET_PLAN.equals(tool)
                && !TOOL_CLEAR_PLAN.equals(tool)
                && !TOOL_SET_INTERVAL.equals(tool)
                && !TOOL_BIND_CHAT.equals(tool)
                && !TOOL_CANCEL_HEARTBEAT.equals(tool)
                && !TOOL_GET_CURRENT_TIME.equals(tool)
                && !TOOL_CAPTURE_SCREEN.equals(tool)
                && !TOOL_TAP_SCREEN.equals(tool)
                && !TOOL_SWIPE_SCREEN.equals(tool)
                && !TOOL_PRESS_BACK.equals(tool)
                && !TOOL_ASK_USER.equals(tool)) return null;
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
                    String option = cleanLine(
                            rawOptions.optString(optionIndex, ""), MAX_OPTION_TEXT);
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
        return new ToolCall(id, tool, scope, "", "", 0, "", "");
    }

    private static boolean validScreenCoordinate(int value) {
        return value >= 0 && value <= 1000;
    }

    static String systemPrompt(long now, String existingPlan, int intervalMinutes,
                               String conversationScope) {
        String localTime = timestamp(now);
        String plan = cleanInstruction(existingPlan);
        if (plan.length() == 0) plan = "尚未设置；周期心跳时自然、简短地找用户聊天";
        int interval = Math.max(15, Math.min(7 * 24 * 60, intervalMinutes));
        String scope = cleanScope(conversationScope);
        return "你可以使用 Deekseep 在此设备上提供的真实本地心跳与基础界面工具。"
                + "这些工具确实可执行；当用户要求定时提醒、稍后主动联系、修改心跳用途或间隔时，"
                + "不要声称你无法设置闹钟或无法主动发消息，而要调用对应工具。"
                + "界面工具只操作当前前台的 DeepSeek 窗口。\n"
                + "当前设备本地时间：" + localTime + "。当前周期心跳间隔："
                + interval + " 分钟。当前对话的心跳约定：" + plan + "。"
                + "当前对话绑定标识：" + scope + "。\n"
                + "每个工具调用必须单独使用一组完整控制块，一组只能包含一个 call。"
                + "控制块必须是纯 JSON，不得放进 Markdown 代码块。固定格式：\n"
                + CONTROL_START + "\n"
                + "{\"call\":{\"id\":\"唯一短标识\",\"tool\":\"schedule_once\","
                + "\"scope\":\"" + scope + "\","
                + "\"at\":\"YYYY-MM-DDTHH:mm:ss+08:00\","
                + "\"instruction\":\"届时要做什么\"}}\n"
                + CONTROL_END + "\n"
                + "如需调用多个工具，必须按执行顺序为每个工具重复一整组“开始标记、"
                + "单个 call JSON、结束标记”；写完一个 call 后立刻写结束标记，"
                + "再开始下一个。禁止使用 calls 数组，禁止在同一控制块放入两个工具，"
                + "也禁止等所有工具组织完后才统一闭合。\n"
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
                + "每个问题必须给 2 到 4 个简短且互不重复的候选答案。"
                + "用户选择或输入后，应用会把 Question 与 Answer 作为普通可见用户消息发回本对话。"
                + "只有用户明确要求操作当前界面时，才可调用点击、滑动或返回工具；"
                + "不得自行点击删除、退出登录或其他不可逆操作。"
                + "每个调用都必须原样携带当前对话绑定标识 scope；它把任务限定在这个对话，"
                + "绝不能改成其他标识或当成全局任务。"
                + "当用户只要求把心跳绑定到当前对话时调用 bind_chat。"
                + "控制块只能放在最终回答部分，不能写进思考过程。"
                + "控制块外照常用自然语言回复并确认结果；绝不要向用户解释、复述或展示控制标记与 JSON。"
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
        String base = systemPrompt(
                now, existingPlan, intervalMinutes, conversationScope);
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
        StringBuilder allowed = new StringBuilder(base.length() + 240);
        allowed.append(base)
                .append("\n当前用户实际启用的工具只有：");
        for (int index = 0; index < names.size(); index++) {
            if (index > 0) allowed.append(", ");
            allowed.append(names.get(index));
        }
        allowed.append("。只能调用这份清单中的工具；上文示例中未列入清单的工具视为不可用，"
                + "不得调用或假装执行。");
        return allowed.toString();
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

    static String cleanInstruction(String value) {
        if (value == null) return "";
        String out = value.replace(CONTROL_START, "")
                .replace(CONTROL_END, "")
                .replace(EVENT_START, "")
                .replace(EVENT_END, "")
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
        String[] markers = new String[]{CONTROL_START, EVENT_START};
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

    private static String cleanLine(String value, int max) {
        String out = value == null ? "" : value.replace('\r', ' ')
                .replace('\n', ' ').trim();
        if (out.length() > max) out = out.substring(0, max).trim();
        return out;
    }
}
