# Third-Party Notices

## Xposed compatibility adapter

The maintained domestic and Google Play packages use the traditional Xposed
entry and a small in-tree compatibility adapter. No modern-only API archive is
required or packaged.

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

## Google Material Symbols

The standalone module application uses Google Material Symbols through the
Compose icon package. The interface layout, data presentation, theme fallback,
and application code are implemented in this project.

- Project: Google Material Symbols
- Source: https://github.com/google/material-design-icons
- License: Apache License 2.0

## WeKit UI reference

The settings information hierarchy and a few interaction ideas were reviewed
against the WeKit project as a visual and product reference. Deekseep does not
copy WeKit source code, assets, or implementation. WeKit remains the property
of its original authors; consult its repository for the applicable license and
notices.
