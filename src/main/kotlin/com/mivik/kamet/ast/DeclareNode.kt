package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

private fun Context.convert(value: Value, expected: TypeDescriptor? = null): Value {
	val type = expected?.translate()
	type?.let { return value.implicitCast(type) }
	return value
}

internal class ValDeclareNode(val name: String, val type: TypeDescriptor? = null, val defaultValue: ASTNode) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val value = convert(defaultValue.codegen(), type)
		declare(name, value)
		LLVM.LLVMSetValueName2(value.llvm, name, name.length.toLong())
		return Value.Nothing
	}

	override fun toString(): String = "val $name = $defaultValue"
}

internal class VarDeclareNode(
	val name: String,
	val type: TypeDescriptor? = null,
	val defaultValue: ASTNode,
	val isConst: Boolean = false
) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val value = convert(defaultValue.codegen(), type)
		declareVariable(name, value, isConst)
		return Value.Nothing
	}

	override fun toString(): String = "var $name = $defaultValue"
}