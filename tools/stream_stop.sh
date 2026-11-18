#!/usr/bin/env bash
# NOX stream teardown — kills sidecar + overlay http server.
set -u
cd "$(dirname "$0")/.."
RUN="$(pwd)/run/stream"
QUIET="${1:-}"

for name in sidecar overlay; do
  pidfile="$RUN/$name.pid"
  if [ -f "$pidfile" ]; then
    pid="$(cat "$pidfile")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null
      [ "$QUIET" = "quiet" ] || echo "stopped $name (pid $pid)"
    fi
    rm -f "$pidfile"
  fi
done
# belt & braces — match by cmdline in case the pidfiles went stale
pkill -f "tiktok_sidecar.py" 2>/dev/null && [ "$QUIET" != "quiet" ] && echo "sidecar killed (cmdline)" || true
pkill -f "http.server 8892" 2>/dev/null || true

# nested stream screen (private KWin window on desktop "STREAM")
if [ "${NOX_KEEP_NESTED:-0}" != "1" ]; then
  bash tools/stream/nested_screen.sh stop 2>/dev/null || true
fi
[ "$QUIET" = "quiet" ] || echo "stream env stopped."
