#!/usr/bin/env bash
#
# Compiles the Android sources against React Native's own classes and the
# Codegen-generated view manager interface, without a Gradle project. Requires
# a JDK, an Android SDK (ANDROID_HOME) and `npm install`.
#
#   ./scripts/check-android.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

RN="$ROOT/node_modules/react-native"
BUILD="$ROOT/build/check-android"
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"

if [ ! -d "$RN" ]; then
  echo "react-native is not installed — run \`npm install\` first." >&2
  exit 1
fi

ANDROID_JAR="$(ls -d "$SDK_ROOT"/platforms/android-* 2>/dev/null | sort -V | tail -1)/android.jar"

if [ ! -f "$ANDROID_JAR" ]; then
  echo "No Android platform found under $SDK_ROOT/platforms." >&2
  exit 1
fi

RN_VERSION="$(node -p "require('$RN/package.json').version")"

mkdir -p "$BUILD/classes"

# 1. React Native's Android classes.
if [ ! -f "$BUILD/react-android/classes.jar" ]; then
  echo "Fetching the React Native Android classes ($RN_VERSION)..."
  curl -sfL -o "$BUILD/react-android.aar" \
    "https://repo1.maven.org/maven2/com/facebook/react/react-android/$RN_VERSION/react-android-$RN_VERSION-release.aar"
  unzip -q -o "$BUILD/react-android.aar" -d "$BUILD/react-android"
fi

# 2. The Codegen-generated view manager interface and delegate.
GEN="$BUILD/codegen"
rm -rf "$GEN"
node "$RN/scripts/generate-codegen-artifacts.js" \
  --path "$ROOT" --outputPath "$GEN" --targetPlatform android > /dev/null

# 3. androidx.annotation, which only annotations are needed from.
mkdir -p "$BUILD/stubs/androidx/annotation"
cat > "$BUILD/stubs/androidx/annotation/Nullable.java" <<'JAVA'
package androidx.annotation;

public @interface Nullable {}
JAVA

javac -nowarn --release 17 \
  -cp "$BUILD/react-android/classes.jar:$ANDROID_JAR" \
  -d "$BUILD/classes" \
  "$BUILD/stubs/androidx/annotation/Nullable.java" \
  "$GEN"/android/app/build/generated/source/codegen/java/com/facebook/react/viewmanagers/*.java \
  android/src/main/java/com/bluroverly/*.java

echo "Android sources compile against React Native $RN_VERSION."
