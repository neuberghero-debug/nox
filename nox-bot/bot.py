"""NOXBot — community bot for the NOX client Discord."""
import os
import discord
from discord.ext import commands
from dotenv import load_dotenv

load_dotenv()

INTENTS = discord.Intents.default()
INTENTS.members = True
INTENTS.message_content = True

bot = commands.Bot(command_prefix="!", intents=INTENTS)

PINK = 0xFF6EC7  # NOX brand color


@bot.event
async def on_ready():
    for cog in ("roles", "tickets", "moderation", "posts", "branding", "licenses", "invite"):
        try:
            await bot.load_extension(f"cogs.{cog}")
            print(f"[NOXBot] loaded {cog}")
        except Exception as e:
            print(f"[NOXBot] FAILED {cog}: {e}")
    try:
        await bot.tree.sync()
        print("[NOXBot] slash commands synced")
    except Exception as e:
        print(f"[NOXBot] sync failed: {e}")
    print(f"[NOXBot] online as {bot.user}")


if __name__ == "__main__":
    token = os.environ["DONUT_BOT_TOKEN"]
    bot.run(token)
