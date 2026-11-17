#!/usr/bin/env python3
"""NOX TikTok Live sidecar (Modul 1.1/1.2).

Bridges TikTokLive events → normalized JSON on ws://127.0.0.1:8890 for the
client's stream.TikTokBridge. Likes are bundled (100 likes = 1 event).

Deps:  pip install TikTokLive websockets
Run:   NOX_TIKTOK_USER=@name python3 tools/tiktok_sidecar.py

Reconnect: TikTokLive handles its own reconnect; the WS server just rebroadcasts.
"""
import asyncio
import json
import os
import time

import websockets

USER = os.environ.get("NOX_TIKTOK_USER", "")
PORT = int(os.environ.get("NOX_TIKTOK_WS_PORT", "8890"))

clients: set = set()
like_bucket = {"n": 0, "user": "", "userId": "", "ts": 0.0}


def norm(t, user="", user_id="", value=0, gift="", coins=0):
    return json.dumps({
        "type": t, "user": user, "userId": user_id, "value": value,
        "giftName": gift, "coinValue": coins,
        "timestamp": int(time.time() * 1000),
    })


async def broadcast(msg: str):
    for ws in list(clients):
        try:
            await ws.send(msg)
        except Exception:
            clients.discard(ws)


async def ws_handler(ws):
    clients.add(ws)
    try:
        await ws.send(norm("ping"))
        async for _ in ws:
            pass  # client never sends; keepalive only
    finally:
        clients.discard(ws)


def wire(client):
    from TikTokLive.events import (
        CommentEvent, ConnectEvent, DisconnectEvent, FollowEvent,
        GiftEvent, LikeEvent, ShareEvent, SubNotifyEvent, JoinEvent,
    )

    async def flush_likes():
        while True:
            await asyncio.sleep(2)
            if like_bucket["n"] >= 100:
                await broadcast(norm("LIKE", like_bucket["user"], like_bucket["userId"],
                                     like_bucket["n"]))
                like_bucket.update(n=0, ts=time.time())

    @client.on(ConnectEvent)
    async def _connect(e):  # noqa
        print(f"[sidecar] connected @{USER}")
        await broadcast(norm("STATUS", value=1, gift="connected"))

    @client.on(DisconnectEvent)
    async def _dc(e):  # noqa
        await broadcast(norm("STATUS", value=0, gift="disconnected"))

    @client.on(GiftEvent)
    async def _gift(e):  # noqa
        g = e.gift
        if getattr(g, "repeat_end", True) or getattr(g, "streakable", False) is False:
            await broadcast(norm("GIFT", e.user.nickname, str(e.user.unique_id),
                                 getattr(g, "repeat_count", 1),
                                 g.name, getattr(g, "diamond_count", 0)))

    @client.on(LikeEvent)
    async def _like(e):  # noqa
        like_bucket["n"] += getattr(e, "count", 1)
        like_bucket["user"] = e.user.nickname
        like_bucket["userId"] = str(e.user.unique_id)

    @client.on(CommentEvent)
    async def _comment(e):  # noqa
        await broadcast(norm("COMMENT", e.user.nickname, str(e.user.unique_id),
                             0, e.comment, 0))

    @client.on(FollowEvent)
    async def _follow(e):  # noqa
        await broadcast(norm("FOLLOW", e.user.nickname, str(e.user.unique_id)))

    @client.on(ShareEvent)
    async def _share(e):  # noqa
        await broadcast(norm("SHARE", e.user.nickname, str(e.user.unique_id)))

    @client.on(SubNotifyEvent)
    async def _sub(e):  # noqa
        u = getattr(e, "user", None)
        await broadcast(norm("SUBSCRIBE",
                             getattr(u, "nickname", "?"), str(getattr(u, "unique_id", ""))))

    @client.on(JoinEvent)
    async def _join(e):  # noqa
        u = getattr(e, "user", None)
        await broadcast(norm("JOIN",
                             getattr(u, "nickname", "?"), str(getattr(u, "unique_id", ""))))

    return flush_likes


async def main():
    if not USER:
        print("NOX_TIKTOK_USER env required"); return
    try:
        from TikTokLive import TikTokLiveClient
    except ImportError:
        print("pip install TikTokLive websockets"); return

    client = TikTokLiveClient(unique_id=USER)
    flush_task = wire(client)
    asyncio.ensure_future(flush_task())
    asyncio.ensure_future(run_tiktok(client))

    async with websockets.serve(ws_handler, "127.0.0.1", PORT):
        print(f"[sidecar] ws://127.0.0.1:{PORT} ← @{USER}")
        await asyncio.Future()


async def run_tiktok(client):
    """Keep trying to attach to the live — startable before the stream
    goes online; rejoins after disconnects."""
    while True:
        try:
            await client.start()          # returns when the live ends
        except Exception as ex:
            print(f"[sidecar] tiktok connect failed: {type(ex).__name__}: {ex}", flush=True)
        await broadcast(norm("STATUS", value=0, gift="waiting for live"))
        print("[sidecar] retrying in 20s…", flush=True)
        await asyncio.sleep(20)


if __name__ == "__main__":
    asyncio.run(main())
