# OBS + TikTok (<1000 Follower) — Setup

## 1. Stream-Key ohne 1000 Follower

TikTok gibt OBS-Stream-Keys nur über **Creator Networks / LIVE Agencies**
frei — Follower-Zahl egal, Beitritt kostenlos. Ablauf:

1. Agency beitreten (verified Liste: toktutorials.com/list-of-agencies —
   z.B. Atomik (US/CA+EU), TALENT-ME, YOYOY). Beitritt = TikTok-Handle angeben,
   meist <24h Review über deren Discord.
2. Agency schaltet **LIVE-Zugang + Stream-Key** auf `jbbbbbrk` frei.
3. Pro Stream: `livecenter.tiktok.com/producer` → Titel/Kategorie →
   **Server-URL + Key generieren** (Key ist pro Session frisch!).
4. OBS → Einstellungen → Stream: Service „Benutzerdefiniert“, Server + Key
   einfügen. (Im Profil `NOX-TikTok` vorbereitet: `service.json` = rtmp_custom.)

Alternative Route: **Streamlabs TikTok-LIVE-Access** anfragen
(support.streamlabs.com — funktioniert ebenfalls <1k) → danach liefert
livecenter oder das StreamlabsTikTokStreamKeyGenerator-Tool die RTMP-Daten.

> Hinweis: TikTok verlangt bei Third-Party-Streaming überwiegend
> Gaming-/Software-Content — der NOX-Dev-Stream (MC-Client) passt da rein.

## 2. OBS einrichten (einmalig, ~1 Minute)

`tools/stream_start.sh` hat Szene + Profil bereits erzeugt
(`tools/stream/make_scene.py`). In OBS:

1. **Profile → NOX-TikTok** · **Szenensammlung → NOX-TikTok**
2. Szene „Dev Stream“ — zwei Quellen brauchen einmalig den Fenster-Pick
   (Wayland/Portal, wird danach im RestoreToken gespeichert):
   - `DEV — Devin Desktop` → Devin-Desktop-/IDE-/Terminal-Fenster wählen
   - `GAME — Minecraft` → Minecraft-Fenster wählen
3. `NOX-Overlay` = fertige Browser-Quelle (topmost) — Frames, Chat,
   Vote-Board, Alerts, DC/GitHub-Chips. Nicht anfassen.

## 3. Layout (1080x1920, fix verdrahtet)

| Zone | Bereich | Inhalt |
|------|---------|--------|
| Header | 0–110 | NOX · DEV STREAM · @jbbbbbrk · Live-Status |
| DEV | 110–702 | **Devin/IDE-Fenster** (größte Fläche — dev ist der Content) |
| GAME | 702–1182 | Minecraft (Live-Test des Clients) |
| VOTE | 1182–1472 | Top-5 voted Fixes/Features (features.json, Chat `1`–`8`) |
| CHAT | 1472–1844 | TikTok-Chat, farbcodiert (Gift= pink, Sub=gold, Meta=cyan) |
| LINKS | 1844–1920 | `discord.gg/tewtZTsZf5` · `github.com/neuberghero-debug/nox` |

- `!discord`/`!dc` → DC-Chip flasht · `!github`/`!repo` → GitHub-Chip flasht
- Gift → Center-Alert-Pop · Vote-Board persistiert pro Stream (localStorage)
- Features editieren: `tools/stream/overlay/features.json` (hot-reload)

## 4. Audio

Desktop-Audio + Mic auf `default` (Pulse/PipeWire-Default) — in den
Audio-Mixer-Einstellungen ggf. konkrete Devices wählen.

## 5. Multistream (optional)

Aitum Multistream ist installiert — YouTube/Twitch parallel möglich
(Ziel in den Plugin-Einstellungen ergänzen).
