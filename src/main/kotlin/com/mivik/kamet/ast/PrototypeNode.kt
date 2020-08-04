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
) : AttributedNode(attributes) {
	val mangled: String
		get() = "$name(${parameters.joinToString(",") { "${it.second}" }}):$returnType"

	val functionName: String

	init {
		var tmp: String? = null
		for (attr in attributes)
			when (attr) {
				Attribute.NATIVE -> tmp = name
				else -> error("\"$attr\" attribute is not applicable to Prototype")
			}
		functionName = tmp ?: mangled
	}

	override fun codegen(context: Context): Value {
		context.lookupValueOrNull(functionName)?.let { return it }
		val returnType = returnType.translate(context)
		val functionType = Type.Function(
			returnType,
			parameters.map { it.second.translate(context) }
		)
		val function = LLVM.LLVMAddFunction(context.module, functionName, functionType.llvm)
		for (i in parameters.indices) {
			val paramName = parameters[i].first
			LLVM.LLVMSetValueName2(LLVM.LLVMGetParam(function, i), paramName, paramName.length.toLong())
		}
		return Value(function, functionType).also { context.declareFunction(this, it) }
	}

	override fun toString(): String =
		"fun $name(${parameters.joinToString(", ") { "${it.first}: ${it.second}" }}): $returnType"
}