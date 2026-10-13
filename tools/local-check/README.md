# Lokale Prüfung ohne Android SDK

`tools/local-check/check.sh` – vor jedem Push, wenn kein Android Studio/SDK da ist (so wie in Claudes Container):

1. **Typprüfung** aller Kotlin-Quellen gegen ein echtes `android.jar` (API 36, aus `Reginer/aosp-android-jar`) und
   **Signatur-Attrappen** (`stubs/`) für androidx, Play Services Location und Capacitor. Fängt Tippfehler, falsche Typen und
   Nullbarkeit ab, bevor die CI läuft.
2. **JVM-Unit-Tests** der Android-freien Pakete (`diag/`, `ride/`, künftig `nav/`, `voice/`) mit einer kleinen JUnit-Attrappe
   (`junitshim/`) – dieselben Testquellen laufen in der CI mit echtem JUnit 4.

Braucht JDK 21 (`javac`) und `python3`; Kotlin 2.2.20 und `android.jar` werden einmal nach `~/.cache/eltouro-local-check`
geladen (nur `github.com`/`raw.githubusercontent.com`, kein Maven).

**Grenzen:** Die Attrappen bilden nur nach, was die App heute benutzt. Kommt eine neue Bibliotheks-Klasse dazu, muss ihre
Signatur in `stubs/` ergänzt werden (mit den `@NonNull`/`@Nullable` der echten API, sonst prüft Kotlin die Nullbarkeit falsch).
Gradle, Manifest-Merge, Ressourcen und Annotation-Processing prüft nur die CI. Grün hier heißt: gute Chancen – entscheidend
bleibt der CI-Lauf (Annotations am Lauf zeigen Kotlin-Fehler, Warnungen und die Testzahl).
