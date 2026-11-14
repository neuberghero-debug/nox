# NOX agent notes

## Build/deploy
- `bash tools/devloop.sh` — one command: gradle build → nox_doctor → atomic deploy
  to the Modrinth profile (`profiles/NOX/mods`) → tail latest.log + newest crash.
  Never `cp` straight onto the jar: always tmp + `mv` + `sync` (corrupt-jar history).
- `python3 tools/nox_doctor.py` — static crash prevention, ~seconds, no MC launch:
  mixin targets vs javap on the merged 26.3 jar, blur-per-frame rule,
  jar zip integrity + sig.txt roundtrip, asset references. Exit != 0 → don't deploy.
- `tools/render_sky.py` — regenerates `assets/noxclient/sky/frame_*.jpg`
  (env: `SKY_OUT`, `SKY_FRAMES` for quick test renders). `tools/video-to-sky.sh`
  converts a video clip into sky frames.

## MC 26.3 gotchas (learned from crashes)
- `ResourceLocation` is now `net.minecraft.resources.Identifier`
  (`Identifier.fromNamespaceAndPath`); `GuiGraphicsExtractor.blit(id, x0,y0,x1,y1,
  u0,u1,v0,v1)` takes END coords + UV order u0,u1,v0,v1 (not u0,v0,u1,v1).
- `Setting.get()` must coerce (raw `set()` can store wrong types from config/cmd)
  — all typed settings override get() with numOr/intOr/string fallbacks.
- Cape: `AvatarRenderState.skin` is a `PlayerSkin` record — swap `cape`/`elytra`
  with `ClientAsset.ResourceTexture(Identifier)`; match local player via
  `state.skin == mc.player.getSkin()`.
- Move packets in the first ~60 ticks after join/teleport →
  "Invalid move player packet" kick. Guard with `player.tickCount < 60`.
- `blurBeforeThisStratum` = ONE per frame total; vanilla screens already blur.
- Current screen lives on `mc.gui.screen()` (public accessor; `Gui.setScreen` is the
  choke point, `setScreenAndShow` delegates). For input injection: vanilla widgets
  gate clicks on `isHovered` (render-time, real cursor) — set `MouseHandler.xpos/ypos`
  (window px = gui*scale) + `AbstractWidget.isHovered` via reflection.
- `GuiGraphicsExtractor` (not old GuiGraphics); screens override `extractRenderState`.
- Swing: `mc.player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true)`.
- `SimpleTexture` has no texture view before reload → use `DynamicTexture`+`iconReady`.
- `mc.getConnection().send()` re-enters send hooks — bypass via the `connection` field.
- `handleContainerInput` clicks on `inventoryMenu` (containerId 0) get server-side
  reverted when no screen is open — they only land on server-opened containers.
  For own-inventory moves use packets instead: `ServerboundPlayerActionPacket
  .SWAP_ITEM_WITH_OFFHAND` (hotbar↔offhand) or `gameMode.useItem` on held armor
  (server-side Equippable equips; the legit right-click path).
- `Legitifier.ready(owner, cps, jitter)` — pacing state is PER-OWNER
  (IdentityHashMap). Always pass `this`; the shared no-arg variant starves
  later modules when several pace-gated modules are enabled.
- Keyboard input uses SDL scancodes, not GLFW codes (ESC=41, T=23, RShift=229,
  mouse buttons LMB=1/MMB=2/RMB=3).
- **SDL, not GLFW**: keys are SDL scancodes (ESC=41, RETURN=40, BACKSPACE=42,
  T=23, RSHIFT=229); mouse buttons are 1-indexed (LMB=1, MMB=2, RMB=3).
  `Map.ofEntries` throws on duplicate keys at class-init.
- Armor lives in `EntityEquipment` (`player.getItemBySlot(EquipmentSlot.X)`);
  `Inventory.getSelectedItem()` for held item.
- `g.pose()` = `Matrix3x2fStack` — push/translate/scale works for GUI animation.

## Debug console (runtime automation)
- `util/DebugConsole` polls `<gameDir>/nox_cmd` each tick → `nox_ack`.
  `tools/noxctl.py [run|prod] "cmd"` — quote multi-word commands (each argv
  is sent separately!). Commands: shot, gui, close, screen <Class>, click X Y [B],
  dclick, drag, move, scroll, key CODE (GLFW→SDL translated), char C, tab N,
  join HOST, slot N [B] (real container packet), slots, toggle/set/get MODULE,
  chat TEXT, state.
- `move`/`click` set `DebugConsole.hoverX/Y` — `extractRenderState` mx/my args
  come from the real cursor, so screens read the override for hover state.
- `ScreenTrackMixin` tracks Screen.init + removed for arbitrary-screen input.
- `tools/relaunch.sh [join]` — kill dev MC, compile, relaunch, wait for title.
- Test server console: `/var/home/julius/NOX/patch-devin/test-server/ctl.sh
  "cmd"` (tmux send-keys to `dsmp` session). Player name changes each dev
  launch — get it from `./ctl.sh "list"`.

