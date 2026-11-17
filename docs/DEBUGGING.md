# NOX debugging & runtime automation

## devloop
`bash tools/devloop.sh` — build → `nox_doctor` static checks → atomic deploy (`profiles/NOX/mods`, tmp+mv+sync) → tail latest.log + newest crash. Never `cp` onto the jar.

## relaunch
`bash tools/relaunch.sh [join]` — kill dev client → `gradle compileJava` → `gradle runClient` → wait title → optionally `join localhost`.

## noxctl + DebugConsole
`tools/noxctl.py "cmd"` writes `<gameDir>/nox_cmd`; `util/DebugConsole` drains it per tick → `nox_ack`.

Commands: `state`, `shot`, `close`, `screen <Class>`, `gui`, `join <host>`,
`toggle|set|get <module>`, `chat <text>`, `key <sdlCode> [mods]` (SDL not GLFW; KMOD_CTRL=0xC0), `char <c>`, `click X Y [btn]`, `dclick`, `drag`, `move`, `scroll`, `holdkey <code> <1|0>` (ModuleManager.FAKE_KEYS — synthetic keys don't flip `isKeyDown`), `mods`, `slots`, `slot <i> <btn> [swap|quick|pickup|throw]`, `share`, `clip`, `import`, `moveguard`, `oresim`.

## In-game
`/nox` lists commands → `AddonContext.COMMANDS` (`alt`, `audit`, `chat`, `chatkey`, `chatrelay`, `cosmetic`, `crashreport`, `friendadd`, `friendcode`, `replay`, `stashes`, …). Args to handlers are 0-indexed (dispatcher strips the name).

## Test server
`/var/home/julius/NOX/patch-devin/test-server/` — `start.sh` boots Paper on :25565 under tmux `dsmp`; `ctl.sh "cmd"` → server console (`list`, `op <name>`, `setblock x y z chest`).

## Traps (learned the hard way)
- **NEVER jstack/jcmd the dev client** — the Gradle daemon ends up holding a ptrace stop on the Render thread; the whole client freezes and the daemon must be killed (client usually dies with it). Use logs/noxctl only.
- Extra `ServerboundMovePlayerPacket`s → GrimAC/vanilla kick ("Invalid move player packet"). All module movement rides vanilla packets via `MoveGuard`.
- `/proc/<pid>/task/*/status` `State: t` = ptrace stop — find `TracerPid`, kill the tracer.
- Synthetic key events don't affect `InputConstants.isKeyDown` — use `holdkey`.
- Stale crash files aren't current crashes — check timestamps before panicking.
