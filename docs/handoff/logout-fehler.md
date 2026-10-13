# Logout scheitert mit „Da ist etwas schiefgelaufen“ – von der Android-Session (14.10.2026)

**Befund (Marco):** Abmelden auf der Webseite geht nicht, Fehlerseite „Da ist etwas schiefgelaufen … Referenz: 9116eaba“.

**Ursache:** Seit dem Geräteschlüssel-Patch (`fc55030`) ruft `logout.php` `revokeAppDevices()` auf. Fehlt die Tabelle
`app_devices` (Migration auf diesem Server nicht gelaufen oder dort gescheitert), wirft das eine PDOException und der Logout endet
im Fehlerhandler. Lokal mit `main` @ `608c78a` nachgestellt: mit Tabelle → 303 `/login`; ohne Tabelle → 500, Log
`ElTouro [<ref>] PDOException: … Table '…app_devices' doesn't exist`. Die Referenz 9116eaba steht genauso im PHP-Fehlerlog des Servers.

**Sofort (Marco):** Auf dem betroffenen Server die Migration ausführen – Admin → Betrieb → Migration (oder `/migrate.php?key=…`).
Im Protokoll muss „+ app_devices angelegt“ stehen; steht dort eine Zeile mit „!“ (z. B. Fremdschlüssel), bitte an die Web-Session.
Auf test.eltouro.de führt die GitHub Action die Migration aus – dort ggf. das Log des letzten Deploy-Laufs prüfen.

**Dauerhaft (Web-Session):** `eltouro-logout-robust.patch` (ein Commit gegen `608c78a`): `revokeAppDevicesSafely()` in
`app_device_lib.php` fängt den Fehler ab und schreibt ihn nur ins Log; `logout.php` und `password_reset.php` benutzen sie.
Lokal geprüft: ohne Tabelle Logout 303 + Logzeile, mit Tabelle unverändert. `php -l` sauber.

Hat mit dem Login-Problem in der App nichts zu tun (`logIn()` und `/auth/app` fassen `app_devices` nicht an). Ohne die Tabelle
registriert die App keinen Geräteschlüssel (`/api/app/device` → 500) und lädt weiter mit Cookie + CSRF hoch – kein Datenverlust.
