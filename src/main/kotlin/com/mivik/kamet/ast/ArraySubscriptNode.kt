package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.ValueRef
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.llvm.global.LLVM

internal class ArraySubscriptNode(val array: ASTNode, val index: ASTNode) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val array = array.codegen().dereference()
		val arrayType = array.type
		require(arrayType is Type.Array) { "Subscripting a non-array type: ${array.type}" }
		val index = index.codegen()
		require(index.type is Type.Primitive.Integral) { "Array subscript is not a integer: ${index.type}" }
		return ValueRef(
			LLVM.LLVMBuildGEP2(
				builder,
				arrayType.elementType.llvm,
				array.llvm,
				index.llvm,
				1,
				BytePointer("array_subscript")
			),
			arrayType.elementType,
			arrayType.isConst
		)
	}

	override fun toString(): String = "$array[$index]"
}