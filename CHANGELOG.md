# Deekseep Changelog

## [1.8] - 2026-09-24

### What's New in Version 1.8

#### 1. Context Compaction
- **Manual context folding**: the chat page's `压缩对话` action folds the older turns of the current conversation into a summary, stores that summary, and continues in a new conversation. Summaries are editable and can be attached to any conversation.
- **Three shaping numbers**: `建议压缩线` (threshold, default 10000 characters), `保留最近轮数` (turns kept verbatim, default 6) and `摘要字数上限` (summary cap, default 8000 characters), plus a live row showing the size of the conversation you are looking at.
- **Nothing compacts automatically**: the threshold is only the line the chat-page button reports the conversation against.
- **One-shot, scoped delivery**: a stored summary is handed to the next genuine interactive send for that conversation exactly once. Heartbeat, Agent private transports, Local API calls and synthetic sends keep their own prompts untouched.

#### 2. Stored Summary Records
- **压缩记录 page**: a full-screen list of every stored summary — review, edit, attach to the current conversation, or delete.
- **Three entry points**: the `压缩记录` button in the `压缩对话` chip window, the `压缩记录` row in the `上下文压缩` settings screen, and the `压缩对话` / `压缩记录` cards in the UI Navigation Manager.
- **No-plan paths still lead somewhere**: with no conversation open, or with fewer than two messages, the notice offers the records page instead of a dead end.

#### 3. UI Polish & Layout Fixes
- **The `上下文压缩` card no longer collapses into a square window**: it is sized `min(screen width − 32dp, 460dp)` by `screen height − 96dp`, so the `压缩记录` row and both buttons stay on screen on tall displays.
- **Module app bottom-navigation labels are centred** under their glyphs.

#### 4. Account Management
- **Bulk account deletion as one transaction**: `AccountManager.removeSlots` rewrites the slots file once and `deleteUserRows` removes the matching `app_user_info` rows inside a single SQLite transaction, replacing per-account removal and per-account file writes.

#### 5. Open-Edition Build
- **Activation card and build/device information panel are no longer gated on `PROTECTED_BUILD`** in the compose launcher, so Open builds show them.

#### 6. Architecture & Extensions
- New `ChatContextCompactor` / `ChatCompactionBridge` core, wired into the chat pipeline and registered with the module config bridge as `deekseep_context_compaction.json`.
- All new copy is covered by `UiLanguageCatalog` (Chinese and English); `ContextCompactionUi.java` joins the language-catalog scan.
- New `ChatContextCompactorRegressionTest` runs with the thinking regression suite, and `module-universal/verify-compile.sh` verifies an Open source tree compiles locally.

---

## [1.7.5] - 2026-09-20

### Critical Announcement & Distribution Changes
- **Indefinite Suspension of Updates**: Due to severe abuse, unauthorized code reselling, and egregious GPL license violations by copycat projects, all public repository updates are temporarily suspended indefinitely.
- **Closed Edition Exclusivity**: The Closed edition is no longer provided, updated, or distributed through GitHub releases or repository commits. Users can join the official Telegram group ([@Deekseepapp](https://t.me/Deekseepapp)) to obtain Closed edition builds.

---

### What's New in Version 1.7.5

#### 1. Host Version Compatibility
- **DeepSeek 2.4.1 Adaptation**: Full compatibility branch implemented for Mainland China DeepSeek v2.4.1 (versionCode 257).

#### 2. Chat & Message Management
- **Batch Chat Deletion**: Multi-select mode in conversation history now supports one-tap batch deletion of selected sessions.
- **Local Quota Unlock**: Removed local chat count edit limits (bypassed the 5-turn session modification constraint).
- **Thinking Chain Code Copy**: Added a one-click copy button for reasoning/thinking code blocks to easily copy code outputs.
- **Anti-Recall Reliability & Context Preservation**: Fixed edge-case failures where recalled messages could disappear, and preserved conversation context for revoked/retracted messages.

#### 3. Security & Anti-Risk Bypass
- **Risk Control SDK Bypass**: Added runtime inline bypass for SMSDK / Shumei device risk-control detection routines.
- **Anti-Ban Protection**: Implemented defensive heuristics and randomized behaviors to reduce account risk and mitigate automatic bans.

#### 4. UI Polish & Navigation
- **Theme Color Extraction**: Dynamic host theme color palette synchronization with custom HEX accent overrides.
- **Entry Setting Renamed**: "使用原生入口" (Use Native Entry) renamed to "使用旧版入口" (Use Legacy Entry) for clarity.
- **UI Navigation Manager**: Integrated Activity and Compose route manager to navigate hidden host pages and toggle route visibility.

#### 5. Diagnostic Tools & System Logs
- **High-Frequency Raw Logging**: Added high-throughput, unmasked real-time hook trace logging with automatic multi-volume rotation.
- **Export Diagnostic Logs**: One-click export of system diagnostic logs, crash dumps, and runtime traces into a `.zip` archive.
- **Application Data Backup**: Added support for backing up and restoring user application data.

#### 6. Architecture & Framework Extensions
- **Java Plugin Framework v3**: Robust runtime Java plugin system featuring dedicated hook catalogs, ABI contracts, and lifecycle management.
- **Remote Feature Flags Extension**: Expanded runtime remote feature toggles and gray-release configuration overrides.

#### 7. Agent & MCP Integration
- **Built-in Termux Integration**: Integrated Termux runtime environment support for Agent execution (*Note: Compatibility issues may exist depending on device architecture and environment*).
- **Agent Model Context Protocol (MCP)**: Added MCP tool protocol support with streaming HTTP/SSE server connectivity and automated capability discovery.
- **Multi-Round Segmented Prompt Injection**: Split prompt injection logic into coordinated multi-turn phases for complex instruction flows.

#### 8. General Fixes
- Addressed numerous edge-case bugs, memory leaks, and UI rendering inconsistencies across various Android versions. Please explore the release for further improvements.
