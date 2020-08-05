package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.escape
import com.mivik.kamet.toLongIgnoringOverflow
import org.bytedeco.llvm.LLVM.LLVMBuilderRef
import org.bytedeco.llvm.global.LLVM

internal class ConstantNode(val type: Type.Primitive, val value: String) : ASTNode {
	override fun codegen(context: Context): Value =
		Value(
			when (type) {
				Type.Primitive.Integer.Char -> LLVM.LLVMConstInt(type.llvm, run {
					val string = value.substring(1, value.length - 1)
					if (string.startsWith("\\u"))
						string.substring(2).toShort(16).toLong()
					else if (string[0] == '\\') string[1].escape().toLong()
					else string[0].toLong()
				}, 0)
				is Type.Primitive.Boolean -> LLVM.LLVMConstInt(type.llvm, if (value == "true") 1 else 0, 0)
				is Type.Primitive.Integer -> LLVM.LLVMConstInt(type.llvm, value.toLongIgnoringOverflow(), 0)
				is Type.Primitive.Real -> LLVM.LLVMConstReal(type.llvm, value.toDouble())
			}, type
		)

	override fun toString(): String = value
}