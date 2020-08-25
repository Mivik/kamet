package com.mivik.kamet.ast

import com.mivik.kamet.Attribute
import com.mivik.kamet.Attributes
import com.mivik.kamet.Context
import com.mivik.kamet.Function
import com.mivik.kamet.FunctionGenerator
import com.mivik.kamet.Prototype
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
	override val prototype: Prototype
) : FunctionGenerator, ASTNode() {
	val noMangle: Boolean
	val extern: Boolean

	val functionName: String

	init {
		var functionName: String? = null
		var extern = false
		for (attr in attributes)
			when (attr) {
				Attribute.NO_MANGLE -> functionName = prototype.name
				Attribute.EXTERN -> extern = true
				else -> attr.notApplicableTo("Prototype")
			}
		this.extern = extern
		if (functionName == null) {
			this.functionName = prototype.mangledName
			noMangle = false
		} else {
			this.functionName = functionName
			noMangle = true
		}
	}

	fun Context.resolveForThis() =
		PrototypeNode(attributes, prototype.resolve())

	fun rename(newName: String) =
		PrototypeNode(attributes, prototype.rename(newName))

	override fun Context.generateForThis(newName: String?): Function {
		val renamed =
			if (newName == null) this@PrototypeNode
			else rename(newName)
		return Function.Static(renamed.codegen())
	}

	override fun Context.codegenForThis(): Value {
		val type = prototype.type.resolve() as Type.Function
		lookupValueOrNull(functionName)?.let { return it }
		val function = LLVM.LLVMAddFunction(module, functionName, type.llvm)
		val offset = type.hasReceiver.toInt()
		if (type.hasReceiver) LLVM.LLVMSetValueName2(LLVM.LLVMGetParam(function, 0), "this", "this".length.toLong())
		prototype.parameterNames.forEachIndexed { index, name ->
			LLVM.LLVMSetValueName2(LLVM.LLVMGetParam(function, index + offset), name, name.length.toLong())
		}
		return type.new(function).also { declareFunction(prototype, Function.Static(it)) }
	}

	override fun toString(): String =
		"$attributes$prototype"

	override fun equals(other: Any?): Boolean =
		other is PrototypeNode && prototype == other.prototype
}

internal class GenericPrototypeNode(
	val node: PrototypeNode,
	val typeParameters: List<TypeParameter>
) : FunctionGenerator, ASTNode() {
	override val prototype: Prototype
		get() = node.prototype

	override fun Context.generateForThis(newName: String?): Function =
		Function.Generic.obtain(this, node, typeParameters)

	override fun Context.codegenForThis(): Value {
		declareFunction(prototype, generate())
		return Value.Unit
	}

	override fun toString(): String =
		node.rename(genericName(prototype.name, typeParameters)).toString()
}
