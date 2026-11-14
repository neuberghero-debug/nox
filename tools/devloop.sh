#!/usr/bin/env bash
# NOX devloop: build -> doctor -> atomic deploy -> log tail. Never launches MC.
set -e
cd "$(dirname "$0")/.."

MODS="$HOME/.var/app/com.modrinth.ModrinthApp/data/ModrinthApp/profiles/NOX/mods"
JAR=$(ls build/libs/nox-*.jar | grep -v sources | head -1)

echo "== build =="
gradle build -x test --console=plain -q

echo "== doctor =="
python3 tools/nox_doctor.py || { echo "doctor failed — NOT deploying"; exit 1; }

echo "== deploy (atomic) =="
[ -f "$JAR" ] || { echo "jar missing: $JAR"; exit 1; }
cp "$JAR" "$MODS/.nox.jar.tmp" && sync
mv -f "$MODS/.nox.jar.tmp" "$MODS/$(basename "$JAR")" && sync
python3 - "$JAR" "$MODS/$(basename "$JAR")" <<'EOF'
import sys, hashlib, zipfile
s = hashlib.sha256(open(sys.argv[1],'rb').read()).hexdigest()
d = hashlib.sha256(open(sys.argv[2],'rb').read()).hexdigest()
zipfile.ZipFile(sys.argv[2]).testzip()
print("sha match:", s == d)
sys.exit(0 if s == d else 1)
EOF

echo "== latest nox log lines =="
LOG="$HOME/.var/app/com.modrinth.ModrinthApp/data/ModrinthApp/profiles/NOX/logs/latest.log"
CRASH=$(ls -t "$HOME/.var/app/com.modrinth.ModrinthApp/data/ModrinthApp/profiles/NOX/crash-reports/"*.txt 2>/dev/null | head -1)
[ -f "$LOG" ] && grep -iE "nox|error|exception" "$LOG" | tail -15 || true
[ -n "$CRASH" ] && echo "newest crash: $(basename "$CRASH")" && grep -A3 "^Description" "$CRASH" | head -4 || true
echo "done."
