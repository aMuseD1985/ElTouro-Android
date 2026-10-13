# Von der Web-Session an die Android-Session (14.10.2026, nach Marcos Log von 00:46)

Marcos Diagnose-Log (APK 0.3.0 / 1008, Xiaomi 15 Ultra, live) zeigt es eindeutig:

```
page: /login bridge=no capacitor=yes tracker=yes user=no
```

`bridge=no` heißt: `ElTouroNative.available()` ist falsch, obwohl `Capacitor.isNativePlatform()` wahr ist und `ElTouroNative` existiert.
**Die Seite hat also nie den nativen Pfad benutzt** – das erklärt: Aufzeichnung wie in der PWA (Browser-Pfad, bei Display aus pausiert → kerzengerade Linie),
kein „angemeldet bleiben“ (`stay signed in=no`: `native_tracker.js` holt den Token nur bei `available()`), kein Geräteschlüssel, keine `start()`-Zeile im Log.
Das ist **Fall (a)** aus `aufgaben-nach-geraetetest-1.md`.

## Ursache (Web-Seite, von mir behoben)

`assets/native_tracker.js` holte das Plugin nur über `Capacitor.registerPlugin('EltouroTracker')`. Auf Seiten, die von einer Remote-`server.url` geladen werden,
injiziert die native Bridge (`native-bridge.js`) die Plugins unter **`Capacitor.Plugins.<Name>`**; `registerPlugin` (aus `@capacitor/core`) gehört nicht dazu.
Fix (Web-Repo `163ba1b`, `native_tracker.js?v=4`): zuerst `Capacitor.Plugins.EltouroTracker`, dann `registerPlugin` als Rückfall; `stayRemembered()` in try/catch.
Geprüft mit einer Attrappe in Node: nur `Plugins` → `available()` true; nur `registerPlugin` → true; Browser/Web-Plattform → false.
Live wirkt es erst mit Marcos nächstem ZIP; auf test automatisch.

## Was ich von euch erwarte

1. **Keine Änderung nötig** an der Brücke. Bitte in der Diagnose die Zeile `bridge=yes` nach dem Update bestätigen lassen und beim Fahrtstart die Zeile
   `page: start(session=…)` (kam bisher nie – ein weiterer Beleg für Fall (a)).
2. Für künftige Seiten: **nicht** auf `registerPlugin` setzen; `Capacitor.Plugins.EltouroTracker` ist der Weg auf Remote-Seiten.
3. Zur Anmeldung im Log: `session cookie=no` bei jedem Kaltstart nach Marcos eigenem Abmelden um 00:27 ist erwartbar (er hat sich abgemeldet);
   `stay signed in=no` war die Folge von (a). Erst mit `bridge=yes` lässt sich eure `?r=`-Logik am Gerät prüfen.
4. Sprachansagen mit Display aus bleiben M3.
