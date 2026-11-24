package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.module.donutsmp.SurveyBook.Family;
import dev.noxclient.module.donutsmp.SurveyBook.Point;
import dev.noxclient.module.donutsmp.SurveyBook.Sign;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BaseSurvey extends Module {
    private final Setting.Bool storage = add(new Setting.Bool("Storage", true));
    private final Setting.Bool machinery = add(new Setting.Bool("Machinery", true));
    private final Setting.Bool work = add(new Setting.Bool("Workstations", true));
    private final Setting.Bool lighting = add(new Setting.Bool("Lighting", true));
    private final Setting.Bool distinctive = add(new Setting.Bool("Distinctive block", true,
        "Require a visible shulker box, ender chest, crafter or beacon as well as the count thresholds"));
    private final Setting.Num minimum = add(new Setting.Num("Min distinct blocks", 3, 2, 16));
    private final Setting.Num kinds = add(new Setting.Num("Min families", 2, 1, 4));
    private final Setting.Num radius = add(new Setting.Num("Group radius", 12, 2, 32, "3D distance in blocks from the first visible observation"));
    private final Setting.Num range = add(new Setting.Num("Look distance", 32, 4, 64));
    private final Setting.Num memory = add(new Setting.Num("Remember (s)", 180, 10, 600));
    private final Setting.Bool sweep = add(new Setting.Bool("View sweep", true,
        "Also sample a small grid around your viewing direction, one extra ray per tick"));
    private final Setting.Bool notify = add(new Setting.Bool("Chat notify", true));
    public final Setting.Bool showHud = add(new Setting.Bool("HUD panel", true));
    private final Setting.Num maxRows = add(new Setting.Num("Panel rows", 3, 1, 6));
    private final SurveyBook book = new SurveyBook();
    private final Set<Point> announced = new HashSet<>();
    private List<String> rows = List.of();
    private ClientLevel level;
    private ServerData server;
    private String address;
    private boolean accepted;
    private long tick;
    private String status = "DonutSMP only";

    public BaseSurvey() { super("BaseSurvey", "DonutSMP: groups visible blocks into base candidates", Category.DONUTSMP); }
    public List<String> rows() { return rows; }
    public boolean inScope() { return accepted && level != null && level == mc.level && server == mc.getCurrentServer() && mc.player != null; }
    @Override public String status() { return isEnabled() ? status : "off"; }

    @Override protected void onDisable() { reset(); server = null; level = null; address = null; accepted = false; }
    private void reset() { book.clear(); announced.clear(); rows = List.of(); tick = 0; status = "DonutSMP only"; }

    @Override public void onTick() {
        var current = mc.getCurrentServer();
        String ip = current == null ? null : current.ip;
        if (current != server || level != mc.level || !java.util.Objects.equals(ip, address)) {
            reset(); server = current; address = ip; level = mc.level;
            accepted = SurveyBook.acceptsServer(ip);
        }
        if (!inScope()) return;
        tick++;
        if (mc.gui.screen() == null && mc.player.isAlive() && !mc.player.isSpectator()
                && mc.getCameraEntity() == mc.player && mc.options.getCameraType().isFirstPerson()) {
            sight(mc.player.getXRot(), mc.player.getYRot());
            if (sweep.on()) {
                int cell = (int) (tick % 25);
                if (cell != 12) sight(Math.clamp(mc.player.getXRot() + (cell / 5 - 2) * 3f, -90f, 90f),
                    mc.player.getYRot() + (cell % 5 - 2) * 3f);
            }
        }
        if (tick == 1 || tick % 10 == 0) publish();
    }

    private void sight(float pitch, float yaw) {
        Vec3 from = mc.player.getEyePosition();
        Vec3 end = from.add(Vec3.directionFromRotation(pitch, yaw).scale(range.get()));
        // Do not treat missing terrain between the observer and the target as air.
        var a = BlockPos.containing(from); var b = BlockPos.containing(end);
        if (!level.hasChunksAt(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()),
                Math.max(a.getX(), b.getX()), Math.max(a.getZ(), b.getZ()))) return;
        BlockHitResult hit = level.clip(new ClipContext(from, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK || hit.isInside()) return;
        var p = hit.getBlockPos();
        book.observe(new Point(p.getX(), p.getY(), p.getZ()), classify(level.getBlockState(p).getBlock()), tick);
    }
    private static Sign classify(Block b) {
        if (b instanceof ShulkerBoxBlock || b == Blocks.ENDER_CHEST) return new Sign(Family.STORAGE, true);
        if (b == Blocks.CHEST || b == Blocks.TRAPPED_CHEST || b == Blocks.BARREL) return new Sign(Family.STORAGE, false);
        if (b == Blocks.CRAFTER) return new Sign(Family.MACHINERY, true);
        if (b == Blocks.HOPPER || b == Blocks.COMPARATOR || b == Blocks.REPEATER || b == Blocks.OBSERVER
            || b == Blocks.DISPENSER || b == Blocks.DROPPER) return new Sign(Family.MACHINERY, false);
        if (b == Blocks.BEACON) return new Sign(Family.WORKSTATION, true);
        if (b == Blocks.FURNACE || b == Blocks.SMOKER || b == Blocks.BLAST_FURNACE || b == Blocks.CRAFTING_TABLE)
            return new Sign(Family.WORKSTATION, false);
        if (b == Blocks.TORCH || b == Blocks.WALL_TORCH || b == Blocks.LANTERN || b == Blocks.SOUL_TORCH
            || b == Blocks.SOUL_WALL_TORCH || b == Blocks.SOUL_LANTERN || b == Blocks.REDSTONE_LAMP) return new Sign(Family.LIGHT, false);
        return null;
    }
    private void publish() {
        var enabled = EnumSet.noneOf(Family.class);
        if (storage.on()) enabled.add(Family.STORAGE);
        if (machinery.on()) enabled.add(Family.MACHINERY);
        if (work.on()) enabled.add(Family.WORKSTATION);
        if (lighting.on()) enabled.add(Family.LIGHT);
        var found = book.candidates(tick, 20 * memory.intVal(), radius.intVal(), enabled,
            minimum.intVal(), kinds.intVal(), distinctive.on());
        Set<Point> current = new HashSet<>();
        var lines = new ArrayList<String>();
        for (var f : found) {
            var p = f.anchor(); current.add(p);
            String coords = p.x() + " " + p.y() + " " + p.z();
            if (lines.size() < maxRows.intVal()) lines.add(coords + " | " + f.blocks() + " blocks, " + f.families().size() + " kinds");
            if (announced.add(p) && notify.on()) mc.player.sendSystemMessage(Component.literal(
                "[NOX] Candidate, not verified: " + coords + " | " + f.blocks() + " visible blocks " + f.families()));
        }
        announced.retainAll(current);
        status = enabled.size() < kinds.intVal() ? "Enable more signal families" : found.isEmpty() ? "No candidates" : found.size() + " candidates";
        rows = List.copyOf(lines);
    }
}
