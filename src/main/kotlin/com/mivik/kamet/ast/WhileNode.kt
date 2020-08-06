package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class WhileNode(val condition: ASTNode, val block: ASTNode) : ASTNode {
	override fun codegen(context: Context): Value {
		val function = context.llvmFunction
		val whileBB = LLVM.LLVMAppendBasicBlock(function, "while")
		val finalBB = LLVM.LLVMAppendBasicBlock(function, "final")
		LLVM.LLVMBuildCondBr(context.builder, condition.codegen(context).llvm, whileBB, finalBB)
		context.codegenUsing(whileBB, block)
		LLVM.LLVMBuildCondBr(context.builder, condition.codegen(context).llvm, whileBB, finalBB)
		context.block = finalBB
		return Value.Nothing
	}

	override fun toString(): String = "while ($condition) $block"
}