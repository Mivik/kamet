package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.Function
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.buildAttributes
import com.mivik.kamet.ifNotNull
import com.mivik.kamet.ifThat
import com.mivik.kamet.impossible
import com.mivik.kamet.toInt
import org.bytedeco.llvm.global.LLVM

internal class PrototypeNode(
	attributes: Attributes,
	val name: String,
	val type: Type.Function,
	val parameterNames: List<String>
) : ASTNode {
	val noMangle: Boolean
	val extern: Boolean

	inline val mangledName: String
		get() = "${(type.receiverType != null).ifThat { "${type.receiverType}." }}$name(${
			type.parameterTypes.joinToString(
				","
			)
		}):${type.returnType}"

	val functionName: String

	init {
		var functionName: String? = null
		var extern = false
		for (attr in attributes.verify("Prototype", setOf(Attribute.NO_MANGLE, Attribute.EXTERN)))
			when (attr) {
				Attribute.NO_MANGLE -> functionName = name
				Attribute.EXTERN -> extern = true
				else -> impossible()
			}
		this.extern = extern
		if (functionName == null) {
			this.functionName = mangledName
			noMangle = false
		} else {
			this.functionName = functionName
			noMangle = true
		}
	}

	override fun Context.codegenForThis(): Value {
		val type = type.resolve() as Type.Function
		lookupValueOrNull(functionName)?.let { return it }
		val function = LLVM.LLVMAddFunction(module, functionName, type.llvm)
		val offset = type.hasReceiver.toInt()
		if (type.hasReceiver) LLVM.LLVMSetValueName2(LLVM.LLVMGetParam(function, 0), "this", "this".length.toLong())
		parameterNames.forEachIndexed { index, name ->
			LLVM.LLVMSetValueName2(LLVM.LLVMGetParam(function, index + offset), name, name.length.toLong())
		}
		return type.new(function).also { declareFunction(this@PrototypeNode, Function.Static(it)) }
	}

	override fun toString(): String =
		"${
			buildAttributes {
				if (noMangle) +Attribute.NO_MANGLE
				if (extern) +Attribute.EXTERN
			}
		}fun ${type.receiverType.ifNotNull { "${type.receiverType}." }}$name(${parameterNames.indices.joinToString { "${parameterNames[it]}: ${type.parameterTypes[it]}" }}): ${type.returnType}"
}