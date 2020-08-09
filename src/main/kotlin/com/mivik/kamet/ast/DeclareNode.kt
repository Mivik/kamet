package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value
import com.mivik.kamet.ifThat
import org.bytedeco.llvm.global.LLVM

private fun Context.convert(value: Value, expected: TypeDescriptor? = null): Value {
	expected?.translate()?.let { return value.implicitCast(it) }
	return value
}

@Suppress("NOTHING_TO_INLINE")
private inline fun unclearVariable(name: String): Nothing =
	error("Declaration of variable \"$name\" without type or initializer.")

internal class ValDeclareNode(
	val name: String,
	val type: TypeDescriptor? = null,
	val defaultValue: ASTNode? = null
) : ASTNode {
	override fun Context.codegenForThis(): Value {
		if (defaultValue == null) {
			type ?: unclearVariable(name)
			val value = type.translate().undefined()
			declare(name, value)
			LLVM.LLVMSetValueName2(value.llvm, name, name.length.toLong())
		} else {
			val value = convert(defaultValue.codegen(), type)
			declare(name, value)
			LLVM.LLVMSetValueName2(value.llvm, name, name.length.toLong())
		}
		return Value.Nothing
	}

	override fun toString(): String = "val $name${(type==null).ifThat { ": $type" }} = $defaultValue"
}

internal class VarDeclareNode(
	val name: String,
	val type: TypeDescriptor? = null,
	val defaultValue: ASTNode? = null,
	val isConst: Boolean = false
) : ASTNode {
	override fun Context.codegenForThis(): Value {
		if (defaultValue == null) {
			type ?: unclearVariable(name)
			declareVariable(name, type.translate().undefined(), isConst)
		} else
			declareVariable(name, convert(defaultValue.codegen(), type), isConst)
		return Value.Nothing
	}

	override fun toString(): String =
		"${isConst.ifThat { "const " }}var $name${if (type == null) "" else ": $type"} = $defaultValue"
}