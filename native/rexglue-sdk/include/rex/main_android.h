#pragma once
/**
 * Android platform init and JNI bridge.
 *
 * The Java launcher (MainActivity / SDLActivity) calls into the native bridge
 * before SDL_main runs so that the runtime knows where the app's storage
 * directories are and how to reach the Java application context.
 */

#include <jni.h>

#include <string>

namespace rex {

// Stores the global JavaVM so background threads can attach for JNI calls.
void InitializeAndroidJavaVM(JavaVM* java_vm);

// Called by the Java layer before SDL_main with the app's storage paths.
void SetAndroidAppPaths(const std::string& native_library_dir,
                        const std::string& internal_data_dir,
                        const std::string& external_files_dir,
                        const std::string& external_storage_dir,
                        const std::string& cache_dir);

// The rexglue app identifier to run (e.g. "redahm"), as selected by the Java
// layer. Used by the SDL_main entry to look up the registered app creator.
void SetAndroidAppName(const std::string& app_name);
const std::string& GetAndroidAppName();

// Runtime device API level (e.g. 33 for Android 13).
int GetAndroidApiLevel();

// Accessors for the paths set by SetAndroidAppPaths.
const std::string& GetAndroidNativeLibraryDir();
const std::string& GetAndroidInternalDataDir();
const std::string& GetAndroidExternalFilesDir();
const std::string& GetAndroidExternalStorageDir();
const std::string& GetAndroidCacheDir();

// Java application context, usable by any thread (returns a global reference
// cast to void*, or nullptr if the JVM was not initialized).
void* GetAndroidApplicationContext();

}  // namespace rex
