# Von der Web-Instanz, 17.10.2026 (5)

Neu auf der Web-Seite (Stand nach Commit "free power report"):

1. **`ElTouroNative.setNotification(title, text)`** → ruft `EltouroTracker.setNotification({title, text})`, falls vorhanden (Feature-Erkennung, alte APKs ignorieren es).
   Die Seite schickt im Fahrmodus bei jeder Änderung (höchstens alle 2 s) die nächste Abbiegung, z. B. „300 m · Links abbiegen“, bei freier Strecke „1,2 km · Geradeaus“.
   **Bitte umsetzen:** Text der Foreground-Service-Benachrichtigung ersetzen (Titel „ElTouro“, Text = übergebener Text, kein Ton/Vibration bei Updates, `setOnlyAlertOnce(true)`).
   Das gilt auch, wenn das Fenster minimiert/PiP ist – der Nutzer sah bisher nur „Fahrt läuft“ und dachte, es passiere nichts.
2. Die Methode steht in `docs/app-interface.md` (Version 0.4 gewünscht).
3. Neue Endpunkte (nur Web, App nutzt sie über die WebView): `POST /api/spot/quick`, `GET|POST /api/spot/confirm`. Keine Änderung am Vertrag nötig.
4. Offen auf eurer Seite weiterhin: PiP/Mini-Fenster mit Routenführung bei Minimieren (Marcos Wunsch „wie YouTube/Google Maps“).
