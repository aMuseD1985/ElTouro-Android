# Aufgaben für die Web-Session (Android-Session, 14.10.2026)

Drei Aufträge aus Marcos Test und Wünschen. Teil 1 und 2 betreffen den Fahrmodus (`track.php`, `track_lib.php`, `tour_go.php`,
`free.php`, `navigate.js`, `free_ride.js`), Teil 3 ist ein neuer Endpunkt für die Homescreen-Widgets der App. Die App-Seite ist für
alle drei schon gebaut (APK `versionCode` 1010). Bitte Antwort als `docs/handoff/from-web-4.md`.

---

## 1. Eine Fahrt darf nie verloren gehen

**Marco:** „Bei einer Fahrt wurde die Session beendet (vermutlich durch App-Neustart), danach waren alle Fahrtinfos weg. Das darf
nicht passieren – laufend am Profil speichern, beim nächsten Reconnect/Relogin wieder zeigen und mit den weiteren Daten zeitlich
und in der Strecke verbinden.“

**Was passiert ist (aus dem Code):**
- Die Punkte selbst liegen laufend auf dem Server (Upload alle 15 s) – verloren ist die *Fahrt als Ganzes*: nach dem Neustart
  öffnete die App die Startseite; auf der Tourseite heißt der große Knopf „Losfahren“, und `start` legt **immer eine neue**
  Aufzeichnung an (`startTrack()`), auch wenn eine offene derselben Tour existiert. Die neue beginnt bei 0 km, die alte bleibt
  offen und wird nach 2 h still beendet → zwei halbe Fahrten, keine Anzeige der bisherigen.
- Punkte, die nach dieser 2-h-Grenze ankommen (Funkloch, Handy lange aus), nimmt `addTrackPoints()` mit `{"stored":0}` an und
  verwirft sie.

**Schon in der App (bitte nicht doppelt bauen):** Die App merkt sich die Fahrseite (`/tour/<id>/go` oder `/free`) und öffnet sie
nach einem Neustart innerhalb von 12 h selbst wieder (auch nach Neu-Anmeldung: `/auth/app?r=…&next=<Fahrseite>`); die
Fahrt-Benachrichtigung führt zur Fahrt; der native Dienst zeichnet über den Neustart hinweg unter derselben Session weiter. Startet
die Seite trotzdem eine zweite Aufzeichnung, steht im Diagnose-Log `page: NEW recording #… while #… was still open`.

**Bitte im Web-Repo:**
1. **`start` setzt fort statt neu anzulegen.** Gibt es für den Fahrer eine offene Aufzeichnung derselben Tour (bzw. eine offene
   freie Fahrt bei `tour_id: 0`), antwortet `start` mit deren `session_id` und `"resumed": true` (plus den Daten, die heute `resume`
   liefert). Eine neue Aufzeichnung nur mit `{"action":"start","fresh":true}` („Neue Fahrt beginnen“) – die alte wird dann
   ordentlich beendet. Offene Aufzeichnung einer *anderen* Tour: vorher beenden (sie bleibt als eigene Fahrt erhalten).
2. **Seite setzt automatisch fort.** `tour_go.php`/`free.php` mit offener Aufzeichnung: ohne Rückfrage fortsetzen und alles
   wiederherstellen – gefahrener Weg auf der Karte, Distanz, Zeit seit Start, Position auf der Tour (`state.along`, `halfway`, Hinweise
   davor als erledigt) aus dem letzten Punkt per `locate()`. „Neue Fahrt beginnen“ als kleiner zweiter Weg. Startseite: „Weiterfahren“
   (`anyActiveRecording()`) gut sichtbar.
3. **Späte Punkte anhängen statt verwerfen.** Kommen Punkte für eine Aufzeichnung, die der Server wegen 2 h Stille beendet hat,
   und liegen sie innerhalb von 12 h nach deren Start: annehmen, an die Fahrt hängen (`seq` fortführen) und die Kennzahlen neu
   rechnen (`finishTrack()` noch einmal bzw. eine Funktion „recompute“). `{"stored":0}` nur noch, wenn die Punkte wirklich nicht
   passen (vor dem Start, in der Zukunft, über `TRACK_MAX_POINTS`). Für die App gilt weiter: 2xx = angekommen, 404/409 = verwerfen.
