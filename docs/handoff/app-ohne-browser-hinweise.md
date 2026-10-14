# In der App: keine „Display an lassen“-Hinweise, Rechtliches ins Profil (Android-Session, 14.10.2026)

**Marco:** „Die Hinweise auf Display an lassen bitte in der App raus lassen – ebenso wie Impressum, Datenschutz etc. Pack das unter
‚§§ Rechtliches‘ unten ins Profil, damit es da ist.“

**Patch:** `eltouro-app-ohne-browser-hinweise.patch` (ein Commit gegen `main` @ `163ba1b`, `git am`).
- `isApp()` in `core.php`: User-Agent enthält `ElTouroApp/` – sendet die App ab APK `versionCode` 1011
  (`appendUserAgent` in `capacitor.config.js`). Ältere APKs: `app-pref.js` (läuft im `<head>`) setzt `is-app` auf `<html>`, wenn
  `Capacitor.isNativePlatform()`; `.is-app .web-only { display: none !important }` in `style.css`. `<html class="is-app">` setzt der
  Server selbst, wenn er die App erkennt.
- `free.php`: „📵 Display nicht ausschalten …“ (`nav.keep_open`) und `nav.screen_hint` nur im Browser.
- `tour_go.php`: `#ride-keepopen-warn` bleibt als Element (navigate.js schaltet es), ist aber `web-only` und in der App leer;
  `nav.screen_hint` in `#ride-hints` nur im Browser. Akku-Knopf, Aufzeichnungs- und Live-Hinweis bleiben.
- `pageFooter()`: in der App für angemeldete Fahrer weggelassen (und `web-only` für alte APKs). **Abgemeldet bleibt die Fußzeile**,
  damit Impressum und Datenschutz ohne Konto erreichbar sind (Login, Registrierung) – das Profil gibt es dort ja nicht.
- `profile.php`: neuer letzter Abschnitt „§§ Rechtliches“ (`#legal`: Impressum, Datenschutz, Nutzungsbedingungen, dazu
  „Etwas fehlt oder klemmt? Feedback“, weil der Feedback-Link der Fußzeile in der App sonst fehlt) – auch im Web.
- Neue Texte `profile.legal_title`, `profile.legal_feedback` (DE+EN); `app-pref.js` v3, `style.css` v81.

**Lokal geprüft** (MariaDB, angemeldet/abgemeldet, App- und Browser-User-Agent): Browser angemeldet → Hinweise + Fußzeile da;
App angemeldet → keine Hinweise, keine Fußzeile, `is-app`; Profil → „§§ Rechtliches“ in beiden; App abgemeldet `/login` →
Fußzeile da. `tour_go.php` nur per `php -l` (keine Tour in der Test-DB). `php -l` sauber.

Bitte in `docs/app-interface.md` (beide Repos) übernehmen: „Die App sendet `ElTouroApp/<n>` im User-Agent; Browser-Hinweise und die
Fußzeile angemeldeter Fahrer markiert die Seite mit `web-only`.“
