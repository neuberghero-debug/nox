# FIXES.md — funktionale Bugs

Stand: 2026-11-16 (Update 2). **P0+P1 sind umgesetzt** (DeepSeek-Review,
deployed sig `721c0de`) — P2 bleibt Plan.
Priorität: **P0** = falsches Verhalten/Ban-Risiko · **P1** = sichtbarer Bug · **P2** = Robustheit/Polish.

### ✅ Umgesetzt (1-9)

1. **Auction: Spieler-Chat als Bid** — `ChatMixin` füttert `a.onChat()` nur
   noch aus `addServerSystemMessage`; `addPlayerMessage` zählt nie als Gebot.
2. **Auction: Regex** — alle 3 PAY-Patterns verankert (`^…$`, `matches()`),
   optionales `[Prefix]`, explizite Gruppen-Reihenfolge (`amountFirst` per
   Pattern-Index) statt Heuristik — "Steve7" kann kein Betrag mehr werden.
3. **Auction: Tie/Leader** — `firstSeen`-Map; `refreshLeader()` wählt
   amount DESC, firstSeen ASC — frühester Bieter gewinnt Gleichstand.
4. **Scoreboard** — `getPlayerTeam` → `getPlayersTeam` (Mitgliedschaft);
   gleicher Fix in `DebugConsole` Sidebar-Dump.
5. **SelfTag: catch** — `warned`-Flag, log-once statt still schlucken.
6. **SelfTag: stale Attachment** — `nameTagAttachment` wird jeden Frame
   neu gerechnet (Teleport/Dim-Wechsel safe).
7. **HudEditor Nudge** — SDL-Mapping korrigiert: 80=LEFT→`-x`, 79=RIGHT→`+x`,
   82=UP→`-y`, 81=DOWN→`+y`.
8. **HudLayout.rects stale** — 400ms-TTL über `seen`-Map; `rect()` expired
   + entfernt Einträge von Widgets, die nicht mehr reporten.
9. **SessionStats Parsing** — nur Member-Teams *sichtbarer* Sidebar-Entries
   (`listPlayerScores` + `getPlayersTeam`), nicht mehr alle PlayerTeams;
   Baseline-Reset sobald sich das Label-Set ändert (Layout-Wechsel).

---

## ~~P0~~ — Auction (`module/donutsmp/Auction.java` + `mixin/ChatMixin.java`) — GEFIXT

1. **Spieler-Chat wird als Gebot gezählt.** `ChatMixin` füttert signierte
   Spielernachrichten in den Parser — ein normaler `/pay`-Erfolg im Chat ODER
   ein Spieler, der Zahlen schreibt, kann als Bid durchgehen.
   *Fix:* System-Chat-spezifischen Hook nutzen —
   `ClientPacketListener.handleSystemChat` (26.3: verifiziert vorhanden,
   `handleSystemChat(SystemChatPacket)` / disallowed-Variante). Nur
   System-/Server-Nachrichten in `Auction.parse()` lassen.

2. **Regex nicht verankert + Betrags-Raten.** Patterns matchen substrings;
   Bidder-Namen mit Ziffern ("Steve7", "Bob5") wurden als Betrag gelesen.
   Teilfix existiert (Full-Match-Ansatz begonnen) — offen: explizite
   Gruppen-Reihenfolge `(sender)` vs `(amount)` statt Heuristik, und
   `matches()` statt `find()` für alle drei Pattern-Varianten.

3. **Leader/Winner-Inkonsistenz bei Gleichstand + `System.currentTimeMillis()`.**
   Countdown und "highest bid" können auseinanderlaufen; bei Ties gewinnt
   nicht zwingend der früheste Bieter.
   *Fix:* ein `Bid`-Record (name, amount, firstSeenTick) — sortiert nach
   amount DESC, firstSeen ASC; Ende via Tick-Zähler statt Wall-Clock.

---

## P0 — Scoreboard (`module/render/Scoreboard.java` + `mixin/ScoreboardMixin.java`)

