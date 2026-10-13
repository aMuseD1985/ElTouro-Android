# Was die App über die Web-App wissen muss

Auszug aus der `CLAUDE.md` und dem Code des Web-Repos `aMuseD1985/ElTouro`, Stand `fa48544` (13.10.2026).
**Maßgeblich ist immer das Web-Repo** – vor Arbeit an einem Bereich die genannten Dateien dort lesen.

## Fahrmodus und Aufzeichnung (M2, M3)

- `tour_go.php` (`/tour/<id>/go`, `?sim=1` Zeitraffer), `assets/navigate.js`, `assets/voice.js`, `assets/native_tracker.js`,
  `assets/free_ride.js` (`/free`, freie Fahrt ohne Tour).
- **Abbilden auf die Strecke (`locate()`):** Wer die Strecke kurz verlässt und weiter vorn wieder einsteigt, gilt als angekommen
  (Übersprungenes zählt als gefahren). Abkürzungen nicht: Sprung nach vorn höchstens 300 m + 8 m/s × Zeit seit dem letzten
  Treffer (Zeitraffer und erster Treffer unbegrenzt), rückwärts bis 1,5 km; bei mehreren Treffern (Schleife, Hin- und Rückweg)
  gewinnt der Ort nächst dem letzten, Rückwärts zählt dreifach. Wer weiter springt, ist „abseits“ und wird zur letzten gültigen
  Stelle zurückgeführt (`reroute(p, nearest)`). Test: `?debug=1`, `window.__nav.onFix()` mit `state.running = true`.
- **Ansagen nach Zeit statt Distanz.** Abbiegehinweise von BRouter in `tours.guidance_json` (`[[Koordinatenindex, VoiceHint-Befehl,
  Ausfahrt]]`, `GUIDANCE_COMMANDS`); ohne sie erkennt `navigate.js` Kurven aus der Geometrie.
- **Tempo-Sprüche:** ab `FAST_KMH` (22) nach 6 s am Stück, höchstens alle 2,5–5,5 min (`fastBanter()`, Texte `nav.say_fast_<n>`),
  nur bei echtem GPS-Tempo, nie im Zeitraffer, nie kurz vor Abbiegung/Pause. Ton-Knopf: alles an → nur Ansagen → aus
  (`eltouro.sound`, schreibt `eltouro.banter` mit). Landschaftssprüche aus Overpass-Daten pro Tour (`scenery_lib.php`).
- **Stimme:** fertige Clips `assets/voice/<lang>/manifest.json` (exakter Text → Datei), sonst Gerätestimme; Klänge
  `assets/voice/sfx/`; Clips erzeugt `php tools/voice/phrases.php`; Zusammensetzen aus Teilen `voice_lib.php`.
- **Eine Fahrt endet nur, wenn der Fahrer sie beendet** (2 s halten). Offene Aufzeichnung: `activeRecording()` (`track_lib.php`);
  nach 2 h ohne Position beendet sie sich selbst (`finishStaleRecordings()`, beim Öffnen und im Cron). `/api/track resume`
  liefert den bisherigen Weg; `tour_go.php` setzt `data-resume` („Fahrt fortsetzen“), `/free` setzt sofort fort.
- `/api/track`: `start {tour_id}` (0 = freie Fahrt) → `session_id`; `points` (höchstens 600 = `TRACK_MAX_BATCH`, 40 000 pro Fahrt;
  Punkte aus der Zukunft oder vor dem Start werden nicht angenommen); `resume`; `finish` → Kennzahlen (rechnet der Server).
- Datenschutz: Standort im Fahrmodus bleibt auf dem Gerät; gespeichert nur bei „Fahrt aufzeichnen“ (standardmäßig aus, nur für
  den Fahrer sichtbar, löschbar). Aus Aufzeichnungen gespeicherte Touren bekommen einen Wegpunkt alle ≥ 400 m.

## Live (M7)

