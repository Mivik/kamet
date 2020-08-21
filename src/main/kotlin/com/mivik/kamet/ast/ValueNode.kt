package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.ValueRef
import com.mivik.kamet.escape
import com.mivik.kamet.expect
import com.mivik.kamet.impossible
import com.mivik.kamet.reference
import org.bytedeco.llvm.global.LLVM

internal class ValueNode(val name: String) : ASTNode {
	private fun fail(): Nothing = error("Unknown identifier ${name.escape()}")

	override fun Context.codegenForThis(): Value =
		lookupValueOrNull(name) ?: run {
			val thisObject = lookupValueOrNull("this") ?: fail()
			val type = thisObject.type as Type.Reference
			val originStruct: Type.Struct = when (val origin = type.originalType) {
				is Type.Struct -> origin
				is Type.Dynamic -> origin.type.expect()
				else -> impossible()
			}
			val index = originStruct.elements.indexOfFirst { it.first == name }
			if (index == -1) fail()
			ValueRef(
				LLVM.LLVMBuildStructGEP2(
					builder,
					originStruct.llvm,
					thisObject.implicitCast(originStruct.reference(true)).llvm,
					index,
					"access_member"
				),
				originStruct.memberType(index),
				type.isConst
			)
		}

	override fun toString(): String = name
}