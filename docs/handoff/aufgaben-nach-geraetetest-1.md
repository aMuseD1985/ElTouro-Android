# Aufgabenliste nach dem ersten Gerätetest (13.10.2026, abends) – von der Web-Repo-Session, im Auftrag von Marco

**Priorität:** Die App muss sich wie eine App verhalten. Marco hat die APK (Lauf `38455230038`, gegen `https://app.eltouro.de`,
Xiaomi 15 Ultra, HyperOS) installiert. Server-Stand live = `608c78a` (inkl. Geräteschlüssel-Patch; `/api/app/device`, `track.php` mit
`eld_…` laufen live, `native_tracker.js` v3 ist drauf).

## Beobachtungen von Marco (wörtlich zusammengefasst)

1. **„Fühlt sich genauso an wie die Web-App / installierte PWA.“** Bei **Display aus wird nicht getrackt**; die aufgezeichnete Linie
   ist **kerzengerade**, wenn zwischendurch der Bildschirm aus war (zwei Punkte vor/nach der Pause, dazwischen nichts). Das passierte
   auch mit der APK dieser Version – also **nicht** nur mit dem alten Java-Dienst.
2. **Der Login macht wieder Probleme** (derselbe Eindruck wie vor dem Cookie-/Token-Fix): nach Google-Login/Neustart nicht verlässlich angemeldet.
3. Marco hat das Diagnose-Log noch **nicht** geschickt (kommt nach seinem nächsten Test) – nichts davon ist also durch Log belegt.

## Was ich im Code gesehen habe (ungeprüft auf dem Gerät – bitte verifizieren)

**Verdacht Login (konkret):** `MainActivity.kt` verlässt sich darauf, dass `BridgeActivity.load()` den Start-Intent an
`onNewIntent` → `openLink` gibt (Kommentar in `onCreate`). Das stimmt nicht: `BridgeActivity.load()` ruft `bridge.onNewIntent(intent)` (Plugins),
**nicht** `Activity.onNewIntent`. In der Web-Repo-Fassung (Java) stand deshalb ausdrücklich `openLink(getIntent())` am Ende von `onCreate`.
Auf HyperOS wird die App während der Google-Anmeldung im Custom Tab sehr oft **beendet** – dann startet `de.eltouro.app://login?t=…` die App
**kalt**, `onNewIntent` kommt nie, der Token wird nicht eingelöst, die WebView bleibt abgemeldet. Genau das Bild von Marco.
→ Aufgabe 1.

**Verdacht Aufzeichnung:** noch offen, drei Möglichkeiten, die sich über das Log trennen lassen:
(a) Die Seite benutzt den nativen Pfad gar nicht (`ElTouroNative.available()` falsch → Browser-Pfad, der bei Display aus pausiert wird),
(b) der Dienst läuft, bekommt aber bei Display aus keine Fixes (HyperOS drosselt GPS/Fused, fehlende „Immer“-Berechtigung, Standort-Sparmodus),
(c) Fixes kommen, aber Punkte werden nicht gespeichert/hochgeladen (Session = 0, 401/403, Weiterleitung).
Die Diagnose-Ansicht muss diese drei sofort unterscheidbar machen (Aufgabe 2).

## Aufgaben in dieser Reihenfolge

**1. Login-Übergabe auch bei Kaltstart** (kleinster Fix, größter Effekt)
- In `MainActivity.onCreate` nach `super.onCreate`: `openLink(intent)` aufrufen (WebView per `post`, da die Bridge dann geladen ist) – nicht auf `onNewIntent` verlassen.
- `launchMode=singleTask` bleibt; prüfen, dass `onNewIntent` bei laufender App weiterhin greift (nicht doppelt ausführen: gleichen `t` nur einmal einlösen).
- Alles protokollieren (`Diag`): Intent empfangen (Schema/Host, **ohne** Token), Kalt-/Warmstart, `loadUrl` abgesetzt, Seitenende der Übergabe
  (`/auth/app` antwortet 303 → `next`). Die Web-Seite hat danach den Remember-Token geholt: `getRemember()` ≠ leer prüfen und im Diagnose-Zustand zeigen
  („angemeldet bleiben: ✓ / ✗, Geräteschlüssel: ✓ / ✗ mit Präfix“).
