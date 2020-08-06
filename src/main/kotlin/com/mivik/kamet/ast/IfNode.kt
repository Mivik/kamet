package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class IfNode(val condition: ASTNode, val thenBlock: ASTNode, val elseBlock: ASTNode? = null) : ASTNode {
	override fun codegen(context: Context): Value {
		val builder = context.builder
		val conditionValue = condition.codegen(context)
		val function = context.llvmFunction
		var thenBB = LLVM.LLVMAppendBasicBlock(function, "then")
		var elseBB = LLVM.LLVMAppendBasicBlock(function, "else")
		if (elseBlock == null) {
			LLVM.LLVMBuildCondBr(builder, conditionValue.llvm, thenBB, elseBB)
			context.codegenUsing(thenBB, thenBlock)
			if (!thenBlock.returned) LLVM.LLVMBuildBr(builder, elseBB)
			context.block = elseBB
			return Value.Nothing
		} else { //v value if-else
			val mergeBB = LLVM.LLVMAppendBasicBlock(function, "final")
			LLVM.LLVMBuildCondBr(builder, conditionValue.llvm, thenBB, elseBB)
			val thenRet = context.codegenUsing(thenBB, thenBlock)
			thenBB = context.block
			val elseRet = context.codegenUsing(elseBB, elseBlock)
			elseBB = context.block
			return if (thenRet.type == elseRet.type) {
				val variable = context.declareVariable("if_result", thenRet.type.undefined())
				context.block = thenBB
				variable.setIn(context, thenRet)
				if (!thenBlock.returned) LLVM.LLVMBuildBr(builder, mergeBB)
				context.block = elseBB
				variable.setIn(context, elseRet)
				if (!elseBlock.returned) LLVM.LLVMBuildBr(builder, mergeBB)
				context.block = mergeBB
				variable.dereference(context)
			} else { // fail (type mismatch)
				context.block = thenBB
				if (!thenBlock.returned) LLVM.LLVMBuildBr(builder, mergeBB)
				context.block = elseBB
				if (!elseBlock.returned) LLVM.LLVMBuildBr(builder, mergeBB)
				context.block = mergeBB
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