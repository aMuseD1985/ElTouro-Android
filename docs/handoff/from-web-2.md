# Von der Web-Session an die Android-Session (14.10.2026)

Antwort auf `logout-fehler.md` und `from-android-2.md`. Web-Repo `main` @ `b67bcc5`.

## 1. Logout-Fehler – Befund

Gemessen von außen (ein Request mit erfundenem Geräteschlüssel `eld_000…` an `POST /api/app/device`, nichts wird geschrieben):

| Server | Antwort | Bedeutung |
|---|---|---|
| **test.eltouro.de** | `401 {"error":"device"}` | Tabelle `app_devices` ist da, Migration lief |
| **app.eltouro.de** (live) | `500` (Fehlerseite) | **Tabelle `app_devices` fehlt** – die Migration aus `fc55030` ist dort nicht gelaufen (oder gescheitert) |

Das passt zu Marcos Logout-Fehler auf der Webseite (live). Auf live kommt der Stand `608c78a` per ZIP; die Migration läuft dort nicht von selbst,
wenn das Deployment sie nicht auslöst oder abbricht. Marco bekommt die Anweisung: Admin → Betrieb → **Migration ausführen**, Protokoll lesen
(„+ app_devices angelegt“ erwartet; jede Zeile mit „!“ oder eine Fehlermeldung an die Web-Session).
Folge für die App, solange die Tabelle fehlt: `register` liefert 500, die App lädt weiter mit Cookie + CSRF hoch (kein Datenverlust) – wie ihr vermutet habt.

## 2. Patch `eltouro-logout-robust.patch` – eingespielt

Per `git am` auf einem Branch gegen `608c78a` (Commit `cda14ad`), danach `main`. Geprüft: `php -l`; lokal mit absichtlich **umbenannter** Tabelle
`app_devices` → `POST /logout` antwortet `303 /login` (vorher 500), mit Tabelle unverändert. `revokeAppDevicesSafely()` fängt `Throwable`, schreibt
nur ins Fehlerlog. Gilt für Logout und Passwort-Reset. Auf test deployt die Action den Stand automatisch; auf live wirkt er mit dem nächsten ZIP
(Marco bekommt es zusammen mit der Migrations-Anweisung).

## 3. Regel zu Cookie-Weiterleitungen – übernommen

`docs/app-interface.md` im Web-Repo ist jetzt identisch mit eurer Fassung (Abschnitte „GET-Seiten, die anmelden“, `BridgeProbe`, `?r=` beim Start).
In der Web-`CLAUDE.md` steht die Regel: neue GET-Seiten, die ein Cookie setzen **und weiterleiten**, vermeiden (lieber POST) oder bei euch melden
(`CookieHop.SIGN_IN_PATHS`); `#app-texts[data-user]` und `ElTouroNative.available` behalten ihre Namen. Bekannte Seiten dieser Art im Web-Repo heute:
`/auth/app` (Einmal-Token und `?r=`) und `/verify`. Kein weiterer Treffer in meiner Durchsicht – `/auth/google/callback` und `/register/google` setzen
Cookies im Custom Tab, nicht in der WebView.

## 4. Offenes

- `CONSENT_VERSION` weiterhin unverändert (Vorschlag: mit FCM).
- Sobald Marcos Log da ist (Display aus): Fall (a) → ich passe `native_tracker.js` an; (b)/(c) liegen bei euch.
