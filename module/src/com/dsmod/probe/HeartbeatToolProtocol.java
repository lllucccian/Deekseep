package com.dsmod.probe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
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

    private static final int MAX_CONTROL_JSON = 16 * 1024;
    private static final int MAX_CALLS_PER_BLOCK = 8;
    private static final int MAX_INSTRUCTION = 1200;

    private HeartbeatToolProtocol() {}

    static final class ToolCall {
        final String id;
        final String tool;
        final String scope;
        final String at;
        final String instruction;
        final int minutes;
        final String mode;
        final String targetId;

        ToolCall(String id, String tool, String scope, String at,
                 String instruction, int minutes, String mode, String targetId) {
            this.id = id;
            this.tool = tool;
            this.scope = scope;
            this.at = at;
            this.instruction = instruction;
            this.minutes = minutes;
            this.mode = mode;
            this.targetId = targetId;
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
        return parse(value).visibleText;
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
            visible.append("> ").append(toolStatusText(call, chinese));
        }
        visible.append("\n\n");
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
        if (call == null) return chinese ? "\u5fc3\u8df3\u8bbe\u7f6e" : "Heartbeat settings";
        if (TOOL_SCHEDULE_ONCE.equals(call.tool)) {
            return joinStatus(
                    chinese ? "\u8bbe\u7f6e\u5fc3\u8df3" : "Set heartbeat",
                    friendlyToolTime(call.at, chinese), chinese);
        }
        if (TOOL_SET_PLAN.equals(call.tool)) {
            return chinese ? "\u66f4\u65b0\u5fc3\u8df3\u7ea6\u5b9a" : "Update heartbeat plan";
        }
        if (TOOL_CLEAR_PLAN.equals(call.tool)) {
            return chinese ? "\u6e05\u9664\u5fc3\u8df3\u7ea6\u5b9a" : "Clear heartbeat plan";
        }
        if (TOOL_SET_INTERVAL.equals(call.tool)) {
            String interval = call.minutes > 0
                    ? (chinese ? "\u6bcf" + call.minutes + "\u5206\u949f"
                    : "Every " + call.minutes
                    + (call.minutes == 1 ? " minute" : " minutes"))
                    : "";
            return joinStatus(
                    chinese ? "\u8bbe\u7f6e\u5fc3\u8df3" : "Set heartbeat",
                    interval, chinese);
        }
        if (TOOL_BIND_CHAT.equals(call.tool)) {
            return joinStatus(
                    chinese ? "\u7ed1\u5b9a\u5fc3\u8df3" : "Bind heartbeat",
                    chinese ? "\u5f53\u524d\u5bf9\u8bdd" : "Current chat",
                    chinese);
        }
        if (TOOL_CANCEL_HEARTBEAT.equals(call.tool)) {
            return joinStatus(
                    chinese ? "\u53d6\u6d88\u5fc3\u8df3" : "Cancel heartbeat",
                    cancelDetail(call, chinese), chinese);
        }
        return chinese ? "\u5fc3\u8df3\u8bbe\u7f6e" : "Heartbeat settings";
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
                        chinese ? "M\u6708d\u65e5 HH:mm" : "MMM d, HH:mm",
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
                && !TOOL_CANCEL_HEARTBEAT.equals(tool)) return null;
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
        return new ToolCall(id, tool, scope, "", "", 0, "", "");
    }

    static String systemPrompt(long now, String existingPlan, int intervalMinutes,
                               String conversationScope) {
        String localTime = timestamp(now);
        String plan = cleanInstruction(existingPlan);
        if (plan.length() == 0) plan = "尚未设置；周期心跳时自然、简短地找用户聊天";
        int interval = Math.max(15, Math.min(7 * 24 * 60, intervalMinutes));
        String scope = cleanScope(conversationScope);
        return "你可以使用 Deekseep 在此设备上提供的真实本地心跳工具。"
                + "这些工具确实可执行；当用户要求定时提醒、稍后主动联系、修改心跳用途或间隔时，"
                + "不要声称你无法设置闹钟或无法主动发消息，而要调用对应工具。\n"
                + "当前设备本地时间：" + localTime + "。当前周期心跳间隔："
                + interval + " 分钟。当前对话的心跳约定：" + plan + "。"
                + "当前对话绑定标识：" + scope + "。\n"
                + "工具只能使用下面的严格控制块；控制块必须是纯 JSON，不得放进 Markdown 代码块：\n"
                + CONTROL_START + "\n"
                + "{\"calls\":["
                + "{\"id\":\"唯一短标识\",\"tool\":\"schedule_once\",\"scope\":\""
                + scope + "\","
                + "\"at\":\"YYYY-MM-DDTHH:mm:ss+08:00\",\"instruction\":\"届时要做什么\"},"
                + "{\"id\":\"唯一短标识\",\"tool\":\"set_plan\",\"scope\":\""
                + scope + "\",\"instruction\":\"每次周期心跳要做什么\"},"
                + "{\"id\":\"唯一短标识\",\"tool\":\"clear_plan\",\"scope\":\""
                + scope + "\"},"
                + "{\"id\":\"唯一短标识\",\"tool\":\"bind_chat\",\"scope\":\""
                + scope + "\"},"
                + "{\"id\":\"唯一短标识\",\"tool\":\"set_interval\",\"scope\":\""
                + scope + "\",\"minutes\":180},"
                + "{\"id\":\"唯一短标识\",\"tool\":\"cancel_heartbeat\",\"scope\":\""
                + scope + "\",\"mode\":\"all_once\"}"
                + "]}\n" + CONTROL_END + "\n"
                + "规则：schedule_once 的 at 必须换算成未来的绝对本地时间并带时区，最长一年；"
                + "set_interval 只接受 15 到 10080 分钟。只在确实需要时输出调用。"
                + "cancel_heartbeat 的 mode 可为 once、all_once、periodic 或 all；"
                + "once 还必须用 target_id 指定原 schedule_once 的 id，"
                + "all_once 取消此对话全部一次性心跳，periodic 关闭周期心跳，"
                + "all 同时取消两者。"
                + "每个调用都必须原样携带当前对话绑定标识 scope；它把任务限定在这个对话，"
                + "绝不能改成其他标识或当成全局任务。"
                + "当用户只要求把心跳绑定到当前对话时调用 bind_chat。"
                + "控制块只能放在最终回答部分，不能写进思考过程。"
                + "控制块外照常用自然语言回复并确认结果；绝不要向用户解释、复述或展示控制标记与 JSON。"
                + "以后若收到以 " + EVENT_START + " 开头、以 " + EVENT_END
                + " 结尾的匿名消息，那是已经到时的真实心跳活动：按其中约定自然回复，"
                + "不要说自己无法主动联系，也不要提及后台、定时器、模块或系统提示词。";
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
