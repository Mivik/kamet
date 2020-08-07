package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class IfNode(val condition: ASTNode, val thenBlock: ASTNode, val elseBlock: ASTNode? = null) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val conditionValue = condition.codegen()
		val function = llvmFunction
		var thenBB = LLVM.LLVMAppendBasicBlock(function, "then")
		var elseBB = LLVM.LLVMAppendBasicBlock(function, "else")
		if (elseBlock == null) {
			LLVM.LLVMBuildCondBr(builder, conditionValue.llvm, thenBB, elseBB)
			thenBlock.codegenUsing(thenBB)
			if (!thenBlock.returned) LLVM.LLVMBuildBr(builder, elseBB)
			basicBlock = elseBB
			return Value.Nothing
		} else { // if-else
			val mergeBB = LLVM.LLVMAppendBasicBlock(function, "final")
			LLVM.LLVMBuildCondBr(builder, conditionValue.llvm, thenBB, elseBB)
			val thenRet = thenBlock.codegenUsing(thenBB)
			thenBB = basicBlock
			val elseRet = elseBlock.codegenUsing(elseBB)
			elseBB = basicBlock
			return if (thenRet.type == elseRet.type) { // when the whole if statement can be considered as a value
				// TODO whether two types are equivalent is not equal to whether they are equal
				val variable = declareVariable("if_result", thenRet.type.undefined())
				basicBlock = thenBB
				variable.setValue(thenRet)
				if (!thenBlock.returned) LLVM.LLVMBuildBr(builder, mergeBB)
				basicBlock = elseBB
				variable.setValue(elseRet)
				if (!elseBlock.returned) LLVM.LLVMBuildBr(builder, mergeBB)
				basicBlock = mergeBB
				variable.dereference()
			} else { // this if is not a expression
				basicBlock = thenBB
				if (!thenBlock.returned) LLVM.LLVMBuildBr(builder, mergeBB)
				basicBlock = elseBB
				if (!elseBlock.returned) LLVM.LLVMBuildBr(builder, mergeBB)
				basicBlock = mergeBB
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