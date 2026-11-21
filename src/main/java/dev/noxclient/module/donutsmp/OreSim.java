package dev.noxclient.module.donutsmp;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.render.NoxRender;
import dev.noxclient.setting.Setting;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.worldgen.placement.OrePlacements;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.BlockReplacement;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.heightproviders.HeightProvider;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Seed-based ore simulation. Replicates the vanilla placement pass
 * (decoration seed → feature seed → height provider → vein math) against the
 * REAL loaded chunk data, so predicted positions respect actual terrain,
 * caves and — optionally — air exposure. Adapted from the OreSim concept,
 * rewritten for 26.3's record-based placement/feature APIs.
 */
public class OreSim extends Module {
    private static OreSim instance;

    /* ---------- settings ---------- */
    private final Setting.Str seed = add(new Setting.Str("Seed", "",
        "World seed — leave empty to auto-detect singleplayer"));
    private final Setting.Num range = add(new Setting.Num("Range (chunks)", 5, 1, 10));
    private final Setting.Mode airCheck = add(Setting.Mode.of("Air check", "Recheck",
        "Recheck removes markers when the block is mined; On load checks once; Off shows all", "Recheck", "On load", "Off"));
    private final Setting.Mode style = add(Setting.Mode.of("Style", "Both",
        "Boxes = outlines only; Filled = translucent fill; Both", "Boxes", "Filled", "Both"));
    private final Setting.Bool xray = add(new Setting.Bool("Through walls", true,
        "Depth-test off — markers visible through terrain"));

    private final Setting.Bool coal     = add(new Setting.Bool("Coal", false));
    private final Setting.Bool iron     = add(new Setting.Bool("Iron", false));
    private final Setting.Bool gold     = add(new Setting.Bool("Gold", true));
    private final Setting.Bool redstone = add(new Setting.Bool("Redstone", true));
    private final Setting.Bool diamond  = add(new Setting.Bool("Diamond", true));
    private final Setting.Bool lapis    = add(new Setting.Bool("Lapis", false));
    private final Setting.Bool copper   = add(new Setting.Bool("Copper", false));
    private final Setting.Bool emerald  = add(new Setting.Bool("Emerald", true));
    private final Setting.Bool quartz   = add(new Setting.Bool("Nether quartz", true));
    private final Setting.Bool debris   = add(new Setting.Bool("Ancient debris", true));

    /* ---------- state ---------- */
    private final Map<Long, Map<OreDef, Map<BlockPos, BlockState>>> chunkOres = new ConcurrentHashMap<>();
    private Map<ResourceKey<Biome>, List<OreDef>> oreConfig;
    private long worldSeed;
    private final NoxRender.Emitter emitter = this::emitGizmos;
    private final Map<OreDef, GizmoStyle> styleCache = new HashMap<>();
    /* auto-verification: sample predictions against real blocks */
    private int verifyChecked, verifyHits;
    private boolean verifyWarned;

    public OreSim() { super("OreSim", "Seed-based ore prediction — needs the world seed", Category.DONUTSMP); instance = this; }

    /* ---------- module lifecycle ---------- */
    @Override protected void onEnable() {
        instance = this;
        if (!resolveSeed()) {
            dev.noxclient.gui.Toasts.push("OreSim: no seed — set it in settings or use /seed", 0xFFFF6666);
            setEnabled(false); return;
        }
        try {
            oreConfig = buildRegistry();
        } catch (Throwable t) {
            dev.noxclient.gui.Toasts.push("OreSim: registry build failed — " + t.getClass().getSimpleName(), 0xFFFF6666);
            lastError = String.valueOf(t); lastErrorAt = System.currentTimeMillis();
            setEnabled(false); return;
        }
        chunkOres.clear(); styleCache.clear();
        verifyChecked = verifyHits = 0; verifyWarned = false;
        NoxRender.add(emitter);
        loadVisibleChunks();
        mark("sim started (seed " + worldSeed + ")");
    }

    @Override protected void onDisable() {
        NoxRender.remove(emitter);
        chunkOres.clear(); oreConfig = null;
    }

