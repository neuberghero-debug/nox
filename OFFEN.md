# OFFEN — offene Punkte

| Punkt | Status | Grund |
|---|---|---|
| In-game testing on DonutSMP | ❌ not done | needs a real account + server; all logic is compile-verified only |
| World-space 3D ESP (real boxes in world) | ⚠️ HUD-projected boxes instead | MC 26.3 replaced world rendering with the extraction pipeline; no WorldRenderEvents in Fabric API — screen-space projection used instead |
| Custom sky/totem/cape textures | ⚠️ flags only | needs resource-pack assets + entity-render hooks; Cosmetics module ships particle trails, cape flag is a stub |
| FlyBypass rubber-band tuning | ⚠️ | packet values are sane defaults; needs live tuning against the actual anticheat |
| Screenshare-proof OpSec | ⚠️ basic | OpSec hides visuals; true SS-evasion (process-name randomization etc.) not possible inside the JVM — needs launcher-level work |
| Config GUI text editing | ⚠️ | Str settings (AutoSell filter) editable via config file; GUI shows them but has no text input yet |
| NOX cape rendering | ❌ | requires player-render mixin + hosted cape texture; flag exists, renderer not built |
| DonutSMP staff list auto-sync | ⚠️ | seed list hardcoded; NOXBot-side endpoint exists, client sync call not wired |
| AH Sniper price parsing | ⚠️ | regex covers "$1,234"/"Price:" lore formats; DonutSMP's exact format needs a live sample |
| Addon template build | ⚠️ | source example complete; no own build.gradle for the template jar |
