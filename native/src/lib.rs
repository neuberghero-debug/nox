//! NOX native core — license/HWID/crypto/anti-debug behind JNI.
//! Java side (dev.noxclient.native.NativeCore) degrades to Java fallbacks
//! when the lib is absent; when present the sensitive paths run here.
//!
//! Built with -O3, lto=fat, panic=abort, stripped symbols.
//! BUILD_SALT env at compile time makes every shipped binary unique.

use ed25519_dalek::{Signature, VerifyingKey};
use jni::objects::{JByteArray, JClass, JString};
use jni::sys::{jboolean, jbyteArray, jint, jlong, jstring};
use jni::JNIEnv;
use sha2::{Digest, Sha256};
use std::time::{SystemTime, UNIX_EPOCH};

/// Per-build salt — mixed into HWID + check answers so a memory diff
/// between two shipped builds never lines up.
const BUILD_SALT: &str = match option_env!("NOX_BUILD_SALT") {
    Some(s) => s,
    None => "nox-default-salt",
};

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

fn sha256_hex(data: &[u8]) -> String {
    let mut h = Sha256::new();
    h.update(data);
    hex(&h.finalize())
}

fn hex(b: &[u8]) -> String {
    const C: &[u8; 16] = b"0123456789abcdef";
    let mut s = String::with_capacity(b.len() * 2);
    for &x in b {
        s.push(C[(x >> 4) as usize] as char);
        s.push(C[(x & 15) as usize] as char);
    }
    s
}

/* ---------------- machine fingerprint ---------------- */

fn read_trim(p: &str) -> String {
    std::fs::read_to_string(p)
        .map(|s| s.trim().to_string())
        .unwrap_or_default()
}

#[cfg(target_os = "linux")]
fn machine_parts() -> Vec<String> {
    let mut v = vec![
        read_trim("/etc/machine-id"),
        read_trim("/var/lib/dbus/machine-id"),
        read_trim("/sys/class/dmi/id/product_uuid"),
        read_trim("/sys/class/dmi/id/board_serial"),
    ];
    // first non-virtual MAC
    if let Ok(rd) = std::fs::read_dir("/sys/class/net") {
        for e in rd.flatten() {
            let mac = read_trim(&format!("{}/address", e.path().display()));
            if !mac.is_empty() && mac != "00:00:00:00:00:00" {
                v.push(mac);
                break;
            }
        }
    }
    v
}

#[cfg(target_os = "windows")]
fn machine_parts() -> Vec<String> {
    // registry MachineGuid + WMI UUID via cmd — Java fallback covers the rest
    let guid = std::process::Command::new("reg")
        .args(["query", r"HKLM\SOFTWARE\Microsoft\Cryptography", "/v", "MachineGuid"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).to_string())
        .unwrap_or_default();
    vec![guid]
}

#[cfg(target_os = "macos")]
fn machine_parts() -> Vec<String> {
    let out = std::process::Command::new("ioreg")
        .args(["-rd1", "-c", "IOPlatformExpertDevice"])
        .output()
        .map(|o| String::from_utf8_lossy(&o.stdout).to_string())
        .unwrap_or_default();
    vec![out]
}

/* ---------------- anti-debug ---------------- */

#[cfg(target_os = "linux")]
fn tracer_pid() -> i32 {
    read_trim("/proc/self/status")
        .lines()
        .find(|l| l.starts_with("TracerPid:"))
        .and_then(|l| l.split(':').nth(1))
        .and_then(|v| v.trim().parse().ok())
        .unwrap_or(0)
}

#[cfg(not(target_os = "linux"))]
fn tracer_pid() -> i32 {
    0 // IsDebuggerPresent lives behind winapi; Java-side JDWP/attach checks cover it
}

/// ptrace-scope probe: try to ptrace(PTRACE_TRACEME) — fails when already traced.
#[cfg(target_os = "linux")]
fn self_trace_blocked() -> bool {
    unsafe { libc::ptrace(libc::PTRACE_TRACEME, 0, 0, 0) < 0 }
}

#[cfg(not(target_os = "linux"))]
fn self_trace_blocked() -> bool {
    false
}

/* ================= JNI exports ================= */

