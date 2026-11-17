# OFFEN.md — Blocker & externe Abhängigkeiten

## Braucht externe Infra (lokal nicht abschließbar)
- **M1/M3/M4 TikTok/Twitch**: Python-Sidecar (TikTokLive) + WS-Relay; Votes brauchen OAuth-Token des Streamers. → lokale Skelette baubar, Live-Test erst mit Account.
- **M8 Friend Sync**: FastAPI-Backend + JWT + HTTPS-Hosting — Server-Code schreibbar, Deploy extern.
- **M18 Backend**: `nox-license-server` läuft lokal via uvicorn; produktives Hosting + `NOX_LICENSE_API` Deployment steht aus.
- **M19 Update-CDN**: `NOX_UPDATE_URL` Manifest-Hosting nötig.
- **M21 E2E-Chat**: Relay-Server (kein Klartext) — Code baubar, Betrieb extern.
- **M27 Analytics**: Dashboard + Admin-Auth + Datenquelle aus Backend-Telemetrie.
- **M28 Discord-Roadmap**: `nox-community-bot` ausbauen; braucht laufende Bot-Instanz.

## Technisch nicht sinnvoll/umsetzbar im Client
- **M2 Highlight-Reel**: kein H.264-Encoder in MC-Runtime. Optionen: `ffmpeg` als externer Prozess wenn installiert (PATH-Check), sonst nur Marker+Replay-Export statt Video. [OFFEN: ffmpeg auf Zielsystemen?]
- **M5 OCR**: Tesseract/OpenCV nicht im Classpath — Regex-Parsing für "X: n Y: n Z: n"-Texte umsetzbar, echte Screenshot-OCR [OFFEN].
- **M30.10 Anti-VM**: umstritten — Premium-Aktivierung in VMs blocken frustriert legitime Nutzer; als opt-in Server-Flag empfohlen statt Client-Default.
- **M30.9 Build-per-User**: braucht Build-Farm/CI — Infrastruktur-Entscheidung, kein Code-Problem.

## Code-Blocker / Verifikation ausstehend
- `FIXES.md` P0/P1: Auction-SystemChat-Hook, Scoreboard `getPlayersTeam`, SelfTag stale attachment, HudEditor Nudge-Inversion + stale rects — geplant, nicht umgesetzt (Scope-Entscheidung des Users).
- GUI/HUD-Finalisierung: NoxClickGui (A) vs NoxWindowScreen (legacy) — Keybind-Migration sobald A fertig.
- Merge-Check `gui/ds/DS` vs `gui/UICustom`: Doppel-Token-Quelle vereinheitlichen.
- `Session A` Module 11/12/13 gerade gelandet (TamperWarner/CrashReporter) — Runtime-Test auf Testserver ausstehend.
- `devloop.sh` + `nox_doctor.py` Lauf nach Merge-Stabilität.
