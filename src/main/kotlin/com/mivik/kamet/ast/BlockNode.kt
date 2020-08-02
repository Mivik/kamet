package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class BlockNode : ASTNode {
	val elements: MutableList<ASTNode> = mutableListOf()

	override fun codegen(context: Context): Value {
		if (elements.isEmpty()) return Value.Unit
		for (i in 0 until elements.lastIndex) elements[i].codegen(context)
		return elements.last().codegen(context)
	}
}