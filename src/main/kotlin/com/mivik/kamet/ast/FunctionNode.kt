package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class FunctionNode(
	val prototype: PrototypeNode,
	val body: BlockNode
) : ASTNode {
	override fun Context.codegenForThis(): Value {
		if (hasValue(prototype.functionName)) error("Redeclaration: ${prototype.functionName}")
		val function = prototype.codegen()
		LLVM.LLVMGetNamedFunction(module, prototype.name)
		basicBlock = LLVM.LLVMAppendBasicBlock(function.llvm, "entry")
		val sub = subContext(function)
		for ((i, parameter) in prototype.parameters.withIndex()) {
			val (name, type) = parameter
			sub.declare(name, Value(LLVM.LLVMGetParam(function.llvm, i), type.translate()))
		}
		with(sub) { body.codegen() }
		declare(prototype.name, function)
		return function
	}

	override fun toString(): String = "$prototype $body"
}