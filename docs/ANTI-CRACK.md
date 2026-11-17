# NOX anti-crack design (Module 30)

Layered defense — each layer assumes the others can be defeated.

```
                  ┌──────────────────────────────────────────┐
   build time →   │ noxJarSig (sha256 all classes + jsons)   │
                  │ Watermark (build fingerprint)            │
                  │ sig.txt embedded in jar                  │
                  └──────────────────────────────────────────┘
   boot →         │ TamperGuard.verifyAsync (rehash vs sig)  │
                  │ CrashLoopGuard.init (safe mode)          │
                  │ LicenseManager.init (Ed25519 offline)    │
                  └──────────────────────────────────────────┘
   runtime →      │ TamperWarner (60s recheck, injection)    │
                  │ FailSafe.tick (arbiter)                  │
                  │ VmGuard (jvm args + /proc/maps libs)     │
                  │ Canary (reflection surface)              │
                  │ Distributed c1..c8 (native+java checks)  │
                  │ Heartbeat (signed server attest)         │
                  │ ConfigSig (HMAC on persisted state)      │
                  └──────────────────────────────────────────┘
   native →       │ libnoxcore.so: Ed25519, hwid, antidebug, │
                  │ pubkey fragments, nWrap, nCheck table    │
                  └──────────────────────────────────────────┘
```

## Escalation ladder (FailSafe)
`WARN (transient)` → `DEGRADE (premium visuals off)` → `LOCK (all modules off + TamperScreen, sticky)`.
Weight-1 fails = suspicious (vm-agent, tracer), weight-2 = strong (jar-sig, canary, clock-rollback) → straight to LOCK.

## Attack-surface decisions
- **Don't lock on unverifiable**: sig can't run (classes-dir dev run) → verify skipped, never a false ban.
- **Heartbeat, not kill-switch**: server sees `ok=false` beats; client keeps running offline (grace) — revocation lands via next `/validate`.
- **Fragments**: pubkey never exists as one contiguous constant in the jar — `NativeCore` reassembles at runtime.
- **ConfigSig**: `license.state` lastValid/lastSeen are HMAC'd — can't hand-edit grace back to life.
- **Alt keystore**: `NativeCore.wrap` (hwid-bound XOR) — copied alts.json is useless on another machine.

## What remains attackable
Local JVM with full control (custom JVM, in-process agent loaded before ours, patched classloader) can bypass everything — that's inherent to client-side protection. The stack raises cost high enough that cracking > repurchasing for the target audience.
