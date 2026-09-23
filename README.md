# Deekseep LSPosed

Deekseep is an independent LSPosed/Xposed module for the official DeepSeek
Android app. It runs in the DeepSeek process and provides an optional module
settings entry and compatibility layer. It is not made by or affiliated with
DeepSeek.

[简体中文](README_CN.md) | [Changelog](CHANGELOG.md)

> **QQ Group: 1106465300** — Feedback and discussion welcome!
>
> **Telegram Group:** [@Deekseepapp](https://t.me/Deekseepapp)

---

### Indefinite Hiatus & Statement on Open-Source Degradation

I don't know when the open-source community environment deteriorated to this extent.

The original purpose of creating and open-sourcing this project was to provide users with a better mobile experience and greater freedom. However, recent events have left me completely disheartened and profoundly disappointed:
- **Blatant GPL License Violations & Code Theft**: Multiple projects have been copying and repackaging this codebase while refusing to abide by the GPL license, giving zero attribution and closing their derived sources.
- **Author Impersonation**: Malicious actors have been actively impersonating the author across platforms to deceive community members.
- **Rampant Commercial Resale & Abuse**: A free and open tool has been repeatedly stolen and resold for profit by opportunists, leading to widespread abuse and platform risk control countermeasures.

A developer's genuine passion and dedication should not become fuel for opportunists. Given this toxic environment, I have lost all enthusiasm to maintain public releases here.

**Effective Immediately:**
1. **Public open-source repository updates are suspended indefinitely**, with no plans for future open-source releases.
2. **The Closed edition will NO LONGER be updated or distributed via GitHub**. Due to rampant abuse, legitimate users must join the official **Telegram Community** ([@Deekseepapp](https://t.me/Deekseepapp)) to obtain Closed builds. Please do not request releases here.

Thank you to everyone who supported this project with sincerity and kindness along the way.

---

## Current Version (v1.8)

- [Build from source](docs/BUILDING.md)
- [Full English Changelog](CHANGELOG.md)

> **Notice on Prebuilt Binaries**: Due to resale and abuse, prebuilt APK release artifacts are no longer published on GitHub releases. You can build from source or obtain builds from the Telegram group.

Adapted for Mainland China DeepSeek **2.4.1** (versionCode 257), 2.3.6, and 2.3.4.

## Requirements

- Android 7.0 or newer (API 24+).
- Official DeepSeek package `com.deepseek.chat`.
- Mainland DeepSeek 2.4.1 (versionCode 257), DeepSeek 2.3.6 (249), DeepSeek 2.3.4 (245/246), DeepSeek 2.3.0 (237), or DeepSeek 2.2.x.
- LSPosed/Xposed that can load the traditional Xposed entry, covering API 82–102.
- Root or the permissions required by your LSPosed/Xposed setup.

## Before Installing

1. Confirm the DeepSeek package, channel, and version code in Android app info.
2. Back up important conversations and local files.
3. Install and configure LSPosed/Xposed first, then enable its module scope.
4. Remove battery restrictions from DeepSeek if you need background requests or notifications.

## Source and Releases

- [Build from source](docs/BUILDING.md)
- [Report a reproducible problem](https://github.com/lllucccian/Deekseep/issues)

> **About Downloads**: Pre-compiled Open edition Release APK (`Open.apk`), Debug APK (`Open-debug.apk`), SHA-256 checksums (`SHA256.txt`), and source archive are available under GitHub Releases. Closed edition is distributed only via the official Telegram group.

This repository contains the complete source for the 1.8 Open edition. Except for the Local API and closed-source protection components, core module features remain aligned.

Licensed under [GPL-3.0-only](LICENSE).

---

**Sponsor the author:** [爱发电](https://www.afdian.com/a/lllucccian)

**User discussion:** QQ Group 1106465300

**Telegram:** [@Deekseepapp](https://t.me/Deekseepapp)
