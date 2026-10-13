# Schnittstelle App ↔ Web-App

Gilt für beide Repos: `aMuseD1985/ElTouro-Android` (diese App) und `aMuseD1985/ElTouro` (Web-App, Server). Eine Kopie liegt
im Web-Repo; wer hier etwas ändert, ändert beide. **Grundregel: nur ergänzen, nie umbenennen oder entfernen.** Die APK lädt
die Web-App vom Server – alte APKs treffen auf neue Seiten und neue APKs auf alte Seiten. Neues wird per Feature-Erkennung
benutzt (Methode vorhanden? `status().version`?), nie vorausgesetzt.

## 1. Brücke Seite → App (Capacitor-Plugin `EltouroTracker`)

JS-Seite: `assets/native_tracker.js` im Web-Repo (`window.ElTouroNative`). Native Seite: `TrackerPlugin.kt` hier.

| Methode | Argumente | Seit | Zweck |
|---|---|---|---|
| `start` | `{baseUrl, csrf, session?, title, text}` | 0.1 | Berechtigungen, Fahrt-Dienst starten; `session` > 0 = Aufzeichnung |
| `setSession` | `{session, csrf}` | 0.1 | Aufzeichnung ein (Session des Servers) |
| `clearSession` | – | 0.1 | Aufzeichnung aus, Rest hochladen |
| `stop` | – | 0.1 | Rest hochladen, Dienst beenden, Fahrt nicht mehr aktiv |
| `batterySettings` | – | 0.1 | Akku-Optimierung (seit 0.2 direkter Systemdialog) |
| `openExternal` | `{url}` (nur `https://`) | 0.1 | Custom Tab, z. B. Google-Anmeldung |
| `getRemember` / `setRemember` / `clearRemember` | `{token}` (64 hex) | 0.1 | „Angemeldet bleiben“; `clearRemember` widerruft seit 0.3 auch den Geräteschlüssel |
| `log` | `{msg}` | 0.2 | Zeile ins Diagnose-Log (max. 60/min, 300 Zeichen) |
| `status` | – → `{preciseLocation, backgroundLocation, notifications, batteryUnrestricted, complete, serviceRunning, version}` | 0.2 | was für Fahrten mit Display aus fehlt (`version` = 2) |
| `diagnostics` | – | 0.2 | Bildschirm „Diagnose“ öffnen |

Ereignis `location`: `{lat, lng, acc, heading?, speedMs?, t}` (jede Position, solange die Seite lebt).

**GET-Seiten, die anmelden** (Sitzungs-Cookie in einer Weiterleitung): `/auth/app` und `/verify`. Die App öffnet sie selbst
(`CookieHop`) statt über die WebView, weil Capacitors Proxy das Cookie einer Weiterleitung verliert. Kommt im Web-Repo eine
weitere GET-Seite dazu, die Cookies setzt und weiterleitet, muss sie in `CookieHop.SIGN_IN_PATHS` (oder als POST gebaut werden).

Die App fragt außerdem jede geladene Seite per `evaluateJavascript` ab (`BridgeProbe` – Diagnose): `location.pathname`,
`window.ElTouroNative.available()`, `Capacitor.isNativePlatform()` und `document.getElementById('app-texts').dataset.user`
(`'1'` = angemeldet). Diese Namen bitte nicht ändern.

## 2. Endpunkte, die die App direkt ruft

| Endpunkt | Auth | Inhalt |
|---|---|---|
| `POST /api/track` | Geräteschlüssel (`Authorization: Bearer eld_…` **und** `X-Device-Token`) oder Session-Cookie + `X-CSRF` | `{"action":"points","session_id":n,"points":[[unixMs,lat,lng,acc,kmh\|null],…]}` → `{"stored":n}`; höchstens 600 Punkte (`TRACK_MAX_BATCH`) |
| `POST /api/app/device` | Session + `X-CSRF` | `{"action":"register","version":"…"}` → `{"token":"eld_…"}` |
| `POST /api/app/device` | Geräteschlüssel | `{"action":"check"}` → `{"ok":true}`; `{"action":"revoke"}` |
| `POST /api/app/remember` | Session + `X-CSRF` | → `{"token": 64 hex}` (holt die Seite, speichert die App) |
| `GET /auth/app?t=…&next=…` / `?r=…` | Einmal-Token / Remember-Token | meldet die WebView an; die App selbst ruft `?r=` beim Start, wenn das Cookie `eltouro_sid` fehlt |
| `GET /auth/app-return` | – | Ende der Google-Anmeldung im Browser → `de.eltouro.app://login?t=…&next=…` |

Antworten, auf die sich die App verlässt (`Uploader`): 2xx = angekommen; `404`/`409` = Session unbekannt/nicht mehr offen →
Punkte verwerfen; `401` = Schlüssel/Sitzung weg (mit Schlüssel: Rückfall auf Cookie, neuer Schlüssel); `400` CSRF, `403`
Einwilligung (`{"error":"consent"}`), `302` Zugangsschutz, 5xx → behalten, später erneut. **Weiterleitungen sind für die App
Fehler** – eine API-Antwort darf nie eine Seite sein. `{"stored":0}` bei Punkten = Aufzeichnung serverseitig schon beendet
(nach 2 h ohne Position, `finishStaleRecordings()`).

Anfragen mit Geräteschlüssel laufen ohne Tester-Cookie und ohne Session (`SKIP_ACCESS_GATE`, `NO_SESSION`), die Einwilligung
prüft `appDeviceAuthenticate()`.

## 3. Links und Hosts

App Links / Intent-Filter: `https://app.eltouro.de`, `test.eltouro.de`, `beta.eltouro.de` (andere Hosts öffnet die App nicht).
Eigenes Schema: `de.eltouro.app://login?t=<64 hex>&next=<Pfad>`. Google-Knopf in der App: die Seite hängt `?app=1` an und öffnet
ihn über `openExternal`.

## 4. Datenschutz

Jede Änderung, was die App speichert oder überträgt, braucht die Datenschutzerklärung im Web-Repo (`legal_texts.php`, Abschnitt
„Android-App“, DE+EN, Marker in `legalMarkers()`, Hash des alten Standardtexts in `$earlierDefaults`, ggf. `CONSENT_VERSION`,
Export/Löschung in `account_data_lib.php`).
