package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import com.mivik.kamet.doInside

internal class WhileNode(val condition: ASTNode, val block: ASTNode) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val whileBB = basicBlock("while")
		val finalBB = basicBlock("final")
		condBr(condition.codegen(), whileBB, finalBB)
		doInside(whileBB) {
			block.codegen()
			condBr(condition.codegen(), whileBB, finalBB)
		}
		insertAt(finalBB)
		return Value.Nothing
	}

	override fun toString(): String = "while ($condition) $block"
}