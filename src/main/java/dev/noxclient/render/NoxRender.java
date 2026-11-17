package dev.noxclient.render;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * World-space render emitters, drained once per frame inside the vanilla
 * gizmo-collection window (see GizmoEmitMixin). Emitters call
 * net.minecraft.gizmos.Gizmos.* — the API gives through-wall lines/cuboids/
 * billboard text without a custom pipeline.
 */
public final class NoxRender {
    @FunctionalInterface
    public interface Emitter { void emit(); }

    private static final List<Emitter> EMITTERS = new CopyOnWriteArrayList<>();

    public static void add(Emitter e) { if (e != null && !EMITTERS.contains(e)) EMITTERS.add(e); }
    public static void remove(Emitter e) { EMITTERS.remove(e); }

    /** Fire-and-forget: emits on the next frame, then removes itself. */
    public static void once(Runnable r) {
        Emitter e = new Emitter() {
            @Override public void emit() { r.run(); EMITTERS.remove(this); }
        };
        EMITTERS.add(e);
    }

    public static void emit() {
        for (Emitter e : EMITTERS) {
            try { e.emit(); } catch (Throwable ignored) {}
        }
    }

    private NoxRender() {}
}
