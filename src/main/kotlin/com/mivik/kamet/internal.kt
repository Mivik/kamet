package com.mivik.kamet

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.global.LLVM

internal fun Char.description(): String = "$this (0x${toShort().toString(16)})"

internal fun unreachable(): Nothing = error("Unreachable code reached!")

internal fun LLVMTypeRef.pointer(): LLVMTypeRef = LLVM.LLVMPointerType(this, 0)

internal fun Char.escape(): Char =
	when (this) {
		'\\' -> '\\'
		'"' -> '"'
		'n' -> '\n'
		'r' -> '\r'
		't' -> '\t'
		'b' -> '\b'
		'f' -> '\u000c'
		'v' -> '\u000b'
		'0' -> '\u0000'
		else -> throw IllegalEscapeException(this)
	}

internal fun String.toLongIgnoringOverflow(): Long {
	var ret = 0L
	val negative = first() == '-'
	for (i in (if (negative) 1 else 0) until length) ret = ret * 10 + (this[i] - '0')
	return ret
}

internal fun BytePointer.settle(): String =
	string.also { LLVM.LLVMDisposeMessage(this) }
