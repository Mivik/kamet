package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Function
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.ifNotNull

internal class CallNode(
	val function: Function,
	val receiver: ASTNode?,
	val elements: List<ASTNode>,
	val typeArguments: List<Type>
) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val receiver = receiver?.codegen()
		val arguments = elements.map { it.codegen() }
		return function.resolve().invoke(receiver, arguments, typeArguments.map { it.resolve() })
	}

	override fun toString(): String =
		"${receiver.ifNotNull { "$receiver." }}$function(${elements.joinToString()})"
}