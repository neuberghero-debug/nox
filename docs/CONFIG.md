# NOX config system + migration

## Layout
```
run/nox/config.json            global: theme, geometry, client opts, schema_version
run/nox/config.json.bak.vN     automatic backup before each migration step
run/nox/presets/<server>.json  per-server module/settings profile (auto-load on join)
run/nox/hud.json               HUD element positions
run/nox/license.key|.state     license token + signed grace state
run/nox/alts.json              enc-token keystore
run/nox/stashes.json|stash-index.json   persistent stash records
run/nox/privacy.json           telemetry consent
run/nox/reported-crashes.txt   crash-report dedup store
```

## Migration (`config/ConfigMigrator`)
- `schema_version` (current: 3) lives in config.json.
- On load: backup → step-wise migrate v0→v1→v2→v3 → rewrite.
- Steps are pure functions on the JsonObject: v1 renamed removed modules (TamperWarner→TamperWarning legacy), v2 renamed settings (`air-check-mode`→`airCheck`), v3 = current.
- Migration failure → restore from `.bak.vN` and continue with defaults.

## Setting coercion
`Setting.get()` implementations coerce raw config values (wrong types from hand-edits/old configs) — `numOr`, `intOr`, `string` fallbacks. Never trust `set()` input types.

## Signed state
`license.state` carries `ConfigSig` HMAC (hwid-keyed). Tampering resets `lastValid` → premium requires real server validation.

## Share/import
`noxctl share [mod]` → clipboard JSON; `import` applies clipboard config (per-module or full). Presets auto-load by server name via `config.onServerChange()`.
