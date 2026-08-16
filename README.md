# ReDAHM — Android Port

Android port of [reDAHM](https://github.com/masterspike52/reDAHM), a ReXGlue-based
recompilation of **Destroy All Humans! Path of the Furon** (Xbox 360).

Built with **Gradle + Android NDK (CMake, Clang)** and a GitHub Actions CI that
produces an installable `ARM64` APK.

> ⚠️ This project is for personal/educational use. All rights to the original game
> belong to their respective owners. The game data (XEX + `KronosGame/`) is **not
> included** and must be provided by you from your own legally obtained copy.

## How it works

1. `build-android.sh` (run by CI) builds the **rexglue** codegen tool for the host,
   runs codegen against `assets/default.xex`, then cross-compiles the app + ReXGlue
   SDK for `arm64-v8a` (Vulkan backend, Xenos GPU plugin emulated on the device).
2. Gradle packages the shared libraries (`libmain.so`, `libSDL3.so`,
   `librexruntime.so`, `librexgpu-xenos.so`) + SDL3 Java layer into the APK.
3. At runtime the app auto-detects the extracted game data on device storage and
   passes `--game_data_root` to the engine.

## Building the APK (CI)

Push to `main` → `.github/workflows/build.yml` runs and attaches the app
`redahm-android-apk` (artifact: `android/app/build/outputs/apk/debug/`).

Local build (requires Android NDK 27.2.12479018):

```bash
export ANDROID_NDK_HOME=/path/to/ndk/27.2.12479018
bash build-android.sh          # host tool + codegen + NDK build
cd android && ./gradlew assembleDebug
```

## Installing game data

The extracted US version of the game must be on the device. Example:

```
/storage/emulated/0/redahm/game/
├── default.xex
└── KronosGame/
```

The app auto-detects the folder (also checks `Download/redahm/game` and its private
external storage). On Android 11+ you'll be asked to grant "All files access".
Estimated size: ~6 GB.

## Requirements

- arm64 device, Android 8.0+ (API 26), Vulkan 1.1+ driver
- ~6 GB free storage for game data + a few hundred MB for APK/cache

## Status

See [docs/PROGRESS.md](docs/PROGRESS.md).
