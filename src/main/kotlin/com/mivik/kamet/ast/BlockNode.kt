package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class BlockNode : StmtNode, ExprNode {
	val elements: MutableList<StmtNode> = mutableListOf()

	override fun codegen(context: Context): Value {
		for (element in elements) element.codegen(context)
		return Value.Empty
	}
}