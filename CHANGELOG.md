# Changelog

## [0.1.0-beta] — 2026-11-23

### HUD editor, smooth corners, input fixes

- **Account manager** (Alts pill): paste a Microsoft session token -> resolved
  via api.minecraftservices.com and swapped into Minecraft.user (Unsafe field
  write); offline-name alts for cracked/test servers; per-connection SOCKS5/
  SOCKS4/HTTP proxy injected into the Netty pipeline (covers join + status
  ping); accounts persist in nox/alts.json. Switch disconnects to menu.

- **Shader sky (CustomSky)**: real renderpearl GLSL pipeline replaces the vanilla
  sky — 4 raymarched/procedural modes (Gargantua black hole with lensed accretion
  disk, DeepSpace nebula, Aurora, RingPlanet), palette presets (NOX/Ember/Frost/
  Toxic/Blood), star density/nebula/band/exposure uniforms. Old fog palettes kept.
- Accent color: full HSV picker (hue strip + SV square + hex field, draggable)
  in the Style screen alongside presets.
- Backdrop contrast: Frost/Prism noticeably stronger tint + bands vs Dim; Glass
  window style no longer shows the gargantua sky (Solid=flat, Sky=gargantua,
  Glass=translucent sheen pane).
- Search: type-to-search — any printable char autofocuses the module search.
- Added HudEditorScreen (HUD button): drag every HUD element with edge/center snapping + guide lines, right-click per-element reset, live preview over the world, "GUI layout" quick-cycle pill — persisted to run/nox/hud.json (resolution-independent)
- HudLayout: normalized free positions for info/arraylist/compass/gear/totems/targethud/sessionstats/spotify/lyrics — NoxOverlay reads them live
- Fixed "pixelated" corners: Gui.vgrad now renders AA corner tiles (SmoothShapes) instead of stair-stepped insets — affects window, compass, all gradient surfaces
- HUD text: Inter everywhere, all drop shadows removed; ArrayList = flat edge-flush chips + inset accent pill, slide/fade kept
- Fixed: Interface module could be toggled off → killed ALL customization sync; now always-on + ticked unconditionally

