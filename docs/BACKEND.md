# NOX backend (nox-license-server)

FastAPI + sqlite (`NOX_DB`), single file `main.py`. Run:
`NOX_ADMIN_TOKEN=… NOX_PRIVATE_PEM=keys/private.pem uvicorn main:app --port 8787`

## License
| Endpoint | Auth | Purpose |
|---|---|---|
| POST /activate | key+hwid | bind license↔hwid (1 reset/30d) |
| POST /validate | key+hwid | silent re-check; revoke→premium off |
| POST /deactivate | key+hwid | unbind |
| POST /issue | admin | mint Ed25519 key {days,tier} |
| POST /revoke | admin | revoke lid |
| GET /public-key | — | raw Ed25519 pubkey |
| POST /heartbeat | — | integrity attest (v, shape, ok, dbg, watermark) |

## Friends (JWT sessions)
`POST /friends/login` {key,hwid} → HS256 token (rides premium gate).
`POST /friends/code` → 6-char code · `POST /friends/redeem` {code} mutual-link ·
`POST /friends/ping` {name,server,dim,x,y,z} presence (TTL 90s) · `GET /friends` list · `POST /friends/remove`.

## Cosmetics marketplace
`POST /cosmetics/publish` (JWT) → pending · `GET /cosmetics[?slot=]` public browse approved ·
`GET /cosmetics/{id}` (JWT) download+count · `POST /cosmetics/{id}/report` (5 reports→flagged) ·
`POST /admin/cosmetics/{id}/moderate` {approve|reject} · `GET /admin/cosmetics?status=` queue.

## Analytics
`GET /admin/analytics` — license/beats/friends/audit aggregation; `GET /admin/dashboard` — self-refreshing HTML.

## Operational notes
- Rate limits: per-IP 30/min, per-key 10/min (in-memory buckets).
- `iphash`/`keyhash` truncate — PII never stored raw.
- `audit(ts,event,lid,ip)` on every mutating endpoint.
- Friends/cosmetics JWTs are HS256 over `NOX_JWT_SECRET` (env, else random-per-boot → logins drop on restart).
