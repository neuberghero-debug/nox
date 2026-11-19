#!/usr/bin/env python3
"""KWin zkde_screencast feeder — connects to the nested KWin wayland display,
requests a stream of its output, prints the pipewire node id, then stays alive
to keep the stream feeding.

Usage: kwin_feed.py [envfile]   (default /tmp/nox-nested/env)
Prints "NODE=<id>" on stdout once the stream is created.
"""
import os
import sys
import threading
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

from pywayland.client import Display  # noqa: E402
from pywayland.protocol.wayland import WlOutput  # noqa: E402
from pyproto.zkde_screencast_unstable_v1 import ZkdeScreencastUnstableV1  # noqa: E402

ENVFILE = sys.argv[1] if len(sys.argv) > 1 else "/tmp/nox-nested/env"

env = {}
with open(ENVFILE) as f:
    for line in f:
        k, _, v = line.strip().partition("=")
        if k and v:
            env[k] = v

os.environ["WAYLAND_DISPLAY"] = env["WAYLAND_DISPLAY"]
os.environ.pop("DISPLAY", None)
os.environ["XDG_RUNTIME_DIR"] = os.environ.get("XDG_RUNTIME_DIR", f"/run/user/{os.getuid()}")

disp = Display()
disp.connect()
registry = disp.get_registry()

found = {}


def on_global(reg, name, iface, version):
    if iface in ("wl_output", "zkde_screencast_unstable_v1"):
        found[iface] = (name, min(version, 6 if iface.startswith("zkde") else 4))


registry.dispatcher["global"] = on_global
disp.roundtrip()

if "zkde_screencast_unstable_v1" not in found:
    print("ERR no zkde_screencast_unstable_v1 global", file=sys.stderr)
    sys.exit(2)
if "wl_output" not in found:
    print("ERR no wl_output global", file=sys.stderr)
    sys.exit(3)

screencast = registry.bind(found["zkde_screencast_unstable_v1"][0],
                           ZkdeScreencastUnstableV1,
                           found["zkde_screencast_unstable_v1"][1])
output = registry.bind(found["wl_output"][0], WlOutput, found["wl_output"][1])

state = {}
stream = screencast.stream_output(output, 1)  # pointer: hidden


def on_created(s, node):
    state["node"] = node


def on_failed(s, err):
    state["err"] = err


def on_serial(s, hi, lo):
    state["serial"] = (hi << 32) | lo


stream.dispatcher["created"] = on_created
stream.dispatcher["failed"] = on_failed
stream.dispatcher["serial"] = on_serial

deadline = time.time() + 10
while time.time() < deadline and "node" not in state and "err" not in state:
    disp.dispatch(block=True)

if "err" in state:
    print(f"ERR stream failed: {state['err']}", file=sys.stderr)
    sys.exit(4)
if "node" not in state:
    print("ERR timeout waiting for stream", file=sys.stderr)
    sys.exit(5)

print(f"NODE={state['node']}", flush=True)
if "serial" in state:
    print(f"SERIAL={state['serial']}", flush=True)

# keep the wayland connection + stream alive; watch for closed/failed events
def watch():
    while True:
        disp.dispatch(block=True)


t = threading.Thread(target=watch, daemon=True)
t.start()
try:
    while True:
        time.sleep(3600)
except KeyboardInterrupt:
    pass
