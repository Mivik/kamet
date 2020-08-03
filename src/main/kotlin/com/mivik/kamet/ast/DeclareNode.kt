package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class ValDeclareNode(val name: String, val defaultValue: ASTNode) : ASTNode {
	override fun codegen(context: Context): Value {
		val value = defaultValue.codegen(context)
		context.declare(name, value)
		LLVM.LLVMSetValueName2(value.llvm, name, name.length.toLong())
		return Value.Nothing
	}

	override fun toString(): String = "val $name = $defaultValue"
}

internal class VarDeclareNode(val name: String, val defaultValue: ASTNode) : ASTNode {
	override fun codegen(context: Context): Value {
		context.declareVariable(name, defaultValue.codegen(context))
		return Value.Nothing
	}

	override fun toString(): String = "var $name = $defaultValue"
}