cmake_minimum_required(VERSION 3.22)
project(OspfCLib C)

set(CMAKE_C_STANDARD 99)

add_library(OspfCLib SHARED library.c)
