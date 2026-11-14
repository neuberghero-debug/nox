"""Canonical NOX invite — /invite creates a permanent, unlimited-use link
(max_age=0, max_uses=0, unique) and caches it in nox-bot/invite.txt.
/discord shows the stored link to anyone."""
import discord
from discord import app_commands
from discord.ext import commands
from pathlib import Path

PINK = 0xFF6EC7
STORE = Path(__file__).resolve().parent.parent / "invite.txt"
ICON = Path(__file__).resolve().parent.parent / "assets" / "icon.png"


def saved() -> str | None:
    if STORE.exists():
        s = STORE.read_text().strip()
        return s or None
    return None


class Invite(commands.Cog):
    def __init__(self, bot): self.bot = bot

    @app_commands.command(name="invite", description="Create the permanent NOX invite (staff)")
    @app_commands.describe(channel="Channel the invite points at (default: this one)")
    @app_commands.checks.has_permissions(manage_guild=True)
    async def invite(self, it: discord.Interaction, channel: discord.TextChannel | None = None):
        ch = channel or it.channel
        try:
            inv = await ch.create_invite(max_age=0, max_uses=0, unique=True,
                                         reason="NOX canonical invite (bot-generated)")
        except discord.Forbidden:
            await it.response.send_message(f"No 'Create Invite' permission in {ch.mention}.", ephemeral=True)
            return
        STORE.write_text(inv.url)
        e = discord.Embed(title="NOX invite created",
                          description=f"**{inv.url}**\npermanent · unlimited uses → {ch.mention}",
                          color=PINK)
        f = None
        if ICON.exists():
            f = discord.File(ICON, filename="nox.png"); e.set_thumbnail(url="attachment://nox.png")
        kw = {"embed": e}
        if f: kw["file"] = f
        await it.response.send_message(**kw)

    @app_commands.command(name="discord", description="Get the NOX Discord invite")
    async def discord_cmd(self, it: discord.Interaction):
        url = saved()
        if not url:
            await it.response.send_message("No invite stored yet — staff runs /invite once.", ephemeral=True)
            return
        e = discord.Embed(title="Join NOX", description=url, color=PINK)
        f = None
        if ICON.exists():
            f = discord.File(ICON, filename="nox.png"); e.set_thumbnail(url="attachment://nox.png")
        kw = {"embed": e}
        if f: kw["file"] = f
        await it.response.send_message(**kw)


async def setup(bot):
    await bot.add_cog(Invite(bot))
