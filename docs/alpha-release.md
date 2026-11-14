# NOX 0.1.0-alpha — early alpha

First public alpha of the NOX client for **Minecraft 26.3 Fabric** on DonutSMP.
Expect rough edges — report everything on the Discord:
**https://discord.gg/tewtZTsZf5**

## Install

1. Fabric Loader for 26.3 + Fabric API in your mods folder.
2. Drop `noxclient-<version>.jar` into `mods/`.
3. Launch, press **Right Shift** for the ClickGUI.

## What's in

- **~140 modules** — DonutSMP automation (AutoSell, AHSniper, AutoStonks,
  TradeChecker, DropPartyAlert, StaffNotifier, base/stash finders), combat
  (KillAura, AutoTotem, AutoArmor, AutoSoup, Anchor/AutoCrystal helpers),
  movement (Scaffold, Tower, FlyBypass, Freecam, Zoom), render (ESP, HoleESP,
  StorageESP, Cosmetics), misc utilities.
- **GUI** — Right Shift opens the ClickGUI. `Interface` module switches the
  look live: **Cards** (dashboard), **Compact rows**, **Sidebar**, **Panels**
  (Meteor-style floating category columns — drag headers, scroll columns,
  RMB opens the settings popup). Themes, accents, colors, radius, density,
  window styles all customizable.
- **Per-module keybinds** — every module has Activation (Toggle/Hold) +
  Keybind in its settings popup, independent of vanilla controls.
- **HUD** — auto-fits any GUI scale; widgets anchor to any corner; module
  arraylist caps to screen with "+N more".
- **Cosmetics** — trails, auras, kill effects, real capes (NOX / Inferno /
  Void / Animated / **Custom** — drop your own PNG into `run/nox/capes/`).
- **Premium** — crypto-signed licenses (`NOX1.*` keys, Ed25519). Buy/activate
  via the shop screen or `/buy` on Discord.
- **Discord** — Rich Presence, community bot (tickets, roles, panels).

## Diagnostics

Hovering an enabled module shows a **live status line** (what it's doing or
why it's idle). File-driven debug console in `run/nox_cmd` (`why <module>`,
`selftest`, `mods`, `state`, `toggle`, `set`…).

## Known issues

- Voice chat: not in this alpha.
- Some DonutSMP modules need specific screens/contexts — check the status
  line first, then report.
- `Invalid move player packet` kicks were fixed for NoFall/FlyBypass; other
  packet-affecting modules may still trip server checks.
