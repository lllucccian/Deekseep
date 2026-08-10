package com.dsmod.probe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.TimeZone;

/** Regression coverage for the structured DeepSeek/JLaTeXMath panel tool. */
public final class RichPanelRendererRegressionTest {
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static JSONObject row(String type) throws Exception {
        return new JSONObject().put("type", type);
    }

    private static JSONObject showcase() throws Exception {
        JSONArray rows = new JSONArray();
        rows.put(row("header").put("text", "概览").put("styles",
                new JSONArray().put("bold").put("underline")));
        rows.put(row("text").put("text", "支持中文、English 与 100% 安全转义")
                .put("style", "italic").put("color", "text"));
        rows.put(row("key_value").put("label", "状态").put("value", "运行中"));
        rows.put(row("progress").put("label", "进度").put("value", 72)
                .put("max", 100).put("bar_style", "bracket"));
        rows.put(row("segments").put("label", "阶段").put("value", 3)
                .put("max", 5).put("segments", 5)
                .put("colors", new JSONArray().put("success").put("warning")));
        rows.put(row("rating").put("label", "评分").put("value", 4)
                .put("max", 5).put("symbol", "★"));
        rows.put(row("badge").put("text", "READY").put("background", "success"));
        rows.put(row("status").put("label", "工具已连接").put("state", "success")
                .put("note", "Root"));
        rows.put(row("divider").put("width_pt", 120).put("height_pt", 1));
        rows.put(row("spacer").put("height_em", 0.4));
        rows.put(row("fraction").put("label", "比例").put("numerator", "3")
                .put("denominator", "5"));
        rows.put(row("formula").put("label", "公式")
                .put("latex", "\\frac{a+b}{\\sqrt{x}} \\leq \\infty"));
        rows.put(row("matrix").put("label", "矩阵").put("matrix_style", "bmatrix")
                .put("values", new JSONArray()
                        .put(new JSONArray().put("1").put("2"))
                        .put(new JSONArray().put("3").put("4"))));
        rows.put(row("arrow").put("from", "计划").put("to", "完成")
                .put("label", "执行"));
        rows.put(row("accent").put("text", "重点").put("accent", "widehat"));
        rows.put(row("quote").put("text", "保持简洁而清晰").put("author", "Deekseep"));
        rows.put(row("list").put("items", new JSONArray()
                .put("第一项").put("第二项").put("第三项")));
        rows.put(row("table").put("header", true).put("values", new JSONArray()
                .put(new JSONArray().put("项目").put("结果"))
                .put(new JSONArray().put("测试").put("通过"))));
        rows.put(row("sparkline").put("label", "趋势")
                .put("values", new JSONArray().put(1).put(3).put(2).put(7).put(5)));
        rows.put(row("counter").put("label", "已完成").put("value", "18")
                .put("total", "24"));
        rows.put(row("callout").put("text", "下一步：等待用户确认")
                .put("border", "warning"));
        rows.put(row("columns").put("values", new JSONArray()
                .put("左栏").put("中栏").put("右栏")));
        return new JSONObject()
                .put("preset", "dashboard")
                .put("title", "Agent 富面板")
                .put("subtitle", "由结构化参数生成")
                .put("footer", "本地渲染 · 不加载外部资源")
                .put("theme", "cyan")
                .put("frame", "single")
                .put("width_pt", 180)
                .put("bar_height_pt", 8)
                .put("rows", rows);
    }

