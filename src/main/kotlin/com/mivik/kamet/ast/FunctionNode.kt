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
		val subContext = context.subContext()
		val parameters = prototype.parameters
		for (i in parameters.indices)
			subContext.declare(
				parameters[i].first,
				Value(LLVM.LLVMGetParam(function.llvm, i), context.lookupType(parameters[i].second)).asVal()
			)
		body.codegen(subContext)
		val result = subContext.result
		val actualReturnType = result?.type ?: Type.Unit
		val returnType = context.lookupType(prototype.returnTypeName)
		if (!actualReturnType.isSubtypeOf(returnType))
			error("Expected return value to be ${returnType}, got $actualReturnType")
		result.let {
			if (it == null) LLVM.LLVMBuildRetVoid(context.builder)
			else LLVM.LLVMBuildRet(context.builder, it.llvm)
		}
		context.declare(prototype.name, function.asVal())
		return function
	}
}