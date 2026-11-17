#!/usr/bin/env python3
"""Generate the NOX-TikTok OBS scene collection + profile.

Writes into the flatpak OBS config:
  ~/.var/app/com.obsproject.Studio/config/obs-studio/basic/scenes/NOX-TikTok.json
  ~/.var/app/com.obsproject.Studio/config/obs-studio/basic/profiles/NOX-TikTok/{basic.ini,service.json,streamEncoder.json}

Canvas 1080x1920 (TikTok vertical). Sources:
  NOX-Overlay      browser_source  http://127.0.0.1:8892/?layout=split  (topmost)
  SCREEN — eDP-1   ONE pipewire screen capture of the panel, shown TWICE:
                   cropped to the dev-terminal rect and to the Minecraft rect
                   (KWin rules in ~/.config/kwinrulesrc dock the windows into
                   those rects — MC/dev relaunches re-dock automatically,
                   the capture itself never goes stale)
  BG               color source
  Desktop+Mic audio (default devices)

The portal picker appears ONCE for the screen source (check "restore in
future sessions"); the token is stored in this file afterwards — this
script preserves an existing RestoreToken on regeneration.

Zone rects MUST stay in sync with tools/stream/overlay/index.html :root
and the dock rects with tools/stream/apply_kwin_rules.py.
"""
import json
import uuid
from pathlib import Path

OBS = Path.home() / ".var/app/com.obsproject.Studio/config/obs-studio"
SCENES = OBS / "basic/scenes"
PROFILES = OBS / "basic/profiles"
OVERLAY_URL = (
    "http://127.0.0.1:8892/?layout=split&user={user}"
    "&task=NOX+Client+Dev"
    "&discord=discord.gg%2FtewtZTsZf5"
    "&github=github.com%2Fneuberghero-debug%2Fnox"
)

W, H = 1080, 1920

# screen: eDP-1 native 2256x1504, logical 1672x1115 @ 1.35
# logical dock rects (x, y, w, h) — mirrored in apply_kwin_rules.py
SCALE = 1.35
DEV_RECT = (0, 32, 1672, 597)    # below the 30px top panel
GAME_RECT = (0, 629, 1672, 455)  # above the autohide bottom dock
SCREEN_W, SCREEN_H = 2256, 1504

# canvas zones (x, y, w, h) — overlay holes, see index.html
ZONES = {
    "DEV": (12, 118, 1056, 574),
    "GAME": (12, 710, 1056, 464),
}

SCREEN_SRC = "SCREEN — eDP-1"


def uid():
    return str(uuid.uuid4())


def crop(rect):
    """Logical rect -> native-pixel crop_* values for a scene item."""
    x, y, w, h = rect
    nx, ny = round(x * SCALE), round(y * SCALE)
    nw, nh = min(round(w * SCALE), SCREEN_W - nx), min(round(h * SCALE), SCREEN_H - ny)
    return (nx, ny, max(0, SCREEN_W - nx - nw), max(0, SCREEN_H - ny - nh))


def src(name, sid, settings, mixers=0):
    return {
        "prev_ver": 537001986, "name": name, "uuid": uid(), "id": sid,
        "versioned_id": sid, "settings": settings, "mixers": mixers, "sync": 0,
        "flags": 0, "volume": 1.0, "balance": 0.5, "enabled": True, "muted": False,
        "push-to-mute": False, "push-to-mute-delay": 0, "push-to-talk": False,
        "push-to-talk-delay": 0, "hotkeys": {}, "deinterlace_mode": 0,
        "deinterlace_field_order": 0, "monitoring_type": 0, "private_settings": {},
    }


def item(source, iid, x, y, w=None, h=None, crop_vals=(0, 0, 0, 0)):
    it = {
        "name": source["name"], "source_uuid": source["uuid"], "visible": True,
        "locked": False, "rot": 0.0,
        "scale_ref": {"x": float(W), "y": float(H)}, "align": 5,
        "bounds_type": 0, "bounds_align": 0, "bounds_crop": False,
        "crop_left": crop_vals[0], "crop_top": crop_vals[1],
        "crop_right": crop_vals[2], "crop_bottom": crop_vals[3],
        "id": iid, "group_item_backup": False,
        "pos": {"x": float(x), "y": float(y)},
        "pos_rel": {"x": -1.0, "y": -1.0},
        "scale": {"x": 1.0, "y": 1.0}, "scale_rel": {"x": 1.0, "y": 1.0},
        "bounds": {"x": 0.0, "y": 0.0}, "bounds_rel": {"x": 0.0, "y": 0.0},
        "scale_filter": "disable", "blend_method": "default", "blend_type": "normal",
        "show_transition": {"duration": 300}, "hide_transition": {"duration": 300},
        "private_settings": {},
    }
    if w is not None:      # fit-to-zone: scale-into-bounds keeps aspect
        it["bounds_type"] = 2            # OBS_BOUNDS_SCALE_INNER
        it["bounds"] = {"x": float(w), "y": float(h)}
        it["bounds_align"] = 5           # top-left within the zone
    return it


def audio(name, sid, settings):
    s = src(name, sid, settings)
    s["mixers"] = 255
    return s


def existing_token():
    """Keep the portal restore token across regeneration."""
    f = SCENES / "NOX-TikTok.json"
    if not f.exists():
        return ""
    try:
        d = json.loads(f.read_text())
        for s in d.get("sources", []):
            if s.get("name") == SCREEN_SRC:
                st = s.get("settings", {})
                return st.get("restore_token") or st.get("RestoreToken") or ""
    except Exception:
        pass
    return ""


