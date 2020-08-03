package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.global.LLVM

internal class InvocationNode(val functionName: String, val elements: List<ASTNode>) : ASTNode {
	override fun codegen(context: Context): Value {
		// TODO polymorphism
		val function = context.lookupValue(functionName).dereference(context)
		val functionType = function.type
		if (functionType !is Type.Function) error("Attempt to invoke non-function $functionName: ${function.type}")
		val parameterTypes = functionType.parameterTypes
		if (elements.size != parameterTypes.size) error("Expected ${parameterTypes.size} arguments for function $functionName, got ${elements.size}")
		val arguments = elements.map { it.codegen(context) }
		for (i in arguments.indices)
			if (!arguments[i].type.isSubtypeOf(parameterTypes[i]))
				error("Expected ${parameterTypes[i]}, got ${arguments[i].type}, at no.${i + 1} parameter of the function $functionName")
		return Value(
			LLVM.LLVMBuildCall(
				context.builder,
				function.llvm,
				PointerPointer(*Array(arguments.size) { arguments[it].llvm }),
				arguments.size,
				"${functionName}_result"
			), functionType.returnType
		)
	}

	override fun toString(): String = "$functionName(${elements.joinToString(", ")})"
}