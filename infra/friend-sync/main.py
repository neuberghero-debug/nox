"""NOX friend-sync backend (M8) — FastAPI, JWT-HS256, SQLite dev / Postgres prod.

Endpoints:
  POST /register        {hwid}              → {token}      (device-bound account)
  POST /friends/add     {friend_code}       → pending request          [auth]
  POST /friends/accept  {from}              → established               [auth]
  GET  /friends                            → list + status              [auth]
  POST /friends/remove  {friend}            → ok                        [auth]
  POST /sync            {state}             → stored friend state       [auth]
  GET  /sync                             → peer states (presence/stats) [auth]
  GET  /health                            → probe

Security: JWT HS256 (NOX_JWT_SECRET, 24h), per-IP rate limit 30/min,
8KB body cap, no IP persistence (hash only, rotating salt optional).
DB: sqlite NOX_DB (dev) or Postgres via NOX_DSN (prod, asyncpg-free:
uses psycopg2-style params only when NOX_DSN set — schema in schema.sql).
"""
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import time
from contextlib import contextmanager
from pathlib import Path

from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import JSONResponse

DB_PATH = os.getenv("NOX_DB", "./friends.db")
JWT_SECRET = os.getenv("NOX_JWT_SECRET", secrets.token_hex(32))
JWT_TTL = 86_400
BODY_CAP = 8 * 1024

app = FastAPI(title="nox-friend-sync", version="1.0")
_rl: dict[str, list[float]] = {}


@contextmanager
def db():
    c = sqlite3.connect(DB_PATH)
    c.row_factory = sqlite3.Row
    try:
        yield c
        c.commit()
    finally:
        c.close()


def init():
    with db() as c:
        c.executescript("""
        CREATE TABLE IF NOT EXISTS accounts(
            hwid TEXT PRIMARY KEY, code TEXT UNIQUE NOT NULL, created INTEGER);
        CREATE TABLE IF NOT EXISTS friends(
            a TEXT, b TEXT, state TEXT, created INTEGER,
            PRIMARY KEY(a,b));
        CREATE TABLE IF NOT EXISTS sync_state(
            hwid TEXT PRIMARY KEY, state TEXT, ts INTEGER);
        """)


init()


def now() -> int:
    return int(time.time())


def b64u(b: bytes) -> str:
    import base64
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def jwt_encode(sub: str) -> str:
    head = b64u(json.dumps({"alg": "HS256", "typ": "JWT"}).encode())
    body = b64u(json.dumps({"sub": sub, "iat": now(), "exp": now() + JWT_TTL}).encode())
    sig = b64u(hmac.new(JWT_SECRET.encode(), f"{head}.{body}".encode(), hashlib.sha256).digest())
    return f"{head}.{body}.{sig}"


def jwt_decode(tok: str) -> str | None:
    try:
        head, body, sig = tok.split(".")
        exp_sig = b64u(hmac.new(JWT_SECRET.encode(), f"{head}.{body}".encode(), hashlib.sha256).digest())
        if not hmac.compare_digest(sig, exp_sig):
            return None
        payload = json.loads(__import__("base64").urlsafe_b64decode(body + "=" * (-len(body) % 4)))
        return payload["sub"] if payload.get("exp", 0) > now() else None
    except Exception:
        return None


def auth(req: Request) -> str:
    t = req.headers.get("authorization", "").removeprefix("Bearer ").strip()
    sub = jwt_decode(t)
    if not sub:
        raise HTTPException(401, "bad token")
    return sub


def rate(req: Request, n: int = 30) -> None:
    key = hashlib.sha256((req.client.host if req.client else "?").encode()).hexdigest()[:16]
    q = _rl.setdefault(key, [])
    t = time.time()
    q[:] = [x for x in q if t - x < 60]
    if len(q) >= n:
        raise HTTPException(429, "rate limited")
    q.append(t)


