# ElTouro Android-App (Test-APK)

Die App ist eine **Capacitor-Hülle** (dieses Repo): ein WebView, der die Web-App lädt (Standard **Produktion `https://app.eltouro.de`**; test nur bewusst per Run workflow), plus
ein nativer Kern in **Kotlin** für das, was der Browser nicht kann – Aufzeichnen und Navigieren mit ausgeschaltetem Display.
Arbeitspaket und Reihenfolge der Meilensteine: `docs/android-native-prompt.md`; Schnittstelle zur Web-App: `docs/app-interface.md`.
Die Web-App, ihre Endpunkte und die Rechtstexte liegen im Repo `aMuseD1985/ElTouro`.

## APK bekommen (ohne Android Studio)

1. GitHub → Repo `aMuseD1985/ElTouro-Android` → **Actions** → **Android APK** → *Run workflow* (Adresse: Standard `https://app.eltouro.de` = Produktion; für Tests gegen den Testserver `https://test.eltouro.de` eintragen).
   Der Bau läuft auch automatisch bei jedem Push.
2. Nach ca. 5–8 Minuten am Lauf unter **Artifacts** `eltouro-apk` herunterladen (ZIP mit `app-debug.apk`).
   Per Terminal: `gh run download -R aMuseD1985/ElTouro-Android -n eltouro-apk`
3. APK aufs Handy bringen (AirDrop geht nicht; E-Mail/Drive/USB) und öffnen → „Installation aus unbekannten Quellen“ für diese App erlauben.
   Die Test-APK ist immer mit demselben Test-Schlüssel signiert: spätere Versionen lassen sich einfach drüberinstallieren.

## Aufbau (`android/app/src/main/java/de/eltouro/app/`)

| Datei | Aufgabe |
|---|---|
| `ElTouroApp.kt` | Application: startet die Diagnose, protokolliert Prozessstart und **warum der vorige Prozess endete** (`ApplicationExitInfo`, Android 11+) |
| `RideState.kt` | Zustand der Fahrt (Server, CSRF, Session, aktiv) – **auf Platte**, damit ein vom System neu gestarteter Dienst weiter aufzeichnet |
| `TrackerService.kt` | Foreground-Service (Typ Standort, Kanal `eltouro_ride_v2` mit hoher Wichtigkeit, lautlos), Fused Location 1 s, Upload nach `/api/track`, Wakelock 30 min (jede Minute verlängert), Diagnose-Protokoll |
| `TrackerPlugin.kt` | Brücke `EltouroTracker` für `assets/native_tracker.js` – alte Methoden unverändert, neu: `log`, `status`, `diagnostics` |
| `MainActivity.kt` | WebView-Host, Login-Übergabe, Links nur für `app`/`test`/`beta.eltouro.de`; **WebView-Herzschlag** alle 10 s während einer Fahrt |
| `Setup.kt` | Berechtigungen, Akku-Ausnahme, Hersteller-Einstellungen (Autostart usw., nicht-öffentliche Bildschirme nur versucht, Rückfall App-Info), Hinweisdialog beim Fahrtstart (höchstens 1× pro Tag) |
| `DiagnoseActivity.kt` | Bildschirm „Diagnose“: Einrichtung, Zustand, Log, „Log teilen“ |
| `diag/` | `LogFile` (Ringpuffer 2 × 400 kB), `FixStats`, `Names`/`Vendor` (reines Kotlin, JVM-getestet), `Diag`, `DeviceStatus` |
| `ride/` | `PointStore`/`Uploader`/`Http` (reines Kotlin, JVM-getestet) und `SqlitePointStore` (`ride.db`): jeder Aufzeichnungspunkt landet zuerst auf dem Handy und geht von dort nach `/api/track` – bis zu 50 000 Punkte, älteste Fahrt zuerst, ein Request pro Session, höchstens 500 Punkte |
| `DeviceKey.kt` | Geräteschlüssel `eld_…` (`/api/app/device`, `app_device_lib.php`): einmal aus der angemeldeten WebView geholt (Cookie + CSRF), danach lädt der Dienst damit hoch – auch wenn die Cookies weg sind. Antwortet der Server 401, fällt der Upload auf Cookie + CSRF zurück und holt einen neuen Schlüssel |
| `RideWatchdog.kt` | Alarm, den der laufende Dienst jede Minute 10 min nach vorn schiebt; feuert er, ist der Dienst tot und wird neu gestartet. `BootReceiver` setzt eine aktive Fahrt nach Neustart oder App-Update fort |

