# Build Variants

Deekseep 1.7.4 publishes one merged universal APK. It compiles the complete
feature core and selects the domestic or Google Play host symbol map at runtime.

## Selection Guide

| Source | 1.7.4 release asset | DeepSeek target | Framework target |
|---|---|---|---|
| `main:module-universal/` | `Deekseep.apk` | 2.2.0/2.3.0 and 2.3.4 domestic/Google Play | Traditional Xposed-compatible universal entry |

Use `Deekseep.apk` for either supported channel. DeepSeek 2.3.1–2.3.3 are not supported.

## 1.7.2 Feature Parity

The two mainland APKs are built from `module/src/com/dsmod/probe`. The Google
Play branch carries the same maintained features with its own mapped host
symbols. All three include:

- settings entry, prompt injection, response preservation and diagnostics;
- account import/export with strict server validation;
- refreshed cross-account chat editor, search, export, statistics and backup;
- local conversation/image persistence, native navigation and deletion;
- regional native-login restoration controls;
- OpenAI Chat/Responses and Anthropic Messages local gateway;
- Codex and Claude Code tool-result loops and conversation isolation;
- the optional **Experimental Features** page, short one-time usage note and
  separate experimental help.

Features in the Experimental Features page remain off by default and are not a
stability guarantee. See [Experimental Features](EXPERIMENTAL_FEATURES.md).

## Test Editions Are Discontinued

Starting with 1.7.1, the former `module-inject/` and
`module-inject-legacy/` test editions are discontinued and receive no GitHub
Release APKs. Their direct Compose settings injection and host long-press menu
experiments were too dependent on obfuscated UI internals to maintain as public
parallel products. The separate `module-mtest/` load probe is also excluded
from the 1.7.1 release.

The historical source directories remain for archaeology and comparison, but
they are not built by `scripts/build-all.sh`, not covered by the 1.7.1 release
tests, and must not be presented as supported downloads. Experimental end-user
features that remain maintained now live behind the dedicated page in the two
stable APKs.

## Interface Packaging

The universal APK:

- implement `IXposedHookLoadPackage` through the in-tree adapter;
- receives packages through `handleLoadPackage`;
- declares `assets/xposed_init` and traditional manifest metadata;
- compile the shared core through `module-legacy/compat`;
- do not package framework-provided Xposed stubs.

The package contains the complete canonical feature core. Its traditional entry
has `xposedminversion=82` and no maximum; API 82 through 102 are exercised by
the adapter matrix during the release test. Host compatibility covers DeepSeek
2.2.0, 2.3.0, and mapped domestic/Google Play 2.3.4 builds.

## Signature and Switching Rules

The stable APK uses `com.dsmod.probe`. A locally rebuilt APK may use a different
development key; Android can then require uninstalling the installed module
before reinstalling the rebuild. This does not uninstall DeepSeek.

Enable only one Deekseep implementation for `com.deepseek.chat`. Duplicate
hooks can rewrite the same request or database row twice and are unsupported.
Release keys are local and excluded from Git, so a build made on another
machine may likewise require uninstalling the previous APK.
