#!/usr/bin/env bash
# deploy.sh — install the bot as a systemd service on a Linux VPS.
# Usage: sudo ./deploy.sh
set -euo pipefail
DEST=/opt/nox-community-bot

if ! command -v node >/dev/null; then
  echo "node not found — install Node.js 18+ first (e.g. via nvm or apt)"; exit 1
fi

id noxbot >/dev/null 2>&1 || useradd -r -s /usr/sbin/nologin noxbot
mkdir -p "$DEST"
rsync -a --delete --exclude node_modules --exclude bot.log --exclude .env \
  ./ "$DEST"/
if [ -f ./.env ]; then cp -f ./.env "$DEST/.env"; chmod 600 "$DEST/.env"; fi

cd "$DEST"
npm ci --omit=dev || npm install --omit=dev
chown -R noxbot:noxbot "$DEST"

cp nox-bot.service /etc/systemd/system/nox-bot.service
touch /var/log/nox-bot.log && chown noxbot:noxbot /var/log/nox-bot.log
systemctl daemon-reload
systemctl enable --now nox-bot
sleep 2
systemctl status nox-bot --no-pager -l | head -15
echo "— logs: journalctl -u nox-bot -f"
