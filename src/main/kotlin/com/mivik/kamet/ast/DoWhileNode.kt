package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class DoWhileNode(val block: ASTNode, val condition: ASTNode) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val function = llvmFunction
		val whileBB = LLVM.LLVMAppendBasicBlock(function, "while")
		val finalBB = LLVM.LLVMAppendBasicBlock(function, "final")
		LLVM.LLVMBuildBr(builder, whileBB)
		basicBlock = whileBB
		block.codegen()
		LLVM.LLVMBuildCondBr(builder, condition.codegen().llvm, whileBB, finalBB)
		basicBlock = finalBB
		return Value.Nothing
	}

	override fun toString(): String = "do $block while ($condition)"
}