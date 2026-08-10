# Installation

## Prerequisites

- Android 7.0 or newer.
- The official DeepSeek Android client installed as `com.deepseek.chat`.
- A current LSPosed/Xposed build that can load the traditional entry. API 82 through 102 are verified by the compatibility matrix.
- A current backup of important conversations.

This repository does not distribute the DeepSeek APK, patched target APKs, or
framework installers.

## Choose the merged APK

For DeepSeek 2.2.0, 2.3.0 (`versionCode 237`), or 2.3.4 domestic/Google Play
(`versionCode 245/246`), install:

```text
Deekseep.apk
```

The runtime detects the installed channel and chooses its mapping. DeepSeek
2.3.1–2.3.3 are unsupported; upgrade to 2.3.4.

The former modern and legacy test editions are discontinued in 1.7.1 and are
not release downloads. Maintained optional tools now live on the dedicated
**Experimental Features** page in both stable builds and remain off by default.

See [Build Variants](VARIANTS.md) for the full comparison.

## Install on Current LSPosed

1. Download `Deekseep.apk` and verify its SHA-256 value against
   `SOURCE-SHA256.txt`.
2. Install the APK.
3. Enable Deekseep in LSPosed.
4. Select `com.deepseek.chat` in the module scope. Do not select the module app
   itself.
5. Force-stop DeepSeek.
6. Start DeepSeek, read the short first-use note, and select **Got it**.
7. Open DeepSeek Settings. The Deekseep entry should appear on the settings
   screen.

The launcher reports **Enabled** when the official Xposed service connects and
**Active** after the DeepSeek target process sends its UID-validated heartbeat.

Only one Deekseep package should be enabled for a given DeepSeek process.

## First Safe Configuration

1. Leave response and protocol diagnostics disabled.
2. Use **Back up chat database now** before opening the editor.
3. Import a small test prompt.
4. Enable prompt injection and test it in a disposable conversation.
5. Enable response preservation or one experimental feature at a time so a
   failure can be attributed to one feature.
6. Leave the DeepSeek Local API disabled unless a trusted local client needs it.
   When enabled, copy the Key only from its control page and treat connection
   files and API logs as credentials. Its foreground keeper intentionally increases
   background battery use. See [DeepSeek Local API](LOCAL_DEEPSEEK_API.md).

## Upgrading from 1.7 or an Older Reasoning Writer

The maintained 1.7.2 builds scan local assistant rows for a `THINK` fragment
without a numeric `id` and repair it idempotently.

1. Install the 1.7.2 build matching both your DeepSeek channel and framework.
2. Confirm it is the only enabled Deekseep hook.
3. Force-stop and restart DeepSeek.
4. Open the affected conversation.

The migration preserves the original response and gives the malformed reasoning
fragment a unique ID. A diagnostic line reports
`repairMalformedThinkFragments fixed=N`. A later launch should report zero.

The 1.7.2 APKs have higher Android version codes for their stable interface
tracks. Switching between modern and traditional interfaces still requires an
uninstall when those builds use different keys.

## Rollback

If DeepSeek crashes or behaves incorrectly:

1. Disable Deekseep in the injection framework.
2. Force-stop and restart DeepSeek.
3. Restore a database backup if local editing or deletion changed data.
4. Report the exact DeepSeek version, module variant, framework version, and
   redacted error lines.

Disabling or uninstalling the module does not automatically undo database edits
already written by the conversation editor.
