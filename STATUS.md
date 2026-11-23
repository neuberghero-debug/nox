# STATUS Matrix

Legend: ✅ done & compiling · ⚠️ partial/likely needs tuning · ❌ missing · 🔒 premium

## Phase 1 — Core
| # | Feature | Status |
|---|---|---|
| 1.1 | GUI | ✅ floating window, drag/resize/tabs/search/settings/keybinds |
| 1.2 | Config | ✅ JSON + per-server presets + share/import |
| 1.3 | Module registry | ✅ 5 categories, enable/disable, settings, keybinds |
| 1.4 | SelfDestruct | ✅ armed two-stage wipe (RAM+disk+jar) |

## Phase 2 — DonutSMP
| # | Feature | Status |
|---|---|---|
| 2.1 | AutoSell | ✅ |
| 2.2 | AutoEat | ✅ |
| 2.3 | FastPlace | ✅ |
| 2.4 | AutoTool | ✅ |
| 2.5 | StorageESP | ✅ |
| 2.6 | PlayerESP | ✅ |
| 2.7 | Fullbright | ✅ |
| 2.8 | AH Sniper 🔒 | ✅ |
| 2.9 | Tunnel Base Finder 🔒 | ✅ |
| 2.10 | RTP Base Finder 🔒 | ✅ |
| 2.11 | Chunk Finder | ✅ |
| 2.12 | Amethyst Stash Finder | ✅ |
| 2.13 | AutoCrystal 🔒 | ✅ |
| 2.14 | Anchor Macro 🔒 | ✅ |
| 2.15 | Anti-ESP-Bypass 🔒 | ⚠️ heuristic |
| 2.16 | Anti-Vanish 🔒 | ✅ |
| 2.17 | Fly-Bypass 🔒 | ⚠️ needs live tuning |
| 2.18 | Auto Totem Order 🔒 | ✅ |

## Phase 3 — PvP & Movement
| # | Feature | Status |
|---|---|---|
| 3.1 | KillAura legit | ✅ |
| 3.2 | AutoTotem | ✅ |
| 3.3 | Velocity | ✅ packet mixin |
| 3.4 | NoFall | ✅ |
| 3.5 | ElytraGlide/Swap | ✅ |
| 3.6 | AntiTrap | ✅ |

## Phase 4 — Discord
| # | Feature | Status |
|---|---|---|
| 4.1 | Bot base | ✅ discord.py, slash cmds, .env |
| 4.2 | Roles | ✅ Premium/Donator/Staff/Tester |
| 4.3 | Tickets | ✅ private channels + transcripts |
| 4.4 | Product channels | ✅ /post branded embeds |
| 4.5 | Embeds | ✅ template system, #FF6EC7 |
| 4.6 | Token mgmt | ✅ env only — rotate the leaked one |
| 4.7 | Moderation | ✅ spam/invite/caps + timeouts |
| 4.8 | License API | ✅ FastAPI /api/verify + /api/issue |

## Phase 5 — Paid & Cosmetics
| # | Feature | Status |
|---|---|---|
| 5.1 | Premium gate | ✅ license.key + API + PremiumScreen |
| 5.2 | Cosmetics | ⚠️ particle trails done, cape renderer offen |
| 5.3 | Donator ranks | ⚠️ role system done, perks doc only |
| 5.4 | In-client shop | ✅ footer button → donate URL |
| 5.5 | Donate channel | ✅ /post donate embed |
| 5.6 | Transparency | ✅ pricing text in embed |

