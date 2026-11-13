cmake_minimum_required(VERSION 3.22.1)

project("sroboe")

find_package(oboe REQUIRED CONFIG)

add_library(terminal_logger SHARED

    ${CMAKE_CURRENT_SOURCE_DIR}/terminal_logger.cpp

)

target_link_libraries(terminal_logger

    PRIVATE

    log

)

add_library(audio SHARED

    ${CMAKE_CURRENT_SOURCE_DIR}/sroboe.cpp

)

target_include_directories(audio

    PRIVATE

    ${CMAKE_CURRENT_SOURCE_DIR}

)

target_link_libraries(audio

    PRIVATE

    oboe::oboe

    log

    OpenSLES

    terminal_logger

)

add_library(usb_audio SHARED

    ${CMAKE_CURRENT_SOURCE_DIR}/usb_audio/usb-audio-output.cpp

)

target_include_directories(usb_audio

    PRIVATE

    ${CMAKE_CURRENT_SOURCE_DIR}

)

target_link_libraries(usb_audio

    PRIVATE

    log

    terminal_logger

)

add_library(dsd SHARED

    ${CMAKE_CURRENT_SOURCE_DIR}/dsd/dsd_engine.cpp

)

target_include_directories(dsd

    PRIVATE

    ${CMAKE_CURRENT_SOURCE_DIR}

)

target_link_libraries(dsd

    PRIVATE

    log

    terminal_logger

)