    /* ---------- event entry points (called from mixins) ---------- */
    public static void onChunkLoad(LevelChunk chunk) {
        OreSim s = instance; if (s == null || !s.isEnabled() || s.oreConfig == null || chunk == null) return;
        s.doMathOnChunk(chunk);
    }

    public static void onChunkUnload(ChunkPos pos) {
        OreSim s = instance; if (s == null) return;
        s.chunkOres.remove(pos.pack());
    }

    public static void onBlockUpdate(BlockPos pos, BlockState state) {
        OreSim s = instance;
        if (s == null || !s.isEnabled() || state == null) return;
        if (!"Recheck".equals(s.airCheck.get()) || state.canOcclude()) return;
        Map<OreDef, Map<BlockPos, BlockState>> m = s.chunkOres.get(ChunkPos.pack(pos));
        if (m != null) m.values().forEach(set -> set.remove(pos));
    }

    /* ---------- seed ---------- */
    private boolean resolveSeed() {
        String s = seed.get().trim();
        if (!s.isEmpty()) {
            try { worldSeed = Long.parseLong(s); return true; }
            catch (NumberFormatException e) {
                // vanilla hashes text seeds — keep that, but say it out loud:
                // a typo'd seed otherwise produces confidently wrong ores
                worldSeed = s.hashCode();
                dev.noxclient.gui.Toasts.push(
                    "OreSim: seed isn't numeric — using text-seed hash " + worldSeed
                    + " (typo? servers use numeric seeds)", 0xFFFFAA33);
                return true;
            }
        }
        try {
            var server = mc.getSingleplayerServer();
            if (server != null) { worldSeed = server.overworld().getSeed(); return true; }
        } catch (Throwable ignored) {}
        return false;
    }

