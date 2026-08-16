#pragma once
/**
 * Android native-window surface.
 *
 * Provides the ANativeWindow backing store for the Vulkan presenter on
 * Android. The ANativeWindow is obtained from SDL3 via the window's
 * SDL_PROP_WINDOW_ANDROID_WINDOW_POINTER property.
 */

#include <cstdint>

#include <android/native_window.h>

#include <rex/ui/surface.h>

namespace rex {
namespace ui {

class AndroidNativeWindowSurface final : public Surface {
 public:
  explicit AndroidNativeWindowSurface(ANativeWindow* window) : window_(window) {}
  TypeIndex GetType() const override { return kTypeIndex_AndroidNativeWindow; }
  ANativeWindow* window() const { return window_; }

 protected:
  bool GetSizeImpl(uint32_t& width_out, uint32_t& height_out) const override;

 private:
  ANativeWindow* window_;
};

}  // namespace ui
}  // namespace rex