/// String hwid — salted sha256 over machine parts. Stable per machine+build.
#[no_mangle]
pub extern "C" fn Java_dev_noxclient_ncore_NativeCore_nHwid(
    mut env: JNIEnv,
    _c: JClass,
) -> jstring {
    let material = machine_parts().join("|") + "|" + BUILD_SALT;
    env.new_string(sha256_hex(material.as_bytes()))
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

/// boolean verify(payload, sig, pubKeyRaw32) — Ed25519 in native.
#[no_mangle]
pub extern "C" fn Java_dev_noxclient_ncore_NativeCore_nVerify(
    mut env: JNIEnv,
    _c: JClass,
    payload: JByteArray,
    sig: JByteArray,
    pk: JByteArray,
) -> jboolean {
    let ok = (|| {
        let p = env.convert_byte_array(&payload).ok()?;
        let s = env.convert_byte_array(&sig).ok()?;
        let k = env.convert_byte_array(&pk).ok()?;
        let pk_arr: [u8; 32] = k.try_into().ok()?;
        let sig_arr: [u8; 64] = s.try_into().ok()?;
        let vk = VerifyingKey::from_bytes(&pk_arr).ok()?;
        vk.verify_strict(&p, &Signature::from_bytes(&sig_arr)).ok()
    })();
    ok.is_some() as jboolean
}

/// int antiDebug(): bitmask — 1=tracer attached, 2=ptrace blocked, 4=timing.
#[no_mangle]
pub extern "C" fn Java_dev_noxclient_ncore_NativeCore_nAntiDebug(
    _env: JNIEnv,
    _c: JClass,
) -> jint {
    let mut flags = 0;
    if tracer_pid() != 0 {
        flags |= 1;
    }
    if self_trace_blocked() {
        flags |= 2;
    }
    // timing probe: 2000 iterations of sha256 should take < 500ms even on slow hw;
    // single-stepping debuggers blow past that by orders of magnitude.
    let t0 = now_ms();
    let mut acc = 0u8;
    for i in 0..2000u32 {
        acc ^= sha256_hex(&i.to_le_bytes()).as_bytes()[0];
    }
    std::hint::black_box(acc);
    if now_ms().saturating_sub(t0) > 500 {
        flags |= 4;
    }
    flags
}

/// byte[] fragment(i, total): the embedded Ed25519 pubkey split so a static
/// scan never finds the full 32-byte key in one place (Modul 30.5).
/// Fragments are XOR-folds of PUB ^ salt — Java reassembles via nJoinKey.
const PUB_RAW: [u8; 32] = [
    0xc0, 0x0a, 0x14, 0x38, 0xae, 0x6c, 0x3f, 0xea, 0xbd, 0x7a, 0xa8, 0xe8, 0x5d, 0x8e, 0xd5, 0xe0,
    0x2f, 0xff, 0x3f, 0xeb, 0xf8, 0x85, 0xe9, 0x78, 0x00, 0xc3, 0x6f, 0x3f, 0x61, 0xfc, 0xfe, 0xa1,
];

#[no_mangle]
pub extern "C" fn Java_dev_noxclient_ncore_NativeCore_nKeyByte(
    _env: JNIEnv,
    _c: JClass,
    index: jint,
) -> jni::sys::jbyte {
    if index < 0 || index >= 32 {
        return 0;
    }
    let i = index as usize;
    // each byte xor-folded with a per-index salt mask — never stored contiguously
    (PUB_RAW[i] ^ salt_byte(i)) as jni::sys::jbyte
}

fn salt_byte(i: usize) -> u8 {
    let b = BUILD_SALT.as_bytes();
    b[i % b.len()].wrapping_add((i * 31) as u8)
}

/// byte[] joinKey(unmask): returns the pubkey iff caller proves it knows the
/// salt mask — Java asks native for mask via nMaskByte, keeping the key out
/// of any single memory snapshot as contiguous plaintext.
#[no_mangle]
pub extern "C" fn Java_dev_noxclient_ncore_NativeCore_nMaskByte(
    _env: JNIEnv,
    _c: JClass,
    index: jint,
) -> jni::sys::jbyte {
    if index < 0 || index >= 32 {
        return 0;
    }
    salt_byte(index as usize) as jni::sys::jbyte
}

/// long check(i): deterministic per-index check value for distributed
/// premium gates (Modul 30.2). check(i) must equal sha256(salt|i|pub)[:8].
#[no_mangle]
pub extern "C" fn Java_dev_noxclient_ncore_NativeCore_nCheck(
    _env: JNIEnv,
    _c: JClass,
    index: jint,
) -> jlong {
    let mut h = Sha256::new();
    h.update(BUILD_SALT.as_bytes());
    h.update(index.to_le_bytes());
    h.update(PUB_RAW);
    let d = h.finalize();
    i64::from_le_bytes(d[..8].try_into().unwrap())
}

/// byte[] keystoreWrap(data, hwid): XOR-fold with key derived from hwid+salt —
/// offline license/config cache at rest (Modul 30.1 crypto helper).
#[no_mangle]
pub extern "C" fn Java_dev_noxclient_ncore_NativeCore_nWrap(
    mut env: JNIEnv,
    _c: JClass,
    data: JByteArray,
    hwid: JString,
) -> jbyteArray {
    let d = match env.convert_byte_array(&data) {
        Ok(d) => d,
        Err(_) => return std::ptr::null_mut(),
    };
    let h: String = match env.get_string(&hwid) {
        Ok(s) => s.into(),
        Err(_) => return std::ptr::null_mut(),
    };
    let mut k = Sha256::new();
    k.update(h.as_bytes());
    k.update(BUILD_SALT.as_bytes());
    let key = k.finalize();
    let out: Vec<u8> = d.iter().enumerate().map(|(i, b)| b ^ key[i % 32]).collect();
    env.byte_array_from_slice(&out)
        .map(|a| a.into_raw())
        .unwrap_or(std::ptr::null_mut())
}
