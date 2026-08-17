/**
 * @file        src/android_bridge.cpp
 * @brief       JNI bridge between GameActivity (Java) and the rex runtime.
 *
 * Called once from GameActivity.onCreate() after SDL3/libmain.so are loaded
 * (but before the SDL main thread starts). It hands the app's storage paths
 * to the runtime so GetAndroidNativeLibraryDir() / GetAndroidExternalFilesDir()
 * resolve to real directories (the GPU plugin is loaded from the native
 * library dir, and user data lives in app external storage).
 */

#include <jni.h>

#include <filesystem>
#include <string>

#include <rex/main_android.h>

extern "C" JNIEXPORT void JNICALL
Java_io_redahm_android_GameActivity_setupNativePaths(JNIEnv* env, jobject thiz) {
  // Make the JavaVM available to native threads regardless of whether
  // librexruntime.so's JNI_OnLoad ran (dependency libraries are not notified
  // of load events by the VM).
  JavaVM* vm = nullptr;
  env->GetJavaVM(&vm);
  if (vm) {
    rex::InitializeAndroidJavaVM(vm);
  }

  jclass activity_cls = env->GetObjectClass(thiz);
  if (!activity_cls) {
    return;
  }

  jmethodID get_app_info =
      env->GetMethodID(activity_cls, "getApplicationInfo", "()Landroid/content/pm/ApplicationInfo;");
  jclass ai_cls = env->FindClass("android/content/pm/ApplicationInfo");
  jfieldID native_dir = env->GetFieldID(ai_cls, "nativeLibraryDir", "Ljava/lang/String;");

  jmethodID get_files = env->GetMethodID(activity_cls, "getFilesDir", "()Ljava/io/File;");
  jmethodID get_ext_files =
      env->GetMethodID(activity_cls, "getExternalFilesDir", "(Ljava/lang/String;)Ljava/io/File;");
  jmethodID get_cache = env->GetMethodID(activity_cls, "getCacheDir", "()Ljava/io/File;");
  jclass env_cls = env->FindClass("android/os/Environment");
  jmethodID get_ext_storage = env->GetStaticMethodID(env_cls, "getExternalStorageDirectory",
                                                     "()Ljava/io/File;");
  jclass file_cls = env->FindClass("java/io/File");
  jmethodID file_to_string = env->GetMethodID(file_cls, "getAbsolutePath", "()Ljava/lang/String;");

  auto str_from_jstring = [env](jstring s) -> std::string {
    if (!s) {
      return {};
    }
    const char* c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) {
      env->ReleaseStringUTFChars(s, c);
    }
    env->DeleteLocalRef(s);
    return out;
  };
  auto str_from_file = [env, file_to_string, &str_from_jstring](jobject file) -> std::string {
    if (!file) {
      return {};
    }
    jstring s = static_cast<jstring>(env->CallObjectMethod(file, file_to_string));
    env->DeleteLocalRef(file);
    return str_from_jstring(s);
  };

  std::string native_library_dir;
  jobject app_info = env->CallObjectMethod(thiz, get_app_info);
  if (app_info) {
    jstring s = static_cast<jstring>(env->GetObjectField(app_info, native_dir));
    env->DeleteLocalRef(app_info);
    native_library_dir = str_from_jstring(s);
  }

  // ApplicationInfo.nativeLibraryDir points at the base native lib dir
  // (e.g. /data/app/<pkg>/lib); the actual .so files are in the ABI subdir
  // below it (e.g. .../lib/arm64). Resolve that subdir by looking for a
  // directory that contains libSDL3.so (SDLActivity loads it first).
  {
    std::filesystem::path base(native_library_dir);
    std::error_code ec;
    std::filesystem::directory_iterator it(base, ec);
    for (; !ec && it != std::filesystem::directory_iterator(); it.increment(ec)) {
      const auto& entry = *it;
      if (entry.is_directory() &&
          std::filesystem::exists(entry.path() / "libSDL3.so", ec)) {
        native_library_dir = entry.path().string();
        break;
      }
    }
  }

  std::string internal_data_dir = str_from_file(env->CallObjectMethod(thiz, get_files));
  std::string external_files_dir = str_from_file(env->CallObjectMethod(thiz, get_ext_files, nullptr));
  std::string external_storage_dir =
      str_from_file(env->CallStaticObjectMethod(env_cls, get_ext_storage));
  std::string cache_dir = str_from_file(env->CallObjectMethod(thiz, get_cache));

  rex::SetAndroidAppPaths(native_library_dir, internal_data_dir, external_files_dir,
                          external_storage_dir, cache_dir);
  rex::SetAndroidAppName("redahm");
}