## TamperGuard
- `gradle noxSig` writes `assets/noxclient/sig.txt` = sha256(name+bytes) over
  `dev/noxclient/**.class` + `fabric.mod.json` + `noxclient.mixins.json`, sorted.
- `util/TamperGuard` rehashes at runtime → mismatch opens `gui/TamperScreen`
  + periodic chat warning. Dev runs (classes dir) skip automatically.

## Premium/licensing
- Ed25519 keys `NOX1.<b64url payload>.<b64url sig>`; public key embedded in
  `premium/LicenseManager`; private key only in `nox-bot/keys/` (gitignored).
- Bot: `/issue @user <days>` mints keys, `/buy` shows `NOX_WALLETS` env JSON.
- Premium modules open `gui/PremiumScreen` when toggled without license.

## Media / Discord
- `media/MediaService` — now-playing via playerctl (Linux), osascript/nowplaying-cli
  (macOS), playerctl→window-title fallback (Windows). Module: `SpotifyHud`.
- `discord/NoxRPC` — IPC socket scan covers native, flatpak (Discord/Canary/PTB/
  Vesktop/WebCord/ArmCord), snap, `/tmp`, `$TMPDIR`; Windows pipes discord-ipc-0..3.
- Discord link single source: `discord/DiscordLink.get()` (`nox/discord.txt` override).

## GUI
- `NoxWindowScreen` = card grid; `ModuleCard` tiles (LMB toggle / RMB+⚙ settings /
  MMB keybind); `SettingsPopup` = separate draggable modal — no dropdowns.
- `gui/Gui` helpers: round/pill/circle/roundBorder/shadow. `Theme.cycle()` on T.

## GuiGraphicsExtractor.blit (26.3, from bytecode)
- `blit(id, x0, y0, x1, y1, u0, u1, v0, v1)` — END coords (not w/h),
  normalized UVs in u0,u1,v0,v1 order (NOT u0,v0,u1,v1).
- `gui/SmoothShapes` — per-color 64px supersampled masks + DynamicTexture
  + bilinear corner blits + axis fills = AA rounded corners at any scale.

## MC 26.3 — more findings (session 2)
- `InputConstants.isKeyDown(int)` reads RAW SDL keyboard state — synthetic
  keyPress events never flip it. For module keybind tests use
  `ModuleManager.FAKE_KEYS` via `holdkey <code> <1|0>` (also sends real
  `keyboardHandler.keyPress(win, act, new KeyEvent(key,0,0))`).
- `DebugConsole` test commands: `share [mod]` (config→clipboard),
  `clip` (read clipboard), `import`, `holdkey`, `mods`, `slots`, `slot <i> <btn> [swap|quick|pickup|throw]`.
- Server-container clicks work (`slot N 0`), inventoryMenu (containerId 0)
  clicks get reverted — use `useItem` on held armor to equip instead.
- `ContainerInput` (not ClickType) in 26.3 handleContainerInput.
- `Level.getSkyDarken()` (int, no args) replaces getDayTime.
- `Minecraft.rightClickDelay` is private; `MultiPlayerGameMode.destroyDelay`
  private → `mixin/GameModeAccessor`.
- `Gui.graphicsExtractor()` → `mc.gui.screen()` for current Screen (Gui-owned).
- SwingAnimation package: `net.minecraft.world.item.component`.
- `GuiGraphicsExtractor.blit(id,x0,y0,x1,y1,u0,u1,v0,v1)` — endpoint coords
  + u0,u1,v0,v1 UV order (NOT u0,v0,u1,v1). Used by `gui/SmoothShapes`
  for AA corners/circles via supersampled DynamicTextures (call upload()!).
- ModuleManager registers each module ONCE — dup names are unreachable
  (reg() dedup enforced by audit).
- Testserver blocks /give|/kill|/gamemode|/effect — DonutSMP-style perms.

## NanoVG overlay (gui/nvg/NvgOverlay)
- `lwjgl-nanovg` dep + `include`d natives (jarjar) — LWJGL loads .so from
  nested classpath jars; works in dev + production without extra mods.
- MC 26.3 GUI is DEFERRED (GuiRenderer batches) — raw GL inside
  extractRenderState lands BEHIND everything. Post-pass lives in
  GuiRendererMixin @ render() TAIL → NvgOverlay.render().
- Screens queue ops (glow/sheen/vignette) per extractRenderState; render()
  drains + clears. NVG coords = GUI units via nvgBeginFrame(guiW, guiH, guiScale).
- sig: noxJarSig runs AFTER jar (loom include remaps fabric.mod.json) and
  REWRITES the zip (ZipOutputStream append corrupts — never append).
