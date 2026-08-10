# Deekseep LSPosed

An independent LSPosed/Xposed module that adds account, chat, image, interface, and local API tools to the official DeepSeek Android app.

English | [简体中文](README_CN.md)

[![Latest Release](https://img.shields.io/github/v/release/lllucccian/Deekseep?display_name=tag&sort=semver)](https://github.com/lllucccian/Deekseep/releases/latest)
[![GitHub Downloads](https://img.shields.io/github/downloads/lllucccian/Deekseep/total?label=Downloads)](https://github.com/lllucccian/Deekseep/releases)
[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3ddc84)](#requirements)
[![Universal Xposed](https://img.shields.io/badge/Xposed-universal-2f6feb)](#requirements)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

> [!NOTE]
> Deekseep is an independent enhancement module. Check that the APK matches
> your DeepSeek version, and back up important data before using chat, account,
> or experimental tools.

## 1.7.4 release

The 1.7.4 release refactors the module and settings UI into searchable
**Chat**, **Account & Privacy**, **Appearance**, **Debugging**, and
**Engineering** categories. It also adds compact switch controls, aligned
execution buttons, and clearer settings entry points (the small gear button to
the right of a feature name).

Highlights:

• Basic Agent tools (download and inspect the available tools in the app).
• Automatic continue-generation for server-paused long thinking.
• A visible **Reply-ready notification** switch.
• Local mute/ban and custom home greeting.
• Custom assistant avatar (requires **Show assistant avatar** in the Feature Flag Manager).
• Whale rotation and deep-sea text-wave effects.
• On-screen Hook logs and crash recording/tests.
• Custom DeepSeek requests, hot-update blocking, cache cleanup, and process management.
• Long-context file upload for the local API.
• Foreground-heartbeat persistence without a mandatory background-running exemption.
• Battery optimization exemptions for DeepSeek are still recommended.

The music Agent tool requires the latest QQ Music **20.7 or newer**; after
granting Root, it can play music automatically in the background. The native
settings injection is experimental and may cause the host app to crash.
“Disable data used for service improvement” actively turns off that host
setting and prevents it from being enabled again. When a background response
finishes, the new notification returns you to the conversation; the switch is
in the Chat section beside automatic continue-generation.

This release is one merged domestic/Google Play runtime APK and is strongly
recommended with DeepSeek **2.3.4** (version codes 245/246). DeepSeek 2.2.0
and 2.3.0 remain supported with possible feature gaps; **2.3.1–2.3.3 are not
supported**. Some features may still be unavailable or abnormal on older host
builds—please report reproducible problems so they can be fixed quickly.

Support development at [爱发电](https://www.afdian.com/a/lllucccian).

## Compatibility at a glance

> [!TIP]
> Deekseep LSPosed 1.7.4 ships one universal APK. Its compatibility layer
> covers domestic and Google Play DeepSeek 2.2.0, 2.3.0, and 2.3.4 host
> symbol families at runtime.

- Domestic or Google Play build: DeepSeek 2.2.0, 2.3.0 (`versionCode 237`), or 2.3.4 (`versionCode 245/246`) — supported by the universal APK.
- Android: 7.0 or newer (API 24+).
- Framework: an Xposed-compatible LSPosed environment exposing the traditional entry; API 82 through 102 are covered by the regression matrix.
- Module scope: `com.deepseek.chat` only.

## Download

### [Download Deekseep LSPosed 1.7.4](https://github.com/lllucccian/Deekseep/releases/download/v1.7.4/Deekseep.apk)

This is the only maintained module APK. The runtime detects the domestic or
Google Play host and selects its mapping internally.

<details>
<summary>More project screenshots</summary>

| Data tools, language, and module information | Experimental features and usage note |
|---|---|
| <img src="docs/images/data-tools-preview.jpg" alt="Deekseep LSPosed data tools and module information" width="320"> | <img src="docs/images/experimental-features-preview.jpg" alt="Deekseep LSPosed Experimental Features page" width="320"> |

</details>

## What is Deekseep LSPosed?

Deekseep LSPosed runs inside the official DeepSeek Android app through a compatible LSPosed/Xposed environment. It adds local conversation and account tools, prompt and interface controls, image workflows, and an optional developer-facing API gateway.

This is an independent third-party project. It is not part of, affiliated with, endorsed by, or supported by DeepSeek.

## Features

### Chat tools

- Import a system prompt and inject it into outgoing requests without changing the visible input box.
- Edit local conversation titles, user messages, model responses, reasoning text, reasoning duration, and message images. Create local conversations and search across prompts, answers, and reasoning.
- Current supported source builds can import images as chat wallpaper or stickers. Wallpaper supports crop focus, rotation, opacity, optional depth, and either unified or per-screen offsets. Its motion uses a fast-starting ease-out curve, follows the main screen right with the sidebar, moves left in settings, and smoothly returns in both directions. Advanced binding selects chat/sidebar/settings visibility. Stickers remain on chat and settings screens and can be moved, resized, rotated, layered, or faded.
- Export conversations as Markdown, view local statistics, create manual and rotating automatic database backups, and optionally batch-select conversations for deletion.
- Preserve text already delivered to the device when the known client-side `CONTENT_FILTER` replacement event occurs. This cannot recover text the server never sent.

### Account tools

- Save multiple account slots and explicitly add, switch, remove, import, or export selected account records with validation before imported credentials are stored.
- Optionally restore DeepSeek's native Google sign-in entry on the mainland login page, or its native WeChat and SMS entries on overseas login pages. Server-side account, region, and risk checks still apply.

### Image tools

- Reuse or replace images attached to locally edited messages while keeping durable private copies for later rendering.
- Experimentally relay expert-mode image requests through temporary vision sessions and preserve image metadata in local history. Availability remains dependent on the DeepSeek service.

### Developer and API tools

- Run an opt-in, Gateway-Key-protected local/trusted-LAN service that exposes OpenAI Chat Completions/Responses or Anthropic Messages-compatible endpoints through DeepSeek's native transport.
- Use streaming, tool-result continuation, Codex and Claude Code tool loops, deep-thinking parameters, native web search, and live request diagnostics. Advanced settings can pin the listener port and connect an existing custom hostname through a user-owned Cloudflare Tunnel token. The gateway is under the optional Experimental Features page and is disabled by default.

### Interface and compatibility tools

- Open the Deekseep LSPosed settings entry inside DeepSeek, with Chinese/English selection and automatic host-language detection.
- Use the single universal Xposed-compatible package; its runtime table selects the supported host generation.

See the [feature reference](docs/FEATURES.md) and [Experimental Features notice](docs/EXPERIMENTAL_FEATURES.md) for behavior and limits.

## Requirements

- Android 7.0 / API 24 or newer.
- The official DeepSeek Android app in one of the exact supported channel builds listed above.
- A supported LSPosed/Xposed loading environment and any root/framework setup required by that environment.
- An Xposed-compatible LSPosed environment that can load the traditional entry; API 82 through 102 are verified.
- LSPosed/Xposed scope set to `com.deepseek.chat`.
- A current backup of important conversations before using database, account, deletion, or experimental tools.

The repository does not distribute the official DeepSeek APK, a rooting solution, or an LSPosed/Xposed installer.

## Installation

1. In Android app information, verify the installed DeepSeek channel and version code (2.2.0, `237` for 2.3.0, or `245/246` for 2.3.4).
2. Back up important DeepSeek conversations and local files.
3. Download the universal Deekseep APK and enable it in the compatible Xposed framework.
4. Install the module APK and enable it in the LSPosed/Xposed manager.
5. Select only `com.deepseek.chat` as the module scope. Do not add the modern module application itself to scope.
6. Force-stop DeepSeek, then open it again. A full device reboot is normally unnecessary; use one only if your framework does not reload the module after restarting the target app.
7. Read the short first-use note, select **Got it**, then open DeepSeek Settings and choose the injected Deekseep entry.

The universal APK uses package ID `com.dsmod.probe`. See the full [installation guide](docs/INSTALLATION.md).

## Compatibility table

| App channel | App version | Version code | Status | Notes |
|---|---:|---:|---|---|
| Domestic or Google Play | 2.2.0 | Varies | ✅ Supported with possible gaps | Use the merged runtime; 2.2.x symbol coverage is retained. |
| Domestic or Google Play | 2.3.0 | 237 | ✅ Supported with possible gaps | The runtime selects the 2.3.0 map. |
| Domestic or Google Play | 2.3.4 | 245/246 | ✅ Recommended | One APK selects the channel-specific map at runtime. |
| Any channel | 2.3.1–2.3.3 | Varies | ❌ Unsupported | Upgrade to 2.3.4. |

## Troubleshooting

- The Deekseep LSPosed entry does not appear: verify the exact app channel/version, install the matching APK, enable only one module variant, scope `com.deepseek.chat`, and fully force-stop DeepSeek before reopening Settings.
- The module is enabled but hooks do not work: check the launcher activation state, use only one module variant, and do not self-scope the module app. Disable other modules that may hook the same screen or request path.
- The DeepSeek version is incompatible: disable Deekseep LSPosed and confirm the unmodified app works. Use only documented version codes; an app update may require a new symbol mapping.
- The framework entry is incompatible: use an Xposed-compatible LSPosed build that supports the traditional universal entry; the supported matrix is API 82 through 102.
- The host is unsupported: upgrade to DeepSeek 2.3.4; 2.3.1–2.3.3 are intentionally not supported.
- Features fail after a DeepSeek update: disable the module, restart DeepSeek, and report the new channel, `versionName`, and `versionCode`. Future app versions are not automatically supported.
- Multi-account tools fail: back up current account data, test one add/import operation at a time, and retain the original active account until validation succeeds. Never post exported account JSON publicly.
- Image tools fail: verify the system photo picker can read the file and test one image first. Expert image relay is experimental and can fail because of server permissions, model routing, proof-of-work, or changed host internals.
- Collecting logs: reproduce once, then copy only a short excerpt around the first error from the module's diagnostics. Remove tokens, cookies, authorization data, account information, email addresses, phone numbers, device identifiers, private server addresses, prompts, responses, file URLs, and any other private data.
- Opening an issue: search existing reports, then use the [Bug report](https://github.com/lllucccian/Deekseep/issues/new?template=bug_report.yml) or [Compatibility report](https://github.com/lllucccian/Deekseep/issues/new?template=compatibility_report.yml) form with exact versions and a minimal redacted log.

More cases are covered in [Troubleshooting](docs/TROUBLESHOOTING.md).

## Before using optional tools

- Match the APK to the listed DeepSeek channel and `versionCode`.
- Back up important chats before editing, deleting, or switching accounts.
- Keep account exports, API keys, and diagnostic logs private.

The concise [project notice](DISCLAIMER.md) has more detail. Experimental
features show a one-time usage note and remain off until you enable them.

## Roadmap

The public API implementation plan currently records these statuses:

- Completed: OpenAI and Anthropic formats, stable mainland interfaces, the exact Google Play 2.2.2 mapping, and the gated Experimental Features page.
- Planned: explicit socket-to-host cancellation confirmation, API image input, persistent Responses state with idempotency keys, a redacted diagnostic bundle, and broader Anthropic/Claude Code regression coverage.
- Not scheduled: support for additional DeepSeek versions. Each host update requires compatibility confirmation and may require a new mapping.

See the [local API implementation plan](docs/LOCAL_DEEPSEEK_API_GATEWAY_PLAN.md). Planned work is not part of the current feature set until it is implemented and released.

## Contributing

Contributions are welcome for new-version compatibility testing, Google Play mapping updates, focused hook repairs, documentation, bug reports, translations, interface screenshots, and installation testing.

Before contributing, read [CONTRIBUTING.md](CONTRIBUTING.md), search the [Issues](https://github.com/lllucccian/Deekseep/issues), and describe the exact DeepSeek channel, app version, version code, Android version, and LSPosed/Xposed environment. Focused changes can be proposed through [Pull Requests](https://github.com/lllucccian/Deekseep/pulls).

## Project notice

Deekseep LSPosed is an independent third-party project and is not part of
DeepSeek. Product names and trademarks belong to their respective owners. See
the concise [project notice](DISCLAIMER.md) for compatibility, data, and
privacy notes.

## Acknowledgements

The settings information hierarchy and interaction ideas were reviewed against
WeKit as a UI reference only; no WeKit source code or assets were copied.
Third-party libraries and their licenses are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## License

Project-owned source and documentation are licensed under [GNU GPL-3.0-only](LICENSE). Third-party components and notices are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

If Deekseep LSPosed is useful to you, consider giving the repository a ⭐ or
[sponsoring development](https://www.afdian.com/a/lllucccian) so more DeepSeek
and LSPosed users can find it.
