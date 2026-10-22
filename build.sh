#!/bin/sh
# Builds out/tv-recents.apk. Needs Java 17 and the Android SDK (platform 34, build-tools 34.0.0).
#   macOS:  brew install openjdk@17 && brew install --cask android-commandlinetools
#           sdkmanager "platforms;android-34" "build-tools;34.0.0"
# Then install on your TV:  adb connect <TV_IP>:5555 && adb install -r out/tv-recents.apk
set -e
cd "$(dirname "$0")"

[ -n "$JAVA_HOME" ] || JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null) || true
[ -n "$JAVA_HOME" ] || for j in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 /usr/lib/jvm/java-17-openjdk-amd64; do [ -d "$j" ] && JAVA_HOME=$j && break; done
[ -n "$JAVA_HOME" ] && export PATH="$JAVA_HOME/bin:$PATH"
SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[ -n "$SDK" ] || for d in "$HOME/Library/Android/sdk" /opt/homebrew/share/android-commandlinetools "$HOME/Android/Sdk"; do [ -d "$d" ] && SDK=$d && break; done
BT=$SDK/build-tools/34.0.0; JAR=$SDK/platforms/android-34/android.jar
[ -f "$JAR" ] && [ -d "$BT" ] || { echo "Android SDK not found. Set ANDROID_HOME, and install platforms;android-34 and build-tools;34.0.0." >&2; exit 1; }

rm -rf out; mkdir -p out/classes out/dex
"$BT/aapt2" compile --dir res -o out/res.zip
"$BT/aapt2" link -o out/base.apk -I "$JAR" --manifest AndroidManifest.xml out/res.zip
# shellcheck disable=SC2046 # javac and d8 want one argument per file
javac -nowarn -Xlint:-options -encoding UTF-8 -source 1.8 -target 1.8 -bootclasspath "$JAR:$BT/core-lambda-stubs.jar" \
  -d out/classes $(find src -name "*.java")
# shellcheck disable=SC2046
"$BT/d8" --min-api 26 --release --lib "$JAR" --output out/dex $(find out/classes -name '*.class')
cp out/base.apk out/unsigned.apk; (cd out/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 out/unsigned.apk out/aligned.apk

# A signing key is made on first build (key.jks, never committed). Keep it: updates must be signed
# with the same key. Release builds can use their own: set KEYSTORE and KEYSTORE_PASS.
KS=${KEYSTORE:-key.jks}; PASS=${KEYSTORE_PASS:-tvrecents}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass "$PASS" -keypass "$PASS" -alias recents \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=tv-recents" >/dev/null 2>&1
"$BT/apksigner" sign --ks "$KS" --ks-pass "pass:$PASS" --out out/tv-recents.apk out/aligned.apk
echo "Built out/tv-recents.apk"
