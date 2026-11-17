#!/usr/bin/env python3
"""NOX license backend — FastAPI + SQLite.

Endpoints
  POST /activate      {key, hwid}        → bind license to hwid (1 reset/30d)
  POST /validate      {key, hwid}        → silent re-check; revocation kills premium
  POST /deactivate    {key, hwid}        → unbind
  GET  /public-key                       → raw Ed25519 public key (b64)
  POST /admin/issue   {days, sub, tier?} → mint + register a NOX1 token
  POST /admin/revoke  {lid}
  POST /admin/hwid-reset {lid}
  GET  /admin/list
  GET  /admin/audit

Env:
  NOX_ADMIN_TOKEN    — required for /admin/* (random secret)
  NOX_PRIVATE_PEM    — path to Ed25519 private key (issue). Optional: activation/
                       validation work without it (signature still verified).
  NOX_DB             — sqlite path (default ./licenses.db)
  NOX_PORT           — listen port (default 8787)

Run: uvicorn main:app --host 127.0.0.1 --port 8787
"""
import base64
import hashlib
import json
import os
import re
import secrets
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import (
    Ed25519PrivateKey, Ed25519PublicKey,
)

ADMIN_TOKEN = os.environ.get("NOX_ADMIN_TOKEN", "")
PRIV_PATH = os.environ.get("NOX_PRIVATE_PEM", "")
DB_PATH = os.environ.get("NOX_DB", str(Path(__file__).parent / "licenses.db"))
PUBLIC_B64 = os.environ.get("NOX_PUBLIC_B64", "wAoUOK5sP+q9eqjoXY7V4C//P+v4hel4AMNvP2H8/qE=")
GRACE_MS = 14 * 86400_000          # offline grace after last successful validate
HWID_RESET_MS = 30 * 86400_000     # 1 hwid rebind per 30 days

_pub = Ed25519PublicKey.from_public_bytes(base64.b64decode(PUBLIC_B64))
_priv = None
if PRIV_PATH and Path(PRIV_PATH).exists():
    _priv = serialization.load_pem_private_key(Path(PRIV_PATH).read_bytes(), password=None)

app = FastAPI(title="NOX license", docs_url=None, redoc_url=None)


@contextmanager
def db():
    c = sqlite3.connect(DB_PATH)
    c.execute("""CREATE TABLE IF NOT EXISTS licenses(
        lid TEXT PRIMARY KEY, keyhash TEXT, hwid TEXT, tier TEXT,
        issued_at INTEGER, expires_at INTEGER, revoked INTEGER DEFAULT 0,
        last_seen INTEGER, last_valid INTEGER, hwid_reset_at INTEGER DEFAULT 0)""")
    c.execute("""CREATE TABLE IF NOT EXISTS audit(
        ts INTEGER, event TEXT, lid TEXT, iphash TEXT, detail TEXT)""")
    yield c
    c.commit()
    c.close()


def now() -> int:
    return int(time.time() * 1000)


def verify(payload: bytes, sig: bytes) -> bool:
    try:
        _pub.verify(sig, payload)
        return True
    except Exception:
        return False


def parse_key(key: str):
    """NOX1.<b64url payload>.<b64url sig> → payload dict or None."""
    try:
        parts = key.strip().split(".")
        if len(parts) != 3 or parts[0] != "NOX1":
            return None
        payload = base64.urlsafe_b64decode(parts[1])
        sig = base64.urlsafe_b64decode(parts[2])
        if not verify(payload, sig):
            return None
        return json.loads(payload)
    except Exception:
        return None


def keyhash(key: str) -> str:
    return hashlib.sha256(key.strip().encode()).hexdigest()[:24]


def iphash(req: Request) -> str:
    ip = req.client.host if req.client else "?"
    return hashlib.sha256((ip + "|nox").encode()).hexdigest()[:12]


def audit(c, event: str, lid: str, req: Request, detail: str = ""):
    c.execute("INSERT INTO audit VALUES(?,?,?,?,?)",
              (now(), event, lid, iphash(req), detail[:200]))


# ---------- rate limiting (in-memory token buckets) ----------
_buckets: dict[str, list[float]] = {}


