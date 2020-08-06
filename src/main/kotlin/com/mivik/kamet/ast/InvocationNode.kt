package com.mivik.kamet.ast

import com.mivik.kamet.CastManager
import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.canImplicitlyCastTo
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.global.LLVM

internal class InvocationNode(val name: String, val elements: List<ASTNode>) : ASTNode {
	private fun findMatchingFunction(context: Context, arguments: List<Value>): Value {
		val functions =
			context.lookupFunctions(name).takeUnless { it.isEmpty() } ?: error("No function named \"$name\"")
		var found: Value? = null
		val argStr by lazy { arguments.joinToString(", ") { it.type.name } }
		nextFunction@ for (function in functions) {
			val type = function.type as Type.Function
			val parameterTypes = type.parameterTypes
			if (arguments.size != parameterTypes.size) continue
			for (i in elements.indices)
				if (!arguments[i].type.canImplicitlyCastTo(parameterTypes[i])) continue@nextFunction
			if (found == null) found = function
			else error("Ambiguous call to function \"$name\": ${function.type} and ${found.type} are both applicable to arguments ($argStr)")
		}
		return found ?: error("No matching function for call to \"$name\" with argument types: ($argStr)")
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
					CastManager.implicitCast(context, arguments[it], parameterTypes[it]).llvm
				}),
				arguments.size,
				if (type.returnType.canImplicitlyCastTo(Type.Unit)) "" else "${name}_result"
			), type.returnType
		)
	}

	override fun toString(): String = "$name(${elements.joinToString(", ")})"
}