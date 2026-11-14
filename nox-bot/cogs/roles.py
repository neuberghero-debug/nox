"""Role management: Premium/Donator/Staff/Tester linked to license keys."""
import os
import discord
from discord import app_commands
from discord.ext import commands

ROLE_NAMES = {"premium": "Premium", "donator": "Donator", "staff": "Staff", "tester": "Tester"}


class Roles(commands.Cog):
    def __init__(self, bot):
        self.bot = bot

    @app_commands.command(name="role", description="Assign a NOX role")
    @app_commands.describe(kind="premium|donator|staff|tester", member="target member")
    @app_commands.checks.has_permissions(manage_roles=True)
    async def role(self, it: discord.Interaction, kind: str, member: discord.Member):
        name = ROLE_NAMES.get(kind.lower())
        role = discord.utils.get(it.guild.roles, name=name)
        if role is None:
            role = await it.guild.create_role(name=name, colour=discord.Colour(0xFF6EC7), hoist=True)
        await member.add_roles(role)
        await it.response.send_message(f"{member.mention} → **{name}**", ephemeral=True)

    @commands.Cog.listener()
    async def on_member_join(self, member: discord.Member):
        ch = discord.utils.get(member.guild.text_channels, name="welcome")
        if ch:
            e = discord.Embed(
                title="Welcome to NOX",
                description=f"{member.mention} — grab the client in #downloads, configs in #config-share.",
                colour=0xFF6EC7)
            e.set_footer(text="NOX Client").timestamp = discord.utils.utcnow()
            await ch.send(embed=e)


async def setup(bot):
    await bot.add_cog(Roles(bot))
