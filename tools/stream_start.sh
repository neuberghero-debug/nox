#!/usr/bin/env bash
# NOX TikTok dev-stream — full environment bring-up (one command).
#
#   sidecar   TikTokLive(@jbbbbbrk) → ws://127.0.0.1:8890  (client + overlay feed)
#   overlay   http://127.0.0.1:8892                        (OBS browser source)
#   profile   discord.txt + stream presets → Modrinth NOX profile
#
# Usage: tools/stream_start.sh [tiktok_user]
# Stop:  tools/stream_stop.sh
set -u
cd "$(dirname "$0")/.."
ROOT="$(pwd)"

STREAM_USER="${1:-${NOX_TIKTOK_USER:-jbbbbbrk}}"
DISCORD="${NOX_DISCORD:-https://discord.gg/tewtZTsZf5}"
PROFILE="${NOX_PROFILE:-$HOME/.var/app/com.modrinth.ModrinthApp/data/ModrinthApp/profiles/NOX}"
VENV="$ROOT/tools/stream/.venv"
RUN="$ROOT/run/stream"
WS_PORT=8890
OVERLAY_PORT=8892

mkdir -p "$RUN"
echo "== NOX stream env — @${STREAM_USER} =="

# ---- 1. python deps ------------------------------------------------------
[ -x "$VENV/bin/python" ] || { echo "== venv =="; python3 -m venv "$VENV"; }
"$VENV/bin/python" -c "import TikTokLive, websockets" 2>/dev/null \
  || { echo "== pip: TikTokLive + websockets =="; "$VENV/bin/pip" install --quiet TikTokLive websockets; }

# ---- 2. stop leftovers ---------------------------------------------------
bash tools/stream_stop.sh quiet 2>/dev/null || true

# ---- 3. sidecar ----------------------------------------------------------
NOX_TIKTOK_USER="$STREAM_USER" NOX_TIKTOK_WS_PORT=$WS_PORT \
  nohup "$VENV/bin/python" tools/tiktok_sidecar.py > "$RUN/sidecar.log" 2>&1 &
echo $! > "$RUN/sidecar.pid"
sleep 1
echo "== sidecar  pid $(cat "$RUN/sidecar.pid")  →  ws://127.0.0.1:$WS_PORT  (@$STREAM_USER)"

# ---- 4. overlay ----------------------------------------------------------
nohup "$VENV/bin/python" -m http.server $OVERLAY_PORT \
  --directory tools/stream/overlay > "$RUN/overlay.log" 2>&1 &
echo $! > "$RUN/overlay.pid"
echo "== overlay  pid $(cat "$RUN/overlay.pid")  →  http://127.0.0.1:$OVERLAY_PORT"

# ---- 5. profile files ----------------------------------------------------
python3 tools/stream/apply_preset.py --profile "$PROFILE" --user "$STREAM_USER" --discord "$DISCORD"

# ---- 6. nested stream screen (private 1080x1920 KWin window) ---------------
echo "== nested stream screen =="
bash tools/stream/nested_screen.sh start
bash tools/stream/nested_screen.sh apps

# ---- 7. Minecraft inside the nested screen --------------------------------
if [ "${NOX_SKIP_MC:-0}" != "1" ]; then
    echo "== minecraft (nested, auto-docked to GAME zone) =="
    bash -c "set -a; . /tmp/nox-nested/env; set +a;
        nohup bash '$ROOT/tools/relaunch.sh' join > '$RUN/mc-launch.log' 2>&1 &"
fi

# ---- 8. obs scene collection + profile (manual: tools/stream/make_scene.py)
# python3 tools/stream/make_scene.py "$STREAM_USER" >/dev/null \
#   && echo "== obs scene NOX-TikTok + profile regenerated"

cat <<EOF

== OBS checklist ==
  Profile/Collection NOX-TikTok aktiv (global.ini). Szene "Dev Stream":
    STREAM — Nested Screen → 1× Fenster „KDE-Wayland-Compositor WL-0" im
    Portal-Dialog wählen (Haken „Wiederherstellung" an → danach nie wieder).
    MC/Terminal laufen im privaten KWin-Fenster (Desktop „STREAM") —
    Relaunches docken automatisch in die Zonen, Capture bleibt stabil.
  Overlay-URL: http://127.0.0.1:$OVERLAY_PORT/?layout=split&user=$STREAM_USER&task=<task>
  Layout: DEV groß oben · MC mitte · VOTE-Board (fixes/features) · Chat · DC+GitHub
== Stream-Key (<1000 Follower) ==
  Agency/Creator-Network nötig → Key kommt aus livecenter.tiktok.com/producer
  Paste: Settings→Stream (Service: Custom) — siehe tools/stream/OBS_SETUP.md
== In-game ==
  Join eine Welt — Preset aktiviert TikTokLive + StreamerMode; Chat-Panel
  unten links, Gifts feuern echte Actions. Features-Liste: tools/stream/overlay/features.json
  logs: $RUN/sidecar.log · $RUN/overlay.log
EOF
