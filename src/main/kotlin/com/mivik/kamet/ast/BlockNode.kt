package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class BlockNode : ASTNode {
	val elements: MutableList<ASTNode> = mutableListOf()

	override fun Context.codegenForThis(): Value {
		for (i in 0 until elements.lastIndex) elements[i].codegen()
		return elements.lastOrNull()?.codegen() ?: Value.Unit
	}

	override fun toString(): String =
		if (elements.isEmpty()) "{}"
		else "{\n${elements.joinToString("\n").split('\n').joinToString("\n") { '\t' + it }}\n}"

	override val returned: Boolean
		get() = elements.isNotEmpty() && elements.last().returned
}