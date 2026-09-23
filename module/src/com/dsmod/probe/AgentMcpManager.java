package com.dsmod.probe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small independent MCP Streamable-HTTP client used by the in-chat Agent. */
final class AgentMcpManager {
    static final String TOOL_PREFIX = "mcp__";
    static final String TRANSPORT_HTTP = "streamable_http";
    static final String TRANSPORT_SSE = "sse";

    private static final String DIRECTORY =
            "/data/data/com.deepseek.chat/files/deekseep_agent";
    private static final String FILE_PATH = DIRECTORY + "/mcp.json";
    private static final Object LOCK = new Object();
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Deekseep-MCP");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN;
    private static final AtomicLong REQUEST_IDS = new AtomicLong(1L);
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    static { Handler h; try { h = new Handler(Looper.getMainLooper()); } catch (Throwable t) { h = null; } MAIN = h; }

    enum State { DISABLED, CONNECTING, CONNECTED, ERROR }

    interface StatusCallback { void onStatus(Status status); }
    interface ResultCallback { void onResult(boolean ok, String output, String detail); }

    static final class Status {
        final State state;
        final String detail;
        final int toolCount;
        Status(State state, String detail, int toolCount) {
            this.state = state;
            this.detail = detail == null ? "" : detail;
            this.toolCount = toolCount;
        }
    }

    static final class Header {
        final String name;
        final String value;
        Header(String name, String value) {
            this.name = cleanHeaderName(name);
            this.value = cleanHeaderValue(value);
        }
    }

    static final class Config {
        final boolean enabled;
        final String name;
        final String url;
        final String transport;
        final List<Header> headers;
        final Map<String, Tool> tools;

        Config(boolean enabled, String name, String url, String transport,
               List<Header> headers, Map<String, Tool> tools) {
            this.enabled = enabled;
            this.name = cleanName(name);
            this.url = cleanUrl(url);
            this.transport = TRANSPORT_SSE.equals(transport)
                    ? TRANSPORT_SSE : TRANSPORT_HTTP;
            this.headers = Collections.unmodifiableList(new ArrayList<>(
                    headers == null ? Collections.<Header>emptyList() : headers));
            this.tools = Collections.unmodifiableMap(new LinkedHashMap<>(
                    tools == null ? Collections.<String, Tool>emptyMap() : tools));
        }

        Config with(boolean enabled, String name, String url, String transport,
                    List<Header> headers) {
            return new Config(enabled, name, url, transport, headers, tools);
        }
    }

    static final class Tool {
        final String name;
        final String title;
        final String description;
        final JSONObject schema;
        final boolean enabled;
        Tool(String name, String description, JSONObject schema, boolean enabled) {
            this(name, name, description, schema, enabled);
        }
        Tool(String name, String title, String description,
             JSONObject schema, boolean enabled) {
            this.name = cleanToolToken(name);
            String cleanTitle = trim(title, 120);
            this.title = cleanTitle.length() == 0 ? this.name : cleanTitle;
            this.description = trim(description, 1200);
            this.schema = schema == null ? new JSONObject() : schema;
            this.enabled = enabled;
        }
    }

    private static volatile Config cached;
    private static volatile List<Config> cachedProfiles;
    private static volatile long cachedModified = Long.MIN_VALUE;
    private static volatile Status status = new Status(State.DISABLED, "未连接", 0);
    private static volatile String sessionId = "";
    private static volatile String connectedKey = "";
    private static volatile LegacySseSession legacySse;

    private AgentMcpManager() {}

    static Config defaults() {
        return new Config(false, "MCP", "", TRANSPORT_HTTP,
                Collections.<Header>emptyList(), Collections.<String, Tool>emptyMap());
    }

    static Config load() {
        List<Config> all = loadAll();
        return all.isEmpty() ? defaults() : all.get(0);
    }

