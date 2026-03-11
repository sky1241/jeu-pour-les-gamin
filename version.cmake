# This generated file provides version information.
# See mk/cmake/SuperTux/BuildVersion.cmake

set(SUPERTUX_PACKAGE_VERSION_TAG "GIT_TAG-NOTFOUND")
if(OFF)
  set(SUPERTUX_PACKAGE_VERSION "GIT_TAG-NOTFOUND")
  set(SUPERTUX_VERSION_STRING "GIT_TAG-NOTFOUND")
else()
  if(NOT 128 EQUAL 0)
    set(SUPERTUX_PACKAGE_VERSION "dev 0aa26bf (main)")
  else()
    set(SUPERTUX_PACKAGE_VERSION "dev GIT_TAG-NOTFOUND - 0aa26bf (main)")
  endif()
  set(SUPERTUX_VERSION_STRING "main-0aa26bf")
endif()
