package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import com.mivik.kamet.doInsideAndThen

internal class DoWhileNode(val block: ASTNode, val condition: ASTNode) : ASTNode() {
	override fun Context.codegenForThis(): Value {
		val whileBB = basicBlock("while")
		val finalBB = basicBlock("final")
		doInsideAndThen(whileBB, finalBB) {
			block.codegen()
			condBr(condition.codegen(), whileBB, finalBB)
		}
		return Value.Unit
	}

	override fun toString(): String = "do $block while ($condition)"
}