package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value

internal class BlockNode : ASTNode {
	val elements: MutableList<ASTNode> = mutableListOf()

	override fun codegen(context: Context): Value {
		var last = Value.Unit
		for (element in elements) last = element.codegen(context)
		return last
	}

	override fun toString(): String =
		if (elements.isEmpty()) "{}"
		else "{\n${elements.joinToString("\n").split('\n').joinToString("\n") { '\t' + it }}\n}"

	override val returned: Boolean
		get() = elements.isNotEmpty() && elements.last().returned
}