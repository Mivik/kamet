package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class ValueNode(val name: String) : ASTNode {
	override fun Context.codegenForThis(): Value = lookupValue(name)

	override fun toString(): String = name
}