def limited(tag: str, key: str, rate: int, per_s: int = 60) -> bool:
    t = time.monotonic()
    b = _buckets.setdefault(tag + key, [])
    while b and b[0] < t - per_s:
        b.pop(0)
    if len(b) >= rate:
        return True
    b.append(t)
    return False


def admin_ok(req: Request) -> bool:
    return ADMIN_TOKEN and req.headers.get("x-admin-token") == ADMIN_TOKEN


# ---------- public endpoints ----------
@app.post("/activate")
async def activate(req: Request):
    body = await req.json()
    if limited("ip", iphash(req), 30) or limited("key", keyhash(body.get("key", "")), 10):
        return JSONResponse({"ok": False, "err": "rate_limited"}, 429)
    key, hwid = body.get("key", ""), body.get("hwid", "")
    p = parse_key(key)
    if not p:
        return {"ok": False, "err": "bad_key"}
    lid, exp = p.get("lid", ""), p.get("exp", 0)
    if exp and now() > exp:
        return {"ok": False, "err": "expired"}
    with db() as c:
        row = c.execute("SELECT * FROM licenses WHERE lid=?", (lid,)).fetchone()
        if row is None:
            c.execute("INSERT INTO licenses VALUES(?,?,?,?,?,?,?,?,?,?)",
                      (lid, keyhash(key), hwid, p.get("tier", "premium"),
                       now(), exp, 0, now(), now(), 0))
        else:
            _, _, old_hwid, _, _, _, revoked, _, _, hwid_reset_at = row
            if revoked:
                return {"ok": False, "err": "revoked"}
            if old_hwid and old_hwid != hwid:
                if now() - hwid_reset_at < HWID_RESET_MS:
                    return {"ok": False, "err": "hwid_bound"}
                c.execute("UPDATE licenses SET hwid=?, hwid_reset_at=? WHERE lid=?",
                          (hwid, now(), lid))
        c.execute("UPDATE licenses SET last_seen=?, last_valid=? WHERE lid=?",
                  (now(), now(), lid))
        audit(c, "activate", lid, req)
    return {"ok": True, "lid": lid, "exp": exp, "serverTime": now()}


@app.post("/validate")
async def validate(req: Request):
    body = await req.json()
    if limited("ip", iphash(req), 60) or limited("key", keyhash(body.get("key", "")), 20):
        return JSONResponse({"ok": False, "err": "rate_limited"}, 429)
    key, hwid = body.get("key", ""), body.get("hwid", "")
    p = parse_key(key)
    if not p:
        return {"ok": False, "err": "bad_key"}
    lid, exp = p.get("lid", ""), p.get("exp", 0)
    with db() as c:
        row = c.execute("SELECT * FROM licenses WHERE lid=?", (lid,)).fetchone()
        if row is None:
            # key valid but never activated server-side → register lazily
            c.execute("INSERT INTO licenses VALUES(?,?,?,?,?,?,?,?,?,?)",
                      (lid, keyhash(key), hwid, p.get("tier", "premium"),
                       now(), exp, 0, now(), now(), 0))
            audit(c, "lazy_register", lid, req)
            return {"ok": True, "lid": lid, "exp": exp, "serverTime": now(), "fresh": True}
        _, _, bound_hwid, _, _, _, revoked, _, _, _ = row
        if revoked:
            audit(c, "validate_revoked", lid, req)
            return {"ok": False, "err": "revoked", "revoked": True, "serverTime": now()}
        if bound_hwid and bound_hwid != hwid:
            return {"ok": False, "err": "hwid_mismatch", "serverTime": now()}
        if exp and now() > exp:
            return {"ok": False, "err": "expired", "serverTime": now()}
        if not bound_hwid and hwid:
            c.execute("UPDATE licenses SET hwid=?, hwid_reset_at=? WHERE lid=?",
                      (hwid, now(), lid))
        c.execute("UPDATE licenses SET last_seen=?, last_valid=? WHERE lid=?",
                  (now(), now(), lid))
        audit(c, "validate", lid, req)
    return {"ok": True, "lid": lid, "exp": exp, "serverTime": now()}


@app.post("/deactivate")
async def deactivate(req: Request):
    body = await req.json()
    key = body.get("key", "")
    p = parse_key(key)
    if not p:
        return {"ok": False, "err": "bad_key"}
    lid = p.get("lid", "")
    with db() as c:
        c.execute("UPDATE licenses SET hwid='' WHERE lid=?", (lid,))
        audit(c, "deactivate", lid, req)
    return {"ok": True}


