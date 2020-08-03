package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class ValueNode(val name: String) : ASTNode {
	override fun codegen(context: Context): Value = context.lookupValue(name).get(context)

	override fun toString(): String = name
}