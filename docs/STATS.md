# NOX client stats (Modul 26, Session B)

## Collection (`stats/Stats`)
Per-session counters on the client tick:
- playtime, distance (walked/sprinted/fallen/flown), jumps
- kills (last-hit credit: your last attacker-touch within the kill window), deaths, damage dealt/taken
- blocks broken (`StatsHooksMixin`), blocks placed
- module usage minutes (per enabled module per minute)
- fps ring buffer → `fpsRing` sparkline

## Persistence (`stats/StatsStore`)
Lifetime totals merge into `nox/stats.json` (atomic tmp+rename save, async via `saveAsync` on shutdown).

## Display
- `StatsScreen` — Session / Lifetime cards + module-usage bars + fps sparkline (own mini chart code, no deps).
- `Stats.sessionView()` — compact model for HUD widgets.
- `StatsExport` — CSV + JSON export to `nox/stats-export/`.

## Privacy
All stats are local-only by default; nothing is sent unless telemetry opt-in is on (`nox/privacy.json`), and even then module usage is sanitized.
