package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value

internal class UndefNode(val type: Type) : ASTNode {
	override fun Context.codegenForThis(): Value = type.resolve().undefined()

	override fun toString(): String = "undefined($type)"
}