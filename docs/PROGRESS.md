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

## 2026-08-16: Second round of Android compile/link fixes (full native build green locally)
- Full NDK r28 arm64 build now completes locally: `libmain.so` (317MB, exports SDL_main), `librexruntime.so`, `librexgpu-xenos.so`. NINJA_EXIT=0.
- Fixes in this round:
  - `fiber.h`/`fiber_android.cpp`/`fiber_posix.cpp`: bionic removed getcontext/makecontext/swapcontext. New ARM64 asm context switch (naked fn saving x19-x28, x29, x30, sp, d8-d15) in `fiber_android.cpp`; struct gets an `AndroidContext`; `fiber_posix.cpp` guarded to non-Android.
  - `filesystem_posix.cpp`: removed duplicate `GetUserFolder` (Android branch copy vs. the general one which already handles Android).
  - `numeric.h`: libc++ lacks floating-point `std::from_chars`; added `detail::fp_from_chars` (strtof/strtod fallback when `__cpp_lib_to_chars` absent).
  - `src/ui/windowed_app_main_sdl.cpp` + `include/rex/system.h`: `InitializeAndroidSystemForApplicationContext`/`ShutdownAndroidSystem` are declared/defined under `rex::system` (fixed namespace mismatch; rex::system is already used across the SDK).
  - `core/CMakeLists.txt`: on Android don't link `-lpthread -lrt` (merged into libc in bionic).
  - `ui/CMakeLists.txt`: link `android nativewindow` on Android for ANativeWindow_* symbols.
  - `native/redahm/CMakeLists.txt`: add `${REXSDK_DIR}/thirdparty/imgui` include dir to the `main` target (rexruntime links rexui/imgui PRIVATE so the imgui header dir never propagates; redahm_engine headers include imgui.h directly).
  - `build-android.sh`: stage .so's also from `$SDK/out/android-arm64` (librexruntime.so/libSDL3.so/plugin live deeper than the original `find -maxdepth 3` reached; libmain.so NEEDED them).

## 2026-08-17: APK packaging fix + JNI storage bridge
- **CI native step passed** (commit dc1d0f4b): codegen + NDK arm64 build all green on the runner. The whole rexglue SDK + redahm now cross-compile for Android.
- **Gradle packaging fix:** AGP 9 rejects `android:extractNativeLibs="true"` in the manifest (`:app:packageDebug FAILED`). Moved to `packagingOptions.jniLibs.useLegacyPackaging = true` in `android/app/build.gradle.kts` and removed the manifest attribute. Keeps the ~460MB of .so's compressed in the APK.
- **JNI bridge (needed at runtime):** `MainActivity.setupNativePaths()` (native, in libmain.so via new `native/redahm/src/android_bridge.cpp`) is called from onCreate after SDLActivity loads the libs and before the SDL main thread starts. It feeds ApplicationInfo.nativeLibraryDir + files/cache dirs into `rex::SetAndroidAppPaths`, so `GetAndroidNativeLibraryDir()` resolves correctly (GPU plugin `librexgpu-xenos.so` is loaded from that dir) and user/external dirs work. Also stores the JavaVM via `InitializeAndroidJavaVM` (dependency libs never get JNI_OnLoad called).
- Remaining: CI producing the APK artifact; then on-device runtime validation (MANAGE_EXTERNAL_STORAGE, game data at /storage/emulated/0/redahm/game, plugin load, Vulkan init).

## 2026-08-17: First complete APK (all native libs packaged)
- Root cause of the 917KB APK: Gradle `jniLibs` source dirs resolve relative to the module dir (`android/app/`), so `../../../native/build-android/lib` pointed ABOVE the repo root. Fixed to `../../native/build-android/lib` (commit 844f5bee), combined with staging `.so`'s under `lib/arm64-v8a/` (commit daa3e208).
- CI run 31988273110 all-green: codegen + NDK build + `assembleDebug` + artifact upload.
- APK contents (`lib/arm64-v8a/`): libmain.so (~91MB stripped), librexruntime.so (~8.5MB stripped), libSDL3.so, librexgpu-xenos.so, libSPIRV-Tools-shared.so. Total APK ~40MB compressed.
- Next: install on device (Android 8+/arm64, MANAGE_EXTERNAL_STORAGE granted), game data at /storage/emulated/0/redahm/game (default.xex + KronosGame/), then runtime bring-up (GPU plugin load, Vulkan init, first frames).

