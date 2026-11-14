NOXBot — community Discord bot (separate Node/Python service, NOT in the jar)
============================================================================
Token: read from env `DONUT_BOT_TOKEN` — NEVER hardcode or ship it in the mod.
(The token pasted in chat is compromised; rotate it in the Dev Portal first.)

Responsibilities:
  - relay in-client bug reports / suggestions (webhook) into #reports / #suggestions
  - publish the signed release SHA-256 that TamperGuard verifies against
  - publish the DonutSMP staff-name list that StaffNotifier syncs each session
  - host config-share strings (#config-share channel)
  - announce releases in #releases (the channel the in-client Discord buttons link to)

Minimal Python sketch (discord.py 2.x):

    import os, discord
    bot = discord.Client(intents=discord.Intents.default())
    @bot.event
    async def on_ready():
        print("NOXBot online")
    bot.run(os.environ["DONUT_BOT_TOKEN"])
