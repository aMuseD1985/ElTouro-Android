# ElTouro-Android – Übergabe für Claude

Einstiegspunkt für jede Session in diesem Repo. Erst lesen, dann arbeiten. Stand: 13.10.2026.
Ansprechpartner und Product Owner: Marco. Kommunikation auf Deutsch, direkt und technisch.

## Worum es geht

Die Android-App von ElTouro (Community für E-Scooter-Fahrer). Hybrid: Inhalte bleiben die Web-App im WebView (Capacitor 8),
alles, was Hintergrund, Sensoren oder System braucht, ist nativ in Kotlin.

| Dokument | Inhalt |
|---|---|
| `docs/android-native-prompt.md` | Arbeitspaket (Ziel, Meilensteine M1–M7, Abnahme) |
| `docs/plan.md` | **mit Marco abgestimmter Plan**: Befund, Architektur-Entscheidungen und Abweichungen, Vorgehen je Meilenstein, offene Entscheidungen |
| `docs/android.md` | Aufbau der App, Diagnose, Upload-Regeln, Testlisten pro Meilenstein |
| `docs/app-interface.md` | Vertrag mit der Web-App (Brücke, Endpunkte, Antworten, Hosts) – liegt in beiden Repos |
| `docs/web-context.md` | was die App über die Web-App wissen muss (Fahrmodus-Regeln, Stimme, Live, Push, Fotos, Login, Recht, Marke) |
| `docs/handoff/` | Patches und Notizen für das Web-Repo (`web-repo.md` zuerst lesen) |
| `tools/local-check/` | Typprüfung und JVM-Tests ohne Android SDK – vor jedem Push |

## Zwei Repos

- **Hier:** nur die App – `package.json`/`capacitor.config.js`/`www/` (Capacitor), `android/` (Gradle-Projekt), CI, Doku.
- **`aMuseD1985/ElTouro`:** Web-App, alle Endpunkte (`track.php`, `app_device.php`, `app_login.php` …), `assets/native_tracker.js`
  (JS-Seite der Brücke), `assets/navigate.js`/`voice.js`/`voice_lib.php` (Vorlage für den nativen Fahrmodus), Rechtstexte,
  Migrationen. Dort deployt jeder Push auf `main` sofort auf test.eltouro.de – **Server-Änderungen gehen als Patch an Marco**,
  er prüft PHP und Rechtstexte und spielt sie ein. Lesen darf diese Instanz das Web-Repo, schreiben nicht.
- Hier deployt nichts automatisch; die CI baut bei jedem Push eine APK.

## Harte Regeln

- Kotlin, Code und Kommentare englisch; UI-Texte in `res/values/*.xml` (EN) **und** `res/values-de/*.xml` (DE), gleiche Schlüssel.
  Duzen, **kein Gendern**, Marke „ElTouro“ in einem Wort, Touro trägt immer seine Sonnenbrille.
- Keine neuen Tracker oder Drittanbieter; einzige geplante Ausnahme: Firebase Cloud Messaging (M5). Bibliotheken nur mit Grund;
  Plattform-APIs bevorzugt (M2: SQLite direkt statt Room, AlarmManager statt WorkManager).
- Die Brücke `EltouroTracker` und die Endpunkte nur ergänzen, nie ändern (`docs/app-interface.md`).
- **Signierschlüssel `android/app/debug.keystore` nie ersetzen** – sonst lässt sich die APK nicht mehr über die alte installieren.
  Der Play-Store-Schlüssel wird ein anderer, geheimer.
- Eine Fahrt endet nur, wenn der Fahrer sie beendet. Standort nur, solange eine Fahrt läuft; gespeichert nur bei „Fahrt aufzeichnen“.
- Datenflüsse geändert → Datenschutz-Patch fürs Web-Repo (siehe `docs/app-interface.md`, Abschnitt 4).

## Bauen und testen

