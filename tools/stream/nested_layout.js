// NOX nested-screen layout — runs inside the PRIVATE nested KWin only.
// Fallback for the kwinrulesrc docking (rules are the primary mechanism).
// Positions match the OBS overlay zones: DEV 110-702, GAME 702-1182.
function dock(w) {
    if (!w.normalWindow) return;
    var cap = (w.caption || "") + " " + (w.resourceClass || "");
    if (cap.indexOf("noxdev") >= 0) {
        w.noBorder = true;
        w.frameGeometry = { x: 13, y: 111, width: 1054, height: 590 };
    } else if (cap.indexOf("Minecraft") >= 0 || cap.indexOf("minecraft") >= 0) {
        w.noBorder = true;
        w.frameGeometry = { x: 13, y: 703, width: 1054, height: 478 };
    }
}
workspace.windowAdded.connect(dock);
workspace.windowList().forEach(dock);
print("NOX nested layout active");
