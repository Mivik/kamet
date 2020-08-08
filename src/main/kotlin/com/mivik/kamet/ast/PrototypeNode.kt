package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class PrototypeNode(
	attributes: Attributes,
	val name: String,
	val returnType: TypeDescriptor,
	val parameters: List<Pair<String, TypeDescriptor>>
) : ASTNode {
	val mangled: String
		get() = "$name(${parameters.joinToString(",") { "${it.second}" }}):$returnType"

	val functionName: String

	init {
		var functionName: String? = null
		for (attr in attributes)
			when (attr) {
				Attribute.NO_MANGLE -> functionName = name
				else -> attr.notApplicableTo("Prototype")
			}
		this.functionName = functionName ?: mangled
	}

	override fun Context.codegenForThis(): Value {
		lookupValueOrNull(functionName)?.let { return it }
		val returnType = returnType.translate()
		val functionType = Type.Function(
			returnType,
			parameters.map { it.second.translate() }
		)
		val function = LLVM.LLVMAddFunction(module, functionName, functionType.llvm)
		for (i in parameters.indices) {
			val paramName = parameters[i].first
			LLVM.LLVMSetValueName2(LLVM.LLVMGetParam(function, i), paramName, paramName.length.toLong())
		}
		return Value(function, functionType).also { declareFunction(this@PrototypeNode, it) }
	}

	override fun toString(): String =
		"fun $name(${parameters.joinToString { "${it.first}: ${it.second}" }}): $returnType"
}