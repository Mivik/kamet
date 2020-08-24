package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.ValueRef
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.llvm.global.LLVM

internal class PointerSubscriptNode(val pointer: ASTNode, val index: ASTNode) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val value = pointer.codegen().let {
			it.asPointerOrNull() ?: error("Subscripting a non-pointer type: ${it.type}")
		}
		val type = value.type as Type.Pointer
		val index = index.codegen()
		require(index.type is Type.Primitive.Integral) { "Array subscript is not a integer: ${index.type}" }
		return ValueRef(
			LLVM.LLVMBuildGEP2(
				builder,
				type.elementType.llvm,
				value.llvm,
				index.llvm,
				1,
				BytePointer("array_subscript")
			),
			type.elementType,
			type.isConst
		)
	}

	override fun toString(): String = "$pointer[$index]"
}