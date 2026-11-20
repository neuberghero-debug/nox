#!/bin/bash
# Spawn/restart the dev client inside tmux (escapes exec-supervisor ptrace
# that suspends the render thread under `gradle runClient`).
# usage: bash tools/mc-tmux.sh [join]
cd "$(dirname "$0")/.."
tmux kill-session -t noxclient 2>/dev/null
sleep 1
tmux new-session -d -s noxclient "cd $PWD/run && WAYLAND_DISPLAY=wayland-0 DISPLAY=:0 XDG_RUNTIME_DIR=/run/user/1000 XAUTHORITY=/run/user/1000/xauth_HsoQAG /var/home/linuxbrew/.linuxbrew/Cellar/openjdk/27.0.1.0.3/libexec/bin/java -Dfabric.dli.config=$PWD/.gradle/loom-cache/launch.cfg -Dfabric.dli.env=client -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient @$PWD/build/loom-cache/argFiles/runClient -XX:StackShadowPages=32 --sun-misc-unsafe-memory-access=allow --enable-native-access=ALL-UNNAMED -Dfile.encoding=UTF-8 net.fabricmc.devlaunchinjector.Main > /tmp/mc-direct.log 2>&1"
for i in $(seq 1 75); do
    python3 tools/noxctl.py run "state" 2>/dev/null | grep -q "screen=" && break
    sleep 1
done
echo "client up (~${i}s)"
python3 tools/noxctl.py run "state" 2>&1 | tail -1
if [ "$1" = "join" ]; then
    python3 tools/noxctl.py run "join localhost" >/dev/null 2>&1
    sleep 14
    python3 tools/noxctl.py run state 2>&1 | tail -1
fi
