package com.mivik.kamet

import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.LLVM.LLVMTypeRef
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal fun Char.description(): String = "$this (0x${toShort().toString(16)})" //'a' (0x61)

internal fun impossible(): Nothing = error("Unreachable code reached!")

internal fun LLVMTypeRef.pointer(): LLVMTypeRef = LLVM.LLVMPointerType(this, 0)
internal fun LLVMTypeRef.array(size: Int): LLVMTypeRef = LLVM.LLVMArrayType(this, size)
internal fun LLVMValueRef.dump() = LLVM.LLVMDumpValue(this)

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

internal fun String.addIndent() =
	split('\n').joinToString("\n") { '\t' + it }

internal fun genericName(baseName: String, typeParameters: List<TypeParameter>) =
	"$baseName<${typeParameters.joinToString()}>"

internal fun actualGenericName(baseName: String, typeArguments: List<Type>) =
	if (typeArguments.isEmpty()) baseName
	else "$baseName<${typeArguments.joinToString()}>"


internal fun Int.toLLVM() = LLVM.LLVMConstInt(Type.Primitive.Integral.Int.llvm, toLong(), 0)

internal fun Context.findMatchingFunction(
	name: String,
	alternatives: Iterable<Function>,
	receiverType: Type?,
	argumentTypes: List<Type>,
	typeArguments: List<Type>
): Function {
	val functions = alternatives.iterator().takeIf { it.hasNext() } ?: error("No function named ${name.escape()}")
	var found: Function? = null
	for (function in functions) {
		val instantiated = function.instantiate(receiverType, argumentTypes, typeArguments) ?: continue
		if (found == null) found = instantiated
		else error("Ambiguous call to function ${name.escape()}: ${instantiated.type} and ${found.type} are both applicable to arguments (${argumentTypes.joinToString()})")
	}
	return found ?: error(
		"No matching function for call to ${name.escape()} with argument types: ${receiverType.ifNotNull { "$receiverType." }}${
			actualGenericName(
				name,
				typeArguments
			)
		}(${argumentTypes.joinToString()})"
	)
}

internal inline fun <T : Pointer> buildPointerPointer(size: Int, generator: (Int) -> T): PointerPointer<T> =
	PointerPointer<T>(size.toLong()).apply {
		for (i in 0 until size) put(i.toLong(), generator(i))
	}

internal fun <T : Pointer> buildPointerPointer(vararg pointers: T) =
	buildPointerPointer(pointers.size) { pointers[it] }

internal fun List<Type>.unexpected() {
	if (isNotEmpty()) error("Unexpected type arguments: <${joinToString()}>")
}
