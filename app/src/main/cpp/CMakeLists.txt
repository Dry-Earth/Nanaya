cmake_minimum_required(VERSION 3.22.1)
project(ptybridge C)

add_library(ptybridge SHARED pty_bridge.c)
target_compile_options(ptybridge PRIVATE -Wall -Wextra -O2)
