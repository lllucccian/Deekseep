# Building 1.7.4 from source

The public source edition intentionally excludes the Local API, tunnel
connectors, and protected-build payloads. It otherwise uses the same 1.7.4
feature core for the supported domestic and Google Play DeepSeek packages.

Requirements: JDK 17, Bash, Android SDK Platform 35 or newer, Android Build
Tools (`aapt2`, `d8`, `zipalign`, and `apksigner`), `zip`, and `curl`.

Set `ANDROID_SDK_ROOT` or `ANDROID_HOME`, then run:

```bash
bash scripts/build-all.sh
```

For one channel only:

```bash
(cd module-universal && bash build.sh)
(cd module-universal && GOOGLE_PLAY_BUILD=true bash build.sh)
```

The scripts create a local development signing key when needed. Do not use that
key as a production identity. Build products and keys are ignored by Git.

The universal entry declares Xposed API 82 as its minimum and has no maximum;
the compatibility regression exercises API values 82 through 102.
