# Arbeitspaket: ElTouro als native Android-App (Prompt für Claude Code)

> **Seit 13.10.2026 eigenes Repo (`aMuseD1985/ElTouro-Android`).** Pfade wie `android/…` unten meinen jetzt dieses Repo
> (`android/` = Gradle-Projekt), alles andere (`assets/navigate.js`, `voice_lib.php`, `push_lib.php`, `legal_texts.php`,
> `CLAUDE.md` der Web-App …) liegt im Web-Repo `aMuseD1985/ElTouro`. Server-Änderungen gehen als Patch dorthin, Marco prüft
> und spielt sie ein. Stand der Meilensteine: `CLAUDE.md` in diesem Repo.

Lies zuerst `CLAUDE.md` (Projektregeln, Aufbau, Stolperfallen) und `docs/android.md`. Kommunikation mit Marco auf Deutsch, direkt und technisch.

## Ausgangslage

ElTouro ist eine PHP-Web-App (PWA) für E-Scooter-Fahrer (Touren planen, Fahrmodus mit Sprachansagen, Aufzeichnen, Live-Karte, Ausfahrten, Herden mit Chat/Stammtisch/Fotowand, Forum). Backend: Plain PHP + MariaDB, REST-API `/api/v1` (Bearer-Token), Session-Cookie-Login, Google-Login. Es gibt bereits eine **Capacitor-Hülle** in `android/` (WebView lädt `https://app.eltouro.de` bzw. test; natives Plugin `EltouroTracker` mit Foreground-Service `TrackerService`; Google-Login über Chrome Custom Tab + Token-Übergabe `de.eltouro.app://login`; eigener „stay signed in“-Token). Die APK baut die GitHub Action `.github/workflows/android-apk.yml` (kein Android Studio auf Marcos Mac; Gradle/SDK nur in der CI).

## Das Problem, das als Erstes gelöst sein muss (Test: Xiaomi 15 Ultra, HyperOS)

Bei ausgeschaltetem Display **läuft nichts weiter**: keine Sprachansagen, und in der aufgezeichneten Fahrt klafft eine Lücke (die Linie an der Stelle ist nur die geplante Route, keine echten Punkte). Ursachen sind nicht gesichert. Vermutungen, die du prüfen musst:
1. Die Navigationslogik (`assets/navigate.js`: Abbilden auf die Strecke, Ansagen nach Zeit, Sprüche, Clips über `assets/voice.js`) läuft in der WebView – die wird bei Display aus pausiert/gedrosselt. → Die Navigation muss **nativ im Service** laufen.
2. Der Foreground-Service (Fused Location, `FOREGROUND_SERVICE_LOCATION`) wird von HyperOS gedrosselt/getötet (Autostart, Akku-Sparmodus „Intelligent“, fehlende `ACCESS_BACKGROUND_LOCATION`, Standortgenauigkeit, kein Auto-Start-Recht), oder der Dienst startet gar nicht (Berechtigung, Plugin-Aufruf scheitert still).
3. Der native Upload (`TrackerService.flushNow()` → `/api/track`, Cookie aus `CookieManager`, `X-CSRF`) scheitert still (Cookie/CSRF/Session, Funkloch-Puffer geht verloren).
Ohne Diagnose rät man nur – **baue zuerst eine Diagnose**: In-App-Bildschirm „Diagnose“ (Service läuft seit …, letzter Fix, Fixes/min, Puffergröße, letzter Upload mit HTTP-Status, Wakelock/Standby-Bucket/Akku-Optimierung, Berechtigungsstatus) und ein exportierbares Log (Teilen als Textdatei). Marco testet mit dem Gerät und schickt das Log.

## Zielbild

Eine **native Android-App** (Kotlin), die mit dem bestehenden Backend arbeitet, Google-Login und alle Gerätefunktionen nutzt und auch bei Display aus zuverlässig funktioniert. Architektur-Empfehlung (du darfst begründet abweichen): **Hybrid mit nativem Kern** – Inhaltsseiten bleiben die Web-App im WebView (Single Source of Truth, kein Doppelaufwand), aber alles, was Hintergrund/Sensoren/System braucht, wird nativ:

