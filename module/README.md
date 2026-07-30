# Canonical API 102 Compile Target

This project contains the canonical channel feature source and the modern API
102 compile target. It remains part of build and regression validation, but its
APK is no longer a public release choice.

Current release: **1.7.3**.

- Package: `com.dsmod.probe`
- Interface: libxposed API 102
- Metadata: `META-INF/xposed/`
- Output: `ds-probe.apk`
- Internal output: `ds-probe-api102.apk`
- Public package: built by `../module-universal/`

It contains the broadest maintained stable feature set, including the advanced
chat editor with live refresh, persistent blank local conversations, direct
user/AI row appending, gallery image persistence and existing-image reuse,
the current-source chat wallpaper and multi-sticker editor with scaling,
horizontal/vertical framing, rotation, opacity, and offline sticker cutout,
1.7.1 reasoning creation, repair and duration support,
reasoning-aware native chat search, local data tools, sidebar multi-select, and
the current expert image relay port. It also includes independent opt-in switches
that restore DeepSeek's own Google Credential Manager entry on the mainland
login screen, or restore the native WeChat and SMS/mobile entries together on
an overseas login screen. Modern activation uses the official Xposed service
provider and a separately UID-validated heartbeat from the DeepSeek process.

This build additionally contains the local API gateway under a gated
Experimental Features subpage alongside expert image relay. First entry requires a
five-second risk confirmation and has separate help. The manually drawn API control
page exposes a persistent enable switch, actual URL, copy/custom/rotate Key actions,
a current-format row with an OpenAI/Anthropic picker, live queue and failure
statistics, Chat/Responses/Messages non-stream and SSE support, deep-thinking
parameters, Codex/Claude Code tools, and a module foreground keeper for Android's
Cached Apps Freezer. All completions share one fair account-wide native generation
permit; tool-bearing main/Task turns receive priority, while reusable hidden sessions
are isolated by workload plus a hashed client UUID/conversation-root scope so
`/clear` and `/new` start clean branches without returning to session-creation 429s.
Anthropic message/thinking events begin only after native upstream collection starts. OpenAI
Responses additionally tracks Codex thread/session metadata, emits current `phase`, `end_turn`,
and custom-tool status fields, and keeps encrypted transport items out of model context.

The 1.7.3 universal APK compiles this same canonical feature core through
`module-legacy/compat` and supports API 82 / 100 / 101 / 102. Dedicated API
102, Legacy, test, and diagnostic APKs are no longer published as current
release assets.

```bash
cd module
bash build.sh
bash test-thinking-regression.sh
bash test-expert-relay-regression.sh
python ../scripts/test-codex-responses-compat.py
```

See the root [README](../README.md), [feature reference](../docs/FEATURES.md),
[1.7.1 implementation and porting guide](../docs/V1_7_1_PORTING_GUIDE.md),
[local API usage guide](../docs/LOCAL_DEEPSEEK_API.md),
[local API implementation status](../docs/LOCAL_DEEPSEEK_API_GATEWAY_PLAN.md),
and [variant matrix](../docs/VARIANTS.md).
