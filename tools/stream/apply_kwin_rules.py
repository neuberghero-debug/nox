#!/usr/bin/env python3
"""Install KWin window rules docking Minecraft + the noxdev terminal into the
screen rects that OBS crops for the stream zones. Idempotent, reloads KWin.

  DEV  rect: 0,32   1672x597   (class: noxdev)
  GAME rect: 0,629  1672x486   (title: Minecraft*)
Relaunch any matching window -> rule re-docks it -> OBS capture never moves.
"""

import configparser
import subprocess
from pathlib import Path

RULES_FILE = Path.home() / ".config/kwinrulesrc"
MARK = "NOX stream"

# StringMatch: 0=unimportant 1=exact 2=substring 3=regexp
# Rule apply: 0=unused 1=remember 2=force 3=apply-initially 4=force-temp
NOX_RULES = [
    {
        "Description": f"{MARK} - Minecraft dock",
        "title": "(?i)minecraft.*",
        "titlematch": "3",
        "position": "0,629",
        "positionrule": "2",
        "size": "1672,455",
        "sizerule": "2",
        "above": "true",
        "aboverule": "2",
    },
    {
        "Description": f"{MARK} - dev terminal dock",
        "wmclass": "noxdev",
        "wmclassmatch": "1",
        "position": "0,32",
        "positionrule": "2",
        "size": "1672,597",
        "sizerule": "2",
        "above": "true",
        "aboverule": "2",
    },
]


def main():
    cfg = configparser.ConfigParser(interpolation=None)
    cfg.optionxform = str
    if RULES_FILE.exists():
        cfg.read(RULES_FILE)

    # drop our previous rules
    for sec in list(cfg.sections()):
        if cfg.get(sec, "Description", fallback="").startswith(MARK):
            cfg.remove_section(sec)

    # append ours as fresh numbered groups
    n = 1
    used = set(cfg.sections())
    for rule in NOX_RULES:
        while str(n) in used:
            n += 1
        sec = str(n)
        cfg.add_section(sec)
        for k, v in rule.items():
            cfg.set(sec, k, v)
        n += 1

    if not cfg.has_section("General"):
        cfg.add_section("General")
    count = len([s for s in cfg.sections() if s != "General"])
    cfg.set("General", "count", str(count))

    RULES_FILE.parent.mkdir(parents=True, exist_ok=True)
    with RULES_FILE.open("w") as f:
        cfg.write(f, space_around_delimiters=False)
    print(f"[kwin] wrote {len(NOX_RULES)} rules -> {RULES_FILE}")

    r = subprocess.run(
        ["busctl", "--user", "call", "org.kde.KWin", "/KWin",
         "org.kde.KWin", "reconfigure"],
        capture_output=True, text=True)
    print("[kwin] reconfigure:", r.returncode, (r.stderr or "").strip())


if __name__ == "__main__":
    main()
