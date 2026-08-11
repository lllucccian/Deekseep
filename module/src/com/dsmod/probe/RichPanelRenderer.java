package com.dsmod.probe;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Converts a narrow JSON visual description into LaTeX understood by DeepSeek's bundled
 * JLaTeXMath renderer.
 *
 * <p>The host currently exposes the same 285 macro registrations in 2.2.2 and 2.3.0. This
 * generator deliberately uses only the stable, local drawing families from that registry:
 * arrays, color boxes, framed boxes, rules, typography, transforms, fractions, matrices,
 * arrows and accents. Resource-loading and macro-definition commands are never accepted.</p>
 */
final class RichPanelRenderer {
    static final int MAX_ROWS = 24;
    static final int MAX_PANEL_JSON = 32 * 1024;
    static final int MAX_LATEX = 32 * 1024;

    private static final Set<String> PRESETS = setOf(
            "dashboard", "character", "task", "timeline", "comparison",
            "terminal", "alert", "report", "game", "science");
    private static final Set<String> MODES = setOf(
            "panel", "standalone", "art", "canvas");
    private static final Set<String> FRAMES = setOf(
            "single", "double", "shadow", "oval", "none");
    private static final Set<String> ALIGNS = setOf("left", "center", "right");
    private static final Set<String> SIZES = setOf(
            "tiny", "scriptsize", "footnotesize", "small", "normal",
            "large", "xlarge", "huge");
    private static final Set<String> ROW_TYPES = setOf(
            "header", "text", "key_value", "progress", "segments", "rating",
            "badge", "status", "divider", "spacer", "fraction", "formula",
            "matrix", "arrow", "accent", "quote", "list", "table",
            "sparkline", "counter", "callout", "columns", "shape", "line", "lantern",
            "traffic_light", "battery", "signal", "gauge", "pixel_art",
            "ornament", "flow");

    /** Safe raw-formula commands present in both inspected host versions. */
    private static final Set<String> SAFE_FORMULA_COMMANDS = setOf(
            // Layout and fractions.
            "frac", "dfrac", "tfrac", "sfrac", "cfrac", "sqrt", "binom",
            "dbinom", "tbinom", "left", "right", "middle", "overline",
            "underline", "underbrace", "overbrace", "underbrack", "overbrack",
            "underparen", "overparen", "overset", "underset", "stackrel",
            // Fonts and text.
            "text", "textbf", "textit", "textsf", "texttt", "textrm",
            "mathbf", "mathbb", "mathcal", "mathit", "mathrm", "mathscr",
            "mathsf", "mathtt", "mathfrak", "mathds", "boldsymbol",
            "operatorname", "textsuperscript", "textsubscript", "textcircled",
            // Accents and directional decoration.
            "hat", "widehat", "tilde", "widetilde", "acute", "grave", "ddot",
            "bar", "breve", "check", "vec", "dot", "mathring", "accentset",
            "underaccent", "undertilde", "overrightarrow", "overleftarrow",
            "overleftrightarrow", "underrightarrow", "underleftarrow",
            "underleftrightarrow", "xrightarrow", "xleftarrow",
            // Common Greek letters.
            "alpha", "beta", "gamma", "delta", "epsilon", "varepsilon", "zeta",
            "eta", "theta", "vartheta", "iota", "kappa", "lambda", "mu", "nu",
            "xi", "pi", "varpi", "rho", "varrho", "sigma", "varsigma", "tau",
            "upsilon", "phi", "varphi", "chi", "psi", "omega", "Gamma", "Delta",
            "Theta", "Lambda", "Xi", "Pi", "Sigma", "Upsilon", "Phi", "Psi",
            "Omega",
            // Operators, relations, sets and arrows.
            "sum", "prod", "coprod", "int", "iint", "iiint", "oint", "infty",
            "partial", "nabla", "pm", "mp", "times", "div", "cdot", "ast",
            "star", "circ", "bullet", "oplus", "ominus", "otimes", "oslash",
            "cap", "cup", "setminus", "subset", "supset", "subseteq", "supseteq",
            "in", "ni", "notin", "le", "leq", "ge", "geq", "ne", "neq",
            "equiv", "approx", "sim", "simeq", "cong", "propto", "parallel",
            "perp", "mid", "models", "vdash", "dashv", "prec", "succ",
            "preceq", "succeq", "to", "mapsto", "gets", "leftarrow",
            "rightarrow", "leftrightarrow", "Leftarrow", "Rightarrow",
            "Leftrightarrow", "longleftarrow", "longrightarrow",
            "longleftrightarrow", "Longleftarrow", "Longrightarrow",
            "Longleftrightarrow", "uparrow", "downarrow", "updownarrow",
            "nearrow", "searrow", "swarrow", "nwarrow",
            // Geometric and decorative symbols exposed by the bundled TeXSymbols.xml.
            "bigcirc", "varbigcirc", "circledast", "square", "blacksquare",
            "Box", "Diamond", "diamond", "lozenge", "blacklozenge", "triangle",
            "vartriangle", "triangledown", "blacktriangle", "blacktriangledown",
            "triangleleft", "triangleright", "bigstar", "star", "heartsuit",
            "diamondsuit", "clubsuit", "spadesuit",
            // Delimiters, dots and spacing.
            "langle", "rangle", "lceil", "rceil", "lfloor", "rfloor", "lvert",
            "rvert", "Vert", "vert", "lbrace", "rbrace", "ldots", "cdots",
            "vdots", "ddots", "quad", "qquad", "thinspace", "medspace",
            "thickspace", "displaystyle", "textstyle", "scriptstyle",
            "scriptscriptstyle", "nolimits", "limits");

    private static final Map<String, Theme> THEMES = createThemes();

    private RichPanelRenderer() {}

    static final class RenderedPanel {
        final String latex;
        final String title;
        final String preset;
        final int rowCount;

        RenderedPanel(String latex, String title, String preset, int rowCount) {
            this.latex = latex == null ? "" : latex;
            this.title = title == null ? "" : title;
            this.preset = preset == null ? "" : preset;
            this.rowCount = rowCount;
        }
    }

    private static final class Theme {
        final String border;
        final String background;
        final String title;
        final String text;
        final String muted;
        final String accent;
        final String track;
        final String success;
        final String warning;
        final String danger;

        Theme(String border, String background, String title, String text,
              String muted, String accent, String track, String success,
              String warning, String danger) {
            this.border = border;
            this.background = background;
            this.title = title;
            this.text = text;
            this.muted = muted;
            this.accent = accent;
            this.track = track;
            this.success = success;
            this.warning = warning;
            this.danger = danger;
        }
    }

    private static final class Config {
        final String mode;
        final String preset;
        final Theme theme;
        final String frame;
        final String align;
        final String bodySize;
        final String titleSize;
        final float widthPt;
        final float barHeightPt;
        final float rowGapEm;
        final float scale;
        final float rotation;
        final boolean titleDivider;

        Config(String mode, String preset, Theme theme, String frame, String align,
               String bodySize, String titleSize, float widthPt,
               float barHeightPt, float rowGapEm, float scale,
               float rotation, boolean titleDivider) {
            this.mode = mode;
            this.preset = preset;
            this.theme = theme;
            this.frame = frame;
            this.align = align;
            this.bodySize = bodySize;
            this.titleSize = titleSize;
            this.widthPt = widthPt;
            this.barHeightPt = barHeightPt;
            this.rowGapEm = rowGapEm;
            this.scale = scale;
            this.rotation = rotation;
            this.titleDivider = titleDivider;
        }
    }

