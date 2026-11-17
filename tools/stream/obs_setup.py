#!/usr/bin/env python3
"""Live-configure the running OBS instance for the NOX TikTok dev stream.

- Activates profile + scene collection NOX-TikTok via obs-websocket.
- Rebuilds the "Dev Stream" scene with ONE PipeWire screen capture that is
  cropped per scene-item into the DEV and GAME zones (survives app relaunches
  because KWin window rules re-dock the windows into the same screen rects).
- Keeps the browser overlay + BG + audio sources.
"""

import asyncio
import json
import sys

sys.path.insert(0, str(__file__ and __import__("pathlib").Path(__file__).parent))
from obs_ws import ObsWs  # noqa: E402

SCENE = "Dev Stream"

# Screen-native pixel size of eDP-1 (2256x1504 @ scale 1.35, logical 1672x1115).
# Capture rects in LOGICAL coordinates -> converted with SCALE for crop.
SCALE = 1.35
TOP_PANEL = 32  # logical px of the Plasma panel to keep out of capture
SCREEN_W, SCREEN_H = 2256, 1504  # native px of the pipewire stream

# logical rects (x, y, w, h) — top panel 30px, bottom autohide dock ~56px
DEV_RECT = (0, TOP_PANEL, 1672, 597)
GAME_RECT = (0, 629, 1672, 455)

# canvas zones (from tools/stream/overlay split layout), 1080x1920 canvas
DEV_ZONE = (12, 118, 1056, 574)
GAME_ZONE = (12, 710, 1056, 464)


def crop_for(rect):
    x, y, w, h = rect
    nx, ny = round(x * SCALE), round(y * SCALE)
    nw, nh = round(w * SCALE), round(h * SCALE)
    nw = min(nw, SCREEN_W - nx)
    nh = min(nh, SCREEN_H - ny)
    return {
        "cropLeft": nx, "cropTop": ny,
        "cropRight": max(0, SCREEN_W - nx - nw),
        "cropBottom": max(0, SCREEN_H - ny - nh),
    }


async def call(obs, req, data=None):
    return await obs.call(req, data or {})


async def get_transform(obs, scene, item_id):
    r = await call(obs, "GetSceneItemTransform", {"sceneName": scene, "sceneItemId": item_id})
    return r.get("sceneItemTransform", {})


async def set_transform(obs, scene, item_id, t):
    await call(obs, "SetSceneItemTransform", {
        "sceneName": scene, "sceneItemId": item_id, "sceneItemTransform": t})


def fit_transform(rect, zone):
    """Scene-item transform: crop to screen rect, fit inside canvas zone."""
    zx, zy, zw, zh = zone
    return {
        **crop_for(rect),
        "positionX": zx, "positionY": zy,
        "boundsType": "OBS_BOUNDS_SCALE_INNER",
        "boundsAlignment": 0,
        "boundsWidth": zw, "boundsHeight": zh,
        "alignment": 5,  # top-left
    }


async def ensure_item(obs, scene, source_name, input_kind, settings, transform=None):
    """Create the source+scene item if missing; return sceneItemId."""
    items = await call(obs, "GetSceneItemList", {"sceneName": scene})
    for it in items.get("sceneItems", []):
        if it.get("sourceName") == source_name:
            if settings:
                await call(obs, "SetInputSettings",
                           {"inputName": source_name, "inputSettings": settings, "overlay": True})
            if transform:
                await set_transform(obs, scene, it["sceneItemId"], transform)
            return it["sceneItemId"]
    inputs = await call(obs, "GetInputList")
    have = {i["inputName"] for i in inputs.get("inputs", [])}
    if source_name not in have:
        r = await call(obs, "CreateInput", {
            "sceneName": scene, "inputName": source_name,
            "inputKind": input_kind, "inputSettings": settings or {},
            "sceneItemEnabled": True})
        return r["sceneItemId"]
    r = await call(obs, "CreateSceneItem", {"sceneName": scene, "sourceName": source_name})
    item_id = r["sceneItemId"]
    if settings:
        await call(obs, "SetInputSettings",
                   {"inputName": source_name, "inputSettings": settings, "overlay": True})
    if transform:
        await set_transform(obs, scene, item_id, transform)
    return item_id


async def remove_item(obs, scene, item_id):
    await call(obs, "RemoveSceneItem", {"sceneName": scene, "sceneItemId": item_id})