## 2026-08-17: Java launcher screen before the game (run 32014478908)
- MainActivity is now a plain Java activity (no native libs): SAF folder picker + ISO picker + "Iniciar Jogo" button. Selection persisted in SharedPreferences; folder must contain default.xex + KronosGame/; ISO resolves to a sibling extracted folder.
- GameActivity (extends org.libsdl.app.SDLActivity, android:process=":game" so each launch gets fresh SDL native state) receives game_data_root/user_data_root via intent extras, builds --game_data_root/--user_data_root/--gpu_plugin=xenos args for SDL_main, and calls the JNI bridge (renamed Java_io_redahm_android_GameActivity_setupNativePaths) after libs load.
- Fixed two more read-only-path bugs in rex_app.cpp SetupEnvironment: log dir and config file (redahm.toml) now live in the user data dir on Android (executable folder = read-only nativeLibraryDir). This was the actual SIGABRT: create_directories("/data/app/.../lib/logs") Permission denied.
- App lookup chain verified: REX_DEFINE_APP(redahm,...) + SetAndroidAppName("redahm") + GetCreator by name (XE_UI_WINDOWED_APPS_IN_LIBRARY always 1).
- Verified: GameActivity + SDLActivity classes in the APK dex; all 5 native libs packaged.
- Device test (moto g34): APK run 32014478908 -> https://github.com/deivid22srk/redahm-android/actions/runs/32014478908

## 2026-08-17: User-importable custom Vulkan driver (AdrenoTools style)
- Feature requested by the user, matching https://github.com/SansNope/UnleashedRecomp-Android: a button in the launcher to import a custom Adreno GPU driver (Mesa Turnip) as a `.zip` (AdrenoTools package) or plain `.so`, so the game can use a different Vulkan driver than the system one.
- Vendored `libadrenotools` + `liblinkernsbypass` (both BSD-2-Clause, the exact libraries UnleashedRecomp-Android uses) into `native/rexglue-sdk/thirdparty/adrenotools/` with a custom CMakeLists (Android-arm64 only, builds the static `adrenotools` lib + the 4 runtime hook shared libs: libhook_impl/libmain_hook/libfile_redirect_hook/libgsl_alloc_hook). Wired into `thirdparty/CMakeLists.txt`.
- Native integration:
  - New `src/ui/vulkan/vulkan_custom_driver_android.cpp`: calls `adrenotools_open_libvulkan(RTLD_NOW, ADRENOTOOLS_DRIVER_CUSTOM, cacheDir, nativeLibraryDir, driverDir, driverName, ...)` and returns the driver's `vkGetInstanceProcAddr`.
  - `vulkan_instance.cpp` `VulkanInstance::Create`: on Android, if a custom driver is configured it replaces the system `libvulkan.so` loader (whole stack: instance/device/surface/swapchain go through the custom driver); otherwise falls back to the system loader.
  - `main_android.{h,cpp}`: `SetAndroidVulkanDriver`/`GetAndroidVulkanDriver` (thread-safe globals).
  - `android_bridge.cpp`: new JNI `Java_io_redahm_android_GameActivity_setVulkanDriver(JNIEnv*, jobject, jstring dir, jstring name)`.
- Java (launcher):
  - MainActivity: "Driver Vulkan" section — import button (SAF picker, zip or .so), extract into `getFilesDir()/vulkan_drivers/<name>/` (internal storage, required for dlopen), auto-detect main driver .so (libvulkan_turnip.so > libvulkan.so.qualcomm > any non-freedreno .so), "Trocar Driver" dialog (system driver + imported drivers), selection persisted in SharedPreferences and passed to GameActivity via EXTRA_VULKAN_DRIVER_DIR/EXTRA_VULKAN_DRIVER_SO.
  - GameActivity: calls `setVulkanDriver(dir, name)` after `setupNativePaths()`.
- Constraints met: `useLegacyPackaging=true` (hook libs must be extracted to nativeLibraryDir); hook libs staged automatically by build-android.sh (the `$SDK/out/android-arm64/*.so` find picks them up).
- Verified locally: full NDK arm64 build green; libmain.so exports both JNI symbols; librexruntime.so contains `adrenotools_open_libvulkan`; local `./gradlew assembleDebug` green; APK packages all 9 libs (5 runtime + 4 hooks).
- Next: CI run; on-device test — import a Turnip driver and confirm Vulkan init picks it up (or confirm the stock driver is enough).

