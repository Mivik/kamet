package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class TopLevelNode(val elements: List<ASTNode>) : ASTNode {
	override fun Context.codegenForThis(): Value {
		for (element in elements) element.codegen()
		return Value.Unit
	}

	override fun toString(): String = elements.joinToString("\n\n")
}