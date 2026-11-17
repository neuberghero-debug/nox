# NOX HUD + overlay system

## Render path (26.3 deferred GUI)
`NoxOverlay` registers via `HudElementRegistry.addLast` — draws each enabled `HudWidget` into `GuiGraphicsExtractor` every frame. GUI batches are deferred: raw GL work must go through `gui/nvg/NvgOverlay` (drained in `GuiRendererMixin` render TAIL).

## Widgets (`gui/HudWidgets`)
arraylist, coords, fps, tps (`NoxOverlay.noteTick` feeds it), keypeek, armor, potions, server brand, media (SpotifyHud), session stats, sparklines.

## Modes (Modul 16)
`HudWidgets.mode` — Off / Minimal / Standard / Full. `showDisabled` adds disabled modules to the arraylist (dimmed, strike). Coords render as a monospace card (JetBrains Mono) with per-axis color coding.

## Editor
`HudEditor` screen — drag widgets, per-widget toggles; persisted `nox/hud.json`. Resolution-independent: widgets anchor to edges, scale = guiScale.

## Toasts
`Toasts.push(text, argb)` — right-top card: icon, progress bar, 250ms slide-in (`Anim`), `UiSounds` cue. Used by every subsystem (replay saved, license, cosmetics, fail-safe warnings).

## UI sounds
`assets/noxclient/sounds/ui/*.ogg` + `sounds.json` → `UiSounds.click/hover/toast/error/…`.
