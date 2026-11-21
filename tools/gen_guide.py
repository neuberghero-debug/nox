#!/usr/bin/env python3
"""gen_guide — regenerate nox-community-bot/guide-data.js from the module
sources so the Discord client-guide never drifts from the real module list.
Deterministic: sorted by Category enum order, then module name.
"""
import json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/dev/noxclient/module"
OUT = ROOT / "nox-community-bot/guide-data.js"

CAT_ORDER = ["COMBAT", "MOVEMENT", "RENDER", "DONUTSMP", "MISC"]
SUPER = re.compile(
    r'super\(\s*"((?:[^"\\]|\\.)*)"\s*,\s*"((?:[^"\\]|\\.)*)"\s*,\s*Category\.(\w+)')

mods = []
for f in sorted(SRC.rglob("*.java")):
    if f.name in ("Module.java", "Category.java", "ModuleManager.java"):
        continue
    text = f.read_text()
    m = SUPER.search(text)
    if not m:
        continue
    name = m.group(1).replace('\\"', '"').replace("\\\\", "\\")
    desc = m.group(2).replace('\\"', '"').replace("\\\\", "\\")
    cat = m.group(3)
    premium = bool(re.search(r'\bpremium\s*=\s*true\b', text))
    mods.append({"name": name, "desc": desc, "cat": cat, "premium": premium})

mods.sort(key=lambda m: (CAT_ORDER.index(m["cat"]) if m["cat"] in CAT_ORDER else 99,
                         m["name"].lower()))

def js_str(s):
    return json.dumps(s, ensure_ascii=False)

lines = ["// generated from the NOX client source — module guide data",
         "// REGENERATE: python3 tools/gen_guide.py (runs in devloop)",
         "module.exports = ["]
for m in mods:
    lines.append("  {")
    lines.append(f'    "name": {js_str(m["name"])},')
    lines.append(f'    "desc": {js_str(m["desc"])},')
    lines.append(f'    "cat": {js_str(m["cat"])},')
    lines.append(f'    "premium": {"true" if m["premium"] else "false"}')
    lines.append("  },")
lines.append("];")

OUT.write_text("\n".join(lines) + "\n")
print(f"guide-data.js: {len(mods)} modules")
if len(mods) < 100:
    print("WARNING: suspiciously low module count — parser may be off",
          file=sys.stderr)
    sys.exit(1)
