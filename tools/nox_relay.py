#!/usr/bin/env python3
"""NOX chat relay (Modul 21) — dumb WebSocket forwarder.

Sees ONLY routing metadata (to-fingerprint); ciphertext is end-to-end
X25519+ChaCha20-Poly1305 between clients. No accounts, no storage.

Protocol (JSON text frames):
  client → relay: {"type":"hello","fp":"<b64url pubkey fingerprint>"}
  client → relay: {"type":"msg","to":"<fp>","from":"<fp>","n":"<b64 nonce>","ct":"<b64 ct>"}
  relay  → client: the "msg" object verbatim to the socket registered under "to"

Run:  python3 tools/nox_relay.py [host] [port]   (default 127.0.0.1:8891)
Deps: pip install websockets   (or use any ws lib — protocol is trivial)
"""
import asyncio, json, sys

try:
    import websockets
except ImportError:
    sys.exit("pip install websockets")

PEERS = {}   # fp -> websocket


async def handle(ws):
    fp = None
    try:
        async for raw in ws:
            try:
                m = json.loads(raw)
            except Exception:
                continue
            t = m.get("type")
            if t == "hello":
                fp = m.get("fp", "")
                if fp:
                    PEERS[fp] = ws
            elif t == "msg":
                to = PEERS.get(m.get("to"))
                if to is not None:
                    try:
                        await to.send(raw)
                    except Exception:
                        pass
    except websockets.ConnectionClosed:
        pass
    finally:
        if fp and PEERS.get(fp) is ws:
            del PEERS[fp]


async def main(host, port):
    async with websockets.serve(handle, host, port):
        print(f"nox-relay listening on ws://{host}:{port} "
              f"({len(PEERS)} peers)", flush=True)
        await asyncio.Future()          # run forever


if __name__ == "__main__":
    host = sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1"
    port = int(sys.argv[2]) if len(sys.argv) > 2 else 8891
    asyncio.run(main(host, port))
