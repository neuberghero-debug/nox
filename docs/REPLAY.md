# .donutreplay format + tooling

## File layout (gzip container)
```
u32  0x444E5250            'DNRP' magic
u16  1                     format version
utf  "NOX <clientVersion>"
u32  frameCount
per frame: u32 len | u8[len] frame
```

## Frame types
**Snapshot frame** (4 Hz, ring-buffered 10 min):
```
u32 ms-since-start
u16 entityCount
per entity:
  i32 entityId | u8 tag | f32 x,y,z | f32 yaw,pitch | u8 flags
  tag: 0=other 1=player 2=item 3=projectile
  flags: 1=sprinting 2=crouching 4=invisible
```

**Event frame** (tag byte 0xF0):
```
u32 ms | u8 0xF0 | utf kind | utf data(≤200)
kinds: rec_start, death, kill, chat, toggle, …
```

## Sidecar
`<file>.marks.json` — HighlightMarks list `{ms, kind, text}` for clip tooling.

## Client APIs
- `ReplayRecorder` — start/stop/tick (4Hz sampler), `event()`, `save()` → `nox/replays/rec-*.donutreplay`.
- `ReplayPlayer` — `load(path)`, `tick(realMs)`, `seek(ms)`, `sample([ms])` → lerped `Ent` list; `events()` for timeline markers.
- `ReplayScreen` — orbit-camera viewer (drag=orbit, wheel=zoom, space=play, `[`/`]` speed, marker-click seek).
- `ReplayExporter` — `exportPng` sequence, `exportMp4` (rawvideo pipe → ffmpeg libx264).
- `ReplayMod` module — record toggle, autosave on death/kill; `/nox replay [save|open <file>|export]`; export auto-uploads to Discord when DiscordNotifier webhook is set.
