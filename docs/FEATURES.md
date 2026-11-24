# NOX feature catalog

Complete module list by category. Premium modules are gated by `LicenseManager.assertPremium()`.

## Render
- **Cosmetics** — trails (13 styles), particle auras (15), GLB hats/pets/mesh-auras, capes (bundled/custom PNG/URL, cloth physics). `.cosmetic` export/import + marketplace (`/nox cosmetic`).
- **StorageESP** — container highlight + stash detection (feeds `StashIndex`).
- OreSim, BlockESP, mob/player ESP set.

## DonutSMP
- **BaseSurvey** - DonutSMP-only, first-person surface survey. The centre ray and optional small view sweep record only the first block hit. Storage, machinery, workstations and lights share one notebook; by default three distinct blocks from two families including a distinctive block within 12 blocks make a candidate, never a confirmed base. Tune these gates, memory, look distance, HUD rows and chat notifications in one module. Entries are session-local; world/server changes clear them. The module does not activate other finders. Fresh Donut presets use it instead of StorageESP/BaseFinder; existing presets and legacy consumers are unchanged.
- StorageESP, ChunkFinder, SusChunkFinder and ArtificialLightFinder overlap in the block categories; BaseSurvey records only visible examples. ActivityFind, SoundFind, EntityFind, BaitESP and VanishSense are not inputs. Geode, debris, ore and seed predictions are not evidence of a base. StashLogger, RegionMap, Breadcrumbs, AutoScout, DigDown, ElytraHunt and premium RTP/tunnel finders remain independent utilities; no automated travel, digging or bypass feeds into the survey.
- **StashLogger** — persistent categorized stash index (`nox/stash-index.json`), `/nox stashes`.
- AHSniper, Auction, AutoTPAccept, StaffNotifier, CoordBook (`Gizmos` markers), SoundFind.

## Misc
- **FriendSync** (premium) — cross-client presence: `/nox friendcode`, `/nox friendadd`, JWT session via license server, gizmo markers.
- **Replay** — `.donutreplay` ring-buffer recorder (10min@4Hz), playback screen (`/nox replay`), MP4 export (`/nox replay export`, needs ffmpeg), Discord upload via DiscordNotifier webhook.
- **AltManager** — enc-token keystore (`NativeCore.wrap`), groups, `/nox alt <name>`.
- **TamperWarner** — integrity re-check, injection watch, lock mode.
- SecurityAudit (`/nox audit`), CrashReporter (`/nox crashreport`), DiscordNotifier, WaypointShare, StreamerMode, PvpCoach, SkinChanger, Emotes, CoordFinder, TikTokLive, ReplayMod.

## Stream (Session B)
- TikTokBridge (`tools/tiktok_sidecar.py` ws://127.0.0.1:8890), ViewerVotes, GiftCombos, RuleEngine.

## Security/infra (always-on, hidden)
- TamperGuard (jar sig rehash), FailSafe escalation arbiter, VmGuard, CrashLoopGuard, Canary, Watermark, Distributed (8 checks), Heartbeat, ConfigSig, NativeCore (Rust JNI).

## Chat
- **NoxChat** — E2E X25519+ChaCha20-Poly1305: `/nox chat`, `/nox chatkey` (fingerprint), `/nox chatrelay` (relay URL); `tools/nox_relay.py`.