    /* ---------- ore registry (real vanilla placement data) ---------- */
    private Map<ResourceKey<Biome>, List<OreDef>> buildRegistry() {
        // placed_feature lives ONLY server-side (worldgen registry, not synced).
        // SP: the integrated server's real registry (incl. datapacks).
        // MP: vanilla bootstrap (server's datapack overrides unknowable — best effort).
        ResourceKey<Level> dk = mc.level.dimension();
        var srv = mc.getSingleplayerServer();
        var lvl = srv != null ? srv.getLevel(dk) : null;

        var features = srv != null
            ? srv.registryAccess().lookupOrThrow(Registries.PLACED_FEATURE)
            : VanillaRegistries.createWorldLookup().lookupOrThrow(Registries.PLACED_FEATURE);

        // Biome holders drive FeatureSorter — order matters for the feature index salt.
        // SP: the REAL generator's biome source (identical object vanilla sorts on).
        // MP: client's synced biome registry.
        List<Holder<Biome>> biomeHolders;
        if (lvl != null)
            biomeHolders = new ArrayList<>(
                lvl.getChunkSource().getGenerator().getBiomeSource().possibleBiomes());
        else
            biomeHolders = mc.level.registryAccess().lookupOrThrow(Registries.BIOME)
                .listElements().map(h -> (Holder<Biome>) h).toList();

        List<FeatureSorter.StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(
            biomeHolders, b -> b.value().getGenerationSettings().features(), true);

        Map<PlacedFeature, OreDef> f2o = new HashMap<>();
        // overworld ores — generation step 6
        reg(f2o, steps, features, OrePlacements.ORE_COAL_LOWER, 6, "coal", 0xFF8F8F96);
        reg(f2o, steps, features, OrePlacements.ORE_COAL_UPPER, 6, "coal", 0xFF8F8F96);
        reg(f2o, steps, features, OrePlacements.ORE_IRON_MIDDLE, 6, "iron", 0xFFECAD77);
        reg(f2o, steps, features, OrePlacements.ORE_IRON_SMALL, 6, "iron", 0xFFECAD77);
        reg(f2o, steps, features, OrePlacements.ORE_IRON_UPPER, 6, "iron", 0xFFECAD77);
        reg(f2o, steps, features, OrePlacements.ORE_GOLD, 6, "gold", 0xFFF7E51E);
        reg(f2o, steps, features, OrePlacements.ORE_GOLD_LOWER, 6, "gold", 0xFFF7E51E);
        reg(f2o, steps, features, OrePlacements.ORE_GOLD_EXTRA, 6, "gold", 0xFFF7E51E);
        reg(f2o, steps, features, OrePlacements.ORE_REDSTONE, 6, "redstone", 0xFFF50717);
        reg(f2o, steps, features, OrePlacements.ORE_REDSTONE_LOWER, 6, "redstone", 0xFFF50717);
        reg(f2o, steps, features, OrePlacements.ORE_DIAMOND, 6, "diamond", 0xFF21F4FF);
        reg(f2o, steps, features, OrePlacements.ORE_DIAMOND_BURIED, 6, "diamond", 0xFF21F4FF);
        reg(f2o, steps, features, OrePlacements.ORE_DIAMOND_LARGE, 6, "diamond", 0xFF21F4FF);
        reg(f2o, steps, features, OrePlacements.ORE_DIAMOND_MEDIUM, 6, "diamond", 0xFF21F4FF);
        reg(f2o, steps, features, OrePlacements.ORE_LAPIS, 6, "lapis", 0xFF4466FF);
        reg(f2o, steps, features, OrePlacements.ORE_LAPIS_BURIED, 6, "lapis", 0xFF4466FF);
        reg(f2o, steps, features, OrePlacements.ORE_COPPER, 6, "copper", 0xFFEF9700);
        reg(f2o, steps, features, OrePlacements.ORE_COPPER_LARGE, 6, "copper", 0xFFEF9700);
        reg(f2o, steps, features, OrePlacements.ORE_EMERALD, 6, "emerald", 0xFF1BD12D);
        // nether ores — generation step 7
        reg(f2o, steps, features, OrePlacements.ORE_GOLD_NETHER, 7, "gold", 0xFFF7E51E);
        reg(f2o, steps, features, OrePlacements.ORE_GOLD_DELTAS, 7, "gold", 0xFFF7E51E);
        reg(f2o, steps, features, OrePlacements.ORE_QUARTZ_NETHER, 7, "quartz", 0xFFEDEDED);
        reg(f2o, steps, features, OrePlacements.ORE_QUARTZ_DELTAS, 7, "quartz", 0xFFEDEDED);
        reg(f2o, steps, features, OrePlacements.ORE_ANCIENT_DEBRIS_SMALL, 7, "debris", 0xFFD91BF5);
        reg(f2o, steps, features, OrePlacements.ORE_ANCIENT_DEBRIS_LARGE, 7, "debris", 0xFFD91BF5);

        Map<ResourceKey<Biome>, List<OreDef>> map = new HashMap<>();
        for (var h : biomeHolders) {
            List<OreDef> list = new ArrayList<>();
            h.unwrapKey().ifPresent(k -> map.putIfAbsent(k, list));
            h.value().getGenerationSettings().features().stream()
                .flatMap(HolderSet::stream).map(Holder::value)
                .filter(f2o::containsKey)
                .forEach(f -> list.add(f2o.get(f)));
        }
        return map;
    }

    private void reg(Map<PlacedFeature, OreDef> map, List<FeatureSorter.StepFeatureData> steps,
                     HolderLookup.RegistryLookup<PlacedFeature> features,
                     ResourceKey<PlacedFeature> key, int step, String name, int argb) {
        PlacedFeature pf;
        try { pf = features.getOrThrow(key).value(); }
        catch (Throwable t) { return; }   // key missing in this version — skip
        int index = steps.get(step).indexMapping().applyAsInt(pf);
        map.put(pf, new OreDef(pf, step, index, name, argb, toggle(name)));
    }

    private Setting.Bool toggle(String name) {
        return switch (name) {
            case "coal" -> coal; case "iron" -> iron; case "gold" -> gold;
            case "redstone" -> redstone; case "diamond" -> diamond; case "lapis" -> lapis;
            case "copper" -> copper; case "emerald" -> emerald; case "quartz" -> quartz;
            default -> debris;
        };
    }