## 2026-08-17: Recover from ANativeWindow recreation (Android surface loss)
- First on-device test (Build #17, stock driver) got the game BOOTING on the moto g34: Xenos plugin loaded, Vulkan device (Adreno 619, stock driver, full feature set incl. geometry/tessellation/depthClamp), 1600x720 swapchain, default.xex loaded, splash/UnrealLogo BINK movies rendered. Confirmed the game data is read correctly.
- Failure mode: after the loading screens the screen froze black with
  `VulkanPresenter: Presentation to the swapchain image has been dropped as the swapchain or the surface has become outdated` followed by `VulkanPresenter: Failed to get Vulkan surface capabilities`.
- Root cause (verified in the vendored SDL3 source): on Android the system can destroy + recreate the window's ANativeWindow (pause/resume, IME, SurfaceView recreation). SDL3's `onNativeSurfaceCreated`/`onNativeSurfaceDestroyed` ONLY refresh `SDL_PROP_WINDOW_ANDROID_WINDOW_POINTER` and post NO SDL event. rex kept its VkSurfaceKHR created from the old (now released) ANativeWindow, so every present failed with VK_ERROR_SURFACE_LOST_KHR and the reconnect used the same stale surface ("Failed to get Vulkan surface capabilities") - permanent black screen.
- Fix in `WindowSDL` (Android only):
  - Cache the last-known ANativeWindow pointer; refresh it in `CreateSurfaceImpl`.
  - New `CheckAndroidNativeWindowChanged()` (UI thread): re-reads the SDL property; if the native window changed (and is non-null), calls `OnSurfaceChanged(true)` so `CreateSurfaceImpl` re-reads the new ANativeWindow and the presenter re-creates the VkSurfaceKHR + swapchain. The presenter takes paint ownership during this, so it's safe vs. the guest-output present thread.
  - Called from `HandlePaintEvent` and `HandleWindowEvent` (fast path), plus a 250 ms repeating SDL timer watchdog (deferred to the UI thread) so recovery also happens while painting is idle (paint mode kNone).
- Verified: NDK arm64 build green; symbol present in librexruntime.so; local gradle APK built; APK's librexruntime.so confirmed byte-identical (minus .comment) to the fresh build.
- On-device (12:51 UTC, run 32025905524 APK pending): this APK was built from b64c533b; the user's next test should use it with the stock driver first (validates the surface-loss fix), then optionally a Turnip driver.

## 2026-08-17: Fix Turnip driver import (libvulkan_freedreno.so) + harden zip extraction
- Second on-device test (09:51 -03, run 32024087991 APK = driver-import feature WITHOUT the surface-loss fix) still showed the same black screen (expected: that build predates b64c533b), and the driver import failed:
  `W ReDAHM : Driver import: no .so found in /data/user/0/io.redahm.android/files/vulkan_drivers/WN-Turnip-1.06-p_Axxx`
- Root cause: the user's package (WN-Turnip-1.06-p_Axxx.zip from WinNative-Emu/Drivers, unified Adreno build) ships `libvulkan_freedreno.so` + `meta.json`. `findDriverSo` excluded any `.so` whose name contained "freedreno" and preferred `libvulkan_turnip.so`/`libvulkan.so.qualcomm`, neither of which exist in that package -> "no .so found".
- Fix (MainActivity.java):
  - `findDriverSo` preferred order is now `libvulkan_freedreno.so` > `libvulkan_turnip.so` > `libvulkan.so.qualcomm` > any `.so` (freedreno exclusion removed).
  - `extractDriverZip`: skip extraction when the output file already exists (keep the first/root-level copy; per-device subfolder variants share basenames) and `setExecutable(true, false)` on extracted `.so`.
- `libdolphin.so` log spam explained: `Unable to open libdolphin.so` is a benign Android system message (also emitted by SurfaceFlinger and unrelated apps; a Qualcomm vendor probe) - not from this app.
- Next: CI run for this fix; user re-imports WN-Turnip-1.06-p_Axxx.zip and confirms the Vulkan init log shows the Turnip/Mesa driver name instead of the Qualcomm stock driver.

## 2026-08-17: Turnip selected but game exits instantly -> instrumenting adrenotools
- On-device (run 32036671319 APK): with the Turnip driver selected, the game now starts AND the driver is found (`Using custom Vulkan driver: libvulkan_freedreno.so (/data/user/0/io.redahm.android/files/vulkan_drivers/WN-Turnip-1.06-p_Axxx)` - the findDriverSo fix works), but `SDL_main` returns instantly and the app goes back to the launcher.
- Game log shows: `Failed to open custom Vulkan driver 'libvulkan_freedreno.so' from ...` -> `Unable to create graphics provider` -> `Graphics presentation setup failed: C0000001`. So `adrenotools_open_libvulkan()` returned NULL.
- libadrenotools + linkernsbypass are SILENT on failure (return nullptr without logging), so the exact failing step is unknown. Instrumented both:
  - `thirdparty/adrenotools/src/driver.cpp`: `__android_log_print(ANDROID_LOG_ERROR, "adrenotools", ...)` at every `return nullptr` (linkernsbypass load status, param checks, stat of driver file, android_create_namespace, link_namespace_to_default_all_libs, libhook_impl.so dlopen, init_hook_param dlsym, libmain_hook.so dlopen, patched libvulkan memfd dlopen) + a SUCCESS line.
  - `thirdparty/adrenotools/linkernsbypass/android_linker_ns.cpp`: logs every `resolve_linker_symbols()` constructor failure (API<28, ld-android.so open, each dlsym) and success.
  - Added `log` to the link libraries of `adrenotools`, `linkernsbypass`, and `rexcore` (for the spdlog android_sink).
- Also added a logcat sink to rex logging on Android: `logging.cpp` uses `spdlog::sinks::android_sink_mt("ReDAHM")` for the console sink, and `rex_app.cpp` sets `log_config.log_to_console = true` on Android - so all game logs now appear in logcat too (no more file-only logs for adb debugging).
- Verified locally: full NDK arm64 build green; librexruntime.so contains all new "adrenotools"/"open_libvulkan" log strings.
- Next on-device: with the new APK, launch once with Turnip selected and grab logcat filtered to `adb logcat -s adrenotools ReDAHM:*` (plus the game log file). The exact failing step will be identified.