    static RenderedPanel render(JSONObject panel) {
        if (panel == null || panel.toString().length() > MAX_PANEL_JSON) return null;
        try {
            String mode = token(panel.optString("mode", "panel"), 16);
            if ("visual".equals(mode)) mode = "standalone";
            if (!MODES.contains(mode)) mode = "panel";
            String preset = token(panel.optString(
                    "preset", panel.optString("template", "dashboard")), 24);
            if (!PRESETS.contains(preset)) preset = "dashboard";
            String requestedTheme = token(panel.optString(
                    "theme", defaultThemeForPreset(preset)), 24);
            Theme base = THEMES.get(requestedTheme);
            if (base == null) base = THEMES.get(defaultThemeForPreset(preset));
            Theme theme = overrideTheme(base, panel);
            String frame = token(panel.optString(
                    "frame", defaultFrameForPreset(preset)), 16);
            if (!FRAMES.contains(frame)) frame = defaultFrameForPreset(preset);
            String align = token(panel.optString("align", "center"), 12);
            if (!ALIGNS.contains(align)) align = "center";
            String bodySize = sizeToken(panel.optString("body_size", "normal"));
            String titleSize = sizeToken(panel.optString("title_size", "large"));
            Config config = new Config(
                    mode, preset, theme, frame, align, bodySize, titleSize,
                    number(panel, "width_pt", 180f, 72f, 260f),
                    number(panel, "bar_height_pt", 9f, 2f, 18f),
                    number(panel, "row_gap_em",
                            panel.optBoolean("dense", false) ? 0.15f : 0.35f,
                            0f, 1.5f),
                    number(panel, "scale", 1f, 0.75f, 1.2f),
                    number(panel, "rotation_deg", 0f, -6f, 6f),
                    panel.optBoolean("title_divider", true));

            String title = cleanText(panel.optString("title", ""), 180);
            String subtitle = cleanText(panel.optString("subtitle", ""), 240);
            String footer = cleanText(panel.optString("footer", ""), 240);
            JSONArray rows = panel.optJSONArray("rows");
            if (rows == null) rows = panel.optJSONArray("elements");
            int rowCount = rows == null ? 0 : rows.length();
            if (rowCount > MAX_ROWS || (title.length() == 0 && subtitle.length() == 0
                    && footer.length() == 0 && rowCount == 0)) return null;

            ArrayList<String> renderedRows = new ArrayList<>();
            if (title.length() > 0) {
                renderedRows.add(colored(config.theme.title,
                        sized(config.titleSize,
                                "\\mathbf{\\text{" + escapeText(title) + "}}")));
            }
            if (subtitle.length() > 0) {
                renderedRows.add(colored(config.theme.muted,
                        sized("small", "\\text{" + escapeText(subtitle) + "}")));
            }
            if ("panel".equals(config.mode) && title.length() > 0
                    && config.titleDivider && rowCount > 0) {
                renderedRows.add(rule(config.theme.border, config.widthPt, 0.65f));
            }
            for (int index = 0; index < rowCount; index++) {
                JSONObject row = rows.optJSONObject(index);
                String rendered = renderRow(row, config);
                if (rendered == null) return null;
                renderedRows.add(rendered);
            }
            if (footer.length() > 0) {
                renderedRows.add(colored(config.theme.muted,
                        sized("footnotesize", "\\text{" + escapeText(footer) + "}")));
            }
            if (renderedRows.isEmpty()) return null;

            StringBuilder inner = new StringBuilder(2048);
            inner.append("\\begin{array}{")
                    .append(arrayAlignment(config.align)).append("}\n");
            for (int index = 0; index < renderedRows.size(); index++) {
                if (index > 0) inner.append(" \\\\\n");
                String row = renderedRows.get(index);
                if (config.rowGapEm > 0f && !row.startsWith("\\rule{0pt}")) {
                    inner.append("\\rule{0pt}{")
                            .append(decimal(0.8f + config.rowGapEm))
                            .append("em} ");
                }
                inner.append(row);
            }
            inner.append("\n\\end{array}");

            String framed = "panel".equals(config.mode)
                    ? frame(inner.toString(), config) : inner.toString();
            if (Math.abs(config.scale - 1f) > 0.001f) {
                framed = "\\scalebox{" + decimal(config.scale) + "}{" + framed + "}";
            }
            if (Math.abs(config.rotation) > 0.01f) {
                framed = "\\rotatebox{" + decimal(config.rotation) + "}{" + framed + "}";
            }
            String latex = "$$\n" + framed + "\n$$";
            if (latex.length() > MAX_LATEX) return null;
            return new RenderedPanel(latex, title, preset, rowCount);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static boolean isRenderedPanel(String value) {
        if (value == null) return false;
        String text = value.trim();
        return text.startsWith("$$") && text.endsWith("$$")
                && text.length() <= MAX_LATEX
                && text.indexOf("\\begin{array}") >= 0;
    }

    static Set<String> supportedRowTypes() {
        return ROW_TYPES;
    }

    private static String renderRow(JSONObject row, Config config) {
        if (row == null) return null;
        String type = token(row.optString("type", "text"), 32);
        if ("metric".equals(type) || "pair".equals(type)) type = "key_value";
        if ("bar".equals(type)) type = "progress";
        if ("separator".equals(type)) type = "divider";
        if (!ROW_TYPES.contains(type)) return null;

        if ("header".equals(type)) {
            String text = cleanText(row.optString("text", row.optString("label", "")), 180);
            if (text.length() == 0) return null;
            return styledText(text, resolveColor(row.optString("color", "title"),
                    config.theme), row, "bold", "large");
        }
        if ("text".equals(type)) {
            String text = cleanText(row.optString("text", ""), 360);
            if (text.length() == 0) return null;
            text = cleanText(row.optString("prefix", ""), 40) + text
                    + cleanText(row.optString("suffix", ""), 40);
            return styledText(text, resolveColor(row.optString("color", "text"),
                    config.theme), row, "", config.bodySize);
        }
        if ("key_value".equals(type)) {
            String label = cleanText(row.optString("label", ""), 120);
            String value = cleanText(row.optString("value", ""), 180);
            if (label.length() == 0 || value.length() == 0) return null;
            String separator = cleanText(row.optString("separator", "："), 8);
            String labelColor = resolveColor(row.optString("label_color", "muted"),
                    config.theme);
            String valueColor = resolveColor(row.optString("value_color", "accent"),
                    config.theme);
            String valuePart = row.optBoolean("bold_value", true)
                    ? "\\mathbf{\\text{" + escapeText(value) + "}}"
                    : "\\text{" + escapeText(value) + "}";
            return colored(labelColor, "\\text{" + escapeText(label + separator) + "}")
                    + " \\quad " + colored(valueColor, valuePart);
        }
        if ("progress".equals(type)) return progressRow(row, config, false);
        if ("segments".equals(type)) return progressRow(row, config, true);
        if ("rating".equals(type)) {
            String label = cleanText(row.optString("label", ""), 100);
            int total = integer(row, "max", 5, 1, 10);
            int filled = integer(row, "value", 0, 0, total);
            String symbol = cleanText(row.optString("symbol", "●"), 4);
            if (symbol.length() == 0) symbol = "●";
            StringBuilder on = new StringBuilder();
            StringBuilder off = new StringBuilder();
            for (int index = 0; index < filled; index++) on.append(symbol);
            for (int index = filled; index < total; index++) off.append(symbol);
            String result = label.length() == 0 ? ""
                    : colored(config.theme.muted,
                    "\\text{" + escapeText(label + "：") + "}") + " ";
            if (on.length() > 0) result += colored(
                    resolveColor(row.optString("color", "accent"), config.theme),
                    "\\text{" + escapeText(on.toString()) + "}");
            if (off.length() > 0) result += colored(
                    resolveColor(row.optString("off_color", "track"), config.theme),
                    "\\text{" + escapeText(off.toString()) + "}");
            return result;
        }
        if ("badge".equals(type)) {
            String text = cleanText(row.optString("text", row.optString("label", "")), 80);
            if (text.length() == 0) return null;
            String foreground = resolveColor(row.optString("color", "background"),
                    config.theme);
            String background = resolveColor(row.optString("background", "accent"),
                    config.theme);
            return "\\colorbox{" + background + "}{"
                    + colored(foreground,
                    "\\mathbf{\\text{ " + escapeText(text) + " }}") + "}";
        }
        if ("status".equals(type)) {
            String label = cleanText(row.optString("label", row.optString("text", "")), 140);
            String note = cleanText(row.optString("note", row.optString("value", "")), 160);
            if (label.length() == 0) return null;
            String state = token(row.optString("state", "info"), 16);
            String stateColor;
            if ("success".equals(state)) stateColor = config.theme.success;
            else if ("warning".equals(state)) stateColor = config.theme.warning;
            else if ("error".equals(state) || "danger".equals(state)) {
                stateColor = config.theme.danger;
            } else if ("neutral".equals(state)) stateColor = config.theme.muted;
            else stateColor = config.theme.accent;
            stateColor = resolveColor(row.optString("color", stateColor), config.theme);
            return colored(stateColor, "\\bullet") + " \\quad "
                    + colored(config.theme.text,
                    "\\mathbf{\\text{" + escapeText(label) + "}}")
                    + (note.length() == 0 ? "" : " \\quad "
                    + colored(config.theme.muted,
                    "\\text{" + escapeText(note) + "}"));
        }
        if ("divider".equals(type)) {
            return rule(resolveColor(row.optString("color", "border"), config.theme),
                    number(row, "width_pt", config.widthPt, 24f, 260f),
                    number(row, "height_pt", 0.7f, 0.3f, 8f));
        }
        if ("spacer".equals(type)) {
            return "\\rule{0pt}{" + decimal(number(
                    row, "height_em", 0.7f, 0.1f, 2.5f)) + "em}";
        }
        if ("fraction".equals(type)) {
            String numerator = formulaOrText(row.optString("numerator", ""),
                    row.optBoolean("math", false));
            String denominator = formulaOrText(row.optString("denominator", ""),
                    row.optBoolean("math", false));
            if (numerator == null || denominator == null
                    || numerator.length() == 0 || denominator.length() == 0) return null;
            String label = cleanText(row.optString("label", ""), 100);
            String expression = "\\frac{" + numerator + "}{" + denominator + "}";
            if (label.length() > 0) {
                expression = "\\text{" + escapeText(label + "：") + "} \\quad "
                        + expression;
            }
            return colored(resolveColor(row.optString("color", "accent"), config.theme),
                    expression);
        }
        if ("formula".equals(type)) {
            String latex = safeFormula(row.optString("latex", row.optString("value", "")));
            if (latex == null || latex.length() == 0) return null;
            String label = cleanText(row.optString("label", ""), 100);
            return (label.length() == 0 ? "" : colored(config.theme.muted,
                    "\\text{" + escapeText(label + "：") + "}") + " \\quad ")
                    + colored(resolveColor(row.optString("color", "text"), config.theme),
                    latex);
        }
        if ("matrix".equals(type)) return matrixRow(row, config);
        if ("arrow".equals(type)) {
            String from = cleanText(row.optString("from", ""), 80);
            String to = cleanText(row.optString("to", ""), 80);
            String label = cleanText(row.optString("label", ""), 80);
            if (from.length() == 0 || to.length() == 0) return null;
            String direction = token(row.optString("direction", "right"), 12);
            String arrow;
            if ("left".equals(direction)) {
                arrow = "\\xleftarrow{\\text{" + escapeText(label) + "}}";
            } else if ("both".equals(direction)) {
                arrow = "\\overset{\\text{" + escapeText(label)
                        + "}}{\\longleftrightarrow}";
            } else {
                arrow = "\\xrightarrow{\\text{" + escapeText(label) + "}}";
            }
            return colored(resolveColor(row.optString("color", "accent"), config.theme),
                    "\\text{" + escapeText(from) + "} " + arrow
                            + " \\text{" + escapeText(to) + "}");
        }
        if ("accent".equals(type)) {
            String text = cleanText(row.optString("text", ""), 80);
            String accent = token(row.optString("accent", "hat"), 24);
            Set<String> accents = setOf("hat", "widehat", "tilde", "widetilde",
                    "bar", "vec", "dot", "ddot", "check", "breve", "overline",
                    "underline", "overbrace", "underbrace", "overrightarrow",
                    "overleftarrow");
            if (text.length() == 0 || !accents.contains(accent)) return null;
            return colored(resolveColor(row.optString("color", "accent"), config.theme),
                    "\\" + accent + "{\\text{" + escapeText(text) + "}}");
        }
        if ("quote".equals(type)) {
            String text = cleanText(row.optString("text", ""), 300);
            String author = cleanText(row.optString("author", ""), 100);
            if (text.length() == 0) return null;
            return colored(resolveColor(row.optString("color", "text"), config.theme),
                    "\\mathit{\\text{“" + escapeText(text) + "”}}")
                    + (author.length() == 0 ? "" : " \\quad "
                    + colored(config.theme.muted,
                    "\\text{— " + escapeText(author) + "}"));
        }
        if ("list".equals(type)) return listRow(row, config);
        if ("table".equals(type)) return tableRow(row, config);
        if ("sparkline".equals(type)) return sparklineRow(row, config);
        if ("counter".equals(type)) {
            String label = cleanText(row.optString("label", ""), 100);
            String value = cleanText(row.optString("value", ""), 80);
            String total = cleanText(row.optString("total", ""), 80);
            if (value.length() == 0) return null;
            String count = total.length() == 0 ? value : value + " / " + total;
            return colored(resolveColor(row.optString("color", "accent"), config.theme),
                    sized(sizeToken(row.optString("size", "xlarge")),
                            "\\mathbf{\\text{" + escapeText(count) + "}}"))
                    + (label.length() == 0 ? "" : " \\quad "
                    + colored(config.theme.muted,
                    "\\text{" + escapeText(label) + "}"));
        }
        if ("callout".equals(type)) {
            String text = cleanText(row.optString("text", ""), 260);
            if (text.length() == 0) return null;
            String border = resolveColor(row.optString("border", "accent"), config.theme);
            String background = resolveColor(row.optString("background", "track"), config.theme);
            String foreground = resolveColor(row.optString("color", "text"), config.theme);
            return "\\fcolorbox{" + border + "}{" + background + "}{"
                    + colored(foreground, "\\text{ " + escapeText(text) + " }") + "}";
        }
        if ("columns".equals(type)) {
            JSONArray values = row.optJSONArray("values");
            if (values == null) values = row.optJSONArray("items");
            if (values == null || values.length() < 2 || values.length() > 4) return null;
            StringBuilder result = new StringBuilder();
            JSONArray colors = row.optJSONArray("colors");
            for (int index = 0; index < values.length(); index++) {
                String value = cleanText(values.optString(index, ""), 100);
                if (value.length() == 0) return null;
                if (index > 0) result.append(" \\qquad ");
                String color = colors == null ? config.theme.text
                        : resolveColor(colors.optString(index, "text"), config.theme);
                result.append(colored(color,
                        "\\text{" + escapeText(value) + "}"));
            }
            return result.toString();
        }
        if ("shape".equals(type)) return shapeRow(row, config);
        if ("line".equals(type)) return lineRow(row, config);
        if ("lantern".equals(type)) return lanternRow(row, config);
        if ("traffic_light".equals(type)) return trafficLightRow(row, config);
        if ("battery".equals(type)) return batteryRow(row, config);
        if ("signal".equals(type)) return signalRow(row, config);
        if ("gauge".equals(type)) return gaugeRow(row, config);
        if ("pixel_art".equals(type)) return pixelArtRow(row, config);
        if ("ornament".equals(type)) return ornamentRow(row, config);
        if ("flow".equals(type)) return flowRow(row, config);
        return null;
    }

    private static String shapeRow(JSONObject row, Config config) {
        String shape = token(row.optString("shape", "circle"), 24);
        String label = cleanText(row.optString("label", row.optString("text", "")), 40);
        String color = resolveColor(row.optString("color", "accent"), config.theme);
        String size = sizeToken(row.optString("size", "xlarge"));
        String value;
        if ("circle_label".equals(shape)) {
            if (label.length() == 0 || label.length() > 4) return null;
            value = "\\textcircled{\\text{" + escapeText(label) + "}}";
        } else if ("oval".equals(shape)) {
            value = "\\ovalbox{\\text{ " + escapeText(
                    label.length() == 0 ? " " : label) + " }}";
        } else if ("rectangle".equals(shape)) {
            if (label.length() > 0) {
                value = "\\fbox{\\text{ " + escapeText(label) + " }}";
            } else {
                value = "\\fbox{\\rule{"
                        + decimal(number(row, "width_pt", 28f, 4f, 160f))
                        + "pt}{" + decimal(number(row, "height_pt", 14f, 3f, 80f))
                        + "pt}}";
            }
        } else {
            String symbol;
            if ("circle".equals(shape) || "ring".equals(shape)) symbol = "\\bigcirc";
            else if ("dot".equals(shape)) symbol = "\\bullet";
            else if ("square".equals(shape)) symbol = "\\square";
            else if ("filled_square".equals(shape)) symbol = "\\blacksquare";
            else if ("diamond".equals(shape)) symbol = "\\lozenge";
            else if ("filled_diamond".equals(shape)) symbol = "\\blacklozenge";
            else if ("triangle".equals(shape)) symbol = "\\triangle";
            else if ("triangle_down".equals(shape)) symbol = "\\triangledown";
            else if ("star".equals(shape)) symbol = "\\bigstar";
            else if ("heart".equals(shape)) symbol = "\\heartsuit";
            else return null;
            int repeat = integer(row, "repeat", 1, 1, 16);
            StringBuilder repeated = new StringBuilder();
            for (int index = 0; index < repeat; index++) {
                if (index > 0) repeated.append(" \\quad ");
                repeated.append(symbol);
            }
            value = repeated.toString();
            if (label.length() > 0) {
                value += " \\quad \\text{" + escapeText(label) + "}";
            }
        }
        String rendered = colored(color, sized(size, value));
        float scale = number(row, "scale", 1f, 0.5f, 2.2f);
        if (Math.abs(scale - 1f) > 0.001f) {
            rendered = "\\scalebox{" + decimal(scale) + "}{" + rendered + "}";
        }
        float rotation = number(row, "rotation_deg", 0f, -180f, 180f);
        if (Math.abs(rotation) > 0.01f) {
            rendered = "\\rotatebox{" + decimal(rotation) + "}{" + rendered + "}";
        }
        return rendered;
    }

    private static String lineRow(JSONObject row, Config config) {
        String color = resolveColor(row.optString("color", "accent"), config.theme);
        float width = number(row, "width_pt", 54f, 2f, 240f);
        float thickness = number(row, "height_pt", 1.2f, 0.3f, 10f);
        float rotation = number(row, "rotation_deg", 0f, -180f, 180f);
        String line = rule(color, width, thickness);
        if (Math.abs(rotation) > 0.01f) {
            line = "\\rotatebox{" + decimal(rotation) + "}{" + line + "}";
        }
        String start = cleanText(row.optString("from", ""), 40);
        String end = cleanText(row.optString("to", ""), 40);
        if (start.length() > 0) {
            line = colored(config.theme.muted,
                    "\\text{" + escapeText(start) + "}") + " \\quad " + line;
        }
        if (end.length() > 0) {
            line += " \\quad " + colored(config.theme.muted,
                    "\\text{" + escapeText(end) + "}");
        }
        return line;
    }

    private static String lanternRow(JSONObject row, Config config) {
        String bodyColor = resolveColor(row.optString("body_color", "danger"), config.theme);
        String trimColor = resolveColor(row.optString("trim_color", "warning"), config.theme);
        String shadowColor = color(row.optString("shadow_color", ""), "#7F1D1D");
        String glyph = cleanText(row.optString("glyph", "福"), 4);
        if (glyph.length() == 0) glyph = "福";
        String lanternCore = "\\colorbox{" + shadowColor + "}{"
                + "\\begin{array}{c}"
                + rule(bodyColor, 34f, 3f) + " \\\\ "
                + colored(trimColor,
                "\\mathbf{\\text{ " + escapeText(glyph) + " }}")
                + " \\\\ " + rule(bodyColor, 34f, 3f)
                + "\\end{array}}";
        StringBuilder lantern = new StringBuilder("\\begin{array}{c}");
        lantern.append(rule(trimColor, 2f, 10f)).append(" \\\\ ")
                .append(rule(trimColor, 22f, 2.2f)).append(" \\\\ ")
                .append(colored(bodyColor, "\\ovalbox{" + lanternCore + "}"))
                .append(" \\\\ ").append(rule(trimColor, 22f, 2.2f))
                .append(" \\\\ ").append(rule(trimColor, 1.4f, 10f))
                .append(" \\\\ ")
                .append(colored(trimColor, "\\bigtriangledown"))
                .append("\\end{array}");
        String rendered = lantern.toString();
        float scale = number(row, "scale", 1f, 0.55f, 2f);
        if (Math.abs(scale - 1f) > 0.001f) {
            rendered = "\\scalebox{" + decimal(scale) + "}{" + rendered + "}";
        }
        return rendered;
    }

    private static String trafficLightRow(JSONObject row, Config config) {
        String active = token(row.optString("active", "green"), 12);
        if (!setOf("red", "yellow", "green", "none").contains(active)) active = "green";
        String red = "red".equals(active) ? config.theme.danger : config.theme.track;
        String yellow = "yellow".equals(active) ? config.theme.warning : config.theme.track;
        String green = "green".equals(active) ? config.theme.success : config.theme.track;
        String body = "\\begin{array}{c}"
                + colored(red, "{\\Large \\bullet}") + " \\\\ "
                + colored(yellow, "{\\Large \\bullet}") + " \\\\ "
                + colored(green, "{\\Large \\bullet}")
                + "\\end{array}";
        return "\\fcolorbox{" + config.theme.border + "}{"
                + config.theme.background + "}{" + body + "}";
    }

    private static String batteryRow(JSONObject row, Config config) {
        double maximum = doubleNumber(row, "max", 100d, 0.000001d, 1000000000d);
        double value = doubleNumber(row, "value", 0d, 0d, maximum);
        double ratio = Math.max(0d, Math.min(1d, value / maximum));
        float width = number(row, "width_pt", 72f, 24f, 180f);
        float height = number(row, "height_pt", 13f, 5f, 32f);
        String fill = ratio <= 0.2d ? config.theme.danger
                : (ratio <= 0.5d ? config.theme.warning : config.theme.success);
        fill = resolveColor(row.optString("color", fill), config.theme);
        float filled = (float) (width * ratio);
        StringBuilder level = new StringBuilder();
        if (filled > 0.05f) level.append(rule(fill, filled, height));
        if (width - filled > 0.05f) {
            level.append(rule(config.theme.track, width - filled, height));
        }
        String percent = formatNumber(ratio * 100d) + "%";
        return "\\fbox{" + level + "}"
                + colored(config.theme.border,
                "\\rule{3pt}{" + decimal(height * 0.52f) + "pt}")
                + " \\quad " + colored(fill,
                "\\mathbf{\\text{" + escapeText(percent) + "}}");
    }

    private static String signalRow(JSONObject row, Config config) {
        int bars = integer(row, "bars", 5, 3, 8);
        int strength = integer(row, "value", bars, 0, bars);
        String active = resolveColor(row.optString("color", "accent"), config.theme);
        float width = number(row, "bar_width_pt", 5f, 2f, 14f);
        float step = number(row, "height_step_pt", 4f, 1.5f, 10f);
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < bars; index++) {
            if (index > 0) output.append("\\hspace{2pt}");
            output.append(rule(index < strength ? active : config.theme.track,
                    width, step * (index + 1)));
        }
        String label = cleanText(row.optString("label", ""), 80);
        if (label.length() > 0) {
            output.append(" \\quad ").append(colored(config.theme.muted,
                    "\\text{" + escapeText(label) + "}"));
        }
        return output.toString();
    }

    private static String gaugeRow(JSONObject row, Config config) {
        double maximum = doubleNumber(row, "max", 100d, 0.000001d, 1000000000d);
        double value = doubleNumber(row, "value", 0d, 0d, maximum);
        double ratio = Math.max(0d, Math.min(1d, value / maximum));
        float angle = (float) (-130d + ratio * 260d);
        String color = resolveColor(row.optString("color", "accent"), config.theme);
        String display = cleanText(row.optString("display", ""), 40);
        if (display.length() == 0) display = formatNumber(ratio * 100d) + "%";
        String label = cleanText(row.optString("label", ""), 80);
        return "\\begin{array}{c}"
                + "\\mathrlap{" + colored(config.theme.track,
                "{\\Huge \\bigcirc}") + "}"
                + colored(color, "\\rotatebox{" + decimal(angle)
                + "}{\\longrightarrow}")
                + " \\\\ " + colored(color,
                "\\mathbf{\\text{" + escapeText(display) + "}}")
                + (label.length() == 0 ? "" : " \\\\ "
                + colored(config.theme.muted,
                "\\text{" + escapeText(label) + "}"))
                + "\\end{array}";
    }

    private static String pixelArtRow(JSONObject row, Config config) {
        JSONArray pixels = row.optJSONArray("pixels");
        if (pixels == null) pixels = row.optJSONArray("grid");
        if (pixels == null) pixels = row.optJSONArray("data");
        if (pixels == null) {
            String multiline = row.optString("pixels", "");
            if (multiline.indexOf('\n') >= 0 || multiline.indexOf('\r') >= 0) {
                pixels = new JSONArray();
                String[] rawLines = multiline.split("\\r?\\n", -1);
                int first = 0;
                int last = rawLines.length;
                while (first < last && rawLines[first].length() == 0) first++;
                while (last > first && rawLines[last - 1].length() == 0) last--;
                for (int index = first; index < last; index++) {
                    pixels.put(rawLines[index]);
                }
            }
        }
        JSONArray palette = row.optJSONArray("palette");
        if (palette == null) palette = row.optJSONArray("colors");
        JSONObject paletteMap = row.optJSONObject("palette");
        if (paletteMap == null) paletteMap = row.optJSONObject("colors");
        if (pixels == null || pixels.length() == 0 || pixels.length() > 18) {
            return null;
        }

        HashMap<Character, String> colors = new HashMap<>();
        HashSet<Character> transparent = new HashSet<>();
        if (palette != null) {
            if (palette.length() == 0 || palette.length() > 16) return null;
            for (int index = 0; index < palette.length(); index++) {
                char code = paletteCode(index);
                String requested = palette.optString(index, "accent");
                if (isTransparentColor(requested)) transparent.add(code);
                else colors.put(code, resolveColor(requested, config.theme));
            }
        } else if (paletteMap != null) {
            if (paletteMap.length() == 0 || paletteMap.length() > 16) return null;
            Iterator<String> keys = paletteMap.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key == null || key.length() != 1) return null;
                char code = Character.toUpperCase(key.charAt(0));
                if (code == '.' || Character.isWhitespace(code)) return null;
                String requested = paletteMap.optString(key, "accent");
                if (isTransparentColor(requested)) transparent.add(code);
                else colors.put(code, resolveColor(requested, config.theme));
            }
        } else {
            // A one-colour sketch is unambiguous enough to recover safely when the model omitted
            // palette. This is visual-only and cannot broaden any device-side permission.
            colors.put('0', config.theme.accent);
            colors.put('1', config.theme.accent);
            colors.put('#', config.theme.accent);
            colors.put('X', config.theme.accent);
        }

