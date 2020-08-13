package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.escape
import com.mivik.kamet.ifNotNull
import com.mivik.kamet.ifThat
import org.bytedeco.llvm.global.LLVM

private fun Context.convert(value: Value, expected: Type? = null): Value {
	expected?.resolve()?.let { return value.implicitCast(it) }
	return value.dereference()
}

@Suppress("NOTHING_TO_INLINE")
private inline fun unclearVariable(name: String): Nothing =
	error("Declaration of variable ${name.escape()} without type or initializer.")

internal class ValDeclareNode(
	val name: String,
	val type: Type? = null,
	val defaultValue: ASTNode? = null
) : ASTNode {
	override fun Context.codegenForThis(): Value {
		if (defaultValue == null) {
			type ?: unclearVariable(name)
			val value = type.resolve().undefined()
			declare(name, value)
			LLVM.LLVMSetValueName2(value.llvm, name, name.length.toLong())
		} else {
			val value = convert(defaultValue.codegen(), type)
			declare(name, value)
			LLVM.LLVMSetValueName2(value.llvm, name, name.length.toLong())
		}
		return Value.Unit
	}

	override fun toString(): String = "val $name${type.ifNotNull { ": $type" }} = $defaultValue"
}

internal class VarDeclareNode(
	val name: String,
	val type: Type? = null,
	val defaultValue: ASTNode? = null,
	val isConst: Boolean = false
) : ASTNode {
	override fun Context.codegenForThis(): Value {
		if (defaultValue == null) {
			type ?: unclearVariable(name)
			declareVariable(name, type.resolve().undefined(), isConst)
		} else
			declareVariable(name, convert(defaultValue.codegen(), type), isConst)
		return Value.Unit
	}

	override fun toString(): String =
		"${isConst.ifThat { "const " }}var $name${type.ifNotNull { ": $type" }} = $defaultValue"
}