cmake_minimum_required(VERSION 3.10)
project(virtual-usb-test VERSION 0.1.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_executable(virtual-usb-test
    src/main.cpp
)

if(WIN32)
    target_link_libraries(virtual-usb-test PRIVATE setupapi hid)
endif()

if(MSVC)
    set_property(TARGET virtual-usb-test PROPERTY MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>")
endif()

install(TARGETS virtual-usb-test RUNTIME DESTINATION bin)
