# Übergabe an die Web-Repo-Session: Android-App ist umgezogen, Server-Teil von M2

Von der Android-Session, 13.10.2026. Liegt im (privaten) Repo `aMuseD1985/ElTouro-Android` unter `docs/handoff/`.
Holen mit Marcos Zugang, z. B.:
`gh api repos/aMuseD1985/ElTouro-Android/contents/docs/handoff/eltouro-server-device-key.patch -H "Accept: application/vnd.github.raw" > device-key.patch`
Einspielen auf einem Branch: `git am device-key.patch`, prüfen (`php -l`, Sprachdateien), dann wie gewohnt auf `main`.
Weitere Dokumente dort: `docs/app-interface.md` (Vertrag), `docs/plan.md` (Entscheidungen, nächste Server-Teile), `CLAUDE.md`.

## 1. Die App lebt jetzt in `aMuseD1985/ElTouro-Android`

Mit Historie aus `android/` (git subtree split), dazu CI, Doku und eine eigene `CLAUDE.md`. Im Web-Repo kann deshalb weg:
`android/`, `.github/workflows/android-apk.yml`, `docs/android.md` und `docs/android-native-prompt.md` (oder durch einen Verweis
ersetzen). Die Sperren für `android/` in `.htaccess`, `.gitattributes` und im Deploy-Workflow schaden nicht, können aber mit.
In der `CLAUDE.md` des Web-Repos die Zeile „Android-App“ kürzen: Verweis auf das neue Repo und auf `docs/app-interface.md`.
Der Branch `android-native` wurde nie gepusht – im Web-Repo ist nichts aufzuräumen.

**Bleibt im Web-Repo:** `assets/native_tracker.js` (JS-Seite der Brücke) und alle Endpunkte, die die App ruft.
**Neu in beiden Repos:** `docs/app-interface.md` – Vertrag zwischen App und Web-App (Plugin-Methoden, Endpunkte, Antworten, auf
die sich der Upload verlässt, Hosts, Datenschutz-Regel). Grundregel: nur ergänzen, nie ändern. Beide Kopien gleich halten.

## 2. Patch `docs/handoff/eltouro-server-device-key.patch` (ein Commit gegen `main` @ `fa48544`)

Geräteschlüssel `eld_…` für den Hintergrund-Upload der App:
- Tabelle `app_devices` (Migration am Ende von `runMigrations()`): nur SHA-256, Präfix, App-Version, angelegt/zuletzt benutzt
  (höchstens alle 5 min geschrieben)/widerrufen; FK `ON DELETE CASCADE`; höchstens 5 Schlüssel pro Fahrer.
- `app_device_lib.php`, `app_device.php` (`POST /api/app/device`, Regel in `.htaccess`): `register` mit Session + `X-CSRF`,
  `check`/`revoke` mit dem Schlüssel (`Authorization: Bearer eld_…` oder `X-Device-Token`).
- `track.php`: mit Schlüssel `SKIP_ACCESS_GATE` + `NO_SESSION` (wie `api.php`), Einwilligung geprüft (403 `consent`), kein CSRF.
  Ohne Schlüssel unverändert.
- Widerruf bei Logout und Passwort-Reset. **Verhaltensänderung:** der Reset widerruft jetzt auch die App-Remember-Tokens.
- Export: `app_devices` (ohne Hash). Löschung über die FK.
- Datenschutzerklärung DE+EN: Absatz „Android-App“ nach „Fahrmodus“, Marker in `legalMarkers()`, Hashes der bisherigen
  Standardtexte in `$earlierDefaults`. **`CONSENT_VERSION` bewusst nicht erhöht** – Vorschlag: einmal zusammen mit FCM (M5).
- `docs/app-interface.md`.

**Achtung beim Einspielen:** Die Hashes in `$earlierDefaults` gelten für `legal_texts.php` auf Stand `fa48544`. Wurde die
Datenschutzerklärung seitdem geändert, vor `git am` neu berechnen und im Patch ersetzen:
`php -r '$t=require "legal_texts.php"; echo hash("sha256",$t["privacy"]["de"]),"\n",hash("sha256",$t["privacy"]["en"]),"\n";'`

Lokal gegen MariaDB getestet: Migration; Upload mit Schlüssel bei aktivem Zugangsschutz 200; nur `X-Device-Token` 200;
unbekannte Session 404; nach `finish` `{"stored":0}`; falscher/widerrufener Schlüssel 401; alte Einwilligung 403; ohne Schlüssel
weiter 302 auf `/access`; Anfragen mit Schlüssel legen keine Session-Datei an; Export und Cascade-Löschung. `php -l` sauber.

**Offen zur Entscheidung:** Nach 2 h ohne Position beendet `finishStaleRecordings()` die Aufzeichnung; später ankommende Punkte
(langes Funkloch) nimmt der Server mit `{"stored":0}` an und verwirft sie. Die App protokolliert das nur.

## 3. Was als Nächstes aus der Android-Session kommt

Pro Meilenstein ein Patch in `docs/handoff/` mit eigener Notiz. Absehbar: M3 – `/api/route` mit Geräteschlüssel, `navigate.js`
als reine Anzeige, wenn der native Fahrmodus läuft (Feature-Erkennung); M5 – FCM (`app_devices.fcm_token`, Versand über HTTP v1,
Datenschutz, `CONSENT_VERSION`); M6 – `/api/app/summary`; M7 – `/api/live/token` mit Geräteschlüssel. Bitte `native_tracker.js`
und die Endpunkte aus `docs/app-interface.md` nur ergänzen, nie ändern – sonst brechen installierte APKs.
