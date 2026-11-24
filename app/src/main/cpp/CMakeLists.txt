cmake_minimum_required(VERSION 3.22.1)
project(nanaya C)


# Termux terminal-emulator JNI (Apache 2.0). The Java side calls
# System.loadLibrary("termux") from com.termux.terminal.JNI.
add_library(termux SHARED termux.c)
