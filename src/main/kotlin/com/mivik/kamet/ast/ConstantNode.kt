package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM

internal class ConstantNode(val type: Type.Primitive, val value: String) : ASTNode {
	override fun codegen(context: Context): Value =
		Value(
			when (type) {
				is Type.Primitive.Boolean -> LLVM.LLVMConstInt(type.llvm, if (value == "true") 1 else 0, 0)
				is Type.Primitive.Integer -> LLVM.LLVMConstInt(type.llvm, value.toLong(), 0)
				is Type.Primitive.Real -> LLVM.LLVMConstReal(type.llvm, value.toDouble())
			}, type
		)

	override fun toString(): String = value
}