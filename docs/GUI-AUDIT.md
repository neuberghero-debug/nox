# NOX GUI and module audit

1. Studio: check desktop and compact GUI scales, click/drag bounds, search,
   keyboard selection, settings reveal, themes and reduced motion. Verify both
   the dev client and the packaged NanoVG runtime.
2. WindowGUI: cover Cards, Compact rows, Sidebar, Panels and Hub. Verify
   Studio/Window switching, placement, clipping, popup focus and restoring GUI
   state. Keep the two styles distinct.
3. Settings and HUD: test booleans, slider edges, enum overflow, color, text
   editing, bind capture/cancel, Toggle/Hold and the command palette. Then check
   HUD editor dragging, snapping and scaling; compare Frost and Prism on the
   window itself.
4. Secondary screens: title and loading, first run/privacy, cosmetics/capes,
   profiles, presets and shop. Check back navigation, empty/error states and
   layout at the minimum supported GUI size.
5. Modules: start with the static registration/defaults audit. In an isolated
   local world check enable/disable, config reload, settings boundaries, keybind
   mode and cleanup on world/connection change. Verify the actual effect per
   module, not just that it toggles. Report checks requiring external services,
   platforms or hardware separately (including Discord IPC, Spotify and LyricsSky).

For each pass record the screen or module, client/profile, GUI scale, reproduction
and result. Mark blocked or untested cases explicitly; retain screenshots or fresh
log excerpts for regressions. Do not use live public gameplay as a test harness.

## First pass

- Packaged Linux/Fabric 26.3 client: NanoVG context initialized using only the
  packaged bindings; Studio rendered at GUI scales 2 and 4.
- At scale 4, Style and HUD could be opened independently. Clicking below a
  clipped module row left the module off. Search showed a zero-results view and
  the expanded settings list could be scrolled.
- At 577 x 324 GUI units, switching Studio > Cards > Studio updated the profile
  setting both ways. With maximum font scale and Large text, the ClickGUI
  setting switched to a compact cycle control and still accepted clicks.
- Module smoke tests: DiscordRPC stayed off when saved as disabled and connected
  to Vesktop after enabling. Fullbright followed the Toggle and Hold key edges
  at the title screen; in-world lighting was not tested.
- Next WindowGUI regressions: Panels renders a filtered list but clicks still
  index the unfiltered category; a narrow Cards footer can hide Shop and Panic.
  Verify both alongside popup focus and clipping before auditing the modules in
  a local world. Spotify and LyricsSky still need playback tests.
