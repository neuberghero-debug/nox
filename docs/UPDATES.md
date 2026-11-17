# NOX signed auto-updates (Modul 19)

## Trust chain
`update/UpdateManager` fetches `https://nox-client.vercel.app/api/update.json`:

```json
{
  "version": "0.2.0",
  "url": "https://…/nox-0.2.0.jar",
  "sha256": "…",
  "sig": "<b64 Ed25519 over version|url|sha256>",
  "minVersion": "0.1.0"          // anti-downgrade floor
}
```

Verify path: Ed25519 sig (embedded pubkey, `NativeCore` fragments) → version > current AND ≥ minVersion → download → sha256 match → stage to `nox/update/`.

## Apply
Jars can't self-overwrite while locked → staged swap:
1. `Apply` helper class copies `new.jar` over `mods/nox.jar` (after the old process exits — file-lock wait, 30s).
2. Old jar preserved as `nox.jar.bak`.
3. Next boot: if the new jar fails (class-load error within first 60s), `.bak` is restored automatically and the update marked bad (won't re-offer).

## Server side
`tools/sign_update.py` — builds the signed manifest from a jar + private key.

## Guarantees
- Downgrade / MITM / corrupted download → rejected before apply.
- Broken update → automatic rollback; user lands back on the last good build.
- Dev runs (classes-dir) never self-update.
