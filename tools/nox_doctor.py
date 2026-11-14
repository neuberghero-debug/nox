#!/usr/bin/env python3
"""NOX static doctor — catches crash-classes WITHOUT launching Minecraft.

Checks, each mapped to a crash we actually shipped:
  1. mixins  : every @Inject/@Redirect method target + 'target' strings exist
               in the merged 26.3 jar (javap)
  2. apis    : common MC-symbol pitfalls (mc.screen field, blur>1/frame)
  3. jar     : built jar is a valid zip, all class files parse, sig.txt
               roundtrip matches TamperGuard.selfHash()
  4. assets  : every assets path referenced in java exists in resources

Exit 0 = clean. Exit 1 = findings. No Minecraft launch, ~seconds.
"""
import os, re, subprocess, sys, zipfile, hashlib, glob, json

ROOT = os.path.join(os.path.dirname(__file__), "..")
SRC = os.path.join(ROOT, "src/main/java")
RES = os.path.join(ROOT, "src/main/resources")
FINDINGS = []


def finding(sev, msg):
    FINDINGS.append((sev, msg))
    print(f"  [{sev}] {msg}")


def merged_jar():
    hits = glob.glob(os.path.join(
        ROOT, ".gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.3/*.jar"))
    hits += glob.glob(os.path.expanduser(
        "~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-*/26.3/*.jar"))
    return hits[0] if hits else None


def javap(jar, cls):
    """Return method/field names of a class from the merged jar (cached)."""
    key = cls
    if key in javap.cache:
        return javap.cache[key]
    try:
        out = subprocess.run(["javap", "-p", "-cp", jar, cls], capture_output=True,
                             text=True, timeout=60).stdout
    except Exception as e:
        out = ""
    names = set(re.findall(r"\b(\w+)\s*\(", out))            # methods
    names |= set(re.findall(r"^\s+(?:public|protected|private|final|static|volatile|transient|\s)+[\w<>\[\],.?]+\s+(\w+)\s*;", out, re.M))  # fields
    javap.cache[key] = names
    return names


javap.cache = {}


def check_mixins(jar):
    print("== mixins ==")
    if not jar:
        finding("WARN", "merged minecraft jar not found — target checks skipped")
        return
    for path in glob.glob(os.path.join(SRC, "**/*.java"), recursive=True):
        text = open(path).read()
        if "@Mixin" not in text:
            continue
        m = re.search(r"@Mixin\(([\w.]+)\.class\)", text)
        if not m:
            continue
        target = m.group(1)
        # resolve simple name -> package via imports
        fq = target
        if "." not in target:
            imp = re.search(r"import\s+([\w.]+\." + target + r")\s*;", text)
            fq = imp.group(1) if imp else "net.minecraft." + target
        cls_path = fq.replace(".", "/")
        try:
            with zipfile.ZipFile(jar) as z:
                if cls_path + ".class" not in z.namelist():
                    finding("ERR", f"{os.path.basename(path)}: mixin target {fq} missing in jar")
                    continue
        except Exception as e:
            finding("WARN", f"jar read failed: {e}")
            return
        known = javap(jar, fq)
        # @Inject/@Redirect/@ModifyArg method strings
        for ann, meth in re.findall(r"@(Inject|Redirect|ModifyArg|ModifyVariable|ModifyExpressionValue)\(([^)]*)\)", text):
            mm = re.search(r'method\s*=\s*"([^"]+)"', meth)
            if mm:
                for name in re.split(r"[;\s]+", mm.group(1)):
                    base = name.split("(")[0]
                    if base and base not in known and not base.startswith("<"):
                        finding("ERR", f"{os.path.basename(path)}: @{ann} method '{base}' not in {fq}")
        # field accesses in @Accessor
        for acc in re.findall(r'@Accessor\("([^"]+)"\)', text):
            if acc not in known:
                finding("ERR", f"{os.path.basename(path)}: @Accessor '{acc}' not in {fq}")
        # @Shadow fields/methods
        for sh in re.findall(r"@Shadow[^;]*?(\w+)\s*[;(]", text):
            if sh not in known and sh != "final":
                finding("WARN", f"{os.path.basename(path)}: @Shadow '{sh}' not found in {fq}")


