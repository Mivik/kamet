package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value
import com.mivik.kamet.canImplicitlyCastTo
import com.mivik.kamet.findMatchingFunction
import com.mivik.kamet.ifThat
import org.bytedeco.javacpp.PointerPointer
import org.bytedeco.llvm.global.LLVM

internal class CallNode(val name: String, val receiver: ASTNode?, val elements: List<ASTNode>) : ASTNode {
	override fun Context.codegenForThis(): Value {
		val arguments = elements.map { it.codegen() }
		val function = findMatchingFunction(name, lookupFunctions(name), arguments.map { it.type })
		return function.invoke(receiver?.codegen(), arguments)
	}

	override fun toString(): String = "$name(${elements.joinToString()})"
}