#!/usr/bin/env bash
# Convert any video clip into NOX menu-sky frames.
# usage: tools/video-to-sky.sh input.mp4 [fps] [width]
set -euo pipefail
IN="${1:?input video required}"
FPS="${2:-15}"
W="${3:-640}"
OUT="$(dirname "$0")/../src/main/resources/assets/noxclient/sky"
mkdir -p "$OUT"
rm -f "$OUT"/f*.png
ffmpeg -y -i "$IN" -vf "fps=$FPS,scale=$W:-2:flags=lanczos" -q:v 2 "$OUT/f%04d.png"
N=$(ls "$OUT"/f*.png | wc -l)
SIZE=$(du -sh "$OUT" | cut -f1)
echo "wrote $N frames ($SIZE) -> $OUT"