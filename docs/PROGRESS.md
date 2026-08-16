# ReDAHM Android Port — Progress Log

Port of [reDAHM](https://github.com/masterspike52/reDAHM) (ReXGlue v0.9.0 recomps of
*Destroy All Humans! Path of the Furon*, Xbox 360) to Android (arm64, Gradle + NDK + CI).

## Layout

```
redahm-android/
├── .github/workflows/build.yml   # CI: host codegen tool -> codegen -> NDK build -> APK
├── android/                      # Gradle project (AGP, SDL3 Java layer, MainActivity)
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── java/io/redahm/android/MainActivity.java
│       └── java/org/libsdl/app/*.java     # SDL3 Android layer (from vendored SDL3)
├── assets/default.xex            # Entrypoint XEX (build-time, for codegen)
├── build-android.sh              # Orchestrates host tool build + codegen + NDK build
└── native/
    ├── rexglue-sdk/              # Vendored ReXGlue SDK v0.9.0 (patched for Android)
    ├── redahm/                   # Vendored reDAHM (patched for Android); assets/default.xex
    ├── build-host/               # (gitignored) host codegen tool build
    └── build-android/            # (gitignored) NDK cross-build output
```

## Done

- [x] Investigated reDAHM + ReXGlue architecture (entrypoint XEX, codegen, runtime paths,
      GPU plugin `librexgpu-xenos.so`, `game_data_root`/`user_data_root` cvars).
- [x] Vendored SDK + reDAHM into `native/`, stripped `.git`.
- [x] Copied `assets/default.xex` (build-time input for codegen; keep in repo).
- [x] SDK Android patches:
  - `CMakeLists.txt`: Android platform detection (arm64 → `REX_PLATFORM_ANDROID=1`).
  - `src/ui/`: new `surface_android.{h,cpp}` (`AndroidNativeWindowSurface`), new
    `main_android.{h,cpp}` (JNI path init, content-URI helpers, `JNI_OnLoad`),
    `window_sdl.cpp` Android surface via `SDL_PROP_WINDOW_ANDROID_WINDOW_POINTER`,
    `windowed_app_main_sdl.cpp` exports `SDL_main`, SDL3 built **shared** (`libSDL3.so`)
      on Android (SDLActivity requirement).
  - `src/core/filesystem_posix.cpp`: Android `GetExecutablePath()` / `GetUserFolder()`.
  - `thirdparty/CMakeLists.txt`: Android-only SDL shared build; desktop audio backends
      disabled.
- [x] reDAHM Android patches:
  - `CMakeLists.txt`: builds SHARED `main` → `libmain.so` on Android; xaudio2 only on
    Windows.
  - `redahm_app.h`: `OnFinalizePaths` returns defaults directly on Android (no desktop
    dialog wizard).
  - `path_setup_wizard.h` / `path_config_store.h`: removed hard Win32 dependencies.
- [x] Gradle project (AGP 9.3.1 + Gradle 9.3.1 wrapper, JDK 21, minSdk 26, targetSdk 35,
      arm64-v8a), jniLibs from `native/build-android/lib`, SDL3 Java layer included.
- [x] `MainActivity` (SDLActivity subclass): requests "All files access" (Android 11+),
      auto-detects extracted game data (`default.xex` + `KronosGame/`) under
      `/storage/emulated/0/redahm/game/` etc., passes `--game_data_root=`,
      `--user_data_root=`, `--gpu_plugin=xenos` to `SDL_main`.
- [x] CI workflow `.github/workflows/build.yml` (ubuntu-24.04, clang-18, JDK 21,
      actions/setup-android + NDK 27.2.12479018, cached host tool, APK artifact).

## In progress

- [x] Host codegen tool built (clang-18, `-march=x86-64-v3`, `-D__cpp_concepts=202002L`).
- [x] `rexglue codegen` verified locally: `CG_EXIT: 0` (1294s) → `generated/` (150 recomp
      units + `sources.cmake`). Key lesson: **`rexglue init` must NOT be run on CI** — it
      rewrites the manifest and strips the `redahm_config.toml` includes (which declare
      edge-case function starts that otherwise cause "8 unresolved calls"). So
      `generated/rexglue.cmake` is committed and CI runs `codegen` only.
