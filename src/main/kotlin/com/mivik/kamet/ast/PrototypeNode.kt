package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.Function
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.ifThat
import org.bytedeco.llvm.global.LLVM

internal class PrototypeNode(
	attributes: Attributes,
	val name: String,
	val returnType: Type,
	val parameters: List<Pair<String, Type>>
) : ASTNode {
	val noMangle: Boolean

	inline val mangledName: String
		get() = "$name(${parameters.joinToString(",") { "${it.second}" }}):$returnType"

	val functionName: String

	init {
		var functionName: String? = null
		for (attr in attributes)
			when (attr) {
				Attribute.NO_MANGLE -> functionName = name
				else -> attr.notApplicableTo("Prototype")
			}
		if (functionName == null) {
			this.functionName = mangledName
			noMangle = false
		} else {
			this.functionName = functionName
			noMangle = true
		}
		val has = mutableSetOf<String>()
		for (para in parameters)
			if (has.contains(para.first)) error("Duplicate parameter name: ${para.first}")
			else has += para.first
	}

	override fun Context.codegenForThis(): Value {
		lookupValueOrNull(functionName)?.let { return it }
		val returnType = returnType.resolve()
		val functionType = Type.Function(
			returnType,
			parameters.map { it.second.resolve() }
		)
		val function = LLVM.LLVMAddFunction(module, functionName, functionType.llvm)
		for (i in parameters.indices) {
			val paramName = parameters[i].first
			LLVM.LLVMSetValueName2(LLVM.LLVMGetParam(function, i), paramName, paramName.length.toLong())
		}
		return functionType.new(function).also { declareFunction(this@PrototypeNode, Function.Static(it)) }
	}

	override fun toString(): String =
		"${noMangle.ifThat { "#[no_mangle] " }}fun $name(${parameters.joinToString { "${it.first}: ${it.second}" }}): $returnType"
}