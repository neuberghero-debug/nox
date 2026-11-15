package dev.noxclient.physics;

/** Render-owned snapshot. No world, entity, or animator references survive extraction. */
public final class AnimationFrame {
    public final float[] weights = new float[AnimState.values().length];
    public float headYaw, headPitch, landing, phase, strafe;
    public boolean active;
    public float weight(AnimState state) { return weights[state.ordinal()]; }
    public interface Access { AnimationFrame nox$animation(); }
}
