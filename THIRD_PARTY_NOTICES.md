# Third-Party Notices

## libxposed API

The modern module projects contain an unmodified `classes.jar` extracted from
the official libxposed API 102 AAR as their compile-only `libs/api.jar`.

- Project: libxposed API
- Version: 102.0.0
- Source: https://github.com/libxposed/api
- Release: https://github.com/libxposed/api/releases/tag/102.0.0
- License: Apache License 2.0
- JAR SHA-256:
  `a515dd7a53cd7a47c05e101dff77d61acb3091a97b20a885b9ea3494412db985`

The complete license is included at
[third_party/libxposed-api-LICENSE.txt](third_party/libxposed-api-LICENSE.txt).
The JAR is not packaged in Deekseep APKs; it is used only during compilation.

## OmniRoute DeepSeek tool bridge

The local API tool translation adapter is based on OmniRoute's DeepSeek web tool bridge. An
unmodified source snapshot is retained for audit and upstream comparison, while the APK contains
an Android/Java adaptation of the same prompt and parsing state machine.

- Project: OmniRoute
- Source: https://github.com/diegosouzapw/OmniRoute
- Snapshot commit: `dffff5d656c169e41c4862cb38affbd9992f24a5`
- License: MIT
- Preserved files: `third_party/omniroute-tool-bridge/webTools.ts` and
  `third_party/omniroute-tool-bridge/deepseekWebTools.ts`

The complete license and snapshot notes are included under
[third_party/omniroute-tool-bridge](third_party/omniroute-tool-bridge/README.md).

## Cloudflare cloudflared

The APK packages unmodified Android executables from the Termux build of
Cloudflare's open-source `cloudflared` connector. Android selects one executable
for the current ABI at installation time.

- Project: Cloudflare cloudflared
- Version: 2026.6.0
- Source: https://github.com/cloudflare/cloudflared/tree/2026.6.0
- Android packaging recipe: https://github.com/termux/termux-packages/tree/master/packages/cloudflared
- License: Apache License 2.0

The complete license, binary checksums, and reproducible fetch information are
included under [third_party/cloudflared](third_party/cloudflared/NOTICE.md).
