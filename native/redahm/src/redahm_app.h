
// redahm - ReXGlue Recompiled Project
//
// This file is yours to edit. 'rexglue migrate' will NOT overwrite it.
// Customize your app by overriding virtual hooks from rex::ReXApp.

#pragma once

#include <rex/platform.h>

#include <rex/rex_app.h>
#include "redahm_engine/path_setup_wizard.h"
#include "redahm_engine/redahm_logging.h"
#include <rex/runtime.h>
#include "redahm_engine/overlays/redahm_logging_overlay.h"
#if REX_PLATFORM_WIN32
#include "redahm_engine/audio/xaudio2_audio_system.h"
#endif
#include "redahm_engine/overlays/fps_overlay.h"
#include "redahm_engine/overlays/debug_command_overlay.h"

class RedahmApp : public rex::ReXApp {
 public:
  using rex::ReXApp::ReXApp;

  static std::unique_ptr<rex::ui::WindowedApp> Create(
      rex::ui::WindowedAppContext& ctx) {
    return std::unique_ptr<RedahmApp>(new RedahmApp(ctx, "redahm",
        PPCImageConfig));
  }


  void OnPreSetup(rex::RuntimeConfig& config) override {
      // SDK 0.9.0 moved Xenos emulation into a runtime-loaded plugin and
      // defaults the selection to none. No plugin means no presenter, which
      // means no ImGui overlays, which means no path wizard. Default to xenos
      // so the game data root selector runs; the gpu_plugin cvar still wins.
      if (config.gpu_plugin.empty()) {
          config.gpu_plugin = "xenos";
      }

#if REX_PLATFORM_WIN32
      config.audio_factory = [](rex::runtime::FunctionDispatcher* fd) {
          return xaudio2::XAudio2AudioSystem::Create(fd);
          };
#endif
  }


  void OnCreateDialogs(rex::ui::ImGuiDrawer* drawer) override {
      drawer->AddDialog(new REDAHMLogOverlayDialog(drawer));
      drawer->AddDialog(new DebugCommandOverlayDialog(drawer));
      path_wizard_ = new PathSetupWizard(drawer);
      drawer->AddDialog(path_wizard_);
      auto* fps = new FpsOverlayDialog(drawer);
      drawer->AddDialog(fps);
      g_fps_overlay = fps;
  }
  std::optional<rex::PathConfig> OnFinalizePaths(
      const rex::PathConfig& defaults,
      std::function<void(rex::PathConfig)> resume) override
  {
#if REX_PLATFORM_ANDROID
      // On Android the game/user roots come from the Java launcher via
      // command-line cvars (--game_data_root / --user_data_root); there is no
      // desktop-style dialog flow, so resolve from the defaults directly.
      RDAHM_INFO("Android: resolving paths from defaults (game_data_root={})",
                 defaults.game_data_root.string());
      return defaults;
#else
      // OnCreateDialogs only runs once the overlays exist, and those are only
      // built when there is a presenter (a GPU plugin) or an app-supplied
      // immediate drawer. With neither, there is no wizard to drive, so
      // resolve from the defaults rather than dereference a null dialog.
      if (!path_wizard_) {
          RDAHM_ERROR("Path wizard was never created (no GPU plugin loaded, so no "
                      "overlays); falling back to default paths without prompting");
          return defaults;
      }
      path_wizard_->Init(GetName(), defaults, [resume](rex::PathConfig resolved) {
          resume(resolved);
          });
      return std::nullopt;
#endif
  }

private:
    PathSetupWizard* path_wizard_ = nullptr;
};

