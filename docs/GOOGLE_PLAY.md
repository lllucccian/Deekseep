# Google Play port status

The `google-play` branch is an experimental, build-specific port for Google
Play DeepSeek Android. It is separate from the maintained mainland-China
release and must not be assumed compatible with a later Play Store update.

## Exact target

- Package: `com.deepseek.chat`
- Version name: `2.2.2`
- Version code: `236`
- Minimum SDK: `32`
- Source APKS SHA-256:
  `f9a3f7c313c39a4e825f996b2bd562a408f318c5d7a8e480cf2e6883e44399b3`
- Current module build: `1.7.3` (`versionCode 37`)

No DeepSeek APK, account material, device log, or decompiled source is included
in this repository.

## Feature-parity audit

The Google Play source now carries the same maintained feature set as the
mainland development tree. Host-facing hooks still use Play 236's own R8
symbols; source equality alone is not treated as a mapping proof.

| Area | Google Play 236 status |
|---|---|
| Settings, launcher activation and language | Synchronized. The launcher heartbeat fallback, target verification, Chinese/English catalog and nested settings pages pass local regressions. |
| Prompt import and system-prompt injection | Synchronized and mapped to `tx0`. Imported prompt state remains independent of other settings. |
| Accounts and regional login | Account add/switch/remove/import/export, strict server validation, Google login restoration, and WeChat/mobile restoration are synchronized. |
| Chat data tools | Editor, local conversation persistence, gallery images, native navigation/deletion, search, Markdown export, statistics and backup are synchronized. |
| Response preservation | Clear-response, content-filter, status, final merge/apply, online history and cold-history protection use the Play mappings. |
| Expert mode and image relay | Expert features, native upload, temporary vision description, prompt rewrite and image-metadata restoration are synchronized. |
| Local API and public tunnels | OpenAI Chat/Responses, Anthropic Messages, tool loops, background keeper, Binder bridge, JSch/Pinggy and the arm64 cloudflared backend are synchronized. |
| Chat appearance | Wallpaper, stickers, scale/framing/rotation/opacity, offline cutout, and bubble/input styling are synchronized. Several Easter eggs were also updated. |
| AI proactive messages | Interval control, chat binding, one-time tasks, cancellation tools, inherited deep-thinking state, same-chat native streaming, folded hidden transport turns, notifications and notification-to-chat navigation are synchronized. |

The only intentional channel exception is the private bundled-prompt payload:
it is not packaged, provisioned or exposed by the Google Play build.

## Restored and device-verified before the 1.7.3 parity pass

| Area | Evidence |
|---|---|
| Settings and Experimental Features | Native entry loads; the short first-entry usage note and nested pages render. |
| Expert mode | Expert selection is accepted by the native composer and the expert request path completes. |
| Image upload and expert relay | Native picker, upload, temporary vision session, streamed description extraction, expert prompt rewrite, and local image-metadata restoration were exercised on-device. |
| Response overwrite protection | Play mappings for clear-response, content-filter patch, status write, final merge, final apply, online history, and cold local history are installed. A real clear-response event was blocked; cold-history behavior is covered by regression tests. |
| Local API | `/v1/models`, OpenAI Chat, Responses JSON/SSE, and Anthropic Messages returned HTTP 200 through the real DeepSeek transport. Responses emitted the complete created/in-progress/item/text/done/completed lifecycle. |
| Codex compatibility | The installed Codex CLI completed a disposable custom `apply_patch` tool loop with an isolated `CODEX_HOME`; the current Codex login was not read or changed. |
| Protocol security | Gateway keys are no longer written to diagnostic logs or runtime status JSON. The explicit connection file remains a credential and must be protected. |

The API acceptance test temporarily enabled the listener and battery exemption,
then restored the original disabled state, removed the generated test key, and
removed the temporary exemption.

## Important symbol changes

Google Play 236 uses a different R8 map even where class shapes are identical.
Several successful class loads in the first probe were unrelated classes, which
is why visible controls could appear while their behavior remained inert.

| Role | Mainland 2.2.2 (`233`) | Google Play 2.2.2 (`236`) |
|---|---|---|
| Settings content / navigation / route | `u25.i` / `rm5` / `vc7` | `ph6.d` / `eo5` / `og7` |
| Chat completion request / transport | `ew0` / `s92.b` | `tx0` / `kb2.d` |
| Flow / collector / continuation | `b41` / `q03` / `uz1` | `q51` / `q23` / `j12` |
| Coroutine suspended / failure | `w02` / `fx6` | `l22` / `m07` |
| Stream event / event payload / error | `xs0` / `lv7` / `ws0` | `mu0` / `iz7` / `lu0` |
| Expert config / file feature / upload gate | `sf5` / `gf5` / `y91` | `eh5` / `sg5` / `mb1` |
| Session / message / online history | `tp` / `uo` / `pw0` | `vp` / `xo` / `ey0` |
| Clear response / response model / patch decoder | `kb7` / `mv` / `mv.i` | `df7` / `ov` / `ov.k` |
| Sidebar composable / host / drawer state | `mq5.i` / `zm2` / `bn2` | `ds5.w` / `so2` / `uo2` |
| Sidebar click / drawer value / anchors | `n51` / `cn2` / `na2` | `c71` / `vo2` / `fc2` |
| Chat route / settings route | `c81` / `vc7` | `r91` / `og7` |
| Active chat ViewModel / session / message / outcome | `za1` / `tp` / `uo` / `bu0` | `nc1` / `vp` / `xo` / `qv0` |
| Empty attachments / idle generation state | `uo7.i()` / `gp` | `ms7.k()` / `ip` |
| History request / parser context / continuation | `lj9` / `pl9` / `uz1` | `sn9` / `o6` / `j12` |
| History repository / metadata / codec | `gm8` / `am8` / `x94` | `mq8` / `gq8` / `cc4` |
| Live response/think fragments | `fo2.g/i` / `ho2.g/i` | `yp2.h/k` / `aq2.h/k` |
| Static response/think fragments | `at7` / `ht7` | `vw7` / `cx7` |

The coroutine-suspended mapping is particularly important: using the mainland
sentinel made the API collector declare an asynchronous native Flow complete
before its first event and return `502 empty_completion` even though DeepSeek
was still generating.

## Current local verification

The `1.7.3` APK builds successfully with `GOOGLE_PLAY=true`. The complete JVM
regression suite passes, including the Play appearance mapping and proactive
message protocol/history-folding tests. The heartbeat and native-history
reflection shapes were also checked directly against the cached JADX output for
the exact Play 236 APK.

The currently installed DeepSeek host on this device is mainland `233`, so the
Play APK was not installed over it. Appearance and proactive same-chat streaming
still require an end-to-end acceptance pass on an actual Play 236 host before
they can be promoted from mapping-verified to device-verified.

## Remaining caveats

- This is not a general “international version” compatibility layer. Only the
  exact target above was inspected.
- Server-side availability, rate limits, account policy, and content the server
  never sends cannot be changed by client hooks.
- A source/regression/mapping pass cannot replace a real Play-host UI and
  background-alarm acceptance pass. Keep the exact-host distinction visible in
  release notes.
- Back up important chats before enabling optional database or account tools,
  and enable only the feature you currently need.