- [x] Android CMake **configure** validated locally: `Platform: android-arm64`,
      Clang 18.0.3 (NDK r27), Vulkan=ON, `rexglue_setup_target(redahm GPU_PLUGINS xenos)` OK.
- [x] Pushed to `deivid22srk/redahm-android` (main). CI workflow runs.
- [ ] **CI run**: host tool → codegen → NDK arm64 build → Gradle APK → artifact.

## First CI attempt findings

- `actions/setup-android` does **not exist** → replaced with the runner image's
  `sdkmanager` (`$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager`).
- AGP 9.3.1 requires **Gradle ≥ 9.5.0** (not 9.3.x/9.4.x) → wrapper bumped to 9.5.0.
- `git push` used the wrong account (`deividgames5566`) → push explicitly with the
  `deivid22srk` token URL.

## Known gotchas

| Issue | Fix |
|---|---|
| clang-18 with libstdc++13: `std::expected` missing (`__cpp_concepts` 201907) | `-D__cpp_concepts=202002L` |
| `src/core/memory.cpp` uses SSSE3/SSE4.1 intrinsics | `-march=x86-64-v3` (host) |
| SDL3 must be shared on Android (SDLActivity loads `libSDL3.so`) | `SDL_SHARED=ON`, `SDL_STATIC=OFF` when `ANDROID` |
| `rexglue init` would clobber customized `CMakeLists.txt` | `init --force` then restore (done in build-android.sh) |
| Background builds die when the shell tool times out | launch with `setsid nohup bash -c '...' &` and poll logs |

## Runtime requirements (on device)

- arm64 device, Android 8.0+ (min 26), Vulkan 1.1+ device driver.
- Extracted US game data placed on the device, e.g.
  `/storage/emulated/0/redahm/game/` containing `default.xex` + `KronosGame/`
  (~6 GB). App auto-detects and reads it (MANAGE_EXTERNAL_STORAGE).
- User/save data: app external files dir (`io.redahm.android/user`).

## TODO next

1. Verify local codegen completes (validates manifest + configs for CI).
2. Push to `deivid22srk/redahm-android`; iterate on CI until `redahm-android-apk` artifact.
3. Manual device smoke test (app opens, Vulkan init, module load) once APK available.

## 2026-08-16: libc++/chrono + NDK r28 fixes
- **Discovery:** `std::chrono::clock_time_conversion` / `clock_cast<>` is ONLY implemented by libstdc++ (and MSVC STL). libc++ (used by ALL Android NDK versions, incl. LLVM 19 r28c and 21) does not provide it. That is why the NDK r27 build failed at `threading.cpp.o` (`chrono.h:120 explicit specialization of undeclared template struct 'clock_time_conversion'`); the host tool build passed because Linux uses libstdc++ 13.
- **Fix (SDK patch):** added portable helpers in `rex::chrono` namespace replicating the exact conversion logic:
  - `XSystemToWinSystemTime` / `WinSystemToXSystemTime` (chrono.h)
  - `SteadyToWinSystemTime` / `WinSystemToSteadyTime` (chrono_steady_cast.h)
  - Call sites switched from `std::chrono::clock_cast<>` to the helpers: `xtimer.cpp` (X→Win), `threading_posix.cpp` (Win→steady, 2 sites), `threading_win.cpp` (steady→Win, 2 sites, Win-only).
  - The `std::chrono::clock_time_conversion` specializations are now guarded by `#if defined(__GLIBCXX__) || defined(_MSC_VER)` (unused by rex code; kept so host libstdc++ builds that call clock_cast still compile).
- **Other Android compile fixes (threading_posix.cpp):** robust mutexes (`PTHREAD_MUTEX_ROBUST`, `pthread_mutex_consistent`) are glibc-only; bionic lacks them. Guards changed `#if REX_PLATFORM_LINUX` → `#if REX_PLATFORM_LINUX && !REX_PLATFORM_ANDROID` (3 sites). Added `#include <rex/math.h>` (rex::countof used in Android name-setter, was only reached transitively on Linux).
- **NDK bump:** workflow `NDK_VERSION: 27.2.12479018` → `28.2.13676358` (r28c, clang 19). Verified locally: r28 configure OK; `threading.cpp.o`, `threading_posix.cpp.o`, `xtimer.cpp.o` compile clean with r28 + patches; host libstdc++ compile of the same objects also clean.
- Committed & pushed (see log).
