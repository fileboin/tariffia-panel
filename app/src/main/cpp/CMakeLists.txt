cmake_minimum_required(VERSION 3.22.1)
project(nodepoc CXX)

# Node 24 V8 headers require C++20 (v8config.h: "C++20 or later required.").
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# CI stages the prebuilt fogtape libnode.so at app/src/main/jniLibs/<abi>/libnode.so
set(JNI_LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs)

add_library(node SHARED IMPORTED)
set_target_properties(node PROPERTIES
    IMPORTED_LOCATION ${JNI_LIBS_DIR}/${ANDROID_ABI}/libnode.so)

# CI stages the Node headers at app/src/main/cpp/include/node/*.h
# Node/V8 headers include each other by bare name (e.g. "v8config.h"), so the
# include directory must be the node/ directory itself.
add_library(nodepoc SHARED nodepoc_bridge.cpp)
target_include_directories(nodepoc PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/include/node)

target_link_libraries(nodepoc node android log)

# 16 KB page-size compatible shared object.
target_link_options(nodepoc PRIVATE "-Wl,-z,max-page-size=16384")