        String fallbackColor = colors.isEmpty()
                ? config.theme.accent : colors.values().iterator().next();
        int columns = 0;
        ArrayList<String> lines = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < pixels.length(); rowIndex++) {
            String line = normalizePixelLine(pixels.optString(rowIndex, ""));
            if (line.length() == 0 || line.length() > 24) return null;
            columns = Math.max(columns, line.length());
            for (int column = 0; column < line.length(); column++) {
                char code = Character.toUpperCase(line.charAt(column));
                if (code == '.' || transparent.contains(code)) continue;
                if (!colors.containsKey(code)) {
                    // Common model-authored bitmap glyphs all mean "filled". Alias them to the
                    // first safe palette colour instead of dropping the complete tool call.
                    if (code == '#' || code == 'X' || code == '@'
                            || code == '*' || code == 'O') {
                        colors.put(code, fallbackColor);
                    } else {
                        return null;
                    }
                }
            }
            lines.add(line);
        }
        float cell = number(row, "cell_pt", 5f, 2f, 14f);
        StringBuilder art = new StringBuilder("\\begin{array}{");
        for (int column = 0; column < columns; column++) art.append('c');
        art.append('}');
        for (int rowIndex = 0; rowIndex < lines.size(); rowIndex++) {
            if (rowIndex > 0) art.append(" \\\\ ");
            String line = lines.get(rowIndex);
            for (int column = 0; column < columns; column++) {
                if (column > 0) art.append(" & ");
                char code = column < line.length()
                        ? Character.toUpperCase(line.charAt(column)) : '.';
                if (code == '.' || transparent.contains(code)) {
                    art.append("\\phantom{\\rule{").append(decimal(cell))
                            .append("pt}{").append(decimal(cell)).append("pt}}");
                } else {
                    art.append(rule(colors.get(code), cell, cell));
                }
            }
        }
        art.append("\\end{array}");
        return art.toString();
    }

    private static char paletteCode(int index) {
        return index < 10 ? (char) ('0' + index) : (char) ('A' + index - 10);
    }

    private static boolean isTransparentColor(String value) {
        String color = value == null ? "" : value.trim().toLowerCase(Locale.US);
        return "transparent".equals(color) || "none".equals(color)
                || "clear".equals(color) || "#00000000".equals(color);
    }

    private static String normalizePixelLine(String value) {
        if (value == null || value.length() == 0) return "";
        StringBuilder clean = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char code = value.charAt(index);
            if (code == '\u0000' || code == '\r' || code == '\n') return "";
            clean.append(Character.isWhitespace(code) ? '.' : code);
        }
        return clean.toString();
    }

    private static String ornamentRow(JSONObject row, Config config) {
        String symbolName = token(row.optString("symbol", "star"), 24);
        String symbol;
        if ("diamond".equals(symbolName)) symbol = "\\lozenge";
        else if ("dot".equals(symbolName)) symbol = "\\bullet";
        else if ("circle".equals(symbolName)) symbol = "\\circ";
        else if ("heart".equals(symbolName)) symbol = "\\heartsuit";
        else if ("triangle".equals(symbolName)) symbol = "\\triangle";
        else symbol = "\\star";
        int count = integer(row, "count", 7, 1, 24);
        String first = resolveColor(row.optString("color", "accent"), config.theme);
        String second = resolveColor(row.optString("alternate_color", "muted"), config.theme);
        String center = cleanText(row.optString("text", ""), 80);
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) output.append(" \\; ");
            if (center.length() > 0 && index == count / 2) {
                output.append(colored(config.theme.text,
                        "\\mathbf{\\text{" + escapeText(center) + "}}"));
            } else {
                output.append(colored((index & 1) == 0 ? first : second, symbol));
            }
        }
        return output.toString();
    }

    private static String flowRow(JSONObject row, Config config) {
        JSONArray nodes = row.optJSONArray("nodes");
        if (nodes == null || nodes.length() < 2 || nodes.length() > 6) return null;
        String direction = token(row.optString("direction", "right"), 12);
        String nodeColor = resolveColor(row.optString("node_color", "text"), config.theme);
        String arrowColor = resolveColor(row.optString("arrow_color", "accent"), config.theme);
        if ("down".equals(direction)) {
            StringBuilder vertical = new StringBuilder("\\begin{array}{c}");
            for (int index = 0; index < nodes.length(); index++) {
                String node = cleanText(nodes.optString(index, ""), 80);
                if (node.length() == 0) return null;
                if (index > 0) {
                    vertical.append(" \\\\ ")
                            .append(colored(arrowColor, "\\downarrow"))
                            .append(" \\\\ ");
                }
                vertical.append(colored(nodeColor,
                        "\\boxed{\\text{ " + escapeText(node) + " }}"));
            }
            return vertical.append("\\end{array}").toString();
        }
        StringBuilder horizontal = new StringBuilder();
        for (int index = 0; index < nodes.length(); index++) {
            String node = cleanText(nodes.optString(index, ""), 80);
            if (node.length() == 0) return null;
            if (index > 0) horizontal.append(" ")
                    .append(colored(arrowColor, "\\longrightarrow"))
                    .append(" ");
            horizontal.append(colored(nodeColor,
                    "\\boxed{\\text{ " + escapeText(node) + " }}"));
        }
        return horizontal.toString();
    }

    private static String progressRow(JSONObject row, Config config, boolean segmented) {
        String label = cleanText(row.optString("label", ""), 100);
        double maximum = doubleNumber(row, "max", 100d, 0.000001d, 1000000000d);
        double value = doubleNumber(row, "value", 0d, -1000000000d, 1000000000d);
        double ratio = Math.max(0d, Math.min(1d, value / maximum));
        float width = number(row, "width_pt", config.widthPt, 30f, 260f);
        float height = number(row, "height_pt", config.barHeightPt, 2f, 18f);
        String fillColor = resolveColor(row.optString("color", "accent"), config.theme);
        String trackColor = resolveColor(row.optString("track_color", "track"), config.theme);
        StringBuilder bar = new StringBuilder();
        if (segmented) {
            int segments = integer(row, "segments", 10, 2, 20);
            int filled = (int) Math.round(ratio * segments);
            float cell = Math.max(1f, (width - (segments - 1)) / segments);
            JSONArray colors = row.optJSONArray("colors");
            for (int index = 0; index < segments; index++) {
                if (index > 0) bar.append("\\hspace{1pt}");
                String cellColor = index < filled ? fillColor : trackColor;
                if (index < filled && colors != null && colors.length() > 0) {
                    cellColor = resolveColor(
                            colors.optString(index % colors.length(), fillColor), config.theme);
                }
                bar.append(rule(cellColor, cell, height));
            }
        } else {
            float filledWidth = (float) (width * ratio);
            float emptyWidth = Math.max(0f, width - filledWidth);
            if (filledWidth > 0.05f) bar.append(rule(fillColor, filledWidth, height));
            if (emptyWidth > 0.05f) bar.append(rule(trackColor, emptyWidth, height));
        }
        String format = token(row.optString("format", "percent"), 16);
        String display;
        if ("none".equals(format)) display = "";
        else if ("fraction".equals(format)) {
            display = formatNumber(value) + "/" + formatNumber(maximum);
        } else if ("value".equals(format)) display = formatNumber(value);
        else display = formatNumber(ratio * 100d) + "%";
        display += cleanText(row.optString("suffix", ""), 24);
        String prefix = label.length() == 0 ? "" : colored(config.theme.muted,
                "\\text{" + escapeText(label + "：") + "}") + " \\quad ";
        String brackets = token(row.optString("bar_style", "plain"), 12);
        String renderedBar = bar.toString();
        if ("bracket".equals(brackets)) {
            renderedBar = colored(fillColor, "[") + renderedBar
                    + colored(fillColor, "]");
        } else if ("box".equals(brackets)) {
            renderedBar = "\\fbox{" + renderedBar + "}";
        }
        return prefix + renderedBar + (display.length() == 0 ? "" : " \\quad "
                + colored(fillColor, "\\text{" + escapeText(display) + "}"));
    }

    private static String matrixRow(JSONObject row, Config config) {
        JSONArray values = row.optJSONArray("values");
        if (values == null || values.length() == 0 || values.length() > 5) return null;
        String style = token(row.optString("matrix_style", "bmatrix"), 16);
        if (!setOf("matrix", "smallmatrix", "pmatrix", "bmatrix", "Bmatrix",
                "vmatrix", "Vmatrix").contains(style)) style = "bmatrix";
        int columns = -1;
        StringBuilder matrix = new StringBuilder("\\begin{" + style + "}");
        for (int rowIndex = 0; rowIndex < values.length(); rowIndex++) {
            JSONArray cells = values.optJSONArray(rowIndex);
            if (cells == null || cells.length() == 0 || cells.length() > 5) return null;
            if (columns < 0) columns = cells.length();
            if (cells.length() != columns) return null;
            if (rowIndex > 0) matrix.append(" \\\\ ");
            for (int column = 0; column < cells.length(); column++) {
                if (column > 0) matrix.append(" & ");
                String cell = formulaOrText(String.valueOf(cells.opt(column)),
                        row.optBoolean("math", false));
                if (cell == null || cell.length() == 0) return null;
                matrix.append(cell);
            }
        }
        matrix.append("\\end{").append(style).append("}");
        String label = cleanText(row.optString("label", ""), 100);
        return (label.length() == 0 ? "" : colored(config.theme.muted,
                "\\text{" + escapeText(label + "：") + "}") + " \\quad ")
                + colored(resolveColor(row.optString("color", "text"), config.theme),
                matrix.toString());
    }

    private static String listRow(JSONObject row, Config config) {
        JSONArray items = row.optJSONArray("items");
        if (items == null || items.length() == 0 || items.length() > 8) return null;
        String marker = cleanText(row.optString("marker", "•"), 4);
        StringBuilder list = new StringBuilder("\\begin{array}{l}");
        for (int index = 0; index < items.length(); index++) {
            String item = cleanText(items.optString(index, ""), 220);
            if (item.length() == 0) return null;
            if (index > 0) list.append(" \\\\ ");
            list.append("\\text{").append(escapeText(marker + " " + item)).append("}");
        }
        list.append("\\end{array}");
        return colored(resolveColor(row.optString("color", "text"), config.theme),
                list.toString());
    }

    private static String tableRow(JSONObject row, Config config) {
        JSONArray values = row.optJSONArray("values");
        if (values == null) values = row.optJSONArray("cells");
        if (values == null || values.length() == 0 || values.length() > 6) return null;
        int columns = -1;
        StringBuilder table = new StringBuilder("\\begin{array}{");
        ArrayList<JSONArray> parsedRows = new ArrayList<>();
        for (int rowIndex = 0; rowIndex < values.length(); rowIndex++) {
            JSONArray cells = values.optJSONArray(rowIndex);
            if (cells == null || cells.length() == 0 || cells.length() > 5) return null;
            if (columns < 0) columns = cells.length();
            if (cells.length() != columns) return null;
            parsedRows.add(cells);
        }
        for (int column = 0; column < columns; column++) table.append('c');
        table.append('}');
        boolean header = row.optBoolean("header", true);
        for (int rowIndex = 0; rowIndex < parsedRows.size(); rowIndex++) {
            if (rowIndex > 0) table.append(" \\\\ ");
            JSONArray cells = parsedRows.get(rowIndex);
            for (int column = 0; column < cells.length(); column++) {
                if (column > 0) table.append(" & ");
                String cell = cleanText(cells.optString(column, ""), 100);
                if (cell.length() == 0) cell = " ";
                String text = "\\text{" + escapeText(cell) + "}";
                if (header && rowIndex == 0) text = "\\mathbf{" + text + "}";
                table.append(colored(
                        header && rowIndex == 0 ? config.theme.accent : config.theme.text,
                        text));
            }
            if (header && rowIndex == 0 && parsedRows.size() > 1) {
                table.append(" \\\\ \\hline");
            }
        }
        table.append("\\end{array}");
        return table.toString();
    }

    private static String sparklineRow(JSONObject row, Config config) {
        JSONArray values = row.optJSONArray("values");
        if (values == null || values.length() < 2 || values.length() > 20) return null;
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double[] parsed = new double[values.length()];
        for (int index = 0; index < values.length(); index++) {
            double value;
            try {
                value = values.getDouble(index);
            } catch (Throwable ignored) {
                return null;
            }
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;
            parsed[index] = value;
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        String levels = "▁▂▃▄▅▆▇█";
        StringBuilder graph = new StringBuilder();
        double range = Math.max(0.000001d, maximum - minimum);
        for (double value : parsed) {
            int level = (int) Math.round((value - minimum) / range * (levels.length() - 1));
            graph.append(levels.charAt(Math.max(0, Math.min(levels.length() - 1, level))));
        }
        String label = cleanText(row.optString("label", ""), 100);
        return (label.length() == 0 ? "" : colored(config.theme.muted,
                "\\text{" + escapeText(label + "：") + "}") + " \\quad ")
                + colored(resolveColor(row.optString("color", "accent"), config.theme),
                "\\text{" + escapeText(graph.toString()) + "}");
    }

    private static String styledText(String text, String color, JSONObject row,
                                     String defaultStyle, String defaultSize) {
        String content = "\\text{" + escapeText(text) + "}";
        HashSet<String> styles = new HashSet<>();
        String style = token(row.optString("style", defaultStyle), 80);
        if (style.length() > 0) styles.addAll(Arrays.asList(style.split("[, +]")));
        JSONArray rawStyles = row.optJSONArray("styles");
        if (rawStyles != null && rawStyles.length() <= 8) {
            for (int index = 0; index < rawStyles.length(); index++) {
                String item = token(rawStyles.optString(index, ""), 20);
                if (item.length() > 0) styles.add(item);
            }
        }
        if (styles.contains("bold")) content = "\\mathbf{" + content + "}";
        if (styles.contains("italic")) content = "\\mathit{" + content + "}";
        if (styles.contains("sans")) content = "\\mathsf{" + content + "}";
        if (styles.contains("mono")) content = "\\mathtt{" + content + "}";
        if (styles.contains("underline")) content = "\\underline{" + content + "}";
        if (styles.contains("overline")) content = "\\overline{" + content + "}";
        if (styles.contains("boxed")) content = "\\boxed{" + content + "}";
        if (styles.contains("circled") && text.length() <= 4) {
            content = "\\textcircled{" + content + "}";
        }
        return colored(color, sized(sizeToken(
                row.optString("size", defaultSize)), content));
    }

    private static String safeFormula(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.length() == 0 || value.length() > 1200
                || value.indexOf('\u0000') >= 0 || value.indexOf("$$") >= 0
                || value.indexOf('&') >= 0 || value.indexOf('%') >= 0
                || value.indexOf(HeartbeatToolProtocol.CONTROL_START) >= 0) return null;
        int depth = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current == '{') {
                depth++;
                if (depth > 16) return null;
            } else if (current == '}') {
                depth--;
                if (depth < 0) return null;
            } else if (current == '\\') {
                int commandStart = index + 1;
                if (commandStart >= value.length()) return null;
                int cursor = commandStart;
                while (cursor < value.length()) {
                    char part = value.charAt(cursor);
                    if (!Character.isLetter(part) && part != '@') break;
                    cursor++;
                }
                String command;
                if (cursor == commandStart) {
                    command = String.valueOf(value.charAt(commandStart));
                    cursor = commandStart + 1;
                    if (!",:;! \\{}_%#$".contains(command)) return null;
                } else {
                    command = value.substring(commandStart, cursor);
                    if (!SAFE_FORMULA_COMMANDS.contains(command)) return null;
                }
                index = cursor - 1;
            }
        }
        return depth == 0 ? value.replace('\r', ' ').replace('\n', ' ') : null;
    }

    private static String formulaOrText(String value, boolean math) {
        String cleaned = cleanText(value, 500);
        if (cleaned.length() == 0) return "";
        if (!math) return "\\text{" + escapeText(cleaned) + "}";
        return safeFormula(value);
    }

    private static Theme overrideTheme(Theme base, JSONObject panel) {
        if (base == null) base = THEMES.get("cyan");
        return new Theme(
                color(panel.optString("border_color", ""), base.border),
                color(panel.optString("background_color", ""), base.background),
                color(panel.optString("title_color", ""), base.title),
                color(panel.optString("text_color", ""), base.text),
                color(panel.optString("muted_color", ""), base.muted),
                color(panel.optString("accent_color", ""), base.accent),
                color(panel.optString("track_color", ""), base.track),
                color(panel.optString("success_color", ""), base.success),
                color(panel.optString("warning_color", ""), base.warning),
                color(panel.optString("danger_color", ""), base.danger));
    }

    private static String frame(String inner, Config config) {
        if ("none".equals(config.frame)) {
            return "\\colorbox{" + config.theme.background + "}{" + inner + "}";
        }
        if ("double".equals(config.frame) || "shadow".equals(config.frame)
                || "oval".equals(config.frame)) {
            String command = "double".equals(config.frame) ? "doublebox"
                    : ("shadow".equals(config.frame) ? "shadowbox" : "ovalbox");
            return "\\colorbox{" + config.theme.background + "}{"
                    + colored(config.theme.border,
                    "\\" + command + "{" + inner + "}") + "}";
        }
        return "\\fcolorbox{" + config.theme.border + "}{"
                + config.theme.background + "}{" + inner + "}";
    }

    private static String resolveColor(String requested, Theme theme) {
        String value = requested == null ? "" : requested.trim();
        if (value.startsWith("#")) return color(value, theme.text);
        if ("border".equals(value)) return theme.border;
        if ("background".equals(value)) return theme.background;
        if ("title".equals(value)) return theme.title;
        if ("muted".equals(value)) return theme.muted;
        if ("accent".equals(value) || value.length() == 0) return theme.accent;
        if ("track".equals(value)) return theme.track;
        if ("success".equals(value)) return theme.success;
        if ("warning".equals(value)) return theme.warning;
        if ("danger".equals(value) || "error".equals(value)) return theme.danger;
        return theme.text;
    }

    private static String color(String requested, String fallback) {
        if (requested == null) return fallback;
        String value = requested.trim().toUpperCase(Locale.US);
        if (value.matches("#[0-9A-F]{6}")) return value;
        return fallback;
    }

    private static String colored(String color, String content) {
        return "\\textcolor{" + color + "}{" + content + "}";
    }

    private static String sized(String size, String content) {
        String command;
        if ("normal".equals(size)) return content;
        if ("xlarge".equals(size)) command = "Large";
        else command = size;
        return "{\\" + command + " " + content + "}";
    }

    private static String rule(String color, float width, float height) {
        return colored(color, "\\rule{" + decimal(width) + "pt}{"
                + decimal(height) + "pt}");
    }

    private static String arrayAlignment(String align) {
        if ("left".equals(align)) return "l";
        if ("right".equals(align)) return "r";
        return "c";
    }

    private static String sizeToken(String value) {
        String size = token(value, 20);
        return SIZES.contains(size) ? size : "normal";
    }

    private static String defaultThemeForPreset(String preset) {
        if ("character".equals(preset)) return "rose";
        if ("task".equals(preset)) return "ocean";
        if ("timeline".equals(preset)) return "amber";
        if ("comparison".equals(preset)) return "violet";
        if ("terminal".equals(preset)) return "terminal";
        if ("alert".equals(preset)) return "crimson";
        if ("report".equals(preset)) return "monochrome";
        if ("game".equals(preset)) return "forest";
        if ("science".equals(preset)) return "ice";
        return "cyan";
    }

    private static String defaultFrameForPreset(String preset) {
        if ("character".equals(preset) || "alert".equals(preset)) return "double";
        if ("timeline".equals(preset)) return "shadow";
        if ("game".equals(preset)) return "oval";
        return "single";
    }

    private static Map<String, Theme> createThemes() {
        HashMap<String, Theme> themes = new HashMap<>();
        themes.put("cyan", new Theme("#00FFFF", "#0F0F0F", "#00FFFF",
                "#E6FFFF", "#A9A9A9", "#34D399", "#203638",
                "#34D399", "#FFD166", "#FF5A5F"));
        themes.put("ocean", new Theme("#38BDF8", "#071A2B", "#7DD3FC",
                "#E0F2FE", "#7B9BB4", "#22D3EE", "#15384D",
                "#2DD4BF", "#FBBF24", "#FB7185"));
        themes.put("violet", new Theme("#A78BFA", "#171126", "#C4B5FD",
                "#F5F3FF", "#9A8EB7", "#D946EF", "#34254A",
                "#6EE7B7", "#FCD34D", "#FB7185"));
        themes.put("rose", new Theme("#FB7185", "#241016", "#FDA4AF",
                "#FFF1F2", "#B98A94", "#F472B6", "#4A202A",
                "#4ADE80", "#FBBF24", "#F43F5E"));
        themes.put("forest", new Theme("#4ADE80", "#0B1C12", "#86EFAC",
                "#ECFDF5", "#82A78D", "#22C55E", "#1D3A27",
                "#34D399", "#FACC15", "#F87171"));
        themes.put("amber", new Theme("#F59E0B", "#21170A", "#FCD34D",
                "#FFF7D6", "#B7A076", "#FB923C", "#493416",
                "#4ADE80", "#FBBF24", "#EF4444"));
        themes.put("terminal", new Theme("#22C55E", "#050A06", "#4ADE80",
                "#BBF7D0", "#568A64", "#22C55E", "#16341E",
                "#22C55E", "#EAB308", "#EF4444"));
        themes.put("crimson", new Theme("#EF4444", "#210A0A", "#F87171",
                "#FEE2E2", "#B68A8A", "#FB7185", "#491919",
                "#4ADE80", "#F59E0B", "#DC2626"));
        themes.put("monochrome", new Theme("#B8B8B8", "#151515", "#F5F5F5",
                "#E5E5E5", "#8A8A8A", "#D4D4D4", "#333333",
                "#B8D8BE", "#D8C99B", "#D9A0A0"));
        themes.put("ice", new Theme("#A5F3FC", "#10202A", "#CFFAFE",
                "#ECFEFF", "#8BB4BF", "#67E8F9", "#25404A",
                "#5EEAD4", "#FDE68A", "#FDA4AF"));
        themes.put("light", new Theme("#3B82F6", "#F8FAFC", "#1D4ED8",
                "#111827", "#64748B", "#2563EB", "#CBD5E1",
                "#16A34A", "#D97706", "#DC2626"));
        themes.put("candy", new Theme("#F472B6", "#24152B", "#F9A8D4",
                "#FAE8FF", "#B69AC2", "#C084FC", "#4A2B55",
                "#6EE7B7", "#FDE047", "#FB7185"));
        return Collections.unmodifiableMap(themes);
    }

    private static String cleanText(String value, int maximum) {
        if (value == null) return "";
        String text = value.replace('\u0000', ' ').replace('\r', ' ')
                .replace('\n', ' ').trim();
        if (text.length() > maximum) text = text.substring(0, maximum);
        return text;
    }

    private static String escapeText(String value) {
        String text = value == null ? "" : value;
        StringBuilder escaped = new StringBuilder(text.length() + 16);
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current == '\\') escaped.append('＼');
            else if (current == '{') escaped.append('｛');
            else if (current == '}') escaped.append('｝');
            else if (current == '#') escaped.append('＃');
            else if (current == '$') escaped.append('＄');
            else if (current == '&') escaped.append("\\&");
            else if (current == '%') escaped.append("\\%");
            else if (current == '_') escaped.append('＿');
            else if (current == '^') escaped.append('＾');
            else if (current == '~') escaped.append('～');
            else escaped.append(current);
        }
        return escaped.toString();
    }

    private static String token(String value, int maximum) {
        if (value == null) return "";
        String token = value.trim().toLowerCase(Locale.US);
        if (token.length() > maximum) token = token.substring(0, maximum);
        return token.matches("[a-z0-9_+ ,.-]*") ? token : "";
    }

    private static float number(JSONObject value, String key, float fallback,
                                float minimum, float maximum) {
        double parsed = value.optDouble(key, fallback);
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) parsed = fallback;
        return (float) Math.max(minimum, Math.min(maximum, parsed));
    }

    private static double doubleNumber(JSONObject value, String key, double fallback,
                                       double minimum, double maximum) {
        double parsed = value.optDouble(key, fallback);
        if (Double.isNaN(parsed) || Double.isInfinite(parsed)) parsed = fallback;
        return Math.max(minimum, Math.min(maximum, parsed));
    }

    private static int integer(JSONObject value, String key, int fallback,
                               int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value.optInt(key, fallback)));
    }

    private static String decimal(float value) {
        String formatted = String.format(Locale.US, "%.3f", value);
        while (formatted.endsWith("0")) {
            formatted = formatted.substring(0, formatted.length() - 1);
        }
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted.length() == 0 || "-0".equals(formatted) ? "0" : formatted;
    }

    private static String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.000001d) {
            return String.valueOf((long) Math.rint(value));
        }
        String formatted = String.format(Locale.US, "%.2f", value);
        while (formatted.endsWith("0")) formatted = formatted.substring(0, formatted.length() - 1);
        if (formatted.endsWith(".")) formatted = formatted.substring(0, formatted.length() - 1);
        return formatted;
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }
}
