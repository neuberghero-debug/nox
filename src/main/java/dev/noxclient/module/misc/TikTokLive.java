package dev.noxclient.module.misc;

import dev.noxclient.module.Category;
import dev.noxclient.module.Module;
import dev.noxclient.setting.Setting;
import dev.noxclient.stream.GiftCombos;
import dev.noxclient.stream.RuleEngine;
import dev.noxclient.stream.StreamChat;
import dev.noxclient.stream.TikTokBridge;
import dev.noxclient.stream.ViewerVotes;

import java.util.List;

/**
 * TikTok Live integration (Module 1/3/4 wrapper).
 * Connects the local sidecar bridge, feeds events through the rule
 * engine, combo tracker, vote parser and the in-game stream chat HUD.
 * TEST MODE defaults ON — actions are simulated to toasts until
 * explicitly armed.
 */
public class TikTokLive extends Module {
    public final Setting.Str username = add(new Setting.Str("TikTok user", ""));
    public final Setting.Bool testMode = add(new Setting.Bool("Test mode", true));
    public final Setting.Bool combos = add(new Setting.Bool("Combos", true));
    public final Setting.Bool votes = add(new Setting.Bool("Viewer votes", true));
    public final Setting.Str rules = add(new Setting.Str("Rules",
        "gift=Rose|jump\ntype=FOLLOW|title:Welcome %u!\ncoins>=100|sound"));
    public final Setting.Bool chatHud = add(new Setting.Bool("Stream chat HUD", true));
    public final Setting.Num chatLines = add(new Setting.Num("Chat lines", 6, 2, 12));
    public final Setting.Bool dcStrip = add(new Setting.Bool("Discord strip", true));
    public final Setting.Num posX = add(new Setting.Num("Chat X", 4, 0, 2000));
    /** 0 = auto (above the hotbar); >0 = absolute gui Y. */
    public final Setting.Num posY = add(new Setting.Num("Chat Y", 0, 0, 1200));

    private List<RuleEngine.Rule> parsed = List.of();
    private String lastRules = "";
    private final TikTokBridge.Listener listener = this::onEvent;

    public TikTokLive() { super("TikTokLive", "TikTok gifts → in-game actions (test mode default)", Category.MISC); premium = true; }

    @Override
    protected void onEnable() {
        TikTokBridge.addListener(listener);
        TikTokBridge.connect();
    }

    @Override
    protected void onDisable() {
        TikTokBridge.removeListener(listener);
        TikTokBridge.disconnect();
        ViewerVotes.stop();
    }

    private void onEvent(TikTokBridge.Event e) {
        StreamChat.add(e);
        if (votes.on()) {
            int opt = ViewerVotes.parseVote(e.giftName());   // comments arrive via giftName/comment field
            if (opt > 0) ViewerVotes.vote(e.user(), opt, tier(e));
        }
        if (combos.on() && "GIFT".equals(e.type())) {
            int tier = GiftCombos.onGift(e);
            if (tier >= 3) dev.noxclient.gui.Toasts.push(
                "COMBO T" + tier + " — " + GiftCombos.live(), 0xFFFF6EC7);
        }
        RuleEngine.fire(e, parsed);
    }

    private static int tier(TikTokBridge.Event e) {
        long c = e.coinValue();
        return c >= 1000 ? 4 : c >= 100 ? 3 : c >= 10 ? 2 : c > 0 ? 1 : 0;
    }

    @Override
    public void onTick() {
        RuleEngine.testMode = testMode.on();
        RuleEngine.pump();
        TikTokBridge.tick();
        ViewerVotes.tick();
        String r = rules.get();
        if (!r.equals(lastRules)) { lastRules = r; parsed = RuleEngine.parse(r); }
    }

    /** HUD line for the TikTok counter (M16.6). */
    public static String hudLine() {
        return TikTokBridge.connected()
            ? "TTK " + TikTokBridge.received() + " events · " + GiftCombos.live()
            : "TTK offline";
    }
}
