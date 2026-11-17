#!/usr/bin/env python3
"""Merge the stream-module config into the NOX profile presets.

Writes (idempotent):
  <profile>/nox/discord.txt                     canonical invite
  <profile>/nox/presets/<server>.json           merged, never replaced —
                                                only the stream modules
                                                below are touched.

Usage:
  apply_preset.py [--profile <modrinth-profile>] [--user <tiktok>] [--discord <invite>]
"""
import argparse
import json
import sys
from pathlib import Path

DEFAULT_PROFILE = (Path.home() / ".var/app/com.modrinth.ModrinthApp"
                   "/data/ModrinthApp/profiles/NOX")

RULES = "\n".join([
    "type=FOLLOW|title:Welcome %u!",
    "type=SUBSCRIBE|title:%u subscribed!",
    "gift=Rose|sound",
    "gift=Heart Me|jump",
    "gift=Doughnut|theme",
    "coins>=100|look",
    "coins>=1000|module:Animations",
    "comment~^!discord\\b|discord",
    "comment~^!dc\\b|discord",
    "comment~^!theme\\b|theme",
    "comment~^!vote\\b|vote:Chat waehlt — 1 oder 2",
])

def stream_modules(user: str) -> dict:
    return {
        "TikTokLive": {
            "enabled": True, "keybind": -1,
            "settings": {
                "TikTok user": user,
                "Test mode": "false",          # armed — gifts DO trigger actions
                "Combos": "true",
                "Viewer votes": "true",
                "Rules": RULES,
                "Stream chat HUD": "true",
                "Chat lines": "7",
                "Discord strip": "true",
                "Chat X": "4.0",
                "Chat Y": "0.0",               # auto — above hotbar
            },
        },
        "StreamerMode": {
            "enabled": True, "keybind": -1,
            "settings": {
                "Hide name": "true", "All player names": "false",
                "Fake name": "Steve", "Hide coords": "true",
                "Hide server IP": "true", "Hide skin": "false",
            },
        },
        "HudWidgets": {
            "enabled": True, "keybind": -1,
            "settings": {"HUD mode": "Standard"},
        },
    }


def merge(path: Path, mods: dict) -> str:
    data = {}
    if path.exists():
        try:
            data = json.loads(path.read_text())
        except Exception:
            path.rename(path.with_suffix(".json.broken"))
            data = {}
    data.update({k: v for k, v in data.items() if k == "schema_version"})
    for name, cfg in mods.items():
        cur = data.get(name, {})
        cur.update(cfg)
        cur.setdefault("settings", {}).update(cfg["settings"])
        data[name] = cur
    path.write_text(json.dumps(data, indent=2) + "\n")
    return path.name


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--profile", default=str(DEFAULT_PROFILE))
    ap.add_argument("--user", default="jbbbbbrk")
    ap.add_argument("--discord", default="https://discord.gg/tewtZTsZf5")
    a = ap.parse_args()

    nox = Path(a.profile) / "nox"
    presets = nox / "presets"
    presets.mkdir(parents=True, exist_ok=True)

    (nox / "discord.txt").write_text(a.discord + "\n")

    mods = stream_modules(a.user.lstrip("@"))
    targets = set(p.stem for p in presets.glob("*.json"))
    targets |= {"singleplayer", "localhost", "donutsmp"}   # always present
    for name in sorted(targets):
        merge(presets / f"{name}.json", mods)
        print(f"[preset] {name}.json merged")
    print(f"[discord] {a.discord} → nox/discord.txt")
    return 0


if __name__ == "__main__":
    sys.exit(main())
