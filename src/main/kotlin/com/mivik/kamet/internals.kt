package com.mivik.kamet

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.global.LLVM
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal fun Char.description(): String = "$this (0x${toShort().toString(16)})" //'a' (0x61)

internal fun impossible(): Nothing = error("Unreachable code reached!")

internal fun LLVMTypeRef.pointer(): LLVMTypeRef = LLVM.LLVMPointerType(this, 0)

private val escapeMap = mapOf(
	'\\' to '\\',
	'"' to '"',
	'n' to '\n',
	'r' to '\r',
	't' to '\t',
	'b' to '\b',
	'f' to '\u000c',
	'v' to '\u000b',
	'0' to '\u0000'
)
private val reversedEscapeMap = escapeMap.entries.associate { it.value to it.key }
internal fun Char.escape(): Char = escapeMap.getOrElse(this) { throw IllegalEscapeException(this) }

// Working replacement for https://github.com/Mivik/Kot/blob/master/src/main/kotlin/com/mivik/kot/Kot.kt#L13 (StringEscapeUtils.escapeForJava)
internal fun String.escape(): String =
	fold(StringBuilder()) { sb, c ->
		reversedEscapeMap[c]?.let { sb.append('\\').append(it) } ?: sb.append(c)
	}.toString()

internal fun String.toLongIgnoringOverflow(): Long {
	var acc = 0L
	val start = (first() == '-').toInt()
	for (i in start..lastIndex) acc = acc * 10 + (this[i] - '0')
	return acc
}

internal fun BytePointer.toJava(): String =
	string.also { LLVM.LLVMDisposeMessage(this) }

inline fun Boolean.ifThat(string: () -> String) =
	if (this) string() else ""

@Suppress("NOTHING_TO_INLINE")
inline fun Boolean.toInt() = if (this) 1 else 0

@OptIn(ExperimentalContracts::class)
internal inline fun <reified T> Any?.expect(): T {
	contract {
		returns() implies (this@expect is T)
	}
	require(this is T) { "Expected ${T::class.simpleName}, got $this" }
	return this
}

internal val nullPointer = Pointer(null as Pointer?)

internal fun findMatchingFunction(name: String, alternatives: List<Function>, argumentTypes: List<Type>): Function {
	val functions = alternatives.takeUnless { it.isEmpty() } ?: error("No function named \"$name\"")
	var found: Function? = null
	val argStr by lazy { argumentTypes.joinToString { it.name } }
	for (function in functions) {
		val type = function.type
		val parameterTypes = type.parameterTypes
		if (argumentTypes.size != parameterTypes.size) continue
		if (argumentTypes.indices.any { !argumentTypes[it].canImplicitlyCastTo(parameterTypes[it]) }) continue
		if (found == null) found = function
		else error("Ambiguous call to function \"$name\": ${function.type} and ${found.type} are both applicable to arguments ($argStr)")
	}
	return found ?: error("No matching function for call to \"$name\" with argument types: ($argStr)")
}

internal inline fun <T : Pointer> buildPointerPointer(size: Int, generator: (Int) -> T): PointerPointer<T> =
	PointerPointer<T>(size.toLong()).apply {
		for (i in 0 until size) put(i.toLong(), generator(i))
	}