Vor jedem Push `tools/local-check/check.sh` (Typprüfung aller Kotlin-Quellen gegen `android.jar` + Attrappen, JVM-Tests).
Kein Android Studio, kein Gerät: gebaut wird in der CI (`android-apk.yml`: `testDebugUnitTest assembleDebug`, Artefakt
`eltouro-apk`). Logik, die sich testen lässt, liegt Android-frei in `diag/`, `ride/` (später `nav/`, `voice/`) und hat JVM-Tests
unter `android/app/src/test/`. Marco testet auf einem **Xiaomi 15 Ultra (HyperOS)** und schickt das Log aus „Diagnose → Log teilen“.

## Stand

| Meilenstein | Stand |
|---|---|
| M1 Diagnose + Härtung | gebaut, CI grün (erster Lauf 13.10., 21 Unit-Tests, keine Warnungen), APK `versionCode` 1003; **Gerätetest auf dem Xiaomi offen** – Marco schickt das Log |
| M2 Aufzeichnung nativ (SQLite-Puffer, Geräteschlüssel, Watchdog) | App-Teil gebaut und in derselben APK; läuft bis zum Server-Teil mit Cookie + CSRF. Server-Teil: `docs/handoff/` (Tabelle `app_devices`, `/api/app/device`, `track.php`, Datenschutz „Android-App“; lokal gegen MariaDB getestet; `CONSENT_VERSION` bewusst nicht erhöht) – **Server-Teil im Web-Repo eingespielt (`fc55030`, test automatisch; live mit Marcos ZIP) – Antworten und Hinweise: `docs/handoff/from-web.md`** |
| M3 Fahrmodus nativ (Navigation + Sprache) | offen – Port von `navigate.js` mit Golden-Tests |
| M4 Kamera/Dateien, Teilen, Deep-Links | offen |
| M5 FCM-Push | offen – Marco legt das Firebase-Projekt an |
| M6 Widgets + Hintergrund-Prüfung | offen |
| M7 Live per WebSocket | offen |

## Zugriff

Diese Instanz braucht: dieses Repo Contents/Actions/Workflows lesen+schreiben, das Web-Repo lesen (für `navigate.js`, `voice.js`,
`voice_lib.php`, Endpunkte). Ins Web-Repo schreibt sie nicht – dafür gibt es `docs/handoff/`. Tokens gehören nie ins Repo.
CI-Läufe und Annotations: `GET /repos/aMuseD1985/ElTouro-Android/actions/runs`, dann `/commits/<sha>/check-runs` →
`/check-runs/<id>/annotations`. Das rohe Log ist von außen oft nicht erreichbar.

## Stolperfallen, die schon einmal passiert sind

- Der alte Dienst hielt Session und Puffer nur im RAM: nach einem Neustart durch das System war die Session 0 und jeder Punkt
  wurde verworfen. Zustand gehört auf Platte (`RideState`, `ride.db`).
- `HttpURLConnection` folgt Weiterleitungen: ein Upload ohne Tester-Cookie landete auf `/access`, bekam 200 und galt als
  gesendet. `Http.postJson` folgt keinen Weiterleitungen.
- Netzwerk im `onDestroy` läuft auf dem Main-Thread und scheitert immer – auf den Dienst-Thread legen.
- Die Wichtigkeit eines bestehenden Benachrichtigungskanals lässt sich nicht erhöhen → neue Kanal-ID (`eltouro_ride_v2`).
- Android 12+: Foreground-Service aus dem Hintergrund (Neustart, Watchdog, Booten) nur mit Akku-Ausnahme; Typ Standort
  zusätzlich nur mit „Immer zulassen“. `startForeground` immer in try/catch und protokollieren.
- Kotlin 2.2: `kotlinOptions {}` ist ein Fehler, `kotlin { compilerOptions { jvmTarget… } }` nehmen (JVM 21 wie Capacitor).
- `capacitor.build.gradle` und `capacitor.settings.gradle` erzeugt `npx cap sync` neu – nicht von Hand ändern.
- `versionCode` = 1000 + Laufnummer der CI: die Laufnummern fingen im neuen Repo wieder bei 1 an, die alten APKs aus dem
  Web-Repo hatten höhere – ohne Versatz lehnt Android das Update als Downgrade ab.
- Das rohe Job-Log ist nicht aus jeder Umgebung erreichbar; Kotlin-Fehler/-Warnungen und die Testzahl stehen deshalb als
  Annotations am Lauf (Check-Runs-API).
