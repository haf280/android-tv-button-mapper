#!/bin/sh
# Builds out/tv-recents.apk inside Docker: no Java or Android SDK needed on this computer.
# Signing: set KEYSTORE_PASS to your key.jks password (see build.sh); it's passed through to the build.
set -e
cd "$(dirname "$0")/.."
docker build -q -t tv-recents-build docker > /dev/null
docker run --rm --user "$(id -u):$(id -g)" -e HOME=/tmp -e KEYSTORE_PASS -v "$PWD":/src tv-recents-build sh build.sh
