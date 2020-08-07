package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.TypeDescriptor
import com.mivik.kamet.Value

internal class UndefNode(val type: TypeDescriptor) : ASTNode {
	override fun Context.codegenForThis(): Value = type.translate().undefined()

	override fun toString(): String = "undefined($type)"
}