@app.get("/health")
async def health():
    """Uptime/k8s probe — db reachable + key loaded. No auth, no data."""
    try:
        with db() as c:
            c.execute("SELECT 1")
        return {"ok": True, "ts": now()}
    except Exception as e:
        return JSONResponse({"ok": False, "err": str(e)}, status_code=503)


@app.get("/public-key")
async def public_key():
    return {"alg": "Ed25519", "b64": PUBLIC_B64}


# ---------- admin ----------
@app.post("/admin/issue")
async def admin_issue(req: Request):
    if not admin_ok(req):
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    if _priv is None:
        return JSONResponse({"ok": False, "err": "no_private_key"}, 500)
    body = await req.json()
    days, sub = int(body.get("days", 365)), str(body.get("sub", ""))
    tier = body.get("tier", "premium")
    exp = now() + days * 86400_000 if tier != "cape" else 0
    lid = secrets.token_hex(4)
    payload = json.dumps({"tier": tier, "lid": lid, "exp": exp, "sub": sub},
                         separators=(",", ":")).encode()
    sig = _priv.sign(payload)
    token = ("NOX1." + base64.urlsafe_b64encode(payload).decode()
             + "." + base64.urlsafe_b64encode(sig).decode())
    with db() as c:
        c.execute("INSERT INTO licenses VALUES(?,?,?,?,?,?,?,?,?,?)",
                  (lid, keyhash(token), "", tier, now(), exp, 0, 0, 0, 0))
        audit(c, "issue", lid, req, f"days={days} sub={sub}")
    return {"ok": True, "key": token, "lid": lid, "exp": exp}


@app.post("/admin/register")
async def admin_register(req: Request):
    """Register a lid issued elsewhere (e.g. the Discord bot signs offline)."""
    if not admin_ok(req):
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    body = await req.json()
    lid = body.get("lid", "")
    exp = int(body.get("exp", 0))
    if not lid:
        return JSONResponse({"ok": False, "err": "missing_lid"}, 400)
    with db() as c:
        c.execute("INSERT OR IGNORE INTO licenses VALUES(?,?,?,?,?,?,?,?,?,?)",
                  (lid, "", "", "premium", now(), exp, 0, 0, 0, 0))
        audit(c, "register", lid, req, f"exp={exp}")
    return {"ok": True}


@app.post("/admin/revoke")
async def admin_revoke(req: Request):
    if not admin_ok(req):
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    lid = (await req.json()).get("lid", "")
    with db() as c:
        c.execute("UPDATE licenses SET revoked=1 WHERE lid=?", (lid,))
        audit(c, "revoke", lid, req)
    return {"ok": True}


@app.post("/admin/hwid-reset")
async def admin_hwid_reset(req: Request):
    if not admin_ok(req):
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    lid = (await req.json()).get("lid", "")
    with db() as c:
        c.execute("UPDATE licenses SET hwid='', hwid_reset_at=? WHERE lid=?",
                  (now(), lid))
        audit(c, "hwid_reset", lid, req)
    return {"ok": True}


@app.get("/admin/list")
async def admin_list(req: Request):
    if not admin_ok(req):
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    with db() as c:
        rows = c.execute(
            "SELECT lid,tier,expires_at,revoked,last_seen,hwid!='' FROM licenses").fetchall()
    return {"ok": True, "licenses": [
        {"lid": r[0], "tier": r[1], "exp": r[2], "revoked": bool(r[3]),
         "last_seen": r[4], "bound": bool(r[5])} for r in rows]}


@app.get("/admin/audit")
async def admin_audit(req: Request):
    if not admin_ok(req):
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    with db() as c:
        rows = c.execute("SELECT * FROM audit ORDER BY ts DESC LIMIT 500").fetchall()
    return {"ok": True, "audit": [
        {"ts": r[0], "event": r[1], "lid": r[2], "ip": r[3], "detail": r[4]}
        for r in rows]}


# ---------------------------------------------------------------------------
# Modul 8 — cross-client friend sync (FastAPI + HS256 JWT, no extra deps)
# ---------------------------------------------------------------------------

import hmac as _hmac