4. **Zeitlich dicht liegende Teile verbinden.** Liegen trotzdem zwei Aufzeichnungen desselben Fahrers dicht hintereinander (Ende A
   → Start B < 30 min, gleiche Tour bzw. beide frei), beim Beenden zu **einer** Fahrt zusammenführen: Punkte zeitlich sortiert,
   Kennzahlen neu, eine einzige `ride_recorded`-Aktivität (die zweite `voided`). Zusätzlich in `/drives` „Mit der vorigen Fahrt
   verbinden“ für alte Fälle. Eine Lücke > 2 min zwischen zwei Punkten in Karte und `/drives` als Unterbrechung zeichnen, nicht als
   gerade Linie (sonst sieht ein Funkloch wie Abkürzen aus).

**Abnahme:** Fahrt starten, App beenden (wegwischen) und wieder öffnen → dieselbe Fahrt mit bisherigem Weg und Distanz; auf der
Tourseite „Losfahren“ → setzt fort statt neu; Flugmodus 3 h → danach ankommende Punkte landen in derselben Fahrt.

---

## 2. Einsteigen in eine Tour an beliebiger Stelle

**Marco:** „Es muss möglich sein, bei einer Tour jederzeit einzusteigen und nicht erst ab Start – das wird der Server regeln müssen.“

**Heute:** `locate()` lässt vor dem ersten Treffer jede Stelle der Strecke zu (`state.locked` false → kein Limit); wer weiter weg
ist, wird zur nächsten Stelle geführt (`reroute(p, nearest)`). Technisch klappt Einsteigen also schon – aber alles drumherum
rechnet mit „ab Start“:
- **Ansagen:** Halbzeit (`total / 2`) kommt sofort, wenn man bei 70 % einsteigt; Start-/Restansage nennt die ganze Länge.
- **Kennzahlen und Aktivität:** eine Teilfahrt zählt wie die ganze Tour (`ride_recorded` mit `tour_id`), Bestzeiten/Auszeichnungen
  (falls vorhanden) wären falsch.
- **Ausfahrten:** wer später dazustößt, kann sich nach dem Start evtl. nicht mehr zusagen oder einchecken.

**Bitte:**
1. **Einstieg erkennen** (`navigate.js`): erster Treffer bei mehr als 200 m vom Start → `join_along` merken. Ansage „Du steigst bei
   km X ein – noch Y km“ (neue Sätze in `voicePhrases()`!), Halbzeit bei `join + (total − join) / 2`, Ziel wie gehabt, „erledigt“ auf
   der Karte erst ab dem Einstieg. Gerne eine Vorschau „Hier steigst du ein“ auf der Karte vor dem Losfahren.
2. **Server merkt sich den Einstieg:** `start {tour_id, join_along_m}` (bzw. nachgereicht mit dem ersten Treffer), Spalte
   `track_sessions.join_along_m`; Kennzahlen: gefahrener Teil der Tour (von–bis, Anteil in %); `ride_recorded` mit `partial: true`
   und Anteil. Bestenlisten/Auszeichnungen nur für vollständige Fahrten (z. B. ≥ 90 %).
3. **Ausfahrten:** Zusagen bzw. „ich stoße dazu“ auch nach dem Start bis zum Ende; Live-Sichtbarkeit wie bei allen anderen.
4. Logik bitte in `navigate.js` lassen und über `?debug=1` (`window.__nav`) testbar halten – die Android-Session portiert sie in M3
   1:1 nach Kotlin und prüft gegen genau diese Hooks.

---

## 3. Endpunkt für die Homescreen-Widgets: `GET /api/app/summary`

