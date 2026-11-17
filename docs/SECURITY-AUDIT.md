# NOX security audit

## Threat model
Protect the premium/anti-crack stack against: jar tampering, config tampering,
debugger/agent attach, code injection at runtime, license sharing, crash-loop
exploits, cracked updates.

## Layers (all independent — defeating one doesn't help)

| Layer | File | What it does |
|---|---|---|
| Jar signature | `util/TamperGuard` | sha256 over every `dev/noxclient/**.class` + mod jsons vs `assets/noxclient/sig.txt` (written by `gradle noxJarSig` post-jar). Dev runs skip. |
| User warning | `module/misc/TamperWarner` | 60s re-check, injection watch (java agents, ptrace/native anti-debug), warn cascade (chat+sound+toast+webhook), lock mode. |
| Fail-safe | `security/FailSafe` | Central arbiter: WARN → DEGRADE (cosmetics off) → LOCK (all modules off + TamperScreen). Auto de-escalates except LOCK. |
| VM watch | `security/VmGuard` | JVM args scan (-javaagent/-agentlib/-Xdebug; dev agents whitelisted) + `/proc/self/maps` injector-lib scan, 60s re-scan. |
| Canary | `security/Canary` | Reflection integrity over security-critical class surfaces (`missing()` = attack found). |
| Distributed | `premium/Distributed` | 8 independent checks c1–c8 gated inside `Module.setEnabled` — premium can't be enabled when they fail. |
| Native core | `native/` + `ncore/NativeCore` | Rust JNI: Ed25519 verify, salted HWID, anti-debug bitmask, fragmented pubkey (reassembled at runtime), wrap() machine-binding. Java fallback for dev. |
| Watermark | `security/Watermark` | Build fingerprint baked into the signed jar — leaked builds are attributable. |
| Heartbeat | `security/Heartbeat` | Signed server ping (`/heartbeat` endpoint) — server sees which builds fail integrity, offline grace still works. |
| Crash-loop | `security/CrashLoopGuard` | ≥3 crashes/15min → safe mode (non-essential modules off). |
| Config sig | `security/ConfigSig` | HMAC(hwid-key) over license grace state — hand-edited `license.state` is rejected. |
| Keystore | `alts/AltManager` | Tokens stored `enc:` = Base64(NativeCore.wrap). Plaintext `token` migrated on load. |
| Webhook hardening | `security/WebhookSec` | Rate-limit, HMAC, dedup, sanitize, retry — single transport for security alerts. |
| Updates | `update/UpdateManager` + `Apply` | Ed25519 manifest, sha256 artifact, anti-downgrade, `.bak` rollback, staged swap. |

## Licensing
Ed25519 `NOX1.<payload>.<sig>`; pubkey embedded (native fragments). Server:
activate→bind hwid, 24h silent revalidate, 30-day grace, revocation, clock-rollback detection. See `nox-license-server/`.

## Audit command
`/nox audit` → `SecurityAudit.run()` — self-check over tamper/ncore/migrator/update/telemetry-gate.

## Known limitations
- Pure-Java fallbacks (dev runs) don't resist a determined attacker with JVM control — the .so paths are the production defense.
- `sig.txt` covers `dev/noxclient/**` only; mixin classes outside that package aren't hashed.
- Native `nWrap` is XOR — fine for token-at-rest, not for secrets-at-rest generally.
