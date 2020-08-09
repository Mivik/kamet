package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.canImplicitlyCastTo
import com.mivik.kamet.ifThat
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.global.LLVM

internal class InvocationNode(val name: String, val elements: List<ASTNode>) : ASTNode {
	private fun Context.findMatchingFunction(arguments: List<Value>): Value {
		val functions =
			lookupFunctions(name).takeUnless { it.isEmpty() } ?: error("No function named \"$name\"")
		var found: Value? = null
		val argStr by lazy { arguments.joinToString { it.type.name } }
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

	override fun Context.codegenForThis(): Value {
		val arguments = elements.map { it.codegen() }
		val function = findMatchingFunction(arguments)
		val type = function.type as Type.Function
		val parameterTypes = type.parameterTypes
		return type.returnType.new(
			LLVM.LLVMBuildCall(
				builder,
				function.llvm,
				PointerPointer(*Array(arguments.size) {
					arguments[it].implicitCast(parameterTypes[it]).llvm
				}),
				arguments.size,
				(!type.returnType.canImplicitlyCastTo(Type.Unit)).ifThat { "${name}_result" }
			)
		)
	}

	override fun toString(): String = "$name(${elements.joinToString()})"
}