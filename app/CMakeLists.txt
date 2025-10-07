cmake_minimum_required(VERSION 3.18.1)
project(native_lib)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(native-lib SHARED src/main/cpp/native-lib.cpp)
find_library(log-lib log)
target_link_libraries(native-lib ${log-lib})
