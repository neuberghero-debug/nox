# NOX premium licensing

## Key format
`NOX1.<b64url payload>.<b64url Ed25519 sig>` — payload JSON: `{tier, lid, exp, iat}`. Verify is fully offline (public key embedded, split into fragments reassembled by `NativeCore`). Private key: `nox-bot/keys/` (gitignored); bot `/issue @user <days>` mints.

## Client flow (`premium/LicenseManager`)
1. `init()` reads `nox/license.key` → offline-verify → `premium=true`.
2. Server activation (`/activate`) binds license↔HWID once (1 reset/30d).
3. Every 24h silent `/validate` — revocation kills premium next run.
4. Grace: offline OK while `now-lastValid < 30d` AND `expires_at > now`.
5. `nox/license.state` persists `{lid,lastValid,lastSeen,sig}` — `sig` is `ConfigSig` HMAC (hwid-keyed) so hand-edits are rejected.
6. Clock rollback (`lastSeen > now+60s`) → premium off.
7. `premium()` additionally requires `Distributed.premiumAllowed()` (8 native/Java checks).

## HWID (`premium/Hwid`)
`sha256(os.name|arch | machine-id | primary MAC)`, url-safe b64[0:24]. Components hashed separately — no raw identifiers leave the machine.

## Server (`nox-license-server/main.py`, FastAPI+sqlite)
`/activate` bind · `/validate` re-check · `/deactivate` unbind · `/issue` admin mint · `/heartbeat` integrity attest · `/friends/*` session+presence · `/cosmetics*` marketplace+moderation · `/admin/analytics`+`/admin/dashboard` ops view · `audit` table on every mutating call. Rate-limited per ip+key.

## Entitlements
- `premium` — gated modules/cosmetics (`assertPremium()` → PremiumScreen on fail).
- `capeUnlocked` — one-time custom-cape entitlement (`tier:"cape"` key or premium).
- `graceExpired()`/`clockRolledBack()` — exposed for audits/Distributed.
- `storedKey()`/`api()` — session bootstrap for FriendSync/CosmeticsMarket.
