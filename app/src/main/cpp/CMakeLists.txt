cmake_minimum_required(VERSION 3.22.1)

project(imageloader)

add_library(
    imageloader
    SHARED
    imageloader.cpp
)

include_directories(include)

find_library(
    log-lib
    log
)

target_link_libraries(
    imageloader
    ${log-lib}
)
