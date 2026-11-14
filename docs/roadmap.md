# NOX roadmap

## LyricsSky (lyrics on the title sky)
- `media/LyricsService`: fetch synced LRC from lrclib.net (`/api/get?artist_name=&track_name=`),
  keyed by MediaService artist+title; parse `[mm:ss.xx]` lines → list<(t, text)>, cache in `nox/lyrics/`.
- Render: TitleScreenMixin after AnimatedSky — current line centered big + next line dimmed below,
  karaoke-style accent fill by position/progress. Toggle: HudWidgets setting `Lyrics on sky`.
- Cross-platform already covered via MediaService providers.

## Voice chat (design)
- Phase 1: proximity-ish PTT in the same server — UDP relay through a tiny NOX voice server
  (opus codec via concentus jar, per-server channels derived from server ip hash + secret).
- Phase 2: Discord-bridge — bot hosts voice channels, client streams mic → relay → bot → discord.
- Anti-abuse: only for licensed users, per-module toggle, PTT keybind setting.

## Feature ideas (Krypton/Anubis tier)
- AH sniper filters: max price, min profit margin, item whitelist/blacklist, snipe sound
- AutoStonks: tracks buy/sell prices, margin HUD widget
- DropPartyAlert: chat-parse "Drop party" → ping + auto-run to spawn
- TradeChecker: scans trade window items vs AH average, flags overpay
- DeathRecap: last damage sources + killer on death screen
- SessionStats HUD: money earned/hr, kills, deaths, playtime
- WaypointShare: post coords to discord via webhook, click-to-import links
- BaseFinder scoring: weight chunk flags by player-count proximity to spawn
- Cosmetics: cape gallery + animated emblems synced via bot
- PanicProfiles: one-key "full legit" preset swap
