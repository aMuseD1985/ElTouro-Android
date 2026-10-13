#!/bin/bash
# Local check before a CI round trip – no Android SDK needed:
#   1. type-checks ALL Kotlin sources of the app against android.jar (API 36) + signature stubs of androidx/Play Services/Capacitor
#   2. compiles and runs the JVM unit tests of the Android-free packages (diag/, ride/, nav/, voice/, core/)
# Needs JDK 21 (javac) and python3; downloads Kotlin 2.2.20 and android.jar once into ~/.cache/eltouro-local-check.
# The stubs only mimic the real APIs: a green check here is a good sign, the CI build stays the judge.
set -e
REPO=$(cd "$(dirname "$0")/../.." && pwd)
HERE="$REPO/tools/local-check"
C="${ELTOURO_CHECK_CACHE:-$HOME/.cache/eltouro-local-check}"
mkdir -p "$C"
if [ ! -x "$C/kotlinc/bin/kotlinc" ]; then
  echo "· Kotlin 2.2.20 laden"
  curl -sSL -o "$C/kc.zip" https://github.com/JetBrains/kotlin/releases/download/v2.2.20/kotlin-compiler-2.2.20.zip
  (cd "$C" && unzip -q kc.zip && rm kc.zip)
fi
if [ ! -s "$C/android.jar" ]; then
  echo "· android.jar (API 36) laden"
  curl -sSL -o "$C/android.jar" https://raw.githubusercontent.com/Reginer/aosp-android-jar/main/android-36/android.jar
fi
KOTLINC="$C/kotlinc/bin/kotlinc"
rm -rf "$C/stubs" "$C/tc" "$C/jvm" "$C/shim" && mkdir -p "$C/stubs" "$C/tc" "$C/jvm" "$C/shim"
cp -r "$HERE/stubs/." "$C/stubs/"
python3 "$HERE/gen_r.py" "$REPO" "$C/stubs"
javac -nowarn -d "$C/stubs-classes" -cp "$C/android.jar" $(find "$C/stubs" -name '*.java')
javac -nowarn -d "$C/shim" $(find "$HERE/junitshim" -name '*.java')

echo "== Typprüfung (alle Kotlin-Quellen)"
SRC="$REPO/android/app/src/main/java"
if "$KOTLINC" -jvm-target 21 -nowarn -cp "$C/android.jar:$C/stubs-classes" -d "$C/tc" $(find "$SRC" -name '*.kt') 2>&1 | sed "s#$SRC/##" | grep -E 'error:' ; then
  echo "✗ Typprüfung fehlgeschlagen"; exit 1
fi
echo "✓ Typprüfung"

echo "== JVM-Unit-Tests (Android-freie Pakete)"
MAIN=$(grep -L -r -E '^import (android|androidx|com\.google|com\.getcapacitor)|\bR\.' "$SRC" --include=*.kt | grep -E '/(diag|nav|voice|core|ride|web)/' || true)
TESTS=$(find "$REPO/android/app/src/test/java/de" -name '*.kt')
"$KOTLINC" -nowarn -cp "$C/shim" -d "$C/jvm" $MAIN $TESTS 2>&1 | grep -E 'error:' && { echo "✗ Tests kompilieren nicht"; exit 1; }
CLASSES=$(cd "$C/jvm" && find . -name '*Test.class' | sed 's#^\./##; s#\.class$##; s#/#.#g')
java -cp "$C/jvm:$C/shim:$C/kotlinc/lib/kotlin-stdlib.jar" Runner $CLASSES
