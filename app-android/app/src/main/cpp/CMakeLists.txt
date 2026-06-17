cmake_minimum_required(VERSION 3.22.1)

project(kraken_native_placeholder)

add_library(
    kraken_native_placeholder
    SHARED
    kraken_native_placeholder.cpp
)

if(ANDROID)
    target_link_options(
        kraken_native_placeholder
        PRIVATE
        "-Wl,-z,max-page-size=16384"
    )
endif()
