# NOX Community Bot

Discord server bot for the NOX client community — verify panel, ticket system
(12 topics, transcripts), self-roles, notification pings, lock/unlock, member
counters, stickers, welcome messages.

## Setup

```bash
cp .env.example .env    # fill in DISCORD_TOKEN + GUILD_ID
npm ci
node index.js           # or ./start.sh for the supervisor loop
```

Invite + panels: <https://discord.gg/tewtZTsZf5>

## VPS / 24-7 hosting

```bash
sudo ./deploy.sh        # installs to /opt/nox-community-bot + systemd unit
```

- Service: `nox-bot.service` — `Restart=always`, starts on boot, unprivileged
  `noxbot` user, logs to `journalctl -u nox-bot -f` and `/var/log/nox-bot.log`.
- No systemd? `./start.sh` keeps a bash supervisor loop alive (nohup + restart
  on crash); logs to `bot.log`.

## Config

`config.json` holds guild/channel/role IDs. Secrets never live there —
`DISCORD_TOKEN` and `GUILD_ID` come from `.env` (or real env vars, which win).
Panel/ticket state persists in `data/state.json`; panel banners + emojis in
`assets/`.
