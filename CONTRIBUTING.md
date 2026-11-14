# Contributing to NOX

NOX is fully open source (GPL-3.0) — everyone is welcome to contribute code, modules,
designs, textures, docs or testing. Join the community: **https://discord.gg/tewtZTsZf5**

We especially need **designers**: better icons, themes, HUD layouts, animated textures
(capes, sky frames), marketing art. Grab the `Designer` role in the Discord's
role panel and show us what you've got.

## Ways to contribute

- **Modules** — pick anything from `docs/roadmap.md` or propose your own.
- **Design** — themes live in `src/main/java/dev/noxclient/gui/theme/Theme.java`,
  tokens in `gui/UICustom.java`. The design language is documented in
  `.agents/skills/nox-design-system/SKILL.md`; GUI conventions in
  `.agents/skills/mc-gui-architect/SKILL.md`.
- **Textures/art** — sky frames (`tools/render_sky.py`, `tools/video-to-sky.sh`),
  cape PNGs, icons. Drop WIP in `#general` for feedback.
- **Bot** — `nox-community-bot/` (discord.js v14), `nox-bot/` (Python license service).
- **Docs & testing** — run the client on DonutSMP, report what breaks.

## Dev setup

    gradle build           # → build/libs/nox-*.jar  (drop into mods/)
    gradle runClient       # dev instance
    bash tools/devloop.sh  # build → doctor → atomic deploy to the NOX profile

- Java 25, Gradle ≥ 9.9, Fabric for Minecraft 26.3 (Mojang names, no mappings).
- `python3 tools/nox_doctor.py` must pass before deploying — it statically checks
  mixin targets, API pitfalls and jar integrity.
- Runtime automation: `tools/noxctl.py run "<cmd>"` talks to the in-game debug
  console (`state`, `gui`, `shot`, `toggle`, `set`, `why`, `selftest`, …).
- Read `AGENTS.md` first — it lists the 26.3 gotchas that already cost us crashes.

## Code conventions

- Match the existing style: compact code, no comments unless needed, ARGB colors
  always full (`0xFF…`), dt-based animations, `Gui`/`SmoothShapes` helpers for
  rounded shapes.
- One module per file in `module/<category>/`; register it in `ModuleManager`.
- Settings via `add(new Setting.X(...))`; every visible control must do something.
- No secrets in the repo — tokens/keys live in `.env` (gitignored).

## Process

1. Discuss bigger changes in Discord first (or open a draft PR early).
2. Small focused PRs; describe what runtime test you ran (`noxctl` output helps).
3. Design submissions: post screenshots in the Discord; maintainers pick what ships.
