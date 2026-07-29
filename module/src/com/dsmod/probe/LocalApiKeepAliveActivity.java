package com.dsmod.probe;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

/** Invisible user-initiated bridge that controls the module-private foreground keeper. */
public final class LocalApiKeepAliveActivity extends Activity {
    static final String SCHEME = "deekseep-module";
    static final String HOST = "local-api-keepalive";
    static final String QUERY_MODE = "mode";
    static final String QUERY_TOKEN = "token";
    static final String EXTRA_PUBLIC_TUNNEL_RECEIVER =
            "deekseep_public_tunnel_result_receiver";
    static final String EXTRA_PUBLIC_TUNNEL_BINDER =
            "deekseep_public_tunnel_binder";
    static final String MODE_START = "start";
    static final String MODE_STOP = "stop";
    static final String MODE_PROTOCOL_OPENAI = "protocol-openai";
    static final String MODE_PROTOCOL_ANTHROPIC = "protocol-anthropic";
    static final String MODE_PUBLIC_TUNNEL_BIND = "public-tunnel-bind";

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        handle(getIntent());
        finishImmediately();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handle(intent);
        finishImmediately();
    }

    private void handle(Intent intent) {
        Uri data = intent == null ? null : intent.getData();
        if (data == null || !SCHEME.equals(data.getScheme()) || !HOST.equals(data.getHost())
                || !LocalApiKeepAliveService.CONTROL_TOKEN.equals(
                        data.getQueryParameter(QUERY_TOKEN))) return;
        android.os.ResultReceiver receiver = intent.getParcelableExtra(
                EXTRA_PUBLIC_TUNNEL_RECEIVER);
        if (receiver != null) {
            Bundle result = new Bundle();
            result.putBinder(EXTRA_PUBLIC_TUNNEL_BINDER,
                    PublicTunnelBinderBridge.binder(this));
            receiver.send(RESULT_OK, result);
        }
        String mode = data.getQueryParameter(QUERY_MODE);
        if (MODE_START.equals(mode)) {
            LocalApiKeepAliveService.setEnabled(this, true);
        } else if (MODE_STOP.equals(mode)) {
            LocalApiKeepAliveService.setEnabled(this, false);
        } else if (MODE_PROTOCOL_OPENAI.equals(mode)) {
            LocalApiKeepAliveService.sendProtocolControl(this, "openai");
        } else if (MODE_PROTOCOL_ANTHROPIC.equals(mode)) {
            LocalApiKeepAliveService.sendProtocolControl(this, "anthropic");
        } else if (MODE_PUBLIC_TUNNEL_BIND.equals(mode)) {
            // The Binder was returned above; no foreground-service state changes are needed.
        }
    }

    private void finishImmediately() {
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
        finish();
        try { overridePendingTransition(0, 0); } catch (Throwable ignored) {}
    }
}
