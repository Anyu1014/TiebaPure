#!/usr/bin/env bash
set -euo pipefail
source /Users/y/toolchains/env.sh
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# build/src/.cache 规范：缓存进 .cache，成品进 build
CACHE="$(cd "$ROOT/.." && pwd)/.cache"
if [[ "${1:-}" == clean ]]; then
  rm -rf -- "$CACHE"
  mkdir -p "$CACHE"
  exit 0
fi
cd "$ROOT/android"
printf 'sdk.dir=/Users/y/toolchains/android-sdk\n' > local.properties
MODE="${TIEBAPURE_ANDROID_MODE:-debug}"
if [[ "$MODE" == release ]]; then
  : "${TIEBAPURE_KEYSTORE:?Set the release keystore path}"
  : "${TIEBAPURE_KEY_PASSWORD_FILE:?Set the private password file path}"
  ./gradlew --no-daemon testDebugUnitTest assembleRelease "$@"
elif [[ "$MODE" == debug ]]; then
  ./gradlew --no-daemon testDebugUnitTest assembleDebug "$@"
else
  echo 'TIEBAPURE_ANDROID_MODE must be debug or release' >&2
  exit 2
fi
VERSION="$(/Users/y/toolchains/bin/node -p 'require("../app-config.json").version')"
APP_NAME="$(/Users/y/toolchains/bin/node -p 'require("../app-config.json").name')"
NAME="$APP_NAME-$VERSION.apk"
if [[ "$MODE" == debug ]]; then NAME="$APP_NAME-$VERSION-debug.apk"; fi
SOURCE_APK="app/build/outputs/apk/$MODE/app-$MODE.apk"
/Users/y/toolchains/bin/apksigner verify --verbose "$SOURCE_APK"
OUTPUT="$(/Users/y/toolchains/bin/node "$ROOT/tool/tiebapure/store-package.mjs" "$SOURCE_APK" "$NAME")"
/usr/bin/shasum -a 256 "$OUTPUT"
