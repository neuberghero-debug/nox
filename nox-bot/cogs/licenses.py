"""NOX premium license issuing — crypto payment flow.

/issue (staff): signs a NOX1.<payload>.<sig> Ed25519 token with the private
key in keys/license_private.pem and hands it to the buyer — the client
verifies it fully offline against the embedded public key.
/buy: shows accepted coins + instructions. Wallet addresses come from env
NOX_WALLETS as JSON: {"BTC": "...", "LTC": "...", "ETH": "...", "SOL": "..."}
"""
import base64
import json
import os
import secrets
import time
from pathlib import Path

import discord
from discord import app_commands
from discord.ext import commands
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey

PINK = 0xFF6EC7
ICON = Path(__file__).resolve().parent.parent / "assets" / "icon.png"
KEY = Path(__file__).resolve().parent.parent / "keys" / "license_private.pem"

_priv = None


def _key() -> Ed25519PrivateKey:
    global _priv
    if _priv is None:
        _priv = serialization.load_pem_private_key(KEY.read_bytes(), password=None)
    return _priv


def issue_token(days: int, sub: str) -> str:
    lid = secrets.token_hex(4)
    exp = int(time.time() * 1000) + days * 86400_000
    payload = json.dumps({
        "tier": "premium",
        "lid": lid,
        "exp": exp,
        "sub": sub,
    }, separators=(",", ":")).encode()
    sig = _key().sign(payload)
    _register(lid, exp)
    return ("NOX1." + base64.urlsafe_b64encode(payload).decode()
            + "." + base64.urlsafe_b64encode(sig).decode())


def _register(lid: str, exp: int) -> None:
    """Register the lid with the license backend (revocation support).
    Optional — set NOX_LICENSE_API + NOX_ADMIN_TOKEN; silently skipped offline."""
    api = os.environ.get("NOX_LICENSE_API", "")
    tok = os.environ.get("NOX_ADMIN_TOKEN", "")
    if not api or not tok:
        return
    try:
        import urllib.request
        req = urllib.request.Request(
            api + "/admin/register",
            data=json.dumps({"lid": lid, "exp": exp}).encode(),
            headers={"Content-Type": "application/json", "x-admin-token": tok})
        urllib.request.urlopen(req, timeout=4).read()
    except Exception:
        pass


def _embed(title: str, desc: str) -> tuple[discord.Embed, discord.File | None]:
    e = discord.Embed(title=title, description=desc, color=PINK)
    f = None
    if ICON.exists():
        f = discord.File(ICON, filename="nox.png")
        e.set_thumbnail(url="attachment://nox.png")
    return e, f


class Licenses(commands.Cog):
    def __init__(self, bot): self.bot = bot

    @app_commands.command(name="issue", description="Issue a NOX premium license (staff)")
    @app_commands.describe(user="Buyer", days="License duration in days")
    @app_commands.checks.has_permissions(manage_guild=True)
    async def issue(self, it: discord.Interaction, user: discord.Member, days: int = 365):
        if not KEY.exists():
            await it.response.send_message("license_private.pem missing on the bot host.", ephemeral=True)
            return
        token = issue_token(days, str(user.id))
        e, f = _embed("NOX Premium License",
                      f"For {user.mention} · **{days}d**\n```{token}```\n"
                      "In-game: open a premium module → paste → Activate.")
        kwargs = {"embed": e, "ephemeral": True}
        if f: kwargs["file"] = f
        await it.response.send_message(**kwargs)

    @app_commands.command(name="buy", description="How to buy NOX Premium with crypto")
    async def buy(self, it: discord.Interaction):
        try:
            wallets = json.loads(os.environ.get("NOX_WALLETS", "{}"))
        except json.JSONDecodeError:
            wallets = {}
        lines = [f"**{k}**\n`{v}`" for k, v in wallets.items()] or \
            ["Open a ticket — staff will send you the current payment addresses."]
        e, f = _embed("Buy NOX Premium",
                      "Accepted: **BTC · LTC · ETH · SOL**\n\n" + "\n".join(lines) +
                      "\n\nAfter payment is confirmed you get a `NOX1.` key instantly — "
                      "paste it in-game (premium module → Activate).")
        kwargs = {"embed": e, "ephemeral": True}
        if f: kwargs["file"] = f
        await it.response.send_message(**kwargs)


async def setup(bot):
    await bot.add_cog(Licenses(bot))
