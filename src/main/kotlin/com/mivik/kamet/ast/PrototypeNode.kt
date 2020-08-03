package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class PrototypeNode(
	val name: String,
	val returnType: TypeDescriptor,
	val parameters: List<Pair<String, TypeDescriptor>>
) : ASTNode {
	override fun codegen(context: Context): Value {
		val returnType = returnType.translate(context)
		val functionType = Type.Function(
			returnType,
			parameters.map { it.second.translate(context) }
		)
		val function = LLVM.LLVMAddFunction(context.module, name, functionType.llvm)
		for (i in parameters.indices) {
			val paramName = parameters[i].first
			LLVM.LLVMSetValueName2(LLVM.LLVMGetParam(function, i), paramName, paramName.length.toLong())
		}
		return Value(function, functionType).also { context.declare(name, it) }
	}

	override fun toString(): String =
		"fun $name(${parameters.joinToString(", ") { "${it.first}: ${it.second}" }}): $returnType"
}