Texte: `res/values/strings_ride.xml` (EN) und `res/values-de/strings_ride.xml` (DE), gleiche Schlüssel.

## Diagnose

Öffnen: App-Symbol lange drücken → **Diagnose**, oder in der Fahrt-Benachrichtigung „Diagnose“. Oben steht, was für Fahrten mit
Display aus fehlt (✗ antippen), darunter der Zustand (Dienst, letzte Position, Fixes/min, Lücken, Puffer, letzter Upload mit
HTTP-Status, ob die Webseite antwortet, Berechtigungen, Akku-Optimierung, Hintergrund-Einschränkung, Energiesparmodus, Doze,
Standby-Gruppe, Wakelock, Gerät/ROM) und das Log. **„Log teilen“** schickt beides als Textdatei.

Das Log enthält: Prozessstarts und Exit-Gründe des Systems, Start/Neustart/Ende des Dienstes, abgelehntes `startForeground`
(Hintergrund-Start-Sperre), Display an/aus, Doze, Energiesparmodus, Lücken über 10 s, gebündelt gelieferte Positionen,
Wechsel des Upload-Status (401 = Sitzung weg, 400 = CSRF, „no response“ = kein Netz), Pausen der Webseite und jede Minute eine
Zusammenfassung. Keine Positionen, keine Cookies, kein CSRF-Token.

## Upload-Regeln (M2)

`Uploader` (getestet in `UploaderTest`): 2xx → gesendete Punkte löschen; 404/409 → Session kennt der Server nicht mehr, ihre Punkte
werden verworfen; alles andere (401, 400, 403, 302 Zugangsschutz, 5xx, kein Netz) → behalten und später erneut. Weiterleitungen werden
nicht verfolgt – vorher landete ein Upload ohne Tester-Cookie auf der Zugangsseite, bekam dort 200 und die Punkte galten als gesendet.
Fahrtende: `clearSession`/`stop` versuchen den Rest bis zu dreimal in etwa 9 s hochzuladen, bevor die Seite die Aufzeichnung
beendet – endet die Fahrt in einem Funkloch, kommen die letzten Punkte so oft noch an. Klappt es nicht, bleiben sie im Puffer; der
Server nimmt sie danach aber nicht mehr an (Aufzeichnung beendet → `{"stored":0}`).
Offen: Nach 2 h ohne Position beendet der Server die Aufzeichnung (`finishStaleRecordings()`); Punkte, die danach ankommen, nimmt er
mit `{"stored":0}` an und verwirft sie. Das Log meldet es; ob der Server solche Fahrten wieder öffnen soll, entscheidet Marco.

## Testliste M2 (Aufzeichnung nativ)

Voraussetzung: der Server-Teil von M2 (Patch `web-repo/` → Repo `ElTouro`, Migration `app_devices`) läuft auf test.eltouro.de. Ohne ihn lädt die App wie bisher mit Cookie + CSRF hoch.
1. Diagnose: „Geräteschlüssel für den Upload: ja“ (kommt nach dem ersten Fahrtstart innerhalb einer Minute).
2. Fahrt mit Aufzeichnung starten, 2 Minuten fahren, dann **Flugmodus an** für 3–5 Minuten weiterfahren, Flugmodus aus.
   Diagnose: Punkte im Puffer steigen und gehen danach auf 0; `/drives` zeigt die Fahrt ohne Lücke.
