# Experimental Features

Deekseep groups its optional expert-image relay and local API tools on a
dedicated **Experimental Features** page in both stable builds. They stay off by
default and are kept separate because they depend on host internals, online
service behavior, or local Agent tool execution.

## One-Time Usage Note

The first attempt to enter the page opens a short, dismissible usage note.
**Back** returns to the main Deekseep page without recording an
acknowledgement. **Continue** enters immediately and stores a versioned marker,
so the note does not appear again unless its content is revised.

The note explains that results depend on the DeepSeek service, the local API key
should remain private, important content should be backed up before edits, and
client confirmation and permission controls should remain enabled.

## Expert Mode and Image Relay

The page contains the expert-feature unlock and the image-to-vision relay. The
relay can describe uploaded images in isolated temporary sessions and append
the ordered descriptions to an expert request. It also preserves image metadata
for local history rendering. Server authorization, proof-of-work, model routing
and obfuscated data classes remain outside Deekseep's control.

## Local API Service

The same page contains the local API control entry. Its own panel provides one
**Format / current format** row; selecting it opens a popup for OpenAI or
Anthropic instead of presenting two permanent protocol buttons.

The OpenAI mode implements model listing, Chat Completions and Responses,
including streaming, structured output and Agent tool-result continuation. The
Anthropic mode implements Messages, count_tokens, thinking/text/tool streaming
and Claude Code tool loops. Requests are authenticated by a separate gateway
key. Keep that key and all connection diagnostics private.

The service adds model-facing instructions that workspace creation or editing
must be performed through the client's Write/Edit/NotebookEdit/apply_patch-style
tools. A bounded repair can turn a code-only or narrated response into a real
tool call, but the client still controls sandboxing, approval and filesystem
permissions.

At the bottom of the Local API panel, **Advanced settings** provides a fixed
listener port, a persistent Cloudflare custom-domain connector, multiple saved
hostnames, transport diagnostics, and a direct-public-IP URL profile. The
Cloudflare token is AES-GCM encrypted with Android Keystore in the module app,
and the ABI-matched cloudflared process runs in the existing foreground
keepalive service. Localhost and LAN listening remain active while the public
connector runs.

Hostname-to-origin routes still belong to the user's Cloudflare account. The
app does not claim a domain merely because its name is entered locally. Direct
public-IP access likewise requires a real routable address, router forwarding,
and external HTTPS; it cannot bypass carrier CGNAT.

## Separate Help

The Experimental Features page has its own **Help & Issues** entry. It covers
format endpoints, API authentication/readiness errors, Codex setup, Claude Code
`/clear` and `/new`, delayed thinking lifecycle, and practical usage controls.
Questions specific to these features are intentionally removed from the normal
help page.

## Practical Tips

- Enable only the options you need.
- Back up important DeepSeek chats and Agent workspace files before editing.
- Keep only one Deekseep module enabled for DeepSeek.
- Retain client sandboxes, confirmation prompts and minimal tool permissions.
- If a DeepSeek update causes an issue, turn off the related option and restart
  the app.
- Keep API keys, request logs, databases and account exports private.
