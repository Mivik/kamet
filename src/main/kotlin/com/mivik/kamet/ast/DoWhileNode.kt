package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class DoWhileNode(val block: ASTNode, val condition: ASTNode) : ASTNode {
	override fun codegen(context: Context): Value {
		val function = context.llvmFunction
		val whileBB = LLVM.LLVMAppendBasicBlock(function, "while")
		val finalBB = LLVM.LLVMAppendBasicBlock(function, "final")
		LLVM.LLVMBuildBr(context.builder, whileBB)
		context.block = whileBB
		block.codegen(context)
		LLVM.LLVMBuildCondBr(context.builder, condition.codegen(context).llvm, whileBB, finalBB)
		context.block = finalBB
		return Value.Nothing
	}

	override fun toString(): String = "do $block while ($condition)"
}