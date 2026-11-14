"""Ticket system: /ticket opens a private channel, transcript on close."""
import discord
from discord import app_commands
from discord.ext import commands


class TicketView(discord.ui.View):
    def __init__(self):
        super().__init__(timeout=None)

    @discord.ui.button(label="Close ticket", style=discord.ButtonStyle.danger, custom_id="nox:close")
    async def close(self, it: discord.Interaction, _):
        ch = it.channel
        transcript = "\n".join(
            f"[{m.created_at:%H:%M}] {m.author}: {m.content}"
            async for m in ch.history(limit=500, oldest_first=True))
        log = discord.utils.get(it.guild.text_channels, name="ticket-logs")
        if log:
            await log.send(f"Transcript {ch.name}:\n```\n{transcript[:1900]}\n```")
        await ch.delete(reason="ticket closed")


class Tickets(commands.Cog):
    def __init__(self, bot):
        self.bot = bot

    @app_commands.command(name="ticket", description="Open a support ticket")
    async def ticket(self, it: discord.Interaction):
        overwrites = {
            it.guild.default_role: discord.PermissionOverwrite(view_channel=False),
            it.user: discord.PermissionOverwrite(view_channel=True, send_messages=True),
        }
        staff = discord.utils.get(it.guild.roles, name="Staff")
        if staff:
            overwrites[staff] = discord.PermissionOverwrite(view_channel=True)
        ch = await it.guild.create_text_channel(
            f"ticket-{it.user.name}", overwrites=overwrites,
            category=discord.utils.get(it.guild.categories, name="support"))
        await ch.send(f"{it.user.mention} describe your issue — staff will respond.",
                      view=TicketView())
        await it.response.send_message(f"Ticket opened: {ch.mention}", ephemeral=True)


async def setup(bot):
    bot.add_view(TicketView())
    await bot.add_cog(Tickets(bot))
