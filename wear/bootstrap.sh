#!/usr/bin/env bash
# Sets up the Gradle wrapper and local.properties so you can build the Ref6 Wear OS APK.
set -e

GRADLE_VERSION="8.7"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"
WRAPPER_URL="https://raw.githubusercontent.com/gradle/gradle/v${GRADLE_VERSION}.0/gradle/wrapper/gradle-wrapper.jar"
DIST_URL="https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"

# --- Android SDK path ---
if [ ! -f "local.properties" ]; then
  # Try to auto-detect common SDK locations
  if [ -n "$ANDROID_HOME" ]; then
    SDK_DIR="$ANDROID_HOME"
  elif [ -d "$HOME/Android/Sdk" ]; then
    SDK_DIR="$HOME/Android/Sdk"
  elif [ -d "$HOME/Library/Android/sdk" ]; then
    SDK_DIR="$HOME/Library/Android/sdk"
  else
    echo "ERROR: Cannot find Android SDK."
    echo "  Set ANDROID_HOME or create local.properties manually:"
    echo "  echo 'sdk.dir=/path/to/Android/Sdk' > local.properties"
    exit 1
  fi
  echo "sdk.dir=$SDK_DIR" > local.properties
  echo "Android SDK found at: $SDK_DIR"
fi

# --- Gradle wrapper jar ---
if [ -f "$WRAPPER_JAR" ]; then
  echo "Gradle wrapper already present."
else
  echo "Downloading gradle-wrapper.jar..."
  curl -fL "$WRAPPER_URL" -o "$WRAPPER_JAR" 2>/dev/null || {
    echo "Direct jar download failed — bootstrapping via full Gradle distribution..."
    TMP_DIR=$(mktemp -d)
    curl -fL "$DIST_URL" -o "$TMP_DIR/gradle.zip"
    unzip -q "$TMP_DIR/gradle.zip" -d "$TMP_DIR"
    "$TMP_DIR/gradle-${GRADLE_VERSION}/bin/gradle" wrapper --gradle-version="$GRADLE_VERSION"
    rm -rf "$TMP_DIR"
  }
fi

chmod +x gradlew
echo ""
echo "Bootstrap complete. Build the APK:"
echo "  ./gradlew assembleDebug"
echo ""
echo "Then sideload to Galaxy Watch 5 Pro:"
echo "  1. On watch: Settings > Developer options > ADB debugging ON"
echo "  2. Settings > Developer options > Debug over Wi-Fi ON"
echo "  3. Note the IP shown on-watch (e.g. 192.168.x.x:5555)"
echo "  4. adb connect <watch-ip>:5555"
echo "  5. adb install app/build/outputs/apk/debug/app-debug.apk"
