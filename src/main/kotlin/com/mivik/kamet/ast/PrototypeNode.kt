package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.asVal
import org.bytedeco.llvm.global.LLVM

internal class PrototypeNode(
	val name: String,
	val returnTypeName: String,
	val parameters: List<Pair<String, String>>
) : ASTNode {
	override fun codegen(context: Context): Value {
		val returnType = context.lookupType(returnTypeName)
		val functionType = Type.Function(
			returnType,
			parameters.map { context.lookupType(it.second) }
		)
		val function = LLVM.LLVMAddFunction(context.module, name, functionType.llvm)
		for (i in parameters.indices) {
			val paramName = parameters[i].first
			LLVM.LLVMSetValueName2(LLVM.LLVMGetParam(function, i), paramName, paramName.length.toLong())
		}
		return Value(function, functionType).also {
			context.declare(name, it.asVal())
		}
	}

	override fun toString(): String =
		"fun $name(${parameters.joinToString(", ") { "${it.first}: ${it.second}" }})${if (returnTypeName == Type.Unit.name) "" else ": $returnTypeName"}"
}