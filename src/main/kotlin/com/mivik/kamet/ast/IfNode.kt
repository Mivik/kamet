package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class IfNode(val condition: ASTNode, val thenBlock: ASTNode, val elseBlock: ASTNode? = null) : ASTNode {
	override fun codegen(context: Context): Value {
		val builder = context.builder
		val conditionValue = condition.codegen(context)
		val function = LLVM.LLVMGetBasicBlockParent(LLVM.LLVMGetInsertBlock(builder))
		val llvmThenBlock = LLVM.LLVMAppendBasicBlock(function, "then")
		val llvmElseBlock = LLVM.LLVMAppendBasicBlock(function, "else")
		if (elseBlock == null) {
			LLVM.LLVMBuildCondBr(builder, conditionValue.llvm, llvmThenBlock, llvmElseBlock)
			LLVM.LLVMPositionBuilderAtEnd(builder, llvmThenBlock)
			LLVM.LLVMBuildBr(builder, llvmElseBlock)
			LLVM.LLVMPositionBuilderAtEnd(builder, llvmElseBlock)
			return Value.Nothing
		} else {
			val llvmFinalBlock = LLVM.LLVMAppendBasicBlock(function, "final")
			LLVM.LLVMBuildCondBr(builder, conditionValue.llvm, llvmThenBlock, llvmElseBlock)
			LLVM.LLVMPositionBuilderAtEnd(builder, llvmThenBlock)
			val thenRet = thenBlock.codegen(context)
			LLVM.LLVMPositionBuilderAtEnd(builder, llvmElseBlock)
			val elseRet = elseBlock.codegen(context)
			return if (thenRet.type == elseRet.type) {
				val variable = context.declareVariable("if_result", thenRet.type.undefined())
				LLVM.LLVMPositionBuilderAtEnd(builder, llvmThenBlock)
				variable.set(context, thenRet)
				LLVM.LLVMBuildBr(builder, llvmFinalBlock)
				LLVM.LLVMPositionBuilderAtEnd(builder, llvmElseBlock)
				variable.set(context, elseRet)
				LLVM.LLVMBuildBr(builder, llvmFinalBlock)
				LLVM.LLVMPositionBuilderAtEnd(builder, llvmFinalBlock)
				variable.get(context)
			} else {
				LLVM.LLVMPositionBuilderAtEnd(builder, llvmThenBlock)
				LLVM.LLVMBuildBr(builder, llvmFinalBlock)
				LLVM.LLVMPositionBuilderAtEnd(builder, llvmElseBlock)
				LLVM.LLVMBuildBr(builder, llvmFinalBlock)
				LLVM.LLVMPositionBuilderAtEnd(builder, llvmFinalBlock)
				Value.Nothing
			}
		}
	}
}