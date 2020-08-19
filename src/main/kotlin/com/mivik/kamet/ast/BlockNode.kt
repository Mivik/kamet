package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import com.mivik.kamet.addIndent

internal class BlockNode : ASTNode {
	val elements: MutableList<ASTNode> = mutableListOf()

	override fun Context.codegenForThis(): Value {
		for (i in 0 until elements.lastIndex) elements[i].codegen()
		return elements.lastOrNull()?.codegen() ?: Value.Unit
	}

	override fun toString(): String =
		if (elements.isEmpty()) "{}"
		else "{\n${elements.joinToString("\n").addIndent()}\n}"

	override val returned: Boolean
		get() = elements.isNotEmpty() && elements.last().returned
}