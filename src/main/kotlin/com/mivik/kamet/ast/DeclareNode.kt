package com.mivik.kamet.ast

import com.mivik.kamet.CastManager
import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

private fun convert(context: Context, value: Value, expected: TypeDescriptor? = null): Value {
	val type = expected?.translate(context)
	type?.let {
		require(value.type.isSubtypeOf(it)) { "Expected $expected, got a ${value.type}" }
		return CastManager.cast(context, value, type)
	}
	return value
}

internal class ValDeclareNode(val name: String, val expected: TypeDescriptor? = null, val defaultValue: ASTNode) :
	ASTNode {
	override fun codegen(context: Context): Value {
		val value = convert(context, defaultValue.codegen(context), expected)
		context.declare(name, value)
		LLVM.LLVMSetValueName2(value.llvm, name, name.length.toLong())
		return Value.Nothing
	}

	override fun toString(): String = "val $name = $defaultValue"
}

internal class VarDeclareNode(
	val name: String,
	val expected: TypeDescriptor? = null,
	val defaultValue: ASTNode,
	val isConst: Boolean = false
) : ASTNode {
	override fun codegen(context: Context): Value {
		val value = convert(context, defaultValue.codegen(context), expected)
		context.declareVariable(name, value, isConst)
		return Value.Nothing
	}

	override fun toString(): String = "var $name = $defaultValue"
}