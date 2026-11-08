#!/usr/bin/env bash
set -euo pipefail
source /Users/y/toolchains/env.sh
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
# build/src/.cache 规范：.cache 只放可零成本丢弃的日志/临时物；
# 影响构建速度的中间物留在 src/.build，成品进 build
CACHE="$(cd "$ROOT/.." && pwd)/.cache"
if [[ "${1:-}" == clean ]]; then
  rm -rf -- "$CACHE"
  mkdir -p "$CACHE"
  exit 0
fi
MODE="${1:-debug}"
/Users/y/toolchains/bin/node "$ROOT/tool/tiebapure/sync-config.mjs"
/Users/y/toolchains/bin/xcodegen generate --spec "$ROOT/ios/project.yml" --project "$ROOT/ios" --project-root "$ROOT/ios"
APP_NAME="$(/Users/y/toolchains/bin/node -p 'require(process.argv[1]).name' "$ROOT/app-config.json")"
COMMON=(-project "$ROOT/ios/TiebaPure.xcodeproj" -scheme TiebaPure -derivedDataPath "$ROOT/ios/DerivedData")
mkdir -p "$CACHE/logs" "$ROOT/.build"
case "$MODE" in
  debug)
    /Users/y/toolchains/bin/xcodebuild "${COMMON[@]}" -configuration Debug -sdk iphoneos -destination 'generic/platform=iOS' build CODE_SIGNING_ALLOWED=NO ;;
  archive|ipa)
    ARCHIVE="$ROOT/.build/$APP_NAME.xcarchive"
    /Users/y/toolchains/bin/xcodebuild "${COMMON[@]}" -configuration Release -sdk iphoneos -destination 'generic/platform=iOS' -archivePath "$ARCHIVE" archive CODE_SIGNING_ALLOWED=NO
    if [[ "$MODE" == ipa ]]; then
      PACKAGE="$(mktemp -d "$CACHE/ipa.XXXXXX")"
      trap 'rm -rf "$PACKAGE"' EXIT
      mkdir -p "$PACKAGE/Payload"
      /usr/bin/ditto "$ARCHIVE/Products/Applications/TiebaPure.app" "$PACKAGE/Payload/TiebaPure.app"
      VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$PACKAGE/Payload/TiebaPure.app/Info.plist")"
      (cd "$PACKAGE" && /usr/bin/zip -qry archive.ipa Payload)
      /usr/bin/unzip -t "$PACKAGE/archive.ipa"
      OUTPUT="$(/Users/y/toolchains/bin/node "$ROOT/tool/tiebapure/store-package.mjs" "$PACKAGE/archive.ipa" "$APP_NAME-$VERSION-unsigned.ipa")"
      /usr/bin/shasum -a 256 "$OUTPUT"
    fi ;;
  test)
    : "${SIMULATOR_UDID:?Set SIMULATOR_UDID to an available iOS simulator}"
    /Users/y/toolchains/bin/xcodebuild "${COMMON[@]}" -destination "platform=iOS Simulator,id=$SIMULATOR_UDID" test -only-testing:TiebaPureTests -skip-testing:TiebaPureTests/AnonymousLiveSmokeTests ;;
  signed-archive)
    : "${DEVELOPMENT_TEAM:?Set the signing team and install matching provisioning profiles}"
    /Users/y/toolchains/bin/xcodebuild "${COMMON[@]}" -configuration Release -destination 'generic/platform=iOS' -archivePath "$ROOT/.build/$APP_NAME-signed.xcarchive" archive DEVELOPMENT_TEAM="$DEVELOPMENT_TEAM" ;;
  export)
    : "${2:?Provide an ExportOptions.plist matching your signing method}"
    EXPORT="$(mktemp -d "$CACHE/ios-export.XXXXXX")"
    /Users/y/toolchains/bin/xcodebuild -exportArchive -archivePath "$ROOT/.build/$APP_NAME-signed.xcarchive" -exportOptionsPlist "$2" -exportPath "$EXPORT"
    shopt -s nullglob
    EXPORTED_IPAS=("$EXPORT"/*.ipa)
    [[ ${#EXPORTED_IPAS[@]} -eq 1 ]] || { echo 'Expected one exported IPA' >&2; exit 1; }
    VERSION="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$ROOT/.build/$APP_NAME-signed.xcarchive/Products/Applications/TiebaPure.app/Info.plist")"
    OUTPUT="$(/Users/y/toolchains/bin/node "$ROOT/tool/tiebapure/store-package.mjs" "${EXPORTED_IPAS[0]}" "$APP_NAME-$VERSION.ipa")"
    /usr/bin/shasum -a 256 "$OUTPUT" ;;
  *) echo 'Usage: build-ios.sh debug|archive|ipa|test|signed-archive|export <ExportOptions.plist>|clean' >&2; exit 2 ;;
esac