1. **Nativer Fahrmodus-Dienst** (höchste Priorität): Kotlin-Port der Navigations-Engine aus `assets/navigate.js` – Abbilden auf die Strecke (Regeln: Sprünge nach vorn höchstens 300 m + 8 m/s × Zeit, rückwärts bis 1,5 km, bei Mehrdeutigkeit der Ort nächst dem letzten Treffer), Abbiegehinweise aus `tours.guidance_json`, Pausen, Halbzeit/Ziel, Abweichung/Rückführung (`/api/route`), Tempo-/Landschafts-Sprüche, Geschichten. **Sprache:** fertige Clips (`/assets/voice/<lang>/manifest.json`, Clip-ID = erste 12 Zeichen von sha1(Text), Zusammensetzen aus Teilen wie in `voice_lib.php`/`voice.js`), Fallback `TextToSpeech`; Wiedergabe über Foreground-Service mit Audio-Fokus (Media3/ExoPlayer), Ducking von Musik. Test-Orakel: die Debug-Hooks in `navigate.js` (`?debug=1`, `window.__nav`) und `voicePhrases()` – Verhalten muss 1:1 gleich sein.
2. **Aufzeichnung**: Punkte lokal in Room puffern (nichts geht bei Funkloch verloren), robust hochladen (`/api/track` start/points/finish/resume, Format wie `TrackerService`), Privatzonen/Regeln wie die Web-App. Freie Fahrt (`/free`) und Fahrmodus.
3. **Live teilen**: WebSocket-Client zum Live-Server (`assets/live-server/`, Token von `GET /api/live/token`), Rückfall `/api/live`.
4. **Push (FCM)**: statt Web-Push in der App. Server: Tabelle für FCM-Tokens, Versand über FCM HTTP v1 (Service-Account in `config.php`), Anbindung an die bestehende Warteschlange `push_queue`/`push_lib.php` (gleiche Arten/Einstellungen wie Web-Push), Deep-Links in die passende Seite. Datenschutzerklärung (`legal_texts.php`, DE+EN, Marker in `legalMarkers()` + Hash in `$earlierDefaults`) ergänzen, `CONSENT_VERSION` erhöhen, Export/Löschung (`account_data_lib.php`) mitziehen – siehe CLAUDE.md. **Marco muss das Firebase-Projekt anlegen und `google-services.json` + Service-Account liefern – sag ihm genau, was er klicken muss; bis dahin Code so bauen, dass die App ohne Firebase kompiliert.**
5. **Kamera/Fotos**: Profilfoto (bestehender Editor `assets/avatar_editor.js`) und Fotowand (`/crew/<slug>/photos`) – Foto aufnehmen/auswählen (Android-Foto-Picker, `CAMERA` nur bei Bedarf), Upload über die vorhandenen Endpunkte (der Server zeichnet neu und entfernt EXIF). Datei-Eingaben in der WebView müssen Kamera/Galerie öffnen können (`WebChromeClient.onShowFileChooser`).
6. **Sensoren/System**: Standort inkl. Hintergrund (korrekte Berechtigungsabfolge Android 10–15, Begründungsdialog), Benachrichtigungen, Teilen-Intent (Tour/Ausfahrt teilen), Deep-Links (`app.eltouro.de/...`, App Links inkl. `assetlinks.json`-Vorlage), Akku-Optimierung-Hinweis, Wakelock, Bildschirm an halten im Fahrmodus.
7. **Widgets** (Jetpack Glance): „Nächste Ausfahrt“ (Countdown), „Ausfahrten in der Nähe“, „Fahrt starten/fortsetzen“. Daten über `/api/v1` (Token pro Gerät, siehe `api_lib.php`; ggf. einen schlanken Endpunkt `/api/v1/me/summary` ergänzen) und periodisch (WorkManager); Hintergrund-Prüfung auf neue Ausfahrten, auch ohne Navigation.
8. **Anmeldung**: bestehenden Ablauf behalten (E-Mail/Passwort in der WebView; Google über Custom Tab + `de.eltouro.app://login?t=` + `/auth/app`; „stay signed in“-Token `app_remember`). Verbesserungen erlaubt (z. B. Credential Manager), aber die Sicherheitsregeln aus CLAUDE.md gelten (Tokens nur als Hash in der DB, CSRF, kein Passwort im Klartext).