Die App hat vier Widgets (Freie Fahrt, In der Nähe, Deine Herden, Was gibt's Neues). Das erste braucht nichts vom Server, die
anderen drei lesen diesen Endpunkt (alle 30 min, beim Öffnen der App, per ↻). Solange er fehlt (404), zeigen sie „Kommt mit dem
nächsten Server-Update“ und öffnen beim Tippen `/rides`, `/crews` bzw. `/notifications`.

**Auth wie `track.php` mit Geräteschlüssel:** `Authorization: Bearer eld_…` / `X-Device-Token`, `SKIP_ACCESS_GATE`, `NO_SESSION`,
`appDeviceAuthenticate()` (401 `device`, 403 `consent`). Nur GET. `Cache-Control: no-store`. Darf pro Fahrer 60 s gecacht werden.

**Parameter:** `v=1`; optional `lat`, `lng` – die letzte Position einer Fahrt, von der App auf **2 Nachkommastellen** gerundet
(≈ 1 km). Nicht speichern, nicht loggen. Ohne sie: das Gebiet aus `notify_prefs` (Push & Nähe); ohne beides `near` leer mit
`label: null`. Umkreis: der des Fahrers aus `notify_prefs`, sonst 25 km.

**Antwort (genau dieses Format, jede Liste höchstens 3, `url` immer ein eigener Pfad „/…“, unbekannte Felder ignoriert die App):**
```json
{
  "v": 1,
  "generated_at": "2026-10-14T12:00:00Z",
  "user": {"name": "Marco"},
  "ride": {"session_id": 12, "tour_id": 45, "title": "Rhein-Runde", "url": "/tour/45/go", "started_at": "2026-10-14T11:00:00Z", "distance_m": 12345},
  "near": {
    "label": "Duisburg", "radius_km": 25,
    "rides": [{"title": "Feierabendrunde", "starts_at": "2026-10-14T16:30:00Z", "distance_km": 3.2, "url": "/ride/9"}],
    "tours": [{"title": "Hafen & Halden", "length_km": 32.5, "distance_km": 1.2, "url": "/tour/45"}]
  },
  "herds": [{"name": "Rheinbullen", "unread": 4, "last": "Anna: Treffen um 18 Uhr?", "url": "/crew/rheinbullen/talk"}],
  "news": [{"text": "Neue Ausfahrt in deiner Herde: Feierabendrunde", "at": "2026-10-14T10:00:00Z", "url": "/ride/9"}],
  "unread_total": 7
}
```
- `ride`: offene Aufzeichnung (`anyActiveRecording()`), sonst `null`; `url` = Fahrseite.
- `near.rides`: kommende Ausfahrten der nächsten 7 Tage im Umkreis, die der Fahrer sehen darf (öffentlich oder eigene Herden), nach
  Startzeit. `near.tours`: sichtbare Touren im Umkreis, nach Entfernung zum Startpunkt. `label`: Ortsname des Gebiets, sonst `null`.
- `herds`: Herden des Fahrers mit ungelesenen Stammtisch-Beiträgen (`herd_reads`), die mit den meisten zuerst; `last` = „Name: Text“
  des neuesten Beitrags, ohne Formatierung, ≤ 80 Zeichen. `unread_total` = Summe der ungelesenen Beiträge.
- `news`: das, was die Startseite bzw. `/notifications` dem Fahrer als neu zeigt, neueste zuerst, als kurzer Satz.
- Zeiten ISO 8601 in UTC. Gleiche Sichtbarkeitsregeln wie im Web (`canSeeTour()` usw.).

**Dazu:**
- `/free?autostart=1` (Widget „Freie Fahrt aufzeichnen“): `/free` startet die Aufzeichnung ohne zweiten Tipp – oder setzt die offene
  fort (Teil 1). Ohne Parameter wie heute.
- **Datenschutz** („Android-App“, DE+EN, Marker bleibt): ein Satz zu den Widgets – die App fragt mit dem Geräteschlüssel eine
  Übersicht ab und schickt dafür, wenn vorhanden, die ungefähre letzte Position einer Fahrt (auf etwa 1 km gerundet) mit; der Server
  speichert sie nicht. Hash des alten Standardtexts in `$earlierDefaults` wie gehabt; `CONSENT_VERSION` mit FCM.
- `docs/app-interface.md` (beide Repos): Endpunkt in Abschnitt 2 eintragen.
