#!/usr/bin/env bash
# release_update.sh — build, sign, and publish a signed NOX update manifest.
#
# Pipeline: gradle build → sha256+Ed25519 manifest (tools/sign_update.py)
#           → upload jar + update.json to the CDN bucket (rclone remote "noxcdn:")
#
# Usage:
#   bash infra/update-cdn/release_update.sh 0.2.0
#
# Requires: rclone configured with remote "noxcdn" (Cloudflare R2 / S3),
#           private key at nox-bot/keys/license_private.pem (never committed).
# Client side: UpdateManager reads NOX_UPDATE_URL / -Dnox.update.url —
# point it at https://<cdn>/update.json (default already https).
set -euo pipefail
cd "$(dirname "$0")/../.."

VER="${1:?usage: release_update.sh <version>}"
JAR="build/libs/nox-${VER}.jar"
CDN="noxcdn:nox-updates"
KEY="nox-bot/keys/license_private.pem"

echo "== build =="
gradle jar -q
[ -f "$JAR" ] || { echo "jar missing: $JAR"; exit 1; }

echo "== sign manifest =="
python3 tools/sign_update.py "$JAR" "https://cdn.nox.client/nox-${VER}.jar" \
    --min 0.1.0 --key "$KEY" --version "$VER" > /tmp/nox-update.json
cat /tmp/nox-update.json | python3 -m json.tool | head -10

echo "== upload =="
rclone copyto "$JAR" "$CDN/nox-${VER}.jar"
rclone copyto /tmp/nox-update.json "$CDN/update.json"

echo "== verify =="
rclone cat "$CDN/update.json" | python3 -c 'import json,sys; m=json.load(sys.stdin); print("manifest ok:", m["version"])'
echo "DONE — set NOX_UPDATE_URL=https://<your-cdn-domain>/update.json"
