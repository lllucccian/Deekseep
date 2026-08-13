# Deekseep LSPosed

Deekseep is an independent LSPosed/Xposed module for the official DeepSeek
Android app. It runs in the DeepSeek process and provides an optional module
settings entry and compatibility layer. It is not made by or affiliated with
DeepSeek.

[简体中文](README_CN.md)

## Stable release

- [Download Open.apk](https://github.com/lllucccian/Deekseep/releases/download/v1.7.4-fix/Open.apk) — free, open-source edition without the Local API.
- [Download Closed.apk](https://github.com/lllucccian/Deekseep/releases/download/v1.7.4-fix/Closed.apk) — free, closed-source edition with the Local API.

Both editions are universal APKs for the supported domestic and Google Play
hosts. Mainland DeepSeek 2.3.6 and domestic/Google Play DeepSeek 2.3.4 are
supported. DeepSeek 2.2.0 and 2.3.0 remain usable with possible limitations;
2.3.1–2.3.3 and 2.3.5 are not supported.

## Requirements

- Android 7.0 or newer (API 24+).
- Official DeepSeek package `com.deepseek.chat`.
- Mainland DeepSeek 2.3.6 (version code 249), DeepSeek 2.3.4 (245/246),
  DeepSeek 2.3.0 (237), or DeepSeek 2.2.x.
- LSPosed/Xposed that can load the traditional Xposed entry, covering API 82–102.
- Root or the permissions required by your LSPosed/Xposed setup.

## Before installing

1. Confirm the DeepSeek package, channel, and version code in Android app info.
2. Back up important conversations and local files.
3. Install and configure LSPosed/Xposed first, then enable its module scope.
4. Remove battery restrictions from DeepSeek if you need background requests or notifications.

## Installation

1. Download one of the APKs above and install it. Do not install both editions at the same time.
2. Enable **Deekseep** in LSPosed/Xposed.
3. Scope it only to `com.deepseek.chat`; do not add unrelated apps.
4. Force-stop and reopen DeepSeek. Reboot only if your framework does not reload the target process.

The module does not include the official DeepSeek APK, a rooting solution, or an
LSPosed/Xposed installer. Experimental settings can affect the host app; keep a
backup and disable the module if the host becomes unstable.

## Source and releases

- [Build from source](docs/BUILDING.md)
- [Release notes](https://github.com/lllucccian/Deekseep/releases)
- [Report a reproducible problem](https://github.com/lllucccian/Deekseep/issues)

This repository contains the complete source for the 1.7.4 Fix Open edition. For
certain reasons, the Local API is no longer open source and has moved to the
Closed edition; source code for the Closed edition is not published. All other
module features are kept in sync between the two free editions.

Licensed under [GPL-3.0-only](LICENSE).

---

**Sponsor the author to accelerate development:** [爱发电](https://www.afdian.com/a/lllucccian)
