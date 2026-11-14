#!/usr/bin/env python3
"""Post an automatic changelog embed to the Discord #changelogs channel.

Usage:
    python3 tools/changelog.py              # posts HEAD..last-posted commits
    python3 tools/changelog.py "text"       # posts a custom entry
    NOX_CHANGELOG_DRY=1 ...                 # prints instead of posting

State: .changelog_state tracks the last posted commit so each deploy posts
only the new commits. Token comes from nox-community-bot/.env — never printed.
"""
import json, os, re, subprocess, sys, time, urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
GUILD = "1570135653826433086"
CHANNEL = "1571142656644620418"
STATE = os.path.join(ROOT, ".changelog_state")


def token():
    for env in (os.path.join(ROOT, "nox-community-bot/.env"),
                os.path.join(ROOT, ".env")):
        try:
            for line in open(env):
                if line.startswith("DISCORD_TOKEN="):
                    return line.split("=", 1)[1].strip()
        except FileNotFoundError:
            pass
    return os.environ.get("DISCORD_TOKEN", "")


def git(*a):
    return subprocess.check_output(["git", "-C", ROOT, *a], text=True).strip()


def commits_since(ref):
    """Newest-first list of (sha, subject) since ref (exclusive)."""
    try:
        out = git("log", "--pretty=%h%x00%s", f"{ref}..HEAD") if ref else \
            git("log", "--pretty=%h%x00%s", "-n", "10")
    except subprocess.CalledProcessError:
        out = git("log", "--pretty=%h%x00%s", "-n", "10")
    rows = [l.split("\x00", 1) for l in out.splitlines() if "\x00" in l]
    return [(s, m) for s, m in rows if not m.startswith("Merge")]


def post(tok, embed):
    body = json.dumps({"embeds": [embed]}).encode()
    req = urllib.request.Request(
        f"https://discord.com/api/v10/channels/{CHANNEL}/messages",
        data=body, method="POST",
        headers={"Authorization": f"Bot {tok}",
                 "Content-Type": "application/json",
                 "User-Agent": "DiscordBot (https://nox.client, 1.0)"})
    with urllib.request.urlopen(req, timeout=20) as r:
        return json.load(r)


def main():
    tok = token()
    custom = sys.argv[1] if len(sys.argv) > 1 else None

    last = open(STATE).read().strip() if os.path.exists(STATE) else ""
    head = git("rev-parse", "--short", "HEAD")
    rows = commits_since(last)
    if not rows and not custom:
        print("changelog: nothing new")
        return

    if custom:
        title, desc = "Update", custom
    else:
        title = f"Build {head}"
        lines = [f"[`{s}`](https://github.com/neuberghero-debug/nox/commit/{git('rev-parse', s)}) {m}"
                 for s, m in rows[:12]]
        desc = "\n".join(lines)
        if len(rows) > 12:
            desc += f"\n… and {len(rows) - 12} more commits"

    embed = {
        "author": {"name": "NOX Client", "icon_url":
            "https://raw.githubusercontent.com/neuberghero-debug/nox/main/src/main/resources/assets/noxclient/textures/icon.png"},
        "title": title,
        "description": desc[:4000],
        "color": 0x7A2BE2,
        "footer": {"text": "github.com/neuberghero-debug/nox"},
        "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    if os.environ.get("NOX_CHANGELOG_DRY"):
        print(json.dumps(embed, indent=1)); return
    resp = post(tok, embed)
    print("changelog posted:", resp.get("id"), f"({len(rows)} commits)")
    open(STATE, "w").write(head + "\n")


main()
