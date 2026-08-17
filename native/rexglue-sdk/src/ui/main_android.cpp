/**
 * Android platform init and JNI bridge.
 *
 * Coordinates the app-side storage paths and the runtime's Android helpers
 * (filesystem, thread, memory) with the JVM so that guest code can reach the
 * Java application context when needed (e.g. content URI resolution).
 */

#include <rex/main_android.h>

#include <android/api-level.h>
#include <jni.h>

#include <atomic>
#include <mutex>
#include <string>

#include <rex/filesystem.h>
#include <rex/logging.h>
#include <rex/memory/utils.h>
#include <rex/platform.h>
#include <rex/system.h>
#include <rex/thread.h>

namespace {

JavaVM* g_java_vm = nullptr;
// Global reference to the Android application context (MainActivity).
jobject g_application_context = nullptr;
std::once_flag g_jvm_once;

std::mutex g_paths_mutex;
std::string g_native_library_dir;
std::string g_internal_data_dir;
std::string g_external_files_dir;
std::string g_external_storage_dir;
std::string g_cache_dir;
std::string g_app_name;
std::string g_vulkan_driver_dir;
std::string g_vulkan_driver_name;

}  // namespace

namespace rex {

void InitializeAndroidJavaVM(JavaVM* java_vm) {
  g_java_vm = java_vm;
}

void SetAndroidAppName(const std::string& app_name) {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  g_app_name = app_name;
}

const std::string& GetAndroidAppName() {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  return g_app_name;
}

void SetAndroidAppPaths(const std::string& native_library_dir,
                        const std::string& internal_data_dir,
                        const std::string& external_files_dir,
                        const std::string& external_storage_dir,
                        const std::string& cache_dir) {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  g_native_library_dir = native_library_dir;
  g_internal_data_dir = internal_data_dir;
  g_external_files_dir = external_files_dir;
  g_external_storage_dir = external_storage_dir;
  g_cache_dir = cache_dir;
}

int GetAndroidApiLevel() {
  // Available from NDK r18 (API level 24 runtime lib). Falls back to 0 if the
  // symbol is missing, in which case callers treat it as "pre-26".
  static int api_level = android_get_device_api_level();
  return api_level;
}

const std::string& GetAndroidNativeLibraryDir() {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  return g_native_library_dir;
}

const std::string& GetAndroidInternalDataDir() {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  return g_internal_data_dir;
}

const std::string& GetAndroidExternalFilesDir() {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  return g_external_files_dir;
}

const std::string& GetAndroidExternalStorageDir() {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  return g_external_storage_dir;
}

const std::string& GetAndroidCacheDir() {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  return g_cache_dir;
}

void SetAndroidVulkanDriver(const std::string& driver_dir, const std::string& driver_name) {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  g_vulkan_driver_dir = driver_dir;
  g_vulkan_driver_name = driver_name;
}

void GetAndroidVulkanDriver(std::string* driver_dir, std::string* driver_name) {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  if (driver_dir) {
    *driver_dir = g_vulkan_driver_dir;
  }
  if (driver_name) {
    *driver_name = g_vulkan_driver_name;
  }
}

void* GetAndroidApplicationContext() {
  return reinterpret_cast<void*>(g_application_context);
}

namespace filesystem {

void AndroidInitialize() {
  REXLOG_INFO("filesystem: Android initialization (API level {})", rex::GetAndroidApiLevel());
}

void AndroidShutdown() {
  std::lock_guard<std::mutex> lock(g_paths_mutex);
  g_native_library_dir.clear();
  g_internal_data_dir.clear();
  g_external_files_dir.clear();
  g_external_storage_dir.clear();
  g_cache_dir.clear();
  if (g_application_context) {
    if (g_java_vm) {
      JNIEnv* env = nullptr;
      g_java_vm->AttachCurrentThread(&env, nullptr);
      if (env) {
        env->DeleteGlobalRef(g_application_context);
        g_application_context = nullptr;
      }
    }
  }
}

bool IsAndroidContentUri(const std::string_view source) {
  return source.starts_with("content://");
}

int OpenAndroidContentFileDescriptor(const std::string_view uri, const char* mode) {
  if (!g_java_vm) {
    REXLOG_ERROR("OpenAndroidContentFileDescriptor: no JavaVM initialized");
    return -1;
  }
  JNIEnv* env = nullptr;
  if (g_java_vm->AttachCurrentThread(&env, nullptr) != JNI_OK || !env) {
    REXLOG_ERROR("OpenAndroidContentFileDescriptor: failed to attach to JVM");
    return -1;
  }
  if (!g_application_context) {
    REXLOG_ERROR("OpenAndroidContentFileDescriptor: no application context");
    return -1;
  }

  jclass context_class = env->FindClass("android/content/Context");
  if (!context_class) {
    REXLOG_ERROR("OpenAndroidContentFileDescriptor: Context class not found");
    return -1;
  }
  jmethodID content_resolver_method =
      env->GetMethodID(context_class, "getContentResolver", "()Landroid/content/ContentResolver;");
  if (!content_resolver_method) {
    REXLOG_ERROR("OpenAndroidContentFileDescriptor: getContentResolver not found");
    return -1;
  }
  jobject content_resolver =
      env->CallObjectMethod(g_application_context, content_resolver_method);
  if (!content_resolver) {
    REXLOG_ERROR("OpenAndroidContentFileDescriptor: content resolver is null");
    return -1;
  }

  jclass cr_class = env->FindClass("android/content/ContentResolver");
  jmethodID open_fd_method = env->GetMethodID(
      cr_class, "openFileDescriptor",
      "(Landroid/net/Uri;Ljava/lang/String;)Landroid/os/ParcelFileDescriptor;");
  if (!open_fd_method) {
    REXLOG_ERROR("OpenAndroidContentFileDescriptor: openFileDescriptor not found");
    return -1;
  }

  jclass uri_class = env->FindClass("android/net/Uri");
  jmethodID parse_method = env->GetStaticMethodID(uri_class, "parse",
                                                  "(Ljava/lang/String;)Landroid/net/Uri;");
  jstring uri_jstr = env->NewStringUTF(std::string(uri).c_str());
  jobject uri_obj = env->CallStaticObjectMethod(uri_class, parse_method, uri_jstr);

  jstring mode_jstr = env->NewStringUTF(mode);
  jobject pfd = env->CallObjectMethod(content_resolver, open_fd_method, uri_obj, mode_jstr);

  int fd = -1;
  if (pfd) {
    jclass pfd_class = env->FindClass("android/os/ParcelFileDescriptor");
    jmethodID detach_fd = env->GetMethodID(pfd_class, "detachFd", "()I");
    fd = env->CallIntMethod(pfd, detach_fd);
  } else {
    REXLOG_ERROR("OpenAndroidContentFileDescriptor: openFileDescriptor returned null");
  }

  env->DeleteLocalRef(mode_jstr);
  env->DeleteLocalRef(uri_jstr);
  env->DeleteLocalRef(pfd);

  g_java_vm->DetachCurrentThread();
  return fd;
}

}  // namespace filesystem

namespace system {

bool InitializeAndroidSystemForApplicationContext() {
  rex::thread::AndroidInitialize();
  rex::memory::AndroidInitialize();
  rex::filesystem::AndroidInitialize();
  return true;
}

void ShutdownAndroidSystem() {
  rex::filesystem::AndroidShutdown();
  rex::memory::AndroidShutdown();
  rex::thread::AndroidShutdown();
}

}  // namespace system

}  // namespace rex

// Expose a JNI_OnLoad so the JVM gives us the JavaVM pointer early. Because
// SDL3 also defines JNI_OnLoad in libSDL3.so, this one is harmless here (the
// linker resolves each library's own JNI_OnLoad).
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* java_vm, void* reserved) {
  (void)reserved;
  rex::InitializeAndroidJavaVM(java_vm);
  return JNI_VERSION_1_6;
}
