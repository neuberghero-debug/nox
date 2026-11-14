"""Server + bot branding: apply the shared NOX icon (assets/icon.png)."""
import os
import discord
from discord import app_commands
from discord.ext import commands

ICON_PATH = os.path.join(os.path.dirname(__file__), "..", "assets", "icon.png")


class Branding(commands.Cog):
    def __init__(self, bot):
        self.bot = bot

    @app_commands.command(name="brand", description="Set bot avatar + server icon to the NOX logo")
    @app_commands.checks.has_permissions(manage_guild=True)
    async def brand(self, it: discord.Interaction):
        await it.response.defer(ephemeral=True)
        with open(ICON_PATH, "rb") as f:
            png = f.read()
        done = []
        try:
            await self.bot.user.edit(avatar=png)
            done.append("bot avatar")
        except discord.HTTPException as e:
            done.append(f"avatar failed: {e.text or e.status}")
        try:
            await it.guild.edit(icon=png)
            done.append("server icon")
        except discord.Forbidden:
            done.append("server icon failed: bot lacks Manage Server")
        except discord.HTTPException as e:
            done.append(f"server icon failed: {e.text or e.status}")
        await it.followup.send("Done: " + ", ".join(done))


async def setup(bot):
    await bot.add_cog(Branding(bot))
