"""Embed system + product channel posts (downloads, changelogs, donate, guides)."""
import os
import discord
from discord import app_commands
from discord.ext import commands

PINK = 0xFF6EC7
ICON_PATH = os.path.join(os.path.dirname(__file__), "..", "assets", "icon.png")


def nox_embed(title: str, body: str, *, footer: str = "NOX Client") -> discord.Embed:
    e = discord.Embed(title=title, description=body, colour=PINK)
    e.set_footer(text=footer)
    e.timestamp = discord.utils.utcnow()
    e.set_thumbnail(url="attachment://icon.png")
    return e


def nox_icon() -> discord.File:
    return discord.File(ICON_PATH, filename="icon.png")


class Posts(commands.Cog):
    def __init__(self, bot):
        self.bot = bot

    @app_commands.command(name="post", description="Post a branded embed")
    @app_commands.describe(channel="target", kind="downloads|changelog|donate|guides|rules")
    @app_commands.checks.has_permissions(manage_messages=True)
    async def post(self, it: discord.Interaction, channel: discord.TextChannel, kind: str):
        embeds = {
            "downloads": nox_embed("NOX Downloads",
                "Latest client jar below. Verify the SHA-256 posted in #releases — "
                "modified jars can steal your account."),
            "changelog": nox_embed("Changelog", "See #releases for the full list."),
            "donate": nox_embed("Support NOX",
                "**Premium** — all DonutSMP modules, priority support\n"
                "**Donator** — cosmetics + early builds\n"
                "Link: https://noxclient.dev/donate\n\n"
                "Transparent pricing, no hidden costs. Refunds: see #refund-policy."),
            "guides": nox_embed("Guides", "Setup, keybinds, per-server presets."),
            "rules": nox_embed("Rules",
                "1. Respect others\n2. No scamming/account links\n"
                "3. No external invites\n4. English in public channels"),
        }
        await channel.send(embed=embeds[kind], file=nox_icon())
        await it.response.send_message(f"Posted {kind} in {channel.mention}", ephemeral=True)


async def setup(bot):
    await bot.add_cog(Posts(bot))
