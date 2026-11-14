# Changelog

All notable changes to NOX are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/), versions follow SemVer.

## [0.1.0-alpha] — 2026-11-14

First public early-alpha. NOX is now fully open source (MIT).

### Added
- **~140 modules** across Combat, Movement, Render, DonutSMP and Misc — killaura/autototem/velocity,
  finders & ESP suite, AutoSell/AHSniper/AutoStonks/TradeChecker economy tools, staff alerts,
  panic profiles, auto-everything QoL.
- **Two switchable GUI looks** (`Interface` → ClickGUI):
  - **Panels** — floating category columns (Meteor/Anubis-style) with a top pill nav
    (Modules · HUD · Shop · Theme · Discord · search) and filled toggle rows.
  - **Hub** — launcher-style window with left category rail, search and square tiles
    with ON/OFF strips (LABY-style).
  - plus the existing Cards / Compact rows / Sidebar window layouts.
- **SettingsPopup** — separate draggable Lunar-style popup: per-module activation mode
  (Toggle/Hold), internal keybind chip, typed rows (switch, slider, pill-selector, color
  swatch, editable text fields, bind capture).
- **HUD** — auto-fits every screen and GUI scale, scoreboard-aware anchoring, corner
  collision handling, draggable anchor corners for info block and module arraylist,
  `+N more` overflow, SessionStats/TargetHUD/Spotify widgets clamped to bounds.
- **Cosmetics** — real rendered capes (NOX / Inferno / Void / animated / custom PNG upload
  from `nox/capes/`), trails, auras (incl. Flame/Void/BlackHole/Halo/Notes …), kill effects.
- **Shop** — 36 equippable cosmetic items incl. cape gallery, Discord-gated premium flow.
- **Diagnostics** — every module reports a live status (`why <module>`, `selftest`,
  hover tooltips show idle reasons and last errors).
- **Discord** — Rich Presence with real invite `discord.gg/tewtZTsZf5`, report/suggest
  buttons open the community server, config share/import via clipboard.
- **TamperGuard** — always-on jar integrity check (not a toggleable module).
- **NOX Community Bot** (`nox-community-bot/`) — verify/ticket/roles/ping panels,
  member counters, ticket workflow, systemd-ready VPS deployment (`deploy.sh`).

### Fixed
- ClickGUI left-click did nothing (buttons were swapped) — LMB toggles everywhere now.
- Zoom FOV below vanilla minimum → clean `Camera.getFov` mixin instead of options write.
- `Setting` ClassCastException crashes from wrongly-typed values → all getters coerce.
- `NoFall`/`FlyBypass` packet kicks after join/teleport → 60-tick grace + latched sending.
- Cape skin matching, animated-cape texture caching, HUD overflow at GUI scale 5.

## [Unreleased]

See `docs/roadmap.md` for what's next — contributions welcome (see CONTRIBUTING.md).
