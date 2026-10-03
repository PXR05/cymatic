cmake_minimum_required(VERSION 3.22.1)
project(cymatic_alac LANGUAGES C CXX)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(APPLE_ALAC "${CMAKE_CURRENT_SOURCE_DIR}/../../../third_party/apple")
add_library(apple_alac STATIC
    "${APPLE_ALAC}/ALACDecoder.cpp"
    "${APPLE_ALAC}/ALACBitUtilities.c"
    "${APPLE_ALAC}/ag_dec.c"
    "${APPLE_ALAC}/dp_dec.c"
    "${APPLE_ALAC}/matrix_dec.c"
    "${APPLE_ALAC}/EndianPortable.c")
target_include_directories(apple_alac PUBLIC "${APPLE_ALAC}")
target_compile_definitions(apple_alac PRIVATE TARGET_RT_LITTLE_ENDIAN=1)
target_compile_options(apple_alac PRIVATE -fwrapv -fno-strict-aliasing)

add_library(cymatic_alac SHARED AlacFrameDecoder.cpp)
target_compile_features(cymatic_alac PRIVATE cxx_std_17)
target_compile_options(cymatic_alac PRIVATE -Wall -Wextra -Werror)
target_link_libraries(cymatic_alac PRIVATE apple_alac)
target_link_options(cymatic_alac PRIVATE -Wl,--exclude-libs,ALL)
