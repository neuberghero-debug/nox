"""NOX E2E chat relay (M21) — ciphertext-only WebSocket relay.

Clients connect with a JWT (same secret/issuer as friend-sync) and a
room id derived from the friend-pair (client-side: sorted hwid pair hash).
The relay NEVER sees plaintext — messages are opaque base64 ciphertext
blobs, capped at 8KB, rate-limited 30/min per connection, deduped by
client nonce for 60s.

Protocol (JSON frames):
  → {"t":"join","room":"<sha256 hex>"}
  → {"t":"msg","nonce":"<b64>","ct":"<b64 ciphertext>"}
  ← {"t":"msg","nonce":"…","ct":"…","ts":…}   (fanned out to other members)
  ← {"t":"ok"|"err","why":…}
"""
import asyncio
import hashlib
import hmac
import json
import os
import secrets
import time

from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse

JWT_SECRET = os.getenv("NOX_JWT_SECRET", secrets.token_hex(32))
MAX_CT = 8 * 1024
RATE = 30          # msgs/min per conn
DEDUP_S = 60
ROOM_CAP = 8

app = FastAPI(title="nox-chat-relay", version="1.0")
rooms: dict[str, set[WebSocket]] = {}
seen: dict[str, float] = {}


def now() -> float:
    return time.time()


def jwt_ok(tok: str) -> bool:
    try:
        import base64
        head, body, sig = tok.split(".")
        exp = base64.urlsafe_b64encode(
            hmac.new(JWT_SECRET.encode(), f"{head}.{body}".encode(), hashlib.sha256).digest()
        ).rstrip(b"=").decode()
        if not hmac.compare_digest(sig, exp):
            return False
        payload = json.loads(base64.urlsafe_b64decode(body + "=" * (-len(body) % 4)))
        return payload.get("exp", 0) > now()
    except Exception:
        return False


@app.websocket("/ws")
async def ws(sock: WebSocket):
    tok = sock.query_params.get("token", "")
    if not jwt_ok(tok):
        await sock.close(code=4401)
        return
    await sock.accept()
    room = None
    sent: list[float] = []
    try:
        while True:
            try:
                frame = json.loads(await asyncio.wait_for(sock.receive_text(), 30))
            except asyncio.TimeoutError:
                await sock.send_text('{"t":"ping"}')
                continue
            t = frame.get("t")
            if t == "join" and room is None:
                r = str(frame.get("room", ""))[:128]
                if not r or len(rooms.get(r, ())) >= ROOM_CAP:
                    await sock.send_text('{"t":"err","why":"room"}')
                    continue
                room = r
                rooms.setdefault(room, set()).add(sock)
                await sock.send_text('{"t":"ok","join":true}')
            elif t == "msg" and room:
                nowt = now()
                sent[:] = [x for x in sent if nowt - x < 60]
                if len(sent) >= RATE:
                    await sock.send_text('{"t":"err","why":"rate"}')
                    continue
                nonce, ct = str(frame.get("nonce", ""))[:64], str(frame.get("ct", ""))
                if not nonce or not ct or len(ct) > MAX_CT * 4 // 3:
                    await sock.send_text('{"t":"err","why":"size"}')
                    continue
                dk = room + ":" + nonce
                if dk in seen and nowt - seen[dk] < DEDUP_S:
                    continue                       # dedup, silent
                seen[dk] = nowt
                sent.append(nowt)
                out = json.dumps({"t": "msg", "nonce": nonce, "ct": ct, "ts": int(nowt)})
                dead = []
                for peer in rooms.get(room, ()):
                    if peer is not sock:
                        try:
                            await peer.send_text(out)
                        except Exception:
                            dead.append(peer)
                for d in dead:
                    rooms.get(room, set()).discard(d)
            else:
                await sock.send_text('{"t":"err","why":"frame"}')
    except (WebSocketDisconnect, Exception):
        pass
    finally:
        if room and room in rooms:
            rooms[room].discard(sock)
            if not rooms[room]:
                rooms.pop(room, None)
        # prune dedup map lazily
        for k in [k for k, v in seen.items() if now() - v > DEDUP_S]:
            seen.pop(k, None)


@app.get("/health")
async def health():
    return {"ok": True, "rooms": len(rooms)}