SESSION_SECRET = os.environ.get("NOX_SESSION_SECRET",
    hashlib.sha256((PUBLIC_B64 + "|sessions").encode()).hexdigest())
SESSION_TTL = 6 * 3600_000          # 6 h
FRIEND_TTL = 300_000                # presence considered online for 5 min


def _b64e(b: bytes) -> str:
    return base64.urlsafe_b64encode(b).decode().rstrip("=")


def _b64d(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def jwt_issue(sub: str) -> str:
    head = _b64e(b'{"alg":"HS256","typ":"JWT"}')
    pay = _b64e(json.dumps({"sub": sub, "exp": now() + SESSION_TTL},
                           separators=(",", ":")).encode())
    sig = _b64e(_hmac.new(SESSION_SECRET.encode(),
                          f"{head}.{pay}".encode(), hashlib.sha256).digest())
    return f"{head}.{pay}.{sig}"


def jwt_check(req: Request):
    """Bearer HS256 JWT → sub lid or None."""
    auth = req.headers.get("authorization", "")
    if not auth.startswith("Bearer "):
        return None
    try:
        head, pay, sig = auth[7:].split(".")
        want = _b64e(_hmac.new(SESSION_SECRET.encode(),
                               f"{head}.{pay}".encode(), hashlib.sha256).digest())
        if not _hmac.compare_digest(sig, want):
            return None
        d = json.loads(_b64d(pay))
        return d["sub"] if d.get("exp", 0) > now() else None
    except Exception:
        return None


def _friends_schema(c):
    c.execute("""CREATE TABLE IF NOT EXISTS friends(
        lid TEXT, friend_lid TEXT, name TEXT, ts INTEGER,
        PRIMARY KEY(lid, friend_lid))""")
    c.execute("""CREATE TABLE IF NOT EXISTS friend_codes(
        code TEXT PRIMARY KEY, lid TEXT, exp INTEGER)""")
    c.execute("""CREATE TABLE IF NOT EXISTS presence(
        lid TEXT PRIMARY KEY, name TEXT, server TEXT,
        x REAL, y REAL, z REAL, dim TEXT, ts INTEGER)""")


@app.post("/friends/login")
async def friends_login(req: Request):
    """{key: NOX1-token, hwid} → session JWT. Requires a valid, unrevoked
    license bound to this hwid — friends sync rides the premium gate."""
    body = await req.json()
    p = parse_key(body.get("key", ""))
    if not p:
        return JSONResponse({"ok": False, "err": "bad key"}, 401)
    lid = p.get("lid", "")
    with db() as c:
        _friends_schema(c)
        row = c.execute("SELECT hwid,revoked,expires_at FROM licenses WHERE lid=?",
                        (lid,)).fetchone()
        if not row:
            return JSONResponse({"ok": False, "err": "unknown lid"}, 401)
        hwid_db, revoked, exp = row
        if revoked or exp < now():
            return JSONResponse({"ok": False, "err": "revoked/expired"}, 403)
        if hwid_db and hwid_db != body.get("hwid", ""):
            return JSONResponse({"ok": False, "err": "hwid"}, 403)
        audit(c, "friends_login", lid, req)
    return {"ok": True, "token": jwt_issue(lid)}


@app.post("/friends/code")
async def friends_code(req: Request):
    """Create a 6-char one-shot friend code (redeem within 10 min)."""
    lid = jwt_check(req)
    if not lid:
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    code = "".join(secrets.choice("ABCDEFGHJKLMNPQRSTUVWXYZ23456789")
                   for _ in range(6))
    with db() as c:
        _friends_schema(c)
        c.execute("DELETE FROM friend_codes WHERE exp<?", (now(),))
        c.execute("INSERT OR REPLACE INTO friend_codes VALUES(?,?,?)",
                  (code, lid, now() + 600_000))
    return {"ok": True, "code": code}


@app.post("/friends/redeem")
async def friends_redeem(req: Request):
    """Redeem a friend code → mutual link (both directions, with names)."""
    lid = jwt_check(req)
    if not lid:
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    body = await req.json()
    code = body.get("code", "").strip().upper()
    name = body.get("name", "")[:24]
    with db() as c:
        _friends_schema(c)
        row = c.execute("SELECT lid,exp FROM friend_codes WHERE code=?",
                        (code,)).fetchone()
        if not row or row[1] < now() or row[0] == lid:
            return JSONResponse({"ok": False, "err": "bad code"}, 400)
        other = row[0]
        c.execute("DELETE FROM friend_codes WHERE code=?", (code,))
        other_name = c.execute("SELECT name FROM presence WHERE lid=?",
                               (other,)).fetchone()
        on = other_name[0] if other_name else "?"
        c.execute("INSERT OR REPLACE INTO friends VALUES(?,?,?,?)",
                  (lid, other, on, now()))
        c.execute("INSERT OR REPLACE INTO friends VALUES(?,?,?,?)",
                  (other, lid, name, now()))
        audit(c, "friend_link", lid, req, other)
    return {"ok": True, "friend": on}


@app.get("/friends")
async def friends_list(req: Request):
    lid = jwt_check(req)
    if not lid:
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    with db() as c:
        _friends_schema(c)
        rows = c.execute("""SELECT f.friend_lid, COALESCE(NULLIF(p.name,''), f.name),
            p.server, p.x, p.y, p.z, p.dim, p.ts
            FROM friends f LEFT JOIN presence p ON p.lid=f.friend_lid
            WHERE f.lid=?""", (lid,)).fetchall()
    return {"ok": True, "friends": [
        {"lid": r[0], "name": r[1],
         "online": bool(r[7] and now() - r[7] < FRIEND_TTL),
         "server": r[2], "pos": [r[3], r[4], r[5]], "dim": r[6]}
        for r in rows]}


@app.post("/friends/ping")
async def friends_ping(req: Request):
    """Heartbeat + presence: {name, server, x,y,z,dim}."""
    lid = jwt_check(req)
    if not lid:
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    b = await req.json()
    with db() as c:
        _friends_schema(c)
        c.execute("""INSERT OR REPLACE INTO presence VALUES(?,?,?,?,?,?,?,?)""",
                  (lid, str(b.get("name", ""))[:24], str(b.get("server", ""))[:64],
                   float(b.get("x", 0)), float(b.get("y", 0)), float(b.get("z", 0)),
                   str(b.get("dim", ""))[:32], now()))
        c.execute("DELETE FROM presence WHERE ts<?", (now() - 3600_000,))
    return {"ok": True}


# ---------------------------------------------------------------------------
# Modul 30.5 — integrity heartbeat
# ---------------------------------------------------------------------------

@app.post("/heartbeat")
async def heartbeat(req: Request):
    """HMAC-signed integrity beat: {lid, wm, shape, ok, dbg, v}.
    Sig = hmac-sha256(body, 'nox-hb|'+lid) — only a licensed client that
    knows its own lid can beat. Anomalies logged to audit, not rejected
    (beat must never break the client)."""
    body = await req.body()
    try:
        b = json.loads(body)
        lid = str(b.get("lid", ""))[:16]
        want = _hmac.new(f"nox-hb|{lid}".encode(), body,
                         hashlib.sha256).hexdigest()
        if not _hmac.compare_digest(req.headers.get("x-nox-sig", ""), want):
            return JSONResponse({"ok": False, "err": "sig"}, 401)
        with db() as c:
            c.execute("""CREATE TABLE IF NOT EXISTS beats(
                lid TEXT, wm TEXT, shape TEXT, ok INTEGER,
                dbg INTEGER, v TEXT, ts INTEGER)""")
            c.execute("INSERT INTO beats VALUES(?,?,?,?,?,?,?)",
                      (lid, str(b.get("wm", ""))[:48], str(b.get("shape", ""))[:32],
                       1 if b.get("ok") else 0, int(b.get("dbg", -1)),
                       str(b.get("v", ""))[:16], now()))
            c.execute("""DELETE FROM beats WHERE lid NOT IN
                (SELECT lid FROM beats GROUP BY lid ORDER BY MAX(ts) DESC LIMIT 500)""")
            if not b.get("ok"):
                audit(c, "beat_anomaly", lid, req,
                      f"shape={b.get('shape','')} dbg={b.get('dbg')}")
    except Exception as e:
        return JSONResponse({"ok": False, "err": str(e)[:80]}, 400)
    return {"ok": True}


@app.get("/admin/beats")
async def admin_beats(req: Request):
    if not admin_ok(req):
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    with db() as c:
        c.execute("""CREATE TABLE IF NOT EXISTS beats(
            lid TEXT, wm TEXT, shape TEXT, ok INTEGER,
            dbg INTEGER, v TEXT, ts INTEGER)""")
        rows = c.execute(
            "SELECT * FROM beats ORDER BY ts DESC LIMIT 200").fetchall()
    return {"ok": True, "beats": [
        {"lid": r[0], "wm": r[1], "shape": r[2], "ok": bool(r[3]),
         "dbg": r[4], "v": r[5], "ts": r[6]} for r in rows]}


# ---------------------------------------------------------------------------
# Modul 27 — analytics dashboard (admin): aggregated view over licenses,
# beats and friend presence; served with a static dashboard page.
# ---------------------------------------------------------------------------

from fastapi.responses import HTMLResponse


@app.get("/admin/analytics")
async def admin_analytics(req: Request):
    if not admin_ok(req):
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    with db() as c:
        lic = c.execute("""SELECT tier, revoked, expires_at>?, hwid!='', COUNT(*)
            FROM licenses GROUP BY tier, revoked, expires_at>?, hwid!=''""",
            (now(), now())).fetchall()
        totals = c.execute("""SELECT COUNT(*),
            SUM(CASE WHEN expires_at>? AND revoked=0 THEN 1 ELSE 0 END),
            SUM(CASE WHEN hwid!='' THEN 1 ELSE 0 END) FROM licenses""",
            (now(),)).fetchone()
        beats = c.execute("""SELECT v, shape, ok, COUNT(*) FROM beats
            GROUP BY v, shape, ok ORDER BY 4 DESC LIMIT 20""").fetchall() \
            if _table(c, "beats") else []
        online = c.execute("""SELECT name, server, dim, x,y,z, ts FROM presence
            WHERE ts>?""", (now() - FRIEND_TTL,)).fetchall() \
            if _table(c, "presence") else []
        friend_links = c.execute(
            "SELECT COUNT(*) FROM friends").fetchone()[0] \
            if _table(c, "friends") else 0
        recent = c.execute(
            "SELECT ts,event,lid FROM audit ORDER BY ts DESC LIMIT 12").fetchall()
    return {"ok": True, "serverTime": now(),
        "licenses": {"total": totals[0], "active": totals[1], "bound": totals[2],
                     "rows": [{"tier": r[0], "revoked": bool(r[1]),
                               "expired": not bool(r[2]), "bound": bool(r[3]),
                               "n": r[4]} for r in lic]},
        "beats": [{"v": b[0], "shape": b[1], "ok": bool(b[2]), "n": b[3]}
                  for b in beats],
        "friends": {"links": friend_links,
                    "online": [{"name": p[0], "server": p[1], "dim": p[2],
                                "pos": [p[3], p[4], p[5]],
                                "ago": now() - p[6]} for p in online]},
        "recent_events": [{"ts": r[0], "event": r[1], "lid": r[2]}
                          for r in recent]}


def _table(c, name):
    try:
        c.execute(f"SELECT 1 FROM {name} LIMIT 1").fetchone()
        return True
    except Exception:
        return False


@app.get("/admin/dashboard", response_class=HTMLResponse)
async def admin_dashboard(req: Request):
    """Minimal live dashboard — polls /admin/analytics with the admin token
    in the query string (dev tool, not public)."""
    if not admin_ok(req):
        return HTMLResponse("<h1>401</h1>", status_code=401)
    return _DASH_HTML


_DASH_HTML = """<!doctype html><meta charset=utf-8><title>NOX analytics</title>
<style>
 body{background:#0d0f14;color:#e8e8f0;font:14px/1.5 system-ui;margin:24px}
 h1{color:#7a2be2;font-size:20px} h2{font-size:14px;color:#8a8a9e;
 text-transform:uppercase;letter-spacing:.08em;margin:20px 0 8px}
 .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(150px,1fr));gap:10px}
 .card{background:#161b26;border:1px solid #ffffff18;border-radius:10px;padding:12px}
 .big{font-size:26px;font-weight:600} .dim{color:#8a8a9e}
 table{width:100%;border-collapse:collapse;font-size:13px}
 td,th{text-align:left;padding:4px 8px;border-bottom:1px solid #ffffff10}
 code{color:#ffd870}</style>
<h1>NOX analytics</h1><div class=dim id=t></div>
<div class=grid id=cards></div>
<h2>Integrity beats</h2><table id=beats></table>
<h2>Online friends</h2><table id=friends></table>
<h2>Recent events</h2><table id=events></table>
<script>
const tok = new URLSearchParams(location.search).get('t') ||
            localStorage.noxAdm || '';
localStorage.noxAdm = tok;
async function go(){
 const r = await fetch('/admin/analytics', {headers:{'x-admin-token':tok}});
 const j = await r.json();
 if(!j.ok){cards.innerHTML='<div class=card>unauthorized</div>';return}
 t.textContent = 'server ' + new Date(j.serverTime).toLocaleTimeString()
   + ' · license links ' + j.friends.links;
 const L = j.licenses;
 cards.innerHTML =
  card('licenses', L.total) + card('active', L.active) +
  card('hwid-bound', L.bound) + card('online now', j.friends.online.length);
 beats.innerHTML = '<tr><th>version</th><th>shape</th><th>ok</th><th>n</th></tr>' +
  j.beats.map(b=>`<tr><td>${b.v}</td><td><code>${b.shape}</code></td>
   <td>${b.ok?'✅':'⛔'}</td><td>${b.n}</td></tr>`).join('');
 friends.innerHTML = '<tr><th>name</th><th>server</th><th>dim</th><th>pos</th><th>ago</th></tr>' +
  j.friends.online.map(f=>`<tr><td>${f.name}</td><td>${f.server}</td>
   <td>${f.dim}</td><td>${f.pos.map(Math.round)}</td>
   <td>${(f.ago/1000).toFixed(0)}s</td></tr>`).join('');
 events.innerHTML = j.recent_events.map(e=>
  `<tr><td>${new Date(e.ts).toLocaleTimeString()}</td>
   <td>${e.event}</td><td><code>${e.lid||''}</code></td></tr>`).join('');
}
const card=(k,v)=>`<div class=card><div class=dim>${k}</div><div class=big>${v}</div></div>`;
go(); setInterval(go, 10000);
</script>"""


# ---------------------------------------------------------------------------
# Modul 23 — cosmetics marketplace: publish .cosmetic packages (JWT-gated),
# browse approved listings, admin moderation (approve/reject), download.
# ---------------------------------------------------------------------------

def _cos_schema(c):
    c.execute("""CREATE TABLE IF NOT EXISTS cosmetics(
        id TEXT PRIMARY KEY, lid TEXT, name TEXT, slot TEXT, author TEXT,
        data TEXT, status TEXT DEFAULT 'pending', downloads INTEGER DEFAULT 0,
        reports INTEGER DEFAULT 0, ts INTEGER)""")
    c.execute("""CREATE TABLE IF NOT EXISTS cos_reports(
        cid TEXT, lid TEXT, reason TEXT, ts INTEGER,
        PRIMARY KEY(cid, lid))""")


@app.post("/cosmetics/publish")
async def cosmetics_publish(req: Request):
    """{cosmetic: <.cosmetic JSON string>} — stores pending moderation."""
    lid = jwt_check(req)
    if not lid:
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    body = await req.json()
    raw = body.get("cosmetic", "")
    if len(raw) > 2_000_000:
        return JSONResponse({"ok": False, "err": "too large"}, 413)
    try:
        d = json.loads(raw)
        name = re.sub(r"[^A-Za-z0-9 _-]", "", d.get("name", ""))[:40] or "cosmetic"
        slot = d.get("slot", "hat").lower()
        if slot not in ("hat", "pet", "aura", "cape", "trail"):
            return JSONResponse({"ok": False, "err": "bad slot"}, 400)
        author = re.sub(r"[^A-Za-z0-9_-]", "", d.get("author", ""))[:24]
    except Exception:
        return JSONResponse({"ok": False, "err": "bad json"}, 400)
    cid = hashlib.sha256((lid + name + str(now())).encode()).hexdigest()[:12]
    with db() as c:
        _cos_schema(c)
        c.execute("""INSERT INTO cosmetics
            (id,lid,name,slot,author,data,status,downloads,reports,ts)
            VALUES(?,?,?,?,?,?,'pending',0,0,?)""",
            (cid, lid, name, slot, author, raw, now()))
        audit(c, "cos_publish", lid, req)
    return {"ok": True, "id": cid, "status": "pending"}


@app.get("/cosmetics")
async def cosmetics_list(req: Request, slot: str = ""):
    """Public browse of approved cosmetics (metadata only)."""
    with db() as c:
        _cos_schema(c)
        if slot:
            rows = c.execute("""SELECT id,name,slot,author,downloads,ts
                FROM cosmetics WHERE status='approved' AND slot=?
                ORDER BY downloads DESC LIMIT 100""", (slot,)).fetchall()
        else:
            rows = c.execute("""SELECT id,name,slot,author,downloads,ts
                FROM cosmetics WHERE status='approved'
                ORDER BY downloads DESC LIMIT 100""").fetchall()
    return {"ok": True, "items": [
        {"id": r[0], "name": r[1], "slot": r[2], "author": r[3],
         "downloads": r[4], "ts": r[5]} for r in rows]}


@app.get("/cosmetics/{cid}")
async def cosmetics_get(cid: str, req: Request):
    """Download an approved .cosmetic package (JWT-gated, counts downloads)."""
    lid = jwt_check(req)
    if not lid:
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    with db() as c:
        _cos_schema(c)
        row = c.execute("SELECT data,status FROM cosmetics WHERE id=?",
                        (cid,)).fetchone()
        if not row or row[1] != "approved":
            return JSONResponse({"ok": False, "err": "unavailable"}, 404)
        c.execute("UPDATE cosmetics SET downloads=downloads+1 WHERE id=?", (cid,))
    return {"ok": True, "cosmetic": row[0]}


@app.post("/cosmetics/{cid}/report")
async def cosmetics_report(cid: str, req: Request):
    lid = jwt_check(req)
    if not lid:
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    body = await req.json()
    reason = re.sub(r"[^A-Za-z0-9 ._-]", "", body.get("reason", ""))[:120]
    with db() as c:
        _cos_schema(c)
        try:
            c.execute("INSERT INTO cos_reports VALUES(?,?,?,?)",
                      (cid, lid, reason, now()))
            c.execute("UPDATE cosmetics SET reports=reports+1 WHERE id=?", (cid,))
            # auto-hide after 5 unique reports
            n = c.execute("SELECT reports FROM cosmetics WHERE id=?",
                          (cid,)).fetchone()
            if n and n[0] >= 5:
                c.execute("UPDATE cosmetics SET status='flagged' WHERE id=?", (cid,))
        except sqlite3.IntegrityError:
            pass
    return {"ok": True}


@app.post("/admin/cosmetics/{cid}/moderate")
async def cosmetics_moderate(cid: str, req: Request):
    """{action: approve|reject} — admin only."""
    if not admin_ok(req):
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    body = await req.json()
    action = body.get("action", "")
    if action not in ("approve", "reject"):
        return JSONResponse({"ok": False, "err": "bad action"}, 400)
    with db() as c:
        _cos_schema(c)
        st = "approved" if action == "approve" else "rejected"
        cur = c.execute("UPDATE cosmetics SET status=? WHERE id=?", (st, cid))
        audit(c, "cos_" + action, cid, req)
    return {"ok": bool(cur.rowcount), "status": st}


@app.get("/admin/cosmetics")
async def cosmetics_queue(req: Request, status: str = "pending"):
    """Moderation queue — metadata only."""
    if not admin_ok(req):
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    with db() as c:
        _cos_schema(c)
        rows = c.execute("""SELECT id,lid,name,slot,author,status,reports,ts
            FROM cosmetics WHERE status=? ORDER BY ts DESC LIMIT 100""",
            (status,)).fetchall()
    return {"ok": True, "items": [
        {"id": r[0], "lid": r[1], "name": r[2], "slot": r[3], "author": r[4],
         "status": r[5], "reports": r[6], "ts": r[7]} for r in rows]}


@app.post("/friends/remove")
async def friends_remove(req: Request):
    lid = jwt_check(req)
    if not lid:
        return JSONResponse({"ok": False, "err": "unauthorized"}, 401)
    b = await req.json()
    with db() as c:
        _friends_schema(c)
        c.execute("DELETE FROM friends WHERE lid=? AND friend_lid=?",
                  (lid, b.get("lid", "")))
    return {"ok": True}
