# NOX addon API

External Fabric mods can hook NOX through `api/` — the same surface internal modules use.

## Entrypoint
Implement `NoxAddon`, declare `"nox:addon": "your.Entrypoint"` in your `fabric.mod.json` custom section. `NoxAddonLoader` hands you an `AddonContext`:

```java
public interface NoxAddon {
    void init(AddonContext ctx);
}
```

## AddonContext
- `ctx.modules` — ModuleManager (register additional `Module` subclasses, read/toggle existing by name).
- `ctx.commands` (`AddonContext.COMMANDS`, `Map<String, Consumer<String[]>>`) — register `/nox <name>` handlers; args arrive 0-indexed.
- `ctx.hud` — HUD widget registration.
- `ctx.events` — lightweight client-event bus (tick, join, disconnect, chat).

## Modules
```java
class MyModule extends Module {
    MyModule() { super("Name", "desc", Category.MISC); }
    @Override public void onTick() { /* mc.player/mc.level available */ }
}
```
Settings: `add(new Setting.Bool("x", true))`, `Num(min,max)`, `Mode("n","d","A","B")`, `Str`, `Color`, `Key`. All settings persist + appear in ClickGUI automatically.

## Rules
- Never inject `ServerboundMovePlayerPacket`s — use `util/MoveGuard` helpers (`requestHop`, `requestRot`) or the server kicks.
- Network/disk work on your own daemon threads; marshal back via `Minecraft.getInstance().execute()`.
- Register once — duplicate module names are unreachable.
