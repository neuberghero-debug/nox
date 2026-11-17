"""NOX analytics backend (M27) — aggregates telemetry batches from opted-in
clients and serves KPIs to the Discord bot.

Ingest: POST /ingest {batch} — HMAC-signed by client (Telemetry), rate-limited,
        anonymized before storage (no hwid/IP paths).
Query:  GET /kpis?days=7 — X-Api-Key header (NOX_ANALYTICS_KEY).
        GET /health
"""
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import time
from contextlib import contextmanager

from fastapi import FastAPI, Request, HTTPException
from fastapi.responses import JSONResponse

DB = os.getenv("NOX_DB", "./analytics.db")
API_KEY = os.getenv("NOX_ANALYTICS_KEY", "")
INGEST_HMAC = os.getenv("NOX_INGEST_HMAC", secrets.token_hex(32))
BODY_CAP = 8 * 1024

app = FastAPI(title="nox-analytics", version="1.0")
_rl: dict[str, list[float]] = {}


@contextmanager
def db():
    c = sqlite3.connect(DB)
    c.row_factory = sqlite3.Row
    try:
        yield c
        c.commit()
    finally:
        c.close()


def init():
    with db() as c:
        c.executescript("""
        CREATE TABLE IF NOT EXISTS events(
            day TEXT, kind TEXT, n INTEGER, meta TEXT,
            PRIMARY KEY(day,kind));
        CREATE TABLE IF NOT EXISTS modules(
            day TEXT, module TEXT, uses INTEGER,
            PRIMARY KEY(day,module));
        """)


init()


def rate(req: Request, n: int = 60):
    key = hashlib.sha256((req.client.host if req.client else "?").encode()).hexdigest()[:16]
    q = _rl.setdefault(key, [])
    t = time.time()
    q[:] = [x for x in q if t - x < 60]
    if len(q) >= n:
        raise HTTPException(429, "rate")
    q.append(t)


@app.post("/ingest")
async def ingest(req: Request):
    rate(req, 120)
    body = await req.body()
    if len(body) > BODY_CAP:
        raise HTTPException(413)
    sig = req.headers.get("x-nox-sig", "")
    exp = hmac.new(INGEST_HMAC.encode(), body, hashlib.sha256).hexdigest()
    if not hmac.compare_digest(sig, exp):
        raise HTTPException(401, "bad sig")
    try:
        batch = json.loads(body)
    except Exception:
        raise HTTPException(400)
    day = time.strftime("%Y-%m-%d", time.gmtime())
    with db() as c:
        for ev in batch.get("events", [])[:500]:
            kind = str(ev.get("k", ""))[:40].replace("/", "_")
            if not kind:
                continue
            c.execute(
                "INSERT INTO events VALUES(?,?,1,'') ON CONFLICT(day,kind) DO UPDATE SET n=n+1",
                (day, kind))
            for mod, n in (ev.get("mods") or {}).items():
                m = str(mod)[:40]
                c.execute(
                    "INSERT INTO modules VALUES(?,?,?) ON CONFLICT(day,module) DO UPDATE SET uses=uses+?",
                    (day, m, int(n), int(n)))
    return {"ok": True}


@app.get("/kpis")
async def kpis(req: Request, days: int = 7):
    if not API_KEY or req.headers.get("x-api-key") != API_KEY:
        raise HTTPException(401)
    days = min(max(days, 1), 90)
    with db() as c:
        ev = c.execute(
            "SELECT day, kind, n FROM events WHERE day >= date('now', ?) ORDER BY day",
            (f"-{days} days",)).fetchall()
        top = c.execute(
            "SELECT module, SUM(uses) u FROM modules WHERE day >= date('now', ?) "
            "GROUP BY module ORDER BY u DESC LIMIT 5", (f"-{days} days",)).fetchall()
    totals: dict[str, int] = {}
    for r in ev:
        totals[r["kind"]] = totals.get(r["kind"], 0) + r["n"]
    crash = totals.get("crash", 0)
    sessions = totals.get("session", 0) or totals.get("launch", 1)
    return {
        "days": days,
        "totals": totals,
        "dau": len({r["day"] for r in ev}),
        "top_modules": [{"module": r["module"], "uses": r["u"]} for r in top],
        "crash_rate": round(crash / max(sessions, 1) * 100, 2),
    }


@app.get("/health")
async def health():
    try:
        with db() as c:
            c.execute("SELECT 1")
        return {"ok": True}
    except Exception as e:
        return JSONResponse({"ok": False, "err": str(e)}, status_code=503)
