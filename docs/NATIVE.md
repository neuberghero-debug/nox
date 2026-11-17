# NOX native core (Modul 30.1)

`native/` — Rust cdylib `libnoxcore.so`, packaged inside the jar and loaded by `ncore/NativeCore` (Java fallback when absent → dev runs keep working).

## Exports (JNI)
| Java | Native | Purpose |
|---|---|---|
| `nVerify(payload,sig,pub32)` | ed25519-dalek verify | license+update signature checks in Rust — no Java-side verify to patch |
| `nHwid()` | machine-id + MAC, salted | stable fingerprint (same result as `premium/Hwid` Java path) |
| `nAntiDebug()` | ptrace status, /proc checks, timing | bitmask: 1=tracer, 2=ptrace-blocked, 4=timing anomaly, -1=unavailable |
| `nKeyByte(i)` / `nMaskByte(i)` | build-time tables | key material fragments (ConfigSig), deobfuscation mask bytes |
| `nCheck(i)` | distributed check table | `Distributed.c*` gate — returns -1 when lib missing (treated as pass, grace) |
| `nWrap(data,hwid)` | XOR stream keyed sha256(hwid+salt) | machine-bound at-rest protection (alts keystore, chat.key) |
| `publicKeyRaw()` | 32B pubkey reassembled from fragments | pubkey never exists as a contiguous constant |

## Build
`cargo build --release` in `native/` (or `gradle buildNative` task — requires cargo). Output `target/release/libnoxcore.so` → copied to `src/main/resources/noxclient/native/linux-x86_64/`. `NativeCore` extracts to `nox/native/` and `System.load`s it.

## Fallback contract
Every native call has a Java equivalent; `available()` gates usage. A stripped/blocked .so degrades security but never breaks the client — by design (don't lock users out for a missing optional lib).
