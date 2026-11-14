#!/usr/bin/env bash
# register-domain.sh — register j6ilbrk.is-a.dev via the is-a.dev/register repo.
#
# is-a.dev works like this: you fork github.com/is-a-dev/register, add
# domains/<name>.json, open a PR. Once merged, the subdomain goes live
# (Cloudflare NS).
#
# Usage:
#   gh auth login                       # one-time, needs YOUR GitHub account
#   GH_USER=<github-username> bash tools/register-domain.sh [target-url]
#
# Defaults: name=j6ilbrk, record=URL redirect to the NOX discord invite.
set -euo pipefail

NAME="${NOX_DOMAIN:-j6ilbrk}"
GH_USER="${GH_USER:-j6ilbrk}"
EMAIL="${GH_EMAIL:-$GH_USER@users.noreply.github.com}"
TARGET="${1:-https://discord.gg/tewtZTsZf5}"
WORK=/tmp/is-a-dev-register

command -v gh >/dev/null || { echo "need github-cli (gh)"; exit 1; }
gh api user --jq .login >/dev/null 2>&1 || { echo "run: gh auth login"; exit 1; }
GH_USER="${GH_USER:-$(gh api user --jq .login)}"

rm -rf "$WORK"
gh repo fork is-a-dev/register --clone "$WORK" --remote=false 2>/dev/null \
  || git clone "https://github.com/$GH_USER/register" "$WORK"

mkdir -p "$WORK/domains"
cat > "$WORK/domains/$NAME.json" <<EOF
{
  "description": "NOX client — Fabric utility client for DonutSMP",
  "owner": {
    "username": "$GH_USER",
    "email": "$EMAIL"
  },
  "records": {
    "URL": "$TARGET"
  }
}
EOF

cd "$WORK"
git checkout -b "add-$NAME" 2>/dev/null || git checkout "add-$NAME"
git add "domains/$NAME.json"
git -c user.name="$GH_USER" -c user.email="$EMAIL" \
  commit -m "add $NAME.is-a.dev" || echo "nothing to commit"
git push origin "add-$NAME" -f
gh pr create --repo is-a-dev/register \
  --title "Add $NAME.is-a.dev" \
  --body "Registers \`$NAME.is-a.dev\` → $TARGET (NOX client community)." \
  --head "$GH_USER:add-$NAME"
echo "done — https://$NAME.is-a.dev goes live after merge"
