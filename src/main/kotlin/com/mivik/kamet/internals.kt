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

inline fun Any?.ifNotNull(string: () -> String) =
	if (this != null) string() else ""

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

internal fun <T> List<T>.readOnly() =
	when (size) {
		0 -> emptyList()
		1 -> listOf(this[0])
		else -> this
	}

internal fun <T> Set<T>.readOnly() =
	when (size) {
		0 -> emptySet()
		1 -> setOf(first())
		else -> this
	}

internal fun genericName(baseName: String, typeParameters: List<Type>) =
	"$baseName<${typeParameters.joinToString()}>"

internal fun Function.match(receiverType: Type?, argumentTypes: List<Type>): Boolean {
	val type = type
	val parameterTypes = type.parameterTypes
	if (argumentTypes.size != parameterTypes.size) return false
	if (receiverType == null) {
		if (type.receiverType != null) return false
	} else {
		if (type.receiverType == null || !receiverType.canImplicitlyCastTo(type.receiverType)) return false
	}
	return argumentTypes.indices.all { argumentTypes[it].canImplicitlyCastTo(parameterTypes[it]) }
}

internal fun noMatchingFunction(name: String, argumentTypes: List<Type>): Nothing =
	error("No matching function for call to ${name.escape()} with argument types: (${argumentTypes.joinToString()})")

internal fun findMatchingFunction(
	name: String,
	alternatives: Iterable<Function>,
	receiverType: Type?,
	argumentTypes: List<Type>
): Function {
	val functions = alternatives.iterator().takeIf { it.hasNext() } ?: error("No function named ${name.escape()}")
	var found: Function? = null
	for (function in functions) {
		if (!function.match(receiverType, argumentTypes)) continue
		if (found == null) found = function
		else error("Ambiguous call to function ${name.escape()}: ${function.type} and ${found.type} are both applicable to arguments (${argumentTypes.joinToString()})")
	}
	return found ?: noMatchingFunction(name, argumentTypes)
}

internal fun <T : Pointer> List<T>.toPointerPointer() =
	buildPointerPointer(size) { this[it] }

internal inline fun <T : Pointer> buildPointerPointer(size: Int, generator: (Int) -> T): PointerPointer<T> =
	PointerPointer<T>(size.toLong()).apply {
		for (i in 0 until size) put(i.toLong(), generator(i))
	}
