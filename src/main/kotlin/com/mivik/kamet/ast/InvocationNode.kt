package com.mivik.kamet.ast

import com.mivik.kamet.CastManager
import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.global.LLVM

internal class InvocationNode(val name: String, val elements: List<ASTNode>) : ASTNode {
	@Suppress("NOTHING_TO_INLINE")
	inline fun findMatchingFunction(context: Context, arguments: List<Value>): Value {
		val functions = context.lookupFunctions(name)
		if (functions.isEmpty()) error("No function named \"$name\"")
		var ret: Value? = null
		loop@ for (function in functions) {
			val type = function.type as Type.Function
			val parameterTypes = type.parameterTypes
			if (arguments.size != parameterTypes.size) continue
			for (i in elements.indices)
				if (!arguments[i].type.isSubtypeOf(parameterTypes[i])) continue@loop
			ret.let {
				if (it == null) ret = function
				else error(
					"Ambiguous call to function \"$name\": ${function.type} and ${it.type} are all applicable to arguments (${arguments.joinToString(
						", "
					) { it.type.name }})"
				)
			}
		}
		return ret
			?: error("No matching function for call to \"$name\" with argument types: (${arguments.joinToString(", ") { it.type.name }})")
	}

	override fun codegen(context: Context): Value {
		val arguments = elements.map { it.codegen(context) }
		val function = findMatchingFunction(context, arguments)
		val type = function.type as Type.Function
		val parameterTypes = type.parameterTypes
		return Value(
			LLVM.LLVMBuildCall(
				context.builder,
				function.llvm,
				PointerPointer(*Array(arguments.size) {
					CastManager.cast(context, arguments[it], parameterTypes[it]).llvm
				}),
				arguments.size,
				if (type.returnType == Type.Unit) "" else "${name}_result"
			), type.returnType
		)
	}

	override fun toString(): String = "$name(${elements.joinToString(", ")})"
}