3. Während der Fahrt die App in den letzten Apps wegwischen (ohne Schloss). Beendet HyperOS dabei den Dienst, zeigt das Log
   „restarted by the system“ oder nach bis zu 10 min „watchdog: … starting it again“; die Fahrt läuft unter derselben Aufzeichnung
   weiter. **Nicht „Stopp erzwingen“** – das löscht absichtlich auch den Watchdog-Alarm, Android startet danach nichts mehr von selbst.
4. Handy während einer aktiven Fahrt neu starten, entsperren: Benachrichtigung „ElTouro …“ erscheint wieder, Log „reboot“.
5. Abmelden in der App: Diagnose zeigt „Geräteschlüssel: nein“.

## Testliste M1 (Diagnose + Härtung)

1. APK installieren (über die alte), App öffnen, anmelden. App-Symbol lange drücken → „Diagnose“: alles mit ✗ einrichten
   (genauer Standort, Benachrichtigungen, „Immer zulassen“, Akku-Optimierung aus), dann die Xiaomi-Schritte darunter
   (Autostart, Akku-Sparmodus „Keine Einschränkungen“, App in den letzten Apps sperren).
2. Fahrmodus einer Tour starten, „Fahrt aufzeichnen“ an. Der Hinweisdialog darf nur kommen, wenn noch etwas fehlt.
3. 15–30 Minuten fahren, **Display aus**, Handy in der Tasche. Zwischendurch einmal ein paar Minuten ohne Netz, falls es sich ergibt.
4. Fahrt beenden, Diagnose öffnen → **Log teilen** → an Claude schicken. Dazu: Hast du Ansagen gehört? Wie sieht die Fahrt unter `/drives` aus?
5. Gegenprobe: dieselbe Strecke kurz mit Display an.
6. Optional: während einer Fahrt die App aus den letzten Apps wischen – das Log zeigt, ob HyperOS den Dienst danach beendet.

## Was im Hintergrund passiert

- `TrackerService` (Foreground-Service, Benachrichtigung „ElTouro …“) liefert die Position, auch mit Display aus. Beendet das
  System den Prozess, startet Android den Dienst wieder (`START_STICKY`) und er zeichnet mit dem gespeicherten `RideState` weiter –
  vorausgesetzt „Immer zulassen“ und Akku-Optimierung aus (sonst verbietet Android 12+ den Neustart, das Log zeigt es).
- Läuft eine Aufzeichnung, lädt der Dienst die Punkte **selbst** nach `/api/track` hoch (Cookie der WebView + `X-CSRF`), alle 15 s,
  unabhängig davon, ob Android die Seite pausiert. Bei Funkloch wird der Puffer (bis 6000 Punkte) später nachgeschickt.
- Die Seite bekommt jede Position zusätzlich als Ereignis (`assets/native_tracker.js` → `ElTouroNative`) für Karte und Ansagen,
  solange sie läuft. Ansagen mit Display aus hängen davon ab, ob Android die WebView weiterlaufen lässt (Akku-Optimierung ausschalten).
- Im Browser (und iOS) ist `ElTouroNative.available()` falsch – dort ändert sich nichts.

## Bauen (selbst)

```
npm ci && ELTOURO_URL=https://app.eltouro.de npx cap sync android
cd android && ./gradlew testDebugUnitTest assembleDebug   # braucht JDK 21 + Android SDK (36); Kotlin 2.2.20 kommt über Gradle
```

Die CI (`.github/workflows/android-apk.yml`) läuft bei jedem Push und Pull Request: erst die JVM-Unit-Tests (`android/app/src/test/`),
dann die APK. Kotlin-Fehler/-Warnungen, die Testzahl und der `versionCode` stehen als Annotations am Lauf.
Ohne SDK vorab prüfen: `tools/local-check/check.sh` (siehe dort).

## Noch offen

- Widgets (nächste Ausfahrt, in der Nähe), FCM-Push statt Web-Push, Hintergrund-Prüfung auf neue Events.
- Play Store: eigener Upload-Schlüssel (geheim), Datenschutz-Angaben, Begründung für Standort im Vordergrunddienst.
- App Links (`assetlinks.json`), damit Links ohne Auswahldialog in der App öffnen.
