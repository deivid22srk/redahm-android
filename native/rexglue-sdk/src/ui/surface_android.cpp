/**
 * Android native-window surface implementation.
 */

#include <rex/ui/surface_android.h>

#include <android/native_window.h>

namespace rex {
namespace ui {

bool AndroidNativeWindowSurface::GetSizeImpl(uint32_t& width_out,
                                             uint32_t& height_out) const {
  if (!window_) {
    width_out = 0;
    height_out = 0;
    return false;
  }
  width_out = static_cast<uint32_t>(ANativeWindow_getWidth(window_));
  height_out = static_cast<uint32_t>(ANativeWindow_getHeight(window_));
  return true;
}

}  // namespace ui
}  // namespace rex
