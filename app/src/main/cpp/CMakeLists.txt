cmake_minimum_required(VERSION 3.22.1)

project(imageloader)

add_library(
    imageloader
    SHARED
    src/main/cpp/imageloader.cpp
)

include_directories(src/main/cpp/include)

find_library(
    log-lib
    log
)

target_link_libraries(
    imageloader
    ${log-lib}
)
