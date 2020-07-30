package com.mivik.kamet

internal fun Char.description(): String = "$this (0x${toShort().toString(16)})"

internal fun unreachable(): Nothing = error("Unreachable code reached!")