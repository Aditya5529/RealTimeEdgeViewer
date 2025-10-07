cmake_minimum_required(VERSION 3.22.1)
project(real_time_edge_viewer)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# --- Native sources
add_library(native-lib SHARED
        src/main/cpp/native-lib.cpp
)

# --- OpenCV headers
target_include_directories(native-lib PRIVATE
        ${CMAKE_SOURCE_DIR}/src/main/cpp/third_party/opencv4
)

# --- Import prebuilt OpenCV .so from jniLibs
add_library(opencv_java4 SHARED IMPORTED GLOBAL)
set_target_properties(opencv_java4 PROPERTIES
        IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/src/main/jniLibs/${ANDROID_ABI}/libopencv_java4.so
)

# --- NDK libs
find_library(log-lib log)

# --- Link
target_link_libraries(native-lib
        opencv_java4
        ${log-lib}
)
