package com.mivik.kamet.ast

import com.mivik.kamet.CastManager
import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.global.LLVM

internal class InvocationNode(val name: String, val elements: List<ASTNode>) : ASTNode {
	fun findMatchingFunction(context: Context, arguments: List<Value>): Value {
		val functions = context.lookupFunctions(name)
		if (functions.isEmpty()) error("No function named \"$name\"")
		loop@ for (function in functions) {
			val type = function.type as Type.Function
			val parameterTypes = type.parameterTypes
			if (arguments.size != parameterTypes.size) continue
			for (i in elements.indices)
				if (!arguments[i].type.isSubtypeOf(parameterTypes[i])) continue@loop
			return function
		}
		error("No matching function for call to \"name\"")
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
					CastManager.cast(arguments[it], parameterTypes[it]).llvm
				}),
				arguments.size,
				"${name}_result"
			), type.returnType
		)
	}

	override fun toString(): String = "$name(${elements.joinToString(", ")})"
}