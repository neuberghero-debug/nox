"""Auto-mod: spam, invite links, caps abuse + mute/timeout commands."""
import re
import time
import discord
from discord import app_commands
from discord.ext import commands

MSG_WINDOW = {}
INVITE = re.compile(r"(discord\.gg/|discord\.com/invite/)(?!nox\b)", re.I)


class Moderation(commands.Cog):
    def __init__(self, bot):
        self.bot = bot

    @commands.Cog.listener()
    async def on_message(self, m: discord.Message):
        if m.author.bot or not m.guild:
            return
        # anti-spam: >5 msgs / 4s → timeout
        times = MSG_WINDOW.setdefault(m.author.id, [])
        times.append(time.time())
        times[:] = [t for t in times if time.time() - t < 4]
        if len(times) > 5:
            await m.author.timeout(discord.utils.timedelta(minutes=5), reason="spam")
            return
        # external invite links
        if INVITE.search(m.content):
            await m.delete()
            await m.channel.send(f"{m.author.mention} no external invites.", delete_after=8)
            return
        # caps abuse
        if len(m.content) > 12 and m.content.isupper():
            await m.delete()
            return

    @app_commands.command(name="timeout", description="Timeout a member")
    @app_commands.checks.has_permissions(moderate_members=True)
    async def timeout(self, it: discord.Interaction, member: discord.Member, minutes: int):
        await member.timeout(discord.utils.timedelta(minutes=minutes))
        await it.response.send_message(f"{member.mention} timed out {minutes}m", ephemeral=True)


async def setup(bot):
    await bot.add_cog(Moderation(bot))
