package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.toInt
import org.bytedeco.llvm.global.LLVM

internal class FunctionNode(
	val prototype: PrototypeNode,
	val body: BlockNode
) : ASTNode {
	override fun Context.codegenForThis(): Value {
		if (hasValue(prototype.functionName)) error("Redeclaration: ${prototype.functionName}")
		val function = prototype.codegen()
		LLVM.LLVMGetNamedFunction(module, prototype.name)
		val sub = subContext(function)
		insertAt(sub.basicBlock("entry"))
		val type = function.type as Type.Function
		val parameterTypes = type.parameterTypes
		val offset = type.hasReceiver.toInt()
		if (type.hasReceiver)
			sub.declare("this", type.receiverType!!.new(LLVM.LLVMGetParam(function.llvm, 0)))
		for ((i, name) in prototype.parameterNames.withIndex())
			sub.declare(name, parameterTypes[i].new(LLVM.LLVMGetParam(function.llvm, i + offset)))
		with(sub) { body.codegen() }
		return function
	}

	override fun toString(): String = "$prototype $body"
}