#!/usr/bin/env bash
# Builds the sing-box native core (libbox.aar) that NebulaBox tunnels through.
#
# This is deliberately a separate step: it needs a Go toolchain and the Android
# NDK, and the build is memory hungry (roughly 2 GB for the link step), so it
# should run on a machine with headroom rather than inside a constrained CI box.
#
# Requirements:
#   - Go 1.25 or newer
#   - Android SDK with platform 35 and build-tools 35.0.0
#   - Android NDK r28
#   - OpenJDK 17 exactly (the sing-box build script asserts on the version string)
#   - github.com/sagernet/gomobile and .../gobind on PATH
#
# Usage:  ./scripts/build-libbox.sh [android/arm64 | android]
set -euo pipefail

TARGET="${1:-android/arm64}"
SING_BOX_VERSION="${SING_BOX_VERSION:-v1.14.2}"
WORK="${WORK:-/tmp/libbox-build}"

: "${ANDROID_HOME:?set ANDROID_HOME to your Android SDK}"
: "${ANDROID_NDK_HOME:?set ANDROID_NDK_HOME to your NDK}"
: "${JAVA_HOME:?set JAVA_HOME to an OpenJDK 17 install}"

command -v go >/dev/null || { echo "go not found on PATH" >&2; exit 1; }

mkdir -p "$WORK"
cd "$WORK"

if [ ! -d sing-box ]; then
  git clone --depth 1 --branch "$SING_BOX_VERSION" https://github.com/SagerNet/sing-box.git
fi
cd sing-box

echo ">> installing gomobile + gobind"
go install github.com/sagernet/gomobile/cmd/gomobile@latest
go install github.com/sagernet/gomobile/cmd/gobind@latest
export PATH="$(go env GOPATH)/bin:$PATH"

echo ">> building libbox for $TARGET (this is the slow part)"
go run ./cmd/internal/build_libbox -platform "$TARGET"

# The build script copies into ../sing-box-for-android/app/libs when that
# directory exists, so look in both places.
FOUND="$(find . ../sing-box-for-android/app/libs -maxdepth 3 -name 'libbox.aar' 2>/dev/null | head -1)"
if [ -z "$FOUND" ]; then
  echo "!! libbox.aar was not produced" >&2
  exit 1
fi

DEST="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/app/libs"
mkdir -p "$DEST"
cp "$FOUND" "$DEST/libbox.aar"
echo ">> installed $(du -h "$DEST/libbox.aar" | cut -f1) to $DEST/libbox.aar"
echo ">> now rebuild the app: ./gradlew :app:assembleRelease"
