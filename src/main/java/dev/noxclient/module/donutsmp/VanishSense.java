package dev.noxclient.module.donutsmp;

import dev.noxclient.NoxClient;
import dev.noxclient.gui.Toasts;
import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.world.level.block.Block;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * VanishSense (Anubis AntiVanish port, compact): vanished staff still cause
 * real block changes — doors, trapdoors, fence gates, chest/barrel/shulker
 * lids — which the server broadcasts. When such a mechanism flips near us and
 * NO visible player (incl. self) is close enough to have done it, someone
 * invisible is touching things. Weighted hits inside a time window → alert.
 */
public class VanishSense extends Module {
    private final Setting.Num range = add(new Setting.Num("Sensor range", 48, 16, 128));
    private final Setting.Num prox = add(new Setting.Num("Source prox", 6, 2, 16,
        "No visible player this close to the block = invisible source"));
    private final Setting.Num need = add(new Setting.Num("Hits to alert", 2, 1, 8));
    private final Setting.Num window = add(new Setting.Num("Window (s)", 15, 5, 60));
    private final Setting.Num cooldown = add(new Setting.Num("Alert cd (s)", 60, 10, 300));
    private final Setting.Bool ignorePistons = add(new Setting.Bool("Ignore pistons", true,
        "Piston events fire constantly at farms — off only if you want them counted"));

    private final Deque<long[]> hits = new ArrayDeque<>(); // {time, x, y, z}
    private final Map<Long, Long> perPos = new HashMap<>(); // pos -> last hit ms
    private long lastAlert;

    private static VanishSense inst;
    public VanishSense() {
        super("VanishSense", "Detects vanished players via door/chest/mechanism events", Category.DONUTSMP);
        inst = this;
    }

    @Override protected void onEnable() { hits.clear(); perPos.clear(); }
    @Override protected void onDisable() { hits.clear(); perPos.clear(); }

    // ---- static packet taps (SoundSniffMixin) ----

    public static void onBlockUpdate(ClientboundBlockUpdatePacket p) {
        var m = inst; if (m == null || !m.isEnabled()) return;
        var state = p.getBlockState();
        if (state == null) return;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (!mechanism(path)) return;
        m.hit(p.getPos(), path);
    }

    public static void onBlockEvent(ClientboundBlockEventPacket p) {
        var m = inst; if (m == null || !m.isEnabled()) return;
        // chest/ender/trapped/shulker lid + piston events
        Block b = p.getBlock();
        String path = BuiltInRegistries.BLOCK.getKey(b).getPath();
        if (path.contains("piston") && m.ignorePistons.on()) return;
        if (!path.contains("chest") && !path.contains("shulker") && !path.contains("piston")
            && !path.contains("barrel")) return;
        m.hit(p.getPos(), path);
    }

    private static boolean mechanism(String path) {
        return path.contains("door") || path.contains("trapdoor")
            || path.contains("fence_gate") || path.contains("chest")
            || path.contains("barrel") || path.contains("shulker_box");
    }

    private void hit(BlockPos pos, String what) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (pos.distSqr(mc.player.blockPosition()) > range.get() * range.get()) return;
        long now = System.currentTimeMillis();
        Long prev = perPos.get(pos.asLong());
        if (prev != null && now - prev < 2500) return; // same block spam → once
        perPos.put(pos.asLong(), now);

        // any VISIBLE player close to the block = legit source (self included:
        // you standing next to your own chest is not a ghost)
        double pr = prox.get() * prox.get();
        for (var pl : mc.level.players())
            if (pl.blockPosition().distSqr(pos) <= pr) return;

        hits.addLast(new long[]{now, pos.getX(), pos.getY(), pos.getZ()});
        long w = (long) (window.get() * 1000);
        while (!hits.isEmpty() && now - hits.peekFirst()[0] > w) hits.pollFirst();
        if (perPos.size() > 512) perPos.clear();

        if (hits.size() >= need.get() && now - lastAlert > cooldown.get() * 1000) {
            lastAlert = now;
            long[] h = hits.peekLast();
            Toasts.push("VanishSense: " + hits.size() + "x " + what
                + " @ " + h[1] + " " + h[2] + " " + h[3] + " — no visible player", 0xFFFF6666);
        }
    }

    @Override public String status() {
        if (!isEnabled()) return "off";
        return hits.isEmpty() ? "quiet" : hits.size() + " suspicious hits";
    }
}
