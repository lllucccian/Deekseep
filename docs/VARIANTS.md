# Build Variants

Deekseep 1.7.3 publishes exactly two installable APKs. Both use the same
API 82 / 100 / 101 / 102 compatibility packaging; the only public choice is
the DeepSeek channel.

## Selection Guide

| Release asset | DeepSeek target | Xposed interfaces |
|---|---|---|
| `deekseep-mainland-universal-api82-100-101-102-v1.7.3.apk` | Mainland 2.2.2 (`233`) and 2.3.0 (`237`) | API 82 / 100 / 101 / 102 |
| `deekseep-google-play-universal-api82-100-101-102-v1.7.3.apk` | Google Play 2.2.2 (`236`) only | API 82 / 100 / 101 / 102 |

The Google Play package does not support the latest Google Play DeepSeek.
Mainland and Google Play APKs use different R8 host-symbol mappings and are not
interchangeable.

Dedicated API 102, Legacy, test, and diagnostic packages are retired from the
current release. Historical tags may retain old artifacts for reference, but
they are not current download choices.

## Source Layout

| Source | Purpose | Public release status |
|---|---|---|
| `main:module/` | Canonical Mainland feature source and modern API 102 compile/regression target | Internal validation only |
| `main:module-universal/` | Universal adapter and package for Mainland hosts | Published |
| `google-play:module/` | Canonical Google Play 236 feature source and mapping | Internal validation only |
| `google-play:module-universal/` | Universal adapter and package for Google Play 236 | Published |
| `module-legacy/` | Traditional callback adapter regression fixtures | Not published separately |

The API 102-only build remains useful as a compile-time and parity check, but
`scripts/build-all.sh` places only the universal APK in `dist/`.

## 1.7.3 Feature Parity

The Mainland and Google Play branches maintain the same user-facing feature
core where their host mappings allow it, including:

- settings entry, prompt injection, response preservation, and activation
  diagnostics;
- account import/export with server validation;
- cross-account chat editor, search, export, statistics, and backup;
- local conversation/image persistence, native navigation, and deletion;
- chat wallpaper, stickers, opacity/framing controls, and offline sticker
  cutout;
- regional native-login restoration controls;
- OpenAI Chat/Responses and Anthropic Messages local gateway;
- temporary public URL and custom-domain tunnel controls;
- the optional Experimental Features page and its separate usage notice.

The Mainland package supports 2.2.2 and 2.3.0 through runtime host-generation
detection. The Google Play package remains limited to the separately mapped
2.2.2 (`236`) host.

## Universal Interface Packaging

The release APK:

- uses the canonical feature source for its channel;
- generates a traditional Xposed-compatible entry from `Main.java`;
- declares `assets/xposed_init` and Xposed minimum API 82 metadata;
- runs on supported API 82 / 100 / 101 / 102 framework environments;
- signs with the same project key used for the corresponding channel build;
- includes only the arm64 Cloudflared payload used by the current target
  devices.

Enable only one Deekseep implementation for `com.deepseek.chat`. Duplicate
hooks can rewrite the same request or database row twice and are unsupported.
Builds signed on another machine may require uninstalling the previous module
APK before installation.
