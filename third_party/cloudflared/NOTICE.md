# cloudflared Android connector

This directory contains unmodified `cloudflared` 2026.6.0 executables built for Android 24+
by the Termux package project from Cloudflare's open-source `cloudflared` source.

- Upstream source: <https://github.com/cloudflare/cloudflared/tree/2026.6.0>
- Android build recipe: <https://github.com/termux/termux-packages/tree/master/packages/cloudflared>
- Upstream license: Apache License 2.0 (see `LICENSE`)
- Bundled command name: `libcloudflared.so`, so Android extracts the ABI-matched executable
  into the application's read-only native library directory.

The bundled files are not modified after extraction from their Termux packages:

| Android ABI | Package architecture | Binary SHA-256 |
| --- | --- | --- |
| `arm64-v8a` | `aarch64` | `b0259b5e0d4664f14fd118f2b2184226fa5154a2b2c30152707b69be8d1c01e5` |
| `armeabi-v7a` | `arm` | `18cb3bb4ea0769d4c0e251e846600d361dbff82831bb1c3ecde9c679ed517186` |
| `x86_64` | `x86_64` | `e12ead14ead5c3527b7236a8af4c0167e79ba3bf08d1f6bab87faaedd52a6fdb` |
| `x86` | `i686` | `8a79b64b6a271c016400e097d4b3fa85022b9358f3f8ce5cdaa8aa51606f9f28` |

`scripts/fetch-cloudflared-android.sh` records and verifies the source package hashes before
replacing these files.
