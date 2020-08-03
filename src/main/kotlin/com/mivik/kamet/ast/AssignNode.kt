package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class AssignNode(val name: String, val value: ASTNode) : ASTNode {
	override fun codegen(context: Context): Value {
		context.lookupValue(name).set(context, value.codegen(context))
		return Value.Nothing
	}

	override fun toString(): String = "$name = $value"
}