/**
 * @file        src/ui/vulkan/vulkan_custom_driver_android.cpp
 * @brief       Android-only loader for user-imported custom Vulkan GPU drivers.
 *
 * When the Java launcher has imported a custom Adreno driver (e.g. Mesa Turnip
 * shipped as an AdrenoTools-style package), it hands the driver folder + soname
 * to the runtime. Here we replace the system libvulkan.so loader with that
 * driver via libadrenotools, which creates an isolated linker namespace rooted
 * at the driver folder so the driver's dependencies (libvulkan_freedreno.so)
 * resolve, then dlopens it and returns the standard Vulkan loader entry points.
 *
 * The hook shared libraries (libhook_impl.so, libmain_hook.so, ...) must live
 * in the app's native library dir (getApplicationInfo().nativeLibraryDir) and
 * the app must be packaged with useLegacyPackaging=true, both of which the
 * build already does.
 */

#if REX_PLATFORM_ANDROID

#include <dlfcn.h>

#include <string>

#include <adrenotools/driver.h>

#include <rex/logging.h>
#include <rex/main_android.h>
#include <rex/ui/vulkan/instance.h>

namespace rex {
namespace ui {
namespace vulkan {

bool LoadCustomVulkanDriverOnAndroid(const std::string& driver_dir,
                                     const std::string& driver_name,
                                     PFN_vkGetInstanceProcAddr* out_vk_get_instance_proc_addr) {
  // libadrenotools concatenates customDriverDir + customDriverName directly (no
  // separator), so the dir must end with a '/'.
  std::string driver_dir_with_slash = driver_dir;
  if (driver_dir_with_slash.empty() || driver_dir_with_slash.back() != '/')
    driver_dir_with_slash += '/';

  void* handle = adrenotools_open_libvulkan(
      RTLD_NOW,
      ADRENOTOOLS_DRIVER_CUSTOM,
      rex::GetAndroidCacheDir().c_str(),             // tmpLibDir (memfd on API >= 29)
      rex::GetAndroidNativeLibraryDir().c_str(),     // hookLibDir (must be nativeLibraryDir)
      driver_dir_with_slash.c_str(),                 // customDriverDir
      driver_name.c_str(),                           // customDriverName
      nullptr,                                       // fileRedirectDir
      nullptr);                                      // userMappingHandle
  if (!handle) {
    REXLOG_ERROR("Failed to open custom Vulkan driver '{}' from {}", driver_name, driver_dir);
    return false;
  }

  *out_vk_get_instance_proc_addr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
      dlsym(handle, "vkGetInstanceProcAddr"));
  if (!*out_vk_get_instance_proc_addr) {
    REXLOG_ERROR("Custom Vulkan driver '{}' does not export vkGetInstanceProcAddr", driver_name);
    return false;
  }

  REXLOG_INFO("Loaded custom Vulkan driver '{}' from {}", driver_name, driver_dir);
  return true;
}

}  // namespace vulkan
}  // namespace ui
}  // namespace rex

#endif  // REX_PLATFORM_ANDROID