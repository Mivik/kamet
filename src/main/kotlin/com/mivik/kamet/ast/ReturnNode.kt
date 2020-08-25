package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import org.bytedeco.llvm.global.LLVM

internal class ReturnNode(val value: ASTNode) : ASTNode() {
	override fun Context.codegenForThis(): Value {
		val returnType = (currentFunction!!.type as Type.Function).returnType
		val result = value.codegen().implicitCast(returnType)
		if (result.type.canImplicitlyCastTo(Type.Unit)) LLVM.LLVMBuildRetVoid(builder)
		else LLVM.LLVMBuildRet(builder, result.llvm)
		return Value.Unit
	}

	override fun toString(): String = "return $value"

	override val returned: Boolean get() = true
}