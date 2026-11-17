# NOX streaming features (Session B modules)

## Full stream environment — one command
`tools/stream_start.sh [tiktok_user]` (default `jbbbbbrk`) brings up everything:

- **sidecar** `tools/tiktok_sidecar.py` — TikTokLive → normalized JSON on
  `ws://127.0.0.1:8890`; auto-retry every 20s so it can run before the live
  starts and rejoins after disconnects.
- **OBS overlay** `tools/stream/overlay/index.html` on `http://127.0.0.1:8892`
  — two layouts: `?layout=default` (banner/task/chat/stats) and
  `?layout=split` (1080x1920: DEV | GAME capture holes, vote board, chat,
  discord+github chips). Vote board = ranked fixes/features from
  `tools/stream/overlay/features.json`, chat `1`..`N` votes (one per user,
  persisted via localStorage). `!discord`/`!dc`/`!github` flash the chips.
  Query params: `?user= ?task= ?discord= ?github= ?accent= ?chat= ?ws=
  ?board= ?features=`.
- **OBS scene collection + profile** — `tools/stream/make_scene.py` writes
  `NOX-TikTok` (1080x1920, overlay browser source + 2 PipeWire window
  captures + audio). One-time: pick DEV + GAME windows in the portal
  dialog. TikTok stream key (<1000 followers → agency/creator network):
  `tools/stream/OBS_SETUP.md`.
- **profile** `tools/stream/apply_preset.py` merges stream modules into every
  `nox/presets/*.json` (never clobbers other modules) + writes
  `nox/discord.txt`. Premium license lives in `nox/license.key` (TikTokLive is
  premium-gated).
- `tools/stream_stop.sh` tears it down. Logs in `run/stream/`.

## In-game (client side)
- `stream/TikTokBridge` — ws client, exp-backoff reconnect.
- `stream/StreamChat` — ring buffer of rendered events + stats; drawn by
  `NoxOverlay.drawStreamChat`: pink-accent card (live dot, @user, event
  count, kind-colored lines, active-vote bar, Discord strip via
  `DiscordLink.shortLabel()`). Draggable in HUD editor ("streamchat").
- `stream/RuleEngine` — triggers: `gift= coins>= likes>= type= comment~ user=`.
  Actions: `jump drop use swap:N look sound title: command:` (whitelisted)
  **+ `module:<name>` (toggle any module), `theme` (cycle), `vote:<q>`
  (viewer poll), `discord` (invite toast).** Game actions are queued to the
  client tick (`pump()` from `TikTokLive.onTick`) — ws thread never touches
  `mc.player`. 5 actions/s global limit, 3s per-rule cooldown, testMode
  default ON (stream preset arms it OFF — rules are client-side safe).
- `stream/ViewerVotes` — `1`..`5` / `!vote n`, donator weighting, countdown,
  winner callback (rule `vote:` starts one).
- `stream/GiftCombos` — 30s chain window, T1–T4 tiers, HUD readout.
- Module `TikTokLive` settings: user, testMode, combos, votes, rules,
  Stream chat HUD, Chat lines, Discord strip, Chat X/Y (0 = auto above hotbar).

## Stream rules preset (dev stream)
```
type=FOLLOW|title:Welcome %u!      type=SUBSCRIBE|title:%u subscribed!
gift=Rose|sound                    gift=Heart Me|jump
gift=Doughnut|theme                coins>=100|look
coins>=1000|module:Animations      comment~^!discord\b|discord
comment~^!dc\b|discord             comment~^!theme\b|theme
comment~^!vote\b|vote:Chat waehlt — 1 oder 2
```

## Highlight reel (Modul 2)
See `docs/REPLAY.md` — ReplayRecorder ring buffer + HighlightMarks + MP4 export
+ Discord webhook upload (`/nox replay export`).

## Twitch
IRC integration via the same RuleEngine surface; `!` commands map to viewer
votes. `media/TwitchService` + `module/render/TwitchChat` for the IRC chat card.

## Safety notes
- Sidecar is localhost-only; cookies/credentials never reach the client.
- `StreamerMode` masks name/coords/server-IP on stream (preset enables it).
- TikTokLive premium-gated → `nox/license.key` must verify.
