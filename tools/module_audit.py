#!/usr/bin/env python3
"""Static module audit — verifies every module + every setting without a
Minecraft launch. Parses the source for ctor calls so a malformed setting
range, an unregistered module, or a missing super() can't reach runtime.
Run standalone or via nox_doctor."""
import re, sys, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/dev/noxclient"
MODS = SRC / "module"

errors, warns = [], []
ctor_re = re.compile(r'super\(\s*"([^"]+)"\s*,\s*"([^"]*)"\s*,\s*Category\.(\w+)')
num_re = re.compile(r'new\s+Setting\.Num\s*\(\s*"([^"]+)"\s*,\s*(-?[\d._]+)\s*,\s*(-?[\d._]+)\s*,\s*(-?[\d._]+)')
mode_re = re.compile(r'(?:new\s+Setting\.Mode|Setting\.Mode\.of)\s*\(\s*"([^"]+)"\s*,\s*"([^"]+)"\s*((?:,\s*"[^"]*")*)')

# ---- registration cross-check ----
mgr = (MODS / "ModuleManager.java").read_text()
registered = {r.rsplit(".", 1)[-1] for r in re.findall(r'reg\(new\s+([\w.]+)\s*\(', mgr)}
files = {p.stem: p for p in MODS.rglob("*.java") if p.stem != "ModuleManager"}
for cls in sorted(files):
    if cls not in registered:
        txt = files[cls].read_text()
        if ctor_re.search(txt):
            errors.append(f"{cls}: ctor present but not reg()'d in ModuleManager")

names, total_settings = {}, 0
for cls, path in sorted(files.items()):
    txt = path.read_text()
    sup = ctor_re.search(txt)
    if not sup:
        if cls in registered:
            errors.append(f"{cls}: registered but no super(name,desc,cat) call")
        continue
    name, desc, cat = sup.group(1), sup.group(2), sup.group(3)
    if name in names:
        errors.append(f"{cls}: dup module name '{name}' (also {names[name]})")
    names[name] = cls
    if not desc.strip():
        warns.append(f"{cls}: empty description")
    if cat not in {"COMBAT", "MOVEMENT", "RENDER", "MISC", "DONUTSMP"}:
        errors.append(f"{cls}: unknown category {cat}")

    for m in num_re.finditer(txt):
        sn, d, lo, hi = m.group(1), *(float(m.group(i).replace("_", "")) for i in (2, 3, 4))
        total_settings += 1
        if lo >= hi:
            errors.append(f"{cls}.{sn}: min({lo}) >= max({hi})")
        elif not (lo <= d <= hi):
            errors.append(f"{cls}.{sn}: default {d} outside [{lo},{hi}]")
        # human-hostile defaults
        if re.search(r'cps', sn, re.I) and d > 20:
            warns.append(f"{cls}.{sn}: inhuman cps default {d} (>20)")
        if re.search(r'delay', sn, re.I) and 0 < d < 0.05:
            warns.append(f"{cls}.{sn}: near-zero delay default {d}")

    for m in mode_re.finditer(txt):
        sn, d = m.group(1), m.group(2)
        opts = re.findall(r'"([^"]*)"', m.group(3) or "")
        total_settings += 1
        if opts and d not in opts:
            warns.append(f"{cls}.{sn}: default '{d}' not in modes (falls back to def)")

print(f"module audit: {len(names)} modules, {total_settings} numeric/mode settings")
for w in warns: print("  warn", w)
for e in errors: print("  ERR ", e)
sys.exit(1 if errors else 0)
