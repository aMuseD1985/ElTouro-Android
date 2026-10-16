# Von der Web-Session an die Android-Session (16.10.2026)

Antwort auf `aufgaben-server-1.md` und `app-ohne-browser-hinweise.md`. Web-Repo `main` @ `0bf6133`. Auf test automatisch; **live erst mit Marcos ZIP**.

## Hinweise-Patch
`bae378b` („In the app: no keep-the-display-on hints …“) steht schon auf `main` (jemand hat ihn am 14.10. eingespielt) – ich habe nichts doppelt gemacht,
nur darauf aufgesetzt. Bitte in `docs/app-interface.md` den Satz zu `ElTouroApp/<n>` und `web-only` ergänzen (steht dort noch nicht in meiner Kopie).

## Teil 1 – eine Fahrt darf nie verloren gehen (erledigt, lokal gegen MariaDB getestet)
- **`POST /api/track {action:"start", tour_id}` setzt fort** (`resumed: true` + `session_id`, `points`, `distance_m`, `started_at`, neu: `last_at`), wenn es eine offene
  Aufzeichnung derselben Tour gibt (bzw. bei `tour_id: 0` eine offene freie Fahrt). Neue Fahrt nur mit `{"fresh": true}` (die offene wird ordentlich beendet).
  Eine offene Aufzeichnung einer *anderen* Tour wird vorher beendet (bleibt als eigene Fahrt). Ohne offene Aufzeichnung wie bisher `{session_id, resumed:false}`.
- **Späte Punkte werden angehängt:** für eine beendete Aufzeichnung nimmt der Server Punkte bis **24 h nach dem Ende** an, wenn sie innerhalb der ersten
  **12 h der Fahrt** liegen (Zukunft/vor dem Start/`TRACK_MAX_POINTS` wie bisher raus); danach werden Kennzahlen und das `ride_recorded`-Ereignis neu gerechnet.
  `{"stored":0}` heißt jetzt wirklich „nichts passte“. 2xx = angekommen, 404/409 = verwerfen bleibt.
- **Nahe Teile werden zu einer Fahrt:** beim Beenden wird eine Aufzeichnung mit der davor verbunden (gleicher Fahrer, gleiche Tour bzw. beide frei, Lücke < 30 min):
  Punkte zeitlich zusammen, Kennzahlen neu, ein Ereignis. **Die Antwort auf `finish` nennt dann die `session_id` der verbleibenden Fahrt** (kann von der
  gesendeten abweichen – bitte immer die zurückgegebene nehmen); die alte Id gibt danach 404.
- **Lücken > 2 min** werden auf der Fahrtseite (`/drive/<id>`) als Unterbrechung gezeichnet (MultiLineString), nicht als gerade Linie. **Offen:** „Mit der vorigen Fahrt
  verbinden“ in `/drives` für alte Fälle – kommt, wenn Marco es braucht.
- **Seite:** `navigate.js` stellt beim Fortsetzen den Stand wieder her (`restoreProgress()`: Position auf der Tour aus dem letzten Punkt, Halbzeit still gesetzt,
  Restdistanz), sagt den Start nicht noch einmal an und **setzt in der App von selbst fort** (`ui.start.click()` nach 700 ms, wenn `ElTouroNative.available()`), ohne
  Tippen. `free_ride.js` übernimmt eine fortgesetzte Fahrt aus `start` (Weg zurück auf die Karte). „Neue Fahrt beginnen“ bleibt der vorhandene Knopf `ride-discard`.
- **Noch nicht (Teil 2):** Einstieg an beliebiger Stelle (`join_along`, Ansagen, `partial`, Spalte). Ich setze das nach Marcos Rückmeldung um; die Logik bleibt in
  `navigate.js` und über `?debug=1` testbar, wie gewünscht.

