#!/usr/bin/env bash
#
# Type-checks the Objective-C(++) sources against React Native's own headers,
# for both the new and the legacy architecture, without an example app or a
# CocoaPods install. Requires Xcode and `npm install`.
#
#   ./scripts/check-ios.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

RN="$ROOT/node_modules/react-native"
BUILD="$ROOT/build/check-ios"

if [ ! -d "$RN" ]; then
  echo "react-native is not installed — run \`npm install\` first." >&2
  exit 1
fi

RN_VERSION="$(node -p "require('$RN/package.json').version")"
SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"

mkdir -p "$BUILD"

# 1. React's headers, in the <React/...> layout Xcode is given by CocoaPods.
HEADERS="$BUILD/headers"
if [ ! -d "$HEADERS" ]; then
  mkdir -p "$HEADERS/React" "$HEADERS/RCTDeprecation"
  find "$RN/React" -name '*.h' -exec ln -sf {} "$HEADERS/React/" \;
  find "$RN/ReactApple" -name 'RCTDeprecation.h' -exec ln -sf {} "$HEADERS/RCTDeprecation/" \;
fi

# 2. folly / glog / boost, as prebuilt for this React Native version.
DEPS="$BUILD/deps-$RN_VERSION"
if [ ! -d "$DEPS" ]; then
  echo "Fetching the prebuilt React Native dependencies ($RN_VERSION)..."
  mkdir -p "$DEPS"
  curl -sfL "https://repo1.maven.org/maven2/com/facebook/react/react-native-artifacts/$RN_VERSION/react-native-artifacts-$RN_VERSION-reactnative-dependencies-debug.tar.gz" \
    | tar xz -C "$DEPS"
fi
DEPS_HEADERS="$(find "$DEPS" -type d -path '*ReactNativeDependencies.xcframework/Headers' | head -1)"

# 3. The Codegen artifacts the new architecture compiles against.
GEN="$BUILD/codegen"
GEN_HEADERS="$GEN/build/generated/ios/ReactCodegen"
rm -rf "$GEN"
# The last step of the script generates an SPM manifest for an app project and
# fails here for lack of an .xcodeproj, well after the component headers this
# check needs have been written.
node "$RN/scripts/generate-codegen-artifacts.js" \
  --path "$ROOT" --outputPath "$GEN" --targetPlatform ios > /dev/null 2>&1 || true

SPEC_NAME="$(node -p "require('$ROOT/package.json').codegenConfig.name")"

if [ ! -f "$GEN_HEADERS/react/renderer/components/$SPEC_NAME/Props.h" ]; then
  echo "Codegen did not produce $SPEC_NAME/Props.h." >&2
  exit 1
fi

RC="$RN/ReactCommon"
INCLUDES=(
  -I "$HEADERS"
  -I "$RC"
  -I "$RC/yoga"
  -I "$RC/jsi"
  -I "$RC/callinvoker"
  -I "$RC/runtimeexecutor"
  -I "$RC/react/renderer/graphics/platform/ios"
  -I "$RC/react/utils/platform/ios"
  -I "$RC/react/renderer/components/view/platform/cxx"
  -I "$RC/react/renderer/imagemanager/platform/ios"
  -I "$RC/react/renderer/textlayoutmanager/platform/ios"
  -I "$GEN_HEADERS"
  -I "$DEPS_HEADERS"
)

status=0

for architecture in new legacy; do
  if [ "$architecture" = "new" ]; then
    defines=(-DRCT_NEW_ARCH_ENABLED=1)
  else
    # An empty array is an unbound variable under `set -u`, so pass a no-op.
    defines=(-DSAJJAD_BLUR_OVERLAY_LEGACY_CHECK=1)
  fi

  for source in ios/*.mm ios/*.m; do
    echo "Checking $source ($architecture architecture)"

    if ! xcrun clang -fsyntax-only -x objective-c++ -std=c++20 -fobjc-arc -Wall \
      -isysroot "$SDK" -target arm64-apple-ios15.1-simulator \
      "${defines[@]}" "${INCLUDES[@]}" "$source"; then
      status=1
    fi
  done
done

if [ "$status" -eq 0 ]; then
  echo "iOS sources check out on both architectures."
fi

exit "$status"
