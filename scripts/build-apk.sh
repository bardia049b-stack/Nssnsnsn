#!/usr/bin/env bash
# Assembles a signed release APK.
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
export JAVA_HOME="${JAVA_HOME:-$(command -v java >/dev/null && dirname "$(dirname "$(readlink -f "$(command -v java)")")")}"
./gradlew --no-daemon :app:assembleRelease "$@"
echo
echo "APK:"
find app/build/outputs/apk -name '*.apk' -printf '  %p  %s bytes\n'
