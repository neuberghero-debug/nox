#!/usr/bin/env bash
# audit_run.sh — drive the M29 SecurityAudit at runtime (not just boot).
# Usage: bash tools/audit_run.sh [run|prod]
# Requires the client running with DebugConsole polling (nox_cmd file).
set -euo pipefail
cd "$(dirname "$0")/.."

MODE="${1:-run}"
echo "== nox security audit ($MODE) =="
out="$(python3 tools/noxctl.py "$MODE" "audit" 2>&1 || true)"
echo "$out"

if echo "$out" | grep -q 'all checks pass'; then
    echo "RESULT: PASS"
    exit 0
elif echo "$out" | grep -q 'audit:'; then
    echo "RESULT: FINDINGS"
    exit 2
else
    echo "RESULT: UNREACHABLE (client not running / debug console off?)"
    exit 3
fi
