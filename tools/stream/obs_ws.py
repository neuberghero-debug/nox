#!/usr/bin/env python3
"""Minimal obs-websocket v5 client for NOX-TikTok stream setup.

Usage:
  obs_ws.py call <RequestType> [json-request-data]
  obs_ws.py setup          # activate NOX-TikTok profile + scene collection live
  obs_ws.py probe          # dump version/profiles/scene-collections/inputs/kinds

Auth is read from the flatpak obs-websocket config (never printed).
"""

import asyncio
import base64
import hashlib
import json
import sys
from pathlib import Path

WS_URL = "ws://127.0.0.1:4455"
WS_CONFIG = Path.home() / ".var/app/com.obsproject.Studio/config/obs-studio/plugin_config/obs-websocket/config.json"


def _password() -> str:
    return json.loads(WS_CONFIG.read_text())["server_password"]


def _auth(password: str, salt: str, challenge: str) -> str:
    secret = base64.b64encode(hashlib.sha256((password + salt).encode()).digest())
    return base64.b64encode(hashlib.sha256(secret + challenge.encode()).digest()).decode()


class ObsWs:
    def __init__(self, ws):
        self.ws = ws
        self._id = 0

    @classmethod
    async def connect(cls):
        import websockets

        ws = await websockets.connect(WS_URL, max_size=64 * 1024 * 1024)
        hello = json.loads(await ws.recv())
        if hello.get("op") != 0:
            raise RuntimeError(f"expected Hello, got {hello}")
        d = hello["d"]
        identify = {"op": 1, "d": {"rpcVersion": 1, "eventSubscriptions": 0}}
        if "authentication" in d:
            identify["d"]["authentication"] = _auth(
                _password(), d["authentication"]["salt"], d["authentication"]["challenge"]
            )
        await ws.send(json.dumps(identify))
        identified = json.loads(await ws.recv())
        if identified.get("op") != 2:
            raise RuntimeError(f"identify failed: {identified}")
        return cls(ws)

    async def call(self, request_type: str, data: dict | None = None) -> dict:
        self._id += 1
        rid = f"nox-{self._id}"
        await self.ws.send(json.dumps({
            "op": 6,
            "d": {"requestType": request_type, "requestId": rid, "requestData": data or {}},
        }))
        while True:
            msg = json.loads(await self.recv())
            if msg.get("op") == 7 and msg["d"].get("requestId") == rid:
                status = msg["d"]["requestStatus"]
                if not status.get("result"):
                    raise RuntimeError(f"{request_type} failed: {status.get('code')} {status.get('comment')}")
                return msg["d"].get("responseData") or {}
            # ignore events/other responses

    async def recv(self):
        raw = await self.ws.recv()
        return raw


async def probe(obs: ObsWs):
    ver = await obs.call("GetVersion")
    print("version:", {k: ver[k] for k in ("obsVersion", "obsWebSocketVersion") if k in ver})
    prof = await obs.call("GetProfileList")
    print("profiles:", prof.get("profiles"), "current:", prof.get("currentProfileName"))
    cols = await obs.call("GetSceneCollectionList")
    print("scene-collections:", cols.get("sceneCollections"), "current:", cols.get("currentSceneCollectionName"))
    scenes = await obs.call("GetSceneList")
    print("scenes:", [(s.get("sceneName"), s.get("sceneIndex")) for s in scenes.get("scenes", [])],
          "current:", scenes.get("currentProgramSceneName"))
    inputs = await obs.call("GetInputList")
    for i in inputs.get("inputs", []):
        print("  input:", i.get("inputKind"), "|", i.get("inputName"))
    kinds = await obs.call("GetInputKindList")
    print("input-kinds:", kinds.get("inputKinds"))


async def setup(obs: ObsWs):
    prof = await obs.call("GetProfileList")
    if "NOX-TikTok" in (prof.get("profiles") or []):
        if prof.get("currentProfileName") != "NOX-TikTok":
            await obs.call("SetCurrentProfile", {"profileName": "NOX-TikTok"})
            print("[setup] profile -> NOX-TikTok")
    else:
        print("[setup] WARN: profile NOX-TikTok not listed; OBS may need restart to see files")

    cols = await obs.call("GetSceneCollectionList")
    if "NOX-TikTok" in (cols.get("sceneCollections") or []):
        if cols.get("currentSceneCollectionName") != "NOX-TikTok":
            await obs.call("SetCurrentSceneCollection", {"sceneCollectionName": "NOX-TikTok"})
            print("[setup] scene-collection -> NOX-TikTok")
    else:
        print("[setup] WARN: scene collection NOX-TikTok not listed; OBS may need restart")

    scenes = await obs.call("GetSceneList")
    names = [s.get("sceneName") for s in scenes.get("scenes", [])]
    print("[setup] scenes:", names)
    if "Dev Stream" in names:
        await obs.call("SetCurrentProgramScene", {"sceneName": "Dev Stream"})
        items = await obs.call("GetSceneItemList", {"sceneName": "Dev Stream"})
        for it in items.get("sceneItems", []):
            print(f"  item #{it.get('sceneItemId')}: {it.get('sourceName')} enabled={it.get('sceneItemEnabled')}")
    await probe(obs)


async def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd = sys.argv[1]
    obs = await ObsWs.connect()
    if cmd == "probe":
        await probe(obs)
    elif cmd == "setup":
        await setup(obs)
    elif cmd == "call":
        if len(sys.argv) < 3:
            print("call <RequestType> [json]")
            return
        data = json.loads(sys.argv[2]) if len(sys.argv) > 3 else {}
        print(json.dumps(await obs.call(sys.argv[2], data), indent=2, ensure_ascii=False))
    else:
        print(f"unknown cmd {cmd}")


if __name__ == "__main__":
    asyncio.run(main())
