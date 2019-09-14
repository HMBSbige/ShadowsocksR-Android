cmake_minimum_required(VERSION 3.4.1)

include_directories(src/main/cpp/cpufeatures)
include_directories(src/main/cpp/ancillary)

find_library( log-lib
              log )

add_library( system
             SHARED
             src/main/cpp/system.cpp )

add_library( cpufeatures
             SHARED
             src/main/cpp/cpufeatures/cpu-features.c )

add_library( ancillary
             SHARED
             src/main/cpp/ancillary/fd_recv.c
             src/main/cpp/ancillary/fd_send.c )

target_link_libraries( system
                       cpufeatures
                       ancillary
                       ${log-lib})