# NOX architecture

Fabric client mod, MC 26.3, single source set. Entry: `dev.noxclient.NoxClient` (`onInitializeClient`).

## Package map
| Package | Role |
|---|---|
| `module/` | Module base, `ModuleManager` (registration dedup, FAKE_KEYS for synthetic binds), `Category`, `ExtraModules` (second registration site — Session B modules) |
| `module/{combat,movement,render,misc,donutsmp,player}` | Feature modules |
| `setting/` | Typed settings (Bool/Num/Mode/Str/Color/Key) — `get()` coerces raw values |
| `gui/` | NoxClickGui (comp-lib rebuild), NoxWindowScreen (legacy grid), HudEditor, screens, Toasts, `Gui`/`UICustom`/`SmoothShapes` helpers |
| `gui/ds`,`gui/comp` | Design tokens + component library |
| `gui/nvg` | NanoVG post-pass (GuiRendererMixin TAIL — deferred GUI needs it) |
| `premium/` | LicenseManager, Hwid, Distributed checks |
| `security/` | FailSafe, VmGuard, Canary, Watermark, Heartbeat, CrashLoopGuard, ConfigSig, WebhookSec |
| `ncore/` | `NativeCore` — JNI bridge to `native/` Rust cdylib (`libnoxcore.so` in jar) |
| `update/` | UpdateManager + Apply (signed self-update) |
| `alts/` | AltManager + AltScreen (enc keystore) |
| `chat/` | NoxChat (E2E) |
| `replay/` | ReplayRecorder/Player/Screen/Exporter/HighlightMarks |
| `cosmetics/` | GlbModel loader, NoxModels registry, layers, CosmeticDef, CosmeticsMarket |
| `stream/` | TikTokBridge, ViewerVotes, GiftCombos, RuleEngine |
| `stats/` | Stats + StatsStore + StatsScreen + StatsExport |
| `telemetry/` | Opt-in telemetry + Sanitizer |
| `media/` | MediaService (playerctl/osascript/fallback), LyricsService |
| `discord/` | NoxRPC (rich presence), Webhook, DiscordLink (single invite source) |
| `util/` | TamperGuard, CrashReporter, StashIndex, CoordBook, DebugConsole, MoveGuard, Legitifier, AnimMath |
| `mixin/` | Render/input/packet hooks (kept thin — logic lives in util/module classes) |
| `api/` | AddonContext + NoxAddonLoader (`nox:addon` entrypoints, `COMMANDS` map) |

## Runtime flow
`onInitializeClient`: ModuleManager.registerAll → config.load → RPC → LicenseManager → AltManager → TamperGuard.verifyAsync → CrashLoopGuard → UpdateManager → Telemetry → Stats → CrashReporter → NoxChat → ExtraModules → CrashLoopGuard.enforce → HUD registration → addons.

`onTick` (END_CLIENT_TICK): GUI key handling → DebugConsole → NoxOverlay.noteTick → modules.tick → AnimationSystem → FailSafe.tick → TamperGuard screen/warn → config.onServerChange → LicenseManager/Telemetry/Stats/Update ticks.

## Commands
`/nox <cmd> [args]` → `AddonContext.COMMANDS` (any module may `put` in its ctor — args are 0-indexed after the command name). DebugConsole additionally drives `<gameDir>/nox_cmd` per tick (`tools/noxctl.py`).

## Threads
Render thread owns all module ticks + GUI. Network I/O runs on dedicated daemon threads (`nox-friendsync`, `nox-cosmetics`, `nox-webhook*` …) that marshal results back via `mc.execute()`.