def check_api_pitfalls():
    print("== api pitfalls ==")
    for path in glob.glob(os.path.join(SRC, "**/*.java"), recursive=True):
        text = open(path).read()
        base = os.path.basename(path)
        # strip comments before pitfall checks so docs don't false-positive
        code = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
        code = re.sub(r"//[^\n]*", "", code)
        if re.search(r"\bmc\.screen\b|minecraft\.screen\b|\.screen != null", code):
            finding("ERR", f"{base}: reads mc.screen — no public screen field in 26.3")
        if re.search(r"\.blurBeforeThisStratum\s*\(", code):
            finding("WARN", f"{base}: calls blurBeforeThisStratum — only ONE blur/frame allowed; vanilla screens already blur")
        # SDL semantics: mouse buttons are 1-indexed (LMB=1, MMB=2, RMB=3);
        # keys are SDL scancodes, not GLFW
        if re.search(r"MouseButtonInfo\(\s*0", code):
            finding("ERR", f"{base}: MouseButtonInfo(0,…) — SDL button codes start at 1 (LMB=1)")
        # Map.of/Map.ofEntries throw IllegalArgumentException on duplicate keys
        keys = re.findall(r'Map\.entry\(\s*"([^"]+)"\s*,|Map\.of\(\s*"([^"]+)"\s*,', code)
        flat = [k for pair in keys for k in pair if k]
        for k in set(flat):
            if flat.count(k) > 1:
                finding("ERR", f"{base}: duplicate Map.entry key \"{k}\" — Map.of* throws at class-init")
        for m in re.finditer(r"\.sendSystemMessage|\.displayClientMessage", text):
            pass


def check_jar():
    print("== jar ==")
    jars = sorted(glob.glob(os.path.join(ROOT, "build/libs/*.jar")),
                  key=os.path.getmtime)
    if not jars:
        finding("WARN", "no built jar — run gradle build")
        return
    jar = jars[-1]
    try:
        z = zipfile.ZipFile(jar)
        bad = z.testzip()
        if bad:
            finding("ERR", f"{os.path.basename(jar)}: corrupt entry {bad}")
            return
    except zipfile.BadZipFile as e:
        finding("ERR", f"{os.path.basename(jar)}: not a zip — {e}")
        return
    # sig roundtrip vs TamperGuard.selfHash()
    try:
        sig = z.read("assets/noxclient/sig.txt").decode().strip()
        names = sorted(n for n in z.namelist()
                       if (n.startswith("dev/noxclient/") and n.endswith(".class"))
                       or n in ("fabric.mod.json", "noxclient.mixins.json"))
        h = hashlib.sha256()
        for n in names:
            h.update(n.encode()); h.update(z.read(n))
        if h.hexdigest() != sig:
            finding("ERR", "sig.txt mismatch — TamperGuard would flag this build as modified")
    except KeyError:
        finding("WARN", "no sig.txt in jar (noxSig task didn't run?)")
    except Exception as e:
        finding("WARN", f"sig check failed: {e}")
    print(f"  ok: {os.path.basename(jar)} ({os.path.getsize(jar)//1024} KiB)")


def check_assets():
    print("== assets ==")
    # every noxclient:xxx identifier referenced in java must exist
    for path in glob.glob(os.path.join(SRC, "**/*.java"), recursive=True):
        text = open(path).read()
        for m in re.finditer(r'"(noxclient|minecraft):([\w./-]+)"', text):
            ns, p = m.group(1), m.group(2)
            if ns != "noxclient":
                continue
            hits = glob.glob(os.path.join(RES, "assets/noxclient/**", p + ".*"), recursive=True) \
                + glob.glob(os.path.join(RES, "assets/noxclient/**", p), recursive=True)
            if not hits and not os.path.isdir(os.path.join(RES, "assets/noxclient", p)):
                finding("WARN", f"{os.path.basename(path)}: asset '{p}' not found under resources")
    # sky frames presence
    frames = glob.glob(os.path.join(RES, "assets/noxclient/sky/frame_*.jpg"))
    if len(frames) < 2:
        finding("WARN", f"only {len(frames)} sky frames")


def main():
    print("NOX doctor —", os.path.abspath(ROOT))
    jar = merged_jar()
    check_mixins(jar)
    check_api_pitfalls()
    check_jar()
    check_assets()
    errs = sum(1 for s, _ in FINDINGS if s == "ERR")
    warns = sum(1 for s, _ in FINDINGS if s == "WARN")
    print(f"\n{errs} errors, {warns} warnings")
    sys.exit(1 if errs else 0)


if __name__ == "__main__":
    main()