    /** MCP is a collection of independently enabled servers.  Legacy one-server files migrate
     * transparently to a one-item collection. */
    static List<Config> loadAll() {
        synchronized (LOCK) {
            File file = new File(FILE_PATH);
            long modified = file.isFile() ? file.lastModified() : -1L;
            if (cachedProfiles != null && modified == cachedModified) {
                return Collections.unmodifiableList(new ArrayList<>(cachedProfiles));
            }
            List<Config> value = Collections.emptyList();
            if (file.isFile() && file.length() > 0L && file.length() <= 256L * 1024L) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    StringBuilder text = new StringBuilder((int) file.length());
                    String line;
                    while ((line = reader.readLine()) != null) text.append(line);
                    value = decodeAll(text.toString());
                } catch (Throwable ignored) {}
            }
            cached = value.isEmpty() ? defaults() : value.get(0);
            cachedProfiles = Collections.unmodifiableList(new ArrayList<>(value));
            cachedModified = modified;
            return Collections.unmodifiableList(new ArrayList<>(cachedProfiles));
        }
    }

    static boolean save(Config value) {
        return saveAll(value == null ? Collections.<Config>emptyList()
                : Collections.singletonList(value));
    }

    static boolean saveAll(List<Config> values) {
        ArrayList<Config> safeList = new ArrayList<>();
        if (values != null) for (Config value : values) {
            if (value != null) safeList.add(value);
        }
        synchronized (LOCK) {
            File dir = new File(DIRECTORY);
            File target = new File(FILE_PATH);
            File temporary = new File(FILE_PATH + ".tmp");
            try {
                if (!dir.isDirectory() && !dir.mkdirs()) return false;
                try (FileWriter writer = new FileWriter(temporary, false)) {
                    writer.write(encodeAll(safeList));
                    writer.write('\n');
                }
                if (!temporary.renameTo(target)) return false;
                cached = safeList.isEmpty() ? defaults() : safeList.get(0);
                cachedProfiles = Collections.unmodifiableList(new ArrayList<>(safeList));
                cachedModified = target.lastModified();
                connectedKey = "";
                sessionId = "";
                if (safeList.isEmpty()) setStatus(State.DISABLED, "未连接", 0);
                return true;
            } catch (Throwable ignored) {
                return false;
            } finally {
                if (temporary.exists() && !temporary.equals(target)) temporary.delete();
            }
        }
    }

    static Status status() { return status; }

    static void startIfEnabled(Context context) {
        boolean hasEnabled = false;
        for (Config config : loadAll()) if (config.enabled) { hasEnabled = true; break; }
        if (hasEnabled && STARTED.compareAndSet(false, true)) {
            probeAllAsync(context, null);
        }
    }

    static void probeAsync(final Context context, final StatusCallback callback) {
        final Config config = load();
        if (!config.enabled) {
            setStatus(State.DISABLED, "未连接", 0);
            deliverStatus(callback);
            return;
        }
        setStatus(State.CONNECTING, "正在连接…", config.tools.size());
        deliverStatus(callback);
        EXECUTOR.execute(() -> {
            try {
                Config synced = connectAndSync(config);
                saveSyncedTools(synced);
                setStatus(State.CONNECTED, "已连接", synced.tools.size());
            } catch (Throwable error) {
                connectedKey = "";
                sessionId = "";
                setStatus(State.ERROR, safeError(error), 0);
            }
            deliverStatus(callback);
        });
    }

    static void probeAllAsync(final Context context, final StatusCallback callback) {
        final List<Config> configs = loadAll();
        if (configs.isEmpty()) { setStatus(State.DISABLED, "未连接", 0); deliverStatus(callback); return; }
        EXECUTOR.execute(() -> {
            int online = 0, tools = 0; String lastError = "";
            for (Config config : configs) {
                if (!config.enabled) continue;
                try { Config synced = connectAndSync(config); saveSyncedTools(synced); online++; tools += synced.tools.size(); }
                catch (Throwable error) { lastError = safeError(error); }
            }
            setStatus(online > 0 ? State.CONNECTED : State.ERROR,
                    online > 0 ? "已连接" : lastError, tools);
            deliverStatus(callback);
        });
    }

    static boolean isDynamicTool(String name) {
        return name != null && name.startsWith(TOOL_PREFIX);
    }

    static boolean isToolEnabled(String fullName) {
        if (!isDynamicTool(fullName)) return false;
        if (!AgentToolConfig.load().enabledTools.contains(
                HeartbeatToolProtocol.TOOL_MCP)) return false;
        for (Config config : loadAll()) {
            Tool tool = resolveTool(config, fullName);
            if (config.enabled && tool != null && tool.enabled) return true;
        }
        return false;
    }

    static String displayName(String fullName) {
        for (Config config : loadAll()) {
            Tool tool = resolveTool(config, fullName);
            if (tool != null) return tool.title;
        }
        return fullName;
    }

    static String promptContract(String scope) {
        StringBuilder out = new StringBuilder();
        for (Config config : loadAll()) {
            if (!config.enabled) continue;
            for (Tool tool : config.tools.values()) {
            if (!tool.enabled) continue;
            String full = fullToolName(config, tool.name);
            out.append("\n").append(full).append("：")
                    .append(tool.description.length() == 0 ? "MCP 工具" : tool.description)
                    .append("；arguments 必须符合 JSON Schema ")
                    .append(tool.schema.toString()).append("。调用格式：{\"id\":\"唯一短标识\",\"tool\":\"")
                    .append(full).append("\",\"scope\":\"").append(scope)
                    .append("\",\"arguments\":{}}。");
            }
        }
        return out.toString();
    }

    static void executeAsync(final String fullName, final String rawArguments,
                             final ResultCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                Config config = findConfigForTool(fullName);
                Tool tool = resolveTool(config, fullName);
                if (tool == null || !tool.enabled) throw new Exception("MCP 工具未启用");
                if (!connectionKey(config).equals(connectedKey)) {
                    config = connectAndSync(config);
                    saveSyncedTools(config);
                }
                JSONObject arguments = new JSONObject(
                        rawArguments == null || rawArguments.trim().length() == 0
                                ? "{}" : rawArguments);
                JSONObject params = new JSONObject().put("name", tool.name)
                        .put("arguments", arguments);
                JSONObject response = rpc(config, "tools/call", params, true);
                JSONObject error = response.optJSONObject("error");
                if (error != null) throw new Exception(error.optString("message", "MCP 调用失败"));
                JSONObject result = response.optJSONObject("result");
                String output = normalizeToolResult(result);
                MAIN.post(() -> callback.onResult(true, output, "MCP 工具执行完成"));
            } catch (Throwable error) {
                String detail = safeError(error);
                setStatus(State.ERROR, detail, 0);
                MAIN.post(() -> callback.onResult(false, "", detail));
            }
        });
    }

    private static Config connectAndSync(Config config) throws Exception {
        validate(config);
        sessionId = "";
        LegacySseSession oldSse = legacySse;
        legacySse = null;
        if (oldSse != null) oldSse.close();
        if (TRANSPORT_SSE.equals(config.transport)) {
            LegacySseSession created = new LegacySseSession(config);
            created.start();
            legacySse = created;
        }
        JSONObject client = new JSONObject().put("name", "Deekseep").put("version", "1.8");
        JSONObject params = new JSONObject().put("protocolVersion", "2025-03-26")
                .put("capabilities", new JSONObject()).put("clientInfo", client);
        JSONObject initialized = rpc(config, "initialize", params, false);
        if (initialized.optJSONObject("error") != null) {
            throw new Exception(initialized.getJSONObject("error")
                    .optString("message", "MCP 初始化失败"));
        }
        notify(config, "notifications/initialized", new JSONObject());
        JSONObject listed = rpc(config, "tools/list", new JSONObject(), true);
        if (listed.optJSONObject("error") != null) {
            throw new Exception(listed.getJSONObject("error")
                    .optString("message", "MCP 工具同步失败"));
        }
        JSONArray tools = listed.optJSONObject("result") == null ? null
                : listed.optJSONObject("result").optJSONArray("tools");
        LinkedHashMap<String, Tool> merged = new LinkedHashMap<>();
        if (tools != null) {
            for (int i = 0; i < tools.length(); i++) {
                JSONObject item = tools.optJSONObject(i);
                if (item == null) continue;
                String name = cleanToolToken(item.optString("name", ""));
                if (name.length() == 0) continue;
                Tool old = config.tools.get(name);
                String title = item.optString("title", "");
                JSONObject annotations = item.optJSONObject("annotations");
                if (title.trim().length() == 0 && annotations != null) {
                    title = annotations.optString("title", "");
                }
                if (title.trim().length() == 0 && old != null) title = old.title;
                merged.put(name, new Tool(name, title,
                        item.optString("description", ""),
                        item.optJSONObject("inputSchema"), old == null || old.enabled));
            }
        }
        Config synced = new Config(config.enabled, config.name, config.url,
                config.transport, config.headers, merged);
        connectedKey = connectionKey(synced);
        return synced;
    }

    private static JSONObject rpc(Config config, String method, JSONObject params,
                                  boolean includeSession) throws Exception {
        long id = REQUEST_IDS.getAndIncrement();
        JSONObject request = new JSONObject().put("jsonrpc", "2.0")
                .put("id", id).put("method", method).put("params", params);
        return post(config, request, includeSession, id);
    }

    private static void notify(Config config, String method, JSONObject params) throws Exception {
        JSONObject request = new JSONObject().put("jsonrpc", "2.0")
                .put("method", method).put("params", params);
        post(config, request, true, -1L);
    }

    private static JSONObject post(Config config, JSONObject body,
                                   boolean includeSession, long expectedId) throws Exception {
        if (TRANSPORT_SSE.equals(config.transport)) {
            LegacySseSession active = legacySse;
            if (active == null) throw new Exception("MCP SSE 尚未连接");
            return active.send(body, expectedId);
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(config.url).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(120000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        connection.setRequestProperty("MCP-Protocol-Version", "2025-03-26");
        if (includeSession && sessionId.length() > 0) {
            connection.setRequestProperty("Mcp-Session-Id", sessionId);
        }
        for (Header header : config.headers) {
            if (header.name.length() > 0 && header.value.length() > 0
                    && !isReservedHeader(header.name)) {
                connection.setRequestProperty(header.name, header.value);
            }
        }
        byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
        int code = connection.getResponseCode();
        String newSession = connection.getHeaderField("Mcp-Session-Id");
        if (newSession != null && newSession.trim().length() > 0) sessionId = newSession.trim();
        InputStream input = code >= 200 && code < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String response = readLimited(input, 1024 * 1024);
        if (code == 202 && expectedId < 0L) return new JSONObject();
        if (code < 200 || code >= 300) throw new Exception("HTTP " + code + ": " + trim(response, 400));
        if (response.trim().length() == 0) return new JSONObject();
        // Some otherwise-compatible Streamable HTTP servers acknowledge JSON-RPC
        // notifications with a plain `OK` body and HTTP 200. Notifications have no id and no
        // response payload by definition, so accept any successful non-JSON acknowledgement.
        // Calls with an id remain strict: tools/list and tools/call must return JSON-RPC.
        if (acceptsPlainNotificationAckV241(code, expectedId, response)) {
            return new JSONObject();
        }
        String payload = response.trim().startsWith("{")
                ? response.trim() : extractSseJson(response, expectedId);
        return new JSONObject(payload);
    }

    static boolean acceptsPlainNotificationAckV241(
            int statusCode, long expectedId, String response) {
        return HostCompat.isV241() && statusCode >= 200 && statusCode < 300
                && expectedId < 0L && response != null && response.trim().length() > 0
                && !looksLikeJsonRpcOrSse(response);
    }

    private static boolean looksLikeJsonRpcOrSse(String response) {
        String value = response == null ? "" : response.trim();
        return value.startsWith("{") || value.startsWith("data:")
                || value.startsWith("event:");
    }

    private static String extractSseJson(String text, long expectedId) throws Exception {
        String candidate = "";
        for (String line : text.split("\\r?\\n")) {
            if (!line.startsWith("data:")) continue;
            String value = line.substring(5).trim();
            if (!value.startsWith("{")) continue;
            candidate = value;
            JSONObject object = new JSONObject(value);
            if (expectedId < 0L || object.optLong("id", Long.MIN_VALUE) == expectedId) return value;
        }
        if (candidate.length() > 0) return candidate;
        throw new Exception("MCP SSE 未返回 JSON-RPC 结果");
    }

    private static String normalizeToolResult(JSONObject result) {
        if (result == null) return "";
        JSONArray content = result.optJSONArray("content");
        if (content == null) return result.toString();
        StringBuilder output = new StringBuilder();
        for (int i = 0; i < content.length(); i++) {
            JSONObject item = content.optJSONObject(i);
            if (item == null) continue;
            if (output.length() > 0) output.append('\n');
            if ("text".equals(item.optString("type"))) output.append(item.optString("text"));
            else output.append(item.toString());
        }
        return trim(output.toString(), 48 * 1024);
    }

    private static Tool resolveTool(Config config, String fullName) {
        if (config == null || !isDynamicTool(fullName)) return null;
        for (Tool tool : config.tools.values()) {
            if (fullToolName(config, tool.name).equals(fullName)) return tool;
        }
        return null;
    }

    private static Config findConfigForTool(String fullName) {
        for (Config config : loadAll()) if (resolveTool(config, fullName) != null) return config;
        return defaults();
    }

    private static String fullToolName(Config config, String tool) {
        return TOOL_PREFIX + token(config.name) + "__" + token(tool);
    }

    private static String token(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.US);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lower.length() && out.length() < 36; i++) {
            char c = lower.charAt(i);
            out.append(Character.isLetterOrDigit(c) ? c : '_');
        }
        return out.length() == 0 ? "server" : out.toString();
    }

    private static void saveSyncedTools(Config synced) {
        synchronized (LOCK) {
            ArrayList<Config> next = new ArrayList<>(); boolean changed = false;
            for (Config current : loadAll()) {
                if (connectionKey(current).equals(connectionKey(synced))) {
                    next.add(new Config(current.enabled, current.name, current.url,
                            current.transport, current.headers, synced.tools)); changed = true;
                } else next.add(current);
            }
            if (changed) { saveAll(next); connectedKey = connectionKey(synced); }
        }
    }

    private static void validate(Config config) throws Exception {
        if (!config.enabled) throw new Exception("MCP 未启用");
        if (config.name.length() == 0) throw new Exception("请填写 MCP 名称");
        if (!(config.url.startsWith("http://") || config.url.startsWith("https://"))) {
            throw new Exception("MCP 地址必须使用 HTTP 或 HTTPS");
        }
    }

    private static String connectionKey(Config config) {
        StringBuilder out = new StringBuilder(config.transport).append('|')
                .append(config.name).append('|').append(config.url);
        for (Header header : config.headers) out.append('|').append(header.name).append('=').append(header.value);
        return out.toString();
    }

    private static String encode(Config config) {
        try {
            JSONObject root = new JSONObject().put("version", 1).put("enabled", config.enabled)
                    .put("name", config.name).put("url", config.url).put("transport", config.transport);
            JSONArray headers = new JSONArray();
            for (Header header : config.headers) headers.put(new JSONObject()
                    .put("name", header.name).put("value", header.value));
            root.put("headers", headers);
            JSONArray tools = new JSONArray();
            for (Tool tool : config.tools.values()) tools.put(new JSONObject()
                    .put("name", tool.name).put("title", tool.title)
                    .put("description", tool.description)
                    .put("inputSchema", tool.schema).put("enabled", tool.enabled));
            root.put("tools", tools);
            return root.toString();
        } catch (Throwable error) { return "{}"; }
    }

    private static String encodeAll(List<Config> configs) {
        try {
            JSONObject root = new JSONObject().put("version", 2);
            JSONArray profiles = new JSONArray();
            if (configs != null) for (Config config : configs) {
                profiles.put(new JSONObject(encode(config)));
            }
            root.put("profiles", profiles);
            return root.toString();
        } catch (Throwable error) { return "{\"version\":2,\"profiles\":[]}"; }
    }

    private static List<Config> decodeAll(String text) throws Exception {
        JSONObject root = new JSONObject(text == null ? "" : text);
        JSONArray profiles = root.optJSONArray("profiles");
        if (profiles == null) return Collections.singletonList(decode(text));
        ArrayList<Config> result = new ArrayList<>();
        for (int i = 0; i < profiles.length() && result.size() < 16; i++) {
            JSONObject item = profiles.optJSONObject(i);
            if (item != null) result.add(decode(item.toString()));
        }
        return result;
    }

    private static Config decode(String text) throws Exception {
        JSONObject root = new JSONObject(text == null ? "" : text);
        ArrayList<Header> headers = new ArrayList<>();
        JSONArray rawHeaders = root.optJSONArray("headers");
        if (rawHeaders != null) for (int i = 0; i < rawHeaders.length() && i < 24; i++) {
            JSONObject item = rawHeaders.optJSONObject(i);
            if (item != null) headers.add(new Header(item.optString("name"), item.optString("value")));
        }
        LinkedHashMap<String, Tool> tools = new LinkedHashMap<>();
        JSONArray rawTools = root.optJSONArray("tools");
        if (rawTools != null) for (int i = 0; i < rawTools.length(); i++) {
            JSONObject item = rawTools.optJSONObject(i);
            if (item == null) continue;
            Tool tool = new Tool(item.optString("name"), item.optString("title"),
                    item.optString("description"), item.optJSONObject("inputSchema"),
                    item.optBoolean("enabled", true));
            if (tool.name.length() > 0) tools.put(tool.name, tool);
        }
        return new Config(root.optBoolean("enabled", false), root.optString("name", "MCP"),
                root.optString("url", ""), root.optString("transport", TRANSPORT_HTTP), headers, tools);
    }

    private static void setStatus(State stateValue, String detail, int count) {
        status = new Status(stateValue, detail, count);
    }

    private static void deliverStatus(final StatusCallback callback) {
        if (callback != null) MAIN.post(() -> callback.onStatus(status));
    }

    private static String readLimited(InputStream input, int maximum) throws Exception {
        if (input == null) return "";
        try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            StringBuilder out = new StringBuilder();
            int count;
            while ((count = reader.read(buffer)) >= 0 && out.length() < maximum) {
                out.append(buffer, 0, Math.min(count, maximum - out.length()));
            }
            return out.toString();
        }
    }

    private static String safeError(Throwable error) {
        String message = error == null ? "连接失败" : error.getMessage();
        return trim(message == null || message.trim().length() == 0
                ? error.getClass().getSimpleName() : message.trim(), 300);
    }

    private static boolean isReservedHeader(String name) {
        return "content-type".equalsIgnoreCase(name) || "accept".equalsIgnoreCase(name)
                || "content-length".equalsIgnoreCase(name) || "host".equalsIgnoreCase(name)
                || "mcp-session-id".equalsIgnoreCase(name)
                || "mcp-protocol-version".equalsIgnoreCase(name);
    }

    private static String cleanName(String value) { return trim(value, 48); }
    private static String cleanUrl(String value) { return trim(value, 2048); }
    private static String cleanToolToken(String value) { return trim(value, 96); }
    private static String cleanHeaderName(String value) {
        String safe = trim(value, 120);
        return safe.matches("[A-Za-z0-9!#$%&'*+.^_`|~-]+") ? safe : "";
    }
    private static String cleanHeaderValue(String value) {
        String safe = trim(value, 4096);
        return safe.indexOf('\r') >= 0 || safe.indexOf('\n') >= 0 ? "" : safe;
    }
    private static String trim(String value, int maximum) {
        String safe = value == null ? "" : value.trim();
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    /** Legacy MCP SSE: one long GET receives endpoint and JSON-RPC responses. */
    private static final class LegacySseSession {
        final Config config;
        final CountDownLatch endpointReady = new CountDownLatch(1);
        final ConcurrentHashMap<Long, Reply> replies = new ConcurrentHashMap<>();
        volatile String endpoint = "";
        volatile String failure = "";
        volatile HttpURLConnection stream;
        volatile boolean closed;

        LegacySseSession(Config config) { this.config = config; }

        void start() throws Exception {
            Thread reader = new Thread(this::readLoop, "Deekseep-MCP-SSE");
            reader.setDaemon(true);
            reader.start();
            if (!endpointReady.await(15, TimeUnit.SECONDS)) {
                close();
                throw new Exception("MCP SSE 等待 endpoint 超时");
            }
            if (endpoint.length() == 0) throw new Exception(
                    failure.length() == 0 ? "MCP SSE 未返回 endpoint" : failure);
        }

        JSONObject send(JSONObject body, long expectedId) throws Exception {
            if (closed || endpoint.length() == 0) throw new Exception("MCP SSE 已断开");
            Reply reply = expectedId >= 0L ? new Reply() : null;
            if (reply != null) replies.put(expectedId, reply);
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(endpoint).openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "application/json, text/event-stream");
                applyHeaders(connection, config);
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream output = connection.getOutputStream()) { output.write(bytes); }
                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) {
                    throw new Exception("MCP SSE HTTP " + code + ": "
                            + trim(readLimited(connection.getErrorStream(), 8192), 400));
                }
                if (expectedId < 0L) return new JSONObject();
                String inline = readLimited(connection.getInputStream(), 1024 * 1024).trim();
                if (inline.startsWith("{")) return new JSONObject(inline);
                if (!reply.ready.await(120, TimeUnit.SECONDS)) {
                    throw new Exception("MCP SSE 工具响应超时");
                }
                if (reply.error.length() > 0) throw new Exception(reply.error);
                return new JSONObject(reply.payload);
            } finally {
                if (expectedId >= 0L) replies.remove(expectedId);
                if (connection != null) connection.disconnect();
            }
        }

        void readLoop() {
            try {
                stream = (HttpURLConnection) new URL(config.url).openConnection();
                stream.setRequestMethod("GET");
                stream.setConnectTimeout(15000);
                stream.setReadTimeout(0);
                stream.setRequestProperty("Accept", "text/event-stream");
                applyHeaders(stream, config);
                int code = stream.getResponseCode();
                if (code < 200 || code >= 300) throw new Exception("MCP SSE HTTP " + code);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        stream.getInputStream(), StandardCharsets.UTF_8))) {
                    String event = "";
                    StringBuilder data = new StringBuilder();
                    String line;
                    while (!closed && (line = reader.readLine()) != null) {
                        if (line.length() == 0) {
                            dispatch(event, data.toString());
                            event = "";
                            data.setLength(0);
                        } else if (line.startsWith("event:")) {
                            event = line.substring(6).trim();
                        } else if (line.startsWith("data:")) {
                            if (data.length() > 0) data.append('\n');
                            data.append(line.substring(5).trim());
                        }
                    }
                }
                if (!closed) throw new Exception("MCP SSE 连接已关闭");
            } catch (Throwable error) {
                failure = safeError(error);
                endpointReady.countDown();
                for (Reply reply : replies.values()) {
                    reply.error = failure;
                    reply.ready.countDown();
                }
            }
        }

        void dispatch(String event, String data) {
            if (("endpoint".equals(event) || endpoint.length() == 0)
                    && data != null && !data.trim().startsWith("{")) {
                try {
                    endpoint = new URL(new URL(config.url), data.trim()).toString();
                    endpointReady.countDown();
                    return;
                } catch (Throwable ignored) {}
            }
            if (data == null || !data.trim().startsWith("{")) return;
            try {
                JSONObject object = new JSONObject(data.trim());
                long id = object.optLong("id", Long.MIN_VALUE);
                Reply reply = replies.get(id);
                if (reply != null) {
                    reply.payload = object.toString();
                    reply.ready.countDown();
                }
            } catch (Throwable ignored) {}
        }

        void close() {
            closed = true;
            HttpURLConnection current = stream;
            if (current != null) current.disconnect();
            endpointReady.countDown();
        }
    }

    private static final class Reply {
        final CountDownLatch ready = new CountDownLatch(1);
        volatile String payload = "";
        volatile String error = "";
    }

    private static void applyHeaders(HttpURLConnection connection, Config config) {
        for (Header header : config.headers) {
            if (header.name.length() > 0 && header.value.length() > 0
                    && !isReservedHeader(header.name)) {
                connection.setRequestProperty(header.name, header.value);
            }
        }
    }
}
