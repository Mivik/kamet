package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class TopLevelNode(val elements: List<ASTNode>) : ASTNode {
	override fun codegen(context: Context): Value {
		for (element in elements) element.codegen(context)
		return Value.Nothing
	}

	override fun toString(): String = elements.joinToString("\n\n")
}