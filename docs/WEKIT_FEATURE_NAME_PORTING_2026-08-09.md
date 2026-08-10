# WeKit feature-name applicability for DeepSeek

This inventory intentionally uses only WeKit feature names and category names. It does not copy or
study WeKit's implementation because the host applications and runtime contracts are different.

## Strong DeepSeek candidates

- `HideConversationListDividers` — optional cleaner conversation-list appearance.
- `CustomChatInputBarPlaceholderText` — custom composer placeholder independent of the home greeting.
- `QuickRemoveQuote` — one-tap removal when DeepSeek has an active quote/reference attachment.
- `QuotedMessageDirectJump` — jump from a quote/reference to its source message where a stable ID exists.
- `SwipeConversationOperations` — archive/delete/pin-style sidebar gestures.
- `SwipeMessageOperations` — copy/edit/regenerate-style message gestures.
- `RemoveMessageSelectionLimit` — extend the existing conversation multi-select idea to messages.
- `AutoViewOriginalMedia` — prefer original image viewing when the original URL is available.
- `NoCompressUploadedImages` — optional original-quality image upload.
- `CustomDpi` — per-host density override with restart and safe reset.
- `ForceTabletMode` — tablet/two-pane layout experiment, gated by host-version checks.
- `DisableLowAvailableStorageDetection` — bypass only the host warning, never Android storage safety.
- `PreventModuleDataDeletion` — preserve Deekseep configuration during host-side cleanup flows.

## Already represented in Deekseep

- `ApplyDialogBackgroundBlur`, `ApplyGlobalBackground` — liquid glass and background customization.
- `FeatureFlagManager`, `Experiments2` — native feature management and the experimental section.
- `CopyWeChatDebugInfo` — compatibility diagnostics, hook logs, and exportable traces.
- `MarkdownRendering` — Markdown optimization controls and message rendering tools.
- `ConversationGrouping` — chat/project category and search organization cover part of this territory.
- `RoundAvatars` — avatar/bubble appearance controls overlap, although a dedicated radius switch may
  still be useful.

## Weak or host-specific candidates

- Contacts, Moments, red-packet/payment, Mini Program, WeChat VoIP, sports-step, sticker, Pat,
  nearby-friend, wallet, and WeChat signature items have no direct DeepSeek domain equivalent.
- `PreventXposedDetection`, `SpoofEnvironment`, and signature-verification bypass names are not good
  default product features; they add fragility and security risk without improving chat workflows.

## Recommended next batch

1. Custom chat-input placeholder.
2. Swipe conversation operations.
3. Original-quality image upload.
4. Message multi-select limit management.
5. Custom DPI / tablet mode as separately warned experimental options.