    public static void main(String[] args) throws Exception {
        require(RichPanelRenderer.supportedRowTypes().size() == 32,
                "the rich-panel tool does not expose every documented row type");

        JSONObject panel = showcase();
        RichPanelRenderer.RenderedPanel rendered = RichPanelRenderer.render(panel);
        require(rendered != null && rendered.rowCount == 22,
                "the complete rich-panel showcase did not render");
        require(RichPanelRenderer.isRenderedPanel(rendered.latex)
                        && rendered.latex.startsWith("$$\n")
                        && rendered.latex.endsWith("\n$$"),
                "the generated panel is not a complete display-math block");
        require(rendered.latex.contains("\\fcolorbox{#00FFFF}{#0F0F0F}")
                        && rendered.latex.contains("\\rule{")
                        && rendered.latex.contains("\\frac{")
                        && rendered.latex.contains("\\begin{bmatrix}")
                        && rendered.latex.contains("\\xrightarrow{")
                        && rendered.latex.contains("\\widehat{")
                        && rendered.latex.contains("▁")
                        && rendered.latex.contains("\\colorbox{"),
                "one or more reversed JLaTeXMath visual families were not generated");
        require(rendered.latex.contains("100\\%")
                        && !rendered.latex.contains("100% 安全"),
                "plain panel text was not escaped for LaTeX");

        JSONObject transformed = new JSONObject(panel.toString())
                .put("frame", "double")
                .put("theme", "violet")
                .put("scale", 0.92)
                .put("rotation_deg", 2.5);
        RichPanelRenderer.RenderedPanel effects = RichPanelRenderer.render(transformed);
        require(effects != null
                        && effects.latex.contains("\\doublebox{")
                        && effects.latex.contains("\\scalebox{0.92}")
                        && effects.latex.contains("\\rotatebox{2.5}"),
                "frame or transform effects were not generated");

        JSONObject standaloneCircle = new JSONObject()
                .put("mode", "standalone")
                .put("theme", "candy")
                .put("rows", new JSONArray().put(row("shape")
                        .put("shape", "circle").put("color", "accent")
                        .put("scale", 1.4)));
        RichPanelRenderer.RenderedPanel circle =
                RichPanelRenderer.render(standaloneCircle);
        require(circle != null
                        && circle.latex.startsWith("$$\n\\begin{array}")
                        && circle.latex.contains("\\bigcirc")
                        && circle.latex.contains("\\scalebox{1.4}")
                        && !circle.latex.contains("\\fcolorbox{"),
                "standalone mode still forced a panel around a rough circle");

        JSONArray artRows = new JSONArray();
        artRows.put(row("shape").put("shape", "heart").put("repeat", 3));
        artRows.put(row("line").put("width_pt", 72)
                .put("height_pt", 2).put("rotation_deg", -18));
        artRows.put(row("lantern").put("glyph", "福").put("scale", 1.1));
        artRows.put(row("traffic_light").put("active", "yellow"));
        artRows.put(row("battery").put("value", 64).put("max", 100));
        artRows.put(row("signal").put("value", 3).put("bars", 5));
        artRows.put(row("gauge").put("label", "速度").put("value", 72));
        artRows.put(row("pixel_art")
                .put("palette", new JSONArray().put("danger").put("warning"))
                .put("pixels", new JSONArray()
                        .put("..000..")
                        .put(".01110.")
                        .put("0111110")
                        .put("0111110")
                        .put(".01110.")
                        .put("..000..")));
        artRows.put(row("ornament").put("symbol", "diamond")
                .put("count", 7).put("text", "节日快乐"));
        artRows.put(row("flow").put("nodes", new JSONArray()
                .put("开始").put("处理").put("完成")));
        JSONObject art = new JSONObject().put("mode", "art")
                .put("theme", "amber").put("rows", artRows);
        RichPanelRenderer.RenderedPanel renderedArt = RichPanelRenderer.render(art);
        require(renderedArt != null && renderedArt.rowCount == 10
                        && renderedArt.latex.startsWith("$$\n\\begin{array}")
                        && renderedArt.latex.contains("\\heartsuit")
                        && renderedArt.latex.contains("\\rotatebox{-18}")
                        && renderedArt.latex.contains("\\ovalbox{")
                        && renderedArt.latex.contains("\\bullet")
                        && renderedArt.latex.contains("\\mathrlap{")
                        && renderedArt.latex.contains("\\phantom{\\rule{")
                        && renderedArt.latex.contains("\\lozenge")
                        && renderedArt.latex.contains("\\longrightarrow"),
                "symbol-art mode omitted a shape, lantern, gauge, pixel, or flow family");

        JSONObject tolerantPixels = new JSONObject()
                .put("mode", "art")
                .put("rows", new JSONArray().put(row("pixel_art")
                        .put("palette", new JSONArray()
                                .put("transparent").put("#F59E0B"))
                        // Model-authored pixel rows are frequently off by one. The missing cell
                        // must become transparent rather than rejecting the complete Agent step.
                        .put("pixels", new JSONArray()
                                .put("01110")
                                .put("11 11")
                                .put(".111." )
                                .put("11"))));
        RichPanelRenderer.RenderedPanel tolerantArt =
                RichPanelRenderer.render(tolerantPixels);
        require(tolerantArt != null
                        && tolerantArt.latex.contains("\\phantom{\\rule{")
                        && tolerantArt.latex.contains("#F59E0B"),
                "pixel-art recovery did not accept transparency, spaces, or uneven rows");

        JSONObject mappedPixels = new JSONObject()
                .put("mode", "canvas")
                .put("rows", new JSONArray().put(row("pixel_art")
                        .put("palette", new JSONObject()
                                .put("0", "transparent")
                                .put("X", "#34D399"))
                        .put("pixels", new JSONArray()
                                .put("0XX0")
                                .put("XXXX"))));
        RichPanelRenderer.RenderedPanel mappedArt =
                RichPanelRenderer.render(mappedPixels);
        require(mappedArt != null && mappedArt.latex.contains("#34D399"),
                "object-mapped pixel palette was rejected");

        JSONObject hostileFormula = new JSONObject()
                .put("title", "bad")
                .put("rows", new JSONArray().put(row("formula")
                        .put("latex", "\\includegraphics{https://example.test/a.png}")));
        require(RichPanelRenderer.render(hostileFormula) == null,
                "external-resource LaTeX was accepted");
        hostileFormula.getJSONArray("rows").getJSONObject(0)
                .put("latex", "\\newcommand{\\x}{boom}\\x");
        require(RichPanelRenderer.render(hostileFormula) == null,
                "model-defined LaTeX macros were accepted");
        hostileFormula.getJSONArray("rows").getJSONObject(0)
                .put("latex", "\\jlmExternalFont{remote}");
        require(RichPanelRenderer.render(hostileFormula) == null,
                "external JLaTeXMath font loading was accepted");

        JSONObject invalidType = new JSONObject()
                .put("title", "bad")
                .put("rows", new JSONArray().put(row("unknown_widget")
                        .put("text", "must fail")));
        require(RichPanelRenderer.render(invalidType) == null,
                "an undocumented panel row was silently accepted");

        JSONObject badColor = new JSONObject(panel.toString())
                .put("border_color", "#fff}{\\includegraphics{evil}");
        RichPanelRenderer.RenderedPanel colorSafe = RichPanelRenderer.render(badColor);
        require(colorSafe != null && !colorSafe.latex.contains("includegraphics"),
                "a custom color escaped into executable LaTeX");

        JSONObject call = new JSONObject()
                .put("id", "panel_001")
                .put("tool", HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL)
                .put("scope", "conversation-1234")
                .put("panel", panel);
        String block = HeartbeatToolProtocol.CONTROL_START + "\n"
                + new JSONObject().put("call", call).toString() + "\n"
                + HeartbeatToolProtocol.CONTROL_END;
        Locale previousLocale = Locale.getDefault();
        TimeZone previousZone = TimeZone.getDefault();
        HeartbeatToolProtocol.Result visible;
        try {
            Locale.setDefault(Locale.CHINA);
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
            visible = HeartbeatToolProtocol.parseForConversation(block);
        } finally {
            Locale.setDefault(previousLocale);
            TimeZone.setDefault(previousZone);
        }
        require(visible.calls.size() == 1
                        && HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL.equals(
                        visible.calls.get(0).tool)
                        && RichPanelRenderer.isRenderedPanel(visible.calls.get(0).content),
                "render_rich_panel was not parsed as one strict tool call");
        String visibleText = HeartbeatToolProtocol.stripToolStatusStyleMarkers(
                visible.visibleText);
        require(visibleText.contains("生成富视觉：Agent 富面板")
                        && visibleText.contains("$$")
                        && visibleText.contains("\\begin{array}"),
                "the activity row and generated panel were not shown together");
        HeartbeatToolProtocol.Result privateView = HeartbeatToolProtocol.parse(block);
        require(privateView.visibleText.length() == 0
                        && privateView.calls.size() == 1,
                "the model-context parser retained a presentation-only panel");

        JSONObject invalidVisualCall = new JSONObject()
                .put("id", "panel_bad")
                .put("tool", HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL)
                .put("scope", "conversation-1234")
                .put("panel", invalidType);
        String invalidVisualBlock = HeartbeatToolProtocol.CONTROL_START + "\n"
                + new JSONObject().put("call", invalidVisualCall).toString() + "\n"
                + HeartbeatToolProtocol.CONTROL_END;
        HeartbeatToolProtocol.Result rejectedVisual =
                HeartbeatToolProtocol.parseForConversation(invalidVisualBlock);
        require(rejectedVisual.calls.isEmpty()
                        && rejectedVisual.rejectedCalls.size() == 1
                        && "invalid_visual_schema".equals(
                        rejectedVisual.rejectedCalls.get(0).reason)
                        && !rejectedVisual.visibleText.contains("$$")
                        && rejectedVisual.visibleText.length() > 0,
                "invalid rich visual was silently swallowed or promoted to executable content");

        AgentToolConfig.Snapshot migrated = AgentToolConfig.decode(
                "{\"version\":2,\"enabled\":true,\"backend\":\"in_app\","
                        + "\"permission\":\"all\",\"enabled_tools\":[\"ask_user\"]}");
        require(migrated.enabledTools.contains(
                        HeartbeatToolProtocol.TOOL_RENDER_RICH_PANEL),
                "existing Agent settings did not automatically enable the new panel tool");

        System.out.println("Rich panel renderer regression tests passed");
    }
}
