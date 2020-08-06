package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class FunctionNode(
	val prototype: PrototypeNode,
	val body: BlockNode
) : ASTNode {
	override fun codegen(context: Context): Value {
		if (context.hasValue(prototype.functionName)) error("Redeclaration: ${prototype.functionName}")
		val function = prototype.codegen(context)
		LLVM.LLVMGetNamedFunction(context.module, prototype.name)
		context.block = LLVM.LLVMAppendBasicBlock(function.llvm, "entry")
		context.subContext(function).run {
			for ((i, parameter) in prototype.parameters.withIndex()) {
				val (name, type) = parameter
				declare(name, Value(LLVM.LLVMGetParam(function.llvm, i), type.translate(context)))
			}
			body.codegen(this)
		}
		context.declare(prototype.name, function)
		return function
	}

	override fun toString(): String = "$prototype $body"
}