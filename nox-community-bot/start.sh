#!/usr/bin/env bash
# start.sh — keep the NOX community bot alive (Linux VPS / any bash host).
# For real 24/7 hosting prefer systemd: sudo ./deploy.sh
cd "$(dirname "$0")" || exit 1
mkdir -p data

if pgrep -f "node index.js" > /dev/null 2>&1; then
  echo "already running (pid $(pgrep -f 'node index.js' | head -1))"
  exit 0
fi

if [ ! -d node_modules/discord.js ]; then
  echo "installing deps…"
  npm ci --omit=dev || npm install --omit=dev
fi

nohup bash -c '
  cd "'"$PWD"'" || exit 1
  while :; do
    node index.js >> bot.log 2>&1
    echo "[supervisor] exited $? – restarting in 5s" >> bot.log
    sleep 5
  done
' > /dev/null 2>&1 &
sleep 1
echo "started (pid $(pgrep -f 'node index.js' | head -1)) — logs: tail -f bot.log"