    /* ---------- per-ore descriptor ---------- */
    private final class OreDef {
        final int step, index;
        int size;
        float rarity = 1F;
        boolean scattered;
        final String name; final int argb;
        final Setting.Bool active;
        IntProvider count = ConstantInt.of(1);
        HeightProvider heightProvider;
        WorldGenerationContext heightContext;
        AbstractOreFeature feature;
        List<BlockReplacement> targets = List.of();

        OreDef(PlacedFeature pf, int step, int index, String name, int argb, Setting.Bool active) {
            this.step = step; this.index = index; this.name = name; this.argb = argb; this.active = active;
            for (PlacementModifier m : pf.placement()) {
                if (m instanceof CountPlacement c) count = c.count();
                else if (m instanceof HeightRangePlacement h) heightProvider = h.height();
                else if (m instanceof RarityFilter r) rarity = r.chance();
            }
            Feature f = pf.feature().value();
            if (f instanceof AbstractOreFeature of) {
                size = of.size(); feature = of; targets = of.targetStates();
                scattered = f instanceof ScatteredOreFeature;
            } else { size = 4; feature = null; scattered = false; }
            heightContext = WorldGenerationContext.of(mc.level);
        }
    }

    /* ---------- simulation ---------- */
    private void loadVisibleChunks() {
        if (mc.player == null || mc.level == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int vd = mc.options.getEffectiveRenderDistance() + 1;
        for (int dx = -vd; dx <= vd; dx++) for (int dz = -vd; dz <= vd; dz++) {
            if (mc.level.hasChunk(pc.x() + dx, pc.z() + dz))
                doMathOnChunk(mc.level.getChunk(pc.x() + dx, pc.z() + dz));
        }
    }

    private void doMathOnChunk(ChunkAccess chunk) {
        ChunkPos cp = chunk.getPos();
        long key = cp.pack();
        ClientLevel world = mc.level;
        if (chunkOres.containsKey(key) || world == null) return;

        // biome union over the 3x3 area (veins cross borders)
        Set<ResourceKey<Biome>> biomes = new HashSet<>();
        ChunkPos.rangeClosed(cp, 1).forEach(c -> {
            ChunkAccess cc = world.getChunk(c.x(), c.z(), ChunkStatus.BIOMES, false);
            if (cc == null) return;
            for (LevelChunkSection sec : cc.getSections())
                sec.getBiomes().getAll(h -> h.unwrapKey().ifPresent(biomes::add));
        });
        Set<OreDef> oreSet = biomes.stream()
            .flatMap(b -> defaults(b).stream()).collect(Collectors.toSet());
        if (oreSet.isEmpty()) { chunkOres.put(key, Map.of()); return; }

        int baseX = cp.getMinBlockX(), baseZ = cp.getMinBlockZ();
        WorldgenRandom random = new WorldgenRandom(WorldgenRandom.Algorithm.XOROSHIRO.newInstance(0));
        long populationSeed = random.setDecorationSeed(worldSeed, baseX, baseZ);
        Map<OreDef, Map<BlockPos, BlockState>> result = new HashMap<>();

        for (OreDef ore : oreSet) {
            if (!ore.active.on() || ore.heightProvider == null || ore.feature == null) continue;
            random.setFeatureSeed(populationSeed, ore.index, ore.step);
            int repeat = ore.count.sample(random);
            Map<BlockPos, BlockState> found = new HashMap<>();
            for (int i = 0; i < repeat; i++) {
                if (ore.rarity != 1F && random.nextFloat() >= 1F / ore.rarity) continue;
                int x = random.nextInt(16) + baseX;
                int z = random.nextInt(16) + baseZ;
                int y = ore.heightProvider.sample(random, ore.heightContext);
                ResourceKey<Biome> b = chunk.getNoiseBiome(
                    QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))
                    .unwrapKey().orElse(null);
                if (b == null || !defaults(b).contains(ore)) continue;
                BlockPos origin = new BlockPos(x, y, z);
                if (ore.scattered) genScattered(world, random, origin, ore, found);
                else genVein(world, random, origin, ore, found);
            }
            if (!found.isEmpty()) result.put(ore, found);
        }
        chunkOres.put(key, result);
        verify(world, result);
    }

