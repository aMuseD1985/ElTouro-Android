# Plan und Entscheidungen (Stand 13.10.2026)

Mit Marco im Plan Mode abgestimmt („Gibst du den Plan frei?“ → „mach mal weiter“). Grundlage: `docs/android-native-prompt.md`.
Abweichungen vom Prompt sind begründet und hier festgehalten – eine neue Session hält sich daran oder spricht Änderungen mit Marco ab.

## Ausgangsbefund (Code-Lektüre vor M1)

Warum Fahrten mit Display aus Lücken hatten – drei Funde im alten Java-Code, alle in M1/M2 behoben:
1. **Zustand nur im RAM** (`TrackerState`): beendet HyperOS den Prozess und Android startet den Dienst per `START_STICKY` neu,
   ist `session = 0` → `onLocation()` verwirft jeden Punkt. Der Dienst „läuft“, zeichnet aber nichts auf.
2. **Puffer nur im RAM** und der letzte Upload in `onDestroy()` auf dem Main-Thread (scheitert immer).
3. **Weiterleitungen wurden verfolgt:** ohne Tester-Cookie → 302 auf `/access` → 200 → Punkte galten als gesendet.
Dazu (sicher, löst erst M3): die Ansagen laufen in `navigate.js`/`voice.js` in der WebView, und die wird bei Display aus pausiert.
Ob HyperOS den Prozess wirklich beendet, zeigt das Diagnose-Log (`ApplicationExitInfo`, Neustarts, WebView-Herzschlag).

## Architektur

- **Hybrid bleibt, Capacitor bleibt WebView-Host.** Inhalte = Web-App (eine Quelle der Wahrheit), nativ in Kotlin nur, was
  Hintergrund/Sensoren/System braucht. Die vier Java-Klassen sind nach Kotlin portiert (M1).
- **Brücke nur ergänzen** (`docs/app-interface.md`): alte APK + neue Seite und neue APK + alte Seite müssen funktionieren.
- **Geräteschlüssel statt `/api/v1`** (Abweichung): `/api/v1` ist nur mit `api_access` oder Admin nutzbar, normale Fahrer kommen
  nicht dran. Die App bekommt einen eigenen Schlüssel `eld_…` (`app_devices`, `/api/app/device`) für alle Hintergrund-Endpunkte:
  `/api/track` (M2), später `/api/route` (M3), `/api/live` (M7), `/api/app/fcm` (M5), `/api/app/summary` (M6, statt
  `/api/v1/me/summary`). Jeder dieser Endpunkte bekommt die Schlüssel-Annahme wie `track.php` (Patch fürs Web-Repo).
- **Testbare Logik Android-frei** in eigenen Paketen (`diag/`, `ride/`, `nav/`, `voice/`) mit JVM-Tests; Android-Code dünn drumherum.
- **Wenige Bibliotheken** (Abweichung): M2 nutzt SQLite direkt statt Room und AlarmManager statt WorkManager – keine neue
  Abhängigkeit, kein Annotation-Processing, lokal vollständig typprüfbar. WorkManager kommt mit den Widgets (M6).
- **Einwilligung einmal erneuern:** Rechtstexte pro Meilenstein mitschreiben, `CONSENT_VERSION` aber nur einmal erhöhen –
  Vorschlag: zusammen mit FCM (M5). Marco entscheidet beim Einspielen.
- **Server-Änderungen klein, abwärtskompatibel, per Feature-Erkennung.** Die APK lädt die Web-App vom Server: Web-/PHP-Teile
  wirken erst, wenn Marco sie im Web-Repo eingespielt hat (Push auf dessen `main` deployt auf test). Deshalb pro Meilenstein ein
  Patch, nicht alles am Ende. Was ohne Server-Teil schon geht, muss ohne ihn laufen (M2: Rückfall auf Cookie + CSRF).

## Meilensteine

**M1 Diagnose + Härtung** – gebaut. Diagnose-Bildschirm (App-Symbol lange drücken / Benachrichtigung), Log mit Exit-Gründen,
Dienst-Neustarts, abgelehntem `startForeground`, Display/Doze/Energiesparen, Lücken > 10 s, gebündelten Fixes, Upload-Status,
WebView-Herzschlag; Berechtigungsablauf mit „Immer zulassen“, Akku-Dialog, Hersteller-Anleitungen (Xiaomi/HyperOS, Samsung,
Huawei/Honor, OnePlus/Oppo/Realme) mit Direktlinks, Kanal `eltouro_ride_v2` (hoch, lautlos), Wakelock 30 min verlängert.
Kein Server-Teil.

**M2 Aufzeichnung nativ** – gebaut. SQLite-Puffer (`ride.db`), `Uploader`-Regeln, Geräteschlüssel, Watchdog, Boot-Fortsetzung.
Server-Teil: `docs/handoff/` (Patch fürs Web-Repo, lokal gegen MariaDB getestet).

