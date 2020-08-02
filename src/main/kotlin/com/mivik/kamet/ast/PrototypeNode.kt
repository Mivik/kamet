package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.global.LLVM

internal class PrototypeNode(
	val name: String,
	val returnTypeName: String,
	val parameters: List<Pair<String, String>>
) : ASTNode {
	override fun codegen(context: Context): Value {
		val returnType = context.lookupType(returnTypeName)
		val functionType =
			LLVM.LLVMFunctionType(
				returnType.llvm,
				PointerPointer(*Array(parameters.size) { context.lookupType(parameters[it].second).llvm }),
				parameters.size,
				0
			)
		val function = LLVM.LLVMAddFunction(context.module, name, functionType)
		for (i in parameters.indices) {
			val paramName = parameters[i].first
			LLVM.LLVMSetValueName2(LLVM.LLVMGetParam(function, i), paramName, paramName.length.toLong())
		}
		return Value.Empty
	}
}