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

- [ ] **Host codegen tool build** (`native/build-host`, clang-18, `-march=x86-64-v3`,
      `-D__cpp_concepts=202002L`) → then first full `rexglue codegen` run, committed
      knowledge: manifest/configs parse, `generated/` is gitignored (CI regenerates it).
- [ ] First CI run → fix errors iteratively until APK artifact builds.

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
