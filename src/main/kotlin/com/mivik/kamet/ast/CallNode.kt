package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.actualGenericName
import com.mivik.kamet.findMatchingFunction
import com.mivik.kamet.ifNotNull

internal class CallNode(
	val receiver: ASTNode?,
	val name: String,
	val elements: List<ASTNode>,
	val typeArguments: List<Type>
) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val receiver = receiver?.codegen()
		val arguments = elements.map { it.codegen() }
		val function =
			findMatchingFunction(
				name,
				lookupFunctions(name, receiver?.type),
				receiver?.type,
				arguments.map { it.type },
				typeArguments
			)
		return function.invoke(receiver, arguments)
	}

	override fun toString(): String =
		"${receiver.ifNotNull { "$receiver." }}${actualGenericName(name, typeArguments)}(${elements.joinToString()})"
}