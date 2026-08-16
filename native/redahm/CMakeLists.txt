# redahm - ReXGlue Recompiled Project
#
# This file is yours to edit. 'rexglue migrate' will NOT overwrite it.
# SDK boilerplate lives in generated/rexglue.cmake.

cmake_minimum_required(VERSION 3.25)
project(redahm LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 23)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(generated/rexglue.cmake)

# Sources
set(REDAHM_SOURCES
    src/main.cpp
    src/redahm_engine/hooks.cpp
    src/redahm_engine/dlc_unlock.cpp
    src/redahm_engine/console_exec.cpp
    src/redahm_engine/movie_sequence.cpp
    src/redahm_engine/framerate.cpp
)

if(WIN32)
    list(APPEND REDAHM_SOURCES
        src/redahm_engine/audio/xaudio2_audio_system.cpp
        src/redahm_engine/audio/xaudio2_audio_driver.cpp
    )
endif()

if(ANDROID)
    # Android loads the game as a shared library (libmain.so) that exports
    # SDL_main; the SDL3 Java layer (SDLActivity) drives it.
    add_library(main SHARED ${REDAHM_SOURCES})
    set_target_properties(main PROPERTIES
        OUTPUT_NAME "main"
        PREFIX "lib"
        CXX_VISIBILITY_PRESET default
    )
    target_compile_definitions(main PRIVATE SDL_MAIN_HANDLED)
    rexglue_setup_target(main GPU_PLUGINS xenos)
elseif(WIN32)
    add_executable(redahm WIN32 ${REDAHM_SOURCES})
    # SDK 0.9.0 moved Xenos GPU emulation out of the runtime into a plugin DLL
    # that is loaded at startup from the executable folder, so it has to be
    # staged here.
    rexglue_setup_target(redahm GPU_PLUGINS xenos)
    target_link_libraries(redahm PRIVATE xaudio2.lib)
else()
    add_executable(redahm ${REDAHM_SOURCES})
    rexglue_setup_target(redahm GPU_PLUGINS xenos)
endif()