**M3 Fahrmodus nativ** – nächster Schritt. Vorgehen:
- Port der Engine aus `assets/navigate.js` (Web-Repo) nach `nav/` (reines Kotlin): `dist`/`bearing`/`segAt`/`pointAt`/`bendAt`,
  `alongOf`, `locate()` (Sprungregeln siehe `docs/web-context.md`), `onFix()`/`guide()`, `worthAnnouncing()`, Abbiegehinweise aus
  `tours.guidance_json`, Pausen, Halbzeit/Ziel, Abweichung/`reroute()` über `/api/route`, `spokenDistance`, `splitParts`,
  `hintText`, Sprüche (`fastBanter`, `banter`, `bagPick`), Geschichten.
- **Test-Orakel:** ein Node-Harness lädt `navigate.js` **unverändert** mit Attrappen für DOM, Karte und `speechSynthesis`, nutzt
  die Debug-Hooks (`?debug=1` → `window.__nav` mit `onFix`, `state`, `hints`, `spokenDistance`, `splitParts`, `hintText`) und
  spielt GPS-Folgen ab (synthetisch + echte Fahrten aus `/drives`). Ergebnis: Golden Files (Zeitpunkt, Text, Clip-Teile) unter
  `android/app/src/test/resources/`. `Math.random` bekommt auf beiden Seiten denselben Seed (gleicher PRNG in Kotlin); was
  deterministisch ist, muss 1:1 stimmen.
- `voice/`: Manifest `/assets/voice/<lang>/manifest.json`, Clip-ID = erste 12 Zeichen von sha1(Text), Zusammensetzen aus Teilen
  wie `voice_lib.php`/`voice.js`, Clips lokal zwischenspeichern (Display aus, Funkloch), Rückfall `TextToSpeech`; Wiedergabe im
  Dienst mit Audio-Fokus und Ducking (Media3 – erste neue Bibliothek, Stubs für die lokale Prüfung ergänzen).
- Web-Seite: läuft der native Fahrmodus, wird `navigate.js` zur reinen Anzeige (Feature-Erkennung) – Patch fürs Web-Repo.

**M4 Kamera/Dateien, Teilen, Deep-Links** – `onShowFileChooser` mit Android-Foto-Picker (Capacitors `BridgeWebChromeClient`
kann Kamera-Capture schon, prüfen und erweitern), Teilen-Brücke (`navigator.share` fehlt in der WebView), App Links mit
`autoVerify` + Vorlage `/.well-known/assetlinks.json` (Fingerabdruck des Test-Schlüssels).

**M5 FCM-Push** – Server: FCM HTTP v1 mit JWT aus dem Service-Account (openssl, ohne Composer), angebunden an `push_queue`
(gleiche Arten/Einstellungen wie Web-Push), Tokens an `app_devices`; App kompiliert ohne `google-services.json` (das bestehende
`build.gradle` wendet das Plugin nur an, wenn die Datei da ist). Datenschutz (Firebase/Google, Übermittlung), Export/Löschung,
`CONSENT_VERSION`. **Marco legt das Firebase-Projekt an** – die Klickanleitung kommt, wenn M5 dran ist.

**M6 Widgets + Hintergrund-Prüfung** – Glance („Nächste Ausfahrt“, „In der Nähe“, „Fahrt starten/fortsetzen“), Daten über
`/api/app/summary` (Geräteschlüssel), WorkManager. Abweichung: neue Ausfahrten in der Nähe meldet ab M5 der Push; die periodische
Prüfung nur als Rückfall ohne Firebase (Akku).

**M7 Live per WebSocket** – OkHttp-WebSocket im Dienst zum Live-Server (Token `GET /api/live/token`, mit Geräteschlüssel),
Rückfall `/api/live`. Regeln bleiben allein serverseitig.

Native Compose-Bildschirme erst, wenn M1–M7 stabil sind. Kein Redesign der Web-Oberfläche. Play Store später (eigener geheimer
Upload-Schlüssel, Datenschutz-Angaben, Begründung für Hintergrund-Standort) – jetzt nur APK.

## Offene Entscheidungen für Marco

1. `CONSENT_VERSION` – wann erhöhen (Vorschlag: einmal mit M5).
2. Lange Funklöcher: nach 2 h ohne Position beendet der Server die Aufzeichnung (`finishStaleRecordings()`), später ankommende
   Punkte werden mit `{"stored":0}` verworfen. Soll der Server eine solche Fahrt wieder öffnen, wenn die App nachliefert?
3. Ob `app.eltouro.de` schon existiert – bis dahin bauen alle APKs gegen `https://test.eltouro.de`.

## Arbeitsweise

Pro Meilenstein: Commit auf `main` dieses Repos (hier deployt nichts), grüne CI, APK-Artefakt, Testliste in `docs/android.md`,
Server-/Rechtstext-Teil als Patch unter `docs/handoff/` mit Übergabe-Notiz. Vor dem Push `tools/local-check/check.sh`.
Rechte, die diese Instanz braucht: dieses Repo Contents/Actions/Workflows lesen+schreiben, Web-Repo lesen. Tokens nie ins Repo.
