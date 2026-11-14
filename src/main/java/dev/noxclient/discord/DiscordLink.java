package dev.noxclient.discord;

import dev.noxclient.NoxClient;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the community invite. The bot's /invite command generates the
 * canonical permanent link — paste it into nox/discord.txt in the game dir
 * (one line, e.g. https://discord.gg/AbCdEf) and every menu, popup, RPC
 * button and chat mention picks it up without a rebuild. Falls back to the
 * compiled constant until then.
 */
public final class DiscordLink {
    private static volatile String cached;

    public static String get() {
        String c = cached;
        if (c == null) {
            c = NoxClient.DISCORD_INVITE;
            try {
                Path p = Minecraft.getInstance().gameDirectory.toPath().resolve("nox/discord.txt");
                if (Files.exists(p)) {
                    String s = Files.readString(p).trim();
                    if (s.startsWith("http")) c = s;
                }
            } catch (Exception ignored) {}
            cached = c;
        }
        return c;
    }

    /** Short label for display (strips protocol). */
    public static String shortLabel() {
        return get().replaceFirst("^https?://(www\\.)?", "");
    }

    /** Deep link into a channel of the NOX guild (discord app + browser both open it). */
    public static String channelUrl(String channelId) {
        return "https://discord.com/channels/" + GUILD_ID + "/" + channelId;
    }

    /** NOX guild + general channel ids (source of truth: nox-community-bot/config.json). */
    public static final String GUILD_ID = "1570135653826433086";
    public static final String CH_GENERAL = "1570449433114575001";
}
