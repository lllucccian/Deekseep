# Deekseep 1.8

Deekseep 1.8 is the open-source release of Deekseep. It adds manual context
compaction with stored-summary records, and a set of layout and account-management
fixes on top of 1.7.5.

## Downloads

- No prebuilt APK is carried in this repository. `bash scripts/build-all.sh` builds both
  channels, leaving `module-universal/ds-probe-universal.apk` and
  `module-universal/ds-probe-universal-google-play.apk`. It then runs the feature-parity
  check, three regression suites and further release guards before copying the two APKs
  to `dist/deekseep-universal-v1.8.apk` and
  `dist/deekseep-google-play-universal-v1.8.apk`, and writes `SHA256SUMS.txt` only after
  the remaining guards pass. Because the `module-universal/` products are built first,
  they are what you still have when a guard stops the script.
- See [docs/BUILDING.md](../docs/BUILDING.md) for requirements (JDK 17, Bash, Android
  SDK Platform 35+, Build Tools `aapt2`/`d8`/`zipalign`/`apksigner`).

Host support is unchanged from 1.7.5: Mainland China DeepSeek 2.4.1 (versionCode 257),
2.3.6 (249), 2.3.4 (245/246), 2.3.0 (237) and 2.2.x, entering through the traditional
Xposed entry covering API 82–102.

## Highlights & What's New

### 1. Context Compaction

- **Manual fold, never automatic**: the chat page's `压缩对话` action folds the older
  turns of the conversation in front of you into a summary, persists that summary, and
  continues in a new conversation.
- **Three shaping numbers** in module settings — `建议压缩线` (threshold, default 30000
  characters, range 1000–400000), `保留最近轮数` (turns kept verbatim, default 6, range
  1–40) and `摘要字数上限` (summary cap, default 2000 characters, range 400–8000). Nothing
  fires on its own: the threshold is only the line the chat-page button reports the
  current conversation against.
- **The chip is optional and movable**: `在聊天页显示压缩按钮` decides whether the
  `压缩对话` chip appears in the chat page, and a dragged chip position is remembered.
- **Scoped, one-shot delivery**: a stored summary rides the next genuine interactive
  send for that conversation exactly once. Heartbeat, Agent private transports, Local
  API calls and synthetic sends keep their own prompts untouched, because rewriting
  them would change the semantics of the caller.

### 2. Stored Summary Records

- **Full-screen records page** listing every stored summary: review, edit, attach it to
  the current conversation, or delete it.
- **Three ways in**: the `压缩记录` button in the `压缩对话` chip window, the
  `压缩记录` row in the `上下文压缩` settings screen, and the `压缩对话` / `压缩记录`
  cards in the UI Navigation Manager.
- **The no-plan paths stay useful**: when no conversation is open, or when the open
  conversation has fewer than two messages, the notice still offers the records page.

### 3. UI Polish & Layout Fixes

- **The `上下文压缩` card no longer collapses into a square window.** It is sized
  `min(screen width − 32dp, 460dp)` by `screen height − 96dp`, so the `压缩记录` row and
  both buttons stay on screen on tall displays where a square window pushed them out of
  view.
- **Module app bottom-navigation labels are centred** under their glyphs instead of
  hugging the left edge of each cell.

### 4. Account Management

- **Multi-select account deletion runs as one transaction.** `AccountManager.removeSlots`
  rewrites the slots file once and `deleteUserRows` deletes the matching `app_user_info`
  rows inside a single SQLite transaction, replacing the previous per-account removal and
  per-account file write.

### 5. Open-Edition Build

- **The compose launcher's activation card and build/device information panel are no
  longer gated on `PROTECTED_BUILD`**, so Open builds show them again.

### 6. Architecture & Extensions

- New `ChatContextCompactor` (settings, storage, fold planning) and `ChatCompactionBridge`
  (host conversation and usage access) core, wired into the chat pipeline and exposed to
  the config bridge as `deekseep_context_compaction.json`.
- All new copy is covered by `UiLanguageCatalog` in Chinese and English, and
  `ContextCompactionUi.java` now joins the language-catalog scan.
- New `ChatContextCompactorRegressionTest` runs as part of the thinking regression suite,
  and `module-universal/verify-compile.sh` compiles an Open source tree locally without
  packaging.
