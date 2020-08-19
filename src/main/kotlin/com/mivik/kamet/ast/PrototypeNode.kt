package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.Function
import com.mivik.kamet.FunctionGenerator
import com.mivik.kamet.Type
import com.mivik.kamet.TypeParameter
import com.mivik.kamet.Value
import com.mivik.kamet.genericName
import com.mivik.kamet.ifNotNull
import com.mivik.kamet.toInt
import org.bytedeco.llvm.global.LLVM

private fun buildString(attributes: Attributes, name: String, type: Type.Function, parameterNames: List<String>) =
	"${attributes}fun ${type.receiverType.ifNotNull { "${type.receiverType}." }}$name(${parameterNames.indices.joinToString { "${parameterNames[it]}: ${type.parameterTypes[it]}" }}): ${type.returnType}"

@Suppress("EqualsOrHashCode")
internal class PrototypeNode(
	val attributes: Attributes,
	val name: String,
	val type: Type.Function,
	val parameterNames: List<String>
) : FunctionGenerator, ASTNode {
	val noMangle: Boolean
	val extern: Boolean

	inline val mangledName: String
		get() = type.makeName(name)

	val functionName: String

	init {
		var functionName: String? = null
		var extern = false
		for (attr in attributes)
			when (attr) {
				Attribute.NO_MANGLE -> functionName = name
				Attribute.EXTERN -> extern = true
				else -> attr.notApplicableTo("Prototype")
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

	fun Context.resolveForThis() =
		PrototypeNode(attributes, name, type.resolve(true) as Type.Function, parameterNames)

	fun rename(newName: String) =
		PrototypeNode(attributes, newName, type, parameterNames)

	override fun Context.generateForThis(newName: String?): Function {
		val renamed =
			if (newName == null) this@PrototypeNode
			else rename(newName)
		return Function.Static(renamed.codegen())
	}

	override val prototype: PrototypeNode
		get() = this

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
		buildString(attributes, name, type, parameterNames)

	override fun equals(other: Any?): Boolean =
		other is PrototypeNode && name == other.name && type == other.type
}

internal class GenericPrototypeNode(
	override val prototype: PrototypeNode,
	val typeParameters: List<TypeParameter>
) : FunctionGenerator, ASTNode {
	override fun Context.generateForThis(newName: String?): Function =
		Function.Generic.obtain(this, prototype, typeParameters)

	override fun Context.codegenForThis(): Value {
		declareFunction(prototype, generate())
		return Value.Unit
	}

	override fun toString(): String =
		buildString(
			prototype.attributes,
			genericName(prototype.name, typeParameters),
			prototype.type,
			prototype.parameterNames
		)
}