- Test: App per Wischen beenden, Custom Tab (Google) abschließen, App startet kalt → muss angemeldet sein; App neu starten → muss angemeldet bleiben.

**2. Diagnose belegt den Pfad** (vor jeder weiteren Aufzeichnungs-Arbeit)
- Diagnose-Ansicht ergänzen um: „Seite nutzt die App (start() empfangen: Zeit)“, „Session gesetzt: ID/Zeit“, „Fixes von GPS: n in den letzten 5 min, **letzter Fix: vor X s**, mit Display an/aus getrennt gezählt“, „Punkte gespeichert / hochgeladen / verworfen“, „Dienst seit …, Neustarts“.
- Beim Fahrtstart eine Zeile `page: start(session=…) native=true/false` ins Log; wenn die Seite den Browser-Pfad nimmt, soll das in der Diagnose **rot** stehen (Marco merkt es dann sofort).
- Marco schickt danach das Log; erst damit entscheidet sich (a)/(b)/(c).

**3. Wenn (b) „keine Fixes bei Display aus“:**
- Berechtigung „Immer zulassen“ erzwingen und prüfen (`ACCESS_BACKGROUND_LOCATION` **vor** Fahrtstart, nicht nur als Hinweis); Genauigkeit „genau“.
- Zweite Quelle als Rückfall: `LocationManager` mit `GPS_PROVIDER` parallel zu Fused, wenn Fused bei Display aus > 20 s still ist; Log, welche Quelle liefert.
- HyperOS: „Akku: Keine Einschränkungen“, Autostart, App im Task-Manager sperren, **„Standortgenauigkeit/GPS-Sparmodus aus“** – Direktlinks/Anleitung prüfen und im Diagnose-Bildschirm abfragen, soweit abfragbar.
- Der Dienst soll beim Display-aus-Zustand `PARTIAL_WAKE_LOCK` halten (ist da) **und** den Fix-Takt überwachen (Watchdog „keine Fixes seit 60 s“ → Log + Benachrichtigung „GPS liefert nichts“).

**4. Wenn (c) „Fixes kommen, Punkte fehlen“:** Upload-/Session-Pfad prüfen (`RideState.session` nach `setSession`, Zeitpunkt, Persistenz; 401/403/302; Reihenfolge `finish` ↔ letzter Upload).

**5. Wenn (a) „Seite nimmt Browser-Pfad“:** `ElTouroNative.available()`/`Capacitor.isNativePlatform()`/Bridge-Injektion für die Remote-URL prüfen (Reihenfolge der Skripte,
`window.Capacitor` zur Laufzeit), ggf. die Seite (`assets/native_tracker.js`) anpassen – **als Patch an die Web-Session**, nicht selbst im Web-Repo schreiben.

**6. App-Gefühl (kleiner):** Wie eine App starten (kein Browser-Look: Statusleiste/Navigationsleiste Farben, Splash, keine Adressleiste – WebView ist bereits randlos prüfen),
Zurück-Taste, Wiederaufnahme der letzten Seite, Offline-Seite.

**7. Danach M3 (nativer Fahrmodus)** – erst, wenn 1–5 am Gerät grün sind. Ohne M3 gibt es bei Display aus keine Sprachansagen (Navigationslogik läuft in der WebView).

## Wie Marco prüft (bitte als Testliste in `docs/android.md` pflegen)
1. App-Kaltstart-Login: App wischen → Google-Login → zurück in die App → angemeldet? → App wischen → öffnen → noch angemeldet?
2. 10 min Fahrt „Freie Fahrt“ mit Aufzeichnung, **Display aus**, Handy in der Tasche → `/drives` → Linie hat Kurven?
3. „Diagnose → Log teilen“ an Marco/Web-Session schicken.

## Zugriff
Diese Notiz liegt im Android-Repo; Server-Änderungen weiterhin als Patch unter `docs/handoff/` (die Web-Session prüft und spielt ein).
