#!/usr/bin/env bash
# NOX stream nested screen — private 1080x1920 KWin compositor in one window.
# Everything stream-related lives inside; the real desktop stays untouched.
# Usage: nested_screen.sh [start|env|apps|layout|stop]
set -euo pipefail

RUN=/tmp/nox-nested
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
W=1080; H=1920
BUS() { busctl --address="$(cat $RUN/bus)" "$@"; }

die(){ echo "!! $*" >&2; exit 1; }

case "${1:-start}" in
start)
    if [ -f "$RUN/kwin.pid" ] && kill -0 "$(cat $RUN/kwin.pid)" 2>/dev/null; then
        echo "nested already running (pid $(cat $RUN/kwin.pid))"; "$0" env; exit 0
    fi
    mkdir -p "$RUN"
    # kill leftovers from a crashed/stale instance (pidfile only holds the
    # dbus-run-session wrapper; the actual kwin is its child)
    pkill -f "kwin_wayland.*output-count" 2>/dev/null && sleep 1 || true
    ls "$XDG_RUNTIME_DIR"/wayland-* 2>/dev/null | sort > "$RUN/wl.before" || true
    ls /tmp/.X11-unix/ 2>/dev/null | sort > "$RUN/x.before" || true

    # private bus so the nested kwin never collides with the real org.kde.KWin;
    # own XDG_CONFIG_HOME => window rules (zone docking) are scoped to nested only
    dbus-run-session -- bash -c '
        echo "$DBUS_SESSION_BUS_ADDRESS" > "'$RUN'/bus"
        XDG_CONFIG_HOME="'"$HERE/nested-config"'" \
        exec kwin_wayland --width '$W' --height '$H' --scale 1 \
            --output-count 1 --xwayland --no-lockscreen \
            > "'$RUN'/kwin.log" 2>&1
    ' &
    echo $! > "$RUN/kwin.pid"

    for i in $(seq 1 60); do
        WL=$(comm -13 "$RUN/wl.before" <(ls "$XDG_RUNTIME_DIR"/wayland-* 2>/dev/null | sort) | grep -v '\.lock' | head -1 || true)
        XD=$(comm -13 "$RUN/x.before" <(ls /tmp/.X11-unix/ 2>/dev/null | sort) | head -1 || true)
        [ -n "$WL" ] && [ -n "$XD" ] && break
        sleep 0.5
    done
    [ -n "${WL:-}" ] || die "nested wayland socket never appeared (see $RUN/kwin.log)"
    [ -n "${XD:-}" ]  || die "nested xwayland display never appeared"
    WL=${WL##*/}; XD=${XD#X}
    printf 'WAYLAND_DISPLAY=%s\nDISPLAY=:%s\nDBUS_SESSION_BUS_ADDRESS=%s\n' \
        "$WL" "$XD" "$(cat $RUN/bus)" > "$RUN/env"
    echo "nested screen up: wayland=$WL x11=:$XD"
    "$0" layout
    ;;

layout)
    [ -f "$RUN/bus" ] || die "not running"
    # load the docking script into the nested kwin (scoped to the private bus)
    SID=$(BUS call org.kde.KWin /Scripting org.kde.kwin.Scripting \
        loadScript s "$HERE/nested_layout.js" | awk '{print $2}')
    [ -n "$SID" ] || die "loadScript failed"
    BUS call org.kde.KWin "/Scripting/Script$SID" org.kde.kwin.Script run
    echo "layout script loaded (script $SID) — MC relaunches auto-dock bottom half"
    ;;

env)
    [ -f "$RUN/env" ] || die "not running — start it first"
    cat "$RUN/env"
    ;;

apps)
    [ -f "$RUN/env" ] || die "not running"
    set -a; . "$RUN/env"; set +a
    if command -v ghostty >/dev/null; then
        ghostty --title=noxdev --command="tmux new-session -A -s noxdev" >/dev/null 2>&1 &
    else
        export QT_QPA_PLATFORM=xcb
        konsole -p tabtitle=noxdev -e tmux new-session -A -s noxdev >/dev/null 2>&1 &
    fi
    echo "dev terminal launched inside nested screen (top half)"
    echo "start MC:   . $RUN/env ; cd $(dirname $HERE)/.. && ./tools/relaunch.sh"
    ;;

stop)
    # kill the kwin process (child of the dbus-run-session wrapper) + wrapper
    pkill -f "kwin_wayland.*output-count" 2>/dev/null || true
    [ -f "$RUN/kwin.pid" ] && kill "$(cat $RUN/kwin.pid)" 2>/dev/null || true
    sleep 1
    pkill -9 -f "kwin_wayland.*output-count" 2>/dev/null || true
    rm -f "$RUN/kwin.pid" "$RUN/env" "$RUN/bus"
    echo "nested screen stopped"
    ;;

*) echo "usage: $0 [start|env|apps|layout|stop]"; exit 1;;
esac
