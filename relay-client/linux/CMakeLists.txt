cmake_minimum_required(VERSION 3.10)
project(relay-client VERSION 0.1.0 LANGUAGES C)

set(CMAKE_C_STANDARD 11)

add_executable(relay-client
    main.c
    ../common/protocol.c
    ../common/tcp_transport.c
)

target_include_directories(relay-client PRIVATE ../common)
target_link_libraries(relay-client PRIVATE pthread)

install(TARGETS relay-client RUNTIME DESTINATION bin)
