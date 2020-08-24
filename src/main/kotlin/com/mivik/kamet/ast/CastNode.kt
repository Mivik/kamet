package com.mivik.kamet.ast

import com.mivik.kamet.Context
import com.mivik.kamet.Type
import com.mivik.kamet.Value

internal class CastNode(val value: ASTNode, val type: Type) : ASTNode {
	override fun Context.codegenForThis(): Value =
		value.codegen().explicitCast(type.resolve())

	override fun toString(): String = "$value as $type"
}