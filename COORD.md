# Session-Koordination — NOX Master-Upgrade

Zwei Sessions arbeiten parallel am Master-Prompt. Bitte NICHT doppelt bauen.

## Session A (diese — Backend/Security/Infra)
Owned / done:
- Modul 18 ✅ — premium/LicenseManager (Ed25519+HWID+Grace+Revalidate), Hwid.java, nox-license-server/
- Modul 19 ✅ — update/UpdateManager + Apply (signed manifest, anti-downgrade, .bak rollback), tools/sign_update.py
- Modul 30.1 ✅ — native/ (Rust cdylib: Ed25519, HWID, anti-debug, pubkey-Fragmente), ncore/NativeCore
- Modul 24 ✅ — config/ConfigMigrator (schema_version chain, .bak.vN backup+rollback)
- Modul 14 ✅ — gui/ds/DS tokens, gui/comp/* (Toggle/Slider/Input/Button/TabPill/Select/Card/Badge/Modal/Tooltip/Scrollable/Icons)
- Modul 15 ✅ — gui/NoxClickGui (rebuild on comp-lib; NoxWindowScreen = legacy, stays)
- Modul 16 ✅ — Toasts (right-top card+progress), UiSounds + sounds/ui/*.ogg + sounds.json, HudWidgets.mode/showDisabled, mono Coords-Card
- DebugConsole: `key <code> [sdlmods]`

- Modul 8 ✅ — nox-license-server /friends/* (login→HS256-JWT, 6-char friend codes, presence ping/list/remove; e2e curl-verified: mutual link + online+pos), module/misc/FriendSync (premium, /nox friendcode|friendadd, NoxRender gizmo markers, Friends.isFriendName hook), LicenseManager.storedKey()/api()
- FIX: /nox-dispatcher strippt parts[0] vor h.accept(rest) — Handler-Args sind 0-indiziert (alt/chat/chatrelay fixed)

Done (Session A, seit COORD):
- Modul 11 ✅ — module/misc/TamperWarner (60s re-check, injection watch, lock, webhook), security/WebhookSec (rate-limit+HMAC+dedup+sanitize), TamperGuard.verified()/tick()/injectionDetected()
- Modul 12 ✅ — util/CrashReporter (dedup store, opt-in gate, /nox crashreport|audit)
- Modul 13 ✅ — alts/AltManager enc-token keystore (NativeCore.wrap), groups, /nox alt
- Modul 21 ✅ — chat/NoxChat (X25519+ChaCha20-Poly1305, /nox chat|chatkey|chatrelay) + tools/nox_relay.py
- CoordBook Gizmos-API-Fix (26.3: point(Vec3,int,float), billboardText(String,Vec3,Style))

Done (Session A, continued):
- Modul 7 ✅ — util/StashIndex (persistenter Kategorien-Index), StashLogger-Modul hooked, /nox stashes (runtime-verified: 6 marks logged, categorized query works)
- Modul 8 ✅ RUNTIME-VERIFIED — login→JWT→ping→friendcode e2e vs. live server (code 3D7LSJ, presence row w/ real pos+dim)
- Modul 11 ✅ RUNTIME-VERIFIED — injection watch fired on real ptrace attach (Gradle daemon)
- Modul 27 ✅ — /admin/analytics (licenses/beats/friends/audit aggregation) + /admin/dashboard (self-contained live HTML, admin-token gated; smoke-verified 401/200)
- Modul 28 ✅ — docs/roadmap.json (structured status source) + nox-bot/cogs/roadmap.py (/roadmap post + 30s mtime auto-edit sync)
- Anti-Crack 30.3-30.5 ✅ — premium/Canary (reflection integrity), premium/Watermark (build fingerprint in sig step), premium/Heartbeat (signed server ping → /heartbeat endpoint)

Done (Session A, final batch):
- Modul 23 ✅ — cosmetics/CosmeticDef (.cosmetic format, asset install, apply), cosmetics/CosmeticsMarket (browse/get/export/publish/import), license-server /cosmetics/* (publish→moderation queue→approve→download counter→5-report auto-flag), /nox cosmetic commands — RUNTIME-VERIFIED e2e (export file, browse listed server item, get installed+applied live)
- Modul 30.6-30.11 ✅ — security/FailSafe (WARN→DEGRADE→LOCK arbiter, wired in onTick), VmGuard (jvm args + /proc/maps lib scan), CrashLoopGuard (≥3 crashes/15min → safe mode, init+enforce in NoxClient), ConfigSig (HMAC over license.state — hand-edits rejected); Canary.ok(), Cosmetics.degrade(), TamperWarner.engageLock() hooks
- Modul 2+22 ✅ — /nox replay command (save|open|export), MP4 pipe→ffmpeg VERIFIED (16.2s mp4 written), Discord clip upload (Webhook.postFile multipart → DiscordNotifier webhook)
- Endreport ✅ — docs/ 14 files: FEATURES, SECURITY-AUDIT, DESIGN-SYSTEM, ARCHITECTURE, PREMIUM, REPLAY, BACKEND, COSMETICS, STREAMING, DEBUGGING, ANTI-CRACK, CONFIG, STATS, HUD, UPDATES, NATIVE, ADDONS

In Arbeit (Session A):
- (leer — Session-A-Scope komplett; Rest = B-Lane: Modul 17/25 GUI-Restyle, 5 OCR, 26 charts)

## Session B (bestätigt — Telemetry/Stats/Audit/Fix-Doku)
Done:
- Modul 20 ✅ — telemetry/Telemetry (opt-in, batch+HMAC, endpoint ENV), TelemetrySanitizer (denylist keys, IP/coords/home-path scrub), gui/PrivacyScreen (first-start consent, default OFF, persisted nox/privacy.json), NoxClient wired (init/tick/shutdown/popup)
- Modul 26 ✅ (Basis) — stats/Stats (playtime, distance, kills via last-hit credit, deaths, dmg taken, module usage min), StatsStore (lifetime JSON, atomic save), StatsScreen (Session/Lifetime cards + module-usage bars), StatsHooksMixin (destroyBlock), AttackMixin→Stats.onHit. OFFEN: Live-Diagramme-Canvas, Modes, CSV/REST-Export
- Modul 29 ✅ — module/misc/SecurityAudit (checks: tamper/ncore/migrator/update/telemetry-gate → toast+stdout), registriert via module/ExtraModules (NICHT in registerAll — Kollisionsvermeidung)
- M26.5/26.7 teilweise: sessionView() für HUD-Nutzung vorhanden; CSV/REST-Export fehlt

Done (Fortsetzung):
- Modul 1 ✅ — stream/TikTokBridge + stream/RuleEngine + tools/tiktok_sidecar.py + module/misc/TikTokLive (testMode default AN)
- Modul 3 ✅ — stream/ViewerVotes (TikTok-Pfad; Twitch-IRC offen)
- Modul 4 ✅ — stream/GiftCombos
- Modul 5 🟡 — module/misc/CoordFinder (Regex+Clipboard; OCR [OFFEN])
- Modul 6 ✅ — module/misc/PvpCoach (Regelwerk, premium)
- Modul 9 ✅ — module/misc/SkinChanger (nox/skins)
- Modul 10 ✅ — module/misc/Emotes (20 Presets)
- Modul 17.5 ✅ — Interface: colorBlind-Mode + largeText; UICustom.refreshA11y + ok/err/warn-Tokens
- Modul 22 🟡 — replay/ReplayRecorder + module/misc/ReplayMod (Record-Seite; Playback [OFFEN])
- Modul 26.7 ✅ — stats/StatsExport (CSV+JSON); Stats.fpsRing Sparkline
- Modul 30.2 ✅ — premium/Distributed (8 Checks, Gate in Module.setEnabled)
- FIXES.md ✅ — priorisierter Plan für offene funktionale Bugs (nicht umgesetzt, per Scope)

Registrierung: alle B-Module via module/ExtraModules (NICHT registerAll).

In Arbeit (Session B):
- OFFEN.md + STATUS.md Pflege; devloop-Verifikation nach A's Merge

Nicht-anfassen-Bitte an A: telemetry/**, stats/**, gui/PrivacyScreen.java, module/misc/SecurityAudit.java, module/ExtraModules.java, mixin/StatsHooksMixin.java
Konflikt-Notiz: M29 stand auch in A's Liste — Session B hat SecurityAudit.java bereits gebaut und registriert; wenn A eine andere Variante gebaut hat, bitte B's Stand mergen statt ersetzen.

## Regeln
- Wer eine Datei aus der anderen Liste editiert: erst lesen, minimal-diff.
- Neue Packages nur in eigenem Scope.
- COORD.md aktualisieren wenn ein Modul fertig/blockiert ist.
