package dev.noxclient.module;

/**
 * Modules added by the parallel master-upgrade session (M20/26/29) —
 * kept out of ModuleManager.registerAll to avoid file collisions with
 * the other session editing it. Called once from NoxClient.init.
 */
public final class ExtraModules {
    public static void register(ModuleManager mm) {
        mm.register(new dev.noxclient.module.misc.SecurityAudit());
        mm.register(new dev.noxclient.module.misc.ClipMarker());
        mm.register(new dev.noxclient.module.render.ChatTriggers());
        mm.register(new dev.noxclient.module.misc.CoordFinder());
        mm.register(new dev.noxclient.module.misc.SkinChanger());
        mm.register(new dev.noxclient.module.misc.ReplayMod());
    }
}
