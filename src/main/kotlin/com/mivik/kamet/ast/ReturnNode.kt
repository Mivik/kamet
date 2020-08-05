package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.canImplicitlyCastTo
import com.mivik.kamet.implicitCast
import org.bytedeco.llvm.global.LLVM

internal class ReturnNode(val value: ASTNode) : ASTNode {
	override fun codegen(context: Context): Value {
		val returnType = (context.currentFunction!!.type as Type.Function).returnType
		val result = value.codegen(context).implicitCast(context, returnType)
		if (result.type.canImplicitlyCastTo(Type.Unit)) LLVM.LLVMBuildRetVoid(context.builder)
		else LLVM.LLVMBuildRet(context.builder, result.llvm)
		return Value.Nothing
	}

	override fun toString(): String = "return $value"

	override val returned: Boolean
		get() = true
}