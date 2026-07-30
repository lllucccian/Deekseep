# Installation

## Prerequisites

- Android 7.0 or newer.
- The official DeepSeek Android client installed as `com.deepseek.chat`.
- An Xposed-compatible environment using API 82, 100, 101, or 102.
- A current backup of important conversations.

This repository does not distribute the DeepSeek APK, patched target APKs,
rooting solutions, or framework installers.

## Choose One APK

Deekseep 1.7.3 provides only two installable APKs. Choose by DeepSeek channel,
not by Xposed API.

For Mainland China DeepSeek 2.2.2 (`versionCode 233`) or 2.3.0
(`versionCode 237`):

```text
deekseep-mainland-universal-api82-100-101-102-v1.7.3.apk
```

For Google Play DeepSeek 2.2.2 (`versionCode 236`) only:

```text
deekseep-google-play-universal-api82-100-101-102-v1.7.3.apk
```

The Google Play package does not support the latest Google Play DeepSeek.
Mainland and Google Play APKs are not interchangeable. Dedicated API 102,
Legacy, test, and diagnostic APKs are no longer current release downloads.

Use the direct links on the repository [home page](../README.md), or open the
[1.7.3 Release](https://github.com/lllucccian/Deekseep/releases/tag/v1.7.3).

## Install

1. Confirm the installed DeepSeek channel and `versionCode`.
2. Download the matching multi-API APK and verify it against
   `SHA256SUMS.txt`.
3. Install the module APK.
4. Enable Deekseep in the LSPosed/Xposed manager.
5. Select only `com.deepseek.chat` in module scope. Do not select the module
   application itself.
6. Force-stop DeepSeek and open it again.
7. Read the short first-use note, then open DeepSeek Settings and select the
   injected Deekseep entry.

The launcher reports **Enabled** when the Xposed service connects and
**Active** after the DeepSeek target process completes its validated activation
handshake. Version 1.7.3 improves this check so an injected target no longer
remains indefinitely at **Pending verification**.

## Traditional/FPA Environments

The same 1.7.3 multi-API APK contains the API 82-compatible entry. Follow the
framework's normal module installation or injection workflow, select
`com.deepseek.chat`, and restart the target process.

Framework behavior can vary by version. Do not install a retired Legacy APK
alongside the universal build.

## Upgrading from an Older Build

1. Back up important conversations and module configuration.
2. Disable the old Deekseep module.
3. Try installing the matching 1.7.3 channel APK as an update.
4. If Android reports an incompatible signature, uninstall only the old module
   APK and then install 1.7.3. This does not uninstall DeepSeek.
5. Enable only the new module, reselect `com.deepseek.chat`, and restart
   DeepSeek.

The maintained 1.7.3 builds still repair old local assistant rows whose
`THINK` fragment has no numeric ID. The migration is idempotent and preserves
the original response.

## First Safe Configuration

1. Leave response and protocol diagnostics disabled.
2. Use **Back up chat database now** before opening the editor.
3. Import a small test prompt.
4. Enable prompt injection and test it in a disposable conversation.
5. Enable optional features one at a time so a failure can be attributed to a
   single change.
6. Leave the DeepSeek Local API disabled unless a trusted client needs it.
   Treat its Gateway Key, connection files, and logs as credentials.

See [DeepSeek Local API](LOCAL_DEEPSEEK_API.md) for gateway configuration.

## Rollback

If DeepSeek crashes or behaves incorrectly:

1. Disable Deekseep in the injection framework.
2. Force-stop and restart DeepSeek.
3. Restore a database backup if local editing or deletion changed data.
4. Report the exact DeepSeek channel/version, Deekseep version, framework
   version, and a minimal redacted error excerpt.

Disabling or uninstalling the module does not automatically undo database
edits already written by the conversation editor.
