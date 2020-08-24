package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value

internal class SizeOfNode(val type: Type) : ASTNode {
	override fun Context.codegenForThis(): Value = type.resolve().sizeOf()

	override fun toString(): String = "sizeof($type)"
}