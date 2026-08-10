# Building from Source

The repository uses small shell-based Android builds instead of Gradle. The
universal target compiles Java, converts project classes with D8, packages
traditional Xposed metadata, aligns the APK, and signs it with a local
development key. Domestic and Google Play host maps are selected at runtime.

## Requirements

- Git
- Bash
- JDK 17 or newer
- Android SDK Platform 35
- Android Build Tools containing `aapt2`, `d8`, `zipalign`, and `apksigner`
- `zip`
- `curl` for the JSON regression test

Set either `ANDROID_SDK_ROOT` or `ANDROID_HOME`:

```bash
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
```

`scripts/android-tools.sh` selects Android Platform 35 when available, otherwise
the newest installed platform. It searches Termux's `$PREFIX/bin` first, then
the current `PATH`, Android command-line tools, and Android Build Tools.

## Clone and Build the Stable Release

```bash
git clone https://github.com/lllucccian/Deekseep.git
cd Deekseep
bash scripts/build-all.sh
```

The final `dist/` directory contains channel build outputs and checksums. The
GitHub release is intentionally repackaged with only these two assets:

```text
Deekseep.apk
SOURCE-SHA256.txt
```

The release build runs the stable protocol, account, editor and expert-relay
regressions, verifies the universal manifest is version 1.7.4, checks both
channel APKs use the traditional Xposed metadata layout, and refuses test/probe
APKs in `dist/`.

The old test and load-probe projects are intentionally not release targets.
See [Build Variants](VARIANTS.md).

`GOOGLE_PLAY_BUILD=true` remains available for local channel parity testing;
it is not published as a second release APK.

## Build One Channel

```bash
(cd module-universal && bash build.sh)
(cd module-universal && GOOGLE_PLAY_BUILD=true bash build.sh)
```

The unrenamed outputs remain in their project directories:

| Project | Output |
|---|---|
| `module-universal/` | `ds-probe-universal.apk` |
| `module-universal/` with `GOOGLE_PLAY_BUILD=true` | `ds-probe-universal-google-play.apk` |

## Termux

The scripts were originally developed on Termux/ARM. Android SDK desktop tools
are commonly x86 binaries and cannot run natively there, so tool discovery
prefers Termux-native `aapt2`, `zipalign`, and `apksigner` under `$PREFIX/bin`.
D8 and `android.jar` are loaded from the configured Android SDK.

Run the same root command:

```bash
cd ~/deepseek
bash scripts/build-all.sh
```

When shared storage is available, individual build scripts make a best-effort
copy of their APK to `/storage/emulated/0/`. A failed optional copy does not fail
the build.

## Shared Core and Universal Adapter

`module/src/com/dsmod/probe` is the canonical 1.7.4 feature core. Both channel
builds compile the same `Main.java` and feature classes through
`module-legacy/compat/LegacyXposedModule.java`, so the domestic and Google Play
packages do not drift.

The adapter includes small declarations under
`module-legacy/src/de/robv/android/xposed/`. They expose only compile-time
signatures. D8 packages only `com/dsmod`, so these stubs do not shadow framework
classes at runtime.

The traditional entry declares API 82 as its minimum and no maximum. The adapter
regression executes the complete hook/proceed/fail-open contract once for every
framework API value from 82 through 102. The release build also verifies that
every canonical feature class is present in both the mainland and Google Play
APKs, preventing a channel build from silently omitting a newer feature.

## Generated Files and Signing

Every build generates `BuildInfo.java` with the API version and build time.
Build directories, BuildInfo files, APKs, signature sidecars, and keystores are
ignored by Git.

If no local keystore exists, a build script creates a development key. These
keys are not release-grade identity keys. CI creates fresh temporary keys, and
APK signatures can therefore differ between machines. Android may require an
uninstall before installing a build signed elsewhere.

## Regression Tests

The stable build includes JVM regressions for chat editing, account credentials,
regional login policy, chat-appearance motion configuration and chat/settings
route recognition, expert
relay, response preservation, native-session
refresh/delete behavior, and the local API protocol/tool bridge:

```bash
cd module-universal
bash build.sh
GOOGLE_PLAY_BUILD=true bash build.sh
bash test-thinking-regression.sh
bash test-expert-relay-regression.sh
(cd ../module-legacy && bash test-adapter-regression.sh)
```

`test-thinking-regression.sh` runs the Java regression classes, including
`OpenAiToolBridgeRegressionTest` and
`LocalApiGatewayProtocolRegressionTest`. The latter checks Chat and Responses
tool objects, SSE frames, namespace calls, and `previous_response_id` tool-result
continuation with a fake native backend. Run `build.sh` first because the test
classpath includes the freshly compiled production classes.

The legacy adapter regression verifies that several canonical interceptors on
the same reflected member register one traditional callback, preserve replaced
arguments through the complete `chain.proceed()` order, and use the lowest
callback priority so other modules' before/after hooks are not suppressed.

It verifies that adding reasoning:

- creates a numeric, unique fragment ID;
- places the reasoning fragment before the response;
- keeps the response ID and content unchanged;
- repairs an old reasoning fragment without an ID;
- is idempotent on the second repair pass.

The relay test verifies that an explicit first-turn expert request and a
later-turn request with a send-point-captured expert model both enter the relay,
while missing or explicit non-expert model contexts remain untouched. It also
checks that the modern and legacy relay gate implementations are identical.

## Package Verification

```bash
apksigner verify --verbose module-universal/ds-probe-universal.apk
unzip -l module-universal/ds-probe-universal.apk | grep 'assets/xposed_init'
unzip -l module-universal/ds-probe-universal-google-play.apk | grep 'assets/xposed_init'
sha256sum dist/*.apk
```

Both universal APKs must contain `assets/xposed_init` and neither should contain
compiled framework stub classes.

## Continuous Integration

`.github/workflows/build.yml` installs Android Platform and Build Tools 35,
builds and tests the domestic and Google Play universal interfaces, and uploads the exact 1.7.4 `dist/`
contents as a workflow artifact on pushes, pull requests, and manual dispatches.
Test editions are not built or uploaded.
