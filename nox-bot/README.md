# NOXBot

Setup:
    python3 -m venv .venv && ./.venv/bin/pip install -r requirements.txt
    cp .env.example .env   # paste your rotated bot token + guild id
    ./.venv/bin/python bot.py

Bot needs: Manage Roles, Manage Channels, Manage Messages, Moderate Members,
Send Messages, Embed Links + Server Members intent enabled in the dev portal.

Slash commands: /role /ticket /timeout /post /issue /buy
Licenses: `/issue @user <days>` signs a `NOX1.<payload>.<sig>` Ed25519 token
with `keys/license_private.pem` (gitignored) — the client verifies it fully
offline against the public key embedded in `premium/LicenseManager`.
`/buy` shows accepted coins from the `NOX_WALLETS` env JSON
({"BTC": "...", "LTC": "...", "ETH": "...", "SOL": "..."}).