## Phase 6 — Bypasses & Stealth
| # | Feature | Status |
|---|---|---|
| 6.1 | Anti-ESP | ⚠️ |
| 6.2 | Anti-Vanish | ✅ |
| 6.3 | Fly bypass | ⚠️ |
| 6.4 | Anti-trap | ✅ |
| 6.5 | OpSec | ⚠️ in-client only |
| 6.6 | Anti-AFK | ✅ |
| 6.7 | GCD rotation fix | ✅ Legitifier quantizes deltas to mouse quantum |
| 6.8 | Silent rotations | ✅ look packets w/o moving view (KillAura mode) |
| 6.9 | SprintReset / hit-select | ✅ attack mixin + W pulse |
| 6.10 | Triggerbot | ✅ crosshair-only attacks |
| 6.11 | NoSlow | ✅ item slowdown compensation |
| 6.12 | InventoryMove | ✅ moves with screens open |
| 6.13 | Blink (lag-switch) 🔒 | ✅ send-buffer mixin, hold/pulse |
| 6.14 | ShieldBreak / AutoMace 🔒 / AutoPearl / AutoArmor | ✅ |
| 6.15 | AutoSprint / StepAssist | ✅ |
| 6.16 | ModSpoofer (brand/payload fingerprint) | ✅ vanilla brand + optional full strip |
| 6.17 | AutoCommander / AutoHarvest / SellWand / ArmorAlert | ✅ |
| 6.18 | CustomSky (6 premades + custom RGB) | ✅ fog-environment mixin |
| 6.19 | HudWidgets (watermark/coords/fps/ping/arraylist) | ✅ |

## Extra (self-proposed DSMP features, top 3 built)
| Feature | Status | Aufwand/Nutzen |
|---|---|---|
| StashLogger | ✅ | low/high — persists every find to stashes.json |
| CombatLog | ✅ | low/high — auto-disconnect at low HP |
| AutoReconnect | ✅ | low/med — rejoin with delay |
| Bounty tracker | ❌ suggested | med/med — polls /bounty for target coords |
| Death waypoint | ❌ suggested | low/med — marks death pos |
| Auction price history | ❌ suggested | high/med — needs a DB + bot side |
| PearlQueue / pearl cooldown HUD | ❌ suggested | low/med |
| Warp-book GUI | ❌ suggested | med/med |
| Seed mapper | ❌ suggested | high/low — legally risky vs server rules |

## Build
`gradle build` → `build/libs/nox-0.1.0.jar` ✅ verified compiling (MC 26.3, loom 1.20.0-alpha.31, Java 25)

## Runtime-Fixes (26.3 live)

| Fix | Status |
|---|---|
| ButtonMixin @Shadow auf geerbte Methoden → Cast | ✅ |
| BlinkMixin non-private static → entfernt, direktes send | ✅ |
| LoadingOverlay: onFinish/fadeOut/setOverlay selbst gefahren (HEAD-Cancel killte Abschluss) | ✅ |
| DynamicTexture statt SimpleTexture (TextureView vor Reload) | ✅ |
| Fullbright: gamma-Clamp → NIGHT_VISION-Mixin | ✅ |
| Modul-Exceptions: silent → Stacktrace + §c-Chat | ✅ |
| Invite-Popup: Parent hinterm Modal (war Blackscreen-Look) | ✅ |

## DonutSMP intel suite (session: base-finding + Grim-bypass)

| Feature | Status |
|---|---|
| Packet bus: GamePacketMixin → Module.onPacket (20 inbound handler) | ✅ |
| BaseIntel/BaseSigs: shared hit store, chunk scoring, overlay marks | ✅ |
| SoundBaseFinder 🔒: named/entity sounds, level+block events | ✅ |
| BlockLeakFinder: updates, sections, destruction, transient, explosion, particles | ✅ |
| EntityLeakFinder: spawn/gear/take-item/damage/passenger leaks | ✅ |
| ChunkBELeak 🔒: block-entity list in chunk packets (anti-xray blind spot) | ✅ |
| RoomFinder: sealed air pockets below Y=0 | ✅ |
| EventTriangulator: global level events → region wedges | ✅ |
| XrayRadar 🔒: fake-ore fields + ore→stone reveal flips = hidden zones | ✅ |
| RevealProbe 🔒: START+ABORT_DESTROY de-obfuscation | ⚠️ active packets, risky — default OFF |
| DonutGuard: Grim-strict master switch (default ON) | ✅ compile-verified |
| AC hardening: silent-rot placements (Interact.placeOn), reach clamps, Speed→real bhop, Flight/NoFall/AntiVoid/NoSlow/HighJump blocked on strict, Velocity→JumpReset, FastPlace/FastBreak/Nuker/KillAura caps | ✅ compile-verified, live-Tuning offen |
| Stealth: ModSpoofer default-on, brand immer "vanilla" | ✅ |
