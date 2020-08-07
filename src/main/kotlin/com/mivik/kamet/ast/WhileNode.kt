package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class WhileNode(val condition: ASTNode, val block: ASTNode) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val function = llvmFunction
		val whileBB = LLVM.LLVMAppendBasicBlock(function, "while")
		val finalBB = LLVM.LLVMAppendBasicBlock(function, "final")
		LLVM.LLVMBuildCondBr(builder, condition.codegen().llvm, whileBB, finalBB)
		block.codegenUsing(whileBB)
		LLVM.LLVMBuildCondBr(builder, condition.codegen().llvm, whileBB, finalBB)
		basicBlock = finalBB
		return Value.Nothing
	}

	override fun toString(): String = "while ($condition) $block"
}