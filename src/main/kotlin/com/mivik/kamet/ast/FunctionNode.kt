package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.ValueRef
import com.mivik.kamet.asVal
import org.bytedeco.llvm.global.LLVM

internal class FunctionNode(
	val prototype: PrototypeNode,
	val body: BlockNode
) : ASTNode {
	override fun codegen(context: Context): Value {
		if (context.hasValue(prototype.name)) error("Redeclare of ${prototype.name}")
		val function = prototype.codegen(context)
		LLVM.LLVMGetNamedFunction(context.module, prototype.name)
		LLVM.LLVMPositionBuilderAtEnd(context.builder, LLVM.LLVMAppendBasicBlock(function.llvm, "entry"))
		val subContext = context.subContext(function)
		val parameters = prototype.parameters
		for (i in parameters.indices)
			subContext.declare(
				parameters[i].first,
				Value(LLVM.LLVMGetParam(function.llvm, i), context.lookupType(parameters[i].second)).asVal()
			)
		body.codegen(subContext)
		context.declare(prototype.name, function.asVal())
		return function
	}
}