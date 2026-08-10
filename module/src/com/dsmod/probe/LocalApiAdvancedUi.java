package com.dsmod.probe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.Locale;

/** Full-screen child page for persistent and direct public Local API endpoints. */
final class LocalApiAdvancedUi {
    private static final int BRAND = 0xFF4D6BFE;

    private LocalApiAdvancedUi() {}

    static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        final boolean dark = isDark(activity);
        final int bg = dark ? 0xFF1B1B1D : 0xFFF5F6F8;
        final int bar = dark ? 0xFF232326 : 0xFFFFFFFF;
        final int cardColor = dark ? 0xFF2A2A2D : 0xFFFFFFFF;
        final int text = dark ? 0xFFECECEC : 0xFF1A1A1A;
        final int sub = dark ? 0xFFAAAAAF : 0xFF70757D;
        final int divider = dark ? 0xFF3A3A3D : 0xFFEEEEEE;

        final Dialog dialog = new Dialog(
                activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        final LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(bar);
        int statusTop = statusBarHeight(activity);
        top.setPadding(dp(activity, 8), statusTop, dp(activity, 16), 0);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 56) + statusTop));

        TextView back = new TextView(activity);
        back.setText("\u2039");
        back.setTextColor(text);
        back.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        back.setGravity(Gravity.CENTER);
        back.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
        top.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 40)));

        TextView title = label(activity,
                "本地 API 高级设置", "Local API advanced settings",
                18, text, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.leftMargin = dp(activity, 8);
        top.addView(title, titleParams);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 16), dp(activity, 16),
                dp(activity, 16), dp(activity, 24));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Bundle initial = Main.localApiPublicTunnelStatus(activity);
        final Bundle initialPinggy = Main.localApiPinggyTunnelStatus(activity);

        LinearLayout relayCard = card(activity, cardColor);
        content.addView(relayCard, matchWrap());
        LinearLayout relayRow = new LinearLayout(activity);
        relayRow.setOrientation(LinearLayout.HORIZONTAL);
        relayRow.setGravity(Gravity.CENTER_VERTICAL);
        relayRow.setPadding(dp(activity, 16), dp(activity, 12),
                dp(activity, 12), dp(activity, 12));
        LinearLayout relayCopy = new LinearLayout(activity);
        relayCopy.setOrientation(LinearLayout.VERTICAL);
        relayCopy.addView(label(activity,
                "超长上下文转文件", "Move long context to a file", 15, text, true));
        relayCopy.addView(label(activity,
                "超过输出上限或 9000 字时自动附加 TXT",
                "Attach overflow as TXT beyond the output limit or 9,000 characters",
                12, sub, false));
        relayRow.addView(relayCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch relayEnabled = new HubInsetSwitch(activity);
        relayEnabled.setChecked(Main.isLocalApiContextRelayEnabled());
        relayEnabled.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override public void onCheckedChanged(
                            CompoundButton button, boolean checked) {
                        Main.setLocalApiContextRelayEnabled(checked);
                    }
                });
        relayRow.addView(relayEnabled);
        relayCard.addView(relayRow);

        LinearLayout pinggyCard = card(activity, cardColor);
        LinearLayout.LayoutParams pinggyCardParams = matchWrap();
        pinggyCardParams.topMargin = dp(activity, 14);
        content.addView(pinggyCard, pinggyCardParams);
        pinggyCard.addView(section(activity,
                "一小时临时网址（Pinggy）", "One-hour temporary URL (Pinggy)", text));
        pinggyCard.addView(body(activity,
                "无需注册或填写密码。点击申请后，APP 会按 Pinggy 官方 SSH 流程自动提交空密码"
                        + "（等同于在密码提示处直接回车），再把随机 HTTPS 地址转发到本地 API。"
                        + "本机和局域网监听会继续运行；免费地址约 60 分钟后失效。",
                "No account or password is required. The app follows Pinggy's documented SSH "
                        + "flow and submits an empty password (the same as pressing Enter), then "
                        + "forwards the random HTTPS address to the local API. Local and LAN "
                        + "listeners stay active; the free address expires after about 60 minutes.",
                sub), inset(activity, 0, 8));

        final TextView pinggyStatus = info(activity,
                pinggyStatusText(activity, initialPinggy), text, dark);
        pinggyCard.addView(pinggyStatus, inset(activity, 0, 10));

        LinearLayout pinggyActions = new LinearLayout(activity);
        pinggyActions.setOrientation(LinearLayout.HORIZONTAL);
        pinggyActions.setPadding(dp(activity, 16), dp(activity, 4),
                dp(activity, 16), dp(activity, 8));
        final TextView startPinggy = action(activity,
                "申请临时网址", "Request temporary URL", BRAND, dark);
        pinggyActions.addView(startPinggy, weighted());
        final TextView copyPinggy = action(activity,
                "复制公网 URL", "Copy public URL", BRAND, dark);
        LinearLayout.LayoutParams copyPinggyParams = weighted();
        copyPinggyParams.leftMargin = dp(activity, 8);
        pinggyActions.addView(copyPinggy, copyPinggyParams);
        pinggyCard.addView(pinggyActions);

        final TextView stopPinggy = action(activity,
                "关闭临时网址", "Close temporary URL", 0xFFE05252, dark);
        pinggyCard.addView(stopPinggy, inset(activity, 0, 8));
        final TextView pinggyLogToggle = action(activity,
                "展开 Pinggy 日志", "Show Pinggy log", sub, dark);
        pinggyCard.addView(pinggyLogToggle, inset(activity, 0, 10));
        final TextView pinggyLogs = info(activity,
                initialPinggy.getString("recent_log", ""), sub, dark);
        pinggyLogs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        pinggyLogs.setVisibility(View.GONE);
        pinggyCard.addView(pinggyLogs, inset(activity, 0, 12));

        LinearLayout tunnelCard = card(activity, cardColor);
        LinearLayout.LayoutParams tunnelCardParams = matchWrap();
        tunnelCardParams.topMargin = dp(activity, 14);
        content.addView(tunnelCard, tunnelCardParams);
        tunnelCard.addView(section(activity,
                "Cloudflare 自有域名", "Cloudflare custom domains", text));

        TextView tunnelIntro = body(activity,
                "用于长期使用自己的域名。APP 内置 cloudflared，并在模块的前台保活进程中运行；"
                        + "本机与局域网监听不会关闭。一个 Tunnel 可以配置多个域名。",
                "Use your own domains persistently. The bundled cloudflared connector runs in "
                        + "the module foreground process while local and LAN endpoints remain active. "
                        + "One tunnel can serve multiple domains.",
                sub);
        tunnelCard.addView(tunnelIntro, inset(activity, 0, 8));

        LinearLayout enableRow = new LinearLayout(activity);
        enableRow.setOrientation(LinearLayout.HORIZONTAL);
        enableRow.setGravity(Gravity.CENTER_VERTICAL);
        enableRow.setPadding(dp(activity, 16), dp(activity, 10),
                dp(activity, 12), dp(activity, 12));
        LinearLayout enableCopy = new LinearLayout(activity);
        enableCopy.setOrientation(LinearLayout.VERTICAL);
        enableCopy.addView(label(activity, "启用持久公网入口",
                "Enable persistent public endpoint", 15, text, true));
        enableCopy.addView(label(activity,
                "域名映射完成后再打开", "Turn on after hostname routing is configured",
                12, sub, false));
        enableRow.addView(enableCopy, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Switch enabled = new HubInsetSwitch(activity);
        enabled.setChecked(initial.getBoolean("requested", false));
        enableRow.addView(enabled);
        tunnelCard.addView(enableRow);

        tunnelCard.addView(line(activity, divider));
        tunnelCard.addView(fieldTitle(activity,
                "Tunnel token", "Tunnel token", text));
        LinearLayout tokenRow = new LinearLayout(activity);
        tokenRow.setOrientation(LinearLayout.HORIZONTAL);
        tokenRow.setGravity(Gravity.CENTER_VERTICAL);
        tokenRow.setPadding(dp(activity, 16), 0, dp(activity, 16), dp(activity, 8));
        final EditText token = edit(activity, text, sub, dark);
        token.setHint(initial.getBoolean("token_configured", false)
                ? t(activity, "已安全保存；留空不会覆盖", "Stored securely; leave blank to keep it")
                : t(activity, "粘贴 cloudflared 连接器 token", "Paste the cloudflared connector token"));
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setTransformationMethod(PasswordTransformationMethod.getInstance());
        tokenRow.addView(token, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView reveal = action(activity, "显示", "Show", BRAND, dark);
        LinearLayout.LayoutParams revealParams = new LinearLayout.LayoutParams(
                dp(activity, 68), ViewGroup.LayoutParams.WRAP_CONTENT);
        revealParams.leftMargin = dp(activity, 8);
        tokenRow.addView(reveal, revealParams);
        tunnelCard.addView(tokenRow);

        tunnelCard.addView(fieldTitle(activity,
                "公网域名（每行一个）", "Public domains (one per line)", text));
        final EditText domains = edit(activity, text, sub, dark);
        domains.setHint("api.example.com");
        domains.setSingleLine(false);
        domains.setMinLines(2);
        domains.setMaxLines(6);
        domains.setGravity(Gravity.TOP);
        domains.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        domains.setText(initial.getString("domains", ""));
        tunnelCard.addView(domains, inset(activity, 0, 8));

        tunnelCard.addView(fieldTitle(activity,
                "固定本地监听端口", "Fixed local listener port", text));
        final EditText port = edit(activity, text, sub, dark);
        port.setSingleLine(true);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText(String.valueOf(Main.localApiPreferredPort(activity)));
        tunnelCard.addView(port, inset(activity, 0, 8));

        LinearLayout transportRow = new LinearLayout(activity);
        transportRow.setOrientation(LinearLayout.HORIZONTAL);
        transportRow.setGravity(Gravity.CENTER_VERTICAL);
        transportRow.setPadding(dp(activity, 16), dp(activity, 8),
                dp(activity, 16), dp(activity, 12));
        transportRow.addView(label(activity, "边缘传输", "Edge transport",
                14, text, true), new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final String[] transport = new String[]{
                normalizeTransport(initial.getString("transport",
                        PublicTunnelManager.TRANSPORT_AUTO))
        };
        final TextView transportValue = action(activity,
                transportName(activity, transport[0]),
                transportName(activity, transport[0]), BRAND, dark);
        transportRow.addView(transportValue);
        tunnelCard.addView(transportRow);

        final TextView status = info(activity, statusText(activity, initial), text, dark);
        tunnelCard.addView(status, inset(activity, 0, 10));

        LinearLayout primaryActions = new LinearLayout(activity);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        primaryActions.setPadding(dp(activity, 16), dp(activity, 4),
                dp(activity, 16), dp(activity, 8));
        final TextView save = action(activity, "保存配置", "Save", BRAND, dark);
        primaryActions.addView(save, weighted());
        final TextView copyPublic = action(activity,
                "复制公网 URL", "Copy public URL", BRAND, dark);
        LinearLayout.LayoutParams copyPublicParams = weighted();
        copyPublicParams.leftMargin = dp(activity, 8);
        primaryActions.addView(copyPublic, copyPublicParams);
        tunnelCard.addView(primaryActions);

        LinearLayout setupActions = new LinearLayout(activity);
        setupActions.setOrientation(LinearLayout.HORIZONTAL);
        setupActions.setPadding(dp(activity, 16), 0,
                dp(activity, 16), dp(activity, 12));
        final TextView copyOrigin = action(activity,
                "复制源站地址", "Copy origin", 0xFFE07A22, dark);
        setupActions.addView(copyOrigin, weighted());
        final TextView openDashboard = action(activity,
                "打开 Cloudflare", "Open Cloudflare", 0xFFE07A22, dark);
        LinearLayout.LayoutParams dashboardParams = weighted();
        dashboardParams.leftMargin = dp(activity, 8);
        setupActions.addView(openDashboard, dashboardParams);
        tunnelCard.addView(setupActions);

        TextView setupHelp = body(activity,
                "Cloudflare 端只需配置一次：Zero Trust → Networks / Connectors → 选择该 Tunnel → "
                        + "Published application routes。把上面每个域名的 Service 都设为本页显示的"
                        + "源站地址，然后从连接器安装命令中复制 token 粘贴到这里。不要启用会要求"
                        + "浏览器登录的 Access 验证，否则普通 API 客户端无法调用。",
                "One-time Cloudflare setup: Zero Trust → Networks / Connectors → select the "
                        + "tunnel → Published application routes. Point every hostname above to "
                        + "the origin shown here, then paste the token from the connector install "
                        + "command. Do not require interactive Access login for API clients.",
                sub);
        tunnelCard.addView(setupHelp, inset(activity, 0, 14));

        final TextView logToggle = action(activity,
                "展开连接日志", "Show connector log", sub, dark);
        LinearLayout.LayoutParams logToggleParams = inset(activity, 0, 10);
        tunnelCard.addView(logToggle, logToggleParams);
        final TextView logs = info(activity, initial.getString("recent_log", ""), sub, dark);
        logs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        logs.setVisibility(View.GONE);
        tunnelCard.addView(logs, inset(activity, 0, 12));

        LinearLayout directCard = card(activity, cardColor);
        LinearLayout.LayoutParams directCardParams = matchWrap();
        directCardParams.topMargin = dp(activity, 14);
        content.addView(directCard, directCardParams);
        directCard.addView(section(activity,
                "公网 IP / 路由器直连", "Public IP / router forwarding", text));
        directCard.addView(body(activity,
                "本地 API 已监听 0.0.0.0，所以设备若确实拥有公网 IP，可以把路由器公网端口转发到"
                        + "这台设备的固定端口，再让域名 A/AAAA 记录指向该公网 IP。APP 不能把任意 "
                        + "IP 绑定到手机，也不能绕过运营商 CGNAT；移动网络通常无法使用这种方式。"
                        + "直接使用 HTTP 会明文传输 API Key，公网使用时应另配 HTTPS 反向代理。",
                "The local API already listens on 0.0.0.0. If the device is behind a real public "
                        + "IP, forward a router WAN port to this fixed port and point an A/AAAA "
                        + "record at that IP. An app cannot assign an arbitrary IP or bypass carrier "
                        + "CGNAT; cellular networks usually cannot use this mode. Plain HTTP exposes "
                        + "the API key, so add an HTTPS reverse proxy for Internet use.",
                sub), inset(activity, 0, 8));
        directCard.addView(fieldTitle(activity,
                "外部根地址（仅保存、复制和校验）",
                "External root URL (stored for copy/validation)", text));
        final EditText directRoot = edit(activity, text, sub, dark);
        directRoot.setSingleLine(true);
        directRoot.setHint("http://203.0.113.10:8765");
        directRoot.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_URI);
        directRoot.setText(initial.getString("direct_root", ""));
        directCard.addView(directRoot, inset(activity, 0, 8));
        final TextView copyDirect = action(activity,
                "复制直连 API URL", "Copy direct API URL", BRAND, dark);
        directCard.addView(copyDirect, inset(activity, 0, 14));

        TextView security = info(activity,
                t(activity,
                        "公网暴露后，API Key 就等同于当前 DeepSeek 账号的调用权限。请使用随机长 Key，"
                                + "不要把 Key 写进公开脚本或截图；Cloudflare 提供 TLS，但不会替你"
                                + "限制模型调用次数。",
                        "Once public, the API key grants use of the signed-in DeepSeek account. "
                                + "Use a long random key and never publish it. Cloudflare supplies "
                                + "TLS but does not enforce model-call quotas for this gateway."),
                text, dark);
        LinearLayout.LayoutParams securityParams = matchWrap();
        securityParams.topMargin = dp(activity, 14);
        content.addView(security, securityParams);

        final TextView feedback = label(activity, "", "", 12, BRAND, true);
        feedback.setGravity(Gravity.CENTER);
        feedback.setPadding(dp(activity, 12), dp(activity, 10),
                dp(activity, 12), dp(activity, 8));
        content.addView(feedback, matchWrap());

        final boolean[] syncingSwitch = new boolean[]{false};
        final boolean[] tokenVisible = new boolean[]{false};
        final boolean[] logsVisible = new boolean[]{false};
        final boolean[] pinggyLogsVisible = new boolean[]{false};
        final Bundle[] latest = new Bundle[]{initial};
        final Bundle[] latestPinggy = new Bundle[]{initialPinggy};
        final String[] announcedPinggyRoot = new String[]{
                initialPinggy.getString("primary_root", "")
        };

        startPinggy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String portError = savePreferredPort(activity, port);
                if (portError != null) {
                    feedback.setText(portError);
                    return;
                }
                if (!Main.isLocalApiEnabled() && !Main.setLocalApiEnabled(true)) {
                    feedback.setText(t(activity,
                            "请先通过本地 API 的后台运行校验",
                            "Approve background operation for the local API first"));
                    return;
                }
                announcedPinggyRoot[0] = "";
                Bundle result = Main.setLocalApiPinggyTunnelEnabled(activity, true);
                latestPinggy[0] = result;
                feedback.setText(resultMessage(activity, result,
                        "正在连接 Pinggy；出现密码请求时会自动提交空密码…",
                        "Connecting to Pinggy; an empty password is submitted automatically…"));
                pinggyStatus.setText(pinggyStatusText(activity, result));
            }
        });

        copyPinggy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Bundle current = Main.localApiPinggyTunnelStatus(activity);
                latestPinggy[0] = current;
                String endpoint = Main.localApiEndpointForPublicRoot(
                        current.getString("primary_root", ""));
                if (endpoint.length() == 0) {
                    feedback.setText(t(activity,
                            "临时网址尚未连接", "The temporary URL is not connected yet"));
                    return;
                }
                copy(activity, "Pinggy API URL", endpoint);
                feedback.setText(t(activity,
                        "Pinggy 公网 API URL 已复制",
                        "Pinggy public API URL copied"));
            }
        });

        stopPinggy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Bundle result = Main.setLocalApiPinggyTunnelEnabled(activity, false);
                latestPinggy[0] = result;
                announcedPinggyRoot[0] = "";
                feedback.setText(resultMessage(activity, result,
                        "Pinggy 临时网址已关闭", "Pinggy temporary URL closed"));
                pinggyStatus.setText(pinggyStatusText(activity, result));
            }
        });

        pinggyLogToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                pinggyLogsVisible[0] = !pinggyLogsVisible[0];
                pinggyLogs.setVisibility(pinggyLogsVisible[0] ? View.VISIBLE : View.GONE);
                pinggyLogToggle.setText(pinggyLogsVisible[0]
                        ? t(activity, "收起 Pinggy 日志", "Hide Pinggy log")
                        : t(activity, "展开 Pinggy 日志", "Show Pinggy log"));
            }
        });

        reveal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                tokenVisible[0] = !tokenVisible[0];
                int selection = token.getSelectionStart();
                token.setTransformationMethod(tokenVisible[0]
                        ? null : PasswordTransformationMethod.getInstance());
                if (selection >= 0) token.setSelection(Math.min(selection, token.length()));
                reveal.setText(tokenVisible[0]
                        ? t(activity, "隐藏", "Hide") : t(activity, "显示", "Show"));
            }
        });

        transportValue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                if (PublicTunnelManager.TRANSPORT_AUTO.equals(transport[0])) {
                    transport[0] = PublicTunnelManager.TRANSPORT_HTTP2;
                } else if (PublicTunnelManager.TRANSPORT_HTTP2.equals(transport[0])) {
                    transport[0] = PublicTunnelManager.TRANSPORT_QUIC;
                } else {
                    transport[0] = PublicTunnelManager.TRANSPORT_AUTO;
                }
                transportValue.setText(transportName(activity, transport[0]));
            }
        });

        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String error = saveConfiguration(activity, token, domains, port,
                        transport[0], directRoot, latest);
                feedback.setText(error);
                if (latest[0].getBoolean("accepted", false)) {
                    token.setText("");
                    status.setText(statusText(activity, latest[0]));
                }
            }
        });

        enabled.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton button, boolean checked) {
                if (syncingSwitch[0]) return;
                if (!checked) {
                    Bundle result = Main.setLocalApiPublicTunnelEnabled(activity, false);
                    latest[0] = result;
                    feedback.setText(resultMessage(activity, result,
                            "公网入口已停止", "Public endpoint stopped"));
                    status.setText(statusText(activity, result));
                    return;
                }
                if (!Main.isLocalApiEnabled() && !Main.setLocalApiEnabled(true)) {
                    syncingSwitch[0] = true;
                    enabled.setChecked(false);
                    syncingSwitch[0] = false;
                    feedback.setText(t(activity,
                            "请先通过本地 API 的后台运行校验",
                            "Approve background operation for the local API first"));
                    return;
                }
                String saved = saveConfiguration(activity, token, domains, port,
                        transport[0], directRoot, latest);
                if (!latest[0].getBoolean("accepted", false)) {
                    syncingSwitch[0] = true;
                    enabled.setChecked(false);
                    syncingSwitch[0] = false;
                    feedback.setText(saved);
                    return;
                }
                token.setText("");
                Bundle result = Main.setLocalApiPublicTunnelEnabled(activity, true);
                latest[0] = result;
                if (!result.getBoolean("accepted", false)) {
                    syncingSwitch[0] = true;
                    enabled.setChecked(false);
                    syncingSwitch[0] = false;
                }
                feedback.setText(resultMessage(activity, result,
                        "正在启动 Cloudflare Tunnel…", "Starting Cloudflare Tunnel…"));
                status.setText(statusText(activity, result));
            }
        });

        copyPublic.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Bundle current = Main.localApiPublicTunnelStatus(activity);
                latest[0] = current;
                String endpoint = Main.localApiEndpointForPublicRoot(
                        current.getString("primary_root", ""));
                if (endpoint.length() == 0) {
                    feedback.setText(t(activity,
                            "尚未保存公网域名", "No public domain has been saved"));
                    return;
                }
                copy(activity, "Public API URL", endpoint);
                feedback.setText(t(activity, "公网 API URL 已复制",
                        "Public API URL copied"));
            }
        });

        copyOrigin.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                Bundle current = Main.localApiPublicTunnelStatus(activity);
                String origin = current.getString("origin", Main.localApiRootEndpoint());
                copy(activity, "Cloudflare origin", origin);
                feedback.setText(t(activity,
                        "源站地址已复制；把它填入 Cloudflare 的 Service",
                        "Origin copied; use it as the Cloudflare Service"));
            }
        });

        openDashboard.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                try {
                    activity.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://one.dash.cloudflare.com/")));
                } catch (Throwable t) {
                    feedback.setText(t(activity,
                            "无法打开浏览器", "Could not open a browser"));
                }
            }
        });

        copyDirect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                String error = saveConfiguration(activity, token, domains, port,
                        transport[0], directRoot, latest);
                if (!latest[0].getBoolean("accepted", false)) {
                    feedback.setText(error);
                    return;
                }
                token.setText("");
                String endpoint = Main.localApiEndpointForPublicRoot(
                        latest[0].getString("direct_root", ""));
                if (endpoint.length() == 0) {
                    feedback.setText(t(activity,
                            "请先填写公网 IP 或外部地址",
                            "Enter a public IP or external URL first"));
                    return;
                }
                copy(activity, "Direct API URL", endpoint);
                feedback.setText(t(activity,
                        "直连 API URL 已复制", "Direct API URL copied"));
            }
        });

        logToggle.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                logsVisible[0] = !logsVisible[0];
                logs.setVisibility(logsVisible[0] ? View.VISIBLE : View.GONE);
                logToggle.setText(logsVisible[0]
                        ? t(activity, "收起连接日志", "Hide connector log")
                        : t(activity, "展开连接日志", "Show connector log"));
            }
        });

        final Runnable refresh = new Runnable() {
            @Override public void run() {
                if (!dialog.isShowing()) return;
                Bundle current = Main.localApiPublicTunnelStatus(activity);
                latest[0] = current;
                status.setText(statusText(activity, current));
                Bundle currentPinggy = Main.localApiPinggyTunnelStatus(activity);
                latestPinggy[0] = currentPinggy;
                pinggyStatus.setText(pinggyStatusText(activity, currentPinggy));
                if (pinggyLogsVisible[0]) {
                    String value = currentPinggy.getString("recent_log", "");
                    pinggyLogs.setText(value.length() == 0
                            ? t(activity, "暂无日志", "No log entries yet") : value);
                }
                String pinggyRoot = currentPinggy.getString("primary_root", "");
                if ("connected".equals(currentPinggy.getString("state", ""))
                        && pinggyRoot.length() > 0
                        && !pinggyRoot.equals(announcedPinggyRoot[0])) {
                    announcedPinggyRoot[0] = pinggyRoot;
                    showPinggyReadyDialog(activity, pinggyRoot, feedback);
                }
                if (logsVisible[0]) {
                    String value = current.getString("recent_log", "");
                    logs.setText(value.length() == 0
                            ? t(activity, "暂无日志", "No log entries yet") : value);
                }
                boolean requested = current.getBoolean("requested", false);
                if (enabled.isChecked() != requested) {
                    syncingSwitch[0] = true;
                    enabled.setChecked(requested);
                    syncingSwitch[0] = false;
                }
                status.postDelayed(this, 1000L);
            }
        };

        final Runnable close = new Runnable() {
            @Override public void run() {
                int distance = activity.getResources().getDisplayMetrics().widthPixels;
                root.animate().translationX(distance).setDuration(190L)
                        .withEndAction(new Runnable() {
                            @Override public void run() { dialog.dismiss(); }
                        }).start();
            }
        };
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { close.run(); }
        });
        dialog.setOnKeyListener(new Dialog.OnKeyListener() {
            @Override public boolean onKey(android.content.DialogInterface ignored,
                                           int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    close.run();
                    return true;
                }
                return false;
            }
        });

        DeekseepUi.addBuildFooter(activity, content, sub);
        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(bg));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        }
        int distance = activity.getResources().getDisplayMetrics().widthPixels;
        root.setTranslationX(distance);
        root.animate().translationX(0f).setDuration(220L).start();
        status.post(refresh);
    }

    private static String savePreferredPort(Activity activity, EditText port) {
        String value = port.getText().toString().trim();
        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (Throwable t) {
            return t(activity, "监听端口必须是数字", "Listener port must be numeric");
        }
        if (parsed < 1024 || parsed > 65535) {
            return t(activity, "监听端口必须在 1024–65535 之间",
                    "Listener port must be between 1024 and 65535");
        }
        if (parsed == Main.localApiPreferredPort(activity)) return null;
        String result = Main.setLocalApiPreferredPort(activity, value);
        return Main.localApiPreferredPort(activity) == parsed ? null : result;
    }

    private static void showPinggyReadyDialog(final Activity activity, String root,
                                              final TextView feedback) {
        final String endpoint = Main.localApiEndpointForPublicRoot(root);
        if (endpoint.length() == 0 || activity.isFinishing()) return;
        new AlertDialog.Builder(activity)
                .setTitle(t(activity, "临时公网网址已就绪",
                        "Temporary public URL is ready"))
                .setMessage(t(activity,
                        "有效期约 60 分钟，倒计时以高级设置页显示为准。本机与局域网地址仍可同时使用。\n\n",
                        "The URL is valid for about 60 minutes; the advanced page shows the "
                                + "live countdown. Local and LAN addresses remain available.\n\n")
                        + endpoint)
                .setPositiveButton(t(activity, "一键复制", "Copy"), (dialog, which) -> {
                    copy(activity, "Pinggy API URL", endpoint);
                    feedback.setText(t(activity,
                            "Pinggy 公网 API URL 已复制",
                            "Pinggy public API URL copied"));
                })
                .setNegativeButton(t(activity, "关闭", "Close"), null)
                .show();
    }

    private static String pinggyStatusText(Context context, Bundle status) {
        if (status == null) return t(context,
                "无法读取 Pinggy 状态", "Could not read Pinggy status");
        if (!status.getBoolean("accepted", false)) {
            return t(context, "模块连接：不可用\n", "Module connector: unavailable\n")
                    + status.getString("error", "");
        }
        String rawState = status.getString("state", "stopped");
        String state;
        if ("connected".equals(rawState)) state = t(context, "已连接", "Connected");
        else if ("connecting".equals(rawState)) state = t(context,
                "正在连接", "Connecting");
        else if ("waiting_local_api".equals(rawState)) state = t(context,
                "等待本地 API", "Waiting for local API");
        else if ("expired".equals(rawState)) state = t(context, "已到期", "Expired");
        else if ("error".equals(rawState)) state = t(context, "已断开", "Disconnected");
        else state = t(context, "未开启", "Not running");

        String root = status.getString("primary_root", "");
        String endpoint = Main.localApiEndpointForPublicRoot(root);
        StringBuilder value = new StringBuilder();
        value.append(t(context, "连接状态：", "Connector: ")).append(state)
                .append('\n').append(t(context, "SSH 客户端：", "SSH client: "))
                .append(status.getString("ssh_client", "JSch"))
                .append('\n').append(t(context, "认证：", "Authentication: "))
                .append(t(context, "自动空密码回车", "automatic empty-password Enter"))
                .append('\n').append(t(context, "源站：", "Origin: "))
                .append(status.getString("origin", "http://127.0.0.1:8765"))
                .append('\n').append(t(context, "公网 API：", "Public API: "))
                .append(endpoint.length() == 0
                        ? t(context, "尚未分配", "not allocated") : endpoint);
        long remaining = status.getLong("remaining_ms", 0L);
        if (remaining > 0L) {
            value.append('\n').append(t(context, "剩余时间：", "Time remaining: "))
                    .append(formatDuration(remaining));
        }
        String fingerprint = status.getString("host_fingerprint", "");
        if (fingerprint.length() > 0) {
            value.append('\n').append(t(context, "主机指纹：", "Host fingerprint: "))
                    .append(fingerprint);
        }
        String error = status.getString("error", "");
        if (error.length() > 0) {
            value.append('\n').append(t(context, "提示：", "Message: "))
                    .append(UiLanguage.dynamic(context, error));
        }
        return value.toString();
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remainder);
    }

    private static String saveConfiguration(Activity activity, EditText token,
                                            EditText domains, EditText port,
                                            String transport, EditText directRoot,
                                            Bundle[] latest) {
        String portText = port.getText().toString().trim();
        int parsed;
        try {
            parsed = Integer.parseInt(portText);
        } catch (Throwable t) {
            Bundle failure = new Bundle();
            failure.putBoolean("accepted", false);
            failure.putString("error", t(activity,
                    "监听端口必须是数字", "Listener port must be numeric"));
            latest[0] = failure;
            return failure.getString("error");
        }
        if (parsed != Main.localApiPreferredPort(activity)) {
            String portResult = Main.setLocalApiPreferredPort(activity, portText);
            if (parsed < 1024 || parsed > 65535) {
                Bundle failure = new Bundle();
                failure.putBoolean("accepted", false);
                failure.putString("error", portResult);
                latest[0] = failure;
                return portResult;
            }
        }
        Bundle result = Main.configureLocalApiPublicTunnel(activity,
                token.getText().toString(), domains.getText().toString(),
                transport, directRoot.getText().toString());
        latest[0] = result;
        return resultMessage(activity, result,
                "配置已安全保存", "Configuration saved securely");
    }

    private static String statusText(Context context, Bundle status) {
        if (status == null) return t(context,
                "无法读取公网连接状态", "Could not read public connector status");
        if (!status.getBoolean("accepted", false)) {
            return t(context, "模块连接：不可用\n", "Module connector: unavailable\n")
                    + status.getString("error", "");
        }
        String rawState = status.getString("state", "stopped");
        String state;
        if ("connected".equals(rawState)) state = t(context, "已连接", "Connected");
        else if ("connecting".equals(rawState)) state = t(context, "正在连接", "Connecting");
        else if ("retry_wait".equals(rawState)) state = t(context,
                "等待重试", "Waiting to retry");
        else if ("waiting_local_api".equals(rawState)) state = t(context,
                "等待本地 API", "Waiting for local API");
        else if ("error".equals(rawState)) state = t(context, "错误", "Error");
        else state = t(context, "已停止", "Stopped");
        String root = status.getString("primary_root", "");
        String endpoint = Main.localApiEndpointForPublicRoot(root);
        String error = status.getString("error", "");
        StringBuilder value = new StringBuilder();
        value.append(t(context, "连接状态：", "Connector: ")).append(state)
                .append('\n').append(t(context, "cloudflared：", "cloudflared: "))
                .append(status.getBoolean("binary_available", false)
                        ? status.getString("cloudflared_version", "")
                        : t(context, "当前 ABI 缺失", "missing for this ABI"))
                .append('\n').append(t(context, "源站：", "Origin: "))
                .append(status.getString("origin", "http://127.0.0.1:8765"))
                .append('\n').append(t(context, "公网 API：", "Public API: "))
                .append(endpoint.length() == 0
                        ? t(context, "尚未配置", "not configured") : endpoint)
                .append('\n').append(t(context, "凭据：", "Credential: "))
                .append(status.getBoolean("token_configured", false)
                        ? t(context, "已用 Android Keystore 加密保存",
                                "encrypted with Android Keystore")
                        : t(context, "尚未保存", "not stored"));
        long retry = status.getLong("retry_in_ms", 0L);
        if (retry > 0L) {
            value.append('\n').append(t(context, "重试倒计时：", "Retry in: "))
                    .append((retry + 999L) / 1000L).append('s');
        }
        if (error != null && error.length() > 0) {
            value.append('\n').append(t(context, "诊断：", "Diagnostic: "))
                    .append(UiLanguage.dynamic(context, error));
        }
        value.append('\n').append(t(context, "日志：", "Log: "))
                .append(status.getString("log_file", ""));
        return value.toString();
    }

    private static String resultMessage(Context context, Bundle result,
                                        String successZh, String successEn) {
        if (result != null && result.getBoolean("accepted", false)) {
            return t(context, successZh, successEn);
        }
        String error = result == null ? "" : result.getString("error", "");
        return error.length() == 0
                ? t(context, "操作失败", "Operation failed")
                : UiLanguage.dynamic(context, error);
    }

    private static String normalizeTransport(String value) {
        if (PublicTunnelManager.TRANSPORT_HTTP2.equals(value)) {
            return PublicTunnelManager.TRANSPORT_HTTP2;
        }
        if (PublicTunnelManager.TRANSPORT_QUIC.equals(value)) {
            return PublicTunnelManager.TRANSPORT_QUIC;
        }
        return PublicTunnelManager.TRANSPORT_AUTO;
    }

    private static String transportName(Context context, String value) {
        if (PublicTunnelManager.TRANSPORT_HTTP2.equals(value)) return "HTTP/2  \u203A";
        if (PublicTunnelManager.TRANSPORT_QUIC.equals(value)) return "QUIC  \u203A";
        return t(context, "自动（推荐）  \u203A", "Auto (recommended)  \u203A");
    }

    private static void copy(Context context, String label, String value) {
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
    }

    private static LinearLayout card(Context context, int color) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(context, 8));
        card.setBackground(background);
        return card;
    }

    private static TextView section(Context context, String zh, String en, int color) {
        TextView view = label(context, zh, en, 16, color, true);
        view.setPadding(dp(context, 16), dp(context, 16),
                dp(context, 16), dp(context, 7));
        return view;
    }

    private static TextView fieldTitle(Context context, String zh, String en, int color) {
        TextView view = label(context, zh, en, 13, color, true);
        view.setPadding(dp(context, 16), dp(context, 9),
                dp(context, 16), dp(context, 5));
        return view;
    }

    private static TextView body(Context context, String zh, String en, int color) {
        TextView view = label(context, zh, en, 12, color, false);
        view.setLineSpacing(0f, 1.15f);
        return view;
    }

    private static TextView info(Context context, String value, int color, boolean dark) {
        TextView view = label(context, value, value, 12, color, false);
        view.setPadding(dp(context, 12), dp(context, 10),
                dp(context, 12), dp(context, 10));
        view.setTextIsSelectable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF202024 : 0xFFF4F6FA);
        background.setCornerRadius(dp(context, 6));
        view.setBackground(background);
        return view;
    }

    private static EditText edit(Context context, int text, int hint, boolean dark) {
        EditText view = new EditText(context);
        view.setTextColor(text);
        view.setHintTextColor(hint);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setPadding(dp(context, 12), dp(context, 10),
                dp(context, 12), dp(context, 10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF202024 : 0xFFF4F6FA);
        background.setCornerRadius(dp(context, 6));
        background.setStroke(dp(context, 1), dark ? 0xFF48484E : 0xFFD8DCE5);
        view.setBackground(background);
        return view;
    }

    private static TextView action(Context context, String zh, String en,
                                   int color, boolean dark) {
        TextView view = label(context, zh, en, 13, color, false);
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(context, 10), dp(context, 10),
                dp(context, 10), dp(context, 10));
        view.setClickable(true);
        view.setFocusable(true);
        GradientDrawable background = new GradientDrawable();
        background.setColor(dark ? 0xFF2A2A2E : 0xFFF1F3F8);
        background.setCornerRadius(dp(context, 6));
        view.setBackground(background);
        return view;
    }

    private static TextView label(Context context, String zh, String en,
                                  int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(t(context, zh, en));
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private static View line(Context context, int color) {
        View view = new View(context);
        view.setBackgroundColor(color);
        view.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 1)));
        return view;
    }

    private static LinearLayout.LayoutParams inset(Context context,
                                                   int top, int bottom) {
        LinearLayout.LayoutParams params = matchWrap();
        params.setMargins(dp(context, 16), dp(context, top),
                dp(context, 16), dp(context, bottom));
        return params;
    }

    private static LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int statusBarHeight(Context context) {
        int id = context.getResources().getIdentifier(
                "status_bar_height", "dimen", "android");
        return id > 0 ? context.getResources().getDimensionPixelSize(id) : 0;
    }

    private static boolean isDark(Context context) {
        int mode = context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return mode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private static int dp(Context context, float value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String t(Context context, String zh, String en) {
        return UiLanguage.text(context, zh, en);
    }
}
