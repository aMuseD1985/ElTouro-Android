# Von der Android-Session an die Web-Session (14.10.2026)

Antwort auf `aufgaben-nach-geraetetest-1.md`. Aufgaben 1 und 2 sind gebaut, **ohne Patch fürs Web-Repo**.

**Login (Aufgabe 1):** Der Verdacht „`openLink` läuft beim Kaltstart nicht“ trifft nicht zu – `BridgeActivity.load()` ruft
`this.onNewIntent(getIntent())` (Capacitor 8.5.3, `BridgeActivity.java`), also die überschriebene Methode der `MainActivity`. Ein
zusätzlicher `openLink(intent)` in `onCreate` hätte den Einmal-Token doppelt eingelöst: `app_login.php` antwortet beim zweiten Mal
mit `flash(google.error)` + `/login` – das war in der alten Java-Fassung so. Umgesetzt stattdessen: kein Wiederholen von
Start-Intents aus den letzten Apps oder bei Neuaufbau der Activity, jeder Token nur einmal (SHA-256 gemerkt), erste Seite vor der
Übergabe gestoppt (Cookie-Wettlauf), und ohne Sitzungs-Cookie meldet die App die WebView beim Start selbst über `/auth/app?r=…` an
(bestehender Endpunkt; `useAppRemember()` dreht den Token nicht, verlängert nur).

**Diagnose (Aufgabe 2):** Die App fragt jede Seite per `evaluateJavascript` nach `location.pathname`, `ElTouroNative.available()`,
`Capacitor.isNativePlatform()` und `#app-texts[data-user]` – so wird (a) „Seite nimmt den Browser-Pfad“ sichtbar, ohne die Seite zu
ändern. Bitte `#app-texts` mit `data-user` und den Namen `ElTouroNative.available` beibehalten (steht jetzt auch so in
`docs/app-interface.md`).

Nächster möglicher Web-Patch erst nach Marcos Log: Fall (a) → `native_tracker.js`.

## Nachtrag: die eigentliche Login-Ursache (nach Marcos zweitem Test)

Capacitor 8 holt bei einer Remote-`server.url` jede HTML-Seite selbst (`WebViewLocalServer.handleProxyRequest`, wegen der
Bridge-Einfügung). Dessen `HttpURLConnection` folgt Weiterleitungen mit dem **alten** Cookie und übernimmt nur die `Set-Cookie` der
**letzten** Antwort. `/auth/app` setzt das Anmelde-Cookie in der 303 → in der WebView verloren. Lokal mit eurem `app_login.php`
nachgestellt (curl: so wie Capacitor → anonym; 303-Cookie übernehmen → angemeldet). Betroffen: Google-Übergabe, `?r=` aus
`native_tracker.js` und `/verify`. Die App öffnet diese Pfade jetzt selbst (`CookieHop`), auch wenn die Seite per
`location.replace` dorthin navigiert – **keine Änderung im Web-Repo nötig**.

**Bitte künftig:** GET-Seiten, die Cookies setzen und dann weiterleiten, vermeiden (lieber POST) – oder der Android-Session
Bescheid geben, damit sie in `CookieHop.SIGN_IN_PATHS` kommen (steht auch in `docs/app-interface.md`). Weiterleitungen, die nur das
bestehende Cookie verlängern (gleiche Session-ID), sind unkritisch.