def main(user="jbbbbbrk"):
    token = existing_token()
    browser = src("NOX-Overlay", "browser_source", {
        "url": OVERLAY_URL.format(user=user),
        "width": W, "height": H, "fps": 30, "fps_custom": True,
        "reroute_audio": False, "restart_when_active": True,
        "shutdown": False, "css": "body { background: transparent; }",
    })
    screen = src(SCREEN_SRC, "pipewire-screen-capture-source", {
        "method": "screen", "capture_cursor": False, "show_cursor": False,
        "priority": 0, "restore_token": token, "RestoreToken": token,
    })
    bg = src("BG", "color_source_v3", {"color": 4279637264, "width": W, "height": H})

    desktop = audio("Desktop-Audio", "pulse_output_capture", {"device_id": "default"})
    mic = audio("Mikrofon-/AUX-Audio", "pulse_input_capture", {"device_id": "default"})

    items = [
        item(bg, 4, 0, 0),                                                # bottom
        item(screen, 2, *ZONES["DEV"][:2], *ZONES["DEV"][2:], crop(DEV_RECT)),
        item(screen, 3, *ZONES["GAME"][:2], *ZONES["GAME"][2:], crop(GAME_RECT)),
        item(browser, 1, 0, 0),                                           # top: overlay
    ]

    scene = src("Dev Stream", "scene", {"id_counter": 10, "custom_size": False,
                                        "items": items})
    scene["hotkeys"] = {"OBSBasic.SelectScene": []}

    coll = {
        "name": "NOX-TikTok",
        "DesktopAudioDevice1": desktop,
        "AuxAudioDevice1": mic,
        "sources": [scene, browser, screen, bg],
        "groups": [],
        "scene_order": [{"name": "Dev Stream"}],
        "current_scene": "Dev Stream",
        "current_program_scene": "Dev Stream",
        "canvases": [],
        "current_transition": "fade_transition",
        "transition_duration": 300,
        "transitions": [{"id": "fade_transition", "name": "Überblenden",
                         "settings": {"duration": 300}}],
        "quick_transitions": [],
        "saved_projectors": {},
        "preview_locked": False,
        "scaling_enabled": False, "scaling_level": 0,
        "scaling_off_x": 0.0, "scaling_off_y": 0.0,
        "virtual-camera": {"type2": 3},
        "modules": {"decklink_caption": False, "scripts": []},
        "resolution": {"x": W, "y": H},
        "version": 1,
    }

    SCENES.mkdir(parents=True, exist_ok=True)
    out = SCENES / "NOX-TikTok.json"
    out.write_text(json.dumps(coll, indent=1, ensure_ascii=False))
    print(f"[scene] {out}" + (" (RestoreToken preserved)" if token else " (no token — picker will ask once)"))

    # ---- profile: 1080x1920 @30, x264 superfast 6M/160k + tiktok custom rtmp ----
    prof = PROFILES / "NOX-TikTok"
    prof.mkdir(parents=True, exist_ok=True)
    (prof / "basic.ini").write_text(f"""[General]
Name=NOX-TikTok
OpenStatsOnStartup=false

[Output]
Mode=Simple
FilenameFormatting=%CCYY-%MM-%DD %hh-%mm-%ss
DelayEnable=false
DelaySec=20
DelayPreserve=true
Reconnect=true
RetryDelay=2
MaxRetries=25
BindIP=default
IPFamily=IPv4+IPv6
DynamicBitrate=false

[SimpleOutput]
FilePath={Path.home()}/Videos/OBS
RecFormat2=hybrid_mp4
VBitrate=6000
ABitrate=160
Preset=superfast
RecQuality=Stream
RecRB=true
RecRBTime=60
StreamAudioEncoder=aac
RecAudioEncoder=aac
RecTracks=1
StreamEncoder=x264
RecEncoder=x264
UseAdvanced=false

[Video]
BaseCX={W}
BaseCY={H}
OutputCX={W}
OutputCY={H}
FPSType=0
FPSCommon=30
ScaleType=bicubic

[Audio]
MonitoringDeviceId=default
MonitoringDeviceName=Default
SampleRate=48000
ChannelSetup=Stereo

[Stream1]
IgnoreRecommended=true
EnableMultitrackVideo=false
""")
    # Custom RTMP — paste fresh server+key from livecenter.tiktok.com/producer
    svc = prof / "service.json"
    if not svc.exists():
        svc.write_text(json.dumps({
            "type": "rtmp_custom",
            "settings": {
                "server": "rtmp://push.tiktokcdn.com/REPLACE-ME",
                "key": "PASTE-FRESH-KEY-FROM-livecenter.tiktok.com/producer",
                "use_auth": False,
            },
        }))
    (prof / "streamEncoder.json").write_text("{}\n")
    print(f"[profile] {prof}  (basic.ini + service.json)")
    print('\n1× klikni „Laptop-Bildschirm teilen" v portal dialogu (jednou provždy —')
    print("   restore token se pak obnovuje bez dialogu).")
    print("MC/dev-okna dokují KWin pravidla z apply_kwin_rules.py — relaunch se")
    print("zachytí automaticky, bez dalšího výběru.")
    print("Stream-Key: Settings→Stream — kommt aus livecenter.tiktok.com/producer")


if __name__ == "__main__":
    import sys
    main(*sys.argv[1:])