- Live-Server `assets/live-server/` (Node, `ws`) auf dem VPS `live.eltouro.de`, Positionen **nur im Arbeitsspeicher**. Token:
  `GET /api/live/token` (HMAC, 2 min gültig: Fahrer, Name, aktive Herden). Browser: `assets/live_ws.js`.
- Takt: Fahrmodus sendet alle 3 s (Rückfall PHP `/api/live`: 10 s); Server nimmt eine Position je 2,5 s an, pusht alle 1,5 s.
- Regeln nur in `live_lib.php`/`server.js`: sichtbar für gemeinsame Herden oder alle, unsichtbar in 300 m um den Startpunkt,
  „stop“ löscht, nach 120 s ohne Update weg. „Live teilen“ ist pro Fahrt einzuschalten (Einwilligung).

## Push und Nähe (M5, M6)

- `push_lib.php`: Web-Push ohne Composer (VAPID pro Umgebung in `settings` – nie ersetzen), Warteschlange `push_queue`,
  versendet am Requestende (`pushAfterResponse()`) und im Cron `digest.php`; `push_api.php` (`/api/push`); `sw.js`.
- Einstellungen pro Fahrer in `notify_prefs` (Arten `push_social`, `push_rides_near`, `push_rides_soon`, Umkreis, Gebiet auf
  2 Nachkommastellen gerundet ≈ 1 km). `announceRideNearby()` bei neuer öffentlicher Ausfahrt, `sendRideSoonAlerts()` im Cron.
- FCM soll sich hier einhängen: gleiche Arten, gleiche Einstellungen, Deep-Link auf die passende Seite.

## Fotos (M4)

- Profilfoto: Editor `assets/avatar_editor.js`. Fotowand `/crew/<slug>/photos` (`photos_lib.php`, `photos.php`): der Server
  zeichnet jedes Bild mit GD neu (EXIF/GPS weg, max. 1600 px) – die App muss nichts entfernen, nur Datei-Auswahl/Kamera liefern.

## Anmeldung und Konto

- E-Mail/Passwort in der WebView; Google über Custom Tab (`?app=1`) → `/auth/app-return` → `de.eltouro.app://login?t=…` →
  `/auth/app?t=…` (Einmal-Token `app_login`). „Angemeldet bleiben“: `app_remember` (64 hex, `POST /api/app/remember`,
  `/auth/app?r=…`), widerrufen beim Logout. Session-Cookie `eltouro_sid` lebt 90 Tage.
- Sicherheitsregeln: Tokens nur als SHA-256 in der DB, CSRF als `X-CSRF` bei jedem `fetch`, gleiche Antworten bei
  „Adresse existiert/existiert nicht“.
- Zugangsschutz (Test/Beta): Tester-Cookie `et_access`, sonst 302 auf `/access` – Ausnahmen per Konstante `SKIP_ACCESS_GATE`.
- Einwilligung: `consentGate()`, APIs antworten 403 `{"error":"consent"}`; `CONSENT_VERSION` (aktuell 8) in `core.php`.
- DSGVO: jede neue Tabelle/Spalte mit Personenbezug in `exportAccountData()` und `deleteAccount()` bedenken.

## Rechtliche Leitplanken (nicht verhandelbar)

ElTouro vermittelt, veranstaltet keine Fahrten; nichts als „geprüft“ oder „sicher“ kennzeichnen. Mindestalter 16. Keine
Drittanbieter-Requests aus der App außer den Kartenkacheln (und künftig FCM). Die Datenschutzerklärung beschreibt das
tatsächliche Verhalten – wer Datenflüsse ändert, passt sie an (Abschnitt „Android-App“ in `legal_texts.php`).

## Sprache und Marke

Duzen, kein Gendern (Fahrer, Teilnehmer, Organisator). Herde (crew), Leitstier (crew lead), Stammtisch (crew talk), Tour (route),
Ausfahrt (ride). Maskottchen Touro – der Stier auf dem E-Scooter – trägt immer seine Sonnenbrille. Farben: Nacht `#14263F`,
Jeans `#2F5E8C`, Gold `#D7A845`, Kreide `#E8ECF1`. Claim: „Finde deine Tour. Finde deine Herde.“
