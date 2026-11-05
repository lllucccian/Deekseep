# Deekseep Changelog

## [1.7.5] - 2026-09-20

### Critical Announcement & Distribution Changes
- **Indefinite Suspension of Updates**: Due to severe abuse, unauthorized code reselling, and egregious GPL license violations by copycat projects, all public repository updates are temporarily suspended indefinitely.
- **Closed Edition Exclusivity**: The Closed edition is no longer provided, updated, or distributed through GitHub releases or repository commits. Users can join the official Telegram group ([Join link](https://t.me/+umw24KbNl3g4NzVl)) to obtain Closed edition builds.

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
