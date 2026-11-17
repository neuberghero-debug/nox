# NOX design system

Canonical tokens live in `gui/ds/DS.java`; the interactive component library is `gui/comp/`. Both sit on top of `gui/UICustom` (legacy palette bridge — new code imports `DS`, not `UICustom`).

## Tokens (DS)
- **Palette**: bg `#0D0F14`, panel `#161B26`, card `#1B2230`, border `rgba(255,255,255,.08)`, text `#E8E8F0`, dim `#8A8A9E`, accent `#7A2BE2`, ok `#66D9A0`, warn `#FFD870`, err `#FF6B6B`.
- **Radii**: card 10, pill full, button 8, modal 12.
- **Spacing**: 4-pt grid (4/8/12/16/24). Row heights: module 56, setting 24.
- **Type**: UI = default font; data/coords = JetBrains Mono (`assets/noxclient/fonts/jetbrains_mono.json` → `DS.mono`).

## Components (gui/comp)
`Comp` base (x/y/w/h + hover/focus state), `Inputs` (text fields w/ caret, focus ring, clear button), `Buttons` (primary/ghost/danger + icon), `Toggle`, `Slider` (drag + wheel), `Select` (mode cycle), `Card`, `Badge`, `Modal`, `Tooltip`, `Scrollable`, `TabPill`, `Icons` (12px vector set — no texture deps).

## Usage rules
- New screens MUST use DS tokens + comp widgets (Module 25 migrates all legacy screens).
- Accent strip = 2px left edge on interactive rows; selected state = accent fill at 18% alpha.
- Toasts: `Toasts.push(text, argb)` — right-top card, 250ms slide-in, progress bar, `UiSounds` hook.
- Animations: `gui/Anim` (lerp + spring via `AnimMath`); hover fades ≥120ms.
- Screens in 26.3 override `extractRenderState(GuiGraphicsExtractor, mx, my, dt)`; text via `Gui.text(g, font, s, x, y, argb)`; shapes via `g.fill` + `SmoothShapes` for AA corners.
- Command palette pattern: Ctrl+K modal over a dimmed screen, live filter, accent selection.

## A11y
`Interface` module → `colorBlind` (ok/err become cyan/orange + shape cues), `largeText` (1.25x). `UICustom.refreshA11y()` re-derives tokens.
