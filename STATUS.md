# STATUS.md — Master-Upgrade Stand

Stand: 2026-11-16 15:15. Zwei Sessions parallel (siehe COORD.md); Session B liefert M20/26/29 + M1/3/4/5/6/9/10/22/30.2-Skelett.
Legende: ✅ fertig · 🟡 teilweise/in Arbeit · 🔶 Code liegt vor, Backend/Deploy extern · ❌ offen

| # | Modul | Status | Wo |
|---|-------|--------|----|
| 1 | TikTok Live Bridge | ✅ | `stream/TikTokBridge` (WS-Client, exp-backoff), `stream/RuleEngine` (trigger|action, testMode default AN, 5/s rate, cmd-whitelist), `tools/tiktok_sidecar.py`, `module/misc/TikTokLive` (premium) |
| 2 | Highlight Reel + Kill-Cam | 🔶 | ffmpeg/Encoder [OFFEN]; Marker/Metadaten via `replay/ReplayRecorder.meta()` |
| 3 | Viewer Votes | ✅ | `stream/ViewerVotes` (1-5 Optionen, dedupe, donator-Gewichtung, Deadline-Cb) — TikTok-Pfad angebunden; Twitch IRC [OFFEN] |
| 4 | Gift-Combo-Ketten | ✅ | `stream/GiftCombos` (30s-Fenster, T1-T4, HUD-live, Session-Top) |
| 5 | Screenshot Coordinate Finder | 🟡 | `module/misc/CoordFinder` (3 Regex-Varianten, Bounds-Check, Clipboard→Waypoint-Toast); OCR [OFFEN] |
| 6 | PvP-Coach | ✅ | `module/misc/PvpCoach` (Regelwerk: Reach/Accuracy/Combo/Outcome → Drill-Tipps; premium) |
| 7 | Stash Organizer | 🟡 | `util/StashLogger` + `module/donutsmp/StashLogger` Basis; Kategorisierung offen |
| 8 | Cross-Client Friend Sync | 🔶 | Backend nötig; Friends-Modul lokal vorhanden |
| 9 | Skin Changer | ✅ | `module/misc/SkinChanger` (`nox/skins/*.png`, cape-Slot, applyToOthers) |
| 10 | Emotes | ✅ | `module/misc/Emotes` (20 Partikel-Presets, Cooldown, Sounds) |
| 11 | Tamper Warner | ✅ | `util/TamperGuard` + `module/misc/TamperWarner` + `gui/TamperScreen` |
| 12 | Auto Crash Report | ✅ | `util/CrashReporter` (+init/tick in NoxClient) |
| 13 | Multi-Account | ✅ | `alts/AltManager`+`AltProxy`, `gui/AltManagerScreen` |
| 14 | Design-System + Comp-Lib | ✅ | `gui/ds/DS`, `gui/comp/{Buttons,Cards,Comp,Icons,Inputs,Overlays}` |
| 15 | ClickGUI-Rebuild | ✅ | `gui/NoxClickGui` (comp-lib); `NoxWindowScreen` = legacy |
| 16 | HUD + Toast + Sounds | ✅ | `gui/Toasts`, `gui/UiSounds`, `assets/noxclient/sounds/` |
| 17 | Themes + Micro-Anim + A11y | 🟡 | `gui/theme/Theme`, `UICustom`; A11y-Toggles offen |
| 18 | License Hardening | ✅ | `premium/LicenseManager`+`Hwid`, `nox-license-server/` |
| 19 | Signierte Auto-Updates | ✅ | `update/{UpdateManager,Apply}`, `tools/sign_update.py` |
| 20 | Telemetrie (opt-in) | ✅ | `telemetry/{Telemetry,TelemetrySanitizer}`, `gui/PrivacyScreen` |
| 21 | Client-Chat E2E | 🔶 | Relay-Server + UI fehlen |
| 22 | Replay (.donutreplay) | 🟡 | `replay/ReplayRecorder` (Ringbuffer 10min@4Hz, gzip, DNRP-Format, Event-Frames) + `module/misc/ReplayMod`; Playback-Screen [OFFEN] |
| 23 | Cosmetics-Werkstatt | 🟡 | `cosmetics/{CosmeticLayer,GlbModel}`, `gui/CapeEditorScreen` |
| 24 | Config Migration | ✅ | `config/ConfigMigrator` (v0→v3 + .bak.vN) |
| 25 | Alle GUIs redesigned | 🟡 | NoxClickGui/Shop/HudEditor gemacht; Inventar/Chat-Reskins offen |
| 26 | Stats-System | 🟡 | `stats/{Stats,StatsStore,StatsScreen}` Basis ✅; Live-Charts/Modes/Export offen |
| 27 | Analytics-Dashboard | 🔶 | Server-Code/Env fehlt |
| 28 | Roadmap in Discord | ❌ | `nox-community-bot` existiert als Anker |
| 29 | Security Audit | ✅ | `module/misc/SecurityAudit` via `ExtraModules` |
| 30 | Anti-Crack gesamt | 🟡 | 30.1 `native/`+`ncore/NativeCore` ✅; 30.2 `premium/Distributed` (8 verteilte Checks → `Module.setEnabled`-Gate) ✅; 30.3-30.13 teilweise (NativeCore.antiDebug vorhanden; Fail-Safe/Watermark/Build-Salt [OFFEN]) |

## Zusatz-Features (außerhalb Prompt, diese Session/früher)
- `module/donutsmp/{Auction,OreSim,SoundFind,TunnelBaseFinder,RTPBaseFinder,...}`
- `module/render/{Scoreboard,SelfTag,Tracers,TwitchChat,StreamerMode}`
- `gui/{HudEditorScreen,HudLayout,ModuleIcons,Favorites,SeasonFx,PresetScreen,ReportScreen}`
- `media/{MediaService,TwitchService}` (Spotify-HUD + Twitch-Basis)

## Verifikation
- `gradle compileJava` ✅ (16.11. 14:50, nur pre-existing Warnungen)
- Devloop/Deploy: ausstehend bis Batch-Merge stabil
