#!/usr/bin/env python3
"""Save an OBS source/program screenshot. Usage: obs_shot.py [sourceName] out.png [w h]"""
import asyncio, base64, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
from obs_ws import ObsWs


async def main():
    src = sys.argv[1] if len(sys.argv) > 1 else None
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else Path("/tmp/obs_shot.png")
    w = int(sys.argv[3]) if len(sys.argv) > 3 else 1080
    h = int(sys.argv[4]) if len(sys.argv) > 4 else 1920
    obs = await ObsWs.connect()
    req = {"imageFormat": "png", "imageWidth": w, "imageHeight": h,
           "imageCompressionQuality": 80}
    if src:
        req["sourceName"] = src
        r = await obs.call("GetSourceScreenshot", req)
    else:
        r = await obs.call("GetSourceScreenshot", {**req, "sourceName": "Dev Stream"})
    img = r["imageData"].split(",", 1)[1]
    out.write_bytes(base64.b64decode(img))
    print("saved", out, out.stat().st_size, "bytes")


asyncio.run(main())
