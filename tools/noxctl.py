#!/usr/bin/env python3
"""noxctl — drive the NOX debug console inside a running dev/prod client.
Writes commands to <gameDir>/nox_cmd, waits for nox_ack, fetches screenshots.

Usage:
  python3 tools/noxctl.py [run|prod] <command...>
Examples:
  noxctl.py run shot            -> latest screenshot path printed
  noxctl.py run gui; sleep; shot
  noxctl.py run "click 200 300" "key 256"
"""
import os, sys, time, glob, shutil

DIRS = {
    "run": os.path.join(os.path.dirname(__file__), "..", "run"),
    "prod": os.path.expanduser("~/.var/app/com.modrinth.ModrinthApp/data/"
                               "ModrinthApp/profiles/NOX"),
}

def wait_ack(gd, line, timeout=8.0):
    ack = os.path.join(gd, "nox_ack")
    t0 = time.time()
    last_txt = None
    while time.time() - t0 < timeout:
        if os.path.exists(ack):
            txt = open(ack).read()
            os.remove(ack)
            # discard stale acks from earlier commands: the client echoes the
            # command (or a custom payload). Accept only a matching or
            # payload-shaped ack for the matching command type.
            if line in txt or txt.startswith("err"):
                return txt
            if line.startswith("key ") and txt.startswith("key " + line[4:]):
                return txt
            if line == "state" and txt.startswith("noxwin="):
                return txt
            if line == "slots" and txt.startswith("slots:"):
                return txt
            if line.startswith("set ") and txt.startswith(
                "set " + line.split(" ")[1] + "."):
                return txt
            if line.startswith("get ") and txt.startswith(
                line.split(" ")[1] + ":"):
                return txt
            # generic: replies echo the verb ("click 256.0,144.0 -> ...", "mods: x y")
            verb = line.split()[0]
            if txt.startswith(verb + " ") or txt.startswith(verb + ":"):
                return txt
            last_txt = txt      # fresh ack but no match — keep as fallback
        time.sleep(0.05)
    return last_txt if last_txt else "<ack timeout>"

def send(gd, line):
    open(os.path.join(gd, "nox_cmd"), "w").write(line)
    r = wait_ack(gd, line)
    print(f"$ {line}\n  -> {r}")
    return r

def latest_shot(gd, since, out="/tmp/nox_shot.png"):
    # Screenshot.grab schedules an asynchronous encode. An ack and a filename
    # do not mean the PNG has finished writing.
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        shots = sorted(glob.glob(os.path.join(gd, "screenshots", "*.png")),
                       key=os.path.getmtime, reverse=True)
        for shot in shots:
            if os.path.getmtime(shot) < since:
                continue
            with open(shot, "rb") as f:
                data = f.read()
            if data.startswith(b"\x89PNG\r\n\x1a\n") and data.endswith(b"\0\0\0\0IEND\xaeB`\x82"):
                with open(out, "wb") as f:
                    f.write(data)
                print("  saved ->", out)
                return out
        time.sleep(0.1)
    print("  (screenshot did not finish within 15s)")
    return None

def main():
    which = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1] in DIRS else "run"
    args = sys.argv[2:] if which in DIRS and len(sys.argv) > 2 else sys.argv[1:]
    gd = os.path.abspath(DIRS[which])
    for a in args:
        started = time.time()
        send(gd, a)
        if a == "shot":
            latest_shot(gd, started)
        time.sleep(0.25)

if __name__ == "__main__":
    main()
