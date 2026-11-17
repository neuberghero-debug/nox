"""Modul 28 — roadmap sync: posts the master-upgrade roadmap as a branded
embed into a configured channel and keeps it updated when docs/roadmap.json
changes (mtime poll every 30 s; the posted message is edited in place).

Config: NOX_ROADMAP_CHANNEL env var (channel id) or /roadmap channel:… command.
State (message id) in nox-bot/data/roadmap.json.
"""
import json
import os
from pathlib import Path

import discord
from discord import app_commands
from discord.ext import commands, tasks

DATA = Path(__file__).parent.parent / "data" / "roadmap.json"
ROADMAP_SRC = Path(__file__).parent.parent.parent / "docs" / "roadmap.json"
STATE = {"channel": int(os.environ.get("NOX_ROADMAP_CHANNEL", "0")),
         "message": 0}
_mark = {"mtime": 0.0}
if DATA.exists():
    try: STATE.update(json.loads(DATA.read_text()))
    except Exception: pass


def save_state():
    DATA.parent.mkdir(parents=True, exist_ok=True)
    DATA.write_text(json.dumps(STATE))


def load_roadmap():
    try:
        return json.loads(ROADMAP_SRC.read_text())
    except Exception:
        return {"title": "NOX roadmap", "modules": []}


def build_embed() -> discord.Embed:
    r = load_roadmap()
    mods = r.get("modules", [])
    done = sum(1 for m in mods if m.get("status") == "done")
    wip = sum(1 for m in mods if m.get("status") == "wip")
    e = discord.Embed(
        title=f"🗺  {r.get('title', 'NOX roadmap')}",
        description=f"**{done}/{len(mods)} done** · {wip} in progress\n"
                    "Live-synced from the repo.",
        color=0x7A2BE2)
    icon = {"done": "✅", "wip": "🟡", "open": "⬜"}
    lines = [f"{icon.get(m.get('status'),'⬜')} **{m['id']}** — {m['name']}"
             + (f" *({m['note']})*" if m.get("note") else "")
             for m in mods]
    # two columns: split at midpoint
    half = (len(lines) + 1) // 2
    e.add_field(name="modules", value="\n".join(lines[:half]) or "—", inline=True)
    e.add_field(name="\u200b", value="\n".join(lines[half:]) or "\u200b", inline=True)
    e.set_footer(text=f"updated {discord.utils.utcnow().strftime('%Y-%m-%d %H:%M')} UTC")
    return e


class Roadmap(commands.Cog):
    def __init__(self, bot):
        self.bot = bot
        self.sync.start()

    def cog_unload(self):
        self.sync.cancel()

    @app_commands.command(name="roadmap", description="Post/sync the roadmap embed")
    @app_commands.describe(channel="target channel (defaults to configured)")
    @app_commands.checks.has_permissions(manage_messages=True)
    async def roadmap(self, it: discord.Interaction,
                    channel: discord.TextChannel = None):
        ch = channel or self.bot.get_channel(STATE["channel"])
        if ch is None:
            return await it.response.send_message("no channel", ephemeral=True)
        msg = await ch.send(embed=build_embed())
        STATE.update(channel=ch.id, message=msg.id)
        save_state()
        _mark["mtime"] = ROADMAP_SRC.stat().st_mtime
        await it.response.send_message(f"roadmap posted in {ch.mention}", ephemeral=True)

    @tasks.loop(seconds=30)
    async def sync(self):
        try:
            if not STATE["message"] or not STATE["channel"]:
                return
            mtime = ROADMAP_SRC.stat().st_mtime
            if mtime == _mark.get("mtime"):
                return
            _mark["mtime"] = mtime
            ch = self.bot.get_channel(STATE["channel"])
            if ch is None:
                return
            msg = await ch.fetch_message(STATE["message"])
            await msg.edit(embed=build_embed())
        except Exception:
            pass

    @sync.before_loop
    async def before(self):
        await self.bot.wait_until_ready()


async def setup(bot):
    await bot.add_cog(Roadmap(bot))
