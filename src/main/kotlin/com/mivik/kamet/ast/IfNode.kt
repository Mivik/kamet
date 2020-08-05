package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class IfNode(val condition: ASTNode, val thenBlock: ASTNode, val elseBlock: ASTNode? = null) : ASTNode {
	override fun codegen(context: Context): Value {
		val builder = context.builder
		val conditionValue = condition.codegen(context)
		val function = context.llvmFunction
		var llvmThenBlock = LLVM.LLVMAppendBasicBlock(function, "then")
		var llvmElseBlock = LLVM.LLVMAppendBasicBlock(function, "else")
		if (elseBlock == null) {
			LLVM.LLVMBuildCondBr(builder, conditionValue.llvm, llvmThenBlock, llvmElseBlock)
			context.block = llvmThenBlock
			thenBlock.codegen(context)
			if (!thenBlock.returned) LLVM.LLVMBuildBr(builder, llvmElseBlock)
			context.block = llvmElseBlock
			return Value.Nothing
		} else {
			val llvmFinalBlock = LLVM.LLVMAppendBasicBlock(function, "final")
			LLVM.LLVMBuildCondBr(builder, conditionValue.llvm, llvmThenBlock, llvmElseBlock)
			context.block = llvmThenBlock
			val thenRet = thenBlock.codegen(context)
			llvmThenBlock = context.block
			context.block = llvmElseBlock
			val elseRet = elseBlock.codegen(context)
			llvmElseBlock = context.block
			return if (thenRet.type == elseRet.type) {
				val variable = context.declareVariable("if_result", thenRet.type.undefined())
				context.block = llvmThenBlock
				variable.set(context, thenRet)
				if (!thenBlock.returned) LLVM.LLVMBuildBr(builder, llvmFinalBlock)
				context.block = llvmElseBlock
				variable.set(context, elseRet)
				if (!elseBlock.returned) LLVM.LLVMBuildBr(builder, llvmFinalBlock)
				context.block = llvmFinalBlock
				variable.dereference(context)
			} else {
				context.block = llvmThenBlock
				if (!thenBlock.returned) LLVM.LLVMBuildBr(builder, llvmFinalBlock)
				context.block = llvmElseBlock
				if (!elseBlock.returned) LLVM.LLVMBuildBr(builder, llvmFinalBlock)
				context.block = llvmFinalBlock
				Value.Nothing
			}
		}
	}

	override fun toString(): String =
		if (elseBlock == null) "if $condition $thenBlock"
		else "if ($condition) $thenBlock else $elseBlock"

	override val returned: Boolean
		get() = thenBlock.returned && elseBlock != null && elseBlock.returned
}