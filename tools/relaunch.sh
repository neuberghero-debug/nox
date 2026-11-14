#!/bin/bash
# relaunch.sh — kill dev MC, rebuild, relaunch, wait until the client is at the
# title screen, then optionally auto-join the test server.
# Usage: bash tools/relaunch.sh [join]
cd "$(dirname "$0")/.."
pkill -f devlaunchinjector 2>/dev/null
pkill -f "gradle-cli.*runClient" 2>/dev/null
sleep 2
gradle compileJava -q --console=plain || { echo "compile failed"; exit 1; }
nohup gradle runClient --console=plain > /tmp/mc-run.log 2>&1 &
echo "booting…"
for i in $(seq 1 90); do
    if grep -q "NOX-RPC\|Realms server" /tmp/mc-run.log 2>/dev/null; then break; fi
    sleep 1
done
echo "client up (~${i}s)"
if [ "$1" = "join" ]; then
    python3 tools/noxctl.py run "join localhost" >/dev/null
    sleep 14
    python3 tools/noxctl.py run state
fi