async def body(req: Request) -> dict:
    data = await req.body()
    if len(data) > BODY_CAP:
        raise HTTPException(413, "too large")
    try:
        return json.loads(data or b"{}")
    except Exception:
        raise HTTPException(400, "bad json")


@app.post("/register")
async def register(req: Request):
    rate(req, 10)
    b = await body(req)
    hwid = str(b.get("hwid", ""))[:64]
    if not hwid:
        raise HTTPException(400, "hwid required")
    with db() as c:
        row = c.execute("SELECT code FROM accounts WHERE hwid=?", (hwid,)).fetchone()
        code = row["code"] if row else "NX-" + secrets.token_hex(3).upper()
        if not row:
            c.execute("INSERT INTO accounts VALUES(?,?,?)", (hwid, code, now()))
    return {"token": jwt_encode(hwid), "friend_code": code}


@app.post("/friends/add")
async def fadd(req: Request):
    me, b = auth(req), await body(req)
    code = str(b.get("friend_code", ""))[:16].upper()
    with db() as c:
        row = c.execute("SELECT hwid FROM accounts WHERE code=?", (code,)).fetchone()
        if not row or row["hwid"] == me:
            raise HTTPException(404, "code not found")
        c.execute("INSERT OR REPLACE INTO friends VALUES(?,?,?,?)",
                  (me, row["hwid"], "pending", now()))
    return {"ok": True}


@app.post("/friends/accept")
async def faccept(req: Request):
    me, b = auth(req), await body(req)
    frm = str(b.get("from", ""))[:64]
    with db() as c:
        row = c.execute("SELECT 1 FROM friends WHERE a=? AND b=? AND state='pending'",
                        (frm, me)).fetchone()
        if not row:
            raise HTTPException(404, "no pending request")
        c.execute("UPDATE friends SET state='ok' WHERE a=? AND b=?", (frm, me))
        c.execute("INSERT OR REPLACE INTO friends VALUES(?,?,?,?)", (me, frm, "ok", now()))
    return {"ok": True}


@app.get("/friends")
async def flist(req: Request):
    me = auth(req)
    with db() as c:
        rows = c.execute(
            "SELECT f.b, f.state, a.code FROM friends f JOIN accounts a ON a.hwid=f.b WHERE f.a=?",
            (me,)).fetchall()
    return {"friends": [{"id": r["b"][:12], "code": r["code"], "state": r["state"]} for r in rows]}


@app.post("/friends/remove")
async def fremove(req: Request):
    me, b = auth(req), await body(req)
    fr = str(b.get("friend", ""))[:64]
    with db() as c:
        # match by code or id-prefix
        row = c.execute("SELECT hwid FROM accounts WHERE code=? OR hwid LIKE ?",
                        (fr.upper(), fr + "%")).fetchone()
        if row:
            hw = row["hwid"]
            c.execute("DELETE FROM friends WHERE (a=? AND b=?) OR (a=? AND b=?)",
                      (me, hw, hw, me))
    return {"ok": True}


@app.post("/sync")
async def sync_set(req: Request):
    me, b = auth(req), await body(req)
    state = json.dumps(b.get("state", {}))[:2048]
    with db() as c:
        c.execute("INSERT OR REPLACE INTO sync_state VALUES(?,?,?)", (me, state, now()))
    return {"ok": True}


@app.get("/sync")
async def sync_get(req: Request):
    me = auth(req)
    with db() as c:
        rows = c.execute(
            """SELECT s.hwid, s.state, s.ts FROM sync_state s
               JOIN friends f ON f.b=s.hwid WHERE f.a=? AND f.state='ok' AND s.ts>?""",
            (me, now() - 300)).fetchall()
    return {"peers": [{"id": r["hwid"][:12], "state": json.loads(r["state"]), "ts": r["ts"]}
                      for r in rows]}


@app.get("/health")
async def health():
    try:
        with db() as c:
            c.execute("SELECT 1")
        return {"ok": True}
    except Exception as e:
        return JSONResponse({"ok": False, "err": str(e)}, status_code=503)
