#!/usr/bin/env bash
# Build the ReDAHM native code for Android (arm64) and stage the .so files
# into native/build-android/lib for the Gradle APK packaging step.
#
# Pipeline:
#   1. Build the rexglue codegen tool for the HOST (Linux x86_64).
#   2. Run codegen against assets/default.xex to produce generated/ code.
#   3. Cross-compile the reDAHM app + ReXGlue SDK for Android arm64-v8a.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
NATIVE="$ROOT/native"
REDAHM="$NATIVE/redahm"
SDK="$NATIVE/rexglue-sdk"
HOST_BUILD="$NATIVE/build-host"
ANDROID_BUILD="$NATIVE/build-android"
LIB_OUT="$ANDROID_BUILD/lib"
ANDROID_ABI="arm64-v8a"
ANDROID_API="26"
JOBS="${JOBS:-$(nproc)}"

HOST_CC="${CC:-clang}"
HOST_CXX="${CXX:-clang++}"
command -v cmake >/dev/null || { echo "cmake required"; exit 1; }
command -v ninja >/dev/null || { echo "ninja required"; exit 1; }
command -v "$HOST_CXX" >/dev/null || { echo "C++ compiler '$HOST_CXX' required"; exit 1; }

NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK:-}}"
if [ -z "$NDK" ] || [ ! -f "$NDK/build/cmake/android.toolchain.cmake" ]; then
  echo "ANDROID_NDK_HOME must point to an Android NDK (with build/cmake/android.toolchain.cmake)"
  exit 1
fi

echo "==> [1/4] Building rexglue host codegen tool"
# Note: the SDK places the tool in <sdk>/out/linux-amd64/rexglue
REXGLUE_TOOL="$SDK/out/linux-amd64/rexglue"
mkdir -p "$HOST_BUILD"
if [ ! -f "$REXGLUE_TOOL" ]; then
  cmake -G Ninja -S "$SDK" -B "$HOST_BUILD" \
    -DCMAKE_C_COMPILER=clang -DCMAKE_CXX_COMPILER=clang++ \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_C_FLAGS="-march=x86-64-v3" \
    -DCMAKE_CXX_FLAGS="-march=x86-64-v3 -D__cpp_concepts=202002L" \
    -DREXGLUE_BUILD_TESTS=OFF \
    -DREXGLUE_ENABLE_TRACY=OFF \
    -DREXGLUE_ENABLE_FIDELITYFX=OFF \
    -DREXGLUE_USE_D3D12=OFF \
    -DREXGLUE_USE_VULKAN=ON
  cmake --build "$HOST_BUILD" --target rexglue -j "$JOBS"
fi

echo "==> [2/4] Running rexglue codegen on assets/default.xex"
if [ ! -f "$REXGLUE_TOOL" ]; then
  echo "rexglue tool missing: $REXGLUE_TOOL"; exit 1
fi
if [ ! -f "$REDAHM/generated/rexglue.cmake" ]; then
  echo "generated/rexglue.cmake missing (committed SDK boilerplate). Run 'rexglue init' once,"
  echo "then restore redahm_manifest.toml (init strips its config includes)."
  exit 1
fi
"$REXGLUE_TOOL" codegen "$REDAHM/redahm_manifest.toml"

echo "==> [3/4] Cross-compiling for Android $ANDROID_ABI (API $ANDROID_API)"
rm -rf "$ANDROID_BUILD"
cmake -G Ninja -S "$REDAHM" -B "$ANDROID_BUILD" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ANDROID_ABI" \
  -DANDROID_PLATFORM="android-$ANDROID_API" \
  -DANDROID_ARM_NEON=ON \
  -DCMAKE_BUILD_TYPE=Release \
  -DREXSDK_DIR="$SDK" \
  -DREXGLUE_BUILD_TESTS=OFF \
  -DREXGLUE_ENABLE_TRACY=OFF \
  -DREXGLUE_ENABLE_FIDELITYFX=OFF \
  -DREXGLUE_USE_D3D12=OFF \
  -DREXGLUE_USE_VULKAN=ON
cmake --build "$ANDROID_BUILD" -j "$JOBS"

echo "==> [4/4] Staging shared libraries for the APK"
mkdir -p "$LIB_OUT"
find "$ANDROID_BUILD" -maxdepth 3 -name "*.so" -type f -exec cp {} "$LIB_OUT/" \;
# Runtime shared libs (librexruntime.so, libSDL3.so, GPU plugin) are emitted to
# the SDK out/android-arm64 dir, which sits deeper than the find above reaches.
find "$SDK/out/android-arm64" -maxdepth 1 -name "*.so" -type f -exec cp {} "$LIB_OUT/" \;
cp "$ANDROID_BUILD/libmain.so" "$LIB_OUT/" 2>/dev/null || true
echo
echo "Native libraries staged in $LIB_OUT:"
ls -la "$LIB_OUT"
echo
echo "Done. Run 'cd android && ./gradlew assembleRelease' (or assembleDebug) to build the APK."