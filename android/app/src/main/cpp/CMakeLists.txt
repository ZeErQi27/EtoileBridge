cmake_minimum_required(VERSION 3.22.1)
project(etoile_lite_android C)

if(ANDROID_ABI STREQUAL "arm64-v8a")
  set(ETOILE_LITE_RID linux-bionic-arm64)
elseif(ANDROID_ABI STREQUAL "x86_64")
  set(ETOILE_LITE_RID linux-bionic-x64)
else()
  message(FATAL_ERROR "Etoile.Lite NativeAOT is unavailable for ${ANDROID_ABI}")
endif()

set(DOTNET "$ENV{ETOILEBRIDGE_DOTNET}")
if(NOT DOTNET)
  find_program(DOTNET_COMMAND dotnet NO_CMAKE_FIND_ROOT_PATH)
  set(DOTNET "${DOTNET_COMMAND}")
endif()
if(NOT DOTNET)
  message(FATAL_ERROR "Install .NET 10.0.404+ or set ETOILEBRIDGE_DOTNET.")
endif()

get_filename_component(LITE_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../native/etoile-lite-adapter" ABSOLUTE)
get_filename_component(NDK_BIN "${CMAKE_C_COMPILER}" DIRECTORY)
if(CMAKE_HOST_WIN32)
  set(NDK_PATH "${NDK_BIN};$ENV{PATH}")
else()
  set(NDK_PATH "${NDK_BIN}:$ENV{PATH}")
endif()
set(LITE_OUTPUT "${CMAKE_CURRENT_BINARY_DIR}/lite")
set(LITE_LIBRARY "${LITE_OUTPUT}/libetoile_lite.so")

add_custom_command(
  OUTPUT "${LITE_LIBRARY}"
  COMMAND "${CMAKE_COMMAND}" -E env "PATH=${NDK_PATH}"
    "${DOTNET}" publish "${LITE_SOURCE}/EtoileBridge.Lite.csproj"
    -c Release -r "${ETOILE_LITE_RID}"
    --artifacts-path "${CMAKE_CURRENT_BINARY_DIR}/dotnet"
    -o "${LITE_OUTPUT}"
  DEPENDS "${LITE_SOURCE}/EtoileBridge.Lite.csproj" "${LITE_SOURCE}/global.json"
    "${LITE_SOURCE}/ChartConversion.cs" "${LITE_SOURCE}/AndroidExports.cs"
  WORKING_DIRECTORY "${LITE_SOURCE}"
  VERBATIM)
add_custom_target(build_etoile_lite DEPENDS "${LITE_LIBRARY}")
add_library(etoile_lite SHARED IMPORTED)
set_target_properties(etoile_lite PROPERTIES IMPORTED_LOCATION "${LITE_LIBRARY}")
add_dependencies(etoile_lite build_etoile_lite)

add_library(etoile_lite_jni SHARED etoile_lite_jni.c)
target_link_libraries(etoile_lite_jni PRIVATE etoile_lite)