async def main():
    obs = await ObsWs.connect()

    # --- steal an existing screen-capture restore token if one exists ---
    token = None
    inputs = await call(obs, "GetInputList")
    for i in inputs.get("inputs", []):
        if i.get("inputKind") == "pipewire-screen-capture-source":
            s = await call(obs, "GetInputSettings", {"inputName": i["inputName"]})
            t = (s.get("inputSettings") or {}).get("restore_token")
            if t:
                token = t
                print(f"[obs] found restore_token on '{i['inputName']}'")
                break
    if not token:
        print("[obs] no existing restore_token — portal picker will appear once")

    # --- activate profile + scene collection ---
    prof = await call(obs, "GetProfileList")
    if "NOX-TikTok" in (prof.get("profiles") or []) and prof.get("currentProfileName") != "NOX-TikTok":
        await call(obs, "SetCurrentProfile", {"profileName": "NOX-TikTok"})
        print("[obs] profile -> NOX-TikTok")
    cols = await call(obs, "GetSceneCollectionList")
    if "NOX-TikTok" in (cols.get("sceneCollections") or []) and \
            cols.get("currentSceneCollectionName") != "NOX-TikTok":
        await call(obs, "SetCurrentSceneCollection", {"sceneCollectionName": "NOX-TikTok"})
        print("[obs] scene collection -> NOX-TikTok")
    await asyncio.sleep(0.5)  # let OBS settle after collection switch

    scenes = await call(obs, "GetSceneList")
    names = [s.get("sceneName") for s in scenes.get("scenes", [])]
    if SCENE not in names:
        await call(obs, "CreateScene", {"sceneName": SCENE})
        print(f"[obs] created scene {SCENE}")

    # --- one screen capture feeds both zones ---
    settings = {"method": "screen", "capture_cursor": False, "show_cursor": False,
                "priority": 0, "restore_token": token or ""}
    screen_id = await ensure_item(obs, SCENE, "SCREEN — eDP-1",
                                  "pipewire-screen-capture-source", settings)

    # remove the old per-window pipewire sources if present
    items = await call(obs, "GetSceneItemList", {"sceneName": SCENE})
    for it in items.get("sceneItems", []):
        if it.get("sourceName") in ("DEV — Devin Desktop", "GAME — Minecraft"):
            await remove_item(obs, SCENE, it["sceneItemId"])
            print(f"[obs] removed stale window-capture '{it['sourceName']}'")

    # drop the plain scene-item of the raw source, then add two cropped items
    items = await call(obs, "GetSceneItemList", {"sceneName": SCENE})
    raw_ids = [it["sceneItemId"] for it in items.get("sceneItems", [])
               if it.get("sourceName") == "SCREEN — eDP-1"]
    for rid in raw_ids:
        await remove_item(obs, SCENE, rid)

    dev_id = await ensure_item(obs, SCENE, "SCREEN — eDP-1", None, None)
    await set_transform(obs, SCENE, dev_id, fit_transform(DEV_RECT, DEV_ZONE))
    game_id = await ensure_item(obs, SCENE, "SCREEN — eDP-1", None, None)
    # ensure_item returns existing id — need a second, distinct scene item
    if game_id == dev_id:
        r = await call(obs, "CreateSceneItem",
                       {"sceneName": SCENE, "sourceName": "SCREEN — eDP-1"})
        game_id = r["sceneItemId"]
    await set_transform(obs, SCENE, game_id, fit_transform(GAME_RECT, GAME_ZONE))

    # overlay + bg
    await ensure_item(obs, SCENE, "NOX-Overlay", "browser_source", {
        "url": "http://127.0.0.1:8892/?layout=split&user=jbbbbbrk"
               "&task=NOX+Client+Dev&discord=discord.gg%2FtewtZTsZf5"
               "&github=github.com%2Fneuberghero-debug%2Fnox",
        "width": 1080, "height": 1920, "fps": 30,
        "reroute_audio": False, "restart_when_active": True,
    }, {"positionX": 0, "positionY": 0, "scaleX": 1.0, "scaleY": 1.0,
        "boundsType": "OBS_BOUNDS_NONE", "alignment": 5})

    # order: BG bottom -> game -> dev -> overlay top
    items = await call(obs, "GetSceneItemList", {"sceneName": SCENE})
    by_id = {it["sceneItemId"]: it for it in items.get("sceneItems", [])}
    order = [i for i, n in sorted(
        [(iid, it["sourceName"]) for iid, it in by_id.items()],
        key=lambda p: {"BG": 0, "SCREEN — eDP-1": 1, "NOX-Overlay": 9}.get(p[1], 5))]
    # index-based reorder: set each item's index explicitly
    for idx, iid in enumerate(order):
        await call(obs, "SetSceneItemIndex",
                   {"sceneName": SCENE, "sceneItemId": iid, "sceneItemIndex": idx})

    # audio
    for name, kind in (("Desktop-Audio", "pulse_output_capture"),
                       ("Mikrofon-/AUX-Audio", "pulse_input_capture")):
        await ensure_item(obs, SCENE, name, kind, {})

    await call(obs, "SetCurrentProgramScene", {"sceneName": SCENE})
    print("[obs] scene setup complete")
    items = await call(obs, "GetSceneItemList", {"sceneName": SCENE})
    for it in items.get("sceneItems", []):
        print(f"  #{it['sceneItemIndex']} {it['sourceName']}")


if __name__ == "__main__":
    asyncio.run(main())
