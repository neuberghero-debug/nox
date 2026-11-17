#!/usr/bin/env python3
"""highlight_reel.py — NOX clip processor (Modul 2, E.2/E.3/E.4).

Turns a screen recording + .marks.json (written by the client next to
.donutreplay files) into individual highlight clips:

  python3 tools/highlight_reel.py recording.mp4 marks.json -o clips/
    --pre 5 --post 8            seconds around each marker
    --thumb                     also write <clip>.png (first frame, E.4)
    --upload                    post each clip to $NOX_CLIPS_WEBHOOK (E.3)

Webhook safety: 8MB cap, 3 retries max, silent on failure.
"""
import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path


def cut(src: Path, out: Path, start: float, dur: float) -> bool:
    r = subprocess.run([
        "ffmpeg", "-y", "-ss", f"{start:.2f}", "-t", f"{dur:.2f}", "-i", str(src),
        "-c", "copy", "-avoid_negative_ts", "make_zero", str(out)],
        capture_output=True)
    return r.returncode == 0


def thumb(clip: Path, png: Path) -> bool:
    r = subprocess.run(["ffmpeg", "-y", "-i", str(clip), "-vframes", "1",
                        "-q:v", "3", str(png)], capture_output=True)
    return r.returncode == 0


def upload(webhook: str, file: Path) -> bool:
    if file.stat().st_size > 8 * 1024 * 1024:
        return False
    for _ in range(3):
        try:
            import urllib.request
            boundary = "nox" + os.urandom(6).hex()
            body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\";"
                    f" filename=\"{file.name}\"\r\nContent-Type: video/mp4\r\n\r\n"
                    ).encode() + file.read_bytes() + f"\r\n--{boundary}--\r\n".encode()
            req = urllib.request.Request(webhook, data=body, headers={
                "Content-Type": f"multipart/form-data; boundary={boundary}"})
            urllib.request.urlopen(req, timeout=15)
            return True
        except Exception:
            time.sleep(2)
    return False


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("recording", type=Path)
    ap.add_argument("marks", type=Path)
    ap.add_argument("-o", "--out", type=Path, default=Path("clips"))
    ap.add_argument("--pre", type=float, default=5)
    ap.add_argument("--post", type=float, default=8)
    ap.add_argument("--thumb", action="store_true")
    ap.add_argument("--upload", action="store_true")
    a = ap.parse_args()

    marks = json.loads(a.marks.read_text()).get("marks", [])
    a.out.mkdir(parents=True, exist_ok=True)
    webhook = os.getenv("NOX_CLIPS_WEBHOOK", "")
    made = 0
    for i, m in enumerate(marks):
        start = max(0, m["ts"] / 1000 - a.pre)
        dur = a.pre + a.post
        clip = a.out / f"highlight-{i:02d}-{m['kind']}.mp4"
        if not cut(a.recording, clip, start, dur):
            print(f"[skip] ffmpeg failed @ {m['ts']}ms", file=sys.stderr)
            continue
        made += 1
        print(f"[clip] {clip.name}  ({m['kind']}: {m.get('note','')})")
        if a.thumb:
            thumb(clip, clip.with_suffix(".png"))
        if a.upload and webhook:
            print("[upload]", "ok" if upload(webhook, clip) else "failed")
    print(f"done — {made}/{len(marks)} clips")
    return 0


if __name__ == "__main__":
    sys.exit(main())
