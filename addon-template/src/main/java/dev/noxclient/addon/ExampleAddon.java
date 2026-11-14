package dev.noxclient.addon;

import dev.noxclient.api.NoxAddon;
import dev.noxclient.api.NoxAddonContext;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;

/**
 * Template NOX addon. Build a jar with fabric.mod.json:
 *   "entrypoints": { "nox:addon": ["dev.noxclient.addon.ExampleAddon"] }
 * Drop it in mods/ next to NOX.
 */
public class ExampleAddon implements NoxAddon {
    public String name() { return "Example"; }
    public String author() { return "you"; }

    public void register(NoxAddonContext ctx) {
        ctx.registerModule(new Module("HelloAddon", "example", Category.CLIENT) {
            @Override public void onTick() { /* your code */ }
        });
        ctx.registerCommand("hello", args -> System.out.println("hi from addon"));
    }
}