## Teil 3 – `GET /api/app/summary` (erledigt, Format genau wie spezifiziert)
Geräteschlüssel wie `track.php` (`SKIP_ACCESS_GATE`, `NO_SESSION`, 401 `device`, 403 `consent`; zum Anschauen geht auch die Browser-Sitzung), nur GET, `no-store`.
`lat`/`lng` auf 2 Nachkommastellen gerundet, nur für diese Antwort, nicht gespeichert/protokolliert; sonst das Gebiet aus `notify_prefs` (Umkreis dort, sonst 25 km).
`label` per `placeNear()` (Nominatim, gecacht – die erste Anfrage zu einem neuen Ort kann ~1 s dauern; Antwort bleibt sonst unter 100 ms).
`near.rides`: 7 Tage, sichtbar wie im Web; `near.tours`: sichtbare Touren nach Entfernung zum Start; `herds`: nur mit ungelesenen Beiträgen, die meisten zuerst, `last` ≤ 80 Zeichen
ohne Formatierung; `news`: ungelesene Benachrichtigungen (Glocke); Zeiten ISO 8601 UTC. **Kein 60-s-Cache** (nicht nötig; jede Liste ist ein paar indizierte Abfragen).
Ohne Gebiet: `near.label` null, Listen leer. Datenschutz „Android-App“ (DE+EN) hat den Widget-Satz; `CONSENT_VERSION` unverändert.
- **`/free?autostart=1`** startet die freie Fahrt von selbst (oder setzt die offene fort, s. o.).

## Zu Marcos neuen Meldungen
1. **„Fortschritt weg nach Minimieren/Zurückkommen“:** war genau Teil 1 (jedes „Losfahren“ legte eine neue Aufzeichnung an und beendete die alte; die Seite
   kannte den Fortschritt nicht mehr). Mit APK 1010+ und diesem Server-Stand sollte die laufende Fahrt nahtlos weitergehen. **Ansagen bei minimierter App kommen
   weiter erst mit M3** (die Navigation läuft in der WebView).
2. **„Session nach Google-Login verschwindet oft“:** serverseitig kann ich es nicht belegen – Sitzungen liegen 90 Tage in `data/sessions` (nichts räumt dort auf), das Cookie
   ist persistent, `?r=` meldet neu an. Bitte Marcos Diagnose-Log nach so einem Fall auswerten (Abschnitt „Anmeldung“: Cookie ja/nein, Token ja/nein, welche
   Übergabe mit welchem HTTP-Status). Verdachtspunkte, die ich von hier sehe: (a) `/auth/app?t=…` ist einmalig – ein zweiter Aufruf (Wiederholung des Intents)
   endet mit `flash(google.error)` + `/login`; (b) `app_remember`-Token liegen höchstens 5 pro Fahrer, ältere werden gelöscht (mehrere Geräte/Neuinstallationen können
   sich verdrängen); (c) Logout/Passwort-Reset widerrufen alle App-Tokens des Kontos. Wenn im Log „Cookie nein, Token ja, `?r=` → /login?forget=1“ steht, ist (b)/(c) der Fall.
3. **Minimierte Ansicht (Bild-in-Bild) wie YouTube/Google Maps:** das ist **App-seitig** (Activity). Web-Seite ist vorbereitet: `assets/style.css` v82 zeigt in einem kleinen
   Fenster (Breite *und* Höhe ≤ 420 px) nur nächste Abbiegung + Karte und blendet Bedienelemente aus (`.ride-panel`, `(i)`, Untertitel, Karten-Steuerung).
   Für euch: `android:supportsPictureInPicture="true"`, `configChanges` um `smallestScreenSize|screenLayout|screenSize|orientation` ergänzen (ist schon so), beim Verlassen
   (`onUserLeaveHint`) während einer Fahrt `enterPictureInPictureMode(PictureInPictureParams … Rational(1,1)/(9,16))`, in `onPictureInPictureModeChanged` WebView weiterlaufen lassen
   (kein `onPause`-Ausschalten), Audio bleibt über den Dienst. Ausfahrts-/Fahrseiten außerhalb der Fahrt gehen nicht in PiP.
