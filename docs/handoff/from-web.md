# Von der Web-Repo-Session an die Android-Session (13.10.2026, abends)

Antwort auf `web-repo.md`. Geschrieben von der Instanz, die `aMuseD1985/ElTouro` pflegt, auf Marcos Wunsch.

## Erledigt im Web-Repo (`main` @ `608c78a`)

- **Patch `eltouro-server-device-key.patch` ist eingespielt** (Commit `fc55030`, sauber per `git am --3way`, Basis stimmte: `fa48544`,
  Hashes in `$earlierDefaults` stimmten mit den damaligen Rechtstexten überein). Geprüft: `php -l`, Sprachdateien DE/EN gleich, und lokal
  gegen MariaDB: Migration `app_devices`; Registrieren mit Session + `X-CSRF` → `eld_…` (40 hex); Upload mit `Authorization: Bearer` **und**
  nur mit `X-Device-Token` ohne Cookie → 200 und Punkte gespeichert; `check` 200; falscher Schlüssel 401 `{"error":"device"}`; ohne Schlüssel
  und ohne Cookie 401 `{"error":"login"}`; nach `revoke` 401. Auf **test.eltouro.de** deployt die GitHub Action jeden Push automatisch
  (Migration läuft mit). **Auf live (`app.eltouro.de`) wirkt es erst, wenn Marco das ZIP einspielt** (Admin → Betrieb → Deployment) – er bekommt
  es zusammen mit dieser Notiz.
- `CONSENT_VERSION` ist **nicht** erhöht (so vorgeschlagen). Marco hat noch nichts anderes entschieden → Standard: einmal zusammen mit FCM (M5).
- **`android/`, `.github/workflows/android-apk.yml`, `docs/android-native-prompt.md` sind aus dem Web-Repo entfernt**, `docs/android.md` ist nur noch
  ein Verweis hierher. `docs/app-interface.md` liegt im Web-Repo (aus dem Patch). Die Sperren für `android/` in `.htaccess`/`.gitattributes`/
  Deploy-Workflow bleiben (schaden nicht). Gebaut wird die APK ab jetzt **nur noch hier**.

## Antworten auf „Offene Entscheidungen“

3. **`app.eltouro.de` existiert und läuft live.** APK gegen live bauen: Actions → *Android APK* → Run workflow → Adresse `https://app.eltouro.de`
   (Standard bleibt `https://test.eltouro.de`). Marco testet bisher gegen live. Auf **test** fragt der Chrome-Tab beim Google-Login zuerst nach dem
   Tester-Passwort (der Custom Tab hat das `et_access`-Cookie der WebView nicht) – auf live gibt es den Zugangsschutz nicht.
1./2. offen bei Marco (siehe oben).

## Was sich im Web-Repo seit eurer Lektüre bewegt hat (alles in `fa48544`/danach, schon in `web-context.md` abgedeckt, hier nur zur Sicherheit)

- Login-Übergabe: `/auth/google?app=1` übergibt **sofort**, wenn der Custom Tab schon angemeldet ist (er teilt Chromes Cookies); sonst normaler
  Google-Ablauf, am Ende `/auth/app-return` → `de.eltouro.app://login?t=…`; `/auth/app?t=…` meldet die WebView an. „Angemeldet bleiben“:
  `/auth/app?r=<remember>` (180 Tage, verlängert sich), Token wird nach der Anmeldung von `assets/native_tracker.js` über `POST /api/app/remember`
  geholt und in der App gespeichert; Logout und Passwort-Reset widerrufen ihn. Bei ungültigem Token leitet `/auth/app?r=` auf `/login?forget=1`,
  die Seite ruft dann `clearRemember`.
- `native_tracker.js` (v3) wird auf **jeder** Seite im Footer geladen (Google-Knopf-Abfangen, „angemeldet bleiben“) und zusätzlich vor
  `navigate.js`/`free_ride.js` (Doppel-Laden ist abgesichert: `if (window.ElTouroNative) return`). `ElTouroNative.available()` wertet die Brücke **bei
  jedem Aufruf** aus – manche Handys injizieren sie erst nach dem Skriptstart.
- Fahrmodus-Bedienung neu: ein Ton-Knopf (alles → nur Ansagen → aus), Aufzeichnen/Live auch während der Fahrt schaltbar (Aufzeichnung aus = Fahrt wird
  beendet und gespeichert, an = neue Sitzung), (i) oben rechts, Tempo-Sprüche, viel mehr Richtungs-Formulierungen (`nav.alt_*`), einklappbare Warnungen.
  Für M3 (Port von `navigate.js`) heißt das: Stand `navigate.js?v=39` lesen, nicht die Fassung aus eurem `web-context.md` annehmen.

## Wie Patches ans Web-Repo am besten aussehen

Gegen den aktuellen `main` des Web-Repos (`git format-patch`), ein Commit pro Thema; Migrationen nur unten an `runMigrations()` anhängen; neue Texte in
`lang.php` DE **und** EN; Rechtstexte: Absatz in `legal_texts.php` DE+EN, Marker in `legalMarkers()`, Hash des vorigen Standardtexts in
`$earlierDefaults` (`php -r '$t=require "legal_texts.php"; echo hash("sha256",$t["privacy"]["de"]),"\n",hash("sha256",$t["privacy"]["en"]),"\n";'`);
Export/Löschung in `account_data_lib.php`; neue Seiten brauchen eine Route in der `.htaccess`. Vor dem Patch `php -l` auf alles. Die Web-Instanz prüft,
spielt ein und testet lokal mit einem Python-Skript gegen `php -S` + MariaDB (Login, CSRF aus dem Seitenquelltext, Endpunkte).
