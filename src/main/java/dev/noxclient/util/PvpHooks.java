package dev.noxclient.util;

import net.minecraft.world.entity.Entity;

import java.util.function.Consumer;

/** Hit-event bridge: AttackMixin (free jar) fires, premium modules
 *  (PvpCoach in the encrypted pack) listen. */
public final class PvpHooks {
    private static volatile Consumer<Entity> hitListener;

    public static void hitListener(Consumer<Entity> c) { hitListener = c; }

    public static void onHitEntity(Entity target) {
        Consumer<Entity> h = hitListener;
        if (h != null) h.accept(target);
    }

    private PvpHooks() {}
}
