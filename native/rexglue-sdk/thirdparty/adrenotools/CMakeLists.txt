# libadrenotools (vendored) - rootless custom Adreno GPU driver loading.
# Upstream: https://github.com/bylaws/libadrenotools (BSD-2-Clause)
# Depends on liblinkernsbypass (https://github.com/bylaws/liblinkernsbypass, BSD-2-Clause).
#
# Only meaningful on Android arm64: it lets the app load a user-imported Vulkan
# driver (e.g. Mesa Turnip) instead of the system libvulkan.so. The hook shared
# libraries are dlopened at runtime from the app's nativeLibraryDir, so they are
# built standalone (never linked) and staged into the APK like the other .so
# files by build-android.sh.
if(NOT ANDROID)
    return()
endif()

project(adrenotools LANGUAGES CXX C)

#=============================================================================
# linkernsbypass - bypasses the linker namespace so the custom driver can be
# loaded from the app's internal storage.
#=============================================================================
add_library(linkernsbypass STATIC
    linkernsbypass/android_linker_ns.cpp
    linkernsbypass/android_linker_ns.h
    linkernsbypass/elf_soname_patcher.cpp
    linkernsbypass/elf_soname_patcher.h
)
target_compile_options(linkernsbypass PRIVATE -Wall -Wextra)
target_link_libraries(linkernsbypass android dl log)
target_include_directories(linkernsbypass PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/linkernsbypass)

#=============================================================================
# adrenotools - opens a (possibly custom) libvulkan instance.
#=============================================================================
add_library(adrenotools
    src/bcenabler.cpp
    src/driver.cpp
    include/adrenotools/bcenabler.h
    include/adrenotools/driver.h
    include/adrenotools/priv.h
)
target_include_directories(adrenotools PUBLIC
    ${CMAKE_CURRENT_SOURCE_DIR}/include)
target_include_directories(adrenotools PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR})  # For gen/ and src/hook/ relative includes
target_compile_options(adrenotools PRIVATE -Wall -Wextra)
target_link_libraries(adrenotools android linkernsbypass log)

#=============================================================================
# Hook shared libraries (dlopened by adrenotools from the native library dir).
#=============================================================================
set(ADRENOTOOLS_HOOK_DIR ${CMAKE_CURRENT_SOURCE_DIR}/src/hook)

add_library(hook_impl SHARED
    ${ADRENOTOOLS_HOOK_DIR}/hook_impl.cpp
    ${ADRENOTOOLS_HOOK_DIR}/hook_impl.h
    ${ADRENOTOOLS_HOOK_DIR}/hook_impl_params.h
)
target_compile_options(hook_impl PRIVATE -Wall -Wextra)
target_link_libraries(hook_impl linkernsbypass log)
target_include_directories(hook_impl PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR})
set_target_properties(hook_impl PROPERTIES CXX_VISIBILITY_PRESET hidden)

foreach(_hook
        main_hook
        file_redirect_hook
        gsl_alloc_hook)
    add_library(${_hook} SHARED ${ADRENOTOOLS_HOOK_DIR}/${_hook}.c)
    target_compile_options(${_hook} PRIVATE -Wall -Wextra)
    target_link_options(${_hook} PRIVATE -z global)
    target_link_libraries(${_hook} hook_impl)
    set_target_properties(${_hook} PROPERTIES CXX_VISIBILITY_PRESET hidden)
    unset(_hook)
endforeach()