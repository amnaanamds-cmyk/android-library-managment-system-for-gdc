#!/usr/bin/env bash
# ===================================================================
#  Compile every Kotlin file with the real Kotlin compiler and report the
#  errors that do NOT require the Android SDK to detect:
#
#    * syntax errors            "Expecting an element", "Expecting '}'"
#    * declaration conflicts    "conflicting declarations", "redeclaration"
#
#  The second class was added after a duplicate `val context` in one composable
#  shipped and broke the build. It is a RESOLUTION error, not a syntax error,
#  so a syntax-only filter reported a clean tree while the app would not
#  compile. Both classes are about names in the file itself, so neither needs
#  Android, Compose or Firebase on the classpath.
#
#      ./tools/kotlin-syntax-check.sh
#
#  Why this exists: the Android app cannot always be compiled — the
#  Android Gradle Plugin lives only on dl.google.com, which is blocked
#  in some environments, and a full build needs the Android SDK. A
#  syntax error ("Expecting an element", "Expecting '}'") needs neither.
#  This catches that whole class in about a minute, anywhere.
#
#  It does NOT type-check: without the Android, Compose and Firebase
#  jars on the classpath the compiler emits ~20,000 "unresolved
#  reference" errors that are pure noise, so they are filtered out.
#  For real type checking, run:  ./gradlew :app:compileDebugKotlin
# ===================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

KOTLIN_VERSION="${KOTLIN_VERSION:-2.0.21}"
COROUTINES_VERSION="1.8.1"
CACHE="${KOTLIN_CHECK_CACHE:-$HOME/.cache/nexlib-kotlinc}"
MAVEN="https://repo.maven.apache.org/maven2"

mkdir -p "$CACHE"

fetch() { # url dest
  [ -s "$2" ] && return 0
  echo "  downloading $(basename "$2")..."
  for i in 1 2 3; do
    if curl -sSLf -o "$2" "$1"; then return 0; fi
    sleep $((i * 5))          # Maven Central rate-limits; back off
  done
  echo "  could not download $(basename "$2")" >&2
  return 1
}

echo "Preparing Kotlin $KOTLIN_VERSION compiler..."
fetch "$MAVEN/org/jetbrains/kotlin/kotlin-compiler-embeddable/$KOTLIN_VERSION/kotlin-compiler-embeddable-$KOTLIN_VERSION.jar" "$CACHE/kotlin-compiler-embeddable.jar"
fetch "$MAVEN/org/jetbrains/kotlin/kotlin-stdlib/$KOTLIN_VERSION/kotlin-stdlib-$KOTLIN_VERSION.jar"                           "$CACHE/kotlin-stdlib.jar"
fetch "$MAVEN/org/jetbrains/kotlin/kotlin-reflect/$KOTLIN_VERSION/kotlin-reflect-$KOTLIN_VERSION.jar"                         "$CACHE/kotlin-reflect.jar"
fetch "$MAVEN/org/jetbrains/kotlin/kotlin-script-runtime/$KOTLIN_VERSION/kotlin-script-runtime-$KOTLIN_VERSION.jar"           "$CACHE/kotlin-script-runtime.jar"
fetch "$MAVEN/org/jetbrains/kotlin/kotlin-daemon-embeddable/$KOTLIN_VERSION/kotlin-daemon-embeddable-$KOTLIN_VERSION.jar"     "$CACHE/kotlin-daemon-embeddable.jar"
fetch "$MAVEN/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/$COROUTINES_VERSION/kotlinx-coroutines-core-jvm-$COROUTINES_VERSION.jar" "$CACHE/coroutines.jar"
fetch "$MAVEN/org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330.jar"                                      "$CACHE/trove4j.jar"

CP="$CACHE/kotlin-compiler-embeddable.jar:$CACHE/kotlin-stdlib.jar:$CACHE/kotlin-reflect.jar:$CACHE/kotlin-script-runtime.jar:$CACHE/kotlin-daemon-embeddable.jar:$CACHE/coroutines.jar:$CACHE/trove4j.jar"

SRC=$(mktemp)
OUT=$(mktemp -d)
LOG=$(mktemp)
trap 'rm -rf "$SRC" "$OUT" "$LOG"' EXIT

find app/src/main shared/src -name '*.kt' > "$SRC"
echo "Parsing $(wc -l < "$SRC" | tr -d ' ') Kotlin files..."

java -Xmx2g -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -no-reflect -cp "$CACHE/kotlin-stdlib.jar" \
  "@$SRC" -d "$OUT" -nowarn 2>&1 | grep -v '^Picked up' > "$LOG" || true

SYNTAX=$(grep -c 'syntax error' "$LOG" || true)
CONFLICT=$(grep -ic 'conflicting declaration\|redeclaration' "$LOG" || true)

echo
if [ "$SYNTAX" -eq 0 ] && [ "$CONFLICT" -eq 0 ]; then
  echo "No syntax errors and no declaration conflicts in any Kotlin file."
  exit 0
fi

if [ "$SYNTAX" -ne 0 ]; then
  echo "$SYNTAX syntax error(s):"
  echo
  grep -B1 -A2 'syntax error' "$LOG"
  echo
fi

if [ "$CONFLICT" -ne 0 ]; then
  echo "$CONFLICT declaration conflict(s) — the same name declared twice in one scope:"
  echo
  grep -i -A3 'conflicting declaration\|redeclaration' "$LOG"
  echo
fi
exit 1