### Customization entry, card restyle, season FX, bug fixes
- Added CustomizeScreen ("Style" footer pill): visual theme cards, Season/Backdrop/level pickers, ClickGUI layout styles, accent palette, density/radius/HUD-scale steppers, reduced-motion toggle — all live-applied via Interface settings
- Added seasonal ambient FX (Winter snow / Spring petals / Summer fireflies / Autumn leaves) inside windows — Interface "Seasonal FX" toggle, reduced-motion aware
- ShopScreen rebuilt: filter tabs, 2-col card grid, animated per-item previews (trail/aura/kill/cape), FREE/OWNED/EQUIPPED pills, equip/unequip toggle
- ModuleCard restyle: 28×28 icon chip (accent-fill when on), Active/Idle status dot, keybind chip, ⚙ affordance, category label only in search results, accent border+glow on enabled
- Sidebar: category glyphs + enabled counts; footer: 20px icon-pill toolbar; scrollbar: 4px rounded thumb w/ fade
- SettingsPopup: module icon chip in title row
- Fixed: Interface.onTick never synced season/backdrop/backdropLevel to UICustom — Dim/Frost/Prism + seasons had zero visual effect
- Fixed: outside-click while editing now commits instead of silently dropping the edit; Num-field edit zone widened (slider no longer eats the field's lower edge)
- New module: Wtap — sprint-reset W-tap on hits (client-input only, no packets)

### Look system, commerce, settings quality
- Added 4 seasonal themes (Winter/Spring/Summer/Autumn) + Interface "Season" (Auto=calendar, applied on change)
- Added "Screen backdrop" (Dim/Frost/Prism) + "Backdrop level" (Light/Medium/Strong); Prism = animated specular border + hue fringe via NvgOverlay, Frost = tint/sheen/vignette (no 2nd blur call)
- Added card stagger entrance (20ms×≤12), clickStyle cross-fade veil, sidebar sliding indicator, ArrayList 180ms slide-out, toast slide-out; AnimationSystem.seconds/approach drive all UI easing
- SettingsPopup: per-row reset ↺, slider min/max labels, str placeholder, Mode ←/→ cycle, DEL unbind, bind-collision warning, desc tooltips, in-popup search (>8 settings)
- Added ReportScreen → bridge POST (nox/reporturl.txt override, default 127.0.0.1:7734), clipboard fallback; payload audited (no secrets)
- Added PresetScreen: list/apply/rename/delete/save-as, export (NOXCFG2 gzip code ≤2k else JSON), import with diff preview + applySafe rollback
- LicenseManager: deactivate/daysLeft/activatedAt; PremiumScreen status surface + parent return; ShopScreen activate link + hover lift
- Fixed: NvgOverlay.clear() ordering wiped queued backdrop ops; "Backdrop" mode name collided with existing Col setting → renamed "Screen backdrop"; UICustom.reset() now covers all new fields; ModuleButton crash was a corrupt jar (atomic deploy already prevents)


### UI overhaul, animation system, cape system, bug hunt

### Added
- Studio ClickGUI layout (default): sidebar categories, ≤3 readable card columns,
  full descriptions, meta row (category/keybind/premium), no cramped 6-col grid.
- SettingsPopup rebuild: fixed header/footer, bounded height with scissored
  scroll body + scrollbar, drag-and-type number fields with slider, mode
  dropdowns, HSB+alpha color picker, hex input, keybind capture, Tab/Enter
  keyboard navigation, per-module Reset/Copy/Done/Cape-editor actions.
- Animations module (`module/render/Animations`): procedural pose layer —
  20-state machine (idle/walk/sprint/sneak/jump/fall/land/swim/elytra/climb/
  attack/use/bow/crossbow/shield/hurt/death/riding) with 5-tick smootherstep
  cross-fades, spring-driven head follow, sprint lean, landing squash, idle
  breathing, foot-plant offsets; first-person wind-up/follow-through item
  swing, hand bob, item-use overrides (bow/crossbow/shield/eat/trident/spear);
  optional camera roll/breathing/landing. Per-group toggles + intensity 0–1,
  spring stiffness/damping, 32-block distance cap. Supports player + 8 mob
  types (zombie, skeleton, creeper, spider/cave spider, enderman, wolf).
  PlayerAnimator/Emotecraft detection defers the player layer (log once).
- Async custom-cape pipeline (`gui/CustomCape`): precedence file > URL > first
  local PNG; bounded single-thread worker for I/O+decode (4 MiB cap, PNG
  signature + dimension validation), GPU upload only on client thread via
  generation token (stale results discarded); multi-frame capes via sidecar
  JSON frame table; LRU memory cache (4 entries) + 16-file disk cache for
  URLs with 24 h TTL, 5 s/8 s timeouts, offline fallback to disk cache.
- `physics/VerletChain`: reusable fixed-step secondary-motion chain.
- Cosmetics settings: Cape file, Cape URL, Cape distance (render cull + LOD).
- Interface: Reduced motion toggle (skips scroll/open/card animations).

### Changed
- `AnimMath.spring` now solves x'' + c·x' + k·x = 0 exactly per step (unit
  mass) instead of explicit Euler — identical results at 30 vs 240 steps,
  unconditionally stable for any dt; handles overdamped/critically-damped/
  underdamped branches and non-finite input.
- `AnimMath.cubicBezier` is now a real CSS timing curve (parameter solved by
  bisection); the parametric ordinate remains as a separate overload.
- `ClothSim`: per-step displacement cap (no explosions), dimension guards,
  `copyFrom` LOD resampling preserving drape/momentum.
- `NoxCapeLayer`: entity-id local-player match, distance cull via Cosmetics
  setting, LOD switch without pose snap, reset on player-instance change
  (respawn/dimension), both LOD grids reset on teleport.
- `SmoothShapes` renders from 3 reusable white masks tinted at blit time —
  no per-color rasterization/upload during hover/fade animation.
- `Gui.styled` caches styled Components (bounded LRU) — removes repeated
  Component allocation in the text hot path.
- `Setting.Num.get()` clamps to [min,max] + finite check; `Setting.reset()`
  restores defaults.
- ModuleCard: wider tile (210), two-line clipped description, bottom-right
  toggle, settings affordance via 3-dot indicator, meta row.
- `cape` debug cmd reports status without forcing a reload (`cape reload`).

### Fixed
- ESP `projectBox` near-range flicker: boxes no longer vanish when a corner
  crosses behind the camera — edges are clipped against the near plane
  (w is linear in world space), rect covers the visible portion only.
- `noxctl shot` raced the async PNG encode — produced invalid files; now
  waits for a complete PNG (signature + IEND) written after the command.
- Settings popup could grow beyond the screen; rows now live in a scissored
  scrollable viewport.
- MoveGuard (carried from prior fix): zero-extra-packet model verified —
  `inj=0 rep=166` over 150 s+ Grim soak, no Timer flags, no disconnect.

### Verified live (MC 26.3 dev client + DonutSMP test server)
- New ClickGUI + SettingsPopup screenshots; color/num/mode edits round-trip.
- Custom cape: file→worker→GPU→rendered on player (third person, cloth drape).
- Animations: 1–2 animators active, 24–51 µs/tick on dev hardware.
- nox_doctor: 0 errors, 0 warnings.

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