4. **`getPlayerTeam(String)` ist Team-Name-Lookup, nicht Mitgliedschaft.**
   Für "welches Team gehört diesem Spieler" muss `getPlayersTeam(String)`
   (verifiziert in 26.3 vorhanden) genutzt werden — sonst fallen
   Sidebar-Einträge/Own-Team-Filter leer oder falsch aus.

---

## P1 — SelfTag (`module/render/SelfTag.java` + `mixin/SelfTagMixin.java`)

5. **Breites `catch(Throwable ignored)`** schluckt jeden Fehler still —
   schwer debuggbar; mindestens log-once einbauen.
6. **`nameTagAttachment` bleibt stale**, wenn das Attachment einmal gesetzt
   wurde und danach die Bedingung nicht mehr greift — Attachment muss
   jeden Frame neu gerechnet oder explizit gecleart werden.

---

## P1 — HUD-Editor (`gui/HudEditorScreen.java` + `gui/HudLayout.java`)

7. **Pfeiltasten-Nudge invertiert.** SDL/GLFW-Map (DebugConsole):
   260/261/262/263 → 80/79/81/82. Aktueller Code:
   `dx = key == 79 ? -step : key == 80 ? step : 0` — SDL 79 = RIGHT ergibt
   `-step` (falsch). Rechts/links (und analog oben/unten) prüfen+drehen.
8. **`HudLayout.rects` stale.** `report()` schreibt Rechtecke, aber ohne
   Frame-Reset bleiben Einträge für Widgets, die nicht mehr rendern —
   Editor zeigt Geister-Boxen.
   *Fix:* `HudLayout.beginFrame()`-Reset pro Frame oder TTL pro Rect.

---

## P1 — SessionStats (`module/donutsmp/SessionStats.java`)

9. **Team-Prefix-Parsing fragil**: DonutSMP verschiebt Sidebar-Zeilen
   zwischen Prefix/Suffix; Baseline setzt sich beim ersten Parse fest und
   kann durch Layout-Wechsel vergiftet werden.
   *Fix:* `Hud.extractScoreboardSidebar`-basierte Snapshot-Logik +
   Baseline-Invalidierung bei Server-/World-Wechsel; Label-Whitelist als
   Setting statt hartem Regex.

---

## P2 — UI-Infra

10. **`UICustom` fehlen `warn`/`ok`/`err`-Tokens** — volle Design-Palette
    (Session A hat `gui/ds/DS` gebaut — ggf. dort schon abgedeckt; beim
    Merge prüfen und ggf. UICustom auf DS mappen).
11. **`NoxRPC` "Origin is not allowed"** spammt im Log — IPC-Handshake
    gegen neuere Discord-Builds: Origin-Header/Whitelist prüfen, sonst
    Fehler einmal melden und Backoff statt Spam.
12. **`NoxWindowScreen` vs `NoxClickGui` Doppel-GUI** — Legacy-Screen
    lebt noch parallel; nach M25-Abnahme: Keybind/Einstiegspunkt auf das
    neue GUI vereinheitlichen, altes als `legacy` flaggen oder entfernen.

---

## P2 — aus OFFEN/früheren Audits

13. OreSim/SoundFind: Chunk-Event-Filter auf Performance prüfen
    (pro-Tick-Allokationen im Hotpath).
14. StaffNotifier/GhostMode: KEEP-Liste regelmäßig gegen neue Detektoren
    auditieren (Self-Kill-Bug wurde gefixt — Regressionstest fehlt).
15. Tracers: Entity-Lookup pro Frame — bei großen Servern
    `level.entitiesForRendering()` statt voller Iteration erwägen.

---

### Annahmen/Notizen
- Alles oben wurde mindestens einmal code-verifiziert (javap/Source),
  NICHT runtime-reproduziert — P0/P1 vor Umsetzung kurz auf dem
  Testserver reproduzieren.
- Änderungen an Mixins → `python3 tools/nox_doctor.py` Pflichtlauf.
