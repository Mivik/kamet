package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import com.mivik.kamet.ValueRef
import com.mivik.kamet.asVal
import org.bytedeco.llvm.global.LLVM

internal class ValDeclareNode(val name: String, val defaultValue: ExprNode) : StmtNode {
	override fun codegen(context: Context): Value {
		val value = defaultValue.codegen(context)
		context.declare(name, value.asVal())
		LLVM.LLVMSetValueName2(value.llvm, name, name.length.toLong())
		return Value.Null
	}
}

internal class VarDeclareNode(val name: String, val defaultValue: ExprNode) : StmtNode {
	override fun codegen(context: Context): Value {
		val function = LLVM.LLVMGetBasicBlockParent(LLVM.LLVMGetInsertBlock(context.builder))
		val entryBlock = LLVM.LLVMGetEntryBasicBlock(function)
		val tmpBuilder = LLVM.LLVMCreateBuilder()
		LLVM.LLVMPositionBuilderAtEnd(tmpBuilder, entryBlock)
		val value = defaultValue.codegen(context)
		val address = LLVM.LLVMBuildAlloca(tmpBuilder, value.type.llvm, name)
		val ref = ValueRef.Var(address, value.type)
		LLVM.LLVMSetValueName2(address, name, name.length.toLong())
		LLVM.LLVMDisposeBuilder(tmpBuilder)
		context.declare(name, ref)
		ref.set(context, value)
		return Value.Null
	}
}