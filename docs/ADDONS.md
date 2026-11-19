# NOX addon API

External Fabric mods can hook NOX through `api/` — the same surface internal modules use.

## Entrypoint
Implement `NoxAddon`, declare `"nox:addon": "your.Entrypoint"` in your `fabric.mod.json` custom section. `NoxAddonLoader` hands you a `NoxAddonContext`:

```java
public interface NoxAddon {
    void init(NoxAddonContext ctx);
}
```

## NoxAddonContext
- `ctx.registerModule(module)` — dedup by name, appears in ClickGUI, settings persist.
- `ctx.modules()` — ModuleManager (read/toggle existing modules by name).
- `ctx.registerCommand(name, args -> …)` — `/nox <name>` handlers.
- `ctx.toast(msg, argb)` / `ctx.toastInfo(msg)` — overlay toasts.
- `ctx.chat(msg)` — local `[NOX]` chat line (no-op before join).

## Modules
```java
class MyModule extends Module {
    MyModule() { super("Name", "desc", Category.MISC); }
    @Override public void onTick() { /* mc.player/mc.level available */ }
}
```
Settings: `add(new Setting.Bool("x", true))`, `Num(min,max)`, `Mode("n","d","A","B")`, `Str`, `Color`, `Bind`. All settings persist + appear in ClickGUI automatically.

## Rules
- Never inject `ServerboundMovePlayerPacket`s — use `util/MoveGuard` helpers (`requestHop`, `requestRot`) or the server kicks.
- Network/disk work on your own daemon threads; marshal back via `Minecraft.getInstance().execute()`.
- Set `risky = true` in the ctor for anything ban-bait — the confirm gate handles the warning.

## Minimal working addon (copy-paste)

`fabric.mod.json` (your mod):
```json
{
  "schemaVersion": 1,
  "id": "mynoxaddon",
  "depends": { "nox": "*" },
  "custom": { "nox:addon": "com.example.mynoxaddon.MyAddon" }
}
```

`MyAddon.java`:
```java
package com.example.mynoxaddon;

import dev.noxclient.api.NoxAddon;
import dev.noxclient.api.NoxAddonContext;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

public class MyAddon implements NoxAddon {
    @Override public void init(NoxAddonContext ctx) {
        ctx.registerModule(new HelloModule());
        ctx.registerCommand("hello", args -> ctx.toastInfo("hello from my addon"));
    }

    static class HelloModule extends Module {
        private final Setting.Num interval = add(new Setting.Num("Interval (s)", 10, 1, 120));
        private long next;
        HelloModule() { super("HelloAddon", "example module", Category.MISC); }
        @Override public void onTick() {
            if (System.currentTimeMillis() < next) return;
            next = System.currentTimeMillis() + (long) (interval.get() * 1000);
            mark("tick");   // shows up in `why`/selftest
        }
    }
}
```

That's it — the module appears in the ClickGUI under Misc, its settings
persist, and `/nox hello` works. No mixins, one entrypoint key.
