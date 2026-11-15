package dev.noxclient.module;

import dev.noxclient.module.combat.*;
import dev.noxclient.module.donutsmp.*;
import dev.noxclient.module.misc.*;
import dev.noxclient.module.movement.*;
import dev.noxclient.module.render.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import dev.noxclient.gui.NoxWindowScreen;

import java.util.ArrayList;
import java.util.List;

public class ModuleManager {
    private final List<Module> all = new ArrayList<>();

    public void registerAll() {
        // DonutSMP free
        reg(new AutoSell());
        reg(new AutoEat());
        reg(new AutoVoiceConfirm());
        reg(new SessionStats());
        // DonutSMP premium
        reg(new AHSniper());
        reg(new TunnelBaseFinder());
        reg(new RTPBaseFinder());
        reg(new AntiESPBypass());
        reg(new AntiVanish());
        reg(new AutoTotemOrder());
        reg(new AutoCommander());
        reg(new AutoHarvest());
        reg(new SellWand());
        reg(new ArmorAlert());
        // Finders
        reg(new ChunkFinder());
        reg(new AmethystBaseFinder());
        reg(new ArtificialLightFinder());
        reg(new SusChunkFinder());
        // Combat
        reg(new KillAura());
        reg(new AutoTotem());
        reg(new Criticals());
        reg(new AutoCrystal());
        reg(new AnchorMacro());
        reg(new Velocity());
        reg(new AntiTrap());
        reg(new Triggerbot());
        reg(new SprintReset());
        reg(new AutoMace());
        reg(new ShieldBreak());
        reg(new AutoWeb());
        reg(new AutoPearl());
        reg(new AutoArmor());
        // Movement
        reg(new Speed());
        reg(new Flight());
        reg(new FlyBypass());
        reg(new NoFall());
        reg(new ElytraGlide());
        reg(new NoSlow());
        reg(new InventoryMove());
        reg(new Blink());
        reg(new AutoSprint());
        reg(new StepAssist());
        // Render
        reg(new ESP());
        reg(new PlayerESP());
        reg(new StorageESP());
        reg(new Tracers());
        reg(new CustomSky());
        reg(new Fullbright());
        reg(new Cosmetics());
        reg(new HudWidgets());
        reg(new SpotifyHud());
        reg(new LyricsSky());
        reg(new TargetHUD());
        // Client
        reg(new Panic());
        reg(new StaffNotifier());
        reg(new AntiAFK());
        reg(new CombatLog());
        reg(new AutoReconnect());
        reg(new OpSec());
        reg(new ModSpoofer());
        reg(new AutoTP());
                reg(new WTap());
        reg(new AutoPot());
        reg(new BunnyHop());
        reg(new AutoWalk());
        reg(new AutoMine());
        reg(new ChestStealer());
        reg(new TradeChecker());
        reg(new DropPartyAlert());
        reg(new AutoRespawn());
        reg(new DeathRecap());
        reg(new Spammer());
        reg(new SafeWalk());
        reg(new AntiVoid());
        reg(new FastLadder());
        reg(new ElytraBoost());
        reg(new ElytraSwap());
        reg(new AutoRTP());
        reg(new AutoLogin());
        reg(new TimeChanger());
        reg(new AutoStonks());
        reg(new DiscordNotifier());
        reg(new NoWeather());
        reg(new AntiBlind());
        reg(new AutoKit());
        reg(new AutoSoup());
        reg(new AutoAuction());
        reg(new StashLogger());
        reg(new VisualRange());
        reg(new Friends());
        reg(new AutoGG());
        reg(new Sneak());
        reg(new OreESP());
        reg(new AutoFish());
        reg(new AntiFire());
        reg(new AutoSword());
        reg(new LowTotemAlert());
        reg(new DurabilityAlert());
        reg(new DeathWaypoint());
        reg(new MobAlert());
        reg(new AutoLog());
        reg(new AutoGap());
        reg(new AnchorAlert());
        reg(new HoleESP());
        reg(new Breadcrumbs());
        reg(new AutoXP());
        reg(new RaidAlarm());
        reg(new AutoSleep());
        reg(new KillEffects());
        reg(new Surround());
        reg(new AutoMLG());
        reg(new HighJump());
        reg(new ArmorNotify());
        reg(new YawLock());
        reg(new AutoTPAccept());
        reg(new AutoDaily());
        reg(new AutoJump());
        reg(new BlockInfo());
        reg(new ChunkBorders());
        reg(new WaypointShare());
        reg(new NoHurtCam());
        reg(new FastBreak());
        reg(new AutoTrap());
        reg(new WebAura());
        reg(new Nuker());
        reg(new Zoom());
        reg(new Freecam());
        reg(new AutoRocket());
        reg(new Hitboxes());
        reg(new Scaffold());
        reg(new Refill());
        reg(new AntiAim());
        reg(new Burrow());
        reg(new SelfWeb());
        reg(new AutoBreed());
        reg(new AutoShear());
        reg(new dev.noxclient.module.donutsmp.AutoTool());
        reg(new dev.noxclient.module.donutsmp.FastPlace());
        // TamperGuard is core — runs always, not a module
        reg(new Interface());
        reg(new Animations());
        reg(new GhostMode());
        reg(new DiscordRPCModule());
        reg(new SelfDestruct());
    }

    private void reg(Module m) { all.add(m); }
    public List<Module> all() { return all; }
    public List<Module> by(Category c) { return all.stream().filter(m -> m.category == c).toList(); }
    public Module get(String name) {
        return all.stream().filter(m -> m.name.equalsIgnoreCase(name)).findFirst().orElse(null);
    }
    public void tick(Minecraft mc) {
        for (Module m : all) {
            // keybind: edge-detect toggle, or track key state in hold mode.
            // pressed[] updates unconditionally — while the GUI is open only the
            // action is skipped, otherwise a stale 'down' would eat the next press.
            if (m.keybind() > 0) {
                boolean down = InputConstants.isKeyDown(m.keybind())
                    || FAKE_KEYS.contains(m.keybind());
                if (m.holdMode()) {
                    pressed.put(m, down);
                    // gated premium in Hold mode: setEnabled would re-open the
                    // premium screen every tick without ever enabling
                    if (!NoxWindowScreen.open && down != m.isEnabled() && !(down && m.gated()))
                        m.setEnabled(down);
                } else {
                    Boolean was = pressed.put(m, down);
                    if (!NoxWindowScreen.open && down && was != Boolean.TRUE) m.toggle();
                }
            }
            if (m.isEnabled()) {
                try { m.onTick(); } catch (Throwable t) {
                    m.lastError = String.valueOf(t);
                    m.lastErrorAt = System.currentTimeMillis();
                    if (failed.add(m)) {
                        t.printStackTrace();
                        if (mc.player != null) mc.player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal(
                                "§c[NOX] " + m.name + " error: " + t));
                    }
                }
            }
        }
    }

    private final java.util.WeakHashMap<Module, Boolean> pressed = new java.util.WeakHashMap<>();
    /** Debug/test only — synthetic key states injected by DebugConsole. */
    public static final java.util.Set<Integer> FAKE_KEYS = new java.util.HashSet<>();
    private final java.util.Set<Module> failed = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
}
