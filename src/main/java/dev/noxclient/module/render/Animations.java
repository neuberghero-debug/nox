package dev.noxclient.module.render;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;

/** Independent animation groups; all settings use the existing preset system. */
public final class Animations extends Module {
    public static Animations INSTANCE;
    public record Group(Setting.Bool enabled, Setting.Num intensity) {
        public float amount() { return enabled.on() ? intensity.floatVal() : 0; }
    }
    public final Group head = group("Head follow", .4);
    public final Group body = group("Body motion", .5);
    public final Group arms = group("Arm motion", .5);
    public final Group legs = group("Stride", .5);
    public final Group idle = group("Idle motion", .35);
    public final Group creatures = group("Creature motion", .5);
    public final Group handBob = group("Hand bob", .4);
    public final Group itemSwing = group("Item swing", .6);
    public final Group itemUse = group("Item use", .4);
    public final Setting.Bool cameraRoll = add(new Setting.Bool("Camera roll", false));
    public final Setting.Bool cameraBreath = add(new Setting.Bool("Camera breathing", false));
    public final Setting.Bool cameraLanding = add(new Setting.Bool("Camera landing", false));
    public final Setting.Num cameraIntensity = add(new Setting.Num("Camera intensity", .3, 0, 1));
    public final Setting.Num stiffness = add(new Setting.Num("Spring stiffness", 100, 10, 300));
    public final Setting.Num damping = add(new Setting.Num("Spring damping ratio", .9, .5, 2));
    public final Setting.Num radius = add(new Setting.Num("Animation distance", 32, 8, 64));

    public Animations() { super("Animations", "Procedural poses, hands and secondary motion", Category.RENDER); INSTANCE = this; }
    private Group group(String name, double intensity) {
        return new Group(add(new Setting.Bool(name, true)),
            add(new Setting.Num(name + " intensity", intensity, 0, 1)));
    }
    public static Animations active() { return INSTANCE != null && INSTANCE.isEnabled() ? INSTANCE : null; }
    @Override protected void onDisable() { dev.noxclient.physics.AnimationSystem.clear(); }
    @Override public String status() { return dev.noxclient.physics.AnimationSystem.diagnostics(); }
}