Ob einzelne Bildschirme (Start, Ausfahrten, Chat) zusätzlich komplett nativ in Compose gebaut werden, entscheidest du nach Aufwand/Nutzen – **erst wenn 1–7 stabil sind**. Kein Redesign der Web-Oberfläche.

## Xiaomi/HyperOS-Härtung (Pflicht)

Foreground-Service mit korrektem Typ und sichtbarer Benachrichtigung (hohe Wichtigkeit, Kategorie Navigation), `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`/Direktdialog, Hinweis- und Direktlinks zu **Autostart**, „Akku: Keine Einschränkungen“, „App im Task-Manager sperren“, Standort „Immer erlauben“ + „Genauen Standort“; Erkennung des Herstellers (Xiaomi/Samsung/Huawei/OnePlus) mit passender Anleitung (https://dontkillmyapp.com-Inhalte selbst formulieren, keine Drittanbieter-Requests). Partieller Wakelock nur bei aktiver Fahrt, mit Timeout. Watchdog: stirbt der Service, startet ihn `WorkManager`/AlarmManager wieder und die Aufzeichnung wird fortgesetzt (`/api/track` `resume`). Eine Fahrt endet nur, wenn der Fahrer sie beendet.

## Rahmenbedingungen

- **Kein Android Studio, kein Gerät in deiner Umgebung**: baue/prüfe über GitHub Actions (`gh workflow run "Android APK" -R aMuseD1985/ElTouro-Android -f url=https://test.eltouro.de --ref <branch>`, danach `gh run download`). Schreibe Unit-Tests (JVM) für alles Testbare (Streckenabbildung, Ansagen-Zeitpunkte, Distanz-/Text-Zerlegung, Clip-IDs) und lasse sie in der CI laufen. Gib Marco am Ende eine **Testliste** mit genauen Schritten pro Gerätefunktion.
- Arbeite auf einem **Branch** `android-native` (kein Push auf `main` ohne Marcos Freigabe – jeder Push auf `main` deployt sofort auf test.eltouro.de). PHP-Änderungen klein halten, `php -l`, Texte in `lang.php` DE+EN, Rechtstexte wie oben, `CLAUDE.md` und `docs/android.md` aktualisieren.
- Signierung: fester Debug-Test-Schlüssel bleibt (Updates über die alte APK); Play Store später (eigener geheimer Upload-Schlüssel, Datenschutz-Angaben, Begründung für Hintergrund-Standort) – **jetzt nur APK**.
- Marke/Ton/Tonalität: siehe CLAUDE.md (duzen, kein Gendern, Touro trägt immer seine Sonnenbrille). Keine neuen Tracker/Drittanbieter außer Firebase Cloud Messaging für Push.
- `android/` wird nicht auf den Server deployt (ist bereits ausgeschlossen).

## Reihenfolge und Abnahme

M1 Diagnose + Härtung (Service-Zustand, Log, Berechtigungsfluss, Akku-Anleitungen) → M2 nativer Aufzeichnungspfad mit Room-Puffer → M3 nativer Fahrmodus-Dienst (Navigation + Sprache) → M4 Kamera/Datei-Auswahl, Teilen, Deep-Links → M5 FCM-Push (Server + App) → M6 Widgets + Hintergrund-Prüfung → M7 Live per WebSocket nativ. Pro Meilenstein: Commit, grüne CI, APK-Artefakt, kurze Testanleitung für Marco.

Abnahme (Marco, Xiaomi 15 Ultra): 30+ Minuten Fahrt mit **Display aus**: lückenlose Aufzeichnung unter `/drives`, Sprachansagen kommen pünktlich (mit Clips), Live-Position wird weitergeschickt, nach Tippen auf die Benachrichtigung ist alles wieder da; Neustart des Handys/App-Kill während der Fahrt → Fortsetzen ohne Datenverlust; Google-Login und Login bleiben nach Neustart; Push kommt an; Fotoaufnahme für Profil und Fotowand funktioniert.
