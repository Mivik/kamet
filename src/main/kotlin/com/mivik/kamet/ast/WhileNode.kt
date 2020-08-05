package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class WhileNode(val condition: ASTNode, val block: ASTNode) : ASTNode {
	override fun codegen(context: Context): Value {
		val function = context.llvmFunction
		val llvmWhileBlock = LLVM.LLVMAppendBasicBlock(function, "while")
		val llvmFinalBlock = LLVM.LLVMAppendBasicBlock(function, "final")
		LLVM.LLVMBuildCondBr(context.builder, condition.codegen(context).llvm, llvmWhileBlock, llvmFinalBlock)
		context.block = llvmWhileBlock
		block.codegen(context)
		LLVM.LLVMBuildCondBr(context.builder, condition.codegen(context).llvm, llvmWhileBlock, llvmFinalBlock)
		context.block = llvmFinalBlock
		return Value.Nothing
	}

	override fun toString(): String = "while ($condition) $block"
}