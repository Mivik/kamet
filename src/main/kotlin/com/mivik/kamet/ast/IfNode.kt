package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Value
import com.mivik.kamet.doInside
import com.mivik.kamet.ifThat

internal class IfNode(val condition: ASTNode, val thenBlock: ASTNode, val elseBlock: ASTNode? = null) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val conditionValue = condition.codegen()
		var thenBB = basicBlock("then")
		var elseBB = basicBlock("else")
		if (elseBlock == null) {
			condBr(conditionValue, thenBB, elseBB)
			insertAt(thenBB)
			thenBlock.codegen()
			if (!thenBlock.returned) br(elseBB)
			insertAt(elseBB)
			return Value.Nothing
		} else { // if-else
			val mergeBB = basicBlock("merge")
			condBr(conditionValue, thenBB, elseBB)
			val thenRet: Value
			val elseRet: Value
			thenBB = doInside(thenBB) {
				thenRet = thenBlock.codegen()
			}
			elseBB = doInside(elseBB) {
				elseRet = elseBlock.codegen()
			}
			return if (thenRet.type == elseRet.type) { // when the whole if statement can be considered as a value
				// TODO whether two types are equivalent is not equal to whether they are equal
				val variable = declareVariable("if_result", thenRet.type.undefined())
				doInside(thenBB) {
					variable.setValue(thenRet)
					if (!thenBlock.returned) br(mergeBB)
				}
				doInside(elseBB) {
					variable.setValue(elseRet)
					if (!elseBlock.returned) br(mergeBB)
				}
				insertAt(mergeBB)
				variable.dereference()
			} else { // this if is not a expression
				doInside(thenBB) {
					if (!thenBlock.returned) br(mergeBB)
				}
				doInside(elseBB) {
					if (!elseBlock.returned) br(mergeBB)
				}
				insertAt(mergeBB)
				Value.Nothing
			}
		}
	}

	override fun toString(): String =
		"if ($condition) $thenBlock${(elseBlock != null).ifThat { " else $elseBlock" }}"

	override val returned: Boolean
		get() = thenBlock.returned && elseBlock != null && elseBlock.returned
}