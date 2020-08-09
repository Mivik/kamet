package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.escape
import com.mivik.kamet.toLongIgnoringOverflow
import org.bytedeco.llvm.LLVM.LLVMValueRef
import org.bytedeco.llvm.global.LLVM

internal class ConstantNode(val type: Type.Primitive, val value: String) : ASTNode {
	override fun Context.codegenForThis() = type.new(makeLLVMConst())

	@Suppress("NOTHING_TO_INLINE")
	private inline fun makeLLVMConst(): LLVMValueRef {
		return when (type) {
			Type.Primitive.Integral.Char -> LLVM.LLVMConstInt(type.llvm, run {
				val content = value.substring(1, value.length - 1) // '[content]'
				when {
					content.startsWith("\\u") -> content.substring(2).toShort(16).toChar()
					content[0] == '\\' -> content[1].escape()
					else -> content[0]
				}.toLong()
			}, 0)
			is Type.Primitive.Boolean -> LLVM.LLVMConstInt(type.llvm, if (value == "true") 1 else 0, 0)
			is Type.Primitive.Integral -> LLVM.LLVMConstInt(type.llvm, value.toLongIgnoringOverflow(), 0)
			is Type.Primitive.Real -> LLVM.LLVMConstReal(type.llvm, value.toDouble())
		}
	}

	override fun toString(): String = value
}