    private List<OreDef> defaults(ResourceKey<Biome> b) {
        List<OreDef> l = oreConfig.get(b);
        return l != null ? l : oreConfig.values().stream().findAny().orElse(List.of());
    }

    /* ---------- vanilla vein math (OreFeature.place reimplementation) ---------- */
    private void genVein(ClientLevel world, WorldgenRandom random, BlockPos pos, OreDef ore,
                         Map<BlockPos, BlockState> out) {
        int veinSize = ore.size;
        float f = random.nextFloat() * (float) Math.PI;
        float g = veinSize / 8.0F;
        int i = Mth.ceil((veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = pos.getX() + Math.sin(f) * g, e = pos.getX() - Math.sin(f) * g;
        double h = pos.getZ() + Math.cos(f) * g, j = pos.getZ() - Math.cos(f) * g;
        double l = pos.getY() + random.nextInt(3) - 2, m = pos.getY() + random.nextInt(3) - 2;
        int n = pos.getX() - Mth.ceil(g) - i, o = pos.getY() - 2 - i, p = pos.getZ() - Mth.ceil(g) - i;
        int q = 2 * (Mth.ceil(g) + i), r = 2 * (2 + i);
        for (int s = n; s <= n + q; ++s) for (int t = p; t <= p + q; ++t)
            if (o <= world.getHeight(Heightmap.Types.MOTION_BLOCKING, s, t)) {
                genVeinPart(world, random, veinSize, d, e, h, j, l, m, n, o, p, q, r, ore, out);
                return;
            }
    }

    private void genVeinPart(ClientLevel world, WorldgenRandom random, int veinSize,
                             double startX, double endX, double startZ, double endZ,
                             double startY, double endY, int x, int y, int z,
                             int size, int i, OreDef ore, Map<BlockPos, BlockState> out) {
        BitSet bits = new BitSet(size * i * size);
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        double[] ds = new double[veinSize * 4];
        for (int n = 0; n < veinSize; ++n) {
            float f = (float) n / veinSize;
            ds[n * 4]     = Mth.lerp(f, startX, endX);
            ds[n * 4 + 1] = Mth.lerp(f, startY, endY);
            ds[n * 4 + 2] = Mth.lerp(f, startZ, endZ);
            double s = random.nextDouble() * veinSize / 16.0;
            ds[n * 4 + 3] = ((Mth.sin((float) Math.PI * f) + 1.0F) * s + 1.0) / 2.0;
        }
        for (int n = 0; n < veinSize - 1; ++n) {
            if (ds[n * 4 + 3] <= 0) continue;
            for (int o = n + 1; o < veinSize; ++o) {
                if (ds[o * 4 + 3] <= 0) continue;
                double p = ds[n * 4] - ds[o * 4], q = ds[n * 4 + 1] - ds[o * 4 + 1],
                       r = ds[n * 4 + 2] - ds[o * 4 + 2], s = ds[n * 4 + 3] - ds[o * 4 + 3];
                if (s * s > p * p + q * q + r * r) { if (s > 0) ds[o * 4 + 3] = -1; else ds[n * 4 + 3] = -1; }
            }
        }
        for (int n = 0; n < veinSize; ++n) {
            double u = ds[n * 4 + 3];
            if (u < 0) continue;
            double v = ds[n * 4], w = ds[n * 4 + 1], aa = ds[n * 4 + 2];
            int ab = Math.max(Mth.floor(v - u), x), ac = Math.max(Mth.floor(w - u), y), ad = Math.max(Mth.floor(aa - u), z);
            int ae = Math.max(Mth.floor(v + u), ab), af = Math.max(Mth.floor(w + u), ac), ag = Math.max(Mth.floor(aa + u), ad);
            for (int ah = ab; ah <= ae; ++ah) {
                double ai = (ah + 0.5 - v) / u;
                if (ai * ai >= 1) continue;
                for (int aj = ac; aj <= af; ++aj) {
                    double ak = (aj + 0.5 - w) / u;
                    if (ai * ai + ak * ak >= 1) continue;
                    for (int al = ad; al <= ag; ++al) {
                        double am = (al + 0.5 - aa) / u;
                        if (ai * ai + ak * ak + am * am >= 1) continue;
                        int an = ah - x + (aj - y) * size + (al - z) * size * i;
                        if (bits.get(an)) continue;
                        bits.set(an);
                        mut.set(ah, aj, al);
                        if (aj >= world.getMinY() && aj < world.getMaxY())
                            tryPlace(world, random, mut, ore, out);
                    }
                }
            }
        }
    }

    /* real vanilla check: RuleTest on the actual block + air-exposure discard.
       Client twist: the server already generated the chunk, so predicted spots may
       already hold the ore block itself — that's a CONFIRMED hit, keep it.
       RNG must stay in sync: vanilla consumed a nextFloat in shouldSkipAirCheck
       for every RuleTest-passing block when 0 < discard < 1 — we consume it too. */
    private void tryPlace(ClientLevel world, WorldgenRandom random, BlockPos.MutableBlockPos pos,
                          OreDef ore, Map<BlockPos, BlockState> out) {
        BlockState cur = world.getBlockState(pos);
        float discard = ore.feature.discardChanceOnAirExposure();
        boolean off = "Off".equals(airCheck.get());
        for (BlockReplacement r : ore.targets) {
            BlockState want = r.state();
            if (cur.getBlock() == want.getBlock()) {            // already-placed ore = confirmed hit
                if (!off && discard > 0F && discard < 1F) random.nextFloat();
                out.putIfAbsent(pos.immutable(), want);
                return;
            }
            if (off) {                                          // show all candidates
                if (r.target().test(cur, pos, random) && discard > 0F && discard < 1F) random.nextFloat();
                out.putIfAbsent(pos.immutable(), want);
                return;
            }
            if (ore.feature.canPlaceOre(cur, world::getBlockState, random, r, pos)) {
                out.putIfAbsent(pos.immutable(), want);
                return;
            }
        }
    }

    private void genScattered(ClientLevel world, WorldgenRandom random, BlockPos pos, OreDef ore,
                              Map<BlockPos, BlockState> out) {
        int size = ore.size;
        int i = random.nextInt(size + 1);
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
        for (int j = 0; j < i; ++j) {
            int s = Math.min(j, 7);
            mut.set(scatterCoord(random, s) + pos.getX(), scatterCoord(random, s) + pos.getY(),
                scatterCoord(random, s) + pos.getZ());
            tryPlace(world, random, mut, ore, out);
        }
    }

    /* ---------- auto-verification: is this world really vanilla terrain? ---------- */
    private void verify(ClientLevel world, Map<OreDef, Map<BlockPos, BlockState>> result) {
        if (verifyChecked >= 24) return;
        List<Map.Entry<BlockPos, BlockState>> flat = new ArrayList<>();
        result.values().forEach(m -> flat.addAll(m.entrySet()));
        if (flat.isEmpty()) return;
        Collections.shuffle(flat, new Random(result.hashCode()));
        for (int i = 0, n = 0; i < flat.size() && n < 3; i++) {
            var e = flat.get(i);
            BlockState real = world.getBlockState(e.getKey());
            if (real.is(net.minecraft.world.level.block.Blocks.VOID_AIR)) continue;   // unloaded — not evidence
            n++; verifyChecked++;
            if (real.getBlock() == e.getValue().getBlock()) verifyHits++;
            else if (verifyChecked - verifyHits <= 6)
                System.out.println("[OreSim-verify] " + e.getKey().toShortString()
                    + " expected=" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(e.getValue().getBlock())
                    + " real=" + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(real.getBlock()));
        }
        if (!verifyWarned && verifyChecked >= 24 && verifyHits * 5 < verifyChecked) {
            verifyWarned = true;
            dev.noxclient.gui.Toasts.push(
                "OreSim: predictions don't match terrain — custom world gen or wrong seed (" + verifyHits + "/" + verifyChecked + " verified)", 0xFFFFAA33);
        }
    }

    private int scatterCoord(WorldgenRandom random, int size) {
        return Math.round((random.nextFloat() - random.nextFloat()) * size);
    }

    /* ---------- rendering ---------- */
    private void emitGizmos() {
        if (mc.player == null || mc.level == null || oreConfig == null) return;
        ChunkPos pc = mc.player.chunkPosition();
        int r = range.intVal();
        boolean boxes = !"Filled".equals(style.get()), fill = !"Boxes".equals(style.get());
        boolean top = xray.on();
        for (var e : chunkOres.entrySet()) {
            int cx = ChunkPos.getX(e.getKey()), cz = ChunkPos.getZ(e.getKey());
            if (Math.max(Math.abs(cx - pc.x()), Math.abs(cz - pc.z())) > r) continue;
            for (var oe : e.getValue().entrySet()) {
                OreDef o = oe.getKey();
                if (!o.active.on()) continue;
                GizmoStyle st = styleCache.computeIfAbsent(o, k ->
                    GizmoStyle.strokeAndFill(k.argb, 1.5f, (k.argb & 0x00FFFFFF) | 0x2E000000));
                for (BlockPos p : oe.getValue().keySet()) {
                    var props = Gizmos.cuboid(new AABB(p), st);
                    if (top) props = props.setAlwaysOnTop();
                }
            }
        }
    }

    /** Nearest simulated ore positions — for debug/verification. */
    public String nearest(Vec3 from, int limit) {
        record E(double d, String s) {}
        List<E> all = new ArrayList<>();
        for (var e : chunkOres.entrySet())
            for (var oe : e.getValue().entrySet())
                for (var pe : oe.getValue().entrySet()) {
                    BlockPos p = pe.getKey();
                    all.add(new E(p.distSqr(BlockPos.containing(from)),
                        oe.getKey().name + " " + p.getX() + " " + p.getY() + " " + p.getZ()
                        + " " + net.minecraft.core.registries.BuiltInRegistries.BLOCK
                            .getKey(pe.getValue().getBlock()).getPath()));
                }
        all.sort(Comparator.comparingDouble(E::d));
        var sb = new StringBuilder();
        for (int i = 0; i < Math.min(limit, all.size()); i++)
            sb.append((int) Math.sqrt(all.get(i).d)).append('m').append(':')
              .append(all.get(i).s).append("  ");
        return sb.isEmpty() ? "none" : sb.toString();
    }

    /** Debug dump: predictions inside chunk (cx,cz), grouped by ore. */
    public String dumpChunk(int cx, int cz) {
        var m = chunkOres.get(ChunkPos.pack(cx, cz));
        if (m == null) return "chunk not simulated";
        var sb = new StringBuilder();
        m.forEach((o, set) -> {
            sb.append(o.name).append('#').append(o.index).append('=').append(set.size()).append('[');
            int i = 0;
            for (var e : set.entrySet()) {
                if (i++ >= 8) { sb.append("…"); break; }
                sb.append(e.getKey().toShortString()).append(' ');
            }
            sb.append("] ");
        });
        return sb.isEmpty() ? "empty" : sb.toString();
    }

    @Override public String status() {
        if (!isEnabled()) return "off";
        if (oreConfig == null) return "no seed";
        int n = 0; for (var m : chunkOres.values()) for (var s : m.values()) n += s.size();
        String v = verifyChecked == 0 ? "" :
            verifyChecked < 24 ? " | verifying " + verifyHits + "/" + verifyChecked :
            verifyHits * 5 >= verifyChecked ? " | VERIFIED " + verifyHits + "/" + verifyChecked :
            " | MISMATCH " + verifyHits + "/" + verifyChecked + " (custom gen/wrong seed)";
        return n + " ores in " + chunkOres.size() + " chunks" + v;
    }
}
