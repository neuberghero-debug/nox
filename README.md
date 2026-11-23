# NOX

Fabric client for **Minecraft 26.3** (unobfuscated era — Mojang names, no mappings) built for DonutSMP.
Fully open source (GPL-3.0) — switchable ClickGUI looks (Panels / Hub / Cards / Sidebar),
per-server configs, Discord integration, cosmetics & shop, premium module gate.

**Community:** https://discord.gg/tewtZTsZf5 · **Changelog:** [CHANGELOG.md](CHANGELOG.md) ·
**Contribute:** [CONTRIBUTING.md](CONTRIBUTING.md) — devs & designers wanted!

## Build
    gradle build           # → build/libs/nox-0.9.0-beta.jar  (drop in mods/)
    gradle runClient       # dev instance

Requires Gradle ≥ 9.9 and Java 25.

## Features

### FREE
| Module | What it does |
|---|---|
| NoxWindow GUI | floating, draggable, resizable, tabbed, searchable — RShift |
| Config system | JSON, per-server presets, NOXCFG: share strings |
| AutoSell | /sell whitelist + /sellall blacklist, human pacing |
| AutoEat | best-food picker, gapple priority |
| FastPlace | no right-click delay, CPS-capped |
| AutoTool | best tool per block, swaps back |
| StorageESP | chests/shulkers/barrels/furnaces through walls |
| PlayerESP | box + name + distance + health |
| ESP / Tracers | generic entity ESP + tracer lines |
| Fullbright | gamma, no night-vision effect |
| ChunkFinder / SusChunkFinder / AmethystBaseFinder / ArtificialLightFinder | base hunting |
| StaffNotifier | staff join alarm + auto-panic |
| AutoTP | queued /tpa,/rtp,/warp pacing |
| KillAura (Legit) | smoothed aim, raycast gate, CPS jitter |
| AutoTotem | offhand restock |
| Criticals | packet crits |
| Velocity | % knockback (packet mixin) |
| Speed / Flight / NoFall / ElytraGlide | movement |
| TamperWarning | unremovable jar-integrity check (anti account-scam) |
| GhostMode / OpSec | one-click legit + streamer mode |
| SelfDestruct | removes NOX from RAM + disk |
| DiscordRPC | rich presence via raw IPC |
| AntiAFK / CombatLog / AutoReconnect | QoL |
| StashLogger | finds persist to run/nox/stashes.json |
| TamperWarning / SelfDestruct / Report+Suggest buttons | safety |

### PREMIUM 🔒 (license-gated)
| Module | What it does |
|---|---|
| AHSniper | buys underpriced AH items, webhook ping |
| TunnelBaseFinder | auto-dig + base-signature detection |
| RTPBaseFinder | /rtp loop + sus-chunk scoring |
| AntiESPBypass | detects ores/loot the server's anti-ESP hides |
| AntiVanish | flags players not in tab list |
| FlyBypass | packet fly, ground-spoof |
| AutoCrystal | place + detonate, human pacing |
| AnchorMacro | place → charge → detonate |
| AutoTotemOrder | buys cheap totems, profit tracking |
| AntiTrap | escapes obsidian/cobweb cages |

License: `run/nox/license.key` → verified against NOX license API (Discord-role backed, 7-day offline grace).

## Discord
- Every menu/loading screen: subtle accent line + `discord.gg/tewtZTsZf5` footer (NOX-themed, re-skinned buttons)
- One-time invite popup on first launch
- Community bot (`nox-community-bot/`): verify/tickets/roles panels, counters — VPS/systemd-ready
- License service (`nox-bot/`): FastAPI license API, key minting

## Donate
Premium (all DSMP modules) & Donator (cosmetics) — transparent pricing in the Discord
shop channel. See #refund-policy.

## License
GPL-3.0 — see [LICENSE](LICENSE). Forks and derivatives must stay open source under the same license.

## Structure
    dev.noxclient
      api/ config/ discord/ gui/ mixin/ module/ premium/ setting/ util/
    nox-bot/        discord bot + FastAPI license service
    addon-template/ example addon (nox:addon entrypoint)
